package com.pocketarcade.engine.r3d

import com.pocketarcade.engine.Particles
import com.pocketarcade.engine.PunchSpring
import com.pocketarcade.engine.gl.Gfx
import com.pocketarcade.engine.gl.GfxQuality
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

    /**
     * The particle pool of the game being drawn, offered to the GPU picture: the game host sets
     * it before the game draws, and [Stage3D.present] records it into the pass so the particles
     * are drawn inside the 3D image and glow in its bloom. It is cleared once the game has drawn.
     */
    var particles: Particles? = null

    /**
     * Set by [Stage3D.present] when the offered [particles] are in the GPU picture (recorded into
     * this frame's pass, or already in the last picture when the frame cap skipped this one).
     * Anything left false, such as a flat game with no [Stage3D], paints them in 2D instead.
     */
    var particlesInGl = false

    /**
     * A camera punch the game host wants (from [com.pocketarcade.games.GameFx.punch]), 0 for none.
     * The next [Stage3D.begin] takes it; the host clears what no stage took (a flat game).
     */
    var punchRequest = 0f
        private set

    /** Asks for a camera punch of [amount] (0..1); the strongest request in a frame wins. */
    fun requestPunch(amount: Float) {
        if (amount > punchRequest) punchRequest = amount
    }

    /** Hands the pending punch to a stage and clears it. */
    fun takePunch(): Float {
        val p = punchRequest
        punchRequest = 0f
        return p
    }
}

/**
 * A mini-game's 3D view. The game aims the camera with [look]; each frame [begin] starts a
 * picture covering the whole [fieldW] × [fieldH] play field and [present] hands it to the GPU.
 * It also maps between field units (touches, particles, score popups) and the 3D world.
 *
 * Projection and touch helpers are plain math, so games can use them in headless tests too.
 */
class Stage3D(val fieldW: Int, val fieldH: Int) {
    companion object {
        /** At a full punch the lens tightens by this fraction of the field of view... */
        const val PUNCH_FOV = 0.05f
        /** ...and the eye moves this fraction of the way to the target: together about 8% bigger. */
        const val PUNCH_DOLLY = 0.03f
    }

    /** Follows the frame-rate cap: on a fast display, frames the GPU would not draw aren't recorded. */
    val r = Renderer3D(fieldW, fieldH).also { it.frameCapped = true }

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

    private val punchSpring = PunchSpring()
    private var punchedCam = false
    private var lastBeginNanos = 0L

    /** Where the frame clock comes from; tests swap it to step the punch deterministically. */
    internal var nanoClock: () -> Long = System::nanoTime

    /**
     * Kicks the camera: a quick push-in (the lens tightens and the eye dollies toward the target)
     * that springs back with a slight overshoot, applied in [begin] and off with reduce motion.
     * [amount] is 0..1 (0.3 for a solid hit, 1 for the biggest moment).
     */
    fun punch(amount: Float) = punchSpring.kick(amount)

    /** How far the punch is out right now (1 at the biggest, slightly negative on the swing back). */
    val punchValue: Float get() = punchSpring.value

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

    /** Starts a frame with the camera set (and any punch applied). Draw into the returned renderer. */
    fun begin(): Renderer3D {
        r.startFrame()
        r.resize(fieldW, fieldH)
        r.camera.near = cam.near
        val requested = GameViewport.takePunch()
        if (requested > 0f) punchSpring.kick(requested)
        // The punch runs on the wall clock, so it plays out over the same half second whether the
        // game is frozen by a hit-stop, slowed, paused or drawing at 30 or 120 fps.
        val now = nanoClock()
        val dt = if (lastBeginNanos == 0L) 0f else ((now - lastBeginNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastBeginNanos = now
        punchSpring.update(dt)
        val p = punchSpring.value
        if (p != 0f) {
            // Push in: a tighter lens and the eye a little nearer the target. The game's own camera
            // follows, so touches and projected popups still land where the picture shows things.
            val k = p * PUNCH_DOLLY
            val ex = eyeX + (tgtX - eyeX) * k
            val ey = eyeY + (tgtY - eyeY) * k
            val ez = eyeZ + (tgtZ - eyeZ) * k
            val f = fov * (1f - p * PUNCH_FOV)
            r.camera.lookAt(ex, ey, ez, tgtX, tgtY, tgtZ, f, fieldW, fieldH, centerY)
            cam.lookAt(ex, ey, ez, tgtX, tgtY, tgtZ, f, fieldW, fieldH, centerY)
            punchedCam = true
        } else {
            r.camera.lookAt(eyeX, eyeY, eyeZ, tgtX, tgtY, tgtZ, fov, fieldW, fieldH, centerY)
            if (punchedCam) {
                cam.lookAt(eyeX, eyeY, eyeZ, tgtX, tgtY, tgtZ, fov, fieldW, fieldH, centerY)
                punchedCam = false
            }
        }
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
        val source = v.particles
        if (source != null && GfxQuality.glParticles) {
            // A skipped frame keeps the last picture on screen, with its particles in it.
            if (!pass.skipped) source.recordGl(pass, fieldW.toFloat(), fieldH.toFloat())
            v.particlesInGl = true
        }
        Gfx.submit(GameViewport.SLOT, pass)
    }
}
