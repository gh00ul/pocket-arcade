package com.pocketarcade.games.claw

import androidx.compose.ui.graphics.ImageBitmap
import com.pocketarcade.data.Plush
import com.pocketarcade.data.PlushShape
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas

/** Pixel-art plush sprites, generated from each plush's shape and colours. */
object PlushArt {
    const val SIZE = 18
    private val cache = HashMap<String, ImageBitmap>()

    fun image(p: Plush): ImageBitmap = cache.getOrPut(p.id) { build(p).toImageBitmap() }

    fun build(p: Plush): PixelCanvas {
        val c = PixelCanvas(SIZE, SIZE)
        val m = p.main
        val a = p.accent
        val shadow = Pal.shade(m, 0.72f)
        val ink = Pal.BLACK
        val blush = Pal.HOTPINK
        fun eyes(y: Int, lx: Int = 6, rx: Int = 11) {
            c.set(lx, y, ink); c.set(lx, y + 1, ink)
            c.set(rx, y, ink); c.set(rx, y + 1, ink)
            c.set(lx, y - 1, Pal.WHITE); c.set(rx, y - 1, Pal.WHITE)
        }
        when (p.shape) {
            PlushShape.BEAR -> {
                c.disc(4.5f, 4.5f, 2.6f, m); c.disc(13.5f, 4.5f, 2.6f, m)
                c.disc(4.5f, 4.5f, 1.2f, a); c.disc(13.5f, 4.5f, 1.2f, a)
                c.disc(9f, 10f, 6.6f, m)
                c.fill(3, 14, 12, 2, shadow)
                c.ellipse(9f, 12.5f, 3f, 2f, a)
                c.fill(8, 11, 2, 1, ink)
                eyes(8)
                c.set(4, 11, blush); c.set(13, 11, blush)
            }
            PlushShape.SLIME -> {
                c.ellipse(9f, 11f, 7.5f, 6.5f, m)
                c.fill(2, 13, 14, 4, m)
                c.fill(2, 16, 14, 1, a)
                c.fill(4, 6, 2, 1, Pal.WHITE); c.set(4, 7, Pal.WHITE)
                eyes(10)
                c.fill(8, 13, 2, 1, ink)
            }
            PlushShape.CAT -> {
                for (i in 0 until 4) {
                    c.hline(3, 3 + i, 2 + i, m); c.hline(14 - i, 14, 2 + i, m)
                }
                c.set(4, 4, a); c.set(13, 4, a)
                c.disc(9f, 10.5f, 6.4f, m)
                c.fill(4, 8, 10, 3, a)
                c.set(6, 9, Pal.WHITE); c.set(11, 9, Pal.WHITE)
                c.set(6, 8, ink); c.set(11, 8, ink)
                c.set(9, 12, blush)
                c.hline(1, 3, 12, ink); c.hline(14, 16, 12, ink)
                c.fill(4, 15, 10, 1, shadow)
            }
            PlushShape.DUCK -> {
                c.disc(9f, 10f, 6.6f, m)
                c.fill(8, 2, 2, 2, m); c.set(10, 2, m)
                c.ellipse(9f, 12.5f, 3.2f, 1.6f, a)
                c.hline(7, 11, 13, Pal.shade(a, 0.7f))
                c.ellipse(4.5f, 12f, 1.5f, 2.5f, shadow)
                c.ellipse(13.5f, 12f, 1.5f, 2.5f, shadow)
                eyes(8)
            }
            PlushShape.GHOST -> {
                c.disc(9f, 8f, 6.2f, m)
                c.fill(3, 8, 12, 7, m)
                for (x in 3..14) if (x % 3 != 0) c.set(x, 15, m)
                c.fill(5, 7, 2, 3, ink); c.fill(11, 7, 2, 3, ink)
                c.set(5, 7, Pal.WHITE); c.set(11, 7, Pal.WHITE)
                c.set(4, 11, a); c.set(13, 11, a)
                c.fill(8, 11, 2, 2, ink)
            }
            PlushShape.DINO -> {
                c.disc(9f, 11f, 6.4f, m)
                c.fill(5, 3, 1, 2, a); c.fill(4, 4, 3, 1, a)
                c.fill(9, 2, 1, 2, a); c.fill(8, 3, 3, 1, a)
                c.fill(13, 3, 1, 2, a); c.fill(12, 4, 3, 1, a)
                c.ellipse(9f, 13.5f, 4f, 2.5f, Pal.mix(m, Pal.YELLOW, 0.5f))
                eyes(9)
                c.hline(7, 10, 12, ink)
                c.fill(4, 16, 3, 1, shadow); c.fill(11, 16, 3, 1, shadow)
            }
            PlushShape.OCTO -> {
                c.disc(9f, 8f, 6.4f, m)
                for (i in 0 until 4) {
                    val x = 3 + i * 3
                    c.fill(x, 12, 2, 4, m)
                    c.set(x + (if (i % 2 == 0) -1 else 2), 16, m)
                }
                c.set(6, 4, a); c.set(12, 5, a); c.set(9, 3, a)
                eyes(8)
                c.fill(8, 11, 2, 1, ink)
            }
            PlushShape.BUNNY -> {
                c.fill(5, 0, 3, 8, m); c.fill(10, 0, 3, 8, m)
                c.fill(6, 1, 1, 6, a); c.fill(11, 1, 1, 6, a)
                c.disc(9f, 11f, 6f, m)
                eyes(10)
                c.set(8, 12, a); c.set(9, 12, a)
                c.set(4, 12, blush); c.set(13, 12, blush)
                c.fill(5, 16, 8, 1, shadow)
            }
            PlushShape.FROG -> {
                c.ellipse(9f, 11.5f, 7.6f, 5.4f, m)
                c.disc(5f, 6.5f, 2.6f, m); c.disc(13f, 6.5f, 2.6f, m)
                c.disc(5f, 6.5f, 1.5f, Pal.WHITE); c.disc(13f, 6.5f, 1.5f, Pal.WHITE)
                c.set(5, 6, ink); c.set(13, 6, ink)
                c.fill(7, 3, 5, 2, a); c.set(7, 2, a); c.set(9, 2, a); c.set(11, 2, a)
                c.hline(6, 12, 13, ink); c.set(5, 12, ink); c.set(13, 12, ink)
                c.set(4, 11, blush); c.set(14, 11, blush)
            }
            PlushShape.WHALE -> {
                c.ellipse(8.5f, 11f, 7.8f, 5.6f, m)
                c.fill(15, 7, 2, 3, m); c.set(17, 6, m); c.set(16, 6, m)
                c.ellipse(8.5f, 13.6f, 6f, 2.4f, a)
                c.set(8, 3, Pal.CYAN); c.set(7, 2, Pal.CYAN); c.set(9, 2, Pal.CYAN); c.set(8, 4, Pal.CYAN)
                c.fill(4, 9, 1, 2, ink); c.set(4, 8, Pal.WHITE)
                c.set(3, 12, blush)
            }
        }
        if (p.rare) {
            c.set(2, 2, Pal.WHITE); c.set(15, 1, Pal.WHITE); c.set(16, 14, Pal.WHITE)
        }
        c.outline(Pal.BLACK)
        return c
    }
}
