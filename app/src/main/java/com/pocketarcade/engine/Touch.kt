package com.pocketarcade.engine

enum class TouchType { DOWN, MOVE, UP }

/**
 * Tracks recent pointer samples and reports the release velocity of a flick, measured over the
 * last [windowMs] so a slow wind-up followed by a fast snap reads as a fast flick.
 */
class FlickTracker(private val windowMs: Long = 90L) {
    private val capacity = 24
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
