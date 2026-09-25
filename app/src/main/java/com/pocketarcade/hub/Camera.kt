package com.pocketarcade.hub

import com.pocketarcade.engine.damp

/**
 * Follows a target smoothly with a little look-ahead in the direction of travel and never shows
 * anything outside the hall. Positions are the top-left of the view in art pixels.
 */
class Camera {
    var x = 0f
        private set
    var y = 0f
        private set
    var viewW = 180f
        private set
    var viewH = 320f
        private set
    private var worldW = 0f
    private var worldH = 0f
    private var leadX = 0f
    private var leadY = 0f
    /** Extra room above the hall so the HUD never covers the back wall. */
    var topMargin = 0f

    fun setViewport(viewWidth: Float, viewHeight: Float, worldWidth: Float, worldHeight: Float) {
        viewW = viewWidth
        viewH = viewHeight
        worldW = worldWidth
        worldH = worldHeight
    }

    fun snapTo(targetX: Float, targetY: Float) {
        x = clampX(targetX - viewW / 2f)
        y = clampY(targetY - viewH / 2f)
    }

    fun follow(targetX: Float, targetY: Float, velX: Float, velY: Float, dt: Float) {
        leadX = damp(leadX, velX * 0.35f, 3f, dt)
        leadY = damp(leadY, velY * 0.45f, 3f, dt)
        val tx = clampX(targetX + leadX - viewW / 2f)
        val ty = clampY(targetY + leadY - viewH * 0.55f)
        x = damp(x, tx, 5f, dt)
        y = damp(y, ty, 5f, dt)
    }

    private fun clampX(v: Float): Float =
        if (worldW <= viewW) (worldW - viewW) / 2f else v.coerceIn(0f, worldW - viewW)

    private fun clampY(v: Float): Float =
        if (worldH + topMargin <= viewH) (worldH - viewH) / 2f else v.coerceIn(-topMargin, worldH - viewH)
}
