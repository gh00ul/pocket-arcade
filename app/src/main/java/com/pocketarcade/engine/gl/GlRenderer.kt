package com.pocketarcade.engine.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.util.Log
import com.pocketarcade.engine.r3d.Blend
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

/**
 * Draws [RenderPass]es with OpenGL ES 3: multisampled scene rendering at an adaptive resolution,
 * per-pixel lighting, a bloom glow and a final composite into the window. Lives on the GL thread.
 */
internal class GlRenderer {
    companion object {
        private const val TAG = "PocketArcadeGL"
        private const val NEAR = 8f
        private const val FAR = 9000f
        private const val GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE
        private const val GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF
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
    private var blurProg = 0
    private var compProg = 0

    /** A linked program with its uniform locations looked up once each. */
    private class Prog(val id: Int) {
        private val locs = HashMap<String, Int>()
        fun loc(name: String): Int = locs.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }
    }
    private var scene = Prog(0)
    private var bg = Prog(0)
    private var bright = Prog(0)
    private var blur = Prog(0)
    private var comp = Prog(0)
    private var streamVbo = 0
    private var streamVao = 0
    private var bgVbo = 0
    private var bgVao = 0
    private var quadVbo = 0
    private var quadVao = 0
    private var gridTex = 0
    private var gridTexW = 0
    private var gridTexH = 0
    private var whiteTex = 0
    private var samples = 0
    private var anisotropy = 1f

    /** Fraction of full resolution the scene renders at; eased down if frames run slow. */
    var renderScale = 0.8f

    private class Targets(val w: Int, val h: Int) {
        var msFbo = 0
        var msColor = 0
        var msDepth = 0
        var sceneFbo = 0
        var sceneTex = 0
        var sceneDepth = 0
        var bloomW = 0
        var bloomH = 0
        var bloomFboA = 0
        var bloomTexA = 0
        var bloomFboB = 0
        var bloomTexB = 0
        var lastUsed = 0L
    }
    private val targets = ArrayList<Targets>()
    private var frameNo = 0L

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
        blurProg = program(GlShaders.POST_VS, GlShaders.BLUR_FS)
        compProg = program(GlShaders.POST_VS, GlShaders.COMPOSITE_FS)
        scene = Prog(sceneProg)
        bg = Prog(bgProg)
        bright = Prog(brightProg)
        blur = Prog(blurProg)
        comp = Prog(compProg)

        GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, tmp, 0)
        samples = minOf(4, tmp[0])
        val ext = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: ""
        if (ext.contains("GL_EXT_texture_filter_anisotropic")) {
            val f = FloatArray(1)
            GLES30.glGetFloatv(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT, f, 0)
            anisotropy = minOf(8f, f[0])
        }
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        Log.i(TAG, "GL ${GLES30.glGetString(GLES30.GL_VERSION)} / ${GLES30.glGetString(GLES30.GL_RENDERER)}, MSAA $samples, aniso $anisotropy")

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
        GLES30.glGenTextures(1, tmp, 0); whiteTex = tmp[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, whiteTex)
        upload.clear()
        upload.put(byteArrayOf(-1, -1, -1, -1)).position(0)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, upload)
        texParams(GLES30.GL_NEAREST, GLES30.GL_NEAREST, false)
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
        for (p in m.polys) {
            val key = Key(p.region.tex, p.blend.ordinal, p.cull)
            val list = groups.getOrPut(key) { ArrayList() }
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
                    out[o + 8] = tr; out[o + 9] = tg; out[o + 10] = tb; out[o + 11] = 1f
                    out[o + 12] = p.emissive; out[o + 13] = 1f; out[o + 14] = p.gloss; out[o + 15] = 1f
                    o += S
                }
            }
            list += out
        }
        var total = 0
        for (l in groups.values) for (a in l) total += a.size
        val fb = ByteBuffer.allocateDirect(total * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        val info = IntArray(groups.size * 5)
        val textures = ArrayList<Texture>()
        var gi = 0
        var first = 0
        for ((key, list) in groups) {
            var count = 0
            for (a in list) {
                fb.put(a); count += a.size / S
            }
            info[gi * 5] = textures.size
            info[gi * 5 + 1] = key.blend
            info[gi * 5 + 2] = if (key.cull) 1 else 0
            info[gi * 5 + 3] = first
            info[gi * 5 + 4] = count
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

    /** Frees GPU copies of models and textures the app no longer holds. */
    private fun collectGarbage() {
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
        targets.firstOrNull { it.w == w && it.h == h }?.let { it.lastUsed = frameNo; return it }
        // Drop the least recently used set when too many sizes pile up.
        if (targets.size >= 3) {
            val old = targets.minByOrNull { it.lastUsed }!!
            freeTargets(old)
            targets.remove(old)
        }
        val t = Targets(w, h)
        t.lastUsed = frameNo
        if (samples > 1) {
            GLES30.glGenFramebuffers(1, tmp, 0); t.msFbo = tmp[0]
            GLES30.glGenRenderbuffers(1, tmp, 0); t.msColor = tmp[0]
            GLES30.glGenRenderbuffers(1, tmp, 0); t.msDepth = tmp[0]
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, t.msColor)
            GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_RGBA8, w, h)
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, t.msDepth)
            GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_DEPTH_COMPONENT24, w, h)
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.msFbo)
            GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, t.msColor)
            GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, t.msDepth)
            if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
                Log.w(TAG, "MSAA framebuffer incomplete; rendering without multisampling")
                samples = 0
                tmp[0] = t.msFbo; GLES30.glDeleteFramebuffers(1, tmp, 0)
                tmp[0] = t.msColor; GLES30.glDeleteRenderbuffers(1, tmp, 0)
                tmp[0] = t.msDepth; GLES30.glDeleteRenderbuffers(1, tmp, 0)
                t.msFbo = 0
            }
        }
        GLES30.glGenFramebuffers(1, tmp, 0); t.sceneFbo = tmp[0]
        t.sceneTex = colorTexture(w, h)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.sceneFbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, t.sceneTex, 0)
        if (t.msFbo == 0) {
            GLES30.glGenRenderbuffers(1, tmp, 0); t.sceneDepth = tmp[0]
            GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, t.sceneDepth)
            GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, w, h)
            GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, t.sceneDepth)
        }
        t.bloomW = max(1, w / 4)
        t.bloomH = max(1, h / 4)
        GLES30.glGenFramebuffers(1, tmp, 0); t.bloomFboA = tmp[0]
        t.bloomTexA = colorTexture(t.bloomW, t.bloomH)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.bloomFboA)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, t.bloomTexA, 0)
        GLES30.glGenFramebuffers(1, tmp, 0); t.bloomFboB = tmp[0]
        t.bloomTexB = colorTexture(t.bloomW, t.bloomH)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.bloomFboB)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, t.bloomTexB, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        targets += t
        return t
    }

    private fun colorTexture(w: Int, h: Int): Int {
        GLES30.glGenTextures(1, tmp, 0)
        val id = tmp[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        texParams(GLES30.GL_LINEAR, GLES30.GL_LINEAR, false)
        return id
    }

    private fun freeTargets(t: Targets) {
        fun fbo(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteFramebuffers(1, tmp, 0) } }
        fun rb(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteRenderbuffers(1, tmp, 0) } }
        fun tex(id: Int) { if (id != 0) { tmp[0] = id; GLES30.glDeleteTextures(1, tmp, 0) } }
        fbo(t.msFbo); rb(t.msColor); rb(t.msDepth)
        fbo(t.sceneFbo); tex(t.sceneTex); rb(t.sceneDepth)
        fbo(t.bloomFboA); tex(t.bloomTexA); fbo(t.bloomFboB); tex(t.bloomTexB)
    }

    // ------------------------------------------------------------------ frame

    /** Draws every pass into the window surface ([surfaceW] × [surfaceH]). */
    fun drawFrame(passes: Collection<RenderPass>, surfaceW: Int, surfaceH: Int) {
        frameNo++
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
        drawPass(p, snapFbo, h, 1f)
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
        val drawFbo = if (t.msFbo != 0) t.msFbo else t.sceneFbo
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, drawFbo)
        GLES30.glViewport(0, 0, rw, rh)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        val cc = p.clearColor
        GLES30.glClearColor((cc shr 16 and 255) / 255f, (cc shr 8 and 255) / 255f, (cc and 255) / 255f, 1f)
        GLES30.glDepthMask(true)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        drawBackground(p)
        drawScene(p)

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

        // Bloom: bright parts at quarter size, blurred twice each way.
        if (p.bloom > 0f) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.bloomFboA)
            GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, discardColor, 0)
            GLES30.glViewport(0, 0, t.bloomW, t.bloomH)
            GLES30.glUseProgram(brightProg)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.sceneTex)
            GLES30.glUniform1i(bright.loc("uTex"), 0)
            GLES30.glUniform2f(bright.loc("uTexel"), 1f / rw, 1f / rh)
            GLES30.glUniform1f(bright.loc("uThreshold"), 0.62f)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glUseProgram(blurProg)
            GLES30.glUniform1i(blur.loc("uTex"), 0)
            repeat(2) { pass ->
                val spread = 1f + pass
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.bloomFboB)
                GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, discardColor, 0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.bloomTexA)
                GLES30.glUniform2f(blur.loc("uDir"), spread / t.bloomW, 0f)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.bloomFboA)
                GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, discardColor, 0)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.bloomTexB)
                GLES30.glUniform2f(blur.loc("uDir"), 0f, spread / t.bloomH)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            }
        }

        // Composite into the window at the pass's rectangle (GL's origin is bottom-left).
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, outFbo)
        GLES30.glViewport(p.vx, surfaceH - p.vy - p.vh, p.vw, p.vh)
        if (p.clip) {
            GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
            GLES30.glScissor(p.cx0, surfaceH - p.cy1, (p.cx1 - p.cx0).coerceAtLeast(0), (p.cy1 - p.cy0).coerceAtLeast(0))
        }
        GLES30.glUseProgram(compProg)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.sceneTex)
        GLES30.glUniform1i(comp.loc("uScene"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t.bloomTexA)
        GLES30.glUniform1i(comp.loc("uBloom"), 1)
        GLES30.glUniform1f(comp.loc("uBloomAmount"), p.bloom)
        GLES30.glUniform1f(comp.loc("uVignette"), 0.22f)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
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
        GLES30.glUseProgram(bgProg)
        GLES30.glBindVertexArray(bgVao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, bgVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, need * 4, fb, GLES30.GL_STREAM_DRAW)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(false)
        GLES30.glDisable(GLES30.GL_BLEND)
        for (i in 0 until n) GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, i * 4, 4)
        GLES30.glDepthMask(true)
    }

    private fun floatBuffer(floats: Int): FloatBuffer {
        if (vertBuf.capacity() < floats) {
            vertBuf = ByteBuffer.allocateDirect(maxOf(floats, vertBuf.capacity() * 2) * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        }
        vertBuf.clear()
        return vertBuf
    }

    private fun drawScene(p: RenderPass) {
        val prog = sceneProg
        GLES30.glUseProgram(prog)
        val c = p.cam
        GLES30.glUniform3f(scene.loc("uEye"), c[0], c[1], c[2])
        GLES30.glUniform3f(scene.loc("uRight"), c[3], c[4], c[5])
        GLES30.glUniform3f(scene.loc("uUp"), c[6], c[7], c[8])
        GLES30.glUniform3f(scene.loc("uFwd"), c[9], c[10], c[11])
        // ndc.x = 2 f/W · x/z + (2 cx/W − 1); ndc.y = 2 f/H · y/z + (1 − 2 cy/H)
        GLES30.glUniform4f(scene.loc("uProj"), 2f * c[12], 2f * c[13] - 1f, 2f * c[15], 1f - 2f * c[14])
        GLES30.glUniform2f(scene.loc("uDepth"), (FAR + NEAR) / (FAR - NEAR), -2f * FAR * NEAR / (FAR - NEAR))
        GLES30.glUniform3f(scene.loc("uAmbient"), p.ambient[0], p.ambient[1], p.ambient[2])
        GLES30.glUniform3f(scene.loc("uDirDir"), p.dirDir[0], p.dirDir[1], p.dirDir[2])
        GLES30.glUniform3f(scene.loc("uDirCol"), p.dirCol[0], p.dirCol[1], p.dirCol[2])
        GLES30.glUniform3f(scene.loc("uFog"), p.fogNear, p.fogFar, p.fogFloor)
        GLES30.glUniform1f(scene.loc("uExposure"), p.exposure)
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
        if (p.gridW > 0) {
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

        // Stream the immediate geometry.
        if (p.vertCount > 0) {
            val floats = p.vertCount * RenderPass.STRIDE
            val fb = floatBuffer(floats)
            fb.put(p.verts, 0, floats).position(0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, streamVbo)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, floats * 4, fb, GLES30.GL_STREAM_DRAW)
        }

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glFrontFace(GLES30.GL_CCW)
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
        GLES30.glUniformMatrix4fv(uModel, 1, false, ident, 0)
        GLES30.glUniform4f(uTint, 1f, 1f, 1f, 1f)
        GLES30.glUniform1f(uEm, 1f)
        for (d in 0 until p.drawCount) {
            val o = d * 5
            val kind = p.draws[o]
            val blend = p.draws[o + 1]
            val index = p.draws[o + 2]
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
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, p.draws[o + 3], p.draws[o + 4])
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
                for (k in 0 until g.size / 5) {
                    if (g[k * 5 + 1] != blend) continue
                    setCull(g[k * 5 + 2] == 1)
                    bindTexture(mesh.textures[g[k * 5]])
                    GLES30.glDrawArrays(GLES30.GL_TRIANGLES, g[k * 5 + 3], g[k * 5 + 4])
                }
            }
        }
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glDisable(GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE)
        GLES30.glDepthMask(true)
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
                if (samples > 1) GLES30.glEnable(GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE)
                GLES30.glUniform1f(uCut, if (samples > 1) 0.08f else 0.5f)
            }
            Blend.ALPHA.ordinal -> {
                GLES30.glDisable(GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE)
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                GLES30.glDepthMask(false)
                GLES30.glUniform1f(uCut, 0.003f)
            }
            else -> {
                GLES30.glDisable(GLES30.GL_SAMPLE_ALPHA_TO_COVERAGE)
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE, GLES30.GL_ZERO, GLES30.GL_ONE)
                GLES30.glDepthMask(false)
                GLES30.glUniform1f(uCut, 0.003f)
            }
        }
    }
}
