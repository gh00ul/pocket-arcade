package com.pocketarcade.ui

import com.pocketarcade.data.Catalog
import com.pocketarcade.data.SaveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The title's clock: the sign lighting, the prompt, the exit, the dust and the welcome line. */
class TitleTimelineTest {
    private val start = TitleTimeline.POCKET_START

    @Test
    fun smoothstepHoldsItsEndsAndRisesBetween() {
        assertEquals(0f, smoothstep(1f, 3f, 0f), 0f)
        assertEquals(1f, smoothstep(1f, 3f, 9f), 0f)
        assertEquals(0.5f, smoothstep(1f, 3f, 2f), 1e-6f)
    }

    // ---------------------------------------------------------------- the sign

    @Test
    fun aTubeIsAGhostBeforeItsTurnAndSteadyAfterItsFlicker() {
        for (i in 0 until 6) {
            assertEquals(TitleTimeline.UNLIT, TitleTimeline.letterOn(0f, start, i, false), 0f)
            val settled = start + i * TitleTimeline.LETTER_STAGGER + TitleTimeline.IGNITE_SECONDS
            assertEquals(1f, TitleTimeline.letterOn(settled, start, i, false), 0f)
            assertEquals(1f, TitleTimeline.letterOn(settled + 100f, start, i, false), 0f)
        }
    }

    @Test
    fun theLettersLightOneAfterAnotherLeftToRight() {
        // Part way through the first letter's ignition the last is still a ghost.
        val t = start + 0.3f
        assertTrue(TitleTimeline.letterOn(t, start, 0, false) > TitleTimeline.UNLIT)
        assertEquals(TitleTimeline.UNLIT, TitleTimeline.letterOn(start + 5 * TitleTimeline.LETTER_STAGGER - 0.001f, start, 5, false), 0f)
    }

    @Test
    fun anIgnitingTubeStaysWithinRangeAndActuallyStutters() {
        var dropped = false
        var last = 0f
        var t = start
        while (t < start + TitleTimeline.IGNITE_SECONDS) {
            val v = TitleTimeline.letterOn(t, start, 0, false)
            assertTrue("$v at $t", v in 0f..1f)
            if (v < last - 1e-4f) dropped = true
            last = v
            t += 0.004f
        }
        assertTrue("a neon tube flickers on, it doesn't just fade", dropped)
    }

    @Test
    fun withReducedMotionTheSignFadesInTogetherWithoutAFlicker() {
        var last = 0f
        var t = 0f
        while (t < start + TitleTimeline.CALM_FADE_SECONDS + 1f) {
            val a = TitleTimeline.letterOn(t, start, 0, true)
            val b = TitleTimeline.letterOn(t, start, 5, true)
            // Every letter alike (no stagger), only ever brighter.
            assertEquals(a, b, 0f)
            assertTrue("$a after $last at $t", a >= last - 1e-6f)
            last = a
            t += 0.01f
        }
        assertEquals(1f, last, 1e-6f)
        assertEquals(TitleTimeline.UNLIT, TitleTimeline.letterOn(0f, start, 0, true), 0f)
    }

    @Test
    fun theGlowBreathesWithinAGentleRange() {
        var lo = 2f
        var hi = 0f
        var t = 0f
        while (t < TitleTimeline.BREATH_PERIOD * 2f) {
            val b = TitleTimeline.breath(t, false)
            lo = minOf(lo, b)
            hi = maxOf(hi, b)
            t += 0.02f
        }
        assertTrue(hi <= 1f + 1e-6f)
        assertTrue("dips no lower than ${1f - TitleTimeline.BREATH_DEPTH}", lo >= 1f - TitleTimeline.BREATH_DEPTH - 1e-3f)
        assertTrue("actually breathes", hi - lo > 0.08f)
        // Calm breathes half as deep.
        var calmLo = 2f
        t = 0f
        while (t < TitleTimeline.BREATH_PERIOD) {
            calmLo = minOf(calmLo, TitleTimeline.breath(t, true))
            t += 0.02f
        }
        assertTrue(calmLo > lo)
    }

    @Test
    fun theSweepCrossesLeftToRightThenRestsAndRepeats() {
        assertEquals(-1f, TitleTimeline.sweep(0f, false), 0f)
        assertEquals(-1f, TitleTimeline.sweep(TitleTimeline.SWEEP_START - 0.01f, false), 0f)
        val s = TitleTimeline.SWEEP_START
        assertEquals(0f, TitleTimeline.sweep(s, false), 1e-6f)
        var last = -0.001f
        var t = s
        while (t <= s + TitleTimeline.SWEEP_SECONDS) {
            val v = TitleTimeline.sweep(t, false)
            assertTrue("$v at $t", v in 0f..1f)
            assertTrue(v >= last)
            last = v
            t += 0.01f
        }
        // Resting between sweeps, and again a period later.
        assertEquals(-1f, TitleTimeline.sweep(s + TitleTimeline.SWEEP_SECONDS + 0.5f, false), 0f)
        assertEquals(TitleTimeline.sweep(s + 0.4f, false), TitleTimeline.sweep(s + TitleTimeline.SWEEP_PERIOD + 0.4f, false), 1e-4f)
    }

    @Test
    fun reducedMotionHasNoSweepAndNoBuzz() {
        var t = 0f
        while (t < 40f) {
            assertEquals(-1f, TitleTimeline.sweep(t, true), 0f)
            for (i in 0 until 6) assertEquals(1f, TitleTimeline.buzz(t, i, 6, 0, true), 0f)
            t += 0.05f
        }
    }

    @Test
    fun theOddTubeBuzzsBrieflyAndOnlyOne() {
        var buzzes = 0
        var t = TitleTimeline.SWEEP_START + 0.01f
        while (t < 60f) {
            var dipped = 0
            for (i in 0 until 6) if (TitleTimeline.buzz(t, i, 6, 1, false) < 1f) dipped++
            assertTrue("at most one tube at a time ($dipped at $t)", dipped <= 1)
            if (dipped == 1) buzzes++
            t += 0.01f
        }
        assertTrue("it happens now and then", buzzes > 0)
        // And is brief: a few percent of the time at most.
        assertTrue("$buzzes hundredths", buzzes < 60 * 100 * 0.05)
    }

    @Test
    fun theTaglineFadesInAfterTheSign() {
        assertEquals(0f, TitleTimeline.taglineAlpha(TitleTimeline.ARCADE_START), 0f)
        assertEquals(1f, TitleTimeline.taglineAlpha(TitleTimeline.TAGLINE_START + TitleTimeline.TAGLINE_FADE + 1f), 0f)
    }

    // ---------------------------------------------------------------- the prompt

    @Test
    fun thePromptWaitsForTheSignThenPulsesWithinRange() {
        assertEquals(0f, TitleTimeline.promptAlpha(0f, false), 0f)
        assertEquals(0f, TitleTimeline.promptAlpha(TitleTimeline.PROMPT_START, false), 0f)
        var lo = 2f
        var hi = 0f
        var t = TitleTimeline.PROMPT_START + TitleTimeline.PROMPT_FADE + 0.1f
        while (t < 30f) {
            val a = TitleTimeline.promptAlpha(t, false)
            assertTrue("$a", a in 0f..1f)
            lo = minOf(lo, a)
            hi = maxOf(hi, a)
            t += 0.01f
        }
        assertTrue("pulses (${hi - lo})", hi - lo > 0.3f)
        assertTrue("never vanishes once up ($lo)", lo > 0.4f)
    }

    @Test
    fun theCalmPromptPulsesLessAndDoesNotSwell() {
        var lo = 2f
        var hi = 0f
        var t = TitleTimeline.PROMPT_START + TitleTimeline.PROMPT_FADE + 0.1f
        while (t < 30f) {
            val a = TitleTimeline.promptAlpha(t, true)
            lo = minOf(lo, a)
            hi = maxOf(hi, a)
            assertEquals(1f, TitleTimeline.promptScale(t, true), 0f)
            t += 0.01f
        }
        assertTrue(hi - lo < 0.3f)
        var swell = 1f
        t = TitleTimeline.PROMPT_START
        while (t < 20f) {
            swell = maxOf(swell, TitleTimeline.promptScale(t, false))
            t += 0.01f
        }
        assertTrue("swells a little ($swell)", swell in 1.02f..1.05f)
    }

    // ---------------------------------------------------------------- leaving

    @Test
    fun theSignLeavesAndTheSmallPrintLeavesFirst() {
        assertEquals(1f, TitleTimeline.logoExitAlpha(0f), 0f)
        assertEquals(0f, TitleTimeline.logoExitAlpha(0.6f), 0f)
        assertEquals(0f, TitleTimeline.logoExitAlpha(1f), 0f)
        assertEquals(1f, TitleTimeline.uiExitAlpha(0f), 0f)
        assertEquals(0f, TitleTimeline.uiExitAlpha(0.32f), 0f)
        var last = 1f
        for (i in 0..100) {
            val a = TitleTimeline.logoExitAlpha(i / 100f)
            assertTrue(a <= last + 1e-6f)
            last = a
            assertTrue(TitleTimeline.uiExitAlpha(i / 100f) <= a + 1e-6f || i / 100f > 0.6f)
        }
        assertEquals(0f, TitleTimeline.logoExitLift(0f), 0f)
        assertTrue(TitleTimeline.logoExitLift(1f) < 0f)
        assertEquals(1f, TitleTimeline.logoExitScale(0f), 0f)
        assertTrue(TitleTimeline.logoExitScale(1f) > 1f)
    }

    // ---------------------------------------------------------------- dust

    @Test
    fun everyMoteStaysOnScreenSpaceAndDimlyLit() {
        for (i in 0 until DustField.COUNT) {
            var t = 0f
            while (t < 60f) {
                val x = DustField.x(i, t, 137f, false)
                val y = DustField.y(i, t, false)
                assertTrue("x $x", x >= 0f && x < 1f)
                assertTrue("y $y", y >= 0f && y < 1f)
                val a = DustField.alpha(i, t, false)
                assertTrue("alpha $a", a in 0f..0.4f)
                t += 1.7f
            }
            assertTrue(DustField.depth(i) in 0.3f..1f)
            assertTrue(DustField.radius(i) > 0f)
        }
    }

    @Test
    fun aNearMoteSlidesFurtherThanAFarOneWhenTheCameraMoves() {
        // Pick the nearest and the farthest mote.
        val near = (0 until DustField.COUNT).maxByOrNull { DustField.depth(it) }!!
        val far = (0 until DustField.COUNT).minByOrNull { DustField.depth(it) }!!
        fun slide(i: Int): Float {
            val a = DustField.x(i, 5f, 0f, false)
            val b = DustField.x(i, 5f, 60f, false)
            var d = abs(b - a)
            if (d > 0.5f) d = 1f - d
            return d
        }
        assertTrue(slide(near) > slide(far))
    }

    @Test
    fun aCalmFieldNeverMoves() {
        for (i in 0 until DustField.COUNT) {
            assertEquals(DustField.x(i, 0f, 0f, true), DustField.x(i, 99f, 250f, true), 0f)
            assertEquals(DustField.y(i, 0f, true), DustField.y(i, 99f, true), 0f)
            assertEquals(DustField.alpha(i, 0f, true), DustField.alpha(i, 99f, true), 0f)
        }
    }

    @Test
    fun aMoteRisesContinuouslyAndWrapsFromTopToBottom() {
        val i = 3
        var t = 0f
        var wraps = 0
        var prev = DustField.y(i, 0f, false)
        while (t < 300f) {
            t += 0.05f
            val y = DustField.y(i, t, false)
            if (y > prev) wraps++ else assertTrue("rose by ${prev - y}", prev - y < 0.01f)
            prev = y
        }
        assertTrue("wrapped at least once", wraps > 0)
    }

    @Test
    fun streamingLeavesTheCentreAndTheStartAlone() {
        for (i in 0 until DustField.COUNT) {
            assertEquals(0.31f, DustField.stream(0.31f, i, 0f), 1e-6f)
            assertEquals(0.5f, DustField.stream(0.5f, i, 1f), 1e-6f)
            // Away from the centre it moves outward as the exit runs.
            assertTrue(DustField.stream(0.8f, i, 1f) > 0.8f)
            assertTrue(DustField.stream(0.2f, i, 1f) < 0.2f)
        }
    }

    // ---------------------------------------------------------------- welcome

    @Test
    fun theWelcomeSaysNothingUntilTheSaveHasLoaded() {
        assertEquals("", TitleCopy.welcome(SaveState(loaded = false, tickets = 500)))
    }

    @Test
    fun aBrandNewPlayerIsWelcomedNotWelcomedBack() {
        assertEquals("WELCOME, PLAYER ONE!", TitleCopy.welcome(SaveState(loaded = true)))
    }

    @Test
    fun ticketsThatCanBuySomethingAreWorthMentioning() {
        val line = TitleCopy.welcome(SaveState(loaded = true, tickets = 120, totalPlays = 4))
        assertEquals("WELCOME BACK!  120 TICKETS TO SPEND", line)
    }

    @Test
    fun ticketsThatBuyNothingNewAreNotMentioned() {
        // Everything cheap is already owned, and not enough for the rest.
        val cheap = Catalog.all.filter { it.price in 1..100 }.map { it.id }.toSet()
        val save = SaveState(loaded = true, tickets = 90, totalPlays = 4, owned = cheap + Catalog.DEFAULT_OUTFIT)
        assertEquals("WELCOME BACK!", TitleCopy.welcome(save))
    }

    @Test
    fun plushProgressIsTheNextThingToMention() {
        val found = Catalog.plushies.take(3).associate { it.id to 1 }
        val save = SaveState(loaded = true, tickets = 0, totalPlays = 9, collection = found)
        assertEquals("WELCOME BACK!  3 OF ${Catalog.plushies.size} PLUSHIES FOUND", TitleCopy.welcome(save))
        val all = Catalog.plushies.associate { it.id to 2 }
        assertEquals("WELCOME BACK!  EVERY PLUSH FOUND", TitleCopy.welcome(save.copy(collection = all)))
    }

    private fun abs(v: Float) = kotlin.math.abs(v)
}
