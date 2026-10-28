# Build and signing

Requirements: JDK 17, Android SDK Platform 35, and Build Tools 36.0.0. The source pins AGP 8.9.2 and Kotlin 2.1.20. The wrapper pins Gradle 8.11.1 and its distribution SHA-256.

Configure `JAVA_HOME` and `ANDROID_HOME`, or use a private `src/android/local.properties` with the appropriate `sdk.dir`. Do not commit local SDK configuration. Accept SDK licenses through the official tools.

From `src/android`:

```sh
./gradlew --no-daemon --max-workers=1 testDebugUnitTest verifyRulesCoverage assembleRelease lintRelease
```

On Windows, use `gradlew.bat`. Release output: `app/build/outputs/apk/release/app-release.apk`.

## Native instrumentation

Run `assembleReleaseAndroidTest` to build `app/build/outputs/apk/androidTest/release/app-release-androidTest.apk`. The project sets `testBuildType = "release"` so Kotlin internal names match the installed evaluation APK. A debug instrumentation APK can produce `NoSuchMethodError` when it calls release-internal methods; that mismatch is not a launcher runtime failure. JVM tests still use the debug variant.

Use an explicit ADB serial and inspect the [physical-device validation procedure](operations/device-validation.md) before testing. Auxiliary APKs and private evidence are not release assets.

## Coverage

The JaCoCo gate requires at least 90% line and branch coverage for `core/**`. Reports are written under `app/build/reports/jacoco/rulesCoverage` and `app/build/reports/lint-results-release.*`. Core coverage does not measure the entire Activity, graphics pipeline, network, PackageInstaller, or physical remote behavior.

## Signing

The local release configuration uses Android's development signing configuration with `isDebuggable = false`. The official APK is an evaluation build signed with the author's retained private key. It is not a Google Play deployment or a store-managed production-signing claim.

Keys, passwords, and local signing configuration are not in Git. A clone can generate a different key and certificate; a successful build does not imply byte-for-byte reproducibility or permission to update an official installation. Compatible updates require the same signing key. Forks should use their own identity and signing setup. Do not clear app data or uninstall the only working Home launcher to bypass a certificate mismatch.

Verify official downloads against the release SHA-256 checksums. The certificate fingerprint is public; the private key must never be published.
