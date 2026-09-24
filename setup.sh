#!/data/data/com.termux/files/usr/bin/bash

# Termux Android Build Environment Setup
#
# Target:
#   - Android / Termux ARM64
#   - OpenJDK 21
#   - Gradle 9.6.0
#   - Android SDK Platform 36
#   - Android SDK Build Tools 36.0.0
#   - Native AArch64 AAPT2 compatible with SDK 35/36/37
#
# The native AAPT2 is taken from:
#   https://github.com/poordevcode/termux-android-studio
#
# IMPORTANT:
#   The stock Termux aapt2 is too old for SDK 35+.
#   The normal Android SDK aapt2 may also be unusable directly on Termux.
#
# This script configures AAPT2 through:
#   android.aapt2FromMavenOverride
#
# It does NOT modify Android projects.

set -Eeuo pipefail

# -----------------------------------------------------------------------------
# Configuration
# -----------------------------------------------------------------------------

readonly GRADLE_VERSION="9.6.0"
readonly ANDROID_API="36"
readonly BUILD_TOOLS_VERSION="36.0.0"

readonly TERMUX_PREFIX="${PREFIX:-/data/data/com.termux/files/usr}"
readonly HOME_DIR="${HOME:-/data/data/com.termux/files/home}"

readonly ANDROID_HOME="${HOME_DIR}/android-sdk"
readonly ANDROID_SDK_ROOT="${ANDROID_HOME}"

readonly GRADLE_DIR="${ANDROID_HOME}/gradle"
readonly GRADLE_BIN="${GRADLE_DIR}/bin/gradle"

readonly JAVA_HOME="${TERMUX_PREFIX}/lib/jvm/java-21-openjdk"

readonly BUILD_TOOLS_DIR="${ANDROID_HOME}/build-tools/${BUILD_TOOLS_VERSION}"
readonly AAPT2_PATH="${BUILD_TOOLS_DIR}/aapt2"

readonly GRADLE_PROPERTIES="${HOME_DIR}/.gradle/gradle.properties"

readonly AAPT2_URL="https://raw.githubusercontent.com/poordevcode/termux-android-studio/main/prebuilt/aapt2-aarch64"

# -----------------------------------------------------------------------------
# Output
# -----------------------------------------------------------------------------

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

status() {
    echo -e "${GREEN}[✓]${NC} $*"
}

info() {
    echo -e "${CYAN}[i]${NC} $*"
}

warn() {
    echo -e "${YELLOW}[!]${NC} $*"
}

error() {
    echo -e "${RED}[✗]${NC} $*" >&2
}

die() {
    error "$*"
    exit 1
}

# -----------------------------------------------------------------------------
# Trap
# -----------------------------------------------------------------------------

trap 'error "Setup failed at line ${LINENO}: ${BASH_COMMAND}"' ERR

# -----------------------------------------------------------------------------
# Header
# -----------------------------------------------------------------------------

echo
echo "================================================"
echo " Termux Android Build Environment Setup"
echo "================================================"
echo
echo "Target:"
echo "  Java          : 21"
echo "  Gradle        : ${GRADLE_VERSION}"
echo "  Android API   : ${ANDROID_API}"
echo "  Build Tools   : ${BUILD_TOOLS_VERSION}"
echo "  Architecture  : $(uname -m)"
echo
echo "SDK:"
echo "  ${ANDROID_HOME}"
echo

# -----------------------------------------------------------------------------
# Architecture
# -----------------------------------------------------------------------------

if [ "$(uname -m)" != "aarch64" ]; then
    die "This setup ships an AArch64 AAPT2 and requires an aarch64 Android device."
fi

status "Architecture: aarch64"

# -----------------------------------------------------------------------------
# Step 1 - Termux packages
# -----------------------------------------------------------------------------

info "Updating Termux packages..."

pkg update -y
pkg upgrade -y

status "Termux packages updated"

echo

info "Installing required packages..."

pkg install -y \
    curl \
    wget \
    unzip \
    file \
    openjdk-21

status "Required packages installed"

echo

# -----------------------------------------------------------------------------
# Step 2 - Java
# -----------------------------------------------------------------------------

info "Configuring Java 21..."

if [ ! -x "${JAVA_HOME}/bin/java" ]; then
    die "Java 21 was not found at ${JAVA_HOME}"
fi

export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"

JAVA_VERSION="$(
    "${JAVA_HOME}/bin/java" -version 2>&1 |
    head -n 1
)"

status "JAVA_HOME=${JAVA_HOME}"
status "${JAVA_VERSION}"

echo

# -----------------------------------------------------------------------------
# Step 3 - Android SDK
# -----------------------------------------------------------------------------

info "Configuring Android SDK..."

mkdir -p "${ANDROID_HOME}"

export ANDROID_HOME
export ANDROID_SDK_ROOT

SDKMANAGER="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "${SDKMANAGER}" ]; then

    info "Android SDK command-line tools not found."

    SDK_INSTALLER="${HOME_DIR}/install-android-sdk.sh"

    curl \
        --fail \
        --location \
        --retry 5 \
        --connect-timeout 15 \
        -o "${SDK_INSTALLER}" \
        "https://raw.githubusercontent.com/Sohil876/termux-sdk-installer/main/installer.sh"

    chmod +x "${SDK_INSTALLER}"

    bash "${SDK_INSTALLER}" -i

    rm -f "${SDK_INSTALLER}"
fi

if [ ! -x "${SDKMANAGER}" ]; then
    die "sdkmanager was not found at ${SDKMANAGER}"
fi

export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

status "Android SDK found"

echo

# -----------------------------------------------------------------------------
# Step 4 - SDK licenses
# -----------------------------------------------------------------------------

info "Accepting Android SDK licenses..."

yes | "${SDKMANAGER}" --licenses >/dev/null 2>&1 || true

status "SDK licenses accepted"

echo

# -----------------------------------------------------------------------------
# Step 5 - Android API 36
# -----------------------------------------------------------------------------

info "Installing Android Platform API ${ANDROID_API}..."

"${SDKMANAGER}" "platforms;android-${ANDROID_API}"

status "Android Platform API ${ANDROID_API} installed"

echo

# -----------------------------------------------------------------------------
# Step 6 - Build Tools 36
# -----------------------------------------------------------------------------

info "Installing Android Build Tools ${BUILD_TOOLS_VERSION}..."

"${SDKMANAGER}" "build-tools;${BUILD_TOOLS_VERSION}"

if [ ! -d "${BUILD_TOOLS_DIR}" ]; then
    die "Build Tools directory was not created: ${BUILD_TOOLS_DIR}"
fi

status "Build Tools ${BUILD_TOOLS_VERSION} installed"

echo

# -----------------------------------------------------------------------------
# Step 7 - Native AAPT2
# -----------------------------------------------------------------------------

info "Installing native AArch64 AAPT2..."

mkdir -p "${BUILD_TOOLS_DIR}"

TMP_AAPT2="${HOME_DIR}/aapt2-aarch64.tmp"

rm -f "${TMP_AAPT2}"

curl \
    --fail \
    --location \
    --retry 5 \
    --connect-timeout 15 \
    -o "${TMP_AAPT2}" \
    "${AAPT2_URL}"

chmod +x "${TMP_AAPT2}"

# Verify the downloaded binary before installing it.
AAPT2_TYPE="$(file "${TMP_AAPT2}")"

echo "  ${AAPT2_TYPE}"

if ! echo "${AAPT2_TYPE}" | grep -q "aarch64"; then
    rm -f "${TMP_AAPT2}"
    die "Downloaded AAPT2 is not an AArch64 binary"
fi

mv -f "${TMP_AAPT2}" "${AAPT2_PATH}"

chmod 755 "${AAPT2_PATH}"

if ! "${AAPT2_PATH}" version >/dev/null 2>&1; then
    die "Native AAPT2 cannot be executed"
fi

AAPT2_VERSION="$(
    "${AAPT2_PATH}" version 2>&1 |
    head -n 1
)"

status "Native AAPT2 installed"
status "${AAPT2_VERSION}"

# Also expose it under a distinct name so it never gets confused with
# the stock Termux aapt2 package.
install -m 755 \
    "${AAPT2_PATH}" \
    "${TERMUX_PREFIX}/bin/aapt2-aosp"

echo

# -----------------------------------------------------------------------------
# Step 8 - Gradle 9.6.0
# -----------------------------------------------------------------------------

info "Installing Gradle ${GRADLE_VERSION}..."

CURRENT_GRADLE_VERSION=""

if [ -x "${GRADLE_BIN}" ]; then
    CURRENT_GRADLE_VERSION="$(
        "${GRADLE_BIN}" --version 2>/dev/null |
        awk '/^Gradle / { print $2; exit }'
    )"
fi

if [ "${CURRENT_GRADLE_VERSION}" = "${GRADLE_VERSION}" ]; then

    status "Gradle ${GRADLE_VERSION} already installed"

else

    GRADLE_ZIP="${ANDROID_HOME}/gradle-${GRADLE_VERSION}-bin.zip"
    TMP_GRADLE="${ANDROID_HOME}/.gradle-${GRADLE_VERSION}-tmp"

    rm -rf "${TMP_GRADLE}"

    if [ ! -f "${GRADLE_ZIP}" ]; then

        info "Downloading Gradle ${GRADLE_VERSION}..."

        curl \
            --fail \
            --location \
            --retry 5 \
            --connect-timeout 15 \
            -o "${GRADLE_ZIP}" \
            "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

    else

        info "Using existing Gradle archive"

    fi

    mkdir -p "${TMP_GRADLE}"

    unzip -q \
        "${GRADLE_ZIP}" \
        -d "${TMP_GRADLE}"

    if [ ! -d "${TMP_GRADLE}/gradle-${GRADLE_VERSION}" ]; then
        die "Invalid Gradle archive"
    fi

    rm -rf "${GRADLE_DIR}"

    mv \
        "${TMP_GRADLE}/gradle-${GRADLE_VERSION}" \
        "${GRADLE_DIR}"

    rm -rf "${TMP_GRADLE}"
    rm -f "${GRADLE_ZIP}"

    status "Gradle ${GRADLE_VERSION} installed"
fi

export PATH="${GRADLE_DIR}/bin:${PATH}"

INSTALLED_GRADLE="$(
    "${GRADLE_BIN}" --version |
    awk '/^Gradle / { print $2; exit }'
)"

if [ "${INSTALLED_GRADLE}" != "${GRADLE_VERSION}" ]; then
    die "Expected Gradle ${GRADLE_VERSION}, got ${INSTALLED_GRADLE}"
fi

status "Gradle ${INSTALLED_GRADLE}"

echo

# -----------------------------------------------------------------------------
# Step 9 - Gradle configuration
# -----------------------------------------------------------------------------

info "Configuring Gradle for Termux..."

mkdir -p "${HOME_DIR}/.gradle"

if [ -f "${GRADLE_PROPERTIES}" ]; then

    BACKUP="${GRADLE_PROPERTIES}.bak.$(date +%Y%m%d%H%M%S)"

    cp \
        "${GRADLE_PROPERTIES}" \
        "${BACKUP}"

    info "Existing gradle.properties backed up to:"
    echo "  ${BACKUP}"

fi

# Remove only settings managed by this script.
if [ -f "${GRADLE_PROPERTIES}" ]; then

    sed -i \
        '/^[[:space:]]*android\.aapt2FromMavenOverride[[:space:]]*=/d' \
        "${GRADLE_PROPERTIES}"

    sed -i \
        '/^[[:space:]]*org\.gradle\.java\.home[[:space:]]*=/d' \
        "${GRADLE_PROPERTIES}"

fi

cat >> "${GRADLE_PROPERTIES}" <<EOF

# Termux Android build environment
# Managed by setup.sh

android.aapt2FromMavenOverride=${AAPT2_PATH}
org.gradle.java.home=${JAVA_HOME}
EOF

status "Gradle configured"
echo "  AAPT2: ${AAPT2_PATH}"
echo "  Java : ${JAVA_HOME}"

echo

# -----------------------------------------------------------------------------
# Step 10 - Shell environment
# -----------------------------------------------------------------------------

info "Configuring shell environment..."

SHELL_RC=""

case "${SHELL:-}" in
    */zsh)
        SHELL_RC="${HOME_DIR}/.zshrc"
        ;;
    */bash)
        SHELL_RC="${HOME_DIR}/.bashrc"
        ;;
esac

if [ -z "${SHELL_RC}" ]; then
    # Termux commonly starts with bash even when $SHELL isn't populated.
    SHELL_RC="${HOME_DIR}/.bashrc"
fi

touch "${SHELL_RC}"

START_MARKER="# >>> Termux Android Build Environment >>>"
END_MARKER="# <<< Termux Android Build Environment <<<"

# Remove a previous block written by this script.
sed -i \
    "/^${START_MARKER}$/,/^${END_MARKER}$/d" \
    "${SHELL_RC}"

cat >> "${SHELL_RC}" <<EOF

${START_MARKER}
export ANDROID_HOME="${ANDROID_HOME}"
export ANDROID_SDK_ROOT="\$ANDROID_HOME"
export JAVA_HOME="${JAVA_HOME}"
export PATH="\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/gradle/bin:\$PATH"
export JAVA_OPTS="-Djava.net.preferIPv4Stack=true"
${END_MARKER}
EOF

status "Environment added to ${SHELL_RC}"

echo

# -----------------------------------------------------------------------------
# Step 11 - Verification
# -----------------------------------------------------------------------------

info "Running final checks..."

export ANDROID_HOME
export ANDROID_SDK_ROOT
export JAVA_HOME

export PATH="${JAVA_HOME}/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/gradle/bin:${PATH}"

echo
echo "Java:"
"${JAVA_HOME}/bin/java" -version 2>&1 | head -n 1

echo
echo "Gradle:"
"${GRADLE_BIN}" --version | sed -n '1,8p'

echo
echo "Android SDK:"
echo "  ${ANDROID_HOME}"

echo
echo "Android Platform:"
if [ -f "${ANDROID_HOME}/platforms/android-${ANDROID_API}/android.jar" ]; then
    status "android-${ANDROID_API}/android.jar present"
else
    die "Android API ${ANDROID_API} is missing"
fi

echo
echo "Build Tools:"
if [ -x "${BUILD_TOOLS_DIR}/aapt2" ]; then
    status "${BUILD_TOOLS_VERSION}"
else
    die "Build Tools ${BUILD_TOOLS_VERSION} missing"
fi

echo
echo "Native AAPT2:"
"${AAPT2_PATH}" version

echo
echo "AAPT2 architecture:"
file "${AAPT2_PATH}"

echo
echo "================================================"
echo -e "${GREEN} Setup completed successfully!${NC}"
echo "================================================"
echo
echo "Installed:"
echo "  Java        : 21"
echo "  Gradle      : ${GRADLE_VERSION}"
echo "  Android API : ${ANDROID_API}"
echo "  Build Tools : ${BUILD_TOOLS_VERSION}"
echo "  AAPT2       : native AArch64"
echo
echo "Environment:"
echo "  ANDROID_HOME: ${ANDROID_HOME}"
echo "  JAVA_HOME   : ${JAVA_HOME}"
echo
echo "AAPT2 override:"
echo "  ${AAPT2_PATH}"
echo
echo "Reload your shell:"
echo
echo "  source ${SHELL_RC}"
echo
echo "Then verify:"
echo
echo "  gradle --version"
echo "  ${AAPT2_PATH} version"
echo
echo "For an Android project:"
echo
echo "  ./gradlew assembleDebug"
echo
