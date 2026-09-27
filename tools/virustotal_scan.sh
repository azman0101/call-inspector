#!/usr/bin/env bash
# Uploads an APK to VirusTotal, waits for the analysis and reports the result: detection counts and the
# public report link, in the GitHub job summary and as step outputs (report_url, malicious, suspicious).
# Informational only: detections produce a warning, never a failure. The CI step also sets
# continue-on-error, so an API error or timeout cannot block the release either.
#
# Usage: VIRUSTOTAL_API_KEY=... tools/virustotal_scan.sh path/to/app.apk
# Without VIRUSTOTAL_API_KEY the scan is skipped. The public API allows 4 requests a minute: the
# analysis is polled every 20 seconds, for at most POLL_ATTEMPTS times (default 30, i.e. 10 minutes).
set -euo pipefail

API="https://www.virustotal.com/api/v3"
APK="${1:?usage: $0 path/to/app.apk}"
POLL_ATTEMPTS="${POLL_ATTEMPTS:-30}"
POLL_SECONDS="${POLL_SECONDS:-20}"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/stdout}"
OUTPUT="${GITHUB_OUTPUT:-/dev/null}"

[ -f "$APK" ] || { echo "::error::APK not found: $APK"; exit 1; }
if [ -z "${VIRUSTOTAL_API_KEY:-}" ]; then
  echo "::notice::VIRUSTOTAL_API_KEY is not set: VirusTotal scan skipped."
  exit 0
fi

# The key goes to curl through a header file, not the command line (visible to other processes).
header_file="$(mktemp)"
trap 'rm -f "$header_file"' EXIT
printf 'x-apikey: %s\n' "$VIRUSTOTAL_API_KEY" > "$header_file"
vt() { curl -fsS --retry 3 --retry-delay 5 -H "@$header_file" "$@"; }
json() { python3 -c "import json, sys; data = json.load(sys.stdin); print($1)"; }

sha256="$(sha256sum "$APK" | cut -d' ' -f1)"
size="$(stat -c %s "$APK")"
report_url="https://www.virustotal.com/gui/file/$sha256"
echo "APK: $APK ($size bytes, sha256 $sha256)"

# Files over 32 MB need a one-time upload URL.
upload_url="$API/files"
if [ "$size" -gt 32000000 ]; then
  upload_url="$(vt "$API/files/upload_url" | json 'data["data"]')"
fi
analysis_id="$(vt -F "file=@$APK" "$upload_url" | json 'data["data"]["id"]')"
echo "Uploaded, analysis $analysis_id"

status=""
for attempt in $(seq 1 "$POLL_ATTEMPTS"); do
  sleep "$POLL_SECONDS"
  analysis="$(vt "$API/analyses/$analysis_id")"
  status="$(json 'data["data"]["attributes"]["status"]' <<<"$analysis")"
  echo "Analysis status ($attempt/$POLL_ATTEMPTS): $status"
  [ "$status" = "completed" ] && break
done

{
  echo "### VirusTotal"
  echo
  echo "- APK: \`$(basename "$APK")\` ($size bytes)"
  echo "- SHA-256: \`$sha256\`"
  echo "- Report: $report_url"
} >> "$SUMMARY"
echo "report_url=$report_url" >> "$OUTPUT"

if [ "$status" != "completed" ]; then
  echo "- Analysis still \`${status:-unknown}\` after $POLL_ATTEMPTS checks: see the report link." >> "$SUMMARY"
  echo "::warning::VirusTotal analysis not finished yet: $report_url"
  exit 0
fi

read -r malicious suspicious undetected harmless total < <(json '" ".join(str(x) for x in (
    data["data"]["attributes"]["stats"].get("malicious", 0),
    data["data"]["attributes"]["stats"].get("suspicious", 0),
    data["data"]["attributes"]["stats"].get("undetected", 0),
    data["data"]["attributes"]["stats"].get("harmless", 0),
    sum(data["data"]["attributes"]["stats"].values())))' <<<"$analysis")
echo "- Detections: **$malicious malicious**, $suspicious suspicious, $undetected undetected, $harmless harmless (out of $total engine results)" >> "$SUMMARY"
{ echo "malicious=$malicious"; echo "suspicious=$suspicious"; } >> "$OUTPUT"
echo "VirusTotal: $malicious malicious, $suspicious suspicious out of $total. Report: $report_url"
if [ "$malicious" -gt 0 ] || [ "$suspicious" -gt 0 ]; then
  echo "::warning::VirusTotal: $malicious malicious and $suspicious suspicious detections, check for false positives: $report_url"
fi
