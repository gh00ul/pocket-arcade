extends PaTest
## Godot: air hockey played headless by build-13's bots (games/GameSimulationTest.kt `hockey`):
## the quick and the laggy player pay inside the bands every machine must hit, skill pays, and a
## seeded round replays to the same score. The round seeds are the ones build-13's simulation gave
## air hockey (its shared counter reached 121 by then).

const ROUNDS := 12
## build-13's GameSimulationTest round seeds for air hockey: good 121..132, casual 133..144.
const GOOD_SEED := 121
const CASUAL_SEED := 133


## games/GameSimulationTest.kt `hockey`: guards the goal, and when the puck comes into our half
## strikes through it towards the far goal. [param lag] is how often the bot re-reads the table;
## [param noise] is aiming error in world units.
static func bot(game: AirHockeyGame, rng: KRandom, lag: float, noise: float) -> Callable:
	var st := [0.0, false, 180.0, 540.0]  # next, down, tx, ty
	return func(t: float, ms: int) -> void:
		if t >= st[0]:
			st[0] = t + lag
			var px := game.bot_puck_x()
			var py := game.bot_puck_y()
			if py > 340.0:
				# Aim for the side of the goal the CPU isn't covering.
				var aim_x := 140.0 if game.bot_cpu_x() > 180.0 else 220.0
				var gx := aim_x - px
				var gy := 60.0 - py
				var gl := maxf(sqrt(gx * gx + gy * gy), 1.0)
				var behind := -8.0 if game.bot_mallet_y() > py + 8.0 else 40.0
				st[2] = px - gx / gl * behind + SimHarness.gaussian(rng) * noise
				st[3] = py - gy / gl * behind + SimHarness.gaussian(rng) * noise
			else:
				st[2] = 180.0 + (px - 180.0) * 0.5
				st[3] = 550.0
		var s := game.bot_screen(st[2], st[3])
		if not st[1]:
			game.on_touch(TouchType.DOWN, 1, s.x, s.y, ms)
			st[1] = true
		else:
			game.on_touch(TouchType.MOVE, 1, s.x, s.y, ms)


func _play(name: String, rounds: int, first_seed: int, lag: float, noise: float, rng_seed: int) -> SimHarness.Stats:
	var stats := SimHarness.Stats.new(name)
	var rng := KRandom.new(rng_seed)
	var game := AirHockeyGame.new()
	for i in rounds:
		SimHarness.play_round(self, game, first_seed + i, stats, bot(game, rng, lag, noise))
	return stats


func test_air_hockey_pays_out_and_rewards_skill() -> void:
	var good := _play("hockey lag 50ms", ROUNDS, GOOD_SEED, 0.05, 3.0, 11)
	var casual := _play("hockey lag 300ms", ROUNDS, CASUAL_SEED, 0.3, 18.0, 12)
	print(good)
	print(casual)
	SimHarness.assert_payout_bands(self, good, casual)


func test_air_hockey_same_seed_same_score() -> void:
	var a := AirHockeyGame.new()
	SimHarness.play_round(self, a, GOOD_SEED, null, bot(a, KRandom.new(11), 0.05, 3.0))
	var b := AirHockeyGame.new()
	# A round on another seed first: a reused machine must replay exactly like a fresh one.
	SimHarness.play_round(self, b, 7, null, bot(b, KRandom.new(3), 0.05, 3.0))
	SimHarness.play_round(self, b, GOOD_SEED, null, bot(b, KRandom.new(11), 0.05, 3.0))
	assert_eq(a.score, b.score)
	assert_eq(a.bot_goals(), b.bot_goals())
	assert_eq(a.bot_cpu_x(), b.bot_cpu_x())
	assert_eq(a.bot_puck_y(), b.bot_puck_y())
