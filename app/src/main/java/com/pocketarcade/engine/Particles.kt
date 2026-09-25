package com.pocketarcade.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Allocation-free particle pool stored as parallel arrays. Particles are square "pixels",
 * confetti strips that flip as they fall, or sparkles that twinkle.
 */
class Particles(private val capacity: Int = 600) {
    companion object {
        const val SQUARE = 0
        const val CONFETTI = 1
        const val SPARKLE = 2
    }

    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val life = FloatArray(capacity)
    private val maxLife = FloatArray(capacity)
    private val size = FloatArray(capacity)
    private val gravity = FloatArray(capacity)
    private val drag = FloatArray(capacity)
    private val phase = FloatArray(capacity)
    private val color = IntArray(capacity)
    private val shape = IntArray(capacity)
    var count = 0
        private set

    private val rng = Random(1234)

    fun clear() {
        count = 0
    }

    fun spawn(
        px: Float, py: Float, pvx: Float, pvy: Float,
        lifetime: Float, sz: Float, argb: Int,
        grav: Float = 0f, dragPerSec: Float = 0f, kind: Int = SQUARE,
    ) {
        val i = if (count < capacity) count++ else rng.nextInt(capacity)
        x[i] = px; y[i] = py; vx[i] = pvx; vy[i] = pvy
        life[i] = lifetime; maxLife[i] = lifetime; size[i] = sz
        color[i] = argb; gravity[i] = grav; drag[i] = dragPerSec
        shape[i] = kind; phase[i] = rng.nextFloat() * TAU
    }

    /** Radial burst of [n] particles. */
    fun burst(
        px: Float, py: Float, n: Int,
        speedMin: Float, speedMax: Float,
        colors: IntArray,
        lifetime: Float = 0.6f, sz: Float = 3f,
        grav: Float = 0f, dragPerSec: Float = 2f, kind: Int = SQUARE,
        angleFrom: Float = 0f, angleTo: Float = TAU,
    ) {
        repeat(n) {
            val a = rng.range(angleFrom, angleTo)
            val sp = rng.range(speedMin, speedMax)
            spawn(
                px, py, cos(a) * sp, sin(a) * sp,
                lifetime * rng.range(0.6f, 1.2f), sz * rng.range(0.7f, 1.3f),
                colors[rng.nextInt(colors.size)], grav, dragPerSec, kind,
            )
        }
    }

    /** Confetti shower falling from the top of an area [width] wide. */
    fun confetti(left: Float, top: Float, width: Float, n: Int, sz: Float = 4f) {
        val colors = intArrayOf(Pal.PINK, Pal.YELLOW, Pal.CYAN, Pal.LIME, Pal.ORANGE, Pal.PURPLE, Pal.WHITE)
        repeat(n) {
            spawn(
                left + rng.nextFloat() * width, top - rng.nextFloat() * 60f,
                rng.range(-60f, 60f), rng.range(40f, 180f),
                rng.range(1.6f, 3.2f), sz * rng.range(0.8f, 1.4f),
                colors[rng.nextInt(colors.size)], 220f, 1.4f, CONFETTI,
            )
        }
    }

    fun update(dt: Float) {
        var i = 0
        while (i < count) {
            life[i] -= dt
            if (life[i] <= 0f) {
                val last = count - 1
                if (i != last) {
                    x[i] = x[last]; y[i] = y[last]; vx[i] = vx[last]; vy[i] = vy[last]
                    life[i] = life[last]; maxLife[i] = maxLife[last]; size[i] = size[last]
                    gravity[i] = gravity[last]; drag[i] = drag[last]; phase[i] = phase[last]
                    color[i] = color[last]; shape[i] = shape[last]
                }
                count--
                continue
            }
            val k = 1f - (drag[i] * dt).coerceAtMost(1f)
            vx[i] *= k
            vy[i] = vy[i] * k + gravity[i] * dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            phase[i] += dt * 9f
            i++
        }
    }

    /** Draws with world→screen transform: screen = origin + world * scale. */
    fun draw(scope: DrawScope, originX: Float = 0f, originY: Float = 0f, scale: Float = 1f) {
        for (i in 0 until count) {
            val t = life[i] / maxLife[i]
            val alpha = if (t < 0.3f) t / 0.3f else 1f
            val c = Color(color[i])
            val sx = originX + x[i] * scale
            val sy = originY + y[i] * scale
            when (shape[i]) {
                CONFETTI -> {
                    val w = size[i] * scale * (0.25f + 0.75f * abs(sin(phase[i])))
                    val h = size[i] * scale * 0.6f
                    scope.drawRect(c, Offset(sx - w / 2f, sy - h / 2f), Size(w, h), alpha)
                }
                SPARKLE -> {
                    val s = size[i] * scale * (0.5f + 0.5f * abs(sin(phase[i])))
                    val thin = (s * 0.34f).coerceAtLeast(1f)
                    scope.drawRect(c, Offset(sx - s, sy - thin / 2f), Size(s * 2f, thin), alpha)
                    scope.drawRect(c, Offset(sx - thin / 2f, sy - s), Size(thin, s * 2f), alpha)
                }
                else -> {
                    val s = size[i] * scale * (0.4f + 0.6f * t)
                    scope.drawRect(c, Offset(sx - s / 2f, sy - s / 2f), Size(s, s), alpha)
                }
            }
        }
    }
}
