package com.pocketarcade.engine

/**
 * Turns 1x pixel sprites into rounder, more detailed 2x sprites: an EPX/Scale2x upscale smooths
 * diagonals, a bevel pass adds a top-left rim light and bottom-right shading so shapes read as
 * solid and 3D, and a fresh 1-pixel outline keeps them crisp at the new size.
 */
object SpriteFX {
    fun scale2x(src: PixelCanvas): PixelCanvas {
        val w = src.w
        val h = src.h
        val out = PixelCanvas(w * 2, h * 2)
        val p = src.px
        fun at(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= w || y >= h) 0 else p[y * w + x]
        for (y in 0 until h) for (x in 0 until w) {
            val c = p[y * w + x]
            val a = at(x, y - 1)
            val b = at(x + 1, y)
            val l = at(x - 1, y)
            val d = at(x, y + 1)
            var e0 = c
            var e1 = c
            var e2 = c
            var e3 = c
            if (l == a && l != d && a != b) e0 = a
            if (a == b && a != l && b != d) e1 = b
            if (d == l && d != b && l != a) e2 = l
            if (b == d && b != a && d != l) e3 = d
            val o = (y * 2) * out.w + x * 2
            out.px[o] = e0
            out.px[o + 1] = e1
            out.px[o + out.w] = e2
            out.px[o + out.w + 1] = e3
        }
        return out
    }

    /** Rim-light pixels facing up-left and shade pixels facing down-right. */
    fun bevel(c: PixelCanvas, light: Float = 0.3f, shade: Float = 0.7f) {
        val src = c.px.copyOf()
        val w = c.w
        val h = c.h
        fun opaque(x: Int, y: Int) = x in 0 until w && y in 0 until h && src[y * w + x] ushr 24 != 0
        for (y in 0 until h) for (x in 0 until w) {
            val v = src[y * w + x]
            if (v ushr 24 == 0) continue
            val edgeLight = !opaque(x - 1, y - 1) || !opaque(x, y - 1) || !opaque(x - 1, y)
            val edgeShade = !opaque(x + 1, y + 1) || !opaque(x, y + 1) || !opaque(x + 1, y)
            c.px[y * w + x] = when {
                edgeLight && !edgeShade -> Pal.mix(v, Pal.WHITE, light)
                edgeShade && !edgeLight -> Pal.shade(v, shade)
                else -> v
            }
        }
    }

    /** Scale2x + bevel + outline: the standard "HD" treatment for in-world sprites. */
    fun hd(base: PixelCanvas, outline: Int = Pal.BLACK, bevel: Boolean = true): PixelCanvas {
        val big = scale2x(base)
        if (bevel) bevel(big)
        big.outline(outline)
        return big
    }

    /** Pads a canvas by [pad] transparent pixels on every side (room for outlines). */
    fun pad(src: PixelCanvas, pad: Int): PixelCanvas {
        val out = PixelCanvas(src.w + pad * 2, src.h + pad * 2)
        out.blit(src, pad, pad)
        return out
    }
}
