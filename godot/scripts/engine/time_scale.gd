class_name TimeScale
extends RefCounted
## engine/Juice.kt TimeScale: time control for the game host: hit-stops (a freeze of a few frames
## on a big hit) and slow-motion beats with eased ramps. Pure and headless: the host feeds
## [method update] the real frame step and gets back how much of it the game gets. The game still
## steps by exactly GameLoop.FIXED_DT (see [SimClock]), so payouts never depend on it.
##
## Rules: a hit-stop is at most MAX_HIT_STOP s (a chain can't extend one past that) and a new one
## is ignored for HIT_STOP_COOLDOWN s after one ends; slow-mo is at least MIN_SCALE × speed for at
## most MAX_SLOW_SECONDS s, deepened and extended by overlapping requests, with SLOW_COOLDOWN after;
## with reduce motion (motion 0) both are off.

## The longest freeze, in seconds (about 14 steps at 120 Hz).
const MAX_HIT_STOP := 0.12
## Freezes shorter than this can't be felt and are ignored.
const MIN_HIT_STOP := 0.015
const HIT_STOP_COOLDOWN := 0.22
## The longest slow-motion hold, in seconds (the ramps are on top).
const MAX_SLOW_SECONDS := 0.9
## The slowest the game may run.
const MIN_SCALE := 0.25
const SLOW_COOLDOWN := 1.6
## How fast the scale eases into a beat and back out (per second, exponential).
const RAMP_IN_RATE := 28.0
const RAMP_OUT_RATE := 7.0
## Within this of full speed the scale snaps back to 1.
const SNAP := 0.012

## Where the motion setting comes from (tests swap it): returns 0..1.
var motion: Callable = func() -> float: return ScreenShake.intensity

var _stop_left := 0.0
var _stop_spent := 0.0
var _stop_cooldown := 0.0
var _slow_left := 0.0
var _slow_spent := 0.0
var _slow_cooldown := 0.0
var _slow_target := 1.0

## The eased speed of the game right now, 1 at full speed (a freeze is separate: see [method frozen]).
var scale := 1.0


func _init(p_motion: Callable = Callable()) -> void:
	if p_motion.is_valid():
		motion = p_motion


## True while a hit-stop holds the game still.
func frozen() -> bool:
	return _stop_left > 0.0


## How deep into a slow-motion beat the game is (0 at full speed).
func depth() -> float:
	return 1.0 - scale


## Whether anything is slowing or holding the game.
func active() -> bool:
	return frozen() or scale < 1.0 or _slow_left > 0.0


## Freezes the game for about [param seconds] (at most MAX_HIT_STOP). Returns whether it took.
func hit_stop(seconds: float) -> bool:
	if motion.call() <= 0.0:
		return false
	var s := 0.0 if is_nan(seconds) else minf(seconds, MAX_HIT_STOP)
	if s < MIN_HIT_STOP:
		return false
	if _stop_left <= 0.0:
		if _stop_cooldown > 0.0:
			return false
		_stop_left = s
		_stop_spent = 0.0
	else:
		var budget := MAX_HIT_STOP - _stop_spent
		if budget <= 0.0:
			return false
		_stop_left = minf(maxf(_stop_left, s), budget)
	return true


## Runs the game at [param speed] for [param seconds], easing in and out. Returns whether it took.
func slow_mo(speed: float, seconds: float) -> bool:
	if motion.call() <= 0.0:
		return false
	var sp := 1.0 if is_nan(speed) else clampf(speed, MIN_SCALE, 1.0)
	var s := 0.0 if is_nan(seconds) else minf(seconds, MAX_SLOW_SECONDS)
	if sp > 0.98 or s <= 0.0:
		return false
	if _slow_left <= 0.0:
		if _slow_cooldown > 0.0:
			return false
		_slow_target = sp
		_slow_left = s
		_slow_spent = 0.0
	else:
		var budget := MAX_SLOW_SECONDS - _slow_spent
		if budget <= 0.0:
			return false
		_slow_target = minf(_slow_target, sp)
		_slow_left = minf(maxf(_slow_left, s), budget)
	return true


## Advances real time by [param real_dt] and returns how much of it the game gets.
func update(real_dt: float) -> float:
	if motion.call() <= 0.0:
		# Reduce motion switched on mid-beat: drop everything at once.
		if active():
			reset()
		return real_dt
	var frozen_now := _stop_left > 0.0
	if frozen_now:
		_stop_left -= real_dt
		_stop_spent += real_dt
		if _stop_left <= 0.0:
			_stop_left = 0.0
			_stop_spent = 0.0
			_stop_cooldown = HIT_STOP_COOLDOWN
	elif _stop_cooldown > 0.0:
		_stop_cooldown = maxf(_stop_cooldown - real_dt, 0.0)
	var target: float
	if _slow_left > 0.0:
		_slow_left -= real_dt
		_slow_spent += real_dt
		target = _slow_target
		if _slow_left <= 0.0:
			_slow_left = 0.0
			_slow_spent = 0.0
			_slow_cooldown = SLOW_COOLDOWN
	else:
		target = 1.0
		if _slow_cooldown > 0.0:
			_slow_cooldown = maxf(_slow_cooldown - real_dt, 0.0)
	scale = MathUtil.damp(scale, target, RAMP_IN_RATE if target < scale else RAMP_OUT_RATE, real_dt)
	if scale > 1.0 - SNAP:
		scale = 1.0
	return 0.0 if frozen_now else real_dt * scale


## Back to full speed at once, forgetting every request and cooldown.
func reset() -> void:
	_stop_left = 0.0
	_stop_spent = 0.0
	_stop_cooldown = 0.0
	_slow_left = 0.0
	_slow_spent = 0.0
	_slow_cooldown = 0.0
	_slow_target = 1.0
	scale = 1.0
