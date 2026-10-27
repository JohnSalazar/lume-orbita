package br.com.lume.launcher.core

import org.junit.Assert.*
import org.junit.Test

class ProceduralSceneRulesTest {
    @Test fun allTwentyThemesHaveDistinctReadableMetadataAndHandleExtremeSeeds() {
        assertEquals(ProceduralSceneRules.themes.toSet(), (0L..19L).map { ProceduralSceneRules.theme(it) }.toSet())
        assertEquals(20, (0L..19L).map { ProceduralSceneRules.title(it) }.toSet().size)
        listOf(Long.MIN_VALUE, -7L, -1L, 0L, 6L, Long.MAX_VALUE).forEach {
            assertTrue(ProceduralSceneRules.themeIndex(it) in 0..19)
            assertTrue(ProceduralSceneRules.title(it).isNotBlank())
        }
    }

    @Test fun paletteIsOpaqueDeterministicAndCallerCannotMutateOtherScenes() {
        val distinct = mutableSetOf<List<Int>>()
        for (seed in 0L..19L) {
            val palette = ProceduralSceneRules.palette(seed)
            assertTrue(palette.size >= 3)
            assertTrue(palette.all { it ushr 24 == 255 })
            assertArrayEquals(palette, ProceduralSceneRules.palette(seed))
            distinct.add(palette.toList())
            val original = palette[0]
            palette[0] = 0
            assertEquals(original, ProceduralSceneRules.palette(seed)[0])
        }
        assertEquals(9, distinct.size)
    }

    @Test fun nextSeedAvoidsImmediateSeedAndThemeRepetitionIncludingWraparound() {
        val values = (-12L..12L).toList() + listOf(Long.MIN_VALUE, Long.MAX_VALUE)
        values.forEach { assertEquals(it, ProceduralSceneRules.nextSeed(null, it)) }
        values.forEach { previous -> values.forEach { entropy ->
            val next = ProceduralSceneRules.nextSeed(previous, entropy)
            assertNotEquals(previous, next)
            assertNotEquals(ProceduralSceneRules.theme(previous), ProceduralSceneRules.theme(next))
            assertEquals(next, ProceduralSceneRules.nextSeed(previous, entropy))
        } }
    }

    @Test fun sceneOrderCyclesWithoutDependingOnWallClockOrDeviceState() {
        var seed = 0L
        val visited = mutableSetOf<String>()
        repeat(20) { visited.add(ProceduralSceneRules.theme(seed)); seed = ProceduralSceneRules.nextSeed(seed, seed) }
        assertEquals(20, visited.size)
        assertEquals("ocean", ProceduralSceneRules.theme(seed))
    }

    @Test fun geometryIsDeterministicAndDifferentSeedsProvideRealSceneVariety() {
        val scenes = (0L..35L).map { seed ->
            val first = ProceduralSceneRules.geometry(1280, 720, seed)
            assertEquals(first, ProceduralSceneRules.geometry(1280, 720, seed))
            first
        }
        assertTrue(scenes.toSet().size > 24)
        assertNotEquals(scenes[0], scenes[20]) // Same theme still varies the composition.
    }

    @Test fun geometryFitsEveryRequestedViewportWithoutOverflowOrInvalidCoordinates() {
        val dimensions = listOf(1 to 1, 1280 to 720, 1920 to 1080, 3840 to 2160, 720 to 1280, 1 to 4096, Int.MAX_VALUE to 1, 1 to Int.MAX_VALUE)
        dimensions.forEach { (width, height) -> (0L..19L).forEach { seed ->
            val scene = ProceduralSceneRules.geometry(width, height, seed)
            assertEquals(4, scene.layers.size)
            scene.layers.forEach { points ->
                assertTrue(points.size >= 2)
                assertEquals(0f, points.first().x, 0f)
                assertEquals(width.toFloat(), points.last().x, 0f)
                assertTrue(points.zipWithNext().all { (a, b) -> a.x <= b.x })
                points.forEach { point ->
                    assertTrue(point.x.isFinite() && point.x in 0f..width.toFloat())
                    assertTrue(point.y.isFinite() && point.y in 0f..height.toFloat())
                }
            }
            assertTrue(scene.radius > 0 && scene.radius.isFinite())
            assertTrue(scene.sun.x - scene.radius >= 0 && scene.sun.x + scene.radius <= width.toFloat())
            assertTrue(scene.sun.y - scene.radius >= 0 && scene.sun.y + scene.radius <= height.toFloat())
            assertEquals(seed == 5L, scene.stars.isNotEmpty())
            scene.stars.forEach { point -> assertTrue(point.x in 0f..width.toFloat() && point.y in 0f..height.toFloat()) }
        } }
    }

    @Test fun sameSceneScalesProportionallyFrom720pTo4k() {
        (0L..19L).forEach { seed ->
            val small = ProceduralSceneRules.geometry(1280, 720, seed)
            val large = ProceduralSceneRules.geometry(3840, 2160, seed)
            small.layers.zip(large.layers).forEach { (a, b) -> a.zip(b).forEach { (p, q) ->
                assertEquals(p.x, q.x / 3f, 0.01f); assertEquals(p.y, q.y / 3f, 0.01f)
            } }
            assertEquals(small.sun.x, large.sun.x / 3f, 0.01f)
            assertEquals(small.sun.y, large.sun.y / 3f, 0.01f)
            assertEquals(small.radius, large.radius / 3f, 0.01f)
        }
    }

    @Test fun invalidBoundsAreRejectedBeforeGeometryAllocation() {
        listOf(0 to 1, 1 to 0, -1 to 1, 1 to -1, Int.MIN_VALUE to 720, 1280 to Int.MIN_VALUE).forEach { (w, h) ->
            assertThrows(IllegalArgumentException::class.java) { ProceduralSceneRules.geometry(w, h, 0) }
        }
    }
}
