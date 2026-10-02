extends Control
## Capture tool for hoops, air hockey and the stacker (games-b; not part of the game): plays a
## machine with build-13's simulation bot (the good one, from its tests), fast-forwarding
## --seconds in fixed steps, then keeps playing live for --live frames (one step a frame, as the
## host does at 120 Hz) and saves a PNG laid out like game_probe.tscn. Non-headless:
##   godot --path godot --resolution 1080x2400 res://tools/capture/games_b_probe.tscn --
##       --game=hoops|airhockey|stacker [--seconds=6] [--live=20] [--seed=97] [--calm]
##       [--attract] [--timing] [--out=C:/path/shot.png] [--hdr|--ldr]
## --calm turns screen shake off (reduce motion: the scenes calm their flashes). --attract draws
## the machine's attract screen (24 × 18 art pixels for the hall cabinets) instead of a round.

var game: MiniGame
var bot := Callable()
var seconds := 6.0
var live := 20
var seed_value := 97
var out := "user://games_b_probe.png"
var attract := false
## --timing: average microseconds per simulation step and per drawn frame over the live frames.
var timing := false
var _step_us := 0
var _steps_timed := 0
var _draw_us := 0
var _draws_timed := 0
var frames := 0
var time_left := 0.0
var gx := 0.0
var gy := 0.0
var gs := 1.0
var _gfx_layer: Control
var _field: Control
var _t := 0.0


func _ready() -> void:
	var game_id := "hoops"
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--game="):
			game_id = a.substr(7)
		elif a.begins_with("--seconds="):
			seconds = a.substr(10).to_float()
		elif a.begins_with("--live="):
			live = a.substr(7).to_int()
		elif a.begins_with("--seed="):
			seed_value = a.substr(7).to_int()
		elif a.begins_with("--out="):
			out = a.substr(6)
		elif a == "--calm":
			ScreenShake.intensity = 0.0
		elif a == "--attract":
			attract = true
		elif a == "--timing":
			timing = true
		elif a == "--hdr":
			GfxQuality.rung = 0
		elif a == "--ldr":
			GfxQuality.rung = 2
	Display.configure(get_window())
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_gfx_layer = Control.new()
	_gfx_layer.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_gfx_layer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_gfx_layer)
	_field = Control.new()
	_field.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_field.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_field.draw.connect(_draw_field)
	add_child(_field)
	Gfx.host = _gfx_layer
	game = GameRegistry.create(game_id)
	if game == null:
		push_error("no game '%s'" % game_id)
		get_tree().quit(1)
		return
	(game as BaseMiniGame).fixed_seed = seed_value
	game.start(SimHarness.fx())
	if game is HoopsGame:
		bot = load("res://tests/games/hoops/hoops_simulation_test.gd").bot(game, KRandom.new(9), 0.04, 40.0)
	elif game is AirHockeyGame:
		bot = load("res://tests/games/airhockey/air_hockey_simulation_test.gd").bot(game, KRandom.new(11), 0.05, 3.0)
	elif game is StackerGame:
		bot = load("res://tests/games/stacker/stacker_simulation_test.gd").bot(game, KRandom.new(13), 8.0)
	time_left = game.round_seconds
	for i in int(seconds / GameLoop.FIXED_DT):
		_step()


func _step() -> void:
	if bot.is_valid() and not game.finished():
		bot.call(_t, int(_t * 1000.0))
	time_left = maxf(time_left - GameLoop.FIXED_DT, 0.0)
	game.update(GameLoop.FIXED_DT, time_left)
	_t += GameLoop.FIXED_DT


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


func _process(_dt: float) -> void:
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
	if not attract:
		var t0 := Time.get_ticks_usec()
		_step()
		if timing:
			_step_us += Time.get_ticks_usec() - t0
			_steps_timed += 1
	queue_redraw()
	_field.queue_redraw()
	frames += 1
	if frames == maxi(live, 12):
		await RenderingServer.frame_post_draw
		var img := get_viewport().get_texture().get_image()
		img.save_png(out)
		var s := Gfx.slot(GameViewport.SLOT)
		var calls := "no 3D slot"
		if s != null:
			calls = "planned %d measured %d" % [s.planned_draw_calls(), s.measured_draw_calls()]
		print("saved %s %s t=%.2f score %d draw calls %s" % [out, img.get_size(), _t, game.score, calls])
		if timing and _steps_timed > 0 and _draws_timed > 0:
			print("timing: %.1f us per step, %.1f us per frame drawn (%d frames)" % [float(_step_us) / _steps_timed, float(_draw_us) / _draws_timed, _draws_timed])
		get_tree().quit()


func _draw() -> void:
	if game == null:
		return
	var look := game.look
	draw_rect(Rect2(Vector2.ZERO, size), Pal.c(Pal.shade(look.body, 0.28)))


func _draw_field() -> void:
	if game == null:
		return
	var ds := DrawScope.new(_field, size)
	ds.push()
	ds.clip_rect(gx, gy, gx + MiniGame.GAME_W * gs, gy + MiniGame.GAME_H * gs)
	ds.translate(gx, gy)
	ds.scale_by(gs, gs, Vector2.ZERO)
	if attract:
		_draw_attract(ds)
	else:
		var t0 := Time.get_ticks_usec()
		game.draw(ds)
		if timing and frames > 3:
			_draw_us += Time.get_ticks_usec() - t0
			_draws_timed += 1
		GameViewport.take_punch()
	ds.pop()


## A contact sheet of the attract loop: the screen painted at eight moments of its cycle with a
## TexPaint-backed painter at the hall's scale (10 texels a pixel; 24 × 18 pixels, 16 × 24 for the
## tower cabinet, as hub/MachineArt.kt screenUnits), two to a row.
const ATTRACT_TIMES := [0.2, 0.9, 1.6, 2.3, 3.0, 4.2, 5.8, 6.4]
var _attract_texs: Array[PaTexture] = []
var _attract_tps: Array[TexPaint] = []


func _draw_attract(ds: DrawScope) -> void:
	var w := 16 if game is StackerGame else 24
	var h := 24 if game is StackerGame else 18
	var scale := 10
	var base := 9.6 * 3.0 if game is StackerGame else (3.4 * 3.0 if game is HoopsGame else 7.0 * 3.0)
	if _attract_tps.is_empty():
		for i in ATTRACT_TIMES.size():
			var tp := TexPaint.new(w * scale, h * scale)
			tp.fill(0xFF000000)
			game.draw_attract(_TexPainter.new(tp, float(scale)), w, h, base + ATTRACT_TIMES[i])
			var tex := PaTexture.new(w * scale, h * scale)
			tp.update(tex)
			_attract_tps.append(tp)
			_attract_texs.append(tex)
	var cell_w := MiniGame.GAME_W / 2.0
	var cell_h := cell_w * h / w
	for i in _attract_texs.size():
		var g := _attract_texs[i].gpu()
		if g != null:
			ds.draw_image(g, Vector2((i % 2) * cell_w + 2.0, (i / 2) * (cell_h + 4.0) + 2.0), Vector2(cell_w - 4.0, cell_h))


## Kotlin's CanvasPainter (engine/r3d/TexPaint.kt), for this tool only: attract drawings in art
## pixels painted as clean shapes and real text at [member scale] texels per pixel.
class _TexPainter:
	extends Painter
	var tp: TexPaint
	var scale: float

	func _init(p_tp: TexPaint, p_scale: float) -> void:
		tp = p_tp
		scale = p_scale

	static func _with_alpha(c: Variant, alpha: float) -> int:
		var argb := Pal.to_argb(c) if c is Color else int(c) & 0xFFFFFFFF
		var a := clampi(int(((argb >> 24) & 0xFF) * alpha), 0, 255)
		return (a << 24) | (argb & 0xFFFFFF)

	func fill(x: float, y: float, w: float, h: float, color: Variant, alpha: float = 1.0) -> void:
		tp.rect(x * scale, y * scale, w * scale, h * scale, _with_alpha(color, alpha))

	func disc(cx: float, cy: float, r: float, color: Variant, alpha: float = 1.0) -> void:
		tp.circle(cx * scale, cy * scale, r * scale, _with_alpha(color, alpha))

	func frame(x: float, y: float, w: float, h: float, color: Variant, alpha: float = 1.0) -> void:
		var s := scale * 0.6
		var p := tp.paint.reset()
		p.style = PaPaint.Style.STROKE
		p.stroke_width = s
		p.color = _with_alpha(color, alpha)
		tp.c_draw_rect(x * scale + s / 2.0, y * scale + s / 2.0, (x + w) * scale - s / 2.0, (y + h) * scale - s / 2.0, p)

	func text(s: String, x: float, y: float, color: Variant, tiny: bool = false, alpha: float = 1.0, size: float = 1.0) -> void:
		var px := (6.2 if tiny else 8.5) * size * scale
		tp.text(s, x * scale, y * scale + px * 0.78, px, _with_alpha(color, alpha), Fonts.heavy(), TexPaint.ALIGN_LEFT)

	func text_centered(s: String, cx: float, y: float, color: Variant, tiny: bool = false, alpha: float = 1.0, size: float = 1.0) -> void:
		var px := (6.2 if tiny else 8.5) * size * scale
		tp.text(s, cx * scale, y * scale + px * 0.78, px, _with_alpha(color, alpha), Fonts.heavy(), TexPaint.ALIGN_CENTER)
