class_name ArcadeGame
extends Node3D

signal round_finished
var game_id: String = ""
var title: String = ""
var instructions: String = ""
var duration: float = 60.0
var time_left: float = 60.0
var score: int = 0
var running: bool = false
var finished: bool = false
var expired: bool = false
var settle_time: float = 0.0
var status: String = ""
var prizes: Array[String] = []
var rng: RandomNumberGenerator = RandomNumberGenerator.new()
var camera: Camera3D
var sound: AudioStreamPlayer
var sound_wait: float = 0.0

func _ready() -> void:
	rng.randomize()
	time_left = duration
	camera = Camera3D.new()
	camera.fov = 55.0
	camera.near = 0.05
	add_child(camera)
	camera.current = true
	var env = WorldEnvironment.new()
	env.environment = Environment.new()
	env.environment.background_mode = Environment.BG_COLOR
	env.environment.background_color = Color("0c0d21")
	env.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.environment.ambient_light_color = Color("a7b8ff")
	env.environment.ambient_light_energy = 0.65
	add_child(env)
	var sun = DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-48, -25, 0)
	sun.light_energy = 1.3
	add_child(sun)
	sound = AudioStreamPlayer.new()
	add_child(sound)
	build_game()

func _physics_process(dt: float) -> void:
	sound_wait = maxf(0, sound_wait - dt)
	if not running or finished:
		return
	if not expired:
		time_left = maxf(0.0, time_left - dt)
		if time_left <= 0:
			expired = true
			time_up()
	step(dt)
	if expired:
		settle_time += dt
		if is_settled() or settle_time > 10.0:
			finish()

func build_game() -> void:
	pass
func step(_dt: float) -> void:
	pass
func pointer(_action: String, _pos: Vector2, _id: int) -> void:
	pass
func cancel_input() -> void:
	pass
func time_up() -> void:
	pass
func is_settled() -> bool:
	return true
func tickets_for_score() -> int:
	return 1 + int(score / 20.0)
func award(points: int) -> void:
	score = maxi(0, score + points)
	if points > 0:
		beep()
		if OS.get_name() == "Android":
			Input.vibrate_handheld(12)
func finish() -> void:
	if finished:
		return
	finished = true
	running = false
	cancel_input()
	round_finished.emit()

func material(color: Color, glow: float = 0.0) -> StandardMaterial3D:
	var mat = StandardMaterial3D.new()
	mat.albedo_color = color
	mat.roughness = 0.48
	if color.a < 1:
		mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	if glow > 0:
		mat.emission_enabled = true
		mat.emission = color
		mat.emission_energy_multiplier = glow
	return mat

func mesh_node(mesh: Mesh, pos: Vector3, color: Color, parent: Node = null, glow: float = 0) -> MeshInstance3D:
	var instance = MeshInstance3D.new()
	instance.mesh = mesh
	instance.material_override = material(color, glow)
	(parent if parent != null else self).add_child(instance)
	instance.position = pos
	return instance

func box(pos: Vector3, dimensions: Vector3, color: Color, glow: float = 0, parent: Node = null) -> MeshInstance3D:
	var mesh = BoxMesh.new()
	mesh.size = dimensions
	return mesh_node(mesh, pos, color, parent, glow)

func sphere(pos: Vector3, radius: float, color: Color, parent: Node = null) -> MeshInstance3D:
	var mesh = SphereMesh.new()
	mesh.radius = radius
	mesh.height = radius * 2
	mesh.radial_segments = 16
	mesh.rings = 8
	return mesh_node(mesh, pos, color, parent)

func cylinder(pos: Vector3, radius: float, height: float, color: Color, parent: Node = null) -> MeshInstance3D:
	var mesh = CylinderMesh.new()
	mesh.top_radius = radius
	mesh.bottom_radius = radius
	mesh.height = height
	mesh.radial_segments = 20
	return mesh_node(mesh, pos, color, parent)

func label3d(words: String, pos: Vector3, font_size: int = 32, color: Color = Color.WHITE, parent: Node = null) -> Label3D:
	var label = Label3D.new()
	label.text = words
	label.font_size = font_size
	label.pixel_size = 0.008
	label.modulate = color
	label.outline_size = 5
	label.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	(parent if parent != null else self).add_child(label)
	label.position = pos
	return label

func aim(pos: Vector2, y: float = 0.0) -> Vector3:
	var point = pos * get_viewport().get_visible_rect().size
	var origin = camera.project_ray_origin(point)
	var direction = camera.project_ray_normal(point)
	if absf(direction.y) < 0.00001:
		return Vector3.ZERO
	return origin + direction * ((y - origin.y) / direction.y)

func beep(freq: float = 660.0, length: float = 0.07) -> void:
	if sound_wait > 0 or SaveStore.data.muted or sound == null or DisplayServer.get_name() == "headless":
		return
	sound_wait = 0.045
	var stream = AudioStreamWAV.new()
	stream.format = AudioStreamWAV.FORMAT_16_BITS
	stream.mix_rate = 22050
	var samples = int(22050 * length)
	var bytes = PackedByteArray()
	bytes.resize(samples * 2)
	for i in samples:
		var wave = sin(TAU * freq * i / 22050.0)
		bytes.encode_s16(i * 2, int(wave * 4500 * (1.0 - float(i) / samples)))
	stream.data = bytes
	sound.stream = stream
	sound.play()
