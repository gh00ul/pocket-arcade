package com.pocketarcade.games.airhockey

import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.TouchType
import com.pocketarcade.games.ENDING_SECONDS
import com.pocketarcade.games.simFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Once the clock runs out nothing new may score: the puck can still be sliding (and bounce off
 * the player's mallet) through the host's ENDING tail, and the host reads the score after that.
 */
class AirHockeyBuzzerTest {
    private fun started(): AirHockeyGame = AirHockeyGame().apply {
        seed = 11L
        start(simFx)
    }

    /** Steps [seconds] of the host's clock at [timeLeft] (0 = the ENDING tail). */
    private fun AirHockeyGame.run(seconds: Float, timeLeft: Float) {
        var t = 0f
        while (t < seconds) {
            update(FIXED_DT, timeLeft)
            t += FIXED_DT
        }
    }

    /** A fast puck just short of the CPU's goal mouth, off to one side of its mallet. */
    private fun AirHockeyGame.shootAtCpuGoal() = botPlacePuck(230f, 85f, 0f, -1300f)

    @Test
    fun aShotBeforeTheBuzzerScores() {
        // Sanity check for the test below: the same shot scores while the clock is running.
        val g = started()
        g.run(0.1f, 30f)
        g.shootAtCpuGoal()
        g.run(0.3f, 30f)
        assertEquals(1 to 0, g.botGoals)
        assertTrue(g.score > 0)
    }

    @Test
    fun noGoalScoresAfterTheBuzzer() {
        val g = started()
        g.run(0.1f, 30f)
        g.run(FIXED_DT, 0f)
        g.shootAtCpuGoal()
        g.run(ENDING_SECONDS, 0f)
        assertEquals(0 to 0, g.botGoals)
        assertEquals(0, g.score)
        assertTrue("the round must still end promptly", g.finished)

        // Same for a puck heading into the player's own goal.
        g.botPlacePuck(180f, 560f, 0f, 1300f)
        g.run(ENDING_SECONDS, 0f)
        assertEquals(0 to 0, g.botGoals)
        assertTrue(g.finished)
    }

    @Test
    fun theBuzzerFreezesThePlayersMallet() {
        val g = started()
        g.run(0.1f, 30f)
        // Grab the mallet and fling the finger across the table, then the clock runs out at once.
        val (fx, fy) = g.botScreen(300f, 400f)
        g.onTouch(TouchType.DOWN, 1L, fx, fy, 100L)
        val x0 = g.botMalletX
        val y0 = g.botMalletY
        g.run(ENDING_SECONDS, 0f)
        assertEquals(x0, g.botMalletX, 0.01f)
        assertEquals(y0, g.botMalletY, 0.01f)
    }
}
