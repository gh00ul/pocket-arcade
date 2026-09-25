package com.pocketarcade.games.whackamole

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.SpriteFX
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.bigText
import com.pocketarcade.engine.r3d.bigTextCentered
import com.pocketarcade.engine.r3d.bigTextWidth
import com.pocketarcade.engine.r3d.vgrad
import kotlin.math.sqrt

/** Procedural art for the 3D whack-a-mole table. */
internal object WhackArt {
    /**
     * The playfield: grassy felt with a painted border, and the holes cut out (fully
     * transparent texels, which the renderer's alpha test skips) so moles can rise through them.
     */
    fun table(w: Int, h: Int, holes: List<FloatArray>, holeR: Float): Texture {
        val c = PixelCanvas(w, h)
        c.vgrad(0, 0, w, h, Pal.shade(Pal.GREEN, 0.9f), Pal.shade(Pal.GREEN, 0.75f))
        // Mown stripes and tufts.
        for (y in 0 until h step 40) c.fill(0, y, w, 20, Pal.shade(Pal.GREEN, 0.84f))
        for (i in 0 until 500) {
            val x = (hash01(i, 11) * w).toInt()
            val y = (hash01(i, 12) * h).toInt()
            val col = if (i % 3 == 0) Pal.LIME else Pal.DARKGREEN
            c.fill(x, y, 1, 3, Pal.shade(col, 0.9f))
            c.set(x + 1, y + 1, Pal.shade(col, 0.8f))
        }
        for (i in 0 until 26) {
            val x = (hash01(i, 13) * w).toInt()
            val y = (hash01(i, 14) * h).toInt()
            val col = when (i % 3) {
                0 -> Pal.YELLOW
                1 -> Pal.WHITE
                else -> Pal.HOTPINK
            }
            c.disc(x.toFloat(), y.toFloat(), 1.6f, col)
            c.set(x, y, Pal.ORANGE)
        }
        // Wooden border.
        val b = 18
        c.fill(0, 0, w, b, Pal.WOOD)
        c.fill(0, h - b, w, b, Pal.WOOD)
        c.fill(0, 0, b, h, Pal.WOOD)
        c.fill(w - b, 0, b, h, Pal.WOOD)
        c.rect(b - 2, b - 2, w - 2 * b + 4, h - 2 * b + 4, Pal.shade(Pal.WOOD, 0.6f))
        c.rect(0, 0, w, h, Pal.shade(Pal.WOOD, 0.55f))
        // Soft dirt around each hole, then the hole itself.
        for (hc in holes) {
            c.disc(hc[0], hc[1], holeR + 16f, Pal.shade(Pal.GREEN, 0.62f))
            c.disc(hc[0], hc[1] + 3f, holeR + 12f, Pal.shade(Pal.BROWN, 0.8f))
        }
        for (hc in holes) {
            for (y in (hc[1] - holeR - 1).toInt()..(hc[1] + holeR + 1).toInt()) {
                for (x in (hc[0] - holeR - 1).toInt()..(hc[0] + holeR + 1).toInt()) {
                    val dx = x + 0.5f - hc[0]
                    val dy = y + 0.5f - hc[1]
                    if (sqrt(dx * dx + dy * dy) < holeR) c.set(x, y, 0)
                }
            }
        }
        return Texture.of(c)
    }

    /** Front of the cabinet under the table. */
    val front: Texture by lazy {
        val c = PixelCanvas(210, 100)
        c.vgrad(0, 0, c.w, c.h, Pal.shade(Pal.GREEN, 0.7f), Pal.shade(Pal.DARKGREEN, 0.6f))
        for (x in 0 until c.w step 30) c.fill(x, 12, 14, c.h - 12, Pal.shade(Pal.DARKGREEN, 0.75f))
        c.fill(0, 0, c.w, 8, Pal.WOOD)
        c.fill(0, 8, c.w, 2, Pal.shade(Pal.WOOD, 0.5f))
        c.fill(0, c.h - 6, c.w, 6, Pal.shade(Pal.BROWN, 0.6f))
        Texture.of(c)
    }

    /** Backboard: wooden frame, a painted meadow and the title. */
    val backboard: Texture by lazy {
        val c = PixelCanvas(210, 140)
        c.vgrad(0, 0, c.w, c.h, Pal.SKY, Pal.shade(Pal.SKY, 0.7f))
        // Rolling hills.
        for (x in 0 until c.w) {
            val h1 = 96 + (kotlin.math.sin(x / 17f) * 6f + kotlin.math.sin(x / 7f) * 2f).toInt()
            c.fill(x, h1, 1, c.h - h1, Pal.shade(Pal.GREEN, 0.85f))
            val h2 = 112 + (kotlin.math.sin(x / 11f + 2f) * 5f).toInt()
            c.fill(x, h2, 1, c.h - h2, Pal.DARKGREEN)
        }
        c.disc(178f, 30f, 12f, Pal.YELLOW)
        c.disc(178f, 30f, 9f, Pal.CREAM)
        for (i in 0 until 3) c.ellipse(30f + i * 55f, 22f + (i % 2) * 10f, 14f, 5f, Pal.WHITE)
        val text = "WHACK-A-MOLE"
        val s = 2
        val tw = bigTextWidth(text, s)
        val x = (c.w - tw) / 2
        c.fill(x - 8, 42, tw + 16, 24, Pal.shade(Pal.BROWN, 0.7f))
        c.rect(x - 8, 42, tw + 16, 24, Pal.GOLD)
        c.bigText(text, x + 1, 48, Pal.BLACK, s)
        c.bigText(text, x, 47, Pal.YELLOW, s)
        c.fill(0, 0, c.w, 6, Pal.WOOD)
        c.fill(0, 0, 6, c.h, Pal.WOOD)
        c.fill(c.w - 6, 0, 6, c.h, Pal.WOOD)
        c.rect(6, 6, c.w - 12, c.h - 6, Pal.shade(Pal.WOOD, 0.6f))
        Texture.of(c)
    }

    val wood: Texture by lazy {
        val c = PixelCanvas(32, 16)
        c.vgrad(0, 0, c.w, c.h, Pal.WOOD, Pal.shade(Pal.WOOD, 0.7f))
        for (i in 0 until 10) c.fill((hash01(i, 5) * 30).toInt(), (hash01(i, 6) * 15).toInt(), 6, 1, Pal.shade(Pal.WOOD, 0.8f))
        Texture.of(c)
    }

    /** Dark soil inside the holes, darker towards the bottom. */
    val well: Texture by lazy {
        val c = PixelCanvas(16, 32)
        c.vgrad(0, 0, c.w, c.h, Pal.shade(Pal.DARKBROWN, 0.7f), Pal.BLACK)
        for (i in 0 until 12) c.set((hash01(i, 7) * 16).toInt(), (hash01(i, 8) * 20).toInt(), Pal.shade(Pal.BROWN, 0.6f))
        Texture.of(c)
    }
    val wellBottom: Texture by lazy { TexKit.solid(8, 8, Pal.BLACK) }

    /** Rubber hole rim: a light top edge fading down. */
    val rim: Texture by lazy {
        val c = PixelCanvas(32, 8)
        c.vgrad(0, 0, c.w, c.h, Pal.shade(Pal.DARKGREEN, 1.25f), Pal.shade(Pal.DARKGREEN, 0.7f))
        c.fill(0, 0, c.w, 2, Pal.LIME)
        Texture.of(c)
    }

    /** The score display panel. Repainted in place, so the texture shares these pixels. */
    val panel = PixelCanvas(128, 24)
    val panelTex: Texture = Texture.of(panel)

    fun paintPanel(text: String, color: Int) {
        panel.fill(0, 0, panel.w, panel.h, Pal.BLACK)
        for (y in 0 until panel.h step 2) panel.fill(0, y, panel.w, 1, Pal.shade(Pal.NIGHT, 1.2f))
        panel.rect(0, 0, panel.w, panel.h, Pal.shade(Pal.GRAY, 0.6f))
        panel.bigTextCentered(text, panel.w / 2, 6, color, 2, Pal.shade(color, 0.3f))
    }

    val malletHead: Texture by lazy {
        val c = PixelCanvas(32, 16)
        c.vgrad(0, 0, c.w, c.h, Pal.RED, Pal.DARKRED)
        c.fill(0, 0, 4, c.h, Pal.CREAM)
        c.fill(c.w - 4, 0, 4, c.h, Pal.CREAM)
        c.fill(0, 3, c.w, 2, Pal.mix(Pal.RED, Pal.WHITE, 0.4f))
        Texture.of(c)
    }
    val malletCap: Texture by lazy {
        val c = PixelCanvas(16, 16)
        c.disc(8f, 8f, 8f, Pal.CREAM)
        c.ring(8f, 8f, 7f, 1.5f, Pal.shade(Pal.CREAM, 0.7f))
        Texture.of(c)
    }
    val handle: Texture by lazy {
        val c = PixelCanvas(8, 32)
        c.vgrad(0, 0, c.w, c.h, Pal.TAN, Pal.shade(Pal.TAN, 0.75f))
        c.fill(0, 0, c.w, 6, Pal.shade(Pal.DARKRED, 0.8f))
        Texture.of(c)
    }

    val star: Texture by lazy {
        val c = PixelCanvas(9, 9)
        val rows = arrayOf(
            "....#....",
            "....#....",
            "...###...",
            "#########",
            ".#######.",
            "..#####..",
            ".###.###.",
            ".##...##.",
            "#.......#",
        )
        c.sprite(rows, 0, 0, mapOf('#' to Pal.YELLOW))
        c.set(4, 3, Pal.WHITE)
        Texture.of(SpriteFX.hd(c, Pal.ORANGE, bevel = true))
    }

    // ------------------------------------------------------------------ characters

    fun mole(body: Int, face: Int, bonked: Boolean, sparkle: Boolean): PixelCanvas {
        val c = PixelCanvas(22, 38)
        c.disc(11f, 10f, 9f, body)
        c.fill(2, 10, 18, 28, body)
        c.ellipse(11f, 13f, 6.5f, 5.5f, face)
        // Belly.
        c.ellipse(11f, 22f, 6f, 4f, Pal.mix(face, Pal.WHITE, 0.2f))
        if (bonked) {
            for (d in 0..2) {
                c.set(6 + d, 7 + d, Pal.BLACK); c.set(8 - d, 7 + d, Pal.BLACK)
                c.set(13 + d, 7 + d, Pal.BLACK); c.set(15 - d, 7 + d, Pal.BLACK)
            }
            c.fill(9, 15, 4, 2, Pal.DARKRED)
        } else {
            c.fill(7, 8, 2, 3, Pal.BLACK); c.fill(13, 8, 2, 3, Pal.BLACK)
            c.set(7, 8, Pal.WHITE); c.set(13, 8, Pal.WHITE)
            c.fill(10, 15, 1, 2, Pal.WHITE); c.fill(11, 15, 1, 2, Pal.WHITE)
        }
        c.ellipse(11f, 12.5f, 2.4f, 1.5f, Pal.HOTPINK)
        c.set(10, 12, Pal.WHITE)
        c.disc(4f, 19f, 2.4f, face); c.disc(18f, 19f, 2.4f, face)
        c.set(3, 17, Pal.WHITE); c.set(5, 17, Pal.WHITE); c.set(17, 17, Pal.WHITE); c.set(19, 17, Pal.WHITE)
        if (sparkle) {
            c.set(3, 3, Pal.WHITE); c.set(18, 2, Pal.WHITE); c.set(20, 12, Pal.WHITE)
        }
        return c
    }

    fun bomb(bonked: Boolean): PixelCanvas {
        val c = PixelCanvas(22, 38)
        c.fill(10, 0, 2, 4, Pal.TAN)
        c.fill(8, 3, 6, 2, Pal.GRAY)
        // A round bomb on a jack-in-the-box spring.
        for (y in 20 until 38) {
            val x = if ((y / 3) % 2 == 0) 8 else 10
            c.fill(x, y, 5, 2, if (y % 3 == 0) Pal.LIGHTGRAY else Pal.GRAY)
        }
        c.disc(11f, 13f, 9.5f, Pal.DARKGRAY)
        c.disc(8f, 9f, 2.5f, Pal.GRAY)
        c.set(7, 8, Pal.WHITE)
        if (bonked) {
            c.disc(11f, 13f, 6f, Pal.ORANGE)
            c.disc(11f, 13f, 3.5f, Pal.YELLOW)
        } else {
            c.hline(5, 9, 10, Pal.RED); c.hline(13, 17, 10, Pal.RED)
            c.set(5, 9, Pal.RED); c.set(17, 9, Pal.RED)
            c.fill(7, 11, 2, 2, Pal.RED); c.fill(14, 11, 2, 2, Pal.RED)
            c.hline(8, 14, 17, Pal.BLACK)
            c.set(9, 16, Pal.WHITE); c.set(13, 16, Pal.WHITE)
        }
        return c
    }

    /** HD sprite textures: normal, bonked, gold, gold bonked, bomb, bomb hit. */
    val sprites: Array<Texture> by lazy {
        arrayOf(
            mole(Pal.BROWN, Pal.TAN, bonked = false, sparkle = false),
            mole(Pal.BROWN, Pal.TAN, bonked = true, sparkle = false),
            mole(Pal.GOLD, Pal.YELLOW, bonked = false, sparkle = true),
            mole(Pal.GOLD, Pal.YELLOW, bonked = true, sparkle = true),
            bomb(false),
            bomb(true),
        ).map { Texture.of(SpriteFX.hd(it)) }.toTypedArray()
    }
}
