package com.pocketarcade.games.airhockey

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture

/** Painted art for the 3D air hockey table. */
internal object HockeyArt {
    /** The playing surface: ice-white with air holes, centre line, circles and goal creases. */
    fun surface(w: Int, h: Int, goalHalf: Float): Texture = paintTexture(w, h, 3) {
        vgrad(0f, 0f, w.toFloat(), h.toFloat(), Pal.mix(Pal.WHITE, Pal.SKY, 0.16f), Pal.mix(Pal.WHITE, Pal.SKY, 0.3f))
        for (y in 6 until h step 12) for (x in 6 until w step 12) {
            circle(x.toFloat(), y.toFloat(), 0.9f, Pal.shade(Pal.SKY, 0.72f))
        }
        val cx = w / 2f
        val cy = h / 2f
        rect(0f, cy - 2f, w.toFloat(), 4f, Pal.withAlpha(Pal.RED, 0.85f))
        ring(cx, cy, 44f, 3f, Pal.withAlpha(Pal.RED, 0.85f))
        circle(cx, cy, 6f, Pal.RED)
        // Goal creases (half circles at each end) and blue lines.
        for (end in 0..1) {
            val ey = if (end == 0) 0f else h.toFloat()
            ring(cx, ey, goalHalf + 14f, 3f, Pal.withAlpha(Pal.BLUE, 0.85f))
            val by = if (end == 0) h / 4f else h * 3f / 4f
            rect(0f, by - 2f, w.toFloat(), 4f, Pal.withAlpha(Pal.BLUE, 0.85f))
        }
        strokeRound(0.5f, 0.5f, w - 1f, h - 1f, 6f, 1.5f, Pal.shade(Pal.SKY, 0.6f))
    }

    val rail: Texture by lazy {
        paintTexture(16, 16) {
            vgrad(0f, 0f, 16f, 16f, Pal.mix(Pal.SKY, Pal.WHITE, 0.3f), Pal.BLUE)
            vgrad(0f, 0f, 16f, 2.5f, Pal.WHITE, Pal.withAlpha(Pal.WHITE, 0.2f))
        }
    }

    val body: Texture by lazy {
        paintTexture(64, 32) {
            vgrad(0f, 0f, 64f, 32f, Pal.shade(Pal.BLUE, 0.8f), Pal.shade(Pal.NAVY, 0.6f))
            for (x in 0 until 64 step 16) round(x + 6f, 4f, 4f, 24f, 2f, Pal.shade(Pal.CYAN, 0.45f))
            rect(0f, 0f, 64f, 2f, Pal.CYAN)
        }
    }

    val slot: Texture by lazy { TexKit.solid(8, 8, Pal.BLACK) }

    val post: Texture by lazy { paintTexture(16, 16) { vgrad(0f, 0f, 16f, 16f, Pal.DARKGRAY, Pal.BLACK) } }

    val puckSide: Texture by lazy {
        paintTexture(32, 5) {
            fill(Pal.shade(Pal.RED, 0.7f))
            vgrad(0f, 0f, 32f, 1.5f, Pal.RED, Pal.shade(Pal.RED, 0.7f))
        }
    }
    val puckTop: Texture by lazy {
        paintTexture(16, 16, 8) {
            radial(6f, 5.5f, 11f, Pal.mix(Pal.RED, Pal.WHITE, 0.25f), Pal.shade(Pal.RED, 0.8f))
            ring(8f, 8f, 6f, 0.8f, Pal.shade(Pal.RED, 0.55f))
            circle(8f, 8f, 2f, Pal.YELLOW)
        }
    }

    fun malletSide(color: Int): Texture = paintTexture(32, 8) {
        vgrad(0f, 0f, 32f, 8f, Pal.mix(color, Pal.WHITE, 0.25f), Pal.shade(color, 0.6f))
    }
    fun malletTop(color: Int): Texture = paintTexture(16, 16, 8) {
        radial(6f, 5.5f, 11f, Pal.mix(color, Pal.WHITE, 0.2f), color)
        ring(8f, 8f, 7f, 0.8f, Pal.mix(color, Pal.WHITE, 0.4f))
    }
    fun knob(color: Int): Texture = paintTexture(8, 8, 8) {
        radial(3f, 3f, 6f, Pal.mix(color, Pal.WHITE, 0.55f), Pal.mix(color, Pal.WHITE, 0.2f))
    }

    /** LED scoreboard, repainted when the score changes. */
    class Scoreboard {
        private val painter by lazy { TexPaint(96 * 4, 30 * 4).also { it.useUnits(4f) } }
        val tex = Texture(96, 30, IntArray(96 * 4 * 30 * 4), 4)
        private var last = ""

        fun paint(you: Int, cpu: Int, flash: Boolean) {
            val key = "$you:$cpu:$flash"
            if (key == last) return
            last = key
            with(painter) {
                fill(if (flash) Pal.shade(Pal.CYAN, 0.35f) else 0xFF06040A.toInt())
                for (y in 0 until 30 step 2) rect(0f, y.toFloat(), 96f, 0.6f, Pal.withAlpha(Pal.NIGHT, 0.6f))
                strokeRound(0.5f, 0.5f, 95f, 29f, 2f, 1f, Pal.SKY)
                label("YOU", 24f, 3.5f, 6f, Pal.CYAN, tiny = true)
                label("CPU", 72f, 3.5f, 6f, Pal.PINK, tiny = true)
                glow(1.5f, Pal.withAlpha(Pal.YELLOW, 0.7f)) {
                    label(you.toString(), 24f, 13f, 13f, -1)
                    label(cpu.toString(), 72f, 13f, 13f, -1)
                }
                label(you.toString(), 24f, 13f, 13f, Pal.YELLOW)
                label(cpu.toString(), 72f, 13f, 13f, Pal.YELLOW)
                round(45f, 18.5f, 6f, 2f, 1f, Pal.WHITE)
                update(tex)
            }
        }
    }
}
