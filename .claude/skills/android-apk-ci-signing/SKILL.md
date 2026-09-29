---
name: android-apk-ci-signing
description: Set up, debug or harden a CI workflow (GitHub Actions or similar) that builds and signs Android APKs, and fix keystore / keytool / apksigner problems. Use this whenever the user mentions building an APK in CI, signingConfig, debug or release/upload keys, keystores (.jks, .keystore, .p12), keytool errors ("Alias does not exist", "keystore password was incorrect", "Cannot recover key"), KEYSTORE_BASE64-style secrets, app-release-unsigned.apk, validateSigningRelease failures, INSTALL_FAILED_UPDATE_INCOMPATIBLE, verifying an APK signature, or versionCode/caching of an Android CI build, even if they do not say "skill" or "signing" explicitly.
---

# Android APK build & signing in CI

Signing is where Android CI setups fail silently: the workflow goes green, the artifact is
uploaded, and the APK turns out to be unsigned, signed with a throwaway key, or signed with a
key that cannot update the installed app. This skill is about making every one of those failures
loud, and about the keytool traps that cause them.

Reference implementation in this repository (read it when working here, copy the ideas elsewhere):
`.github/workflows/build_apk.yml`, `app/build.gradle.kts` (signingConfigs, androidComponents),
`tools/generate-debug-keystore.sh`, `tools/setup-release-environment.sh`,
`tools/set-gradle-encryption-key.sh`.

## 1. The signing model to aim for

Two keys, with different exposure:

| | Debug key | Release (upload) key |
|---|---|---|
| Purpose | Branch / PR APKs that testers sideload | APKs from the main branch (or tags) |
| Stability | Stable across runs (stored as a secret) so a new debug APK updates the previous one | Stable forever: a different key can never update an existing install |
| Exposure | Any run, PRs included | Only runs of the protected branch, via a GitHub **Environment** restricted to that branch |
| Package | `applicationIdSuffix = ".debug"` so it installs next to the release app | Real applicationId |

Why: an APK update is only accepted if it is signed with the same certificate
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE` otherwise). A per-run generated debug key means testers
must uninstall every time; a release key readable from PR builds means any PR can exfiltrate it.

Put the release secrets (`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`) in an environment
whose deployment branch policy allows only the release branch, select it with
`environment: ${{ (github.event_name == 'push' && github.ref == 'refs/heads/main') && 'release' || 'production' }}`,
and delete any repository-level copies once a release build has passed (repository secrets are
readable by every branch's workflow runs).

## 2. Gradle signing config: the traps

```kotlin
// Relative KEYSTORE_PATH resolved from the repo root, where CI decodes it.
val releaseKeystore = rootProject.file(System.getenv("KEYSTORE_PATH") ?: "my-upload-key.jks")
signingConfigs {
  create("release") {
    storeFile = releaseKeystore
    storePassword = System.getenv("STORE_PASSWORD")
    keyAlias = "upload"                      // must match the alias inside the keystore
    keyPassword = System.getenv("KEY_PASSWORD")
  }
}
buildTypes { release { signingConfig = signingConfigs.getByName("release") } }
```

- **`file()` vs `rootProject.file()`**: in `app/build.gradle.kts`, `file("x.jks")` resolves to
  `app/x.jks`. CI usually writes the keystore at the repo root, so `validateSigningRelease` fails
  with "Keystore file ... not found". Use `rootProject.file()`.
- **Never make release signing conditional on the keystore existing.** The tempting "fix"
  (`if (file.exists()) signingConfig = ...`) produces `app-release-unsigned.apk`: CI is green,
  and Android refuses to install the artifact. Let a missing keystore fail the build, or have
  CI create an explicit, warned-about throwaway key (section 4).
- **Alias**: `keyAlias` must be the alias stored in the keystore. The Android debug keystore uses
  `androiddebugkey`; upload keys are often `upload` or `key0`. Check with
  `keytool -list -keystore K -storepass P` (lists aliases). Copying the debug keystore as a
  release fallback fails with `Alias <upload> does not exist`.
- **Values that change per run** (versionCode, versionName, DSNs) should be read lazily
  (`providers.environmentVariable(...)` inside `androidComponents.onVariants`), not with
  `System.getenv()` at configuration time, or every run misses the configuration cache. Set such
  env vars at job level so BuildConfig is identical across test and assemble steps (otherwise
  Kotlin recompiles).

## 3. keytool pitfalls (the ones that cost hours)

1. **PKCS12 is the default keystore type (JDK 9+), and it has one password.** With `-keypass`
   different from `-storepass`, keytool prints "Different store and key passwords not supported
   for PKCS12 KeyStores. Ignoring user-specified -keypass value." The key password *is* the store
   password: set `KEY_PASSWORD` = `STORE_PASSWORD` for such keystores.
2. **keytool does not validate the key password of a PKCS12 keystore.** `keytool -list` and even
   `keytool -certreq -keypass WRONG` succeed, yet Gradle's signing (`KeyStore.getKey`) fails with
   a wrong key password. To really check both passwords before storing secrets, load the key
   the way signing does: `java scripts/CheckKey.java <keystore> <alias>` with the two passwords
   on stdin (exit 0 ok, 2 wrong store password, 3 no such alias, 4 wrong key password).
   For JKS keystores, `keytool -certreq` with a wrong `-keypass` does fail ("Cannot recover key").
3. **Error messages map to causes**:
   - `keystore password was incorrect` → store password (or a corrupted/mis-decoded file: check
     the base64 round trip before blaming the password).
   - `Alias <x> does not exist` → wrong alias, often the debug keystore used as release.
   - `Cannot recover key`, `Get Key failed`, `BadPaddingException` → wrong key password.
   - `Unrecognized keystore format` / `Invalid keystore format` → the file is not a keystore
     (base64 still encoded, PEM or text file); `java.io.EOFException` → truncated file (secret
     cut off, or decoded from a wrapped/partial copy).
4. **keytool cannot read APK Signature Scheme v2+.** `keytool -printcert -jarfile app.apk` prints
   nothing for an APK with `minSdk` ≥ 24 (no v1 signature). Use
   `apksigner verify --print-certs app.apk` (Android build-tools; Termux: `pkg install apksigner`).
   keytool prints fingerprints uppercase with colons, apksigner lowercase without: normalize
   (`tr -d ':' | tr 'A-F' 'a-f'`) before comparing.
5. **Noise and locale in keytool output**: behind a proxy, Java prints
   `Picked up JAVA_TOOL_OPTIONS: ...` on stderr; keytool output is localized
   ("Propriétaire" instead of "Owner"). Filter with `grep -v JAVA_TOOL`, and force English with
   `keytool -J-Duser.language=en ...` before grepping.
6. **Never overwrite an existing keystore.** Scripts that generate keys should refuse if the file
   exists: installs in the wild depend on it, and a lost upload key cannot be regenerated (on
   Google Play only an upload-key reset through Play App Signing recovers; for sideloaded apps
   every user must uninstall).

## 4. Keystores as CI secrets

- Encode on one line and verify the round trip:
  ```sh
  base64 -w 0 upload.jks > upload.jks.b64          # GNU; macOS: base64 -i upload.jks | tr -d '\n'
  [ "$(base64 -d < upload.jks.b64 | sha256sum)" = "$(sha256sum < upload.jks)" ] && echo same
  ```
- Store secrets through stdin, never on a command line (shell history, `ps`):
  `printf '%s' "$B64" | gh secret set KEYSTORE_BASE64 --env release`.
- In the workflow, pass secrets through the step's `env:` and decode with
  `printf '%s' "$KEYSTORE_BASE64" | base64 -d > upload.jks`. Do not interpolate
  `${{ secrets.X }}` directly inside `run:` scripts (log exposure and script injection).
- A secret that is not set evaluates to an empty string (always on fork PRs). Branch on it
  explicitly: restore the key when present; otherwise generate a throwaway key **with the alias
  the Gradle config expects** and emit `::warning::` saying the APK cannot update other installs.
  Silent fallbacks are how unsigned or wrongly signed APKs ship.
- Trim secrets that are API keys (`tr -d '[:space:]'`): a trailing newline pasted into the
  secret breaks authentication in confusing ways.
- Keep keystores out of git: `.gitignore` `*.jks`, `*.keystore`, `*.p12`, `*.b64`, `keys-*/`.

## 5. Workflow blueprint

Order the job so failures happen before anything is published:

1. `permissions: contents: read` at workflow level; widen per job only where needed. Pin
   third-party actions by commit SHA (Dependabot can update SHA pins).
2. Checkout → `actions/setup-java` (JDK the AGP version needs, e.g. 21) →
   `gradle/actions/setup-gradle` (cache; `cache-encryption-key` from a secret created with
   `openssl rand -base64 16` lets it persist the configuration cache, which may contain secrets).
3. Compute version: `versionCode` from `github.run_number` (monotonic), `versionName` with the
   branch and short SHA on non-release builds.
4. Prepare signing (section 4): debug key always, release key only on the release branch.
5. Unit tests (`testDebugUnitTest`), then any dependency/security gate, **before** assembling a
   release APK.
6. Assemble: `assembleDebug` on PRs, `assembleRelease` on the release branch with
   `STORE_PASSWORD`, `KEY_PASSWORD`, `KEYSTORE_PATH` in the step env.
7. Verify the signature before upload (see below), then `actions/upload-artifact`
   with `if-no-files-found: error`.

Signature check step worth adding to any release job:
```sh
apk=$(ls app/build/outputs/apk/release/*.apk | head -1)
case "$apk" in *unsigned*) echo "::error::unsigned APK: $apk"; exit 1;; esac
"$ANDROID_HOME"/build-tools/*/apksigner verify --print-certs "$apk" | tee /dev/stderr \
  | grep -q "SHA-256 digest: $EXPECTED_CERT_SHA256" || { echo "::error::APK not signed with the expected key"; exit 1; }
```
Publish the expected certificate SHA-256 in the README so users can check downloads.

## 6. Diagnosing a failing or misbehaving build

Start from the symptom:

| Symptom | Likely cause | Check |
|---|---|---|
| `validateSigningRelease` fails, "not found" | path resolved from `app/` | `rootProject.file`, `ls` in the step |
| `app-release-unsigned.apk` produced | conditional signingConfig | remove the condition |
| `Alias <upload> does not exist` | wrong keystore or alias | `keytool -list` |
| Build signs locally but not in CI | secret empty (fork PR, wrong environment, branch policy) | log `[ -n "$KEYSTORE_BASE64" ]`, never the value |
| `keystore password was incorrect` right after decoding | multi-line or truncated base64 | sha256 round trip |
| Install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | different signing key than the installed app | `apksigner verify --print-certs` on both |
| `keytool -printcert -jarfile` prints nothing | v2+ only APK | use apksigner |
| Every run reconfigures Gradle | per-run values read at configuration time, or no cache encryption key | section 2 and 5 |

When reporting back, say which key signed the artifact (certificate SHA-256), whether it is the
stable key or a throwaway one, and whether the APK can update an existing install.
