package br.com.lume.launcher.core

/** Identity checks for the installer's single persisted lease, including asynchronous callbacks. */
object InstallerAttemptRules {
    fun owns(activeOwner: String?, activeSession: Int, owner: String, session: Int): Boolean =
        owner.isNotBlank() && owner == activeOwner && session >= -1 && session == activeSession

    fun acceptsCallback(activeOwner: String?, activeSession: Int, activeToken: String?,
                        owner: String, session: Int, token: String?): Boolean =
        owns(activeOwner, activeSession, owner, session) && session >= 0 &&
            !token.isNullOrBlank() && token == activeToken
}
