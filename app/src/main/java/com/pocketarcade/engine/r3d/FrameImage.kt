package com.pocketarcade.engine.r3d

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** Copies a [Renderer3D] framebuffer into a reusable bitmap and draws it scaled up, pixel-sharp. */
class FrameImage {
    private var bitmap: Bitmap? = null
    private var image: ImageBitmap? = null

    fun update(r: Renderer3D): ImageBitmap {
        var b = bitmap
        if (b == null || b.width != r.width || b.height != r.height) {
            b = Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888)
            bitmap = b
            image = b.asImageBitmap()
        }
        b.setPixels(r.color, 0, r.width, 0, 0, r.width, r.height)
        return image!!
    }

    /** Draws the latest frame to fill ([x], [y], [w], [h]) in the current draw coordinates. */
    fun draw(scope: DrawScope, r: Renderer3D, x: Float, y: Float, w: Float, h: Float) {
        val img = update(r)
        scope.drawImage(
            image = img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(x.roundToInt(), y.roundToInt()),
            dstSize = IntSize(w.roundToInt(), h.roundToInt()),
            filterQuality = FilterQuality.None,
        )
    }
}

/**
 * Tracks how long 3D frames take and suggests a coarser pixel scale when rendering runs slow,
 * so busy scenes stay at 60 fps on slower phones.
 */
class FrameBudget(private val budgetMs: Float = 7.5f, private val name: String = "3d") {
    companion object {
        const val TAG = "PocketArcade3D"

        /** Logs the average 3D frame time once a second. */
        var logging = false
    }

    private var sum = 0f
    private var count = 0
    private var start = 0L
    var averageMs = 0f
        private set

    fun begin() {
        start = SystemClock.elapsedRealtimeNanos()
    }

    /** Returns true when the average over the last second is over budget. */
    fun end(): Boolean {
        sum += (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000f
        count++
        if (count >= 60) {
            averageMs = sum / count
            sum = 0f
            count = 0
            if (logging) android.util.Log.d(TAG, "%s render %.2f ms/frame".format(name, averageMs))
            return averageMs > budgetMs
        }
        return false
    }
}
