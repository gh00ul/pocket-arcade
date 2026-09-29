package com.pocketarcade.games.racer

import com.pocketarcade.games.RoundDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The racer scene's own numbers, and the rule that its visual effects never touch the race. */
class RacerSceneTest {
    @Test
    fun theSceneBuildsNothingUntilItDraws() {
        // Constructing the scene (and a game that owns one) needs no art: tests never draw.
        RacerScene()
        RacerGame()
    }

    @Test
    fun lookConstantsFitTheViewDistance() {
        // The road is drawn 72 segments of 40 units ahead: the cheap car and the detail cut-off sit inside it.
        assertTrue(RacerLook.LOD_DISTANCE < 72 * 40f)
        assertTrue(RacerLook.DETAIL_SEGMENTS in 10..72)
        assertTrue(RacerLook.SPEED_LINE_FROM in 0f..1f)
    }

    /**
     * Drifting lays smoke and skid marks and boosting lights lamps, all in the game's step; none of
     * it may change how the race runs. The same seed with and without a drift-and-turbo bot's
     * extra effects can't be compared directly, so run the same bot twice and compare the whole
     * race: identical progress means the effects consume no shared randomness.
     */
    @Test
    fun effectsLeaveTheRaceReproducible() {
        fun race(): String {
            val game = RacerGame()
            val d = RoundDriver(game, 11L)
            val trace = StringBuilder()
            var k = 0
            d.play(30f) { t, ms ->
                // Hold the drift button for two seconds in every five.
                if ((t % 5f) < 2f) {
                    if (!game.botDrifting) game.onTouch(com.pocketarcade.engine.TouchType.DOWN, 7L, DriftButton.X, DriftButton.Y, ms)
                } else if (game.botDrifting) {
                    game.onTouch(com.pocketarcade.engine.TouchType.UP, 7L, DriftButton.X, DriftButton.Y, ms)
                }
                if (k++ % 60 == 0) trace.append("%.1f,%.1f;".format(game.botProgress, game.botPX))
            }
            return trace.append(game.score).toString()
        }
        assertEquals(race(), race())
    }
}
