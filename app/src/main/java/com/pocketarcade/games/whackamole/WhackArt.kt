package com.pocketarcade.games.whackamole

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture
import kotlin.math.sin

/** Painted art for the 3D whack-a-mole table. */
internal object WhackArt {
    /** Wood with a few long grain streaks across a [w] × [h] area. */
    private fun TexPaint.woodGrain(x: Float, y: Float, w: Float, h: Float, base: Int, seed: Int) {
        vgrad(x, y, w, h, base, Pal.shade(base, 0.8f))
        val lines = (w * h / 90f).toInt().coerceAtLeast(3)
        for (i in 0 until lines) {
            val gx = x + hash01(i, seed) * w
            val gy = y + hash01(i, seed + 1) * h
            val len = 6f + hash01(i, seed + 2) * 18f
            line(gx, gy, (gx + len).coerceAtMost(x + w), gy + 0.3f, 0.45f, Pal.withAlpha(Pal.shade(base, 0.6f), 0.5f))
        }
    }

    /**
     * The playfield: mown grass with flowers inside a wooden border, earthy rings round the holes
     * and the holes themselves cut out (transparent) so moles can rise through them.
     */
    fun table(w: Int, h: Int, holes: List<FloatArray>, holeR: Float): Texture = paintTexture(w, h, 2) {
        vgrad(0f, 0f, w.toFloat(), h.toFloat(), Pal.shade(Pal.GREEN, 0.9f), Pal.shade(Pal.GREEN, 0.74f))
        // Mown stripes, blade tufts and little flowers.
        for (y in 0 until h step 40) rect(0f, y.toFloat(), w.toFloat(), 20f, Pal.withAlpha(Pal.shade(Pal.GREEN, 0.7f), 0.35f))
        for (i in 0 until 900) {
            val x = hash01(i, 11) * w
            val y = hash01(i, 12) * h
            val col = if (i % 3 == 0) Pal.LIME else Pal.DARKGREEN
            val lean = (hash01(i, 15) - 0.5f) * 2f
            line(x, y, x + lean, y - 2.6f, 0.6f, Pal.withAlpha(Pal.shade(col, 0.9f), 0.8f))
        }
        for (i in 0 until 30) {
            val x = hash01(i, 13) * w
            val y = hash01(i, 14) * h
            val col = when (i % 3) {
                0 -> Pal.YELLOW
                1 -> Pal.WHITE
                else -> Pal.HOTPINK
            }
            for (k in 0 until 5) {
                val a = k * 1.2566f
                circle(x + kotlin.math.cos(a) * 1.3f, y + kotlin.math.sin(a) * 1.3f, 1f, col)
            }
            circle(x, y, 0.8f, Pal.ORANGE)
        }
        // Soft earth round each hole.
        for (hc in holes) {
            radial(hc[0], hc[1] + 2f, holeR + 20f, Pal.withAlpha(Pal.shade(Pal.GREEN, 0.45f), 0.9f), 0)
            circle(hc[0], hc[1] + 3f, holeR + 11f, Pal.shade(Pal.BROWN, 0.78f))
            ring(hc[0], hc[1] + 3f, holeR + 10f, 2f, Pal.shade(Pal.BROWN, 0.6f))
        }
        // Wooden border.
        val b = 18f
        woodGrain(0f, 0f, w.toFloat(), b, Pal.WOOD, 21)
        woodGrain(0f, h - b, w.toFloat(), b, Pal.WOOD, 22)
        woodGrain(0f, 0f, b, h.toFloat(), Pal.WOOD, 23)
        woodGrain(w - b, 0f, b, h.toFloat(), Pal.WOOD, 24)
        strokeRound(b - 1f, b - 1f, w - 2 * b + 2f, h - 2 * b + 2f, 3f, 2f, Pal.shade(Pal.WOOD, 0.55f))
        strokeRound(0.5f, 0.5f, w - 1f, h - 1f, 2f, 1f, Pal.shade(Pal.WOOD, 0.5f))
        for (hc in holes) punch(hc[0], hc[1], holeR)
    }

    /** Front of the cabinet under the table. */
    val front: Texture by lazy {
        paintTexture(210, 100) {
            vgrad(0f, 0f, 210f, 100f, Pal.shade(Pal.GREEN, 0.7f), Pal.shade(Pal.DARKGREEN, 0.6f))
            for (x in 0 until 210 step 30) roundGrad(x + 2f, 14f, 22f, 78f, 4f, Pal.shade(Pal.DARKGREEN, 0.85f), Pal.shade(Pal.DARKGREEN, 0.6f))
            woodGrain(0f, 0f, 210f, 8f, Pal.WOOD, 31)
            rect(0f, 8f, 210f, 1.5f, Pal.shade(Pal.WOOD, 0.45f))
            rect(0f, 94f, 210f, 6f, Pal.shade(Pal.BROWN, 0.55f))
        }
    }

    /** Backboard: wooden frame, a painted meadow under a sunny sky, and the title plaque. */
    val backboard: Texture by lazy {
        paintTexture(210, 140) {
            vgrad(0f, 0f, 210f, 140f, Pal.mix(Pal.SKY, Pal.WHITE, 0.15f), Pal.shade(Pal.SKY, 0.72f))
            radial(178f, 30f, 30f, Pal.withAlpha(Pal.YELLOW, 0.55f), 0)
            circle(178f, 30f, 11f, Pal.YELLOW)
            circle(178f, 30f, 8.5f, Pal.CREAM)
            for (i in 0 until 3) {
                val cx = 30f + i * 55f
                val cy = 22f + (i % 2) * 10f
                oval(cx, cy + 1f, 15f, 5f, Pal.withAlpha(Pal.shade(Pal.SKY, 0.8f), 0.5f))
                oval(cx, cy, 14f, 5f, Pal.WHITE)
                circle(cx - 5f, cy - 3f, 5f, Pal.WHITE)
                circle(cx + 4f, cy - 4f, 6f, Pal.WHITE)
            }
            // Rolling hills.
            fun hill(base: Float, amp1: Float, f1: Float, amp2: Float, f2: Float, phase: Float, color: Int) {
                val pts = ArrayList<Float>()
                pts += 0f; pts += 140f
                var x = 0f
                while (x <= 210f) {
                    pts += x; pts += base + sin(x / f1 + phase) * amp1 + sin(x / f2) * amp2
                    x += 3f
                }
                pts += 210f; pts += 140f
                polygon(pts.toFloatArray(), color)
            }
            hill(96f, 6f, 17f, 2f, 7f, 0f, Pal.shade(Pal.GREEN, 0.85f))
            hill(112f, 5f, 11f, 1f, 5f, 2f, Pal.DARKGREEN)
            // Title plaque.
            val text = "WHACK-A-MOLE"
            roundGrad(24f, 42f, 162f, 26f, 5f, Pal.shade(Pal.BROWN, 0.85f), Pal.shade(Pal.BROWN, 0.6f))
            strokeRound(24f, 42f, 162f, 26f, 5f, 1.6f, Pal.GOLD)
            label(text, 105f, 48f, 14f, Pal.YELLOW, shadow = Pal.BLACK)
            // Frame.
            woodGrain(0f, 0f, 210f, 6f, Pal.WOOD, 41)
            woodGrain(0f, 0f, 6f, 140f, Pal.WOOD, 42)
            woodGrain(204f, 0f, 6f, 140f, Pal.WOOD, 43)
            strokeRound(6f, 6f, 198f, 140f, 1f, 1f, Pal.shade(Pal.WOOD, 0.55f))
        }
    }

    val wood: Texture by lazy { paintTexture(32, 16) { woodGrain(0f, 0f, 32f, 16f, Pal.WOOD, 5) } }

    /** Dark soil inside the holes, darker towards the bottom. */
    val well: Texture by lazy {
        paintTexture(16, 32) {
            vgrad(0f, 0f, 16f, 32f, Pal.shade(Pal.DARKBROWN, 0.7f), Pal.BLACK)
            for (i in 0 until 14) circle(hash01(i, 7) * 16f, hash01(i, 8) * 20f, 0.5f, Pal.shade(Pal.BROWN, 0.55f))
        }
    }
    val wellBottom: Texture by lazy { TexKit.solid(8, 8, Pal.BLACK) }

    /** Rubber hole rim: a light top edge fading down. */
    val rim: Texture by lazy {
        paintTexture(32, 8) {
            vgrad(0f, 0f, 32f, 8f, Pal.shade(Pal.DARKGREEN, 1.25f), Pal.shade(Pal.DARKGREEN, 0.7f))
            vgrad(0f, 0f, 32f, 2.5f, Pal.LIME, Pal.withAlpha(Pal.LIME, 0f))
        }
    }

    /** The score display panel, repainted whenever its message changes. */
    private val panelPaint by lazy { TexPaint(128 * 4, 24 * 4).also { it.useUnits(4f) } }
    val panelTex: Texture = Texture(128, 24, IntArray(128 * 4 * 24 * 4), 4)

    fun paintPanel(text: String, color: Int) {
        with(panelPaint) {
            fill(0xFF06040A.toInt())
            for (y in 0 until 24 step 2) rect(0f, y.toFloat(), 128f, 0.6f, Pal.withAlpha(Pal.NIGHT, 0.6f))
            strokeRound(0.5f, 0.5f, 127f, 23f, 2f, 1f, Pal.shade(Pal.GRAY, 0.6f))
            glow(2f, Pal.withAlpha(color, 0.8f)) { label(text, 64f, 6f, 12f, -1) }
            label(text, 64f, 6f, 12f, Pal.mix(color, Pal.WHITE, 0.25f))
            update(panelTex)
        }
    }

    val malletHead: Texture by lazy {
        paintTexture(32, 16) {
            vgrad(0f, 0f, 32f, 16f, Pal.RED, Pal.DARKRED)
            rect(0f, 0f, 4f, 16f, Pal.CREAM)
            rect(28f, 0f, 4f, 16f, Pal.CREAM)
            vgrad(0f, 2.5f, 32f, 3f, Pal.mix(Pal.RED, Pal.WHITE, 0.45f), Pal.withAlpha(Pal.RED, 0f))
        }
    }
    val malletCap: Texture by lazy {
        paintTexture(16, 16) {
            fill(Pal.CREAM)
            ring(8f, 8f, 6.5f, 1.2f, Pal.shade(Pal.CREAM, 0.7f))
        }
    }
    val handle: Texture by lazy {
        paintTexture(8, 32) {
            vgrad(0f, 0f, 8f, 32f, Pal.TAN, Pal.shade(Pal.TAN, 0.75f))
            rect(0f, 0f, 8f, 6f, Pal.shade(Pal.DARKRED, 0.8f))
            for (y in 1 until 6 step 2) rect(0f, y.toFloat(), 8f, 0.5f, Pal.shade(Pal.DARKRED, 0.6f))
        }
    }

    /** The dizzy stars that circle a bonked mole. */
    val star: Texture by lazy {
        paintTexture(9, 9, 12) {
            clear(0)
            glow(0.8f, Pal.withAlpha(Pal.ORANGE, 0.9f)) { star(4.5f, 4.7f, 4.2f, -1) }
            star(4.5f, 4.7f, 4f, Pal.YELLOW)
            star(4.2f, 4.3f, 2f, Pal.mix(Pal.YELLOW, Pal.WHITE, 0.6f))
        }
    }
}
