class_name PinballTable
extends RefCounted
## games/pinball/PinballTable.kt: the pinball table's layout in table units: x across (0 at the left
## wall, 300 at the right wall; the shooter lane is 272..300), y down the table from the top arch (0)
## to the drain (640). Gravity pulls towards +y. The 3D view uses the same numbers as world x and z.
##
## Every wall, rubber post and bumper the ball can touch lives here as plain arrays, so the
## physics, the painted playfield and the rail models all read one source. Pure numbers: safe for
## headless tests. Kotlin's `object` init block is [method _static_init].

const W := 300.0
## Right edge of the playfield proper: the shooter lane's inner wall.
const PLAY_W := 272.0
## Middle of the playfield (not of the whole table).
const CX := 136.0
const BALL_R := 9.0
## A ball whose centre passes this line has drained.
const DRAIN_Y := 640.0
## Where the apron (the plastic over the drain) starts.
const APRON_Y := 586.0

# Shooter lane and plunger.
const LANE_X := 286.0
const LANE_REST_Y := 575.0
const LANE_TOP_Y := 160.0
## The one-way gate at the top of the lane runs from the outer wall down to the lane wall.
const GATE_Y0 := 132.0

# Flippers: pivots, length, radii at the pivot and the tip, and their swing.
const FLIP_L_X := 74.0
const FLIP_R_X := 198.0
const FLIP_Y := 520.0
const FLIP_LEN := 52.0
const FLIP_R0 := 9.0
const FLIP_R1 := 5.0
## Left flipper angles (radians from +x towards +y, i.e. down the table); the right mirrors them.
const FLIP_REST := 0.52
const FLIP_UP := -0.45

# Pop bumpers.
const BUMPER_X: Array[float] = [100.0, 172.0, 136.0]
const BUMPER_Y: Array[float] = [150.0, 150.0, 206.0]
const BUMPER_R := 17.0

# Top rollover lanes between four guide posts.
const LANE_GUIDE_X: Array[float] = [76.0, 116.0, 156.0, 196.0]
const LANE_GUIDE_Y0 := 44.0
const LANE_GUIDE_Y1 := 84.0
const ROLLOVER_X: Array[float] = [96.0, 136.0, 176.0]
const ROLLOVER_Y := 64.0
const ROLLOVER_HALF := 16.0

# Drop targets: a vertical bank of three on the right, facing left.
const DROP_X := 258.0
const DROP_Y0: Array[float] = [236.0, 266.0, 296.0]
const DROP_LEN := 24.0

# Left orbit: the lane between the left wall and this inner wall, with the spinner across it.
const ORBIT_X := 40.0
const ORBIT_Y0 := 120.0
const ORBIT_Y1 := 330.0
const SPINNER_Y := 232.0

# Slingshots (left one; the right mirrors it): top, bottom-left and bottom-right corners.
const SLING_AX := 50.0
const SLING_AY := 410.0
const SLING_BX := 50.0
const SLING_BY := 464.0
const SLING_CX := 84.0
const SLING_CY := 488.0
## Where the inlane guide turns into the sloped floor that feeds the flipper.
const INLANE_BEND_Y := 476.0

# Collider kinds.
const WALL := 0
## A slingshot's kicking face; the tag is the sling (0 left, 1 right).
const SLING := 1
## The shooter lane's one-way gate: solid only from above.
const GATE := 2
## Lively rubber (sling sides, post rubbers).
const RUBBER := 3

## How a wall looks in 3D: a tall outer rail, a metal guide or a rubber band.
const STYLE_RAIL := 0
const STYLE_GUIDE := 1
const STYLE_RUBBER := 2

const MAX_SEGS := 64
const MAX_CIRCLES := 24

# Kotlin FloatArrays (32-bit), so the stored layout is build-13's to the bit wherever it was.
static var ax := PackedFloat32Array()
static var ay := PackedFloat32Array()
static var bx := PackedFloat32Array()
static var by := PackedFloat32Array()
static var bounce := PackedFloat32Array()
static var kind := PackedInt32Array()
static var tag := PackedInt32Array()
static var style := PackedInt32Array()
static var seg_count := 0

## Round posts (rubber-ringed pins, guide tips). Bumpers are separate.
static var px := PackedFloat32Array()
static var py := PackedFloat32Array()
static var pr := PackedFloat32Array()
static var post_count := 0

## Solid side of the gate (unit normal).
static var gate_nx := 0.0
static var gate_ny := 0.0


static func _seg(x0: float, y0: float, x1: float, y1: float, e: float, k: int = WALL, t: int = 0, s: int = STYLE_GUIDE) -> void:
	var i := seg_count
	seg_count += 1
	ax[i] = x0
	ay[i] = y0
	bx[i] = x1
	by[i] = y1
	bounce[i] = e
	kind[i] = k
	tag[i] = t
	style[i] = s


## The same segment on both sides of the playfield.
static func _pair(x0: float, y0: float, x1: float, y1: float, e: float, k: int = WALL, s: int = STYLE_GUIDE) -> void:
	_seg(x0, y0, x1, y1, e, k, 0, s)
	_seg(PLAY_W - x0, y0, PLAY_W - x1, y1, e, k, 1, s)


static func _post(x: float, y: float, r: float) -> void:
	var i := post_count
	post_count += 1
	px[i] = x
	py[i] = y
	pr[i] = r


static func _post_pair(x: float, y: float, r: float) -> void:
	_post(x, y, r)
	_post(PLAY_W - x, y, r)


## An arc of [param n] segments around ([param cx], [param cy]) from angle [param a0] to [param a1]
## (degrees, y down).
static func _arc(cx: float, cy: float, r: float, a0: float, a1: float, n: int) -> void:
	for k in n:
		var t0 := (a0 + (a1 - a0) * k / n) * PI / 180.0
		var t1 := (a0 + (a1 - a0) * (k + 1) / n) * PI / 180.0
		_seg(cx + cos(t0) * r, cy + sin(t0) * r, cx + cos(t1) * r, cy + sin(t1) * r, 0.45, WALL, 0, STYLE_RAIL)


static func _static_init() -> void:
	ax.resize(MAX_SEGS)
	ay.resize(MAX_SEGS)
	bx.resize(MAX_SEGS)
	by.resize(MAX_SEGS)
	bounce.resize(MAX_SEGS)
	kind.resize(MAX_SEGS)
	tag.resize(MAX_SEGS)
	style.resize(MAX_SEGS)
	px.resize(MAX_CIRCLES)
	py.resize(MAX_CIRCLES)
	pr.resize(MAX_CIRCLES)
	seg_count = 0
	post_count = 0
	# The cabinet: side walls, the top arch and the shooter lane.
	_seg(0.0, 70.0, 0.0, DRAIN_Y + 20.0, 0.45, WALL, 0, STYLE_RAIL)
	_seg(W, 70.0, W, DRAIN_Y + 20.0, 0.45, WALL, 0, STYLE_RAIL)
	_seg(70.0, 0.0, 230.0, 0.0, 0.45, WALL, 0, STYLE_RAIL)
	_arc(70.0, 70.0, 70.0, 180.0, 270.0, 8)
	_arc(230.0, 70.0, 70.0, 270.0, 360.0, 8)
	_seg(PLAY_W, LANE_TOP_Y, PLAY_W, DRAIN_Y + 20.0, 0.4)
	_seg(PLAY_W, LANE_REST_Y + 14.0, W, LANE_REST_Y + 14.0, 0.1)
	_seg(W, GATE_Y0, PLAY_W, LANE_TOP_Y, 0.3, GATE)
	var gx := PLAY_W - W
	var gy := LANE_TOP_Y - GATE_Y0
	var gl := sqrt(gx * gx + gy * gy)
	# Perpendicular to the gate, pointing up the table (towards smaller y).
	gate_nx = gy / gl * -1.0
	gate_ny = gx / gl
	# Left orbit's inner wall, with rounded ends.
	_seg(ORBIT_X, ORBIT_Y0, ORBIT_X, ORBIT_Y1, 0.4)
	_post(ORBIT_X, ORBIT_Y0, 4.0)
	_post(ORBIT_X, ORBIT_Y1, 5.0)
	# Deflectors that turn a ball running down a side wall into the playfield.
	_pair(0.0, 350.0, 20.0, 378.0, 0.35)
	# Inlane guides and the inlane floors that feed the flippers; outlanes run outside them.
	# The floor bends low enough to leave a ball (18) about 4 units to spare under the sling's
	# bottom post (50, 464, r 4); bending at 466 left 16.8 and trapped it there.
	_pair(22.0, 398.0, 22.0, INLANE_BEND_Y, 0.35)
	_pair(22.0, INLANE_BEND_Y, 64.0, 506.0, 0.25)
	_post_pair(22.0, 398.0, 4.0)
	# Slingshots: two plain rubber sides and a kicking face.
	_pair(SLING_AX, SLING_AY, SLING_BX, SLING_BY, 0.5, RUBBER, STYLE_RUBBER)
	_pair(SLING_BX, SLING_BY, SLING_CX, SLING_CY, 0.5, RUBBER, STYLE_RUBBER)
	_pair(SLING_CX, SLING_CY, SLING_AX, SLING_AY, 0.6, SLING, STYLE_RUBBER)
	_post_pair(SLING_AX, SLING_AY, 4.0)
	_post_pair(SLING_BX, SLING_BY, 4.0)
	_post_pair(SLING_CX, SLING_CY, 4.0)
	# Top lane guides.
	for x: float in LANE_GUIDE_X:
		_seg(x, LANE_GUIDE_Y0, x, LANE_GUIDE_Y1, 0.4)
		_post(x, LANE_GUIDE_Y0, 3.5)
		_post(x, LANE_GUIDE_Y1, 3.5)
	# Guards above and below the drop target bank.
	_seg(PLAY_W, 196.0, DROP_X, 230.0, 0.35)
	_post(DROP_X, 231.0, 3.0)
	_post(DROP_X, DROP_Y0[2] + DROP_LEN + 2.0, 3.0)


## The right flipper's mirrored angles (Kotlin: `PI.toFloat() - angle`).
const FLIP_REST_R := PI - FLIP_REST
const FLIP_UP_R := PI - FLIP_UP


## Pivot x of flipper [param side] (0 left, 1 right).
static func flip_x(side: int) -> float:
	return FLIP_L_X if side == 0 else FLIP_R_X


## Resting angle of flipper [param side].
static func rest_angle(side: int) -> float:
	return FLIP_REST if side == 0 else FLIP_REST_R


## Raised angle of flipper [param side].
static func up_angle(side: int) -> float:
	return FLIP_UP if side == 0 else FLIP_UP_R
