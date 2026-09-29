package com.pocketarcade.engine.r3d

import kotlin.math.sqrt

/**
 * A camera's view volume cut off at a draw distance, for culling: the four side planes through
 * the eye (the image edges) and a far plane. [footprint] finds the patch of floor the volume
 * covers between two heights, which is right for any camera — looking down on the hall or
 * straight along it at eye level. Allocation-free once built.
 */
class Frustum {
    /**
     * Planes as (nx, ny, nz, d); a point p is inside when n · p + d ≥ 0. Left, right, top,
     * bottom, far, and near (the sides alone would let boxes just behind the eye through).
     */
    private val planes = FloatArray(PLANES * 4)

    /** The eye and the four far corners, in world space: the corners of the (pointed) volume. */
    private val px = FloatArray(5)
    private val py = FloatArray(5)
    private val pz = FloatArray(5)

    /** The draw distance (view depth) the volume was cut at. */
    var far = 1f
        private set

    fun set(cam: Camera3D, far: Float) {
        this.far = far
        val f = cam.focal
        val right = (cam.imageW - cam.cx) / f
        val left = cam.cx / f
        val top = cam.cy / f
        val bottom = (cam.imageH - cam.cy) / f
        // Inward normals in view space (x right, y up, z forward), taken to world space.
        plane(0, 1f, 0f, left, cam)
        plane(1, -1f, 0f, right, cam)
        plane(2, 0f, -1f, top, cam)
        plane(3, 0f, 1f, bottom, cam)
        val o = 16
        planes[o] = -cam.fx
        planes[o + 1] = -cam.fy
        planes[o + 2] = -cam.fz
        planes[o + 3] = cam.fx * cam.ex + cam.fy * cam.ey + cam.fz * cam.ez + far
        planes[o + 4] = cam.fx
        planes[o + 5] = cam.fy
        planes[o + 6] = cam.fz
        planes[o + 7] = -(cam.fx * cam.ex + cam.fy * cam.ey + cam.fz * cam.ez) - cam.near
        px[0] = cam.ex; py[0] = cam.ey; pz[0] = cam.ez
        for (k in 0 until 4) {
            val sx = if (k and 1 == 0) -left else right
            val sy = if (k < 2) top else -bottom
            px[k + 1] = cam.ex + (cam.fx + cam.rx * sx + cam.ux * sy) * far
            py[k + 1] = cam.ey + (cam.fy + cam.ry * sx + cam.uy * sy) * far
            pz[k + 1] = cam.ez + (cam.fz + cam.rz * sx + cam.uz * sy) * far
        }
    }

    private fun plane(i: Int, ax: Float, ay: Float, az: Float, cam: Camera3D) {
        val l = sqrt(ax * ax + ay * ay + az * az)
        val nx = (cam.rx * ax + cam.ux * ay + cam.fx * az) / l
        val ny = (cam.ry * ax + cam.uy * ay + cam.fy * az) / l
        val nz = (cam.rz * ax + cam.uz * ay + cam.fz * az) / l
        val o = i * 4
        planes[o] = nx
        planes[o + 1] = ny
        planes[o + 2] = nz
        planes[o + 3] = -(nx * cam.ex + ny * cam.ey + nz * cam.ez)
    }

    /** Whether a sphere reaches the volume (conservative: true near corners it only grazes). */
    fun sphereVisible(x: Float, y: Float, z: Float, r: Float): Boolean {
        for (i in 0 until PLANES) {
            val o = i * 4
            if (planes[o] * x + planes[o + 1] * y + planes[o + 2] * z + planes[o + 3] < -r) return false
        }
        return true
    }

    /** Whether an axis-aligned box reaches the volume (conservative, like [sphereVisible]). */
    fun boxVisible(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float): Boolean {
        for (i in 0 until PLANES) {
            val o = i * 4
            val nx = planes[o]
            val ny = planes[o + 1]
            val nz = planes[o + 2]
            // The corner furthest along the normal: if even that is outside, all of it is.
            val cx = if (nx >= 0f) x1 else x0
            val cy = if (ny >= 0f) y1 else y0
            val cz = if (nz >= 0f) z1 else z0
            if (nx * cx + ny * cy + nz * cz + planes[o + 3] < 0f) return false
        }
        return true
    }

    /**
     * The floor rectangle (x, z bounds) covering everything in the volume between heights
     * [yMin] and [yMax], into [out] as minX, maxX, minZ, maxZ. False if the volume misses that
     * slab entirely (out is left alone).
     */
    fun footprint(yMin: Float, yMax: Float, out: FloatArray): Boolean {
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        var any = false
        // The clipped volume's corners: its own corners inside the slab, and where its edges
        // cross the slab's top and bottom.
        for (i in 0 until 5) {
            if (py[i] in yMin..yMax) {
                minX = minOf(minX, px[i]); maxX = maxOf(maxX, px[i])
                minZ = minOf(minZ, pz[i]); maxZ = maxOf(maxZ, pz[i])
                any = true
            }
        }
        for (e in 0 until 8) {
            val a: Int
            val b: Int
            if (e < 4) {
                a = 0; b = e + 1
            } else {
                // The far rectangle's rim: corners 1-2, 2-4, 4-3, 3-1.
                val k = e - 4
                a = RIM[k]; b = RIM[(k + 1) % 4]
            }
            for (s in 0 until 2) {
                val h = if (s == 0) yMin else yMax
                val ya = py[a] - h
                val yb = py[b] - h
                if ((ya < 0f) == (yb < 0f) || ya == yb) continue
                val t = ya / (ya - yb)
                val x = px[a] + (px[b] - px[a]) * t
                val z = pz[a] + (pz[b] - pz[a]) * t
                minX = minOf(minX, x); maxX = maxOf(maxX, x)
                minZ = minOf(minZ, z); maxZ = maxOf(maxZ, z)
                any = true
            }
        }
        if (!any) return false
        out[0] = minX; out[1] = maxX; out[2] = minZ; out[3] = maxZ
        return true
    }

    private companion object {
        const val PLANES = 6
        val RIM = intArrayOf(1, 2, 4, 3)
    }
}
