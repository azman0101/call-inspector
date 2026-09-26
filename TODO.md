# TODO

Follow-ups collected from the descriptions of recently closed PRs (#11, #13, #15, #16, #17).
Only #16 and #17 contained open items.

## Open

### APK signing (from #17)

- [ ] **Make the debug keystore persistent too.**
  `debug.keystore` is also regenerated on every CI run, because there is no
  `debug.keystore.base64` in the repo. Debug APKs from different runs therefore can't update
  each other. Commit a `debug.keystore.base64`, which the workflow already supports, or provide it through a secret.

### Package / app identity (from #16)

- [ ] **Migrate existing installs to the new `applicationId`.**
  `applicationId` changed from `com.aistudio.operatorlookup.wkvqmt` to `net.slashetc.callinspector`.
  Android treats this as a different app: it installs alongside the old one and does not
  inherit its data (call notes, favorites, spam flags). Uninstall the old app once the new one
  is set up.

### Development environment (from #16 and #17)

- [ ] **Allow Gradle to build in Claude Code cloud sessions (optional).**
  The session's network policy blocks `dl.google.com` (Google Maven), so the Android Gradle
  Plugin can't be downloaded and `./gradlew` can't run there. Changes could only be validated
  by CI. Allowing that host in the environment's network policy would enable local builds and tests.

## Resolved (kept for context)

- [x] Release APK is signed again: on main run #63 (after #17), `validateSigningRelease`
  and `packageRelease` succeeded and the throwaway-key warning was emitted as expected.
- [x] Package rename compiles and tests pass: CI "Build & Test Android APK" on #16 succeeded.
- [x] Persistent upload keystore: the `KEYSTORE_BASE64`, `STORE_PASSWORD` and `KEY_PASSWORD`
  secrets are set. On main run #67 (attempt 2), the log shows
  `Restoring my-upload-key.jks from secrets.KEYSTORE_BASE64...`, and `validateSigningRelease` and
  `packageRelease` succeeded. Keep a backup of the keystore outside the repo: without it, no
  future APK can update existing installs.
