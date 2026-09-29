package com.pocketarcade.hub

import com.pocketarcade.engine.len
import kotlin.math.PI
import kotlin.math.atan2

/**
 * The player's kid: analog movement with wall sliding, smooth turning and a walk cycle.
 * Overhead the kid moves at once; in first person ([walkFirstPerson]) they get up to speed and
 * stop over a fraction of a second, and the stride quickens with the pace.
 */
class Player {
    companion object {
        const val SPEED = 78f
        /** First person: seconds from standing to full walking speed... */
        const val ACCEL_TIME = 0.18f
        /** ...and from full walking speed to a stop (also how hard a reversal brakes). */
        const val STOP_TIME = 0.1f
        /** First person: walking backwards and sideways is a little slower than forwards. */
        const val BACK_SCALE = 0.72f
        const val STRAFE_SCALE = 0.85f
        /** First person: pushing the stick to its rim breaks into a run this much faster. */
        const val RUN_SCALE = 1.35f
        /** Below this speed (world units a second) first person counts as standing still. */
        const val STILL_SPEED = 3f
    }

    var x = 0f
    var y = 0f
    var vx = 0f
        private set
    var vy = 0f
        private set
    /** Facing, radians: 0 looks toward the entrance (+z), π toward the back wall. */
    var yaw = PI.toFloat()
    var pose = Pose.STAND
        private set
    var phase = 0f
        private set
    var moving = false
        private set
    /** True for the one update in which a foot touched down (for footstep sounds). */
    var stepped = false
        private set

    var look: CharacterLook? = null
        private set

    private val out = FloatArray(2)
    private val slid = FloatArray(2)

    /** Speed as a fraction of [SPEED] (up to [RUN_SCALE] running). */
    val speedFrac: Float get() = len(vx, vy) / SPEED

    fun setLook(newLook: CharacterLook) {
        look = newLook
    }

    /**
     * Walks by the analog input ([inputX], [inputY] in world x and z, length ≤ 1). The kid turns
     * toward where they walk, or, given a [faceYaw] (first person), faces that way instead.
     */
    fun update(dt: Float, inputX: Float, inputY: Float, solids: List<Box>, faceYaw: Float = Float.NaN) {
        stepped = false
        val mag = len(inputX, inputY).coerceAtMost(1f)
        if (mag > 0.01f) {
            vx = inputX * SPEED
            vy = inputY * SPEED
            val moved = Collision.move(solids, x, y, vx * dt, vy * dt, out)
            vx = (out[0] - x) / dt
            vy = (out[1] - y) / dt
            x = out[0]
            y = out[1]
            moving = moved
            if (!faceYaw.isNaN()) {
                yaw = faceYaw
            } else {
                // Turn smoothly towards where the stick points.
                val target = atan2(inputX, inputY)
                var d = (target - yaw) % (2f * PI.toFloat())
                if (d > PI) d -= 2f * PI.toFloat()
                if (d < -PI) d += 2f * PI.toFloat()
                yaw += d.coerceIn(-dt * 12f, dt * 12f)
            }
            if (moved) {
                val before = (phase / PI.toFloat()).toInt()
                phase += dt * (6f + 6f * mag)
                if ((phase / PI.toFloat()).toInt() != before) stepped = true
            }
        } else {
            vx = 0f
            vy = 0f
            moving = false
            if (!faceYaw.isNaN()) yaw = faceYaw
        }
        pose = if (moving) Pose.WALK else Pose.STAND
    }

    /**
     * First-person walking: eases the velocity toward ([wishX], [wishY]) × [SPEED] (world x and
     * z, length up to [RUN_SCALE]), reaching it in about [ACCEL_TIME] and stopping in about
     * [STOP_TIME], and moves the round [Body] through [solids], stepping round corners. The kid
     * faces [faceYaw]. The walk cycle runs off the actual speed, so steps quicken with the pace.
     */
    fun walkFirstPerson(dt: Float, wishX: Float, wishY: Float, solids: List<Box>, faceYaw: Float) {
        stepped = false
        val tx = wishX * SPEED
        val ty = wishY * SPEED
        val wishing = wishX * wishX + wishY * wishY > 1e-4f
        // Speeding up is gentle; stopping, or turning back against the way you're going, is quick.
        val braking = !wishing || vx * tx + vy * ty < 0f
        val rate = SPEED / (if (braking) STOP_TIME else ACCEL_TIME)
        val dvx = tx - vx
        val dvy = ty - vy
        val dl = len(dvx, dvy)
        val maxDv = rate * dt
        if (dl <= maxDv) {
            vx = tx
            vy = ty
        } else {
            vx += dvx / dl * maxDv
            vy += dvy / dl * maxDv
        }
        val x0 = x
        val y0 = y
        val mx = vx * dt
        val my = vy * dt
        Body.move(solids, x, y, mx, my, out)
        val want = mx * mx + my * my
        if (want > 1e-8f) {
            // All but stopped by a face met nearly head-on (sliding along a wall met at an angle
            // is fine as it is): if its end is close, step round it.
            val gx = out[0] - x0
            val gy = out[1] - y0
            if (gx * gx + gy * gy < 0.09f * want && Body.slideRound(solids, out[0], out[1], mx, my, slid)) {
                out[0] = slid[0]
                out[1] = slid[1]
            }
        }
        x = out[0]
        y = out[1]
        if (dt > 0f) {
            // What actually happened is the new velocity (a wall takes the speed into it away),
            // but getting pushed clear of something isn't walking.
            val wanted = len(vx, vy)
            vx = (x - x0) / dt
            vy = (y - y0) / dt
            val got = len(vx, vy)
            if (got > wanted && got > 0f) {
                vx *= wanted / got
                vy *= wanted / got
            }
        }
        yaw = faceYaw
        val frac = speedFrac
        moving = frac * SPEED > STILL_SPEED
        if (moving) {
            val before = (phase / PI.toFloat()).toInt()
            phase += dt * (5f + 7f * frac)
            if ((phase / PI.toFloat()).toInt() != before) stepped = true
        }
        pose = if (moving) Pose.WALK else Pose.STAND
    }

    /** Nudges the kid (after something else moved them, like a kid bumping into them). */
    fun place(nx: Float, ny: Float) {
        x = nx
        y = ny
    }

    /** Stops dead (a view switch, a machine). */
    fun halt() {
        vx = 0f
        vy = 0f
        moving = false
        pose = Pose.STAND
    }
}
