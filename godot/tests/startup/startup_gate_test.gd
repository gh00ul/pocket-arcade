extends PaTest
## startup/StartupGateTest.kt: the loading gate's two plans on a frame clock that ticks as fast as
## the gate asks for frames (16.7 ms of frame time apiece).

const FRAME_NS := 16_666_667


class Frames:
	var now := 0
	var count := 0

	## One frame: Kotlin's withFrameNanos on the fake clock.
	func tick(g: StartupGate) -> void:
		now += 16_666_667
		count += 1
		g.frame(now)


var _lines: Array = []


func before_each() -> void:
	Startup.reset()
	_lines = []
	Startup.sink = func(line: String) -> void: _lines.append(line)


func after_each() -> void:
	Startup.sink = Callable()
	Startup.reset()


func _steps(prefix: String, n: int, events: Array, weight: float = 1.0) -> LoadPlan:
	var s: Array = []
	for i in n:
		s.append(LoadStep.Work.new("%s %d" % [prefix, i], weight, func() -> void: events.append("%s %d" % [prefix, i])))
	return LoadPlan.new(s)


func _gate(boot: LoadPlan, hall: Callable) -> StartupGate:
	return StartupGate.new(boot, hall, func() -> int: return FRAME_NS)


## Runs [param g] to the end on a fake frame clock; [param urgent] may look at the frame count.
func _run_gate(g: StartupGate, frames: Frames = null, urgent: Callable = Callable()) -> Frames:
	var f := frames if frames != null else Frames.new()
	var u := urgent if urgent.is_valid() else func(_f: Frames) -> bool: return true
	g.run(func() -> bool: return u.call(f))
	var guard := 0
	while not g.finished() and guard < 10000:
		f.tick(g)
		guard += 1
	assert_true(g.finished(), "the gate finished")
	return f


func test_the_title_comes_first_then_the_hall() -> void:
	var events: Array = []
	var gb: Array = [null]
	var boot_seen: Array = []
	var boot_steps: Array = []
	for i in 3:
		boot_steps.append(LoadStep.Work.new("boot %d" % i, 1.0, func() -> void:
			boot_seen.append((gb[0] as StartupGate).boot_done)
			events.append("boot %d" % i)))
	var hall_seen: Array = []
	var hall_steps: Array = []
	for i in 4:
		hall_steps.append(LoadStep.Work.new("hall %d" % i, 1.0, func() -> void:
			hall_seen.append((gb[0] as StartupGate).boot_done)
			events.append("hall %d" % i)))
	var hall := LoadPlan.new(hall_steps)
	var g := _gate(LoadPlan.new(boot_steps), func() -> LoadPlan: return hall)
	gb[0] = g
	assert_false(g.boot_done)
	assert_false(g.hall_ready)
	_run_gate(g)
	assert_true(g.boot_done)
	assert_true(g.hall_ready)
	assert_eq(["boot 0", "boot 1", "boot 2", "hall 0", "hall 1", "hall 2", "hall 3"], events)
	assert_false(boot_seen.has(true), "the title isn't shown while its own plan still runs")
	assert_false(hall_seen.has(false), "the hall plan only starts once the title can show")
	assert_eq(1.0, g.progress)
	gb[0] = null  # the steps hold the gate that holds them: let go so nothing leaks


func test_progress_of_each_plan_starts_over_and_only_rises() -> void:
	var gb: Array = [null]
	var seen: Array = []
	var boot_steps: Array = []
	var hall_steps: Array = []
	for i in 8:
		boot_steps.append(LoadStep.Work.new("boot %d" % i, 1.0, func() -> void: seen.append([(gb[0] as StartupGate).boot_done, (gb[0] as StartupGate).progress])))
		hall_steps.append(LoadStep.Work.new("hall %d" % i, 1.0, func() -> void: seen.append([(gb[0] as StartupGate).boot_done, (gb[0] as StartupGate).progress])))
	var hall := LoadPlan.new(hall_steps)
	var g := _gate(LoadPlan.new(boot_steps), func() -> LoadPlan: return hall)
	gb[0] = g
	_run_gate(g)
	for plan in [false, true]:
		var values: Array = []
		for s: Array in seen:
			if s[0] == plan:
				values.append(s[1])
		for i in range(1, values.size()):
			assert_true(values[i] >= values[i - 1], "progress fell in the %s plan" % ("hall" if plan else "boot"))
	var first_hall := -1.0
	for s: Array in seen:
		if s[0]:
			first_hall = s[1]
			break
	assert_true(first_hall >= 0.0 and first_hall <= 0.3, "the hall plan's bar starts again from the start")
	gb[0] = null


func test_the_hall_waits_quietly_behind_the_title_until_its_first_moments_have_played() -> void:
	var events: Array = []
	var frames := Frames.new()
	var started_at := [-1]
	var hall_steps: Array = []
	for i in 3:
		hall_steps.append(LoadStep.Work.new("hall %d" % i, 1.0, func() -> void:
			if started_at[0] < 0:
				started_at[0] = frames.count
			events.append("hall %d" % i)))
	var hall := LoadPlan.new(hall_steps)
	var g := _gate(_steps("boot", 1, events), func() -> LoadPlan: return hall)
	_run_gate(g, frames, func(_f: Frames) -> bool: return false)
	assert_true(g.hall_ready)
	var quiet_frames := StartupGate.TITLE_QUIET_NS / FRAME_NS
	assert_true(started_at[0] >= quiet_frames, "the hall began after %d frames, the title's quiet time is %d" % [started_at[0], quiet_frames])


func test_a_tap_during_the_quiet_time_starts_the_hall_at_once() -> void:
	var events: Array = []
	var frames := Frames.new()
	var started_at := [-1]
	var hall_steps: Array = []
	for i in 3:
		hall_steps.append(LoadStep.Work.new("hall %d" % i, 1.0, func() -> void:
			if started_at[0] < 0:
				started_at[0] = frames.count
			events.append("hall %d" % i)))
	var hall := LoadPlan.new(hall_steps)
	var g := _gate(_steps("boot", 1, events), func() -> LoadPlan: return hall)
	# Tapped on frame 10: the quiet time (54 frames) is cut short.
	_run_gate(g, frames, func(f: Frames) -> bool: return f.count >= 10)
	assert_true(g.hall_ready)
	assert_true(started_at[0] >= 10 and started_at[0] <= 14, "started on frame %d" % started_at[0])


func test_a_step_too_big_for_a_title_frame_still_gets_its_turn() -> void:
	var events: Array = []
	var frames := Frames.new()
	var ran_at := [-1]
	# Expected to take 150 ms: far more than the slack of a title frame, so only a forced slice runs it.
	var hall := LoadPlan.new([LoadStep.Work.new("heavy", 100.0, func() -> void: ran_at[0] = frames.count)])
	var g := _gate(_steps("boot", 1, events), func() -> LoadPlan: return hall)
	_run_gate(g, frames, func(_f: Frames) -> bool: return false)
	assert_true(g.hall_ready)
	var quiet_frames := StartupGate.TITLE_QUIET_NS / FRAME_NS
	var force := StartupGate.LoadBudget.FORCE_AFTER_FRAMES
	assert_true(ran_at[0] >= quiet_frames + force, "forced after %d idle frames (ran on %d)" % [force, ran_at[0]])
	assert_true(ran_at[0] <= quiet_frames + force + 8, "but not much later (ran on %d)" % ran_at[0])


func test_a_heavy_step_runs_at_once_once_the_hall_is_wanted() -> void:
	var events: Array = []
	var frames := Frames.new()
	var ran_at := [-1]
	var hall := LoadPlan.new([LoadStep.Work.new("heavy", 100.0, func() -> void: ran_at[0] = frames.count)])
	var g := _gate(_steps("boot", 1, events), func() -> LoadPlan: return hall)
	_run_gate(g, frames, func(_f: Frames) -> bool: return true)
	assert_true(ran_at[0] >= 1 and ran_at[0] <= 8, "ran on frame %d" % ran_at[0])


func test_a_plan_that_cannot_be_made_lets_the_screens_through() -> void:
	var events: Array = []
	# Kotlin: the hall plan's lambda throws ("no hall today"); here it can't give a plan.
	var g := _gate(_steps("boot", 2, events), func() -> Variant: return null)
	_run_gate(g)
	assert_true(g.boot_done)
	assert_true(g.hall_ready, "the hall builds itself where it is drawn, rather than the loading screen staying up for ever")


func test_a_step_that_throws_does_not_stop_the_startup() -> void:
	var events: Array = []
	var boot := LoadPlan.new([
		LoadStep.Work.new("bad", 1.0, func() -> String: return "font missing"),
		LoadStep.Work.new("good", 1.0, func() -> void: events.append("good")),
	])
	var hall := _steps("hall", 1, events)
	var g := _gate(boot, func() -> LoadPlan: return hall)
	_run_gate(g)
	assert_true(g.hall_ready)
	assert_eq(["good", "hall 0"], events)


func test_the_label_names_what_is_being_made() -> void:
	var labels: Array = []
	var gb: Array = [null]
	var boot := LoadPlan.new([
		LoadStep.Work.new("PAINTING", 1.0, func() -> void: labels.append((gb[0] as StartupGate).label)),
		LoadStep.Work.new("BUILDING", 1.0, func() -> void: labels.append((gb[0] as StartupGate).label)),
	])
	var g := _gate(boot, func() -> LoadPlan: return LoadPlan.EMPTY)
	gb[0] = g
	_run_gate(g)
	assert_eq("PAINTING", labels[0], "the label is set before the first step runs, then follows the plan a slice at a time")
	gb[0] = null
