#!/usr/bin/env bash
# Generates the stable debug keystore used to sign CI debug APKs, and its base64 form
# for the DEBUG_KEYSTORE_BASE64 GitHub secret. Run from the repository root (Termux or Linux).
set -euo pipefail

OUT_DIR="${1:-keys-debug}"
KEYSTORE="$OUT_DIR/debug.keystore"
B64="$OUT_DIR/debug.keystore.b64"

if ! command -v keytool >/dev/null 2>&1; then
  if command -v pkg >/dev/null 2>&1; then
    pkg install -y openjdk-21
  else
    echo "keytool not found: install a JDK first." >&2
    exit 1
  fi
fi

if [ -e "$KEYSTORE" ]; then
  echo "$KEYSTORE already exists: refusing to overwrite a key that installs may depend on." >&2
  exit 1
fi

mkdir -p "$OUT_DIR"
chmod 700 "$OUT_DIR"

# Alias and passwords must match signingConfigs.debugConfig in app/build.gradle.kts.
keytool -genkeypair \
  -keystore "$KEYSTORE" \
  -storepass android -keypass android \
  -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=slashetc.net debug, O=slashetc, C=FR"

base64 -w0 "$KEYSTORE" > "$B64"
chmod 600 "$KEYSTORE" "$B64"

echo
echo "Keystore : $KEYSTORE"
echo "Base64   : $B64"
keytool -list -v -keystore "$KEYSTORE" -storepass android -alias androiddebugkey | grep -E "Owner|Propriétaire|SHA256"
