package br.com.lume.launcher.core

import java.net.URI
import java.util.Locale

/** Screensaver limits shared by network, rendering and preference validation. */
object ScreensaverRules {
    const val MAX_ENCODED_BYTES = 6 * 1024 * 1024
    const val MAX_REDIRECTS = 3
    const val RETRY_MILLIS = 30_000L
    const val REQUEST_MILLIS = 30_000L

    fun idleMinutes(value: Int): Int = if (value in listOf(2, 5, 10, 15)) value else 2
    fun idleMillis(value: Int): Long = idleMinutes(value) * 60_000L
    fun intervalMillis(value: Long): Long = if (value in listOf(30_000L, 60_000L, 180_000L, 300_000L)) value else 60_000L
    fun dimPercent(value: Int): Int = if (value in listOf(10, 25, 40)) value else 25
    fun decodeBounds(lowRam: Boolean): Pair<Int, Int> = if (lowRam) 1280 to 720 else 1920 to 1080

    fun isAllowedImageUrl(value: String): Boolean = try {
        val uri = URI(value)
        value.length <= 4096 && uri.scheme?.lowercase(Locale.ROOT) == "https" &&
            uri.host?.lowercase(Locale.ROOT) == "upload.wikimedia.org" &&
            uri.userInfo == null && uri.fragment == null && (uri.port == -1 || uri.port == 443)
    } catch (_: java.net.URISyntaxException) {
        false
    }

    fun isJpegMime(value: String?): Boolean = value?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) == "image/jpeg"
    fun hasJpegSignature(bytes: ByteArray): Boolean = bytes.size >= 5 &&
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() &&
        bytes[bytes.lastIndex - 1] == 0xFF.toByte() && bytes.last() == 0xD9.toByte()

    fun sourceAllowed(width: Int, height: Int): Boolean = width in 1..32768 && height in 1..32768 &&
        width.toLong() * height <= 128_000_000L

    fun sampleSize(width: Int, height: Int, lowRam: Boolean): Int {
        require(sourceAllowed(width, height))
        val (maxWidth, maxHeight) = decodeBounds(lowRam)
        var sample = 1
        while ((width.toLong() + sample - 1) / sample > maxWidth || (height.toLong() + sample - 1) / sample > maxHeight) sample *= 2
        return sample
    }

    fun decodedAllowed(width: Int, height: Int, allocationBytes: Long, lowRam: Boolean): Boolean {
        val (maxWidth, maxHeight) = decodeBounds(lowRam)
        return width in 1..maxWidth && height in 1..maxHeight && allocationBytes in 1..(maxWidth.toLong() * maxHeight * 4)
    }

    /** Maps an arbitrary random Int to an index, excluding the last successful image. */
    fun nextIndex(count: Int, previous: Int, random: Int): Int {
        require(count > 0)
        if (count == 1) return 0
        if (previous !in 0 until count) return Math.floorMod(random, count)
        val slot = Math.floorMod(random, count - 1)
        return if (slot >= previous) slot + 1 else slot
    }

    fun nextRequestIndex(count: Int, displayed: Int, attempted: Int, random: Int): Int {
        require(count > 0)
        val preferred = (0 until count).filter { it != displayed && it != attempted }
        val choices = preferred.ifEmpty { (0 until count).filter { it != attempted } }.ifEmpty { listOf(0) }
        return choices[Math.floorMod(random, choices.size)]
    }
}
