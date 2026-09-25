package com.pocketarcade.engine

import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.pocketarcade.engine.r3d.Fonts
import kotlin.math.cos
import kotlin.math.sin

/**
 * The game's type: a black sans for headings and numbers and a condensed bold for small labels,
 * anti-aliased at any size, with a few symbols (play, star, heart, token, ticket, note, arrows)
 * drawn inline as vector icons.
 *
 * Sizes are given as a *unit*: capitals stand [CAP] units tall ([TINY_CAP] for tiny text), so
 * layouts can keep reasoning in simple grid units. Text is always set in capitals.
 */
object ArcadeFont {
    const val CAP = 7f
    const val TINY_CAP = 5f

    const val PLAY = '▶'
    const val STAR = '★'
    const val HEART = '♥'
    const val TOKEN = '¤'
    const val TICKET = '¢'
    const val NOTE = '♪'
    const val LEFT = '←'
    const val RIGHT = '→'
    const val UP = '↑'
    const val DOWN = '↓'

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val cut = Path()
    private val capRatios = HashMap<Typeface, Float>()
    private var shadowBlur = 0f
    private var shadowDx = 0f
    private var shadowDy = 0f
    private var shadowColor = 0
    private val upper = HashMap<String, String>()

    private fun isIcon(c: Char) = c == PLAY || c == STAR || c == HEART || c == TOKEN || c == TICKET ||
        c == NOTE || c == LEFT || c == RIGHT || c == UP || c == DOWN

    private fun capRatio(face: Typeface): Float = capRatios.getOrPut(face) {
        val p = Paint()
        p.typeface = face
        p.textSize = 100f
        val b = Rect()
        p.getTextBounds("H", 0, 1, b)
        b.height() / 100f
    }

    private fun caps(text: String): String {
        if (upper.size > 512) upper.clear()
        return upper.getOrPut(text) { text.uppercase() }
    }

    /** Sets the paint up for [unit]-sized text and returns the capital height in pixels. */
    private fun setup(unit: Float, tiny: Boolean): Float {
        val face = if (tiny) Fonts.condensed else Fonts.display
        val cap = (if (tiny) TINY_CAP else CAP) * unit
        paint.typeface = face
        paint.textSize = cap / capRatio(face)
        paint.letterSpacing = if (tiny) 0.07f else 0.035f
        return cap
    }

    private fun iconWidth(c: Char, cap: Float) = when (c) {
        TICKET -> cap * 1.5f
        LEFT, RIGHT -> cap * 1.2f
        UP, DOWN, NOTE, PLAY -> cap * 0.9f
        else -> cap * 1.15f
    }

    private fun run(s: String, a: Int, b: Int) = if (b > a) paint.measureText(s, a, b) else 0f

    fun width(text: String, unit: Float, tiny: Boolean = false): Float {
        if (text.isEmpty()) return 0f
        val s = caps(text)
        val cap = setup(unit, tiny)
        var w = 0f
        var start = 0
        for (i in s.indices) {
            if (!isIcon(s[i])) continue
            w += run(s, start, i) + iconWidth(s[i], cap) + cap * 0.18f
            start = i + 1
        }
        return w + run(s, start, s.length)
    }

    fun height(unit: Float, tiny: Boolean = false): Float = (if (tiny) TINY_CAP else CAP) * unit

    /** Draws [text] with its top-left corner at ([x], [y]) (the top of the capitals). */
    fun draw(scope: DrawScope, text: String, x: Float, y: Float, unit: Float, color: Color, alpha: Float = 1f, tiny: Boolean = false) {
        drawTo(scope.drawContext.canvas.nativeCanvas, text, x, y, unit, color.toArgb(), alpha, tiny, 0)
    }

    /** Draws text with a soft drop shadow for legibility over busy pictures. */
    fun drawShadowed(
        scope: DrawScope, text: String, x: Float, y: Float, unit: Float, color: Color,
        shadow: Color = Color(0xFF05030A), alpha: Float = 1f, tiny: Boolean = false,
    ) {
        drawTo(scope.drawContext.canvas.nativeCanvas, text, x, y, unit, color.toArgb(), alpha, tiny, shadow.toArgb())
    }

    fun drawCentered(
        scope: DrawScope, text: String, cx: Float, y: Float, unit: Float, color: Color,
        alpha: Float = 1f, tiny: Boolean = false, shadow: Boolean = true,
    ) {
        val x = cx - width(text, unit, tiny) / 2f
        drawTo(scope.drawContext.canvas.nativeCanvas, text, x, y, unit, color.toArgb(), alpha, tiny, if (shadow) 0xFF05030A.toInt() else 0)
    }

    /** Draws onto any Android canvas (the screen, or a texture being painted). */
    fun drawTo(canvas: Canvas, text: String, x: Float, y: Float, unit: Float, color: Int, alpha: Float, tiny: Boolean, shadow: Int) {
        if (text.isEmpty() || alpha <= 0.004f) return
        val s = caps(text)
        val cap = setup(unit, tiny)
        val argb = withAlpha(color, alpha)
        paint.color = argb
        shadowColor = if (shadow != 0) withAlpha(shadow, alpha * 0.85f) else 0
        shadowBlur = (unit * 0.9f).coerceAtLeast(1f)
        shadowDx = unit * 0.3f
        shadowDy = unit * 0.55f
        if (shadowColor != 0) paint.setShadowLayer(shadowBlur, shadowDx, shadowDy, shadowColor) else paint.clearShadowLayer()
        val baseline = y + cap
        var pen = x
        var start = 0
        for (i in s.indices) {
            val c = s[i]
            if (!isIcon(c)) continue
            if (i > start) {
                canvas.drawText(s, start, i, pen, baseline, paint)
                pen += run(s, start, i)
            }
            icon(canvas, c, pen + cap * 0.09f, y, cap, argb)
            pen += iconWidth(c, cap) + cap * 0.18f
            start = i + 1
        }
        if (start < s.length) canvas.drawText(s, start, s.length, pen, baseline, paint)
    }

    private fun withAlpha(c: Int, a: Float): Int {
        val al = ((c ushr 24) * a).toInt().coerceIn(0, 255)
        return (al shl 24) or (c and 0xFFFFFF)
    }

    private fun darker(c: Int): Int {
        val r = ((c shr 16) and 255) * 45 / 100
        val g = ((c shr 8) and 255) * 45 / 100
        val b = (c and 255) * 45 / 100
        return (c and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
    }

    /** One vector symbol in a box [cap] tall starting at ([x], [y]). */
    private fun icon(canvas: Canvas, c: Char, x: Float, y: Float, cap: Float, argb: Int) {
        val p = iconPaint
        p.style = Paint.Style.FILL
        p.pathEffect = null
        p.color = argb
        if (shadowColor != 0) p.setShadowLayer(shadowBlur, shadowDx, shadowDy, shadowColor) else p.clearShadowLayer()
        path.reset()
        when (c) {
            PLAY -> {
                path.moveTo(x, y)
                path.lineTo(x + cap * 0.9f, y + cap / 2f)
                path.lineTo(x, y + cap)
                path.close()
                p.pathEffect = CornerPathEffect(cap * 0.15f)
                canvas.drawPath(path, p)
            }
            STAR -> {
                star(x + cap * 0.575f, y + cap * 0.55f, cap * 0.64f)
                canvas.drawPath(path, p)
            }
            HEART -> {
                val w = cap * 1.15f
                val cx = x + w / 2f
                path.moveTo(cx, y + cap)
                path.cubicTo(x - w * 0.05f, y + cap * 0.58f, x, y - cap * 0.02f, cx, y + cap * 0.24f)
                path.cubicTo(x + w, y - cap * 0.02f, x + w * 1.05f, y + cap * 0.58f, cx, y + cap)
                path.close()
                canvas.drawPath(path, p)
            }
            TOKEN -> {
                val r = cap * 0.575f
                val cx = x + r
                val cy = y + cap / 2f
                canvas.drawCircle(cx, cy, r, p)
                p.clearShadowLayer()
                p.color = darker(argb)
                p.style = Paint.Style.STROKE
                p.strokeWidth = cap * 0.09f
                canvas.drawCircle(cx, cy, r * 0.72f, p)
                p.style = Paint.Style.FILL
                star(cx, cy + r * 0.04f, r * 0.5f)
                canvas.drawPath(path, p)
                p.color = argb
            }
            TICKET -> {
                val w = cap * 1.5f
                val top = y + cap * 0.1f
                val bottom = y + cap * 0.95f
                path.addRoundRect(x, top, x + w, bottom, cap * 0.12f, cap * 0.12f, Path.Direction.CW)
                cut.reset()
                val mid = (top + bottom) / 2f
                cut.addCircle(x, mid, cap * 0.16f, Path.Direction.CW)
                cut.addCircle(x + w, mid, cap * 0.16f, Path.Direction.CW)
                path.op(cut, Path.Op.DIFFERENCE)
                canvas.drawPath(path, p)
                p.clearShadowLayer()
                p.color = darker(argb)
                p.style = Paint.Style.STROKE
                p.strokeWidth = cap * 0.07f
                canvas.drawRoundRect(x + cap * 0.26f, top + cap * 0.14f, x + w - cap * 0.26f, bottom - cap * 0.14f, cap * 0.06f, cap * 0.06f, p)
                p.style = Paint.Style.FILL
                p.color = argb
            }
            NOTE -> {
                val stemX = x + cap * 0.52f
                canvas.save()
                canvas.rotate(-22f, x + cap * 0.3f, y + cap * 0.82f)
                canvas.drawOval(x + cap * 0.02f, y + cap * 0.66f, x + cap * 0.58f, y + cap * 1.0f, p)
                canvas.restore()
                canvas.drawRect(stemX - cap * 0.08f, y, stemX + cap * 0.04f, y + cap * 0.82f, p)
                path.moveTo(stemX - cap * 0.06f, y)
                path.cubicTo(stemX + cap * 0.1f, y + cap * 0.2f, stemX + cap * 0.5f, y + cap * 0.22f, stemX + cap * 0.36f, y + cap * 0.62f)
                path.cubicTo(stemX + cap * 0.36f, y + cap * 0.38f, stemX + cap * 0.14f, y + cap * 0.34f, stemX - cap * 0.06f, y + cap * 0.3f)
                path.close()
                canvas.drawPath(path, p)
            }
            LEFT, RIGHT, UP, DOWN -> {
                val w = iconWidth(c, cap)
                val cx = x + w / 2f
                val cy = y + cap / 2f
                val len = if (c == LEFT || c == RIGHT) w else cap
                val half = len / 2f
                // An arrow pointing right, turned to face its way.
                path.moveTo(cx - half, cy - cap * 0.11f)
                path.lineTo(cx + half - cap * 0.42f, cy - cap * 0.11f)
                path.lineTo(cx + half - cap * 0.42f, cy - cap * 0.4f)
                path.lineTo(cx + half, cy)
                path.lineTo(cx + half - cap * 0.42f, cy + cap * 0.4f)
                path.lineTo(cx + half - cap * 0.42f, cy + cap * 0.11f)
                path.lineTo(cx - half, cy + cap * 0.11f)
                path.close()
                canvas.save()
                canvas.rotate(
                    when (c) {
                        LEFT -> 180f
                        UP -> -90f
                        DOWN -> 90f
                        else -> 0f
                    },
                    cx, cy,
                )
                p.pathEffect = CornerPathEffect(cap * 0.06f)
                canvas.drawPath(path, p)
                canvas.restore()
            }
        }
        p.pathEffect = null
    }

    private fun star(cx: Float, cy: Float, r: Float) {
        path.reset()
        for (k in 0 until 10) {
            val a = -Math.PI.toFloat() / 2f + k * Math.PI.toFloat() / 5f
            val rr = if (k % 2 == 0) r else r * 0.45f
            val px = cx + cos(a) * rr
            val py = cy + sin(a) * rr
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
    }
}
