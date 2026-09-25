package com.pocketarcade.hub

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.pocketarcade.data.DecorStyle
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape

/** A rectangle inside a sprite, in sprite pixels. */
data class SpriteRect(val x: Int, val y: Int, val w: Int, val h: Int)

/** A generated sprite plus the spots the renderer animates on top of it. */
class PropSprite(
    val image: ImageBitmap,
    val w: Int,
    val h: Int,
    /** Collision footprint relative to the sprite's top-left. */
    val foot: SpriteRect,
    /** Where the animated attract screen goes (cabinets only). */
    val screen: SpriteRect? = null,
    /** Marquee bulb positions for chase lights. */
    val bulbs: List<Pair<Int, Int>> = emptyList(),
)

/** Every prop in the hall, painted pixel by pixel. */
object PropArt {

    private fun light(c: Int, t: Float = 0.3f) = Pal.mix(c, Pal.WHITE, t)

    /** Neon flamingo outline, shared by the sprite (dim) and the live glow overlay. */
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

    fun cabinet(look: CabinetLook, marquee: String): PropSprite = when (look.shape) {
        CabinetShape.UPRIGHT -> upright(look, marquee)
        CabinetShape.WIDE -> wide(look, marquee)
        CabinetShape.LANE -> lane(look, marquee)
    }

    private fun upright(look: CabinetLook, marquee: String): PropSprite {
        val c = PixelCanvas(32, 52)
        val body = look.body
        val trim = look.trim
        val dark = Pal.shade(body, 0.68f)
        c.fill(2, 9, 28, 43, body)
        c.fill(2, 7, 28, 2, light(body))
        c.fill(2, 9, 3, 43, dark)
        c.fill(27, 9, 3, 43, dark)
        c.fill(1, 0, 30, 9, trim)
        c.fill(1, 0, 30, 1, light(trim, 0.4f))
        c.textCentered(marquee, 16, 2, Pal.shade(body, 0.45f), tiny = true)
        c.fill(4, 11, 24, 19, Pal.BLACK)
        c.fill(5, 12, 22, 17, Pal.DEEP)
        c.fill(1, 31, 30, 6, Pal.shade(body, 0.85f))
        c.fill(1, 31, 30, 1, light(body, 0.35f))
        c.fill(7, 32, 1, 3, Pal.BLACK)
        c.fill(6, 31, 3, 2, Pal.RED)
        c.fill(16, 33, 2, 2, Pal.YELLOW)
        c.fill(20, 33, 2, 2, Pal.CYAN)
        c.fill(24, 33, 2, 2, Pal.PINK)
        c.fill(3, 37, 26, 13, body)
        c.fill(3, 38, 1, 11, trim)
        c.fill(28, 38, 1, 11, trim)
        c.fill(12, 40, 8, 7, Pal.shade(body, 0.45f))
        c.fill(14, 42, 1, 3, Pal.GOLD)
        c.fill(17, 42, 1, 3, Pal.GOLD)
        c.fill(2, 50, 28, 2, Pal.shade(body, 0.35f))
        c.outline(Pal.BLACK)
        val bulbs = (0 until 8).flatMap { i -> listOf((2 + i * 4) to 0, (2 + i * 4) to 8) }
        return PropSprite(c.toImageBitmap(), 32, 52, SpriteRect(1, 38, 30, 14), SpriteRect(6, 13, 20, 15), bulbs)
    }

    private fun wide(look: CabinetLook, marquee: String): PropSprite {
        val c = PixelCanvas(48, 56)
        val body = look.body
        val trim = look.trim
        c.fill(2, 0, 44, 10, trim)
        c.fill(2, 0, 44, 1, light(trim, 0.4f))
        c.textCentered(marquee, 24, 3, Pal.shade(body, 0.45f), tiny = true)
        c.fill(1, 10, 46, 2, light(body))
        c.fill(1, 12, 46, 26, body)
        c.fill(1, 12, 2, 26, Pal.shade(body, 0.7f))
        c.fill(45, 12, 2, 26, Pal.shade(body, 0.7f))
        c.fill(4, 14, 40, 22, Pal.BLACK)
        c.fill(0, 38, 48, 4, Pal.shade(body, 0.85f))
        c.fill(0, 38, 48, 1, light(body, 0.35f))
        c.fill(1, 42, 46, 12, body)
        c.fill(4, 44, 12, 8, Pal.BLACK)
        c.rect(3, 43, 14, 10, trim)
        c.fill(20, 45, 10, 1, trim)
        c.fill(20, 48, 10, 1, trim)
        c.fill(35, 45, 2, 4, Pal.GOLD)
        c.fill(39, 45, 2, 4, Pal.GOLD)
        c.fill(1, 54, 46, 2, Pal.shade(body, 0.35f))
        c.outline(Pal.BLACK)
        val bulbs = (0 until 11).flatMap { i -> listOf((3 + i * 4) to 0, (3 + i * 4) to 9) }
        return PropSprite(c.toImageBitmap(), 48, 56, SpriteRect(1, 40, 46, 16), SpriteRect(5, 15, 38, 20), bulbs)
    }

    private fun lane(look: CabinetLook, marquee: String): PropSprite {
        val c = PixelCanvas(32, 84)
        val body = look.body
        val trim = look.trim
        c.fill(2, 0, 28, 20, trim)
        c.fill(2, 0, 28, 1, light(trim, 0.4f))
        c.fill(4, 2, 24, 15, Pal.BLACK)
        c.fill(0, 20, 5, 50, body)
        c.fill(27, 20, 5, 50, body)
        c.fill(0, 20, 5, 1, light(body))
        c.fill(27, 20, 5, 1, light(body))
        c.fill(5, 20, 22, 50, Pal.WOOD)
        for (x in intArrayOf(10, 16, 21)) c.vline(x, 20, 69, Pal.shade(Pal.WOOD, 0.85f))
        c.fill(5, 20, 22, 8, Pal.shade(body, 0.45f))
        for (k in 0 until 3) {
            val y = 40 + k * 8
            c.set(15, y, Pal.CREAM); c.set(16, y, Pal.CREAM)
            c.set(14, y + 1, Pal.CREAM); c.set(17, y + 1, Pal.CREAM)
        }
        c.fill(0, 70, 32, 14, body)
        c.fill(0, 70, 32, 2, light(body))
        c.fill(7, 73, 18, 6, Pal.BLACK)
        c.textCentered(marquee, 16, 74, trim, tiny = true)
        c.fill(10, 80, 12, 2, Pal.BLACK)
        c.outline(Pal.BLACK)
        val bulbs = (0 until 7).flatMap { i -> listOf((4 + i * 4) to 0, (4 + i * 4) to 19) }
        return PropSprite(c.toImageBitmap(), 32, 84, SpriteRect(0, 22, 32, 62), SpriteRect(5, 3, 22, 13), bulbs)
    }

    /** A dark "coming soon" cabinet for empty machine slots. */
    fun brokenCabinet(): PropSprite = upright(CabinetLook(Pal.DARKGRAY, Pal.GRAY, Pal.GRAY), "SOON")

    fun counter(): PropSprite {
        val c = PixelCanvas(96, 32)
        // Glass prize cases on either side of the clerk.
        for (x0 in intArrayOf(2, 58)) {
            c.fill(x0, 3, 36, 11, Pal.NAVY)
            c.fill(x0, 3, 36, 1, Pal.LIGHTGRAY)
            val colors = intArrayOf(Pal.PINK, Pal.YELLOW, Pal.CYAN, Pal.LIME, Pal.ORANGE, Pal.LAVENDER, Pal.WHITE, Pal.GOLD)
            for (i in 0 until 8) {
                val px = x0 + 2 + i * 4
                val py = if (i % 2 == 0) 6 else 9
                c.fill(px, py, 3, 3, colors[(i + x0) % colors.size])
                c.set(px, py, Pal.WHITE)
            }
            c.rect(x0, 3, 36, 11, Pal.LIGHTGRAY)
        }
        c.fill(0, 14, 96, 4, Pal.CREAM)
        c.fill(0, 17, 96, 1, Pal.TAN)
        c.fill(0, 18, 96, 14, Pal.DARKRED)
        c.fill(0, 18, 96, 1, Pal.GOLD)
        c.fill(0, 30, 96, 2, Pal.shade(Pal.DARKRED, 0.6f))
        c.textCentered("PRIZES", 48, 22, Pal.GOLD, tiny = true)
        for (i in 0 until 6) {
            c.set(8 + i * 14, 24, Pal.GOLD)
            c.set(9 + i * 14, 24, Pal.GOLD)
        }
        c.fill(40, 10, 16, 4, Pal.DARKGRAY)
        c.fill(42, 8, 12, 3, Pal.GRAY)
        c.set(44, 9, Pal.LIME)
        c.outline(Pal.BLACK)
        return PropSprite(c.toImageBitmap(), 96, 32, SpriteRect(0, 14, 96, 18))
    }

    fun tokenMachine(): PropSprite {
        val c = PixelCanvas(24, 42)
        c.fill(0, 0, 24, 9, Pal.RED)
        c.fill(0, 0, 24, 1, Pal.HOTPINK)
        c.textCentered("TOKEN", 12, 2, Pal.YELLOW, tiny = true)
        c.fill(1, 9, 22, 31, Pal.GOLD)
        c.fill(1, 9, 2, 31, Pal.shade(Pal.GOLD, 0.75f))
        c.fill(21, 9, 2, 31, Pal.shade(Pal.GOLD, 0.75f))
        c.fill(4, 12, 16, 8, Pal.BLACK)
        c.fill(8, 22, 8, 2, Pal.DARKGRAY)
        c.fill(5, 32, 14, 5, Pal.BLACK)
        c.set(7, 35, Pal.YELLOW); c.set(9, 35, Pal.YELLOW); c.set(8, 34, Pal.GOLD); c.set(14, 35, Pal.YELLOW)
        c.fill(1, 40, 22, 2, Pal.shade(Pal.GOLD, 0.4f))
        c.outline(Pal.BLACK)
        return PropSprite(c.toImageBitmap(), 24, 42, SpriteRect(1, 30, 22, 12), SpriteRect(4, 12, 16, 8))
    }

    fun sodaMachine(): PropSprite {
        val c = PixelCanvas(24, 42)
        c.fill(1, 2, 22, 38, Pal.RED)
        c.fill(1, 0, 22, 2, Pal.HOTPINK)
        c.textCentered("SODA", 12, 3, Pal.WHITE, tiny = true)
        c.fill(3, 10, 13, 20, Pal.NAVY)
        val cans = intArrayOf(Pal.LIME, Pal.ORANGE, Pal.SKY, Pal.YELLOW)
        for (row in 0 until 4) for (col in 0 until 3) {
            c.fill(4 + col * 4, 11 + row * 5, 3, 4, cans[(row + col) % cans.size])
        }
        c.fill(18, 12, 3, 6, Pal.DARKGRAY)
        c.set(19, 13, Pal.GOLD)
        c.fill(4, 33, 12, 4, Pal.BLACK)
        c.fill(1, 40, 22, 2, Pal.DARKRED)
        c.outline(Pal.BLACK)
        return PropSprite(c.toImageBitmap(), 24, 42, SpriteRect(1, 30, 22, 12))
    }

    fun bench(): PropSprite {
        val c = PixelCanvas(36, 14)
        c.fill(0, 0, 36, 3, Pal.BROWN)
        c.fill(0, 4, 36, 4, Pal.WOOD)
        c.fill(0, 4, 36, 1, Pal.TAN)
        c.fill(2, 8, 2, 6, Pal.DARKBROWN)
        c.fill(32, 8, 2, 6, Pal.DARKBROWN)
        c.fill(2, 1, 2, 3, Pal.DARKBROWN)
        c.fill(32, 1, 2, 3, Pal.DARKBROWN)
        c.outline(Pal.BLACK)
        return PropSprite(c.toImageBitmap(), 36, 14, SpriteRect(0, 3, 36, 11))
    }

    fun plant(): PropSprite {
        val c = PixelCanvas(14, 22)
        c.fill(3, 14, 8, 8, Pal.BROWN)
        c.fill(2, 14, 10, 2, Pal.TAN)
        c.disc(7f, 9f, 6f, Pal.GREEN)
        c.disc(4f, 7f, 3f, Pal.DARKGREEN)
        c.disc(10f, 6f, 3f, Pal.LIME)
        c.set(7, 3, Pal.LIME)
        c.outline(Pal.BLACK)
        return PropSprite(c.toImageBitmap(), 14, 22, SpriteRect(2, 15, 10, 7))
    }

    fun trashCan(): PropSprite {
        val c = PixelCanvas(12, 16)
        c.fill(1, 3, 10, 13, Pal.GRAY)
        c.fill(0, 1, 12, 3, Pal.LIGHTGRAY)
        c.vline(4, 5, 14, Pal.DARKGRAY)
        c.vline(7, 5, 14, Pal.DARKGRAY)
        c.outline(Pal.BLACK)
        return PropSprite(c.toImageBitmap(), 12, 16, SpriteRect(1, 9, 10, 7))
    }

    fun decor(style: DecorStyle): PropSprite = when (style) {
        DecorStyle.PALM -> {
            val c = PixelCanvas(22, 32)
            c.fill(7, 24, 8, 8, Pal.ORANGE)
            c.fill(6, 24, 10, 2, Pal.TAN)
            for (y in 10 until 25) c.set(10 + (if (y % 4 < 2) 0 else 1), y, if (y % 3 == 0) Pal.DARKBROWN else Pal.BROWN)
            c.fill(10, 12, 2, 12, Pal.BROWN)
            val fronds = listOf(-8 to -3, -5 to -6, 0 to -8, 5 to -6, 8 to -3, -9 to 1, 9 to 1)
            for ((dx, dy) in fronds) c.line(11, 11, 11 + dx, 11 + dy, Pal.GREEN)
            for ((dx, dy) in fronds) c.set(11 + dx, 12 + dy, Pal.DARKGREEN)
            c.disc(11f, 11f, 2f, Pal.LIME)
            c.outline(Pal.BLACK)
            PropSprite(c.toImageBitmap(), 22, 32, SpriteRect(6, 26, 10, 6))
        }
        DecorStyle.LAVA_LAMP -> {
            val c = PixelCanvas(16, 32)
            c.fill(0, 23, 16, 3, Pal.WOOD)
            c.fill(1, 26, 2, 6, Pal.DARKBROWN)
            c.fill(13, 26, 2, 6, Pal.DARKBROWN)
            c.fill(5, 17, 6, 6, Pal.GRAY)
            c.fill(4, 21, 8, 2, Pal.LIGHTGRAY)
            c.fill(5, 4, 6, 13, Pal.PLUM)
            c.disc(8f, 7f, 1.6f, Pal.ORANGE)
            c.disc(7.5f, 13f, 2f, Pal.ORANGE)
            c.fill(6, 2, 4, 2, Pal.GRAY)
            c.outline(Pal.BLACK)
            PropSprite(c.toImageBitmap(), 16, 32, SpriteRect(0, 24, 16, 8), SpriteRect(5, 4, 6, 13))
        }
        DecorStyle.FLAMINGO -> {
            val c = PixelCanvas(22, 36)
            c.fill(0, 0, 22, 26, Pal.NIGHT)
            c.rect(0, 0, 22, 26, Pal.DARKGRAY)
            c.sprite(FLAMINGO_ROWS, 1, 1, mapOf('#' to Pal.shade(Pal.PINK, 0.55f)))
            c.fill(10, 26, 2, 8, Pal.GRAY)
            c.fill(6, 33, 10, 3, Pal.DARKGRAY)
            c.outline(Pal.BLACK)
            PropSprite(c.toImageBitmap(), 22, 36, SpriteRect(5, 30, 12, 6), SpriteRect(1, 1, 20, 24))
        }
        DecorStyle.GUMBALL -> {
            val c = PixelCanvas(16, 28)
            c.fill(4, 16, 8, 12, Pal.RED)
            c.fill(3, 16, 10, 2, Pal.HOTPINK)
            c.fill(7, 20, 2, 2, Pal.GOLD)
            c.fill(6, 24, 4, 2, Pal.BLACK)
            c.disc(8f, 9f, 6.5f, Pal.mix(Pal.SKY, Pal.WHITE, 0.6f))
            val dots = intArrayOf(Pal.RED, Pal.YELLOW, Pal.LIME, Pal.PINK, Pal.SKY, Pal.ORANGE)
            var k = 0
            for (y in 6..13 step 2) for (x in 4..12 step 2) {
                if ((x - 8) * (x - 8) + (y - 9) * (y - 9) < 30) c.set(x, y, dots[k++ % dots.size])
            }
            c.fill(5, 2, 6, 2, Pal.RED)
            c.set(5, 5, Pal.WHITE)
            c.outline(Pal.BLACK)
            PropSprite(c.toImageBitmap(), 16, 28, SpriteRect(3, 21, 10, 7))
        }
        DecorStyle.FISH_TANK -> {
            val c = PixelCanvas(36, 30)
            c.fill(0, 18, 36, 12, Pal.DARKBROWN)
            c.fill(0, 18, 36, 1, Pal.BROWN)
            c.fill(1, 2, 34, 16, Pal.mix(Pal.BLUE, Pal.SKY, 0.4f))
            c.fill(1, 2, 34, 2, Pal.SKY)
            c.fill(2, 15, 32, 2, Pal.TAN)
            c.vline(6, 9, 14, Pal.GREEN); c.vline(7, 11, 14, Pal.DARKGREEN)
            c.vline(28, 8, 14, Pal.GREEN); c.vline(29, 10, 14, Pal.LIME)
            c.rect(0, 1, 36, 18, Pal.LIGHTGRAY)
            c.outline(Pal.BLACK)
            PropSprite(c.toImageBitmap(), 36, 30, SpriteRect(0, 19, 36, 11), SpriteRect(2, 4, 32, 11))
        }
        DecorStyle.JUKEBOX -> {
            val c = PixelCanvas(26, 36)
            c.disc(13f, 10f, 11f, Pal.PURPLE)
            c.fill(2, 10, 22, 26, Pal.PURPLE)
            c.disc(13f, 10f, 8f, Pal.ORANGE)
            c.disc(13f, 10f, 6f, Pal.NIGHT)
            c.fill(5, 18, 16, 8, Pal.NIGHT)
            c.fill(5, 28, 16, 6, Pal.DARKGRAY)
            for (x in 6..19 step 2) c.vline(x, 29, 32, Pal.GRAY)
            c.fill(2, 34, 22, 2, Pal.PLUM)
            c.outline(Pal.BLACK)
            PropSprite(c.toImageBitmap(), 26, 36, SpriteRect(1, 26, 24, 10), SpriteRect(5, 18, 16, 8))
        }
        DecorStyle.PLUSH_BEAR -> {
            val c = PixelCanvas(30, 34)
            val fur = Pal.BROWN
            c.disc(15f, 24f, 11f, fur)
            c.disc(15f, 26f, 6f, Pal.TAN)
            c.disc(5f, 30f, 4f, fur); c.disc(25f, 30f, 4f, fur)
            c.disc(5f, 31f, 2f, Pal.TAN); c.disc(25f, 31f, 2f, Pal.TAN)
            c.disc(6f, 5f, 4f, fur); c.disc(24f, 5f, 4f, fur)
            c.disc(6f, 5f, 2f, Pal.TAN); c.disc(24f, 5f, 2f, Pal.TAN)
            c.disc(15f, 10f, 9f, fur)
            c.ellipse(15f, 13f, 4f, 3f, Pal.TAN)
            c.fill(14, 11, 3, 2, Pal.BLACK)
            c.fill(10, 8, 2, 2, Pal.BLACK); c.fill(19, 8, 2, 2, Pal.BLACK)
            c.set(10, 8, Pal.WHITE); c.set(19, 8, Pal.WHITE)
            c.fill(11, 18, 3, 3, Pal.RED); c.fill(17, 18, 3, 3, Pal.RED); c.fill(14, 19, 3, 2, Pal.DARKRED)
            c.outline(Pal.BLACK)
            PropSprite(c.toImageBitmap(), 30, 34, SpriteRect(3, 24, 24, 10))
        }
        DecorStyle.DISCO_BALL -> {
            val c = PixelCanvas(14, 14)
            c.disc(7f, 7f, 6.5f, Pal.GRAY)
            for (y in 0 until 14) for (x in 0 until 14) {
                if (c.get(x, y) != 0 && (x / 2 + y / 2) % 2 == 0) c.set(x, y, Pal.LIGHTGRAY)
            }
            c.set(4, 4, Pal.WHITE); c.set(5, 3, Pal.WHITE)
            c.outline(Pal.BLACK)
            PropSprite(c.toImageBitmap(), 14, 14, SpriteRect(0, 0, 0, 0))
        }
        DecorStyle.TROPHY_CASE -> {
            val c = PixelCanvas(34, 36)
            c.fill(0, 0, 34, 36, Pal.DARKBROWN)
            c.fill(2, 2, 30, 26, Pal.NAVY)
            c.fill(2, 14, 30, 1, Pal.BROWN)
            for (i in 0 until 3) {
                val x = 5 + i * 10
                c.fill(x, 8, 5, 4, Pal.GOLD); c.fill(x + 1, 12, 3, 1, Pal.GOLD); c.fill(x, 13, 5, 1, Pal.ORANGE)
                c.set(x - 1, 9, Pal.GOLD); c.set(x + 5, 9, Pal.GOLD); c.set(x + 1, 8, Pal.YELLOW)
                val y2 = 20
                c.fill(x + 1, y2, 3, 4, if (i == 1) Pal.LIGHTGRAY else Pal.ORANGE)
                c.fill(x, y2 + 5, 5, 1, Pal.BROWN)
            }
            c.set(3, 3, Pal.WHITE); c.set(4, 3, Pal.WHITE); c.set(3, 4, Pal.WHITE)
            c.fill(0, 30, 34, 6, Pal.BROWN)
            c.outline(Pal.BLACK)
            PropSprite(c.toImageBitmap(), 34, 36, SpriteRect(0, 24, 34, 12))
        }
    }

    /** A soft white radial blob used for neon glow pools (tinted when drawn). */
    val glow: ImageBitmap by lazy {
        val n = 64
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f - n / 2f) / (n / 2f)
            val dy = (y + 0.5f - n / 2f) / (n / 2f)
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            val a = ((1f - d).coerceIn(0f, 1f).let { it * it } * 255).toInt()
            px[y * n + x] = (a shl 24) or 0xFFFFFF
        }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}
