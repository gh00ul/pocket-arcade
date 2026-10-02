extends PaTest
## startup/LoadPlanTest.kt: the staged loader: budgets, monotone progress, every step exactly once,
## waits, failures and cancel. (Kotlin's thrown exceptions are a step's returned failure here, and
## its IllegalArgumentException for a bad weight is an error plus an invalid step: see LoadStep.)

const MS := 1_000_000


## A clock the steps move themselves.
class FakeClock:
	var ns := 0

	func read() -> int:
		return ns

	func spend_ms(ms: float) -> void:
		ns += int(ms * 1_000_000.0)


## [param n] steps of the given weight that each cost [param ms_per_weight] ms per weight and count
## their runs in [param runs] (an Array of ints).
func _plan(clock: FakeClock, n: int, weight: float = 1.0, ms_per_weight: float = 1.0, runs: Array = []) -> LoadPlan:
	if runs.is_empty():
		runs.resize(n)
		runs.fill(0)
	var steps: Array = []
	for i in n:
		steps.append(LoadStep.Work.new("step %d" % i, weight, func() -> void:
			runs[i] += 1
			clock.spend_ms(weight * ms_per_weight)))
	return LoadPlan.new(steps)


static func _all_one(runs: Array) -> bool:
	for r: int in runs:
		if r != 1:
			return false
	return true


static func _sum(runs: Array) -> int:
	var s := 0
	for r: int in runs:
		s += r
	return s


func test_every_step_runs_exactly_once() -> void:
	var clock := FakeClock.new()
	var runs: Array = []
	runs.resize(25)
	runs.fill(0)
	var driver := LoadDriver.new(_plan(clock, 25, 1.0, 1.0, runs), clock.read)
	var slices := 0
	while not driver.advance(4 * MS):
		slices += 1
	assert_true(driver.done)
	assert_eq(25, driver.steps_done)
	assert_true(_all_one(runs), "every step ran once")
	# Asking again after the end runs nothing and stays done.
	assert_true(driver.advance(4 * MS))
	assert_true(_all_one(runs))
	assert_true(slices > 3, "it took several slices, not one")


func test_a_slice_stays_within_its_budget_when_the_steps_are_evenly_sized() -> void:
	var clock := FakeClock.new()
	var driver := LoadDriver.new(_plan(clock, 60), clock.read)
	var budget := 5 * MS
	var worst := 0
	var slices := 0
	while not driver.done:
		var t0 := clock.ns
		driver.advance(budget)
		worst = maxi(worst, clock.ns - t0)
		slices += 1
	# The first slice has only the initial guess to go on: it may run one step more than it should.
	assert_true(worst <= budget + 1 * MS, "no slice ran longer than the budget plus one step (worst %d ms)" % (worst / MS))
	assert_true(slices >= 60 / 6)


func test_a_heavy_step_is_not_started_behind_light_ones_that_have_used_the_budget() -> void:
	var clock := FakeClock.new()
	var order: Array = []
	var steps: Array = []
	for i in 4:
		steps.append(LoadStep.Work.new("light %d" % i, 1.0, func() -> void:
			order.append("light %d" % i)
			clock.spend_ms(1.0)))
	steps.append(LoadStep.Work.new("heavy", 8.0, func() -> void:
		order.append("heavy")
		clock.spend_ms(8.0)))
	var driver := LoadDriver.new(LoadPlan.new(steps), clock.read, null, 1 * MS)
	# 6 ms: the four light steps use 4, and 4 + the 8 the heavy one is expected to take is too much.
	driver.advance(6 * MS)
	assert_eq(["light 0", "light 1", "light 2", "light 3"], order)
	assert_false(driver.done)
	# A slice of its own runs it: the first step of a slice always goes, whatever it costs.
	driver.advance(6 * MS)
	assert_eq("heavy", order.back())
	assert_true(driver.done)


func test_a_strict_slice_leaves_a_step_that_would_not_fit() -> void:
	var clock := FakeClock.new()
	var order: Array = []
	var steps: Array = [
		LoadStep.Work.new("light", 1.0, func() -> void:
			order.append("light")
			clock.spend_ms(1.0)),
		LoadStep.Work.new("heavy", 10.0, func() -> void:
			order.append("heavy")
			clock.spend_ms(10.0)),
	]
	var driver := LoadDriver.new(LoadPlan.new(steps), clock.read, null, 1 * MS)
	driver.advance(4 * MS, true)
	assert_eq(["light"], order, "the light one fits")
	# The heavy step is expected to take 10 ms: a strict 4 ms slice won't start it, however often it is asked.
	driver.advance(4 * MS, true)
	driver.advance(4 * MS, true)
	assert_eq(["light"], order)
	assert_false(driver.done)
	# A slice that isn't strict (the forced one) runs it, the first step of a slice always going.
	driver.advance(4 * MS)
	assert_eq(["light", "heavy"], order)
	assert_true(driver.done)


func test_a_strict_slice_that_can_hold_the_step_runs_it() -> void:
	var clock := FakeClock.new()
	var ran := [0]
	var steps: Array = [LoadStep.Work.new("a", 2.0, func() -> void:
		ran[0] += 1
		clock.spend_ms(2.0))]
	var driver := LoadDriver.new(LoadPlan.new(steps), clock.read, null, 1 * MS)
	driver.advance(5 * MS, true)
	assert_eq(1, ran[0])


func test_any_budget_makes_progress() -> void:
	var clock := FakeClock.new()
	var driver := LoadDriver.new(_plan(clock, 5, 3.0, 10.0), clock.read)
	var guard := 0
	while not driver.advance(0):
		assert_true(guard < 10, "stuck")
		guard += 1
		if guard > 20:
			break
	assert_eq(5, driver.steps_done)


func test_progress_only_moves_up_and_reaches_one() -> void:
	var clock := FakeClock.new()
	var steps: Array = []
	for i in 12:
		steps.append(LoadStep.Work.new("s%d" % i, float(1 + i % 4), func() -> void: clock.spend_ms(1.0 + i % 3)))
	var driver := LoadDriver.new(LoadPlan.new(steps), clock.read)
	var last := driver.progress
	assert_eq(0.0, last)
	while not driver.done:
		driver.advance(3 * MS)
		assert_true(driver.progress >= last, "progress fell from %s to %s" % [last, driver.progress])
		last = driver.progress
	assert_near(1.0, driver.progress, 1e-6)


func test_progress_follows_the_weights() -> void:
	var clock := FakeClock.new()
	var steps: Array = [
		LoadStep.Work.new("small", 1.0, func() -> void: clock.spend_ms(1.0)),
		LoadStep.Work.new("big", 3.0, func() -> void: clock.spend_ms(3.0)),
	]
	var driver := LoadDriver.new(LoadPlan.new(steps), clock.read)
	driver.advance(1 * MS)
	assert_near(0.25, driver.progress, 1e-6)
	assert_eq("big", driver.label)
	driver.advance(10 * MS)
	assert_near(1.0, driver.progress, 1e-6)
	assert_eq("big", driver.label, "the last stage stays named once done")


func test_an_empty_plan_is_done_at_once() -> void:
	var driver := LoadDriver.new(LoadPlan.EMPTY, func() -> int: return 0)
	assert_true(driver.done)
	assert_eq(1.0, driver.progress)
	assert_true(driver.advance(1))


func test_cancel_stops_what_has_not_started() -> void:
	var clock := FakeClock.new()
	var runs: Array = []
	runs.resize(10)
	runs.fill(0)
	var driver := LoadDriver.new(_plan(clock, 10, 1.0, 1.0, runs), clock.read)
	driver.advance(3 * MS)
	var before := _sum(runs)
	assert_true(before >= 1 and before <= 9)
	driver.cancel()
	assert_false(driver.advance(100 * MS))
	assert_eq(before, _sum(runs), "nothing runs after a cancel")
	assert_true(driver.cancelled)
	assert_true(driver.finished)
	assert_false(driver.done, "a cancelled plan is not a finished load")


func test_a_step_can_cancel_the_rest_of_the_plan() -> void:
	var clock := FakeClock.new()
	var box: Array = [null]
	var later := [0]
	var steps: Array = [
		LoadStep.Work.new("first", 1.0, func() -> void: clock.spend_ms(1.0)),
		LoadStep.Work.new("stop", 1.0, func() -> void: (box[0] as LoadDriver).cancel()),
		LoadStep.Work.new("never", 1.0, func() -> void: later[0] += 1),
	]
	var driver := LoadDriver.new(LoadPlan.new(steps), clock.read)
	box[0] = driver
	driver.advance(100 * MS)
	assert_eq(0, later[0])
	assert_true(driver.cancelled)
	box[0] = null  # the step holds the driver that holds the step: let go so nothing leaks


class _Seen:
	extends LoadListener
	var seen: Array = []

	func on_step_done(_step: LoadStep, _index: int, _ns: int, failure: String) -> void:
		seen.append(failure)


func test_a_failing_step_is_recorded_and_the_rest_still_run() -> void:
	var clock := FakeClock.new()
	var after := [0]
	var steps: Array = [
		# Kotlin: throw IllegalStateException("no such font").
		LoadStep.Work.new("bad", 1.0, func() -> String: return "no such font"),
		LoadStep.Work.new("good", 1.0, func() -> void: after[0] += 1),
	]
	var listener := _Seen.new()
	var driver := LoadDriver.new(LoadPlan.new(steps), clock.read, listener)
	driver.run_to_end()
	assert_true(driver.done)
	assert_eq(1, after[0])
	assert_eq(1, driver.failures.size())
	assert_eq(2, listener.seen.size())
	assert_eq("no such font", listener.seen[0])
	assert_eq("", listener.seen[1])
	assert_near(1.0, driver.progress, 1e-6)


func test_a_wait_holds_the_slice_until_it_is_ready() -> void:
	var clock := FakeClock.new()
	var started := [0]
	var ready := [false]
	var after := [0]
	var steps: Array = [
		LoadStep.Wait.new("gpu", 2.0, 1000, func() -> void: started[0] += 1, Callable(), Callable(), func() -> bool: return ready[0]),
		LoadStep.Work.new("after", 1.0, func() -> void: after[0] += 1),
	]
	var driver := LoadDriver.new(LoadPlan.new(steps), clock.read)
	driver.advance(5 * MS)
	driver.advance(5 * MS)
	driver.advance(5 * MS)
	assert_eq(1, started[0], "started once however often it is polled")
	assert_eq(0, after[0])
	assert_eq(0.0, driver.progress)
	ready[0] = true
	driver.advance(5 * MS)
	assert_eq(1, after[0])
	assert_true(driver.done)
	assert_eq(0, driver.timeouts)


class _Told:
	extends LoadListener
	var told: Array = []

	func on_timeout(step: LoadStep.Wait) -> void:
		told.append(step.name)


func test_a_wait_that_never_answers_gives_up_at_its_timeout() -> void:
	var clock := FakeClock.new()
	var timed_out := [0]
	var after := [0]
	var steps: Array = [
		LoadStep.Wait.new("gpu", 1.0, 500, Callable(), func() -> void: timed_out[0] += 1, Callable(), func() -> bool: return false),
		LoadStep.Work.new("after", 1.0, func() -> void: after[0] += 1),
	]
	var listener := _Told.new()
	var driver := LoadDriver.new(LoadPlan.new(steps), clock.read, listener)
	driver.advance(5 * MS)
	assert_false(driver.done)
	clock.spend_ms(499.0)
	driver.advance(5 * MS)
	assert_false(driver.done, "not yet at the timeout")
	clock.spend_ms(2.0)
	driver.advance(5 * MS)
	assert_true(driver.done)
	assert_eq(1, timed_out[0])
	assert_eq(1, after[0])
	assert_eq(1, driver.timeouts)
	assert_eq(["gpu"], listener.told)


func test_the_timeout_runs_from_when_the_wait_started_not_from_the_plan() -> void:
	var clock := FakeClock.new()
	var steps: Array = [
		LoadStep.Work.new("slow", 1.0, func() -> void: clock.spend_ms(2000.0)),
		LoadStep.Wait.new("gpu", 1.0, 500, Callable(), Callable(), Callable(), func() -> bool: return false),
	]
	var driver := LoadDriver.new(LoadPlan.new(steps), clock.read)
	driver.advance(1 * MS)
	# 2 s went on the slow step, which must not count against the wait.
	driver.advance(1 * MS)
	assert_false(driver.done)
	assert_eq(0, driver.timeouts)


func test_the_driver_learns_what_a_step_costs_on_this_phone() -> void:
	var clock := FakeClock.new()
	# Steps really take 4 ms each; the first guess says 1 ms. After the first slice the guess is right.
	var driver := LoadDriver.new(_plan(clock, 40, 1.0, 4.0), clock.read, null, 1 * MS)
	var worst := 0
	var slices := 0
	while not driver.done:
		var t0 := clock.ns
		driver.advance(9 * MS)
		if slices > 2:
			worst = maxi(worst, clock.ns - t0)
		slices += 1
	assert_true(worst <= 9 * MS, "once it has learnt, slices stay within the budget (worst %d ms)" % (worst / MS))


func test_a_wait_that_reports_its_fraction_moves_the_bar_but_never_backwards() -> void:
	var clock := FakeClock.new()
	var frac := [0.0]
	var steps: Array = [
		LoadStep.Work.new("a", 1.0, func() -> void: clock.spend_ms(1.0)),
		LoadStep.Wait.new("gpu", 3.0, 1000, Callable(), Callable(), func() -> float: return frac[0], func() -> bool: return false),
	]
	var driver := LoadDriver.new(LoadPlan.new(steps), clock.read)
	driver.advance(5 * MS)
	assert_near(0.25, driver.progress, 1e-6)
	frac[0] = 0.5
	assert_near(0.25 + 0.375, driver.progress, 1e-6)
	var seen := driver.progress
	frac[0] = 0.2
	assert_eq(seen, driver.progress, "a wait that reports less than before doesn't pull the bar back")
	frac[0] = 5.0
	assert_true(driver.progress <= 1.0, "a fraction past 1 is clamped")


func test_plans_join_in_order() -> void:
	var a := LoadPlan.new([LoadStep.Work.new("a", 1.0, func() -> void: pass)])
	var b := LoadPlan.new([LoadStep.Work.new("b", 2.0, func() -> void: pass)])
	var both := a.plus(b)
	var names: Array = []
	for s in both.steps:
		names.append(s.name)
	assert_eq(["a", "b"], names)
	assert_eq(3.0, both.total_weight)


func test_a_step_needs_a_positive_weight() -> void:
	# Kotlin: @Test(expected = IllegalArgumentException::class). Here: an error, an invalid step and
	# a plan that can't be used.
	expect_error("positive weight")
	var free := LoadStep.Work.new("free", 0.0, func() -> void: pass)
	assert_false(free.valid)
	assert_false(LoadPlan.new([free]).is_valid())
	assert_false(LoadStep.Work.new("nan", NAN, func() -> void: pass).valid)
	assert_false(LoadStep.Wait.new("inf", INF, 10).valid)
	assert_true(LoadStep.Work.new("fine", 0.5, func() -> void: pass).valid)


func test_stage_clock_reports_gaps_and_spans() -> void:
	var t := [1000]
	var lines: Array = []
	var clock := Startup.StageClock.new(func() -> int: return t[0], func(line: String) -> void: lines.append(line), 400)
	t[0] = 1250
	clock.mark("composed")
	t[0] = 1300
	clock.begin("tap to hall")
	t[0] = 1600
	assert_true(clock.is_open("tap to hall"))
	assert_eq(300, clock.end("tap to hall"))
	assert_false(clock.is_open("tap to hall"))
	assert_eq(-1, clock.end("tap to hall"), "never begun")
	assert_eq(["composed: +850 ms (850 ms in)", "tap to hall: 300 ms"], lines)
