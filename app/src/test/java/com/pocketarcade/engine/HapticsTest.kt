package com.pocketarcade.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parts of [Haptics] that can be checked without a vibrator: scaling, clamping and never throwing. */
class HapticsTest {
    @Test
    fun strengthScalesAmplitudesAndStaysInRange() {
        assertEquals(255, Haptics.scaleAmplitude(255, 1f))
        assertEquals(128, Haptics.scaleAmplitude(255, 0.5f))
        assertEquals(35, Haptics.scaleAmplitude(70, 0.5f))
        // Never zero (zero means off in a waveform) and never past the top.
        assertEquals(1, Haptics.scaleAmplitude(70, 0f))
        assertEquals(255, Haptics.scaleAmplitude(255, 3f))
    }

    @Test
    fun aWeakerSettingShortensABuzzOnAVibratorWithoutAmplitudeControl() {
        assertEquals(55L, Haptics.scaleDuration(55, 1f))
        assertTrue(Haptics.scaleDuration(55, 0.5f) in 25L..40L)
        assertEquals(4L, Haptics.scaleDuration(5, 0f))
    }

    @Test
    fun aRumbleIsAlwaysALowBuzz() {
        assertTrue(Haptics.rumbleAmplitude(0f) in 10..40)
        assertTrue(Haptics.rumbleAmplitude(1f) <= 100)
        assertTrue(Haptics.rumbleAmplitude(0.3f) < Haptics.rumbleAmplitude(0.9f))
        // Nonsense levels clamp instead of overshooting.
        assertEquals(Haptics.rumbleAmplitude(1f), Haptics.rumbleAmplitude(7f))
        assertEquals(Haptics.rumbleAmplitude(0f), Haptics.rumbleAmplitude(-3f))
    }

    @Test
    fun strengthClampsToZeroToOne() {
        val h = Haptics(null)
        assertEquals(1f, h.strength, 0f)
        h.strength = 0.4f
        assertEquals(0.4f, h.strength, 0f)
        h.strength = 2f
        assertEquals(1f, h.strength, 0f)
        h.strength = -1f
        assertEquals(0f, h.strength, 0f)
        h.strength = Float.NaN
        assertEquals(1f, h.strength, 0f)
    }

    @Test
    fun withoutAVibratorEveryCallIsAHarmlessNoOp() {
        val h = Haptics(null)
        repeat(3) {
            h.tick(); h.hit(); h.heavy(); h.win(); h.jackpot()
            h.soft(); h.bump(); h.rumble(0.5f); h.rumble(Float.NaN); h.rumble(-1f)
        }
        h.enabled = false
        h.strength = 0f
        h.tick(); h.soft(); h.bump(); h.rumble(1f); h.win(); h.jackpot()
    }
}
