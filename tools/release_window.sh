#!/usr/bin/env bash
# Decides whether this build of main publishes a release, so that merges landing close together share one
# release instead of one each. It waits QUIET_MINUTES, then waits for the newer runs of this workflow on main
# (pushes and "Publier maintenant") to end, at most PENDING_MINUTES:
#   - a newer run published a release (its version is higher than this one): it holds this build's changes,
#     so this build does not publish;
#   - none did (no newer run, or their build failed, or they were cancelled, or their publication failed):
#     this build is the last good one of the wave, and publishes. A wave whose last run fails still gets a
#     release, from its last good build.
# Past PENDING_MINUTES with a newer run still going, this one publishes: at worst the newer run publishes a
# second release, none is lost. Writes publish=true or publish=false to GITHUB_OUTPUT.
#
# Usage: tools/release_window.sh
# Environment:
#   GH_TOKEN          token with actions: read and contents: read (the workflow's github.token)
#   GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_RUN_NUMBER  (set by Actions)
#   QUIET_MINUTES     default 30; 0 skips the wait (newer runs still going are waited for)
#   PENDING_MINUTES   default 300
#   POLL_SECONDS      default 60, between two looks at the newer runs
set -euo pipefail

QUIET_MINUTES="${QUIET_MINUTES:-30}"
PENDING_MINUTES="${PENDING_MINUTES:-300}"
POLL_SECONDS="${POLL_SECONDS:-60}"
: "${GITHUB_REPOSITORY:?}" "${GITHUB_RUN_ID:?}" "${GITHUB_RUN_NUMBER:?}"
OUTPUT="${GITHUB_OUTPUT:-/dev/stdout}"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/stdout}"

# A transient API error after half an hour of waiting must not lose the release.
api() {
  local attempt out
  for attempt in 1 2 3; do
    if out="$(gh api "$@")"; then
      printf '%s\n' "$out"
      return 0
    fi
    sleep 15
  done
  return 1
}

decide() { # publish (true|false), reason
  echo "publish=$1" >> "$OUTPUT"
  echo "$2"
  printf '### Release window\n\n%s\n' "$2" >> "$SUMMARY"
  exit 0
}

if [ "$QUIET_MINUTES" -gt 0 ]; then
  echo "Waiting $QUIET_MINUTES minutes for the merges that follow build #$GITHUB_RUN_NUMBER..."
  sleep $((QUIET_MINUTES * 60))
fi

workflow_id="$(api "repos/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID" --jq .workflow_id)"
deadline=$(($(date +%s) + PENDING_MINUTES * 60))
while :; do
  pending="$(api "repos/$GITHUB_REPOSITORY/actions/workflows/$workflow_id/runs?branch=main&per_page=50" \
    --jq "[.workflow_runs[] | select((.event == \"push\" or .event == \"workflow_dispatch\") and .run_number > $GITHUB_RUN_NUMBER and .status != \"completed\") | \"#\(.run_number)\"] | join(\" \")")"
  [ -n "$pending" ] || break
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "::warning::Run(s) $pending of main still going after $PENDING_MINUTES minutes: build #$GITHUB_RUN_NUMBER publishes now."
    break
  fi
  echo "Waiting for run(s) $pending of main, which may publish these changes instead..."
  sleep "$POLL_SECONDS"
done

# The highest version published, from the tags v1.0.<run number>.
published="$(api "repos/$GITHUB_REPOSITORY/releases?per_page=20" \
  --jq '[.[] | select(.draft | not) | .tag_name | capture("^v1\\.0\\.(?<n>[0-9]+)$").n | tonumber] | max // 0')"
if [ "$published" -gt "$GITHUB_RUN_NUMBER" ]; then
  decide false "Release v1.0.$published, published by a newer run of main, holds the changes of build #$GITHUB_RUN_NUMBER: nothing to publish."
fi
decide true "No newer run of main published a release: build #$GITHUB_RUN_NUMBER publishes release v1.0.$GITHUB_RUN_NUMBER."
