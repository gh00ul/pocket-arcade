extends ArcadeGame

const BALL_RADIUS: float = 0.12
const RIM_RADIUS: float = 0.27
const RIM_HEIGHT: float = 2.3
const HOOP_Z: float = -2.6
var balls: Array[Dictionary] = []
var ready_ball: Node3D
var hoop: Node3D
var hoop_x: float = 0.0
var ready_x: float = 0.0
var reload: float = 0.0
var drag_id: int = -1
var drag_start: Vector2
var flick_samples: Array[Dictionary] = []
var shots: int = 0
var makes: int = 0
var streak: int = 0
var notice: float = 0.0
var hoop_label: Label3D

func _init() -> void:
	game_id = "hoops"
	title = "HOOP SHOT"
	instructions = "Flick upward from the ball to shoot. Flick speed sets power. Consecutive baskets multiply points up to ×5. The hoop moves in the second half!"
	duration = 44.0

func build_game() -> void:
	camera.fov = 59.0
	camera.position = Vector3(0, 2.25, 3.15)
	camera.look_at(Vector3(0, 1.65, -1.8))
	box(Vector3(0, -0.15, -1.3), Vector3(2.25, 0.3, 4.0), Color("643942"))
	for i in range(10):
		box(Vector3(0, 0.008, -3.15 + i * 0.39), Vector3(2, 0.01, 0.012), Color("a77265"))
	box(Vector3(0, 1.75, -3.25), Vector3(3.0, 3.8, 0.1), Color("321b32"))
	for side in [-1, 1]:
		box(Vector3(side * 1.04, 0.28, -1.3), Vector3(0.10, 0.65, 4.0), Color("971f52"))
		for depth in [-3.1, -1.2, 0.4]:
			box(Vector3(side * 1.04, 1.7, depth), Vector3(0.045, 3.5, 0.045), Color("da7581"))
		for row in range(12):
			box(Vector3(side * 1.04, 0.5 + row * 0.25, -1.35), Vector3(0.012, 0.009, 3.5), Color("875b72"))
		for column in range(13):
			box(Vector3(side * 1.04, 1.85, -3.05 + column * 0.27), Vector3(0.012, 2.85, 0.009), Color("875b72"))
		box(Vector3(side * 1.04, 3.4, -1.35), Vector3(0.055, 0.04, 3.55), Color("ff9a4c"), 1.0)
	hoop = Node3D.new()
	add_child(hoop)
	box(Vector3(0, 2.65, -2.96), Vector3(1.32, 0.9, 0.09), Color("e0e4f0"), 0, hoop)
	box(Vector3(0, 2.53, -2.905), Vector3(0.5, 0.36, 0.014), Color("cc3656"), 0, hoop)
	box(Vector3(0, 2.54, -2.89), Vector3(0.43, 0.29, 0.012), Color("e0e4f0"), 0, hoop)
	box(Vector3(0, 1.5, -3.03), Vector3(0.08, 2.9, 0.08), Color("697b9d"), 0, hoop)
	box(Vector3(0, RIM_HEIGHT, -2.90), Vector3(0.15, 0.07, 0.22), Color("ef643e"), 0, hoop)
	make_ring(hoop, Vector3(0, RIM_HEIGHT, HOOP_Z), RIM_RADIUS, 0.022, Color("ff7348"))
	for level in range(4):
		make_ring(hoop, Vector3(0, RIM_HEIGHT - 0.09 - level * 0.09, HOOP_Z), RIM_RADIUS - level * 0.035, 0.006, Color("f4f4ec"))
	for i in range(12):
		var angle: float = TAU * i / 12.0
		var top = Vector3(cos(angle) * RIM_RADIUS, RIM_HEIGHT - 0.02, HOOP_Z + sin(angle) * RIM_RADIUS)
		var bottom = Vector3(cos(angle + 0.2) * 0.16, RIM_HEIGHT - 0.4, HOOP_Z + sin(angle + 0.2) * 0.16)
		var cord = box((top + bottom) * 0.5, Vector3(0.008, top.distance_to(bottom), 0.008), Color("f5ede6"), 0, hoop)
		cord.quaternion = Quaternion(Vector3.UP, (top - bottom).normalized())
	hoop_label = label3d("HOOP SHOT", Vector3(0, 3.3, -3.08), 36, Color("ffb779"))
	ready_ball = basketball()
	ready_ball.position = Vector3(0, 0.7, -0.2)
	update_status()

func make_ring(parent: Node3D, pos: Vector3, radius: float, thickness: float, color: Color) -> MeshInstance3D:
	var torus = TorusMesh.new()
	torus.inner_radius = radius - thickness
	torus.outer_radius = radius + thickness
	torus.rings = 24
	torus.ring_segments = 8
	return mesh_node(torus, pos, color, parent)

func basketball() -> Node3D:
	var node = Node3D.new()
	add_child(node)
	sphere(Vector3.ZERO, BALL_RADIUS, Color("ee8834"), node)
	for axis in range(3):
		var seam: MeshInstance3D = make_ring(node, Vector3.ZERO, BALL_RADIUS + 0.0005, 0.004, Color("432a28"))
		if axis == 1:
			seam.rotation.x = PI * 0.5
		elif axis == 2:
			seam.rotation.z = PI * 0.5
	return node

func pointer(action: String, pos: Vector2, id: int) -> void:
	if not running or finished or expired:
		return
	if action == "down" and drag_id < 0 and reload <= 0.0 and pos.y > 0.45:
		drag_id = id
		drag_start = pos
		flick_samples.clear()
		record_flick(pos)
	elif action == "move" and id == drag_id:
		record_flick(pos)
		ready_x = clampf((pos.x - 0.5) * 1.0, -0.45, 0.45)
	elif action == "up" and id == drag_id:
		record_flick(pos)
		drag_id = -1
		var flick: Vector2 = flick_velocity()
		if -flick.y > 0.5 and drag_start.y - pos.y > 0.04:
			shoot(flick)
		flick_samples.clear()

func record_flick(pos: Vector2, timestamp: int = -1) -> void:
	# Retain the original FlickTracker's 24-sample buffer and measure only
	# its final 90 ms, so holding to aim does not weaken a sharp release.
	flick_samples.append({"pos": pos, "time": Time.get_ticks_msec() if timestamp < 0 else timestamp})
	if flick_samples.size() > 24:
		flick_samples.remove_at(0)

func flick_velocity() -> Vector2:
	if flick_samples.size() < 2:
		return Vector2.ZERO
	var newest: int = flick_samples.size() - 1
	var oldest: int = newest
	var release_time: int = flick_samples[newest]["time"]
	for i in range(newest - 1, -1, -1):
		if release_time - int(flick_samples[i]["time"]) > 90:
			break
		oldest = i
	if oldest == newest:
		oldest = newest - 1
	var elapsed: float = maxi(8, release_time - int(flick_samples[oldest]["time"])) / 1000.0
	var release_pos: Vector2 = flick_samples[newest]["pos"]
	var earlier_pos: Vector2 = flick_samples[oldest]["pos"]
	return (release_pos - earlier_pos) / elapsed

func shoot(flick: Vector2) -> void:
	if balls.size() >= 6:
		return
	var theta: float = deg_to_rad(68)
	var ideal: float = sqrt(9.8 * 2.4 * 2.4 / (2.0 * pow(cos(theta), 2) * (tan(theta) * 2.4 - 1.6)))
	var power: float = clampf(ideal * (1.0 + (-flick.y * 640.0 - 1700.0) * 0.0001), 3.0, 11.0)
	var forward: float = power * cos(theta)
	var crossing_time: float = 2.4 / forward
	var lateral: float = (hoop_x - ready_x) / crossing_time * 0.72 + flick.x * 360.0 * 0.0012
	var node: Node3D = basketball()
	node.position = Vector3(ready_x, 0.7, -0.2)
	balls.append({"node": node, "velocity": Vector3(lateral, power * sin(theta), -forward), "time": 0.0, "scored": false, "resolved": false, "rim": false})
	reload = 0.30
	ready_ball.visible = false
	shots += 1
	beep(380, 0.055)
	update_status()

func cancel_input() -> void:
	drag_id = -1
	flick_samples.clear()

func time_up() -> void:
	cancel_input()
	ready_ball.visible = false
	status = "Buzzer! Last shots count…"

func is_settled() -> bool:
	for item in balls:
		if not item["resolved"]:
			return false
	return true

func step(dt: float) -> void:
	var elapsed: float = duration - time_left
	if elapsed > duration * 0.5:
		hoop_x = sin((elapsed - duration * 0.5) / 3.2 * TAU) * 0.45
	hoop.position.x = hoop_x
	reload = maxf(0.0, reload - dt)
	if reload <= 0.0 and not expired:
		ready_ball.visible = true
	if drag_id < 0:
		ready_x = lerpf(ready_x, 0.0, minf(1.0, dt * 3))
	ready_ball.position.x = ready_x
	for i in range(balls.size() - 1, -1, -1):
		var ball: Dictionary = balls[i]
		ball["time"] += dt
		for substep in range(4):
			simulate_ball(ball, dt / 4.0)
		var node: Node3D = ball["node"]
		node.rotate_x(dt * 6)
		if ball["time"] > 6.0 or node.position.z > 0.5:
			resolve(ball)
			node.queue_free()
			balls.remove_at(i)
	if notice > 0.0:
		notice -= dt
		if notice <= 0.0:
			update_status()

func simulate_ball(ball: Dictionary, dt: float) -> void:
	var node: Node3D = ball["node"]
	var position_before: Vector3 = node.position
	var velocity: Vector3 = ball["velocity"]
	velocity.y -= 9.8 * dt
	var pos: Vector3 = node.position + velocity * dt
	var horizontal = Vector2(pos.x - hoop_x, pos.z - HOOP_Z)
	if not ball["scored"] and not ball["resolved"] and position_before.y > RIM_HEIGHT and pos.y <= RIM_HEIGHT and velocity.y < 0.0:
		if horizontal.length() < RIM_RADIUS - BALL_RADIUS * 0.4:
			ball["scored"] = true
			ball["resolved"] = true
			streak += 1
			makes += 1
			var points: int = (10 if ball["rim"] else 13) * mini(streak, 5)
			award(points)
			status = "%s!  +%d  ·  ×%d" % ["BASKET" if ball["rim"] else "SWISH", points, mini(streak, 5)]
			notice = 1.3
	if not ball["scored"]:
		var radius: float = horizontal.length()
		if radius > 0.0001:
			var ring_point = Vector3(hoop_x + horizontal.x / radius * RIM_RADIUS, RIM_HEIGHT, HOOP_Z + horizontal.y / radius * RIM_RADIUS)
			var offset: Vector3 = pos - ring_point
			var distance: float = offset.length()
			if distance < BALL_RADIUS + 0.02 and distance > 0.0001:
				var normal: Vector3 = offset / distance
				pos = ring_point + normal * (BALL_RADIUS + 0.02)
				if velocity.dot(normal) < 0.0:
					velocity -= normal * velocity.dot(normal) * 1.55
					ball["rim"] = true
					beep(220, 0.03)
	else:
		pos.x = lerpf(pos.x, hoop_x, dt * 10)
		pos.z = lerpf(pos.z, HOOP_Z, dt * 10)
		velocity.x *= 0.9
		velocity.z *= 0.9
	if pos.z - BALL_RADIUS < -2.915 and velocity.z < 0.0 and pos.y > 2.08 and pos.y < 3.20 and absf(pos.x - hoop_x) < 0.78:
		pos.z = -2.915 + BALL_RADIUS
		velocity.z = absf(velocity.z) * 0.55
		beep(170, 0.05)
	if pos.z < -3.08:
		pos.z = -3.08
		velocity.z = absf(velocity.z) * 0.4
	if absf(pos.x) > 0.9:
		pos.x = clampf(pos.x, -0.9, 0.9)
		velocity.x = -signf(pos.x) * absf(velocity.x) * 0.4
	if pos.y < BALL_RADIUS:
		pos.y = BALL_RADIUS
		velocity.y = absf(velocity.y) * 0.45
		velocity.z = lerpf(velocity.z, 2.6, dt * 4)
		resolve(ball)
	if ball["time"] > 3.5:
		resolve(ball)
	node.position = pos
	ball["velocity"] = velocity

func resolve(ball: Dictionary) -> void:
	if ball["resolved"]:
		return
	ball["resolved"] = true
	if not ball["scored"]:
		streak = 0
		update_status()

func update_status() -> void:
	status = "%d / %d baskets  ·  ×%d  ·  %s" % [makes, shots, maxi(1, mini(streak, 5)), "Moving hoop!" if time_left < duration * 0.5 else "Flick up to shoot"]

func tickets_for_score() -> int:
	return 2 + score / 20
