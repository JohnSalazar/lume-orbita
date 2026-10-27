package br.com.lume.launcher

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.os.CancellationSignal
import br.com.lume.launcher.core.ContentRules
import br.com.lume.launcher.core.SharedProgram
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal object TvContentWorker {
    internal val executor = ThreadPoolExecutor(0, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(1))
    fun submit(task: Runnable): java.util.concurrent.Future<*> { executor.purge(); return executor.submit(task) }
    fun purgeCanceled() = executor.purge()
}

/** Remote cancellation is best-effort; it must not occupy the UI or deadline scheduler. */
internal object TvContentCancellation {
    internal val executor = ThreadPoolExecutor(0, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(1))

    fun tryCancel(action: () -> Unit): Boolean = try {
        executor.execute { try { action() } catch (_: Exception) { /* Logical cancellation already detached the request. */ } }
        true
    } catch (_: RejectedExecutionException) {
        false // A stuck cancellation cannot grow the worker or queue.
    }
}

/** Shared recommendations only; never writes the provider or reads streaming accounts. */
class TvContentCatalog(context: Context) {
    private val resolver = context.contentResolver
    data class Snapshot(val next: List<SharedProgram>, val suggestions: List<SharedProgram>, val unavailable: Boolean)

    fun load(installed: Set<String>, signal: CancellationSignal): Snapshot {
        var unavailable = false
        fun read(watchNext: Boolean): List<SharedProgram> = try {
            val uri = if (watchNext) TvContract.WatchNextPrograms.CONTENT_URI else TvContract.PreviewPrograms.CONTENT_URI
            val result = mutableListOf<SharedProgram>()
            val projection = mutableListOf("_id", "package_name", "title", "intent_uri", "browsable", "live", "poster_art_uri", "thumbnail_uri")
            if (watchNext) projection.addAll(listOf("watch_next_type", "last_playback_position_millis", "duration_millis", "last_engagement_time_utc_millis"))
            val order = if (watchNext) "last_engagement_time_utc_millis DESC" else null
            val detailedProjection = projection + listOf("short_description", "long_description", "season_display_number", "episode_display_number") +
                if (watchNext) emptyList() else listOf("duration_millis")
            val queried = try {
                resolver.query(uri, detailedProjection.toTypedArray(), null, null, order, signal)
            } catch (_: IllegalArgumentException) {
                signal.throwIfCanceled()
                resolver.query(uri, projection.toTypedArray(), null, null, order, signal)
            } catch (_: android.database.SQLException) {
                signal.throwIfCanceled()
                resolver.query(uri, projection.toTypedArray(), null, null, order, signal)
            }
            queried?.use { cursor ->
                val shortIndex = cursor.getColumnIndex("short_description")
                val longIndex = cursor.getColumnIndex("long_description")
                val seasonIndex = cursor.getColumnIndex("season_display_number")
                val episodeIndex = cursor.getColumnIndex("episode_display_number")
                val durationIndex = cursor.getColumnIndex("duration_millis")
                fun optionalString(index: Int): String? = if (index < 0 || cursor.isNull(index)) null else cursor.getString(index)
                var scanned = 0
                while (scanned++ < ContentRules.MAX_ROWS && cursor.moveToNext()) {
                    signal.throwIfCanceled()
                    if (cursor.getInt(4) == 0) continue
                    val pkg = cursor.getString(1) ?: continue
                    if (pkg !in installed) continue
                    val title = ContentRules.label(cursor.getString(2) ?: "")
                    if (title.isEmpty()) continue
                    val raw = cursor.getString(3) ?: continue
                    if (raw.length > 8192) continue
                    val parsed = try { Intent.parseUri(raw, Intent.URI_INTENT_SCHEME) } catch (_: java.net.URISyntaxException) { continue }
                    // Ignore all supplied components, extras, flags, selector and fallback URLs.
                    val data = parsed.dataString ?: continue
                    fun longOrNull(index: Int) = if (cursor.isNull(index)) null else cursor.getLong(index)
                    result.add(SharedProgram("${if (watchNext) "next" else "preview"}:${cursor.getLong(0)}", pkg, title, data, cursor.getInt(5) == 1,
                        cursor.getString(6)?.takeIf { it.length <= 4096 } ?: "", cursor.getString(7)?.takeIf { it.length <= 4096 } ?: "",
                        if (watchNext && !cursor.isNull(8)) cursor.getInt(8) else null,
                        if (watchNext) longOrNull(9) else null, if (durationIndex >= 0) longOrNull(durationIndex) else null,
                        if (watchNext) longOrNull(11) ?: 0 else 0,
                        ContentRules.description(optionalString(longIndex), optionalString(shortIndex)),
                        ContentRules.displayNumber(optionalString(seasonIndex)), ContentRules.displayNumber(optionalString(episodeIndex))))
                }
            }
            ContentRules.select(result, installed, onlyInProgress = watchNext)
        } catch (_: SecurityException) { unavailable = true; emptyList() }
          catch (_: android.database.SQLException) { unavailable = true; emptyList() }
          catch (_: IllegalArgumentException) { unavailable = true; emptyList() }
        val next = read(true)
        signal.throwIfCanceled()
        val suggestions = read(false)
        return Snapshot(next, suggestions, unavailable)
    }
}
