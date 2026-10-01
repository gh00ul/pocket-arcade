class_name MusicControl
extends RefCounted
## engine/audio/Music.kt's control surface: the theme for a scene, intensity, quiet, stingers and
## ducking, the music volume and mute.
##
## The soundtrack itself is build-13's synthesizer (Music, ScorePlayer, MusicVoice, Patches, Score
## and Tracks, unchanged), which runs natively inside the Android plugin and plays through its own
## low-latency AudioTrack exactly as build-13 did: GDScript cannot synthesize its 20 to 30
## subtractive voices in real time on a phone (measured 2.0M voice-samples/s on a desktop core,
## against about 1M/s at a busy moment), and pre-rendering every theme's layers would take minutes
## and hundreds of megabytes. This class keeps the state, forwards every change to the backend and
## re-sends the state when a backend attaches. Without one (desktop runs, tests) the music is silent.

## Base class of the music backends (see [PluginMusic]); every method is a no-op here.
class Backend:
	extends RefCounted

	func set_scene(_scene: String) -> void:
		pass

	func set_intensity(_level: float) -> void:
		pass

	func set_quiet(_on: bool) -> void:
		pass

	func stinger(_which: int) -> void:
		pass

	func duck(_depth: float, _hold: float) -> void:
		pass

	func set_volume(_volume: float) -> void:
		pass

	func set_muted(_muted: bool) -> void:
		pass

	## The app went to the background (false) or came back (true).
	func set_active(_active: bool) -> void:
		pass


var _backend: Backend = null

## The music volume the player chose, as a gain: 0 is off.
var volume := 1.0:
	set(v):
		volume = v
		if _backend != null:
			_backend.set_volume(v)

## Silences the music entirely (the mute button).
var muted := false:
	set(v):
		muted = v
		if _backend != null:
			_backend.set_muted(v)

## The scene last asked for (see [MusicScene]).
var scene := MusicScene.SILENCE
## The intensity last asked for, 0..1.
var intensity := 0.0
## Whether the music has been sat back.
var quiet := false
## Every stinger asked for, in order (tests read it).
var stingers: Array[int] = []
## The deepest duck asked for since the last [method take_duck] (tests read it).
var duck_depth := 0.0
var duck_hold := 0.0


## Plays the music through [param backend] from now on, starting from the current state.
func attach(backend: Backend) -> void:
	_backend = backend
	if backend == null:
		return
	backend.set_volume(volume)
	backend.set_muted(muted)
	backend.set_quiet(quiet)
	backend.set_intensity(intensity)
	backend.set_scene(scene)


func backend() -> Backend:
	return _backend


## Moves to the theme for [param p_scene], crossfading over about a second. Asking for the current
## scene again does nothing.
func set_scene(p_scene: String) -> void:
	scene = p_scene
	if _backend != null:
		_backend.set_scene(p_scene)


## How intense the moment is, 0..1: higher layers of the theme fade in as it rises.
func set_intensity(level: float) -> void:
	intensity = clampf(level, 0.0, 1.0)
	if _backend != null:
		_backend.set_intensity(intensity)


## Sits the music back (true) under something that needs the ear, and brings it back (false).
func set_quiet(on: bool) -> void:
	quiet = on
	if _backend != null:
		_backend.set_quiet(on)


## Plays [param which] (a [Stinger]) over the scene, ducking it while it sounds.
func stinger(which: int) -> void:
	stingers.append(which)
	if _backend != null:
		_backend.stinger(which)


## Dips the music by [param depth] (0..1) for [param hold_seconds]; overlapping ducks keep the
## deeper and the longer.
func duck(depth: float, hold_seconds: float) -> void:
	duck_depth = maxf(duck_depth, clampf(depth, 0.0, 1.0))
	duck_hold = maxf(duck_hold, hold_seconds)
	if _backend != null:
		_backend.duck(clampf(depth, 0.0, 1.0), hold_seconds)


## The app went to the background (false) or came back (true).
func set_active(active: bool) -> void:
	if _backend != null:
		_backend.set_active(active)
