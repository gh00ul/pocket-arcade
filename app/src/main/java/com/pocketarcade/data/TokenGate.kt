package com.pocketarcade.data

/**
 * A guard for taps that spend or refund a token through a suspending save.
 *
 * The save returns a frame or more after the tap, and a second tap in that gap would run the
 * same spend again. [tryClaim] takes the guard on the tap itself, before the coroutine is
 * launched, so the second tap is turned away; the coroutine [release]s it once the save is
 * settled (in a `finally`, so a failure or a cancellation can't leave it stuck).
 *
 * Not thread-safe on purpose: it is only used from the main thread, where the taps arrive.
 */
class TokenGate {
    /** True while a claimed action is still waiting to settle. */
    var claimed = false
        private set

    /** Takes the guard. Returns false, changing nothing, if an earlier claim hasn't settled. */
    fun tryClaim(): Boolean {
        if (claimed) return false
        claimed = true
        return true
    }

    /** Lets the next [tryClaim] through. */
    fun release() {
        claimed = false
    }
}
