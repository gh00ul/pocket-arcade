extends SceneTree

func _initialize() -> void:
	call_deferred("capture")

func capture() -> void:
	var output: String = ProjectSettings.globalize_path("res://../.tools/screenshots")
	DirAccess.make_dir_recursive_absolute(output)
	for name: String in ["shooter", "pinball", "fishing"]:
		var script: Script = load("res://games/%s.gd" % name)
		var game: Node = script.new()
		root.add_child(game)
		game.set_physics_process(false)
		game.running = true
		game.rng.seed = 12345
		if name == "shooter":
			game._spawn_target(false)
			game._spawn_target(false)
			game.step(0.3)
		elif name == "pinball":
			game.step(0.01)
		else:
			game.pointer("down", Vector2(0.4, 0.4), 8)
			game.step(0.4)
		for i: int in 4:
			await process_frame
		await RenderingServer.frame_post_draw
		root.get_texture().get_image().save_png(output.path_join("%s.png" % name))
		game.queue_free()
		await process_frame
	quit()
