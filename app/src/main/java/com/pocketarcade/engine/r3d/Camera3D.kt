package com.pocketarcade.engine.r3d

import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Pinhole camera. World axes: x right, y up, z toward the viewer ("south").
 * View space: vx right, vy up, vz depth in front of the eye.
 */
class Camera3D {
    var ex = 0f
        private set
    var ey = 0f
        private set
    var ez = 0f
        private set
    var rx = 1f; var ry = 0f; var rz = 0f
        private set
    var ux = 0f; var uy = 1f; var uz = 0f
        private set
    var fx = 0f; var fy = 0f; var fz = -1f
        private set
    var focal = 1f
        private set
    var cx = 0f
        private set
    var cy = 0f
        private set
    var near = 8f

    /** Aims the camera from the eye at a target with a vertical field of view in radians. */
    fun lookAt(eyeX: Float, eyeY: Float, eyeZ: Float, tx: Float, ty: Float, tz: Float, fovY: Float, width: Int, height: Int) {
        ex = eyeX; ey = eyeY; ez = eyeZ
        var dx = tx - eyeX
        var dy = ty - eyeY
        var dz = tz - eyeZ
        val dl = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-5f)
        dx /= dl; dy /= dl; dz /= dl
        fx = dx; fy = dy; fz = dz
        // right = forward × worldUp(0,1,0)
        var ax = -fz
        var az = fx
        val al = sqrt(ax * ax + az * az).coerceAtLeast(1e-5f)
        ax /= al; az /= al
        rx = ax; ry = 0f; rz = az
        // up = right × forward
        ux = ry * fz - rz * fy
        uy = rz * fx - rx * fz
        uz = rx * fy - ry * fx
        focal = (height / 2f) / tan(fovY / 2f)
        cx = width / 2f
        cy = height / 2f
    }

    fun viewX(x: Float, y: Float, z: Float) = (x - ex) * rx + (y - ey) * ry + (z - ez) * rz
    fun viewY(x: Float, y: Float, z: Float) = (x - ex) * ux + (y - ey) * uy + (z - ez) * uz
    fun viewZ(x: Float, y: Float, z: Float) = (x - ex) * fx + (y - ey) * fy + (z - ez) * fz

    /** Projects a world point to framebuffer pixels in [out] (x, y, depth). False if behind the eye. */
    fun project(x: Float, y: Float, z: Float, out: FloatArray): Boolean {
        val vz = viewZ(x, y, z)
        if (vz < near) return false
        out[0] = cx + viewX(x, y, z) / vz * focal
        out[1] = cy - viewY(x, y, z) / vz * focal
        out[2] = vz
        return true
    }

    /**
     * Casts a ray through framebuffer pixel ([sx], [sy]) and intersects the horizontal plane
     * y = [planeY]. Writes the hit (x, z) into [out]; false if the ray misses.
     */
    fun rayToPlaneY(sx: Float, sy: Float, planeY: Float, out: FloatArray): Boolean {
        val px = (sx - cx) / focal
        val py = -(sy - cy) / focal
        val dx = fx + rx * px + ux * py
        val dy = fy + ry * px + uy * py
        val dz = fz + rz * px + uz * py
        if (dy > -1e-5f && dy < 1e-5f) return false
        val t = (planeY - ey) / dy
        if (t <= 0f) return false
        out[0] = ex + dx * t
        out[1] = ez + dz * t
        return true
    }
}
