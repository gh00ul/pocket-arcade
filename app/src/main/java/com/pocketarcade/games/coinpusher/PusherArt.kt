package com.pocketarcade.games.coinpusher

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.SpriteFX
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.bigTextCentered
import com.pocketarcade.engine.r3d.vgrad

/** Procedural art for the 3D coin pusher. */
internal object PusherArt {
    /** A coin face seen from above: rim, face, emboss and a glint. */
    fun coinFace(n: Int, face: Int, rim: Int, mark: Boolean): PixelCanvas {
        val c = PixelCanvas(n, n)
        val m = n / 2f
        c.disc(m, m, m - 0.5f, rim)
        c.disc(m, m, m - 2.5f, face)
        c.ring(m, m, m - 4f, 1f, Pal.shade(face, 0.85f))
        if (mark) {
            // A star stamped in the middle.
            val cx = m.toInt()
            for (i in -4..4) c.set(cx + i, cx - 1, Pal.shade(face, 0.75f))
            for (i in -3..3) c.set(cx, cx + i - 1, Pal.shade(face, 0.75f))
            c.set(cx - 2, cx + 2, Pal.shade(face, 0.75f)); c.set(cx + 2, cx + 2, Pal.shade(face, 0.75f))
        } else {
            c.ellipse(m, m, 3f, 3.5f, Pal.shade(face, 0.8f))
            c.ellipse(m - 0.5f, m - 0.5f, 2f, 2.5f, face)
        }
        c.set((m - 4).toInt(), (m - 4).toInt(), Pal.WHITE)
        c.set((m - 3).toInt(), (m - 4).toInt(), Pal.WHITE)
        c.set((m - 4).toInt(), (m - 3).toInt(), Pal.WHITE)
        return c
    }

    val coin: Texture by lazy { Texture.of(coinFace(24, Pal.GOLD, Pal.ORANGE, mark = false)) }
    val coinEdge: Texture by lazy {
        val c = PixelCanvas(24, 24)
        c.disc(12f, 12f, 11.5f, Pal.shade(Pal.ORANGE, 0.6f))
        Texture.of(c)
    }
    val bigCoin: Texture by lazy { Texture.of(coinFace(32, Pal.YELLOW, Pal.ORANGE, mark = true)) }

    val gem: Texture by lazy {
        val c = PixelCanvas(12, 12)
        for (y in 0 until 12) {
            val half = if (y < 4) 2 + y else 11 - y
            c.hline(6 - half, 5 + half, y, if (y < 4) Pal.CYAN else Pal.SKY)
        }
        c.hline(2, 9, 4, Pal.WHITE)
        c.set(4, 2, Pal.WHITE)
        Texture.of(SpriteFX.hd(c, Pal.NAVY))
    }

    val tickets: Texture by lazy {
        val c = PixelCanvas(20, 14)
        c.fill(0, 3, 20, 11, Pal.ORANGE)
        c.fill(0, 0, 20, 11, Pal.GOLD)
        c.rect(0, 0, 20, 11, Pal.ORANGE)
        for (x in 4 until 20 step 5) c.fill(x, 1, 1, 9, Pal.shade(Pal.GOLD, 0.8f))
        c.set(2, 5, Pal.DARKRED); c.set(2, 6, Pal.DARKRED)
        Texture.of(c)
    }

    val star: Texture by lazy {
        val c = PixelCanvas(14, 14)
        val rows = arrayOf(
            "......##......",
            "......##......",
            ".....####.....",
            ".....####.....",
            "##############",
            ".############.",
            "..##########..",
            "...########...",
            "...########...",
            "..####..####..",
            "..###....###..",
            ".###......###.",
            ".##........##.",
            "..............",
        )
        c.sprite(rows, 0, 0, mapOf('#' to Pal.YELLOW))
        c.set(6, 5, Pal.WHITE); c.set(7, 5, Pal.WHITE)
        Texture.of(SpriteFX.hd(c, Pal.ORANGE))
    }

    /** The deck: dark blue with lane stripes and arrows towards the lip. */
    fun deck(w: Int, h: Int): Texture {
        val c = PixelCanvas(w, h)
        c.vgrad(0, 0, w, h, Pal.shade(Pal.NAVY, 0.8f), Pal.NAVY)
        for (y in 0 until h step 40) c.fill(0, y, w, 2, Pal.INDIGO)
        for (x in 0 until w step 50) c.fill(x, 0, 1, h, Pal.shade(Pal.INDIGO, 0.8f))
        for (i in 0 until 60) {
            val x = (hash01(i, 31) * w).toInt()
            val y = (hash01(i, 32) * h).toInt()
            c.set(x, y, Pal.shade(Pal.SKY, 0.5f))
        }
        for (k in 0 until 3) {
            val cy = h - 26 - k * 10
            for (i in 0 until 6) {
                val cx = 25 + i * 50
                for (d in 0 until 6) {
                    c.set(cx - d, cy - 6 + d, Pal.shade(Pal.GOLD, 0.5f))
                    c.set(cx + d, cy - 6 + d, Pal.shade(Pal.GOLD, 0.5f))
                }
            }
        }
        return Texture.of(c)
    }

    /** Pusher shelf front: hazard stripes. */
    val shelfFront: Texture by lazy {
        val c = PixelCanvas(120, 14)
        for (x in 0 until c.w) for (y in 0 until c.h) {
            c.set(x, y, if (((x + y) / 7) % 2 == 0) Pal.YELLOW else Pal.BLACK)
        }
        c.fill(0, 0, c.w, 2, Pal.LIGHTGRAY)
        Texture.of(c)
    }
    val shelfTop: Texture by lazy {
        val c = PixelCanvas(60, 40)
        c.vgrad(0, 0, c.w, c.h, Pal.GRAY, Pal.LIGHTGRAY)
        for (y in 0 until c.h step 6) c.fill(0, y, c.w, 1, Pal.shade(Pal.GRAY, 0.8f))
        Texture.of(c)
    }

    /** Back wall of the cabinet, with a coin slot and room for the coin counter. */
    val backWall: Texture by lazy {
        val c = PixelCanvas(180, 130)
        c.vgrad(0, 0, c.w, c.h, Pal.shade(Pal.ORANGE, 0.8f), Pal.shade(Pal.DARKRED, 0.8f))
        for (i in 0 until 9) {
            val x = 10 + i * 20
            c.fill(x, 0, 8, c.h, Pal.shade(Pal.ORANGE, 0.7f))
        }
        c.fill(0, c.h - 10, c.w, 10, Pal.shade(Pal.DARKRED, 0.5f))
        c.fill(0, 0, c.w, 4, Pal.GOLD)
        c.bigTextCentered("COIN PUSHER", c.w / 2, 8, Pal.YELLOW, 2, Pal.DARKRED)
        Texture.of(c)
    }

    val cabinet: Texture by lazy {
        val c = PixelCanvas(32, 32)
        c.vgrad(0, 0, c.w, c.h, Pal.ORANGE, Pal.shade(Pal.ORANGE, 0.6f))
        c.fill(0, 0, c.w, 3, Pal.GOLD)
        Texture.of(c)
    }
    val gold: Texture by lazy { TexKit.solid(8, 8, Pal.GOLD) }

    /** The win tray: plum velvet with gold stripes. */
    val tray: Texture by lazy {
        val c = PixelCanvas(64, 32)
        c.vgrad(0, 0, c.w, c.h, Pal.PLUM, Pal.shade(Pal.PLUM, 0.7f))
        for (x in 0 until c.w step 8) c.fill(x, 0, 1, c.h, Pal.shade(Pal.GOLD, 0.45f))
        Texture.of(c)
    }

    /** The face below the lip that coins tumble past. */
    val lipFace: Texture by lazy {
        val c = PixelCanvas(64, 16)
        c.vgrad(0, 0, c.w, c.h, Pal.shade(Pal.ORANGE, 0.55f), Pal.shade(Pal.DARKRED, 0.5f))
        for (x in 0 until c.w step 16) c.fill(x, 3, 8, 2, Pal.YELLOW)
        Texture.of(c)
    }
    val dark: Texture by lazy { TexKit.solid(8, 8, Pal.shade(Pal.NIGHT, 0.7f)) }
    val glass: Texture by lazy {
        val c = PixelCanvas(32, 16)
        c.fill(0, 0, 32, 16, Pal.withAlpha(Pal.SKY, 0.16f))
        for (i in 0 until 10) c.set(4 + i, 2 + i / 2, Pal.withAlpha(Pal.WHITE, 0.5f))
        c.fill(0, 0, 32, 1, Pal.withAlpha(Pal.WHITE, 0.6f))
        Texture.of(c)
    }

    /** Small LED panel repainted with the coins left / won counters. */
    class Panel(w: Int, h: Int) {
        val canvas = PixelCanvas(w, h)
        val tex = Texture.of(canvas)
        private var last = ""

        fun paint(text: String, color: Int) {
            if (text == last) return
            last = text
            canvas.fill(0, 0, canvas.w, canvas.h, Pal.BLACK)
            for (y in 0 until canvas.h step 2) canvas.fill(0, y, canvas.w, 1, Pal.shade(Pal.NIGHT, 1.3f))
            canvas.rect(0, 0, canvas.w, canvas.h, Pal.shade(Pal.GOLD, 0.6f))
            canvas.bigTextCentered(text, canvas.w / 2, (canvas.h - 14) / 2, color, 2, Pal.shade(color, 0.3f))
        }
    }
}
