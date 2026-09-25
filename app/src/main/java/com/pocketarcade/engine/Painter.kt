package com.pocketarcade.engine

import androidx.compose.ui.graphics.Color

/**
 * Something that draws in "painter units", a coarse grid scaled up by some factor. Games'
 * attract-mode screens are written against this so they can be painted into a texture that the
 * 3D renderer maps onto a cabinet's screen.
 */
interface Painter {
    fun fill(x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float = 1f)

    fun fill(x: Int, y: Int, w: Int, h: Int, color: Color, alpha: Float = 1f) =
        fill(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), color, alpha)

    fun px(x: Float, y: Float, color: Color, alpha: Float = 1f) = fill(x, y, 1f, 1f, color, alpha)

    fun disc(cx: Float, cy: Float, r: Float, color: Color, alpha: Float = 1f)

    fun frame(x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float = 1f)

    /** Text in the game's type; [size] scales it (1 = capitals about 7 units tall). */
    fun text(text: String, x: Float, y: Float, color: Color, tiny: Boolean = false, alpha: Float = 1f, size: Float = 1f)

    fun textCentered(text: String, cx: Float, y: Float, color: Color, tiny: Boolean = false, alpha: Float = 1f, size: Float = 1f)
}
