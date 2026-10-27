package br.com.lume.launcher

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.LocaleList
import br.com.lume.launcher.core.LocaleRules
import java.util.Locale

data class LanguageOption(val tag: String, val nativeName: String)

/** Custom in-app language selection for API30+, without changing the system/process locale. */
object AppLocales {
    private const val PREFS = "orbita"
    private const val KEY = "app_locale"
    val languages = listOf(
        LanguageOption("en-US", "English (United States)"), LanguageOption("en-GB", "English (United Kingdom)"),
        LanguageOption("en-CA", "English (Canada)"), LanguageOption("pt-BR", "Português (Brasil)"),
        LanguageOption("pt-PT", "Português (Portugal)"), LanguageOption("es-ES", "Español (España)"),
        LanguageOption("fr-FR", "Français (France)"), LanguageOption("zh-CN", "简体中文（中国）"),
        LanguageOption("hi-IN", "हिन्दी (भारत)"), LanguageOption("ar", "العربية الفصحى"),
        LanguageOption("bn-BD", "বাংলা (বাংলাদেশ)"), LanguageOption("ru-RU", "Русский (Россия)"),
        LanguageOption("ur-PK", "اردو (پاکستان)"), LanguageOption("ja-JP", "日本語 (日本)"),
        LanguageOption("pnb-Arab-PK", "پنجابی (شاہ مکھی، پاکستان)"), LanguageOption("mr-IN", "मराठी (भारत)"),
        LanguageOption("de-DE", "Deutsch (Deutschland)"), LanguageOption("it-IT", "Italiano (Italia)"),
        LanguageOption("ko-KR", "한국어 (대한민국)"), LanguageOption("id-ID", "Bahasa Indonesia"),
        LanguageOption("tr-TR", "Türkçe (Türkiye)")
    )

    // Scoped to this wrapped context, never stored in a static context cache.
    private class LocalizedContext(base: Context, val original: Context) : ContextWrapper(base)

    private fun original(context: Context): Context {
        var cursor = context
        repeat(32) {
            if (cursor is LocalizedContext) return (cursor as LocalizedContext).original
            val next = (cursor as? ContextWrapper)?.baseContext ?: return context
            if (next === cursor) return context
            cursor = next
        }
        return context
    }

    private fun systemTags(context: Context): List<String> {
        val list = original(context).resources.configuration.locales
        return (0 until list.size()).map { list[it].toLanguageTag() }
    }

    fun selected(context: Context): String = LocaleRules.selection(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, LocaleRules.SYSTEM)
    ) ?: LocaleRules.SYSTEM

    fun effectiveTag(context: Context): String = LocaleRules.resolve(selected(context), systemTags(context))
    fun currentLocale(context: Context): Locale = Locale.forLanguageTag(effectiveTag(context))
    fun formattingLocale(context: Context): Locale = Locale.forLanguageTag(LocaleRules.formattingTag(effectiveTag(context)))

    fun set(context: Context, option: String) {
        val canonical = requireNotNull(LocaleRules.selection(option)) { "Unsupported app locale" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, canonical).apply()
    }

    fun wrap(base: Context): Context {
        val source = original(base)
        val tag = LocaleRules.resolve(selected(base), systemTags(base))
        val locale = Locale.forLanguageTag(tag)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocales(if (tag == LocaleRules.FALLBACK) LocaleList(locale) else LocaleList(locale, Locale.US))
            setLayoutDirection(locale)
            // Explicit script direction also covers OEM ICU tables missing Western Punjabi.
            screenLayout = (screenLayout and Configuration.SCREENLAYOUT_LAYOUTDIR_MASK.inv()) or
                if (LocaleRules.isRtl(tag)) Configuration.SCREENLAYOUT_LAYOUTDIR_RTL else Configuration.SCREENLAYOUT_LAYOUTDIR_LTR
        }
        return LocalizedContext(base.createConfigurationContext(configuration), source)
    }
}
