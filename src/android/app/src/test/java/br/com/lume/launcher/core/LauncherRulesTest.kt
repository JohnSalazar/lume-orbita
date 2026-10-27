package br.com.lume.launcher.core

import java.net.URI
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test


class LauncherRulesTest {
    @Test fun contentEntryRestoresAvailableSelectionAndWaitsForMissingSelectionWhileLoading() {
        val cards = listOf("program:next:1", "program:next:2")
        assertEquals(cards[1], LauncherRules.contentEntryTag(cards[1], cards, false))
        assertEquals(cards[1], LauncherRules.contentEntryTag(cards[1], cards, true))
        assertNull(LauncherRules.contentEntryTag("program:next:gone", cards, true))
        assertEquals(cards[0], LauncherRules.contentEntryTag("program:next:gone", cards, false))
        assertEquals(cards[0], LauncherRules.contentEntryTag(null, cards, false))
        assertEquals(cards[0], LauncherRules.contentEntryTag(null, cards, true))
        assertNull(LauncherRules.contentEntryTag(cards[1], emptyList(), true))
        assertNull(LauncherRules.contentEntryTag(cards[1], emptyList(), false))
        assertNull(LauncherRules.contentEntryTag(null, emptyList(), false))
        assertEquals("platform:app", LauncherRules.contentEntryTag("invalid", listOf("platform:app"), false))
    }
    @Test fun enteringAHeaderTargetsOnlyAnAvailableCardInTheRequestedSection() {
        assertTrue(LauncherRules.canEnterContent("next", "next", "content-tab:next", "program:1"))
        assertTrue(LauncherRules.canEnterContent("suggestions", "suggestions", "content-tab:suggestions", "platform:app"))
        assertFalse(LauncherRules.canEnterContent(null, "next", "content-tab:next", "program:1"))
        assertFalse(LauncherRules.canEnterContent("next", "suggestions", "content-tab:next", "program:1"))
        assertFalse(LauncherRules.canEnterContent("next", "next", null, "program:1"))
        assertFalse(LauncherRules.canEnterContent("next", "next", "app:favorite", "program:1"))
        assertFalse(LauncherRules.canEnterContent("next", "next", "content-tab:next", null))
        assertFalse(LauncherRules.canEnterContent("next", "next", "content-tab:next", "content-connect"))
        assertFalse(LauncherRules.canEnterContent("next", "next", "content-tab:next", "other"))
    }
    @Test fun focusGuttersContainScaledContentAt720pAnd4k() {
        listOf(740 to 175, 2220 to 525, 3840 to 2160).forEach { (width, height) ->
            val (horizontal, vertical) = LauncherRules.contentFocusInsets(width, height, 8)
            assertTrue(horizontal >= LauncherRules.contentCardWidth(height, width) * 0.03 + 8)
            assertTrue(vertical >= height * 0.03 + 8)
            assertTrue(horizontal * 2 < width); assertTrue(vertical * 2 < height)
        }
    }
    @Test fun focusGuttersLeaveAtLeastOnePixelAndDoNotOverflow() {
        assertEquals(0 to 0, LauncherRules.contentFocusInsets(1, 1, 8))
        val (horizontal, vertical) = LauncherRules.contentFocusInsets(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
        assertTrue(horizontal.toLong() * 2 < Int.MAX_VALUE)
        assertTrue(vertical.toLong() * 2 < Int.MAX_VALUE)
        listOf(Triple(0, 1, 0), Triple(1, 0, 0), Triple(1, 1, -1)).forEach { (w, h, s) ->
            assertThrows(IllegalArgumentException::class.java) { LauncherRules.contentFocusInsets(w, h, s) }
        }
    }
    @Test fun contentCardsUseLandscapeAspectWithoutOverflowingTheViewport() {
        assertEquals(320, LauncherRules.contentCardWidth(180, 1280))
        assertEquals(1280, LauncherRules.contentCardWidth(720, 1920))
        assertEquals(3840, LauncherRules.contentCardWidth(2160, 3840))
        assertEquals(500, LauncherRules.contentCardWidth(900, 500))
        assertEquals(1, LauncherRules.contentCardWidth(1, 1))
        assertEquals(2, LauncherRules.contentCardWidth(1, Int.MAX_VALUE))
        assertEquals(Int.MAX_VALUE, LauncherRules.contentCardWidth(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test fun contentCardGeometryRejectsUnmeasuredOrInvalidBounds() {
        listOf(0 to 720, 720 to 0, -1 to 1920, 720 to -1).forEach { (height, width) ->
            assertThrows(IllegalArgumentException::class.java) { LauncherRules.contentCardWidth(height, width) }
        }
    }
    @Test fun focusBorderHasSmoothThreePointSixSecondCycle() {
        assertEquals(0f, LauncherRules.focusBorderPhase(0), 0f)
        assertEquals(90f, LauncherRules.focusBorderPhase(900), 0f)
        assertEquals(180f, LauncherRules.focusBorderPhase(1800), 0f)
        assertEquals(0f, LauncherRules.focusBorderPhase(3600), 0f)
        assertEquals(90f, LauncherRules.focusBorderPhase(4500), 0f)
    }

    @Test fun focusBorderPhaseDoesNotOverflowAfterLongUptime() {
        assertEquals(180.7f, LauncherRules.focusBorderPhase(Long.MAX_VALUE), 0.0001f)
        assertTrue(LauncherRules.focusBorderPhase(3599) < 360f)
    }

    @Test fun focusBorderRejectsNegativeElapsedTime() {
        assertThrows(IllegalArgumentException::class.java) { LauncherRules.focusBorderPhase(-1) }
        assertThrows(IllegalArgumentException::class.java) { LauncherRules.focusBorderPhase(Long.MIN_VALUE) }
    }

    private val nightHours = setOf(0, 1, 2, 3, 4, 5, 20, 21, 22, 23)
    private val packages = listOf("com.example.first", "com.example.second", "com.example.third")

    @Test fun cinemaModeDefaultsOffAndAcceptsOnlyExactModes() {
        for (value in listOf(null, "", "ON", "true", " auto ", "unknown", "off")) {
            assertEquals("off", LauncherRules.cinemaMode(value))
        }
        assertEquals("on", LauncherRules.cinemaMode("on"))
        assertEquals("auto", LauncherRules.cinemaMode("auto"))
    }

    @Test fun cinemaScheduleMatchesEveryLocalHourIncludingBoundaries() {
        for (hour in 0..23) {
            assertEquals("Local hour $hour", hour in nightHours, LauncherRules.cinemaActive("auto", hour))
            assertTrue(LauncherRules.cinemaActive("on", hour))
            assertFalse(LauncherRules.cinemaActive("off", hour))
            assertFalse(LauncherRules.cinemaActive(null, hour))
            assertFalse(LauncherRules.cinemaActive("invalid", hour))
        }
        assertFalse(LauncherRules.cinemaActive("auto", 19))
        assertTrue(LauncherRules.cinemaActive("auto", 20))
        assertTrue(LauncherRules.cinemaActive("auto", 5))
        assertFalse(LauncherRules.cinemaActive("auto", 6))
    }

    @Test fun cinemaRejectsHoursOutsideTheCivilDay() {
        for (hour in listOf(-1, 24, Int.MIN_VALUE, Int.MAX_VALUE)) {
            for (mode in listOf(null, "off", "on", "auto")) {
                assertThrows(IllegalArgumentException::class.java) { LauncherRules.cinemaActive(mode, hour) }
                assertThrows(IllegalArgumentException::class.java) { LauncherRules.toggleCinema(mode, hour) }
            }
        }
    }

    @Test fun cinemaManualOverrideRemainsStableAfterTheClockChanges() {
        val manualOff = LauncherRules.toggleCinema("auto", 21)
        val manualOn = LauncherRules.toggleCinema("auto", 12)
        assertEquals("off", manualOff)
        assertEquals("on", manualOn)
        for (hour in 0..23) {
            assertFalse(LauncherRules.cinemaActive(manualOff, hour))
            assertTrue(LauncherRules.cinemaActive(manualOn, hour))
        }
        assertEquals("on", LauncherRules.toggleCinema("off", 22))
        assertEquals("off", LauncherRules.toggleCinema("on", 12))
        assertEquals("on", LauncherRules.toggleCinema(null, 12))
    }

    @Test fun cinemaCanEnableSchedulingFromAnyModeAndPreserveStateWhenDisabled() {
        for (mode in listOf(null, "off", "on", "auto", "invalid")) {
            for (hour in 0..23) {
                assertEquals("auto", LauncherRules.scheduleCinema(mode, true, hour))
                val manual = LauncherRules.scheduleCinema(mode, false, hour)
                assertTrue(manual in listOf("on", "off"))
                assertEquals(LauncherRules.cinemaActive(mode, hour), LauncherRules.cinemaActive(manual, hour))
            }
        }
    }

    @Test fun disablingCinemaScheduleDoesNotChangeTheEffectiveAppearance() {
        for (hour in listOf(0, 5, 6, 12, 19, 20, 23)) {
            val before = LauncherRules.cinemaActive("auto", hour)
            val manual = LauncherRules.scheduleCinema("auto", false, hour)
            val after = LauncherRules.cinemaActive(manual, hour)
            assertEquals(LauncherRules.transparency(74, before), LauncherRules.transparency(74, after))
            assertEquals(LauncherRules.reflections(82, before), LauncherRules.reflections(82, after))
            assertEquals(LauncherRules.animations(true, before), LauncherRules.animations(true, after))
        }
    }

    @Test fun cinemaEffectsRespectBoundsAndRestoreTheUnchangedBase() {
        for ((base, normal, cinema) in listOf(
            Triple(Int.MIN_VALUE, 0, 0), Triple(-1, 0, 0), Triple(0, 0, 0),
            Triple(20, 20, 20), Triple(28, 28, 28), Triple(29, 29, 28),
            Triple(74, 74, 28), Triple(100, 100, 28), Triple(101, 100, 28),
            Triple(Int.MAX_VALUE, 100, 28)
        )) {
            assertEquals(normal, LauncherRules.transparency(base, false))
            assertEquals(cinema, LauncherRules.transparency(base, true))
            assertEquals(normal, LauncherRules.reflections(base, false))
            assertEquals(0, LauncherRules.reflections(base, true))
            assertEquals(normal, LauncherRules.transparency(base, false))
            assertEquals(normal, LauncherRules.reflections(base, false))
        }
        assertTrue(LauncherRules.animations(true, false))
        assertFalse(LauncherRules.animations(true, true))
        assertFalse(LauncherRules.animations(false, false))
        assertFalse(LauncherRules.animations(false, true))
    }

    @Test fun dailyScenesHaveKnownEpochFixturesIncludingNegativeDays() {
        assertEquals(0, LauncherRules.sceneIndex(30, true, 0, LocalDate.of(1970, 1, 1)))
        assertEquals(1, LauncherRules.sceneIndex(30, true, 0, LocalDate.of(1970, 1, 2)))
        assertEquals(29, LauncherRules.sceneIndex(30, true, 0, LocalDate.of(1969, 12, 31)))
        assertEquals(7, LauncherRules.sceneIndex(30, true, 0, LocalDate.of(2000, 1, 1)))
    }

    @Test fun dailyScenesVisitAllThirtyBeforeRepeatingAcrossMonthAndYearChanges() {
        for (start in listOf(LocalDate.of(2024, 2, 20), LocalDate.of(2026, 12, 20), LocalDate.of(1969, 12, 20))) {
            val indexes = (0L..30L).map { offset -> LauncherRules.sceneIndex(30, true, 0, start.plusDays(offset)) }
            assertEquals((0..29).toSet(), indexes.take(30).toSet())
            assertEquals(indexes.first(), indexes.last())
        }
    }

    @Test fun dailyScenesUseLocalDatesThroughTwentyThreeAndTwentyFiveHourDays() {
        val zone = ZoneId.of("America/New_York")
        for ((date, expectedHours) in listOf(LocalDate.of(2024, 3, 10) to 23L, LocalDate.of(2024, 11, 3) to 25L)) {
            val start = date.atStartOfDay(zone)
            val next = date.plusDays(1).atStartOfDay(zone)
            val late = date.atTime(23, 59).atZone(zone)
            assertEquals(expectedHours, Duration.between(start.toInstant(), next.toInstant()).toHours())
            assertEquals(
                LauncherRules.sceneIndex(30, true, 0, start.toLocalDate()),
                LauncherRules.sceneIndex(30, true, 0, late.toLocalDate())
            )
            assertNotEquals(
                LauncherRules.sceneIndex(30, true, 0, start.toLocalDate()),
                LauncherRules.sceneIndex(30, true, 0, next.toLocalDate())
            )
        }
    }

    @Test fun manualSceneSelectionIsDateIndependentAndClamped() {
        for (date in listOf(LocalDate.of(1969, 12, 31), LocalDate.of(2026, 10, 25))) {
            assertEquals(0, LauncherRules.sceneIndex(30, false, Int.MIN_VALUE, date))
            assertEquals(0, LauncherRules.sceneIndex(30, false, 0, date))
            assertEquals(17, LauncherRules.sceneIndex(30, false, 17, date))
            assertEquals(29, LauncherRules.sceneIndex(30, false, Int.MAX_VALUE, date))
            assertEquals(0, LauncherRules.sceneIndex(1, true, Int.MAX_VALUE, date))
            assertEquals(0, LauncherRules.sceneIndex(1, false, Int.MAX_VALUE, date))
        }
    }

    @Test fun sceneSelectionRejectsAnEmptyOrNegativeCatalog() {
        for (count in listOf(0, -1, Int.MIN_VALUE)) {
            for (daily in listOf(true, false)) {
                assertThrows(IllegalArgumentException::class.java) {
                    LauncherRules.sceneIndex(count, daily, 0, LocalDate.of(2026, 10, 25))
                }
            }
        }
    }

    @Test fun favoritesFilterRemovedAppsAndDuplicatesWhilePreservingOrderAndInput() {
        val saved = mutableListOf(packages[1], "com.example.removed", packages[0], packages[1], packages[2])
        val original = saved.toList()
        assertEquals(listOf(packages[1], packages[0]), LauncherRules.validFavorites(saved, packages.take(2).toSet()))
        assertEquals(original, saved)
        assertEquals(emptyList<String>(), LauncherRules.validFavorites(saved, emptySet()))
        assertEquals(emptyList<String>(), LauncherRules.validFavorites(emptyList(), packages.toSet()))
    }

    @Test fun movingAFavoriteChangesOnlyItsPositionWithoutMutatingInput() {
        val saved = packages.toMutableList()
        assertEquals(listOf(packages[1], packages[0], packages[2]), LauncherRules.moveFavorite(saved, packages[1], -1))
        assertEquals(listOf(packages[0], packages[2], packages[1]), LauncherRules.moveFavorite(saved, packages[1], 1))
        assertEquals(packages, LauncherRules.moveFavorite(saved, packages[1], 0))
        assertEquals(packages, saved)
    }

    @Test fun movingFavoritesHandlesDuplicatesAbsentEntriesAndEmptyCollections() {
        val repeated = listOf(packages[0], packages[1], packages[0], packages[2], packages[1])
        assertEquals(packages, LauncherRules.moveFavorite(repeated, "com.example.absent", 1))
        assertEquals(listOf(packages[0], packages[2], packages[1]), LauncherRules.moveFavorite(repeated, packages[1], 1))
        assertEquals(emptyList<String>(), LauncherRules.moveFavorite(emptyList(), packages[0], 1))
        assertEquals(listOf(packages[0]), LauncherRules.moveFavorite(listOf(packages[0], packages[0]), packages[0], Int.MIN_VALUE))
    }

    @Test fun movingFavoritesClampsExtremeDeltasWithoutIntegerOverflowOrWrapping() {
        assertEquals(listOf(packages[0], packages[2], packages[1]), LauncherRules.moveFavorite(packages, packages[1], Int.MAX_VALUE))
        assertEquals(listOf(packages[1], packages[0], packages[2]), LauncherRules.moveFavorite(packages, packages[1], Int.MIN_VALUE))
        assertEquals(packages, LauncherRules.moveFavorite(packages, packages.first(), Int.MIN_VALUE))
        assertEquals(packages, LauncherRules.moveFavorite(packages, packages.last(), Int.MAX_VALUE))
    }

    @Test fun uninstallAllowsOnlyUserAppsOutsideOwnPackageAndCurrentHome() {
        assertTrue(LauncherRules.canRequestUninstall("com.example.userapp", "br.com.lume.launcher", "com.example.home", false))
        assertFalse(LauncherRules.canRequestUninstall("com.example.userapp", "br.com.lume.launcher", "com.example.home", true))
        assertFalse(LauncherRules.canRequestUninstall("br.com.lume.launcher", "br.com.lume.launcher", "com.example.home", false))
        assertFalse(LauncherRules.canRequestUninstall("com.example.home", "br.com.lume.launcher", "com.example.home", false))
    }

    @Test fun uninstallFailsClosedWhenHomeOrTargetIsUnknown() {
        for (home in listOf(null, "", " ")) {
            assertFalse(LauncherRules.canRequestUninstall("com.example.userapp", "br.com.lume.launcher", home, false))
        }
        for (target in listOf("", " ")) {
            assertFalse(LauncherRules.canRequestUninstall(target, "br.com.lume.launcher", "com.example.home", false))
        }
    }

    @Test fun storeUrlsHaveOnlyTheFixedHttpsOriginAndValidatedPackageQuery() {
        for (packageName in listOf("com.google.android.youtube.tv", "org.videolan.vlc", "br.com.lume_launcher", "Com.Example_App")) {
            val uri = URI(requireNotNull(LauncherRules.storeUrl(packageName)))
            assertEquals("https", uri.scheme)
            assertEquals("play.google.com", uri.host)
            assertEquals("/store/apps/details", uri.path)
            assertEquals("id=$packageName", uri.rawQuery)
            assertNull(uri.userInfo)
            assertNull(uri.fragment)
        }
    }

    @Test fun storeUrlsRejectMalformedPackagesAndInjectionCharacters() {
        for (packageName in listOf(
            "", "com", ".app", "com.", "com..app", "1com.app", "com.9app", "com.foo-bar",
            "com.foo/bar", "com.app?x=1", "com.app&referrer=x", "com.app#fragment", "com.app%26x=y",
            "javascript:alert(1)", "https://example.com/app", "com.app\n", " com.app", "com.app name",
            "com.app<script>", "com.café"
        )) assertNull("Package must be rejected: $packageName", LauncherRules.storeUrl(packageName))
    }

    @Test fun smallBitmapIconsKeepTheirNativeResolutionWithoutUpscaling() {
        assertEquals(48 to 48, LauncherRules.fitIcon(64, 48, 48, false))
        assertEquals(32 to 16, LauncherRules.fitIcon(64, 32, 16, false))
        assertEquals(64 to 64, LauncherRules.fitIcon(64, 64, 64, false))
    }

    @Test fun oversizedBitmapIconsAndBannersFitTheBoxWithoutStretching() {
        assertEquals(64 to 64, LauncherRules.fitIcon(64, 192, 192, false))
        assertEquals(64 to 36, LauncherRules.fitIcon(64, 320, 180, false))
        assertEquals(36 to 64, LauncherRules.fitIcon(64, 180, 320, false))
    }

    @Test fun vectorIconsCanScaleUpWhenTheCallerExplicitlyAllowsIt() {
        assertEquals(64 to 64, LauncherRules.fitIcon(64, 24, 24, true))
        assertEquals(24 to 24, LauncherRules.fitIcon(64, 24, 24, false))
        assertEquals(64 to 32, LauncherRules.fitIcon(64, 32, 16, true))
        assertEquals(64 to 36, LauncherRules.fitIcon(64, 320, 180, true))
    }

    @Test fun extremeIconDimensionsRemainPositiveAndInsideTheRequestedBox() {
        assertEquals(64 to 1, LauncherRules.fitIcon(64, Int.MAX_VALUE, 1, false))
        assertEquals(1 to 64, LauncherRules.fitIcon(64, 1, Int.MAX_VALUE, false))
        assertEquals(1 to 1, LauncherRules.fitIcon(1, Int.MAX_VALUE, Int.MAX_VALUE, false))
        assertEquals(1 to 1, LauncherRules.fitIcon(Int.MAX_VALUE, 1, 1, false))
        assertEquals(Int.MAX_VALUE to Int.MAX_VALUE, LauncherRules.fitIcon(Int.MAX_VALUE, 1, 1, true))
        assertEquals(Int.MAX_VALUE to 1, LauncherRules.fitIcon(Int.MAX_VALUE, Int.MAX_VALUE, 1, true))
    }

    @Test fun iconSizingRejectsZeroAndNegativeBoundsOrSourceDimensions() {
        for (invalid in listOf(0, -1, Int.MIN_VALUE)) {
            for (upscale in listOf(false, true)) {
                assertThrows(IllegalArgumentException::class.java) { LauncherRules.fitIcon(invalid, 48, 48, upscale) }
                assertThrows(IllegalArgumentException::class.java) { LauncherRules.fitIcon(64, invalid, 48, upscale) }
                assertThrows(IllegalArgumentException::class.java) { LauncherRules.fitIcon(64, 48, invalid, upscale) }
            }
        }
    }

    @Test fun popupBoundsKeepEquivalentLogicalMarginsAcrossHdFullHdAndFourKFixtures() {
        // Equivalent 960x540dp areas at illustrative densities; actual OEM metrics are a device gate.
        assertEquals(800 to 656, LauncherRules.popupBounds(1280, 720, 800, 32))
        assertEquals(1200 to 984, LauncherRules.popupBounds(1920, 1080, 1200, 48))
        assertEquals(2400 to 1968, LauncherRules.popupBounds(3840, 2160, 2400, 96))
    }

    @Test fun popupBoundsConstrainNarrowAndShortAreasIndependently() {
        assertEquals(312 to 592, LauncherRules.popupBounds(360, 640, 880, 24))
        assertEquals(660 to 132, LauncherRules.popupBounds(960, 180, 660, 24))
        assertEquals(192 to 132, LauncherRules.popupBounds(240, 180, 660, 24))
    }

    @Test fun popupBoundsUsePreferredWidthWithoutInventingAContentHeight() {
        assertEquals(400 to 480, LauncherRules.popupBounds(800, 480, 400, 0))
        assertEquals(800 to 480, LauncherRules.popupBounds(800, 480, 1600, 0))
        assertEquals(352 to 432, LauncherRules.popupBounds(400, 480, 800, 24))
    }

    @Test fun popupBoundsRemainPositiveWhenMarginsExhaustEitherAvailableAxis() {
        assertEquals(1 to 1, LauncherRules.popupBounds(40, 40, 100, 24))
        assertEquals(1 to 168, LauncherRules.popupBounds(20, 200, 100, 16))
        assertEquals(100 to 1, LauncherRules.popupBounds(200, 20, 100, 16))
        assertEquals(1 to 1, LauncherRules.popupBounds(1, 1, 1, 0))
    }

    @Test fun popupBoundsAvoidIntegerOverflowAtExtremeAvailableSizesAndMargins() {
        assertEquals(Int.MAX_VALUE to Int.MAX_VALUE, LauncherRules.popupBounds(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, 0))
        assertEquals(1 to 1, LauncherRules.popupBounds(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(64 to 2147483645, LauncherRules.popupBounds(Int.MAX_VALUE, Int.MAX_VALUE, 64, 1))
        assertEquals(1 to 1, LauncherRules.popupBounds(Int.MAX_VALUE, Int.MAX_VALUE, 64, Int.MAX_VALUE / 2))
    }

    @Test fun popupBoundsRejectInvalidAreasPreferredWidthsAndNegativeMargins() {
        for (invalid in listOf(0, -1, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { LauncherRules.popupBounds(invalid, 720, 800, 24) }
            assertThrows(IllegalArgumentException::class.java) { LauncherRules.popupBounds(1280, invalid, 800, 24) }
            assertThrows(IllegalArgumentException::class.java) { LauncherRules.popupBounds(1280, 720, invalid, 24) }
        }
        for (invalid in listOf(-1, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { LauncherRules.popupBounds(1280, 720, 800, invalid) }
        }
    }

    @Test fun favoriteToggleAddsToAnEmptyListAndRemovesTheOnlyFavorite() {
        val empty = mutableListOf<String>()
        val added = LauncherRules.toggleFavorite(empty, packages[0])
        assertEquals(listOf(packages[0]), added)
        assertTrue(empty.isEmpty())
        assertEquals(emptyList<String>(), LauncherRules.toggleFavorite(added, packages[0]))
        assertEquals(listOf(packages[0]), added)
    }

    @Test fun favoriteToggleDeduplicatesInStableOrderWithoutMutatingTheCaller() {
        val saved = mutableListOf(packages[0], packages[1], packages[0], packages[2], packages[1])
        val original = saved.toList()
        assertEquals(packages + "com.example.fourth", LauncherRules.toggleFavorite(saved, "com.example.fourth"))
        assertEquals(listOf(packages[1], packages[2]), LauncherRules.toggleFavorite(saved, packages[0]))
        assertEquals(original, saved)
    }

    @Test fun favoriteTogglePreservesOtherPositionsAndReaddingAppendsAtTheEnd() {
        val original = packages.toMutableList()
        val removed = LauncherRules.toggleFavorite(original, packages[1])
        assertEquals(listOf(packages[0], packages[2]), removed)
        assertEquals(listOf(packages[0], packages[2], packages[1]), LauncherRules.toggleFavorite(removed, packages[1]))
        assertEquals(listOf(packages[0], packages[2]), removed)
        assertEquals(packages, original)
        val added = LauncherRules.toggleFavorite(original, "com.example.fourth")
        assertEquals(original, LauncherRules.toggleFavorite(added, "com.example.fourth"))
    }

    @Test fun favoriteToggleRejectsBlankIdsWithoutChangingSavedFavorites() {
        val saved = packages.toMutableList()
        for (invalid in listOf("", " ", "\t\n")) {
            assertThrows(IllegalArgumentException::class.java) { LauncherRules.toggleFavorite(saved, invalid) }
            assertEquals(packages, saved)
        }
    }

    @Test fun favoriteSlotsRespectTheBoundaryBeforeAnExtraCardWouldOverflow() {
        assertEquals(8, LauncherRules.favoriteSlots(870, 100, 10))
        assertEquals(7, LauncherRules.favoriteSlots(869, 100, 10))
        assertEquals(7, LauncherRules.favoriteSlots(760, 100, 10))
        assertEquals(6, LauncherRules.favoriteSlots(759, 100, 10))
    }

    @Test fun favoriteSlotsSupportNarrowAreasZeroGapsAndAnEightCardMaximum() {
        assertEquals(1, LauncherRules.favoriteSlots(1, 100, 10))
        assertEquals(1, LauncherRules.favoriteSlots(99, 100, 10))
        assertEquals(8, LauncherRules.favoriteSlots(5000, 100, 10))
        assertEquals(8, LauncherRules.favoriteSlots(800, 100, 0))
        assertEquals(7, LauncherRules.favoriteSlots(799, 100, 0))
    }

    @Test fun favoriteSlotsStayEquivalentForProportionallyScaledPixelFixtures() {
        // Pure geometry fixtures; no claim about the actual density of a particular TV.
        assertEquals(7, LauncherRules.favoriteSlots(960, 120, 12))
        assertEquals(7, LauncherRules.favoriteSlots(1920, 240, 24))
        assertEquals(7, LauncherRules.favoriteSlots(3840, 480, 48))
    }

    @Test fun favoriteSlotsDoNotOverflowWhenAddingExtremeWidthsOrGaps() {
        assertEquals(8, LauncherRules.favoriteSlots(Int.MAX_VALUE, 1, 0))
        assertEquals(8, LauncherRules.favoriteSlots(Int.MAX_VALUE, 1, 1))
        assertEquals(1, LauncherRules.favoriteSlots(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
        assertEquals(1, LauncherRules.favoriteSlots(Int.MAX_VALUE, 1, Int.MAX_VALUE))
        assertEquals(1, LauncherRules.favoriteSlots(1, Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test fun favoriteSlotsRejectNonPositiveWidthsAndNegativeGaps() {
        for (invalid in listOf(0, -1, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { LauncherRules.favoriteSlots(invalid, 100, 10) }
            assertThrows(IllegalArgumentException::class.java) { LauncherRules.favoriteSlots(960, invalid, 10) }
        }
        for (invalid in listOf(-1, Int.MIN_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { LauncherRules.favoriteSlots(960, 100, invalid) }
        }
    }
}
