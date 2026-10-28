package br.com.lume.launcher

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.AssetManager
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.widget.TextView
import br.com.lume.launcher.core.LocaleRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Collections
import java.util.Date
import java.util.IdentityHashMap
import java.util.Locale
import java.util.TimeZone

/** App-only clock regression. No screenshots, system clock changes or photo downloads. */
class ClockLocaleInstrumentation : Instrumentation() {
    private var options = Bundle()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); options = arguments ?: Bundle(); start() }

    private fun mainRun(action: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private fun writeJson(file: File, value: JSONObject) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use {
            it.write((value.toString(2) + "\n").toByteArray(Charsets.UTF_8)); it.fd.sync()
        }
        check(temporary.renameTo(file)) { "Cannot commit clock test artifact: ${file.name}" }
    }

    // Compare spacing/isolate-neutral semantics and exact native digit shapes separately.
    // Keep both raw strings in the report for diagnosis.
    private fun normalized(value: String): String = value
        .replace(Regex("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]"), "")
        .map { char -> val digit = Character.digit(char, 10); if (digit >= 0) digit.toString() else char.toString() }
        .joinToString("").replace(Regex("[\\s\\p{Zs}]+"), " ").trim()

    private fun clock(view: Any): TextView = view.javaClass.getDeclaredField("clock")
        .apply { isAccessible = true }.get(view) as TextView

    private fun measure(view: TextView): JSONObject {
        val layout = checkNotNull(view.layout) { "Clock has no layout" }
        val visible = Rect()
        val intersects = view.getGlobalVisibleRect(visible)
        val usableWidth = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
        val ellipsis = (0 until layout.lineCount).sumOf { layout.getEllipsisCount(it) }
        val textTooWide = (0 until layout.lineCount).any { layout.getLineWidth(it) > usableWidth + 1f }
        val clipped = !view.isShown || !intersects || visible.width() < view.width || visible.height() < view.height ||
            layout.height + view.compoundPaddingTop + view.compoundPaddingBottom > view.height ||
            ellipsis > 0 || textTooWide || layout.lineCount != 1 || layout.getLineEnd(layout.lineCount - 1) < view.text.length
        return JSONObject().put("width", view.width).put("height", view.height)
            .put("visible_width", visible.width()).put("visible_height", visible.height())
            .put("layout_height", layout.height).put("usable_width", usableWidth)
            .put("line_count", layout.lineCount).put("ellipsis", ellipsis).put("clipped", clipped)
    }

    override fun onStart() {
        val report = JSONObject().put("helper_cases", JSONArray()).put("ui", JSONArray())
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
        var registered = false
        var directory: File? = null
        var snapshot: JSONObject? = null
        var renderer: ScreenSaverView? = null
        var dialog: Dialog? = null
        var failure: Throwable? = null
        fun failed(error: Throwable) {
            if (failure == null) failure = error else if (failure !== error) failure!!.addSuppressed(error)
        }
        fun parkAndFinish() {
            mainRun { renderer?.stop(); renderer = null; dialog?.dismiss(); dialog = null }
            val settings = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val component = requireNotNull(settings.resolveActivity(targetContext.packageManager)) { "No safe Settings parking target" }
            check(component.packageName != targetContext.packageName)
            mainRun { targetContext.startActivity(settings.setComponent(component)) }
            waitForIdleSync()
            val parkDeadline = SystemClock.uptimeMillis() + 5_000L
            var parked = false
            while (SystemClock.uptimeMillis() < parkDeadline) {
                val root = uiAutomation.rootInActiveWindow
                val foreground = try { root?.packageName?.toString() } finally { root?.recycle() }
                var paused = false
                mainRun { paused = resumed.isEmpty() }
                if (foreground == component.packageName && paused) { parked = true; break }
                SystemClock.sleep(100)
            }
            check(parked) { "Settings foreground not confirmed; refusing to finish default Home" }
            val destroyDeadline = SystemClock.uptimeMillis() + 5_000L
            while (SystemClock.uptimeMillis() < destroyDeadline) {
                mainRun { tracked.toList().filterNot { it.isDestroyed || it.isFinishing }.forEach { it.finish() } }
                waitForIdleSync()
                var empty = false
                mainRun { empty = tracked.isEmpty() }
                if (empty) return
                SystemClock.sleep(100)
            }
            error("Clock test Activities survived cleanup")
        }
        try {
            val name = options.getString("output") ?: "clock-${System.currentTimeMillis()}"
            require(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}").matches(name))
            val base = requireNotNull(targetContext.getExternalFilesDir(null)).canonicalFile
            val destination = File(base, name).canonicalFile
            require(destination.parentFile == base)
            check(!destination.exists() && destination.mkdirs()) { "Clock output directory must be new" }
            directory = destination
            mainRun { snapshot = PreferenceStateSnapshot.read(targetContext) }
            val before = requireNotNull(snapshot)
            writeJson(File(destination, "prefs-before.json"), before)
            val beforeDigest = PreferenceStateSnapshot.digest(before)
            check(PreferenceStateSnapshot.digest(JSONObject(File(destination, "prefs-before.json").readText())) == beforeDigest)
            report.put("before_digest", beforeDigest).put("status", "pending")
            writeJson(File(destination, "clock.json"), report)
            mainRun { application.registerActivityLifecycleCallbacks(callbacks); registered = true }

            val times = listOf(LocalTime.MIDNIGHT, LocalTime.NOON, LocalTime.of(17, 5))
            for (tag in LocaleRules.supportedTags) {
                val nativeLocale = Locale.forLanguageTag(if (tag == "pnb-Arab-PK") "pa-Arab-PK" else tag)
                val nativeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, nativeLocale).apply { timeZone = TimeZone.getTimeZone("UTC") }
                for ((index, time) in times.withIndex()) {
                    val actual = LocaleRules.formatTime(time, tag)
                    val expected = nativeFormat.format(Date.from(time.atDate(LocalDate.of(2024, 1, 15)).toInstant(ZoneOffset.UTC)))
                    val digitsMatch = actual.filter { Character.isDigit(it) } == expected.filter { Character.isDigit(it) }
                    report.getJSONArray("helper_cases").put(JSONObject().put("locale", tag).put("time", time.toString())
                        .put("actual", actual).put("native_short", expected).put("digit_shapes_match", digitsMatch))
                    check(normalized(actual) == normalized(expected)) { "Native SHORT mismatch: $tag at $time" }
                    check(digitsMatch) { "Native clock digit shapes mismatch: $tag at $time" }
                    if (tag in listOf("pt-BR", "en-GB", "en-US")) {
                        val golden = if (tag == "en-US") listOf("12:00 AM", "12:00 PM", "5:05 PM") else listOf("00:00", "12:00", "17:05")
                        check(normalized(actual) == golden[index]) { "12/24-hour regression: $tag at $time" }
                    }
                }
            }

            for (tag in listOf("pt-BR", "en-US", "en-GB")) {
                mainRun { AppLocales.set(targetContext, tag) }
                val activity = startActivitySync(Intent(targetContext, LauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as LauncherActivity
                waitForIdleSync()
                check(AppLocales.effectiveTag(activity) == tag)
                val row = JSONObject().put("locale", tag)
                mainRun {
                    val beforeTime = LocalTime.now()
                    LauncherActivity::class.java.getDeclaredMethod("refreshAmbient").apply { isAccessible = true }.invoke(activity)
                    val observed = clock(activity).text.toString()
                    val afterTime = LocalTime.now()
                    row.put("home", observed).put("home_expected_before", LocaleRules.formatTime(beforeTime, tag))
                        .put("home_expected_after", LocaleRules.formatTime(afterTime, tag))
                    check(observed in setOf(LocaleRules.formatTime(beforeTime, tag), LocaleRules.formatTime(afterTime, tag))) { "Home clock differs from shared formatter: $tag" }
                    val wrapped = object : ContextWrapper(AppLocales.wrap(activity)) {
                        override fun getAssets(): AssetManager = this@ClockLocaleInstrumentation.context.assets
                    }
                    check(wrapped.assets.open("screensaver.json").bufferedReader().use { it.readText().trim() } == "[]")
                    val view = ScreenSaverView(wrapped)
                    renderer = view
                    dialog = Dialog(activity).apply {
                        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        setContentView(view); show(); window?.setLayout(-1, -1)
                    }
                    val saverBefore = LocalTime.now()
                    view.start(30_000L, 25, true)
                    val saverObserved = clock(view).text.toString()
                    val saverAfter = LocalTime.now()
                    row.put("saver", saverObserved).put("saver_expected_before", LocaleRules.formatTime(saverBefore, tag))
                        .put("saver_expected_after", LocaleRules.formatTime(saverAfter, tag))
                    check(saverObserved in setOf(LocaleRules.formatTime(saverBefore, tag), LocaleRules.formatTime(saverAfter, tag))) { "Saver clock differs from shared formatter: $tag" }
                    check(dialog!!.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                }
                waitForIdleSync(); SystemClock.sleep(250)
                mainRun {
                    row.put("saver_geometry", measure(clock(requireNotNull(renderer))))
                    renderer?.stop(); renderer = null; dialog?.dismiss(); dialog = null
                }
                waitForIdleSync(); SystemClock.sleep(250)
                mainRun { row.put("home_geometry", measure(clock(activity))) }
                report.getJSONArray("ui").put(row)
                check(!row.getJSONObject("home_geometry").getBoolean("clipped") && !row.getJSONObject("saver_geometry").getBoolean("clipped")) { "Clock clipped: $tag" }
                parkAndFinish()
            }
            report.put("checks_passed", true)
        } catch (error: Throwable) {
            failed(error)
        } finally {
            if (registered) {
                try { parkAndFinish() } catch (error: Throwable) { failed(error); report.put("cleanup_error", error.stackTraceToString()) }
            }
            // A failed Settings transition must not leave the test renderer/timers running.
            try { mainRun { renderer?.stop(); renderer = null; dialog?.dismiss(); dialog = null } }
            catch (error: Throwable) { failed(error); report.put("renderer_cleanup_error", error.stackTraceToString()) }
            snapshot?.let { before ->
                try {
                    var after: JSONObject? = null
                    mainRun {
                        PreferenceStateSnapshot.write(targetContext, before)
                        check(PreferenceStateSnapshot.matches(targetContext, before))
                        after = PreferenceStateSnapshot.read(targetContext)
                    }
                    directory?.let { writeJson(File(it, "prefs-after.json"), requireNotNull(after)) }
                    report.put("after_digest", PreferenceStateSnapshot.digest(requireNotNull(after))).put("restored", true)
                } catch (error: Throwable) { failed(error); report.put("restoration_error", error.stackTraceToString()) }
            }
            if (registered) {
                try { mainRun { application.unregisterActivityLifecycleCallbacks(callbacks); tracked.clear(); resumed.clear() } }
                catch (error: Throwable) { failed(error) }
            }
            report.put("status", if (failure == null) "passed" else "failed")
            failure?.let { report.put("error", it.stackTraceToString()) }
            try { directory?.let { writeJson(File(it, "clock.json"), report) } } catch (error: Throwable) { failed(error) }
        }
        finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("result", if (failure == null) "clock:ok" else "clock:error")
            putString("output", directory?.absolutePath)
            failure?.let { putString("error", it.stackTraceToString()) }
        })
    }
}
