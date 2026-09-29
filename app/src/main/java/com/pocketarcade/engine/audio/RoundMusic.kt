package com.pocketarcade.engine.audio

import com.pocketarcade.engine.AudioSynth
import com.pocketarcade.engine.clamp01

/**
 * How a round in a machine sounds, as a handful of cues the game host calls: this machine's theme
 * sat back under the intro card, the countdown, the round heating up towards the buzzer, the pause
 * menu and the results. Kept out of the host so the numbers live (and are tested) in one place.
 */
object RoundMusic {
    /** Under the intro card: the bed of the theme only (its layers start at 0.3 and 0.65). */
    const val INTRO_INTENSITY = 0.15f

    /** Counting down: the first extra layer begins to show. */
    const val COUNTDOWN_INTENSITY = 0.3f

    /** On the results: the theme's middle layer, warm but not busy. */
    const val RESULTS_INTENSITY = 0.5f

    /** At the start of a round the intensity is this, rising steadily to [MID_INTENSITY] by the buzzer's approach... */
    const val START_INTENSITY = 0.35f
    const val MID_INTENSITY = 0.6f

    /** ...and in the last this many seconds it climbs from there to full. */
    const val CRUNCH_SECONDS = 10f

    /**
     * How intense a round is with [timeLeft] seconds left of [roundSeconds]: steady growth from
     * [START_INTENSITY] to [MID_INTENSITY] as the round goes on, then a climb to 1 over the last
     * [CRUNCH_SECONDS]. Always in 0..1 and never falls as the clock runs down.
     */
    fun intensity(timeLeft: Float, roundSeconds: Float): Float {
        val elapsed = if (roundSeconds > 0f) 1f - clamp01(timeLeft / roundSeconds) else 1f
        val base = START_INTENSITY + (MID_INTENSITY - START_INTENSITY) * elapsed
        val crunch = clamp01((CRUNCH_SECONDS - timeLeft) / CRUNCH_SECONDS)
        return clamp01(base + (1f - base) * crunch)
    }

    /** The intro card: machine [gameId]'s theme, quiet, at its bed. */
    fun intro(audio: AudioSynth, gameId: String) {
        audio.enterScene(MusicScene.Game(gameId))
        audio.music.setQuiet(true)
        audio.music.setIntensity(INTRO_INTENSITY)
    }

    /** The countdown: the theme comes up to full volume. */
    fun countdown(audio: AudioSynth, gameId: String) {
        audio.enterScene(MusicScene.Game(gameId))
        audio.music.setQuiet(false)
        audio.music.setIntensity(COUNTDOWN_INTENSITY)
    }

    /** Playing (and the moment after the buzzer): full volume; the host sets the intensity every frame. */
    fun play(audio: AudioSynth) {
        audio.music.setQuiet(false)
    }

    /** The pause menu: the music sits back. */
    fun pause(audio: AudioSynth) {
        audio.music.setQuiet(true)
    }

    /** The results screen: its own resolving loop. */
    fun results(audio: AudioSynth) {
        audio.enterScene(MusicScene.Results)
        audio.music.setQuiet(false)
        audio.music.setIntensity(RESULTS_INTENSITY)
    }
}
