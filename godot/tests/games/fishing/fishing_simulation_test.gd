extends PaTest
## games/fishing/FishingSimulationTest.kt: plays Gone Fishing headlessly through the touch path with
## bots of different skill, and checks the payout bands. Plus (Godot-only) the same seed replaying
## the same round.

const Bots := preload("res://tests/games/fishing/fishing_bots.gd")

var _round_seed := 1


func _angler(rounds: int, seed_value: int, good: bool) -> SimHarness.Stats:
	var stats := SimHarness.Stats.new("fishing good" if good else "fishing casual")
	var rng := KRandom.new(seed_value)
	var game := FishingGame.new()
	var landed := 0
	var snaps := 0
	var thrown := 0
	var missed := 0
	var early := 0
	var casts := 0
	for r in rounds:
		var bot: RefCounted = Bots.GoodAngler.new(game, rng) if good else Bots.CasualAngler.new(game, rng)
		SimHarness.play_round(self, game, _round_seed, stats, bot.tick)
		_round_seed += 1
		landed += game.bot_landed()
		snaps += game.bot_snaps()
		thrown += game.bot_thrown()
		missed += game.bot_missed()
		early += game.bot_too_soon()
		casts += game.bot_casts()
		if good:
			assert_eq(0, game.bot_failsafe_trips(), "failsafes tripped")
	if good:
		print("good angler: landed %d, snapped %d, thrown %d, missed %d over %d rounds" % [landed, snaps, thrown, missed, rounds])
	else:
		print("casual angler: %d casts, landed %d, snapped %d, thrown %d, missed %d, too soon %d over %d rounds" % [casts, landed, snaps, thrown, missed, early, rounds])
	return stats


func test_fishing_pays_out_and_rewards_skill() -> void:
	var rounds := 12
	var good := _angler(rounds, 31, true)
	var casual := _angler(rounds, 32, false)
	print(good)
	print(casual)
	SimHarness.assert_payout_bands(self, good, casual)
	var all_landed := true
	for s in good.scores:
		if s <= 0:
			all_landed = false
	assert_true(all_landed, "a good angler lands fish every round: %s" % str(good.scores))


## Godot-only: a seed and a bot replay the same round (score, casts, catches) on a fresh machine and
## on one that already played another round.
func test_godot_seeded_rounds_replay_exactly() -> void:
	for good: bool in [true, false]:
		var results := []
		for attempt in 2:
			var game := FishingGame.new()
			if attempt == 1:
				var warm: RefCounted = Bots.GoodAngler.new(game, KRandom.new(99)) if good else Bots.CasualAngler.new(game, KRandom.new(99))
				SimHarness.play_round(self, game, 4242, null, warm.tick)
			var bot: RefCounted = Bots.GoodAngler.new(game, KRandom.new(7)) if good else Bots.CasualAngler.new(game, KRandom.new(7))
			SimHarness.play_round(self, game, 123, null, bot.tick)
			results.append([game.score, game.bot_casts(), game.bot_landed(), game.bot_snaps(), game.bot_thrown()])
		assert_eq(results[0], results[1], "the same seed and bot replay the same round")
