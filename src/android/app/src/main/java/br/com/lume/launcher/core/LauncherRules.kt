package br.com.lume.launcher.core

import java.time.LocalDate
import kotlin.math.roundToInt

/** Policies shared by UI and storage; no Android runtime needed in tests. */
object LauncherRules {
    fun contentEntryTag(remembered: String?, available: Collection<String>, loading: Boolean): String? =
        remembered?.takeIf { it in available } ?: if (loading && remembered != null) null else available.firstOrNull()

    fun canEnterContent(pending: String?, selected: String, focusTag: String?, firstTag: String?): Boolean =
        pending != null && pending == selected && focusTag == "content-tab:$pending" &&
            (firstTag?.startsWith("program:") == true || firstTag?.startsWith("platform:") == true)

    fun contentFocusInsets(width: Int, height: Int, shadow: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && shadow >= 0)
        // A 1.06 focus scale grows 3% per edge; keep that growth and shadow inside the viewport.
        val cardWidth = contentCardWidth(height, width)
        fun inset(size: Int, limit: Int) = (shadow.toLong() + (size.toLong() * 3 + 99) / 100)
            .coerceAtMost((limit - 1L) / 2).toInt()
        return inset(cardWidth, width) to inset(height, height)
    }

    fun contentCardWidth(height: Int, viewportWidth: Int): Int {
        require(height > 0 && viewportWidth > 0)
        return ((height.toLong() * 16 + 4) / 9).coerceIn(1L, viewportWidth.toLong()).toInt()
    }
    fun focusBorderPhase(elapsedMs: Long): Float {
        require(elapsedMs >= 0)
        return (elapsedMs % 3600L) * 360f / 3600f
    }

    fun cinemaMode(value: String?): String = if (value in listOf("on", "auto")) value!! else "off"

    fun cinemaActive(mode: String?, hour: Int): Boolean {
        require(hour in 0..23)
        return when (cinemaMode(mode)) { "on" -> true; "auto" -> hour >= 20 || hour < 6; else -> false }
    }

    fun toggleCinema(mode: String?, hour: Int): String = if (cinemaActive(mode, hour)) "off" else "on"
    fun scheduleCinema(mode: String?, enabled: Boolean, hour: Int): String =
        if (enabled) "auto" else if (cinemaActive(mode, hour)) "on" else "off"

    fun transparency(base: Int, active: Boolean): Int = if (active) base.coerceIn(0, 28) else base.coerceIn(0, 100)
    fun reflections(base: Int, active: Boolean): Int = if (active) 0 else base.coerceIn(0, 100)
    fun animations(base: Boolean, active: Boolean): Boolean = base && !active

    fun fitIcon(size: Int, width: Int, height: Int, allowUpscale: Boolean): Pair<Int, Int> {
        require(size > 0 && width > 0 && height > 0)
        val scale = minOf(size.toDouble() / width, size.toDouble() / height, if (allowUpscale) Double.POSITIVE_INFINITY else 1.0)
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    fun popupBounds(availableWidth: Int, availableHeight: Int, preferredWidth: Int, margin: Int): Pair<Int, Int> {
        require(availableWidth > 0 && availableHeight > 0 && preferredWidth > 0 && margin >= 0)
        val width = (availableWidth.toLong() - 2L * margin).coerceAtLeast(1L).toInt()
        val height = (availableHeight.toLong() - 2L * margin).coerceAtLeast(1L).toInt()
        return minOf(preferredWidth, width) to height
    }

    fun sceneIndex(count: Int, daily: Boolean, manual: Int, date: LocalDate): Int {
        require(count > 0)
        return if (daily) Math.floorMod(date.toEpochDay(), count.toLong()).toInt() else manual.coerceIn(0, count - 1)
    }

    fun validFavorites(saved: List<String>, installed: Set<String>): List<String> =
        saved.filter { it in installed }.distinct()

    fun toggleFavorite(saved: List<String>, packageName: String): List<String> {
        require(packageName.isNotBlank())
        val unique = saved.distinct()
        return if (packageName in unique) unique.filterNot { it == packageName } else unique + packageName
    }

    fun favoriteSlots(availableWidth: Int, minSlotWidth: Int, gap: Int): Int {
        require(availableWidth > 0 && minSlotWidth > 0 && gap >= 0)
        return ((availableWidth.toLong() + gap) / (minSlotWidth.toLong() + gap)).coerceIn(1L, 8L).toInt()
    }

    fun moveFavorite(saved: List<String>, packageName: String, delta: Int): List<String> {
        val result = saved.distinct().toMutableList()
        val index = result.indexOf(packageName)
        if (index < 0) return result
        val destination = (index.toLong() + delta).coerceIn(0, result.lastIndex.toLong()).toInt()
        result.removeAt(index)
        result.add(destination, packageName)
        return result
    }

    fun canRequestUninstall(packageName: String, ownPackage: String, homePackage: String?, systemApp: Boolean): Boolean =
        packageName.isNotBlank() && packageName != ownPackage && !homePackage.isNullOrBlank() &&
            packageName != homePackage && !systemApp

    fun storeUrl(packageName: String): String? = if (packageName.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")))
        "https://play.google.com/store/apps/details?id=$packageName" else null
}
