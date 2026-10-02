extends PaTest
## Godot-only: build-13's coin-pusher bots from games/GameSimulationTest.kt (see [PusherBot]) play
## seeded rounds, the payouts land in the bands every machine must hit, and a seed replays the same
## round. Round seeds are the ones GameSimulationTest gave the pusher (73..96). Also
## GameSimulationTest's pusherNeverScoresBeforeTheFirstCoin, which is about this machine alone.
##
## A pusher round costs GDScript seconds (the deck's 140-odd coins at 120 Hz), so the default run
## plays the first 3 of build-13's 12 rounds per bot; `tools/run_tests.sh --slow` plays all 12.


func _rounds() -> int:
	return 12 if OS.get_cmdline_user_args().has("--slow") else 3


func test_good_and_casual_bots_hit_the_payout_bands() -> void:
	var rounds := _rounds()
	var good := PusherBot.play(self, rounds, 0.5, 7, [73])
	var casual := PusherBot.play(self, rounds, 1.8, 8, [85])
	print(good)
	print(casual)
	SimHarness.assert_payout_bands(self, good, casual)


func test_a_seed_replays_the_same_round() -> void:
	var a := PusherBot.play(self, 1, 0.5, 7, [73])
	var b := PusherBot.play(self, 1, 0.5, 7, [73])
	assert_eq(a.scores, b.scores)
	assert_eq(a.tickets, b.tickets)
	assert_gt(a.scores[0], 0, "the bot scored")


func test_pusher_never_scores_before_the_first_coin() -> void:
	var game := CoinPusherGame.new()
	game.fixed_seed = 42
	game.start(SimHarness.fx())
	var time_left := game.round_seconds
	for i in int(5.0 / GameLoop.FIXED_DT):
		time_left -= GameLoop.FIXED_DT
		game.update(GameLoop.FIXED_DT, time_left)
	assert_true(game.score == 0, "deck spilled on its own: %d" % game.score)
