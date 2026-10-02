class_name UiAnim
extends RefCounted
## Compose's animation specs as the menus use them (androidx.compose.animation.core): the easing
## curves, tween, spring (SpringSimulation's closed form, so a spring moves exactly as Compose's
## does), infinite repeats, and [UiAnim.Animatable] (a value with a running animation that keeps its
## velocity when a new target interrupts it, as Animatable does).
##
## Nothing here reads a clock: whoever owns an animation steps it with the frame's delta, so tests
## drive it with fixed steps.

enum Easing { LINEAR, FAST_OUT_SLOW_IN, FAST_OUT_LINEAR_IN, LINEAR_OUT_SLOW_IN }

## Spring.Stiffness* and Spring.DampingRatio*.
const STIFFNESS_HIGH := 10000.0
const STIFFNESS_MEDIUM := 1500.0
const STIFFNESS_MEDIUM_LOW := 400.0
const STIFFNESS_LOW := 200.0
const STIFFNESS_VERY_LOW := 50.0
const DAMPING_NO_BOUNCY := 1.0
const DAMPING_LOW_BOUNCY := 0.75
const DAMPING_MEDIUM_BOUNCY := 0.5
const DAMPING_HIGH_BOUNCY := 0.2
## Spring.DefaultDisplacementThreshold: a Float animation is done once it is this close.
const DEFAULT_THRESHOLD := 0.01


## The easing [param easing] at [param x] (0..1).
static func curve(easing: int, x: float) -> float:
	match easing:
		Easing.FAST_OUT_SLOW_IN:
			return cubic_bezier(0.4, 0.0, 0.2, 1.0, x)
		Easing.FAST_OUT_LINEAR_IN:
			return cubic_bezier(0.4, 0.0, 1.0, 1.0, x)
		Easing.LINEAR_OUT_SLOW_IN:
			return cubic_bezier(0.0, 0.0, 0.2, 1.0, x)
	return x


## CubicBezierEasing(x1, y1, x2, y2).transform(x): the curve's height where it is [param x] across.
static func cubic_bezier(x1: float, y1: float, x2: float, y2: float, x: float) -> float:
	if x <= 0.0:
		return 0.0
	if x >= 1.0:
		return 1.0
	# Solve bx(t) = x: Newton from t = x, then bisection if it wanders.
	var t := x
	for i in 8:
		var bx := _bez(x1, x2, t) - x
		if absf(bx) < 1e-7:
			return _bez(y1, y2, t)
		var d := _bez_d(x1, x2, t)
		if absf(d) < 1e-6:
			break
		t -= bx / d
	if t < 0.0 or t > 1.0 or absf(_bez(x1, x2, t) - x) > 1e-6:
		var lo := 0.0
		var hi := 1.0
		t = x
		for i in 40:
			var bx := _bez(x1, x2, t)
			if absf(bx - x) < 1e-7:
				break
			if bx < x:
				lo = t
			else:
				hi = t
			t = (lo + hi) * 0.5
	return _bez(y1, y2, t)


static func _bez(p1: float, p2: float, t: float) -> float:
	var u := 1.0 - t
	return 3.0 * u * u * t * p1 + 3.0 * u * t * t * p2 + t * t * t


static func _bez_d(p1: float, p2: float, t: float) -> float:
	var u := 1.0 - t
	return 3.0 * u * u * p1 + 6.0 * u * t * (p2 - p1) + 3.0 * t * t * (1.0 - p2)


## infiniteRepeatable(tween([param millis], easing = [param easing]), repeatMode) of 0 → 1, at
## [param time_s] seconds since it started; [param reverse] is RepeatMode.Reverse.
static func repeat_value(time_s: float, millis: float, easing: int, reverse: bool) -> float:
	var period := maxf(millis / 1000.0, 1e-4)
	var k := floori(time_s / period)
	var frac := (time_s - k * period) / period
	if reverse and (k % 2) != 0:
		frac = 1.0 - frac
	return curve(easing, clampf(frac, 0.0, 1.0))


## Compose's SpringSimulation: where a spring of [param damping] and [param stiffness] (mass 1)
## released at [param x0] with velocity [param v0] (units per second) is after [param t] seconds,
## heading for [param target]: (value, velocity).
static func spring_at(x0: float, v0: float, target: float, damping: float, stiffness: float, t: float) -> Vector2:
	var w := sqrt(stiffness)
	var d := x0 - target
	var r := -damping * w
	var disp: float
	var vel: float
	if damping > 1.0:
		var s := w * sqrt(damping * damping - 1.0)
		var gp := r + s
		var gm := r - s
		var cb := (gm * d - v0) / (gm - gp)
		var ca := d - cb
		var em := exp(gm * t)
		var ep := exp(gp * t)
		disp = ca * em + cb * ep
		vel = ca * gm * em + cb * gp * ep
	elif damping == 1.0:
		var ca := d
		var cb := v0 + w * d
		var e := exp(-w * t)
		disp = (ca + cb * t) * e
		vel = (ca + cb * t) * e * (-w) + cb * e
	else:
		var df := w * sqrt(1.0 - damping * damping)
		var cc := d
		var sc := (1.0 / df) * (-r * d + v0)
		var e := exp(r * t)
		var c := cos(df * t)
		var sn := sin(df * t)
		disp = e * (cc * c + sc * sn)
		vel = disp * r + e * (-df * cc * sn + df * sc * c)
	return Vector2(disp + target, vel)


## How long the spring takes to settle within [param threshold] of its target (Compose's
## estimateAnimationDurationMillis, in seconds): the time its decaying envelope gets that close.
static func spring_duration(x0: float, v0: float, target: float, damping: float, stiffness: float, threshold: float) -> float:
	var w := sqrt(stiffness)
	var d := x0 - target
	if absf(d) < threshold and absf(v0) < threshold:
		return 0.0
	var r := -damping * w
	if damping < 1.0:
		var df := w * sqrt(1.0 - damping * damping)
		var sc := (-r * d + v0) / df
		var amp := sqrt(d * d + sc * sc)
		if amp <= threshold:
			return 0.0
		return log(amp / threshold) / -r
	# Critically and over-damped: bisect the (monotonic once past its peak) envelope.
	var env := func(t: float) -> float:
		if damping == 1.0:
			return (absf(d) + absf(v0 + w * d) * t) * exp(-w * t)
		var s := w * sqrt(damping * damping - 1.0)
		var gp := r + s
		var gm := r - s
		var cb := (gm * d - v0) / (gm - gp)
		var ca := d - cb
		return absf(ca) * exp(gm * t) + absf(cb) * exp(gp * t)
	var lo := 0.0
	var hi := 0.05
	while env.call(hi) > threshold and hi < 60.0:
		lo = hi
		hi *= 2.0
	for i in 40:
		var mid := (lo + hi) * 0.5
		if env.call(mid) > threshold:
			lo = mid
		else:
			hi = mid
	return hi


## A value that animates toward targets the way Compose's Animatable(Float) does: a tween ignores
## the motion it starts from, a spring carries the current velocity on (so a press interrupted by a
## release swings through smoothly), and at the end the value lands exactly on its target.
class Animatable:
	extends RefCounted
	var value := 0.0
	var velocity := 0.0
	var target := 0.0
	var threshold := DEFAULT_THRESHOLD
	## 0 idle, 1 tween, 2 spring.
	var _mode := 0
	var _time := 0.0
	var _from := 0.0
	var _v0 := 0.0
	var _length := 0.0
	var _delay := 0.0
	var _easing := 0
	var _damping := 1.0
	var _stiffness := STIFFNESS_MEDIUM

	func _init(initial: float = 0.0, p_threshold: float = DEFAULT_THRESHOLD) -> void:
		value = initial
		target = initial
		threshold = p_threshold

	func is_running() -> bool:
		return _mode != 0

	func snap_to(v: float) -> void:
		value = v
		target = v
		velocity = 0.0
		_mode = 0

	func stop() -> void:
		_mode = 0
		velocity = 0.0

	## tween([param millis], [param delay_ms], [param easing]) to [param to].
	func animate_tween(to: float, millis: float, easing: int = Easing.FAST_OUT_SLOW_IN, delay_ms: float = 0.0) -> void:
		target = to
		_from = value
		_time = 0.0
		_length = maxf(millis, 0.0) / 1000.0
		_delay = maxf(delay_ms, 0.0) / 1000.0
		_easing = easing
		_mode = 1
		if _length <= 0.0 and _delay <= 0.0:
			snap_to(to)

	## spring([param damping], [param stiffness]) to [param to], starting with the current velocity.
	func animate_spring(to: float, damping: float = DAMPING_NO_BOUNCY, stiffness: float = STIFFNESS_MEDIUM) -> void:
		target = to
		_from = value
		_v0 = velocity
		_time = 0.0
		_delay = 0.0
		_damping = damping
		_stiffness = stiffness
		_length = UiAnim.spring_duration(value, velocity, to, damping, stiffness, threshold)
		_mode = 2
		if _length <= 0.0:
			snap_to(to)

	## Advances by [param dt] seconds; true while it is still moving.
	func step(dt: float) -> bool:
		if _mode == 0:
			return false
		_time += dt
		if _mode == 1:
			var t := _time - _delay
			if t < 0.0:
				return true
			if t >= _length:
				snap_to(target)
				return false
			var prev := value
			value = lerpf(_from, target, UiAnim.curve(_easing, t / _length))
			velocity = (value - prev) / maxf(dt, 1e-6)
			return true
		if _time >= _length:
			snap_to(target)
			return false
		var s := UiAnim.spring_at(_from, _v0, target, _damping, _stiffness, _time)
		value = s.x
		velocity = s.y
		return true
