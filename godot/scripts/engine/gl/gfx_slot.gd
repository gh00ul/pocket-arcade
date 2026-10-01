class_name GfxSlot
extends Control
## One 3D picture on screen (engine/gl/GlRenderer.kt drawPass, for one slot): a SubViewport scene
## rebuilt from each [RenderPass] (the camera, a data texture of lights and globals, the light
## grid, the immediate polygons as mesh surfaces, every model part as a MultiMesh of its
## placements, see-through draws kept in recording order, the background bands and the
## particles), composited at the pass's rectangle on the window.
##
## Draw calls are kept down the way build-13 kept them down: one material per (texture, blend,
## culling) shared by everything that uses it, one mesh per model part shared by every slot, and
## one MultiMesh per model part for all its placements (bug 6 of 2.0.0: one material and one mesh
## per object).

const FAR := 9000.0
## Transparent draws are ordered by this much "depth" each (Godot sorts by depth + offset).
const ORDER_STEP := 100000.0

var slot_name := ""
var scene_vp: SubViewport
var picture: TextureRect
var cam: Camera3D
var environment: Environment
var _bg: MeshInstance3D
var _bg_mat: ShaderMaterial
var _opaque_mi: MeshInstance3D
var _opaque_mesh: ArrayMesh
var _opaque_mm := {}
var _ord_meshes: Array = []
var _ord_mms: Array = []
var _glow_layer: Node2D
var _particle_layer: Node2D
var _particle_mat: ShaderMaterial
var _glow_mat: ShaderMaterial
var _composite: ShaderMaterial
var _materials := {}
var _data_img: Image
var _data_tex: ImageTexture
var _grid_img: Image
var _grid_tex: ImageTexture
var _pass: RenderPass
var _hdr := false

## What the last apply drew (tests and draw-call budgets): opaque surfaces, MultiMeshes, ordered
## draws, background, particle layers.
var stats := {}


func _init() -> void:
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	scene_vp = SubViewport.new()
	scene_vp.name = "Scene"
	scene_vp.own_world_3d = true
	scene_vp.transparent_bg = false
	scene_vp.handle_input_locally = false
	scene_vp.gui_disable_input = true
	scene_vp.render_target_update_mode = SubViewport.UPDATE_ONCE
	scene_vp.size = Vector2i(16, 16)
	add_child(scene_vp)
	cam = Camera3D.new()
	cam.projection = Camera3D.PROJECTION_FRUSTUM
	cam.keep_aspect = Camera3D.KEEP_HEIGHT
	cam.current = true
	scene_vp.add_child(cam)
	environment = Environment.new()
	environment.background_mode = Environment.BG_COLOR
	environment.tonemap_mode = Environment.TONE_MAPPER_LINEAR
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_DISABLED
	environment.reflected_light_source = Environment.REFLECTION_SOURCE_DISABLED
	var we := WorldEnvironment.new()
	we.environment = environment
	scene_vp.add_child(we)
	_bg = MeshInstance3D.new()
	var q := QuadMesh.new()
	q.size = Vector2(1, 1)
	_bg.mesh = q
	_bg_mat = ShaderMaterial.new()
	_bg_mat.shader = load("res://shaders/background.gdshader")
	_bg.material_override = _bg_mat
	_bg.custom_aabb = AABB(Vector3(-1e6, -1e6, -1e6), Vector3(2e6, 2e6, 2e6))
	_bg.visible = false
	scene_vp.add_child(_bg)
	_opaque_mesh = ArrayMesh.new()
	_opaque_mi = MeshInstance3D.new()
	_opaque_mi.mesh = _opaque_mesh
	scene_vp.add_child(_opaque_mi)
	_glow_layer = _ParticleLayer.new()
	(_glow_layer as _ParticleLayer).glows = true
	_glow_mat = ShaderMaterial.new()
	_glow_mat.shader = load("res://shaders/particle_glow.gdshader")
	_glow_layer.material = _glow_mat
	scene_vp.add_child(_glow_layer)
	_particle_layer = _ParticleLayer.new()
	_particle_mat = ShaderMaterial.new()
	_particle_mat.shader = load("res://shaders/particle.gdshader")
	_particle_layer.material = _particle_mat
	scene_vp.add_child(_particle_layer)
	picture = TextureRect.new()
	picture.name = "Picture"
	picture.mouse_filter = Control.MOUSE_FILTER_IGNORE
	picture.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	picture.stretch_mode = TextureRect.STRETCH_SCALE
	picture.texture = scene_vp.get_texture()
	_composite = ShaderMaterial.new()
	_composite.shader = load("res://shaders/composite.gdshader")
	picture.material = _composite
	add_child(picture)
	_data_img = Image.create_empty(SceneShader.DATA_W, 3, false, Image.FORMAT_RGBAF)
	_data_tex = ImageTexture.create_from_image(_data_img)
	_grid_img = Image.create_empty(RenderPass.GRID_MAX_W * 2, RenderPass.GRID_MAX_H, false, Image.FORMAT_RGBA8)
	_grid_tex = ImageTexture.create_from_image(_grid_img)


## Draws the particle pool of the pass inside the picture (glows additive, under the particles).
class _ParticleLayer:
	extends Node2D
	var glows := false
	var pool: Particles = null

	func _draw() -> void:
		if pool == null:
			return
		if glows:
			pool.draw_glows(self, TexKit.glow().gpu())
		else:
			pool.draw_shapes(self)


## The material for one (texture, blend, culling, instanced) combination, shared by every draw.
func material_for(tex: PaTexture, blend: int, cull: bool, instanced: bool) -> ShaderMaterial:
	var key := "%d|%d|%d|%d" % [tex.id, blend, int(cull), int(instanced)]
	var m: ShaderMaterial = _materials.get(key)
	if m == null:
		m = ShaderMaterial.new()
		m.shader = SceneShader.get_shader(blend, cull, tex.smooth, tex.repeat, instanced)
		m.set_shader_parameter("data_tex", _data_tex)
		m.set_shader_parameter("grid_tex", _grid_tex)
		m.set_shader_parameter("env_tex", EnvMap.cubemap())
		m.set_shader_parameter("refl_tex", MeshKit.black())
		m.set_meta("gv", -1)
		_materials[key] = m
	var gpu := tex.gpu()
	if int(m.get_meta("gv")) != tex.gpu_version:
		m.set_shader_parameter("tex", gpu)
		m.set_meta("gv", tex.gpu_version)
	return m


func material_count() -> int:
	return _materials.size()


## Rebuilds the slot's scene from [param p] and asks for one render.
func apply(p: RenderPass) -> void:
	_pass = p
	var rung: GfxQuality.Rung = GfxQuality.LADDER[clampi(GfxQuality.rung, 0, GfxQuality.LADDER.size() - 1)]
	_hdr = rung.hdr
	_layout(p)
	_camera(p)
	_write_globals(p)
	_grid_img.set_data(RenderPass.GRID_MAX_W * 2, RenderPass.GRID_MAX_H, false, Image.FORMAT_RGBA8, p.grid)
	_grid_tex.update(_grid_img)
	_background(p)
	var opaque_surfaces := _opaque(p)
	var mms := _models(p)
	var ordered := _ordered(p)
	_particles(p)
	scene_vp.msaa_3d = _msaa(rung.msaa)
	_composite.set_shader_parameter("hdr", _hdr)
	_composite.set_shader_parameter("exposure", p.exposure)
	scene_vp.render_target_update_mode = SubViewport.UPDATE_ONCE
	stats = {
		"opaque_surfaces": opaque_surfaces, "multimeshes": mms, "ordered": ordered,
		"background": 1 if _bg.visible else 0, "particles": 2 if p.particles != null and p.particles.count > 0 else 0,
		"materials": _materials.size(), "polys": p.polys_drawn,
	}


## Draw calls the GPU makes for the last pass (an upper bound: Godot may cull some).
func planned_draw_calls() -> int:
	return int(stats.get("opaque_surfaces", 0)) + int(stats.get("multimeshes", 0)) + int(stats.get("ordered", 0)) \
		+ int(stats.get("background", 0)) + int(stats.get("particles", 0))


## Draw calls Godot actually made for this slot's picture last frame (0 when not rendering).
func measured_draw_calls() -> int:
	return RenderingServer.viewport_get_render_info(scene_vp.get_viewport_rid(), RenderingServer.VIEWPORT_RENDER_INFO_TYPE_VISIBLE, RenderingServer.VIEWPORT_RENDER_INFO_DRAW_CALLS_IN_FRAME)


static func _msaa(samples: int) -> Viewport.MSAA:
	if samples >= 4:
		return Viewport.MSAA_4X
	if samples >= 2:
		return Viewport.MSAA_2X
	return Viewport.MSAA_DISABLED


func _layout(p: RenderPass) -> void:
	var r := Rect2(p.vx, p.vy, p.vw, p.vh)
	if p.clip:
		var c := Rect2(p.cx0, p.cy0, p.cx1 - p.cx0, p.cy1 - p.cy0)
		position = c.position
		size = c.size
		clip_contents = true
		picture.position = r.position - c.position
	else:
		position = r.position
		size = r.size
		clip_contents = false
		picture.position = Vector2.ZERO
	picture.size = r.size
	var px := Vector2i(maxi(16, roundi(p.vw * Display.density * Gfx.render_scale)), maxi(16, roundi(p.vh * Display.density * Gfx.render_scale)))
	if scene_vp.size != px:
		scene_vp.size = px


func _camera(p: RenderPass) -> void:
	var c := p.cam
	cam.transform = c.to_transform()
	var near := clampf(p.near, 0.25, FAR * 0.5)
	var f: Array = c.frustum(near)
	cam.set_frustum(f[0], f[1], near, FAR)
	var clear := p.clear_color
	var col := Pal.c(clear)
	if _hdr:
		var k := 1.0 / maxf(p.exposure, 0.05)
		var lin := Vector3(HdrMath.inverse_aces(col.r), HdrMath.inverse_aces(col.g), HdrMath.inverse_aces(col.b)) * k
		var e := 1.0 / (1.0 + maxf(lin.x, maxf(lin.y, lin.z)))
		col = Color(lin.x * e, lin.y * e, lin.z * e, 1.0)
	environment.background_color = col


## The per-frame globals and the lights, in the data texture the scene shader reads.
func _write_globals(p: RenderPass) -> void:
	var img := _data_img
	var lights := p.lights
	for i in p.light_count:
		var o := i * 8
		img.set_pixel(i, 0, Color(lights[o], lights[o + 1], lights[o + 2], lights[o + 3]))
		img.set_pixel(i, 1, Color(lights[o + 4], lights[o + 5], lights[o + 6], lights[o + 7]))
	img.set_pixel(0, 2, Color(p.ambient.x, p.ambient.y, p.ambient.z, p.rim))
	img.set_pixel(1, 2, Color(p.dir_dir.x, p.dir_dir.y, p.dir_dir.z, p.floor_glow))
	img.set_pixel(2, 2, Color(p.dir_col.x, p.dir_col.y, p.dir_col.z, p.exposure))
	img.set_pixel(3, 2, Color(p.cam.ex, p.cam.ey, p.cam.ez, p.env_reflect))
	img.set_pixel(4, 2, Color(p.fog_near, p.fog_far, p.fog_floor, 1.0 if _hdr else 0.0))
	img.set_pixel(5, 2, Color(p.grid_x0, p.grid_z0, p.grid_inv_cell, 0.0))
	img.set_pixel(6, 2, Color(p.grid_w if p.light_count > 0 else 0, p.grid_h if p.light_count > 0 else 0, 0.0, 0.0))
	img.set_pixel(7, 2, Color(0.0, 0.0, 0.0, 0.0))
	_data_tex.update(img)


func _background(p: RenderPass) -> void:
	var n := mini(p.gradients.size(), 8)
	_bg.visible = n > 0
	if n == 0:
		return
	var tops := PackedVector4Array()
	var bottoms := PackedVector4Array()
	var spans := PackedVector2Array()
	for i in 8:
		if i < n:
			var g: Array = p.gradients[i]
			var t := Pal.c(int(g[0]))
			var b := Pal.c(int(g[1]))
			tops.append(Vector4(t.r, t.g, t.b, 1.0))
			bottoms.append(Vector4(b.r, b.g, b.b, 1.0))
			spans.append(Vector2(g[2], g[3]))
		else:
			tops.append(Vector4.ZERO)
			bottoms.append(Vector4.ZERO)
			spans.append(Vector2(-1.0, -1.0))
	_bg_mat.set_shader_parameter("bands", n)
	_bg_mat.set_shader_parameter("tops", tops)
	_bg_mat.set_shader_parameter("bottoms", bottoms)
	_bg_mat.set_shader_parameter("spans", spans)
	_bg_mat.set_shader_parameter("hdr", _hdr)
	_bg_mat.set_shader_parameter("exposure", p.exposure)
	_bg_mat.set_shader_parameter("far_depth", FAR * 0.9)


func _opaque(p: RenderPass) -> int:
	_opaque_mesh.clear_surfaces()
	var i := 0
	for b: RenderPass.Batch in p.opaque_batches:
		if b.vertex_count() == 0:
			continue
		_opaque_mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, MeshKit.batch_arrays(b), [], {}, MeshKit.CUSTOM0_FLOAT)
		_opaque_mesh.surface_set_material(i, material_for(b.tex, Blend.OPAQUE, false, false))
		i += 1
	return i


func _mm_node(key: String, pool: Dictionary) -> MultiMeshInstance3D:
	var mmi: MultiMeshInstance3D = pool.get(key)
	if mmi == null:
		mmi = MultiMeshInstance3D.new()
		var mm := MultiMesh.new()
		mm.transform_format = MultiMesh.TRANSFORM_3D
		mm.use_colors = true
		mm.use_custom_data = true
		mmi.multimesh = mm
		scene_vp.add_child(mmi)
		pool[key] = mmi
	return mmi


static func _fill(mm: MultiMesh, mesh: Mesh, data: PackedFloat32Array, count: int) -> void:
	if mm.mesh != mesh:
		mm.mesh = mesh
	if mm.instance_count != count:
		mm.instance_count = count
	mm.buffer = data


var _used := {}


func _models(p: RenderPass) -> int:
	_used.clear()
	var n := 0
	for run: RenderPass.ModelRun in p.opaque_runs:
		for part: Model.Part in run.model.parts():
			if part.blend != Blend.OPAQUE or part.mesh.get_surface_count() == 0:
				continue
			var key := "%d:%d" % [run.model.id, part.index]
			var mmi := _mm_node(key, _opaque_mm)
			_fill(mmi.multimesh, part.mesh, run.data, run.count)
			mmi.material_override = material_for(part.tex, Blend.OPAQUE, part.cull, true)
			mmi.visible = true
			_used[key] = true
			n += 1
	for key in _opaque_mm:
		if not _used.has(key):
			(_opaque_mm[key] as MultiMeshInstance3D).visible = false
	return n


func _ordered(p: RenderPass) -> int:
	var mesh_i := 0
	var mm_i := 0
	var order := 0
	for e in p.ordered:
		if e is RenderPass.Batch:
			var b: RenderPass.Batch = e
			if b.vertex_count() == 0:
				continue
			var mi: MeshInstance3D
			if mesh_i < _ord_meshes.size():
				mi = _ord_meshes[mesh_i]
			else:
				mi = MeshInstance3D.new()
				mi.mesh = ArrayMesh.new()
				scene_vp.add_child(mi)
				_ord_meshes.append(mi)
			mesh_i += 1
			var am: ArrayMesh = mi.mesh
			am.clear_surfaces()
			am.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, MeshKit.batch_arrays(b), [], {}, MeshKit.CUSTOM0_FLOAT)
			mi.material_override = material_for(b.tex, b.blend, false, false)
			mi.sorting_offset = order * ORDER_STEP
			mi.visible = true
			order += 1
		else:
			var run: RenderPass.ModelRun = e
			for part: Model.Part in run.model.parts():
				if part.blend != run.blend or part.mesh.get_surface_count() == 0:
					continue
				var mmi: MultiMeshInstance3D
				if mm_i < _ord_mms.size():
					mmi = _ord_mms[mm_i]
				else:
					mmi = MultiMeshInstance3D.new()
					var mm := MultiMesh.new()
					mm.transform_format = MultiMesh.TRANSFORM_3D
					mm.use_colors = true
					mm.use_custom_data = true
					mmi.multimesh = mm
					scene_vp.add_child(mmi)
					_ord_mms.append(mmi)
				mm_i += 1
				_fill(mmi.multimesh, part.mesh, run.data, run.count)
				mmi.material_override = material_for(part.tex, part.blend, part.cull, true)
				mmi.sorting_offset = order * ORDER_STEP
				mmi.visible = true
				order += 1
	for i in range(mesh_i, _ord_meshes.size()):
		(_ord_meshes[i] as MeshInstance3D).visible = false
	for i in range(mm_i, _ord_mms.size()):
		(_ord_mms[i] as MultiMeshInstance3D).visible = false
	return order


func _particles(p: RenderPass) -> void:
	var pool := p.particles
	var on := pool != null and pool.count > 0 and p.particle_field.x > 0.0
	for layer: _ParticleLayer in [_glow_layer, _particle_layer]:
		layer.pool = pool if on else null
		layer.visible = on
		if on:
			var k := Vector2(scene_vp.size) / p.particle_field
			layer.transform = Transform2D(0.0, k, 0.0, p.particle_offset * k)
			layer.queue_redraw()
	if on:
		for m: ShaderMaterial in [_glow_mat, _particle_mat]:
			m.set_shader_parameter("hdr", _hdr)
			m.set_shader_parameter("exposure", p.exposure)
