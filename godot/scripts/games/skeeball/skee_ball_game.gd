class_name SkeeBallGame
extends BaseMiniGame
## games/skeeball/SkeeBallGame.kt: roll balls up the lane, hop the ramp and drop them into the
## scoring rings of the tilted board. The tuning knobs are in [SkeeTuning].

const LANE_L := 60.0
const LANE_R := 300.0
const RAMP_Y := 318.0
const RAMP_TOP := 290.0
const BOARD_TOP := 30.0
const BOARD_BOTTOM := 252.0
const CX := 180.0
const CY := 146.0
const SQUASH := 0.78
const BONUS_X := 318.0
const BONUS_Y := 60.0
const BONUS_R := 13.0
const BALL_R := 14.0
const REST_X := 180.0
const REST_Y := 586.0
const GRAB_TOP := 430.0

# 3D alley layout (world units = field units).
const BOARD_L := 20.0
const BOARD_R := 340.0
const LANE_END := 780.0
const RAMP_H := 10.0
const BOARD_BASE := 16.0
## tan of the board's tilt; its cosine is SQUASH, which turns the ring ellipses into circles.
const BOARD_TAN := 0.802
const PIT_Y := -40.0
const RAIL_H := 20.0
const SIGN_TOP := 300.0
const SIDE_BACK_Z := 10.0
const SIDE_FRONT_Z := 298.0
const SIDE_BACK_Y := 340.0
const SIDE_FRONT_Y := 100.0

# ---- Look (presentation only): these change how the alley looks, never how it plays.

## Brightness above which things bloom; a touch over the default so the pale lane and rails stay calm.
const BLOOM_THRESHOLD := 0.66
## Gloss of the varnished lane and of the blue rails.
const LANE_GLOSS := 0.32
const RAIL_GLOSS := 0.5
## How many past positions a ball's trail keeps, how often it takes one, how long each lives (s), how bright the trail is.
const TRAIL_LEN := 10
const TRAIL_STEP := 0.02
const TRAIL_LIFE := 0.26
const TRAIL_ALPHA := 0.45
## Score impacts drawn at once, how long each ring takes to cross the board (s).
const IMPACTS := 6
const IMPACT_LIFE := 0.75
## Seconds between the quiet pulses that travel out over the board, and how bright they are.
const PULSE_PERIOD := 3.4
const PULSE_ALPHA := 0.16
## Strength of the work light that rides with a rolling ball.
const BALL_LIGHT := 0.5
## Seconds between sheens sweeping across the marquee.
const SWEEP_PERIOD := 5.5

# Attract loop: seconds per throw, and which ring each throw drops into (index into the six rings) with its label.
const ATTRACT_LOOP := 3.4
const ATTRACT_TARGETS := [3, 1, 5, 2, 4]
const ATTRACT_LABELS := ["10", "20", "30", "40", "50", "100"]

enum Phase { ROLLING, FLYING, SETTLING, GUTTER }


class Ball:
	extends RefCounted
	var x := 0.0
	var y := 0.0
	var z := 0.0
	var vx := 0.0
	var vy := 0.0
	var vz := 0.0
	var spin := 0.0
	var phase: int = Phase.ROLLING
	var t := 0.0
	var from_x := 0.0
	var from_y := 0.0
	var to_x := 0.0
	var to_y := 0.0
	var points := 0
	var ring := -1
	var active := false
	## Seconds since the roll started, whatever the phase (for the failsafe).
	var age := 0.0

	# Where the ball has been, for its trail: positions and the age each was taken at. Looks only;
	# nothing in the rules reads them.
	var trail_x := PackedFloat32Array()
	var trail_y := PackedFloat32Array()
	var trail_z := PackedFloat32Array()
	var trail_at := PackedFloat32Array()
	var trail_head := 0
	var trail_count := 0
	var trail_clock := 0.0

	func _init() -> void:
		trail_x.resize(TRAIL_LEN)
		trail_y.resize(TRAIL_LEN)
		trail_z.resize(TRAIL_LEN)
		trail_at.resize(TRAIL_LEN)

	func clear_trail() -> void:
		trail_head = 0
		trail_count = 0
		trail_clock = 0.0

	## Notes the current spot once every TRAIL_STEP seconds.
	func sample_trail(dt: float) -> void:
		trail_clock += dt
		if trail_clock < TRAIL_STEP:
			return
		trail_clock = 0.0
		trail_x[trail_head] = x
		trail_y[trail_head] = y
		trail_z[trail_head] = z
		trail_at[trail_head] = age
		trail_head = (trail_head + 1) % TRAIL_LEN
		if trail_count < TRAIL_LEN:
			trail_count += 1

	## Kotlin's `stepBall(b, dt)`, run as the ball's own method: GDScript reads an object's own
	## fields several times faster than another object's, and this is the alley's hot loop. The
	## rules are the game's, unchanged; [param game] plays the sounds and takes the gutter, landing
	## and scoring events.
	func advance(dt: float, game: SkeeBallGame) -> void:
		t += dt
		age += dt
		if phase == Phase.ROLLING or phase == Phase.FLYING:
			sample_trail(dt)
		if age > SkeeTuning.BALL_TIMEOUT and phase != Phase.GUTTER:
			game.failsafe_trips += 1
			game._gutter(self)
		match phase:
			Phase.ROLLING:
				var sp := sqrt(vx * vx + vy * vy)
				if sp > 0.0:
					var ns := maxf(sp - SkeeTuning.ROLL_FRICTION * dt, 0.0)
					vx *= ns / sp
					vy *= ns / sp
				x += vx * dt
				y += vy * dt
				spin += sp * dt / BALL_R
				if x < LANE_L + BALL_R:
					x = LANE_L + BALL_R
					vx = absf(vx) * 0.6
					game.play(Sfx.BOUNCE, 0.3, 1.5)
				if x > LANE_R - BALL_R:
					x = LANE_R - BALL_R
					vx = -absf(vx) * 0.6
					game.play(Sfx.BOUNCE, 0.3, 1.5)
				if y <= RAMP_Y:
					var planar := sqrt(vx * vx + vy * vy)
					if vy < 0.0 and planar > 60.0:
						phase = Phase.FLYING
						vz = planar * SkeeTuning.LAUNCH_RATIO
						game.play(Sfx.SWISH, 0.3, 0.7)
					else:
						vy = absf(vy) + 40.0
				elif vy >= 0.0 and sqrt(vx * vx + vy * vy) < 200.0:
					# Too slow to reach the ramp: it rolls back down the lane. This takes any ball
					# that isn't heading up the lane, including one that friction stopped dead (vy
					# is 0 or -0) or that only slides across it after bouncing off a wall.
					vy += 300.0 * dt
				if y > MiniGame.GAME_H + BALL_R:
					game._gutter(self)
			Phase.FLYING:
				x += vx * dt
				y += vy * dt
				z += vz * dt
				vz -= SkeeTuning.FLIGHT_GRAVITY * dt
				spin += sqrt(vx * vx + vy * vy) * dt / BALL_R
				if x < 20.0 + BALL_R:
					x = 20.0 + BALL_R
					vx = absf(vx) * 0.5
				if x > MiniGame.GAME_W - 20.0 - BALL_R:
					x = MiniGame.GAME_W - 20.0 - BALL_R
					vx = -absf(vx) * 0.5
				if y < BOARD_TOP + BALL_R:
					# Hit the back wall: drop straight down into the outer ring.
					y = BOARD_TOP + BALL_R
					vy = absf(vy) * 0.3
					game.play(Sfx.THUD, 0.6)
				if z <= 0.0:
					game._land(self)
			Phase.SETTLING:
				var k := clampf(t / 0.45, 0.0, 1.0)
				var u := 1.0 - k
				var e := 1.0 - u * u * u
				x = from_x + (to_x - from_x) * e
				y = from_y + (to_y - from_y) * e
				z = absf(sin(k * PI * 2.0)) * 14.0 * (1.0 - k)
				if t >= 0.5:
					game._score(self)
			Phase.GUTTER:
				if t > 0.2:
					active = false


var balls: Array[Ball] = []
var ready_x := REST_X
var ready_y := REST_Y
var has_ready := true
var reload_t := 0.0
var dragging := -1
var flick := FlickTracker.new()
var ring_flash := PackedFloat32Array()
# Impacts on the board: where a ball came to rest, in what colour, and how long ago (looks only).
var impact_x := PackedFloat32Array()
var impact_y := PackedFloat32Array()
var impact_color := PackedInt64Array()
var impact_age := PackedFloat32Array()
var impact_big: Array[bool] = []
var impact_next := 0
## 0..1 excitement of the marquee: jumps on big scores and cools off.
var marquee_boost := 0.0
var last_speed := 0.0
var speed_show_t := 0.0
var ready_squash := Spring.new()
var gutters := 0
var failsafe_trips := 0

## The alley in 3D. The simulation stays top-down (x across, y up the lane, z height); the world
## uses x as is, the lane's y as depth and adds the surface height: flat lane, the jump ramp, the
## pit, then the target board tilted back so the rings read as circles.
var stage := Stage3D.new(int(MiniGame.GAME_W), int(MiniGame.GAME_H))
## Kotlin's `pt` out-array: the last projected point (field x, y, depth) or plane hit (x, z).
var pt := PackedFloat32Array([0.0, 0.0, 0.0])

var _board_tex: PaTexture = null
var _lane_tex: PaTexture = null
var _ring_walls: Model = null
var _ball_model: Model = null
var _ball_xf := Xform.new()

var board_light := PointLight.new(CX, 330.0, 70.0, 1.0, 0.95, 1.05, 520.0, 1.1)
var lane_light := PointLight.new(CX, 150.0, 470.0, 1.0, 0.8, 0.6, 420.0, 0.7)
var marquee_light := PointLight.new(CX, 260.0, 40.0, 1.0, 0.35, 0.55, 260.0, 0.8)
var ring_light := PointLight.new(CX, 150.0, CY, 1.0, 1.0, 1.0, 260.0, 0.0)
## A warm light that rides with the ball on the lane (the waiting ball's is faint).
var ball_light := PointLight.new(CX, 40.0, 400.0, 1.0, 0.6, 0.38, 140.0, 0.0)


func _init() -> void:
	id = "skeeball"
	title = "SKEE-BALL"
	marquee = "SKEE"
	instructions = PackedStringArray([
		"DRAG THE BALL TO AIM",
		"FLICK UP TO ROLL",
		"FASTER FLICK = LONGER JUMP",
		"HIT THE CENTER FOR 100",
		"CORNER HOLE = 200 BONUS!",
	])
	look = MiniGame.CabinetLook.new(Pal.BLUE, Pal.YELLOW, Pal.SKY, MiniGame.CabinetShape.SKEEBALL)
	round_seconds = SkeeTuning.ROUND_SECONDS
	for i in 6:
		balls.append(Ball.new())
	ring_flash.resize(7)
	impact_x.resize(IMPACTS)
	impact_y.resize(IMPACTS)
	impact_color.resize(IMPACTS)
	impact_age.resize(IMPACTS)
	impact_age.fill(IMPACT_LIFE)
	for i in IMPACTS:
		impact_big.append(false)
	stage.look(CX, 300.0, 1000.0, CX, 40.0, 300.0, 44.0)
	stage.r.bloom_threshold = BLOOM_THRESHOLD


func reset() -> void:
	for b in balls:
		b.active = false
	ready_x = REST_X
	ready_y = REST_Y
	has_ready = true
	reload_t = 0.0
	dragging = -1
	ring_flash.fill(0.0)
	impact_age.fill(IMPACT_LIFE)
	marquee_boost = 0.0
	speed_show_t = 0.0
	ready_squash.snap(1.0)
	gutters = 0
	failsafe_trips = 0


func tickets_for(p_score: int) -> int:
	return SkeeTuning.BASE_TICKETS + p_score / SkeeTuning.POINTS_PER_TICKET


func is_settled() -> bool:
	for b in balls:
		if b.active:
			return false
	return true


func on_time_up() -> void:
	cancel_input()


## Drops the ball being dragged; it eases back to its resting spot.
func cancel_input() -> void:
	dragging = -1


## Kotlin's `stage.toField(x, y, z, pt)`: projects into [member pt] (left as it was when the point
## is behind the eye) and says whether it projected.
func _to_field(x: float, y: float, z: float) -> bool:
	var v: Variant = stage.to_field(x, y, z)
	if v == null:
		return false
	var p: Vector3 = v
	pt[0] = p.x
	pt[1] = p.y
	pt[2] = p.z
	return true


## Kotlin's `stage.touchToPlane(x, y, planeY, pt)`: the hit's (x, z) into pt[0], pt[1].
func _touch_to_plane(x: float, y: float, plane_y: float) -> bool:
	var v: Variant = stage.touch_to_plane(x, y, plane_y)
	if v == null:
		return false
	var p: Vector2 = v
	pt[0] = p.x
	pt[1] = p.y
	return true


func on_touch(type: int, pointer_id: int, x: float, y: float, time_ms: int) -> void:
	if type == TouchType.DOWN:
		if dragging < 0 and has_ready and not time_up and y > GRAB_TOP:
			dragging = pointer_id
			flick.reset(x, y, time_ms)
			ready_squash.kick(-3.0)
			play(Sfx.BLIP, 0.4, 0.8)
	elif type == TouchType.MOVE:
		if pointer_id == dragging:
			flick.add(x, y, time_ms)
			# The ball follows the finger across the lane.
			if _touch_to_plane(x, y, 0.0):
				ready_x = clampf(pt[0], LANE_L + BALL_R, LANE_R - BALL_R)
				ready_y = clampf(pt[1], GRAB_TOP + 60.0, MiniGame.GAME_H - 30.0)
	elif type == TouchType.UP:
		if pointer_id == dragging:
			flick.add(x, y, time_ms)
			dragging = -1
			_try_roll()


func _try_roll() -> void:
	var v := flick.velocity()
	var up := -v.y
	if up < SkeeTuning.MIN_FLICK or time_up:
		return
	var speed := clampf(MathUtil.len2(v.x, v.y) * SkeeTuning.FLICK_TO_SPEED, SkeeTuning.MIN_SPEED, SkeeTuning.MAX_SPEED)
	var max_a := SkeeTuning.MAX_ANGLE_DEG * (PI / 180.0)
	var angle := clampf(atan2(v.x, up), -max_a, max_a)
	if not _launch(ready_x, ready_y, speed, angle):
		return
	has_ready = false
	reload_t = SkeeTuning.RELOAD_SECONDS
	last_speed = speed
	speed_show_t = 1.6
	play(Sfx.ROLL, 0.8, 0.8 + speed / 1500.0)
	fx.haptics.tick()


## Sets a free ball rolling from (x, y) at [param speed], [param angle] radians off straight up the lane.
func _launch(x: float, y: float, speed: float, angle: float) -> bool:
	var b: Ball = null
	for c in balls:
		if not c.active:
			b = c
			break
	if b == null:
		return false
	b.active = true
	b.phase = Phase.ROLLING
	b.x = x
	b.y = y
	b.z = 0.0
	b.vx = sin(angle) * speed
	b.vy = -cos(angle) * speed
	b.vz = 0.0
	b.t = 0.0
	b.age = 0.0
	b.spin = 0.0
	b.clear_trail()
	return true


func step(dt: float) -> void:
	ready_squash.update(dt)
	speed_show_t -= dt
	for i in ring_flash.size():
		ring_flash[i] = maxf(ring_flash[i] - dt * 2.0, 0.0)
	for i in IMPACTS:
		if impact_age[i] < IMPACT_LIFE:
			impact_age[i] += dt
	marquee_boost = maxf(marquee_boost - dt * 0.6, 0.0)
	if not has_ready:
		reload_t -= dt
		if reload_t <= 0.0 and not time_up:
			has_ready = true
			ready_x = REST_X
			ready_y = REST_Y
			ready_squash.snap(0.6)
			play(Sfx.THUD, 0.4, 1.4)
	elif dragging < 0:
		ready_x = MathUtil.damp(ready_x, REST_X, 8.0, dt)
		ready_y = MathUtil.damp(ready_y, REST_Y, 8.0, dt)
	for b in balls:
		if b.active:
			b.advance(dt, self)


func _gutter(b: Ball) -> void:
	if b.phase == Phase.GUTTER:
		return
	gutters += 1
	b.phase = Phase.GUTTER
	b.t = 0.0
	popups.add("GUTTER", CX, 420.0, Pal.GRAY, 3.0)
	play(Sfx.GUTTER, 0.8)


func _land(b: Ball) -> void:
	b.z = 0.0
	b.phase = Phase.SETTLING
	b.t = 0.0
	b.from_x = b.x
	b.from_y = b.y
	play(Sfx.THUD, 0.8, 1.2)
	fx.haptics.tick()
	var bdx := b.x - BONUS_X
	var bdy := (b.y - BONUS_Y) / SQUASH
	if bdx * bdx + bdy * bdy < (BONUS_R + 6.0) * (BONUS_R + 6.0):
		b.ring = 6
		b.points = SkeeTuning.BONUS_POINTS
		b.to_x = BONUS_X
		b.to_y = BONUS_Y
		return
	var dx := b.x - CX
	var dy := (b.y - CY) / SQUASH
	var d := sqrt(dx * dx + dy * dy)
	var ring := -1
	for i in SkeeTuning.RING_RADII.size():
		if d < float(SkeeTuning.RING_RADII[i]):
			ring = i
			break
	if ring < 0 or b.y > BOARD_BOTTOM:
		ring = SkeeTuning.RING_RADII.size() - 1
	b.ring = ring
	b.points = SkeeTuning.RING_POINTS[ring]
	# Roll into the cup at the top of the ring it landed in.
	var r := 0.0 if ring == 0 else (float(SkeeTuning.RING_RADII[ring - 1]) + float(SkeeTuning.RING_RADII[ring])) / 2.0
	var ang := atan2(dy, dx)
	b.to_x = CX + cos(ang) * r
	b.to_y = CY + sin(ang) * r * SQUASH


func _score(b: Ball) -> void:
	b.active = false
	var pts := b.points
	ring_flash[b.ring] = 1.0
	var color := _ring_color(b.ring)
	_add_impact(b.to_x, b.to_y, _ring_color(b.ring), pts >= 100)
	marquee_boost = maxf(marquee_boost, 1.0 if b.ring == 6 else (0.8 if pts >= 100 else 0.2 + pts / 200.0))
	# Effects appear where the cup is on screen.
	_to_field(b.to_x, _surface_y(b.to_y) + BALL_R, b.to_y)
	var sx := pt[0]
	var sy := pt[1]
	if b.ring == 6:
		add_score(pts, sx - 40.0, sy + 30.0, Pal.GOLD)
		popups.add("BONUS!!", CX, 200.0, Pal.GOLD, 5.0, 1.4)
		play(Sfx.JACKPOT)
		fx.haptics.jackpot()
		shake.add(0.6)
		flash.trigger(0.8)
		particles.confetti(0.0, 0.0, MiniGame.GAME_W, 90)
		particles.burst(sx, sy, 40, 80.0, 300.0, [Pal.GOLD, Pal.YELLOW, Pal.WHITE], 0.9, 5.0, 0.0, 2.0, Particles.SPARKLE)
	elif pts >= 100:
		add_score(pts, sx, sy - 20.0, color)
		popups.add("BULLSEYE!", CX, 300.0, Pal.RED, 4.0, 1.2)
		play(Sfx.WIN)
		fx.haptics.win()
		shake.add(0.45)
		particles.burst(sx, sy, 36, 80.0, 260.0, [Pal.RED, Pal.YELLOW, Pal.WHITE], 0.8, 5.0)
	else:
		add_score(pts, sx, sy - 20.0, color)
		play(Sfx.COIN, 0.7, 0.7 + pts / 100.0)
		fx.haptics.hit()
		shake.add(0.08 + pts / 400.0)
		particles.burst(sx, sy, 10 + pts / 4, 50.0, 180.0, [_ring_color(b.ring), Pal.WHITE], 0.6, 4.0)


## Starts a ring spreading over the board from where a ball settled.
func _add_impact(x: float, y: float, color: int, big: bool) -> void:
	var i := impact_next
	impact_next = (impact_next + 1) % IMPACTS
	impact_x[i] = x
	impact_y[i] = y
	impact_color[i] = color
	impact_big[i] = big
	impact_age[i] = 0.0


static func _ring_color(ring: int) -> int:
	match ring:
		0:
			return Pal.RED
		1:
			return Pal.YELLOW
		2:
			return Pal.ORANGE
		3:
			return Pal.PINK
		4:
			return Pal.PURPLE
		5:
			return Pal.SKY
	return Pal.GOLD


# ---------------------------------------------------------------- 3D presentation

func _board_texture() -> PaTexture:
	if _board_tex == null:
		var colors: Array = []
		for i in 6:
			colors.append(_ring_color(i))
		_board_tex = SkeeArt.board(BOARD_L, BOARD_TOP - 10.0, BOARD_R, BOARD_BOTTOM, CX, CY, SQUASH,
			SkeeTuning.RING_RADII, SkeeTuning.RING_POINTS, colors, BONUS_X, BONUS_Y, BONUS_R)
	return _board_tex


func _lane_texture() -> PaTexture:
	if _lane_tex == null:
		_lane_tex = SkeeArt.lane(int(LANE_R - LANE_L), int(LANE_END - RAMP_Y), 1.5)
	return _lane_tex


## Raised walls standing up from the board between the scoring rings.
func _ring_walls_model() -> Model:
	if _ring_walls == null:
		var tex := SkeeArt.ring_wall().full()
		var b := ModelBuilder.new()
		var n_y := SQUASH
		var n_z := 0.626
		var n := 32
		for ring in SkeeTuning.RING_RADII.size():
			var rad: float = SkeeTuning.RING_RADII[ring]
			var h := 5.0 + ring * 0.6
			var tint := Pal.mix(_ring_color(ring), Pal.WHITE, 0.12)
			for k in n:
				var a0 := k * TAU / n
				var a1 := (k + 1) * TAU / n
				var x0 := CX + cos(a0) * rad
				var z0 := CY + sin(a0) * rad * SQUASH
				var x1 := CX + cos(a1) * rad
				var z1 := CY + sin(a1) * rad * SQUASH
				var y0 := _board_y(z0)
				var y1 := _board_y(z1)
				b.quad(x0, y0 + n_y * h, z0 + n_z * h, x1, y1 + n_y * h, z1 + n_z * h, x1, y1, z1, x0, y0, z0,
					tex, 0.0, n_y, n_z, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, false, tint)
		_ring_walls = b.build()
	return _ring_walls


## Height of the playfield surface under lane position [param y].
func _surface_y(y: float) -> float:
	if y >= RAMP_Y:
		return 0.0
	if y >= RAMP_TOP:
		var t := (RAMP_Y - y) / (RAMP_Y - RAMP_TOP)
		return RAMP_H * t * t
	if y >= BOARD_BOTTOM:
		return RAMP_H + (BOARD_BASE - RAMP_H) * (RAMP_TOP - y) / (RAMP_TOP - BOARD_BOTTOM)
	return _board_y(y)


func _board_y(y: float) -> float:
	return BOARD_BASE + (BOARD_BOTTOM - y) * BOARD_TAN


func render(scope: DrawScope) -> void:
	var r := stage.begin()
	var motion := clampf(ScreenShake.intensity, 0.0, 1.0)
	_light_scene(r)
	r.gradient(0xFF04020A, Pal.NIGHT)
	_draw_cabinet(r, motion)
	_draw_board_fx(r, motion)
	for b in balls:
		if b.active and b.phase != Phase.GUTTER:
			_draw_trail(r, b)
			_draw_ball(r, b.x, b.y, b.z, b.spin, 1.0, 1.0)
	if has_ready:
		var s := ready_squash.value
		# A quiet ring on the lane under the waiting ball says "take me".
		if dragging < 0 and not time_up:
			SceneFx.shockwave(r, ready_x, 0.6, ready_y, 46.0 + 8.0 * sin(time * 4.0) * motion, Pal.CREAM, 0.18 + 0.1 * sin(time * 4.0) * motion)
		_draw_ball(r, ready_x, ready_y, 0.0, 0.0, 2.0 - s, s)
	_draw_impacts(r)
	stage.present()

	if has_ready and dragging < 0 and not time_up and _to_field(ready_x, 0.0, ready_y):
		var a := 0.5 + 0.5 * sin(time * 6.0)
		ArcadeFont.draw_centered(scope, "FLICK " + ArcadeFont.UP, pt[0], pt[1] + 26.0, 1.8, Color.WHITE, a)
	_draw_speed_meter(scope)


func _light_scene(r: Renderer3D) -> void:
	var l := r.lighting
	# A shade dimmer and cooler than before, so the varnished lane stops washing out.
	l.amb_r = 0.46
	l.amb_g = 0.43
	l.amb_b = 0.6
	l.set_direction(0.2, 1.0, 0.7)
	l.dir_r = 0.32
	l.dir_g = 0.3
	l.dir_b = 0.28
	l.points.clear()
	l.points.append(board_light)
	l.points.append(lane_light)
	marquee_light.intensity = 0.7 + 0.2 * sin(time * 5.0) + marquee_boost * 0.7
	l.points.append(marquee_light)
	# The ball lights the lane it rolls over: the first one in play, else the waiting one.
	var lit := false
	for b in balls:
		if b.active and (b.phase == Phase.ROLLING or b.phase == Phase.FLYING):
			ball_light.x = b.x
			ball_light.z = b.y
			ball_light.y = _surface_y(b.y) + b.z + BALL_R + 22.0
			ball_light.intensity = BALL_LIGHT
			lit = true
			break
	if not lit and has_ready:
		ball_light.x = ready_x
		ball_light.z = ready_y
		ball_light.y = BALL_R + 22.0
		ball_light.intensity = BALL_LIGHT * 0.5
		lit = true
	if lit:
		l.points.append(ball_light)
	var hot := -1
	for i in ring_flash.size():
		if ring_flash[i] > 0.0 and (hot < 0 or ring_flash[i] > ring_flash[hot]):
			hot = i
	if hot >= 0:
		var c := _ring_color(hot)
		ring_light.r = ((c >> 16) & 255) / 255.0
		ring_light.g = ((c >> 8) & 255) / 255.0
		ring_light.b = (c & 255) / 255.0
		ring_light.intensity = ring_flash[hot] * 1.6
		if hot == 6:
			ring_light.x = BONUS_X
			ring_light.z = BONUS_Y
			ring_light.y = _board_y(BONUS_Y) + 60.0
		else:
			ring_light.x = CX
			ring_light.z = CY
			ring_light.y = _board_y(CY) + 70.0
		l.points.append(ring_light)


func _draw_cabinet(r: Renderer3D, motion: float) -> void:
	# The pit between ramp and board, and the board's front edge.
	r.quad(BOARD_L, PIT_Y, BOARD_BOTTOM, BOARD_R, PIT_Y, BOARD_BOTTOM, BOARD_R, PIT_Y, RAMP_TOP + 4.0, BOARD_L, PIT_Y, RAMP_TOP + 4.0,
		SkeeArt.pit().full(), 0.0, 1.0, 0.0)
	r.quad(BOARD_L, BOARD_BASE, BOARD_BOTTOM, BOARD_R, BOARD_BASE, BOARD_BOTTOM, BOARD_R, PIT_Y, BOARD_BOTTOM, BOARD_L, PIT_Y, BOARD_BOTTOM,
		SkeeArt.board_edge().full(), 0.0, 0.0, 1.0)
	# Jump ramp: a curved hump in three strips.
	var ramp_tex := SkeeArt.ramp().full()
	var steps := 3
	for i in steps:
		var y0 := RAMP_Y - (RAMP_Y - RAMP_TOP) * i / steps
		var y1 := RAMP_Y - (RAMP_Y - RAMP_TOP) * (i + 1) / steps
		var h0 := _surface_y(y0)
		var h1 := _surface_y(y1)
		var v0 := ramp_tex.h * (1.0 - (i + 1.0) / steps)
		var v1 := ramp_tex.h * (1.0 - float(i) / steps)
		r.quad(LANE_L, h1, y1, LANE_R, h1, y1, LANE_R, h0, y0, LANE_L, h0, y0,
			ramp_tex, 0.0, 0.95, 0.3, 0.0, v0, NAN, v1)
	# The lane.
	r.quad(LANE_L, 0.0, RAMP_Y, LANE_R, 0.0, RAMP_Y, LANE_R, 0.0, LANE_END, LANE_L, 0.0, LANE_END,
		_lane_texture().full(), 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, 1.0, true, -1, 1.0, LANE_GLOSS)
	# Rails along both sides of the lane and ramp.
	var rail_tex := SkeeArt.rail_side().full()
	var rt := SkeeArt.rail_top().full()
	r.quad(BOARD_L, RAIL_H, BOARD_BOTTOM, LANE_L, RAIL_H, BOARD_BOTTOM, LANE_L, RAIL_H, LANE_END, BOARD_L, RAIL_H, LANE_END,
		rt, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, 1.0, true, -1, 1.0, RAIL_GLOSS)
	r.quad(LANE_R, RAIL_H, BOARD_BOTTOM, BOARD_R, RAIL_H, BOARD_BOTTOM, BOARD_R, RAIL_H, LANE_END, LANE_R, RAIL_H, LANE_END,
		rt, 0.0, 1.0, 0.0, float(rt.w), 0.0, 0.0, NAN, Blend.OPAQUE, 0.0, 1.0, true, -1, 1.0, RAIL_GLOSS)
	r.quad(LANE_L, RAIL_H, BOARD_BOTTOM, LANE_L, RAIL_H, LANE_END, LANE_L, PIT_Y, LANE_END, LANE_L, PIT_Y, BOARD_BOTTOM,
		rail_tex, 1.0, 0.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, 1.0, true, -1, 1.0, RAIL_GLOSS)
	r.quad(LANE_R, RAIL_H, LANE_END, LANE_R, RAIL_H, BOARD_BOTTOM, LANE_R, PIT_Y, BOARD_BOTTOM, LANE_R, PIT_Y, LANE_END,
		rail_tex, -1.0, 0.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, 1.0, true, -1, 1.0, RAIL_GLOSS)
	# The target board, tilted back.
	var far := BOARD_TOP - 10.0
	r.quad(BOARD_L, _board_y(far), far, BOARD_R, _board_y(far), far, BOARD_R, BOARD_BASE, BOARD_BOTTOM, BOARD_L, BOARD_BASE, BOARD_BOTTOM,
		_board_texture().full(), 0.0, SQUASH, 0.626)
	_ring_walls_model().draw(r)
	# Cabinet sides: tall at the back, sloping down towards the player.
	var side := SkeeArt.side_panel().full()
	_side_panel(r, BOARD_L, 1.0, side)
	_side_panel(r, BOARD_R, -1.0, side)
	# Back wall and marquee.
	var top_y := _board_y(far)
	r.quad(BOARD_L, SIGN_TOP + 30.0, far, BOARD_R, SIGN_TOP + 30.0, far, BOARD_R, top_y - 4.0, far, BOARD_L, top_y - 4.0, far,
		SkeeArt.board_edge().full(), 0.0, 0.0, 1.0)
	var m := SkeeArt.marquee().full()
	r.quad(BOARD_L + 6.0, SIGN_TOP, far + 2.0, BOARD_R - 6.0, SIGN_TOP, far + 2.0, BOARD_R - 6.0, SIGN_TOP - 84.0, far + 2.0, BOARD_L + 6.0, SIGN_TOP - 84.0, far + 2.0,
		m, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 1.0 + 0.15 * marquee_boost)
	_draw_marquee_fx(r, far, motion)
	# Marquee bulbs, chasing (faster when a big score has excited the sign).
	var glow := TexKit.glow().full()
	var dot := TexKit.dot().full()
	for i in 18:
		var bx := BOARD_L + 6.0 + (9.0 + i * 17.8) * (BOARD_R - BOARD_L - 12.0) / 320.0
		var on := (int(time * (8.0 + 8.0 * marquee_boost)) + i) % 3 == 0
		var c := Pal.YELLOW if on else Pal.shade(Pal.ORANGE, 0.5)
		for row in 2:
			var by := SIGN_TOP - 10.0 * 84.0 / 90.0 if row == 0 else SIGN_TOP - 80.0 * 84.0 / 90.0
			r.sprite(bx, by, far + 3.0, 6.0, 6.0, dot, 0.0, Blend.OPAQUE, 1.2, 1.0, 1.0, c)
			if on:
				r.sprite(bx, by, far + 4.0, 22.0, 22.0, glow, 0.0, Blend.ADD, 1.0, 0.55, 1.0, Pal.YELLOW)
	# Bulbs along each side's sloping top edge.
	for s in 2:
		var x := BOARD_L + 2.0 if s == 0 else BOARD_R - 2.0
		for i in 11:
			var t := i / 10.0
			var z := SIDE_BACK_Z + (SIDE_FRONT_Z - SIDE_BACK_Z) * t
			var y := SIDE_BACK_Y + (SIDE_FRONT_Y - SIDE_BACK_Y) * t - 6.0
			# Kotlin's % keeps the dividend's sign, as GDScript's does: a negative count never lights.
			var on := (int(time * 6.0) - i) % 4 == 0
			r.sprite(x, y, z, 7.0, 7.0, dot, 0.0, Blend.OPAQUE, 1.2, 1.0, 1.0, Pal.YELLOW if on else Pal.shade(Pal.GOLD, 0.45))
			if on:
				r.sprite(x, y, z, 26.0, 26.0, glow, 0.0, Blend.ADD, 1.0, 0.5, 1.0, Pal.GOLD)
	# Guide chevrons pulse up the lane.
	var chev := SkeeArt.chevron().full()
	for i in 3:
		var y := 470.0 - i * 44.0
		var a := 0.2 + 0.3 * (0.5 + 0.5 * sin(time * 5.0 - i))
		r.flat(CX, y, 0.5, 34.0, 20.0, chev, 0.0, Blend.ADD, 1.0, a, Pal.CREAM)


func _side_panel(r: Renderer3D, x: float, nx: float, tex: Region) -> void:
	var w := float(tex.w)
	var h := float(tex.h)
	var span_z := SIDE_FRONT_Z - SIDE_BACK_Z
	var span_y := SIDE_BACK_Y - PIT_Y
	r.begin(tex)
	r.normal(nx, 0.0, 0.0)
	r.vertex(x, SIDE_BACK_Y, SIDE_BACK_Z, 0.0, 0.0)
	r.vertex(x, SIDE_FRONT_Y, SIDE_FRONT_Z, w, (SIDE_BACK_Y - SIDE_FRONT_Y) / span_y * h)
	r.vertex(x, RAIL_H, SIDE_FRONT_Z, w, (SIDE_BACK_Y - RAIL_H) / span_y * h)
	r.vertex(x, PIT_Y, SIDE_FRONT_Z - span_z * 0.1, w * 0.9, h)
	r.vertex(x, PIT_Y, SIDE_BACK_Z, 0.0, h)
	r.end()


## Ring flashes, quiet pulses over the board and the blinking bonus hole, drawn as light on the board.
func _draw_board_fx(r: Renderer3D, motion: float) -> void:
	var white := TexKit.white().full()
	for ring in 6:
		var f := ring_flash[ring]
		if f <= 0.0:
			continue
		var r_in := 9.0 if ring == 0 else float(SkeeTuning.RING_RADII[ring - 1])
		var r_out: float = SkeeTuning.RING_RADII[ring]
		_annulus(r, r_in, r_out, _ring_color(ring), f * 0.75, white)
	# A soft pulse travels out from the bullseye now and then, so the board is never quite still.
	if motion > 0.0:
		var ph := fmod(time / PULSE_PERIOD, 1.0)
		_board_ring(r, CX, CY, 8.0 + ph * 118.0, Pal.CREAM, PULSE_ALPHA * sin(ph * PI) * motion)
	var f0 := ring_flash[0]
	if f0 > 0.0:
		SceneFx.flare(r, CX, _board_y(CY) + 30.0, CY, 110.0 * (0.6 + 0.4 * f0), Pal.YELLOW, f0 * 0.85)
	var glow := TexKit.glow().full()
	var blink := 0.5 + 0.5 * sin(time * 9.0)
	var by := _board_y(BONUS_Y) + 3.0
	r.sprite(BONUS_X, by, BONUS_Y, 44.0, 44.0, glow, 0.0, Blend.ADD, 1.0, 0.25 + 0.35 * blink, 1.0, Pal.GOLD)
	# The bonus hole shines a faint gold shaft up, and a ring of chasing lights circles it.
	SceneFx.shaft_beam(r, BONUS_X, by, BONUS_Y, BONUS_X, by + 90.0, BONUS_Y, 26.0, Pal.GOLD, 0.05 + 0.07 * blink)
	var dot := TexKit.dot().full()
	for k in 8:
		var a := k * TAU / 8.0
		var bx := BONUS_X + cos(a) * (BONUS_R + 9.0)
		var bz := BONUS_Y + sin(a) * (BONUS_R + 9.0) * SQUASH
		var on := (int(time * 10.0) + k) % 3 == 0
		r.sprite(bx, _board_y(bz) + 3.0, bz, 5.0, 5.0, dot, 0.0, Blend.OPAQUE, 1.15, 1.0, 1.0, Pal.YELLOW if on else Pal.shade(Pal.GOLD, 0.4))
	var bf := ring_flash[6]
	if bf > 0.0:
		r.sprite(BONUS_X, by, BONUS_Y, 90.0 * (1.0 + bf), 90.0 * (1.0 + bf), glow, 0.0, Blend.ADD, 1.0, bf, 1.0, Pal.GOLD)
		SceneFx.flare(r, BONUS_X, by + 20.0, BONUS_Y, 140.0 * (0.5 + 0.5 * bf), Pal.YELLOW, bf, time * 2.0 * motion)


## The largest reach for a ring centred on lane position (x, y) that still ends on the board (a
## shockwave from the corner bonus hole must not spill over the cabinet's sides).
func _fit_on_board(x: float, y: float, reach: float) -> float:
	var room := minf(minf(x - BOARD_L, BOARD_R - x), minf((y - (BOARD_TOP - 10.0)) / SQUASH, (BOARD_BOTTOM - y) / SQUASH))
	return maxf(minf(reach, room), 16.0)


## A soft ring of light lying on the tilted board: a true circle on it once seen through the camera.
func _board_ring(r: Renderer3D, x: float, y: float, rx: float, color: int, alpha: float) -> void:
	if alpha <= 0.004:
		return
	var rz := rx * SQUASH
	var z0 := y - rz
	var z1 := y + rz
	r.quad(x - rx, _board_y(z0) + 0.9, z0, x + rx, _board_y(z0) + 0.9, z0, x + rx, _board_y(z1) + 0.9, z1, x - rx, _board_y(z1) + 0.9, z1,
		SceneFx.ring().full(), 0.0, SQUASH, 0.626, 0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, alpha, false, color)


## Shockwaves crossing the board from where balls came to rest; the top scores throw a flare too.
func _draw_impacts(r: Renderer3D) -> void:
	for i in IMPACTS:
		var t := impact_age[i] / IMPACT_LIFE
		if t >= 1.0:
			continue
		var e := MathUtil.ease_out_cubic(t)
		var big := impact_big[i]
		var reach := _fit_on_board(impact_x[i], impact_y[i], 125.0 if big else 70.0)
		_board_ring(r, impact_x[i], impact_y[i], 10.0 + e * reach, impact_color[i], (1.0 - t) * (0.8 if big else 0.55))
		if big and t < 0.55:
			var k := 1.0 - t / 0.55
			SceneFx.flare(r, impact_x[i], _board_y(impact_y[i]) + 22.0, impact_y[i], 60.0 + 60.0 * e, impact_color[i], k * 0.8)


## A warm smear behind a moving ball, fading over TRAIL_LIFE; samples close to the ball hide behind it.
func _draw_trail(r: Renderer3D, b: Ball) -> void:
	var n := b.trail_count
	for k in n:
		var idx := (b.trail_head - 1 - k + TRAIL_LEN * 2) % TRAIL_LEN
		var life := 1.0 - (b.age - b.trail_at[idx]) / TRAIL_LIFE
		if life <= 0.0:
			break
		var dx := b.trail_x[idx] - b.x
		var dy := b.trail_y[idx] - b.y
		var away := MathUtil.clamp01(sqrt(dx * dx + dy * dy) / (BALL_R * 1.4))
		var gy := b.trail_y[idx]
		SceneFx.glow(r, b.trail_x[idx], _surface_y(gy) + b.trail_z[idx] + BALL_R, gy, BALL_R * (0.9 + 1.4 * life), Pal.ORANGE, TRAIL_ALPHA * life * life * away)


## The glow behind the marquee, and a sheen that sweeps across it now and then.
func _draw_marquee_fx(r: Renderer3D, far: float, motion: float) -> void:
	SceneFx.glow(r, CX, SIGN_TOP - 42.0, far + 8.0, 380.0, Pal.ORANGE, 0.09 + 0.16 * marquee_boost)
	var ph := fmod(time / SWEEP_PERIOD, 1.0)
	if motion <= 0.0 or ph >= 0.35:
		return
	var k := ph / 0.35
	var hw := 34.0
	var sx := lerpf(BOARD_L - 20.0, BOARD_R + 20.0, k)
	var x0 := maxf(BOARD_L + 6.0, sx - hw)
	var x1 := minf(BOARD_R - 6.0, sx + hw)
	if x1 <= x0:
		return
	var reg := SceneFx.streak().full()
	var u0 := (x0 - (sx - hw)) / (2.0 * hw) * reg.w
	var u1 := (x1 - (sx - hw)) / (2.0 * hw) * reg.w
	r.quad(x0, SIGN_TOP, far + 2.5, x1, SIGN_TOP, far + 2.5, x1, SIGN_TOP - 84.0, far + 2.5, x0, SIGN_TOP - 84.0, far + 2.5,
		reg, 0.0, 0.0, 1.0, u0, 0.0, u1, NAN, Blend.ADD, 1.0, 0.32, true, Pal.CREAM)


func _annulus(r: Renderer3D, r_in: float, r_out: float, color: int, alpha: float, tex: Region) -> void:
	var n := 28
	for k in n:
		var a0 := k * TAU / n
		var a1 := (k + 1) * TAU / n
		var c0 := cos(a0)
		var s0 := sin(a0)
		var c1 := cos(a1)
		var s1 := sin(a1)
		var ax := CX + c0 * r_out
		var az := CY + s0 * r_out * SQUASH
		var bx := CX + c1 * r_out
		var bz := CY + s1 * r_out * SQUASH
		var cx := CX + c1 * r_in
		var cz := CY + s1 * r_in * SQUASH
		var dx := CX + c0 * r_in
		var dz := CY + s0 * r_in * SQUASH
		r.quad(ax, _board_y(az) + 0.6, az, bx, _board_y(bz) + 0.6, bz, cx, _board_y(cz) + 0.6, cz, dx, _board_y(dz) + 0.6, dz,
			tex, 0.0, SQUASH, 0.626, 0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, alpha, false, color)


func _draw_ball(r: Renderer3D, x: float, y: float, z: float, spin: float, sx: float, sy: float) -> void:
	var base := _surface_y(y)
	# Contact shadow following the surface under the ball.
	var sh := TexKit.shadow().full()
	var a := 0.55 / (1.0 + z / 60.0)
	var sw := BALL_R * 1.1
	r.quad(x - sw, _surface_y(y - sw) + 0.8, y - sw, x + sw, _surface_y(y - sw) + 0.8, y - sw,
		x + sw, _surface_y(y + sw) + 0.8, y + sw, x - sw, _surface_y(y + sw) + 0.8, y + sw,
		sh, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.ALPHA, 0.0, a, false)
	# Rolling up the lane (towards -z) turns the ball about the x axis.
	if _ball_model == null:
		_ball_model = SkeeArt.ball(BALL_R)
	_ball_xf.set_xf(x, base + z + BALL_R * sy, y, 0.0, -spin).stretch(sx, sy, sx)
	_ball_model.draw(r, -1, 1.0, _ball_xf)


func _draw_speed_meter(scope: DrawScope) -> void:
	if speed_show_t <= 0.0:
		return
	var a := MathUtil.clamp01(speed_show_t / 0.4)
	var x := LANE_R + 12.0
	var top := 340.0
	var h := 220.0
	scope.draw_rect(Pal.BLACK, Vector2(x, top), Vector2(34.0, h), 0.7 * a)
	# Speeds that land a straight roll from the tray inside the 50 ring.
	var ideal_lo := 425.0
	var ideal_hi := 478.0
	var y_hi := _speed_y(ideal_hi, top, h)
	scope.draw_rect(Pal.LIME, Vector2(x, y_hi), Vector2(34.0, _speed_y(ideal_lo, top, h) - y_hi), 0.35 * a)
	var fill_top := _speed_y(last_speed, top, h)
	scope.draw_rect(Pal.ORANGE, Vector2(x + 6.0, fill_top), Vector2(22.0, top + h - fill_top), a)
	ArcadeFont.draw_centered(scope, "PWR", x + 17.0, top + h + 6.0, 2.0, Color.WHITE, a)


static func _speed_y(s: float, top: float, h: float) -> float:
	return top + h - (s - SkeeTuning.MIN_SPEED) / (SkeeTuning.MAX_SPEED - SkeeTuning.MIN_SPEED) * h


# ---------------------------------------------------------------- simulation-test hooks

## Lane position of the ball waiting to be rolled.
func bot_ready_x() -> float:
	return ready_x


func bot_has_ready() -> bool:
	return has_ready


## Balls still rolling, flying or settling into a cup.
func bot_balls_active() -> int:
	var n := 0
	for b in balls:
		if b.active:
			n += 1
	return n


## Balls that went down the gutter this round.
func bot_gutters() -> int:
	return gutters


## Balls the SkeeTuning.BALL_TIMEOUT failsafe had to retire this round (should stay 0).
func bot_failsafe_trips() -> int:
	return failsafe_trips


## Rolls a ball straight from (x, y), bypassing the finger: no reload, no sound.
func bot_launch(x: float, y: float, speed: float, angle_rad: float) -> bool:
	return _launch(x, y, speed, angle_rad)


# ---------------------------------------------------------------- attract mode

## The alley in miniature, playing itself: a lit marquee with chasing bulbs, the ringed board, and a
## ball that rolls up the lane, hops the ramp and drops into a different ring each time. The ring
## lights up, a shockwave crosses the board and its score floats up. ATTRACT_LOOP seconds a throw.
func draw_attract(p: Painter, w: int, h: int, p_time: float) -> void:
	var wf := float(w)
	var hf := float(h)
	for row in h:
		p.fill(0.0, float(row), wf, 1.05, Pal.mix(Pal.NAVY, Pal.shade(Pal.NIGHT, 0.8), row / (hf - 1.0)))
	var cx := wf / 2.0
	var cy := hf * 0.5
	# Board: concentric rings, each with a dark edge, and the cup in the middle.
	var radii := [7.6, 6.3, 5.0, 3.8, 2.7, 1.6]
	var colors := [Pal.SKY, Pal.PURPLE, Pal.PINK, Pal.ORANGE, Pal.YELLOW, Pal.RED]
	var t := fmod(p_time, ATTRACT_LOOP)
	var throw_index := int(p_time / ATTRACT_LOOP)
	var target: int = ATTRACT_TARGETS[throw_index % ATTRACT_TARGETS.size()]
	var landed := t >= 1.7
	var since := t - 1.7
	for i in radii.size():
		var rr: float = radii[i]
		var lit := landed and i == target and since < 0.9
		p.disc(cx, cy, rr + 0.35, Pal.shade(colors[i], 0.35))
		p.disc(cx, cy, rr, Pal.shade(colors[i], 1.0 if lit else 0.62))
		p.disc(cx, cy + 0.2, rr - 0.55, Pal.shade(colors[i], 0.85 if lit else 0.42))
	p.disc(cx, cy, 0.7, Pal.BLACK)
	# A shockwave rippling out over the rings where the ball landed.
	if landed and since < 0.7:
		var k := since / 0.7
		var rr := 1.5 + k * 6.6
		var a := (1.0 - k) * 0.9
		for i in 18:
			var ang := i * TAU / 18.0
			p.px(cx + cos(ang) * rr, cy + sin(ang) * rr, Color.WHITE, a)

	# Marquee: a red plate edged in gold with the name and chasing bulbs.
	p.fill(0.0, 0.0, wf, 3.4, Pal.DARKRED)
	p.fill(0.0, 3.4, wf, 0.35, Pal.GOLD)
	p.fill(0.0, 0.0, wf, 0.3, Pal.GOLD)
	p.text_centered("SKEE-BALL", cx, 0.3, Pal.YELLOW, true, 1.0, 0.5)
	for i in 12:
		var on := (int(p_time * 6.0) + i) % 3 == 0
		p.px(i * (wf / 12.0) + 0.9, 3.05, Pal.YELLOW if on else Pal.shade(Pal.ORANGE, 0.5))

	# Lane: varnished wood with blue rails and a warm glow under the ball.
	var lane_top := hf - 5.2
	p.fill(0.0, lane_top, wf, hf - lane_top, Pal.shade(Pal.WOOD, 0.8))
	for i in range(0, w, 3):
		p.fill(float(i), lane_top, 0.3, hf - lane_top, Pal.shade(Pal.WOOD, 0.55), 0.7)
	p.fill(0.0, lane_top, wf, 0.5, Pal.TAN)
	p.fill(0.0, lane_top, 1.4, hf - lane_top, Pal.BLUE)
	p.fill(wf - 1.4, lane_top, 1.4, hf - lane_top, Pal.BLUE)
	p.fill(1.4, lane_top, 0.3, hf - lane_top, Pal.YELLOW)
	p.fill(wf - 1.7, lane_top, 0.3, hf - lane_top, Pal.YELLOW)

	# The throw: roll up the lane, hop off the ramp, drop into the ring, then vanish for the next.
	var bx: float
	var by: float
	var br: float
	# The cup of a ring sits at the top of it: the ball comes to rest there.
	var target_y := cy - (0.0 if target >= 5 else (float(radii[target]) + float(radii[target + 1])) / 2.0 * 0.85)
	if t < 1.1:
		var k := t / 1.1
		bx = cx + sin(p_time * 1.3) * 0.8 * (1.0 - k)
		by = lerpf(hf - 1.6, lane_top + 0.6, k)
		br = lerpf(1.7, 1.2, k)
	elif t < 1.7:
		var k := (t - 1.1) / 0.6
		bx = cx
		by = lerpf(lane_top + 0.6, target_y, k) - sin(k * PI) * 3.6
		br = lerpf(1.2, 0.95, k)
	elif t < 2.9:
		bx = cx
		by = target_y
		br = 0.95
	else:
		bx = cx
		by = hf + 4.0
		br = 0.0
	if br > 0.0:
		# A warm smear behind the ball while it moves.
		if t < 1.7:
			p.disc(bx, by + br * 1.6, br * 0.8, Pal.ORANGE, 0.3)
		p.disc(bx, by, br, Pal.DARKRED)
		p.disc(bx - br * 0.25, by - br * 0.3, br * 0.6, Pal.RED)
		p.disc(bx - br * 0.35, by - br * 0.4, br * 0.25, Color.WHITE, 0.6)
	# The ring's score floats up.
	if landed and since < 1.1:
		var a := 1.0 - since / 1.1
		p.text_centered(ATTRACT_LABELS[target], cx, target_y - 1.5 - since * 3.0, Pal.WHITE, true, a, 0.6)
		for i in 6:
			var ang := i * 1.05 + 0.3
			var rad := 1.0 + since * 5.0
			p.px(cx + cos(ang) * rad, target_y + sin(ang) * rad * 0.7, Pal.YELLOW if i % 2 == 0 else Pal.WHITE, a)
