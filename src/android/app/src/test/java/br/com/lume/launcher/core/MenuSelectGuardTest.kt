package br.com.lume.launcher.core

import org.junit.Assert.*
import org.junit.Test

class MenuSelectGuardTest {
    @Test fun openingHoldRepeatsAndReleaseNeverActivate() {
        val guard = MenuSelectGuard(10)
        assertTrue(guard.consume(10, true, 0, false))
        assertTrue(guard.consume(10, true, 50, false))
        assertTrue(guard.consume(10, false, 0, false))
    }

    @Test fun newPressAndReleaseAreAcceptedOnce() {
        val guard = MenuSelectGuard(10)
        assertFalse(guard.consume(20, true, 0, false))
        assertFalse(guard.consume(20, false, 0, false))
        assertTrue(guard.consume(20, false, 0, false))
    }

    @Test fun repeatsWithoutInitialPressAndUnmatchedReleaseAreConsumed() {
        val guard = MenuSelectGuard(10)
        assertTrue(guard.consume(20, true, 1, false))
        assertTrue(guard.consume(20, false, 0, false))
        assertFalse(guard.consume(30, true, 0, false))
        assertTrue(guard.consume(31, false, 0, false))
        assertTrue(guard.consume(30, false, 0, false))
    }

    @Test fun repeatsBelongToFreshPressButCanceledReleaseDoesNotActivate() {
        val guard = MenuSelectGuard(10)
        assertFalse(guard.consume(20, true, 0, false))
        assertFalse(guard.consume(20, true, 5, false))
        assertTrue(guard.consume(20, false, 0, true))
        assertFalse(guard.consume(30, true, 0, false))
        assertFalse(guard.consume(30, false, 0, false))
    }
}
