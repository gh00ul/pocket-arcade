extends ArcadeGame

const NAMES = ["PERCH", "BASS", "CATFISH", "GOLDEN CARP", "OLD BOOT"]
const WEIGHT_MIN = [0.4, 1.5, 4.0, 1.0, 1.0]
const WEIGHT_MAX = [1.0, 3.5, 8.0, 2.0, 1.0]
const VALUE = [18.0, 12.0, 9.0, 60.0, 3.0]
const PULL = [0.25, 0.4, 0.55, 0.5, 0.0]
const RUN_SPEED = [40.0, 60.0, 75.0, 90.0, 0.0]
const FISH_COLOR = [Color("a9cc63"), Color("69bda3"), Color("a9a1ca"), Color("ffd35b"), Color("9c7556")]
const DOCK = Vector3(0, 0, 4.1)
const REEL = Vector2(0.79, 0.77)
var fishes: Array[Dictionary] = []
var phase: String = "idle"
var phase_time: float = 0.0
var elapsed: float = 0.0
var power: float = 0.0
var cast_id: int = -1
var aim_x: float = 0.5
var lure: Node3D
var marker: MeshInstance3D
var fishing_line: MeshInstance3D
var reel_overlay: Control
var cast_target: Vector3
var flight_start: Vector3
var flight_length: float = 0.7
var suitor: int = -1
var nibble_count: int = 0
var bite_left: float = 0.0
var notice_wait: float = 0.0
var strike_turn: float = 0.0
var hooked: int = -1
var line_distance: float = 150.0
var line_angle: float = 0.0
var tension: float = 0.0
var pull_state: String = "rest"
var pull_left: float = 0.9
var stamina: float = 1.0
var fight_time: float = 0.0
var strain: float = 0.0
var slack: float = 0.0
var crank_id: int = -1
var crank_angle: float = 0.0
var crank_last_angle: float = 0.0
var crank_sample_time: float = 0.0
var crank_rate: float = 0.0
var pending_turn: float = 0.0
var message_left: float = 0.0
var catch_label: Label3D
var landing_start: Vector3

class ReelOverlay extends Control:
	var game: Node
	func _draw() -> void:
		if game == null:
			return
		var c: Vector2 = size * Vector2(0.79, 0.77)
		var radius: float = size.x * 0.115
		var active: bool = game.phase == "fight" or game.bite_left > 0.0
		var color: Color = Color("ffdf73") if active else Color("6ae6e0")
		draw_circle(c, radius + 9, Color("112535"))
		draw_arc(c, radius + 5, 0, TAU, 48, color, 3, true)
		draw_circle(c, radius * 0.67, Color("2c5060"))
		for i: int in 3:
			var a: float = game.crank_angle + i * TAU / 3.0
			draw_line(c, c + Vector2(cos(a), sin(a)) * radius * 0.68, Color("66869a"), 5, true)
		var handle: Vector2 = c + Vector2(cos(game.crank_angle), sin(game.crank_angle)) * radius * 0.83
		draw_line(c, handle, color, 7, true)
		draw_circle(handle, radius * 0.20, Color("fff4d6"))
		draw_circle(c, 5, Color.WHITE)
		var font: Font = ThemeDB.fallback_font
		var font_size: int = maxi(16, int(size.x * 0.032))
		draw_string(font, c + Vector2(-radius - 10, radius + 30), "CIRCLE TO REEL", HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, color)
		var bar: Rect2 = Rect2(size * Vector2(0.07, 0.61), Vector2(size.x * 0.037, size.y * 0.20))
		draw_rect(bar.grow(5), Color("132735"))
		var charging: bool = game.phase == "charge"
		if not charging:
			draw_rect(Rect2(bar.position, Vector2(bar.size.x, bar.size.y * 0.23)), Color("a84046"))
			draw_rect(Rect2(bar.position + Vector2(0, bar.size.y * 0.23), Vector2(bar.size.x, bar.size.y * 0.59)), Color("258d6b"))
			draw_rect(Rect2(bar.position + Vector2(0, bar.size.y * 0.82), Vector2(bar.size.x, bar.size.y * 0.18)), Color("276787"))
		var fraction: float = game.power if charging else clampf(game.tension / 1.1, 0, 1)
		var fill_color: Color = Color("ffe489") if charging else Color.WHITE
		draw_rect(Rect2(Vector2(bar.position.x + 5, bar.end.y - bar.size.y * fraction), Vector2(bar.size.x - 10, bar.size.y * fraction)), fill_color)
		draw_string(font, bar.position + Vector2(-8, -12), "POWER" if charging else "LINE", HORIZONTAL_ALIGNMENT_LEFT, -1, font_size, Color.WHITE)

func _init() -> void:
	game_id = "fishing"
	title = "GONE FISHING"
	duration = 55.0
	instructions = "Hold on the pond to charge; move left or right to aim, release to cast. When the bobber dives, circle the reel clockwise! Keep winding to land the fish. Ease off when it pulls; keep the line in the green."

func build_game() -> void:
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 17.5
	camera.position = Vector3(0, 14, 12)
	camera.look_at(Vector3(0, 0, -0.7))
	box(Vector3(0, -0.6, -1), Vector3(12, 1, 17), Color("30653f"))
	box(Vector3(0, -0.1, -1.35), Vector3(8, 0.25, 11.8), Color("166178"))
	for i: int in 20:
		var x: float = -4.3 if i % 2 == 0 else 4.3
		var z: float = -7.0 + (i / 2) * 1.3
		var rock: MeshInstance3D = sphere(Vector3(x + rng.randf_range(-0.2, 0.2), 0.1, z), 0.35, Color("769888"))
		rock.scale = Vector3(1.5, 0.6, 1)
	for i: int in 9:
		var p: Vector3 = Vector3(rng.randf_range(-5, 5), 0, rng.randf_range(-9, -7.5))
		cylinder(p + Vector3(0, 0.6, 0), 0.12, 1.2, Color("796146"))
		var crown: MeshInstance3D = sphere(p + Vector3(0, 1.5, 0), 0.9, Color("439b62"))
		crown.scale.y = 1.5
	for i: int in 7:
		box(Vector3(0, 0.08, 3.7 + i * 0.32), Vector3(2.7, 0.16, 0.28), Color("b99261"))
	for x: float in [-1.2, 1.2]:
		cylinder(Vector3(x, 0.35, 4.2), 0.12, 0.85, Color("775a44"))
	var rod: MeshInstance3D = box(Vector3(0.85, 1.0, 3.3), Vector3(0.06, 0.06, 2.6), Color("d4b476"))
	rod.rotation.x = 0.55
	lure = Node3D.new()
	add_child(lure)
	sphere(Vector3.ZERO, 0.115, Color("fff5d7"), lure)
	sphere(Vector3(0, 0.075, 0), 0.09, Color("ff644c"), lure)
	lure.position = DOCK + Vector3(0, 0.35, -0.4)
	marker = _ring(Color("ffdd78"), 0.23)
	marker.visible = false
	fishing_line = box(Vector3.ZERO, Vector3(0.014, 0.014, 1), Color("fff2c4"))
	for i: int in 11:
		_add_fish(i == 10)
	catch_label = label3d("", Vector3(0, 2, 1.5), 38, Color("fff2a9"))
	var layer := CanvasLayer.new()
	layer.layer = 4
	add_child(layer)
	reel_overlay = ReelOverlay.new()
	reel_overlay.game = self
	reel_overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE
	layer.add_child(reel_overlay)
	reel_overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	status = "Hold on the pond to cast"

func _ring(color: Color, radius: float) -> MeshInstance3D:
	var mesh := TorusMesh.new()
	mesh.inner_radius = radius * 0.8
	mesh.outer_radius = radius
	mesh.rings = 24
	mesh.ring_segments = 8
	return mesh_node(mesh, Vector3.ZERO, color)

func _add_fish(boot: bool) -> void:
	var sp: int = _species() if not boot else 4
	var node := Node3D.new()
	add_child(node)
	if boot:
		box(Vector3(0, 0.12, 0), Vector3(0.24, 0.23, 0.24), FISH_COLOR[4], 0, node)
		box(Vector3(0, 0, -0.12), Vector3(0.25, 0.12, 0.44), FISH_COLOR[4], 0, node)
	else:
		var body: MeshInstance3D = sphere(Vector3.ZERO, 0.18 + sp * 0.035, FISH_COLOR[sp], node)
		body.scale = Vector3(0.68, 0.48, 1.9)
		var tail: MeshInstance3D = box(Vector3(0, 0, 0.4 + sp * 0.03), Vector3(0.36, 0.04, 0.23), FISH_COLOR[sp], 0, node)
		tail.rotation.y = 0.4
		for x: float in [-0.10, 0.10]:
			sphere(Vector3(x, 0.065, -0.20), 0.027, Color("102b30"), node)
	node.position = Vector3(rng.randf_range(-3.4, 3.4), 0.06, rng.randf_range(-6.5, 2.0))
	if boot:
		node.position = Vector3(-2.65, 0.02, 1.3)
	fishes.append({"node": node, "species": sp, "weight": rng.randf_range(WEIGHT_MIN[sp], WEIGHT_MAX[sp]), "heading": rng.randf_range(-PI, PI), "turn": rng.randf_range(-0.5, 0.5), "spook": 0.0, "respawn": 0.0})

func _species() -> int:
	var roll: float = rng.randf() * 100.0
	return 0 if roll < 45 else (1 if roll < 78 else (2 if roll < 94 else 3))

func step(dt: float) -> void:
	elapsed += dt
	phase_time += dt
	message_left = maxf(0, message_left - dt)
	if elapsed - crank_sample_time > 0.07:
		crank_rate *= exp(-14.0 * dt)
	var turn: float = pending_turn
	pending_turn = 0.0
	for i: int in fishes.size():
		var f: Dictionary = fishes[i]
		f.spook = maxf(0, f.spook - dt)
		if f.respawn > 0:
			f.respawn -= dt
			if f.respawn <= 0:
				f.node.position = Vector3(rng.randf_range(-3.4, 3.4), 0.06, rng.randf_range(-6, 1))
				f.node.visible = true
			continue
		if i == hooked or f.species == 4:
			continue
		if i == suitor:
			f.node.position = f.node.position.move_toward(lure.position + Vector3(0, -0.1, 0), dt * 1.0)
		else:
			f.heading += f.turn * dt
			var velocity: Vector3 = Vector3(sin(f.heading), 0, -cos(f.heading)) * [0.72, 0.6, 0.4, 1.0][f.species]
			f.node.position += velocity * dt * (2.0 if f.spook > 0 else 1.0)
			if absf(f.node.position.x) > 3.55 or f.node.position.z < -6.7 or f.node.position.z > 2.6:
				f.heading += PI * 0.65
				f.node.position.x = clampf(f.node.position.x, -3.55, 3.55)
				f.node.position.z = clampf(f.node.position.z, -6.7, 2.6)
		f.node.rotation.y = -f.heading + sin(elapsed * 5 + i) * 0.15
	match phase:
		"charge":
			power = 1.0 - absf(fmod(phase_time / 0.7, 2.0) - 1.0)
			cast_target = _landing_point()
			marker.position = cast_target
			marker.visible = true
		"flight":
			var t: float = minf(phase_time / flight_length, 1.0)
			lure.position = flight_start.lerp(cast_target, t) + Vector3(0, 4.0 * t * (1.0 - t) * 2.6, 0)
			if t >= 1.0:
				_phase("wait")
				notice_wait = 0.2
				beep(300, 0.05)
		"wait":
			_wait_for_bite(dt, turn)
		"fight":
			_fight(dt, turn)
		"landing":
			var t: float = minf(phase_time / 0.9, 1.0)
			fishes[hooked].node.position = landing_start.lerp(Vector3(0, 2.1, 2.5), t) + Vector3(0, sin(t * PI), 0)
			if t >= 1.0:
				_score_catch()
		"recover":
			if phase_time > 0.5:
				_phase("idle")
	if phase == "idle" or phase == "charge":
		lure.position = DOCK + Vector3(0.6, 0.5, -0.5)
	if phase == "wait":
		lure.position.y = -0.07 if bite_left > 0 else 0.12 + sin(elapsed * 5.0) * 0.025
	marker.visible = phase == "charge"
	_update_line()
	reel_overlay.queue_redraw()
	if message_left <= 0:
		catch_label.text = ""
		match phase:
			"idle": status = "Hold on the pond to cast"
			"charge": status = "POWER %d%% — release to cast" % int(power * 100)
			"flight": status = "Watch the bobber..."
			"wait": status = "STRIKE! CIRCLE THE REEL!" if bite_left > 0 else ("NIBBLING... wait for the dive" if suitor >= 0 else "Wait for a bite • circle to retrieve")
			"fight": status = "PULLING! EASE OFF!" if pull_state == "run" or tension > 0.85 else ("WATCH IT..." if pull_state == "warn" else "REEL IT IN! Keep the line green")

func _landing_point() -> Vector3:
	var angle: float = (aim_x - 0.5) * 2.0 * deg_to_rad(38)
	var distance: float = (90.0 + 380.0 * power) * 0.02
	var point: Vector3 = DOCK + Vector3(sin(angle) * distance, 0.12, -cos(angle) * distance)
	point.x = clampf(point.x, -3.5, 3.5)
	point.z = clampf(point.z, -6.6, 2.5)
	return point

func _wait_for_bite(dt: float, turn: float) -> void:
	if suitor < 0:
		notice_wait -= dt
		if notice_wait <= 0:
			notice_wait = 0.45
			var nearest: float = 2.4
			for i: int in fishes.size():
				var f: Dictionary = fishes[i]
				var d: float = f.node.position.distance_to(lure.position)
				var reach: float = 0.8 if f.species == 4 else nearest
				if f.spook <= 0 and f.respawn <= 0 and d < reach:
					nearest = d
					suitor = i
			if suitor >= 0:
				nibble_count = rng.randi_range(1, 3)
				notice_wait = 0.8 + nearest * 0.45
				strike_turn = 0
		if turn != 0:
			lure.position = lure.position.move_toward(DOCK, maxf(0, turn) * 0.18)
			if lure.position.distance_to(DOCK) < 1.0:
				_phase("idle")
		return
	strike_turn += maxf(0, turn)
	if strike_turn >= 0.6:
		if bite_left > 0:
			_hook(suitor)
		else:
			_lose("TOO SOON! Wait for the bobber to dive")
		return
	if bite_left > 0:
		bite_left -= dt
		if bite_left <= 0:
			_lose("MISSED THE BITE — cast again")
		return
	notice_wait -= dt
	if notice_wait <= 0:
		nibble_count -= 1
		notice_wait = rng.randf_range(0.45, 0.85)
		lure.position.y = -0.03
		beep(490, 0.035)
		if nibble_count <= 0:
			bite_left = 1.4 if fishes[suitor].species == 4 else 1.0
			strike_turn = 0
			beep(900, 0.10)

func _hook(index: int) -> void:
	hooked = index
	suitor = -1
	bite_left = 0
	var point: Vector3 = fishes[hooked].node.position - DOCK
	line_distance = maxf(51, Vector2(point.x, point.z).length() / 0.02)
	line_angle = atan2(point.x, -point.z)
	tension = 0.45
	pull_state = "rest"
	pull_left = 0.9
	stamina = 1
	fight_time = 0
	strain = 0
	slack = 0
	_phase("fight")
	_message("HOOKED! %s" % NAMES[fishes[hooked].species], 0.8)
	beep(780, 0.08)

func _fight(dt: float, turn: float) -> void:
	fight_time += dt
	var f: Dictionary = fishes[hooked]
	var sp: int = f.species
	var c: float = maxf(0, crank_rate / 10.0)
	var c_out: float = maxf(0, -crank_rate / 10.0)
	if turn > 0:
		line_distance -= turn * 12.0 / (1.0 + f.weight * 0.08)
	else:
		line_distance -= turn * 8.0
	var target: float = 0.3 + 0.45 * c * 1.3
	if sp != 4:
		var rest_target: float = 0.14 + 0.45 * c * (1 + f.weight * 0.05)
		pull_left -= dt
		if pull_state == "rest":
			line_distance += 8.0 * PULL[sp] * dt
			target = rest_target
			if pull_left <= 0:
				pull_state = "warn"
				pull_left = 0.5
		elif pull_state == "warn":
			target = rest_target + 0.08
			if pull_left <= 0:
				pull_state = "run"
				pull_left = rng.randf_range(0.55, 1.1) * (0.7 + PULL[sp])
				tension += 0.25 * PULL[sp]
		else:
			line_distance += RUN_SPEED[sp] * stamina * dt
			target = 0.25 + PULL[sp] * 0.8 * stamina + 0.45 * c * (1.6 + PULL[sp])
			if pull_left <= 0:
				pull_state = "rest"
				pull_left = rng.randf_range(0.9, 1.8)
				stamina *= 0.82
	target = maxf(0, target - 0.5 * c_out)
	line_distance = minf(line_distance, 480)
	tension = lerpf(tension, target, 1.0 - exp(-6.0 * dt))
	f.node.position = DOCK + Vector3(sin(line_angle), 0, -cos(line_angle)) * line_distance * 0.02 + Vector3(0, 0.08, 0)
	f.node.rotation.y = -line_angle + sin(fight_time * 7) * 0.4
	lure.position = f.node.position + Vector3(0, 0.10, 0.12)
	strain = strain + dt if tension > 1.0 else maxf(0, strain - dt)
	slack = slack + dt if tension < 0.2 and sp != 4 else maxf(0, slack - dt * 2)
	if strain > 0.35:
		_lose("LINE SNAPPED! Ease off during a run")
	elif slack > 1.2:
		_lose("IT GOT AWAY! Keep some tension")
	elif line_distance <= 50:
		landing_start = f.node.position
		_phase("landing")
	elif fight_time > 40:
		_lose("THE FISH GOT AWAY")

func _score_catch() -> void:
	var f: Dictionary = fishes[hooked]
	var points: int = maxi(1, int(round(f.weight * VALUE[f.species])))
	award(points)
	var description: String = "%s · %.1f LB · +%d" % [NAMES[f.species], f.weight, points]
	catch_label.text = description
	_message(description, 2.1)
	f.node.visible = false
	f.respawn = 4.5 if f.species == 4 else 1.5
	hooked = -1
	tension = 0
	_phase("idle")
	beep(1040, 0.15)

func _lose(text: String) -> void:
	var index: int = hooked if hooked >= 0 else suitor
	if index >= 0:
		fishes[index].spook = 5.0
	hooked = -1
	suitor = -1
	bite_left = 0
	strike_turn = 0
	tension = 0
	_phase("recover")
	_message(text, 1.7)
	beep(150, 0.12)

func _update_line() -> void:
	var start: Vector3 = Vector3(0.85, 1.65, 2.2)
	var end: Vector3 = lure.position
	if hooked >= 0:
		end = fishes[hooked].node.position
	fishing_line.position = (start + end) * 0.5
	fishing_line.look_at(end, Vector3.UP)
	fishing_line.scale.z = start.distance_to(end)

func pointer(action: String, pos: Vector2, id: int) -> void:
	if not running or finished or expired:
		return
	var screen: Vector2 = get_viewport().get_visible_rect().size
	var relative: Vector2 = (pos - REEL) * screen
	var radius: float = screen.x * 0.115
	if action == "down":
		if relative.length() < radius * 1.65:
			if crank_id < 0:
				crank_id = id
				crank_last_angle = relative.angle()
				crank_sample_time = elapsed
		elif phase == "idle":
			cast_id = id
			aim_x = pos.x
			power = 0
			_phase("charge")
	elif action == "move":
		if id == cast_id:
			aim_x = clampf(pos.x, 0, 1)
		elif id == crank_id and relative.length() > radius * 0.22:
			var angle: float = relative.angle()
			var delta: float = wrapf(angle - crank_last_angle, -PI, PI)
			var gap: float = maxf(0.004, elapsed - crank_sample_time)
			crank_rate = lerpf(crank_rate, clampf(delta / gap, -60, 60), 0.35)
			pending_turn += delta
			crank_angle += delta
			crank_last_angle = angle
			crank_sample_time = elapsed
	elif action == "up":
		if id == cast_id and phase == "charge":
			cast_id = -1
			cast_target = _landing_point()
			flight_start = lure.position
			flight_length = 0.35 + cast_target.distance_to(DOCK) / 0.02 * 0.0011
			suitor = -1
			bite_left = 0
			_phase("flight")
		if id == crank_id:
			crank_id = -1

func cancel_input() -> void:
	cast_id = -1
	crank_id = -1
	crank_rate = 0
	pending_turn = 0
	if phase == "charge":
		_phase("idle")

func _phase(next: String) -> void:
	phase = next
	phase_time = 0

func _message(text: String, seconds: float) -> void:
	status = text
	message_left = seconds

func time_up() -> void:
	cancel_input()
	if phase != "landing":
		_phase("idle")
		hooked = -1
		suitor = -1

func is_settled() -> bool:
	return phase != "landing"

func tickets_for_score() -> int:
	return 2 + int(score / 10)
