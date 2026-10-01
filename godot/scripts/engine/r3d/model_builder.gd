class_name ModelBuilder
extends RefCounted
## engine/r3d/Model.kt ModelBuilder: builds static models out of quads, boxes, prisms, spheres
## and lathed shapes. Arguments are positional in Kotlin's declaration order (GDScript has no named
## arguments): see docs/PORTING.md.

var polys: Array[Poly] = []


static func _f(values: Array) -> PackedFloat32Array:
	return PackedFloat32Array(values)


func quad(ax: float, ay: float, az: float, bx: float, by: float, bz: float,
		cx: float, cy: float, cz: float, dx: float, dy: float, dz: float,
		region: Region, nx: float, ny: float, nz: float,
		u0: float = 0.0, v0: float = 0.0, u1: float = NAN, v1: float = NAN,
		blend: int = Blend.OPAQUE, emissive: float = 0.0, cull: bool = true, tint: int = -1,
		gloss: float = 0.0, shade: PackedFloat32Array = PackedFloat32Array()) -> ModelBuilder:
	if is_nan(u1):
		u1 = float(region.w)
	if is_nan(v1):
		v1 = float(region.h)
	polys.append(Poly.new(region, 4, _f([ax, bx, cx, dx]), _f([ay, by, cy, dy]), _f([az, bz, cz, dz]),
		_f([u0, u1, u1, u0]), _f([v0, v0, v1, v1]), nx, ny, nz, blend, emissive, cull, tint, gloss,
		PackedFloat32Array(), PackedFloat32Array(), PackedFloat32Array(), shade))
	return self


## Any convex polygon of up to 8 vertices, flat shaded.
func poly(xs: PackedFloat32Array, ys: PackedFloat32Array, zs: PackedFloat32Array, us: PackedFloat32Array, vs: PackedFloat32Array,
		region: Region, nx: float, ny: float, nz: float, blend: int = Blend.OPAQUE, emissive: float = 0.0,
		cull: bool = true, tint: int = -1, gloss: float = 0.0, shade: PackedFloat32Array = PackedFloat32Array()) -> ModelBuilder:
	polys.append(Poly.new(region, xs.size(), xs, ys, zs, us, vs, nx, ny, nz, blend, emissive, cull, tint, gloss,
		PackedFloat32Array(), PackedFloat32Array(), PackedFloat32Array(), shade))
	return self


func _uv_for(r: Region, w: float, h: float, tpu: float) -> Vector2:
	if r.wrap and tpu > 0.0:
		return Vector2(w * tpu, h * tpu)
	return Vector2(r.w, r.h)


## Axis-aligned box; x0 < x1 (left→right), y0 < y1 (floor→up), z0 < z1 (north→south).
func box(x0: float, y0: float, z0: float, x1: float, y1: float, z1: float, f: BoxFaces, tint: int = -1) -> ModelBuilder:
	var tpu := f.texels_per_unit
	var g := f.gloss
	if f.front != null:
		var uv := _uv_for(f.front, x1 - x0, y1 - y0, tpu)
		quad(x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, f.front, 0, 0, 1, 0, 0, uv.x, uv.y, Blend.OPAQUE, f.front_emissive, true, tint, g)
	if f.back != null:
		var uv := _uv_for(f.back, x1 - x0, y1 - y0, tpu)
		quad(x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, f.back, 0, 0, -1, 0, 0, uv.x, uv.y, Blend.OPAQUE, 0.0, true, tint, g)
	if f.left != null:
		var uv := _uv_for(f.left, z1 - z0, y1 - y0, tpu)
		quad(x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, f.left, -1, 0, 0, 0, 0, uv.x, uv.y, Blend.OPAQUE, 0.0, true, tint, g)
	if f.right != null:
		var uv := _uv_for(f.right, z1 - z0, y1 - y0, tpu)
		quad(x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, f.right, 1, 0, 0, 0, 0, uv.x, uv.y, Blend.OPAQUE, 0.0, true, tint, g)
	if f.top != null:
		var uv := _uv_for(f.top, x1 - x0, z1 - z0, tpu)
		quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, f.top, 0, 1, 0, 0, 0, uv.x, uv.y, Blend.OPAQUE, f.top_emissive, true, tint, g)
	return self


## A vertical prism approximating a cylinder, with optional lids. With [param inward] the walls face
## the axis; with [param smooth] they are shaded as a round surface rather than facets.
func cylinder(cx: float, cz: float, y0: float, y1: float, radius: float, sides: int,
		side: Region, top: Region = null, emissive: float = 0.0, tint: int = -1,
		bottom: Region = null, inward: bool = false, top_radius: float = NAN,
		smooth: bool = true, gloss: float = 0.0) -> ModelBuilder:
	if is_nan(top_radius):
		top_radius = radius
	var step := TAU / sides
	var flip := -1.0 if inward else 1.0
	for i in sides:
		var a0 := i * step
		var a1 := (i + 1) * step
		var am := (a0 + a1) / 2.0
		var u0 := side.w * i / float(sides)
		var u1 := side.w * (i + 1) / float(sides)
		var xs := _f([cx + cos(a1) * top_radius, cx + cos(a0) * top_radius, cx + cos(a0) * radius, cx + cos(a1) * radius])
		var ys := _f([y1, y1, y0, y0])
		var zs := _f([cz + sin(a1) * top_radius, cz + sin(a0) * top_radius, cz + sin(a0) * radius, cz + sin(a1) * radius])
		var us := _f([u0, u1, u1, u0])
		var vs := _f([0.0, 0.0, float(side.h), float(side.h)])
		if smooth:
			# Tilt the normals for cones so light falls correctly on tapered sides.
			var slope := (radius - top_radius) / maxf(y1 - y0, 1e-3)
			var ny := slope * flip
			var nl := sqrt(1.0 + ny * ny)
			polys.append(Poly.new(side, 4, xs, ys, zs, us, vs, cos(am) * flip, 0.0, sin(am) * flip, Blend.OPAQUE, emissive, true, tint, gloss,
				_f([cos(a1) * flip / nl, cos(a0) * flip / nl, cos(a0) * flip / nl, cos(a1) * flip / nl]),
				_f([ny / nl, ny / nl, ny / nl, ny / nl]),
				_f([sin(a1) * flip / nl, sin(a0) * flip / nl, sin(a0) * flip / nl, sin(a1) * flip / nl])))
		else:
			polys.append(Poly.new(side, 4, xs, ys, zs, us, vs, cos(am) * flip, 0.0, sin(am) * flip, Blend.OPAQUE, emissive, true, tint, gloss))
	if top != null:
		disc(cx, cz, y1, top_radius, sides, top, 1.0, emissive, tint, gloss)
	if bottom != null:
		disc(cx, cz, y0, radius, sides, bottom, -1.0, emissive, tint, gloss)
	return self


## A flat regular polygon facing up ([param ny] = 1) or down (-1), fanned into ≤ 8-gons.
func disc(cx: float, cz: float, y: float, radius: float, sides: int, t: Region, ny: float = 1.0,
		emissive: float = 0.0, tint: int = -1, gloss: float = 0.0, blend: int = Blend.OPAQUE) -> ModelBuilder:
	var step := TAU / sides
	var xs := PackedFloat32Array()
	var zs := PackedFloat32Array()
	var us := PackedFloat32Array()
	var vs := PackedFloat32Array()
	for i in sides:
		var a := i * step
		xs.append(cx + cos(a) * radius)
		zs.append(cz + sin(a) * radius)
		us.append(t.w / 2.0 + cos(a) * t.w / 2.0)
		vs.append(t.h / 2.0 + sin(a) * t.h / 2.0)
	var start := 1
	while start < sides - 1:
		var count := mini(7, sides - start)
		var idx := PackedInt32Array([0])
		for k in count:
			idx.append(start + k)
		var px := PackedFloat32Array()
		var py := PackedFloat32Array()
		var pz := PackedFloat32Array()
		var pu := PackedFloat32Array()
		var pv := PackedFloat32Array()
		for j in idx:
			px.append(xs[j])
			py.append(y)
			pz.append(zs[j])
			pu.append(us[j])
			pv.append(vs[j])
		polys.append(Poly.new(t, idx.size(), px, py, pz, pu, pv, 0.0, ny, 0.0, blend, emissive, true, tint, gloss))
		start += count - 1
	return self


## A flat ring (annulus) at height [param y], facing up, in [param sides] quads.
func annulus(cx: float, cz: float, y: float, r_in: float, r_out: float, sides: int, t: Region, tint: int = -1, gloss: float = 0.0, emissive: float = 0.0) -> ModelBuilder:
	var step := TAU / sides
	for i in sides:
		var a0 := i * step
		var a1 := (i + 1) * step
		quad(cx + cos(a0) * r_out, y, cz + sin(a0) * r_out, cx + cos(a1) * r_out, y, cz + sin(a1) * r_out,
			cx + cos(a1) * r_in, y, cz + sin(a1) * r_in, cx + cos(a0) * r_in, y, cz + sin(a0) * r_in,
			t, 0.0, 1.0, 0.0, t.w * i / float(sides), 0.0, t.w * (i + 1) / float(sides), float(t.h),
			Blend.OPAQUE, emissive, true, tint, gloss)
	return self


## A surface of revolution around the vertical axis through ([param cx], [param cz]): [param profile]
## lists (radius, height) pairs from bottom to top. Smooth shaded; the texture wraps around once
## horizontally and runs bottom→top vertically.
func lathe(cx: float, cy: float, cz: float, profile: PackedFloat32Array, sides: int, t: Region,
		tint: int = -1, gloss: float = 0.0, emissive: float = 0.0, cull: bool = true) -> ModelBuilder:
	var rings := profile.size() / 2
	if rings < 2:
		return self
	var pnr := PackedFloat32Array()
	var pny := PackedFloat32Array()
	pnr.resize(rings)
	pny.resize(rings)
	for j in rings:
		var a := maxi(j - 1, 0)
		var b := mini(j + 1, rings - 1)
		var dr := profile[b * 2] - profile[a * 2]
		var dy := profile[b * 2 + 1] - profile[a * 2 + 1]
		# Outward normal of the (r, y) curve: rotate the tangent by -90°.
		var nr := dy
		var ny := -dr
		var l := maxf(sqrt(nr * nr + ny * ny), 1e-5)
		pnr[j] = nr / l
		pny[j] = ny / l
	var step := TAU / sides
	var total_h := profile[(rings - 1) * 2 + 1] - profile[1]
	if total_h == 0.0:
		total_h = 1.0
	for j in rings - 1:
		var r0 := profile[j * 2]
		var y0 := profile[j * 2 + 1]
		var r1 := profile[j * 2 + 2]
		var y1 := profile[j * 2 + 3]
		var v0 := t.h * (1.0 - (y0 - profile[1]) / total_h)
		var v1 := t.h * (1.0 - (y1 - profile[1]) / total_h)
		for i in sides:
			var a0 := i * step
			var a1 := (i + 1) * step
			var c0 := cos(a0)
			var s0 := sin(a0)
			var c1 := cos(a1)
			var s1 := sin(a1)
			var u0 := t.w * i / float(sides)
			var u1 := t.w * (i + 1) / float(sides)
			var am := (a0 + a1) / 2.0
			var fnr := (pnr[j] + pnr[j + 1]) / 2.0
			var fny := (pny[j] + pny[j + 1]) / 2.0
			polys.append(Poly.new(t, 4,
				_f([cx + c1 * r1, cx + c0 * r1, cx + c0 * r0, cx + c1 * r0]),
				_f([cy + y1, cy + y1, cy + y0, cy + y0]),
				_f([cz + s1 * r1, cz + s0 * r1, cz + s0 * r0, cz + s1 * r0]),
				_f([u1, u0, u0, u1]), _f([v1, v1, v0, v0]),
				cos(am) * fnr, fny, sin(am) * fnr, Blend.OPAQUE, emissive, cull, tint, gloss,
				_f([c1 * pnr[j + 1], c0 * pnr[j + 1], c0 * pnr[j], c1 * pnr[j]]),
				_f([pny[j + 1], pny[j + 1], pny[j], pny[j]]),
				_f([s1 * pnr[j + 1], s0 * pnr[j + 1], s0 * pnr[j], s1 * pnr[j]])))
	return self


## A UV sphere (or ellipsoid with [param sy] ≠ 1) centred on (cx, cy, cz); latitude bands between
## [param y_from] and [param y_to] (sine of latitude) make domes and caps easy.
func sphere(cx: float, cy: float, cz: float, radius: float, t: Region,
		slices: int = 16, stacks: int = 10, sy: float = 1.0, tint: int = -1, gloss: float = 0.0, emissive: float = 0.0,
		y_from: float = -1.0, y_to: float = 1.0) -> ModelBuilder:
	var lat0 := asin(clampf(y_from, -1.0, 1.0))
	var lat1 := asin(clampf(y_to, -1.0, 1.0))
	var profile := PackedFloat32Array()
	profile.resize((stacks + 1) * 2)
	for j in stacks + 1:
		var lat := lat0 + (lat1 - lat0) * j / stacks
		profile[j * 2] = cos(lat) * radius
		profile[j * 2 + 1] = sin(lat) * radius * sy
	# Make the poles meet exactly.
	if y_from <= -1.0:
		profile[0] = 0.0
	if y_to >= 1.0:
		profile[stacks * 2] = 0.0
	lathe(cx, cy, cz, profile, slices, t, tint, gloss, emissive)
	return self


## A capsule (rounded rod) from a to b: arms, legs and handles. Built along +y and rotated into place.
func capsule(ax: float, ay: float, az: float, bx: float, by: float, bz: float, radius: float, t: Region,
		slices: int = 10, tint: int = -1, gloss: float = 0.0) -> ModelBuilder:
	var dx := bx - ax
	var dy := by - ay
	var dz := bz - az
	var length := sqrt(dx * dx + dy * dy + dz * dz)
	var caps := 3
	var profile := PackedFloat32Array()
	for j in caps + 1:
		var lat := -PI / 2.0 + (PI / 2.0) * j / caps
		profile.append(cos(lat) * radius)
		profile.append(sin(lat) * radius)
	for j in caps + 1:
		var lat := (PI / 2.0) * j / caps
		profile.append(cos(lat) * radius)
		profile.append(length + sin(lat) * radius)
	profile[0] = 0.0
	profile[profile.size() - 2] = 0.0
	var local := ModelBuilder.new().lathe(0.0, 0.0, 0.0, profile, slices, t, tint, gloss).build()
	# Rotate +y onto the rod's direction.
	var yaw := atan2(dx, dz)
	var pitch := atan2(sqrt(dx * dx + dz * dz), dy)
	add_xf(local, Xform.new().set_xf(ax, ay, az, yaw, pitch))
	return self


## A torus lying flat around the vertical axis through (cx, cy, cz).
func torus(cx: float, cy: float, cz: float, radius: float, tube: float, t: Region,
		segments: int = 20, sides: int = 8, tint: int = -1, gloss: float = 0.0, emissive: float = 0.0) -> ModelBuilder:
	var profile := PackedFloat32Array()
	profile.resize((sides + 1) * 2)
	for j in sides + 1:
		var a := -PI / 2.0 + j * TAU / sides
		profile[j * 2] = radius + cos(a) * tube
		profile[j * 2 + 1] = sin(a) * tube
	lathe(cx, cy, cz, profile, segments, t, tint, gloss, emissive, true)
	return self


func add(model: Model) -> ModelBuilder:
	polys.append_array(model.polys)
	return self


## Adds a copy of [param model] stretched by (sx, sy, sz) about the origin, then moved by (tx, ty, tz).
func add_scaled(model: Model, sx: float, sy: float, sz: float, tx: float = 0.0, ty: float = 0.0, tz: float = 0.0) -> ModelBuilder:
	for p in model.polys:
		var xs := PackedFloat32Array()
		var ys := PackedFloat32Array()
		var zs := PackedFloat32Array()
		xs.resize(p.n)
		ys.resize(p.n)
		zs.resize(p.n)
		for i in p.n:
			xs[i] = p.xs[i] * sx + tx
			ys[i] = p.ys[i] * sy + ty
			zs[i] = p.zs[i] * sz + tz
		var fn := _scaled_normal(p.nx, p.ny, p.nz, sx, sy, sz)
		var vnx := PackedFloat32Array()
		var vny := PackedFloat32Array()
		var vnz := PackedFloat32Array()
		if p.has_vertex_normals():
			vnx.resize(p.n)
			vny.resize(p.n)
			vnz.resize(p.n)
			for i in p.n:
				var v := _scaled_normal(p.vnx[i], p.vny[i], p.vnz[i], sx, sy, sz)
				vnx[i] = v.x
				vny[i] = v.y
				vnz[i] = v.z
		polys.append(Poly.new(p.region, p.n, xs, ys, zs, p.us, p.vs, fn.x, fn.y, fn.z, p.blend, p.emissive, p.cull, p.tint, p.gloss, vnx, vny, vnz, p.shade))
	return self


static func _scaled_normal(x: float, y: float, z: float, sx: float, sy: float, sz: float) -> Vector3:
	var nx := x / sx
	var ny := y / sy
	var nz := z / sz
	var l := maxf(sqrt(nx * nx + ny * ny + nz * nz), 1e-6)
	return Vector3(nx / l, ny / l, nz / l)


## Adds a copy of [param model] moved by [param xf] (normals stay correct if xf is stretched).
## Kotlin's `add(model, xf)`.
func add_xf(model: Model, xf: Xform) -> ModelBuilder:
	for p in model.polys:
		var xs := PackedFloat32Array()
		var ys := PackedFloat32Array()
		var zs := PackedFloat32Array()
		xs.resize(p.n)
		ys.resize(p.n)
		zs.resize(p.n)
		for i in p.n:
			xs[i] = xf.x(p.xs[i], p.ys[i], p.zs[i])
			ys[i] = xf.y(p.xs[i], p.ys[i], p.zs[i])
			zs[i] = xf.z(p.xs[i], p.ys[i], p.zs[i])
		var fn := _xf_normal(xf, p.nx, p.ny, p.nz)
		var vnx := PackedFloat32Array()
		var vny := PackedFloat32Array()
		var vnz := PackedFloat32Array()
		if p.has_vertex_normals():
			vnx.resize(p.n)
			vny.resize(p.n)
			vnz.resize(p.n)
			for i in p.n:
				var v := _xf_normal(xf, p.vnx[i], p.vny[i], p.vnz[i])
				vnx[i] = v.x
				vny[i] = v.y
				vnz[i] = v.z
		polys.append(Poly.new(p.region, p.n, xs, ys, zs, p.us, p.vs, fn.x, fn.y, fn.z, p.blend, p.emissive, p.cull, p.tint, p.gloss, vnx, vny, vnz, p.shade))
	return self


static func _xf_normal(xf: Xform, x: float, y: float, z: float) -> Vector3:
	var nx := xf.normal_x(x, y, z)
	var ny := xf.normal_y(x, y, z)
	var nz := xf.normal_z(x, y, z)
	var l := maxf(sqrt(nx * nx + ny * ny + nz * nz), 1e-6)
	return Vector3(nx / l, ny / l, nz / l)


func build() -> Model:
	return Model.new(polys.duplicate())
