class_name AudioBuses
extends RefCounted
## The game's audio bus layout, built in code at startup (build-13 mixed everything itself; here
## Godot's mixer does it):
##   PA_Out       the master gain (MixEngine.MASTER) and a limiter in place of build-13's soft clip
##   PA_Sfx       every sound effect voice, each through its own PA_V<n> bus with a panner
##   PA_SfxSend   the voices' reverb sends: a 16 ms pre-delay, then the room reverb (wet only)
##   PA_Ambience  the hall's hum and crowd murmur loops

const OUT := &"PA_Out"
const SFX := &"PA_Sfx"
const SEND := &"PA_SfxSend"
const AMBIENCE := &"PA_Ambience"
const VOICE_PREFIX := "PA_V"

## The limiter's ceiling: just under full scale.
const CEILING_DB := -0.3


static func voice_bus(i: int) -> StringName:
	return StringName(VOICE_PREFIX + str(i))


## Creates any missing bus of the layout (idempotent).
static func ensure(voices: int) -> void:
	if _bus(OUT, &"Master"):
		var amp := AudioEffectAmplify.new()
		amp.volume_db = linear_to_db(MixEngine.MASTER)
		AudioServer.add_bus_effect(index(OUT), amp)
		var lim := AudioEffectHardLimiter.new()
		lim.ceiling_db = CEILING_DB
		AudioServer.add_bus_effect(index(OUT), lim)
	_bus(SFX, OUT)
	if _bus(SEND, OUT):
		var pre := AudioEffectDelay.new()
		pre.dry = 0.0
		pre.tap1_active = true
		pre.tap1_delay_ms = ReverbRoom.PRE_DELAY_MS
		pre.tap1_level_db = 0.0
		pre.tap1_pan = 0.0
		pre.tap2_active = false
		pre.feedback_active = false
		AudioServer.add_bus_effect(index(SEND), pre)
		AudioServer.add_bus_effect(index(SEND), _new_reverb())
	_bus(AMBIENCE, OUT)
	for i in voices:
		if _bus(voice_bus(i), SFX):
			AudioServer.add_bus_effect(index(voice_bus(i)), AudioEffectPanner.new())


static func index(bus: StringName) -> int:
	return AudioServer.get_bus_index(bus)


## The panner on voice [param i]'s bus.
static func panner(i: int) -> AudioEffectPanner:
	return AudioServer.get_bus_effect(index(voice_bus(i)), 0) as AudioEffectPanner


## The room reverb on the send bus.
static func reverb() -> AudioEffectReverb:
	return AudioServer.get_bus_effect(index(SEND), 1) as AudioEffectReverb


## Replaces the reverb with a fresh one with the same settings, which empties its tail.
static func reset_reverb() -> void:
	var bus := index(SEND)
	var old := reverb()
	var fresh := _new_reverb()
	if old != null:
		fresh.room_size = old.room_size
		fresh.damping = old.damping
		fresh.wet = old.wet
		AudioServer.remove_bus_effect(bus, 1)
	AudioServer.add_bus_effect(bus, fresh, 1)


static func _new_reverb() -> AudioEffectReverb:
	var r := AudioEffectReverb.new()
	r.dry = 0.0
	r.spread = 1.0
	r.hipass = ReverbRoom.hipass_param()
	# Godot's pre-delay is an echo; with no feedback it adds nothing (the delay before it is the pre-delay).
	r.predelay_msec = 10.0
	r.predelay_feedback = 0.0
	r.room_size = ReverbRoom.room_size_for(ReverbRoom.RT60[ReverbRoom.GAME])
	r.damping = ReverbRoom.damping_param(ReverbRoom.DAMPING[ReverbRoom.GAME])
	r.wet = ReverbRoom.wet_param(ReverbRoom.WET[ReverbRoom.GAME], ReverbRoom.RT60[ReverbRoom.GAME])
	return r


## Adds bus [param bus] sending to [param send] if it is missing; true when it was created.
static func _bus(bus: StringName, send: StringName) -> bool:
	if index(bus) >= 0:
		return false
	AudioServer.add_bus()
	var i := AudioServer.bus_count - 1
	AudioServer.set_bus_name(i, bus)
	AudioServer.set_bus_send(i, send)
	return true
