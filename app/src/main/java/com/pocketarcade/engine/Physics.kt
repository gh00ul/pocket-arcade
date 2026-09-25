package com.pocketarcade.engine

import kotlin.math.sqrt

/** A circular rigid body (no rotational dynamics; [angle] is a visual roll derived from motion). */
class Body(var x: Float, var y: Float, var r: Float) {
    var vx = 0f
    var vy = 0f
    var mass = r * r
    var restitution = 0.2f
    var friction = 0.4f
    /** Fraction of velocity lost per second (air drag / surface friction). */
    var damping = 0.1f
    /** Kinematic bodies are moved by game code and push others with infinite mass. */
    var kinematic = false
    var enabled = true
    var angle = 0f
    var kind = 0
    var tag = 0
    var data: Any? = null
    var touching = false

    val invMass: Float get() = if (kinematic || mass <= 0f) 0f else 1f / mass
}

/** A static line segment wall; bodies collide with it from either side. */
class Segment(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val bounce: Float = 0.3f)

/**
 * Impulse-based circle physics with positional correction, used by the claw machine (side view
 * with gravity) and the coin pusher (top-down, gravity off, heavy damping).
 */
class CircleWorld(var gravityX: Float = 0f, var gravityY: Float = 0f) {
    val bodies = ArrayList<Body>()
    val segments = ArrayList<Segment>()
    var iterations = 6
    /** Relative normal speeds below this are treated as resting contact (no bounce) to kill jitter. */
    var restingSpeed = 40f
    /** Extra per-body constraint applied every solver iteration (e.g. a moving pusher wall). */
    var constraint: ((Body) -> Unit)? = null

    /** Keeps [bodies] sorted by x (insertion sort: cheap because order barely changes per step). */
    private fun sortByX() {
        for (i in 1 until bodies.size) {
            val b = bodies[i]
            var j = i - 1
            while (j >= 0 && bodies[j].x > b.x) {
                bodies[j + 1] = bodies[j]
                j--
            }
            bodies[j + 1] = b
        }
    }

    fun step(dt: Float) {
        for (b in bodies) {
            if (!b.enabled) continue
            b.touching = false
            if (b.kinematic) continue
            b.vx += gravityX * dt
            b.vy += gravityY * dt
            val k = (1f - b.damping * dt).coerceIn(0f, 1f)
            b.vx *= k
            b.vy *= k
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.angle += b.vx * dt / b.r
        }
        var maxR = 0f
        for (b in bodies) if (b.r > maxR) maxR = b.r
        val extra = constraint
        repeat(iterations) {
            // Sweep and prune along x: once the gap exceeds the largest possible overlap, stop.
            sortByX()
            val n = bodies.size
            for (i in 0 until n) {
                val a = bodies[i]
                if (!a.enabled) continue
                val reach = a.r + maxR
                for (j in i + 1 until n) {
                    val b = bodies[j]
                    if (b.x - a.x > reach) break
                    if (!b.enabled) continue
                    collide(a, b)
                }
            }
            for (a in bodies) {
                if (!a.enabled || a.kinematic) continue
                for (s in segments) collideSegment(a, s)
                if (extra != null) extra(a)
            }
        }
    }

    fun collide(a: Body, b: Body): Boolean {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val rs = a.r + b.r
        val d2 = dx * dx + dy * dy
        if (d2 >= rs * rs) return false
        val wa = a.invMass
        val wb = b.invMass
        val wsum = wa + wb
        if (wsum <= 0f) return false
        var d = sqrt(d2)
        var nx: Float
        var ny: Float
        if (d < 1e-4f) {
            nx = 0f; ny = 1f; d = 0f
        } else {
            nx = dx / d; ny = dy / d
        }
        val pen = rs - d
        val corr = pen / wsum * 0.9f
        a.x -= nx * corr * wa
        a.y -= ny * corr * wa
        b.x += nx * corr * wb
        b.y += ny * corr * wb
        a.touching = true
        b.touching = true

        val rvx = b.vx - a.vx
        val rvy = b.vy - a.vy
        val vn = rvx * nx + rvy * ny
        if (vn < 0f) {
            val e = if (-vn < restingSpeed) 0f else minOf(a.restitution, b.restitution)
            val j = -(1f + e) * vn / wsum
            a.vx -= j * nx * wa
            a.vy -= j * ny * wa
            b.vx += j * nx * wb
            b.vy += j * ny * wb
            // Coulomb friction along the tangent.
            val tx = -ny
            val ty = nx
            val vt = (b.vx - a.vx) * tx + (b.vy - a.vy) * ty
            var jt = -vt / wsum
            val mu = (a.friction + b.friction) * 0.5f
            val maxF = j * mu
            jt = jt.coerceIn(-maxF, maxF)
            a.vx -= jt * tx * wa
            a.vy -= jt * ty * wa
            b.vx += jt * tx * wb
            b.vy += jt * ty * wb
        }
        return true
    }

    fun collideSegment(b: Body, s: Segment): Boolean {
        val ex = s.x2 - s.x1
        val ey = s.y2 - s.y1
        val l2 = ex * ex + ey * ey
        var t = if (l2 > 0f) ((b.x - s.x1) * ex + (b.y - s.y1) * ey) / l2 else 0f
        t = t.coerceIn(0f, 1f)
        val cx = s.x1 + ex * t
        val cy = s.y1 + ey * t
        val dx = b.x - cx
        val dy = b.y - cy
        val d2 = dx * dx + dy * dy
        if (d2 >= b.r * b.r) return false
        val d = sqrt(d2)
        val nx: Float
        val ny: Float
        if (d < 1e-4f) {
            val l = sqrt(l2).coerceAtLeast(1e-4f)
            nx = -ey / l; ny = ex / l
        } else {
            nx = dx / d; ny = dy / d
        }
        val pen = b.r - d
        b.x += nx * pen
        b.y += ny * pen
        b.touching = true
        val vn = b.vx * nx + b.vy * ny
        if (vn < 0f) {
            val e = if (-vn < restingSpeed) 0f else minOf(b.restitution, s.bounce)
            b.vx -= (1f + e) * vn * nx
            b.vy -= (1f + e) * vn * ny
            val tx = -ny
            val ty = nx
            val vt = b.vx * tx + b.vy * ty
            val maxF = -vn * b.friction
            val jt = (-vt).coerceIn(-maxF, maxF)
            b.vx += jt * tx
            b.vy += jt * ty
        }
        return true
    }
}
