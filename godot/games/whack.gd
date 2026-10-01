extends ArcadeGame

var moles: Array[Dictionary] = []
var spawn_timer: float = 0.6
var combo: int = 0
var stun: float = 0.0
var hammer: Node3D
var hammer_time: float = 1.0
var panel: Label3D

func _init() -> void:
	game_id = "whack"
	title = "WHACK-A-MOLE"
	instructions = "Tap the moles as they pop up. Gold moles score double. Avoid black bombs: they cost 30 points! Consecutive hits build a combo."
	duration = 45.0

func build_game() -> void:
	camera.position = Vector3(0, 7.5, 9.8)
	camera.look_at(Vector3(0, 0.4, 0))
	camera.fov = 49
	box(Vector3(0, -0.5, 0), Vector3(4.7, 0.95, 5), Color("264b38"))
	box(Vector3(0, -0.01, 0), Vector3(4.55, 0.09, 4.9), Color("549650"))
	for x in [-2.35, 2.35]:
		box(Vector3(x, 0.07, 0), Vector3(0.14, 0.22, 5.1), Color("c89858"))
	box(Vector3(0, 1.0, -2.5), Vector3(4.8, 2.15, 0.18), Color("164a37"))
	label3d("MOLE PATROL", Vector3(0, 1.6, -2.36), 46, Color("ffe994"))
	panel = label3d("BONK 'EM!", Vector3(0, 0.92, -2.3), 33, Color("aeffbf"))
	for i in range(13):
		sphere(Vector3(-2.14 + i * 0.355, 2.07, -2.36), 0.065, Color("ffdc59"))
	for i in range(9):
		var p = Vector3((i % 3 - 1) * 1.43, 0.04, (int(i / 3) - 1) * 1.55)
		cylinder(p, 0.58, 0.08, Color("997144"))
		cylinder(p + Vector3(0, 0.052, 0), 0.48, 0.03, Color("101b17"))
		var root = Node3D.new()
		add_child(root)
		root.position = p
		root.visible = false
		moles.append({"root": root, "pos": p, "phase": 0, "kind": 0, "t": 0.0, "up": 1.0, "rise": 0.0})
	hammer = Node3D.new()
	add_child(hammer)
	var head = cylinder(Vector3.ZERO, 0.2, 0.75, Color("ff859b"), hammer)
	head.rotation.z = PI / 2.0
	box(Vector3(0, -0.38, 0.19), Vector3(0.10, 0.8, 0.10), Color("dfb27f"), 0, hammer)
	hammer.visible = false
	status = "Tap moles • Avoid bombs"

func _make_mole(m: Dictionary) -> void:
	var root: Node3D = m.root
	for child in root.get_children():
		child.queue_free()
	var kind: int = m.kind
	var color = Color("bb7d50") if kind == 0 else Color("ffc849")
	if kind == 2:
		sphere(Vector3(0, 0.40, 0), 0.40, Color("182033"), root)
		var fuse = cylinder(Vector3(0.06, 0.85, 0), 0.036, 0.23, Color("d79558"), root)
		fuse.rotation.z = -0.35
		sphere(Vector3(0.1, 0.96, 0), 0.07, Color("ff763d"), root)
		label3d("!", Vector3(0, 0.5, 0.4), 35, Color("ff7777"), root)
	else:
		var body = sphere(Vector3(0, 0.31, 0), 0.4, color, root)
		body.scale.y = 1.35
		sphere(Vector3(-0.27, 0.67, 0), 0.13, color, root)
		sphere(Vector3(0.27, 0.67, 0), 0.13, color, root)
		sphere(Vector3(0, 0.42, 0.30), 0.21, Color("efd2a1"), root)
		for x in [-0.14, 0.14]:
			sphere(Vector3(x, 0.59, 0.315), 0.05, Color("111b28"), root)
		sphere(Vector3(0, 0.46, 0.5), 0.075, Color("714639"), root)
		if kind == 1:
			label3d("★", Vector3(0, 0.96, 0), 30, Color("fff194"), root)

func step(dt: float) -> void:
	stun = maxf(0, stun - dt)
	hammer_time += dt
	hammer.visible = hammer_time < 0.28
	if hammer.visible:
		hammer.rotation.x = -0.3 + absf(hammer_time - 0.1) * 5
	var progress: float = clampf(1.0 - time_left / duration, 0, 1)
	if not expired:
		spawn_timer -= dt
		if spawn_timer <= 0:
			_spawn(progress)
			spawn_timer = lerpf(0.95, 0.38, progress) * rng.randf_range(0.75, 1.25)
	for m in moles:
		m.t += dt
		match int(m.phase):
			1:
				m.rise = minf(1, m.t / 0.12)
				if m.t >= 0.12:
					m.phase = 2
					m.t = 0.0
			2:
				if m.t >= m.up:
					m.phase = 3
					m.t = 0.0
					if m.kind != 2 and not expired:
						combo = 0
			3:
				m.rise = maxf(0, 1 - m.t / 0.14)
				if m.t >= 0.14:
					m.phase = 0
			4:
				m.rise = maxf(0, 1 - m.t / 0.4)
				if m.t >= 0.4:
					m.phase = 0
		var root: Node3D = m.root
		root.visible = m.phase != 0
		root.position.y = 0.1 - (1 - float(m.rise)) * 0.9
		root.scale.y = 0.5 if m.phase == 4 else 1.0
	panel.text = "STUNNED!" if stun > 0 else ("COMBO × %d" % combo if combo >= 2 else "BONK 'EM!")
	status = "Avoid bombs!" if stun > 0 else "Combo %d • Gold = 20 points" % combo

func _spawn(progress: float) -> void:
	var free: Array[int] = []
	for i in range(moles.size()):
		if moles[i].phase == 0:
			free.append(i)
	if free.is_empty():
		return
	var m: Dictionary = moles[free[rng.randi_range(0, free.size() - 1)]]
	var chance = lerpf(0.10, 0.22, progress)
	var roll = rng.randf()
	m.kind = 2 if roll < chance else (1 if roll < chance + 0.08 else 0)
	m.phase = 1
	m.t = 0.0
	m.rise = 0.0
	m.up = lerpf(1.15, 0.55, progress) * rng.randf_range(0.85, 1.15)
	_make_mole(m)
	beep(850 if m.kind == 1 else 480, 0.035)

func pointer(action: String, pos: Vector2, _id: int) -> void:
	if action != "down" or not running or expired or stun > 0:
		return
	var viewport_size: Vector2 = get_viewport().get_visible_rect().size
	var pixel = pos * viewport_size
	var best: int = -1
	var distance: float = INF
	for i in range(moles.size()):
		var p: Vector3 = moles[i].pos
		var top = camera.unproject_position(p + Vector3(0, 0.86, 0))
		var bottom = camera.unproject_position(p + Vector3(0, 0, 0.36))
		var middle = camera.unproject_position(p + Vector3(0, 0.4, 0))
		var edge = camera.unproject_position(p + Vector3(0.62, 0.4, 0))
		if absf(pixel.x - middle.x) < absf(edge.x - middle.x) and pixel.y > top.y - 10 and pixel.y < bottom.y + 10:
			var d = pixel.distance_to(middle)
			if d < distance:
				distance = d
				best = i
	if best < 0:
		return
	var m: Dictionary = moles[best]
	hammer.position = m.pos + Vector3(0.10, 0.85, 0.20)
	hammer_time = 0
	if m.phase < 1 or m.phase > 3 or m.rise < 0.3:
		combo = 0
		beep(160, 0.05)
		return
	m.phase = 4
	m.t = 0.0
	if m.kind == 2:
		combo = 0
		stun = 0.5
		award(-30)
		beep(85, 0.18)
	else:
		combo += 1
		award((20 if m.kind == 1 else 10) + int(combo / 5) * 2)
		beep(700 + combo * 12, 0.07)

func time_up() -> void:
	for m in moles:
		if m.phase in [1, 2]:
			m.phase = 3
			m.t = 0.0

func tickets_for_score() -> int:
	return 1 + int(score / 30)
