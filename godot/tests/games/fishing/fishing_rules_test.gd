extends PaTest
## games/fishing/FishingRulesTest.kt: the rules of Gone Fishing: the crank, the strike, the line's
## tension, scoring and the round's end.

const K := preload("res://scripts/games/fishing/fishing_tuning.gd")
const Bots := preload("res://tests/games/fishing/fishing_bots.gd")
const CastPhase := FishingGame.CastPhase
const Pull := FishingGame.Pull
const FishMode := FishingGame.FishMode
const DT := GameLoop.FIXED_DT


# ---------------------------------------------------------------- the crank

## Feeds [param n] samples [param step] radians apart round (100, 100) at radius [param r], 16 ms apart.
func _circle(c: Crank, pid: int, from: float, step: float, n: int, ms0: int, r: float = 50.0) -> int:
	var a := from
	var ms := ms0
	for i in n:
		a += step
		ms += 16
		c.move(pid, 100.0 + cos(a) * r, 100.0 + sin(a) * r, ms)
	return ms


func test_clockwise_cranking_is_positive_and_counter_clockwise_negative() -> void:
	var c := Crank.new(100.0, 100.0, 16.0)
	assert_true(c.grab(1, 150.0, 100.0, 0))
	# 0.16 rad every 16 ms is 10 rad/s, clockwise on screen (y down); it crosses ±π on the way.
	var ms := _circle(c, 1, 0.0, 0.16, 30, 0)
	assert_near(10.0, c.rate, 0.2)
	assert_near(4.8, c.take(), 1e-3)
	assert_eq(0.0, c.take(), "taken turning is gone")
	ms = _circle(c, 1, 4.8, -0.16, 30, ms)
	assert_near(-10.0, c.rate, 0.2)
	assert_near(-4.8, c.take(), 1e-3)
	# Half the speed reads as half the rate.
	_circle(c, 1, 0.0, 0.08, 30, ms)
	assert_near(5.0, c.rate, 0.2)


func test_crank_ignores_the_hub_and_jitter() -> void:
	var c := Crank.new(100.0, 100.0, 16.0)
	c.grab(1, 150.0, 100.0, 0)
	# A sample right on the hub, then one a quarter turn round: no jump is counted.
	c.move(1, 101.0, 101.0, 16)
	c.move(1, 100.0, 150.0, 32)
	assert_near(0.0, c.take(), 1e-4)
	# Only real movement after it counts.
	c.move(1, 100.0 + cos(1.7708) * 50.0, 100.0 + sin(1.7708) * 50.0, 48)
	assert_near(0.2, c.take(), 1e-3)
	# A finger trembling on the spot goes nowhere.
	var ms := 64
	for k in 40:
		var a := 1.7708 + (0.01 if k % 2 == 0 else -0.01)
		c.move(1, 100.0 + cos(a) * 50.0, 100.0 + sin(a) * 50.0, ms)
		ms += 16
	assert_near(0.0, c.take(), 0.011)
	assert_true(absf(c.rate) < 1.0, "jitter barely spins the reel: %s" % c.rate)


func test_crank_follows_only_its_own_pointer() -> void:
	var c := Crank.new(100.0, 100.0, 16.0)
	c.grab(1, 150.0, 100.0, 0)
	assert_false(c.grab(2, 100.0, 150.0, 0), "a second finger can't take the held crank")
	_circle(c, 2, 0.0, 0.2, 10, 0)
	assert_eq(0.0, c.take(), "another finger's samples turn nothing")
	c.release(2)
	assert_true(c.held(), "releasing another finger lets nothing go")
	c.release(1)
	assert_false(c.held())
	_circle(c, 1, 0.0, 0.2, 10, 200)
	assert_eq(0.0, c.take(), "a lifted finger's samples turn nothing")
	assert_true(c.grab(2, 150.0, 100.0, 400))
	_circle(c, 2, 0.0, 0.2, 10, 400)
	assert_near(2.0, c.take(), 1e-3)
	# Stop moving: the reel spins down.
	for i in 60:
		c.age(DT)
	assert_near(0.0, c.rate, 0.05)


# ---------------------------------------------------------------- helpers

## A round with fish [param slot] placed as given and already hooked: [game, driver].
func _hooked(seed_value: int, species: int, weight: float, x: float, z: float, slot: int = 0) -> Array:
	var game := FishingGame.new()
	var d := SimHarness.RoundDriver.new(game, seed_value)
	d.play(0.2)
	game.bot_place_fish(slot, species, weight, x, z)
	game.bot_hook_now(slot)
	assert_eq(CastPhase.FIGHT, game.bot_phase())
	return [game, d]


## Winds like a careful angler: hard while the fish rests, gently while it thrashes and runs.
static func _careful(game: FishingGame) -> float:
	if game.bot_tension() > 0.9:
		return 0.0
	if game.bot_pull() != Pull.REST:
		return 1.5
	if game.bot_tension() > 0.75:
		return 5.0
	return 10.0


## Holds a cast on the pond aimed at world (x, z) and lets go at the right power.
func _cast_at(game: FishingGame, d: SimHarness.RoundDriver, pid: int, x: float, z: float) -> void:
	var aim := game.bot_aim_for(x, z)
	game.on_touch(TouchType.DOWN, pid, aim.x, 300.0, d.ms())
	var guard := 0
	while absf(game.bot_power() - aim.y) >= 0.02:
		var g := guard
		guard += 1
		if g >= 600:
			break
		d.play(DT * 1.5)
	game.on_touch(TouchType.UP, pid, aim.x, 300.0, d.ms())
	assert_eq(CastPhase.FLIGHT, game.bot_phase())


func _play_until(d: SimHarness.RoundDriver, seconds: float, done: Callable, bot: Callable = Callable()) -> bool:
	var steps := int(seconds / DT)
	for i in steps:
		if done.call():
			return true
		d.play(DT * 1.5, bot)
	return done.call()


# ---------------------------------------------------------------- the strike

func test_a_missed_strike_loses_the_fish() -> void:
	var game := FishingGame.new()
	var d := SimHarness.RoundDriver.new(game, 14)
	d.play(0.2)
	game.bot_place_fish(0, 1, 2.0, 180.0, 280.0)
	_cast_at(game, d, 1, 180.0, 310.0)
	assert_true(_play_until(d, 30.0, game.bot_biting), "something should bite")
	var biter := game.bot_suitor()
	# Never touch the reel: the window runs out.
	d.play(K.STRIKE_WINDOW + 0.05)
	assert_eq(1, game.bot_missed())
	assert_false(game.bot_biting())
	assert_eq(-1, game.bot_hooked())
	assert_eq(CastPhase.WAIT, game.bot_phase(), "the lure stays in the water")
	assert_eq(FishMode.SWIM, game.bot_fish_mode(biter), "the fish swims off")
	assert_eq(0, game.score)


func test_cranking_in_the_window_sets_the_hook() -> void:
	var game := FishingGame.new()
	var d := SimHarness.RoundDriver.new(game, 15)
	d.play(0.2)
	game.bot_place_fish(0, 0, 0.8, 180.0, 280.0)
	_cast_at(game, d, 1, 180.0, 310.0)
	var reel := Bots.ReelFinger.new(game)
	reel.press(2, d.ms())
	assert_true(_play_until(d, 30.0, game.bot_biting, func(_t: float, ms: int) -> void: reel.turn(0.0, ms)))
	var biter := game.bot_suitor()
	_play_until(d, 0.5, func() -> bool: return game.bot_phase() == CastPhase.FIGHT, func(_t: float, ms: int) -> void: reel.turn(10.0, ms))
	assert_eq(CastPhase.FIGHT, game.bot_phase())
	assert_eq(biter, game.bot_hooked())
	assert_eq(FishMode.HOOKED, game.bot_fish_mode(biter))


# ---------------------------------------------------------------- the line

func test_too_much_tension_snaps_the_line() -> void:
	var h := _hooked(11, 1, 2.5, 180.0, 250.0)
	var game: FishingGame = h[0]
	var d: SimHarness.RoundDriver = h[1]
	var reel := Bots.ReelFinger.new(game)
	reel.press(5, d.ms())
	var peak := [0.0]
	d.play(6.0, func(_t: float, ms: int) -> void:
		reel.turn(30.0, ms)
		peak[0] = maxf(peak[0], game.bot_tension()))
	assert_eq(1, game.bot_snaps())
	assert_true(peak[0] > K.SNAP_TENSION, "the tension went over the limit: %s" % peak[0])
	assert_eq(0, game.bot_landed())
	assert_eq(0, game.score)
	assert_eq(-1, game.bot_hooked())


func test_a_slack_line_throws_the_hook() -> void:
	# Winding backwards gives line: slack at once.
	var h := _hooked(12, 1, 2.5, 180.0, 300.0)
	var game: FishingGame = h[0]
	var d: SimHarness.RoundDriver = h[1]
	var reel := Bots.ReelFinger.new(game)
	reel.press(5, d.ms())
	d.play(2.5, func(_t: float, ms: int) -> void: reel.turn(-10.0, ms))
	assert_eq(1, game.bot_thrown())
	assert_eq(0, game.bot_snaps())
	assert_eq(0, game.score)
	# Never winding at all loses it too, a little later.
	var h2 := _hooked(13, 0, 0.8, 180.0, 300.0)
	var idle: FishingGame = h2[0]
	var d2: SimHarness.RoundDriver = h2[1]
	assert_true(_play_until(d2, 20.0, func() -> bool: return idle.bot_thrown() == 1))
	assert_eq(0, idle.score)


func test_landing_scores_weight_times_species_value() -> void:
	var h := _hooked(16, 1, 2.5, 180.0, 420.0)
	var game: FishingGame = h[0]
	var d: SimHarness.RoundDriver = h[1]
	var reel := Bots.ReelFinger.new(game)
	reel.press(5, d.ms())
	assert_true(_play_until(d, 20.0, func() -> bool: return game.bot_landed() == 1 and game.bot_phase() == CastPhase.IDLE,
		func(_t: float, ms: int) -> void: reel.turn(_careful(game), ms)))
	assert_eq(30, game.bot_points(1, 2.5))
	assert_eq(30, game.score)
	assert_eq(30, game.bot_last_catch())
	assert_eq(0, game.bot_snaps() + game.bot_thrown())


func _seconds_to_land(species: int, weight: float, seed_value: int) -> float:
	var h := _hooked(seed_value, species, weight, 180.0, 250.0)
	var game: FishingGame = h[0]
	var d: SimHarness.RoundDriver = h[1]
	var reel := Bots.ReelFinger.new(game)
	reel.press(5, d.ms())
	var start := d.t
	assert_true(_play_until(d, 40.0, func() -> bool: return game.bot_landed() == 1, func(_t: float, ms: int) -> void: reel.turn(_careful(game), ms)))
	assert_eq(0, game.bot_snaps() + game.bot_thrown(), "%s got away" % K.NAMES[species])
	return d.t - start


func test_big_fish_take_longer() -> void:
	var perch := _seconds_to_land(0, 0.6, 17)
	var catfish := _seconds_to_land(2, 7.0, 17)
	assert_true(catfish > perch * 1.3, "catfish %s s vs perch %s s" % [catfish, perch])


# ---------------------------------------------------------------- the round's end

func test_no_scoring_after_time_up() -> void:
	var game := FishingGame.new()
	var d := SimHarness.RoundDriver.new(game, 18)
	d.play(game.round_seconds - 1.0)
	game.bot_place_fish(0, 2, 6.0, 180.0, 120.0)
	game.bot_hook_now(0)
	var reel := Bots.ReelFinger.new(game)
	reel.press(5, d.ms())
	assert_true(d.play(25.0, func(_t: float, ms: int) -> void: reel.turn(_careful(game), ms)))
	assert_eq(0, game.bot_landed(), "the fish still fighting got away")
	assert_eq(0, game.score)
	assert_eq(CastPhase.IDLE, game.bot_phase())
	# Nothing new is cast once time's up.
	game.on_touch(TouchType.DOWN, 9, 180.0, 300.0, d.ms())
	d.end()
	game.on_touch(TouchType.UP, 9, 180.0, 300.0, d.ms())
	assert_eq(0, game.bot_casts())
	assert_eq(0, game.score)


func test_a_fish_being_lifted_out_at_time_up_still_scores() -> void:
	var game := FishingGame.new()
	var d := SimHarness.RoundDriver.new(game, 19)
	d.play(game.round_seconds - 0.3)
	game.bot_place_fish(0, 0, 0.6, 180.0, FishingGame.DOCK_Z - K.LAND_DIST - 3.0)
	game.bot_hook_now(0)
	var reel := Bots.ReelFinger.new(game)
	reel.press(5, d.ms())
	_play_until(d, 0.25, func() -> bool: return game.bot_phase() == CastPhase.LANDING, func(_t: float, ms: int) -> void: reel.turn(8.0, ms))
	assert_eq(CastPhase.LANDING, game.bot_phase(), "landing before the buzzer")
	assert_true(d.play(5.0))
	d.end()
	assert_eq(1, game.bot_landed())
	assert_eq(game.bot_points(0, 0.6), game.score)


# ---------------------------------------------------------------- input

func test_cancel_input_drops_the_cast_being_wound_up() -> void:
	var game := FishingGame.new()
	var d := SimHarness.RoundDriver.new(game, 20)
	d.play(0.3)
	game.on_touch(TouchType.DOWN, 1, 120.0, 300.0, d.ms())
	d.play(0.4)
	assert_eq(CastPhase.CHARGE, game.bot_phase())
	assert_true(game.bot_power() > 0.0)
	d.pause()
	assert_eq(CastPhase.IDLE, game.bot_phase())
	d.play(0.3)
	# The lost finger's late events cast nothing.
	game.on_touch(TouchType.MOVE, 1, 200.0, 300.0, d.ms())
	game.on_touch(TouchType.UP, 1, 200.0, 300.0, d.ms())
	assert_eq(0, game.bot_casts())
	assert_eq(CastPhase.IDLE, game.bot_phase())
	# A new finger casts.
	game.on_touch(TouchType.DOWN, 2, 180.0, 300.0, d.ms())
	d.play(0.5)
	game.on_touch(TouchType.UP, 2, 180.0, 300.0, d.ms())
	assert_eq(1, game.bot_casts())
	assert_eq(CastPhase.FLIGHT, game.bot_phase())


func test_cancel_input_lets_go_of_the_reel() -> void:
	# The old boot: no runs, so winding always brings it closer.
	var boot := FishingGame.BOOT
	var h := _hooked(21, 4, 1.0, 180.0, 200.0, boot)
	var game: FishingGame = h[0]
	var d: SimHarness.RoundDriver = h[1]
	var reel := Bots.ReelFinger.new(game)
	reel.press(1, d.ms())
	d.play(0.5, func(_t: float, ms: int) -> void: reel.turn(8.0, ms))
	assert_true(game.bot_crank_rate() > 5.0)
	d.pause()
	reel.lost()
	assert_false(game.bot_crank_held())
	assert_eq(0.0, game.bot_crank_rate())
	var before := game.bot_line_dist()
	for k in range(1, 11):
		var a := k * 0.3
		game.on_touch(TouchType.MOVE, 1, FishingGame.REEL_CX + cos(a) * 46.0, FishingGame.REEL_CY + sin(a) * 46.0, d.ms() + k * 16)
	d.play(0.2)
	assert_eq(0.0, game.bot_crank_rate(), "the lost finger winds nothing")
	assert_true(game.bot_line_dist() >= before - 0.01)
	assert_eq(CastPhase.FIGHT, game.bot_phase())
	var again := Bots.ReelFinger.new(game)
	again.press(2, d.ms())
	d.play(0.4, func(_t: float, ms: int) -> void: again.turn(8.0, ms))
	assert_true(game.bot_line_dist() < before - 10.0, "a new finger winds the fish in")


func test_cast_and_reel_fingers_are_tracked_by_their_ids() -> void:
	var game := FishingGame.new()
	var d := SimHarness.RoundDriver.new(game, 22)
	d.play(0.3)
	# One finger holds a cast out to the left, another circles the reel.
	game.on_touch(TouchType.DOWN, 1, 60.0, 300.0, d.ms())
	var reel := Bots.ReelFinger.new(game)
	reel.press(2, d.ms())
	d.play(0.3, func(_t: float, ms: int) -> void: reel.turn(10.0, ms))
	assert_true(game.bot_crank_held())
	assert_eq(CastPhase.CHARGE, game.bot_phase())
	# A stranger's events change nothing.
	game.on_touch(TouchType.MOVE, 7, 300.0, 300.0, d.ms())
	game.on_touch(TouchType.UP, 7, 300.0, 300.0, d.ms())
	assert_eq(CastPhase.CHARGE, game.bot_phase())
	assert_true(game.bot_crank_held())
	game.on_touch(TouchType.UP, 1, 60.0, 300.0, d.ms())
	assert_eq(1, game.bot_casts())
	assert_true(game.bot_crank_held(), "the reel finger is still on")
	d.play(1.5, func(_t: float, ms: int) -> void: reel.turn(0.0, ms))
	assert_true(game.bot_lure_x() < FishingGame.DOCK_X - 20.0, "the cast went left: %s" % game.bot_lure_x())


# ---------------------------------------------------------------- robustness

## Random play: taps, holds, casts, circles of any speed either way, pauses.
func _fuzz_round(game: FishingGame, seed_value: int, bot_seed: int, check: bool, problems: Array) -> int:
	var rng := KRandom.new(bot_seed)
	var reel := Bots.ReelFinger.new(game)
	# next time, cast id, next id, omega
	var s := [0.0, -1, 10, 0.0]
	var slots := game.bot_slots()
	SimHarness.play_round(self, game, seed_value, null, func(t: float, ms: int) -> void:
		if t >= s[0]:
			s[0] = t + 0.2 + rng.next_float() * 0.8
			var pick := rng.next_int_until(6)
			if pick == 0:
				if s[1] < 0:
					s[1] = s[2]
					s[2] += 1
					game.on_touch(TouchType.DOWN, s[1], rng.next_float() * 360.0, rng.next_float() * 460.0, ms)
			elif pick == 1:
				if s[1] >= 0:
					game.on_touch(TouchType.UP, s[1], rng.next_float() * 360.0, 300.0, ms)
					s[1] = -1
			elif pick == 2:
				if not reel.down():
					reel.press(s[2], ms)
					s[2] += 1
				else:
					reel.lift(ms)
			elif pick == 3:
				s[3] = (rng.next_float() - 0.3) * 30.0
			elif pick == 4:
				if rng.next_float() < 0.15:
					game.cancel_input()
					reel.lost()
					s[1] = -1
			else:
				game.on_touch(TouchType.MOVE, 999, rng.next_float() * 360.0, rng.next_float() * 640.0, ms)
		reel.turn(s[3], ms)
		if check:
			if game.bot_slots() != slots:
				problems.append("the fish pool grew to %d" % game.bot_slots())
			if not (is_finite(game.bot_tension()) and is_finite(game.bot_line_dist()) and is_finite(game.bot_power())):
				problems.append("tension, line or power went NaN at %s" % t)
			if not (is_finite(game.bot_lure_x()) and is_finite(game.bot_lure_z())):
				problems.append("the lure went NaN at %s" % t)
			var busy := 0
			for i in slots:
				if not (is_finite(game.bot_fish_x(i)) and is_finite(game.bot_fish_y(i)) and is_finite(game.bot_fish_z(i))):
					problems.append("fish %d went NaN" % i)
				var m := game.bot_fish_mode(i)
				if m == FishMode.HOOKED or m == FishMode.LANDED:
					busy += 1
			if busy > 1:
				problems.append("%d fish on the line" % busy))
	return game.score


func test_random_play_always_finishes_without_nan() -> void:
	var game := FishingGame.new()
	var problems: Array[String] = []
	for k in 8:
		_fuzz_round(game, 100 + k, k, true, problems)
	assert_true(problems.is_empty(), str(problems.slice(0, 5)))
	assert_eq(K.FISH_SLOTS + 1, game.bot_slots())


func test_reset_leaves_nothing_from_the_last_round() -> void:
	var game := FishingGame.new()
	var d := SimHarness.RoundDriver.new(game, 23)
	d.play(0.2)
	game.bot_place_fish(0, 1, 2.0, 180.0, 300.0)
	game.bot_hook_now(0)
	var reel := Bots.ReelFinger.new(game)
	reel.press(1, d.ms())
	d.play(0.6, func(_t: float, ms: int) -> void: reel.turn(8.0, ms))
	game.on_touch(TouchType.DOWN, 2, 100.0, 300.0, d.ms())
	# A new round starts on the same machine mid-fight.
	d = SimHarness.RoundDriver.new(game, 24)
	assert_eq(CastPhase.IDLE, game.bot_phase())
	assert_eq(-1, game.bot_hooked())
	assert_eq(-1, game.bot_suitor())
	assert_eq(0.0, game.bot_tension())
	assert_eq(0.0, game.bot_line_dist())
	assert_false(game.bot_crank_held())
	assert_eq(0.0, game.bot_crank_rate())
	assert_eq(0, game.score)
	assert_eq(0, game.bot_casts() + game.bot_landed() + game.bot_snaps() + game.bot_thrown() + game.bot_missed() + game.bot_too_soon())
	for i in game.bot_slots():
		assert_eq(FishMode.SWIM, game.bot_fish_mode(i))
	# The old fingers are forgotten.
	game.on_touch(TouchType.MOVE, 1, FishingGame.REEL_CX, FishingGame.REEL_CY + 46.0, d.ms())
	game.on_touch(TouchType.UP, 2, 100.0, 300.0, d.ms())
	d.play(0.2)
	assert_eq(0.0, game.bot_crank_rate())
	assert_eq(0, game.bot_casts())
	# And a round replays exactly whether or not the machine played before.
	var fresh := _fuzz_round(FishingGame.new(), 77, 5, false, [])
	var reused := FishingGame.new()
	_fuzz_round(reused, 78, 6, false, [])
	assert_eq(fresh, _fuzz_round(reused, 77, 5, false, []))
