class_name FrameAnim
extends RefCounted
## Compose's `Animatable<Float>` with `tween(durationMillis, easing)`, driven by frame times: the
## app's flow (ui/Handoff.kt, ArcadeApp.kt's dive and fade) ran its animations in coroutines; here
## each animation is stepped once a frame with the frame's time in nanoseconds ([method frame]),
## with Compose's own arithmetic, so a sequence takes the same frames build-13's did:
##  - the first frame after [method animate_to] starts the clock (play time 0: the value is still the
##    start value), every later frame sets the value at its play time (whole milliseconds, as
##    TweenSpec reads it), and the frame whose play time reaches the duration lands exactly on the
##    target and ends the animation;
##  - a duration scale of 0 (Android's "remove animations") lands on the target on the first frame;
##  - [method snap_to] jumps and cancels whatever was running.
## The `block` of Compose's animateTo is [param on_frame], called after every frame's value.

enum Easing { LINEAR, FAST_OUT_SLOW_IN, FAST_OUT_LINEAR_IN, LINEAR_OUT_SLOW_IN }

## Android's animator duration scale for every animation that doesn't set its own (1 = normal,
## 0 = animations off). The app sets it from the system setting when the platform reports one.
static var system_scale := 1.0

var value := 0.0
var target_value := 0.0
## True while an animation is running.
var running := false
## This animation's duration scale, or a negative number for [member system_scale].
var duration_scale := -1.0

var _from := 0.0
var _duration_ms := 0
var _easing: int = Easing.FAST_OUT_SLOW_IN
var _start_ns := -1
var _on_frame: Callable = Callable()


func _init(initial: float = 0.0) -> void:
	value = initial
	target_value = initial


## Jumps to [param v] and stops any animation (Compose's snapTo).
func snap_to(v: float) -> void:
	value = v
	target_value = v
	running = false
	_on_frame = Callable()


## Starts animating from the current value to [param target] over [param duration_ms]
## (Compose's animateTo(target, tween(duration, easing)) { on_frame(value) }).
func animate_to(target: float, duration_ms: int, easing: int = Easing.FAST_OUT_SLOW_IN, on_frame: Callable = Callable()) -> void:
	_from = value
	target_value = target
	_duration_ms = maxi(duration_ms, 0)
	_easing = easing
	_start_ns = -1
	_on_frame = on_frame
	running = true


## One frame at [param now_ns]. Returns true on the frame the animation ends.
func frame(now_ns: int) -> bool:
	if not running:
		return false
	if _start_ns < 0:
		_start_ns = now_ns
	var scale := duration_scale if duration_scale >= 0.0 else system_scale
	var duration_ns := _duration_ms * 1_000_000
	var play_ns: int
	if scale == 0.0:
		play_ns = duration_ns
	else:
		play_ns = int((now_ns - _start_ns) / scale)
	var play_ms := clampi(play_ns / 1_000_000, 0, _duration_ms)
	var raw := 1.0 if _duration_ms == 0 else float(play_ms) / _duration_ms
	var f := transform(_easing, clampf(raw, 0.0, 1.0))
	value = _from + (target_value - _from) * f
	var ended := play_ns >= duration_ns
	if ended:
		running = false
	var cb := _on_frame
	if ended:
		_on_frame = Callable()
	if cb.is_valid():
		cb.call(value)
	return ended


## Compose's easing curves: LinearEasing and the CubicBezierEasings FastOutSlowIn (0.4, 0, 0.2, 1),
## FastOutLinearIn (0.4, 0, 1, 1) and LinearOutSlowIn (0, 0, 0.2, 1).
static func transform(easing: int, fraction: float) -> float:
	match easing:
		Easing.LINEAR:
			return fraction
		Easing.FAST_OUT_SLOW_IN:
			return cubic_bezier(0.4, 0.0, 0.2, 1.0, fraction)
		Easing.FAST_OUT_LINEAR_IN:
			return cubic_bezier(0.4, 0.0, 1.0, 1.0, fraction)
		Easing.LINEAR_OUT_SLOW_IN:
			return cubic_bezier(0.0, 0.0, 0.2, 1.0, fraction)
	return fraction


## CubicBezierEasing(a, b, c, d).transform: the curve through (0,0), (a,b), (c,d), (1,1) read at x =
## [param fraction] (the ends pass through untouched, as in Compose).
static func cubic_bezier(a: float, b: float, c: float, d: float, fraction: float) -> float:
	if fraction <= 0.0 or fraction >= 1.0:
		return fraction
	# Solve x(t) = fraction: Newton steps from a good guess, bisection if they wander.
	var t := fraction
	for i in 8:
		var x := _bez(a, c, t) - fraction
		if absf(x) < 1e-7:
			return _bez(b, d, t)
		var dx := _bez_slope(a, c, t)
		if absf(dx) < 1e-6:
			break
		t -= x / dx
		if t < 0.0 or t > 1.0:
			break
	var lo := 0.0
	var hi := 1.0
	t = fraction
	for i in 40:
		var x := _bez(a, c, t)
		if absf(x - fraction) < 1e-7:
			break
		if x < fraction:
			lo = t
		else:
			hi = t
		t = (lo + hi) * 0.5
	return _bez(b, d, t)


## One coordinate of the cubic with control values p1, p2 (ends 0 and 1) at t.
static func _bez(p1: float, p2: float, t: float) -> float:
	var u := 1.0 - t
	return 3.0 * u * u * t * p1 + 3.0 * u * t * t * p2 + t * t * t


static func _bez_slope(p1: float, p2: float, t: float) -> float:
	var u := 1.0 - t
	return 3.0 * u * u * p1 + 6.0 * u * t * (p2 - p1) + 3.0 * t * t * (1.0 - p2)
