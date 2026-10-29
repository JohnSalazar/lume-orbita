package br.com.lume.launcher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.content.ComponentName
import android.content.pm.PackageManager
import br.com.lume.launcher.core.InstallerAttemptRules
import br.com.lume.launcher.core.InstallJournal
import br.com.lume.launcher.core.InstallResolution
import br.com.lume.launcher.core.InstallReconciliationRules
import br.com.lume.launcher.core.UpdateRules
import java.util.UUID
import java.security.MessageDigest
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Public user-consented entry points. Never uninstalls, clears data or changes Home. */
object UpdateInstaller {
    internal const val RESULT_ACTION = "br.com.lume.launcher.UPDATE_INSTALL_RESULT"
    internal const val PREFS = "lume_updater"
    // All lease reads/writes use this object's monitor. Never release a staging lease before its worker exits.
    private var liveOwner: String? = null
    private var retiringOwner: String? = null
    private val reconciliationWorker = ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, ArrayBlockingQueue(1),
        { task -> Thread(task, "lume-update-result").apply { isDaemon = true } }).apply { allowCoreThreadTimeOut(true) }
    private val main = Handler(Looper.getMainLooper())
    private var reconciling = false
    @Volatile private var protectedReplacement = false
    private val reconciliationCallbacks = mutableListOf<(InstallResolution) -> Unit>()

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

    @Synchronized internal fun prepareCommit(context: Context, owner: String, id: Int, token: String, update: PreparedUpdate? = null): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!InstallerAttemptRules.owns(prefs.getString("owner", null), prefs.getInt("session", -1), owner, id) ||
            prefs.getString("token", null) != null) return false
        @Suppress("DEPRECATION")
        val code = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        val edit = prefs.edit().putString("token", token).putString("home_owner", owner)
            .putString("before_home", currentHome(context)).putLong("before_code", code)
            .putBoolean("commit_requested", true).putLong("interactive_at", System.currentTimeMillis())
        if (update != null) {
            val signers = currentSigners(context)
            if (update.versionCode <= code || !update.sha256.matches(Regex("[0-9a-f]{64}")) || signers.isEmpty()) return false
            edit.putLong("expected_code", update.versionCode).putString("expected_hash", update.sha256).putStringSet("expected_signers", signers)
        }
        return edit.commit()
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
            .remove("expected_code").remove("expected_hash").remove("expected_signers").remove("commit_requested").remove("interactive_at")
        if (!keepHome && prefs.getString("home_owner", null) == owner)
            edit.remove("home_owner").remove("before_home").remove("before_code")
        if (!edit.commit()) return false
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

    /** One process-wide worker and a finite coalesced callback set. Hashing never runs on the UI thread. */
    fun reconcile(context: Context, protectedReplace: Boolean = false, callback: (InstallResolution) -> Unit) {
        val app = context.applicationContext
        synchronized(this) {
            if (reconciliationCallbacks.size >= 8) { main.post { callback(InstallResolution.NEUTRAL) }; return }
            reconciliationCallbacks.add(callback)
            protectedReplacement = protectedReplacement || protectedReplace
            if (reconciling) return
            reconciling = true
        }
        reconciliationWorker.execute {
            var protectedPass = synchronized(this) { protectedReplacement }
            var result = runCatching { reconcileNow(app, protectedPass) }.getOrDefault(InstallResolution.NEUTRAL)
            val replay = synchronized(this) { InstallReconciliationRules.needsProtectedReplay(protectedPass, protectedReplacement) }
            if (replay) {
                protectedPass = true
                result = InstallReconciliationRules.mergeReplay(result,
                    runCatching { reconcileNow(app, true) }.getOrDefault(InstallResolution.NEUTRAL))
            }
            // Check-and-drain must share the same lock as enqueue, including a last-moment protected event.
            var callbacks = synchronized(this) {
                if (InstallReconciliationRules.needsProtectedReplay(protectedPass, protectedReplacement)) null else drainReconciliation()
            }
            if (callbacks == null) {
                result = InstallReconciliationRules.mergeReplay(result,
                    runCatching { reconcileNow(app, true) }.getOrDefault(InstallResolution.NEUTRAL))
                callbacks = synchronized(this) { drainReconciliation() }
            }
            val completedCallbacks = requireNotNull(callbacks)
            main.post { completedCallbacks.forEach { runCatching { it(result) } } }
        }
    }

    private fun drainReconciliation(): List<(InstallResolution) -> Unit> {
        val copy = reconciliationCallbacks.toList()
        reconciliationCallbacks.clear(); reconciling = false; protectedReplacement = false
        return copy
    }

    @Suppress("DEPRECATION")
    private fun currentSigners(context: Context): Set<String> = context.packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        ?.map { certificate -> hex(MessageDigest.getInstance("SHA-256").digest(certificate.toByteArray())) }?.toSet().orEmpty()

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }

    private fun installedHash(context: Context): String? {
        val file = File(context.applicationInfo.sourceDir)
        if (!file.isFile || file.length() !in 1..UpdateRules.MAX_APK_BYTES) return null
        val digest = MessageDigest.getInstance("SHA-256")
        val started = System.nanoTime(); var bytes = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                if (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started) >= 8) return null
                val count = input.read(buffer)
                if (count < 0) break
                bytes += count
                if (bytes > UpdateRules.MAX_APK_BYTES) return null
                digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest())
    }

    @Suppress("DEPRECATION")
    private fun reconcileNow(context: Context, protectedReplace: Boolean): InstallResolution {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ownHome = ComponentName(context, LauncherActivity::class.java).flattenToString()
        val journal = synchronized(this) {
            val owner = prefs.getString("owner", null)
            owner?.let { InstallJournal(it, prefs.getInt("session", -1), prefs.getString("token", null), prefs.getLong("before_code", 0),
                prefs.getLong("expected_code", 0), prefs.getString("expected_hash", null), prefs.getStringSet("expected_signers", emptySet()).orEmpty().toSet(),
                prefs.contains("expected_code") || prefs.contains("expected_hash") || prefs.contains("expected_signers"),
                prefs.getBoolean("commit_requested", false), prefs.getLong("interactive_at", 0), prefs.getString("home_owner", null),
                prefs.getString("home_owner", null) == owner && prefs.getString("before_home", null) == ownHome) }
        }
        if (journal == null) {
            cleanupCompletedDownloads(context)
            return InstallResolution.NEUTRAL
        }
        if (journal.session < 0 && synchronized(this) { liveOwner == journal.owner }) return InstallResolution.PENDING
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val installer = context.packageManager.packageInstaller
        var sessionExists = journal.session >= 0 && installer.getSessionInfo(journal.session) != null
        if (!journal.hasExpected && protectedReplace && sessionExists) {
            Thread.sleep(250) // One bounded recheck: package-replaced can precede session removal.
            sessionExists = installer.getSessionInfo(journal.session) != null
        }
        val home = currentHome(context)
        val isHome = home == ownHome
        val source = runCatching { context.packageManager.getInstallSourceInfo(context.packageName) }.getOrNull()
        val ownSource = source?.installingPackageName == context.packageName && source?.initiatingPackageName == context.packageName
        val actualHash = if (journal.hasExpected && info.longVersionCode == journal.expectedCode) installedHash(context) else null
        val result = InstallReconciliationRules.resolve(journal, info.longVersionCode, actualHash, currentSigners(context),
            sessionExists, protectedReplace, ownSource, isHome)
        var returnHome = false
        synchronized(this) {
            if (!InstallerAttemptRules.owns(prefs.getString("owner", null), prefs.getInt("session", -1), journal.owner.orEmpty(), journal.session) ||
                prefs.getString("token", null) != journal.token) return InstallResolution.NEUTRAL
            if (result == InstallResolution.VERIFIED || result == InstallResolution.LEGACY) {
                returnHome = InstallReconciliationRules.mayReturnHome(result, protectedReplace, journal.wasHome, isHome,
                    journal.interactiveAt, System.currentTimeMillis(), false)
                val edit = prefs.edit().putString("result_owner", journal.owner).putLong("result_code", info.longVersionCode)
                    .putString("result_kind", result.name).putBoolean("result_notified", false)
                    .putBoolean("result_home_changed", InstallReconciliationRules.homeChanged(prefs.getString("before_home", null), home))
                    .putBoolean("return_attempted", returnHome)
                listOf("owner", "session", "token", "before_code", "before_home", "home_owner", "expected_code", "expected_hash",
                    "expected_signers", "commit_requested", "interactive_at").forEach { edit.remove(it) }
                if (!edit.commit()) return InstallResolution.NEUTRAL
                if (liveOwner == journal.owner) liveOwner = null
            } else if (!sessionExists && !journal.hasExpected && (journal.beforeCode <= 0 || protectedReplace)) {
                // Unknown/legacy state is neutral, never a fabricated successful or failed install.
                finishAttempt(context, journal.owner.orEmpty(), journal.session, journal.token, false)
            }
        }
        if (returnHome && currentHome(context) == ownHome) {
            runCatching { context.startActivity(Intent(context, LauncherActivity::class.java).apply {
                action = Intent.ACTION_MAIN; addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }) } // BAL/OEM refusal is not an installation failure and is never retried automatically.
        }
        if (result == InstallResolution.VERIFIED || result == InstallResolution.LEGACY) cleanupCompletedDownloads(context)
        return result
    }

    /** Worker-only, retryable maintenance; a cleanup failure never changes an installation result. */
    private fun cleanupCompletedDownloads(context: Context) {
        runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            @Suppress("DEPRECATION")
            val code = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
            val allowed = synchronized(this) {
                InstallReconciliationRules.mayCleanDownloads(prefs.getString("owner", null), prefs.getString("result_owner", null),
                    prefs.getString("result_kind", null), prefs.getLong("result_code", 0), code)
            }
            if (allowed) GitHubUpdater.cleanupDownloads(context)
        }
    }

    @Synchronized internal fun resolvedOwner(context: Context, owner: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        @Suppress("DEPRECATION")
        val code = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        return owner.isNotBlank() && prefs.getString("result_owner", null) == owner &&
            InstallReconciliationRules.mayShowResolved(prefs.getString("owner", null), owner, prefs.getLong("result_code", 0), code)
    }

    @Synchronized fun consumeInstallSuccess(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("result_notified", true)) return false
        @Suppress("DEPRECATION")
        val code = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        if (!InstallReconciliationRules.mayShowResolved(prefs.getString("owner", null), prefs.getString("result_owner", null),
                prefs.getLong("result_code", 0), code)) return false
        return prefs.edit().putBoolean("result_notified", true).commit()
    }

    /** Warning only, after an actual package update. Never launches the chooser automatically. */
    @Synchronized fun consumeHomeWarning(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("result_home_changed", false)) return false
        return prefs.edit().putBoolean("result_home_changed", false).commit()
    }
}
