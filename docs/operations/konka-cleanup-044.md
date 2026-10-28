# KONKA maintenance cleanup — October 28, 2026

The owner explicitly requested removal of obsolete project files, TV-side backups and Projectivy while retaining all currently used applications. No firmware, root, protected-setting change or application-data reset was authorized or performed.

## Verified upgrade

- The user installed 0.4.4 through Lume and confirmed that physical Home returns to Lume.
- Android reports version 0.4.4/code 30 and Lume as installing/initiating package. The installed APK hash and certificate match the published release.
- All 16 typed `orbita` preferences match the pre-upgrade baseline, without a restore. Home remains Lume and READ_TV_LISTINGS remains granted.
- A new post-upgrade backup, including raw preferences, was exported and verified on the private host before deleting TV-side backup files. The host copies were retained and were not published.

## Cleanup performed

- Removed only the non-system Projectivy package `com.spocky.projengmenu`; Android returned Success and the package no longer appears in the retained-package query.
- Copied `/data/local/tmp` to private host storage, verified the 92 selected Lume PNG/XML files were present, then removed those exact files. The unrelated `dalvik-cache` directory was retained.
- A temporary, matching-signature test APK inventoried the Lume external files and updater cache. Its cleanup used an explicit directory allowlist, APK filename validation, a complete preflight without following symlinks, and checks that no installer session was active. No production launcher APK was installed by ADB.
- Removed 100 files totaling 13,440,054 logical bytes from the reviewed maintenance/audit/old-test directories and temporary updater APK. The final inventory showed no external maintenance files or updater APKs remaining.
- Uninstalled the auxiliary `br.com.lume.launcher.test` package and reopened Lume.
- Preserved other applications, their data, streaming caches, Lume preferences and updater operational markers. No global cache trimming or `pm clear` was used.

## Executed checks

The auxiliary release-test APK built successfully. On-device selftests rejected nine unsafe path/symlink cases and retained unrelated sentinel files. Both the selftest and actual cleanup reported unchanged preferences and Home. The final Android package query confirmed 0.4.4/code 30, the Lume Home resolver, retained content permission and the expected remaining user applications.

`df -k /data` reported 737,868 KiB available before cleanup and 869,324 KiB afterward: a net increase of 131,456 KiB, approximately 128.4 MiB. Final available storage was approximately 849.0 MiB. Background Android writes can change these figures; they are observed filesystem values, not a sum of APK sizes.

## Remaining updater defect

The owner reported HBO appearing and an error popup after the successful upgrade, but could not recall the message. The captured Android log shows the installer-result Activity blocked by a background-start restriction after package replacement. No new Lume crash was found in that interval. The installer session list was empty while operational owner/session/token markers remained; their values and the log are retained privately.

This cleanup did not modify production callback handling or clear those markers to conceal the defect. A separate production fix and a subsequent real in-app upgrade test are still required. The exact popup text remains unverified.

## Recovery

Current settings backups remain on the private host. File copies can be recovered from that archive if needed; preferences must not be restored automatically to hide a mismatch. Projectivy would require deliberate reinstallation and reconfiguration if requested. The retained older Lume APK is not a guaranteed data-preserving downgrade; retain the signing identity and prefer a compatible forward repair. Never remove Lume while it is the active Home.
