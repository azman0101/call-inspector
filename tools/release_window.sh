#!/usr/bin/env bash
# Decides whether this build of main publishes a release, so that merges landing close together share one
# release instead of one each. For QUIET_MINUTES it watches the newer runs of this workflow on main (pushes
# and "Publier maintenant"):
#   - as soon as one of them passes its build job, this build stops (publish=false): that newer run
#     publishes later, with this build's changes. If it fails after its build (publication, cancellation),
#     re-run it, or use "Run workflow" on main;
#   - at the end of the wait, a newer build job still running is waited for, at most PENDING_MINUTES, since
#     it may fail: it passes, this build stops; it fails, this build publishes. Past PENDING_MINUTES, this
#     build goes on to publish (none is lost);
#   - no newer build passed: this build is the last good one of the wave, and publishes (publish=true),
#     unless a higher version is already published (a re-run of an older build).
# The publish job checks again (--recheck) once its turn comes, so it never publishes after a newer release.
# Writes publish=true or publish=false to GITHUB_OUTPUT.
#
# Usage: tools/release_window.sh [--recheck]
#   --recheck  only the last check, without waiting: is a higher version already published? For the publish
#              job, once it holds the release-publish slot (publications run one at a time), before building.
# Environment:
#   GH_TOKEN          token with actions: read and contents: read (the workflow's github.token)
#   GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_RUN_NUMBER  (set by Actions)
#   QUIET_MINUTES     default 30; 0 skips the wait (a newer build still running is still waited for)
#   PENDING_MINUTES   default 45
#   BUILD_JOB         name of the build job, default "Build & Test Android APK"
#   POLL_SECONDS      default 60, between two looks at the newer runs
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

# Looks at the build job of every newer run of main: stops this build as soon as one passed, and lists in
# $pending those still running (or not started yet).
check_newer_builds() {
  local runs id number build
  pending=""
  runs="$(api "repos/$GITHUB_REPOSITORY/actions/workflows/$workflow_id/runs?branch=main&per_page=50" \
    --jq ".workflow_runs[] | select((.event == \"push\" or .event == \"workflow_dispatch\") and .run_number > $GITHUB_RUN_NUMBER) | \"\(.id) \(.run_number)\"")"
  while read -r id number; do
    [ -n "$id" ] || continue
    # "null null" while the run has not started its build job yet.
    build="$(api "repos/$GITHUB_REPOSITORY/actions/runs/$id/jobs" \
      --jq "[.jobs[] | select(.name == \"$BUILD_JOB\")][0] | \"\(.status) \(.conclusion)\"")"
    case "$build" in
      "completed success")
        decide false "Build #$number of main passed after this one: it publishes the release, with the changes of build #$GITHUB_RUN_NUMBER. Should it fail later, re-run it, or use Run workflow on main." ;;
      completed\ *) ;; # failed or cancelled: it publishes nothing
      *) pending="$pending #$number" ;;
    esac
  done <<<"$runs"
}

wait_for_the_wave() {
  local now quiet_end pending_end
  workflow_id="$(api "repos/$GITHUB_REPOSITORY/actions/runs/$GITHUB_RUN_ID" --jq .workflow_id)"
  quiet_end=$(($(date +%s) + QUIET_MINUTES * 60))
  [ "$QUIET_MINUTES" -eq 0 ] || echo "Waiting $QUIET_MINUTES minutes for the merges that follow build #$GITHUB_RUN_NUMBER..."
  while :; do
    check_newer_builds
    now="$(date +%s)"
    [ "$now" -lt "$quiet_end" ] || break
    sleep $((quiet_end - now < POLL_SECONDS ? quiet_end - now : POLL_SECONDS))
  done
  pending_end=$(($(date +%s) + PENDING_MINUTES * 60))
  while [ -n "$pending" ]; do
    if [ "$(date +%s)" -ge "$pending_end" ]; then
      echo "::warning::Build(s)$pending of main still running after $PENDING_MINUTES minutes: build #$GITHUB_RUN_NUMBER goes on to publish (checked again before building)."
      return 0
    fi
    echo "Waiting for build(s)$pending of main, which may pass and publish these changes instead..."
    sleep "$POLL_SECONDS"
    check_newer_builds
  done
}

[ "${1:-}" = "--recheck" ] || wait_for_the_wave

# The highest version published, from the tags v1.0.<run number>.
published="$(api "repos/$GITHUB_REPOSITORY/releases?per_page=20" \
  --jq '[.[] | select(.draft | not) | .tag_name | capture("^v1\\.0\\.(?<n>[0-9]+)$").n | tonumber] | max // 0')"
if [ "$published" -gt "$GITHUB_RUN_NUMBER" ]; then
  decide false "Release v1.0.$published, published by a newer run of main, holds the changes of build #$GITHUB_RUN_NUMBER: nothing to publish."
fi
decide true "No newer build of main passed: build #$GITHUB_RUN_NUMBER publishes release v1.0.$GITHUB_RUN_NUMBER."
