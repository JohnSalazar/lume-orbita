package br.com.lume.launcher

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.TimeUnit

/** JVM transport/policy tests. BitmapFactory/device decoding is deliberately not simulated. */
class ProgramArtworkLoaderTest {
    private val publisher = "com.globo.globotv"
    private val address = "https://s.glbimg.com/art.jpg"
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xff.toByte(), 0xd9.toByte())
    private val publicIp = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))

    private class Connection(
        url: URL, private val bytes: ByteArray, private val mime: String? = "image/jpeg",
        private val code: Int = 200, private val declared: Long = bytes.size.toLong(),
        private val location: String? = null, private val beforeRead: (() -> Unit)? = null
    ) : HttpURLConnection(url) {
        var disconnected = false
        var closed = false
        var opened = false
        override fun connect() {}
        override fun usingProxy() = false
        override fun disconnect() { disconnected = true }
        override fun getResponseCode() = code
        override fun getContentType() = mime
        override fun getContentLengthLong() = declared
        override fun getHeaderField(name: String?): String? = if (name == "Location") location else null
        override fun getInputStream(): InputStream {
            opened = true
            return object : ByteArrayInputStream(bytes) {
                override fun read(target: ByteArray, offset: Int, length: Int): Int {
                    beforeRead?.invoke()
                    return super.read(target, offset, length)
                }
                override fun close() { closed = true; super.close() }
            }
        }
    }

    private fun newLoader(factory: (URL) -> HttpURLConnection, clock: () -> Long = System::nanoTime) = ProgramArtworkLoader(factory, { listOf(publicIp) }, clock)

    @Test fun acceptsPublisherCdnAndReleasesStreamWithoutCachingOrCredentials() {
        val connection = Connection(URL(address), jpeg)
        assertArrayEquals(jpeg, newLoader({ connection }).download(address, publisher))
        assertFalse(connection.useCaches); assertFalse(connection.instanceFollowRedirects); assertFalse(connection.allowUserInteraction)
        assertEquals(1000, connection.connectTimeout); assertTrue(connection.readTimeout in 1..7000)
        assertEquals("identity", connection.getRequestProperty("Accept-Encoding"))
        assertNull(connection.getRequestProperty("Cookie")); assertNull(connection.getRequestProperty("Authorization"))
        assertTrue(connection.closed); assertTrue(connection.disconnected)
    }

    @Test fun rejectsUnsupportedOriginsBeforeDnsOrConnection() {
        val loader = ProgramArtworkLoader({ throw AssertionError("connection") }, { throw AssertionError("DNS") })
        listOf("content://s.glbimg.com/a", "file:///a", "http://s.glbimg.com/a", "https://s.glbimg.com:443/a", "https://user@s.glbimg.com/a", "https://s.glbimg.com/a#x", "https://s.glbimg.com.evil.test/a", "https://evilglbimg.com/a", "https://127.0.0.1/a", "https://[::1]/a").forEach {
            assertNull(loader.download(it, publisher))
        }
        assertNull(loader.download(address, "unknown.app"))
        assertNull(loader.download(address, "com.netflix.ninja"))
        assertNull(ArtworkPolicy.url("https://s.glbimg.com/" + "a".repeat(4096), publisher))
    }

    @Test fun publisherBoundariesDoNotPermitOtherPlatforms() {
        listOf("com.disney.disneyplus" to "prod-ripcut-delivery.disney-plus.net", "com.netflix.ninja" to "occ.nflximg.net", "com.amazon.amazonvideo.livingroom" to "m.media-amazon.com", "com.wbd.stream" to "images.hbomaxcdn.com", "com.apple.atve.androidtv.appletv" to "is1-ssl.mzstatic.com", "com.google.android.youtube.tv" to "i.ytimg.com", "br.com.sbt.mais" to "sbt.com.br").forEach { (pkg, host) ->
            assertNotNull(ArtworkPolicy.url("https://$host/a", pkg))
            assertNull(ArtworkPolicy.url("https://$host.attacker.test/a", pkg))
            assertNull(ArtworkPolicy.url("https://s.glbimg.com/a", pkg))
        }
    }

    @Test fun observedDisneyAndMaxHostsAreExactAndBoundToTheirPublisher() {
        val observed = listOf("com.wbd.stream" to "images.cdn.prd.api.discomax.com", "com.disney.disneyplus" to "disney.images.edge.bamgrid.com")
        observed.forEach { (pkg, host) ->
            assertNotNull(ArtworkPolicy.url("https://$host/a", pkg))
            assertNull(ArtworkPolicy.url("https://extra.$host/a", pkg))
            assertNull(ArtworkPolicy.url("https://$host.attacker.test/a", pkg))
            observed.filter { it.first != pkg }.forEach { (other, _) -> assertNull(ArtworkPolicy.url("https://$host/a", other)) }
            assertNull(ArtworkPolicy.url("https://$host/a", publisher))
        }
        assertNull(ArtworkPolicy.url("https://discomax.com/a", "com.wbd.stream"))
        assertNull(ArtworkPolicy.url("https://bamgrid.com/a", "com.disney.disneyplus"))
    }

    @Test fun allResolvedAddressesMustBePublicAndEmptyDnsCannotConnect() {
        val private = InetAddress.getByAddress(byteArrayOf(192.toByte(), 168.toByte(), 1, 22))
        listOf(emptyList(), listOf(publicIp, private)).forEach { ips ->
            assertNull(ProgramArtworkLoader({ throw AssertionError("connection") }, { ips }).download(address, publisher))
        }
        listOf("0.0.0.0", "10.1.2.3", "127.0.0.1", "169.254.1.1", "172.31.0.1", "192.168.0.1", "100.64.0.1", "224.0.0.1", "198.18.0.1", "192.0.2.1", "198.51.100.1", "203.0.113.1", "::1", "fc00::1", "fe80::1", "ff02::1", "2001:db8::1").forEach {
            assertFalse(it, ArtworkPolicy.publicAddress(InetAddress.getByName(it)))
        }
        assertTrue(ArtworkPolicy.publicAddress(publicIp))
        assertTrue(ArtworkPolicy.publicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }

    @Test fun redirectsAreManualRevalidatedAndBounded() {
        val connections = mutableListOf<Connection>()
        val loader = newLoader({ url -> Connection(url, jpeg, code = if (connections.isEmpty()) 302 else 200, location = "/next.jpg").also { connections.add(it) } })
        assertArrayEquals(jpeg, loader.download(address, publisher)); assertEquals(2, connections.size)
        assertTrue(connections.all { it.disconnected })
        var opened = 0
        val denied = newLoader({ url -> opened++; Connection(url, jpeg, code = 302, location = "https://evil.test/a") })
        assertNull(denied.download(address, publisher)); assertEquals(1, opened)
        opened = 0
        val looping = newLoader({ url -> opened++; Connection(url, jpeg, code = 307, location = "/again") })
        assertThrows(IOException::class.java) { looping.download(address, publisher) }; assertEquals(4, opened)
    }

    @Test fun redirectDnsIsRecheckedAndFailureDisconnectsPreviousConnection() {
        val connection = Connection(URL(address), jpeg, code = 302, location = "https://other.glbimg.com/a")
        var dns = 0
        val loader = ProgramArtworkLoader({ connection }, { if (dns++ == 0) listOf(publicIp) else emptyList() })
        assertNull(loader.download(address, publisher)); assertEquals(2, dns); assertTrue(connection.disconnected)
    }

    @Test fun countsUnknownLengthAndRejectsOversizedOrLyingBodies() {
        assertArrayEquals(jpeg, newLoader({ Connection(it, jpeg, declared = -1) }).download(address, publisher))
        listOf(0L, (ArtworkPolicy.MAX_BYTES + 1).toLong(), 2L, 100L).forEach { length ->
            val connection = Connection(URL(address), jpeg, declared = length)
            assertThrows(IOException::class.java) { newLoader({ connection }).download(address, publisher) }
            assertTrue(connection.disconnected)
        }
        val connection = Connection(URL(address), ByteArray(ArtworkPolicy.MAX_BYTES + 1), declared = -1)
        assertThrows(IOException::class.java) { newLoader({ connection }).download(address, publisher) }
        assertTrue(connection.closed); assertTrue(connection.disconnected)
    }

    @Test fun rejectsMimeMismatchHttpErrorsAndMalformedPayload() {
        listOf(Connection(URL(address), jpeg, mime = "text/html"), Connection(URL(address), jpeg, mime = "image/png"), Connection(URL(address), jpeg, code = 401), Connection(URL(address), byteArrayOf(1, 2, 3))).forEach { connection ->
            assertThrows(IOException::class.java) { newLoader({ connection }).download(address, publisher) }
            assertTrue(connection.disconnected)
        }
        assertEquals("image/jpeg", ArtworkPolicy.mime("IMAGE/JPEG; charset=binary"))
        assertNull(ArtworkPolicy.mime(null)); assertNull(ArtworkPolicy.mime("image/gif"))
    }

    @Test fun cancelNeverDisconnectsOnCallingThreadAndStopsBeforePublication() {
        lateinit var loader: ProgramArtworkLoader
        lateinit var connection: Connection
        connection = Connection(URL(address), jpeg, beforeRead = {
            loader.cancel()
            assertFalse("cancel must not do socket I/O", connection.disconnected)
        })
        loader = newLoader({ connection })
        assertThrows(InterruptedIOException::class.java) { loader.download(address, publisher) }
        assertTrue(connection.closed); assertTrue(connection.disconnected)
        assertThrows(InterruptedIOException::class.java) { loader.download(address, publisher) }
        val never = newLoader({ throw AssertionError("connection") }); never.cancel()
        assertThrows(InterruptedIOException::class.java) { never.download(address, publisher) }
    }

    @Test fun cooperativeDeadlineRejectsSlowDnsAndReadAndAlwaysCleansUp() {
        var time = 0L
        val connection = Connection(URL(address), jpeg, beforeRead = { time = TimeUnit.SECONDS.toNanos(12) })
        assertThrows(InterruptedIOException::class.java) { newLoader({ connection }, { time }).download(address, publisher) }
        assertTrue(connection.closed); assertTrue(connection.disconnected)
        time = 0
        val slowDns = ProgramArtworkLoader({ throw AssertionError("connection") }, { time = TimeUnit.SECONDS.toNanos(13); listOf(publicIp) }, { time })
        assertThrows(InterruptedIOException::class.java) { slowDns.download(address, publisher) }
    }

    private fun chunk(tag: String, payload: ByteArray, little: Boolean): ByteArray {
        val length = ByteArray(4) { (payload.size ushr (8 * if (little) it else 3 - it)).toByte() }
        return if (little) tag.toByteArray() + length + payload + ByteArray(payload.size % 2) else length + tag.toByteArray() + payload + ByteArray(4)
    }
    private fun png(vararg chunks: ByteArray) = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10) + chunks.fold(byteArrayOf()) { all, item -> all + item }
    private fun webp(vararg chunks: ByteArray): ByteArray {
        val body = "WEBP".toByteArray() + chunks.fold(byteArrayOf()) { all, item -> all + item }
        return "RIFF".toByteArray() + ByteArray(4) { (body.size ushr (8 * it)).toByte() } + body
    }

    @Test fun staticPngWebpAcceptedButAnimationAndTruncatedChunksRejected() {
        val data = chunk("IDAT", byteArrayOf(1), false); val end = chunk("IEND", byteArrayOf(), false)
        assertEquals("image/png", ArtworkPolicy.format(png(data, end)))
        listOf("acTL", "fcTL", "fdAT").forEach { assertNull(ArtworkPolicy.format(png(chunk(it, byteArrayOf(1), false), data, end))) }
        assertNull(ArtworkPolicy.format(png(data, end).dropLast(1).toByteArray()))
        assertNull(ArtworkPolicy.format(png(end)))
        val image = chunk("VP8 ", byteArrayOf(1), true)
        assertEquals("image/webp", ArtworkPolicy.format(webp(image)))
        assertEquals("image/webp", ArtworkPolicy.format(webp(chunk("VP8L", byteArrayOf(1), true))))
        listOf("ANIM", "ANMF").forEach { assertNull(ArtworkPolicy.format(webp(chunk(it, byteArrayOf(0), true), image))) }
        assertNull(ArtworkPolicy.format(webp(chunk("VP8X", byteArrayOf(2) + ByteArray(9), true), image)))
        assertNull(ArtworkPolicy.format(webp(image).dropLast(1).toByteArray()))
        assertNull(ArtworkPolicy.format(byteArrayOf()))
    }

    @Test fun downsamplingRejectsBombsAndAlwaysFitsThumbnailBudget() {
        listOf(0 to 100, -1 to 1, 8193 to 1, Int.MAX_VALUE to Int.MAX_VALUE, 8192 to 8192).forEach { (w, h) -> assertNull(ArtworkPolicy.sampleSize(w, h)) }
        listOf(1 to 1, 480 to 270, 1920 to 1080, 3840 to 2160, 8192 to 1, 1 to 8192, 4000 to 4000).forEach { (w, h) ->
            val sample = ArtworkPolicy.sampleSize(w, h)!!
            val width = (w + sample - 1) / sample; val height = (h + sample - 1) / sample
            assertTrue(width <= 480 && height <= 270); assertTrue(width.toLong() * height * 4 <= 1024 * 1024)
            assertEquals(0, sample and (sample - 1))
        }
        assertEquals(1, ArtworkPolicy.sampleSize(480, 270))
        assertEquals(8, ArtworkPolicy.sampleSize(1920, 1081))
    }

    @Test fun connectBudgetAccountsForManyResolvedAddresses() {
        assertEquals(500, ArtworkPolicy.connectTimeout(12_000, 12))
        assertEquals(315, ArtworkPolicy.connectTimeout(12_000, 19))
        assertEquals(1000, ArtworkPolicy.connectTimeout(12_000, 1))
        assertEquals(1000, ArtworkPolicy.connectTimeout(Int.MAX_VALUE, 1))
    }

    @Test fun connectBudgetNeverExceedsSmallRemainingDeadline() {
        assertEquals(200, ArtworkPolicy.connectTimeout(200, 19))
        assertEquals(1, ArtworkPolicy.connectTimeout(1, Int.MAX_VALUE))
        assertEquals(250, ArtworkPolicy.connectTimeout(1000, Int.MAX_VALUE))
    }

    @Test fun connectBudgetRejectsInvalidRemainingTimeOrAddressCount() {
        listOf(0 to 1, -1 to 1, 1 to 0, 1 to -1).forEach { (time, count) ->
            assertThrows(IllegalArgumentException::class.java) { ArtworkPolicy.connectTimeout(time, count) }
        }
    }
}
