package com.pocketarcade.ui

import com.pocketarcade.hub.Pose
import com.pocketarcade.share.PhotoStrip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The photo booth's poses and its timeline: a 3-2-1 countdown, then four shots with a flash each. */
class PhotoBoothPlanTest {
    private val plan = PhotoBoothPlan

    @Test
    fun thereIsOnePoseForEveryShotAndTheyAllLookDifferent() {
        assertEquals(PhotoStrip.SHOTS, plan.poses.size)
        val poses = plan.poses.map { it.pose }
        // Idle, a cheer and a seat, at least.
        for (p in listOf(Pose.STAND, Pose.CHEER, Pose.SIT)) assertTrue("no $p shot", p in poses)
        // No two shots alike, in pose and in the way the kid is turned.
        for (i in plan.poses.indices) for (j in i + 1 until plan.poses.size) {
            assertTrue("shots $i and $j look the same", plan.poses[i].pose != plan.poses[j].pose || plan.poses[i].yaw != plan.poses[j].yaw)
        }
        for (p in plan.poses) {
            assertTrue("'${p.cry}'", p.cry.isNotBlank() && p.cry.length <= 14)
            assertTrue("a pose turned right round to the back: ${p.yaw}", kotlin.math.abs(p.yaw) < 0.6f)
        }
    }

    @Test
    fun theCountdownRunsThreeTwoOneThenTheShotsStart() {
        var s = plan.at(0f)
        assertEquals(3, s.count)
        assertEquals(0, s.captured)
        assertEquals(0, s.pose)
        assertEquals(0f, s.flash, 0f)
        assertFalse(s.done)
        assertEquals(3, plan.at(PhotoBoothPlan.TICK - 0.01f).count)
        assertEquals(2, plan.at(PhotoBoothPlan.TICK).count)
        assertEquals(2, plan.at(2 * PhotoBoothPlan.TICK - 0.01f).count)
        assertEquals(1, plan.at(2 * PhotoBoothPlan.TICK).count)
        assertEquals(1, plan.at(3 * PhotoBoothPlan.TICK - 0.01f).count)
        // The first shot is taken as the count ends, with a full flash.
        s = plan.at(plan.shotTime(0))
        assertEquals(0, s.count)
        assertEquals(1, s.captured)
        assertEquals(1f, s.flash, 1e-4f)
        // A time before the start is just the start.
        assertEquals(3, plan.at(-1f).count)
        assertEquals(0, plan.at(-1f).captured)
    }

    @Test
    fun fourShotsAreTakenAGapApartEachWithAFlashThatFades() {
        assertEquals(plan.shotTime(0) + PhotoBoothPlan.GAP, plan.shotTime(1), 1e-4f)
        for (i in 0 until PhotoStrip.SHOTS) {
            val t = plan.shotTime(i)
            assertEquals("just before shot $i", i, plan.at(t - 0.001f).captured)
            val at = plan.at(t)
            assertEquals("at shot $i", i + 1, at.captured)
            assertEquals(1f, at.flash, 1e-3f)
            val half = plan.at(t + PhotoBoothPlan.FLASH / 2f)
            assertEquals(0.5f, half.flash, 0.02f)
            assertEquals(0f, plan.at(t + PhotoBoothPlan.FLASH).flash, 1e-4f)
        }
        // The flash is done before the next shot, so each is a separate flash.
        assertTrue(PhotoBoothPlan.FLASH < PhotoBoothPlan.GAP)
        assertEquals(PhotoStrip.SHOTS, plan.at(plan.duration).captured)
    }

    @Test
    fun thePoseChangesRightAfterEachFlashAndStaysOnTheLastOne() {
        for (i in 0 until PhotoStrip.SHOTS) {
            // The pose on show while the kid holds still for shot i is pose i...
            assertEquals("before shot $i", i, plan.at(plan.shotTime(i) - 0.001f).pose)
            // ...and once it's taken the next pose is on show (the last one stays).
            assertEquals("after shot $i", minOf(i + 1, PhotoStrip.SHOTS - 1), plan.at(plan.shotTime(i) + 0.001f).pose)
        }
        assertEquals(PhotoStrip.SHOTS - 1, plan.at(plan.duration).pose)
    }

    @Test
    fun theGoEndsAfterTheLastFlashAndAHold() {
        assertFalse(plan.at(plan.duration - 0.01f).done)
        assertTrue(plan.at(plan.duration).done)
        assertTrue(plan.at(plan.duration + 5f).done)
        assertTrue("the go takes ${plan.duration}s", plan.duration in 6f..12f)
        assertTrue(plan.duration >= plan.shotTime(PhotoStrip.SHOTS - 1) + PhotoBoothPlan.FLASH)
    }

    @Test
    fun sweepingTheTimelineNeverGoesBackwards() {
        var lastCount = Int.MAX_VALUE
        var lastCaptured = 0
        var lastPose = 0
        var t = 0f
        while (t <= plan.duration + 1f) {
            val s = plan.at(t)
            assertTrue("count went up at $t", s.count <= lastCount)
            assertTrue("shots went backwards at $t", s.captured >= lastCaptured)
            assertTrue("pose went backwards at $t", s.pose >= lastPose)
            assertTrue("flash out of range at $t: ${s.flash}", s.flash in 0f..1f)
            assertTrue(s.count in 0..PhotoBoothPlan.COUNT)
            assertTrue(s.captured in 0..PhotoStrip.SHOTS)
            assertTrue(s.pose in 0 until PhotoStrip.SHOTS)
            // No countdown once the first shot is taken, and no flash during it.
            if (s.captured > 0) assertEquals(0, s.count)
            if (s.count > 0) assertEquals(0f, s.flash, 0f)
            lastCount = s.count
            lastCaptured = s.captured
            lastPose = s.pose
            t += 0.01f
        }
    }
}
