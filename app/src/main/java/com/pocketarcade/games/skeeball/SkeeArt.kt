package com.pocketarcade.games.skeeball

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.bigText
import com.pocketarcade.engine.r3d.bigTextCentered
import com.pocketarcade.engine.r3d.bigTextWidth
import com.pocketarcade.engine.r3d.vgrad

/** Procedural textures for the 3D skee-ball alley. */
internal object SkeeArt {
    /** Texels per world unit across the target board. */
    const val BOARD_TPU = 1.5f

    /** Wooden planks running along a lane, with seams, grain and the odd knot. */
    fun planks(c: PixelCanvas, plankW: Int, base: Int, seed: Int) {
        val n = (c.w + plankW - 1) / plankW
        for (i in 0 until n) {
            val tone = 0.86f + hash01(i, seed) * 0.22f
            val col = if (tone > 1f) Pal.mix(base, Pal.CREAM, (tone - 1f) * 1.5f) else Pal.shade(base, tone)
            val x0 = i * plankW
            c.fill(x0, 0, plankW, c.h, col)
            // Grain: long faint streaks.
            for (k in 0 until c.h / 6) {
                val gx = x0 + 1 + (hash01(i * 97 + k, seed + 1) * (plankW - 2)).toInt()
                val gy = (hash01(i * 97 + k, seed + 2) * c.h).toInt()
                val len = 6 + (hash01(k, i + seed) * 22).toInt()
                c.fill(gx, gy, 1, len, Pal.shade(col, 0.9f))
            }
            if (hash01(i, seed + 7) > 0.5f) {
                val ky = (hash01(i, seed + 8) * (c.h - 8)).toInt()
                c.ellipse(x0 + plankW / 2f, ky + 3f, 1.8f, 2.6f, Pal.shade(col, 0.72f))
            }
            c.fill(x0, 0, 1, c.h, Pal.shade(base, 0.6f))
            c.fill(x0 + 1, 0, 1, c.h, Pal.mix(col, Pal.WHITE, 0.12f))
            // Butt joints at staggered lengths.
            var y = (hash01(i, seed + 3) * 120).toInt()
            while (y < c.h) {
                c.fill(x0, y, plankW, 1, Pal.shade(base, 0.62f))
                y += 140 + (hash01(i + y, seed + 4) * 80).toInt()
            }
        }
    }

    /** The lane from the ramp (top of the texture) to the player's end. */
    fun lane(widthUnits: Int, lengthUnits: Int, tpu: Float): Texture {
        val c = PixelCanvas((widthUnits * tpu).toInt(), (lengthUnits * tpu).toInt())
        planks(c, 18, Pal.WOOD, 3)
        // Varnish sheen down the middle and darker edges.
        for (x in 0 until c.w) {
            val e = kotlin.math.abs(x - c.w / 2f) / (c.w / 2f)
            if (e > 0.82f) for (y in 0 until c.h) c.set(x, y, Pal.shade(c.get(x, y), 1f - (e - 0.82f) * 1.6f))
        }
        // Foul line near the player's end.
        val fy = c.h - (60 * tpu).toInt()
        c.fill(0, fy, c.w, 3, Pal.CREAM)
        c.fill(0, fy + 3, c.w, 1, Pal.shade(Pal.WOOD, 0.6f))
        return Texture.of(c)
    }

    /** A chevron pointing up the lane, for the pulsing guide lights. */
    val chevron: Texture by lazy {
        val c = PixelCanvas(24, 16)
        for (i in 0 until 12) {
            c.fill(i, 12 - i, 4, 4, Pal.WHITE)
            c.fill(23 - i - 3, 12 - i, 4, 4, Pal.WHITE)
        }
        Texture.of(c)
    }

    /** Lighter, steeper planks for the jump ramp. */
    val ramp: Texture by lazy {
        val c = PixelCanvas(120, 24)
        planks(c, 9, Pal.TAN, 11)
        c.fill(0, 0, c.w, 2, Pal.CREAM)
        Texture.of(c)
    }

    /**
     * The inclined target board: concentric scoring rings, their values, the centre cup and
     * the 200 bonus hole. Laid out so the game's squashed ring ellipses become true circles
     * once the board is tilted back.
     */
    fun board(
        left: Float, top: Float, right: Float, bottom: Float, cx: Float, cy: Float, squash: Float,
        radii: FloatArray, points: IntArray, colors: IntArray, bonusX: Float, bonusY: Float, bonusR: Float,
    ): Texture {
        val tu = BOARD_TPU
        val tv = BOARD_TPU / squash
        val c = PixelCanvas(((right - left) * tu).toInt(), ((bottom - top) * tv).toInt())
        c.vgrad(0, 0, c.w, c.h, Pal.INDIGO, Pal.NAVY)
        for (x in 0 until c.w step 24) c.fill(x, 0, 1, c.h, Pal.shade(Pal.NAVY, 0.8f))
        for (y in 0 until c.h step 24) c.fill(0, y, c.w, 1, Pal.shade(Pal.NAVY, 0.8f))
        val ux = (cx - left) * tu
        val vy = (cy - top) * tv
        for (i in radii.indices.reversed()) {
            val r = radii[i] * tu
            val col = colors[i]
            c.disc(ux, vy, r, Pal.shade(col, 0.32f))
            // Recessed cup: a shadow along the top inside edge, a lit lip along the bottom.
            c.disc(ux, vy + 3f, r - 4f, Pal.shade(col, 0.5f))
            c.ring(ux, vy, r - 1f, 3f, col)
            c.ring(ux, vy + 1f, r - 3f, 1f, Pal.mix(col, Pal.WHITE, 0.45f))
        }
        c.disc(ux, vy, 9f * tu, Pal.BLACK)
        c.ring(ux, vy, 9f * tu, 1.5f, Pal.shade(Pal.RED, 0.6f))
        for (i in 1 until radii.size) {
            val side = if (i % 2 == 1) 1f else -1f
            val lx = ux + side * (radii[i - 1] + radii[i]) / 2f * tu
            c.bigTextCentered(points[i].toString(), lx.toInt(), (vy - 7).toInt(), Pal.WHITE, 2)
        }
        c.bigTextCentered(points[0].toString(), ux.toInt(), (vy - radii[0] * tu - 22).toInt(), Pal.YELLOW, 2)
        // Bonus hole.
        val bx = (bonusX - left) * tu
        val by = (bonusY - top) * tv
        c.disc(bx, by, (bonusR + 5f) * tu, Pal.GOLD)
        c.ring(bx, by, (bonusR + 5f) * tu, 1.5f, Pal.ORANGE)
        c.disc(bx, by, bonusR * tu, Pal.BLACK)
        c.bigTextCentered("200", bx.toInt(), (by + (bonusR + 7f) * tu).toInt(), Pal.GOLD, 2)
        return Texture.of(c)
    }

    /** Inner face of a cabinet side: blue panel, stars, and a dark socket strip for the bulbs. */
    val sidePanel: Texture by lazy {
        val c = PixelCanvas(160, 140)
        c.vgrad(0, 0, c.w, c.h, Pal.shade(Pal.BLUE, 0.75f), Pal.shade(Pal.NAVY, 0.7f))
        for (i in 0 until 40) {
            val x = (hash01(i, 21) * c.w).toInt()
            val y = (hash01(i, 22) * c.h).toInt()
            c.set(x, y, Pal.SKY)
            if (i % 3 == 0) {
                c.set(x - 1, y, Pal.shade(Pal.SKY, 0.6f)); c.set(x + 1, y, Pal.shade(Pal.SKY, 0.6f))
            }
        }
        for (x in 0 until c.w step 20) c.fill(x, 0, 2, c.h, Pal.shade(Pal.NAVY, 0.55f))
        c.fill(0, 0, c.w, 6, Pal.YELLOW)
        c.fill(0, 6, c.w, 2, Pal.ORANGE)
        Texture.of(c)
    }

    /** Lane rails: blue cabinet wood with a yellow cap. */
    val railSide: Texture by lazy {
        val c = PixelCanvas(64, 16)
        c.vgrad(0, 0, c.w, c.h, Pal.BLUE, Pal.shade(Pal.BLUE, 0.55f))
        c.fill(0, 0, c.w, 2, Pal.SKY)
        Texture.of(c)
    }

    /** Marquee above the target. */
    val marquee: Texture by lazy {
        val c = PixelCanvas(320, 90)
        c.vgrad(0, 0, c.w, c.h, Pal.DARKRED, Pal.shade(Pal.PLUM, 0.8f))
        c.fill(0, 0, c.w, 4, Pal.GOLD)
        c.fill(0, c.h - 4, c.w, 4, Pal.GOLD)
        val text = "SKEE-BALL"
        val scale = 5
        val tw = bigTextWidth(text, scale)
        val x = (c.w - tw) / 2
        for (o in 0..2) c.bigText(text, x + o, 26 + o, Pal.shade(Pal.DARKRED, 0.4f), scale)
        c.bigText(text, x - 1, 25, Pal.ORANGE, scale)
        c.bigText(text, x, 24, Pal.YELLOW, scale)
        for (i in 0 until 18) {
            c.disc(9f + i * 17.8f, 10f, 3.2f, Pal.shade(Pal.BROWN, 0.5f))
            c.disc(9f + i * 17.8f, c.h - 10f, 3.2f, Pal.shade(Pal.BROWN, 0.5f))
        }
        Texture.of(c)
    }

    val pit: Texture by lazy { TexKit.solid(8, 8, Pal.shade(Pal.NIGHT, 0.6f)) }
    val boardEdge: Texture by lazy { TexKit.solid(8, 8, Pal.shade(Pal.NAVY, 0.5f)) }

    /** Eight frames of a striped ball rolling away from the viewer. */
    val ballFrames: Array<Texture> by lazy {
        Array(8) { k -> Texture.of(TexKit.sphere(32, Pal.DARKRED, stripe = Pal.RED, roll = k * (Math.PI.toFloat() / 4f))) }
    }
}
