extends PaTest
## Godot-only: FigureAnim writes Kotlin's Spring, Gait and AnimMath maths out in place (a GDScript
## call costs more than the sums it makes). Here it is checked against FigureAnimReference, the
## line-by-line port that calls those helpers: every joint, the stride and the fidgets agree
## through a long random run at every frame time, with and without reduced motion.


func after_each() -> void:
	FigureAnim.reduce_motion = false
	FigureAnimReference.reduce_motion = false


static func _values(a: Object) -> PackedFloat64Array:
	return PackedFloat64Array([a.yaw, a.root_y, a.sway, a.lean, a.lean_roll, a.twist, a.breath, a.breath_lift,
		a.head_yaw, a.head_pitch, a.head_roll, a.hat_pitch, a.hat_roll, a.hat_lift, a.tail_pitch, a.tail_roll,
		a.blink, a.item_amount, a.arm_pitch[0], a.arm_roll[0], a.leg_pitch[0], a.leg_lift[0],
		a.arm_pitch[1], a.arm_roll[1], a.leg_pitch[1], a.leg_lift[1], a.phase, a.speed, a.clock,
		float(a.fidget_count), 1.0 if a.stepped else 0.0])


func test_the_inlined_rig_matches_the_line_by_line_port() -> void:
	for reduced: bool in [false, true]:
		FigureAnim.reduce_motion = reduced
		FigureAnimReference.reduce_motion = reduced
		for dt: float in [1.0 / 120.0, 1.0 / 60.0, 1.0 / 30.0, 0.1, 0.5]:
			var rng := KRandom.new(21)
			var a := FigureAnim.new(9)
			var b := FigureAnimReference.new(9)
			var x := 0.0
			var z := 0.0
			var yaw := 0.0
			var speed := 0.0
			var pose := Pose.STAND
			var speeds: Array[float] = [0.0, 0.0, 20.0, 44.0, 80.0, 110.0]
			var worst := 0.0
			for n in 2400:
				if n % 37 == 0:
					pose = Pose.ALL[rng.next_int_until(Pose.COUNT)]
				if n % 61 == 0:
					speed = speeds[rng.next_int_until(speeds.size())]
				if n % 90 == 0:
					yaw = rng.next_float() * 6.28 - 3.14
				if n % 47 == 0:
					var lx := x + rng.next_float() * 200.0 - 100.0
					var lz := z + rng.next_float() * 200.0 - 100.0
					var ly := rng.next_float() * 90.0
					a.look(lx, lz, ly)
					b.look(lx, lz, ly)
				if n % 700 == 0:
					x += 300.0
					z -= 200.0
				if n == 1200:
					a.set_static(Pose.CARRY, 1.3, 0.4, 0.2)
					b.set_static(Pose.CARRY, 1.3, 0.4, 0.2)
				if n == 1800:
					a.prime(x, z, 1.0, Pose.SIT)
					b.prime(x, z, 1.0, Pose.SIT)
				x += speed * dt * sin(yaw)
				z += speed * dt * cos(yaw)
				a.update(dt, x, z, yaw, pose)
				b.update(dt, x, z, yaw, pose)
				var va := _values(a)
				var vb := _values(b)
				for i in va.size():
					worst = maxf(worst, absf(va[i] - vb[i]))
			assert_true(worst < 1e-9, "reduced %s, dt %f: the rigs differ by %s" % [reduced, dt, str(worst)])
