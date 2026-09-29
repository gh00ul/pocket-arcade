package com.pocketarcade.engine.gl

import com.pocketarcade.engine.r3d.RenderPass

/**
 * Chooses the render scale, and how much else to spend, from how long frames take. Pure logic
 * (no GL), fed once per frame.
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
 * - Above the rung's ceiling (up to its boost) it only goes when the GPU time is known and the
 *   next step (cost ∝ scale²) is predicted to stay under two thirds of a frame.
 *
 * On top of the scale sits a **ladder** of quality [rung]s (see [GfxQuality.LADDER]), each with
 * its own scale floor, ceiling and boost. When frames are still slow at the floor scale it
 * steps down a rung (after another [slowToDrop] slow frames); after [RUNG_UP_FRAMES] smooth
 * frames at the rung's ceiling it steps back up, with the same doubling wait for a rung that
 * was just too slow. A rung never leaves the [topRung]..[bottomRung] range of the tier.
 *
 * The 24 / 17.5 ms thresholds are for 60 fps; with a 30 fps cap ([targetFps]) they double.
 */
internal class ScalePacer(
    floor: Float = 0.5f,
    ceiling: Float = 0.8f,
    boost: Float = 1f,
    val start: Float = 0.8f,
    val warmupNs: Long = 2_000_000_000L,
    val window: Int = 120,
    val slowToDrop: Int = 30,
    ladder: Array<GfxQuality.Rung>? = null,
    startRung: Int = 0,
    topRung: Int = 0,
    bottomRung: Int = Int.MAX_VALUE,
) {
    companion object {
        const val STEP = 0.1f
        const val SLOW_MS = 24f
        const val FAST_MS = 17.5f
        /** Intervals longer than this are pauses or loads, not frames. */
        const val HITCH_MS = 200f
        /** Two thirds of a 60 fps frame: the GPU budget for going above the ceiling. */
        const val GPU_BUDGET_MS = 11f
        /** Smooth frames at a rung's ceiling before trying the next rung up (about 6 s at 60 fps). */
        const val RUNG_UP_FRAMES = 360
        /** The GPU time (ms per 60 fps frame) under which a rung up is tried; unknown counts as fine. */
        const val RUNG_UP_GPU_MS = 9f
    }

    /** Without a ladder there is just one rung, made from the plain floor, ceiling and boost. */
    private val rungs: Array<GfxQuality.Rung> =
        ladder ?: arrayOf(GfxQuality.Rung(floor, ceiling, boost, 4, 4, GfxQuality.Reflections.MIRROR, RenderPass.MAX_LIGHTS))

    private var top = topRung.coerceIn(0, rungs.lastIndex)
    private var bottom = bottomRung.coerceIn(top, rungs.lastIndex)

    /** The quality rung in force (0 = best). */
    var rung = startRung.coerceIn(top, bottom)
        private set

    var scale = start.coerceIn(rungs[rung].scaleFloor, rungs[rung].scaleCeiling)
        private set

    /** 60 or 30: the frame rate the slow and fast limits are set for. */
    var targetFps = 60
        set(v) {
            field = if (v <= 30) 30 else 60
        }

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
    /** Smooth frames spent at this rung's ceiling. */
    private var climbFrames = 0
    /** The rung last dropped from (-1 = none), and how many times [RUNG_UP_FRAMES] a return to it takes. */
    private var rungFailedAt = -1
    private var rungBackoff = 1

    private val frameK get() = 60f / targetFps

    /** Starts over at [start] (or keeps the current scale), ignoring the next [warmupNs]. */
    fun reset(nowNs: Long, keepScale: Boolean = false) {
        if (!keepScale) scale = start.coerceIn(rungs[rung].scaleFloor, rungs[rung].scaleCeiling)
        failedAt = Float.MAX_VALUE
        backoff = 1
        rungFailedAt = -1
        rungBackoff = 1
        climbFrames = 0
        warmupUntil = nowNs + warmupNs
        started = true
        lastNs = 0L
        clearWindow()
        slowRun = 0
    }

    /**
     * Confines the ladder to rungs [topRung]..[bottomRung] (a tier changed), moving the current
     * rung inside if it is outside. Gentle: the scale and the warm-up are left alone.
     */
    fun setLimits(topRung: Int, bottomRung: Int) {
        top = topRung.coerceIn(0, rungs.lastIndex)
        bottom = bottomRung.coerceIn(top, rungs.lastIndex)
        moveTo(rung.coerceIn(top, bottom))
        climbFrames = 0
    }

    /** Jumps to [r] (a device's starting guess), within the limits. */
    fun jumpTo(r: Int) {
        moveTo(r.coerceIn(top, bottom))
        climbFrames = 0
        slowRun = 0
        clearWindow()
    }

    private fun moveTo(r: Int) {
        rung = r
        scale = scale.coerceIn(rungs[r].scaleFloor, rungs[r].scaleCeiling)
    }

    private fun clearWindow() {
        frames = 0
        fast = 0
        gpuSum = 0f
        gpuCount = 0
    }

    /**
     * A frame was swapped at [nowNs]; [gpuMs] is its GPU time when the driver can tell (< 0
     * otherwise). Returns the scale to render at next; read [rung] for the rest.
     */
    fun onFrame(nowNs: Long, gpuMs: Float = -1f): Float {
        if (!started) reset(nowNs, keepScale = true)
        val last = lastNs
        lastNs = nowNs
        if (nowNs < warmupUntil || last == 0L) return scale
        val ms = (nowNs - last) / 1_000_000f
        if (ms >= HITCH_MS) return scale
        val k = frameK
        val r = rungs[rung]
        if (ms > SLOW_MS * k) slowRun++ else slowRun = 0
        if (slowRun > slowToDrop) {
            if (scale > r.scaleFloor + 1e-4f) {
                backoff = if (kotlin.math.abs(scale - failedAt) < 1e-3f) minOf(backoff * 2, 8) else 2
                failedAt = scale
                scale = maxOf(r.scaleFloor, scale - STEP)
                slowRun = 0
                clearWindow()
                return scale
            }
            if (rung < bottom) {
                // Already at the floor and still slow: give up an effect instead.
                rungBackoff = if (rung == rungFailedAt) minOf(rungBackoff * 2, 8) else 2
                rungFailedAt = rung
                moveTo(rung + 1)
                climbFrames = 0
                slowRun = 0
                clearWindow()
                return scale
            }
        }
        frames++
        if (ms < FAST_MS * k) fast++
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
                val wasAtCeiling = scale >= r.scaleCeiling - 1e-4f
                if (scale < r.scaleCeiling - 1e-4f) {
                    // Under the ceiling a smooth window is enough; with a GPU time, only if the
                    // bigger image is predicted to fit.
                    if (gpuAvg < 0f || predict(gpuAvg, scale, next) < 16f * k) scale = minOf(r.scaleCeiling, next)
                } else if (scale < r.scaleBoost - 1e-4f && gpuAvg >= 0f && predict(gpuAvg, scale, next) < GPU_BUDGET_MS * k) {
                    scale = minOf(r.scaleBoost, next)
                }
                if (wasAtCeiling) {
                    climbFrames += frames
                    if (rung > top && climbFrames >= climbNeed() && (gpuAvg < 0f || gpuAvg < RUNG_UP_GPU_MS * k)) {
                        moveTo(rung - 1)
                        climbFrames = 0
                    }
                } else {
                    climbFrames = 0
                }
            } else {
                climbFrames = 0
            }
            clearWindow()
        }
        return scale
    }

    /** Smooth frames at the ceiling before the next rung up: more for one that was just too slow. */
    private fun climbNeed(): Int = if (rung - 1 <= rungFailedAt) RUNG_UP_FRAMES * rungBackoff else RUNG_UP_FRAMES

    /** GPU time at scale [to] given [ms] at scale [from]: pixel work goes with the area. */
    fun predict(ms: Float, from: Float, to: Float): Float = ms * (to * to) / (from * from)
}
