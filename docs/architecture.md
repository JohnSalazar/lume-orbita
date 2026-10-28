# Architecture

- `src/android/app/src/main/java/br/com/lume/launcher`: Activity, shared UI, app catalog, Android actions, shared content, in-memory images, screensaver, and manual updates.
- `core/`: pure rules that can be tested without a device.
- `app/src/test`: JVM tests for policies, limits, cancellation, and fake transport.
- `app/src/androidTest`: separate device-only instrumentation; not distributed in the release APK.
- `app/src/main/assets`: original atmospheres and remote-photo metadata/attribution.

The application uses Kotlin and native Views, with no WebView, NDK, application server, or persistent automation service. The manifest declares Home and Leanback; Android determines the Home selection. D-pad navigation retains real focus on content headers while previewing the remembered card independently. Preview metadata does not update the remembered selection; real card focus does. Persistent identifiers and platform content caches remain private app data.

Static materials and native transforms avoid expensive blur on Android 11. Image caches are bounded in RAM, paused with the UI, and released on destruction; offscreen content can be evicted. A terminated process must reload remote photos. Procedural scenes are rendered locally. Photo attribution remains accessible in screensaver settings.

The updater runs only after a user action. `GitHubUpdater` checks a fixed repository, downloads a bounded private temporary APK, and verifies the checksum, package, version, SDK, and current signing certificates. A separate bounded worker and UI watchdog isolate network stalls. `UpdateInstallActivity` uses a private, session-correlated PackageInstaller flow with Android confirmation. APK staging requires disk storage and does not change the screensaver's RAM-only photo policy. See the [security contract](design/update-security.md) and [operations guide](operations/github-updates.md).
