package com.pocketarcade.hub

import com.pocketarcade.engine.len

/**
 * Floating virtual joystick: it appears wherever the thumb lands and the base trails the thumb
 * if it is dragged past the rim. It is thumb-sized on any screen ([radiusFor]). Output is an
 * analog vector through a response curve ([curve]): a small dead zone, fine control near the
 * centre, full walking speed a little short of the rim, and [run] ramping up at the rim.
 */
class Joystick {
    companion object {
        /** The base's radius on a phone: about a thumb's reach, in dp... */
        const val RADIUS_DP = 56f
        /** ...but never more than this much of the screen's shorter side. */
        const val MAX_SCREEN_FRAC = 0.16f
        /** Deflections under this (of the radius) do nothing, so resting a thumb doesn't creep. */
        const val DEAD_ZONE = 0.1f
        /** The deflection (of the radius) that gives full walking speed. */
        const val FULL_AT = 0.85f
        /** How linear the curve is at the centre (the rest is quadratic): lower is finer. */
        const val LINEAR = 0.4f
        /** [run] ramps from 0 to 1 between these deflections, right at the rim. */
        const val RUN_FROM = 0.9f
        const val RUN_FULL = 0.98f

        /** The base radius in pixels for a screen [w] × [h] at [density] pixels per dp. */
        fun radiusFor(density: Float, w: Float, h: Float): Float {
            val byThumb = RADIUS_DP * density.coerceAtLeast(0.5f)
            val short = minOf(w, h)
            return if (short > 0f) minOf(byThumb, MAX_SCREEN_FRAC * short) else byThumb
        }

        /** Output (0..1) for a deflection [m] (0..1 of the radius). */
        fun curve(m: Float): Float {
            if (m <= DEAD_ZONE) return 0f
            val t = ((m - DEAD_ZONE) / (FULL_AT - DEAD_ZONE)).coerceAtMost(1f)
            return t * (LINEAR + (1f - LINEAR) * t)
        }

        /** How much of a run (0..1) a deflection [m] asks for. */
        fun runFor(m: Float): Float = ((m - RUN_FROM) / (RUN_FULL - RUN_FROM)).coerceIn(0f, 1f)
    }

    var active = false
        private set
    var pointerId = -1L
        private set
    var baseX = 0f
        private set
    var baseY = 0f
        private set
    var knobX = 0f
        private set
    var knobY = 0f
        private set
    /** Radius of the base in screen pixels. */
    var radius = 120f
    var outX = 0f
        private set
    var outY = 0f
        private set
    /** 0..1: how hard the thumb is pushing at the rim (first person runs). */
    var run = 0f
        private set
    /** How far (pixels) the thumb has strayed from where it landed; a tap barely moves. */
    var travel = 0f
        private set
    private var downX = 0f
    private var downY = 0f

    fun down(id: Long, x: Float, y: Float) {
        if (active) return
        active = true
        pointerId = id
        baseX = x
        baseY = y
        knobX = x
        knobY = y
        downX = x
        downY = y
        travel = 0f
        outX = 0f
        outY = 0f
        run = 0f
    }

    fun move(id: Long, x: Float, y: Float) {
        if (!active || id != pointerId) return
        travel = maxOf(travel, len(x - downX, y - downY))
        var dx = x - baseX
        var dy = y - baseY
        val d = len(dx, dy)
        if (d > radius) {
            // Drag the base along so reversing direction is instant.
            baseX = x - dx / d * radius
            baseY = y - dy / d * radius
            dx = x - baseX
            dy = y - baseY
        }
        knobX = x
        knobY = y
        val l = len(dx, dy)
        val m = l / radius
        val out = curve(m)
        if (out <= 0f) {
            outX = 0f; outY = 0f
        } else {
            outX = dx / l * out
            outY = dy / l * out
        }
        run = runFor(m)
    }

    fun up(id: Long) {
        if (id != pointerId) return
        release()
    }

    fun release() {
        active = false
        pointerId = -1L
        outX = 0f
        outY = 0f
        run = 0f
    }
}
