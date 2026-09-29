package com.pocketarcade.ui

import com.pocketarcade.data.Catalog
import com.pocketarcade.data.SaveState
import com.pocketarcade.engine.TAU
import com.pocketarcade.engine.clamp01
import com.pocketarcade.engine.hash01
import kotlin.math.floor
import kotlin.math.sin

/** 0 below [a], 1 above [b], and an S-curve between. */
internal fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = clamp01((x - a) / (b - a))
    return t * t * (3f - 2f * t)
}

/**
 * The title screen's clock: when each part of the sign lights, how the tap prompt pulses, and how
 * everything leaves when the player taps. Pure functions of time, so they can be tested without a
 * screen; [calm] (the reduce-motion setting) swaps every flicker, sweep and bob for a slow fade.
 */
object TitleTimeline {
    /** Seconds after the title appears that POCKET, then ARCADE, begin to light. */
    const val POCKET_START = 0.5f
    const val ARCADE_START = 1.35f

    /** Each letter lights this much after the one before it, and takes [IGNITE_SECONDS] to settle. */
    const val LETTER_STAGGER = 0.09f
    const val IGNITE_SECONDS = 0.75f

    /** How bright an unlit neon tube looks: a faint ghost of the letter, never nothing. */
    const val UNLIT = 0.10f

    /** How many times a second an igniting tube can change its mind. */
    private const val FLICKER_RATE = 22f

    /** Under reduce motion the sign fades in together over this long. */
    const val CALM_FADE_SECONDS = 1.2f

    /**
     * How lit letter [index] of a word that starts at [wordStart] is at time [t]: a ghost, then a
     * few stuttering flashes as the tube catches, then steady at 1 for good. Calm is a smooth fade.
     */
    fun letterOn(t: Float, wordStart: Float, index: Int, calm: Boolean): Float {
        if (calm) return UNLIT + (1f - UNLIT) * smoothstep(0f, CALM_FADE_SECONDS, t - wordStart)
        val local = t - wordStart - index * LETTER_STAGGER
        if (local <= 0f) return UNLIT
        if (local >= IGNITE_SECONDS) return 1f
        val p = local / IGNITE_SECONDS
        val ramp = UNLIT + (1f - UNLIT) * p * p
        // A hash of which flicker slot we are in decides on or off; the stutter dies away as p -> 1.
        val slot = floor(local * FLICKER_RATE).toInt()
        val flash = if (hash01(index, slot, 7) > 0.42f) 1f else 0.18f
        val settle = p * p * p
        return ramp * (flash + (1f - flash) * settle)
    }

    /** Seconds after the title appears before the tagline starts to fade in, and how long it takes. */
    const val TAGLINE_START = ARCADE_START + 1.0f
    const val TAGLINE_FADE = 0.9f

    fun taglineAlpha(t: Float): Float = smoothstep(TAGLINE_START, TAGLINE_START + TAGLINE_FADE, t)

    /** The glow behind the sign breathes: its strength multiplier over time (a gentle 1.0 down to about 0.88). */
    const val BREATH_PERIOD = 4.6f
    const val BREATH_DEPTH = 0.12f

    fun breath(t: Float, calm: Boolean): Float {
        val depth = if (calm) BREATH_DEPTH * 0.5f else BREATH_DEPTH
        return 1f - depth * (0.5f + 0.5f * sin(TAU * t / BREATH_PERIOD))
    }

    /** A light sweep crosses the sign for [SWEEP_SECONDS] every [SWEEP_PERIOD] seconds, from [SWEEP_START]. */
    const val SWEEP_START = 3.6f
    const val SWEEP_PERIOD = 6.5f
    const val SWEEP_SECONDS = 1.15f

    /** Where the sweep's centre is across the sign (0 = its left edge, 1 = its right), or -1 when there is none. */
    fun sweep(t: Float, calm: Boolean): Float {
        if (calm || t < SWEEP_START) return -1f
        val local = (t - SWEEP_START) % SWEEP_PERIOD
        if (local > SWEEP_SECONDS) return -1f
        return smoothstep(0f, 1f, local / SWEEP_SECONDS)
    }

    /** Once in a while one tube of the settled sign buzzes: every [BUZZ_PERIOD] seconds, for [BUZZ_SECONDS]. */
    const val BUZZ_PERIOD = 9f
    const val BUZZ_SECONDS = 0.14f

    /** The multiplier (1 normally, a dip while buzzing) for letter [index] of a [count]-letter word. */
    fun buzz(t: Float, index: Int, count: Int, word: Int, calm: Boolean): Float {
        if (calm || t < SWEEP_START || count <= 0) return 1f
        val cycle = floor((t + word * 3.1f) / BUZZ_PERIOD).toInt()
        val local = (t + word * 3.1f) - cycle * BUZZ_PERIOD
        if (local > BUZZ_SECONDS) return 1f
        val victim = (hash01(cycle, word, 11) * count).toInt().coerceIn(0, count - 1)
        return if (index == victim) 0.55f else 1f
    }

    /** TAP TO START waits for the sign, fades in over [PROMPT_FADE], then pulses. */
    const val PROMPT_START = 2.8f
    const val PROMPT_FADE = 0.8f
    const val PULSE_HZ = 0.75f

    /** How opaque the prompt is at [t]. */
    fun promptAlpha(t: Float, calm: Boolean): Float {
        val fade = smoothstep(PROMPT_START, PROMPT_START + PROMPT_FADE, t)
        if (fade <= 0f) return 0f
        val pulse = 0.5f + 0.5f * sin(TAU * t * (if (calm) PULSE_HZ * 0.6f else PULSE_HZ))
        val depth = if (calm) 0.25f else 0.42f
        return fade * (1f - depth + depth * pulse)
    }

    /** How much bigger than normal the prompt is at [t] (1 when calm): it swells with each pulse. */
    fun promptScale(t: Float, calm: Boolean): Float {
        if (calm || t < PROMPT_START) return 1f
        return 1f + 0.04f * (0.5f + 0.5f * sin(TAU * t * PULSE_HZ))
    }

    // ---------------------------------------------------------------- leaving

    /** The sign's opacity as the exit progresses (0..1): gone by 60%. */
    fun logoExitAlpha(exit: Float): Float = 1f - smoothstep(0.05f, 0.6f, exit)

    /** How far the sign has risen and grown by the exit's [exit] progress, as a share of the screen height / a scale. */
    fun logoExitLift(exit: Float): Float = -0.05f * smoothstep(0f, 1f, exit)

    fun logoExitScale(exit: Float): Float = 1f + 0.10f * smoothstep(0f, 1f, exit)

    /** The small print (prompt, save line, version) is gone by a third of the way. */
    fun uiExitAlpha(exit: Float): Float = 1f - smoothstep(0f, 0.32f, exit)
}

/**
 * The dust motes hanging in the showroom's air. Each has a fixed depth, so the nearer ones drift
 * across the screen further as the camera moves (parallax) and rise a little faster; on the way out
 * they stream outward past the lens. All positions are shares of the screen (0..1) computed from
 * the mote's index alone: nothing is stored, nothing is allocated.
 */
object DustField {
    const val COUNT = 46

    /** Depth 0.3 (far) … 1 (near). */
    fun depth(i: Int): Float = 0.3f + 0.7f * hash01(i, 1, 21)

    /** How many screen widths a near mote slides per world unit the camera moves along the row. */
    private const val PARALLAX = 0.0011f

    /** Screen-height shares per second a near mote rises. */
    private const val RISE = 0.030f

    private fun wrap01(v: Float): Float = v - floor(v)

    /** Horizontal position (0..1) at time [t] for a camera eye at world x [camX]; a calm field never moves. */
    fun x(i: Int, t: Float, camX: Float, calm: Boolean): Float {
        val d = depth(i)
        if (calm) return hash01(i, 2, 21)
        val sway = sin(t * 0.31f + i) * 0.012f * d
        return wrap01(hash01(i, 2, 21) + sway - camX * PARALLAX * d)
    }

    /** Vertical position (0..1): rising slowly, wrapping from the top back to the bottom. */
    fun y(i: Int, t: Float, calm: Boolean): Float {
        val d = depth(i)
        if (calm) return hash01(i, 3, 21)
        return wrap01(hash01(i, 3, 21) - t * RISE * d)
    }

    /** Peak opacity: brighter the nearer, and a slow twinkle; a calm field is a little fainter and still. */
    fun alpha(i: Int, t: Float, calm: Boolean): Float {
        val d = depth(i)
        val base = 0.10f + 0.26f * d
        if (calm) return base * 0.6f
        return base * (0.65f + 0.35f * sin(t * (0.6f + hash01(i, 4, 21)) + i * 1.7f))
    }

    /** Radius in units of the screen width: nearer motes are bigger. */
    fun radius(i: Int): Float = (0.0009f + 0.0026f * depth(i)) * (0.7f + 0.6f * hash01(i, 5, 21))

    /**
     * Pushes a position outward from the screen centre as the exit progresses, nearer motes faster,
     * so leaving the title feels like flying through the dust. [v] is a 0..1 position, [exit] 0..1.
     */
    fun stream(v: Float, i: Int, exit: Float): Float {
        val e = clamp01(exit)
        return 0.5f + (v - 0.5f) * (1f + 2.6f * e * e * depth(i))
    }
}

/** The small welcome line under the title's save summary: a word about where the player left off. */
object TitleCopy {
    /**
     * A short line for [save] (empty until it has loaded): a greeting for a brand-new player,
     * otherwise a welcome back with the most useful thing to say, in order: tickets that can buy
     * something, how many plushies are found, or just the greeting.
     */
    fun welcome(save: SaveState): String {
        if (!save.loaded) return ""
        if (save.totalPlays == 0 && save.tickets == 0) return "WELCOME, PLAYER ONE!"
        val canBuy = Catalog.all.any { it.price in 1..save.tickets && !save.owns(it.id) }
        if (canBuy) return "WELCOME BACK!  ${save.tickets} TICKETS TO SPEND"
        val found = Catalog.plushies.count { (save.collection[it.id] ?: 0) > 0 }
        val total = Catalog.plushies.size
        if (found >= total) return "WELCOME BACK!  EVERY PLUSH FOUND"
        if (found > 0) return "WELCOME BACK!  $found OF $total PLUSHIES FOUND"
        return "WELCOME BACK!"
    }
}
