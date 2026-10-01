extends PaTest
## engine/AudioSynthTest.kt: every sound effect must actually be synthesized: audible, finite and
## not clipping. Plus (Godot) the buffers are build-13's exact lengths, and the sounds survive the
## trip through the 16-bit streams and the cache.


func test_every_sfx_has_a_sound() -> void:
	var bank := AudioFixture.bank()
	for sfx in Sfx.COUNT:
		var name: String = Sfx.NAMES[sfx]
		var s := bank.samples(sfx)
		assert_false(s.is_empty(), name + " has no sound")
		assert_true(s.size() > 100, name + " is empty")
		var peak := 0.0
		var finite := true
		for v in s:
			if is_nan(v) or is_inf(v):
				finite = false
			peak = maxf(peak, absf(v))
		assert_true(finite, name + " has a bad sample")
		assert_true(peak > 0.02, "%s is silent (peak %f)" % [name, peak])
		assert_true(peak <= 0.951, "%s clips (peak %f)" % [name, peak])


func test_buffers_are_build_13s_lengths() -> void:
	# FloatArray((seconds * sampleRate).toInt() + 2) in Kotlin's Float arithmetic: 1.3f * 48000 rounds
	# to 62399.996 in 32 bits, so the jackpot's buffer is 62401 long.
	var bank := AudioFixture.bank()
	assert_eq(2882, bank.samples(Sfx.BLIP).size())
	assert_eq(15362, bank.samples(Sfx.COIN).size())
	assert_eq(62401, bank.samples(Sfx.JACKPOT).size())
	assert_eq(1442, bank.samples(Sfx.STEP).size())
	assert_eq(115202, bank.samples(Sfx.STEAM).size())


func test_each_sound_starts_on_its_first_note() -> void:
	# Every sound but the spill (whose clinks fall at random) has a note at time 0, so its first few
	# samples move off zero at once.
	var bank := AudioFixture.bank()
	for sfx in Sfx.COUNT:
		if sfx == Sfx.SPILL:
			continue
		var s := bank.samples(sfx)
		var moved := false
		for i in mini(200, s.size()):
			if absf(s[i]) > 1e-4:
				moved = true
				break
		assert_true(moved, Sfx.NAMES[sfx] + " starts silent")


func test_streams_hold_the_samples_as_16_bit() -> void:
	var bank := AudioFixture.bank()
	var s := bank.samples(Sfx.COIN)
	var w := bank.stream_of(s)
	assert_eq(AudioStreamWAV.FORMAT_16_BITS, w.format)
	assert_eq(48000, w.mix_rate)
	assert_false(w.stereo)
	assert_eq(s.size() * 2, w.data.size())
	for i in [0, 100, 5000, s.size() - 1]:
		assert_near(s[i], w.data.decode_s16(i * 2) / 32767.0, 2.0 / 32767.0, "sample %d" % i)


func test_the_cache_round_trips() -> void:
	var dir := "user://test_tmp/audio_cache"
	DirAccess.make_dir_recursive_absolute(dir)
	# A low rate keeps the test quick; the cache is per rate.
	var built := AudioCache.build(8000)
	assert_eq(Sfx.COUNT, built.sfx.size())
	assert_true(AudioCache.save(built, dir))
	var back := AudioCache.read(8000, dir)
	assert_not_null(back)
	if back != null:
		assert_eq(Sfx.COUNT, back.sfx.size())
		for i in Sfx.COUNT:
			assert_eq((built.sfx[i] as AudioStreamWAV).data, (back.sfx[i] as AudioStreamWAV).data, Sfx.NAMES[i])
		assert_eq(AudioStreamWAV.LOOP_FORWARD, back.hum.loop_mode)
		assert_true(back.murmur.stereo)
		assert_eq(HallAmbience.babble_loop_frames(8000), back.murmur.loop_end)
	# Another rate, or another version, is not this cache.
	assert_null(AudioCache.read(16000, dir))
	DirAccess.remove_absolute(AudioCache.path_for(8000, dir))


func test_an_unreadable_cache_is_ignored() -> void:
	var dir := "user://test_tmp/audio_cache_bad"
	DirAccess.make_dir_recursive_absolute(dir)
	var f := FileAccess.open(AudioCache.path_for(8000, dir), FileAccess.WRITE)
	f.store_string("not a cache at all, just some text")
	f.close()
	assert_null(AudioCache.read(8000, dir))
	DirAccess.remove_absolute(AudioCache.path_for(8000, dir))
