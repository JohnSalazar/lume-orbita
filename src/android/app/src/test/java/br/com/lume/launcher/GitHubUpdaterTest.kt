package br.com.lume.launcher

import br.com.lume.launcher.core.UpdateRules
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assume
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.nio.file.Files
import java.nio.file.FileSystemException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

/** No network, Android PackageManager or installer: archive identities here are explicit fakes. */
class GitHubUpdaterTest {
    @get:Rule val temporary = TemporaryFolder()
    private val publicIp = InetAddress.getByAddress(byteArrayOf(8, 8, 8, 8))
    private val cert = "a".repeat(64)
    private val current = UpdateApkIdentity("br.com.lume.launcher", "0.4.3", 29, 30, setOf(cert))
    private val candidate = current.copy(version = "0.4.4", code = 30)
    private val payload = ByteArray(40_001) { (it % 251).toByte() }
    private val release = UpdateRelease("0.4.4", "v0.4.4", "https://github.com/JohnSalazar/lume-orbita/releases/tag/v0.4.4",
        "https://github.com/JohnSalazar/lume-orbita/releases/download/v0.4.4/lume-orbita-0.4.4.apk",
        "https://github.com/JohnSalazar/lume-orbita/releases/download/v0.4.4/SHA256SUMS.txt", payload.size.toLong(), true)
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun sums(bytes: ByteArray = payload) = "${sha(bytes)}  lume-orbita-0.4.4.apk\n".toByteArray()

    private class Connection(url: URL, private val bytes: ByteArray = byteArrayOf(1, 2, 3),
        private val status: Int = 200, private val declared: Long = bytes.size.toLong(),
        private val headers: Map<String, String> = emptyMap(), private val onRead: () -> Unit = {},
        private val readFailure: Boolean = false) : HttpURLConnection(url) {
        @Volatile var disconnected = false
        var opened = false
        var closed = false
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentLengthLong() = declared
        override fun getHeaderField(name: String?) = name?.let(headers::get)
        override fun getInputStream(): InputStream {
            opened = true
            return object : ByteArrayInputStream(bytes) {
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    onRead()
                    if (readFailure) throw IOException("synthetic transport failure")
                    return super.read(buffer, offset, length)
                }
                override fun close() { closed = true; super.close() }
            }
        }
    }

    private fun transport(connect: (URL) -> HttpURLConnection, resolve: (String) -> List<InetAddress> = { listOf(publicIp) },
        now: () -> Long = System::nanoTime) = UpdateHttpTransport(connect, resolve, now)

    private fun client(directory: File, archive: () -> UpdateApkIdentity? = { candidate },
        checksum: ByteArray = sums(), bytes: ByteArray = payload, transfer: UpdateHttpTransport? = null,
        openOutput: (File) -> OutputStream = { it.outputStream() }) =
        GitHubUpdater(directory, { current }, { archive() }, 30, transfer ?: transport({ url ->
            when (url.toString()) {
                release.checksumsUrl -> Connection(url, checksum)
                release.apkUrl -> Connection(url, bytes)
                else -> throw AssertionError("Unexpected URL")
            }
        }), openOutput)

    private fun failure(reason: UpdateFailureReason, action: () -> Unit) {
        assertEquals(reason, assertThrows(UpdateFailure::class.java) { action() }.reason)
    }

    @Test fun boundedTransportClosesResourcesAndDoesNotSendCredentialsOrUseCaches() {
        val connection = Connection(URL(GitHubUpdater.LIST_URL))
        assertArrayEquals(byteArrayOf(1, 2, 3), transport({ connection }).bytes(GitHubUpdater.LIST_URL, 16))
        assertTrue(connection.closed && connection.disconnected)
        assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.useCaches)
        assertFalse(connection.allowUserInteraction)
        assertEquals("identity", connection.getRequestProperty("Accept-Encoding"))
        assertNull(connection.getRequestProperty("Authorization"))
        assertNull(connection.getRequestProperty("Cookie"))
        assertTrue(connection.connectTimeout in 1..1000)
        assertTrue(connection.readTimeout in 1..10_000)
    }

    @Test fun httpFailuresAreClassifiedWithoutOpeningTheBody() {
        for ((status, headers, reason) in listOf(
            Triple(429, emptyMap(), UpdateFailureReason.RATE_LIMIT),
            Triple(403, mapOf("X-RateLimit-Remaining" to "0"), UpdateFailureReason.RATE_LIMIT),
            Triple(403, emptyMap(), UpdateFailureReason.NETWORK),
            Triple(404, emptyMap(), UpdateFailureReason.NETWORK),
            Triple(500, emptyMap(), UpdateFailureReason.NETWORK))) {
            val connection = Connection(URL(GitHubUpdater.LIST_URL), status = status, headers = headers)
            failure(reason) { transport({ connection }).bytes(GitHubUpdater.LIST_URL, 16) }
            assertFalse(connection.opened); assertTrue(connection.disconnected)
        }
    }

    @Test fun streamLimitsRejectLyingLengthAndOversizeWithoutTrustingHeaders() {
        for (declared in listOf(-1L, 2L)) {
            val connection = Connection(URL(GitHubUpdater.LIST_URL), ByteArray(9), declared = declared)
            failure(UpdateFailureReason.INVALID_RELEASE) { transport({ connection }).bytes(GitHubUpdater.LIST_URL, 8) }
            assertTrue(connection.closed && connection.disconnected)
        }
        for (declared in listOf(0L, 9L)) {
            val connection = Connection(URL(GitHubUpdater.LIST_URL), declared = declared)
            failure(UpdateFailureReason.INVALID_RELEASE) { transport({ connection }).bytes(GitHubUpdater.LIST_URL, 8) }
            assertFalse(connection.opened)
        }
        val truncated = Connection(URL(GitHubUpdater.LIST_URL), declared = 4)
        failure(UpdateFailureReason.NETWORK) { transport({ truncated }).bytes(GitHubUpdater.LIST_URL, 8) }
        val broken = Connection(URL(GitHubUpdater.LIST_URL), readFailure = true)
        failure(UpdateFailureReason.NETWORK) { transport({ broken }).bytes(GitHubUpdater.LIST_URL, 8) }
        assertTrue(broken.closed && broken.disconnected)
    }

    @Test fun privateOrMixedDnsNeverOpensAConnection() {
        val privateIp = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))
        for (addresses in listOf(emptyList(), listOf(privateIp), listOf(publicIp, privateIp))) {
            failure(UpdateFailureReason.NETWORK) {
                transport({ throw AssertionError("No connection may open") }, { addresses }).bytes(GitHubUpdater.LIST_URL, 8)
            }
        }
    }

    @Test fun redirectsAreManualRevalidatedAndDnsCheckedAgain() {
        val destination = "https://release-assets.githubusercontent.com/fixture/asset?sig=synthetic"
        val first = Connection(URL(release.apkUrl), status = 302, headers = mapOf("Location" to destination))
        val second = Connection(URL(destination))
        val hosts = mutableListOf<String>()
        val transfer = transport({ if (it.host == "github.com") first else second }, { hosts.add(it); listOf(publicIp) })
        assertArrayEquals(byteArrayOf(1, 2, 3), transfer.bytes(release.apkUrl, 8))
        assertEquals(listOf("github.com", "release-assets.githubusercontent.com"), hosts)
        assertTrue(first.disconnected && second.disconnected)
        for (bad in listOf("http://github.com/x", "https://evil.test/a", "https://user@objects.githubusercontent.com/a")) {
            val redirect = Connection(URL(release.apkUrl), status = 302, headers = mapOf("Location" to bad))
            failure(UpdateFailureReason.INVALID_RELEASE) { transport({ redirect }).bytes(release.apkUrl, 8) }
        }
        val apiRedirect = Connection(URL(GitHubUpdater.LIST_URL), status = 302, headers = mapOf("Location" to destination))
        failure(UpdateFailureReason.INVALID_RELEASE) { transport({ apiRedirect }).bytes(GitHubUpdater.LIST_URL, 8) }
        var opened = 0
        failure(UpdateFailureReason.NETWORK) {
            transport({ opened++; Connection(it, status = 302, headers = mapOf("Location" to destination)) }).bytes(release.apkUrl, 8)
        }
        assertEquals(4, opened)
    }

    @Test fun deadlineIsMonotonicAndCancellationBeforeConnectIsTerminal() {
        var time = 0L
        val delayed = Connection(URL(GitHubUpdater.LIST_URL), onRead = { time = TimeUnit.SECONDS.toNanos(60) })
        failure(UpdateFailureReason.NETWORK) { transport({ delayed }, now = { time }).bytes(GitHubUpdater.LIST_URL, 8) }
        assertTrue(delayed.closed && delayed.disconnected)
        val canceled = transport({ throw AssertionError("Canceled transport must not connect") })
        canceled.cancel(); canceled.cancel()
        repeat(2) { failure(UpdateFailureReason.CANCELED) { canceled.bytes(GitHubUpdater.LIST_URL, 8) } }
    }

    @Test fun redirectToPrivateDnsIsRejectedBeforeTheSecondConnection() {
        val destination = "https://objects.githubusercontent.com/fixture/asset"
        val first = Connection(URL(release.apkUrl), status = 302, headers = mapOf("Location" to destination))
        var opened = 0
        val transfer = transport({ opened++; first }, { host ->
            if (host == "github.com") listOf(publicIp) else listOf(InetAddress.getByAddress(byteArrayOf(10, 0, 0, 1)))
        })
        failure(UpdateFailureReason.NETWORK) { transfer.bytes(release.apkUrl, 8) }
        assertEquals(1, opened); assertTrue(first.disconnected)
    }

    @Test fun validDownloadStreamsToOwnedFileAndRevalidatesBeforeDiscard() {
        val directory = temporary.newFolder()
        val updater = client(directory)
        val progress = mutableListOf<Int>()
        val result = updater.download(release) { progress.add(it) }
        assertArrayEquals(payload, result.apk.readBytes())
        assertEquals(directory.canonicalFile, result.apk.canonicalFile.parentFile)
        assertEquals(sha(payload), result.sha256); assertEquals(30L, result.versionCode)
        assertEquals(0, progress.first()); assertEquals(100, progress.last())
        assertTrue(progress.zipWithNext().all { (before, after) -> after >= before })
        assertEquals(candidate, updater.validatePrepared(result))
        updater.discard(result); assertFalse(result.apk.exists())
    }

    @Test fun checksumMismatchSizeMismatchAndInvalidArchiveRemoveOnlyOwnPartial() {
        for (scenario in listOf("hash", "size", "archive")) {
            val directory = temporary.newFolder()
            val sentinel = File(directory, "unrelated.txt").apply { writeText("keep") }
            val updater = client(directory, archive = { if (scenario == "archive") null else candidate },
                checksum = if (scenario == "hash") sums(byteArrayOf(9)) else sums())
            failure(UpdateFailureReason.INVALID_APK) { updater.download(if (scenario == "size") release.copy(apkBytes = payload.size + 1L) else release) }
            assertEquals(listOf("unrelated.txt"), directory.list()!!.sorted())
            assertEquals("keep", sentinel.readText())
        }
    }

    @Test fun metadataVersionDoesNotOverrideArchiveVersionCodeSignerPackageOrSdk() {
        for (identity in listOf(current.copy(version = release.version), candidate.copy(version = "0.4.5"),
            candidate.copy(packageName = "other.package"), candidate.copy(minSdk = 31),
            candidate.copy(certificates = setOf("b".repeat(64))), candidate.copy(certificates = emptySet()))) {
            val directory = temporary.newFolder()
            failure(UpdateFailureReason.INVALID_APK) { client(directory, archive = { identity }).download(release) }
            assertTrue(directory.list()!!.isEmpty())
        }
    }

    @Test fun untrustedReleaseOrMissingChecksumFailsBeforeCreatingApk() {
        val directory = temporary.newFolder()
        val updater = client(directory, checksum = "not a checksum\n".toByteArray())
        failure(UpdateFailureReason.INVALID_RELEASE) { updater.download(release) }
        assertTrue(directory.list()!!.isEmpty())
        for (invalid in listOf(release.copy(tag = "v0.4.5"), release.copy(apkUrl = "https://evil.test/a"),
            release.copy(apkBytes = UpdateRules.MAX_APK_BYTES + 1), release.copy(apkBytes = 0))) {
            failure(UpdateFailureReason.INVALID_RELEASE) { updater.download(invalid) }
        }
    }

    @Test fun canceledDownloadNeverPublishesAndCleansItsPartial() {
        val directory = temporary.newFolder()
        val updater = client(directory)
        failure(UpdateFailureReason.CANCELED) { updater.download(release) { if (it > 0) updater.cancel() } }
        assertTrue(directory.list()!!.isEmpty())
    }

    @Test fun unwritableDestinationIsStorageFailureWithoutReplacingExistingFile() {
        val destination = temporary.newFile().apply { writeText("sentinel") }
        failure(UpdateFailureReason.STORAGE) { client(destination).download(release) }
        assertEquals("sentinel", destination.readText())
    }

    @Test fun failedSecondWriteIsStorageFailureAndRemovesThePartialDownload() {
        assertPartialWriteFailure(cancelDuringFailure = false)
    }

    @Test fun concurrentCancellationTakesPrecedenceOverTheSecondWriteFailure() {
        assertPartialWriteFailure(cancelDuringFailure = true)
    }

    private fun assertPartialWriteFailure(cancelDuringFailure: Boolean) {
        val directory = temporary.newFolder()
        val sentinel = File(directory, "unrelated.txt").apply { writeText("keep") }
        val apk = Connection(URL(release.apkUrl), payload)
        val enteredWrite = CountDownLatch(1)
        val canceled = CountDownLatch(1)
        val threadFailure = AtomicReference<Throwable?>()
        var writes = 0; var closed = false; var inspected = false
        val updater = client(directory, archive = { inspected = true; candidate }, transfer = transport({ url ->
            if (url.toString() == release.checksumsUrl) Connection(url, sums()) else apk
        }), openOutput = { file ->
            object : OutputStream() {
                private val actual = file.outputStream()
                override fun write(value: Int) { actual.write(value) }
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    writes++
                    if (writes == 2) {
                        assertTrue("Failure must happen after a real partial write", file.length() > 0)
                        if (cancelDuringFailure) {
                            enteredWrite.countDown()
                            assertTrue("Cancellation must complete before the write fails", canceled.await(2, TimeUnit.SECONDS))
                        }
                        throw IOException("synthetic disk write failure")
                    }
                    actual.write(buffer, offset, length)
                }
                override fun close() { closed = true; actual.close() }
            }
        })
        val canceller = if (cancelDuringFailure) Thread {
            try {
                check(enteredWrite.await(2, TimeUnit.SECONDS))
                updater.cancel()
            } catch (failure: Throwable) { threadFailure.set(failure) }
            finally { canceled.countDown() }
        }.apply { isDaemon = true; start() } else null
        try {
            failure(if (cancelDuringFailure) UpdateFailureReason.CANCELED else UpdateFailureReason.STORAGE) { updater.download(release) }
        } finally {
            canceller?.join(3_000)
            if (canceller?.isAlive == true) { canceller.interrupt(); canceller.join(1_000) }
        }
        assertNull(threadFailure.get())
        assertEquals(2, writes); assertTrue(closed)
        assertTrue(apk.closed && apk.disconnected); assertFalse(inspected)
        assertEquals(listOf("unrelated.txt"), directory.list()!!.sorted())
        assertEquals("keep", sentinel.readText())
    }

    @Test fun failedNetworkReadAfterPartialWriteStaysNetworkFailure() {
        val directory = temporary.newFolder()
        val sentinel = File(directory, "unrelated.txt").apply { writeText("keep") }
        var reads = 0; var inspected = false
        val apk = Connection(URL(release.apkUrl), payload, onRead = {
            reads++
            if (reads == 2) throw IOException("synthetic network read failure")
        })
        val updater = client(directory, archive = { inspected = true; candidate }, transfer = transport({ url ->
            if (url.toString() == release.checksumsUrl) Connection(url, sums()) else apk
        }))
        failure(UpdateFailureReason.NETWORK) { updater.download(release) }
        assertEquals(2, reads); assertTrue(apk.closed && apk.disconnected); assertFalse(inspected)
        assertEquals(listOf("unrelated.txt"), directory.list()!!.sorted())
        assertEquals("keep", sentinel.readText())
    }

    @Test fun preparedFileMutationOrWrongOwnershipCannotReachInstallerValidation() {
        val directory = temporary.newFolder()
        val updater = client(directory)
        val result = updater.download(release)
        result.apk.writeBytes(payload.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
        failure(UpdateFailureReason.INVALID_APK) { updater.validatePrepared(result) }
        val outside = temporary.newFile("lume-update-outside.apk").apply { writeBytes(payload) }
        failure(UpdateFailureReason.INVALID_APK) { updater.validatePrepared(result.copy(apk = outside)) }
        updater.discard(result.copy(apk = outside)); assertTrue(outside.exists())
        updater.discard(result)
    }

    @Test fun cleanupRemovesAllUnleasedOwnedApksWithoutRecursingOrTouchingOtherFiles() {
        val directory = temporary.newFolder()
        val old = File(directory, "lume-update-old.apk").apply { writeText("old"); check(setLastModified(1)) }
        val recent = File(directory, "lume-update-recent.apk").apply { writeText("recent") }
        val keep = File(directory, "base.apk").apply { writeText("installed sentinel") }
        val other = File(directory, "lume-update-old.apk.backup").apply { writeText("keep") }
        val folder = File(directory, "lume-update-directory.apk").apply { check(mkdir()) }
        val nested = File(folder, "lume-update-nested.apk").apply { writeText("keep") }
        val outside = temporary.newFile("lume-update-outside.apk").apply { writeText("keep") }
        assertEquals(2, GitHubUpdater.cleanupOwnedDownloads(directory))
        assertFalse(old.exists()); assertFalse(recent.exists())
        assertEquals("installed sentinel", keep.readText()); assertEquals("keep", other.readText())
        assertEquals("keep", nested.readText()); assertEquals("keep", outside.readText())
        assertEquals(0, GitHubUpdater.cleanupOwnedDownloads(directory))
    }

    @Test fun cleanupProtectsAnActiveDownloadAndItsPreparedAndStagingOwnership() {
        val directory = temporary.newFolder()
        val old = File(directory, "lume-update-abandoned.apk").apply { writeText("old") }
        val started = CountDownLatch(1); val resume = CountDownLatch(1)
        val outcome = AtomicReference<PreparedUpdate?>(); val error = AtomicReference<Throwable?>()
        val updater = client(directory, openOutput = { file ->
            object : OutputStream() {
                private val actual = file.outputStream()
                private var first = true
                override fun write(value: Int) { actual.write(value) }
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    if (first) {
                        first = false; started.countDown()
                        check(resume.await(3, TimeUnit.SECONDS))
                    }
                    actual.write(buffer, offset, length)
                }
                override fun close() { actual.close() }
            }
        })
        val thread = Thread {
            try { outcome.set(updater.download(release)) } catch (failure: Throwable) { error.set(failure) }
        }.apply { isDaemon = true; start() }
        try {
            assertTrue(started.await(3, TimeUnit.SECONDS))
            assertEquals(1, GitHubUpdater.cleanupOwnedDownloads(directory))
            assertFalse(old.exists())
            assertEquals(1, directory.listFiles()!!.size)
        } finally {
            resume.countDown(); thread.join(4_000)
            if (thread.isAlive) { thread.interrupt(); thread.join(1_000) }
        }
        assertFalse(thread.isAlive); assertNull(error.get())
        val prepared = requireNotNull(outcome.get())
        try {
            assertTrue(prepared.apk.setLastModified(1))
            assertEquals(0, GitHubUpdater.cleanupOwnedDownloads(directory, System.currentTimeMillis()))
            assertEquals(candidate, client(directory).validatePrepared(prepared))
            assertEquals(0, GitHubUpdater.cleanupOwnedDownloads(directory))
            assertArrayEquals(payload, prepared.apk.readBytes())
        } finally { updater.discard(prepared) }
        assertFalse(prepared.apk.exists())
    }

    @Test fun cleanupErrorsAreBestEffortAndOldFailuresCanBeRetried() {
        val directory = temporary.newFolder()
        val stuck = File(directory, "lume-update-stuck.apk").apply { writeText("fixture") }
        val removable = File(directory, "lume-update-removable.apk").apply { writeText("fixture") }
        assertEquals(1, GitHubUpdater.cleanupOwnedDownloads(directory, deleteFile = { file ->
            if (file.name == stuck.name) throw IOException("synthetic deletion failure") else file.delete()
        }))
        assertTrue(stuck.exists()); assertFalse(removable.exists())
        assertEquals(1, GitHubUpdater.cleanupOwnedDownloads(directory))
        val notDirectory = temporary.newFile().apply { writeText("keep") }
        assertEquals(0, GitHubUpdater.cleanupOwnedDownloads(notDirectory))
        assertEquals("keep", notDirectory.readText())
    }

    @Test fun cleanupNeverFollowsAnApkSymlinkOrAnUpdatesDirectorySymlink() {
        val directory = temporary.newFolder()
        val outside = temporary.newFile("lume-update-external-target.apk").apply { writeText("keep") }
        val fileLink = File(directory, "lume-update-link.apk").toPath()
        val directoryLink = File(temporary.root, "linked-updates").toPath()
        try {
            try {
                Files.createSymbolicLink(fileLink, outside.toPath())
                Files.createSymbolicLink(directoryLink, directory.toPath())
            } catch (failure: UnsupportedOperationException) {
                Assume.assumeNoException("Filesystem does not support symbolic links", failure)
            } catch (failure: FileSystemException) {
                if (System.getProperty("os.name", "").startsWith("Windows", ignoreCase = true) &&
                    failure.reason.orEmpty().contains("privil", ignoreCase = true))
                    Assume.assumeNoException("Windows symbolic-link privilege unavailable", failure)
                else throw failure
            }
            val regular = File(directory, "lume-update-owned.apk").apply { writeText("owned") }
            assertEquals(1, GitHubUpdater.cleanupOwnedDownloads(directory))
            assertFalse(regular.exists()); assertTrue(Files.isSymbolicLink(fileLink)); assertEquals("keep", outside.readText())
            val retained = File(directory, "lume-update-retained.apk").apply { writeText("keep") }
            assertEquals(0, GitHubUpdater.cleanupOwnedDownloads(directoryLink.toFile()))
            assertEquals("keep", retained.readText()); assertEquals("keep", outside.readText())
        } finally {
            Files.deleteIfExists(fileLink); Files.deleteIfExists(directoryLink)
        }
    }
}
