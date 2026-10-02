extends PaTest
## hub/HighlightTest.kt: the "you are standing at this one" highlight: its easing, its strength and
## which prop each spot lights. The floor is build-13's, with every decoration bought.

var games: Array[MiniGame]
var map: HubMap


func before_each() -> void:
	games = HallGames.games()
	map = HallGames.maps()[1][1]


func test_easing_is_smooth_and_stays_in_range() -> void:
	assert_near(0.0, Highlight.ease(0.0), 0.0)
	assert_near(1.0, Highlight.ease(1.0), 0.0)
	assert_near(0.5, Highlight.ease(0.5), 1e-6)
	assert_near(0.0, Highlight.ease(-3.0), 0.0)
	assert_near(1.0, Highlight.ease(7.0), 0.0)
	var last := 0.0
	for i in 101:
		var e := Highlight.ease(i / 100.0)
		assert_true(e >= last, "eased level must not fall")
		last = e
	# Gentle at both ends: the first hundredth moves it far less than a straight line would.
	assert_true(Highlight.ease(0.05) < 0.05)
	assert_true(Highlight.ease(0.95) > 0.95)


func test_fades_in_and_out_over_the_fade_time() -> void:
	var level := 0.0
	var t := 0.0
	var dt := 1.0 / 60.0
	while level < 1.0 and t < 2.0:
		level = Highlight.step(level, true, dt)
		t += dt
	assert_near(1.0, level, 0.0)
	assert_near(Highlight.FADE_SECONDS, t, 2.0 * dt)
	while level > 0.0 and t < 4.0:
		level = Highlight.step(level, false, dt)
		t += dt
	assert_near(0.0, level, 0.0)
	assert_near(2.0 * Highlight.FADE_SECONDS, t, 3.0 * dt)
	# A hitch doesn't overshoot, and no time passing changes nothing.
	assert_near(1.0, Highlight.step(0.9, true, 5.0), 0.0)
	assert_near(0.0, Highlight.step(0.1, false, 5.0), 0.0)
	assert_near(0.4, Highlight.step(0.4, true, 0.0), 0.0)


func test_a_cabinet_that_is_not_highlighted_draws_exactly_as_before() -> void:
	for i in 51:
		assert_eq(1.0, Highlight.boost(0.0, i * 0.3))


func test_the_boost_pulses_but_stays_small_enough_not_to_flare_the_bloom() -> void:
	var lo := 1.0 + Highlight.EMISSIVE_BOOST * (1.0 - Highlight.PULSE_DEPTH)
	var hi := 1.0 + Highlight.EMISSIVE_BOOST
	var seen_low := 99.0
	var seen_high := 0.0
	for i in 601:
		var b := Highlight.boost(1.0, i * 0.01)
		assert_true(b >= lo - 1e-4, "boost %s under the pulse's floor %s" % [b, lo])
		assert_true(b <= hi + 1e-4, "boost %s over the pulse's top %s" % [b, hi])
		seen_low = minf(seen_low, b)
		seen_high = maxf(seen_high, b)
	assert_true(seen_high - seen_low > 0.5 * Highlight.EMISSIVE_BOOST * Highlight.PULSE_DEPTH, "the pulse should actually swing")
	# The marquee glows at 1.25 and the brightest neon at 1.8: a quarter more is the most that stays a
	# highlight rather than a flare (the hall's café tiles crossed the threshold twice).
	assert_true(hi <= 1.25, "boost cap")
	assert_true(Highlight.POOL_ALPHA <= 0.1)
	# Idle marquee bulbs are 0.7 dimmed to under half of white trim: 1.0 is where they'd start to glow.
	assert_true(0.7 + Highlight.BULB_BOOST <= 1.0)
	# Fading in raises it steadily.
	var prev := 1.0
	for i in 21:
		var b := Highlight.boost(i / 20.0, 0.0)
		assert_true(b >= prev - 1e-6)
		prev = b


func test_every_machine_spot_lights_exactly_its_own_cabinet() -> void:
	var machines: Array[Prop] = []
	for p: Prop in map.props:
		if p.kind == PropKind.MACHINE:
			machines.append(p)
	assert_false(machines.is_empty())
	for spot: Spot in map.spots:
		if spot.type != SpotType.MACHINE:
			continue
		var lit: Array[Prop] = []
		for p: Prop in map.props:
			if Highlight.is_spot_of(spot, p):
				lit.append(p)
		assert_eq(1, lit.size(), "spot for %s at %s" % [games[spot.machine].id, spot.area.center_x])
		if lit.size() == 1:
			assert_eq(PropKind.MACHINE, lit[0].kind)
			assert_eq(spot.machine, lit[0].machine)
	# And every cabinet has a spot that lights it (the copies in a bank each have their own).
	for p in machines:
		var n := 0
		for spot: Spot in map.spots:
			if Highlight.is_spot_of(spot, p):
				n += 1
		assert_eq(1, n, "%s at %s" % [games[p.machine].id, p.x0])


func test_the_kiosk_and_the_prize_counter_light_up_too() -> void:
	var tokens: Spot = null
	var prizes: Spot = null
	for s: Spot in map.spots:
		if s.type == SpotType.TOKENS:
			tokens = s
		elif s.type == SpotType.PRIZES:
			prizes = s
	var by_tokens := {}
	var by_prizes := {}
	for p: Prop in map.props:
		if Highlight.is_spot_of(tokens, p):
			by_tokens[p.kind] = true
		if Highlight.is_spot_of(prizes, p):
			by_prizes[p.kind] = true
	assert_true(by_tokens.has(PropKind.TOKENS))
	assert_eq([PropKind.COUNTER], by_prizes.keys())
	assert_false(by_tokens.has(PropKind.MACHINE))
	for p: Prop in map.props:
		assert_false(p.kind == PropKind.VENDING and Highlight.is_spot_of(tokens, p))


func test_nobody_at_anything_lights_nothing() -> void:
	for p: Prop in map.props:
		assert_false(Highlight.is_spot_of(null, p))
