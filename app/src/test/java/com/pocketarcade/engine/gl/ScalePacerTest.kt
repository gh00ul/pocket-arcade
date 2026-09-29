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
}
