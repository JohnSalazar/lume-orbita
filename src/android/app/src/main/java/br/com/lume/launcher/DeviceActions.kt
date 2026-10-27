package br.com.lume.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.net.Uri
import android.provider.Settings
import br.com.lume.launcher.core.LauncherRules
import java.text.Collator
import java.util.Locale

class DeviceActions(private val activity: Activity) {
    private fun open(intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    fun openApp(app: InstalledApp): Boolean = open(Intent.makeMainActivity(app.component))

    fun openProgram(program: br.com.lume.launcher.core.SharedProgram): Boolean =
        br.com.lume.launcher.core.ContentRules.safeData(program.data) &&
            open(Intent(Intent.ACTION_VIEW, Uri.parse(program.data)).setPackage(program.packageName).addCategory(Intent.CATEGORY_BROWSABLE))

    fun openAppDetails(app: InstalledApp): Boolean =
        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null)))

    fun canUninstall(app: InstalledApp): Boolean = try {
        val packages = activity.packageManager
        val info = packages.getApplicationInfo(app.packageName, 0)
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val home = packages.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.takeIf { resolved ->
            packages.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY).any { candidate ->
                candidate.activityInfo?.let { it.packageName == resolved.packageName && it.name == resolved.name } == true
            }
        }?.packageName
        val system = info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        LauncherRules.canRequestUninstall(app.packageName, activity.packageName, home, system)
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    @Suppress("DEPRECATION")
    fun requestUninstall(app: InstalledApp): Boolean = canUninstall(app) &&
        open(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.fromParts("package", app.packageName, null)))

    fun openSettings(): Boolean = open(Intent(Settings.ACTION_SETTINGS))

    fun openScreensaverSettings(): Boolean = open(Intent(Settings.ACTION_DREAM_SETTINGS))

    fun openScreensaver(): Boolean = open(Intent(activity, ScreenSaverActivity::class.java))

    fun openNetwork(): Boolean = open(Intent(Settings.ACTION_WIFI_SETTINGS)) ||
        open(Intent(Settings.ACTION_WIRELESS_SETTINGS)) || openSettings()

    fun openStore(packageName: String? = null): Boolean {
        val intent = if (packageName == null) {
            val packages = activity.packageManager
            packages.getLeanbackLaunchIntentForPackage("com.android.vending")
                ?: packages.getLaunchIntentForPackage("com.android.vending")
                ?: return false
        } else {
            val url = LauncherRules.storeUrl(packageName) ?: return false
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.android.vending")
        }
        return open(intent)
    }

    fun isHome(): Boolean {
        val roles = activity.getSystemService(RoleManager::class.java) ?: return false
        return roles.isRoleAvailable(RoleManager.ROLE_HOME) && roles.isRoleHeld(RoleManager.ROLE_HOME)
    }

    @Suppress("DEPRECATION")
    fun requestHome(): Boolean {
        val roles = activity.getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
            if (roles.isRoleHeld(RoleManager.ROLE_HOME)) return true
            try {
                // This controller identifies the requesting app through the result caller.
                activity.startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 1001)
                return true
            } catch (_: ActivityNotFoundException) {
                // Some TV firmware advertises a role without a usable request screen.
            } catch (_: SecurityException) {
                // Fall back to the user-controlled system Home selector.
            }
        }
        return open(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    fun inputs(): List<Pair<String, String>> {
        val manager = activity.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return try {
            val collator = Collator.getInstance(Locale.forLanguageTag("pt-BR"))
            manager.tvInputList.filter { it.isPassthroughInput && !it.isHidden(activity) }
                .map { input ->
                    input.id to (input.loadCustomLabel(activity) ?: input.loadLabel(activity)).toString()
                        .ifBlank { input.id }
                }
                .sortedWith { first, second -> collator.compare(first.second, second.second) }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    fun openInput(inputId: String): Boolean {
        if (inputs().none { it.first == inputId }) return false
        val uri = TvContract.buildChannelUriForPassthroughInput(inputId)
        return open(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
