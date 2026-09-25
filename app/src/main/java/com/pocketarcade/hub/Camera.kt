package com.pocketarcade.hub

import com.pocketarcade.engine.damp
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.r3d.Camera3D
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * The hall's 3D camera: it looks north and down across the floor from behind the player, far
 * enough back that a whole bank of machines fits across the screen whatever its aspect, trails
 * the player smoothly with a little look-ahead, stays over the hall, and can dive into a machine
 * for the enter/exit transition.
 */
class HubCamera {
    var targetX = 304f
        private set
    var targetZ = 600f
        private set

    var pitchDeg = 55f
    /** Vertical field of view, degrees. */
    var fovDeg = 56f
    /** How much of the floor, in world units, should span the screen at the target. */
    var coverWidth = 370f
    var coverHeight = 420f
    var minX = 150f
    var maxX = 458f
    /** Clamp for the target so the view stays over the hall (and the pavement out front). */
    var minZ = 190f
    var maxZ = HubLayout.FRONT_WALL - 204f

    private var leadX = 0f
    private var leadZ = 0f

    /** 0 = following the player, 1 = right in front of the dive target. */
    var dive = 0f
    var diveX = 0f
    var diveY = 0f
    var diveZ = 0f

    /** The player sits a little below the middle of the screen, where there's less perspective squeeze. */
    private val below = 30f

    fun snapTo(px: Float, pz: Float) {
        targetX = px.coerceIn(minX, maxX)
        targetZ = (pz - below).coerceIn(minZ, maxZ)
    }

    fun follow(px: Float, pz: Float, vx: Float, vz: Float, dt: Float) {
        leadX = damp(leadX, vx * 0.35f, 3f, dt)
        leadZ = damp(leadZ, vz * 0.45f, 3f, dt)
        targetX = damp(targetX, (px + leadX).coerceIn(minX, maxX), 4f, dt)
        targetZ = damp(targetZ, (pz + leadZ - below).coerceIn(minZ, maxZ), 4f, dt)
    }

    fun apply(cam: Camera3D, width: Int, height: Int) {
        val p = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val fovY = Math.toRadians(fovDeg.toDouble()).toFloat()
        val aspect = width.toFloat() / height.coerceAtLeast(1)
        val halfV = tan(fovY / 2f)
        val halfH = halfV * aspect
        // Back off until the wanted slice of floor fits both ways.
        val distance = maxOf(coverWidth / (2f * halfH), coverHeight / (2f * halfV))
        var ex = targetX
        var ey = 10f + sin(p) * distance
        var ez = targetZ + cos(p) * distance
        var gx = targetX
        var gy = 10f
        var gz = targetZ
        var fov = fovY
        if (dive > 0f) {
            val t = dive.coerceIn(0f, 1f)
            val s = t * t * (3f - 2f * t)
            ex = lerp(ex, diveX, s)
            ey = lerp(ey, diveY + 2f, s)
            ez = lerp(ez, diveZ + 22f, s)
            gx = lerp(gx, diveX, s)
            gy = lerp(gy, diveY, s)
            gz = lerp(gz, diveZ, s)
            fov = lerp(fov, Math.toRadians(50.0).toFloat(), s)
        }
        cam.lookAt(ex, ey, ez, gx, gy, gz, fov, width, height)
    }
}
