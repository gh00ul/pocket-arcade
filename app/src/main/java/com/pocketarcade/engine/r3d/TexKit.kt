package com.pocketarcade.engine.r3d

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.PixelFont
import kotlin.math.max
import kotlin.math.sqrt

/** Draws bitmap-font text into a canvas at an integer [scale]. */
fun PixelCanvas.bigText(s: String, x: Int, y: Int, color: Int, scale: Int, tiny: Boolean = false) {
    val adv = if (tiny) PixelFont.TADV else PixelFont.ADV
    for (i in s.indices) {
        val rows = PixelFont.rows(s[i], tiny) ?: continue
        for (ry in rows.indices) for (rx in rows[ry].indices) {
            if (rows[ry][rx] == '#') fill(x + (i * adv + rx) * scale, y + ry * scale, scale, scale, color)
        }
    }
}

fun bigTextWidth(s: String, scale: Int, tiny: Boolean = false): Int =
    if (s.isEmpty()) 0 else ((if (tiny) PixelFont.TADV else PixelFont.ADV) * s.length - 1) * scale

/** Centred [bigText] with a one-texel drop shadow. */
fun PixelCanvas.bigTextCentered(s: String, cx: Int, y: Int, color: Int, scale: Int, shadow: Int = Pal.BLACK) {
    val x = cx - bigTextWidth(s, scale) / 2
    bigText(s, x + scale / 2 + 1, y + scale / 2 + 1, shadow, scale)
    bigText(s, x, y, color, scale)
}

/** Vertical gradient fill. */
fun PixelCanvas.vgrad(x: Int, y: Int, w: Int, h: Int, top: Int, bottom: Int) {
    for (yy in 0 until h) fill(x, y + yy, w, 1, Pal.mix(top, bottom, if (h <= 1) 0f else yy / (h - 1f)))
}

/** Shared procedural textures: glows, shadows, sparks, flat colours and shaded balls. */
object TexKit {
    /** Soft white radial glow; tinted when drawn additively. */
    val glow: Texture by lazy { radial(32, 1f, 255) }

    /** Soft dark ellipse for contact shadows. */
    val shadow: Texture by lazy { radial(32, 1f, 170, Pal.BLACK) }

    /** A tiny bright spark for particles. */
    val spark: Texture by lazy {
        Texture(4, 4, IntArray(16) { i ->
            val x = i % 4
            val y = i / 4
            if ((x == 1 || x == 2) || (y == 1 || y == 2)) -1 else 0x40FFFFFF
        })
    }

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

    /**
     * A lit sphere sprite [n] texels across: [base] colour with a soft top-left key light, a
     * dark rim and a specular glint. [stripe] (ARGB, 0 for none) paints a band around the ball,
     * turned [roll] radians about the horizontal axis, so a strip of frames shows it rolling.
     */
    fun sphere(n: Int, base: Int, stripe: Int = 0, outline: Int = Pal.BLACK, roll: Float = 0.3f): PixelCanvas {
        val rc = kotlin.math.cos(roll)
        val rs = kotlin.math.sin(roll)
        val c = PixelCanvas(n, n)
        val r = n / 2f - 1f
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f - n / 2f) / r
            val dy = (y + 0.5f - n / 2f) / r
            val d2 = dx * dx + dy * dy
            if (d2 > 1f) continue
            val dz = sqrt(1f - d2)
            // Key light from the upper left, towards the viewer.
            val lam = (-dx * 0.45f - dy * 0.55f + dz * 0.7f).coerceAtLeast(0f)
            var col = if (stripe != 0 && kotlin.math.abs(dy * rc + dz * rs) < 0.17f) stripe else base
            col = if (lam > 0.55f) Pal.mix(col, Pal.WHITE, (lam - 0.55f) * 0.6f) else Pal.shade(col, 0.55f + lam * 0.8f)
            val spec = max(0f, -dx * 0.4f - dy * 0.5f + dz * 0.77f)
            if (spec > 0.97f) col = Pal.mix(col, Pal.WHITE, 0.85f)
            c.set(x, y, col)
        }
        if (outline != 0) c.outline(outline)
        return c
    }
}
