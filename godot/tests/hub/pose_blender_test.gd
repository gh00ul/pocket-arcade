extends PaTest
## hub/PoseBlenderTest.kt: poses cross-fade: weights always sum to 1, move smoothly and
## monotonically, and survive being interrupted.


func _assert_valid(b: PoseBlender, tag: String) -> bool:
	var sum := 0.0
	var ok := true
	for w in b.weights:
		if is_nan(w):
			fail("%s: NaN weight" % tag)
			ok = false
		if not (w >= -1e-6 and w <= 1.0 + 1e-6):
			fail("%s: weight %f out of range" % [tag, w])
			ok = false
		sum += w
	if absf(sum - 1.0) > 1e-4:
		fail("%s: weights sum %f" % [tag, sum])
		ok = false
	return ok


func test_starts_fully_in_its_initial_pose() -> void:
	var b := PoseBlender.new(Pose.SIT)
	assert_eq(1.0, b.weight(Pose.SIT))
	assert_eq(0.0, b.weight(Pose.STAND))
	assert_true(b.settled)
	_assert_valid(b, "start")


func test_every_blend_time_is_in_the_quick_but_not_popping_range() -> void:
	for p in Pose.ALL:
		var t := PoseBlender.blend_time(p)
		assert_true(t >= PoseBlender.BLEND_MIN - 1e-6 and t <= PoseBlender.BLEND_MAX + 1e-6, "%s blends in %f s" % [Pose.name_of(p), t])


func test_a_blend_moves_the_target_up_and_everything_else_down_and_finishes_in_blend_time() -> void:
	for from in Pose.ALL:
		for to in Pose.ALL:
			if from == to:
				continue
			var tag := "%s->%s" % [Pose.name_of(from), Pose.name_of(to)]
			var b := PoseBlender.new(from)
			b.set_pose(to)
			var prev_to := b.weight(to)
			var prev_from := b.weight(from)
			var dt := 1.0 / 120.0
			var steps := int(PoseBlender.blend_time(to) / dt) + 2
			for i in steps:
				b.update(dt)
				if not _assert_valid(b, tag):
					return
				if not (b.weight(to) >= prev_to - 1e-6):
					fail("%s target weight fell" % tag)
					return
				if not (b.weight(from) <= prev_from + 1e-6):
					fail("%s source weight rose" % tag)
					return
				prev_to = b.weight(to)
				prev_from = b.weight(from)
			assert_true(b.settled, "%s not settled" % tag)
			assert_eq(1.0, b.weight(to), tag)
			assert_eq(0.0, b.weight(from), tag)


func test_the_ease_is_smooth_at_both_ends() -> void:
	var b := PoseBlender.new(Pose.STAND)
	b.set_pose(Pose.WALK)
	var total := PoseBlender.blend_time(Pose.WALK)
	# Half way through, a smoothstep is at one half.
	b.update(total / 2.0)
	assert_near(0.5, b.weight(Pose.WALK), 0.02)
	# The first sliver moves next to nothing (no pop at the start)...
	var c := PoseBlender.new(Pose.STAND)
	c.set_pose(Pose.WALK)
	c.update(total * 0.02)
	assert_true(c.weight(Pose.WALK) < 0.01, "popped %f" % c.weight(Pose.WALK))
	# ...and it arrives without a knock at the end too.
	var d := PoseBlender.new(Pose.STAND)
	d.set_pose(Pose.WALK)
	d.update(total * 0.98)
	assert_true(d.weight(Pose.WALK) > 0.99, "arrived with a jolt %f" % d.weight(Pose.WALK))


func test_asking_for_the_pose_already_coming_changes_nothing() -> void:
	var b := PoseBlender.new(Pose.STAND)
	b.set_pose(Pose.PLAY)
	b.update(0.05)
	var w := b.weight(Pose.PLAY)
	b.set_pose(Pose.PLAY)
	assert_eq(w, b.weight(Pose.PLAY))
	b.update(0.02)
	assert_true(b.weight(Pose.PLAY) > w)


func test_an_interruption_blends_on_from_the_middle_without_a_jump() -> void:
	var b := PoseBlender.new(Pose.STAND)
	b.set_pose(Pose.CHEER)
	b.update(PoseBlender.blend_time(Pose.CHEER) * 0.5)
	var cheer_before := b.weight(Pose.CHEER)
	var stand_before := b.weight(Pose.STAND)
	assert_true(cheer_before > 0.3 and cheer_before < 0.7)
	# Change our mind: nothing on screen jumps at the moment of the change.
	b.set_pose(Pose.SIT)
	assert_near(cheer_before, b.weight(Pose.CHEER), 1e-5)
	assert_near(stand_before, b.weight(Pose.STAND), 1e-5)
	_assert_valid(b, "interrupted")
	# The abandoned poses fade out and the new one comes in, all summing to 1.
	var prev_cheer := cheer_before
	var prev_sit := 0.0
	for i in 60:
		b.update(1.0 / 120.0)
		_assert_valid(b, "interrupted")
		assert_true(b.weight(Pose.CHEER) <= prev_cheer + 1e-6)
		assert_true(b.weight(Pose.SIT) >= prev_sit - 1e-6)
		prev_cheer = b.weight(Pose.CHEER)
		prev_sit = b.weight(Pose.SIT)
	assert_eq(1.0, b.weight(Pose.SIT))
	assert_eq(0.0, b.weight(Pose.CHEER))


func test_no_pose_makes_a_step_bigger_than_the_blend_allows() -> void:
	# Per-step change in any weight stays small however the poses are flipped about.
	var rng := KRandom.new(11)
	var b := PoseBlender.new()
	var dt := 1.0 / 120.0
	var prev := PackedFloat64Array()
	prev.resize(Pose.COUNT)
	for n in 20000:
		if rng.next_int_until(40) == 0:
			b.set_pose(Pose.ALL[rng.next_int_until(Pose.COUNT)])
		for i in Pose.COUNT:
			prev[i] = b.weights[i]
		b.update(dt)
		for i in Pose.COUNT:
			if not (absf(b.weights[i] - prev[i]) < 0.12):
				fail("weight %d jumped %f -> %f" % [i, prev[i], b.weights[i]])
				return
		if not _assert_valid(b, "fuzz"):
			return


func test_odd_frame_times_never_break_the_weights() -> void:
	var rng := KRandom.new(5)
	var b := PoseBlender.new()
	var dts: Array[float] = [0.0, -1.0, NAN, 1e-9, 1.0 / 240.0, 1.0 / 30.0, 0.5, 100.0]
	for n in 5000:
		if rng.next_int_until(7) == 0:
			b.set_pose(Pose.ALL[rng.next_int_until(Pose.COUNT)])
		b.update(dts[rng.next_int_until(dts.size())])
		if not _assert_valid(b, "odd dt"):
			return
	# A huge frame just finishes the blend.
	b.set_pose(Pose.WAVE)
	b.update(100.0)
	assert_eq(1.0, b.weight(Pose.WAVE))


func test_snap_jumps_straight_there() -> void:
	var b := PoseBlender.new(Pose.STAND)
	b.set_pose(Pose.SIT)
	b.update(0.05)
	b.snap(Pose.CHEER)
	assert_eq(1.0, b.weight(Pose.CHEER))
	assert_true(b.settled)
	_assert_valid(b, "snap")
