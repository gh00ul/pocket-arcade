extends Control
## Opens a machine in the game host and saves a PNG of one moment of a round (non-headless):
##   godot --path godot --resolution 1080x2400 res://tools/capture/host_probe.tscn -- --game=claw
##       --phase=intro|countdown|play|ending|results|paused [--out=C:/path/shot.png] [--taps]
## The round is fast-forwarded on the host's fixed steps to the moment asked for. Not part of the game.

var host: GameHostScreen
var phase := "intro"
var out := "user://host_probe.png"
var taps := false
var frames := 0


func _ready() -> void:
	var game_id := "claw"
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--game="):
			game_id = a.substr(7)
		elif a.begins_with("--phase="):
			phase = a.substr(8)
		elif a.begins_with("--out="):
			out = a.substr(6)
		elif a == "--taps":
			taps = true
	Display.configure(get_window())
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	var gfx := Control.new()
	gfx.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	gfx.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(gfx)
	Gfx.host = gfx
	var audio := AudioSynth.new()
	add_child(audio)
	var dir := "user://host_probe"
	DirAccess.make_dir_recursive_absolute(dir)
	var services := ArcadeServices.open(dir, audio)
	var game: MiniGame = load(game_id).new() if game_id.begins_with("res://") else GameRegistry.create(game_id)
	if game is BaseMiniGame:
		(game as BaseMiniGame).fixed_seed = 3
	host = GameHostScreen.create(game, services)
	add_child(host)
	var r := host.round
	if phase == "intro":
		return
	r.start_round()
	var targets := {"countdown": 1.0, "play": 4.0, "paused": 4.0, "ending": 0.0, "results": 0.0}
	var seconds: float = targets.get(phase, 0.0)
	var steps := 0
	while steps < 120 * 200:
		if phase == "ending" and r.phase == GameRound.Phase.ENDING and r.phase_t > 0.4:
			break
		if phase == "results" and r.phase == GameRound.Phase.RESULTS and r.phase_t > 4.5:
			break
		if seconds > 0.0 and steps >= int(seconds * 120.0):
			break
		if taps and r.phase == GameRound.Phase.PLAYING and steps % 30 == 0:
			var x := r.gx + (40.0 + (steps * 7919 % 280)) * r.gs
			var y := r.gy + (140.0 + (steps * 104729 % 400)) * r.gs
			r.touch(TouchType.DOWN, 1, x, y, steps * 8)
			r.touch(TouchType.UP, 1, x, y, steps * 8 + 40)
		r.frame(GameLoop.FIXED_DT)
		steps += 1
	if phase == "paused":
		r.pause()


func _process(_dt: float) -> void:
	frames += 1
	if frames == 30:
		await RenderingServer.frame_post_draw
		var img := get_viewport().get_texture().get_image()
		img.save_png(out)
		print("saved %s phase %s score %d host size %s probe size %s anchors %s %s" % [out, GameRound.Phase.keys()[host.round.phase], host.game.score, host.size, size, host.anchor_right, host.anchor_bottom])
		get_tree().quit()
