extends PaTest
## hub/CabinetFormTest.kt: chamfered, floor-shaded cabinet boxes: closed, outward-facing, inside
## their box and only ever darker. (Kotlin's `ModelBuilder.beveledBox` extension is
## `CabinetForm.beveled_box(builder, ...)` here.)

var tex: PaTexture
var r: Region
var all: BoxFaces


func before_each() -> void:
	var px := PackedInt32Array()
	px.resize(16)
	px.fill(-1)
	tex = PaTexture.from_argb(4, 4, px)
	r = tex.full()
	all = BoxFaces.new(r, r, r, r, r, 0.0, 0.0, 0.0, 0.3)


func build(f: BoxFaces = null, x0: float = 10.0, y0: float = 0.0, z0: float = 20.0, x1: float = 50.0, y1: float = 80.0, z1: float = 50.0,
		bevel: float = CabinetForm.BEVEL, floor_ao: float = CabinetForm.FLOOR_AO) -> Model:
	var faces := all if f == null else f
	var b := CabinetForm.beveled_box(ModelBuilder.new(), x0, y0, z0, x1, y1, z1, faces, -1, bevel, floor_ao)
	return Model.new(b.build().polys)


static func key(p: Poly, i: int) -> String:
	return "%d,%d,%d" % [MathUtil.round_to_int(p.xs[i] * 1000.0), MathUtil.round_to_int(p.ys[i] * 1000.0), MathUtil.round_to_int(p.zs[i] * 1000.0)]


static func fmin(a: PackedFloat32Array) -> float:
	var m := INF
	for v in a:
		m = minf(m, v)
	return m


static func fmax(a: PackedFloat32Array) -> float:
	var m := -INF
	for v in a:
		m = maxf(m, v)
	return m


static func axis(p: Poly) -> float:
	return maxf(absf(p.nx), maxf(absf(p.ny), absf(p.nz)))


func test_a_full_box_becomes_seventeen_polygons_or_twenty_five_with_occlusion() -> void:
	assert_eq(17, build(null, 10.0, 0.0, 20.0, 50.0, 80.0, 50.0, CabinetForm.BEVEL, 0.0).polys.size())
	assert_eq(25, build().polys.size())
	assert_eq(5, ModelBuilder.new().box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, all).build().polys.size())


func test_the_box_keeps_exactly_its_bounds() -> void:
	var m := build()
	assert_near(10.0, m.min_x, 1e-4)
	assert_near(50.0, m.max_x, 1e-4)
	assert_near(0.0, m.min_y, 1e-4)
	assert_near(80.0, m.max_y, 1e-4)
	assert_near(20.0, m.min_z, 1e-4)
	assert_near(50.0, m.max_z, 1e-4)


func test_the_surface_is_closed_above_the_floor() -> void:
	for ao: float in [0.0, CabinetForm.FLOOR_AO]:
		var edges := {}
		var on_floor := {}
		for p: Poly in build(null, 10.0, 0.0, 20.0, 50.0, 80.0, 50.0, CabinetForm.BEVEL, ao).polys:
			for i in p.n:
				var j := (i + 1) % p.n
				var a := key(p, i)
				var b := key(p, j)
				if a == b:
					continue
				var e := a + "|" + b if a < b else b + "|" + a
				edges[e] = int(edges.get(e, 0)) + 1
				if p.ys[i] == 0.0 and p.ys[j] == 0.0:
					on_floor[e] = true
		for e: String in edges:
			var n: int = edges[e]
			if on_floor.has(e):
				assert_eq(1, n, "floor edge %s (ao %s)" % [e, ao])
			else:
				assert_eq(2, n, "edge %s (ao %s) should join exactly two polygons" % [e, ao])


func test_every_normal_is_unit_length_and_points_out_of_the_box() -> void:
	var cx := 30.0
	var cy := 40.0
	var cz := 35.0
	for p: Poly in build().polys:
		var length := sqrt(p.nx * p.nx + p.ny * p.ny + p.nz * p.nz)
		assert_near(1.0, length, 1e-3)
		var mx := 0.0
		var my := 0.0
		var mz := 0.0
		for i in p.n:
			mx += p.xs[i]
			my += p.ys[i]
			mz += p.zs[i]
		mx /= p.n
		my /= p.n
		mz /= p.n
		assert_true(p.nx * (mx - cx) + p.ny * (my - cy) + p.nz * (mz - cz) > 0.0, "normal (%s, %s, %s) at (%s, %s, %s)" % [p.nx, p.ny, p.nz, mx, my, mz])
		# And it is the polygon's own plane (the mesh builder winds triangles from it).
		var gx := 0.0
		var gy := 0.0
		var gz := 0.0
		for a in p.n:
			var b := (a + 1) % p.n
			gx += (p.ys[a] - p.ys[b]) * (p.zs[a] + p.zs[b])
			gy += (p.zs[a] - p.zs[b]) * (p.xs[a] + p.xs[b])
			gz += (p.xs[a] - p.xs[b]) * (p.ys[a] + p.ys[b])
		var gl := sqrt(gx * gx + gy * gy + gz * gz)
		assert_true(gl > 1e-4, "degenerate polygon")
		assert_near(1.0, absf(gx * p.nx + gy * p.ny + gz * p.nz) / gl, 1e-3)


func test_chamfers_catch_the_light_with_at_least_the_bevel_gloss() -> void:
	var flat := BoxFaces.new(r, r, r, r, r, 0.0, 0.0, 0.0, 0.0)
	# The cuts are the polygons that face neither straight along an axis: 4 down the corners, 4
	# along the top, 4 corners.
	var cuts: Array[Poly] = []
	for p: Poly in build(flat, 10.0, 0.0, 20.0, 50.0, 80.0, 50.0, CabinetForm.BEVEL, 0.0).polys:
		if axis(p) < 0.99:
			cuts.append(p)
	assert_eq(12, cuts.size())
	for p in cuts:
		assert_true(p.gloss >= CabinetForm.BEVEL_GLOSS - 1e-6)


func test_occlusion_only_darkens_the_foot_of_unlit_sides() -> void:
	var m := build()
	var darkest := 1.0
	for p: Poly in m.polys:
		var s := p.shade
		if s.is_empty():
			# Anything without shading is a top face, a cut on top, or the plain upper part of a side.
			continue
		assert_eq(p.n, s.size())
		for i in p.n:
			assert_true(s[i] >= 1.0 - CabinetForm.FLOOR_AO - 1e-4 and s[i] <= 1.0, "shade %s" % s[i])
			darkest = minf(darkest, s[i])
			# Darkest right at the floor, plain from the top of the occlusion band up.
			if p.ys[i] >= CabinetForm.FLOOR_AO_HEIGHT - 1e-3:
				assert_near(1.0, s[i], 1e-5)
			if p.ys[i] <= 1e-4:
				assert_near(1.0 - CabinetForm.FLOOR_AO, s[i], 1e-5)
	assert_near(1.0 - CabinetForm.FLOOR_AO, darkest, 1e-5)
	# Everything at the floor is shaded.
	for p: Poly in m.polys:
		for i in p.n:
			if p.ys[i] <= 1e-4:
				assert_false(p.shade.is_empty())


func test_glowing_faces_are_never_darkened() -> void:
	var lit := BoxFaces.new(r, r, r, r, r, 0.9, 0.0, 0.0, 0.3)
	var any_lit := false
	for p: Poly in build(lit).polys:
		if p.emissive > 0.0:
			any_lit = true
			assert_true(p.shade.is_empty(), "a glowing polygon must keep its full brightness")
	assert_true(any_lit)


func test_boxes_not_on_the_floor_get_no_occlusion_by_default() -> void:
	var m := Model.new(CabinetForm.beveled_box(ModelBuilder.new(), 10.0, 30.0, 20.0, 50.0, 60.0, 50.0, all).build().polys)
	for p: Poly in m.polys:
		assert_true(p.shade.is_empty())
	assert_eq(17, m.polys.size())


func test_a_missing_face_keeps_its_neighbours_square_on_that_side() -> void:
	# A kick panel between two side panels: front, top and back only, no sides.
	var m := build(BoxFaces.new(r, null, null, r, r), 10.0, 0.0, 20.0, 50.0, 80.0, 50.0, CabinetForm.BEVEL, 0.0)
	assert_near(10.0, m.min_x, 1e-4)
	assert_near(50.0, m.max_x, 1e-4)
	# The front runs the full width (nothing cut at its ends); there is one cut along the top front,
	# one along the top back, and no vertical cuts or corners.
	var front: Array[Poly] = []
	for p: Poly in m.polys:
		if p.nz == 1.0 and p.ny == 0.0:
			front.append(p)
	assert_eq(1, front.size())
	assert_near(10.0, fmin(front[0].xs), 1e-4)
	assert_near(50.0, fmax(front[0].xs), 1e-4)
	assert_eq(3 + 2, m.polys.size())


func test_front_face_keeps_its_texture_registered_to_the_whole_box() -> void:
	var m := build(null, 10.0, 0.0, 20.0, 50.0, 80.0, 50.0, CabinetForm.BEVEL, 0.0)
	var front: Array[Poly] = []
	for p: Poly in m.polys:
		if p.nz == 1.0 and p.ny == 0.0:
			front.append(p)
	assert_eq(1, front.size())
	var b := CabinetForm.BEVEL
	# The face is inset by a bevel on each side; its u runs the same fraction of the texture.
	assert_near(r.w * b / 40.0, fmin(front[0].us), 1e-3)
	assert_near(r.w * (40.0 - b) / 40.0, fmax(front[0].us), 1e-3)
	# Rows too: inset by the top cut, all the way down to the floor.
	assert_near(r.h * b / 80.0, fmin(front[0].vs), 1e-3)
	assert_near(float(r.h), fmax(front[0].vs), 1e-3)


func test_thin_panels_keep_most_of_their_width() -> void:
	# The side panels are only 1.8 thick: the chamfer must not eat the panel.
	var m := build(null, 10.0, 0.0, 20.0, 11.8, 80.0, 50.0, CabinetForm.BEVEL, 0.0)
	var front: Array[Poly] = []
	for p: Poly in m.polys:
		if p.nz == 1.0 and p.ny == 0.0:
			front.append(p)
	assert_eq(1, front.size())
	var width := fmax(front[0].xs) - fmin(front[0].xs)
	assert_true(width > 1.8 * 0.35, "front width %s" % width)


func test_a_bevel_of_zero_keeps_square_edges_but_still_shades_the_foot() -> void:
	# A kick panel tucked between side panels: nothing to cut, but it still sits in the floor.
	var kick := BoxFaces.new(r, null, null, r, r)
	var m := build(kick, 10.0, 0.0, 20.0, 50.0, 80.0, 50.0, 0.0)
	assert_eq(2 + 2 + 1, m.polys.size())
	assert_near(10.0, m.min_x, 1e-4)
	assert_near(50.0, m.max_x, 1e-4)
	var shaded := 0
	for p: Poly in m.polys:
		if not p.shade.is_empty():
			shaded += 1
		assert_true(axis(p) > 0.99, "only axis-facing polygons")
	assert_eq(2, shaded)
	# And with no occlusion either it is exactly a plain box.
	assert_eq(3, build(kick, 10.0, 0.0, 20.0, 50.0, 80.0, 50.0, 0.0, 0.0).polys.size())


func test_tiny_boxes_stay_plain() -> void:
	var m := Model.new(CabinetForm.beveled_box(ModelBuilder.new(), 0.0, 0.0, 0.0, 0.2, 0.2, 0.2, all).build().polys)
	assert_eq(5, m.polys.size())


func test_the_occlusion_band_is_clamped_on_short_boxes() -> void:
	# A box lower than the band: the darkening stops short of the top cut instead of overshooting it.
	var m := build(null, 10.0, 0.0, 20.0, 50.0, 6.0, 50.0)
	var any_shaded := false
	for p: Poly in m.polys:
		for i in p.n:
			assert_true(p.ys[i] >= 0.0 and p.ys[i] <= 6.0)
		if not p.shade.is_empty():
			any_shaded = true
	assert_true(any_shaded)
