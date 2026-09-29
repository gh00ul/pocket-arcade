package com.pocketarcade.startup

import com.pocketarcade.engine.gl.Warmup

/**
 * Feeds the GPU a queue of warm-up pictures one at a time and reports how far along it is, for a
 * loading plan's `Wait` step (`poll` is its `ready`, `fraction` its progress). One at a time, so the GL thread gets a normal frame
 * between two uploads instead of one long stall, and so a GPU that stops answering is noticed:
 * after [stallMs] with nothing back, the run gives up on the GPU for good ([Warmup.giveUp]) and
 * reports done, and the hall uploads as it draws, as it did before there was a warm-up.
 *
 * [send] hands job number `i` to the GPU and returns its ticket; [now] is the clock the stall is
 * timed on (milliseconds; by default [Startup.frameClockMs], which stands still while the app is in
 * the background, when the GPU can't answer). Main thread only.
 */
class WarmRun(
    private val count: Int,
    private val send: (Int) -> Warmup.Ticket,
    private val stallMs: Long = 3000L,
    private val now: () -> Long = { Startup.frameClockMs },
    private val onStall: () -> Unit = {},
) {
    private var next = 0
    private var completed = 0
    private var ticket: Warmup.Ticket? = null
    private var progressAt = now()

    /** How much of the queue the GPU has drawn, 0 to 1. */
    val fraction: Float get() = if (count <= 0) 1f else completed.toFloat() / count

    /** Whether every job has been drawn (or the GPU stopped answering). Call once a slice. */
    fun poll(): Boolean {
        while (true) {
            if (Warmup.disabled) return true
            val t = ticket
            if (t != null) {
                if (!t.done) {
                    if (now() - progressAt > stallMs) {
                        onStall()
                        Warmup.giveUp()
                        return true
                    }
                    return false
                }
                completed++
                ticket = null
                progressAt = now()
            }
            if (next >= count) return true
            ticket = send(next++)
            progressAt = now()
            // A ticket that is already done (the GPU is off, or was given up on) is taken on the next turn round.
            if (ticket?.done != true) return false
        }
    }
}
