class_name LoadDriver
extends RefCounted
## startup/LoadPlan.kt LoadDriver: runs a [LoadPlan] a slice at a time, so a loading screen (or a
## title screen) keeps animating while the work goes on. The caller asks for a slice once a frame,
## on the main thread (the painting code shares state and isn't thread safe), and gives it a time
## budget.
##
## A slice runs whole steps. It starts another only while the time already used plus what the step
## is expected to take fits the budget, so a slice overruns only by a step that turns out longer
## than expected, and never starts a heavy step behind a light one that has used the budget up. The
## first step of a slice always runs, so any budget makes progress, unless the slice is strict (see
## [method advance]), for a screen that must not drop a frame: then a step expected to outlast the
## budget waits for a slice that can hold it, or a forced one. The expected cost of a step is its
## weight times the time per weight the driver has seen so far.
##
## [member progress] only moves up. Meant for one thread: the one that calls [method advance].

var _plan: LoadPlan
var _steps: Array[LoadStep]
var _clock: Callable
var _listener: LoadListener
var _ns_per_weight: float
var _done_weight := 0.0
var _wait_start_ns := -1
var _peak := 0.0

## Index of the step that runs next (the step count once the plan is done).
var index := 0
## True after [method cancel]; nothing more will run.
var cancelled := false
## How many waits gave up.
var timeouts := 0
## The failure messages of work that failed (the plan carried on without it).
var failures := PackedStringArray()

## Steps finished so far, whether their work failed or a wait timed out.
var steps_done: int:
	get:
		return index

## True once every step has finished.
var done: bool:
	get:
		return index >= _steps.size()

## Done or cancelled: the driver has nothing more to do.
var finished: bool:
	get:
		return done or cancelled

## The share of the plan's weight that has finished, 0 to 1 (a wait that reports its own
## [member LoadStep.Wait.fraction] counts that part of its weight). Never goes down.
var progress: float:
	get:
		return _progress()

## What the loading screen should call the current stage: the step about to run, else the last one.
var label: String:
	get:
		if index < _steps.size():
			return _steps[index].name
		if not _steps.is_empty():
			return _steps[_steps.size() - 1].name
		return ""


## [param p_clock] returns nanoseconds (Kotlin's System::nanoTime by default);
## [param initial_ns_per_weight] is what a step of weight 1 is expected to cost until the first has
## been seen.
func _init(p_plan: LoadPlan, p_clock: Callable = Callable(), p_listener: LoadListener = null, initial_ns_per_weight: int = 1_500_000) -> void:
	_plan = p_plan
	_steps = p_plan.steps
	_clock = p_clock
	_listener = p_listener
	_ns_per_weight = maxf(float(initial_ns_per_weight), 1.0)


func _now() -> int:
	if _clock.is_valid():
		return int(_clock.call())
	return Time.get_ticks_usec() * 1000


func _progress() -> float:
	if _plan.total_weight <= 0.0:
		return 1.0
	var waiting := 0.0
	if index < _steps.size() and _steps[index] is LoadStep.Wait:
		var w: LoadStep.Wait = _steps[index]
		waiting = w.weight * clampf(w.get_fraction(), 0.0, 1.0)
	var p := clampf((_done_weight + waiting) / _plan.total_weight, 0.0, 1.0)
	if p > _peak:
		_peak = p
	return _peak


## Stops the plan for good (the screen went away). A step already running can't be interrupted;
## none starts after this.
func cancel() -> void:
	cancelled = true


## Runs steps for about [param budget_ns] nanoseconds of this driver's clock. Returns whether the
## plan is done. Safe to call again after it is done or cancelled (it does nothing). With
## [param strict] not even the first step runs if it is expected to take longer than the budget
## (the caller comes back with more, or forces it with a slice that isn't strict).
func advance(budget_ns: int, strict: bool = false) -> bool:
	if cancelled or done:
		return done
	var slice_start := _now()
	var ran := 0
	while not cancelled and index < _steps.size():
		var step: LoadStep = _steps[index]
		if step is LoadStep.Work:
			var work: LoadStep.Work = step
			if ran > 0 or strict:
				var expected := int(work.weight * _ns_per_weight)
				if _now() - slice_start + expected > budget_ns:
					break
			var t0 := _now()
			# A stage that fails to preload will build itself when first used, as it did before
			# there was a loading plan: carry on rather than block the whole start.
			var failure := work.perform()
			if not failure.is_empty():
				failures.append(failure)
			var dt := maxi(_now() - t0, 0)
			# Weigh recent steps more, so the guess follows the phone it is running on.
			_ns_per_weight = _ns_per_weight * 0.6 + maxf(dt / float(work.weight), 1.0) * 0.4
			_finish_step(step, dt, failure)
			ran += 1
		else:
			var wait: LoadStep.Wait = step
			var now := _now()
			if _wait_start_ns < 0:
				_wait_start_ns = now
				wait.do_start()
			if wait.is_ready():
				_finish_step(step, _now() - _wait_start_ns, "")
			elif _now() - _wait_start_ns >= wait.timeout_ms * 1_000_000:
				timeouts += 1
				if _listener != null:
					_listener.on_timeout(wait)
				wait.do_timeout()
				_finish_step(step, _now() - _wait_start_ns, "")
			else:
				# Still waiting: nothing further can run this slice.
				break
		if _now() - slice_start >= budget_ns:
			break
	return done


## Runs every remaining step now, however long it takes (waits included, up to their timeouts).
func run_to_end() -> void:
	while not finished:
		advance(0x7FFFFFFFFFFFFFFF / 4)


func _finish_step(step: LoadStep, ns: int, failure: String) -> void:
	_done_weight += step.weight
	var i := index
	index += 1
	_wait_start_ns = -1
	if _listener != null:
		_listener.on_step_done(step, i, ns, failure)
