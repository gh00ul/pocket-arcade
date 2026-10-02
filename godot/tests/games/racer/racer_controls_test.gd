extends PaTest
## games/racer/RacerControlsTest.kt: the racer's controls: the on-screen DRIFT button, the
## second-finger drift, and tilt steering.

const DEG := PI / 180.0


## A racer alone on the road (so nothing shoves it), started and driving for a moment: [game, driver].
func _racing(tilt: bool = false) -> Array:
	var game := RacerGame.new()
	game.solo_for_tests = true
	game.tilt_steering = tilt
	var d := SimHarness.RoundDriver.new(game, 3)
	d.play(0.3)
	return [game, d]


# ------------------------------------------------------------------ the DRIFT button

func test_the_drift_button_sits_low_right_in_the_field_clear_of_the_gauges() -> void:
	var r := DriftButton.HIT_R
	assert_true(DriftButton.X > MiniGame.GAME_W / 2.0, "right of centre")
	assert_true(DriftButton.X + r <= MiniGame.GAME_W, "inside the field's right edge")
	assert_true(DriftButton.Y - r > MiniGame.GAME_H / 2.0, "in the lower half")
	assert_true(DriftButton.Y + r < 592.0, "above the speed and drift gauges")
	assert_true(DriftButton.R * 2.0 >= 44.0, "thumb-sized (at least 44 field units across)")
	assert_true(DriftButton.HIT_R > DriftButton.R, "the touch reaches past the disc")


func test_the_drift_button_hit_test_is_a_circle() -> void:
	var x := DriftButton.X
	var y := DriftButton.Y
	var r := DriftButton.HIT_R
	assert_true(DriftButton.hit(x, y))
	assert_true(DriftButton.hit(x + r - 0.5, y))
	assert_true(DriftButton.hit(x, y - r + 0.5))
	assert_false(DriftButton.hit(x + r + 0.5, y))
	assert_false(DriftButton.hit(x, y + r + 0.5))
	# The corner of the bounding square is outside the circle.
	assert_false(DriftButton.hit(x + r * 0.9, y + r * 0.9))
	assert_false(DriftButton.hit(60.0, 300.0))
	assert_false(DriftButton.hit(180.0, 450.0))


func test_holding_the_button_drifts_even_as_the_first_finger_and_letting_go_ends() -> void:
	var gd := _racing()
	var game: RacerGame = gd[0]
	var d: SimHarness.RoundDriver = gd[1]
	assert_false(game.bot_drifting())
	game.on_touch(TouchType.DOWN, 1, DriftButton.X, DriftButton.Y, d.ms())
	assert_true(game.bot_drifting(), "the button should drift")
	d.play(0.2)
	assert_true(game.bot_drifting())
	game.on_touch(TouchType.UP, 1, DriftButton.X, DriftButton.Y, d.ms())
	assert_false(game.bot_drifting(), "letting go should end the drift")


func test_the_button_and_a_steering_thumb_work_together() -> void:
	var gd := _racing()
	var game: RacerGame = gd[0]
	var d: SimHarness.RoundDriver = gd[1]
	# The right thumb holds DRIFT, then the left steers.
	game.on_touch(TouchType.DOWN, 1, DriftButton.X, DriftButton.Y, d.ms())
	game.on_touch(TouchType.DOWN, 2, 100.0, 450.0, d.ms())
	var t0 := game.bot_steer_target()
	game.on_touch(TouchType.MOVE, 2, 130.0, 450.0, d.ms() + 10)
	assert_near(t0 + 30.0 * RacerTuning.STEER_GAIN, game.bot_steer_target(), 0.01, "the steering finger moves the target")
	assert_true(game.bot_drifting(), "still drifting while steering")
	# Moving the drift finger doesn't steer.
	var t1 := game.bot_steer_target()
	game.on_touch(TouchType.MOVE, 1, DriftButton.X - 20.0, DriftButton.Y, d.ms() + 20)
	assert_near(t1, game.bot_steer_target(), 0.001)
	game.on_touch(TouchType.UP, 2, 130.0, 450.0, d.ms() + 30)
	assert_true(game.bot_drifting(), "the steering finger lifting leaves the drift")
	game.on_touch(TouchType.UP, 1, DriftButton.X, DriftButton.Y, d.ms() + 40)
	assert_false(game.bot_drifting())


func test_a_finger_sliding_off_the_button_keeps_the_drift() -> void:
	var gd := _racing()
	var game: RacerGame = gd[0]
	var d: SimHarness.RoundDriver = gd[1]
	game.on_touch(TouchType.DOWN, 1, DriftButton.X, DriftButton.Y, d.ms())
	game.on_touch(TouchType.MOVE, 1, 100.0, 300.0, d.ms() + 10)
	assert_true(game.bot_drifting(), "held until the finger lifts")
	game.on_touch(TouchType.UP, 1, 100.0, 300.0, d.ms() + 20)
	assert_false(game.bot_drifting())


func test_a_steering_finger_landing_on_the_button_while_drifting_still_steers() -> void:
	var gd := _racing()
	var game: RacerGame = gd[0]
	var d: SimHarness.RoundDriver = gd[1]
	# Drift is held by a finger that landed elsewhere (a second finger anywhere still drifts).
	game.on_touch(TouchType.DOWN, 1, 180.0, 450.0, d.ms())
	game.on_touch(TouchType.DOWN, 2, 60.0, 300.0, d.ms())
	assert_true(game.bot_drifting())
	# Steering is the first finger; lifting it and putting it down on the button steers, as the button is busy.
	game.on_touch(TouchType.UP, 1, 180.0, 450.0, d.ms())
	game.on_touch(TouchType.DOWN, 3, DriftButton.X, DriftButton.Y, d.ms())
	var t0 := game.bot_steer_target()
	game.on_touch(TouchType.MOVE, 3, DriftButton.X - 40.0, DriftButton.Y, d.ms() + 10)
	assert_near(t0 - 40.0 * RacerTuning.STEER_GAIN, game.bot_steer_target(), 0.01)


func test_a_second_finger_anywhere_still_drifts() -> void:
	var gd := _racing()
	var game: RacerGame = gd[0]
	var d: SimHarness.RoundDriver = gd[1]
	game.on_touch(TouchType.DOWN, 1, 180.0, 450.0, d.ms())
	assert_false(game.bot_drifting(), "the first finger steers")
	game.on_touch(TouchType.DOWN, 2, 40.0, 200.0, d.ms())
	assert_true(game.bot_drifting(), "the second drifts, wherever it lands")
	game.on_touch(TouchType.UP, 2, 40.0, 200.0, d.ms())
	assert_false(game.bot_drifting())


func test_a_repeated_down_on_a_held_finger_changes_nothing() -> void:
	var gd := _racing()
	var game: RacerGame = gd[0]
	var d: SimHarness.RoundDriver = gd[1]
	game.on_touch(TouchType.DOWN, 1, DriftButton.X, DriftButton.Y, d.ms())
	game.on_touch(TouchType.DOWN, 1, 180.0, 450.0, d.ms())
	var t0 := game.bot_steer_target()
	game.on_touch(TouchType.MOVE, 1, 200.0, 450.0, d.ms() + 10)
	assert_near(t0, game.bot_steer_target(), 0.001, "the drift finger never steers")
	assert_true(game.bot_drifting())


# ------------------------------------------------------------------ tilt steering

func test_tilt_does_nothing_unless_the_option_is_on() -> void:
	var gd := _racing(false)
	var game: RacerGame = gd[0]
	var d: SimHarness.RoundDriver = gd[1]
	game.on_tilt(0.0)
	d.play(0.1)
	game.on_tilt(25.0 * DEG)
	var t0 := game.bot_steer_target()
	d.play(0.5)
	assert_false(game.bot_tilt_live())
	assert_near(t0, game.bot_steer_target(), 0.5)


func test_leaning_right_or_left_slides_the_steering_target_that_way() -> void:
	for side: int in [1, -1]:
		var gd := _racing(true)
		var game: RacerGame = gd[0]
		var d: SimHarness.RoundDriver = gd[1]
		game.on_tilt(0.0)
		d.play(0.05)
		game.on_tilt(0.0)
		var t0 := game.bot_steer_target()
		game.on_tilt(side * 20.0 * DEG)
		d.play(0.5)
		assert_true(game.bot_tilt_live())
		var moved := (game.bot_steer_target() - t0) * side
		# 20 degrees is full lock: about TILT_RATE road units a second, less the small dead zone and curve.
		assert_true(moved >= 60.0 and moved <= RacerTuning.TILT_RATE * 0.5 + 1.0, "moved %f to the %s" % [moved, "right" if side > 0 else "left"])
		# Levelling off stops it.
		game.on_tilt(0.0)
		var there := game.bot_steer_target()
		d.play(0.3)
		assert_near(there, game.bot_steer_target(), 1.0)


func test_the_level_is_where_the_phone_was_at_the_start_of_the_race() -> void:
	var gd := _racing(true)
	var game: RacerGame = gd[0]
	var d: SimHarness.RoundDriver = gd[1]
	# Held 15 degrees over to the right as the race starts: that's straight ahead.
	game.on_tilt(15.0 * DEG)
	d.play(0.05)
	game.on_tilt(15.0 * DEG)
	var t0 := game.bot_steer_target()
	d.play(0.4)
	assert_near(t0, game.bot_steer_target(), 0.5, "no steering from a steady hold")
	assert_near(0.0, game.bot_tilt_input(), 0.0)
	# ...and a lean from there steers, from there.
	game.on_tilt(35.0 * DEG)
	assert_true(game.bot_tilt_input() > 0.9, str(game.bot_tilt_input()))
	game.on_tilt(-5.0 * DEG)
	assert_true(game.bot_tilt_input() < -0.9, str(game.bot_tilt_input()))


func test_a_pause_recalibrates_the_level() -> void:
	var gd := _racing(true)
	var game: RacerGame = gd[0]
	var d: SimHarness.RoundDriver = gd[1]
	game.on_tilt(0.0)
	d.play(0.05)
	game.on_tilt(0.0)
	game.on_tilt(10.0 * DEG)
	assert_true(game.bot_tilt_input() > 0.2)
	d.pause()
	# Picked up again held differently: the first reading after the pause is level.
	game.on_tilt(-20.0 * DEG)
	assert_near(0.0, game.bot_tilt_input(), 0.0)
	game.on_tilt(-20.0 * DEG + 10.0 * DEG)
	assert_true(game.bot_tilt_input() > 0.2)


func test_under_tilt_any_finger_drifts_and_dragging_steers_nothing() -> void:
	var gd := _racing(true)
	var game: RacerGame = gd[0]
	var d: SimHarness.RoundDriver = gd[1]
	game.on_tilt(0.0)
	d.play(0.05)
	game.on_tilt(0.0)
	game.on_touch(TouchType.DOWN, 1, 180.0, 450.0, d.ms())
	assert_true(game.bot_drifting(), "a finger anywhere drifts under tilt")
	var t0 := game.bot_steer_target()
	game.on_touch(TouchType.MOVE, 1, 300.0, 450.0, d.ms() + 10)
	assert_near(t0, game.bot_steer_target(), 0.001)
	game.on_touch(TouchType.UP, 1, 300.0, 450.0, d.ms() + 20)
	assert_false(game.bot_drifting())


func test_without_a_sensor_the_option_leaves_touch_steering_alone() -> void:
	var gd := _racing(true)
	var game: RacerGame = gd[0]
	var d: SimHarness.RoundDriver = gd[1]
	# No reading has ever arrived (no sensor): dragging still steers, a second finger still drifts.
	assert_false(game.bot_tilt_live())
	game.on_touch(TouchType.DOWN, 1, 180.0, 450.0, d.ms())
	assert_false(game.bot_drifting())
	var t0 := game.bot_steer_target()
	game.on_touch(TouchType.MOVE, 1, 200.0, 450.0, d.ms() + 10)
	assert_near(t0 + 20.0 * RacerTuning.STEER_GAIN, game.bot_steer_target(), 0.01)


func test_turning_tilt_off_gives_touch_steering_back() -> void:
	var gd := _racing(true)
	var game: RacerGame = gd[0]
	var d: SimHarness.RoundDriver = gd[1]
	game.on_tilt(0.0)
	assert_true(game.bot_tilt_live())
	game.tilt_steering = false
	assert_false(game.bot_tilt_live())
	game.on_touch(TouchType.DOWN, 1, 180.0, 450.0, d.ms())
	assert_false(game.bot_drifting(), "the first finger steers again")
	# Old readings are forgotten.
	assert_near(0.0, game.bot_tilt_input(), 0.0)
