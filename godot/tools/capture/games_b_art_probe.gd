extends Control
## Capture tool for the games-b art (not part of the game): paints every texture of HoopsArt,
## HockeyArt and StackerArt (plus the live readouts and scoreboard) and lays them out on a
## checkerboard, labelled, then saves the window. Non-headless:
##   godot --path godot --resolution 1080x2400 res://tools/capture/games_b_art_probe.tscn --
##       --set=hoops|airhockey|stacker [--from=12] [--out=C:/path/art.png]

var out := "user://games_b_art_probe.png"
var which := "hoops"
## Index of the first item shown (the grid holds 12).
var first := 0
var frames := 0
var items: Array = []  # [name, PaTexture]
var _readout_a: HoopsArt.Readout
var _readout_b: HoopsArt.Readout
var _board: HockeyArt.Scoreboard


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
		elif a.begins_with("--set="):
			which = a.substr(6)
		elif a.begins_with("--from="):
			first = a.substr(7).to_int()
	Display.configure(get_window())
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	if which == "hoops":
		items = [
			["floor", HoopsArt.floor_tex()], ["apron", HoopsArt.apron()], ["crowd", HoopsArt.crowd()],
			["logo_core", HoopsArt.logo_core()], ["logo_halo", HoopsArt.logo_halo()], ["ad_board", HoopsArt.ad_board()],
			["steel", HoopsArt.steel()], ["pad", HoopsArt.pad()], ["board_face", HoopsArt.board_face()],
			["board_marks", HoopsArt.board_marks()], ["glint", HoopsArt.glint()], ["rim", HoopsArt.rim()],
			["cage_net", HoopsArt.cage_net()], ["ring", HoopsArt.ring()], ["flare", HoopsArt.flare()],
			["shaft", HoopsArt.shaft()],
		]
		_readout_a = HoopsArt.Readout.new("MAKES", Pal.ORANGE)
		_readout_a.paint(1, "12/15", Pal.YELLOW)
		_readout_b = HoopsArt.Readout.new("MULT", Pal.PINK)
		_readout_b.paint(2, "x5", Pal.ORANGE)
		items.append(["readout MAKES", _readout_a.tex()])
		items.append(["readout MULT", _readout_b.tex()])
		var ball := HoopsArt.ball(12.0)
		items.append(["ball skin", ball.polys[0].region.tex])
	elif which == "airhockey":
		items = [
			["surface", HockeyArt.surface(280, 540, 62.0)], ["rail", HockeyArt.rail()], ["body", HockeyArt.body()],
			["slot", HockeyArt.slot()], ["post", HockeyArt.post()], ["floor_tile", HockeyArt.floor_tile()],
			["wall", HockeyArt.wall()], ["mallet cyan", HockeyArt.mallet_skin(0xFF29C8E8)],
			["mallet pink", HockeyArt.mallet_skin(0xFFE8408C)], ["puck_side", HockeyArt.puck_side()],
			["puck_top", HockeyArt.puck_top()], ["ring", HockeyArt.ring()], ["flare", HockeyArt.flare()],
		]
		_board = HockeyArt.Scoreboard.new(HockeyTuning.GOALS_TO_WIN)
		_board.paint(3, 5)
		items.append(["scoreboard 3-5", _board.tex()])
	else:
		items = [
			["slab_side", StackerArt.slab_side()], ["city", StackerArt.city()], ["cloud", StackerArt.cloud()],
			["roof", StackerArt.roof()], ["steel", StackerArt.steel()], ["ring", StackerArt.ring()],
			["flare", StackerArt.flare()], ["shaft", StackerArt.shaft()], ["markers", StackerArt.marker(1).tex],
		]


func _process(_dt: float) -> void:
	if frames == 0 and first > 0:
		items = items.slice(first)
	frames += 1
	queue_redraw()
	if frames == 30:
		await RenderingServer.frame_post_draw
		get_viewport().get_texture().get_image().save_png(out)
		print("saved ", out, " pending ", PaintPump.pending())
		get_tree().quit()


func _draw() -> void:
	draw_rect(Rect2(Vector2.ZERO, size), Color(0.18, 0.18, 0.2))
	var cols := 4
	var cell_w := size.x / cols
	var cell_h := 330.0
	for i in items.size():
		var name: String = items[i][0]
		var t: PaTexture = items[i][1]
		var cx := (i % cols) * cell_w
		var cy := (i / cols) * cell_h
		# Checkerboard, so transparency shows.
		for yy in range(0, int(cell_h) - 40, 20):
			for xx in range(0, int(cell_w) - 10, 20):
				var dark := ((xx + yy) / 20) % 2 == 0
				draw_rect(Rect2(cx + 5 + xx, cy + 30 + yy, 20, 20), Color(0.3, 0.3, 0.32) if dark else Color(0.42, 0.42, 0.45))
		var g := t.gpu()
		if g != null:
			var aspect := float(t.width) / float(t.height)
			var w := cell_w - 10.0
			var h := w / aspect
			if h > cell_h - 40.0:
				h = cell_h - 40.0
				w = h * aspect
			draw_texture_rect(g, Rect2(cx + 5, cy + 30, w, h), false)
		draw_string(ThemeDB.fallback_font, Vector2(cx + 5, cy + 22), "%s %dx%d" % [name, t.width, t.height], HORIZONTAL_ALIGNMENT_LEFT, cell_w - 10, 16)
