package com.pocketarcade.games.hoops

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture
import kotlin.math.PI
import kotlin.math.sin

/** Painted art and the ball for the 3D basketball alley. */
internal object HoopsArt {
    /** The alley floor: maple planks with a key and a free-throw arc. */
    val floor: Texture by lazy {
        paintTexture(100, 190, 4) {
            for (x in 0 until 100 step 5) {
                val tone = 0.88f + hash01(x, 51) * 0.18f
                val col = if (tone > 1f) Pal.mix(Pal.TAN, Pal.CREAM, (tone - 1f) * 2f) else Pal.shade(Pal.TAN, tone)
                hgrad(x.toFloat(), 0f, 5f, 190f, Pal.shade(col, 0.96f), col)
                rect(x.toFloat(), 0f, 0.4f, 190f, Pal.shade(Pal.TAN, 0.7f))
                var y = hash01(x, 52) * 40f
                while (y < 190f) {
                    rect(x.toFloat(), y, 5f, 0.4f, Pal.shade(Pal.TAN, 0.72f))
                    y += 45f + hash01(x + y.toInt(), 53) * 30f
                }
            }
            // Varnish shine.
            vgrad(0f, 0f, 100f, 190f, 0x10FFFFFF, 0, 0x18FFFFFF)
            // Painted key towards the hoop (top of the texture is the far end).
            rect(34f, 0f, 32f, 68f, Pal.withAlpha(Pal.RED, 0.33f))
            val line = Pal.withAlpha(Pal.WHITE, 0.95f)
            rect(33f, 0f, 1.6f, 69f, line)
            rect(65.4f, 0f, 1.6f, 69f, line)
            rect(33f, 67.4f, 34f, 1.6f, line)
            paint.reset()
            paint.isAntiAlias = true
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 1.6f
            paint.color = line
            canvas.drawArc(32f, 51f, 68f, 87f, 0f, 180f, false, paint)
        }
    }

    val backWall: Texture by lazy {
        paintTexture(130, 110, 3) {
            vgrad(0f, 0f, 130f, 110f, Pal.shade(Pal.PLUM, 0.7f), Pal.PLUM)
            for (x in 0 until 130 step 10) rect(x.toFloat(), 0f, 0.8f, 110f, Pal.shade(Pal.PLUM, 0.55f))
            rect(0f, 88f, 130f, 3f, Pal.ORANGE)
            rect(0f, 88f, 130f, 0.8f, Pal.mix(Pal.ORANGE, Pal.WHITE, 0.4f))
        }
    }

    val cabinet: Texture by lazy {
        paintTexture(16, 16) {
            vgrad(0f, 0f, 16f, 16f, Pal.RED, Pal.DARKRED)
            vgrad(0f, 0f, 16f, 2.5f, Pal.ORANGE, Pal.withAlpha(Pal.ORANGE, 0.3f))
        }
    }

    val post: Texture by lazy { paintTexture(8, 16) { vgrad(0f, 0f, 8f, 16f, Pal.LIGHTGRAY, Pal.GRAY) } }

    /** Clear backboard with the red target square. */
    val board: Texture by lazy {
        paintTexture(120, 85, 4) {
            vgrad(0f, 0f, 120f, 85f, Pal.mix(Pal.WHITE, Pal.SKY, 0.1f), Pal.mix(Pal.WHITE, Pal.SKY, 0.22f))
            strokeRound(2f, 2f, 116f, 81f, 2f, 4f, Pal.RED)
            // Inner square above the rim.
            paint.reset()
            paint.isAntiAlias = true
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = Pal.RED
            canvas.drawRect(41.5f, 39.5f, 78.5f, 66f, paint)
            // A glint across the glass.
            polygon(floatArrayOf(8f, 8f, 26f, 8f, 12f, 30f, 6f, 30f), Pal.withAlpha(Pal.WHITE, 0.5f))
        }
    }
    val boardEdge: Texture by lazy { TexKit.solid(8, 8, Pal.shade(Pal.RED, 0.8f)) }

    val rim: Texture by lazy {
        paintTexture(32, 4) { vgrad(0f, 0f, 32f, 4f, Pal.mix(Pal.ORANGE, Pal.YELLOW, 0.3f), Pal.shade(Pal.ORANGE, 0.7f)) }
    }

    /** Diamond mesh for the cage nets (alpha-blended). */
    val net: Texture by lazy {
        paintTexture(64, 64, 4) {
            clear(0)
            val c = Pal.withAlpha(Pal.LAVENDER, 0.5f)
            for (k in -4..8) {
                val o = k * 16f
                line(o, 0f, o + 64f, 64f, 0.7f, c)
                line(o, 64f, o + 64f, 0f, 0.7f, c)
            }
        }
    }

    /** The basketball: pebbled orange leather with black seams. */
    fun ball(radius: Float): Model {
        val tex = paintTexture(128, 64, 4) {
            vgrad(0f, 0f, 128f, 64f, Pal.mix(Pal.ORANGE, Pal.YELLOW, 0.15f), Pal.shade(Pal.ORANGE, 0.85f))
            for (i in 0 until 900) {
                circle(hash01(i, 61) * 128f, hash01(i, 62) * 64f, 0.35f, Pal.withAlpha(Pal.shade(Pal.ORANGE, 0.7f), 0.6f))
            }
            val seam = 0xFF2A140A.toInt()
            rect(0f, 31.3f, 128f, 1.4f, seam)
            rect(0f, 0f, 1.4f, 64f, seam)
            rect(63.3f, 0f, 1.4f, 64f, seam)
            // The two curved seams, bowing round the ball.
            for (centre in floatArrayOf(32f, 96f)) {
                var prevX = 0f
                var prevY = 0f
                for (k in 0..32) {
                    val v = k / 32f
                    val x = centre + (if (centre < 64f) 1f else -1f) * 14f * sin(v * PI.toFloat())
                    val y = v * 64f
                    if (k > 0) line(prevX, prevY, x, y, 1.4f, seam)
                    prevX = x
                    prevY = y
                }
            }
        }
        return ModelBuilder().sphere(0f, 0f, 0f, radius, tex.full, slices = 22, stacks = 14, gloss = 0.35f).build()
    }
}
