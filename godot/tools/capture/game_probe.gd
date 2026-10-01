extends Control
## Plays a machine with a scripted bot and saves a PNG, laid out the way the game host lays out
## its field (bar on top, field scaled to fit, bezel round it). Non-headless:
##   godot --path godot --resolution 1080x2400 res://tools/capture/game_probe.tscn -- --game=claw
##       [--seconds=3] [--seed=1] [--taps] [--out=C:/path/shot.png] [--hdr|--ldr]
## --seconds of round are fast-forwarded in fixed steps (with --taps, a tap somewhere in the field
## every 0.3 s), then a few frames are drawn live and captured. Not part of the game.

var game: MiniGame
var seconds := 3.0
var seed_value := 1
var taps := false
var out := "user://game_probe.png"
var frames := 0
var time_left := 0.0
var gx := 0.0
var gy := 0.0
var gs := 1.0
var _next_tap := 0.3
## The 3D pictures go in their own layer, under the field's 2D drawing (as in the app).
var _gfx_layer: Control
var _field: Control
var _rng := KRandom.new(5)
var _t := 0.0


func _ready() -> void:
	var game_id := "claw"
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--game="):
			game_id = a.substr(7)
		elif a.begins_with("--seconds="):
			seconds = a.substr(10).to_float()
		elif a.begins_with("--seed="):
			seed_value = a.substr(7).to_int()
		elif a == "--taps":
			taps = true
		elif a.begins_with("--out="):
			out = a.substr(6)
		elif a == "--hdr":
			GfxQuality.rung = 0
		elif a == "--ldr":
			GfxQuality.rung = 2
	Display.configure(get_window())
	set_anchors_preset(Control.PRESET_FULL_RECT)
	_gfx_layer = Control.new()
	_gfx_layer.set_anchors_preset(Control.PRESET_FULL_RECT)
	_gfx_layer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_gfx_layer)
	_field = Control.new()
	_field.set_anchors_preset(Control.PRESET_FULL_RECT)
	_field.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_field.draw.connect(_draw_field)
	add_child(_field)
	Gfx.host = _gfx_layer
	# A registered id, or a script path (res://...) for a game that isn't registered yet.
	game = load(game_id).new() as MiniGame if game_id.begins_with("res://") else GameRegistry.create(game_id)
	if game == null:
		push_error("no game '%s' (is its script in place?)" % game_id)
		get_tree().quit(1)
		return
	if game is BaseMiniGame:
		(game as BaseMiniGame).fixed_seed = seed_value
	game.start(SimHarness.fx() if ResourceLoader.exists("res://tests/games/sim_harness.gd") else GameFx.new())
	time_left = game.round_seconds
	# Fast-forward the round in the host's fixed steps.
	var steps := int(seconds / GameLoop.FIXED_DT)
	for i in steps:
		_bot()
		time_left = maxf(time_left - GameLoop.FIXED_DT, 0.0)
		game.update(GameLoop.FIXED_DT, time_left)
		_t += GameLoop.FIXED_DT


func _bot() -> void:
	if not taps or _t < _next_tap:
		return
	_next_tap = _t + 0.3
	var x := 30.0 + _rng.next_float() * (MiniGame.GAME_W - 60.0)
	var y := 120.0 + _rng.next_float() * (MiniGame.GAME_H - 200.0)
	var ms := int(_t * 1000.0)
	game.on_touch(TouchType.DOWN, 1, x, y, ms)
	game.on_touch(TouchType.UP, 1, x, y, ms + 40)


func _layout() -> void:
	# GameHostScreen.drawHost's layout, in dp.
	var w := size.x
	var h := size.y
	var insets := Display.insets_dp(get_window())
	var unit := maxf(floorf(2.0 * Display.density), 2.0) / Display.density
	var bar_h := insets.y + 42.0 * unit
	var avail_h := h - bar_h - insets.w - 6.0 * unit
	gs = minf(w / MiniGame.GAME_W, avail_h / MiniGame.GAME_H)
	gx = (w - MiniGame.GAME_W * gs) / 2.0
	gy = bar_h + maxf((avail_h - MiniGame.GAME_H * gs) / 2.0, 0.0)


func _process(dt: float) -> void:
	if game == null:
		return
	_layout()
	GameViewport.x = gx
	GameViewport.y = gy
	GameViewport.scale = gs
	GameViewport.clip_x0 = gx
	GameViewport.clip_y0 = gy
	GameViewport.clip_x1 = gx + MiniGame.GAME_W * gs
	GameViewport.clip_y1 = gy + MiniGame.GAME_H * gs
	time_left = maxf(time_left - dt, 0.0)
	game.update(GameLoop.FIXED_DT, time_left)
	queue_redraw()
	_field.queue_redraw()
	frames += 1
	if frames == 20:
		await RenderingServer.frame_post_draw
		var img := get_viewport().get_texture().get_image()
		img.save_png(out)
		var s := Gfx.slot(GameViewport.SLOT)
		var calls := "no 3D slot"
		if s != null:
			calls = "planned %d measured %d" % [s.planned_draw_calls(), s.measured_draw_calls()]
		print("saved %s %s score %d draw calls %s" % [out, img.get_size(), game.score, calls])
		get_tree().quit()


func _draw() -> void:
	if game == null:
		return
	var look := game.look
	var bezel := Pal.c(Pal.shade(look.body if look != null else Pal.DEEP, 0.28))
	draw_rect(Rect2(Vector2.ZERO, size), bezel)


func _draw_field() -> void:
	if game == null:
		return
	var ds := DrawScope.new(_field, size)
	ds.push()
	ds.clip_rect(gx, gy, gx + MiniGame.GAME_W * gs, gy + MiniGame.GAME_H * gs)
	ds.translate(gx, gy)
	ds.scale_by(gs, gs, Vector2.ZERO)
	game.draw(ds)
	GameViewport.take_punch()
	ds.pop()
