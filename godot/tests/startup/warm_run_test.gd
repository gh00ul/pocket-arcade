extends PaTest
## startup/WarmRunTest.kt: the GPU warm-up queue: one request at a time, progress, and giving up on
## a GPU that stops answering. Tickets are stand-ins for Warmup.Ticket (WarmRun only reads `done`);
## `Warmup.disabled` is read through WarmRun.warmup_disabled(), which is the look port's Warmup
## once it is in the project.


## Warmup.Ticket: done once the renderer has drawn the picture (or never will).
class Ticket:
	var done := false

	func _init(p_done: bool = false) -> void:
		done = p_done


var clock := [0]
var sent: Array = []
var tickets: Array = []


func before_each() -> void:
	clock = [0]
	sent = []
	tickets = []
	WarmRun.reset_warmup()


func after_each() -> void:
	# Warmup is process-wide: leave it as the next test found it.
	WarmRun.reset_warmup()


func _send(i: int) -> Ticket:
	sent.append(i)
	var t := Ticket.new()
	tickets.append(t)
	return t


func _run(count: int, stall_ms: int = 1000, on_stall: Callable = Callable()) -> WarmRun:
	return WarmRun.new(count, _send, stall_ms, func() -> int: return clock[0], on_stall, func() -> int: return 1)


func test_sends_one_job_at_a_time_and_finishes_when_the_gpu_has_answered_all() -> void:
	var r := _run(3)
	assert_eq(0.0, r.fraction)
	assert_false(r.poll())
	assert_eq([0], sent, "only the first job is out")
	assert_false(r.poll(), "still waiting for it")
	assert_eq([0], sent)
	tickets[0].done = true
	assert_false(r.poll())
	assert_eq([0, 1], sent)
	assert_near(1.0 / 3.0, r.fraction, 1e-6)
	tickets[1].done = true
	assert_false(r.poll())
	tickets[2].done = true
	assert_true(r.poll())
	assert_eq(1.0, r.fraction)
	assert_eq([0, 1, 2], sent)


func test_keeps_several_in_flight_when_loading_has_the_screen_to_itself() -> void:
	var r := WarmRun.new(5, _send, 1000, func() -> int: return clock[0], Callable(), func() -> int: return 3)
	assert_false(r.poll())
	assert_eq([0, 1, 2], sent, "three out at once")
	assert_false(r.poll())
	assert_eq([0, 1, 2], sent)
	tickets[0].done = true
	assert_false(r.poll())
	assert_eq([0, 1, 2, 3], sent, "one came back, one more goes")
	assert_near(1.0 / 5.0, r.fraction, 1e-6)
	# Answers arrive in the order sent; a later one alone doesn't count until the ones before it are in.
	tickets[3].done = true
	assert_false(r.poll())
	assert_near(1.0 / 5.0, r.fraction, 1e-6)
	tickets[1].done = true
	tickets[2].done = true
	assert_false(r.poll())
	assert_eq([0, 1, 2, 3, 4], sent)
	tickets[4].done = true
	assert_true(r.poll())
	assert_eq(1.0, r.fraction)


func test_one_at_a_time_while_the_title_is_being_watched() -> void:
	var loading := [false]
	var r := WarmRun.new(4, _send, 1000, func() -> int: return clock[0], Callable(), func() -> int: return 3 if loading[0] else 1)
	assert_false(r.poll())
	assert_eq([0], sent)
	loading[0] = true
	assert_false(r.poll())
	assert_eq([0, 1, 2], sent, "the loading screen went up: three in flight")


func test_the_stall_clock_runs_from_the_last_answer_even_with_several_out() -> void:
	var stalled := [0]
	var r := WarmRun.new(6, _send, 500, func() -> int: return clock[0], func() -> void: stalled[0] += 1, func() -> int: return 3)
	r.poll()
	clock[0] += 400
	tickets[0].done = true
	assert_false(r.poll())
	clock[0] += 400
	assert_false(r.poll(), "an answer 400 ms ago is progress")
	clock[0] += 200
	assert_true(r.poll())
	assert_eq(1, stalled[0])


func test_an_empty_queue_is_done_at_once() -> void:
	var r := _run(0)
	assert_eq(1.0, r.fraction)
	assert_true(r.poll())
	assert_true(sent.is_empty())


func test_a_gpu_that_stops_answering_is_given_up_on() -> void:
	var stalled := [0]
	var r := _run(4, 500, func() -> void: stalled[0] += 1)
	assert_false(r.poll())
	clock[0] += 400
	assert_false(r.poll())
	clock[0] += 200
	assert_true(r.poll(), "nothing back for longer than the stall time: done, whatever is left")
	assert_eq(1, stalled[0])
	assert_true(WarmRun.warmup_disabled(), "later warm-ups are answered at once")
	assert_eq([0], sent)


func test_progress_resets_the_stall_clock_each_time_a_job_lands() -> void:
	var r := _run(3, 500)
	r.poll()
	clock[0] += 400
	tickets[0].done = true
	assert_false(r.poll())
	clock[0] += 400
	assert_false(r.poll(), "the second job has only been out 400 ms")
	assert_false(WarmRun.warmup_disabled())


func test_jobs_answered_at_once_run_straight_through() -> void:
	# A GPU that is switched off answers every request with a finished ticket.
	var done: Array = []
	var answered := func(i: int) -> Ticket:
		done.append(i)
		return Ticket.new(true)
	var r := WarmRun.new(3, answered, 1000, func() -> int: return clock[0], Callable(), func() -> int: return 1)
	assert_true(r.poll())
	assert_eq([0, 1, 2], done)
