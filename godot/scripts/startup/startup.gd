class_name Startup
extends RefCounted
## startup/Startup.kt: the app's startup log. build-13 wrote to logcat under [constant TAG]
## (`adb logcat -s PocketArcadeStartup`); here every line goes through Godot's print (which reaches
## logcat on Android), prefixed with the tag, so the numbers can be read off a phone:
##  - cold start to the first frame, to the title (the boot plan's steps each timed on the way),
##  - title tap to the first hall frame,
##  - leaving a machine to the first hall frame,
##  - buying a decoration to the hall showing it.
##
## "Since the origin" counts from the engine's start (Time.get_ticks_msec), which is the process's
## own start for a Godot app (build-13 asked Process.getStartElapsedRealtime).

const TAG := "PocketArcadeStartup"

## Steps quicker than this aren't worth a log line each.
const LOG_STEP_MS := 4

## Where the lines go: print by default; tests may swap in their own (one String argument), or an
## empty Callable for print.
static var sink: Callable = Callable()

static var _clock: StageClock = null

## The span the next hall frame closes, or "".
static var _hall_span := ""

## Time that only runs while frames do, in milliseconds, at most 100 a frame: for timeouts that must
## not fire because the app sat in the background (no frames come then). Fed by [method tick_frame].
static var frame_clock_ms := 0
static var _last_frame_ns := 0

## Whether loading has the screen to itself (a loading screen is up) rather than sharing it with
## something being watched (the title): set every frame by the loading gate, read by [WarmRun] to
## decide how many warm-up pictures to keep in flight.
static var urgent := false


static func _emit(line: String) -> void:
	if sink.is_valid():
		sink.call(line)
	else:
		print(TAG, ": ", line)


## A warning line (Kotlin's Log.w / Log.e): printed to the error stream, never as an engine error.
static func warn(line: String) -> void:
	if sink.is_valid():
		sink.call(line)
	else:
		printerr(TAG, ": ", line)


static func _get_clock() -> StageClock:
	if _clock == null:
		_clock = StageClock.new(func() -> int: return Time.get_ticks_msec(), func(line: String) -> void: Startup._emit(line))
		# The engine's clock starts with the process (build-13: Process.getStartElapsedRealtime).
		_clock.set_origin(0)
	return _clock


## Called once a frame by whatever drives loading, with the frame's time in nanoseconds.
static func tick_frame(frame_time_ns: int) -> void:
	if _last_frame_ns != 0:
		frame_clock_ms += clampi((frame_time_ns - _last_frame_ns) / 1_000_000, 0, 100)
	_last_frame_ns = frame_time_ns


static func mark(stage: String) -> void:
	_get_clock().mark(stage)


static func begin(span: String) -> void:
	_get_clock().begin(span)


static func end(span: String) -> int:
	return _get_clock().end(span)


## Times [param span] from now until the hall draws its next frame ([method hall_frame_drawn]).
static func await_hall_frame(span: String) -> void:
	_get_clock().begin(span)
	_hall_span = span


## Called by the hall after it hands a frame to the GPU: closes a span waiting for one.
static func hall_frame_drawn() -> void:
	if _hall_span.is_empty():
		return
	var span := _hall_span
	_hall_span = ""
	_get_clock().end(span)


## A [LoadListener] that logs each step's time under [param plan]'s name (only steps worth a line).
static func listener(plan: String) -> LoadListener:
	return _LogListener.new(plan)


## Forgets the frame clock and the open spans (tests).
static func reset() -> void:
	_clock = null
	_hall_span = ""
	frame_clock_ms = 0
	_last_frame_ns = 0
	urgent = false


class _LogListener:
	extends LoadListener

	var plan: String

	func _init(p_plan: String) -> void:
		plan = p_plan

	func on_step_done(step: LoadStep, index: int, ns: int, failure: String) -> void:
		var ms := ns / 1_000_000
		if not failure.is_empty():
			Startup.warn("%s #%d %s failed after %d ms: %s" % [plan, index, step.name, ms, failure])
		elif ms >= Startup.LOG_STEP_MS:
			Startup._emit("%s #%d %s: %d ms (weight %s)" % [plan, index, step.name, ms, str(step.weight)])

	func on_timeout(step: LoadStep.Wait) -> void:
		Startup.warn("%s %s timed out after %d ms; carrying on" % [plan, step.name, step.timeout_ms])


## Stage timings for the log (startup/Startup.kt StageClock). [method mark] says how long since the
## last mark and since the origin, [method begin] and [method end] time a span that other code
## starts and finishes (title tap to the first hall frame). Pure but for its two injected functions
## (milliseconds now, and where a line goes), so the tests give it a fake clock.
class StageClock:
	extends RefCounted

	var _now: Callable
	var _emit_line: Callable
	var _origin: int
	var _last: int
	var _open := {}

	## [param origin] defaults to the clock's time now (Kotlin's `origin: Long = now()`).
	func _init(now: Callable, emit: Callable, origin: Variant = null) -> void:
		_now = now
		_emit_line = emit
		_origin = int(now.call()) if origin == null else int(origin)
		_last = _origin

	## Restarts the "since the origin" count at [param ms] on this clock (the process's own start, say).
	func set_origin(ms: int) -> void:
		_origin = ms

	## Logs [param stage] with the time since the previous mark and since the origin.
	func mark(stage: String) -> void:
		var t := int(_now.call())
		_emit_line.call("%s: +%d ms (%d ms in)" % [stage, t - _last, t - _origin])
		_last = t

	## Starts timing [param span]. Starting it again restarts it.
	func begin(span: String) -> void:
		_open[span] = int(_now.call())

	## Whether [param span] has begun and not ended.
	func is_open(span: String) -> bool:
		return _open.has(span)

	## Logs how long [param span] took and returns that in ms, or -1 if it never began.
	func end(span: String) -> int:
		if not _open.has(span):
			return -1
		var t0: int = _open[span]
		_open.erase(span)
		var d := int(_now.call()) - t0
		_emit_line.call("%s: %d ms" % [span, d])
		return d
