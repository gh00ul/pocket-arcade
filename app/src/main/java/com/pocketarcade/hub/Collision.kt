package com.pocketarcade.hub

/** Axis-aligned box in hall art pixels. */
class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float) = x >= left && x < right && y >= top && y < bottom

    fun intersects(l: Float, t: Float, r: Float, b: Float) = l < right && r > left && t < bottom && b > top

    companion object {
        fun ofSize(x: Float, y: Float, w: Float, h: Float) = Box(x, y, x + w, y + h)
    }
}

/**
 * Moves an actor's feet box through a list of solid boxes one axis at a time, so walking into a
 * wall at an angle slides along it instead of sticking.
 */
object Collision {
    /** Half-width and height of the feet box used for walking collision. */
    const val FEET_HALF_W = 5f
    const val FEET_H = 5f

    fun blocked(solids: List<Box>, x: Float, y: Float): Boolean {
        val l = x - FEET_HALF_W
        val r = x + FEET_HALF_W
        val t = y - FEET_H
        for (i in solids.indices) {
            if (solids[i].intersects(l, t, r, y)) return true
        }
        return false
    }

    /**
     * Attempts to move from ([x], [y]) by ([dx], [dy]). Writes the resolved position into [out]
     * (index 0 = x, 1 = y) and returns true if any movement happened.
     */
    fun move(solids: List<Box>, x: Float, y: Float, dx: Float, dy: Float, out: FloatArray): Boolean {
        var nx = x
        var ny = y
        if (dx != 0f) {
            val tx = x + dx
            if (!blocked(solids, tx, ny)) nx = tx else nx = slideTo(solids, x, ny, dx, horizontal = true)
        }
        if (dy != 0f) {
            val ty = ny + dy
            if (!blocked(solids, nx, ty)) ny = ty else ny = slideTo(solids, nx, ny, dy, horizontal = false)
        }
        out[0] = nx
        out[1] = ny
        return nx != x || ny != y
    }

    /** Binary-searches the furthest free position along one axis so actors stop flush with walls. */
    private fun slideTo(solids: List<Box>, x: Float, y: Float, delta: Float, horizontal: Boolean): Float {
        var lo = 0f
        var hi = 1f
        repeat(6) {
            val mid = (lo + hi) / 2f
            val px = if (horizontal) x + delta * mid else x
            val py = if (horizontal) y else y + delta * mid
            if (blocked(solids, px, py)) hi = mid else lo = mid
        }
        return if (horizontal) x + delta * lo else y + delta * lo
    }
}
