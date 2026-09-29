package com.pocketarcade.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The odometer maths behind rolling counters, and the flight path of coins and tickets. */
class RollingNumberTest {
    @Test
    fun wholeNumbersRestEveryWheelOnItsDigit() {
        for (v in intArrayOf(0, 7, 10, 19, 20, 99, 100, 4321, 100000)) {
            val f = v.toFloat()
            for (p in 0..5) {
                assertEquals("roll of $v at $p", 0f, Odometer.roll(f, p), 0f)
                assertEquals(v / Math.pow(10.0, p.toDouble()).toInt().coerceAtLeast(1), Odometer.turns(f, p))
            }
        }
    }

    @Test
    fun theUnitsWheelTurnsWithTheValueAndEasesAtBothEnds() {
        assertEquals(0f, Odometer.roll(4.0f, 0), 0f)
        assertEquals(0.5f, Odometer.roll(4.5f, 0), 1e-4f)
        // Smoothstep: slow start and end, so a digit lingers on its number while counting fast.
        assertTrue(Odometer.roll(4.1f, 0) < 0.1f)
        assertTrue(Odometer.roll(4.9f, 0) > 0.9f)
        assertEquals(4, Odometer.turns(4.99f, 0))
    }

    @Test
    fun aHigherWheelOnlyRollsWhileTheOneBelowCarries() {
        // 19 -> 20: the tens wheel stays put until the units wheel is in its last tenth.
        assertEquals(0f, Odometer.roll(11.3f, 1), 0f)
        assertEquals(0f, Odometer.roll(15.8f, 1), 0f)
        assertEquals(0f, Odometer.roll(18.9f, 1), 1e-6f)
        assertTrue(Odometer.roll(19.5f, 1) in 0.4f..0.6f)
        assertTrue(Odometer.roll(19.99f, 1) > 0.99f)
        // And the units wheel is mid-turn (9 -> 0) at the same moment.
        assertTrue(Odometer.roll(19.5f, 0) in 0.4f..0.6f)
        assertEquals(1, Odometer.turns(19.5f, 1))
    }

    @Test
    fun aCarryThroughSeveralDigitsRollsThemAllTogether() {
        // 99.5 -> 100: units, tens and hundreds all halfway through their turn at once.
        for (p in 0..2) assertTrue("wheel $p", Odometer.roll(99.5f, p) in 0.4f..0.6f)
        // But 90.5 has only the units wheel moving.
        assertTrue(Odometer.roll(90.5f, 0) in 0.4f..0.6f)
        assertEquals(0f, Odometer.roll(90.5f, 1), 0f)
        assertEquals(0f, Odometer.roll(90.5f, 2), 0f)
    }

    @Test
    fun aLeadingBlankWheelRollsInTheNewDigit() {
        // 9.95: no tens digit yet (turns 0), but the wheel is about to bring in a 1.
        assertEquals(0, Odometer.turns(9.95f, 1))
        assertTrue(Odometer.roll(9.95f, 1) > 0.7f)
        assertEquals(1, Odometer.turns(10f, 1))
        assertEquals(0f, Odometer.roll(10f, 1), 0f)
    }

    @Test
    fun negativeAndHugeValuesStayInRange() {
        assertEquals(0, Odometer.turns(-5f, 0))
        assertEquals(0f, Odometer.roll(-5f, 0), 0f)
        assertEquals(0, Odometer.turns(5f, 12) )
        assertTrue(Odometer.roll(2_000_000_000f, 9) in 0f..1f)
    }

    @Test
    fun digitCounts() {
        assertEquals(1, Odometer.digitCount(0))
        assertEquals(1, Odometer.digitCount(9))
        assertEquals(2, Odometer.digitCount(10))
        assertEquals(3, Odometer.digitCount(999))
        assertEquals(4, Odometer.digitCount(1000))
        assertEquals(1, Odometer.digitCount(-40))
    }

    @Test
    fun bigJumpsRollLongerButNeverForever() {
        assertTrue(rollMillis(0f, 1f) in 300..400)
        assertTrue(rollMillis(0f, 30f) > rollMillis(0f, 1f))
        assertEquals(1100, rollMillis(0f, 1_000_000f))
        assertEquals(rollMillis(5f, 0f), rollMillis(0f, 5f))
    }

    // ---- the arc a coin flies

    @Test
    fun aFlightStartsAndEndsExactlyWhereItShould() {
        assertEquals(0f, FlyPath.ease(0f), 0f)
        assertEquals(1f, FlyPath.ease(1f), 1e-6f)
        assertEquals(0.5f, FlyPath.ease(0.5f), 1e-6f)
        assertEquals(10f, FlyPath.bezier(10f, 50f, 90f, 0f), 0f)
        assertEquals(90f, FlyPath.bezier(10f, 50f, 90f, 1f), 1e-4f)
    }

    @Test
    fun easingNeverGoesBackwardsOrOutOfRange() {
        var prev = 0f
        for (i in 0..100) {
            val e = FlyPath.ease(i / 100f)
            assertTrue(e >= prev - 1e-6f && e in 0f..1f)
            prev = e
        }
        assertEquals(0f, FlyPath.ease(-3f), 0f)
        assertEquals(1f, FlyPath.ease(9f), 1e-6f)
    }

    @Test
    fun theArcRisesAboveTheStraightLineBetweenTheEnds() {
        // From (0, 300) to (300, 300): a level flight must lift (smaller y) in the middle.
        val lift = FlyPath.lift(300f, 0f)
        assertTrue(lift >= 56f)
        val midY = FlyPath.bezier(300f, 300f - lift, 300f, 0.5f)
        assertTrue("mid y $midY", midY < 300f - lift / 3f)
        // A short hop still gets a visible arc.
        assertEquals(56f, FlyPath.lift(10f, 10f), 0f)
    }
}
