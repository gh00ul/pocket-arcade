package com.pocketarcade.hub

import androidx.compose.ui.graphics.ImageBitmap
import com.pocketarcade.data.DecorStyle
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.engine.SpriteFX
import com.pocketarcade.engine.r3d.Texture

/**
 * Pixel art for the buyable decorations and the potted plants. Each piece is drawn once at 1x;
 * shop previews use it outlined as-is, the 3D hall uses the Scale2x + bevel "HD" version.
 */
object DecorArt {
    /** Neon flamingo outline, shared by the sign's backing art and its live glow. */
    val FLAMINGO_ROWS = arrayOf(
        "......####..........",
        ".....#....#.........",
        ".....#.##..#........",
        "......#..##.........",
        "..........#.........",
        ".........#..........",
        "........#...........",
        ".......#............",
        "......#.....####....",
        ".....#....##....##..",
        "....#....#........#.",
        "....#...#..........#",
        ".....#..#.........#.",
        "......#..#.......#..",
        ".......##.#####.#...",
        "...........#.##.....",
        "...........#........",
        "...........#........",
        "...........#........",
        "..........###.......",
        "...........#........",
        "...........#........",
        "..........##........",
    )

    private val previews = HashMap<DecorStyle, ImageBitmap>()
    private val hdTextures = HashMap<DecorStyle, Texture>()

    /** 1x outlined image for menus. */
    fun preview(style: DecorStyle): ImageBitmap = previews.getOrPut(style) {
        canvas(style).also { it.outline(Pal.BLACK) }.toImageBitmap()
    }

    /** 2x shaded texture for billboards and box fronts in the 3D hall. */
    fun hd(style: DecorStyle): Texture = hdTextures.getOrPut(style) { Texture.of(SpriteFX.hd(SpriteFX.pad(canvas(style), 1))) }

    val plantHd: Texture by lazy { Texture.of(SpriteFX.hd(SpriteFX.pad(plant(), 1))) }

    /** 2x neon flamingo outline for the additive glow. */
    val flamingoNeon: Texture by lazy {
        val c = PixelCanvas(20, FLAMINGO_ROWS.size)
        c.sprite(FLAMINGO_ROWS, 0, 0, mapOf('#' to Pal.HOTPINK))
        val big = SpriteFX.scale2x(c)
        Texture.of(big)
    }

    fun plant(): PixelCanvas {
        val c = PixelCanvas(14, 22)
        c.fill(3, 14, 8, 8, Pal.BROWN)
        c.fill(2, 14, 10, 2, Pal.TAN)
        c.disc(7f, 9f, 6f, Pal.GREEN)
        c.disc(4f, 7f, 3f, Pal.DARKGREEN)
        c.disc(10f, 6f, 3f, Pal.LIME)
        c.set(7, 3, Pal.LIME)
        return c
    }

    /** The un-outlined 1x drawing of a decoration. */
    fun canvas(style: DecorStyle): PixelCanvas = when (style) {
        DecorStyle.PALM -> PixelCanvas(22, 32).apply {
            fill(7, 24, 8, 8, Pal.ORANGE)
            fill(6, 24, 10, 2, Pal.TAN)
            for (y in 10 until 25) set(10 + (if (y % 4 < 2) 0 else 1), y, if (y % 3 == 0) Pal.DARKBROWN else Pal.BROWN)
            fill(10, 12, 2, 12, Pal.BROWN)
            val fronds = listOf(-8 to -3, -5 to -6, 0 to -8, 5 to -6, 8 to -3, -9 to 1, 9 to 1)
            for ((dx, dy) in fronds) line(11, 11, 11 + dx, 11 + dy, Pal.GREEN)
            for ((dx, dy) in fronds) set(11 + dx, 12 + dy, Pal.DARKGREEN)
            disc(11f, 11f, 2f, Pal.LIME)
        }
        DecorStyle.LAVA_LAMP -> PixelCanvas(16, 32).apply {
            fill(0, 23, 16, 3, Pal.WOOD)
            fill(1, 26, 2, 6, Pal.DARKBROWN)
            fill(13, 26, 2, 6, Pal.DARKBROWN)
            fill(5, 17, 6, 6, Pal.GRAY)
            fill(4, 21, 8, 2, Pal.LIGHTGRAY)
            fill(5, 4, 6, 13, Pal.PLUM)
            disc(8f, 7f, 1.6f, Pal.ORANGE)
            disc(7.5f, 13f, 2f, Pal.ORANGE)
            fill(6, 2, 4, 2, Pal.GRAY)
        }
        DecorStyle.FLAMINGO -> PixelCanvas(22, 36).apply {
            fill(0, 0, 22, 26, Pal.NIGHT)
            rect(0, 0, 22, 26, Pal.DARKGRAY)
            sprite(FLAMINGO_ROWS, 1, 1, mapOf('#' to Pal.shade(Pal.PINK, 0.55f)))
            fill(10, 26, 2, 8, Pal.GRAY)
            fill(6, 33, 10, 3, Pal.DARKGRAY)
        }
        DecorStyle.GUMBALL -> PixelCanvas(16, 28).apply {
            fill(4, 16, 8, 12, Pal.RED)
            fill(3, 16, 10, 2, Pal.HOTPINK)
            fill(7, 20, 2, 2, Pal.GOLD)
            fill(6, 24, 4, 2, Pal.BLACK)
            disc(8f, 9f, 6.5f, Pal.mix(Pal.SKY, Pal.WHITE, 0.6f))
            val dots = intArrayOf(Pal.RED, Pal.YELLOW, Pal.LIME, Pal.PINK, Pal.SKY, Pal.ORANGE)
            var k = 0
            for (y in 6..13 step 2) for (x in 4..12 step 2) {
                if ((x - 8) * (x - 8) + (y - 9) * (y - 9) < 30) set(x, y, dots[k++ % dots.size])
            }
            fill(5, 2, 6, 2, Pal.RED)
            set(5, 5, Pal.WHITE)
        }
        DecorStyle.FISH_TANK -> PixelCanvas(36, 30).apply {
            fill(0, 18, 36, 12, Pal.DARKBROWN)
            fill(0, 18, 36, 1, Pal.BROWN)
            fill(1, 2, 34, 16, Pal.mix(Pal.BLUE, Pal.SKY, 0.4f))
            fill(1, 2, 34, 2, Pal.SKY)
            fill(2, 15, 32, 2, Pal.TAN)
            vline(6, 9, 14, Pal.GREEN); vline(7, 11, 14, Pal.DARKGREEN)
            vline(28, 8, 14, Pal.GREEN); vline(29, 10, 14, Pal.LIME)
            rect(0, 1, 36, 18, Pal.LIGHTGRAY)
        }
        DecorStyle.JUKEBOX -> PixelCanvas(26, 36).apply {
            disc(13f, 10f, 11f, Pal.PURPLE)
            fill(2, 10, 22, 26, Pal.PURPLE)
            disc(13f, 10f, 8f, Pal.ORANGE)
            disc(13f, 10f, 6f, Pal.NIGHT)
            fill(5, 18, 16, 8, Pal.NIGHT)
            fill(5, 28, 16, 6, Pal.DARKGRAY)
            for (x in 6..19 step 2) vline(x, 29, 32, Pal.GRAY)
            fill(2, 34, 22, 2, Pal.PLUM)
        }
        DecorStyle.PLUSH_BEAR -> PixelCanvas(30, 34).apply {
            val fur = Pal.BROWN
            disc(15f, 24f, 11f, fur)
            disc(15f, 26f, 6f, Pal.TAN)
            disc(5f, 30f, 4f, fur); disc(25f, 30f, 4f, fur)
            disc(5f, 31f, 2f, Pal.TAN); disc(25f, 31f, 2f, Pal.TAN)
            disc(6f, 5f, 4f, fur); disc(24f, 5f, 4f, fur)
            disc(6f, 5f, 2f, Pal.TAN); disc(24f, 5f, 2f, Pal.TAN)
            disc(15f, 10f, 9f, fur)
            ellipse(15f, 13f, 4f, 3f, Pal.TAN)
            fill(14, 11, 3, 2, Pal.BLACK)
            fill(10, 8, 2, 2, Pal.BLACK); fill(19, 8, 2, 2, Pal.BLACK)
            set(10, 8, Pal.WHITE); set(19, 8, Pal.WHITE)
            fill(11, 18, 3, 3, Pal.RED); fill(17, 18, 3, 3, Pal.RED); fill(14, 19, 3, 2, Pal.DARKRED)
        }
        DecorStyle.DISCO_BALL -> PixelCanvas(14, 14).apply {
            disc(7f, 7f, 6.5f, Pal.GRAY)
            for (y in 0 until 14) for (x in 0 until 14) {
                if (get(x, y) != 0 && (x / 2 + y / 2) % 2 == 0) set(x, y, Pal.LIGHTGRAY)
            }
            set(4, 4, Pal.WHITE); set(5, 3, Pal.WHITE)
        }
        DecorStyle.TROPHY_CASE -> PixelCanvas(34, 36).apply {
            fill(0, 0, 34, 36, Pal.DARKBROWN)
            fill(2, 2, 30, 26, Pal.NAVY)
            fill(2, 14, 30, 1, Pal.BROWN)
            for (i in 0 until 3) {
                val x = 5 + i * 10
                fill(x, 8, 5, 4, Pal.GOLD); fill(x + 1, 12, 3, 1, Pal.GOLD); fill(x, 13, 5, 1, Pal.ORANGE)
                set(x - 1, 9, Pal.GOLD); set(x + 5, 9, Pal.GOLD); set(x + 1, 8, Pal.YELLOW)
                val y2 = 20
                fill(x + 1, y2, 3, 4, if (i == 1) Pal.LIGHTGRAY else Pal.ORANGE)
                fill(x, y2 + 5, 5, 1, Pal.BROWN)
            }
            set(3, 3, Pal.WHITE); set(4, 3, Pal.WHITE); set(3, 4, Pal.WHITE)
            fill(0, 30, 34, 6, Pal.BROWN)
        }
    }
}
