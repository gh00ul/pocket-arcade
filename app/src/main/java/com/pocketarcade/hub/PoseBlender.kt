package com.pocketarcade.hub

/**
 * Cross-fades between [Pose]s so a figure never snaps from one to the next. It keeps a weight
 * for every pose (they always sum to 1); asking for a new pose eases the new one in and every
 * other out over [blendTime], starting from wherever the weights are right now, so a pose
 * changed again half way through blends on smoothly from the middle of the last one.
 *
 * Pure and allocation-free after construction: one blender per figure, updated every step.
 */
class PoseBlender(initial: Pose = Pose.STAND) {
    /** Each pose's current weight, indexed by [Pose.ordinal]. Non-negative, summing to 1. */
    val weights = FloatArray(Pose.COUNT)

    /** The weights when the current blend began (a snapshot, so an interruption starts clean). */
    private val from = FloatArray(Pose.COUNT)

    /** The pose being blended to (or held, once [settled]). */
    var target: Pose = initial
        private set

    private var progress = 1f
    private var duration = blendTime(initial)

    /** True once the target has fully taken over (its weight is 1). */
    val settled: Boolean get() = progress >= 1f

    init {
        weights[initial.ordinal] = 1f
        from[initial.ordinal] = 1f
    }

    /** [pose]'s weight now (0..1). */
    fun weight(pose: Pose): Float = weights[pose.ordinal]

    /** Blends to [pose]; asking for the pose already being blended to changes nothing. */
    fun set(pose: Pose) {
        if (pose == target) return
        // Start from what is on screen now (renormalised, so rounding can never build up).
        var sum = 0f
        for (i in weights.indices) sum += weights[i]
        val inv = if (sum > 1e-6f) 1f / sum else 1f
        for (i in weights.indices) from[i] = weights[i] * inv
        target = pose
        progress = 0f
        duration = blendTime(pose)
    }

    /** Jumps straight to [pose] with no blend (a photo, a respawn). */
    fun snap(pose: Pose) {
        target = pose
        progress = 1f
        duration = blendTime(pose)
        for (i in weights.indices) {
            weights[i] = if (i == pose.ordinal) 1f else 0f
            from[i] = weights[i]
        }
    }

    /** Advances the blend by [dt] seconds. */
    fun update(dt: Float) {
        if (progress >= 1f || !(dt > 0f)) return
        progress = minOf(1f, progress + dt / duration)
        val s = AnimMath.smooth(progress)
        val t = target.ordinal
        for (i in weights.indices) weights[i] = from[i] * (1f - s) + if (i == t) s else 0f
    }

    companion object {
        /** Shortest and longest blends (seconds): quick enough to feel responsive, slow enough not to pop. */
        const val BLEND_MIN = 0.12f
        const val BLEND_MAX = 0.22f

        /**
         * How long blending *into* [pose] takes. Sharp poses (a cheer, a clap) come in fast, settling
         * ones (sitting down, standing easy) take a little longer.
         */
        fun blendTime(pose: Pose): Float = when (pose) {
            Pose.CLAP -> 0.12f
            Pose.CHEER, Pose.WAVE -> 0.14f
            Pose.WALK, Pose.CARRY -> 0.16f
            Pose.PLAY, Pose.WIPE, Pose.HOLD -> 0.18f
            Pose.STAND, Pose.SIP -> 0.20f
            Pose.SIT -> 0.22f
        }
    }
}
