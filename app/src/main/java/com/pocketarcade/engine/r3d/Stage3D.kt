package com.pocketarcade.engine.r3d

import com.pocketarcade.engine.gl.Gfx
import kotlin.math.roundToInt

/**
 * Where the game field currently sits on the window, set by the game host every frame so a
 * game's 3D picture lines up with the interface drawn over it.
 */
object GameViewport {
    /** Top-left of the field in window pixels, and window pixels per field unit. */
    var x = 0f
    var y = 0f
    var scale = 1f
    /** Screen-shake offset in field units, applied while a game draws. */
    var shakeX = 0f
    var shakeY = 0f
    /** The field's clip rectangle in window pixels. */
    var clipX0 = 0
    var clipY0 = 0
    var clipX1 = 0
    var clipY1 = 0
    /** Slot the game's picture is submitted to. */
    const val SLOT = "game"
}

/**
 * A mini-game's 3D view. The game aims the camera with [look]; each frame [begin] starts a
 * picture covering the whole [fieldW] × [fieldH] play field and [present] hands it to the GPU.
 * It also maps between field units (touches, particles, score popups) and the 3D world.
 *
 * Projection and touch helpers are plain math, so games can use them in headless tests too.
 */
class Stage3D(val fieldW: Int, val fieldH: Int) {
    val r = Renderer3D(fieldW, fieldH)

    /** The camera in field units; [begin] copies it to the renderer. */
    val cam = Camera3D()

    private var eyeX = 0f
    private var eyeY = 0f
    private var eyeZ = 1f
    private var tgtX = 0f
    private var tgtY = 0f
    private var tgtZ = 0f
    private var fov = 1f
    private var centerY = 0.5f

    fun look(
        eyeX: Float, eyeY: Float, eyeZ: Float, tx: Float, ty: Float, tz: Float,
        fovDeg: Float, centerYFrac: Float = 0.5f,
    ) {
        this.eyeX = eyeX; this.eyeY = eyeY; this.eyeZ = eyeZ
        tgtX = tx; tgtY = ty; tgtZ = tz
        fov = fovDeg * (Math.PI.toFloat() / 180f)
        centerY = centerYFrac
        cam.lookAt(eyeX, eyeY, eyeZ, tx, ty, tz, fov, fieldW, fieldH, centerYFrac)
    }

    /** Projects a world point to field units in [out] (x, y, depth). False if behind the eye. */
    fun toField(x: Float, y: Float, z: Float, out: FloatArray): Boolean = cam.project(x, y, z, out)

    /** How many field units one world unit spans at that point's depth. */
    fun scaleAt(x: Float, y: Float, z: Float): Float = cam.focal / cam.viewZ(x, y, z).coerceAtLeast(cam.near)

    /** Casts a touch at field ([fx], [fy]) onto the plane y = [planeY]; (x, z) goes in [out]. */
    fun touchToPlane(fx: Float, fy: Float, planeY: Float, out: FloatArray): Boolean =
        cam.rayToPlaneY(fx, fy, planeY, out)

    /** Starts a frame with the camera set. Draw into the returned renderer. */
    fun begin(): Renderer3D {
        r.startFrame()
        r.resize(fieldW, fieldH)
        r.camera.near = cam.near
        r.camera.lookAt(eyeX, eyeY, eyeZ, tgtX, tgtY, tgtZ, fov, fieldW, fieldH, centerY)
        return r
    }

    /** Hands the frame to the GPU, placed over the field wherever the host has put it. */
    fun present() {
        val v = GameViewport
        val x = v.x + v.shakeX * v.scale
        val y = v.y + v.shakeY * v.scale
        val pass = r.finishFrame(
            x.roundToInt(), y.roundToInt(), (fieldW * v.scale).roundToInt(), (fieldH * v.scale).roundToInt(),
            v.clipX0, v.clipY0, v.clipX1, v.clipY1, clip = true,
        )
        Gfx.submit(GameViewport.SLOT, pass)
    }
}
