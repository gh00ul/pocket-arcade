extends PaTest
## data/SettingsStoreTest.kt: the options survive a save and a reload, stay in range whatever the
## file says, and default to how the game played before.


func _gs(look: int = 100, invert: bool = false, left: bool = false, fov: int = 70, latch: bool = true, calm: bool = false, haptics: bool = true, sfx: int = 100, amb: int = 100) -> GameSettings:
	var s := GameSettings.new()
	s.look_percent = look
	s.invert_y = invert
	s.left_handed = left
	s.fov_deg = fov
	s.run_latch = latch
	s.reduce_motion = calm
	s.haptics = haptics
	s.sfx_percent = sfx
	s.ambience_percent = amb
	return s


func test_an_empty_file_gives_the_defaults_which_are_the_original_feel() -> void:
	var s := SettingsStore.read({})
	assert_true(GameSettings.new().equals(s))
	assert_eq(100, s.look_percent)
	assert_eq(70, s.fov_deg)
	assert_false(s.invert_y)
	assert_false(s.left_handed)
	assert_false(s.reduce_motion)
	assert_true(s.haptics)
	assert_true(s.run_latch)
	assert_near(1.0, s.look_scale(), 0.0)
	assert_near(1.0, s.sfx_gain(), 0.0)
	assert_near(1.0, s.ambience_gain(), 0.0)


func test_every_option_round_trips() -> void:
	var want := _gs(140, true, true, 85, false, true, false, 30, 0)
	var p := {}
	SettingsStore.write(p, want)
	assert_true(want.equals(SettingsStore.read(p)))
	# Writing again over the top replaces, it doesn't pile up.
	SettingsStore.write(p, GameSettings.new())
	assert_true(GameSettings.new().equals(SettingsStore.read(p)))


func test_out_of_range_values_are_brought_back_in_range() -> void:
	var p := {"look_percent": 9999, "fov_deg": -5, "sfx_percent": 250, "ambience_percent": 47}
	var s := SettingsStore.read(p)
	assert_eq(200, s.look_percent)
	assert_eq(60, s.fov_deg)
	assert_eq(100, s.sfx_percent)
	# Off the grid snaps to the nearest step.
	assert_eq(50, s.ambience_percent)
	assert_true(_gs(1, false, false, 500, true, false, true, -3, 999).sanitized().equals(_gs(50, false, false, 90, true, false, true, 0, 100)))
	# Writing an out-of-range value stores an in-range one.
	var q := {}
	SettingsStore.write(q, _gs(1000))
	assert_eq(200, SettingsStore.read(q).look_percent)


func test_the_graphics_haptic_and_tilt_options_default_to_how_the_game_played_before() -> void:
	var s := SettingsStore.read({})
	assert_eq(100, s.haptics_percent)
	assert_near(1.0, s.haptics_strength(), 0.0)
	assert_false(s.tilt_steering)
	assert_eq(GameSettings.QUALITY_AUTO, s.quality)
	assert_eq(GameSettings.CAP_AUTO, s.frame_cap)


func test_the_graphics_haptic_and_tilt_options_round_trip() -> void:
	var want := GameSettings.new()
	want.haptics_percent = 40
	want.tilt_steering = true
	want.quality = GameSettings.QUALITY_BATTERY
	want.frame_cap = 30
	var p := {}
	SettingsStore.write(p, want)
	assert_true(want.equals(SettingsStore.read(p)))
	var next := want.copy()
	next.quality = GameSettings.QUALITY_BEST
	next.frame_cap = 60
	SettingsStore.write(p, next)
	var back := SettingsStore.read(p)
	assert_eq(GameSettings.QUALITY_BEST, back.quality)
	assert_eq(60, back.frame_cap)
	# The other options ride along untouched.
	assert_eq(40, back.haptics_percent)
	assert_true(back.tilt_steering)


func test_the_graphics_and_haptic_options_stay_in_range_whatever_the_file_says() -> void:
	var s := SettingsStore.read({"haptics_percent": 999, "gfx_quality": 7, "gfx_frame_cap": 45})
	assert_eq(100, s.haptics_percent)
	assert_eq(GameSettings.QUALITY_BEST, s.quality)
	# A cap that isn't on offer falls back to automatic, never to some odd frame rate.
	assert_eq(GameSettings.CAP_AUTO, s.frame_cap)
	# Haptics are never silenced by the strength alone: the lowest stop is still felt.
	var z := GameSettings.new()
	z.haptics_percent = 0
	assert_eq(20, z.sanitized().haptics_percent)
	var q := GameSettings.new()
	q.quality = -3
	assert_eq(GameSettings.QUALITY_AUTO, q.sanitized().quality)
	var c := GameSettings.new()
	c.frame_cap = 60
	assert_eq(60, c.sanitized().frame_cap)


func test_the_haptic_strength_stepper_walks_its_stops_and_the_caps_cycle() -> void:
	var r := GameSettings.HAPTIC_STRENGTH
	var stops: Array = []
	var v := r.min_v
	while true:
		stops.append(v)
		var n := r.nudge(v, 1)
		if n == v:
			break
		v = n
	assert_eq([20, 40, 60, 80, 100], stops)
	assert_eq(20, r.nudge(20, -1))
	assert_eq(100, r.nudge(100, 1))
	# Every cap the button offers survives sanitising, and stepping through them comes back round.
	for cap in GameSettings.CAPS:
		var s := GameSettings.new()
		s.frame_cap = cap
		assert_eq(cap, s.sanitized().frame_cap)
	var cap := GameSettings.CAP_AUTO
	var seen: Array = []
	for i in GameSettings.CAPS.size():
		seen.append(cap)
		cap = GameSettings.CAPS[(GameSettings.CAPS.find(cap) + 1) % GameSettings.CAPS.size()]
	assert_eq(GameSettings.CAP_AUTO, cap)
	assert_eq(Array(GameSettings.CAPS), seen)


func test_the_options_share_no_keys_with_the_save() -> void:
	# A different file as well, but the names alone must not collide either.
	var p := {}
	ArcadeRepository.write_first_person(p, true)
	var calm := GameSettings.new()
	calm.reduce_motion = true
	SettingsStore.write(p, calm)
	assert_true(ArcadeRepository.read(p).first_person)
	assert_true(SettingsStore.read(p).reduce_motion)
	assert_false(ArcadeRepository.read(p).muted)
	for k in SettingsStore.KEY_TYPES:
		assert_false(ArcadeRepository.KEY_TYPES.has(k), k)


func test_steppers_move_one_step_and_stop_at_the_ends() -> void:
	var look := GameSettings.LOOK
	assert_eq(110, look.nudge(100, 1))
	assert_eq(90, look.nudge(100, -1))
	assert_eq(200, look.nudge(200, 1))
	assert_eq(50, look.nudge(50, -1))
	# From off the grid it snaps first, then steps.
	assert_eq(70, look.nudge(64, 1))
	assert_eq(60, GameSettings.FOV.nudge(65, -1))
	assert_eq(90, GameSettings.FOV.nudge(90, 1))
	assert_eq(0, GameSettings.VOLUME.nudge(0, -1))
	# Every stop of every range is reachable from the default, and all are in range.
	for r: StepRange in [GameSettings.LOOK, GameSettings.FOV, GameSettings.VOLUME]:
		var v: int = r.min_v
		var stops := 1
		while r.nudge(v, 1) != v:
			v = r.nudge(v, 1)
			stops += 1
			assert_true(v >= r.min_v and v <= r.max_v)
		assert_eq(r.max_v, v)
		assert_eq((r.max_v - r.min_v) / r.step + 1, stops)


func test_volume_gain_is_silent_at_zero_untouched_at_full_and_monotone() -> void:
	assert_near(0.0, GameSettings.gain(0), 0.0)
	assert_near(1.0, GameSettings.gain(100), 0.0)
	assert_near(0.25, GameSettings.gain(50), 1e-6)
	var last := -1.0
	for pct in range(0, 101, 10):
		var g := GameSettings.gain(pct)
		assert_true(g > last)
		last = g
	assert_near(1.0, GameSettings.gain(500), 0.0)
	assert_near(0.0, GameSettings.gain(-20), 0.0)


func test_settings_survive_the_settings_file() -> void:
	# Godot-only: the settings file is its own versioned file, ints stay ints.
	var fx := RepoFixture.new()
	var store := SettingsStore.new(PrefsStore.new(fx.dir + "/settings.json"))
	var want := _gs(140, true, true, 85, false, true, false, 30, 0)
	assert_true(store.save(want))
	var back := SettingsStore.new(PrefsStore.new(fx.dir + "/settings.json")).settings()
	assert_true(want.equals(back), str(back))
	assert_is_int(back.look_percent)
	fx.cleanup()
