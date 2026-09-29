package com.pocketarcade.hub

import com.pocketarcade.engine.r3d.Xform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * The animation rig: joints stay finite and inside what a body can do, poses change without
 * snapping, and the pieces behave the way the tuning comments say they do.
 */
class FigureAnimTest {
    private val dt = 1f / 120f

    /** Every joint value the renderer reads, in a fixed order, for checking them all at once. */
    private fun joints(a: FigureAnim, out: FloatArray) {
        var i = 0
        out[i++] = a.yaw; out[i++] = a.rootY; out[i++] = a.sway
        out[i++] = a.lean; out[i++] = a.leanRoll; out[i++] = a.twist
        out[i++] = a.breath; out[i++] = a.breathLift
        out[i++] = a.headYaw; out[i++] = a.headPitch; out[i++] = a.headRoll
        out[i++] = a.hatPitch; out[i++] = a.hatRoll; out[i++] = a.hatLift
        out[i++] = a.tailPitch; out[i++] = a.tailRoll
        out[i++] = a.blink; out[i++] = a.itemAmount
        for (s in 0..1) {
            out[i++] = a.armPitch[s]; out[i++] = a.armRoll[s]
            out[i++] = a.legPitch[s]; out[i++] = a.legLift[s]
        }
    }

    private fun assertSane(a: FigureAnim, tag: String) {
        val v = FloatArray(JOINTS)
        joints(a, v)
        for (x in v) assertFalse("$tag: non-finite joint", x.isNaN() || x.isInfinite())
        for (s in 0..1) {
            // Arms: never past straight up or well behind the back; never crossing through the body sideways.
            assertTrue("$tag: arm pitch ${a.armPitch[s]}", a.armPitch[s] in -3.1f..1.3f)
            assertTrue("$tag: arm roll ${a.armRoll[s]}", a.armRoll[s] in -0.9f..0.9f)
            // Legs: a sitting kid's legs reach horizontal at most; walking legs stay inside a stride.
            assertTrue("$tag: leg pitch ${a.legPitch[s]}", a.legPitch[s] in -1.62f..1.0f)
            assertTrue("$tag: leg lift ${a.legLift[s]}", a.legLift[s] in -0.01f..2.6f)
        }
        assertTrue("$tag: body height ${a.rootY}", a.rootY in -6.5f..5f)
        assertTrue("$tag: lean ${a.lean}", a.lean in -0.5f..0.5f)
        assertTrue("$tag: bank ${a.leanRoll}", abs(a.leanRoll) < 0.3f)
        assertTrue("$tag: twist ${a.twist}", abs(a.twist) < 0.5f)
        assertTrue("$tag: head yaw ${a.headYaw}", abs(a.headYaw) < 1.2f)
        assertTrue("$tag: head pitch ${a.headPitch}", a.headPitch in -0.6f..0.7f)
        assertTrue("$tag: head roll ${a.headRoll}", abs(a.headRoll) < 0.4f)
        assertTrue("$tag: hat pitch ${a.hatPitch}", abs(a.hatPitch) < 0.3f)
        assertTrue("$tag: hat roll ${a.hatRoll}", abs(a.hatRoll) < 0.3f)
        assertTrue("$tag: hat lift ${a.hatLift}", abs(a.hatLift) < 1.5f)
        assertTrue("$tag: tail ${a.tailPitch}/${a.tailRoll}", abs(a.tailPitch) < 0.9f && abs(a.tailRoll) < 0.9f)
        assertTrue("$tag: blink ${a.blink}", a.blink in 0f..1f)
        assertTrue("$tag: item ${a.itemAmount}", a.itemAmount in 0f..1f)
    }

    @Test
    fun theRigsAxesAreWhatTheTuningAssumes() {
        // Positive pitch tips the top forwards (+z) and the face down; negative swings a hanging limb forwards.
        val xf = Xform().set(pitch = 0.3f)
        assertTrue("a forward lean is positive pitch", xf.z(0f, 1f, 0f) > 0.2f)
        assertTrue("looking down is positive pitch", xf.y(0f, 0f, 1f) < -0.2f)
        assertTrue("a hanging arm swings forwards with negative pitch", Xform().set(pitch = -0.3f).z(0f, -1f, 0f) > 0.2f)
        // Positive roll tips the top toward -x; positive yaw turns the nose toward +x.
        assertTrue(Xform().set(roll = 0.3f).x(0f, 1f, 0f) < -0.2f)
        assertTrue(Xform().set(yaw = 0.3f).x(0f, 0f, 1f) > 0.2f)
    }

    @Test
    fun aStillPoseIsSolvedInstantlyAndSanely() {
        val a = FigureAnim()
        for (p in Pose.ALL) {
            a.setStatic(p, 1.3f, 0.7f, 0.4f)
            assertSane(a, "static $p")
            assertEquals(0.4f, a.yaw, 0f)
        }
        a.setStatic(Pose.SIT, 0f, 0f, 0f)
        assertEquals(-FigureAnim.SEAT_LEG, a.legPitch[0], 1e-4f)
        assertEquals(-FigureAnim.SEAT_DROP, a.rootY, 0.05f)
        a.setStatic(Pose.HOLD, 0f, 0f, 0f)
        assertEquals(1f, a.itemAmount, 1e-5f)
        a.setStatic(Pose.STAND, 0f, 0f, 0f)
        assertEquals(0f, a.itemAmount, 0f)
    }

    @Test
    fun changingPoseNeverSnapsAnyJoint() {
        // Flip poses at random for a minute and watch the biggest one-step change of every joint.
        val rng = Random(3)
        val a = FigureAnim(seed = 4)
        val prev = FloatArray(JOINTS)
        val now = FloatArray(JOINTS)
        var biggest = 0f
        var where = ""
        var pose = Pose.STAND
        var x = 100f
        repeat(120 * 60) { n ->
            if (n % 90 == 0) pose = Pose.ALL[rng.nextInt(Pose.COUNT)]
            if (pose == Pose.WALK || pose == Pose.CARRY) x += 40f * dt
            joints(a, prev)
            a.update(dt, x, 50f, 0f, pose, n * 0.1f)
            joints(a, now)
            assertSane(a, "step $n ($pose)")
            if (n > 0) for (i in now.indices) {
                val d = abs(now[i] - prev[i])
                if (d > biggest) {
                    biggest = d
                    where = "${JOINT_NAMES[i]} at step $n ($pose)"
                }
            }
        }
        // A cheer's arms travel about 2.6 rad in 0.14 s (peaking at 1.5 times the average speed),
        // so a step of 1/120 s is a fraction of that.
        assertTrue("$where moved $biggest in one step", biggest < 0.3f)
    }

    @Test
    fun sittingDownSettlesWithASmallOvershootAndStandingUpDoesNot() {
        val a = FigureAnim()
        a.update(dt, 0f, 0f, 0f, Pose.STAND, 0f)
        var deepest = 0f
        var last = 0f
        repeat(120 * 2) {
            a.update(dt, 0f, 0f, 0f, Pose.SIT, 0f)
            deepest = minOf(deepest, a.rootY)
            last = a.rootY
        }
        assertEquals(-FigureAnim.SEAT_DROP, last, 0.05f)
        val overshoot = (-deepest - FigureAnim.SEAT_DROP) / FigureAnim.SEAT_DROP
        assertTrue("no settle at all ($overshoot)", overshoot > 0.02f)
        assertTrue("a plop, not a settle ($overshoot)", overshoot < 0.12f)
    }

    private companion object {
        const val JOINTS = 18 + 8
        val JOINT_NAMES = arrayOf(
            "yaw", "rootY", "sway", "lean", "leanRoll", "twist", "breath", "breathLift", "headYaw", "headPitch", "headRoll",
            "hatPitch", "hatRoll", "hatLift", "tailPitch", "tailRoll", "blink", "item",
            "armPitchL", "armRollL", "legPitchL", "legLiftL", "armPitchR", "armRollR", "legPitchR", "legLiftR",
        )
    }
}
