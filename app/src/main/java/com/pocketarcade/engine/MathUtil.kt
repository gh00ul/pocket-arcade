package com.pocketarcade.engine

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

const val TAU = (PI * 2).toFloat()

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)

/** Moves [current] toward [target] by at most [maxDelta]. */
fun approach(current: Float, target: Float, maxDelta: Float): Float = when {
    current < target -> minOf(current + maxDelta, target)
    current > target -> maxOf(current - maxDelta, target)
    else -> current
}

/** Frame-rate independent exponential smoothing toward [target]. */
fun damp(current: Float, target: Float, rate: Float, dt: Float): Float =
    lerp(current, target, 1f - exp(-rate * dt))

fun easeOutCubic(t: Float): Float {
    val x = 1f - clamp01(t)
    return 1f - x * x * x
}

fun easeOutBack(t: Float): Float {
    val x = clamp01(t)
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}

fun len(x: Float, y: Float): Float = sqrt(x * x + y * y)

fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float = len(x2 - x1, y2 - y1)

fun Random.range(min: Float, max: Float): Float = min + nextFloat() * (max - min)

fun Random.chance(p: Float): Boolean = nextFloat() < p

/** Small mutable 2D vector used by physics and gameplay code to avoid allocations. */
class Vec2(var x: Float = 0f, var y: Float = 0f) {
    fun set(nx: Float, ny: Float): Vec2 {
        x = nx; y = ny; return this
    }
}

/** Hash-based value noise in 0..1 for deterministic procedural detail. */
fun hash01(x: Int, y: Int, seed: Int = 0): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1274126177
    h = (h xor (h ushr 13)) * 1103515245
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 65535f
}
