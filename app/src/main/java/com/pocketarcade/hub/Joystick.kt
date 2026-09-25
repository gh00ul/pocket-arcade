package com.pocketarcade.hub

import com.pocketarcade.engine.len

/**
 * Floating virtual joystick: it appears wherever the thumb lands and the base trails the thumb
 * if it is dragged past the rim. Output is an analog vector with a small dead zone.
 */
class Joystick {
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

    private val deadZone = 0.14f

    fun down(id: Long, x: Float, y: Float) {
        if (active) return
        active = true
        pointerId = id
        baseX = x
        baseY = y
        knobX = x
        knobY = y
        outX = 0f
        outY = 0f
    }

    fun move(id: Long, x: Float, y: Float) {
        if (!active || id != pointerId) return
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
        val m = len(dx, dy) / radius
        if (m < deadZone) {
            outX = 0f; outY = 0f
        } else {
            val scaled = ((m - deadZone) / (1f - deadZone)).coerceAtMost(1f)
            val l = len(dx, dy)
            outX = dx / l * scaled
            outY = dy / l * scaled
        }
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
    }
}
