extends PaTest
## engine/audio/ReverbTest.kt, for Godot's reverb. build-13 tested its own reverb's decay, width,
## stability and return level; the same checks run here on an offline copy of Godot 4.6.2's reverb
## ([GodotReverbSim]) set up for each room exactly as the mixer sets AudioEffectReverb, which tests
## the room mapping against the algorithm that will play. 24 kHz keeps it quick (the mapping is
## computed per rate). build-13's "the tail stops costing" check has no counterpart: Godot decides
## when a bus does work.

const RATE := 24000
const WINDOW := RATE / 4

static var _irs := {}


## The left channel's impulse response in [param room], 1.6 reverb times plus a little long.
func ir(room: int) -> PackedFloat32Array:
	if not _irs.has(room):
		var n := int((ReverbRoom.RT60[room] * 1.6 + 0.3) * RATE)
		var src := PackedFloat32Array()
		src.resize(n)
		src[0] = 1.0
		_irs[room] = GodotReverbSim.for_room(room, RATE).process(src)
	return _irs[room]


func rms(a: PackedFloat32Array, from: int, to: int) -> float:
	var s := 0.0
	for i in range(from, to):
		s += a[i] * a[i]
	return sqrt(s / maxi(to - from, 1))


func test_impulse_response_decays_and_stays_bounded_in_every_room() -> void:
	for room in ReverbRoom.COUNT:
		var name: String = ReverbRoom.NAMES[room]
		var ch := ir(room)
		var peak := 0.0
		var finite := true
		for v in ch:
			if is_nan(v) or is_inf(v):
				finite = false
			peak = maxf(peak, absf(v))
		assert_true(finite, name + " has a bad sample")
		assert_true(peak < 1.5 and peak > 0.001, "%s impulse response peaks at %f" % [name, peak])
		var start := rms(ch, 0, WINDOW)
		var at_rt60 := int(ReverbRoom.RT60[room] * RATE)
		var later := rms(ch, at_rt60, at_rt60 + WINDOW)
		assert_true(later < start * 0.05, "%s: %f at its rt60 should be far below %f" % [name, later, start])
		assert_true(rms(ch, ch.size() - WINDOW, ch.size()) < start * 1e-3, name + ": dead by the end")


func test_the_tail_is_wide_and_the_ears_differ() -> void:
	for room in ReverbRoom.COUNT:
		var src := PackedFloat32Array()
		src.resize(RATE / 2)
		src[0] = 1.0
		var l := GodotReverbSim.for_room(room, RATE).process(src)
		var r := GodotReverbSim.for_room(room, RATE, true).process(src)
		var diff := 0.0
		var sum := 0.0
		for i in l.size():
			diff += absf(l[i] - r[i])
			sum += absf(l[i]) + absf(r[i])
		assert_true(diff / sum > 0.4, "%s ears identical (%f)" % [ReverbRoom.NAMES[room], diff / sum])


## Reverb time from the energy decay curve (Schroeder's backward integration), T20 scaled to 60 dB.
func measured_rt60(ch: PackedFloat32Array) -> float:
	var edc := PackedFloat64Array()
	edc.resize(ch.size())
	var acc := 0.0
	for i in range(ch.size() - 1, -1, -1):
		acc += ch[i] * ch[i]
		edc[i] = acc
	var total := edc[0]
	var t5 := -1.0
	var t25 := -1.0
	for i in edc.size():
		var db := 10.0 * log(edc[i] / total) / log(10.0)
		if t5 < 0.0 and db <= -5.0:
			t5 = float(i) / RATE
		if db <= -25.0:
			t25 = float(i) / RATE
			break
	if t25 < 0.0:
		t25 = float(ch.size()) / RATE
	return (t25 - t5) * 3.0


func test_the_reverb_time_is_about_what_the_room_says_and_bigger_rooms_ring_longer() -> void:
	var measured := {}
	for room in ReverbRoom.COUNT:
		var rt := measured_rt60(ir(room))
		measured[room] = rt
		var nominal: float = ReverbRoom.RT60[room]
		assert_true(rt > nominal * 0.35 and rt < nominal * 1.3, "%s measured %f vs nominal %f" % [ReverbRoom.NAMES[room], rt, nominal])
	assert_true(measured[ReverbRoom.GAME] < measured[ReverbRoom.HALL])
	assert_true(measured[ReverbRoom.HALL] < measured[ReverbRoom.TITLE])


func test_every_room_stays_finite_and_bounded_under_loud_noise_and_square_waves() -> void:
	var rng := KRandom.new(5)
	for room in ReverbRoom.COUNT:
		var noise := PackedFloat32Array()
		var square := PackedFloat32Array()
		var dc := PackedFloat32Array()
		for i in RATE:
			noise.append(rng.next_float() * 2.0 - 1.0)
			square.append(1.0 if (i * 100 / RATE) % 2 == 0 else -1.0)
			dc.append(1.0)
		for sig: PackedFloat32Array in [noise, square, dc]:
			var out := GodotReverbSim.for_room(room, RATE).process(sig)
			var peak := 0.0
			var finite := true
			for v in out:
				if is_nan(v) or is_inf(v):
					finite = false
				peak = maxf(peak, absf(v))
			assert_true(finite, ReverbRoom.NAMES[room] + " is not finite")
			assert_true(peak < 40.0, "%s peaks at %f" % [ReverbRoom.NAMES[room], peak])


func test_a_white_noise_send_comes_back_at_about_the_rooms_return_level() -> void:
	var rng := KRandom.new(9)
	for room in ReverbRoom.COUNT:
		var input := PackedFloat32Array()
		for i in RATE * 2:
			input.append(rng.next_float() - 0.5)
		var in_rms := rms(input, RATE, input.size())
		var out := GodotReverbSim.for_room(room, RATE).process(input)
		var wet: float = ReverbRoom.WET[room]
		var ratio := rms(out, RATE, out.size()) / in_rms / wet
		assert_true(ratio >= 0.4 and ratio <= 2.2, "%s returns %fx its wet level" % [ReverbRoom.NAMES[room], ratio])
		assert_true(wet >= 0.0 and wet <= 1.5, "wet in bounds")


func test_changing_the_room_glides_there() -> void:
	var engine := AudioFixture.engine(host)
	assert_eq(ReverbRoom.GAME, engine.room)
	engine.room = ReverbRoom.HALL
	var rt1 := 0.0
	var size_before := AudioBuses.reverb().room_size
	var worst_step := 0.0
	var worst_wet := 0.0
	var last_size := size_before
	var last_wet := AudioBuses.reverb().wet
	for b in 300:
		engine.advance(0.01)
		var size := AudioBuses.reverb().room_size
		var wet := AudioBuses.reverb().wet
		worst_step = maxf(worst_step, absf(size - last_size))
		worst_wet = maxf(worst_wet, absf(wet - last_wet) / last_wet)
		last_size = size
		last_wet = wet
		if b == 99:
			rt1 = engine.current_rt60
	var game: float = ReverbRoom.RT60[ReverbRoom.GAME]
	var hall: float = ReverbRoom.RT60[ReverbRoom.HALL]
	assert_true(rt1 > game + (hall - game) * 0.8, "rt60 on its way after 1 s: %f" % rt1)
	assert_near(hall, engine.current_rt60, 0.02, "settled after 3 s")
	assert_near(ReverbRoom.WET[ReverbRoom.HALL], engine.current_wet, 0.01)
	# Godot's reverb follows in small steps. A change of feedback alters how fast the tail decays,
	# not its level, so it cannot click; the return level, which could, glides as build-13's did (2.8% of
	# the way a block), well under a decibel a step even at the start of a change from the dry GAME room.
	assert_true(worst_step < 0.1, "room size jumped by %f in one block" % worst_step)
	assert_true(worst_wet < 0.1, "return level jumped by %f in one block" % worst_wet)
	assert_near(ReverbRoom.room_size_for(engine.current_rt60), AudioBuses.reverb().room_size, 1e-6)


func test_muting_empties_the_tail() -> void:
	var engine := AudioFixture.engine(host)
	engine.room = ReverbRoom.TITLE
	for b in 100:
		engine.advance(0.01)
	var before := AudioBuses.reverb()
	var size := before.room_size
	engine.muted = true
	var after := AudioBuses.reverb()
	assert_true(before != after, "a fresh reverb (an empty tail) replaces the old one")
	assert_near(size, after.room_size, 1e-6)
	assert_eq(0.0, after.dry)
	engine.muted = false


func test_room_settings_are_inside_godots_ranges() -> void:
	for room in ReverbRoom.COUNT:
		var rt: float = ReverbRoom.RT60[room]
		var size := ReverbRoom.room_size_for(rt)
		var damp := ReverbRoom.damping_param(ReverbRoom.DAMPING[room])
		var wet := ReverbRoom.wet_param(ReverbRoom.WET[room], rt)
		for v: float in [size, damp, wet]:
			assert_true(v >= 0.0 and v <= 1.0, "%s setting %f out of range" % [ReverbRoom.NAMES[room], v])
	# Bigger rooms get bigger Godot rooms.
	assert_true(ReverbRoom.room_size_for(3.2) > ReverbRoom.room_size_for(2.3))
	assert_true(ReverbRoom.room_size_for(2.3) > ReverbRoom.room_size_for(2.0))
