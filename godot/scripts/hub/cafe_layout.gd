class_name CafeLayout
extends RefCounted
## hub/CafeLayout.kt: the café in the hall's front-left corner, left of the doors and open to the
## main aisle, with the fishing tubs behind it: a back bar with the machines and the lit menu, the
## service counter in front of it (the barista works the lane between them), a queue along the
## counter front, diner booths against the wall and round tables with chairs. Everything faces the
## entrance, so it reads from the hall camera, and the queue runs out towards the aisle. Every depth
## is measured from [constant FLOOR_Z0], so the whole café moves with it. Pure floor-plan data: no
## drawing here, so the headless tests can build it.

## The tiled café floor.
const FLOOR_X0 := 16.0
const FLOOR_X1 := 216.0
const FLOOR_Z0 := 796.0
const FLOOR_Z1 := FLOOR_Z0 + 280.0

## The back bar: machines on top, the menu board along its back edge.
const BAR_X0 := 20.0
const BAR_X1 := 152.0
const BAR_Z0 := FLOOR_Z0 + 16.0
const BAR_Z1 := FLOOR_Z0 + 34.0
const BAR_H := 30.0

## The service counter. Its footprint takes in the barista's lane behind it.
const COUNTER_Z0 := FLOOR_Z0 + 34.0
const COUNTER_BACK := FLOOR_Z0 + 54.0
const COUNTER_Z1 := FLOOR_Z0 + 72.0
const COUNTER_H := 28.0

## Where the barista walks, and how far along the lane they go.
const LANE_Z := FLOOR_Z0 + 44.0
const LANE_X0 := 30.0
const LANE_X1 := 142.0

## Stations along the lane (x): the till, the pastry case, the slushie tanks, the espresso machine
## and the soft-serve.
const TILL_X := 134.0
const PASTRY_X := 44.0
const SLUSH_X := 94.0
const ESPRESSO_X := 36.0
const SOFTSERVE_X := 138.0

## The slushie tanks' centres on the counter (x), their centre z and the tank radius and height.
const SLUSH_TANKS := [84.0, 94.0, 104.0]
const SLUSH_Z := FLOOR_Z0 + 62.0
const SLUSH_R := 4.2
const SLUSH_Y0 := COUNTER_H + 7.0
const SLUSH_Y1 := COUNTER_H + 22.0

## Where the espresso machine's steam rises from.
const STEAM_X := 32.0
const STEAM_Y := BAR_H + 20.0
const STEAM_Z := FLOOR_Z0 + 24.0

## Round tables (centre x, z) and the directions their chairs sit (radians, 0 = south of the table).
const TABLES := [
	108.0, FLOOR_Z0 + 132.0, 172.0, FLOOR_Z0 + 132.0, 108.0, FLOOR_Z0 + 210.0, 172.0, FLOOR_Z0 + 210.0, 140.0, FLOOR_Z0 + 264.0,
]
const _TABLE_CHAIRS := [
	[0.0, PI / 2.0, -PI / 2.0],
	[0.0, PI / 2.0, -PI / 2.0],
	[PI, PI / 2.0, -PI / 2.0],
	[PI, PI / 2.0, -PI / 2.0],
	[PI, PI / 2.0, -PI / 2.0],
]
const TABLE_R := 11.0
const CHAIR_D := 17.0

## Diner booths against the left wall, back to back: their near edge z and depth.
const BOOTHS := [FLOOR_Z0 + 100.0, FLOOR_Z0 + 146.0]
const BOOTH_X0 := 20.0
const BOOTH_X1 := 60.0
const BOOTH_D := 46.0

## Queue spots from the till backwards along the counter front.
const _QUEUE := [TILL_X, FLOOR_Z0 + 86.0, 158.0, FLOOR_Z0 + 89.0, 182.0, FLOOR_Z0 + 95.0, 204.0, FLOOR_Z0 + 106.0]

## The café's own point lights (the vending machines bring one each).
const LIGHTS := 2


## Adds the café's props, its seats (as hangouts marked cafe) and its queue spots to the floor plan
## being built.
static func add(props: Array[Prop], hangouts: Array[Hangout], queue: Array[Hangout]) -> void:
	var none := Catalog.NONE
	var up := MiniGame.CabinetShape.UPRIGHT
	# The floor, the lighting rig and the signs hang off a prop with no footprint, so they cast no
	# floor shadow.
	props.append(Prop.new(PropKind.CAFE_FLOOR, FLOOR_X0, FLOOR_Z0 + 136.0, FLOOR_X1, FLOOR_Z0 + 136.0, 0.0, -1, none, false))
	props.append(Prop.new(PropKind.CAFE_BAR, BAR_X0, BAR_Z0, BAR_X1, BAR_Z1, BAR_H))
	props.append(Prop.new(PropKind.CAFE_COUNTER, BAR_X0, COUNTER_Z0, BAR_X1, COUNTER_Z1, COUNTER_H))
	# Vending machines side by side beside the counter, facing the tables; a plant by the booths and
	# one in the front corner by the window.
	props.append(Prop.new(PropKind.VENDING, 152.0, FLOOR_Z0 + 14.0, 182.0, FLOOR_Z0 + 36.0, 62.0, -1, none, true, up, 0))
	props.append(Prop.new(PropKind.VENDING, 182.0, FLOOR_Z0 + 14.0, 212.0, FLOOR_Z0 + 36.0, 62.0, -1, none, true, up, 1))
	props.append(Prop.new(PropKind.PLANT, 20.0, FLOOR_Z0 + 198.0, 38.0, FLOOR_Z0 + 216.0, 40.0))
	props.append(Prop.new(PropKind.PLANT, 186.0, FLOOR_Z0 + 260.0, 204.0, FLOOR_Z0 + 278.0, 40.0))

	for i in BOOTHS.size():
		var z0: float = BOOTHS[i]
		props.append(Prop.new(PropKind.BOOTH, BOOTH_X0, z0, BOOTH_X1, z0 + BOOTH_D, 24.0, -1, none, true, up, i))
		# Kids sit at the open end of each bench, facing each other, and get in from the aisle.
		var seat_x := BOOTH_X1 - 10.0
		hangouts.append(Hangout.new(seat_x, z0 + 7.0, 0.0, false, Hangout.AUTO_TILE, Hangout.AUTO_TILE, true, BOOTH_X1 + 14.0))
		hangouts.append(Hangout.new(seat_x, z0 + BOOTH_D - 7.0, PI, false, Hangout.AUTO_TILE, Hangout.AUTO_TILE, true, BOOTH_X1 + 14.0))
	for i in TABLES.size() / 2:
		var tx: float = TABLES[i * 2]
		var tz: float = TABLES[i * 2 + 1]
		props.append(Prop.new(PropKind.CAFE_TABLE, tx - TABLE_R, tz - TABLE_R, tx + TABLE_R, tz + TABLE_R, 24.0, -1, none, true, up, i))
		for a: float in _TABLE_CHAIRS[i]:
			var sx := tx + sin(a) * CHAIR_D
			var sz := tz + cos(a) * CHAIR_D
			var yaw := atan2(tx - sx, tz - sz)
			# The chair's facing rides in its variant, in whole degrees (Math.round: half up).
			props.append(Prop.new(PropKind.CHAIR, sx - 5.0, sz - 5.0, sx + 5.0, sz + 5.0, 22.0, -1, none, false, up,
				MathUtil.round_to_int(rad_to_deg(yaw))))
			hangouts.append(Hangout.new(sx, sz, yaw, false, Hangout.AUTO_TILE, Hangout.AUTO_TILE, true))
	for k in _QUEUE.size() / 2:
		var qx: float = _QUEUE[k * 2]
		var qz: float = _QUEUE[k * 2 + 1]
		# The front of the queue faces the till; everyone else faces the kid ahead.
		var yaw := PI if k == 0 else atan2(float(_QUEUE[k * 2 - 2]) - qx, float(_QUEUE[k * 2 - 1]) - qz)
		queue.append(Hangout.new(qx, qz, yaw, false))


## Adds the café's lights: a warm wash over the counter and one over the seating. The pendants, neon
## and menu glow on their own, so the hall's light budget barely notices.
static func lights(out: Array[PointLight]) -> void:
	out.append(PointLight.new(86.0, 80.0, COUNTER_Z1 + 6.0, 1.0, 0.85, 0.66, 130.0, 1.05))
	out.append(PointLight.new(130.0, 110.0, FLOOR_Z0 + 186.0, 1.0, 0.82, 0.63, 150.0, 0.6))


## Which way a chair of [param variant] faces.
static func chair_yaw(variant: int) -> float:
	return deg_to_rad(float(variant))
