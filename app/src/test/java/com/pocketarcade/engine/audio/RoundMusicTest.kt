package com.pocketarcade.engine.audio

import com.pocketarcade.data.GameSettings
import com.pocketarcade.data.SettingsStore
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.pocketarcade.engine.AudioSynth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The round's music cues and the music-volume setting. */
class RoundMusicTest {
    @Test
    fun aRoundHeatsUpAndNeverCoolsAsTheClockRunsDown() {
        val round = 60f
        var last = -1f
        var t = round
        while (t >= 0f) {
            val v = RoundMusic.intensity(t, round)
            assertTrue("intensity $v out of range at $t", v in 0f..1f)
            assertTrue("fell at $t: $v < $last", v >= last - 1e-6f)
            last = v
            t -= 0.25f
        }
        assertEquals(RoundMusic.START_INTENSITY, RoundMusic.intensity(round, round), 1e-4f)
        assertEquals(1f, RoundMusic.intensity(0f, round), 1e-4f)
        // The last ten seconds carry it well above where the middle of the round sits.
        val before = RoundMusic.intensity(RoundMusic.CRUNCH_SECONDS + 0.1f, round)
        assertTrue("the middle of the round sits at $before", before <= RoundMusic.MID_INTENSITY + 0.05f)
        assertTrue(RoundMusic.intensity(5f, round) > 0.75f)
    }

    @Test
    fun shortAndDegenerateRoundsStayInRange() {
        for (round in floatArrayOf(0f, 5f, 10f, 45f, 75f)) {
            for (left in floatArrayOf(-1f, 0f, 3f, round, round * 2f)) {
                val v = RoundMusic.intensity(left, round)
                assertTrue("$left of $round gave $v", v in 0f..1f && v.isFinite())
            }
        }
    }

    @Test
    fun theCuesDriveTheAudioSystem() {
        val audio = AudioSynth()
        RoundMusic.intro(audio, "racer")
        RoundMusic.countdown(audio, "racer")
        RoundMusic.play(audio)
        RoundMusic.pause(audio)
        RoundMusic.results(audio)
        audio.enterScene(MusicScene.Hall)
        audio.musicVolume = 0.5f
        assertEquals(0.5f, audio.musicVolume, 0f)
        assertEquals(0.5f, audio.music.volume, 0f)
    }

    @Test
    fun theMusicVolumeIsASavedStepperSetting() {
        val d = GameSettings()
        assertEquals(100, d.musicPercent)
        assertEquals(1f, d.musicGain, 0f)
        // A file from before the setting existed has no key: the default.
        assertEquals(100, SettingsStore.read(mutablePreferencesOf()).musicPercent)
        // Stored and read back.
        val p = mutablePreferencesOf()
        SettingsStore.write(p, GameSettings(musicPercent = 40))
        assertEquals(40, SettingsStore.read(p).musicPercent)
        // Sanitised into the stepper's range and steps, however it arrives.
        assertEquals(100, GameSettings(musicPercent = 999).sanitized().musicPercent)
        assertEquals(0, GameSettings(musicPercent = -5).sanitized().musicPercent)
        assertEquals(30, GameSettings(musicPercent = 33).sanitized().musicPercent)
        // Squared like the other volumes: half way is a quarter of the amplitude.
        assertEquals(0.25f, GameSettings(musicPercent = 50).musicGain, 1e-6f)
        assertEquals(0f, GameSettings(musicPercent = 0).musicGain, 0f)
        // The other settings are untouched by it.
        val both = GameSettings(sfxPercent = 30, ambiencePercent = 60, musicPercent = 80)
        val q = mutablePreferencesOf()
        SettingsStore.write(q, both)
        assertEquals(both, SettingsStore.read(q))
    }
}
