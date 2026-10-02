class_name LoadStep
extends RefCounted
## startup/LoadPlan.kt LoadStep: one piece of loading work: a [member name] the loading screen can
## show, a [member weight] (its share of the progress bar, and a rough guide to how long it takes:
## a step of weight 4 is expected to take about four times as long as one of weight 1) and either
## work to run ([LoadStep.Work]) or something to wait for ([LoadStep.Wait]).
##
## Kotlin's `require(weight > 0 && weight.isFinite())` threw while the step was being made, so a
## plan holding such a step could never be built. GDScript has no exceptions: the step reports the
## error, is marked not [member valid], and a plan holding it is not valid either
## ([method LoadPlan.is_valid]), which the loading gate treats as a plan that couldn't be made.

var name: String
var weight: float
## False when the weight was not a positive finite number (Kotlin's IllegalArgumentException).
var valid := true


func _init(p_name: String, p_weight: float) -> void:
	name = p_name
	weight = p_weight
	if not (p_weight > 0.0 and is_finite(p_weight)):
		valid = false
		push_error("A load step needs a positive weight, not %s (%s)" % [str(p_weight), p_name])


## Work done on the calling thread when the step is reached.
##
## Kotlin's work could throw, and the driver carried on without it (a stage that fails to preload
## builds itself when first used). Here [member run] reports a failure by returning a non-empty
## String (the message); anything else it returns (nothing, a bool, a count) is success. A script
## error inside it is logged by the engine and the plan carries on, as with Kotlin's catch.
class Work:
	extends LoadStep

	var run: Callable

	func _init(p_name: String, p_weight: float = 1.0, p_run: Callable = Callable()) -> void:
		super(p_name, p_weight)
		run = p_run

	## Runs the work; returns the failure message, or "" when it went well.
	func perform() -> String:
		if not run.is_valid():
			return ""
		var r: Variant = run.call()
		if r is String and not (r as String).is_empty():
			return r
		return ""


## A wait for something outside the driver, the GPU for one: [member start] runs once when the step
## is reached (to send off the request), then [member ready] is polled every slice until it says
## yes. After [member timeout_ms] the step gives up waiting, calls [member on_timeout] and counts as
## finished, so a GPU that never answers can't hold the loading screen up for good. A wait that is
## really a queue of requests can say how far along it is with [member fraction] (0 to 1), so the
## bar moves while it waits.
##
## Arguments are Kotlin's, in its order (`start`, `onTimeout` and `fraction` default to doing
## nothing / 0; `ready` is required in Kotlin: a wait without one counts as ready).
class Wait:
	extends LoadStep

	var timeout_ms: int
	var start: Callable
	var on_timeout: Callable
	var fraction: Callable
	var ready: Callable

	func _init(p_name: String, p_weight: float = 1.0, p_timeout_ms: int = 0, p_start: Callable = Callable(),
			p_on_timeout: Callable = Callable(), p_fraction: Callable = Callable(), p_ready: Callable = Callable()) -> void:
		super(p_name, p_weight)
		timeout_ms = p_timeout_ms
		start = p_start
		on_timeout = p_on_timeout
		fraction = p_fraction
		ready = p_ready

	func do_start() -> void:
		if start.is_valid():
			start.call()

	func do_timeout() -> void:
		if on_timeout.is_valid():
			on_timeout.call()

	func get_fraction() -> float:
		if not fraction.is_valid():
			return 0.0
		return float(fraction.call())

	func is_ready() -> bool:
		if not ready.is_valid():
			return true
		return bool(ready.call())
