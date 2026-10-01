extends PaTest
## engine/HapticsTest.kt: scaling, clamping and never throwing; plus (Godot) the patterns each call
## sends to the phone, the fallbacks and the rate limits, against a recording vibrator and a clock
## the test moves.


## A vibrator that records what it is asked to play.
class Recorder:
	extends Haptics.Device
	var calls: Array = []

	func one_shot(ms: int, amplitude: int) -> void:
		calls.append(["one_shot", ms, amplitude])

	func waveform(timings: PackedInt32Array, amplitudes: PackedInt32Array) -> void:
		calls.append(["waveform", Array(timings), Array(amplitudes)])

	func composition(prims: PackedInt32Array, scales: PackedFloat32Array, delays: PackedInt32Array) -> void:
		# Scales in thousandths (they cross as 32-bit floats).
		var s: Array = []
		for v in scales:
			s.append(roundi(v * 1000.0))
		calls.append(["composition", Array(prims), s, Array(delays)])


var now := 100000


func haptics(amplitude: bool, primitives: bool, thud: bool = true, low_tick: bool = true) -> Array:
	var r := Recorder.new()
	r.amplitude_control = amplitude
	r.primitives = primitives
	r.thud = thud
	r.low_tick = low_tick
	var h := Haptics.new(r)
	h.clock = func() -> int: return now
	return [h, r]


func test_strength_scales_amplitudes_and_stays_in_range() -> void:
	assert_eq(255, Haptics.scale_amplitude(255, 1.0))
	assert_eq(128, Haptics.scale_amplitude(255, 0.5))
	assert_eq(35, Haptics.scale_amplitude(70, 0.5))
	# Never zero (zero means off in a waveform) and never past the top.
	assert_eq(1, Haptics.scale_amplitude(70, 0.0))
	assert_eq(255, Haptics.scale_amplitude(255, 3.0))


func test_a_weaker_setting_shortens_a_buzz_on_a_vibrator_without_amplitude_control() -> void:
	assert_eq(55, Haptics.scale_duration(55, 1.0))
	var half := Haptics.scale_duration(55, 0.5)
	assert_true(half >= 25 and half <= 40)
	assert_eq(4, Haptics.scale_duration(5, 0.0))


func test_a_rumble_is_always_a_low_buzz() -> void:
	assert_true(Haptics.rumble_amplitude(0.0) >= 10 and Haptics.rumble_amplitude(0.0) <= 40)
	assert_true(Haptics.rumble_amplitude(1.0) <= 100)
	assert_true(Haptics.rumble_amplitude(0.3) < Haptics.rumble_amplitude(0.9))
	# Nonsense levels clamp instead of overshooting.
	assert_eq(Haptics.rumble_amplitude(1.0), Haptics.rumble_amplitude(7.0))
	assert_eq(Haptics.rumble_amplitude(0.0), Haptics.rumble_amplitude(-3.0))


func test_strength_clamps_to_zero_to_one() -> void:
	var h := Haptics.new(null)
	assert_eq(1.0, h.strength)
	h.strength = 0.4
	assert_near(0.4, h.strength, 0.0)
	h.strength = 2.0
	assert_eq(1.0, h.strength)
	h.strength = -1.0
	assert_eq(0.0, h.strength)
	h.strength = NAN
	assert_eq(1.0, h.strength)


func test_without_a_vibrator_every_call_is_a_harmless_no_op() -> void:
	var h := Haptics.new(null)
	for i in 3:
		h.tick()
		h.hit()
		h.heavy()
		h.win()
		h.jackpot()
		h.soft()
		h.bump()
		h.rumble(0.5)
		h.rumble(NAN)
		h.rumble(-1.0)
	h.enabled = false
	h.strength = 0.0
	h.tick()
	h.soft()
	h.bump()
	h.rumble(1.0)
	h.win()
	h.jackpot()


func test_a_haptic_engine_plays_build_13s_primitives() -> void:
	var hr := haptics(true, true)
	var h: Haptics = hr[0]
	var r: Recorder = hr[1]
	h.tick()
	now += 100
	h.hit()
	now += 100
	h.heavy()
	now += 100
	h.soft()
	now += 100
	h.bump()
	assert_eq([
		["composition", [Haptics.TICK], [600], [0]],
		["composition", [Haptics.CLICK], [700], [0]],
		["composition", [Haptics.THUD], [1000], [0]],
		["composition", [Haptics.LOW_TICK], [500], [0]],
		["composition", [Haptics.THUD], [550], [0]],
	], r.calls)


func test_win_and_jackpot_patterns() -> void:
	var hr := haptics(true, true)
	var h: Haptics = hr[0]
	var r: Recorder = hr[1]
	h.strength = 0.5
	h.win()
	now += 1000
	h.jackpot()
	assert_eq(["composition", [1, 1, 2], [350, 425, 500], [0, 70, 70]], r.calls[0])
	assert_eq(["composition", [7, 1, 1, 1, 4, 2], [300, 350, 400, 450, 500, 500], [0, 45, 45, 45, 60, 30]], r.calls[1])


func test_fallbacks_without_primitives() -> void:
	# Amplitude control: one-shots at scaled amplitudes, waveforms with scaled amplitudes (zeros stay off).
	var hr := haptics(true, false)
	var h: Haptics = hr[0]
	var r: Recorder = hr[1]
	h.strength = 0.5
	h.hit()
	now += 1000
	h.win()
	assert_eq(["one_shot", 22, 85], r.calls[0])
	assert_eq(["waveform", [0, 35, 50, 35, 50, 90], [0, 90, 0, 110, 0, 128]], r.calls[1])
	# No amplitude control: shorter buzzes at the default amplitude, on/off waveforms, and no rumble.
	var hr2 := haptics(false, false)
	var h2: Haptics = hr2[0]
	var r2: Recorder = hr2[1]
	h2.strength = 0.5
	h2.heavy()
	now += 1000
	h2.jackpot()
	now += 1000
	h2.rumble(0.8)
	assert_eq(["one_shot", Haptics.scale_duration(55, 0.5), -1], r2.calls[0])
	assert_eq(["waveform", [0, 40, 40, 40, 40, 40, 40, 140], []], r2.calls[1])
	assert_eq(2, r2.calls.size(), "a vibrator without amplitude control skips rumbles")


func test_a_phone_without_a_thud_clicks_instead_and_without_a_low_tick_ticks_softly() -> void:
	var hr := haptics(true, true, false, false)
	var h: Haptics = hr[0]
	var r: Recorder = hr[1]
	h.heavy()
	now += 100
	h.soft()
	assert_eq(["composition", [Haptics.CLICK], [1000], [0]], r.calls[0])
	assert_eq(["composition", [Haptics.TICK], [300], [0]], r.calls[1])


func test_rate_limits() -> void:
	var hr := haptics(true, true)
	var h: Haptics = hr[0]
	var r: Recorder = hr[1]
	h.tick()
	now += 20
	h.tick() # inside the 35 ms gap
	assert_eq(1, r.calls.size())
	now += 20
	h.tick()
	assert_eq(2, r.calls.size())
	# A win holds background texture off for 280 ms...
	now += 100
	h.win()
	now += 100
	h.soft()
	h.rumble(0.9)
	assert_eq(3, r.calls.size(), "texture waits for the pattern")
	now += 200
	h.soft()
	assert_eq(4, r.calls.size())
	# ...but texture never makes an event wait: a hit straight after a soft tick plays.
	now += 30
	h.hit()
	assert_eq(5, r.calls.size())
	# A bump only waits for another bump, and holds a tick off right after it.
	now += 100
	h.bump()
	now += 10
	h.bump()
	h.tick()
	assert_eq(6, r.calls.size())
	now += 90
	h.bump()
	assert_eq(7, r.calls.size())


func test_switched_off_or_silenced_plays_nothing() -> void:
	var hr := haptics(true, true)
	var h: Haptics = hr[0]
	var r: Recorder = hr[1]
	h.enabled = false
	h.hit()
	h.enabled = true
	h.strength = 0.01
	now += 100
	h.hit()
	assert_eq(0, r.calls.size())
	h.strength = 0.02
	now += 100
	h.hit()
	assert_eq(1, r.calls.size())
