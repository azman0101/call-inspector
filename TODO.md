# TODO

Follow-ups collected from the descriptions of PRs #11 to #36 and from direct commits to `main`
(up to `989be97`). Checked on 2026-09-27.

## Open

### Terms of use (from #29)

- [ ] **Check #29 on a device**:
  - Observatoire > CGU shows the same text as `legal/CGU.md`, formatted (headings, lists, bold, `code`);
  - the telemetry badge ("Activé" / "Désactivé") is readable in light and dark themes.
- [ ] **Change the "Dernière mise à jour" date on every change to `legal/CGU.md`.** It is the version the
  app compares with the accepted one: without a new date, users aren't asked to accept the new text.
- [ ] **Keep the executable bit on `gradlew` and `tools/generate-debug-keystore.sh`.** #29 restored it
  (`100755`) after `9d56dcf` dropped it, as `4a3e621` had before #26. The editor or tool used for direct
  commits loses the mode: check `core.fileMode` there, or fix it with `git update-index --chmod=+x`.

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

- [ ] **Run `CallNotesEncryptionTest` on a device** (from #30):
  `./gradlew connectedDebugAndroidTest --tests '*CallNotesEncryptionTest'`. It checks that notes never
  appear in cleartext in `user_notes_secure.db` and are gone from `arcep_data.db`.
- [ ] **(Idea) Export / import notes.** Since #30, notes no longer follow a device-to-device transfer: their
  database is excluded because its Keystore key never leaves the phone. An explicit export (already in the
  README roadmap) would let users move them to a new phone.
- [ ] **Upgrade `sqlcipher-android` when possible.** It is pinned at 4.17.0 because 4.19.0 doesn't build
  against compile SDK 36 (found on the phase 2 branch). Check the next releases, and the OWASP report,
  which showed 0 vulnerabilities on the #27 CI runs.

### Security audit follow-ups

- [ ] **Find the audit items M4 and M5.** Commits exist for M1 (build hardening), M2 (encrypted storage,
  #27 and #30), M3 (TLS pinning and update hardening, #22 and #23) and M6 (dependency scan), but none for
  M4 and M5. The source audit document isn't in the repo.
- [x] ~~**Biometric app lock.**~~ Dropped on 2026-10-03 by the maintainer: the phone app already shows the
  call log without authentication, so locking this app would not protect it.
- [ ] **Renew the ARCEP TLS pins before 2027-09-25** (from #22). After that date Android ignores the
  pin-set and updates keep working without pinning (fail-open by design).
- [ ] **Check an ARCEP update on a device** (from #23): "Vérifier et actualiser la base ARCEP" still
  succeeds, and the stats show about 20 600 ranges afterwards.

### Telemetry (from #26)

- [ ] **Check the opt-out on a device**: with "Rapports techniques d'anomalies" off, no events,
  transactions or sessions reach Sentry; turned back on, the "Info Opérateur initialisé" startup log
  shows up again.

### Build and CI (from #31 to #36)

- [ ] **Decide on #36 (Dependabot, GitHub Actions majors).** checkout 7, setup-java 6, upload-artifact 7 and
  setup-python 7 are fine. `gradle/actions/setup-gradle` 6 is not: its default cache is the proprietary
  `gradle-actions-caching` component (Gradle Terms of Use; free for public repositories, "Free Preview" for
  private ones), and its open-source `basic` mode drops `gradle-home-cache-includes` (the NVD database
  cache) and restore keys. Take setup-gradle 5.0.2 instead (MIT, Node 24, same inputs as 4.4.3), and add
  an `ignore` rule for `gradle/actions >= 6` to `.github/dependabot.yml`. The four other bumps also clear
  GitHub's Node.js 20 deprecation warning.
- [ ] **Replace `material-icons-extended`.** About 50 icons are used, 25 of them outside
  `material-icons-core`, but the library makes `classes.dex` 44 MB in the debug APK and is the main input
  of the slowest remaining task on main, `minifyReleaseWithR8` (about 3 min 15). Copy the used icons as
  local `ImageVector`s (Apache 2.0, keep the header) and drop the dependency.
- [ ] **Confirm the pull request speed-up on the next PR.** Since #35, main restores the configuration
  cache ("Reusing configuration cache") and the debug dexing (`mergeExtDexDebug` 2.2 s instead of
  2 min 48, main job about 7 min instead of 12). The next PR run should show the same; its job summary
  lists the slowest tasks (`tools/gradle_profile_summary.py`).
- [ ] **Check `update_arcep.yml` with pinned actions** (from #34) on its next scheduled or manual run.
- [ ] **(Optional) Install the Android SDK in a SessionStart hook for cloud sessions.** Since #31, cloud
  sessions download Maven Central artifacts from Google's mirror, so parallel builds no longer hit
  `429 Too Many Requests` (`AGENTS.md`); each new container still needs the SDK (`platforms;android-36.1`,
  `build-tools;36.0.0`) in `/root/android-sdk`.

### Going public (security review of 2026-09-27)

A gitleaks scan of all 134 commits (34 branches, 32 pull request refs) and of the working tree found no
secret; keystores, `.env` and `google-services.json` were never committed.

- [ ] **Turn on the repository's security settings when it goes public**: secret scanning and push
  protection, Dependabot alerts, private vulnerability reporting, a protection rule on `main` (pull request
  and green CI required), and approval for workflows from outside contributors (Settings > Actions).
- [ ] **Rate-limit the Sentry project.** Its DSN is in the manifests and `SentryHelper.FALLBACK_DSN`, public
  by design: turn on spike protection and inbound filters so that nobody can spend the quota.
- [ ] **Delete stale branches** (Jules, Copilot and merged branches) before publishing, and consider
  "Automatically delete head branches" in the repository settings.
- [ ] **Keep the author email private from now on** ("Keep my email addresses private" and the noreply
  address). 112 commits already carry `azman0101@gmail.com`; rewriting history is not worth it.
- [ ] **Check who can read the `copilot` environment** (it holds no secret today).
- [ ] **Check #33 on a device**: in demo mode, Sophie Martin, Cabinet Médical and Alexandre D. appear on
  the fictional numbers (01 99 00 01 34, 06 39 98 01 12, 06 39 98 05 67), without an operator.
- [ ] **Check #31 on a device**: notes, favorites and the SignalConso profile are still read after updating
  (Room was dropped and `androidx.sqlite` is now a direct dependency, same version 2.6.2).

## Resolved (kept for context)

- [x] Release APK is signed again: on main run #63 (after #17), `validateSigningRelease`
  and `packageRelease` succeeded and the throwaway-key warning was emitted as expected.
- [x] Package rename compiles and tests pass: CI "Build & Test Android APK" on #16 succeeded.
- [x] Persistent upload keystore: the `KEYSTORE_BASE64`, `STORE_PASSWORD` and `KEY_PASSWORD`
  secrets are set (since #35, only in the `release` environment). On main run #67 (attempt 2), the log shows
  `Restoring my-upload-key.jks from secrets.KEYSTORE_BASE64...`, and `validateSigningRelease` and
  `packageRelease` succeeded. Keep a backup of the keystore outside the repo: without it, no
  future APK can update existing installs.
- [x] Persistent debug keystore (#24): `DEBUG_KEYSTORE_BASE64` is set, and debug APKs are signed
  `CN=slashetc.net debug`, SHA-256 `bf094faa…2f554045`. They install next to the release app as
  `net.slashetc.callinspector.debug`, and the release key is only restored on pushes to `main`.
- [x] Gradle can build in Claude Code cloud sessions: `dl.google.com` was allowed in the environment's
  network policy, and since #31 Maven Central artifacts come from Google's mirror there
  (`CLAUDE_CODE_REMOTE=true`), so builds no longer need `--max-workers=1`.
- [x] ARCEP TLS pinning (#22) and update hardening (#23), including the `GlobalSign Root CA - R3`
  backup pin, confirmed from the Debian CA bundle, and backup pins for Root R6, R46 and E46.
- [x] Call history search by number without spaces, and manual lookup by operator name (#25).
- [x] Terms of use single-sourced (#29): `legal/CGU.md` is bundled as an asset and rendered in the app;
  the text matches what the app does. On a device, the terms block the app on first launch until
  "J'accepte", "Quitter l'application" closes it, and they aren't asked again on the next launch.
- [x] Call notes, favorites and spam flags encrypted (M2, #30): moved from the cleartext `call_notes` table
  of `arcep_data.db` to `user_notes_secure.db` (SQLCipher, own Android Keystore key), excluded from backups
  and device transfers. Upgrading over a version with existing notes kept them (checked on a device).
- [x] `security/phase-2-encrypted-storage` branch deleted (superseded by #30, which encrypts only the user's
  data). Its tip was `ad7f7de` ("ci: run build checks only for pull requests"); to restore it:
  `git push origin ad7f7de6e06090a642bc4a9ede67e2d5970cded7:refs/heads/security/phase-2-encrypted-storage`.
- [x] Unused Room, Moshi codegen and KSP removed; the Sentry Gradle plugin no longer sends its own
  telemetry (#31).
- [x] CI build time (#31, #32): Kotlin compiles once per run (`SENTRY_DSN` set for the whole job); the
  version and DSN are read as lazy providers, so the configuration cache survives across runs (saved with
  the `GRADLE_ENCRYPTION_KEY` secret, `tools/set-gradle-encryption-key.sh`); the pull request OWASP scan
  runs in its own job; main also builds the debug APK so pull requests restore its dexing; every build
  writes its slowest tasks to the job summary. Main went from about 12 to 7 minutes.
- [x] Release signing secrets only in the `release` environment, restricted to `main` (#35,
  `tools/setup-release-environment.sh`); the repository-level copies were deleted. Main run 130 signed
  the release APK from it (`Restoring my-upload-key.jks from secrets.KEYSTORE_BASE64...`, no throwaway
  key), with `SENTRY_DSN` and `NVD_API_KEY` from the same environment.
- [x] GitHub Actions pinned to commit SHAs, updated weekly by Dependabot (#34).
- [x] Unit tests no longer send events to the project's Sentry, and the demo contacts with invented names
  use ARCEP's fiction ranges (#33).
- [x] Telemetry scope decided (after #26): diagnostics stay opt-in and off by default, so nothing is sent.
  Once enabled, error reports, performance traces and session data are sent with a random installation id,
  i.e. pseudonymous data without personal information; `legal/CGU.md` (article 6, #29) says so.
- [x] Telemetry opt-out stops every Sentry path (#26). Since `9d56dcf`, telemetry is opt-in
  (off by default), which settles the "opt-out by default" point raised in #26.
