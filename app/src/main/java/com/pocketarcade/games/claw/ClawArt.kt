package com.pocketarcade.games.claw

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture

/** Painted art for the 3D claw machine. */
internal object ClawArt {
    /** A night sky of soft glowing stars. */
    private fun TexPaint.starry(w: Int, h: Int, top: Int, bottom: Int, seed: Int) {
        vgrad(0f, 0f, w.toFloat(), h.toFloat(), top, bottom)
        for (i in 0 until w * h / 160) {
            val x = hash01(i, seed) * w
            val y = hash01(i, seed + 1) * h
            val col = if (i % 5 == 0) Pal.HOTPINK else Pal.LAVENDER
            val b = 0.55f + hash01(i, seed + 2) * 0.45f
            if (i % 7 == 0) {
                radial(x, y, 3.2f, Pal.withAlpha(col, 0.5f * b), 0)
                line(x - 2.2f, y, x + 2.2f, y, 0.35f, Pal.shade(col, b))
                line(x, y - 2.2f, x, y + 2.2f, 0.35f, Pal.shade(col, b))
            } else {
                circle(x, y, 0.35f + hash01(i, seed + 3) * 0.3f, Pal.shade(col, b))
            }
        }
    }

    private fun TexPaint.heart(cx: Float, cy: Float, s: Float, color: Int) {
        val p = android.graphics.Path()
        p.moveTo(cx, cy + s * 0.95f)
        p.cubicTo(cx - s * 1.25f, cy + s * 0.1f, cx - s * 0.7f, cy - s * 0.95f, cx, cy - s * 0.35f)
        p.cubicTo(cx + s * 0.7f, cy - s * 0.95f, cx + s * 1.25f, cy + s * 0.1f, cx, cy + s * 0.95f)
        p.close()
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color
        canvas.drawPath(p, paint)
    }

    val backWall: Texture by lazy {
        paintTexture(170, 250) {
            starry(170, 250, Pal.INDIGO, Pal.PLUM, 3)
            // Big soft hearts.
            for (k in 0 until 6) {
                val cx = 20f + (k % 3) * 65f
                val cy = 44f + (k / 3) * 110f + (k % 2) * 20f
                glow(4f, Pal.withAlpha(Pal.HOTPINK, 0.35f)) { heart(cx, cy, 13f, -1) }
                heart(cx, cy, 12f, Pal.shade(Pal.PINK, 0.4f))
            }
        }
    }

    val sideWall: Texture by lazy {
        paintTexture(60, 250) { starry(60, 250, Pal.shade(Pal.INDIGO, 0.8f), Pal.shade(Pal.PLUM, 0.8f), 7) }
    }

    val floor: Texture by lazy {
        paintTexture(96, 32) {
            fill(Pal.VIOLET)
            for (y in 0 until 32 step 8) for (x in 0 until 96 step 8) {
                if ((x / 8 + y / 8) % 2 == 0) rect(x.toFloat(), y.toFloat(), 8f, 8f, Pal.shade(Pal.VIOLET, 0.85f))
            }
            for (x in 0..96 step 8) rect(x - 0.15f, 0f, 0.3f, 32f, Pal.shade(Pal.VIOLET, 0.7f))
            for (y in 0..32 step 8) rect(0f, y - 0.15f, 96f, 0.3f, Pal.shade(Pal.VIOLET, 0.7f))
        }
    }

    val cabinet: Texture by lazy {
        paintTexture(32, 32) {
            vgrad(0f, 0f, 32f, 32f, Pal.PINK, Pal.shade(Pal.PINK, 0.65f))
            vgrad(0f, 0f, 32f, 2f, Pal.mix(Pal.HOTPINK, Pal.WHITE, 0.3f), Pal.HOTPINK)
            grain(0.03f, 5)
        }
    }

    val marquee: Texture by lazy {
        paintTexture(180, 36) {
            vgrad(0f, 0f, 180f, 36f, Pal.HOTPINK, Pal.shade(Pal.PINK, 0.6f))
            rect(0f, 0f, 180f, 3f, Pal.YELLOW)
            rect(0f, 33f, 180f, 3f, Pal.YELLOW)
            glow(2.5f, Pal.withAlpha(Pal.YELLOW, 0.7f)) { label("CLAW MACHINE", 90f, 11f, 14f, -1) }
            label("CLAW MACHINE", 90f, 11f, 14f, Pal.YELLOW, shadow = Pal.shade(Pal.DARKRED, 0.6f))
        }
    }

    val base: Texture by lazy {
        paintTexture(180, 60) {
            vgrad(0f, 0f, 180f, 60f, Pal.shade(Pal.PINK, 0.8f), Pal.shade(Pal.DARKRED, 0.6f))
            rect(0f, 0f, 180f, 3f, Pal.YELLOW)
            for (x in 0 until 180 step 12) round(x + 4f, 10f, 4f, 44f, 2f, Pal.shade(Pal.PINK, 0.6f))
        }
    }

    private fun metalTex(top: Int, bottom: Int, shine: Boolean) = paintTexture(16, 16) {
        vgrad(0f, 0f, 16f, 16f, top, bottom)
        if (shine) vgrad(0f, 0f, 16f, 3f, Pal.WHITE, Pal.withAlpha(Pal.WHITE, 0f))
    }

    val metal: Texture by lazy { metalTex(Pal.LIGHTGRAY, Pal.GRAY, true) }
    val darkMetal: Texture by lazy { metalTex(Pal.GRAY, Pal.DARKGRAY, false) }
    val goldMetal: Texture by lazy { metalTex(Pal.YELLOW, Pal.ORANGE, true) }

    val glass: Texture by lazy { paintTexture(16, 16, 1) { fill(Pal.withAlpha(Pal.LAVENDER, 0.07f)) } }

    /** Diagonal glare streaks on the front glass (drawn additively). */
    val glare: Texture by lazy {
        paintTexture(64, 64) {
            clear(0)
            for (off in intArrayOf(-64, 0, 64)) {
                val p = floatArrayOf(off + 0f, 64f, off + 5f, 64f, off + 5f + 38f, 0f, off + 38f, 0f)
                polygon(p, Pal.withAlpha(Pal.WHITE, 0.2f))
                val q = floatArrayOf(off + 10f, 64f, off + 12f, 64f, off + 12f + 38f, 0f, off + 10f + 38f, 0f)
                polygon(q, Pal.withAlpha(Pal.WHITE, 0.14f))
            }
        }
    }

    val lipGlass: Texture by lazy {
        paintTexture(16, 32) {
            fill(Pal.withAlpha(Pal.SKY, 0.22f))
            vgrad(0f, 0f, 16f, 3f, Pal.withAlpha(Pal.WHITE, 0.85f), Pal.withAlpha(Pal.WHITE, 0.1f))
        }
    }

    val winSign: Texture by lazy {
        paintTexture(40, 18, 8) {
            fill(0xFF0A0610.toInt())
            strokeRound(0.6f, 0.6f, 38.8f, 16.8f, 2f, 1.2f, Pal.GOLD)
            glow(1.5f, Pal.withAlpha(Pal.YELLOW, 0.8f)) { label("WIN", 20f, 4.5f, 9f, -1) }
            label("WIN", 20f, 4.5f, 9f, Pal.YELLOW)
        }
    }

    val neon: Texture by lazy { TexKit.solid(8, 8, Pal.HOTPINK) }
    val pit: Texture by lazy { TexKit.solid(8, 8, Pal.BLACK) }
}
