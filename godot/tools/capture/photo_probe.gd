extends Control
## The photo booth's pictures, for comparing with build-13 (non-headless):
##   godot --path godot --resolution 1080x2400 res://tools/capture/photo_probe.tscn -- --what=strip
##       [--name="POCKET ARCADE"] [--date="OCT 1 2026"] [--out=C:/path/window.png] [--strip=C:/path/strip.png]
## --what=strip composes a strip from four stand-in shots (the booth's backdrop with a numbered
## disc), waits for its pixels, saves the strip's own PNG (as the booth saves it) and the window
## with the strip drawn at its own size. Not part of the game.

var out := "user://photo_probe.png"
var strip_out := "user://photo_probe_strip.png"
var what := "strip"
var arcade_name := "POCKET ARCADE"
var date := "OCT 1 2026"
var strip: PaTexture
var shots: Array = []
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
	set_anchors_preset(Control.PRESET_FULL_RECT)
	for i in PhotoStrip.SHOTS:
		shots.append(_stand_in(i))


## A stand-in shot: the booth's backdrop (0xFF7A2A96 to 0xFF1E0E3A) with a numbered disc.
func _stand_in(i: int) -> PaTexture:
	return TexPaint.paint_texture(PhotoStrip.FRAME, PhotoStrip.FRAME, 1, func(tp: TexPaint) -> void:
		tp.vgrad(0, 0, PhotoStrip.FRAME, PhotoStrip.FRAME, [0xFF7A2A96, 0xFF1E0E3A])
		tp.ball(180, 170, 70, [Pal.RED, Pal.GREEN, Pal.SKY, Pal.ORANGE][i])
		tp.label(str(i + 1), 180, 140, 60, Pal.WHITE, true, false, ArcadeFont.SHADOW))


func _process(_dt: float) -> void:
	frames += 1
	queue_redraw()
	if strip == null:
		# The stand-in shots arrive first, then the strip is composed from their pictures.
		var ready := true
		for s: PaTexture in shots:
			ready = ready and s.image != null
		if ready or frames > 30:
			var tex: Array = []
			for s: PaTexture in shots:
				tex.append(s.gpu())
			strip = PhotoStripArt.compose(tex, arcade_name, date)
		return
	if strip.image != null and not _saved:
		_saved = true
		strip.image.save_png(strip_out)
		print("strip ", strip.image.get_size(), " saved ", strip_out)
	if _saved and frames > 60:
		await RenderingServer.frame_post_draw
		get_viewport().get_texture().get_image().save_png(out)
		print("saved ", out)
		get_tree().quit()
	if frames > 600:
		push_error("the strip never arrived")
		get_tree().quit(1)


func _draw() -> void:
	draw_rect(Rect2(Vector2.ZERO, size), Pal.c(Pal.NIGHT))
	if strip == null:
		return
	var g := strip.gpu()
	if g != null:
		# At its own size (one strip pixel to one window pixel), from the top left.
		draw_texture_rect(g, Rect2(20, 20, strip.width, strip.height), false)
	for i in shots.size():
		var s: PaTexture = shots[i]
		var sg := s.gpu()
		if sg != null:
			draw_texture_rect(sg, Rect2(480, 20 + i * 380, PhotoStrip.FRAME, PhotoStrip.FRAME), false)
