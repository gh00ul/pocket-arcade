class_name CircleWorld
extends RefCounted
## engine/Physics.kt: impulse-based circle physics with positional correction, used by the claw
## machine (side view with gravity) and the coin pusher (top-down, gravity off, heavy damping).
## Bodies and walls are [CircleWorld.Body] and [CircleWorld.Segment].


## A circular rigid body (no rotational dynamics; [member angle] is a visual roll derived from motion).
class Body:
	extends RefCounted
	var x: float
	var y: float
	var r: float
	var vx := 0.0
	var vy := 0.0
	var mass: float
	var restitution := 0.2
	var friction := 0.4
	## Fraction of velocity lost per second (air drag / surface friction).
	var damping := 0.1
	## Kinematic bodies are moved by game code and push others with infinite mass.
	var kinematic := false
	var enabled := true
	var angle := 0.0
	var kind := 0
	var tag := 0
	var data: Variant = null
	var touching := false

	func _init(p_x: float, p_y: float, p_r: float) -> void:
		x = p_x
		y = p_y
		r = p_r
		mass = p_r * p_r

	func inv_mass() -> float:
		return 0.0 if kinematic or mass <= 0.0 else 1.0 / mass


## A static line segment wall; bodies collide with it from either side.
class Segment:
	extends RefCounted
	var x1: float
	var y1: float
	var x2: float
	var y2: float
	var bounce: float

	func _init(p_x1: float, p_y1: float, p_x2: float, p_y2: float, p_bounce: float = 0.3) -> void:
		x1 = p_x1
		y1 = p_y1
		x2 = p_x2
		y2 = p_y2
		bounce = p_bounce


var gravity_x := 0.0
var gravity_y := 0.0
var bodies: Array[Body] = []
var segments: Array[Segment] = []
var iterations := 6
## Relative normal speeds below this are treated as resting contact (no bounce) to kill jitter.
var resting_speed := 40.0
## Extra per-body constraint applied every solver iteration (e.g. a moving pusher wall); takes a Body.
var constraint: Callable = Callable()


func _init(p_gravity_x: float = 0.0, p_gravity_y: float = 0.0) -> void:
	gravity_x = p_gravity_x
	gravity_y = p_gravity_y


## Keeps [member bodies] sorted by x (insertion sort: cheap because order barely changes per step).
func _sort_by_x() -> void:
	for i in range(1, bodies.size()):
		var b := bodies[i]
		var j := i - 1
		while j >= 0 and bodies[j].x > b.x:
			bodies[j + 1] = bodies[j]
			j -= 1
		bodies[j + 1] = b


func step(dt: float) -> void:
	for b in bodies:
		if not b.enabled:
			continue
		b.touching = false
		if b.kinematic:
			continue
		b.vx += gravity_x * dt
		b.vy += gravity_y * dt
		var k := clampf(1.0 - b.damping * dt, 0.0, 1.0)
		b.vx *= k
		b.vy *= k
		b.x += b.vx * dt
		b.y += b.vy * dt
		b.angle += b.vx * dt / b.r
	var max_r := 0.0
	for b in bodies:
		if b.r > max_r:
			max_r = b.r
	var extra := constraint
	var has_extra := extra.is_valid()
	for it in iterations:
		# Sweep and prune along x: once the gap exceeds the largest possible overlap, stop.
		_sort_by_x()
		var n := bodies.size()
		for i in n:
			var a := bodies[i]
			if not a.enabled:
				continue
			var reach := a.r + max_r
			for j in range(i + 1, n):
				var b := bodies[j]
				if b.x - a.x > reach:
					break
				if not b.enabled:
					continue
				collide(a, b)
		for a in bodies:
			if not a.enabled or a.kinematic:
				continue
			for s in segments:
				collide_segment(a, s)
			if has_extra:
				extra.call(a)


func collide(a: Body, b: Body) -> bool:
	var dx := b.x - a.x
	var dy := b.y - a.y
	var rs := a.r + b.r
	var d2 := dx * dx + dy * dy
	if d2 >= rs * rs:
		return false
	var wa := a.inv_mass()
	var wb := b.inv_mass()
	var wsum := wa + wb
	if wsum <= 0.0:
		return false
	var d := sqrt(d2)
	var nx: float
	var ny: float
	if d < 1e-4:
		nx = 0.0
		ny = 1.0
		d = 0.0
	else:
		nx = dx / d
		ny = dy / d
	var pen := rs - d
	var corr := pen / wsum * 0.9
	a.x -= nx * corr * wa
	a.y -= ny * corr * wa
	b.x += nx * corr * wb
	b.y += ny * corr * wb
	a.touching = true
	b.touching = true

	var rvx := b.vx - a.vx
	var rvy := b.vy - a.vy
	var vn := rvx * nx + rvy * ny
	if vn < 0.0:
		var e := 0.0 if -vn < resting_speed else minf(a.restitution, b.restitution)
		var j := -(1.0 + e) * vn / wsum
		a.vx -= j * nx * wa
		a.vy -= j * ny * wa
		b.vx += j * nx * wb
		b.vy += j * ny * wb
		# Coulomb friction along the tangent.
		var tx := -ny
		var ty := nx
		var vt := (b.vx - a.vx) * tx + (b.vy - a.vy) * ty
		var jt := -vt / wsum
		var mu := (a.friction + b.friction) * 0.5
		var max_f := j * mu
		jt = clampf(jt, -max_f, max_f)
		a.vx -= jt * tx * wa
		a.vy -= jt * ty * wa
		b.vx += jt * tx * wb
		b.vy += jt * ty * wb
	return true


func collide_segment(b: Body, s: Segment) -> bool:
	var ex := s.x2 - s.x1
	var ey := s.y2 - s.y1
	var l2 := ex * ex + ey * ey
	var t := ((b.x - s.x1) * ex + (b.y - s.y1) * ey) / l2 if l2 > 0.0 else 0.0
	t = clampf(t, 0.0, 1.0)
	var cx := s.x1 + ex * t
	var cy := s.y1 + ey * t
	var dx := b.x - cx
	var dy := b.y - cy
	var d2 := dx * dx + dy * dy
	if d2 >= b.r * b.r:
		return false
	var d := sqrt(d2)
	var nx: float
	var ny: float
	if d < 1e-4:
		var l := maxf(sqrt(l2), 1e-4)
		nx = -ey / l
		ny = ex / l
	else:
		nx = dx / d
		ny = dy / d
	var pen := b.r - d
	b.x += nx * pen
	b.y += ny * pen
	b.touching = true
	var vn := b.vx * nx + b.vy * ny
	if vn < 0.0:
		var e := 0.0 if -vn < resting_speed else minf(b.restitution, s.bounce)
		b.vx -= (1.0 + e) * vn * nx
		b.vy -= (1.0 + e) * vn * ny
		var tx := -ny
		var ty := nx
		var vt := b.vx * tx + b.vy * ty
		var max_f := -vn * b.friction
		var jt := clampf(-vt, -max_f, max_f)
		b.vx += jt * tx
		b.vy += jt * ty
	return true
