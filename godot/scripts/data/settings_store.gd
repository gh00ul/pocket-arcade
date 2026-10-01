class_name SettingsStore
extends RefCounted
## data/SettingsStore.kt: the options, on their own file, apart from progress (so options and
## progress can't trample each other). Keys are build-13's DataStore keys.

const LOOK := "look_percent"
const INVERT_Y := "invert_y"
const LEFT_HANDED := "left_handed"
const FOV := "fov_deg"
const RUN_LATCH := "run_latch"
const REDUCE_MOTION := "reduce_motion"
const HAPTICS := "haptics"
const SFX := "sfx_percent"
const AMBIENCE := "ambience_percent"
const MUSIC := "music_percent"
const HAPTIC_STRENGTH := "haptics_percent"
const TILT := "tilt_steering"
const QUALITY := "gfx_quality"
const FRAME_CAP := "gfx_frame_cap"

## Every key SettingsStore.kt defines, by DataStore type.
const KEY_TYPES := {
	LOOK: "int", INVERT_Y: "bool", LEFT_HANDED: "bool", FOV: "int", RUN_LATCH: "bool",
	REDUCE_MOTION: "bool", HAPTICS: "bool", SFX: "int", AMBIENCE: "int", MUSIC: "int",
	HAPTIC_STRENGTH: "int", TILT: "bool", QUALITY: "int", FRAME_CAP: "int",
}

var store: PrefsStore


func _init(p_store: PrefsStore) -> void:
	store = p_store


static func _i(p: Dictionary, key: String, d: int) -> int:
	var v: Variant = p.get(key)
	return v if v is int else d


static func _b(p: Dictionary, key: String, d: bool) -> bool:
	var v: Variant = p.get(key)
	return v if v is bool else d


## Everything stored, in range; whatever is missing is the default.
static func read(p: Dictionary) -> GameSettings:
	var d := GameSettings.new()
	var s := GameSettings.new()
	s.look_percent = _i(p, LOOK, d.look_percent)
	s.invert_y = _b(p, INVERT_Y, d.invert_y)
	s.left_handed = _b(p, LEFT_HANDED, d.left_handed)
	s.fov_deg = _i(p, FOV, d.fov_deg)
	s.run_latch = _b(p, RUN_LATCH, d.run_latch)
	s.reduce_motion = _b(p, REDUCE_MOTION, d.reduce_motion)
	s.haptics = _b(p, HAPTICS, d.haptics)
	s.sfx_percent = _i(p, SFX, d.sfx_percent)
	s.ambience_percent = _i(p, AMBIENCE, d.ambience_percent)
	s.music_percent = _i(p, MUSIC, d.music_percent)
	s.haptics_percent = _i(p, HAPTIC_STRENGTH, d.haptics_percent)
	s.tilt_steering = _b(p, TILT, d.tilt_steering)
	s.quality = _i(p, QUALITY, d.quality)
	s.frame_cap = _i(p, FRAME_CAP, d.frame_cap)
	return s.sanitized()


## Stores [param s] (in range).
static func write(p: Dictionary, s: GameSettings) -> void:
	var v := s.sanitized()
	p[LOOK] = v.look_percent
	p[INVERT_Y] = v.invert_y
	p[LEFT_HANDED] = v.left_handed
	p[FOV] = v.fov_deg
	p[RUN_LATCH] = v.run_latch
	p[REDUCE_MOTION] = v.reduce_motion
	p[HAPTICS] = v.haptics
	p[SFX] = v.sfx_percent
	p[AMBIENCE] = v.ambience_percent
	p[MUSIC] = v.music_percent
	p[HAPTIC_STRENGTH] = v.haptics_percent
	p[TILT] = v.tilt_steering
	p[QUALITY] = v.quality
	p[FRAME_CAP] = v.frame_cap


func settings() -> GameSettings:
	return read(store.read())


## Saves [param s]; false if the write failed.
func save(s: GameSettings) -> bool:
	return store.edit(func(p: Dictionary) -> void:
		write(p, s))
