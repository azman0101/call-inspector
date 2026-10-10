#!/usr/bin/env bash
# Decides whether this build of main publishes a release, so that merges landing close together share one
# release instead of one each. It waits QUIET_MINUTES, then looks at the newer runs of this workflow on main
# (pushes and "Publier maintenant"):
#   - one whose build job passed publishes later, with this build's changes: this one does not publish;
#   - one whose build job is still running is waited for, since it may fail, at most PENDING_MINUTES (then
#     this one publishes: at worst the newer one publishes a second release, none is lost);
#   - none, or only failed ones: this build is the last good one of the wave, and publishes.
# Writes publish=true or publish=false to GITHUB_OUTPUT.
#
# Usage: tools/release_window.sh
# Environment:
#   GH_TOKEN          token with actions: read (the workflow's github.token)
#   GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_RUN_NUMBER  (set by Actions)
#   QUIET_MINUTES     default 30; 0 publishes right away unless a newer build passed
#   PENDING_MINUTES   default 45
#   BUILD_JOB         name of the build job, default "Build & Test Android APK"
#   POLL_SECONDS      default 60, between two looks at a newer build still running
set -euo pipefail

QUIET_MINUTES="${QUIET_MINUTES:-30}"
PENDING_MINUTES="${PENDING_MINUTES:-45}"
BUILD_JOB="${BUILD_JOB:-Build & Test Android APK}"
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
  newer="$(api "repos/$GITHUB_REPOSITORY/actions/workflows/$workflow_id/runs?branch=main&per_page=50" \
    --jq ".workflow_runs[] | select((.event == \"push\" or .event == \"workflow_dispatch\") and .run_number > $GITHUB_RUN_NUMBER) | \"\(.id) \(.run_number)\"")"
  pending=""
  while read -r id number; do
    [ -n "$id" ] || continue
    # "null null" while the run has not started its build job yet.
    build="$(api "repos/$GITHUB_REPOSITORY/actions/runs/$id/jobs" \
      --jq "[.jobs[] | select(.name == \"$BUILD_JOB\")][0] | \"\(.status) \(.conclusion)\"")"
    case "$build" in
      "completed success")
        decide false "Build #$number of main passed after this one: it publishes the release, with the changes of build #$GITHUB_RUN_NUMBER." ;;
      completed\ *) ;; # failed or cancelled: it publishes nothing
      *) pending="$pending #$number" ;;
    esac
  done <<<"$newer"
  [ -n "$pending" ] || break
  if [ "$(date +%s)" -ge "$deadline" ]; then
    echo "::warning::Build(s)$pending of main still running after $PENDING_MINUTES minutes: build #$GITHUB_RUN_NUMBER publishes now."
    break
  fi
  echo "Waiting for build(s)$pending of main, which may pass and publish instead..."
  sleep "$POLL_SECONDS"
done
decide true "No newer build of main passed: build #$GITHUB_RUN_NUMBER publishes release v1.0.$GITHUB_RUN_NUMBER."
