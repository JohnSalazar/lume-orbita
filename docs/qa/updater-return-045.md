# Self-update return correction — 0.4.5 candidate

## Status

The 0.4.5/code 31 candidate passed its JVM, coverage, release build, matching instrumentation build, lint and locale catalog gates. No 0.4.5 publication, physical installation or successful automatic return is claimed. The KONKA remains on the previously verified 0.4.4/code 30 until a real update is authorized and confirmed.

The user explicitly authorized publishing 0.4.5 and testing it on the KONKA after reviewing the candidate. A fresh private baseline was exported and verified before publication: 16 typed preferences, original signing identity, Lume selected as Home, content permission granted, installation-source access already enabled and no owned installer sessions. The old incomplete 0.4.4 attempt markers were recorded privately and were not cleared to conceal the earlier defect.

Executed final gate: **186 JVM tests**, no failures/errors/skips; core coverage **464/465 lines and 911/953 branches**, with the new recovery policy at **19/19 lines and 94/94 branches**. Release and release-instrumentation builds succeeded in the final 8m 19s Gradle invocation. Lint reported **0 errors and 17 warnings**. Locale validation passed **21 catalogs and 21 guides**; no translated UI resources were added or changed. The six new Android adapter contracts compiled but have **not been executed**.

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
- [ ] Execute Android contracts in an isolated test preference namespace without changing user configuration or Home; distinguish simulated replacement evidence from a native installation.
- [x] Verify candidate APK package, version, signer and complete hash.
- [x] Export and verify a fresh private physical-device baseline before the human-driven update. Recheck its equality immediately before the user starts if intervening actions may have changed preferences.
- [x] Obtain separate publication approval if GitHub delivery is required; do not fabricate a release or bypass the newer-version check.
- [ ] Publish the reviewed APK and English documentation, then verify the public tag, metadata, checksum manifest and exact anonymously downloaded asset bytes.
- [ ] Have the user confirm the real installation with the remote. Do not run instrumentation during download, staging or native confirmation.
- [ ] Observe the post-install foreground destination and any error; verify the installed APK, exact configuration preservation, Home, content permission and terminal updater state.

## Platform limits

Android controls process replacement, task visibility and background activity starts. The application can request return to its own selected Home; it cannot promise that no frame of a previous task will appear during the system transition. An OEM refusal to reopen must leave a correct recorded result for a subsequent Home press or manual launch.

Official references: [PackageInstaller commit callback](https://developer.android.com/reference/android/content/pm/PackageInstaller.Session#commit(android.content.IntentSender)), [protected package-replacement event](https://developer.android.com/reference/android/content/Intent#ACTION_MY_PACKAGE_REPLACED), and [Android activity launch restrictions](https://developer.android.com/guide/components/activities/secure-bal).
