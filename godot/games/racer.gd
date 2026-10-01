extends ArcadeGame

const LAPS: int = 3
const ROAD_HALF: float = 1.5
const PLACE_POINTS: Array[int] = [500, 380, 290, 220, 160, 110, 70, 40]
var course: PackedVector3Array = []
var distances: PackedFloat32Array = []
var lap_length: float = 0.0
var cars: Array[Dictionary] = []
var player_distance: float = -4.8
var player_x: float = -0.52
var player_speed: float = 0.0
var steer_target: float = -0.52
var steer_id: int = -1
var drift_id: int = -1
var last_touch_x: float = 0.5
var drifting: bool = false
var charge: float = 0.0
var boost: float = 0.0
var keyboard_drift: bool = false
var point_carry: float = 0.0
var last_contact: float = -10.0
var final_place: int = 0
var rank: int = 7
var finish_count: int = 0
var player_car: Node3D
var exhaust: MeshInstance3D
var last_lap: int = 1
var sound_timer: float = 0.0

func _init() -> void:
	game_id = "racer"
	title = "TURBO RACER"
	instructions = "Drag left and right to steer; acceleration is automatic. Race three laps against seven rivals. Hold a second finger to drift through bends, then release for turbo! Keyboard: arrows / A D, Space to drift."
	duration = 72.0

func build_game() -> void:
	camera.fov = 65.0
	camera.far = 400.0
	build_course()
	box(Vector3(0, -1.3, 0), Vector3(190, 0.4, 190), Color("17182f"))
	for i in range(-9, 10):
		box(Vector3(i * 10, -1.085, 0), Vector3(0.05, 0.015, 180), Color("482656"), 0.2)
		box(Vector3(0, -1.085, i * 10), Vector3(180, 0.015, 0.05), Color("482656"), 0.2)
	for i in range(55):
		var angle: float = TAU * i / 55.0
		var radius: float = 56 + rng.randf_range(0, 22)
		var height: float = rng.randf_range(4, 22)
		var pos = Vector3(sin(angle) * radius, height * 0.5 - 1.0, cos(angle) * radius)
		box(pos, Vector3(rng.randf_range(2, 5), height, rng.randf_range(2, 5)), Color("222540"))
		box(pos + Vector3(0, height * 0.5, 0), Vector3(2, 0.06, 2), Color("b85acf"), 0.8)
	var start: Dictionary = sample_course(0)
	var gate = Node3D.new()
	add_child(gate)
	gate.position = start["position"]
	gate.look_at(gate.position + (start["forward"] as Vector3))
	for side in [-1, 1]:
		box(Vector3(side * 2.15, 1.7, 0), Vector3(0.18, 3.4, 0.18), Color("38d6e5"), 0.5, gate)
	box(Vector3(0, 3.35, 0), Vector3(4.6, 0.55, 0.2), Color("301f4d"), 0, gate)
	label3d("TURBO CIRCUIT", Vector3(0, 3.38, 0.15), 32, Color("fff1ab"), gate)
	for row in range(2):
		for col in range(12):
			box(Vector3(-1.375 + col * 0.25, 0.02, row * 0.25), Vector3(0.25, 0.01, 0.25), Color.WHITE if (row + col) % 2 == 0 else Color("1d1c35"), 0, gate)
	player_car = car_model(Color("f4496b"))
	exhaust = box(Vector3(0, 0.14, 0.57), Vector3(0.30, 0.08, 0.30), Color("42e4fa"), 2.0, player_car)
	exhaust.visible = false
	var colors: Array[Color] = [Color("58bbff"), Color("b5f85c"), Color("ffdd68"), Color("9a66ef"), Color("ff914b"), Color("f0f0ff"), Color("48dbaf")]
	var rival: int = 0
	for slot in range(8):
		if slot == 6:
			continue
		var distance: float = -(0.3 + (slot / 2) * 1.5 + (slot % 2) * 0.25)
		cars.append({"node": car_model(colors[rival]), "distance": distance, "x": -0.52 if slot % 2 == 0 else 0.52, "speed": 0.0, "skill": 1.0 - rival * 0.016, "bias": rng.randf_range(-0.45, 0.45), "finished": 0, "was_ahead": distance > player_distance, "ahead_time": 0.0})
		rival += 1
	update_visuals(1.0)
	update_status()

func build_course() -> void:
	for i in range(241):
		var theta: float = TAU * i / 240.0
		# A closed circuit with a tighter inner bend and rolling hills.
		var point = Vector3(28 * sin(theta) + 5 * sin(theta * 2), 0.8 * sin(theta * 3), 36 * cos(theta))
		if i > 0:
			lap_length += point.distance_to(course[i - 1])
		course.append(point)
		distances.append(lap_length)
	for i in range(240):
		var a: Vector3 = course[i]
		var b: Vector3 = course[i + 1]
		var center: Vector3 = (a + b) * 0.5
		var forward: Vector3 = (b - a).normalized()
		var right: Vector3 = forward.cross(Vector3.UP).normalized()
		var segment: MeshInstance3D = box(center - Vector3(0, 0.055, 0), Vector3(3.08, 0.11, a.distance_to(b) + 0.04), Color("35384d"))
		segment.look_at(segment.position + forward)
		for side in [-1, 1]:
			var edge: MeshInstance3D = box(center + right * side * 1.58, Vector3(0.14, 0.08, a.distance_to(b) + 0.04), Color("fb4e91") if (i / 4) % 2 == 0 else Color("87d7ec"), 0.22)
			edge.look_at(edge.position + forward)
		if i % 3 == 0:
			for lane in [-1, 1]:
				var mark: MeshInstance3D = box(center + right * lane * 0.5 + Vector3(0, 0.012, 0), Vector3(0.025, 0.012, 0.55), Color("b1a1c9"))
				mark.look_at(mark.position + forward)
		if i % 12 == 0:
			for side in [-1, 1]:
				box(center + right * side * 2.3 + Vector3(0, 1.25, 0), Vector3(0.04, 2.5, 0.04), Color("34d9ed"), 0.7)

func sample_course(distance: float) -> Dictionary:
	var wrapped: float = fposmod(distance, lap_length)
	var low: int = 0
	var high: int = distances.size() - 1
	while low + 1 < high:
		var middle: int = (low + high) / 2
		if distances[middle] <= wrapped:
			low = middle
		else:
			high = middle
	var weight: float = (wrapped - distances[low]) / (distances[low + 1] - distances[low])
	var forward: Vector3 = (course[low + 1] - course[low]).normalized()
	var next_forward: Vector3 = (course[(low + 2) % 240] - course[(low + 1) % 240]).normalized()
	return {"position": course[low].lerp(course[low + 1], weight), "forward": forward, "right": forward.cross(Vector3.UP).normalized(), "bend": forward.cross(next_forward).y * 30.0 / maxf(0.01, distances[low + 1] - distances[low])}

func car_model(color: Color) -> Node3D:
	var node = Node3D.new()
	add_child(node)
	box(Vector3(0, 0.16, 0), Vector3(0.44, 0.19, 0.82), color, 0, node)
	box(Vector3(0, 0.29, 0.06), Vector3(0.34, 0.16, 0.39), color.lightened(0.12), 0, node)
	box(Vector3(0, 0.31, -0.145), Vector3(0.30, 0.10, 0.016), Color("182c49"), 0, node)
	box(Vector3(0, 0.31, 0.265), Vector3(0.30, 0.10, 0.016), Color("182c49"), 0, node)
	box(Vector3(0, 0.27, 0.37), Vector3(0.50, 0.045, 0.11), color, 0, node)
	for side in [-1, 1]:
		box(Vector3(side * 0.145, 0.18, -0.418), Vector3(0.095, 0.06, 0.015), Color("fff1c4"), 0.8, node)
		box(Vector3(side * 0.145, 0.18, 0.418), Vector3(0.095, 0.05, 0.015), Color("ff3056"), 0.8, node)
		for z in [-0.27, 0.26]:
			var wheel: MeshInstance3D = cylinder(Vector3(side * 0.235, 0.10, z), 0.10, 0.075, Color("111324"), node)
			wheel.rotation.z = PI * 0.5
	return node

func pointer(action: String, pos: Vector2, id: int) -> void:
	if not running or finished or expired:
		return
	if action == "down":
		if steer_id < 0:
			steer_id = id
			last_touch_x = pos.x
		elif drift_id < 0 and id != steer_id:
			drift_id = id
			drifting = true
			charge = 0.0
	elif action == "move" and id == steer_id:
		steer_target = clampf(steer_target + (pos.x - last_touch_x) * 5.76, -2.6, 2.6)
		last_touch_x = pos.x
	elif action == "up":
		if id == steer_id:
			steer_id = -1
		if id == drift_id:
			drift_id = -1
			release_drift()

func release_drift() -> void:
	if not drifting:
		return
	drifting = false
	if charge >= 1.0:
		boost = maxf(boost, 1.5 if charge >= 2.2 else 0.8)
		beep(920, 0.12)
	charge = 0.0

func cancel_input() -> void:
	steer_id = -1
	drift_id = -1
	drifting = false
	keyboard_drift = false
	charge = 0.0

func time_up() -> void:
	cancel_input()
	if final_place == 0:
		final_place = rank
		award(int(PLACE_POINTS[rank - 1] * 0.4))
		status = "Time up · %d / 8 · %d laps completed" % [rank, maxi(0, int(player_distance / lap_length))]

func step(dt: float) -> void:
	if expired:
		return
	var elapsed: float = duration - time_left
	var steer: float = float(Input.is_key_pressed(KEY_RIGHT) or Input.is_key_pressed(KEY_D)) - float(Input.is_key_pressed(KEY_LEFT) or Input.is_key_pressed(KEY_A))
	steer_target = clampf(steer_target + steer * 2.8 * dt, -2.6, 2.6)
	var space: bool = Input.is_key_pressed(KEY_SPACE)
	if space and not keyboard_drift:
		drifting = true
		charge = 0.0
	elif not space and keyboard_drift and drift_id < 0:
		release_drift()
	keyboard_drift = space
	boost = maxf(0, boost - dt)
	var bend: float = sample_course(player_distance)["bend"]
	var offroad: bool = absf(player_x) > ROAD_HALF + 0.04
	var top_speed: float = 14.5 if boost > 0.0 else 11.0
	top_speed *= 1.0 - minf(absf(bend), 2.5) * 0.08 * (0.25 if drifting else 1.0)
	if drifting and absf(bend) < 0.3:
		top_speed *= 0.85
	if offroad:
		top_speed = minf(top_speed, 4.5)
	player_speed = move_toward(player_speed, top_speed, (19.2 if boost > 0 else 5.2) * dt)
	player_x = move_toward(player_x, steer_target, 6.2 * dt)
	var push: float = bend * player_speed / 11.0 * 1.1 * dt * (0.35 if drifting else 1.0)
	player_x = clampf(player_x + push, -2.6, 2.6)
	steer_target = clampf(steer_target + push, -2.6, 2.6)
	if drifting and player_speed > 3.0 and not offroad:
		charge = minf(2.6, charge + absf(bend) * player_speed / 11.0 * dt)
	var before: float = player_distance
	player_distance += player_speed * dt * (1.0 - player_x * bend * 0.02)
	if not offroad and player_distance > 0:
		point_carry += (player_distance - maxf(before, 0)) / 3.5
		if point_carry >= 1.0:
			var points: int = int(point_carry)
			score += points
			point_carry -= points
	for i in range(cars.size()):
		step_rival(cars[i], dt, elapsed)
	rank = 1
	for car in cars:
		if car["distance"] > player_distance or car["finished"] > 0:
			rank += 1
		var ahead: bool = car["distance"] > player_distance
		if ahead:
			car["ahead_time"] += dt
		elif car["was_ahead"] and car["ahead_time"] > 0.5 and elapsed - last_contact > 1.2:
			award(25)
			car["ahead_time"] = 0.0
		car["was_ahead"] = ahead
	if player_distance >= lap_length * LAPS:
		final_place = mini(8, finish_count + 1)
		award(PLACE_POINTS[final_place - 1] + int(time_left) * 12)
		status = "FINISH!  %d / 8  ·  Three laps complete" % final_place
		update_visuals(dt)
		finish()
		return
	var lap: int = clampi(int(maxf(0, player_distance) / lap_length) + 1, 1, LAPS)
	if lap > last_lap:
		beep(1150, 0.12)
		last_lap = lap
	update_visuals(dt)
	update_status()
	sound_timer -= dt
	if sound_timer <= 0.0:
		beep(70 + player_speed * 8, 0.025)
		sound_timer = 0.18

func step_rival(car: Dictionary, dt: float, elapsed: float) -> void:
	var distance: float = car["distance"]
	var bend: float = sample_course(distance + 2.0)["bend"]
	var target_x: float = clampf(-bend * 0.45 + float(car["bias"]), -1.15, 1.15)
	for other in cars:
		var gap: float = float(other["distance"]) - distance
		if gap > 0.1 and gap < 2.5 and absf(float(other["x"]) - float(car["x"])) < 0.55:
			target_x = clampf(float(other["x"]) + (0.65 if float(other["x"]) < 0.0 else -0.65), -1.15, 1.15)
	car["x"] = move_toward(float(car["x"]), target_x, 2.4 * dt)
	var speed_target: float = 11.0 * float(car["skill"]) * (1.0 - minf(absf(bend), 2.5) * 0.045)
	speed_target *= 1.0 - 0.05 * clampf((distance - player_distance) / 15.0, -1.0, 1.0)
	car["speed"] = move_toward(float(car["speed"]), speed_target, 4.8 * dt)
	car["distance"] += float(car["speed"]) * dt * (1.0 - float(car["x"]) * bend * 0.02)
	var gap: float = float(car["distance"]) - player_distance
	var dx: float = float(car["x"]) - player_x
	if absf(gap) < 0.8 and absf(dx) < 0.44:
		var side: float = 1.0 if dx >= 0 else -1.0
		var shove: float = minf((0.44 - absf(dx)) * 0.5, 3.2 * dt)
		player_x -= side * shove
		steer_target = clampf(steer_target - side * shove, -2.6, 2.6)
		car["x"] = clampf(float(car["x"]) + side * shove, -1.7, 1.7)
		if gap > 0.36:
			player_speed = minf(player_speed, float(car["speed"]) - 0.1)
		if elapsed - last_contact > 0.25:
			beep(140, 0.055)
		last_contact = elapsed
	if car["distance"] >= lap_length * LAPS and car["finished"] == 0:
		finish_count += 1
		car["finished"] = finish_count

func place_car(node: Node3D, distance: float, offset: float, drift_angle: float = 0) -> void:
	var point: Dictionary = sample_course(distance)
	node.position = (point["position"] as Vector3) + (point["right"] as Vector3) * offset + Vector3(0, 0.04, 0)
	node.look_at(node.position + (point["forward"] as Vector3))
	node.rotate_object_local(Vector3.UP, drift_angle)

func update_visuals(dt: float) -> void:
	var point: Dictionary = sample_course(player_distance)
	var bend: float = point["bend"]
	place_car(player_car, player_distance, player_x, -signf(bend) * 0.24 if drifting else 0.0)
	for car in cars:
		place_car(car["node"], car["distance"], car["x"])
	var forward: Vector3 = point["forward"]
	var target_pos: Vector3 = player_car.position - forward * 3.6 + Vector3(0, 2.35, 0)
	camera.position = camera.position.lerp(target_pos, minf(1.0, dt * 8.0))
	var ahead: Vector3 = sample_course(player_distance + 6.0)["position"]
	camera.look_at(ahead + Vector3(0, 0.3, 0))
	exhaust.visible = boost > 0.0
	if exhaust.visible:
		exhaust.scale.z = rng.randf_range(0.8, 1.8)

func update_status() -> void:
	var mode: String = "Drag to steer"
	if absf(player_x) > 1.54:
		mode = "OFF ROAD · Steer back!"
	elif boost > 0:
		mode = "TURBO!"
	elif drifting:
		mode = "DRIFT %d%% · Release for turbo" % mini(100, int(charge / 2.2 * 100))
	status = "LAP %d/3 · %d/8 · %d km/h\n%s" % [last_lap, rank, int(player_speed / 11.0 * 220), mode]

func tickets_for_score() -> int:
	return 1 + score / 40
