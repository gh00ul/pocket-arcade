package com.pocketarcade.engine

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Short vibration patterns for hits, bonks and wins, rate-limited so bursts don't smear together.
 * A null [vibrator] (no hardware, or headless tests) turns every call into a no-op.
 */
class Haptics(private val vibrator: Vibrator?) {
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
    }

    var enabled = true
    private var lastAt = 0L

    fun tick() = oneShot(10, 70, minGapMs = 35)
    fun hit() = oneShot(22, 170)
    fun heavy() = oneShot(55, 255)

    fun win() = waveform(longArrayOf(0, 35, 50, 35, 50, 90), intArrayOf(0, 180, 0, 220, 0, 255))

    fun jackpot() = waveform(
        longArrayOf(0, 40, 40, 40, 40, 40, 40, 140),
        intArrayOf(0, 160, 0, 200, 0, 230, 0, 255),
    )

    private fun ready(minGapMs: Long): Vibrator? {
        if (!enabled) return null
        val v = vibrator ?: return null
        val now = SystemClock.uptimeMillis()
        if (now - lastAt < minGapMs) return null
        lastAt = now
        return v
    }

    private fun oneShot(ms: Long, amplitude: Int, minGapMs: Long = 25) {
        val v = ready(minGapMs) ?: return
        try {
            val amp = if (v.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
            v.vibrate(VibrationEffect.createOneShot(ms, amp))
        } catch (_: Exception) {
        }
    }

    private fun waveform(timings: LongArray, amplitudes: IntArray) {
        val v = ready(0) ?: return
        try {
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            v.vibrate(effect)
        } catch (_: Exception) {
        }
    }
}
