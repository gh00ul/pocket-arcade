package com.pocketarcade.games.racer

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.SpriteFX
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.bigTextCentered
import com.pocketarcade.engine.r3d.vgrad

/** Procedural art for the synthwave racer. */
internal object RacerArt {
    /** Setting sun with scanline cut-outs. */
    val sun: Texture by lazy {
        val n = 64
        val c = PixelCanvas(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = x + 0.5f - n / 2f
            val dy = y + 0.5f - n / 2f
            if (dx * dx + dy * dy > (n / 2f - 1) * (n / 2f - 1)) continue
            // Horizontal gaps that widen towards the bottom.
            val band = y - n / 2
            if (band > 0 && (band % 8) < 1 + band / 8) continue
            c.set(x, y, Pal.mix(Pal.YELLOW, Pal.PINK, y / n.toFloat()))
        }
        Texture.of(c)
    }

    /** City skyline silhouette for the horizon. */
    val skyline: Texture by lazy {
        val c = PixelCanvas(256, 48)
        var x = 0
        var k = 0
        while (x < c.w) {
            val w = 8 + (k * 37 % 14)
            val h = 12 + (k * 53 % 30)
            c.fill(x, c.h - h, w, h, Pal.shade(Pal.PLUM, 0.7f))
            for (wy in c.h - h + 3 until c.h - 2 step 4) for (wx in x + 2 until x + w - 2 step 3) {
                if ((wx * 7 + wy * 13 + k) % 5 == 0) c.set(wx, wy, Pal.shade(Pal.YELLOW, 0.7f))
            }
            x += w + (k % 3)
            k++
        }
        Texture.of(c)
    }

    val mountains: Texture by lazy {
        val c = PixelCanvas(256, 40)
        for (x in 0 until c.w) {
            val h = (18 + kotlin.math.sin(x / 19f) * 9 + kotlin.math.sin(x / 7f + 1f) * 4 + kotlin.math.sin(x / 43f) * 6).toInt()
            for (y in c.h - h until c.h) c.set(x, y, Pal.mix(Pal.INDIGO, Pal.VIOLET, (y - (c.h - h)) / h.toFloat() * 0.5f))
            c.set(x, c.h - h, Pal.HOTPINK)
        }
        Texture.of(c)
    }

    /** Ground with a neon grid; each segment shows one cross line along its start. */
    val ground: Texture by lazy {
        val c = PixelCanvas(128, 8)
        c.fill(0, 0, c.w, c.h, 0xFF14082A.toInt())
        for (x in 0 until c.w step 8) c.fill(x, 0, 1, c.h, Pal.shade(Pal.PINK, 0.8f))
        c.fill(0, 0, c.w, 1, Pal.PINK)
        Texture.of(c)
    }

    val asphalt: Texture by lazy {
        val c = PixelCanvas(16, 8)
        c.fill(0, 0, c.w, c.h, 0xFF24203A.toInt())
        for (i in 0 until 10) c.set((i * 7) % 16, (i * 3) % 8, 0xFF2E2A48.toInt())
        Texture.of(c)
    }
    val asphaltDark: Texture by lazy {
        val c = PixelCanvas(16, 8)
        c.fill(0, 0, c.w, c.h, 0xFF1C1830.toInt())
        for (i in 0 until 10) c.set((i * 5) % 16, (i * 7) % 8, 0xFF26223E.toInt())
        Texture.of(c)
    }

    val white: Texture get() = TexKit.white

    /** Palm tree silhouette with neon rim. */
    val palm: Texture by lazy {
        val c = PixelCanvas(28, 44)
        for (y in 14 until 44) {
            val x = 13 + (kotlin.math.sin(y / 9f) * 2f).toInt()
            c.fill(x, y, 3, 1, Pal.shade(Pal.BROWN, 0.55f))
            if (y % 4 == 0) c.set(x, y, Pal.shade(Pal.BROWN, 0.35f))
        }
        val fronds = arrayOf(-1f to -0.35f, 1f to -0.35f, -1f to 0.25f, 1f to 0.25f, -0.4f to -0.9f, 0.4f to -0.9f)
        for ((dx, dy) in fronds) {
            for (t in 0 until 13) {
                val px = 14 + (dx * t).toInt()
                val py = 14 + (dy * t + t * t * 0.05f).toInt()
                c.fill(px - 1, py, 3, 2, Pal.shade(Pal.GREEN, 0.5f))
            }
        }
        c.disc(14f, 14f, 2.5f, Pal.shade(Pal.BROWN, 0.5f))
        Texture.of(SpriteFX.hd(c, Pal.PINK, bevel = false))
    }

    /** A roadside billboard. */
    fun sign(text: String, color: Int): Texture {
        val c = PixelCanvas(60, 22)
        c.fill(0, 0, c.w, c.h, Pal.BLACK)
        c.rect(0, 0, c.w, c.h, color)
        c.rect(2, 2, c.w - 4, c.h - 4, Pal.shade(color, 0.5f))
        c.bigTextCentered(text, c.w / 2, 7, color, 1)
        return Texture.of(c)
    }
    val signs: Array<Texture> by lazy {
        arrayOf(
            sign("ARCADE", Pal.CYAN),
            sign("TURBO!", Pal.PINK),
            sign("TOKENS", Pal.YELLOW),
            sign("HI-SCORE", Pal.LIME),
        )
    }
    val post: Texture by lazy { TexKit.solid(4, 4, Pal.DARKGRAY) }

    /** A spinning token pickup (drawn squashed to fake the spin). */
    val token: Texture by lazy {
        val c = PixelCanvas(14, 14)
        c.disc(7f, 7f, 6.5f, Pal.ORANGE)
        c.disc(7f, 7f, 5f, Pal.GOLD)
        c.fill(6, 4, 2, 6, Pal.ORANGE)
        c.set(4, 3, Pal.WHITE)
        Texture.of(SpriteFX.hd(c, Pal.DARKBROWN, bevel = true))
    }

    // ------------------------------------------------------------------ cars

    fun bodySide(color: Int): Texture {
        val c = PixelCanvas(40, 10)
        c.vgrad(0, 0, c.w, c.h, Pal.mix(color, Pal.WHITE, 0.25f), Pal.shade(color, 0.6f))
        c.fill(0, 2, c.w, 1, Pal.mix(color, Pal.WHITE, 0.5f))
        return Texture.of(c)
    }
    fun bodyTop(color: Int): Texture {
        val c = PixelCanvas(20, 40)
        c.vgrad(0, 0, c.w, c.h, Pal.mix(color, Pal.WHITE, 0.15f), Pal.shade(color, 0.85f))
        c.fill(8, 0, 4, c.h, Pal.mix(color, Pal.WHITE, 0.4f))
        return Texture.of(c)
    }
    fun rear(color: Int): Texture {
        val c = PixelCanvas(24, 10)
        c.vgrad(0, 0, c.w, c.h, Pal.shade(color, 0.85f), Pal.shade(color, 0.55f))
        c.fill(1, 2, 6, 3, Pal.RED)
        c.fill(c.w - 7, 2, 6, 3, Pal.RED)
        c.fill(2, 2, 4, 1, Pal.mix(Pal.RED, Pal.WHITE, 0.5f))
        c.fill(c.w - 6, 2, 4, 1, Pal.mix(Pal.RED, Pal.WHITE, 0.5f))
        c.fill(9, 6, 6, 2, Pal.shade(Pal.DARKGRAY, 0.8f))
        c.fill(0, c.h - 2, c.w, 2, Pal.BLACK)
        return Texture.of(c)
    }
    val glass: Texture by lazy {
        val c = PixelCanvas(16, 8)
        c.vgrad(0, 0, c.w, c.h, Pal.shade(Pal.SKY, 0.6f), Pal.shade(Pal.NAVY, 0.8f))
        c.fill(2, 1, 5, 1, Pal.mix(Pal.SKY, Pal.WHITE, 0.4f))
        Texture.of(c)
    }
    val tyre: Texture by lazy { TexKit.solid(4, 4, 0xFF101018.toInt()) }
    val tailGlow: Texture get() = TexKit.glow
}
