package com.pocketarcade.engine

import com.pocketarcade.games.GameFx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The host's time control: hit-stops, slow-mo beats, their limits and the reduce-motion switch. */
class TimeScaleTest {
    private var motion = 1f
    private val time = TimeScale { motion }

    /** Runs [seconds] of real time in fixed steps; returns the game time it left (sum of scaled steps). */
    private fun run(seconds: Float): Float {
        var game = 0f
        repeat((seconds / FIXED_DT).toInt()) { game += time.update(FIXED_DT) }
        return game
    }

    @Test
    fun idleGivesTheGameEveryStep() {
        assertEquals(1f, time.scale, 0f)
        assertEquals(FIXED_DT, time.update(FIXED_DT), 0f)
        assertFalse(time.active)
        assertEquals(1f, run(1f), 0.02f)
    }

    @Test
    fun aHitStopFreezesForRoughlyItsLengthThenResumes() {
        assertTrue(time.hitStop(0.06f))
        assertTrue(time.frozen)
        val during = run(0.06f + FIXED_DT)
        // About 60 ms lost, nothing more; afterwards full speed again.
        assertEquals(0f, during, FIXED_DT * 2f)
        assertFalse(time.frozen)
        assertEquals(FIXED_DT, time.update(FIXED_DT), 0f)
    }

    @Test
    fun aHitStopIsCappedAndChainingCantExtendItPastTheCap() {
        assertTrue(time.hitStop(5f))
        var frozenSteps = 0
        // A game shouting for a longer freeze every step still gets one freeze of at most the cap
        // (watched for less than the cap plus the cooldown, before a second freeze may begin).
        repeat((0.3f / FIXED_DT).toInt()) {
            time.hitStop(0.1f)
            if (time.update(FIXED_DT) == 0f) frozenSteps++
        }
        assertTrue("froze $frozenSteps steps", frozenSteps * FIXED_DT <= TimeScale.MAX_HIT_STOP + FIXED_DT * 2f)
        assertTrue(frozenSteps > 0)
    }

    @Test
    fun tinyOrNanHitStopsAreIgnored() {
        assertFalse(time.hitStop(0.005f))
        assertFalse(time.hitStop(Float.NaN))
        assertFalse(time.hitStop(-1f))
        assertFalse(time.frozen)
    }

    @Test
    fun aHitStopCooldownKeepsAComboFromStuttering() {
        assertTrue(time.hitStop(0.05f))
        run(0.06f)
        assertFalse("just ended", time.frozen)
        // Straight after a freeze another is refused...
        assertFalse(time.hitStop(0.05f))
        // ...and allowed again once the cooldown has passed.
        run(TimeScale.HIT_STOP_COOLDOWN + 0.05f)
        assertTrue(time.hitStop(0.05f))
    }

    @Test
    fun slowMoEasesDownHoldsAndEasesBackWithoutSnapping() {
        assertTrue(time.slowMo(0.3f, 0.4f))
        var prev = 1f
        var lowest = 1f
        var maxDrop = 0f
        var maxRise = 0f
        var steps = 0
        while (steps < 600) {
            time.update(FIXED_DT)
            val s = time.scale
            maxDrop = maxOf(maxDrop, prev - s)
            maxRise = maxOf(maxRise, s - prev)
            lowest = minOf(lowest, s)
            prev = s
            steps++
        }
        // It reaches (about) the asked speed, and never below it.
        assertEquals(0.3f, lowest, 0.03f)
        assertTrue(lowest >= 0.3f - 1e-3f)
        // Eased: no single 120 Hz step changes the speed by more than a fifth, in or out.
        assertTrue("drop $maxDrop", maxDrop < 0.2f)
        assertTrue("rise $maxRise", maxRise < 0.1f)
        // And it is back to exactly full speed in the end.
        assertEquals(1f, time.scale, 0f)
        assertFalse(time.active)
    }

    @Test
    fun aSlowMoBeatCostsBoundedGameTime() {
        time.slowMo(0.3f, 0.4f)
        val game = run(3f)
        // Slower than real time, but a beat only costs the round about a third of a second.
        assertTrue("game time $game", game in 2.4f..2.85f)
    }

    @Test
    fun slowMoNeverGoesBelowTheFloorOrPastTheMaxHold() {
        assertTrue(time.slowMo(0.01f, 60f))
        var lowest = 1f
        var slowSteps = 0
        repeat((6f / FIXED_DT).toInt()) {
            time.update(FIXED_DT)
            lowest = minOf(lowest, time.scale)
            if (time.scale < 0.9f) slowSteps++
        }
        assertTrue("lowest ${lowest}", lowest >= TimeScale.MIN_SCALE - 1e-3f)
        // Held for at most the cap plus the ramp back out.
        assertTrue("slow for ${slowSteps * FIXED_DT}s", slowSteps * FIXED_DT < TimeScale.MAX_SLOW_SECONDS + 0.8f)
    }

    @Test
    fun stackedSlowMoDeepensAndExtendsButStaysUnderTheCap() {
        assertTrue(time.slowMo(0.6f, 0.3f))
        run(0.1f)
        assertTrue(time.slowMo(0.35f, 0.5f))
        // The stronger (lower) speed wins; a gentler follow-up doesn't undo it.
        assertTrue(time.slowMo(0.8f, 0.2f))
        var lowest = 1f
        var held = 0
        // Two seconds: the hold is over by then and the cooldown still stops a second beat starting.
        repeat((2f / FIXED_DT).toInt()) {
            time.slowMo(0.35f, 0.5f)
            time.update(FIXED_DT)
            lowest = minOf(lowest, time.scale)
            if (time.scale < 0.98f) held++
        }
        assertEquals(0.35f, lowest, 0.04f)
        // Asking every step never holds it past the max plus the ramp out.
        assertTrue("held ${held * FIXED_DT}s", held * FIXED_DT < TimeScale.MAX_SLOW_SECONDS + 1.2f)
    }

    @Test
    fun aSlowMoCooldownSpacesBeatsOut() {
        assertTrue(time.slowMo(0.4f, 0.3f))
        run(1f)
        assertFalse("still cooling down", time.slowMo(0.4f, 0.3f))
        run(TimeScale.SLOW_COOLDOWN)
        assertTrue(time.slowMo(0.4f, 0.3f))
    }

    @Test
    fun unremarkableSpeedsAreIgnored() {
        assertFalse(time.slowMo(1f, 0.5f))
        assertFalse(time.slowMo(0.99f, 0.5f))
        assertFalse(time.slowMo(0.4f, 0f))
        assertFalse(time.slowMo(Float.NaN, 0.5f))
        assertFalse(time.active)
    }

    @Test
    fun reduceMotionTurnsBothOff() {
        motion = 0f
        assertFalse(time.hitStop(0.08f))
        assertFalse(time.slowMo(0.3f, 0.5f))
        assertEquals(FIXED_DT, time.update(FIXED_DT), 0f)
        assertEquals(1f, time.scale, 0f)
        assertFalse(time.active)
    }

    @Test
    fun reduceMotionSwitchedOnMidBeatEndsItAtOnce() {
        time.slowMo(0.3f, 0.5f)
        run(0.1f)
        assertTrue(time.scale < 0.6f)
        motion = 0f
        assertEquals(FIXED_DT, time.update(FIXED_DT), 0f)
        assertEquals(1f, time.scale, 0f)
        assertFalse(time.frozen)
    }

    @Test
    fun resetForgetsEverythingIncludingCooldowns() {
        time.hitStop(0.05f)
        time.slowMo(0.4f, 0.4f)
        run(0.3f)
        time.reset()
        assertFalse(time.active)
        assertTrue(time.hitStop(0.05f))
        assertTrue(time.slowMo(0.4f, 0.4f))
    }

    @Test
    fun theGameStillStepsByExactlyTheFixedStep() {
        // Slow motion is fewer steps, never a scaled step: a SimClock says yes or no, and the count follows the scale.
        val clock = SimClock()
        time.slowMo(0.5f, 0.6f)
        var steps = 0
        var gameTime = 0f
        repeat((2f / FIXED_DT).toInt()) {
            if (clock.advance(time.update(FIXED_DT))) {
                steps++
                gameTime += FIXED_DT
            }
        }
        // Two real seconds of which about half a second was at reduced speed: fewer than 240 steps, more than 150.
        assertTrue("steps $steps", steps in 150..235)
        assertEquals(steps * FIXED_DT, gameTime, 1e-4f)
    }

    // ---- the requests a game makes through GameFx

    private fun fx() = GameFx(AudioSynth(), Haptics(null)) {}

    @Test
    fun gameFxKeepsTheStrongestRequestAndClearsOnFlush() {
        val fx = fx()
        fx.hitStop(0.03f)
        fx.hitStop(0.08f)
        fx.hitStop(0.05f)
        fx.slowMo(0.6f, 0.2f)
        fx.slowMo(0.35f, 0.5f)
        fx.punch(0.3f)
        fx.punch(0.9f)
        fx.punch(0.5f)
        val punch = fx.flush(time)
        assertEquals(0.9f, punch, 0f)
        assertTrue(time.frozen)
        run(0.2f)
        assertTrue("slow-mo started at the deeper speed", time.scale < 0.5f)
        // Everything was consumed: a second flush hands over nothing.
        assertEquals(0f, fx.flush(time), 0f)
    }

    @Test
    fun outsideThePlayingPhaseRequestsAreDroppedNotQueued() {
        val fx = fx()
        fx.hitStop(0.1f)
        fx.slowMo(0.3f, 0.5f)
        fx.punch(0.4f)
        assertEquals(0.4f, fx.flush(null), 0f)
        assertFalse(time.frozen)
        // Nothing left over to fire later.
        fx.flush(time)
        assertFalse(time.active)
    }
}
