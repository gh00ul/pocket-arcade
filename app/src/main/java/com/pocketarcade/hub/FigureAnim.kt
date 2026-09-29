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

        /** The hard limits of an arm (radians): straight up and a little behind the back, and how far it can splay. */
        private const val ARM_PITCH_MIN = -3.05f
        private const val ARM_PITCH_MAX = 1.25f
        private const val ARM_ROLL_MAX = 0.85f

        /** What a still picture assumes for the ground speed of a walking pose. */
        private const val STILL_WALK_SPEED = 40f

        // ---- looking

        /**
         * The head turns at most this far (radians) from the body's facing. A target up to [LOOK_FULL]
         * round from the facing is looked at as far as that allows; beyond it the gaze eases off, and
         * past [LOOK_CUTOFF] (well behind the figure) it is ignored.
         */
        private const val HEAD_YAW_MAX = 1.0f
        private const val LOOK_FULL = 1.75f
        private const val LOOK_CUTOFF = 2.5f

        /** How far the head tips up and down (radians) to follow a target's height. */
        private const val HEAD_UP = 0.35f
        private const val HEAD_DOWN = 0.45f

        /** The shoulders turn this share of the head's turn, so a look starts in the body and not only the neck. */
        private const val GAZE_SHARE = 0.28f

        /** The gaze fades in and out at this rate (per second); the head's spring is [HEAD_OMEGA] and [HEAD_ZETA] (almost no overshoot). */
        private const val GAZE_RATE = 7f
        private const val HEAD_OMEGA = 12f
        private const val HEAD_ZETA = 0.85f

        /** Walking or turning, the head leads the body into the turn: this share of the turn still to do, up to [LEAD_MAX] radians. */
        private const val LEAD_GAIN = 0.55f
        private const val LEAD_MAX = 0.55f

        // ---- idle life

        /** Breathing: seconds a breath takes, how far the chest swells (a fraction) and lifts the shoulders (figure units). */
        private const val BREATH_PERIOD = 3.8f
        private const val BREATH_SWELL = 0.018f
        private const val BREATH_LIFT = 0.22f

        /** Standing still is never quite still: a slow sway (figure units, radians of roll) and a wandering head (radians). */
        private const val SWAY_PERIOD = 7f
        private const val SWAY_X = 0.25f
        private const val SWAY_ROLL = 0.012f
        private const val DRIFT_YAW = 0.03f

        /** Seconds between fidgets while standing about (a fidget is a glance, a weight shift or a foot tap). */
        private const val FIDGET_MIN = 3.5f
        private const val FIDGET_MAX = 8.5f

        /** A glance: how far the head turns away (radians) and for how long (seconds). */
        private const val GLANCE_YAW = 0.6f
        private const val GLANCE_TIME = 1.4f

        /** A weight shift: sway (figure units), the opposite tilt of the shoulders and the free leg's step forward (radians), and its length. */
        private const val SHIFT_SWAY = 0.9f
        private const val SHIFT_ROLL = 0.05f
        private const val SHIFT_LEG = 0.10f
        private const val SHIFT_TIME = 1.9f

        /** A foot tap: the toe's lift (figure units), the leg's step forward (radians), taps per second (radians) and its length. */
        private const val TAP_LIFT = 0.35f
        private const val TAP_PITCH = 0.07f
        private const val TAP_RATE = 14f
        private const val TAP_TIME = 1.5f

        /** Seconds between blinks, how long one takes, and how often one is a quick double. */
        private const val BLINK_MIN = 2.2f
        private const val BLINK_MAX = 5.6f
        private const val BLINK_TIME = 0.14f
        private const val DOUBLE_BLINK = 0.15f

        // ---- follow-through

        /** The arms settle onto each pose with a little overshoot (frequency in rad/s, damping ratio). */
        private const val ARM_OMEGA = 18f
        private const val ARM_ZETA = 0.6f

        /**
         * Arms trail behind a body that speeds up or turns: a spring pushed by the acceleration
         * (radians a second squared per world unit a second squared, forwards and sideways), capped
         * at [ARM_LAG_MAX]. A held arm only trails this much of the way.
         */
        private const val ARM_LAG_OMEGA = 13f
        private const val ARM_LAG_ZETA = 0.42f
        private const val ARM_LAG_PITCH = 0.05f
        private const val ARM_LAG_ROLL = 0.03f
        private const val ARM_LAG_MAX = 0.4f
        private const val ARM_HELD_LAG = 0.3f

        /**
         * A hat sits on the head on springs: it tips back when the body speeds up and to the side
         * when it turns ([HAT_PITCH_GAIN], [HAT_ROLL_GAIN] as for the arms, never past [HAT_MAX]
         * radians), and rides up and down a little with hops ([HAT_LIFT_MAX] figure units).
         */
        private const val HAT_OMEGA = 17f
        private const val HAT_ZETA = 0.3f
        private const val HAT_PITCH_GAIN = 0.045f
        private const val HAT_ROLL_GAIN = 0.03f
        private const val HAT_MAX = 0.16f
        private const val HAT_LIFT_OMEGA = 22f
        private const val HAT_LIFT_ZETA = 0.35f
        private const val HAT_LIFT_MAX = 0.9f

        /** The strongest vertical acceleration (figure units a second squared) that shakes a hat or a ponytail. */
        private const val VERT_CAP = 600f

        /** A ponytail swings on a looser spring, pushed by acceleration and by the head turning, never past [TAIL_MAX] radians. */
        private const val TAIL_OMEGA = 10f
        private const val TAIL_ZETA = 0.28f
        private const val TAIL_GAIN = 0.06f
        private const val TAIL_TURN_GAIN = 2.5f
        private const val TAIL_MAX = 0.6f

        /** With reduce motion on, the follow-through keeps this share of its push (and the springs are critically damped, so there is no overshoot). */
        private const val REDUCED_FOLLOW = 0.35f

        /** Sitting down and standing up tip the body forwards while the seat is moving: radians per unit of seat speed, up to [SEAT_LEAN_MAX]. */
        private const val SEAT_LEAN = 0.035f
        private const val SEAT_LEAN_MAX = 0.25f

        /**
         * A cheer is preceded by a short crouch: the body dips, the arms sweep back and the torso tips
         * forwards for [CHEER_WINDUP] seconds before the arms fly up.
         */
        private const val CHEER_WINDUP = 0.11f
        private const val CHEER_DIP = 1.6f
        private const val CHEER_ARM_BACK = 0.55f
        private const val CHEER_LEAN = 0.14f

        /** A wave: how far the hand swings from side to side (radians) and how fast (radians a second). */
        private const val WAVE_SWING = 0.32f
        private const val WAVE_RATE = 11f

        /** A clap: the arms roll in by [CLAP_MID] plus or minus [CLAP_SWING] (radians), hands meeting at the most, at [CLAP_RATE] radians a second. */
        private const val CLAP_MID = 0.30f
        private const val CLAP_SWING = 0.26f
        private const val CLAP_RATE = 12f

        private const val FIDGET_NONE = 0
        private const val FIDGET_GLANCE = 1
        private const val FIDGET_SHIFT = 2
        private const val FIDGET_TAP = 3
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

    /** How many fidgets this figure has started (for tests and tuning). */
    val fidgetCount: Int get() = fidgetN

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
    private var accHeadYaw = 0f
    private var accHeadPitch = 0f
    private var accHeadRoll = 0f
    private var accLean = 0f

    // Gaze: a target set with [look] for the next update, how strongly it is followed, and the head's springs.
    private var lookOn = false
    private var lookX = 0f
    private var lookZ = 0f
    private var lookY = 0f
    private var gaze = 0f
    private val headYawSpring = Spring()
    private val headPitchSpring = Spring()
    private val headRollSpring = Spring()

    // Follow-through: springs on the arms (settling onto a pose, and trailing behind acceleration), the hat and a ponytail.
    private val armPoseP = arrayOf(Spring(), Spring())
    private val armPoseR = arrayOf(Spring(), Spring())
    private val armLagP = arrayOf(Spring(), Spring())
    private val armLagR = arrayOf(Spring(), Spring())
    private val hatPitchSpring = Spring()
    private val hatRollSpring = Spring()
    private val hatLiftSpring = Spring()
    private val tailPitchSpring = Spring()
    private val tailRollSpring = Spring()
    private var prevRootY = 0f
    private var prevRootVy = 0f

    /** Seconds left of a cheer wind-up (negative when not winding up) and how much of it shows (0..1). */
    private var windLeft = -1f
    private var windEnv = 0f

    // Fidgets and blinks (seeded from [seed], so the same figure fidgets the same way every run).
    private var fidgetKind = FIDGET_NONE
    private var fidgetT = 0f
    private var fidgetDur = 0f
    private var fidgetDir = 1f
    private var fidgetN = 0
    private var fidgetWait = FIDGET_MIN * (0.3f + 0.7f * AnimMath.unit(seed, 0, 5))
    private var blinkT = -1f
    private var blinkWait = BLINK_MIN + (BLINK_MAX - BLINK_MIN) * AnimMath.unit(seed, 0, 1)
    private var blinkN = 1

    // What the fidget is doing to each joint this step (already weighted by how idle the figure is).
    private var fidYaw = 0f
    private var fidPitch = 0f
    private var fidRoll = 0f
    private var fidSway = 0f
    private var fidBank = 0f
    private val fidLeg = FloatArray(2)
    private val fidLift = FloatArray(2)

    /**
     * Asks the figure to look at the world point ([x], [y] up, [z]) for the coming update: the
     * head turns toward it (within what a neck can do), the shoulders follow a little, and the
     * gaze eases off again once this stops being called. A point behind the figure is ignored.
     */
    fun look(x: Float, z: Float, y: Float = Figure.HEAD_Y * scale) {
        lookOn = true
        lookX = x
        lookZ = z
        lookY = y
    }

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
        // And sideways (along the figure own +x).
        val al = ax * cos(this.yaw) - az * sin(this.yaw)

        // --- pose
        val shown = windUp(pose, h)
        val was = blender.target
        blender.set(shown)
        if (blender.target != was) {
            when (shown) {
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
        seat.step(blender.weight(Pose.SIT) + blender.weight(Pose.SIP), h, SEAT_OMEGA, if (reduceMotion) 1f else SEAT_ZETA)
        accumulatePose()
        // Arms settle onto their poses with a little overshoot.
        val armZeta = if (reduceMotion) 1f else ARM_ZETA
        for (s in 0..1) {
            armPoseP[s].step(accArmP[s], h, ARM_OMEGA, armZeta)
            armPoseR[s].step(accArmR[s], h, ARM_OMEGA, armZeta)
        }

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

        stepFidgets(h)
        stepGaze(x, z, yawGoal, h)
        stepBlink(h)
        stepFollowThrough(af, al, h)
        solve()
    }

    /**
     * Holds a cheer back for a beat: asked for a [pose] of CHEER, the figure keeps the pose it was
     * in for [CHEER_WINDUP] seconds while it crouches ([windEnv] carries the crouch), then cheers.
     * Returns the pose to blend to now.
     */
    private fun windUp(pose: Pose, h: Float): Pose {
        if (reduceMotion || pose != Pose.CHEER || blender.target == Pose.CHEER) {
            windLeft = -1f
            return pose
        }
        if (windLeft < 0f) windLeft = CHEER_WINDUP
        windLeft -= h
        return if (windLeft > 0f) blender.target else pose
    }

    /**
     * Springs that lag behind the body: a hat and a ponytail that trail behind speeding up and
     * turning and hops, arms that trail behind it. [af] and [al] are the accelerations along and
     * across the way the figure faces.
     */
    private fun stepFollowThrough(af: Float, al: Float, h: Float) {
        val calm = if (reduceMotion) REDUCED_FOLLOW else 1f
        // How hard the body is being thrown up and down, from its own height over the last steps.
        val v = (rootY - prevRootY) / h
        val ay = ((v - prevRootVy) / h).coerceIn(-VERT_CAP, VERT_CAP)
        prevRootY = rootY
        prevRootVy = v
        // The wind-up crouch eases in and out.
        val windTarget = if (windLeft > 0f) AnimMath.bell(1f - windLeft / CHEER_WINDUP) else 0f
        windEnv += (windTarget - windEnv) * AnimMath.k(30f, h)

        // A hat lags: forward acceleration tips its top back (negative pitch), sideways acceleration to the other side.
        val hatZ = if (reduceMotion) 1f else HAT_ZETA
        hatPitchSpring.drive(0f, -HAT_PITCH_GAIN * af * calm, h, HAT_OMEGA, hatZ)
        hatRollSpring.drive(0f, HAT_ROLL_GAIN * al * calm, h, HAT_OMEGA, hatZ)
        hatLiftSpring.drive(0f, -ay * calm, h, HAT_LIFT_OMEGA, if (reduceMotion) 1f else HAT_LIFT_ZETA)
        // A ponytail hangs from the back of the head: speeding up swings its end back, sideways
        // acceleration or the head turning swings it the other way.
        val tailZ = if (reduceMotion) 1f else TAIL_ZETA
        tailPitchSpring.drive(0f, TAIL_GAIN * af * calm, h, TAIL_OMEGA, tailZ)
        tailRollSpring.drive(0f, (-TAIL_GAIN * al - TAIL_TURN_GAIN * headYawSpring.v) * calm, h, TAIL_OMEGA, tailZ)
        // Arms trail behind: forward acceleration swings them back, sideways acceleration across.
        val armZ = if (reduceMotion) 1f else ARM_LAG_ZETA
        for (s in 0..1) {
            // The two arms springs differ a touch, so they never flap in perfect step.
            val omega = ARM_LAG_OMEGA * (1f + 0.04f * (2 * s - 1))
            armLagP[s].drive(0f, ARM_LAG_PITCH * af * calm, h, omega, armZ)
            armLagR[s].drive(0f, -ARM_LAG_ROLL * al * calm, h, omega, armZ)
        }
    }

    /**
     * Idle fidgets: while the figure stands easy, every few seconds it glances away, shifts its
     * weight or taps a foot. Timed and chosen from the seed, and weighted by how idle it is, so
     * setting off or sitting down fades one out instead of cutting it.
     */
    private fun stepFidgets(h: Float) {
        val idle = blender.weight(Pose.STAND) + blender.weight(Pose.HOLD)
        val still = (1f - gait) * (1f - seat.x.coerceIn(0f, 1f))
        fidYaw = 0f; fidPitch = 0f; fidRoll = 0f; fidSway = 0f; fidBank = 0f
        fidLeg[0] = 0f; fidLeg[1] = 0f; fidLift[0] = 0f; fidLift[1] = 0f
        if (fidgetKind == FIDGET_NONE) {
            if (idle > 0.9f && still > 0.95f) fidgetWait -= h
            if (fidgetWait <= 0f) {
                val n = fidgetN++
                val r = AnimMath.unit(seed, n, 3)
                fidgetKind = if (r < 0.45f) FIDGET_GLANCE else if (r < 0.75f) FIDGET_SHIFT else FIDGET_TAP
                fidgetDur = when (fidgetKind) {
                    FIDGET_GLANCE -> GLANCE_TIME
                    FIDGET_SHIFT -> SHIFT_TIME
                    else -> TAP_TIME
                }
                fidgetDir = if (AnimMath.unit(seed, n, 4) < 0.5f) -1f else 1f
                fidgetT = 0f
            }
        } else {
            fidgetT += h
            if (fidgetT >= fidgetDur) {
                fidgetKind = FIDGET_NONE
                fidgetWait = FIDGET_MIN + (FIDGET_MAX - FIDGET_MIN) * AnimMath.unit(seed, fidgetN, 5)
            }
        }
        val calm = idle * still
        if (fidgetKind != FIDGET_NONE) {
            // Up quickly, held, and back down: a raised window over the fidget's length.
            val e = AnimMath.smooth(fidgetT / 0.25f) * AnimMath.smooth((fidgetDur - fidgetT) / 0.35f) * calm
            val d = fidgetDir
            when (fidgetKind) {
                FIDGET_GLANCE -> {
                    fidYaw = d * GLANCE_YAW * e
                    fidPitch = -0.06f * e
                    fidRoll = d * 0.05f * e
                }
                FIDGET_SHIFT -> {
                    fidSway = d * SHIFT_SWAY * e
                    fidBank = d * SHIFT_ROLL * e
                    // The free leg (opposite the weight-bearing side) steps out a little.
                    fidLeg[if (d > 0f) 0 else 1] = -SHIFT_LEG * e
                }
                else -> {
                    val tap = sin(fidgetT * TAP_RATE)
                    val leg = if (d > 0f) 1 else 0
                    fidLeg[leg] = -TAP_PITCH * e
                    fidLift[leg] = TAP_LIFT * maxOf(0f, tap) * e
                    fidPitch = 0.03f * tap * e
                }
            }
        }
        // Standing still is never quite still: a slow sway and a wandering head.
        val sw = sin(clock * AnimMath.TAU / SWAY_PERIOD)
        fidSway += SWAY_X * sw * calm
        fidBank += SWAY_ROLL * sw * calm
        fidYaw += DRIFT_YAW * sin(clock * 0.7f + seed) * calm
    }

    /**
     * Where the head points: at the [look] target if there is one, otherwise leading the body into
     * whatever turn it is making, plus the fidgets. Springs give the head a soft arrival.
     */
    private fun stepGaze(x: Float, z: Float, yawGoal: Float, h: Float) {
        var targetYaw = 0f
        var targetPitch = 0f
        var strength = 0f
        if (lookOn) {
            val dx = lookX - x
            val dz = lookZ - z
            val d = hypot(dx, dz)
            if (d > 1f) {
                val rel = AnimMath.wrap(kotlin.math.atan2(dx, dz) - this.yaw)
                val away = abs(rel)
                if (away < LOOK_CUTOFF) {
                    targetYaw = rel.coerceIn(-HEAD_YAW_MAX, HEAD_YAW_MAX)
                    targetPitch = kotlin.math.atan2(Figure.HEAD_Y * scale - lookY, d).coerceIn(-HEAD_UP, HEAD_DOWN)
                    // A target well round the side is let go of gradually rather than at a cut-off.
                    strength = 1f - AnimMath.smooth((away - LOOK_FULL) / (LOOK_CUTOFF - LOOK_FULL))
                }
            }
        }
        lookOn = false
        gaze += (strength - gaze) * AnimMath.k(GAZE_RATE, h)
        val lead = (AnimMath.wrap(yawGoal - this.yaw) * LEAD_GAIN).coerceIn(-LEAD_MAX, LEAD_MAX)
        val yawT = gaze * targetYaw + (1f - gaze) * lead + accHeadYaw + fidYaw
        val pitchT = gaze * targetPitch + accHeadPitch + fidPitch
        val rollT = accHeadRoll + fidRoll
        val zeta = if (reduceMotion) 1f else HEAD_ZETA
        headYawSpring.step(yawT.coerceIn(-HEAD_YAW_MAX, HEAD_YAW_MAX), h, HEAD_OMEGA, zeta)
        headPitchSpring.step(pitchT, h, HEAD_OMEGA, zeta)
        headRollSpring.step(rollT, h, HEAD_OMEGA, zeta)
    }

    /** Blinking on a seeded timer: a quick close and slower open, now and then twice in a row. */
    private fun stepBlink(h: Float) {
        if (blinkT >= 0f) {
            blinkT += h
            val p = blinkT / BLINK_TIME
            if (p >= 1f) {
                blinkT = -1f
                val n = blinkN++
                blinkWait = if (AnimMath.unit(seed, n, 2) < DOUBLE_BLINK) 0.16f
                else BLINK_MIN + (BLINK_MAX - BLINK_MIN) * AnimMath.unit(seed, n, 1)
                blink = 0f
            } else {
                blink = if (p < 0.4f) AnimMath.smooth(p / 0.4f) else 1f - AnimMath.smooth((p - 0.4f) / 0.6f)
            }
        } else {
            blink = 0f
            blinkWait -= h
            if (blinkWait <= 0f) blinkT = 0f
        }
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
        // No history: no gaze, no fidget, eyes open, and every spring at rest on its pose.
        lookOn = false
        gaze = 0f
        fidgetKind = FIDGET_NONE
        fidYaw = 0f; fidPitch = 0f; fidRoll = 0f; fidSway = 0f; fidBank = 0f
        fidLeg[0] = 0f; fidLeg[1] = 0f; fidLift[0] = 0f; fidLift[1] = 0f
        blinkT = -1f
        blink = 0f
        accumulatePose()
        windLeft = -1f
        windEnv = 0f
        for (s in 0..1) {
            armPoseP[s].reset(accArmP[s])
            armPoseR[s].reset(accArmR[s])
            armLagP[s].reset()
            armLagR[s].reset()
        }
        hatPitchSpring.reset(); hatRollSpring.reset(); hatLiftSpring.reset()
        tailPitchSpring.reset(); tailRollSpring.reset()
        prevRootY = 0f
        prevRootVy = 0f
        headYawSpring.reset(accHeadYaw)
        headPitchSpring.reset(accHeadPitch)
        headRollSpring.reset(accHeadRoll)
        solve()
    }

    private fun seatTarget(pose: Pose): Float = if (pose == Pose.SIT || pose == Pose.SIP) 1f else 0f

    // ------------------------------------------------------------------ the rig

    /** Sums every pose's share of the arms, the body and the head into the accumulators. */
    private fun accumulatePose() {
        val w = blender.weights
        accArmP[0] = 0f; accArmP[1] = 0f
        accArmR[0] = 0f; accArmR[1] = 0f
        accSwing[0] = 0f; accSwing[1] = 0f
        accRoot = 0f
        accItem = 0f
        accHeadYaw = 0f
        accHeadPitch = 0f
        accHeadRoll = 0f
        accLean = 0f
        for (i in w.indices) {
            val wi = w[i]
            if (wi > 1e-4f) addPose(Pose.ALL[i], wi)
        }
    }

    private fun solve() {
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
            legPitch[s] = pitch - SEAT_LEG * seat.x + fidLeg[s]
            legLift[s] = lift + fidLift[s]
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
            // A held arm trails behind acceleration less than a free one.
            val lag = ARM_HELD_LAG + (1f - ARM_HELD_LAG) * accSwing[s].coerceIn(0f, 1f)
            armPitch[s] = (armPoseP[s].x + CHEER_ARM_BACK * windEnv + lag * armLagP[s].x.coerceIn(-ARM_LAG_MAX, ARM_LAG_MAX) +
                free * (-side * armAmp * c - RUN_ARM_FORWARD * run)).coerceIn(ARM_PITCH_MIN, ARM_PITCH_MAX)
            armRoll[s] = (armPoseR[s].x + lag * armLagR[s].x.coerceIn(-ARM_LAG_MAX, ARM_LAG_MAX) +
                free * (-side * RUN_ARM_IN * run)).coerceIn(-ARM_ROLL_MAX, ARM_ROLL_MAX)
        }

        rootY = accRoot - low * dip * gaitK + hop - SEAT_DROP * seat.x - CHEER_DIP * windEnv
        itemAmount = accItem.coerceIn(0f, 1f)

        // Spine: shoulders twist against the hips, the torso rocks over the planted foot, leans and banks.
        twist = -TWIST_WALK * (1f + TWIST_RUN_BONUS * run) * c * gait + GAZE_SHARE * headYawSpring.x
        lean = leanSpring.x + accLean + CHEER_LEAN * windEnv + minOf(SEAT_LEAN_MAX, SEAT_LEAN * abs(seat.v))
        leanRoll = bank + GAIT_ROLL * sin(rig) * gait + fidBank
        sway = fidSway

        // Hat and ponytail lag behind the head.
        hatPitch = hatPitchSpring.x.coerceIn(-HAT_MAX, HAT_MAX)
        hatRoll = hatRollSpring.x.coerceIn(-HAT_MAX, HAT_MAX)
        hatLift = hatLiftSpring.x.coerceIn(-HAT_LIFT_MAX, HAT_LIFT_MAX)
        tailPitch = tailPitchSpring.x.coerceIn(-TAIL_MAX, TAIL_MAX)
        tailRoll = tailRollSpring.x.coerceIn(-TAIL_MAX, TAIL_MAX)

        // Head: the gaze, less what the spine has already turned, so it stays steady through a stride.
        headYaw = headYawSpring.x - twist
        headPitch = headPitchSpring.x - HEAD_LEVEL * leanSpring.x
        headRoll = headRollSpring.x

        // Breathing, a little less noticeable once the figure is moving.
        val breathing = sin(clock * AnimMath.TAU / BREATH_PERIOD) * (1f - 0.7f * gait)
        breath = BREATH_SWELL * breathing
        breathLift = BREATH_LIFT * breathing
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
                // Leaning in to the game, eyes on the screen and glancing about it.
                accHeadPitch += wi * 0.10f
                accHeadYaw += wi * 0.10f * sin(t * 0.9f)
                accLean += wi * 0.05f
            }
            Pose.CHEER -> {
                accArmP[0] += wi * (-2.6f + sin(cheerAge * 9f - 1f) * 0.25f); accArmR[0] += wi * -0.2f
                accArmP[1] += wi * (-2.6f + sin(cheerAge * 9f + 1f) * 0.25f); accArmR[1] += wi * 0.2f
                accRoot += wi * abs(sin(cheerAge * 9f)) * 2.5f
                accHeadPitch += wi * -0.16f
                accLean += wi * -0.04f
            }
            Pose.SIT -> {
                accArmP[0] += wi * -0.6f; accArmR[0] += wi * -0.1f
                accArmP[1] += wi * -0.6f; accArmR[1] += wi * 0.1f
            }
            Pose.WIPE -> {
                // Circles with a cloth on the counter top.
                accArmP[1] += wi * (-1.15f + sin(t * 7f) * 0.1f); accArmR[1] += wi * (0.05f + cos(t * 7f) * 0.28f)
                accArmP[0] += wi * -0.75f; accArmR[0] += wi * -0.12f
                // Head down, watching the cloth.
                accHeadPitch += wi * 0.28f
                accLean += wi * 0.10f
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
                accHeadPitch += wi * -0.12f * lift
                accItem += wi
            }
            Pose.WAVE -> {
                // The right arm goes up and the hand swings from side to side; the other hangs as when standing.
                accArmP[1] += wi * (-2.75f + 0.05f * sin(waveAge * 3f)); accArmR[1] += wi * (0.15f + WAVE_SWING * sin(waveAge * WAVE_RATE))
                accArmP[0] += wi * sin(t * 1.3f - 1f) * 0.05f; accArmR[0] += wi * -0.1f
                accSwing[0] += wi
                accHeadPitch += wi * -0.05f
                accHeadRoll += wi * 0.07f
            }
            Pose.CLAP -> {
                // Both hands out in front, coming together and apart (inward roll is negative on the right, positive on the left).
                val shut = CLAP_MID + CLAP_SWING * sin(clapAge * CLAP_RATE)
                accArmP[0] += wi * -1.35f; accArmR[0] += wi * shut
                accArmP[1] += wi * -1.35f; accArmR[1] += wi * -shut
                accHeadPitch += wi * -0.1f
                if (!reduceMotion) accRoot += wi * 0.5f * abs(sin(clapAge * CLAP_RATE * 0.5f))
            }
        }
    }
}

/** Left and right (-1, +1) for per-side limbs; index 0 is the left arm and leg. */
private val SIDES = floatArrayOf(-1f, 1f)
