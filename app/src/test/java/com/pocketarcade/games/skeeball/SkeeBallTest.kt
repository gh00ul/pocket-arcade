package com.pocketarcade.games.skeeball

import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.games.Stats
import com.pocketarcade.games.flickNoMove
import com.pocketarcade.games.playRound
import com.pocketarcade.games.simFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the skee-ball round hang: a roll that friction stopped just short of the
 * jump ramp stayed in play forever, so the round never finished and the host sat at 0:00.
 */
class SkeeBallTest {
    private companion object {
        const val REST_X = 180f
        const val REST_Y = 586f
        /** A roll must be over (scored or guttered) within this long; the slowest takes about 5.5 s. */
        const val MAX_ROLL_SECONDS = 7f
        /** Launch speeds that used to stall from the rest spot (the ramp dead band). */
        const val OLD_BAD_LO = 178.25f
        const val OLD_BAD_HI = 179.55f
    }

    private fun startedGame(): SkeeBallGame = SkeeBallGame().also {
        it.seed = 1L
        it.start(simFx)
    }

    /**
     * Launches a batch of balls at [speeds] (up to six, the rack's size) from ([x], [y]) at
     * [angleDeg], steps until every one is out of play and returns how long that took. The clock
     * is held away from zero so nothing but the balls ends the waiting.
     */
    private fun rollBatch(game: SkeeBallGame, x: Float, y: Float, angleDeg: Float, speeds: FloatArray, n: Int): Float {
        val angle = angleDeg * (Math.PI.toFloat() / 180f)
        for (i in 0 until n) assertTrue(game.botLaunch(x, y, speeds[i], angle))
        var t = 0f
        while (game.botBallsActive > 0 && t < SkeeTuning.BALL_TIMEOUT + 2f) {
            game.update(FIXED_DT, 30f)
            t += FIXED_DT
        }
        return t
    }

    /** Sweeps launch speed from [lo] to [hi] in [step]s from one spot and angle. */
    private fun sweep(game: SkeeBallGame, x: Float, y: Float, angleDeg: Float, lo: Float, hi: Float, step: Float): Int {
        val speeds = FloatArray(6)
        var n = 0
        var k = 0
        var rolled = 0
        var worst = 0f
        while (true) {
            val v = lo + k * step
            if (v > hi + 1e-3f) break
            speeds[n++] = v
            k++
            if (n == speeds.size) {
                worst = maxOf(worst, rollBatch(game, x, y, angleDeg, speeds, n))
                rolled += n
                n = 0
            }
        }
        if (n > 0) {
            worst = maxOf(worst, rollBatch(game, x, y, angleDeg, speeds, n))
            rolled += n
        }
        assertTrue("a roll from ($x, $y) at $angleDeg° took ${worst}s", worst <= MAX_ROLL_SECONDS)
        assertEquals("the failsafe had to retire a roll from ($x, $y) at $angleDeg°", 0, game.botFailsafeTrips)
        return rolled
    }

    @Test
    fun oldDeadBandFromTheRestSpotAlwaysFinishes() {
        val game = startedGame()
        val maxA = SkeeTuning.MAX_ANGLE_DEG
        for (a in floatArrayOf(0f, -maxA, maxA)) {
            sweep(game, REST_X, REST_Y, a, OLD_BAD_LO - 2f, OLD_BAD_HI + 2f, 0.01f)
        }
    }

    @Test
    fun everyRollFinishesFromAnywhereInTheGrabArea() {
        val game = startedGame()
        val maxA = SkeeTuning.MAX_ANGLE_DEG
        var rolled = 0
        // Across the lane (ball centre limits) and along the grab area (the drag limits).
        for (x in floatArrayOf(74f, 130f, 180f, 236f, 286f)) {
            for (y in floatArrayOf(490f, 540f, 586f, 610f)) {
                for (a in floatArrayOf(-maxA, -12f, 0f, 12f, maxA)) {
                    rolled += sweep(game, x, y, a, SkeeTuning.MIN_SPEED, SkeeTuning.MAX_SPEED, 0.25f)
                }
            }
        }
        println("skee-ball sweep: $rolled rolls, all finished, failsafe trips ${game.botFailsafeTrips}")
    }

    @Test
    fun failsafeRetiresABallThePhysicsCannotFinish() {
        val game = startedGame()
        // A ball whose physics went bad never lands, rolls back or reaches the gutter by itself.
        assertTrue(game.botLaunch(REST_X, REST_Y, Float.NaN, 0f))
        var t = 0f
        while (game.botBallsActive > 0 && t < 30f) {
            game.update(FIXED_DT, 30f)
            t += FIXED_DT
        }
        assertEquals("the stuck ball is still in play", 0, game.botBallsActive)
        assertTrue("retired after ${t}s", t <= SkeeTuning.BALL_TIMEOUT + 0.5f)
        assertEquals(1, game.botFailsafeTrips)
        assertEquals(1, game.botGutters)
    }

    @Test
    fun aGutterBallGuttersOnce() {
        val game = startedGame()
        // Too slow to reach the ramp: it rolls back into the gutter.
        rollBatch(game, REST_X, REST_Y, 0f, floatArrayOf(150f), 1)
        assertEquals(1, game.botGutters)
    }

    @Test
    fun roundEndsAfterAGentleFlickAtTheOldStuckSpeed() {
        // A flick with no MOVE samples leaves the ball on its rest spot, and its speed times
        // FLICK_TO_SPEED lands inside the old dead band. Flicked just before time runs out.
        val game = SkeeBallGame()
        val stats = Stats("gentle skee")
        var seed = 100L
        for (flickSpeed in floatArrayOf(540.5f, 541.5f, 542.5f, 543.5f)) {
            val ballSpeed = flickSpeed * SkeeTuning.FLICK_TO_SPEED
            assertTrue(ballSpeed in OLD_BAD_LO..OLD_BAD_HI)
            var flicked = false
            playRound(game, seed++, stats) { t, ms ->
                if (!flicked && t >= game.roundSeconds - 2f) {
                    flicked = true
                    flickNoMove(game, 1L, REST_X, 600f, 0f, -flickSpeed, ms)
                }
            }
            assertTrue("the flick never rolled", flicked && game.botGutters == 1)
            assertEquals(0, game.botFailsafeTrips)
        }
    }
}
