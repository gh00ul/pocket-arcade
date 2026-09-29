package com.pocketarcade.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** The title showroom's camera route: the spline maths, the loop, and the exit push. */
class ShowroomPathTest {
    private val rowHalf = 290f
    private val path = ShowroomPath.forRow(rowHalf)

    private fun poseAt(t: Float) = CameraPose().also { path.pose(t, it) }

    @Test
    fun catmullRomPassesThroughItsControlPoints() {
        assertEquals(3f, Spline.catmullRom(1f, 3f, 9f, 4f, 0f), 1e-5f)
        assertEquals(9f, Spline.catmullRom(1f, 3f, 9f, 4f, 1f), 1e-5f)
    }

    @Test
    fun catmullRomThroughEvenlySpacedPointsIsAStraightLine() {
        // Four points on a line, equally spaced: every t in between lies on that line.
        for (i in 0..10) {
            val t = i / 10f
            assertEquals(2f + 2f * t, Spline.catmullRom(0f, 2f, 4f, 6f, t), 1e-4f)
        }
    }

    @Test
    fun theCameraIsOnEachKeyframeWhenItsTurnComes() {
        val first = poseAt(0f)
        // The first keyframe of the route, scaled by the row.
        assertEquals(-0.42f * rowHalf, first.ex, 1e-3f)
        assertEquals(50f, first.ey, 1e-3f)
        assertEquals(128f, first.ez, 1e-3f)
        assertEquals(-0.22f * rowHalf, first.tx, 1e-3f)
        val third = poseAt(2 * path.segmentSeconds)
        assertEquals(0.32f * rowHalf, third.ex, 1e-3f)
        assertEquals(0.96f, third.fovScale, 1e-4f)
    }

    @Test
    fun theLoopClosesWithoutASeam() {
        val a = poseAt(0f)
        val b = poseAt(path.loopSeconds)
        assertEquals(a.ex, b.ex, 1e-3f)
        assertEquals(a.ey, b.ey, 1e-3f)
        assertEquals(a.ez, b.ez, 1e-3f)
        assertEquals(a.tx, b.tx, 1e-3f)
        assertEquals(a.fovScale, b.fovScale, 1e-5f)
        // Any number of laps later is the same shot, and negative time works too.
        val c = poseAt(path.loopSeconds * 3f + 4.2f)
        val d = poseAt(4.2f)
        assertEquals(d.ex, c.ex, 1e-2f)
        val e = poseAt(-path.segmentSeconds)
        val f = poseAt(path.loopSeconds - path.segmentSeconds)
        assertEquals(f.ex, e.ex, 1e-2f)
    }

    @Test
    fun theSlopeIsContinuousAcrossEveryKeyframeAndTheSeam() {
        val eps = 0.02f
        for (k in 0 until path.count) {
            val t = k * path.segmentSeconds
            val before = poseAt(t - eps)
            val at = poseAt(t)
            val after = poseAt(t + eps)
            // Slope going in and slope coming out of the knot agree.
            assertEquals((at.ex - before.ex) / eps, (after.ex - at.ex) / eps, 1.0f)
            assertEquals((at.ez - before.ez) / eps, (after.ez - at.ez) / eps, 1.0f)
            assertEquals((at.tx - before.tx) / eps, (after.tx - at.tx) / eps, 1.0f)
        }
    }

    @Test
    fun theCameraNeverMovesFasterThanADolly() {
        var maxEye = 0f
        var maxTarget = 0f
        val dt = 1f / 60f
        var prev = poseAt(0f)
        var t = dt
        while (t < path.loopSeconds) {
            val p = poseAt(t)
            val eye = sqrt((p.ex - prev.ex) * (p.ex - prev.ex) + (p.ey - prev.ey) * (p.ey - prev.ey) + (p.ez - prev.ez) * (p.ez - prev.ez)) / dt
            val tgt = sqrt((p.tx - prev.tx) * (p.tx - prev.tx) + (p.ty - prev.ty) * (p.ty - prev.ty) + (p.tz - prev.tz) * (p.tz - prev.tz)) / dt
            if (eye > maxEye) maxEye = eye
            if (tgt > maxTarget) maxTarget = tgt
            prev = p
            t += dt
        }
        // Slow: a machine (58 apart) takes seconds to cross the screen.
        assertTrue("eye speed $maxEye", maxEye < 45f)
        assertTrue("target speed $maxTarget", maxTarget < 45f)
    }

    @Test
    fun theEyeStaysClearOfTheCabinetsAndTheFloor() {
        var t = 0f
        while (t < path.loopSeconds) {
            val p = poseAt(t)
            // The machines' fronts are at z = 20 and none is taller than 82.
            assertTrue("eye z ${p.ez} at $t", p.ez >= 100f)
            assertTrue("eye y ${p.ey} at $t", p.ey in 35f..130f)
            assertTrue("eye x ${p.ex} at $t", abs(p.ex) <= rowHalf * 1.05f)
            // The target stays on the row: at the fronts, and along it.
            assertTrue("target z ${p.tz} at $t", p.tz in 0f..40f)
            assertTrue("target x ${p.tx} at $t", abs(p.tx) <= rowHalf * 1.0f)
            assertTrue("fov ${p.fovScale} at $t", p.fovScale in 0.9f..1.1f)
            t += 0.25f
        }
    }

    @Test
    fun theRouteScalesWithTheRow() {
        val small = ShowroomPath.forRow(100f)
        val a = CameraPose().also { small.pose(0f, it) }
        assertEquals(-0.42f * 100f, a.ex, 1e-3f)
        // Depth and height don't scale with the row.
        assertEquals(128f, a.ez, 1e-3f)
    }

    @Test
    fun aLoopNeedsFourKeyframes() {
        val e = runCatching { ShowroomPath(FloatArray(ShowroomPath.STRIDE * 3), 5f) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test
    fun theStartIsAWellFramedShotNotTheLoopsFirstKeyframe() {
        val p = poseAt(ShowroomPath.START_SEGMENTS * path.segmentSeconds)
        assertTrue("starts inside the row (${p.ex})", abs(p.ex) < rowHalf * 0.5f)
    }

    // ---------------------------------------------------------------- the exit push

    @Test
    fun thePushStartsAtRestAndEndsAtOne() {
        assertEquals(0f, TitlePush.ease(0f), 0f)
        assertEquals(1f, TitlePush.ease(1f), 1e-6f)
        assertEquals(0f, TitlePush.ease(-3f), 0f)
        assertEquals(1f, TitlePush.ease(4f), 1e-6f)
    }

    @Test
    fun thePushOnlyEverGoesForwardAndAcceleratesBeforeAnEasedLanding() {
        var last = 0f
        var lastStep = 0f
        for (i in 1..100) {
            val v = TitlePush.ease(i / 100f)
            assertTrue(v >= last)
            val step = v - last
            // Accelerating (each step at least as long as the one before, within rounding) until the
            // last tenth, where it eases in to where it stops.
            if (i in 2..85) assertTrue("step $i", step >= lastStep - 1e-5f)
            last = v
            lastStep = step
        }
    }

    @Test
    fun theFullPushLeavesTheEyeShortOfTheCabinetsAndNarrowsTheLens() {
        var t = 0f
        while (t < path.loopSeconds) {
            val p = poseAt(t)
            val before = p.fovScale
            TitlePush.apply(p, 1f)
            // Never gets nearer the row than 70 (the fronts are at 20), so no cabinet is ever clipped through.
            assertTrue("eye z ${p.ez} at $t", p.ez >= 70f)
            assertEquals(before * TitlePush.FOV_END, p.fovScale, 1e-4f)
            t += 0.5f
        }
    }

    @Test
    fun noPushLeavesThePoseAlone() {
        val p = poseAt(3f)
        val q = CameraPose().also { it.set(p) }
        TitlePush.apply(q, 0f)
        assertEquals(p.ex, q.ex, 0f)
        assertEquals(p.ez, q.ez, 0f)
        assertEquals(p.fovScale, q.fovScale, 0f)
    }
}
