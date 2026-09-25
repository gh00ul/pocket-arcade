package com.pocketarcade.games.airhockey

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.bigText
import com.pocketarcade.engine.r3d.bigTextCentered
import com.pocketarcade.engine.r3d.bigTextWidth
import com.pocketarcade.engine.r3d.vgrad

/** Procedural art for the 3D air hockey table. */
internal object HockeyArt {
    /** The playing surface: ice-white with air holes, centre line, circles and goal creases. */
    fun surface(w: Int, h: Int, goalHalf: Float): Texture {
        val c = PixelCanvas(w, h)
        c.vgrad(0, 0, w, h, Pal.mix(Pal.WHITE, Pal.SKY, 0.18f), Pal.mix(Pal.WHITE, Pal.SKY, 0.3f))
        for (y in 6 until h step 12) for (x in 6 until w step 12) {
            c.set(x, y, Pal.shade(Pal.SKY, 0.75f))
        }
        val cx = w / 2f
        val cy = h / 2f
        c.fill(0, (cy - 2).toInt(), w, 4, Pal.RED)
        c.ring(cx, cy, 44f, 3f, Pal.RED)
        c.disc(cx, cy, 6f, Pal.RED)
        // Goal creases (half circles at each end) and blue lines.
        for (end in 0..1) {
            val ey = if (end == 0) 0f else h.toFloat()
            c.ring(cx, ey, goalHalf + 14f, 3f, Pal.BLUE)
            val by = if (end == 0) h / 4 else h * 3 / 4
            c.fill(0, by - 2, w, 4, Pal.BLUE)
        }
        c.rect(0, 0, w, h, Pal.shade(Pal.SKY, 0.6f))
        return Texture.of(c)
    }

    val rail: Texture by lazy {
        val c = PixelCanvas(16, 16)
        c.vgrad(0, 0, c.w, c.h, Pal.mix(Pal.SKY, Pal.WHITE, 0.3f), Pal.BLUE)
        c.fill(0, 0, c.w, 2, Pal.WHITE)
        Texture.of(c)
    }

    val body: Texture by lazy {
        val c = PixelCanvas(64, 32)
        c.vgrad(0, 0, c.w, c.h, Pal.shade(Pal.BLUE, 0.8f), Pal.shade(Pal.NAVY, 0.6f))
        for (x in 0 until c.w step 16) c.fill(x + 6, 4, 4, c.h - 8, Pal.shade(Pal.CYAN, 0.45f))
        c.fill(0, 0, c.w, 2, Pal.CYAN)
        Texture.of(c)
    }

    val slot: Texture by lazy { TexKit.solid(8, 8, Pal.BLACK) }

    val post: Texture by lazy {
        val c = PixelCanvas(16, 16)
        c.vgrad(0, 0, c.w, c.h, Pal.DARKGRAY, Pal.BLACK)
        Texture.of(c)
    }

    val puckSide: Texture by lazy {
        val c = PixelCanvas(32, 5)
        c.fill(0, 0, c.w, c.h, Pal.shade(Pal.RED, 0.7f))
        c.fill(0, 0, c.w, 1, Pal.RED)
        Texture.of(c)
    }
    val puckTop: Texture by lazy {
        val c = PixelCanvas(16, 16)
        c.disc(8f, 8f, 8f, Pal.RED)
        c.ring(8f, 8f, 6f, 1f, Pal.shade(Pal.RED, 0.6f))
        c.disc(8f, 8f, 2f, Pal.YELLOW)
        c.set(5, 4, Pal.WHITE)
        Texture.of(c)
    }

    fun malletSide(color: Int): Texture {
        val c = PixelCanvas(32, 8)
        c.vgrad(0, 0, c.w, c.h, Pal.mix(color, Pal.WHITE, 0.25f), Pal.shade(color, 0.6f))
        return Texture.of(c)
    }
    fun malletTop(color: Int): Texture {
        val c = PixelCanvas(16, 16)
        c.disc(8f, 8f, 8f, color)
        c.ring(8f, 8f, 7f, 1f, Pal.mix(color, Pal.WHITE, 0.4f))
        return Texture.of(c)
    }
    fun knob(color: Int): Texture {
        val c = PixelCanvas(8, 8)
        c.disc(4f, 4f, 4f, Pal.mix(color, Pal.WHITE, 0.3f))
        c.set(2, 2, Pal.WHITE)
        return Texture.of(c)
    }

    /** LED scoreboard, repainted when the score changes. */
    class Scoreboard {
        val canvas = PixelCanvas(96, 30)
        val tex = Texture.of(canvas)
        private var last = ""

        fun paint(you: Int, cpu: Int, flash: Boolean) {
            val key = "$you:$cpu:$flash"
            if (key == last) return
            last = key
            canvas.fill(0, 0, canvas.w, canvas.h, if (flash) Pal.shade(Pal.CYAN, 0.35f) else Pal.BLACK)
            for (y in 0 until canvas.h step 2) canvas.fill(0, y, canvas.w, 1, Pal.shade(Pal.NIGHT, 1.2f))
            canvas.rect(0, 0, canvas.w, canvas.h, Pal.SKY)
            canvas.bigTextCentered("YOU", 24, 3, Pal.CYAN, 1)
            canvas.bigTextCentered("CPU", 72, 3, Pal.PINK, 1)
            canvas.bigText(you.toString(), 24 - bigTextWidth(you.toString(), 2) / 2, 13, Pal.YELLOW, 2)
            canvas.bigText(cpu.toString(), 72 - bigTextWidth(cpu.toString(), 2) / 2, 13, Pal.YELLOW, 2)
            canvas.fill(46, 18, 4, 2, Pal.WHITE)
        }
    }
}
