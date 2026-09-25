package com.pocketarcade.engine.r3d

/**
 * An ARGB image the GPU samples from. Pixels are owned by the UI thread; after changing them
 * call [touch] so the GL thread uploads the new version.
 *
 * [width] and [height] are the texture's size in texels as drawing code sees it (texture
 * coordinates are measured in them); the image itself can be stored [scale] times finer, so
 * art can be painted in high detail without changing any of the code that maps it.
 */
class Texture(val width: Int, val height: Int, val pixels: IntArray = IntArray(width * height), val scale: Int = 1) {
    /** Stored image size in pixels. */
    val pixelWidth get() = width * scale
    val pixelHeight get() = height * scale

    /** Linear filtering with mipmaps (true) or nearest-neighbour (false). */
    var smooth = true

    /** Tiles when texture coordinates run past its edges (set by wrapping regions). */
    var repeat = false

    @Volatile
    var version = 0
        private set

    /** Marks the pixels as changed so the GPU copy is refreshed. */
    fun touch() {
        version++
    }

    // Snapshot of a changing texture's pixels, taken on the UI thread for the GL thread to upload.
    @Volatile internal var snap: IntArray? = null
    @Volatile internal var snapVersion = 0

    /** Called while recording a frame: freezes a copy of pixels that changed since the last frame. */
    internal fun prepare() {
        val v = version
        if (v != 0 && snapVersion != v) {
            snap = pixels.copyOf()
            snapVersion = v
        }
    }

    /** Pixels the GL thread should upload: the frozen copy for changing textures, else the originals. */
    internal fun uploadSource(): IntArray = if (snapVersion != 0) snap ?: pixels else pixels

    // Owned by the GL thread.
    internal var glId = 0
    internal var glVersion = -1
    internal var glGen = -1

    fun region(x: Int = 0, y: Int = 0, w: Int = width, h: Int = height, wrap: Boolean = false): Region {
        if (wrap) repeat = true
        return Region(this, x, y, w, h, wrap)
    }

    val full: Region by lazy { Region(this, 0, 0, width, height, false) }
}

/**
 * A rectangle of a [Texture]. Texture coordinates passed to the renderer are texel offsets
 * inside the region; with [wrap] they repeat (only valid for a whole texture).
 */
class Region(val tex: Texture, val x: Int, val y: Int, val w: Int, val h: Int, val wrap: Boolean = false)
