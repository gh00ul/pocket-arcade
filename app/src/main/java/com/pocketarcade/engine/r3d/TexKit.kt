package com.pocketarcade.engine.r3d

import com.pocketarcade.engine.Pal
import kotlin.math.sqrt

/** Shared procedural textures: glows, shadows, bulbs and flat colours. */
object TexKit {
    /** Soft white radial glow; tinted when drawn additively. */
    val glow: Texture by lazy { radial(32, 1f, 255) }

    /** Soft dark ellipse for contact shadows. */
    val shadow: Texture by lazy { radial(32, 1f, 170, Pal.BLACK) }

    /** A white disc with a soft edge: light bulbs and round sparks. */
    val dot: Texture by lazy { radial(32, -0.85f, 255) }

    /** Plain white, for tinted solid-colour polygons. */
    val white: Texture by lazy { solid(4, 4, Pal.WHITE) }

    fun solid(w: Int, h: Int, color: Int) = Texture(w, h, IntArray(w * h) { color })

    fun radial(n: Int, power: Float, maxAlpha: Int, color: Int = Pal.WHITE): Texture {
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f - n / 2f) / (n / 2f)
            val dy = (y + 0.5f - n / 2f) / (n / 2f)
            val d = sqrt(dx * dx + dy * dy)
            val v = (1f - d).coerceIn(0f, 1f)
            val a = (Math.pow(v.toDouble(), power.toDouble() + 1.0) * maxAlpha).toInt()
            px[y * n + x] = (a shl 24) or (color and 0xFFFFFF)
        }
        return Texture(n, n, px)
    }
}
