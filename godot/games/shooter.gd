extends ArcadeGame

const SPAWN_INTERVAL = [1.0, 0.78, 0.6, 1.4]
const MAX_UP = [2, 3, 4, 2]
const UP_TIME = [2.3, 1.9, 1.55, 1.7]
const CIVILIAN_CHANCE = [0.12, 0.15, 0.18, 0.12]
const GUNNER_CHANCE = [0.15, 0.28, 0.4, 0.35]
const TELL_TIME = [1.05, 0.9, 0.75, 0.75]
const DRONE_CHANCE = [0.0, 0.25, 0.3, 0.5]
var targets: Array[Dictionary] = []
var spots: Array[Vector3] = []
var ammo: int = 6
var hearts: int = 3
var combo: int = 0
var shots: int = 0
var hits: int = 0
var reload_left: float = 0.0
var down_left: float = 0.0
var spawn_left: float = 0.8
var gold_at: float = 22.0
var gold_spawned: bool = false
var elapsed: float = 0.0
var wave: int = 0
var boss: Node3D
var boss_cores: Array[MeshInstance3D] = []
var boss_hp: int = 100
var boss_clock: float = 0.0
var boss_charge: float = 0.0
var boss_charge_wait: float = 3.4
var boss_stagger: int = 0
var cleared: bool = false
var tallied: bool = false
var info: Label3D
var boss_label: Label3D
var flash: MeshInstance3D
var flash_left: float = 0.0
var message_left: float = 0.0

func _init() -> void:
	game_id = "shooter"
	title = "SHOOTOUT"
	duration = 55.0
	instructions = "Tap the robots to shoot. Six shots: tap RELOAD below. Save the hands-up civilians! Red robots fire back. Aim for the mothership's glowing cores."

func build_game() -> void:
	camera.projection = Camera3D.PROJECTION_ORTHOGONAL
	camera.size = 14.0
	camera.position = Vector3(0, 4.5, 18)
	camera.look_at(Vector3(0, 4.5, 0))
	box(Vector3(0, -0.15, 0), Vector3(12, 0.3, 18), Color("182036"))
	box(Vector3(0, 4.7, -3), Vector3(9, 9.4, 0.8), Color("28334e"))
	for x: float in [-3.0, -1.5, 0.0, 1.5, 3.0]:
		box(Vector3(x, 4.4, -2.52), Vector3(0.12, 8.6, 0.08), Color("3a4864"))
	label3d("NEON CITY BANK", Vector3(0, 9, -2.3), 44, Color("ffd77d"))
	for x: float in [-2.8, 0.0, 2.8]:
		box(Vector3(x, 6.7, -2.3), Vector3(1.65, 1.8, 0.14), Color("080d1d"))
		box(Vector3(x, 5.76, -2.0), Vector3(1.9, 0.16, 0.45), Color("49d9ff"), 0.5)
		spots.append(Vector3(x, 6.2, -1.9))
	for x: float in [-2.8, 0.0, 2.8]:
		box(Vector3(x, 2.2, 0.0), Vector3(1.8, 1.4, 1.0), Color("72514c"))
		box(Vector3(x, 2.2, 0.55), Vector3(1.62, 0.14, 0.08), Color("e0ac67"))
		spots.append(Vector3(x, 3.65, -0.05))
	for x: float in [-1.8, 1.8]:
		cylinder(Vector3(x, 0.7, 2.3), 0.72, 1.4, Color("435071"))
		spots.append(Vector3(x, 2.05, 2.1))
	box(Vector3(0, 0.24, 4), Vector3(6.7, 0.65, 0.2), Color("db704c"), 0.25)
	label3d("TAP HERE TO RELOAD", Vector3(0, 0.3, 4.14), 37, Color("fff0c6"))
	info = label3d("", Vector3(0, 1.15, 4.2), 30, Color("fff0c6"))
	flash = sphere(Vector3.ZERO, 0.15, Color("fff4ae"))
	flash.visible = false
	gold_at = rng.randf_range(15, 34)
	status = "Shoot robots • protect the cyan civilians"

func step(dt: float) -> void:
	elapsed += dt
	wave = 3 if elapsed >= 38 else (2 if elapsed >= 26 else (1 if elapsed >= 13 else 0))
	message_left = maxf(0.0, message_left - dt)
	flash_left -= dt
	flash.visible = flash_left > 0.0
	if reload_left > 0.0:
		reload_left -= dt
		if reload_left <= 0.0:
			ammo = 6
			beep(440, 0.08)
	if down_left > 0.0:
		down_left -= dt
		if down_left <= 0.0:
			hearts = 3
	for i: int in range(targets.size() - 1, -1, -1):
		var t: Dictionary = targets[i]
		t.age += dt
		var node: Node3D = t.node
		if t.kind >= 2:
			node.position.x += t.speed * dt
			node.position.y = t.origin.y + sin(elapsed * 5.0) * 0.13
		else:
			node.position.y = t.origin.y - (1.0 - minf(t.age / 0.18, 1.0)) * 1.2
			if t.gunner and not t.fired and t.age > t.life - TELL_TIME[wave]:
				t.ring.visible = true
				t.ring.scale = Vector3.ONE * (1.0 + sin(t.age * 20.0) * 0.13)
			if t.gunner and not t.fired and t.age > t.life:
				t.fired = true
				_hurt()
		if t.age > t.life + 0.15 or absf(node.position.x) > 5.5:
			node.queue_free()
			targets.remove_at(i)
	spawn_left -= dt
	if spawn_left <= 0.0:
		spawn_left = SPAWN_INTERVAL[wave] * rng.randf_range(0.75, 1.25)
		if targets.size() < MAX_UP[wave]:
			_spawn_target(false)
	if not gold_spawned and elapsed >= gold_at:
		gold_spawned = true
		_spawn_target(true)
	if wave == 3 and boss == null:
		_build_boss()
	if boss != null and not cleared:
		_step_boss(dt)
	var rounds: String = "RELOADING..." if reload_left > 0.0 else "● ".repeat(ammo) + "○ ".repeat(6 - ammo)
	info.text = rounds + "\n" + "♥ ".repeat(hearts) + "   COMBO ×%d" % _mult()
	if message_left <= 0.0:
		status = "KNOCKED DOWN — recovering" if down_left > 0.0 else ("BOSS: aim at glowing cores!" if wave == 3 else "WAVE %d • Cyan hands up = civilian" % (wave + 1))

func _spawn_target(gold: bool) -> void:
	var kind: int = 3 if gold else (2 if rng.randf() < DRONE_CHANCE[wave] else (1 if rng.randf() < CIVILIAN_CHANCE[wave] else 0))
	var spot: int = rng.randi_range(0, spots.size() - 1)
	for attempt: int in 12:
		var busy: bool = false
		for existing: Dictionary in targets:
			if existing.spot == spot and existing.kind < 2:
				busy = true
		if not busy:
			break
		spot = (spot + 1) % spots.size()
		if attempt == 11:
			return
	var node := Node3D.new()
	add_child(node)
	node.position = spots[spot] if kind < 2 else Vector3(-4.8, rng.randf_range(4.7, 7.3), 1)
	var color: Color = [Color("ff6c51"), Color("54efff"), Color("b485fc"), Color("ffd34f")][kind]
	if kind < 2:
		box(Vector3(0, -0.12, 0), Vector3(0.7, 0.72, 0.35), color, 0.12, node)
		box(Vector3(0, 0.49, 0), Vector3(0.58, 0.5, 0.42), color, 0.2, node)
		box(Vector3(0, 0.51, 0.24), Vector3(0.41, 0.1, 0.05), Color("0e1533"), 0.0, node)
		for side: float in [-1.0, 1.0]:
			var arm: MeshInstance3D = box(Vector3(side * 0.52, 0.3 if kind == 1 else -0.05, 0), Vector3(0.2, 0.62, 0.22), color, 0, node)
			arm.rotation.z = side * (-0.4 if kind == 1 else 0.3)
		if kind == 1:
			label3d("SAVE", Vector3(0, 1, 0), 22, Color("8effef"), node)
	else:
		var body: MeshInstance3D = sphere(Vector3.ZERO, 0.55, color, node)
		body.scale = Vector3(1.4, 0.52, 1)
		box(Vector3.ZERO, Vector3(1.65, 0.13, 0.25), color, 0.5, node)
		cylinder(Vector3(0, 0.2, 0), 0.26, 0.3, Color("222840"), node)
	var ring: MeshInstance3D = sphere(Vector3(0, 0.9, 0), 0.13, Color("ff263e"), node)
	ring.visible = false
	targets.append({"node": node, "kind": kind, "spot": spot, "origin": node.position, "age": 0.0, "life": (UP_TIME[wave] * rng.randf_range(0.85, 1.15)) if kind < 2 else 5.0, "gunner": kind == 0 and rng.randf() < GUNNER_CHANCE[wave], "fired": false, "ring": ring, "speed": 4.5 if gold else 2.6})

func _build_boss() -> void:
	boss = Node3D.new()
	add_child(boss)
	boss.position = Vector3(0, 8.0, 2)
	var hull: MeshInstance3D = sphere(Vector3.ZERO, 1, Color("8e749b"), boss)
	hull.scale = Vector3(2.75, 0.6, 0.8)
	var dome: MeshInstance3D = sphere(Vector3(0, 0.5, 0), 0.6, Color("3c7e93"), boss)
	dome.scale.x = 1.3
	for x: float in [-1.95, 1.95, 0.0]:
		cylinder(Vector3(x, -0.35, 0.5), 0.43, 0.7, Color("26314a"), boss)
		boss_cores.append(sphere(Vector3(x, -0.43, 0.94), 0.26, Color("ffd659"), boss))
	boss_label = label3d("MOTHERSHIP 100", Vector3(0, 1.25, 0.4), 32, Color("ffe3a3"), boss)
	beep(180, 0.4)

func _step_boss(dt: float) -> void:
	boss_clock += dt
	boss.position.x = sin(boss_clock * 0.65) * 0.6
	boss.position.y = 7.85 + sin(boss_clock * 1.3) * 0.22
	for i: int in 2:
		boss_cores[i].visible = fmod(boss_clock + i * 1.6, 3.2) < 2.0
	boss_cores[2].visible = boss_charge > 0.0
	boss_label.text = "MOTHERSHIP %d%s" % [boss_hp, " • CHARGING!" if boss_charge > 0.0 else ""]
	if boss_clock < 2.2:
		return
	if boss_charge > 0.0:
		boss_charge -= dt
		if boss_stagger >= 12:
			boss_charge = 0.0
			boss_charge_wait = 3.4
		elif boss_charge <= 0.0:
			_hurt()
			boss_charge_wait = 3.4
	else:
		boss_charge_wait -= dt
		if boss_charge_wait <= 0.0:
			boss_charge = 1.3
			boss_stagger = 0

func pointer(action: String, pos: Vector2, _id: int) -> void:
	if action != "down" or not running or finished:
		return
	var point: Vector3 = _at_z(pos, 4.0)
	if point.y < 0.9 or ammo == 0:
		if reload_left <= 0.0 and ammo < 6:
			reload_left = 0.75
			beep(240, 0.07)
		return
	if reload_left > 0.0 or down_left > 0.0:
		return
	ammo -= 1
	shots += 1
	flash.position = _at_z(pos, 4.5)
	flash_left = 0.07
	beep(120, 0.04)
	for i: int in range(targets.size() - 1, -1, -1):
		var t: Dictionary = targets[i]
		if _hit_rect(t.node.global_position, Vector2(0.9, 1.45) if t.kind < 2 else Vector2(1.6, 0.85), pos):
			if t.kind == 1:
				award(-100)
				combo = 0
				status = "CIVILIAN! −100"
			else:
				var points: int = (50 + (25 if t.ring.visible else 0)) if t.kind == 0 else (300 if t.kind == 3 else 75)
				award(points * _mult())
				hits += 1
				combo += 1
				status = "+%d • NICE SHOT!" % (points * _mult())
				beep(860, 0.05)
			message_left = 0.75
			t.node.queue_free()
			targets.remove_at(i)
			return
	if boss != null and not cleared and boss_clock >= 2.2:
		var part: int = -1
		for i: int in 3:
			if boss_cores[i].visible and _hit_rect(boss_cores[i].global_position, Vector2(0.75, 0.75), pos):
				part = i
		if part >= 0 or _hit_rect(boss.global_position, Vector2(5.5, 1.3), pos):
			boss_hp -= 6 if part >= 0 else 1
			award((40 if part >= 0 else 5) * _mult())
			if part == 2:
				boss_stagger += 6
			hits += 1
			combo += 1
			if boss_hp <= 0:
				cleared = true
				award(1500 + int(ceil(time_left)) * 20)
				status = "MOTHERSHIP DOWN! +5 BONUS TICKETS"
				finish()
			return
	combo = 0

func _hit_rect(world: Vector3, size: Vector2, pos: Vector2) -> bool:
	var tap: Vector3 = _at_z(pos, world.z)
	return absf(tap.x - world.x) < size.x * 0.5 and absf(tap.y - world.y - 0.18) < size.y * 0.5

func _at_z(pos: Vector2, z: float) -> Vector3:
	var pixel: Vector2 = pos * get_viewport().get_visible_rect().size
	var origin: Vector3 = camera.project_ray_origin(pixel)
	var direction: Vector3 = camera.project_ray_normal(pixel)
	return origin + direction * ((z - origin.z) / direction.z)

func _hurt() -> void:
	if down_left > 0.0:
		return
	hearts -= 1
	combo = 0
	award(-50)
	status = "HIT! −50 • shoot red warnings first"
	message_left = 1.0
	beep(90, 0.2)
	if hearts <= 0:
		down_left = 1.6

func _mult() -> int:
	return mini(4, 1 + int(combo / 5))

func finish() -> void:
	if not tallied:
		tallied = true
		if shots > 0:
			var accuracy: float = float(hits) / shots
			award(int(600.0 * accuracy * accuracy / 10.0) * 10)
	super.finish()

func tickets_for_score() -> int:
	return 2 + int(score / 400) + (5 if cleared else 0)
