class_name RoundMusic
extends RefCounted
## engine/audio/RoundMusic.kt: how a round in a machine sounds, as a handful of cues the game host
## calls: the theme sat back under the intro card, the countdown, the round heating up towards the
## buzzer, the pause menu and the results.

## Under the intro card: the bed of the theme only (its layers start at 0.3 and 0.65).
const INTRO_INTENSITY := 0.15
## Counting down: the first extra layer begins to show.
const COUNTDOWN_INTENSITY := 0.3
## On the results: the theme's middle layer, warm but not busy.
const RESULTS_INTENSITY := 0.5
## At the start of a round the intensity is this, rising steadily to MID_INTENSITY...
const START_INTENSITY := 0.35
const MID_INTENSITY := 0.6
## ...and in the last this many seconds it climbs from there to full.
const CRUNCH_SECONDS := 10.0


## How intense a round is with [param time_left] seconds left of [param round_seconds]: steady
## growth from START_INTENSITY to MID_INTENSITY, then a climb to 1 over the last CRUNCH_SECONDS.
## Always in 0..1 and never falls as the clock runs down.
static func intensity(time_left: float, round_seconds: float) -> float:
	var elapsed := 1.0 - MathUtil.clamp01(time_left / round_seconds) if round_seconds > 0.0 else 1.0
	var base := START_INTENSITY + (MID_INTENSITY - START_INTENSITY) * elapsed
	var crunch := MathUtil.clamp01((CRUNCH_SECONDS - time_left) / CRUNCH_SECONDS)
	return MathUtil.clamp01(base + (1.0 - base) * crunch)


## The intro card: machine [param game_id]'s theme, quiet, at its bed.
static func intro(audio: AudioSynth, game_id: String) -> void:
	audio.enter_scene(MusicScene.game(game_id))
	audio.music.set_quiet(true)
	audio.music.set_intensity(INTRO_INTENSITY)


## The countdown: the theme comes up to full volume.
static func countdown(audio: AudioSynth, game_id: String) -> void:
	audio.enter_scene(MusicScene.game(game_id))
	audio.music.set_quiet(false)
	audio.music.set_intensity(COUNTDOWN_INTENSITY)


## Playing (and the moment after the buzzer): full volume; the host sets the intensity every frame.
static func play(audio: AudioSynth) -> void:
	audio.music.set_quiet(false)


## The pause menu: the music sits back.
static func pause(audio: AudioSynth) -> void:
	audio.music.set_quiet(true)


## The results screen: its own resolving loop.
static func results(audio: AudioSynth) -> void:
	audio.enter_scene(MusicScene.RESULTS)
	audio.music.set_quiet(false)
	audio.music.set_intensity(RESULTS_INTENSITY)
