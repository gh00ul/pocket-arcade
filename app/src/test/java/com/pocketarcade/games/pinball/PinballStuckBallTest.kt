package com.pocketarcade.games.pinball

import com.pocketarcade.engine.TouchType
import com.pocketarcade.games.RoundDriver
import com.pocketarcade.games.playRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinballStuckBallTest {
    /** A ball the kicks can't free is served again after the third kick, even though each kick sends it flying. */
    @Test
    fun failsafeReservesABallKicksCannotFree() {
        val g = PinballGame()
        val d = RoundDriver(g, 11L)
        d.play(0.2f)
        for (i in 0 until 3) g.botRemove(i)
        g.botPlace(0, 38f, 469f, 0f, 0f)
        var kicks = g.botSearchKicks
        var kickT = -99f
        var tripT = -1f
        d.play(16f) { t, _ ->
            if (g.botSearchKicks != kicks) { kicks = g.botSearchKicks; kickT = t }
            // Let each kick fly fast for half a second, then drop the ball back in its "pocket".
            if (g.botFailsafeTrips == 0 && t - kickT > 0.5f) g.botHold(0, 38f, 469f)
            if (tripT < 0f && g.botFailsafeTrips > 0) tripT = t
        }
        println("pinball stuck: ${g.botSearchKicks} kicks, trip at ${"%.1f".format(tripT)} s")
        assertTrue("the failsafe never served the stuck ball again", g.botFailsafeTrips > 0)
        assertEquals(PinballTuning.STUCK_KICKS, g.botSearchKicks)
        assertTrue("took ${tripT}s to serve again", tripT < 15f)
    }

    /** Seed 500, full plunges and flippers toggled at 5 Hz used to wedge the ball in the left inlane for 40 s. */
    @Test
    fun spamFlippingNeverFreezesTheBall() {
        val g = PinballGame()
        var k = 0
        var held = false
        var id = 1L
        var lastScore = -1
        var lastChange = 0f
        var worst = 0f
        playRound(g, 500L, null) { t, ms ->
            if (g.botLaneReady && !g.botPlungerHeld) {
                g.onTouch(TouchType.DOWN, id, 320f, 520f, ms)
                g.onTouch(TouchType.MOVE, id, 320f, 630f, ms + 50)
                g.onTouch(TouchType.UP, id, 320f, 630f, ms + 100)
                id++
            }
            k++
            if (k % 12 == 0) {
                if (!held) {
                    g.onTouch(TouchType.DOWN, 2000L + k, 70f, 560f, ms)
                    g.onTouch(TouchType.DOWN, 3000L + k, 230f, 560f, ms)
                } else {
                    g.onTouch(TouchType.UP, 2000L + k - 12, 70f, 560f, ms)
                    g.onTouch(TouchType.UP, 3000L + k - 12, 230f, 560f, ms)
                }
                held = !held
            }
            if (t >= g.roundSeconds) return@playRound
            val live = (0 until 3).any { g.botBallInPlay(it) }
            if (g.score != lastScore || !live) { lastScore = g.score; lastChange = t }
            worst = maxOf(worst, t - lastChange)
        }
        println("pinball spam-flip seed 500: score ${g.score}, kicks ${g.botSearchKicks}, trips ${g.botFailsafeTrips}, longest scoreless live ball ${"%.1f".format(worst)} s")
        assertTrue("a live ball went ${worst}s without scoring", worst < 12f)
    }
}
