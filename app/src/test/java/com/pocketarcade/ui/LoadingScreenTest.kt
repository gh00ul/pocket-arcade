package com.pocketarcade.ui

import com.pocketarcade.startup.LoadBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The loading screen's numbers, and the time the loader is given a frame. */
class LoadingScreenTest {
    @Test
    fun theBarChasesTheRealProgressAndNeverGoesBack() {
        var shown = 0f
        var last = 0f
        // The real progress jumps up, dips (a wait reporting less), then finishes.
        val targets = floatArrayOf(0.1f, 0.4f, 0.4f, 0.3f, 0.2f, 0.7f, 1f, 1f)
        for (target in targets) {
            repeat(6) {
                shown = LoadingLook.ease(shown, target, 1f / 60f, LoadingLook.CHASE)
                assertTrue("the bar fell from $last to $shown", shown >= last)
                assertTrue(shown <= 1f)
                last = shown
            }
        }
        repeat(120) { shown = LoadingLook.ease(shown, 1f, 1f / 60f, LoadingLook.CHASE) }
        assertEquals(1f, shown, 0.01f)
    }

    @Test
    fun theCalmBarCatchesUpFasterAndAFrameOfZeroTimeMovesNothing() {
        assertEquals(0f, LoadingLook.ease(0f, 1f, 0f, LoadingLook.CHASE), 0f)
        val calm = LoadingLook.ease(0f, 1f, 1f / 60f, LoadingLook.CHASE_CALM)
        val lively = LoadingLook.ease(0f, 1f, 1f / 60f, LoadingLook.CHASE)
        assertTrue(calm > lively)
    }

    @Test
    fun tipsCycleWithMotionAndHoldStillWithout() {
        val n = LoadingLook.TIPS.size
        assertEquals(0, LoadingLook.tipIndex(0f, true, n))
        assertEquals(1, LoadingLook.tipIndex(LoadingLook.TIP_SECONDS + 0.1f, true, n))
        assertEquals(0, LoadingLook.tipIndex(LoadingLook.TIP_SECONDS * n + 0.1f, true, n))
        assertEquals("calm: the first tip for good", 0, LoadingLook.tipIndex(99f, false, n))
        assertEquals(1f, LoadingLook.tipAlpha(99f, false), 0f)
    }

    @Test
    fun aTipFadesInAndOutAtTheEndsOfItsTime() {
        assertEquals(0f, LoadingLook.tipAlpha(0f, true), 1e-6f)
        assertEquals(1f, LoadingLook.tipAlpha(LoadingLook.TIP_SECONDS / 2f, true), 1e-6f)
        assertTrue(LoadingLook.tipAlpha(LoadingLook.TIP_SECONDS * 0.99f, true) < 0.2f)
    }

    // ------------------------------------------------------------------ the frame budget

    private val vsync60 = LoadBudget.vsyncNs(60f)

    @Test
    fun aLoadingScreenGivesMostOfTheFrameToLoading() {
        val s = LoadBudget.sliceNs(urgent = true, frameNs = vsync60, vsyncNs = vsync60)
        assertTrue(s > vsync60 / 2 && s < vsync60)
        // Even after a slow frame: the screen is only a bar.
        assertEquals(s, LoadBudget.sliceNs(urgent = true, frameNs = vsync60 * 3, vsyncNs = vsync60))
    }

    @Test
    fun theTitleGivesLoadingOnlyTheSlackAndStepsBackAfterASlowFrame() {
        val gentle = LoadBudget.sliceNs(urgent = false, frameNs = vsync60, vsyncNs = vsync60)
        assertTrue(gentle in 1 until vsync60 / 3)
        assertEquals("the frame before ran long: skip this one", 0L, LoadBudget.sliceNs(urgent = false, frameNs = vsync60 * 2, vsyncNs = vsync60))
        assertEquals("no frame before yet", 0L, LoadBudget.sliceNs(urgent = false, frameNs = 0L, vsyncNs = vsync60))
    }

    @Test
    fun aFasterDisplayGetsASmallerSlice() {
        val s60 = LoadBudget.sliceNs(true, LoadBudget.vsyncNs(60f), LoadBudget.vsyncNs(60f))
        val s120 = LoadBudget.sliceNs(true, LoadBudget.vsyncNs(120f), LoadBudget.vsyncNs(120f))
        assertTrue(s120 < s60)
        // An odd refresh rate can't make a zero or absurd frame.
        assertTrue(LoadBudget.vsyncNs(0f) in 1..100_000_000)
        assertTrue(LoadBudget.vsyncNs(1000f) > 1_000_000)
    }
}
