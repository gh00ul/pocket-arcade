extends PaTest
## games/skeeball/SkeeBallTest.kt: regression tests for the skee-ball round hang: a roll that
## friction stopped just short of the jump ramp stayed in play forever, so the round never
## finished and the host sat at 0:00.

const REST_X := 180.0
const REST_Y := 586.0
## A roll must be over (scored or guttered) within this long; the slowest takes about 5.5 s.
const MAX_ROLL_SECONDS := 7.0
## Launch speeds that used to stall from the rest spot (the ramp dead band).
const OLD_BAD_LO := 178.25
const OLD_BAD_HI := 179.55


func _started_game() -> SkeeBallGame:
	var g := SkeeBallGame.new()
	g.fixed_seed = 1
	g.start(SimHarness.fx())
	return g


## Launches a batch of balls at [param speeds] (up to six, the rack's size) from (x, y) at
## [param angle_deg], steps until every one is out of play and returns how long that took. The
## clock is held away from zero so nothing but the balls ends the waiting.
func _roll_batch(game: SkeeBallGame, x: float, y: float, angle_deg: float, speeds: PackedFloat64Array, n: int) -> float:
	var angle := angle_deg * (PI / 180.0)
	for i in n:
		assert_true(game.bot_launch(x, y, speeds[i], angle))
	var t := 0.0
	while game.bot_balls_active() > 0 and t < SkeeTuning.BALL_TIMEOUT + 2.0:
		game.update(GameLoop.FIXED_DT, 30.0)
		t += GameLoop.FIXED_DT
	return t


## Sweeps launch speed from [param lo] to [param hi] in [param step_v]s from one spot and angle.
func _sweep(game: SkeeBallGame, x: float, y: float, angle_deg: float, lo: float, hi: float, step_v: float) -> int:
	var speeds := PackedFloat64Array()
	speeds.resize(6)
	var n := 0
	var k := 0
	var rolled := 0
	var worst := 0.0
	while true:
		var v := lo + k * step_v
		if v > hi + 1e-3:
			break
		speeds[n] = v
		n += 1
		k += 1
		if n == speeds.size():
			worst = maxf(worst, _roll_batch(game, x, y, angle_deg, speeds, n))
			rolled += n
			n = 0
	if n > 0:
		worst = maxf(worst, _roll_batch(game, x, y, angle_deg, speeds, n))
		rolled += n
	assert_true(worst <= MAX_ROLL_SECONDS, "a roll from (%s, %s) at %s° took %ss" % [x, y, angle_deg, worst])
	assert_eq(0, game.bot_failsafe_trips(), "the failsafe had to retire a roll from (%s, %s) at %s°" % [x, y, angle_deg])
	return rolled


func test_old_dead_band_from_the_rest_spot_always_finishes() -> void:
	var game := _started_game()
	var max_a := SkeeTuning.MAX_ANGLE_DEG
	for a in [0.0, -max_a, max_a]:
		_sweep(game, REST_X, REST_Y, a, OLD_BAD_LO - 2.0, OLD_BAD_HI + 2.0, 0.01)


func test_every_roll_finishes_from_anywhere_in_the_grab_area() -> void:
	var game := _started_game()
	# The sweep only checks the balls: particles and popups are pure presentation (their randomness
	# is their own, never the game's), and each burst costs GDScript hundreds of microseconds.
	game.particles = QuietParticles.new(1)
	game.popups = QuietPopups.new(1)
	var max_a := SkeeTuning.MAX_ANGLE_DEG
	var full := OS.get_cmdline_user_args().has("--slow")
	# Kotlin's 0.25 steps (256,100 rolls) take GDScript minutes: the default run rolls every fourth
	# of those speeds (64,100 rolls); `tools/run_tests.sh --slow` rolls them all.
	var step_v := 0.25 if full else 1.0
	var rolled := 0
	# Across the lane (ball centre limits) and along the grab area (the drag limits).
	for x in [74.0, 130.0, 180.0, 236.0, 286.0]:
		for y in [490.0, 540.0, 586.0, 610.0]:
			for a in [-max_a, -12.0, 0.0, 12.0, max_a]:
				rolled += _sweep(game, x, y, a, SkeeTuning.MIN_SPEED, SkeeTuning.MAX_SPEED, step_v)
	assert_eq(256100 if full else 64100, rolled, "every speed of every sweep was rolled")
	print("skee-ball sweep (step %s): %d rolls, all finished, failsafe trips %d" % [step_v, rolled, game.bot_failsafe_trips()])


## Particles that never spawn (see test_every_roll_finishes_from_anywhere_in_the_grab_area).
class QuietParticles:
	extends Particles

	func spawn(_px: float, _py: float, _pvx: float, _pvy: float, _lifetime: float, _sz: float, _argb: int,
			_grav: float = 0.0, _drag_per_sec: float = 0.0, _kind: int = SQUARE) -> void:
		pass

	func burst(_px: float, _py: float, _n: int, _speed_min: float, _speed_max: float, _colors: Array,
			_lifetime: float = 0.6, _sz: float = 3.0, _grav: float = 0.0, _drag_per_sec: float = 2.0, _kind: int = SQUARE,
			_angle_from: float = 0.0, _angle_to: float = TAU) -> void:
		pass

	func confetti(_left: float, _top: float, _width: float, _n: int, _sz: float = 4.0) -> void:
		pass


## Popups that never show.
class QuietPopups:
	extends FloatingTexts

	func add(_text: String, _x: float, _y: float, _color: Variant, _size: float = 2.0, _life: float = 0.9, _rise: float = 60.0) -> void:
		pass

	func update(_dt: float) -> void:
		pass


func test_failsafe_retires_a_ball_the_physics_cannot_finish() -> void:
	var game := _started_game()
	# A ball whose physics went bad never lands, rolls back or reaches the gutter by itself.
	assert_true(game.bot_launch(REST_X, REST_Y, NAN, 0.0))
	var t := 0.0
	while game.bot_balls_active() > 0 and t < 30.0:
		game.update(GameLoop.FIXED_DT, 30.0)
		t += GameLoop.FIXED_DT
	assert_eq(0, game.bot_balls_active(), "the stuck ball is still in play")
	assert_true(t <= SkeeTuning.BALL_TIMEOUT + 0.5, "retired after %ss" % t)
	assert_eq(1, game.bot_failsafe_trips())
	assert_eq(1, game.bot_gutters())


func test_a_gutter_ball_gutters_once() -> void:
	var game := _started_game()
	# Too slow to reach the ramp: it rolls back into the gutter.
	_roll_batch(game, REST_X, REST_Y, 0.0, PackedFloat64Array([150.0]), 1)
	assert_eq(1, game.bot_gutters())


func test_round_ends_after_a_gentle_flick_at_the_old_stuck_speed() -> void:
	# A flick with no MOVE samples leaves the ball on its rest spot, and its speed times
	# FLICK_TO_SPEED lands inside the old dead band. Flicked just before time runs out.
	var game := SkeeBallGame.new()
	var stats := SimHarness.Stats.new("gentle skee")
	var seed_v := 100
	for flick_speed in [540.5, 541.5, 542.5, 543.5]:
		var ball_speed: float = flick_speed * SkeeTuning.FLICK_TO_SPEED
		assert_true(ball_speed >= OLD_BAD_LO and ball_speed <= OLD_BAD_HI, "ball speed %s" % ball_speed)
		var flicked := [false]
		SimHarness.play_round(self, game, seed_v, stats, func(t: float, ms: int) -> void:
			if not flicked[0] and t >= game.round_seconds - 2.0:
				flicked[0] = true
				SimHarness.flick_no_move(game, 1, REST_X, 600.0, 0.0, -flick_speed, ms))
		seed_v += 1
		assert_true(flicked[0] and game.bot_gutters() == 1, "the flick never rolled")
		assert_eq(0, game.bot_failsafe_trips())
