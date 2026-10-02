extends Control
## Paints the hall's textures (hub/HallArt, MachineKit, CabinetPaint, MachineArt) and lays them
## out in a grid, then saves a PNG (non-headless). Not part of the game:
##   godot --path godot --resolution 1080x2400 res://tools/capture/hall_art_probe.tscn -- --out=C:/path/art.png [--page=1]

var out := "user://hall_art_probe.png"
var page := 0
var frames := 0
var items: Array = []
var screen: LiveScreen
var art: MachineArt


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
		elif a.begins_with("--page="):
			page = a.substr(7).to_int()
	pass
	set_anchors_preset(Control.PRESET_FULL_RECT)
	var mat := CanvasItemMaterial.new()
	mat.blend_mode = CanvasItemMaterial.BLEND_MODE_PREMULT_ALPHA
	material = mat
	var games := []
	for e: Array in HallGames.BUILD_13:
		games.append(HallGames.stand_in(e))
	if page == 0:
		art = MachineArt.new(games[0])
		items = [
			["marquee", art.marquee()], ["topper", art.topper()], ["side tall", art.side_art()],
			["side square", art.side_art_square()], ["side long", art.side_art_long()], ["kick", art.kick()],
			["panel", art.panel()], ["panel big", art.panel_big()], ["bezel", art.bezel()],
			["coin door", MachineKit.coin_door()], ["slot", MachineKit.coin_slot_lit()], ["speaker", MachineKit.speaker_strip()],
			["ticket plate", MachineKit.ticket_plate()], ["ticket", MachineKit.ticket_paper()], ["prize chute", MachineKit.prize_chute()],
			["skee rings", MachineKit.skee_rings()], ["hockey", MachineKit.hockey_surface()], ["whack top", MachineKit.whack_top(Pal.GREEN)],
			["rear tall", MachineKit.rear_panel(true)], ["rear wide", MachineKit.rear_panel(false)], ["court", MachineKit.court()],
			["hoop board", MachineKit.hoop_board()], ["mole face", MachineKit.mole_face()], ["coin face", MachineKit.coin_face()],
		]
		screen = LiveScreen.new(art, 0)
	elif page == 1:
		items = [
			["carpet", HallArt.carpet()], ["tiles", HallArt.tiles()], ["concrete", HallArt.concrete()],
			["floor logo", HallArt.floor_logo()], ["mat", HallArt.mat()], ["wall", HallArt.wall()],
			["upper wall", HallArt.upper_wall()], ["mural", HallArt.mural()], ["mural city", HallArt.mural_city()],
			["mural space", HallArt.mural_space()], ["race sign", HallArt.race_sign()], ["ceiling", HallArt.ceiling_tiles()],
			["troffer", HallArt.troffer()], ["duct", HallArt.duct()], ["street", HallArt.street_backdrop()],
			["poster 0", HallArt.poster(0)], ["poster 1", HallArt.poster(1)], ["poster 2", HallArt.poster(2)], ["poster 3", HallArt.poster(3)],
			["neon", HallArt.neon("JACKPOT", 0xFFFFB03D, 512, 128, 104.0)], ["lightbox", HallArt.lightbox("TOKENS", Pal.GOLD, Pal.NIGHT)],
			["prize box", HallArt.prize_box(2)], ["glass", HallArt.glass()], ["wood", HallArt.wood()],
		]
	else:
		for g: MiniGame in games:
			var a := MachineArt.new(g)
			items.append([g.id, a.marquee()])
			items.append([g.id + " side", a.side_art()])


func _process(_dt: float) -> void:
	frames += 1
	if screen != null:
		screen.paint(4321, frames * 0.05)
		art.update_display(4321, 1.0)
	queue_redraw()
	if frames == 24:
		await RenderingServer.frame_post_draw
		get_viewport().get_texture().get_image().save_png(out)
		print("saved ", out, " pending ", PaintPump.pending())
		get_tree().quit()


func _draw() -> void:
	draw_rect(Rect2(Vector2.ZERO, size), Color(0.2, 0.2, 0.22))
	var x := 10.0
	var y := 10.0
	var row_h := 0.0
	var cols_w := size.x - 20.0
	for e: Array in items:
		var t: PaTexture = e[1]
		var g := t.gpu()
		var w := 250.0
		var h := w * t.height / float(t.width)
		if h > 300.0:
			h = 300.0
			w = h * t.width / float(t.height)
		if x + w > cols_w:
			x = 10.0
			y += row_h + 30.0
			row_h = 0.0
		if g != null:
			draw_texture_rect(g, Rect2(x, y, w, h), false)
		draw_string(Fonts.body(), Vector2(x, y + h + 18), e[0], HORIZONTAL_ALIGNMENT_LEFT, -1, 16)
		x += w + 12.0
		row_h = maxf(row_h, h)
	if screen != null:
		var s := screen.texture.gpu()
		if s != null:
			draw_texture_rect(s, Rect2(10, size.y - 400, 480, 360), false)
		var d := art.display().gpu()
		if d != null:
			draw_texture_rect(d, Rect2(520, size.y - 400, 512, 128), false)
