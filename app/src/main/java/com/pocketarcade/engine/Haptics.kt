package com.pocketarcade.engine

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt

/**
 * Short vibration patterns for hits, bonks and wins, rate-limited so bursts don't smear together.
 * A null [vibrator] (no hardware, or headless tests) turns every call into a no-op.
 *
 * On phones with a haptic engine (API 30+ that supports the primitives) the patterns are built from
 * `VibrationEffect.Composition` primitives (click, tick, thud...), which feel crisp; everything
 * else falls back to plain one-shots and waveforms. From API 33 every effect is played with the
 * touch usage, so the system's touch-feedback setting is honoured.
 *
 * The open class lets tests count what the hall and the games ask for.
 */
open class Haptics(private val vibrator: Vibrator?) {
    companion object {
        fun from(context: Context): Haptics = Haptics(
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            } catch (_: Exception) {
                null
            },
        )

        // VibrationEffect.Composition.PRIMITIVE_* as plain numbers, so nothing here loads a class an
        // older phone lacks (LOW_TICK is API 31); each is only used after a support check.
        private const val CLICK = 1
        private const val THUD = 2
        private const val QUICK_RISE = 4
        private const val TICK = 7
        private const val LOW_TICK = 8

        /** Below this strength the vibrator stays off. */
        private const val MIN_STRENGTH = 0.02f

        /** Shortest gaps between the effects that are only background texture. */
        private const val SOFT_GAP_MS = 45L
        private const val RUMBLE_GAP_MS = 110L
        private const val BUMP_GAP_MS = 90L

        private val WIN_WAVE_MS = longArrayOf(0, 35, 50, 35, 50, 90)
        private val WIN_WAVE_AMP = intArrayOf(0, 180, 0, 220, 0, 255)
        private val WIN_PRIMS = intArrayOf(CLICK, CLICK, THUD)
        private val WIN_SCALES = floatArrayOf(0.7f, 0.85f, 1f)
        private val WIN_DELAYS = intArrayOf(0, 70, 70)

        private val JACKPOT_WAVE_MS = longArrayOf(0, 40, 40, 40, 40, 40, 40, 140)
        private val JACKPOT_WAVE_AMP = intArrayOf(0, 160, 0, 200, 0, 230, 0, 255)
        private val JACKPOT_PRIMS = intArrayOf(TICK, CLICK, CLICK, CLICK, QUICK_RISE, THUD)
        private val JACKPOT_SCALES = floatArrayOf(0.6f, 0.7f, 0.8f, 0.9f, 1f, 1f)
        private val JACKPOT_DELAYS = intArrayOf(0, 45, 45, 45, 60, 30)

        /** A base amplitude (1..255) at the given [strength] (0..1). */
        internal fun scaleAmplitude(base: Int, strength: Float): Int = (base * strength).roundToInt().coerceIn(1, 255)

        /**
         * A base duration at the given [strength], for a vibrator that can't vary its amplitude: a
         * weaker setting then means a shorter buzz (never below 4 ms).
         */
        internal fun scaleDuration(ms: Long, strength: Float): Long = (ms * (0.4f + 0.6f * strength)).toLong().coerceAtLeast(4L)

        /** Amplitude (1..255) of a rumble at [level] (0..1): always a low buzz, never a knock. */
        internal fun rumbleAmplitude(level: Float): Int = (20f + 70f * level.coerceIn(0f, 1f)).roundToInt()
    }

    /** Master switch: false silences every call. */
    var enabled = true

    /**
     * How strong the vibration is, 0..1 (1 by default), scaling every amplitude and primitive; at
     * about zero the vibrator stays off. For a settings slider to drive.
     */
    var strength = 1f
        set(value) {
            field = if (value.isNaN()) 1f else value.coerceIn(0f, 1f)
        }

    private var lastAt = 0L
    private var lastAmbientAt = 0L
    /** Until when a multi-beat pattern is still playing (background texture waits for it). */
    private var busyUntil = 0L

    private val amplitudeControl = vibrator != null && try {
        vibrator.hasAmplitudeControl()
    } catch (_: Exception) {
        false
    }
    private val primitives = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supported(CLICK, TICK, THUD, QUICK_RISE)
    private val lowTick = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supported(LOW_TICK)

    @RequiresApi(Build.VERSION_CODES.R)
    private fun supported(vararg ids: Int): Boolean = try {
        vibrator?.areAllPrimitivesSupported(*ids) == true
    } catch (_: Exception) {
        false
    }

    /** A light tick: a tap on something, a step in a count. */
    open fun tick() = single(35, TICK, 0.6f, 10, 70)

    /** A firm knock: a hit or a landed catch. */
    open fun hit() = single(25, CLICK, 0.7f, 22, 170)

    /** The heaviest single thud: a slam. */
    open fun heavy() = single(25, THUD, 1f, 55, 255)

    /** A very light tick, for texture rather than events: a prompt appearing, a reel click. */
    open fun soft() {
        if (lowTick) single(SOFT_GAP_MS, LOW_TICK, 0.5f, 8, 40, ambient = true)
        else single(SOFT_GAP_MS, TICK, 0.3f, 8, 40, ambient = true)
    }

    /** A short dull thud, for walking into something or a flipper slamming up; rate-limited. */
    open fun bump() = single(BUMP_GAP_MS, THUD, 0.55f, 30, 150)

    /**
     * A short, low buzz for an engine or a rumble strip, [level] 0..1 of how hard. Rate-limited,
     * it never cuts off a hit or a win, and a vibrator that can't vary its amplitude skips it
     * (a full-strength buzz is no rumble).
     */
    open fun rumble(level: Float) {
        if (!(level >= 0.05f) || !amplitudeControl) return
        val v = ready(RUMBLE_GAP_MS, ambient = true) ?: return
        oneShot(v, 28, scaleAmplitude(rumbleAmplitude(level), strength))
    }

    open fun win() = sequence(WIN_WAVE_MS, WIN_WAVE_AMP, WIN_PRIMS, WIN_SCALES, WIN_DELAYS, holdMs = 280)

    open fun jackpot() = sequence(JACKPOT_WAVE_MS, JACKPOT_WAVE_AMP, JACKPOT_PRIMS, JACKPOT_SCALES, JACKPOT_DELAYS, holdMs = 480)

    /**
     * The vibrator, if an effect may start now: [enabled], not silenced by [strength], and no
     * sooner than [minGapMs] after the last one. Background texture ([ambient]) also waits for a
     * pattern still playing and doesn't count against the events that follow it, so a rumble can
     * never make a hit or a win get dropped.
     */
    private fun ready(minGapMs: Long, ambient: Boolean = false, holdMs: Long = 0L): Vibrator? {
        if (!enabled || strength < MIN_STRENGTH) return null
        val v = vibrator ?: return null
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < minGapMs) return null
        if (ambient) {
            if (now - lastAmbientAt < minGapMs || now < busyUntil) return null
            lastAmbientAt = now
        } else {
            lastAt = now
            busyUntil = now + holdMs
        }
        return v
    }

    /** One beat: a primitive where the phone has them, else a one-shot of [ms] at [amp]. */
    private fun single(minGapMs: Long, primitive: Int, scale: Float, ms: Long, amp: Int, ambient: Boolean = false) {
        val v = ready(minGapMs, ambient) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && (if (primitive == LOW_TICK) lowTick else primitives)) {
                start(v, VibrationEffect.startComposition().addPrimitive(primitive, (scale * strength).coerceIn(0f, 1f)).compose())
            } else {
                oneShot(v, ms, scaleAmplitude(amp, strength))
            }
        } catch (_: Exception) {
        }
    }

    /** A pattern of several beats: primitives with [delays] between, else the [waveMs]/[waveAmp] waveform. */
    private fun sequence(waveMs: LongArray, waveAmp: IntArray, prims: IntArray, scales: FloatArray, delays: IntArray, holdMs: Long) {
        val v = ready(0, holdMs = holdMs) ?: return
        try {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && primitives) {
                val c = VibrationEffect.startComposition()
                for (i in prims.indices) c.addPrimitive(prims[i], (scales[i] * strength).coerceIn(0f, 1f), delays[i])
                c.compose()
            } else if (amplitudeControl) {
                val amps = IntArray(waveAmp.size) { if (waveAmp[it] == 0) 0 else scaleAmplitude(waveAmp[it], strength) }
                VibrationEffect.createWaveform(waveMs, amps, -1)
            } else {
                VibrationEffect.createWaveform(waveMs, -1)
            }
            start(v, effect)
        } catch (_: Exception) {
        }
    }

    private fun oneShot(v: Vibrator, ms: Long, amplitude: Int) {
        try {
            val effect = if (amplitudeControl) {
                VibrationEffect.createOneShot(ms, amplitude)
            } else {
                VibrationEffect.createOneShot(scaleDuration(ms, strength), VibrationEffect.DEFAULT_AMPLITUDE)
            }
            start(v, effect)
        } catch (_: Exception) {
        }
    }

    /** Plays [effect], as touch feedback from API 33 so the system's setting for it applies. */
    private fun start(v: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, TouchUsage.attributes)
        } else {
            v.vibrate(effect)
        }
    }
}

/** The touch usage's attributes, in their own object so an older phone never loads them. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object TouchUsage {
    val attributes: VibrationAttributes = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
}
