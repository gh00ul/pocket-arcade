package com.pocketarcade.engine.r3d

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * One frame of 3D drawing for one area of the screen, recorded on the UI thread by
 * [Renderer3D] and drawn by the GL thread. Arrays are reused between frames.
 */
class RenderPass internal constructor(private val pool: ConcurrentLinkedQueue<RenderPass>) {
    companion object {
        /** Floats per vertex: position 3, normal 3, uv 2, colour 4, extra 4 (emissive, depth bias, gloss, fog). */
        const val STRIDE = 16
        const val MAX_LIGHTS = 64
        /** Light indices stored per light-grid cell. */
        const val CELL_LIGHTS = 8

        // Draw list entry kinds.
        const val KIND_BATCH = 0
        const val KIND_MODEL = 1
    }

    // Where on the window to draw (pixels, top-left origin) and an optional clip rectangle.
    var vx = 0; var vy = 0; var vw = 1; var vh = 1
    var clip = false
    var cx0 = 0; var cy0 = 0; var cx1 = 0; var cy1 = 0

    // Camera: eye, basis and pinhole projection in viewport pixels.
    val cam = FloatArray(16)

    // Background.
    var clearColor = 0
    var gradientCount = 0
    /** Per gradient: top r, g, b, bottom r, g, b, then y0, y1 as fractions of the viewport height. */
    var gradients = FloatArray(32)

    // Lighting and fog.
    val ambient = FloatArray(3)
    val dirDir = FloatArray(3)
    val dirCol = FloatArray(3)
    var lightCount = 0
    /** Per light: x, y, z, radius, r, g, b, intensity. */
    val lights = FloatArray(MAX_LIGHTS * 8)
    var fogNear = 1e8f
    var fogFar = 2e8f
    var fogFloor = 0f
    var exposure = 1f
    var bloom = 0.8f

    // Light grid over the XZ plane: RGBA bytes, two texels per cell holding 8 light indices + 1.
    var gridW = 0
    var gridH = 0
    var gridX0 = 0f
    var gridZ0 = 0f
    var gridInvCell = 0f
    var grid = ByteArray(0)

    // Immediate geometry.
    var verts = FloatArray(1 shl 14)
    var vertCount = 0
    val textures = ArrayList<Texture>()

    /** Draw list: per entry kind, blend ordinal, texture index or instance index, first vertex, vertex count. */
    var draws = IntArray(64 * 5)
    var drawCount = 0

    // Model instances: model, which blend layer, 4×4 matrix, tint RGBA, emissive boost.
    val models = ArrayList<Model>()
    var instances = FloatArray(64 * 22)
    var instanceCount = 0

    internal fun reset() {
        clip = false
        clearColor = 0
        gradientCount = 0
        lightCount = 0
        fogNear = 1e8f
        fogFar = 2e8f
        fogFloor = 0f
        exposure = 1f
        bloom = 0.8f
        gridW = 0
        gridH = 0
        vertCount = 0
        textures.clear()
        drawCount = 0
        models.clear()
        instanceCount = 0
    }

    /** Hands the pass back to its recorder once the GL thread is done with it. */
    fun recycle() {
        pool.offer(this)
    }

    internal fun ensureVerts(extra: Int) {
        val need = (vertCount + extra) * STRIDE
        if (need > verts.size) verts = verts.copyOf(maxOf(need, verts.size * 2))
    }

    internal fun addDraw(kind: Int, blend: Int, index: Int, first: Int, count: Int) {
        if ((drawCount + 1) * 5 > draws.size) draws = draws.copyOf(draws.size * 2)
        val o = drawCount * 5
        draws[o] = kind; draws[o + 1] = blend; draws[o + 2] = index; draws[o + 3] = first; draws[o + 4] = count
        drawCount++
    }

    internal fun addGradient(top: Int, bottom: Int, y0: Float, y1: Float) {
        if ((gradientCount + 1) * 8 > gradients.size) gradients = gradients.copyOf(gradients.size * 2)
        val o = gradientCount * 8
        gradients[o] = (top shr 16 and 255) / 255f
        gradients[o + 1] = (top shr 8 and 255) / 255f
        gradients[o + 2] = (top and 255) / 255f
        gradients[o + 3] = (bottom shr 16 and 255) / 255f
        gradients[o + 4] = (bottom shr 8 and 255) / 255f
        gradients[o + 5] = (bottom and 255) / 255f
        gradients[o + 6] = y0
        gradients[o + 7] = y1
        gradientCount++
    }
}
