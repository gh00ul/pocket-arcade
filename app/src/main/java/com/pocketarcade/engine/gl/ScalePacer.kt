package com.pocketarcade.engine.gl

/**
 * Chooses the render scale from how long frames take. Pure logic (no GL), fed once per frame.
 *
 * - After a [reset] (new surface, new context, a different screen) the first [warmupNs] are
 *   ignored: shader compiles and texture uploads make a slow burst that says nothing about the
 *   scene's real cost, and used to drop the scale to the floor for good.
 * - It lowers the scale by a step when [slowToDrop] frames in a row miss 24 ms.
 * - It raises a step when at least 95% of the last [window] frames made 60 fps (a stray hitch no
 *   longer blocks it), or sooner — after a quarter of the window — when the GPU time is known
 *   and clearly under budget.
 * - Going back up to a scale that was just too slow takes 2, 4, then 8 windows, so a scene
 *   that sits on the edge doesn't bounce between two scales every couple of seconds.
 * - Above [ceiling] (up to [boost]) it only goes when the GPU time is known and the next step
 *   (cost ∝ scale²) is predicted to stay under two thirds of a 60 fps frame.
 */
internal class ScalePacer(
    val floor: Float = 0.5f,
    val ceiling: Float = 0.8f,
    val boost: Float = 1f,
    val start: Float = 0.8f,
    val warmupNs: Long = 2_000_000_000L,
    val window: Int = 120,
    val slowToDrop: Int = 30,
) {
    companion object {
        const val STEP = 0.1f
        const val SLOW_MS = 24f
        const val FAST_MS = 17.5f
        /** Intervals longer than this are pauses or loads, not frames. */
        const val HITCH_MS = 200f
        /** Two thirds of a 60 fps frame: the GPU budget for going above the ceiling. */
        const val GPU_BUDGET_MS = 11f
    }

    var scale = start
        private set

    private var warmupUntil = Long.MIN_VALUE
    private var started = false
    private var lastNs = 0L
    private var slowRun = 0
    private var frames = 0
    private var fast = 0
    private var gpuSum = 0f
    private var gpuCount = 0
    /** The scale last dropped from, and how many windows it takes to try it again. */
    private var failedAt = Float.MAX_VALUE
    private var backoff = 1

    /** Starts over at [start] (or keeps the current scale), ignoring the next [warmupNs]. */
    fun reset(nowNs: Long, keepScale: Boolean = false) {
        if (!keepScale) scale = start
        failedAt = Float.MAX_VALUE
        backoff = 1
        warmupUntil = nowNs + warmupNs
        started = true
        lastNs = 0L
        clearWindow()
        slowRun = 0
    }

    private fun clearWindow() {
        frames = 0
        fast = 0
        gpuSum = 0f
        gpuCount = 0
    }

    /**
     * A frame was swapped at [nowNs]; [gpuMs] is its GPU time when the driver can tell (< 0
     * otherwise). Returns the scale to render at next.
     */
    fun onFrame(nowNs: Long, gpuMs: Float = -1f): Float {
        if (!started) reset(nowNs, keepScale = true)
        val last = lastNs
        lastNs = nowNs
        if (nowNs < warmupUntil || last == 0L) return scale
        val ms = (nowNs - last) / 1_000_000f
        if (ms >= HITCH_MS) return scale
        if (ms > SLOW_MS) slowRun++ else slowRun = 0
        if (slowRun > slowToDrop && scale > floor + 1e-4f) {
            backoff = if (kotlin.math.abs(scale - failedAt) < 1e-3f) minOf(backoff * 2, 8) else 2
            failedAt = scale
            scale = maxOf(floor, scale - STEP)
            slowRun = 0
            clearWindow()
            return scale
        }
        frames++
        if (ms < FAST_MS) fast++
        if (gpuMs >= 0f) {
            gpuSum += gpuMs
            gpuCount++
        }
        val gpuAvg = if (gpuCount > 0) gpuSum / gpuCount else -1f
        val next = scale + STEP
        val retry = next >= failedAt - 1e-3f
        val need = if (retry) window * backoff else window
        val quick = !retry && gpuAvg >= 0f && frames >= window / 4 && fast * 20 >= frames * 19
        if (frames >= need || quick) {
            val smooth = fast * 20 >= frames * 19
            if (smooth) {
                if (scale < ceiling - 1e-4f) {
                    // Under the ceiling a smooth window is enough; with a GPU time, only if the
                    // bigger image is predicted to fit.
                    if (gpuAvg < 0f || predict(gpuAvg, scale, next) < 16f) scale = minOf(ceiling, next)
                } else if (scale < boost - 1e-4f && gpuAvg >= 0f && predict(gpuAvg, scale, next) < GPU_BUDGET_MS) {
                    scale = minOf(boost, next)
                }
            }
            clearWindow()
        }
        return scale
    }

    /** GPU time at scale [to] given [ms] at scale [from]: pixel work goes with the area. */
    fun predict(ms: Float, from: Float, to: Float): Float = ms * (to * to) / (from * from)
}
