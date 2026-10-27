package br.com.lume.launcher

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

/** A separate secure window prevents online photos from entering launcher/task snapshots. */
class ScreenSaverActivity : Activity() {
    private lateinit var renderer: ScreenSaverView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        renderer = ScreenSaverView(this)
        setContentView(renderer)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.windowInsetsController?.apply {
            hide(WindowInsets.Type.systemBars())
            systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("orbita", MODE_PRIVATE)
        renderer.start(prefs.getLong("screensaver_interval", 60000L), prefs.getInt("screensaver_dim", 25), prefs.getBoolean("screensaver_clock", false))
    }

    override fun onPause() { renderer.stop(); super.onPause(); finish() }
    override fun onDestroy() { renderer.stop(); super.onDestroy() }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val exits = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> true
            else -> false
        }
        if (!exits) return super.dispatchKeyEvent(event)
        // Finish after consuming UP, so the same press cannot open an app underneath.
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) finish()
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) finish()
        return true
    }

    @Deprecated("Android 11 remote Back support")
    override fun onBackPressed() { finish() }
}
