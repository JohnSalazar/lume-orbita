# Self-update return correction — 0.4.5

## Status

The 0.4.5/code 31 evaluation prerelease is public and its real installation through Lume was verified. The user reported no error popup. **Automatic foreground return did not pass:** the KONKA blocked the replacement receiver's explicit request to open Lume and resumed HBO. Lume remains the resolved Home; no permission or Home-setting workaround was applied.

The user explicitly authorized publishing 0.4.5 and testing it on the KONKA after reviewing the candidate. A fresh private baseline was exported and verified before publication: 16 typed preferences, original signing identity, Lume selected as Home, content permission granted, installation-source access already enabled and no owned installer sessions. The old incomplete 0.4.4 attempt markers were recorded privately and were not cleared to conceal the earlier defect.

Executed final gate: **186 JVM tests**, no failures/errors/skips; core coverage **464/465 lines and 911/953 branches**, with the new recovery policy at **19/19 lines and 94/94 branches**. Release and release-instrumentation builds succeeded in the final 8m 19s Gradle invocation. Lint reported **0 errors and 17 warnings**. Locale validation passed **21 catalogs and 21 guides**; no translated UI resources were added or changed. All **six isolated Android adapter contracts passed on the KONKA after the real installation**. Their synthetic journals and intercepted navigation do not override the observed foreground-return failure.

Candidate APK: `br.com.lume.launcher`, version **0.4.5/code 31**, minimum SDK **30**, target SDK **35**, **10,248,505 bytes**. SHA-256: `21d4fc3de9199c8c4ba2a8293e46dc45a30f7bd1c9fa955c7091f159ca29685b`. Signature verification passed with the unchanged evaluation certificate. Compiled manifest inspection confirmed the package-replacement receiver is not exported.

## Observed defect

During the real 0.4.3-to-0.4.4 installation, Android terminated the outgoing Lume process for package replacement, then blocked the installer-result Activity with `Abort background activity starts`. The package installation succeeded, all 16 typed preferences remained identical and physical Home returned to Lume. The user observed HBO and an error popup afterward, but could not recall the popup text. Residual updater markers remained despite an empty own-session list.

## Correction scope

Retain the existing explicit native confirmation flow. Add recovery after Android's protected package-replacement broadcast and foreground resume, correlated to the recorded attempt. New attempts persist the validated target code, APK hash and current signer set before commit. An absent installer session alone never means success. A verified successful replacement can request a one-time return only if Lume was and remains Home; a blocked foreground start never changes a verified success to failure. Do not modify user preferences, choose Home, request additional installation privileges or silently confirm Android dialogs.

The incoming 0.4.5 package must also handle a new attempt started by 0.4.4. That old version does not persist the exact target identity. Its narrower legacy path may request UI return only on the protected replacement event, with a real version increase, correlated owner/home ownership, Lume as installing and initiating package, an absent recorded session, and Lume as Home before and afterward. This must be reported separately from the new exact-target recovery. Previously consumed/incomplete legacy markers remain inconclusive.

## Validation checklist

- [x] Review callers, lifecycle recreation, receiver manifest, duplicate/late callbacks and conditional cleanup. A late protected-event race and stale-result-owner handling were corrected during review; the existing generic Home-change warning was retained.
- [x] Run JVM regressions for exact/mismatched identity, ownership, stale attempts, duplicate completion, legacy event restrictions and Home conditions. Eleven new regression methods passed.
- [x] Run the core coverage gate, release build, matching instrumentation build, lint and locale catalog verification.
- [x] Execute six Android contracts in an isolated test preference namespace. Real user preferences, updater namespace, sessions, installation-source access and Home were unchanged by those contracts; the temporary test preferences were removed. No broadcast or real navigation was simulated as a physical success.
- [x] Verify candidate APK package, version, signer and complete hash.
- [x] Export and verify a fresh private physical-device baseline before the human-driven update. Recheck its equality immediately before the user starts if intervening actions may have changed preferences.
- [x] Obtain separate publication approval if GitHub delivery is required; do not fabricate a release or bypass the newer-version check.
- [x] Publish the reviewed APK and English documentation, then verify the public tag, metadata, checksum manifest and exact anonymously downloaded asset bytes.
- [x] Have the user confirm the real installation with the remote. No instrumentation ran during download, staging or native confirmation.
- [x] Observe the foreground destination and error status; verify installed APK, signing identity, installation source, Home, content permission and terminal updater state. Automatic return was blocked, not passed.
- [x] Compare every preference: 14/16 entries were identical; the active content tab and remembered Suggestions card differed. The user explicitly confirmed navigating tabs/cards during the test, accounting for those UI-selection changes. No values were restored.

## Physical outcome and cleanup

The installed package is 0.4.5/code 31 with the exact public APK hash and original certificate. Android records Lume as both installing and initiating package. The Home resolver still selects Lume, content access remains granted and installation-source access remains enabled. No owned installer sessions remain. The updater records a LEGACY result for code 31, a return attempt and a consumed notification; the old active owner/session/token markers were cleared by the production recovery path.

The Android log shows the original result Activity blocked after package replacement. It then starts PackageReplacedReceiver, which records the result and attempts the explicit Lume HOME Activity. Android rejects that second request with `Abort background activity starts`. This is direct evidence of the firmware restriction; HBO is the previous resumed task, not a content-card launch requested by the updater. Pressing Home remains the safe manual return path. No root, overlay, alarm, default-Home write or extra privilege was used.

Before and after snapshots and raw updater evidence were exported and verified privately. The complete maps differ in the active content tab and remembered Suggestions card, while all other 14 entries match. The user explicitly confirmed navigating between tabs/cards during the test; those two changes are recorded as user navigation, not silently treated as byte-identical preservation. The six subsequent adapter tests and maintenance cleanup preserved their own before/after real-state snapshots exactly.

After evidence export, the reviewed maintenance helper removed 12 test/backup files totaling 8,227 logical bytes. The private updater APK directory was already empty. The auxiliary test package was uninstalled and Lume reopened. Available storage was 869,024 KiB. These are point-in-time observations, not proof that all future failed downloads are automatically removed; a separate automatic-cleanup change is being prepared.

## Platform limits

Android controls process replacement, task visibility and background activity starts. The application can request return to its own selected Home; it cannot promise that no frame of a previous task will appear during the system transition. An OEM refusal to reopen must leave a correct recorded result for a subsequent Home press or manual launch.

Official references: [PackageInstaller commit callback](https://developer.android.com/reference/android/content/pm/PackageInstaller.Session#commit(android.content.IntentSender)), [protected package-replacement event](https://developer.android.com/reference/android/content/Intent#ACTION_MY_PACKAGE_REPLACED), and [Android activity launch restrictions](https://developer.android.com/guide/components/activities/secure-bal).

## Publication evidence

Published [v0.4.5](https://github.com/JohnSalazar/lume-orbita/releases/tag/v0.4.5), an evaluation prerelease, with the tag resolving to `f359e3b117cfa0d3c32ddb592135c3537503e8d7`. All four assets were downloaded anonymously and compared byte-for-byte with the reviewed local files; their release-API digests matched. The checksum manifest and documentation ZIP integrity passed.

| Asset | Bytes | SHA-256 |
|---|---:|---|
| APK | 10,248,505 | `21d4fc3de9199c8c4ba2a8293e46dc45a30f7bd1c9fa955c7091f159ca29685b` |
| English documentation ZIP | 61,664 | `58e3135c794f2a881b241d50685ef2548e28794630d5e3a04e7c1c299a198513` |
| Release notes | 3,901 | `f4f0dcad956b5f582c34ecc3e377433a28d15a83efb022c464002dbdec92cb6c` |
| SHA256SUMS.txt | 285 | Exact bytes and API digest verified |

The published release assets and tag remain fixed. This living validation record records physical observations without replacing assets or moving the published tag. The real install, six adapter contracts and final maintenance cleanup are complete. The two UI-selection changes were explained by the user; the automatic foreground-return goal failed under the KONKA background-start restriction.
