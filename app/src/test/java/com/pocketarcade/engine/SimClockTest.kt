package com.pocketarcade.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The host's step pacer: every step at full speed, a fraction under slow-mo, none when frozen. */
class SimClockTest {
    private fun stepsFor(scale: Float, realSteps: Int): Int {
        val clock = SimClock()
        var n = 0
        repeat(realSteps) { if (clock.advance(FIXED_DT * scale)) n++ }
        return n
    }

    @Test
    fun fullSpeedStepsEveryTime() {
        val clock = SimClock()
        repeat(1000) { assertTrue(clock.advance(FIXED_DT)) }
    }

    @Test
    fun aFrozenClockNeverSteps() {
        assertEquals(0, stepsFor(0f, 500))
    }

    @Test
    fun stepsFollowTheScaleOverTime() {
        assertEquals(500f, stepsFor(0.5f, 1000).toFloat(), 2f)
        assertEquals(300f, stepsFor(0.3f, 1000).toFloat(), 2f)
        assertEquals(900f, stepsFor(0.9f, 1000).toFloat(), 2f)
    }

    @Test
    fun halfSpeedAlternatesEvenly() {
        val clock = SimClock()
        val pattern = BooleanArray(8) { clock.advance(FIXED_DT * 0.5f) }
        // Never two steps in a row and never two skipped in a row: an even 60 Hz beat.
        for (i in 1 until pattern.size) assertTrue(pattern[i] != pattern[i - 1])
    }

    @Test
    fun resetForgetsTheLeftover() {
        val clock = SimClock()
        assertFalse(clock.advance(FIXED_DT * 0.9f))
        clock.reset()
        // With the 0.9 gone, a further 0.2 isn't enough for a step.
        assertFalse(clock.advance(FIXED_DT * 0.2f))
    }
}
