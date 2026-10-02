extends PaTest
## Godot: the game host's round flow (ui/GameHostScreen.kt's HostState and loop): the countdown,
## the round clock on game time, Back and the exit button in every phase (2.0.0 bug 1 inside a
## machine), the ending's minimum settle, the payout saved once, the staged reveal and printing,
## and PLAY AGAIN spending exactly one token.


class TestGame:
	extends BaseMiniGame
	var cancels := 0
	var touches: Array = []
	var finish_after := -1.0
	var payout := 0

	func _init(seconds: float = 3.0) -> void:
		id = "test"
		title = "TEST"
		marquee = "TEST"
		round_seconds = seconds
		look = MiniGame.CabinetLook.new(Pal.PURPLE, Pal.YELLOW, Pal.PINK)

	func step(_dt: float) -> void:
		if finish_after >= 0.0 and time >= finish_after:
			ended_early = true

	func on_touch(type: int, pid: int, x: float, y: float, t: int) -> void:
		touches.append([type, pid, x, y, t])
		if type == TouchType.DOWN:
			add_score(10, x, y, Pal.WHITE)

	func cancel_input() -> void:
		cancels += 1

	func tickets_for(s: int) -> int:
		return payout if payout > 0 else s / 10


var fixture: RepoFixture
var audio: AudioSynth
var device: HapticsRecorder


class HapticsRecorder:
	extends Haptics.Device
	var calls := 0

	func _init() -> void:
		amplitude_control = true
		primitives = true
		thud = true
		low_tick = true

	func composition(_p: PackedInt32Array, _s: PackedFloat32Array, _d: PackedInt32Array) -> void:
		calls += 1


func before_each() -> void:
	fixture = RepoFixture.new()
	audio = AudioSynth.new()
	host.add_child(audio)
	device = HapticsRecorder.new()


func after_each() -> void:
	fixture.cleanup()


func round_for(game: MiniGame, tokens: int = 5) -> GameRound:
	var repo := fixture.repo(tokens, 0)
	var h := Haptics.new(device)
	h.clock = func() -> int: return 0
	return GameRound.new(game, audio, h, repo)


## Runs [param seconds] of the host's fixed 120 Hz steps.
func run(r: GameRound, seconds: float) -> void:
	for i in roundi(seconds * 120.0):
		r.frame(GameLoop.FIXED_DT)


func test_the_intro_waits_and_start_counts_down_three_beats_then_plays() -> void:
	var r := round_for(TestGame.new())
	assert_eq(GameRound.Phase.INTRO, r.phase)
	run(r, 1.0)
	assert_eq(GameRound.Phase.INTRO, r.phase, "the intro card waits for START")
	assert_true(audio.music.quiet, "the theme sits back under the intro card")
	assert_eq(MusicScene.game("test"), audio.music.scene)
	r.start_round()
	assert_eq(GameRound.Phase.COUNTDOWN, r.phase)
	assert_false(audio.music.quiet)
	run(r, 1.9)
	assert_eq(GameRound.Phase.COUNTDOWN, r.phase)
	run(r, 0.1)
	assert_eq(GameRound.Phase.PLAYING, r.phase, "3 x 0.65 s of countdown")
	assert_eq(str([Stinger.COUNTDOWN, Stinger.COUNTDOWN, Stinger.COUNTDOWN, Stinger.GO]), str(audio.music.stingers))


func test_the_round_clock_is_game_time_and_freezes_with_a_hit_stop() -> void:
	var g := TestGame.new(10.0)
	var r := round_for(g)
	r.start_round()
	run(r, 1.95)
	assert_eq(10.0, r.time_left)
	run(r, 1.0)
	assert_near(9.0, r.time_left, 0.05)
	# A hit-stop freezes the game and its clock; real-time frames keep coming.
	g.fx.hit_stop(0.1)
	var before := r.time_left
	r.frame(GameLoop.FIXED_DT)
	r.frame(GameLoop.FIXED_DT)
	assert_near(before, r.time_left, 1e-9, "frozen")
	run(r, 0.5)
	assert_true(r.time_left < before)


func test_back_pauses_a_round_resumes_from_pause_and_leaves_from_the_intro_with_a_refund() -> void:
	var exits: Array = []
	var g := TestGame.new()
	var r := round_for(g)
	r.exit_requested.connect(func(refund: bool) -> void: exits.append(refund))
	r.on_exit_pressed()
	assert_eq([true], exits, "Back on the intro card leaves and refunds the token")
	exits.clear()
	r.start_round()
	run(r, 0.5)
	r.on_exit_pressed()
	assert_eq(GameRound.Phase.PAUSED, r.phase, "Back during the countdown pauses")
	assert_eq(GameRound.Phase.COUNTDOWN, r.resume_phase)
	assert_eq(1, g.cancels, "pausing lets go of every pointer")
	assert_true(audio.music.quiet, "the music sits back under the pause card")
	r.on_exit_pressed()
	assert_eq(GameRound.Phase.COUNTDOWN, r.phase, "Back on the pause card resumes")
	run(r, 2.0)
	assert_eq(GameRound.Phase.PLAYING, r.phase)
	r.on_exit_pressed()
	assert_eq(GameRound.Phase.PAUSED, r.phase, "Back while playing pauses")
	var t := r.time_left
	run(r, 1.0)
	assert_eq(t, r.time_left, "nothing runs while paused")
	r.resume()
	assert_eq(GameRound.Phase.PLAYING, r.phase)
	r.quit_round()
	assert_eq([false], exits, "quitting forfeits the token")


func test_touches_reach_the_game_only_while_playing_in_field_units() -> void:
	var g := TestGame.new()
	var r := round_for(g)
	r.gx = 20.0
	r.gy = 100.0
	r.gs = 2.0
	r.touch(TouchType.DOWN, 1, 120.0, 300.0, 5)
	assert_eq(0, g.touches.size(), "not on the intro card")
	r.start_round()
	run(r, 2.0)
	r.touch(TouchType.DOWN, 3, 120.0, 300.0, 1234)
	assert_eq([[TouchType.DOWN, 3, 50.0, 100.0, 1234]], g.touches)


func test_the_ending_settles_for_at_least_144_steps_then_shows_the_results() -> void:
	var g := TestGame.new(30.0)
	g.finish_after = 1.0
	var r := round_for(g)
	r.start_round()
	run(r, 1.95 + 1.1)
	assert_eq(GameRound.Phase.ENDING, r.phase, "the game ended itself")
	assert_true(r.ended_early)
	assert_eq(1, g.cancels, "the round's end lets go of the pointers")
	# The ending runs 1.2 s and at least ENDING_STEPS game steps before the results.
	run(r, 1.0)
	assert_eq(GameRound.Phase.ENDING, r.phase)
	run(r, 0.5)
	assert_eq(GameRound.Phase.RESULTS, r.phase)
	assert_true(r.end_steps >= GameRound.ENDING_STEPS)


func test_the_payout_is_saved_once_with_the_high_score_and_the_plays() -> void:
	var g := TestGame.new(2.0)
	g.payout = 7
	var r := round_for(g)
	r.start_round()
	run(r, 2.0)
	r.touch(TouchType.DOWN, 1, 100.0, 100.0, 0)
	r.touch(TouchType.DOWN, 1, 100.0, 100.0, 50)
	run(r, 2.0 + 1.5)
	assert_eq(GameRound.Phase.RESULTS, r.phase)
	assert_eq(20, r.result_score)
	assert_eq(7, r.result_tickets)
	assert_true(r.new_high, "a first score beats an empty best")
	var s := r.repo.state()
	assert_eq(7, s.tickets)
	assert_eq(20, s.high_score("test"))
	assert_eq(1, s.stat("plays:test"))
	assert_eq(7, s.stat("tickets:earned"))
	assert_eq(0, r.tickets_before)
	# Nothing more is paid however long the results stay up.
	run(r, 6.0)
	assert_eq(7, r.repo.state().tickets)


func test_the_reveal_prints_every_ticket_and_they_all_land_on_the_counter() -> void:
	var g := TestGame.new(2.0)
	g.payout = 40
	var r := round_for(g)
	r.start_round()
	run(r, 1.95 + 2.0 + 1.5)
	assert_eq(GameRound.Phase.RESULTS, r.phase)
	assert_eq(ResultsPlan.flight_chunk(40), r.chunk)
	run(r, 2.0)
	assert_eq(4, r.reveal_stage)
	run(r, 6.0)
	assert_true(r.printing_done)
	assert_eq(40, r.printed)
	assert_eq(40, r.landed, "every ticket flew into the counter")
	assert_true(r.flights.size() <= ResultsPlan.MAX_FLIGHTS)


func test_a_tap_skips_the_reveal_to_the_printing_and_a_second_finishes_it() -> void:
	var g := TestGame.new(2.0)
	g.payout = 60
	var r := round_for(g)
	r.start_round()
	run(r, 1.95 + 2.0 + 1.5)
	r.touch(TouchType.DOWN, 1, 0.0, 0.0, 0)
	assert_false(r.skipped, "not in the first moment (a last tap of the round must not skip)")
	run(r, 0.7)
	r.touch(TouchType.DOWN, 1, 0.0, 0.0, 0)
	assert_true(r.skipped)
	assert_true(r.phase_t >= ResultsPlan.PRINT_AT)
	run(r, 0.1)
	r.touch(TouchType.DOWN, 1, 0.0, 0.0, 0)
	run(r, 0.1)
	assert_eq(60, r.printed)
	assert_true(r.printing_done)


func test_play_again_spends_exactly_one_token_and_restarts_at_the_countdown() -> void:
	var g := TestGame.new(2.0)
	var r := round_for(g, 2)
	r.start_round()
	run(r, 1.95 + 2.0 + 1.5)
	assert_eq(GameRound.Phase.RESULTS, r.phase)
	assert_true(r.play_again())
	assert_false(r.play_again(), "a double tap spends no second token")
	assert_eq(1, r.repo.state().tokens)
	assert_eq(GameRound.Phase.COUNTDOWN, r.phase)
	assert_eq(2.0, r.time_left)
	# Out of tokens: refused, still on the results.
	run(r, 1.95 + 2.0 + 1.5)
	assert_true(r.play_again())
	run(r, 1.95 + 2.0 + 1.5)
	assert_eq(0, r.repo.state().tokens)
	assert_false(r.play_again())
	assert_eq(GameRound.Phase.RESULTS, r.phase)


func test_leaving_the_results_waits_while_play_again_spends_its_token() -> void:
	var exits: Array = []
	var g := TestGame.new(2.0)
	var r := round_for(g)
	r.exit_requested.connect(func(refund: bool) -> void: exits.append(refund))
	r.start_round()
	run(r, 1.95 + 2.0 + 1.5)
	r.play_again_gate.try_claim()
	r.on_exit_pressed()
	assert_eq([], exits, "a token being spent would be lost")
	r.play_again_gate.release()
	r.on_exit_pressed()
	assert_eq([false], exits)


func test_the_best_on_the_top_bar_waits_for_the_reveal() -> void:
	var g := TestGame.new(2.0)
	var r := round_for(g)
	r.start_round()
	run(r, 2.0)
	r.touch(TouchType.DOWN, 1, 100.0, 100.0, 0)
	run(r, 2.0 + 1.4)
	assert_eq(0, r.live_best())
	run(r, ResultsPlan.BEST_AT + 0.1)
	assert_eq(10, r.live_best())
