extends PaTest
## hub/CabinetModelsTest.kt: the hall's cabinet models, built for real (headless: the painted
## textures have no pixels, which the models don't need): every game's own cabinet and every
## generic shape. Nothing here can say a cabinet is pretty, but it keeps the things a detailed model
## can silently get wrong in check: parts poking out of the footprint, garbage normals or
## coordinates, a runaway polygon count (every cabinet in view is drawn each frame), a runaway number
## of textures (each one is a draw call) and glow bright enough to bloom white.
##
## The machines are build-13's eleven ([HallGames]); a machine whose own cabinet design isn't ported
## yet (a stand-in that only knows the design's sizes) has no model to check and is left out until
## its design lands (the list is printed). The last test is Godot-only: banks share their model.

## Loudest emissive multiplier any cabinet polygon may have; the highlight adds a fifth on top.
const MAX_EMISSIVE := 1.6
const POLY_BUDGET := 5600
const TEXTURE_BUDGET := 48

## How far a cabinet's parts may reach past its footprint: marquees, rails and the air-hockey
## scoreboard past the sides, the mains cable trailing off behind, a coin door, a lip or a leaning
## seat at the front. Details go inside these margins; the footprint, the collision boxes and the
## bank cells are unchanged. [side, back, front, top].
const OVERHANG := [3.1, 6.6, 2.0, 0.7]
## The pinball table's plunger pokes out of its front.
const OVERHANG_OF := {"pinball": [3.1, 6.6, 4.6, 0.7]}

static var _built: Array = []


## One built cabinet plus what it was built in: [name, prop, unit].
static func built() -> Array:
	if not _built.is_empty():
		return _built
	var games := HallGames.games()
	var map: HubMap = HallGames.maps()[1][1]
	var skipped := PackedStringArray()
	for i in games.size():
		var g := games[i]
		if g.cabinet() is HallGames.SizedDesign:
			skipped.append(g.id)
			continue
		var p: Prop = null
		for q: Prop in map.props:
			if q.kind == PropKind.MACHINE and q.machine == i:
				p = q
				break
		_built.append([g.id, p, MachineUnit.new(p, g, MachineArt.new(g))])
	if not skipped.is_empty():
		print("CABINET designs not ported yet (left out): ", ", ".join(skipped))
	var claw_index := -1
	for i in games.size():
		if games[i].id == "claw":
			claw_index = i
	var claw_game := games[claw_index]
	for shape: int in [MiniGame.CabinetShape.UPRIGHT, MiniGame.CabinetShape.WIDE, MiniGame.CabinetShape.LANE, MiniGame.CabinetShape.TABLE]:
		var p := machine_prop(shape, claw_index)
		_built.append(["generic-" + String(MiniGame.CabinetShape.keys()[shape]).to_lower(), p, MachineUnit.new(p, claw_game, MachineArt.new(claw_game))])
	return _built


static func machine_prop(shape: int, machine: int) -> Prop:
	var s := HubLayout.cabinet_size(shape)
	return Prop.new(PropKind.MACHINE, 100.0, 100.0, 100.0 + s.x, 100.0 + s.y, s.z, machine, Catalog.NONE, true, shape)


static func textures(m: Model) -> int:
	var seen := {}
	for p: Poly in m.polys:
		seen[p.region.tex.id] = true
	return seen.size()


func test_print_budgets() -> void:
	# Read with the test output; the asserts below are the actual limits.
	for e: Array in built():
		var p: Prop = e[1]
		var u: MachineUnit = e[2]
		var m := u.full_model()
		var opaque := 0
		var alpha_n := 0
		var add_n := 0
		for q: Poly in m.polys:
			if q.blend == Blend.OPAQUE:
				opaque += 1
			elif q.blend == Blend.ALPHA:
				alpha_n += 1
			else:
				add_n += 1
		print("CABINET %-16s polys %4d (opaque %4d alpha %3d add %3d) textures %2d lights %d  x %.1f..%.1f (box %.1f..%.1f)  z %.1f..%.1f (box %.1f..%.1f)  y 0..%.1f (h %.1f)" % [
			e[0], m.polys.size(), opaque, alpha_n, add_n, textures(m), u.lights.size(),
			m.min_x, m.max_x, p.x0, p.x1, m.min_z, m.max_z, p.z0, p.z1, m.max_y, p.height])


func test_every_model_has_sane_geometry() -> void:
	for e: Array in built():
		var name: String = e[0]
		var m := (e[2] as MachineUnit).full_model()
		assert_false(m.polys.is_empty(), "%s is empty" % name)
		for p: Poly in m.polys:
			assert_not_null(p.region)
			assert_true(p.n >= 3 and p.n <= 8, "%s: a polygon with %d vertices" % [name, p.n])
			for i in p.n:
				for v: float in [p.xs[i], p.ys[i], p.zs[i], p.us[i], p.vs[i]]:
					if not is_finite(v):
						fail("%s: non-finite vertex value" % name)
			var length := sqrt(p.nx * p.nx + p.ny * p.ny + p.nz * p.nz)
			# The shader normalises, and a few hand-written quads lean a normal without renormalising
			# it. Lathed shapes (spheres, tori, capsules) average their edge normals, so their face
			# normal is shorter.
			if not p.has_vertex_normals():
				assert_near(1.0, length, 0.08, "%s: a normal that isn't near unit length" % name)
			else:
				assert_true(length > 0.5, "%s: a smooth polygon's normal is only %s long" % [name, length])
			assert_true(p.gloss >= 0.0 and p.gloss <= 1.0, "%s: gloss %s" % [name, p.gloss])
			assert_true(p.emissive >= 0.0 and p.emissive <= MAX_EMISSIVE, "%s: emissive %s" % [name, p.emissive])


func test_cabinets_stay_inside_their_footprint_plus_the_known_overhang() -> void:
	for e: Array in built():
		var name: String = e[0]
		var p: Prop = e[1]
		var o: Array = OVERHANG_OF.get(name, OVERHANG)
		var side: float = o[0]
		var back: float = o[1]
		var front: float = o[2]
		var top: float = o[3]
		# Solid and glass parts only: an additive glow decal (the racer's floor pan light) spills
		# further by design.
		var min_x := INF
		var max_x := -INF
		var min_z := INF
		var max_z := -INF
		var min_y := INF
		var max_y := -INF
		for q: Poly in (e[2] as MachineUnit).full_model().polys:
			if q.blend == Blend.ADD:
				continue
			for i in q.n:
				min_x = minf(min_x, q.xs[i])
				max_x = maxf(max_x, q.xs[i])
				min_z = minf(min_z, q.zs[i])
				max_z = maxf(max_z, q.zs[i])
				min_y = minf(min_y, q.ys[i])
				max_y = maxf(max_y, q.ys[i])
		assert_true(min_x >= p.x0 - side - 1e-3, "%s sticks out left: %s vs %s" % [name, min_x, p.x0])
		assert_true(max_x <= p.x1 + side + 1e-3, "%s sticks out right: %s vs %s" % [name, max_x, p.x1])
		assert_true(min_z >= p.z0 - back - 1e-3, "%s sticks out at the back: %s vs %s" % [name, min_z, p.z0])
		assert_true(max_z <= p.z1 + front + 1e-3, "%s sticks out at the front: %s vs %s" % [name, max_z, p.z1])
		assert_true(max_y <= p.height + top + 1e-3, "%s is too tall: %s vs %s" % [name, max_y, p.height])
		assert_true(min_y >= -1e-3, "%s goes below the floor: %s" % [name, min_y])


func test_polygon_and_texture_budgets_hold() -> void:
	for e: Array in built():
		var m := (e[2] as MachineUnit).full_model()
		assert_true(m.polys.size() <= POLY_BUDGET, "%s has %d polygons (budget %d)" % [e[0], m.polys.size(), POLY_BUDGET])
		assert_true(textures(m) <= TEXTURE_BUDGET, "%s uses %d textures (budget %d)" % [e[0], textures(m), TEXTURE_BUDGET])


func test_every_cabinet_has_its_floor_pool_and_a_few_lights_of_its_own() -> void:
	for e: Array in built():
		# The hall has a light budget: the floor pool every cabinet throws plus one or two of its own.
		var n := (e[2] as MachineUnit).lights.size()
		assert_true(n >= 2 and n <= 4, "%s has %d lights" % [e[0], n])


func test_pale_colours_glow_less_than_saturated_ones_and_dark_ones_are_left_alone() -> void:
	var white := MachineKit.glow_for(0xFFFFFFFF, 1.0)
	var yellow := MachineKit.glow_for(0xFFFFE14D, 1.0)
	var red := MachineKit.glow_for(0xFFB0213A, 1.0)
	assert_true(white < yellow, "white %s, yellow %s" % [white, yellow])
	assert_true(yellow <= red, "yellow %s, red %s" % [yellow, red])
	assert_near(1.0, red, 1e-6)
	assert_near(1.0 - MachineKit.PALE_GLOW_CUT, white, 1e-6)
	assert_near(0.0, MachineKit.glow_for(-1, 0.0), 0.0)


func test_control_panel_hardware_fits_on_its_printed_panel() -> void:
	# An upright's panel is 20.4 wide; the joystick's plate is 4.6 across, a button's collar 2.3.
	var w := 20.4
	var stick_left := MachineKit.PANEL_STICK_U * w - 2.3
	var stick_right := MachineKit.PANEL_STICK_U * w + 2.3
	var first_button_left := MachineKit.PANEL_BUTTON_U * w - 1.15
	var last_button_right := (MachineKit.PANEL_BUTTON_U + 2 * MachineKit.PANEL_BUTTON_STEP_U + MachineKit.PANEL_BUTTON_ROW_SHIFT_U) * w + 1.15
	assert_true(stick_left > 0.0, "the joystick runs off the panel's left")
	assert_true(stick_right < first_button_left, "the joystick overlaps the first button")
	assert_true(last_button_right < w, "the buttons run off the panel's right")
	assert_true(MachineKit.PANEL_BUTTON_STEP_U * w > 2.3, "neighbouring buttons overlap")
	# The two rows sit 8 deep panel units apart at most; their collars must clear each other.
	assert_true((MachineKit.PANEL_ROW2_V - MachineKit.PANEL_ROW_V) * 8.0 > 2.3, "the rows overlap")


## Godot-only: the copies of a bank share one model (placed where each stands) and keep only their
## own parts apart, and each copy still draws exactly what it built.
func test_a_banks_copies_share_their_model_and_draw_what_they_built() -> void:
	var games := HallGames.games()
	var map: HubMap = HallGames.maps()[0][1]
	for gi in games.size():
		var g := games[gi]
		if g.cabinet() is HallGames.SizedDesign:
			continue
		var art := MachineArt.new(g)
		var units: Array[MachineUnit] = []
		var alone: Array[Model] = []
		for p: Prop in map.props:
			if p.kind == PropKind.MACHINE and p.machine == gi:
				units.append(MachineUnit.new(p, g, art))
				alone.append(MachineUnit.new(p, g, art).model)
		var shared := MachineUnit.share_bank(units)
		assert_eq(units.size(), shared, "%s: every copy shares" % g.id)
		for i in units.size():
			var u := units[i]
			assert_true(u.model == units[0].model, "%s: one shared model" % g.id)
			var whole := u.full_model()
			assert_eq(alone[i].polys.size(), whole.polys.size(), "%s copy %d keeps every polygon" % [g.id, i])
			assert_near(alone[i].min_x, whole.min_x, 2e-3, "%s copy %d stands where it did" % [g.id, i])
			assert_near(alone[i].max_x, whole.max_x, 2e-3)
			assert_near(alone[i].max_z, whole.max_z, 2e-3)
			if u.screen != null:
				assert_not_null(u.own, "%s: the live screen is each copy's own" % g.id)
