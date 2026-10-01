class_name GameSettings
extends RefCounted
## data/SettingsStore.kt GameSettings: what the player can tune. Every default is how the game
## played before the options existed; values come back through [method sanitized].

const QUALITY_AUTO := 0
const QUALITY_BATTERY := 1
const QUALITY_BEST := 2
const CAP_AUTO := 0
## The frame caps a player can pick, in the order the button cycles through them.
const CAPS: Array[int] = [CAP_AUTO, 30, 60]

static var LOOK := StepRange.new(50, 200, 10)
static var FOV := StepRange.new(60, 90, 5)
static var VOLUME := StepRange.new(0, 100, 10)
static var HAPTIC_STRENGTH := StepRange.new(20, 100, 20)

## First-person look speed, as a percentage of the default.
var look_percent := 100
## Dragging up looks down (and the other way round).
var invert_y := false
## First person with the halves swapped: the right thumb walks and the left one looks.
var left_handed := false
## First-person field of view (vertical degrees on a squarish screen).
var fov_deg := 70
## Pushing the stick to its rim locks the run instead of running only while held there.
var run_latch := true
## No head bob, no run field-of-view kick and no screen shake.
var reduce_motion := false
var haptics := true
## Sound effect and ambience volumes, 0..100 (see [method gain]).
var sfx_percent := 100
var ambience_percent := 100
var music_percent := 100
## How hard the haptics hit, as a percentage.
var haptics_percent := 100
## Racer: lean the phone to steer instead of dragging (drag stays available).
var tilt_steering := false
## Graphics quality tier: QUALITY_AUTO, QUALITY_BATTERY or QUALITY_BEST.
var quality := QUALITY_AUTO
## Frame-rate cap: CAP_AUTO (60 where the screen allows), 30 or 60.
var frame_cap := CAP_AUTO


## The loudness multiplier for a volume of [param percent]: squared, so the steps sound even.
static func gain(percent: int) -> float:
	var v := clampi(percent, 0, 100) / 100.0
	return v * v


func look_scale() -> float:
	return look_percent / 100.0


func sfx_gain() -> float:
	return gain(sfx_percent)


func ambience_gain() -> float:
	return gain(ambience_percent)


func music_gain() -> float:
	return gain(music_percent)


## Haptic strength as the 0..1 multiplier Haptics.strength takes.
func haptics_strength() -> float:
	return haptics_percent / 100.0


func copy() -> GameSettings:
	var s := GameSettings.new()
	s.look_percent = look_percent
	s.invert_y = invert_y
	s.left_handed = left_handed
	s.fov_deg = fov_deg
	s.run_latch = run_latch
	s.reduce_motion = reduce_motion
	s.haptics = haptics
	s.sfx_percent = sfx_percent
	s.ambience_percent = ambience_percent
	s.music_percent = music_percent
	s.haptics_percent = haptics_percent
	s.tilt_steering = tilt_steering
	s.quality = quality
	s.frame_cap = frame_cap
	return s


## This, with every number put back in its range.
func sanitized() -> GameSettings:
	var s := copy()
	s.look_percent = LOOK.clamp_to(look_percent)
	s.fov_deg = FOV.clamp_to(fov_deg)
	s.sfx_percent = VOLUME.clamp_to(sfx_percent)
	s.ambience_percent = VOLUME.clamp_to(ambience_percent)
	s.music_percent = VOLUME.clamp_to(music_percent)
	s.haptics_percent = HAPTIC_STRENGTH.clamp_to(haptics_percent)
	s.quality = clampi(quality, QUALITY_AUTO, QUALITY_BEST)
	s.frame_cap = frame_cap if CAPS.has(frame_cap) else CAP_AUTO
	return s


func equals(o: GameSettings) -> bool:
	return o != null and look_percent == o.look_percent and invert_y == o.invert_y and left_handed == o.left_handed \
		and fov_deg == o.fov_deg and run_latch == o.run_latch and reduce_motion == o.reduce_motion \
		and haptics == o.haptics and sfx_percent == o.sfx_percent and ambience_percent == o.ambience_percent \
		and music_percent == o.music_percent and haptics_percent == o.haptics_percent \
		and tilt_steering == o.tilt_steering and quality == o.quality and frame_cap == o.frame_cap


func _to_string() -> String:
	return "GameSettings(look=%d invertY=%s left=%s fov=%d latch=%s calm=%s haptics=%s/%d sfx=%d amb=%d music=%d tilt=%s q=%d cap=%d)" % [
		look_percent, invert_y, left_handed, fov_deg, run_latch, reduce_motion, haptics, haptics_percent,
		sfx_percent, ambience_percent, music_percent, tilt_steering, quality, frame_cap]
