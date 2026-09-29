package com.pocketarcade.engine.gl

import com.pocketarcade.engine.gl.GfxQuality.Reflections
import com.pocketarcade.engine.gl.GfxQuality.Tier
import com.pocketarcade.engine.r3d.RenderPass
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GfxQualityTest {
    @After
    fun restore() {
        GfxQuality.frameCap = 0
        GfxQuality.tier = Tier.AUTO
    }

    @Test
    fun rungZeroIsHowTheRendererLookedBeforeTiersExisted() {
        val r = GfxQuality.LADDER[0]
        assertEquals(0.5f, r.scaleFloor, 0f)
        assertEquals(0.8f, r.scaleCeiling, 0f)
        assertEquals(1f, r.scaleBoost, 0f)
        assertEquals(4, r.msaa)
        assertEquals(4, r.bloomOctaves)
        assertEquals(Reflections.MIRROR, r.reflections)
        assertEquals(RenderPass.MAX_LIGHTS, r.lights)
        // And it is where the HDR picture and its whole cinematic finish live.
        assertTrue(r.hdr)
        assertTrue(r.glare)
        assertTrue(r.film)
    }

    @Test
    fun theFinishGoesFirstThenHdrThenMultisampling() {
        val l = GfxQuality.LADDER
        // Rung 1 keeps HDR and full multisampling but drops the glare and the film finish.
        assertTrue(l[1].hdr)
        assertFalse(l[1].glare)
        assertFalse(l[1].film)
        assertEquals(4, l[1].msaa)
        // Rung 2 is the LDR picture; multisampling falls with it, never before it.
        assertFalse(l[2].hdr)
        assertTrue(l[2].msaa < l[1].msaa)
        for (i in 0 until l.size) if (l[i].msaa < 4) assertFalse("HDR with reduced msaa at $i", l[i].hdr)
    }

    @Test
    fun batteryNeverStartsOnAnHdrRung() {
        for (i in GfxQuality.BATTERY_TOP until GfxQuality.LADDER.size) assertFalse("rung $i", GfxQuality.LADDER[i].hdr)
        assertFalse(GfxQuality.LADDER[GfxQuality.startRung(Tier.BATTERY, 0)].hdr)
    }

    @Test
    fun theFinishLeversNeedHdr() {
        for (r in GfxQuality.LADDER) if (!r.hdr) {
            assertFalse(r.glare)
            assertFalse(r.film)
        }
    }

    @Test
    fun everyRungIsNoBetterThanTheOneAbove() {
        for (i in 1 until GfxQuality.LADDER.size) {
            val up = GfxQuality.LADDER[i - 1]
            val r = GfxQuality.LADDER[i]
            assertTrue("floor at $i", r.scaleFloor <= up.scaleFloor)
            assertTrue("ceiling at $i", r.scaleCeiling <= up.scaleCeiling)
            assertTrue("boost at $i", r.scaleBoost <= up.scaleBoost)
            assertTrue("boost above ceiling at $i", r.scaleBoost >= r.scaleCeiling)
            assertTrue("msaa at $i", r.msaa <= up.msaa)
            assertTrue("bloom at $i", r.bloomOctaves <= up.bloomOctaves)
            assertTrue("reflections at $i", r.reflections.ordinal >= up.reflections.ordinal)
            assertTrue("lights at $i", r.lights <= up.lights)
            assertTrue("hdr at $i", !r.hdr || up.hdr)
            assertTrue("glare at $i", !r.glare || up.glare)
            assertTrue("film at $i", !r.film || up.film)
            assertTrue("floor under ceiling at $i", r.scaleFloor < r.scaleCeiling)
        }
    }

    @Test
    fun leversStayWithinWhatTheRendererCanDo() {
        for (r in GfxQuality.LADDER) {
            assertTrue(r.msaa == 0 || r.msaa == 2 || r.msaa == 4)
            assertTrue(r.bloomOctaves in 2..4)
            assertTrue(r.lights in 1..RenderPass.MAX_LIGHTS)
        }
    }

    @Test
    fun tiersSetTheRangeOfTheLadder() {
        val last = GfxQuality.LADDER.lastIndex
        assertEquals(0, GfxQuality.topRung(Tier.AUTO))
        assertEquals(last, GfxQuality.bottomRung(Tier.AUTO))
        assertEquals(GfxQuality.BATTERY_TOP, GfxQuality.topRung(Tier.BATTERY))
        assertEquals(last, GfxQuality.bottomRung(Tier.BATTERY))
        assertEquals(0, GfxQuality.topRung(Tier.QUALITY))
        assertEquals(0, GfxQuality.bottomRung(Tier.QUALITY))
    }

    @Test
    fun startingRungFollowsTheTierAndTheDevice() {
        // AUTO trusts the device; BATTERY never starts above its top; QUALITY always starts at the best.
        assertEquals(0, GfxQuality.startRung(Tier.AUTO, 0))
        assertEquals(3, GfxQuality.startRung(Tier.AUTO, 3))
        assertEquals(GfxQuality.BATTERY_TOP, GfxQuality.startRung(Tier.BATTERY, 0))
        assertEquals(4, GfxQuality.startRung(Tier.BATTERY, 4))
        assertEquals(0, GfxQuality.startRung(Tier.QUALITY, 4))
    }

    @Test
    fun theDeviceSuggestionIsCautiousAboutOldAndSoftwareRenderers() {
        val last = GfxQuality.LADDER.lastIndex
        assertEquals(0, GfxQuality.deviceRung("Adreno (TM) 740", false))
        assertEquals(0, GfxQuality.deviceRung("Adreno (TM) 618", false))
        assertEquals(0, GfxQuality.deviceRung("Mali-G78", false))
        assertEquals(0, GfxQuality.deviceRung("Android Emulator OpenGL ES Translator (Apple M2 Pro)", false))
        assertEquals(0, GfxQuality.deviceRung("", false))
        assertEquals(2, GfxQuality.deviceRung("Adreno (TM) 306", false))
        assertEquals(2, GfxQuality.deviceRung("Adreno (TM) 420", false))
        assertEquals(2, GfxQuality.deviceRung("Mali-T760", false))
        assertEquals(2, GfxQuality.deviceRung("Mali-400 MP", false))
        assertEquals(2, GfxQuality.deviceRung("PowerVR SGX 544MP", false))
        assertEquals(2, GfxQuality.deviceRung("Adreno (TM) 740", true))
        assertEquals(last, GfxQuality.deviceRung("Google SwiftShader", false))
        assertEquals(last, GfxQuality.deviceRung("llvmpipe (LLVM 15.0.7, 256 bits)", true))
        assertEquals(last, GfxQuality.deviceRung("ANGLE (Google, Vulkan 1.3.0 (SwiftShader Device (Subzero)))", false))
    }

    @Test
    fun theCapIsThirtyOrSixtyAndAutoMeansSixty() {
        GfxQuality.frameCap = 0
        assertEquals(60, GfxQuality.effectiveCap())
        GfxQuality.frameCap = 30
        assertEquals(30, GfxQuality.effectiveCap())
        GfxQuality.frameCap = 60
        assertEquals(60, GfxQuality.effectiveCap())
        GfxQuality.frameCap = 45 // not an offered value
        assertEquals(60, GfxQuality.effectiveCap())
    }

    @Test
    fun aCapOnlyBitesOnADisplayFastEnoughForIt() {
        assertTrue(GfxQuality.capApplies(60, 120f))
        assertTrue(GfxQuality.capApplies(60, 144f))
        assertTrue(GfxQuality.capApplies(30, 60f))
        assertTrue(GfxQuality.capApplies(30, 90f))
        assertFalse(GfxQuality.capApplies(60, 60f))
        // 90 Hz would be pushed down to 45 by the vsync grid: better left alone.
        assertFalse(GfxQuality.capApplies(60, 90f))
    }

    // ------------------------------------------------------------------ FrameGate

    /** How many of the frames a [hz] display offers for [seconds] the gate lets through. */
    private fun accepted(cap: Int, hz: Float, seconds: Int = 4, jitterNs: Long = 0L): Int {
        val gate = FrameGate()
        val period = (1e9 / hz).toLong()
        val frames = (hz * seconds).toInt()
        var taken = 0
        for (k in 1..frames) {
            // A small deterministic wobble, as a real choreographer has.
            val wobble = if (jitterNs == 0L) 0L else ((k * 7919) % 5 - 2) * jitterNs / 2
            if (gate.due(k * period + wobble, cap, hz)) taken++
        }
        return taken
    }

    @Test
    fun aSixtyFpsCapOnA120HzDisplayDrawsEverySecondFrame() {
        assertEquals(240, accepted(60, 120f, 4))
        assertEquals(240, accepted(60, 120f, 4, jitterNs = 1_000_000L))
    }

    @Test
    fun aThirtyFpsCapDrawsEveryFourthFrameOn120HzAndEverySecondOn60() {
        assertEquals(120, accepted(30, 120f, 4))
        assertEquals(120, accepted(30, 60f, 4))
        assertEquals(120, accepted(30, 120f, 4, jitterNs = 1_000_000L))
        assertEquals(120, accepted(30, 90f, 4))
    }

    @Test
    fun noCapWhereTheDisplayIsNotFastEnough() {
        assertEquals(240, accepted(60, 60f, 4))
        assertEquals(360, accepted(60, 90f, 4))
    }

    @Test
    fun theGateSaysHowLongToWait() {
        val gate = FrameGate()
        val ms = 1_000_000L
        // Nothing taken yet: due at once.
        assertEquals(0L, gate.waitNs(5 * ms, 60, 120f))
        gate.take(100 * ms)
        // 60 fps is 16.67 ms; less the 3 ms slack, 13.67 ms must pass.
        assertEquals(13_666_666L, gate.waitNs(100 * ms, 60, 120f))
        assertEquals(3_666_666L, gate.waitNs(110 * ms, 60, 120f))
        assertEquals(0L, gate.waitNs(114 * ms, 60, 120f))
        // A clock that stepped back never asks for more than one interval.
        assertEquals(13_666_666L, gate.waitNs(50 * ms, 60, 120f))
        // Uncapped displays never wait.
        assertEquals(0L, gate.waitNs(100 * ms, 60, 60f))
    }
}
