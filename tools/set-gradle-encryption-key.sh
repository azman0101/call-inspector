#!/usr/bin/env bash
# Creates the GRADLE_ENCRYPTION_KEY GitHub secret: the AES key setup-gradle uses to save the
# configuration cache between CI runs (.github/workflows/build_apk.yml). Runs on Termux or Linux.
#
# Usage: tools/set-gradle-encryption-key.sh [--repo OWNER/REPO] [--env ENVIRONMENT]
#   --repo  defaults to azman0101/call-inspector
#   --env   stores it as an environment secret (e.g. production) instead of a repository secret
#
# The key is never printed or written to disk. Replacing it is harmless: the next CI run only
# rebuilds the configuration cache.
set -euo pipefail

REPO="azman0101/call-inspector"
ENVIRONMENT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --repo) REPO="$2"; shift 2 ;;
    --env) ENVIRONMENT="$2"; shift 2 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

install_or_fail() { # command, Termux package
  command -v "$1" >/dev/null 2>&1 && return
  if command -v pkg >/dev/null 2>&1; then
    pkg install -y "$2"
  else
    echo "$1 not found: install it first." >&2
    exit 1
  fi
}
install_or_fail openssl openssl-tool
install_or_fail gh gh

if ! gh auth status >/dev/null 2>&1; then
  echo "Logging in to GitHub (the account needs admin access to $REPO)..."
  gh auth login
fi

target="repository $REPO"
env_args=()
if [ -n "$ENVIRONMENT" ]; then
  target="environment '$ENVIRONMENT' of $REPO"
  env_args=(--env "$ENVIRONMENT")
fi

if gh secret list --repo "$REPO" "${env_args[@]}" 2>/dev/null | grep -q '^GRADLE_ENCRYPTION_KEY\b'; then
  echo "GRADLE_ENCRYPTION_KEY already exists in the $target: it will be replaced."
fi

# 16 random bytes, base64: the AES key format setup-gradle expects.
openssl rand -base64 16 | tr -d '\n' | gh secret set GRADLE_ENCRYPTION_KEY --repo "$REPO" "${env_args[@]}"

echo "GRADLE_ENCRYPTION_KEY set in the $target."
echo "Check: gh secret list --repo $REPO ${env_args[*]}"
