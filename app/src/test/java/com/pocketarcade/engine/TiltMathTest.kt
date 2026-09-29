package com.pocketarcade.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Tilt to steering: the lean of a phone from its gravity reading, and how far it must lean to steer. */
class TiltMathTest {
    private val deg = PI.toFloat() / 180f

    @Test
    fun aFlatPhoneLeansNowhereAndRightEdgeDownLeansRight() {
        assertEquals(0f, TiltMath.lean(0f, 0f, 9.8f), 1e-6f)
        // The right edge down 20 degrees: "up" tips towards -x in the phone's own axes.
        assertEquals(20f, TiltMath.lean(-sin(20f * deg) * 9.8f, 0f, cos(20f * deg) * 9.8f) / deg, 0.01f)
        // The left edge down 15 degrees leans left.
        assertEquals(-15f, TiltMath.lean(sin(15f * deg), 0f, cos(15f * deg)) / deg, 0.01f)
    }

    @Test
    fun anUprightPhoneTurnedLikeAWheelLeansTheSameAngle() {
        // Held upright (up is along the screen's y axis), turned clockwise 30 degrees.
        assertEquals(30f, TiltMath.lean(-sin(30f * deg), cos(30f * deg), 0f) / deg, 0.01f)
        assertEquals(-30f, TiltMath.lean(sin(30f * deg), cos(30f * deg), 0f) / deg, 0.01f)
        // Length is no matter: an accelerometer's 9.8 or a rotation matrix's 1.
        assertEquals(TiltMath.lean(-0.5f, 0.8f, 0.1f), TiltMath.lean(-4.9f, 7.84f, 0.98f), 1e-5f)
    }

    @Test
    fun steeringHasADeadZoneAndFullLockAndIsSymmetric() {
        val n = 0.1f
        // Level, and a shaky hand inside the dead zone, do nothing.
        assertEquals(0f, TiltMath.steer(n, n), 0f)
        assertEquals(0f, TiltMath.steer(n + 2f * deg, n), 0f)
        assertEquals(0f, TiltMath.steer(n - 2f * deg, n), 0f)
        // Past it, steering starts small and grows to full lock, then holds.
        val small = TiltMath.steer(n + 5f * deg, n)
        val mid = TiltMath.steer(n + 12f * deg, n)
        assertTrue("$small $mid", small > 0f && small < mid && mid < 1f)
        assertEquals(1f, TiltMath.steer(n + TiltMath.FULL_DEG * deg, n), 1e-4f)
        assertEquals(1f, TiltMath.steer(n + 70f * deg, n), 0f)
        // Left is the mirror of right.
        for (a in floatArrayOf(4f, 9f, 15f, 40f)) {
            assertEquals(-TiltMath.steer(n + a * deg, n), TiltMath.steer(n - a * deg, n), 1e-5f)
        }
    }

    @Test
    fun steeringIsMeasuredFromTheLevelThePlayerHeldNotFromFlat() {
        // Someone holding the phone 12 degrees over to the right: that's level for them.
        val held = 12f * deg
        assertEquals(0f, TiltMath.steer(held, held), 0f)
        assertTrue(TiltMath.steer(held + 10f * deg, held) > 0.2f)
        assertTrue(TiltMath.steer(held - 10f * deg, held) < -0.2f)
        // Every step of lean steers at least as much as the one before it.
        var last = -1f
        var a = 0f
        while (a <= 40f) {
            val s = TiltMath.steer(held + a * deg, held)
            assertTrue("steering fell at $a: $s after $last", s >= last)
            last = s
            a += 0.5f
        }
    }

    @Test
    fun nonsenseReadingsSteerNothing() {
        assertEquals(0f, TiltMath.steer(Float.NaN, 0f), 0f)
        assertEquals(0f, TiltMath.steer(0f, Float.NaN), 0f)
    }
}
