package br.com.lume.launcher

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import br.com.lume.launcher.core.UpdateRules
import org.json.JSONArray
import org.json.JSONException
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

data class UpdateRelease(val version: String, val tag: String, val pageUrl: String, val apkUrl: String,
                         val checksumsUrl: String, val apkBytes: Long, val prerelease: Boolean)
data class PreparedUpdate(val release: UpdateRelease, val apk: File, val versionCode: Long, val sha256: String)
enum class UpdateFailureReason { NETWORK, RATE_LIMIT, INVALID_RELEASE, INVALID_APK, STORAGE, CANCELED }
class UpdateFailure(val reason: UpdateFailureReason) : IOException(reason.name)

internal data class UpdateApkIdentity(val packageName: String, val version: String, val code: Long,
                                      val minSdk: Int, val certificates: Set<String>)

/** Manual, synchronous worker API. No executor, periodic checks, credentials or installation here. */
class GitHubUpdater internal constructor(
    private val directory: File,
    private val installed: () -> UpdateApkIdentity,
    private val inspect: (File) -> UpdateApkIdentity?,
    private val deviceSdk: Int,
    internal val transport: UpdateHttpTransport,
    private val openOutput: (File) -> OutputStream = { it.outputStream() }
) {
    private constructor(environment: Environment) : this(environment.directory, environment.installed,
        environment.inspect, Build.VERSION.SDK_INT, UpdateHttpTransport())
    constructor(context: Context) : this(environment(context.applicationContext))

    fun cancel() = transport.cancel()

    fun check(): UpdateRelease? {
        // A killed process can leave an uncommitted download; expire only our old private APKs.
        val expired = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        directory.listFiles()?.filter { it.name.matches(Regex("lume-update-[A-Za-z0-9-]+\\.apk")) && it.lastModified() < expired }
            ?.forEach { runCatching { if (it.canonicalFile.parentFile == directory.canonicalFile) it.delete() } }
        val current = installed()
        if (UpdateRules.compareVersions(current.version, current.version) == null) throw UpdateFailure(UpdateFailureReason.INVALID_APK)
        val bytes = transport.bytes(LIST_URL, UpdateRules.MAX_JSON_BYTES)
        return parseReleases(bytes.toString(Charsets.UTF_8), current.version)
    }

    fun download(release: UpdateRelease, onProgress: (Int) -> Unit = {}): PreparedUpdate {
        validateRelease(release)
        transport.checkCanceled()
        val filename = "lume-orbita-${release.version}.apk"
        val checksumText = transport.bytes(release.checksumsUrl, UpdateRules.MAX_CHECKSUM_BYTES).toString(Charsets.UTF_8)
        val expected = UpdateRules.checksum(checksumText, filename) ?: throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE)
        val file = try {
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException()
            File.createTempFile("lume-update-", ".apk", directory)
        } catch (_: IOException) { throw UpdateFailure(UpdateFailureReason.STORAGE) }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            onProgress(0)
            try {
                openOutput(file).use { output ->
                    transport.read(release.apkUrl, UpdateRules.MAX_APK_BYTES) { buffer, count ->
                        try { output.write(buffer, 0, count) }
                        catch (_: IOException) {
                            transport.checkCanceled()
                            throw UpdateFailure(UpdateFailureReason.STORAGE)
                        }
                        digest.update(buffer, 0, count); total += count
                        onProgress(((total * 100L) / release.apkBytes).coerceIn(0, 100).toInt())
                    }
                }
            } catch (failure: UpdateFailure) { throw failure }
              catch (_: IOException) { throw UpdateFailure(UpdateFailureReason.STORAGE) }
            transport.checkCanceled()
            if (total != release.apkBytes || hex(digest.digest()) != expected) throw UpdateFailure(UpdateFailureReason.INVALID_APK)
            val result = PreparedUpdate(release, file, validateArchive(file, release).code, expected)
            transport.checkCanceled()
            return result
        } catch (failure: Exception) {
            file.delete()
            throw failure
        }
    }

    internal fun validatePrepared(prepared: PreparedUpdate): UpdateApkIdentity {
        validateRelease(prepared.release)
        val file = prepared.apk
        if (file.canonicalFile.parentFile != directory.canonicalFile || !file.name.matches(Regex("lume-update-[A-Za-z0-9-]+\\.apk")) ||
            !file.isFile || file.length() != prepared.release.apkBytes) throw UpdateFailure(UpdateFailureReason.INVALID_APK)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                transport.checkCanceled()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > prepared.release.apkBytes) throw UpdateFailure(UpdateFailureReason.INVALID_APK)
                digest.update(buffer, 0, count)
            }
        }
        if (total != prepared.release.apkBytes || hex(digest.digest()) != prepared.sha256) throw UpdateFailure(UpdateFailureReason.INVALID_APK)
        return validateArchive(file, prepared.release).also {
            if (it.code != prepared.versionCode) throw UpdateFailure(UpdateFailureReason.INVALID_APK)
        }
    }

    fun discard(prepared: PreparedUpdate) {
        // Only our own private temporary directory; never accept arbitrary paths for cleanup.
        runCatching {
            if (prepared.apk.canonicalFile.parentFile == directory.canonicalFile && prepared.apk.name.matches(Regex("lume-update-[A-Za-z0-9-]+\\.apk"))) prepared.apk.delete()
        }
    }

    private fun validateArchive(file: File, release: UpdateRelease): UpdateApkIdentity {
        val current = installed()
        val candidate = try { inspect(file) } catch (_: Exception) { null }
            ?: throw UpdateFailure(UpdateFailureReason.INVALID_APK)
        if (candidate.version != release.version || !UpdateRules.validApk(candidate.packageName, current.code,
                candidate.code, deviceSdk, candidate.minSdk, current.certificates, candidate.certificates))
            throw UpdateFailure(UpdateFailureReason.INVALID_APK)
        return candidate
    }

    private fun validateRelease(release: UpdateRelease) {
        if (release.version != release.tag.removePrefix("v") ||
            release.pageUrl != "https://github.com/JohnSalazar/lume-orbita/releases/tag/${release.tag}" ||
            !UpdateRules.isReleaseAssetUrl(release.apkUrl, release.tag, "lume-orbita-${release.version}.apk") ||
            !UpdateRules.isReleaseAssetUrl(release.checksumsUrl, release.tag, "SHA256SUMS.txt") ||
            release.apkBytes !in 1..UpdateRules.MAX_APK_BYTES) throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE)
    }

    internal fun parseReleases(text: String, currentVersion: String): UpdateRelease? {
        try {
            val releases = JSONArray(text)
            if (releases.length() > 30) throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE)
            var best: org.json.JSONObject? = null
            for (index in 0 until releases.length()) {
                val release = releases.getJSONObject(index)
                if (release.getBoolean("draft")) continue
                val tag = release.getString("tag_name")
                val newer = UpdateRules.compareVersions(tag, currentVersion) ?: continue
                if (!tag.startsWith("v") || newer <= 0) continue
                if (best == null || (UpdateRules.compareVersions(tag, best.getString("tag_name")) ?: 0) > 0) best = release
            }
            val selected = best ?: return null
            val tag = selected.getString("tag_name")
            val version = tag.removePrefix("v")
            val assets = selected.getJSONArray("assets")
            if (assets.length() > 100) throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE)
            var apkUrl: String? = null; var sumsUrl: String? = null; var bytes = 0L
            for (index in 0 until assets.length()) {
                val asset = assets.getJSONObject(index)
                val name = asset.getString("name")
                if (name != "lume-orbita-$version.apk" && name != "SHA256SUMS.txt") continue
                if (asset.getString("state") != "uploaded") throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE)
                val url = asset.getString("browser_download_url")
                if (!UpdateRules.isReleaseAssetUrl(url, tag, name)) throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE)
                if (name.endsWith(".apk")) {
                    if (apkUrl != null) throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE)
                    apkUrl = url; bytes = asset.getLong("size")
                } else {
                    if (sumsUrl != null || asset.getLong("size") !in 1..UpdateRules.MAX_CHECKSUM_BYTES.toLong())
                        throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE)
                    sumsUrl = url
                }
            }
            return UpdateRelease(version, tag, selected.getString("html_url"),
                apkUrl ?: throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE),
                sumsUrl ?: throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE), bytes, selected.getBoolean("prerelease"))
                .also(::validateRelease)
        } catch (_: JSONException) { throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE) }
    }

    private data class Environment(val directory: File, val installed: () -> UpdateApkIdentity, val inspect: (File) -> UpdateApkIdentity?)
    companion object {
        internal const val LIST_URL = "https://api.github.com/repos/JohnSalazar/lume-orbita/releases?per_page=30"
        private fun environment(app: Context): Environment {
            val pm = app.packageManager
            fun identity(info: PackageInfo): UpdateApkIdentity {
                val certificates = info.signingInfo?.apkContentsSigners?.map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }?.toSet().orEmpty()
                return UpdateApkIdentity(info.packageName, info.versionName.orEmpty(), info.longVersionCode,
                    info.applicationInfo?.minSdkVersion ?: 0, certificates)
            }
            return Environment(File(app.cacheDir, "updates"), {
                @Suppress("DEPRECATION")
                identity(pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES))
            }, { file ->
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)?.let(::identity)
            })
        }
        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

/** A bounded transport seam for JVM fakes; no Android state or installer calls. */
internal class UpdateHttpTransport(
    private val connect: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val resolve: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val nowNanos: () -> Long = System::nanoTime
) {
    @Volatile private var canceled = false
    @Volatile private var connection: HttpURLConnection? = null

    fun cancel() {
        canceled = true
        // disconnect is not guaranteed non-blocking. Never run socket cleanup on the UI thread.
        connection?.let { active -> cancellation.execute { runCatching { active.disconnect() } } }
    }
    fun checkCanceled() { if (canceled || Thread.currentThread().isInterrupted) throw UpdateFailure(UpdateFailureReason.CANCELED) }

    fun bytes(address: String, maximum: Int): ByteArray {
        val output = ByteArrayOutputStream()
        read(address, maximum.toLong()) { buffer, count -> output.write(buffer, 0, count) }
        return output.toByteArray()
    }

    fun read(address: String, maximum: Long, consume: (ByteArray, Int) -> Unit): Long {
        checkCanceled()
        val started = nowNanos()
        fun remaining(): Int {
            checkCanceled()
            val left = 60_000L - TimeUnit.NANOSECONDS.toMillis(nowNanos() - started).coerceAtLeast(0L)
            if (left <= 0) throw UpdateFailure(UpdateFailureReason.NETWORK)
            return left.toInt()
        }
        var target = address; var redirects = 0
        try {
            while (true) {
                if (target != GitHubUpdater.LIST_URL && !UpdateRules.redirectUrl(target)) throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE)
                val url = URL(target)
                val addresses = resolve(url.host)
                remaining()
                if (addresses.isEmpty() || addresses.any { !publicAddress(it) }) throw UpdateFailure(UpdateFailureReason.NETWORK)
                val active = connect(url)
                connection = active
                try {
                    active.instanceFollowRedirects = false; active.useCaches = false; active.allowUserInteraction = false
                    val left = remaining()
                    active.connectTimeout = minOf(left, (left.toLong() / addresses.size / 2).coerceIn(250, 1000).toInt())
                    active.readTimeout = minOf(10_000, left)
                    active.setRequestProperty("Accept", if (target == GitHubUpdater.LIST_URL) "application/vnd.github+json" else "application/octet-stream,text/plain")
                    active.setRequestProperty("Accept-Encoding", "identity")
                    active.setRequestProperty("Cache-Control", "no-store, no-cache")
                    active.setRequestProperty("User-Agent", "LumeOrbitaUpdater/1 (Android TV manual update)")
                    val status = active.responseCode
                    remaining()
                    if (status in listOf(301, 302, 303, 307, 308)) {
                        if (redirects++ >= UpdateRules.MAX_REDIRECTS) throw UpdateFailure(UpdateFailureReason.NETWORK)
                        val location = active.getHeaderField("Location") ?: throw UpdateFailure(UpdateFailureReason.NETWORK)
                        target = URL(url, location).toExternalForm()
                        if (address == GitHubUpdater.LIST_URL || !UpdateRules.redirectUrl(target)) throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE)
                        continue
                    }
                    if (status == 429 || (status == 403 && active.getHeaderField("X-RateLimit-Remaining") == "0")) throw UpdateFailure(UpdateFailureReason.RATE_LIMIT)
                    if (status != 200) throw UpdateFailure(UpdateFailureReason.NETWORK)
                    val length = active.contentLengthLong
                    if (length == 0L || length > maximum) throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE)
                    var count = 0L
                    active.inputStream.use { input ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            active.readTimeout = minOf(10_000, remaining())
                            val read = input.read(buffer)
                            remaining()
                            if (read < 0) break
                            count += read
                            if (count > maximum) throw UpdateFailure(UpdateFailureReason.INVALID_RELEASE)
                            consume(buffer, read)
                        }
                    }
                    if (count == 0L || (length >= 0 && length != count)) throw UpdateFailure(UpdateFailureReason.NETWORK)
                    return count
                } finally {
                    connection = null
                    runCatching { active.disconnect() }
                }
            }
        } catch (failure: UpdateFailure) { throw failure }
          catch (_: InterruptedIOException) { checkCanceled(); throw UpdateFailure(UpdateFailureReason.NETWORK) }
          catch (_: IOException) { checkCanceled(); throw UpdateFailure(UpdateFailureReason.NETWORK) }
          catch (_: SecurityException) { throw UpdateFailure(UpdateFailureReason.NETWORK) }
    }

    companion object {
        // Process-wide finite cancellation queue, separate from potentially blocked network workers.
        private val cancellation = ThreadPoolExecutor(1, 1, 15, TimeUnit.SECONDS, ArrayBlockingQueue(1),
            { task -> Thread(task, "lume-update-cancel").apply { isDaemon = true } }, ThreadPoolExecutor.DiscardPolicy())
            .apply { allowCoreThreadTimeOut(true) }
        private fun publicAddress(address: InetAddress): Boolean {
            if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
            val bytes = address.address
            if (bytes.size == 4) {
                val a = bytes[0].toInt() and 255; val b = bytes[1].toInt() and 255
                return a !in listOf(0, 10, 127) && a < 224 && !(a == 100 && b in 64..127) &&
                    !(a == 169 && b == 254) && !(a == 172 && b in 16..31) && !(a == 192 && b == 168)
            }
            return bytes.size == 16 && (bytes[0].toInt() and 0xfe) != 0xfc
        }
    }
}
