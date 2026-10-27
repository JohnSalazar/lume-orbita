package br.com.lume.launcher

import android.content.Context
import br.com.lume.launcher.core.ContentRules
import br.com.lume.launcher.core.SharedProgram
import java.text.NumberFormat

/** Presentation only: publisher metadata and persistent identifiers are never translated. */
object LocalizedText {
    private val sceneTitleIds = intArrayOf(R.string.common_scene_01, R.string.common_scene_02, R.string.common_scene_03, R.string.common_scene_04, R.string.common_scene_05, R.string.common_scene_06, R.string.common_scene_07, R.string.common_scene_08, R.string.common_scene_09, R.string.common_scene_10, R.string.common_scene_11, R.string.common_scene_12, R.string.common_scene_13, R.string.common_scene_14, R.string.common_scene_15, R.string.common_scene_16, R.string.common_scene_17, R.string.common_scene_18, R.string.common_scene_19, R.string.common_scene_20, R.string.common_scene_21, R.string.common_scene_22, R.string.common_scene_23, R.string.common_scene_24, R.string.common_scene_25, R.string.common_scene_26, R.string.common_scene_27, R.string.common_scene_28, R.string.common_scene_29, R.string.common_scene_30)
    fun sceneTitle(context: Context, index: Int): String = context.getString(sceneTitleIds[index])
    private val sceneThemeIds = intArrayOf(R.string.common_theme_01, R.string.common_theme_02, R.string.common_theme_03, R.string.common_theme_04, R.string.common_theme_05, R.string.common_theme_06, R.string.common_theme_07, R.string.common_theme_08, R.string.common_theme_09, R.string.common_theme_10, R.string.common_theme_11, R.string.common_theme_12, R.string.common_theme_13, R.string.common_theme_14, R.string.common_theme_15, R.string.common_theme_16, R.string.common_theme_17, R.string.common_theme_18, R.string.common_theme_19, R.string.common_theme_20, R.string.common_theme_21, R.string.common_theme_22, R.string.common_theme_23, R.string.common_theme_24, R.string.common_theme_25, R.string.common_theme_26, R.string.common_theme_27, R.string.common_theme_28, R.string.common_theme_29, R.string.common_theme_30)
    fun sceneTheme(context: Context, index: Int): String = context.getString(sceneThemeIds[index])
    private val photoTitleIds = intArrayOf(R.string.common_photo_01, R.string.common_photo_02, R.string.common_photo_03, R.string.common_photo_04, R.string.common_photo_05, R.string.common_photo_06, R.string.common_photo_07, R.string.common_photo_08, R.string.common_photo_09, R.string.common_photo_10, R.string.common_photo_11, R.string.common_photo_12, R.string.common_photo_13, R.string.common_photo_14)
    fun photoTitle(context: Context, index: Int): String = context.getString(photoTitleIds[index])
    private val proceduralTitleIds = intArrayOf(R.string.common_procedural_01, R.string.common_procedural_02, R.string.common_procedural_03, R.string.common_procedural_04, R.string.common_procedural_05, R.string.common_procedural_06, R.string.common_procedural_07, R.string.common_procedural_08, R.string.common_procedural_09, R.string.common_procedural_10, R.string.common_procedural_11, R.string.common_procedural_12, R.string.common_procedural_13, R.string.common_procedural_14, R.string.common_procedural_15, R.string.common_procedural_16, R.string.common_procedural_17, R.string.common_procedural_18, R.string.common_procedural_19, R.string.common_procedural_20)
    fun proceduralTitle(context: Context, index: Int): String = context.getString(proceduralTitleIds[index])

    fun playback(context: Context, program: SharedProgram): String {
        if (program.live) return ""
        val duration = program.durationMs?.takeIf { it > 0 } ?: return ""
        val remaining = ContentRules.inProgress(program)
        val seconds = (if (remaining) duration - program.positionMs!! else duration) / 1000
        val number = NumberFormat.getIntegerInstance(AppLocales.formattingLocale(context))
        val value = when {
            seconds == 0L -> context.getString(R.string.common_less_second)
            seconds < 60 -> context.getString(R.string.common_seconds, number.format(seconds))
            seconds % 60 == 0L -> context.getString(R.string.common_minutes, number.format(seconds / 60))
            else -> context.getString(R.string.common_minutes_seconds, number.format(seconds / 60), number.format(seconds % 60))
        }
        return if (remaining) context.getString(R.string.common_remaining, value) else value
    }
}
