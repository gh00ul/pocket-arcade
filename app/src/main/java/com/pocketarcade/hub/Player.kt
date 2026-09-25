package com.pocketarcade.hub

import androidx.compose.ui.graphics.ImageBitmap
import com.pocketarcade.engine.len
import kotlin.math.abs

/** The player's kid: analog movement with wall sliding and a 4-direction walk cycle. */
class Player {
    companion object {
        const val SPEED = 74f
        private val CYCLE = intArrayOf(0, 1, 0, 2)
    }

    var x = 0f
    var y = 0f
    var vx = 0f
        private set
    var vy = 0f
        private set
    var dir = CharacterArt.UP
    var frame = 0
        private set
    var moving = false
        private set
    /** True for the one update in which a foot touched down (for footstep sounds). */
    var stepped = false
        private set

    var look: CharacterLook? = null
        private set
    var frames: Array<Array<ImageBitmap>> = emptyArray()
        private set

    private var animT = 0f
    private var lastCycle = 0
    private val out = FloatArray(2)

    fun setLook(newLook: CharacterLook) {
        if (newLook == look) return
        look = newLook
        frames = CharacterArt.frames(newLook)
    }

    fun update(dt: Float, inputX: Float, inputY: Float, solids: List<Box>) {
        stepped = false
        val mag = len(inputX, inputY).coerceAtMost(1f)
        if (mag > 0.01f) {
            vx = inputX * SPEED
            vy = inputY * SPEED
            dir = if (abs(inputX) > abs(inputY) * 1.1f) {
                if (inputX < 0f) CharacterArt.LEFT else CharacterArt.RIGHT
            } else {
                if (inputY < 0f) CharacterArt.UP else CharacterArt.DOWN
            }
            val moved = Collision.move(solids, x, y, vx * dt, vy * dt, out)
            vx = (out[0] - x) / dt
            vy = (out[1] - y) / dt
            x = out[0]
            y = out[1]
            moving = moved
            if (moved) animT += dt * (5f + 5f * mag) else animT = 0f
        } else {
            vx = 0f
            vy = 0f
            moving = false
            animT = 0f
        }
        val cycle = animT.toInt() % 4
        frame = CYCLE[cycle]
        if (cycle != lastCycle && (cycle == 1 || cycle == 3)) stepped = true
        lastCycle = cycle
    }
}
