#!/usr/bin/env bash
# Creates the "release" GitHub environment, limited to the main branch, and stores the release signing
# secrets in it: KEYSTORE_BASE64 (rebuilt from the keystore file), STORE_PASSWORD and KEY_PASSWORD.
# The passwords are prompted and checked against the keystore before anything is sent. Runs on Termux or
# Linux.
#
# Usage: tools/setup-release-environment.sh [--keys-dir DIR] [--keystore FILE] [--alias ALIAS]
#                                           [--repo OWNER/REPO] [--env NAME] [--branch BRANCH]
#   --keys-dir  where to look for the keystore (default: ~/keys)
#   --keystore  the keystore file, when the keys directory holds several
#   --alias     key alias (default: upload, as keyAlias in app/build.gradle.kts)
#   --repo      default azman0101/call-inspector
#   --env       environment name (default: release)
#   --branch    only branch allowed to deploy to it (default: main)
#
# It then asks for SENTRY_DSN and NVD_API_KEY if the environment lacks them: the build job of main runs
# in it. Nothing is printed or written to disk except a temporary Java checker; values go to gh through
# stdin, never on a command line.
set -euo pipefail

KEYS_DIR="$HOME/keys"
KEYSTORE=""
ALIAS="upload"
REPO="azman0101/call-inspector"
ENVIRONMENT="release"
BRANCH="main"
while [ $# -gt 0 ]; do
  case "$1" in
    --keys-dir) KEYS_DIR="$2"; shift 2 ;;
    --keystore) KEYSTORE="$2"; shift 2 ;;
    --alias) ALIAS="$2"; shift 2 ;;
    --repo) REPO="$2"; shift 2 ;;
    --env) ENVIRONMENT="$2"; shift 2 ;;
    --branch) BRANCH="$2"; shift 2 ;;
    -h|--help) sed -n '2,18p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
done

fail() { echo "Error: $*" >&2; exit 1; }

install_or_fail() { # command, Termux package
  command -v "$1" >/dev/null 2>&1 && return
  if command -v pkg >/dev/null 2>&1; then
    pkg install -y "$2"
  else
    fail "$1 not found: install it first."
  fi
}
install_or_fail gh gh
install_or_fail java openjdk-21
install_or_fail base64 coreutils
install_or_fail sha256sum coreutils

# --- GitHub access -----------------------------------------------------------------------------------
if ! gh auth status >/dev/null 2>&1; then
  echo "Logging in to GitHub (the account needs admin access to $REPO)..."
  gh auth login
fi
[ "$(gh api "repos/$REPO" --jq .permissions.admin 2>/dev/null)" = "true" ] ||
  fail "your GitHub account is not an admin of $REPO: it cannot create environments or secrets."

# --- Keystore ----------------------------------------------------------------------------------------
if [ -z "$KEYSTORE" ]; then
  [ -d "$KEYS_DIR" ] || fail "keys directory not found: $KEYS_DIR (use --keys-dir or --keystore)."
  mapfile -t candidates < <(find "$KEYS_DIR" -maxdepth 1 -type f \
    \( -name '*.jks' -o -name '*.keystore' -o -name '*.p12' -o -name '*.pfx' \) ! -name 'debug.keystore' | sort)
  case "${#candidates[@]}" in
    0) fail "no keystore (*.jks, *.keystore, *.p12) in $KEYS_DIR." ;;
    1) KEYSTORE="${candidates[0]}" ;;
    *)
      echo "Several keystores in $KEYS_DIR:"
      select choice in "${candidates[@]}"; do
        [ -n "$choice" ] && { KEYSTORE="$choice"; break; }
      done
      ;;
  esac
fi
[ -f "$KEYSTORE" ] || fail "keystore not found: $KEYSTORE"
echo "Keystore: $KEYSTORE (alias '$ALIAS')"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"; unset STORE_PASSWORD KEY_PASSWORD KEYSTORE_B64' EXIT

# Loads the private key the way release signing does (KeyStore.getKey), which also checks the key password
# of PKCS12 keystores; keytool ignores it for them. Reads both passwords from stdin.
# Exit codes: 0 ok, 2 wrong store password, 3 no key under this alias, 4 wrong key password.
cat > "$WORK_DIR/CheckKey.java" <<'EOF'
import java.io.*;
import java.security.*;

public class CheckKey {
    public static void main(String[] args) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        char[] storePassword = in.readLine().toCharArray();
        char[] keyPassword = in.readLine().toCharArray();
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance(new File(args[0]), storePassword);
        } catch (IOException | GeneralSecurityException e) {
            System.exit(2); return;
        }
        if (!keyStore.isKeyEntry(args[1])) { System.exit(3); return; }
        try {
            if (keyStore.getKey(args[1], keyPassword) == null) System.exit(4);
        } catch (UnrecoverableKeyException e) {
            System.exit(4);
        }
    }
}
EOF

check_key() { # store password, key password -> exit code of CheckKey
  local status=0
  printf '%s\n%s\n' "$1" "$2" | java "$WORK_DIR/CheckKey.java" "$KEYSTORE" "$ALIAS" 2>/dev/null || status=$?
  return "$status"
}

STORE_PASSWORD=""
for attempt in 1 2 3; do
  read -rsp "Keystore password (STORE_PASSWORD): " STORE_PASSWORD; echo
  status=0; check_key "$STORE_PASSWORD" "$STORE_PASSWORD" || status=$?
  case "$status" in
    0|4) break ;;
    3) fail "the keystore has no private key under the alias '$ALIAS' (see --alias)." ;;
    *) echo "Wrong keystore password ($attempt/3)."; STORE_PASSWORD="" ;;
  esac
done
[ -n "$STORE_PASSWORD" ] || fail "keystore password not confirmed."
echo "Keystore password OK: the keystore opens."

KEY_PASSWORD=""
for attempt in 1 2 3; do
  read -rsp "Key password (KEY_PASSWORD, Enter = same as the keystore): " KEY_PASSWORD; echo
  [ -n "$KEY_PASSWORD" ] || KEY_PASSWORD="$STORE_PASSWORD"
  if check_key "$STORE_PASSWORD" "$KEY_PASSWORD"; then
    break
  fi
  echo "Wrong key password ($attempt/3)."; KEY_PASSWORD=""
done
[ -n "$KEY_PASSWORD" ] || fail "key password not confirmed."
echo "Key password OK: the private key '$ALIAS' decrypts."

# Single-line base64, checked by decoding it back to the exact same bytes.
KEYSTORE_B64="$(base64 -w 0 "$KEYSTORE")"
[ "$(printf '%s' "$KEYSTORE_B64" | base64 -d | sha256sum | cut -d' ' -f1)" = "$(sha256sum < "$KEYSTORE" | cut -d' ' -f1)" ] ||
  fail "the base64 copy of the keystore does not decode to the same file."
echo "KEYSTORE_BASE64 rebuilt from the keystore (${#KEYSTORE_B64} characters, decodes to the same file)."

# --- Environment -------------------------------------------------------------------------------------
echo "Creating or updating the '$ENVIRONMENT' environment, limited to the '$BRANCH' branch..."
gh api -X PUT "repos/$REPO/environments/$ENVIRONMENT" \
  -F 'deployment_branch_policy[protected_branches]=false' \
  -F 'deployment_branch_policy[custom_branch_policies]=true' >/dev/null
if ! gh api "repos/$REPO/environments/$ENVIRONMENT/deployment-branch-policies" \
    --jq '.branch_policies[] | select(.type == "branch" or .type == null) | .name' | grep -qx "$BRANCH"; then
  gh api -X POST "repos/$REPO/environments/$ENVIRONMENT/deployment-branch-policies" \
    -f name="$BRANCH" -f type=branch >/dev/null
fi

# --- Secrets -----------------------------------------------------------------------------------------
printf '%s' "$KEYSTORE_B64" | gh secret set KEYSTORE_BASE64 --repo "$REPO" --env "$ENVIRONMENT"
printf '%s' "$STORE_PASSWORD" | gh secret set STORE_PASSWORD --repo "$REPO" --env "$ENVIRONMENT"
printf '%s' "$KEY_PASSWORD" | gh secret set KEY_PASSWORD --repo "$REPO" --env "$ENVIRONMENT"

# The build job of main runs in this environment, so it also needs the build's other environment secrets
# (repository secrets are visible everywhere). Environments cannot share secrets: ask for the values.
env_secrets="$(gh secret list --repo "$REPO" --env "$ENVIRONMENT" --json name --jq '.[].name')"
for name in SENTRY_DSN NVD_API_KEY; do
  grep -qx "$name" <<<"$env_secrets" && continue
  read -rsp "$name for '$ENVIRONMENT' (Enter to skip): " value; echo
  if [ -n "$value" ]; then
    printf '%s' "$value" | gh secret set "$name" --repo "$REPO" --env "$ENVIRONMENT"
  else
    echo "  Skipped: builds on $BRANCH will run without $name until it is set in '$ENVIRONMENT'."
  fi
done
unset value

echo
echo "Secrets of the '$ENVIRONMENT' environment:"
gh secret list --repo "$REPO" --env "$ENVIRONMENT"
echo
echo "Other secrets the build reads, and where they are now (names only):"
echo "  repository:"; gh secret list --repo "$REPO" | sed 's/^/    /'
for env in $(gh api "repos/$REPO/environments" --jq '.environments[].name'); do
  [ "$env" = "$ENVIRONMENT" ] && continue
  echo "  environment '$env':"; gh secret list --repo "$REPO" --env "$env" | sed 's/^/    /'
done
# The signing secrets may also exist elsewhere, from before this environment: list the exact commands to
# remove those copies, to run once the workflow uses this environment and a release build has passed.
cleanup=()
repo_secrets="$(gh secret list --repo "$REPO" --json name --jq '.[].name')"
for name in KEYSTORE_BASE64 STORE_PASSWORD KEY_PASSWORD; do
  if grep -qx "$name" <<<"$repo_secrets"; then
    cleanup+=("gh secret delete $name --repo $REPO")
  fi
  for env in $(gh api "repos/$REPO/environments" --jq '.environments[].name'); do
    [ "$env" = "$ENVIRONMENT" ] && continue
    if gh secret list --repo "$REPO" --env "$env" --json name --jq '.[].name' | grep -qx "$name"; then
      cleanup+=("gh secret delete $name --repo $REPO --env $env")
    fi
  done
done

echo
echo "Done."
if [ "${#cleanup[@]}" -gt 0 ]; then
  cat <<EOF
Older copies of the signing secrets are still readable outside '$ENVIRONMENT'. Repository secrets are
readable by every workflow run of every branch of the repository. Once the workflow uses '$ENVIRONMENT'
for $BRANCH and a release build there has passed, remove them:
EOF
  printf '  %s\n' "${cleanup[@]}"
fi
