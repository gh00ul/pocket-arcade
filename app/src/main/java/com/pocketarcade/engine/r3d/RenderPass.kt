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

        /** The near clipping distance every pass used before it became configurable. */
        const val DEFAULT_NEAR = 8f

        /** Floats per particle vertex: position in clip space 2, quad-local coordinates 2, colour 4. */
        const val PARTICLE_STRIDE = 8
    }

    // Where on the window to draw (pixels, top-left origin) and an optional clip rectangle.
    var vx = 0; var vy = 0; var vw = 1; var vh = 1
    var clip = false
    var cx0 = 0; var cy0 = 0; var cx1 = 0; var cy1 = 0

    // Camera: eye, basis and pinhole projection in viewport pixels.
    val cam = FloatArray(16)
    /** Near clipping distance (view depth) for the GPU's depth range; games keep the default. */
    var near = DEFAULT_NEAR

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

    // Look: see the matching fields on Renderer3D.
    var bloomThreshold = Look.BLOOM_THRESHOLD
    var bloomRadius = Look.BLOOM_RADIUS
    var grade = Look.GRADE
    var sharpen = Look.SHARPEN
    var vignette = Look.VIGNETTE
    var rim = Look.RIM
    var floorGlow = 0f
    var envReflect = Look.ENV_REFLECT
    var floorReflect = 0f
    var floorReflectMatte = 0f
    var floorMirror = false

    /** UI-thread time spent recording this pass (startFrame → finishFrame), for frame stats. */
    var recordNs = 0L
    internal var statsTaken = false

    /**
     * A stand-in returned for a frame the frame-rate cap skipped (see [Renderer3D.frameCapped]):
     * empty, and dropped by [com.pocketarcade.engine.gl.Gfx.submit] so the GPU keeps showing the
     * last picture.
     */
    var skipped = false
        internal set

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

    /** Per draw-list entry: whether it holds glowing (emissive) geometry, for floor reflections. */
    var drawGlow = BooleanArray(64)
    /** How many draw-list entries glow. */
    var glowDraws = 0

    /**
     * Screen-space particle quads (see [com.pocketarcade.engine.Particles.recordGl]), drawn over
     * the 3D picture but before the bloom, so bright ones glow. Triangles, [PARTICLE_STRIDE]
     * floats a vertex; the first [particleHaloCount] vertices are soft additive glows, the rest
     * are the particles themselves.
     */
    var particleVerts = FloatArray(0)
    var particleVertCount = 0
    var particleHaloCount = 0

    // Model instances: model, which blend layer, 4×4 matrix, tint RGBA, emissive boost.
    val models = ArrayList<Model>()
    var instances = FloatArray(64 * 22)
    var instanceCount = 0

    internal fun reset() {
        skipped = false
        clip = false
        near = DEFAULT_NEAR
        clearColor = 0
        gradientCount = 0
        lightCount = 0
        fogNear = 1e8f
        fogFar = 2e8f
        fogFloor = 0f
        exposure = 1f
        bloom = 0.8f
        bloomThreshold = Look.BLOOM_THRESHOLD
        bloomRadius = Look.BLOOM_RADIUS
        grade = Look.GRADE
        sharpen = Look.SHARPEN
        vignette = Look.VIGNETTE
        rim = Look.RIM
        floorGlow = 0f
        envReflect = Look.ENV_REFLECT
        floorReflect = 0f
        floorReflectMatte = 0f
        floorMirror = false
        recordNs = 0L
        statsTaken = false
        particleVertCount = 0
        particleHaloCount = 0
        gridW = 0
        gridH = 0
        vertCount = 0
        textures.clear()
        drawCount = 0
        glowDraws = 0
        models.clear()
        instanceCount = 0
    }

    /** Hands the pass back to its recorder once the GL thread is done with it. */
    fun recycle() {
        pool.offer(this)
    }

    /**
     * Adds one particle quad (two triangles) centred on clip-space ([cx], [cy]) with half sizes
     * ([hx], [hy]); the corners carry local coordinates -1..1 for the soft glow shape.
     */
    fun addParticleQuad(cx: Float, cy: Float, hx: Float, hy: Float, r: Float, g: Float, b: Float, a: Float) {
        val need = (particleVertCount + 6) * PARTICLE_STRIDE
        if (need > particleVerts.size) particleVerts = particleVerts.copyOf(maxOf(need, particleVerts.size * 2, 64 * 6 * PARTICLE_STRIDE))
        val v = particleVerts
        var o = particleVertCount * PARTICLE_STRIDE
        // Corners in order (-1,-1) (1,-1) (1,1) (-1,1); triangles 0-1-2 and 0-2-3.
        for (k in 0 until 6) {
            val corner = when (k) {
                0 -> 0
                1 -> 1
                2 -> 2
                3 -> 0
                4 -> 2
                else -> 3
            }
            val ux = if (corner == 1 || corner == 2) 1f else -1f
            val uy = if (corner >= 2) 1f else -1f
            v[o] = cx + ux * hx; v[o + 1] = cy + uy * hy
            v[o + 2] = ux; v[o + 3] = uy
            v[o + 4] = r; v[o + 5] = g; v[o + 6] = b; v[o + 7] = a
            o += PARTICLE_STRIDE
        }
        particleVertCount += 6
    }

    internal fun ensureVerts(extra: Int) {
        val need = (vertCount + extra) * STRIDE
        if (need > verts.size) verts = verts.copyOf(maxOf(need, verts.size * 2))
    }

    internal fun addDraw(kind: Int, blend: Int, index: Int, first: Int, count: Int, glow: Boolean = false) {
        if ((drawCount + 1) * 5 > draws.size) draws = draws.copyOf(draws.size * 2)
        if (drawCount + 1 > drawGlow.size) drawGlow = drawGlow.copyOf(drawGlow.size * 2)
        val o = drawCount * 5
        draws[o] = kind; draws[o + 1] = blend; draws[o + 2] = index; draws[o + 3] = first; draws[o + 4] = count
        drawGlow[drawCount] = glow
        if (glow) glowDraws++
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
