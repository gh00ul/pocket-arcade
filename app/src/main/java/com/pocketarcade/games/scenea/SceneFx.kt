package com.pocketarcade.games.scenea

import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Texture
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Small lighting-and-glow toolkit shared by the claw, skee-ball, whack-a-mole and coin-pusher
 * scenes: soft rings, light shafts and flares built from a handful of pure-math textures (no
 * Bitmap, so they are safe to build anywhere) and thin helpers that draw them additively.
 *
 * Everything here is presentation: nothing reads or writes a game's simulation or its `rng`, and
 * every helper is allocation-free once the textures exist.
 */
internal object SceneFx {
    /** A thin bright band at [RING_RADIUS] of the sprite's half-width, inside a faint halo. */
    const val RING_RADIUS = 0.8f
    private const val RING_WIDTH = 0.07f
    private const val RING_HALO = 0.16f

    /** Pixel alpha (0..255) for a soft-edged ring; exposed so tests can check the profile. */
    internal fun ringAlpha(d: Float): Int {
        val z = (d - RING_RADIUS) / RING_WIDTH
        val band = exp(-z * z)
        val halo = (1f - d).coerceIn(0f, 1f).let { it * it } * RING_HALO
        // Fade to nothing over the last 7% so the quad's edge never shows.
        val edge = 1f - ((d - 0.93f) / 0.07f).coerceIn(0f, 1f)
        return (((band + halo) * edge).coerceIn(0f, 1f) * 255f).toInt()
    }

    /** A soft ring for shockwaves and pulses (white; tint it when drawing). */
    val ring: Texture by lazy {
        val n = 64
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f - n / 2f) / (n / 2f)
            val dy = (y + 0.5f - n / 2f) / (n / 2f)
            px[y * n + x] = (ringAlpha(sqrt(dx * dx + dy * dy)) shl 24) or 0xFFFFFF
        }
        Texture(n, n, px)
    }

    /**
     * A light shaft: soft on both long edges, brightest at the top (row 0) and fading to nothing
     * at the bottom, so a beam hung from a lamp thins out as it falls.
     */
    val shaft: Texture by lazy {
        val w = 16
        val h = 64
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val u = (x + 0.5f) / w * 2f - 1f
            val edge = (1f - abs(u)).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
            val along = (1f - (y + 0.5f) / h).let { it * it }
            px[y * w + x] = ((edge * along * 255f).toInt() shl 24) or 0xFFFFFF
        }
        Texture(w, h, px)
    }

    /** A four-point flare: a bright core with a long thin cross, for jackpots and glints. */
    val flare: Texture by lazy {
        val n = 64
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = abs((x + 0.5f - n / 2f) / (n / 2f))
            val dy = abs((y + 0.5f - n / 2f) / (n / 2f))
            val arms = exp(-dx * 9f) * exp(-dy * 42f) + exp(-dy * 9f) * exp(-dx * 42f)
            val core = exp(-(dx * dx + dy * dy) * 40f)
            val edge = 1f - (sqrt(dx * dx + dy * dy) - 0.85f).div(0.15f).coerceIn(0f, 1f)
            px[y * n + x] = (((arms * 0.75f + core).coerceIn(0f, 1f) * edge * 255f).toInt() shl 24) or 0xFFFFFF
        }
        Texture(n, n, px)
    }

    /** A soft horizontal band, faded on all four sides: sheens that sweep across signs and glass. */
    val streak: Texture by lazy {
        val w = 64
        val h = 16
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val u = (x + 0.5f) / w * 2f - 1f
            val v = (y + 0.5f) / h * 2f - 1f
            val a = (1f - abs(u)).coerceIn(0f, 1f) * (1f - abs(v)).coerceIn(0f, 1f)
            px[y * w + x] = ((a * a * 255f).toInt() shl 24) or 0xFFFFFF
        }
        Texture(w, h, px)
    }

    /** A vertical gradient of [top] to [bottom] (opaque), for backdrops and panels. */
    fun gradient(w: Int, h: Int, top: Int, bottom: Int): Texture {
        val px = IntArray(w * h)
        for (y in 0 until h) {
            val t = y / (h - 1).coerceAtLeast(1).toFloat()
            val c = com.pocketarcade.engine.Pal.mix(top, bottom, t)
            for (x in 0 until w) px[y * w + x] = c
        }
        return Texture(w, h, px)
    }

    // ------------------------------------------------------------------ drawing helpers

    /** A soft additive light pool lying flat at height [y]. */
    fun pool(r: Renderer3D, x: Float, y: Float, z: Float, w: Float, d: Float, color: Int, alpha: Float) {
        if (alpha <= 0.004f) return
        r.flat(x, z, y, w, d, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = alpha, tint = color)
    }

    /** A soft additive glow facing the camera. */
    fun glow(r: Renderer3D, x: Float, y: Float, z: Float, size: Float, color: Int, alpha: Float) {
        if (alpha <= 0.004f) return
        r.sprite(x, y, z, size, size, TexKit.glow.full, blend = Blend.ADD, emissive = 1f, alpha = alpha, tint = color)
    }

    /** An expanding ring lying flat at height [y] (impacts on a table, board or floor). */
    fun shockwave(r: Renderer3D, x: Float, y: Float, z: Float, diameter: Float, color: Int, alpha: Float) {
        if (alpha <= 0.004f) return
        r.flat(x, z, y, diameter, diameter, ring.full, blend = Blend.ADD, emissive = 1f, alpha = alpha, tint = color)
    }

    /** An expanding ring facing the camera (bursts in the air). */
    fun burstRing(r: Renderer3D, x: Float, y: Float, z: Float, diameter: Float, color: Int, alpha: Float) {
        if (alpha <= 0.004f) return
        r.sprite(x, y, z, diameter, diameter, ring.full, blend = Blend.ADD, emissive = 1f, alpha = alpha, tint = color)
    }

    /** A four-point flare facing the camera, turned by [roll]. */
    fun flare(r: Renderer3D, x: Float, y: Float, z: Float, size: Float, color: Int, alpha: Float, roll: Float = 0f) {
        if (alpha <= 0.004f) return
        r.sprite(x, y, z, size, size, flare.full, roll = roll, blend = Blend.ADD, emissive = 1f, alpha = alpha, tint = color)
    }

    /** A light shaft [width] wide from a lamp at the first point, thinning out towards the second. */
    fun shaft(
        r: Renderer3D, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
        width: Float, color: Int, alpha: Float,
    ) {
        if (alpha <= 0.004f) return
        r.beam(x0, y0, z0, x1, y1, z1, width, shaft.full, blend = Blend.ADD, emissive = 1f, alpha = alpha, tint = color)
    }

    /** Scales a colour's brightness by [k] (for fading an additive sprite through its tint). */
    fun dim(argb: Int, k: Float): Int = com.pocketarcade.engine.Pal.shade(argb, k.coerceIn(0f, 1f))
}
