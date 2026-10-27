package br.com.lume.launcher.core

import org.junit.Assert.*
import org.junit.Test

class ContentRulesTest {
    private val continuing = SharedProgram("c", "tv.test", "Título", "app:watch", watchType = 0, positionMs = 25, durationMs = 100)

    @Test fun optionalMetadataDefaultsPreserveExistingCallers() {
        assertEquals("", continuing.description)
        assertEquals("", continuing.season)
        assertEquals("", continuing.episode)
        assertEquals(25, ContentRules.progressPercent(continuing))
        assertTrue(ContentRules.inProgress(continuing))
    }

    @Test fun descriptionPrefersCleanLongTextAndFallsBackToShortWhenEmpty() {
        assertEquals("Longa descrição", ContentRules.description(" \nLonga descrição\u0000\t ", "Curta"))
        assertEquals("Curta", ContentRules.description(null, " Curta "))
        assertEquals("Curta", ContentRules.description(" \n\t\u007f ", " Curta "))
        assertEquals("", ContentRules.description(null, null))
        assertEquals("", ContentRules.description(" \t ", "\n\u0000"))
        assertEquals("<b>Texto</b>", ContentRules.description("<b>Texto</b>", null))
    }

    @Test fun optionalMetadataIsBoundedBeforeRetention() {
        assertEquals("x".repeat(240), ContentRules.description("x".repeat(500), null))
        assertEquals("y".repeat(240), ContentRules.description(" ", "y".repeat(500)))
        assertEquals("z".repeat(20), ContentRules.displayNumber("z".repeat(100)))
        assertEquals("x".repeat(240), ContentRules.description(" ".repeat(500) + "x".repeat(500), null))
    }

    @Test fun displayNumbersRemainOptionalNonNumericText() {
        assertEquals("Specials", ContentRules.displayNumber(" \tSpecials\u0000 "))
        assertEquals("12B", ContentRules.displayNumber("12B"))
        assertEquals("", ContentRules.displayNumber(null))
        assertEquals("", ContentRules.displayNumber(" \n\u007f "))
    }

    @Test fun playbackLabelRequiresKnownPositiveDurationAndNonLiveContent() {
        listOf(continuing.copy(durationMs = null), continuing.copy(durationMs = 0),
            continuing.copy(durationMs = -1), continuing.copy(live = true)).forEach {
            assertEquals("", ContentRules.playbackLabel(it))
        }
    }

    @Test fun playbackLabelUsesWholeSecondsAndMinutesWithoutRoundingUp() {
        val program = continuing.copy(watchType = null, positionMs = null)
        assertEquals("Menos de 1 s", ContentRules.playbackLabel(program.copy(durationMs = 999)))
        assertEquals("1 s", ContentRules.playbackLabel(program.copy(durationMs = 1000)))
        assertEquals("59 s", ContentRules.playbackLabel(program.copy(durationMs = 59999)))
        assertEquals("1 min", ContentRules.playbackLabel(program.copy(durationMs = 60000)))
        assertEquals("2 min 5 s", ContentRules.playbackLabel(program.copy(durationMs = 125000)))
    }

    @Test fun playbackLabelShowsRemainingOnlyForStrictIncompleteContinueProgress() {
        assertEquals("Faltam 1 min", ContentRules.playbackLabel(continuing.copy(positionMs = 65000, durationMs = 125000)))
        assertEquals("Faltam menos de 1 s", ContentRules.playbackLabel(continuing.copy(positionMs = 1, durationMs = 999)))
        listOf(continuing.copy(watchType = null), continuing.copy(positionMs = null), continuing.copy(positionMs = -1),
            continuing.copy(positionMs = 0), continuing.copy(positionMs = 100), continuing.copy(positionMs = 101)).forEach {
            assertEquals("Menos de 1 s", ContentRules.playbackLabel(it))
        }
    }

    @Test fun playbackLabelHandlesLongMaximumWithoutOverflow() {
        assertEquals("153722867280912 min 55 s", ContentRules.playbackLabel(continuing.copy(watchType = null, durationMs = Long.MAX_VALUE)))
        assertEquals("Faltam menos de 1 s", ContentRules.playbackLabel(continuing.copy(positionMs = Long.MAX_VALUE - 1, durationMs = Long.MAX_VALUE)))
    }

    @Test fun continuingRequiresAnExplicitContinueTypeAndKnownIncompleteProgress() {
        assertTrue(ContentRules.inProgress(continuing))
        listOf(continuing.copy(watchType = null), continuing.copy(watchType = 1), continuing.copy(watchType = 2), continuing.copy(watchType = 3),
            continuing.copy(live = true), continuing.copy(positionMs = null), continuing.copy(durationMs = null),
            continuing.copy(positionMs = 0), continuing.copy(positionMs = -1), continuing.copy(durationMs = 0), continuing.copy(durationMs = -1),
            continuing.copy(positionMs = 100), continuing.copy(positionMs = 101)).forEach { assertFalse(it.toString(), ContentRules.inProgress(it)); assertNull(ContentRules.progressPercent(it)) }
    }

    @Test fun progressDoesNotOverflowOrTurnAnIncompleteItemIntoCompleted() {
        assertEquals(25, ContentRules.progressPercent(continuing))
        assertEquals(0, ContentRules.progressPercent(continuing.copy(positionMs = 1, durationMs = Long.MAX_VALUE)))
        assertEquals(99, ContentRules.progressPercent(continuing.copy(positionMs = Long.MAX_VALUE - 1, durationMs = Long.MAX_VALUE)))
    }

    @Test fun continueSelectionFiltersBeforeLimitingAndShowsMostRecentFirst() {
        val rows = listOf(continuing.copy(watchType = 1)) + (1L..20L).map { continuing.copy(key = "$it", data = "app:watch$it", engagementMs = it) }
        val selected = ContentRules.select(rows, setOf("tv.test"), onlyInProgress = true)
        assertEquals(12, selected.size)
        assertEquals("20", selected.first().key)
        assertTrue(selected.all { ContentRules.inProgress(it) })
        assertEquals(12, ContentRules.select(rows, setOf("tv.test")).size)
    }

    @Test fun onlyInstalledPublishersWithUsableDataAppear() {
        val good = SharedProgram("1", "tv.test", "  Episódio\n novo  ", "stream://episode/1")
        val result = ContentRules.select(listOf(good, good.copy(key = "2"), good.copy(packageName = "gone"), good.copy(title = "\n"), good.copy(data = "file:///x")), setOf("tv.test"))
        assertEquals(listOf(good.copy(title = "Episódio novo")), result)
        assertTrue(ContentRules.select(listOf(good), emptySet()).isEmpty())
    }
    @Test fun untrustedDataCannotBecomeAFileGrantOrExecutableIntent() {
        listOf("", "https://a/\n", "relative/path", "https://user:pass@a/x", "file:///a", "content://x/y", "data:text/html,x", "javascript:go()", "intent://x#Intent;end", "package:tv.test", "market://details?id=x", "http://a/x", "bad uri", "https://a/" + "x".repeat(4096), "custom:").forEach { assertFalse(it, ContentRules.safeData(it)) }
        listOf("https://example.org/watch?id=1", "netflix://watch/1", "app:episode").forEach { assertTrue(it, ContentRules.safeData(it)) }
    }
    @Test fun scansAndOutputRemainBounded() {
        val rows = (0..300).map { SharedProgram("$it", "tv.test", "Title", "app:item$it") }
        assertEquals(12, ContentRules.select(rows, setOf("tv.test")).size)
        assertTrue(ContentRules.select(rows.take(200).map { it.copy(packageName = "gone") } + rows.last(), setOf("tv.test")).isEmpty())
        assertEquals(100, ContentRules.label("x".repeat(101)).length)
        assertEquals("", ContentRules.label(" \n\t "))
        assertFalse(ContentRules.safeData("custom:/[invalid"))
    }

    @Test fun suggestionsMixPublishersAndPreferThoseProvidingArtwork() {
        val rows = (1..20).map { continuing.copy(key = "a$it", data = "app:item$it", packageName = "a") } +
            (1..20).map { continuing.copy(key = "b$it", data = "app:item$it", packageName = "b", posterUri = "https://example.org/art") } +
            listOf(continuing.copy(key = "c", packageName = "c", thumbnailUri = "https://example.org/thumb"))
        val selected = ContentRules.select(rows, setOf("a", "b", "c"))
        assertEquals(listOf("b", "c", "a"), selected.take(3).map { it.packageName })
        assertEquals(12, selected.size)
        assertEquals(listOf("b1", "b2"), selected.filter { it.packageName == "b" }.take(2).map { it.key })
    }
}
