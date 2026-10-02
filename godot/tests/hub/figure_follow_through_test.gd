extends PaTest
## hub/FigureFollowThroughTest.kt: secondary motion: hats, ponytails and arms lag and settle;
## sitting and cheering have a beat before and after.

const DT := RigChecks.DT


func after_each() -> void:
	FigureAnim.reduce_motion = false


func _standing(seed_value: int = 1) -> FigureAnim:
	var a := FigureAnim.new(seed_value)
	for i in 120:
		a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
	return a


## Sets a standing [param a] off at [param speed] for [param secs]; calls [param each] after every step.
func _set_off(a: FigureAnim, speed: float, secs: float, each: Callable) -> void:
	var z := 0.0
	for i in int(secs / DT):
		z += speed * DT
		a.update(DT, 0.0, z, 0.0, Pose.WALK)
		each.call()


func test_a_hat_tips_back_when_the_kid_sets_off_and_forward_when_they_stop() -> void:
	var a := _standing()
	var backmost := [0.0]
	_set_off(a, 44.0, 0.5, func() -> void: backmost[0] = minf(backmost[0], a.hat_pitch))
	assert_true(backmost[0] < -0.03, "the hat barely moved on setting off (%f)" % backmost[0])
	assert_true(backmost[0] >= -0.16 - 1e-6, "the hat tipped too far (%f)" % backmost[0])
	# Keep walking at a steady pace: it settles.
	var z := 44.0 * 0.5
	for i in 240:
		z += 44.0 * DT
		a.update(DT, 0.0, z, 0.0, Pose.WALK)
	assert_near(0.0, a.hat_pitch, 0.02, "the hat never settled")
	# Stopping throws it forward.
	var forward := 0.0
	for i in 60:
		a.update(DT, 0.0, z, 0.0, Pose.STAND)
		forward = maxf(forward, a.hat_pitch)
	assert_true(forward > 0.03, "the hat didn't swing forward on stopping (%f)" % forward)


func test_a_ponytail_swings_out_behind_on_setting_off_and_sways_with_the_head() -> void:
	var a := _standing()
	var back := [0.0]
	_set_off(a, 44.0, 0.5, func() -> void: back[0] = maxf(back[0], a.tail_pitch))
	assert_true(back[0] > 0.04, "the ponytail barely moved (%f)" % back[0])
	# Turning the head sends it swaying sideways.
	var b := FigureAnim.new(2)
	for i in 120:
		b.update(DT, 0.0, 0.0, 0.0, Pose.SIT)
	var side := 0.0
	for i in 120:
		b.look(90.0, 30.0)
		b.update(DT, 0.0, 0.0, 0.0, Pose.SIT)
		side = maxf(side, absf(b.tail_roll))
	assert_true(side > 0.05, "the ponytail ignored the head turning (%f)" % side)


func _highest(reduced: bool) -> float:
	FigureAnim.reduce_motion = reduced
	var a := _standing()
	var top := 0.0
	for i in 120:
		a.update(DT, 0.0, 0.0, 0.0, Pose.CHEER)
		top = minf(top, a.arm_pitch[1])
	FigureAnim.reduce_motion = false
	return top


func test_arms_settle_onto_a_cheer_with_a_little_overshoot_unless_motion_is_reduced() -> void:
	var full := _highest(false)
	var calm := _highest(true)
	assert_true(full < calm - 0.04, "no overshoot on a cheer (%f vs %f)" % [full, calm])
	assert_true(full >= -3.05, "the arms went past straight up (%f)" % full)


func test_a_cheer_is_preceded_by_a_crouch_and_arms_sweeping_back() -> void:
	var a := _standing()
	var lowest := 0.0
	var backmost := 0.0
	var first_up := -1
	for n in 60:
		a.update(DT, 0.0, 0.0, 0.0, Pose.CHEER)
		lowest = minf(lowest, a.root_y)
		backmost = maxf(backmost, a.arm_pitch[1])
		if first_up < 0 and a.arm_pitch[1] < -1.0:
			first_up = n
		RigChecks.assert_sane(self, a, "cheer %d" % n)
	assert_true(lowest < -0.5, "no crouch before the cheer (%f)" % lowest)
	assert_true(backmost > 0.1, "the arms didn't wind back (%f)" % backmost)
	assert_true(first_up > 0, "the arms never went up")
	# Reduce motion goes straight to the cheer.
	FigureAnim.reduce_motion = true
	var b := _standing(3)
	var low := 0.0
	for i in 20:
		b.update(DT, 0.0, 0.0, 0.0, Pose.CHEER)
		low = minf(low, b.root_y)
	FigureAnim.reduce_motion = false
	assert_true(low > -0.05, "reduce motion still crouches (%f)" % low)


func test_a_cheer_cancelled_before_it_starts_leaves_nothing_behind() -> void:
	var a := _standing()
	for i in 6:
		a.update(DT, 0.0, 0.0, 0.0, Pose.CHEER)
	for i in 240:
		a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
	assert_near(0.0, a.root_y, 0.1)
	assert_near(0.0, a.arm_pitch[1], 0.15)


func test_sitting_down_tips_the_body_forward_while_moving_and_standing_up_does_too() -> void:
	var a := _standing()
	var down := 0.0
	for i in 120:
		a.update(DT, 0.0, 0.0, 0.0, Pose.SIT)
		down = maxf(down, a.lean)
	assert_true(down > 0.05, "no lean going down (%f)" % down)
	assert_near(0.0, a.lean, 0.02, "didn't settle upright seated")
	var up := 0.0
	for i in 120:
		a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
		up = maxf(up, a.lean)
	assert_true(up > 0.05, "no lean getting up (%f)" % up)
	assert_true(up < 0.3, "leaned too far (%f)" % up)


func _swing(reduced: bool) -> float:
	FigureAnim.reduce_motion = reduced
	var a := _standing()
	var peak := [0.0]
	_set_off(a, 60.0, 0.6, func() -> void: peak[0] = maxf(peak[0], maxf(absf(a.hat_pitch), absf(a.tail_pitch) * 0.3)))
	FigureAnim.reduce_motion = false
	return peak[0]


func test_reduce_motion_calms_the_follow_through() -> void:
	var full := _swing(false)
	var calm := _swing(true)
	assert_true(calm < full * 0.6, "reduce motion didn't calm the hat (%f vs %f)" % [calm, full])


func test_anything_a_kid_can_do_in_any_order_stays_in_range() -> void:
	var rng := KRandom.new(21)
	for reduced: bool in [false, true]:
		FigureAnim.reduce_motion = reduced
		var a := FigureAnim.new(9)
		var x := 0.0
		var z := 0.0
		var yaw := 0.0
		var speed := 0.0
		var pose := Pose.STAND
		var speeds: Array[float] = [0.0, 0.0, 20.0, 44.0, 80.0, 110.0]
		for n in 120 * 120:
			if n % 37 == 0:
				pose = Pose.ALL[rng.next_int_until(Pose.COUNT)]
			if n % 61 == 0:
				speed = speeds[rng.next_int_until(speeds.size())]
			if n % 90 == 0:
				yaw = rng.next_float() * 6.28 - 3.14
			if n % 47 == 0:
				var lx := x + rng.next_float() * 200.0 - 100.0
				var lz := z + rng.next_float() * 200.0 - 100.0
				a.look(lx, lz, rng.next_float() * 90.0)
			if n % 700 == 0:
				x += 300.0
				z -= 200.0
			x += speed * DT * sin(yaw)
			z += speed * DT * cos(yaw)
			a.update(DT, x, z, yaw, pose)
			if not RigChecks.assert_sane(self, a, "chaos %d (%s, %f, reduced=%s)" % [n, Pose.name_of(pose), speed, reduced]):
				FigureAnim.reduce_motion = false
				return
	FigureAnim.reduce_motion = false


func test_the_rig_stays_stable_at_any_frame_time() -> void:
	# A slow phone or a long hitch: the springs must not blow up at 10, 30 or 100 ms steps.
	for dt: float in [1.0 / 240.0, 1.0 / 60.0, 1.0 / 30.0, 0.1, 0.5]:
		var a := FigureAnim.new(4)
		var rng := KRandom.new(2)
		var z := 0.0
		var pose := Pose.STAND
		for n in 600:
			if n % 20 == 0:
				pose = Pose.ALL[rng.next_int_until(Pose.COUNT)]
			z += 40.0 * dt
			a.update(dt, 0.0, z, 0.0, pose)
			if not RigChecks.assert_sane(self, a, "dt=%f step %d" % [dt, n]):
				return
