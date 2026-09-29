package com.pocketarcade.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Kept apart from the save file, so options and progress can't trample each other. */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "pocket_arcade_settings")

/** A stepper's range: [min]..[max] in whole [step]s. */
class StepRange(val min: Int, val max: Int, val step: Int) {
    /** [v] limited to the range and snapped to the nearest step. */
    fun clamp(v: Int): Int = (((v.coerceIn(min, max) - min + step / 2) / step) * step + min).coerceAtMost(max)

    /** One step up ([dir] > 0) or down from [v], stopping at the ends. */
    fun nudge(v: Int, dir: Int): Int = clamp(clamp(v) + (if (dir > 0) step else -step))
}

/**
 * What the player can tune: how the hall's first-person controls feel, comfort options and the
 * volumes. Every default is how the game played before the options existed. Values come back
 * through [sanitized], so a hand-edited or older file can never put the game out of range.
 */
data class GameSettings(
    /** First-person look speed, as a percentage of the default. */
    val lookPercent: Int = 100,
    /** Dragging up looks down (and the other way round). */
    val invertY: Boolean = false,
    /** First person with the halves swapped: the right thumb walks and the left one looks. */
    val leftHanded: Boolean = false,
    /**
     * First-person field of view: the vertical degrees on a squarish screen, and the same
     * proportional widening or narrowing on taller ones (the default, 70, leaves the view as it was).
     */
    val fovDeg: Int = 70,
    /** Pushing the stick to its rim locks the run (true) instead of running only while it's held there. */
    val runLatch: Boolean = true,
    /** No head bob, no run field-of-view kick and no screen shake. */
    val reduceMotion: Boolean = false,
    val haptics: Boolean = true,
    /** Sound effect and ambience volumes, 0..100 (see [gain]). */
    val sfxPercent: Int = 100,
    val ambiencePercent: Int = 100,
    /** The music's volume, 0..100 (see [gain]). */
    val musicPercent: Int = 100,
    /** How hard the haptics hit, as a percentage (100 is the full strength they always had). */
    val hapticsPercent: Int = 100,
    /** Racer: lean the phone to steer instead of dragging (drag stays available). */
    val tiltSteering: Boolean = false,
    /** Graphics quality tier: [QUALITY_AUTO], [QUALITY_BATTERY] or [QUALITY_BEST]. */
    val quality: Int = QUALITY_AUTO,
    /** Frame-rate cap: [CAP_AUTO] (60 where the screen allows), 30 or 60. */
    val frameCap: Int = CAP_AUTO,
) {
    companion object {
        val LOOK = StepRange(50, 200, 10)
        val FOV = StepRange(60, 90, 5)
        val VOLUME = StepRange(0, 100, 10)
        val HAPTIC_STRENGTH = StepRange(20, 100, 20)

        const val QUALITY_AUTO = 0
        const val QUALITY_BATTERY = 1
        const val QUALITY_BEST = 2
        const val CAP_AUTO = 0
        /** The frame caps a player can pick, in the order the button cycles through them. */
        val CAPS = intArrayOf(CAP_AUTO, 30, 60)

        /**
         * The loudness multiplier for a volume of [percent]: squared, so the steps sound even
         * (half way is a quarter of the amplitude) and 100 leaves the sound untouched.
         */
        fun gain(percent: Int): Float {
            val v = percent.coerceIn(0, 100) / 100f
            return v * v
        }
    }

    /** The look speed as a multiplier of the default. */
    val lookScale: Float get() = lookPercent / 100f

    /** Sound effect loudness multiplier. */
    val sfxGain: Float get() = gain(sfxPercent)

    /** Ambience loudness multiplier. */
    val ambienceGain: Float get() = gain(ambiencePercent)

    /** Music loudness multiplier. */
    val musicGain: Float get() = gain(musicPercent)

    /** Haptic strength as the 0..1 multiplier `Haptics.strength` takes. */
    val hapticsStrength: Float get() = hapticsPercent / 100f

    /** This, with every number put back in its range. */
    fun sanitized(): GameSettings = copy(
        lookPercent = LOOK.clamp(lookPercent),
        fovDeg = FOV.clamp(fovDeg),
        sfxPercent = VOLUME.clamp(sfxPercent),
        ambiencePercent = VOLUME.clamp(ambiencePercent),
        musicPercent = VOLUME.clamp(musicPercent),
        hapticsPercent = HAPTIC_STRENGTH.clamp(hapticsPercent),
        quality = quality.coerceIn(QUALITY_AUTO, QUALITY_BEST),
        frameCap = if (frameCap in CAPS) frameCap else CAP_AUTO,
    )
}

/**
 * The options, on their own DataStore file ("pocket_arcade_settings"). Reading and writing are
 * plain functions over [Preferences], so the encoding is testable without a device.
 */
class SettingsStore(context: Context) {
    companion object {
        private val LOOK = intPreferencesKey("look_percent")
        private val INVERT_Y = booleanPreferencesKey("invert_y")
        private val LEFT_HANDED = booleanPreferencesKey("left_handed")
        private val FOV = intPreferencesKey("fov_deg")
        private val RUN_LATCH = booleanPreferencesKey("run_latch")
        private val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        private val HAPTICS = booleanPreferencesKey("haptics")
        private val SFX = intPreferencesKey("sfx_percent")
        private val AMBIENCE = intPreferencesKey("ambience_percent")
        private val MUSIC = intPreferencesKey("music_percent")
        private val HAPTIC_STRENGTH = intPreferencesKey("haptics_percent")
        private val TILT = booleanPreferencesKey("tilt_steering")
        private val QUALITY = intPreferencesKey("gfx_quality")
        private val FRAME_CAP = intPreferencesKey("gfx_frame_cap")

        /** Everything stored, in range; whatever is missing is the default. */
        fun read(p: Preferences): GameSettings {
            val d = GameSettings()
            return GameSettings(
                lookPercent = p[LOOK] ?: d.lookPercent,
                invertY = p[INVERT_Y] ?: d.invertY,
                leftHanded = p[LEFT_HANDED] ?: d.leftHanded,
                fovDeg = p[FOV] ?: d.fovDeg,
                runLatch = p[RUN_LATCH] ?: d.runLatch,
                reduceMotion = p[REDUCE_MOTION] ?: d.reduceMotion,
                haptics = p[HAPTICS] ?: d.haptics,
                sfxPercent = p[SFX] ?: d.sfxPercent,
                ambiencePercent = p[AMBIENCE] ?: d.ambiencePercent,
                musicPercent = p[MUSIC] ?: d.musicPercent,
                hapticsPercent = p[HAPTIC_STRENGTH] ?: d.hapticsPercent,
                tiltSteering = p[TILT] ?: d.tiltSteering,
                quality = p[QUALITY] ?: d.quality,
                frameCap = p[FRAME_CAP] ?: d.frameCap,
            ).sanitized()
        }

        /** Stores [s] (in range). */
        fun write(p: MutablePreferences, s: GameSettings) {
            val v = s.sanitized()
            p[LOOK] = v.lookPercent
            p[INVERT_Y] = v.invertY
            p[LEFT_HANDED] = v.leftHanded
            p[FOV] = v.fovDeg
            p[RUN_LATCH] = v.runLatch
            p[REDUCE_MOTION] = v.reduceMotion
            p[HAPTICS] = v.haptics
            p[SFX] = v.sfxPercent
            p[AMBIENCE] = v.ambiencePercent
            p[MUSIC] = v.musicPercent
            p[HAPTIC_STRENGTH] = v.hapticsPercent
            p[TILT] = v.tiltSteering
            p[QUALITY] = v.quality
            p[FRAME_CAP] = v.frameCap
        }
    }

    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<GameSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p -> read(p) }

    /** Saves [s]. */
    suspend fun save(s: GameSettings) {
        store.edit { p -> write(p, s) }
    }
}
