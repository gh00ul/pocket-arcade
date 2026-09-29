package com.pocketarcade.games.racer

import com.pocketarcade.engine.TouchType
import com.pocketarcade.games.GAME_H
import com.pocketarcade.games.GAME_W
import com.pocketarcade.games.RoundDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/** The racer's controls: the on-screen DRIFT button, the second-finger drift, and tilt steering. */
class RacerControlsTest {
    private val deg = PI.toFloat() / 180f

    /** A racer alone on the road (so nothing shoves it), started and driving for a moment. */
    private fun racing(tilt: Boolean = false): Pair<RacerGame, RoundDriver> {
        val game = RacerGame()
        game.soloForTests = true
        game.tiltSteering = tilt
        val d = RoundDriver(game, 3L)
        d.play(0.3f)
        return game to d
    }

    // ------------------------------------------------------------------ the DRIFT button

    @Test
    fun theDriftButtonSitsLowRightInTheFieldClearOfTheGauges() {
        val r = DriftButton.HIT_R
        assertTrue("right of centre", DriftButton.X > GAME_W / 2f)
        assertTrue("inside the field's right edge", DriftButton.X + r <= GAME_W)
        assertTrue("in the lower half", DriftButton.Y - r > GAME_H / 2f)
        assertTrue("above the speed and drift gauges", DriftButton.Y + r < 592f)
        assertTrue("thumb-sized (at least 44 field units across)", DriftButton.R * 2f >= 44f)
        assertTrue("the touch reaches past the disc", DriftButton.HIT_R > DriftButton.R)
    }

    @Test
    fun theDriftButtonHitTestIsACircle() {
        val x = DriftButton.X
        val y = DriftButton.Y
        val r = DriftButton.HIT_R
        assertTrue(DriftButton.hit(x, y))
        assertTrue(DriftButton.hit(x + r - 0.5f, y))
        assertTrue(DriftButton.hit(x, y - r + 0.5f))
        assertFalse(DriftButton.hit(x + r + 0.5f, y))
        assertFalse(DriftButton.hit(x, y + r + 0.5f))
        // The corner of the bounding square is outside the circle.
        assertFalse(DriftButton.hit(x + r * 0.9f, y + r * 0.9f))
        assertFalse(DriftButton.hit(60f, 300f))
        assertFalse(DriftButton.hit(180f, 450f))
    }

    @Test
    fun holdingTheButtonDriftsEvenAsTheFirstFingerAndLettingGoEnds() {
        val (game, d) = racing()
        assertFalse(game.botDrifting)
        game.onTouch(TouchType.DOWN, 1L, DriftButton.X, DriftButton.Y, d.ms)
        assertTrue("the button should drift", game.botDrifting)
        d.play(0.2f)
        assertTrue(game.botDrifting)
        game.onTouch(TouchType.UP, 1L, DriftButton.X, DriftButton.Y, d.ms)
        assertFalse("letting go should end the drift", game.botDrifting)
    }

    @Test
    fun theButtonAndASteeringThumbWorkTogether() {
        val (game, d) = racing()
        // The right thumb holds DRIFT, then the left steers.
        game.onTouch(TouchType.DOWN, 1L, DriftButton.X, DriftButton.Y, d.ms)
        game.onTouch(TouchType.DOWN, 2L, 100f, 450f, d.ms)
        val t0 = game.botSteerTarget
        game.onTouch(TouchType.MOVE, 2L, 130f, 450f, d.ms + 10)
        assertEquals("the steering finger moves the target", t0 + 30f * RacerTuning.STEER_GAIN, game.botSteerTarget, 0.01f)
        assertTrue("still drifting while steering", game.botDrifting)
        // Moving the drift finger doesn't steer.
        val t1 = game.botSteerTarget
        game.onTouch(TouchType.MOVE, 1L, DriftButton.X - 20f, DriftButton.Y, d.ms + 20)
        assertEquals(t1, game.botSteerTarget, 0.001f)
        game.onTouch(TouchType.UP, 2L, 130f, 450f, d.ms + 30)
        assertTrue("the steering finger lifting leaves the drift", game.botDrifting)
        game.onTouch(TouchType.UP, 1L, DriftButton.X, DriftButton.Y, d.ms + 40)
        assertFalse(game.botDrifting)
    }

    @Test
    fun aFingerSlidingOffTheButtonKeepsTheDrift() {
        val (game, d) = racing()
        game.onTouch(TouchType.DOWN, 1L, DriftButton.X, DriftButton.Y, d.ms)
        game.onTouch(TouchType.MOVE, 1L, 100f, 300f, d.ms + 10)
        assertTrue("held until the finger lifts", game.botDrifting)
        game.onTouch(TouchType.UP, 1L, 100f, 300f, d.ms + 20)
        assertFalse(game.botDrifting)
    }

    @Test
    fun aSteeringFingerLandingOnTheButtonWhileDriftingStillSteers() {
        val (game, d) = racing()
        // Drift is held by a finger that landed elsewhere (a second finger anywhere still drifts).
        game.onTouch(TouchType.DOWN, 1L, 180f, 450f, d.ms)
        game.onTouch(TouchType.DOWN, 2L, 60f, 300f, d.ms)
        assertTrue(game.botDrifting)
        // Steering is the first finger; lifting it and putting it down on the button steers, as the button is busy.
        game.onTouch(TouchType.UP, 1L, 180f, 450f, d.ms)
        game.onTouch(TouchType.DOWN, 3L, DriftButton.X, DriftButton.Y, d.ms)
        val t0 = game.botSteerTarget
        game.onTouch(TouchType.MOVE, 3L, DriftButton.X - 40f, DriftButton.Y, d.ms + 10)
        assertEquals(t0 - 40f * RacerTuning.STEER_GAIN, game.botSteerTarget, 0.01f)
    }

    @Test
    fun aSecondFingerAnywhereStillDrifts() {
        val (game, d) = racing()
        game.onTouch(TouchType.DOWN, 1L, 180f, 450f, d.ms)
        assertFalse("the first finger steers", game.botDrifting)
        game.onTouch(TouchType.DOWN, 2L, 40f, 200f, d.ms)
        assertTrue("the second drifts, wherever it lands", game.botDrifting)
        game.onTouch(TouchType.UP, 2L, 40f, 200f, d.ms)
        assertFalse(game.botDrifting)
    }

    @Test
    fun aRepeatedDownOnAHeldFingerChangesNothing() {
        val (game, d) = racing()
        game.onTouch(TouchType.DOWN, 1L, DriftButton.X, DriftButton.Y, d.ms)
        game.onTouch(TouchType.DOWN, 1L, 180f, 450f, d.ms)
        val t0 = game.botSteerTarget
        game.onTouch(TouchType.MOVE, 1L, 200f, 450f, d.ms + 10)
        assertEquals("the drift finger never steers", t0, game.botSteerTarget, 0.001f)
        assertTrue(game.botDrifting)
    }

    // ------------------------------------------------------------------ tilt steering

    @Test
    fun tiltDoesNothingUnlessTheOptionIsOn() {
        val (game, d) = racing(tilt = false)
        game.onTilt(0f)
        d.play(0.1f)
        game.onTilt(25f * deg)
        val t0 = game.botSteerTarget
        d.play(0.5f)
        assertFalse(game.botTiltLive)
        assertEquals(t0, game.botSteerTarget, 0.5f)
    }

    @Test
    fun leaningRightOrLeftSlidesTheSteeringTargetThatWay() {
        for (side in intArrayOf(1, -1)) {
            val (game, d) = racing(tilt = true)
            game.onTilt(0f)
            d.play(0.05f)
            game.onTilt(0f)
            val t0 = game.botSteerTarget
            game.onTilt(side * 20f * deg)
            d.play(0.5f)
            assertTrue(game.botTiltLive)
            val moved = (game.botSteerTarget - t0) * side
            // 20 degrees is full lock: about TILT_RATE road units a second, less the small dead zone and curve.
            assertTrue("moved $moved to the ${if (side > 0) "right" else "left"}", moved in 60f..RacerTuning.TILT_RATE * 0.5f + 1f)
            // Levelling off stops it.
            game.onTilt(0f)
            val there = game.botSteerTarget
            d.play(0.3f)
            assertEquals(there, game.botSteerTarget, 1f)
        }
    }

    @Test
    fun theLevelIsWhereThePhoneWasAtTheStartOfTheRace() {
        val (game, d) = racing(tilt = true)
        // Held 15 degrees over to the right as the race starts: that's straight ahead.
        game.onTilt(15f * deg)
        d.play(0.05f)
        game.onTilt(15f * deg)
        val t0 = game.botSteerTarget
        d.play(0.4f)
        assertEquals("no steering from a steady hold", t0, game.botSteerTarget, 0.5f)
        assertEquals(0f, game.botTiltInput, 0f)
        // ...and a lean from there steers, from there.
        game.onTilt(35f * deg)
        assertTrue("${game.botTiltInput}", game.botTiltInput > 0.9f)
        game.onTilt(-5f * deg)
        assertTrue("${game.botTiltInput}", game.botTiltInput < -0.9f)
    }

    @Test
    fun aPauseRecalibratesTheLevel() {
        val (game, d) = racing(tilt = true)
        game.onTilt(0f)
        d.play(0.05f)
        game.onTilt(0f)
        game.onTilt(10f * deg)
        assertTrue(game.botTiltInput > 0.2f)
        d.pause()
        // Picked up again held differently: the first reading after the pause is level.
        game.onTilt(-20f * deg)
        assertEquals(0f, game.botTiltInput, 0f)
        game.onTilt(-20f * deg + 10f * deg)
        assertTrue(game.botTiltInput > 0.2f)
    }

    @Test
    fun underTiltAnyFingerDriftsAndDraggingSteersNothing() {
        val (game, d) = racing(tilt = true)
        game.onTilt(0f)
        d.play(0.05f)
        game.onTilt(0f)
        game.onTouch(TouchType.DOWN, 1L, 180f, 450f, d.ms)
        assertTrue("a finger anywhere drifts under tilt", game.botDrifting)
        val t0 = game.botSteerTarget
        game.onTouch(TouchType.MOVE, 1L, 300f, 450f, d.ms + 10)
        assertEquals(t0, game.botSteerTarget, 0.001f)
        game.onTouch(TouchType.UP, 1L, 300f, 450f, d.ms + 20)
        assertFalse(game.botDrifting)
    }

    @Test
    fun withoutASensorTheOptionLeavesTouchSteeringAlone() {
        val (game, d) = racing(tilt = true)
        // No reading has ever arrived (no sensor): dragging still steers, a second finger still drifts.
        assertFalse(game.botTiltLive)
        game.onTouch(TouchType.DOWN, 1L, 180f, 450f, d.ms)
        assertFalse(game.botDrifting)
        val t0 = game.botSteerTarget
        game.onTouch(TouchType.MOVE, 1L, 200f, 450f, d.ms + 10)
        assertEquals(t0 + 20f * RacerTuning.STEER_GAIN, game.botSteerTarget, 0.01f)
    }

    @Test
    fun turningTiltOffGivesTouchSteeringBack() {
        val (game, d) = racing(tilt = true)
        game.onTilt(0f)
        assertTrue(game.botTiltLive)
        game.tiltSteering = false
        assertFalse(game.botTiltLive)
        game.onTouch(TouchType.DOWN, 1L, 180f, 450f, d.ms)
        assertFalse("the first finger steers again", game.botDrifting)
        // Old readings are forgotten.
        assertEquals(0f, game.botTiltInput, 0f)
    }
}
