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

/**
 * First person's body: a circle round the feet, wider than the feet box, so the eye (which sits
 * at the head) keeps a little personal space from every cabinet and wall. Moves slide along
 * walls, roll round corners, and step round the end of a face that blocks only the edge of the
 * body instead of snagging on it.
 */
object Body {
    /** The body's radius: under half the narrowest aisle (21), so every walkway stays open. */
    const val RADIUS = 10f
    /** Machines, the token kiosk and the prize counter keep you this much further off their fronts. */
    const val FRONT_GAP = 8f
    /**
     * A move blocked by a face is steered round its end when the body's centre is no more than
     * this far in from the end (the body then has to shift [RADIUS] + this sideways to clear it).
     */
    const val CORNER_REACH = 6f
    private const val EPS = 1e-3f

    /**
     * The solids the first-person body walks among: the map's, with the front of everything that
     * has a prompt spot pushed out by [FRONT_GAP] so you stop at a comfortable distance to play.
     */
    fun solidsFor(map: HubMap): List<Box> {
        val out = ArrayList<Box>(map.solids.size)
        for (b in map.solids) {
            var front = false
            for (s in map.spots) {
                if (b.bottom == s.area.top && b.left < s.area.right && b.right > s.area.left) front = true
            }
            out += if (front) Box(b.left, b.top, b.right, b.bottom + FRONT_GAP) else b
        }
        return out
    }

    /** How deep a circle of radius [r] at ([x], [y]) sinks into the solids (0 when clear). */
    fun penetration(solids: List<Box>, x: Float, y: Float, r: Float = RADIUS): Float {
        var worst = 0f
        for (i in solids.indices) {
            val b = solids[i]
            if (x <= b.left - r || x >= b.right + r || y <= b.top - r || y >= b.bottom + r) continue
            val cx = x.coerceIn(b.left, b.right)
            val cy = y.coerceIn(b.top, b.bottom)
            val dx = x - cx
            val dy = y - cy
            val d2 = dx * dx + dy * dy
            val p = if (d2 <= 1e-12f) r + minOf(minOf(x - b.left, b.right - x), minOf(y - b.top, b.bottom - y)) else r - kotlin.math.sqrt(d2)
            if (p > worst) worst = p
        }
        return worst
    }

    fun clear(solids: List<Box>, x: Float, y: Float, r: Float = RADIUS): Boolean = penetration(solids, x, y, r) <= EPS

    /** Pushes a circle out of every solid it overlaps (a few passes, so it settles in corners). */
    fun pushOut(solids: List<Box>, x: Float, y: Float, r: Float, out: FloatArray) {
        var px = x
        var py = y
        for (pass in 0 until 4) {
            var moved = false
            for (i in solids.indices) {
                val b = solids[i]
                if (px <= b.left - r || px >= b.right + r || py <= b.top - r || py >= b.bottom + r) continue
                val cx = px.coerceIn(b.left, b.right)
                val cy = py.coerceIn(b.top, b.bottom)
                val dx = px - cx
                val dy = py - cy
                val d2 = dx * dx + dy * dy
                if (d2 >= r * r) continue
                if (d2 > 1e-12f) {
                    val d = kotlin.math.sqrt(d2)
                    val push = r - d + 1e-4f
                    px += dx / d * push
                    py += dy / d * push
                } else {
                    // The centre is inside the box: out through the nearest side.
                    val l = px - b.left
                    val rt = b.right - px
                    val t = py - b.top
                    val bt = b.bottom - py
                    val m = minOf(minOf(l, rt), minOf(t, bt))
                    when (m) {
                        l -> px = b.left - r
                        rt -> px = b.right + r
                        t -> py = b.top - r
                        else -> py = b.bottom + r
                    }
                }
                moved = true
            }
            if (!moved) break
        }
        out[0] = px
        out[1] = py
    }

    /**
     * Moves the body from ([x], [y]) by ([dx], [dy]) in short substeps, pushing it out of
     * whatever it runs into (so it slides along walls and rolls round corners). A step that would
     * wedge it into a crack narrower than the body is refused. Writes the result into [out].
     */
    fun move(solids: List<Box>, x: Float, y: Float, dx: Float, dy: Float, out: FloatArray, r: Float = RADIUS): Boolean {
        val d = kotlin.math.sqrt(dx * dx + dy * dy)
        val n = if (d <= r * 0.4f) 1 else kotlin.math.ceil(d / (r * 0.4f)).toInt()
        // Starting inside something (the view just switched, a decoration landed): just get out.
        val stuck = penetration(solids, x, y, r) > EPS
        var px = x
        var py = y
        for (s in 0 until n) {
            pushOut(solids, px + dx / n, py + dy / n, r, out)
            if (!stuck && penetration(solids, out[0], out[1], r) > EPS) break
            px = out[0]
            py = out[1]
        }
        out[0] = px
        out[1] = py
        return px != x || py != y
    }

    /**
     * When a move ([dx], [dy]) from ([x], [y]) was mostly blocked, looks a little to either side
     * for a way past the end of what's in the way and, if there is one, steps sideways toward it
     * (by up to the move's length). Writes the new position into [out]; false if there's no way round.
     */
    fun slideRound(solids: List<Box>, x: Float, y: Float, dx: Float, dy: Float, out: FloatArray, r: Float = RADIUS): Boolean {
        val d = kotlin.math.sqrt(dx * dx + dy * dy)
        if (d < 1e-5f) return false
        val ux = dx / d
        val uy = dy / d
        // Perpendicular to the move.
        val sx = -uy
        val sy = ux
        // The nearest lane either side that's clear to walk on along the move.
        var k = 2f
        while (k <= r + CORNER_REACH + 1e-3f) {
            for (side in 0 until 2) {
                val sgn = if (side == 0) 1f else -1f
                val ox = x + sx * sgn * k
                val oy = y + sy * sgn * k
                if (clear(solids, ox, oy, r) && clear(solids, ox + ux * r * 0.6f, oy + uy * r * 0.6f, r)) {
                    val step = minOf(d, k)
                    return move(solids, x, y, sx * sgn * step, sy * sgn * step, out, r)
                }
            }
            k += 2f
        }
        return false
    }
}
