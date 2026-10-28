package br.com.lume.launcher

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import br.com.lume.launcher.core.LocaleRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One real no-update check; remaining states are explicitly synthetic UI/transport contracts. */
class UpdaterCheckInstrumentation : Instrumentation() {
    private var options = Bundle()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); options = arguments ?: Bundle(); start() }

    private fun mainRun(action: () -> Unit) {
        var error: Throwable? = null
        runOnMainSync { try { action() } catch (failure: Throwable) { error = failure } }
        error?.let { throw it }
    }
    private fun invoke(host: LauncherActivity, name: String) = LauncherActivity::class.java
        .getDeclaredMethod(name).apply { isAccessible = true }.invoke(host)
    private fun dialog(host: LauncherActivity) = LauncherActivity::class.java.getDeclaredField("openDialog")
        .apply { isAccessible = true }.get(host) as? Dialog
    private fun panel(host: LauncherActivity, text: String, busy: Boolean = false, action: String? = null) {
        val noOp: () -> Unit = {}
        LauncherActivity::class.java.getDeclaredMethod("updatePanel", String::class.java, Boolean::class.javaPrimitiveType,
            String::class.java, Function0::class.java).apply { isAccessible = true }.invoke(host, text, busy, action, if (action == null) null else noOp)
    }
    private fun writeJson(file: File, json: JSONObject) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { it.write((json.toString(2) + "\n").toByteArray(Charsets.UTF_8)); it.fd.sync() }
        check(temporary.renameTo(file)) { "Cannot commit updater test artifact" }
    }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun identity(info: PackageInfo) = UpdateApkIdentity(info.packageName, info.versionName.orEmpty(), info.longVersionCode,
        info.applicationInfo?.minSdkVersion ?: 0, info.signingInfo?.apkContentsSigners?.map {
            hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
        }?.toSet().orEmpty())

    private class LocalConnection(url: URL, private val length: Long, private val body: () -> InputStream) : HttpURLConnection(url) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getContentLengthLong() = length
        override fun getInputStream() = body()
    }
    private fun releaseJson(version: String, draft: Boolean = false, prerelease: Boolean = false): JSONObject {
        val tag = "v$version"
        val path = "https://github.com/JohnSalazar/lume-orbita/releases/download/$tag/"
        return JSONObject().put("tag_name", tag).put("draft", draft).put("prerelease", prerelease)
            .put("html_url", "https://github.com/JohnSalazar/lume-orbita/releases/tag/$tag")
            .put("assets", JSONArray().put(JSONObject().put("name", "lume-orbita-$version.apk").put("size", 100)
                .put("state", "uploaded").put("browser_download_url", path + "lume-orbita-$version.apk"))
                .put(JSONObject().put("name", "SHA256SUMS.txt").put("size", 100).put("state", "uploaded")
                    .put("browser_download_url", path + "SHA256SUMS.txt")))
    }

    /** Android JSON and PackageManager contracts; every connection below is a local fake. */
    private fun nativeContracts(directory: File, cases: JSONArray) {
        val pm = targetContext.packageManager
        @Suppress("DEPRECATION")
        val current = identity(pm.getPackageInfo(targetContext.packageName, PackageManager.GET_SIGNING_CERTIFICATES))
        check(current.version == "0.4.3" && current.code == 29L) { "No-update fixture requires installed0.4.3/code29" }
        val client = GitHubUpdater(directory, { current }, { file ->
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)?.let(::identity)
        }, Build.VERSION.SDK_INT, UpdateHttpTransport({ error("Metadata parsing must not connect") }))
        fun passed(name: String) { cases.put(JSONObject().put("case", name).put("passed", true)) }
        check(client.parseReleases(JSONArray().put(releaseJson("0.4.2")).put(releaseJson("0.4.3")).toString(), current.version) == null)
        passed("older_and_current_have_no_update")
        val selected = client.parseReleases(JSONArray().put(releaseJson("0.4.4")).put(releaseJson("0.4.6", draft = true))
            .put(releaseJson("0.4.5", prerelease = true)).toString(), current.version)
        checkNotNull(selected)
        check(selected.version == "0.4.5" && selected.prerelease)
        passed("newest_non_draft_includes_evaluation_release")
        fun rejected(name: String, text: String) {
            val failure = runCatching { client.parseReleases(text, current.version) }.exceptionOrNull()
            check(failure is UpdateFailure && failure.reason == UpdateFailureReason.INVALID_RELEASE) { "Metadata accepted: $name" }
            passed(name)
        }
        rejected("malformed_json", "<html>not JSON</html>")
        rejected("missing_assets", JSONArray().put(releaseJson("0.4.4").put("assets", JSONArray())).toString())
        val duplicate = releaseJson("0.4.4").apply { getJSONArray("assets").put(getJSONArray("assets").getJSONObject(0)) }
        rejected("duplicate_asset", JSONArray().put(duplicate).toString())
        val foreign = releaseJson("0.4.4").apply { getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://evil.test/a.apk") }
        rejected("foreign_asset", JSONArray().put(foreign).toString())
        rejected("too_many_releases", JSONArray().apply { repeat(31) { put(releaseJson("0.4.2")) } }.toString())

        // A future tag around the installed APK must not make its actual version/code newer.
        check(!directory.exists() && directory.mkdir())
        val apk = File(targetContext.applicationInfo.sourceDir)
        val digest = MessageDigest.getInstance("SHA-256")
        apk.inputStream().use { input -> val buffer = ByteArray(16 * 1024); while (true) {
            val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read)
        } }
        val sums = "${hex(digest.digest())}  lume-orbita-0.4.4.apk\n".toByteArray(Charsets.UTF_8)
        val fake = UpdateRelease("0.4.4", "v0.4.4", "https://github.com/JohnSalazar/lume-orbita/releases/tag/v0.4.4",
            "https://github.com/JohnSalazar/lume-orbita/releases/download/v0.4.4/lume-orbita-0.4.4.apk",
            "https://github.com/JohnSalazar/lume-orbita/releases/download/v0.4.4/SHA256SUMS.txt", apk.length(), true)
        val transport = UpdateHttpTransport({ url -> when (url.toString()) {
            fake.checksumsUrl -> LocalConnection(url, sums.size.toLong()) { ByteArrayInputStream(sums) }
            fake.apkUrl -> LocalConnection(url, apk.length()) { apk.inputStream() }
            else -> error("Unexpected fake transport URL")
        } }, { listOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))) })
        val archiveClient = GitHubUpdater(directory, { current }, { file ->
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)?.let(::identity)
        }, Build.VERSION.SDK_INT, transport)
        try {
            val failure = runCatching { archiveClient.download(fake) }.exceptionOrNull()
            check(failure is UpdateFailure && failure.reason == UpdateFailureReason.INVALID_APK)
            check(directory.listFiles().orEmpty().isEmpty()) { "Rejected archive left a partial" }
            passed("real_installed_archive_rejected_despite_future_metadata")
        } finally {
            archiveClient.cancel()
            directory.listFiles().orEmpty().forEach { if (it.canonicalFile.parentFile == directory.canonicalFile) it.delete() }
            directory.delete()
        }
    }

    private fun inspectPanel(host: LauncherActivity): JSONObject {
        val problems = JSONArray()
        var visibleCount = 0
        val window = checkNotNull(dialog(host)?.window)
        fun visit(view: View) {
            if (view is TextView && view.isShown && view.text.isNotBlank() && view.layout != null) {
                val rect = Rect(); val layout = checkNotNull(view.layout)
                if (view.getGlobalVisibleRect(rect) && view.width > 0 && view.height > 0) {
                    visibleCount++
                    val ellipsis = (0 until layout.lineCount).sumOf { layout.getEllipsisCount(it) }
                    if (ellipsis > 0 || layout.height + view.compoundPaddingTop + view.compoundPaddingBottom > view.height)
                        problems.put(JSONObject().put("text", view.text.toString()).put("ellipsis", ellipsis)
                            .put("height", view.height).put("layout_height", layout.height))
                }
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(window.decorView)
        check(visibleCount > 0)
        return JSONObject().put("visible_texts", visibleCount).put("problems", problems)
    }

    /** Exercise real controller closures, but use only latch jobs and a fake HTTP stream. */
    private fun controllerContracts(host: LauncherActivity, report: JSONObject) {
        val worker = LauncherActivity::class.java.getDeclaredField("updateWorker").apply { isAccessible = true }.get(null) as ThreadPoolExecutor
        val idleDeadline = SystemClock.uptimeMillis() + 3_000
        while ((worker.activeCount != 0 || worker.queue.isNotEmpty()) && SystemClock.uptimeMillis() < idleDeadline) SystemClock.sleep(10)
        check(worker.maximumPoolSize == 1 && worker.queue.isEmpty() && worker.activeCount == 0)
        check(worker.queue.remainingCapacity() == 1)
        val unblockWorker = CountDownLatch(1)
        val workerEntered = CountDownLatch(1)
        val readEntered = CountDownLatch(1)
        val unblockRead = CountDownLatch(1)
        var blocker: Future<*>? = null
        var barrier: Future<*>? = null
        val jobField = LauncherActivity::class.java.getDeclaredField("updateJob").apply { isAccessible = true }
        try {
            blocker = worker.submit { workerEntered.countDown(); check(unblockWorker.await(10, TimeUnit.SECONDS)) }
            check(workerEntered.await(3, TimeUnit.SECONDS))
            val queued = worker.submit { error("Canceled queued fixture must not run") }
            mainRun {
                jobField.set(host, queued)
                // Queue is full: this must show the bounded-controller error, never another worker.
                LauncherActivity::class.java.getDeclaredMethod("submitUpdate", Function0::class.java)
                    .apply { isAccessible = true }.invoke(host, { error("Rejected fixture must not run") })
                check(queued.isCancelled && worker.queue.isEmpty())
                check(dialog(host)!!.window!!.decorView.findViewWithTag<TextView>("update:status").text.toString() == host.getString(R.string.activity_update_error))
                invoke(host, "closeDialog")
                invoke(host, "checkForUpdate")
                val client = LauncherActivity::class.java.getDeclaredField("updateClient").apply { isAccessible = true }.get(host) as GitHubUpdater
                val transport = UpdateHttpTransport({ url -> LocalConnection(url, 2) {
                    object : ByteArrayInputStream("[]".toByteArray()) {
                        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                            readEntered.countDown()
                            val expires = SystemClock.uptimeMillis() + 5_000
                            while (unblockRead.count > 0 && SystemClock.uptimeMillis() < expires) {
                                try { unblockRead.await(50, TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { /* model blocked I/O */ }
                            }
                            check(unblockRead.count == 0L) { "Fake read was not released" }
                            return super.read(buffer, offset, length)
                        }
                    }
                } }, { listOf(InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))) })
                // Before the queued check runs, replace only its app-owned transport seam.
                GitHubUpdater::class.java.getDeclaredField("transport").apply { isAccessible = true }.set(client, transport)
            }
            unblockWorker.countDown()
            check(readEntered.await(3, TimeUnit.SECONDS))
            var deadlineDialog: Dialog? = null
            mainRun {
                val generation = LauncherActivity::class.java.getDeclaredField("updateGeneration").apply { isAccessible = true }
                val before = generation.getInt(host)
                val deadline = LauncherActivity::class.java.getDeclaredField("updateDeadline").apply { isAccessible = true }.get(host) as Runnable
                deadline.run() // Logical watchdog test: no claim of60seconds elapsed.
                check(generation.getInt(host) > before)
                check(LauncherActivity::class.java.getDeclaredField("updateClient").apply { isAccessible = true }.get(host) == null)
                deadlineDialog = dialog(host)
                check(deadlineDialog!!.window!!.decorView.findViewWithTag<TextView>("update:status").text.toString() == host.getString(R.string.activity_update_error))
            }
            val drained = CountDownLatch(1)
            barrier = worker.submit { drained.countDown() }
            unblockRead.countDown()
            check(drained.await(3, TimeUnit.SECONDS))
            waitForIdleSync()
            mainRun {
                check(dialog(host) === deadlineDialog) { "Stale callback replaced the watchdog result" }
                invoke(host, "closeDialog")
            }
            report.put("controller_fixture", JSONObject().put("maximum_workers", 1).put("queue_capacity", 1)
                .put("rejection_and_purge", true).put("logical_watchdog", true).put("late_callback_ignored", true)
                .put("method", "latch jobs and fake HTTP; watchdog Runnable invoked directly"))
        } finally {
            // Cancel a queued production closure before releasing the worker, even if injection failed.
            try { mainRun { invoke(host, "cancelUpdate") } }
            finally {
                unblockRead.countDown(); unblockWorker.countDown()
                barrier?.cancel(true); blocker?.cancel(true); worker.purge()
            }
        }
    }

    override fun onStart() {
        val report = JSONObject().put("native_contracts", JSONArray()).put("ui_fixtures", JSONArray()).put("compiled_update_strings", JSONObject())
        val tracked = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
        val resumed = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
        var installActivitySeen = false
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) { tracked.add(activity); if (activity is UpdateInstallActivity) installActivitySeen = true }
            override fun onActivityStarted(activity: Activity) { tracked.add(activity) }
            override fun onActivityResumed(activity: Activity) { tracked.add(activity); resumed.add(activity) }
            override fun onActivityPaused(activity: Activity) { resumed.remove(activity) }
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) { tracked.remove(activity); resumed.remove(activity) }
        }
        val app = targetContext.applicationContext as Application
        var registered = false
        var snapshot: JSONObject? = null
        var directory: File? = null
        var failure: Throwable? = null
        val updaterPrefs = targetContext.getSharedPreferences(UpdateInstaller.PREFS, 0)
        val updaterBefore = updaterPrefs.all.toMap()
        val canInstallBefore = UpdateInstaller.canInstall(targetContext)
        val sessionsBefore = targetContext.packageManager.packageInstaller.mySessions.map { it.sessionId }.toSet()
        val homeBefore = UpdateInstaller.currentHome(targetContext)
        fun failed(error: Throwable) { if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error) }
        fun parkAndFinish() {
            val settings = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val component = requireNotNull(settings.resolveActivity(targetContext.packageManager))
            check(component.packageName != targetContext.packageName)
            mainRun { targetContext.startActivity(settings.setComponent(component)) }
            waitForIdleSync()
            val expires = SystemClock.uptimeMillis() + 5_000
            var parked = false
            while (SystemClock.uptimeMillis() < expires) {
                val root = uiAutomation.rootInActiveWindow
                val foreground = try { root?.packageName?.toString() } finally { root?.recycle() }
                var paused = false; mainRun { paused = resumed.isEmpty() }
                if (foreground == component.packageName && paused) { parked = true; break }
                SystemClock.sleep(100)
            }
            check(parked) { "Settings parking not confirmed" }
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (SystemClock.uptimeMillis() < deadline) {
                mainRun { tracked.toList().filterNot { it.isFinishing || it.isDestroyed }.forEach { it.finish() } }
                waitForIdleSync(); var empty = false; mainRun { empty = tracked.isEmpty() }
                if (empty) return
                SystemClock.sleep(100)
            }
            error("Updater test Activities survived cleanup")
        }
        try {
            check(updaterBefore.isEmpty()) { "Pending installer state exists; do not consume it in a smoke test" }
            check(sessionsBefore.isEmpty()) { "An installer session already exists; do not test concurrently" }
            val name = options.getString("output") ?: "updater-${System.currentTimeMillis()}"
            require(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}").matches(name))
            val base = requireNotNull(targetContext.getExternalFilesDir(null)).canonicalFile
            val destination = File(base, name).canonicalFile
            require(destination.parentFile == base)
            check(!destination.exists() && destination.mkdirs())
            directory = destination
            mainRun { snapshot = PreferenceStateSnapshot.read(targetContext) }
            val before = requireNotNull(snapshot)
            writeJson(File(destination, "prefs-before.json"), before)
            val beforeDigest = PreferenceStateSnapshot.digest(before)
            check(PreferenceStateSnapshot.digest(JSONObject(File(destination, "prefs-before.json").readText())) == beforeDigest)
            report.put("before_digest", beforeDigest).put("status", "pending").put("can_install_before", canInstallBefore)
            writeJson(File(destination, "updater.json"), report)
            mainRun { app.registerActivityLifecycleCallbacks(callbacks); registered = true }
            nativeContracts(File(targetContext.cacheDir, "qa-updater-${System.currentTimeMillis()}"), report.getJSONArray("native_contracts"))

            // This is the only live API check. No installer or permission-setting action is clicked.
            val live = startActivitySync(Intent(targetContext, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as LauncherActivity
            waitForIdleSync()
            mainRun {
                invoke(live, "showAbout")
                check(dialog(live)!!.window!!.decorView.findViewWithTag<View>("update:check").requestFocus())
            }
            waitForIdleSync(); sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); waitForIdleSync()
            val deadline = SystemClock.uptimeMillis() + 75_000
            var status = ""
            while (SystemClock.uptimeMillis() < deadline) {
                var busy = false
                mainRun {
                    status = dialog(live)?.window?.decorView?.findViewWithTag<TextView>("update:status")?.text?.toString().orEmpty()
                    busy = status.isEmpty() || status == live.getString(R.string.activity_update_checking)
                }
                if (!busy) break
                SystemClock.sleep(100)
            }
            report.put("live_check", JSONObject().put("source", "public GitHub releases API").put("result_text", status))
            check(status == live.getString(R.string.activity_update_current)) { "Live repository was not no-update; inspect state instead of installing" }
            mainRun { invoke(live, "cancelUpdate"); invoke(live, "closeDialog") }
            controllerContracts(live, report)
            parkAndFinish()

            val tags = options.getString("locales")?.split(',') ?: LocaleRules.supportedTags
            require(tags.isNotEmpty() && tags.distinct().size == tags.size && tags.all { it in LocaleRules.supportedTags })
            for (tag in tags) {
                mainRun { AppLocales.set(targetContext, tag) }
                val host = startActivitySync(Intent(targetContext, LauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as LauncherActivity
                waitForIdleSync(); check(AppLocales.effectiveTag(host) == tag)
                val compiled = JSONObject()
                R.string::class.java.declaredFields.filter { it.name.startsWith("activity_update_") }.forEach { compiled.put(it.name, host.getString(it.getInt(null))) }
                report.getJSONObject("compiled_update_strings").put(tag, compiled)
                val states = JSONObject()
                val fixtureReport = JSONObject().put("locale", tag).put("states", states).put("back_closed", false)
                report.getJSONArray("ui_fixtures").put(fixtureReport)
                fun state(name: String, render: () -> Unit) {
                    mainRun { render() }; waitForIdleSync(); SystemClock.sleep(100)
                    var result: JSONObject? = null
                    mainRun { result = inspectPanel(host) }
                    states.put(name, result)
                    check(requireNotNull(result).getJSONArray("problems").length() == 0) { "Updater fixture text clipped: $tag/$name" }
                }
                state("checking") { panel(host, host.getString(R.string.activity_update_checking), true) }
                state("current") { panel(host, host.getString(R.string.activity_update_current), action = host.getString(R.string.activity_update_check)) }
                state("available_evaluation") {
                    val fixture = UpdateRelease("0.4.4", "v0.4.4", "https://github.com/JohnSalazar/lume-orbita/releases/tag/v0.4.4",
                        "https://github.com/JohnSalazar/lume-orbita/releases/download/v0.4.4/lume-orbita-0.4.4.apk",
                        "https://github.com/JohnSalazar/lume-orbita/releases/download/v0.4.4/SHA256SUMS.txt", 100, true)
                    LauncherActivity::class.java.getDeclaredMethod("showAvailableUpdate", UpdateRelease::class.java).apply { isAccessible = true }.invoke(host, fixture)
                }
                state("progress") { panel(host, host.getString(R.string.activity_update_progress, 50), true) }
                state("verifying") { panel(host, host.getString(R.string.activity_update_verify), true) }
                state("source_required") { panel(host, host.getString(R.string.activity_update_source_required), action = host.getString(R.string.activity_update_settings)) }
                for (reason in listOf(UpdateFailureReason.NETWORK, UpdateFailureReason.RATE_LIMIT, UpdateFailureReason.INVALID_APK, UpdateFailureReason.STORAGE)) {
                    state("error_${reason.name}") {
                        LauncherActivity::class.java.getDeclaredMethod("updateError", Throwable::class.java).apply { isAccessible = true }
                            .invoke(host, UpdateFailure(reason))
                    }
                }
                state("installing_label_only") { panel(host, host.getString(R.string.activity_update_installing), true) }
                state("install_canceled_label_only") { panel(host, host.getString(R.string.activity_update_install_canceled)) }
                state("install_success_label_only") { panel(host, host.getString(R.string.activity_update_install_success)) }
                state("home_warning_label_only") { panel(host, host.getString(R.string.activity_update_home_changed), action = host.getString(R.string.activity_update_home_review)) }
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync()
                mainRun {
                    check(dialog(host) == null)
                    check(LauncherActivity::class.java.getDeclaredField("updateClient").apply { isAccessible = true }.get(host) == null)
                    check(host.currentFocus != null && host.currentFocus!!.isShown)
                }
                fixtureReport.put("back_closed", true)
                parkAndFinish()
            }
            check(!installActivitySeen) { "Installer Activity must never be launched by this test" }
            report.put("checks_passed", true)
        } catch (error: Throwable) { failed(error) }
        finally {
            if (registered) try { parkAndFinish() } catch (error: Throwable) { failed(error); report.put("cleanup_error", error.stackTraceToString()) }
            snapshot?.let { before ->
                try {
                    var after: JSONObject? = null
                    mainRun { PreferenceStateSnapshot.write(targetContext, before); check(PreferenceStateSnapshot.matches(targetContext, before)); after = PreferenceStateSnapshot.read(targetContext) }
                    directory?.let { writeJson(File(it, "prefs-after.json"), requireNotNull(after)) }
                    report.put("after_digest", PreferenceStateSnapshot.digest(requireNotNull(after))).put("restored", true)
                } catch (error: Throwable) { failed(error); report.put("restoration_error", error.stackTraceToString()) }
            }
            try {
                check(updaterPrefs.all == updaterBefore) { "Updater marker state changed" }
                check(UpdateInstaller.canInstall(targetContext) == canInstallBefore) { "Install access changed" }
                check(targetContext.packageManager.packageInstaller.mySessions.map { it.sessionId }.toSet() == sessionsBefore) { "Installer session was created" }
                check(!installActivitySeen && UpdateInstaller.currentHome(targetContext) == homeBefore)
                report.put("updater_state_unchanged", true).put("install_access_unchanged", true).put("installer_sessions_unchanged", true).put("home_unchanged", true)
            } catch (error: Throwable) { failed(error) }
            if (registered) try { mainRun { app.unregisterActivityLifecycleCallbacks(callbacks); tracked.clear(); resumed.clear() } } catch (error: Throwable) { failed(error) }
            report.put("status", if (failure == null) "passed" else "failed")
            failure?.let { report.put("error", it.stackTraceToString()) }
            try { directory?.let { writeJson(File(it, "updater.json"), report) } } catch (error: Throwable) { failed(error) }
        }
        finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("result", if (failure == null) "updater:ok" else "updater:error")
            putString("output", directory?.absolutePath); failure?.let { putString("error", it.stackTraceToString()) }
        })
    }
}
