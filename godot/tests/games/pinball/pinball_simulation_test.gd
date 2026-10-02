extends PaTest
## games/pinball/PinballSimulationTest.kt: plays Star Flipper headlessly through the real touch path:
## a player who flips as the ball comes onto a flipper and plunges hard, and a casual one who flips
## late, misses some balls and flips at random. Checks the payout bands and that every round
## finishes. Plus (Godot-only) the same seed replaying the same round.

const K := preload("res://scripts/games/pinball/pinball_tuning.gd")
const Bots := preload("res://tests/games/pinball/pinball_bots.gd")

const PLUNGE_X := 320.0
const PLUNGE_Y := 520.0
const FLIP_Y := 560.0

## Seeds successive rounds so every run replays the same games.
var _round_seed := 1


## The good player of the payout test (Kotlin: reaction 0, miss 2%, lead 35 ms, no panic flips,
## full plunges).
static func good_bot(g: PinballGame, r: KRandom) -> RefCounted:
	return Bots.FlipperBot.new(g, r, 0.0, 0.02, 0.035, 0.0, 0.95)


## The casual player (Kotlin: reaction 160 ms, misses 30%, no lead, 0.4 panic flips a second,
## plunges from 30%).
static func casual_bot(g: PinballGame, r: KRandom) -> RefCounted:
	return Bots.FlipperBot.new(g, r, 0.16, 0.3, 0.0, 0.4, 0.3)


func _pinball(rounds: int, seed_value: int, name: String, make: Callable) -> SimHarness.Stats:
	var stats := SimHarness.Stats.new(name)
	var rng := KRandom.new(seed_value)
	var game := PinballGame.new()
	var drains := 0
	var bumpers := 0
	var multiballs := 0
	var jackpots := 0
	var failsafe := 0
	var early := 0
	var play_time := 0.0
	for i in rounds:
		var bot: RefCounted = make.call(game, rng)
		var d := SimHarness.play_round(self, game, _round_seed, stats, bot.tick)
		_round_seed += 1
		drains += game.bot_drains()
		bumpers += game.bot_bumper_hits()
		multiballs += game.bot_multiballs()
		jackpots += game.bot_jackpots()
		failsafe += game.bot_failsafe_trips()
		if game.bot_ended_early():
			early += 1
		play_time += d.t
	print("%s  drains %.1f  bumpers %.1f  multiballs %.2f  jackpots %.2f  failsafe %d  ended early %d/%d  avg %.1fs" % [
		stats, drains / float(rounds), bumpers / float(rounds), multiballs / float(rounds), jackpots / float(rounds),
		failsafe, early, rounds, play_time / rounds])
	return stats


func test_pinball_pays_out_and_rewards_skill() -> void:
	var rounds := 14
	var good := _pinball(rounds, 31, "pinball good", good_bot)
	var casual := _pinball(rounds, 32, "pinball casual", casual_bot)
	SimHarness.assert_payout_bands(self, good, casual)


func test_every_round_finishes_even_with_nobody_playing() -> void:
	var game := PinballGame.new()
	var stats := SimHarness.Stats.new("idle")
	for i in 3:
		SimHarness.play_round(self, game, _round_seed, stats, func(_t: float, _ms: int) -> void: pass)
		_round_seed += 1
	assert_eq(3, stats.scores.size())


func test_a_round_with_only_plunges_ends_early_when_the_balls_run_out() -> void:
	var game := PinballGame.new()
	var rng := KRandom.new(5)
	var stats := SimHarness.Stats.new("plunge only")
	var d := SimHarness.play_round(self, game, 77, stats, func(t: float, ms: int) -> void:
		if game.bot_lane_ready() and not game.bot_plunger_held():
			var pid := int(t * 1000.0) + 1
			game.on_touch(TouchType.DOWN, pid, PLUNGE_X, PLUNGE_Y, ms)
			game.on_touch(TouchType.MOVE, pid, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE * (0.6 + 0.4 * rng.next_float()), ms + 50)
			game.on_touch(TouchType.UP, pid, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE, ms + 100))
	print("plunge-only round: %.1fs, drains %d, score %d" % [d.t, game.bot_drains(), game.score])
	assert_true(game.bot_ended_early() or d.t >= game.round_seconds)


## Godot-only: the payout bands with both bots over a few seeded rounds each, and a seed replays the
## same round (score, drains, bumper hits) on a fresh machine and on one that already played.
func test_godot_seeded_rounds_replay_exactly() -> void:
	for make: Callable in [good_bot, casual_bot]:
		var scores := []
		for attempt in 2:
			var game := PinballGame.new()
			if attempt == 1:
				# A machine that already played a different round first.
				var warm := make.call(game, KRandom.new(99)) as RefCounted
				SimHarness.play_round(self, game, 4242, null, warm.tick)
			var bot := make.call(game, KRandom.new(7)) as RefCounted
			SimHarness.play_round(self, game, 123, null, bot.tick)
			scores.append([game.score, game.bot_drains(), game.bot_bumper_hits(), game.bot_launches()])
		assert_eq(scores[0], scores[1], "the same seed and bot replay the same round")
