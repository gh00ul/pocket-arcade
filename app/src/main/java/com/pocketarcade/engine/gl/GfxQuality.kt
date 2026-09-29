package com.pocketarcade.engine.gl

import com.pocketarcade.engine.r3d.RenderPass
import kotlin.math.max

/**
 * Graphics quality: a tier the player (or a settings row) picks, a frame-rate cap, and the
 * ladder of levers the renderer walks down when the GPU can't keep up. Nothing here is saved
 * and there is no UI yet: whoever wires a setting just assigns [tier] and [frameCap].
 *
 * The levers, from the picture's point of view:
 * - **MSAA** samples (4 / 2 / 0),
 * - **bloom octaves** (4 / 3 / 2),
 * - **floor reflections**: real mirror images where a scene asks for them, cheap streaks, or off,
 * - the **light budget** packed per frame (64 / 32 / 16),
 * - the **render scale** floor, ceiling and boost.
 *
 * Rung 0 of the [LADDER] is exactly how the renderer looked before quality tiers existed, so a
 * device that can hold it (and [Tier.AUTO], the default) looks unchanged.
 */
object GfxQuality {
    /** How the ladder is used. */
    enum class Tier {
        /** Start where the device suggests, drop when frames run long, climb back when there is headroom. */
        AUTO,

        /** Never climb above [BATTERY_TOP]: cooler and lighter, still drops further if it must. */
        BATTERY,

        /** Never trade effects for speed: only the render scale gives way. */
        QUALITY,
    }

    /** What floor reflections may do. */
    enum class Reflections {
        /** As the scene asks: mirror images where it wants them, streaks elsewhere. */
        MIRROR,

        /** Streaks gathered from the previous frame's glow, even where a scene asks for a mirror. */
        STREAKS,
        OFF,
    }

    /** One step of the ladder: every lever at once. */
    class Rung(
        val scaleFloor: Float,
        val scaleCeiling: Float,
        /** How far the scale may go above [scaleCeiling] when the GPU time is known to allow it. */
        val scaleBoost: Float,
        val msaa: Int,
        val bloomOctaves: Int,
        val reflections: Reflections,
        val lights: Int,
    )

    /**
     * From the best picture (0) to the cheapest. Each step gives up something worth a real
     * saving for the least visible loss first: the mirror pass, an octave of glow and half the
     * lights; then multisampling, which is what costs a tiling GPU the most; then the rest.
     */
    internal val LADDER = arrayOf(
        Rung(0.5f, 0.8f, 1.0f, 4, 4, Reflections.MIRROR, RenderPass.MAX_LIGHTS),
        Rung(0.5f, 0.8f, 0.8f, 4, 3, Reflections.STREAKS, 32),
        Rung(0.5f, 0.7f, 0.7f, 2, 3, Reflections.STREAKS, 32),
        Rung(0.45f, 0.7f, 0.7f, 2, 2, Reflections.OFF, 16),
        Rung(0.4f, 0.6f, 0.6f, 0, 2, Reflections.OFF, 16),
    )

    /** The best rung [Tier.BATTERY] may use. */
    internal const val BATTERY_TOP = 2

    /** The display must refresh at least this many times faster than the cap for the cap to bite. */
    internal const val CAP_MIN_RATIO = 1.6f

    /** A capped frame is due this much before a whole interval has passed, to absorb timing jitter. */
    internal const val CAP_SLACK_NS = 3_000_000L

    @Volatile var tier = Tier.AUTO

    /** Frames per second to draw at most: 0 = auto (60), 30 or 60. Rendering only; the simulation is unaffected. */
    @Volatile var frameCap = 0

    /** Set by the surface: the display's refresh rate, and whether the OS calls this a low-RAM device. */
    @Volatile internal var displayHz = 60f
    @Volatile internal var lowRamDevice = false

    /**
     * Whether the GL thread can draw particles inside the picture (see
     * [com.pocketarcade.engine.Particles.recordGl]); if not, they are painted in 2D as before.
     */
    @Volatile internal var glParticles = true

    /**
     * The light budget of the rung the GL thread is on, read by the recorder when it packs the
     * lights (so a cheaper rung also saves the UI thread's work).
     */
    @Volatile internal var lightBudget = RenderPass.MAX_LIGHTS

    /** The cap in force: 30 or 60. */
    fun effectiveCap(): Int = if (frameCap == 30) 30 else 60

    /**
     * Whether a cap does anything on a display refreshing at [hz]: a 90 Hz screen with a 60 fps
     * cap would only be pushed to 45 by the vsync grid, so it is left to run.
     */
    internal fun capApplies(cap: Int, hz: Float): Boolean = hz >= cap * CAP_MIN_RATIO

    /** The best rung [tier] may use. */
    internal fun topRung(tier: Tier): Int = if (tier == Tier.BATTERY) BATTERY_TOP else 0

    /** The cheapest rung [tier] may fall to. */
    internal fun bottomRung(tier: Tier): Int = if (tier == Tier.QUALITY) 0 else LADDER.lastIndex

    /** The rung to start on for [tier] on a device whose own suggestion is [deviceRung]. */
    internal fun startRung(tier: Tier, deviceRung: Int): Int = when (tier) {
        Tier.AUTO -> deviceRung
        Tier.BATTERY -> max(BATTERY_TOP, deviceRung)
        Tier.QUALITY -> 0
    }.coerceIn(topRung(tier), bottomRung(tier))

    private val SOFTWARE = arrayOf("swiftshader", "llvmpipe", "softpipe", "software", "microsoft basic")
    private val OLD_GPU = Regex(
        "mali-4\\d\\d|mali-t[0-7]\\d\\d|adreno \\(tm\\) ?[2-4]\\d\\d\\b|powervr sgx|powervr rogue ge8|vivante|tegra [234]\\b",
    )

    /**
     * The rung a device should start on, from what the driver says about itself: a software
     * renderer or an old GPU starts low, a phone the OS calls low-RAM starts lower than a good
     * one. Only a starting guess; the pacer corrects it either way within seconds.
     */
    internal fun deviceRung(glRenderer: String, lowRam: Boolean): Int {
        val r = glRenderer.lowercase()
        return when {
            SOFTWARE.any { r.contains(it) } -> LADDER.lastIndex
            lowRam || OLD_GPU.containsMatchIn(r) -> 2
            else -> 0
        }
    }
}

/**
 * Lets frames through at the [GfxQuality] cap. Pure logic, fed the time: the UI thread asks
 * before recording a frame the GL thread would only throw away, and the GL thread asks before
 * drawing one (for scenes that do not ask on the UI side).
 *
 * A frame is due once an interval (less [GfxQuality.CAP_SLACK_NS]) has passed since the last
 * one taken. On a 120 Hz display that lets every second frame through at a 60 fps cap and every
 * fourth at 30.
 */
internal class FrameGate {
    private var lastNs = 0L

    /** Nanoseconds until a frame is due (0 if it is due now). Takes nothing. */
    fun waitNs(nowNs: Long, cap: Int, displayHz: Float): Long {
        if (lastNs == 0L || !GfxQuality.capApplies(cap, displayHz)) return 0L
        val gap = 1_000_000_000L / cap - GfxQuality.CAP_SLACK_NS
        val left = gap - (nowNs - lastNs)
        // A clock that stepped back must not hold a frame for longer than one interval.
        return left.coerceIn(0L, gap)
    }

    /** Counts a frame as taken at [nowNs]. */
    fun take(nowNs: Long) {
        lastNs = nowNs
    }

    /** Whether a frame is due at [nowNs]; if so it is taken. */
    fun due(nowNs: Long, cap: Int, displayHz: Float): Boolean {
        if (waitNs(nowNs, cap, displayHz) > 0L) return false
        take(nowNs)
        return true
    }
}

/** A monotonic clock in nanoseconds; a function interface so reading it never boxes a Long. */
internal fun interface NanoClock {
    fun nanos(): Long
}
