package com.pocketarcade.engine

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.sin

/**
 * Trauma-based screen shake: [add] trauma (0..1), offsets grow with trauma² and decay smoothly.
 */
class ScreenShake(private val maxOffset: Float = 14f, private val decayPerSec: Float = 1.8f) {
    var trauma = 0f
        private set
    private var time = 0f
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set

    fun add(amount: Float) {
        trauma = (trauma + amount).coerceAtMost(1f)
    }

    fun reset() {
        trauma = 0f; offsetX = 0f; offsetY = 0f
    }

    fun update(dt: Float) {
        time += dt
        trauma = (trauma - decayPerSec * dt).coerceAtLeast(0f)
        val k = trauma * trauma * maxOffset
        offsetX = k * (sin(time * 71f) * 0.6f + sin(time * 113f + 1.3f) * 0.4f)
        offsetY = k * (sin(time * 83f + 2.1f) * 0.6f + sin(time * 127f + 0.4f) * 0.4f)
    }
}

/**
 * Damped spring around 1.0 used for squash and stretch: [kick] it and read [value] as a scale.
 */
class Spring(
    private val stiffness: Float = 380f,
    private val damping: Float = 16f,
    var target: Float = 1f,
) {
    var value = target
    private var velocity = 0f

    fun kick(impulse: Float) {
        velocity += impulse
    }

    fun snap(v: Float) {
        value = v; velocity = 0f
    }

    fun update(dt: Float) {
        val force = (target - value) * stiffness - velocity * damping
        velocity += force * dt
        value += velocity * dt
    }
}

/** Short-lived popup texts ("+50", "SWISH!") that pop in, float upward and fade. */
class FloatingTexts(capacity: Int = 24) {
    private class Item {
        var text = ""
        var x = 0f
        var y = 0f
        var vy = 0f
        var life = 0f
        var maxLife = 1f
        var color = Color.White
        var size = 2f
        var active = false
    }

    private val items = Array(capacity) { Item() }
    private var next = 0

    fun add(text: String, x: Float, y: Float, color: Color, size: Float = 2f, life: Float = 0.9f, rise: Float = 60f) {
        val it = items[next]
        next = (next + 1) % items.size
        it.text = text; it.x = x; it.y = y; it.vy = -rise
        it.life = life; it.maxLife = life; it.color = color; it.size = size; it.active = true
    }

    fun clear() {
        items.forEach { it.active = false }
    }

    fun update(dt: Float) {
        for (it in items) {
            if (!it.active) continue
            it.life -= dt
            if (it.life <= 0f) {
                it.active = false; continue
            }
            it.y += it.vy * dt
            it.vy *= (1f - 2.5f * dt).coerceAtLeast(0f)
        }
    }

    /** Draws in a space where one unit is [unit] screen pixels, offset by the origin. */
    fun draw(scope: DrawScope, originX: Float, originY: Float, unit: Float) {
        for (it in items) {
            if (!it.active) continue
            val age = 1f - it.life / it.maxLife
            val pop = if (age < 0.15f) easeOutBack(age / 0.15f) else 1f
            val alpha = if (it.life < 0.3f) it.life / 0.3f else 1f
            val scale = it.size * unit * (0.6f + 0.4f * pop)
            PixelFont.drawCentered(scope, it.text, originX + it.x * unit, originY + it.y * unit, scale, it.color, alpha)
        }
    }
}

/** A value that jumps to 1 and decays to 0; used for white flashes and hit highlights. */
class Flash(private val decayPerSec: Float = 4f) {
    var value = 0f
        private set

    fun trigger(amount: Float = 1f) {
        value = maxOf(value, amount)
    }

    fun update(dt: Float) {
        value = (value - decayPerSec * dt).coerceAtLeast(0f)
    }
}
