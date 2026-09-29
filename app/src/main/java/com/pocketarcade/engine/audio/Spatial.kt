package com.pocketarcade.engine.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where a sound lands in the stereo field: what it does to the left and right channels and how
 * much of it goes to the reverb. One instance is reused (the audio thread never allocates).
 */
class Placement {
    /** Gain into the left and right channels (equal-power pan, distance and rear shadow folded in). */
    var left = 1f
    var right = 1f

    /**
     * How much of the source's reverb send survives the distance, 0..1. It falls off much more
     * slowly than the dry gain, so far sounds arrive wetter than near ones, which is what the ear
     * reads as distance.
     */
    var send = 1f

    /** Straight-line distance to the listener, world units. */
    var distance = 0f

    /** Pan, -1 (hard left) to +1 (hard right). */
    var pan = 0f

    /** True when nothing of it would be heard, so the caller can skip the voice altogether. */
    val silent: Boolean get() = left <= SILENT && right <= SILENT

    private companion object {
        const val SILENT = 0.0005f
    }
}

/**
 * The maths of positional sound, as pure functions so it can be tested without a device.
 *
 * World coordinates are the hall's: x runs across the hall, z from the back wall towards the
 * entrance. A listener's yaw is [com.pocketarcade.hub.HubCamera.yaw]'s: 0 faces +z, and the
 * forward direction is (sin yaw, cos yaw), so the right-hand side is (-cos yaw, sin yaw).
 * (Overhead the camera looks at the back wall, which is yaw pi: screen right is +x.)
 */
object Spatial {
    /** Inside this distance a source is at full level (about a cabinet's width). */
    const val REF_DISTANCE = 40f

    /** Past this distance a source is silent. The hall is 608 x 1100, so this is "about a third of it". */
    const val MAX_DISTANCE = 420f

    /**
     * Sources closer than this pan less and less towards the side, so a kid walking past at your
     * feet doesn't throw the sound from ear to ear.
     */
    const val NEAR_FIELD = 30f

    /** A source dead behind the listener is this much quieter than one dead ahead (the head's shadow). */
    const val REAR_SHADOW = 0.3f

    /**
     * [place] scales the equal-power gains by this (the square root of two) so a sound dead ahead
     * and close plays exactly as loud in each channel as an unpanned one does ([panGains] alone
     * would put it 3 dB down in each).
     */
    const val CENTRE_MAKEUP = 1.4142135f

    /**
     * Level at [distance]: 1 out to [REF_DISTANCE], then falling smoothly (a quadratic ease) to
     * exactly 0 at [MAX_DISTANCE], so a far machine fades out instead of switching off.
     * Monotone non-increasing.
     */
    fun attenuation(distance: Float): Float {
        val t = fade(distance)
        return (1f - t) * (1f - t)
    }

    /** How far through the audible range [distance] is: 0 at [REF_DISTANCE] or closer, 1 at [MAX_DISTANCE] or further. */
    fun fade(distance: Float): Float =
        ((distance - REF_DISTANCE) / (MAX_DISTANCE - REF_DISTANCE)).coerceIn(0f, 1f)

    /**
     * The equal-power pan law: the left and right gains for [pan] (-1..1) are cos and sin of an
     * angle, so left² + right² is 1 wherever the sound sits and its loudness doesn't dip when
     * it crosses the middle. Writes them into [out] at [Placement.left] and [Placement.right]
     * (without [CENTRE_MAKEUP] or any distance: those are [place]'s).
     */
    fun panGains(pan: Float, out: Placement) {
        val a = (pan.coerceIn(-1f, 1f) + 1f) * (PI.toFloat() / 4f)
        out.left = cos(a)
        out.right = sin(a)
        out.pan = pan.coerceIn(-1f, 1f)
    }

    /**
     * Places a source at ([x], [z]) for a listener at ([listenerX], [listenerZ]) looking along
     * [yaw]: the pan from its bearing, the level from its distance and how far behind you it is,
     * and the reverb share. The result goes into [out].
     */
    fun place(listenerX: Float, listenerZ: Float, yaw: Float, x: Float, z: Float, out: Placement) {
        val dx = x - listenerX
        val dz = z - listenerZ
        val dist = sqrt(dx * dx + dz * dz)
        out.distance = dist
        val att = attenuation(dist)
        val t = fade(dist)
        out.send = 1f - t
        val k = att * CENTRE_MAKEUP
        if (dist < 1e-3f) {
            panGains(0f, out)
            out.left *= k
            out.right *= k
            return
        }
        val sy = sin(yaw)
        val cy = cos(yaw)
        val ahead = (dx * sy + dz * cy) / dist
        val side = (dx * -cy + dz * sy) / dist
        // Easing the pan in over the near field: smoothstep of distance.
        val n = (dist / NEAR_FIELD).coerceIn(0f, 1f)
        val pan = side * (n * n * (3f - 2f * n))
        panGains(pan, out)
        val rear = if (ahead < 0f) -ahead else 0f
        val g = k * (1f - REAR_SHADOW * rear)
        out.left *= g
        out.right *= g
    }
}
