extends ArcadeGame

const CATALOG: Array[Array] = [
	["plush_bear", "PIXEL BEAR", 26.0, 100, 10, Color("ad794e")],
	["plush_slime", "SLIME PAL", 22.0, 60, 14, Color("a5df57")],
	["plush_cat", "ROBO CAT", 24.0, 80, 12, Color("c4d7e5")],
	["plush_duck", "SPACE DUCK", 22.0, 70, 12, Color("ffe168")],
	["plush_ghost", "MINI GHOST", 21.0, 60, 14, Color("ece9ff")],
	["plush_dino", "DINO DAN", 28.0, 120, 8, Color("77ce81")],
	["plush_octo", "OCTO POP", 24.0, 90, 10, Color("ef8cbe")],
	["plush_bunny", "STAR BUN", 23.0, 90, 10, Color("bda0e2")],
	["plush_frog", "FROG PRINCE", 25.0, 110, 7, Color("68c983")],
	["plush_whale", "BLOOP WHALE", 30.0, 150, 5, Color("69badd")],
	["plush_golden", "GOLDEN CAT", 24.0, 400, 1, Color("f3c654")]
]

class Plush:
	var x: float = 0
	var y: float = 0
	var vx: float = 0
	var vy: float = 0
	var radius: float = 24
	var kind: int = 0
	var node: Node3D
	var held: bool = false

var pile: Array[Plush] = []
var state: int = 0
var state_time: float = 0
var trolley_x: float = 200
var trolley_v: float = 0
var cable: float = 36
var drop_cable: float = 36
var theta: float = 0
var theta_v: float = 0
var openness: float = 1
var held: Plush
var slip_at: float = 99
var progress: float = 0
var carry_start: float = 200
var lucky: bool = false
var move_pointers: Dictionary = {}
var drag_id: int = -1
var target_x: float = -1
var gantry: Node3D
var claw_root: Node3D
var cable_node: MeshInstance3D
var left_prong: Node3D
var right_prong: Node3D
var sign: Label3D
var respawns: Array[float] = []
var won: int = 0

func _init() -> void:
	game_id = "claw"
	title = "CLAW MACHINE"
	instructions = "Hold LEFT or RIGHT to move, or drag over the prizes to aim. Tap DROP to grab. Center the claw carefully: off-center prizes can slip. Watch for a lucky claw! Won plushies join your collection."
	duration = 45.0

func build_game() -> void:
	camera.position = Vector3(0, 2.6, 10.6)
	camera.look_at(Vector3(0, 2.05, 0))
	camera.fov = 47
	box(Vector3(0, -0.4, 0), Vector3(3.75, 0.7, 1.7), Color("732957"))
	box(Vector3(0.35, -0.02, 0), Vector3(2.85, 0.12, 1.6), Color("ffbddb"))
	box(Vector3(0, 2.3, -0.85), Vector3(3.75, 4.7, 0.12), Color("352c59"))
	for x in [-1.82, 1.82]:
		box(Vector3(x, 2.25, 0), Vector3(0.15, 4.65, 1.75), Color("d55795"))
		box(Vector3(x, 2.25, 0.9), Vector3(0.05, 4.6, 0.05), Color("ffb2dc"), 0.3)
	box(Vector3(0, 4.58, 0), Vector3(3.8, 0.32, 1.8), Color("b64588"))
	label3d("PLUSH PALACE", Vector3(0, 4.58, 0.98), 38, Color("fff0ad"))
	box(Vector3(0, 4.40, 0), Vector3(3.48, 0.09, 0.12), Color("b1c4dd"))
	box(Vector3(-1.32, -0.02, 0.2), Vector3(0.7, 0.07, 1.0), Color("151a34"))
	box(Vector3(-0.92, 0.36, 0), Vector3(0.09, 0.8, 1.5), Color("934471"))
	label3d("PRIZES", Vector3(-1.30, 0.15, 0.83), 18, Color("ffdf8b"))
	gantry = Node3D.new()
	add_child(gantry)
	box(Vector3.ZERO, Vector3(0.48, 0.2, 0.4), Color("4bcddb"), 0, gantry)
	cable_node = box(Vector3.ZERO, Vector3(0.024, 1, 0.024), Color("d6d6e3"))
	claw_root = Node3D.new()
	add_child(claw_root)
	cylinder(Vector3.ZERO, 0.12, 0.14, Color("e3e4f0"), claw_root)
	left_prong = _prong(-1)
	right_prong = _prong(1)
	for i in range(14):
		var p: Plush = _add_plush()
		p.x = 120 + (i % 4) * 55 + rng.randf_range(-6, 6)
		p.y = 440 - int(i / 4) * 51
	for i in range(180):
		_physics(1.0 / 120)
	for p in pile:
		p.vx = 0
		p.vy = 0
		_place(p)
	for i in range(3):
		var x: float = -1.24 + i * 1.21
		box(Vector3(x, -0.54, 0.97), Vector3(0.98, 0.37, 0.21), Color("2c647a") if i < 2 else Color("f0649d"))
		label3d(["◀ LEFT", "RIGHT ▶", "DROP"][i], Vector3(x, -0.53, 1.10), 27, Color.WHITE)
	sign = label3d("CENTER YOUR GRAB", Vector3(0, 3.85, -0.7), 23, Color("dcc9f0"))
	_update_claw()
	status = "Hold LEFT / RIGHT • Tap DROP"

func _prong(side: float) -> Node3D:
	var root = Node3D.new()
	claw_root.add_child(root)
	root.position.x = side * 0.065
	var shaft = box(Vector3(side * 0.07, -0.16, 0), Vector3(0.045, 0.32, 0.06), Color("bec9da"), 0, root)
	shaft.rotation.z = side * 0.42
	box(Vector3(side * 0.02, -0.32, 0), Vector3(0.16, 0.045, 0.06), Color("ecedf3"), 0, root)
	return root

func _add_plush() -> Plush:
	var total: int = 0
	for data in CATALOG:
		total += int(data[4])
	var roll = rng.randi_range(0, total - 1)
	var kind: int = 0
	for i in range(CATALOG.size()):
		roll -= int(CATALOG[i][4])
		if roll < 0:
			kind = i
			break
	var p = Plush.new()
	p.kind = kind
	p.radius = CATALOG[kind][2]
	p.x = rng.randf_range(126, 314)
	p.y = 70
	p.node = _toy(kind, p.radius / 100)
	pile.append(p)
	return p

func _toy(kind: int, radius: float) -> Node3D:
	var root = Node3D.new()
	add_child(root)
	var color: Color = CATALOG[kind][5]
	var body = sphere(Vector3.ZERO, radius, color, root)
	body.scale = Vector3(1, 1.05, 0.80)
	if kind in [0, 2, 7, 10]:
		for side in [-1, 1]:
			var ear = sphere(Vector3(side * radius * 0.63, radius * 0.80, 0), radius * 0.33, color, root)
			ear.scale.y = 2.1 if kind == 7 else 1.1
	if kind == 8:
		for side in [-1, 1]:
			sphere(Vector3(side * radius * 0.55, radius * 0.70, 0.03), radius * 0.38, color, root)
	if kind == 6:
		for k in range(6):
			var a: float = k * TAU / 6
			sphere(Vector3(cos(a) * radius * 0.8, -radius * 0.62, sin(a) * radius * 0.5), radius * 0.3, color, root)
	if kind in [3, 5, 9]:
		for side in [-1, 1]:
			var fin = sphere(Vector3(side * radius * 0.9, -radius * 0.15, 0), radius * 0.35, color, root)
			fin.scale = Vector3(1.4, 0.45, 0.8)
	if kind in [0, 2, 7, 10]:
		var tummy = sphere(Vector3(0, -radius * 0.28, radius * 0.68), radius * 0.53, Color("f8dbce"), root)
		tummy.scale = Vector3(1, 0.8, 0.25)
	for side in [-1, 1]:
		sphere(Vector3(side * radius * 0.34, radius * 0.28, radius * 0.75), radius * 0.115, Color("20273e"), root)
		sphere(Vector3(side * radius * 0.34 - 0.006, radius * 0.31, radius * 0.84), radius * 0.033, Color.WHITE, root)
	var nose_color = Color("f99463") if kind == 3 else Color("d9809a")
	var nose = sphere(Vector3(0, radius * 0.06, radius * 0.87), radius * 0.12, nose_color, root)
	if kind == 3:
		nose.scale = Vector3(1.9, 0.7, 1.3)
	return root

func _place(p: Plush) -> void:
	p.node.position = Vector3((p.x - 180) / 100, (470 - p.y) / 100, 0.1)

func _physics(dt: float) -> void:
	for p in pile:
		if p.held:
			continue
		p.vy += 900 * dt
		p.vx *= exp(-0.9 * dt)
		p.x += p.vx * dt
		p.y += p.vy * dt
	for pass_index in range(4):
		for i in range(pile.size()):
			var a: Plush = pile[i]
			if not a.held:
				if a.x > 348 - a.radius:
					a.x = 348 - a.radius
					a.vx = -absf(a.vx) * 0.12
				if a.x > 88 - a.radius and a.y > 392:
					a.x = maxf(a.x, 88 + a.radius)
				if a.x >= 88 and a.y > 470 - a.radius:
					a.y = 470 - a.radius
					a.vy = minf(0, -a.vy * 0.08)
					a.vx *= 0.8
				if a.x < 12 + a.radius:
					a.x = 12 + a.radius
			for j in range(i + 1, pile.size()):
				var b: Plush = pile[j]
				if a.held or b.held:
					continue
				var delta = Vector2(b.x - a.x, b.y - a.y)
				var distance: float = delta.length()
				var span: float = a.radius + b.radius
				if distance >= span or distance < 0.01:
					continue
				var normal: Vector2 = delta / distance
				var push: Vector2 = normal * (span - distance) * 0.5
				a.x -= push.x
				a.y -= push.y
				b.x += push.x
				b.y += push.y
				var velocity: float = (b.vx - a.vx) * normal.x + (b.vy - a.vy) * normal.y
				if velocity < 0:
					var impulse: Vector2 = normal * (-velocity * 0.56)
					a.vx -= impulse.x
					a.vy -= impulse.y
					b.vx += impulse.x
					b.vy += impulse.y

func _enter(next: int) -> void:
	state = next
	state_time = 0

func step(dt: float) -> void:
	state_time += dt
	var old_velocity: float = trolley_v
	var old_cable: float = cable
	match state:
		0:
			var direction: float = 0
			for value in move_pointers.values():
				direction += float(value)
			var want: float = clampf(direction, -1, 1) * 170
			if target_x >= 0 and direction == 0:
				want = clampf((target_x - trolley_x) * 5, -170, 170)
			if expired:
				want = 0
			trolley_v = move_toward(trolley_v, want, (900 if want != 0 else 1400) * dt)
			openness = move_toward(openness, 1, dt * 3)
		1:
			cable += 200 * dt
			var tip: float = 26 + cable * cos(theta) + 28
			var contact: bool = tip >= 468
			for p in pile:
				if absf(p.x - _claw_x()) < p.radius * 0.85 + 8 and tip > p.y - p.radius * 0.3:
					contact = true
			if contact:
				drop_cable = cable
				_enter(2)
				beep(340, 0.10)
		2:
			openness = lerpf(1, 0.1, minf(1, state_time / 0.45))
			if state_time >= 0.45:
				_grab()
		3:
			cable = move_toward(cable, 36, 150 * dt)
			progress = 1 - (cable - 36) / maxf(1, drop_cable - 36)
			if cable <= 36:
				carry_start = trolley_x
				_enter(4)
		4:
			var to_go: float = 48 - trolley_x
			trolley_v = move_toward(trolley_v, clampf(to_go * 4, -150, 150), 900 * dt)
			progress = 1 + clampf(1 - absf(to_go) / maxf(1, absf(carry_start - 48)), 0, 1)
			if absf(to_go) < 1.5 and absf(trolley_v) < 12:
				trolley_v = 0
				_release(true)
				_enter(5)
		5:
			openness = move_toward(openness, 1, dt * 3)
			if state_time > 0.55:
				_enter(0)
				lucky = not expired and rng.randf() < 0.12
				if lucky:
					status = "LUCKY CLAW! Wider reach, guaranteed grip"
					beep(1250, 0.18)
	trolley_x = clampf(trolley_x + trolley_v * dt, 46, 322)
	var acceleration: float = (trolley_v - old_velocity) / maxf(dt, 0.001) * 0.35
	var cable_velocity: float = (cable - old_cable) / maxf(dt, 0.001)
	theta_v += (-(900 / cable) * sin(theta) - acceleration / cable * cos(theta) - (2 * cable_velocity / cable + 0.9) * theta_v) * dt
	theta = clampf(theta + theta_v * dt, -0.9, 0.9)
	if held != null:
		held.x = _claw_x()
		held.y = 26 + cable * cos(theta) + 15.4 + held.radius * 0.35
		if progress >= slip_at:
			_release(false)
			status = "So close! Center the next grab"
			beep(200, 0.15)
	_physics(dt)
	for i in range(pile.size() - 1, -1, -1):
		var p: Plush = pile[i]
		_place(p)
		if not p.held and p.x < 86 and p.y > 406:
			award(int(CATALOG[p.kind][3]))
			prizes.append(str(CATALOG[p.kind][0]))
			won += 1
			status = "%s! +%d" % [CATALOG[p.kind][1], CATALOG[p.kind][3]]
			beep(1450 if p.kind == 10 else 950, 0.2)
			p.node.queue_free()
			pile.remove_at(i)
			respawns.append(1.2)
	for i in range(respawns.size() - 1, -1, -1):
		respawns[i] -= dt
		if respawns[i] <= 0:
			_add_plush()
			respawns.remove_at(i)
	_update_claw()
	sign.text = "LUCKY CLAW!" if lucky else ("CENTER YOUR GRAB" if state == 0 else ["", "DROPPING", "GRABBING", "LIFTING", "TO THE CHUTE", "PRIZE DROP"][state])
	sign.modulate = Color("fff18a") if lucky else Color.WHITE

func _claw_x() -> float:
	return trolley_x + cable * sin(theta)

func _update_claw() -> void:
	gantry.position = Vector3((trolley_x - 180) / 100, 4.44, 0.1)
	claw_root.position = Vector3((_claw_x() - 180) / 100, (444 - cable * cos(theta)) / 100, 0.1)
	cable_node.position = (gantry.position + claw_root.position) * 0.5
	cable_node.scale.y = cable / 100
	cable_node.rotation.z = theta
	left_prong.rotation.z = -openness * 0.55
	right_prong.rotation.z = openness * 0.55

func _grab() -> void:
	var centered: float = 0
	var best: Plush
	var claw_y: float = 26 + cable * cos(theta)
	for p in pile:
		var span: float = (30 if lucky else 16) + p.radius * 0.5
		var dx: float = absf(p.x - _claw_x())
		if dx > span or claw_y + 28 < p.y - p.radius - 4 or p.y < claw_y - 6:
			continue
		var value: float = 1 - dx / span
		if value > centered:
			centered = value
			best = p
	if best != null:
		var chance: float = 1 if lucky else clampf(0.85 * rng.randf_range(0.6, 1) * centered * centered / sqrt(best.radius / 24), 0, 1)
		slip_at = 99 if rng.randf() < chance else (0.12 if chance < 0.08 else rng.randf_range(0.2, 1.35))
		held = best
		held.held = true
		openness = 0.35
		status = "Hold on..."
	else:
		status = "Missed! Aim over a plushie"
	progress = 0
	_enter(3)

func _release(delivered: bool) -> void:
	if held == null:
		return
	held.held = false
	held.vx = 0
	held.vy = 40 if delivered else 0
	if delivered:
		held.x = 48
	held = null
	slip_at = 99

func pointer(action: String, pos: Vector2, id: int) -> void:
	if action == "up":
		move_pointers.erase(id)
		if drag_id == id:
			drag_id = -1
		return
	if not running or expired or state != 0:
		return
	if pos.y > 0.76:
		if action == "down" and pos.x > 0.64:
			cancel_input()
			trolley_v = 0
			_enter(1)
			beep(700, 0.08)
		elif pos.x < 0.64:
			move_pointers[id] = -1 if pos.x < 0.36 else 1
			target_x = -1
	else:
		move_pointers.erase(id)
		if action == "down":
			drag_id = id
		if drag_id == id:
			var pixel: Vector2 = pos * get_viewport().get_visible_rect().size
			var origin: Vector3 = camera.project_ray_origin(pixel)
			var ray: Vector3 = camera.project_ray_normal(pixel)
			var world: Vector3 = origin + ray * (-origin.z / ray.z)
			target_x = clampf(world.x * 100 + 180, 46, 322)

func cancel_input() -> void:
	move_pointers.clear()
	drag_id = -1
	target_x = -1

func time_up() -> void:
	cancel_input()

func is_settled() -> bool:
	if state != 0 or held != null:
		return false
	for p in pile:
		if p.x < 88 and p.y <= 406:
			return false
	return true

func tickets_for_score() -> int:
	return 2 + int(score / 10)
