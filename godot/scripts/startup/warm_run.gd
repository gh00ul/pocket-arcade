class_name WarmRun
extends RefCounted
## startup/WarmRun.kt: feeds the GPU a queue of warm-up pictures and reports how far along it is,
## for a loading plan's [LoadStep.Wait] ([method poll] is its `ready`, [member fraction] its
## progress). Only a few are ever in flight (`depth`): behind a loading screen a few at once, which
## hides the round trip to the renderer and back, and while the title is being drawn one at a time,
## so the renderer gets a normal frame between two uploads instead of one long stall. A GPU that
## stops answering is noticed: after `stall_ms` with nothing back, the run gives up on the GPU for
## good (Warmup.give_up) and reports done, and the hall uploads as it draws, as it did before there
## was a warm-up.
##
## `send` hands job number `i` to the GPU and returns its ticket (Warmup.Ticket: anything with a
## bool `done`); `now` is the clock the stall is timed on (milliseconds; by default
## [member Startup.frame_clock_ms], which stands still while the app is in the background, when the
## GPU can't answer). Main thread only.
##
## Warmup (engine/gl/Warmup.kt) is the look port's `scripts/engine/gl/warmup.gd`; it is reached by
## path ([method warmup_disabled], [method warmup_give_up]) so this runs before it lands. Until then
## a flag here stands in for `Warmup.disabled`.

## How many pictures are in flight at once behind a loading screen.
const DEPTH_LOADING := 3

const WARMUP_PATH := "res://scripts/engine/gl/warmup.gd"

static var _warmup_script: Script = null
static var _warmup_looked := false
## Stands in for Warmup.disabled while the look port's Warmup isn't there.
static var _fallback_disabled := false

var _count: int
var _send: Callable
var _stall_ms: int
var _now: Callable
var _on_stall: Callable
var _depth: Callable
var _next := 0
var _completed := 0
var _in_flight: Array = []
var _progress_at := 0

## How much of the queue the GPU has drawn, 0 to 1.
var fraction: float:
	get:
		return 1.0 if _count <= 0 else float(_completed) / _count


func _init(p_count: int, p_send: Callable, p_stall_ms: int = 3000, p_now: Callable = Callable(),
		p_on_stall: Callable = Callable(), p_depth: Callable = Callable()) -> void:
	_count = p_count
	_send = p_send
	_stall_ms = p_stall_ms
	_now = p_now
	_on_stall = p_on_stall
	_depth = p_depth
	_progress_at = _clock()


func _clock() -> int:
	if _now.is_valid():
		return int(_now.call())
	return Startup.frame_clock_ms


func _depth_now() -> int:
	if _depth.is_valid():
		return int(_depth.call())
	return DEPTH_LOADING if Startup.urgent else 1


## Whether every job has been drawn (or the GPU stopped answering). Call once a slice.
func poll() -> bool:
	while true:
		if warmup_disabled():
			return true
		# The renderer draws them in the order sent: retire what has been answered.
		while not _in_flight.is_empty() and bool(_in_flight[0].done):
			_in_flight.pop_front()
			_completed += 1
			_progress_at = _clock()
		if _next >= _count and _in_flight.is_empty():
			return true
		if not _in_flight.is_empty() and _clock() - _progress_at > _stall_ms:
			if _on_stall.is_valid():
				_on_stall.call()
			warmup_give_up()
			return true
		var room := maxi(_depth_now(), 1) - _in_flight.size()
		if room <= 0 or _next >= _count:
			return false
		if _in_flight.is_empty():
			_progress_at = _clock()
		var sent := 0
		while sent < room and _next < _count:
			_in_flight.append(_send.call(_next))
			_next += 1
			sent += 1
		# Tickets that are already done (the GPU is off, or was given up on) are retired on the next turn round.
		if bool(_in_flight[0].done):
			continue
		return false
	return false


static func _warmup() -> Script:
	if not _warmup_looked:
		_warmup_looked = true
		if ResourceLoader.exists(WARMUP_PATH):
			_warmup_script = load(WARMUP_PATH)
	return _warmup_script


## Warmup.disabled: true once a warm-up has been given up on (later requests are answered at once).
static func warmup_disabled() -> bool:
	var w := _warmup()
	if w != null:
		return bool(w.get("disabled"))
	return _fallback_disabled


## Warmup.giveUp(): the GPU isn't answering, so later requests complete immediately.
static func warmup_give_up() -> void:
	var w := _warmup()
	if w != null:
		w.call("give_up")
	else:
		_fallback_disabled = true


## Puts Warmup back as a fresh start found it (tests: Warmup is process-wide).
static func reset_warmup() -> void:
	var w := _warmup()
	if w != null:
		w.set("disabled", false)
	_fallback_disabled = false
