class_name Joystick
extends RefCounted
## hub/Joystick.kt: floating virtual joystick: it appears wherever the thumb lands and the base
## trails the thumb if it is dragged past the rim. It is thumb-sized on any screen
## ([method radius_for]). Output is an analog vector through a response curve ([method curve]): a
## small dead zone, fine control near the centre, full walking speed a little short of the rim, and
## [member run] ramping up at the rim. With [member run_latch] on, reaching the rim locks the run in
## ([member latched]) so the thumb can ease off the rim and keep running, until it lifts or falls
## back under [constant LATCH_RELEASE].

## The base's radius on a phone: about a thumb's reach, in dp...
const RADIUS_DP := 56.0
## ...but never more than this much of the screen's shorter side.
const MAX_SCREEN_FRAC := 0.16
## Deflections under this (of the radius) do nothing, so resting a thumb doesn't creep.
const DEAD_ZONE := 0.1
## The deflection (of the radius) that gives full walking speed.
const FULL_AT := 0.85
## How linear the curve is at the centre (the rest is quadratic): lower is finer.
const LINEAR := 0.4
## [member run] ramps from 0 to 1 between these deflections, right at the rim.
const RUN_FROM := 0.9
const RUN_FULL := 0.98
## A latched run lets go when the thumb comes back under this deflection: half way, so a relaxed
## thumb keeps running and a real ease-off (a turn, a stop) doesn't.
const LATCH_RELEASE := 0.5

var active := false
var pointer_id := -1
var base_x := 0.0
var base_y := 0.0
var knob_x := 0.0
var knob_y := 0.0
## Radius of the base in screen pixels.
var radius := 120.0
var out_x := 0.0
var out_y := 0.0
## 0..1: how hard the thumb is pushing at the rim (first person runs).
var run := 0.0
## Whether reaching the rim latches the run (see [member latched]). Off, running takes the thumb
## at the rim throughout. Set before the touch lands; the world turns it on in first person only.
var run_latch := false
## A run is locked in: full speed in the stick's direction until the thumb lifts or eases under
## [constant LATCH_RELEASE].
var latched := false
## How far (pixels) the thumb has strayed from where it landed; a tap barely moves.
var travel := 0.0
var _down_x := 0.0
var _down_y := 0.0


## The base radius in pixels for a screen [param w] × [param h] at [param density] pixels per dp.
static func radius_for(density: float, w: float, h: float) -> float:
	var by_thumb := RADIUS_DP * maxf(density, 0.5)
	var short := minf(w, h)
	return minf(by_thumb, MAX_SCREEN_FRAC * short) if short > 0.0 else by_thumb


## Output (0..1) for a deflection [param m] (0..1 of the radius).
static func curve(m: float) -> float:
	if m <= DEAD_ZONE:
		return 0.0
	var t := minf((m - DEAD_ZONE) / (FULL_AT - DEAD_ZONE), 1.0)
	return t * (LINEAR + (1.0 - LINEAR) * t)


## How much of a run (0..1) a deflection [param m] asks for.
static func run_for(m: float) -> float:
	return clampf((m - RUN_FROM) / (RUN_FULL - RUN_FROM), 0.0, 1.0)


func down(id: int, x: float, y: float) -> void:
	if active:
		return
	active = true
	pointer_id = id
	base_x = x
	base_y = y
	knob_x = x
	knob_y = y
	_down_x = x
	_down_y = y
	travel = 0.0
	out_x = 0.0
	out_y = 0.0
	run = 0.0
	latched = false


func move(id: int, x: float, y: float) -> void:
	if not active or id != pointer_id:
		return
	travel = maxf(travel, MathUtil.len2(x - _down_x, y - _down_y))
	var dx := x - base_x
	var dy := y - base_y
	var d := MathUtil.len2(dx, dy)
	if d > radius:
		# Drag the base along so reversing direction is instant.
		base_x = x - dx / d * radius
		base_y = y - dy / d * radius
		dx = x - base_x
		dy = y - base_y
	knob_x = x
	knob_y = y
	var l := MathUtil.len2(dx, dy)
	var m := l / radius
	if not run_latch:
		latched = false
	elif not latched:
		latched = m >= RUN_FULL
	elif m < LATCH_RELEASE:
		latched = false
	# A locked run holds full speed whichever way the thumb points, however far it has eased off.
	var out := 1.0 if latched else curve(m)
	if out <= 0.0:
		out_x = 0.0
		out_y = 0.0
	else:
		out_x = dx / l * out
		out_y = dy / l * out
	run = 1.0 if latched else run_for(m)


func up(id: int) -> void:
	if id != pointer_id:
		return
	release()


func release() -> void:
	active = false
	pointer_id = -1
	out_x = 0.0
	out_y = 0.0
	run = 0.0
	latched = false
