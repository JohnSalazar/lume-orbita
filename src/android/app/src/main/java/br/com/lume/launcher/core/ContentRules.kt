package br.com.lume.launcher.core

import java.net.URI

data class SharedProgram(
    val key: String, val packageName: String, val title: String, val data: String, val live: Boolean = false,
    val posterUri: String = "", val thumbnailUri: String = "", val watchType: Int? = null,
    val positionMs: Long? = null, val durationMs: Long? = null, val engagementMs: Long = 0,
    val description: String = "", val season: String = "", val episode: String = ""
)

object ContentRules {
    const val MAX_ROWS = 200
    const val MAX_CARDS = 12
    fun label(value: String): String = value.filterNot { it.isISOControl() }.trim().take(100)

    fun description(long: String?, short: String?): String =
        metadataText(long, 240).ifEmpty { metadataText(short, 240) }

    fun displayNumber(value: String?): String = metadataText(value, 20)

    private fun metadataText(value: String?, limit: Int): String = value.orEmpty().asSequence()
        .filterNot { it.isISOControl() }.dropWhile { it.isWhitespace() }.take(limit).joinToString("").trimEnd()

    fun safeData(value: String): Boolean {
        if (value.length !in 1..4096 || value.any { it.isISOControl() }) return false
        return try {
            val uri = URI(value)
            val scheme = uri.scheme?.lowercase(java.util.Locale.ROOT) ?: return false
            scheme !in setOf("file", "content", "data", "javascript", "intent", "package", "market", "http") &&
                uri.rawUserInfo == null && uri.rawSchemeSpecificPart?.isNotBlank() == true
        } catch (_: java.net.URISyntaxException) { false }
    }

    fun inProgress(program: SharedProgram): Boolean = program.watchType == 0 && !program.live &&
        program.positionMs != null && program.durationMs != null && program.positionMs > 0 && program.positionMs < program.durationMs

    fun progressPercent(program: SharedProgram): Int? = if (inProgress(program))
        ((program.positionMs!!.toDouble() / program.durationMs!!) * 100).toInt().coerceIn(0, 99) else null

    fun playbackLabel(program: SharedProgram): String {
        if (program.live) return ""
        val duration = program.durationMs?.takeIf { it > 0 } ?: return ""
        val remaining = inProgress(program)
        val seconds = (if (remaining) duration - program.positionMs!! else duration) / 1000
        if (seconds == 0L) return if (remaining) "Faltam menos de 1 s" else "Menos de 1 s"
        val minutes = seconds / 60
        val remainder = seconds % 60
        val value = listOfNotNull(if (minutes > 0) "$minutes min" else null, if (remainder > 0) "$remainder s" else null).joinToString(" ")
        return if (remaining) "Faltam $value" else value
    }

    fun select(rows: List<SharedProgram>, installed: Set<String>, onlyInProgress: Boolean = false): List<SharedProgram> {
        val valid = rows.asSequence()
        .take(MAX_ROWS)
        .filter { it.packageName in installed && label(it.title).isNotEmpty() && safeData(it.data) }
        .filter { !onlyInProgress || inProgress(it) }
        .sortedByDescending { if (onlyInProgress) it.engagementMs else 0 }
        .map { it.copy(title = label(it.title)) }
        .distinctBy { it.packageName to it.data }
        .toList()
        if (onlyInProgress) return valid.take(MAX_CARDS)
        val publishers = valid.groupBy { it.packageName }.values.sortedByDescending { group ->
            group.any { it.posterUri.startsWith("https://") || it.thumbnailUri.startsWith("https://") }
        }
        return (0 until MAX_CARDS).asSequence().flatMap { index -> publishers.asSequence().mapNotNull { it.getOrNull(index) } }.take(MAX_CARDS).toList()
    }
}
