package com.pocketarcade.data

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The options survive a save and a reload, stay in range whatever the file says, and default to how the game played before. */
class SettingsStoreTest {
    @Test
    fun anEmptyFileGivesTheDefaultsWhichAreTheOriginalFeel() {
        val s = SettingsStore.read(mutablePreferencesOf())
        assertEquals(GameSettings(), s)
        assertEquals(100, s.lookPercent)
        assertEquals(70, s.fovDeg)
        assertFalse(s.invertY)
        assertFalse(s.leftHanded)
        assertFalse(s.reduceMotion)
        assertTrue(s.haptics)
        assertTrue(s.runLatch)
        assertEquals(1f, s.lookScale, 0f)
        assertEquals(1f, s.sfxGain, 0f)
        assertEquals(1f, s.ambienceGain, 0f)
    }

    @Test
    fun everyOptionRoundTrips() {
        val want = GameSettings(
            lookPercent = 140, invertY = true, leftHanded = true, fovDeg = 85, runLatch = false,
            reduceMotion = true, haptics = false, sfxPercent = 30, ambiencePercent = 0,
        )
        val p = mutablePreferencesOf()
        SettingsStore.write(p, want)
        assertEquals(want, SettingsStore.read(p))
        // Writing again over the top replaces, it doesn't pile up.
        SettingsStore.write(p, GameSettings())
        assertEquals(GameSettings(), SettingsStore.read(p))
    }

    @Test
    fun outOfRangeValuesAreBroughtBackInRange() {
        val p = mutablePreferencesOf(
            intPreferencesKey("look_percent") to 9999,
            intPreferencesKey("fov_deg") to -5,
            intPreferencesKey("sfx_percent") to 250,
            intPreferencesKey("ambience_percent") to 47,
        )
        val s = SettingsStore.read(p)
        assertEquals(200, s.lookPercent)
        assertEquals(60, s.fovDeg)
        assertEquals(100, s.sfxPercent)
        // Off the grid snaps to the nearest step.
        assertEquals(50, s.ambiencePercent)
        assertEquals(GameSettings(lookPercent = 1, fovDeg = 500, sfxPercent = -3, ambiencePercent = 999).sanitized(), GameSettings(50, false, false, 90, true, false, true, 0, 100))
        // Writing an out-of-range value stores an in-range one.
        val q = mutablePreferencesOf()
        SettingsStore.write(q, GameSettings(lookPercent = 1000))
        assertEquals(200, SettingsStore.read(q).lookPercent)
    }

    @Test
    fun theOptionsShareNoKeysWithTheSave() {
        // A different DataStore file as well, but the names alone must not collide either.
        val p = mutablePreferencesOf()
        ArcadeRepository.writeFirstPerson(p, true)
        SettingsStore.write(p, GameSettings(reduceMotion = true))
        assertTrue(ArcadeRepository.read(p).firstPerson)
        assertTrue(SettingsStore.read(p).reduceMotion)
        assertFalse(ArcadeRepository.read(p).muted)
    }

    @Test
    fun steppersMoveOneStepAndStopAtTheEnds() {
        val look = GameSettings.LOOK
        assertEquals(110, look.nudge(100, 1))
        assertEquals(90, look.nudge(100, -1))
        assertEquals(200, look.nudge(200, 1))
        assertEquals(50, look.nudge(50, -1))
        // From off the grid it snaps first, then steps.
        assertEquals(70, look.nudge(64, 1))
        assertEquals(60, GameSettings.FOV.nudge(65, -1))
        assertEquals(90, GameSettings.FOV.nudge(90, 1))
        assertEquals(0, GameSettings.VOLUME.nudge(0, -1))
        // Every stop of every range is reachable from the default, and all are in range.
        for (range in listOf(GameSettings.LOOK, GameSettings.FOV, GameSettings.VOLUME)) {
            var v = range.min
            var stops = 1
            while (range.nudge(v, 1) != v) {
                v = range.nudge(v, 1)
                stops++
                assertTrue(v in range.min..range.max)
            }
            assertEquals(range.max, v)
            assertEquals((range.max - range.min) / range.step + 1, stops)
        }
    }

    @Test
    fun volumeGainIsSilentAtZeroUntouchedAtFullAndMonotone() {
        assertEquals(0f, GameSettings.gain(0), 0f)
        assertEquals(1f, GameSettings.gain(100), 0f)
        assertEquals(0.25f, GameSettings.gain(50), 1e-6f)
        var last = -1f
        for (pct in 0..100 step 10) {
            val g = GameSettings.gain(pct)
            assertTrue(g > last)
            last = g
        }
        assertEquals(1f, GameSettings.gain(500), 0f)
        assertEquals(0f, GameSettings.gain(-20), 0f)
    }
}
