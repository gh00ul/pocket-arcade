package com.pocketarcade.hub

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * The maths of walking and running: how far a stride swings, how far the cycle advances per unit
 * of ground covered (so a planted foot stays put instead of skating), and where the feet are.
 * Pure functions and constants; [FigureAnim] does the stateful part.
 *
 * Conventions: [FigureAnim.phase] is the stride cycle in radians, and a foot lands (both legs at
 * their furthest reach, the body at its lowest) at every multiple of π; the legs pass under the
 * body at π/2 + kπ. A leg's pitch is negative when the foot is forward.
 */
object Gait {
    private const val PI_F = PI.toFloat()

    /** Hip to sole (figure units): what the body drops by as a straight leg leans out from vertical. */
    const val LEG_LEN = 14f

    /** The kids' fastest walk (world units a second); above it the stride opens out into a run. */
    const val WALK_TOP = 46f

    /** Speeds (world units a second) where a run starts to take over from a walk and where it is complete. */
    const val RUN_FROM = 54f
    const val RUN_FULL = 78f

    /** Leg swing amplitude (radians each way): a shuffle, a full walking stride and a run. */
    const val AMP_MIN = 0.24f
    const val AMP_WALK = 0.60f
    const val AMP_RUN = 0.76f

    /**
     * How square the leg's back-and-forth is: 0 is a sine, 1 a triangle wave. A triangle moves the
     * planted foot back at a constant speed (so it doesn't slide); a touch less keeps the ends soft.
     */
    const val SHAPE_K = 0.92f

    /** How high a swinging foot clears the ground (figure units) in a full walking stride and in a run. */
    const val LIFT_WALK = 1.1f
    const val LIFT_RUN = 2.0f

    /**
     * How much of the body's geometric dip (straight legs leaning out lower the hips) is kept in a
     * walk, and how much less of it in a run (where the feet leave the ground at the ends of the
     * stride). All of it would make a compass walker; none leaves the feet floating in a walk.
     */
    const val CONTACT = 0.75f
    const val CONTACT_RUN = 0.5f

    /** Extra rise at the top of a run stride (the flight), figure units. */
    const val RUN_HOP = 1.6f

    private val asinK = asin(SHAPE_K)

    /** 0 walking .. 1 running, for a ground speed of [speed]. */
    fun runBlend(speed: Float): Float = AnimMath.smooth((speed - RUN_FROM) / (RUN_FULL - RUN_FROM))

    /** Leg swing amplitude (radians) at [speed]: grows through the walk, then on into the run. */
    fun amplitude(speed: Float): Float {
        val walk = AMP_MIN + (AMP_WALK - AMP_MIN) * AnimMath.smooth(speed / WALK_TOP)
        val r = runBlend(speed)
        return walk + (AMP_RUN - walk) * r
    }

    /** How high a swinging foot clears the ground at swing amplitude [amp] and run blend [run]: a shuffle barely lifts it. */
    fun lift(amp: Float, run: Float): Float {
        val walk = LIFT_WALK * (0.4f + 0.6f * ((amp - AMP_MIN) / (AMP_WALK - AMP_MIN)).coerceIn(0f, 1f))
        return walk + (LIFT_RUN - walk) * run
    }

    /** Ground covered by one step (heel strike to the other heel strike) at swing amplitude [amp]. */
    fun stride(amp: Float): Float = 2f * LEG_LEN * sin(amp)

    /** How far the cycle advances (radians) for [dist] of ground at swing amplitude [amp]: π a stride. */
    fun phaseFor(dist: Float, amp: Float): Float = dist * PI_F / stride(amp)

    /**
     * The leg swing's shape: −1..1 for [c], the sine of the leg's swing angle (which is
     * cos of the cycle phase). Nearly linear through the middle, rounding off at the ends.
     */
    fun shape(c: Float): Float = asin((SHAPE_K * c).coerceIn(-SHAPE_K, SHAPE_K)) / asinK

    /** The cycle phase nearest [phase] at which the legs stand under the body (π/2 + kπ). */
    fun restPhase(phase: Float): Float = Math.round((phase - PI_F / 2f) / PI_F) * PI_F + PI_F / 2f

    /** Leg [side]'s (−1 left, +1 right) pitch at cycle [phase] and swing amplitude [amp]. */
    fun legPitch(side: Float, phase: Float, amp: Float): Float = side * amp * shape(cos(phase))

    /** How high leg [side]'s foot is lifted at [phase]: a half sine through its swing, on the ground while it pushes back. */
    fun legLift(side: Float, phase: Float, lift: Float): Float = lift * maxOf(0f, side * sin(phase))

    /** Height of a foot above where a standing one would be: the lean of a straight leg plus its [lift]. */
    fun footHeight(pitch: Float, lift: Float): Float = LEG_LEN * (1f - cos(pitch)) + lift

    /** How far forward of the hip a foot is at [pitch]. */
    fun footForward(pitch: Float): Float = -LEG_LEN * sin(pitch)
}
