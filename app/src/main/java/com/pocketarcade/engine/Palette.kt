package com.pocketarcade.engine

import androidx.compose.ui.graphics.Color

/**
 * The one palette every texture, effect and menu draws from. Values are ARGB ints so they
 * can go straight into painted textures; use [Pal.c] to get a Compose [Color].
 */
object Pal {
    val NIGHT = 0xFF120A24.toInt()
    val DEEP = 0xFF1E1440.toInt()
    val PLUM = 0xFF2E1F5E.toInt()
    val INDIGO = 0xFF3B2A7A.toInt()
    val VIOLET = 0xFF5B2DB0.toInt()
    val PURPLE = 0xFF8A4FFF.toInt()
    val LAVENDER = 0xFFC3A6FF.toInt()
    val PINK = 0xFFFF3FA4.toInt()
    val HOTPINK = 0xFFFF77C8.toInt()
    val RED = 0xFFFF4D4D.toInt()
    val DARKRED = 0xFFB0213A.toInt()
    val ORANGE = 0xFFFF9A3C.toInt()
    val GOLD = 0xFFFFC83D.toInt()
    val YELLOW = 0xFFFFE14D.toInt()
    val CREAM = 0xFFFFF4D6.toInt()
    val LIME = 0xFFA6F04A.toInt()
    val GREEN = 0xFF3DDC84.toInt()
    val DARKGREEN = 0xFF1E7A4E.toInt()
    val TEAL = 0xFF1FA89A.toInt()
    val CYAN = 0xFF3DF5FF.toInt()
    val SKY = 0xFF4DA6FF.toInt()
    val BLUE = 0xFF2F5BE0.toInt()
    val NAVY = 0xFF1A2A6C.toInt()
    val WHITE = 0xFFFFFFFF.toInt()
    val LIGHTGRAY = 0xFFC8C8E0.toInt()
    val GRAY = 0xFF7A7A9A.toInt()
    val DARKGRAY = 0xFF3A3A55.toInt()
    val BLACK = 0xFF08060F.toInt()
    val BROWN = 0xFF8B5A2B.toInt()
    val DARKBROWN = 0xFF5A3418.toInt()
    val TAN = 0xFFD9A066.toInt()
    val WOOD = 0xFFC07A3A.toInt()
    val SKIN_LIGHT = 0xFFFFD1A6.toInt()
    val SKIN_MID = 0xFFE0A87A.toInt()
    val SKIN_TAN = 0xFFB5764C.toInt()
    val SKIN_DARK = 0xFF7A4A2A.toInt()
    const val CLEAR = 0

    fun c(argb: Int): Color = Color(argb)

    /** Multiplies the RGB channels by [f] (0..1 darkens), keeping alpha. */
    fun shade(argb: Int, f: Float): Int {
        val a = argb ushr 24 and 0xFF
        val r = ((argb shr 16 and 0xFF) * f).toInt().coerceIn(0, 255)
        val g = ((argb shr 8 and 0xFF) * f).toInt().coerceIn(0, 255)
        val b = ((argb and 0xFF) * f).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    /** Blends [a] toward [b] by [t]. */
    fun mix(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        fun ch(shift: Int) = ((a shr shift and 0xFF) * (1 - u) + (b shr shift and 0xFF) * u).toInt()
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun withAlpha(argb: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24) or (argb and 0xFFFFFF)
}
