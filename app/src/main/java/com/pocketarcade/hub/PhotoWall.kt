package com.pocketarcade.hub

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.pocketarcade.engine.r3d.Fonts
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.share.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where the photo wall hangs and how its picture is laid out: the last [SLOTS] strips as tall
 * posters side by side on the right wall, above the booth and level with it, under a neon sign.
 * The posters share one texture, [POSTER_W] by [POSTER_H] texels each. Pure numbers, so the JVM
 * tests can check that it fits the wall and keeps a strip's shape.
 */
object PhotoWallLayout {
    /** One poster per strip kept ([PhotoStore.KEEP]). */
    const val SLOTS = PhotoStore.KEEP

    /** Each poster's texels: a strip is about 1 to 4.2, and 64 by 268 keeps that. */
    const val POSTER_W = 64
    const val POSTER_H = 268
    const val TEX_W = SLOTS * POSTER_W
    const val TEX_H = POSTER_H

    /** On the wall, in world units: each poster's width along the wall, the gap between, and the height band. */
    const val WORLD_W = 10f
    const val WORLD_GAP = 3f
    const val Y0 = 84f
    const val Y1 = 126f

    /** The neon sign over the posters. */
    const val SIGN_Y0 = 127f
    const val SIGN_Y1 = 139.5f

    /** How far the wall is from the booth's back (it hangs on the wall beside its side). */
    const val REACH = 12f

    /** The band's length along the wall. */
    const val WORLD_LENGTH = SLOTS * WORLD_W + (SLOTS - 1) * WORLD_GAP

    /** Where the band starts along the wall (z) so it's centred on [booth]. */
    fun startZ(booth: Prop): Float = booth.centerZ - WORLD_LENGTH / 2f

    /** Where poster [slot] starts along the wall (z); slot 0, the newest strip, is the first from the north. */
    fun posterZ(booth: Prop, slot: Int): Float = startZ(booth) + slot * (WORLD_W + WORLD_GAP)

    /** The left edge of poster [slot] in the texture. */
    fun texX(slot: Int): Int = slot * POSTER_W

    /** Which of the posters hold a strip when [strips] are saved: the newest first, the rest stay blank. */
    fun filled(strips: Int): BooleanArray = BooleanArray(SLOTS) { it < strips }
}

/**
 * The photo wall's picture: one live [texture] the booth's model shows, holding the newest saved
 * strips as posters and a blank "PHOTO WALL" frame for each poster still empty. It starts out
 * blank, and [refresh] repaints it (at the start, and whenever the booth saves a strip), so the
 * wall changes without the hall's models being rebuilt.
 */
object PhotoWall {
    private const val FRAME = 2f

    /** The wall's texture, blank until the first [refresh]; shared by every hall scene. */
    val texture: Texture by lazy {
        val t = Texture(PhotoWallLayout.TEX_W, PhotoWallLayout.TEX_H)
        paint(emptyList(), t)
        t
    }

    /**
     * Reloads the strips saved under [filesDir] and repaints the wall with them. Decoding runs
     * off the UI thread; the painting, which touches the texture the frame is recorded from,
     * runs on it.
     */
    suspend fun refresh(filesDir: File) {
        val strips = withContext(Dispatchers.IO) {
            PhotoStore.list(PhotoStore.dir(filesDir)).take(PhotoWallLayout.SLOTS).mapNotNull { decode(it) }
        }
        withContext(Dispatchers.Main) {
            paint(strips, texture)
            for (b in strips) b.recycle()
        }
    }

    /** A strip's PNG read at a quarter of its size (plenty for a poster this small), or null if it can't be read. */
    private fun decode(file: File): Bitmap? = try {
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 })
    } catch (_: Exception) {
        null
    }

    /** Paints [strips] (newest first) into [into], a blank frame for every poster without one. */
    private fun paint(strips: List<Bitmap>, into: Texture) {
        val tp = TexPaint(PhotoWallLayout.TEX_W, PhotoWallLayout.TEX_H)
        tp.clear(0)
        val filled = PhotoWallLayout.filled(strips.size)
        // The painter's own paint is reset for every shape it draws, which drops the smoothing.
        val smooth = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        for (slot in 0 until PhotoWallLayout.SLOTS) {
            val x = PhotoWallLayout.texX(slot).toFloat()
            val w = PhotoWallLayout.POSTER_W.toFloat()
            val h = PhotoWallLayout.POSTER_H.toFloat()
            if (filled[slot]) {
                val src = strips[slot]
                tp.canvas.drawBitmap(src, Rect(0, 0, src.width, src.height), RectF(x, 0f, x + w, h), smooth)
            } else {
                blank(tp, x, w, h)
            }
            // A white border, like the strip's own frames.
            tp.strokeRound(x + FRAME / 2f, FRAME / 2f, w - FRAME, h - FRAME, 1f, FRAME, 0xFFF4F0FF.toInt())
        }
        tp.update(into)
        tp.recycle()
    }

    /** An empty frame: a dark card asking for photos. */
    private fun blank(tp: TexPaint, x: Float, w: Float, h: Float) {
        tp.vgrad(x, 0f, w, h, 0xFF2A1A52.toInt(), 0xFF120A24.toInt())
        val cx = x + w / 2f
        tp.text("PHOTO", cx, h * 0.42f, 15f, 0xFF7ACBFF.toInt(), Fonts.display)
        tp.text("WALL", cx, h * 0.42f + 20f, 15f, 0xFF7ACBFF.toInt(), Fonts.display)
        // A little lens over the words.
        tp.circle(cx, h * 0.3f, 9f, 0xFF3A2A6A.toInt())
        tp.ring(cx, h * 0.3f, 9f, 2f, 0xFFF4F0FF.toInt())
        tp.circle(cx, h * 0.3f, 4f, 0xFF7ACBFF.toInt())
    }
}
