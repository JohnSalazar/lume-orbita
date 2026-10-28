package br.com.lume.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import br.com.lume.launcher.core.InstallerAttemptRules
import java.util.UUID

/** Public user-consented entry points. Never uninstalls, clears data or changes Home. */
object UpdateInstaller {
    internal const val RESULT_ACTION = "br.com.lume.launcher.UPDATE_INSTALL_RESULT"
    internal const val PREFS = "lume_updater"
    // All lease reads/writes use this object's monitor. Never release a staging lease before its worker exits.
    private var liveOwner: String? = null
    private var retiringOwner: String? = null

    fun canInstall(context: Context): Boolean = try { context.packageManager.canRequestPackageInstalls() }
        catch (_: SecurityException) { false }

    fun openPermissionSettings(activity: Activity): Boolean = try {
        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
        true
    } catch (_: ActivityNotFoundException) { false }
      catch (_: SecurityException) { false }

    /** True transfers ownership of the private temporary APK to UpdateInstallActivity. */
    fun launch(activity: Activity, prepared: PreparedUpdate): Boolean {
        if (!canInstall(activity) || activity.isFinishing || activity.isDestroyed) return false
        val owner = claim(activity) ?: return false
        val release = prepared.release
        return try {
            activity.startActivity(Intent(activity, UpdateInstallActivity::class.java).apply {
                putExtra("owner", owner)
                putExtra("file", prepared.apk.name); putExtra("sha256", prepared.sha256)
                putExtra("code", prepared.versionCode); putExtra("version", release.version)
                putExtra("tag", release.tag); putExtra("page", release.pageUrl)
                putExtra("apk", release.apkUrl); putExtra("sums", release.checksumsUrl)
                putExtra("bytes", release.apkBytes); putExtra("prerelease", release.prerelease)
            })
            true
        } catch (_: ActivityNotFoundException) { abandonOwned(activity, owner, -1); false }
          catch (_: SecurityException) { abandonOwned(activity, owner, -1); false }
    }

    @Synchronized private fun claim(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (retiringOwner != null) return null
        val previous = prefs.getString("owner", null)
        if (previous != null && liveOwner == previous) return null
        try {
            val id = prefs.getInt("session", -1)
            if (previous != null && id >= 0 && context.packageManager.packageInstaller.getSessionInfo(id) != null) return null
            // Fail closed on a process-death window between createSession and persisting its id.
            // Never abandon an unrecorded session or another package's session.
            if (context.packageManager.packageInstaller.mySessions.any { it.appPackageName == context.packageName }) return null
        } catch (_: RuntimeException) { return null }
        val owner = UUID.randomUUID().toString()
        if (!prefs.edit().putString("owner", owner).putInt("session", -1).remove("token").commit()) return null
        liveOwner = owner
        return owner
    }

    @Synchronized internal fun attach(context: Context, owner: String): Boolean {
        val active = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("owner", null)
        if (owner.isBlank() || owner != active || retiringOwner != null || (liveOwner != null && liveOwner != owner)) return false
        liveOwner = owner
        return true
    }

    @Synchronized internal fun recordSession(context: Context, owner: String, id: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (id < 0 || !InstallerAttemptRules.owns(prefs.getString("owner", null), prefs.getInt("session", -1), owner, -1)) return false
        return prefs.edit().putInt("session", id).commit()
    }

    @Synchronized internal fun prepareCommit(context: Context, owner: String, id: Int, token: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!InstallerAttemptRules.owns(prefs.getString("owner", null), prefs.getInt("session", -1), owner, id) ||
            prefs.getString("token", null) != null) return false
        @Suppress("DEPRECATION")
        val code = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        return prefs.edit().putString("token", token).putString("home_owner", owner)
            .putString("before_home", currentHome(context)).putLong("before_code", code).commit()
    }

    @Synchronized internal fun acceptsCallback(context: Context, owner: String, id: Int, token: String?): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return InstallerAttemptRules.acceptsCallback(prefs.getString("owner", null), prefs.getInt("session", -1),
            prefs.getString("token", null), owner, id, token)
    }

    @Synchronized internal fun finishAttempt(context: Context, owner: String, id: Int, token: String?, keepHome: Boolean): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!InstallerAttemptRules.owns(prefs.getString("owner", null), prefs.getInt("session", -1), owner, id) ||
            token != prefs.getString("token", null)) return false
        val edit = prefs.edit().remove("owner").remove("session").remove("token")
        if (!keepHome && prefs.getString("home_owner", null) == owner)
            edit.remove("home_owner").remove("before_home").remove("before_code")
        edit.commit()
        if (liveOwner == owner) liveOwner = null
        return true
    }

    /** Worker cleanup for exactly the recorded attempt. Stale callbacks/hosts cannot clear a newer lease. */
    internal fun abandonOwned(context: Context, owner: String, id: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val token = synchronized(this) {
            if (!InstallerAttemptRules.owns(prefs.getString("owner", null), prefs.getInt("session", -1), owner, id)) return
            if (retiringOwner != null) return
            retiringOwner = owner
            prefs.getString("token", null)
        }
        try {
            if (id >= 0) {
                val installer = context.packageManager.packageInstaller
                val session = installer.getSessionInfo(id)
                if (session != null) {
                    if (session.installerPackageName != context.packageName) return
                    installer.abandonSession(id)
                    if (installer.getSessionInfo(id) != null) return
                }
            }
            finishAttempt(context, owner, id, token, false)
        } finally {
            synchronized(this) {
                if (retiringOwner == owner) retiringOwner = null
                detach(owner)
            }
        }
    }

    @Synchronized internal fun detach(owner: String) { if (liveOwner == owner) liveOwner = null }

    internal fun currentHome(context: Context): String? = try {
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).resolveActivity(context.packageManager)?.flattenToString()
    } catch (_: RuntimeException) { null }

    /** Warning only, after an actual package update. Never launches the chooser automatically. */
    @Synchronized fun consumeHomeWarning(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val beforeCode = prefs.getLong("before_code", 0L)
        if (beforeCode <= 0) return false
        @Suppress("DEPRECATION")
        val currentCode = try { context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode }
            catch (_: Exception) { return false }
        if (currentCode <= beforeCode) return false
        val beforeHome = prefs.getString("before_home", null)
        prefs.edit().remove("home_owner").remove("before_home").remove("before_code").apply()
        return beforeHome != null && currentHome(context) != beforeHome
    }
}
