# Automatic updater APK cleanup — 0.4.6

## Scope and current status

The owner requested automatic cleanup after a successful update, including APK remnants from failed older attempts. The public evaluation prerelease is 0.4.6/code 32. Implementation, review, build gates and public-asset verification passed. The user explicitly authorized publication and testing after artifact review. Physical installation and cleanup validation are still pending; this document does not replace human native confirmation.

Final Gradle gate succeeded in **11m 37s**: release and matching instrumentation builds, unit tests, core coverage and lint. JUnit reported **191 cases: 190 passed, zero failures/errors and one explicit skip**. The skipped case checks file and directory symlinks; Windows denied symbolic-link creation privilege. This is not a passing symlink test. Core coverage is **466/467 lines and 917/959 branches**, with the recovery/cleanup policy at **21/21 lines and 100/100 branches**. Lint: **0 errors, 17 warnings**. Locale verification passed all **21 catalogs and 21 guides**, with no translated UI-resource changes.

Reviewed APK: `br.com.lume.launcher`, 0.4.6/code 32, minimum SDK 30, target SDK 35, **10,251,865 bytes**, SHA-256 `6d2bc7a05c1d073674e10e46e0f1ad60c1b1968eea959bf2b5bf670fac88de19`. Signature verification passed with the unchanged evaluation certificate `c435b38a840b6381b56c2f71f6e914f7958218762139b47ecd9607793cd67cdb`. The updated six Android contracts compiled but remain unexecuted; their real POSIX file-symlink check does not cover a directory alias until separately exercised.

The physical KONKA remains on 0.4.5/code 31. That update installed without an error popup, but Android blocked automatic foreground return and resumed HBO. Lume remains the selected Home. The [observed 0.4.5 result](updater-return-045.md) distinguishes that restriction, the successful installation and six passing isolated recovery contracts. No permission workaround is introduced in this candidate.

## Implemented policy

After a successful result is committed, the existing worker performs cleanup after any best-effort Home request. A later foreground reconciliation can retry when the recorded success belongs to the currently installed version and no new attempt owner exists. A cleanup failure does not reclassify success as failure.

The client registers each generated APK atomically with creation. Its lease covers download, the prepared file waiting for confirmation, transfer to another updater instance and staging until discard. The same process-wide lock guards cleanup decisions. Only regular, non-symlink files matching `lume-update-[A-Za-z0-9-]+.apk` under the canonical private updater-cache directory are eligible; there is no recursion. Public maintenance refuses to run while any own native installer session exists or its state cannot be read. No session is abandoned by this policy.

Each pass examines at most 4,096 entries with a cooperative two-second budget. File/OS operations can exceed that cooperative limit. Unremoved files remain eligible for a later retry, while active sources remain protected. The old 24-hour expiry on manual checks reuses the same ownership-aware policy.

## Validation checklist

- [x] Review cleanup trust boundaries, active-source ownership, failure handling and worker hooks.
- [x] Add four filesystem/concurrency regressions and a pure success-proof gate regression.
- [x] Isolate the Android adapter's cache in a private UUID fixture, with synthetic orphan files, retry, sentinel preservation and a real POSIX file symlink. No actual navigation or real updater-cache writes are performed by the contract.
- [x] Execute JVM tests, coverage, release build, matching instrumentation build, lint and locale validation; record actual results and the explicit Windows symlink skip.
- [x] Verify the final APK identity, byte count, hash and original signing certificate.
- [x] Obtain publication authorization for GitHub delivery; retain the TV on 0.4.5 for the real in-app transition.
- [x] Publish and verify the public release tag, four assets, checksum manifest and exact anonymously downloaded bytes.
- [x] Export and verify a fresh private baseline: 16 typed preferences and both raw preference files, current 0.4.5 APK/signature, Lume Home, content/source access and an empty own-session list. Recheck equality immediately before the human update instructions.
- [ ] Have the user confirm the native update, then verify the installed APK and recorded full target-identity result. Do not run instrumentation during installation.
- [ ] Execute the updated six isolated Android contracts and inspect actual updater-cache state; distinguish synthetic orphan deletion from a real failed-download remnant.
- [ ] Export evidence, remove only owned test artifacts and uninstall the auxiliary test package; retain host backups and the user's installed apps/configuration/Home.

## Limits

This policy removes updater APK copies and partials, not the tiny bounded success journal needed for verification and retry. Android manages native installer staging. The candidate does not claim to bypass the KONKA's foreground-start restriction, guarantee deletion during an I/O failure, or remove a file still in use. No root, firmware change, broad storage access or other-app cleanup is involved.

## Publication evidence

Published [v0.4.6](https://github.com/JohnSalazar/lume-orbita/releases/tag/v0.4.6), an evaluation prerelease. Its tag resolves to `a6448c186f34b8a4d0c10b78e8cf15bf90d70d8d`. All four assets were anonymously downloaded and compared byte-for-byte against the reviewed local files; GitHub's asset digests also matched. Checksum-manifest validation and documentation ZIP integrity passed.

| Asset | Bytes | SHA-256 |
|---|---:|---|
| APK | 10,251,865 | `6d2bc7a05c1d073674e10e46e0f1ad60c1b1968eea959bf2b5bf670fac88de19` |
| English documentation ZIP | 66,399 | `227d819a8499816bd27a2a19f20a82f1ba6da7dbaae6cd33bcf50e44908d7223` |
| Release notes | 3,522 | `6d327c170c7ab96534a19c0a25140ba5b12a0e2092972962d821db034c7684e5` |
| SHA256SUMS.txt | 285 | Exact bytes and API digest verified |

The published APK/assets/tag remain fixed. The host retains a verified private 16-entry baseline and raw updater evidence. The last external TV read before the human instructions still showed 0.4.5/code 31. The user was instructed to confirm 0.4.6 through Lume, then press physical Home and report the outcome. Do not run instrumentation or navigate the TV during download, staging or native confirmation. The updated six Android contracts and final owned-artifact cleanup await a terminal human report and actual code-32 confirmation.
