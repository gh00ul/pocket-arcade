package com.pocketarcade.engine.r3d

import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

enum class Blend {
    /** Depth-tested and written; texels with alpha under one half are cut out. */
    OPAQUE,
    /** Depth-tested, not written; blended by texel alpha × polygon alpha. */
    ALPHA,
    /** Depth-tested, not written; added on top (glows, light pools, sparks). */
    ADD,
}

/**
 * Records a frame of 3D drawing for the GPU. Scenes describe polygons (lit per pixel by the
 * [lighting] rig), sprites and whole [Model]s; [finishFrame] packs everything into a
 * [RenderPass] that the GL thread draws at full resolution with anti-aliasing and bloom.
 *
 * Polygons have up to 8 vertices. Opaque polygons are grouped by texture; see-through ones are
 * drawn in the order they were recorded, after everything opaque.
 */
class Renderer3D(w: Int, h: Int) {
    /** Size of the camera image in pixels (the camera's cx, cy and focal length use it). */
    var width = w
        private set
    var height = h
        private set

    val camera = Camera3D()
    val lighting = Lighting()

    /** Fog darkens surfaces from [fogNear] to [fogFar] (view depth), down to [fogFloor] brightness. */
    var fogNear = 1e8f
    var fogFar = 2e8f
    var fogFloor = 0.15f

    /** Overall brightness before tone mapping, and how strongly bright things glow. */
    var exposure = 1f
    var bloom = 0.8f

    var polysDrawn = 0
        private set

    private companion object {
        const val MAXV = 8
        const val S = RenderPass.STRIDE
    }

    private val pool = ConcurrentLinkedQueue<RenderPass>()
    private var pass: RenderPass? = null

    // Opaque immediate geometry, bucketed by texture.
    private class Bucket(val tex: Texture) {
        var data = FloatArray(1024 * S)
        var count = 0
    }
    private val buckets = IdentityHashMap<Texture, Bucket>()
    private val bucketList = ArrayList<Bucket>()
    private var lastBucket: Bucket? = null

    // See-through immediate geometry and model layers, in submission order.
    private var trans = FloatArray(1024 * S)
    private var transCount = 0
    private var order = IntArray(64 * 5)
    private var orderCount = 0
    private var opaqueInstances = IntArray(64)
    private var opaqueInstanceCount = 0

    private val texIndex = IdentityHashMap<Texture, Int>()
    private var fogUsedNear = 1e8f
    private var fogUsedFar = 2e8f
    private var fogUsedFloor = 0f

    fun resize(w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
    }

    /** Starts recording a new frame. */
    fun startFrame() {
        val p = pool.poll() ?: RenderPass(pool)
        p.reset()
        pass = p
        for (b in bucketList) b.count = 0
        lastBucket = null
        transCount = 0
        orderCount = 0
        opaqueInstanceCount = 0
        texIndex.clear()
        polysDrawn = 0
        fogUsedNear = 1e8f
        fogUsedFar = 2e8f
        fogUsedFloor = 0f
    }

    private fun current(): RenderPass = pass ?: run { startFrame(); pass!! }

    /** Clears the background to [argb]. */
    fun clear(argb: Int) {
        val p = current()
        p.clearColor = argb or -0x1000000
        p.gradientCount = 0
    }

    /** Paints rows [y0, y1) of the background (camera image pixels) with a vertical gradient. */
    fun gradient(top: Int, bottom: Int, y0: Int = 0, y1: Int = height) {
        current().addGradient(top, bottom, y0 / height.toFloat(), y1 / height.toFloat())
    }

    // ------------------------------------------------------------------ polygon assembly

    private var n = 0
    private val wx = FloatArray(MAXV)
    private val wy = FloatArray(MAXV)
    private val wz = FloatArray(MAXV)
    private val wu = FloatArray(MAXV)
    private val wv = FloatArray(MAXV)
    private val wnx = FloatArray(MAXV)
    private val wny = FloatArray(MAXV)
    private val wnz = FloatArray(MAXV)
    private var smoothNormals = false

    private var region: Region? = null
    private var blend = Blend.OPAQUE
    private var emissive = 0f
    private var alphaK = 1f
    private var bias = 1f
    private var cull = true
    private var gloss = 0f
    private var hasNormal = false
    private var nX = 0f
    private var nY = 0f
    private var nZ = 1f
    private var tR = 1f
    private var tG = 1f
    private var tB = 1f

    /**
     * Starts a polygon. [emissive] > 0 ignores lighting and uses that brightness (1 = texture
     * colour). [depthBias] > 1 pulls the polygon toward the camera for depth tests. [gloss]
     * (0..1) adds specular highlights.
     */
    fun begin(
        region: Region,
        blend: Blend = Blend.OPAQUE,
        emissive: Float = 0f,
        alpha: Float = 1f,
        depthBias: Float = 1f,
        cull: Boolean = true,
        gloss: Float = 0f,
    ) {
        this.region = region
        this.blend = blend
        this.emissive = emissive
        this.alphaK = alpha
        this.bias = depthBias
        this.cull = cull
        this.gloss = gloss
        n = 0
        hasNormal = false
        smoothNormals = false
        tR = 1f; tG = 1f; tB = 1f
    }

    fun normal(x: Float, y: Float, z: Float) {
        nX = x; nY = y; nZ = z
        hasNormal = true
    }

    fun tint(r: Float, g: Float, b: Float) {
        tR = r; tG = g; tB = b
    }

    fun tint(argb: Int) {
        if (argb == -1) return
        tR = (argb shr 16 and 255) / 255f
        tG = (argb shr 8 and 255) / 255f
        tB = (argb and 255) / 255f
    }

    /** Adds a vertex: world position and texel coordinates inside the region. */
    fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float) {
        if (n >= MAXV) return
        wx[n] = x; wy[n] = y; wz[n] = z; wu[n] = u; wv[n] = v
        n++
    }

    /** Adds a vertex with its own normal, for smoothly shaded surfaces. */
    fun vertex(x: Float, y: Float, z: Float, u: Float, v: Float, nx: Float, ny: Float, nz: Float) {
        if (n >= MAXV) return
        wx[n] = x; wy[n] = y; wz[n] = z; wu[n] = u; wv[n] = v
        wnx[n] = nx; wny[n] = ny; wnz[n] = nz
        smoothNormals = true
        n++
    }

    fun end() {
        val reg = region ?: return
        if (n < 3) return
        val cam = camera
        if (!hasNormal) {
            val ax = wx[2] - wx[0]; val ay = wy[2] - wy[0]; val az = wz[2] - wz[0]
            val bx = wx[1] - wx[0]; val by = wy[1] - wy[0]; val bz = wz[1] - wz[0]
            var cxn = ay * bz - az * by
            var cyn = az * bx - ax * bz
            var czn = ax * by - ay * bx
            val l = sqrt(cxn * cxn + cyn * cyn + czn * czn).coerceAtLeast(1e-6f)
            cxn /= l; cyn /= l; czn /= l
            nX = cxn; nY = cyn; nZ = czn
        }
        if (cull) {
            var mx = 0f
            var my = 0f
            var mz = 0f
            for (i in 0 until n) {
                mx += wx[i]; my += wy[i]; mz += wz[i]
            }
            mx /= n; my /= n; mz /= n
            if ((mx - cam.ex) * nX + (my - cam.ey) * nY + (mz - cam.ez) * nZ >= 0f) return
        }
        // Quick reject when every vertex is behind the eye.
        var anyFront = false
        for (i in 0 until n) {
            if (cam.viewZ(wx[i], wy[i], wz[i]) > cam.near) {
                anyFront = true
                break
            }
        }
        if (!anyFront) return
        polysDrawn++
        val tex = reg.tex
        noteTexture(tex)
        val tris = n - 2
        val count = tris * 3
        val dst: FloatArray
        var o: Int
        if (blend == Blend.OPAQUE) {
            var b = lastBucket
            if (b == null || b.tex !== tex) {
                b = buckets[tex] ?: Bucket(tex).also { buckets[tex] = it; bucketList += it }
                lastBucket = b
            }
            if ((b.count + count) * S > b.data.size) b.data = b.data.copyOf(maxOf((b.count + count) * S, b.data.size * 2))
            o = b.count * S
            b.count += count
            dst = b.data
        } else {
            if ((transCount + count) * S > trans.size) trans = trans.copyOf(maxOf((transCount + count) * S, trans.size * 2))
            o = transCount * S
            // Merge with the previous see-through batch when the texture and blend match.
            val bi = blend.ordinal
            val ti = texIndex[tex]!!
            if (orderCount > 0) {
                val p = (orderCount - 1) * 5
                if (order[p] == RenderPass.KIND_BATCH && order[p + 1] == bi && order[p + 2] == ti && order[p + 3] + order[p + 4] == transCount) {
                    order[p + 4] += count
                } else {
                    addOrder(RenderPass.KIND_BATCH, bi, ti, transCount, count)
                }
            } else {
                addOrder(RenderPass.KIND_BATCH, bi, ti, transCount, count)
            }
            transCount += count
            dst = trans
        }
        val iw = 1f / tex.width
        val ih = 1f / tex.height
        val fog = if (fogNear < 1e7f) 1f else 0f
        if (fog > 0f) {
            fogUsedNear = fogNear; fogUsedFar = fogFar; fogUsedFloor = fogFloor
        }
        val rx = reg.x.toFloat()
        val ry = reg.y.toFloat()
        for (t in 1..tris) {
            for (k in 0 until 3) {
                val i = when (k) {
                    0 -> 0
                    1 -> t
                    else -> t + 1
                }
                dst[o] = wx[i]; dst[o + 1] = wy[i]; dst[o + 2] = wz[i]
                if (smoothNormals) {
                    dst[o + 3] = wnx[i]; dst[o + 4] = wny[i]; dst[o + 5] = wnz[i]
                } else {
                    dst[o + 3] = nX; dst[o + 4] = nY; dst[o + 5] = nZ
                }
                dst[o + 6] = (rx + wu[i]) * iw
                dst[o + 7] = (ry + wv[i]) * ih
                dst[o + 8] = tR; dst[o + 9] = tG; dst[o + 10] = tB; dst[o + 11] = alphaK
                dst[o + 12] = emissive; dst[o + 13] = bias; dst[o + 14] = gloss; dst[o + 15] = fog
                o += S
            }
        }
    }

    private fun noteTexture(tex: Texture): Int {
        val known = texIndex[tex]
        if (known != null) return known
        tex.prepare()
        val p = current()
        val i = p.textures.size
        p.textures += tex
        texIndex[tex] = i
        return i
    }

    private fun addOrder(kind: Int, blend: Int, index: Int, first: Int, count: Int) {
        if ((orderCount + 1) * 5 > order.size) order = order.copyOf(order.size * 2)
        val o = orderCount * 5
        order[o] = kind; order[o + 1] = blend; order[o + 2] = index; order[o + 3] = first; order[o + 4] = count
        orderCount++
    }

    // ------------------------------------------------------------------ models

    private val identity = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    /** Draws [model] (placed by [xf]); [only] limits it to one blend layer. */
    fun drawModel(model: Model, only: Blend?, emissiveBoost: Float, xf: Xform?, tint: Int) {
        val p = current()
        val wantOpaque = model.hasOpaque && (only == null || only == Blend.OPAQUE)
        val wantAlpha = model.hasAlpha && (only == null || only == Blend.ALPHA)
        val wantAdd = model.hasAdd && (only == null || only == Blend.ADD)
        if (!wantOpaque && !wantAlpha && !wantAdd) return
        for (tex in modelTextures(model)) noteTexture(tex)
        val mi = p.models.size
        p.models += model
        fun instance(layer: Blend): Int {
            if ((p.instanceCount + 1) * 22 > p.instances.size) p.instances = p.instances.copyOf(p.instances.size * 2)
            val o = p.instanceCount * 22
            if (xf != null) xf.toMatrix(p.instances, o) else identity.copyInto(p.instances, o)
            if (tint == -1) {
                p.instances[o + 16] = 1f; p.instances[o + 17] = 1f; p.instances[o + 18] = 1f
            } else {
                p.instances[o + 16] = (tint shr 16 and 255) / 255f
                p.instances[o + 17] = (tint shr 8 and 255) / 255f
                p.instances[o + 18] = (tint and 255) / 255f
            }
            p.instances[o + 19] = 1f
            p.instances[o + 20] = emissiveBoost
            p.instances[o + 21] = mi.toFloat()
            return p.instanceCount++
        }
        if (wantOpaque) {
            val idx = instance(Blend.OPAQUE)
            if (opaqueInstanceCount + 1 > opaqueInstances.size) opaqueInstances = opaqueInstances.copyOf(opaqueInstances.size * 2)
            opaqueInstances[opaqueInstanceCount++] = idx
        }
        if (wantAlpha) addOrder(RenderPass.KIND_MODEL, Blend.ALPHA.ordinal, instance(Blend.ALPHA), 0, 0)
        if (wantAdd) addOrder(RenderPass.KIND_MODEL, Blend.ADD.ordinal, instance(Blend.ADD), 0, 0)
        polysDrawn += model.polys.size
    }

    private val modelTex = IdentityHashMap<Model, Array<Texture>>()

    private fun modelTextures(model: Model): Array<Texture> =
        modelTex.getOrPut(model) {
            val seen = IdentityHashMap<Texture, Boolean>()
            for (p in model.polys) seen[p.region.tex] = true
            seen.keys.toTypedArray()
        }

    // ------------------------------------------------------------------ finishing

    /**
     * Packs the recorded frame into a [RenderPass] to show at window pixels ([x], [y], [w], [h]),
     * optionally clipped to ([clipX0], [clipY0])–([clipX1], [clipY1]).
     */
    fun finishFrame(
        x: Int, y: Int, w: Int, h: Int,
        clipX0: Int = 0, clipY0: Int = 0, clipX1: Int = 0, clipY1: Int = 0, clip: Boolean = false,
    ): RenderPass {
        val p = current()
        pass = null
        p.vx = x; p.vy = y; p.vw = w.coerceAtLeast(1); p.vh = h.coerceAtLeast(1)
        p.clip = clip
        p.cx0 = clipX0; p.cy0 = clipY0; p.cx1 = clipX1; p.cy1 = clipY1
        val cam = camera
        val c = p.cam
        c[0] = cam.ex; c[1] = cam.ey; c[2] = cam.ez
        c[3] = cam.rx; c[4] = cam.ry; c[5] = cam.rz
        c[6] = cam.ux; c[7] = cam.uy; c[8] = cam.uz
        c[9] = cam.fx; c[10] = cam.fy; c[11] = cam.fz
        c[12] = cam.focal / width
        c[13] = cam.cx / width
        c[14] = cam.cy / height
        c[15] = cam.focal / height
        p.fogNear = fogUsedNear
        p.fogFar = fogUsedFar
        p.fogFloor = fogUsedFloor
        p.exposure = exposure
        p.bloom = bloom
        packLights(p)

        // Opaque buckets first, then the see-through geometry in order.
        var total = transCount
        for (b in bucketList) total += b.count
        p.vertCount = 0
        p.ensureVerts(total)
        var at = 0
        for (b in bucketList) {
            if (b.count == 0) continue
            System.arraycopy(b.data, 0, p.verts, at * S, b.count * S)
            p.addDraw(RenderPass.KIND_BATCH, Blend.OPAQUE.ordinal, texIndex[b.tex]!!, at, b.count)
            at += b.count
        }
        for (i in 0 until opaqueInstanceCount) p.addDraw(RenderPass.KIND_MODEL, Blend.OPAQUE.ordinal, opaqueInstances[i], 0, 0)
        val transBase = at
        System.arraycopy(trans, 0, p.verts, at * S, transCount * S)
        at += transCount
        for (i in 0 until orderCount) {
            val o = i * 5
            val first = if (order[o] == RenderPass.KIND_BATCH) order[o + 3] + transBase else 0
            p.addDraw(order[o], order[o + 1], order[o + 2], first, order[o + 4])
        }
        p.vertCount = at
        // Forget buckets for textures that weren't used this frame.
        if (bucketList.size > 64) {
            bucketList.removeAll { it.count == 0 }
            buckets.clear()
            for (b in bucketList) buckets[b.tex] = b
        }
        return p
    }

    /** Copies the lights and builds the grid telling each patch of floor which lights reach it. */
    private fun packLights(p: RenderPass) {
        val l = lighting
        p.ambient[0] = l.ambR; p.ambient[1] = l.ambG; p.ambient[2] = l.ambB
        p.dirDir[0] = l.dirX; p.dirDir[1] = l.dirY; p.dirDir[2] = l.dirZ
        p.dirCol[0] = l.dirR; p.dirCol[1] = l.dirG; p.dirCol[2] = l.dirB
        val count = minOf(l.points.size, RenderPass.MAX_LIGHTS)
        p.lightCount = count
        if (count == 0) return
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        for (i in 0 until count) {
            val pl = l.points[i]
            val o = i * 8
            p.lights[o] = pl.x; p.lights[o + 1] = pl.y; p.lights[o + 2] = pl.z; p.lights[o + 3] = pl.radius
            p.lights[o + 4] = pl.r; p.lights[o + 5] = pl.g; p.lights[o + 6] = pl.b; p.lights[o + 7] = pl.intensity
            minX = minOf(minX, pl.x - pl.radius); maxX = maxOf(maxX, pl.x + pl.radius)
            minZ = minOf(minZ, pl.z - pl.radius); maxZ = maxOf(maxZ, pl.z + pl.radius)
        }
        val extent = maxOf(maxX - minX, maxZ - minZ)
        val cell = maxOf(24f, extent / 48f)
        val gw = ceil((maxX - minX) / cell).toInt().coerceIn(1, 64)
        val gh = ceil((maxZ - minZ) / cell).toInt().coerceIn(1, 64)
        p.gridW = gw
        p.gridH = gh
        p.gridX0 = minX
        p.gridZ0 = minZ
        p.gridInvCell = 1f / cell
        val size = gw * 2 * gh * 4
        if (p.grid.size < size) p.grid = ByteArray(size)
        java.util.Arrays.fill(p.grid, 0, size, 0)
        val cellCount = IntArray(gw * gh)
        val cellWeakest = FloatArray(gw * gh)
        for (i in 0 until count) {
            val pl = l.points[i]
            if (pl.intensity <= 0f) continue
            val weight = pl.intensity * pl.radius
            val cx0 = floor((pl.x - pl.radius - minX) / cell).toInt().coerceIn(0, gw - 1)
            val cx1 = floor((pl.x + pl.radius - minX) / cell).toInt().coerceIn(0, gw - 1)
            val cz0 = floor((pl.z - pl.radius - minZ) / cell).toInt().coerceIn(0, gh - 1)
            val cz1 = floor((pl.z + pl.radius - minZ) / cell).toInt().coerceIn(0, gh - 1)
            for (gz in cz0..cz1) for (gx in cx0..cx1) {
                // Skip cells the light's circle doesn't reach.
                val nx = pl.x.coerceIn(minX + gx * cell, minX + (gx + 1) * cell)
                val nz = pl.z.coerceIn(minZ + gz * cell, minZ + (gz + 1) * cell)
                val dx = nx - pl.x
                val dz = nz - pl.z
                if (dx * dx + dz * dz > pl.radius * pl.radius) continue
                val ci = gz * gw + gx
                val k = cellCount[ci]
                val base = (gz * gw * 2 + gx * 2) * 4
                if (k < RenderPass.CELL_LIGHTS) {
                    p.grid[base + k] = (i + 1).toByte()
                    cellCount[ci] = k + 1
                    if (k == 0 || weight < cellWeakest[ci]) cellWeakest[ci] = weight
                } else if (weight > cellWeakest[ci]) {
                    // Replace the weakest light in a crowded cell.
                    var weakest = 0
                    var wv = Float.MAX_VALUE
                    for (j in 0 until RenderPass.CELL_LIGHTS) {
                        val li = (p.grid[base + j].toInt() and 255) - 1
                        val lp = l.points[li]
                        val w = lp.intensity * lp.radius
                        if (w < wv) {
                            wv = w; weakest = j
                        }
                    }
                    p.grid[base + weakest] = (i + 1).toByte()
                    var newWeakest = Float.MAX_VALUE
                    for (j in 0 until RenderPass.CELL_LIGHTS) {
                        val li = (p.grid[base + j].toInt() and 255) - 1
                        val lp = l.points[li]
                        newWeakest = minOf(newWeakest, lp.intensity * lp.radius)
                    }
                    cellWeakest[ci] = newWeakest
                }
            }
        }
    }

    // ------------------------------------------------------------------ convenience shapes

    fun quad(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        region: Region,
        nx: Float, ny: Float, nz: Float,
        u0: Float = 0f, v0: Float = 0f, u1: Float = region.w.toFloat(), v1: Float = region.h.toFloat(),
        blend: Blend = Blend.OPAQUE, emissive: Float = 0f, alpha: Float = 1f, cull: Boolean = true,
        tint: Int = -1, depthBias: Float = 1f, gloss: Float = 0f,
    ) {
        begin(region, blend, emissive, alpha, depthBias, cull, gloss)
        normal(nx, ny, nz)
        tint(tint)
        vertex(ax, ay, az, u0, v0)
        vertex(bx, by, bz, u1, v0)
        vertex(cx, cy, cz, u1, v1)
        vertex(dx, dy, dz, u0, v1)
        end()
    }

    /**
     * A camera-facing sprite standing on ([x], [y], [z]) (bottom centre). [lean] tilts it back
     * toward the camera's up axis so tall sprites don't look squashed from above.
     */
    fun billboard(
        x: Float, y: Float, z: Float, w: Float, h: Float, region: Region,
        flipX: Boolean = false, lean: Float = 0.5f, blend: Blend = Blend.OPAQUE,
        emissive: Float = 0f, alpha: Float = 1f, depthBias: Float = 1.03f, tint: Int = -1,
    ) {
        val cam = camera
        val rx = cam.rx
        val rz = cam.rz
        var upx = cam.ux * lean
        var upy = (1f - lean) + cam.uy * lean
        var upz = cam.uz * lean
        val ul = sqrt(upx * upx + upy * upy + upz * upz).coerceAtLeast(1e-5f)
        upx /= ul; upy /= ul; upz /= ul
        val hw = w / 2f
        val blx = x - rx * hw
        val blz = z - rz * hw
        val brx = x + rx * hw
        val brz = z + rz * hw
        val u0 = if (flipX) region.w.toFloat() else 0f
        val u1 = if (flipX) 0f else region.w.toFloat()
        begin(region, blend, emissive, alpha, depthBias, cull = false)
        normal(-cam.fx, -cam.fy, -cam.fz)
        tint(tint)
        vertex(blx + upx * h, y + upy * h, blz + upz * h, u0, 0f)
        vertex(brx + upx * h, y + upy * h, brz + upz * h, u1, 0f)
        vertex(brx, y, brz, u1, region.h.toFloat())
        vertex(blx, y, blz, u0, region.h.toFloat())
        end()
    }

    /**
     * A sprite centred on ([x], [y], [z]) that faces the camera squarely, turned by [roll]
     * radians in the screen plane (balls, plush toys, sparks).
     */
    fun sprite(
        x: Float, y: Float, z: Float, w: Float, h: Float, region: Region, roll: Float = 0f,
        blend: Blend = Blend.OPAQUE, emissive: Float = 0f, alpha: Float = 1f, depthBias: Float = 1f,
        tint: Int = -1, flipX: Boolean = false,
    ) {
        val cam = camera
        val c = cos(roll)
        val s = sin(roll)
        val hw = w / 2f
        val hh = h / 2f
        val ax = (cam.rx * c + cam.ux * s) * hw
        val ay = (cam.ry * c + cam.uy * s) * hw
        val az = (cam.rz * c + cam.uz * s) * hw
        val bx = (cam.ux * c - cam.rx * s) * hh
        val by = (cam.uy * c - cam.ry * s) * hh
        val bz = (cam.uz * c - cam.rz * s) * hh
        val u0 = if (flipX) region.w.toFloat() else 0f
        val u1 = if (flipX) 0f else region.w.toFloat()
        begin(region, blend, emissive, alpha, depthBias, cull = false)
        normal(-cam.fx, -cam.fy, -cam.fz)
        tint(tint)
        vertex(x - ax + bx, y - ay + by, z - az + bz, u0, 0f)
        vertex(x + ax + bx, y + ay + by, z + az + bz, u1, 0f)
        vertex(x + ax - bx, y + ay - by, z + az - bz, u1, region.h.toFloat())
        vertex(x - ax - bx, y - ay - by, z - az - bz, u0, region.h.toFloat())
        end()
    }

    /**
     * A ribbon [width] wide from one point to another, turned to face the camera (cables,
     * table lines, light beams).
     */
    fun beam(
        x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, width: Float, region: Region,
        blend: Blend = Blend.OPAQUE, emissive: Float = 0f, alpha: Float = 1f, tint: Int = -1,
    ) {
        val cam = camera
        val dx = x1 - x0
        val dy = y1 - y0
        val dz = z1 - z0
        val vx = (x0 + x1) / 2f - cam.ex
        val vy = (y0 + y1) / 2f - cam.ey
        val vz = (z0 + z1) / 2f - cam.ez
        var sx = dy * vz - dz * vy
        var sy = dz * vx - dx * vz
        var sz = dx * vy - dy * vx
        val l = sqrt(sx * sx + sy * sy + sz * sz)
        if (l < 1e-5f) return
        val k = width / 2f / l
        sx *= k; sy *= k; sz *= k
        begin(region, blend, emissive, alpha, 1f, cull = false)
        normal(-cam.fx, -cam.fy, -cam.fz)
        tint(tint)
        vertex(x0 - sx, y0 - sy, z0 - sz, 0f, 0f)
        vertex(x0 + sx, y0 + sy, z0 + sz, region.w.toFloat(), 0f)
        vertex(x1 + sx, y1 + sy, z1 + sz, region.w.toFloat(), region.h.toFloat())
        vertex(x1 - sx, y1 - sy, z1 - sz, 0f, region.h.toFloat())
        end()
    }

    /**
     * A horizontal quad [w] × [d] lying at height [y], centred on ([x], [z]) and turned by
     * [angle] around the vertical axis (coins, pucks, spinning shadows).
     */
    fun flat(
        x: Float, z: Float, y: Float, w: Float, d: Float, region: Region, angle: Float = 0f,
        blend: Blend = Blend.OPAQUE, emissive: Float = 0f, alpha: Float = 1f, tint: Int = -1,
        depthBias: Float = 1f,
    ) {
        val c = cos(angle)
        val s = sin(angle)
        val ax = c * w / 2f
        val az = s * w / 2f
        val bx = -s * d / 2f
        val bz = c * d / 2f
        begin(region, blend, emissive, alpha, depthBias, cull = false)
        normal(0f, 1f, 0f)
        tint(tint)
        vertex(x - ax - bx, y, z - az - bz, 0f, 0f)
        vertex(x + ax - bx, y, z + az - bz, region.w.toFloat(), 0f)
        vertex(x + ax + bx, y, z + az + bz, region.w.toFloat(), region.h.toFloat())
        vertex(x - ax + bx, y, z - az + bz, 0f, region.h.toFloat())
        end()
    }

    /** A horizontal quad lying at height [y] (floor decals, light pools, shadows). */
    fun decal(
        x0: Float, z0: Float, x1: Float, z1: Float, y: Float, region: Region,
        blend: Blend = Blend.ALPHA, emissive: Float = 0f, alpha: Float = 1f, tint: Int = -1,
    ) {
        quad(
            x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, region, 0f, 1f, 0f,
            blend = blend, emissive = emissive, alpha = alpha, cull = false, tint = tint,
        )
    }
}

/** Blends two ARGB colours. */
fun mixArgb(a: Int, b: Int, t: Float): Int {
    val u = t.coerceIn(0f, 1f)
    val ar = a shr 16 and 255; val ag = a shr 8 and 255; val ab = a and 255
    val br = b shr 16 and 255; val bg = b shr 8 and 255; val bb = b and 255
    val r = (ar + (br - ar) * u).toInt()
    val g = (ag + (bg - ag) * u).toInt()
    val bl = (ab + (bb - ab) * u).toInt()
    return (a and -0x1000000) or (r shl 16) or (g shl 8) or bl
}
