package br.com.lume.launcher

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import br.com.lume.launcher.core.LocaleRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** App-only UI smoke runner; never changes device language, Home role or system settings. */
class LocaleSmokeInstrumentation : Instrumentation() {
    private var options = Bundle()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); options = arguments ?: Bundle(); start() }
    private fun mainRun(action: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
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
        val output = File(targetContext.getExternalFilesDir(null), "locale-smoke").apply { mkdirs() }
        val report = JSONArray()
        val original = AppLocales.selected(targetContext)
        var activity: LauncherActivity? = null
        var failure: Throwable? = null
        try {
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
            mainRun { activity?.finish() }
            waitForIdleSync()
            val tags = options.getString("locales")?.split(',') ?: LocaleRules.supportedTags
            for (tag in tags) {
                require(tag in LocaleRules.supportedTags)
                mainRun { activity?.finish(); AppLocales.set(targetContext, "system") }
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
                File(output, "$tag-compiled-strings.json").writeText(compiledStrings.toString(2))
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
                val availableScreens = listOf("home", "library", "gallery", "showSettings", "showCinema", "showAppearance", "showScreensaverSettings", "showScreensaverCredits", "showStore", "showLanguage", "appOptions", "showInputs", "showHomeChoice")
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
                    uiAutomation.takeScreenshot()?.let { bitmap ->
                        File(output, "$tag-$screen.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
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
                File(output, "$tag-screensaver.json").writeText(saverReport.toString(2))
                sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); waitForIdleSync()
                check(saver.isFinishing || saver.isDestroyed) { "Screensaver did not exit on Back: $tag" }
                mainRun { current.finish() }
            }
            File(output,"report.json").writeText(report.toString(2))
        } catch (error: Throwable) {
            File(output,"report.json").writeText(report.toString(2))
            failure = error
        } finally {
            mainRun { activity?.finish(); AppLocales.set(targetContext, original) }
        }
        val error = failure
        finish(if (error == null) android.app.Activity.RESULT_OK else android.app.Activity.RESULT_CANCELED,
            Bundle().apply { if (error == null) putString("summary", "Automatic fallback, manual picker recreation/persistence and ${report.length()} app-only locale screens recorded") else putString("error", error.stackTraceToString()) })
    }
}
