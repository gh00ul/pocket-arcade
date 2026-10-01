class_name PaBody
extends RefCounted
## hub/Collision.kt Body, named PaBody here because a global class Body would hide the engine's
## CircleWorld.Body. First person's body: a circle round the feet, wider than the feet box, so the
## eye (which sits at the head) keeps a little personal space from every cabinet and wall. Moves
## slide along walls, roll round corners, and step round the end of a face that blocks only the
## edge of the body instead of snagging on it.
##
## [param out] arguments are Kotlin's FloatArray out-parameters: a [PackedFloat32Array] of at least
## two elements (index 0 = x, 1 = y).

## The body's radius: under half the narrowest aisle (21), so every walkway stays open.
const RADIUS := 10.0
## Machines, the token kiosk and the prize counter keep you this much further off their fronts.
const FRONT_GAP := 8.0
## A move blocked by a face is steered round its end when the body's centre is no more than this
## far in from the end (the body then has to shift [constant RADIUS] + this sideways to clear it).
const CORNER_REACH := 6.0
const EPS := 1e-3


## The solids the first-person body walks among: the map's, with the front of everything that has
## a prompt spot pushed out by [constant FRONT_GAP] so you stop at a comfortable distance to play.
static func solids_for(map: HubMap) -> Array[Box]:
	var out: Array[Box] = []
	for b: Box in map.solids:
		var front := false
		for s: Spot in map.spots:
			if b.bottom == s.area.top and b.left < s.area.right and b.right > s.area.left:
				front = true
		out.append(Box.new(b.left, b.top, b.right, b.bottom + FRONT_GAP) if front else b)
	return out


## How deep a circle of radius [param r] at ([param x], [param y]) sinks into the solids (0 when clear).
static func penetration(solids: Array[Box], x: float, y: float, r: float = RADIUS) -> float:
	var worst := 0.0
	for b: Box in solids:
		if x <= b.left - r or x >= b.right + r or y <= b.top - r or y >= b.bottom + r:
			continue
		var cx := clampf(x, b.left, b.right)
		var cy := clampf(y, b.top, b.bottom)
		var dx := x - cx
		var dy := y - cy
		var d2 := dx * dx + dy * dy
		var p: float
		if d2 <= 1e-12:
			p = r + minf(minf(x - b.left, b.right - x), minf(y - b.top, b.bottom - y))
		else:
			p = r - sqrt(d2)
		if p > worst:
			worst = p
	return worst


static func clear(solids: Array[Box], x: float, y: float, r: float = RADIUS) -> bool:
	return penetration(solids, x, y, r) <= EPS


## Pushes a circle out of every solid it overlaps (a few passes, so it settles in corners).
static func push_out(solids: Array[Box], x: float, y: float, r: float, out: PackedFloat32Array) -> void:
	var px := x
	var py := y
	for pass_i in 4:
		var moved := false
		for b: Box in solids:
			if px <= b.left - r or px >= b.right + r or py <= b.top - r or py >= b.bottom + r:
				continue
			var cx := clampf(px, b.left, b.right)
			var cy := clampf(py, b.top, b.bottom)
			var dx := px - cx
			var dy := py - cy
			var d2 := dx * dx + dy * dy
			if d2 >= r * r:
				continue
			if d2 > 1e-12:
				var d := sqrt(d2)
				var push := r - d + 1e-4
				px += dx / d * push
				py += dy / d * push
			else:
				# The centre is inside the box: out through the nearest side.
				var l := px - b.left
				var rt := b.right - px
				var t := py - b.top
				var bt := b.bottom - py
				var m := minf(minf(l, rt), minf(t, bt))
				if m == l:
					px = b.left - r
				elif m == rt:
					px = b.right + r
				elif m == t:
					py = b.top - r
				else:
					py = b.bottom + r
			moved = true
		if not moved:
			break
	out[0] = px
	out[1] = py


## Moves the body from ([param x], [param y]) by ([param dx], [param dy]) in short substeps,
## pushing it out of whatever it runs into (so it slides along walls and rolls round corners). A
## step that would wedge it into a crack narrower than the body is refused. Writes the result into
## [param out].
static func move(solids: Array[Box], x: float, y: float, dx: float, dy: float, out: PackedFloat32Array, r: float = RADIUS) -> bool:
	var d := sqrt(dx * dx + dy * dy)
	var n := 1 if d <= r * 0.4 else ceili(d / (r * 0.4))
	# Starting inside something (the view just switched, a decoration landed): just get out.
	var stuck := penetration(solids, x, y, r) > EPS
	var px := x
	var py := y
	for s in n:
		push_out(solids, px + dx / n, py + dy / n, r, out)
		if not stuck and penetration(solids, out[0], out[1], r) > EPS:
			break
		px = out[0]
		py = out[1]
	out[0] = px
	out[1] = py
	return px != x or py != y


## When a move ([param dx], [param dy]) from ([param x], [param y]) was mostly blocked, looks a
## little to either side for a way past the end of what's in the way and, if there is one, steps
## sideways toward it (by up to the move's length). Writes the new position into [param out];
## false if there's no way round.
static func slide_round(solids: Array[Box], x: float, y: float, dx: float, dy: float, out: PackedFloat32Array, r: float = RADIUS) -> bool:
	var d := sqrt(dx * dx + dy * dy)
	if d < 1e-5:
		return false
	var ux := dx / d
	var uy := dy / d
	# Perpendicular to the move.
	var sx := -uy
	var sy := ux
	# The nearest lane either side that's clear to walk on along the move.
	var k := 2.0
	while k <= r + CORNER_REACH + 1e-3:
		for side in 2:
			var sgn := 1.0 if side == 0 else -1.0
			var ox := x + sx * sgn * k
			var oy := y + sy * sgn * k
			if clear(solids, ox, oy, r) and clear(solids, ox + ux * r * 0.6, oy + uy * r * 0.6, r):
				var step := minf(d, k)
				return move(solids, x, y, sx * sgn * step, sy * sgn * step, out, r)
		k += 2.0
	return false
