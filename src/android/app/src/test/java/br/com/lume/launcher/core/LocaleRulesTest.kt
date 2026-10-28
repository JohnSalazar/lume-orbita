package br.com.lume.launcher.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.Locale

class LocaleRulesTest {
    @Test fun catalogContainsExactlyTheTwentyOneRequestedLocales() {
        val expected = listOf("en-US", "en-GB", "en-CA", "pt-BR", "pt-PT", "es-ES", "fr-FR", "zh-CN", "hi-IN", "ar", "bn-BD", "ru-RU", "ur-PK", "ja-JP", "pnb-Arab-PK", "mr-IN", "de-DE", "it-IT", "ko-KR", "id-ID", "tr-TR")
        assertEquals(expected, LocaleRules.supportedTags)
        expected.forEach { tag ->
            assertEquals(tag, LocaleRules.selection(tag))
            assertEquals(tag, LocaleRules.resolve(tag, listOf("ja-JP")))
            assertEquals(tag, LocaleRules.resolve("system", listOf(tag)))
        }
    }

    @Test fun normalizesCaseLegacySpellingAndSeparatorsWithoutAcceptingExtraManualLocales() {
        mapOf("EN_us" to "en-US", "pt_br" to "pt-BR", "in-ID" to "id-ID", "en-UK" to "en-GB", " PNB_arab_pk " to "pnb-Arab-PK", " SYSTEM " to "system").forEach { (input, expected) ->
            assertEquals(expected, LocaleRules.selection(input))
        }
        listOf(null, "", "  ", "zz-ZZ", "pt-AO", "ar-SA", "pa-Guru-IN", "en-US-u-nu-arab", "en-US-POSIX", "en-US-!bad", "a".repeat(81)).forEach {
            assertNull(it, LocaleRules.selection(it))
        }
    }

    @Test fun autoRespectsSystemPreferenceOrderAndUsesEnglishOnlyWhenNoMatchExists() {
        assertEquals("fr-FR", LocaleRules.resolve("system", listOf("zz-ZZ", "fr-CA", "pt-BR")))
        assertEquals("en-GB", LocaleRules.resolve(null, listOf("en-UK", "en-US")))
        assertEquals("pt-BR", LocaleRules.resolve("invalid", listOf("pt-AO")))
        assertEquals("en-US", LocaleRules.resolve("system", listOf("en-AU", "fr-FR")))
        assertEquals("en-US", LocaleRules.resolve("system", listOf("", "bad_tag_!", "zz-ZZ")))
        assertEquals("en-US", LocaleRules.resolve("system", emptyList()))
    }

    @Test fun regionalFallbackKeepsSupportedLanguagesAndExtensionsDoNotBecomeStoredOptions() {
        mapOf("pt-MZ" to "pt-BR", "es-MX" to "es-ES", "fr-CA" to "fr-FR", "bn-IN" to "bn-BD", "ur-IN" to "ur-PK", "in-ID" to "id-ID", "ar-EG" to "ar", "en-Latn-CA" to "en-CA", "ja-JP-u-ca-japanese" to "ja-JP").forEach { (source, expected) ->
            // An explicit default script can still preserve a supported region.
            assertEquals(source, expected, LocaleRules.resolve("system", listOf(source)))
        }
    }

    @Test fun traditionalChineseIsNeverSilentlySubstitutedWithSimplifiedChinese() {
        listOf("zh-Hant", "zh-Hant-TW", "zh-TW", "zh-HK", "zh-MO", "zh-Latn-CN").forEach {
            assertEquals(it, "en-GB", LocaleRules.resolve("system", listOf(it, "en-GB")))
        }
        listOf("zh-CN", "zh-SG", "zh", "zh-Hans", "zh-Hans-TW").forEach {
            assertEquals(it, "zh-CN", LocaleRules.resolve("system", listOf(it)))
        }
    }

    @Test fun shahmukhiMatchesOnlyCompatiblePunjabiScripts() {
        listOf("pnb-Arab-PK", "pnb-PK", "pnb", "pa-Arab-PK", "pa-PK", "pa-Arab").forEach {
            assertEquals(it, "pnb-Arab-PK", LocaleRules.resolve("system", listOf(it)))
        }
        listOf("pa", "pa-IN", "pa-Guru-PK", "pnb-Guru-IN", "pnb-Latn-PK").forEach {
            assertEquals(it, "en-US", LocaleRules.resolve("system", listOf(it)))
        }
    }

    @Test fun explicitUnsupportedScriptsSkipToTheNextSystemLanguage() {
        listOf("en-Cyrl-US", "ar-Latn", "ur-Latn-PK", "ru-Latn", "hi-Latn-IN").forEach {
            assertEquals(it, "de-DE", LocaleRules.resolve("system", listOf(it, "de-DE")))
        }
    }

    @Test fun rtlIsExplicitForArabicUrduAndWesternPunjabi() {
        LocaleRules.supportedTags.forEach { assertEquals(it, it in setOf("ar", "ur-PK", "pnb-Arab-PK"), LocaleRules.isRtl(it)) }
        assertTrue(LocaleRules.isRtl("pa-PK")); assertTrue(LocaleRules.isRtl("pa-Arab-PK"))
        assertFalse(LocaleRules.isRtl("pnb-Guru-IN")); assertFalse(LocaleRules.isRtl("invalid-!"))
    }

    @Test fun resourceKeysAreUniqueAndUseBcp47QualifierForThreeLetterScriptLocale() {
        assertEquals("values", LocaleRules.resourceQualifier("en-US"))
        assertEquals("values-en-rGB", LocaleRules.resourceQualifier("en-UK"))
        assertEquals("values-pt-rBR", LocaleRules.resourceQualifier("pt-BR"))
        assertEquals("values-ar", LocaleRules.resourceQualifier("ar"))
        assertEquals("values-in-rID", LocaleRules.resourceQualifier("id-ID"))
        assertEquals("values-b+pnb+Arab+PK", LocaleRules.resourceQualifier("pnb-Arab-PK"))
        assertEquals(21, LocaleRules.supportedTags.map(LocaleRules::resourceQualifier).toSet().size)
        listOf("system", "zz-ZZ").forEach { assertThrows(IllegalArgumentException::class.java) { LocaleRules.resourceQualifier(it) } }
    }

    @Test fun onlyHorizontalAppOwnedKeysAreMirroredAndTheMappingIsReversible() {
        listOf(19, 20, 21, 22, 23, 3, 4, 24, 25, 66, 999).forEach { key ->
            assertEquals(key, LocaleRules.navigationKey(key, false))
            assertEquals(key, LocaleRules.navigationKey(LocaleRules.navigationKey(key, true), true))
        }
        assertEquals(22, LocaleRules.navigationKey(21, true)); assertEquals(21, LocaleRules.navigationKey(22, true))
        listOf(19, 20, 23, 3, 4, 24, 25, 66, 999).forEach { assertEquals(it, LocaleRules.navigationKey(it, true)) }
    }

    @Test fun shahmukhiFormattingUsesTheCompatibleCldrScriptWithoutChangingOtherLocales() {
        LocaleRules.supportedTags.forEach { tag ->
            assertEquals(if (tag == "pnb-Arab-PK") "pa-Arab-PK" else tag, LocaleRules.formattingTag(tag))
        }
    }

    // CLDR versions differ between JDK and Android in their regular/nonbreaking AM/PM separator.
    private fun normalizedTime(value: String) = value.replace('\u202f', ' ').replace('\u00a0', ' ')

    @Test fun shortTimeFollowsAppLocaleInsteadOfAlwaysUsingTwentyFourHours() {
        val afternoon = LocalTime.of(17, 5)
        assertEquals("17:05", LocaleRules.formatTime(afternoon, "pt-BR"))
        assertEquals("5:05 PM", normalizedTime(LocaleRules.formatTime(afternoon, "en-US")))
        assertEquals("17:05", LocaleRules.formatTime(afternoon, "en-GB"))
        assertEquals("5:05 AM", normalizedTime(LocaleRules.formatTime(LocalTime.of(5, 5), "en-US")))
    }

    @Test fun midnightAndNoonUseTheCorrectTwelveHourPeriod() {
        assertEquals("12:00 AM", normalizedTime(LocaleRules.formatTime(LocalTime.MIDNIGHT, "en-US")))
        assertEquals("12:00 PM", normalizedTime(LocaleRules.formatTime(LocalTime.NOON, "en-US")))
        assertEquals("00:00", LocaleRules.formatTime(LocalTime.MIDNIGHT, "pt-BR"))
        assertEquals("12:00", LocaleRules.formatTime(LocalTime.NOON, "pt-BR"))
    }

    @Test fun everyAppLocaleFormatsWithoutChangingAnyJvmDefaultLocale() {
        val original = Locale.getDefault()
        val display = Locale.getDefault(Locale.Category.DISPLAY)
        val format = Locale.getDefault(Locale.Category.FORMAT)
        LocaleRules.supportedTags.forEach { tag ->
            listOf(LocalTime.MIDNIGHT, LocalTime.NOON, LocalTime.of(17, 5)).forEach { time ->
                assertTrue(tag, LocaleRules.formatTime(time, tag).isNotBlank())
            }
        }
        assertEquals(original, Locale.getDefault())
        assertEquals(display, Locale.getDefault(Locale.Category.DISPLAY))
        assertEquals(format, Locale.getDefault(Locale.Category.FORMAT))
    }

    @Test fun timeFormattingPreservesShahmukhiMappingAliasesAndEnglishFallback() {
        val time = LocalTime.of(17, 5)
        val locale = Locale.forLanguageTag("pa-Arab-PK")
        val shahmukhi = time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale).withDecimalStyle(DecimalStyle.of(locale)))
        assertEquals(shahmukhi, LocaleRules.formatTime(time, "pnb-Arab-PK"))
        assertEquals(LocaleRules.formatTime(time, "pt-BR"), LocaleRules.formatTime(time, "PT_br"))
        listOf("system", "invalid-!", "zz-ZZ").forEach { tag ->
            assertEquals(LocaleRules.formatTime(time, "en-US"), LocaleRules.formatTime(time, tag))
        }
    }

    @Test fun timeDigitsFollowTheFormattingLocalesDefaultNumbering() {
        LocaleRules.supportedTags.forEach { tag ->
            val locale = Locale.forLanguageTag(LocaleRules.formattingTag(tag))
            val zero = DecimalStyle.of(locale).zeroDigit
            val digits = LocaleRules.formatTime(LocalTime.of(17, 5), tag).filter(Char::isDigit)
            assertTrue(tag, digits.isNotEmpty())
            assertTrue(tag, digits.all { it.code in zero.code..zero.code + 9 })
        }
        listOf("ar", "bn-BD").forEach { tag ->
            assertNotEquals(tag, '0', DecimalStyle.of(Locale.forLanguageTag(tag)).zeroDigit)
        }
    }
}
