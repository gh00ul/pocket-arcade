package com.pocketarcade.hub

import com.pocketarcade.engine.damp
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.r3d.Camera3D
import kotlin.math.cos
import kotlin.math.sin

/**
 * The hall's 3D camera: it looks north and down at the player from behind, trails them smoothly
 * with a little look-ahead, and can dive into a cabinet's screen for the enter/exit transition.
 */
class HubCamera {
    var targetX = 112f
        private set
    var targetZ = 700f
        private set

    var pitchDeg = 44f
    var distance = 390f
    var fovDeg = 48f
    var minX = 78f
    var maxX = 146f
    /** Clamp for the target so the view never runs past the back wall or the entrance. */
    var minZ = 200f
    var maxZ = 1e6f

    private var leadX = 0f
    private var leadZ = 0f

    /** 0 = following the player, 1 = right in front of the dive target. */
    var dive = 0f
    var diveX = 0f
    var diveY = 0f
    var diveZ = 0f

    fun snapTo(px: Float, pz: Float) {
        targetX = px.coerceIn(minX, maxX)
        targetZ = (pz - 30f).coerceIn(minZ, maxZ)
    }

    fun follow(px: Float, pz: Float, vx: Float, vz: Float, dt: Float) {
        leadX = damp(leadX, vx * 0.25f, 3f, dt)
        leadZ = damp(leadZ, vz * 0.35f, 3f, dt)
        targetX = damp(targetX, (px + leadX).coerceIn(minX, maxX), 4f, dt)
        targetZ = damp(targetZ, (pz + leadZ - 30f).coerceIn(minZ, maxZ), 4f, dt)
    }

    fun apply(cam: Camera3D, width: Int, height: Int) {
        val p = Math.toRadians(pitchDeg.toDouble()).toFloat()
        var ex = targetX
        var ey = 12f + sin(p) * distance
        var ez = targetZ + cos(p) * distance
        var gx = targetX
        var gy = 12f
        var gz = targetZ
        var fov = Math.toRadians(fovDeg.toDouble()).toFloat()
        if (dive > 0f) {
            val t = dive.coerceIn(0f, 1f)
            val s = t * t * (3f - 2f * t)
            ex = lerp(ex, diveX, s)
            ey = lerp(ey, diveY + 2f, s)
            ez = lerp(ez, diveZ + 20f, s)
            gx = lerp(gx, diveX, s)
            gy = lerp(gy, diveY, s)
            gz = lerp(gz, diveZ, s)
            fov = lerp(fov, Math.toRadians(48.0).toFloat(), s)
        }
        cam.lookAt(ex, ey, ez, gx, gy, gz, fov, width, height)
    }
}
