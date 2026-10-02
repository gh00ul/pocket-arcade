extends PaTest
## Godot-only: build-13's cross-game round checks (games/InputCancelTest.kt, ReplayResetTest.kt),
## run on hoops here so the machine is covered on its own branch: a pause lets go of the ball being
## lined up, and PLAY AGAIN replays like a fresh machine.


func test_cancel_input_drops_the_ball_being_lined_up() -> void:
	# InputCancelTest.hoopsDropsTheBallBeingLinedUp
	var game := HoopsGame.new()
	var d := SimHarness.RoundDriver.new(game, 9)
	d.play(0.3)
	game.on_touch(TouchType.DOWN, 1, 180.0, 560.0, d.ms())
	game.on_touch(TouchType.MOVE, 1, 220.0, 540.0, d.ms() + 16)
	d.pause()
	d.play(0.5)
	game.on_touch(TouchType.UP, 1, 220.0, 300.0, d.ms())
	assert_eq(0, game.bot_shots())
	SimHarness.flick(game, 2, 180.0, 560.0, 0.0, -HoopsTuning.FLICK_IDEAL, d.ms() + 100)
	assert_eq(1, game.bot_shots(), "a new finger should shoot")


## ReplayResetTest's bot: seeded taps and upward flicks, not tuned to any machine.
static func replay_bot(game: MiniGame) -> Callable:
	var r := KRandom.new(99)
	var st := [0.2, 0]  # next, n
	return func(t: float, ms: int) -> void:
		if t >= st[0]:
			st[0] += 0.37
			st[1] += 10
			if r.next_boolean():
				var x := r.next_float() * MiniGame.GAME_W
				var y := r.next_float() * MiniGame.GAME_H
				SimHarness.tap(game, st[1], x, y, ms)
			else:
				var x0 := 120.0 + r.next_float() * 120.0
				var vx := (r.next_float() - 0.5) * 600.0
				var vy := -1200.0 - r.next_float() * 900.0
				SimHarness.flick(game, st[1] + 1, x0, 590.0, vx, vy, ms)


## ReplayResetTest.trace: the score every second of a whole round, then the payout.
static func trace(test: PaTest, game: MiniGame, seed_value: int, bot: Callable) -> String:
	var parts: Array[String] = []
	var last := [-1]
	var d := SimHarness.RoundDriver.new(game, seed_value)
	d.play(game.round_seconds + 20.0, func(t: float, ms: int) -> void:
		bot.call(t, ms)
		var s := int(t)
		if s != last[0]:
			last[0] = s
			parts.append(str(game.score)))
	d.end()
	test.assert_true(game.finished(), "%s finished" % game.title)
	return "%s steps=%d score=%d tickets=%d+%d" % [",".join(PackedStringArray(parts)), d.steps, game.score, game.tickets_for(game.score), game.bonus_tickets]


func test_a_replay_plays_like_a_fresh_machine() -> void:
	# ReplayResetTest.everyMachineReplaysLikeAFreshOne, for hoops.
	var fresh := HoopsGame.new()
	var expected := trace(self, fresh, 42, replay_bot(fresh))
	var reused := HoopsGame.new()
	trace(self, reused, 7, replay_bot(reused))
	assert_eq(expected, trace(self, reused, 42, replay_bot(reused)))
