class_name AudioSynth
extends Node
## engine/AudioSynth.kt: the game's sound. Synthesized sound effects and a procedural hall
## ambience (rendered once, then played by [MixEngine]'s voice pool), positional voices, the room
## reverb and the soundtrack ([MusicControl]). Nothing is loaded from a file.
##
## [method start] loads the sounds from the cache or synthesizes them on a worker thread; until
## they are ready, requests are ignored (as build-13's were before its bank was generated).

## The app's sound (the hall and the games reach it through here).
static var instance: AudioSynth = null

var engine: MixEngine

## Emitted once the sound effects can be heard.
signal sounds_ready

var _loading := false
var _ready_sounds := false


func _init() -> void:
	engine = MixEngine.new()
	engine.name = "MixEngine"
	add_child(engine)
	# The soundtrack plays in the Android plugin where there is one (see MusicControl).
	var plugin_music := PluginMusic.create()
	if plugin_music != null:
		engine.music.attach(plugin_music)


func _enter_tree() -> void:
	if instance == null:
		instance = self


func _exit_tree() -> void:
	if instance == self:
		instance = null


## Silences everything (the player's mute button).
var muted: bool:
	get:
		return engine.muted
	set(v):
		engine.muted = v

## Target loudness of the arcade ambience (0 = silent). Smoothly approached.
var ambient_target: float:
	get:
		return engine.ambient_target
	set(v):
		engine.ambient_target = v

## The player's volume settings: master multipliers on every sound effect, and on the ambience.
var sfx_volume: float:
	get:
		return engine.sfx_volume
	set(v):
		engine.sfx_volume = v

var ambience_volume: float:
	get:
		return engine.ambience_volume
	set(v):
		engine.ambience_volume = v

## The music volume as a gain (0 = off).
var music_volume: float:
	get:
		return engine.music.volume
	set(v):
		engine.music.volume = v

## The soundtrack: scenes, intensity, stingers and ducking. Most callers want [method enter_scene].
var music: MusicControl:
	get:
		return engine.music

## The room the sound effects sound in ([ReverbRoom]); changing it glides over about a second.
var room: int:
	get:
		return engine.room
	set(v):
		engine.room = v


## Goes to a screen's sound: the theme for [param scene] (crossfading) and the room to match (the
## title's big glossy space, the hall's, or the dry close room inside a game and its results).
func enter_scene(scene: String) -> void:
	engine.music.set_scene(scene)
	if scene == MusicScene.TITLE:
		engine.room = ReverbRoom.TITLE
	elif scene == MusicScene.HALL:
		engine.room = ReverbRoom.HALL
	else:
		engine.room = ReverbRoom.GAME


## Loads the sounds (from the cache, or synthesized on a worker thread).
func start() -> void:
	if _loading or _ready_sounds:
		return
	_loading = true
	var rate := int(AudioServer.get_mix_rate())
	var cached := AudioCache.read(rate)
	if cached != null:
		_take(cached)
		return
	WorkerThreadPool.add_task(_build.bind(rate), false, "Synthesize sounds")


func _build(rate: int) -> void:
	var s := AudioCache.build(rate)
	AudioCache.save(s)
	_take.call_deferred(s)


func _take(s: AudioCache.Sounds) -> void:
	_loading = false
	_ready_sounds = true
	engine.set_streams(s.sfx, s.hum, s.murmur)
	sounds_ready.emit()


func is_ready() -> bool:
	return _ready_sounds


## The app went to the background (false) or came back (true).
func set_active(value: bool) -> void:
	engine.music.set_active(value)


## Plays [param sfx] from the middle of the stereo field; [param pitch] scales playback speed.
func play(sfx: int, volume: float = 1.0, pitch: float = 1.0) -> void:
	engine.play(sfx, volume, pitch)


## Plays [param sfx] from the world point ([param x], [param z]) as heard from the listener: panned
## by where it is, quieter and wetter the further away, silent (and free) when out of earshot.
func play_at(sfx: int, x: float, z: float, volume: float = 1.0, pitch: float = 1.0) -> void:
	engine.play_at(sfx, x, z, volume, pitch)


## Puts the listener's ears at world point ([param x], [param z]) facing [param yaw_rad] (0 faces +z,
## the entrance). Cheap: call it every frame.
func set_listener(x: float, z: float, yaw_rad: float) -> void:
	engine.set_listener(x, z, yaw_rad)


## Tells the ambience where the machines are, so their attract-mode bleeps come from the cabinets.
func set_hall_sources(xs: PackedFloat32Array, zs: PackedFloat32Array, kinds: PackedInt32Array, count: int) -> void:
	engine.ambience.set_sources(xs, zs, kinds, count)


## Tells the ambience where the cafe counter is (its steam wand and cups sound from there).
func set_cafe(x: float, z: float) -> void:
	engine.ambience.set_cafe(x, z)


## How busy the hall is around the player, 0..1: the crowd murmur swells with it.
func set_crowd(level: float) -> void:
	engine.ambience.crowd = clampf(level, 0.0, 1.0)


func set_music_intensity(level: float) -> void:
	engine.music.set_intensity(level)
