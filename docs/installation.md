# Installation, Home selection, and recovery

Install only on Android TV or Google TV 11 or later. Confirm the device OS and its official debugging method. ADB must be authorized or paired. `SERIAL` and `USER_ID` below are placeholders; replace them explicitly, especially when multiple devices are connected.

```sh
adb devices
adb -s SERIAL shell am get-current-user
adb -s SERIAL shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
adb -s SERIAL install -r lume-orbita-0.4.3.apk
adb -s SERIAL shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
```

Record the previous Home component before installing. Some firmware can reset the preference when another launcher is added. An update must retain the package and signing certificate. Never uninstall Lume to force an incompatible update.

Compare Home before and after installation. If the choice unexpectedly changes or disappears, restore the previous selection through the device's official settings. A reset is not authorization to select another launcher automatically. After confirming a working Home, open Lume for evaluation:

```sh
adb -s SERIAL shell am start -n br.com.lume.launcher/.LauncherActivity
```

## Default Home

Evaluate the application first. To select it as Home, use Lume's Home-selection option or Android's default-app settings and confirm on the TV. OEM support varies; installation alone does not guarantee Home integration.

If supported, the following advanced command requires explicit owner authorization:

```sh
adb -s SERIAL shell cmd package set-home-activity --user USER_ID br.com.lume.launcher/.LauncherActivity
```

Recheck the resolver and test Home, Back, Android settings, HDMI, apps, and standby separately. Keep the previous launcher available during evaluation. Root, accessibility interception, system-app disabling, and firmware changes are not required.

## Updates from About

Version 0.4.3 adds **About > Check for updates**. Download and verify a newer compatible APK, select **Install update**, and confirm Android's prompt. If requested, the user must grant installation-source access personally in Android settings, then return and continue. Lume does not grant this permission itself. See the [GitHub update guide](operations/github-updates.md).

## Recovery and removal

Choose the previous launcher in official settings and verify Home before uninstalling Lume. If Lume cannot open but ADB remains authorized, start the previously recorded Home component and select it using the device's supported method. Never remove system components. The repository does not distribute backups of third-party launchers or scripts tied to a particular TV.

An older APK is not a guaranteed data-preserving downgrade for a non-debuggable app. A recovery build may need the retained certificate and a compatible version code. Preserve a private backup and follow the [device validation procedure](operations/device-validation.md).

## Shared content and privacy

Continue Watching and Suggestions depend on Android records supplied by installed apps and optional `READ_TV_LISTINGS` access. The user grants access on the TV. Lume has no platform login, application backend, or telemetry. Its app catalog uses enabled, exported LAUNCHER/LEANBACK_LAUNCHER activities; disabled apps and services without a launchable screen are not imported as shortcuts.
