package com.pocketarcade.engine

import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class TouchType { DOWN, MOVE, UP }

/**
 * Keeps the system's back gesture from firing under a thumb resting near a screen edge: the
 * pinball flippers are "hold the left or right half" and the racer steers by dragging, so a thumb
 * on the lower left or right easily lands inside the edge strip that Back swipes start from (and
 * the host maps Back to pause). Excludes a strip [edgeWidth] wide up the bottom of each side of
 * the node, [zoneHeight] tall. Android honours only about 200 dp of exclusion height per edge (the
 * rest is ignored), so the zones are that tall and no more, and they only take effect from API 29.
 */
fun Modifier.thumbZoneGestureExclusion(
    density: Density,
    zoneHeight: Dp = 200.dp,
    edgeWidth: Dp = 80.dp,
): Modifier = this
    .systemGestureExclusion { coords ->
        val h = coords.size.height.toFloat()
        val zone = minOf(with(density) { zoneHeight.toPx() }, h)
        Rect(0f, h - zone, minOf(with(density) { edgeWidth.toPx() }, coords.size.width / 2f), h)
    }
    .systemGestureExclusion { coords ->
        val w = coords.size.width.toFloat()
        val h = coords.size.height.toFloat()
        val zone = minOf(with(density) { zoneHeight.toPx() }, h)
        Rect(w - minOf(with(density) { edgeWidth.toPx() }, w / 2f), h - zone, w, h)
    }

/**
 * Tracks recent pointer samples and reports the release velocity of a flick, measured over the
 * last [windowMs] so a slow wind-up followed by a fast snap reads as a fast flick.
 *
 * The host forwards every batched touch sample (not just one per frame), so a fast digitiser or a
 * pen gives dense samples: the ring holds twice as many as a [windowMs] window needs at 240 Hz
 * (about 45 for the default 90 ms), so the window never comes up short.
 */
class FlickTracker(private val windowMs: Long = 90L) {
    private val capacity = maxOf(24, (windowMs * 2L * 240L / 1000L).toInt() + 2)
    private val times = LongArray(capacity)
    private val xs = FloatArray(capacity)
    private val ys = FloatArray(capacity)
    private var count = 0
    private var head = 0

    var startX = 0f
        private set
    var startY = 0f
        private set
    var startTime = 0L
        private set

    fun reset(x: Float, y: Float, timeMs: Long) {
        count = 0
        head = 0
        startX = x
        startY = y
        startTime = timeMs
        add(x, y, timeMs)
    }

    fun add(x: Float, y: Float, timeMs: Long) {
        times[head] = timeMs
        xs[head] = x
        ys[head] = y
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    private fun index(back: Int): Int = ((head - 1 - back) % capacity + capacity) % capacity

    val lastX: Float get() = if (count == 0) startX else xs[index(0)]
    val lastY: Float get() = if (count == 0) startY else ys[index(0)]

    /** Velocity in units per second as (vx, vy) written into [out]. */
    fun velocity(out: Vec2): Vec2 {
        if (count < 2) return out.set(0f, 0f)
        val newest = index(0)
        var oldest = newest
        for (i in 1 until count) {
            val idx = index(i)
            if (times[newest] - times[idx] > windowMs) break
            oldest = idx
        }
        if (oldest == newest) {
            oldest = index(1)
        }
        val dt = (times[newest] - times[oldest]).coerceAtLeast(8L) / 1000f
        return out.set((xs[newest] - xs[oldest]) / dt, (ys[newest] - ys[oldest]) / dt)
    }
}
