class_name FastCircleWorld
extends CircleWorld
## [CircleWorld] (engine/Physics.kt) with its step run on packed arrays: the same algorithm, the
## same order of operations and the same 64-bit arithmetic, so every body ends a step exactly where
## CircleWorld would leave it (tests/games/coinpusher/fast_circle_world_test.gd checks this bit for
## bit), only about four times faster. GDScript pays for every read of another object's member, and
## the claw's pile and the pusher's deck are read thousands of times a step.
##
## How it stays the same: the bodies are copied into arrays once a step (integrating on the way),
## the arrays are kept in the x order CircleWorld sorts its list into (insertion sort, the same
## comparisons, so the same permutation), pairs are tried in exactly CircleWorld's order with its
## break, and the list is left in that order at the end. The only extra work skipped is a pair two
## bodies apart in y by at least the sweep's reach, which CircleWorld.collide would reject anyway.
##
## Bodies, segments, gravity, iterations and resting speed are CircleWorld's own fields. The coin
## pusher's shelf is built in ([member push_wall]); any other [member CircleWorld.constraint]
## still works as long as it only reads and moves the body it is given.

## The pusher shelf (Kotlin's `constraint = { b -> pushBody(b) }`): every solver iteration a body
## whose centre is behind [member push_front] + its radius is moved up to it, and its vy raised to
## at least [member push_v].
var push_wall := false
var push_front := 0.0
var push_v := 0.0

# The bodies' state for one step, in sorted (x) order; _id is each one's index in the list as the
# step began.
var _x := PackedFloat64Array()
var _y := PackedFloat64Array()
var _vx := PackedFloat64Array()
var _vy := PackedFloat64Array()
var _r := PackedFloat64Array()
var _w := PackedFloat64Array()
var _rest := PackedFloat64Array()
var _fric := PackedFloat64Array()
var _flags := PackedInt32Array()
var _touch := PackedInt32Array()
var _id := PackedInt32Array()
var _tmp: Array[CircleWorld.Body] = []
# Segments: start, edge vector, squared length, bounce.
var _sx1 := PackedFloat64Array()
var _sy1 := PackedFloat64Array()
var _sex := PackedFloat64Array()
var _sey := PackedFloat64Array()
var _sl2 := PackedFloat64Array()
var _sb := PackedFloat64Array()
# Segments' bounding boxes, grown by SEG_SLACK.
var _smin_x := PackedFloat64Array()
var _smax_x := PackedFloat64Array()
var _smin_y := PackedFloat64Array()
var _smax_y := PackedFloat64Array()

## How far past a segment's box a body is still tested against it: far more than the rounding of
## the distance maths, so skipping a body outside it never skips a contact CircleWorld would make.
const SEG_SLACK := 0.001

const _ENABLED := 1
const _KINEMATIC := 2


func _init(p_gravity_x: float = 0.0, p_gravity_y: float = 0.0) -> void:
	super(p_gravity_x, p_gravity_y)


func _grow(n: int) -> void:
	var cap := maxi(n, 16) * 2
	_x.resize(cap)
	_y.resize(cap)
	_vx.resize(cap)
	_vy.resize(cap)
	_r.resize(cap)
	_w.resize(cap)
	_rest.resize(cap)
	_fric.resize(cap)
	_flags.resize(cap)
	_touch.resize(cap)
	_id.resize(cap)


func step(dt: float) -> void:
	var list := bodies
	var n := list.size()
	if _x.size() < n:
		_grow(n)
	var xs := _x
	var ys := _y
	var vxs := _vx
	var vys := _vy
	var rr := _r
	var ws := _w
	var rest := _rest
	var fric := _fric
	var flags := _flags
	var touch := _touch
	var ids := _id
	var gx := gravity_x
	var gy := gravity_y
	var resting := resting_speed
	# Integrate (CircleWorld's first loop) while copying each body in.
	var max_r := 0.0
	for i in n:
		var b := list[i]
		var x := b.x
		var y := b.y
		var vx := b.vx
		var vy := b.vy
		var r := b.r
		var en := b.enabled
		var kin := b.kinematic
		var t := 1 if b.touching else 0
		if en:
			t = 0
			if not kin:
				vx += gx * dt
				vy += gy * dt
				var k := clampf(1.0 - b.damping * dt, 0.0, 1.0)
				vx *= k
				vy *= k
				x += vx * dt
				y += vy * dt
				b.angle += vx * dt / r
		xs[i] = x
		ys[i] = y
		vxs[i] = vx
		vys[i] = vy
		rr[i] = r
		var mass := b.mass
		ws[i] = 0.0 if kin or mass <= 0.0 else 1.0 / mass
		rest[i] = b.restitution
		fric[i] = b.friction
		flags[i] = (_ENABLED if en else 0) | (_KINEMATIC if kin else 0)
		touch[i] = t
		ids[i] = i
		if r > max_r:
			max_r = r
	# Segments, with the edge maths CircleWorld redoes per contact done once.
	var ns := segments.size()
	if _sx1.size() < ns:
		_sx1.resize(ns)
		_sy1.resize(ns)
		_sex.resize(ns)
		_sey.resize(ns)
		_sl2.resize(ns)
		_sb.resize(ns)
		_smin_x.resize(ns)
		_smax_x.resize(ns)
		_smin_y.resize(ns)
		_smax_y.resize(ns)
	var sx1 := _sx1
	var sy1 := _sy1
	var sex := _sex
	var sey := _sey
	var sl2 := _sl2
	var sb := _sb
	var smin_x := _smin_x
	var smax_x := _smax_x
	var smin_y := _smin_y
	var smax_y := _smax_y
	for s in ns:
		var seg := segments[s]
		var ex := seg.x2 - seg.x1
		var ey := seg.y2 - seg.y1
		sx1[s] = seg.x1
		sy1[s] = seg.y1
		sex[s] = ex
		sey[s] = ey
		sl2[s] = ex * ex + ey * ey
		sb[s] = seg.bounce
		smin_x[s] = minf(seg.x1, seg.x2) - SEG_SLACK
		smax_x[s] = maxf(seg.x1, seg.x2) + SEG_SLACK
		smin_y[s] = minf(seg.y1, seg.y2) - SEG_SLACK
		smax_y[s] = maxf(seg.y1, seg.y2) + SEG_SLACK
	var extra := constraint
	var has_extra := extra.is_valid()
	var wall := push_wall
	var wall_front := push_front
	var wall_v := push_v
	for it in iterations:
		# Sweep and prune along x: insertion sort (the order barely changes per step) carrying every
		# array along, then pairs until the gap exceeds the largest possible overlap.
		for i in range(1, n):
			var ox := xs[i]
			if xs[i - 1] <= ox:
				continue
			var oy := ys[i]
			var ovx := vxs[i]
			var ovy := vys[i]
			var o_r := rr[i]
			var ow := ws[i]
			var orest := rest[i]
			var ofric := fric[i]
			var oflags := flags[i]
			var otouch := touch[i]
			var oid := ids[i]
			var j := i - 1
			while j >= 0 and xs[j] > ox:
				var k := j + 1
				xs[k] = xs[j]
				ys[k] = ys[j]
				vxs[k] = vxs[j]
				vys[k] = vys[j]
				rr[k] = rr[j]
				ws[k] = ws[j]
				rest[k] = rest[j]
				fric[k] = fric[j]
				flags[k] = flags[j]
				touch[k] = touch[j]
				ids[k] = ids[j]
				j -= 1
			j += 1
			xs[j] = ox
			ys[j] = oy
			vxs[j] = ovx
			vys[j] = ovy
			rr[j] = o_r
			ws[j] = ow
			rest[j] = orest
			fric[j] = ofric
			flags[j] = oflags
			touch[j] = otouch
			ids[j] = oid
		for a in n:
			if (flags[a] & _ENABLED) == 0:
				continue
			var xa := xs[a]
			var ya := ys[a]
			var ra := rr[a]
			var reach := ra + max_r
			var reach2 := reach * reach
			for b in range(a + 1, n):
				var xb := xs[b]
				if xb - xa > reach:
					break
				var dy := ys[b] - ya
				# Too far apart in y for any radius: then d2 >= dy * dy >= reach² >= rs², so
				# CircleWorld.collide would reject it too (rounding is monotonic; NaN never skips).
				if dy * dy >= reach2:
					continue
				if (flags[b] & _ENABLED) == 0:
					continue
				# CircleWorld.collide(a, b), inline.
				var dx := xb - xa
				var rs := ra + rr[b]
				var d2 := dx * dx + dy * dy
				if d2 >= rs * rs:
					continue
				var wa := ws[a]
				var wb := ws[b]
				var wsum := wa + wb
				if wsum <= 0.0:
					continue
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
				xa -= nx * corr * wa
				ya -= ny * corr * wa
				xs[a] = xa
				ys[a] = ya
				xs[b] = xb + nx * corr * wb
				ys[b] += ny * corr * wb
				touch[a] = 1
				touch[b] = 1
				var vxa := vxs[a]
				var vya := vys[a]
				var vxb := vxs[b]
				var vyb := vys[b]
				var vn := (vxb - vxa) * nx + (vyb - vya) * ny
				if vn < 0.0:
					var e := 0.0 if -vn < resting else minf(rest[a], rest[b])
					var jn := -(1.0 + e) * vn / wsum
					vxa -= jn * nx * wa
					vya -= jn * ny * wa
					vxb += jn * nx * wb
					vyb += jn * ny * wb
					# Coulomb friction along the tangent.
					var tx := -ny
					var ty := nx
					var vt := (vxb - vxa) * tx + (vyb - vya) * ty
					var jt := -vt / wsum
					var mu := (fric[a] + fric[b]) * 0.5
					var max_f := jn * mu
					jt = clampf(jt, -max_f, max_f)
					vxs[a] = vxa - jt * tx * wa
					vys[a] = vya - jt * ty * wa
					vxs[b] = vxb + jt * tx * wb
					vys[b] = vyb + jt * ty * wb
		for a in n:
			if flags[a] != _ENABLED:
				continue
			var x := xs[a]
			var y := ys[a]
			var vx := vxs[a]
			var vy := vys[a]
			var r := rr[a]
			for s in ns:
				# Clear of the segment's box (with room to spare for rounding): CircleWorld's
				# distance test would reject it too.
				if x - r > smax_x[s] or x + r < smin_x[s] or y - r > smax_y[s] or y + r < smin_y[s]:
					continue
				# CircleWorld.collide_segment(a, s), inline.
				var ex := sex[s]
				var ey := sey[s]
				var l2 := sl2[s]
				var t := ((x - sx1[s]) * ex + (y - sy1[s]) * ey) / l2 if l2 > 0.0 else 0.0
				t = clampf(t, 0.0, 1.0)
				var dx := x - (sx1[s] + ex * t)
				var dy := y - (sy1[s] + ey * t)
				var d2 := dx * dx + dy * dy
				if d2 >= r * r:
					continue
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
				var pen := r - d
				x += nx * pen
				y += ny * pen
				touch[a] = 1
				var vn := vx * nx + vy * ny
				if vn < 0.0:
					var e := 0.0 if -vn < resting else minf(rest[a], sb[s])
					vx -= (1.0 + e) * vn * nx
					vy -= (1.0 + e) * vn * ny
					var tx := -ny
					var ty := nx
					var vt := vx * tx + vy * ty
					var max_f := -vn * fric[a]
					var jt := clampf(-vt, -max_f, max_f)
					vx += jt * tx
					vy += jt * ty
			if wall:
				var front := wall_front + r
				if y < front:
					y = front
					if vy < wall_v:
						vy = wall_v
			xs[a] = x
			ys[a] = y
			vxs[a] = vx
			vys[a] = vy
			if has_extra:
				_call_constraint(extra, a)
	# Copy back, and leave the list in x order as CircleWorld does.
	if _tmp.size() < n:
		_tmp.resize(n)
	var tmp := _tmp
	for i in n:
		var b := list[ids[i]]
		b.x = xs[i]
		b.y = ys[i]
		b.vx = vxs[i]
		b.vy = vys[i]
		b.touching = touch[i] != 0
		tmp[i] = b
	for i in n:
		list[i] = tmp[i]
		tmp[i] = null


## Runs a general constraint on the body at sorted position [param a]: hands it the body as the
## arrays have it, then takes back whatever the constraint changed.
func _call_constraint(extra: Callable, a: int) -> void:
	var b := bodies[_id[a]]
	b.x = _x[a]
	b.y = _y[a]
	b.vx = _vx[a]
	b.vy = _vy[a]
	b.touching = _touch[a] != 0
	extra.call(b)
	_x[a] = b.x
	_y[a] = b.y
	_vx[a] = b.vx
	_vy[a] = b.vy
	_touch[a] = 1 if b.touching else 0
