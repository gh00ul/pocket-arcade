package com.pocketarcade.hub

import com.pocketarcade.engine.dist
import com.pocketarcade.engine.range
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.random.Random

/**
 * A kid enjoying the arcade: wanders a path to a free machine, plays it for a while (with the
 * odd cheer), sits at the snack bar, then moves on.
 */
class Npc(val look: CharacterLook, var x: Float, var y: Float, private val rng: Random, val seed: Float) {
    enum class State { IDLE, WALK, PLAY, SIT }

    var yaw = rng.range(0f, 6.28f)
        private set
    var pose = Pose.STAND
        private set
    var phase = 0f
        private set
    var state = State.IDLE
        private set
    /** Index of the hangout this kid is heading to or using, or -1. */
    var hangout = -1
        private set

    private var timer = rng.range(0.5f, 3f)
    private var path: IntArray = IntArray(0)
    private var pathPos = 0
    private val speed = rng.range(30f, 44f)
    private var stuckT = 0f
    private var lastX = x
    private var lastY = y
    private var targetYaw = yaw

    fun update(dt: Float, world: HubWorld) {
        when (state) {
            State.IDLE -> {
                pose = Pose.STAND
                timer -= dt
                if (timer <= 0f) chooseTarget(world)
            }
            State.PLAY, State.SIT -> {
                timer -= dt
                // A little celebration now and then.
                pose = if (state == State.SIT) Pose.SIT else if ((timer % 5f) < 0.8f) Pose.CHEER else Pose.PLAY
                if (timer <= 0f) {
                    state = State.IDLE
                    hangout = -1
                    timer = rng.range(1f, 3f)
                }
            }
            State.WALK -> walk(dt, world)
        }
        yaw = turnTowards(yaw, targetYaw, dt * 8f)
    }

    private fun turnTowards(a: Float, b: Float, maxStep: Float): Float {
        var d = (b - a) % (2f * PI.toFloat())
        if (d > PI) d -= 2f * PI.toFloat()
        if (d < -PI) d += 2f * PI.toFloat()
        return a + d.coerceIn(-maxStep, maxStep)
    }

    private fun chooseTarget(world: HubWorld) {
        val map = world.map
        var goalX: Int
        var goalY: Int
        var target = -1
        if (rng.nextFloat() < 0.75f && map.hangouts.isNotEmpty()) {
            val pick = rng.nextInt(map.hangouts.size)
            if (world.hangoutFree(pick, this)) target = pick
        }
        if (target >= 0) {
            val h = map.hangouts[target]
            goalX = (h.x / HubLayout.TILE).toInt()
            goalY = (h.z / HubLayout.TILE).toInt()
        } else {
            var tries = 0
            do {
                goalX = rng.nextInt(1, map.cols - 1)
                goalY = rng.nextInt(8, map.rows - 4)
                tries++
            } while (!map.tileWalkable(goalX, goalY) && tries < 30)
            if (!map.tileWalkable(goalX, goalY)) {
                timer = 1f
                return
            }
        }
        val sx = (x / HubLayout.TILE).toInt()
        val sy = ((y - 4f) / HubLayout.TILE).toInt()
        val p = world.findPath(sx, sy, goalX, goalY)
        if (p == null || p.isEmpty()) {
            timer = rng.range(0.5f, 1.5f)
            return
        }
        path = p
        pathPos = 0
        hangout = target
        state = State.WALK
        stuckT = 0f
    }

    private fun walk(dt: Float, world: HubWorld) {
        if (pathPos >= path.size) {
            arrive(world)
            return
        }
        pose = Pose.WALK
        val node = path[pathPos]
        val tx = (node % world.map.cols) * HubLayout.TILE + HubLayout.TILE / 2f
        val ty = (node / world.map.cols) * HubLayout.TILE + HubLayout.TILE / 2f + 4f
        val finalNode = pathPos == path.size - 1
        val goalX = if (finalNode && hangout >= 0) world.map.hangouts[hangout].x else tx
        val goalY = if (finalNode && hangout >= 0) world.map.hangouts[hangout].z else ty
        val dx = goalX - x
        val dy = goalY - y
        val d = dist(x, y, goalX, goalY)
        if (d < 1.5f) {
            pathPos++
        } else {
            val step = minOf(speed * dt, d)
            x += dx / d * step
            y += dy / d * step
            targetYaw = atan2(dx, dy)
        }
        phase += dt * speed * 0.2f
        stuckT = if (dist(x, y, lastX, lastY) < 0.01f) stuckT + dt else 0f
        lastX = x
        lastY = y
        if (stuckT > 1.5f) {
            state = State.IDLE
            hangout = -1
            timer = 0.5f
        }
    }

    private fun arrive(world: HubWorld) {
        if (hangout >= 0 && world.hangoutFree(hangout, this)) {
            val h = world.map.hangouts[hangout]
            state = if (h.playing) State.PLAY else State.SIT
            targetYaw = h.yaw
            timer = rng.range(5f, 12f)
        } else {
            state = State.IDLE
            hangout = -1
            timer = rng.range(1.5f, 4f)
        }
    }
}
