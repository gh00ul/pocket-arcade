package com.pocketarcade.engine.r3d

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Painter
import kotlin.random.Random

/** The app's typefaces: heavy for signs and marquees, condensed for labels, plain for body text. */
object Fonts {
    val display: Typeface by lazy { Typeface.create("sans-serif-black", Typeface.NORMAL) }
    val heavy: Typeface by lazy { Typeface.create("sans-serif", Typeface.BOLD) }
    val condensed: Typeface by lazy { Typeface.create("sans-serif-condensed", Typeface.BOLD) }
    val body: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.NORMAL) }
}

/**
 * Paints smooth, anti-aliased textures with Android's 2D canvas: gradients, rounded shapes,
 * real fonts, soft glows and grain. Call [toTexture] for a static texture, or keep the painter
 * and [update] a changing one every frame.
 */
class TexPaint(val w: Int, val h: Int) {
    val bitmap: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private var pixels: IntArray? = null

    /** Pixels per drawing unit: above 1 when painting in texel units at a finer resolution. */
    var unit = 1f
        private set

    /** From now on draw in units of [scale] pixels (see [paintTexture]). */
    fun useUnits(scale: Float) {
        unit = scale
        canvas.setMatrix(null)
        canvas.scale(scale, scale)
    }

    private fun reset(color: Int) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = color
        paint.style = Paint.Style.FILL
    }

    fun clear(color: Int = 0) {
        bitmap.eraseColor(color)
    }

    fun fill(color: Int) = rect(0f, 0f, w.toFloat(), h.toFloat(), color)

    fun rect(x: Float, y: Float, rw: Float, rh: Float, color: Int) {
        reset(color)
        canvas.drawRect(x, y, x + rw, y + rh, paint)
    }

    fun round(x: Float, y: Float, rw: Float, rh: Float, r: Float, color: Int) {
        reset(color)
        rect.set(x, y, x + rw, y + rh)
        canvas.drawRoundRect(rect, r, r, paint)
    }

    fun circle(cx: Float, cy: Float, r: Float, color: Int) {
        reset(color)
        canvas.drawCircle(cx, cy, r, paint)
    }

    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
        reset(color)
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        canvas.drawOval(rect, paint)
    }

    fun line(x0: Float, y0: Float, x1: Float, y1: Float, width: Float, color: Int, round: Boolean = true) {
        reset(color)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.strokeCap = if (round) Paint.Cap.ROUND else Paint.Cap.BUTT
        canvas.drawLine(x0, y0, x1, y1, paint)
    }

    fun strokeRound(x: Float, y: Float, rw: Float, rh: Float, r: Float, width: Float, color: Int) {
        reset(color)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        rect.set(x, y, x + rw, y + rh)
        canvas.drawRoundRect(rect, r, r, paint)
    }

    /** Cuts a fully transparent round hole. */
    fun punch(cx: Float, cy: Float, r: Float) {
        reset(0)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        canvas.drawCircle(cx, cy, r, paint)
        paint.xfermode = null
    }

    /** A five-pointed star of outer radius [r]. */
    fun star(cx: Float, cy: Float, r: Float, color: Int, inner: Float = 0.45f) {
        val pts = FloatArray(20)
        for (k in 0 until 10) {
            val a = -Math.PI.toFloat() / 2f + k * Math.PI.toFloat() / 5f
            val rr = if (k % 2 == 0) r else r * inner
            pts[k * 2] = cx + kotlin.math.cos(a) * rr
            pts[k * 2 + 1] = cy + kotlin.math.sin(a) * rr
        }
        polygon(pts, color)
    }

    fun ring(cx: Float, cy: Float, r: Float, width: Float, color: Int) {
        reset(color)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        canvas.drawCircle(cx, cy, r, paint)
    }

    fun polygon(points: FloatArray, color: Int) {
        reset(color)
        val p = Path()
        p.moveTo(points[0], points[1])
        var i = 2
        while (i < points.size) {
            p.lineTo(points[i], points[i + 1])
            i += 2
        }
        p.close()
        canvas.drawPath(p, paint)
    }

    /** A vertical gradient over a rectangle through the given colours (evenly spaced). */
    fun vgrad(x: Float, y: Float, rw: Float, rh: Float, vararg colors: Int) {
        reset(-1)
        paint.shader = LinearGradient(0f, y, 0f, y + rh, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(x, y, x + rw, y + rh, paint)
        paint.shader = null
    }

    fun hgrad(x: Float, y: Float, rw: Float, rh: Float, vararg colors: Int) {
        reset(-1)
        paint.shader = LinearGradient(x, 0f, x + rw, 0f, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRect(x, y, x + rw, y + rh, paint)
        paint.shader = null
    }

    /** A rounded rectangle filled with a vertical gradient. */
    fun roundGrad(x: Float, y: Float, rw: Float, rh: Float, r: Float, top: Int, bottom: Int) {
        reset(-1)
        paint.shader = LinearGradient(0f, y, 0f, y + rh, top, bottom, Shader.TileMode.CLAMP)
        rect.set(x, y, x + rw, y + rh)
        canvas.drawRoundRect(rect, r, r, paint)
        paint.shader = null
    }

    /** A soft radial blob fading from [inner] at the centre to [outer] at radius [r]. */
    fun radial(cx: Float, cy: Float, r: Float, inner: Int, outer: Int) {
        reset(-1)
        paint.shader = RadialGradient(cx, cy, r.coerceAtLeast(0.5f), inner, outer, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }

    /** A shaded sphere-like disc: lit from the top left with a soft rim. */
    fun ball(cx: Float, cy: Float, r: Float, color: Int, light: Float = 0.45f) {
        reset(-1)
        val hi = mixArgb(color, -1, light)
        val lo = mixArgb(color, 0xFF000000.toInt(), 0.45f)
        paint.shader = RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.4f, intArrayOf(hi, color, lo), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r, paint)
        paint.shader = null
    }

    /** Text with its top-left, centre or right edge at [x] and its baseline at [y]. */
    fun text(
        s: String, x: Float, y: Float, size: Float, color: Int,
        face: Typeface = Fonts.display, align: Paint.Align = Paint.Align.CENTER, spacing: Float = 0f,
    ) {
        reset(color)
        paint.typeface = face
        paint.textSize = size
        paint.textAlign = align
        paint.letterSpacing = spacing
        canvas.drawText(s, x, y, paint)
    }

    /** Text with a blurred halo behind it, for neon and lit signs. */
    fun glowText(
        s: String, x: Float, y: Float, size: Float, color: Int, glow: Int, radius: Float,
        face: Typeface = Fonts.display, spacing: Float = 0f,
    ) {
        reset(glow)
        paint.typeface = face
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        paint.letterSpacing = spacing
        paint.maskFilter = BlurMaskFilter(radius.coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawText(s, x, y, paint)
        canvas.drawText(s, x, y, paint)
        paint.maskFilter = null
        paint.color = color
        canvas.drawText(s, x, y, paint)
    }

    /** Outlined text (a stroke under the fill), for marquees and labels that must read anywhere. */
    fun outlinedText(
        s: String, x: Float, y: Float, size: Float, color: Int, outline: Int, stroke: Float,
        face: Typeface = Fonts.display, spacing: Float = 0f,
    ) {
        reset(outline)
        paint.typeface = face
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        paint.letterSpacing = spacing
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawText(s, x, y, paint)
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawText(s, x, y, paint)
    }

    fun textWidth(s: String, size: Float, face: Typeface = Fonts.display, spacing: Float = 0f): Float {
        paint.typeface = face
        paint.textSize = size
        paint.letterSpacing = spacing
        return paint.measureText(s)
    }

    /** Draws a soft, blurred [color] halo in the shape of whatever [block] paints (glows, shadows). */
    fun glow(radius: Float, color: Int, block: TexPaint.() -> Unit) {
        val layer = TexPaint(w, h)
        if (unit != 1f) layer.useUnits(unit)
        layer.block()
        val blur = Paint()
        blur.maskFilter = BlurMaskFilter((radius * unit).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        val offset = IntArray(2)
        val alpha = layer.bitmap.extractAlpha(blur, offset)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color
        canvas.save()
        canvas.setMatrix(null)
        canvas.drawBitmap(alpha, offset[0].toFloat(), offset[1].toFloat(), p)
        canvas.restore()
        alpha.recycle()
        layer.recycle()
    }

    /**
     * Game-style text (see [ArcadeFont]) with capitals [cap] units tall and their top at [y],
     * centred on [x] or starting there; [shadow] (ARGB, 0 for none) adds a soft drop shadow.
     */
    fun label(s: String, x: Float, y: Float, cap: Float, color: Int, centered: Boolean = true, tiny: Boolean = false, shadow: Int = 0) {
        val u = cap / (if (tiny) ArcadeFont.TINY_CAP else ArcadeFont.CAP)
        val left = if (centered) x - ArcadeFont.width(s, u, tiny) / 2f else x
        ArcadeFont.drawTo(canvas, s, left, y, u, color, 1f, tiny, shadow)
    }

    /** Sprinkles random brightness variation over the painted pixels (fabric, paint, wood grain). */
    fun grain(amount: Float, seed: Int = 1) {
        val px = snapshot()
        val rnd = Random(seed)
        for (i in px.indices) {
            val c = px[i]
            if (c ushr 24 == 0) continue
            val k = 1f + (rnd.nextFloat() - 0.5f) * 2f * amount
            val r = ((c shr 16 and 255) * k).toInt().coerceIn(0, 255)
            val g = ((c shr 8 and 255) * k).toInt().coerceIn(0, 255)
            val b = ((c and 255) * k).toInt().coerceIn(0, 255)
            px[i] = (c and -0x1000000) or (r shl 16) or (g shl 8) or b
        }
        bitmap.setPixels(px, 0, w, 0, 0, w, h)
    }

    private fun snapshot(): IntArray {
        val px = pixels ?: IntArray(w * h).also { pixels = it }
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        return px
    }

    /**
     * A new texture with the painted pixels (the painter can keep drawing afterwards). Painted
     * in units, it measures [w] / [unit] texels across.
     */
    fun toTexture(smooth: Boolean = true): Texture {
        val s = unit.toInt().coerceAtLeast(1)
        val t = Texture(w / s, h / s, IntArray(w * h), s)
        bitmap.getPixels(t.pixels, 0, w, 0, 0, w, h)
        t.smooth = smooth
        return t
    }

    /** Copies the painted pixels into [t] (same size) and marks it changed. */
    fun update(t: Texture) {
        bitmap.getPixels(t.pixels, 0, w, 0, 0, w, h)
        t.touch()
    }

    fun recycle() = bitmap.recycle()
}

/**
 * Paints a texture that code maps as [w] × [h] texels at [scale] times that resolution: [draw]
 * works in texel units, so ported drawing code keeps its coordinates and comes out smooth.
 */
fun paintTexture(w: Int, h: Int, scale: Int = 4, draw: TexPaint.() -> Unit): Texture {
    val tp = TexPaint(w * scale, h * scale)
    tp.useUnits(scale.toFloat())
    tp.draw()
    return tp.toTexture().also { tp.recycle() }
}

/**
 * A [Painter] over a [TexPaint]: games' attract-mode drawings (in coarse "art pixel" units)
 * come out as clean, anti-aliased shapes and real text at [scale] texels per unit.
 */
class CanvasPainter(private val tp: TexPaint, private val scale: Float) : Painter {
    override fun fill(x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float) {
        tp.rect(x * scale, y * scale, w * scale, h * scale, withAlpha(color, alpha))
    }

    override fun disc(cx: Float, cy: Float, r: Float, color: Color, alpha: Float) {
        tp.circle(cx * scale, cy * scale, r * scale, withAlpha(color, alpha))
    }

    override fun frame(x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float) {
        val s = scale * 0.6f
        tp.paint.reset()
        tp.paint.isAntiAlias = true
        tp.paint.style = Paint.Style.STROKE
        tp.paint.strokeWidth = s
        tp.paint.color = withAlpha(color, alpha)
        tp.canvas.drawRect(x * scale + s / 2f, y * scale + s / 2f, (x + w) * scale - s / 2f, (y + h) * scale - s / 2f, tp.paint)
    }

    override fun text(text: String, x: Float, y: Float, color: Color, tiny: Boolean, alpha: Float, size: Float) {
        val px = (if (tiny) 6.2f else 8.5f) * size * scale
        tp.text(text, x * scale, y * scale + px * 0.78f, px, withAlpha(color, alpha), Fonts.heavy, Paint.Align.LEFT)
    }

    override fun textCentered(text: String, cx: Float, y: Float, color: Color, tiny: Boolean, alpha: Float, size: Float) {
        val px = (if (tiny) 6.2f else 8.5f) * size * scale
        tp.text(text, cx * scale, y * scale + px * 0.78f, px, withAlpha(color, alpha), Fonts.heavy, Paint.Align.CENTER)
    }

    private fun withAlpha(c: Color, alpha: Float): Int {
        val argb = c.toArgb()
        val a = ((argb ushr 24) * alpha).toInt().coerceIn(0, 255)
        return (a shl 24) or (argb and 0xFFFFFF)
    }
}
