package br.com.lume.launcher

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
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
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID

/** App-only UI smoke runner; never changes device language, Home role or system settings. */
class LocaleSmokeInstrumentation : Instrumentation() {
    private var options = Bundle()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); options = arguments ?: Bundle(); start() }
    private fun mainRun(action: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun writeJournal(file: File, json: JSONObject) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write((json.toString(2) + "\n").toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        check(temporary.renameTo(file)) { "Could not commit preference journal: ${file.name}" }
    }
    private fun chooseLanguage(activity: LauncherActivity, tag: String): LauncherActivity {
        mainRun { LauncherActivity::class.java.getDeclaredMethod("showLanguage").apply { isAccessible = true }.invoke(activity) }
        waitForIdleSync()
        val monitor = addMonitor(LauncherActivity::class.java.name, null, false)
        try {
            mainRun {
                val dialog = LauncherActivity::class.java.getDeclaredField("openDialog").apply { isAccessible = true }.get(activity) as android.app.Dialog
                check(dialog.window!!.decorView.findViewWithTag<View>("language:$tag").requestFocus())
            }
            waitForIdleSync(); android.os.SystemClock.sleep(150)
            sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            val recreated = waitForMonitorWithTimeout(monitor, 10000) as? LauncherActivity
                ?: error("Language selection did not recreate the Activity: $tag")
            waitForIdleSync()
            check(AppLocales.selected(recreated) == tag)
            return recreated
        } finally { removeMonitor(monitor) }
    }

    override fun onStart() {
        val report = JSONArray()
        val tracked = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
        val resumed = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) { tracked.add(activity) }
            override fun onActivityStarted(activity: Activity) { tracked.add(activity) }
            override fun onActivityResumed(activity: Activity) { tracked.add(activity); resumed.add(activity) }
            override fun onActivityPaused(activity: Activity) { resumed.remove(activity) }
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) { tracked.remove(activity); resumed.remove(activity) }
        }
        val application = targetContext.applicationContext as Application
        var callbacksRegistered = false
        var output: File? = null
        var journal: File? = null
        var snapshot: JSONObject? = null
        var beforeDigest: String? = null
        var afterDigest: String? = null
        var activity: LauncherActivity? = null
        var failure: Throwable? = null
        var restorationFailure: Throwable? = null
        var parkingCount = 0
        var parkingPackage: String? = null
        fun finishTestActivities() {
            // Closing the default Home without another foreground task can recreate it immediately.
            val settings = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val component = requireNotNull(settings.resolveActivity(targetContext.packageManager)) { "Android Settings unavailable for safe cleanup" }
            check(component.packageName != targetContext.packageName) { "Parking must use external Android Settings" }
            mainRun { targetContext.startActivity(settings.setComponent(component)) }
            waitForIdleSync()
            val expires = SystemClock.uptimeMillis() + 5_000L
            var parked = false
            while (SystemClock.uptimeMillis() < expires) {
                val window = uiAutomation.rootInActiveWindow
                val foreground = try { window?.packageName?.toString() } finally { window?.recycle() }
                var appPaused = false
                mainRun { appPaused = resumed.isEmpty() }
                if (foreground == component.packageName && appPaused) { parked = true; break }
                SystemClock.sleep(50L)
            }
            check(parked) { "Settings foreground/pause not confirmed; refusing to finish Home" }
            parkingCount++; parkingPackage = component.packageName
            val destroyExpires = SystemClock.uptimeMillis() + 5_000L
            while (SystemClock.uptimeMillis() < destroyExpires) {
                mainRun { tracked.toList().filterNot { it.isDestroyed }.forEach { it.finish() } }
                waitForIdleSync()
                var destroyed = false
                mainRun { destroyed = tracked.isEmpty() }
                if (destroyed) return
                SystemClock.sleep(100L)
            }
            error("Test Activities survived cleanup")
        }
        fun recordFailure(error: Throwable) {
            val previous = failure
            if (previous == null) failure = error else if (previous !== error) previous.addSuppressed(error)
        }
        fun recordRestorationFailure(error: Throwable) {
            val previous = restorationFailure
            if (previous == null) restorationFailure = error else if (previous !== error) previous.addSuppressed(error)
        }
        try {
            val outputName = options.getString("output") ?: "locale-smoke"
            require(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}").matches(outputName)) { "Invalid output directory name" }
            val base = requireNotNull(targetContext.getExternalFilesDir(null)) { "External app files unavailable" }.canonicalFile
            val destination = File(base, outputName).canonicalFile
            require(destination.parentFile == base) { "Output must stay within app files" }
            check(destination.isDirectory || destination.mkdirs()) { "Cannot create output directory" }
            output = destination
            val stateDirectory = File(destination, "state-${System.currentTimeMillis()}-${UUID.randomUUID()}")
            check(stateDirectory.mkdir()) { "Cannot create preference journal directory" }
            journal = stateDirectory
            mainRun { snapshot = PreferenceStateSnapshot.read(targetContext) }
            val before = requireNotNull(snapshot)
            beforeDigest = PreferenceStateSnapshot.digest(before)
            writeJournal(File(stateDirectory, "prefs-before.json"), before)
            check(PreferenceStateSnapshot.digest(JSONObject(File(stateDirectory, "prefs-before.json").readText())) == beforeDigest) { "Preference journal readback failed" }
            writeJournal(File(stateDirectory, "state.json"), JSONObject().put("status", "pending")
                .put("before_digest", beforeDigest).put("entries", before.getJSONArray("entries").length()))
            mainRun { application.registerActivityLifecycleCallbacks(callbacks); callbacksRegistered = true }
            mainRun { AppLocales.set(targetContext, "system") }
            for ((requested, expected) in listOf(listOf("zz-ZZ") to "en-US", listOf("zh-Hant-TW", "pt-PT") to "pt-PT", listOf("hi-IN") to "hi-IN")) {
                val configuration = android.content.res.Configuration(targetContext.resources.configuration).apply {
                    setLocales(android.os.LocaleList(*requested.map(java.util.Locale::forLanguageTag).toTypedArray()))
                }
                val scoped = targetContext.createConfigurationContext(configuration)
                check(AppLocales.effectiveTag(AppLocales.wrap(scoped)) == expected) { "App-only automatic resolution failed: $requested" }
            }
            mainRun { AppLocales.set(targetContext, "en-US") }
            activity = startActivitySync(Intent(targetContext, LauncherActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as LauncherActivity
            val initial = requireNotNull(activity)
            val favorites = targetContext.getSharedPreferences("orbita", 0).getString("favorites", null)
            mainRun {
                LauncherActivity::class.java.getDeclaredMethod("showPage", String::class.java).apply { isAccessible = true }.invoke(initial, "apps")
            }
            activity = chooseLanguage(initial, "ar")
            check(AppLocales.effectiveTag(requireNotNull(activity)) == "ar")
            check(LauncherActivity::class.java.getDeclaredField("currentPage").apply { isAccessible = true }.get(activity) == "apps") { "Manual selection lost the current page" }
            check(targetContext.getSharedPreferences("orbita", 0).getString("favorites", null) == favorites) { "Language changed favorites" }
            finishTestActivities()
            val tags = options.getString("locales")?.split(',') ?: LocaleRules.supportedTags
            for (tag in tags) {
                require(tag in LocaleRules.supportedTags)
                mainRun { AppLocales.set(targetContext, "system") }
                waitForIdleSync()
                activity = startActivitySync(Intent(targetContext, LauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as LauncherActivity
                waitForIdleSync()
                activity = chooseLanguage(requireNotNull(activity), tag)
                val current = requireNotNull(activity)
                check(AppLocales.effectiveTag(current) == tag) { "Wrong effective locale: $tag" }
                check((current.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) == LocaleRules.isRtl(tag)) { "Wrong direction: $tag" }
                val compiledStrings = JSONObject()
                for (field in R.string::class.java.declaredFields.filter { it.name.startsWith("activity_") || it.name.startsWith("common_") }) {
                    val id = field.getInt(null)
                    val raw = current.getString(id)
                    compiledStrings.put(field.name, raw)
                    val arguments = Regex("%(\\d+)\\$([sd])").findAll(raw).toList()
                    if (arguments.isNotEmpty()) {
                        val values = Array<Any>(arguments.maxOf { it.groupValues[1].toInt() }) { "Sample" }
                        arguments.forEach { if (it.groupValues[2] == "d") values[it.groupValues[1].toInt()-1] = 7 }
                        current.getString(id, *values)
                    }
                }
                File(destination, "$tag-compiled-strings.json").writeText(compiledStrings.toString(2))
                fun focused(view: View) {
                    var matches = false
                    mainRun { matches = current.currentFocus === view }
                    check(matches) { "Wrong DPAD focus in $tag: expected ${view.tag}" }
                }
                val next = current.window.decorView.findViewWithTag<View>("content-tab:next")
                val suggestions = current.window.decorView.findViewWithTag<View>("content-tab:suggestions")
                val home = current.window.decorView.findViewWithTag<View>("navigation:home")
                val cinema = LauncherActivity::class.java.getDeclaredField("cinemaButton").apply { isAccessible = true }.get(current) as View
                mainRun { check(next.requestFocus()) }
                val towardEnd = if (LocaleRules.isRtl(tag)) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
                val towardStart = if (LocaleRules.isRtl(tag)) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                for ((key, expected) in listOf(towardStart to home, towardEnd to next, towardEnd to suggestions, towardEnd to cinema, towardStart to suggestions, towardStart to next)) {
                    sendKeyDownUpSync(key); waitForIdleSync(); focused(expected)
                }
                val availableScreens = listOf("home", "library", "gallery", "showSettings", "showCinema", "showAppearance", "showScreensaverSettings", "showScreensaverCredits", "showStore", "showLanguage", "appOptions", "showInputs", "showHomeChoice", "showAbout")
                val screens = options.getString("screens")?.split(',') ?: availableScreens
                require(screens.isNotEmpty() && screens.distinct().size == screens.size && screens.all { it in availableScreens })
                for (screen in screens) {
                    mainRun {
                        LauncherActivity::class.java.getDeclaredMethod("closeAppMenu", Boolean::class.javaPrimitiveType).apply { isAccessible = true }.invoke(current, false)
                        val close = LauncherActivity::class.java.getDeclaredMethod("closeDialog").apply { isAccessible = true }
                        close.invoke(current)
                        when (screen) {
                            "home", "library", "gallery" -> LauncherActivity::class.java.getDeclaredMethod("showPage", String::class.java).apply { isAccessible = true }.invoke(current, when(screen) { "library" -> "apps"; "gallery" -> "scenes"; else -> "home" })
                            "showScreensaverSettings" -> LauncherActivity::class.java.getDeclaredMethod(screen, Int::class.javaPrimitiveType).apply { isAccessible = true }.invoke(current, 0)
                            "showAppearance" -> LauncherActivity::class.java.getDeclaredMethod(screen, String::class.java).apply { isAccessible = true }.invoke(current, null)
                            "appOptions" -> {
                                LauncherActivity::class.java.getDeclaredMethod("showPage", String::class.java).apply { isAccessible = true }.invoke(current, "apps")
                                val installed = (LauncherActivity::class.java.getDeclaredField("apps").apply { isAccessible = true }.get(current) as List<*>).first() as InstalledApp
                                val anchor = current.window.decorView.findViewWithTag<View>("app:${installed.packageName}")
                                LauncherActivity::class.java.getDeclaredMethod("showAppOptions", InstalledApp::class.java, View::class.java).apply { isAccessible = true }.invoke(current, installed, anchor)
                            }
                            else -> LauncherActivity::class.java.getDeclaredMethod(screen).apply { isAccessible = true }.invoke(current)
                        }
                    }
                    waitForIdleSync()
                    android.os.SystemClock.sleep(250) // Let the new window reach SurfaceFlinger before capture.
                    val texts = JSONArray()
                    fun visit(view: View) {
                        if (view is TextView && view.visibility == View.VISIBLE && view.text.isNotEmpty()) {
                            val location = IntArray(2); view.getLocationOnScreen(location)
                            val layout = view.layout
                            var ellipsis = 0
                            if (layout != null) for (line in 0 until layout.lineCount) ellipsis += layout.getEllipsisCount(line)
                            val visible = android.graphics.Rect()
                            val inViewport = view.getGlobalVisibleRect(visible)
                            texts.put(JSONObject().put("text", view.text.toString()).put("x",location[0]).put("y",location[1]).put("width",view.width).put("height",view.height)
                                .put("shown",view.isShown).put("laidOut",layout != null).put("inViewport",inViewport).put("visibleWidth",visible.width()).put("visibleHeight",visible.height())
                                .put("layoutHeight",layout?.height).put("paddingTop",view.compoundPaddingTop).put("paddingBottom",view.compoundPaddingBottom)
                                .put("lineCount",layout?.lineCount).put("maxLines",view.maxLines)
                                .put("lastLineEnd",if (layout != null && layout.lineCount > 0) layout.getLineEnd(layout.lineCount-1) else 0)
                                .put("ellipsis",ellipsis).put("description",view.contentDescription?.toString()).put("focused",view.hasFocus()))
                        }
                        if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
                    }
                    mainRun {
                        val dialog = LauncherActivity::class.java.getDeclaredField("openDialog").apply { isAccessible = true }.get(current) as? android.app.Dialog
                        val popup = LauncherActivity::class.java.getDeclaredField("appMenu").apply { isAccessible = true }.get(current) as? android.widget.PopupWindow
                        if (screen == "appOptions") {
                            check(popup?.isShowing == true)
                            val location = IntArray(2); popup!!.contentView.getLocationOnScreen(location)
                            check(location[0] >= 0 && location[0]+popup.contentView.width <= current.resources.displayMetrics.widthPixels) { "App dropdown crosses horizontal screen bounds: $tag" }
                        }
                        visit(popup?.contentView ?: dialog?.window?.decorView ?: current.window.decorView)
                    }
                    check(texts.length() > 0) { "Empty UI: $tag/$screen" }
                    (if (options.getString("screenshots") == "false") null else uiAutomation.takeScreenshot())?.let { bitmap ->
                        try {
                            File(destination, "$tag-$screen.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        } finally { bitmap.recycle() }
                    }
                    report.put(JSONObject().put("locale",tag).put("screen",screen).put("texts",texts))
                }
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
                waitForIdleSync()
                val saver = startActivitySync(Intent(targetContext, ScreenSaverActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ScreenSaverActivity
                waitForIdleSync()
                check(AppLocales.effectiveTag(saver) == tag) { "Screensaver locale mismatch: $tag" }
                val saverReport = JSONObject().put("locale", tag)
                mainRun {
                    check(saver.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
                    val renderer = ScreenSaverActivity::class.java.getDeclaredField("renderer").apply { isAccessible = true }.get(saver) as ScreenSaverView
                    val caption = ScreenSaverView::class.java.getDeclaredField("caption").apply { isAccessible = true }.get(renderer) as TextView
                    check(caption.text.isNotBlank()) { "Missing localized screensaver caption: $tag" }
                    check(renderer.contentDescription.startsWith(saver.getString(R.string.common_screensaver)))
                    saverReport.put("caption", caption.text.toString()).put("description",renderer.contentDescription).put("secure",true)
                }
                File(destination, "$tag-screensaver.json").writeText(saverReport.toString(2))
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync()
                check(saver.isFinishing || saver.isDestroyed) { "Screensaver did not exit on Back: $tag" }
                finishTestActivities()
            }
        } catch (error: Throwable) {
            recordFailure(error)
        } finally {
            if (callbacksRegistered) {
                try {
                    finishTestActivities()
                } catch (error: Throwable) { recordRestorationFailure(error) }
            }
            snapshot?.let { before ->
                try {
                    mainRun {
                        PreferenceStateSnapshot.write(targetContext, before)
                        check(PreferenceStateSnapshot.matches(targetContext, before)) { "Full preference restoration mismatch" }
                    }
                } catch (error: Throwable) { recordRestorationFailure(error) }
                try {
                    var after: JSONObject? = null
                    mainRun { after = PreferenceStateSnapshot.read(targetContext) }
                    afterDigest = PreferenceStateSnapshot.digest(requireNotNull(after))
                    journal?.let { writeJournal(File(it, "prefs-after.json"), requireNotNull(after)) }
                    check(afterDigest == beforeDigest) { "Restored preference digest differs" }
                } catch (error: Throwable) { recordRestorationFailure(error) }
            }
            if (callbacksRegistered) {
                try { mainRun { application.unregisterActivityLifecycleCallbacks(callbacks); tracked.clear(); resumed.clear() } }
                catch (error: Throwable) { recordRestorationFailure(error) }
            }
            try {
                journal?.let {
                    writeJournal(File(it, "state.json"), JSONObject()
                        .put("status", if (snapshot != null && restorationFailure == null) "restored" else "failed")
                        .put("before_digest", beforeDigest).put("after_digest", afterDigest)
                        .put("parking_count", parkingCount).put("parking_package", parkingPackage)
                        .put("test_failed", failure != null)
                        .put("restoration_error", restorationFailure?.stackTraceToString()))
                }
            } catch (error: Throwable) { recordRestorationFailure(error) }
            restorationFailure?.let { recordFailure(it) }
            try { output?.let { File(it, "report.json").writeText(report.toString(2)) } }
            catch (error: Throwable) { recordFailure(error) }
        }
        val error = failure
        finish(if (error == null) android.app.Activity.RESULT_OK else android.app.Activity.RESULT_CANCELED,
            Bundle().apply {
                putString("preference_journal", journal?.absolutePath)
                putString("before_digest", beforeDigest); putString("after_digest", afterDigest)
                putInt("parking_count", parkingCount); putString("parking_package", parkingPackage)
                restorationFailure?.let { putString("restoration_error", it.stackTraceToString()) }
                if (error == null) putString("summary", "Automatic fallback, manual picker recreation/persistence, full preference restoration and ${report.length()} app-only locale screens recorded")
                else putString("error", error.stackTraceToString())
            })
    }
}
