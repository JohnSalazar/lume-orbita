# lume órbita

An original launcher for **Android TV and Google TV 11 or later**, implemented in Kotlin with native Views. It combines a translucent glass interface, remote-control navigation, and local customization without root access.

## Release 0.4.4

**Evaluation prerelease** — package `br.com.lume.launcher`, version code `30`, minimum SDK `30`.

[Download APK](https://github.com/JohnSalazar/lume-orbita/releases/download/v0.4.4/lume-orbita-0.4.4.apk) · [Release assets and SHA-256 checksums](https://github.com/JohnSalazar/lume-orbita/releases/tag/v0.4.4) · [Release notes](docs/releases/0.4.4.md) · [US English update guide](docs/operations/github-updates.md)

This maintenance release correctly reports APK storage-write failures as storage errors while retaining cancellation and network-error handling. The manual updater introduced in 0.4.3 remains available under **Settings > About > Check for updates**. Checks run only when requested. A newer compatible release can be downloaded, verified, and handed to Android for explicit installation confirmation. If Android requires installation-source access, the user must grant it in Android settings. The app never grants that access itself, silently installs a package, uninstalls the launcher, clears its data, or automatically changes Home.

The existing evaluation signing certificate is retained. A custom build signed with a different key cannot update the official installation in place. See [build and signing](docs/build.md).

## Features

- Installed launchable apps with device-provided icons, reorderable favorites, and long-press actions.
- Continue Watching and Suggestions based on content providers exposed by installed platforms.
- Header navigation with an independent preview of the remembered card; Down or OK enters the card.
- Lightweight glass materials, focus halos, animated RGB favorites, and an optional local Cinema schedule.
- Thirty packaged atmospheres with daily rotation; online screensaver photos or randomly selected procedural scenes.
- Locale-default clocks, including the US 12-hour cycle with AM/PM and localized digits where applicable.
- Android settings, network settings, app-store access, and manufacturer-exposed inputs.
- Home integration through Android/OEM facilities, with no global interception or firmware changes.
- Manual GitHub updates with bounded downloads, checksum and certificate verification, and user confirmation.

## Updating from About

1. Select **Check for updates** in **About**.
2. If a newer compatible version is available, select **Download update**.
3. After verification, select **Install update**. If prompted, open **Allow this source**, grant access yourself, and return to confirm.
4. Complete Android's installation prompt. Verify the launcher configuration and Home selection afterward.

Updates preserve app data through Android's same-package update mechanism. If an OEM changes the Home preference, Lume reports the change and offers access to the official selector. It does not silently change protected settings. See the [update operations guide](docs/operations/github-updates.md), [security contract](docs/design/update-security.md), and [installation and recovery guide](docs/installation.md).

## Localization

The application supports 21 locales: English (US, UK, Canada), Portuguese (Brazil, Portugal), Spanish, French, Simplified Chinese, Hindi, Modern Standard Arabic, Bengali, Russian, Urdu, Japanese, Western Punjabi (Shahmukhi), Marathi, German, Italian, Korean, Indonesian, and Turkish.

Automatic selection uses the first supported device language, with **English (United States)** as the fallback. Manual selection changes only the launcher. Platform-supplied titles and descriptions retain their original language.

[US English user and contributor guide](docs/i18n/en-US/README.md) · [Localized guides in the repository](https://github.com/JohnSalazar/lume-orbita/tree/v0.4.3/docs/i18n)

## Build and test

Requirements: JDK 17, Android SDK Platform 35, and Build Tools 36.0.0. The Gradle wrapper supplies Gradle 8.11.1. Configure `JAVA_HOME` and `ANDROID_HOME`, then run from `src/android`:

```sh
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

On Windows, use `gradlew.bat`. Release output: `app/build/outputs/apk/release/app-release.apk`.

Build the auxiliary native test APK with `assembleReleaseAndroidTest`. `testBuildType = "release"` keeps Kotlin internal API linkage compatible with the installed release APK. JVM tests continue to use `testDebugUnitTest`. Test APKs are not release assets.

From the repository root, `python tools/build_locale_resources.py` generates Android resources, and `python tools/verify_locales.py` validates catalogs, placeholders, and guides. `--self-check` exercises the verifier with isolated fixtures.

[Architecture](docs/architecture.md) · [Build and signing](docs/build.md) · [Update validation report](docs/qa/updater-validation-043.md) · [Physical-device validation procedure](docs/operations/device-validation.md)

## Validation and limitations

The 0.4.3 validation recorded 172 passing JVM tests, eight Android contract cases, one real public GitHub check, and 294 updater-state panels across 21 locales. All 483 new compiled strings matched their catalogs. Sixteen typed preferences, Home, installer access, and installer-session state were preserved. See the [validation report](docs/qa/updater-validation-043.md) for exact scope and limitations.

**A future release has not been installed end to end through PackageInstaller.** Installation-state fixtures are not proof of a completed update. The no-update check was performed when the installed 0.4.3 was newer than the public 0.4.2. Publication of 0.4.3 does not turn that historical check into an installation test.

Validation on one KONKA Android TV 11 device does not certify other OEMs, resolutions, physical remotes, standby behavior, or performance. Translations have not been certified by native-language reviewers. Home and resume from standby are separate OEM-dependent behaviors.

Screensaver photos are loaded into RAM without an app-managed disk photo cache. Update APKs require separate temporary private storage. Shared recommendations, artwork, progress, and links depend on what each platform exposes; Lume does not access private streaming accounts.

## License

Original code and documentation: [MIT](LICENSE), copyright 2026 Joao Salazar. Third-party photos, libraries, tools, trademarks, and publisher content retain their own rights: [third-party notices](THIRD_PARTY_NOTICES.md).

Signing keys, personal backups, private test evidence, and third-party application backups are not distributed in this repository or its release assets.
