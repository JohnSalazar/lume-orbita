package br.com.lume.launcher.core

data class InstallJournal(val owner: String?, val session: Int, val token: String?, val beforeCode: Long,
    val expectedCode: Long, val expectedHash: String?, val expectedSigners: Set<String>, val hasExpected: Boolean,
    val committed: Boolean, val interactiveAt: Long, val homeOwner: String?, val wasHome: Boolean)

enum class InstallResolution { VERIFIED, LEGACY, PENDING, NEUTRAL }

/** Missing sessions are never installation evidence. Legacy proof is restricted to a protected replace event. */
object InstallReconciliationRules {
    private val hash = Regex("[0-9a-f]{64}")
    const val RETURN_WINDOW_MILLIS = 10 * 60 * 1000L
    fun homeChanged(before: String?, after: String?): Boolean = before != null && before != after
    fun mayCleanDownloads(activeOwner: String?, resultOwner: String?, resultKind: String?, resultCode: Long, installedCode: Long): Boolean =
        activeOwner == null && resultKind in setOf(InstallResolution.VERIFIED.name, InstallResolution.LEGACY.name) &&
            mayShowResolved(null, resultOwner, resultCode, installedCode)
    fun needsProtectedReplay(passWasProtected: Boolean, protectedEventJoined: Boolean): Boolean =
        !passWasProtected && protectedEventJoined
    fun mayShowResolved(activeOwner: String?, resultOwner: String?, resultCode: Long, installedCode: Long): Boolean =
        !resultOwner.isNullOrBlank() && (activeOwner == null || activeOwner == resultOwner) &&
            resultCode > 0 && resultCode == installedCode
    fun mergeReplay(first: InstallResolution, replay: InstallResolution): InstallResolution =
        if (replay == InstallResolution.NEUTRAL && first in setOf(InstallResolution.VERIFIED, InstallResolution.LEGACY)) first else replay

    fun resolve(journal: InstallJournal, code: Long, actualHash: String?, signers: Set<String>,
                sessionExists: Boolean, protectedReplace: Boolean, ownInstallSource: Boolean, isHome: Boolean): InstallResolution {
        if (!InstallerAttemptRules.acceptsCallback(journal.owner, journal.session, journal.token,
                journal.owner.orEmpty(), journal.session, journal.token)) return InstallResolution.NEUTRAL
        if (journal.hasExpected && journal.committed && journal.beforeCode > 0 && journal.expectedCode > journal.beforeCode &&
            code == journal.expectedCode && journal.expectedHash?.let(hash::matches) == true && actualHash == journal.expectedHash &&
            journal.expectedSigners.isNotEmpty() && journal.expectedSigners.all(hash::matches) && signers == journal.expectedSigners)
            return InstallResolution.VERIFIED
        if (!journal.hasExpected && protectedReplace && !sessionExists && journal.beforeCode > 0 && code > journal.beforeCode &&
            ownInstallSource && journal.homeOwner == journal.owner && journal.wasHome && isHome) return InstallResolution.LEGACY
        return if (sessionExists) InstallResolution.PENDING else InstallResolution.NEUTRAL
    }

    fun mayReturnHome(result: InstallResolution, protectedReplace: Boolean, wasHome: Boolean, isHome: Boolean,
                      interactiveAt: Long, now: Long, alreadyAttempted: Boolean): Boolean {
        if (!protectedReplace || !wasHome || !isHome || alreadyAttempted) return false
        if (result == InstallResolution.LEGACY) return true // The fresh protected event and own install source are mandatory above.
        return result == InstallResolution.VERIFIED && interactiveAt > 0 && now >= interactiveAt &&
            now - interactiveAt <= RETURN_WINDOW_MILLIS
    }
}
