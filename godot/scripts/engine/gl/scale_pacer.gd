class_name ScalePacer
extends RefCounted
## engine/gl/ScalePacer.kt: chooses the render scale, and how much else to spend, from how long
## frames take. Pure logic (no rendering), fed once per drawn frame. Times are microseconds here
## (Godot's clock; build-13 used nanoseconds).
##
## - After a [method reset] (new screen) the first [member warmup_usec] are ignored: shader
##   compiles and uploads make a slow burst that says nothing about the scene's real cost.
## - It lowers the scale by a step when [member slow_to_drop] frames in a row miss 24 ms.
## - It raises a step when at least 95% of the last [member window] frames made 60 fps, or sooner
##   (after a quarter of the window) when the GPU time is known and clearly under budget.
## - Going back up to a scale that was just too slow takes 2, 4, then 8 windows.
## - Above the rung's ceiling (up to its boost) it only goes when the GPU time is known and the next
##   step (cost ∝ scale²) is predicted to stay under two thirds of a frame.
##
## On top of the scale sits the ladder of quality rungs ([member GfxQuality.LADDER]): still slow at
## the floor scale, it steps down a rung; after RUNG_UP_FRAMES smooth frames at the ceiling it steps
## back up, with the same doubling wait for a rung that was just too slow. The 24 / 17.5 ms
## thresholds are for 60 fps; with a 30 fps cap ([member target_fps]) they double.

const STEP := 0.1
const SLOW_MS := 24.0
const FAST_MS := 17.5
## Intervals longer than this are pauses or loads, not frames.
const HITCH_MS := 200.0
## Two thirds of a 60 fps frame: the GPU budget for going above the ceiling.
const GPU_BUDGET_MS := 11.0
## Smooth frames at a rung's ceiling before trying the next rung up (about 6 s at 60 fps).
const RUNG_UP_FRAMES := 360
## The GPU time (ms per 60 fps frame) under which a rung up is tried; unknown counts as fine.
const RUNG_UP_GPU_MS := 9.0
const INT_MAX := 9223372036854775807

var start: float
var warmup_usec: int
var window: int
var slow_to_drop: int

## Without a ladder there is just one rung, made from the plain floor, ceiling and boost.
var _rungs: Array = []
var _top := 0
var _bottom := 0

## The quality rung in force (0 = best).
var rung := 0
var scale := 0.8
## 60 or 30: the frame rate the slow and fast limits are set for (see [method set_target_fps]).
var target_fps := 60

var _warmup_until := -INT_MAX
var _started := false
var _last := 0
var _slow_run := 0
var _frames := 0
var _fast := 0
var _gpu_sum := 0.0
var _gpu_count := 0
## The scale last dropped from, and how many windows it takes to try it again.
var _failed_at := INF
var _backoff := 1
## Smooth frames spent at this rung's ceiling.
var _climb_frames := 0
## The rung last dropped from (-1 = none), and how many times RUNG_UP_FRAMES a return to it takes.
var _rung_failed_at := -1
var _rung_backoff := 1


func _init(floor_scale: float = 0.5, ceiling: float = 0.8, boost: float = 1.0, p_start: float = 0.8,
		p_warmup_usec: int = 2000000, p_window: int = 120, p_slow_to_drop: int = 30, ladder: Array = [],
		start_rung: int = 0, top_rung: int = 0, bottom_rung: int = INT_MAX) -> void:
	start = p_start
	warmup_usec = p_warmup_usec
	window = p_window
	slow_to_drop = p_slow_to_drop
	if ladder.is_empty():
		_rungs = [GfxQuality.Rung.new(floor_scale, ceiling, boost, 4, 4, GfxQuality.Reflections.MIRROR, RenderPass.MAX_LIGHTS)]
	else:
		_rungs = ladder
	var last := _rungs.size() - 1
	_top = clampi(top_rung, 0, last)
	_bottom = clampi(bottom_rung, _top, last)
	rung = clampi(start_rung, _top, _bottom)
	var r: GfxQuality.Rung = _rungs[rung]
	scale = clampf(start, r.scale_floor, r.scale_ceiling)


## Kotlin's `targetFps` setter: anything at or under 30 is 30, else 60.
func set_target_fps(v: int) -> void:
	target_fps = 30 if v <= 30 else 60


func _frame_k() -> float:
	return 60.0 / target_fps


## Starts over at [member start] (or keeps the current scale), ignoring the next warm-up.
func reset(now_usec: int, keep_scale: bool = false) -> void:
	if not keep_scale:
		var r: GfxQuality.Rung = _rungs[rung]
		scale = clampf(start, r.scale_floor, r.scale_ceiling)
	_failed_at = INF
	_backoff = 1
	_rung_failed_at = -1
	_rung_backoff = 1
	_climb_frames = 0
	_warmup_until = now_usec + warmup_usec
	_started = true
	_last = 0
	_clear_window()
	_slow_run = 0


## Confines the ladder to rungs [param top_rung]..[param bottom_rung] (a tier changed), moving the
## current rung inside if it is outside. Gentle: the scale and the warm-up are left alone.
func set_limits(top_rung: int, bottom_rung: int) -> void:
	var last := _rungs.size() - 1
	_top = clampi(top_rung, 0, last)
	_bottom = clampi(bottom_rung, _top, last)
	_move_to(clampi(rung, _top, _bottom))
	_climb_frames = 0


## Jumps to [param r] (a device's starting guess), within the limits.
func jump_to(r: int) -> void:
	_move_to(clampi(r, _top, _bottom))
	_climb_frames = 0
	_slow_run = 0
	_clear_window()


func _move_to(r: int) -> void:
	rung = r
	var g: GfxQuality.Rung = _rungs[r]
	scale = clampf(scale, g.scale_floor, g.scale_ceiling)


func _clear_window() -> void:
	_frames = 0
	_fast = 0
	_gpu_sum = 0.0
	_gpu_count = 0


## A frame was drawn at [param now_usec]; [param gpu_ms] is its GPU time when known (< 0
## otherwise). Returns the scale to render at next; read [member rung] for the rest.
func on_frame(now_usec: int, gpu_ms: float = -1.0) -> float:
	if not _started:
		reset(now_usec, true)
	var last := _last
	_last = now_usec
	if now_usec < _warmup_until or last == 0:
		return scale
	var ms := (now_usec - last) / 1000.0
	if ms >= HITCH_MS:
		return scale
	var k := _frame_k()
	var r: GfxQuality.Rung = _rungs[rung]
	if ms > SLOW_MS * k:
		_slow_run += 1
	else:
		_slow_run = 0
	if _slow_run > slow_to_drop:
		if scale > r.scale_floor + 1e-4:
			_backoff = mini(_backoff * 2, 8) if absf(scale - _failed_at) < 1e-3 else 2
			_failed_at = scale
			scale = maxf(r.scale_floor, scale - STEP)
			_slow_run = 0
			_clear_window()
			return scale
		if rung < _bottom:
			# Already at the floor and still slow: give up an effect instead.
			_rung_backoff = mini(_rung_backoff * 2, 8) if rung == _rung_failed_at else 2
			_rung_failed_at = rung
			_move_to(rung + 1)
			_climb_frames = 0
			_slow_run = 0
			_clear_window()
			return scale
	_frames += 1
	if ms < FAST_MS * k:
		_fast += 1
	if gpu_ms >= 0.0:
		_gpu_sum += gpu_ms
		_gpu_count += 1
	var gpu_avg := _gpu_sum / _gpu_count if _gpu_count > 0 else -1.0
	var next := scale + STEP
	var retry := next >= _failed_at - 1e-3
	var need := window * _backoff if retry else window
	var quick := not retry and gpu_avg >= 0.0 and _frames >= window / 4 and _fast * 20 >= _frames * 19
	if _frames >= need or quick:
		var smooth := _fast * 20 >= _frames * 19
		if smooth:
			var was_at_ceiling := scale >= r.scale_ceiling - 1e-4
			if scale < r.scale_ceiling - 1e-4:
				# Under the ceiling a smooth window is enough; with a GPU time, only if the bigger
				# image is predicted to fit.
				if gpu_avg < 0.0 or predict(gpu_avg, scale, next) < 16.0 * k:
					scale = minf(r.scale_ceiling, next)
			elif scale < r.scale_boost - 1e-4 and gpu_avg >= 0.0 and predict(gpu_avg, scale, next) < GPU_BUDGET_MS * k:
				scale = minf(r.scale_boost, next)
			if was_at_ceiling:
				_climb_frames += _frames
				if rung > _top and _climb_frames >= _climb_need() and (gpu_avg < 0.0 or gpu_avg < RUNG_UP_GPU_MS * k):
					_move_to(rung - 1)
					_climb_frames = 0
			else:
				_climb_frames = 0
		else:
			_climb_frames = 0
		_clear_window()
	return scale


## Smooth frames at the ceiling before the next rung up: more for one that was just too slow.
func _climb_need() -> int:
	return RUNG_UP_FRAMES * _rung_backoff if rung - 1 <= _rung_failed_at else RUNG_UP_FRAMES


## GPU time at scale [param to] given [param ms] at scale [param from]: pixel work goes with the area.
func predict(ms: float, from: float, to: float) -> float:
	return ms * (to * to) / (from * from)
