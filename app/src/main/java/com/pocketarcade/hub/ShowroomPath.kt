package com.pocketarcade.hub

import kotlin.math.floor

/**
 * Where the title's camera is: its eye, the point it looks at, and a multiplier on the showcase's
 * base field of view. Mutable and reused, so posing the camera every frame allocates nothing.
 */
class CameraPose {
    var ex = 0f
    var ey = 0f
    var ez = 0f
    var tx = 0f
    var ty = 0f
    var tz = 0f
    var fovScale = 1f

    fun set(other: CameraPose) {
        ex = other.ex; ey = other.ey; ez = other.ez
        tx = other.tx; ty = other.ty; tz = other.tz
        fovScale = other.fovScale
    }
}

/** Spline maths for scripted camera moves. */
object Spline {
    /**
     * Uniform Catmull-Rom between [p1] and [p2] at [t] (0..1), with [p0] and [p3] the neighbours
     * either side. It passes through every control point and has a continuous slope across them,
     * so a run of segments glides without a corner.
     */
    fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (
            2f * p1 + (p2 - p0) * t +
                (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 +
                (3f * p1 - p0 - 3f * p2 + p3) * t3
            )
    }
}

/**
 * The title showroom's scripted camera: a closed loop through a ring of keyframes (eye, look-at
 * target, field of view) joined by a Catmull-Rom spline, one keyframe every [segmentSeconds]. It
 * never stops or reverses, so the title can idle on it for as long as the player likes, and the
 * loop's end meets its start without a seam.
 *
 * Keyframes are seven floats each: eye x y z, target x y z, field-of-view scale.
 */
class ShowroomPath(private val keys: FloatArray, val segmentSeconds: Float) {
    init {
        require(keys.size % STRIDE == 0 && keys.size / STRIDE >= 4) { "a loop needs at least four keyframes of $STRIDE floats" }
        require(segmentSeconds > 0f) { "segments take time" }
    }

    /** How many keyframes make the loop. */
    val count = keys.size / STRIDE

    /** Seconds for one whole trip round the loop. */
    val loopSeconds = count * segmentSeconds

    private fun key(i: Int, channel: Int): Float = keys[((i % count + count) % count) * STRIDE + channel]

    private fun channel(i: Int, f: Float, c: Int): Float =
        Spline.catmullRom(key(i - 1, c), key(i, c), key(i + 1, c), key(i + 2, c), f)

    /** Writes the camera's pose [t] seconds into the (endlessly repeating) loop into [out]. */
    fun pose(t: Float, out: CameraPose) {
        var u = (t / segmentSeconds) % count
        if (u < 0f) u += count
        val i = floor(u).toInt().coerceIn(0, count - 1)
        val f = u - i
        out.ex = channel(i, f, 0)
        out.ey = channel(i, f, 1)
        out.ez = channel(i, f, 2)
        out.tx = channel(i, f, 3)
        out.ty = channel(i, f, 4)
        out.tz = channel(i, f, 5)
        out.fovScale = channel(i, f, 6)
    }

    companion object {
        const val STRIDE = 7

        /** Seconds between keyframes: slow enough that a machine takes several seconds to cross the screen. */
        const val SEGMENT_SECONDS = 7.5f

        /** Where in the loop the title begins, in segments: a well-composed shot near the middle of the row. */
        const val START_SEGMENTS = 1.3f

        /**
         * The camera route for a row whose machines span x = ±[rowHalf]. Keys are written as a share of
         * that half-width, so the same shots fit any number of machines. Low and oblique along the
         * row's front (the machines' fronts are at z = 20), then a crane back and up over it and round
         * to the start again. Every eye stays 95 or more in front of the fronts, above the floor and
         * clear of the cabinets, and the target never leaves the row.
         */
        fun forRow(rowHalf: Float): ShowroomPath {
            val k = floatArrayOf(
                // eye x, y, z             target x, y, z            fov
                -0.42f, 50f, 128f, -0.22f, 40f, 20f, 1.00f,
                -0.05f, 54f, 132f, 0.15f, 40f, 18f, 1.00f,
                0.32f, 48f, 118f, 0.55f, 40f, 20f, 0.96f,
                0.72f, 58f, 150f, 0.86f, 42f, 20f, 1.00f,
                0.95f, 76f, 205f, 0.62f, 46f, 16f, 1.05f,
                0.45f, 100f, 250f, 0.15f, 48f, 10f, 1.05f,
                -0.10f, 88f, 230f, -0.30f, 44f, 14f, 1.02f,
                -0.62f, 64f, 175f, -0.55f, 42f, 20f, 1.00f,
            )
            for (i in k.indices step STRIDE) {
                k[i] *= rowHalf
                k[i + 3] *= rowHalf
            }
            return ShowroomPath(k, SEGMENT_SECONDS)
        }
    }
}

/**
 * The title's exit push: as the player taps, the camera dollies toward the row of machines while its
 * lens narrows, accelerating into the dissolve that hands over to the hall. It stops well short of
 * the cabinets, so the eye never passes through one.
 */
object TitlePush {
    /** How much of the eye-to-target distance the eye covers by the end of the push. */
    const val EYE_FRACTION = 0.42f

    /** The field of view's multiplier at the end of the push. */
    const val FOV_END = 0.70f

    /** How far the push has got (0..1) for exit progress [exit]: slow to start, quick to finish. */
    fun ease(exit: Float): Float {
        val x = exit.coerceIn(0f, 1f)
        return x * x * (1.6f - 0.6f * x)
    }

    /** Moves [pose] along the push for exit progress [exit] (0 leaves it as it is). */
    fun apply(pose: CameraPose, exit: Float) {
        val k = ease(exit)
        if (k <= 0f) return
        val f = EYE_FRACTION * k
        pose.ex += (pose.tx - pose.ex) * f
        pose.ey += (pose.ty - pose.ey) * f
        pose.ez += (pose.tz - pose.ez) * f
        pose.fovScale *= 1f - (1f - FOV_END) * k
    }
}
