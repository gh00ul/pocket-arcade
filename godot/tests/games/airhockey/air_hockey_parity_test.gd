extends PaTest
## Godot-only: air hockey against numbers build-13 printed (scratch runs of its classes). The serve,
## the CPU's reading of the table, the touch-to-table mapping and the player's mallet chase agree to
## a thousandth of a unit. (A played round then parts ways at the first serve: build-13 counts the
## serve delay down in Floats and the puck goes live one step later than in 64-bit floats, so the
## first contact differs; the simulation statistics test covers the rest.)


## The CPU alone (nobody touches the table), round seed 42: puck and CPU mallet every 30 steps.
func test_the_cpu_alone_follows_build_13() -> void:
	var g := AirHockeyGame.new()
	var d := F32RoundDriver.new(g, 42)
	var samples := {}
	d.play(12.0, func(_t: float, _ms: int) -> void:
		if d.steps % 30 == 0:
			samples[d.steps] = [g.bot_puck_x(), g.bot_puck_y(), g.bot_cpu_x(), g.bot_cpu_y()])
	# build-13: the serve puts the puck at 163.579 (the round's first random draw), 110 towards the
	# player; the CPU goes home, then defends once the puck is live.
	var expected := {0: [163.579, 440.0, 180.0, 120.0], 30: [163.579, 440.0, 180.0, 110.0], 60: [163.579, 440.0, 180.0, 110.0]}
	for s in range(90, 1440, 30):
		expected[s] = [163.579, 440.0, 172.306, 118.889]
	assert_eq(expected.size(), samples.size())
	for s: int in expected:
		var want: Array = expected[s]
		var got: Array = samples.get(s, [NAN, NAN, NAN, NAN])
		for k in 4:
			assert_near(want[k], got[k], 1e-3, "step %d value %d" % [s, k])
	assert_eq(Vector2i(0, 0), g.bot_goals())


## Where table points land on the screen (the bots and the touch mapping use it).
func test_the_table_maps_to_the_screen_like_build_13() -> void:
	var g := AirHockeyGame.new()
	g.fixed_seed = 1
	g.start(SimHarness.fx())
	var a := g.bot_screen(180.0, 540.0)
	assert_near(180.0, a.x, 1e-3)
	assert_near(508.7672, a.y, 1e-3)
	var b := g.bot_screen(70.0, 100.0)
	assert_near(93.6964, b.x, 1e-3)
	assert_near(184.8620, b.y, 1e-3)


## games/GameSimulationTest.kt `hockey` with its Float shot clock.
static func f32_bot(game: AirHockeyGame, rng: KRandom, lag: float, noise: float) -> Callable:
	var st := [0.0, false, 180.0, 540.0]  # next, down, tx, ty
	return func(t: float, ms: int) -> void:
		if t >= st[0]:
			st[0] = F32RoundDriver.f32(t + F32RoundDriver.f32(lag))
			var px := game.bot_puck_x()
			var py := game.bot_puck_y()
			if py > 340.0:
				var aim_x := 140.0 if game.bot_cpu_x() > 180.0 else 220.0
				var gx := aim_x - px
				var gy := 60.0 - py
				var gl := maxf(sqrt(gx * gx + gy * gy), 1.0)
				var behind := -8.0 if game.bot_mallet_y() > py + 8.0 else 40.0
				st[2] = px - gx / gl * behind + F32RoundDriver.gaussian(rng) * noise
				st[3] = py - gy / gl * behind + F32RoundDriver.gaussian(rng) * noise
			else:
				st[2] = 180.0 + (px - 180.0) * 0.5
				st[3] = 550.0
		var s := game.bot_screen(st[2], st[3])
		if not st[1]:
			game.on_touch(TouchType.DOWN, 1, s.x, s.y, ms)
			st[1] = true
		else:
			game.on_touch(TouchType.MOVE, 1, s.x, s.y, ms)


## The good bot's first 0.6 s of round seed 121: the mallet chases its finger exactly as in build-13.
func test_the_mallet_chases_the_finger_like_build_13() -> void:
	var g := AirHockeyGame.new()
	var d := F32RoundDriver.new(g, 121)
	var bot := f32_bot(g, KRandom.new(11), 0.05, 3.0)
	var me := {}
	d.play_steps(72, func(t: float, ms: int) -> void:
		bot.call(t, ms)
		me[d.steps] = Vector2(g.bot_mallet_x(), g.bot_mallet_y())
		assert_near(168.5893, g.bot_puck_x(), 1e-3, "the puck waits for the serve")
		assert_near(440.0, g.bot_puck_y(), 1e-3))
	# build-13: the mallet on the steps it moved, after the bot's re-reads every 0.05 s.
	var expected := {
		55: Vector2(171.7491, 436.0131), 59: Vector2(168.8412, 454.1143), 60: Vector2(165.9332, 472.2155),
		61: Vector2(164.7521, 479.5674), 64: Vector2(164.7521, 479.5674), 65: Vector2(164.7287, 461.2340),
		66: Vector2(164.7052, 442.9007), 67: Vector2(164.6968, 436.3653), 71: Vector2(164.6968, 436.3653),
	}
	for s: int in expected:
		assert_true(me.has(s), "step %d played" % s)
		var got: Vector2 = me.get(s, Vector2(NAN, NAN))
		assert_near(expected[s].x, got.x, 1e-3, "step %d x" % s)
		assert_near(expected[s].y, got.y, 1e-3, "step %d y" % s)
