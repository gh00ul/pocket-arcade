class_name Collision
extends RefCounted
## hub/Collision.kt Collision: moves an actor's feet box through a list of solid boxes one axis at
## a time, so walking into a wall at an angle slides along it instead of sticking.
##
## [param out] arguments are Kotlin's FloatArray out-parameters: a [PackedFloat32Array] of at least
## two elements (packed arrays are passed by reference), index 0 = x, 1 = y.

## Half-width and height of the feet box used for walking collision.
const FEET_HALF_W := 5.0
const FEET_H := 5.0


static func blocked(solids: Array[Box], x: float, y: float) -> bool:
	var l := x - FEET_HALF_W
	var r := x + FEET_HALF_W
	var t := y - FEET_H
	for b: Box in solids:
		# Box.intersects(l, t, r, y), inlined: this runs for every kid every step.
		if l < b.right and r > b.left and t < b.bottom and y > b.top:
			return true
	return false


## Attempts to move from ([param x], [param y]) by ([param dx], [param dy]). Writes the resolved
## position into [param out] (index 0 = x, 1 = y) and returns true if any movement happened.
static func move(solids: Array[Box], x: float, y: float, dx: float, dy: float, out: PackedFloat32Array) -> bool:
	var nx := x
	var ny := y
	if dx != 0.0:
		var tx := x + dx
		if not blocked(solids, tx, ny):
			nx = tx
		else:
			nx = _slide_to(solids, x, ny, dx, true)
	if dy != 0.0:
		var ty := ny + dy
		if not blocked(solids, nx, ty):
			ny = ty
		else:
			ny = _slide_to(solids, nx, ny, dy, false)
	out[0] = nx
	out[1] = ny
	return nx != x or ny != y


## Binary-searches the furthest free position along one axis so actors stop flush with walls.
static func _slide_to(solids: Array[Box], x: float, y: float, delta: float, horizontal: bool) -> float:
	var lo := 0.0
	var hi := 1.0
	for i in 6:
		var mid := (lo + hi) / 2.0
		var px := x + delta * mid if horizontal else x
		var py := y if horizontal else y + delta * mid
		if blocked(solids, px, py):
			hi = mid
		else:
			lo = mid
	return x + delta * lo if horizontal else y + delta * lo
