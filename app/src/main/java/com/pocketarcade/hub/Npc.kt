package com.pocketarcade.hub

import androidx.compose.ui.graphics.ImageBitmap
import com.pocketarcade.engine.dist
import com.pocketarcade.engine.range
import kotlin.math.abs
import kotlin.random.Random

/**
 * An idle kid who wanders the hall: waits, walks a BFS path to a random spot or a free machine,
 * "plays" for a while facing the cabinet, then moves on.
 */
class Npc(val look: CharacterLook, var x: Float, var y: Float, private val rng: Random) {
    enum class State { IDLE, WALK, PLAY }

    private companion object {
        val WALK_CYCLE = intArrayOf(0, 1, 0, 2)
    }

    val frames: Array<Array<ImageBitmap>> = CharacterArt.frames(look)
    var dir = CharacterArt.DOWN
    var frame = 0
        private set
    var state = State.IDLE
        private set
    /** Index of the hangout (machine) this kid is heading to or playing at, or -1. */
    var hangout = -1
        private set
    var hop = 0f
        private set

    private var timer = rng.range(0.5f, 3f)
    private var path: IntArray = IntArray(0)
    private var pathPos = 0
    private var animT = 0f
    private val speed = rng.range(34f, 48f)
    private var stuckT = 0f
    private var lastX = x
    private var lastY = y
    private var playT = 0f

    fun update(dt: Float, world: HubWorld) {
        hop = 0f
        when (state) {
            State.IDLE -> {
                frame = 0
                timer -= dt
                if (timer <= 0f) chooseTarget(world)
            }
            State.PLAY -> {
                frame = 0
                dir = CharacterArt.UP
                playT += dt
                // Little celebratory hops now and then.
                val cycle = playT % 2.4f
                if (cycle < 0.3f && (playT / 2.4f).toInt() % 2 == 1) hop = kotlin.math.sin(cycle / 0.3f * Math.PI.toFloat()) * 3f
                timer -= dt
                if (timer <= 0f) {
                    state = State.IDLE
                    hangout = -1
                    dir = CharacterArt.DOWN
                    timer = rng.range(1f, 3f)
                }
            }
            State.WALK -> walk(dt, world)
        }
    }

    private fun chooseTarget(world: HubWorld) {
        val map = world.map
        var goalX: Int
        var goalY: Int
        var target = -1
        if (rng.nextFloat() < 0.55f && map.hangouts.isNotEmpty()) {
            val pick = rng.nextInt(map.hangouts.size)
            if (world.hangoutFree(pick, this)) {
                target = pick
            }
        }
        if (target >= 0) {
            val (hx, hy) = map.hangouts[target]
            goalX = (hx / HubLayout.TILE).toInt()
            goalY = (hy / HubLayout.TILE).toInt()
        } else {
            var tries = 0
            do {
                goalX = rng.nextInt(1, map.cols - 1)
                goalY = rng.nextInt(4, map.rows - 2)
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
        val node = path[pathPos]
        val tx = (node % world.map.cols) * HubLayout.TILE + HubLayout.TILE / 2f
        val ty = (node / world.map.cols) * HubLayout.TILE + HubLayout.TILE / 2f + 4f
        val finalNode = pathPos == path.size - 1
        val goalX = if (finalNode && hangout >= 0) world.map.hangouts[hangout].first else tx
        val goalY = if (finalNode && hangout >= 0) world.map.hangouts[hangout].second else ty
        val dx = goalX - x
        val dy = goalY - y
        val d = dist(x, y, goalX, goalY)
        if (d < 1.5f) {
            pathPos++
        } else {
            val step = minOf(speed * dt, d)
            x += dx / d * step
            y += dy / d * step
            dir = if (abs(dx) > abs(dy)) {
                if (dx < 0f) CharacterArt.LEFT else CharacterArt.RIGHT
            } else {
                if (dy < 0f) CharacterArt.UP else CharacterArt.DOWN
            }
        }
        animT += dt * 7f
        frame = WALK_CYCLE[animT.toInt() % 4]
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
            state = State.PLAY
            playT = 0f
            timer = rng.range(4f, 9f)
        } else {
            state = State.IDLE
            hangout = -1
            timer = rng.range(1.5f, 4f)
        }
        frame = 0
    }
}
