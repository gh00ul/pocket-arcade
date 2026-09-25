package com.pocketarcade.engine

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Draws a 1x pixel-art image scaled by [scale] with its top-left at ([x], [y]), nearest-neighbour. */
fun DrawScope.drawPixelImage(
    img: ImageBitmap,
    x: Float,
    y: Float,
    scale: Float,
    alpha: Float = 1f,
    tint: Color? = null,
) {
    drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
        dstSize = IntSize((img.width * scale).roundToInt(), (img.height * scale).roundToInt()),
        alpha = alpha,
        colorFilter = tint?.let { TintCache.get(it) },
        filterQuality = FilterQuality.None,
    )
}

/**
 * Draws [img] with squash and stretch: ([sx], [sy]) scale the sprite around a pivot at its
 * bottom-centre, which sits at screen position ([pivotX], [pivotY]).
 */
fun DrawScope.drawPixelImageSquash(
    img: ImageBitmap,
    pivotX: Float,
    pivotY: Float,
    scale: Float,
    sx: Float,
    sy: Float,
    alpha: Float = 1f,
    tint: Color? = null,
) {
    val w = img.width * scale * sx
    val h = img.height * scale * sy
    drawImage(
        image = img,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(img.width, img.height),
        dstOffset = IntOffset((pivotX - w / 2f).roundToInt(), (pivotY - h).roundToInt()),
        dstSize = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
        alpha = alpha,
        colorFilter = tint?.let { TintCache.get(it) },
        filterQuality = FilterQuality.None,
    )
}

/**
 * Draws in "virtual pixels": coordinates are in art pixels and every rectangle is snapped to
 * whole screen pixels, so dynamic effects line up with the 1x sprites around them.
 */
class PixelPainter {
    private var scope: DrawScope? = null
    var ox = 0f
        private set
    var oy = 0f
        private set
    var s = 1f
        private set

    fun begin(drawScope: DrawScope, originX: Float, originY: Float, pixelSize: Float): PixelPainter {
        scope = drawScope
        ox = originX
        oy = originY
        s = pixelSize
        return this
    }

    fun sx(x: Float): Float = floor(ox + x * s)
    fun sy(y: Float): Float = floor(oy + y * s)

    fun fill(x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float = 1f) {
        val d = scope ?: return
        val x0 = sx(x)
        val y0 = sy(y)
        val x1 = sx(x + w)
        val y1 = sy(y + h)
        if (x1 <= x0 || y1 <= y0) return
        d.drawRect(color, Offset(x0, y0), Size(x1 - x0, y1 - y0), alpha)
    }

    fun fill(x: Int, y: Int, w: Int, h: Int, color: Color, alpha: Float = 1f) =
        fill(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat(), color, alpha)

    fun px(x: Float, y: Float, color: Color, alpha: Float = 1f) = fill(x, y, 1f, 1f, color, alpha)

    fun disc(cx: Float, cy: Float, r: Float, color: Color, alpha: Float = 1f) {
        val top = floor(cy - r).toInt()
        val bottom = floor(cy + r).toInt()
        for (row in top..bottom) {
            val dy = row + 0.5f - cy
            val hw2 = r * r - dy * dy
            if (hw2 <= 0f) continue
            val hw = sqrt(hw2)
            val x0 = floor(cx - hw + 0.5f)
            val x1 = floor(cx + hw + 0.5f)
            if (x1 > x0) fill(x0, row.toFloat(), x1 - x0, 1f, color, alpha)
        }
    }

    fun frame(x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float = 1f) {
        fill(x, y, w, 1f, color, alpha)
        fill(x, y + h - 1f, w, 1f, color, alpha)
        fill(x, y, 1f, h, color, alpha)
        fill(x + w - 1f, y, 1f, h, color, alpha)
    }

    fun text(text: String, x: Float, y: Float, color: Color, tiny: Boolean = false, alpha: Float = 1f, size: Float = 1f) {
        val d = scope ?: return
        PixelFont.draw(d, text, sx(x), sy(y), s * size, color, alpha, tiny)
    }

    fun textCentered(text: String, cx: Float, y: Float, color: Color, tiny: Boolean = false, alpha: Float = 1f, size: Float = 1f) {
        val w = PixelFont.width(text, size, tiny)
        text(text, floor(cx - w / 2f), y, color, tiny, alpha, size)
    }

    fun image(img: ImageBitmap, x: Float, y: Float, alpha: Float = 1f, tint: Color? = null) {
        val d = scope ?: return
        d.drawPixelImage(img, sx(x), sy(y), s, alpha, tint)
    }
}
