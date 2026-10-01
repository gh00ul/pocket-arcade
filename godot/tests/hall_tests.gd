extends Node

var hall: ArcadeGame
var failures: int = 0

func _ready() -> void:
	call_deferred("run_tests")

func check(condition: bool, message: String) -> void:
	if condition:
		print("PASS: ",message)
	else:
		push_error("FAIL: "+message)
		failures += 1

func run_tests() -> void:
	var original: Dictionary = SaveStore.data.duplicate(true)
	for item in ArcadeCatalog.items("decor"):
		if item.id not in SaveStore.data.owned:
			SaveStore.data.owned.append(item.id)
	SaveStore.data.hat = "hat_propeller"
	SaveStore.data.outfit = "outfit_neon"
	var script = load("res://scripts/hall.gd")
	hall = script.new()
	get_tree().root.add_child(hall)
	hall.set_physics_process(false)
	await get_tree().physics_frame
	await get_tree().process_frame
	for argument in OS.get_cmdline_user_args():
		if argument.begins_with("--hall-preview="):
			var mode: String = argument.trim_prefix("--hall-preview=")
			hall.first_person = mode == "first_person"
			hall.body.position = Vector3(0,0,14 if hall.first_person else 6)
			hall._physics_process(1.0/120.0)
			hall.update_camera(1.0)
			await get_tree().process_frame
			await RenderingServer.frame_post_draw
			var path: String = ProjectSettings.globalize_path("res://../.tools/hall_"+mode+".png")
			get_viewport().get_texture().get_image().save_png(path)
			print(path)
			SaveStore.data = original
			get_tree().quit()
			return
	check(hall.spots.size() == hall.spot_games.size(), "Every play spot has a logical game mapping")
	check(hall.cabinets.size() == 19, "Hall contains all eleven games and eight playable duplicates")
	var space: PhysicsDirectSpaceState3D = hall.get_world_3d().direct_space_state
	var shape = CapsuleShape3D.new()
	shape.radius = 0.27
	shape.height = 1.35
	for i in range(hall.spots.size()):
		var spot: Vector3 = hall.spots[i]
		var route: Array[Vector3] = [Vector3(0,0.72,14),Vector3(0,0.72,spot.z),Vector3(spot.x,0.72,spot.z)]
		var clear: bool = true
		for leg in range(2):
			var query = PhysicsShapeQueryParameters3D.new()
			query.shape = shape
			query.transform = Transform3D(Basis.IDENTITY,route[leg])
			query.motion = route[leg+1]-route[leg]
			query.exclude = [hall.body.get_rid()]
			var result: PackedFloat32Array = space.cast_motion(query)
			if result[0] < 0.999:
				clear = false
		check(clear,"Spot %d (game %d) is reachable from the main aisle" % [i,hall.spot_games[i]])
		hall.body.position = spot
		hall._physics_process(1.0/120.0)
		check(hall.nearby == hall.spot_games[i],"Spot %d selects its own logical game" % i)
	for id in range(13):
		check(id in hall.spot_games,"Logical machine %d is present" % id)
	check(hall.decor_root.get_child_count() == 9,"All nine purchased decorations have native 3D models")
	for item in ArcadeCatalog.items("hat"):
		SaveStore.data.hat = item.id
		hall.refresh_avatar()
		check(hall.avatar.get_child_count() > 12,item.name+" has a visible native model")
	for outfit in ArcadeCatalog.OUTFITS:
		SaveStore.data.outfit = "outfit_"+outfit[0]
		hall.refresh_avatar()
		var torso: MeshInstance3D = hall.avatar.get_child(0)
		check(torso.material_override.albedo_color.is_equal_approx(Color(outfit[3])),outfit[1]+" applies its expected colour")
	SaveStore.data = original
	hall.free()
	await get_tree().process_frame
	print("HALL TESTS: ", "PASS" if failures == 0 else str(failures)+" FAILURES")
	get_tree().quit(0 if failures == 0 else 1)
