extends Node
## Starts the app (main.tscn) and saves a PNG after it settles (non-headless):
##   godot --path godot --resolution 1080x2400 res://tools/capture/app_probe.tscn -- [--out=C:/path/shot.png]
##       [--density=2.625] [--insets=0,48,0,24] [--dummy] [--frames=40]
## --dummy adds the capture tools' stand-in machine to the preview lobby. Not part of the game.

var out := "user://app_probe.png"
var wait := 40
var dummy := false
var frames := 0
var main: Main


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
		elif a.begins_with("--density="):
			Display.forced_density = float(a.substr(10))
		elif a.begins_with("--insets="):
			var v := a.substr(9).split(",")
			Display.forced_insets = Vector4(float(v[0]), float(v[1]), float(v[2]), float(v[3]))
		elif a == "--dummy":
			dummy = true
		elif a.begins_with("--frames="):
			wait = int(a.substr(9))
	Main.user_dir = "user://app_probe"
	DirAccess.make_dir_recursive_absolute(Main.user_dir)
	main = (load("res://main.tscn") as PackedScene).instantiate() as Main
	add_child(main)


func _process(_dt: float) -> void:
	frames += 1
	if frames == 3 and dummy:
		var lobby := main.app_root.get_child(0) as PreviewLobby
		if lobby != null:
			lobby.games.append(load("res://tools/capture/dummy_game.gd").new())
			lobby.queue_redraw()
	if frames == wait:
		await RenderingServer.frame_post_draw
		get_viewport().get_texture().get_image().save_png(out)
		print("saved %s" % out)
		get_tree().quit()
