extends ArcadeGame

const SLAB_HEIGHT = 0.18
var tower: Array[Dictionary] = []
var chunks: Array[Dictionary] = []
var moving_slab: MeshInstance3D
var lives: int = 3
var combo: int = 0
var height: int = 0
var move_on_x: bool = true
var direction: float = 1.0
var slide: float = -1.7
var landing: float = 0.0
var camera_height: float = 0.0

func _init() -> void:
	game_id = "stacker"
	title = "STACKER"
	instructions = "Tap to drop the sliding slab. Overhang breaks away. Perfect drops keep the whole slab and build a bonus. Three misses end the round."
	duration = 60.0

func build_game() -> void:
	camera.fov = 56.0
	box(Vector3(0, -0.35, 0), Vector3(8, 0.25, 8), Color("17152d"))
	for i in range(-4, 5):
		box(Vector3(i, -0.215, 0), Vector3(0.018, 0.01, 8), Color("583486"), 0.5)
		box(Vector3(0, -0.215, i), Vector3(8, 0.01, 0.018), Color("583486"), 0.5)
	box(Vector3(0, -0.10, 0), Vector3(1.65, 0.35, 1.65), Color("462968"))
	tower.append({"center": Vector2.ZERO, "size": Vector2(1.2, 1.2)})
	box(Vector3.ZERO, Vector3(1.2, SLAB_HEIGHT, 1.2), layer_color(0))
	spawn_slab()
	update_camera(1.0)
	update_status()

func layer_color(level: int) -> Color:
	return Color.from_hsv(fposmod(0.72 + level * 0.036, 1.0), 0.63, 1.0)

func spawn_slab() -> void:
	move_on_x = tower.size() % 2 == 1
	direction = 1.0 if rng.randf() > 0.5 else -1.0
	slide = -1.7 * direction
	var size: Vector2 = tower.back()["size"]
	moving_slab = box(Vector3.ZERO, Vector3(size.x, SLAB_HEIGHT, size.y), layer_color(tower.size()), 0.12)
	position_slab()

func position_slab() -> void:
	var center: Vector2 = tower.back()["center"]
	moving_slab.position = Vector3(center.x + (slide if move_on_x else 0.0), tower.size() * SLAB_HEIGHT, center.y + (0.0 if move_on_x else slide))

func pointer(action: String, _pos: Vector2, _id: int) -> void:
	if action == "down" and running and not finished and not expired and landing <= 0.0:
		drop_slab()

func drop_slab() -> void:
	var top: Dictionary = tower.back()
	var size: Vector2 = top["size"]
	var center: Vector2 = top["center"]
	var axis_size: float = size.x if move_on_x else size.y
	var overlap: float = axis_size - absf(slide)
	var y: float = tower.size() * SLAB_HEIGHT
	var color: Color = layer_color(tower.size())
	if overlap <= 0.0:
		add_chunk(moving_slab, Vector3(direction * 0.6 if move_on_x else 0.0, 0.4, direction * 0.6 if not move_on_x else 0.0))
		lives -= 1
		combo = 0
		beep(180, 0.15)
		if lives <= 0:
			status = "Tower toppled · %d floors" % height
			finish()
			return
	else:
		if absf(slide) <= 0.05:
			combo += 1
			if combo > 3:
				size = (size + Vector2(0.08, 0.08)).min(Vector2(1.2, 1.2))
			award(20 + 5 * mini(combo - 1, 4))
			beep(700 + mini(combo, 8) * 80, 0.09)
		else:
			combo = 0
			var cut: float = absf(slide)
			var side: float = signf(slide)
			if move_on_x:
				size.x = overlap
				center.x += slide * 0.5
				var piece = box(Vector3(center.x + side * (overlap + cut) * 0.5, y, center.y), Vector3(cut, SLAB_HEIGHT, size.y), color)
				add_chunk(piece, Vector3(side * 0.6, 0.4, 0))
			else:
				size.y = overlap
				center.y += slide * 0.5
				var piece = box(Vector3(center.x, y, center.y + side * (overlap + cut) * 0.5), Vector3(size.x, SLAB_HEIGHT, cut), color)
				add_chunk(piece, Vector3(0, 0.4, side * 0.6))
			award(10)
			beep(410, 0.05)
		moving_slab.queue_free()
		box(Vector3(center.x, y, center.y), Vector3(size.x, SLAB_HEIGHT, size.y), color, 0.05)
		tower.append({"center": center, "size": size})
		height += 1
	landing = 0.15
	spawn_slab()
	update_status()

func add_chunk(node: MeshInstance3D, velocity: Vector3) -> void:
	chunks.append({"node": node, "velocity": velocity, "spin": Vector3(rng.randf_range(-3, 3), 0, rng.randf_range(-3, 3))})

func step(dt: float) -> void:
	landing = maxf(0.0, landing - dt)
	if landing <= 0.0:
		slide += minf(1.5 + height * 0.07, 4.3) * direction * dt
		if absf(slide) > 1.7:
			slide = clampf(slide, -1.7, 1.7)
			direction = -direction
		position_slab()
	for i in range(chunks.size() - 1, -1, -1):
		var item: Dictionary = chunks[i]
		var velocity: Vector3 = item["velocity"]
		velocity.y -= 9.0 * dt
		item["velocity"] = velocity
		var node: MeshInstance3D = item["node"]
		node.position += velocity * dt
		node.rotation += (item["spin"] as Vector3) * dt
		if node.position.y < camera_height - 5:
			node.queue_free()
			chunks.remove_at(i)
	update_camera(dt)

func update_camera(dt: float) -> void:
	camera_height = lerpf(camera_height, height * SLAB_HEIGHT, minf(1.0, dt * 4))
	camera.position = Vector3(3.9, camera_height + 4.0, 5.1)
	camera.look_at(Vector3(0, camera_height, 0))

func update_status() -> void:
	status = "%d floors  ·  %d lives  ·  %s" % [height, lives, "PERFECT ×%d" % combo if combo > 0 else "Tap to drop"]

func cancel_input() -> void:
	pass

func tickets_for_score() -> int:
	return 1 + score / 20
