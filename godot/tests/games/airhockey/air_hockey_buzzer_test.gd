extends PaTest
## games/airhockey/AirHockeyBuzzerTest.kt: once the clock runs out nothing new may score: the puck
## can still be sliding (and bounce off the player's mallet) through the host's ENDING tail, and the
## host reads the score after that.


func _started() -> AirHockeyGame:
	var g := AirHockeyGame.new()
	g.fixed_seed = 11
	g.start(SimHarness.fx())
	return g


## Steps [param seconds] of the host's clock at [param time_left] (0 = the ENDING tail).
func _run(g: AirHockeyGame, seconds: float, time_left: float) -> void:
	var t := 0.0
	while t < seconds:
		g.update(GameLoop.FIXED_DT, time_left)
		t += GameLoop.FIXED_DT


## A fast puck just short of the CPU's goal mouth, off to one side of its mallet.
func _shoot_at_cpu_goal(g: AirHockeyGame) -> void:
	g.bot_place_puck(230.0, 85.0, 0.0, -1300.0)


func test_a_shot_before_the_buzzer_scores() -> void:
	# Sanity check for the test below: the same shot scores while the clock is running.
	var g := _started()
	_run(g, 0.1, 30.0)
	_shoot_at_cpu_goal(g)
	_run(g, 0.3, 30.0)
	assert_eq(Vector2i(1, 0), g.bot_goals())
	assert_true(g.score > 0)


func test_no_goal_scores_after_the_buzzer() -> void:
	var g := _started()
	_run(g, 0.1, 30.0)
	_run(g, GameLoop.FIXED_DT, 0.0)
	_shoot_at_cpu_goal(g)
	_run(g, SimHarness.ENDING_SECONDS, 0.0)
	assert_eq(Vector2i(0, 0), g.bot_goals())
	assert_eq(0, g.score)
	assert_true(g.finished(), "the round must still end promptly")

	# Same for a puck heading into the player's own goal.
	g.bot_place_puck(180.0, 560.0, 0.0, 1300.0)
	_run(g, SimHarness.ENDING_SECONDS, 0.0)
	assert_eq(Vector2i(0, 0), g.bot_goals())
	assert_true(g.finished())


func test_the_buzzer_freezes_the_players_mallet() -> void:
	var g := _started()
	_run(g, 0.1, 30.0)
	# Grab the mallet and fling the finger across the table, then the clock runs out at once.
	var f := g.bot_screen(300.0, 400.0)
	g.on_touch(TouchType.DOWN, 1, f.x, f.y, 100)
	var x0 := g.bot_mallet_x()
	var y0 := g.bot_mallet_y()
	_run(g, SimHarness.ENDING_SECONDS, 0.0)
	assert_near(x0, g.bot_mallet_x(), 0.01)
	assert_near(y0, g.bot_mallet_y(), 0.01)
