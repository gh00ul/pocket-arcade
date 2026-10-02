extends PaTest
## Godot-only: build-13's cross-game round checks (games/InputCancelTest.kt, ReplayResetTest.kt),
## run on the stacker here so the machine is covered on its own branch: a tap still drops after a
## pause, and PLAY AGAIN replays like a fresh machine.

const ROUND_TEST := preload("res://tests/games/hoops/hoops_round_test.gd")


func _wait_for_line_up(game: StackerGame, d: SimHarness.RoundDriver) -> void:
	while not (game.bot_moving() and absf(game.bot_delta()) < 4.0) and d.t < 20.0:
		d.play(GameLoop.FIXED_DT)
	assert_true(game.bot_moving(), "the slab never lined up")


func test_a_tap_still_drops_after_a_pause() -> void:
	# InputCancelTest.stackerStillDropsAfterAPause
	var game := StackerGame.new()
	var d := SimHarness.RoundDriver.new(game, 13)
	_wait_for_line_up(game, d)
	game.on_touch(TouchType.DOWN, 1, 180.0, 300.0, d.ms())
	assert_eq(1, game.bot_height())
	d.pause()
	d.play(0.2)
	game.on_touch(TouchType.UP, 1, 180.0, 300.0, d.ms())
	_wait_for_line_up(game, d)
	SimHarness.tap(game, 2, 180.0, 300.0, d.ms())
	assert_eq(2, game.bot_height(), "a new finger should drop the next slab")


func test_a_replay_plays_like_a_fresh_machine() -> void:
	# ReplayResetTest.everyMachineReplaysLikeAFreshOne, for the stacker.
	var fresh := StackerGame.new()
	var expected := ROUND_TEST.trace(self, fresh, 42, ROUND_TEST.replay_bot(fresh))
	var reused := StackerGame.new()
	ROUND_TEST.trace(self, reused, 7, ROUND_TEST.replay_bot(reused))
	assert_eq(expected, ROUND_TEST.trace(self, reused, 42, ROUND_TEST.replay_bot(reused)))
