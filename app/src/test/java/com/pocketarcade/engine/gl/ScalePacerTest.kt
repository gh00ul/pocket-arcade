package com.pocketarcade.engine.gl

import org.junit.Assert.assertEquals
import org.junit.Test

class ScalePacerTest {
    private val ms = 1_000_000L

    /** Feeds [n] frames [frameMs] apart starting after [t0]; returns the time of the last one. */
    private fun run(p: ScalePacer, t0: Long, n: Int, frameMs: Float, gpuMs: Float = -1f): Long {
        var t = t0
        repeat(n) {
            t += (frameMs * ms).toLong()
            p.onFrame(t, gpuMs)
        }
        return t
    }

    @Test
    fun aSlowStartupBurstIsIgnored() {
        val p = ScalePacer()
        p.reset(0L)
        // 1.9 s of 40 ms frames while shaders compile and textures upload.
        run(p, 0L, 47, 40f)
        assertEquals(0.8f, p.scale, 1e-6f)
    }

    @Test
    fun sustainedSlowFramesStepDownToTheFloor() {
        val p = ScalePacer()
        p.reset(0L)
        var t = run(p, 0L, 130, 16.6f) // past the warm-up
        t = run(p, t, 30, 30f)
        assertEquals(0.8f, p.scale, 1e-6f)
        t = run(p, t, 1, 30f)
        assertEquals(0.7f, p.scale, 1e-6f)
        run(p, t, 400, 40f)
        assertEquals(0.5f, p.scale, 1e-6f)
    }

    @Test
    fun hitchesAndPausesDoNotBlockRecovery() {
        val p = ScalePacer(start = 0.5f)
        p.reset(0L)
        var t = run(p, 0L, 130, 16.6f)
        // Fast frames with a stray 30 ms hitch every 40 frames and a 300 ms pause: still climbs,
        // a step per 120-frame window (about 2 s), to the 0.8 ceiling.
        for (k in 0 until 12) {
            t = run(p, t, 39, 16.6f)
            t = run(p, t, 1, 30f)
            if (k == 5) t = run(p, t, 1, 300f)
        }
        assertEquals(0.8f, p.scale, 1e-6f)
        // Without a GPU timer it never goes past the ceiling.
        run(p, t, 1000, 16.6f)
        assertEquals(0.8f, p.scale, 1e-6f)
    }

    @Test
    fun aKnownLightGpuLoadRaisesQuicklyAndAboveTheCeiling() {
        val p = ScalePacer(start = 0.5f)
        p.reset(0L)
        var t = run(p, 0L, 121, 16.6f, gpuMs = 3f)
        // Quarter windows once the GPU time says there is plenty of room.
        t = run(p, t, 30 * 3, 16.6f, gpuMs = 3f)
        assertEquals(0.8f, p.scale, 1e-4f)
        // 3 ms at 0.8 predicts under 5 ms at 1.0: allowed above the usual ceiling.
        run(p, t, 300, 16.6f, gpuMs = 3f)
        assertEquals(1f, p.scale, 1e-4f)
    }

    @Test
    fun aHeavyGpuLoadStaysAtTheCeiling() {
        val p = ScalePacer()
        p.reset(0L)
        // 9 ms at 0.8 would be ~14 ms at 0.9: over the two-thirds budget.
        run(p, 0L, 2000, 16.6f, gpuMs = 9f)
        assertEquals(0.8f, p.scale, 1e-4f)
        assertEquals(14.06f, p.predict(9f, 0.8f, 1f), 0.01f)
    }

    @Test
    fun bouncingOffTheSameScaleBacksOff() {
        val p = ScalePacer()
        p.reset(0L)
        var t = run(p, 0L, 130, 16.6f)
        t = run(p, t, 32, 30f)
        assertEquals(0.7f, p.scale, 1e-6f)
        // Trying 0.8 again now takes two windows, not one.
        t = run(p, t, 125, 16.6f)
        assertEquals(0.7f, p.scale, 1e-6f)
        t = run(p, t, 125, 16.6f)
        assertEquals(0.8f, p.scale, 1e-6f)
        // Too slow again: the next retry takes four windows.
        t = run(p, t, 32, 30f)
        assertEquals(0.7f, p.scale, 1e-6f)
        t = run(p, t, 360, 16.6f)
        assertEquals(0.7f, p.scale, 1e-6f)
        run(p, t, 130, 16.6f)
        assertEquals(0.8f, p.scale, 1e-6f)
    }

    @Test
    fun aNewScreenStartsOverAtTheDefault() {
        val p = ScalePacer()
        p.reset(0L)
        var t = run(p, 0L, 130, 16.6f)
        t = run(p, t, 200, 40f)
        assertEquals(0.5f, p.scale, 1e-6f)
        p.reset(t)
        assertEquals(0.8f, p.scale, 1e-6f)
        // A surface change keeps the scale but still skips the warm-up.
        p.reset(t, keepScale = true)
        run(p, t, 40, 40f)
        assertEquals(0.8f, p.scale, 1e-6f)
    }

    // ------------------------------------------------------------------ the quality ladder

    private fun ladderPacer(startRung: Int = 0, top: Int = 0, bottom: Int = Int.MAX_VALUE, start: Float = 0.8f) =
        ScalePacer(start = start, ladder = GfxQuality.LADDER, startRung = startRung, topRung = top, bottomRung = bottom)

    /** Runs the pacer past its warm-up with fast frames; returns the time. */
    private fun warmUp(p: ScalePacer): Long {
        p.reset(0L)
        return run(p, 0L, 130, 16.6f)
    }

    @Test
    fun stillSlowAtTheFloorStepsDownARungAtATime() {
        val p = ladderPacer()
        var t = warmUp(p)
        // 31 slow frames per step: three scale steps to the 0.5 floor first, resolution before effects.
        t = run(p, t, 31 * 3, 40f)
        assertEquals(0.5f, p.scale, 1e-6f)
        assertEquals(0, p.rung)
        t = run(p, t, 31, 40f)
        assertEquals(1, p.rung)
        t = run(p, t, 31, 40f)
        assertEquals(2, p.rung)
        // Rung 3 has a lower floor: the scale gives way once more before rung 4 comes.
        t = run(p, t, 31, 40f)
        assertEquals(3, p.rung)
        assertEquals(0.5f, p.scale, 1e-6f)
        t = run(p, t, 31, 40f)
        assertEquals(0.45f, p.scale, 1e-6f)
        assertEquals(3, p.rung)
        t = run(p, t, 31, 40f)
        assertEquals(4, p.rung)
        t = run(p, t, 31, 40f)
        assertEquals(0.4f, p.scale, 1e-6f)
        // The bottom of the ladder is the end of the road.
        run(p, t, 2000, 40f)
        assertEquals(4, p.rung)
        assertEquals(0.4f, p.scale, 1e-6f)
    }

    @Test
    fun aTierCanForbidGivingUpEffects() {
        val p = ladderPacer(bottom = 0)
        val t = warmUp(p)
        run(p, t, 3000, 40f)
        assertEquals(0, p.rung)
        assertEquals(0.5f, p.scale, 1e-6f)
    }

    @Test
    fun aLongSmoothStretchAtTheCeilingClimbsOneRungUp() {
        val p = ladderPacer(startRung = 3, start = 0.5f)
        var t = warmUp(p)
        // Two windows lift the scale from 0.5 to the rung's 0.7 ceiling, then three more windows
        // (360 smooth frames) at the ceiling earn a rung.
        t = run(p, t, 560, 16.6f)
        assertEquals(0.7f, p.scale, 1e-6f)
        assertEquals(3, p.rung)
        t = run(p, t, 60, 16.6f)
        assertEquals(2, p.rung)
        // And it does not run on: the next rung has to be earned the same way.
        t = run(p, t, 200, 16.6f)
        assertEquals(2, p.rung)
        run(p, t, 1000, 16.6f)
        assertEquals(0, p.rung)
        assertEquals(0.8f, p.scale, 1e-6f)
    }

    @Test
    fun aTierCapsHowHighItClimbs() {
        val p = ladderPacer(startRung = 3, top = 2)
        val t = warmUp(p)
        run(p, t, 5000, 16.6f)
        assertEquals(2, p.rung)
    }

    @Test
    fun aRungThatWasJustTooSlowTakesTwiceAsLongToReturnTo() {
        val p = ladderPacer(startRung = 2, start = 0.5f)
        var t = warmUp(p)
        // Slow at rung 2's floor: down to rung 3.
        t = run(p, t, 31, 40f)
        assertEquals(3, p.rung)
        // Two windows to the 0.7 ceiling, then 360 frames would normally do; now it takes 720.
        t = run(p, t, 120 * 2 + 120 * 5, 16.6f)
        assertEquals(3, p.rung)
        run(p, t, 130, 16.6f)
        assertEquals(2, p.rung)
    }

    @Test
    fun aKnownHeavyGpuLoadHoldsTheRungDown() {
        val p = ladderPacer(startRung = 3)
        val t = warmUp(p)
        // Frames make 60 fps but the GPU is busy for 12 ms of each: no headroom for more effects.
        run(p, t, 5000, 16.6f, gpuMs = 12f)
        assertEquals(3, p.rung)
    }

    @Test
    fun aThirtyFpsCapDoublesTheFrameTimeLimits() {
        val slow = ScalePacer()
        slow.reset(0L)
        var t = run(slow, 0L, 130, 16.6f)
        // At the 60 fps limits, 33 ms frames are slow.
        run(slow, t, 40, 33f)
        assertEquals(0.7f, slow.scale, 1e-6f)

        val capped = ScalePacer()
        capped.targetFps = 30
        capped.reset(0L)
        t = run(capped, 0L, 65, 33f)
        t = run(capped, t, 400, 33f)
        assertEquals(0.8f, capped.scale, 1e-6f)
        // 60 ms frames are slow even against 30 fps.
        run(capped, t, 40, 60f)
        assertEquals(0.7f, capped.scale, 1e-6f)
    }

    @Test
    fun aThirtyFpsCapCountsThirtyFpsFramesAsFastForClimbing() {
        val p = ScalePacer(start = 0.5f)
        p.targetFps = 30
        p.reset(0L)
        // 33 ms frames: fast at a 30 fps target, so the scale climbs to the ceiling.
        val t = run(p, 0L, 65, 33f)
        run(p, t, 800, 33f)
        assertEquals(0.8f, p.scale, 1e-6f)
    }

    @Test
    fun changingTheTierMovesTheRungIntoRangeWithoutTouchingTheScaleUnduly() {
        val p = ladderPacer()
        warmUp(p)
        assertEquals(0.8f, p.scale, 1e-6f)
        p.setLimits(2, 4)
        assertEquals(2, p.rung)
        // Rung 2's ceiling is 0.7.
        assertEquals(0.7f, p.scale, 1e-6f)
        p.setLimits(0, 0)
        assertEquals(0, p.rung)
        p.jumpTo(3)
        assertEquals(0, p.rung)
        p.setLimits(0, 4)
        p.jumpTo(3)
        assertEquals(3, p.rung)
    }
}
