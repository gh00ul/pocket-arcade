extends PaTest
## Godot: hoops played headless by build-13's bots (games/GameSimulationTest.kt `hoops`): the good
## and casual shooters pay inside the bands every machine must hit, skill pays, and a seeded round
## replays to the same score. The round seeds are the ones build-13's simulation gave hoops (its
## shared counter reached 97 by the time hoops played), so the table can be read against it.

const ROUNDS := 12
## build-13's GameSimulationTest round seeds for hoops: good 97..108, casual 109..120.
const GOOD_SEED := 97
const CASUAL_SEED := 109


## games/GameSimulationTest.kt `hoops`: every second a flick from the bottom middle, its power off
## the ideal by [param speed_noise] (a share) and drifting sideways by [param lateral_noise].
static func bot(game: HoopsGame, rng: KRandom, speed_noise: float, lateral_noise: float) -> Callable:
	var next := [0.5]
	var id := [1]
	return func(t: float, ms: int) -> void:
		if t >= next[0]:
			next[0] = t + 1.0
			var up := HoopsTuning.FLICK_IDEAL * (1.0 + SimHarness.gaussian(rng) * speed_noise)
			var lateral := SimHarness.gaussian(rng) * lateral_noise
			SimHarness.flick(game, id[0], 180.0, 560.0, lateral, -up, ms)
			id[0] += 1


func _play(name: String, rounds: int, first_seed: int, speed_noise: float, lateral_noise: float, rng_seed: int) -> SimHarness.Stats:
	var stats := SimHarness.Stats.new(name)
	var rng := KRandom.new(rng_seed)
	var game := HoopsGame.new()
	for i in rounds:
		SimHarness.play_round(self, game, first_seed + i, stats, bot(game, rng, speed_noise, lateral_noise))
	return stats


func test_hoops_pays_out_and_rewards_skill() -> void:
	var good := _play("hoops noise 4%", ROUNDS, GOOD_SEED, 0.04, 40.0, 9)
	var casual := _play("hoops noise 14%", ROUNDS, CASUAL_SEED, 0.14, 150.0, 10)
	print(good)
	print(casual)
	SimHarness.assert_payout_bands(self, good, casual)


func test_hoops_same_seed_same_score() -> void:
	var a := HoopsGame.new()
	SimHarness.play_round(self, a, GOOD_SEED, null, bot(a, KRandom.new(9), 0.04, 40.0))
	var b := HoopsGame.new()
	# A round on another seed first: a reused machine must replay exactly like a fresh one.
	SimHarness.play_round(self, b, 5, null, bot(b, KRandom.new(3), 0.04, 40.0))
	SimHarness.play_round(self, b, GOOD_SEED, null, bot(b, KRandom.new(9), 0.04, 40.0))
	assert_eq(a.score, b.score)
	assert_eq(a.bot_shots(), b.bot_shots())
	assert_gt(a.score, 0, "the good shooter scores")
