extends PaTest
## games/pinball/PinballRulesTest.kt: rule-level tests for Star Flipper: physics that never tunnels,
## the controls, the rules and the failsafes.

const T := preload("res://scripts/games/pinball/pinball_table.gd")
const K := preload("res://scripts/games/pinball/pinball_tuning.gd")
const Bots := preload("res://tests/games/pinball/pinball_bots.gd")

const LEFT_X := 70.0
const RIGHT_X := 230.0
const FLIP_Y := 560.0
const PLUNGE_X := 320.0
const PLUNGE_Y := 520.0
## Plenty of clock left, so only the table decides what happens.
const CLOCK := 30.0
const DT := GameLoop.FIXED_DT

## Kotlin's test loops add FIXED_DT (a 32-bit 1/120) to a 32-bit clock: how many steps fit in a time
## depends on that rounding, so the loops here count time the same way.
static var _f32 := PackedFloat32Array([0.0])


static func _tick(t: float) -> float:
	_f32[0] = DT
	var dt32 := _f32[0]
	_f32[0] = t + dt32
	return _f32[0]


func _started_game(seed_value: int = 1) -> PinballGame:
	var g := PinballGame.new()
	g.fixed_seed = seed_value
	g.start(SimHarness.fx())
	return g


## A started game with no ball anywhere, so a test can place exactly the balls it wants.
func _empty_table(seed_value: int = 1) -> PinballGame:
	var g := _started_game(seed_value)
	for i in g.bot_max_balls():
		g.bot_remove(i)
	return g


func _steps(game: PinballGame, seconds: float, clock: float = CLOCK) -> void:
	var t := 0.0
	while t < seconds:
		game.update(DT, clock)
		t = _tick(t)


static func _rad(deg: float) -> float:
	return deg * PI / 180.0


# ---------------------------------------------------------------- tunnelling

## Fires ball 0 from (x, y) at [param speed] along [param angles] (degrees, y down) at the wall from
## (ax, ay) to (bx, by) and checks after every step that it never gets to the far side of the wall
## anywhere along its length, nor sinks deep into it.
func _fire_at_wall(name: String, x: float, y: float, angles: PackedFloat64Array, speed: float,
		ax: float, ay: float, bx: float, by: float, seconds: float = 0.25) -> int:
	var shots := 0
	for deg in angles:
		var game := _empty_table()
		var a := _rad(deg)
		game.bot_place(0, x, y, cos(a) * speed, sin(a) * speed)
		var ex := bx - ax
		var ey := by - ay
		var length := sqrt(ex * ex + ey * ey)
		var side0 := _side_of(x, y, ax, ay, ex, ey)
		var t := 0.0
		while t < seconds and game.bot_ball_in_play(0):
			game.update(DT, CLOCK)
			t = _tick(t)
			var px := game.bot_ball_x(0)
			var py := game.bot_ball_y(0)
			var along := ((px - ax) * ex + (py - ay) * ey) / (length * length)
			if along < 0.04 or along > 0.96:
				continue
			var dist := _side_of(px, py, ax, ay, ex, ey) / length
			if dist * side0 < 0.0:
				fail("%s: a %d u/s ball at %s° went through the wall (at %s, %s)" % [name, int(speed), deg, px, py])
				return shots
			if absf(dist) < T.BALL_R * 0.4:
				fail("%s: a %d u/s ball at %s° sank into the wall (at %s, %s)" % [name, int(speed), deg, px, py])
				return shots
		shots += 1
	return shots


static func _side_of(x: float, y: float, ax: float, ay: float, ex: float, ey: float) -> float:
	return (x - ax) * ey - (y - ay) * ex


static func _sweep(from: float, to: float, step: float) -> PackedFloat64Array:
	var n := int((to - from) / step) + 1
	var out := PackedFloat64Array()
	out.resize(n)
	for i in n:
		out[i] = from + i * step
	return out


func test_the_fastest_ball_never_tunnels_through_a_wall() -> void:
	var speeds := [K.MAX_SPEED, K.MAX_SPEED * 0.7, K.MAX_SPEED * 0.4]
	var shots := 0
	for s: float in speeds:
		# The thin orbit wall, from the playfield and from inside the orbit.
		shots += _fire_at_wall("orbit wall from the playfield", 72.0, 225.0, _sweep(125.0, 235.0, 5.0), s, T.ORBIT_X, T.ORBIT_Y0, T.ORBIT_X, T.ORBIT_Y1)
		shots += _fire_at_wall("orbit wall from the orbit", 20.0, 225.0, _sweep(-55.0, 55.0, 5.0), s, T.ORBIT_X, T.ORBIT_Y0, T.ORBIT_X, T.ORBIT_Y1)
		# The shooter lane wall, both ways.
		shots += _fire_at_wall("lane wall from the playfield", 240.0, 380.0, _sweep(-60.0, 60.0, 5.0), s, T.PLAY_W, 200.0, T.PLAY_W, 560.0)
		shots += _fire_at_wall("lane wall from the lane", T.LANE_X, 380.0, _sweep(125.0, 235.0, 5.0), s, T.PLAY_W, 200.0, T.PLAY_W, 560.0)
		# The outer walls and the top.
		shots += _fire_at_wall("left wall", 20.0, 225.0, _sweep(125.0, 235.0, 5.0), s, 0.0, 130.0, 0.0, 320.0)
		shots += _fire_at_wall("top wall", T.CX, 110.0, _sweep(-150.0, -30.0, 5.0), s, 70.0, 0.0, 230.0, 0.0)
		# A slingshot's kicking face and the inlane guide behind it.
		shots += _fire_at_wall("left sling", 110.0, 400.0, _sweep(120.0, 170.0, 5.0), s, T.SLING_CX, T.SLING_CY, T.SLING_AX, T.SLING_AY)
		shots += _fire_at_wall("right sling", T.PLAY_W - 110.0, 400.0, _sweep(10.0, 60.0, 5.0), s, T.PLAY_W - T.SLING_CX, T.SLING_CY, T.PLAY_W - T.SLING_AX, T.SLING_AY)
		shots += _fire_at_wall("inlane guide", 36.0, 420.0, _sweep(150.0, 210.0, 5.0), s, 22.0, 398.0, 22.0, 466.0)
		# The shooter lane's one-way gate, from above.
		shots += _fire_at_wall("lane gate", 290.0, 100.0, _sweep(60.0, 120.0, 5.0), s, T.W, T.GATE_Y0, T.PLAY_W, T.LANE_TOP_Y)
	print("wall sweep: %d shots, none tunnelled" % shots)


## Whether a ball at (x, y) would start overlapping some wall or post of the table.
static func _overlaps_table(x: float, y: float) -> bool:
	var r := T.BALL_R + 1.0
	for i in T.seg_count:
		var ex := T.bx[i] - T.ax[i]
		var ey := T.by[i] - T.ay[i]
		var t := clampf(((x - T.ax[i]) * ex + (y - T.ay[i]) * ey) / (ex * ex + ey * ey), 0.0, 1.0)
		var dx := x - (T.ax[i] + ex * t)
		var dy := y - (T.ay[i] + ey * t)
		if dx * dx + dy * dy < r * r:
			return true
	for i in T.post_count:
		var dx := x - T.px[i]
		var dy := y - T.py[i]
		if dx * dx + dy * dy < (r + T.pr[i]) * (r + T.pr[i]):
			return true
	return false


static func _side(ax: float, ay: float, bx: float, by: float, cx: float, cy: float) -> float:
	return (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)


## Whether segments p0-p1 and q0-q1 cross.
static func _crosses(p0x: float, p0y: float, p1x: float, p1y: float, q0x: float, q0y: float, q1x: float, q1y: float) -> bool:
	var d1 := _side(q0x, q0y, q1x, q1y, p0x, p0y)
	var d2 := _side(q0x, q0y, q1x, q1y, p1x, p1y)
	var d3 := _side(p0x, p0y, p1x, p1y, q0x, q0y)
	var d4 := _side(p0x, p0y, p1x, p1y, q1x, q1y)
	return d1 * d2 < 0.0 and d3 * d4 < 0.0


## Drops ball 0 onto flipper [param side] from above at [param speed] along [param angles], with the
## flipper resting, held up, or swinging up as the ball arrives, and checks after every step that the
## ball's path never crossed the bat's centre line and it never sank halfway into it.
func _fire_at_flipper(side: int, mode: int, speed: float, angles: PackedFloat64Array) -> int:
	var shots := 0
	var px := T.flip_x(side)
	var py := T.FLIP_Y
	var length := T.FLIP_LEN
	for deg in angles:
		for along: float in [0.3, 0.55, 0.8, 0.95]:
			var game := _empty_table()
			if mode == 1:
				game.on_touch(TouchType.DOWN, 1, LEFT_X if side == 0 else RIGHT_X, FLIP_Y, 0)
				_steps(game, 0.2)
			var d := game.bot_flipper_angle(side)
			# Aim so the ball would cross the bat [along] of the way out, from 45 units away.
			var tx := px + cos(d) * length * along
			var ty := py + sin(d) * length * along
			var a := _rad(deg)
			var sx := tx - cos(a) * 45.0
			var sy := ty - sin(a) * 45.0
			if _overlaps_table(sx, sy):
				continue
			game.bot_place(0, sx, sy, cos(a) * speed, sin(a) * speed)
			if mode == 2:
				# Arrives mid-swing: pressed so the bat meets the ball on its way up.
				game.update(DT, CLOCK)
				game.on_touch(TouchType.DOWN, 1, LEFT_X if side == 0 else RIGHT_X, FLIP_Y, 0)
			var t := 0.0
			var last_x := game.bot_ball_x(0)
			var last_y := game.bot_ball_y(0)
			var last_ang := game.bot_flipper_angle(side)
			while t < 0.2 and game.bot_ball_in_play(0):
				game.update(DT, CLOCK)
				t = _tick(t)
				var ang := game.bot_flipper_angle(side)
				var bx := game.bot_ball_x(0)
				var by := game.bot_ball_y(0)
				# The ball's path this step against the bat's centre line, before and after it moved.
				for g: float in [last_ang, ang]:
					if _crosses(last_x, last_y, bx, by, px, py, px + cos(g) * length * 0.97, py + sin(g) * length * 0.97):
						fail("flipper %d mode %d: a %d u/s ball at %s° hitting %s passed through the bat (%s, %s → %s, %s)" % [side, mode, int(speed), deg, along, last_x, last_y, bx, by])
						return shots
				var ex := cos(ang) * length
				var ey := sin(ang) * length
				var u := ((bx - px) * ex + (by - py) * ey) / (length * length)
				if u >= 0.0 and u <= 1.0:
					var dx := bx - (px + ex * u)
					var dy := by - (py + ey * u)
					var reach := T.FLIP_R0 + (T.FLIP_R1 - T.FLIP_R0) * u + T.BALL_R
					if sqrt(dx * dx + dy * dy) < reach * 0.5:
						fail("flipper %d mode %d: a %d u/s ball at %s° hitting %s sank into the bat (at %s, %s)" % [side, mode, int(speed), deg, along, bx, by])
						return shots
				last_x = bx
				last_y = by
				last_ang = ang
			shots += 1
	return shots


func test_the_fastest_ball_never_tunnels_through_a_flipper() -> void:
	var shots := 0
	for s: float in [K.MAX_SPEED, K.MAX_SPEED * 0.6, 600.0]:
		for mode in 3:
			shots += _fire_at_flipper(0, mode, s, _sweep(60.0, 120.0, 5.0))
			shots += _fire_at_flipper(1, mode, s, _sweep(60.0, 120.0, 5.0))
	print("flipper sweep: %d shots, none tunnelled" % shots)
	assert_true(shots > 400, "%d shots" % shots)


# ---------------------------------------------------------------- controls

func test_both_flippers_hold_at_once_and_each_lets_go_with_its_own_finger() -> void:
	var game := _started_game()
	game.on_touch(TouchType.DOWN, 1, LEFT_X, FLIP_Y, 0)
	game.on_touch(TouchType.DOWN, 2, RIGHT_X, FLIP_Y, 5)
	_steps(game, 0.15)
	assert_true(game.bot_flipper_held(0))
	assert_true(game.bot_flipper_held(1))
	assert_near(T.up_angle(0), game.bot_flipper_angle(0), 1e-3)
	assert_near(T.up_angle(1), game.bot_flipper_angle(1), 1e-3)
	# Strangers' events change nothing.
	game.on_touch(TouchType.UP, 7, LEFT_X, FLIP_Y, 20)
	game.on_touch(TouchType.MOVE, 8, RIGHT_X, FLIP_Y, 20)
	assert_true(game.bot_flipper_held(0) and game.bot_flipper_held(1))
	# Lifting the left finger drops only the left flipper.
	game.on_touch(TouchType.UP, 1, LEFT_X, FLIP_Y, 30)
	_steps(game, 0.15)
	assert_false(game.bot_flipper_held(0))
	assert_true(game.bot_flipper_held(1))
	assert_near(T.rest_angle(0), game.bot_flipper_angle(0), 1e-3)
	assert_near(T.up_angle(1), game.bot_flipper_angle(1), 1e-3)
	game.on_touch(TouchType.UP, 2, RIGHT_X, FLIP_Y, 40)
	_steps(game, 0.15)
	assert_false(game.bot_flipper_held(1))
	assert_near(T.rest_angle(1), game.bot_flipper_angle(1), 1e-3)


func test_a_flipper_follows_the_finger_that_pressed_it_across_the_middle() -> void:
	var game := _started_game()
	game.on_touch(TouchType.DOWN, 1, LEFT_X, FLIP_Y, 0)
	game.on_touch(TouchType.MOVE, 1, 300.0, FLIP_Y, 40)
	assert_true(game.bot_flipper_held(0))
	assert_false(game.bot_flipper_held(1), "sliding across doesn't press the other flipper")
	# A second finger on the left while the first still holds it does nothing new.
	game.on_touch(TouchType.DOWN, 2, LEFT_X, FLIP_Y, 50)
	game.on_touch(TouchType.UP, 2, LEFT_X, FLIP_Y, 60)
	assert_true(game.bot_flipper_held(0))
	game.on_touch(TouchType.UP, 1, 300.0, FLIP_Y, 70)
	assert_false(game.bot_flipper_held(0))
	assert_false(game.bot_flipper_held(1))


func _plunge_with_pull(pull: float) -> PinballGame:
	var game := _started_game()
	assert_true(game.bot_lane_ready())
	game.on_touch(TouchType.DOWN, 3, PLUNGE_X, PLUNGE_Y, 0)
	assert_true(game.bot_plunger_held())
	game.on_touch(TouchType.MOVE, 3, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE * pull, 100)
	assert_near(minf(pull, 1.0), game.bot_pull(), 1e-4)
	game.on_touch(TouchType.UP, 3, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE * pull, 200)
	return game


func test_plunger_strength_scales_with_the_pull() -> void:
	var last := 0.0
	for pull: float in [0.2, 0.4, 0.6, 0.8, 1.0, 1.5]:
		var game := _plunge_with_pull(pull)
		var speed := game.bot_last_launch_speed()
		var want := K.LAUNCH_MIN + (K.LAUNCH_MAX - K.LAUNCH_MIN) * minf(pull, 1.0)
		assert_near(want, speed, 0.5)
		assert_true(speed >= last, "pull %s launched at %s, not faster than %s" % [pull, speed, last])
		last = speed
		assert_false(game.bot_lane_ready())
	# A tap on the plunger (no pull) doesn't fire the ball.
	var tapped := _plunge_with_pull(0.02)
	assert_eq(0, tapped.bot_launches())
	assert_true(tapped.bot_lane_ready())
	# A full plunge clears the gate and reaches the playfield.
	var full := _plunge_with_pull(1.0)
	_steps(full, 1.2)
	assert_true(full.bot_ball_in_play(0) and full.bot_ball_x(0) < T.PLAY_W)


## Steps until ball 0 is back on the plunger (or [param limit] seconds pass); true if it got there.
func _roll_back(game: PinballGame, limit: float, each: Callable = Callable()) -> bool:
	var t := 0.0
	while not game.bot_ball_in_lane(0) and t < limit:
		if each.is_valid():
			each.call()
		game.update(DT, CLOCK)
		t = _tick(t)
	return game.bot_ball_in_lane(0)


func test_a_weak_plunge_rolls_back_for_the_next_pull_not_a_free_auto_launch() -> void:
	var game := _plunge_with_pull(0.2)
	assert_eq(1, game.bot_launches())
	assert_true(_roll_back(game, 6.0), "a weak plunge should fall short of the gate and roll back")
	# Long enough for an automatic relaunch (AUTO_DELAY) to have happened if one were coming.
	_steps(game, 1.5)
	assert_eq(1, game.bot_launches(), "the machine must not relaunch a ball the player plunged too weakly")
	assert_true(game.bot_lane_ready(), "the ball waits on the plunger for the player again")
	# The player's second try goes.
	game.on_touch(TouchType.DOWN, 4, PLUNGE_X, PLUNGE_Y, 0)
	game.on_touch(TouchType.MOVE, 4, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE, 100)
	game.on_touch(TouchType.UP, 4, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE, 200)
	assert_eq(2, game.bot_launches())


func test_a_weak_plunge_during_multiball_is_relaunched_by_the_machine() -> void:
	var game := _plunge_with_pull(0.2)
	# The player is busy with another ball elsewhere on the table (parked so it can't drain).
	game.bot_place(1, T.CX, 200.0, 0.0, 0.0)
	var park := func() -> void: game.bot_hold(1, T.CX, 200.0)
	assert_true(_roll_back(game, 6.0, park))
	assert_false(game.bot_lane_ready(), "with another ball in play the player can't be plunging")
	var t := 0.0
	while t < 1.5:
		park.call()
		game.update(DT, CLOCK)
		t = _tick(t)
	assert_eq(2, game.bot_launches(), "the machine plunges the returned ball itself")


func test_cancel_input_drops_both_flippers_and_the_plunger_and_a_new_finger_works() -> void:
	var game := _started_game()
	game.on_touch(TouchType.DOWN, 1, LEFT_X, FLIP_Y, 0)
	game.on_touch(TouchType.DOWN, 2, RIGHT_X, FLIP_Y, 0)
	game.on_touch(TouchType.DOWN, 3, PLUNGE_X, PLUNGE_Y, 0)
	game.on_touch(TouchType.MOVE, 3, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE, 50)
	_steps(game, 0.1)
	assert_true(game.bot_flipper_held(0) and game.bot_flipper_held(1) and game.bot_plunger_held())
	game.cancel_input()
	assert_false(game.bot_flipper_held(0))
	assert_false(game.bot_flipper_held(1))
	assert_false(game.bot_plunger_held())
	assert_eq(0.0, game.bot_pull())
	_steps(game, 0.2)
	assert_near(T.rest_angle(0), game.bot_flipper_angle(0), 1e-3)
	assert_near(T.rest_angle(1), game.bot_flipper_angle(1), 1e-3)
	# The lost fingers' late events are harmless: nothing fires, nothing flips.
	game.on_touch(TouchType.MOVE, 3, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE, 300)
	game.on_touch(TouchType.UP, 3, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE, 310)
	game.on_touch(TouchType.UP, 1, LEFT_X, FLIP_Y, 310)
	assert_eq(0, game.bot_launches())
	assert_true(game.bot_lane_ready())
	# New fingers work.
	game.on_touch(TouchType.DOWN, 4, LEFT_X, FLIP_Y, 400)
	assert_true(game.bot_flipper_held(0))
	game.on_touch(TouchType.DOWN, 5, PLUNGE_X, PLUNGE_Y, 400)
	game.on_touch(TouchType.MOVE, 5, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE, 450)
	game.on_touch(TouchType.UP, 5, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE, 500)
	assert_eq(1, game.bot_launches())


# ---------------------------------------------------------------- rules

## Drops ball 0 straight down the middle, between the resting flippers.
func _drain_one(game: PinballGame) -> void:
	game.bot_place(0, T.CX, 540.0, 0.0, 300.0)
	var t := 0.0
	while game.bot_ball_in_play(0) and t < 2.0:
		game.update(DT, CLOCK)
		t = _tick(t)
	assert_false(game.bot_ball_in_play(0), "the ball should have drained")


func test_draining_ends_the_ball_and_the_last_ball_ends_the_round() -> void:
	var game := _empty_table()
	game.bot_end_ball_save()
	for ball in range(1, K.BALLS + 1):
		_drain_one(game)
		assert_eq(ball, game.bot_drains())
		assert_eq(K.BALLS - ball, game.bot_balls_left())
		_steps(game, K.SERVE_DELAY + 0.2)
		if ball < K.BALLS:
			assert_true(game.bot_lane_ready(), "the next ball waits on the plunger")
			assert_false(game.finished())
			game.bot_remove(game.bot_lane_ball_index())
			game.bot_end_ball_save()
	assert_true(game.bot_ended_early())
	assert_true(game.finished(), "out of balls ends the round")


func test_a_drain_during_ball_save_gives_the_ball_back() -> void:
	var game := _plunge_with_pull(1.0)
	assert_true(game.bot_ball_save_left() > 0.0)
	game.bot_remove(0)
	_drain_one(game)
	assert_eq(0, game.bot_drains())
	assert_eq(K.BALLS, game.bot_balls_left())
	_steps(game, 1.5)
	assert_eq(1, game.bot_live_balls(), "the saved ball is launched again")


func test_nothing_scores_after_time_up() -> void:
	var game := PinballGame.new()
	var d := SimHarness.RoundDriver.new(game, 11)
	d.play(game.round_seconds + 1.0)
	assert_true(game.finished())
	var before := game.score
	# A ball thrown into a bumper, through the rollovers and across the spinner during ENDING.
	game.bot_place(0, T.BUMPER_X[0], T.BUMPER_Y[0] - 40.0, 0.0, 900.0)
	game.bot_place(1, T.ROLLOVER_X[1], T.ROLLOVER_Y - 12.0, 0.0, 400.0)
	game.bot_place(2, 20.0, T.SPINNER_Y + 20.0, 0.0, -1500.0)
	game.on_touch(TouchType.DOWN, 9, LEFT_X, FLIP_Y, d.ms())
	assert_false(game.bot_flipper_held(0), "flippers are dead after time-up")
	d.end()
	assert_eq(before, game.score)


func test_scoring_rules() -> void:
	# A bumper hit scores and flashes.
	var game := _empty_table()
	game.bot_place(0, T.BUMPER_X[2], T.BUMPER_Y[2] - 40.0, 0.0, 600.0)
	_steps(game, 0.1)
	assert_true(game.bot_bumper_hits() >= 1)
	assert_true(game.score >= K.BUMPER_POINTS)
	# Knocking down all three drop targets raises the multiplier.
	var g2 := _empty_table()
	for i in 3:
		g2.bot_place(0, T.DROP_X - 60.0, T.DROP_Y0[i] + T.DROP_LEN / 2.0, 900.0, 0.0)
		_steps(g2, 0.12)
	assert_eq(0, g2.bot_drops_up())
	assert_eq(2, g2.bot_multiplier())
	g2.bot_remove(0)
	_steps(g2, 2.0)
	assert_eq(3, g2.bot_drops_up(), "the bank resets")
	# Lighting all three top lanes starts multiball; the orbit then pays the jackpot.
	var g3 := _empty_table()
	for i in 3:
		g3.bot_place(0, T.ROLLOVER_X[i], T.ROLLOVER_Y - 12.0, 0.0, 300.0)
		_steps(g3, 0.1)
		g3.bot_remove(0)
	assert_eq(1, g3.bot_multiballs())
	_steps(g3, 1.0)
	assert_eq(1, g3.bot_live_balls(), "the second ball is launched")
	g3.bot_place(0, 20.0, T.SPINNER_Y + 30.0, 0.0, -1400.0)
	_steps(g3, 0.1)
	assert_eq(1, g3.bot_jackpots())


func test_nudging_too_much_tilts() -> void:
	var game := _started_game()
	game.bot_remove(0)
	game.bot_place(0, T.CX, 300.0, 0.0, 0.0)
	var ms := 0
	var pid := 20
	for i in 3:
		game.on_touch(TouchType.DOWN, pid, LEFT_X, FLIP_Y, ms)
		game.on_touch(TouchType.MOVE, pid, LEFT_X, FLIP_Y - 80.0, ms + 60)
		game.on_touch(TouchType.UP, pid, LEFT_X, FLIP_Y - 80.0, ms + 80)
		pid += 1
		ms += 200
		game.update(DT, CLOCK)
	assert_true(game.bot_tilted())
	game.on_touch(TouchType.DOWN, 99, LEFT_X, FLIP_Y, ms)
	_steps(game, 0.1)
	assert_false(game.bot_flipper_held(0), "a tilted machine's flippers are dead")
	var s := game.score
	game.bot_place(0, T.BUMPER_X[0], T.BUMPER_Y[0] - 40.0, 0.0, 600.0)
	_steps(game, 0.2)
	assert_eq(s, game.score, "no scoring while tilted")
	# The tilt lasts until the ball drains.
	game.on_touch(TouchType.UP, 99, LEFT_X, FLIP_Y, ms)
	game.bot_end_ball_save()
	_drain_one(game)
	assert_false(game.bot_tilted())


# ---------------------------------------------------------------- failsafes

func test_a_ball_balanced_on_a_post_is_kicked_free() -> void:
	var game := _empty_table()
	# Exactly on top of a lane guide's round tip: the forces balance and it never moves.
	var px: float = T.LANE_GUIDE_X[1]
	game.bot_place(0, px, T.LANE_GUIDE_Y0 - 3.5 - T.BALL_R, 0.0, 0.0)
	_steps(game, 1.0)
	assert_near(px, game.bot_ball_x(0), 1e-3, "balanced")
	_steps(game, K.STUCK_SECONDS + 0.2)
	assert_eq(1, game.bot_search_kicks())
	_steps(game, 0.5)
	assert_true(absf(game.bot_ball_x(0) - px) > 5.0 or not game.bot_ball_in_play(0), "kicked away")


func test_a_ball_the_physics_cannot_finish_is_served_again() -> void:
	var game := _empty_table()
	game.bot_place(0, T.CX, 300.0, NAN, 100.0)
	_steps(game, 0.1)
	assert_eq(1, game.bot_failsafe_trips())
	assert_eq(K.BALLS, game.bot_balls_left(), "no ball lost")
	_steps(game, 1.5)
	assert_eq(1, game.bot_live_balls())
	for i in game.bot_max_balls():
		if game.bot_ball_in_play(i):
			assert_true(is_finite(game.bot_ball_x(i)))


func test_a_round_always_finishes_and_the_next_starts_clean() -> void:
	var game := PinballGame.new()
	var rng := KRandom.new(3)
	var stats := SimHarness.Stats.new("clean")
	# A busy round: the good bot plays it, building up multipliers, lanes and targets.
	var bot := Bots.FlipperBot.new(game, rng, 0.0, 0.0, 0.035, 0.0, 1.0)
	SimHarness.play_round(self, game, 5, stats, bot.tick)
	# Leave some state lying around, as a real round would.
	game.on_touch(TouchType.DOWN, 1, LEFT_X, FLIP_Y, 0)
	game.fixed_seed = 6
	game.start(SimHarness.fx())
	assert_eq(0, game.score)
	assert_eq(1, game.bot_multiplier())
	assert_eq(3, game.bot_drops_up())
	assert_eq(K.BALLS, game.bot_balls_left())
	assert_eq(1, game.bot_live_balls())
	assert_true(game.bot_lane_ready())
	assert_false(game.bot_flipper_held(0) or game.bot_flipper_held(1) or game.bot_plunger_held())
	assert_false(game.bot_tilted())
	assert_eq(0, game.bot_drains() + game.bot_failsafe_trips() + game.bot_bumper_hits() + game.bot_multiballs() + game.bot_launches())
	for i in 3:
		assert_false(game.bot_lane_lit(i))
	assert_near(T.rest_angle(0), game.bot_flipper_angle(0), 1e-4)
	assert_near(T.rest_angle(1), game.bot_flipper_angle(1), 1e-4)
	assert_false(game.finished())


func test_no_nan_or_escapes_over_long_wild_rounds() -> void:
	var game := PinballGame.new()
	var rng := KRandom.new(9)
	var stats := SimHarness.Stats.new("wild")
	var checked := [0]
	var problems: Array[String] = []
	for round_i in 10:
		var next_id := [1000]
		var held := [-1, -1]
		SimHarness.play_round(self, game, 100 + round_i, stats, func(_t: float, ms: int) -> void:
			# Mash everything: random flips, plunges and the odd nudge.
			for s in 2:
				if held[s] < 0 and rng.next_float() < 0.04:
					held[s] = next_id[0]
					next_id[0] += 1
					game.on_touch(TouchType.DOWN, held[s], LEFT_X if s == 0 else RIGHT_X, FLIP_Y, ms)
					if rng.next_float() < 0.05:
						game.on_touch(TouchType.MOVE, held[s], LEFT_X if s == 0 else RIGHT_X, FLIP_Y - 90.0, ms + 40)
				elif held[s] >= 0 and rng.next_float() < 0.08:
					game.on_touch(TouchType.UP, held[s], LEFT_X, FLIP_Y, ms)
					held[s] = -1
			if game.bot_lane_ready() and rng.next_float() < 0.02:
				var p: int = next_id[0]
				next_id[0] += 1
				game.on_touch(TouchType.DOWN, p, PLUNGE_X, PLUNGE_Y, ms)
				game.on_touch(TouchType.MOVE, p, PLUNGE_X, PLUNGE_Y + rng.next_float() * 120.0, ms + 30)
				game.on_touch(TouchType.UP, p, PLUNGE_X, PLUNGE_Y + 60.0, ms + 60)
			if game.bot_live_balls() > game.bot_max_balls():
				problems.append("%d live balls" % game.bot_live_balls())
			for i in game.bot_max_balls():
				if not game.bot_ball_in_play(i):
					continue
				var x := game.bot_ball_x(i)
				var y := game.bot_ball_y(i)
				if not (is_finite(x) and is_finite(y) and is_finite(game.bot_ball_vx(i)) and is_finite(game.bot_ball_vy(i))):
					problems.append("ball %d at (%s, %s)" % [i, x, y])
				if not (x > -1.0 and x < T.W + 1.0 and y > -1.0):
					problems.append("ball %d escaped to (%s, %s)" % [i, x, y])
				checked[0] += 1)
		assert_eq(0, game.bot_failsafe_trips(), "no failsafe retirements in round %d" % round_i)
	assert_true(problems.is_empty(), str(problems.slice(0, 5)))
	print("wild rounds: %d ball-steps checked, scores %s" % [checked[0], str(stats.scores)])
