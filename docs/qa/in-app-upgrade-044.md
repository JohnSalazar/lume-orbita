# Physical in-app upgrade — 0.4.3 to 0.4.4

## Status and authorization boundaries

**Publication, physical installation and configuration/Home preservation verified; the terminal callback has an unresolved defect.** The user explicitly authorized publishing 0.4.4 and proceeding with the test, then confirmed the human-driven installation and physical Home behavior. See the outcome record for the observed popup and blocked callback; this is not an unconditional updater PASS.

The TV must remain on **0.4.3/code 29** until the user upgrades through Lume with the physical remote and Android confirmation. Candidate **0.4.4/code 30** contains a real local-storage error-classification correction. Do not install it over ADB, alter the installed version/code, publish a fake future release or substitute fixture metadata to manufacture an in-app success.

## Candidate and publication gates — completed

- [x] Review the focused production/test diff: local APK output-write `IOException` becomes STORAGE; existing CANCELED errors remain CANCELED; network-read failures remain NETWORK. No UI, permission, source or validation-policy change.
- [x] Execute partial-write regression: remove only the partial owned APK, close streams, release the connection, retain an unrelated sentinel and do not inspect the archive after the failed write. This is fault injection, not a physical disk-full observation on the TV.
- [x] Run candidate validation: **175 JVM tests passed**, including all **17 updater-client tests** and **three new regressions** for partial writes, concurrent cancellation and partial network reads. Release and matching instrumentation builds succeeded. Lint: **0 errors / 18 warnings**. Core coverage: **441/442 lines and 817/859 branches**; this is not whole-installer coverage.
- [x] Inspect the actual release APK: package `br.com.lume.launcher`, version 0.4.4/code 30, minimum SDK 30, target SDK 35 and the existing signing identity.
- [x] Record the actual APK: **10,228,073 bytes**; SHA-256 **`498318bf83176f689aed01533bd269f980080a6eeee5c19473c8fde4b8100216`**. This is the APK digest, not a configuration-backup digest.
- [x] Present the prepared candidate, documentation, release notes, test results and limitations; receive the user's explicit publication/test authorization.
- [x] Publish and verify the legitimate assets, checksum manifest and tag. [Release 0.4.4 is public](https://github.com/JohnSalazar/lume-orbita/releases/tag/v0.4.4). All four assets were downloaded anonymously and matched the prepared files byte for byte, with release-API digest verification. The immutable release commit/tag points to `120746dd7c099551df112c8c16f780e011152820`.

## Fresh baseline before the remote-controlled test — captured

The operator uses diagnostics and backups only. No hidden mutation or automatic restoration is part of this acceptance test.

- [x] Verify the current TV and installed **0.4.3/code 29**. The resolved Home remains Lume; `READ_TV_LISTINGS` is granted; Android reports installation-source access as **false**. The TV has not been upgraded to the candidate.
- [x] Capture the complete fresh `orbita` map: **16 typed preferences**, including the exact values, sets and absent keys. Do not substitute an older snapshot or force the post-test count to match without comparing every entry.
- [x] Capture and verify the complete private baseline backup and exported digest. Keep its identifier, digest and raw configuration in private evidence, separate from this public checklist.
- [x] Capture the raw `lume_updater` operational namespace and own-session state read-only: **no updater keys** and **no owned sessions** (`keys=[]`, `mySessions=[]`). Do not publish operational owner/session/token values if a later state contains them.
- [x] Keep the private 0.4.3 APK and diagnostic/settings access available as recovery aids. Neither that APK nor ADB/settings access guarantees a successful downgrade or a fully validated recovery path.
- [x] Record recovery limits without claiming a proven alternative launcher. Observed OEM fallback components are not evidence of a working recovery Home. Do not change Home for this test or treat an unvalidated fallback as a passed gate.
- [x] Leave source authorization unchanged during preparation. Only the user may decide to grant it through Android settings. No ADB/AppOps write, root, shell grant or protected-setting change is authorized as a substitute.

The baseline was checked again after publication: all 16 preferences retained the private baseline digest, Home remained Lume and the TV still reported 0.4.3/code 29. This is a pre-upgrade check, not a post-upgrade result. The user has received remote-control instructions and a request to report the native outcome.

If the user changes configuration or enough time passes before installation, arrange a fresh read-only comparison before starting; do not assume the baseline remains current. Do not run instrumentation or automated navigation while the user is operating the source-permission or installation flow. Wait for the user to confirm that the native flow has ended before the post-upgrade audit.

## Actions performed only by the user with the physical remote — pending

1. Open Lume's gear, **About > Check for updates**. Observe the actual version offered. It must be the published, legitimate 0.4.4 candidate, with real code 30 greater than installed code 29.
2. Choose **Download update**. Observe progress and successful validation. Do not bypass an incompatible signature, invalid checksum, old code or network/storage error. Checking must not download automatically, and downloading must not install automatically.
3. Choose **Install update** only after validation. If Android requests source access, the user chooses **Allow this source**, decides personally in Android settings and returns to Lume to continue.
4. The user reviews and confirms the native Android PackageInstaller dialog. An operator-injected key, ADB install or automated confirmation does not satisfy this step.
5. Record the visible outcome: success, user cancellation, OEM restriction, error or interruption. A pending-user-action callback is not success. If canceled or failed, keep the original installation and record that result without forcing completion.
6. After a reported success, reopen Lume and press Home from another application. Record the observed version and Home destination. Do not initiate additional Power/standby, HDMI or playback tests and claim them as completed without separate execution.

Publication and proceeding with the test are authorized. Source access and native installation still require the user's own decisions at the Android prompts. A declined prompt is not consent and must not be bypassed to satisfy the checklist.

## Read-only post-upgrade comparison — installation and preservation verified; callback defect observed

- [x] Verify actual installed 0.4.4/code 30, expected package/signing identity and the live resumed component. The installed APK hash matches the published APK; Android records Lume as both installing and initiating package.
- [x] Read the complete `orbita` map again and compare it to the fresh baseline: all 16 typed preferences match. No restore was performed.
- [ ] **Do not invoke a preference restore to make the comparison pass.** A mismatch is a finding. Any recovery requires a separate explicit decision and must preserve the original before/after evidence.
- [x] Compare Home and content permission/flags with the baseline: Lume remains Home and READ_TV_LISTINGS remains granted with USER_SET. The user also confirmed physical Home returns to Lume.
- [ ] Compare source access/AppOps. A false-to-true change is acceptable only if it corresponds to the user's recorded Android-settings decision. An automatic grant invalidates this proof.
- [ ] Capture the raw updater namespace and own-session state again. Attempt owners, session IDs, tokens and Home-comparison markers are operational state and may legitimately change; distinguish them from the user-facing `orbita` settings. Check expected terminal cleanup without rewriting the namespace to hide a stale attempt.
- [ ] Preserve backups and evidence privately. Report version transition, data comparison, Home behavior, permission decisions and incomplete gates as separate results.

## Outcome record

On October 28, 2026, the user confirmed installing 0.4.4 through Lume and returning to Lume with the physical Home button. Read-only inspection confirmed code 30, the published APK hash and original signing certificate, all 16 preferences unchanged, Lume still resolved as Home, and READ_TV_LISTINGS still granted. Installation-source access was observed as enabled after the human-driven flow; the operator did not grant it over ADB. No preference restore was performed.

**Installation and preservation passed; the complete terminal-result flow did not.** The user reported that HBO briefly appeared and an error popup followed, but could not recall its text. The Android log shows the app process stopped for replacement, followed by `Abort background activity starts` when the installer callback attempted to open UpdateInstallActivity. No new Lume crash was found in that interval. The own-session list was empty, while updater owner/session/token markers remained. Their values and raw evidence are retained privately. This establishes a callback-delivery/reconciliation defect; it does not establish the exact popup text or a failed package installation. No operational markers were cleared to hide the finding. A production correction and another real upgrade test remain outstanding.

The owner subsequently requested storage cleanup. See the [observed cleanup record](../operations/konka-cleanup-044.md). TV-side test outputs and backups were removed after preserving the current backup privately; no preference restore or production APK replacement was performed during cleanup.

Verified public asset evidence:

| Asset | Bytes | SHA-256 |
|---|---:|---|
| APK | 10,228,073 | `498318bf83176f689aed01533bd269f980080a6eeee5c19473c8fde4b8100216` |
| English documentation ZIP | 61,599 | `2a975f465689318fdaffe2460e7d767678e0f0362afdc1e0ca63d5747d0ee69d` |
| Release-notes Markdown | 5,732 | `1948335386e55cfba9253f05666a04abee52099425e9e01f0c1f25ea2623e928` |
| SHA256SUMS.txt | 285 | Release-API digest and anonymous byte comparison verified; digest not reproduced here |

The published tag and assets remain the immutable release snapshot. This living checklist records publication and subsequent test observations separately; it does not change that tag, replace release assets or rewrite the original preparation evidence. Download verification establishes public artifact integrity, not a PackageInstaller transition on the TV.

The 0.4.3 application will perform the download of 0.4.4. A successful transition would prove the native installation and preservation path; the new 0.4.4 error classification is separately supported by the candidate's fault-injection regressions.

If the process stops between creating a session and storing its ID, an owned orphan may conservatively block a new attempt. Do not abandon an unidentified session or delete user data to continue. Inspect official Android state and follow the [operational recovery guidance](../operations/github-updates.md). Existing DNS/worker limits also remain relevant; do not create unbounded retries while a canceled platform call is still pending.

Reference: [maintenance release-note draft](../releases/0.4.4.md), [updater security contract](../design/update-security.md), and [previous 0.4.3 validation](updater-validation-043.md). Prior ADB deployment, synthetic states and successful no-update queries do not establish this future, user-confirmed PackageInstaller transition.
