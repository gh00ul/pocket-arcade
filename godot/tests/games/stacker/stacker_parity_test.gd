extends PaTest
## Godot-only: a seeded stacker round against numbers build-13 printed (a scratch run of its
## GameSimulationTest bot on round seed 145, the first stacker round of build-13's simulation).
## Played with build-13's clock (F32RoundDriver), every one of the 46 drops lands on the same step
## with the same score: slides, cuts, perfects, combos and the end of the round all agree.

## build-13: "step:score" each time the tower grew, round seed 145, good bot.
const BUILD13_DROPS := "127:10 268:20 399:30 515:50 635:60 744:80 846:90 953:100 1054:120 1154:130 1247:150 1339:175 1430:185 1516:205 1596:230 1670:240 1749:260 1827:285 1903:315 1975:350 2049:360 2117:380 2186:405 2249:415 2318:425 2382:445 2445:470 2504:500 2559:510 2617:530 2679:540 2741:550 2796:570 2849:595 2902:625 2956:635 3006:655 3057:680 3103:690 3151:710 3198:735 3249:745 3292:755 3335:765 3380:775 3429:785"


## games/GameSimulationTest.kt `stacker` with its Float arithmetic (the aim's noise).
static func f32_bot(game: StackerGame, rng: KRandom, sigma: float) -> Callable:
	var st := [-1, 0.0, NAN, 1]  # height, aim, last, id
	return func(_t: float, ms: int) -> void:
		if not game.bot_moving():
			st[2] = NAN
			return
		if game.bot_height() != st[0]:
			st[0] = game.bot_height()
			st[1] = F32RoundDriver.f32(F32RoundDriver.gaussian(rng) * F32RoundDriver.f32(sigma))
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


func test_seeded_round_follows_build_13() -> void:
	var game := StackerGame.new()
	var d := F32RoundDriver.new(game, 145)
	var bot := f32_bot(game, KRandom.new(13), 8.0)
	var drops: Array[String] = []
	var h := [0]
	d.play(game.round_seconds + 20.0, func(t: float, ms: int) -> void:
		bot.call(t, ms)
		if game.bot_height() != h[0]:
			h[0] = game.bot_height()
			drops.append("%d:%d" % [d.steps, game.score]))
	assert_true(game.finished(), "the round ended")
	assert_eq(BUILD13_DROPS, " ".join(PackedStringArray(drops)))
