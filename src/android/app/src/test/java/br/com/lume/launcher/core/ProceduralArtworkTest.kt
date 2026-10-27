package br.com.lume.launcher.core

import org.junit.Assert.*
import org.junit.Test

class ProceduralArtworkTest {
    @Test fun allTwentyThemesProduceDistinctBoundedOriginalDrawings() {
        val scenes = (0L..19L).map { ProceduralArtwork.compose(it) }
        assertEquals(20, scenes.toSet().size)
        assertEquals(20, ProceduralSceneRules.themes.size)
        scenes.forEachIndexed { index, marks ->
            assertEquals(marks, ProceduralArtwork.compose(index.toLong()))
            assertTrue("$index ${marks.size}", marks.size in 20..3000)
            marks.forEach { mark ->
                assertTrue(mark.kind in setOf("ellipse", "curve", "line", "polygon"))
                assertTrue(mark.points.all { it.isFinite() && it in -200f..1500f })
                assertTrue(mark.opacity in 0f..1f)
                assertTrue(mark.stroke >= 0f && mark.stroke <= 15f)
                assertEquals(255, mark.color ushr 24)
                if (mark.kind == "ellipse") { assertEquals(4, mark.points.size); assertTrue(mark.points[2] > 0 && mark.points[3] > 0) }
                else if (mark.kind == "curve") { assertTrue(mark.points.size >= 8); assertEquals(0, (mark.points.size - 2) % 6) }
                else { assertTrue(mark.points.size >= 4); assertEquals(0, mark.points.size % 2) }
            }
        }
    }
    @Test fun newEntropyReallyChangesEveryThemeAndHandlesNegativeSeeds() {
        (0L..19L).forEach { assertNotEquals(ProceduralArtwork.compose(it), ProceduralArtwork.compose(it + 20)) }
        for (seed in listOf(Long.MIN_VALUE, -1L, Long.MAX_VALUE)) assertTrue(ProceduralArtwork.compose(seed).isNotEmpty())
    }
}
