extends PaTest
## Regression (2.0.0 bug 9): a GUI test was flaky because what it checked depended on how many
## frames happened to run. Here the simulation runs on fixed 120 Hz steps whatever the display
## does, so a round played at 60, 90, 120 or 144 Hz, or with jittery frames, comes out the same,
## and tests drive it by steps, never by the wall clock.

const CADENCES := {"60 Hz": 1.0 / 60.0, "90 Hz": 1.0 / 90.0, "120 Hz": 1.0 / 120.0, "144 Hz": 1.0 / 144.0, "jitter": -1.0}


class TestGame:
	extends BaseMiniGame

	func _init() -> void:
		id = "timing"
		title = "TIMING"
		marquee = "TIMING"
		round_seconds = 3.0
		look = MiniGame.CabinetLook.new(Pal.PURPLE, Pal.YELLOW, Pal.PINK)

	func step(_dt: float) -> void:
		pass

	func on_touch(type: int, _pid: int, x: float, y: float, _t: int) -> void:
		if type == TouchType.DOWN:
			add_score(70, x, y, Pal.WHITE)

	func cancel_input() -> void:
		pass

	func tickets_for(s: int) -> int:
		return s / 10


var fx: RepoFixture


func before_each() -> void:
	fx = RepoFixture.new()


func after_each() -> void:
	fx.cleanup()


## A frame length for [param cadence] (seconds, or -1 for frames between 4 and 40 ms from a
## seeded generator).
func _frame(cadence: float, rng: KRandom) -> float:
	return cadence if cadence > 0.0 else 0.004 + rng.next_float() * 0.036


func test_the_loop_turns_any_frame_rate_into_the_same_steps() -> void:
	for name: String in CADENCES:
		var loop := GameLoop.new()
		var rng := KRandom.new(7)
		var t := 0.0
		var steps := 0
		while t < 10.0 - 1e-9:
			var dt := minf(_frame(CADENCES[name], rng), 10.0 - t)
			t += dt
			steps += loop.steps_for(dt)
		assert_true(absi(steps - 1200) <= 1, "%s: %d steps for 10 s" % [name, steps])


func test_a_round_plays_out_the_same_at_any_frame_rate() -> void:
	var outcomes := {}
	for name: String in CADENCES:
		var services := ArcadeServices.open(fx.dir.path_join(name.replace(" ", "_")), null)
		var screen := GameHostScreen.create(TestGame.new(), services)
		host.add_child(screen)
		screen.set_process(false)
		var r := screen.round
		r.start_round()
		var rng := KRandom.new(11)
		var t := 0.0
		var taps := [2.3, 2.9, 3.6]
		var phases: Array = [GameRound.Phase.keys()[r.phase]]
		while t < 12.0:
			var dt := _frame(CADENCES[name], rng)
			t += dt
			screen._process(dt)
			if not taps.is_empty() and t >= taps[0]:
				taps.pop_front()
				r.touch(TouchType.DOWN, 1, r.gx + 100.0 * r.gs, r.gy + 300.0 * r.gs, roundi(t * 1000.0))
				r.touch(TouchType.UP, 1, r.gx + 100.0 * r.gs, r.gy + 300.0 * r.gs, roundi(t * 1000.0) + 30)
			var p: String = GameRound.Phase.keys()[r.phase]
			if p != phases.back():
				phases.append(p)
		var s := services.repo.state()
		outcomes[name] = [phases, screen.game.score, r.printed, s.tickets, s.high_score("timing")]
		screen.queue_free()
	var first: Array = outcomes["60 Hz"]
	assert_eq(["COUNTDOWN", "PLAYING", "ENDING", "RESULTS"], first[0])
	assert_eq(210, first[1], "three taps while playing")
	for name: String in outcomes:
		assert_eq(first, outcomes[name], "%s plays out like 60 Hz" % name)


## Test files allowed to read the clock, and why: none of them times anything.
const CLOCK_OK := {
	"run_tests.gd": "the runner reports how long the run took",
	"data/repo_fixture.gd": "a unique scratch folder name",
	"bugs/frame_timing_test.gd": "this check",
	"share/photo_store_test.gd": "a unique scratch folder name",
	"share/photo_strip_test.gd": "checks the date label against the phone's own date and zone (midnight handled)",
}


func test_no_test_reads_the_wall_clock() -> void:
	# Tests drive time by steps; profiling (tests/perf) is the one place that times things.
	var clock := RegEx.create_from_string("Time[.]get_ticks_[mu]sec|_from_system[(]|OS[.]delay_|create_timer[(]")
	var found: Array = []
	for path: String in _scripts("res://tests"):
		if CLOCK_OK.has(path.trim_prefix("res://tests/")) or path.contains("/perf/"):
			continue
		if clock.search(FileAccess.get_file_as_string(path)) != null:
			found.append(path)
	assert_eq([], found)


func _scripts(dir: String) -> Array:
	var out: Array = []
	for f in DirAccess.get_files_at(dir):
		if f.ends_with(".gd"):
			out.append(dir.path_join(f))
	for d in DirAccess.get_directories_at(dir):
		out.append_array(_scripts(dir.path_join(d)))
	return out
