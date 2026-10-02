class_name Emotes
extends RefCounted
## hub/Emotes.kt: a kid's occasional gestures, on top of what they are doing: waving at the player
## when they stroll past, and cheering or clapping when someone buys a prize. Pure and seeded from
## the kid's own seed (never the simulation's random source), so the gestures are repeatable and
## can never change where a kid goes or when: the owner only swaps the [Pose] it shows for a moment.

## How long a wave lasts (seconds).
const WAVE_TIME := 1.8

## Chance that a kid waves at a passing player on any one encounter, and the shortest gap between
## waves (seconds).
const WAVE_CHANCE := 0.35
const WAVE_COOLDOWN := 20.0

## After deciding to wave, the kid waits this long (plus up to [constant WAVE_DELAY_SPREAD]) so it
## never looks scripted.
const WAVE_DELAY := 0.35
const WAVE_DELAY_SPREAD := 0.5

## How long a celebration lasts (seconds).
const CELEBRATE_TIME := 1.7

## How far apart a crowd's reactions are spread (seconds), on top of how far they are from the counter.
const CELEBRATE_SPREAD := 0.45

## True while this kid is mid-gesture.
var active: bool:
	get:
		return _wave_left > 0.0 or _celebrate_left > 0.0 or _celebrate_delay > 0.0

var _seed: int
var _wave_left := 0.0
var _wave_delay := -1.0
var _cooldown := 0.0
var _was_close := false
var _encounters := 0

var _celebrate_delay := 0.0
var _celebrate_left := 0.0
var _clapping := false


func _init(seed_value: int) -> void:
	_seed = seed_value
	_cooldown = WAVE_COOLDOWN * 0.5 * AnimMath.unit(_seed, 0, 21)


## Sets this kid celebrating after [param delay] seconds (plus a little of their own): a cheer, or
## a clap, which is all a seated kid can do. Ignored if they are already celebrating.
func celebrate(delay: float, seated: bool) -> void:
	if _celebrate_left > 0.0 or _celebrate_delay > 0.0:
		return
	_celebrate_delay = delay + CELEBRATE_SPREAD * AnimMath.unit(_seed, _encounters, 22) + 0.001
	_celebrate_left = CELEBRATE_TIME
	_clapping = seated or AnimMath.unit(_seed, _encounters, 23) < 0.5


## Steps the gestures by [param dt]. [param close] is whether the player has been in front of the
## kid and near for long enough to be noticed; [param standing] whether the kid is free to wave
## (standing about, hands empty); [param seated] and [param walking] what else they might be doing.
## Returns the pose to show in place of the kid's own, or [constant Pose.NONE] to carry on as usual.
func update(dt: float, close: bool, standing: bool, seated: bool, walking: bool) -> int:
	_cooldown -= dt
	# A wave is decided when the player first comes into view, once per encounter.
	if close and not _was_close:
		var n := _encounters
		_encounters += 1
		if standing and _cooldown <= 0.0 and _wave_left <= 0.0 and _wave_delay < 0.0 and AnimMath.unit(_seed, n, 11) < WAVE_CHANCE:
			_wave_delay = WAVE_DELAY + WAVE_DELAY_SPREAD * AnimMath.unit(_seed, n, 12)
	_was_close = close
	if _wave_delay >= 0.0:
		_wave_delay -= dt
		# Set off walking, or the player has gone: the wave is off.
		if not standing or not close:
			_wave_delay = -1.0
		elif _wave_delay < 0.0:
			_wave_left = WAVE_TIME
			_cooldown = WAVE_COOLDOWN
	if _wave_left > 0.0:
		_wave_left -= dt
		if not standing:
			_wave_left = 0.0

	if _celebrate_delay > 0.0:
		_celebrate_delay -= dt
	elif _celebrate_left > 0.0:
		_celebrate_left -= dt
	# A celebration wins over a wave; walking kids are only passing, and get on with it.
	if _celebrate_delay <= 0.0 and _celebrate_left > 0.0 and not walking:
		return Pose.CLAP if _clapping or seated else Pose.CHEER
	if _wave_left > 0.0:
		return Pose.WAVE
	return Pose.NONE
