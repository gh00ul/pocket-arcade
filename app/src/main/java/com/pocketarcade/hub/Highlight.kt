package com.pocketarcade.hub

import kotlin.math.abs
import kotlin.math.sin

/**
 * The glow that marks the thing whose ▶ PLAY prompt is up: a machine, the token kiosk or the
 * prize counter. Its level eases 0 → 1 while it is the [HubWorld.activeSpot] and back down when
 * the kid walks off, and the emissive parts of its model (marquee, screen, T-molding, coin slots)
 * breathe a little brighter meanwhile.
 *
 * Everything tweakable is a named constant. The boost only ever multiplies what already glows,
 * and the peak is kept small so a marquee that sits just under the bloom threshold doesn't
 * flare white; raise [EMISSIVE_BOOST] in small steps and look at the pale marquees.
 */
object Highlight {
    /** Seconds to fade in, or out again. */
    const val FADE_SECONDS = 0.25f
    /** Extra emissive (on top of 1) at the top of the pulse. */
    const val EMISSIVE_BOOST = 0.2f
    /** How much of [EMISSIVE_BOOST] the pulse gives back at its low point (0 = steady glow). */
    const val PULSE_DEPTH = 0.4f
    /** Pulse speed, radians per second (about one breath a second). */
    const val PULSE_RATE = 6f
    /** Extra alpha on the additive light pool on the floor in front of a highlighted cabinet. */
    const val POOL_ALPHA = 0.14f
    /** Extra emissive on the marquee bulbs that are between flashes. */
    const val BULB_BOOST = 0.4f
    /** Extra alpha on the halo of a bulb that is flashing. */
    const val BULB_HALO = 0.2f

    /** Smoothstep: starts and ends gently, so the fade doesn't jump. */
    fun ease(level: Float): Float {
        val x = level.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    /** Moves [level] a step of [dt] seconds towards fully on or fully off. */
    fun step(level: Float, on: Boolean, dt: Float): Float {
        val d = dt / FADE_SECONDS
        return if (on) minOf(1f, level + d) else maxOf(0f, level - d)
    }

    /** The emissive multiplier for a model at fade [level] and hall time [t]: exactly 1 when off. */
    fun boost(level: Float, t: Float): Float {
        val e = ease(level)
        if (e <= 0f) return 1f
        val swing = 0.5f + 0.5f * sin(t * PULSE_RATE)
        return 1f + EMISSIVE_BOOST * e * (1f - PULSE_DEPTH * (1f - swing))
    }

    /**
     * Whether [spot] is the one that [p] is for: a machine's own copy in its bank, the kiosk (its
     * token and ticket machines), the prize counter's desk. Null (nobody is at anything) is no.
     */
    fun isSpotOf(spot: Spot?, p: Prop): Boolean {
        if (spot == null) return false
        return when (p.kind) {
            PropKind.MACHINE -> spot.type == SpotType.MACHINE && spot.machine == p.machine &&
                abs(spot.area.centerX - p.centerX) < 1f && abs(spot.area.top - p.z1) < 1f
            PropKind.TOKENS, PropKind.CHANGE -> spot.type == SpotType.TOKENS
            PropKind.COUNTER -> spot.type == SpotType.PRIZES
            else -> false
        }
    }
}
