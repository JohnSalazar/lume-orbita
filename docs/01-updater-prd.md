# Manual GitHub updates

## Objective

From **Settings > About**, check the public `JohnSalazar/lume-orbita` repository for a newer version and let the user update Lume with explicit application and Android confirmation. Retain the installed package identity, signing identity and configuration. Preserve the existing Home selection where the OEM permits it; detect changes and offer the official manual selector rather than changing Home automatically.

## Scope and user flow

1. **Check for updates** contacts GitHub only after a user action. There are no periodic checks, boot-time checks or silent installations.
2. Show no newer version, a recoverable error, or an available candidate. Support the evaluation/prerelease channel explicitly; `/releases/latest` alone excludes prereleases and is insufficient.
3. **Download update** retrieves the expected APK and checksum manifest from the same official release. Show progress; Back cancels the application's operation. Screensaver photographs and platform content are not part of the request.
4. Validate byte limits, SHA-256, package name, actual APK version, minimum SDK and equality of the current signing-certificate sets. Reject incompatible or unverifiable packages.
5. **Install update** is a separate user action after download and validation. If Android requires source authorization, the user opens the package-specific settings and decides whether to grant it. Recheck access after returning; opening Settings is not authorization. The native installer then requires confirmation.
6. Use Android's normal same-package update mechanism to retain application data. Do not uninstall Lume, clear data, request root or reset Home. If the OEM changes Home during package replacement, explain the change and offer access to the official selector for a manual decision.
7. Recover a successful self-update when Android kills the old process and blocks the result Activity. Persist the selected target identity before committing, verify the installed package on recovery, and return to Lume only when it was and remains the selected Home. A blocked return is not an installation failure. An absent session alone does not establish success.

### Return correction acceptance criteria

- Keep the physically tested native confirmation flow; do not add silent installation or Home-setting writes.
- A private receiver for Android's protected MY_PACKAGE_REPLACED broadcast and foreground resume recovery must tolerate process death, duplicate callbacks and stale attempts.
- New attempts require the installed version code, APK hash and signer set to match the persisted validated target before recovering success. Completion is correlated to the attempt and consumed once.
- The 0.4.4 legacy path may request a one-time UI return only after the protected replacement broadcast, a version increase, matching recorded ownership, an absent recorded session, Lume as installing and initiating package, and Lume as Home before and after replacement. This is not exact persisted-target verification. Old incomplete markers alone remain inconclusive.
- Do not display a generic installation error merely because the old Activity was recreated after a verified replacement. A firmware block on reopening must retain the verified result for the next foreground visit.
- Test negative identities, stale/duplicate callbacks, legacy restrictions and Home preservation. Separately report unit/contract tests, physical installation and the OEM's observed return behavior.

## Implementation

- Version **0.4.3**, `versionCode 29`; Android 11/API 30 or later, using the existing Kotlin/Views stack without a new dependency.
- Pure validation in `UpdateRules`, synchronous `GitHubUpdater` work off the UI thread, and a private installation Activity using the public `PackageInstaller` API.
- Existing launcher panels and remote-navigation conventions; 23 new strings in each of 21 locales.
- A private temporary APK is required for installation. Remove owned temporary files after staging, cancellation or failure. Online screensaver photographs retain their RAM-only application behavior.
- Declare `REQUEST_INSTALL_PACKAGES` to request installation. This does not grant source access. Do not request `INSTALL_PACKAGES` or `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, or grant access through ADB or application code.
- Bound discovery to 30 releases, JSON to 1 MiB, checksums to 64 KiB, APK data to 32 MiB and redirects to three approved HTTPS destinations.
- Use a 60-second transport deadline per request and a separate 60-second UI watchdog per check or download/validation operation. One worker and its bounded queue remain bounded when DNS has not returned. Native confirmation has no promised 60-second completion time.
- Correlate the installation attempt's owner, session and token. Stale callbacks and old Activity instances must not complete, clear or abandon a later attempt.

## Acceptance criteria and evidence

- Exercise version selection, URLs, checksum parsing, payload limits, package/SDK/certificate validation, cancellation and downgrade rejection, including negative cases.
- Exercise controlled transport/download contracts and real GitHub discovery. A build newer than the public release must report no newer version without offering a downgrade.
- Verify translated states, RTL and remote focus/Back behavior. Compare complete typed configuration snapshots, Home and source access before and after tests.
- Keep discovery evidence separate from installation evidence. A button, fixture or successful no-update query does not prove a complete PackageInstaller update. Do not publish fictitious releases to force a test.
- Run build, lint and the core coverage gate. Core coverage is not whole-application or whole-installer coverage. Report warnings and unexecuted gates explicitly.

The recorded 0.4.3 validation checked the then-public **0.4.2** release and found no newer version. It passed 172 JVM tests, eight Android contract cases and 294 updater-state panels across 21 locales, preserving the recorded settings and Home. No future-release PackageInstaller installation or source-permission grant was performed. This is historical evidence, not a statement that the repository must still be on 0.4.2. After 0.4.3 is published, that installed version should still find no newer version until a legitimate higher release appears. See the [validation report](qa/updater-validation-043.md).

## Constraints and residual risks

GitHub can be unavailable, rate-limit requests or change CDN behavior. TLS and a checksum hosted with the APK do not replace verification against the installed signing identity. A checksum is not independent authentication if the repository and signing key are both compromised.

Android/OEM builds can require additional authorization, omit a compatible installer or change Home during replacement. A process interruption between session creation and recording its ID can leave an owned orphan; subsequent attempts fail conservatively until cleanup or expiration rather than abandoning an unidentified session. DNS can outlive logical cancellation and continue occupying the bounded worker. Neither condition authorizes root, protected-setting changes or unbounded retries.

See the [security contract](design/update-security.md), [operational procedure](operations/github-updates.md) and [test plan](qa/updater-test-plan.md).

Official references: [PackageInstaller](https://developer.android.com/reference/android/content/pm/PackageInstaller), [required user action](https://developer.android.com/reference/android/content/pm/PackageInstaller.SessionParams#setRequireUserAction(int)), [source authorization](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()), and [GitHub release listing](https://docs.github.com/en/rest/releases/releases#list-releases).
