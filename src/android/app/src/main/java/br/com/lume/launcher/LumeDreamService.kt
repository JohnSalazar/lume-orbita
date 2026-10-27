package br.com.lume.launcher

import android.service.dreams.DreamService
import android.view.WindowManager

/** Registered as an opt-in native dream; never chooses itself or writes global settings. */
class LumeDreamService : DreamService() {
    private var renderer: ScreenSaverView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false; isFullscreen = true
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        renderer = ScreenSaverView(this).also { setContentView(it) }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        val prefs = getSharedPreferences("orbita", MODE_PRIVATE)
        renderer?.start(prefs.getLong("screensaver_interval", 60_000L), prefs.getInt("screensaver_dim", 25), prefs.getBoolean("screensaver_clock", false))
    }

    override fun onDreamingStopped() { renderer?.stop(); super.onDreamingStopped() }
    override fun onDetachedFromWindow() { renderer?.stop(); renderer = null; super.onDetachedFromWindow() }
    override fun onDestroy() { renderer?.stop(); renderer = null; super.onDestroy() }
}
