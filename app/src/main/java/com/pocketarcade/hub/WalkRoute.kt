package com.pocketarcade.hub

import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * First person's tap-to-walk: a route over the kids' walk grid ([HubWorld.findPath]) from where
 * you stand to where you tapped, with the tile-by-tile zigzag pulled straight wherever the body
 * fits ([Body]), ending at an exact point (a machine's play spot, a patch of floor). Planned
 * once per tap; following it allocates nothing.
 */
class WalkRoute {
    companion object {
        const val MAX_POINTS = 256
        /** A waypoint on the way counts as reached this close (the body cuts corners a little). */
        const val PASS_DIST = 7f
        /** The route ends this close to its last point. */
        const val ARRIVE_DIST = 1.5f
        /** Slows down over this last stretch so the stop is smooth. */
        const val SLOW_DIST = 22f
        /** Gives up if it makes no headway for this long (someone in the way, a gap too tight). */
        const val STUCK_TIME = 1.2f
        /** Straight-line checks sample the way this often (world units). */
        private const val SAMPLE = 3f
    }

    val xs = FloatArray(MAX_POINTS)
    val ys = FloatArray(MAX_POINTS)
    var count = 0
        private set
    var index = 0
        private set
    val active: Boolean get() = count > 0
    /** The prompt spot this route takes you to (to face its machine on arrival), or null. */
    var spot: Spot? = null
        private set
    val goalX: Float get() = if (count > 0) xs[count - 1] else 0f
    val goalY: Float get() = if (count > 0) ys[count - 1] else 0f

    private var bestD = Float.MAX_VALUE
    private var stuckT = 0f
    private val tmp = FloatArray(2)

    fun clear() {
        count = 0
        index = 0
        spot = null
    }

    /**
     * Plans a route for a body at ([fromX], [fromY]) to ([toX], [toY]) through [solids] (the
     * body's), over [world]'s walk grid. False (and no route) if there's no way there.
     */
    fun plan(world: HubWorld, solids: List<Box>, fromX: Float, fromY: Float, toX: Float, toY: Float, target: Spot?): Boolean {
        clear()
        val map = world.map
        val start = nearestTile(map, fromX, fromY, 3)
        val goal = nearestTile(map, toX, toY, 4)
        if (start < 0 || goal < 0) return false
        val tiles = world.findPath(start % map.cols, start / map.cols, goal % map.cols, goal / map.cols) ?: return false
        // Every point the grid walk passes: here, each tile's centre (nudged clear of what the body
        // would brush against), then the exact goal.
        val n = tiles.size + 2
        val rx = FloatArray(n)
        val ry = FloatArray(n)
        rx[0] = fromX
        ry[0] = fromY
        for (i in tiles.indices) {
            val t = tiles[i]
            Body.pushOut(solids, (t % map.cols) * HubLayout.TILE + HubLayout.TILE / 2f, (t / map.cols) * HubLayout.TILE + HubLayout.TILE / 2f, Body.RADIUS, tmp)
            rx[i + 1] = tmp[0]
            ry[i + 1] = tmp[1]
        }
        rx[n - 1] = toX
        ry[n - 1] = toY
        // Pull it straight: from each corner, head for the furthest point in plain sight.
        var at = 0
        while (at < n - 1 && count < MAX_POINTS) {
            var next = at + 1
            while (next + 1 < n && straight(solids, rx[at], ry[at], rx[next + 1], ry[next + 1])) next++
            xs[count] = rx[next]
            ys[count] = ry[next]
            count++
            at = next
        }
        if (count == 0) {
            xs[0] = toX
            ys[0] = toY
            count = 1
        }
        index = 0
        spot = target
        bestD = Float.MAX_VALUE
        stuckT = 0f
        return true
    }

    /** Whether the body can walk straight from one point to another without touching anything. */
    fun straight(solids: List<Box>, x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        val d = hypot(x1 - x0, y1 - y0)
        val steps = (d / SAMPLE).toInt() + 1
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            if (!Body.clear(solids, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, Body.RADIUS - 0.5f)) return false
        }
        return true
    }

    /**
     * One step of following the route from ([x], [y]): writes the wanted walk (world x, z; length
     * ≤ 1) into [out]. Returns false once the route has ended (arrived, or given up).
     */
    fun steer(x: Float, y: Float, dt: Float, out: FloatArray): Boolean {
        out[0] = 0f
        out[1] = 0f
        if (!active) return false
        var gx = xs[index]
        var gy = ys[index]
        var d = hypot(gx - x, gy - y)
        while (index < count - 1 && d < PASS_DIST) {
            index++
            bestD = Float.MAX_VALUE
            gx = xs[index]
            gy = ys[index]
            d = hypot(gx - x, gy - y)
        }
        val last = index == count - 1
        if (last && d < ARRIVE_DIST) {
            count = 0
            return false
        }
        if (d < bestD - 0.25f) {
            bestD = d
            stuckT = 0f
        } else {
            stuckT += dt
            if (stuckT > STUCK_TIME) {
                clear()
                return false
            }
        }
        // Ease off over the last stretch (but keep enough push to finish).
        var left = d
        if (!last) left = Float.MAX_VALUE
        val pace = if (left < SLOW_DIST) (left / SLOW_DIST).coerceIn(0.25f, 1f) else 1f
        out[0] = (gx - x) / d * pace
        out[1] = (gy - y) / d * pace
        return true
    }

    /** The walkable tile nearest ([x], [y]) within [reach] tiles, as an index, or -1. */
    private fun nearestTile(map: HubMap, x: Float, y: Float, reach: Int): Int {
        val tx0 = (x / HubLayout.TILE).toInt()
        val ty0 = (y / HubLayout.TILE).toInt()
        var best = -1
        var bestD = Float.MAX_VALUE
        for (ty in ty0 - reach..ty0 + reach) for (tx in tx0 - reach..tx0 + reach) {
            if (!map.tileWalkable(tx, ty)) continue
            val dx = tx * HubLayout.TILE + HubLayout.TILE / 2f - x
            val dy = ty * HubLayout.TILE + HubLayout.TILE / 2f - y
            val d = sqrt(dx * dx + dy * dy)
            if (d < bestD) {
                bestD = d
                best = ty * map.cols + tx
            }
        }
        return best
    }
}
