package com.pocketarcade.hub

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The animation state of one figure (the player, a kid, the barista, the clerk), and the joint
 * angles [Figure.draw] poses it with. Owners call [update] once per simulation step with where
 * the figure is and what it is doing; every value read by the renderer is solved there, so
 * drawing only composes transforms. Pure maths, allocation-free after construction, so it runs
 * in the JVM tests.
 *
 * Several figures can share one [Figure] (same look) but never one [FigureAnim].
 */
class FigureAnim(val seed: Int = 0, val scale: Float = 1f) {
    companion object {
        /** Reduce-motion setting: no bounce, no overshoot, calmer follow-through (set from the settings). */
        @JvmStatic
        var reduceMotion = false

        private const val PI_F = PI.toFloat()

        /** How far a seated figure's hips drop (figure units), and how far their legs stick out (radians, forward). */
        const val SEAT_DROP = 5f
        const val SEAT_LEG = 1.45f

        /** The seat spring: frequency (rad/s) and damping ratio, which leaves a small settle on sitting down. */
        private const val SEAT_OMEGA = 12f
        private const val SEAT_ZETA = 0.62f

        /** Walking legs and arms swing this far (radians) at the old fixed pace. */
        private const val LEG_SWING = 0.6f
        private const val ARM_SWING = 0.5f
    }

    // ------------------------------------------------------------------ outputs

    /** Visual facing (radians): the owner's yaw, smoothed. */
    var yaw = 0f
        private set

    /** Body height offset, in figure units (multiplied by the figure's scale when drawn). */
    var rootY = 0f
        private set

    /** Arm angles, index 0 the left (-x) and 1 the right (+x): forward swing (negative pitch) and outward roll. */
    val armPitch = FloatArray(2)
    val armRoll = FloatArray(2)

    /** Leg swing (negative pitch is forwards) and how far each leg is lifted at the hip, index as [armPitch]. */
    val legPitch = FloatArray(2)
    val legLift = FloatArray(2)

    /** How much of a café treat is in the hand: 0 none .. 1 fully held. */
    var itemAmount = 0f
        private set

    /** Sideways body sway (figure units, along the figure's own x axis). */
    var sway = 0f
        private set

    /** The spine, about the hips: forward lean (positive is forwards), bank (roll) and twist (yaw). */
    var lean = 0f
        private set
    var leanRoll = 0f
        private set
    var twist = 0f
        private set

    /** Breathing: the chest's swell (a fraction) and how far it lifts the shoulders and head (figure units). */
    var breath = 0f
        private set
    var breathLift = 0f
        private set

    /** The head, about the neck, relative to the spine: turn, nod (positive looks down) and tilt. */
    var headYaw = 0f
        private set
    var headPitch = 0f
        private set
    var headRoll = 0f
        private set

    /** The hat's lag behind the head (pitch and roll about the head's centre) and its lift (figure units). */
    var hatPitch = 0f
        private set
    var hatRoll = 0f
        private set
    var hatLift = 0f
        private set

    /** A ponytail's swing about its joint on the back of the head. */
    var tailPitch = 0f
        private set
    var tailRoll = 0f
        private set

    /** How far the eyelids are closed: 0 open .. 1 shut. */
    var blink = 0f
        private set

    // ------------------------------------------------------------------ state

    /** The cross-fading pose weights. */
    val blender = PoseBlender()

    /** The animation's own clock (seconds), offset per figure so a crowd never moves in step. */
    var clock = (seed and 63) * 1.7f
        private set

    private var started = false

    /** The gait cycle, in radians; only advances while the figure walks. */
    var phase = 0f
        private set

    /** 1 while seated (eases with a small settle), 0 standing. */
    private val seat = Spring()

    private var cheerAge = 0f
    private var waveAge = 0f
    private var clapAge = 0f

    // Pose accumulators (weights times what each pose asks for), reset in solve.
    private val accArmP = FloatArray(2)
    private val accArmR = FloatArray(2)
    private val accSwing = FloatArray(2)
    private var accRoot = 0f
    private var accItem = 0f

    /**
     * One simulation step of [dt] seconds. [x], [z] is where the figure stands, [yaw] where it
     * is facing and [pose] what it is doing. [walkPhase] is the walk cycle.
     */
    fun update(dt: Float, x: Float, z: Float, yaw: Float, pose: Pose, walkPhase: Float) {
        if (!(dt > 0f)) return
        val h = if (dt > 0.1f) 0.1f else dt
        if (!started) {
            started = true
            this.yaw = yaw
            blender.snap(pose)
            seat.reset(seatTarget(pose))
        }
        clock += h
        this.yaw += AnimMath.wrap(yaw - this.yaw) * AnimMath.k(16f, h)
        phase = walkPhase
        val was = blender.target
        blender.set(pose)
        if (blender.target != was) {
            when (pose) {
                Pose.CHEER -> cheerAge = 0f
                Pose.WAVE -> waveAge = 0f
                Pose.CLAP -> clapAge = 0f
                else -> {}
            }
        }
        cheerAge += h
        waveAge += h
        clapAge += h
        blender.update(h)
        // The seat follows the (already eased) sitting weight, so sitting starts gently, and its
        // spring adds the settle at the bottom.
        seat.step(blender.weight(Pose.SIT) + blender.weight(Pose.SIP), h, SEAT_OMEGA, SEAT_ZETA)
        solve()
    }

    /**
     * Sets the pose at once, for a still picture (the prize counter, the photo booth): [time]
     * drives the idle motion, [walkPhase] the stride, and a walking pose gets a walking gait.
     */
    fun setStatic(pose: Pose, time: Float, walkPhase: Float, yaw: Float) {
        started = true
        this.yaw = yaw
        clock = time
        phase = walkPhase
        blender.snap(pose)
        seat.reset(seatTarget(pose))
        cheerAge = time
        waveAge = time
        clapAge = time
        solve()
    }

    private fun seatTarget(pose: Pose): Float = if (pose == Pose.SIT || pose == Pose.SIP) 1f else 0f

    // ------------------------------------------------------------------ the rig

    private fun solve() {
        val w = blender.weights
        accArmP[0] = 0f; accArmP[1] = 0f
        accArmR[0] = 0f; accArmR[1] = 0f
        accSwing[0] = 0f; accSwing[1] = 0f
        accRoot = 0f
        accItem = 0f
        for (i in w.indices) {
            val wi = w[i]
            if (wi > 1e-4f) addPose(Pose.ALL[i], wi)
        }
        val walking = w[Pose.WALK.ordinal] + w[Pose.CARRY.ordinal]
        val swing = sin(phase)
        val seated = seat.x.coerceIn(0f, 1f)
        for (s in 0..1) {
            val side = if (s == 0) -1f else 1f
            legPitch[s] = walking * swing * LEG_SWING * side * (1f - seated) - SEAT_LEG * seat.x
            legLift[s] = 0f
            // Arms swing against the legs while walking (a hand holding a treat keeps still).
            armPitch[s] = accArmP[s] - swing * ARM_SWING * side * accSwing[s]
            armRoll[s] = accArmR[s]
        }
        rootY = accRoot - SEAT_DROP * seat.x
        itemAmount = accItem.coerceIn(0f, 1f)
    }

    /** Adds pose [p]'s share [wi] of every joint. */
    private fun addPose(p: Pose, wi: Float) {
        val t = clock
        when (p) {
            Pose.STAND -> {
                accArmP[0] += wi * sin(t * 1.3f - 1f) * 0.05f; accArmR[0] += wi * -0.1f
                accArmP[1] += wi * sin(t * 1.3f + 1f) * 0.05f; accArmR[1] += wi * 0.1f
            }
            Pose.WALK -> {
                accArmR[0] += wi * -0.12f; accArmR[1] += wi * 0.12f
                accSwing[0] += wi; accSwing[1] += wi
            }
            Pose.PLAY -> {
                accArmP[0] += wi * (-1.05f + sin(t * 11f - 1f) * 0.12f); accArmR[0] += wi * -0.05f
                accArmP[1] += wi * (-1.05f + sin(t * 11f + 1f) * 0.12f); accArmR[1] += wi * 0.05f
            }
            Pose.CHEER -> {
                accArmP[0] += wi * (-2.6f + sin(cheerAge * 9f - 1f) * 0.25f); accArmR[0] += wi * -0.2f
                accArmP[1] += wi * (-2.6f + sin(cheerAge * 9f + 1f) * 0.25f); accArmR[1] += wi * 0.2f
                accRoot += wi * abs(sin(cheerAge * 9f)) * 2.5f
            }
            Pose.SIT -> {
                accArmP[0] += wi * -0.6f; accArmR[0] += wi * -0.1f
                accArmP[1] += wi * -0.6f; accArmR[1] += wi * 0.1f
            }
            Pose.WIPE -> {
                // Circles with a cloth on the counter top.
                accArmP[1] += wi * (-1.15f + sin(t * 7f) * 0.1f); accArmR[1] += wi * (0.05f + cos(t * 7f) * 0.28f)
                accArmP[0] += wi * -0.75f; accArmR[0] += wi * -0.12f
            }
            Pose.CARRY -> {
                accArmP[1] += wi * (-1.25f + sin(t * 2f) * 0.04f); accArmR[1] += wi * 0.02f
                accArmR[0] += wi * -0.12f
                accSwing[0] += wi
                accItem += wi
            }
            Pose.HOLD -> {
                accArmP[1] += wi * (-1.25f + sin(t * 2f) * 0.04f); accArmR[1] += wi * 0.02f
                accArmP[0] += wi * sin(t * 1.3f) * 0.05f; accArmR[0] += wi * -0.1f
                accItem += wi
            }
            Pose.SIP -> {
                // Every few seconds the cup comes up for a sip.
                val c = t % 4.5f
                val lift = if (c < 1.4f) sin(c / 1.4f * PI_F) else 0f
                accArmP[1] += wi * (-0.95f - lift * 1.35f); accArmR[1] += wi * (0.05f + lift * 0.2f)
                accArmP[0] += wi * -0.6f; accArmR[0] += wi * -0.1f
                accItem += wi
            }
            // Emotes: arms as standing until their own poses arrive (see below).
            Pose.WAVE, Pose.CLAP -> {
                accArmR[0] += wi * -0.1f; accArmR[1] += wi * 0.1f
            }
        }
    }
}
