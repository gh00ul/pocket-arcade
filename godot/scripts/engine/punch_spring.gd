class_name PunchSpring
extends RefCounted
## engine/Juice.kt PunchSpring: the spring behind a camera punch. kick() shoves it out (a push-in
## peaking at about the kicked amount in 60 ms), it overshoots a little the other way and settles
## within half a second. Sub-stepped and allocation-free; does nothing with reduce motion on.

const STIFFNESS := 420.0
const DAMPING := 20.0
## The velocity a full-strength kick adds, calibrated so its peak is about 1.
const KICK_SPEED := 37.0
## Kicks stronger than this are clamped.
const MAX_AMOUNT := 1.5
const SUB_STEP := 1.0 / 240.0
const REST := 0.0005

var motion: Callable = func() -> float: return ScreenShake.intensity

## How far the punch is out, 1 at the biggest; slightly negative while it swings back.
var value := 0.0
var _velocity := 0.0


func _init(p_motion: Callable = Callable()) -> void:
	if p_motion.is_valid():
		motion = p_motion


func active() -> bool:
	return value != 0.0 or _velocity != 0.0


func kick(amount: float) -> void:
	if is_nan(amount) or amount <= 0.0 or motion.call() <= 0.0:
		return
	# Capped as a whole, so a stack of kicks can't exceed the strongest single one.
	_velocity = minf(_velocity + minf(amount, MAX_AMOUNT) * KICK_SPEED, MAX_AMOUNT * KICK_SPEED)


func update(dt: float) -> void:
	if not active():
		return
	if motion.call() <= 0.0:
		reset()
		return
	var left := clampf(dt, 0.0, 0.1)
	while left > 0.0:
		var h := minf(left, SUB_STEP)
		# Semi-implicit Euler.
		_velocity += (-STIFFNESS * value - DAMPING * _velocity) * h
		value += _velocity * h
		left -= h
	value = clampf(value, -0.5, MAX_AMOUNT)
	if absf(value) < REST and absf(_velocity) < 0.02:
		reset()


func reset() -> void:
	value = 0.0
	_velocity = 0.0
