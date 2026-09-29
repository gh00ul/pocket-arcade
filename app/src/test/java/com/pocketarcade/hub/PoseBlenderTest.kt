package com.pocketarcade.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Poses cross-fade: weights always sum to 1, move smoothly and monotonically, and survive being interrupted. */
class PoseBlenderTest {
    private fun assertValid(b: PoseBlender, tag: String) {
        var sum = 0f
        for (w in b.weights) {
            assertFalse("$tag: NaN weight", w.isNaN())
            assertTrue("$tag: weight $w out of range", w >= -1e-6f && w <= 1f + 1e-6f)
            sum += w
        }
        assertEquals("$tag: weights sum", 1f, sum, 1e-4f)
    }

    @Test
    fun startsFullyInItsInitialPose() {
        val b = PoseBlender(Pose.SIT)
        assertEquals(1f, b.weight(Pose.SIT), 0f)
        assertEquals(0f, b.weight(Pose.STAND), 0f)
        assertTrue(b.settled)
        assertValid(b, "start")
    }

    @Test
    fun everyBlendTimeIsInTheQuickButNotPoppingRange() {
        for (p in Pose.ALL) {
            val t = PoseBlender.blendTime(p)
            assertTrue("$p blends in $t s", t >= PoseBlender.BLEND_MIN - 1e-6f && t <= PoseBlender.BLEND_MAX + 1e-6f)
        }
    }

    @Test
    fun aBlendMovesTheTargetUpAndEverythingElseDownAndFinishesInBlendTime() {
        for (from in Pose.ALL) for (to in Pose.ALL) {
            if (from == to) continue
            val b = PoseBlender(from)
            b.set(to)
            var prevTo = b.weight(to)
            var prevFrom = b.weight(from)
            val dt = 1f / 120f
            val steps = (PoseBlender.blendTime(to) / dt).toInt() + 2
            repeat(steps) {
                b.update(dt)
                assertValid(b, "$from->$to")
                assertTrue("$from->$to target weight fell", b.weight(to) >= prevTo - 1e-6f)
                assertTrue("$from->$to source weight rose", b.weight(from) <= prevFrom + 1e-6f)
                prevTo = b.weight(to)
                prevFrom = b.weight(from)
            }
            assertTrue("$from->$to not settled", b.settled)
            assertEquals(1f, b.weight(to), 0f)
            assertEquals(0f, b.weight(from), 0f)
        }
    }

    @Test
    fun theEaseIsSmoothAtBothEnds() {
        val b = PoseBlender(Pose.STAND)
        b.set(Pose.WALK)
        val total = PoseBlender.blendTime(Pose.WALK)
        // Half way through, a smoothstep is at one half.
        b.update(total / 2f)
        assertEquals(0.5f, b.weight(Pose.WALK), 0.02f)
        // The first sliver moves next to nothing (no pop at the start)...
        val c = PoseBlender(Pose.STAND)
        c.set(Pose.WALK)
        c.update(total * 0.02f)
        assertTrue("popped ${c.weight(Pose.WALK)}", c.weight(Pose.WALK) < 0.01f)
        // ...and it arrives without a knock at the end too.
        val d = PoseBlender(Pose.STAND)
        d.set(Pose.WALK)
        d.update(total * 0.98f)
        assertTrue("arrived with a jolt ${d.weight(Pose.WALK)}", d.weight(Pose.WALK) > 0.99f)
    }

    @Test
    fun askingForThePoseAlreadyComingChangesNothing() {
        val b = PoseBlender(Pose.STAND)
        b.set(Pose.PLAY)
        b.update(0.05f)
        val w = b.weight(Pose.PLAY)
        b.set(Pose.PLAY)
        assertEquals(w, b.weight(Pose.PLAY), 0f)
        b.update(0.02f)
        assertTrue(b.weight(Pose.PLAY) > w)
    }

    @Test
    fun anInterruptionBlendsOnFromTheMiddleWithoutAJump() {
        val b = PoseBlender(Pose.STAND)
        b.set(Pose.CHEER)
        b.update(PoseBlender.blendTime(Pose.CHEER) * 0.5f)
        val cheerBefore = b.weight(Pose.CHEER)
        val standBefore = b.weight(Pose.STAND)
        assertTrue(cheerBefore > 0.3f && cheerBefore < 0.7f)
        // Change our mind: nothing on screen jumps at the moment of the change.
        b.set(Pose.SIT)
        assertEquals(cheerBefore, b.weight(Pose.CHEER), 1e-5f)
        assertEquals(standBefore, b.weight(Pose.STAND), 1e-5f)
        assertValid(b, "interrupted")
        // The abandoned poses fade out and the new one comes in, all summing to 1.
        var prevCheer = cheerBefore
        var prevSit = 0f
        repeat(60) {
            b.update(1f / 120f)
            assertValid(b, "interrupted")
            assertTrue(b.weight(Pose.CHEER) <= prevCheer + 1e-6f)
            assertTrue(b.weight(Pose.SIT) >= prevSit - 1e-6f)
            prevCheer = b.weight(Pose.CHEER)
            prevSit = b.weight(Pose.SIT)
        }
        assertEquals(1f, b.weight(Pose.SIT), 0f)
        assertEquals(0f, b.weight(Pose.CHEER), 0f)
    }

    @Test
    fun noPoseMakesAStepBiggerThanTheBlendAllows() {
        // Per-step change in any weight stays small however the poses are flipped about.
        val rng = Random(11)
        val b = PoseBlender()
        val dt = 1f / 120f
        val prev = FloatArray(Pose.COUNT)
        repeat(20000) {
            if (rng.nextInt(40) == 0) b.set(Pose.ALL[rng.nextInt(Pose.COUNT)])
            b.weights.copyInto(prev)
            b.update(dt)
            for (i in prev.indices) {
                assertTrue("weight $i jumped ${prev[i]} -> ${b.weights[i]}", kotlin.math.abs(b.weights[i] - prev[i]) < 0.12f)
            }
            assertValid(b, "fuzz")
        }
    }

    @Test
    fun oddFrameTimesNeverBreakTheWeights() {
        val rng = Random(5)
        val b = PoseBlender()
        val dts = floatArrayOf(0f, -1f, Float.NaN, 1e-9f, 1f / 240f, 1f / 30f, 0.5f, 100f)
        repeat(5000) {
            if (rng.nextInt(7) == 0) b.set(Pose.ALL[rng.nextInt(Pose.COUNT)])
            b.update(dts[rng.nextInt(dts.size)])
            assertValid(b, "odd dt")
        }
        // A huge frame just finishes the blend.
        b.set(Pose.WAVE)
        b.update(100f)
        assertEquals(1f, b.weight(Pose.WAVE), 0f)
    }

    @Test
    fun snapJumpsStraightThere() {
        val b = PoseBlender(Pose.STAND)
        b.set(Pose.SIT)
        b.update(0.05f)
        b.snap(Pose.CHEER)
        assertEquals(1f, b.weight(Pose.CHEER), 0f)
        assertTrue(b.settled)
        assertValid(b, "snap")
    }
}
