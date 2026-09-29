package com.pocketarcade.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The daily bonus moment's pacing: tokens landing, the counter ticking, the card coming and going. */
class DailyBonusTest {
    private val granted = 10

    @Test
    fun aBonusShowsAsManyTokensAsItIsWorthUpToACap() {
        assertEquals(0, DailyBonus.tokenCount(0))
        assertEquals(7, DailyBonus.tokenCount(7))
        assertEquals(DailyBonus.MAX_TOKENS, DailyBonus.tokenCount(10))
        assertEquals(DailyBonus.MAX_TOKENS, DailyBonus.tokenCount(250))
        assertEquals(0, DailyBonus.tokenCount(-3))
    }

    @Test
    fun tokensAreLetGoOneAfterAnotherAndTouchDownInOrder() {
        for (i in 1 until DailyBonus.MAX_TOKENS) {
            assertTrue(DailyBonus.startTime(i) > DailyBonus.startTime(i - 1))
            assertTrue(DailyBonus.contactTime(i) > DailyBonus.contactTime(i - 1))
        }
        for (i in 0 until DailyBonus.MAX_TOKENS) {
            // The first touch is before the fall is over: the rest of it is the bounce.
            assertTrue(DailyBonus.contactTime(i) > DailyBonus.startTime(i))
            assertTrue(DailyBonus.contactTime(i) < DailyBonus.startTime(i) + DailyBonus.FALL_SECONDS)
        }
    }

    @Test
    fun theLandedCountRisesFromNoneToAllAndNeverFalls() {
        assertEquals(0, DailyBonus.landed(0f, granted))
        var last = 0
        var t = 0f
        while (t < 10f) {
            val l = DailyBonus.landed(t, granted)
            assertTrue(l >= last && l - last <= 1)
            last = l
            t += 0.01f
        }
        assertEquals(DailyBonus.tokenCount(granted), last)
    }

    @Test
    fun theCounterTicksUpASteadyStepAtATimeAndEndsExactlyOnTheBonus() {
        assertEquals(0, DailyBonus.counter(0f, granted, false))
        var last = 0
        var t = 0f
        while (t < 10f) {
            val c = DailyBonus.counter(t, granted, false)
            assertTrue("$c after $last at $t", c >= last)
            last = c
            t += 0.01f
        }
        assertEquals(granted, last)
    }

    @Test
    fun aBigBonusStillCountsToItsRealTotal() {
        // 25 tokens: only ten tumble, but the counter reaches 25 as the last lands.
        var last = 0
        var t = 0f
        while (t < 10f) {
            val c = DailyBonus.counter(t, 25, false)
            assertTrue(c >= last && c <= 25)
            last = c
            t += 0.01f
        }
        assertEquals(25, last)
        assertEquals(25, DailyBonus.counter(DailyBonus.contactTime(9), 25, false))
        assertTrue(DailyBonus.counter(DailyBonus.contactTime(8), 25, false) < 25)
    }

    @Test
    fun aCalmCounterReadsTheTotalFromTheStart() {
        assertEquals(granted, DailyBonus.counter(0f, granted, true))
        assertEquals(granted, DailyBonus.counter(1f, granted, true))
    }

    @Test
    fun theCardArrivesHoldsAndLeavesWithinItsTime() {
        for (calm in listOf(false, true)) {
            val total = DailyBonus.totalSeconds(granted, calm)
            assertEquals(0f, DailyBonus.cardAlpha(0f, granted, calm), 0f)
            assertEquals(0f, DailyBonus.cardAlpha(total, granted, calm), 1e-6f)
            // Fully there in the middle of the hold.
            val mid = (total - DailyBonus.LEAVE_SECONDS) - 0.3f
            assertEquals(1f, DailyBonus.cardAlpha(mid, granted, calm), 1e-6f)
            var t = 0f
            while (t <= total) {
                assertTrue(DailyBonus.cardAlpha(t, granted, calm) in 0f..1f)
                t += 0.02f
            }
        }
    }

    @Test
    fun theWholeMomentIsAFewSecondsAndACalmOneIsNoLongerThanNecessary() {
        val full = DailyBonus.totalSeconds(granted, false)
        assertTrue("$full s", full in 3f..5f)
        val calm = DailyBonus.totalSeconds(granted, true)
        assertTrue("$calm s", calm in 2f..4f)
    }

    @Test
    fun theLastTokenSettlesBeforeTheCardStartsToLeave() {
        val settle = DailyBonus.settledTime(granted)
        val total = DailyBonus.totalSeconds(granted, false)
        assertTrue(settle + DailyBonus.HOLD_SECONDS <= total - DailyBonus.LEAVE_SECONDS + 1e-4f)
        assertEquals(0f, DailyBonus.settledTime(0), 0f)
    }

    @Test
    fun aTokenFallsInBouncesAndComesToRest() {
        assertEquals(0f, DailyBonus.bounce(0f), 1e-6f)
        assertEquals(1f, DailyBonus.bounce(1f), 1e-6f)
        var maxAfterContact = 0f
        var dipped = false
        var last = 0f
        var p = 0f
        while (p <= 1f) {
            val b = DailyBonus.bounce(p)
            assertTrue("$b at $p", b in 0f..1.0001f)
            // Reaches the floor at the first contact, then rises again (the bounce).
            if (p > DailyBonus.CONTACT + 0.02f) {
                if (b < last - 1e-4f) dipped = true
                maxAfterContact = maxOf(maxAfterContact, b)
            }
            last = b
            p += 0.005f
        }
        assertTrue("it bounces", dipped)
        assertEquals(1f, DailyBonus.bounce(DailyBonus.CONTACT), 0.01f)
    }

    @Test
    fun aTokenFlipsAsItFallsAndLandsFaceUp() {
        var narrowest = 1f
        var p = 0f
        while (p < 1f) {
            val f = DailyBonus.flip(p)
            assertTrue("$f at $p", f in 0.12f..1f)
            narrowest = minOf(narrowest, f)
            p += 0.005f
        }
        assertTrue("goes edge-on ($narrowest)", narrowest < 0.3f)
        assertEquals(1f, DailyBonus.flip(1f), 0f)
    }

    @Test
    fun fallProgressIsZeroBeforeReleaseAndOneOnceSettled() {
        assertEquals(0f, DailyBonus.fallProgress(3, 0f), 0f)
        assertEquals(1f, DailyBonus.fallProgress(3, 100f), 0f)
        assertEquals(0f, DailyBonus.fallProgress(3, DailyBonus.startTime(3)), 0f)
    }

    @Test
    fun theCounterBumpsAtEachLandingAndThenSettles() {
        assertEquals(0f, DailyBonus.pulse(0f, granted), 0f)
        val at = DailyBonus.contactTime(2)
        assertEquals(1f, DailyBonus.pulse(at, granted), 1e-4f)
        assertTrue(DailyBonus.pulse(at + DailyBonus.PULSE_SECONDS / 2f, granted) < 0.5f)
        assertEquals(0f, DailyBonus.pulse(DailyBonus.contactTime(9) + DailyBonus.PULSE_SECONDS + 0.05f, granted), 0f)
    }

    @Test
    fun theTokensRestAcrossTheCardInOrderAndInsideIt() {
        val n = DailyBonus.MAX_TOKENS
        var last = 0f
        for (i in 0 until n) {
            val x = DailyBonus.restX(i, n)
            assertTrue(x > last && x in 0.1f..0.9f)
            last = x
            assertTrue(DailyBonus.restLift(i) in 0f..1f)
        }
        // A single token sits in the middle.
        assertEquals(0.5f, DailyBonus.restX(0, 1), 1e-6f)
    }
}
