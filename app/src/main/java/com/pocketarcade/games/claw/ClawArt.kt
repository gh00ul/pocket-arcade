package com.pocketarcade.games.claw

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.bigText
import com.pocketarcade.engine.r3d.bigTextCentered
import com.pocketarcade.engine.r3d.bigTextWidth
import com.pocketarcade.engine.r3d.vgrad

/** Procedural art for the 3D claw machine. */
internal object ClawArt {
    private fun starry(w: Int, h: Int, top: Int, bottom: Int, seed: Int): PixelCanvas {
        val c = PixelCanvas(w, h)
        c.vgrad(0, 0, w, h, top, bottom)
        for (i in 0 until w * h / 90) {
            val x = (hash01(i, seed) * w).toInt()
            val y = (hash01(i, seed + 1) * h).toInt()
            val col = if (i % 5 == 0) Pal.HOTPINK else Pal.LAVENDER
            c.set(x, y, Pal.shade(col, 0.55f + hash01(i, seed + 2) * 0.45f))
            if (i % 7 == 0) {
                c.set(x - 1, y, Pal.shade(col, 0.5f)); c.set(x + 1, y, Pal.shade(col, 0.5f))
                c.set(x, y - 1, Pal.shade(col, 0.5f)); c.set(x, y + 1, Pal.shade(col, 0.5f))
            }
        }
        return c
    }

    val backWall: Texture by lazy {
        val c = starry(170, 250, Pal.INDIGO, Pal.PLUM, 3)
        // Big faint hearts.
        for (k in 0 until 6) {
            val cx = 20f + (k % 3) * 65f
            val cy = 40f + (k / 3) * 110f + (k % 2) * 20f
            val col = Pal.shade(Pal.PINK, 0.35f)
            c.disc(cx - 6f, cy, 7f, col); c.disc(cx + 6f, cy, 7f, col)
            for (y in 0 until 12) c.hline((cx - 12 + y).toInt(), (cx + 12 - y).toInt(), (cy + 2 + y).toInt(), col)
        }
        Texture.of(c)
    }

    val sideWall: Texture by lazy { Texture.of(starry(60, 250, Pal.shade(Pal.INDIGO, 0.8f), Pal.shade(Pal.PLUM, 0.8f), 7)) }

    val floor: Texture by lazy {
        val c = PixelCanvas(96, 32)
        c.fill(0, 0, c.w, c.h, Pal.VIOLET)
        for (y in 0 until c.h step 8) for (x in 0 until c.w step 8) {
            if ((x / 8 + y / 8) % 2 == 0) c.fill(x, y, 8, 8, Pal.shade(Pal.VIOLET, 0.85f))
        }
        Texture.of(c)
    }

    val cabinet: Texture by lazy {
        val c = PixelCanvas(32, 32)
        c.vgrad(0, 0, c.w, c.h, Pal.PINK, Pal.shade(Pal.PINK, 0.65f))
        c.fill(0, 0, c.w, 2, Pal.HOTPINK)
        Texture.of(c)
    }

    val marquee: Texture by lazy {
        val c = PixelCanvas(180, 36)
        c.vgrad(0, 0, c.w, c.h, Pal.HOTPINK, Pal.shade(Pal.PINK, 0.6f))
        c.fill(0, 0, c.w, 3, Pal.YELLOW)
        c.fill(0, c.h - 3, c.w, 3, Pal.YELLOW)
        val text = "CLAW MACHINE"
        val s = 2
        val x = (c.w - bigTextWidth(text, s)) / 2
        c.bigText(text, x + 1, 12, Pal.shade(Pal.DARKRED, 0.6f), s)
        c.bigText(text, x, 11, Pal.YELLOW, s)
        Texture.of(c)
    }

    val base: Texture by lazy {
        val c = PixelCanvas(180, 60)
        c.vgrad(0, 0, c.w, c.h, Pal.shade(Pal.PINK, 0.8f), Pal.shade(Pal.DARKRED, 0.6f))
        c.fill(0, 0, c.w, 3, Pal.YELLOW)
        for (x in 0 until c.w step 12) c.fill(x + 4, 10, 4, c.h - 16, Pal.shade(Pal.PINK, 0.6f))
        Texture.of(c)
    }

    val metal: Texture by lazy {
        val c = PixelCanvas(16, 16)
        c.vgrad(0, 0, c.w, c.h, Pal.LIGHTGRAY, Pal.GRAY)
        c.fill(0, 0, c.w, 2, Pal.WHITE)
        Texture.of(c)
    }
    val darkMetal: Texture by lazy {
        val c = PixelCanvas(16, 16)
        c.vgrad(0, 0, c.w, c.h, Pal.GRAY, Pal.DARKGRAY)
        Texture.of(c)
    }
    val goldMetal: Texture by lazy {
        val c = PixelCanvas(16, 16)
        c.vgrad(0, 0, c.w, c.h, Pal.YELLOW, Pal.ORANGE)
        c.fill(0, 0, c.w, 2, Pal.WHITE)
        Texture.of(c)
    }

    val glass: Texture by lazy {
        val c = PixelCanvas(64, 64)
        c.fill(0, 0, c.w, c.h, Pal.withAlpha(Pal.LAVENDER, 0.07f))
        Texture.of(c)
    }

    /** Diagonal glare streaks on the front glass (drawn additively). */
    val glare: Texture by lazy {
        val c = PixelCanvas(64, 64)
        for (y in 0 until 64) for (x in 0 until 64) {
            val d = (x - y * 0.6f + 64) % 64
            val a = when {
                d < 3 -> 50
                d in 7f..8f -> 35
                else -> 0
            }
            c.set(x, y, (a shl 24) or 0xFFFFFF)
        }
        Texture.of(c)
    }

    val lipGlass: Texture by lazy {
        val c = PixelCanvas(16, 32)
        c.fill(0, 0, c.w, c.h, Pal.withAlpha(Pal.SKY, 0.22f))
        c.fill(0, 0, c.w, 2, Pal.withAlpha(Pal.WHITE, 0.8f))
        Texture.of(c)
    }

    val winSign: Texture by lazy {
        val c = PixelCanvas(40, 18)
        c.fill(0, 0, c.w, c.h, Pal.BLACK)
        c.rect(0, 0, c.w, c.h, Pal.GOLD)
        c.bigTextCentered("WIN", 20, 5, Pal.YELLOW, 1)
        Texture.of(c)
    }

    val neon: Texture by lazy { TexKit.solid(8, 8, Pal.HOTPINK) }
    val pit: Texture by lazy { TexKit.solid(8, 8, Pal.BLACK) }
}
