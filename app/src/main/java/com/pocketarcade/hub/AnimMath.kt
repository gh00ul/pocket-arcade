package com.pocketarcade.hub

import com.pocketarcade.engine.hash01
import kotlin.math.PI
import kotlin.math.exp

/**
 * Small pure helpers for character animation: angles, easing, a damped spring and seeded
 * "randomness". Everything here is allocation-free so it can run every simulation step.
 */
object AnimMath {
    const val TAU = (PI * 2).toFloat()
    private const val PI_F = PI.toFloat()

    /** [a] wrapped into (-π, π]. */
    fun wrap(a: Float): Float {
        var d = a % TAU
        if (d > PI_F) d -= TAU
        if (d < -PI_F) d += TAU
        return d
    }

    /** 0 at or below 0, 1 at or above 1, and smooth (zero slope at both ends) between. */
    fun smooth(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /** A window that is 0 at [t] = 0 and at [t] = 1 and rises smoothly to 1 in between (for one-off gestures). */
    fun bell(t: Float): Float {
        if (t <= 0f || t >= 1f) return 0f
        val s = kotlin.math.sin(t * PI_F)
        return s * s
    }

    /** Frame-rate independent share of the way to a target covered in [dt] at [rate] per second. */
    fun k(rate: Float, dt: Float): Float = 1f - exp(-rate * dt)

    /** A repeatable number in [0, 1) from a figure's [seed], a running [n] and a [salt] for what it's for. */
    fun unit(seed: Int, n: Int, salt: Int): Float = hash01(seed * 31 + n, salt, seed xor 0x5bd1e995).coerceAtMost(0.9999f)
}

/**
 * A damped spring following a target: [x] is the value and [v] its speed. Integrated with small
 * semi-implicit Euler steps, so it stays stable at any frame time and a long hitch just runs a
 * few more sub-steps. Under-damped springs overshoot and settle, which is what gives hats,
 * hair and swinging arms their follow-through.
 */
class Spring(var x: Float = 0f) {
    var v = 0f

    /** Runs the spring for [dt] toward [target] with natural frequency [omega] (rad/s) and damping ratio [zeta]. */
    fun step(target: Float, dt: Float, omega: Float, zeta: Float) = drive(target, 0f, dt, omega, zeta)

    /**
     * As [step], with an extra acceleration [push] on the spring (a body accelerating under a
     * hanging hat pushes it the other way): the spring pulls back to [target] while it is pushed.
     */
    fun drive(target: Float, push: Float, dt: Float, omega: Float, zeta: Float) {
        var left = dt.coerceIn(0f, MAX_STEP)
        val o2 = omega * omega
        val c = 2f * zeta * omega
        while (left > 0f) {
            val h = if (left > SUB) SUB else left
            v += (o2 * (target - x) - c * v + push) * h
            x += v * h
            left -= h
        }
        // A spring that has come to rest stays put (and never carries a denormal around).
        if (x > -1e-5f && x < 1e-5f && v > -1e-4f && v < 1e-4f && target == 0f && push == 0f) {
            x = 0f
            v = 0f
        }
    }

    fun reset(value: Float = 0f) {
        x = value
        v = 0f
    }

    private companion object {
        /** The longest stretch integrated in one call (a stalled frame is not worth simulating in full). */
        const val MAX_STEP = 0.1f
        /** Sub-step: 1/90 s keeps ω·h under 0.35 for the stiffest spring used here. */
        const val SUB = 1f / 90f
    }
}
