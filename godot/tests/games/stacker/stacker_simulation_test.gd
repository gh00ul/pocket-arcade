extends PaTest
## Godot: the stacker played headless by build-13's bots (games/GameSimulationTest.kt `stacker`):
## the steady and the shaky tapper pay inside the bands every machine must hit, skill pays, and a
## seeded round replays to the same score. The round seeds are the ones build-13's simulation gave
## the stacker (its shared counter reached 145 by then).

const ROUNDS := 12
## build-13's GameSimulationTest round seeds for the stacker: good 145..156, casual 157..168.
const GOOD_SEED := 145
const CASUAL_SEED := 157


## games/GameSimulationTest.kt `stacker`: taps when the sliding slab passes a spot [param sigma]
## away from perfect, on average.
static func bot(game: StackerGame, rng: KRandom, sigma: float) -> Callable:
	var st := [-1, 0.0, NAN, 1]  # height, aim, last, id
	return func(_t: float, ms: int) -> void:
		if not game.bot_moving():
			st[2] = NAN
			return
		if game.bot_height() != st[0]:
			st[0] = game.bot_height()
			st[1] = SimHarness.gaussian(rng) * sigma
			st[2] = NAN
		var d: float = game.bot_delta() - st[1]
		var last: float = st[2]
		if not is_nan(last) and (d > 0.0) != (last > 0.0):
			SimHarness.tap(game, st[3], 180.0, 300.0, ms, 30)
			st[3] += 1
			st[2] = NAN
			st[0] = -2
		else:
			st[2] = d


func _play(name: String, rounds: int, first_seed: int, sigma: float, rng_seed: int) -> SimHarness.Stats:
	var stats := SimHarness.Stats.new(name)
	var rng := KRandom.new(rng_seed)
	var game := StackerGame.new()
	for i in rounds:
		SimHarness.play_round(self, game, first_seed + i, stats, bot(game, rng, sigma))
	return stats


func test_stacker_pays_out_and_rewards_skill() -> void:
	var good := _play("stacker sigma 8", ROUNDS, GOOD_SEED, 8.0, 13)
	var casual := _play("stacker sigma 22", ROUNDS, CASUAL_SEED, 22.0, 14)
	print(good)
	print(casual)
	SimHarness.assert_payout_bands(self, good, casual)


func test_stacker_same_seed_same_score() -> void:
	var a := StackerGame.new()
	SimHarness.play_round(self, a, GOOD_SEED, null, bot(a, KRandom.new(13), 8.0))
	var b := StackerGame.new()
	# A round on another seed first: a reused machine must replay exactly like a fresh one.
	SimHarness.play_round(self, b, 3, null, bot(b, KRandom.new(5), 8.0))
	SimHarness.play_round(self, b, GOOD_SEED, null, bot(b, KRandom.new(13), 8.0))
	assert_eq(a.score, b.score)
	assert_eq(a.bot_height(), b.bot_height())
	assert_gt(a.bot_height(), 10, "the steady tapper builds a tower")
