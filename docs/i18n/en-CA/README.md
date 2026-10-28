# lume órbita — user and contributor guide

QA: [0.4.0](../../qa/i18n-validation.md) · [0.4.1](../../qa/konka-041-validation.md) · [0.4.2](../../qa/clock-locale-042.md)

Original launcher for Android TV and Google TV **11 or later**. Native Kotlin Views; no root or modified firmware. Visual design, favourites and platform content remain the same across languages.

## Language
Open the top settings gear, then **Language**. Automatic follows the first supported device language; otherwise English (United States). A manual choice persists across restarts. Return to Automatic whenever needed. This changes Lume only, including its screensaver; it does not translate Android settings, other apps, publisher titles or descriptions.

Supported locales: en-US, en-GB, en-CA, pt-BR, pt-PT, es-ES, fr-FR, zh-CN, hi-IN, ar, bn-BD, ru-RU, ur-PK, ja-JP, pnb-Arab-PK, mr-IN, de-DE, it-IT, ko-KR, id-ID, tr-TR. Chinese is Simplified; Western Punjabi uses Shahmukhi. Arabic, Urdu and Western Punjabi mirror layout and app-owned horizontal navigation.

## Remote and daily use
- Arrows move focus. OK opens a favourite; in the app library OK adds/removes a favourite. Hold OK for quick actions such as move, app info and the Android uninstall confirmation.
- Continue watching and Suggestions switch previews when their headers receive focus. Left/right move freely across headers and neighboring controls. Down or OK enters the remembered card. Provider data and artwork depend on what installed platforms share with Android and on your permission.
- Favourites retain their order. App labels/icons come from the installed apps. Install apps opens Google Play; Lume does not install APKs silently.
- Network opens Android network settings. The gear opens launcher preferences and Android settings. HDMI depends on the device manufacturer; the remote Input button remains a fallback.
- Cinema dims and warms the launcher and pauses visual effects. It does not modify the TV picture settings. Optional 20:00–06:00 schedule follows the TV's local time and can be disabled.
- Atmospheres include daily rotation and manual choice. Screensaver preferences control idle delay, image interval, dimming and clock. Online photos are decoded in RAM without an app photo disk cache. Offline procedural scenes rotate while photos are unavailable. Arrows, OK or Back exit. Android controls screensaving outside the launcher and standby.

## Install, update and recover
Enable authorized Android debugging using the device's official settings. Confirm the exact target serial before every ADB command:

Before and after `adb install -r`, record and recheck the selected default Home app and what the Home button opens. An update may change the Home selection on some devices; do not assume it is preserved. Keep another working launcher available and restore your choice through Android’s selector if needed.

```sh
adb devices
adb -s DEVICE_SERIAL install -r app-release.apk
```

Updates must keep the same package and signing certificate to preserve preferences. Keep the signing key private; APKs signed by a different certificate cannot update the existing installation. Do not clear app data to change language.

Choose Lume through Android's default Home selector only if the manufacturer supports it. Verify Home from another app and test standby separately. Before uninstalling the default launcher, install/select another working launcher and verify Home. Then uninstall Lume through Android app settings. No firmware changes, root, protected-setting changes or removal of system components are needed. Home and standby behaviour vary by manufacturer.

## Build and verification
Requires JDK 17, Android SDK Platform 35 and Build Tools 36.0.0. Configure JAVA_HOME and ANDROID_HOME. From the repository root, regenerate resources before verification and the build:

```sh
python tools/build_locale_resources.py
cd src/android
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

Windows: use `gradlew.bat`. APK: `app/build/outputs/apk/release/app-release.apk`. From repository root:

```sh
python tools/verify_locales.py
```

Translation sources live under tools: common/activity catalogues and per-locale translations. Regenerate resources after catalog changes; placeholders and persistent IDs must remain intact. `LocaleSmokeInstrumentation` is a test-only runner for app-level locale screens; it does not change the device language or Home role. Test on a dedicated device/emulator and consult the QA report for observed results. Resource coverage and UI tests do not certify every translation as reviewed by native speakers or guarantee absence of bugs. A phone AVD cannot certify TV integrations or physical remote behaviour.

## Licence and limitations
Original code: MIT, copyright 2026 Joao Salazar. See the repository LICENSE and THIRD_PARTY_NOTICES.md. Photos, libraries, trademarks and publisher content retain their own rights; photo authors and licence URLs remain unchanged. External streaming accounts/content are not translated, accessed privately or guaranteed available. Universal compatibility and performance require hardware-specific testing.

Validation status is recorded in docs/qa; do not infer completed tests from this guide. Installation and recovery details: docs/installation.md. Build/signing: docs/build.md.
