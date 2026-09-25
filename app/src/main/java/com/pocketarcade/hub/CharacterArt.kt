package com.pocketarcade.hub

import androidx.compose.ui.graphics.ImageBitmap
import com.pocketarcade.data.HatStyle
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.PixelCanvas

/** Colours and style of one character (the player, a wandering kid or the clerk). */
data class CharacterLook(
    val skin: Int,
    val hair: Int,
    val hairStyle: Int,
    val shirt: Int,
    val pants: Int,
    val shoes: Int = Pal.DARKGRAY,
    val hat: HatStyle? = null,
)

/**
 * Generates 4-direction, 3-frame walk cycles for a chibi kid, pixel by pixel.
 * Frames: 0 = standing, 1 and 2 = alternate steps (the cycle plays 0,1,0,2).
 */
object CharacterArt {
    const val W = 18
    const val H = 30
    private const val OX = 3
    private const val OY = 10
    /** Canvas position of the point between the feet. */
    const val FEET_X = 9
    const val FEET_Y = 28

    const val DOWN = 0
    const val UP = 1
    const val LEFT = 2
    const val RIGHT = 3

    fun frames(look: CharacterLook): Array<Array<ImageBitmap>> = Array(4) { dir ->
        Array(3) { frame -> build(look, dir, frame).toImageBitmap() }
    }

    fun build(look: CharacterLook, dir: Int, frame: Int): PixelCanvas {
        if (dir == RIGHT) return build(look, LEFT, frame).flippedX()
        val c = PixelCanvas(W, H)
        fun p(x: Int, y: Int, col: Int) = c.set(OX + x, OY + y, col)
        fun r(x: Int, y: Int, w: Int, h: Int, col: Int) = c.fill(OX + x, OY + y, w, h, col)

        val skin = look.skin
        val hair = look.hair
        val shirt = look.shirt
        val shirtDark = Pal.shade(shirt, 0.78f)
        val pants = look.pants
        val shoes = look.shoes
        val blush = Pal.mix(skin, Pal.PINK, 0.45f)

        // Legs and shoes.
        when (dir) {
            DOWN, UP -> {
                val leftLift = if (frame == 1) 1 else 0
                val rightLift = if (frame == 2) 1 else 0
                r(3, 14, 3, 3 - leftLift, pants)
                r(3, 17 - leftLift, 3, 1, shoes)
                r(6, 14, 3, 3 - rightLift, pants)
                r(6, 17 - rightLift, 3, 1, shoes)
            }
            else -> when (frame) {
                0 -> {
                    r(4, 14, 4, 3, pants)
                    r(3, 17, 5, 1, shoes)
                }
                1 -> {
                    r(3, 14, 3, 3, pants); r(2, 17, 4, 1, shoes)
                    r(6, 14, 3, 2, Pal.shade(pants, 0.8f)); r(7, 16, 3, 1, shoes)
                }
                else -> {
                    r(6, 14, 3, 3, Pal.shade(pants, 0.8f)); r(6, 17, 4, 1, shoes)
                    r(3, 14, 3, 2, pants); r(1, 16, 4, 1, shoes)
                }
            }
        }

        // Torso and arms.
        when (dir) {
            DOWN, UP -> {
                r(2, 9, 8, 5, shirt)
                r(2, 13, 8, 1, shirtDark)
                val lDrop = if (frame == 2) 1 else 0
                val rDrop = if (frame == 1) 1 else 0
                r(1, 9, 1, 2 + lDrop, shirtDark)
                r(1, 11 + lDrop, 1, 2, skin)
                r(10, 9, 1, 2 + rDrop, shirtDark)
                r(10, 11 + rDrop, 1, 2, skin)
            }
            else -> {
                r(3, 9, 6, 5, shirt)
                r(3, 13, 6, 1, shirtDark)
                when (frame) {
                    1 -> {
                        r(3, 10, 2, 2, shirtDark); r(2, 12, 2, 1, skin)
                    }
                    2 -> {
                        r(7, 10, 2, 2, shirtDark); r(8, 12, 2, 1, skin)
                    }
                    else -> {
                        r(5, 9, 2, 3, shirtDark); r(5, 12, 2, 1, skin)
                    }
                }
            }
        }

        // Head.
        r(3, 0, 6, 1, skin)
        r(2, 1, 8, 1, skin)
        r(1, 2, 10, 6, skin)
        r(2, 8, 8, 1, skin)

        when (dir) {
            DOWN -> {
                r(3, 0, 6, 1, hair)
                r(2, 1, 8, 1, hair)
                r(1, 2, 10, 2, hair)
                p(1, 4, hair); p(10, 4, hair)
                if (look.hairStyle == 1) {
                    r(1, 4, 1, 6, hair); r(10, 4, 1, 6, hair)
                    p(2, 9, hair); p(9, 9, hair)
                }
                p(3, 5, Pal.BLACK); p(3, 6, Pal.BLACK)
                p(8, 5, Pal.BLACK); p(8, 6, Pal.BLACK)
                p(2, 7, blush); p(9, 7, blush)
                p(5, 7, Pal.shade(skin, 0.6f)); p(6, 7, Pal.shade(skin, 0.6f))
            }
            UP -> {
                r(3, 0, 6, 1, hair)
                r(2, 1, 8, 1, hair)
                r(1, 2, 10, 6, hair)
                r(2, 8, 8, 1, hair)
                if (look.hairStyle == 1) r(2, 9, 8, 2, hair)
                else r(4, 8, 4, 1, Pal.shade(skin, 0.85f))
            }
            else -> {
                r(3, 0, 6, 1, hair)
                r(2, 1, 8, 1, hair)
                r(1, 2, 10, 1, hair)
                r(4, 3, 7, 1, hair)
                r(6, 4, 5, 4, hair)
                r(7, 8, 3, 1, hair)
                if (look.hairStyle == 1) r(7, 8, 4, 3, hair)
                p(3, 5, Pal.BLACK); p(3, 6, Pal.BLACK)
                p(4, 7, blush)
                p(1, 7, Pal.shade(skin, 0.6f))
            }
        }
        if (look.hairStyle == 2) {
            p(3, -1, hair); p(5, -1, hair); p(7, -1, hair); p(4, -2, hair); p(8, -1, hair)
        }

        look.hat?.let { drawHat(c, it, dir, frame) }
        c.outline(Pal.BLACK)
        return c
    }

    private fun drawHat(c: PixelCanvas, style: HatStyle, dir: Int, frame: Int) {
        fun p(x: Int, y: Int, col: Int) = c.set(OX + x, OY + y, col)
        fun r(x: Int, y: Int, w: Int, h: Int, col: Int) = c.fill(OX + x, OY + y, w, h, col)
        val side = dir == LEFT
        when (style) {
            HatStyle.CAP -> {
                val cap = Pal.BLUE
                val brim = Pal.NAVY
                r(3, -1, 6, 1, cap); r(2, 0, 8, 1, cap); r(1, 1, 10, 2, cap)
                p(5, -2, Pal.WHITE); p(6, -2, Pal.WHITE)
                when (dir) {
                    DOWN -> {
                        r(1, 3, 10, 1, brim); p(5, 1, Pal.WHITE); p(6, 1, Pal.WHITE)
                    }
                    UP -> r(4, 3, 4, 1, brim)
                    else -> r(-2, 2, 5, 1, brim)
                }
            }
            HatStyle.BEANIE -> {
                val b = Pal.PURPLE
                r(3, -2, 6, 1, b); r(2, -1, 8, 1, b); r(1, 0, 10, 2, b)
                r(1, 2, 10, 1, Pal.LAVENDER)
                r(5, -4, 2, 2, Pal.WHITE)
            }
            HatStyle.PARTY -> {
                val widths = intArrayOf(2, 2, 4, 4, 6, 6, 8, 8, 10, 10)
                for (i in widths.indices) {
                    val w = widths[i]
                    val row = -8 + i
                    r(6 - w / 2, row, w, 1, if ((i / 2) % 2 == 0) Pal.PINK else Pal.YELLOW)
                }
                r(5, -9, 2, 1, Pal.CYAN)
            }
            HatStyle.HEADPHONES -> {
                val band = Pal.DARKGRAY
                r(3, -1, 6, 1, band); p(2, 0, band); p(9, 0, band); p(1, 1, band); p(10, 1, band)
                if (side) {
                    r(5, 3, 3, 4, Pal.RED); p(5, 3, Pal.HOTPINK)
                } else {
                    r(0, 3, 2, 4, Pal.RED); r(10, 3, 2, 4, Pal.RED)
                    p(0, 3, Pal.HOTPINK); p(11, 3, Pal.HOTPINK)
                }
            }
            HatStyle.COWBOY -> {
                val b = Pal.BROWN
                r(3, -4, 2, 1, b); r(7, -4, 2, 1, b)
                r(2, -3, 8, 3, b)
                r(2, 0, 8, 1, Pal.DARKBROWN)
                r(-2, 1, 16, 1, b)
                r(-1, 2, 14, 1, Pal.shade(b, 0.75f))
            }
            HatStyle.PROPELLER -> {
                r(3, -1, 6, 1, Pal.YELLOW)
                for (x in 1..10) {
                    val col = when {
                        x <= 3 -> Pal.RED
                        x <= 5 -> Pal.YELLOW
                        x <= 7 -> Pal.BLUE
                        else -> Pal.GREEN
                    }
                    for (y in 0..2) p(x, y, col)
                }
                r(5, -2, 2, 1, Pal.GRAY)
                when (frame) {
                    0 -> r(1, -3, 10, 1, Pal.LIGHTGRAY)
                    1 -> r(3, -3, 6, 1, Pal.LIGHTGRAY)
                    else -> r(5, -3, 2, 1, Pal.LIGHTGRAY)
                }
            }
            HatStyle.WIZARD -> {
                val b = Pal.VIOLET
                val rows = intArrayOf(2, 2, 2, 4, 4, 6, 6, 6, 8, 8, 10)
                for (i in rows.indices) {
                    val w = rows[i]
                    r(6 - w / 2, -10 + i, w, 1, b)
                }
                p(7, -11, b)
                r(-1, 1, 14, 1, Pal.shade(b, 0.7f))
                p(5, -6, Pal.YELLOW); p(7, -3, Pal.YELLOW); p(4, -1, Pal.YELLOW); p(6, -8, Pal.YELLOW)
            }
            HatStyle.TOPHAT -> {
                val b = Pal.DARKGRAY
                r(2, -7, 8, 8, b)
                r(3, -7, 1, 6, Pal.GRAY)
                r(2, -1, 8, 1, Pal.RED)
                r(-1, 1, 14, 1, b)
            }
            HatStyle.CROWN -> {
                val g = Pal.GOLD
                r(2, -1, 8, 3, g)
                r(2, -3, 1, 2, g); r(5, -3, 2, 2, g); r(9, -3, 1, 2, g)
                r(2, -1, 8, 1, Pal.YELLOW)
                p(4, 0, Pal.RED); p(7, 0, Pal.CYAN)
            }
            HatStyle.HALO -> {
                val y = Pal.YELLOW
                r(3, -6, 6, 1, y); r(1, -5, 2, 1, y); r(9, -5, 2, 1, y); r(3, -4, 6, 1, y)
            }
        }
    }

    private val skins = intArrayOf(Pal.SKIN_LIGHT, Pal.SKIN_MID, Pal.SKIN_TAN, Pal.SKIN_DARK)
    private val hairs = intArrayOf(Pal.DARKBROWN, Pal.BLACK, Pal.GOLD, Pal.ORANGE, Pal.BROWN, Pal.PINK)
    private val shirts = intArrayOf(Pal.GREEN, Pal.YELLOW, Pal.SKY, Pal.PINK, Pal.ORANGE, Pal.LIME, Pal.PURPLE, Pal.TEAL)
    private val pantsColors = intArrayOf(Pal.NAVY, Pal.DARKGRAY, Pal.BROWN, Pal.PLUM, Pal.BLUE)

    /** A deterministic random kid for NPC slot [seed]. */
    fun randomKid(seed: Int): CharacterLook {
        fun pick(arr: IntArray, salt: Int) = arr[((seed * 7919 + salt * 104729) and 0x7FFFFFFF) % arr.size]
        val hatChoices = arrayOf(null, null, HatStyle.CAP, HatStyle.BEANIE, null, HatStyle.HEADPHONES)
        return CharacterLook(
            skin = pick(skins, 1),
            hair = pick(hairs, 2),
            hairStyle = (seed * 13 + 5) % 3,
            shirt = pick(shirts, 3),
            pants = pick(pantsColors, 4),
            hat = hatChoices[(seed * 31 + 7) % hatChoices.size],
        )
    }

    /** The player's kid wearing the given outfit colours and hat. */
    fun player(shirt: Int, pants: Int, hat: HatStyle?) = CharacterLook(
        skin = Pal.SKIN_LIGHT, hair = Pal.DARKBROWN, hairStyle = 2,
        shirt = shirt, pants = pants, shoes = Pal.WHITE, hat = hat,
    )

    val clerk = CharacterLook(
        skin = Pal.SKIN_MID, hair = Pal.BLACK, hairStyle = 0,
        shirt = Pal.RED, pants = Pal.NAVY, hat = HatStyle.CAP,
    )
}
