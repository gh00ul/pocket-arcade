package com.pocketarcade.engine.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/** The pan law, the distance curve and the bearing maths that put a sound in the stereo field. */
class SpatialTest {
    private val p = Placement()
    private val overhead = PI.toFloat()

    @Test
    fun attenuationIsFullNearZeroFarAndNeverRises() {
        assertEquals(1f, Spatial.attenuation(0f), 0f)
        assertEquals(1f, Spatial.attenuation(Spatial.REF_DISTANCE), 0f)
        assertEquals(0f, Spatial.attenuation(Spatial.MAX_DISTANCE), 0f)
        assertEquals(0f, Spatial.attenuation(Spatial.MAX_DISTANCE * 5f), 0f)
        var last = 1f
        var d = 0f
        while (d < Spatial.MAX_DISTANCE * 1.5f) {
            val a = Spatial.attenuation(d)
            assertTrue("attenuation rose at $d", a <= last + 1e-6f)
            assertTrue(a in 0f..1f)
            last = a
            d += 1f
        }
    }

    @Test
    fun panLawKeepsPowerConstant() {
        var pan = -1f
        while (pan <= 1f) {
            Spatial.panGains(pan, p)
            assertEquals("power at pan $pan", 1f, p.left * p.left + p.right * p.right, 1e-5f)
            pan += 0.05f
        }
        Spatial.panGains(0f, p)
        assertEquals(p.left, p.right, 1e-6f)
        Spatial.panGains(-1f, p)
        assertEquals(1f, p.left, 1e-6f)
        assertEquals(0f, p.right, 1e-6f)
        Spatial.panGains(1f, p)
        assertEquals(0f, p.left, 1e-6f)
        assertEquals(1f, p.right, 1e-6f)
        // Out-of-range pans are clamped, not extrapolated.
        Spatial.panGains(7f, p)
        assertEquals(1f, p.right, 1e-6f)
    }

    @Test
    fun aSoundDeadAheadIsCentredAndUnityLoudWhenClose() {
        // Facing +z, a source 20 units straight ahead.
        Spatial.place(100f, 100f, 0f, 100f, 120f, p)
        assertEquals(p.left, p.right, 1e-5f)
        assertEquals(1f, p.left, 1e-4f)
        assertEquals(20f, p.distance, 1e-4f)
    }

    @Test
    fun overheadRightIsPlusXAndLeftIsMinusX() {
        // The overhead camera looks at the back wall (yaw pi): screen right is +x.
        Spatial.place(300f, 500f, overhead, 400f, 500f, p)
        assertTrue("right ear should win: ${p.left} ${p.right}", p.right > p.left * 5f)
        assertTrue(p.pan > 0.9f)
        Spatial.place(300f, 500f, overhead, 200f, 500f, p)
        assertTrue("left ear should win: ${p.left} ${p.right}", p.left > p.right * 5f)
        assertTrue(p.pan < -0.9f)
    }

    @Test
    fun firstPersonFacingTheEntranceHasMinusXOnTheRight() {
        // Yaw 0 faces +z, and the strafe-right direction is (-1, 0) (see HubCamera.moveRelative).
        Spatial.place(300f, 500f, 0f, 200f, 500f, p)
        assertTrue(p.right > p.left * 5f)
        Spatial.place(300f, 500f, 0f, 400f, 500f, p)
        assertTrue(p.left > p.right * 5f)
    }

    @Test
    fun turningTheListenerSwingsTheSound() {
        // A source due east (+x). Facing north (yaw pi) it is on the right; turn to face east (yaw pi/2 has
        // forward (1, 0)) and it is dead ahead; face south (yaw 0) and it is on the left.
        Spatial.place(300f, 500f, overhead, 400f, 500f, p)
        val north = p.pan
        Spatial.place(300f, 500f, (PI / 2).toFloat(), 400f, 500f, p)
        assertEquals(0f, p.pan, 1e-4f)
        Spatial.place(300f, 500f, 0f, 400f, 500f, p)
        assertTrue(north > 0.9f && p.pan < -0.9f)
    }

    @Test
    fun soundsBehindAreQuieterThanTheSameDistanceAhead() {
        Spatial.place(300f, 500f, 0f, 300f, 600f, p)
        val ahead = p.left
        val aheadDist = p.distance
        Spatial.place(300f, 500f, 0f, 300f, 400f, p)
        assertEquals(aheadDist, p.distance, 1e-4f)
        // Dead behind, so still centred but shadowed.
        assertEquals(p.left, p.right, 1e-4f)
        assertTrue("behind ${p.left} should be quieter than ahead $ahead", p.left < ahead * 0.85f)
        assertTrue(p.left > ahead * 0.6f)
    }

    @Test
    fun farSourcesAreSilentAndFlaggedSo() {
        Spatial.place(0f, 0f, 0f, 0f, Spatial.MAX_DISTANCE + 1f, p)
        assertTrue(p.silent)
        assertEquals(0f, p.send, 0f)
        Spatial.place(0f, 0f, 0f, 0f, 60f, p)
        assertTrue(!p.silent)
    }

    @Test
    fun levelFallsMonotonicallyWithDistanceAtAnyBearing() {
        for (yaw in floatArrayOf(0f, 0.7f, 2f, overhead, 5f)) {
            for (bearing in 0 until 12) {
                val a = bearing * (2f * PI.toFloat() / 12f)
                var last = Float.MAX_VALUE
                var d = 5f
                while (d < Spatial.MAX_DISTANCE + 20f) {
                    Spatial.place(0f, 0f, yaw, kotlin.math.sin(a) * d, kotlin.math.cos(a) * d, p)
                    val power = p.left * p.left + p.right * p.right
                    assertTrue("yaw $yaw bearing $bearing rose at $d", power <= last + 1e-5f)
                    last = power
                    d += 5f
                }
            }
        }
    }

    @Test
    fun farSoundsKeepMoreOfTheirReverbThanOfTheirDrySignal() {
        Spatial.place(0f, 0f, 0f, 0f, 100f, p)
        val near = p.send / (p.left * p.left)
        Spatial.place(0f, 0f, 0f, 0f, 300f, p)
        val far = p.send / (p.left * p.left)
        assertTrue("wet/dry should grow with distance", far > near * 3f)
    }

    @Test
    fun aSourceAtTheListenersFeetDoesNotFlipChannels() {
        // Walking past at arm's length: the pan eases through centre instead of jumping.
        var last = 0f
        for (i in -10..10) {
            Spatial.place(0f, 0f, overhead, i * 1f, 2f, p)
            assertTrue(abs(p.pan - last) < 0.35f)
            last = p.pan
        }
        assertTrue(abs(p.pan) <= 1f)
    }
}
