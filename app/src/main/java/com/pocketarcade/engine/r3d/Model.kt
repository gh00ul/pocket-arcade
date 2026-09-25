package com.pocketarcade.engine.r3d

import kotlin.math.cos
import kotlin.math.sin

/** One pre-built polygon of a static model. */
class Poly(
    val region: Region,
    val n: Int,
    val xs: FloatArray,
    val ys: FloatArray,
    val zs: FloatArray,
    val us: FloatArray,
    val vs: FloatArray,
    val nx: Float,
    val ny: Float,
    val nz: Float,
    val blend: Blend,
    val emissive: Float,
    val cull: Boolean,
    var tint: Int = -1,
)

/** Static geometry with a bounding box for quick visibility checks. */
class Model(val polys: List<Poly>) {
    val minX: Float = polys.minOfOrNull { p -> p.xs.take(p.n).min() } ?: 0f
    val maxX: Float = polys.maxOfOrNull { p -> p.xs.take(p.n).max() } ?: 0f
    val minY: Float = polys.minOfOrNull { p -> p.ys.take(p.n).min() } ?: 0f
    val maxY: Float = polys.maxOfOrNull { p -> p.ys.take(p.n).max() } ?: 0f
    val minZ: Float = polys.minOfOrNull { p -> p.zs.take(p.n).min() } ?: 0f
    val maxZ: Float = polys.maxOfOrNull { p -> p.zs.take(p.n).max() } ?: 0f

    /**
     * Draws every polygon of the given blend mode (or all when null), optionally placed by [xf].
     * [tint] (ARGB, -1 for none) overrides the polygons' own tints.
     */
    fun draw(r: Renderer3D, only: Blend? = null, emissiveBoost: Float = 1f, xf: Xform? = null, tint: Int = -1) {
        for (p in polys) {
            if (only != null && p.blend != only) continue
            r.begin(p.region, p.blend, p.emissive * emissiveBoost, 1f, 1f, p.cull)
            r.tint(if (tint != -1) tint else p.tint)
            if (xf == null) {
                r.normal(p.nx, p.ny, p.nz)
                for (i in 0 until p.n) r.vertex(p.xs[i], p.ys[i], p.zs[i], p.us[i], p.vs[i])
            } else {
                r.normal(xf.dirX(p.nx, p.ny, p.nz), xf.dirY(p.nx, p.ny, p.nz), xf.dirZ(p.nx, p.ny, p.nz))
                for (i in 0 until p.n) {
                    val x = p.xs[i]
                    val y = p.ys[i]
                    val z = p.zs[i]
                    r.vertex(xf.x(x, y, z), xf.y(x, y, z), xf.z(x, y, z), p.us[i], p.vs[i])
                }
            }
            r.end()
        }
    }
}

/**
 * A rigid placement for drawing a [Model]: uniform scale, then rotation (roll about z, then
 * pitch about x, then yaw about y), then translation. Placements chain with [setProduct] for
 * jointed parts such as the prongs on a swinging claw.
 */
class Xform {
    private val m = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val tmp = FloatArray(9)
    private var tx = 0f
    private var ty = 0f
    private var tz = 0f

    fun set(
        x: Float = 0f, y: Float = 0f, z: Float = 0f,
        yaw: Float = 0f, pitch: Float = 0f, roll: Float = 0f, scale: Float = 1f,
    ): Xform {
        val cy = cos(yaw); val sy = sin(yaw)
        val cp = cos(pitch); val sp = sin(pitch)
        val cr = cos(roll); val sr = sin(roll)
        // Ry · Rx · Rz, times the scale.
        m[0] = (cy * cr + sy * sp * sr) * scale
        m[1] = (-cy * sr + sy * sp * cr) * scale
        m[2] = (sy * cp) * scale
        m[3] = (cp * sr) * scale
        m[4] = (cp * cr) * scale
        m[5] = (-sp) * scale
        m[6] = (-sy * cr + cy * sp * sr) * scale
        m[7] = (sy * sr + cy * sp * cr) * scale
        m[8] = (cy * cp) * scale
        tx = x; ty = y; tz = z
        return this
    }

    /** Makes this [a] ∘ [b]: [b]'s placement, then [a]'s ([b] is a part mounted on [a]). */
    fun setProduct(a: Xform, b: Xform): Xform {
        val am = a.m
        val bm = b.m
        for (i in 0 until 3) for (j in 0 until 3) {
            tmp[i * 3 + j] = am[i * 3] * bm[j] + am[i * 3 + 1] * bm[3 + j] + am[i * 3 + 2] * bm[6 + j]
        }
        val ntx = a.x(b.tx, b.ty, b.tz)
        val nty = a.y(b.tx, b.ty, b.tz)
        val ntz = a.z(b.tx, b.ty, b.tz)
        tmp.copyInto(m)
        tx = ntx; ty = nty; tz = ntz
        return this
    }

    fun x(x: Float, y: Float, z: Float) = m[0] * x + m[1] * y + m[2] * z + tx
    fun y(x: Float, y: Float, z: Float) = m[3] * x + m[4] * y + m[5] * z + ty
    fun z(x: Float, y: Float, z: Float) = m[6] * x + m[7] * y + m[8] * z + tz

    /** Rotates a direction (normals); the scale doesn't matter once the renderer normalises. */
    fun dirX(x: Float, y: Float, z: Float) = m[0] * x + m[1] * y + m[2] * z
    fun dirY(x: Float, y: Float, z: Float) = m[3] * x + m[4] * y + m[5] * z
    fun dirZ(x: Float, y: Float, z: Float) = m[6] * x + m[7] * y + m[8] * z
}

/** Faces of a box; null faces are not generated. The back and bottom are never seen here. */
class BoxFaces(
    val front: Region? = null,
    val left: Region? = null,
    val right: Region? = null,
    val top: Region? = null,
    val back: Region? = null,
    val frontEmissive: Float = 0f,
    val topEmissive: Float = 0f,
    /** Texels per world unit for wrapping regions (their UVs are scaled by face size). */
    val texelsPerUnit: Float = 0f,
)

/** Builds static models out of quads, boxes and prisms. */
class ModelBuilder {
    private val polys = ArrayList<Poly>()

    fun quad(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        dx: Float, dy: Float, dz: Float,
        region: Region, nx: Float, ny: Float, nz: Float,
        u0: Float = 0f, v0: Float = 0f, u1: Float = region.w.toFloat(), v1: Float = region.h.toFloat(),
        blend: Blend = Blend.OPAQUE, emissive: Float = 0f, cull: Boolean = true, tint: Int = -1,
    ): ModelBuilder {
        polys += Poly(
            region, 4,
            floatArrayOf(ax, bx, cx, dx), floatArrayOf(ay, by, cy, dy), floatArrayOf(az, bz, cz, dz),
            floatArrayOf(u0, u1, u1, u0), floatArrayOf(v0, v0, v1, v1),
            nx, ny, nz, blend, emissive, cull, tint,
        )
        return this
    }

    private fun uvFor(r: Region, w: Float, h: Float, tpu: Float): FloatArray =
        if (r.wrap && tpu > 0f) floatArrayOf(w * tpu, h * tpu) else floatArrayOf(r.w.toFloat(), r.h.toFloat())

    /** Axis-aligned box; x0 < x1 (left→right), y0 < y1 (floor→up), z0 < z1 (north→south). */
    fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, f: BoxFaces, tint: Int = -1): ModelBuilder {
        val tpu = f.texelsPerUnit
        f.front?.let { r ->
            val uv = uvFor(r, x1 - x0, y1 - y0, tpu)
            quad(x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, r, 0f, 0f, 1f, u1 = uv[0], v1 = uv[1], emissive = f.frontEmissive, tint = tint)
        }
        f.back?.let { r ->
            val uv = uvFor(r, x1 - x0, y1 - y0, tpu)
            quad(x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, r, 0f, 0f, -1f, u1 = uv[0], v1 = uv[1], tint = tint)
        }
        f.left?.let { r ->
            val uv = uvFor(r, z1 - z0, y1 - y0, tpu)
            quad(x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, r, -1f, 0f, 0f, u1 = uv[0], v1 = uv[1], tint = tint)
        }
        f.right?.let { r ->
            val uv = uvFor(r, z1 - z0, y1 - y0, tpu)
            quad(x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, r, 1f, 0f, 0f, u1 = uv[0], v1 = uv[1], tint = tint)
        }
        f.top?.let { r ->
            val uv = uvFor(r, x1 - x0, z1 - z0, tpu)
            quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, 0f, 1f, 0f, u1 = uv[0], v1 = uv[1], emissive = f.topEmissive, tint = tint)
        }
        return this
    }

    /**
     * A vertical prism approximating a cylinder, with optional lids. With [inward] the walls
     * face the axis (the inside of a well or cup) instead of outwards.
     */
    fun cylinder(
        cx: Float, cz: Float, y0: Float, y1: Float, radius: Float, sides: Int,
        side: Region, top: Region? = null, emissive: Float = 0f, tint: Int = -1,
        bottom: Region? = null, inward: Boolean = false, topRadius: Float = radius,
    ): ModelBuilder {
        val step = (Math.PI * 2 / sides).toFloat()
        val flip = if (inward) -1f else 1f
        for (i in 0 until sides) {
            val a0 = i * step
            val a1 = (i + 1) * step
            val am = (a0 + a1) / 2f
            val u0 = side.w * i / sides.toFloat()
            val u1 = side.w * (i + 1) / sides.toFloat()
            quad(
                cx + cos(a1) * topRadius, y1, cz + sin(a1) * topRadius,
                cx + cos(a0) * topRadius, y1, cz + sin(a0) * topRadius,
                cx + cos(a0) * radius, y0, cz + sin(a0) * radius,
                cx + cos(a1) * radius, y0, cz + sin(a1) * radius,
                side, cos(am) * flip, 0f, sin(am) * flip, u0 = u1, u1 = u0, emissive = emissive, tint = tint,
            )
        }
        top?.let { disc(cx, cz, y1, topRadius, sides, it, 1f, emissive, tint) }
        bottom?.let { disc(cx, cz, y0, radius, sides, it, -1f, emissive, tint) }
        return this
    }

    /** A flat regular polygon facing up ([ny] = 1) or down (-1), fanned into ≤ 8-gons. */
    fun disc(cx: Float, cz: Float, y: Float, radius: Float, sides: Int, t: Region, ny: Float = 1f, emissive: Float = 0f, tint: Int = -1): ModelBuilder {
        val step = (Math.PI * 2 / sides).toFloat()
        val xs = FloatArray(sides)
        val zs = FloatArray(sides)
        val us = FloatArray(sides)
        val vs = FloatArray(sides)
        for (i in 0 until sides) {
            val a = i * step
            xs[i] = cx + cos(a) * radius
            zs[i] = cz + sin(a) * radius
            us[i] = t.w / 2f + cos(a) * t.w / 2f
            vs[i] = t.h / 2f + sin(a) * t.h / 2f
        }
        var start = 1
        while (start < sides - 1) {
            val count = minOf(7, sides - start)
            val idx = IntArray(count + 1)
            idx[0] = 0
            for (k in 0 until count) idx[k + 1] = start + k
            polys += Poly(
                t, idx.size,
                FloatArray(idx.size) { xs[idx[it]] }, FloatArray(idx.size) { y },
                FloatArray(idx.size) { zs[idx[it]] }, FloatArray(idx.size) { us[idx[it]] },
                FloatArray(idx.size) { vs[idx[it]] },
                0f, ny, 0f, Blend.OPAQUE, emissive, true, tint,
            )
            start += count - 1
        }
        return this
    }

    /** A flat ring (annulus) at height [y], facing up, in [sides] quads. */
    fun annulus(cx: Float, cz: Float, y: Float, rIn: Float, rOut: Float, sides: Int, t: Region, tint: Int = -1): ModelBuilder {
        val step = (Math.PI * 2 / sides).toFloat()
        for (i in 0 until sides) {
            val a0 = i * step
            val a1 = (i + 1) * step
            quad(
                cx + cos(a0) * rOut, y, cz + sin(a0) * rOut, cx + cos(a1) * rOut, y, cz + sin(a1) * rOut,
                cx + cos(a1) * rIn, y, cz + sin(a1) * rIn, cx + cos(a0) * rIn, y, cz + sin(a0) * rIn,
                t, 0f, 1f, 0f, u0 = t.w * i / sides.toFloat(), u1 = t.w * (i + 1) / sides.toFloat(), tint = tint,
            )
        }
        return this
    }

    fun add(model: Model): ModelBuilder {
        polys += model.polys
        return this
    }

    fun build() = Model(ArrayList(polys))
}
