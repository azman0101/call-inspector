#!/usr/bin/env bash
#
# Prepares an Ubuntu machine (22.04 / 24.04, x86_64) so that a coding agent, or a
# human, can build and test this project right away: JDK 21, Android SDK
# (platforms;android-36.1, build-tools;36.0.0), Python 3 + sqlite3 for
# tools/update_arcep_db.py, local.properties, and a Gradle warm-up that downloads
# every dependency and the Robolectric android-all jar the unit tests need.
#
# Idempotent: what is already installed is kept, only what is missing is fetched.
#
# Usage:
#   tools/setup-agent-ubuntu.sh [--check] [--no-warmup]
#     --check      only report what is missing, change nothing
#     --no-warmup  skip the Gradle warm-up (testDebugUnitTest)
#
# Environment:
#   ANDROID_HOME   SDK location (default: existing ANDROID_HOME/ANDROID_SDK_ROOT,
#                  else /root/android-sdk as root, else ~/Android/Sdk)

set -Eeuo pipefail

readonly ANDROID_PLATFORM="platforms;android-36.1"
readonly ANDROID_BUILD_TOOLS="build-tools;36.0.0"
readonly JAVA_MAJOR="21"
# Android command-line tools 23.0 (repository2-3.xml).
readonly CMDLINE_TOOLS_ZIP="commandlinetools-linux-16111833_latest.zip"
readonly CMDLINE_TOOLS_SHA1="e025545c62a8e64c7559119566a569fb1dec5f60"

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

CHECK_ONLY=false
WARMUP=true
for arg in "$@"; do
    case "${arg}" in
        --check) CHECK_ONLY=true ;;
        --no-warmup) WARMUP=false ;;
        -h|--help) sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: ${arg}" >&2; exit 2 ;;
    esac
done

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
status() { echo -e "${GREEN}[✓]${NC} $*"; }
info() { echo -e "${CYAN}[i]${NC} $*"; }
warn() { echo -e "${YELLOW}[!]${NC} $*"; }
die() { echo -e "${RED}[✗]${NC} $*" >&2; exit 1; }
trap 'echo -e "${RED}[✗]${NC} Failed at line ${LINENO}: ${BASH_COMMAND}" >&2' ERR

MISSING=0
missing() { warn "Missing: $*"; MISSING=$((MISSING + 1)); }

SUDO=""
if [ "$(id -u)" -ne 0 ]; then
    command -v sudo >/dev/null || die "Run as root or install sudo."
    SUDO="sudo"
fi

[ -r /etc/os-release ] && . /etc/os-release
[ "${ID:-}" = "ubuntu" ] || warn "This script targets Ubuntu (found: ${PRETTY_NAME:-unknown})."
[ "$(uname -m)" = "x86_64" ] || warn "The Android SDK build-tools are x86_64 binaries (found: $(uname -m)); on ARM64 Termux use setup.sh."

# -----------------------------------------------------------------------------
# 1. System packages
# -----------------------------------------------------------------------------

java_major() {
    "$1" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ { print $2; exit }'
}

find_java_home() {
    local candidate
    for candidate in "${JAVA_HOME:-}" /usr/lib/jvm/java-${JAVA_MAJOR}-openjdk-* /usr/lib/jvm/temurin-${JAVA_MAJOR}-*; do
        if [ -n "${candidate}" ] && [ -x "${candidate}/bin/javac" ] && [ "$(java_major "${candidate}/bin/java")" = "${JAVA_MAJOR}" ]; then
            echo "${candidate}"
            return
        fi
    done
}

APT_PACKAGES=()
JAVA_HOME_FOUND="$(find_java_home)"
[ -n "${JAVA_HOME_FOUND}" ] || APT_PACKAGES+=("openjdk-${JAVA_MAJOR}-jdk-headless")
for tool in curl:curl unzip:unzip git:git python3:python3 sqlite3:sqlite3; do
    command -v "${tool%%:*}" >/dev/null || APT_PACKAGES+=("${tool##*:}")
done

if [ "${#APT_PACKAGES[@]}" -gt 0 ]; then
    if ${CHECK_ONLY}; then
        missing "apt packages: ${APT_PACKAGES[*]}"
    else
        info "Installing: ${APT_PACKAGES[*]}"
        ${SUDO} apt-get update -qq
        DEBIAN_FRONTEND=noninteractive ${SUDO} apt-get install -y -qq --no-install-recommends ca-certificates "${APT_PACKAGES[@]}"
        JAVA_HOME_FOUND="$(find_java_home)"
    fi
fi

if [ -n "${JAVA_HOME_FOUND}" ]; then
    export JAVA_HOME="${JAVA_HOME_FOUND}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
    status "JDK ${JAVA_MAJOR}: ${JAVA_HOME}"
elif ! ${CHECK_ONLY}; then
    die "JDK ${JAVA_MAJOR} not found after installation."
fi

# -----------------------------------------------------------------------------
# 2. Android SDK
# -----------------------------------------------------------------------------

if [ -z "${ANDROID_HOME:-}" ]; then
    if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
        ANDROID_HOME="${ANDROID_SDK_ROOT}"
    elif [ "$(id -u)" -eq 0 ]; then
        ANDROID_HOME="/root/android-sdk"
    else
        ANDROID_HOME="${HOME}/Android/Sdk"
    fi
fi
export ANDROID_HOME ANDROID_SDK_ROOT="${ANDROID_HOME}"

SDKMANAGER="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "${SDKMANAGER}" ]; then
    if ${CHECK_ONLY}; then
        missing "Android command-line tools in ${ANDROID_HOME}"
    else
        info "Downloading Android command-line tools..."
        tmp="$(mktemp -d)"
        curl --fail --location --retry 4 --retry-delay 2 -sS -o "${tmp}/tools.zip" \
            "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"
        echo "${CMDLINE_TOOLS_SHA1}  ${tmp}/tools.zip" | sha1sum -c --quiet - || die "Checksum mismatch for ${CMDLINE_TOOLS_ZIP}"
        unzip -q "${tmp}/tools.zip" -d "${tmp}"
        mkdir -p "${ANDROID_HOME}/cmdline-tools"
        rm -rf "${ANDROID_HOME}/cmdline-tools/latest"
        mv "${tmp}/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest"
        rm -rf "${tmp}"
    fi
fi

SDK_PACKAGES=()
[ -f "${ANDROID_HOME}/platforms/android-36.1/android.jar" ] || SDK_PACKAGES+=("${ANDROID_PLATFORM}")
[ -x "${ANDROID_HOME}/build-tools/36.0.0/aapt2" ] || SDK_PACKAGES+=("${ANDROID_BUILD_TOOLS}")
[ -x "${ANDROID_HOME}/platform-tools/adb" ] || SDK_PACKAGES+=("platform-tools")

if [ "${#SDK_PACKAGES[@]}" -gt 0 ]; then
    if ${CHECK_ONLY}; then
        missing "Android SDK packages: ${SDK_PACKAGES[*]}"
    else
        info "Installing SDK packages: ${SDK_PACKAGES[*]}"
        yes | "${SDKMANAGER}" --sdk_root="${ANDROID_HOME}" --licenses >/dev/null 2>&1 || true
        "${SDKMANAGER}" --sdk_root="${ANDROID_HOME}" "${SDK_PACKAGES[@]}" >/dev/null
    fi
fi
[ "${#SDK_PACKAGES[@]}" -eq 0 ] || ${CHECK_ONLY} || status "Android SDK packages installed"
${CHECK_ONLY} || status "Android SDK: ${ANDROID_HOME}"

# -----------------------------------------------------------------------------
# 3. Project and shell configuration
# -----------------------------------------------------------------------------

LOCAL_PROPERTIES="${REPO_ROOT}/local.properties"
if ! grep -qs "^sdk.dir=${ANDROID_HOME}$" "${LOCAL_PROPERTIES}"; then
    if ${CHECK_ONLY}; then
        missing "sdk.dir in local.properties"
    else
        # local.properties is git-ignored; keep any other key it already holds.
        touch "${LOCAL_PROPERTIES}"
        sed -i '/^sdk\.dir=/d' "${LOCAL_PROPERTIES}"
        echo "sdk.dir=${ANDROID_HOME}" >> "${LOCAL_PROPERTIES}"
        status "local.properties: sdk.dir=${ANDROID_HOME}"
    fi
fi

ENV_BLOCK="export JAVA_HOME=\"${JAVA_HOME:-}\"
export ANDROID_HOME=\"${ANDROID_HOME}\"
export ANDROID_SDK_ROOT=\"\$ANDROID_HOME\"
export PATH=\"\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH\""

if ! ${CHECK_ONLY}; then
    START_MARKER="# >>> call-inspector agent environment >>>"
    END_MARKER="# <<< call-inspector agent environment <<<"
    touch "${HOME}/.bashrc"
    sed -i "/^${START_MARKER}$/,/^${END_MARKER}$/d" "${HOME}/.bashrc"
    printf '%s\n%s\n%s\n' "${START_MARKER}" "${ENV_BLOCK}" "${END_MARKER}" >> "${HOME}/.bashrc"
    status "Environment added to ~/.bashrc"
    # Claude Code SessionStart hooks: variables written there reach later Bash calls.
    if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
        echo "${ENV_BLOCK}" >> "${CLAUDE_ENV_FILE}"
        status "Environment added to CLAUDE_ENV_FILE"
    fi
fi

# -----------------------------------------------------------------------------
# 4. Gradle warm-up
# -----------------------------------------------------------------------------

if ${CHECK_ONLY}; then
    if [ "${MISSING}" -eq 0 ]; then
        status "Environment ready."
        exit 0
    fi
    die "${MISSING} item(s) missing; run without --check to install them."
fi

if ${WARMUP}; then
    info "Gradle warm-up: testDebugUnitTest (downloads dependencies and Robolectric's android-all jar)..."
    (cd "${REPO_ROOT}" && ./gradlew testDebugUnitTest --no-configuration-cache --console=plain -q) \
        || die "Unit tests failed: see app/build/reports/tests/testDebugUnitTest/index.html"
    status "Gradle warm-up done, unit tests pass"
fi

cat <<EOF

================================================================================
 Ready. Notes for agents working on this codebase:
================================================================================
 Project   Android app (Kotlin, Jetpack Compose), single module :app, package
           net.slashetc.callinspector. Looks French numbers up in the ARCEP
           numbering plan, bundled as app/src/main/assets/arcep_data.db.
 Rules     Read AGENTS.md first: never suppress a CVE with CVSS > 7.0; every
           suppression in config/dependency-check-suppressions.xml needs
           <notes> and until="YYYY-MM-DDZ".
 Commands  ./gradlew testDebugUnitTest --no-configuration-cache   unit + Robolectric tests
           ./gradlew assembleDebug                                debug APK
           ./gradlew dependencyCheckAnalyze --no-configuration-cache  (needs NVD_API_KEY)
           python3 tools/update_arcep_db.py --output app/src/main/assets/arcep_data.db
                                                    rebuild the ARCEP DB (needs extranet.arcep.fr)
 Tips      - Inspect the DB with sqlite3 or python3 -c 'import sqlite3'.
           - Robolectric tests (@Config(sdk = [34])) read the real asset DB.
           - Repositories: keep settings.gradle.kts and the Robolectric repo URL in
             app/build.gradle.kts in sync (Maven Central mirror, see AGENTS.md).
           - setup.sh at the repo root is for Termux on ARM64, not for Ubuntu.
 New shell: source ~/.bashrc  (or rely on local.properties for the SDK path)
EOF
