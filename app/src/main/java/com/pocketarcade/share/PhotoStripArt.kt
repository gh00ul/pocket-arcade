package com.pocketarcade.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import com.pocketarcade.engine.ArcadeFont
import com.pocketarcade.engine.Pal

/**
 * Paints a photo strip: the arcade's name over four framed shots one under another, and the date
 * underneath, on the arcade's night-purple card. The geometry comes from [StripLayout]. This
 * must run on the UI thread, like every other use of [ArcadeFont].
 */
object PhotoStripArt {
    /**
     * Composes the strip from [shots] (a null one, not developed in time, is a dark frame), under
     * [arcadeName] and [date]. Shots are cropped to fit their frames, never squashed.
     */
    fun compose(shots: List<Bitmap?>, arcadeName: String, date: String): Bitmap {
        val layout = StripLayout(PhotoStrip.FRAME)
        val strip = createBitmap(layout.width, layout.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(strip)
        val w = layout.width.toFloat()
        val h = layout.height.toFloat()

        // The card: the arcade's night, lit a little from the top.
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h, Pal.INDIGO, Pal.NIGHT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        // A neon rim just inside the edge.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = layout.margin * 0.16f
        paint.color = Pal.PINK
        val rim = layout.margin * 0.4f
        c.drawRoundRect(RectF(rim, rim, w - rim, h - rim), layout.margin * 0.7f, layout.margin * 0.7f, paint)
        paint.style = Paint.Style.FILL

        // The header: the name as big as fits, and what this is.
        val head = layout.header
        val nameUnit = PhotoStrip.fitUnit(ArcadeFont.width(arcadeName, 1f), head.width.toFloat(), maxUnit = head.height * 0.5f / ArcadeFont.CAP)
        val nameH = ArcadeFont.height(nameUnit)
        val subUnit = head.height * 0.14f / ArcadeFont.TINY_CAP
        val subH = ArcadeFont.height(subUnit, tiny = true)
        val blockTop = head.top + (head.height - (nameH + subH + head.height * 0.12f)) / 2f
        centred(c, arcadeName, head.left + head.width / 2f, blockTop, nameUnit, Pal.YELLOW, tiny = false)
        centred(c, "${ArcadeFont.STAR} PHOTO BOOTH ${ArcadeFont.STAR}", head.left + head.width / 2f, blockTop + nameH + head.height * 0.12f, subUnit, Pal.SKY, tiny = true)

        // The shots, each in a white frame with a soft shadow under it.
        for ((i, r) in layout.frames.withIndex()) {
            val dst = RectF(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat())
            val border = layout.margin * 0.16f
            paint.color = 0x66000000
            c.drawRect(dst.left + border, dst.top + border * 1.4f, dst.right + border, dst.bottom + border * 1.4f, paint)
            val shot = shots.getOrNull(i)
            if (shot != null && !shot.isRecycled) {
                val crop = PhotoStrip.cropToAspect(shot.width, shot.height, r.width, r.height)
                c.drawBitmap(shot, Rect(crop.left, crop.top, crop.right, crop.bottom), dst, paint)
            } else {
                paint.color = Pal.BLACK
                c.drawRect(dst, paint)
            }
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = border
            paint.color = Pal.WHITE
            c.drawRect(dst.left - border / 2f, dst.top - border / 2f, dst.right + border / 2f, dst.bottom + border / 2f, paint)
            paint.style = Paint.Style.FILL
        }

        // The date at the foot.
        val foot = layout.footer
        val dateUnit = PhotoStrip.fitUnit(ArcadeFont.width(date, 1f), foot.width.toFloat(), maxUnit = foot.height * 0.3f / ArcadeFont.CAP)
        centred(c, date, foot.left + foot.width / 2f, foot.top + (foot.height - ArcadeFont.height(dateUnit)) / 2f, dateUnit, Pal.LAVENDER, tiny = false)
        return strip
    }

    private fun centred(c: Canvas, text: String, cx: Float, top: Float, unit: Float, color: Int, tiny: Boolean) {
        val x = cx - ArcadeFont.width(text, unit, tiny) / 2f
        ArcadeFont.drawTo(c, text, x, top, unit, color, 1f, tiny, 0xFF05030A.toInt())
    }
}
