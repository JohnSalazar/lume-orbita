package br.com.lume.launcher

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import br.com.lume.launcher.core.ScreensaverRules
import br.com.lume.launcher.core.ScreensaverAttemptCoordinator
import br.com.lume.launcher.core.ProceduralSceneRules
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import kotlin.random.Random

/** Online photos live only in this renderer's RAM. start/stop must run on the UI thread. */
class ScreenSaverView(context: Context) : FrameLayout(context) {
    private data class Photo(val title: String, val artist: String, val license: String, val imageUrl: String)
    private class OutlinedLabel(context: Context) : TextView(context) {
        init { gravity = Gravity.TOP or Gravity.START; setTextColor(Color.WHITE) }
        override fun onDraw(canvas: Canvas) {
            val textLayout = layout
            if (textLayout != null) {
                val checkpoint = canvas.save()
                val oldColor = paint.color; val oldStyle = paint.style
                val oldWidth = paint.strokeWidth; val oldJoin = paint.strokeJoin
                try {
                    canvas.translate(compoundPaddingLeft.toFloat(), extendedPaddingTop.toFloat())
                    paint.color = Color.BLACK; paint.style = Paint.Style.STROKE
                    paint.strokeWidth = resources.displayMetrics.density; paint.strokeJoin = Paint.Join.ROUND
                    textLayout.draw(canvas)
                } finally {
                    paint.color = oldColor; paint.style = oldStyle
                    paint.strokeWidth = oldWidth; paint.strokeJoin = oldJoin
                    canvas.restoreToCount(checkpoint)
                }
            }
            super.onDraw(canvas)
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val attempts = ScreensaverAttemptCoordinator()
    private val image = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val shade = View(context).apply { setBackgroundColor(Color.BLACK); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    private val caption = OutlinedLabel(context).apply {
        textSize = 11f; setPadding(dp(14), dp(8), dp(14), dp(8))
        background = labelBackground()
    }
    private val clock = OutlinedLabel(context).apply {
        textSize = 32f
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setPadding(dp(14), dp(8), dp(14), dp(8)); background = labelBackground()
    }
    private var running = false
    private var interval = 60_000L
    private var clockEnabled = false
    private var photos = emptyList<Photo>()
    private var previous = -1
    private var lastAttempt = -1
    private var lastProceduralSeed: Long? = null
    private var bitmap: Bitmap? = null
    private var request: MemoryImageLoader? = null
    private var job: Future<*>? = null
    private var deadline: Runnable? = null
    private val lowRam = context.getSystemService(ActivityManager::class.java)?.isLowRamDevice ?: true
    private val next = Runnable { loadNext() }
    private val nextProcedural = Runnable { if (attempts.shouldRotateProcedural()) showProcedural(true) }
    private val tick = object : Runnable {
        override fun run() {
            if (running) {
                if (clockEnabled) clock.text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                sizeLabels()
                handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }

    init {
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(4, 13, 24), Color.rgb(20, 31, 40), Color.rgb(8, 20, 29)))
        addView(image, LayoutParams(-1, -1)); addView(shade, LayoutParams(-1, -1))
        addView(caption, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(dp(24), dp(24), dp(24), dp(24)) })
        addView(clock, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(24), dp(24), dp(24), dp(24)) })
        caption.visibility = GONE; clock.visibility = GONE
        contentDescription = "Proteção de tela"
    }

    private fun dp(value: Int) = GlassUi.dp(context, value)
    private fun labelBackground() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
        intArrayOf(Color.argb(34, 12, 20, 28), Color.argb(22, 5, 10, 18))).apply { cornerRadius = dp(18).toFloat() }
    private fun sizeLabels() {
        if (width <= 0) return
        clock.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val reserved = if (clockEnabled) clock.measuredWidth + dp(16) else 0
        caption.maxWidth = minOf(dp(760), (width - dp(48) - reserved).coerceAtLeast(1))
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); sizeLabels() }
    private fun requireUi() = check(Looper.myLooper() == Looper.getMainLooper()) { "Screensaver lifecycle must run on UI thread" }

    fun start(intervalMillis: Long = 60_000L, dimPercent: Int = 25, showClock: Boolean = false) {
        requireUi()
        val requestedInterval = ScreensaverRules.intervalMillis(intervalMillis)
        val requestedShade = ScreensaverRules.dimPercent(dimPercent) / 100f
        if (running && interval == requestedInterval && shade.alpha == requestedShade && clockEnabled == showClock) return
        stop()
        running = true; interval = requestedInterval; clockEnabled = showClock
        shade.alpha = requestedShade
        clock.visibility = if (showClock) VISIBLE else GONE
        attempts.start()
        showProcedural(true)
        tick.run()
        // This tiny packaged metadata catalog does not depend on the network worker becoming free.
        photos = readCatalog()
        if (photos.isNotEmpty()) loadNext()
    }

    fun stop() {
        requireUi(); running = false; attempts.stop()
        handler.removeCallbacks(next); handler.removeCallbacks(tick); handler.removeCallbacks(nextProcedural)
        deadline?.let { handler.removeCallbacks(it) }; deadline = null
        request?.cancel(); request = null
        job?.cancel(true); job = null; MemoryImageWorker.purgeCanceled()
        photos = emptyList(); previous = -1; lastAttempt = -1
        lastProceduralSeed = null
        clearImage(); caption.text = ""; caption.visibility = GONE; clock.text = ""; clock.visibility = GONE
        contentDescription = "Proteção de tela"
    }

    private fun clearImage() {
        image.setImageDrawable(null)
        bitmap = null // Published bitmaps may still be used by RenderThread; never recycle them.
    }

    private fun readCatalog(): List<Photo> = try {
        val json = context.assets.open("screensaver.json").bufferedReader().use { it.readText() }
        val entries = if (json.trimStart().startsWith("[")) JSONArray(json) else JSONObject(json).getJSONArray("entries")
        (0 until minOf(entries.length(), 100)).mapNotNull { index ->
            val entry = entries.optJSONObject(index) ?: return@mapNotNull null
            val address = entry.optString("imageUrl")
            val title = entry.optString("title").take(140)
            val artist = entry.optString("artist").take(140)
            val license = entry.optString("license").take(80)
            if (!ScreensaverRules.isAllowedImageUrl(address) || title.isBlank() || artist.isBlank() || license.isBlank()) null
            else Photo(title, artist, license, address)
        }.distinctBy { it.imageUrl }
    } catch (_: Exception) { emptyList() }

    private fun loadNext() {
        if (!running || photos.isEmpty()) return
        val ticket = attempts.begin() ?: return
        val index = ScreensaverRules.nextRequestIndex(photos.size, previous, lastAttempt, Random.nextInt())
        lastAttempt = index
        val photo = photos[index]
        val loader = MemoryImageLoader()
        request = loader
        val timeout = Runnable {
            if (running && request === loader && attempts.complete(ticket)) {
                val expiredJob = job
                deadline = null; request = null; job = null
                loader.cancel(); expiredJob?.cancel(true); MemoryImageWorker.purgeCanceled()
                Log.w("LumePhoto", "load_timeout")
                failedAttempt(false)
            }
        }
        deadline = timeout; handler.postDelayed(timeout, ScreensaverRules.REQUEST_MILLIS)
        try {
            job = MemoryImageWorker.submit(Runnable {
                var result: Bitmap? = null
                var failure: Throwable? = null
                try { result = loader.load(photo.imageUrl, lowRam) }
                catch (error: Exception) { failure = error }
                catch (error: OutOfMemoryError) { failure = error }
                finally {
                    val loaded = result
                    val error = failure
                    handler.post {
                        handler.removeCallbacks(timeout)
                        if (!attempts.complete(ticket)) {
                            loaded?.recycle() // Never published; an expired attempt cannot mutate the next one.
                            return@post
                        }
                        deadline = null; request = null; job = null
                        if (loaded == null) {
                            val code = (error as? MemoryImageLoader.HttpFailure)?.statusCode
                            Log.w("LumePhoto", "load_failed type=${error?.javaClass?.simpleName ?: "Unknown"} http=${code ?: 0}")
                            failedAttempt(code == 429)
                        } else {
                            handler.removeCallbacks(nextProcedural)
                            clearImage(); bitmap = loaded; image.setImageBitmap(loaded)
                            previous = index; attempts.markDisplayed()
                            contentDescription = "Proteção de tela · ${photo.title}"
                            caption.text = "${photo.title} · ${photo.artist}"
                            caption.visibility = VISIBLE
                            handler.postDelayed(next, interval)
                        }
                    }
                }
            })
        } catch (_: RejectedExecutionException) {
            handler.removeCallbacks(timeout)
            if (attempts.complete(ticket)) {
                deadline = null; request = null; job = null; loader.cancel()
                failedAttempt(true)
            }
        }
    }

    private fun showProcedural(forceNew: Boolean = false) {
        if (!running) return
        val wasProcedural = attempts.proceduralActive
        attempts.markFallback()
        if (wasProcedural && !forceNew) return
        val seed = ProceduralSceneRules.nextSeed(lastProceduralSeed, Random.nextLong())
        val landscape = ProceduralLandscapeDrawable(seed)
        clearImage(); image.setImageDrawable(landscape)
        lastProceduralSeed = seed
        caption.text = "${landscape.title} · Arte procedural · lume órbita"
        caption.visibility = VISIBLE
        contentDescription = "Proteção de tela · Arte procedural · ${landscape.title}"
        handler.removeCallbacks(nextProcedural)
        handler.postDelayed(nextProcedural, interval)
    }

    private fun failedAttempt(rateLimited: Boolean) {
        showProcedural()
        handler.removeCallbacks(next)
        handler.postDelayed(next, attempts.retryDelay(ScreensaverRules.RETRY_MILLIS, rateLimited, photos.size > 1))
    }

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
}
