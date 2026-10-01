class_name MixEngine
extends Node
## engine/audio/MixEngine.kt: everything between "play this" and the speaker. build-13 mixed every
## voice itself on a dedicated thread; here each voice is a pair of [AudioStreamPlayer]s (the dry
## sound on its own panned bus, its reverb send on the room bus) from a fixed pool, and Godot's
## mixer does the arithmetic. The decisions stay build-13's: which voice to steal, the stereo
## gains, the reverb send, the music duck, the ambience's bleeps, mute and the volume settings.
##
## Time is the mixer's own clock, advanced once a frame ([method advance]), so tests drive it
## exactly (set [member manual_clock]).

## build-13's block (10 ms at 48 kHz): the ambience's easing constants are per block.
const BLOCK := 480
## Sounds that may play at once. A busier moment steals the least important.
const MAX_VOICES := 28
## build-13 kept this many extra slots for stolen voices fading out over STEAL_FADE frames; Godot
## fades a stopped stream itself (64 samples), so a stolen voice's players are simply reused.
const DYING_SLOTS := 12
const STEAL_FADE := 96
## Sounds that can start in one frame; a flood past this is dropped rather than stalling anything.
const QUEUE_CAP := 96
## Master gain before the limiter (headroom for stacked voices).
const MASTER := 0.85
## build-13's limiter ceiling as a 16-bit sample value.
const FULL_SCALE := 32000.0
## Pitch limits of a voice (playback speed).
const MIN_PITCH := 0.25
const MAX_PITCH := 4.0
## A send below this is not worth a second player.
const MIN_SEND := 0.0005


## One sound effect being played.
class Voice:
	var dry: AudioStreamPlayer
	var wet: AudioStreamPlayer
	var index := 0
	var active := false
	var sfx := -1
	## Seconds of sound at pitch 1.
	var length := 0.0
	var rate := 1.0
	var started := 0.0
	var priority := 0
	var gain_l := 0.0
	var gain_r := 0.0
	var send := 0.0
	var pan := 0.0
	var volume := 0.0

	## How far through its sound the voice is, 0..1, at time [param now].
	func progress(now: float) -> float:
		return (now - started) * rate / length if length > 0.0 else 1.0


## Set true to drive the clock with [method advance] alone (tests).
var manual_clock := false

## The synthesized sounds as streams, by [Sfx] (null until loaded).
var streams: Array = []
## The hall's background sound.
var ambience := HallAmbience.new()
## The soundtrack's controls (see [MusicControl]).
var music := MusicControl.new()

## The player's volume settings: master multipliers on the sound effects and on the ambience.
var sfx_volume := 1.0
var ambience_volume := 1.0

## Silences everything (the player's mute button); sounds that were playing are dropped.
var muted := false:
	set(v):
		if v == muted:
			return
		muted = v
		music.muted = v
		if v:
			for voice in _voices:
				_silence(voice)
			AudioBuses.reset_reverb()
		if _buses_ready:
			AudioServer.set_bus_mute(AudioBuses.index(AudioBuses.OUT), v)

## Target loudness of the arcade ambience (0 = silent), eased toward by the ambience.
var ambient_target: float:
	get:
		return ambience.target
	set(v):
		ambience.target = v

## The room the sound effects sound in ([ReverbRoom]); changing it glides over about a second.
var room := ReverbRoom.GAME

var listener_x := 0.0
var listener_z := 0.0
var listener_yaw := PI

## Where the reverb's settings have glided to (build-13 Reverb's current rt60, damping, wet, early).
var current_rt60: float = ReverbRoom.RT60[ReverbRoom.GAME]
var current_damping: float = ReverbRoom.DAMPING[ReverbRoom.GAME]
var current_wet: float = ReverbRoom.WET[ReverbRoom.GAME]
var current_early: float = ReverbRoom.EARLY[ReverbRoom.GAME]

var _voices: Array[Voice] = []
var _now := 0.0
var _started_this_frame := 0
var _placement := Placement.new()
var _hum: AudioStreamPlayer
var _murmur: AudioStreamPlayer
var _buses_ready := false


func _init() -> void:
	streams.resize(Sfx.COUNT)
	for i in MAX_VOICES:
		var v := Voice.new()
		v.index = i
		_voices.append(v)


func _ready() -> void:
	AudioBuses.ensure(MAX_VOICES)
	_buses_ready = true
	AudioServer.set_bus_mute(AudioBuses.index(AudioBuses.OUT), muted)
	for v in _voices:
		v.dry = AudioStreamPlayer.new()
		v.dry.bus = AudioBuses.voice_bus(v.index)
		add_child(v.dry)
		v.wet = AudioStreamPlayer.new()
		v.wet.bus = AudioBuses.SEND
		add_child(v.wet)
	_hum = AudioStreamPlayer.new()
	_hum.bus = AudioBuses.AMBIENCE
	add_child(_hum)
	_murmur = AudioStreamPlayer.new()
	_murmur.bus = AudioBuses.AMBIENCE
	add_child(_murmur)
	_apply_room()


func _process(delta: float) -> void:
	if not manual_clock:
		advance(delta)


## Hands over the synthesized sounds: [param sfx_streams] by [Sfx], and the hum and murmur loops.
func set_streams(sfx_streams: Array, hum: AudioStream, murmur: AudioStream) -> void:
	for i in mini(sfx_streams.size(), Sfx.COUNT):
		streams[i] = sfx_streams[i]
	if _hum != null and hum != null:
		_hum.stream = hum
		_murmur.stream = murmur
		_hum.volume_linear = 0.0
		_murmur.volume_linear = 0.0
		_hum.play()
		_murmur.play()
		_hum.stream_paused = true
		_murmur.stream_paused = true


## Builds the sound effect streams straight from a generated [param bank] (tests).
func load_bank(bank: SfxBank) -> void:
	var list: Array = []
	for i in Sfx.COUNT:
		var s := bank.samples(i)
		list.append(bank.stream_of(s) if not s.is_empty() else null)
	set_streams(list, null, null)


## Where the player's ears are: world position and heading (see [Spatial] for the conventions).
func set_listener(x: float, z: float, yaw: float) -> void:
	listener_x = x
	listener_z = z
	listener_yaw = yaw


# ------------------------------------------------------------------ requests

## Plays [param sfx] from dead centre; [param pitch] scales playback speed, so 2.0 is an octave up.
func play(sfx: int, volume: float = 1.0, pitch: float = 1.0) -> void:
	var v := volume * sfx_volume
	if muted or v <= 0.0:
		return
	# Dead centre: the equal-power pan law's 1/sqrt(2) each side, made up to unity.
	_request(sfx, v, v, pitch, AudioPriority.NORMAL, v)


## Plays [param sfx] from the world point ([param x], [param z]) as heard from the listener.
## Inaudible sounds cost nothing.
func play_at(sfx: int, x: float, z: float, volume: float = 1.0, pitch: float = 1.0, priority: int = AudioPriority.NORMAL) -> void:
	var v := volume * sfx_volume
	if muted or v <= 0.0:
		return
	var p := _placement
	Spatial.place(listener_x, listener_z, listener_yaw, x, z, p)
	if p.is_silent():
		return
	_request(sfx, p.left * v, p.right * v, pitch, priority, p.send * v)


## [param send_scale] is how much of the sound's default reverb send to use: its loudness, times how
## near it is. A big sound (a jackpot, a fanfare) also ducks the music, as deep as it is loud here.
func _request(sfx: int, gain_l: float, gain_r: float, pitch: float, priority: int, send_scale: float) -> void:
	if streams[sfx] == null or _started_this_frame >= QUEUE_CAP:
		return
	_started_this_frame += 1
	start_voice(sfx, gain_l, gain_r, SfxMix.send_for(sfx) * send_scale, pitch, priority)
	var depth := SfxMix.duck_depth(sfx)
	if depth > 0.0:
		music.duck(depth * minf(1.0, maxf(gain_l, gain_r)), SfxMix.duck_hold(sfx))


# ------------------------------------------------------------------ voices (build-13's VoiceSink)

## How many lowest-priority (ambience) voices are sounding now.
func ambient_voices() -> int:
	var c := 0
	for v in _voices:
		if v.active and v.priority == AudioPriority.AMBIENT:
			c += 1
	return c


## How many voices are sounding (tests).
func active_voices() -> int:
	var c := 0
	for v in _voices:
		if v.active:
			c += 1
	return c


func voices() -> Array[Voice]:
	return _voices


func start_voice(sfx: int, gain_l: float, gain_r: float, send: float, pitch: float, priority: int) -> void:
	var stream: AudioStream = streams[sfx]
	if stream == null:
		return
	start_voice_of(stream, gain_l, gain_r, send, pitch, priority, sfx)


## [method start_voice] for any stream (tests feed their own). Steals the least important voice when full.
func start_voice_of(stream: AudioStream, gain_l: float, gain_r: float, send: float, pitch: float, priority: int, sfx: int = -1) -> void:
	_expire()
	var free := -1
	var live := 0
	var victim := -1
	var victim_score := INF
	for i in _voices.size():
		var v := _voices[i]
		if not v.active:
			if free < 0:
				free = i
			continue
		live += 1
		# The best voice to lose: the least important, then the one furthest through its sound.
		var score := v.priority * 2.0 - v.progress(_now)
		if score < victim_score:
			victim_score = score
			victim = i
	var slot := free
	if live >= MAX_VOICES:
		# A less important sound never takes the place of a more important one.
		if _voices[victim].priority > priority:
			return
		slot = victim
	if slot < 0:
		return
	var voice := _voices[slot]
	voice.active = true
	voice.sfx = sfx
	voice.length = stream.get_length()
	voice.rate = clampf(pitch, MIN_PITCH, MAX_PITCH)
	voice.started = _now
	voice.priority = priority
	voice.gain_l = gain_l
	voice.gain_r = gain_r
	voice.send = send
	var pv := pan_for(gain_l, gain_r)
	voice.pan = pv.x
	voice.volume = pv.y
	if voice.dry == null:
		return
	# Restarting a player fades its old sound out over Godot's 64 samples: the stolen voice's fade.
	AudioBuses.panner(slot).pan = voice.pan
	voice.dry.stream = stream
	voice.dry.pitch_scale = voice.rate
	voice.dry.volume_linear = voice.volume
	voice.dry.play()
	if send > MIN_SEND:
		voice.wet.stream = stream
		voice.wet.pitch_scale = voice.rate
		voice.wet.volume_linear = send
		voice.wet.play()
	elif voice.wet.playing:
		voice.wet.stop()


## The Godot panner setting and player volume that give a mono sound the stereo gains
## ([param gain_l], [param gain_r]): Godot's AudioEffectPanner sends a mono signal s to
## left = s (1 - pan) and right = s (1 + pan) for pan >= 0 (mirrored below 0), so
## pan = (gr - gl) / (gl + gr) and volume = (gl + gr) / 2 reproduce them exactly.
static func pan_for(gain_l: float, gain_r: float) -> Vector2:
	var sum := gain_l + gain_r
	if sum <= 0.0:
		return Vector2.ZERO
	return Vector2((gain_r - gain_l) / sum, sum / 2.0)


## What Godot's AudioEffectPanner (servers/audio/effects/audio_effect_panner.cpp, 4.6.2) does to a
## mono sound at [param volume] panned to [param pan]: the left and right gains (tests).
static func godot_panner(pan: float, volume: float) -> Vector2:
	var lvol := clampf(1.0 - pan, 0.0, 1.0)
	var rvol := clampf(1.0 + pan, 0.0, 1.0)
	return Vector2(volume * (lvol + (1.0 - rvol)), volume * (rvol + (1.0 - lvol)))


func _silence(v: Voice) -> void:
	v.active = false
	if v.dry != null:
		v.dry.stop()
		v.wet.stop()


## Frees the voices whose sound has played out.
func _expire() -> void:
	for v in _voices:
		if v.active and v.progress(_now) >= 1.0:
			v.active = false


# ------------------------------------------------------------------ the clock

## Moves the mixer on [param seconds]: frees finished voices, glides the room, runs the ambience
## (its levels and bleeps) and sets the hum and murmur loops' gains.
func advance(seconds: float) -> void:
	_now += seconds
	_started_this_frame = 0
	_expire()
	var k := 1.0 - exp(-seconds / ReverbRoom.MORPH_TIME)
	current_rt60 += (ReverbRoom.RT60[room] - current_rt60) * k
	current_damping += (ReverbRoom.DAMPING[room] - current_damping) * k
	current_wet += (ReverbRoom.WET[room] - current_wet) * k
	current_early += (ReverbRoom.EARLY[room] - current_early) * k
	_apply_room()
	if muted:
		_set_loops(0.0, 0.0)
		return
	ambience.tick(seconds, self, ambience_volume, listener_x, listener_z, listener_yaw)
	_set_loops(ambience.hum_gain, ambience.murmur_gain)


## The mixer's clock (seconds since it started).
func now() -> float:
	return _now


func _set_loops(hum: float, murmur: float) -> void:
	if _hum == null or _hum.stream == null:
		return
	_hum.volume_linear = hum
	_murmur.volume_linear = murmur
	_hum.stream_paused = hum <= 0.0
	_murmur.stream_paused = murmur <= 0.0


func _apply_room() -> void:
	if not _buses_ready:
		return
	var r := AudioBuses.reverb()
	if r == null:
		return
	r.room_size = ReverbRoom.room_size_for(current_rt60)
	r.damping = ReverbRoom.damping_param(current_damping, AudioServer.get_mix_rate())
	r.wet = ReverbRoom.wet_param(current_wet, current_rt60)
