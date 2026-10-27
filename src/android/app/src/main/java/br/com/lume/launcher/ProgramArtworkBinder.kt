package br.com.lume.launcher

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import android.view.View
import android.widget.ImageView
import br.com.lume.launcher.core.SharedProgram
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object ProgramArtworkWorker {
    internal val executor = ThreadPoolExecutor(2, 2, 15L, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(4)).apply { allowCoreThreadTimeOut(true) }
    fun submit(task: Runnable): Future<*> { executor.purge(); return executor.submit(task) }
    fun purgeCanceled() = executor.purge()
}

/** Small visible-only artwork cache. Nothing is saved to disk or fetched on the UI thread. */
class ProgramArtworkBinder(private val active: () -> Boolean) {
    private val handler = Handler(Looper.getMainLooper())
    private val bounds = Rect()
    private val cache = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private class Request {
        @Volatile var canceled = false
        @Volatile var loader: ProgramArtworkLoader? = null
        var future: Future<*>? = null
        var timeout: Runnable? = null
    }
    private class Binding(val image: ImageView, val program: SharedProgram, val fallback: Drawable?) {
        var request: Request? = null
        var attempted = false
        var displayed = false
        var retryAt = 0L
    }
    private val bindings = mutableListOf<Binding>()
    private var nextRetryAt = Long.MAX_VALUE
    private val retry = Runnable { nextRetryAt = Long.MAX_VALUE; refresh() }

    private fun scheduleRetry(at: Long) {
        if (at >= nextRetryAt) return
        nextRetryAt = at; handler.removeCallbacks(retry); handler.postAtTime(retry, at)
    }

    fun bind(image: ImageView, program: SharedProgram) {
        bindings.add(Binding(image, program, image.drawable))
    }

    private fun cancel(binding: Binding) {
        binding.request?.let { request ->
            request.canceled = true; request.loader?.cancel(); request.future?.cancel(true)
            request.timeout?.let { handler.removeCallbacks(it) }
        }
        binding.request = null
        ProgramArtworkWorker.purgeCanceled()
    }

    fun pause() {
        handler.removeCallbacks(retry); nextRetryAt = Long.MAX_VALUE
        bindings.forEach { binding ->
            cancel(binding); binding.attempted = false; binding.retryAt = 0
        }
    }

    fun dispose() { pause(); clearBindings(); cache.evictAll() }

    fun clearBindings() {
        handler.removeCallbacks(retry); nextRetryAt = Long.MAX_VALUE
        bindings.forEach { cancel(it); it.image.setImageDrawable(null) }
        bindings.clear()
    }

    fun refresh() {
        if (!active()) return
        bindings.forEach { binding ->
            val view = binding.image
            if (!view.hasWindowFocus()) {
                cancel(binding); binding.attempted = false
                return@forEach
            }
            val visible = view.isAttachedToWindow && view.isShown && view.getGlobalVisibleRect(bounds) && bounds.width() > 4 && bounds.height() > 4
            if (!visible) {
                if (binding.request != null) cancel(binding)
                binding.attempted = false
                if (binding.displayed) {
                    binding.displayed = false; view.scaleType = ImageView.ScaleType.CENTER_INSIDE
                    view.setImageDrawable(binding.fallback); view.contentDescription = null
                }
                return@forEach
            }
            if (binding.displayed || binding.request != null || binding.attempted) return@forEach
            if (SystemClock.uptimeMillis() < binding.retryAt) { scheduleRetry(binding.retryAt); return@forEach }
            val addresses = listOf(binding.program.thumbnailUri, binding.program.posterUri).filter { it.isNotBlank() }.distinct()
            val cached = addresses.firstNotNullOfOrNull { cache.get(binding.program.packageName + "|" + it) }
            if (cached != null) { publish(binding, cached); return@forEach }
            binding.attempted = true
            if (addresses.isEmpty()) return@forEach
            val request = Request(); binding.request = request
            request.timeout = Runnable { if (binding.request === request) {
                cancel(binding); binding.attempted = false
                binding.retryAt = SystemClock.uptimeMillis() + 30_000L; scheduleRetry(binding.retryAt)
            } }
            handler.postDelayed(request.timeout!!, 90_000L) // Queue guard; the transfer gets its own budget after a worker starts.
            try {
                request.future = ProgramArtworkWorker.submit(Runnable {
                    handler.post { if (binding.request === request && !request.canceled) {
                        handler.removeCallbacks(request.timeout!!); handler.postDelayed(request.timeout!!, 25_000L)
                    } }
                    var bitmap: Bitmap? = null
                    var failed = false
                    var cacheKey = ""
                    for (address in addresses) {
                        if (request.canceled) break
                        val loader = ProgramArtworkLoader(); request.loader = loader
                        if (request.canceled) { loader.cancel(); break }
                        bitmap = runCatching { loader.load(address, binding.program.packageName) }.onFailure { failed = true }.getOrNull()
                        if (bitmap != null) { cacheKey = binding.program.packageName + "|" + address; break }
                    }
                    val loaded = bitmap
                    handler.post {
                        request.timeout?.let { handler.removeCallbacks(it) }
                        if (request.canceled || binding.request !== request || !active() || binding !in bindings) {
                            loaded?.recycle() // Never published to a View or cache.
                            return@post
                        }
                        binding.request = null
                        if (loaded != null) { cache.put(cacheKey, loaded); publish(binding, loaded) }
                        else if (failed) {
                            binding.attempted = false; binding.retryAt = SystemClock.uptimeMillis() + 30_000L
                            scheduleRetry(binding.retryAt)
                        }
                        refresh()
                    }
                })
            } catch (_: RejectedExecutionException) {
                cancel(binding); binding.attempted = false
            }
        }
    }

    private fun publish(binding: Binding, bitmap: Bitmap) {
        binding.displayed = true
        binding.image.scaleType = ImageView.ScaleType.CENTER_CROP
        binding.image.setImageBitmap(bitmap)
        // The focusable parent already announces the title and playback details.
        binding.image.contentDescription = null
        binding.image.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}
