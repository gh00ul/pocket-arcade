extends PaTest
## engine/audio/RoundMusicTest.kt: the round's music cues and the music-volume setting.


func test_a_round_heats_up_and_never_cools_as_the_clock_runs_down() -> void:
	var round_s := 60.0
	var last := -1.0
	var t := round_s
	while t >= 0.0:
		var v := RoundMusic.intensity(t, round_s)
		assert_true(v >= 0.0 and v <= 1.0, "intensity %f out of range at %f" % [v, t])
		assert_true(v >= last - 1e-6, "fell at %f: %f < %f" % [t, v, last])
		last = v
		t -= 0.25
	assert_near(RoundMusic.START_INTENSITY, RoundMusic.intensity(round_s, round_s), 1e-4)
	assert_near(1.0, RoundMusic.intensity(0.0, round_s), 1e-4)
	# The last ten seconds carry it well above where the middle of the round sits.
	var before := RoundMusic.intensity(RoundMusic.CRUNCH_SECONDS + 0.1, round_s)
	assert_true(before <= RoundMusic.MID_INTENSITY + 0.05, "the middle of the round sits at %f" % before)
	assert_true(RoundMusic.intensity(5.0, round_s) > 0.75)


func test_short_and_degenerate_rounds_stay_in_range() -> void:
	for round_s: float in [0.0, 5.0, 10.0, 45.0, 75.0]:
		for left: float in [-1.0, 0.0, 3.0, round_s, round_s * 2.0]:
			var v := RoundMusic.intensity(left, round_s)
			assert_true(v >= 0.0 and v <= 1.0 and is_finite(v), "%f of %f gave %f" % [left, round_s, v])


func test_the_cues_drive_the_audio_system() -> void:
	var audio := AudioSynth.new()
	host.add_child(audio)
	RoundMusic.intro(audio, "racer")
	assert_eq(MusicScene.game("racer"), audio.music.scene)
	assert_true(audio.music.quiet)
	assert_near(RoundMusic.INTRO_INTENSITY, audio.music.intensity, 1e-6)
	assert_eq(ReverbRoom.GAME, audio.room)
	RoundMusic.countdown(audio, "racer")
	assert_false(audio.music.quiet)
	assert_near(RoundMusic.COUNTDOWN_INTENSITY, audio.music.intensity, 1e-6)
	RoundMusic.play(audio)
	assert_false(audio.music.quiet)
	RoundMusic.pause(audio)
	assert_true(audio.music.quiet)
	RoundMusic.results(audio)
	assert_eq(MusicScene.RESULTS, audio.music.scene)
	assert_false(audio.music.quiet)
	assert_near(RoundMusic.RESULTS_INTENSITY, audio.music.intensity, 1e-6)
	audio.enter_scene(MusicScene.HALL)
	assert_eq(ReverbRoom.HALL, audio.room)
	audio.enter_scene(MusicScene.TITLE)
	assert_eq(ReverbRoom.TITLE, audio.room)
	audio.music_volume = 0.5
	assert_eq(0.5, audio.music_volume)
	assert_eq(0.5, audio.music.volume)


func test_the_music_volume_is_a_saved_stepper_setting() -> void:
	var d := GameSettings.new()
	assert_eq(100, d.music_percent)
	assert_eq(1.0, d.music_gain())
	# A file from before the setting existed has no key: the default.
	assert_eq(100, SettingsStore.read({}).music_percent)
	# Stored and read back.
	var p := {}
	var s40 := GameSettings.new()
	s40.music_percent = 40
	SettingsStore.write(p, s40)
	assert_eq(40, SettingsStore.read(p).music_percent)
	# Sanitised into the stepper's range and steps, however it arrives.
	for pair: Array in [[999, 100], [-5, 0], [33, 30]]:
		var g := GameSettings.new()
		g.music_percent = pair[0]
		assert_eq(pair[1], g.sanitized().music_percent, "music %d" % pair[0])
	# Squared like the other volumes: half way is a quarter of the amplitude.
	var half := GameSettings.new()
	half.music_percent = 50
	assert_near(0.25, half.music_gain(), 1e-6)
	var off := GameSettings.new()
	off.music_percent = 0
	assert_eq(0.0, off.music_gain())
	# The other settings are untouched by it.
	var both := GameSettings.new()
	both.sfx_percent = 30
	both.ambience_percent = 60
	both.music_percent = 80
	var q := {}
	SettingsStore.write(q, both)
	assert_true(both.equals(SettingsStore.read(q)))


func test_the_music_forwards_to_its_backend_and_resends_its_state() -> void:
	# Godot: the soundtrack plays in the Android plugin; the control keeps the state for it.
	var m := MusicControl.new()
	m.set_scene(MusicScene.HALL)
	m.set_intensity(0.7)
	m.set_quiet(true)
	m.volume = 0.3
	var rec := RecordingBackend.new()
	m.attach(rec)
	assert_eq(["volume 0.3", "muted false", "quiet true", "intensity 0.7", "scene hall"], rec.calls)
	rec.calls.clear()
	m.stinger(Stinger.GO)
	m.duck(0.5, 1.0)
	m.set_intensity(3.0)
	m.set_active(false)
	assert_eq(["stinger 1", "duck 0.5 1.0", "intensity 1.0", "active false"], rec.calls)
	assert_eq(1, m.stingers.size())
	assert_eq(Stinger.GO, m.stingers[0])


class RecordingBackend:
	extends MusicControl.Backend
	var calls: Array = []

	func set_scene(scene: String) -> void:
		calls.append("scene " + scene)

	func set_intensity(level: float) -> void:
		calls.append("intensity " + str(level))

	func set_quiet(on: bool) -> void:
		calls.append("quiet " + str(on))

	func stinger(which: int) -> void:
		calls.append("stinger " + str(which))

	func duck(depth: float, hold: float) -> void:
		calls.append("duck %s %s" % [str(depth), str(hold)])

	func set_volume(v: float) -> void:
		calls.append("volume " + str(v))

	func set_muted(v: bool) -> void:
		calls.append("muted " + str(v))

	func set_active(v: bool) -> void:
		calls.append("active " + str(v))
