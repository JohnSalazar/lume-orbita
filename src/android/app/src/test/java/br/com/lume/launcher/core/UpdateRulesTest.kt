package br.com.lume.launcher.core

import org.junit.Assert.*
import org.junit.Test

class UpdateRulesTest {
    private val hash = "a".repeat(64)
    private val otherHash = "b".repeat(64)
    private val name = "lume-orbita-0.4.3.apk"
    private val url = "https://github.com/JohnSalazar/lume-orbita/releases/download/v0.4.3/$name"

    @Test fun comparesAllVersionComponentsWithoutOverflowOrAmbiguity() {
        assertEquals(0, UpdateRules.compareVersions("v0.4.3", "0.4.3"))
        assertTrue(UpdateRules.compareVersions("1.0.0", "0.99.999")!! > 0)
        assertTrue(UpdateRules.compareVersions("0.5.0", "0.4.999")!! > 0)
        assertTrue(UpdateRules.compareVersions("0.4.3", "0.4.4")!! < 0)
        assertEquals(0, UpdateRules.compareVersions("999999999.999999999.999999999", "v999999999.999999999.999999999"))
        listOf("", "V0.4.3", "vv0.4.3", "0.04.3", "00.4.3", "0.4.03", "0.4", "0.4.3.1", "0.4.3-beta", "0.4.3+build", " 0.4.3", "0.4.3\n", "-1.0.0", "1000000000.0.0", "٠.٤.٣").forEach {
            assertNull(it, UpdateRules.compareVersions(it, "0.4.3"))
            assertNull(it, UpdateRules.compareVersions("0.4.3", it))
        }
    }

    @Test fun constructsOnlyCanonicalRepositoryAssets() {
        assertEquals(url, UpdateRules.releaseUrl("v0.4.3", name))
        assertEquals("https://github.com/JohnSalazar/lume-orbita/releases/download/v0.4.3/SHA256SUMS.txt", UpdateRules.releaseUrl("v0.4.3", "SHA256SUMS.txt"))
        assertNull(UpdateRules.releaseUrl("0.4.3", name))
        assertNull(UpdateRules.releaseUrl("v00.4.3", name))
        listOf("lume-orbita-0.4.2.apk", "../$name", "$name?x=1", "SHA256SUMS", "test.apk").forEach {
            assertNull(UpdateRules.releaseUrl("v0.4.3", it))
        }
        assertTrue(UpdateRules.isReleaseAssetUrl(url, "v0.4.3", name))
        assertFalse(UpdateRules.isReleaseAssetUrl(url + "?x=1", "v0.4.3", name))
        assertFalse(UpdateRules.isReleaseAssetUrl(url, "invalid", name))
    }

    @Test fun permitsOnlyStrictHttpsRedirectsAndDoesNotWidenGithubRepository() {
        listOf(url, "https://github.com:443/JohnSalazar/lume-orbita/releases/download/v0.4.3/$name",
            "https://release-assets.githubusercontent.com/github-production-release-asset/a?token=SIGNED&x=1",
            "https://objects.githubusercontent.com:443/github-production-release-asset/a?x=1",
            "https://RELEASE-ASSETS.GITHUBUSERCONTENT.COM/a").forEach { assertTrue(it, UpdateRules.redirectUrl(it)) }
        listOf("", "https://objects.githubusercontent.com", "http://objects.githubusercontent.com/a", "https://objects.githubusercontent.com:444/a",
            "https://objects.githubusercontent.com.evil.test/a", "https://evil.test/a", "https://127.0.0.1/a",
            "https://u:p@objects.githubusercontent.com/a", "https://objects.githubusercontent.com/a#fragment",
            "https://github.com/evil/lume-orbita/releases/download/v0.4.3/$name", url + "?token=x", url + "/extra",
            url.replace("v0.4.3", "v0.04.3"), url.replace(name, "test.apk"), url.replace("/releases/", "/%72eleases/"),
            "https://objects.githubusercontent.com/a b", "https://objects.githubusercontent.com/a\\b", "https://objects.githubusercontent.com/é",
            "https://objects.githubusercontent.com/%ZZ", "https://objects.githubusercontent.com/a\n", "https:///a", "https://objects.githubusercontent.com/" + "a".repeat(8192)).forEach {
            assertFalse(it, UpdateRules.redirectUrl(it))
        }
    }

    @Test fun checksumRequiresOneExactCompleteEntryAndNeverUsesBasenames() {
        assertEquals(hash, UpdateRules.checksum("${hash.uppercase()}  $name\r\n$otherHash *documentation.zip\r\n", name))
        assertEquals(hash, UpdateRules.checksum("\n$hash *$name\n\n", name))
        assertNull(UpdateRules.checksum("", name))
        assertNull(UpdateRules.checksum("$hash  folder/$name\n", name))
        assertNull(UpdateRules.checksum("$hash  $name\n$hash  $name\n", name))
        assertNull(UpdateRules.checksum("$hash  $name\n$otherHash *$name\n", name))
        assertNull(UpdateRules.checksum("$hash  $name.extra\n", name))
        assertNull(UpdateRules.checksum("${"g".repeat(64)}  $name", name))
        assertNull(UpdateRules.checksum("${"a".repeat(63)}  $name", name))
        assertNull(UpdateRules.checksum("$hash $name", name))
        assertNull(UpdateRules.checksum("$hash  $name\nmalformed", name))
        listOf("", "folder/$name", "folder\\$name", "a b.apk", "é.apk", "a".repeat(129)).forEach {
            assertNull(it, UpdateRules.checksum("$hash  $it", it))
        }
    }

    @Test fun checksumInputIsBoundedByBothCharactersAndUtf8Bytes() {
        val entry = "$hash  $name\n"
        assertEquals(hash, UpdateRules.checksum(entry + " ".repeat(UpdateRules.MAX_CHECKSUM_BYTES - entry.length), name))
        assertNull(UpdateRules.checksum(" ".repeat(UpdateRules.MAX_CHECKSUM_BYTES + 1), name))
        assertNull(UpdateRules.checksum("é".repeat(UpdateRules.MAX_CHECKSUM_BYTES), name))
        assertEquals(32L * 1024 * 1024, UpdateRules.MAX_APK_BYTES)
        assertEquals(1024 * 1024, UpdateRules.MAX_JSON_BYTES)
        assertEquals(3, UpdateRules.MAX_REDIRECTS)
    }

    @Test fun acceptsOnlyNewerCompatibleSamePackageAndExactCurrentSignerSet() {
        fun valid(pkg: String = "br.com.lume.launcher", installed: Long = 28, candidate: Long = 29,
                  sdk: Int = 30, minimum: Int = 30, current: Set<String> = setOf(hash), downloaded: Set<String> = setOf(hash)) =
            UpdateRules.validApk(pkg, installed, candidate, sdk, minimum, current, downloaded)
        assertTrue(valid())
        assertTrue(valid(installed = Long.MAX_VALUE - 1, candidate = Long.MAX_VALUE, minimum = 1))
        assertTrue(valid(current = setOf(hash, otherHash), downloaded = setOf(otherHash, hash)))
        assertFalse(valid(pkg = "br.com.lume.launcher.test"))
        assertFalse(valid(installed = 0))
        assertFalse(valid(installed = -1))
        assertFalse(valid(candidate = 28))
        assertFalse(valid(candidate = 27))
        assertFalse(valid(candidate = Long.MIN_VALUE))
        assertFalse(valid(minimum = 31))
        assertFalse(valid(minimum = 0))
        assertFalse(valid(minimum = -1))
        assertFalse(valid(sdk = 0, minimum = 1))
        assertFalse(valid(current = emptySet(), downloaded = emptySet()))
        assertFalse(valid(current = setOf(""), downloaded = setOf("")))
        assertFalse(valid(current = setOf("g".repeat(64)), downloaded = setOf("g".repeat(64))))
        assertFalse(valid(downloaded = emptySet()))
        assertFalse(valid(downloaded = setOf(otherHash)))
        assertFalse(valid(downloaded = setOf(hash, otherHash)))
    }
}
