package br.com.lume.launcher

import android.app.Activity
import android.app.Dialog
import android.app.Instrumentation
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.AssetManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.widget.TextView
import br.com.lume.launcher.core.ProceduralSceneRules
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Tests real renderer timers. Offline isolation changes only this instance's metadata assets. */
class ScreensaverPlaybackInstrumentation : Instrumentation() {
    private var options = Bundle()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); options = arguments ?: Bundle(); start() }
    private fun mainRun(action: () -> Unit) {
        var failure: Throwable? = null
        runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun field(view: ScreenSaverView, name: String): Any? = ScreenSaverView::class.java
        .getDeclaredField(name).apply { isAccessible = true }.get(view)

    override fun onStart() {
        var host: Activity? = null
        var dialog: Dialog? = null
        var renderer: ScreenSaverView? = null
        var snapshot: JSONObject? = null
        var directory: File? = null
        val report = JSONObject()
        var failure: Throwable? = null
        try {
            val mode = options.getString("mode") ?: "online"
            require(mode in listOf("online", "offline"))
            val name = options.getString("output") ?: "playback-${System.currentTimeMillis()}"
            require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,63}")))
            directory = File(requireNotNull(targetContext.getExternalFilesDir(null)), name)
            check(!directory.exists() && directory.mkdirs())
            snapshot = PreferenceStateSnapshot.read(targetContext)
            File(directory, "prefs-before.json").writeText(snapshot.toString(2))
            report.put("mode", mode).put("before_digest", PreferenceStateSnapshot.digest(snapshot))
                .put("offline_method", if (mode == "offline") "empty instance catalog; device network unchanged" else JSONObject.NULL)
            val activity = startActivitySync(Intent(targetContext, LauncherActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            host = activity
            mainRun {
                val wrapped = AppLocales.wrap(activity)
                val renderContext = if (mode == "offline") object : ContextWrapper(wrapped) {
                    override fun getAssets(): AssetManager = this@ScreensaverPlaybackInstrumentation.context.assets
                } else wrapped
                if (mode == "offline") check(renderContext.assets.open("screensaver.json").bufferedReader().use { it.readText().trim() } == "[]")
                val view = ScreenSaverView(renderContext)
                renderer = view
                dialog = Dialog(activity).apply {
                    window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    setContentView(view)
                    show()
                    window?.setLayout(-1, -1)
                }
                val prefs = targetContext.getSharedPreferences("orbita", 0)
                view.start(30_000L, prefs.getInt("screensaver_dim", 25), prefs.getBoolean("screensaver_clock", false))
                check(dialog!!.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            val frames = JSONArray()
            val begin = SystemClock.elapsedRealtime()
            var last: String? = null
            val required = if (mode == "offline") 3 else 2
            while (frames.length() < required && SystemClock.elapsedRealtime() - begin < 110_000L) {
                var frame: JSONObject? = null
                mainRun {
                    val view = requireNotNull(renderer)
                    val bitmap = field(view, "bitmap")
                    if (mode == "offline") {
                        check((field(view, "photos") as List<*>).isEmpty() && bitmap == null)
                        val seed = field(view, "lastProceduralSeed") as? Long
                        if (seed != null && seed.toString() != last) {
                            val theme = ProceduralSceneRules.theme(seed)
                            if (frames.length() > 0) check(frames.getJSONObject(frames.length() - 1).getString("theme") != theme)
                            last = seed.toString()
                            frame = JSONObject().put("seed", seed.toString()).put("theme", theme)
                        }
                    } else if (bitmap != null) {
                        val index = field(view, "previous") as Int
                        if (index >= 0 && index.toString() != last) {
                            last = index.toString()
                            frame = JSONObject().put("photo_index", index)
                                .put("caption", (field(view, "caption") as TextView).text.toString())
                        }
                    }
                }
                frame?.let { frames.put(it.put("elapsed_ms", SystemClock.elapsedRealtime() - begin)) }
                SystemClock.sleep(500)
            }
            report.put("frames", frames)
            check(frames.length() == required) { "Renderer did not rotate enough frames" }
            report.put("rotation", "passed")
        } catch (error: Throwable) {
            failure = error
            report.put("error", error.javaClass.simpleName + ": " + error.message)
        } finally {
            try {
                mainRun { renderer?.stop(); dialog?.dismiss() }
            } catch (error: Throwable) {
                report.put("renderer_cleanup_error", error.javaClass.simpleName)
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
            try {
                // Keep default Home from immediately relaunching while restoring its preferences.
                val settings = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val component = requireNotNull(settings.resolveActivity(targetContext.packageManager))
                check(component.packageName != targetContext.packageName)
                mainRun { targetContext.startActivity(settings.setComponent(component)) }
                waitForIdleSync()
                val expires = SystemClock.elapsedRealtime() + 5_000L
                var parked = false
                while (SystemClock.elapsedRealtime() < expires) {
                    val root = uiAutomation.rootInActiveWindow
                    val foreground = try { root?.packageName?.toString() } finally { root?.recycle() }
                    var focused = false
                    mainRun { focused = host?.hasWindowFocus() == true }
                    if (foreground == component.packageName && !focused) { parked = true; break }
                    SystemClock.sleep(50)
                }
                check(parked) { "Settings foreground not confirmed" }
                mainRun { host?.finish() }
                waitForIdleSync()
                repeat(20) {
                    if (host?.isDestroyed != false) return@repeat
                    SystemClock.sleep(50)
                }
                check(host?.isDestroyed != false) { "Launcher survived cleanup" }
            } catch (error: Throwable) {
                report.put("cleanup_error", error.javaClass.simpleName)
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
            try {
                snapshot?.let {
                    mainRun {
                        PreferenceStateSnapshot.write(targetContext, it)
                        check(PreferenceStateSnapshot.matches(targetContext, it))
                        val after = PreferenceStateSnapshot.read(targetContext)
                        directory?.let { destination -> File(destination, "prefs-after.json").writeText(after.toString(2)) }
                        report.put("restored", true).put("after_digest", PreferenceStateSnapshot.digest(after))
                    }
                }
            } catch (error: Throwable) {
                report.put("restoration_error", error.javaClass.simpleName)
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
            directory?.let { File(it, "playback.json").writeText(report.toString(2)) }
        }
        finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("result", if (failure == null) "playback:ok" else "playback:error") })
    }
}
