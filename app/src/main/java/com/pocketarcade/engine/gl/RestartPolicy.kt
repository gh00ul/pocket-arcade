package com.pocketarcade.engine.gl

/**
 * Decides whether the GL thread may start over with a fresh context after a failure. Pure logic
 * (no GL), fed the time of each failure.
 *
 * Up to [maxRestarts] restarts are allowed inside any [windowMs] stretch; the next failure after
 * that gives up, since a driver that keeps failing within a minute is not going to get better
 * (and each attempt recompiles every shader). Failures spread further apart than the window
 * never add up. A fatal failure, such as a device with no OpenGL ES 3, never restarts.
 *
 * A failure that is blamed on the new HDR pipeline ([shouldRestart]'s `blamed` flag) restarts
 * without using up a slot: the pipeline is switched off for the run and the LDR picture, which
 * has worked all along, takes over; only if that fails too do the limits apply.
 */
internal class RestartPolicy(val maxRestarts: Int = 3, val windowMs: Long = 60_000L) {
    private val restarts = LongArray(maxRestarts)
    private var count = 0

    /** True once a failure was refused: the answer stays no from then on. */
    var gaveUp = false
        private set

    /** A failure happened at [nowMs] (a monotonic clock); true if the thread may start over. */
    fun shouldRestart(nowMs: Long, fatal: Boolean = false, blamed: Boolean = false): Boolean {
        if (gaveUp) return false
        if (fatal) {
            gaveUp = true
            return false
        }
        if (blamed) return true
        // Forget restarts that have left the window.
        var kept = 0
        for (i in 0 until count) {
            if (nowMs - restarts[i] < windowMs) restarts[kept++] = restarts[i]
        }
        count = kept
        if (count >= maxRestarts) {
            gaveUp = true
            return false
        }
        restarts[count++] = nowMs
        return true
    }
}

/** A device or driver that cannot make an OpenGL ES 3 context at all: restarting cannot help. */
internal class GlUnsupportedException(message: String) : RuntimeException(message)
