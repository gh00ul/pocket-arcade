package com.pocketarcade.hub

import com.pocketarcade.hub.RigChecks.DT
import com.pocketarcade.hub.RigChecks.assertSane
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Secondary motion: hats, ponytails and arms lag and settle; sitting and cheering have a beat before and after. */
class FigureFollowThroughTest {
    private fun standing(seed: Int = 1): FigureAnim {
        val a = FigureAnim(seed)
        repeat(120) { a.update(DT, 0f, 0f, 0f, Pose.STAND) }
        return a
    }

    /** Sets a standing [a] off at [speed] for [secs]; calls [each] after every step. */
    private inline fun setOff(a: FigureAnim, speed: Float, secs: Float, each: () -> Unit) {
        var z = 0f
        repeat((secs / DT).toInt()) {
            z += speed * DT
            a.update(DT, 0f, z, 0f, Pose.WALK)
            each()
        }
    }

    @Test
    fun aHatTipsBackWhenTheKidSetsOffAndForwardWhenTheyStop() {
        val a = standing()
        var backmost = 0f
        setOff(a, 44f, 0.5f) { backmost = minOf(backmost, a.hatPitch) }
        assertTrue("the hat barely moved on setting off ($backmost)", backmost < -0.03f)
        assertTrue("the hat tipped too far ($backmost)", backmost >= -0.16f - 1e-6f)
        // Keep walking at a steady pace: it settles.
        var z = 44f * 0.5f
        repeat(240) {
            z += 44f * DT
            a.update(DT, 0f, z, 0f, Pose.WALK)
        }
        assertEquals("the hat never settled", 0f, a.hatPitch, 0.02f)
        // Stopping throws it forward.
        var forward = 0f
        repeat(60) {
            a.update(DT, 0f, z, 0f, Pose.STAND)
            forward = maxOf(forward, a.hatPitch)
        }
        assertTrue("the hat didn't swing forward on stopping ($forward)", forward > 0.03f)
    }

    @Test
    fun aPonytailSwingsOutBehindOnSettingOffAndSwaysWithTheHead() {
        val a = standing()
        var back = 0f
        setOff(a, 44f, 0.5f) { back = maxOf(back, a.tailPitch) }
        assertTrue("the ponytail barely moved ($back)", back > 0.04f)
        // Turning the head sends it swaying sideways.
        val b = FigureAnim(seed = 2)
        repeat(120) { b.update(DT, 0f, 0f, 0f, Pose.SIT) }
        var side = 0f
        repeat(120) { b.look(90f, 30f); b.update(DT, 0f, 0f, 0f, Pose.SIT); side = maxOf(side, abs(b.tailRoll)) }
        assertTrue("the ponytail ignored the head turning ($side)", side > 0.05f)
    }

    @Test
    fun armsSettleOntoACheerWithALittleOvershootUnlessMotionIsReduced() {
        fun highest(reduced: Boolean): Float {
            FigureAnim.reduceMotion = reduced
            try {
                val a = standing()
                var top = 0f
                repeat(120) {
                    a.update(DT, 0f, 0f, 0f, Pose.CHEER)
                    top = minOf(top, a.armPitch[1])
                }
                return top
            } finally {
                FigureAnim.reduceMotion = false
            }
        }
        val full = highest(false)
        val calm = highest(true)
        assertTrue("no overshoot on a cheer ($full vs $calm)", full < calm - 0.04f)
        assertTrue("the arms went past straight up ($full)", full >= -3.05f)
    }

    @Test
    fun aCheerIsPrecededByACrouchAndArmsSweepingBack() {
        val a = standing()
        var lowest = 0f
        var backmost = 0f
        var firstUp = -1
        repeat(60) { n ->
            a.update(DT, 0f, 0f, 0f, Pose.CHEER)
            lowest = minOf(lowest, a.rootY)
            backmost = maxOf(backmost, a.armPitch[1])
            if (firstUp < 0 && a.armPitch[1] < -1f) firstUp = n
            assertSane(a, "cheer $n")
        }
        assertTrue("no crouch before the cheer ($lowest)", lowest < -0.5f)
        assertTrue("the arms didn't wind back ($backmost)", backmost > 0.1f)
        assertTrue("the arms never went up", firstUp > 0)
        // Reduce motion goes straight to the cheer.
        FigureAnim.reduceMotion = true
        try {
            val b = standing(seed = 3)
            var low = 0f
            repeat(20) {
                b.update(DT, 0f, 0f, 0f, Pose.CHEER)
                low = minOf(low, b.rootY)
            }
            assertTrue("reduce motion still crouches ($low)", low > -0.05f)
        } finally {
            FigureAnim.reduceMotion = false
        }
    }

    @Test
    fun aCheerCancelledBeforeItStartsLeavesNothingBehind() {
        val a = standing()
        repeat(6) { a.update(DT, 0f, 0f, 0f, Pose.CHEER) }
        repeat(240) { a.update(DT, 0f, 0f, 0f, Pose.STAND) }
        assertEquals(0f, a.rootY, 0.1f)
        assertEquals(0f, a.armPitch[1], 0.15f)
    }

    @Test
    fun sittingDownTipsTheBodyForwardWhileMovingAndStandingUpDoesToo() {
        val a = standing()
        var down = 0f
        repeat(120) {
            a.update(DT, 0f, 0f, 0f, Pose.SIT)
            down = maxOf(down, a.lean)
        }
        assertTrue("no lean going down ($down)", down > 0.05f)
        assertEquals("didn't settle upright seated", 0f, a.lean, 0.02f)
        var up = 0f
        repeat(120) {
            a.update(DT, 0f, 0f, 0f, Pose.STAND)
            up = maxOf(up, a.lean)
        }
        assertTrue("no lean getting up ($up)", up > 0.05f)
        assertTrue("leaned too far ($up)", up < 0.3f)
    }

    @Test
    fun reduceMotionCalmsTheFollowThrough() {
        fun swing(reduced: Boolean): Float {
            FigureAnim.reduceMotion = reduced
            try {
                val a = standing()
                var peak = 0f
                setOff(a, 60f, 0.6f) { peak = maxOf(peak, abs(a.hatPitch), abs(a.tailPitch) * 0.3f) }
                return peak
            } finally {
                FigureAnim.reduceMotion = false
            }
        }
        val full = swing(false)
        val calm = swing(true)
        assertTrue("reduce motion didn't calm the hat ($calm vs $full)", calm < full * 0.6f)
    }

    @Test
    fun anythingAKidCanDoInAnyOrderStaysInRange() {
        val rng = Random(21)
        for (reduced in listOf(false, true)) {
            FigureAnim.reduceMotion = reduced
            try {
                val a = FigureAnim(seed = 9)
                var x = 0f
                var z = 0f
                var yaw = 0f
                var speed = 0f
                var pose = Pose.STAND
                val speeds = floatArrayOf(0f, 0f, 20f, 44f, 80f, 110f)
                repeat(120 * 120) { n ->
                    if (n % 37 == 0) pose = Pose.ALL[rng.nextInt(Pose.COUNT)]
                    if (n % 61 == 0) speed = speeds[rng.nextInt(speeds.size)]
                    if (n % 90 == 0) yaw = rng.nextFloat() * 6.28f - 3.14f
                    if (n % 47 == 0) a.look(x + rng.nextFloat() * 200f - 100f, z + rng.nextFloat() * 200f - 100f, rng.nextFloat() * 90f)
                    if (n % 700 == 0) { x += 300f; z -= 200f }
                    x += speed * DT * sin(yaw)
                    z += speed * DT * cos(yaw)
                    a.update(DT, x, z, yaw, pose)
                    assertSane(a, "chaos $n ($pose, $speed, reduced=$reduced)")
                }
            } finally {
                FigureAnim.reduceMotion = false
            }
        }
    }

    @Test
    fun theRigStaysStableAtAnyFrameTime() {
        // A slow phone or a long hitch: the springs must not blow up at 10, 30 or 100 ms steps.
        for (dt in floatArrayOf(1f / 240f, 1f / 60f, 1f / 30f, 0.1f, 0.5f)) {
            val a = FigureAnim(seed = 4)
            val rng = Random(2)
            var z = 0f
            var pose = Pose.STAND
            repeat(600) { n ->
                if (n % 20 == 0) pose = Pose.ALL[rng.nextInt(Pose.COUNT)]
                z += 40f * dt
                a.update(dt, 0f, z, 0f, pose)
                assertSane(a, "dt=$dt step $n")
            }
        }
    }
}
