class_name AirHockeyGame
extends BaseMiniGame
## games/airhockey/AirHockeyGame.kt: air hockey against the CPU. Drag your mallet (it chases the
## finger in your half), smash the puck into the far goal; goals in a row score more and the first
## to 7 wins. The rink is simulated in world units on the table plane (the simulation's y is the
## world's z); [HockeyScene] draws it in 3D and touches land on the mallet's grip plane.

# Rink, in world units (x across, y from the CPU's end to the player's end).
const RL := HockeyGeo.RL
const RR := HockeyGeo.RR
const RT := HockeyGeo.RT
const RB := HockeyGeo.RB
const CX := HockeyGeo.CX
const CY := HockeyGeo.CY
const GOAL_HALF := HockeyGeo.GOAL_HALF
const PUCK_R := HockeyGeo.PUCK_R
const MALLET_R := HockeyGeo.MALLET_R
const RAIL_H := HockeyGeo.RAIL_H
const SUBSTEPS := 4
## Radius of the table's rounded corners, which steer pucks back into play.
const CORNER_R := HockeyGeo.CORNER_R
## Height of the mallet's grip plane, where touches land.
const MALLET_H := HockeyGeo.MALLET_H

const HIT_COLORS := [Pal.WHITE, Pal.CYAN]
const GOAL_COLORS := [Pal.CYAN, Pal.WHITE, Pal.YELLOW]


class Disc:
	extends RefCounted
	var x := 0.0
	var y := 0.0
	var vx := 0.0
	var vy := 0.0


var _puck := Disc.new()
var _me := Disc.new()
var _cpu := Disc.new()
var _target_x := CX
var _target_y := RB - 60.0
var _dragging := -1
var _player_goals := 0
var _cpu_goals := 0
var _streak := 0
var _serve_t := 0.0
var _serve_to_cpu := false
var _puck_live := false
var _goal_flash := 0.0
var _goal_by_player := false
var _cpu_see_x := CX
var _cpu_see_y := CY
var _cpu_see_vx := 0.0
var _cpu_see_vy := 0.0
var _cpu_think_t := 0.0
var _hit_cooldown := 0.0
var _stuck_t := 0.0

## All the presentation state (glows, the puck's trail); the simulation never reads it.
var _scene := HockeyScene.new()

var _stage := Stage3D.new(int(GAME_W), int(GAME_H))
## Kotlin's `pt` scratch: the last point projected to the field (kept when a projection fails).
var _pt_x := 0.0
var _pt_y := 0.0


func _init() -> void:
	id = "airhockey"
	title = "AIR HOCKEY"
	marquee = "HOCKEY"
	instructions = PackedStringArray([
		"DRAG YOUR MALLET",
		"SMASH THE PUCK INTO",
		"THE FAR GOAL",
		"GOALS IN A ROW SCORE MORE",
		"FIRST TO 7 WINS!",
	])
	look = MiniGame.CabinetLook.new(Pal.SKY, Pal.WHITE, Pal.CYAN, MiniGame.CabinetShape.AIR_HOCKEY)
	round_seconds = HockeyTuning.ROUND_SECONDS
	_stage.look(CX, 560.0, 800.0, CX, 0.0, 320.0, 50.0)


func reset() -> void:
	_me.x = CX
	_me.y = RB - 60.0
	_me.vx = 0.0
	_me.vy = 0.0
	_cpu.x = CX
	_cpu.y = RT + 60.0
	_cpu.vx = 0.0
	_cpu.vy = 0.0
	_target_x = _me.x
	_target_y = _me.y
	_dragging = -1
	_player_goals = 0
	_cpu_goals = 0
	_streak = 0
	_goal_flash = 0.0
	_goal_by_player = false
	# PLAY AGAIN reuses this instance: clear the CPU's view of the puck and every timer too, so a
	# replay plays out exactly like a fresh machine.
	_cpu_see_x = CX
	_cpu_see_y = CY
	_cpu_see_vx = 0.0
	_cpu_see_vy = 0.0
	_cpu_think_t = 0.0
	_hit_cooldown = 0.0
	_stuck_t = 0.0
	_scene.reset()
	_serve(false, 0.6)


func _serve(to_cpu: bool, delay: float) -> void:
	_serve_to_cpu = to_cpu
	_serve_t = delay
	_puck_live = false
	_puck.x = CX + rng.range_f(-30.0, 30.0)
	_puck.y = CY - 110.0 if to_cpu else CY + 110.0
	_puck.vx = 0.0
	_puck.vy = 0.0


func tickets_for(p_score: int) -> int:
	return HockeyTuning.BASE_TICKETS + p_score / HockeyTuning.POINTS_PER_TICKET


func is_settled() -> bool:
	return _goal_flash <= 0.3


## The buzzer freezes the player's mallet; a puck still in play can no longer score (see _goal).
func on_time_up() -> void:
	cancel_input()


## Lets go of the mallet: it stops where it is instead of chasing the lost finger.
func cancel_input() -> void:
	_dragging = -1
	_target_x = _me.x
	_target_y = _me.y


func on_touch(type: int, p_id: int, x: float, y: float, _time_ms: int) -> void:
	if type == TouchType.DOWN:
		if _dragging < 0 and not time_up and not ended_early:
			_dragging = p_id
			_aim_at(x, y)
	elif type == TouchType.MOVE:
		if p_id == _dragging:
			_aim_at(x, y)
	elif type == TouchType.UP:
		if p_id == _dragging:
			_dragging = -1


## The mallet chases the spot on the table under the finger.
func _aim_at(fx: float, fy: float) -> void:
	var hit: Variant = _stage.touch_to_plane(fx, fy, MALLET_H)
	if hit != null:
		var at: Vector2 = hit
		_target_x = at.x
		_target_y = at.y


## Kotlin's `stage.toField(x, y, z, pt)`: projects into _pt_x / _pt_y, which keep their last
## value when the point is behind the camera.
func _to_field(x: float, y: float, z: float) -> void:
	var v: Variant = _stage.to_field(x, y, z)
	if v != null:
		var p: Vector3 = v
		_pt_x = p.x
		_pt_y = p.y


func step(dt: float) -> void:
	_goal_flash = maxf(_goal_flash - dt, 0.0)
	_hit_cooldown -= dt
	if not _puck_live:
		_serve_t -= dt
		if _serve_t <= 0.0 and not time_up and not ended_early:
			_puck_live = true
			play(Sfx.BLIP, 0.5, 1.3)
	var h := dt / SUBSTEPS
	for i in SUBSTEPS:
		_sub_step(h)
	# A short glowing trail behind a fast puck (visual only).
	_scene.step(dt)
	_scene.trail(_puck.x, _puck.y, dt)


func _sub_step(h: float) -> void:
	# Player mallet: rate-limited chase of the finger, kept in the near half.
	var tx := clampf(_target_x, RL + MALLET_R, RR - MALLET_R)
	var ty := clampf(_target_y, CY + MALLET_R, RB - MALLET_R)
	_move_mallet(_me, tx, ty, HockeyTuning.PLAYER_MALLET_SPEED, h)
	_cpu_think(h)

	if not _puck_live:
		return
	var puck := _puck
	puck.x += puck.vx * h
	puck.y += puck.vy * h
	var drag := 1.0 - HockeyTuning.PUCK_DRAG * h
	puck.vx *= drag
	puck.vy *= drag

	# Side rails.
	if puck.x < RL + PUCK_R:
		puck.x = RL + PUCK_R
		puck.vx = absf(puck.vx) * HockeyTuning.WALL_BOUNCE
		_clack(puck.vx)
	if puck.x > RR - PUCK_R:
		puck.x = RR - PUCK_R
		puck.vx = -absf(puck.vx) * HockeyTuning.WALL_BOUNCE
		_clack(puck.vx)
	# End rails, with a goal slot in the middle of each.
	var in_mouth := absf(puck.x - CX) < GOAL_HALF - PUCK_R * 0.3
	if puck.y < RT + PUCK_R:
		if in_mouth:
			if puck.y < RT - PUCK_R:
				_goal(true)
		else:
			puck.y = RT + PUCK_R
			puck.vy = absf(puck.vy) * HockeyTuning.WALL_BOUNCE
			_clack(puck.vy)
	if puck.y > RB - PUCK_R:
		if in_mouth:
			if puck.y > RB + PUCK_R:
				_goal(false)
		else:
			puck.y = RB - PUCK_R
			puck.vy = -absf(puck.vy) * HockeyTuning.WALL_BOUNCE
			_clack(puck.vy)
	if not _puck_live:
		return
	_corners()
	_collide(_me)
	_collide(_cpu)
	# A mallet can shove the puck into a rail; keep it on the table.
	puck.x = clampf(puck.x, RL + PUCK_R, RR - PUCK_R)
	if absf(puck.x - CX) >= GOAL_HALF - PUCK_R * 0.3:
		puck.y = clampf(puck.y, RT + PUCK_R, RB - PUCK_R)
	var sp := sqrt(puck.vx * puck.vx + puck.vy * puck.vy)
	_stuck_t = _stuck_t + h if sp < 60.0 else 0.0
	if sp > HockeyTuning.PUCK_MAX_SPEED:
		puck.vx *= HockeyTuning.PUCK_MAX_SPEED / sp
		puck.vy *= HockeyTuning.PUCK_MAX_SPEED / sp


## Bounces the puck off the four rounded corners of the rink.
func _corners() -> void:
	var puck := _puck
	var cx := RL + CORNER_R if puck.x < CX else RR - CORNER_R
	var cy := RT + CORNER_R if puck.y < CY else RB - CORNER_R
	var in_x := puck.x < cx if puck.x < CX else puck.x > cx
	var in_y := puck.y < cy if puck.y < CY else puck.y > cy
	if not in_x or not in_y:
		return
	var dx := puck.x - cx
	var dy := puck.y - cy
	var d := sqrt(dx * dx + dy * dy)
	var max_d := CORNER_R - PUCK_R
	if d <= max_d or d < 1e-3:
		return
	var nx := dx / d
	var ny := dy / d
	puck.x = cx + nx * max_d
	puck.y = cy + ny * max_d
	var vn := puck.vx * nx + puck.vy * ny
	if vn > 0.0:
		puck.vx -= (1.0 + HockeyTuning.WALL_BOUNCE) * vn * nx
		puck.vy -= (1.0 + HockeyTuning.WALL_BOUNCE) * vn * ny
		_clack(vn)


func _move_mallet(m: Disc, tx: float, ty: float, max_speed: float, h: float) -> void:
	var dx := tx - m.x
	var dy := ty - m.y
	var d := sqrt(dx * dx + dy * dy)
	var step_len := max_speed * h
	var nx: float
	var ny: float
	if d <= step_len:
		nx = tx
		ny = ty
	else:
		nx = m.x + dx / d * step_len
		ny = m.y + dy / d * step_len
	m.vx = (nx - m.x) / h
	m.vy = (ny - m.y) / h
	m.x = nx
	m.y = ny


func _collide(m: Disc) -> void:
	var puck := _puck
	var dx := puck.x - m.x
	var dy := puck.y - m.y
	var d := sqrt(dx * dx + dy * dy)
	var min_d := PUCK_R + MALLET_R
	if d >= min_d or d < 1e-3:
		return
	var nx := dx / d
	var ny := dy / d
	puck.x = m.x + nx * min_d
	puck.y = m.y + ny * min_d
	var rel := (puck.vx - m.vx) * nx + (puck.vy - m.vy) * ny
	if rel < 0.0:
		var e := HockeyTuning.MALLET_BOUNCE
		puck.vx -= (1.0 + e) * rel * nx
		puck.vy -= (1.0 + e) * rel * ny
		if _hit_cooldown <= 0.0:
			_hit_cooldown = 0.08
			var power := MathUtil.clamp01(-rel / 900.0)
			play(Sfx.CLINK, 0.4 + power * 0.6, 0.7 + power * 0.6)
			if m == _me:
				fx.haptics.tick()
			_scene.mallet_hit(puck.x - nx * PUCK_R, puck.y - ny * PUCK_R, power, m == _me)
			if power > 0.6:
				shake.add(0.08)
				_to_field(puck.x, MALLET_H, puck.y)
				particles.burst(_pt_x, _pt_y, 8, 40.0, 160.0, HIT_COLORS, 0.3, 3.0)


func _clack(v: float) -> void:
	if absf(v) > 120.0 and _hit_cooldown <= 0.0:
		_hit_cooldown = 0.05
		play(Sfx.BOUNCE, clampf(absf(v) / 1200.0, 0.15, 0.6), 1.6)
		_scene.wall_hit(_puck.x, _puck.y, MathUtil.clamp01(absf(v) / 1200.0))


## The CPU sees the puck with a short delay, guards its goal when the puck is in the far half of
## the table, and lines up behind it to shoot when it drifts into its own half.
func _cpu_think(h: float) -> void:
	_cpu_think_t -= h
	if _cpu_think_t <= 0.0:
		_cpu_think_t = HockeyTuning.CPU_REACTION
		_cpu_see_x = _puck.x
		_cpu_see_y = _puck.y
		_cpu_see_vx = _puck.vx
		_cpu_see_vy = _puck.vy
	var lead := maxi(_player_goals - _cpu_goals, 0)
	var ramp := MathUtil.clamp01(time / round_seconds * 0.6 + lead * 0.12)
	var speed := lerpf(HockeyTuning.CPU_SPEED_START, HockeyTuning.CPU_SPEED_MAX, ramp)
	var home_y := RT + 50.0
	var tx: float
	var ty: float
	if not _puck_live:
		tx = CX
		ty = home_y
	elif _stuck_t > 1.2 and _cpu_see_y < CY:
		# Don't pin the puck against the rails: back off and give it room.
		tx = CX
		ty = home_y
		if _stuck_t > 2.2:
			_stuck_t = 0.0
	elif _cpu_see_y < CY - 10.0 and sqrt(_cpu_see_vx * _cpu_see_vx + _cpu_see_vy * _cpu_see_vy) < 700.0:
		# Attack: get behind the puck (on the far side from the player's goal) and drive through it.
		var gx := CX - _cpu_see_x
		var gy := RB - _cpu_see_y
		var gl := maxf(sqrt(gx * gx + gy * gy), 1.0)
		var behind := -6.0 if _cpu.y < _cpu_see_y - 8.0 else PUCK_R + MALLET_R + 6.0
		tx = _cpu_see_x - gx / gl * behind
		ty = _cpu_see_y - gy / gl * behind
	else:
		# Defend: stay between the puck and the goal, a little off the goal line.
		var t := MathUtil.clamp01((_cpu_see_y - RT) / (RB - RT))
		tx = lerpf(CX, _cpu_see_x, 0.35 + 0.4 * (1.0 - t))
		ty = home_y + (0.0 if _cpu_see_vy < 0.0 else 30.0 * (1.0 - t))
	tx = clampf(tx, RL + MALLET_R, RR - MALLET_R)
	ty = clampf(ty, RT + MALLET_R, CY - MALLET_R)
	_move_mallet(_cpu, tx, ty, speed, h)


func _goal(by_player: bool) -> void:
	_puck_live = false
	if time_up:
		# After the buzzer the puck just drops into the slot: no score, no goal flash to wait on.
		_scene.clear_trail()
		return
	_goal_flash = 1.2
	_goal_by_player = by_player
	_scene.clear_trail()
	_scene.goal(by_player)
	if by_player:
		_player_goals += 1
		_streak += 1
		var pts := HockeyTuning.GOAL_POINTS + (_streak - 1) * HockeyTuning.STREAK_BONUS
		_to_field(CX, 30.0, RT)
		add_score(pts, _pt_x, _pt_y + 40.0, Pal.YELLOW)
		popups.add("HAT TRICK!" if _streak >= 3 else "GOAL!", CX, 250.0, Pal.CYAN, 5.0, 1.2)
		play(Sfx.WIN)
		play(Sfx.CHEER, 0.6)
		fx.haptics.win()
		shake.add(0.35)
		particles.burst(_pt_x, _pt_y, 40, 80.0, 280.0, GOAL_COLORS, 0.8, 5.0, 0.0, 2.0, Particles.SPARKLE)
		if _player_goals >= HockeyTuning.GOALS_TO_WIN:
			add_score(HockeyTuning.WIN_BONUS, CX, 320.0, Pal.GOLD, "YOU WIN +%d" % HockeyTuning.WIN_BONUS)
			play(Sfx.JACKPOT)
			particles.confetti(0.0, 0.0, GAME_W, 90)
			ended_early = true
		else:
			_serve(true, 1.2)
	else:
		_cpu_goals += 1
		_streak = 0
		popups.add("CPU SCORES", CX, 420.0, Pal.RED, 3.0, 1.1)
		play(Sfx.BOMB, 0.5, 1.3)
		fx.haptics.heavy()
		shake.add(0.25)
		if _cpu_goals >= HockeyTuning.GOALS_TO_WIN:
			ended_early = true
		else:
			_serve(false, 1.2)


# ---------------------------------------------------------------- 3D presentation

func render(scope: DrawScope) -> void:
	var r := _stage.begin()
	_scene.light(r, _puck.x, _puck.y, _puck_live, _goal_flash, _goal_by_player)
	r.gradient(0xFF040812, Pal.shade(Pal.NAVY, 0.7))
	_scene.update_board(_player_goals, _cpu_goals)
	_scene.draw_room(r, time, _goal_flash, _goal_by_player)
	_scene.draw_table(r, time, _goal_flash, _goal_by_player)
	_scene.draw_scoreboard(r, time, _goal_flash, _goal_by_player)
	_scene.draw_pieces(r, time,
		_puck.x, _puck.y, _puck.vx, _puck.vy, _puck_live or _serve_t < 0.9, _puck_live,
		_me.x, _me.y, sqrt(_me.vx * _me.vx + _me.vy * _me.vy), _cpu.x, _cpu.y, sqrt(_cpu.vx * _cpu.vx + _cpu.vy * _cpu.vy),
		_serve_t, not time_up and not ended_early)
	_scene.draw_effects(r)
	_stage.present()

	if _dragging < 0 and not time_up and time < 4.0:
		var a := 0.5 + 0.5 * sin(time * 6.0)
		ArcadeFont.draw_centered(scope, "DRAG YOUR MALLET", GAME_W / 2.0, 612.0, 2.0, Pal.WHITE, a)


# ---------------------------------------------------------------- simulation-test hooks

func bot_puck_x() -> float:
	return _puck.x


func bot_puck_y() -> float:
	return _puck.y


func bot_puck_vx() -> float:
	return _puck.vx


func bot_puck_vy() -> float:
	return _puck.vy


func bot_mallet_x() -> float:
	return _me.x


func bot_mallet_y() -> float:
	return _me.y


func bot_cpu_x() -> float:
	return _cpu.x


func bot_cpu_y() -> float:
	return _cpu.y


## (player goals, CPU goals): Kotlin's `botGoals` pair.
func bot_goals() -> Vector2i:
	return Vector2i(_player_goals, _cpu_goals)


## Puts the puck in play at ([param x], [param y]) moving at ([param vx], [param vy]), for tests of edge cases.
func bot_place_puck(x: float, y: float, vx: float, vy: float) -> void:
	_puck.x = x
	_puck.y = y
	_puck.vx = vx
	_puck.vy = vy
	_puck_live = true


## Where the table point ([param x], [param y]) is on screen, for bots that play by touch.
func bot_screen(x: float, y: float) -> Vector2:
	_to_field(x, MALLET_H, y)
	return Vector2(_pt_x, _pt_y)


# ---------------------------------------------------------------- attract mode

## A triangle wave: 0 to 1 and back, once per 2 units of [param v].
func _tri(v: float) -> float:
	var m := fmod(fmod(v, 2.0) + 2.0, 2.0)
	return m if m < 1.0 else 2.0 - m


func _bounce_x(wf: float, t: float) -> float:
	return 2.4 + (wf - 4.8) * _tri(t * 0.62 + 0.2)


func _bounce_y(hf: float, t: float) -> float:
	return 2.8 + (hf - 5.6) * _tri(t * 0.91 + 0.35)


## The table seen from above in miniature: neon markings on a dark surface, a puck with a hot trail
## ricocheting between two mallets, and every seven seconds a goal in the far slot with a flash, a
## burst and GOAL!, after an "AIR HOCKEY" title card. [param w] × [param h] is the cabinet's
## small screen.
func draw_attract(p: Painter, w: int, h: int, tm: float) -> void:
	var wf := float(w)
	var hf := float(h)
	var cx := wf / 2.0
	var cyc := 7.0
	var tt := fmod(tm, cyc)
	var goal_at := 5.6
	# The table: navy playfield in a glowing frame, a wash of each team's colour, air holes.
	p.fill(0.0, 0.0, wf, hf, 0xFF071228)
	p.fill(1.2, 1.2, wf - 2.4, hf - 2.4, 0xFF0C1E44)
	p.fill(1.2, 1.2, wf - 2.4, (hf - 2.4) / 2.0, Pal.PINK, 0.07)
	p.fill(1.2, hf / 2.0, wf - 2.4, (hf - 2.4) / 2.0, Pal.CYAN, 0.07)
	for gy in 6:
		for gx in 8:
			p.disc(2.4 + gx * 2.7, 2.4 + gy * 2.6, 0.16, 0xFF2E5C96, 0.8)
	p.frame(0.3, 0.3, wf - 0.6, hf - 0.6, Pal.SKY, 0.85)
	# Centre line and ring, and the two goal slots with their team glow.
	p.fill(1.2, hf / 2.0 - 0.25, wf - 2.4, 0.5, 0xFFB8F4FF, 0.9)
	for k in 16:
		var a := k / 16.0 * 6.2832
		p.disc(cx + cos(a) * 3.4, hf / 2.0 + sin(a) * 3.4, 0.3, 0xFFB8F4FF, 0.8)
	p.fill(cx - 3.4, 0.0, 6.8, 1.3, 0xFF020308)
	p.fill(cx - 3.4, hf - 1.3, 6.8, 1.3, 0xFF020308)
	p.fill(cx - 3.4, 1.3, 6.8, 0.4, Pal.HOTPINK, 0.9)
	p.fill(cx - 3.4, hf - 1.7, 6.8, 0.4, Pal.CYAN, 0.9)

	# The puck ricochets round the table; in the goal spell it is driven into the far slot.
	var px: float
	var py: float
	if tt < goal_at:
		px = _bounce_x(wf, tt)
		py = _bounce_y(hf, tt)
		for k in range(4, 0, -1):
			var u := maxf(tt - k * 0.05, 0.0)
			p.disc(_bounce_x(wf, u), _bounce_y(hf, u), 0.9 * (1.0 - k * 0.16), Pal.ORANGE, 0.5 - k * 0.09)
	else:
		var u := MathUtil.clamp01((tt - goal_at) / 0.45)
		px = lerpf(_bounce_x(wf, goal_at), cx, u)
		py = lerpf(_bounce_y(hf, goal_at), -0.8, u)
		for k in range(4, 0, -1):
			var uu := MathUtil.clamp01((tt - goal_at - k * 0.04) / 0.45)
			p.disc(lerpf(_bounce_x(wf, goal_at), cx, uu), lerpf(_bounce_y(hf, goal_at), -0.8, uu), 0.9 * (1.0 - k * 0.16), Pal.ORANGE, 0.5 - k * 0.09)
	# Mallets follow it: cyan at the near end, pink at the far.
	var me_x := cx + (_bounce_x(wf, tt - 0.25) - cx) * 0.85
	var cpu_x := cx + (cx - _bounce_x(wf, tt - 0.1)) * 0.7
	p.disc(me_x, hf - 3.1, 2.3, Pal.CYAN, 0.22)
	p.disc(me_x, hf - 3.1, 1.5, 0xFF29C8E8)
	p.disc(me_x - 0.35, hf - 3.45, 0.55, 0xFFB8F4FF, 0.8)
	p.disc(cpu_x, 3.1, 2.3, Pal.PINK, 0.22)
	p.disc(cpu_x, 3.1, 1.5, 0xFFE8408C)
	p.disc(cpu_x - 0.35, 2.75, 0.55, 0xFFFFD0E8, 0.8)
	p.disc(px, py, 1.05, 0xFFE0421E)
	p.disc(px, py, 0.5, 0xFFFFE0B0)

	# The goal: the far end floods cyan, sparks fly from the slot and GOAL! flashes.
	var age := tt - goal_at - 0.45
	if age >= 0.0 and age <= 1.5:
		var fade := 1.0 - age / 1.5
		p.fill(1.2, 1.2, wf - 2.4, hf * 0.4, Pal.CYAN, 0.3 * fade)
		for i in 10:
			var a := 0.35 + i / 9.0 * 2.4
			var d := age * 9.0
			p.disc(cx + cos(a) * d, 0.6 + sin(a) * d, 0.35, Pal.WHITE if i % 2 == 0 else Pal.YELLOW, fade)
		if int(age * 6.0) % 2 == 0:
			p.text_centered("GOAL!", cx, hf / 2.0 - 2.4, Pal.YELLOW, true, 1.0, 0.9)
	# Title card at the top of each loop.
	if tt < 1.6:
		var a := MathUtil.clamp01(minf(tt / 0.2, (1.6 - tt) / 0.4))
		p.fill(1.2, hf / 2.0 - 3.1, wf - 2.4, 6.2, 0xFF020308, 0.6 * a)
		p.text_centered("AIR HOCKEY", cx, hf / 2.0 - 1.6, Pal.CYAN, true, a, 0.62)
