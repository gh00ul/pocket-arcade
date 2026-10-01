extends PaTest
## engine/audio/MixEngineTest.kt, for a mixer whose voices are Godot players: build-13's tests
## listened to the mixed samples; here the same expectations are checked on what each voice is told
## to do (its stereo gains, the panner and volume that reproduce them, its reverb send) and on the
## pool's bookkeeping. Godot's own mixing (the arithmetic build-13 did by hand) is not re-tested.

var engine: MixEngine


func before_each() -> void:
	engine = AudioFixture.engine(host)


## Advances the mixer [param seconds] in 10 ms blocks.
func run(seconds: float) -> void:
	var blocks := roundi(seconds / 0.01)
	for b in blocks:
		engine.advance(0.01)


func test_an_unpanned_sound_comes_out_equal_in_both_channels() -> void:
	engine.play(Sfx.COIN)
	var v := AudioFixture.voice_of(engine, Sfx.COIN)
	assert_not_null(v)
	if v == null:
		return
	assert_eq(v.gain_l, v.gain_r)
	assert_near(1.0, v.gain_l, 1e-6)
	assert_near(0.0, v.pan, 0.0)
	var lr := MixEngine.godot_panner(v.pan, v.volume)
	assert_near(lr.x, lr.y, 0.0)
	assert_near(1.0, lr.x, 1e-6)
	assert_true(v.dry.playing, "the dry player should be playing")


func test_a_sound_from_the_right_is_louder_in_the_right_channel() -> void:
	engine.set_listener(300.0, 500.0, PI)
	engine.play_at(Sfx.COIN, 380.0, 500.0)
	var v := AudioFixture.voice_of(engine, Sfx.COIN)
	assert_not_null(v)
	if v == null:
		return
	assert_true(v.gain_r > v.gain_l * 4.0, "right %f should beat left %f" % [v.gain_r, v.gain_l])
	# Godot's panner, set as the voice was, gives exactly those gains.
	var lr := MixEngine.godot_panner(AudioBuses.panner(v.index).pan, v.dry.volume_linear)
	assert_near(v.gain_l, lr.x, 1e-5)
	assert_near(v.gain_r, lr.y, 1e-5)


func test_a_sound_out_of_earshot_is_never_started() -> void:
	engine.set_listener(0.0, 0.0, 0.0)
	engine.play_at(Sfx.JACKPOT, 0.0, Spatial.MAX_DISTANCE + 50.0)
	engine.advance(0.01)
	assert_eq(0, engine.active_voices())


func test_muted_makes_silence_and_drops_the_voices_so_they_dont_burst_out_later() -> void:
	engine.play(Sfx.JACKPOT)
	engine.advance(0.01)
	assert_true(engine.active_voices() > 0)
	engine.muted = true
	engine.play(Sfx.JACKPOT) # refused
	assert_eq(0, engine.active_voices())
	assert_true(AudioServer.is_bus_mute(AudioBuses.index(AudioBuses.OUT)), "the output is muted")
	for v in engine.voices():
		assert_false(v.dry.playing, "a player is still going")
	engine.muted = false
	assert_false(AudioServer.is_bus_mute(AudioBuses.index(AudioBuses.OUT)))
	run(0.02)
	assert_eq(0, engine.active_voices(), "nothing left to play")


func test_volume_settings_scale_the_sound_effects() -> void:
	engine.play(Sfx.COIN)
	var full := AudioFixture.voice_of(engine, Sfx.COIN).volume
	run(0.4)
	engine.sfx_volume = 0.25
	engine.play(Sfx.COIN)
	var quiet := AudioFixture.voice_of(engine, Sfx.COIN).volume
	assert_near(full * 0.25, quiet, full * 0.03)
	run(0.4)
	engine.sfx_volume = 0.0
	engine.play(Sfx.COIN)
	assert_eq(0, engine.active_voices())


func test_the_voice_count_stays_capped_and_a_stolen_voice_is_replaced() -> void:
	var steady := AudioFixture.steady(4.0)
	for i in MixEngine.MAX_VOICES:
		engine.start_voice_of(steady, 0.02, 0.02, 0.0, 1.0, AudioPriority.NORMAL)
	engine.advance(0.01)
	assert_eq(MixEngine.MAX_VOICES, engine.active_voices())
	# One more, silent: it takes the place of the least important voice furthest through its sound
	# (all tie here, so the first), whose player restarts with it (Godot fades the old sound out).
	engine.start_voice_of(steady, 0.0, 0.0, 0.0, 1.0, AudioPriority.NORMAL)
	assert_true(engine.active_voices() <= MixEngine.MAX_VOICES + MixEngine.DYING_SLOTS)
	assert_eq(MixEngine.MAX_VOICES, engine.active_voices())
	var silent := 0
	for v in engine.voices():
		if v.gain_l == 0.0:
			silent += 1
	assert_eq(1, silent, "exactly one voice was replaced")
	assert_eq(0.0, engine.voices()[0].gain_l)


func test_a_less_important_sound_never_steals_a_more_important_one() -> void:
	var steady := AudioFixture.steady(4.0)
	for i in MixEngine.MAX_VOICES:
		engine.start_voice_of(steady, 0.01, 0.01, 0.0, 1.0, AudioPriority.KEY)
	engine.start_voice_of(steady, 0.5, 0.5, 0.0, 1.0, AudioPriority.AMBIENT)
	engine.advance(0.01)
	# Still the 28 key voices: the ambient one was refused.
	assert_eq(MixEngine.MAX_VOICES, engine.active_voices())
	for v in engine.voices():
		assert_eq(AudioPriority.KEY, v.priority)
		assert_near(0.01, v.gain_l, 1e-9)


func test_a_flood_of_requests_is_absorbed_without_error() -> void:
	for i in MixEngine.QUEUE_CAP * 3:
		engine.play(Sfx.STEP)
	engine.advance(0.01)
	var n := engine.active_voices()
	assert_true(n >= 1 and n <= MixEngine.MAX_VOICES + MixEngine.DYING_SLOTS, "%d voices" % n)
	# Everything is finished a little later.
	run(0.2)
	assert_eq(0, engine.active_voices())


func test_the_output_is_limited_below_full_scale() -> void:
	# build-13 scaled the mix by MASTER and soft-clipped it; here the output bus does the same job.
	var out := AudioBuses.index(AudioBuses.OUT)
	assert_true(out >= 0)
	var amp := AudioServer.get_bus_effect(out, 0) as AudioEffectAmplify
	var lim := AudioServer.get_bus_effect(out, 1) as AudioEffectHardLimiter
	assert_not_null(amp)
	assert_not_null(lim)
	if amp != null:
		assert_near(MixEngine.MASTER, db_to_linear(amp.volume_db), 1e-4)
	if lim != null:
		assert_true(lim.ceiling_db < 0.0)
	for sfx in Sfx.COUNT:
		engine.play(sfx)
	assert_true(engine.active_voices() <= MixEngine.MAX_VOICES)


func test_ambience_is_stereo_decorrelated_and_respects_its_volume() -> void:
	var loop := HallAmbience.render_babble_loop(8000)
	var frames := loop.size() / 2
	assert_eq(HallAmbience.babble_loop_frames(8000), frames)
	var sl := 0.0
	var diff := 0.0
	for i in frames:
		sl += loop[i * 2] * loop[i * 2]
		diff += absf(loop[i * 2] - loop[i * 2 + 1])
	var rms_l := sqrt(sl / frames)
	assert_true(rms_l > 0.005, "murmur should be audible: %f" % rms_l)
	assert_true(rms_l < 1.0)
	# Two ears, two murmurs: not identical.
	assert_true(diff / frames > rms_l * 0.05, "channels are identical")
	# The level eases up to the target, and the volume setting silences it.
	engine.ambient_target = 1.0
	run(3.0)
	assert_true(engine.ambience.hum_gain > 0.0)
	assert_true(engine.ambience.murmur_gain > 0.0)
	engine.ambience_volume = 0.0
	run(0.02)
	assert_eq(0.0, engine.ambience.hum_gain)
	assert_eq(0.0, engine.ambience.murmur_gain)


func test_the_murmur_loop_has_no_seam() -> void:
	# The step from the loop's last frame back to its first is no bigger than the murmur's own.
	var loop := HallAmbience.render_babble_loop(8000)
	var frames := loop.size() / 2
	var worst := 0.0
	for i in range(1, frames):
		worst = maxf(worst, absf(loop[i * 2] - loop[(i - 1) * 2]))
	var seam := absf(loop[0] - loop[(frames - 1) * 2])
	assert_true(seam <= worst, "seam %f vs largest step %f" % [seam, worst])


func test_reverb_sends_follow_the_sound_and_the_room() -> void:
	engine.room = ReverbRoom.HALL
	engine.play(Sfx.COIN)
	var v := AudioFixture.voice_of(engine, Sfx.COIN)
	assert_near(SfxMix.send_for(Sfx.COIN), v.send, 1e-6)
	assert_true(v.wet.playing, "the send player should be playing")
	assert_eq(AudioBuses.SEND, v.wet.bus)
	# The room glides to the hall's reverb.
	run(3.0)
	var r := AudioBuses.reverb()
	assert_near(ReverbRoom.room_size_for(2.3), r.room_size, 0.01)
	assert_near(ReverbRoom.wet_param(0.9, 2.3), r.wet, 0.01)


func test_a_played_sounds_reverb_send_scales_with_its_volume() -> void:
	engine.play(Sfx.COIN)
	var loud := AudioFixture.voice_of(engine, Sfx.COIN).send
	run(0.5)
	engine.play(Sfx.COIN, 0.1)
	var quiet := AudioFixture.voice_of(engine, Sfx.COIN).send
	assert_true(quiet < loud * 0.2, "quiet send %f vs loud %f" % [quiet, loud])


func test_godot_panner_reproduces_any_stereo_gains() -> void:
	var r := KRandom.new(3)
	for i in 200:
		var gl := r.next_float() * 2.0
		var gr := r.next_float() * 2.0
		var pv := MixEngine.pan_for(gl, gr)
		var lr := MixEngine.godot_panner(pv.x, pv.y)
		# Vector2 holds 32-bit floats, as Godot's panner computes.
		assert_near(gl, lr.x, 1e-6)
		assert_near(gr, lr.y, 1e-6)
		assert_true(pv.x >= -1.0 and pv.x <= 1.0)


func test_big_sounds_duck_the_music_as_deep_as_they_are_loud() -> void:
	engine.play(Sfx.JACKPOT)
	assert_near(0.55, engine.music.duck_depth, 1e-6)
	assert_near(1.3, engine.music.duck_hold, 1e-6)
	var e2 := AudioFixture.engine(host)
	e2.set_listener(0.0, 0.0, 0.0)
	e2.play_at(Sfx.WIN, 0.0, 200.0)
	assert_true(e2.music.duck_depth > 0.0 and e2.music.duck_depth < 0.35)
	e2.play(Sfx.COIN)
	assert_true(e2.music.duck_depth < 0.35, "a coin does not duck")


func test_pitch_is_clamped_and_shortens_the_voice() -> void:
	engine.play(Sfx.COIN, 1.0, 9.0)
	var v := AudioFixture.voice_of(engine, Sfx.COIN)
	assert_eq(MixEngine.MAX_PITCH, v.rate)
	assert_eq(MixEngine.MAX_PITCH, v.dry.pitch_scale)
	# A 0.32 s coin at 4x is over in 80 ms.
	run(0.1)
	assert_eq(0, engine.active_voices())
