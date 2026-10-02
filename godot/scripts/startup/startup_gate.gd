class_name StartupGate
extends RefCounted
## startup/StartupGate.kt: the app's loading gate. It runs two plans, one after the other, a slice a
## frame on the main thread (see [LoadDriver]): the *boot* plan, which is what the title screen
## needs (it runs at once behind the loading screen), then the *hall* plan, which builds the hall
## and hands it to the GPU. The hall plan starts quietly while the title is up, and runs flat out
## behind the loading screen once the player has tapped to start.
##
## The screens read [member boot_done] (show the title), [member hall_ready] (show the hall) and,
## for the loading screen, [member progress] and [member label] of whichever plan is running.
## [member progress] restarts at 0 for the hall plan.
##
## build-13 ran this as one coroutine that waited for frames (`withFrameNanos`). Here [method run]
## starts it and [method frame] is that frame callback, called once a frame with the frame's time:
## between two frames the gate moves on exactly where the coroutine would have (a plan that has
## finished hands over to the next before the following frame), so every plan sees the same frames
## build-13's did. A plan that can't be made ([member hall_plan] returning null, or a plan holding
## a step with a bad weight) lets the screens through, as build-13's catch did.

## The title plays on its own for this long (nanoseconds of frame time) before the hall starts
## loading behind it.
const TITLE_QUIET_NS := 900_000_000

enum _State { IDLE, BOOT, QUIET, HALL, DONE }

## True once the title screen has what it needs.
var boot_done := false
## True once the hall is built and on the GPU.
var hall_ready := false
## Progress of the plan that is running, 0 to 1.
var progress := 0.0
## What the running plan is doing now, for the loading screen ("LAYING THE CARPET").
var label := ""

var boot_plan: LoadPlan
## Makes the hall plan when its turn comes (returns a [LoadPlan], or null if it can't be made).
var hall_plan: Callable
var _vsync: Callable
var _urgent: Callable = Callable()
var _state := _State.IDLE
var _driver: LoadDriver = null
var _plan_name := ""
var _plan_urgent: Callable = Callable()
var _last := 0
var _idle := 0
var _quiet := 0
var _quiet_last := 0


## [param p_vsync_ns] returns one frame's length in nanoseconds (by default from the display's
## refresh rate).
func _init(p_boot_plan: LoadPlan, p_hall_plan: Callable, p_vsync_ns: Callable = Callable()) -> void:
	boot_plan = p_boot_plan
	hall_plan = p_hall_plan
	_vsync = p_vsync_ns


func _vsync_now() -> int:
	if _vsync.is_valid():
		return int(_vsync.call())
	return LoadBudget.vsync_ns(GfxQuality.display_hz)


## Whether both plans have run (or been given up on, or cancelled).
func finished() -> bool:
	return _state == _State.DONE


## Starts both plans. [param urgent] says whether the hall is wanted now (the player has left the
## title): read every frame. Call [method frame] once a frame from then on.
func run(urgent: Callable) -> void:
	if _state != _State.IDLE:
		return
	_urgent = urgent
	if boot_plan == null or not boot_plan.is_valid():
		_give_up("the boot plan could not be made")
		return
	_start_plan("boot", boot_plan, func() -> bool: return true)
	_state = _State.BOOT
	_settle()


## One frame, at [param now_ns] nanoseconds (the frame clock's time).
func frame(now_ns: int) -> void:
	if _state == _State.IDLE or _state == _State.DONE:
		return
	Startup.tick_frame(now_ns)
	if _state == _State.QUIET:
		# Let the title's first moments play undisturbed.
		if _quiet_last != 0:
			_quiet += mini(now_ns - _quiet_last, 100_000_000)
		_quiet_last = now_ns
	else:
		_plan_frame(now_ns)
	_settle()


## Stops for good (the app went away): a step already running can't be interrupted; none starts
## after this.
func cancel() -> void:
	if _driver != null:
		_driver.cancel()
	_state = _State.DONE


func _plan_frame(now_ns: int) -> void:
	var frame_ns := 0 if _last == 0 else now_ns - _last
	_last = now_ns
	var wanted := bool(_plan_urgent.call())
	Startup.urgent = wanted
	var slice := LoadBudget.slice_ns(wanted, frame_ns, _vsync_now())
	if slice > 0:
		var before := _driver.steps_done
		# While the title is up a step must fit the frame's slack; one that never will still gets its turn.
		_driver.advance(slice, not wanted and _idle < LoadBudget.FORCE_AFTER_FRAMES)
		_idle = 0 if _driver.steps_done > before else _idle + 1
	progress = _driver.progress
	label = _driver.label


## What the coroutine did between two frames: finish a plan, wait out the quiet, start the next.
func _settle() -> void:
	while true:
		if _state == _State.BOOT and _driver.finished:
			_end_plan()
			boot_done = true
			Startup.mark("boot plan done: the title can show")
			_state = _State.QUIET
			_quiet = 0
			_quiet_last = 0
			continue
		if _state == _State.QUIET and (_quiet >= TITLE_QUIET_NS or _wanted()):
			var plan: Variant = hall_plan.call() if hall_plan.is_valid() else null
			if not (plan is LoadPlan) or not (plan as LoadPlan).is_valid():
				_give_up("the hall plan could not be made")
				return
			_start_plan("hall", plan, _urgent)
			_state = _State.HALL
			continue
		if _state == _State.HALL and _driver.finished:
			_end_plan()
			hall_ready = true
			Startup.mark("hall plan done: the hall can show")
			_state = _State.DONE
		return


func _wanted() -> bool:
	return _urgent.is_valid() and bool(_urgent.call())


func _start_plan(p_name: String, plan: LoadPlan, urgent: Callable) -> void:
	_plan_name = p_name
	_plan_urgent = urgent if urgent.is_valid() else func() -> bool: return false
	_driver = LoadDriver.new(plan, Callable(), Startup.listener(p_name))
	progress = 0.0
	label = _driver.label
	_last = 0
	_idle = 0


func _end_plan() -> void:
	_driver.cancel()
	if not _driver.failures.is_empty():
		Startup.mark("%s plan had %d failing step(s)" % [_plan_name, _driver.failures.size()])
	progress = 1.0


## A plan that can't even be made: let the screens go ahead and build what they need themselves,
## as they did before there was a plan, rather than stay on a loading screen for good.
func _give_up(why: String) -> void:
	Startup.warn("Loading plan failed (%s); the screens will build what they need themselves" % why)
	if _driver != null:
		_driver.cancel()
	boot_done = true
	hall_ready = true
	_state = _State.DONE


## startup/StartupGate.kt LoadBudget: how much of a frame loading may take. A loading screen draws
## next to nothing, so most of the frame is the loader's; while a screen someone is watching is up
## (the title) loading takes only the slack, and steps back for a frame after one that ran long, so
## the picture never judders for the sake of a bar nobody is looking at.
class LoadBudget:
	## The share of a frame a loading screen gives to loading, and the title gives to it.
	const URGENT_SHARE := 0.6
	const GENTLE_SHARE := 0.2

	## A gentle slice is skipped when the last frame took longer than this many frames: frames come
	## in whole vsyncs, so anything over one is a frame that missed its time.
	const STRUGGLING := 1.25

	## A gentle load whose next step is too big for the slack of a frame forces it through anyway
	## (one late frame) after this many frames without progress, so it can't wait for ever.
	const FORCE_AFTER_FRAMES := 30

	## One frame's length in nanoseconds on a display refreshing at [param hz].
	static func vsync_ns(hz: float) -> int:
		return int(1_000_000_000.0 / clampf(hz, 24.0, 240.0))

	## How long this frame may spend loading, in nanoseconds; 0 means not at all. [param urgent] is
	## a loading screen (nothing else to keep smooth); [param frame_ns] is how long the frame before
	## took (0 if there was none).
	static func slice_ns(urgent: bool, frame_ns: int, p_vsync_ns: int) -> int:
		if urgent:
			return int(p_vsync_ns * URGENT_SHARE)
		if frame_ns >= 1 and frame_ns <= int(p_vsync_ns * STRUGGLING):
			return int(p_vsync_ns * GENTLE_SHARE)
		return 0
