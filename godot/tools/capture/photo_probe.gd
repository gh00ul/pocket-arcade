extends Control
## The photo booth's pictures, for comparing with build-13 (non-headless):
##   godot --path godot --resolution 1080x1920 res://tools/capture/photo_probe.tscn -- --what=shots|strip
##       [--name="POCKET ARCADE"] [--date="OCT 1 2026"] [--stand-ins] [--out=C:/path/out.png]
##       [--strip=C:/path/strip.png]
## --what=shots photographs the player's kid in the booth's four poses (PhotoBoothStudio) and saves
## them side by side. --what=strip composes a strip from those four (or, with --stand-ins, from
## numbered discs on the booth's backdrop), waits for its pixels, saves the strip's own PNG (as
## the booth saves it) and the window with the strip drawn at its own size. Not part of the game.

var out := "user://photo_probe.png"
var strip_out := "user://photo_probe_strip.png"
var what := "strip"
var arcade_name := "POCKET ARCADE"
var date := "OCT 1 2026"
var stand_ins := false
var strip: PaTexture
var shots: Array = [null, null, null, null]
var frames := 0
var _saved := false


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
		elif a.begins_with("--strip="):
			strip_out = a.substr(8)
		elif a.begins_with("--what="):
			what = a.substr(7)
		elif a.begins_with("--name="):
			arcade_name = a.substr(7)
		elif a.begins_with("--date="):
			date = a.substr(7)
		elif a == "--stand-ins":
			stand_ins = true
	set_anchors_preset(Control.PRESET_FULL_RECT)
	if stand_ins:
		for i in PhotoStrip.SHOTS:
			shots[i] = _stand_in(i)
	else:
		var look := PhotoBoothStudio.player_look(SaveState.new())
		for i in PhotoStrip.SHOTS:
			PhotoBoothStudio.request(look, i, PhotoStrip.FRAME, func(t: Texture2D) -> void: shots[i] = t)


## A stand-in shot: the booth's backdrop (0xFF7A2A96 to 0xFF1E0E3A) with a numbered disc.
func _stand_in(i: int) -> PaTexture:
	return TexPaint.paint_texture(PhotoStrip.FRAME, PhotoStrip.FRAME, 1, func(tp: TexPaint) -> void:
		tp.vgrad(0, 0, PhotoStrip.FRAME, PhotoStrip.FRAME, [0xFF7A2A96, 0xFF1E0E3A])
		tp.ball(180, 170, 70, [Pal.RED, Pal.GREEN, Pal.SKY, Pal.ORANGE][i])
		tp.label(str(i + 1), 180, 140, 60, Pal.WHITE, true, false, ArcadeFont.SHADOW))


func _shot_texture(i: int) -> Texture2D:
	var s: Variant = shots[i]
	if s is PaTexture:
		return (s as PaTexture).gpu() if (s as PaTexture).image != null else null
	return s


func _all_shots() -> bool:
	for i in shots.size():
		if _shot_texture(i) == null:
			return false
	return true


func _process(_dt: float) -> void:
	frames += 1
	queue_redraw()
	if frames > 900:
		push_error("the pictures never arrived")
		get_tree().quit(1)
		return
	if not _all_shots():
		return
	if what == "shots":
		var sheet := Image.create_empty(PhotoStrip.FRAME * 4, PhotoStrip.FRAME, false, Image.FORMAT_RGBA8)
		for i in 4:
			var img := _shot_texture(i).get_image()
			img.convert(Image.FORMAT_RGBA8)
			sheet.blit_rect(img, Rect2i(Vector2i.ZERO, img.get_size()), Vector2i(i * PhotoStrip.FRAME, 0))
		sheet.save_png(out)
		print("saved ", out)
		get_tree().quit()
		return
	if strip == null:
		var tex: Array = []
		for i in shots.size():
			tex.append(_shot_texture(i))
		strip = PhotoStripArt.compose(tex, arcade_name, date)
		frames = 0
		return
	if strip.image != null and not _saved:
		_saved = true
		strip.image.save_png(strip_out)
		print("strip ", strip.image.get_size(), " saved ", strip_out)
	if _saved and frames > 20:
		await RenderingServer.frame_post_draw
		get_viewport().get_texture().get_image().save_png(out)
		print("saved ", out)
		get_tree().quit()


func _draw() -> void:
	draw_rect(Rect2(Vector2.ZERO, size), Pal.c(Pal.NIGHT))
	if strip != null:
		var g := strip.gpu()
		if g != null:
			# At its own size (one strip pixel to one window pixel), from the top left.
			draw_texture_rect(g, Rect2(20, 20, strip.width, strip.height), false)
	for i in shots.size():
		var t := _shot_texture(i)
		if t != null:
			draw_texture_rect(t, Rect2(480, 20 + i * 380, PhotoStrip.FRAME, PhotoStrip.FRAME), false)
