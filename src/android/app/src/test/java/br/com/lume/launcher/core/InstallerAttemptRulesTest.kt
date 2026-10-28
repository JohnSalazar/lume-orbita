package br.com.lume.launcher.core

import org.junit.Assert.*
import org.junit.Test

class InstallerAttemptRulesTest {
    @Test fun leaseBelongsOnlyToItsOwnerAndRecordedSession() {
        assertTrue(InstallerAttemptRules.owns("A", -1, "A", -1))
        assertTrue(InstallerAttemptRules.owns("A", 10, "A", 10))
        assertFalse(InstallerAttemptRules.owns("A", 10, "B", 10))
        assertFalse(InstallerAttemptRules.owns("A", 10, "A", 11))
        assertFalse(InstallerAttemptRules.owns("A", -2, "A", -2))
        assertFalse(InstallerAttemptRules.owns(null, -1, "A", -1))
        assertFalse(InstallerAttemptRules.owns("", -1, "", -1))
        assertFalse(InstallerAttemptRules.owns(" ", -1, " ", -1))
    }

    @Test fun callbackNeedsTheCompleteCommittedIdentity() {
        assertTrue(InstallerAttemptRules.acceptsCallback("A", 10, "tokenA", "A", 10, "tokenA"))
        assertFalse(InstallerAttemptRules.acceptsCallback("A", -1, "tokenA", "A", -1, "tokenA"))
        assertFalse(InstallerAttemptRules.acceptsCallback("A", 10, "tokenA", "A", 10, "tokenB"))
        assertFalse(InstallerAttemptRules.acceptsCallback("A", 10, "tokenA", "A", 11, "tokenA"))
        assertFalse(InstallerAttemptRules.acceptsCallback("A", 10, "tokenA", "B", 10, "tokenA"))
        listOf(null, "", " ").forEach { token ->
            assertFalse(InstallerAttemptRules.acceptsCallback("A", 10, token, "A", 10, token))
        }
        assertFalse(InstallerAttemptRules.acceptsCallback("A", 10, null, "A", 10, "tokenA"))
    }

    @Test fun lateCallbackFromAbandonedAttemptCannotCompleteOrReplaceTheNewAttempt() {
        var owner = "A"; var session = 10; var token = "tokenA"; var completed = false
        assertTrue(InstallerAttemptRules.acceptsCallback(owner, session, token, "A", 10, "tokenA"))
        owner = "B"; session = 11; token = "tokenB"
        if (InstallerAttemptRules.acceptsCallback(owner, session, token, "A", 10, "tokenA")) completed = true
        assertFalse(completed)
        assertEquals("B", owner); assertEquals(11, session); assertEquals("tokenB", token)
        assertFalse(InstallerAttemptRules.owns(owner, session, "A", 10))
        assertTrue(InstallerAttemptRules.acceptsCallback(owner, session, token, "B", 11, "tokenB"))
    }

    @Test fun aReusedSessionNumberStillCannotAcceptThePreviousOwnersToken() {
        assertFalse(InstallerAttemptRules.acceptsCallback("B", 10, "tokenB", "A", 10, "tokenA"))
        assertFalse(InstallerAttemptRules.acceptsCallback("B", 10, "tokenB", "B", 10, "tokenA"))
        assertTrue(InstallerAttemptRules.acceptsCallback("B", Int.MAX_VALUE, "tokenB", "B", Int.MAX_VALUE, "tokenB"))
    }
}
