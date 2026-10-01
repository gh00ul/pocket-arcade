class_name Renderer3D
extends RefCounted
## engine/r3d/Renderer3D.kt: records a frame of 3D drawing. Scenes describe polygons (lit per pixel
## by the [member lighting] rig), sprites and whole [Model]s; [method finish_frame] packs them into a
## [RenderPass] that [Gfx] turns into Godot meshes and MultiMeshes, drawn with the ported scene
## shader, anti-aliasing and the post chain.
##
## Polygons have up to 8 vertices. Opaque polygons are grouped by texture; see-through ones are
## drawn in the order they were recorded, after everything opaque. Arguments are positional in
## Kotlin's declaration order (see docs/PORTING.md); `-1` tint means none, as in Kotlin.

const MAXV := 8

## Size of the camera image (the camera's cx, cy and focal length use it).
var width: int
var height: int

var camera := PaCamera3D.new()
var lighting := Lighting.new()

## Fog darkens surfaces from fog_near to fog_far (view depth), down to fog_floor brightness.
var fog_near := 1e8
var fog_far := 2e8
var fog_floor := 0.15

## Overall brightness before tone mapping, and how strongly bright things glow.
var exposure := 1.0
var bloom := 0.8
## Brightness (0..1, after tone mapping) above which things glow, in the LDR picture.
var bloom_threshold := Look.BLOOM_THRESHOLD
## The same for the HDR picture, in exposed linear light.
var bloom_threshold_hdr := Look.BLOOM_THRESHOLD_HDR
## How far glows spread (0 = a tight halo, 1 = wide and hazy).
var bloom_radius := Look.BLOOM_RADIUS
## Strength of the colour grade.
var grade := Look.GRADE
## Sharpening of the upscaled image (0 = off).
var sharpen := Look.SHARPEN
## Darkening towards the corners.
var vignette := Look.VIGNETTE
## Fresnel rim light on lit surfaces, tinted by the lights nearby (0 = off).
var rim := Look.RIM
## Blacklight: how much saturated colours on the floor glow.
var floor_glow := 0.0
## Strength of the reflections glossy surfaces pick up from the built-in room ([EnvMap]).
var env_reflect := Look.ENV_REFLECT
## Reflections of glowing things in glossy floors at y = 0 (streaks, or mirror images).
var floor_reflect := 0.0
## A faint blurred glow of the same reflections on matte floors (0 = off).
var floor_reflect_matte := 0.0
## Draw floor reflections as real mirror images (where the quality rung allows).
var floor_mirror := false

var polys_drawn := 0
## Model instances wholly beyond this view depth are skipped (0 = no limit).
var draw_distance := 0.0
## Skips model instances whose bounding sphere is wholly outside the camera's view.
var cull_models := false
## Model instances skipped by draw_distance or cull_models this frame.
var models_culled := 0

## Follow the frame-rate cap: start_frame may decide this frame is not wanted; recording then
## does nothing and finish_frame hands back a pass marked skipped. Off by default.
var frame_capped := false
## False while a frame the cap skipped is being "recorded".
var recording := true

var _gate := FrameGate.new()
## The clock the cap reads (microseconds); tests swap it.
var clock := func() -> int: return Time.get_ticks_usec()

var _pass := RenderPass.new()
var _frame_start := 0
var _started := false

# Opaque immediate geometry bucketed by texture (reused between frames).
var _buckets := {}
var _bucket_pool: Array = []
var _bucket_used := 0
# See-through batches and model runs in submission order.
var _ordered: Array = []
var _batch_pool: Array = []
var _batch_used := 0
var _run_pool: Array = []
var _run_used := 0
var _opaque_runs := {}
var _fog_used_near := 1e8
var _fog_used_far := 2e8
var _fog_used_floor := 0.0

# Polygon assembly.
var _n := 0
var _wx := PackedFloat32Array()
var _wy := PackedFloat32Array()
var _wz := PackedFloat32Array()
var _wu := PackedFloat32Array()
var _wv := PackedFloat32Array()
var _wnx := PackedFloat32Array()
var _wny := PackedFloat32Array()
var _wnz := PackedFloat32Array()
var _smooth_normals := false
var _region: Region = null
var _blend := Blend.OPAQUE
var _emissive := 0.0
var _alpha_k := 1.0
var _bias := 1.0
var _cull := true
var _gloss := 0.0
var _has_normal := false
var _nx := 0.0
var _ny := 0.0
var _nz := 1.0
var _tr := 1.0
var _tg := 1.0
var _tb := 1.0


func _init(w: int, h: int) -> void:
	width = maxi(w, 1)
	height = maxi(h, 1)
	# (Packed arrays placed in an Array are copies: each is resized by name.)
	_wx.resize(MAXV)
	_wy.resize(MAXV)
	_wz.resize(MAXV)
	_wu.resize(MAXV)
	_wv.resize(MAXV)
	_wnx.resize(MAXV)
	_wny.resize(MAXV)
	_wnz.resize(MAXV)


func resize(w: int, h: int) -> void:
	width = maxi(w, 1)
	height = maxi(h, 1)


## Starts recording a new frame (or skips it, if frame_capped and the cap says so).
func start_frame() -> void:
	var now: int = clock.call()
	recording = not frame_capped or _gate.due(now, GfxQuality.effective_cap(), GfxQuality.display_hz)
	if not recording:
		_started = false
		return
	_begin_pass(now)


func _begin_pass(now: int) -> void:
	_frame_start = now
	_started = true
	_pass.reset()
	_buckets.clear()
	_bucket_used = 0
	_ordered.clear()
	_batch_used = 0
	_run_used = 0
	_opaque_runs.clear()
	polys_drawn = 0
	models_culled = 0
	_fog_used_near = 1e8
	_fog_used_far = 2e8
	_fog_used_floor = 0.0


## The pass being recorded; drawing without start_frame starts one (and always records it).
func _current() -> RenderPass:
	if not _started:
		recording = true
		_begin_pass(clock.call())
	return _pass


## Clears the background to [param argb].
func clear(argb: int) -> void:
	if not recording:
		return
	var p := _current()
	p.clear_color = argb | 0xFF000000
	p.gradients.clear()


## Paints rows [y0, y1) of the background (camera image pixels) with a vertical gradient.
func gradient(top: int, bottom: int, y0: float = 0.0, y1: float = NAN) -> void:
	if not recording:
		return
	if is_nan(y1):
		y1 = float(height)
	_current().gradients.append([top, bottom, y0 / height, y1 / height])


# ------------------------------------------------------------------ polygon assembly

## Starts a polygon. [param emissive] > 0 ignores lighting and uses that brightness (1 = texture
## colour). [param depth_bias] > 1 pulls the polygon toward the camera for depth tests.
func begin(region: Region, blend: int = Blend.OPAQUE, emissive: float = 0.0, alpha: float = 1.0,
		depth_bias: float = 1.0, cull: bool = true, gloss: float = 0.0) -> void:
	_region = region
	_blend = blend
	_emissive = emissive
	_alpha_k = alpha
	_bias = depth_bias
	_cull = cull
	_gloss = gloss
	_n = 0
	_has_normal = false
	_smooth_normals = false
	_tr = 1.0
	_tg = 1.0
	_tb = 1.0


func normal(x: float, y: float, z: float) -> void:
	_nx = x
	_ny = y
	_nz = z
	_has_normal = true


## Kotlin `tint(r, g, b)`.
func tint_rgb(r: float, g: float, b: float) -> void:
	_tr = r
	_tg = g
	_tb = b


## Kotlin `tint(argb)`: -1 (white) leaves the polygon untinted.
func tint(argb: int) -> void:
	var c := argb & 0xFFFFFFFF
	if c == 0xFFFFFFFF:
		return
	_tr = ((c >> 16) & 255) / 255.0
	_tg = ((c >> 8) & 255) / 255.0
	_tb = (c & 255) / 255.0


## Adds a vertex: world position and texel coordinates inside the region.
func vertex(x: float, y: float, z: float, u: float, v: float) -> void:
	if _n >= MAXV:
		return
	_wx[_n] = x
	_wy[_n] = y
	_wz[_n] = z
	_wu[_n] = u
	_wv[_n] = v
	_n += 1


## Adds a vertex with its own normal, for smoothly shaded surfaces (Kotlin's 8-argument vertex).
func vertex_n(x: float, y: float, z: float, u: float, v: float, nx: float, ny: float, nz: float) -> void:
	if _n >= MAXV:
		return
	_wx[_n] = x
	_wy[_n] = y
	_wz[_n] = z
	_wu[_n] = u
	_wv[_n] = v
	_wnx[_n] = nx
	_wny[_n] = ny
	_wnz[_n] = nz
	_smooth_normals = true
	_n += 1


func end() -> void:
	if not recording:
		return
	var reg := _region
	if reg == null or _n < 3:
		return
	var cam := camera
	var n := _n
	if not _has_normal:
		var ax := _wx[2] - _wx[0]
		var ay := _wy[2] - _wy[0]
		var az := _wz[2] - _wz[0]
		var bx := _wx[1] - _wx[0]
		var by := _wy[1] - _wy[0]
		var bz := _wz[1] - _wz[0]
		var cxn := ay * bz - az * by
		var cyn := az * bx - ax * bz
		var czn := ax * by - ay * bx
		var l := maxf(sqrt(cxn * cxn + cyn * cyn + czn * czn), 1e-6)
		_nx = cxn / l
		_ny = cyn / l
		_nz = czn / l
	if _cull:
		var mx := 0.0
		var my := 0.0
		var mz := 0.0
		for i in n:
			mx += _wx[i]
			my += _wy[i]
			mz += _wz[i]
		mx /= n
		my /= n
		mz /= n
		if (mx - cam.ex) * _nx + (my - cam.ey) * _ny + (mz - cam.ez) * _nz >= 0.0:
			return
	# Quick reject when every vertex is behind the eye.
	var any_front := false
	for i in n:
		if cam.view_z(_wx[i], _wy[i], _wz[i]) > cam.near:
			any_front = true
			break
	if not any_front:
		return
	polys_drawn += 1
	var tex := reg.tex
	var b: RenderPass.Batch
	if _blend == Blend.OPAQUE:
		b = _buckets.get(tex.id)
		if b == null:
			b = _take_bucket(tex)
			_buckets[tex.id] = b
	else:
		# Merge with the previous see-through batch when the texture and blend match.
		var last: Variant = _ordered.back() if not _ordered.is_empty() else null
		if last is RenderPass.Batch and (last as RenderPass.Batch).tex == tex and (last as RenderPass.Batch).blend == _blend:
			b = last
		else:
			b = _take_batch(tex, _blend)
			_ordered.append(b)
	if _emissive > 0.0:
		b.glow = true
	var iw := 1.0 / tex.width
	var ih := 1.0 / tex.height
	var fog := 0.0
	if fog_near < 1e7:
		fog = 1.0
		_fog_used_near = fog_near
		_fog_used_far = fog_far
		_fog_used_floor = fog_floor
	var rx := float(reg.x)
	var ry := float(reg.y)
	var colr := Color(_tr, _tg, _tb, _alpha_k)
	var fn := Vector3(_nx, _ny, _nz)
	var pos := b.pos
	var nrm := b.nrm
	var uvs := b.uv
	var col := b.col
	var cus := b.cus
	for t in range(1, n - 1):
		for k in 3:
			var i := 0 if k == 0 else (t if k == 1 else t + 1)
			pos.append(Vector3(_wx[i], _wy[i], _wz[i]))
			if _smooth_normals:
				nrm.append(Vector3(_wnx[i], _wny[i], _wnz[i]))
			else:
				nrm.append(fn)
			uvs.append(Vector2((rx + _wu[i]) * iw, (ry + _wv[i]) * ih))
			col.append(colr)
			cus.append(_emissive)
			cus.append(_bias)
			cus.append(_gloss)
			cus.append(fog)


func _take_bucket(tex: PaTexture) -> RenderPass.Batch:
	var b: RenderPass.Batch
	if _bucket_used < _bucket_pool.size():
		b = _bucket_pool[_bucket_used]
	else:
		b = RenderPass.Batch.new()
		_bucket_pool.append(b)
	_bucket_used += 1
	b.reset(tex, Blend.OPAQUE)
	return b


func _take_batch(tex: PaTexture, blend: int) -> RenderPass.Batch:
	var b: RenderPass.Batch
	if _batch_used < _batch_pool.size():
		b = _batch_pool[_batch_used]
	else:
		b = RenderPass.Batch.new()
		_batch_pool.append(b)
	_batch_used += 1
	b.reset(tex, blend)
	return b


func _take_run(model: Model, blend: int) -> RenderPass.ModelRun:
	var r: RenderPass.ModelRun
	if _run_used < _run_pool.size():
		r = _run_pool[_run_used]
	else:
		r = RenderPass.ModelRun.new()
		_run_pool.append(r)
	_run_used += 1
	r.reset(model, blend)
	return r


# ------------------------------------------------------------------ models

## Draws [param model] (placed by [param xf]); [param only] limits it to one blend layer (-1: all).
func draw_model(model: Model, only: int = -1, emissive_boost: float = 1.0, xf: Xform = null, tint_argb: int = -1) -> void:
	if not recording:
		return
	_current()
	if (cull_models or draw_distance > 0.0) and not instance_in_view(model, xf):
		models_culled += 1
		return
	var want_opaque := model.has_opaque and (only == -1 or only == Blend.OPAQUE)
	var want_alpha := model.has_alpha and (only == -1 or only == Blend.ALPHA)
	var want_add := model.has_add and (only == -1 or only == Blend.ADD)
	if not want_opaque and not want_alpha and not want_add:
		return
	# Models always take fog, so a frame drawn only from models still needs its settings.
	if fog_near < 1e7:
		_fog_used_near = fog_near
		_fog_used_far = fog_far
		_fog_used_floor = fog_floor
	var t := tint_argb & 0xFFFFFFFF
	var tr := 1.0
	var tg := 1.0
	var tb := 1.0
	if t != 0xFFFFFFFF:
		tr = ((t >> 16) & 255) / 255.0
		tg = ((t >> 8) & 255) / 255.0
		tb = (t & 255) / 255.0
	var glows := model.glow_mask if emissive_boost > 0.0 else 0
	if want_opaque:
		var run: RenderPass.ModelRun = _opaque_runs.get(model.id)
		if run == null:
			run = _take_run(model, Blend.OPAQUE)
			_opaque_runs[model.id] = run
		_append_instance(run, xf, tr, tg, tb, emissive_boost)
		if glows & (1 << Blend.OPAQUE):
			run.glow = true
	if want_alpha:
		_ordered_instance(model, Blend.ALPHA, xf, tr, tg, tb, emissive_boost, (glows & (1 << Blend.ALPHA)) != 0)
	if want_add:
		_ordered_instance(model, Blend.ADD, xf, tr, tg, tb, emissive_boost, (glows & (1 << Blend.ADD)) != 0)
	polys_drawn += model.polys.size()


func _ordered_instance(model: Model, blend: int, xf: Xform, tr: float, tg: float, tb: float, boost: float, glow: bool) -> void:
	var last: Variant = _ordered.back() if not _ordered.is_empty() else null
	var run: RenderPass.ModelRun
	if last is RenderPass.ModelRun and (last as RenderPass.ModelRun).model == model and (last as RenderPass.ModelRun).blend == blend:
		run = last
	else:
		run = _take_run(model, blend)
		_ordered.append(run)
	_append_instance(run, xf, tr, tg, tb, boost)
	if glow:
		run.glow = true


static func _append_instance(run: RenderPass.ModelRun, xf: Xform, tr: float, tg: float, tb: float, boost: float) -> void:
	var d := run.data
	if xf != null:
		xf.append_to(d)
	else:
		d.append_array(PackedFloat32Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0]))
	# Instance colour (Godot multiplies the vertex colour by it), then custom data.
	d.append(tr)
	d.append(tg)
	d.append(tb)
	d.append(1.0)
	d.append(tr)
	d.append(tg)
	d.append(tb)
	d.append(boost)
	run.count += 1


## Whether a placed model's bounding sphere reaches the view (and lies within draw_distance).
func instance_in_view(model: Model, xf: Xform) -> bool:
	var cam := camera
	var bx := model.bound_x
	var by := model.bound_y
	var bz := model.bound_z
	var x := xf.x(bx, by, bz) if xf != null else bx
	var y := xf.y(bx, by, bz) if xf != null else by
	var z := xf.z(bx, by, bz) if xf != null else bz
	var r := model.bound_r * (xf.max_scale() if xf != null else 1.0) + 0.5
	var vz := cam.view_z(x, y, z)
	if vz + r < cam.near:
		return false
	if draw_distance > 0.0 and vz - r > draw_distance:
		return false
	if not cull_models:
		return true
	var vx := cam.view_x(x, y, z)
	var vy := cam.view_y(x, y, z)
	var f := cam.focal
	var right := (cam.image_w - cam.cx) / f
	var left := cam.cx / f
	var top := cam.cy / f
	var bottom := (cam.image_h - cam.cy) / f
	if (vx - right * vz) / sqrt(1.0 + right * right) > r:
		return false
	if (-vx - left * vz) / sqrt(1.0 + left * left) > r:
		return false
	if (vy - top * vz) / sqrt(1.0 + top * top) > r:
		return false
	if (-vy - bottom * vz) / sqrt(1.0 + bottom * bottom) > r:
		return false
	return true


# ------------------------------------------------------------------ finishing

## Packs the recorded frame into the [RenderPass] shown at ([param x], [param y], [param w],
## [param h]) on the window (logical units), optionally clipped to (clip_x0, clip_y0)–(clip_x1, clip_y1).
func finish_frame(x: float, y: float, w: float, h: float,
		clip_x0: float = 0.0, clip_y0: float = 0.0, clip_x1: float = 0.0, clip_y1: float = 0.0, clip: bool = false) -> RenderPass:
	if not recording:
		var skipped := RenderPass.new()
		skipped.skipped = true
		return skipped
	var p := _current()
	_started = false
	p.vx = x
	p.vy = y
	p.vw = maxf(w, 1.0)
	p.vh = maxf(h, 1.0)
	p.clip = clip
	p.cx0 = clip_x0
	p.cy0 = clip_y0
	p.cx1 = clip_x1
	p.cy1 = clip_y1
	_copy_camera(p.cam)
	p.near = camera.near
	p.fog_near = _fog_used_near
	p.fog_far = _fog_used_far
	p.fog_floor = _fog_used_floor
	p.exposure = exposure
	p.bloom = bloom
	p.bloom_threshold = bloom_threshold
	p.bloom_threshold_hdr = bloom_threshold_hdr
	p.bloom_radius = bloom_radius
	p.grade = grade
	p.sharpen = sharpen
	p.vignette = vignette
	p.rim = rim
	p.floor_glow = floor_glow
	p.env_reflect = env_reflect
	p.floor_reflect = floor_reflect
	p.floor_reflect_matte = floor_reflect_matte
	p.floor_mirror = floor_mirror
	_pack_lights(p)
	for i in _bucket_used:
		var b: RenderPass.Batch = _bucket_pool[i]
		if b.vertex_count() > 0:
			p.opaque_batches.append(b)
			if b.glow:
				p.glow_draws += 1
	for run in _opaque_runs.values():
		p.opaque_runs.append(run)
		if (run as RenderPass.ModelRun).glow:
			p.glow_draws += 1
	for e in _ordered:
		p.ordered.append(e)
		if e.glow:
			p.glow_draws += 1
	p.polys_drawn = polys_drawn
	p.models_culled = models_culled
	p.record_usec = Time.get_ticks_usec() - _frame_start
	return p


func _copy_camera(c: PaCamera3D) -> void:
	var s := camera
	c.ex = s.ex
	c.ey = s.ey
	c.ez = s.ez
	c.rx = s.rx
	c.ry = s.ry
	c.rz = s.rz
	c.ux = s.ux
	c.uy = s.uy
	c.uz = s.uz
	c.fx = s.fx
	c.fy = s.fy
	c.fz = s.fz
	c.focal = s.focal
	c.cx = s.cx
	c.cy = s.cy
	c.image_w = s.image_w
	c.image_h = s.image_h
	c.near = s.near


var _light_sel := PackedInt32Array()
var _light_weight := PackedFloat32Array()
var _cell_count := PackedInt32Array()
var _cell_weakest := PackedFloat32Array()


## Chooses the lights to pack, as indices into the rig's list, and returns how many. Up to the
## budget they are all taken in order; past it, a full budget keeps the first ones and a tighter
## one keeps the strongest (intensity × radius), still in the rig's order.
func _pick_lights(l: Lighting) -> int:
	var n := l.points.size()
	var budget := clampi(GfxQuality.light_budget, 1, RenderPass.MAX_LIGHTS)
	if _light_sel.size() < RenderPass.MAX_LIGHTS:
		_light_sel.resize(RenderPass.MAX_LIGHTS)
		_light_weight.resize(RenderPass.MAX_LIGHTS)
	var sel := _light_sel
	if n <= budget or budget >= RenderPass.MAX_LIGHTS:
		var count := mini(n, RenderPass.MAX_LIGHTS)
		for i in count:
			sel[i] = i
		return count
	var w := _light_weight
	var k := 0
	for i in n:
		var pl := l.points[i]
		var weight := pl.intensity * pl.radius
		if k == budget and weight <= w[k - 1]:
			continue
		var at: int
		if k < budget:
			at = k
			k += 1
		else:
			at = k - 1
		while at > 0 and w[at - 1] < weight:
			w[at] = w[at - 1]
			sel[at] = sel[at - 1]
			at -= 1
		w[at] = weight
		sel[at] = i
	# Back into the rig's order, so packing looks the same whichever lights were kept.
	for a in range(1, k):
		var v := sel[a]
		var bi := a - 1
		while bi >= 0 and sel[bi] > v:
			sel[bi + 1] = sel[bi]
			bi -= 1
		sel[bi + 1] = v
	return k


## Copies the lights and builds the grid telling each patch of floor which lights reach it.
func _pack_lights(p: RenderPass) -> void:
	var l := lighting
	p.ambient = Vector3(l.amb_r, l.amb_g, l.amb_b)
	p.dir_dir = Vector3(l.dir_x, l.dir_y, l.dir_z)
	p.dir_col = Vector3(l.dir_r, l.dir_g, l.dir_b)
	var count := _pick_lights(l)
	var sel := _light_sel
	p.light_count = count
	if count == 0:
		return
	var min_x := INF
	var max_x := -INF
	var min_z := INF
	var max_z := -INF
	var lights := p.lights
	for i in count:
		var pl := l.points[sel[i]]
		var o := i * 8
		lights[o] = pl.x
		lights[o + 1] = pl.y
		lights[o + 2] = pl.z
		lights[o + 3] = pl.radius
		lights[o + 4] = pl.r
		lights[o + 5] = pl.g
		lights[o + 6] = pl.b
		lights[o + 7] = pl.intensity
		min_x = minf(min_x, pl.x - pl.radius)
		max_x = maxf(max_x, pl.x + pl.radius)
		min_z = minf(min_z, pl.z - pl.radius)
		max_z = maxf(max_z, pl.z + pl.radius)
	var extent := maxf(max_x - min_x, max_z - min_z)
	var cell := maxf(24.0, extent / 48.0)
	var gw := clampi(ceili((max_x - min_x) / cell), 1, RenderPass.GRID_MAX_W)
	var gh := clampi(ceili((max_z - min_z) / cell), 1, RenderPass.GRID_MAX_H)
	p.grid_w = gw
	p.grid_h = gh
	p.grid_x0 = min_x
	p.grid_z0 = min_z
	p.grid_inv_cell = 1.0 / cell
	var grid := p.grid
	var row := RenderPass.GRID_MAX_W * 2 * 4
	# Clear only the part in use (rows of the fixed-size texture).
	for gz in gh:
		var base := gz * row
		for i in gw * 8:
			grid[base + i] = 0
	if _cell_count.size() < gw * gh:
		_cell_count.resize(RenderPass.GRID_MAX_W * RenderPass.GRID_MAX_H)
		_cell_weakest.resize(RenderPass.GRID_MAX_W * RenderPass.GRID_MAX_H)
	var cell_count := _cell_count
	var cell_weakest := _cell_weakest
	for i in gw * gh:
		cell_count[i] = 0
	for i in count:
		var pl := l.points[sel[i]]
		if pl.intensity <= 0.0:
			continue
		var weight := pl.intensity * pl.radius
		var cx0 := clampi(floori((pl.x - pl.radius - min_x) / cell), 0, gw - 1)
		var cx1 := clampi(floori((pl.x + pl.radius - min_x) / cell), 0, gw - 1)
		var cz0 := clampi(floori((pl.z - pl.radius - min_z) / cell), 0, gh - 1)
		var cz1 := clampi(floori((pl.z + pl.radius - min_z) / cell), 0, gh - 1)
		var r2 := pl.radius * pl.radius
		for gz in range(cz0, cz1 + 1):
			for gx in range(cx0, cx1 + 1):
				# Skip cells the light's circle doesn't reach.
				var nx := clampf(pl.x, min_x + gx * cell, min_x + (gx + 1) * cell)
				var nz := clampf(pl.z, min_z + gz * cell, min_z + (gz + 1) * cell)
				var dx := nx - pl.x
				var dz := nz - pl.z
				if dx * dx + dz * dz > r2:
					continue
				var ci := gz * gw + gx
				var k := cell_count[ci]
				var base := gz * row + gx * 8
				if k < RenderPass.CELL_LIGHTS:
					grid[base + k] = i + 1
					cell_count[ci] = k + 1
					if k == 0 or weight < cell_weakest[ci]:
						cell_weakest[ci] = weight
				elif weight > cell_weakest[ci]:
					# Replace the weakest light in a crowded cell.
					var weakest := 0
					var wv := INF
					for j in RenderPass.CELL_LIGHTS:
						var li := grid[base + j] - 1
						var lp := l.points[sel[li]]
						var lw := lp.intensity * lp.radius
						if lw < wv:
							wv = lw
							weakest = j
					grid[base + weakest] = i + 1
					var new_weakest := INF
					for j in RenderPass.CELL_LIGHTS:
						var li := grid[base + j] - 1
						var lp := l.points[sel[li]]
						new_weakest = minf(new_weakest, lp.intensity * lp.radius)
					cell_weakest[ci] = new_weakest


# ------------------------------------------------------------------ convenience shapes

func quad(ax: float, ay: float, az: float, bx: float, by: float, bz: float,
		cx: float, cy: float, cz: float, dx: float, dy: float, dz: float,
		region: Region, nx: float, ny: float, nz: float,
		u0: float = 0.0, v0: float = 0.0, u1: float = NAN, v1: float = NAN,
		blend: int = Blend.OPAQUE, emissive: float = 0.0, alpha: float = 1.0, cull: bool = true,
		tint_argb: int = -1, depth_bias: float = 1.0, gloss: float = 0.0) -> void:
	if not recording:
		return
	if is_nan(u1):
		u1 = float(region.w)
	if is_nan(v1):
		v1 = float(region.h)
	begin(region, blend, emissive, alpha, depth_bias, cull, gloss)
	normal(nx, ny, nz)
	tint(tint_argb)
	_wx[0] = ax; _wy[0] = ay; _wz[0] = az; _wu[0] = u0; _wv[0] = v0
	_wx[1] = bx; _wy[1] = by; _wz[1] = bz; _wu[1] = u1; _wv[1] = v0
	_wx[2] = cx; _wy[2] = cy; _wz[2] = cz; _wu[2] = u1; _wv[2] = v1
	_wx[3] = dx; _wy[3] = dy; _wz[3] = dz; _wu[3] = u0; _wv[3] = v1
	_n = 4
	end()


## A camera-facing sprite standing on (x, y, z) (bottom centre). [param lean] tilts it back toward
## the camera's up axis so tall sprites don't look squashed from above.
func billboard(x: float, y: float, z: float, w: float, h: float, region: Region,
		flip_x: bool = false, lean: float = 0.5, blend: int = Blend.OPAQUE,
		emissive: float = 0.0, alpha: float = 1.0, depth_bias: float = 1.03, tint_argb: int = -1) -> void:
	if not recording:
		return
	var cam := camera
	var rx := cam.rx
	var rz := cam.rz
	var upx := cam.ux * lean
	var upy := (1.0 - lean) + cam.uy * lean
	var upz := cam.uz * lean
	var ul := maxf(sqrt(upx * upx + upy * upy + upz * upz), 1e-5)
	upx /= ul
	upy /= ul
	upz /= ul
	var hw := w / 2.0
	var blx := x - rx * hw
	var blz := z - rz * hw
	var brx := x + rx * hw
	var brz := z + rz * hw
	var u0 := float(region.w) if flip_x else 0.0
	var u1 := 0.0 if flip_x else float(region.w)
	begin(region, blend, emissive, alpha, depth_bias, false)
	normal(-cam.fx, -cam.fy, -cam.fz)
	tint(tint_argb)
	var rh := float(region.h)
	_wx[0] = blx + upx * h; _wy[0] = y + upy * h; _wz[0] = blz + upz * h; _wu[0] = u0; _wv[0] = 0.0
	_wx[1] = brx + upx * h; _wy[1] = y + upy * h; _wz[1] = brz + upz * h; _wu[1] = u1; _wv[1] = 0.0
	_wx[2] = brx; _wy[2] = y; _wz[2] = brz; _wu[2] = u1; _wv[2] = rh
	_wx[3] = blx; _wy[3] = y; _wz[3] = blz; _wu[3] = u0; _wv[3] = rh
	_n = 4
	end()


## A sprite centred on (x, y, z) that faces the camera squarely, turned by [param roll] radians in
## the screen plane (balls, plush toys, sparks).
func sprite(x: float, y: float, z: float, w: float, h: float, region: Region, roll: float = 0.0,
		blend: int = Blend.OPAQUE, emissive: float = 0.0, alpha: float = 1.0, depth_bias: float = 1.0,
		tint_argb: int = -1, flip_x: bool = false) -> void:
	if not recording:
		return
	var cam := camera
	var c := cos(roll)
	var s := sin(roll)
	var hw := w / 2.0
	var hh := h / 2.0
	var ax := (cam.rx * c + cam.ux * s) * hw
	var ay := (cam.ry * c + cam.uy * s) * hw
	var az := (cam.rz * c + cam.uz * s) * hw
	var bx := (cam.ux * c - cam.rx * s) * hh
	var by := (cam.uy * c - cam.ry * s) * hh
	var bz := (cam.uz * c - cam.rz * s) * hh
	var u0 := float(region.w) if flip_x else 0.0
	var u1 := 0.0 if flip_x else float(region.w)
	begin(region, blend, emissive, alpha, depth_bias, false)
	normal(-cam.fx, -cam.fy, -cam.fz)
	tint(tint_argb)
	var rh := float(region.h)
	_wx[0] = x - ax + bx; _wy[0] = y - ay + by; _wz[0] = z - az + bz; _wu[0] = u0; _wv[0] = 0.0
	_wx[1] = x + ax + bx; _wy[1] = y + ay + by; _wz[1] = z + az + bz; _wu[1] = u1; _wv[1] = 0.0
	_wx[2] = x + ax - bx; _wy[2] = y + ay - by; _wz[2] = z + az - bz; _wu[2] = u1; _wv[2] = rh
	_wx[3] = x - ax - bx; _wy[3] = y - ay - by; _wz[3] = z - az - bz; _wu[3] = u0; _wv[3] = rh
	_n = 4
	end()


## A ribbon [param w] wide from one point to another, turned to face the camera.
func beam(x0: float, y0: float, z0: float, x1: float, y1: float, z1: float, w: float, region: Region,
		blend: int = Blend.OPAQUE, emissive: float = 0.0, alpha: float = 1.0, tint_argb: int = -1) -> void:
	if not recording:
		return
	var cam := camera
	var dx := x1 - x0
	var dy := y1 - y0
	var dz := z1 - z0
	var vx := (x0 + x1) / 2.0 - cam.ex
	var vy := (y0 + y1) / 2.0 - cam.ey
	var vz := (z0 + z1) / 2.0 - cam.ez
	var sx := dy * vz - dz * vy
	var sy := dz * vx - dx * vz
	var sz := dx * vy - dy * vx
	var l := sqrt(sx * sx + sy * sy + sz * sz)
	if l < 1e-5:
		return
	var k := w / 2.0 / l
	sx *= k
	sy *= k
	sz *= k
	begin(region, blend, emissive, alpha, 1.0, false)
	normal(-cam.fx, -cam.fy, -cam.fz)
	tint(tint_argb)
	var rw := float(region.w)
	var rh := float(region.h)
	_wx[0] = x0 - sx; _wy[0] = y0 - sy; _wz[0] = z0 - sz; _wu[0] = 0.0; _wv[0] = 0.0
	_wx[1] = x0 + sx; _wy[1] = y0 + sy; _wz[1] = z0 + sz; _wu[1] = rw; _wv[1] = 0.0
	_wx[2] = x1 + sx; _wy[2] = y1 + sy; _wz[2] = z1 + sz; _wu[2] = rw; _wv[2] = rh
	_wx[3] = x1 - sx; _wy[3] = y1 - sy; _wz[3] = z1 - sz; _wu[3] = 0.0; _wv[3] = rh
	_n = 4
	end()


## A horizontal quad [param w] × [param d] lying at height [param y], centred on (x, z) and turned by
## [param angle] around the vertical axis (coins, pucks, spinning shadows).
func flat(x: float, z: float, y: float, w: float, d: float, region: Region, angle: float = 0.0,
		blend: int = Blend.OPAQUE, emissive: float = 0.0, alpha: float = 1.0, tint_argb: int = -1,
		depth_bias: float = 1.0) -> void:
	if not recording:
		return
	var c := cos(angle)
	var s := sin(angle)
	var ax := c * w / 2.0
	var az := s * w / 2.0
	var bx := -s * d / 2.0
	var bz := c * d / 2.0
	begin(region, blend, emissive, alpha, depth_bias, false)
	normal(0.0, 1.0, 0.0)
	tint(tint_argb)
	var rw := float(region.w)
	var rh := float(region.h)
	_wx[0] = x - ax - bx; _wy[0] = y; _wz[0] = z - az - bz; _wu[0] = 0.0; _wv[0] = 0.0
	_wx[1] = x + ax - bx; _wy[1] = y; _wz[1] = z + az - bz; _wu[1] = rw; _wv[1] = 0.0
	_wx[2] = x + ax + bx; _wy[2] = y; _wz[2] = z + az + bz; _wu[2] = rw; _wv[2] = rh
	_wx[3] = x - ax + bx; _wy[3] = y; _wz[3] = z - az + bz; _wu[3] = 0.0; _wv[3] = rh
	_n = 4
	end()


## A horizontal quad lying at height [param y] (floor decals, light pools, shadows).
func decal(x0: float, z0: float, x1: float, z1: float, y: float, region: Region,
		blend: int = Blend.ALPHA, emissive: float = 0.0, alpha: float = 1.0, tint_argb: int = -1) -> void:
	quad(x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, region, 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, blend, emissive, alpha, false, tint_argb)
