extends PaTest
## hub/FigureLifeTest.kt: idle life: breathing, fidgets, blinking and where the head points.

const DT := RigChecks.DT


func after_each() -> void:
	FigureAnim.reduce_motion = false


## The head's turn from the body's facing (the neck plus what the shoulders took).
func _gaze_yaw(a: FigureAnim) -> float:
	return a.head_yaw + a.twist


## Runs [param a] for [param secs] (seated by default, so no idle fidget can wander in and turn the
## head) calling [param look] each step.
func _settle(a: FigureAnim, secs: float, look: Callable = Callable(), pose: int = Pose.SIT) -> void:
	for i in int(secs / DT):
		if look.is_valid():
			look.call()
		a.update(DT, 0.0, 0.0, 0.0, pose)


func test_the_head_turns_to_what_it_looks_at_and_never_further_than_a_neck_can() -> void:
	# A target 45 degrees to one side is looked at directly (less the shoulders' share, which is added back).
	var a := FigureAnim.new()
	_settle(a, 1.5, func() -> void: a.look(70.0, 70.0))
	assert_near(0.785, _gaze_yaw(a), 0.06, "looked %f at a target at 45 degrees" % _gaze_yaw(a))
	# Further round than a neck allows: as far as it goes, and no further.
	var b := FigureAnim.new()
	_settle(b, 1.5, func() -> void: b.look(-100.0, -10.0))
	var g := _gaze_yaw(b)
	assert_true(g < -0.9 and g > -1.1, "turned %f for a target at 96 degrees" % g)
	# A target directly behind is ignored altogether.
	var c := FigureAnim.new()
	_settle(c, 1.5, func() -> void: c.look(0.0, -100.0))
	assert_true(absf(_gaze_yaw(c)) < 0.12, "turned %f toward something behind" % _gaze_yaw(c))


func test_the_head_tips_up_and_down_to_the_height_of_what_it_sees() -> void:
	var up := FigureAnim.new()
	_settle(up, 1.5, func() -> void: up.look(0.0, 40.0, 90.0))
	var down := FigureAnim.new()
	_settle(down, 1.5, func() -> void: down.look(0.0, 40.0, 5.0))
	var level := FigureAnim.new()
	_settle(level, 1.5, func() -> void: level.look(0.0, 40.0, Figure.HEAD_Y))
	assert_true(up.head_pitch < level.head_pitch - 0.2, "looking up %f" % up.head_pitch)
	assert_true(down.head_pitch > level.head_pitch + 0.2, "looking down %f" % down.head_pitch)
	assert_true(up.head_pitch > -0.6 and down.head_pitch < 0.7, "nodded %f/%f past what a neck can" % [up.head_pitch, down.head_pitch])


func test_the_gaze_eases_in_and_out_and_the_head_never_snaps() -> void:
	var a := FigureAnim.new()
	_settle(a, 0.5)
	var prev := _gaze_yaw(a)
	var biggest := 0.0
	var side := 1.0
	for n in 120 * 8:
		if n % 120 == 0:
			side = -side
		# Look left, then right, then away altogether.
		if n < 120 * 6:
			a.look(side * 60.0, 30.0)
		a.update(DT, 0.0, 0.0, 0.0, Pose.SIT)
		var g := _gaze_yaw(a)
		biggest = maxf(biggest, absf(g - prev))
		prev = g
		if not RigChecks.assert_sane(self, a, "gaze %d" % n):
			return
	assert_true(biggest < 0.1, "head moved %f in one step" % biggest)
	assert_true(absf(_gaze_yaw(a)) < 0.12, "head didn't come back to rest (%f)" % _gaze_yaw(a))


func test_the_head_leads_the_body_into_a_turn() -> void:
	var a := FigureAnim.new()
	a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
	var lead := 0.0
	# The body is asked to turn a quarter of the way round; its head goes first.
	for i in 30:
		a.update(DT, 0.0, 0.0, 0.0, Pose.STAND, 1.5)
		lead = maxf(lead, _gaze_yaw(a))
	assert_true(lead > 0.2, "head led by only %f" % lead)
	assert_true(lead <= 0.55 + 0.05, "head led by %f" % lead)


func _timeline(seed_value: int) -> Array[int]:
	var a := FigureAnim.new(seed_value)
	var marks: Array[int] = []
	var last := 0
	for n in 120 * 60:
		a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
		if a.fidget_count != last:
			marks.append(n)
			last = a.fidget_count
		if not RigChecks.assert_sane(self, a, "idle %d" % n):
			break
	return marks


func test_standing_about_is_full_of_fidgets_that_are_the_same_every_run() -> void:
	var one := _timeline(3)
	assert_true(one.size() >= 5 and one.size() <= 18, "only %d fidgets in a minute" % one.size())
	assert_eq(one, _timeline(3), "fidgets aren't repeatable")
	assert_ne(one, _timeline(4), "two kids fidget in step")


func test_a_walking_kid_does_not_fidget_and_a_sitting_one_does_not_either() -> void:
	var a := FigureAnim.new(6)
	var z := 0.0
	for i in 120 * 20:
		z += 40.0 * DT
		a.update(DT, 0.0, z, 0.0, Pose.WALK)
	assert_eq(0, a.fidget_count)
	var b := FigureAnim.new(6)
	for i in 120 * 20:
		b.update(DT, 0.0, 0.0, 0.0, Pose.SIT)
	assert_eq(0, b.fidget_count)


func test_fidgets_fade_out_when_the_kid_sets_off() -> void:
	var a := FigureAnim.new(8)
	# Stand until a fidget is in full swing (a weight shift shows in the sway)...
	var n := 0
	while a.fidget_count == 0 and n < 120 * 30:
		a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
		n += 1
	assert_true(a.fidget_count > 0)
	for i in 30:
		a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
	# ...then set off: nothing jumps.
	var prev := PackedFloat64Array()
	var now := PackedFloat64Array()
	var z := 0.0
	var biggest := 0.0
	for i in 120:
		z += 40.0 * DT
		RigChecks.joints(a, prev)
		a.update(DT, 0.0, z, 0.0, Pose.WALK)
		RigChecks.joints(a, now)
		for j in now.size():
			biggest = maxf(biggest, absf(now[j] - prev[j]))
		if not RigChecks.assert_sane(self, a, "setting off"):
			return
	assert_true(biggest < 0.2, "a joint jumped %f setting off" % biggest)


func test_kids_blink_every_few_seconds_smoothly() -> void:
	var a := FigureAnim.new(2)
	var blinks := 0
	var shut := false
	var prev := 0.0
	var biggest := 0.0
	var longest := 0
	var run := 0
	for i in 120 * 60:
		a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
		if not (a.blink >= 0.0 and a.blink <= 1.0):
			fail("blink %f" % a.blink)
			return
		if a.blink > 0.9 and not shut:
			blinks += 1
			shut = true
		if a.blink < 0.3:
			shut = false
		run = run + 1 if a.blink > 0.02 else 0
		longest = maxi(longest, run)
		biggest = maxf(biggest, absf(a.blink - prev))
		prev = a.blink
	assert_true(blinks >= 9 and blinks <= 30, "%d blinks in a minute" % blinks)
	assert_true(biggest < 0.3, "an eyelid moved %f in a step" % biggest)
	# A blink is over in a fraction of a second.
	assert_true(longest <= int(0.2 / DT), "a blink lasted %d steps" % longest)


func test_breathing_rises_and_falls_once_every_few_seconds() -> void:
	var a := FigureAnim.new()
	var hi := -1.0
	var lo := 1.0
	var crossings := 0
	var was := 0.0
	for i in 120 * 30:
		a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
		hi = maxf(hi, a.breath)
		lo = minf(lo, a.breath)
		if was < 0.0 and a.breath >= 0.0:
			crossings += 1
		was = a.breath
	assert_true(hi > 0.01 and lo < -0.01 and hi < 0.03 and lo > -0.03, "breath range %f..%f" % [lo, hi])
	# 30 s at one breath every 3.8 s.
	assert_true(crossings >= 7 and crossings <= 8, "%d breaths in 30 s" % crossings)


func test_identical_figures_animate_identically() -> void:
	var a := FigureAnim.new(5)
	var b := FigureAnim.new(5)
	var va := PackedFloat64Array()
	var vb := PackedFloat64Array()
	var z := 0.0
	for n in 120 * 20:
		if n > 400:
			z += 30.0 * DT
		var pose := Pose.WALK if n > 400 else Pose.STAND
		a.look(50.0, 50.0)
		b.look(50.0, 50.0)
		a.update(DT, 0.0, z, 0.0, pose)
		b.update(DT, 0.0, z, 0.0, pose)
	RigChecks.joints(a, va)
	RigChecks.joints(b, vb)
	assert_true(va == vb)
