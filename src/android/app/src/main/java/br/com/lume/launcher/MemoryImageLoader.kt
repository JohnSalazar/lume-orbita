package br.com.lume.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import br.com.lume.launcher.core.ScreensaverRules
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** One process-wide image worker, including old renderers still unwinding a canceled request. */
internal object MemoryImageWorker {
    internal val executor = ThreadPoolExecutor(0, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(1))
    fun submit(task: Runnable): Future<*> { executor.purge(); return executor.submit(task) }
    fun purgeCanceled() = executor.purge()
}

/** One synchronous request at a time. No disk cache, files, credentials or global HTTP changes. */
class MemoryImageLoader internal constructor(
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val nowNanos: () -> Long = System::nanoTime
) {
    @Volatile private var canceled = false
    class HttpFailure(val statusCode: Int) : IOException("HTTP $statusCode")

    /** Non-blocking: never close a socket or take a connection lock on the UI thread. */
    fun cancel() { canceled = true }

    private fun checkCanceled() {
        if (canceled || Thread.currentThread().isInterrupted) throw InterruptedIOException("Image request canceled")
    }

    internal fun download(imageUrl: String): ByteArray {
        checkCanceled()
        val started = nowNanos()
        fun remaining(): Int {
            checkCanceled()
            val elapsed = TimeUnit.NANOSECONDS.toMillis(nowNanos() - started).coerceAtLeast(0L)
            val left = ScreensaverRules.REQUEST_MILLIS - elapsed
            if (left <= 0) throw InterruptedIOException("Image request deadline exceeded")
            return left.toInt()
        }
        var address = imageUrl
        var redirects = 0
        while (true) {
            remaining()
            if (!ScreensaverRules.isAllowedImageUrl(address)) throw IOException("Image host not allowed")
            val connection = connect(URL(address))
            try {
                checkCanceled()
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.allowUserInteraction = false
                connection.connectTimeout = minOf(8_000, remaining())
                connection.readTimeout = minOf(12_000, remaining())
                connection.setRequestProperty("Accept", "image/jpeg")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("Cache-Control", "no-store, no-cache")
                connection.setRequestProperty("User-Agent", "LumeOrbita/0.1.7 (Android TV screensaver)")
                val status = connection.responseCode
                remaining()
                if (status in listOf(301, 302, 303, 307, 308)) {
                    if (redirects++ >= ScreensaverRules.MAX_REDIRECTS) throw IOException("Too many redirects")
                    val location = connection.getHeaderField("Location")?.takeIf { it.isNotBlank() } ?: throw IOException("Redirect without location")
                    address = URL(URL(address), location).toExternalForm()
                    continue
                }
                if (status != HttpURLConnection.HTTP_OK) throw HttpFailure(status)
                if (!ScreensaverRules.isJpegMime(connection.contentType)) throw IOException("JPEG unavailable")
                val length = connection.contentLengthLong
                if (length > ScreensaverRules.MAX_ENCODED_BYTES || length == 0L) throw IOException("Image length rejected")
                val initial = if (length in 1..ScreensaverRules.MAX_ENCODED_BYTES.toLong()) length.toInt() else 64 * 1024
                val output = ByteArrayOutputStream(initial)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        connection.readTimeout = minOf(12_000, remaining())
                        val count = input.read(buffer)
                        remaining()
                        if (count < 0) break
                        if (count == 0 || count > ScreensaverRules.MAX_ENCODED_BYTES - output.size()) throw IOException("Image payload rejected")
                        output.write(buffer, 0, count)
                    }
                }
                if (length > 0 && length != output.size().toLong()) throw IOException("Incomplete image")
                val bytes = output.toByteArray()
                if (!ScreensaverRules.hasJpegSignature(bytes)) throw IOException("Invalid JPEG signature")
                return bytes
            } finally {
                connection.disconnect()
            }
        }
    }

    fun load(imageUrl: String, lowRam: Boolean): Bitmap {
        val bytes = download(imageUrl)
        checkCanceled()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (!ScreensaverRules.isJpegMime(bounds.outMimeType) || !ScreensaverRules.sourceAllowed(bounds.outWidth, bounds.outHeight)) throw IOException("Image dimensions rejected")
        val options = BitmapFactory.Options().apply {
            inSampleSize = ScreensaverRules.sampleSize(bounds.outWidth, bounds.outHeight, lowRam)
            inScaled = false; inMutable = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        checkCanceled()
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: throw IOException("JPEG decode failed")
        try {
            checkCanceled()
            if (!ScreensaverRules.decodedAllowed(bitmap.width, bitmap.height, bitmap.allocationByteCount.toLong(), lowRam)) throw IOException("Decoded image exceeds budget")
            return bitmap
        } catch (failure: Exception) {
            bitmap.recycle() // Never published to a View or RenderThread.
            throw failure
        }
    }
}
