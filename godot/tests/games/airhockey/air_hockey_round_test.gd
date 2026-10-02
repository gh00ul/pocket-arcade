extends PaTest
## Godot-only: build-13's cross-game round checks (games/InputCancelTest.kt, ReplayResetTest.kt),
## run on air hockey here so the machine is covered on its own branch: the mallet stops chasing a
## lost finger, and PLAY AGAIN replays like a fresh machine (with the generic bot and with
## ReplayResetTest's puck chaser, which keeps the CPU busy defending).

const ROUND_TEST := preload("res://tests/games/hoops/hoops_round_test.gd")


func test_the_mallet_stops_chasing_the_lost_finger() -> void:
	# InputCancelTest.airHockeyMalletStopsChasingTheLostFinger
	var game := AirHockeyGame.new()
	var d := SimHarness.RoundDriver.new(game, 11)
	d.play(0.2)
	var f := game.bot_screen(70.0, 560.0)
	game.on_touch(TouchType.DOWN, 1, f.x, f.y, d.ms())
	d.play(0.02)
	d.pause()
	var mx := game.bot_mallet_x()
	var my := game.bot_mallet_y()
	assert_true(mx < 170.0 and mx > 80.0, "the mallet should be on its way")
	d.play(0.5)
	assert_near(mx, game.bot_mallet_x(), 0.5)
	assert_near(my, game.bot_mallet_y(), 0.5)
	game.on_touch(TouchType.MOVE, 1, f.x, f.y, d.ms())
	game.on_touch(TouchType.UP, 1, f.x, f.y, d.ms())
	var n := game.bot_screen(260.0, 540.0)
	game.on_touch(TouchType.DOWN, 2, n.x, n.y, d.ms())
	d.play(0.3)
	assert_near(260.0, game.bot_mallet_x(), 3.0, "a new finger should move the mallet")
	assert_near(540.0, game.bot_mallet_y(), 3.0)


func test_a_replay_plays_like_a_fresh_machine() -> void:
	# ReplayResetTest.everyMachineReplaysLikeAFreshOne, for air hockey.
	var fresh := AirHockeyGame.new()
	var expected := ROUND_TEST.trace(self, fresh, 42, ROUND_TEST.replay_bot(fresh))
	var reused := AirHockeyGame.new()
	ROUND_TEST.trace(self, reused, 7, ROUND_TEST.replay_bot(reused))
	assert_eq(expected, ROUND_TEST.trace(self, reused, 42, ROUND_TEST.replay_bot(reused)))


## ReplayResetTest.airHockeyReplaysLikeAFreshOneAgainstAChaser's round: the finger follows the puck.
func _chase(g: AirHockeyGame, seed_value: int) -> String:
	var parts: Array[String] = []
	var st := [false, 0]  # down, k
	var d := SimHarness.RoundDriver.new(g, seed_value)
	d.play(g.round_seconds + 20.0, func(_t: float, ms: int) -> void:
		var s := g.bot_screen(g.bot_puck_x(), g.bot_puck_y() + 10.0)
		g.on_touch(TouchType.MOVE if st[0] else TouchType.DOWN, 1, s.x, s.y, ms)
		st[0] = true
		if st[1] % 30 == 0:
			parts.append("%.1f,%.1f;" % [g.bot_cpu_x(), g.bot_cpu_y()])
		st[1] += 1)
	d.end()
	return "".join(PackedStringArray(parts)) + " score=%d goals=%s" % [g.score, g.bot_goals()]


func test_a_replay_plays_like_a_fresh_machine_against_a_chaser() -> void:
	var expected := _chase(AirHockeyGame.new(), 42)
	var reused := AirHockeyGame.new()
	_chase(reused, 7)
	assert_eq(expected, _chase(reused, 42))
