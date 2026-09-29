package com.pocketarcade.hub

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
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

        // ---- moving about

        /** The longest step of time simulated in one update (a stalled frame isn't worth animating in full). */
        private const val MAX_DT = 0.1f

        /** The figure's own velocity is smoothed at this rate (per second) before it is differentiated into an acceleration. */
        private const val VEL_SMOOTH = 22f

        /** A move faster than this (world units a second) in one step is a shove or a teleport, not walking: it isn't animated. */
        private const val MAX_STEP_SPEED = 160f

        /** The strongest acceleration (world units a second squared) that leans a body or throws a hat. */
        private const val ACCEL_CAP = 700f

        /** The gait's weight reaches 1 at this speed, and follows the speed at this rate (per second)... */
        private const val GAIT_FULL_SPEED = 14f
        private const val GAIT_RATE = 16f

        /** ...the walk-to-run blend follows the speed at this rate... */
        private const val RUN_RATE = 5f

        /** ...and below this speed the stride stops advancing and the feet settle together. */
        private const val STOP_SPEED = 4f
        private const val PLANT_RATE = 12f

        /** The stride phase is wrapped after this many radians (whole turns, so nothing changes). */
        private const val PHASE_WRAP = (200.0 * Math.PI).toFloat()

        /** Facing is smoothed at this rate (per second) and never turns faster than [TURN_MAX] radians a second. */
        private const val YAW_RATE = 16f
        private const val TURN_MAX = 14f
        private const val YAW_RATE_SMOOTH = 20f

        /** Arms swing this much for each radian of leg swing, more when running, and never past [ARM_SWING_MAX]. */
        private const val ARM_PER_AMP = 0.9f
        private const val ARM_RUN_BONUS = 0.5f
        private const val ARM_SWING_MAX = 1.05f

        /** A run carries the free arms forward and in (radians). */
        private const val RUN_ARM_FORWARD = 0.45f
        private const val RUN_ARM_IN = 0.10f

        /** Shoulders twist against the hips (radians), 60% more when running, and the torso rocks side to side over the planted foot. */
        private const val TWIST_WALK = 0.11f
        private const val TWIST_RUN_BONUS = 0.6f
        private const val GAIT_ROLL = 0.035f

        /**
         * Forward lean (radians) at a shuffle, a walk and a run; then the body also leans into an acceleration
         * ([LEAN_ACCEL] radians per world unit a second squared, so speeding up tips it forward and a
         * hard stop rocks it back), capped at [LEAN_MAX]. The lean is a spring, so it settles.
         */
        private const val LEAN_SHUFFLE = 0.015f
        private const val LEAN_WALK = 0.05f
        private const val LEAN_RUN = 0.20f
        private const val LEAN_ACCEL = 0.0008f
        private const val LEAN_MAX = 0.4f
        private const val LEAN_OMEGA = 11f
        private const val LEAN_ZETA = 0.75f

        /** The head stays this much more level than the spine (the neck takes up part of a lean). */
        private const val HEAD_LEVEL = 0.6f

        /** Banking into a turn: radians of roll per radian a second of turning, at speeds up to [BANK_SPEED], capped at [BANK_MAX]. */
        private const val TURN_BANK = 0.010f
        private const val BANK_SPEED = 60f
        private const val BANK_MAX = 0.16f
        private const val BANK_RATE = 8f

        /** With reduce motion on, the body's dip with each step keeps this share, the run's hop none. */
        private const val REDUCED_DIP = 0.35f

        /** What a still picture assumes for the ground speed of a walking pose. */
        private const val STILL_WALK_SPEED = 40f
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

    /** True for the one update in which a foot came down (for footstep sounds). */
    var stepped = false
        private set

    // ------------------------------------------------------------------ state

    /** The cross-fading pose weights. */
    val blender = PoseBlender()

    /** The animation's own clock (seconds), offset per figure so a crowd never moves in step. */
    var clock = (seed and 63) * 1.7f
        private set

    private var started = false

    /**
     * The stride cycle (radians), advanced by the ground covered so a planted foot stays put: a
     * foot lands at every multiple of π. It only advances while the figure moves.
     */
    var phase = 0f
        private set

    /** How far the legs are eased on from [phase] to stand under the body when the figure stops (radians). */
    private var plant = 0f

    /** Ground speed (world units a second), smoothed, and how much of a walk (0..1) and of a run it makes. */
    var speed = 0f
        private set
    private var gait = 0f
    private var run = 0f

    private var px = 0f
    private var pz = 0f
    private var vxs = 0f
    private var vzs = 0f
    private var yawRate = 0f
    private var bank = 0f
    private val leanSpring = Spring()

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
     * is facing (the visual facing follows it smoothly) and [pose] what it is doing. Its speed
     * is worked out from how far it moved, unless the owner knows better and passes the
     * velocity ([vx], [vz]); a move too big to be walking (a shove, a teleport) is ignored.
     */
    fun update(dt: Float, x: Float, z: Float, yaw: Float, pose: Pose, yawGoal: Float = yaw, vx: Float = Float.NaN, vz: Float = Float.NaN) {
        if (!(dt > 0f)) return
        val h = if (dt > MAX_DT) MAX_DT else dt
        if (!started) {
            started = true
            this.yaw = yaw
            px = x
            pz = z
            blender.snap(pose)
            seat.reset(seatTarget(pose))
        }
        clock += h

        // --- how the figure moved this step
        val rvx: Float
        val rvz: Float
        var dist: Float
        if (vx.isNaN() || vz.isNaN()) {
            val dx = x - px
            val dz = z - pz
            dist = hypot(dx, dz)
            if (dist > MAX_STEP_SPEED * h) {
                // Shoved or moved by something else: carry on as we were.
                rvx = vxs
                rvz = vzs
                dist = 0f
            } else {
                rvx = dx / h
                rvz = dz / h
            }
        } else {
            rvx = vx
            rvz = vz
            dist = hypot(vx, vz) * h
        }
        px = x
        pz = z
        val kv = AnimMath.k(VEL_SMOOTH, h)
        val ax = ((rvx - vxs) * kv / h).coerceIn(-ACCEL_CAP, ACCEL_CAP)
        val az = ((rvz - vzs) * kv / h).coerceIn(-ACCEL_CAP, ACCEL_CAP)
        vxs += (rvx - vxs) * kv
        vzs += (rvz - vzs) * kv
        speed = hypot(vxs, vzs)

        // --- facing, smoothed; how fast it is turning
        val turn = (AnimMath.wrap(yaw - this.yaw) * AnimMath.k(YAW_RATE, h)).coerceIn(-TURN_MAX * h, TURN_MAX * h)
        this.yaw += turn
        yawRate += (turn / h - yawRate) * AnimMath.k(YAW_RATE_SMOOTH, h)
        // The acceleration along the way the figure faces (forwards is positive).
        val af = ax * sin(this.yaw) + az * cos(this.yaw)

        // --- pose
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

        // --- gait: the stride advances with the ground covered, and settles when the figure stops
        gait += (AnimMath.smooth(speed / GAIT_FULL_SPEED) - gait) * AnimMath.k(GAIT_RATE, h)
        run += (Gait.runBlend(speed) - run) * AnimMath.k(RUN_RATE, h)
        stepped = false
        if (speed > STOP_SPEED) {
            if (dist > 0f) {
                val before = floor(phase / PI_F)
                phase += Gait.phaseFor(dist, Gait.amplitude(speed))
                stepped = floor(phase / PI_F) != before
                if (phase > PHASE_WRAP) phase -= PHASE_WRAP
            }
            plant += (0f - plant) * AnimMath.k(PLANT_RATE, h)
        } else {
            plant += (Gait.restPhase(phase) - phase - plant) * AnimMath.k(PLANT_RATE, h)
        }

        // --- lean into speeding up, out of stopping, and banking into turns
        val walkLean = LEAN_SHUFFLE + (LEAN_WALK - LEAN_SHUFFLE) * AnimMath.smooth(speed / Gait.WALK_TOP)
        val leanNow = walkLean + (LEAN_RUN - walkLean) * run
        val leanTarget = (leanNow * gait + LEAN_ACCEL * af).coerceIn(-LEAN_MAX, LEAN_MAX)
        leanSpring.step(leanTarget, h, LEAN_OMEGA, if (reduceMotion) 1f else LEAN_ZETA)
        val bankTarget = (-yawRate * TURN_BANK * (speed / BANK_SPEED).coerceAtMost(1f)).coerceIn(-BANK_MAX, BANK_MAX)
        bank += (bankTarget - bank) * AnimMath.k(BANK_RATE, h)

        solve()
    }

    /**
     * Sets the pose at once, for a still picture (the prize counter, the photo booth): [time]
     * drives the idle motion, [walkPhase] the stride (0 has the legs under the body), and a
     * walking pose gets a walking gait.
     */
    fun setStatic(pose: Pose, time: Float, walkPhase: Float, yaw: Float) {
        started = true
        this.yaw = yaw
        clock = time
        blender.snap(pose)
        val walking = pose == Pose.WALK || pose == Pose.CARRY
        speed = if (walking) STILL_WALK_SPEED else 0f
        gait = if (walking) 1f else 0f
        run = 0f
        phase = walkPhase + PI_F / 2f
        plant = 0f
        yawRate = 0f
        bank = 0f
        leanSpring.reset(if (walking) LEAN_WALK else 0f)
        seat.reset(seatTarget(pose))
        cheerAge = time
        waveAge = time
        clapAge = time
        stepped = false
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

        // Legs walk unless seated; the body sits on whichever foot is lower.
        val rig = phase + plant
        val c = cos(rig)
        val amp = Gait.amplitude(speed)
        val seated = seat.x.coerceIn(0f, 1f)
        val gaitK = gait * (1f - seated)
        val liftMax = Gait.lift(amp, run)
        var low = Float.MAX_VALUE
        for (s in 0..1) {
            val side = SIDES[s]
            val pitch = Gait.legPitch(side, rig, amp) * gaitK
            val lift = Gait.legLift(side, rig, liftMax) * gaitK
            legPitch[s] = pitch - SEAT_LEG * seat.x
            legLift[s] = lift
            low = minOf(low, Gait.footHeight(pitch, lift))
        }
        val dip = (Gait.CONTACT + (Gait.CONTACT_RUN - Gait.CONTACT) * run) * (if (reduceMotion) REDUCED_DIP else 1f)
        // A run also rises at the top of each stride, when the legs pass under the body.
        val hop = if (reduceMotion) 0f else Gait.RUN_HOP * run * (1f - c * c) * gaitK

        // Arms swing against the legs (a hand holding something keeps still); a run carries them forward.
        val armAmp = minOf(ARM_SWING_MAX, amp * (ARM_PER_AMP + ARM_RUN_BONUS * run))
        for (s in 0..1) {
            val side = SIDES[s]
            val free = accSwing[s] * gait
            armPitch[s] = accArmP[s] + free * (-side * armAmp * c - RUN_ARM_FORWARD * run)
            armRoll[s] = accArmR[s] + free * (-side * RUN_ARM_IN * run)
        }

        rootY = accRoot - low * dip * gaitK + hop - SEAT_DROP * seat.x
        itemAmount = accItem.coerceIn(0f, 1f)

        // Spine: shoulders twist against the hips, the torso rocks over the planted foot, leans and banks.
        twist = -TWIST_WALK * (1f + TWIST_RUN_BONUS * run) * c * gait
        lean = leanSpring.x
        leanRoll = bank + GAIT_ROLL * sin(rig) * gait
        headPitch = -HEAD_LEVEL * lean
    }

    /** Adds pose [p]'s share [wi] of every joint. */
    private fun addPose(p: Pose, wi: Float) {
        val t = clock
        when (p) {
            Pose.STAND -> {
                accArmP[0] += wi * sin(t * 1.3f - 1f) * 0.05f; accArmR[0] += wi * -0.1f
                accArmP[1] += wi * sin(t * 1.3f + 1f) * 0.05f; accArmR[1] += wi * 0.1f
                accSwing[0] += wi; accSwing[1] += wi
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
                accSwing[0] += wi
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

/** Left and right (-1, +1) for per-side limbs; index 0 is the left arm and leg. */
private val SIDES = floatArrayOf(-1f, 1f)
