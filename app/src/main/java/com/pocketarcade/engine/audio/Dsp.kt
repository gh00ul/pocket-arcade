package com.pocketarcade.engine.audio

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Small signal-processing helpers shared by the audio code. Nothing here allocates. */
internal object Dsp {
    private const val SINE_SIZE = 2048
    private const val SINE_MASK = SINE_SIZE - 1

    /** One cycle of a sine plus a guard point, so an interpolated read at the very end is safe. */
    private val SINE = FloatArray(SINE_SIZE + 1) { sin(it * 2.0 * PI / SINE_SIZE).toFloat() }

    /** MIDI note number to Hz: 69 is A440. */
    private val NOTE_HZ = FloatArray(128) { (440.0 * 2.0.pow((it - 69) / 12.0)).toFloat() }

    fun noteHz(midi: Int): Float = NOTE_HZ[midi.coerceIn(0, 127)]

    /** A table-driven sine of [phase], a position in one cycle in 0..1 (wrapped if it strays). */
    fun sine(phase: Float): Float {
        val x = phase * SINE_SIZE
        val i = x.toInt()
        val f = x - i
        val k = i and SINE_MASK
        return SINE[k] + (SINE[k + 1] - SINE[k]) * f
    }

    /**
     * A smooth limiter: a Padé approximation of tanh that is transparent for small signals and
     * never leaves -1..1 (it reaches exactly 1 at an input of 3 and is clamped beyond).
     */
    fun softLimit(x: Float): Float {
        if (x >= 3f) return 1f
        if (x <= -3f) return -1f
        val x2 = x * x
        return x * (27f + x2) / (27f + 9f * x2)
    }

    /** Cubic smoothstep of [x] in 0..1. */
    fun smooth(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * A tiny constant added inside feedback loops so a decaying tail never reaches the
     * denormal range (where some CPUs slow down a great deal). Far below anything audible.
     */
    const val ANTI_DENORMAL = 1e-20f
}
