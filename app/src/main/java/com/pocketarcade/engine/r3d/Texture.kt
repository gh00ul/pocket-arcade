package com.pocketarcade.engine.r3d

import java.util.concurrent.atomic.AtomicReference

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

    /** A frozen copy of a changing texture's pixels, handed from the UI thread to the GL thread. */
    internal class Snapshot(val pixels: IntArray) {
        var version = 0
    }

    // The newest snapshot the GL thread hasn't taken yet, and up to two spare buffers the GL
    // thread hands back, so a live screen repainting every frame reuses three arrays instead of
    // allocating a copy per change.
    private val pending = AtomicReference<Snapshot?>(null)
    private val spareA = AtomicReference<Snapshot?>(null)
    private val spareB = AtomicReference<Snapshot?>(null)

    /** Version of the newest snapshot taken (0 for a texture that never changes). */
    @Volatile internal var snapVersion = 0
        private set

    /** Called while recording a frame: freezes a copy of pixels that changed since the last frame. */
    internal fun prepare() {
        val v = version
        if (v != 0 && snapVersion != v) {
            val s = spareA.getAndSet(null) ?: spareB.getAndSet(null) ?: Snapshot(IntArray(pixels.size))
            System.arraycopy(pixels, 0, s.pixels, 0, pixels.size)
            s.version = v
            // A snapshot the GL thread never took is still ours: keep it as a spare.
            pending.getAndSet(s)?.let { recycle(it) }
            snapVersion = v
        }
    }

    /** GL thread: takes the newest snapshot not yet uploaded, if any. */
    internal fun takeSnapshot(): Snapshot? = pending.getAndSet(null)

    /** Returns a snapshot nobody reads any more to the spares (dropped if both are full). */
    internal fun recycle(s: Snapshot) {
        if (!spareA.compareAndSet(null, s)) spareB.compareAndSet(null, s)
    }

    // Owned by the GL thread.
    internal var glId = 0
    internal var glVersion = -1
    internal var glGen = -1
    /** The snapshot currently on the GPU (kept to re-upload after a context loss). */
    internal var glSnap: Snapshot? = null

    fun region(x: Int = 0, y: Int = 0, w: Int = width, h: Int = height, wrap: Boolean = false): Region {
        if (wrap) repeat = true
        return Region(this, x, y, w, h, wrap)
    }

    val full: Region by lazy { Region(this, 0, 0, width, height, false) }

    internal companion object {
        /**
         * Converts ARGB [src] to premultiplied RGBA bytes packed little-endian into [dst], so
         * filtering and mipmaps never pull dark fringes out of transparent texels.
         */
        fun premultiplyToRgba(src: IntArray, dst: IntArray, count: Int) {
            for (i in 0 until count) {
                val c = src[i]
                val a = c ushr 24
                dst[i] = when (a) {
                    255 -> (c and -0xff0100) or (c shr 16 and 0xFF) or (c and 0xFF shl 16)
                    0 -> 0
                    else -> {
                        val r = ((c shr 16 and 0xFF) * a + 127) / 255
                        val g = ((c shr 8 and 0xFF) * a + 127) / 255
                        val b = ((c and 0xFF) * a + 127) / 255
                        (a shl 24) or (b shl 16) or (g shl 8) or r
                    }
                }
            }
        }
    }
}

/**
 * A rectangle of a [Texture]. Texture coordinates passed to the renderer are texel offsets
 * inside the region; with [wrap] they repeat (only valid for a whole texture).
 */
class Region(val tex: Texture, val x: Int, val y: Int, val w: Int, val h: Int, val wrap: Boolean = false)
