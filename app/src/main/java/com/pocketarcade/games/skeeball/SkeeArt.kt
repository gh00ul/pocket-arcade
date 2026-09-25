package com.pocketarcade.games.skeeball

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture

/** Painted textures and the ball for the 3D skee-ball alley. */
internal object SkeeArt {
    /** Texels per world unit across the target board. */
    const val BOARD_TPU = 1.5f

    /** Wooden planks running along a [w] × [h] area, with seams, grain and the odd knot. */
    private fun TexPaint.planks(w: Float, h: Float, plankW: Float, base: Int, seed: Int) {
        val n = ((w + plankW - 1f) / plankW).toInt()
        for (i in 0 until n) {
            val tone = 0.86f + hash01(i, seed) * 0.22f
            val col = if (tone > 1f) Pal.mix(base, Pal.CREAM, (tone - 1f) * 1.5f) else Pal.shade(base, tone)
            val x0 = i * plankW
            hgrad(x0, 0f, plankW, h, Pal.shade(col, 0.95f), col, Pal.shade(col, 0.93f))
            // Grain: long faint streaks.
            for (k in 0 until (h / 5f).toInt()) {
                val gx = x0 + 1f + hash01(i * 97 + k, seed + 1) * (plankW - 2f)
                val gy = hash01(i * 97 + k, seed + 2) * h
                val len = 8f + hash01(k, i + seed) * 26f
                line(gx, gy, gx + (hash01(k, i) - 0.5f) * 0.6f, gy + len, 0.35f, Pal.withAlpha(Pal.shade(col, 0.82f), 0.6f))
            }
            if (hash01(i, seed + 7) > 0.5f) {
                val ky = hash01(i, seed + 8) * (h - 8f) + 3f
                oval(x0 + plankW / 2f, ky, 1.7f, 2.6f, Pal.shade(col, 0.72f))
                oval(x0 + plankW / 2f, ky, 0.8f, 1.3f, Pal.shade(col, 0.6f))
            }
            rect(x0, 0f, 0.6f, h, Pal.shade(base, 0.55f))
            rect(x0 + 0.6f, 0f, 0.6f, h, Pal.withAlpha(Pal.WHITE, 0.12f))
            // Butt joints at staggered lengths.
            var y = hash01(i, seed + 3) * 120f
            while (y < h) {
                rect(x0, y, plankW, 0.6f, Pal.shade(base, 0.6f))
                y += 140f + hash01(i + y.toInt(), seed + 4) * 80f
            }
        }
    }

    /** The lane from the ramp (top of the texture) to the player's end. */
    fun lane(widthUnits: Int, lengthUnits: Int, tpu: Float): Texture {
        val w = (widthUnits * tpu).toInt()
        val h = (lengthUnits * tpu).toInt()
        return paintTexture(w, h, 2) {
            planks(w.toFloat(), h.toFloat(), 18f, Pal.WOOD, 3)
            // Varnish: a sheen down the middle, darker edges.
            hgrad(0f, 0f, w * 0.18f, h.toFloat(), 0x55000000, 0)
            hgrad(w * 0.82f, 0f, w * 0.18f, h.toFloat(), 0, 0x55000000)
            hgrad(w * 0.3f, 0f, w * 0.4f, h.toFloat(), 0, 0x14FFFFFF, 0)
            // Foul line near the player's end.
            val fy = h - 60f * tpu
            rect(0f, fy, w.toFloat(), 3f, Pal.CREAM)
            rect(0f, fy + 3f, w.toFloat(), 1f, Pal.shade(Pal.WOOD, 0.55f))
        }
    }

    /** A chevron pointing up the lane, for the pulsing guide lights. */
    val chevron: Texture by lazy {
        paintTexture(24, 16, 6) {
            clear(0)
            polygon(floatArrayOf(12f, 1f, 23f, 12f, 19f, 15f, 12f, 8f, 5f, 15f, 1f, 12f), Pal.WHITE)
        }
    }

    /** Lighter, steeper planks for the jump ramp. */
    val ramp: Texture by lazy {
        paintTexture(120, 24) {
            planks(120f, 24f, 9f, Pal.TAN, 11)
            vgrad(0f, 0f, 120f, 2.5f, Pal.CREAM, Pal.withAlpha(Pal.CREAM, 0.3f))
        }
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
        val w = ((right - left) * tu).toInt()
        val h = ((bottom - top) * tv).toInt()
        return paintTexture(w, h, 3) {
            vgrad(0f, 0f, w.toFloat(), h.toFloat(), Pal.INDIGO, Pal.NAVY)
            for (x in 0 until w step 24) rect(x.toFloat(), 0f, 0.8f, h.toFloat(), Pal.withAlpha(Pal.shade(Pal.NAVY, 0.6f), 0.7f))
            for (y in 0 until h step 24) rect(0f, y.toFloat(), w.toFloat(), 0.8f, Pal.withAlpha(Pal.shade(Pal.NAVY, 0.6f), 0.7f))
            val ux = (cx - left) * tu
            val vy = (cy - top) * tv
            for (i in radii.indices.reversed()) {
                val r = radii[i] * tu
                val col = colors[i]
                circle(ux, vy, r, Pal.shade(col, 0.32f))
                // Recessed cup: shadow along the top inside edge, a lit lip along the bottom.
                radial(ux, vy + 3f, r - 3f, Pal.shade(col, 0.55f), Pal.shade(col, 0.38f))
                ring(ux, vy, r - 1.5f, 3f, col)
                ring(ux, vy + 1f, r - 3.5f, 1f, Pal.mix(col, Pal.WHITE, 0.45f))
            }
            radial(ux, vy, 9f * tu, Pal.BLACK, Pal.shade(Pal.NIGHT, 0.6f))
            ring(ux, vy, 9f * tu, 1.5f, Pal.shade(Pal.RED, 0.6f))
            for (i in 1 until radii.size) {
                val side = if (i % 2 == 1) 1f else -1f
                val lx = ux + side * (radii[i - 1] + radii[i]) / 2f * tu
                label(points[i].toString(), lx, vy - 6f, 11f, Pal.WHITE, shadow = Pal.BLACK)
            }
            label(points[0].toString(), ux, vy - radii[0] * tu - 20f, 13f, Pal.YELLOW, shadow = Pal.BLACK)
            // Bonus hole.
            val bx = (bonusX - left) * tu
            val by = (bonusY - top) * tv
            radial(bx, by, (bonusR + 5f) * tu, Pal.mix(Pal.GOLD, Pal.WHITE, 0.3f), Pal.GOLD)
            ring(bx, by, (bonusR + 5f) * tu, 1.5f, Pal.ORANGE)
            circle(bx, by, bonusR * tu, Pal.BLACK)
            label("200", bx, by + (bonusR + 7f) * tu, 11f, Pal.GOLD, shadow = Pal.BLACK)
        }
    }

    /** Inner face of a cabinet side: blue panel, stars, and a dark socket strip for the bulbs. */
    val sidePanel: Texture by lazy {
        paintTexture(160, 140) {
            vgrad(0f, 0f, 160f, 140f, Pal.shade(Pal.BLUE, 0.75f), Pal.shade(Pal.NAVY, 0.7f))
            for (i in 0 until 40) {
                val x = hash01(i, 21) * 160f
                val y = hash01(i, 22) * 140f
                if (i % 3 == 0) star(x, y, 2.2f, Pal.shade(Pal.SKY, 0.8f)) else circle(x, y, 0.5f, Pal.SKY)
            }
            for (x in 0 until 160 step 20) rect(x.toFloat(), 0f, 1.5f, 140f, Pal.shade(Pal.NAVY, 0.55f))
            rect(0f, 0f, 160f, 6f, Pal.YELLOW)
            rect(0f, 6f, 160f, 2f, Pal.ORANGE)
        }
    }

    /** Lane rails: blue cabinet wood with a light cap. */
    val railSide: Texture by lazy {
        paintTexture(64, 16) {
            vgrad(0f, 0f, 64f, 16f, Pal.BLUE, Pal.shade(Pal.BLUE, 0.55f))
            vgrad(0f, 0f, 64f, 2.5f, Pal.SKY, Pal.withAlpha(Pal.SKY, 0.2f))
        }
    }

    /** Along the top of each rail, the yellow edge on the lane side. */
    val railTop: Texture by lazy {
        paintTexture(40, 4) {
            fill(Pal.shade(Pal.BLUE, 0.8f))
            rect(30f, 0f, 10f, 4f, Pal.YELLOW)
            rect(29f, 0f, 1f, 4f, Pal.ORANGE)
        }
    }

    /** The raised ring walls: white, shading darker towards their base. */
    val ringWall: Texture by lazy {
        paintTexture(4, 8) {
            vgrad(0f, 0f, 4f, 8f, Pal.WHITE, Pal.GRAY)
        }
    }

    /** Marquee above the target. */
    val marquee: Texture by lazy {
        paintTexture(320, 90, 3) {
            vgrad(0f, 0f, 320f, 90f, Pal.DARKRED, Pal.shade(Pal.PLUM, 0.8f))
            rect(0f, 0f, 320f, 4f, Pal.GOLD)
            rect(0f, 86f, 320f, 4f, Pal.GOLD)
            val text = "SKEE-BALL"
            for (o in 3 downTo 1) label(text, 160f + o * 0.8f, 24f + o * 1f, 42f, Pal.shade(Pal.DARKRED, 0.4f))
            glow(3f, Pal.withAlpha(Pal.ORANGE, 0.8f)) { label(text, 160f, 24f, 42f, -1) }
            label(text, 160f, 24f, 42f, Pal.YELLOW)
            for (i in 0 until 18) {
                circle(9f + i * 17.8f, 10f, 3.2f, Pal.shade(Pal.BROWN, 0.5f))
                circle(9f + i * 17.8f, 80f, 3.2f, Pal.shade(Pal.BROWN, 0.5f))
            }
        }
    }

    val pit: Texture by lazy { TexKit.solid(8, 8, Pal.shade(Pal.NIGHT, 0.6f)) }
    val boardEdge: Texture by lazy { TexKit.solid(8, 8, Pal.shade(Pal.NAVY, 0.5f)) }

    /** The ball: glossy dark red with a bright band round its middle. */
    fun ball(radius: Float): Model {
        val tex = paintTexture(64, 32) {
            vgrad(0f, 0f, 64f, 32f, Pal.mix(Pal.DARKRED, Pal.RED, 0.5f), Pal.DARKRED)
            rect(0f, 13f, 64f, 6f, Pal.RED)
            rect(0f, 13f, 64f, 0.6f, Pal.mix(Pal.RED, Pal.WHITE, 0.4f))
        }
        return ModelBuilder().sphere(0f, 0f, 0f, radius, tex.full, slices = 20, stacks = 14, gloss = 0.9f).build()
    }
}
