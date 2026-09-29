package com.pocketarcade.hub

import com.pocketarcade.engine.dist
import com.pocketarcade.engine.range
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A kid enjoying the arcade: wanders a path to a free machine, plays it for a while (with the
 * odd cheer), sits down for a bit, or queues at the café, buys a treat and takes it to a café
 * seat, then moves on. In first person they don't let the player walk through them: a kid
 * walking into the player steps aside (or waits), and one standing or playing where the player
 * wants to be makes way.
 */
class Npc(val look: CharacterLook, var x: Float, var y: Float, private val rng: Random, val seed: Float) {
    enum class State { IDLE, WALK, PLAY, SIT, QUEUE }

    private companion object {
        val NO_PATH = IntArray(0)
        /** Kids give way to the player inside this distance (world units, centre to centre). */
        const val GIVE_WAY = Body.RADIUS + HubWorld.KID_RADIUS + 4f
        /** A kid waiting for the player to pass gives up on where they were going after this long. */
        const val WAIT_LIMIT = 2f
        /**
         * Having given up, a kid walks on regardless of the player for this long (they are softly
         * bumped aside): a kid pinned against a cabinet can't step aside, and every place they
         * could go next starts the same way.
         */
        const val IMPATIENT_TIME = 3f
    }

    var yaw = rng.range(0f, 6.28f)
        private set
    var pose = Pose.STAND
        private set
    var phase = 0f
        private set
    /** How this kid moves: blended poses, gait, gaze and follow-through (see [FigureAnim]). */
    val anim = FigureAnim(seed = (seed * 1000f).toInt(), scale = 1f)
    var state = State.IDLE
        private set
    /** Index of the hangout this kid is heading to or using, or -1. */
    var hangout = -1
        private set
    /** The café queue spot this kid holds (walking there or standing in it), or -1. */
    var queueSpot = -1
        private set
    /** True while this kid stands at the till waiting for the barista. */
    val ordering: Boolean get() = state == State.QUEUE && queueSpot == 0
    /** Carrying a café treat (a cup or a cone, by [Figure.heldItem]). */
    var holding = false
        private set

    private var timer = rng.range(0.5f, 3f)
    private var path: IntArray = NO_PATH
    private var pathPos = 0
    private val speed = rng.range(30f, 44f)
    private var stuckT = 0f
    private var lastX = x
    private var lastY = y
    private var targetYaw = yaw
    /** Where the walk ends after the last path tile (a seat, a play spot, a queue spot). */
    private var endX = 0f
    private var endY = 0f
    private var hasEnd = false
    /** How long this kid has been in the café queue; they give up after a while. */
    private var waited = 0f
    /** How long this kid has been waiting for the player to get out of the way. */
    private var giveWayT = 0f
    /** Seconds left of walking on regardless of the player (see [IMPATIENT_TIME]). */
    private var impatientT = 0f

    /**
     * One simulation step: where the kid goes and what they do (which the animation never
     * changes), then the animation follows it.
     */
    fun update(dt: Float, world: HubWorld) {
        step(dt, world)
        anim.update(dt, x, y, yaw, pose, phase)
    }

    private fun step(dt: Float, world: HubWorld) {
        if (world.firstPerson && giveWay(dt, world)) {
            yaw = turnTowards(yaw, targetYaw, dt * 8f)
            return
        }
        when (state) {
            State.IDLE -> {
                pose = if (holding) Pose.HOLD else Pose.STAND
                timer -= dt
                if (timer <= 0f) {
                    // Finished a treat without finding a seat.
                    if (holding && rng.nextFloat() < 0.5f) holding = false
                    chooseTarget(world)
                }
            }
            State.PLAY, State.SIT -> {
                timer -= dt
                // A little celebration now and then.
                pose = if (state == State.SIT) {
                    if (holding) Pose.SIP else Pose.SIT
                } else if ((timer % 5f) < 0.8f) Pose.CHEER else Pose.PLAY
                if (timer <= 0f) {
                    state = State.IDLE
                    hangout = -1
                    holding = false
                    timer = rng.range(1f, 3f)
                }
            }
            State.QUEUE -> queue(dt, world)
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
        // Now and then, fancy a treat from the café.
        if (!holding && map.cafeQueue.isNotEmpty() && rng.nextFloat() < 0.2f) {
            val k = world.cafe.join(this, map.cafeQueue.size)
            if (k >= 0) {
                queueSpot = k
                waited = 0f
                val q = map.cafeQueue[k]
                if (startWalk(world, q.tileX, q.tileY, -1, q.x, q.z)) return
                world.cafe.leave(this)
                queueSpot = -1
                return
            }
        }
        var goalX: Int
        var goalY: Int
        var target = -1
        if (rng.nextFloat() < 0.75f && map.hangouts.isNotEmpty()) {
            val pick = rng.nextInt(map.hangouts.size)
            if (world.hangoutFree(pick, this)) target = pick
        }
        if (target >= 0) {
            val h = map.hangouts[target]
            startWalk(world, h.tileX, h.tileY, target, h.x, h.z)
            return
        }
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
        startWalk(world, goalX, goalY, -1, 0f, 0f, end = false)
    }

    /**
     * Paths to tile ([goalX], [goalY]) and then, with [end], steps on to ([ex], [ey]). Returns
     * false (and waits a moment) when there's no way there.
     */
    private fun startWalk(world: HubWorld, goalX: Int, goalY: Int, target: Int, ex: Float, ey: Float, end: Boolean = true): Boolean {
        val sx = (x / HubLayout.TILE).toInt()
        val sy = ((y - 4f) / HubLayout.TILE).toInt()
        val p = world.findPath(sx, sy, goalX, goalY)
        if (p == null || (p.isEmpty() && !end)) {
            timer = rng.range(0.5f, 1.5f)
            return false
        }
        path = p
        pathPos = 0
        hangout = target
        hasEnd = end
        endX = ex
        endY = ey
        state = State.WALK
        stuckT = 0f
        return true
    }

    private fun walk(dt: Float, world: HubWorld) {
        pose = if (holding) Pose.CARRY else Pose.WALK
        val goalX: Float
        val goalY: Float
        val onPath = pathPos < path.size
        if (onPath) {
            val node = path[pathPos]
            val tx = (node % world.map.cols) * HubLayout.TILE + HubLayout.TILE / 2f
            val ty = (node / world.map.cols) * HubLayout.TILE + HubLayout.TILE / 2f + 4f
            val finalNode = pathPos == path.size - 1
            goalX = if (finalNode && hasEnd) endX else tx
            goalY = if (finalNode && hasEnd) endY else ty
        } else if (hasEnd && dist(x, y, endX, endY) >= 1.5f) {
            goalX = endX
            goalY = endY
        } else {
            arrive(world)
            return
        }
        val dx = goalX - x
        val dy = goalY - y
        val d = dist(x, y, goalX, goalY)
        if (d < 1.5f) {
            if (onPath) pathPos++ else arrive(world)
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
            if (queueSpot >= 0) {
                world.cafe.leave(this)
                queueSpot = -1
            }
            timer = 0.5f
        }
    }

    /** The direction (radians, as [yaw]) this walk is heading right now: at the next path tile or, last, the end point. */
    private fun headingOf(world: HubWorld): Float {
        val gx: Float
        val gy: Float
        if (pathPos < path.size) {
            val node = path[pathPos]
            val atEnd = hasEnd && pathPos == path.size - 1
            gx = if (atEnd) endX else (node % world.map.cols) * HubLayout.TILE + HubLayout.TILE / 2f
            gy = if (atEnd) endY else (node / world.map.cols) * HubLayout.TILE + HubLayout.TILE / 2f + 4f
        } else if (hasEnd) {
            gx = endX
            gy = endY
        } else {
            return targetYaw
        }
        return if (gx == x && gy == y) targetYaw else atan2(gx - x, gy - y)
    }

    private fun arrive(world: HubWorld) {
        if (queueSpot >= 0) {
            state = State.QUEUE
            targetYaw = world.map.cafeQueue[queueSpot].yaw
            return
        }
        if (hangout >= 0 && world.hangoutFree(hangout, this)) {
            val h = world.map.hangouts[hangout]
            state = if (h.playing) State.PLAY else State.SIT
            targetYaw = h.yaw
            timer = if (holding) rng.range(9f, 16f) else rng.range(5f, 12f)
        } else {
            state = State.IDLE
            hangout = -1
            timer = rng.range(1.5f, 4f)
        }
    }

    /** Waiting in the café queue: shuffle up when there's room, give up if it takes too long. */
    private fun queue(dt: Float, world: HubWorld) {
        pose = Pose.STAND
        waited += dt
        val k = queueSpot
        if (k > 0 && world.cafe.free(k - 1)) {
            world.cafe.moveUp(this, k)
            queueSpot = k - 1
            val q = world.map.cafeQueue[k - 1]
            path = NO_PATH
            pathPos = 0
            hangout = -1
            hasEnd = true
            endX = q.x
            endY = q.z
            state = State.WALK
            stuckT = 0f
            return
        }
        if (waited > 45f) {
            world.cafe.leave(this)
            queueSpot = -1
            state = State.IDLE
            timer = rng.range(0.5f, 2f)
        }
    }

    /** The barista hands over the treat: take it to a free café seat. */
    fun served(world: HubWorld) {
        world.cafe.leave(this)
        queueSpot = -1
        holding = true
        val map = world.map
        val n = map.hangouts.size
        val start = rng.nextInt(n.coerceAtLeast(1))
        for (i in 0 until n) {
            val idx = (start + i) % n
            val h = map.hangouts[idx]
            if (!h.cafe || !world.hangoutFree(idx, this)) continue
            if (startWalk(world, h.tileX, h.tileY, idx, h.x, h.z)) return
        }
        // Nowhere to sit: stand about with it for a bit.
        state = State.IDLE
        timer = rng.range(3f, 6f)
    }

    /**
     * First person: keeps out of the player's way. Returns true if this step went on stepping
     * aside (instead of the usual walk). Nobody is ever moved into a solid.
     */
    private fun giveWay(dt: Float, world: HubWorld): Boolean {
        val p = world.player
        val dx = x - p.x
        val dy = y - p.y
        val d2 = dx * dx + dy * dy
        if (d2 >= GIVE_WAY * GIVE_WAY) {
            giveWayT = 0f
            impatientT = 0f
            return false
        }
        if (impatientT > 0f) impatientT -= dt
        val d = sqrt(d2).coerceAtLeast(1e-3f)
        val solids = world.map.solids
        var stepping = false
        when (state) {
            // Playing or loitering right where the player wants to be: make way.
            State.PLAY, State.IDLE -> if (d < GIVE_WAY - 2f) {
                state = State.IDLE
                hangout = -1
                timer = 0f
            }
            State.WALK -> {
                // Which way they're really going: their facing only turns to it once they've taken
                // a step, and this branch keeps them from taking one.
                val heading = headingOf(world)
                val hx = sin(heading)
                val hy = cos(heading)
                // The player is ahead: step aside, away from them, if there's room; else wait.
                if (impatientT <= 0f && -(dx * hx + dy * hy) / d > 0.2f) {
                    var sx = -hy
                    var sy = hx
                    if (sx * dx + sy * dy < 0f) {
                        sx = -sx
                        sy = -sy
                    }
                    val step = speed * 0.8f * dt
                    val nx = x + sx * step
                    val ny = y + sy * step
                    if (!Collision.blocked(solids, nx, ny)) {
                        x = nx
                        y = ny
                        phase += dt * speed * 0.2f
                    }
                    pose = if (holding) Pose.CARRY else Pose.WALK
                    giveWayT += dt
                    stepping = giveWayT < WAIT_LIMIT
                    if (!stepping) {
                        // Waited long enough: go somewhere else.
                        giveWayT = 0f
                        impatientT = IMPATIENT_TIME
                        state = State.IDLE
                        hangout = -1
                        if (queueSpot >= 0) {
                            world.cafe.leave(this)
                            queueSpot = -1
                        }
                        timer = 0.5f
                    }
                }
            }
            // Sitting or queueing: the player goes round.
            else -> {}
        }
        // Bumped into: shuffle out of the way if there's room behind.
        val minD = Body.RADIUS + HubWorld.KID_RADIUS
        if (d < minD && state != State.SIT && state != State.QUEUE) {
            val push = (minD - d) * 0.5f
            val nx = x + dx / d * push
            val ny = y + dy / d * push
            if (!Collision.blocked(solids, nx, ny)) {
                x = nx
                y = ny
            }
        }
        return stepping
    }
}
