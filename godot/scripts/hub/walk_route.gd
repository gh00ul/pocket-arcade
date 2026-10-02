class_name WalkRoute
extends RefCounted
## hub/WalkRoute.kt: first person's tap-to-walk: a route over the kids' walk grid
## ([method HubWorld.find_path]) from where you stand to where you tapped, with the tile-by-tile
## zigzag pulled straight wherever the body fits ([PaBody]), ending at an exact point (a machine's
## play spot, a patch of floor). Planned once per tap; following it allocates nothing.

const MAX_POINTS := 256
## A waypoint on the way counts as reached this close (the body cuts corners a little).
const PASS_DIST := 7.0
## The route ends this close to its last point.
const ARRIVE_DIST := 1.5
## Slows down over this last stretch so the stop is smooth.
const SLOW_DIST := 22.0
## Gives up if it makes no headway for this long (someone in the way, a gap too tight).
const STUCK_TIME := 1.2
## Straight-line checks sample the way this often (world units).
const SAMPLE := 3.0

var xs := PackedFloat32Array()
var ys := PackedFloat32Array()
## Read only.
var count := 0
## Read only.
var index := 0
## The prompt spot this route takes you to (to face its machine on arrival), or null. Read only.
var spot: Spot = null

var active: bool:
	get:
		return count > 0
var goal_x: float:
	get:
		return xs[count - 1] if count > 0 else 0.0
var goal_y: float:
	get:
		return ys[count - 1] if count > 0 else 0.0

var _best_d := INF
var _stuck_t := 0.0
var _tmp := PackedFloat32Array([0.0, 0.0])


func _init() -> void:
	xs.resize(MAX_POINTS)
	ys.resize(MAX_POINTS)


func clear() -> void:
	count = 0
	index = 0
	spot = null


## Plans a route for a body at ([param from_x], [param from_y]) to ([param to_x], [param to_y])
## through [param solids] (the body's), over [param world]'s walk grid. False (and no route) if
## there's no way there.
func plan(world: HubWorld, solids: Array[Box], from_x: float, from_y: float, to_x: float, to_y: float, target: Spot) -> bool:
	clear()
	var map := world.map
	var start := _nearest_tile(map, from_x, from_y, 3)
	var goal := _nearest_tile(map, to_x, to_y, 4)
	if start < 0 or goal < 0:
		return false
	var tiles: Variant = world.find_path(start % map.cols, start / map.cols, goal % map.cols, goal / map.cols)
	if tiles == null:
		return false
	var path: PackedInt32Array = tiles
	# Every point the grid walk passes: here, each tile's centre (nudged clear of what the body would
	# brush against), then the exact goal.
	var n := path.size() + 2
	var rx := PackedFloat32Array()
	var ry := PackedFloat32Array()
	rx.resize(n)
	ry.resize(n)
	rx[0] = from_x
	ry[0] = from_y
	var tile := float(HubLayout.TILE)
	for i in path.size():
		var t := path[i]
		PaBody.push_out(solids, (t % map.cols) * tile + tile / 2.0, (t / map.cols) * tile + tile / 2.0, PaBody.RADIUS, _tmp)
		rx[i + 1] = _tmp[0]
		ry[i + 1] = _tmp[1]
	rx[n - 1] = to_x
	ry[n - 1] = to_y
	# Pull it straight: from each corner, head for the furthest point in plain sight.
	var at := 0
	while at < n - 1 and count < MAX_POINTS:
		var next := at + 1
		while next + 1 < n and straight(solids, rx[at], ry[at], rx[next + 1], ry[next + 1]):
			next += 1
		xs[count] = rx[next]
		ys[count] = ry[next]
		count += 1
		at = next
	if count == 0:
		xs[0] = to_x
		ys[0] = to_y
		count = 1
	index = 0
	spot = target
	_best_d = INF
	_stuck_t = 0.0
	return true


## Whether the body can walk straight from one point to another without touching anything.
func straight(solids: Array[Box], x0: float, y0: float, x1: float, y1: float) -> bool:
	var d := sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
	var steps := int(d / SAMPLE) + 1
	for i in steps + 1:
		var t := float(i) / steps
		if not PaBody.clear(solids, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, PaBody.RADIUS - 0.5):
			return false
	return true


## One step of following the route from ([param x], [param y]): writes the wanted walk (world x,
## z; length ≤ 1) into [param out]. Returns false once the route has ended (arrived, or given up).
func steer(x: float, y: float, dt: float, out: PackedFloat32Array) -> bool:
	out[0] = 0.0
	out[1] = 0.0
	if not active:
		return false
	var gx := xs[index]
	var gy := ys[index]
	var d := sqrt((gx - x) * (gx - x) + (gy - y) * (gy - y))
	while index < count - 1 and d < PASS_DIST:
		index += 1
		_best_d = INF
		gx = xs[index]
		gy = ys[index]
		d = sqrt((gx - x) * (gx - x) + (gy - y) * (gy - y))
	var last := index == count - 1
	if last and d < ARRIVE_DIST:
		count = 0
		return false
	if d < _best_d - 0.25:
		_best_d = d
		_stuck_t = 0.0
	else:
		_stuck_t += dt
		if _stuck_t > STUCK_TIME:
			clear()
			return false
	# Ease off over the last stretch (but keep enough push to finish).
	var left := d
	if not last:
		left = INF
	var pace := clampf(left / SLOW_DIST, 0.25, 1.0) if left < SLOW_DIST else 1.0
	out[0] = (gx - x) / d * pace
	out[1] = (gy - y) / d * pace
	return true


## The walkable tile nearest ([param x], [param y]) within [param reach] tiles, as an index, or -1.
func _nearest_tile(map: HubMap, x: float, y: float, reach: int) -> int:
	var tile := float(HubLayout.TILE)
	var tx0 := int(x / tile)
	var ty0 := int(y / tile)
	var best := -1
	var best_d := INF
	for ty in range(ty0 - reach, ty0 + reach + 1):
		for tx in range(tx0 - reach, tx0 + reach + 1):
			if not map.tile_walkable(tx, ty):
				continue
			var dx := tx * tile + tile / 2.0 - x
			var dy := ty * tile + tile / 2.0 - y
			var d := sqrt(dx * dx + dy * dy)
			if d < best_d:
				best_d = d
				best = ty * map.cols + tx
	return best
