package br.com.lume.launcher.core

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Pure validation only. Network, APK signature verification and installation remain Android adapters. */
object UpdateRules {
    const val MAX_APK_BYTES = 32L * 1024 * 1024
    const val MAX_JSON_BYTES = 1024 * 1024
    const val MAX_CHECKSUM_BYTES = 64 * 1024
    const val MAX_REDIRECTS = 3
    private const val REPOSITORY = "https://github.com/JohnSalazar/lume-orbita/releases/download/"
    private val versionPattern = Regex("v?(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})")
    private val sha256Pattern = Regex("[0-9a-fA-F]{64}")
    private val checksumLine = Regex("([0-9a-fA-F]{64}) [ *]([^\\r\\n]+)")

    private fun version(value: String): List<Int>? = versionPattern.matchEntire(value)
        ?.groupValues?.drop(1)?.map { it.toInt() }

    fun compareVersions(a: String, b: String): Int? {
        val first = version(a) ?: return null
        val second = version(b) ?: return null
        for (index in first.indices) {
            val comparison = first[index].compareTo(second[index])
            if (comparison != 0) return comparison
        }
        return 0
    }

    fun releaseUrl(tag: String, file: String): String? {
        if (!tag.startsWith("v") || version(tag) == null) return null
        val expectedApk = "lume-orbita-${tag.removePrefix("v")}.apk"
        if (file != expectedApk && file != "SHA256SUMS.txt") return null
        return "$REPOSITORY$tag/$file"
    }

    fun isReleaseAssetUrl(url: String, tag: String, file: String): Boolean {
        val expected = releaseUrl(tag, file) ?: return false
        return url == expected
    }

    /** Every redirect is checked again; CDN query strings are signed by GitHub and never logged. */
    fun redirectUrl(url: String): Boolean {
        if (url.length > 8192 || url.any { it.code !in 33..126 || it == '\\' }) return false
        val uri = try { URI(url) } catch (_: URISyntaxException) { return false }
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.rawFragment != null ||
            uri.port !in listOf(-1, 443)) return false
        return when (uri.host?.lowercase(Locale.ROOT)) {
            "release-assets.githubusercontent.com", "objects.githubusercontent.com" -> !uri.rawPath.isNullOrEmpty()
            "github.com" -> {
                val parts = uri.rawPath.orEmpty().split('/')
                uri.rawQuery == null && parts.size == 7 &&
                    releaseUrl(parts[5], parts[6]) == "https://github.com${uri.rawPath}"
            }
            else -> false
        }
    }

    /** Read standard sha256sum text. A malformed or duplicate entry fails closed. */
    fun checksum(text: String, filename: String): String? {
        if (text.length > MAX_CHECKSUM_BYTES || text.toByteArray(Charsets.UTF_8).size > MAX_CHECKSUM_BYTES ||
            filename.isEmpty() || filename.length > 128 || filename.any { it.code !in 33..126 || it in "\\/" }) return null
        var result: String? = null
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val entry = checksumLine.matchEntire(line) ?: return null
            if (entry.groupValues[2] == filename) {
                if (result != null) return null
                result = entry.groupValues[1].lowercase(Locale.ROOT)
            }
        }
        return result
    }

    /** Inputs are verified archive metadata and SHA-256 of the current content signers, never history. */
    fun validApk(packageName: String, installedCode: Long, apkCode: Long, deviceSdk: Int, minSdk: Int,
                 installedCertSet: Set<String>, apkCertSet: Set<String>): Boolean =
        packageName == "br.com.lume.launcher" && installedCode > 0 && apkCode > installedCode &&
            minSdk > 0 && minSdk <= deviceSdk && installedCertSet.isNotEmpty() &&
            installedCertSet.all { sha256Pattern.matches(it) } && installedCertSet == apkCertSet
}
