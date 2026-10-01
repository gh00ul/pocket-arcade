extends Node

func _ready() -> void:
	_run.call_deferred()

func _run() -> void:
	var path: String = ProjectSettings.globalize_path("user://game_previews")
	DirAccess.make_dir_recursive_absolute(path)
	for id in ["claw", "skeeball", "whack", "pusher"]:
		var script = load("res://games/%s.gd" % id)
		var game: ArcadeGame = script.new()
		add_child(game)
		game.set_physics_process(false)
		if id == "whack":
			for i in [0, 4, 8]:
				var m: Dictionary = game.moles[i]
				m.kind = int(i / 4)
				m.phase = 2
				m.rise = 1.0
				game._make_mole(m)
			game.step(0.01)
		await get_tree().process_frame
		await RenderingServer.frame_post_draw
		get_viewport().get_texture().get_image().save_png(path.path_join(id + ".png"))
		print("PREVIEW ", path.path_join(id + ".png"))
		game.queue_free()
		await get_tree().process_frame
	get_tree().quit()
