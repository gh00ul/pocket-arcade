package com.pocketarcade.hub

import com.pocketarcade.engine.dist
import kotlin.math.PI
import kotlin.math.abs
import kotlin.random.Random

/**
 * The café's comings and goings: who holds each queue spot, and the barista behind the counter
 * who idles, wipes the counter, takes the front kid's order, makes it at the right machine and
 * hands it over. Pure logic (seeded, allocation-free per step), so it runs in the JVM tests.
 */
class CafeLife {
    companion object {
        const val QUEUE_LEN = 4
        private const val BARISTA_SPEED = 38f
        /** The barista glances at the player standing within this distance of them (world units). */
        private const val BARISTA_NOTICE = 90f
    }

    /** The kid holding each queue spot (walking there or standing in it), front first. */
    val queue = arrayOfNulls<Npc>(QUEUE_LEN)
    val barista = Barista()
    /** How many treats have been handed over. */
    var served = 0
        private set

    /**
     * Joins the end of the queue: returns the spot [npc] should walk to, or -1 when the queue is
     * full. A newcomer never cuts in ahead of someone already waiting.
     */
    fun join(npc: Npc, spots: Int): Int {
        val len = minOf(QUEUE_LEN, spots)
        var last = -1
        for (k in 0 until len) {
            if (queue[k] === npc) return k
            if (queue[k] != null) last = k
        }
        val k = last + 1
        if (k >= len) return -1
        queue[k] = npc
        return k
    }

    /** Whether queue spot [k] is free. */
    fun free(k: Int): Boolean = k in 0 until QUEUE_LEN && queue[k] == null

    /** Moves [npc] up from spot [k] to [k] - 1. */
    fun moveUp(npc: Npc, k: Int) {
        if (queue[k] === npc) queue[k] = null
        queue[k - 1] = npc
    }

    /** Takes [npc] out of the queue wherever it is. */
    fun leave(npc: Npc) {
        for (k in 0 until QUEUE_LEN) if (queue[k] === npc) queue[k] = null
        if (barista.customer === npc) barista.customer = null
    }

    fun update(dt: Float, world: HubWorld) {
        // Drop anyone who wandered off without leaving properly.
        for (k in 0 until QUEUE_LEN) {
            val n = queue[k] ?: continue
            if (n.queueSpot != k) queue[k] = null
        }
        barista.update(dt, this, world)
    }

    /** The one member of staff: walks the lane behind the counter. */
    class Barista {
        enum class State { IDLE, WALK, WIPE, TAKE, MAKE, SERVE }

        var x = CafeLayout.TILL_X
            private set
        val z = CafeLayout.LANE_Z
        var yaw = 0f
            private set
        var pose = Pose.STAND
            private set
        /** How the barista moves (see [FigureAnim]). */
        val anim = FigureAnim(seed = 7, scale = 1.1f)
        val phase: Float get() = anim.phase
        var state = State.IDLE
            private set
        /** The kid being served, once they reach the till. */
        var customer: Npc? = null
            internal set
        /** Which treat the barista is carrying (see [Figure.heldItem]) or 0. */
        var item = 0
            private set

        private val rng = Random(7)
        private var timer = 2f
        private var goalX = x
        private var goalYaw = 0f
        /** What happens on arriving at [goalX]. */
        private var next = State.IDLE

        private fun walkTo(tx: Float, facing: Float, then: State) {
            goalX = tx.coerceIn(CafeLayout.LANE_X0, CafeLayout.LANE_X1)
            goalYaw = facing
            next = then
            state = State.WALK
        }

        fun update(dt: Float, cafe: CafeLife, world: HubWorld) {
            val c = customer
            // A kid waiting at the till gets served before anything else.
            if (c == null) {
                val front = cafe.queue[0]
                if (front != null && front.ordering && state != State.MAKE && state != State.SERVE) {
                    customer = front
                    item = 0
                    walkTo(CafeLayout.TILL_X, 0f, State.TAKE)
                }
            } else if (c.queueSpot != 0) {
                // They gave up waiting.
                customer = null
                item = 0
                if (state != State.WALK) state = State.IDLE
                timer = 1f
            }
            when (state) {
                State.WALK -> {
                    val d = goalX - x
                    if (abs(d) < 0.8f) {
                        x = goalX
                        state = next
                        yaw = turn(yaw, goalYaw, 1f)
                        timer = when (next) {
                            State.TAKE -> 1.4f
                            State.MAKE -> rng.nextFloat() * 1.2f + 2f
                            State.SERVE -> 1.1f
                            State.WIPE -> rng.nextFloat() * 2f + 2.5f
                            else -> rng.nextFloat() * 2f + 1.5f
                        }
                    } else {
                        val step = minOf(abs(d), BARISTA_SPEED * dt)
                        x += if (d > 0f) step else -step
                        goalYawWhileWalking(if (d > 0f) PI.toFloat() / 2f else -PI.toFloat() / 2f, dt)
                    }
                }
                State.TAKE -> {
                    timer -= dt
                    if (timer <= 0f) {
                        val who = customer
                        item = if (who != null) Figure.heldItem(who.look) else 1
                        // Cups come from the slushie tanks or the espresso machine, cones from the soft-serve.
                        if (item == Figure.ITEM_CONE) walkTo(CafeLayout.SOFTSERVE_X, PI.toFloat(), State.MAKE)
                        else if (rng.nextFloat() < 0.65f) walkTo(CafeLayout.SLUSH_X, 0f, State.MAKE)
                        else walkTo(CafeLayout.ESPRESSO_X, PI.toFloat(), State.MAKE)
                    }
                }
                State.MAKE -> {
                    timer -= dt
                    if (timer <= 0f) {
                        if (customer == null) {
                            item = 0
                            state = State.IDLE
                            timer = 1f
                        } else {
                            walkTo(CafeLayout.TILL_X, 0f, State.SERVE)
                        }
                    }
                }
                State.SERVE -> {
                    timer -= dt
                    if (timer <= 0f) {
                        customer?.let {
                            it.served(world)
                            cafe.served++
                        }
                        customer = null
                        item = 0
                        state = State.IDLE
                        timer = rng.nextFloat() * 1.5f + 0.8f
                    }
                }
                State.WIPE -> {
                    timer -= dt
                    if (timer <= 0f) {
                        state = State.IDLE
                        timer = rng.nextFloat() * 2f + 1f
                    }
                }
                State.IDLE -> {
                    timer -= dt
                    if (timer <= 0f) {
                        // Wipe down a stretch of counter, or check on the machines.
                        val r = rng.nextFloat()
                        if (r < 0.55f) walkTo(CafeLayout.LANE_X0 + rng.nextFloat() * (CafeLayout.LANE_X1 - CafeLayout.LANE_X0), 0f, State.WIPE)
                        else if (r < 0.8f) walkTo(CafeLayout.TILL_X, 0f, State.IDLE)
                        else walkTo(CafeLayout.ESPRESSO_X, PI.toFloat(), State.IDLE)
                    }
                }
            }
            if (state != State.WALK) yaw = turn(yaw, goalYaw, dt * 6f)
            pose = when (state) {
                State.WALK -> if (item != 0 && next == State.SERVE) Pose.CARRY else Pose.WALK
                State.WIPE -> Pose.WIPE
                State.MAKE -> Pose.PLAY
                State.SERVE -> Pose.HOLD
                State.TAKE, State.IDLE -> Pose.STAND
            }
            val served = customer
            if (served != null && (state == State.TAKE || state == State.SERVE)) {
                anim.look(served.x, served.y, Figure.HEAD_Y)
            } else if (state == State.IDLE || state == State.WIPE) {
                val p = world.player
                if (dist(x, z, p.x, p.y) < BARISTA_NOTICE) anim.look(p.x, p.y, Figure.HEAD_Y)
            }
            anim.update(dt, x, z, yaw, pose, if (state == State.WALK) (if (goalX > x) PI.toFloat() / 2f else -PI.toFloat() / 2f) else goalYaw)
        }

        private fun goalYawWhileWalking(target: Float, dt: Float) {
            yaw = turn(yaw, target, dt * 8f)
        }

        private fun turn(a: Float, b: Float, maxStep: Float): Float {
            val tau = 2f * PI.toFloat()
            var d = (b - a) % tau
            if (d > PI) d -= tau
            if (d < -PI) d += tau
            return a + d.coerceIn(-maxStep, maxStep)
        }
    }
}
