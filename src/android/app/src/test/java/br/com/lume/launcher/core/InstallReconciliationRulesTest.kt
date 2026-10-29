package br.com.lume.launcher.core

import org.junit.Assert.*
import org.junit.Test

class InstallReconciliationRulesTest {
    @Test fun cleanupNeedsSuccessfulCurrentResultWithoutAnActiveAttempt() {
        for (kind in listOf("VERIFIED", "LEGACY")) {
            assertTrue(InstallReconciliationRules.mayCleanDownloads(null, "owner", kind, 31, 31))
            for (active in listOf("owner", "new-owner", ""))
                assertFalse(InstallReconciliationRules.mayCleanDownloads(active, "owner", kind, 31, 31))
        }
        for (kind in listOf(null, "NEUTRAL", "PENDING", "unknown"))
            assertFalse(InstallReconciliationRules.mayCleanDownloads(null, "owner", kind, 31, 31))
        for (owner in listOf(null, "", " "))
            assertFalse(InstallReconciliationRules.mayCleanDownloads(null, owner, "VERIFIED", 31, 31))
        assertFalse(InstallReconciliationRules.mayCleanDownloads(null, "owner", "VERIFIED", 0, 0))
        assertFalse(InstallReconciliationRules.mayCleanDownloads(null, "owner", "VERIFIED", 30, 31))
    }
    @Test fun homeChangeWarningPreservesChoicesOfOtherDefaultLaunchers() {
        assertFalse(InstallReconciliationRules.homeChanged(null, "Lume"))
        assertFalse(InstallReconciliationRules.homeChanged("OtherHome", "OtherHome"))
        assertTrue(InstallReconciliationRules.homeChanged("OtherHome", "Lume"))
        assertTrue(InstallReconciliationRules.homeChanged("Lume", "OtherHome"))
        assertTrue(InstallReconciliationRules.homeChanged("OtherHome", null))
    }
    private val hash = "a".repeat(64)
    private val signer = "b".repeat(64)
    private val journal = InstallJournal("owner", 12, "token", 30, 31, hash, setOf(signer), true, true, 1_000, "owner", true)
    private fun resolve(value: InstallJournal = journal, code: Long = 31, actualHash: String? = hash,
        signers: Set<String> = setOf(signer), session: Boolean = false, protected: Boolean = false,
        source: Boolean = false, home: Boolean = false) =
        InstallReconciliationRules.resolve(value, code, actualHash, signers, session, protected, source, home)

    @Test fun exactInstalledIdentityProvesNewUpdateWithoutRelyingOnSessionRemoval() {
        assertEquals(InstallResolution.VERIFIED, resolve())
        assertEquals(InstallResolution.VERIFIED, resolve(session = true))
        assertEquals(InstallResolution.VERIFIED, resolve(protected = true, source = true, home = true))
    }

    @Test fun missingOrDifferentExpectedIdentityNeverBecomesSuccess() {
        listOf(journal.copy(beforeCode = 0), journal.copy(expectedCode = 30), journal.copy(committed = false),
            journal.copy(expectedHash = null), journal.copy(expectedHash = "bad"), journal.copy(expectedSigners = emptySet()),
            journal.copy(expectedSigners = setOf("bad"))).forEach { value ->
            assertEquals(InstallResolution.NEUTRAL, resolve(value, protected = true, source = true, home = true))
        }
        assertEquals(InstallResolution.NEUTRAL, resolve(code = 30))
        assertEquals(InstallResolution.NEUTRAL, resolve(code = 32))
        assertEquals(InstallResolution.NEUTRAL, resolve(actualHash = null))
        assertEquals(InstallResolution.NEUTRAL, resolve(actualHash = "c".repeat(64)))
        assertEquals(InstallResolution.NEUTRAL, resolve(signers = emptySet()))
        assertEquals(InstallResolution.NEUTRAL, resolve(signers = setOf(signer, "c".repeat(64))))
    }

    @Test fun ownerSessionAndTokenMustBeValidBeforeAnyResolution() {
        listOf(journal.copy(owner = null), journal.copy(owner = ""), journal.copy(session = -1),
            journal.copy(token = null), journal.copy(token = " ")).forEach { value ->
            assertEquals(InstallResolution.NEUTRAL, resolve(value, session = true, protected = true, source = true, home = true))
        }
        assertFalse(InstallerAttemptRules.acceptsCallback("new-owner", 13, "new-token", journal.owner!!, journal.session, journal.token))
        assertFalse(InstallerAttemptRules.acceptsCallback(journal.owner, 13, journal.token, journal.owner.orEmpty(), journal.session, journal.token))
    }

    @Test fun activeSessionIsPendingAndAbsenceAloneIsNeutral() {
        assertEquals(InstallResolution.PENDING, resolve(code = 30, actualHash = null, session = true))
        assertEquals(InstallResolution.NEUTRAL, resolve(code = 30, actualHash = null))
    }

    private fun legacy() = journal.copy(expectedCode = 0, expectedHash = null, expectedSigners = emptySet(),
        hasExpected = false, committed = false, interactiveAt = 0)

    @Test fun legacyNeedsProtectedReplaceOwnSourceBothHomesAndAbsentRecordedSession() {
        val legacy = legacy()
        assertEquals(InstallResolution.LEGACY, resolve(legacy, protected = true, source = true, home = true))
        assertEquals(InstallResolution.NEUTRAL, resolve(legacy, source = true, home = true))
        assertEquals(InstallResolution.NEUTRAL, resolve(legacy, protected = true, home = true))
        assertEquals(InstallResolution.NEUTRAL, resolve(legacy, protected = true, source = true))
        assertEquals(InstallResolution.PENDING, resolve(legacy, protected = true, source = true, home = true, session = true))
        listOf(legacy.copy(beforeCode = 0), legacy.copy(homeOwner = "other"), legacy.copy(wasHome = false),
            legacy.copy(hasExpected = true)).forEach {
            assertEquals(InstallResolution.NEUTRAL, resolve(it, protected = true, source = true, home = true))
        }
        assertEquals(InstallResolution.NEUTRAL, resolve(legacy, code = 30, protected = true, source = true, home = true))
        assertEquals(InstallResolution.NEUTRAL, resolve(legacy.copy(beforeCode = 0))) // Actual stale 0.4.4 shape.
    }

    private fun returns(result: InstallResolution = InstallResolution.VERIFIED, protected: Boolean = true,
        wasHome: Boolean = true, isHome: Boolean = true, at: Long = 1_000, now: Long = 2_000,
        attempted: Boolean = false) = InstallReconciliationRules.mayReturnHome(result, protected, wasHome, isHome, at, now, attempted)

    @Test fun returnIsOneShotOnlyForTheCurrentHomeAndProtectedEvent() {
        assertTrue(returns())
        assertFalse(returns(attempted = true))
        assertFalse(returns(protected = false))
        assertFalse(returns(wasHome = false))
        assertFalse(returns(isHome = false))
        assertFalse(returns(result = InstallResolution.PENDING))
        assertFalse(returns(result = InstallResolution.NEUTRAL))
        assertTrue(returns(result = InstallResolution.LEGACY, at = 0))
        assertFalse(returns(result = InstallResolution.LEGACY, protected = false))
    }

    @Test fun newReturnRejectsStaleMissingAndFutureInteractiveTimestamps() {
        val limit = InstallReconciliationRules.RETURN_WINDOW_MILLIS
        assertTrue(returns(now = 1_000 + limit))
        assertFalse(returns(now = 1_001 + limit))
        assertFalse(returns(now = 999))
        assertFalse(returns(at = 0))
        assertFalse(returns(at = -1))
    }

    @Test fun lateProtectedEventGetsOneProtectedReplayBeforeCallbacksAreDrained() {
        assertTrue(InstallReconciliationRules.needsProtectedReplay(false, true))
        assertFalse(InstallReconciliationRules.needsProtectedReplay(false, false))
        assertFalse(InstallReconciliationRules.needsProtectedReplay(true, false))
        assertFalse(InstallReconciliationRules.needsProtectedReplay(true, true))
        val legacy = legacy()
        assertEquals(InstallResolution.NEUTRAL, resolve(legacy, source = true, home = true))
        assertEquals(InstallResolution.LEGACY, resolve(legacy, protected = true, source = true, home = true))
    }

    @Test fun resultFromAnOlderOwnerCannotBeShownDuringANewAttempt() {
        assertTrue(InstallReconciliationRules.mayShowResolved(null, "A", 31, 31))
        assertTrue(InstallReconciliationRules.mayShowResolved("A", "A", 31, 31))
        assertFalse(InstallReconciliationRules.mayShowResolved("B", "A", 31, 31))
        assertFalse(InstallReconciliationRules.mayShowResolved(null, null, 31, 31))
        assertFalse(InstallReconciliationRules.mayShowResolved(null, " ", 31, 31))
        assertFalse(InstallReconciliationRules.mayShowResolved(null, "A", 0, 0))
        assertFalse(InstallReconciliationRules.mayShowResolved(null, "A", 31, 32))
    }

    @Test fun protectedReplayDoesNotEraseAResultAlreadyPersistedByTheFirstPass() {
        assertEquals(InstallResolution.VERIFIED, InstallReconciliationRules.mergeReplay(InstallResolution.VERIFIED, InstallResolution.NEUTRAL))
        assertEquals(InstallResolution.LEGACY, InstallReconciliationRules.mergeReplay(InstallResolution.LEGACY, InstallResolution.NEUTRAL))
        assertEquals(InstallResolution.LEGACY, InstallReconciliationRules.mergeReplay(InstallResolution.NEUTRAL, InstallResolution.LEGACY))
        assertEquals(InstallResolution.NEUTRAL, InstallReconciliationRules.mergeReplay(InstallResolution.PENDING, InstallResolution.NEUTRAL))
        assertEquals(InstallResolution.PENDING, InstallReconciliationRules.mergeReplay(InstallResolution.VERIFIED, InstallResolution.PENDING))
    }
}
