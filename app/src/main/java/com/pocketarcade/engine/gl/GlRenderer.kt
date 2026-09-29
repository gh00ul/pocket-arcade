package com.pocketarcade.engine.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.util.Log
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.EnvMap
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.RenderPass
import com.pocketarcade.engine.r3d.Texture
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Draws [RenderPass]es with OpenGL ES 3: multisampled scene rendering at an adaptive resolution,
 * per-pixel lighting, a bloom glow and a final composite into the window. Lives on the GL thread.
 */
internal class GlRenderer {
    companion object {
        private const val TAG = "PocketArcadeGL"
        private const val FAR = 9000f
        private const val GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE
        private const val GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF
        /** Bloom octaves: 1/4, 1/8, 1/16 and 1/32 of the render size. */
        private const val BLOOM_LEVELS = 4
        /** Share of the sharp quarter-size bright pass kept under the wider octaves. */
        private const val BLOOM_CORE = 0.5f
        /** Ints per model mesh group. */
        private const val GROUP = 6
        /** How far up the screen (fraction of its height) floor streaks gather glow from. */
        private const val STREAK = 0.12f
        /** Brightness of the streaks relative to a mirrored image. */
        private const val STREAK_GAIN = 2f
        /** The render scale a screen starts at (the adaptive resolution's usual ceiling). */
        const val START_SCALE = 0.8f

        private const val GL_RGBA16F = 0x881A
        private const val GL_HALF_FLOAT = 0x140B

        /**
         * How many frames after the HDR pipeline first draws are checked for GL errors. Nothing
         * a working driver does with float targets raises one, so a single error blames the HDR
         * pipeline, which is switched off and the frame drawn again as LDR.
         */
        private const val HDR_PROBE_FRAMES = 4

        /**
         * Frames a screen's HDR buffers may sit unused before their multisampled colour and depth
         * (the biggest allocation: 8 bytes a sample in float) are given back; they are built again
         * on demand if the screen returns. Keeps three screen sizes from holding three float
         * multisampled sets at once.
         */
        private const val HDR_IDLE_FRAMES = 240L
    }

    /**
     * This context's number from [GlGeneration] (process-wide), so textures and models cached
     * across activities know to re-upload after any context is (re)created.
     */
    var generation = 0
        private set

    private var sceneProg = 0
    private var bgProg = 0
    private var brightProg = 0
    private var downProg = 0
    private var upProg = 0
    private var compProg = 0
    private var particleProg = 0

    /** Whether particles can be drawn into the picture (their shader built). */
    var particlesOk = false
        private set

    /** A linked program with its uniform locations looked up once each. */
    private class Prog(val id: Int) {
        private val locs = HashMap<String, Int>()
        fun loc(name: String): Int = locs.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
    }
    /** The scene program in use: [sceneLdr], or [sceneHdr] while an HDR pass draws (never for the mirror). */
    private var scene = Prog(0)
    private var sceneLdr = Prog(0)
    private var sceneHdr = Prog(0)
    private var bg = Prog(0)
    private var bgHdr = Prog(0)
    private var bright = Prog(0)
    private var brightHdr = Prog(0)
    private var glare = Prog(0)
    private var down = Prog(0)
    private var up = Prog(0)
    private var comp = Prog(0)
    private var compHdr = Prog(0)
    private var particle = Prog(0)
    private var particleHdr = Prog(0)

    /** What the driver offers, read at [init]; null before. */
    private var caps: GlCaps? = null

    /** Whether every HDR program compiled and linked. If not the picture is LDR, silently. */
    private var hdrProgramsOk = false
    private var streamVbo = 0
    private var streamVao = 0
    private var bgVbo = 0
    private var bgVao = 0
    private var partVbo = 0
    private var partVao = 0
    private var quadVbo = 0
    private var quadVao = 0
    private var gridTex = 0
    private var envTex = 0
    private var gridTexW = 0
    private var gridTexH = 0
    private var whiteTex = 0
    /** The most samples the GPU offers (capped at 4); whether a multisampled target failed to build. */
    private var maxSamples = 0
    private var msaaBroken = false
    private var anisotropy = 1f

    /** What the driver calls itself (`GL_RENDERER`), for the first guess at a quality rung. */
    var glRendererName = ""
        private set

    // Quality levers (see GfxQuality), set by the GL thread from the current rung. The defaults
    // are rung 0: how the renderer looked before there were tiers.
    /** Multisampling wanted (4, 2 or 0); the GPU's own limit still applies. */
    var msaa = 4
    /** Bloom octaves wanted, 2..[BLOOM_LEVELS]. */
    var bloomOctaves = BLOOM_LEVELS
    var reflections = GfxQuality.Reflections.MIRROR
    /** HDR wanted by the rung (the plan still needs the device's capabilities and [HdrGuard]'s leave). */
    var hdrWanted = true
    /** The rung's cinematic finish: the anamorphic glare, and film grain with chromatic aberration. */
    var glareOn = true
    var filmOn = true
    /** Whether the pass being drawn renders into a multisampled target. */
    private var msActive = false
    /** Whether the pass being drawn is the HDR picture (its scene target is RGBA16F). */
    private var hdrPass = false

    /** The pipeline the last pass was drawn with, for the frame log. */
    var pipeline = Pipeline.LDR
        private set
    private var loggedPipeline: Pipeline? = null
    /** Frames drawn with the HDR pipeline that are still being checked for GL errors. */
    private var hdrProbeLeft = HDR_PROBE_FRAMES

    /** Draw calls and vertices issued by the last [drawFrame], for [FrameStats] (to judge instancing). */
    var drawCalls = 0
        private set
    var vertsDrawn = 0L
        private set

    private fun drawArrays(mode: Int, first: Int, count: Int) {
        drawCalls++
        vertsDrawn += count
        GLES30.glDrawArrays(mode, first, count)
    }

    /** Skips floor reflections whatever the passes ask (for A/B timing from [FrameStats]). */
    var floorReflectOff = false

    /** Fraction of full resolution the scene renders at; eased down if frames run slow. */
    var renderScale = START_SCALE

    /** Sets every quality lever from [r]. */
    fun applyRung(r: GfxQuality.Rung) {
        msaa = r.msaa
        bloomOctaves = r.bloomOctaves
        reflections = r.reflections
        hdrWanted = r.hdr
        glareOn = r.glare
        filmOn = r.film
    }

    /** The multisampling the current levers and the GPU allow: 0 (off) or 2..4. */
    private fun wantSamples(): Int {
        val n = if (msaaBroken) 0 else minOf(msaa, maxSamples)
        return if (n > 1) n else 0
    }

    /** The picture the levers, the device and [HdrGuard] allow right now. */
    private fun plan(): Pipeline = HdrPlan.choose(
        caps, GfxQuality.tier, hdrWanted && hdrProgramsOk, wantSamples(), HdrGuard.blocked, HdrGuard.msaaBlocked,
    )

    private class Targets(val w: Int, val h: Int) {
        var msFbo = 0
        var msColor = 0
        var msDepth = 0
        /** Samples the multisampled buffers were built with (0 = none), or -1 before they exist. */
        var msSamples = -1
        var sceneFbo = 0
        var sceneTex = 0
        var sceneDepth = 0
        /** Whether the scene and bloom textures are RGBA16F (the HDR picture); false = RGBA8. */
        var hdr = false
        /** Whether the scene and bloom textures exist yet (they are built by [setColorFormat]). */
        var colorBuilt = false
        val bloomW = IntArray(BLOOM_LEVELS)
        val bloomH = IntArray(BLOOM_LEVELS)
        val bloomFbo = IntArray(BLOOM_LEVELS)
        val bloomTex = IntArray(BLOOM_LEVELS)
        var bloomLevels = 0
        // The anamorphic glare: the brightest of the bloom's second octave, blurred sideways.
        var glareFbo = 0
        var glareTex = 0
        // The camera of the previous frame and the smoothed 0..1 amount of motion between frames.
        val prevCam = FloatArray(12)
        var hasPrevCam = false
        var motion = 0f
        /** Whether the bloom chain holds a finished frame (read back as floor streaks). */
        var bloomDone = false
        var lastUsed = 0L
        // Floor reflections (made on first use): the mirrored glow at 1/4 size with depth, and
        // a 1/8-size step for blurring it.
        var reflW = 0
        var reflH = 0
        var reflFbo = 0
        var reflTex = 0
        var reflDepth = 0
        var refl2Fbo = 0
        var refl2Tex = 0
    }
    private val targets = ArrayList<Targets>()
    private var frameNo = 0L

    /** Per group ([GROUP] ints): texture index, blend, cull, first vertex, count, glows. */
    private class Mesh(val vbo: Int, val vao: Int, val groups: IntArray, val textures: Array<Texture>)
    private val meshRefs = ArrayList<Pair<WeakReference<Model>, Mesh>>()
    private val texRefs = ArrayList<Pair<WeakReference<Texture>, Int>>()

    private var upload: ByteBuffer = ByteBuffer.allocateDirect(4 * 1024 * 1024).order(ByteOrder.nativeOrder())
    private var uploadInts: IntBuffer = upload.asIntBuffer()
    private var vertBuf: FloatBuffer = ByteBuffer.allocateDirect(4 * 256 * 1024).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val tmp = IntArray(4)
    private var swizzle = IntArray(0)
    private val bgData = FloatArray(4 * 5 * 8)
    private val lightPos = FloatArray(RenderPass.MAX_LIGHTS * 4)
    private val lightCol = FloatArray(RenderPass.MAX_LIGHTS * 4)
    private val ident = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }
    private val discardColorDepth = intArrayOf(GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_DEPTH_ATTACHMENT)
    private val discardColor = intArrayOf(GLES30.GL_COLOR_ATTACHMENT0)
    private val discardDepth = intArrayOf(GLES30.GL_DEPTH_ATTACHMENT)

    // GL state tracked while drawing the scene, to skip redundant calls.
    private var boundTex = -1
    private var boundBlend = -1
    private var cullOn = false
    private var uCut = -1
    private var uGlass = -1
    /** Drawing the mirrored floor-reflection pass (no multisampling, glowing things only). */
    private var mirror = false

    // ------------------------------------------------------------------ setup

    /** Creates programs and buffers for a fresh context. */
    fun init() {
        generation = GlGeneration.next()
        targets.clear()
        snapFbo = 0
        snapTex = 0
        gridTexW = 0
        gridTexH = 0
        meshRefs.clear()
        texRefs.clear()
        sceneProg = program(GlShaders.SCENE_VS, GlShaders.SCENE_FS)
        bgProg = program(GlShaders.BG_VS, GlShaders.BG_FS)
        brightProg = program(GlShaders.POST_VS, GlShaders.BRIGHT_FS)
        downProg = program(GlShaders.POST_VS, GlShaders.DOWN_FS)
        upProg = program(GlShaders.POST_VS, GlShaders.UP_FS)
        compProg = program(GlShaders.POST_VS, GlShaders.COMPOSITE_FS)
        // Particles are a nicety: a driver that rejects their shader must not take the scene down
        // with it, so they fall back to being painted in 2D.
        particleProg = try {
            program(GlShaders.PARTICLE_VS, GlShaders.PARTICLE_FS)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Particle shader unavailable; particles stay 2D", e)
            0
        }
        particlesOk = particleProg != 0
        sceneLdr = Prog(sceneProg)
        scene = sceneLdr
        bg = Prog(bgProg)
        bright = Prog(brightProg)
        down = Prog(downProg)
        up = Prog(upProg)
        comp = Prog(compProg)
        particle = Prog(particleProg)

        GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, tmp, 0)
        maxSamples = minOf(4, tmp[0])
        msaaBroken = false
        glRendererName = GLES30.glGetString(GLES30.GL_RENDERER) ?: ""
        val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        if (ext.contains("GL_EXT_texture_filter_anisotropic")) {
            val f = FloatArray(1)
            GLES30.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, f, 0)
            anisotropy = minOf(8f, f[0])
        }
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        val version = GLES30.glGetString(GLES30.GL_VERSION) ?: ""
        Log.i(TAG, "GL $version / $glRendererName, MSAA $maxSamples, aniso $anisotropy")
        val c = GlCaps(version, ext, maxSamples)
        caps = c
        hdrProgramsOk = false
        if (!HdrGuard.blocked && c.floatTargets) buildHdrPrograms()
        Log.i(
            TAG,
            "HDR: float targets ${c.floatTargets} (color_buffer_float ${c.colorBufferFloat}, half_float ${c.colorBufferHalfFloat}), " +
                "float MSAA ${c.floatMsaa}, programs $hdrProgramsOk" + if (HdrGuard.blocked) ", blocked: ${HdrGuard.reason}" else "",
        )

        // Streaming buffer for immediate geometry.
        GLES30.glGenBuffers(1, tmp, 0); streamVbo = tmp[0]
        GLES30.glGenVertexArrays(1, tmp, 0); streamVao = tmp[0]
        GLES30.glBindVertexArray(streamVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, streamVbo)
        sceneAttribs()

        GLES30.glGenBuffers(1, tmp, 0); bgVbo = tmp[0]
        GLES30.glGenVertexArrays(1, tmp, 0); bgVao = tmp[0]
        GLES30.glBindVertexArray(bgVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bgVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 20, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 20, 8)

        GLES30.glGenBuffers(1, tmp, 0); partVbo = tmp[0]
        GLES30.glGenVertexArrays(1, tmp, 0); partVao = tmp[0]
        GLES30.glBindVertexArray(partVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, partVbo)
        val partStride = RenderPass.PARTICLE_STRIDE * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, partStride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, partStride, 8)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, partStride, 16)

        GLES30.glGenBuffers(1, tmp, 0); quadVbo = tmp[0]
        GLES30.glGenVertexArrays(1, tmp, 0); quadVao = tmp[0]
        GLES30.glBindVertexArray(quadVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        val qb = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(quad)
        qb.position(0)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, qb, GLES30.GL_STATIC_DRAW)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glBindVertexArray(0)

        GLES30.glGenTextures(1, tmp, 0); gridTex = tmp[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, gridTex)
        texParams(GLES30.GL_NEAREST, GLES30.GL_NEAREST, false)
        envTex = envCubemap()
        GLES30.glGenTextures(1, tmp, 0); whiteTex = tmp[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, whiteTex)
        upload.clear()
        upload.put(byteArrayOf(-1, -1, -1, -1)).position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, upload)
        texParams(GLES30.GL_NEAREST, GLES30.GL_NEAREST, false)
    }

    /**
     * Compiles the HDR picture's programs. Any failure leaves [hdrProgramsOk] false, and with it
     * the LDR picture, which has none of these programs to lose; nothing here can take the scene
     * down. The half-built ones are deleted so a failed attempt leaks nothing.
     */
    private fun buildHdrPrograms() {
        val built = ArrayList<Int>()
        fun make(vs: String, fs: String): Int = program(vs, fs).also { built += it }
        try {
            sceneHdr = Prog(make(GlShaders.SCENE_VS, GlShaders.SCENE_FS_HDR))
            bgHdr = Prog(make(GlShaders.BG_VS, GlShaders.BG_HDR_FS))
            brightHdr = Prog(make(GlShaders.POST_VS, GlShaders.BRIGHT_HDR_FS))
            glare = Prog(make(GlShaders.POST_VS, GlShaders.GLARE_FS))
            compHdr = Prog(make(GlShaders.POST_VS, GlShaders.COMPOSITE_HDR_FS))
            particleHdr = if (particlesOk) Prog(make(GlShaders.PARTICLE_VS, GlShaders.PARTICLE_HDR_FS)) else Prog(0)
            hdrProgramsOk = true
        } catch (e: IllegalStateException) {
            Log.w(TAG, "HDR shaders unavailable; drawing the LDR picture", e)
            HdrGuard.block("HDR shader failed: ${e.message?.take(120)}")
            for (id in built) GLES30.glDeleteProgram(id)
            hdrProgramsOk = false
        }
    }

    /** Uploads the reflected room ([EnvMap]) as a mipmapped cube map. */
    private fun envCubemap(): Int {
        GLES30.glGenTextures(1, tmp, 0)
        val id = tmp[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, id)
        val n = EnvMap.SIZE
        ensureUpload(n * n * 4)
        for (f in 0 until 6) {
            uploadInts.clear()
            uploadInts.put(EnvMap.face(f), 0, n * n)
            upload.position(0)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + f, 0, GLES30.GL_RGBA, n, n, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, upload)
        }
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_CUBE_MAP)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, 0)
        return id
    }

    private fun sceneAttribs() {
        val stride = RenderPass.STRIDE * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 12)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, stride, 24)
        GLES30.glEnableVertexAttribArray(3)
        GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, stride, 32)
        GLES30.glEnableVertexAttribArray(4)
        GLES30.glVertexAttribPointer(4, 4, GLES30.GL_FLOAT, false, stride, 48)
    }

    private fun program(vs: String, fs: String): Int {
        fun shader(type: Int, src: String): Int {
            val s = GLES30.glCreateShader(type)
            GLES30.glShaderSource(s, src)
            GLES30.glCompileShader(s)
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, tmp, 0)
            if (tmp[0] == 0) {
                val log = GLES30.glGetShaderInfoLog(s)
                throw IllegalStateException("Shader compile failed: $log")
            }
            return s
        }
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, shader(GLES30.GL_VERTEX_SHADER, vs))
        GLES30.glAttachShader(p, shader(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, tmp, 0)
        if (tmp[0] == 0) throw IllegalStateException("Program link failed: ${GLES30.glGetProgramInfoLog(p)}")
        return p
    }

    private fun texParams(minF: Int, magF: Int, repeat: Boolean) {
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, minF)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, magF)
        val wrap = if (repeat) GLES30.GL_REPEAT else GLES30.GL_CLAMP_TO_EDGE
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
    }

    // ------------------------------------------------------------------ resources

    private fun ensureUpload(bytes: Int) {
        if (upload.capacity() < bytes) {
            upload = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
            uploadInts = upload.asIntBuffer()
        }
        upload.clear()
    }

    /** Binds [t] to texture unit 0, uploading it first if it is new or has changed. */
    private fun bindTexture(t: Texture) {
        if (!GlGeneration.isCurrent(t.glGen, generation) || t.glId == 0) {
            GLES30.glGenTextures(1, tmp, 0)
            t.glId = tmp[0]
            t.glGen = generation
            t.glVersion = -1
            texRefs += WeakReference(t) to t.glId
        }
        // A changing texture's newest frozen copy; the one it replaces goes back for reuse.
        val fresh = t.takeSnapshot()
        if (fresh != null) {
            t.glSnap?.let { t.recycle(it) }
            t.glSnap = fresh
        }
        val snap = t.glSnap
        val wantVersion = snap?.version ?: 0
        if (boundTex != t.glId) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.glId)
            boundTex = t.glId
        }
        if (t.glVersion != wantVersion) {
            val first = t.glVersion == -1
            val src = snap?.pixels ?: t.pixels
            val count = minOf(t.pixelWidth * t.pixelHeight, src.size)
            ensureUpload(t.pixelWidth * t.pixelHeight * 4)
            // ARGB ints to premultiplied RGBA bytes (stored little-endian).
            if (swizzle.size < count) swizzle = IntArray(count)
            Texture.premultiplyToRgba(src, swizzle, count)
            uploadInts.clear()
            uploadInts.put(swizzle, 0, count)
            upload.position(0)
            if (first) {
                GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, t.pixelWidth, t.pixelHeight, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, upload)
                if (!t.smooth) {
                    texParams(GLES30.GL_NEAREST, GLES30.GL_NEAREST, t.repeat)
                } else {
                    // Changing textures (live cabinet screens) get mipmaps too: they are small,
                    // and seen from across the hall they would shimmer without them.
                    texParams(GLES30.GL_LINEAR_MIPMAP_LINEAR, GLES30.GL_LINEAR, t.repeat)
                    if (anisotropy > 1f) GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GL_TEXTURE_MAX_ANISOTROPY_EXT, anisotropy)
                    GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
                }
            } else {
                GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, t.pixelWidth, t.pixelHeight, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, upload)
                if (t.smooth) GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            }
            t.glVersion = wantVersion
        }
    }

    /** Builds (once) the GPU copy of a model: triangles grouped by texture, blend and culling. */
    private fun meshFor(m: Model): Mesh {
        (m.glMesh as? Mesh)?.let { if (GlGeneration.isCurrent(m.glGen, generation)) return it }
        val S = RenderPass.STRIDE
        data class Key(val tex: Texture, val blend: Int, val cull: Boolean)
        val groups = LinkedHashMap<Key, ArrayList<FloatArray>>()
        val glowing = HashSet<Key>()
        for (p in m.polys) {
            val key = Key(p.region.tex, p.blend.ordinal, p.cull)
            val list = groups.getOrPut(key) { ArrayList() }
            if (p.emissive > 0f) glowing += key
            val tris = p.n - 2
            // Wind culled polygons counter-clockwise as seen from the side their normal faces.
            var flip = false
            if (p.cull) {
                // Newell's normal over every edge, so polygons with repeated corners (the
                // pole of a sphere or lathe) still get the right winding.
                var gx = 0f
                var gy = 0f
                var gz = 0f
                for (a in 0 until p.n) {
                    val b = (a + 1) % p.n
                    gx += (p.ys[a] - p.ys[b]) * (p.zs[a] + p.zs[b])
                    gy += (p.zs[a] - p.zs[b]) * (p.xs[a] + p.xs[b])
                    gz += (p.xs[a] - p.xs[b]) * (p.ys[a] + p.ys[b])
                }
                flip = gx * p.nx + gy * p.ny + gz * p.nz < 0f
            }
            val iw = 1f / p.region.tex.width
            val ih = 1f / p.region.tex.height
            val out = FloatArray(tris * 3 * S)
            var o = 0
            val tr = if (p.tint == -1) 1f else (p.tint shr 16 and 255) / 255f
            val tg = if (p.tint == -1) 1f else (p.tint shr 8 and 255) / 255f
            val tb = if (p.tint == -1) 1f else (p.tint and 255) / 255f
            val shade = p.shade
            for (t in 1..tris) {
                for (k in 0 until 3) {
                    val kk = if (flip) 2 - k else k
                    val i = when (kk) {
                        0 -> 0
                        1 -> t
                        else -> t + 1
                    }
                    out[o] = p.xs[i]; out[o + 1] = p.ys[i]; out[o + 2] = p.zs[i]
                    if (p.vnx != null && p.vny != null && p.vnz != null) {
                        out[o + 3] = p.vnx[i]; out[o + 4] = p.vny[i]; out[o + 5] = p.vnz[i]
                    } else {
                        out[o + 3] = p.nx; out[o + 4] = p.ny; out[o + 5] = p.nz
                    }
                    out[o + 6] = (p.region.x + p.us[i]) * iw
                    out[o + 7] = (p.region.y + p.vs[i]) * ih
                    // Baked occlusion darkens the paint per vertex (the vertex colour multiplies the texture).
                    val sh = if (shade != null) shade[i] else 1f
                    out[o + 8] = tr * sh; out[o + 9] = tg * sh; out[o + 10] = tb * sh; out[o + 11] = 1f
                    out[o + 12] = p.emissive; out[o + 13] = 1f; out[o + 14] = p.gloss; out[o + 15] = 1f
                    o += S
                }
            }
            list += out
        }
        var total = 0
        for (l in groups.values) for (a in l) total += a.size
        val fb = ByteBuffer.allocateDirect(total * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val info = IntArray(groups.size * GROUP)
        val textures = ArrayList<Texture>()
        var gi = 0
        var first = 0
        for ((key, list) in groups) {
            var count = 0
            for (a in list) {
                fb.put(a); count += a.size / S
            }
            val o = gi * GROUP
            info[o] = textures.size
            info[o + 1] = key.blend
            info[o + 2] = if (key.cull) 1 else 0
            info[o + 3] = first
            info[o + 4] = count
            info[o + 5] = if (key in glowing) 1 else 0
            textures += key.tex
            first += count
            gi++
        }
        fb.position(0)
        GLES30.glGenBuffers(1, tmp, 0)
        val vbo = tmp[0]
        GLES30.glGenVertexArrays(1, tmp, 0)
        val vao = tmp[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, total * 4, fb, GLES30.GL_STATIC_DRAW)
        sceneAttribs()
        GLES30.glBindVertexArray(0)
        val mesh = Mesh(vbo, vao, info, textures.toTypedArray())
        m.glMesh = mesh
        m.glGen = generation
        meshRefs += WeakReference(m) to mesh
        return mesh
    }

    /** Frees GPU copies of models and textures the app no longer holds, and idle HDR sets' multisampled buffers. */
    private fun collectGarbage() {
        for (i in 0 until targets.size) {
            val t = targets[i]
            if (t.hdr && t.msFbo != 0 && frameNo - t.lastUsed > HDR_IDLE_FRAMES) releaseMultisample(t)
        }
        val mi = meshRefs.iterator()
        while (mi.hasNext()) {
            val (ref, mesh) = mi.next()
            if (ref.get() == null) {
                tmp[0] = mesh.vbo
                GLES30.glDeleteBuffers(1, tmp, 0)
                tmp[0] = mesh.vao
                GLES30.glDeleteVertexArrays(1, tmp, 0)
                mi.remove()
            }
        }
        val ti = texRefs.iterator()
        while (ti.hasNext()) {
            val (ref, id) = ti.next()
            val t = ref.get()
            if (t == null || t.glId != id) {
                if (t == null) {
                    tmp[0] = id
                    GLES30.glDeleteTextures(1, tmp, 0)
                }
                ti.remove()
            }
        }
    }

    private fun targetsFor(w: Int, h: Int): Targets {
        var t = targets.firstOrNull { it.w == w && it.h == h }
        if (t == null) {
            // Drop the least recently used set when too many sizes pile up.
            if (targets.size >= 3) {
                val old = targets.minByOrNull { it.lastUsed }!!
                freeTargets(old)
                targets.remove(old)
            }
            t = Targets(w, h)
            GLES30.glGenFramebuffers(1, tmp, 0); t.sceneFbo = tmp[0]
            targets += t
        }
        t.lastUsed = frameNo
        // Both the picture (HDR or LDR) and the multisampling may have moved since this set was
        // built. A build that fails marks [HdrGuard], so the plan is asked again; each step can
        // only end at LDR, which cannot fail that way, so a few rounds always settle it.
        for (attempt in 0 until 3) {
            val hdr = plan().isHdr
            if (hdr) hdrInFlight = true
            if (!t.colorBuilt || t.hdr != hdr) setColorFormat(t, hdr)
            val samples = wantSamples()
            if (t.msSamples != samples) applyMultisample(t, samples)
            if (plan().isHdr == t.hdr) break
        }
        return t
    }

    /**
     * (Re)builds [t]'s scene and bloom textures and their framebuffers, RGBA16F for the HDR
     * picture or RGBA8 for the LDR one. An HDR build is verified with `glCheckFramebufferStatus`;
     * any incomplete target blocks HDR for the run and the set is rebuilt as LDR.
     */
    private fun setColorFormat(t: Targets, hdr: Boolean) {
        freeColor(t)
        t.hdr = hdr
        t.colorBuilt = true
        // The multisampled colour buffer must match the texture it resolves into.
        t.msSamples = -1
        var ok = true
        t.sceneTex = colorTexture(t.w, t.h, hdr)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.sceneFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, t.sceneTex, 0)
        if (hdr && !framebufferComplete()) ok = false
        // The bloom chain: each octave half the size of the one before, down to about 1/32.
        var bw = t.w / 4
        var bh = t.h / 4
        for (i in 0 until BLOOM_LEVELS) {
            if (i > 0 && (bw < 2 || bh < 2)) break
            t.bloomW[i] = max(1, bw)
            t.bloomH[i] = max(1, bh)
            GLES30.glGenFramebuffers(1, tmp, 0); t.bloomFbo[i] = tmp[0]
            t.bloomTex[i] = colorTexture(t.bloomW[i], t.bloomH[i], hdr)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.bloomFbo[i])
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, t.bloomTex[i], 0)
            if (hdr && !framebufferComplete()) ok = false
            t.bloomLevels = i + 1
            bw /= 2
            bh /= 2
        }
        if (hdr && t.bloomLevels > 1) {
            // The glare lives at the second octave's size, beside the chain.
            GLES30.glGenFramebuffers(1, tmp, 0); t.glareFbo = tmp[0]
            t.glareTex = colorTexture(t.bloomW[1], t.bloomH[1], true)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.glareFbo)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, t.glareTex, 0)
            if (!framebufferComplete()) ok = false
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (hdr && !ok) {
            Log.w(TAG, "Float framebuffer incomplete at ${t.w}x${t.h}; drawing the LDR picture")
            HdrGuard.block("float framebuffer incomplete")
            drainErrors()
            setColorFormat(t, false)
        }
    }

    private fun framebufferComplete(): Boolean =
        GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE

    /** Forgets GL errors already raised, so a later check blames only what it should. */
    private fun drainErrors() {
        var guard = 0
        while (GLES30.glGetError() != GLES30.GL_NO_ERROR && guard++ < 16) { /* drained */ }
    }

    /** Gives back [t]'s multisampled colour and depth; the next use of the set rebuilds them ([Targets.msSamples] = -1). */
    private fun releaseMultisample(t: Targets) {
        if (t.msFbo != 0) { tmp[0] = t.msFbo; GLES30.glDeleteFramebuffers(1, tmp, 0); t.msFbo = 0 }
        if (t.msColor != 0) { tmp[0] = t.msColor; GLES30.glDeleteRenderbuffers(1, tmp, 0); t.msColor = 0 }
        if (t.msDepth != 0) { tmp[0] = t.msDepth; GLES30.glDeleteRenderbuffers(1, tmp, 0); t.msDepth = 0 }
        t.msSamples = -1
    }

    /**
     * Gives [t] multisampled buffers of [want] samples, or none (0): the scene is then drawn
     * straight into the scene target with a depth buffer of its own. Replaces whatever [t] had,
     * so the multisampling lever can move while the app runs. Buffers follow the set's picture:
     * multisampled RGBA16F for HDR, whose failure only rules out HDR with multisampling.
     */
    private fun applyMultisample(t: Targets, want: Int) {
        if (t.msFbo != 0) { tmp[0] = t.msFbo; GLES30.glDeleteFramebuffers(1, tmp, 0); t.msFbo = 0 }
        if (t.msColor != 0) { tmp[0] = t.msColor; GLES30.glDeleteRenderbuffers(1, tmp, 0); t.msColor = 0 }
        if (t.msDepth != 0) { tmp[0] = t.msDepth; GLES30.glDeleteRenderbuffers(1, tmp, 0); t.msDepth = 0 }
        if (t.sceneDepth != 0) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.sceneFbo)
            GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, 0)
            tmp[0] = t.sceneDepth; GLES30.glDeleteRenderbuffers(1, tmp, 0); t.sceneDepth = 0
        }
        var n = want
        if (n > 1) {
            GLES30.glGenFramebuffers(1, tmp, 0); t.msFbo = tmp[0]
            GLES30.glGenRenderbuffers(1, tmp, 0); t.msColor = tmp[0]
            GLES30.glGenRenderbuffers(1, tmp, 0); t.msDepth = tmp[0]
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, t.msColor)
            GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, n, if (t.hdr) GL_RGBA16F else GLES30.GL_RGBA8, t.w, t.h)
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, t.msDepth)
            GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, n, GLES30.GL_DEPTH_COMPONENT24, t.w, t.h)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.msFbo)
            GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, t.msColor)
            GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, t.msDepth)
            if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                if (t.hdr) {
                    Log.w(TAG, "Multisampled float framebuffer incomplete; the HDR picture needs it, so drawing LDR")
                    HdrGuard.blockMsaa("multisampled float framebuffer incomplete")
                } else {
                    Log.w(TAG, "MSAA framebuffer incomplete; rendering without multisampling")
                    msaaBroken = true
                }
                drainErrors()
                tmp[0] = t.msFbo; GLES30.glDeleteFramebuffers(1, tmp, 0)
                tmp[0] = t.msColor; GLES30.glDeleteRenderbuffers(1, tmp, 0)
                tmp[0] = t.msDepth; GLES30.glDeleteRenderbuffers(1, tmp, 0)
                t.msFbo = 0
                t.msColor = 0
                t.msDepth = 0
                n = 0
            }
        }
        if (t.msFbo == 0) {
            GLES30.glGenRenderbuffers(1, tmp, 0); t.sceneDepth = tmp[0]
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, t.sceneDepth)
            GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, t.w, t.h)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.sceneFbo)
            GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, t.sceneDepth)
            if (t.hdr && !framebufferComplete()) {
                Log.w(TAG, "Float scene framebuffer incomplete with depth; drawing the LDR picture")
                HdrGuard.block("float scene framebuffer incomplete")
                drainErrors()
            }
        }
        t.msSamples = n
    }

    /** An RGBA8 texture, or an RGBA16F one for the HDR picture, of [w] × [h], filtered linearly. */
    private fun colorTexture(w: Int, h: Int, hdr: Boolean = false): Int {
        GLES30.glGenTextures(1, tmp, 0)
        val id = tmp[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        if (hdr) {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GLES30.GL_RGBA, GL_HALF_FLOAT, null)
        } else {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        }
        texParams(GLES30.GL_LINEAR, GLES30.GL_LINEAR, false)
        return id
    }

    /** Frees [t]'s scene and bloom textures (and the glare) but keeps its framebuffer object and depth. */
    private fun freeColor(t: Targets) {
        fun fbo(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteFramebuffers(1, tmp, 0) } }
        fun tex(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteTextures(1, tmp, 0) } }
        tex(t.sceneTex)
        t.sceneTex = 0
        for (i in 0 until t.bloomLevels) {
            fbo(t.bloomFbo[i]); tex(t.bloomTex[i])
            t.bloomFbo[i] = 0
            t.bloomTex[i] = 0
        }
        t.bloomLevels = 0
        t.bloomDone = false
        fbo(t.glareFbo); tex(t.glareTex)
        t.glareFbo = 0
        t.glareTex = 0
    }

    private fun freeTargets(t: Targets) {
        fun fbo(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteFramebuffers(1, tmp, 0) } }
        fun rb(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteRenderbuffers(1, tmp, 0) } }
        fun tex(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteTextures(1, tmp, 0) } }
        fbo(t.msFbo); rb(t.msColor); rb(t.msDepth)
        fbo(t.sceneFbo); rb(t.sceneDepth)
        freeColor(t)
        fbo(t.reflFbo); tex(t.reflTex); rb(t.reflDepth)
        fbo(t.refl2Fbo); tex(t.refl2Tex)
    }

    /** Creates the floor-reflection targets for [t] the first time a pass wants them. */
    private fun ensureReflTargets(t: Targets) {
        if (t.reflFbo != 0) return
        t.reflW = max(8, t.w / 4)
        t.reflH = max(8, t.h / 4)
        GLES30.glGenFramebuffers(1, tmp, 0); t.reflFbo = tmp[0]
        t.reflTex = colorTexture(t.reflW, t.reflH)
        GLES30.glGenRenderbuffers(1, tmp, 0); t.reflDepth = tmp[0]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, t.reflDepth)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, t.reflW, t.reflH)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.reflFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, t.reflTex, 0)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, t.reflDepth)
        GLES30.glGenFramebuffers(1, tmp, 0); t.refl2Fbo = tmp[0]
        t.refl2Tex = colorTexture(max(4, t.reflW / 2), max(4, t.reflH / 2))
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.refl2Fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, t.refl2Tex, 0)
    }

    /**
     * The floor mirror: glowing geometry drawn upside down about y = 0 into a quarter-size
     * target, then softened (down to 1/8 and back up with tent filters). Leaves the result in
     * [Targets.reflTex].
     */
    private fun drawMirror(p: RenderPass, t: Targets) {
        ensureReflTargets(t)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.reflFbo)
        GLES30.glViewport(0, 0, t.reflW, t.reflH)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glDepthMask(true)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        mirror = true
        drawScene(p)
        mirror = false
        GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, discardDepth, 0)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE)
        GLES30.glBindVertexArray(quadVao)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        val w2 = max(4, t.reflW / 2)
        val h2 = max(4, t.reflH / 2)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.refl2Fbo)
        GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, discardColor, 0)
        GLES30.glViewport(0, 0, w2, h2)
        GLES30.glUseProgram(downProg)
        GLES30.glUniform1i(down.loc("uTex"), 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.reflTex)
        // Streaked a little more up and down the screen, like light on a polished floor.
        GLES30.glUniform2f(down.loc("uTexel"), 1f / t.reflW, 2f / t.reflH)
        drawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.reflFbo)
        GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, discardColor, 0)
        GLES30.glViewport(0, 0, t.reflW, t.reflH)
        GLES30.glUseProgram(upProg)
        GLES30.glUniform1i(up.loc("uTex"), 0)
        GLES30.glUniform1f(up.loc("uWeight"), 1f)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.refl2Tex)
        GLES30.glUniform2f(up.loc("uTexel"), 1f / w2, 1.5f / h2)
        drawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    // ------------------------------------------------------------------ frame

    /**
     * Draws every pass into the window surface ([surfaceW] × [surfaceH]). If the HDR pipeline
     * throws, or raises a GL error in its first few frames, it is blocked for the rest of the
     * run ([HdrGuard]) and the frame is drawn again with the LDR picture, which is unchanged
     * and cannot fail that way: the player sees at worst one repeated frame, never a black screen.
     */
    fun drawFrame(passes: Collection<RenderPass>, surfaceW: Int, surfaceH: Int) {
        val probing = hdrProbeLeft > 0 && hdrProgramsOk && !HdrGuard.blocked
        if (probing) drainErrors()
        var failure: Throwable? = null
        try {
            drawFrameOnce(passes, surfaceW, surfaceH)
        } catch (e: RuntimeException) {
            // Only the new pipeline's failures are ours to absorb; anything else is the GL
            // thread's restart policy's business.
            if (!hdrInFlight) throw e
            failure = e
        }
        if (failure == null && probing && pipeline.isHdr) {
            hdrProbeLeft--
            val err = GLES30.glGetError()
            if (err != GLES30.GL_NO_ERROR) failure = IllegalStateException("GL error 0x" + Integer.toHexString(err))
        }
        if (failure != null) {
            Log.w(TAG, "HDR pipeline failed; drawing this frame and the rest as LDR", failure)
            HdrGuard.block("HDR frame failed: ${failure.javaClass.simpleName} ${failure.message?.take(80)}")
            drainErrors()
            drawFrameOnce(passes, surfaceW, surfaceH)
        }
    }

    /** Whether the frame being (or last) drawn used or started building the HDR pipeline; see [drawFrame]. */
    var hdrInFlight = false
        private set

    private fun drawFrameOnce(passes: Collection<RenderPass>, surfaceW: Int, surfaceH: Int) {
        hdrInFlight = false
        frameNo++
        drawCalls = 0
        vertsDrawn = 0L
        if (frameNo % 120 == 0L) collectGarbage()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, surfaceW, surfaceH)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        for (p in passes) drawPass(p, 0, surfaceH, renderScale)
    }

    private var snapFbo = 0
    private var snapTex = 0
    private var snapW = 0
    private var snapH = 0

    /** Renders [p] into an off-screen image at full resolution and reads it back. */
    fun snapshot(p: RenderPass): Bitmap {
        val w = p.vw.coerceAtLeast(1)
        val h = p.vh.coerceAtLeast(1)
        if (snapFbo == 0 || snapW != w || snapH != h) {
            if (snapFbo != 0) {
                tmp[0] = snapFbo; GLES30.glDeleteFramebuffers(1, tmp, 0)
                tmp[0] = snapTex; GLES30.glDeleteTextures(1, tmp, 0)
            }
            GLES30.glGenFramebuffers(1, tmp, 0); snapFbo = tmp[0]
            snapTex = colorTexture(w, h)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, snapFbo)
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, snapTex, 0)
            snapW = w
            snapH = h
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, snapFbo)
        GLES30.glViewport(0, 0, w, h)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        // A thumbnail is a still: no film grain or aberration, which are for the moving picture.
        snapshotting = true
        try {
            drawPass(p, snapFbo, h, 1f)
        } finally {
            snapshotting = false
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, snapFbo)
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        val raw = IntArray(w * h)
        buf.asIntBuffer().get(raw)
        // RGBA bytes read as little-endian ints are ABGR; flip rows too (GL is bottom-up).
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val src = (h - 1 - y) * w
            val dst = y * w
            for (x in 0 until w) {
                val c = raw[src + x]
                out[dst + x] = (c and 0xFF00FF00.toInt()) or ((c and 0xFF) shl 16) or ((c shr 16) and 0xFF)
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Draws [p] and composites it into framebuffer [outFbo] ([outH] tall), rendering at [scale]. */
    private fun drawPass(p: RenderPass, outFbo: Int, surfaceH: Int, scale: Float) {
        val rw = (p.vw * scale).roundToInt().coerceAtLeast(16)
        val rh = (p.vh * scale).roundToInt().coerceAtLeast(16)
        val t = targetsFor(rw, rh)
        msActive = t.msFbo != 0
        val hdr = t.hdr
        hdrPass = hdr
        pipeline = if (!hdr) Pipeline.LDR else if (t.msFbo != 0) Pipeline.HDR_MSAA else Pipeline.HDR
        if (pipeline != loggedPipeline) {
            loggedPipeline = pipeline
            Log.i(TAG, "Picture: ${pipeline.label}, MSAA ${t.msSamples}, bloom octaves ${t.bloomLevels}" + if (HdrGuard.blocked) " (HDR blocked: ${HdrGuard.reason})" else "")
        }
        // The finish (grain, aberration, glare) belongs to the live picture, not to thumbnails.
        val finish = hdr && !snapshotting
        val reduce = GfxQuality.motionReduced()
        val motion = if (finish && filmOn && !reduce) cameraMotion(p, t) else 0f
        // Bloom octaves in use this frame: the chain may be longer than the lever asks for.
        val levels = minOf(t.bloomLevels, bloomOctaves.coerceAtLeast(1))
        reflReady = false
        glareDrawn = false
        streamed = false
        passW = rw
        passH = rh
        reflStreak = 0f
        if ((p.floorReflect > 0f || p.floorReflectMatte > 0f) && !floorReflectOff && reflections != GfxQuality.Reflections.OFF) {
            // Real mirror images only where the scene asks and the rung allows; else streaks.
            if (p.floorMirror && reflections == GfxQuality.Reflections.MIRROR) {
                if (p.glowDraws > 0) {
                    drawMirror(p, t)
                    reflReady = true
                    reflTex = t.reflTex
                }
            } else if (t.bloomDone) {
                // Free streaks: last frame's softened glow (1/8 size) gathered up the screen.
                reflReady = true
                reflTex = t.bloomTex[if (levels > 1) 1 else 0]
                reflStreak = STREAK
            }
        }
        val drawFbo = if (t.msFbo != 0) t.msFbo else t.sceneFbo
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, drawFbo)
        GLES30.glViewport(0, 0, rw, rh)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        val cc = p.clearColor
        if (hdr) {
            clearEncoded(cc, p.exposure)
        } else {
            GLES30.glClearColor((cc shr 16 and 255) / 255f, (cc shr 8 and 255) / 255f, (cc and 255) / 255f, 1f)
        }
        GLES30.glDepthMask(true)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        drawBackground(p)
        drawScene(p)
        drawParticles(p)
        if (reflReady) {
            // Let go of the reflection source before the bloom chain draws into it.
            GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, whiteTex)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        }

        // Resolve the multisampled image, then tell the driver the samples and depth can be
        // thrown away (tilers otherwise write them back to memory).
        if (t.msFbo != 0) {
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, t.msFbo)
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, t.sceneFbo)
            GLES30.glBlitFramebuffer(0, 0, rw, rh, 0, 0, rw, rh, GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST)
            GLES30.glInvalidateFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 2, discardColorDepth, 0)
        } else {
            GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, discardDepth, 0)
        }
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE)
        GLES30.glBindVertexArray(quadVao)

        // Bloom: bright parts at quarter size, halved octave by octave with a dual filter, then
        // added back up with tent filters so glows get a tight core and a wide soft halo.
        if (p.bloom > 0f) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.bloomFbo[0])
            GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, discardColor, 0)
            GLES30.glViewport(0, 0, t.bloomW[0], t.bloomH[0])
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.sceneTex)
            if (hdr) {
                // Linear light after exposure: a soft-kneed threshold, a Karis average, a cap.
                GLES30.glUseProgram(brightHdr.id)
                GLES30.glUniform1i(brightHdr.loc("uTex"), 0)
                GLES30.glUniform2f(brightHdr.loc("uTexel"), 1f / rw, 1f / rh)
                GLES30.glUniform1f(brightHdr.loc("uExposure"), p.exposure)
                GLES30.glUniform1f(brightHdr.loc("uThreshold"), p.bloomThresholdHdr)
            } else {
                GLES30.glUseProgram(brightProg)
                GLES30.glUniform1i(bright.loc("uTex"), 0)
                GLES30.glUniform2f(bright.loc("uTexel"), 1f / rw, 1f / rh)
                GLES30.glUniform1f(bright.loc("uThreshold"), p.bloomThreshold)
            }
            drawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glUseProgram(downProg)
            GLES30.glUniform1i(down.loc("uTex"), 0)
            for (i in 1 until levels) {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.bloomFbo[i])
                GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, discardColor, 0)
                GLES30.glViewport(0, 0, t.bloomW[i], t.bloomH[i])
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.bloomTex[i - 1])
                GLES30.glUniform2f(down.loc("uTexel"), 1f / t.bloomW[i - 1], 1f / t.bloomH[i - 1])
                drawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            }
            // Anamorphic glare: only the brightest of the second octave, smeared sideways. Taken
            // before the up chain adds the wider octaves into that texture.
            glareDrawn = false
            if (finish && glareOn && levels > 1 && t.glareFbo != 0) {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.glareFbo)
                GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, discardColor, 0)
                GLES30.glViewport(0, 0, t.bloomW[1], t.bloomH[1])
                GLES30.glUseProgram(glare.id)
                GLES30.glUniform1i(glare.loc("uTex"), 0)
                GLES30.glUniform2f(glare.loc("uStep"), HdrLook.GLARE_SPACING / t.bloomW[1], 0f)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.bloomTex[1])
                drawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
                glareDrawn = true
            }
            GLES30.glUseProgram(upProg)
            GLES30.glUniform1i(up.loc("uTex"), 0)
            GLES30.glUniform1f(up.loc("uWeight"), p.bloomRadius)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
            for (i in levels - 2 downTo 0) {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.bloomFbo[i])
                GLES30.glViewport(0, 0, t.bloomW[i], t.bloomH[i])
                if (i == 0) {
                    // The quarter-size bright pass is barely blurred: keep only part of it, so
                    // lettering on bright signs isn't washed out by its own glow.
                    GLES30.glBlendColor(0f, 0f, 0f, BLOOM_CORE)
                    GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_CONSTANT_ALPHA)
                }
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.bloomTex[i + 1])
                GLES30.glUniform2f(up.loc("uTexel"), 1f / t.bloomW[i + 1], 1f / t.bloomH[i + 1])
                drawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            }
            GLES30.glDisable(GLES30.GL_BLEND)
            t.bloomDone = true
        }

        // Composite into the window at the pass's rectangle (GL's origin is bottom-left).
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, outFbo)
        GLES30.glViewport(p.vx, surfaceH - p.vy - p.vh, p.vw, p.vh)
        if (p.clip) {
            GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
            GLES30.glScissor(p.cx0, surfaceH - p.cy1, (p.cx1 - p.cx0).coerceAtLeast(0), (p.cy1 - p.cy0).coerceAtLeast(0))
        }
        val c = if (hdr) compHdr else comp
        GLES30.glUseProgram(c.id)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.sceneTex)
        GLES30.glUniform1i(c.loc("uScene"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.bloomTex[0])
        GLES30.glUniform1i(c.loc("uBloom"), 1)
        // The octaves add up: scale back so the glow's total light stays what one level gave
        // (a little more, since the wide halo is fainter per pixel, but not so much that big
        // bright areas such as a daytime sky turn hazy).
        val w = p.bloomRadius
        var sum = if (levels > 1) BLOOM_CORE else 1f
        var wk = 1f
        for (i in 1 until levels) {
            wk *= w
            sum += wk
        }
        val bloomNorm = 1.25f / sum
        GLES30.glUniform1f(c.loc("uBloomAmount"), p.bloom * bloomNorm * (if (hdr) HdrLook.BLOOM_GAIN else 1f))
        GLES30.glUniform2f(c.loc("uTexel"), 1f / rw, 1f / rh)
        GLES30.glUniform1f(c.loc("uVignette"), p.vignette)
        GLES30.glUniform1f(c.loc("uSharpen"), p.sharpen)
        GLES30.glUniform1f(c.loc("uGrade"), p.grade)
        if (hdr) {
            GLES30.glUniform1f(c.loc("uExposure"), p.exposure)
            // The cinematic finish. Each strength is zero when its lever is off; reduce motion
            // removes the animated grain, and the aberration's motion coupling.
            val film = finish && filmOn
            GLES30.glUniform1f(c.loc("uGrain"), if (film && !reduce) HdrLook.GRAIN else 0f)
            GLES30.glUniform1f(c.loc("uTime"), (frameNo and 1023L).toFloat())
            GLES30.glUniform1f(c.loc("uAberration"), if (film) HdrLook.ABERRATION * (1f + HdrLook.ABERRATION_MOTION * motion) else 0f)
            val streak = finish && glareOn && glareDrawn
            GLES30.glUniform1f(c.loc("uGlareAmount"), if (streak) HdrLook.GLARE_AMOUNT else 0f)
            if (streak) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.glareTex)
                GLES30.glUniform1i(c.loc("uGlare"), 2)
            }
        }
        drawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        GLES30.glBindVertexArray(0)
    }

    /** Set while a thumbnail is drawn (see [snapshot]). */
    private var snapshotting = false

    /** Whether this pass's glare texture was drawn this frame. */
    private var glareDrawn = false

    /**
     * Clears the HDR scene target to the pass's clear colour, which is authored as a display
     * value: inverse tone mapped, un-exposed and encoded like everything else in the target.
     */
    private fun clearEncoded(argb: Int, exposure: Float) {
        val k = 1f / max(exposure, 0.05f)
        val r = HdrMath.inverseAces((argb shr 16 and 255) / 255f) * k
        val g = HdrMath.inverseAces((argb shr 8 and 255) / 255f) * k
        val b = HdrMath.inverseAces((argb and 255) / 255f) * k
        val e = 1f / (1f + max(r, max(g, b)))
        GLES30.glClearColor(r * e, g * e, b * e, 1f)
    }

    /**
     * How much the camera moved since this set's last frame, 0..1 and smoothed: the radians its
     * view turned plus its travel in world units over [HdrLook.MOTION_TRAVEL]. A dive, a fast
     * turn or a cut reads as full; standing still reads as none. Allocation-free.
     */
    private fun cameraMotion(p: RenderPass, t: Targets): Float {
        val c = p.cam
        val pc = t.prevCam
        var raw = 0f
        if (t.hasPrevCam) {
            val fx = c[9] - pc[9]; val fy = c[10] - pc[10]; val fz = c[11] - pc[11]
            val ex = c[0] - pc[0]; val ey = c[1] - pc[1]; val ez = c[2] - pc[2]
            val turned = sqrt(fx * fx + fy * fy + fz * fz)
            val travelled = sqrt(ex * ex + ey * ey + ez * ez) / HdrLook.MOTION_TRAVEL
            raw = ((turned + travelled) / HdrLook.MOTION_FULL).coerceIn(0f, 1f)
        }
        for (i in 0 until 3) {
            pc[i] = c[i]
            pc[9 + i] = c[9 + i]
        }
        t.hasPrevCam = true
        t.motion += (raw - t.motion) * HdrLook.MOTION_SMOOTH
        return t.motion
    }

    /**
     * Draws the pass's screen-space particles into the scene image, before it is resolved and
     * the bloom is taken from it, so bright sparks and confetti glow like neon. Soft additive
     * glows go first, then the particles alpha-blended, as the 2D fallback paints them.
     */
    private fun drawParticles(p: RenderPass) {
        val n = p.particleVertCount
        if (n == 0 || !particlesOk) return
        val floats = n * RenderPass.PARTICLE_STRIDE
        val fb = floatBuffer(floats)
        fb.put(p.particleVerts, 0, floats).position(0)
        val pr = if (hdrPass) particleHdr else particle
        GLES30.glUseProgram(pr.id)
        if (hdrPass) GLES30.glUniform1f(pr.loc("uExposure"), p.exposure)
        GLES30.glBindVertexArray(partVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, partVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floats * 4, fb, GLES30.GL_STREAM_DRAW)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        val uSoft = pr.loc("uSoft")
        val halo = p.particleHaloCount.coerceIn(0, n)
        if (halo > 0) {
            GLES30.glUniform1f(uSoft, 1f)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
            drawArrays(GLES30.GL_TRIANGLES, 0, halo)
        }
        if (n > halo) {
            GLES30.glUniform1f(uSoft, 0f)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            drawArrays(GLES30.GL_TRIANGLES, halo, n - halo)
        }
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDepthMask(true)
        GLES30.glBindVertexArray(0)
    }

    private fun drawBackground(p: RenderPass) {
        if (p.gradientCount == 0) return
        val n = p.gradientCount
        val need = n * 4 * 5
        val data = if (bgData.size >= need) bgData else FloatArray(need)
        var o = 0
        for (i in 0 until n) {
            val g = i * 8
            val y0 = 1f - 2f * p.gradients[g + 6]
            val y1 = 1f - 2f * p.gradients[g + 7]
            val tr = p.gradients[g]; val tg = p.gradients[g + 1]; val tb = p.gradients[g + 2]
            val br = p.gradients[g + 3]; val bg = p.gradients[g + 4]; val bb = p.gradients[g + 5]
            // Two triangles as a strip per band would need restarts; use separate draws.
            data[o++] = -1f; data[o++] = y0; data[o++] = tr; data[o++] = tg; data[o++] = tb
            data[o++] = 1f; data[o++] = y0; data[o++] = tr; data[o++] = tg; data[o++] = tb
            data[o++] = -1f; data[o++] = y1; data[o++] = br; data[o++] = bg; data[o++] = bb
            data[o++] = 1f; data[o++] = y1; data[o++] = br; data[o++] = bg; data[o++] = bb
        }
        val fb = floatBuffer(need)
        fb.put(data, 0, need).position(0)
        if (hdrPass) {
            GLES30.glUseProgram(bgHdr.id)
            GLES30.glUniform1f(bgHdr.loc("uExposure"), p.exposure)
        } else {
            GLES30.glUseProgram(bgProg)
        }
        GLES30.glBindVertexArray(bgVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bgVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, need * 4, fb, GLES30.GL_STREAM_DRAW)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_BLEND)
        for (i in 0 until n) drawArrays(GLES30.GL_TRIANGLE_STRIP, i * 4, 4)
        GLES30.glDepthMask(true)
    }

    private fun floatBuffer(floats: Int): FloatBuffer {
        if (vertBuf.capacity() < floats) {
            vertBuf = ByteBuffer.allocateDirect(maxOf(floats, vertBuf.capacity() * 2) * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        }
        vertBuf.clear()
        return vertBuf
    }

    /** Whether this pass's floor reflection is ready in [reflTex] ([reflStreak] > 0: bloom streaks). */
    private var reflReady = false
    private var reflTex = 0
    private var reflStreak = 0f
    /** Whether this pass's immediate geometry is already in the stream buffer. */
    private var streamed = false
    /** The size this pass renders at. */
    private var passW = 1
    private var passH = 1

    private fun drawScene(p: RenderPass) {
        // The floor mirror is a display-referred RGBA8 picture, so it always uses the LDR shader.
        scene = if (hdrPass && !mirror) sceneHdr else sceneLdr
        GLES30.glUseProgram(scene.id)
        val c = p.cam
        GLES30.glUniform3f(scene.loc("uEye"), c[0], c[1], c[2])
        GLES30.glUniform3f(scene.loc("uRight"), c[3], c[4], c[5])
        GLES30.glUniform3f(scene.loc("uUp"), c[6], c[7], c[8])
        GLES30.glUniform3f(scene.loc("uFwd"), c[9], c[10], c[11])
        // ndc.x = 2 f/W · x/z + (2 cx/W − 1); ndc.y = 2 f/H · y/z + (1 − 2 cy/H)
        GLES30.glUniform4f(scene.loc("uProj"), 2f * c[12], 2f * c[13] - 1f, 2f * c[15], 1f - 2f * c[14])
        // The pass's own near plane (8 unless a scene needs to get closer, like the hall's eye view).
        val near = p.near.coerceIn(0.25f, FAR * 0.5f)
        GLES30.glUniform2f(scene.loc("uDepth"), (FAR + near) / (FAR - near), -2f * FAR * near / (FAR - near))
        GLES30.glUniform3f(scene.loc("uAmbient"), p.ambient[0], p.ambient[1], p.ambient[2])
        GLES30.glUniform3f(scene.loc("uDirDir"), p.dirDir[0], p.dirDir[1], p.dirDir[2])
        GLES30.glUniform3f(scene.loc("uDirCol"), p.dirCol[0], p.dirCol[1], p.dirCol[2])
        GLES30.glUniform3f(scene.loc("uFog"), p.fogNear, p.fogFar, p.fogFloor)
        GLES30.glUniform1f(scene.loc("uExposure"), p.exposure)
        GLES30.glUniform1f(scene.loc("uRim"), p.rim)
        GLES30.glUniform1f(scene.loc("uFloorGlow"), p.floorGlow)
        GLES30.glUniform1f(scene.loc("uMirror"), if (mirror) -1f else 1f)
        GLES30.glUniform1f(scene.loc("uEnvAmount"), p.envReflect)
        // Units 2 and 3: the reflected room and the floor mirror (a blank stand-in while the
        // mirror itself is being drawn, so it is never read and written at once).
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, envTex)
        GLES30.glUniform1i(scene.loc("uEnv"), 2)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        if (!mirror && reflReady) {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, reflTex)
            // The glow is fainter than the mirrored image itself: lift the streaks to match.
            val k = if (reflStreak > 0f) STREAK_GAIN else 1f
            GLES30.glUniform4f(scene.loc("uReflInfo"), 1f / passW, 1f / passH, p.floorReflect * k, p.floorReflectMatte * k)
        } else {
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, whiteTex)
            GLES30.glUniform4f(scene.loc("uReflInfo"), 0f, 0f, 0f, 0f)
        }
        GLES30.glUniform1f(scene.loc("uReflStreak"), if (mirror) 0f else reflStreak)
        GLES30.glUniform1i(scene.loc("uRefl"), 3)
        for (i in 0 until p.lightCount) {
            val o = i * 8
            lightPos[i * 4] = p.lights[o]; lightPos[i * 4 + 1] = p.lights[o + 1]
            lightPos[i * 4 + 2] = p.lights[o + 2]; lightPos[i * 4 + 3] = p.lights[o + 3]
            lightCol[i * 4] = p.lights[o + 4]; lightCol[i * 4 + 1] = p.lights[o + 5]
            lightCol[i * 4 + 2] = p.lights[o + 6]; lightCol[i * 4 + 3] = p.lights[o + 7]
        }
        if (p.lightCount > 0) {
            GLES30.glUniform4fv(scene.loc("uLightPos"), p.lightCount, lightPos, 0)
            GLES30.glUniform4fv(scene.loc("uLightCol"), p.lightCount, lightCol, 0)
        }
        // Light grid.
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, gridTex)
        if (p.gridW > 0 && !mirror) {
            val bytes = p.gridW * 2 * p.gridH * 4
            ensureUpload(bytes)
            upload.put(p.grid, 0, bytes).position(0)
            if (gridTexW == p.gridW * 2 && gridTexH == p.gridH) {
                GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, p.gridW * 2, p.gridH, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, upload)
            } else {
                GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, p.gridW * 2, p.gridH, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, upload)
                gridTexW = p.gridW * 2
                gridTexH = p.gridH
            }
            GLES30.glUniform4f(scene.loc("uGridInfo"), p.gridX0, p.gridZ0, p.gridInvCell, 0f)
            GLES30.glUniform2i(scene.loc("uGridSize"), p.gridW, p.gridH)
        } else {
            GLES30.glUniform2i(scene.loc("uGridSize"), 0, 0)
        }
        GLES30.glUniform1i(scene.loc("uGrid"), 1)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glUniform1i(scene.loc("uTex"), 0)

        // Stream the immediate geometry (once per pass, shared by the mirror and the scene).
        if (p.vertCount > 0 && !streamed) {
            streamed = true
            val floats = p.vertCount * RenderPass.STRIDE
            val fb = floatBuffer(floats)
            fb.put(p.verts, 0, floats).position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, streamVbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floats * 4, fb, GLES30.GL_STREAM_DRAW)
        }

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        // Mirroring flips every triangle's winding.
        GLES30.glFrontFace(if (mirror) GLES30.GL_CW else GLES30.GL_CCW)
        GLES30.glCullFace(GLES30.GL_BACK)
        boundBlend = -1
        boundTex = -1
        cullOn = false
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        var boundModelMatrix = false
        val uModel = scene.loc("uModel")
        val uTint = scene.loc("uTint")
        val uEm = scene.loc("uEmissiveMul")
        uCut = scene.loc("uAlphaCut")
        uGlass = scene.loc("uGlass")
        GLES30.glUniformMatrix4fv(uModel, 1, false, ident, 0)
        GLES30.glUniform4f(uTint, 1f, 1f, 1f, 1f)
        GLES30.glUniform1f(uEm, 1f)
        for (d in 0 until p.drawCount) {
            val o = d * 5
            val kind = p.draws[o]
            val blend = p.draws[o + 1]
            val index = p.draws[o + 2]
            if (mirror && !p.drawGlow[d]) continue
            setBlend(blend)
            if (kind == RenderPass.KIND_BATCH) {
                if (boundModelMatrix) {
                    GLES30.glUniformMatrix4fv(uModel, 1, false, ident, 0)
                    GLES30.glUniform4f(uTint, 1f, 1f, 1f, 1f)
                    GLES30.glUniform1f(uEm, 1f)
                    boundModelMatrix = false
                }
                setCull(false)
                bindTexture(p.textures[index])
                GLES30.glBindVertexArray(streamVao)
                drawArrays(GLES30.GL_TRIANGLES, p.draws[o + 3], p.draws[o + 4])
            } else {
                val io = index * 22
                val model = p.models[p.instances[io + 21].toInt()]
                val mesh = meshFor(model)
                GLES30.glUniformMatrix4fv(uModel, 1, false, p.instances, io)
                GLES30.glUniform4f(uTint, p.instances[io + 16], p.instances[io + 17], p.instances[io + 18], p.instances[io + 19])
                GLES30.glUniform1f(uEm, p.instances[io + 20])
                boundModelMatrix = true
                GLES30.glBindVertexArray(mesh.vao)
                val g = mesh.groups
                for (k in 0 until g.size / GROUP) {
                    val go = k * GROUP
                    if (g[go + 1] != blend || (mirror && g[go + 5] == 0)) continue
                    setCull(g[go + 2] == 1)
                    bindTexture(mesh.textures[g[go]])
                    drawArrays(GLES30.GL_TRIANGLES, g[go + 3], g[go + 4])
                }
            }
        }
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE)
        GLES30.glDepthMask(true)
        GLES30.glFrontFace(GLES30.GL_CCW)
        GLES30.glBindVertexArray(0)
    }

    private fun setCull(on: Boolean) {
        if (on == cullOn) return
        cullOn = on
        if (on) GLES30.glEnable(GLES30.GL_CULL_FACE) else GLES30.glDisable(GLES30.GL_CULL_FACE)
    }

    private fun setBlend(b: Int) {
        if (b == boundBlend) return
        boundBlend = b
        when (b) {
            Blend.OPAQUE.ordinal -> {
                GLES30.glDisable(GLES30.GL_BLEND)
                GLES30.glDepthMask(true)
                val ms = msActive && !mirror
                if (ms) GLES30.glEnable(GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE)
                GLES30.glUniform1f(uCut, if (ms) 0.08f else 0.5f)
                GLES30.glUniform1f(uGlass, 0f)
            }
            Blend.ALPHA.ordinal -> {
                GLES30.glDisable(GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE)
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                GLES30.glDepthMask(false)
                GLES30.glUniform1f(uCut, 0.003f)
                GLES30.glUniform1f(uGlass, 1f)
            }
            else -> {
                GLES30.glDisable(GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE)
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE, GLES30.GL_ZERO, GLES30.GL_ONE)
                GLES30.glDepthMask(false)
                GLES30.glUniform1f(uCut, 0.003f)
                GLES30.glUniform1f(uGlass, 0f)
            }
        }
    }
}
