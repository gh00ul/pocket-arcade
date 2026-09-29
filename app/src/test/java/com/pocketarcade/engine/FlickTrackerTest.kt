package com.pocketarcade.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The host forwards every batched touch sample, so [FlickTracker] sees anything from one sample per
 * frame to a fast digitiser's 240 Hz. The same gesture must read the same velocity either way.
 */
class FlickTrackerTest {
    /** Feeds a vertical gesture whose height is [y] (units) at time [t] (seconds), sampled at [hz] from t = 0 to [end]. */
    private fun sample(hz: Int, end: Float, y: (Float) -> Float): FlickTracker {
        val tracker = FlickTracker()
        tracker.reset(0f, y(0f), 0L)
        val n = (end * hz).roundToInt()
        for (k in 1..n) {
            val t = k / hz.toFloat()
            tracker.add(0f, y(t), (t * 1000f).roundToInt().toLong())
        }
        return tracker
    }

    private fun vy(tracker: FlickTracker): Float = tracker.velocity(Vec2()).y

    @Test
    fun theSameFlickReadsTheSameAt60And240Hz() {
        val flick = { t: Float -> -1400f * t }
        val slow = vy(sample(60, 0.3f, flick))
        val dense = vy(sample(240, 0.3f, flick))
        assertEquals("60 Hz", -1400f, slow, 100f)
        assertEquals("240 Hz", -1400f, dense, 30f)
        assertTrue("60 Hz $slow vs 240 Hz $dense", abs(slow - dense) < 0.08f * abs(dense))
    }

    @Test
    fun aSnapAfterASlowWindUpReadsFastAtEveryRate() {
        // 300 ms creeping at 40 units/s, then 60 ms snapping at 1500 units/s.
        val wind = 0.3f
        val snap = 0.06f
        val flick = { t: Float ->
            if (t <= wind) -40f * t else -40f * wind - 1500f * (minOf(t, wind + snap) - wind)
        }
        for (hz in intArrayOf(60, 120, 240, 480)) {
            val v = vy(sample(hz, wind + snap, flick))
            // A 90 ms window holds the whole snap and a little of the wind-up: about 1000, and never
            // the ~330 that averaging the whole gesture would give.
            assertTrue("$hz Hz read $v", v < -850f && v > -1500f)
        }
    }

    @Test
    fun aFingerThatStoppedBeforeLettingGoReadsSlow() {
        // Fast for 100 ms, then held still for 150 ms: the last 90 ms is standing still.
        val flick = { t: Float -> if (t <= 0.1f) -1500f * t else -150f }
        for (hz in intArrayOf(60, 240)) {
            val v = vy(sample(hz, 0.25f, flick))
            assertTrue("$hz Hz read $v", abs(v) < 60f)
        }
    }

    @Test
    fun theWindowSurvivesADenseBurst() {
        // 480 Hz for 250 ms is 120 samples: more than any ring, yet the last 90 ms still reads true.
        val v = vy(sample(480, 0.25f) { t -> -900f * t })
        assertEquals(-900f, v, 40f)
    }

    @Test
    fun lowRateInputKeepsItsOldReading() {
        // Six samples 12 ms apart, as the original helper test feeds: the velocity is still exact.
        val tracker = FlickTracker()
        tracker.reset(0f, 0f, 0L)
        for (k in 1..6) tracker.add(0f, -1200f * k * 0.012f, (k * 12).toLong())
        assertEquals(-1200f, vy(tracker), 60f)
    }
}
