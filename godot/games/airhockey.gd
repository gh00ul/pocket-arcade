extends ArcadeGame

const PUCK_RADIUS: float = 0.13
const MALLET_RADIUS: float = 0.22
var puck: Vector2 = Vector2(0, 1.1)
var velocity: Vector2 = Vector2.ZERO
var player: Vector2 = Vector2(0, 2.1)
var cpu: Vector2 = Vector2(0, -2.1)
var target: Vector2 = Vector2(0, 2.1)
var cpu_target: Vector2 = Vector2(0, -2.1)
var puck_mesh: MeshInstance3D
var player_mesh: Node3D
var cpu_mesh: Node3D
var goals: int = 0
var cpu_goals: int = 0
var streak: int = 0
var dragging: int = -1
var serve_timer: float = 0.6
var think_timer: float = 0.0
var hit_cooldown: float = 0.0
var stuck: float = 0.0
var scoreboard: Label3D

func _init() -> void:
	game_id = "airhockey"
	title = "AIR HOCKEY"
	instructions = "Drag your cyan mallet around the near half of the table. Smash the puck into the far goal. Goals in a row earn more. First to seven wins!"
	duration = 60.0

func build_game() -> void:
	camera.fov = 47.0
	camera.position = Vector3(0, 7.5, 6.6)
	camera.look_at(Vector3(0, 0, 0))
	box(Vector3(0, -0.38, 0), Vector3(3.15, 0.65, 5.75), Color("162740"))
	box(Vector3(0, -0.025, 0), Vector3(2.8, 0.05, 5.4), Color("b9e8f4"))
	for side in [-1, 1]:
		box(Vector3(side * 1.48, 0.10, 0), Vector3(0.16, 0.24, 5.72), Color("244a69"))
		box(Vector3(side * 1.47, 0.225, 0), Vector3(0.025, 0.02, 5.5), Color("46e4ff"), 1.6)
		for end in [-1, 1]:
			box(Vector3(side * 1.03, 0.1, end * 2.78), Vector3(0.82, 0.24, 0.16), Color("244a69"))
		box(Vector3(0, -0.025, side * 2.87), Vector3(1.24, 0.12, 0.36), Color("060b1a"))
		box(Vector3(0, 0.045, side * 2.91), Vector3(1.18, 0.025, 0.03), Color("ff477b") if side == -1 else Color("4cecff"), 1.5)
	box(Vector3(0, 0.009, 0), Vector3(2.8, 0.014, 0.025), Color("fa5376"))
	for z in [-1.9, 1.9]:
		ring(Vector3(0, 0.015, z), 0.65, Color("5babc4"))
	ring(Vector3(0, 0.015, 0), 0.43, Color("fa5376"))
	var holes = MultiMeshInstance3D.new()
	var hole_mesh = CylinderMesh.new()
	hole_mesh.top_radius = 0.008
	hole_mesh.bottom_radius = 0.008
	hole_mesh.height = 0.005
	hole_mesh.radial_segments = 6
	holes.multimesh = MultiMesh.new()
	holes.multimesh.transform_format = MultiMesh.TRANSFORM_3D
	holes.multimesh.mesh = hole_mesh
	holes.multimesh.instance_count = 231
	holes.material_override = material(Color("79bbcc"))
	add_child(holes)
	var hole_index: int = 0
	for x in range(-5, 6):
		for z in range(-10, 11):
			holes.multimesh.set_instance_transform(hole_index, Transform3D(Basis.IDENTITY, Vector3(x * 0.24, 0.008, z * 0.24)))
			hole_index += 1
	player_mesh = mallet(Color("00cbea"))
	cpu_mesh = mallet(Color("ef3763"))
	puck_mesh = cylinder(Vector3.ZERO, PUCK_RADIUS, 0.065, Color("192a49"))
	scoreboard = label3d("YOU 0   —   0 CPU", Vector3(0, 0.55, -3.08), 38, Color("c6f7ff"))
	update_meshes()
	update_status()

func ring(pos: Vector3, radius: float, color: Color) -> void:
	var mesh = MeshInstance3D.new()
	var torus = TorusMesh.new()
	torus.inner_radius = radius - 0.012
	torus.outer_radius = radius + 0.012
	torus.rings = 32
	torus.ring_segments = 8
	mesh.mesh = torus
	var material = StandardMaterial3D.new()
	material.albedo_color = color
	mesh.material_override = material
	mesh.position = pos
	add_child(mesh)

func mallet(color: Color) -> Node3D:
	var node = Node3D.new()
	add_child(node)
	cylinder(Vector3(0, 0.07, 0), MALLET_RADIUS, 0.14, color, node)
	cylinder(Vector3(0, 0.18, 0), 0.10, 0.19, color.lightened(0.25), node)
	sphere(Vector3(0, 0.28, 0), 0.1, color.lightened(0.2), node)
	return node

func pointer(action: String, pos: Vector2, id: int) -> void:
	if not running or finished or expired:
		return
	if action == "down" and dragging == -1:
		dragging = id
	if id == dragging:
		if action == "up":
			dragging = -1
		else:
			var point: Vector3 = aim(pos, 0.12)
			target = Vector2(clampf(point.x, -1.18, 1.18), clampf(point.z, 0.22, 2.48))

func cancel_input() -> void:
	dragging = -1
	target = player

func time_up() -> void:
	cancel_input()

func step(dt: float) -> void:
	if expired:
		return
	hit_cooldown -= dt
	serve_timer = maxf(0.0, serve_timer - dt)
	var h: float = dt / 4.0
	for iteration in range(4):
		simulate(h)
		if finished:
			break
	update_meshes()

func simulate(dt: float) -> void:
	var before: Vector2 = player
	player = player.move_toward(target, 22 * dt)
	var player_velocity: Vector2 = (player - before) / dt
	think_timer -= dt
	if think_timer <= 0.0:
		think_timer = 0.16
		cpu_target = Vector2(puck.x * 0.7, -2.18)
		if puck.y < -0.1 and velocity.length() < 7.0:
			var direction: Vector2 = (Vector2(0, 2.7) - puck).normalized()
			var behind: float = -0.09 if cpu.y < puck.y - 0.08 else 0.42
			cpu_target = puck - direction * behind
		if serve_timer > 0.0 or stuck > 1.2:
			cpu_target = Vector2(0, -2.18)
		cpu_target.x = clampf(cpu_target.x, -1.18, 1.18)
		cpu_target.y = clampf(cpu_target.y, -2.48, -0.22)
	before = cpu
	var difficulty: float = clampf((duration - time_left) / duration * 0.6 + maxi(0, goals - cpu_goals) * 0.12, 0, 1)
	cpu = cpu.move_toward(cpu_target, lerpf(3.6, 6.8, difficulty) * dt)
	var cpu_velocity: Vector2 = (cpu - before) / dt
	if serve_timer > 0.0:
		return
	puck += velocity * dt
	velocity *= 1.0 - 0.45 * dt
	if absf(puck.x) > 1.27:
		puck.x = clampf(puck.x, -1.27, 1.27)
		velocity.x = -signf(puck.x) * absf(velocity.x) * 0.9
		clack()
	if absf(puck.y) > 2.57:
		if absf(puck.x) < 0.581:
			if absf(puck.y) > 2.83:
				goal(puck.y < 0.0)
				return
		else:
			puck.y = clampf(puck.y, -2.57, 2.57)
			velocity.y = -signf(puck.y) * absf(velocity.y) * 0.9
			clack()
	collide(player, player_velocity)
	collide(cpu, cpu_velocity)
	puck.x = clampf(puck.x, -1.27, 1.27)
	if absf(puck.x) >= 0.581:
		puck.y = clampf(puck.y, -2.57, 2.57)
	velocity = velocity.limit_length(13)
	stuck = stuck + dt if velocity.length() < 0.6 else 0.0
	if stuck > 2.3:
		velocity = Vector2(rng.randf_range(-1.0, 1.0), 1.4 if puck.y < 0.0 else -1.4)
		stuck = 0.0

func collide(mallet_pos: Vector2, mallet_velocity: Vector2) -> void:
	var offset: Vector2 = puck - mallet_pos
	var distance: float = offset.length()
	if distance >= PUCK_RADIUS + MALLET_RADIUS:
		return
	var normal: Vector2 = offset / distance if distance > 0.0001 else Vector2(0, -1)
	puck = mallet_pos + normal * (PUCK_RADIUS + MALLET_RADIUS)
	var relative: float = (velocity - mallet_velocity).dot(normal)
	if relative < 0.0:
		velocity -= normal * relative * 1.85
		clack()

func clack() -> void:
	if hit_cooldown <= 0.0:
		beep(460 + velocity.length() * 36, 0.035)
		hit_cooldown = 0.07

func goal(by_player: bool) -> void:
	if time_left <= 0.0 or finished:
		return
	if by_player:
		goals += 1
		streak += 1
		award(100 + (streak - 1) * 25)
		beep(1020, 0.18)
	else:
		cpu_goals += 1
		streak = 0
		beep(190, 0.18)
	update_status()
	if goals >= 7 or cpu_goals >= 7:
		if goals >= 7:
			award(300)
		status = "YOU WIN!  %d — %d" % [goals, cpu_goals] if goals >= 7 else "CPU wins  %d — %d" % [goals, cpu_goals]
		finish()
		return
	puck = Vector2(rng.randf_range(-0.3, 0.3), -1.1 if by_player else 1.1)
	velocity = Vector2.ZERO
	serve_timer = 1.0
	stuck = 0.0

func update_meshes() -> void:
	player_mesh.position = Vector3(player.x, 0, player.y)
	cpu_mesh.position = Vector3(cpu.x, 0, cpu.y)
	puck_mesh.position = Vector3(puck.x, 0.05, puck.y)

func update_status() -> void:
	status = "YOU %d  —  %d CPU  ·  First to 7" % [goals, cpu_goals]
	scoreboard.text = "YOU %d    —    %d CPU" % [goals, cpu_goals]

func tickets_for_score() -> int:
	return 2 + score / 50
