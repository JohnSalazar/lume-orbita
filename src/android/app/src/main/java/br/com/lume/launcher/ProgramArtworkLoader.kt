package br.com.lume.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.concurrent.TimeUnit

/** One request per instance, off the UI thread. The caller owns returned bitmaps and scheduling. */
class ProgramArtworkLoader internal constructor(
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val nowNanos: () -> Long = System::nanoTime
) {
    @Volatile private var canceled = false

    /** Terminal, non-blocking cancellation: socket cleanup stays on the calling worker. */
    fun cancel() { canceled = true }

    private fun checkCanceled() {
        if (canceled || Thread.currentThread().isInterrupted) throw InterruptedIOException("Artwork canceled")
    }

    /** Unsupported/unapproved origins return null; transport/format failures throw IOException. */
    fun load(uri: String, publisher: String): Bitmap? {
        val bytes = download(uri, publisher) ?: return null
        checkCanceled()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val sample = ArtworkPolicy.sampleSize(bounds.outWidth, bounds.outHeight)
        if (sample == null || ArtworkPolicy.mime(bounds.outMimeType) != ArtworkPolicy.format(bytes)) throw IOException("Artwork dimensions/format rejected")
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample; inScaled = false; inMutable = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        checkCanceled()
        val bitmap = try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }
            catch (_: OutOfMemoryError) { throw IOException("Artwork allocation rejected") }
            ?: throw IOException("Artwork decode failed")
        try {
            checkCanceled()
            if (bitmap.width > 480 || bitmap.height > 270 || bitmap.allocationByteCount > 1024 * 1024) throw IOException("Artwork allocation rejected")
            return bitmap
        } catch (failure: IOException) {
            bitmap.recycle() // Never published to a View.
            throw failure
        }
    }

    internal fun download(uri: String, publisher: String): ByteArray? {
        checkCanceled()
        var address = ArtworkPolicy.url(uri, publisher) ?: return null
        val started = nowNanos()
        fun remaining(): Int {
            checkCanceled()
            val elapsed = TimeUnit.NANOSECONDS.toMillis(nowNanos() - started).coerceAtLeast(0L)
            val left = 12_000L - elapsed
            if (left <= 0) throw InterruptedIOException("Artwork deadline exceeded")
            return left.toInt()
        }
        var redirects = 0
        while (true) {
            remaining()
            // Defense in depth only: URLConnection resolves again; this does not pin DNS/TLS.
            val addresses = resolve(address.host)
            remaining()
            if (addresses.isEmpty() || addresses.any { !ArtworkPolicy.publicAddress(it) }) return null
            val connection = connect(address)
            try {
                checkCanceled()
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.allowUserInteraction = false
                // URLConnection may spend connectTimeout on each resolved address/route.
                // Reserve roughly half the request budget for TLS/body instead of 5s per IP.
                connection.connectTimeout = ArtworkPolicy.connectTimeout(remaining(), addresses.size)
                connection.readTimeout = minOf(7_000, remaining())
                connection.setRequestProperty("Accept", "image/jpeg,image/png,image/webp")
                connection.setRequestProperty("Accept-Encoding", "identity")
                connection.setRequestProperty("Cache-Control", "no-store, no-cache")
                connection.setRequestProperty("User-Agent", "LumeOrbita/0.2.3 (Android TV artwork)")
                val status = connection.responseCode
                remaining()
                if (status in intArrayOf(301, 302, 303, 307, 308)) {
                    if (redirects++ >= 3) throw IOException("Artwork redirects exceeded")
                    val location = connection.getHeaderField("Location") ?: throw IOException("Artwork redirect unavailable")
                    address = ArtworkPolicy.url(URL(address, location).toExternalForm(), publisher) ?: return null
                    continue
                }
                if (status != 200) throw IOException("Artwork HTTP $status")
                val mime = ArtworkPolicy.mime(connection.contentType) ?: throw IOException("Artwork MIME rejected")
                val length = connection.contentLengthLong
                if (length == 0L || length > ArtworkPolicy.MAX_BYTES) throw IOException("Artwork length rejected")
                val output = ByteArrayOutputStream(if (length in 1..ArtworkPolicy.MAX_BYTES.toLong()) length.toInt() else 16 * 1024)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        connection.readTimeout = minOf(7_000, remaining())
                        val count = input.read(buffer)
                        remaining()
                        if (count < 0) break
                        if (count == 0 || count > ArtworkPolicy.MAX_BYTES - output.size()) throw IOException("Artwork payload rejected")
                        output.write(buffer, 0, count)
                    }
                }
                if (length > 0 && length != output.size().toLong()) throw IOException("Artwork incomplete")
                val bytes = output.toByteArray()
                if (ArtworkPolicy.format(bytes) != mime) throw IOException("Artwork format rejected")
                return bytes
            } finally { connection.disconnect() }
        }
    }
}

/** Conservative publisher/CDN policy. Unknown origins deliberately fall back to the app icon. */
internal object ArtworkPolicy {
    const val MAX_BYTES = 2 * 1024 * 1024
    private val hosts = mapOf(
        "com.globo.globotv" to listOf("glbimg.com"), "com.globo.globoplay" to listOf("glbimg.com"),
        "com.disney.disneyplus" to listOf("disney-plus.net"),
        "com.netflix.ninja" to listOf("nflximg.net", "nflximg.com", "nflxext.com"),
        "com.amazon.amazonvideo.livingroom" to listOf("media-amazon.com", "ssl-images-amazon.com"),
        "com.wbd.stream" to listOf("max.com", "hbo.com", "hbomaxcdn.com"),
        "com.hbo.hbonow" to listOf("hbo.com", "hbomaxcdn.com"),
        "com.apple.atve.androidtv.appletv" to listOf("mzstatic.com"),
        "com.google.android.youtube.tv" to listOf("ytimg.com"),
        "br.com.sbt.mais" to listOf("sbt.com.br"), "br.com.sbt.sbtvideos" to listOf("sbt.com.br"), "com.sbt.tv" to listOf("sbt.com.br")
    )
    private val exactHosts = mapOf(
        "com.wbd.stream" to "images.cdn.prd.api.discomax.com",
        "com.disney.disneyplus" to "disney.images.edge.bamgrid.com"
    )

    fun url(raw: String, publisher: String): URL? = try {
        val uri = URI(raw)
        val host = uri.host?.lowercase(Locale.ROOT)
        if (raw.length > 4096 || uri.scheme != "https" || host == null || uri.rawUserInfo != null || uri.port != -1 || uri.fragment != null ||
            !host.matches(Regex("[a-z0-9]+(?:[.-][a-z0-9]+)*")) || host.all { it.isDigit() || it == '.' } ||
            (hosts[publisher]?.none { host == it || host.endsWith(".$it") } != false && exactHosts[publisher] != host)) null else uri.toURL()
    } catch (_: Exception) { null }

    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 255 }
        return when (bytes.size) {
            4 -> bytes[0] !in listOf(0, 10, 127) && bytes[0] < 224 &&
                !(bytes[0] == 100 && bytes[1] in 64..127) && !(bytes[0] == 169 && bytes[1] == 254) &&
                !(bytes[0] == 172 && bytes[1] in 16..31) && !(bytes[0] == 192 && bytes[1] == 168) &&
                !(bytes[0] == 198 && bytes[1] in 18..19) &&
                !(bytes.take(3) in listOf(listOf(192, 0, 0), listOf(192, 0, 2), listOf(198, 51, 100), listOf(203, 0, 113)))
            16 -> bytes[0] in 0x20..0x3f && bytes.take(4) != listOf(0x20, 0x01, 0x0d, 0xb8)
            else -> false
        }
    }

    fun mime(raw: String?): String? = raw?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in listOf("image/jpeg", "image/png", "image/webp") }

    fun connectTimeout(remainingMillis: Int, resolvedCount: Int): Int {
        require(remainingMillis > 0 && resolvedCount > 0)
        return minOf(remainingMillis, (remainingMillis.toLong() / resolvedCount / 2).coerceIn(250L, 1_000L).toInt())
    }

    fun sampleSize(width: Int, height: Int): Int? {
        if (width !in 1..8192 || height !in 1..8192 || width.toLong() * height > 16_000_000) return null
        var sample = 1
        while ((width.toLong() + sample - 1) / sample > 480 || (height.toLong() + sample - 1) / sample > 270) sample *= 2
        return sample
    }

    fun format(bytes: ByteArray): String? {
        fun isTag(offset: Int, text: String): Boolean = offset >= 0 && offset + text.length <= bytes.size && text.indices.all { bytes[offset + it].toInt() and 255 == text[it].code }
        fun number(offset: Int, little: Boolean): Long = (0..3).fold(0L) { value, i -> value or ((bytes[offset + i].toLong() and 255) shl (8 * if (little) i else 3 - i)) }
        if (bytes.size >= 5 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() && bytes[bytes.size - 2] == 0xff.toByte() && bytes.last() == 0xd9.toByte()) return "image/jpeg"
        val png = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        if (bytes.size >= 8 && bytes.take(8) == png.toList()) {
            var offset = 8
            var image = false
            while (offset.toLong() + 12 <= bytes.size) {
                val length = number(offset, false)
                val end = offset.toLong() + 12 + length
                if (end > bytes.size || isTag(offset + 4, "acTL") || isTag(offset + 4, "fcTL") || isTag(offset + 4, "fdAT")) return null
                if (isTag(offset + 4, "IDAT")) image = true
                if (isTag(offset + 4, "IEND")) return if (length == 0L && end == bytes.size.toLong() && image) "image/png" else null
                offset = end.toInt()
            }
            return null
        }
        if (bytes.size >= 12 && isTag(0, "RIFF") && isTag(8, "WEBP") && number(4, true) + 8 == bytes.size.toLong()) {
            var offset = 12
            var image = false
            while (offset.toLong() + 8 <= bytes.size) {
                val length = number(offset + 4, true)
                val end = offset.toLong() + 8 + length + length % 2
                if (end > bytes.size || isTag(offset, "ANIM") || isTag(offset, "ANMF")) return null
                if (isTag(offset, "VP8X") && (length < 10 || bytes[offset + 8].toInt() and 2 != 0)) return null
                if (isTag(offset, "VP8 ") || isTag(offset, "VP8L")) image = true
                offset = end.toInt()
            }
            if (offset == bytes.size && image) return "image/webp"
        }
        return null
    }
}
