package br.com.lume.launcher

import br.com.lume.launcher.core.ScreensaverRules
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Transport tests use only JVM fakes, never Android decoding or an actual network. */
class MemoryImageLoaderTest {
    private val address = "https://upload.wikimedia.org/test.jpg"
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 12, 0xFF.toByte(), 0xD9.toByte())

    private class Connection(
        url: URL,
        private val code: Int = 200,
        private val mime: String? = "image/jpeg",
        private val bytes: ByteArray = byteArrayOf(),
        private val declared: Long = bytes.size.toLong(),
        private val location: String? = null,
        private val onRead: (() -> Unit)? = null,
        private val failRead: Boolean = false
    ) : HttpURLConnection(url) {
        var disconnected = false
        var inputOpened = false
        var inputClosed = false
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true }
        override fun getResponseCode() = code
        override fun getContentType() = mime
        override fun getContentLengthLong() = declared
        override fun getHeaderField(name: String?): String? = if (name == "Location") location else null
        override fun getInputStream(): InputStream {
            inputOpened = true
            return object : ByteArrayInputStream(bytes) {
                override fun read(target: ByteArray, offset: Int, length: Int): Int {
                    onRead?.invoke()
                    if (failRead) throw IOException("Transport failure")
                    return super.read(target, offset, length)
                }
                override fun close() { inputClosed = true; super.close() }
            }
        }
    }

    @Test fun validJpegHasBoundedTimeoutsNoCacheAndReleasesStreamAndConnection() {
        val connection = Connection(URL(address), bytes = jpeg)
        val loader = MemoryImageLoader({ connection })
        assertArrayEquals(jpeg, loader.download(address))
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertFalse(connection.allowUserInteraction)
        assertEquals(8000, connection.connectTimeout)
        assertTrue(connection.readTimeout in 1..12000)
        assertEquals("identity", connection.getRequestProperty("Accept-Encoding"))
        assertEquals("image/jpeg", connection.getRequestProperty("Accept"))
        assertNull(connection.getRequestProperty("Cookie"))
        assertNull(connection.getRequestProperty("Authorization"))
        assertTrue(connection.inputClosed); assertTrue(connection.disconnected)
    }

    @Test fun unknownContentLengthStillCountsAndAcceptsRealBoundedJpeg() {
        val connection = Connection(URL(address), bytes = jpeg, declared = -1)
        assertArrayEquals(jpeg, MemoryImageLoader({ connection }).download(address))
        assertTrue(connection.inputClosed); assertTrue(connection.disconnected)
    }

    @Test fun untrustedSchemesHostsCredentialsAndPortsNeverOpenConnection() {
        val loader = MemoryImageLoader({ throw AssertionError("Must not connect") })
        listOf("http://upload.wikimedia.org/test.jpg", "https://evil.test/test.jpg", "https://user@upload.wikimedia.org/test.jpg", "https://upload.wikimedia.org:8443/test.jpg").forEach {
            assertThrows(IOException::class.java) { loader.download(it) }
        }
    }

    @Test fun allowedRedirectIsManualAndBothConnectionsAreClosed() {
        val visited = mutableListOf<Connection>()
        val loader = MemoryImageLoader({ url ->
            Connection(url, code = if (visited.isEmpty()) 302 else 200, bytes = jpeg, location = "/final.jpg").also { visited.add(it) }
        })
        assertArrayEquals(jpeg, loader.download(address))
        assertEquals("https://upload.wikimedia.org/final.jpg", visited.last().url.toString())
        assertEquals(2, visited.size); assertTrue(visited.all { it.disconnected })
    }

    @Test fun redirectsCannotEscapeHttpsAllowlistOrExceedThreeHops() {
        listOf("https://evil.test/a.jpg", "http://upload.wikimedia.org/a.jpg", "https://user@upload.wikimedia.org/a.jpg").forEach { location ->
            var opened = 0
            val connection = Connection(URL(address), code = 302, location = location)
            val loader = MemoryImageLoader({ opened++; connection })
            assertThrows(IOException::class.java) { loader.download(address) }
            assertEquals(1, opened); assertTrue(connection.disconnected)
        }
        val visited = mutableListOf<Connection>()
        val loop = MemoryImageLoader({ url -> Connection(url, code = 307, location = address).also { visited.add(it) } })
        assertThrows(IOException::class.java) { loop.download(address) }
        assertEquals(4, visited.size); assertTrue(visited.all { it.disconnected })
    }

    @Test fun missingRedirectStatusMimeAndAdvertisedOversizeAreRejectedBeforeReading() {
        listOf(
            Connection(URL(address), code = 302), Connection(URL(address), code = 404),
            Connection(URL(address), mime = "image/png"), Connection(URL(address), mime = "image/webp"),
            Connection(URL(address), mime = null), Connection(URL(address), declared = 0),
            Connection(URL(address), declared = ScreensaverRules.MAX_ENCODED_BYTES.toLong() + 1)
        ).forEach { connection ->
            assertThrows(IOException::class.java) { MemoryImageLoader({ connection }).download(address) }
            assertFalse(connection.inputOpened); assertTrue(connection.disconnected)
        }
    }

    @Test fun actualPayloadLimitAppliesWhenLengthIsMissingOrLies() {
        val tooLarge = ByteArray(ScreensaverRules.MAX_ENCODED_BYTES + 1)
        listOf(-1L, 5L).forEach { declared ->
            val connection = Connection(URL(address), bytes = tooLarge, declared = declared)
            assertThrows(IOException::class.java) { MemoryImageLoader({ connection }).download(address) }
            assertTrue(connection.inputClosed); assertTrue(connection.disconnected)
        }
    }

    @Test fun badSignatureTruncationAndReadFailureCloseResources() {
        listOf(
            Connection(URL(address), bytes = byteArrayOf(1, 2, 3), declared = -1),
            Connection(URL(address), bytes = jpeg, declared = jpeg.size.toLong() + 1),
            Connection(URL(address), bytes = jpeg, failRead = true)
        ).forEach { connection ->
            assertThrows(IOException::class.java) { MemoryImageLoader({ connection }).download(address) }
            assertTrue(connection.inputClosed); assertTrue(connection.disconnected)
        }
    }

    @Test fun cancelBeforeAndDuringReadPreventsPublicationAndDisconnects() {
        val canceled = MemoryImageLoader({ throw AssertionError("Must not connect") })
        canceled.cancel(); canceled.cancel()
        assertThrows(InterruptedIOException::class.java) { canceled.download(address) }
        lateinit var active: MemoryImageLoader
        lateinit var connection: Connection
        connection = Connection(URL(address), bytes = jpeg, onRead = {
            active.cancel()
            assertFalse(connection.disconnected) // cancel itself is flag-only; cleanup belongs to worker finally.
        })
        active = MemoryImageLoader({ connection })
        assertThrows(InterruptedIOException::class.java) { active.download(address) }
        assertTrue(connection.inputClosed); assertTrue(connection.disconnected)
    }

    @Test fun httpRateLimitIsExposedWithoutLeakingRequestUrls() {
        val connection = Connection(URL(address), code = 429)
        val failure = assertThrows(MemoryImageLoader.HttpFailure::class.java) { MemoryImageLoader({ connection }).download(address) }
        assertEquals(429, failure.statusCode)
        assertEquals("HTTP 429", failure.message)
        assertFalse(connection.inputOpened); assertTrue(connection.disconnected)
    }

    @Test fun deadlineAndRemainingBudgetBoundNewBlockingOperations() {
        var calls = 0
        val expired = MemoryImageLoader({ throw AssertionError("Must not connect") }, { TimeUnit.SECONDS.toNanos((calls++ * 31).toLong()) })
        assertThrows(InterruptedIOException::class.java) { expired.download(address) }
        calls = 0
        val connection = Connection(URL(address), bytes = jpeg)
        val remaining = MemoryImageLoader({ connection }, { if (calls++ == 0) 0L else TimeUnit.SECONDS.toNanos(25) })
        assertArrayEquals(jpeg, remaining.download(address))
        assertEquals(5000, connection.connectTimeout); assertEquals(5000, connection.readTimeout)
    }
}
