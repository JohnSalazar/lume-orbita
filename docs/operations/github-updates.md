# Manual updates through GitHub

## Status and scope

Implemented in **0.4.3 / versionCode 29**, for Android TV and Google TV 11/API 30 or later. This procedure documents the implemented flow and limits; it does not certify a completed update to a future release.

During recorded validation, installed 0.4.3 queried the then-public 0.4.2 release and correctly reported no newer version. No source permission was granted and no future-release PackageInstaller installation was performed. After 0.4.3 is published, that installed version should still report no newer version until a legitimate higher release appears. Publication is not proof that the in-app installer has completed an upgrade.

Sources: [PRD](../01-updater-prd.md), [security contract](../design/update-security.md), [validation report](../qa/updater-validation-043.md) and [test plan](../qa/updater-test-plan.md). Pure tests and adapter fixtures do not replace Android installation confirmation on the device.

A later real 0.4.3-to-0.4.4 upgrade preserved the recorded configuration and Home, but exposed a blocked post-install result Activity. See the [physical outcome](../qa/in-app-upgrade-044.md). The 0.4.5 candidate adds verified replacement recovery; publication and the next physical return test are recorded separately in its [validation checklist](../qa/updater-return-045.md).

## TV workflow

1. Open the gear, then **About > Check for updates**. Only this user action initiates discovery. There are no periodic checks, boot-time checks or silent installations.
2. Lume checks releases from the fixed public `JohnSalazar/lume-orbita` repository. It shows no newer version, a recoverable error or an available candidate. Evaluation versions are identified. The query covers a bounded release window rather than the entire history.
3. If a newer version is available, choose **Download update**. Checking does not start downloading automatically. The expected APK and checksum manifest belong to the same release. Progress is displayed; Back cancels the application's operation.
4. After download and validation, choose **Install update**. This is a separate user click. Only the verified APK can proceed to the installer.
5. If Android requires source authorization, open **Allow this source** and decide personally in Android settings. Lume does not grant access through code, ADB or root. Return to Lume to continue; opening the page does not install anything or prove permission was granted.
6. The flow uses **PackageInstaller** and Android's own confirmation. The user may refuse or cancel. Availability and appearance depend on the Android/OEM implementation; silent or universal installation is not guaranteed.
7. After updating, verify Lume and Home. Android's normal same-package update preserves application data with a compatible signing identity. If the OEM changes Home, **Review Home selection** opens the official settings for a manual choice. Lume does not reset Home automatically.

Do not uninstall Lume or clear data to update. Keep another working launcher available before relying on one application as Home. Source authorization and default-Home selection are separate decisions; one must not change the other.

## Network, storage and concurrency limits

| Boundary | Implemented limit |
|---|---|
| Release discovery | At most 30 entries; drafts and invalid metadata do not authorize installation |
| Release JSON | 1 MiB |
| Checksum manifest | 64 KiB |
| APK | 32 MiB, checking actual bytes read and complete length |
| Redirects | At most three, through HTTPS and approved origins/CDNs only |
| Time | 60 seconds per transport request and a separate 60-second UI watchdog per check or download/validation operation |
| Concurrency | One worker with a bounded queue; superseded callbacks are discarded |

The 60-second limits do not promise installation completion: time spent on source authorization and native confirmation is separate. The watchdog logically cancels an operation and returns a recoverable UI state even if DNS is delayed. The worker can remain occupied until the platform call returns; concurrency remains bounded instead of spawning more workers. Do not repeatedly retry to bypass GitHub limits.

Installation requires a temporary APK in private internal cache. This differs from online screensaver photographs, which retain their RAM-only application behavior. Do not use Downloads, a release-controlled path, broad storage permission or a public URI. Failure, cancellation and completed staging remove only owned temporary files; the client also recognizes expired files matching its private naming pattern. An Android session needs additional storage. Do not delete preferences, wallpapers, unrelated caches or unknown sessions to free space.

## Validation and privacy

The 0.4.6 candidate adds automatic removal of orphaned APKs from Lume's private `cache/updates` after a recorded successful installation. This includes older failed-download remnants. Active downloads, prepared/staging sources and pending native sessions are protected. Maintenance is bounded and best effort; reopening Lume can retry after a recorded success for the installed version. No user setting or manual cleanup action is required. Android continues to manage its native installation/session directories; Lume does not delete the installed package, preferences, other-app caches or unknown sessions. This candidate is not yet a published or physically validated 0.4.6 update.

Downloads use standard TLS and fixed project URLs with restricted redirects. Validation checks byte counts, SHA-256, advertised versus actual APK version, exact package `br.com.lume.launcher`, a strictly higher `versionCode`, compatible minimum SDK and exact equality of the current signer-certificate sets. `PackageManager` inspects the archive and `PackageInstaller` verifies the package when installing. Invalid, ambiguous, modified or incompatibly signed archives are rejected.

A checksum from the same repository detects corruption but is not independent authentication; comparison with the installed signing identity remains mandatory. The updater does not extract and execute downloaded classes or layouts in its process. Validated bytes are rechecked before staging.

There are no GitHub credentials, cookies, application telemetry or uploads of settings, favorites, history or streaming accounts. GitHub and its CDN naturally receive the IP address and ordinary request; this is not anonymity. Do not publish signed CDN query strings, personal data or raw HTTP errors in logs.

## Attempts, delayed callbacks and recovery

In the 0.4.5 candidate, a protected package-replacement receiver and foreground resume can reconcile the installed package with the recorded target. New attempts verify exact code, APK hash and signers. The incoming migration from 0.4.4 uses a separate, narrower protected-event rule because that outgoing version did not record all target pins. A successful verification can request a one-time return to Lume only when Lume already was and remains Home. Android can still refuse automatic foreground opening; press Home in that case. A refused return does not mean installation failed. No user preferences, source permissions or default-Home settings are rewritten to produce success.

An installation attempt has its own owner, session and token. `InstallerAttemptRules` correlates them with the active state. A delayed callback or old Activity must not confirm, clear or abandon a later attempt. The installation Activity is private; only a callback correlated with its active session can advance Android confirmation. The previous Home choice is recorded separately for comparison after a genuinely newer version is installed.

Cancellation or failure does not authorize uninstalling, clearing data, granting source access or changing Home to retry. Cleanup abandons only a session with confirmed ownership. If the process stops between session creation and persistence of its ID, the code must not guess or abandon another session. The owned orphan can conservatively block new attempts until cleanup or expiration. Reopen Lume and inspect Android's official state before retrying. Session recovery is distinct from expiration of old temporary APK files.

Before physical validation, record the version, Home, source access and a recent complete configuration backup. Compare types, values and absent keys afterward; do not restore an older snapshot merely for convenience. Keep backups and detailed evidence private. The [device-validation procedure](device-validation.md) covers typed journals and safe export.

An older APK does not guarantee a data-preserving downgrade. If the OEM changes Home, recover through the official manual selector and keep an alternative available. A successful no-update query proves discovery and that UI path, not a complete installation. Do not publish fictitious releases, grant access through shell commands or approve an invalid package to manufacture a passing test.
