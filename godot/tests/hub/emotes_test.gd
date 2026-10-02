extends PaTest
## hub/EmotesTest.kt: waves and cheers: rare, seeded, repeatable, and they only ever change the pose
## a kid shows.

const DT := RigChecks.DT


func after_each() -> void:
	FigureAnim.reduce_motion = false


## Drives [param e] through [param encounters] meetings with the player (each [param meet] seconds
## long, [param gap] seconds apart); returns the poses seen (Pose.NONE for none).
func _meetings(e: Emotes, encounters: int, meet: float = 3.0, gap: float = 25.0, standing: bool = true) -> Array[int]:
	var seen: Array[int] = []
	for i in encounters:
		var t := 0.0
		var waved := Pose.NONE
		while t < meet:
			var p := e.update(0.05, true, standing, false, false)
			if p != Pose.NONE:
				waved = p
			t += 0.05
		seen.append(waved)
		t = 0.0
		while t < gap:
			e.update(0.05, false, standing, false, false)
			t += 0.05
	return seen


## A kid who has been about a while (long enough that their first-wave cooldown, which staggers a crowd, is over).
func _settled(seed_value: int) -> Emotes:
	var e := Emotes.new(seed_value)
	for i in 240:
		e.update(0.05, false, true, false, false)
	return e


func test_a_kid_waves_at_some_of_the_players_passings_but_not_all() -> void:
	var waves := 0
	var total := 0
	for seed_value in range(1, 41):
		var seen := _meetings(Emotes.new(seed_value), 10)
		for p in seen:
			if p == Pose.WAVE:
				waves += 1
			assert_true(p == Pose.NONE or p == Pose.WAVE)
		total += seen.size()
	var rate := float(waves) / total
	assert_true(rate >= 0.2 and rate <= 0.5, "waved at %f of encounters" % rate)


func test_a_wave_lasts_about_as_long_as_set_and_starts_after_a_moments_hesitation() -> void:
	# Find a kid who waves at the first meeting.
	for seed_value in range(1, 201):
		var e := _settled(seed_value)
		var t := 0.0
		var start := -1.0
		var end := -1.0
		while t < 6.0:
			var p := e.update(0.02, true, true, false, false)
			if p == Pose.WAVE and start < 0.0:
				start = t
			if p == Pose.WAVE:
				end = t
			t += 0.02
		if start >= 0.0:
			assert_true(start >= Emotes.WAVE_DELAY - 0.03, "waved at once (%f)" % start)
			assert_true(start <= Emotes.WAVE_DELAY + Emotes.WAVE_DELAY_SPREAD + 0.05, "hesitated too long (%f)" % start)
			assert_near(Emotes.WAVE_TIME, end - start, 0.1)
			return
	fail("no kid waved at a first meeting in 200 tries")


func test_a_kid_does_not_wave_twice_in_a_row_and_never_while_busy() -> void:
	for seed_value in range(1, 61):
		# Never waves while walking or holding something (standing = false), however often we meet.
		var busy := _meetings(Emotes.new(seed_value), 20, 3.0, 25.0, false)
		for p in busy:
			assert_eq(Pose.NONE, p)
		# Meeting again straight after a wave is met with a straight face (the cooldown).
		var e := _settled(seed_value)
		var first := _meetings(e, 1, 6.0, 0.5)
		if first[0] == Pose.WAVE:
			var again := _meetings(e, 4, 3.0, 0.5)
			for p in again:
				assert_eq(Pose.NONE, p, "waved again inside the cooldown (seed %d)" % seed_value)


func test_a_wave_stops_if_the_kid_sets_off_or_the_player_leaves() -> void:
	for seed_value in range(1, 201):
		var e := _settled(seed_value)
		var t := 0.0
		var waving := false
		while t < 2.0 and not waving:
			waving = e.update(0.02, true, true, false, false) == Pose.WAVE
			t += 0.02
		if not waving:
			continue
		# Walking off ends it at once.
		assert_eq(Pose.NONE, e.update(0.02, true, false, false, true))
		assert_eq(Pose.NONE, e.update(0.02, false, false, false, true))
		return
	fail("nobody waved")


func test_the_same_kid_waves_the_same_way_every_run_and_different_kids_differently() -> void:
	assert_eq(_meetings(Emotes.new(7), 30), _meetings(Emotes.new(7), 30))
	var a := _meetings(Emotes.new(7), 30)
	var b := _meetings(Emotes.new(8), 30)
	var c := _meetings(Emotes.new(9), 30)
	assert_true(a != b or b != c, "three kids waved in perfect step")


func test_celebrating_is_a_cheer_or_a_clap_after_a_short_delay_and_then_over() -> void:
	var e := Emotes.new(4)
	e.celebrate(0.5, false)
	var first := -1.0
	var last := -1.0
	var t := 0.0
	var pose := Pose.NONE
	while t < 6.0:
		var p := e.update(0.02, false, true, false, false)
		if p != Pose.NONE:
			if first < 0.0:
				first = t
			last = t
			pose = p
			assert_true(p == Pose.CHEER or p == Pose.CLAP)
		t += 0.02
	assert_true(first >= 0.5 - 0.03, "cheered too soon (%f)" % first)
	assert_true(first <= 0.5 + Emotes.CELEBRATE_SPREAD + 0.05, "cheered too late (%f)" % first)
	assert_near(Emotes.CELEBRATE_TIME, last - first, 0.1)
	assert_true(pose != Pose.NONE)
	assert_false(e.active)
	# A seated kid can only clap; a walking one is only passing.
	var s := Emotes.new(4)
	s.celebrate(0.0, true)
	var seen := Pose.NONE
	for i in 200:
		var p := s.update(0.02, false, false, true, false)
		if p != Pose.NONE:
			seen = p
	assert_eq(Pose.CLAP, seen)
	var w := Emotes.new(4)
	w.celebrate(0.0, false)
	for i in 200:
		assert_eq(Pose.NONE, w.update(0.02, false, false, false, true))


func test_a_waving_and_cheering_figure_stays_in_range_and_blends_in() -> void:
	for pose: int in [Pose.WAVE, Pose.CLAP]:
		var a := FigureAnim.new(3)
		for i in 120:
			a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
		var peak := 0.0
		var swing := 0.0
		var prev := a.arm_pitch[1]
		var biggest := 0.0
		for n in 240:
			a.update(DT, 0.0, 0.0, 0.0, pose)
			RigChecks.assert_sane(self, a, "%s %d" % [Pose.name_of(pose), n])
			peak = minf(peak, a.arm_pitch[1])
			biggest = maxf(biggest, absf(a.arm_pitch[1] - prev))
			prev = a.arm_pitch[1]
			if n > 60:
				swing = maxf(swing, absf(a.arm_roll[1]))
		var came_up := peak < -2.5 if pose == Pose.WAVE else peak < -1.2
		assert_true(came_up, "%s: arm never came up (%f)" % [Pose.name_of(pose), peak])
		assert_true(biggest < 0.25, "%s: arm jumped %f in a step" % [Pose.name_of(pose), biggest])
		assert_true(swing > 0.15, "%s: no movement in the hand (%f)" % [Pose.name_of(pose), swing])
	# A clap brings both hands in toward each other; a wave moves the right hand only.
	var c := FigureAnim.new(3)
	for i in 240:
		c.update(DT, 0.0, 0.0, 0.0, Pose.CLAP)
	var mirrored := true
	for i in 60:
		c.update(DT, 0.0, 0.0, 0.0, Pose.CLAP)
		if absf(c.arm_roll[0] + c.arm_roll[1]) > 0.05:
			mirrored = false
		assert_true(c.arm_roll[1] < 0.02 and c.arm_roll[0] > -0.02, "hands crossed (%f, %f)" % [c.arm_roll[0], c.arm_roll[1]])
	assert_true(mirrored, "the clap isn't symmetrical")
