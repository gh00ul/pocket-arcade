extends Control
## Paints a test texture with TexPaint and saves the window (non-headless run):
##   godot --path godot --resolution 540x960 res://tools/capture/paint_probe.tscn -- --out=<png>

var out := "user://paint_probe.png"
var tex: PaTexture
var live: PaTexture
var live_tp: TexPaint
var frames := 0


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
	Display.configure(get_window())
	tex = TexPaint.paint_texture(128, 160, 4, func(tp: TexPaint) -> void:
		tp.vgrad(0, 0, 128, 160, [Pal.NIGHT, Pal.VIOLET, Pal.PINK])
		tp.round(8, 8, 112, 40, 10, Pal.DEEP)
		tp.stroke_round(8, 8, 112, 40, 10, 2, Pal.CYAN)
		tp.glow_text("NEON", 64, 38, 26, Pal.WHITE, Pal.HOTPINK, 4, Fonts.display())
		tp.ball(30, 80, 18, Pal.GOLD)
		tp.star(70, 80, 16, Pal.YELLOW)
		tp.radial(106, 80, 18, 0xFFFFFFFF, 0x003DF5FF)
		tp.outlined_text("PLAY", 64, 125, 22, Pal.YELLOW, Pal.NAVY, 4, Fonts.display())
		tp.label("HI 1234 " + ArcadeFont.STAR, 64, 132, 6, Pal.CYAN, true, false, ArcadeFont.SHADOW)
		tp.grain(0.25, 3)
		tp.punch(10, 150, 6)
		tp.glow(3, Pal.LIME, func(g: TexPaint) -> void: g.ring(110, 150, 6, 2, Pal.LIME))
		tp.ring(110, 150, 6, 2, Pal.WHITE))
	live_tp = TexPaint.new(240, 180)
	live = PaTexture.new(240, 180)


func _process(_dt: float) -> void:
	frames += 1
	live_tp.clear(Pal.BLACK)
	live_tp.fill(0xFF000000)
	live_tp.circle(120 + 60 * sin(frames * 0.2), 90, 30, Pal.CYAN)
	live_tp.glow_text("HIGH SCORE", 120, 60, 22, Pal.YELLOW, Pal.ORANGE, 5, Fonts.display())
	live_tp.update(live)
	queue_redraw()
	if frames == 20:
		await RenderingServer.frame_post_draw
		get_viewport().get_texture().get_image().save_png(out)
		print("saved ", out, " tex image: ", tex.image != null, " pending ", PaintPump.pending())
		get_tree().quit()


func _draw() -> void:
	var g := tex.gpu()
	if g != null:
		draw_texture_rect(g, Rect2(10, 10, 256, 320), false)
	var l := live.gpu()
	if l != null:
		draw_texture_rect(l, Rect2(10, 340, 240, 180), false)
	ArcadeFont.draw_to(self, Transform2D.IDENTITY, "ARCADE FONT " + ArcadeFont.TOKEN + " 20 " + ArcadeFont.TICKET + " 450", 10, 540, 3.0, Pal.WHITE, 1.0, false, ArcadeFont.SHADOW)
	ArcadeFont.draw_to(self, Transform2D.IDENTITY, "TINY LABEL " + ArcadeFont.HEART + ArcadeFont.NOTE + ArcadeFont.LEFT + ArcadeFont.RIGHT, 10, 580, 3.0, Pal.LAVENDER, 1.0, true, 0)
	var ds := DrawScope.new(self, size)
	ds.draw_round_rect(PaBrush.vertical([Pal.PINK, Pal.PURPLE]), Vector2(10, 620), Vector2(200, 60), 16.0)
	ds.draw_circle(Pal.CYAN, 20, Vector2(250, 650), 1.0, PaStroke.new(4))
	ds.draw_arc(Pal.GOLD, -90, 270, false, Vector2(290, 620), Vector2(60, 60), 1.0, PaStroke.new(6, true))
