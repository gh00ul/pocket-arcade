package com.pocketarcade.hub

/**
 * A kid's occasional gestures, on top of what they are doing: waving at the player when they
 * stroll past, and cheering or clapping when someone buys a prize. Pure and seeded from the
 * kid's own seed (never the simulation's random source), so the gestures are repeatable and can
 * never change where a kid goes or when: the owner only swaps the [Pose] it shows for a moment.
 */
class Emotes(private val seed: Int) {
    companion object {
        /** How long a wave lasts (seconds). */
        const val WAVE_TIME = 1.8f

        /** Chance that a kid waves at a passing player on any one encounter, and the shortest gap between waves (seconds). */
        const val WAVE_CHANCE = 0.35f
        const val WAVE_COOLDOWN = 20f

        /** After deciding to wave, the kid waits this long (plus up to [WAVE_DELAY_SPREAD]) so it never looks scripted. */
        const val WAVE_DELAY = 0.35f
        const val WAVE_DELAY_SPREAD = 0.5f

        /** How long a celebration lasts (seconds). */
        const val CELEBRATE_TIME = 1.7f

        /** How far apart a crowd's reactions are spread (seconds), on top of how far they are from the counter. */
        const val CELEBRATE_SPREAD = 0.45f
    }

    private var waveLeft = 0f
    private var waveDelay = -1f
    private var cooldown = WAVE_COOLDOWN * 0.5f * AnimMath.unit(seed, 0, 21)
    private var wasClose = false
    private var encounters = 0

    private var celebrateDelay = 0f
    private var celebrateLeft = 0f
    private var clapping = false

    /** True while this kid is mid-gesture. */
    val active: Boolean get() = waveLeft > 0f || celebrateLeft > 0f || celebrateDelay > 0f

    /**
     * Sets this kid celebrating after [delay] seconds (plus a little of their own): a cheer, or a
     * clap, which is all a seated kid can do. Ignored if they are already celebrating.
     */
    fun celebrate(delay: Float, seated: Boolean) {
        if (celebrateLeft > 0f || celebrateDelay > 0f) return
        celebrateDelay = delay + CELEBRATE_SPREAD * AnimMath.unit(seed, encounters, 22) + 0.001f
        celebrateLeft = CELEBRATE_TIME
        clapping = seated || AnimMath.unit(seed, encounters, 23) < 0.5f
    }

    /**
     * Steps the gestures by [dt]. [close] is whether the player has been in front of the kid and
     * near for long enough to be noticed; [standing] whether the kid is free to wave (standing
     * about, hands empty); [seated] and [walking] what else they might be doing. Returns the pose to
     * show in place of the kid's own, or null to carry on as usual.
     */
    fun update(dt: Float, close: Boolean, standing: Boolean, seated: Boolean, walking: Boolean): Pose? {
        cooldown -= dt
        // A wave is decided when the player first comes into view, once per encounter.
        if (close && !wasClose) {
            val n = encounters++
            if (standing && cooldown <= 0f && waveLeft <= 0f && waveDelay < 0f && AnimMath.unit(seed, n, 11) < WAVE_CHANCE) {
                waveDelay = WAVE_DELAY + WAVE_DELAY_SPREAD * AnimMath.unit(seed, n, 12)
            }
        }
        wasClose = close
        if (waveDelay >= 0f) {
            waveDelay -= dt
            // Set off walking, or the player has gone: the wave is off.
            if (!standing || !close) waveDelay = -1f
            else if (waveDelay < 0f) {
                waveLeft = WAVE_TIME
                cooldown = WAVE_COOLDOWN
            }
        }
        if (waveLeft > 0f) {
            waveLeft -= dt
            if (!standing) waveLeft = 0f
        }

        if (celebrateDelay > 0f) {
            celebrateDelay -= dt
        } else if (celebrateLeft > 0f) {
            celebrateLeft -= dt
        }
        // A celebration wins over a wave; walking kids are only passing, and get on with it.
        if (celebrateDelay <= 0f && celebrateLeft > 0f && !walking) {
            return if (clapping || seated) Pose.CLAP else Pose.CHEER
        }
        if (waveLeft > 0f) return Pose.WAVE
        return null
    }
}
