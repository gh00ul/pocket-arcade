class_name PinballGame
extends BaseMiniGame
## games/pinball/PinballGame.kt: pinball on a 3D table: hold the left or right half of the screen for
## that flipper (both at once works), pull the plunger down and let go to launch. Pop bumpers,
## slingshots, a drop target bank (completing it raises the multiplier), three top rollovers (light
## them all for two-ball multiball with a jackpot on the left orbit's spinner) and a ball save for
## the first seconds of every ball. Three balls, or the clock, whichever runs out first.
##
## The simulation is 2D on the playfield plane (see [PinballTable]), sub-stepped so the fastest ball
## never passes through a flipper or a wall; the 3D view looks down the table from the player's end.
## The knobs are in [PinballTuning].

const T := preload("res://scripts/games/pinball/pinball_table.gd")
const K := preload("res://scripts/games/pinball/pinball_tuning.gd")

const OFF := 0
## Resting on the plunger in the shooter lane.
const LANE := 1
const PLAY := 2
const MAX_BALLS := 3
## Seconds before a saved or multiball ball is launched by the machine.
const AUTO_DELAY := 0.6
const MSG_SECONDS := 1.6

# Ball trail: samples kept, seconds between them, and how it looks (it fades in above a walking pace
# and is full strength by a fast shot).
const TRAIL_N := 9
const TRAIL_DT := 1.0 / 90.0
const TRAIL_MIN_SPEED := 260.0
const TRAIL_FULL_SPEED := 900.0
const TRAIL_WIDTH := 11.0
const TRAIL_ALPHA := 0.5
const TRAIL_Y := 6.0

# Look: every number here only changes how the table is lit and drawn.
## The backglass light washing over the table: reach, resting strength and extra on a mood pulse.
const BACK_LIGHT_R := 330.0
const BACK_LIGHT_BASE := 0.45
const BACK_LIGHT_PULSE := 0.55
## The flash at the last event on the table (a sling, a target, a lane, the orbit).
const EVENT_LIGHT_R := 130.0
const EVENT_LIGHT_I := 1.1
## The light riding each ball: height, reach and strength.
const BALL_LIGHT_Y := 26.0
const BALL_LIGHT_R := 78.0
const BALL_LIGHT_I := 0.42
## Rail strips and side stripes: resting brightness, the breathing swing and the extra on a mood pulse.
const RAIL_GLOW_BASE := 0.6
const RAIL_GLOW_SWING := 0.12
const RAIL_GLOW_PULSE := 0.9
## The dot-matrix quad's emissive strength (its brightest dot is what reaches the bloom).
const DMD_GLOW := 1.25
## Backglass rays: turning speed (radians a second), radius and centre height in table units, and strength.
const RAY_SPIN := 0.22
const RAY_R := 76.0
const RAY_CY := 130.0
const RAY_ALPHA := 0.22
## The shimmer band that crosses the glass: how often, how long, how wide and how strong.
const SHIMMER_PERIOD := 9.0
const SHIMMER_SECONDS := 1.7
const SHIMMER_W := 92.0
const SHIMMER_ALPHA := 0.32
## Full-glass wash in the mood colour, at full pulse.
const WASH_ALPHA := 0.22
## Frame bulbs: chase speed (steps a second) normally and in a big moment, and the unlit level.
const BULB_SLOW := 5.0
const BULB_FAST := 11.0
const BULB_DIM := 0.16
## Bumper shock wave: how far it grows past the bumper and how bright it starts.
const SHOCK_GROW := 84.0
const SHOCK_ALPHA := 0.8
const SLING_BEAM_W := 7.0

const BUMPER_COLORS: Array[int] = [Pal.HOTPINK, Pal.CYAN, Pal.YELLOW]
const HIT_PINK := [Pal.HOTPINK, Pal.PINK, Pal.WHITE]
const HIT_CYAN := [Pal.CYAN, Pal.SKY, Pal.WHITE]
const HIT_GOLD := [Pal.GOLD, Pal.YELLOW, Pal.WHITE]
const HIT_LIME := [Pal.LIME, Pal.GREEN, Pal.WHITE]
const DROP_COLORS: Array[int] = [Pal.ORANGE, Pal.YELLOW, Pal.RED]

# Message texts, so showing one never builds a string.
const M_NONE := 0
const M_SHOOT := 1
const M_SAVED := 2
const M_MULTIBALL := 3
const M_JACKPOT := 4
const M_TILT := 5
const M_DANGER := 6
const M_BANK := 7
const M_BALL := 8
const M_OVER := 9
const M_LANES := 10
const M_ORBIT := 11
const M_SEARCH := 12
const MESSAGES: Array[String] = [
	"", "SHOOT!", "BALL SAVED", "MULTIBALL!", "JACKPOT!", "TILT", "DANGER",
	"MULTIPLIER UP", "NEXT BALL", "GAME OVER", "LANES LIT", "ORBIT", "BALL SEARCH",
]
const MULT_TEXT: Array[String] = ["", "1X", "2X", "3X", "4X", "5X"]
const BALL_TEXT: Array[String] = ["BALL 1", "BALL 1", "BALL 2", "BALL 3", "BALL 4", "BALL 5"]
## "PULL " + ArcadeFont.DOWN, ArcadeFont.LEFT + " HOLD", "HOLD " + ArcadeFont.RIGHT.
const PULL_TEXT := "PULL ↓"
const HOLD_LEFT := "← HOLD"
const HOLD_RIGHT := "HOLD →"


class Ball:
	extends RefCounted
	var state := 0
	var x := 0.0
	var y := 0.0
	var vx := 0.0
	var vy := 0.0
	## Position at the start of the step, for the rollover and spinner switches.
	var px := 0.0
	var py := 0.0
	## Seconds until the machine launches it from the lane; negative when the player plunges it.
	var auto_t := -1.0
	## Seconds spent crawling (the stuck-ball failsafe).
	var slow_t := 0.0
	var search_kicks := 0
	## Where the last failsafe kick happened, and seconds of free play since.
	var kick_x := 0.0
	var kick_y := 0.0
	var free_t := 0.0
	## Resting on a raised, held flipper this step: crawling there is on purpose.
	var cradled := false


var _balls: Array[Ball] = []

# Flippers: 0 left, 1 right.
var _flip_ang := PackedFloat64Array([0.0, 0.0])
var _flip_omega := PackedFloat64Array([0.0, 0.0])
var _flip_id := PackedInt64Array([-1, -1])
var _flip_down_y := PackedFloat64Array([0.0, 0.0])
var _flip_down_ms := PackedInt64Array([0, 0])
var _flip_nudged: Array[bool] = [false, false]
# Each flipper's bat this sub-step (cos and sin of its angle times its length): a pure function of
# the angle, worked out once per sub-step instead of once per ball.
var _flip_ex := PackedFloat64Array([0.0, 0.0])
var _flip_ey := PackedFloat64Array([0.0, 0.0])

# Plunger.
var _plunger_id := -1
var _plunger_down_y := 0.0
var _pull := 0.0
## What the plunger rod shows: follows the pull, snaps forward on release.
var _plunger_vis := 0.0

# Rules state.
var _balls_left := K.BALLS
var _ball_number := 1
var _serve_t := 0.0
var _ball_save_t := 0.0
var _ball_save_armed := true
var _multiplier := 1
var _multiball := false
var _lane_lit: Array[bool] = [false, false, false]
var _drop_up: Array[bool] = [true, true, true]
var _bank_reset_t := 0.0
var _tilt_meter := 0.0
var _tilted := false

# Lamps and moving parts.
var _bumper_flash := PackedFloat64Array([0.0, 0.0, 0.0])
var _bumper_cool := PackedFloat64Array([0.0, 0.0, 0.0])
var _sling_flash := PackedFloat64Array([0.0, 0.0])
var _sling_cool := PackedFloat64Array([0.0, 0.0])
var _drop_sink := PackedFloat64Array([0.0, 0.0, 0.0])
var _drop_flash := PackedFloat64Array([0.0, 0.0, 0.0])
var _lane_flash := PackedFloat64Array([0.0, 0.0, 0.0])
var _spin_angle := 0.0
var _spin_speed := 0.0
var _spin_scored := 0.0
var _jackpot_flash := 0.0
var _message := M_NONE
var _message_t := 0.0

# Contact scratch (written by _contact).
var _hit_nx := 0.0
var _hit_ny := 0.0

# Counters for tests and tuning.
var _drains := 0
var _failsafe_trips := 0
var _search_kicks_total := 0
var _bumper_hits := 0
var _multiballs := 0
var _jackpots := 0
var _last_launch_speed := 0.0
var _launches := 0

# Presentation only: read by render(), written by the step's timers and event hooks, and never
# looked at by the rules or the physics (nor do they touch the game's random numbers).
var _trail_x := PackedFloat64Array()
var _trail_y := PackedFloat64Array()
var _trail_count := PackedInt32Array([0, 0, 0])
var _trail_acc := 0.0
## The colour the table's lamps and backglass lean towards, and how strongly right now (0..1, decaying).
var _mood_color := Pal.HOTPINK
var _mood_pulse := 0.0
## A short-lived light at the last event on the table (a sling kick, a target, a lane, the orbit).
var _evt_x := 0.0
var _evt_z := 0.0
var _evt_color := Pal.WHITE
var _evt_t := 0.0

var _labels: Array = []

# Each wall's bounding box (table layout, worked out once): a ball farther than its reach outside
# it can't touch the wall, so the exact test is skipped. The result is the same as testing every
# wall, in the same order.
static var _seg_x0 := PackedFloat64Array()
static var _seg_x1 := PackedFloat64Array()
static var _seg_y0 := PackedFloat64Array()
static var _seg_y1 := PackedFloat64Array()


static func _static_init() -> void:
	var n := T.seg_count
	_seg_x0.resize(n)
	_seg_x1.resize(n)
	_seg_y0.resize(n)
	_seg_y1.resize(n)
	for i in n:
		_seg_x0[i] = minf(T.ax[i], T.bx[i])
		_seg_x1[i] = maxf(T.ax[i], T.bx[i])
		_seg_y0[i] = minf(T.ay[i], T.by[i])
		_seg_y1[i] = maxf(T.ay[i], T.by[i])


func _init() -> void:
	id = "pinball"
	title = "STAR FLIPPER"
	marquee = "FLIP"
	instructions = PackedStringArray([
		"HOLD LEFT / RIGHT HALF TO FLIP",
		"PULL THE PLUNGER DOWN, LET GO",
		"LIGHT 3 TOP LANES = MULTIBALL",
		"DROP TARGETS = MULTIPLIER",
		"SWIPE UP TO NUDGE - DON'T TILT!",
	])
	look = MiniGame.CabinetLook.new(Pal.PURPLE, Pal.CYAN, Pal.HOTPINK, MiniGame.CabinetShape.PINBALL)
	round_seconds = K.ROUND_SECONDS
	for i in MAX_BALLS:
		_balls.append(Ball.new())
	_trail_x.resize(MAX_BALLS * TRAIL_N)
	_trail_y.resize(MAX_BALLS * TRAIL_N)
	_labels.resize(4096)
	_stage.look(150.0, 700.0, 860.0, 150.0, 0.0, 280.0, 44.0)
	for i in MAX_BALLS:
		_ball_lights.append(PointLight.new(0.0, BALL_LIGHT_Y, 0.0, 0.78, 0.86, 1.0, BALL_LIGHT_R, 0.0))


## build-13's custom hall cabinet (PinballCabinet) is a later step: until the hall's cabinet kit is
## in, the hall builds the built-in PINBALL shape.
func cabinet() -> Object:
	return null


func reset() -> void:
	for b in _balls:
		b.state = OFF
	for s in 2:
		_flip_ang[s] = T.rest_angle(s)
		_flip_omega[s] = 0.0
		_flip_id[s] = -1
		_flip_nudged[s] = false
	_plunger_id = -1
	_pull = 0.0
	_plunger_vis = 0.0
	_balls_left = K.BALLS
	_ball_number = 1
	_serve_t = 0.0
	_ball_save_t = 0.0
	_multiplier = 1
	_multiball = false
	_lane_lit.fill(false)
	_drop_up.fill(true)
	_drop_sink.fill(0.0)
	_drop_flash.fill(0.0)
	_lane_flash.fill(0.0)
	_bank_reset_t = 0.0
	_tilt_meter = 0.0
	_tilted = false
	_bumper_flash.fill(0.0)
	_bumper_cool.fill(0.0)
	_sling_flash.fill(0.0)
	_sling_cool.fill(0.0)
	_spin_angle = 0.0
	_spin_speed = 0.0
	_spin_scored = 0.0
	_jackpot_flash = 0.0
	_trail_count.fill(0)
	_trail_acc = 0.0
	_mood_pulse = 0.0
	_evt_t = 0.0
	_drains = 0
	_failsafe_trips = 0
	_search_kicks_total = 0
	_bumper_hits = 0
	_multiballs = 0
	_jackpots = 0
	_last_launch_speed = 0.0
	_launches = 0
	_serve_ball(false)
	_ball_save_armed = true
	_show(M_SHOOT)


func tickets_for(p_score: int) -> int:
	@warning_ignore("integer_division")
	return K.BASE_TICKETS + p_score / K.POINTS_PER_TICKET


## Nothing scores after the clock runs out, so the round is over the moment it does.
func is_settled() -> bool:
	return time_up or _live_balls() == 0


func on_time_up() -> void:
	cancel_input()
	_serve_t = 0.0


## Drops both flippers and lets the plunger go without firing. The balls roll on.
func cancel_input() -> void:
	_flip_id[0] = -1
	_flip_id[1] = -1
	_plunger_id = -1
	_pull = 0.0


# ---------------------------------------------------------------- input

func on_touch(type: int, pid: int, x: float, y: float, time_ms: int) -> void:
	if type == TouchType.DOWN:
		if time_up or pid == _plunger_id or pid == _flip_id[0] or pid == _flip_id[1]:
			return
		if _plunger_id < 0 and x >= K.PLUNGER_ZONE_X and y >= K.PLUNGER_ZONE_Y and _lane_ball() >= 0:
			_plunger_id = pid
			_plunger_down_y = y
			_pull = 0.0
			play(Sfx.CLINK, 0.3, 0.7)
			return
		var side := 0 if x < GAME_W / 2.0 else 1
		if _flip_id[side] >= 0:
			return
		_flip_id[side] = pid
		_flip_down_y[side] = y
		_flip_down_ms[side] = time_ms
		_flip_nudged[side] = false
		if not _tilted:
			play(Sfx.FLIPPER, 0.9, 0.96 if side == 0 else 1.04)
			fx.haptics.bump()
			_change_lanes(side)
	elif type == TouchType.MOVE:
		if pid == _plunger_id:
			_pull = clampf((y - _plunger_down_y) / K.PULL_RANGE, 0.0, 1.0)
			return
		for s in 2:
			if pid != _flip_id[s] or _flip_nudged[s]:
				continue
			if _flip_down_y[s] - y > K.NUDGE_DIST and time_ms - _flip_down_ms[s] <= K.NUDGE_MS:
				_flip_nudged[s] = true
				_nudge()
	elif type == TouchType.UP:
		if pid == _plunger_id:
			_plunger_id = -1
			var p := _pull
			_pull = 0.0
			var i := _lane_ball()
			if p >= K.MIN_PULL and i >= 0 and not time_up:
				_launch(_balls[i], K.LAUNCH_MIN + (K.LAUNCH_MAX - K.LAUNCH_MIN) * p)
				if _ball_save_armed:
					_ball_save_armed = false
					_ball_save_t = K.BALL_SAVE_SECONDS
			return
		for s in 2:
			if pid == _flip_id[s]:
				_flip_id[s] = -1
				if not _tilted and not time_up:
					play(Sfx.FLIPPER, 0.25, 0.7)


## Pressing a flipper shifts the lit top lanes that way (lane change).
func _change_lanes(side: int) -> void:
	var a := _lane_lit[0]
	var b := _lane_lit[1]
	var c := _lane_lit[2]
	if side == 0:
		_lane_lit[0] = b
		_lane_lit[1] = c
		_lane_lit[2] = a
	else:
		_lane_lit[0] = c
		_lane_lit[1] = a
		_lane_lit[2] = b


func _nudge() -> void:
	if _tilted:
		return
	for b in _balls:
		if b.state != PLAY:
			continue
		b.vy -= K.NUDGE_KICK
		b.vx += (rng.next_float() - 0.5) * 100.0
	shake.add(0.35)
	play(Sfx.THUD, 0.8, 0.8)
	fx.haptics.heavy()
	_tilt_meter += 1.0
	if _tilt_meter >= K.TILT_AT:
		_tilted = true
		_show(M_TILT)
		play(Sfx.TILT)
		flash.trigger(0.5)
		# A tilted machine goes dead: the flippers drop until the ball drains.
		_flip_id[0] = -1
		_flip_id[1] = -1
	elif _tilt_meter >= K.TILT_WARN:
		_show(M_DANGER)
		play(Sfx.TILT, 0.5, 1.3)


# ---------------------------------------------------------------- balls in and out

func _live_balls() -> int:
	var n := 0
	for b in _balls:
		if b.state != OFF:
			n += 1
	return n


## The ball waiting for the player's plunger, or -1.
func _lane_ball() -> int:
	for i in _balls.size():
		if _balls[i].state == LANE and _balls[i].auto_t < 0.0:
			return i
	return -1


func _serve_ball(auto: bool) -> void:
	var b: Ball = null
	for c in _balls:
		if c.state == OFF:
			b = c
			break
	if b == null:
		return
	b.state = LANE
	b.x = T.LANE_X
	b.y = T.LANE_REST_Y
	b.vx = 0.0
	b.vy = 0.0
	b.px = b.x
	b.py = b.y
	b.auto_t = AUTO_DELAY if auto else -1.0
	b.slow_t = 0.0
	b.search_kicks = 0
	if not auto:
		play(Sfx.CLINK, 0.5, 1.2)


func _launch(b: Ball, speed: float) -> void:
	b.state = PLAY
	b.vx = 0.0
	b.vy = -speed
	b.auto_t = -1.0
	b.slow_t = 0.0
	_last_launch_speed = speed
	_launches += 1
	_plunger_vis = 0.0
	play(Sfx.PLUNGER, 0.9, 0.8 + speed / 3500.0)
	fx.haptics.tick()


func _drain(b: Ball) -> void:
	b.state = OFF
	if time_up:
		return
	if _ball_save_t > 0.0 and not _tilted:
		_serve_ball(true)
		_show(M_SAVED)
		play(Sfx.LUCKY, 0.8)
		return
	var live := _live_balls()
	if live > 0:
		if live == 1 and _multiball:
			_multiball = false
		play(Sfx.DRAIN, 0.4, 1.3)
		return
	_drains += 1
	play(Sfx.DRAIN)
	fx.haptics.heavy()
	shake.add(0.3)
	_multiball = false
	_multiplier = 1
	_tilted = false
	_tilt_meter = 0.0
	_balls_left -= 1
	if _balls_left > 0:
		_ball_number += 1
		_serve_t = K.SERVE_DELAY
		_show(M_BALL)
	else:
		ended_early = true
		_show(M_OVER)


## Failsafe: takes a ball the physics can't finish off the table and serves it again, free.
func _retire(b: Ball) -> void:
	_failsafe_trips += 1
	b.state = OFF
	if not time_up:
		_serve_ball(true)
		_show(M_SEARCH)


func _start_multiball() -> void:
	_multiball = true
	_multiballs += 1
	_ball_save_t = maxf(_ball_save_t, K.MULTIBALL_SAVE_SECONDS)
	_serve_ball(true)
	_show(M_MULTIBALL)
	play(Sfx.JACKPOT, 0.8)
	play(Sfx.CHEER, 0.5)
	fx.haptics.win()
	flash.trigger(0.6)
	particles.confetti(0.0, 0.0, GAME_W, 60)


# ---------------------------------------------------------------- simulation

func step(dt: float) -> void:
	_step_timers(dt)
	if _serve_t > 0.0:
		_serve_t -= dt
		if _serve_t <= 0.0 and not time_up:
			_serve_ball(false)
			_ball_save_armed = true
	for b in _balls:
		if b.state == LANE and b.auto_t >= 0.0:
			b.auto_t -= dt
			if b.auto_t < 0.0:
				_launch(b, K.AUTO_LAUNCH_SPEED)
	for b in _balls:
		b.px = b.x
		b.py = b.y
		b.cradled = false
	var n := _substeps(dt)
	var h := dt / n
	for s in n:
		_move_flippers(h)
		for b in _balls:
			if b.state == PLAY:
				_move_ball(b, h)
		_collide_balls()
	for b in _balls:
		if b.state == PLAY:
			_after_step(b, dt)
	_step_trails(dt)


## Records where each ball was, a few dozen times a second, for the light trail it drags.
func _step_trails(dt: float) -> void:
	_trail_acc += dt
	if _trail_acc < TRAIL_DT:
		return
	_trail_acc = 0.0 if _trail_acc > 2.0 * TRAIL_DT else _trail_acc - TRAIL_DT
	var tx := _trail_x
	var ty := _trail_y
	for i in MAX_BALLS:
		var b := _balls[i]
		if b.state != PLAY:
			_trail_count[i] = 0
			continue
		var o := i * TRAIL_N
		var n := mini(_trail_count[i] + 1, TRAIL_N)
		var k := n - 1
		while k >= 1:
			tx[o + k] = tx[o + k - 1]
			ty[o + k] = ty[o + k - 1]
			k -= 1
		tx[o] = b.x
		ty[o] = b.y
		_trail_count[i] = n


func _step_timers(dt: float) -> void:
	for i in 3:
		_bumper_flash[i] = maxf(_bumper_flash[i] - dt * 5.0, 0.0)
		_bumper_cool[i] -= dt
		_drop_flash[i] = maxf(_drop_flash[i] - dt * 3.0, 0.0)
		_lane_flash[i] = maxf(_lane_flash[i] - dt * 2.0, 0.0)
		_drop_sink[i] = MathUtil.approach(_drop_sink[i], 0.0 if _drop_up[i] else 1.0, dt * 8.0)
	for i in 2:
		_sling_flash[i] = maxf(_sling_flash[i] - dt * 6.0, 0.0)
		_sling_cool[i] -= dt
	_jackpot_flash = maxf(_jackpot_flash - dt * 1.5, 0.0)
	_mood_pulse = maxf(_mood_pulse - dt * 1.1, 0.0)
	_evt_t = maxf(_evt_t - dt * 4.5, 0.0)
	_message_t -= dt
	if _ball_save_t > 0.0:
		_ball_save_t -= dt
	_tilt_meter = maxf(_tilt_meter - K.TILT_DECAY * dt, 0.0)
	# The plunger rod eases after the finger; it springs forward faster than it's pulled.
	_plunger_vis = MathUtil.approach(_plunger_vis, _pull, dt * (6.0 if _pull > _plunger_vis else 20.0))
	# The spinner winds down; every whole turn scores.
	_spin_angle += _spin_speed * dt
	_spin_speed *= exp(-1.6 * dt)
	if _spin_speed < 0.3:
		_spin_speed = 0.0
	while _spin_angle - _spin_scored >= TAU:
		_spin_scored += TAU
		_award(K.SPINNER_POINTS, 20.0, T.SPINNER_Y, Pal.CYAN, false)
	if _bank_reset_t > 0.0:
		_bank_reset_t -= dt
		if _bank_reset_t <= 0.0:
			if _bank_clear():
				_drop_up.fill(true)
				play(Sfx.CLINK, 0.6, 0.8)
			else:
				_bank_reset_t = 0.2


## Enough sub-steps that neither a ball nor a swinging flipper moves more than a fraction of a ball radius.
func _substeps(dt: float) -> int:
	var fast := 0.0
	for b in _balls:
		if b.state != PLAY:
			continue
		var s := sqrt(b.vx * b.vx + b.vy * b.vy)
		if s > fast:
			fast = s
	var tip := 0.0
	for s in 2:
		var target := T.up_angle(s) if _flipper_held(s) else T.rest_angle(s)
		if _flip_ang[s] != target:
			tip = K.FLIP_UP_SPEED * (T.FLIP_LEN + T.FLIP_R1)
	var move := (fast + tip) * dt
	if not is_finite(move):
		return 24
	# Kotlin's ceil(...).toInt().coerceIn(1, 24), clamped before the cast (a huge value saturates).
	var q := ceilf(move / (T.BALL_R * K.SUBSTEP_FRACTION))
	if q >= 24.0:
		return 24
	if q <= 1.0:
		return 1
	return int(q)


func _flipper_held(side: int) -> bool:
	return _flip_id[side] >= 0 and not _tilted and not time_up


func _move_flippers(h: float) -> void:
	for s in 2:
		var held := _flipper_held(s)
		var target := T.up_angle(s) if held else T.rest_angle(s)
		var speed := K.FLIP_UP_SPEED if held else K.FLIP_DOWN_SPEED
		var a := _flip_ang[s]
		var na := MathUtil.approach(a, target, speed * h)
		_flip_omega[s] = (na - a) / h
		_flip_ang[s] = na
		_flip_ex[s] = cos(na) * T.FLIP_LEN
		_flip_ey[s] = sin(na) * T.FLIP_LEN


func _move_ball(b: Ball, h: float) -> void:
	b.vy += K.GRAVITY * h
	var k := 1.0 - K.DRAG * h
	b.vx *= k
	b.vy *= k
	_clamp_speed(b)
	b.x += b.vx * h
	b.y += b.vy * h
	_collide(b)


func _clamp_speed(b: Ball) -> void:
	var s2 := b.vx * b.vx + b.vy * b.vy
	var mx := K.MAX_SPEED
	if s2 > mx * mx:
		var k := mx / sqrt(s2)
		b.vx *= k
		b.vy *= k


func _collide(b: Ball) -> void:
	var r := T.BALL_R
	# Walls, guides and the slingshots (a wall whose box the ball is clear of by more than its
	# reach can't be touched: skipped without the exact test, which would find nothing).
	var ax := T.ax
	var ay := T.ay
	var bx := T.bx
	var by := T.by
	var kinds := T.kind
	var x0 := _seg_x0
	var x1 := _seg_x1
	var y0 := _seg_y0
	var y1 := _seg_y1
	for i in T.seg_count:
		var x := b.x
		var y := b.y
		if x < x0[i] - r or x > x1[i] + r or y < y0[i] - r or y > y1[i] + r:
			continue
		var kind := kinds[i]
		if kind == T.GATE:
			# One-way: only a ball above the gate and coming down onto it bounces.
			var s := (x - ax[i]) * T.gate_nx + (y - ay[i]) * T.gate_ny
			if s < 0.0 or b.vx * T.gate_nx + b.vy * T.gate_ny > 0.0:
				continue
		var hit := _segment(b, ax[i], ay[i], bx[i], by[i], r, T.bounce[i])
		if hit > K.SLING_MIN and kind == T.SLING:
			_sling(b, T.tag[i])
	var pxs := T.px
	var pys := T.py
	var prs := T.pr
	for i in T.post_count:
		var reach := prs[i] + r
		var dx := b.x - pxs[i]
		var dy := b.y - pys[i]
		if dx > reach or dx < -reach or dy > reach or dy < -reach:
			continue
		_contact(b, pxs[i], pys[i], reach, K.POST_BOUNCE, 0.0, 0.0)
	for i in 3:
		var hit := _contact(b, T.BUMPER_X[i], T.BUMPER_Y[i], T.BUMPER_R + r, K.BUMPER_BOUNCE, 0.0, 0.0)
		if hit >= 0.0:
			_bumper(b, i)
	for i in 3:
		if not _drop_up[i]:
			continue
		var dy0: float = T.DROP_Y0[i]
		var reach := r + 2.0
		var x := b.x
		var y := b.y
		if x < T.DROP_X - reach or x > T.DROP_X + reach or y < dy0 - reach or y > dy0 + T.DROP_LEN + reach:
			continue
		var hit := _segment(b, T.DROP_X, dy0, T.DROP_X, dy0 + T.DROP_LEN, reach, 0.3)
		if hit > K.DROP_MIN:
			_drop_target(i)
	for s in 2:
		_flipper(b, s)


## Keeps [param b] at least [param reach] from the point ([param px], [param py]) moving at
## ([param svx], [param svy]) and bounces it with restitution [param e]. Returns the approach
## speed, 0 for a resting touch, or -1 when not touching. The contact normal is left in
## _hit_nx, _hit_ny.
func _contact(b: Ball, px: float, py: float, reach: float, e: float, svx: float, svy: float) -> float:
	var dx := b.x - px
	var dy := b.y - py
	var d2 := dx * dx + dy * dy
	if d2 >= reach * reach:
		return -1.0
	var d := sqrt(d2)
	var nx: float
	var ny: float
	if d < 1e-4:
		nx = 0.0
		ny = -1.0
	else:
		nx = dx / d
		ny = dy / d
	b.x += nx * (reach - d)
	b.y += ny * (reach - d)
	_hit_nx = nx
	_hit_ny = ny
	var vn := (b.vx - svx) * nx + (b.vy - svy) * ny
	if vn >= 0.0:
		return 0.0
	var ee := 0.0 if -vn < K.REST_SPEED else e
	b.vx -= (1.0 + ee) * vn * nx
	b.vy -= (1.0 + ee) * vn * ny
	return -vn


## _contact against the nearest point of a static segment.
func _segment(b: Ball, ax: float, ay: float, bx: float, by: float, reach: float, e: float) -> float:
	var ex := bx - ax
	var ey := by - ay
	var l2 := ex * ex + ey * ey
	var t := ((b.x - ax) * ex + (b.y - ay) * ey) / l2 if l2 > 0.0 else 0.0
	t = clampf(t, 0.0, 1.0)
	return _contact(b, ax + ex * t, ay + ey * t, reach, e, 0.0, 0.0)


## The flipper is a tapered capsule turning about its pivot; the ball takes its surface speed.
func _flipper(b: Ball, side: int) -> void:
	var pvx := T.flip_x(side)
	var pvy := T.FLIP_Y
	# The ball can't reach a bat it is farther from than the bat's length and thickest reach.
	var qx := b.x - pvx
	var qy := b.y - pvy
	var far := T.FLIP_LEN + T.FLIP_R0 + T.BALL_R
	if qx > far or qx < -far or qy > far or qy < -far:
		return
	var ex := _flip_ex[side]
	var ey := _flip_ey[side]
	var t := (qx * ex + qy * ey) / (T.FLIP_LEN * T.FLIP_LEN)
	t = clampf(t, 0.0, 1.0)
	var cx := pvx + ex * t
	var cy := pvy + ey * t
	var reach := T.FLIP_R0 + (T.FLIP_R1 - T.FLIP_R0) * t + T.BALL_R
	var w := _flip_omega[side]
	var hit := _contact(b, cx, cy, reach, K.FLIP_BOUNCE, -w * (cy - pvy), w * (cx - pvx))
	if hit >= 0.0 and _flipper_held(side) and w == 0.0:
		b.cradled = true


func _collide_balls() -> void:
	for i in MAX_BALLS:
		var a := _balls[i]
		if a.state != PLAY:
			continue
		for j in range(i + 1, MAX_BALLS):
			var c := _balls[j]
			if c.state != PLAY:
				continue
			var dx := c.x - a.x
			var dy := c.y - a.y
			var d2 := dx * dx + dy * dy
			var rs := T.BALL_R * 2.0
			if d2 >= rs * rs or d2 < 1e-6:
				continue
			var d := sqrt(d2)
			var nx := dx / d
			var ny := dy / d
			var push := (rs - d) / 2.0
			a.x -= nx * push
			a.y -= ny * push
			c.x += nx * push
			c.y += ny * push
			var vn := (c.vx - a.vx) * nx + (c.vy - a.vy) * ny
			if vn < 0.0:
				var jj := -0.95 * vn
				a.vx -= jj * nx
				a.vy -= jj * ny
				c.vx += jj * nx
				c.vy += jj * ny


## Switches, the drain and the failsafes, once per step.
func _after_step(b: Ball, dt: float) -> void:
	if not is_finite(b.x) or not is_finite(b.y) or not is_finite(b.vx) or not is_finite(b.vy) \
			or b.x < -30.0 or b.x > T.W + 30.0 or b.y < -40.0:
		_retire(b)
		return
	if b.y > T.DRAIN_Y:
		if b.x > T.PLAY_W:
			_retire(b)
		else:
			_drain(b)
		return
	# A plunge too weak to clear the gate rolls back onto the plunger.
	if b.x > T.PLAY_W and b.y >= T.LANE_REST_Y - 0.5 and b.vy >= 0.0:
		# The machine only plunges it if the player can't: another ball is already waiting on the
		# plunger or is in play. Asked before the ball goes back to the lane, or it would find
		# itself waiting there and always be plunged for free.
		var auto_plunge := _lane_ball() >= 0 or _live_balls() > 1
		b.state = LANE
		b.x = T.LANE_X
		b.y = T.LANE_REST_Y
		b.vx = 0.0
		b.vy = 0.0
		b.auto_t = AUTO_DELAY if auto_plunge else -1.0
		return
	# Top rollovers.
	var ry := T.ROLLOVER_Y
	if (b.py < ry) != (b.y < ry):
		for i in 3:
			if absf(b.x - T.ROLLOVER_X[i]) < T.ROLLOVER_HALF:
				_rollover(i)
	# The spinner across the left orbit; going up it is an orbit shot (a jackpot in multiball).
	var sy := T.SPINNER_Y
	if (b.py < sy) != (b.y < sy) and b.x < T.ORBIT_X:
		_spin_speed = maxf(_spin_speed, absf(b.vy) * 0.05)
		play(Sfx.SPINNER, 0.8)
		if b.vy < 0.0:
			_orbit()
	# Stuck-ball failsafe: a ball crawling for too long (not held on a flipper) gets kicked, and one
	# that stays stuck is served again.
	var speed := sqrt(b.vx * b.vx + b.vy * b.vy)
	if b.cradled:
		b.slow_t = 0.0
	elif speed < K.STUCK_SPEED:
		b.slow_t += dt
		if b.slow_t > K.STUCK_SECONDS:
			b.slow_t = 0.0
			# Stalling somewhere new starts the count again; the same spot keeps counting, however
			# fast the last kick sent it (a kicked ball always moves fast for a moment).
			var kx := b.x - b.kick_x
			var ky := b.y - b.kick_y
			var rr := K.STUCK_RADIUS
			if kx * kx + ky * ky > rr * rr:
				b.search_kicks = 0
			b.search_kicks += 1
			b.kick_x = b.x
			b.kick_y = b.y
			b.free_t = 0.0
			if b.search_kicks > K.STUCK_KICKS:
				_retire(b)
			else:
				_search_kicks_total += 1
				# Mostly up the table; the sideways part alternates and varies so a kick that bounced
				# straight back into the pocket isn't repeated.
				var out := 1.0 if b.x < T.CX else -1.0
				var side := out if b.search_kicks % 2 == 1 else -out
				b.vy = -K.STUCK_KICK
				b.vx = side * (40.0 + rng.next_float() * 120.0)
				play(Sfx.THUD, 0.6, 1.2)
				_show(M_SEARCH)
	else:
		b.slow_t = 0.0
		if b.search_kicks > 0:
			b.free_t += dt
			if b.free_t > K.STUCK_FREE_SECONDS:
				b.search_kicks = 0


# ---------------------------------------------------------------- rules and scoring

func _label(points: int) -> String:
	if points < 0 or points >= _labels.size():
		return "+%d" % points
	var s: Variant = _labels[points]
	if s == null:
		s = "+%d" % points
		_labels[points] = s
	return s


## Scores [param base] times the multiplier (nothing when tilted or after time-up).
func _award(base: int, tx: float, ty: float, color: int, popup: bool) -> bool:
	if time_up or _tilted:
		return false
	var pts := base * _multiplier
	score += pts
	if popup:
		var p: Variant = _stage.to_field(tx, 18.0, ty)
		if p != null:
			popups.add(_label(pts), p.x, p.y - 10.0, color, 2.4)
	return true


func _burst_at(tx: float, ty: float, n: int, colors: Array, speed: float) -> void:
	var p: Variant = _stage.to_field(tx, 8.0, ty)
	if p != null:
		particles.burst(p.x, p.y, n, speed * 0.3, speed, colors, 0.45, 3.0)


func _show(m: int) -> void:
	_message = m
	_message_t = MSG_SECONDS
	if m == M_MULTIBALL:
		_pulse_mood(Pal.CYAN, 1.0)
	elif m == M_JACKPOT or m == M_BANK:
		_pulse_mood(Pal.GOLD, 1.0)
	elif m == M_SAVED:
		_pulse_mood(Pal.LIME, 0.8)
	elif m == M_TILT:
		_pulse_mood(Pal.RED, 1.0)
	elif m == M_LANES or m == M_ORBIT:
		_pulse_mood(Pal.SKY, 0.7)
	elif m == M_NONE:
		pass
	else:
		_pulse_mood(Pal.HOTPINK, 0.5)


## Tints the backglass, rails and table wash towards [param color] for a moment.
func _pulse_mood(color: int, amount: float) -> void:
	_mood_color = color
	_mood_pulse = maxf(_mood_pulse, amount)


## A flash of light at ([param x], [param z]) on the table, fading in about a fifth of a second.
func _event_light(x: float, z: float, color: int) -> void:
	_evt_x = x
	_evt_z = z
	_evt_color = color
	_evt_t = 1.0


func _bumper(b: Ball, i: int) -> void:
	if _tilted:
		return
	# A pop bumper throws the ball away from its centre, however gently it touched.
	var vn := b.vx * _hit_nx + b.vy * _hit_ny
	if vn < K.BUMPER_KICK:
		b.vx += _hit_nx * (K.BUMPER_KICK - vn)
		b.vy += _hit_ny * (K.BUMPER_KICK - vn)
	if _bumper_cool[i] > 0.0:
		return
	_bumper_cool[i] = 0.06
	_bumper_flash[i] = 1.0
	_bumper_hits += 1
	_award(K.BUMPER_POINTS, T.BUMPER_X[i], T.BUMPER_Y[i], BUMPER_COLORS[i], _multiplier > 1 or _multiball)
	play(Sfx.BUMPER, 0.8, 0.9 + i * 0.08)
	fx.haptics.tick()
	shake.add(0.07)
	_burst_at(T.BUMPER_X[i], T.BUMPER_Y[i], 10, HIT_CYAN if i == 1 else HIT_PINK, 170.0)


func _sling(b: Ball, i: int) -> void:
	if _tilted or _sling_cool[i] > 0.0:
		return
	b.vx += _hit_nx * K.SLING_KICK
	b.vy += _hit_ny * K.SLING_KICK
	_sling_cool[i] = 0.12
	_sling_flash[i] = 1.0
	var sx := (T.SLING_AX + T.SLING_CX) / 2.0 if i == 0 else T.PLAY_W - (T.SLING_AX + T.SLING_CX) / 2.0
	var sy := (T.SLING_AY + T.SLING_CY) / 2.0
	_award(K.SLING_POINTS, sx, sy, Pal.LIME, false)
	_event_light(sx, sy, Pal.LIME)
	play(Sfx.SLINGSHOT, 0.8, 0.95 + i * 0.1)
	fx.haptics.tick()
	shake.add(0.05)
	_burst_at(sx, sy, 8, HIT_LIME, 140.0)


func _drop_target(i: int) -> void:
	if _tilted or time_up:
		return
	_drop_up[i] = false
	_drop_flash[i] = 1.0
	var ty: float = T.DROP_Y0[i] + T.DROP_LEN / 2.0
	_award(K.DROP_POINTS, T.DROP_X, ty, Pal.ORANGE, true)
	_event_light(T.DROP_X - 14.0, ty, DROP_COLORS[i])
	play(Sfx.THUD, 0.7, 1.4)
	fx.haptics.hit()
	_burst_at(T.DROP_X, ty, 10, HIT_GOLD, 150.0)
	if not _drop_up[0] and not _drop_up[1] and not _drop_up[2]:
		if _multiplier < K.MAX_MULT:
			_multiplier += 1
		_award(K.BANK_POINTS, T.DROP_X - 40.0, ty, Pal.GOLD, true)
		_show(M_BANK)
		play(Sfx.WIN, 0.7)
		fx.haptics.win()
		flash.trigger(0.3)
		_bank_reset_t = 1.2


## True when no ball is close enough to the bank for the targets to pop up into it.
func _bank_clear() -> bool:
	for b in _balls:
		if b.state != PLAY:
			continue
		if b.x > T.DROP_X - T.BALL_R - 8.0 and b.y > T.DROP_Y0[0] - T.BALL_R and b.y < T.DROP_Y0[2] + T.DROP_LEN + T.BALL_R:
			return false
	return true


func _rollover(i: int) -> void:
	if _tilted or time_up:
		return
	_lane_flash[i] = 1.0
	_event_light(T.ROLLOVER_X[i], T.ROLLOVER_Y + 22.0, Pal.SKY)
	if _lane_lit[i]:
		_award(K.LANE_REPEAT_POINTS, T.ROLLOVER_X[i], T.ROLLOVER_Y, Pal.SKY, false)
		play(Sfx.BLIP, 0.4, 0.9)
		return
	_lane_lit[i] = true
	_award(K.LANE_POINTS, T.ROLLOVER_X[i], T.ROLLOVER_Y, Pal.SKY, true)
	play(Sfx.BLIP, 0.7, 1.2 + i * 0.15)
	if _lane_lit[0] and _lane_lit[1] and _lane_lit[2]:
		_lane_lit.fill(false)
		_lane_flash.fill(1.0)
		_award(K.MULTIBALL_POINTS, T.CX, T.ROLLOVER_Y + 20.0, Pal.CYAN, true)
		if not _multiball and _live_balls() < MAX_BALLS:
			_start_multiball()
		else:
			_show(M_LANES)


func _orbit() -> void:
	if _tilted or time_up:
		return
	if _multiball:
		_jackpots += 1
		_jackpot_flash = 1.0
		_event_light(24.0, T.SPINNER_Y, Pal.GOLD)
		_award(K.JACKPOT_POINTS, 60.0, 260.0, Pal.GOLD, true)
		_show(M_JACKPOT)
		play(Sfx.JACKPOT)
		fx.haptics.jackpot()
		shake.add(0.4)
		flash.trigger(0.5)
		_burst_at(20.0, T.SPINNER_Y, 30, HIT_GOLD, 260.0)
	else:
		_award(K.ORBIT_POINTS, 40.0, T.SPINNER_Y, Pal.CYAN, true)
		_event_light(24.0, T.SPINNER_Y, Pal.CYAN)
		_show(M_ORBIT)


# ---------------------------------------------------------------- 3D presentation

## The player's view: standing at the lockdown bar, looking down the table at the backbox.
var _stage := Stage3D.new(int(GAME_W), int(GAME_H))

var _upper_light := PointLight.new(T.CX, 150.0, 150.0, 1.0, 0.85, 0.95, 420.0, 0.9)
var _lower_light := PointLight.new(T.CX, 140.0, 470.0, 0.85, 0.9, 1.0, 380.0, 0.8)
var _hit_light := PointLight.new(T.CX, 40.0, 200.0, 1.0, 1.0, 1.0, 170.0, 0.0)
## The backglass glow washing down over the top of the table, tinted by the mood.
var _back_light := PointLight.new(T.CX + 14.0, 90.0, -6.0, 1.0, 0.5, 0.8, BACK_LIGHT_R, 0.5)
var _event_point := PointLight.new(T.CX, 26.0, 200.0, 1.0, 1.0, 1.0, EVENT_LIGHT_R, 0.0)
## A soft light riding each ball, so the felt and rails near it pick up its glint.
var _ball_lights: Array[PointLight] = []
var _xf := Xform.new()

## The backbox display: a dot-matrix made on the first frame (never in a headless test).
var _dmd: PinballDisplay.PinballDmd = null
var _score_shown := -1
var _score_text := "0"


## Reduce motion turns flashes down to a third of their strength (persistent glow is unchanged).
func _fx_k() -> float:
	return 0.35 + 0.65 * ScreenShake.intensity


func render(scope: DrawScope) -> void:
	var r := _stage.begin()
	_light(r)
	r.gradient(0xFF05030C, Pal.NIGHT)
	PinballArt.table().draw(r)
	_draw_rail_glow(r)
	_draw_backbox(r)
	_draw_table_wash(r)
	_draw_bumpers(r)
	_draw_slings(r)
	_draw_targets(r)
	_draw_spinner(r)
	_draw_flipper_shadows(r)
	_draw_flippers(r)
	_draw_plunger(r)
	_draw_lamps(r)
	_draw_trails(r)
	for b in _balls:
		if b.state != OFF:
			_draw_ball(r, b)
	# The playfield glass: the room reflected in it, then a faint sheen of streaks.
	r.quad(-4.0, 30.0, -6.0, T.W + 4.0, 30.0, -6.0, T.W + 4.0, 30.0, 650.0, -4.0, 30.0, 650.0,
		TexKit.white().full(), 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.ALPHA, 0.0, 0.02, false, -1, 1.0, 1.0)
	r.quad(-4.0, 30.0, -6.0, T.W + 4.0, 30.0, -6.0, T.W + 4.0, 30.0, 650.0, -4.0, 30.0, 650.0,
		PinballArt.glass_streaks().full(), 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, 0.16, false)
	_stage.present()
	_draw_hints(scope)


## The colour the backglass and rails are giving off now: pink at rest, leaning to the last event's
## colour, cycling in multiball.
func _mood_now() -> int:
	var base := Pal.HOTPINK
	if _tilted:
		base = Pal.RED
	elif _multiball:
		base = Pal.mix(Pal.PINK, Pal.CYAN, 0.5 + 0.5 * sin(time * 3.0))
	return Pal.mix(base, _mood_color, _mood_pulse)


## Kotlin's `PointLight.rgb(argb)`.
static func _rgb(l: PointLight, argb: int) -> void:
	l.r = ((argb >> 16) & 255) / 255.0
	l.g = ((argb >> 8) & 255) / 255.0
	l.b = (argb & 255) / 255.0


func _light(r: Renderer3D) -> void:
	var l := r.lighting
	l.amb_r = 0.5
	l.amb_g = 0.46
	l.amb_b = 0.62
	l.set_direction(-0.2, 1.0, 0.5)
	l.dir_r = 0.45
	l.dir_g = 0.42
	l.dir_b = 0.4
	l.points.clear()
	l.points.append(_upper_light)
	l.points.append(_lower_light)
	_rgb(_back_light, _mood_now())
	_back_light.intensity = BACK_LIGHT_BASE + BACK_LIGHT_PULSE * _mood_pulse * _fx_k() + (0.12 if _multiball else 0.0)
	l.points.append(_back_light)
	var hot := -1
	for i in 3:
		if _bumper_flash[i] > 0.0 and (hot < 0 or _bumper_flash[i] > _bumper_flash[hot]):
			hot = i
	if hot >= 0:
		_hit_light.x = T.BUMPER_X[hot]
		_hit_light.z = T.BUMPER_Y[hot]
		_rgb(_hit_light, BUMPER_COLORS[hot])
		_hit_light.intensity = _bumper_flash[hot] * 1.4
		l.points.append(_hit_light)
	if _evt_t > 0.0:
		_event_point.x = _evt_x
		_event_point.z = _evt_z
		_rgb(_event_point, _evt_color)
		_event_point.intensity = _evt_t * EVENT_LIGHT_I * _fx_k()
		l.points.append(_event_point)
	for i in MAX_BALLS:
		var b := _balls[i]
		if b.state != PLAY:
			continue
		var bl := _ball_lights[i]
		bl.x = b.x
		bl.z = b.y
		bl.intensity = BALL_LIGHT_I
		l.points.append(bl)


## The floor strips along the outer rails and the racing stripes on the side walls, breathing with the mood.
func _draw_rail_glow(r: Renderer3D) -> void:
	var breathe := RAIL_GLOW_BASE + RAIL_GLOW_SWING * sin(time * 2.2) + RAIL_GLOW_PULSE * _mood_pulse * _fx_k() + (0.2 if _multiball else 0.0)
	var tint := Pal.mix_argb(-1, _mood_color, _mood_pulse * 0.75) if _mood_pulse > 0.05 else -1
	PinballArt.rail_glow().draw(r, -1, breathe, null, tint)


## The backbox's moving light: rays turning behind the title, a shimmer crossing the glass now and
## then, a wash in the mood colour when something happens, the dot-matrix display itself and the
## bulbs chasing round its frame. Each is one or two quads.
func _draw_backbox(r: Renderer3D) -> void:
	var z := PinballArt.BACKBOX_Z
	var mood := _mood_now()
	var cx := (PinballArt.GLASS_X0 + PinballArt.GLASS_X1) / 2.0
	# Rays: a square of the ray texture turning about the middle of the title.
	var a := time * RAY_SPIN
	var rc := cos(a) * RAY_R
	var rs := sin(a) * RAY_R
	var ry := RAY_CY
	var ray_tex := PinballArt.rays().full()
	r.quad(
		cx + (-rc - rs), ry + (-rs + rc), z + 0.35, cx + (rc - rs), ry + (rs + rc), z + 0.35,
		cx + (rc + rs), ry + (rs - rc), z + 0.35, cx + (-rc + rs), ry + (-rs - rc), z + 0.35,
		ray_tex, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.ADD, 1.0,
		RAY_ALPHA + 0.06 * sin(time * 1.7) + 0.2 * _mood_pulse * _fx_k(), false, mood, 1.02)
	# Shimmer: a soft vertical band that crosses the glass, clipped to it, every few seconds.
	var ph := fmod(time, SHIMMER_PERIOD) / SHIMMER_SECONDS
	if ph < 1.0:
		var bw := SHIMMER_W
		var bc := PinballArt.GLASS_X0 - bw * 0.5 + ph * (PinballArt.GLASS_X1 - PinballArt.GLASS_X0 + bw)
		var x0 := maxf(bc - bw / 2.0, PinballArt.GLASS_X0)
		var x1 := minf(bc + bw / 2.0, PinballArt.GLASS_X1)
		if x1 > x0:
			var tex := PinballArt.shimmer().full()
			var u0 := (x0 - (bc - bw / 2.0)) / bw * tex.w
			var u1 := (x1 - (bc - bw / 2.0)) / bw * tex.w
			r.quad(
				x0, PinballArt.GLASS_Y1, z + 0.4, x1, PinballArt.GLASS_Y1, z + 0.4,
				x1, PinballArt.GLASS_Y0, z + 0.4, x0, PinballArt.GLASS_Y0, z + 0.4,
				tex, 0.0, 0.0, 1.0, u0, 0.0, u1, float(tex.h),
				Blend.ADD, 1.0, SHIMMER_ALPHA, false, 0xFFFFE8FF, 1.02)
	# A wash over the whole glass when the mood is up.
	if _mood_pulse > 0.02:
		r.quad(
			PinballArt.GLASS_X0, PinballArt.GLASS_Y1, z + 0.45, PinballArt.GLASS_X1, PinballArt.GLASS_Y1, z + 0.45,
			PinballArt.GLASS_X1, PinballArt.GLASS_Y0, z + 0.45, PinballArt.GLASS_X0, PinballArt.GLASS_Y0, z + 0.45,
			TexKit.white().full(), 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, WASH_ALPHA * _mood_pulse * _fx_k(),
			false, _mood_color, 1.02)
	_draw_dmd(r, z)
	_draw_bulbs(r, z, mood)


func _draw_dmd(r: Renderer3D, z: float) -> void:
	if _dmd == null:
		_dmd = PinballDisplay.PinballDmd.new()
	if score != _score_shown:
		_score_shown = score
		_score_text = _group_digits(score)
	var showing := _message_t > 0.0 and _message != M_NONE
	var hot := 0
	if showing:
		if _message == M_JACKPOT:
			hot = 2
		elif _message == M_MULTIBALL:
			hot = 1
	_dmd.update(
		time, _score_text, BALL_TEXT[clampi(_ball_number, 1, 5)], MULT_TEXT[_multiplier],
		MESSAGES[_message] if showing else "", MSG_SECONDS - _message_t, MSG_SECONDS, hot,
		_multiball, _ball_save_t > 0.0 and not _multiball, _tilted)
	var tex := _dmd.matrix.texture.full()
	r.quad(
		PinballArt.DMD_X0, PinballArt.DMD_TOP, z + 0.6, PinballArt.DMD_X1, PinballArt.DMD_TOP, z + 0.6,
		PinballArt.DMD_X1, PinballArt.DMD_BOTTOM, z + 0.6, PinballArt.DMD_X0, PinballArt.DMD_BOTTOM, z + 0.6,
		tex, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, DMD_GLOW, 1.0, false, -1, 1.03)


## The score with commas ("12,340"): built only when it changes.
static func _group_digits(v: int) -> String:
	var raw := str(v)
	if raw.length() <= 3:
		return raw
	var sb := ""
	for i in raw.length():
		if i > 0 and (raw.length() - i) % 3 == 0:
			sb += ","
		sb += raw[i]
	return sb


## Bulbs round the backglass frame: a row along the top and a column down each side, chasing.
## build-13 drew each bulb's dot then its glow; both are additive, so all the dots go first and
## then all the glows (the same picture in two draws instead of one per bulb).
func _draw_bulbs(r: Renderer3D, z: float, mood: int) -> void:
	var fast := _multiball or _jackpot_flash > 0.0
	var step := int(time * (BULB_FAST if fast else BULB_SLOW))
	var bz := z + 2.4
	var top := PinballArt.GLASS_Y1 + 4.0
	var n := 14
	var dot := TexKit.dot().full()
	var glow := TexKit.glow().full()
	for pass_i in 2:
		var index := 0
		for i in n:
			_bulb(r, PinballArt.GLASS_X0 - 8.0 + (PinballArt.GLASS_X1 - PinballArt.GLASS_X0 + 16.0) * i / (n - 1.0), top, bz, step, index, pass_i, dot, glow)
			index += 1
		for i in 6:
			var y := PinballArt.GLASS_Y1 - 14.0 - i * 27.0
			_bulb(r, PinballArt.GLASS_X0 - 8.0, y, bz, step, index, pass_i, dot, glow)
			index += 1
			_bulb(r, PinballArt.GLASS_X1 + 8.0, y, bz, step, index, pass_i, dot, glow)
			index += 1
	# A pool of the mood colour on the top of the table, as though the glass were lighting it.
	r.flat(T.CX + 14.0, 56.0, 0.4, 320.0, 130.0, glow, 0.0, Blend.ADD, 1.0, 0.06 + 0.10 * _mood_pulse * _fx_k(), mood)


## Bulb number [param index] of the frame: its dot on pass 0, its glow (when on) on pass 1.
func _bulb(r: Renderer3D, x: float, y: float, z: float, step: int, index: int, pass_i: int, dot: Region, glow: Region) -> void:
	var on := (index + step) % 3 == 0
	var col := Pal.GOLD
	if _jackpot_flash <= 0.0:
		var k := index % 3
		col = Pal.HOTPINK if k == 0 else (Pal.CYAN if k == 1 else Pal.YELLOW)
	if pass_i == 0:
		r.sprite(x, y, z, 7.0, 7.0, dot, 0.0, Blend.ADD, 1.0, 1.0 if on else BULB_DIM, 1.03, col)
	elif on:
		r.sprite(x, y, z, 19.0, 19.0, glow, 0.0, Blend.ADD, 1.0, 0.55, 1.03, col)


## A faint colour over the whole upper playfield in multiball and after a jackpot, like the room lights swinging.
func _draw_table_wash(r: Renderer3D) -> void:
	if not _multiball and _jackpot_flash <= 0.0:
		return
	var col := Pal.GOLD if _jackpot_flash > 0.0 else Pal.mix(Pal.PINK, Pal.CYAN, 0.5 + 0.5 * sin(time * 3.0))
	r.flat(T.CX, 200.0, 0.45, 300.0, 420.0, TexKit.glow().full(), 0.0, Blend.ADD, 1.0, (0.05 + 0.07 * _jackpot_flash) * _fx_k(), col)


## build-13 drew each bumper's glow then its shock ring; both are additive, so the three glows go
## first and then the rings (the same picture, fewer draws).
func _draw_bumpers(r: Renderer3D) -> void:
	var glow := TexKit.glow().full()
	var ring := PinballArt.ring_glow().full()
	for i in 3:
		var x: float = T.BUMPER_X[i]
		var z: float = T.BUMPER_Y[i]
		var f := _bumper_flash[i]
		var pop := 1.0 - f * 0.25
		_xf.set_xf(x, 0.0, z)
		PinballArt.bumper_base().draw(r, -1, 1.0, _xf)
		_xf.set_xf(x, 0.0, z).stretch(1.0, pop, 1.0)
		PinballArt.bumper_cap().draw(r, -1, 1.35 + 0.15 * sin(time * 4.0 + i * 2.0) + f * 1.5, _xf, BUMPER_COLORS[i])
		var a := 0.18 + 0.1 * sin(time * 3.0 + i) + f * 0.7
		r.flat(x, z, 1.0, 70.0, 70.0, glow, 0.0, Blend.ADD, 1.0, a, BUMPER_COLORS[i])
	for i in 3:
		# A shock wave leaves the bumper on every hit: a ring that swells and fades.
		var f := _bumper_flash[i]
		if f > 0.02:
			var e := 1.0 - f
			var d := 2.0 * (T.BUMPER_R + 4.0) + e * SHOCK_GROW
			r.flat(T.BUMPER_X[i], T.BUMPER_Y[i], 1.3, d, d, ring, 0.0, Blend.ADD, 1.0, f * SHOCK_ALPHA, BUMPER_COLORS[i])


func _draw_slings(r: Renderer3D) -> void:
	var glow := TexKit.glow().full()
	for i in 2:
		var f := _sling_flash[i]
		var sx := (T.SLING_AX + T.SLING_CX) / 2.0 if i == 0 else T.PLAY_W - (T.SLING_AX + T.SLING_CX) / 2.0
		var sy := (T.SLING_AY + T.SLING_CY) / 2.0
		r.flat(sx, sy, 12.5, 40.0, 70.0, glow, 0.0, Blend.ADD, 1.0, 0.25 + f * 0.75, Pal.LIME)
		# The kicking face flares along its length when it fires.
		if f > 0.02:
			var ax := T.SLING_AX if i == 0 else T.PLAY_W - T.SLING_AX
			var cx := T.SLING_CX if i == 0 else T.PLAY_W - T.SLING_CX
			r.beam(cx, 12.6, T.SLING_CY, ax, 12.6, T.SLING_AY, SLING_BEAM_W, glow, Blend.ADD, 1.0, f, Pal.LIME)


func _draw_targets(r: Renderer3D) -> void:
	var glow := TexKit.glow().full()
	for i in 3:
		var y0: float = T.DROP_Y0[i]
		var sink := _drop_sink[i]
		if sink < 1.0:
			_xf.set_xf(T.DROP_X, -sink * 15.0, y0 + T.DROP_LEN / 2.0)
			PinballArt.drop_target().draw(r, -1, 1.0, _xf, DROP_COLORS[i])
		# The lamp in front of each target: lit while it's still standing.
		var lit := _drop_up[i]
		var a := 0.45 + 0.2 * sin(time * 6.0 + i) if lit else 0.08
		var s := 18.0 + _drop_flash[i] * 30.0
		r.flat(T.DROP_X - 22.0, y0 + T.DROP_LEN / 2.0, 0.6, s, s, glow, 0.0, Blend.ADD, 1.0, a + _drop_flash[i], DROP_COLORS[i])


func _draw_spinner(r: Renderer3D) -> void:
	# A flat plate on a wire across the orbit, turning about the wire.
	var c := cos(_spin_angle)
	var s := sin(_spin_angle)
	var h := 7.0
	var y := 11.0
	var tex := PinballArt.spinner_plate().full()
	r.quad(
		1.0, y + c * h, T.SPINNER_Y + s * h, T.ORBIT_X - 1.0, y + c * h, T.SPINNER_Y + s * h,
		T.ORBIT_X - 1.0, y - c * h, T.SPINNER_Y - s * h, 1.0, y - c * h, T.SPINNER_Y - s * h,
		tex, 0.0, -s, c, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, 1.0, false, -1, 1.0, 0.8)
	r.beam(0.0, y, T.SPINNER_Y, T.ORBIT_X, y, T.SPINNER_Y, 1.4, TexKit.white().full(), Blend.OPAQUE, 0.0, 1.0, Pal.LIGHTGRAY)
	# A blur of light while it spins fast.
	if _spin_speed > 6.0:
		r.flat(T.ORBIT_X / 2.0, T.SPINNER_Y, y, T.ORBIT_X - 2.0, 16.0, TexKit.glow().full(), 0.0, Blend.ADD, 1.0, minf(_spin_speed / 40.0, 0.5), Pal.CYAN)


## A soft shadow under each flipper, turning with it (the light is high and a little behind).
func _draw_flipper_shadows(r: Renderer3D) -> void:
	var shadow := TexKit.shadow().full()
	for s in 2:
		var a := _flip_ang[s]
		var mx := T.flip_x(s) + cos(a) * T.FLIP_LEN / 2.0 + 3.0
		var mz := T.FLIP_Y + sin(a) * T.FLIP_LEN / 2.0 + 5.0
		r.flat(mx, mz, 0.3, T.FLIP_LEN + 26.0, 28.0, shadow, a, Blend.ALPHA, 0.0, 0.5)


func _draw_flippers(r: Renderer3D) -> void:
	for s in 2:
		_xf.set_xf(T.flip_x(s), 0.0, T.FLIP_Y, -_flip_ang[s])
		PinballArt.flipper().draw(r, -1, 1.0, _xf)


func _draw_plunger(r: Renderer3D) -> void:
	var back := _plunger_vis * 34.0
	_xf.set_xf(T.LANE_X, 7.0, T.LANE_REST_Y + T.BALL_R + back)
	PinballArt.plunger().draw(r, -1, 1.0, _xf)


func _draw_lamps(r: Renderer3D) -> void:
	var glow := TexKit.glow().full()
	var blink := int(time * 4.0) % 2 == 0
	# Top lane inserts.
	for i in 3:
		var lit := _lane_lit[i]
		var a := (0.75 if lit else 0.06) + _lane_flash[i] * 0.8
		r.flat(T.ROLLOVER_X[i], T.ROLLOVER_Y + 34.0, 0.6, 30.0, 30.0, glow, 0.0, Blend.ADD, 1.0, a, Pal.SKY)
	# Multiplier row.
	for k in 4:
		var lit := _multiplier >= k + 2
		var x := T.CX - 54.0 + k * 36.0
		r.flat(x, 372.0, 0.6, 34.0, 34.0, glow, 0.0, Blend.ADD, 1.0, 0.85 if lit else 0.05, Pal.GOLD)
	# Orbit arrow: jackpot when blinking gold.
	var orbit_lit := _multiball and blink
	r.flat(22.0, 350.0, 0.6, 40.0, 40.0, glow, 0.0, Blend.ADD, 1.0, 0.9 if orbit_lit else 0.12 + _jackpot_flash, Pal.GOLD if _multiball else Pal.CYAN)
	# Shoot again (ball save).
	var save_on := _ball_save_t > 0.0 and (_ball_save_t > 2.0 or blink)
	r.flat(T.CX, 568.0, 0.6, 34.0, 34.0, glow, 0.0, Blend.ADD, 1.0, 0.9 if save_on else 0.06, Pal.RED)


## The light a fast ball drags behind it: a tapering ribbon over the last few hundredths of a second.
func _draw_trails(r: Renderer3D) -> void:
	var glow := TexKit.glow().full()
	for i in MAX_BALLS:
		var b := _balls[i]
		if b.state != PLAY:
			continue
		var speed := sqrt(b.vx * b.vx + b.vy * b.vy)
		var k := clampf((speed - TRAIL_MIN_SPEED) / TRAIL_FULL_SPEED, 0.0, 1.0)
		if k <= 0.0:
			continue
		var n := _trail_count[i]
		var o := i * TRAIL_N
		var col := 0xFFCFE0FF
		if _multiball:
			col = Pal.HOTPINK if i % 2 == 0 else Pal.CYAN
		var px := b.x
		var pz := b.y
		for j in n:
			var tx := _trail_x[o + j]
			var tz := _trail_y[o + j]
			var t := 1.0 - j / float(n)
			r.beam(px, TRAIL_Y, pz, tx, TRAIL_Y, tz, TRAIL_WIDTH * t, glow, Blend.ADD, 1.0, TRAIL_ALPHA * k * t, col)
			px = tx
			pz = tz


func _draw_ball(r: Renderer3D, b: Ball) -> void:
	# A draining ball drops under the apron.
	var sink := (b.y - T.APRON_Y) * 0.6 if b.y > T.APRON_Y else 0.0
	var y := T.BALL_R - sink
	if sink < 20.0:
		r.flat(b.x + 3.0, b.y + 4.0, 0.4, 24.0, 24.0, TexKit.shadow().full(), 0.0, Blend.ALPHA, 0.0, 0.55)
	_xf.set_xf(b.x, y, b.y)
	PinballArt.ball().draw(r, -1, 1.0, _xf)
	r.sprite(b.x - 2.5, y + 4.0, b.y - 1.0, 7.0, 7.0, TexKit.glow().full(), 0.0, Blend.ADD, 1.0, 0.9)


func _draw_hints(scope: DrawScope) -> void:
	if time_up:
		return
	var a := 0.5 + 0.5 * sin(time * 6.0)
	if _lane_ball() >= 0 and _plunger_id < 0:
		var p: Variant = _stage.to_field(T.LANE_X, 0.0, 600.0)
		if p != null:
			ArcadeFont.draw_centered(scope, PULL_TEXT, p.x - 6.0, p.y - 64.0, 1.8, Pal.WHITE, a)
	if time < 6.0:
		ArcadeFont.draw_centered(scope, HOLD_LEFT, 128.0, 562.0, 1.8, Pal.CYAN, a)
		ArcadeFont.draw_centered(scope, HOLD_RIGHT, 232.0, 562.0, 1.8, Pal.CYAN, a)
	if _tilted:
		ArcadeFont.draw_centered(scope, "TILT", GAME_W / 2.0, 300.0, 6.0, Pal.RED, 1.0 if int(time * 5.0) % 2 == 0 else 0.3)


# ---------------------------------------------------------------- simulation-test hooks

func bot_max_balls() -> int:
	return MAX_BALLS


func bot_ball_in_play(i: int) -> bool:
	return _balls[i].state == PLAY


func bot_ball_in_lane(i: int) -> bool:
	return _balls[i].state == LANE


func bot_ball_x(i: int) -> float:
	return _balls[i].x


func bot_ball_y(i: int) -> float:
	return _balls[i].y


func bot_ball_vx(i: int) -> float:
	return _balls[i].vx


func bot_ball_vy(i: int) -> float:
	return _balls[i].vy


## A ball waits on the plunger for the player.
func bot_lane_ready() -> bool:
	return _lane_ball() >= 0


func bot_lane_ball_index() -> int:
	return _lane_ball()


func bot_live_balls() -> int:
	return _live_balls()


func bot_flipper_held(side: int) -> bool:
	return _flipper_held(side)


func bot_flipper_angle(side: int) -> float:
	return _flip_ang[side]


func bot_pull() -> float:
	return _pull


func bot_plunger_held() -> bool:
	return _plunger_id >= 0


func bot_last_launch_speed() -> float:
	return _last_launch_speed


func bot_launches() -> int:
	return _launches


func bot_balls_left() -> int:
	return _balls_left


func bot_drains() -> int:
	return _drains


func bot_failsafe_trips() -> int:
	return _failsafe_trips


## Kicks the stuck-ball failsafe gave a crawling ball this round.
func bot_search_kicks() -> int:
	return _search_kicks_total


func bot_bumper_hits() -> int:
	return _bumper_hits


func bot_multiballs() -> int:
	return _multiballs


func bot_jackpots() -> int:
	return _jackpots


func bot_multiplier() -> int:
	return _multiplier


func bot_tilted() -> bool:
	return _tilted


func bot_ball_save_left() -> float:
	return _ball_save_t


func bot_drops_up() -> int:
	return (1 if _drop_up[0] else 0) + (1 if _drop_up[1] else 0) + (1 if _drop_up[2] else 0)


func bot_lane_lit(i: int) -> bool:
	return _lane_lit[i]


func bot_ended_early() -> bool:
	return ended_early


## Every string the backbox display can be asked to show (its messages, ball and multiplier texts),
## for the font test.
func bot_dmd_strings() -> PackedStringArray:
	var out := PackedStringArray()
	for m in MESSAGES:
		if not m.is_empty():
			out.append(m)
	for b in BALL_TEXT:
		out.append(b)
	for m in MULT_TEXT:
		if not m.is_empty():
			out.append(m)
	return out


## Puts ball [param i] in play at ([param x], [param y]) moving at ([param vx], [param vy]),
## bypassing the plunger.
func bot_place(i: int, x: float, y: float, vx: float, vy: float) -> void:
	var b := _balls[i]
	b.state = PLAY
	b.x = x
	b.y = y
	b.vx = vx
	b.vy = vy
	b.px = x
	b.py = y
	b.auto_t = -1.0
	b.slow_t = 0.0
	b.search_kicks = 0


## Holds ball [param i] still at ([param x], [param y]) without touching the failsafe's counters
## (a trap no kick frees).
func bot_hold(i: int, x: float, y: float) -> void:
	var b := _balls[i]
	b.x = x
	b.y = y
	b.vx = 0.0
	b.vy = 0.0
	b.px = x
	b.py = y


## Takes ball [param i] off the table without any rule firing (test setup).
func bot_remove(i: int) -> void:
	_balls[i].state = OFF


## Stops the ball save so a drain in a test counts.
func bot_end_ball_save() -> void:
	_ball_save_t = 0.0
	_ball_save_armed = false


# ---------------------------------------------------------------- attract mode

func draw_attract(p: Painter, w: int, h: int, at: float) -> void:
	# The backglass's screen: a night sky over a neon horizon, the title card (then a flashing
	# "multiball", then "insert token") and, underneath, a ball zig-zagging among three bumpers
	# that flare when it passes, with a light trail behind it.
	var u := h / 30.0
	var wf := float(w)
	var hf := float(h)
	var amber := 0xFFFF8A1C
	var dim := 0xFF4A2206
	p.fill(0.0, 0.0, wf, hf, 0xFF0A041C)
	p.fill(0.0, hf * 0.22, wf, hf * 0.20, 0xFF160A34)
	p.fill(0.0, hf * 0.42, wf, hf * 0.16, 0xFF26104A)
	p.fill(0.0, hf * 0.58, wf, hf * 0.42, 0xFF130727)
	# The horizon's glow, and a neon line along it.
	p.disc(wf / 2.0, hf * 0.62, wf * 0.55, Pal.PINK, 0.08)
	p.disc(wf / 2.0, hf * 0.62, wf * 0.34, Pal.PINK, 0.10)
	p.fill(0.0, hf * 0.585, wf, 0.35 * u, Pal.HOTPINK, 0.75)
	# Twinkling stars in the sky.
	for i in 16:
		var sx := MathUtil.hash01(i, 11) * wf
		var sy := MathUtil.hash01(i, 12) * hf * 0.55
		var tw := 0.35 + 0.65 * absf(sin(at * (0.9 + MathUtil.hash01(i, 13)) + i * 1.7))
		p.px(sx, sy, Pal.CREAM, tw)
	var phase := fmod(at, 10.0)
	var cx := wf / 2.0
	if phase < 6.0:
		# Title card: each word with a dark extrusion behind it.
		p.text_centered("STAR", cx + 0.55 * u, 1.3 * u + 0.5 * u, 0xFF7A1F63, false, 1.0, 0.85 * u)
		p.text_centered("STAR", cx, 1.3 * u, Pal.YELLOW, false, 1.0, 0.85 * u)
		p.text_centered("FLIPPER", cx + 0.45 * u, 8.2 * u + 0.4 * u, 0xFF1A6A78, false, 1.0, 0.7 * u)
		p.text_centered("FLIPPER", cx, 8.2 * u, Pal.CREAM, false, 1.0, 0.7 * u)
		# A gleam sweeping across the underline.
		var gx := (fmod(at * 0.7, 1.4) - 0.2) * wf
		p.fill(cx - 12.0 * u, 13.2 * u, 24.0 * u, 0.3 * u, Pal.CYAN, 0.35)
		p.fill(clampf(gx - 2.5 * u, cx - 12.0 * u, cx + 12.0 * u), 13.2 * u, 5.0 * u, 0.3 * u, Pal.WHITE, 0.9)
	elif phase < 8.0:
		var on := int(at * 4.0) % 2 == 0
		p.text_centered("MULTI", cx, 2.2 * u, Pal.HOTPINK if on else Pal.CYAN, false, 1.0, 1.1 * u)
		p.text_centered("BALL!", cx, 10.6 * u, Pal.CYAN if on else Pal.HOTPINK, false, 1.0, 1.1 * u)
	else:
		var on := int(at * 2.5) % 2 == 0
		p.text_centered("INSERT", cx, 2.6 * u, Pal.CREAM, false, 1.0, 0.95 * u)
		p.text_centered("TOKEN", cx, 10.6 * u, Pal.GOLD if on else amber, false, 1.0, 0.95 * u)
	# Three bumpers; the ball's path decides when each one flares.
	var a := at * 2.4
	var ball_x := cx + sin(a) * wf * 0.40
	var ball_y := 21.6 * u + cos(a * 1.7) * 4.8 * u
	for i in 3:
		var bxi := wf * (0.26 if i == 0 else (0.74 if i == 1 else 0.5))
		var byi := (19.0 if i < 2 else 24.0) * u
		var col := Pal.HOTPINK if i == 0 else (Pal.CYAN if i == 1 else Pal.YELLOW)
		var d := sqrt((ball_x - bxi) * (ball_x - bxi) + (ball_y - byi) * (ball_y - byi))
		var flare := clampf(1.0 - (d - 2.4 * u) / (3.6 * u), 0.0, 1.0)
		p.disc(bxi, byi, (3.6 + flare * 1.8) * u, col, 0.14 + flare * 0.45)
		p.disc(bxi, byi, 2.5 * u, 0xFF2A2436)
		p.disc(bxi, byi, 1.9 * u, col, 0.55 + flare * 0.45)
		p.disc(bxi, byi, 0.8 * u, Pal.WHITE, 0.5 + flare * 0.5)
	var k := 5
	while k >= 1:
		var ta := (at - k * 0.045) * 2.4
		var tx := cx + sin(ta) * wf * 0.40
		var ty := 21.6 * u + cos(ta * 1.7) * 4.8 * u
		p.disc(tx, ty, (1.05 - k * 0.1) * u, 0xFFCFE0FF, 0.5 * (1.0 - k / 6.0))
		k -= 1
	p.disc(ball_x, ball_y, 1.05 * u, 0xFFDDE4F8)
	p.disc(ball_x - 0.3 * u, ball_y - 0.35 * u, 0.35 * u, Pal.WHITE)
	# Chase lamps along the top and bottom edges, running in opposite directions.
	var n := 10
	for j in n:
		var on_b := (int(at * 8.0) + j) % 4 == 0
		var on_t := (int(at * 8.0) - j + 40) % 4 == 0
		p.disc(wf * (j + 0.5) / n, hf - 1.0 * u, 0.65 * u, amber if on_b else dim)
		p.disc(wf * (j + 0.5) / n, 0.9 * u, 0.5 * u, amber if on_t else dim, 0.7)
