extends PaTest
## engine/audio/SpatialTest.kt: the pan law, the distance curve and the bearing maths that put a
## sound in the stereo field.

var p := Placement.new()
const OVERHEAD := PI


func test_attenuation_is_full_near_zero_far_and_never_rises() -> void:
	assert_eq(1.0, Spatial.attenuation(0.0))
	assert_eq(1.0, Spatial.attenuation(Spatial.REF_DISTANCE))
	assert_eq(0.0, Spatial.attenuation(Spatial.MAX_DISTANCE))
	assert_eq(0.0, Spatial.attenuation(Spatial.MAX_DISTANCE * 5.0))
	var last := 1.0
	var d := 0.0
	while d < Spatial.MAX_DISTANCE * 1.5:
		var a := Spatial.attenuation(d)
		assert_true(a <= last + 1e-6, "attenuation rose at %f" % d)
		assert_true(a >= 0.0 and a <= 1.0)
		last = a
		d += 1.0


func test_pan_law_keeps_power_constant() -> void:
	var pan := -1.0
	while pan <= 1.0:
		Spatial.pan_gains(pan, p)
		assert_near(1.0, p.left * p.left + p.right * p.right, 1e-5, "power at pan %f" % pan)
		pan += 0.05
	Spatial.pan_gains(0.0, p)
	assert_near(p.left, p.right, 1e-6)
	Spatial.pan_gains(-1.0, p)
	assert_near(1.0, p.left, 1e-6)
	assert_near(0.0, p.right, 1e-6)
	Spatial.pan_gains(1.0, p)
	assert_near(0.0, p.left, 1e-6)
	assert_near(1.0, p.right, 1e-6)
	# Out-of-range pans are clamped, not extrapolated.
	Spatial.pan_gains(7.0, p)
	assert_near(1.0, p.right, 1e-6)


func test_a_sound_dead_ahead_is_centred_and_unity_loud_when_close() -> void:
	# Facing +z, a source 20 units straight ahead.
	Spatial.place(100.0, 100.0, 0.0, 100.0, 120.0, p)
	assert_near(p.left, p.right, 1e-5)
	assert_near(1.0, p.left, 1e-4)
	assert_near(20.0, p.distance, 1e-4)


func test_overhead_right_is_plus_x_and_left_is_minus_x() -> void:
	# The overhead camera looks at the back wall (yaw pi): screen right is +x.
	Spatial.place(300.0, 500.0, OVERHEAD, 400.0, 500.0, p)
	assert_true(p.right > p.left * 5.0, "right ear should win: %f %f" % [p.left, p.right])
	assert_true(p.pan > 0.9)
	Spatial.place(300.0, 500.0, OVERHEAD, 200.0, 500.0, p)
	assert_true(p.left > p.right * 5.0, "left ear should win: %f %f" % [p.left, p.right])
	assert_true(p.pan < -0.9)


func test_first_person_facing_the_entrance_has_minus_x_on_the_right() -> void:
	# Yaw 0 faces +z, and the strafe-right direction is (-1, 0).
	Spatial.place(300.0, 500.0, 0.0, 200.0, 500.0, p)
	assert_true(p.right > p.left * 5.0)
	Spatial.place(300.0, 500.0, 0.0, 400.0, 500.0, p)
	assert_true(p.left > p.right * 5.0)


func test_turning_the_listener_swings_the_sound() -> void:
	# A source due east (+x): facing north (yaw pi) it is on the right; facing east (yaw pi/2) it is
	# dead ahead; facing south (yaw 0) it is on the left.
	Spatial.place(300.0, 500.0, OVERHEAD, 400.0, 500.0, p)
	var north := p.pan
	Spatial.place(300.0, 500.0, PI / 2.0, 400.0, 500.0, p)
	assert_near(0.0, p.pan, 1e-4)
	Spatial.place(300.0, 500.0, 0.0, 400.0, 500.0, p)
	assert_true(north > 0.9 and p.pan < -0.9)


func test_sounds_behind_are_quieter_than_the_same_distance_ahead() -> void:
	Spatial.place(300.0, 500.0, 0.0, 300.0, 600.0, p)
	var ahead := p.left
	var ahead_dist := p.distance
	Spatial.place(300.0, 500.0, 0.0, 300.0, 400.0, p)
	assert_near(ahead_dist, p.distance, 1e-4)
	# Dead behind, so still centred but shadowed.
	assert_near(p.left, p.right, 1e-4)
	assert_true(p.left < ahead * 0.85, "behind %f should be quieter than ahead %f" % [p.left, ahead])
	assert_true(p.left > ahead * 0.6)


func test_far_sources_are_silent_and_flagged_so() -> void:
	Spatial.place(0.0, 0.0, 0.0, 0.0, Spatial.MAX_DISTANCE + 1.0, p)
	assert_true(p.is_silent())
	assert_eq(0.0, p.send)
	Spatial.place(0.0, 0.0, 0.0, 0.0, 60.0, p)
	assert_false(p.is_silent())


func test_level_falls_monotonically_with_distance_at_any_bearing() -> void:
	for yaw: float in [0.0, 0.7, 2.0, OVERHEAD, 5.0]:
		for bearing in 12:
			var a := bearing * (TAU / 12.0)
			var last := INF
			var d := 5.0
			while d < Spatial.MAX_DISTANCE + 20.0:
				Spatial.place(0.0, 0.0, yaw, sin(a) * d, cos(a) * d, p)
				var power := p.left * p.left + p.right * p.right
				assert_true(power <= last + 1e-5, "yaw %f bearing %d rose at %f" % [yaw, bearing, d])
				last = power
				d += 5.0


func test_far_sounds_keep_more_of_their_reverb_than_of_their_dry_signal() -> void:
	Spatial.place(0.0, 0.0, 0.0, 0.0, 100.0, p)
	var near := p.send / (p.left * p.left)
	Spatial.place(0.0, 0.0, 0.0, 0.0, 300.0, p)
	var far := p.send / (p.left * p.left)
	assert_true(far > near * 3.0, "wet/dry should grow with distance")


func test_a_source_at_the_listeners_feet_does_not_flip_channels() -> void:
	# Walking past at arm's length: the pan eases through centre instead of jumping.
	var last := 0.0
	for i in range(-10, 11):
		Spatial.place(0.0, 0.0, OVERHEAD, i * 1.0, 2.0, p)
		assert_true(absf(p.pan - last) < 0.35)
		last = p.pan
	assert_true(absf(p.pan) <= 1.0)
