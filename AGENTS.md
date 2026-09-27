# AGENTS Guidelines for Vulnerability & CVE Management

This document sets mandatory guidelines for AI agents and automated tools working on this codebase when handling security vulnerabilities and dependency checks.

## Dependency Security & Suppression Policy

### 1. High and Critical Severity Vulnerabilities (CVSS > 7.0)
- **Do NOT suppress** vulnerabilities with a CVSS score greater than 7.0 (High or Critical severity according to NVD or vendor assessments).
- **Primary Resolution Method**: Vulnerabilities with CVSS > 7.0 must be **fixed directly** by upgrading the affected dependency/version in `gradle/libs.versions.toml` or `build.gradle.kts`, or replacing the vulnerable library if an patch/update is available.

### 2. Requirements Before Suppressing Any CVE
If a CVE or dependency alert cannot be immediately resolved via library update (e.g. verified false positive, CPE version mis-mapping), agents **must** adhere to the following protocol before modifying `config/dependency-check-suppressions.xml`:

1. **Verify Severity & NVD Data**:
   - Check the CVSS score from official sources (NVD / CVE database).
   - If CVSS > 7.0, suppression is strictly forbidden without explicit maintainer authorization.
2. **Document Full Rationale**:
   - Every `<suppress>` entry in `config/dependency-check-suppressions.xml` MUST include an detailed `<notes>` section explaining why the entry is safe or a false positive (e.g., CPE misidentification between library wrapper version and underlying C/C++ engine).
3. **Include Expiration Date**:
   - Every suppression MUST specify a strict `until="YYYY-MM-DDZ"` attribute to ensure periodic re-evaluation.

---

## Testing & Verification
- Run `./gradlew testDebugUnitTest --no-configuration-cache` to ensure changes do not break unit tests.
- Run `./gradlew dependencyCheckAnalyze --no-configuration-cache` (when NVD API key or cache is available) to verify vulnerability reporting.

## Build Environment (Claude Code cloud sessions)
- To prevent HTTP 429 rate limiting when downloading Gradle plugins and dependencies or Robolectric SDK artifacts,
  use the Google Maven Central mirror (`https://maven-central.storage-download.googleapis.com/maven2/`).
  It is declared before `mavenCentral()` in `settings.gradle.kts` (plugins and dependencies), and
  `robolectric.dependency.repo.url` points to it in the unit test options of `app/build.gradle.kts`.
  Keep both when editing repositories, and use the same mirror for any new Maven Central download.
- The Android SDK is expected at `ANDROID_HOME` (`/root/android-sdk` in cloud sessions, with
  `platforms;android-36.1` and `build-tools;36.0.0`).
