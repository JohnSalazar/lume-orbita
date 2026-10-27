package br.com.lume.launcher.core

import org.junit.Assert.*
import org.junit.Test

class ScreensaverRulesTest {
    @Test fun preferencesAcceptOnlySupportedValuesAndFallbackSafely() {
        listOf(2, 5, 10, 15).forEach {
            assertEquals(it, ScreensaverRules.idleMinutes(it))
            assertEquals(it * 60_000L, ScreensaverRules.idleMillis(it))
        }
        listOf(Int.MIN_VALUE, -1, 0, 1, 3, Int.MAX_VALUE).forEach { assertEquals(2, ScreensaverRules.idleMinutes(it)) }
        listOf(30_000L, 60_000L, 180_000L, 300_000L).forEach { assertEquals(it, ScreensaverRules.intervalMillis(it)) }
        listOf(Long.MIN_VALUE, -1, 0, 29_999, 90_000, Long.MAX_VALUE).forEach { assertEquals(60_000L, ScreensaverRules.intervalMillis(it)) }
        listOf(10, 25, 40).forEach { assertEquals(it, ScreensaverRules.dimPercent(it)) }
        listOf(Int.MIN_VALUE, -1, 0, 20, 100, Int.MAX_VALUE).forEach { assertEquals(25, ScreensaverRules.dimPercent(it)) }
    }

    @Test fun onlyExactPublicHttpsImageHostWithoutCredentialsIsAllowed() {
        listOf("https://upload.wikimedia.org/a.jpg", "HTTPS://UPLOAD.WIKIMEDIA.ORG/a.jpg", "https://upload.wikimedia.org:443/a.jpg?width=1920").forEach { assertTrue(it, ScreensaverRules.isAllowedImageUrl(it)) }
        listOf("", "/a.jpg", "//upload.wikimedia.org/a.jpg", "https:/a.jpg", "http://upload.wikimedia.org/a.jpg", "file:///a.jpg", "https://commons.wikimedia.org/a.jpg", "https://upload.wikimedia.org.evil.test/a.jpg", "https://upload.wikimedia.org./a.jpg", "https://user@upload.wikimedia.org/a.jpg", "https://user:pass@upload.wikimedia.org/a.jpg", "https://upload.wikimedia.org:8443/a.jpg", "https://upload.wikimedia.org/a.jpg#x", "https://[invalid", "https://upload.wikimedia.org/" + "a".repeat(4096)).forEach { assertFalse(it, ScreensaverRules.isAllowedImageUrl(it)) }
    }

    @Test fun jpegRequiresMimeAndCompleteSignature() {
        assertTrue(ScreensaverRules.isJpegMime(" IMAGE/JPEG ; charset=binary"))
        listOf(null, "", "image/png", "image/webp", "image/jpeg2", "application/octet-stream").forEach { assertFalse(ScreensaverRules.isJpegMime(it)) }
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xD9.toByte())
        assertTrue(ScreensaverRules.hasJpegSignature(jpeg))
        assertFalse(ScreensaverRules.hasJpegSignature(byteArrayOf()))
        assertFalse(ScreensaverRules.hasJpegSignature(jpeg.copyOf(4)))
        listOf(0, 1, 2, 3, 4).forEach { index -> assertFalse(ScreensaverRules.hasJpegSignature(jpeg.copyOf().also { it[index] = 0 })) }
    }

    @Test fun sourceDimensionsHaveLongArithmeticAndExplicitCeilings() {
        assertTrue(ScreensaverRules.sourceAllowed(1, 1))
        assertTrue(ScreensaverRules.sourceAllowed(32768, 3000))
        listOf(0 to 1, -1 to 1, 1 to 0, 1 to -1, 32769 to 1, 1 to 32769, 32768 to 32768, Int.MAX_VALUE to Int.MAX_VALUE).forEach { (w, h) -> assertFalse(ScreensaverRules.sourceAllowed(w, h)) }
    }

    @Test fun sampleSizeUsesPowersOfTwoWithoutDecodedBudgetOverflow() {
        assertEquals(1280 to 720, ScreensaverRules.decodeBounds(true))
        assertEquals(1920 to 1080, ScreensaverRules.decodeBounds(false))
        assertEquals(1, ScreensaverRules.sampleSize(1280, 720, true))
        assertEquals(2, ScreensaverRules.sampleSize(1920, 1080, true))
        assertEquals(4, ScreensaverRules.sampleSize(3840, 2160, true))
        assertEquals(2, ScreensaverRules.sampleSize(3840, 2160, false))
        assertEquals(2, ScreensaverRules.sampleSize(1280, 721, true))
        listOf(true, false).forEach { low ->
            listOf(1 to 1, 8000 to 6000, 32768 to 1, 1 to 32768, 1920 to 1280).forEach { (w, h) ->
                val sample = ScreensaverRules.sampleSize(w, h, low)
                val (maxW, maxH) = ScreensaverRules.decodeBounds(low)
                assertEquals(0, sample and (sample - 1))
                assertTrue((w.toLong() + sample - 1) / sample <= maxW)
                assertTrue((h.toLong() + sample - 1) / sample <= maxH)
            }
        }
        assertThrows(IllegalArgumentException::class.java) { ScreensaverRules.sampleSize(0, 1, true) }
    }

    @Test fun decodedDimensionsAndActualAllocationAreBothChecked() {
        assertTrue(ScreensaverRules.decodedAllowed(1280, 720, 1280L * 720 * 4, true))
        assertTrue(ScreensaverRules.decodedAllowed(1920, 1080, 1920L * 1080 * 4, false))
        listOf(0 to 1, 1 to 0, -1 to 1, 1281 to 720, 1280 to 721).forEach { (w, h) -> assertFalse(ScreensaverRules.decodedAllowed(w, h, 4, true)) }
        listOf(-1L, 0L, 1280L * 720 * 4 + 1, Long.MAX_VALUE).forEach { assertFalse(ScreensaverRules.decodedAllowed(1, 1, it, true)) }
    }

    @Test fun randomChoiceNeverImmediatelyRepeatsAndHandlesEdges() {
        assertEquals(0, ScreensaverRules.nextIndex(1, 0, Int.MIN_VALUE))
        for (previous in 0..4) {
            val seen = (-10..10).map { ScreensaverRules.nextIndex(5, previous, it) }.toSet()
            assertEquals((0..4).filterNot { it == previous }.toSet(), seen)
        }
        listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE).forEach {
            assertTrue(ScreensaverRules.nextIndex(5, -1, it) in 0..4)
            assertTrue(ScreensaverRules.nextIndex(5, 5, it) in 0..4)
        }
        assertThrows(IllegalArgumentException::class.java) { ScreensaverRules.nextIndex(0, 0, 0) }
    }
}
