class_name HubLayout
extends RefCounted
## hub/HubMap.kt HubLayout: the arcade's floor plan. A wide main aisle runs straight from the doors
## to the prize counter, with zones either side of it in rows across the hall, each row's banks
## facing the doors across a cross aisle: the prize games along the back wall either side of the
## counter, the ticket alleys and coin pushers, the table games, the video games, then the family
## floor by the entrance with the fishing tubs, the café in the front-left corner, kiddie rides, the
## photo booth and the token kiosk just inside the doors. The spare banks sit where the next
## machines would go: beside the pinball tables and at the front of the family floor.
##
## Kotlin's overloads are split: [method cabinet_size] (a shape) and [method cabinet_size_for] (a
## game), [method focus] and [method focus_for]. Sizes come back as Vector3(width, depth, height),
## focus points as Vector2(height, set back). Kotlin's `error()`/`check()` (IllegalStateException)
## become [method _fail]: the error is pushed (loudly) and recorded in [member failure], and
## [method build] / [method stand_area] return null.

const TILE := 16
const WIDTH := 608
const DEPTH := 1100
const WALL := 16.0
const BACK_WALL := 24.0
const FRONT_WALL := 1080.0
const WALL_HEIGHT := 150.0
## The walls carry on up into the dark to here.
const CEILING := 380.0
const DOOR_X0 := 264.0
const DOOR_X1 := 344.0
## How far in front of a cabinet its play spot reaches.
const PROMPT_DEPTH := 26.0
## An interactive prop's spot is never narrower than this (half width), and never wider than
## [constant MAX_STAND_HALF], however wide the prop: a long counter gets a spot the size of a till.
const MIN_STAND_HALF := 12.0
const MAX_STAND_HALF := 22.0
## The least depth a prop's spot may be cut down to by something standing in front of it: enough
## for the body to stop at HubWorld.STAND_DEPTH and still be inside it.
const MIN_STAND_DEPTH := 22.0
## The main aisle: nothing stands between these x from the doors to the prize counter, and the play
## spots in front of the banks either side keep to their own side of it.
const AISLE_X0 := 256.0
const AISLE_X1 := 352.0

const S := MiniGame.CabinetShape
const D := Catalog.DecorStyle

## The message of the last failed build or stand_area (Kotlin's IllegalStateException), else "".
static var failure := ""

## Every bank on the floor, row by row from the back wall, left of the main aisle then right. Rows
## leave a cross aisle of at least 80 between one row's fronts and the next row's backs, and nothing
## tall stands close enough in front of a row to hide its players from the hall camera. The spare
## banks are pre-sized for machines still to come: a cabinet must fit its cell, or the build fails
## rather than overlap its neighbours. A new machine takes the first spare in this list first.
static var slots: Array[Slot] = []

## Neon zone signs on the side walls.
static var wall_signs: Array[WallSign] = []

## Where the posters hang along the left and right walls (centre z), clear of the signs and the tall banks.
static var left_posters := PackedFloat32Array([320.0, 470.0, 625.0, 1030.0])
static var right_posters := PackedFloat32Array([290.0, 480.0, 630.0, 800.0, 900.0])

## Spots for bought decorations, in build-13's order: [style, centre x, front z].
static var _decor_spots: Array = []

## Pillars (centres), in pairs flanking the main aisle through the middle of the hall.
static var _pillars := PackedFloat32Array([238.0, 545.0, 370.0, 545.0, 238.0, 700.0, 370.0, 700.0])

## Standing spots in the aisles: the prize counter, the cross aisles, the entrance.
static var _aisle_spots := PackedFloat32Array([
	304.0, 150.0, 200.0, 305.0, 440.0, 280.0, 304.0, 470.0, 190.0, 470.0, 440.0, 470.0,
	200.0, 632.0, 420.0, 632.0, 304.0, 780.0, 430.0, 930.0, 330.0, 960.0,
])


static func _static_init() -> void:
	slots = [
		# Prize games along the back wall, either side of the prize counter.
		_bank(S.CLAW, 4, 26.0, 34.0, 4.0),
		_bank(S.TOWER, 3, 488.0, 34.0, 4.0),
		# Ticket alleys: skee-ball lanes along the left wall with the basketball alleys beside them,
		# fronts in line; the coin pushers across the aisle against the right wall.
		_bank(S.SKEEBALL, 4, 24.0, 150.0, 2.0),
		_bank(S.HOOPS, 3, 140.0, 160.0, 2.0),
		Slot.new(S.PUSHER, 4, 412.0, 150.0, 4.0, 40.0, 38.0, 66.0, 1),
		# Table games: whack-a-moles by the left wall, the air hockey tables by the aisle; the pinball
		# tables against the right wall with a spare pair of cells beside them.
		Slot.new(S.WHACK, 3, 24.0, 374.0, 6.0, 34.0, 32.0, 62.0, -1),
		_bank(S.AIR_HOCKEY, 2, 146.0, 340.0, 30.0),
		Slot.new(S.PINBALL, 4, 452.0, 346.0, 4.0, 30.0, 60.0, 78.0, 1),
		# Video games, fronts in line across the aisle: the linked racers (their own cabinet design,
		# so the cells leave it room to grow) and the light-gun cabinets.
		Slot.new(S.RACER, 4, 24.0, 516.0, 2.0, 34.0, 58.0, 72.0, -1),
		Slot.new(S.GUN, 3, 440.0, 534.0, 6.0, 44.0, 40.0, 86.0, 1),
		# The family floor: two big fishing tubs by the café, a spare bank across the aisle.
		Slot.new(S.FISHING, 2, 24.0, 670.0, 24.0, 64.0, 64.0, 64.0, -1),
		Slot.new(Slot.SPARE, 3, 452.0, 674.0, 6.0, 40.0, 60.0, 86.0, 1),
		Slot.new(Slot.SPARE, 2, 360.0, 346.0, 6.0, 40.0, 60.0, 86.0, -1),
	]
	wall_signs = [
		WallSign.new("SKEE-BALL", 0xFFFFD84D, false, 200.0, 300.0),
		WallSign.new("JACKPOT", 0xFFFFB03D, true, 150.0, 250.0, 100.0, 124.0, 512, 104.0),
		WallSign.new("PINBALL", 0xFFFF77C8, true, 360.0, 450.0, 100.0, 124.0, 512, 100.0, S.PINBALL),
		WallSign.new("FISHING", 0xFF4DA6FF, false, 690.0, 790.0, 100.0, 124.0, 512, 100.0, S.FISHING),
		WallSign.new("SNACK BAR", 0xFF5CF08A, false, CafeLayout.FLOOR_Z0 + 86.0, CafeLayout.FLOOR_Z0 + 206.0, 92.0, 116.0),
	]
	_decor_spots = [
		# Back wall, either side of the prize counter.
		[D.TROPHY_CASE, 182.0, 42.0],
		[D.PLUSH_BEAR, 442.0, 92.0],
		# In the café, against the wall between the booths and the snack machine. Its front is clear
		# of the lava lamp's corner, so there's room to stand and pick a song.
		[D.JUKEBOX, 34.0, 1030.0],
		# At the right-wall end of the cross aisle between the table and video games.
		[D.FISH_TANK, 562.0, 486.0],
		# In the café's front corner, by the window.
		[D.LAVA_LAMP, 26.0, 1070.0],
		# Round the entrance: a palm left of the doors, the flamingo by the front-right plant, the
		# gumball machine beside the change machine.
		[D.PALM, 234.0, 1070.0],
		[D.FLAMINGO, 556.0, 1070.0],
		[D.GUMBALL, 450.0, 1014.0],
	]


## Width, depth and height of each cabinet (Vector3(w, d, h)).
static func cabinet_size(shape: int) -> Vector3:
	match shape:
		S.UPRIGHT:
			return Vector3(24, 28, 58)
		S.WIDE:
			return Vector3(36, 34, 62)
		S.LANE:
			return Vector3(28, 92, 58)
		S.TABLE:
			return Vector3(36, 64, 58)
		S.CLAW:
			return Vector3(30, 30, 68)
		S.WHACK:
			return Vector3(34, 32, 62)
		S.SKEEBALL:
			return Vector3(26, 100, 60)
		S.HOOPS:
			return Vector3(36, 90, 82)
		S.PUSHER:
			return Vector3(40, 38, 66)
		S.AIR_HOCKEY:
			return Vector3(36, 66, 62)
		S.RACER:
			return Vector3(32, 50, 62)
		S.TOWER:
			return Vector3(28, 26, 76)
		# These machines bring their own design; the sizes are for the stand-in cabinet.
		S.GUN:
			return Vector3(40, 36, 80)
		S.PINBALL:
			return Vector3(28, 54, 70)
		S.FISHING:
			return Vector3(60, 60, 56)
	return Vector3(24, 28, 58)


## Width, depth and height of [param game]'s cabinet: its own design's, else its shape's.
static func cabinet_size_for(game: MiniGame) -> Vector3:
	var d := game.cabinet() as CabinetDesign
	if d != null:
		return Vector3(d.width, d.depth, d.height)
	return cabinet_size(game.look.shape)


## The camera dive point for [param game]'s cabinet: its own design's, else its shape's.
static func focus_for(game: MiniGame) -> Vector2:
	var d := game.cabinet() as CabinetDesign
	if d != null:
		return Vector2(d.focus_height, d.focus_set_back)
	return focus(game.look.shape)


## Where the camera flies to when entering: height and how far behind the cabinet front.
static func focus(shape: int) -> Vector2:
	var d := cabinet_size(shape).y
	match shape:
		S.CLAW:
			return Vector2(38, 3)
		S.TOWER:
			return Vector2(48, 4)
		S.UPRIGHT, S.GUN:
			return Vector2(40, 6)
		S.WIDE:
			return Vector2(38, 4)
		S.PUSHER:
			return Vector2(36, 6)
		S.WHACK:
			return Vector2(44, d - 4.0)
		S.RACER:
			return Vector2(42, d - 10.0)
		S.SKEEBALL:
			return Vector2(36, d - 10.0)
		S.HOOPS:
			return Vector2(56, d - 10.0)
		S.LANE:
			return Vector2(34, d - 10.0)
	# AIR_HOCKEY, TABLE, PINBALL, FISHING
	return Vector2(46, d - 2.0)


## A bank whose cells fit [param shape]'s cabinet exactly.
static func _bank(shape: int, count: int, x0: float, back: float, gap: float) -> Slot:
	var s := cabinet_size(shape)
	return Slot.new(shape, count, x0, back, gap, s.x, s.y, s.z)


## Kotlin's `error()` / `check()`: reports [param msg] loudly and remembers it.
static func _fail(msg: String) -> void:
	failure = msg
	push_error(msg)


## The floor plan for [param games] (in registry order) with the bought decorations
## [param owned_decor] (Catalog.DecorStyle values). Null (and a pushed error, see [member failure])
## when a machine has no bank left or its cabinet doesn't fit its bank's cells, or a prop has no
## room to stand in front of it.
static func build(games: Array[MiniGame], owned_decor: Array[int]) -> HubMap:
	failure = ""
	var w := WIDTH
	var h := DEPTH
	var props: Array[Prop] = []
	var spots: Array[Spot] = []
	var solids: Array[Box] = []
	var hangouts: Array[Hangout] = []

	# Walls, with the entrance gap in the front wall.
	solids.append(Box.new(0.0, 0.0, float(w), BACK_WALL))
	solids.append(Box.new(0.0, 0.0, WALL, float(h)))
	solids.append(Box.new(w - WALL, 0.0, float(w), float(h)))
	solids.append(Box.new(0.0, FRONT_WALL, DOOR_X0, float(h)))
	solids.append(Box.new(DOOR_X1, FRONT_WALL, float(w), float(h)))
	solids.append(Box.new(DOOR_X0, FRONT_WALL + 12.0, DOOR_X1, float(h)))

	# Prize counter with the prize wall behind it and room for the clerk.
	props.append(Prop.new(PropKind.PRIZE_WALL, 200.0, BACK_WALL, 408.0, 40.0, 104.0))
	props.append(Prop.new(PropKind.COUNTER, 224.0, 70.0, 384.0, 94.0, 26.0))
	solids.append(Box.new(200.0, 40.0, 224.0, 94.0))
	solids.append(Box.new(384.0, 40.0, 408.0, 94.0))
	spots.append(Spot.new(SpotType.PRIZES, -1, Box.new(262.0, 94.0, 346.0, 124.0), 304.0, 52.0, 90.0, 304.0, 34.0, 84.0))

	# Machines, bank by bank. A game whose shape has no bank (or whose bank another game already
	# took) gets the next spare slot; when none is left the build fails loudly.
	var used := PackedByteArray()
	used.resize(slots.size())
	var machine_slots: Array[Slot] = []
	for index in games.size():
		var g: MiniGame = games[index]
		var shape: int = g.look.shape
		var si := -1
		for i in slots.size():
			if used[i] == 0 and slots[i].shape == shape:
				si = i
				break
		if si < 0:
			for i in slots.size():
				if used[i] == 0 and slots[i].shape == Slot.SPARE:
					si = i
					break
		if si < 0:
			_fail("No room on the hall floor for '%s': every bank and spare slot is taken. Add a Slot to HubLayout.slots." % g.id)
			return null
		used[si] = 1
		var slot := slots[si]
		machine_slots.append(slot)
		var size := cabinet_size_for(g)
		var cw := size.x
		var cd := size.y
		var ch := size.z
		if not (cw <= slot.max_w and cd <= slot.max_d and ch <= slot.max_h):
			_fail("'%s' cabinet %s x %s x %s doesn't fit its slot's %s x %s x %s cells" % [g.id, cw, cd, ch, slot.max_w, slot.max_d, slot.max_h])
			return null
		for k in slot.count:
			var x0 := slot.cabinet_x(k, cw)
			_add_machine(props, spots, hangouts, index, shape, x0, slot.back, cw, cd, ch, focus_for(g), k)

	# Pillars flanking the main aisle.
	for i in _pillars.size() / 2:
		var px := _pillars[i * 2]
		var pz := _pillars[i * 2 + 1]
		props.append(Prop.new(PropKind.PILLAR, px - 9.0, pz - 9.0, px + 9.0, pz + 9.0, WALL_HEIGHT))

	# The café in the front-left corner, open to the main aisle: counter, booths, tables, vending
	# machines.
	var cafe_queue: Array[Hangout] = []
	CafeLayout.add(props, hangouts, cafe_queue)

	# The family floor right of the doors: kiddie rides with a bench beside them for the grown-ups,
	# and the photo booth against the wall.
	props.append(Prop.new(PropKind.KIDDIE_RIDE, 452.0, 830.0, 480.0, 862.0, 46.0, -1, Catalog.NONE, true, S.UPRIGHT, 0))
	props.append(Prop.new(PropKind.KIDDIE_RIDE, 506.0, 830.0, 534.0, 862.0, 30.0, -1, Catalog.NONE, true, S.UPRIGHT, 1))
	props.append(Prop.new(PropKind.BENCH, 470.0, 912.0, 540.0, 926.0, 16.0))
	props.append(Prop.new(PropKind.PHOTO_BOOTH, 534.0, 960.0, 584.0, 1010.0, 78.0))

	# Token kiosk and change machine just inside the doors, on the right as you come in.
	props.append(Prop.new(PropKind.TOKENS, 372.0, 990.0, 406.0, 1014.0, 60.0))
	props.append(Prop.new(PropKind.CHANGE, 412.0, 992.0, 440.0, 1014.0, 56.0))
	spots.append(Spot.new(SpotType.TOKENS, -1, Box.new(366.0, 1014.0, 412.0, 1042.0), 389.0, 70.0, 1010.0, 389.0, 40.0, 1016.0))
	# Bins either side of the doors; the café keeps the front-left corner, a plant the right.
	props.append(Prop.new(PropKind.TRASH, AISLE_X0 - 12.0, FRONT_WALL - 22.0, AISLE_X0, FRONT_WALL - 10.0, 18.0))
	props.append(Prop.new(PropKind.TRASH, AISLE_X1, FRONT_WALL - 22.0, AISLE_X1 + 12.0, FRONT_WALL - 10.0, 18.0))
	props.append(Prop.new(PropKind.PLANT, 568.0, FRONT_WALL - 22.0, 586.0, FRONT_WALL - 4.0, 40.0))
	props.append(Prop.new(PropKind.DOORS, DOOR_X0, FRONT_WALL, DOOR_X1, FRONT_WALL + 12.0, 80.0, -1, Catalog.NONE, false))

	# Bought decorations.
	for entry: Array in _decor_spots:
		var style: int = entry[0]
		if not owned_decor.has(style):
			continue
		var cx: float = entry[1]
		var front: float = entry[2]
		var ds := decor_size(style)
		props.append(Prop.new(PropKind.DECOR, cx - ds.x / 2.0, front - ds.y, cx + ds.x / 2.0, front, ds.z, -1, style))
	var disco_x := 304.0
	var disco_y := 470.0
	if owned_decor.has(D.DISCO_BALL):
		props.append(Prop.new(PropKind.DECOR, disco_x - 8.0, disco_y - 8.0, disco_x + 8.0, disco_y + 8.0, 120.0, -1, D.DISCO_BALL, false))

	for p: Prop in props:
		if p.foot != null:
			solids.append(p.foot)

	# A spot in front of every interactive prop, now that all the solids are known.
	var extra := prop_spots(props, solids)
	if extra == null:
		return null
	spots.append_array(extra)

	# Walkable tile grid for the kids' path finding.
	var cols := w / TILE
	var rows := h / TILE
	var walkable := PackedByteArray()
	walkable.resize(cols * rows)
	for ty in rows:
		for tx in cols:
			var cx := tx * TILE + TILE / 2.0
			var cy := ty * TILE + TILE / 2.0
			var free := true
			for b: Box in solids:
				if b.intersects(cx - 6.0, cy - 4.0, cx + 6.0, cy + 6.0):
					free = false
					break
			walkable[ty * cols + tx] = 1 if free else 0
	# Standing spots in the aisles.
	for i in _aisle_spots.size() / 2:
		hangouts.append(Hangout.new(_aisle_spots[i * 2], _aisle_spots[i * 2 + 1], 0.0, false))
	# A seat or a play spot can sit closer to its table or cabinet than a walkable tile's centre, so
	# kids aim for the free tile nearest its approach and walk the last step.
	_snap_to_tiles(hangouts, cols, rows, walkable)
	_snap_to_tiles(cafe_queue, cols, rows, walkable)

	return HubMap.new(w, h, cols, rows, props, solids, spots, walkable,
		304.0, FRONT_WALL - 35.0, 304.0, 58.0, hangouts, disco_x, disco_y, machine_slots, cafe_queue)


static func _snap_to_tiles(list: Array[Hangout], cols: int, rows: int, walkable: PackedByteArray) -> void:
	for i in list.size():
		var hg := list[i]
		var best := -1
		var best_d := INF
		var tx0 := int(hg.approach_x / TILE)
		var ty0 := int(hg.approach_z / TILE)
		for ty in range(ty0 - 2, ty0 + 3):
			for tx in range(tx0 - 2, tx0 + 3):
				if tx < 0 or tx >= cols or ty < 0 or ty >= rows or walkable[ty * cols + tx] == 0:
					continue
				var d := Vector2(tx * TILE + TILE / 2.0 - hg.approach_x, ty * TILE + TILE / 2.0 - hg.approach_z).length()
				if d < best_d:
					best_d = d
					best = ty * cols + tx
		if best >= 0:
			list[i] = Hangout.new(hg.x, hg.z, hg.yaw, hg.playing, best % cols, best / cols, hg.cafe, hg.approach_x, hg.approach_z)


## What [param p] does when the player uses it (a [SpotType]), or [constant SpotType.NONE] for a
## prop that only decorates: the photo booth, the kiddie rides, the vending machines, the café's
## service counter and the bought trophy case, fish tank and jukebox. Making another prop
## interactive starts here: give it a SpotType (and a line in the prompt drawing and the app's
## spot handler).
static func spot_type_of(p: Prop) -> int:
	match p.kind:
		PropKind.PHOTO_BOOTH:
			return SpotType.PHOTO
		PropKind.KIDDIE_RIDE:
			return SpotType.RIDE
		PropKind.VENDING:
			return SpotType.VENDING
		PropKind.CAFE_COUNTER:
			return SpotType.CAFE
		PropKind.DECOR:
			if p.decor == D.TROPHY_CASE:
				return SpotType.TROPHY
			if p.decor == D.FISH_TANK:
				return SpotType.TANK
			if p.decor == D.JUKEBOX:
				return SpotType.JUKEBOX
			return SpotType.NONE
	return SpotType.NONE


## The floor where the player stands to use [param p]: the stand-in-front rule. A strip along the
## prop's front, [constant PROMPT_DEPTH] deep, centred on it (a long counter is served at its till,
## the rest at their middle) and at most [constant MAX_STAND_HALF] either side, cut short before
## any of [param solids] that stands in it. The strip starts exactly at the prop's front, so first
## person keeps its body PaBody.FRONT_GAP off it, as it does for a cabinet. Fails loudly (null) if less
## than [constant MIN_STAND_DEPTH] is left, like a bank that doesn't fit: move the prop.
static func stand_area(p: Prop, solids: Array[Box]) -> Box:
	var cx := CafeLayout.TILL_X if p.kind == PropKind.CAFE_COUNTER else p.center_x
	var half := clampf((p.x1 - p.x0) / 2.0 - 1.0, MIN_STAND_HALF, MAX_STAND_HALF)
	var left := cx - half
	var right := cx + half
	var front := p.front_z
	var bottom := front + PROMPT_DEPTH
	for b: Box in solids:
		if b.left < right and b.right > left and b.bottom > front and b.top < bottom:
			bottom = maxf(b.top, front)
	if not (bottom - front >= MIN_STAND_DEPTH):
		_fail("No room to stand in front of the %s at (%s..%s, %s): only %s clear, it needs %s" % [PropKind.name_of(p.kind), left, right, front, bottom - front, MIN_STAND_DEPTH])
		return null
	return Box.new(left, front, right, bottom)


## A spot for every interactive prop in [param props] ([method spot_type_of]), standing in front of
## it ([method stand_area]) with its prompt floating over the top and the view turning to face it.
## Each spot keeps its prop. Null when a stand area fails.
static func prop_spots(props: Array[Prop], solids: Array[Box]) -> Variant:
	var out: Array[Spot] = []
	for p: Prop in props:
		var type := spot_type_of(p)
		if type == SpotType.NONE:
			continue
		var area := stand_area(p, solids)
		if area == null:
			return null
		out.append(Spot.new(type, -1, area,
			area.center_x, p.height + 6.0, p.front_z - 4.0,
			area.center_x, p.height * 0.6, p.front_z - 2.0, p))
	return out


static func _add_machine(props: Array[Prop], spots: Array[Spot], hangouts: Array[Hangout],
		index: int, shape: int, x0: float, back: float, cw: float, cd: float, ch: float,
		focus_pt: Vector2, copy: int) -> void:
	var cx := x0 + cw / 2.0
	var front := back + cd
	props.append(Prop.new(PropKind.MACHINE, x0, back, x0 + cw, front, ch, index, Catalog.NONE, true, shape, copy))
	var fy := focus_pt.x
	var set_back := focus_pt.y
	var half := maxf(cw / 2.0 - 1.0, 12.0)
	spots.append(Spot.new(SpotType.MACHINE, index,
		Box.new(cx - half, front, cx + half, front + PROMPT_DEPTH),
		cx, ch + 6.0, front - 4.0,
		cx, fy, front - set_back))
	hangouts.append(Hangout.new(cx, front + 12.0, PI, true))


## Footprint width, depth and height of each decoration (Vector3(w, d, h)).
static func decor_size(style: int) -> Vector3:
	match style:
		D.TROPHY_CASE:
			return Vector3(34, 14, 60)
		D.PLUSH_BEAR:
			return Vector3(22, 18, 44)
		D.JUKEBOX:
			return Vector3(26, 14, 50)
		D.FISH_TANK:
			return Vector3(40, 16, 44)
		D.LAVA_LAMP:
			return Vector3(10, 10, 34)
		D.FLAMINGO:
			return Vector3(12, 6, 40)
		D.GUMBALL:
			return Vector3(10, 10, 34)
		D.PALM:
			return Vector3(16, 16, 60)
	# DISCO_BALL
	return Vector3(16, 16, 16)
