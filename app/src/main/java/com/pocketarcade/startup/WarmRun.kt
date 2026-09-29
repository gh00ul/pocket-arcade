package com.pocketarcade.startup

import com.pocketarcade.engine.gl.Warmup

/**
 * Feeds the GPU a queue of warm-up pictures and reports how far along it is, for a loading plan's
 * `Wait` step (`poll` is its `ready`, `fraction` its progress). Only a few are ever in flight
 * ([depth]): behind a loading screen a few at once, which hides the round trip to the GL thread
 * and back, and while the title is being drawn one at a time, so the GL thread gets a normal frame
 * between two uploads instead of one long stall. A GPU that stops answering is noticed: after
 * [stallMs] with nothing back, the run gives up on the GPU for good ([Warmup.giveUp]) and reports
 * done, and the hall uploads as it draws, as it did before there was a warm-up.
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
    private val depth: () -> Int = { if (Startup.urgent) DEPTH_LOADING else 1 },
) {
    companion object {
        /** How many pictures are in flight at once behind a loading screen. */
        const val DEPTH_LOADING = 3
    }

    private var next = 0
    private var completed = 0
    private val inFlight = ArrayDeque<Warmup.Ticket>()
    private var progressAt = now()

    /** How much of the queue the GPU has drawn, 0 to 1. */
    val fraction: Float get() = if (count <= 0) 1f else completed.toFloat() / count

    /** Whether every job has been drawn (or the GPU stopped answering). Call once a slice. */
    fun poll(): Boolean {
        while (true) {
            if (Warmup.disabled) return true
            // The GL thread draws them in the order sent: retire what has been answered.
            while (inFlight.isNotEmpty() && inFlight.first().done) {
                inFlight.removeFirst()
                completed++
                progressAt = now()
            }
            if (next >= count && inFlight.isEmpty()) return true
            if (inFlight.isNotEmpty() && now() - progressAt > stallMs) {
                onStall()
                Warmup.giveUp()
                return true
            }
            val room = depth().coerceAtLeast(1) - inFlight.size
            if (room <= 0 || next >= count) return false
            if (inFlight.isEmpty()) progressAt = now()
            var sent = 0
            while (sent < room && next < count) {
                inFlight.addLast(send(next++))
                sent++
            }
            // Tickets that are already done (the GPU is off, or was given up on) are retired on the next turn round.
            if (inFlight.first().done) continue
            return false
        }
    }
}
