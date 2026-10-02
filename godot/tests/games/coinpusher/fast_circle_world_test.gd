extends PaTest
## Godot-only: [FastCircleWorld] is CircleWorld run on packed arrays, and must leave every body
## exactly where CircleWorld does (same order of operations, same 64-bit arithmetic): positions,
## velocities, roll angles, contact flags and the order of the body list, after every step of a
## coin-pusher deck with its moving shelf and of a claw-machine pile with a body held in the claw.


## Two copies of the same bodies, one per world.
func _twin(slow: CircleWorld, fast: CircleWorld, x: float, y: float, r: float) -> Array:
	var out: Array = []
	for w: CircleWorld in [slow, fast]:
		var b := CircleWorld.Body.new(x, y, r)
		w.bodies.append(b)
		out.append(b)
	return out


func _same_segments(slow: CircleWorld, fast: CircleWorld, segs: Array) -> void:
	for s: Array in segs:
		slow.segments.append(CircleWorld.Segment.new(s[0], s[1], s[2], s[3], s[4]))
		fast.segments.append(CircleWorld.Segment.new(s[0], s[1], s[2], s[3], s[4]))


## The first difference between the two worlds, or "" if they match bit for bit.
func _diff(slow: CircleWorld, fast: CircleWorld) -> String:
	if slow.bodies.size() != fast.bodies.size():
		return "body counts %d vs %d" % [slow.bodies.size(), fast.bodies.size()]
	for i in slow.bodies.size():
		var a := slow.bodies[i]
		var b := fast.bodies[i]
		if a.tag != b.tag:
			return "list order differs at %d (body %d vs %d)" % [i, a.tag, b.tag]
		if a.x != b.x or a.y != b.y or a.vx != b.vx or a.vy != b.vy or a.angle != b.angle or a.touching != b.touching:
			return "body %d: (%s, %s, %s, %s, %s, %s) vs (%s, %s, %s, %s, %s, %s)" % [a.tag,
				a.x, a.y, a.vx, a.vy, a.angle, a.touching, b.x, b.y, b.vx, b.vy, b.angle, b.touching]
	return ""


func test_a_pusher_deck_moves_exactly_as_in_circle_world() -> void:
	var slow := CircleWorld.new()
	var fast := FastCircleWorld.new()
	for w: CircleWorld in [slow, fast]:
		w.iterations = 5
		w.resting_speed = 1000.0
	_same_segments(slow, fast, [[30.0, 52.0, 30.0, 492.0, 0.2], [330.0, 52.0, 330.0, 492.0, 0.2]])
	var front := [150.0, 0.0]
	slow.constraint = func(b: CircleWorld.Body) -> void:
		var f: float = front[0] + b.r
		if b.y < f:
			b.y = f
			if b.vy < front[1]:
				b.vy = front[1]
	fast.push_wall = true
	var rng := KRandom.new(5)
	var tag := 0
	var y := 552.2
	var row := 0
	while y > 249.0:
		var x := 44.0 + (0.0 if row % 2 == 0 else 13.3)
		while x < 316.0:
			if rng.next_float() > 0.06:
				var radius := 13.0
				var roll := rng.next_float()
				if roll < 0.04:
					radius = 20.0
				elif roll < 0.08:
					radius = 12.0
				for b: CircleWorld.Body in _twin(slow, fast, x + rng.range_f(-1.5, 1.5), y + rng.range_f(-1.5, 1.5), radius):
					b.damping = 7.0
					b.friction = 0.5
					b.restitution = 0.05
					b.mass = 2.5 if radius == 20.0 else 1.0
					b.tag = tag
			tag += 1
			x += 26.6
		y -= 23.04
		row += 1
	assert_gt(slow.bodies.size(), 120, "a full deck")
	var phase := 0.0
	for s in 480:
		phase += GameLoop.FIXED_DT / 3.2 * TAU
		var nf := 191.0 - cos(phase) * 41.0
		front[1] = (nf - front[0]) / GameLoop.FIXED_DT
		front[0] = nf
		fast.push_front = nf
		fast.push_v = front[1]
		# Now and then a coin lands behind the shelf, as a drop does.
		if s % 40 == 7:
			for b: CircleWorld.Body in _twin(slow, fast, 60.0 + rng.next_float() * 240.0, nf + 13.0 + rng.range_f(4.0, 26.0), 13.0):
				b.damping = 7.0
				b.friction = 0.5
				b.restitution = 0.05
				b.vy = 120.0
				b.tag = tag
			tag += 1
		slow.step(GameLoop.FIXED_DT)
		fast.step(GameLoop.FIXED_DT)
		var d := _diff(slow, fast)
		if d != "":
			fail("step %d: %s" % [s, d])
			return


func test_a_claw_pile_moves_exactly_as_in_circle_world() -> void:
	var slow := CircleWorld.new(0.0, 900.0)
	var fast := FastCircleWorld.new(0.0, 900.0)
	_same_segments(slow, fast, [[88.0, 470.0, 348.0, 470.0, 0.1], [348.0, 44.0, 348.0, 470.0, 0.2],
		[88.0, 392.0, 88.0, 470.0, 0.2], [12.0, 44.0, 12.0, 392.0, 0.2]])
	var rng := KRandom.new(9)
	var held: Array = []
	for i in 14:
		var r := 21.0 + rng.next_float() * 9.0
		var pair := _twin(slow, fast, rng.range_f(88.0 + r + 6.0, 348.0 - r - 4.0), 440.0 - (i / 5) * 55.0 - rng.range_f(0.0, 30.0), r)
		for b: CircleWorld.Body in pair:
			b.restitution = 0.12
			b.friction = 0.9
			b.damping = 0.9
			b.tag = i
		if i == 3:
			held = pair
	for s in 900:
		# From step 420 one body hangs in the claw: kinematic, carried by game code.
		if s == 420:
			for b: CircleWorld.Body in held:
				b.kinematic = true
		if s >= 420:
			for b: CircleWorld.Body in held:
				b.x = 200.0 - (s - 420) * 0.2
				b.y = 300.0 - sin(s * 0.05) * 20.0
				b.vx = -24.0
				b.vy = 0.0
		if s == 700:
			for b: CircleWorld.Body in held:
				b.kinematic = false
				b.vy = 40.0
		slow.step(GameLoop.FIXED_DT)
		fast.step(GameLoop.FIXED_DT)
		var d := _diff(slow, fast)
		if d != "":
			fail("step %d: %s" % [s, d])
			return


func test_a_general_constraint_still_applies() -> void:
	var slow := CircleWorld.new(0.0, 400.0)
	var fast := FastCircleWorld.new(0.0, 400.0)
	var bounce := func(b: CircleWorld.Body) -> void:
		if b.y > 100.0 - b.r:
			b.y = 100.0 - b.r
			b.vy = -absf(b.vy) * 0.5
	slow.constraint = bounce
	fast.constraint = bounce
	var rng := KRandom.new(3)
	for i in 12:
		for b: CircleWorld.Body in _twin(slow, fast, 10.0 + rng.next_float() * 80.0, rng.next_float() * 50.0, 6.0):
			b.tag = i
	for s in 400:
		slow.step(GameLoop.FIXED_DT)
		fast.step(GameLoop.FIXED_DT)
		var d := _diff(slow, fast)
		if d != "":
			fail("step %d: %s" % [s, d])
			return
