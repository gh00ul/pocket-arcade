package com.pocketarcade.hub

import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.hub.RigChecks.DT
import com.pocketarcade.hub.RigChecks.assertSane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * The animation rig's poses: joints stay finite and inside what a body can do, poses change
 * without snapping, and sitting settles the way the tuning comments say.
 */
class FigureAnimTest {
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
    fun aStillWalkHasItsLegsSwingingAndAStillStandDoesNot() {
        val a = FigureAnim()
        a.setStatic(Pose.WALK, 0f, 1.0f, 0f)
        assertTrue("a still walk isn't walking", abs(a.legPitch[0]) > 0.1f && a.legPitch[0] * a.legPitch[1] < 0f)
        a.setStatic(Pose.STAND, 0f, 1.0f, 0f)
        assertEquals(0f, a.legPitch[0], 1e-6f)
        assertEquals(0f, a.legPitch[1], 1e-6f)
    }

    @Test
    fun changingPoseNeverSnapsAnyJoint() {
        // Flip poses at random for a minute and watch the biggest one-step change of every joint.
        val rng = Random(3)
        val a = FigureAnim(seed = 4)
        val prev = FloatArray(RigChecks.JOINTS)
        val now = FloatArray(RigChecks.JOINTS)
        var biggest = 0f
        var where = ""
        var pose = Pose.STAND
        var z = 100f
        repeat(120 * 60) { n ->
            if (n % 90 == 0) pose = Pose.ALL[rng.nextInt(Pose.COUNT)]
            if (pose == Pose.WALK || pose == Pose.CARRY) z += 40f * DT
            RigChecks.joints(a, prev)
            a.update(DT, 50f, z, 0f, pose)
            RigChecks.joints(a, now)
            assertSane(a, "step $n ($pose)")
            if (n > 0) for (i in now.indices) {
                val d = abs(now[i] - prev[i])
                if (d > biggest) {
                    biggest = d
                    where = "${RigChecks.JOINT_NAMES[i]} at step $n ($pose)"
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
        a.update(DT, 0f, 0f, 0f, Pose.STAND)
        var deepest = 0f
        var last = 0f
        repeat(120 * 2) {
            a.update(DT, 0f, 0f, 0f, Pose.SIT)
            deepest = minOf(deepest, a.rootY)
            last = a.rootY
        }
        assertEquals(-FigureAnim.SEAT_DROP, last, 0.05f)
        val overshoot = (-deepest - FigureAnim.SEAT_DROP) / FigureAnim.SEAT_DROP
        assertTrue("no settle at all ($overshoot)", overshoot > 0.02f)
        assertTrue("a plop, not a settle ($overshoot)", overshoot < 0.12f)
    }
}
