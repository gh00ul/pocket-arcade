extends Control
## Renders a small test scene through the whole 3D pipeline and saves a PNG (non-headless run):
##   godot --path godot --resolution 540x960 res://tools/capture/render_probe.tscn -- --out=<png> [--hdr]

var stage: Stage3D
var model: Model
var particles := Particles.new(200)
var t := 0.0
var frames := 0
var out := "user://render_probe.png"


func _ready() -> void:
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--out="):
			out = a.substr(6)
		if a == "--hdr":
			GfxQuality.rung = 0
		if a == "--ldr":
			GfxQuality.rung = 2
	Display.configure(get_window())
	set_anchors_preset(Control.PRESET_FULL_RECT)
	Gfx.host = self
	stage = Stage3D.new(360, 640)
	var white := TexKit.white().full()
	var b := ModelBuilder.new()
	b.box(-120, 0, -120, 120, 18, 120, BoxFaces.all(white, 0.5), Pal.VIOLET)
	b.box(-60, 18, -60, 60, 36, 60, BoxFaces.all(white, 0.6), Pal.PINK)
	b.box(-62, 36, -62, 62, 38, 62, BoxFaces.new(white, white, white, white, white, 1.0, 1.0), Pal.CYAN)
	b.sphere(0, 70, 0, 24, white, 20, 12, 1.0, Pal.GOLD, 0.9)
	b.box(-400, -10, -400, 400, 0, 400, BoxFaces.new(null, null, null, white), Pal.DEEP)
	model = b.build()
	particles.confetti(0, 0, 360, 80)


func _process(dt: float) -> void:
	t += dt
	particles.update(dt)
	GameViewport.x = 0
	GameViewport.y = 0
	GameViewport.scale = size.x / 360.0
	GameViewport.clip_x0 = 0
	GameViewport.clip_y0 = 0
	GameViewport.clip_x1 = size.x
	GameViewport.clip_y1 = size.y
	GameViewport.particles = particles
	stage.look(260, 260, 420, 0, 30, 0, 44)
	var r := stage.begin()
	r.clear(Pal.NIGHT)
	r.gradient(0xFF07030F, 0xFF3A1650, 0, 400)
	var l := r.lighting
	l.amb_r = 0.3
	l.amb_g = 0.3
	l.amb_b = 0.4
	l.set_direction(0.5, 1, 0.3)
	l.dir_r = 0.5
	l.dir_g = 0.48
	l.dir_b = 0.45
	l.points.clear()
	l.points.append(PointLight.new(120, 80, 120, 1.0, 0.3, 0.6, 300, 1.2))
	l.points.append(PointLight.new(-150, 60, 40, 0.2, 0.8, 1.0, 260, 1.0))
	model.draw(r, -1, 1.0, Xform.new().set_xf(0, 0, 0, t * 0.5))
	r.sprite(0, 120, 0, 120, 120, TexKit.glow().full(), 0.0, Blend.ADD, 1.0, 0.8, 1.0, Pal.CYAN)
	r.billboard(150, 0, -50, 40, 80, TexKit.white().full(), false, 0.5, Blend.OPAQUE, 0.0, 1.0, 1.03, Pal.LIME)
	r.decal(-100, -100, 100, 100, 0.5, TexKit.shadow().full(), Blend.ALPHA, 0.0, 0.7)
	stage.present()
	frames += 1
	if frames == 30:
		await RenderingServer.frame_post_draw
		var img := get_viewport().get_texture().get_image()
		img.save_png(out)
		var s := Gfx.slot(GameViewport.SLOT)
		print("saved ", out, " size ", img.get_size(), " stats ", s.stats, " planned ", s.planned_draw_calls(), " measured ", s.measured_draw_calls())
		get_tree().quit()
