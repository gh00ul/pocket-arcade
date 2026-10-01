extends ArcadeGame

const BALL_R: float = 9.0
const BUMPER_POS = [Vector2(100, 150), Vector2(172, 150), Vector2(136, 206)]
const COLORS = [Color("fa57bd"), Color("4ce7f5"), Color("ffd862")]
var balls: Array[Dictionary] = []
var walls: Array[Dictionary] = []
var touches: Dictionary = {}
var flip_nodes: Array[Node3D] = []
var flip_angles: Array[float] = [0.52, PI - 0.52]
var flip_omega: Array[float] = [0.0, 0.0]
var bumper_nodes: Array[MeshInstance3D] = []
var bumper_cool: Array[float] = [0.0, 0.0, 0.0]
var lane_nodes: Array[MeshInstance3D] = []
var lanes: Array[bool] = [false, false, false]
var drop_nodes: Array[MeshInstance3D] = []
var drops: Array[bool] = [true, true, true]
var bank_reset: float = 0.0
var multiplier: int = 1
var multiball: bool = false
var lives: int = 3
var save_left: float = 0.0
var serve_left: float = 0.0
var tilt: float = 0.0
var tilted: bool = false
var elapsed: float = 0.0
var plunger_id: int = -1
var plunger_start: float = 0.0
var pull: float = 0.0
var message_left: float = 0.0
var info: Label3D

func _init() -> void:
	game_id = "pinball"
	title = "STAR FLIPPER"
	duration = 60.0
	instructions = "Hold the left or right half to flip. Pull the lower-right plunger down and release. Light three top lanes for multiball. Drop targets raise your multiplier. Swipe up to nudge — too much tilts!"

func _world(p: Vector2, height: float = 0.12) -> Vector3:
	return Vector3((p.x - 150.0) * 0.02, height, (p.y - 320.0) * 0.02)

func build_game() -> void:
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 17.7
	camera.position = Vector3(0, 18, 3)
	camera.look_at(Vector3.ZERO)
	box(Vector3(0, -0.16, 0), Vector3(6.2, 0.3, 13.3), Color("1e183a"))
	box(Vector3(-0.25, 0.001, -0.2), Vector3(4.6, 0.015, 9.9), Color("302250"))
	for i: int in 25:
		var p: Vector2 = Vector2(rng.randf_range(48, 240), rng.randf_range(110, 490))
		var star: MeshInstance3D = cylinder(_world(p, 0.04), 0.035, 0.01, Color("9e67cf"))
		star.scale.z = 1.4
	_add_wall(Vector2(0, 70), Vector2(0, 660), 0.45)
	_add_wall(Vector2(300, 70), Vector2(300, 660), 0.45)
	_add_wall(Vector2(70, 0), Vector2(230, 0), 0.45)
	for side: int in 2:
		var center: Vector2 = Vector2(70 if side == 0 else 230, 70)
		for i: int in 8:
			var a: float = PI + side * PI * 0.5 + i * PI / 16.0
			_add_wall(center + Vector2(cos(a), sin(a)) * 70, center + Vector2(cos(a + PI / 16), sin(a + PI / 16)) * 70, 0.45)
	_add_wall(Vector2(272, 160), Vector2(272, 660), 0.4)
	_add_wall(Vector2(272, 589), Vector2(300, 589), 0.1)
	_add_wall(Vector2(40, 120), Vector2(40, 330), 0.4)
	for side: int in 2:
		_pair_wall(Vector2(0, 350), Vector2(20, 378), side, 0.35)
		_pair_wall(Vector2(22, 398), Vector2(22, 476), side, 0.35)
		_pair_wall(Vector2(22, 476), Vector2(64, 506), side, 0.25)
		_pair_wall(Vector2(50, 410), Vector2(50, 464), side, 0.5)
		_pair_wall(Vector2(50, 464), Vector2(84, 488), side, 0.5)
		_pair_wall(Vector2(84, 488), Vector2(50, 410), side, 0.6, 1)
	for x: float in [76.0, 116.0, 156.0, 196.0]:
		_add_wall(Vector2(x, 44), Vector2(x, 84), 0.4)
	_add_wall(Vector2(272, 196), Vector2(258, 230), 0.35)
	for i: int in 3:
		cylinder(_world(BUMPER_POS[i], 0.1), 0.47, 0.18, Color("171c39"))
		bumper_nodes.append(cylinder(_world(BUMPER_POS[i], 0.3), 0.34, 0.3, COLORS[i]))
		cylinder(_world(BUMPER_POS[i], 0.49), 0.23, 0.08, Color.WHITE)
		lane_nodes.append(cylinder(_world(Vector2(96 + i * 40, 66), 0.04), 0.14, 0.04, Color("456c83")))
		drop_nodes.append(box(_world(Vector2(258, 248 + 30 * i), 0.26), Vector3(0.14, 0.45, 0.46), COLORS[i], 0.35))
	for side: int in 2:
		var node := Node3D.new()
		add_child(node)
		node.position = _world(Vector2(74 if side == 0 else 198, 520), 0.18)
		box(Vector3(0.51, 0, 0), Vector3(1.06, 0.25, 0.25), Color("fff1cd"), 0, node)
		cylinder(Vector3.ZERO, 0.2, 0.3, COLORS[side], node)
		flip_nodes.append(node)
	label3d("STAR FLIPPER", _world(Vector2(136, 330), 0.08), 45, Color("e6aafa"))
	label3d("MULTIBALL\nLIGHT ALL THREE", _world(Vector2(136, 102), 0.13), 22, Color("67eafa"))
	label3d("PULL ↓", _world(Vector2(283, 612), 0.3), 24, Color("ffe49d"))
	label3d("◀ HOLD       HOLD ▶", _world(Vector2(136, 591), 0.3), 28, Color("d5c3ef"))
	info = label3d("", _world(Vector2(136, 626), 0.3), 23, Color("ffd976"))
	_serve(false)

func _add_wall(a: Vector2, b: Vector2, bounce: float, kind: int = 0) -> void:
	walls.append({"a": a, "b": b, "bounce": bounce, "kind": kind})
	var rail: MeshInstance3D = box((_world(a) + _world(b)) * 0.5, Vector3(a.distance_to(b) * 0.02, 0.3, 0.08), Color("55dbe9") if kind == 0 else Color("ef5fc4"), 0.15)
	rail.rotation.y = -(b - a).angle()

func _pair_wall(a: Vector2, b: Vector2, side: int, bounce: float, kind: int = 0) -> void:
	if side == 1:
		a.x = 272 - a.x
		b.x = 272 - b.x
	_add_wall(a, b, bounce, kind)

func _serve(auto: bool) -> void:
	var mesh: MeshInstance3D = sphere(_world(Vector2(286, 575), 0.2), 0.18, Color("edf7ff"))
	balls.append({"pos": Vector2(286, 575), "vel": Vector2.ZERO, "node": mesh, "lane": true, "auto": 0.6 if auto else -1.0, "slow": 0.0, "cradled": false})
	_message("BALL SAVED" if auto else "PULL THE PLUNGER ↓", 1.2)

func _launch(b: Dictionary, strength: float) -> void:
	b.lane = false
	b.vel = Vector2(0, -(500.0 + 1250.0 * strength))
	save_left = maxf(save_left, 6.0)
	beep(240, 0.08)

func step(dt: float) -> void:
	elapsed += dt
	message_left = maxf(0, message_left - dt)
	tilt = maxf(0, tilt - dt * 0.45)
	save_left = maxf(0, save_left - dt)
	for i: int in 3:
		bumper_cool[i] = maxf(0, bumper_cool[i] - dt)
		bumper_nodes[i].scale = Vector3.ONE * (1.16 if bumper_cool[i] > 0 else 1.0)
	if bank_reset > 0:
		bank_reset -= dt
		if bank_reset <= 0:
			for i: int in 3:
				drops[i] = true
				drop_nodes[i].visible = true
	for side: int in 2:
		var held: bool = _held(side) and not tilted
		var target: float = (-0.45 if side == 0 else PI + 0.45) if held else (0.52 if side == 0 else PI - 0.52)
		var next_angle: float = move_toward(flip_angles[side], target, (24.0 if held else 14.0) * dt)
		flip_omega[side] = (next_angle - flip_angles[side]) / maxf(dt, 0.001)
		flip_angles[side] = next_angle
		flip_nodes[side].rotation.y = -next_angle
	for b: Dictionary in balls:
		if b.lane:
			if b.auto >= 0.0:
				b.auto -= dt
				if b.auto <= 0.0:
					_launch(b, 0.92)
			b.node.position = _world(b.pos, 0.2)
			continue
		var count: int = clampi(int(ceil(b.vel.length() * dt / (BALL_R * 0.45))), 2, 128)
		b.cradled = false
		for sub: int in count:
			_physics_ball(b, dt / count)
			if b.lane:
				break
		if b.vel.length() < 30.0 and not b.cradled and not b.lane:
			b.slow += dt
			if b.slow > 3.0:
				b.vel = Vector2(rng.randf_range(-150, 150), -480)
				b.slow = 0
				_message("BALL SEARCH", 1.0)
		else:
			b.slow = 0
		b.node.position = _world(b.pos, 0.2)
	for i: int in range(balls.size() - 1, -1, -1):
		if balls[i].pos.y > 640:
			balls[i].node.queue_free()
			balls.remove_at(i)
			if save_left > 0 and not tilted:
				_serve(true)
	if balls.is_empty() and serve_left <= 0:
		lives -= 1
		multiball = false
		if lives <= 0:
			finish()
			return
		serve_left = 1.1
	if serve_left > 0:
		serve_left -= dt
		if serve_left <= 0:
			tilted = false
			tilt = 0
			_serve(false)
	info.text = "BALL %d/3   ×%d   %s" % [4 - lives, multiplier, "SAVE" if save_left > 0 else ""]
	if message_left <= 0:
		status = "TILT! Flippers disabled until next ball" if tilted else ("MULTIBALL! Left orbit = 300 jackpot" if multiball else "Hold left / right • upward swipe nudges")

func _physics_ball(b: Dictionary, dt: float) -> void:
	var previous: Vector2 = b.pos
	b.vel.y += 1000.0 * dt
	b.vel *= exp(-0.04 * dt)
	b.pos += b.vel * dt
	if b.pos.x > 272 and b.pos.y > 572 and b.vel.y > 0:
		# An underpowered shot rolls back onto the plunger and can be relaunched.
		b.lane = true
		b.auto = -1.0
		b.pos = Vector2(286, 575)
		b.vel = Vector2.ZERO
		_message("PULL FURTHER TO LAUNCH", 1.5)
		return
	if b.pos.x > 272 and b.pos.y < 140 and b.vel.y < 0:
		b.vel.x = -380.0
	# One-way gate keeps a returning ball out of the shooter lane.
	if b.pos.x > 263 and b.pos.y > 145 and previous.y <= 145 and b.vel.y > 0:
		b.pos.x = 260
		b.vel.x = -absf(b.vel.x) - 100
	for wall: Dictionary in walls:
		var impact: float = _segment_hit(b, wall.a, wall.b, 2.0, wall.bounce, Vector2.ZERO)
		if wall.kind == 1 and impact > 70 and not tilted:
			var direction: Vector2 = (b.pos - (wall.a + wall.b) * 0.5).normalized()
			b.vel += direction * 470
			award(5 * multiplier)
	for i: int in 3:
		var delta: Vector2 = b.pos - BUMPER_POS[i]
		if delta.length() < BALL_R + 17:
			var normal: Vector2 = delta.normalized() if delta.length() > 0.001 else Vector2.UP
			b.pos = BUMPER_POS[i] + normal * (BALL_R + 17.1)
			if b.vel.dot(normal) < 0:
				b.vel -= normal * b.vel.dot(normal) * 1.5
			if bumper_cool[i] <= 0 and not tilted:
				b.vel += normal * 560
				bumper_cool[i] = 0.1
				award(10 * multiplier)
				beep(700.0 + i * 120, 0.025)
		if drops[i] and not tilted:
			var impact: float = _segment_hit(b, Vector2(258, 236 + i * 30), Vector2(258, 260 + i * 30), 3, 0.65, Vector2.ZERO)
			if impact > 60:
				drops[i] = false
				drop_nodes[i].visible = false
				award(25 * multiplier)
				if not drops.has(true):
					award(100 * multiplier)
					multiplier = mini(5, multiplier + 1)
					bank_reset = 1.0
					_message("TARGET BANK! MULTIPLIER ×%d" % multiplier, 1.6)
		if (previous.y - 64) * (b.pos.y - 64) < 0 and absf(b.pos.x - (96 + i * 40)) < 16 and not tilted:
			award((5 if lanes[i] else 25) * multiplier)
			lanes[i] = true
			lane_nodes[i].scale = Vector3.ONE * 1.8
			if not lanes.has(false):
				award(150 * multiplier)
				multiball = true
				save_left = 8.0
				# Deferred to avoid modifying the ball array while stepping it.
				call_deferred("_add_multiball")
				lanes = [false, false, false]
				for lamp: MeshInstance3D in lane_nodes:
					lamp.scale = Vector3.ONE
				_message("MULTIBALL! +150", 2.0)
	if b.pos.x < 40 and (previous.y - 232) * (b.pos.y - 232) < 0 and not tilted:
		award((300 if multiball else 40) * multiplier + 3 * multiplier)
		_message("JACKPOT!" if multiball else "LEFT ORBIT +40", 1.0)
	for side: int in 2:
		var pivot: Vector2 = Vector2(74 if side == 0 else 198, 520)
		var end: Vector2 = pivot + Vector2(cos(flip_angles[side]), sin(flip_angles[side])) * 52
		var relative: Vector2 = b.pos - pivot
		var surface: Vector2 = Vector2(-relative.y, relative.x) * flip_omega[side]
		var impact: float = _segment_hit(b, pivot, end, 7, 0.3, surface)
		if impact > 0 and impact < 45 and _held(side):
			b.cradled = true
	b.vel = b.vel.limit_length(2000)

func _segment_hit(b: Dictionary, a: Vector2, end: Vector2, radius: float, bounce: float, surface: Vector2) -> float:
	var segment: Vector2 = end - a
	var nearest: Vector2 = a + segment * clampf((b.pos - a).dot(segment) / maxf(segment.length_squared(), 0.001), 0, 1)
	var delta: Vector2 = b.pos - nearest
	var distance: float = delta.length()
	if distance >= BALL_R + radius:
		return 0
	var normal: Vector2 = delta / distance if distance > 0.001 else Vector2(-segment.y, segment.x).normalized()
	b.pos = nearest + normal * (BALL_R + radius + 0.05)
	var speed: float = (b.vel - surface).dot(normal)
	if speed < 0:
		b.vel -= normal * speed * (1.0 + bounce)
	return -speed

func _add_multiball() -> void:
	if running and not finished and balls.size() < 3:
		_serve(true)

func _held(side: int) -> bool:
	for t: Dictionary in touches.values():
		if t.side == side:
			return true
	return (Input.is_key_pressed(KEY_LEFT) or Input.is_key_pressed(KEY_A)) if side == 0 else (Input.is_key_pressed(KEY_RIGHT) or Input.is_key_pressed(KEY_D))

func pointer(action: String, pos: Vector2, id: int) -> void:
	if not running or finished:
		return
	if action == "down":
		if pos.x > 0.72 and pos.y > 0.60:
			for b: Dictionary in balls:
				if b.lane and plunger_id < 0:
					plunger_id = id
					plunger_start = pos.y
					pull = 0.0
					return
		touches[id] = {"side": 0 if pos.x < 0.5 else 1, "start": pos, "time": elapsed, "nudged": false}
	elif action == "move":
		if id == plunger_id:
			pull = clampf((pos.y - plunger_start) / 0.14, 0, 1)
			_message("PLUNGER %d%% — RELEASE" % int(pull * 100), 0.5)
		elif touches.has(id):
			var t: Dictionary = touches[id]
			if not t.nudged and t.start.y - pos.y > 0.065 and elapsed - t.time < 0.25:
				t.nudged = true
				tilt += 1.0
				if tilt >= 2.6:
					tilted = true
					_message("TILT!", 2.0)
				else:
					for b: Dictionary in balls:
						if not b.lane:
							b.vel.y -= 170
					_message("DANGER — EASY ON THE NUDGES" if tilt >= 1.6 else "NUDGE", 1.0)
	elif action == "up":
		if id == plunger_id:
			for b: Dictionary in balls:
				if b.lane:
					_launch(b, pull if pull >= 0.08 else 0.85)
					break
			plunger_id = -1
			pull = 0.0
		touches.erase(id)

func cancel_input() -> void:
	touches.clear()
	plunger_id = -1
	pull = 0.0

func _message(text: String, seconds: float) -> void:
	status = text
	message_left = seconds

func tickets_for_score() -> int:
	return 1 + int(score / 60)
