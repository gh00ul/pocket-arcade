extends PaTest
## games/racer/RacerSimulationTest.kt: plays Turbo Racer headlessly with bots of different skill and
## checks its rules and payout bands.

## Seeds successive rounds so every run replays the same games.
var _round_seed := 1


## A driver playing through the touch path like a person: one steering thumb and, if [member drifts],
## a second finger held down through corners. Every [member lag] seconds it decides where on the
## road it wants to be (with [member noise] error: the racing line or the middle, dodging the car
## ahead if [member avoids]) and slides its thumb over to steer there. Between decisions the thumb
## stays put, so the curve's push carries the car outwards.
class Driver:
	extends RefCounted
	const MID := 180.0
	## How far a thumb slides in one 1/120 s step.
	const MAX_MOVE := 10.0
	var game: RacerGame
	var rng: KRandom
	var lag: float
	var noise: float
	var racing_line: bool
	var avoids: bool
	var drifts: bool
	var _next := 0.0
	var _finger_x := MID
	var _finger_goal := MID
	var _steer_id := -1
	var _drift_id := -1
	var _ids := 0

	func _init(p_game: RacerGame, p_rng: KRandom, p_lag: float, p_noise: float, p_racing_line: bool, p_avoids: bool, p_drifts: bool) -> void:
		game = p_game
		rng = p_rng
		lag = p_lag
		noise = p_noise
		racing_line = p_racing_line
		avoids = p_avoids
		drifts = p_drifts

	## The bot's step, under the name the round runners call.
	func step(t: float, ms: int) -> void:
		drive(t, ms)

	func drive(t: float, ms: int) -> void:
		if _steer_id < 0:
			_ids += 1
			_steer_id = _ids
			_finger_x = MID
			_finger_goal = MID
			game.on_touch(TouchType.DOWN, _steer_id, _finger_x, 450.0, ms)
		if t >= _next:
			_next = t + lag
			var want := game.bot_racing_line() if racing_line else 0.0
			if avoids:
				var bx := game.bot_blocker_x(320.0)
				if not is_nan(bx):
					# Pass on the side we're already on, unless that's off the road.
					want = bx - 66.0 if game.bot_px() < bx else bx + 66.0
					if want < -135.0:
						want = bx + 66.0
					if want > 135.0:
						want = bx - 66.0
			want = clampf(want + SimHarness.gaussian(rng) * noise, -135.0, 135.0)
			_finger_goal = _finger_x + (want - game.bot_steer_target()) / RacerTuning.STEER_GAIN
		var move := clampf(_finger_goal - _finger_x, -MAX_MOVE, MAX_MOVE)
		if absf(move) > 0.01:
			_finger_x += move
			game.on_touch(TouchType.MOVE, _steer_id, _finger_x, 450.0, ms)
		# Thumb at the edge of the screen: lift it and put it back down in the middle.
		if _finger_x < 15.0 or _finger_x > 345.0:
			game.on_touch(TouchType.UP, _steer_id, _finger_x, 450.0, ms)
			_finger_goal += MID - _finger_x
			_finger_x = MID
			_ids += 1
			_steer_id = _ids
			game.on_touch(TouchType.DOWN, _steer_id, _finger_x, 450.0, ms)
		if drifts:
			if _drift_id < 0 and not game.bot_boosting() and absf(game.bot_bend_ahead(4)) > 0.9 and game.bot_speed() > 500.0:
				_ids += 1
				_drift_id = _ids
				game.on_touch(TouchType.DOWN, _drift_id, 300.0, 540.0, ms)
			elif _drift_id >= 0 and (absf(game.bot_bend()) < 0.35 or game.bot_charge() >= RacerTuning.BOOST_CHARGE_2):
				game.on_touch(TouchType.UP, _drift_id, 300.0, 540.0, ms)
				_drift_id = -1


class RaceStats:
	extends RefCounted
	var stats: SimHarness.Stats
	var places: Array[int] = []
	var finishes := 0
	var passes: Array[int] = []
	var contacts: Array[int] = []

	func _init(name: String) -> void:
		stats = SimHarness.Stats.new(name)

	func avg_place() -> float:
		return SimHarness.average(places)

	func _to_string() -> String:
		return "%s  place %.2f  finished %d/%d  passes %.1f  bumps %.1f" % [stats, avg_place(), finishes, places.size(), SimHarness.average(passes), SimHarness.average(contacts)]


static func good_driver(game: RacerGame, rng: KRandom) -> Driver:
	return Driver.new(game, rng, 0.1, 6.0, true, true, true)


static func casual_driver(game: RacerGame, rng: KRandom) -> Driver:
	return Driver.new(game, rng, 0.6, 30.0, false, false, false)


func _race(rounds: int, name: String, seed_value: int, make: Callable) -> RaceStats:
	var out := RaceStats.new(name)
	var rng := KRandom.new(seed_value)
	var game := RacerGame.new()
	for r in rounds:
		var driver: Driver = make.call(game, rng)
		SimHarness.play_round(self, game, _round_seed, out.stats, driver.drive)
		_round_seed += 1
		out.places.append(game.bot_place())
		if game.bot_race_done():
			out.finishes += 1
		out.passes.append(game.bot_passes())
		out.contacts.append(game.bot_contacts())
	return out


func test_racer_pays_out_and_rewards_skill() -> void:
	var rounds := 12
	var good := _race(rounds, "racer good", 15, good_driver)
	var casual := _race(rounds, "racer casual", 16, casual_driver)
	print(good)
	print(casual)
	SimHarness.assert_payout_bands(self, good.stats, casual.stats)
	assert_true(good.avg_place() < casual.avg_place(), "skill should place better: %s vs %s" % [good, casual])
	assert_true(good.finishes * 4 >= rounds * 3, "a good driver should usually finish: %s" % good)
	assert_true(casual.finishes * 2 <= rounds, "a casual driver should usually run out of time: %s" % casual)


## Plays [param rounds] rounds with a fresh bot each, from fixed seeds. [param bot] makes the bot
## from the game: an object with a step(t, ms) method, held here for the round (a Callable to a
## method doesn't keep its object alive).
func _play_rounds(rounds: int, name: String, seed_value: int, bot: Callable) -> SimHarness.Stats:
	var out := SimHarness.Stats.new(name)
	var game := RacerGame.new()
	for r in rounds:
		var b: Object = bot.call(game)
		SimHarness.play_round(self, game, seed_value + r, out, Callable(b, "step"))
	print(out)
	return out


## A player who never touches the screen.
class Idle:
	extends RefCounted

	func step(_t: float, _ms: int) -> void:
		pass


## A good driver's steering thumb with a second finger put down at the start and never lifted.
class HoldDrift:
	extends RefCounted
	var game: RacerGame
	var drives: bool
	var driver: Driver
	var started := false

	func _init(p_game: RacerGame, p_drives: bool) -> void:
		game = p_game
		drives = p_drives
		driver = Driver.new(game, KRandom.new(3), 0.1, 6.0, true, true, false)

	func step(t: float, ms: int) -> void:
		if not started:
			started = true
			if not drives:
				game.on_touch(TouchType.DOWN, 1, 180.0, 400.0, ms)
			game.on_touch(TouchType.DOWN, 999, 300.0, 500.0, ms)
		if drives:
			driver.drive(t, ms)


func test_zero_effort_and_endless_drift_pay_less_than_driving() -> void:
	var rounds := 8
	var idle := _play_rounds(rounds, "racer never touch", 100, func(_g: RacerGame) -> Object: return Idle.new())
	var resting := _play_rounds(rounds, "racer rest + hold drift", 100, func(g: RacerGame) -> Object: return HoldDrift.new(g, false))
	var endless := _play_rounds(rounds, "racer steer + hold drift", 100, func(g: RacerGame) -> Object: return HoldDrift.new(g, true))
	var no_drift := _play_rounds(rounds, "racer steer no drift", 100, func(g: RacerGame) -> Object: return Driver.new(g, KRandom.new(3), 0.1, 6.0, true, true, false))
	var full := _play_rounds(rounds, "racer full kit", 100, func(g: RacerGame) -> Object: return Driver.new(g, KRandom.new(3), 0.1, 6.0, true, true, true))
	var casual_rng := KRandom.new(16)
	var casual := _play_rounds(rounds, "racer casual", 100, func(g: RacerGame) -> Object: return casual_driver(g, casual_rng))
	assert_true(idle.avg_tickets() <= 3.0, "never touching the screen pays too much: %s" % idle)
	assert_true(resting.avg_tickets() < casual.avg_tickets(), "resting two fingers should pay less than casual driving: %s vs %s" % [resting, casual])
	assert_true(endless.avg_tickets() < full.avg_tickets(), "holding the drift all race should pay less than the full kit: %s vs %s" % [endless, full])
	assert_true(endless.avg_tickets() <= no_drift.avg_tickets() + 1.0, "holding the drift all race should pay no more than not drifting: %s vs %s" % [endless, no_drift])


func test_the_grid_starts_without_contact() -> void:
	for seed_value in range(1, 9):
		var game := RacerGame.new()
		SimHarness.RoundDriver.new(game, seed_value).play(0.5)
		assert_eq(0, game.bot_contacts(), "contacts in the first 0.5 s with no input (seed %d)" % seed_value)


func test_crossing_the_line_ends_the_round_early_and_ranks_by_finish_order() -> void:
	var game := RacerGame.new()
	var d := SimHarness.RoundDriver.new(game, 3)
	d.play(0.5)
	var line := game.bot_race_length()
	# Two rivals just short of the flag, the player a little further back.
	game.bot_place_car(1, line - 150.0, -60.0, 1000.0)
	game.bot_place_car(2, line - 100.0, 60.0, 1000.0)
	game.bot_place_car(0, line - 400.0, 0.0, 1000.0)
	assert_eq(3, game.bot_place())
	assert_true(d.play(3.0))
	assert_true(d.time_left > 0.0, "ended before the clock")
	assert_true(game.bot_race_done())
	assert_eq(1, game.bot_finish_order(2))
	assert_eq(2, game.bot_finish_order(1))
	assert_eq(3, game.bot_finish_order(0))
	assert_eq(3, game.bot_place())
	# Nothing scores after the flag, through the host's ending either.
	var score := game.score
	assert_true(score >= RacerTuning.PLACE_POINTS[2])
	d.end()
	assert_eq(score, game.score)
	assert_eq(3, game.bot_place())


func test_time_up_ranks_by_progress_and_stops_scoring() -> void:
	var game := RacerGame.new()
	SimHarness.RoundDriver.new(game, 9)
	# No input at all: the curves push the car off the road and it never makes the flag.
	var left := game.round_seconds
	while left - GameLoop.FIXED_DT > 1e-4:
		left -= GameLoop.FIXED_DT
		game.update(GameLoop.FIXED_DT, left)
	assert_false(game.bot_race_done())
	var expected := 1
	for i in range(1, RacerTuning.CARS):
		if game.bot_finish_order(i) > 0 or game.bot_car_d(i) > game.bot_progress():
			expected += 1
	var before := game.score
	game.update(GameLoop.FIXED_DT, 0.0)
	assert_eq(expected, game.bot_place())
	assert_eq(int(RacerTuning.PLACE_POINTS[expected - 1] * RacerTuning.DNF_SHARE), game.score - before)
	# The car rolls to a stop, rivals keep going, but the score and the place are final.
	var final_score := game.score
	for k in int(5.0 / GameLoop.FIXED_DT):
		game.update(GameLoop.FIXED_DT, 0.0)
	assert_true(game.finished())
	assert_eq(final_score, game.score)
	assert_eq(expected, game.bot_place())


## Drives with the good driver and checks, every step, that the place shown is the cars ahead plus one.
class PlaceWatcher:
	extends RefCounted
	var game: RacerGame
	var driver: Driver
	var test: PaTest
	var best := 0
	var changes := 0
	var last := 0
	var failed := false

	func _init(p_game: RacerGame, p_driver: Driver, p_test: PaTest) -> void:
		game = p_game
		driver = p_driver
		test = p_test
		best = game.bot_place()
		last = game.bot_place()

	func step(t: float, ms: int) -> void:
		driver.drive(t, ms)
		if not game.bot_race_done():
			var ahead := 0
			for i in range(1, RacerTuning.CARS):
				if game.bot_finish_order(i) > 0 or game.bot_car_d(i) > game.bot_progress():
					ahead += 1
			if ahead + 1 != game.bot_place() and not failed:
				failed = true
				test.fail("place at %.2f s: expected %d but was %d" % [t, ahead + 1, game.bot_place()])
			if game.bot_place() != last:
				changes += 1
			last = game.bot_place()
			best = mini(best, game.bot_place())


func test_positions_update_when_overtaking() -> void:
	var game := RacerGame.new()
	var driver := good_driver(game, KRandom.new(4))
	var start := SimHarness.RoundDriver.new(game, 44)
	assert_eq(RacerTuning.PLAYER_SLOT + 1, game.bot_place())
	var watcher := PlaceWatcher.new(game, driver, self)
	start.play(game.round_seconds, watcher.step)
	assert_true(watcher.best <= 3, "the good driver should move up the field (best %d)" % watcher.best)
	assert_true(watcher.changes >= 3)
	assert_true(game.bot_passes() > 0, "clean passes should score")


func test_cancel_input_releases_the_pointer_and_a_new_pointer_steers() -> void:
	var game := RacerGame.new()
	game.solo_for_tests = true
	var d := SimHarness.RoundDriver.new(game, 21)
	d.play(0.5)
	var base := game.bot_steer_target()
	game.on_touch(TouchType.DOWN, 1, 180.0, 400.0, d.ms())
	game.on_touch(TouchType.MOVE, 1, 150.0, 400.0, d.ms() + 16)
	assert_near(base - 48.0, game.bot_steer_target(), 0.01)
	game.on_touch(TouchType.DOWN, 2, 300.0, 500.0, d.ms() + 20)
	assert_true(game.bot_drifting(), "a second finger drifts")
	d.pause()
	assert_false(game.bot_drifting(), "pausing lets go of the drift")
	d.play(0.3)
	# The lost fingers' late events change nothing (and the drift gives no boost).
	game.on_touch(TouchType.MOVE, 1, 60.0, 400.0, d.ms())
	game.on_touch(TouchType.UP, 1, 60.0, 400.0, d.ms())
	game.on_touch(TouchType.UP, 2, 300.0, 500.0, d.ms())
	assert_near(base - 48.0, game.bot_steer_target(), 0.01)
	assert_false(game.bot_boosting())
	# A new finger steers on from where the car was heading.
	game.on_touch(TouchType.DOWN, 3, 200.0, 400.0, d.ms())
	game.on_touch(TouchType.MOVE, 3, 250.0, 400.0, d.ms() + 16)
	assert_near(base + 32.0, game.bot_steer_target(), 0.01)
	assert_false(game.bot_drifting())


## Records the car's offset every step, with a finger held down that is either still or trembling.
class Trembler:
	extends RefCounted
	var game: RacerGame
	var jitter: bool
	var noise := KRandom.new(8)
	var next_move := 0.008
	var k := 0
	var out := PackedFloat32Array()

	func _init(p_game: RacerGame, p_jitter: bool) -> void:
		game = p_game
		jitter = p_jitter
		out.resize(int(6.0 / GameLoop.FIXED_DT))

	func step(t: float, ms: int) -> void:
		# A finger that's "still" on glass still reports MOVEs every 8 ms, a pixel either way.
		while jitter and next_move <= t:
			game.on_touch(TouchType.MOVE, 1, 180.0 + (noise.next_float() * 2.0 - 1.0), 400.0, ms)
			next_move += 0.008
		if k < out.size():
			out[k] = game.bot_px()
			k += 1


## Car offsets through the first bend with one finger held down, still or trembling.
func _through_the_first_bend(jitter: bool) -> PackedFloat32Array:
	var game := RacerGame.new()
	game.solo_for_tests = true
	var d := SimHarness.RoundDriver.new(game, 5)
	var bot := Trembler.new(game, jitter)
	game.on_touch(TouchType.DOWN, 1, 180.0, 400.0, 0)
	d.play(6.0, bot.step)
	return bot.out


func test_curve_push_survives_a_jittering_finger() -> void:
	var still := _through_the_first_bend(false)
	var shaky := _through_the_first_bend(true)
	var start := still[0]
	var pushed := 0.0
	var worst := 0.0
	for k in still.size():
		pushed = maxf(pushed, absf(still[k] - start))
		worst = maxf(worst, absf(still[k] - shaky[k]))
	assert_true(pushed > 60.0, "the bend should push the car wide (moved %f)" % pushed)
	assert_true(worst < 4.0, "finger jitter changed the car's line by %f" % worst)


## Up to three fingers landing, sliding wildly and lifting at random, and the host now and then pausing.
class Fingers:
	extends RefCounted
	var game: RacerGame
	var rng: KRandom
	var test: PaTest
	var down: Array[int] = [-1, -1, -1]
	var xs: Array[float] = [0.0, 0.0, 0.0]
	var ids := 0
	var failed := false

	func _init(p_game: RacerGame, p_rng: KRandom, p_test: PaTest) -> void:
		game = p_game
		rng = p_rng
		test = p_test

	func step(_t: float, ms: int) -> void:
		var f := rng.next_int_until(3)
		if down[f] < 0 and rng.next_float() < 0.05:
			ids += 1
			down[f] = ids
			xs[f] = rng.next_float() * 360.0
			game.on_touch(TouchType.DOWN, down[f], xs[f], 400.0, ms)
		elif down[f] >= 0 and rng.next_float() < 0.03:
			game.on_touch(TouchType.UP, down[f], xs[f], 400.0, ms)
			down[f] = -1
		elif down[f] >= 0:
			xs[f] += (rng.next_float() - 0.5) * 60.0
			game.on_touch(TouchType.MOVE, down[f], xs[f], 400.0, ms)
		if rng.next_float() < 0.002:
			# The host pausing: every finger is forgotten.
			game.cancel_input()
			down.fill(-1)
		check()

	func check() -> void:
		if failed:
			return
		for i in RacerTuning.CARS:
			if not (is_finite(game.bot_car_d(i)) and is_finite(game.bot_car_x(i)) and is_finite(game.bot_car_v(i))):
				failed = true
				test.fail("car %d went NaN or infinite" % i)
				return
		var p := game.bot_place()
		if p < 1 or p > RacerTuning.CARS:
			failed = true
			test.fail("place out of range: %d" % p)


func test_no_nan_over_a_long_run() -> void:
	var game := RacerGame.new()
	var rng := KRandom.new(77)
	var d := SimHarness.RoundDriver.new(game, 77)
	var fingers := Fingers.new(game, rng, self)
	d.play(game.round_seconds + 20.0, fingers.step)
	assert_true(game.finished())
	for k in int(10.0 / GameLoop.FIXED_DT):
		game.update(GameLoop.FIXED_DT, 0.0)
		fingers.check()
