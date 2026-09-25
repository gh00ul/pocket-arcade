package com.pocketarcade.hub

import com.pocketarcade.engine.len
import kotlin.math.PI
import kotlin.math.atan2

/** The player's kid: analog movement with wall sliding, smooth turning and a walk cycle. */
class Player {
    companion object {
        const val SPEED = 78f
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

    fun setLook(newLook: CharacterLook) {
        look = newLook
    }

    fun update(dt: Float, inputX: Float, inputY: Float, solids: List<Box>) {
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
            // Turn smoothly towards where the stick points.
            val target = atan2(inputX, inputY)
            var d = (target - yaw) % (2f * PI.toFloat())
            if (d > PI) d -= 2f * PI.toFloat()
            if (d < -PI) d += 2f * PI.toFloat()
            yaw += d.coerceIn(-dt * 12f, dt * 12f)
            if (moved) {
                val before = (phase / PI.toFloat()).toInt()
                phase += dt * (6f + 6f * mag)
                if ((phase / PI.toFloat()).toInt() != before) stepped = true
            }
        } else {
            vx = 0f
            vy = 0f
            moving = false
        }
        pose = if (moving) Pose.WALK else Pose.STAND
    }
}
