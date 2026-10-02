class_name RollingNumber
extends UiView
## ui/RollingNumber.kt RollingNumber: a number in the game's type that rolls like an odometer to
## whatever it becomes: digits slide over each other, counting up or down through the values in
## between, with a small pop when it goes up. The box eases to fit when the number gains or loses
## a digit, so what sits beside it glides instead of jumping. [member on_tick] (if set) is called
## as the number passes whole values, no more than about twenty times a second, for a soft sound.
## With reduce motion on it just shows the number. (UiMotion is [UiMotion].)
##
## `RollingNumber.make(value, color, unit, on_tick)`; change it with [method set_value].

## Every digit as a string, so drawing a wheel never builds one.
const DIGITS: PackedStringArray = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9"]
const TICK_GAP_S := 0.045
const BUMP_PEAK := 1.14

## The number it is heading for.
var value := 0
var color := Color.WHITE:
	set(v):
		color = v
		queue_redraw()
## Grid unit in dp.
var unit := 3.0
var on_tick := Callable()

var _shown := UiAnim.Animatable.new(0.0)
var _bump := UiAnim.Animatable.new(1.0)
var _width := UiAnim.Animatable.new(0.0)
var _reserved := 1
var _advance := 0.0
var _cap := 0.0
var _pad := 0.0
## While a roll runs: the last whole value passed, the clock and when it last ticked.
var _rolling := false
var _last_whole := 0
var _clock := 0.0
var _last_tick_at := -1.0


## The maths of a mechanical counter, so a number can roll instead of jumping. Each digit is a
## wheel: the units wheel turns continuously with the value, and a higher wheel only turns during
## the last whole unit before it carries over (19 to 20 for the tens wheel, 99 to 100 for the
## hundreds), exactly like an odometer. At a whole number every wheel is at rest on its digit.
class Odometer:
	extends RefCounted
	const POW10: PackedFloat64Array = [1.0, 10.0, 100.0, 1000.0, 10000.0, 100000.0, 1000000.0, 10000000.0, 100000000.0, 1000000000.0]

	## How many digits [param n] is written with (at least 1).
	static func digit_count(n: int) -> int:
		var c := 1
		var v := maxi(n, 0)
		while v >= 10:
			v /= 10
			c += 1
		return c

	## The whole part of what the wheel at [param place] (0 = units) has passed: its digit, before the mod 10.
	static func turns(v: float, place: int) -> int:
		return int(floorf(maxf(v, 0.0) / POW10[clampi(place, 0, POW10.size() - 1)]))

	## How far the wheel at [param place] has rolled from its digit toward the next, 0..1, eased at both ends.
	static func roll(v: float, place: int) -> float:
		var x := maxf(v, 0.0)
		var pw := POW10[clampi(place, 0, POW10.size() - 1)]
		# The remainder is exact in floats, so a whole number leaves every wheel at exactly 0.
		var raw := x - floorf(x) if place == 0 else clampf(fmod(x, pw) - (pw - 1.0), 0.0, 1.0)
		return raw * raw * (3.0 - 2.0 * raw)


## How long the roll from one value to another takes (ms): quick for a nudge, longer for a big jump.
static func roll_millis(from: float, to: float) -> int:
	return int(clampf(260.0 + 70.0 * sqrt(absf(to - from)), 300.0, 1100.0))


## RollingNumber(value, color, unit, modifier, onTick).
static func make(p_value: int, p_color: Color, p_unit: float, p_on_tick: Callable = Callable()) -> RollingNumber:
	var n := RollingNumber.new()
	n.color = p_color
	n.unit = p_unit
	n.on_tick = p_on_tick
	n._setup(maxi(p_value, 0))
	return n


func _init() -> void:
	super()
	clip_contents = true


func _setup(target: int) -> void:
	value = target
	var u := unit
	_cap = ArcadeFont.height(u)
	_pad = u * 1.2
	# Digits are drawn in equal-width cells so the number doesn't wobble as it rolls.
	var m := 0.0
	for d in DIGITS:
		m = maxf(m, ArcadeFont.width(d, u))
	_advance = m + u * 0.35
	_shown.snap_to(float(target))
	_reserved = Odometer.digit_count(target)
	_width.snap_to(_reserved * _advance + _pad)
	accessibility_name = str(target)


## Rolls to [param target] (Kotlin's LaunchedEffect(target)).
func set_value(target_in: int) -> void:
	var target := maxi(target_in, 0)
	if target == value and (not _shown.is_running() or _shown.target == float(target)):
		return
	value = target
	accessibility_name = str(target)
	_reserved = maxi(_reserved, Odometer.digit_count(target))
	_aim_width()
	var from := _shown.value
	if not UiMotion.enabled or absf(target - from) < 0.001:
		_shown.snap_to(float(target))
		_rolling = false
		_reserved = Odometer.digit_count(target)
		_aim_width()
		queue_redraw()
		return
	if target > from:
		# A small pop as it goes up, springing back with a little life.
		_bump.snap_to(BUMP_PEAK)
		_bump.animate_spring(1.0, 0.45, UiAnim.STIFFNESS_MEDIUM)
	_last_whole = int(floorf(from))
	_last_tick_at = -1.0
	_rolling = true
	_shown.animate_tween(float(target), roll_millis(from, float(target)), UiAnim.Easing.FAST_OUT_SLOW_IN)
	wake()


## The width the box eases toward: a cell per reserved digit and the shadow pad.
func _aim_width() -> void:
	var w := _reserved * _advance + _pad
	if _width.target == w and (_width.is_running() or _width.value == w):
		return
	if UiMotion.enabled:
		_width.animate_spring(w, 1.0, UiAnim.STIFFNESS_MEDIUM_LOW)
		wake()
	else:
		_width.snap_to(w)
	ui_invalidate()


## What the counter shows right now (between whole values while rolling).
func shown_value() -> float:
	return _shown.value


func _measure_content(max_w: float, max_h: float, _fixed_w: bool, _fixed_h: bool) -> Vector2:
	var w := maxf(px_round(_width.value), px_round(1.0 / maxf(Display.density, 0.01)))
	return Vector2(minf(w, max_w), minf(px_round(_cap + _pad), max_h))


func _animate(dt: float) -> bool:
	var busy := false
	_clock += dt
	if _shown.is_running():
		_shown.step(dt)
		var whole := int(floorf(_shown.value))
		if whole != _last_whole:
			_last_whole = whole
			if _last_tick_at < 0.0 or _clock - _last_tick_at > TICK_GAP_S:
				_last_tick_at = _clock
				if on_tick.is_valid():
					on_tick.call()
		queue_redraw()
		busy = true
	elif _rolling:
		# The roll has landed: the box gives back the digits it no longer needs.
		_rolling = false
		_reserved = Odometer.digit_count(value)
		_aim_width()
		queue_redraw()
	if _bump.is_running():
		_bump.step(dt)
		busy = true
	if _width.is_running():
		_width.step(dt)
		ui_invalidate()
		queue_redraw()
		busy = true
	layer_origin = Vector2(0.5, 0.5)
	set_layer(layer_alpha, Vector2(_bump.value, _bump.value), layer_offset)
	return busy or _width.is_running() or _rolling


func _paint(ds: DrawScope) -> void:
	var v := maxf(_shown.value, 0.0)
	var u := unit
	var right := _width.value - _pad
	var travel := _cap * 1.3
	var places := maxi(_reserved, Odometer.digit_count(int(floorf(v)) + 1))
	for p in places:
		var turns := Odometer.turns(v, p)
		var roll := Odometer.roll(v, p)
		# Blank wheels above the leading digit stay empty until they roll in.
		var blank := p > 0 and turns == 0
		if blank and roll <= 0.0:
			continue
		var cell_x := right - (p + 1) * _advance
		if not blank:
			var d := DIGITS[turns % 10]
			var x := cell_x + (_advance - ArcadeFont.width(d, u)) / 2.0
			ArcadeFont.draw_shadowed(ds, d, x, -roll * travel, u, color, ArcadeFont.SHADOW, 1.0 - roll * roll)
		if roll > 0.0:
			var d := DIGITS[(turns + 1) % 10]
			var x := cell_x + (_advance - ArcadeFont.width(d, u)) / 2.0
			ArcadeFont.draw_shadowed(ds, d, x, (1.0 - roll) * travel, u, color, ArcadeFont.SHADOW, roll)
