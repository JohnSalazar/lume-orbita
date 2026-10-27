package br.com.lume.launcher.core

/** Logical attempt ownership is independent of a worker returning or honoring interruption. UI-thread owned. */
class ScreensaverAttemptCoordinator {
    data class Ticket(val session: Long, val attempt: Long)
    private var session = 0L
    private var sequence = 0L
    private var active = false
    private var pending: Ticket? = null
    private var initialAttempts = 0
    private var displayed = false
    var proceduralActive = false
        private set

    fun start() { session++; active = true; pending = null; initialAttempts = 0; displayed = false; proceduralActive = true }
    fun stop() { session++; active = false; pending = null; proceduralActive = false }

    fun begin(): Ticket? {
        if (!active || pending != null) return null
        if (!displayed) initialAttempts++
        return Ticket(session, ++sequence).also { pending = it }
    }

    /** Both watchdog and completion compete to close the same ticket exactly once. */
    fun complete(ticket: Ticket): Boolean {
        if (!active || pending != ticket) return false
        pending = null
        return true
    }

    fun markDisplayed() { if (active) { displayed = true; proceduralActive = false } }
    fun markFallback() { if (active) proceduralActive = true }
    fun shouldRotateProcedural(): Boolean = active && proceduralActive

    fun retryDelay(intervalMillis: Long, rateLimited: Boolean, alternativesAvailable: Boolean): Long =
        if (!displayed && initialAttempts < 2 && !rateLimited && alternativesAvailable) 250L
        else maxOf(ScreensaverRules.RETRY_MILLIS, intervalMillis)
}
