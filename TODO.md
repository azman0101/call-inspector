# TODO

Follow-ups collected from the descriptions of closed PRs #11 to #28 and from direct commits to `main`
(up to `9d56dcf`). Checked on 2026-09-26.

## Open

### Regressions on `main`

- [ ] **Restore the executable bit on `gradlew` and `tools/generate-debug-keystore.sh`.**
  `9d56dcf` (direct push: CGU, telemetry opt-in) changed both from `100755` to `100644` again,
  undoing the fix from #26 (the same change had come with `4a3e621`). CI still passes because it runs
  `chmod +x gradlew`, but a fresh clone can't run `./gradlew` or `./tools/generate-debug-keystore.sh`.
  The editor or tool used for direct commits drops the mode: `git update-index --chmod=+x` on both
  files fixes it, and checking `core.fileMode` in that environment should stop it from recurring.

### Package / app identity (from #16)

- [ ] **Migrate existing installs to the new `applicationId`.**
  `applicationId` changed from `com.aistudio.operatorlookup.wkvqmt` to `net.slashetc.callinspector`.
  Android treats this as a different app: it installs alongside the old one and does not
  inherit its data (call notes, favorites, spam flags). Uninstall the old app once the new one
  is set up.

### SignalConso report (from #27 and #28)

- [ ] **Run the device test plan of #27** with the release APK from main run 36270426095. Only the CI
  line of that plan is checked. Still to test on a device:
  - the report button on received calls; outgoing calls are no longer listed since #28;
  - the reason: the default one ("refus < 60 jours") with its banner, and the renovation reason from a
    note containing "isolation";
  - the operator found by SIRET and selected in step 2, with the explanation banner;
  - "Mes coordonnées" prefilled in step 4, reference number and share choice included; "Effacer";
    the profile kept after restarting the app;
  - rotation, back navigation, external links opening in the browser.
- [ ] **Confirm the report counter on the first real report.** The report is counted when
  SignalConso's acknowledgment ("Votre signalement a été envoyé.") appears. That page was never seen:
  the text comes from the site's JS bundle, and the detection was only tested against a simulated page.
  Expect the toast "Signalement comptabilisé pour ce numéro", then "Signalé 1 fois · dernier le …" on
  the call card and under the report button. Closing the form without sending must count nothing.
- [ ] **Run `ReporterProfileEncryptionTest` on a device**:
  `./gradlew connectedDebugAndroidTest --tests '*ReporterProfileEncryptionTest'`.
  It checks that the profile never appears in cleartext in the SQLCipher database files. CI only runs
  JVM tests; an emulator job would cover instrumented tests.
- [ ] **Watch for SignalConso form changes.** The prefill script relies on the site's labels and field
  names (`input[name=phone|identity|name|firstName|…]`, radio labels, `#CompanySearchResult`, the
  acknowledgment text). A redesign of the form would silently stop the prefill or the counter.
- [ ] **Check the #28 device plan**: outgoing calls hidden from the history; incoming, missed, rejected
  and blocked calls still listed; the demo data has 10 calls and no outgoing one.
- [ ] **(Later) Classify the call's subject from the note with Jev (TypeSafe).** v1 uses keyword
  regexes. A v2 would go through a server-side proxy (the API key must never ship in the APK), sending
  only the user's note, never the number.

### Data encryption (security audit M2)

- [ ] **Encrypt `arcep_data.db`, or at least the call notes.** `security/phase-2-encrypted-storage`
  (SQLCipher + Keystore) was never opened as a PR, so call notes, favorites and spam flags are still
  stored in cleartext in `arcep_data.db`. #27 applied the same scheme to the SignalConso profile only.
  Its `DatabaseKeyStore` is phase 2's `KeyStoreHelper`, parameterized per database, so phase 2 can
  reuse it. The ARCEP tables themselves are public data.
- [ ] **Upgrade `sqlcipher-android` when possible.** It is pinned at 4.17.0 because 4.19.0 doesn't build
  against compile SDK 36 (found on the phase 2 branch). Check the next releases, and the OWASP report,
  which showed 0 vulnerabilities on the #27 CI runs.

### Security audit follow-ups

- [ ] **Find the audit items M4 and M5.** Commits exist for M1 (build hardening), M2 (encrypted storage,
  unmerged), M3 (TLS pinning and update hardening, #22 and #23) and M6 (dependency scan), but none for
  M4 and M5. The source audit document isn't in the repo.
- [ ] **Biometric app lock: intent not found.** No branch, commit, PR or issue mentions biometric
  authentication (searched "biometric", "BiometricPrompt", "empreinte", "fingerprint",
  "authentification"). If it was planned, it may be one of M4/M5; it needs its own issue or PR.
- [ ] **Renew the ARCEP TLS pins before 2027-09-25** (from #22). After that date Android ignores the
  pin-set and updates keep working without pinning (fail-open by design).
- [ ] **Check an ARCEP update on a device** (from #23): "Vérifier et actualiser la base ARCEP" still
  succeeds, and the stats show about 20 600 ranges afterwards.

### Telemetry (from #26)

- [ ] **Check the opt-out on a device**: with "Rapports techniques d'anomalies" off, no events,
  transactions or sessions reach Sentry; turned back on, the "Info Opérateur initialisé" startup log
  shows up again.
- [ ] **Decide on the remaining #26 points:**
  - release-health sessions still carry the installation id when telemetry is on
    (`isEnableAutoSessionTracking = false` would drop them, at the cost of the crash-free rate);
  - `tracesSampleRate = 1.0` in release sends a transaction for every screen or app start;
  - `Sentry.close()` flushes pending events, so an event queued at the moment of opting out may still
    be sent.

### Development environment

- [ ] **Speed up local builds in Claude Code cloud sessions (optional).** `dl.google.com` is now allowed
  and `testDebugUnitTest` ran locally, but each session has to install the Android SDK (command-line
  tools, `platforms;android-36.1`) with `sdkmanager`, and Maven Central answers `429 Too Many Requests`
  to parallel downloads: Gradle needs `--max-workers=1`, and Robolectric's `android-all` jar download
  can fail the same way. A SessionStart hook could install the SDK and warm the Gradle cache.
- [ ] **(Optional) Turn off the Sentry Gradle plugin's own telemetry.** During local builds it tries to
  reach `ingest.sentry.io`; the sandbox blocks it, harmlessly.

## Resolved (kept for context)

- [x] Release APK is signed again: on main run #63 (after #17), `validateSigningRelease`
  and `packageRelease` succeeded and the throwaway-key warning was emitted as expected.
- [x] Package rename compiles and tests pass: CI "Build & Test Android APK" on #16 succeeded.
- [x] Persistent upload keystore: the `KEYSTORE_BASE64`, `STORE_PASSWORD` and `KEY_PASSWORD`
  secrets are set. On main run #67 (attempt 2), the log shows
  `Restoring my-upload-key.jks from secrets.KEYSTORE_BASE64...`, and `validateSigningRelease` and
  `packageRelease` succeeded. Keep a backup of the keystore outside the repo: without it, no
  future APK can update existing installs.
- [x] Persistent debug keystore (#24): `DEBUG_KEYSTORE_BASE64` is set, and debug APKs are signed
  `CN=slashetc.net debug`, SHA-256 `bf094faa…2f554045`. They install next to the release app as
  `net.slashetc.callinspector.debug`, and the release key is only restored on pushes to `main`.
- [x] Gradle can build in Claude Code cloud sessions: `dl.google.com` was allowed in the environment's
  network policy (see the open item above for what each session still has to set up).
- [x] ARCEP TLS pinning (#22) and update hardening (#23), including the `GlobalSign Root CA - R3`
  backup pin, confirmed from the Debian CA bundle, and backup pins for Root R6, R46 and E46.
- [x] Call history search by number without spaces, and manual lookup by operator name (#25).
- [x] Telemetry opt-out stops every Sentry path (#26). Since `9d56dcf`, telemetry is opt-in
  (off by default), which settles the "opt-out by default" point raised in #26.
