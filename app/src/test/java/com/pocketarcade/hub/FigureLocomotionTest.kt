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

/**
 * Walking and running: the stride follows the ground covered so feet stay planted, it opens out
 * from a walk into a run, stopping plants the feet together, and the body leans and turns smoothly.
 */
class FigureLocomotionTest {
    /** Walks [a] along +z at [speed] for [secs] from [z0], calling [each] after every step; returns the final z. */
    private inline fun walk(a: FigureAnim, speed: Float, secs: Float, z0: Float = 0f, pose: Pose = Pose.WALK, each: (Float) -> Unit = {}): Float {
        var z = z0
        repeat((secs / DT).toInt()) {
            z += speed * DT
            a.update(DT, 0f, z, 0f, pose)
            each(z)
        }
        return z
    }

    /** The planted foot's speed over the ground, as a fraction of [speed]: (mean, mean of the magnitude) over [secs] of walking. */
    private fun slide(speed: Float, secs: Float = 6f): Pair<Float, Float> {
        val a = FigureAnim()
        val z = walk(a, speed, 1.5f)
        val prevForward = FloatArray(2) { Gait.footForward(a.legPitch[it]) }
        var sum = 0f
        var sumAbs = 0f
        var n = 0
        walk(a, speed, secs, z) {
            for (s in 0..1) {
                val fwd = Gait.footForward(a.legPitch[s])
                // The planted foot: on the ground and moving back against the body.
                val planted = a.legLift[s] < 0.02f && fwd - prevForward[s] < 0f
                if (planted) {
                    val slip = (speed * DT + fwd - prevForward[s]) / DT / speed
                    sum += slip
                    sumAbs += abs(slip)
                    n++
                }
                prevForward[s] = fwd
            }
        }
        assertTrue("no planted foot found at $speed", n > 100)
        return (sum / n) to (sumAbs / n)
    }

    @Test
    fun feetStayPlantedInsteadOfSliding() {
        for (speed in floatArrayOf(22f, 34f, 46f, 62f, 78f)) {
            val (mean, meanAbs) = slide(speed)
            // On average the planted foot goes back exactly as fast as the body goes forward...
            assertTrue("at $speed the planted foot slips ${mean * 100}% on average", abs(mean) < 0.12f)
            // ...and never skates far from that (a sine swing, or the old time-driven walk, slid ~30%).
            assertTrue("at $speed the planted foot skates ${meanAbs * 100}% of the body's speed", meanAbs < 0.3f)
        }
    }

    @Test
    fun theStrideOpensOutWithSpeedAndRunningIsItsOwnGait() {
        fun peak(speed: Float): FloatArray {
            val a = FigureAnim()
            walk(a, speed, 2f)
            var leg = 0f
            var arm = 0f
            var lift = 0f
            var lean = 0f
            var n = 0
            walk(a, speed, 2f, 300f) {
                leg = maxOf(leg, abs(a.legPitch[0]))
                arm = maxOf(arm, abs(a.armPitch[1] - a.armPitch[0]))
                lift = maxOf(lift, a.legLift[0])
                lean += a.lean
                n++
            }
            return floatArrayOf(leg, arm, lift, lean / n)
        }
        val shuffle = peak(12f)
        val slow = peak(28f)
        val walk = peak(46f)
        val run = peak(80f)
        for (i in 0..3) {
            assertTrue("joint $i doesn't grow with speed: ${shuffle[i]} ${slow[i]} ${walk[i]} ${run[i]}", shuffle[i] < slow[i] && slow[i] < walk[i] && walk[i] < run[i])
        }
        // A run leans well forward and lifts its feet higher than a walk.
        assertTrue("a run's lean ${run[3]}", run[3] > 0.12f)
        assertTrue("a walk's lean ${walk[3]}", walk[3] < 0.08f)
        assertTrue("a run's foot lift ${run[2]}", run[2] > Gait.LIFT_WALK * 1.4f)
    }

    @Test
    fun stepsLandOncePerStrideOfGroundCovered() {
        for (speed in floatArrayOf(30f, 46f, 78f)) {
            val a = FigureAnim()
            walk(a, speed, 1f)
            var steps = 0
            val z0 = walk(a, speed, 0.5f, 100f)
            val z1 = walk(a, speed, 4f, z0) { if (a.stepped) steps++ }
            val expected = (z1 - z0) / Gait.stride(Gait.amplitude(speed))
            assertTrue("at $speed: $steps steps in ${z1 - z0} units, expected about $expected", abs(steps - expected) <= 1.5f)
        }
    }

    @Test
    fun stoppingBringsTheFeetTogetherAndLevelsTheBody() {
        for (speed in floatArrayOf(30f, 46f, 80f)) {
            for (stopAt in 0 until 8) {
                val a = FigureAnim()
                val z = walk(a, speed, 2f + stopAt * 0.13f)
                repeat(60) { a.update(DT, 0f, z, 0f, Pose.STAND) }
                for (s in 0..1) {
                    assertEquals("leg $s still swinging $speed/$stopAt", 0f, a.legPitch[s], 0.03f)
                    assertEquals("leg $s still lifted", 0f, a.legLift[s], 0.05f)
                }
                assertEquals("body not level", 0f, a.rootY, 0.1f)
            }
        }
    }

    @Test
    fun theBodyLeansIntoAStartAndRocksBackOnAStop() {
        val a = FigureAnim()
        repeat(60) { a.update(DT, 0f, 0f, 0f, Pose.STAND) }
        var z = 0f
        var early = 0f
        repeat(48) {
            z += 60f * DT
            a.update(DT, 0f, z, 0f, Pose.WALK)
            early = maxOf(early, a.lean)
        }
        assertTrue("leaned only $early on setting off", early > 0.15f)
        var back = 0f
        repeat(60) {
            a.update(DT, 0f, z, 0f, Pose.STAND)
            back = minOf(back, a.lean)
        }
        assertTrue("didn't rock back on stopping ($back)", back < -0.03f)
        repeat(240) { a.update(DT, 0f, z, 0f, Pose.STAND) }
        assertEquals("didn't settle upright", 0f, a.lean, 0.005f)
    }

    @Test
    fun turningIsSmoothedAndNeverFasterThanABodyCanSpin() {
        val a = FigureAnim()
        a.update(DT, 0f, 0f, 0f, Pose.STAND)
        var prev = a.yaw
        var biggest = 0f
        var target = 0f
        repeat(120 * 6) { n ->
            if (n % 120 == 0) target = if (target == 0f) 3.0f else 0f
            a.update(DT, 0f, 0f, target, Pose.STAND)
            biggest = maxOf(biggest, abs(a.yaw - prev))
            prev = a.yaw
        }
        assertTrue("turned $biggest in a step", biggest <= 14f * DT + 1e-4f)
        assertEquals(target, a.yaw, 0.02f)
        // It takes the short way round: from just under π to just over -π is a hair, not a spin.
        val b = FigureAnim()
        b.update(DT, 0f, 0f, 3.1f, Pose.STAND)
        repeat(12) { b.update(DT, 0f, 0f, -3.1f, Pose.STAND) }
        assertTrue("went the long way round (${b.yaw})", abs(b.yaw) > 2.9f)
    }

    @Test
    fun aShoveOrATeleportIsNotWalking() {
        val a = FigureAnim()
        a.update(DT, 0f, 0f, 0f, Pose.STAND)
        repeat(30) { a.update(DT, 0f, 0f, 0f, Pose.STAND) }
        val phase = a.phase
        repeat(60) { a.update(DT, 200f, 300f, 0f, Pose.STAND) }
        assertEquals("a teleport advanced the stride", phase, a.phase, 0f)
        assertTrue("a teleport looked like a sprint (${a.speed})", a.speed < 3f)
        assertSane(a, "after a teleport")
    }

    @Test
    fun reduceMotionDropsTheBounceButKeepsTheStride() {
        fun bounce(): Pair<Float, Float> {
            val a = FigureAnim()
            walk(a, 80f, 2f)
            var lo = 99f
            var hi = -99f
            var leg = 0f
            walk(a, 80f, 2f, 500f) {
                lo = minOf(lo, a.rootY); hi = maxOf(hi, a.rootY)
                leg = maxOf(leg, abs(a.legPitch[0]))
            }
            return (hi - lo) to leg
        }
        val (full, legFull) = bounce()
        FigureAnim.reduceMotion = true
        try {
            val (calm, legCalm) = bounce()
            assertTrue("reduce motion still bounces $calm vs $full", calm < full * 0.5f)
            assertEquals("the stride must stay readable", legFull, legCalm, 0.02f)
        } finally {
            FigureAnim.reduceMotion = false
        }
    }

    @Test
    fun runningAboutNeverBendsALimbImpossibly() {
        val rng = Random(9)
        val a = FigureAnim(seed = 2)
        var z = 0f
        var x = 0f
        var yaw = 0f
        var speed = 0f
        val speeds = floatArrayOf(0f, 10f, 25f, 46f, 80f, 100f, 140f)
        repeat(120 * 90) { n ->
            if (n % 100 == 0) speed = speeds[rng.nextInt(speeds.size)]
            if (n % 170 == 0) yaw = rng.nextFloat() * 6.28f - 3.14f
            z += speed * DT * cos(yaw)
            x += speed * DT * sin(yaw)
            a.update(DT, x, z, yaw, if (speed > 0f) Pose.WALK else Pose.STAND)
            assertSane(a, "fuzz $n at $speed")
        }
    }

    @Test
    fun aPlayersStridePhaseKeepsTimeWithItsFootsteps() {
        // The first-person head bob and the footstep sounds read Player.phase and .stepped.
        val p = Player()
        p.x = 200f
        p.y = 600f
        var steps = 0
        val solids = emptyList<Box>()
        repeat(120 * 3) {
            p.update(DT, 0f, -1f, solids)
            if (p.stepped) steps++
            assertTrue(p.phase.isFinite())
        }
        // 3 s at the top speed: about speed / stride steps a second.
        val perSecond = Player.SPEED / Gait.stride(Gait.amplitude(Player.SPEED))
        assertTrue("$steps steps in 3 s, expected about ${perSecond * 3}", abs(steps - perSecond * 3) <= 2.5f)
    }
}
