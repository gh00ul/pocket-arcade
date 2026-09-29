package com.pocketarcade.hub

import com.pocketarcade.engine.approach
import com.pocketarcade.engine.damp
import com.pocketarcade.engine.lerp
import com.pocketarcade.engine.r3d.Camera3D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The hall's 3D camera. Overhead (the default) it looks north and down across the floor from
 * behind the player, far enough back that a whole bank of machines fits across the screen
 * whatever its aspect, trailing the player smoothly with a little look-ahead. In first person
 * it is the player's eyes: at a kid's head (so turning on the spot turns the view in place),
 * free yaw, clamped pitch, a head-bob in step with the feet that grows with the pace, and a
 * slight widening of the view when running. Switching eases between the two poses, and either
 * can dive into a machine for the enter/exit transition.
 */
class HubCamera {
    companion object {
        /** First-person eye height: a little above the kids' heads, so marquees and screens read. */
        const val EYE_HEIGHT = 54f
        /**
         * How far behind the feet the eye sits: at the back of the head, so turning on the spot
         * turns the view in place. The body's clearance ([Body.RADIUS]) keeps cabinets at a
         * comfortable distance instead.
         */
        const val EYE_BACK = 2f
        /** First-person vertical field of view on screens about as wide as they are tall, degrees. */
        const val FP_FOV_DEG = 70f
        /** On tall portrait screens the vertical view widens (up to this) to keep a sensible width. */
        const val FP_MAX_FOV_DEG = 90f
        /** However the player's setting scales it, first person's vertical view stays within these, degrees. */
        const val MIN_FOV_DEG = 40f
        const val MAX_FOV_DEG = 120f
        /** The narrowest horizontal view first person aims for, degrees. */
        const val FP_MIN_HFOV_DEG = 50f
        /** How far first person can look up or down, degrees. */
        const val PITCH_LIMIT_DEG = 40f
        /** First person's resting pitch: a touch down, so the floor ahead and the screens show. */
        const val REST_PITCH_DEG = -6f
        /** Seconds to ease between overhead and first person. */
        const val BLEND_TIME = 0.5f
        /** Head-bob while walking: up and down, and side to side, in world units. */
        const val BOB_HEIGHT = 1.2f
        const val BOB_SWAY = 0.5f
        /** How much wider (degrees) the first-person view gets at a full run. */
        const val RUN_FOV_KICK_DEG = 4f
        /** Near clipping distances: first person stands right against cabinets. */
        const val OVERHEAD_NEAR = 8f
        const val FP_NEAR = 1.5f

        private const val DEG = PI.toFloat() / 180f

        /**
         * First person's vertical field of view (radians) for a screen [aspect] (width / height):
         * [FP_FOV_DEG], widened on narrow screens so the view is at least [FP_MIN_HFOV_DEG]
         * across, but never past [FP_MAX_FOV_DEG].
         */
        fun fpFovY(aspect: Float): Float {
            val base = FP_FOV_DEG * DEG
            val a = aspect.coerceAtLeast(0.1f)
            val needed = 2f * atan(tan(FP_MIN_HFOV_DEG * DEG / 2f) / a)
            return maxOf(base, needed).coerceAtMost(FP_MAX_FOV_DEG * DEG)
        }

        /**
         * Turns a joystick deflection ([jx] right, [jy] down the screen) into a world-space walk
         * direction relative to a first-person [yaw]: up is forward, sideways strafes. Writes
         * (x, z) into [out]; the length is the stick's.
         */
        fun moveRelative(jx: Float, jy: Float, yaw: Float, out: FloatArray) {
            val fx = sin(yaw)
            val fz = cos(yaw)
            // Right of the view: forward × up.
            val rx = -fz
            val rz = fx
            out[0] = rx * jx - fx * jy
            out[1] = rz * jx - fz * jy
        }

        /** Wraps an angle into (-π, π]. */
        fun wrap(a: Float): Float {
            val pi = PI.toFloat()
            val tau = 2f * pi
            var r = a % tau
            if (r > pi) r -= tau
            if (r <= -pi) r += tau
            return r
        }
    }

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
    var maxX = HubLayout.WIDTH - 150f
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

    // ------------------------------------------------------------------ first person

    /** Whether first person is on (what the blend is heading for). */
    var firstPerson = false
        private set

    /** Progress from overhead (0) to first person (1), linear in time; see [fpAmount]. */
    var fpBlend = 0f
        private set

    /** The eased blend between the two poses (0 overhead … 1 first person). */
    val fpAmount: Float
        get() {
            val t = fpBlend.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

    /** First-person heading like [Player.yaw]: 0 faces the entrance (+z), π the back wall. */
    var yaw = PI.toFloat()
        private set

    /** First-person pitch, radians, up positive; clamped to ±[PITCH_LIMIT_DEG]. */
    var pitch = REST_PITCH_DEG * DEG
        private set

    /** Where the player stands (feet), for the eye. */
    private var playerX = 304f
    private var playerZ = 600f
    private var walkPhase = 0f
    /**
     * How much of the head-bob is on: follows the walking pace (1 at walking speed, a little
     * more running) and eases to exactly 0 at rest.
     */
    var bobWeight = 0f
        private set
    /** Degrees the first-person view is widened by right now (running). */
    var fovKick = 0f
        private set

    /**
     * The player's options: a multiplier on the first-person field of view (1 = as designed; the
     * setting's degrees over [FP_FOV_DEG]), and on the head-bob and the run's field-of-view
     * kick (both 0 with reduced motion).
     */
    var fovScale = 1f
    var bobScale = 1f
    var kickScale = 1f

    /** How far behind the feet the eye is. */
    val eyeBack: Float get() = EYE_BACK

    /** The first-person eye as of the last [update], world units. */
    val eyeX: Float get() = playerX - sin(yaw) * eyeBack + sway() * -cos(yaw)
    val eyeY: Float get() = EYE_HEIGHT + bobLift()
    val eyeZ: Float get() = playerZ - cos(yaw) * eyeBack + sway() * sin(yaw)

    private fun bobLift(): Float = if (bobWeight <= 0f) 0f else BOB_HEIGHT * bobScale * bobWeight * (abs(sin(walkPhase)) - 0.35f)
    private fun sway(): Float = if (bobWeight <= 0f) 0f else BOB_SWAY * bobScale * bobWeight * cos(walkPhase)

    fun snapTo(px: Float, pz: Float) {
        targetX = px.coerceIn(minX, maxX)
        targetZ = (pz - below).coerceIn(minZ, maxZ)
        playerX = px
        playerZ = pz
    }

    fun follow(px: Float, pz: Float, vx: Float, vz: Float, dt: Float) {
        leadX = damp(leadX, vx * 0.35f, 3f, dt)
        leadZ = damp(leadZ, vz * 0.45f, 3f, dt)
        targetX = damp(targetX, (px + leadX).coerceIn(minX, maxX), 4f, dt)
        targetZ = damp(targetZ, (pz + leadZ - below).coerceIn(minZ, maxZ), 4f, dt)
    }

    /**
     * One simulation step: trails the player overhead, tracks the eye, runs the head-bob off the
     * walk cycle ([phase], [moving], at [gait] times its walking size) and eases the
     * overhead/first-person blend and the running view ([run], 0..1).
     */
    fun update(
        px: Float, pz: Float, vx: Float, vz: Float, moving: Boolean, phase: Float, dt: Float,
        gait: Float = if (moving) 1f else 0f, run: Float = 0f,
    ) {
        follow(px, pz, vx, vz, dt)
        playerX = px
        playerZ = pz
        walkPhase = phase
        bobWeight = approach(bobWeight, if (moving) gait.coerceIn(0f, 1.5f) else 0f, dt * 5f)
        fovKick = approach(fovKick, RUN_FOV_KICK_DEG * kickScale * run.coerceIn(0f, 1f), dt * RUN_FOV_KICK_DEG * 3f)
        fpBlend = approach(fpBlend, if (firstPerson) 1f else 0f, dt / BLEND_TIME)
    }

    /** Switches view; [animate] eases there over [BLEND_TIME], otherwise it cuts. */
    fun setFirstPerson(on: Boolean, animate: Boolean) {
        if (on != firstPerson) firstPerson = on
        if (!animate) fpBlend = if (on) 1f else 0f
    }

    /** Sets the first-person heading and pitch (pitch clamped). */
    fun setLook(yaw: Float, pitch: Float) {
        this.yaw = wrap(yaw)
        this.pitch = pitch.coerceIn(-PITCH_LIMIT_DEG * DEG, PITCH_LIMIT_DEG * DEG)
    }

    /** Turns the first-person view by [dYaw] (positive turns left) and tilts it by [dPitch] (up). */
    fun look(dYaw: Float, dPitch: Float) = setLook(yaw + dYaw, pitch + dPitch)

    /**
     * Points the first-person view from the eye above ([fromX], [fromZ]) at a world point, with
     * the pitch kept gentle ([-25°, 10°]) so the machine fills the view rather than the floor.
     */
    fun face(fromX: Float, fromZ: Float, x: Float, y: Float, z: Float) {
        val dx = x - fromX
        val dz = z - fromZ
        val flat = sqrt(dx * dx + dz * dz)
        if (flat < 1e-3f) return
        val p = atan2(y - EYE_HEIGHT, flat).coerceIn(-25f * DEG, 10f * DEG)
        setLook(atan2(dx, dz), p)
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
        val s = fpAmount
        if (s > 0f) {
            // The eye, and a point straight ahead of it, so the gaze swings evenly while the eye
            // swoops down (or back up).
            val fx = eyeX
            val fy = eyeY
            val fz = eyeZ
            val cp = cos(pitch)
            val reach = 120f
            val lx = fx + sin(yaw) * cp * reach
            val ly = fy + sin(pitch) * reach
            val lz = fz + cos(yaw) * cp * reach
            ex = lerp(ex, fx, s)
            ey = lerp(ey, fy, s)
            ez = lerp(ez, fz, s)
            gx = lerp(gx, lx, s)
            gy = lerp(gy, ly, s)
            gz = lerp(gz, lz, s)
            fov = lerp(fov, (fpFovY(aspect) * fovScale).coerceIn(MIN_FOV_DEG * DEG, MAX_FOV_DEG * DEG) + fovKick * DEG, s)
        }
        if (dive > 0f) {
            val t = dive.coerceIn(0f, 1f)
            val d = t * t * (3f - 2f * t)
            ex = lerp(ex, diveX, d)
            ey = lerp(ey, diveY + 2f, d)
            ez = lerp(ez, diveZ + 22f, d)
            gx = lerp(gx, diveX, d)
            gy = lerp(gy, diveY, d)
            gz = lerp(gz, diveZ, d)
            fov = lerp(fov, Math.toRadians(50.0).toFloat(), d)
        }
        cam.near = lerp(OVERHEAD_NEAR, FP_NEAR, s)
        cam.lookAt(ex, ey, ez, gx, gy, gz, fov, width, height)
    }
}
