extends PaTest
## ui/PhotoBoothPlanTest.kt: the photo booth's poses and its timeline: a 3-2-1 countdown, then four
## shots with a flash each.

func test_there_is_one_pose_for_every_shot_and_they_all_look_different() -> void:
	var plan := PhotoBoothPlan.poses
	assert_eq(PhotoStrip.SHOTS, plan.size())
	var poses: Array[int] = []
	for p in plan:
		poses.append(p.pose)
	# Idle, a cheer and a seat, at least.
	for p: int in [Pose.STAND, Pose.CHEER, Pose.SIT]:
		assert_true(poses.has(p), "no %s shot" % Pose.name_of(p))
	# No two shots alike, in pose and in the way the kid is turned.
	for i in plan.size():
		for j in range(i + 1, plan.size()):
			assert_true(plan[i].pose != plan[j].pose or plan[i].yaw != plan[j].yaw, "shots %d and %d look the same" % [i, j])
	for p in plan:
		assert_true(not p.cry.strip_edges().is_empty() and p.cry.length() <= 14, "'%s'" % p.cry)
		assert_true(absf(p.yaw) < 0.6, "a pose turned right round to the back: %s" % p.yaw)


func test_the_countdown_runs_three_two_one_then_the_shots_start() -> void:
	var s := PhotoBoothPlan.at(0.0)
	assert_eq(3, s.count)
	assert_eq(0, s.captured)
	assert_eq(0, s.pose)
	assert_near(0.0, s.flash, 0.0)
	assert_false(s.done)
	assert_eq(3, PhotoBoothPlan.at(PhotoBoothPlan.TICK - 0.01).count)
	assert_eq(2, PhotoBoothPlan.at(PhotoBoothPlan.TICK).count)
	assert_eq(2, PhotoBoothPlan.at(2 * PhotoBoothPlan.TICK - 0.01).count)
	assert_eq(1, PhotoBoothPlan.at(2 * PhotoBoothPlan.TICK).count)
	assert_eq(1, PhotoBoothPlan.at(3 * PhotoBoothPlan.TICK - 0.01).count)
	# The first shot is taken as the count ends, with a full flash.
	s = PhotoBoothPlan.at(PhotoBoothPlan.shot_time(0))
	assert_eq(0, s.count)
	assert_eq(1, s.captured)
	assert_near(1.0, s.flash, 1e-4)
	# A time before the start is just the start.
	assert_eq(3, PhotoBoothPlan.at(-1.0).count)
	assert_eq(0, PhotoBoothPlan.at(-1.0).captured)


func test_four_shots_are_taken_a_gap_apart_each_with_a_flash_that_fades() -> void:
	assert_near(PhotoBoothPlan.shot_time(0) + PhotoBoothPlan.GAP, PhotoBoothPlan.shot_time(1), 1e-4)
	for i in PhotoStrip.SHOTS:
		var t := PhotoBoothPlan.shot_time(i)
		assert_eq(i, PhotoBoothPlan.at(t - 0.001).captured, "just before shot %d" % i)
		var at := PhotoBoothPlan.at(t)
		assert_eq(i + 1, at.captured, "at shot %d" % i)
		assert_near(1.0, at.flash, 1e-3)
		var half := PhotoBoothPlan.at(t + PhotoBoothPlan.FLASH / 2.0)
		assert_near(0.5, half.flash, 0.02)
		assert_near(0.0, PhotoBoothPlan.at(t + PhotoBoothPlan.FLASH).flash, 1e-4)
	# The flash is done before the next shot, so each is a separate flash.
	assert_true(PhotoBoothPlan.FLASH < PhotoBoothPlan.GAP)
	assert_eq(PhotoStrip.SHOTS, PhotoBoothPlan.at(PhotoBoothPlan.duration).captured)


func test_the_pose_changes_right_after_each_flash_and_stays_on_the_last_one() -> void:
	for i in PhotoStrip.SHOTS:
		# The pose on show while the kid holds still for shot i is pose i...
		assert_eq(i, PhotoBoothPlan.at(PhotoBoothPlan.shot_time(i) - 0.001).pose, "before shot %d" % i)
		# ...and once it's taken the next pose is on show (the last one stays).
		assert_eq(mini(i + 1, PhotoStrip.SHOTS - 1), PhotoBoothPlan.at(PhotoBoothPlan.shot_time(i) + 0.001).pose, "after shot %d" % i)
	assert_eq(PhotoStrip.SHOTS - 1, PhotoBoothPlan.at(PhotoBoothPlan.duration).pose)


func test_the_go_ends_after_the_last_flash_and_a_hold() -> void:
	assert_false(PhotoBoothPlan.at(PhotoBoothPlan.duration - 0.01).done)
	assert_true(PhotoBoothPlan.at(PhotoBoothPlan.duration).done)
	assert_true(PhotoBoothPlan.at(PhotoBoothPlan.duration + 5.0).done)
	assert_true(PhotoBoothPlan.duration >= 6.0 and PhotoBoothPlan.duration <= 12.0, "the go takes %ss" % PhotoBoothPlan.duration)
	assert_true(PhotoBoothPlan.duration >= PhotoBoothPlan.shot_time(PhotoStrip.SHOTS - 1) + PhotoBoothPlan.FLASH)


func test_sweeping_the_timeline_never_goes_backwards() -> void:
	var last_count := 0x7FFFFFFF
	var last_captured := 0
	var last_pose := 0
	var t := 0.0
	var s := PhotoBoothPlan.Step.new()
	while t <= PhotoBoothPlan.duration + 1.0:
		PhotoBoothPlan.at(t, s)
		assert_true(s.count <= last_count, "count went up at %s" % t)
		assert_true(s.captured >= last_captured, "shots went backwards at %s" % t)
		assert_true(s.pose >= last_pose, "pose went backwards at %s" % t)
		assert_true(s.flash >= 0.0 and s.flash <= 1.0, "flash out of range at %s: %s" % [t, s.flash])
		assert_true(s.count >= 0 and s.count <= PhotoBoothPlan.COUNT)
		assert_true(s.captured >= 0 and s.captured <= PhotoStrip.SHOTS)
		assert_true(s.pose >= 0 and s.pose < PhotoStrip.SHOTS)
		# No countdown once the first shot is taken, and no flash during it.
		if s.captured > 0:
			assert_eq(0, s.count)
		if s.count > 0:
			assert_near(0.0, s.flash, 0.0)
		last_count = s.count
		last_captured = s.captured
		last_pose = s.pose
		t += 0.01


# ---------------------------------------------------------------- Godot-only

## Godot-only: the timeline's numbers are build-13's (the countdown of 3 at 0.9 s, shots 1.5 s
## apart, a 0.3 s flash, a 0.7 s hold: 8.2 s a go), and a reused step reads the same as a new one.
func test_the_timeline_is_build_13s_and_a_reused_step_matches() -> void:
	assert_eq(3, PhotoBoothPlan.COUNT)
	assert_near(0.9, PhotoBoothPlan.TICK, 0.0)
	assert_near(1.5, PhotoBoothPlan.GAP, 0.0)
	assert_near(0.3, PhotoBoothPlan.FLASH, 0.0)
	assert_near(0.7, PhotoBoothPlan.HOLD, 0.0)
	assert_near(8.2, PhotoBoothPlan.duration, 1e-6)
	var reused := PhotoBoothPlan.Step.new()
	var t := -0.5
	while t < 9.0:
		var fresh := PhotoBoothPlan.at(t)
		assert_true(fresh.equals(PhotoBoothPlan.at(t, reused)), "at %s: %s vs %s" % [t, fresh, reused])
		t += 0.037
	var cries := PackedStringArray()
	for p in PhotoBoothPlan.poses:
		cries.append(p.cry)
	assert_eq(PackedStringArray(["SMILE!", "HANDS UP!", "TAKE A SEAT!", "CHEERS!"]), cries)
	var kinds: Array[int] = []
	for p in PhotoBoothPlan.poses:
		kinds.append(p.pose)
	assert_eq([Pose.STAND, Pose.CHEER, Pose.SIT, Pose.HOLD], kinds)
