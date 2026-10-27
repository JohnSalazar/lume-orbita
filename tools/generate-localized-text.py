from pathlib import Path
root = Path(__file__).resolve().parents[1]
path = root/'src/android/app/src/main/java/br/com/lume/launcher/LocalizedText.kt'
lines = ['package br.com.lume.launcher', '', 'import android.content.Context', 'import br.com.lume.launcher.core.ContentRules', 'import br.com.lume.launcher.core.SharedProgram', 'import java.text.NumberFormat', '', '/** Presentation only: publisher metadata and persistent identifiers are never translated. */', 'object LocalizedText {']
for name, prefix, count in [('sceneTitle','scene',30),('sceneTheme','theme',30),('photoTitle','photo',14),('proceduralTitle','procedural',20)]:
    lines += [f'    private val {name}Ids = intArrayOf(' + ', '.join(f'R.string.common_{prefix}_{i:02}' for i in range(1,count+1)) + ')', f'    fun {name}(context: Context, index: Int): String = context.getString({name}Ids[index])']
lines += ['''
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
}''']
path.write_text('\n'.join(lines)+'\n',encoding='utf-8')
