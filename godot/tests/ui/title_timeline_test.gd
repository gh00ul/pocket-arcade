extends PaTest
## ui/TitleTimelineTest.kt: the title's clock: the sign lighting, the prompt, the exit, the dust and
## the welcome line.

const START := TitleTimeline.POCKET_START


func _save(loaded: bool, tickets: int = 0, total_plays: int = 0) -> SaveState:
	var s := SaveState.new()
	s.loaded = loaded
	s.tickets = tickets
	s.total_plays = total_plays
	return s


func test_smoothstep_holds_its_ends_and_rises_between() -> void:
	assert_eq(0.0, TitleTimeline.smoothstep(1.0, 3.0, 0.0))
	assert_eq(1.0, TitleTimeline.smoothstep(1.0, 3.0, 9.0))
	assert_near(0.5, TitleTimeline.smoothstep(1.0, 3.0, 2.0), 1e-6)


# ---------------------------------------------------------------- the sign

func test_a_tube_is_a_ghost_before_its_turn_and_steady_after_its_flicker() -> void:
	for i in 6:
		assert_eq(TitleTimeline.UNLIT, TitleTimeline.letter_on(0.0, START, i, false))
		var settled := START + i * TitleTimeline.LETTER_STAGGER + TitleTimeline.IGNITE_SECONDS
		assert_eq(1.0, TitleTimeline.letter_on(settled, START, i, false))
		assert_eq(1.0, TitleTimeline.letter_on(settled + 100.0, START, i, false))


func test_the_letters_light_one_after_another_left_to_right() -> void:
	# Part way through the first letter's ignition the last is still a ghost.
	var t := START + 0.3
	assert_true(TitleTimeline.letter_on(t, START, 0, false) > TitleTimeline.UNLIT)
	assert_eq(TitleTimeline.UNLIT, TitleTimeline.letter_on(START + 5 * TitleTimeline.LETTER_STAGGER - 0.001, START, 5, false))


func test_an_igniting_tube_stays_within_range_and_actually_stutters() -> void:
	var dropped := false
	var last := 0.0
	var t := START
	while t < START + TitleTimeline.IGNITE_SECONDS:
		var v := TitleTimeline.letter_on(t, START, 0, false)
		assert_true(v >= 0.0 and v <= 1.0, "%s at %s" % [v, t])
		if v < last - 1e-4:
			dropped = true
		last = v
		t += 0.004
	assert_true(dropped, "a neon tube flickers on, it doesn't just fade")


func test_with_reduced_motion_the_sign_fades_in_together_without_a_flicker() -> void:
	var last := 0.0
	var t := 0.0
	while t < START + TitleTimeline.CALM_FADE_SECONDS + 1.0:
		var a := TitleTimeline.letter_on(t, START, 0, true)
		var b := TitleTimeline.letter_on(t, START, 5, true)
		# Every letter alike (no stagger), only ever brighter.
		assert_eq(a, b)
		assert_true(a >= last - 1e-6, "%s after %s at %s" % [a, last, t])
		last = a
		t += 0.01
	assert_near(1.0, last, 1e-6)
	assert_eq(TitleTimeline.UNLIT, TitleTimeline.letter_on(0.0, START, 0, true))


func test_the_glow_breathes_within_a_gentle_range() -> void:
	var lo := 2.0
	var hi := 0.0
	var t := 0.0
	while t < TitleTimeline.BREATH_PERIOD * 2.0:
		var b := TitleTimeline.breath(t, false)
		lo = minf(lo, b)
		hi = maxf(hi, b)
		t += 0.02
	assert_true(hi <= 1.0 + 1e-6)
	assert_true(lo >= 1.0 - TitleTimeline.BREATH_DEPTH - 1e-3, "dips no lower than %s" % (1.0 - TitleTimeline.BREATH_DEPTH))
	assert_true(hi - lo > 0.08, "actually breathes")
	# Calm breathes half as deep.
	var calm_lo := 2.0
	t = 0.0
	while t < TitleTimeline.BREATH_PERIOD:
		calm_lo = minf(calm_lo, TitleTimeline.breath(t, true))
		t += 0.02
	assert_true(calm_lo > lo)


func test_the_sweep_crosses_left_to_right_then_rests_and_repeats() -> void:
	assert_eq(-1.0, TitleTimeline.sweep(0.0, false))
	assert_eq(-1.0, TitleTimeline.sweep(TitleTimeline.SWEEP_START - 0.01, false))
	var s := TitleTimeline.SWEEP_START
	assert_near(0.0, TitleTimeline.sweep(s, false), 1e-6)
	var last := -0.001
	var t := s
	while t <= s + TitleTimeline.SWEEP_SECONDS:
		var v := TitleTimeline.sweep(t, false)
		assert_true(v >= 0.0 and v <= 1.0, "%s at %s" % [v, t])
		assert_true(v >= last)
		last = v
		t += 0.01
	# Resting between sweeps, and again a period later.
	assert_eq(-1.0, TitleTimeline.sweep(s + TitleTimeline.SWEEP_SECONDS + 0.5, false))
	assert_near(TitleTimeline.sweep(s + 0.4, false), TitleTimeline.sweep(s + TitleTimeline.SWEEP_PERIOD + 0.4, false), 1e-4)


func test_reduced_motion_has_no_sweep_and_no_buzz() -> void:
	var t := 0.0
	while t < 40.0:
		assert_eq(-1.0, TitleTimeline.sweep(t, true))
		for i in 6:
			assert_eq(1.0, TitleTimeline.buzz(t, i, 6, 0, true))
		t += 0.05


func test_the_odd_tube_buzzs_briefly_and_only_one() -> void:
	var buzzes := 0
	var t := TitleTimeline.SWEEP_START + 0.01
	while t < 60.0:
		var dipped := 0
		for i in 6:
			if TitleTimeline.buzz(t, i, 6, 1, false) < 1.0:
				dipped += 1
		assert_true(dipped <= 1, "at most one tube at a time (%d at %s)" % [dipped, t])
		if dipped == 1:
			buzzes += 1
		t += 0.01
	assert_true(buzzes > 0, "it happens now and then")
	# And is brief: a few percent of the time at most.
	assert_true(buzzes < 60 * 100 * 0.05, "%d hundredths" % buzzes)


func test_the_tagline_fades_in_after_the_sign() -> void:
	assert_eq(0.0, TitleTimeline.tagline_alpha(TitleTimeline.ARCADE_START))
	assert_eq(1.0, TitleTimeline.tagline_alpha(TitleTimeline.TAGLINE_START + TitleTimeline.TAGLINE_FADE + 1.0))


# ---------------------------------------------------------------- the prompt

func test_the_prompt_waits_for_the_sign_then_pulses_within_range() -> void:
	assert_eq(0.0, TitleTimeline.prompt_alpha(0.0, false))
	assert_eq(0.0, TitleTimeline.prompt_alpha(TitleTimeline.PROMPT_START, false))
	var lo := 2.0
	var hi := 0.0
	var t := TitleTimeline.PROMPT_START + TitleTimeline.PROMPT_FADE + 0.1
	while t < 30.0:
		var a := TitleTimeline.prompt_alpha(t, false)
		assert_true(a >= 0.0 and a <= 1.0, str(a))
		lo = minf(lo, a)
		hi = maxf(hi, a)
		t += 0.01
	assert_true(hi - lo > 0.3, "pulses (%s)" % (hi - lo))
	assert_true(lo > 0.4, "never vanishes once up (%s)" % lo)


func test_the_calm_prompt_pulses_less_and_does_not_swell() -> void:
	var lo := 2.0
	var hi := 0.0
	var t := TitleTimeline.PROMPT_START + TitleTimeline.PROMPT_FADE + 0.1
	while t < 30.0:
		var a := TitleTimeline.prompt_alpha(t, true)
		lo = minf(lo, a)
		hi = maxf(hi, a)
		assert_eq(1.0, TitleTimeline.prompt_scale(t, true))
		t += 0.01
	assert_true(hi - lo < 0.3)
	var swell := 1.0
	t = TitleTimeline.PROMPT_START
	while t < 20.0:
		swell = maxf(swell, TitleTimeline.prompt_scale(t, false))
		t += 0.01
	assert_true(swell >= 1.02 and swell <= 1.05, "swells a little (%s)" % swell)


# ---------------------------------------------------------------- leaving

func test_the_sign_leaves_and_the_small_print_leaves_first() -> void:
	assert_eq(1.0, TitleTimeline.logo_exit_alpha(0.0))
	assert_eq(0.0, TitleTimeline.logo_exit_alpha(0.6))
	assert_eq(0.0, TitleTimeline.logo_exit_alpha(1.0))
	assert_eq(1.0, TitleTimeline.ui_exit_alpha(0.0))
	assert_eq(0.0, TitleTimeline.ui_exit_alpha(0.32))
	var last := 1.0
	for i in 101:
		var a := TitleTimeline.logo_exit_alpha(i / 100.0)
		assert_true(a <= last + 1e-6)
		last = a
		assert_true(TitleTimeline.ui_exit_alpha(i / 100.0) <= a + 1e-6 or i / 100.0 > 0.6)
	assert_eq(0.0, TitleTimeline.logo_exit_lift(0.0))
	assert_true(TitleTimeline.logo_exit_lift(1.0) < 0.0)
	assert_eq(1.0, TitleTimeline.logo_exit_scale(0.0))
	assert_true(TitleTimeline.logo_exit_scale(1.0) > 1.0)


# ---------------------------------------------------------------- dust

func test_every_mote_stays_on_screen_space_and_dimly_lit() -> void:
	for i in TitleTimeline.DustField.COUNT:
		var t := 0.0
		while t < 60.0:
			var x := TitleTimeline.DustField.x(i, t, 137.0, false)
			var y := TitleTimeline.DustField.y(i, t, false)
			assert_true(x >= 0.0 and x < 1.0, "x %s" % x)
			assert_true(y >= 0.0 and y < 1.0, "y %s" % y)
			var a := TitleTimeline.DustField.alpha(i, t, false)
			assert_true(a >= 0.0 and a <= 0.4, "alpha %s" % a)
			t += 1.7
		var d := TitleTimeline.DustField.depth(i)
		assert_true(d >= 0.3 and d <= 1.0)
		assert_true(TitleTimeline.DustField.radius(i) > 0.0)


func _slide(i: int) -> float:
	var a := TitleTimeline.DustField.x(i, 5.0, 0.0, false)
	var b := TitleTimeline.DustField.x(i, 5.0, 60.0, false)
	var d := absf(b - a)
	if d > 0.5:
		d = 1.0 - d
	return d


func test_a_near_mote_slides_further_than_a_far_one_when_the_camera_moves() -> void:
	# Pick the nearest and the farthest mote.
	var near := 0
	var far := 0
	for i in TitleTimeline.DustField.COUNT:
		if TitleTimeline.DustField.depth(i) > TitleTimeline.DustField.depth(near):
			near = i
		if TitleTimeline.DustField.depth(i) < TitleTimeline.DustField.depth(far):
			far = i
	assert_true(_slide(near) > _slide(far))


func test_a_calm_field_never_moves() -> void:
	for i in TitleTimeline.DustField.COUNT:
		assert_eq(TitleTimeline.DustField.x(i, 0.0, 0.0, true), TitleTimeline.DustField.x(i, 99.0, 250.0, true))
		assert_eq(TitleTimeline.DustField.y(i, 0.0, true), TitleTimeline.DustField.y(i, 99.0, true))
		assert_eq(TitleTimeline.DustField.alpha(i, 0.0, true), TitleTimeline.DustField.alpha(i, 99.0, true))


func test_a_mote_rises_continuously_and_wraps_from_top_to_bottom() -> void:
	var i := 3
	var t := 0.0
	var wraps := 0
	var prev := TitleTimeline.DustField.y(i, 0.0, false)
	while t < 300.0:
		t += 0.05
		var y := TitleTimeline.DustField.y(i, t, false)
		if y > prev:
			wraps += 1
		else:
			assert_true(prev - y < 0.01, "rose by %s" % (prev - y))
		prev = y
	assert_true(wraps > 0, "wrapped at least once")


func test_streaming_leaves_the_centre_and_the_start_alone() -> void:
	for i in TitleTimeline.DustField.COUNT:
		assert_near(0.31, TitleTimeline.DustField.stream(0.31, i, 0.0), 1e-6)
		assert_near(0.5, TitleTimeline.DustField.stream(0.5, i, 1.0), 1e-6)
		# Away from the centre it moves outward as the exit runs.
		assert_true(TitleTimeline.DustField.stream(0.8, i, 1.0) > 0.8)
		assert_true(TitleTimeline.DustField.stream(0.2, i, 1.0) < 0.2)


# ---------------------------------------------------------------- welcome

func test_the_welcome_says_nothing_until_the_save_has_loaded() -> void:
	assert_eq("", TitleTimeline.TitleCopy.welcome(_save(false, 500)))


func test_a_brand_new_player_is_welcomed_not_welcomed_back() -> void:
	assert_eq("WELCOME, PLAYER ONE!", TitleTimeline.TitleCopy.welcome(_save(true)))


func test_tickets_that_can_buy_something_are_worth_mentioning() -> void:
	var line := TitleTimeline.TitleCopy.welcome(_save(true, 120, 4))
	assert_eq("WELCOME BACK!  120 TICKETS TO SPEND", line)


func test_tickets_that_buy_nothing_new_are_not_mentioned() -> void:
	# Everything cheap is already owned, and not enough for the rest.
	var owned: Array[String] = []
	for item in Catalog.all():
		if item.price >= 1 and item.price <= 100:
			owned.append(item.id)
	owned.append(Catalog.DEFAULT_OUTFIT)
	var save := _save(true, 90, 4)
	save.owned = owned
	assert_eq("WELCOME BACK!", TitleTimeline.TitleCopy.welcome(save))


func test_plush_progress_is_the_next_thing_to_mention() -> void:
	var found := {}
	for k in 3:
		found[Catalog.plushies()[k].id] = 1
	var save := _save(true, 0, 9)
	save.collection = found
	assert_eq("WELCOME BACK!  3 OF %d PLUSHIES FOUND" % Catalog.plushies().size(), TitleTimeline.TitleCopy.welcome(save))
	var all := {}
	for p in Catalog.plushies():
		all[p.id] = 2
	save.collection = all
	assert_eq("WELCOME BACK!  EVERY PLUSH FOUND", TitleTimeline.TitleCopy.welcome(save))
