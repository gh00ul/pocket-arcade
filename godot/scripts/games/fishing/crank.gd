class_name Crank
extends RefCounted
## games/fishing/FishingGame.kt Crank: the reel's crank: turns one finger drawing circles round the
## reel's centre into how far and how fast the reel turns. Each sample's angle round (cx, cy) is
## compared with the last one from the same pointer; clockwise on screen (y down) is positive and
## reels in. Samples nearer the hub than the hub radius are skipped, since the angle there is mostly
## finger jitter.

## Share of each new sample's speed blended into [member rate].
const SMOOTH := 0.35
## Seconds without a sample before the reel spins down.
const STALE := 0.07
const SPIN_DOWN := 14.0
## Shortest gap between samples used for a speed, and the fastest believable spin.
const MIN_GAP_MS := 4
const MAX_RATE := 60.0

var cx: float
var cy: float
var _hub_radius: float

## The pointer holding the crank, or -1.
var id := -1
## Smoothed turning speed in radians per second, positive clockwise.
var rate := 0.0
## Total radians turned (for drawing the handle).
var angle := 0.0
var _pending := 0.0
var _last_a := 0.0
var _last_ms := 0
var _has_a := false
var _idle := 0.0


func _init(p_cx: float, p_cy: float, hub_radius: float) -> void:
	cx = p_cx
	cy = p_cy
	_hub_radius = hub_radius


## Kotlin's `held`.
func held() -> bool:
	return id >= 0


## Takes hold with pointer [param pid]; false if another finger already has it.
func grab(pid: int, x: float, y: float, ms: int) -> bool:
	if id >= 0:
		return false
	id = pid
	_has_a = false
	_idle = 0.0
	_sample(x, y, ms)
	return true


func move(pid: int, x: float, y: float, ms: int) -> void:
	if id >= 0 and pid == id:
		_sample(x, y, ms)


func release(pid: int) -> void:
	if id >= 0 and pid == id:
		id = -1
		_has_a = false


## Lets go at once and forgets any turning not yet taken.
func cancel() -> void:
	id = -1
	_has_a = false
	rate = 0.0
	_pending = 0.0


## Radians turned since the last call (positive reels in).
func take() -> float:
	var p := _pending
	_pending = 0.0
	return p


## Advances [param dt] seconds of game time: a still or released crank spins down.
func age(dt: float) -> void:
	_idle += dt
	if id < 0 or _idle > STALE:
		rate *= exp(-SPIN_DOWN * dt)
	if absf(rate) < 1e-3:
		rate = 0.0


func _sample(x: float, y: float, ms: int) -> void:
	var dx := x - cx
	var dy := y - cy
	if dx * dx + dy * dy < _hub_radius * _hub_radius:
		# Too near the hub to tell which way the finger is going: start afresh after it.
		_has_a = false
		return
	var a := atan2(dy, dx)
	if _has_a:
		var d := a - _last_a
		if d > PI:
			d -= TAU
		elif d < -PI:
			d += TAU
		var gap := maxi(ms - _last_ms, MIN_GAP_MS) / 1000.0
		var inst := clampf(d / gap, -MAX_RATE, MAX_RATE)
		rate += (inst - rate) * SMOOTH
		_pending += d
		angle += d
		_idle = 0.0
	_last_a = a
	_last_ms = ms
	_has_a = true
