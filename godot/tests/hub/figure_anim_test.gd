extends PaTest
## hub/FigureAnimTest.kt: the animation rig's poses: joints stay finite and inside what a body can
## do, poses change without snapping, and sitting settles the way the tuning comments say.

const DT := RigChecks.DT


func after_each() -> void:
	FigureAnim.reduce_motion = false


func test_the_rigs_axes_are_what_the_tuning_assumes() -> void:
	# Positive pitch tips the top forwards (+z) and the face down; negative swings a hanging limb forwards.
	var xf := Xform.new().set_xf(0.0, 0.0, 0.0, 0.0, 0.3)
	assert_true(xf.z(0.0, 1.0, 0.0) > 0.2, "a forward lean is positive pitch")
	assert_true(xf.y(0.0, 0.0, 1.0) < -0.2, "looking down is positive pitch")
	assert_true(Xform.new().set_xf(0.0, 0.0, 0.0, 0.0, -0.3).z(0.0, -1.0, 0.0) > 0.2, "a hanging arm swings forwards with negative pitch")
	# Positive roll tips the top toward -x; positive yaw turns the nose toward +x.
	assert_true(Xform.new().set_xf(0.0, 0.0, 0.0, 0.0, 0.0, 0.3).x(0.0, 1.0, 0.0) < -0.2)
	assert_true(Xform.new().set_xf(0.0, 0.0, 0.0, 0.3).x(0.0, 0.0, 1.0) > 0.2)


func test_a_still_pose_is_solved_instantly_and_sanely() -> void:
	var a := FigureAnim.new()
	for p in Pose.ALL:
		a.set_static(p, 1.3, 0.7, 0.4)
		RigChecks.assert_sane(self, a, "static %s" % Pose.name_of(p))
		assert_eq(0.4, a.yaw)
	a.set_static(Pose.SIT, 0.0, 0.0, 0.0)
	assert_near(-FigureAnim.SEAT_LEG, a.leg_pitch[0], 1e-4)
	assert_near(-FigureAnim.SEAT_DROP, a.root_y, 0.05)
	a.set_static(Pose.HOLD, 0.0, 0.0, 0.0)
	assert_near(1.0, a.item_amount, 1e-5)
	a.set_static(Pose.STAND, 0.0, 0.0, 0.0)
	assert_eq(0.0, a.item_amount)


func test_a_still_walk_has_its_legs_swinging_and_a_still_stand_does_not() -> void:
	var a := FigureAnim.new()
	a.set_static(Pose.WALK, 0.0, 1.0, 0.0)
	assert_true(absf(a.leg_pitch[0]) > 0.1 and a.leg_pitch[0] * a.leg_pitch[1] < 0.0, "a still walk isn't walking")
	a.set_static(Pose.STAND, 0.0, 1.0, 0.0)
	assert_near(0.0, a.leg_pitch[0], 1e-6)
	assert_near(0.0, a.leg_pitch[1], 1e-6)


func test_changing_pose_never_snaps_any_joint() -> void:
	# Flip poses at random for a minute and watch the biggest one-step change of every joint.
	var rng := KRandom.new(3)
	var a := FigureAnim.new(4)
	var prev := PackedFloat64Array()
	var now := PackedFloat64Array()
	var biggest := 0.0
	var where := ""
	var pose := Pose.STAND
	var z := 100.0
	for n in 120 * 60:
		if n % 90 == 0:
			pose = Pose.ALL[rng.next_int_until(Pose.COUNT)]
		if pose == Pose.WALK or pose == Pose.CARRY:
			z += 40.0 * DT
		RigChecks.joints(a, prev)
		a.update(DT, 50.0, z, 0.0, pose)
		RigChecks.joints(a, now)
		if not RigChecks.assert_sane(self, a, "step %d (%s)" % [n, Pose.name_of(pose)]):
			return
		if n > 0:
			for i in now.size():
				var d := absf(now[i] - prev[i])
				if d > biggest:
					biggest = d
					where = "%s at step %d (%s)" % [RigChecks.JOINT_NAMES[i], n, Pose.name_of(pose)]
	# A cheer's arms travel about 2.6 rad in 0.14 s (peaking at 1.5 times the average speed), so a
	# step of 1/120 s is a fraction of that.
	assert_true(biggest < 0.3, "%s moved %f in one step" % [where, biggest])


func test_sitting_down_settles_with_a_small_overshoot_and_standing_up_does_not() -> void:
	var a := FigureAnim.new()
	a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
	var deepest := 0.0
	var last := 0.0
	for i in 120 * 2:
		a.update(DT, 0.0, 0.0, 0.0, Pose.SIT)
		deepest = minf(deepest, a.root_y)
		last = a.root_y
	assert_near(-FigureAnim.SEAT_DROP, last, 0.05)
	var overshoot := (-deepest - FigureAnim.SEAT_DROP) / FigureAnim.SEAT_DROP
	assert_true(overshoot > 0.02, "no settle at all (%f)" % overshoot)
	assert_true(overshoot < 0.12, "a plop, not a settle (%f)" % overshoot)


func test_portraits_keep_their_old_poses() -> void:
	# The prize counter and the photo booth pose figures from scratch: their arms must be where they always were.
	var a := FigureAnim.new()
	var t := 1.7
	for side in 2:
		var sd := -1.0 if side == 0 else 1.0
		a.set_static(Pose.STAND, t, 0.0, 0.0)
		assert_near(sin(t * 1.3 + sd) * 0.05, a.arm_pitch[side], 1e-4)
		assert_near(0.1 * sd, a.arm_roll[side], 1e-4)
		a.set_static(Pose.CHEER, t, 0.0, 0.0)
		assert_near(-2.6 + sin(t * 9.0 + sd) * 0.25, a.arm_pitch[side], 1e-4)
		assert_near(0.2 * sd, a.arm_roll[side], 1e-4)
		assert_near(absf(sin(t * 9.0)) * 2.5, a.root_y, 1e-4)
		a.set_static(Pose.SIT, t, 0.0, 0.0)
		assert_near(-0.6, a.arm_pitch[side], 1e-4)
		assert_near(-FigureAnim.SEAT_LEG, a.leg_pitch[side], 1e-4)
	a.set_static(Pose.HOLD, t, 0.0, 0.0)
	assert_near(-1.25 + sin(t * 2.0) * 0.04, a.arm_pitch[1], 1e-4)
	assert_near(sin(t * 1.3) * 0.05, a.arm_pitch[0], 1e-4)
	# A portrait has no lean, no tilt, no turned head, no closed eyes and a hat sitting straight.
	for p: int in [Pose.STAND, Pose.CHEER, Pose.SIT, Pose.HOLD]:
		a.set_static(p, t, 0.0, 0.0)
		assert_near(0.0, a.lean_roll, 0.02)
		assert_near(0.0, a.head_yaw, 1e-4)
		assert_eq(0.0, a.blink)
		assert_eq(0.0, a.hat_pitch)
		assert_eq(0.0, a.hat_roll)
		assert_eq(0.0, a.tail_pitch)


func test_a_primed_figure_faces_the_right_way_from_the_first_frame() -> void:
	var a := FigureAnim.new()
	a.prime(300.0, 500.0, 2.2, Pose.STAND)
	assert_eq(2.2, a.yaw)
	# Its first real update from where it stands doesn't look like a sprint.
	a.update(DT, 300.0, 500.0, 2.2, Pose.STAND)
	assert_near(0.0, a.speed, 1e-3)
	assert_near(2.2, a.yaw, 1e-4)
