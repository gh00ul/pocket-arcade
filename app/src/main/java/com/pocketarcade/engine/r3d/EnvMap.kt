package com.pocketarcade.engine.r3d

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The room glossy things reflect: a small cube map painted once in code. It is a generic dark
 * arcade — a black ceiling with warm light panels and neon strips, walls with neon bands and
 * cool light boxes, bright doors and windows toward the entrance (+z) and a dark carpet dotted
 * with coloured pools of cabinet light — so chrome, glass, balls and glossy paint pick up
 * believable highlights from any angle, in the hall and in every game.
 *
 * Pure math, no Android: the GL thread uploads [face] images, and tests check the mapping.
 * Radiance is stored square-root encoded over [RANGE] so darks keep their precision in 8 bits.
 */
object EnvMap {
    /** Texels along a face edge (mipmapped down to 1 for rough reflections). */
    const val SIZE = 64

    /** Brightest radiance the 8-bit faces can hold. */
    const val RANGE = 4f

    // GL cube faces in order: +X, -X, +Y, -Y, +Z, -Z.

    /**
     * The direction through texel coordinates ([s], [t]) in -1..1 of cube [face] (GL's layout:
     * s runs along the stored rows, t down the rows). Written to [out] (not normalized).
     */
    fun faceDir(face: Int, s: Float, t: Float, out: FloatArray) {
        when (face) {
            0 -> { out[0] = 1f; out[1] = -t; out[2] = -s }
            1 -> { out[0] = -1f; out[1] = -t; out[2] = s }
            2 -> { out[0] = s; out[1] = 1f; out[2] = t }
            3 -> { out[0] = s; out[1] = -1f; out[2] = -t }
            4 -> { out[0] = s; out[1] = -t; out[2] = 1f }
            else -> { out[0] = -s; out[1] = -t; out[2] = -1f }
        }
    }

    /**
     * The inverse of [faceDir], as the GPU does it: which face direction ([x], [y], [z]) hits and
     * where, as s and t in -1..1 written to [st]. Returns the face.
     */
    fun dirToFace(x: Float, y: Float, z: Float, st: FloatArray): Int {
        val ax = abs(x)
        val ay = abs(y)
        val az = abs(z)
        return if (ax >= ay && ax >= az) {
            if (x > 0f) { st[0] = -z / ax; st[1] = -y / ax; 0 } else { st[0] = z / ax; st[1] = -y / ax; 1 }
        } else if (ay >= az) {
            if (y > 0f) { st[0] = x / ay; st[1] = z / ay; 2 } else { st[0] = x / ay; st[1] = -z / ay; 3 }
        } else {
            if (z > 0f) { st[0] = x / az; st[1] = -y / az; 4 } else { st[0] = -x / az; st[1] = -y / az; 5 }
        }
    }

    /** GLSL's reflect(): the incident direction ([ix], [iy], [iz]) mirrored about unit normal n. */
    fun reflect(ix: Float, iy: Float, iz: Float, nx: Float, ny: Float, nz: Float, out: FloatArray) {
        val d = 2f * (ix * nx + iy * ny + iz * nz)
        out[0] = ix - d * nx
        out[1] = iy - d * ny
        out[2] = iz - d * nz
    }

    private fun smooth(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** A soft-edged box of half size ([hx], [hy]) at the origin, feathered by [f]. */
    private fun box(x: Float, y: Float, hx: Float, hy: Float, f: Float): Float =
        (1f - smooth(hx - f, hx + f, abs(x))) * (1f - smooth(hy - f, hy + f, abs(y)))

    private fun fract(v: Float): Float = v - kotlin.math.floor(v)

    // Cabinet-light colours for the pools on the carpet (pink, cyan, gold, violet, green).
    private val poolR = floatArrayOf(1f, 0.2f, 1f, 0.6f, 0.3f)
    private val poolG = floatArrayOf(0.3f, 0.85f, 0.75f, 0.4f, 1f)
    private val poolB = floatArrayOf(0.7f, 1f, 0.25f, 1f, 0.55f)

    /** Radiance seen along direction ([x], [y], [z]) (any length), written to [out] as r, g, b. */
    fun radiance(x: Float, y: Float, z: Float, out: FloatArray) {
        val l = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-6f)
        val dx = x / l
        val dy = y / l
        val dz = z / l
        // Base: a purple-black room, darkest overhead.
        val up = smooth(0.05f, 0.55f, dy)
        val down = smooth(-0.02f, -0.45f, dy)
        var r = 0.085f * (1f - up) + 0.02f * up
        var g = 0.06f * (1f - up) + 0.017f * up
        var b = 0.14f * (1f - up) + 0.04f * up
        r = r * (1f - down) + 0.045f * down
        g = g * (1f - down) + 0.03f * down
        b = b * (1f - down) + 0.075f * down
        val az = atan2(dx, dz) // 0 toward the entrance (+z), ±π toward the back wall

        if (dy > 0.12f) {
            // The ceiling plane at height 1: warm light panels in a grid and two neon strips.
            val px = dx / dy
            val pz = dz / dy
            val fade = smooth(0.12f, 0.35f, dy)
            val cx = fract(px * 0.55f + 0.5f) - 0.5f
            val cz = fract(pz * 0.55f + 0.25f) - 0.5f
            val panel = box(cx, cz, 0.16f, 0.08f, 0.02f) * fade
            r += 1.9f * panel; g += 1.6f * panel; b += 1.15f * panel
            val sx = fract(px * 0.3f) - 0.5f
            val strip = (1f - smooth(0.012f, 0.03f, abs(sx))) * fade
            val pink = if (fract(px * 0.15f) < 0.5f) 1f else 0f
            r += strip * (1.2f + 1.3f * pink); g += strip * (1.9f - 1.4f * pink); b += strip * 2.2f
        }
        if (dy > -0.5f && dy < 0.5f) {
            // Walls: a neon band high up all the way round, pink at the back, cyan at the sides.
            val band = (1f - smooth(0.012f, 0.03f, abs(dy - 0.3f)))
            val back = smooth(1.6f, 2.6f, abs(az))
            r += band * (0.5f + 1.9f * back); g += band * (1.9f - 1.5f * back); b += band * (2.1f - 0.4f * back)
            if (abs(az) > 0.8f) {
                // Cool light boxes and warm marquees along the walls...
                val sector = fract(az * 1.2f) - 0.5f
                val cool = box(sector, dy - 0.12f, 0.18f, 0.045f, 0.02f)
                val warm = box(fract(az * 1.2f + 0.5f) - 0.5f, dy - 0.05f, 0.12f, 0.03f, 0.015f)
                r += cool * 0.8f + warm * 2.0f; g += cool * 1.05f + warm * 1.2f; b += cool * 1.5f + warm * 0.55f
                // ...and below the horizon, rows of glowing cabinet screens in every colour.
                val cab = fract(az * 2.6f)
                val scr = box(cab - 0.5f, dy + 0.16f, 0.3f, 0.13f, 0.04f)
                val c = (kotlin.math.floor(az * 2.6f).toInt() and 0x7fffffff) % poolR.size
                val k = scr * 1.1f * smooth(0.8f, 1.1f, abs(az))
                r += k * poolR[c]; g += k * poolG[c]; b += k * poolB[c]
            }
            // The entrance: tall bright windows with dark mullions, reaching well below the
            // horizon so upright glass and chrome seen from above still catch them.
            val win = box(az, dy + 0.075f, 0.72f, 0.42f, 0.06f)
            if (win > 0f) {
                val mullion = 1f - (1f - smooth(0.02f, 0.045f, abs(fract(az * 2.4f) - 0.5f))) * 0.85f
                val sill = 1f - (1f - smooth(0.008f, 0.02f, abs(dy - 0.12f))) * 0.8f
                val k = win * mullion * sill
                // Daylight above the sill, the dimmer street and sidewalk below it.
                val sky = smooth(-0.3f, 0.3f, dy)
                r += k * (0.35f + 0.6f * sky); g += k * (0.4f + 0.65f * sky); b += k * (0.5f + 0.8f * sky)
            }
        }
        if (dy < -0.3f) {
            // The carpet: dense faint specks of blacklight colour that blur into a tint.
            val px = dx / -dy
            val pz = dz / -dy
            val fade = smooth(-0.3f, -0.5f, dy)
            val gx = px * 4f
            val gz = pz * 4f
            val ix = kotlin.math.floor(gx)
            val iz = kotlin.math.floor(gz)
            val fx = gx - ix - 0.5f
            val fz = gz - iz - 0.5f
            val speck = max(0f, 1f - (fx * fx + fz * fz) * 10f) * fade
            val c = abs((ix * 7f + iz * 3f).toInt()) % poolR.size
            r += speck * poolR[c] * 0.35f; g += speck * poolG[c] * 0.35f; b += speck * poolB[c] * 0.35f
        }
        out[0] = min(r, RANGE)
        out[1] = min(g, RANGE)
        out[2] = min(b, RANGE)
    }

    /** Encodes radiance as the stored RGBA bytes (little-endian int, alpha 255). */
    fun encode(r: Float, g: Float, b: Float): Int {
        fun ch(v: Float): Int = (sqrt(min(max(v / RANGE, 0f), 1f)) * 255f + 0.5f).toInt()
        return (255 shl 24) or (ch(b) shl 16) or (ch(g) shl 8) or ch(r)
    }

    /** Decodes one stored channel byte back to radiance (what the shader does). */
    fun decode(byte: Int): Float {
        val s = byte / 255f
        return s * s * RANGE
    }

    private val faces = arrayOfNulls<IntArray>(6)

    /** The [SIZE]² texels of cube [face], 2×2 supersampled, encoded for upload (cached). */
    fun face(face: Int): IntArray = synchronized(faces) {
        faces[face] ?: buildFace(face, SIZE).also { faces[face] = it }
    }

    internal fun buildFace(face: Int, size: Int): IntArray {
        val out = IntArray(size * size)
        val d = FloatArray(3)
        val c = FloatArray(3)
        for (j in 0 until size) for (i in 0 until size) {
            var r = 0f
            var g = 0f
            var b = 0f
            for (k in 0 until 4) {
                val s = 2f * (i + 0.25f + 0.5f * (k and 1)) / size - 1f
                val t = 2f * (j + 0.25f + 0.5f * (k shr 1)) / size - 1f
                faceDir(face, s, t, d)
                radiance(d[0], d[1], d[2], c)
                r += c[0]; g += c[1]; b += c[2]
            }
            out[j * size + i] = encode(r * 0.25f, g * 0.25f, b * 0.25f)
        }
        return out
    }
}
