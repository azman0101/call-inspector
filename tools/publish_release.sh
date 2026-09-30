#!/usr/bin/env bash
# Publishes the release APK of a build on main as a GitHub release: tag v<VERSION_NAME> on the built commit,
# the signed APK under a versioned name with its SHA-256 file, and release notes made of an install and
# verification header (checksums, signing certificate, VirusTotal report, build run) followed by the pull
# requests merged since the previous release (GitHub's generated notes).
#
# It refuses to publish an APK that is not signed with the project's release key: a CI fallback key (missing
# KEYSTORE_BASE64 secret) produces an APK that cannot update any install, which must not become a release.
# Running it again for the same version (a re-run job) replaces the assets and notes instead of failing.
#
# Usage: tools/publish_release.sh path/to/app-release.apk
# Environment:
#   GH_TOKEN                token with contents: write (the workflow's github.token)
#   VERSION_NAME            e.g. 1.0.123 (tag v1.0.123)
#   EXPECTED_CERT_SHA256    SHA-256 of the release signing certificate (README, "Vérifier la signature")
#   GITHUB_REPOSITORY, GITHUB_SHA, GITHUB_SERVER_URL, GITHUB_RUN_ID, GITHUB_RUN_NUMBER  (set by Actions)
#   VIRUSTOTAL_REPORT_URL, VIRUSTOTAL_MALICIOUS, VIRUSTOTAL_SUSPICIOUS  optional, from virustotal_scan.sh
#   ARTIFACT_NAME           optional, the workflow artifact holding the same APK
#   APKSIGNER               optional, path to apksigner (default: newest in $ANDROID_HOME/build-tools)
set -euo pipefail

APK="${1:?usage: $0 path/to/app-release.apk}"
: "${VERSION_NAME:?}" "${EXPECTED_CERT_SHA256:?}" "${GITHUB_REPOSITORY:?}" "${GITHUB_SHA:?}"
SERVER="${GITHUB_SERVER_URL:-https://github.com}"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/stdout}"
TAG="v$VERSION_NAME"

[ -f "$APK" ] || { echo "::error::APK not found: $APK"; exit 1; }
case "$APK" in *unsigned*) echo "::error::Unsigned APK, not published: $APK"; exit 1 ;; esac

if [ -z "${APKSIGNER:-}" ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  APKSIGNER="$(ls -d "$sdk"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1 || true)"
fi
[ -x "${APKSIGNER:-}" ] || { echo "::error::apksigner not found (set APKSIGNER or ANDROID_HOME)"; exit 1; }

# The signer check reads the same certificate users are told to compare in the README.
cert_sha256="$("$APKSIGNER" verify --print-certs "$APK" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p' | head -1)"
expected="$(printf '%s' "$EXPECTED_CERT_SHA256" | tr -d ': ' | tr 'A-F' 'a-f')"
if [ "$cert_sha256" != "$expected" ]; then
  echo "::error::APK signed with certificate ${cert_sha256:-none}, not the release key $expected: release not published."
  exit 1
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
asset="$work/info-operateur-$VERSION_NAME.apk"
cp "$APK" "$asset"
apk_sha256="$(sha256sum "$asset" | cut -d' ' -f1)"
(cd "$work" && sha256sum "$(basename "$asset")" > "$(basename "$asset").sha256")

run_url="$SERVER/$GITHUB_REPOSITORY/actions/runs/${GITHUB_RUN_ID:-}"
short_sha="$(printf '%s' "$GITHUB_SHA" | cut -c1-7)"

# The previous release bounds the generated notes; the first release has none.
previous_tag="$(gh release view --repo "$GITHUB_REPOSITORY" --json tagName --jq .tagName 2>/dev/null || true)"
[ "$previous_tag" = "$TAG" ] && previous_tag="$(gh release list --repo "$GITHUB_REPOSITORY" --limit 2 --json tagName --jq '.[1].tagName // empty' 2>/dev/null || true)"
notes_args=(-f "tag_name=$TAG" -f "target_commitish=$GITHUB_SHA")
[ -n "$previous_tag" ] && notes_args+=(-f "previous_tag_name=$previous_tag")
generated="$(gh api --method POST "repos/$GITHUB_REPOSITORY/releases/generate-notes" "${notes_args[@]}" --jq .body)"

if [ -n "${VIRUSTOTAL_REPORT_URL:-}" ]; then
  if [ -n "${VIRUSTOTAL_MALICIOUS:-}" ]; then
    virustotal="[rapport]($VIRUSTOTAL_REPORT_URL) : ${VIRUSTOTAL_MALICIOUS} détection(s) malveillante(s), ${VIRUSTOTAL_SUSPICIOUS:-0} suspecte(s) (souvent des faux positifs pour une app qui lit le journal d'appels)"
  else
    virustotal="[rapport]($VIRUSTOTAL_REPORT_URL) (analyse encore en cours à la publication)"
  fi
else
  virustotal="non analysé pour cette version"
fi

notes="$work/notes.md"
{
  echo "## Installer"
  echo
  echo "Téléchargez \`$(basename "$asset")\` dans les *Assets* ci-dessous et ouvrez-le sur le téléphone (Android 7.0 ou plus récent). Il met à jour une installation existante de l'application."
  echo
  echo "## Vérifier"
  echo
  echo "- SHA-256 de l'APK : \`$apk_sha256\` (fichier \`.sha256\` joint)"
  echo "- Certificat de signature (SHA-256) : \`$cert_sha256\`, à comparer avec \`apksigner verify --print-certs $(basename "$asset")\`"
  echo "- VirusTotal : $virustotal"
  echo "- Build : [run GitHub Actions #${GITHUB_RUN_NUMBER:-$GITHUB_RUN_ID}]($run_url)${ARTIFACT_NAME:+, artefact \`$ARTIFACT_NAME\`}, commit \`$short_sha\`"
  echo
  echo "$generated"
} > "$notes"

if gh release view "$TAG" --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  echo "Release $TAG already exists: replacing its assets and notes."
  gh release upload "$TAG" "$asset" "$asset.sha256" --repo "$GITHUB_REPOSITORY" --clobber
  gh release edit "$TAG" --repo "$GITHUB_REPOSITORY" --notes-file "$notes"
else
  gh release create "$TAG" "$asset" "$asset.sha256" \
    --repo "$GITHUB_REPOSITORY" \
    --target "$GITHUB_SHA" \
    --title "Info Opérateur $VERSION_NAME" \
    --notes-file "$notes" \
    --latest
fi

release_url="$SERVER/$GITHUB_REPOSITORY/releases/tag/$TAG"
{
  echo "### GitHub release"
  echo
  echo "- Release: $release_url"
  echo "- APK: \`$(basename "$asset")\`, SHA-256 \`$apk_sha256\`"
} >> "$SUMMARY"
echo "Published $release_url"
