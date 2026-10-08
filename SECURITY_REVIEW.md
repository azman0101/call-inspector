# Security review

**Review date:** 2026-10-03

**Reviewed revision:** `25900f3`

**Scope:** Android application, local data storage, embedded web content, network clients,
dependency configuration, release automation, and the ARCEP data-update workflow.

## Executive summary

No confirmed critical or high-severity vulnerability was found in the reviewed source. The
application has a comparatively small attack surface, requests only the call-log and network
permissions it uses, encrypts user-authored data at rest, disables cleartext traffic and Android
backup, and limits exported Android components.

The review identified **two medium** and **two low** risks. The most important improvements are to
constrain the `SignalConsoActivity` WebView more tightly and to stop automatically publishing an
externally sourced ARCEP database without an independent integrity or review gate. Dependency CVE
status remains unverified because NVD cache/API access required by the configured OWASP
Dependency-Check task was not available in the review environment.

| ID | Severity | Area | Status |
| --- | --- | --- | --- |
| SR-01 | Medium | WebView / personal data | Fixed (#58) |
| SR-02 | Medium | Data supply chain / CI | Fixed (#57) |
| SR-03 | Low | External URL dispatch | Open |
| SR-04 | Low | WebView hardening | Open |

## Threat model

### Security-sensitive assets

- Call-log records, caller numbers, and the user's own phone-line numbers.
- User notes, spam/favorite labels, SignalConso report history, and reporter contact details.
- Release-signing keys and CI credentials.
- Integrity of the bundled ARCEP number-allocation database and application updates.
- Diagnostic events sent to Sentry after explicit opt-in.

### Trust boundaries and attacker capabilities

- The Android application sandbox is trusted; other installed applications and content they can
  dispatch through intents are not.
- `signal.conso.gouv.fr`, GitHub's releases API, and `extranet.arcep.fr` are remote trust boundaries.
- Data rendered in the SignalConso WebView, release metadata, ARCEP CSV files, call-log content, and
  user-entered notes are attacker-influenced inputs.
- GitHub Actions dependencies and downloaded build inputs are part of the software supply chain.
- A fully compromised or unlocked device, a compromised upstream service, and vulnerabilities in the
  Android OS/WebView itself are outside the application's ability to prevent, but the application
  should minimize their impact.

## Findings

### SR-01: Personal data is exposed to all script executing in the SignalConso origin — Medium

`SignalConsoActivity` enables JavaScript and writes the report plan and saved reporter profile into
page-global JavaScript variables. It repeats that injection after each completed navigation whose
host exactly equals `signal.conso.gouv.fr`. A script executing in that page's origin—including a
compromised first-party asset or a third-party script included by the page—can read the reporter's
name, email, phone number, reference number, reported number, and report plan. The app has no JavaScript
bridge, which avoids a more dangerous native-code interface, but placing all values in globals makes
the data available for the full lifetime of the document.

**Impact:** compromise of the SignalConso page or one of its script dependencies could disclose call
and contact data supplied by the app. This does not let an arbitrary cross-origin iframe read the
values under the browser same-origin policy.

**Recommendation:** prefer a maintained native-to-web contract owned by SignalConso. If injection must
remain, inject only on the exact HTTPS origin and expected form paths, provide only the fields needed
at the current step, keep values in a closure rather than globals, clear them immediately after use,
and document the remote scripts covered by the trust decision. Add an instrumentation test proving
that no injection occurs for HTTP, look-alike hosts, unexpected paths, redirects, or error pages.

**Status: fixed in #58.**
- **Form path only.** The script is injected only on the report form: HTTPS, exact host and the form's
  path. `SignalConsoReport.isFormUrl` checks the raw path after normalization and refuses encoded
  characters. The script checks `location` again on every DOM change.
- **No data in globals.** The plan is the argument of the script, so it stays in a closure.
- **Contact details at step 4 only.** They are handed over when step 4 asks for them, and kept only
  while it is shown.
- **Dropped after use.** Everything is dropped once the report is sent or the page leaves the form, and
  the WebView is destroyed with the screen.
- **Tests.** JVM tests cover the URL checks: HTTP, look-alike hosts, other paths, `../` and encoded
  escapes.
- **Still open: a contract owned by SignalConso.** The site has a `/{lang}/webview/{path}` mode that
  could serve as a base.

### SR-02: ARCEP data can flow from the network to a release without independent approval — Medium

The scheduled `update_arcep.yml` workflow downloads two CSV files from `extranet.arcep.fr`, generates
the bundled SQLite database, and pushes the result directly to the protected product branch. A push to
that branch starts the release workflow. The generator records SHA-256 hashes, uses parameterized SQL,
and creates a fixed schema, but it does not authenticate expected hashes/signatures, enforce sensible
row-count or file-size bounds, reject malformed required fields, or require a human review before the
new database can be released.

**Impact:** an upstream compromise, DNS/CA failure, or unexpectedly malformed official export could
silently alter operator attribution for every user and trigger publication of that data in a signed
release. The imported strings are treated as data rather than executable SQL, so direct code execution
was not identified.

**Recommendation:** have the scheduled job open a pull request rather than push to the release branch;
require review and normal branch protection; validate content type, download size, required headers,
phone-range syntax, uniqueness, non-empty operator identities, and expected row-count ranges; publish
the complete source hashes in the pull request; and verify an upstream signature or independently
published digest if ARCEP makes one available.

**Status: fixed in #57.**
- **No more scheduled job.** `update_arcep.yml` is removed. The bundled database now changes only
  through a reviewed pull request, after running `tools/update_arcep_db.py` by hand.
- **The app updates its own database.** It checks at first launch, once the terms are accepted, then at
  most weekly. It first asks only for the files' `Last-Modified` dates, and downloads only when they
  changed.
- **Downloaded data is checked before use.** TLS pinning applies, and the download size is bounded. Row
  counts must be plausible (at least 10 000 ranges and 500 operators). The swap is atomic and waits for
  readers. The source SHA-256 are recorded.
- **Still open: an upstream signature or published digest.** ARCEP publishes neither.

### SR-03: Web links can dispatch arbitrary URI schemes — Low

For a main-frame navigation away from `signal.conso.gouv.fr`, the WebView passes the URI unchanged to
an `ACTION_VIEW` intent. A link controlled by the remote page can therefore ask Android to resolve
custom schemes, not only `https` links. Android's resolver and component export rules still apply, and
the application does not add intent flags or explicit components, which limits impact.

**Impact:** a malicious or compromised page could invoke another installed application's deep link,
causing an unexpected cross-application action or exposing the user to intent-handler abuse.

**Recommendation:** allow-list `https` (and, if product requirements demand them, explicitly reviewed
`mailto`/`tel` schemes), reject all other schemes, normalize and validate the URI before dispatch, and
use `Intent.createChooser` when more than one external handler is plausible. Add tests for `intent:`,
`file:`, `content:`, `javascript:`, and custom-scheme links.

### SR-04: The WebView relies on permissive defaults rather than an explicit hardened profile — Low

The WebView enables JavaScript and DOM storage but does not explicitly disable file/content access,
mixed content, multiple windows, geolocation, or password/form-data persistence. Some relevant defaults
are safe on current Android versions, and the network security configuration blocks cleartext traffic,
but relying on defaults makes behavior harder to audit across the supported API 24+ range and future
WebView changes.

**Impact:** this currently represents defense-in-depth rather than a demonstrated exploit. A future
configuration change or WebView behavior difference could increase access available to compromised
web content.

**Recommendation:** explicitly disable file and content access, file-URL cross-origin access,
geolocation, multiple windows, and mixed content; enable Safe Browsing where supported; clear WebView
state when the activity is destroyed if persistence is unnecessary; and add tests asserting the
security-relevant settings.

## Existing controls that were verified

- The manifest disables backup, blocks cleartext traffic through a network security configuration,
  keeps `SignalConsoActivity` private, and protects the exported quick-settings service with Android's
  signature-level `BIND_QUICK_SETTINGS_TILE` permission.
- Reporter contact data, line numbers, report history, and call notes are stored in SQLCipher databases.
  Independent random passphrases are wrapped with AES-256-GCM keys from Android Keystore, and the
  transient passphrase arrays are zeroed after opening each database.
- Database reads use bound parameters and writes use `ContentValues`; no user-controlled SQL
  concatenation was found in the reviewed runtime paths.
- Cleartext traffic is disabled globally. The ARCEP endpoint additionally has certificate pins with
  backup pins and an explicit expiration date.
- Sentry is disabled by default, is closed immediately on opt-out, disables default PII collection,
  and removes common phone/email fields and device identifiers before sending opted-in events.
- Release builds are non-debuggable, minified, and resource-shrunk. The release publishing script
  verifies the signing certificate before publishing, while GitHub Actions are pinned to commit SHAs.
- Dependency-Check is configured to fail at CVSS 7.0, and the sole suppression has a rationale and a
  bounded expiration date. No new suppression was added by this review.

## Dependency and tooling status

- `./gradlew testDebugUnitTest --no-configuration-cache` could not configure because this environment
  has no usable Android SDK installation.
- `./gradlew dependencyCheckAnalyze --no-configuration-cache` reached the analyzer but could not update
  the NVD, hosted-suppression, RetireJS, and CISA feeds (HTTP 403/no valid API-backed cache), then stopped
  rather than producing a report. It was not treated as evidence that dependencies are
  vulnerability-free.
- The dependency declaration and suppression policy were reviewed manually. A current Dependency-Check
  report with an NVD API key/cache remains a release requirement, especially for native SQLCipher and
  WebView-adjacent dependencies.

## Remediation order

1. Address SR-01 and SR-03 together by introducing and testing a single strict WebView navigation and
   injection policy. (SR-01 fixed in #58.)
2. Change the ARCEP update job to a pull-request workflow and add structural/data-volume validation
   (SR-02). (Fixed in #57: the job is removed and the app validates the data it downloads.)
3. Apply explicit WebView settings and lifecycle cleanup (SR-04).
4. Re-run unit tests, Android lint, and OWASP Dependency-Check in the documented Android build
   environment; triage every finding without suppressing CVSS scores greater than 7.0.
