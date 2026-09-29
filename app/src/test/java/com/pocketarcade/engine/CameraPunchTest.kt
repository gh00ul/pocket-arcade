package com.pocketarcade.engine

import com.pocketarcade.engine.r3d.GameViewport
import com.pocketarcade.engine.r3d.Stage3D
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The camera-punch spring and how Stage3D applies it. */
class CameraPunchTest {
    private var motion = 1f
    private val spring = PunchSpring { motion }

    @After
    fun clean() {
        GameViewport.takePunch()
    }

    /** Runs [seconds] in steps of [dt]; returns (peak, lowest, time it last moved more than 1%). */
    private fun run(seconds: Float, dt: Float): Triple<Float, Float, Float> {
        var peak = 0f
        var low = 0f
        var settled = 0f
        var t = 0f
        while (t < seconds) {
            spring.update(dt)
            t += dt
            peak = maxOf(peak, spring.value)
            low = minOf(low, spring.value)
            if (kotlin.math.abs(spring.value) > 0.01f) settled = t
        }
        return Triple(peak, low, settled)
    }

    @Test
    fun aKickPushesInToAboutItsAmountThenSettlesWithASoftOvershoot() {
        spring.kick(1f)
        val (peak, low, settled) = run(1.5f, 1f / 120f)
        assertEquals("peak", 1f, peak, 0.1f)
        // The pull-back is small (about a sixth) and the camera is at rest well inside half a second.
        assertTrue("overshoot $low", low < -0.05f && low > -0.3f)
        assertTrue("settled at $settled s", settled < 0.55f)
        assertFalse(spring.active)
        assertEquals(0f, spring.value, 0f)
    }

    @Test
    fun theKickScalesWithItsAmount() {
        spring.kick(0.3f)
        val (peak, _, _) = run(1f, 1f / 120f)
        assertEquals(0.3f, peak, 0.05f)
    }

    @Test
    fun theKickIsAnEasedAttackNotAPop() {
        spring.kick(1f)
        spring.update(1f / 120f)
        // One step in, it has barely started moving: no instant jump.
        assertTrue("first step ${spring.value}", spring.value in 0f..0.45f)
    }

    @Test
    fun frameRateDoesNotChangeThePunch() {
        val peaks = floatArrayOf(1f / 240f, 1f / 120f, 1f / 60f, 1f / 30f).map { dt ->
            val s = PunchSpring { 1f }
            s.kick(1f)
            var peak = 0f
            var t = 0f
            while (t < 1f) {
                s.update(dt); t += dt; peak = maxOf(peak, s.value)
            }
            peak
        }
        for (p in peaks) assertEquals(peaks[0], p, 0.06f)
    }

    @Test
    fun aHugeFrameCannotBlowItUp() {
        spring.kick(1.5f)
        repeat(50) { spring.update(0.5f) }
        assertTrue(spring.value in -0.5f..PunchSpring.MAX_AMOUNT)
        // And a pile of kicks is clamped, not summed without limit.
        repeat(100) { spring.kick(1f) }
        val (peak, _, _) = run(1f, 1f / 60f)
        assertTrue("peak $peak", peak <= PunchSpring.MAX_AMOUNT + 0.15f)
    }

    @Test
    fun nonsenseKicksAreIgnored() {
        spring.kick(Float.NaN)
        spring.kick(-1f)
        spring.kick(0f)
        assertFalse(spring.active)
    }

    @Test
    fun reduceMotionOffSwitch() {
        motion = 0f
        spring.kick(1f)
        assertFalse(spring.active)
        motion = 1f
        spring.kick(1f)
        spring.update(1f / 120f)
        assertTrue(spring.active)
        // Switched on mid-punch: it stops dead on the next update.
        motion = 0f
        spring.update(1f / 120f)
        assertEquals(0f, spring.value, 0f)
        assertFalse(spring.active)
    }

    // ---- Stage3D applies it

    private var now = 1_000_000_000L

    private fun stage() = Stage3D(360, 640).apply {
        look(180f, 200f, 700f, 180f, 0f, 100f, fovDeg = 45f)
        nanoClock = { now }
    }

    private fun frame(stage: Stage3D, ms: Long = 16) {
        now += ms * 1_000_000L
        stage.begin()
    }

    /** Where the world point (100, 0, 100) lands on the field, from the stage's own camera. */
    private fun screenX(stage: Stage3D): Float {
        val out = FloatArray(3)
        assertTrue(stage.toField(100f, 0f, 100f, out))
        return out[0]
    }

    @Test
    fun aPunchMagnifiesTheSceneAndReturnsToTheExactRestingPicture() {
        val stage = stage()
        frame(stage)
        val rest = screenX(stage)
        // 100 is left of the centre line (x = 180): the resting picture puts it left of the middle.
        assertTrue(rest < 180f)
        stage.punch(1f)
        var maxShift = 0f
        repeat(15) {
            frame(stage)
            maxShift = maxOf(maxShift, rest - screenX(stage))
        }
        // Pushed in: the point moves further from the centre, by a few percent of its offset, not a lurch.
        assertTrue("shift $maxShift", maxShift > 2f && maxShift < 20f)
        repeat(60) { frame(stage) }
        assertEquals(0f, stage.punchValue, 0f)
        assertEquals(rest, screenX(stage), 1e-3f)
    }

    @Test
    fun theHostsRequestReachesTheStageOnItsNextFrameAndIsConsumed() {
        val stage = stage()
        frame(stage)
        GameViewport.requestPunch(0.8f)
        GameViewport.requestPunch(0.4f)
        frame(stage)
        frame(stage)
        assertTrue("punch value ${stage.punchValue}", stage.punchValue > 0.05f)
        assertEquals(0f, GameViewport.takePunch(), 0f)
    }

    @Test
    fun aPunchIsWallClockNotFrameCount() {
        val a = stage()
        frame(a)
        a.punch(1f)
        // Sixteen 8 ms frames and eight 16 ms frames span the same time: the same picture.
        repeat(16) { frame(a, 8) }
        val v1 = a.punchValue
        now = 1_000_000_000L
        val b = stage()
        frame(b)
        b.punch(1f)
        repeat(8) { frame(b, 16) }
        assertEquals(v1, b.punchValue, 0.06f)
    }

    @Test
    fun withReduceMotionTheStagePicturesNeverMove() {
        val saved = ScreenShake.intensity
        try {
            ScreenShake.intensity = 0f
            val stage = stage()
            frame(stage)
            val rest = screenX(stage)
            stage.punch(1f)
            GameViewport.requestPunch(1f)
            repeat(10) {
                frame(stage)
                assertEquals(rest, screenX(stage), 0f)
            }
            assertEquals(0f, stage.punchValue, 0f)
        } finally {
            ScreenShake.intensity = saved
        }
    }
}
