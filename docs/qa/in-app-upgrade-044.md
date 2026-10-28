# Physical in-app upgrade — 0.4.3 to 0.4.4

## Status and authorization boundaries

**Candidate and baseline gates recorded; publication and the physical upgrade are still pending.** The user explicitly authorized publishing 0.4.4 and proceeding with the test after reviewing the prepared candidate. This authorization does not itself grant installation-source access or confirm Android's installation dialog. No successful in-app upgrade is recorded below.

The TV must remain on **0.4.3/code 29** until the user upgrades through Lume with the physical remote and Android confirmation. Candidate **0.4.4/code 30** contains a real local-storage error-classification correction. Do not install it over ADB, alter the installed version/code, publish a fake future release or substitute fixture metadata to manufacture an in-app success.

## Candidate gates — completed; publication pending

- [x] Review the focused production/test diff: local APK output-write `IOException` becomes STORAGE; existing CANCELED errors remain CANCELED; network-read failures remain NETWORK. No UI, permission, source or validation-policy change.
- [x] Execute partial-write regression: remove only the partial owned APK, close streams, release the connection, retain an unrelated sentinel and do not inspect the archive after the failed write. This is fault injection, not a physical disk-full observation on the TV.
- [x] Run candidate validation: **175 JVM tests passed**, including all **17 updater-client tests** and **three new regressions** for partial writes, concurrent cancellation and partial network reads. Release and matching instrumentation builds succeeded. Lint: **0 errors / 18 warnings**. Core coverage: **441/442 lines and 817/859 branches**; this is not whole-installer coverage.
- [x] Inspect the actual release APK: package `br.com.lume.launcher`, version 0.4.4/code 30, minimum SDK 30, target SDK 35 and the existing signing identity.
- [x] Record the actual APK: **10,228,073 bytes**; SHA-256 **`498318bf83176f689aed01533bd269f980080a6eeee5c19473c8fde4b8100216`**. This is the APK digest, not a configuration-backup digest.
- [x] Present the prepared candidate, documentation, release notes, test results and limitations; receive the user's explicit publication/test authorization.
- [ ] Publish and verify the legitimate assets, checksum manifest and tag. Publication is authorized but has not yet been recorded as completed. Recheck what the live API returns; a local tag alone does not prove discoverability.

## Fresh baseline before the remote-controlled test — captured

The operator uses diagnostics and backups only. No hidden mutation or automatic restoration is part of this acceptance test.

- [x] Verify the current TV and installed **0.4.3/code 29**. The resolved Home remains Lume; `READ_TV_LISTINGS` is granted; Android reports installation-source access as **false**. The TV has not been upgraded to the candidate.
- [x] Capture the complete fresh `orbita` map: **16 typed preferences**, including the exact values, sets and absent keys. Do not substitute an older snapshot or force the post-test count to match without comparing every entry.
- [x] Capture and verify the complete private baseline backup and exported digest. Keep its identifier, digest and raw configuration in private evidence, separate from this public checklist.
- [x] Capture the raw `lume_updater` operational namespace and own-session state read-only: **no updater keys** and **no owned sessions** (`keys=[]`, `mySessions=[]`). Do not publish operational owner/session/token values if a later state contains them.
- [x] Keep the private 0.4.3 APK and diagnostic/settings access available as recovery aids. Neither that APK nor ADB/settings access guarantees a successful downgrade or a fully validated recovery path.
- [x] Record recovery limits without claiming a proven alternative launcher. Observed OEM fallback components are not evidence of a working recovery Home. Do not change Home for this test or treat an unvalidated fallback as a passed gate.
- [x] Leave source authorization unchanged during preparation. Only the user may decide to grant it through Android settings. No ADB/AppOps write, root, shell grant or protected-setting change is authorized as a substitute.

If the user changes configuration or enough time passes before installation, take a fresh read-only comparison and baseline again; do not assume this captured state is still current.

## Actions performed only by the user with the physical remote — pending

1. Open Lume's gear, **About > Check for updates**. Observe the actual version offered. It must be the published, legitimate 0.4.4 candidate, with real code 30 greater than installed code 29.
2. Choose **Download update**. Observe progress and successful validation. Do not bypass an incompatible signature, invalid checksum, old code or network/storage error. Checking must not download automatically, and downloading must not install automatically.
3. Choose **Install update** only after validation. If Android requests source access, the user chooses **Allow this source**, decides personally in Android settings and returns to Lume to continue.
4. The user reviews and confirms the native Android PackageInstaller dialog. An operator-injected key, ADB install or automated confirmation does not satisfy this step.
5. Record the visible outcome: success, user cancellation, OEM restriction, error or interruption. A pending-user-action callback is not success. If canceled or failed, keep the original installation and record that result without forcing completion.
6. After a reported success, reopen Lume and press Home from another application. Record the observed version and Home destination. Do not initiate additional Power/standby, HDMI or playback tests and claim them as completed without separate execution.

Publication and proceeding with the test are authorized. Source access and native installation still require the user's own decisions at the Android prompts. A declined prompt is not consent and must not be bypassed to satisfy the checklist.

## Read-only post-upgrade comparison — pending

- [ ] Verify actual installed 0.4.4/code 30, expected package/signing identity and the live resumed component. A version label in the offered metadata is not proof of installation.
- [ ] Read the complete `orbita` map again and compare it to the fresh baseline: key set, types and every value must match, including absent keys. Record differences without changing them.
- [ ] **Do not invoke a preference restore to make the comparison pass.** A mismatch is a finding. Any recovery requires a separate explicit decision and must preserve the original before/after evidence.
- [ ] Compare Home and content permission/flags with the baseline. If the OEM changed Home, record that loss and any user-driven recovery through the official selector separately; do not report Home as continuously preserved.
- [ ] Compare source access/AppOps. A false-to-true change is acceptable only if it corresponds to the user's recorded Android-settings decision. An automatic grant invalidates this proof.
- [ ] Capture the raw updater namespace and own-session state again. Attempt owners, session IDs, tokens and Home-comparison markers are operational state and may legitimately change; distinguish them from the user-facing `orbita` settings. Check expected terminal cleanup without rewriting the namespace to hide a stale attempt.
- [ ] Preserve backups and evidence privately. Report version transition, data comparison, Home behavior, permission decisions and incomplete gates as separate results.

## Outcome record

Candidate results and the fresh baseline are recorded above. **Publication is authorized but pending; the real in-app installation and post-upgrade comparison are pending.** Record the published asset/tag verification and the user's native-confirmation outcome before marking an in-app upgrade successful. No preference restore may be used to conceal a failed comparison.

The 0.4.3 application will perform the download of 0.4.4. A successful transition would prove the native installation and preservation path; the new 0.4.4 error classification is separately supported by the candidate's fault-injection regressions.

If the process stops between creating a session and storing its ID, an owned orphan may conservatively block a new attempt. Do not abandon an unidentified session or delete user data to continue. Inspect official Android state and follow the [operational recovery guidance](../operations/github-updates.md). Existing DNS/worker limits also remain relevant; do not create unbounded retries while a canceled platform call is still pending.

Reference: [maintenance release-note draft](../releases/0.4.4.md), [updater security contract](../design/update-security.md), and [previous 0.4.3 validation](updater-validation-043.md). Prior ADB deployment, synthetic states and successful no-update queries do not establish this future, user-confirmed PackageInstaller transition.
