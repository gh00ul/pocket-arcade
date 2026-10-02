class_name PhotoBoothStudio
extends RefCounted
## ui/PhotoBoothScreen.kt's studio: the kid in the booth's four poses, photographed by the GPU once
## each and kept for the shots, keyed by the look and the pose as build-13 keyed them
## ("booth:<look>:<i>"). Each picture is ui/Thumbs.kt's Studio (its clear colour, backdrop, light
## rig, fog, exposure and bloom) with the booth's own scene recorded over it (boothShot: the
## curtain's gradient, a camera at chest height, a warm key light and a violet one from behind, the
## stool for the seated shot, the kid, a soft shadow).
##
## build-13 had Gfx.snapshot render the recorded pass off screen at its own size, with the whole
## post chain, and read it back. Here the pass is drawn by the engine's own [GfxSlot] inside an
## off-screen SubViewport the size of the picture (the slot sized so its scene renders at exactly
## that many pixels whatever the screen density and render scale) and read back once it has been
## drawn. The textures the scene uses are painted by [TexPaint] a frame or two after they are first
## asked for, so a picture is taken only once the paint pump has delivered them. Until ui/Thumbs.kt
## is ported this stands in for Thumbs.request for the booth's pictures (same cache budget).

## Thumbs' memory budget for kept pictures (least recently used go first).
const BUDGET := 40 << 20

## ui/Thumbs.kt Studio: its clear colour, the backdrop's top colour.
const STUDIO_CLEAR := 0xFF120C22
const BACKDROP_TOP := 0xFF382965

## Frames to wait for the textures before taking a picture anyway, and frames a picture takes to
## be drawn before it is read back.
const PAINT_WAIT_FRAMES := 30
const DRAW_FRAMES := 2

static var _cache := {}
static var _order: Array[String] = []
static var _bytes := 0
static var _jobs: Array = []
static var _figures := {}
static var _stool: Model = null


## The player's kid for [param save]: the outfit's colours and the equipped hat (ui/Hud.kt's
## SaveState.playerLook(), which the HUD's port owns; repeated here until it is in).
static func player_look(save: SaveState) -> CharacterLook:
	var outfit := Catalog.outfit(save.outfit)
	var hat := Catalog.hat(save.hat)
	return Looks.player(outfit.shirt, outfit.pants, hat.hat if hat != null else Catalog.NONE)


## build-13's shotKey: one picture per look and pose.
static func shot_key(look: CharacterLook, i: int) -> String:
	return "booth:%s:%d" % [look.key(), i]


## The picture for [param key] if it has been developed (it becomes the most recently used).
static func cached(key: String) -> Texture2D:
	var t: Texture2D = _cache.get(key)
	if t != null:
		_order.erase(key)
		_order.append(key)
	return t


## Photographs [param look] in the booth's pose [param i] at [param px] pixels square unless it is
## kept or already on its way, and hands the picture to [param on_ready] (Callable(Texture2D)).
static func request(look: CharacterLook, i: int, px: int, on_ready: Callable) -> void:
	var key := shot_key(look, i)
	var t := cached(key)
	if t != null:
		on_ready.call(t)
		return
	for j: Shot in _jobs:
		if j.key == key:
			j.waiting.append(on_ready)
			return
	var tree := Engine.get_main_loop() as SceneTree
	if tree == null or tree.root == null:
		return
	var job := Shot.new()
	job.key = key
	job.look = look
	job.pose = PhotoBoothPlan.poses[i]
	job.px = px
	job.waiting.append(on_ready)
	_jobs.append(job)
	job.start(tree)


static func _keep(key: String, t: Texture2D) -> void:
	if _cache.has(key):
		var old: Texture2D = _cache[key]
		_bytes -= old.get_width() * old.get_height() * 4
		_order.erase(key)
	_cache[key] = t
	_order.append(key)
	_bytes += t.get_width() * t.get_height() * 4
	var k := 0
	while _bytes > BUDGET and k < _order.size():
		var victim: String = _order[k]
		if victim == key:
			k += 1
			continue
		var v: Texture2D = _cache[victim]
		_bytes -= v.get_width() * v.get_height() * 4
		_cache.erase(victim)
		_order.remove_at(k)


## Forgets every kept picture (tests).
static func clear_cache() -> void:
	_cache.clear()
	_order.clear()
	_bytes = 0


## Records the booth's picture of [param look] in [param shot] on [param r], [param w] × [param h]
## pixels: Studio's set-up, then boothShot.
static func record(r: Renderer3D, w: int, h: int, look: CharacterLook, shot: PhotoBoothPlan.PhotoPose) -> void:
	# Studio(r, w, h): a violet backdrop lit from above, a soft light rig, no fog, a little bloom.
	r.clear(STUDIO_CLEAR)
	r.gradient(BACKDROP_TOP, STUDIO_CLEAR)
	var l := r.lighting
	l.amb_r = 0.42
	l.amb_g = 0.4
	l.amb_b = 0.5
	l.set_direction(-0.45, 0.8, 0.7)
	l.dir_r = 0.75
	l.dir_g = 0.72
	l.dir_b = 0.68
	l.points.clear()
	r.fog_near = 5000.0
	r.fog_far = 9000.0
	r.exposure = 1.15
	r.bloom = 0.42
	# boothShot: in front of the booth's curtain, from the front at about the height of the chest,
	# with a warm key light and a violet light from behind.
	r.gradient(0xFF7A2A96, 0xFF1E0E3A)
	# A ball of this radius round the middle of the kid fills the picture: hats and raised arms fit.
	var centre := 28.0
	var radius := 34.0
	var fov_y := deg_to_rad(30.0)
	var half_min := minf(tan(fov_y / 2.0), tan(fov_y / 2.0) * w / float(h))
	var dist := radius / half_min * 1.08
	var pitch := deg_to_rad(7.0)
	r.camera.look_at(0.0, centre + sin(pitch) * dist, cos(pitch) * dist, 0.0, centre, 0.0, fov_y, w, h)
	l.points.append(PointLight.new(-radius * 1.5, centre + radius * 1.8, radius * 2.0, 1.0, 0.9, 0.8, radius * 6.0, 0.75))
	l.points.append(PointLight.new(radius * 1.2, centre + radius, -radius * 1.8, 0.7, 0.45, 1.0, radius * 5.0, 0.9))
	if shot.pose == Pose.SIT:
		stool().draw(r)
	figure(look).draw_still(r, 0.0, 0.0, 0.0, shot.yaw, shot.pose, 0.0, shot.time)
	r.flat(0.0, 0.0, 0.15, 20.0, 14.0, HallArt.shadow().full(), 0.0, Blend.ALPHA, 0.0, 0.5)


## The booth's kid for [param look] (one per look, as build-13's boothFigures).
static func figure(look: CharacterLook) -> Figure:
	var f: Figure = _figures.get(look.key())
	if f == null:
		f = Figure.new(look)
		_figures[look.key()] = f
	return f


## A round chrome stool with a red seat, its top at the height a sitting kid's hips are.
static func stool() -> Model:
	if _stool == null:
		var chrome := HallArt.chrome().full()
		var seat := HallArt.paint(0xFFE8323C).full()
		var b := ModelBuilder.new()
		b.cylinder(0.0, -1.0, 0.0, 0.8, 9.0, 20, chrome, chrome, 0.0, -1, null, false, NAN, true, 0.8)
		b.cylinder(0.0, -1.0, 0.8, 6.5, 1.0, 10, chrome, null, 0.0, -1, null, false, NAN, true, 0.9)
		b.cylinder(0.0, -1.0, 6.5, 9.0, 7.5, 20, seat, seat, 0.0, -1, null, false, NAN, true, 0.5)
		_stool = b.build()
	return _stool


## One picture on its way: records the scene (which paints any texture it hasn't got yet), waits
## for the paint pump to deliver them and records it again if it had to, has a GfxSlot draw it in an
## off-screen viewport, reads it back.
class Shot:
	extends RefCounted
	var key := ""
	var look: CharacterLook
	var pose: PhotoBoothPlan.PhotoPose
	var px := 360
	var waiting: Array[Callable] = []
	var _tree: SceneTree
	var _vp: SubViewport
	var _r: Renderer3D
	var _pass: RenderPass
	var _frames := 0
	var _drawn := 0

	func start(tree: SceneTree) -> void:
		_tree = tree
		_r = Renderer3D.new(px, px)
		_tree.process_frame.connect(_tick)
		_tick()

	func _tick() -> void:
		_frames += 1
		if _vp != null:
			_drawn += 1
			if _drawn > PhotoBoothStudio.DRAW_FRAMES:
				_develop()
			return
		var late := _frames > PhotoBoothStudio.PAINT_WAIT_FRAMES
		if PaintPump.pending() > 0 and not late:
			return
		_record()
		# Recording asks for every texture the scene needs; any it had to start painting are
		# waited for, then the scene is recorded again.
		if PaintPump.pending() > 0 and not late:
			return
		_shoot()

	func _record() -> void:
		# The slot renders its scene at size × density × render scale pixels: ask for exactly px.
		var k := maxf(Display.density * Gfx.render_scale, 0.01)
		var side := px / k
		_r.start_frame()
		_r.resize(px, px)
		PhotoBoothStudio.record(_r, px, px, look, pose)
		_pass = _r.finish_frame(0.0, 0.0, side, side)

	func _shoot() -> void:
		_vp = SubViewport.new()
		_vp.size = Vector2i(px, px)
		_vp.transparent_bg = false
		_vp.gui_disable_input = true
		_vp.render_target_update_mode = SubViewport.UPDATE_ALWAYS
		var slot := GfxSlot.new()
		_vp.add_child(slot)
		_tree.root.add_child(_vp)
		slot.apply(_pass)
		slot.scale = Vector2(px / _pass.vw, px / _pass.vh)

	func _develop() -> void:
		_tree.process_frame.disconnect(_tick)
		var img := _vp.get_texture().get_image()
		_vp.queue_free()
		PhotoBoothStudio._jobs.erase(self)
		if img == null or img.is_empty():
			return
		if img.get_format() != Image.FORMAT_RGBA8:
			img.convert(Image.FORMAT_RGBA8)
		var t := ImageTexture.create_from_image(img)
		PhotoBoothStudio._keep(key, t)
		for cb in waiting:
			if cb.is_valid():
				cb.call(t)
