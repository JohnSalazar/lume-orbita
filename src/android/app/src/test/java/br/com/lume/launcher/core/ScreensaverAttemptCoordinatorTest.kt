package br.com.lume.launcher.core

import org.junit.Assert.*
import org.junit.Test

class ScreensaverAttemptCoordinatorTest {
    @Test fun proceduralIsReadyAtStartAndReturnsAfterOnlineFailure() {
        val coordinator = ScreensaverAttemptCoordinator()
        assertFalse(coordinator.shouldRotateProcedural())
        coordinator.start()
        assertTrue(coordinator.shouldRotateProcedural())
        coordinator.markDisplayed()
        assertFalse(coordinator.shouldRotateProcedural())
        coordinator.markFallback()
        assertTrue(coordinator.shouldRotateProcedural())
        coordinator.markDisplayed()
        assertFalse(coordinator.shouldRotateProcedural())
    }

    @Test fun stopPreventsQueuedProceduralTickAndInactiveStateChanges() {
        val coordinator = ScreensaverAttemptCoordinator().apply { start() }
        coordinator.stop()
        coordinator.markFallback(); coordinator.markDisplayed()
        assertFalse(coordinator.shouldRotateProcedural())
        coordinator.start()
        assertTrue(coordinator.shouldRotateProcedural())
    }

    @Test fun staleOnlineResultCannotDisableNewSessionProceduralFallback() {
        val coordinator = ScreensaverAttemptCoordinator().apply { start() }
        val old = coordinator.begin()!!
        coordinator.stop(); coordinator.start()
        assertFalse(coordinator.complete(old))
        assertTrue(coordinator.shouldRotateProcedural())
        val fresh = coordinator.begin()!!
        assertTrue(coordinator.complete(fresh))
        coordinator.markDisplayed()
        assertFalse(coordinator.shouldRotateProcedural())
    }

    @Test fun onlyOneLiveTicketExistsAndCompletesExactlyOnce() {
        val coordinator = ScreensaverAttemptCoordinator()
        assertNull(coordinator.begin())
        coordinator.start()
        val first = coordinator.begin()!!
        assertNull(coordinator.begin())
        assertTrue(coordinator.complete(first))
        assertFalse(coordinator.complete(first))
        assertNotEquals(first, coordinator.begin())
    }

    @Test fun watchdogAdvancesUiWithoutWorkerCallbackAndLateResultCannotCloseNextAttempt() {
        val coordinator = ScreensaverAttemptCoordinator().apply { start() }
        val stalledWorker = coordinator.begin()!!
        assertTrue(coordinator.complete(stalledWorker)) // UI deadline; the worker never called back.
        assertEquals(250L, coordinator.retryDelay(60_000, false, true))
        val replacement = coordinator.begin()!!
        assertFalse(coordinator.complete(stalledWorker))
        assertNull(coordinator.begin()) // Late cleanup must not free the replacement ticket.
        assertTrue(coordinator.complete(replacement))
        assertEquals(60_000L, coordinator.retryDelay(60_000, false, true))
    }

    @Test fun stopAndRestartRejectOldResultsWithoutWaitingForPhysicalWorker() {
        val coordinator = ScreensaverAttemptCoordinator().apply { start() }
        val old = coordinator.begin()!!
        coordinator.stop(); coordinator.stop()
        assertFalse(coordinator.complete(old)); assertNull(coordinator.begin())
        coordinator.start()
        val fresh = coordinator.begin()!!
        assertNotEquals(old.session, fresh.session)
        assertFalse(coordinator.complete(old)); assertTrue(coordinator.complete(fresh))
        assertEquals(250L, coordinator.retryDelay(30_000, false, true))
    }

    @Test fun initializationHasAtMostTwoQuickAttemptsAndNeverHammersRateLimits() {
        val coordinator = ScreensaverAttemptCoordinator().apply { start() }
        coordinator.complete(coordinator.begin()!!)
        assertEquals(30_000L, coordinator.retryDelay(1, true, true))
        assertEquals(60_000L, coordinator.retryDelay(60_000, false, false))
        assertEquals(250L, coordinator.retryDelay(60_000, false, true))
        coordinator.complete(coordinator.begin()!!)
        assertEquals(30_000L, coordinator.retryDelay(1, false, true))
        coordinator.complete(coordinator.begin()!!)
        assertEquals(180_000L, coordinator.retryDelay(180_000, false, true))
    }

    @Test fun aDisplayedImageDisablesQuickRetriesUntilTheNextSession() {
        val coordinator = ScreensaverAttemptCoordinator().apply { start() }
        coordinator.complete(coordinator.begin()!!); coordinator.markDisplayed()
        coordinator.complete(coordinator.begin()!!)
        assertEquals(60_000L, coordinator.retryDelay(60_000, false, true))
        coordinator.start(); coordinator.complete(coordinator.begin()!!)
        assertEquals(250L, coordinator.retryDelay(60_000, false, true))
    }

    @Test fun nextRequestAvoidsBothFailedUrlAndCurrentlyDisplayedImageWhenPossible() {
        assertEquals(setOf(0, 3), (-20..20).map { ScreensaverRules.nextRequestIndex(4, 1, 2, it) }.toSet())
        assertEquals(setOf(0, 1, 2), (-10..10).map { ScreensaverRules.nextRequestIndex(3, -1, -1, it) }.toSet())
        assertEquals(0, ScreensaverRules.nextRequestIndex(2, 0, 1, Int.MIN_VALUE))
        assertEquals(1, ScreensaverRules.nextRequestIndex(2, 0, 0, Int.MAX_VALUE))
        assertEquals(0, ScreensaverRules.nextRequestIndex(1, 0, 0, 3))
        assertThrows(IllegalArgumentException::class.java) { ScreensaverRules.nextRequestIndex(0, 0, 0, 0) }
    }
}
