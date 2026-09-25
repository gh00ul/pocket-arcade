package com.pocketarcade.engine.r3d

import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.roundToInt

/**
 * A mini-game's 3D view. The game aims the camera once with [look]; the stage renders into a
 * framebuffer covering the whole [fieldW] × [fieldH] play field and maps between field units
 * (touches, particles, score popups) and the 3D world. The framebuffer starts at one pixel per
 * field unit and gets coarser on its own when a phone can't keep up.
 *
 * Projection and touch helpers are plain math, so games can use them in headless tests too.
 */
class Stage3D(val fieldW: Int, val fieldH: Int, name: String, budgetMs: Float = 7f) {
    val r = Renderer3D(1, 1)

    /** The camera in field units; [begin] copies it to the renderer at framebuffer size. */
    val cam = Camera3D()

    private val frame = FrameImage()
    private val budget = FrameBudget(budgetMs, name)
    private var density = 1f
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

    /** Sizes the framebuffer, sets the camera and starts timing. Draw into the returned renderer. */
    fun begin(): Renderer3D {
        val w = (fieldW * density).roundToInt().coerceAtLeast(16)
        val h = (fieldH * density).roundToInt().coerceAtLeast(16)
        r.resize(w, h)
        r.camera.near = cam.near
        r.camera.lookAt(eyeX, eyeY, eyeZ, tgtX, tgtY, tgtZ, fov, w, h, centerY)
        budget.begin()
        return r
    }

    /** Ends timing and draws the frame over the field (0, 0)–(fieldW, fieldH). */
    fun present(scope: DrawScope) {
        if (budget.end() && density > 0.5f) density = (density - 0.25f).coerceAtLeast(0.5f)
        frame.draw(scope, r, 0f, 0f, fieldW.toFloat(), fieldH.toFloat())
    }
}
