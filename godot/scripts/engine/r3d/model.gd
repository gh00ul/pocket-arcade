class_name Model
extends RefCounted
## engine/r3d/Model.kt Model: static geometry. The GPU keeps one copy (one mesh per texture, blend
## and culling group, shared by every place that draws it), so drawing a model many times per
## frame with different placements is cheap: the placements go to one MultiMesh per group.

static var _next_id := 1

var id: int
var polys: Array[Poly]
var min_x := 0.0
var max_x := 0.0
var min_y := 0.0
var max_y := 0.0
var min_z := 0.0
var max_z := 0.0
## A sphere round the model's bounds (in its own coordinates), for culling placed copies.
var bound_x := 0.0
var bound_y := 0.0
var bound_z := 0.0
var bound_r := 0.0
## Which blend layers this model holds, so draws of absent layers can be skipped.
var has_opaque := false
var has_alpha := false
var has_add := false
## Bit (1 << blend) set for each blend layer holding glowing (emissive) polygons.
var glow_mask := 0

## The GPU copy: one [Model.Part] per (texture, blend, cull) group, built on first draw.
var _parts: Array = []
var _built := false


class Part:
	extends RefCounted
	var mesh: ArrayMesh
	var tex: PaTexture
	var blend: int
	var cull: bool
	var glow: bool
	## Index of this part within its model (stable key for instance pools).
	var index: int


func _init(p_polys: Array[Poly]) -> void:
	id = _next_id
	_next_id += 1
	polys = p_polys
	if polys.is_empty():
		return
	min_x = INF
	min_y = INF
	min_z = INF
	max_x = -INF
	max_y = -INF
	max_z = -INF
	for p in polys:
		for i in p.n:
			min_x = minf(min_x, p.xs[i])
			max_x = maxf(max_x, p.xs[i])
			min_y = minf(min_y, p.ys[i])
			max_y = maxf(max_y, p.ys[i])
			min_z = minf(min_z, p.zs[i])
			max_z = maxf(max_z, p.zs[i])
		if p.blend == Blend.OPAQUE:
			has_opaque = true
		elif p.blend == Blend.ALPHA:
			has_alpha = true
		else:
			has_add = true
		if p.emissive > 0.0:
			glow_mask |= 1 << p.blend
	bound_x = (min_x + max_x) / 2.0
	bound_y = (min_y + max_y) / 2.0
	bound_z = (min_z + max_z) / 2.0
	var dx := max_x - min_x
	var dy := max_y - min_y
	var dz := max_z - min_z
	bound_r = sqrt(dx * dx + dy * dy + dz * dz) / 2.0


## Draws every polygon of blend [param only] (or all when -1), placed by [param xf].
func draw(r: Renderer3D, only: int = -1, emissive_boost: float = 1.0, xf: Xform = null, tint: int = -1) -> void:
	r.draw_model(self, only, emissive_boost, xf, tint)


## The textures this model samples (for warm-up and memory accounting).
func textures() -> Array[PaTexture]:
	var out: Array[PaTexture] = []
	for p in polys:
		if not out.has(p.region.tex):
			out.append(p.region.tex)
	return out


## The GPU copy, built once: triangles grouped by texture, blend and culling (GlRenderer.meshFor).
## Culled polygons are wound clockwise as seen from the side their normal faces (Godot's front).
func parts() -> Array:
	if _built:
		return _parts
	_built = true
	var groups := {}
	var order: Array = []
	for p in polys:
		var key := "%d|%d|%d" % [p.region.tex.id, p.blend, 1 if p.cull else 0]
		var g: Dictionary = groups.get(key, {})
		if g.is_empty():
			g = {
				"tex": p.region.tex, "blend": p.blend, "cull": p.cull, "glow": false,
				"pos": PackedVector3Array(), "nrm": PackedVector3Array(), "uv": PackedVector2Array(),
				"col": PackedColorArray(), "cus": PackedFloat32Array(),
			}
			groups[key] = g
			order.append(key)
		if p.emissive > 0.0:
			g["glow"] = true
		_append_poly(p, g)
	var index := 0
	for key in order:
		var g: Dictionary = groups[key]
		var arrays := []
		arrays.resize(Mesh.ARRAY_MAX)
		arrays[Mesh.ARRAY_VERTEX] = g["pos"]
		arrays[Mesh.ARRAY_NORMAL] = g["nrm"]
		arrays[Mesh.ARRAY_TEX_UV] = g["uv"]
		arrays[Mesh.ARRAY_COLOR] = g["col"]
		arrays[Mesh.ARRAY_CUSTOM0] = g["cus"]
		var mesh := ArrayMesh.new()
		if (g["pos"] as PackedVector3Array).size() > 0:
			mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays, [], {}, MeshKit.CUSTOM0_FLOAT)
		var part := Part.new()
		part.mesh = mesh
		part.tex = g["tex"]
		part.blend = g["blend"]
		part.cull = g["cull"]
		part.glow = g["glow"]
		part.index = index
		index += 1
		_parts.append(part)
	return _parts


static func _append_poly(p: Poly, g: Dictionary) -> void:
	var pos: PackedVector3Array = g["pos"]
	var nrm: PackedVector3Array = g["nrm"]
	var uv: PackedVector2Array = g["uv"]
	var col: PackedColorArray = g["col"]
	var cus: PackedFloat32Array = g["cus"]
	var tris := p.n - 2
	if tris < 1:
		return
	var flip := false
	if p.cull:
		# Newell's normal over every edge, so polygons with repeated corners still wind right.
		var gx := 0.0
		var gy := 0.0
		var gz := 0.0
		for a in p.n:
			var b := (a + 1) % p.n
			gx += (p.ys[a] - p.ys[b]) * (p.zs[a] + p.zs[b])
			gy += (p.zs[a] - p.zs[b]) * (p.xs[a] + p.xs[b])
			gz += (p.xs[a] - p.xs[b]) * (p.ys[a] + p.ys[b])
		flip = gx * p.nx + gy * p.ny + gz * p.nz < 0.0
	# Kotlin winds counter-clockwise for GL; Godot's front faces are clockwise.
	flip = not flip
	var iw := 1.0 / p.region.tex.width
	var ih := 1.0 / p.region.tex.height
	var t := Pal.argb(p.tint)
	var tr := 1.0
	var tg := 1.0
	var tb := 1.0
	if t != 0xFFFFFFFF:
		tr = ((t >> 16) & 255) / 255.0
		tg = ((t >> 8) & 255) / 255.0
		tb = (t & 255) / 255.0
	var smooth := p.has_vertex_normals()
	var has_shade := not p.shade.is_empty()
	var rx := float(p.region.x)
	var ry := float(p.region.y)
	for tri in range(1, tris + 1):
		for k in 3:
			var kk := 2 - k if flip else k
			var i := 0 if kk == 0 else (tri if kk == 1 else tri + 1)
			pos.append(Vector3(p.xs[i], p.ys[i], p.zs[i]))
			if smooth:
				nrm.append(Vector3(p.vnx[i], p.vny[i], p.vnz[i]))
			else:
				nrm.append(Vector3(p.nx, p.ny, p.nz))
			uv.append(Vector2((rx + p.us[i]) * iw, (ry + p.vs[i]) * ih))
			var sh := p.shade[i] if has_shade else 1.0
			col.append(Color(tr * sh, tg * sh, tb * sh, 1.0))
			cus.append(p.emissive)
			cus.append(1.0)
			cus.append(p.gloss)
			cus.append(1.0)
