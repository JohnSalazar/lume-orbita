# Updater validation — 0.4.3

## Delivery and historical test context

Version 0.4.3/code 29 was installed on the KONKA running Android 11/API 30 using replacement installation (`-r`) and the existing signing certificate. About now supports a manual GitHub release check. A newer release requires download/validation, an explicit install action, and Android's confirmation. Unknown-source installation access remains the user's decision in official Android settings; no access was granted through ADB.

The updater does not uninstall the package, clear user settings, force the default Home, or request root. Existing Home and TV-content access were preserved. **At the time of the live native test, the newest public release was 0.4.2**, so installed 0.4.3 correctly reported no newer version. No fabricated future release was published to force an update test. Publishing 0.4.3 afterward does not prove a future APK installation or change that historical result.

## Executed gates

| Gate | Observed result |
|---|---|
| JVM | 172 tests; zero failures, errors, or skipped tests. Includes 14 transport/download contracts and four installer-attempt identity regressions |
| Core JaCoCo | 441/442 lines and 817/859 branches; the 90% gate passed |
| Update rules | 40/40 lines and 113/120 branches; installer-attempt rules: 3/3 lines and 22/22 branches |
| Build and lint | Release build succeeded in 3m 21s; matching release instrumentation built in 54s; zero lint errors and 18 warnings |
| Resources | 21 catalogs/guides and 290 strings per locale; resource verification reported no errors |
| Live TV check | Real button activation through injected OK; the public GitHub API reported no newer version for installed 0.4.3 while public 0.4.2 was current |
| Android contracts | Eight cases: current/older versions, newest non-draft evaluation release, malformed JSON, missing/duplicate/foreign assets, excessive release count, and rejection of the actual installed APK despite future-version metadata |
| Controller | Maximum one worker and one queued job; rejection/purge, logical watchdog, and ignored late callback, exercised with latches and local fake HTTP |
| New UI | 21 locales × 14 states = 294 updater panels; 483 new compiled string values matched source catalogs exactly; 1,050 visible text instances had no recorded geometry problems; Back closed the panel in all 21 locales |
| Preservation | All 16 preferences retained their exact types/values and before/after digest; updater namespace and owned installer sessions were unchanged; installation access remained false; Home remained Lume |

Evidence includes `android-043-updater-final.log`, `android-043-release-test.log`, JUnit XML, JaCoCo XML, lint XML, and the private native `updater.json` with typed before/after snapshots. Helena independently parsed the reports and compared all 483 compiled values against their source catalogs; Clara executed builds and device operations. The UI matrix is specifically for the updater, not the separate 294-screen general-app matrix from 0.4.1.

## Artifact verification

The delivery APK and the existing release build output are byte-identical:

- Version: **0.4.3**, versionCode **29**, minSdk **30**, targetSdk **35**.
- Size: **10,227,297 bytes**.
- SHA-256: `ada4b84a3b9d850ad6c5588c59900b14ead590a8e8f7d31937bae2813067b463`.
- APK Signature Scheme v2 verification succeeded with one signer.
- Existing certificate SHA-256: `c435b38a840b6381b56c2f71f6e914f7958218762139b47ecd9607793cd67cdb`.

Read-only comparison used the existing artifacts and apksigner; no rebuild, ADB command, or publication was performed by Helena for this release-document review. Production source remained at commit `9d6858b` during the review; only these two English QA documents were changed.

The auxiliary test APK was removed after the native run, and KEYCODE_HOME resumed LauncherActivity. The native Backdrop screensaver and its 300,000 ms timeout remained unchanged. Complete device evidence and backups remain private.

The two additional lint warnings concern synchronous preference commit in attempt cleanup and the TV install Activity's fixed landscape orientation. The commit remains intentional for attempt-state consistency; the other 16 warnings predate this change. No warning was hidden to manufacture a clean report.

## Security review and fixes

- Initial HTTPS origins and redirects are restricted to the expected repository/CDNs. Payloads and time are bounded, without logging credentials or signed URLs. A colocated checksum is not independent author authentication.
- Policy rejects the wrong package, an incompatible SDK, an old/equal code, or a different set of current certificates. PackageManager validates archive identity/signatures, and PackageInstaller validates again if installation occurs. Fake identity tests do not establish every native cryptographic rejection path.
- Review identified and fixed cross-attempt callback collisions using an exclusive lease, owner UUID, correlated session/token/URI, and identity-checked state updates/cleanup.
- Review identified and fixed an unbounded queue. The updater now has a separate bounded global worker, one queued job, purge/rejection, and a UI watchdog independent of DNS completion. A blocked DNS call may still occupy that single worker after logical cancellation without creating an unlimited queue or blocking the UI.
- A process interruption exactly between Android session creation and persistence of its ID may leave an owned orphan session. New attempts fail conservatively until cleanup/expiration rather than abandoning an unknown session. This is separate from the 24-hour expiry policy for temporary APK files.

## Corrected auxiliary-runner failure

The first native run encountered `NoSuchMethodError` because debug-variant Kotlin internal names were linked against the release app. It stopped before preference mutation; an independent check confirmed the exact backup state. Setting `testBuildType = "release"`, building `assembleReleaseAndroidTest`, and repeating the complete run resolved the mismatch. No public production test hook was added.

## Limits and next step

No future release was installed or committed through PackageInstaller, and unknown-source access was not granted. Installation-related visual states are label fixtures, not real installation outcomes. Download/identity tests use controlled transport and identities; the Android archive contract proves rejection of the real installed binary wrapped in future metadata, not installation-time rejection of a new wrong-signer or tampered APK.

The watchdog Runnable was invoked logically; the test did not wait through an actual 60-second network stall. Reported coverage applies to core rules, not all Activity, HTTP, filesystem, or PackageInstaller code. No new FPS benchmark, Power/standby test, HDMI test, or native-speaker language certification was performed. Geometry checks cover the recorded updater panels on this KONKA, not every OEM or viewport.

Next installation proof: a legitimate newer release with the same package/certificate, explicitly selected by the user. Observe any required source-access decision and native confirmation, retain a backup, and verify settings/Home afterward. Publishing 0.4.3, attaching its APK, or downloading release assets does not establish that future upgrade. Do not offer a downgrade or publish a fabricated APK to manufacture a passing result.
