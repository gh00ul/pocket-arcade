class_name HoopsGame
extends BaseMiniGame
## games/hoops/HoopsGame.kt: basketball hoops. Flick up to shoot (flick speed is shot power), makes
## in a row multiply the points up to ×5, a swish adds 3, and from half time the hoop slides from
## side to side. The simulation is in metres (z away from the player); the 3D alley is drawn by
## [HoopsScene] with a camera that matches the simulation's own projection.

const G := HoopsGeo.G
const CAM_Y := HoopsGeo.CAM_Y
const CAM_Z := HoopsGeo.CAM_Z
const F := HoopsGeo.F
const CX := HoopsGeo.CX
const HORIZON := HoopsGeo.HORIZON
const BALL_R := HoopsGeo.BALL_R
const RIM_R := HoopsGeo.RIM_R
const RIM_Y := HoopsGeo.RIM_Y
const HOOP_Z := HoopsGeo.HOOP_Z
const BOARD_Z := HoopsGeo.BOARD_Z
const BOARD_HALF_W := HoopsGeo.BOARD_HALF_W
const BOARD_BOTTOM := HoopsGeo.BOARD_BOTTOM
const BOARD_TOP := HoopsGeo.BOARD_TOP
const START_Y := HoopsGeo.START_Y
const START_Z := HoopsGeo.START_Z
const CAGE_HALF_W := HoopsGeo.CAGE_HALF_W
const BACK_Z := HoopsGeo.BACK_Z
## World units (centimetres) per simulation metre.
const S := HoopsGeo.S

## Colours of the sparks a rim hit throws.
const SPARK_COLORS := [Pal.WHITE, Pal.ORANGE, Pal.YELLOW]
## The burst of a make at the max multiplier, and the glints of every make.
const FIRE_COLORS := [Pal.ORANGE, Pal.YELLOW, Pal.RED]
const MAKE_COLORS := [Pal.WHITE, Pal.YELLOW]
const FLICK_PROMPT := "FLICK " + ArcadeFont.UP


class Ball:
	extends RefCounted
	var x := 0.0
	var y := 0.0
	var z := 0.0
	var vx := 0.0
	var vy := 0.0
	var vz := 0.0
	var spin := 0.0
	var t := 0.0
	var active := false
	var scored := false
	var touched_rim := false
	var resolved := false
	var on_floor := false
	var rim_cooldown := 0.0


var _balls: Array[Ball] = []
var _has_ready := true
var _ready_x := 0.0
var _reload_t := 0.0
var _dragging := -1
var _flick := FlickTracker.new()
var _hoop_x := 0.0
var _hoop_vx := 0.0
var _moving := false
var _streak := 0
var _net_swish := 0.0
var _rim_shake := 0.0
var _ready_squash := Spring.new()
var _mult_spring := Spring.new(300.0, 10.0)
var _makes := 0
var _shots := 0

## All the presentation state (glows, trails, shockwaves); the simulation never reads it.
var _scene := HoopsScene.new()

## The launch speed (m/s) that drops a ball through the rim from the shooting spot.
var _ideal_power := 0.0

## The alley in 3D. The simulation is already 3D (metres, z away from the player); the world uses
## centimetres with z towards the viewer, and the camera matches the simulation's own projection
## exactly, so _sx/_sy still place effects on screen.
var _stage := Stage3D.new(int(GAME_W), int(GAME_H))
var _ball_model: Model = null
var _ball_xf := Xform.new()


func _init() -> void:
	id = "hoops"
	title = "HOOP SHOT"
	marquee = "HOOPS"
	instructions = PackedStringArray([
		"FLICK UP TO SHOOT",
		"FLICK SPEED = SHOT POWER",
		"MAKES IN A ROW MULTIPLY",
		"YOUR POINTS (UP TO x5)",
		"SECOND HALF: HOOP MOVES!",
	])
	look = MiniGame.CabinetLook.new(Pal.RED, Pal.ORANGE, Pal.ORANGE, MiniGame.CabinetShape.HOOPS)
	round_seconds = HoopsTuning.ROUND_SECONDS
	for i in 6:
		_balls.append(Ball.new())
	var th := HoopsTuning.LAUNCH_ANGLE_DEG * (PI / 180.0)
	var dz := HOOP_Z - START_Z
	var dy := RIM_Y - START_Y
	var c := cos(th)
	_ideal_power = sqrt(G * dz * dz / (2.0 * c * c * (tan(th) * dz - dy)))
	var fov := 2.0 * atan(GAME_H / 2.0 / F) * (180.0 / PI)
	_stage.look(0.0, CAM_Y * S, -CAM_Z * S, 0.0, CAM_Y * S, -CAM_Z * S - 1000.0, fov, HORIZON / GAME_H)


func reset() -> void:
	for b in _balls:
		b.active = false
	_has_ready = true
	_ready_x = 0.0
	_reload_t = 0.0
	_dragging = -1
	_hoop_x = 0.0
	_hoop_vx = 0.0
	_moving = false
	_streak = 0
	_net_swish = 0.0
	_rim_shake = 0.0
	_makes = 0
	_shots = 0
	_ready_squash.snap(1.0)
	_mult_spring.snap(1.0)
	_scene.reset()


func tickets_for(p_score: int) -> int:
	return HoopsTuning.BASE_TICKETS + p_score / HoopsTuning.POINTS_PER_TICKET


func is_settled() -> bool:
	for b in _balls:
		if b.active and not b.resolved:
			return false
	return true


func on_time_up() -> void:
	cancel_input()


## Drops the ball being lined up; it drifts back to the middle.
func cancel_input() -> void:
	_dragging = -1


func _multiplier() -> int:
	return clampi(_streak, 1, HoopsTuning.MAX_MULTIPLIER)


# ---------------------------------------------------------------- projection

func _depth(z: float) -> float:
	return maxf(z - CAM_Z, 0.2)


func _sx(x: float, z: float) -> float:
	return CX + x / _depth(z) * F


func _sy(y: float, z: float) -> float:
	return HORIZON - (y - CAM_Y) / _depth(z) * F


func _sr(r: float, z: float) -> float:
	return r / _depth(z) * F


# ---------------------------------------------------------------- input

func on_touch(type: int, p_id: int, x: float, y: float, time_ms: int) -> void:
	if type == TouchType.DOWN:
		if _dragging < 0 and _has_ready and not time_up and y > 330.0:
			_dragging = p_id
			_flick.reset(x, y, time_ms)
			_ready_squash.kick(-3.0)
	elif type == TouchType.MOVE:
		if p_id == _dragging:
			_flick.add(x, y, time_ms)
			_ready_x = clampf((x - CX) / 300.0, -0.45, 0.45)
	elif type == TouchType.UP:
		if p_id == _dragging:
			_flick.add(x, y, time_ms)
			_dragging = -1
			_shoot()


func _shoot() -> void:
	var v := _flick.velocity()
	var up := -v.y
	if up < HoopsTuning.MIN_FLICK or time_up:
		return
	var b: Ball = null
	for candidate in _balls:
		if not candidate.active:
			b = candidate
			break
	if b == null:
		return
	var th := HoopsTuning.LAUNCH_ANGLE_DEG * (PI / 180.0)
	var power := clampf(_ideal_power * (1.0 + (up - HoopsTuning.FLICK_IDEAL) * HoopsTuning.POWER_SENSITIVITY), 3.0, 11.0)
	b.active = true
	b.resolved = false
	b.scored = false
	b.touched_rim = false
	b.on_floor = false
	b.t = 0.0
	b.x = _ready_x
	b.y = START_Y
	b.z = START_Z
	b.vy = power * sin(th)
	b.vz = power * cos(th)
	var t_cross := (HOOP_Z - START_Z) / b.vz
	var need := (_hoop_x - _ready_x) / t_cross
	b.vx = need * HoopsTuning.AIM_ASSIST + v.x * HoopsTuning.LATERAL_SCALE
	b.spin = 0.0
	b.rim_cooldown = 0.0
	_has_ready = false
	_reload_t = HoopsTuning.RELOAD_SECONDS
	_shots += 1
	play(Sfx.WHOOSH, 0.5, 1.2)
	fx.haptics.tick()


# ---------------------------------------------------------------- simulation

func step(dt: float) -> void:
	_ready_squash.update(dt)
	_mult_spring.update(dt)
	_net_swish = maxf(_net_swish - dt * 2.2, 0.0)
	_rim_shake = maxf(_rim_shake - dt * 5.0, 0.0)
	_scene.step(dt)

	var half := round_seconds / 2.0
	if not _moving and time >= half:
		_moving = true
		popups.add("HOOP ON THE MOVE!", CX, 120.0, Pal.CYAN, 3.0, 1.6)
		play(Sfx.GO, 0.8)
	var new_x := 0.0
	if _moving:
		var ramp := MathUtil.clamp01((time - half) / 1.0)
		new_x = sin((time - half) / HoopsTuning.MOVE_PERIOD * TAU) * HoopsTuning.MOVE_AMPLITUDE_M * ramp
	_hoop_vx = (new_x - _hoop_x) / dt
	_hoop_x = new_x

	if not _has_ready:
		_reload_t -= dt
		if _reload_t <= 0.0 and not time_up:
			_has_ready = true
			_ready_squash.snap(0.65)
			play(Sfx.BOUNCE, 0.4, 1.3)
	elif _dragging < 0:
		_ready_x = MathUtil.damp(_ready_x, 0.0, 3.0, dt)

	for b in _balls:
		if b.active:
			_step_ball(b, dt)
	# Balls in the air leave a glowing trail (visual only).
	for i in _balls.size():
		var b := _balls[i]
		if b.active and not b.on_floor:
			_scene.trail(i, b.x * S, b.y * S, -b.z * S)

	# The hoop catches fire at the max multiplier.
	if _streak >= HoopsTuning.MAX_MULTIPLIER and rng.next_float() < 0.5:
		var rx := _sx(_hoop_x, HOOP_Z)
		var ry := _sy(RIM_Y, HOOP_Z)
		var px := rx + rng.range_f(-24.0, 24.0)
		var pvx := rng.range_f(-10.0, 10.0)
		var pvy := rng.range_f(-120.0, -60.0)
		var col := Pal.ORANGE if rng.next_boolean() else Pal.YELLOW
		particles.spawn(px, ry, pvx, pvy, 0.5, 5.0, col)


func _step_ball(b: Ball, dt: float) -> void:
	b.t += dt
	b.rim_cooldown -= dt
	var prev_y := b.y
	b.vy -= G * dt
	b.x += b.vx * dt
	b.y += b.vy * dt
	b.z += b.vz * dt
	b.spin += dt * (3.0 + absf(b.vz) * 2.0)

	# Rim: treat as a thin torus.
	var dxh := b.x - _hoop_x
	var dzh := b.z - HOOP_Z
	var dh := sqrt(dxh * dxh + dzh * dzh)
	if dh > 1e-4:
		var px := _hoop_x + dxh / dh * RIM_R
		var pz := HOOP_Z + dzh / dh * RIM_R
		var ex := b.x - px
		var ey := b.y - RIM_Y
		var ez := b.z - pz
		var d := sqrt(ex * ex + ey * ey + ez * ez)
		var min_d := BALL_R + 0.02
		if d < min_d and d > 1e-4:
			var nx := ex / d
			var ny := ey / d
			var nz := ez / d
			b.x += nx * (min_d - d)
			b.y += ny * (min_d - d)
			b.z += nz * (min_d - d)
			var vn := b.vx * nx + b.vy * ny + b.vz * nz
			if vn < 0.0:
				var e := 0.55
				b.vx -= (1.0 + e) * vn * nx
				b.vy -= (1.0 + e) * vn * ny
				b.vz -= (1.0 + e) * vn * nz
				b.vx += rng.range_f(-0.25, 0.25)
				if b.rim_cooldown <= 0.0:
					b.rim_cooldown = 0.1
					b.touched_rim = true
					_rim_shake = 1.0
					play(Sfx.RIM, 0.8, rng.range_f(0.9, 1.1))
					fx.haptics.tick()
					_scene.rim_hit(px * S, RIM_Y * S, -pz * S)
					particles.burst(_sx(px, pz), _sy(RIM_Y, pz), 7, 50.0, 170.0, SPARK_COLORS, 0.35, 3.0, 0.0, 2.0, Particles.SPARKLE)

	# Backboard.
	if b.z + BALL_R > BOARD_Z and b.vz > 0.0 and b.y >= BOARD_BOTTOM - BALL_R and b.y <= BOARD_TOP + BALL_R \
			and absf(b.x - _hoop_x) < BOARD_HALF_W + BALL_R:
		b.z = BOARD_Z - BALL_R
		b.vz = -b.vz * 0.55
		b.vx += _hoop_vx * 0.3
		play(Sfx.THUD, 0.7, 1.1)
		shake.add(0.06)
		_scene.board_hit(_hoop_x, (b.x - _hoop_x) * S, b.y * S)
	# Back wall of the cage.
	if b.z + BALL_R > BACK_Z and b.vz > 0.0:
		b.z = BACK_Z - BALL_R
		b.vz = -b.vz * 0.4
	# Side nets.
	if absf(b.x) + BALL_R > CAGE_HALF_W:
		b.x = (CAGE_HALF_W - BALL_R) * (1.0 if b.x > 0.0 else -1.0)
		b.vx = -b.vx * 0.4

	# Score: passes down through the rim plane inside the ring.
	if not b.scored and not b.resolved and prev_y > RIM_Y and b.y <= RIM_Y and b.vy < 0.0:
		var hx := b.x - _hoop_x
		var hz := b.z - HOOP_Z
		if sqrt(hx * hx + hz * hz) < RIM_R - BALL_R * 0.4:
			b.scored = true
			_basket(b)
	if b.scored:
		# Funnel through the net.
		b.x = MathUtil.damp(b.x, _hoop_x, 10.0, dt)
		b.z = MathUtil.damp(b.z, HOOP_Z, 10.0, dt)
		b.vx *= 0.9
		b.vz *= 0.9

	# Floor bounce, then roll back down the ramp to the player.
	if b.y < BALL_R:
		b.y = BALL_R
		if b.vy < 0.0:
			b.vy = -b.vy * 0.45
			if absf(b.vy) > 0.6:
				play(Sfx.BOUNCE, 0.5)
				_scene.floor_hit(b.x * S, -b.z * S, MathUtil.clamp01(absf(b.vy) / 3.0))
		if not b.on_floor:
			b.on_floor = true
			_resolve(b)
		b.vz = MathUtil.damp(b.vz, -2.6, 3.0, dt)
		b.vx = MathUtil.damp(b.vx, 0.0, 2.0, dt)
	if not b.resolved and b.t > 3.5:
		_resolve(b)
	if b.z < START_Z - 0.1 or b.t > 6.0:
		if not b.resolved:
			_resolve(b)
		b.active = false


func _resolve(b: Ball) -> void:
	if b.resolved:
		return
	b.resolved = true
	if not b.scored:
		if _streak >= 2:
			popups.add("STREAK OVER", CX, 250.0, Pal.GRAY, 2.0)
		_streak = 0


func _basket(b: Ball) -> void:
	_streak += 1
	_makes += 1
	var swish := not b.touched_rim
	var mult := _multiplier()
	var points := (HoopsTuning.BASKET_POINTS + (HoopsTuning.SWISH_BONUS if swish else 0)) * mult
	var rx := _sx(_hoop_x, HOOP_Z)
	var ry := _sy(RIM_Y, HOOP_Z)
	add_score(points, rx, ry + 40.0, Pal.YELLOW)
	_net_swish = 1.0
	_mult_spring.snap(1.6)
	_scene.made(_hoop_x, swish, _streak)
	if swish:
		popups.add("SWISH!", CX, 110.0, Pal.CYAN, 4.0)
	if _streak >= HoopsTuning.MAX_MULTIPLIER:
		popups.add("ON FIRE! x%d" % mult, CX, 290.0, Pal.ORANGE, 4.0, 1.2)
		play(Sfx.CHEER, 0.8)
		play(Sfx.WIN, 0.8)
		fx.haptics.jackpot()
		shake.add(0.4)
		particles.burst(rx, ry, 36, 80.0, 300.0, FIRE_COLORS, 0.8, 5.0, -80.0)
	elif _streak >= 2:
		popups.add("x%d COMBO" % mult, CX, 290.0, Pal.PINK, 3.0)
		play(Sfx.SWISH, 1.0)
		play(Sfx.COIN, 0.6, 0.8 + _streak * 0.1)
		fx.haptics.win()
		shake.add(0.22)
	else:
		play(Sfx.SWISH, 1.0)
		fx.haptics.hit()
		shake.add(0.12)
	particles.burst(rx, ry + 20.0, 16, 60.0, 200.0, MAKE_COLORS, 0.5, 4.0, 0.0, 2.0, Particles.SPARKLE)


# ---------------------------------------------------------------- 3D presentation

func render(scope: DrawScope) -> void:
	var r := _stage.begin()
	_scene.light(r, _hoop_x, _streak, time)
	r.gradient(0xFF06030C, Pal.NIGHT)
	_scene.update_readouts(_makes, _shots, _streak)
	_scene.draw_backdrop(r, time, _streak, _mult_spring.value)
	_scene.draw_pools(r, _hoop_x, _streak)
	_scene.draw_board(r, _hoop_x, time, _streak)
	var wob := sin(time * 60.0) * _rim_shake * 1.5
	var rim_y := RIM_Y * S + wob
	_scene.rim_xf.set_xf(_hoop_x * S, rim_y, -HOOP_Z * S)
	_scene.rim_model().draw(r, -1, 1.0, _scene.rim_xf)
	# A ball passing down through the net makes it bulge round it.
	var net_ball_y := -1.0
	for b in _balls:
		if not b.active:
			continue
		_draw_ball(r, b.x, b.y, b.z, b.spin, 1.0, 1.0)
		if b.scored and b.y < RIM_Y + BALL_R and b.y > RIM_Y - 0.55:
			net_ball_y = b.y * S
	if _has_ready:
		var s := _ready_squash.value
		_draw_ball(r, _ready_x, START_Y, START_Z, 0.0, 2.0 - s, s)
	_scene.draw_net(r, _hoop_x, rim_y, time, _net_swish, net_ball_y)
	_scene.draw_effects(r, _hoop_x, rim_y, time, _streak, BALL_R * 2.0 * S)
	_scene.draw_cage(r)
	_stage.present()

	if _has_ready and _dragging < 0 and not time_up:
		ArcadeFont.draw_centered(scope, FLICK_PROMPT, CX, 610.0, 2.0, Pal.WHITE, 0.5 + 0.5 * sin(time * 6.0))


func _draw_ball(r: Renderer3D, x: float, y: float, z: float, spin: float, sqx: float, sqy: float) -> void:
	if _ball_model == null:
		_ball_model = HoopsArt.ball(BALL_R * S)
	var wx := x * S
	var wz := -z * S
	var d := BALL_R * 2.0 * S
	# Contact shadow on the court, softer the higher the ball.
	var a := 0.45 / (1.0 + y * 0.6)
	r.flat(wx, wz, 0.5, d * 1.1, d * 0.9, TexKit.shadow().full(), 0.0, Blend.ALPHA, 0.0, a)
	_ball_xf.set_xf(wx, y * S + (sqy - 1.0) * d / 2.0, wz, 0.0, -spin).stretch(sqx, sqy, sqx)
	_ball_model.draw(r, -1, 1.0, _ball_xf)


# ---------------------------------------------------------------- simulation-test hooks

## Balls shot this round.
func bot_shots() -> int:
	return _shots


# ---------------------------------------------------------------- attract mode

## A night court in miniature: crowd flashes, a lit backboard and a neon title, and a ball that arcs
## up, swishes through the net (which kicks, with a glint and a "+13") and drops away, every few
## seconds from a different spot. [param w] × [param h] is the cabinet's small screen.
func draw_attract(p: Painter, w: int, h: int, tm: float) -> void:
	var wf := float(w)
	var hf := float(h)
	var cx := wf / 2.0
	# Night sky and arena, in bands.
	var bands := 6
	var sky_h := hf * 0.66
	for i in bands:
		p.fill(0.0, i * sky_h / bands, wf, sky_h / bands + 0.3, Pal.mix(0xFF07040F, 0xFF2A1750, i / (bands - 1.0)))
	# The crowd, two rows of heads bobbing, with camera flashes.
	for row in 2:
		var y := hf * (0.52 + row * 0.09)
		var shade := 0xFF150C2C if row == 0 else 0xFF23164A
		for i in 13:
			var x := i * (wf / 12.0) + row * 0.9 - 0.4
			var bob := sin(tm * 2.6 + i * 1.7 + row) * 0.25
			p.disc(x, y + bob, 1.05, shade)
			p.fill(x - 1.1, y + 0.9 + bob, 2.2, hf, shade)
	for i in 3:
		var u := fmod(tm * 1.3 + i * 0.7, 2.4)
		if u < 0.12:
			p.disc(1.5 + MathUtil.hash01(i, int(tm * 1.3 + i * 0.7)) * (wf - 3.0), hf * (0.5 + i * 0.04), 0.55, Pal.WHITE, 1.0 - u / 0.12)
	# The court and its key.
	var floor_y := hf * 0.72
	p.fill(0.0, floor_y, wf, hf - floor_y, 0xFF6B4524)
	p.fill(0.0, floor_y, wf, 0.5, 0xFF2A1A0C)
	p.fill(cx - 3.2, floor_y + 0.6, 6.4, hf - floor_y, 0xFF8E2A38, 0.6)
	p.fill(cx - 3.2, floor_y + 0.6, 0.4, hf - floor_y, 0xFFDCCFAE)
	p.fill(cx + 2.8, floor_y + 0.6, 0.4, hf - floor_y, 0xFFDCCFAE)

	# Backboard, rim and net.
	var cycle := 3.4
	var n := int(tm / cycle)
	var t := fmod(tm, cycle)
	var board_top := hf * 0.06
	p.fill(cx - 4.4, board_top, 8.8, 5.2, 0xFF16224A)
	p.frame(cx - 4.4, board_top, 8.8, 5.2, Pal.RED)
	p.frame(cx - 1.8, board_top + 1.8, 3.6, 2.6, Pal.RED, 0.8)
	var rim_y := board_top + 5.2
	var swish := 1.0 - (t - 1.3) / 0.6 if t >= 1.3 and t <= 1.9 else 0.0
	# Net: five strands narrowing, kicking sideways after a make.
	for k in range(-2, 3):
		var top := cx + k * 0.75
		var foot := cx + k * 0.42 + sin(tm * 22.0) * swish * 0.5
		var px := top
		var py := rim_y
		for seg in range(1, 4):
			var u := seg / 3.0
			var nx := lerpf(top, foot, u)
			var ny := rim_y + 2.6 * u * (1.0 + swish * 0.2)
			p.fill(minf(px, nx), py, maxf(absf(nx - px), 0.35), maxf(ny - py, 0.35), 0xFFE4E0F0, 0.8)
			px = nx
			py = ny
	p.fill(cx - 2.5, rim_y - 0.2, 5.0, 0.6, Pal.ORANGE)
	p.disc(cx - 2.5, rim_y + 0.1, 0.55, Pal.ORANGE)
	p.disc(cx + 2.5, rim_y + 0.1, 0.55, Pal.ORANGE)
	if swish > 0.0:
		# The rim flares and a ring runs out from it.
		p.disc(cx, rim_y + 0.4, 1.6 + (1.0 - swish) * 4.0, Pal.YELLOW, swish * 0.25)
		p.frame(cx - 2.5 - (1.0 - swish) * 3.0, rim_y - 0.7, 5.0 + (1.0 - swish) * 6.0, 1.4, Pal.WHITE, swish * 0.7)

	# The shot: an arc from a different spot each time, a trail behind it, then down through the net.
	var start_x := cx + (MathUtil.hash01(n, 7) - 0.5) * 12.0
	var flight := 1.05
	var su := (t - 0.35) / flight
	if su >= 0.0 and su <= 1.0:
		for k in range(4, -1, -1):
			var uu := maxf(su - k * 0.035, 0.0)
			p.disc(_shot_x(start_x, cx, uu), _shot_y(hf, rim_y, uu), 1.35 * (1.0 - k * 0.13), Pal.ORANGE, 0.9 - k * 0.17)
		p.disc(_shot_x(start_x, cx, su) - 0.4, _shot_y(hf, rim_y, su) - 0.4, 0.5, Pal.WHITE, 0.6)
	elif t >= 0.35 + flight and t <= 1.9 + flight:
		var fall := (t - 0.35 - flight) / 0.55
		var by := rim_y + 0.3 + fall * fall * (hf - rim_y)
		p.disc(cx, by, 1.35, Pal.ORANGE)
	if t >= 1.5 and t <= 2.5:
		var rise := (t - 1.5) / 1.0
		p.text_centered("+13", cx, board_top - 0.3 - rise * 2.0, Pal.YELLOW, true, 1.0 - rise, 0.6)

	# Neon title, pulsing.
	var pulse := 0.75 + 0.25 * sin(tm * 4.0)
	p.text_centered("HOOP SHOT", cx, hf - 3.6, Pal.ORANGE, true, 0.5 * pulse, 0.66)
	p.text_centered("HOOP SHOT", cx, hf - 3.6, Pal.CREAM, true, pulse, 0.62)


## The shot's path across the little screen: [param u] runs 0 to 1 from the shooter's spot to the rim.
func _shot_x(start_x: float, cx: float, u: float) -> float:
	return lerpf(start_x, cx, u)


func _shot_y(h: float, rim_y: float, u: float) -> float:
	return lerpf(h - 2.0, rim_y - 0.4, u) - sin(u * PI) * (h * 0.36)
