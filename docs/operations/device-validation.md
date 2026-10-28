# Physical TV update and reversible validation

Use the TV's explicit ADB serial on every command. Never clear launcher data or uninstall the launcher to force an update. Preserve its signing key. A lower version APK is not a guaranteed rollback for a non-debuggable Android application.

## Before updating

1. Record installed version, resolved HOME activity and existing content permission. Pull the installed base APK privately as a secondary recovery artifact.
2. Build the release and test APKs, run JVM tests, coverage gate, lint and locale verification. Verify that both APK certificates match the installed application.
3. Install only the auxiliary `br.com.lume.launcher.test` package first. Run `DeviceStateSelfTestInstrumentation`, which exercises typed snapshots in an exclusive temporary preference file. Its context wrapper redirects every preference call away from the user's orbita preferences; the temporary file is deleted afterward.
4. Run `DeviceStateInstrumentation` with `action=backup` and a unique `snapshot` ID. It flushes existing preferences, records every `orbita` preference with its type, copies private shared preferences/files, and writes a digest marker last. An incomplete snapshot is rejected.
5. Copy the complete `maintenance-state/<snapshot>` directory to private host storage. If the OEM denies adb pull, use the bounded export below. Verify the ZIP digest, typed snapshot digest, all raw manifest paths, byte counts and SHA-256 hashes before updating. Keep content history, configuration, screenshots and backup APKs out of the public repository.

```text
adb -s SERIAL install -r TEST_APK
adb -s SERIAL shell am instrument -w br.com.lume.launcher.test/br.com.lume.launcher.DeviceStateSelfTestInstrumentation
adb -s SERIAL shell am instrument -w -e action backup -e snapshot UNIQUE_ID br.com.lume.launcher.test/br.com.lume.launcher.DeviceStateInstrumentation
```

The maintenance runner uses the matching certificate and standard instrumentation APIs; it does not use root. Its `verify` and `restore` actions require the same snapshot ID. Restore validates the entire typed snapshot before replacing preferences and checks the resulting digest. It does not automatically replace copied private files. The digest detects accidental corruption; it is not encryption or authentication against deliberate tampering.

For an OEM storage restriction, `action=export` accepts exactly one `snapshot` ID (validated journal) or `output` name (one direct child evidence folder). Redirect the entire instrumentation output to a private host file; never paste its `archive_base64` payload in chat or the repository. It also returns archive size/SHA-256. The in-memory ZIP is limited to192KiB compressed and8MiB raw, rejects links/unsafe paths, and writes no ZIP or photo pixels to TV storage. Validate safe relative paths and hashes on the host before extracting. Larger evidence matrices can run in separate locale batches; merge only unique pairs from the same APK.

## Update and test

Install the release with `adb -s SERIAL install -r RELEASE_APK`. Verify the installed version, HOME activity, content permission and baseline preference digest before opening the updated app.

Start with one locale, then run the complete locale matrix. `LocaleSmokeInstrumentation` journals all preferences and restores their exact types, values and absence of keys after the test. It uses the real in-app language selector, scoped locales, DPAD navigation, dialogs and native screensaver activity. It opens Android Settings temporarily to safely finish a launcher that is already the default HOME; it does not change any Android setting. Return to HOME only after inspecting restoration results. Use `-e screenshots false` to avoid writing screenshot files to limited TV storage.

`ScreensaverPlaybackInstrumentation` tests the actual renderer and its 30-second timer: two online photos or three procedural frames. `mode=offline` injects an empty catalog only into the tested renderer instance, without changing device connectivity. This validates procedural rotation with no available photos, not a real network outage or reconnection. Photo pixels remain in RAM and `FLAG_SECURE` remains enabled; only metadata is recorded. The test restores preferences independently of cleanup success and reports failures.

After all tests, compare against the original baseline again, verify HOME and permissions, remove only the auxiliary test package, and reopen the launcher. Preserve private recovery artifacts on the host. Review test output for failures; a completed instrumentation process alone does not mean its assertions passed. Physical remote, standby and visual quality require actual user/device checks; do not infer them from an emulator or benchmark performance from functional tests.
