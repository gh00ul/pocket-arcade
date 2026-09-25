package com.pocketarcade.games.hoops

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.SpriteFX
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.vgrad
import kotlin.math.sqrt

/** Procedural art for the 3D basketball alley. */
internal object HoopsArt {
    /** The alley floor: maple planks with a key and a free-throw arc. */
    val floor: Texture by lazy {
        val c = PixelCanvas(100, 190)
        for (x in 0 until c.w step 5) {
            val tone = 0.88f + hash01(x, 51) * 0.18f
            c.fill(x, 0, 5, c.h, if (tone > 1f) Pal.mix(Pal.TAN, Pal.CREAM, (tone - 1f) * 2f) else Pal.shade(Pal.TAN, tone))
            c.fill(x, 0, 1, c.h, Pal.shade(Pal.TAN, 0.72f))
            var y = (hash01(x, 52) * 40).toInt()
            while (y < c.h) {
                c.fill(x, y, 5, 1, Pal.shade(Pal.TAN, 0.75f))
                y += 45 + (hash01(x + y, 53) * 30).toInt()
            }
        }
        // Painted key towards the hoop (top of the texture is the far end).
        for (y in 0 until 70) for (x in 32 until 68) {
            if (x < 34 || x > 65 || y > 67) c.set(x, y, Pal.WHITE) else c.set(x, y, Pal.mix(c.get(x, y), Pal.RED, 0.35f))
        }
        for (a in 0 until 90) {
            val t = a / 89f * Math.PI.toFloat()
            val x = (50 + kotlin.math.cos(t) * 18).toInt()
            val y = (69 + kotlin.math.sin(t) * 18).toInt()
            c.fill(x, y, 2, 2, Pal.WHITE)
        }
        Texture.of(c)
    }

    val backWall: Texture by lazy {
        val c = PixelCanvas(130, 110)
        c.vgrad(0, 0, c.w, c.h, Pal.shade(Pal.PLUM, 0.7f), Pal.PLUM)
        for (x in 0 until c.w step 10) c.fill(x, 0, 1, c.h, Pal.shade(Pal.PLUM, 0.6f))
        c.fill(0, 88, c.w, 3, Pal.ORANGE)
        Texture.of(c)
    }

    val cabinet: Texture by lazy {
        val c = PixelCanvas(16, 16)
        c.vgrad(0, 0, c.w, c.h, Pal.RED, Pal.DARKRED)
        c.fill(0, 0, c.w, 2, Pal.ORANGE)
        Texture.of(c)
    }

    val post: Texture by lazy {
        val c = PixelCanvas(8, 16)
        c.vgrad(0, 0, c.w, c.h, Pal.LIGHTGRAY, Pal.GRAY)
        Texture.of(c)
    }

    /** Clear backboard with the red target square. */
    val board: Texture by lazy {
        val c = PixelCanvas(120, 85)
        c.fill(0, 0, c.w, c.h, Pal.mix(Pal.WHITE, Pal.SKY, 0.12f))
        c.fill(0, 0, c.w, 4, Pal.RED); c.fill(0, c.h - 4, c.w, 4, Pal.RED)
        c.fill(0, 0, 4, c.h, Pal.RED); c.fill(c.w - 4, 0, 4, c.h, Pal.RED)
        // Inner square above the rim.
        val l = 40
        val r = 80
        val t = 38
        val b = 64
        c.fill(l, t, r - l, 3, Pal.RED)
        c.fill(l, t, 3, b - t, Pal.RED)
        c.fill(r - 3, t, 3, b - t, Pal.RED)
        for (i in 0 until 14) c.set(8 + i, 8 + i / 2, Pal.WHITE)
        Texture.of(c)
    }
    val boardEdge: Texture by lazy { TexKit.solid(8, 8, Pal.shade(Pal.RED, 0.8f)) }

    val rim: Texture by lazy {
        val c = PixelCanvas(32, 4)
        c.vgrad(0, 0, c.w, c.h, Pal.mix(Pal.ORANGE, Pal.YELLOW, 0.3f), Pal.shade(Pal.ORANGE, 0.7f))
        Texture.of(c)
    }

    /** Diamond mesh for the cage nets (alpha-blended). */
    val net: Texture by lazy {
        val c = PixelCanvas(64, 64)
        for (y in 0 until 64) for (x in 0 until 64) {
            val on = (x + y) % 16 == 0 || (x - y + 64) % 16 == 0
            if (on) c.set(x, y, Pal.withAlpha(Pal.LAVENDER, 0.45f))
        }
        Texture.of(c)
    }

    val ball: Texture by lazy {
        val n = 18
        val c = PixelCanvas(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f - 9f) / 8.6f
            val dy = (y + 0.5f - 9f) / 8.6f
            val d2 = dx * dx + dy * dy
            if (d2 > 1f) continue
            val dz = sqrt(1f - d2)
            val lam = (-dx * 0.45f - dy * 0.55f + dz * 0.7f).coerceAtLeast(0f)
            c.set(x, y, if (lam > 0.6f) Pal.mix(Pal.ORANGE, Pal.YELLOW, (lam - 0.6f) * 1.2f) else Pal.shade(Pal.ORANGE, 0.6f + lam * 0.7f))
        }
        c.vline(9, 1, 16, Pal.DARKBROWN)
        c.hline(1, 16, 9, Pal.DARKBROWN)
        for (y in 2..15) {
            val dx = (sqrt(49f - (y - 9f) * (y - 9f)).coerceAtLeast(0f) * 0.55f).toInt()
            c.set(4 + (3 - dx).coerceAtLeast(0), y, Pal.DARKBROWN)
            c.set(13 - (3 - dx).coerceAtLeast(0), y, Pal.DARKBROWN)
        }
        c.set(6, 4, Pal.WHITE)
        Texture.of(SpriteFX.hd(c, Pal.DARKBROWN, bevel = false))
    }
}
