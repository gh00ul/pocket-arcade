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

    /** Draws every polygon of the given blend mode (or all when null). */
    fun draw(r: Renderer3D, only: Blend? = null, emissiveBoost: Float = 1f) {
        for (p in polys) {
            if (only != null && p.blend != only) continue
            r.begin(p.region, p.blend, p.emissive * emissiveBoost, 1f, 1f, p.cull)
            r.normal(p.nx, p.ny, p.nz)
            r.tint(p.tint)
            for (i in 0 until p.n) r.vertex(p.xs[i], p.ys[i], p.zs[i], p.us[i], p.vs[i])
            r.end()
        }
    }
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

    /** A vertical prism approximating a cylinder, with an optional lid. */
    fun cylinder(
        cx: Float, cz: Float, y0: Float, y1: Float, radius: Float, sides: Int,
        side: Region, top: Region? = null, emissive: Float = 0f, tint: Int = -1,
    ): ModelBuilder {
        val step = (Math.PI * 2 / sides).toFloat()
        for (i in 0 until sides) {
            val a0 = i * step
            val a1 = (i + 1) * step
            val xa = cx + cos(a0) * radius
            val za = cz + sin(a0) * radius
            val xb = cx + cos(a1) * radius
            val zb = cz + sin(a1) * radius
            val am = (a0 + a1) / 2f
            val u0 = side.w * i / sides.toFloat()
            val u1 = side.w * (i + 1) / sides.toFloat()
            quad(xb, y1, zb, xa, y1, za, xa, y0, za, xb, y0, zb, side, cos(am), 0f, sin(am), u0 = u1, u1 = u0, emissive = emissive, tint = tint)
        }
        top?.let { t ->
            val xs = FloatArray(sides)
            val ys = FloatArray(sides) { y1 }
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
            // Fan into polygons of at most 8 vertices.
            var start = 1
            while (start < sides - 1) {
                val count = minOf(7, sides - start)
                val idx = IntArray(count + 1)
                idx[0] = 0
                for (k in 0 until count) idx[k + 1] = start + k
                polys += Poly(
                    t, idx.size,
                    FloatArray(idx.size) { xs[idx[it]] }, FloatArray(idx.size) { ys[idx[it]] },
                    FloatArray(idx.size) { zs[idx[it]] }, FloatArray(idx.size) { us[idx[it]] },
                    FloatArray(idx.size) { vs[idx[it]] },
                    0f, 1f, 0f, Blend.OPAQUE, emissive, true, tint,
                )
                start += count - 1
            }
        }
        return this
    }

    fun add(model: Model): ModelBuilder {
        polys += model.polys
        return this
    }

    fun build() = Model(ArrayList(polys))
}
