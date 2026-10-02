extends PaTest
## Godot-only: build-13's skee-ball bots from games/GameSimulationTest.kt (see [SkeeBot]) play
## seeded rounds, the payouts land in the bands every machine must hit, and a seed replays the same
## round. Round seeds are the ones GameSimulationTest gave skee-ball (25..48: the claw's 24 rounds
## come first).

const ROUNDS := 12


func test_good_and_casual_bots_hit_the_payout_bands() -> void:
	var seeds := [25]
	var good := SkeeBot.play(self, ROUNDS, 0.04, 2.0, 3, seeds)
	var casual := SkeeBot.play(self, ROUNDS, 0.15, 6.0, 4, seeds)
	print(good)
	print(casual)
	SimHarness.assert_payout_bands(self, good, casual)


func test_a_seed_replays_the_same_round() -> void:
	var a := SkeeBot.play(self, 2, 0.15, 6.0, 4, [37])
	var b := SkeeBot.play(self, 2, 0.15, 6.0, 4, [37])
	assert_eq(a.scores, b.scores)
	assert_eq(a.tickets, b.tickets)
	assert_gt(a.scores[0] + a.scores[1], 0, "the bot scored")
