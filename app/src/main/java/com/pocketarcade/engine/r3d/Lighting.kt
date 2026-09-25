package com.pocketarcade.engine.r3d

import kotlin.math.sqrt

/** A coloured point light with a smooth quadratic falloff to zero at [radius]. */
class PointLight(
    var x: Float, var y: Float, var z: Float,
    var r: Float, var g: Float, var b: Float,
    var radius: Float, var intensity: Float = 1f,
)

/**
 * Per-vertex lighting: ambient + one directional light + point lights, evaluated with a
 * wrapped Lambert term so walls and floors both pick up nearby glow. 1.0 = texture colour.
 */
class Lighting {
    var ambR = 0.4f
    var ambG = 0.4f
    var ambB = 0.45f
    var dirX = 0f
    var dirY = 1f
    var dirZ = 0f
    var dirR = 0f
    var dirG = 0f
    var dirB = 0f
    val points = ArrayList<PointLight>()

    fun setDirection(x: Float, y: Float, z: Float) {
        val l = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-5f)
        dirX = x / l; dirY = y / l; dirZ = z / l
    }

    fun shade(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, out: FloatArray) {
        var r = ambR
        var g = ambG
        var b = ambB
        val d = nx * dirX + ny * dirY + nz * dirZ
        if (d > 0f) {
            r += dirR * d; g += dirG * d; b += dirB * d
        }
        for (i in points.indices) {
            val p = points[i]
            val dx = p.x - x
            val dy = p.y - y
            val dz = p.z - z
            val d2 = dx * dx + dy * dy + dz * dz
            val rad = p.radius
            if (d2 >= rad * rad) continue
            val dist = sqrt(d2).coerceAtLeast(1e-3f)
            val fall = 1f - dist / rad
            val lam = ((dx * nx + dy * ny + dz * nz) / dist * 0.6f + 0.4f).coerceAtLeast(0f)
            val k = fall * fall * lam * p.intensity
            r += p.r * k; g += p.g * k; b += p.b * k
        }
        out[0] = r.coerceAtMost(2.2f)
        out[1] = g.coerceAtMost(2.2f)
        out[2] = b.coerceAtMost(2.2f)
    }
}
