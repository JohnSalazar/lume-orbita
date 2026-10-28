package br.com.lume.launcher.core

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DecimalStyle
import java.time.format.FormatStyle
import java.util.IllformedLocaleException
import java.util.Locale

/** App-only locale policy. No Android, process-default locale or persisted display labels. */
object LocaleRules {
    const val SYSTEM = "system"
    const val FALLBACK = "en-US"
    val supportedTags = listOf("en-US", "en-GB", "en-CA", "pt-BR", "pt-PT", "es-ES", "fr-FR", "zh-CN", "hi-IN", "ar", "bn-BD", "ru-RU", "ur-PK", "ja-JP", "pnb-Arab-PK", "mr-IN", "de-DE", "it-IT", "ko-KR", "id-ID", "tr-TR")
    private val defaults = supportedTags.associateBy { it.substringBefore('-') }
        .toMutableMap().apply { put("en", FALLBACK); put("pt", "pt-BR") }.toMap()
    private val scripts = mapOf("en" to "Latn", "pt" to "Latn", "es" to "Latn", "fr" to "Latn", "zh" to "Hans", "hi" to "Deva", "ar" to "Arab", "bn" to "Beng", "ru" to "Cyrl", "ur" to "Arab", "ja" to "Jpan", "pnb" to "Arab", "mr" to "Deva", "de" to "Latn", "it" to "Latn", "ko" to "Kore", "id" to "Latn", "tr" to "Latn")

    private fun parse(tag: String?): Locale? {
        val value = tag?.trim()?.replace('_', '-') ?: return null
        if (value.isEmpty() || value.length > 80) return null
        return try { Locale.Builder().setLanguageTag(value).build() }
            catch (_: IllformedLocaleException) { null }
    }

    // Android/JVM releases differ in whether the legacy Indonesian code is returned here.
    private fun language(locale: Locale) = locale.language.let { if (it == "in") "id" else it }
    private fun region(locale: Locale) = locale.country.let { if (it == "UK") "GB" else it }
    private fun canonical(locale: Locale): String = Locale.Builder().setLanguage(language(locale))
        .setScript(locale.script).setRegion(region(locale)).build().toLanguageTag()

    /** Manual choices must belong to the catalog; legacy spelling/case aliases are canonicalized. */
    fun selection(option: String?): String? {
        if (option?.trim()?.equals(SYSTEM, ignoreCase = true) == true) return SYSTEM
        val locale = parse(option) ?: return null
        if (locale.hasExtensions() || locale.variant.isNotEmpty()) return null
        return canonical(locale).takeIf { it in supportedTags }
    }

    fun resolve(option: String?, systemTags: List<String>): String {
        selection(option)?.takeIf { it != SYSTEM }?.let { return it }
        return systemTags.asSequence().mapNotNull { parse(it)?.let(::matchSystem) }.firstOrNull() ?: FALLBACK
    }

    private fun matchSystem(locale: Locale): String? {
        val language = language(locale)
        val region = region(locale)
        val script = locale.script
        // Punjabi in Pakistan may be advertised as pa-Arab-PK; Gurmukhi is not Shahmukhi.
        if (language == "pa") return if (script == "Arab" || (script.isEmpty() && region == "PK")) "pnb-Arab-PK" else null
        val expectedScript = scripts[language] ?: return null
        if (script.isNotEmpty() && script != expectedScript) return null
        // Do not silently substitute Simplified Chinese for a Traditional Chinese preference.
        if (language == "zh" && script.isEmpty() && region in listOf("TW", "HK", "MO")) return null
        val regional = Locale.Builder().setLanguage(language).setRegion(region).build().toLanguageTag()
        return canonical(locale).takeIf { it in supportedTags } ?: regional.takeIf { it in supportedTags } ?: defaults[language]
    }

    fun isRtl(tag: String): Boolean {
        val locale = parse(tag) ?: return false
        if (locale.script.isNotEmpty()) return locale.script == "Arab"
        return language(locale) in listOf("ar", "ur", "pnb") || (language(locale) == "pa" && region(locale) == "PK")
    }

    // CLDR stores Shahmukhi date/number data under pa-Arab, rather than the pnb UI tag.
    fun formattingTag(tag: String): String = if (tag == "pnb-Arab-PK") "pa-Arab-PK" else tag

    /** Follow the app locale's default hour cycle without changing Android's clock preferences. */
    fun formatTime(time: LocalTime, tag: String): String {
        val locale = Locale.forLanguageTag(formattingTag(resolve(tag, emptyList())))
        return time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale).withDecimalStyle(DecimalStyle.of(locale)))
    }

    fun resourceQualifier(tag: String): String {
        val selected = selection(tag)
        require(selected != null && selected != SYSTEM) { "Unsupported app locale" }
        if (selected == FALLBACK) return "values"
        if (selected == "pnb-Arab-PK") return "values-b+pnb+Arab+PK"
        val locale = Locale.forLanguageTag(selected)
        val resourceLanguage = if (language(locale) == "id") "in" else language(locale)
        return "values-$resourceLanguage" + if (locale.country.isEmpty()) "" else "-r${locale.country}"
    }

    /** Convert physical arrows for app-owned horizontal handlers only; leave framework routing alone. */
    fun navigationKey(code: Int, isRtl: Boolean): Int = if (!isRtl) code else when (code) {
        21 -> 22 // Android KEYCODE_DPAD_LEFT / RIGHT, without an Android dependency in core.
        22 -> 21
        else -> code
    }
}
