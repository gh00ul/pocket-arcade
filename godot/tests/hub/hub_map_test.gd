extends PaTest
## hub/HubMapTest.kt: checks the real hall floor plan, built from every registered machine, with and
## without every decoration bought: nothing overlaps, every aisle is at least as wide as the
## narrowest one in the first floor plan, and the kids' walk grid reaches every prompt, counter and
## hangout. It also holds the plan to how an arcade is laid out: a wide main aisle straight from the
## doors to the prize counter, the token kiosk just inside the doors, rows of banks that face a
## cross aisle rather than the back of the next bank, nothing hiding a bank's players from the hall
## camera, and a café that opens onto the main aisle. The machines are build-13's eleven
## ([HallGames]: the ported ones, stand-ins for the rest).

## The narrowest aisle between fixtures in the first floor plan: the diagonal squeeze between a
## foyer pillar and a kiddie ride (13 across, 17 along, 21.4 corner to corner).
const MIN_WALKWAY := 21.0
## Gaps up to this are closed, not aisles: cabinets side by side in a bank, a machine backed up to a
## wall. Nobody walks through them (a kid needs 12 of clearance).
const CLOSED_GAP := 12.0
## The main aisle is at least as wide as the doors.
const MAIN_AISLE := HubLayout.DOOR_X1 - HubLayout.DOOR_X0
## How far the token kiosk's spot may be from the middle of the doors.
const KIOSK_REACH := 120.0
## Clear floor between a bank's fronts and the backs of a bank in front of it.
const FACING_GAP := 80.0
## The hall camera's pitch (degrees below the horizon) when it follows the player.
const CAMERA_PITCH := 55.0

var games: Array[MiniGame]
var maps: Array


func before_each() -> void:
	games = HallGames.games()
	maps = HallGames.maps()


static func is_wall(b: Box) -> bool:
	return b.left <= 0.0 or b.top <= 0.0 or b.right >= HubLayout.WIDTH or b.bottom >= HubLayout.DEPTH


static func s(b: Box) -> String:
	return "(%s, %s)-(%s, %s)" % [b.left, b.top, b.right, b.bottom]


static func overlaps(a: Box, b: Box) -> bool:
	return a.intersects(b.left, b.top, b.right, b.bottom)


## Clear distance between two boxes: straight across when they face each other, else corner to corner.
static func gap(a: Box, b: Box) -> float:
	var dx := maxf(maxf(b.left - a.right, a.left - b.right), 0.0)
	var dz := maxf(maxf(b.top - a.bottom, a.top - b.bottom), 0.0)
	return Vector2(dx, dz).length() if dx > 0.0 and dz > 0.0 else maxf(dx, dz)


## The empty rectangle between two separated boxes.
static func between(a: Box, b: Box) -> Box:
	var sep_x := a.right <= b.left or b.right <= a.left
	var sep_z := a.bottom <= b.top or b.bottom <= a.top
	var x0 := minf(a.right, b.right) if sep_x else maxf(a.left, b.left)
	var x1 := maxf(a.left, b.left) if sep_x else minf(a.right, b.right)
	var z0 := minf(a.bottom, b.bottom) if sep_z else maxf(a.top, b.top)
	var z1 := maxf(a.top, b.top) if sep_z else minf(a.bottom, b.bottom)
	return Box.new(minf(x0, x1), minf(z0, z1), maxf(x0, x1), maxf(z0, z1))


## Breadth-first flood of the kids' walk grid from the spawn tile.
func reachable(map: HubMap) -> PackedByteArray:
	var seen := PackedByteArray()
	seen.resize(map.cols * map.rows)
	var sx := int(map.spawn_x / HubLayout.TILE)
	var sy := int((map.spawn_y - 4.0) / HubLayout.TILE)
	assert_true(map.tile_walkable(sx, sy), "the spawn tile isn't walkable")
	var queue := PackedInt32Array()
	queue.resize(seen.size())
	var head := 0
	var tail := 0
	queue[tail] = sy * map.cols + sx
	tail += 1
	seen[sy * map.cols + sx] = 1
	while head < tail:
		var cur := queue[head]
		head += 1
		var cx := cur % map.cols
		var cy := cur / map.cols
		for d in 4:
			var nx := cx + (1 if d == 0 else (-1 if d == 1 else 0))
			var ny := cy + (1 if d == 2 else (-1 if d == 3 else 0))
			if not map.tile_walkable(nx, ny):
				continue
			var ni := ny * map.cols + nx
			if seen[ni] != 0:
				continue
			seen[ni] = 1
			queue[tail] = ni
			tail += 1
	return seen


func test_nothing_overlaps() -> void:
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		var sl := map.solids
		for i in sl.size():
			for j in range(i + 1, sl.size()):
				if is_wall(sl[i]) and is_wall(sl[j]):
					continue
				assert_false(overlaps(sl[i], sl[j]), "%s: %s overlaps %s" % [name, s(sl[i]), s(sl[j])])


func test_everything_stays_inside_the_walls() -> void:
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		for p: Prop in map.props:
			if p.kind == PropKind.DOORS:
				continue
			assert_true(p.x0 >= HubLayout.WALL and p.x1 <= HubLayout.WIDTH - HubLayout.WALL and
					p.z0 >= HubLayout.BACK_WALL and p.z1 <= HubLayout.FRONT_WALL,
				"%s: %s at (%s, %s)-(%s, %s) pokes through a wall" % [name, PropKind.name_of(p.kind), p.x0, p.z0, p.x1, p.z1])


func test_aisles_are_at_least_as_wide_as_before() -> void:
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		var sl := map.solids
		for i in sl.size():
			for j in range(i + 1, sl.size()):
				var a := sl[i]
				var b := sl[j]
				if is_wall(a) and is_wall(b):
					continue
				var g := gap(a, b)
				if g <= CLOSED_GAP or g >= MIN_WALKWAY:
					continue
				var r := between(a, b)
				var blocked := false
				for k in sl.size():
					if k != i and k != j and overlaps(sl[k], r):
						blocked = true
						break
				if not blocked:
					fail("%s: a %.1f-wide squeeze between %s and %s (aisles need %s)" % [name, g, s(a), s(b), MIN_WALKWAY])


func test_the_spawn_point_is_clear() -> void:
	for entry: Array in maps:
		var map: HubMap = entry[1]
		assert_false(Collision.blocked(map.solids, map.spawn_x, map.spawn_y), "%s: spawn is inside something" % entry[0])


func test_every_prompt_is_reachable_from_the_door() -> void:
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		var seen := reachable(map)
		for spot: Spot in map.spots:
			var ok := false
			for ty in map.rows:
				for tx in map.cols:
					if seen[ty * map.cols + tx] == 0:
						continue
					var cx := tx * HubLayout.TILE + HubLayout.TILE / 2.0
					var cy := ty * HubLayout.TILE + HubLayout.TILE / 2.0
					if spot.area.contains(cx, cy):
						ok = true
			var what := games[spot.machine].id if spot.type == SpotType.MACHINE else SpotType.name_of(spot.type)
			assert_true(ok, "%s: can't walk to the %s spot at %s" % [name, what, s(spot.area)])


func test_every_hangout_is_reachable() -> void:
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		var seen := reachable(map)
		for h: Hangout in map.hangouts:
			var tx := h.tile_x
			var ty := h.tile_y
			assert_true(map.tile_walkable(tx, ty) and seen[ty * map.cols + tx] != 0, "%s: kids can't reach the hangout at (%s, %s)" % [name, h.x, h.z])
			var far := Vector2(tx * HubLayout.TILE + HubLayout.TILE / 2.0 - h.x, ty * HubLayout.TILE + HubLayout.TILE / 2.0 - h.z).length()
			assert_true(far < 2.0 * HubLayout.TILE, "%s: the hangout at (%s, %s) is %s from its tile" % [name, h.x, h.z, far])


func test_every_machine_has_a_cabinet_and_a_prompt() -> void:
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		for i in games.size():
			var cab := false
			for p: Prop in map.props:
				if p.kind == PropKind.MACHINE and p.machine == i:
					cab = true
			var prompt := false
			for sp: Spot in map.spots:
				if sp.type == SpotType.MACHINE and sp.machine == i:
					prompt = true
			assert_true(cab, "%s: %s has no cabinet" % [name, games[i].id])
			assert_true(prompt, "%s: %s has no prompt" % [name, games[i].id])


func test_every_cabinet_fits_its_bank() -> void:
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		for i in games.size():
			var g := games[i]
			var slot := map.machine_slots[i]
			var d := g.cabinet() as CabinetDesign
			if d != null:
				assert_true(d.width <= slot.max_w and d.depth <= slot.max_d and d.height <= slot.max_h,
					"%s: %s's design %s x %s x %s doesn't fit its bank's %s x %s x %s" % [name, g.id, d.width, d.depth, d.height, slot.max_w, slot.max_d, slot.max_h])
			for p: Prop in map.props:
				if p.kind != PropKind.MACHINE or p.machine != i:
					continue
				assert_true(p.x0 >= slot.x0 and p.x1 <= slot.x1 and p.z0 >= slot.back and p.z1 <= slot.back + slot.max_d,
					"%s: a %s cabinet sticks out of its bank" % [name, g.id])


static func count_kind(map: HubMap, kind: int) -> int:
	var n := 0
	for p: Prop in map.props:
		if p.kind == kind:
			n += 1
	return n


static func single_prop(map: HubMap, kind: int) -> Prop:
	var found: Prop = null
	for p: Prop in map.props:
		if p.kind == kind:
			if found != null:
				return null
			found = p
	return found


func test_the_cafe_is_furnished() -> void:
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		assert_true(count_kind(map, PropKind.CAFE_COUNTER) == 1 and count_kind(map, PropKind.CAFE_BAR) == 1, "%s: the café needs one counter and one back bar" % name)
		var tables := count_kind(map, PropKind.CAFE_TABLE)
		assert_true(tables >= 4 and tables <= 6, "%s: the café wants 4 to 6 tables" % name)
		assert_true(count_kind(map, PropKind.BOOTH) >= 1, "%s: the café wants a booth" % name)
		var seats := 0
		for h: Hangout in map.hangouts:
			if h.cafe:
				seats += 1
		assert_true(seats >= 8, "%s: the café has too few seats" % name)
		assert_true(map.cafe_queue.size() >= 3, "%s: the café queue is too short" % name)


func test_the_cafe_queue_is_reachable_and_clear() -> void:
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		var seen := reachable(map)
		for q: Hangout in map.cafe_queue:
			assert_true(map.tile_walkable(q.tile_x, q.tile_y) and seen[q.tile_y * map.cols + q.tile_x] != 0, "%s: kids can't reach the queue spot at (%s, %s)" % [name, q.x, q.z])
			var far := Vector2(q.tile_x * HubLayout.TILE + HubLayout.TILE / 2.0 - q.x, q.tile_y * HubLayout.TILE + HubLayout.TILE / 2.0 - q.z).length()
			assert_true(far < 2.0 * HubLayout.TILE, "%s: the queue spot at (%s, %s) is %s from its tile" % [name, q.x, q.z, far])
			assert_false(Collision.blocked(map.solids, q.x, q.z), "%s: the queue spot at (%s, %s) is inside something" % [name, q.x, q.z])
		# Café seats are entered from a walkable tile close by.
		for h: Hangout in map.hangouts:
			if h.cafe:
				assert_true(map.tile_walkable(h.tile_x, h.tile_y) and seen[h.tile_y * map.cols + h.tile_x] != 0, "%s: the café seat at (%s, %s) has no way in" % [name, h.x, h.z])


func test_the_barista_lane_is_closed_to_the_crowd() -> void:
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		var counter := single_prop(map, PropKind.CAFE_COUNTER).foot
		var x := CafeLayout.LANE_X0
		while x <= CafeLayout.LANE_X1:
			assert_true(counter.contains(x, CafeLayout.LANE_Z), "%s: the barista's lane at x %s isn't behind the counter" % [name, x])
			var tx := int(x / HubLayout.TILE)
			var ty := int(CafeLayout.LANE_Z / HubLayout.TILE)
			assert_false(map.tile_walkable(tx, ty), "%s: kids can wander behind the counter at x %s" % [name, x])
			x += 8.0


func test_the_cafe_keeps_to_its_light_budget_and_leaves_the_spare_banks_free() -> void:
	var lights: Array[PointLight] = []
	CafeLayout.lights(lights)
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		var vending := count_kind(map, PropKind.VENDING)
		assert_true(lights.size() + vending <= 4, "%s: the café brings %d point lights (keep it to 4)" % [name, lights.size() + vending])
		var cafe_kinds := [PropKind.CAFE_BAR, PropKind.CAFE_COUNTER, PropKind.CAFE_TABLE, PropKind.BOOTH, PropKind.CHAIR, PropKind.VENDING]
		for slot: Slot in HubLayout.slots:
			for p: Prop in map.props:
				if not cafe_kinds.has(p.kind):
					continue
				var box := Box.new(p.x0, p.z0, p.x1, p.z1)
				assert_false(overlaps(box, slot.area), "%s: café %s at %s is in a bank's floor %s" % [name, PropKind.name_of(p.kind), s(box), s(slot.area)])


func test_the_main_aisle_runs_clear_from_the_doors_to_the_prize_counter() -> void:
	assert_true(HubLayout.AISLE_X1 - HubLayout.AISLE_X0 >= MAIN_AISLE, "the main aisle is narrower than the doors")
	assert_true(HubLayout.AISLE_X0 <= HubLayout.DOOR_X0 and HubLayout.AISLE_X1 >= HubLayout.DOOR_X1, "the main aisle doesn't line up with the doors")
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		var counter := single_prop(map, PropKind.COUNTER)
		var prizes: Spot = null
		var n := 0
		for sp: Spot in map.spots:
			if sp.type == SpotType.PRIZES:
				prizes = sp
				n += 1
		assert_eq(1, n, "%s: one prize spot" % name)
		assert_true(prizes.area.left < HubLayout.AISLE_X1 and prizes.area.right > HubLayout.AISLE_X0, "%s: the prize counter isn't at the end of the main aisle" % name)
		var aisle := Box.new(HubLayout.AISLE_X0, counter.front_z, HubLayout.AISLE_X1, HubLayout.FRONT_WALL)
		for b: Box in map.solids:
			assert_false(overlaps(b, aisle), "%s: %s stands in the main aisle %s" % [name, s(b), s(aisle)])
		# Nobody plays a machine standing in the main aisle.
		for spot: Spot in map.spots:
			if spot.type != SpotType.MACHINE:
				continue
			assert_false(overlaps(spot.area, aisle), "%s: %s's play spot %s is in the main aisle" % [name, games[spot.machine].id, s(spot.area)])


func test_the_token_kiosk_is_just_inside_the_doors() -> void:
	var door_x := (HubLayout.DOOR_X0 + HubLayout.DOOR_X1) / 2.0
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		var tokens: Spot = null
		var n := 0
		for sp: Spot in map.spots:
			if sp.type == SpotType.TOKENS:
				tokens = sp
				n += 1
		assert_eq(1, n, "%s: one token spot" % name)
		var d := Vector2(tokens.area.center_x - door_x, tokens.area.center_y - HubLayout.FRONT_WALL).length()
		assert_true(d <= KIOSK_REACH, "%s: the token kiosk is %.0f from the doors (keep it within %s)" % [name, d, KIOSK_REACH])


## Pairs of banks (by the floor they reserve) where front stands ahead of rear across some of the
## same x: [[rear, front], ...].
static func rows_in_line() -> Array:
	var out: Array = []
	var sl := HubLayout.slots
	for a: Slot in sl:
		for b: Slot in sl:
			if a == b or a.x0 >= b.x1 or b.x0 >= a.x1:
				continue
			if b.back >= a.back + a.max_d:
				out.append([a, b])
	return out


static func shape_name(slot: Slot) -> String:
	if slot.shape == Slot.SPARE:
		return "spare"
	return MiniGame.CabinetShape.keys()[slot.shape]


func test_no_bank_faces_the_back_of_another_up_close() -> void:
	for pair: Array in rows_in_line():
		var rear: Slot = pair[0]
		var front: Slot = pair[1]
		var g := front.back - (rear.back + rear.max_d)
		assert_true(g >= FACING_GAP, "the %s bank's back is %.0f in front of the %s bank (keep %s)" % [shape_name(front), g, shape_name(rear), FACING_GAP])


func test_no_bank_hides_the_players_behind_it_from_the_hall_camera() -> void:
	var reach := 1.0 / tan(deg_to_rad(CAMERA_PITCH))
	for pair: Array in rows_in_line():
		var rear: Slot = pair[0]
		var front: Slot = pair[1]
		var shadow := front.back - front.max_h * reach
		var spots := rear.back + rear.max_d + HubLayout.PROMPT_DEPTH
		assert_true(shadow >= spots, "the %s bank hides the %s bank's play spots from the hall camera" % [shape_name(front), shape_name(rear)])


func test_the_cafe_opens_onto_the_main_aisle() -> void:
	var mid := (HubLayout.AISLE_X0 + HubLayout.AISLE_X1) / 2.0
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		var seen := reachable(map)
		# Some rows of tiles run clear from inside the café to the middle of the main aisle.
		var tx0 := int(CafeLayout.FLOOR_X1 / HubLayout.TILE) - 1
		var tx1 := int(mid / HubLayout.TILE)
		var open := 0
		for ty in range(int(CafeLayout.FLOOR_Z0 / HubLayout.TILE), int(CafeLayout.FLOOR_Z1 / HubLayout.TILE)):
			var all_clear := true
			for tx in range(tx0, tx1 + 1):
				if not (map.tile_walkable(tx, ty) and seen[ty * map.cols + tx] != 0):
					all_clear = false
					break
			if all_clear:
				open += 1
		assert_true(open >= 3, "%s: the café is walled off from the main aisle" % name)
		# The queue runs from the till out towards the aisle, not deeper into the café.
		var q := map.cafe_queue
		assert_true(q.back().x > q.front().x, "%s: the café queue runs away from the aisle" % name)
		# And the café is a corner of its own: no bank's floor in it.
		var cafe := Box.new(CafeLayout.FLOOR_X0, CafeLayout.FLOOR_Z0, CafeLayout.FLOOR_X1, CafeLayout.FLOOR_Z1)
		for slot: Slot in HubLayout.slots:
			assert_false(overlaps(slot.area, cafe), "%s: a bank's floor %s is in the café" % [name, s(slot.area)])


func test_the_zone_signs_hang_over_their_banks() -> void:
	for sg: WallSign in HubLayout.wall_signs:
		assert_true(sg.z0 >= HubLayout.BACK_WALL and sg.z1 <= HubLayout.FRONT_WALL and sg.z0 < sg.z1, "the %s sign runs off the wall" % sg.text)
		if sg.shape < 0:
			continue
		var slot: Slot = null
		for sl: Slot in HubLayout.slots:
			if sl.shape == sg.shape:
				slot = sl
				break
		var wall_side := slot.x1 >= HubLayout.WIDTH - HubLayout.WALL - 16.0 if sg.right else slot.x0 <= HubLayout.WALL + 16.0
		assert_true(wall_side, "the %s sign isn't on the wall its bank stands against" % sg.text)
		assert_true(sg.z0 < slot.back + slot.max_d and sg.z1 > slot.back, "the %s sign isn't over its bank" % sg.text)
	# Posters (64 to 100 up the wall) stay clear of any sign that comes down into them.
	for sg: WallSign in HubLayout.wall_signs:
		var posters := HubLayout.right_posters if sg.right else HubLayout.left_posters
		for z: float in posters:
			assert_false(z + 12.0 > sg.z0 and z - 12.0 < sg.z1 and sg.y0 < 100.0, "a poster at z %s runs into the %s sign" % [z, sg.text])


## A do-nothing machine for probing the floor plan's limits (Kotlin's Probe).
static func probe(shape: int, design: CabinetDesign = null) -> MiniGame:
	return HallGames.StandIn.new("probe", "PROBE", "PROBE", MiniGame.CabinetLook.new(0, 0, 0, shape), design)


func build_fails(list: Array[MiniGame]) -> bool:
	var none: Array[int] = []
	return HubLayout.build(list, none) == null


func test_machines_take_spare_banks_then_fail_loudly() -> void:
	expect_error("No room on the hall floor for 'probe'")
	var spares := 0
	for sl: Slot in HubLayout.slots:
		if sl.shape == Slot.SPARE:
			spares += 1
	assert_true(spares >= 2, "keep at least two spare banks for new machines")
	# Machines without a bank of their own take the spares, one each...
	var some: Array[MiniGame] = games.duplicate()
	for i in spares:
		some.append(probe(MiniGame.CabinetShape.UPRIGHT))
	assert_false(build_fails(some))
	# ...and one more has nowhere to go: the build fails rather than overlap anything.
	some.append(probe(MiniGame.CabinetShape.UPRIGHT))
	assert_true(build_fails(some))
	assert_true(HubLayout.failure.contains("No room on the hall floor"), HubLayout.failure)


class HugeDesign:
	extends CabinetDesign

	func _init() -> void:
		width = 500.0
		depth = 40.0
		height = 60.0
		focus_height = 40.0
		focus_set_back = 6.0
		screen_units = null


func test_a_cabinet_too_big_for_its_bank_fails_the_build() -> void:
	expect_error("doesn't fit its slot's")
	var list: Array[MiniGame] = [probe(MiniGame.CabinetShape.UPRIGHT, HugeDesign.new())]
	assert_true(build_fails(list))


func test_slots_are_kept_clear() -> void:
	var sl := HubLayout.slots
	for i in sl.size():
		for j in range(i + 1, sl.size()):
			assert_false(overlaps(sl[i].area, sl[j].area), "slots %d and %d overlap" % [i, j])
	# Nothing but a slot's own cabinets may stand in the floor it reserves.
	for entry: Array in maps:
		var name: String = entry[0]
		var map: HubMap = entry[1]
		for slot: Slot in sl:
			var area := slot.area
			for p: Prop in map.props:
				var foot := p.foot
				if foot == null:
					continue
				if p.kind == PropKind.MACHINE and area.contains(p.center_x, p.center_z):
					continue
				assert_false(overlaps(foot, area), "%s: %s at %s stands in a bank's floor %s" % [name, PropKind.name_of(p.kind), s(foot), s(area)])
