extends ArcadeGame

const RADII: Array[float] = [13, 30, 48, 68, 90, 118]
const POINTS: Array[int] = [100, 50, 40, 30, 20, 10]
const COLORS: Array[Color] = [Color("ff596b"), Color("ffd55c"), Color("ff9658"), Color("ff79bd"), Color("b589ff"), Color("67d6ff")]
var balls: Array[Dictionary] = []
var ready_ball: MeshInstance3D
var ready_x: float = 180.0
var reload: float = 0.0
var dragging: int = -1
var samples: Array[Vector3] = []
var power_label: Label3D
var shots: int = 0
var status_time: float = 0.0

func _init() -> void:
	game_id = "skeeball"
	title = "SKEE-BALL"
	instructions = "Drag the ball left or right to aim, then flick UP to roll. Flick speed controls jump distance. The center scores 100; the small corner hole scores 200!"
	duration = 40.0

func build_game() -> void:
	camera.position = Vector3(0, 5.6, 11.0)
	camera.look_at(Vector3(0, 0.6, 3.2))
	camera.fov = 47
	box(Vector3(0, -0.25, 4.7), Vector3(2.8, 0.45, 6.1), Color("173457"))
	box(Vector3(0, -0.04, 5.13), Vector3(2.4, 0.08, 3.9), Color("bd8a54"))
	for x in [-1.26, 1.26]:
		box(Vector3(x, 0.16, 4.65), Vector3(0.13, 0.36, 5.1), Color("287fb8"))
		box(Vector3(x, 0.36, 4.65), Vector3(0.07, 0.045, 5.1), Color("73e4ff"), 0.6)
	for z in [3.8, 4.4, 5.0, 5.6, 6.2]:
		box(Vector3(0, 0.007, z), Vector3(0.018, 0.009, 0.2), Color("ebc997"))
	var ramp = box(Vector3(0, 0.01, 3.03), Vector3(2.38, 0.10, 0.35), Color("ddad67"))
	ramp.rotation.x = 0.28
	var board = box(Vector3(0, 1.07, 1.36), Vector3(3.45, 0.13, 3.04), Color("1b3155"))
	board.rotation.x = atan(0.802)
	for i in range(5, -1, -1):
		_ring(Vector3(0, _surface(146) * 0.01 + 0.07, 1.46), RADII[i] * 0.01, COLORS[i])
		var z: float = 146.0 + (0.0 if i == 0 else (RADII[i - 1] + RADII[i]) * 0.39)
		label3d(str(POINTS[i]), Vector3(0, _surface(z) * 0.01 + 0.12, z * 0.01), 24 if i == 0 else 22, COLORS[i])
	_ring(Vector3(1.38, _surface(60) * 0.01 + 0.07, 0.6), 0.16, Color("ffdf70"))
	label3d("200", Vector3(1.38, _surface(60) * 0.01 + 0.43, 0.6), 24, Color("fff1aa"))
	box(Vector3(0, 2.30, 0.04), Vector3(3.6, 0.48, 0.18), Color("17598c"))
	label3d("SKEE-BALL", Vector3(0, 2.35, 0.16), 43, Color("f8ecb3"))
	ready_ball = sphere(Vector3(0, 0.15, 5.86), 0.14, Color("f4e6cf"))
	label3d("FLICK UP", Vector3(0, 0.07, 6.3), 29, Color("fff0bb"))
	power_label = label3d("", Vector3(0, 0.64, 4.9), 26, Color("8bebff"))
	status = "Aim • Flick upward to roll"

func _ring(pos: Vector3, radius: float, color: Color) -> void:
	var mesh = TorusMesh.new()
	mesh.inner_radius = maxf(0.03, radius - 0.028)
	mesh.outer_radius = radius + 0.028
	mesh.rings = 28
	mesh.ring_segments = 6
	var node = MeshInstance3D.new()
	node.mesh = mesh
	var mat = StandardMaterial3D.new()
	mat.albedo_color = color
	mat.roughness = 0.35
	node.material_override = mat
	node.position = pos
	node.rotation.x = atan(0.802)
	add_child(node)

func _surface(y: float) -> float:
	if y >= 318:
		return 0
	if y >= 290:
		return 10 * pow((318 - y) / 28, 2)
	if y >= 252:
		return 10 + 6 * (290 - y) / 38
	return 16 + (252 - y) * 0.802

func _sample(pos: Vector2) -> void:
	var now: float = Time.get_ticks_msec() * 0.001
	samples.append(Vector3(pos.x * 360, pos.y * 640, now))
	while samples.size() > 2 and now - samples[0].z > 0.12:
		samples.remove_at(0)

func pointer(action: String, pos: Vector2, id: int) -> void:
	if not running or expired:
		return
	if action == "down" and dragging < 0 and reload <= 0 and pos.y > 0.5:
		dragging = id
		samples.clear()
		_sample(pos)
	elif action == "move" and dragging == id:
		_sample(pos)
		ready_x = clampf(aim(pos).x * 100 + 180, 74, 286)
	elif action == "up" and dragging == id:
		_sample(pos)
		dragging = -1
		if samples.size() < 2:
			return
		var first: Vector3 = samples[0]
		var last: Vector3 = samples[-1]
		var elapsed: float = maxf(0.025, last.z - first.z)
		var velocity = Vector2(last.x - first.x, last.y - first.y) / elapsed
		if -velocity.y < 320:
			status = "Flick upward faster to roll"
			return
		var speed: float = clampf(velocity.length() * 0.33, 120, 760)
		var angle: float = clampf(atan2(velocity.x, -velocity.y), deg_to_rad(-32), deg_to_rad(32))
		_launch(ready_x, speed, angle)

func _launch(x: float, speed: float, angle: float) -> void:
	if balls.size() >= 6:
		return
	var node = sphere(Vector3((x - 180) / 100, 0.14, 5.86), 0.14, Color("f7e8d1"))
	balls.append({"node": node, "x": x, "y": 586.0, "z": 0.0, "vx": sin(angle) * speed, "vy": -cos(angle) * speed, "vz": 0.0, "phase": 0, "t": 0.0, "age": 0.0, "points": 0})
	reload = 0.35
	shots += 1
	power_label.text = "POWER %d%%" % roundi(speed / 760.0 * 100)
	status = "Roll %d • Power %d%%" % [shots, roundi(speed / 760.0 * 100)]
	status_time = 1.5
	beep(250 + speed * 0.4, 0.07)

func step(dt: float) -> void:
	reload = maxf(0, reload - dt)
	status_time -= dt
	if status_time <= 0:
		power_label.text = ""
	ready_ball.visible = reload <= 0 and not expired
	if dragging < 0:
		ready_x = lerpf(ready_x, 180, minf(1, dt * 8))
	ready_ball.position.x = (ready_x - 180) / 100
	for i in range(balls.size() - 1, -1, -1):
		var b: Dictionary = balls[i]
		b.t += dt
		b.age += dt
		if b.age > 8:
			b.phase = 3
		match int(b.phase):
			0:
				var velocity = Vector2(b.vx, b.vy)
				var speed: float = velocity.length()
				if speed > 0:
					velocity *= maxf(0, speed - 60 * dt) / speed
				b.vx = velocity.x
				b.vy = velocity.y
				b.x += b.vx * dt
				b.y += b.vy * dt
				if b.x < 74:
					b.x = 74.0
					b.vx = absf(b.vx) * 0.6
				if b.x > 286:
					b.x = 286.0
					b.vx = -absf(b.vx) * 0.6
				if b.y <= 318:
					if b.vy < 0 and speed > 60:
						b.phase = 1
						b.vz = speed * 0.5
					else:
						b.vy = absf(b.vy) + 40
				elif b.vy >= 0 and velocity.length() < 200:
					b.vy += 300 * dt
				if b.y > 654:
					b.phase = 3
			1:
				b.x += b.vx * dt
				b.y += b.vy * dt
				b.z += b.vz * dt
				b.vz -= 1000 * dt
				if b.x < 34 or b.x > 326:
					b.x = clampf(b.x, 34, 326)
					b.vx *= -0.5
				if b.y < 44:
					b.y = 44.0
					b.vy = absf(b.vy) * 0.3
				if b.z <= 0:
					_land(b)
			2:
				var t: float = clampf(b.t / 0.45, 0, 1)
				var ease: float = 1 - pow(1 - t, 3)
				b.x = lerpf(b.from_x, b.to_x, ease)
				b.y = lerpf(b.from_y, b.to_y, ease)
				b.z = absf(sin(t * TAU)) * 14 * (1 - t)
				if b.t >= 0.5:
					award(b.points)
					status = ("CORNER BONUS! +200" if b.points == 200 else ("BULLSEYE! +100" if b.points == 100 else "+%d points" % b.points))
					beep(500 + b.points * 4, 0.12)
					b.phase = 4
		if b.phase >= 3:
			if b.phase == 3:
				status = "Gutter! Try a stronger flick"
			b.node.queue_free()
			balls.remove_at(i)
		else:
			var node: Node3D = b.node
			node.position = Vector3((b.x - 180) / 100, (_surface(b.y) + 14 + maxf(0, b.z)) / 100, b.y / 100)
			node.rotation.x += dt * 8

func _land(b: Dictionary) -> void:
	b.z = 0.0
	b.phase = 2
	b.t = 0.0
	b.from_x = b.x
	b.from_y = b.y
	if Vector2(b.x - 318, (b.y - 60) / 0.78).length() < 19:
		b.points = 200
		b.to_x = 318.0
		b.to_y = 60.0
		return
	var d = Vector2(b.x - 180, (b.y - 146) / 0.78)
	var ring: int = 5
	for i in range(RADII.size()):
		if d.length() < RADII[i] and b.y <= 252:
			ring = i
			break
	b.points = POINTS[ring]
	var r: float = 0 if ring == 0 else (RADII[ring - 1] + RADII[ring]) / 2
	b.to_x = 180 + cos(d.angle()) * r
	b.to_y = 146 + sin(d.angle()) * r * 0.78

func cancel_input() -> void:
	dragging = -1
	samples.clear()

func time_up() -> void:
	cancel_input()

func is_settled() -> bool:
	return balls.is_empty()

func tickets_for_score() -> int:
	return 1 + int(score / 50)
