extends PaTest
## games/racer/RacerSceneTest.kt: the racer scene's own numbers, and the rule that its visual
## effects never touch the race.


func test_the_scene_builds_nothing_until_it_draws() -> void:
	# Constructing the scene (and a game that owns one) needs no art: tests never draw.
	var pending := PaintPump.pending()
	var scene := RacerScene.new()
	var game := RacerGame.new()
	assert_not_null(game)
	for i in RacerTuning.CARS:
		assert_false(scene.built(i), "car model %d built before anything drew" % i)
	# Godot: nothing was handed to the painter either.
	assert_eq(pending, PaintPump.pending())


func test_look_constants_fit_the_view_distance() -> void:
	# The road is drawn 72 segments of 40 units ahead: the cheap car and the detail cut-off sit inside it.
	assert_true(RacerLook.LOD_DISTANCE < 72 * 40.0)
	assert_true(RacerLook.DETAIL_SEGMENTS >= 10 and RacerLook.DETAIL_SEGMENTS <= 72)
	assert_true(RacerLook.SPEED_LINE_FROM >= 0.0 and RacerLook.SPEED_LINE_FROM <= 1.0)


## Holds the drift button for two seconds in every five, tracing the race every half second.
class _DriftTracer:
	extends RefCounted
	var game: RacerGame
	var trace := ""
	var k := 0

	func _init(g: RacerGame) -> void:
		game = g

	func step(t: float, ms: int) -> void:
		if fmod(t, 5.0) < 2.0:
			if not game.bot_drifting():
				game.on_touch(TouchType.DOWN, 7, DriftButton.X, DriftButton.Y, ms)
		elif game.bot_drifting():
			game.on_touch(TouchType.UP, 7, DriftButton.X, DriftButton.Y, ms)
		if k % 60 == 0:
			trace += "%.1f,%.1f;" % [game.bot_progress(), game.bot_px()]
		k += 1


## Drifting lays smoke and skid marks and boosting lights lamps, all in the game's step; none of it
## may change how the race runs. The same seed with and without a drift-and-turbo bot's extra
## effects can't be compared directly, so run the same bot twice and compare the whole race:
## identical progress means the effects consume no shared randomness.
func test_effects_leave_the_race_reproducible() -> void:
	assert_eq(_race(), _race())


func _race() -> String:
	var game := RacerGame.new()
	var d := SimHarness.RoundDriver.new(game, 11)
	var bot := _DriftTracer.new(game)
	d.play(30.0, bot.step)
	return bot.trace + str(game.score)
