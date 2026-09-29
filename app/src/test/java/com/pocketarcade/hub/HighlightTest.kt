package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "you are standing at this one" highlight: its easing, its strength and which prop each spot lights. */
class HighlightTest {
    private val games = GameRegistry.createAll()
    private val map = HubLayout.build(games, DecorStyle.entries.toSet())

    @Test
    fun easingIsSmoothAndStaysInRange() {
        assertEquals(0f, Highlight.ease(0f), 0f)
        assertEquals(1f, Highlight.ease(1f), 0f)
        assertEquals(0.5f, Highlight.ease(0.5f), 1e-6f)
        assertEquals(0f, Highlight.ease(-3f), 0f)
        assertEquals(1f, Highlight.ease(7f), 0f)
        var last = 0f
        for (i in 0..100) {
            val e = Highlight.ease(i / 100f)
            assertTrue("eased level must not fall", e >= last)
            last = e
        }
        // Gentle at both ends: the first hundredth moves it far less than a straight line would.
        assertTrue(Highlight.ease(0.05f) < 0.05f)
        assertTrue(Highlight.ease(0.95f) > 0.95f)
    }

    @Test
    fun fadesInAndOutOverTheFadeTime() {
        var level = 0f
        var t = 0f
        val dt = 1f / 60f
        while (level < 1f && t < 2f) {
            level = Highlight.step(level, true, dt)
            t += dt
        }
        assertEquals(1f, level, 0f)
        assertEquals(Highlight.FADE_SECONDS, t, 2f * dt)
        while (level > 0f && t < 4f) {
            level = Highlight.step(level, false, dt)
            t += dt
        }
        assertEquals(0f, level, 0f)
        assertEquals(2f * Highlight.FADE_SECONDS, t, 3f * dt)
        // A hitch doesn't overshoot, and no time passing changes nothing.
        assertEquals(1f, Highlight.step(0.9f, true, 5f), 0f)
        assertEquals(0f, Highlight.step(0.1f, false, 5f), 0f)
        assertEquals(0.4f, Highlight.step(0.4f, true, 0f), 0f)
    }

    @Test
    fun aCabinetThatIsNotHighlightedDrawsExactlyAsBefore() {
        for (i in 0..50) assertEquals(1f, Highlight.boost(0f, i * 0.3f), 0f)
    }

    @Test
    fun theBoostPulsesButStaysSmallEnoughNotToFlareTheBloom() {
        val lo = 1f + Highlight.EMISSIVE_BOOST * (1f - Highlight.PULSE_DEPTH)
        val hi = 1f + Highlight.EMISSIVE_BOOST
        var seenLow = 99f
        var seenHigh = 0f
        for (i in 0..600) {
            val b = Highlight.boost(1f, i * 0.01f)
            assertTrue("boost $b under the pulse's floor $lo", b >= lo - 1e-4f)
            assertTrue("boost $b over the pulse's top $hi", b <= hi + 1e-4f)
            seenLow = minOf(seenLow, b)
            seenHigh = maxOf(seenHigh, b)
        }
        assertTrue("the pulse should actually swing", seenHigh - seenLow > 0.5f * Highlight.EMISSIVE_BOOST * Highlight.PULSE_DEPTH)
        // The marquee glows at 1.25 and the brightest neon at 1.8: a quarter more is the most that
        // stays a highlight rather than a flare (the hall's café tiles crossed the threshold twice).
        assertTrue("boost cap", hi <= 1.25f)
        assertTrue(Highlight.POOL_ALPHA <= 0.2f)
        // Fading in raises it steadily.
        var prev = 1f
        for (i in 0..20) {
            val b = Highlight.boost(i / 20f, 0f)
            assertTrue(b >= prev - 1e-6f)
            prev = b
        }
    }

    @Test
    fun everyMachineSpotLightsExactlyItsOwnCabinet() {
        val machines = map.props.filter { it.kind == PropKind.MACHINE }
        assertTrue(machines.isNotEmpty())
        for (spot in map.spots.filter { it.type == SpotType.MACHINE }) {
            val lit = map.props.filter { Highlight.isSpotOf(spot, it) }
            assertEquals("spot for ${games[spot.machine].id} at ${spot.area.centerX}", 1, lit.size)
            assertEquals(PropKind.MACHINE, lit[0].kind)
            assertEquals(spot.machine, lit[0].machine)
        }
        // And every cabinet has a spot that lights it (the copies in a bank each have their own).
        for (p in machines) {
            val spots = map.spots.filter { Highlight.isSpotOf(it, p) }
            assertEquals("${games[p.machine].id} at ${p.x0}", 1, spots.size)
        }
    }

    @Test
    fun theKioskAndThePrizeCounterLightUpToo() {
        val tokens = map.spots.single { it.type == SpotType.TOKENS }
        val prizes = map.spots.single { it.type == SpotType.PRIZES }
        val lightByTokens = map.props.filter { Highlight.isSpotOf(tokens, it) }.map { it.kind }.toSet()
        val lightByPrizes = map.props.filter { Highlight.isSpotOf(prizes, it) }.map { it.kind }.toSet()
        assertTrue(PropKind.TOKENS in lightByTokens)
        assertEquals(setOf(PropKind.COUNTER), lightByPrizes)
        assertFalse(PropKind.MACHINE in lightByTokens)
        assertFalse(map.props.any { it.kind == PropKind.VENDING && Highlight.isSpotOf(tokens, it) })
    }

    @Test
    fun nobodyAtAnythingLightsNothing() {
        assertTrue(map.props.none { Highlight.isSpotOf(null, it) })
    }
}
