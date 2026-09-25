package com.pocketarcade.engine.r3d

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One pre-built polygon of a static model. [nx], [ny], [nz] is the face normal; smooth surfaces
 * also carry per-vertex normals in [vnx], [vny], [vnz]. [gloss] (0..1) adds specular highlights.
 */
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
    val tint: Int = -1,
    val gloss: Float = 0f,
    val vnx: FloatArray? = null,
    val vny: FloatArray? = null,
    val vnz: FloatArray? = null,
)

/**
 * Static geometry. The GPU keeps its own copy, so drawing a model many times per frame (with
 * different placements) is cheap.
 */
class Model(val polys: List<Poly>) {
    val minX: Float = polys.minOfOrNull { p -> p.xs.take(p.n).min() } ?: 0f
    val maxX: Float = polys.maxOfOrNull { p -> p.xs.take(p.n).max() } ?: 0f
    val minY: Float = polys.minOfOrNull { p -> p.ys.take(p.n).min() } ?: 0f
    val maxY: Float = polys.maxOfOrNull { p -> p.ys.take(p.n).max() } ?: 0f
    val minZ: Float = polys.minOfOrNull { p -> p.zs.take(p.n).min() } ?: 0f
    val maxZ: Float = polys.maxOfOrNull { p -> p.zs.take(p.n).max() } ?: 0f

    /** Which blend modes this model contains, so draw calls for absent layers can be skipped. */
    val hasOpaque = polys.any { it.blend == Blend.OPAQUE }
    val hasAlpha = polys.any { it.blend == Blend.ALPHA }
    val hasAdd = polys.any { it.blend == Blend.ADD }

    // Owned by the GL thread.
    @Volatile internal var glMesh: Any? = null
    internal var glGen = -1

    /**
     * Draws every polygon of the given blend mode (or all when null), optionally placed by [xf].
     * [tint] (ARGB, -1 for none) multiplies the polygons' own colours.
     */
    fun draw(r: Renderer3D, only: Blend? = null, emissiveBoost: Float = 1f, xf: Xform? = null, tint: Int = -1) {
        r.drawModel(this, only, emissiveBoost, xf, tint)
    }
}

/**
 * A rigid placement for drawing a [Model]: uniform scale, then rotation (roll about z, then
 * pitch about x, then yaw about y), then translation. Placements chain with [setProduct] for
 * jointed parts such as the prongs on a swinging claw or a walking figure's limbs.
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

    /** Stretches the placed thing along its own axes (squash and stretch). */
    fun stretch(sx: Float, sy: Float, sz: Float): Xform {
        m[0] *= sx; m[3] *= sx; m[6] *= sx
        m[1] *= sy; m[4] *= sy; m[7] *= sy
        m[2] *= sz; m[5] *= sz; m[8] *= sz
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

    fun copyFrom(o: Xform): Xform {
        o.m.copyInto(m)
        tx = o.tx; ty = o.ty; tz = o.tz
        return this
    }

    fun x(x: Float, y: Float, z: Float) = m[0] * x + m[1] * y + m[2] * z + tx
    fun y(x: Float, y: Float, z: Float) = m[3] * x + m[4] * y + m[5] * z + ty
    fun z(x: Float, y: Float, z: Float) = m[6] * x + m[7] * y + m[8] * z + tz

    /** Rotates a direction (normals); the scale doesn't matter once the renderer normalises. */
    fun dirX(x: Float, y: Float, z: Float) = m[0] * x + m[1] * y + m[2] * z
    fun dirY(x: Float, y: Float, z: Float) = m[3] * x + m[4] * y + m[5] * z
    fun dirZ(x: Float, y: Float, z: Float) = m[6] * x + m[7] * y + m[8] * z

    /** Writes the placement as a column-major 4×4 matrix starting at [out][[at]]. */
    fun toMatrix(out: FloatArray, at: Int = 0) {
        out[at] = m[0]; out[at + 1] = m[3]; out[at + 2] = m[6]; out[at + 3] = 0f
        out[at + 4] = m[1]; out[at + 5] = m[4]; out[at + 6] = m[7]; out[at + 7] = 0f
        out[at + 8] = m[2]; out[at + 9] = m[5]; out[at + 10] = m[8]; out[at + 11] = 0f
        out[at + 12] = tx; out[at + 13] = ty; out[at + 14] = tz; out[at + 15] = 1f
    }
}

/** Faces of a box; null faces are not generated. The bottom is never seen. */
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
    val gloss: Float = 0f,
) {
    companion object {
        /** The same region on every visible face. */
        fun all(r: Region, gloss: Float = 0f) = BoxFaces(front = r, left = r, right = r, top = r, back = r, gloss = gloss)
    }
}

/** Builds static models out of quads, boxes, prisms, spheres and lathed shapes. */
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
        gloss: Float = 0f,
    ): ModelBuilder {
        polys += Poly(
            region, 4,
            floatArrayOf(ax, bx, cx, dx), floatArrayOf(ay, by, cy, dy), floatArrayOf(az, bz, cz, dz),
            floatArrayOf(u0, u1, u1, u0), floatArrayOf(v0, v0, v1, v1),
            nx, ny, nz, blend, emissive, cull, tint, gloss,
        )
        return this
    }

    /** Any convex polygon of up to 8 vertices, flat shaded. */
    fun poly(
        xs: FloatArray, ys: FloatArray, zs: FloatArray, us: FloatArray, vs: FloatArray, region: Region,
        nx: Float, ny: Float, nz: Float, blend: Blend = Blend.OPAQUE, emissive: Float = 0f,
        cull: Boolean = true, tint: Int = -1, gloss: Float = 0f,
    ): ModelBuilder {
        polys += Poly(region, xs.size, xs, ys, zs, us, vs, nx, ny, nz, blend, emissive, cull, tint, gloss)
        return this
    }

    private fun uvFor(r: Region, w: Float, h: Float, tpu: Float): FloatArray =
        if (r.wrap && tpu > 0f) floatArrayOf(w * tpu, h * tpu) else floatArrayOf(r.w.toFloat(), r.h.toFloat())

    /** Axis-aligned box; x0 < x1 (left→right), y0 < y1 (floor→up), z0 < z1 (north→south). */
    fun box(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, f: BoxFaces, tint: Int = -1): ModelBuilder {
        val tpu = f.texelsPerUnit
        val g = f.gloss
        f.front?.let { r ->
            val uv = uvFor(r, x1 - x0, y1 - y0, tpu)
            quad(x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, r, 0f, 0f, 1f, u1 = uv[0], v1 = uv[1], emissive = f.frontEmissive, tint = tint, gloss = g)
        }
        f.back?.let { r ->
            val uv = uvFor(r, x1 - x0, y1 - y0, tpu)
            quad(x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, r, 0f, 0f, -1f, u1 = uv[0], v1 = uv[1], tint = tint, gloss = g)
        }
        f.left?.let { r ->
            val uv = uvFor(r, z1 - z0, y1 - y0, tpu)
            quad(x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, r, -1f, 0f, 0f, u1 = uv[0], v1 = uv[1], tint = tint, gloss = g)
        }
        f.right?.let { r ->
            val uv = uvFor(r, z1 - z0, y1 - y0, tpu)
            quad(x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, r, 1f, 0f, 0f, u1 = uv[0], v1 = uv[1], tint = tint, gloss = g)
        }
        f.top?.let { r ->
            val uv = uvFor(r, x1 - x0, z1 - z0, tpu)
            quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, 0f, 1f, 0f, u1 = uv[0], v1 = uv[1], emissive = f.topEmissive, tint = tint, gloss = g)
        }
        return this
    }

    /**
     * A vertical prism approximating a cylinder, with optional lids. With [inward] the walls
     * face the axis (the inside of a well or cup) instead of outwards. With [smooth] the walls
     * are shaded as a round surface rather than facets.
     */
    fun cylinder(
        cx: Float, cz: Float, y0: Float, y1: Float, radius: Float, sides: Int,
        side: Region, top: Region? = null, emissive: Float = 0f, tint: Int = -1,
        bottom: Region? = null, inward: Boolean = false, topRadius: Float = radius,
        smooth: Boolean = true, gloss: Float = 0f,
    ): ModelBuilder {
        val step = (PI * 2 / sides).toFloat()
        val flip = if (inward) -1f else 1f
        for (i in 0 until sides) {
            val a0 = i * step
            val a1 = (i + 1) * step
            val am = (a0 + a1) / 2f
            val u0 = side.w * i / sides.toFloat()
            val u1 = side.w * (i + 1) / sides.toFloat()
            val xs = floatArrayOf(cx + cos(a1) * topRadius, cx + cos(a0) * topRadius, cx + cos(a0) * radius, cx + cos(a1) * radius)
            val ys = floatArrayOf(y1, y1, y0, y0)
            val zs = floatArrayOf(cz + sin(a1) * topRadius, cz + sin(a0) * topRadius, cz + sin(a0) * radius, cz + sin(a1) * radius)
            val us = floatArrayOf(u0, u1, u1, u0)
            val vs = floatArrayOf(0f, 0f, side.h.toFloat(), side.h.toFloat())
            if (smooth) {
                // Tilt the normals for cones so light falls correctly on tapered sides.
                val slope = (radius - topRadius) / (y1 - y0).coerceAtLeast(1e-3f)
                val ny = slope * flip
                val nl = sqrt(1f + ny * ny)
                polys += Poly(
                    side, 4, xs, ys, zs, us, vs, cos(am) * flip, 0f, sin(am) * flip, Blend.OPAQUE, emissive, true, tint, gloss,
                    floatArrayOf(cos(a1) * flip / nl, cos(a0) * flip / nl, cos(a0) * flip / nl, cos(a1) * flip / nl),
                    floatArrayOf(ny / nl, ny / nl, ny / nl, ny / nl),
                    floatArrayOf(sin(a1) * flip / nl, sin(a0) * flip / nl, sin(a0) * flip / nl, sin(a1) * flip / nl),
                )
            } else {
                polys += Poly(side, 4, xs, ys, zs, us, vs, cos(am) * flip, 0f, sin(am) * flip, Blend.OPAQUE, emissive, true, tint, gloss)
            }
        }
        top?.let { disc(cx, cz, y1, topRadius, sides, it, 1f, emissive, tint, gloss) }
        bottom?.let { disc(cx, cz, y0, radius, sides, it, -1f, emissive, tint, gloss) }
        return this
    }

    /** A flat regular polygon facing up ([ny] = 1) or down (-1), fanned into ≤ 8-gons. */
    fun disc(
        cx: Float, cz: Float, y: Float, radius: Float, sides: Int, t: Region, ny: Float = 1f,
        emissive: Float = 0f, tint: Int = -1, gloss: Float = 0f, blend: Blend = Blend.OPAQUE,
    ): ModelBuilder {
        val step = (PI * 2 / sides).toFloat()
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
                0f, ny, 0f, blend, emissive, true, tint, gloss,
            )
            start += count - 1
        }
        return this
    }

    /** A flat ring (annulus) at height [y], facing up, in [sides] quads. */
    fun annulus(cx: Float, cz: Float, y: Float, rIn: Float, rOut: Float, sides: Int, t: Region, tint: Int = -1, gloss: Float = 0f, emissive: Float = 0f): ModelBuilder {
        val step = (PI * 2 / sides).toFloat()
        for (i in 0 until sides) {
            val a0 = i * step
            val a1 = (i + 1) * step
            quad(
                cx + cos(a0) * rOut, y, cz + sin(a0) * rOut, cx + cos(a1) * rOut, y, cz + sin(a1) * rOut,
                cx + cos(a1) * rIn, y, cz + sin(a1) * rIn, cx + cos(a0) * rIn, y, cz + sin(a0) * rIn,
                t, 0f, 1f, 0f, u0 = t.w * i / sides.toFloat(), u1 = t.w * (i + 1) / sides.toFloat(), tint = tint, gloss = gloss, emissive = emissive,
            )
        }
        return this
    }

    /**
     * A surface of revolution around the vertical axis through ([cx], [cz]): [profile] lists
     * (radius, height) pairs from bottom to top. Smooth shaded; the texture wraps around once
     * horizontally and runs bottom→top vertically. Great for pots, lamps, stools and bodies.
     */
    fun lathe(
        cx: Float, cy: Float, cz: Float, profile: FloatArray, sides: Int, t: Region,
        tint: Int = -1, gloss: Float = 0f, emissive: Float = 0f, cull: Boolean = true,
    ): ModelBuilder {
        val rings = profile.size / 2
        if (rings < 2) return this
        // Profile normals from the neighbouring segments.
        val pnr = FloatArray(rings)
        val pny = FloatArray(rings)
        for (j in 0 until rings) {
            val a = (j - 1).coerceAtLeast(0)
            val b = (j + 1).coerceAtMost(rings - 1)
            val dr = profile[b * 2] - profile[a * 2]
            val dy = profile[b * 2 + 1] - profile[a * 2 + 1]
            // Outward normal of the (r, y) curve: rotate the tangent by -90°.
            var nr = dy
            var ny = -dr
            val l = sqrt(nr * nr + ny * ny).coerceAtLeast(1e-5f)
            nr /= l; ny /= l
            pnr[j] = nr; pny[j] = ny
        }
        val step = (PI * 2 / sides).toFloat()
        val totalH = (profile[(rings - 1) * 2 + 1] - profile[1]).let { if (it == 0f) 1f else it }
        for (j in 0 until rings - 1) {
            val r0 = profile[j * 2]; val y0 = profile[j * 2 + 1]
            val r1 = profile[j * 2 + 2]; val y1 = profile[j * 2 + 3]
            val v0 = t.h * (1f - (y0 - profile[1]) / totalH)
            val v1 = t.h * (1f - (y1 - profile[1]) / totalH)
            for (i in 0 until sides) {
                val a0 = i * step
                val a1 = (i + 1) * step
                val c0 = cos(a0); val s0 = sin(a0)
                val c1 = cos(a1); val s1 = sin(a1)
                val xs = floatArrayOf(cx + c1 * r1, cx + c0 * r1, cx + c0 * r0, cx + c1 * r0)
                val ys = floatArrayOf(cy + y1, cy + y1, cy + y0, cy + y0)
                val zs = floatArrayOf(cz + s1 * r1, cz + s0 * r1, cz + s0 * r0, cz + s1 * r0)
                val u0 = t.w * i / sides.toFloat()
                val u1 = t.w * (i + 1) / sides.toFloat()
                val am = (a0 + a1) / 2f
                val fnr = (pnr[j] + pnr[j + 1]) / 2f
                val fny = (pny[j] + pny[j + 1]) / 2f
                polys += Poly(
                    t, 4, xs, ys, zs, floatArrayOf(u1, u0, u0, u1), floatArrayOf(v1, v1, v0, v0),
                    cos(am) * fnr, fny, sin(am) * fnr, Blend.OPAQUE, emissive, cull, tint, gloss,
                    floatArrayOf(c1 * pnr[j + 1], c0 * pnr[j + 1], c0 * pnr[j], c1 * pnr[j]),
                    floatArrayOf(pny[j + 1], pny[j + 1], pny[j], pny[j]),
                    floatArrayOf(s1 * pnr[j + 1], s0 * pnr[j + 1], s0 * pnr[j], s1 * pnr[j]),
                )
            }
        }
        return this
    }

    /** A UV sphere (or ellipsoid with [sy] ≠ 1) centred on ([cx], [cy], [cz]). */
    fun sphere(
        cx: Float, cy: Float, cz: Float, radius: Float, t: Region,
        slices: Int = 16, stacks: Int = 10, sy: Float = 1f, tint: Int = -1, gloss: Float = 0f, emissive: Float = 0f,
        yFrom: Float = -1f, yTo: Float = 1f,
    ): ModelBuilder {
        // Latitude bands between yFrom and yTo (as sine of latitude) so domes and caps are easy.
        val lat0 = kotlin.math.asin(yFrom.coerceIn(-1f, 1f))
        val lat1 = kotlin.math.asin(yTo.coerceIn(-1f, 1f))
        val profile = FloatArray((stacks + 1) * 2)
        for (j in 0..stacks) {
            val lat = lat0 + (lat1 - lat0) * j / stacks
            profile[j * 2] = cos(lat) * radius
            profile[j * 2 + 1] = sin(lat) * radius * sy
        }
        // Make the poles meet exactly.
        if (yFrom <= -1f) profile[0] = 0f
        if (yTo >= 1f) profile[stacks * 2] = 0f
        lathe(cx, cy, cz, profile, slices, t, tint, gloss, emissive)
        return this
    }

    /**
     * A capsule (rounded rod) from ([ax], [ay], [az]) to ([bx], [by], [bz]) — arms, legs and
     * handles. Built along +y and rotated into place.
     */
    fun capsule(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, radius: Float, t: Region,
        slices: Int = 10, tint: Int = -1, gloss: Float = 0f,
    ): ModelBuilder {
        val dx = bx - ax
        val dy = by - ay
        val dz = bz - az
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        val caps = 3
        val profile = ArrayList<Float>()
        for (j in 0..caps) {
            val lat = -PI.toFloat() / 2f + (PI.toFloat() / 2f) * j / caps
            profile += cos(lat) * radius; profile += sin(lat) * radius
        }
        for (j in 0..caps) {
            val lat = (PI.toFloat() / 2f) * j / caps
            profile += cos(lat) * radius; profile += len + sin(lat) * radius
        }
        profile[0] = 0f
        profile[profile.size - 2] = 0f
        val local = ModelBuilder().lathe(0f, 0f, 0f, profile.toFloatArray(), slices, t, tint, gloss).build()
        // Rotate +y onto the rod's direction.
        val yaw = atan2(dx, dz)
        val pitch = atan2(sqrt(dx * dx + dz * dz), dy)
        val xf = Xform().set(ax, ay, az, yaw = yaw, pitch = pitch)
        add(local, xf)
        return this
    }

    /**
     * A torus (ring) lying flat around the vertical axis through ([cx], [cy], [cz]): [radius] to
     * the tube's centre, [tube] the tube's radius. Rotate it into place with [add] and an [Xform].
     */
    fun torus(
        cx: Float, cy: Float, cz: Float, radius: Float, tube: Float, t: Region,
        segments: Int = 20, sides: Int = 8, tint: Int = -1, gloss: Float = 0f, emissive: Float = 0f,
    ): ModelBuilder {
        val profile = FloatArray((sides + 1) * 2)
        // The tube's cross-section, walked bottom → outside → top → inside.
        for (j in 0..sides) {
            val a = -PI.toFloat() / 2f + j * 2f * PI.toFloat() / sides
            profile[j * 2] = radius + cos(a) * tube
            profile[j * 2 + 1] = sin(a) * tube
        }
        lathe(cx, cy, cz, profile, segments, t, tint, gloss, emissive, cull = true)
        return this
    }

    fun add(model: Model): ModelBuilder {
        polys += model.polys
        return this
    }

    /**
     * Adds a copy of [model] stretched by ([sx], [sy], [sz]) about the origin, then moved by
     * ([tx], [ty], [tz]). Normals are corrected for the stretch.
     */
    fun addScaled(model: Model, sx: Float, sy: Float, sz: Float, tx: Float = 0f, ty: Float = 0f, tz: Float = 0f): ModelBuilder {
        fun n(x: Float, y: Float, z: Float, out: FloatArray) {
            val nx = x / sx; val ny = y / sy; val nz = z / sz
            val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
            out[0] = nx / l; out[1] = ny / l; out[2] = nz / l
        }
        val tmp = FloatArray(3)
        for (p in model.polys) {
            val xs = FloatArray(p.n) { p.xs[it] * sx + tx }
            val ys = FloatArray(p.n) { p.ys[it] * sy + ty }
            val zs = FloatArray(p.n) { p.zs[it] * sz + tz }
            n(p.nx, p.ny, p.nz, tmp)
            val fx = tmp[0]; val fy = tmp[1]; val fz = tmp[2]
            var vnx: FloatArray? = null
            var vny: FloatArray? = null
            var vnz: FloatArray? = null
            if (p.vnx != null && p.vny != null && p.vnz != null) {
                vnx = FloatArray(p.n); vny = FloatArray(p.n); vnz = FloatArray(p.n)
                for (i in 0 until p.n) {
                    n(p.vnx[i], p.vny[i], p.vnz[i], tmp)
                    vnx[i] = tmp[0]; vny[i] = tmp[1]; vnz[i] = tmp[2]
                }
            }
            polys += Poly(p.region, p.n, xs, ys, zs, p.us, p.vs, fx, fy, fz, p.blend, p.emissive, p.cull, p.tint, p.gloss, vnx, vny, vnz)
        }
        return this
    }

    /** Adds a copy of [model] moved by [xf]. */
    fun add(model: Model, xf: Xform): ModelBuilder {
        for (p in model.polys) {
            val xs = FloatArray(p.n) { xf.x(p.xs[it], p.ys[it], p.zs[it]) }
            val ys = FloatArray(p.n) { xf.y(p.xs[it], p.ys[it], p.zs[it]) }
            val zs = FloatArray(p.n) { xf.z(p.xs[it], p.ys[it], p.zs[it]) }
            fun norm(x: Float, y: Float, z: Float, out: FloatArray) {
                val nx = xf.dirX(x, y, z); val ny = xf.dirY(x, y, z); val nz = xf.dirZ(x, y, z)
                val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
                out[0] = nx / l; out[1] = ny / l; out[2] = nz / l
            }
            val tmp = FloatArray(3)
            norm(p.nx, p.ny, p.nz, tmp)
            val fx = tmp[0]; val fy = tmp[1]; val fz = tmp[2]
            var vnx: FloatArray? = null
            var vny: FloatArray? = null
            var vnz: FloatArray? = null
            if (p.vnx != null && p.vny != null && p.vnz != null) {
                vnx = FloatArray(p.n); vny = FloatArray(p.n); vnz = FloatArray(p.n)
                for (i in 0 until p.n) {
                    norm(p.vnx[i], p.vny[i], p.vnz[i], tmp)
                    vnx[i] = tmp[0]; vny[i] = tmp[1]; vnz[i] = tmp[2]
                }
            }
            polys += Poly(p.region, p.n, xs, ys, zs, p.us, p.vs, fx, fy, fz, p.blend, p.emissive, p.cull, p.tint, p.gloss, vnx, vny, vnz)
        }
        return this
    }

    fun build() = Model(ArrayList(polys))
}
