package br.com.lume.launcher.core

/** The held key that opened a menu must never select its first action. */
class MenuSelectGuard(private val openingDownTime: Long) {
    private var acceptedDownTime: Long? = null

    fun consume(downTime: Long, down: Boolean, repeat: Int, canceled: Boolean): Boolean {
        if (downTime == openingDownTime) return true
        if (down) {
            if (repeat == 0) acceptedDownTime = downTime
            return acceptedDownTime != downTime
        }
        val consume = acceptedDownTime != downTime || canceled
        acceptedDownTime = null
        return consume
    }
}
