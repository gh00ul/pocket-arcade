package com.pocketarcade.games.coinpusher

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture

/** Painted art for the 3D coin pusher. */
internal object PusherArt {
    /** A coin face seen from above: milled rim, face, an emboss and a glint. */
    private fun coinFace(n: Int, face: Int, rim: Int, mark: Boolean): Texture = paintTexture(n, n, 8) {
        clear(0)
        val m = n / 2f
        circle(m, m, m - 0.3f, rim)
        for (k in 0 until 40) {
            val a = k * (2f * Math.PI.toFloat() / 40f)
            line(m + kotlin.math.cos(a) * (m - 1.6f), m + kotlin.math.sin(a) * (m - 1.6f), m + kotlin.math.cos(a) * (m - 0.4f), m + kotlin.math.sin(a) * (m - 0.4f), 0.25f, Pal.shade(rim, 0.75f))
        }
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = android.graphics.RadialGradient(m - m * 0.3f, m - m * 0.35f, m * 1.3f, Pal.mix(face, Pal.WHITE, 0.35f), Pal.shade(face, 0.85f), android.graphics.Shader.TileMode.CLAMP)
        canvas.drawCircle(m, m, m - 2f, paint)
        paint.shader = null
        ring(m, m, m - 3.6f, 0.6f, Pal.shade(face, 0.8f))
        if (mark) {
            star(m + 0.3f, m + 0.4f, m * 0.42f, Pal.shade(face, 0.72f))
            star(m, m, m * 0.42f, Pal.mix(face, Pal.WHITE, 0.2f))
        } else {
            oval(m + 0.3f, m + 0.4f, m * 0.25f, m * 0.3f, Pal.shade(face, 0.75f))
            oval(m, m, m * 0.25f, m * 0.3f, Pal.mix(face, Pal.WHITE, 0.15f))
        }
        oval(m - m * 0.45f, m - m * 0.45f, m * 0.18f, m * 0.08f, Pal.withAlpha(Pal.WHITE, 0.8f))
    }

    val coin: Texture by lazy { coinFace(24, Pal.GOLD, Pal.ORANGE, mark = false) }
    val coinEdge: Texture by lazy {
        paintTexture(24, 24, 4) {
            clear(0)
            circle(12f, 12f, 11.6f, Pal.shade(Pal.ORANGE, 0.6f))
        }
    }
    val bigCoin: Texture by lazy { coinFace(32, Pal.YELLOW, Pal.ORANGE, mark = true) }

    /** A cut gem: facets in two blues with a sparkle. */
    val gem: Texture by lazy {
        paintTexture(12, 12, 12) {
            clear(0)
            polygon(floatArrayOf(2f, 4f, 6f, 11.5f, 10f, 4f), Pal.SKY)
            polygon(floatArrayOf(2f, 4f, 6f, 11.5f, 6f, 4f), Pal.shade(Pal.SKY, 0.8f))
            polygon(floatArrayOf(3.5f, 1f, 8.5f, 1f, 10f, 4f, 2f, 4f), Pal.CYAN)
            polygon(floatArrayOf(5f, 1f, 7f, 1f, 7.5f, 4f, 4.5f, 4f), Pal.mix(Pal.CYAN, Pal.WHITE, 0.5f))
            line(2f, 4f, 10f, 4f, 0.25f, Pal.WHITE)
            star(4f, 2.4f, 1.3f, Pal.WHITE, 0.3f)
        }
    }

    val tickets: Texture by lazy {
        paintTexture(20, 14, 8) {
            clear(0)
            round(0f, 3f, 20f, 11f, 1f, Pal.ORANGE)
            round(0f, 0f, 20f, 11f, 1f, Pal.GOLD)
            strokeRound(0.4f, 0.4f, 19.2f, 10.2f, 1f, 0.6f, Pal.ORANGE)
            for (x in 4 until 20 step 5) line(x.toFloat(), 1.5f, x.toFloat(), 9.5f, 0.3f, Pal.shade(Pal.GOLD, 0.75f))
            star(2.3f, 5.5f, 1.4f, Pal.DARKRED)
        }
    }

    val star: Texture by lazy {
        paintTexture(14, 14, 10) {
            clear(0)
            glow(0.8f, Pal.withAlpha(Pal.ORANGE, 0.9f)) { star(7f, 7.4f, 6.4f, -1) }
            star(7f, 7.4f, 6.2f, Pal.YELLOW)
            star(6.6f, 6.8f, 3f, Pal.mix(Pal.YELLOW, Pal.WHITE, 0.6f))
        }
    }

    /** The deck: dark blue with lane stripes and arrows towards the lip. */
    fun deck(w: Int, h: Int): Texture = paintTexture(w, h, 3) {
        vgrad(0f, 0f, w.toFloat(), h.toFloat(), Pal.shade(Pal.NAVY, 0.8f), Pal.NAVY)
        for (y in 0 until h step 40) rect(0f, y.toFloat(), w.toFloat(), 1.5f, Pal.INDIGO)
        for (x in 0 until w step 50) rect(x.toFloat(), 0f, 0.8f, h.toFloat(), Pal.shade(Pal.INDIGO, 0.8f))
        for (i in 0 until 60) circle(hash01(i, 31) * w, hash01(i, 32) * h, 0.5f, Pal.shade(Pal.SKY, 0.5f))
        for (k in 0 until 3) {
            val cy = h - 26f - k * 10f
            for (i in 0 until 6) {
                val cx = 25f + i * 50f
                val c = Pal.withAlpha(Pal.shade(Pal.GOLD, 0.6f), 0.85f)
                line(cx - 6f, cy - 6f, cx, cy, 1.2f, c)
                line(cx, cy, cx + 6f, cy - 6f, 1.2f, c)
            }
        }
    }

    /** Pusher shelf front: hazard stripes. */
    val shelfFront: Texture by lazy {
        paintTexture(120, 14, 4) {
            fill(Pal.BLACK)
            var x = -14f
            while (x < 120f) {
                polygon(floatArrayOf(x, 14f, x + 7f, 14f, x + 21f, 0f, x + 14f, 0f), Pal.YELLOW)
                x += 14f
            }
            vgrad(0f, 0f, 120f, 2f, Pal.LIGHTGRAY, Pal.GRAY)
        }
    }
    val shelfTop: Texture by lazy {
        paintTexture(60, 40) {
            vgrad(0f, 0f, 60f, 40f, Pal.GRAY, Pal.LIGHTGRAY)
            for (y in 0 until 40 step 6) rect(0f, y.toFloat(), 60f, 0.6f, Pal.shade(Pal.GRAY, 0.75f))
        }
    }

    /** Back wall of the cabinet, with room for the coin counter. */
    val backWall: Texture by lazy {
        paintTexture(180, 130, 3) {
            vgrad(0f, 0f, 180f, 130f, Pal.shade(Pal.ORANGE, 0.8f), Pal.shade(Pal.DARKRED, 0.8f))
            for (i in 0 until 9) {
                val x = 10f + i * 20f
                hgrad(x, 0f, 8f, 130f, Pal.shade(Pal.ORANGE, 0.62f), Pal.shade(Pal.ORANGE, 0.75f), Pal.shade(Pal.ORANGE, 0.62f))
            }
            vgrad(0f, 120f, 180f, 10f, Pal.shade(Pal.DARKRED, 0.6f), Pal.shade(Pal.DARKRED, 0.4f))
            rect(0f, 0f, 180f, 4f, Pal.GOLD)
            glow(2f, Pal.withAlpha(Pal.YELLOW, 0.6f)) { label("COIN PUSHER", 90f, 8f, 13f, -1) }
            label("COIN PUSHER", 90f, 8f, 13f, Pal.YELLOW, shadow = Pal.DARKRED)
        }
    }

    val cabinet: Texture by lazy {
        paintTexture(32, 32) {
            vgrad(0f, 0f, 32f, 32f, Pal.ORANGE, Pal.shade(Pal.ORANGE, 0.6f))
            vgrad(0f, 0f, 32f, 3f, Pal.GOLD, Pal.withAlpha(Pal.GOLD, 0.4f))
        }
    }
    val gold: Texture by lazy { TexKit.solid(8, 8, Pal.GOLD) }

    /** The win tray: plum velvet with gold stripes. */
    val tray: Texture by lazy {
        paintTexture(64, 32) {
            vgrad(0f, 0f, 64f, 32f, Pal.PLUM, Pal.shade(Pal.PLUM, 0.7f))
            for (x in 0 until 64 step 8) rect(x.toFloat(), 0f, 0.6f, 32f, Pal.shade(Pal.GOLD, 0.5f))
            grain(0.05f, 3)
        }
    }

    /** The face below the lip that coins tumble past. */
    val lipFace: Texture by lazy {
        paintTexture(64, 16) {
            vgrad(0f, 0f, 64f, 16f, Pal.shade(Pal.ORANGE, 0.55f), Pal.shade(Pal.DARKRED, 0.5f))
            for (x in 0 until 64 step 16) round(x + 0f, 3f, 8f, 2f, 1f, Pal.YELLOW)
        }
    }
    val dark: Texture by lazy { TexKit.solid(8, 8, Pal.shade(Pal.NIGHT, 0.7f)) }
    val glass: Texture by lazy {
        paintTexture(32, 16) {
            fill(Pal.withAlpha(Pal.SKY, 0.16f))
            polygon(floatArrayOf(3f, 2f, 12f, 2f, 7f, 9f, 3f, 9f), Pal.withAlpha(Pal.WHITE, 0.3f))
            rect(0f, 0f, 32f, 0.6f, Pal.withAlpha(Pal.WHITE, 0.6f))
        }
    }

    /** Small LED panel repainted with the coins left / won counters. */
    class Panel(private val cols: Int, private val rows: Int) {
        private val painter by lazy { TexPaint(cols * 4, rows * 4).also { it.useUnits(4f) } }
        val tex = Texture(cols, rows, IntArray(cols * 4 * rows * 4), 4)
        private var last = ""

        fun paint(text: String, color: Int) {
            if (text == last) return
            last = text
            with(painter) {
                fill(0xFF06040A.toInt())
                for (y in 0 until rows step 2) rect(0f, y.toFloat(), cols.toFloat(), 0.6f, Pal.withAlpha(Pal.NIGHT, 0.7f))
                strokeRound(0.5f, 0.5f, cols - 1f, rows - 1f, 2f, 1f, Pal.shade(Pal.GOLD, 0.6f))
                val cap = 11f
                glow(1.5f, Pal.withAlpha(color, 0.8f)) { label(text, cols / 2f, (rows - cap) / 2f, cap, -1) }
                label(text, cols / 2f, (rows - cap) / 2f, cap, Pal.mix(color, Pal.WHITE, 0.2f))
                update(tex)
            }
        }
    }
}
