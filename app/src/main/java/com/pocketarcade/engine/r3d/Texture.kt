package com.pocketarcade.engine.r3d

import com.pocketarcade.engine.PixelCanvas

/** An ARGB texel grid the rasterizer samples from (nearest-neighbour). */
class Texture(val width: Int, val height: Int, val pixels: IntArray = IntArray(width * height)) {
    companion object {
        fun of(canvas: PixelCanvas) = Texture(canvas.w, canvas.h, canvas.px)
    }

    fun region(x: Int = 0, y: Int = 0, w: Int = width, h: Int = height, wrap: Boolean = false) =
        Region(this, x, y, w, h, wrap)

    val full: Region by lazy { Region(this, 0, 0, width, height, false) }
}

/**
 * A rectangle of a [Texture]. Texture coordinates passed to the renderer are texel offsets
 * inside the region; with [wrap] they repeat, otherwise they clamp to the edge.
 */
class Region(val tex: Texture, val x: Int, val y: Int, val w: Int, val h: Int, val wrap: Boolean = false)
