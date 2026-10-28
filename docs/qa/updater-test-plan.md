# QA test plan — GitHub updates

Status: **test sources and contracts are frozen; the matching release-variant native run has completed**. See [0.4.3 validation](updater-validation-043.md) for observed results and remaining gaps. Helena reviewed source and existing artifacts; Clara operated Gradle, the TV, and GitHub. This plan does not authorize publishing or installing a fabricated future version.

Scope: About can check the public repository and offer a newer release for explicit download, validation, and Android-confirmed installation while preserving settings and Home. The historical live check ran installed 0.4.3/code 29 against public release 0.4.2 and correctly reported no update. Publishing 0.4.3 afterward does not retroactively turn that check into an installation test.

## Instrumentation variant

The installed target is a release build. The module sets `testBuildType = "release"`; build the auxiliary APK with `assembleReleaseAndroidTest` from `src/android`. JVM tests continue to use `testDebugUnitTest`. Clara runs and installs the auxiliary APK.

```powershell
.\gradlew.bat testDebugUnitTest verifyRulesCoverage assembleRelease assembleReleaseAndroidTest lintRelease
```

The first native attempt stopped before the preference snapshot block with `NoSuchMethodError` for `UpdateInstaller.currentHome$app_debug`, while the installed app exposed `$app_release`. This was a test-artifact variant mismatch, not evidence of an updater defect. Other Kotlin `internal` calls, including `parseReleases`, require the same matching variant. Do not add public production hooks or manually resolve mangled method names to bypass this mismatch. Rebuilding the release instrumentation and repeating the complete run resolved it; independent device checks confirmed the original settings remained intact.

## Frozen contracts

- The synchronous client runs off the UI thread, with injectable connections, DNS resolution, and a monotonic clock. Cancellation must not block the UI. The Activity controller bounds concurrency and rejects callbacks from obsolete generations.
- Metadata comes only from the fixed repository. Selection uses the releases list, explicitly includes evaluation/prerelease releases, skips drafts, and chooses the newest valid semantic version. Incomplete or ambiguous assets fail closed. A displayed tag cannot replace the APK's actual versionCode.
- Initial HTTPS asset URLs must match the repository, tag, and filename exactly. Allow at most three manually validated redirects to approved CDN hosts, port 443/default, without credentials or fragments. Reject private DNS destinations at every hop. Do not forward tokens, cookies, Authorization, or other sensitive headers.
- Require the complete APK's exact declared size, the configured size cap, and SHA-256 matching the single exact filename entry in `SHA256SUMS.txt`. Require the exact package, a genuinely newer versionCode, a compatible minSdk, and the same set of current signing certificates. Historical signers alone are insufficient. Android validates the package again during installation.
- Stream downloads to an owned private APK file. Remove partial files on failure/cancellation and define cleanup of prepared files. This differs from screensaver photos, which remain in RAM only. Never modify unrelated preferences, wallpapers, sources, or files.
- Limits: release metadata 1 MiB and 30 releases; checksums 64 KiB; APK 32 MiB; three redirects; a cooperative 60-second transport deadline per request and a read timeout of at most 10 seconds, with connection time bounded by the remaining budget. The controller also has an independent 60-second UI deadline. Blocking DNS can outlive logical cancellation, so bounded workers remain necessary.

A checksum hosted beside the APK detects corruption but is not an independent authentication root. Matching the installed app's current signing certificates remains mandatory. A fake PackageInfo cannot establish cryptographic verification by Android's PackageManager.

## JVM and local integration tests without a network

Use the existing fake HttpURLConnection pattern, injectable clocks, bounded streams, controlled exceptions/latches, and JUnit 4.13.2. Use TemporaryFolder or an exclusive temporary updater directory. Routine tests need no external service or additional package. The following matrix defines intended coverage; the validation report distinguishes executed tests from pending platform flows.

| Area | Minimum rejection or preservation cases |
|---|---|
| Selection | Empty list; current/older/newer versions; malformed tags; duplicates/order; prerelease policy; missing or ambiguous assets/checksums; truncated or oversized metadata |
| HTTP | Valid 200; 401/403/404/429/5xx; format/encoding policy; empty stream; read failure; missing or misleading Content-Length; actual byte cap and deadline |
| Origin | HTTP; look-alike/unapproved hosts; userInfo/port/fragment; wrong repository/tag/name; allowed relative/absolute redirects; allowlist escape; private DNS at any hop; redirect overflow |
| Checksums | Exact SHA accepted; duplicate/wrong-name entries; path traversal; corrupted bytes; size mismatch; no prepared result on failure |
| Compatibility | Exact package; newer code; compatible SDK; exact current signers; reject downgrade/equal code, wrong package, high minSdk, wrong/extra/history-only signers, and invalid/truncated APKs |
| Cancellation | Before connection/file creation, during reading, after validation, and before publication; nonblocking UI cancellation; partial cleanup; no late success |
| Controller | Repeated actions cannot create unbounded work; blocked worker has bounded thread/queue counts; UI timeout does not depend on worker completion; navigation/recreation cannot publish obsolete state |
| Files | Owned directory/prefix; no path escape; failure/cancel deletes only the attempt's files; prepared and partial files are not confused across generations |
| Installer adapter | Only a validated file may proceed; no commit without required special access; cancellation does not install; safe user-action forwarding; owned-session cleanup |

Do not create or commit a real PackageInstaller session merely to satisfy routine test coverage. Separate pure attempt-identity policy from Android installation integration. Document pending native confirmation/installation rather than claiming a fake session proves it.

Target at least 90% coverage of relevant gated logic, with component-specific evidence. The existing JaCoCo gate covers core rules; its percentage does not represent coverage of all Android client, filesystem, Activity, or PackageInstaller code. Preserve earlier tests, build, and lint gates.

## Focused native no-update check

`UpdaterCheckInstrumentation` has an additional explicit test-manifest entry; the original default runner remains first.

1. Capture and persist a complete typed preference snapshot before mutation; retain recoverable before/after journals and compare the current baseline, not an old session's snapshot. Read Home and unknown-source installation access without changing them. Require an empty updater-attempt namespace and no owned installer sessions before the run.
2. Open About, focus the real check button, and activate it with OK. Record the actual repository result. Never change server metadata or publish a fixture to manufacture success.
3. For the historical 0.4.3-versus-0.4.2 check, require the no-newer-version state. Do not download a live APK, create/commit a session, request special access, or change system settings.
4. Exercise cancellation, bounded queue/rejection, a logically invoked watchdog, and an actual late controller callback through clearly labeled local fake transport/latches. These establish controlled behavior, not a real 60-second wait or GitHub outage.
5. Park in Android Settings, confirm foreground/paused state, finish tracked Activities, and wait for destruction before restoration. In finally, attempt restoration even if cleanup fails and retain both the original and restoration errors.
6. Compare the entire typed preference map, missing keys, digests, updater namespace, owned sessions, installation access, and Home. Clara removes only auxiliary artifacts and returns to Home. No network, OS locale, Dream, or permission changes are required.

One real public API check is sufficient for this path. Calling it 21 times adds quota and rate-limit noise. For the 21 languages, compare compiled strings with the same build's catalogs and render explicitly synthetic updater states separately.

## Languages, focus, and UI states

- Cover base plus 20 localized catalogs: no empty values or accidental fallback; preserve version/size/progress arguments. Compare compiled values against the matching source catalogs rather than assuming a historical key count.
- Render checking, current, available/evaluation, progress, verification, required source access, errors, and installation-related labels. A fixture label saying installed/canceled is not an actual Android installation outcome.
- D-pad/OK/Back must preserve usable focus and cancellation. Repeated input must not start duplicate work. Closing a panel must not allow a late result to replace the current dialog or trigger installation.
- Review RTL Arabic/Urdu/Punjabi and long European, Indic, and CJK strings for ordering, overlap, clipping, and complete actions. Geometry assertions are not native-speaker certification.
- Do not automatically open release links, special-access settings, or the installer. Android owns its confirmation screen, which can differ by OEM.

## Future-version installation is a separate proof

Use fake metadata/identities for isolated policy tests. The native archive test wraps the actual installed code-29 APK in future-version metadata and requires rejection: a tag cannot make an old binary newer.

A future confirmation/CANCEL experiment would require a clearly identified local fixture with a truly higher versionCode, the same package, and the same certificate. It must never be published to manufacture a release test. Opening and canceling a confirmation does not prove a completed upgrade. Do not install a fabricated version or confirm a commit without the required human authorization.

If canRequestPackageInstalls is false, record it without granting access through ADB or toggling system settings. A completed upgrade from a legitimate newer official release, including preservation of settings/Home across that upgrade, remains pending until the actual release and authorization exist. Publishing 0.4.3 alone supplies no such proof.

## Evidence and residual risk

Record actual commands/logs, APK hash and signer, component coverage, the live result, fixture methods, cancellation, preference/Home preservation, and untested states. Keep journals, TvProvider data, device identifiers, and private files out of public packages. One device and controlled mocks cannot certify every OEM.

Next step: publish the validated 0.4.3 artifact and QA documents within the user's authorized release scope. Validate a future legitimate upgrade separately; do not relabel the historical no-update check or installation-state fixtures as that upgrade.
