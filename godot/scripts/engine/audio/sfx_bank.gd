class_name SfxBank
extends RefCounted
## engine/audio/SfxBank.kt: every sound effect, synthesized once into a mono buffer (there are no
## audio files). [method generate_all] renders them; the mixer plays them as [AudioStreamWAV]s.
##
## Each [method _tone] call is the Kotlin one with its named arguments as a dictionary. Sample
## positions are computed in 32-bit floats like Kotlin's, so every note starts on the same sample;
## the synthesis itself runs in GDScript's 64-bit floats.

const SQUARE := 0
const TRIANGLE := 1
const SAW := 2
const SINE := 3
const NOISE := 4

## Bump when the synthesis changes, so cached sounds are rebuilt.
const VERSION := 1

var sample_rate: int
var _sounds: Array = []
var _noise_rng := KRandom.new(7)
var _f32 := PackedFloat32Array([0.0])


func _init(rate: int = 48000) -> void:
	sample_rate = rate
	_sounds.resize(Sfx.COUNT)


## The synthesized samples of [param sfx] once [method generate_all] has run, or an empty array.
func samples(sfx: int) -> PackedFloat32Array:
	var s: Variant = _sounds[sfx]
	return s if s != null else PackedFloat32Array()


func has_all() -> bool:
	for s in _sounds:
		if s == null:
			return false
	return true


## Rounds [param x] to a 32-bit float (Kotlin's Float).
func _to_f32(x: float) -> float:
	_f32[0] = x
	return _f32[0]


## (seconds * sampleRate).toInt() in Kotlin's Float arithmetic.
func _frames(seconds: float) -> int:
	return int(_to_f32(_to_f32(seconds) * float(sample_rate)))


func _buf(seconds: float) -> PackedFloat32Array:
	var b := PackedFloat32Array()
	b.resize(_frames(seconds) + 2)
	return b


## Adds one note to [param b]: frequency sweeps exponentially from f0 to f1 over [param dur] seconds,
## with a linear attack, optional exponential decay and a short click-free release. [param o] holds
## Kotlin's named arguments: f1, wave, vol, attack, decay, duty, vib_hz, vib_depth, lowpass.
func _tone(b: PackedFloat32Array, start: float, dur: float, f0: float, o: Dictionary = {}) -> void:
	var f1: float = o.get("f1", f0)
	var wave: int = o.get("wave", SQUARE)
	var vol: float = o.get("vol", 0.3)
	var attack: float = o.get("attack", 0.003)
	var decay: float = o.get("decay", 0.0)
	var duty: float = o.get("duty", 0.5)
	var vib_hz: float = o.get("vib_hz", 0.0)
	var vib_depth: float = o.get("vib_depth", 0.0)
	var lowpass: float = o.get("lowpass", 1.0)
	var sr := float(sample_rate)
	var s0 := _frames(start)
	var n := mini(_frames(dur), b.size() - s0)
	# The sweep f0 * (f1 / f0)^(t / dur) and the decay exp(-decay * (t - attack)) are stepped by a
	# constant factor per sample (the same curves as Kotlin's per-sample pow and exp).
	var sweep := f0
	var sweep_step := pow(f1 / f0, 1.0 / (dur * sr))
	var decaying := false
	var dec := 1.0
	var dec_step := exp(-decay / sr)
	var tail_from := dur - 0.008
	var phase := 0.0
	var held := 0.0
	var filtered := 0.0
	for i in n:
		var t := i / sr
		var f := sweep * (1.0 + vib_depth * sin(TAU * vib_hz * t)) if vib_hz > 0.0 else sweep
		sweep *= sweep_step
		var prev := phase
		phase += f / sr
		if phase >= 1.0:
			phase = fmod(phase, 1.0)
		var raw: float
		if wave == SQUARE:
			raw = 1.0 if phase < duty else -1.0
		elif wave == TRIANGLE:
			raw = phase * 4.0 - 1.0 if phase < 0.5 else 3.0 - phase * 4.0
		elif wave == SAW:
			raw = phase * 2.0 - 1.0
		elif wave == SINE:
			raw = sin(phase * TAU)
		else:
			if phase < prev:
				held = _noise_rng.next_float() * 2.0 - 1.0
			raw = held
		filtered += (raw - filtered) * lowpass
		var env := 1.0
		if t < attack:
			env = t / attack
		elif decay > 0.0:
			if decaying:
				dec *= dec_step
			else:
				dec = exp(-decay * (t - attack))
				decaying = true
			env = dec
		if t > tail_from:
			env *= maxf((dur - t) / 0.008, 0.0)
		b[s0 + i] += filtered * env * vol


func _put(sfx: int, b: PackedFloat32Array) -> void:
	_sounds[sfx] = b


func generate_all() -> void:
	_noise_rng = KRandom.new(7)
	var b: PackedFloat32Array
	b = _buf(0.06)
	_tone(b, 0.0, 0.055, 880.0, {f1 = 1320.0, vol = 0.22, duty = 0.25})
	_put(Sfx.BLIP, b)
	b = _buf(0.12)
	_tone(b, 0.0, 0.045, 660.0, {vol = 0.22, duty = 0.25})
	_tone(b, 0.045, 0.07, 990.0, {vol = 0.22, duty = 0.25, decay = 20.0})
	_put(Sfx.SELECT, b)
	b = _buf(0.3)
	_tone(b, 0.0, 0.12, 220.0, {f1 = 190.0, vol = 0.25, duty = 0.3})
	_tone(b, 0.14, 0.14, 180.0, {f1 = 150.0, vol = 0.25, duty = 0.3})
	_put(Sfx.ERROR, b)
	b = _buf(0.32)
	_tone(b, 0.0, 0.06, 988.0, {vol = 0.22, duty = 0.5})
	_tone(b, 0.06, 0.25, 1319.0, {vol = 0.22, duty = 0.5, decay = 10.0})
	_put(Sfx.COIN, b)
	b = _buf(0.45)
	_tone(b, 0.0, 0.03, 6000.0, {wave = NOISE, vol = 0.25, decay = 60.0})
	_tone(b, 0.02, 0.12, 520.0, {f1 = 240.0, wave = TRIANGLE, vol = 0.45, decay = 18.0})
	_tone(b, 0.12, 0.32, 1568.0, {wave = SINE, vol = 0.3, decay = 9.0})
	_tone(b, 0.12, 0.32, 2352.0, {wave = SINE, vol = 0.12, decay = 12.0})
	_put(Sfx.TOKEN, b)
	b = _buf(0.05)
	_tone(b, 0.0, 0.03, 1800.0, {wave = TRIANGLE, vol = 0.25, decay = 60.0})
	_tone(b, 0.0, 0.02, 9000.0, {wave = NOISE, vol = 0.08, decay = 90.0})
	_put(Sfx.TICKET, b)
	b = _buf(0.08)
	_tone(b, 0.0, 0.07, 90.0, {f1 = 80.0, vol = 0.12, duty = 0.3, lowpass = 0.3})
	_tone(b, 0.0, 0.07, 3000.0, {wave = NOISE, vol = 0.07, lowpass = 0.4})
	_put(Sfx.PRINT, b)
	b = _buf(0.7)
	var win_notes := [523.0, 659.0, 784.0, 1047.0]
	for i in win_notes.size():
		var last := i == win_notes.size() - 1
		_tone(b, i * 0.08, 0.4 if last else 0.08, win_notes[i], {vol = 0.2, duty = 0.25, decay = 5.0 if last else 0.0, vib_hz = 7.0 if last else 0.0, vib_depth = 0.01})
	_tone(b, 0.0, 0.6, 131.0, {wave = TRIANGLE, vol = 0.3, decay = 3.0})
	_put(Sfx.WIN, b)
	b = _buf(1.3)
	var jackpot_notes := [523.0, 659.0, 784.0, 1047.0, 1319.0, 1568.0]
	for r in 3:
		for i in jackpot_notes.size():
			_tone(b, r * 0.3 + i * 0.045, 0.05, jackpot_notes[i] * (1.0 + r * 0.12), {vol = 0.16, duty = 0.25})
	_tone(b, 0.9, 0.4, 2093.0, {vol = 0.18, duty = 0.25, decay = 5.0, vib_hz = 8.0, vib_depth = 0.015})
	_tone(b, 0.0, 1.2, 131.0, {f1 = 262.0, wave = TRIANGLE, vol = 0.3, decay = 1.5})
	_put(Sfx.JACKPOT, b)
	b = _buf(0.6)
	_tone(b, 0.0, 0.18, 392.0, {vol = 0.2, duty = 0.25})
	_tone(b, 0.18, 0.18, 330.0, {vol = 0.2, duty = 0.25})
	_tone(b, 0.36, 0.22, 262.0, {f1 = 220.0, vol = 0.2, duty = 0.25, decay = 4.0})
	_put(Sfx.LOSE, b)
	b = _buf(0.18)
	_tone(b, 0.0, 0.05, 5000.0, {wave = NOISE, vol = 0.5, decay = 50.0, lowpass = 0.5})
	_tone(b, 0.0, 0.16, 190.0, {f1 = 55.0, wave = SINE, vol = 0.8, decay = 18.0})
	_put(Sfx.WHACK, b)
	b = _buf(0.2)
	_tone(b, 0.0, 0.1, 740.0, {f1 = 370.0, vol = 0.22, duty = 0.5, decay = 18.0})
	_tone(b, 0.0, 0.15, 370.0, {f1 = 185.0, wave = TRIANGLE, vol = 0.35, decay = 14.0})
	_put(Sfx.BONK, b)
	b = _buf(0.9)
	_tone(b, 0.0, 0.85, 2200.0, {f1 = 400.0, wave = NOISE, vol = 0.7, decay = 4.5, lowpass = 0.25})
	_tone(b, 0.0, 0.6, 95.0, {f1 = 28.0, wave = SINE, vol = 0.9, decay = 5.0})
	_put(Sfx.BOMB, b)
	b = _buf(0.08)
	_tone(b, 0.0, 0.07, 300.0, {f1 = 900.0, wave = SINE, vol = 0.35, decay = 20.0})
	_put(Sfx.POP, b)
	b = _buf(0.35)
	_tone(b, 0.0, 0.32, 1500.0, {f1 = 7000.0, wave = NOISE, vol = 0.35, attack = 0.05, decay = 7.0, lowpass = 0.35})
	_put(Sfx.SWISH, b)
	b = _buf(0.35)
	_tone(b, 0.0, 0.3, 523.0, {wave = SINE, vol = 0.3, decay = 12.0})
	_tone(b, 0.0, 0.3, 1190.0, {wave = SINE, vol = 0.2, decay = 14.0})
	_tone(b, 0.0, 0.3, 1870.0, {wave = SINE, vol = 0.14, decay = 16.0})
	_tone(b, 0.0, 0.02, 6000.0, {wave = NOISE, vol = 0.2, decay = 90.0})
	_put(Sfx.RIM, b)
	b = _buf(0.12)
	_tone(b, 0.0, 0.1, 150.0, {f1 = 80.0, wave = SINE, vol = 0.8, decay = 25.0})
	_put(Sfx.BOUNCE, b)
	b = _buf(0.16)
	_tone(b, 0.0, 0.14, 110.0, {f1 = 50.0, wave = SINE, vol = 0.8, decay = 20.0})
	_tone(b, 0.0, 0.04, 3000.0, {wave = NOISE, vol = 0.2, decay = 60.0, lowpass = 0.3})
	_put(Sfx.THUD, b)
	b = _buf(0.6)
	_tone(b, 0.0, 0.58, 900.0, {wave = NOISE, vol = 0.35, attack = 0.04, lowpass = 0.08, decay = 2.0})
	_tone(b, 0.0, 0.58, 70.0, {f1 = 55.0, wave = TRIANGLE, vol = 0.18, attack = 0.04, decay = 2.0})
	_put(Sfx.ROLL, b)
	b = _buf(0.22)
	_tone(b, 0.0, 0.21, 118.0, {wave = SAW, vol = 0.1, attack = 0.02, vib_hz = 30.0, vib_depth = 0.05, lowpass = 0.3})
	_put(Sfx.CLAW_MOTOR, b)
	b = _buf(0.2)
	_tone(b, 0.0, 0.03, 7000.0, {wave = NOISE, vol = 0.25, decay = 70.0})
	_tone(b, 0.01, 0.15, 320.0, {f1 = 200.0, duty = 0.3, vol = 0.16, decay = 16.0})
	_put(Sfx.CLAW_GRAB, b)
	b = _buf(0.3)
	_tone(b, 0.0, 0.28, 700.0, {f1 = 200.0, wave = SINE, vol = 0.3, decay = 5.0})
	_put(Sfx.DROP, b)
	b = _buf(0.8)
	var prize_notes := [784.0, 988.0, 1175.0, 1568.0, 1976.0]
	for i in prize_notes.size():
		_tone(b, i * 0.06, 0.3, prize_notes[i], {wave = TRIANGLE, vol = 0.25, decay = 7.0})
	_tone(b, 0.3, 0.45, 3136.0, {wave = SINE, vol = 0.1, decay = 6.0, vib_hz = 12.0, vib_depth = 0.02})
	_put(Sfx.PRIZE, b)
	b = _buf(1.3)
	for k in 7:
		_tone(b, k * 0.08, 1.1 - k * 0.08, 1200.0 + k * 300.0, {wave = NOISE, vol = 0.12, attack = 0.1, decay = 2.2, lowpass = 0.12, vib_hz = 6.0 + k, vib_depth = 0.3})
	_put(Sfx.CHEER, b)
	b = _buf(0.03)
	_tone(b, 0.0, 0.025, 2000.0, {wave = NOISE, vol = 0.08, decay = 80.0, lowpass = 0.2})
	_put(Sfx.STEP, b)
	b = _buf(0.5)
	_tone(b, 0.0, 0.48, 400.0, {f1 = 5000.0, wave = NOISE, vol = 0.3, attack = 0.2, decay = 3.0, lowpass = 0.2})
	_put(Sfx.WHOOSH, b)
	b = _buf(0.14)
	_tone(b, 0.0, 0.12, 440.0, {vol = 0.22, duty = 0.5, decay = 8.0})
	_put(Sfx.COUNTDOWN, b)
	b = _buf(0.4)
	_tone(b, 0.0, 0.38, 880.0, {vol = 0.22, duty = 0.5, decay = 4.0, vib_hz = 9.0, vib_depth = 0.02})
	_put(Sfx.GO, b)
	b = _buf(1.6)
	var melody := [523.0, 659.0, 784.0, 659.0, 784.0, 1047.0]
	var times := [0.0, 0.12, 0.24, 0.4, 0.52, 0.7]
	for i in melody.size():
		var last := i == melody.size() - 1
		_tone(b, times[i], 0.8 if last else 0.12, melody[i], {vol = 0.2, duty = 0.25, decay = 2.5 if last else 0.0, vib_hz = 6.0 if last else 0.0, vib_depth = 0.012})
		_tone(b, times[i], 0.8 if last else 0.12, melody[i] * 1.5, {wave = TRIANGLE, vol = 0.1, decay = 2.5 if last else 0.0})
	_tone(b, 0.0, 1.5, 131.0, {wave = TRIANGLE, vol = 0.3, decay = 1.2})
	_put(Sfx.HIGHSCORE, b)
	b = _buf(0.16)
	_tone(b, 0.0, 0.15, 2100.0, {wave = SINE, vol = 0.22, decay = 25.0})
	_tone(b, 0.0, 0.15, 3350.0, {wave = SINE, vol = 0.14, decay = 30.0})
	_put(Sfx.CLINK, b)
	b = _buf(0.7)
	var spill := KRandom.new(3)
	for k in 9:
		var st := spill.next_float() * 0.5
		_tone(b, st, 0.12, spill.range_f(1800.0, 2600.0), {wave = SINE, vol = 0.14, decay = 30.0})
		_tone(b, st, 0.12, spill.range_f(3000.0, 3800.0), {wave = SINE, vol = 0.08, decay = 35.0})
	_put(Sfx.SPILL, b)
	b = _buf(0.6)
	_tone(b, 0.0, 0.55, 150.0, {wave = SAW, vol = 0.18, lowpass = 0.5})
	_tone(b, 0.0, 0.55, 155.0, {wave = SQUARE, vol = 0.1, duty = 0.4, lowpass = 0.5})
	_put(Sfx.BUZZER, b)
	b = _buf(1.0)
	for k in 10:
		_tone(b, k * 0.07, 0.2, 1047.0 * pow(2.0, k / 12.0 * 2.0), {wave = TRIANGLE, vol = 0.18, decay = 10.0})
	_tone(b, 0.7, 0.3, 2637.0, {wave = SINE, vol = 0.12, decay = 8.0, vib_hz = 14.0, vib_depth = 0.03})
	_put(Sfx.LUCKY, b)
	b = _buf(0.5)
	_tone(b, 0.0, 0.45, 300.0, {f1 = 90.0, wave = TRIANGLE, vol = 0.3, decay = 3.0})
	_tone(b, 0.0, 0.45, 600.0, {wave = NOISE, vol = 0.15, lowpass = 0.1, decay = 4.0})
	_put(Sfx.GUTTER, b)
	_generate_machine_sounds()
	# Normalise any buffer that clips so stacked partials never distort.
	for i in _sounds.size():
		if _sounds[i] == null:
			continue
		var s: PackedFloat32Array = _sounds[i]
		var peak := 0.0
		for v in s:
			peak = maxf(peak, absf(v))
		if peak > 0.95:
			var k := 0.95 / peak
			for j in s.size():
				s[j] *= k
			_sounds[i] = s


## The light-gun, pinball, fishing and racer sounds.
func _generate_machine_sounds() -> void:
	var b: PackedFloat32Array
	# ---- Light-gun shooter.
	b = _buf(0.3)
	_tone(b, 0.0, 0.012, 9000.0, {wave = NOISE, vol = 0.6, decay = 150.0})
	_tone(b, 0.0, 0.26, 6000.0, {f1 = 700.0, wave = NOISE, vol = 0.7, decay = 16.0, lowpass = 0.45})
	_tone(b, 0.0, 0.2, 170.0, {f1 = 42.0, wave = SINE, vol = 0.9, decay = 20.0})
	_put(Sfx.GUNSHOT, b)
	b = _buf(0.34)
	# Magazine out, slide back, slide home.
	_tone(b, 0.0, 0.03, 2600.0, {wave = SQUARE, vol = 0.18, duty = 0.2, decay = 90.0})
	_tone(b, 0.0, 0.03, 7000.0, {wave = NOISE, vol = 0.25, decay = 90.0})
	_tone(b, 0.1, 0.1, 1500.0, {f1 = 3200.0, wave = NOISE, vol = 0.18, attack = 0.02, lowpass = 0.3})
	_tone(b, 0.22, 0.1, 1900.0, {wave = SQUARE, vol = 0.22, duty = 0.3, decay = 60.0})
	_tone(b, 0.22, 0.06, 8000.0, {wave = NOISE, vol = 0.3, decay = 70.0})
	_tone(b, 0.22, 0.1, 240.0, {f1 = 120.0, wave = SINE, vol = 0.4, decay = 35.0})
	_put(Sfx.RELOAD, b)
	b = _buf(0.06)
	_tone(b, 0.0, 0.04, 3200.0, {wave = SQUARE, vol = 0.18, duty = 0.15, decay = 120.0})
	_tone(b, 0.0, 0.02, 6000.0, {wave = NOISE, vol = 0.15, decay = 150.0})
	_put(Sfx.DRY_FIRE, b)
	b = _buf(0.5)
	_tone(b, 0.0, 0.02, 8000.0, {wave = NOISE, vol = 0.4, decay = 120.0})
	_tone(b, 0.01, 0.46, 3400.0, {f1 = 1100.0, wave = SINE, vol = 0.3, decay = 5.0, vib_hz = 38.0, vib_depth = 0.03})
	_tone(b, 0.01, 0.4, 5100.0, {f1 = 1600.0, wave = SINE, vol = 0.1, decay = 7.0})
	_put(Sfx.RICOCHET, b)
	b = _buf(1.3)
	_tone(b, 0.0, 1.25, 2600.0, {f1 = 180.0, wave = NOISE, vol = 0.75, decay = 3.0, lowpass = 0.22})
	_tone(b, 0.0, 0.9, 80.0, {f1 = 24.0, wave = SINE, vol = 0.95, decay = 3.5})
	var boom := KRandom.new(11)
	for k in 8:
		_tone(b, 0.15 + boom.next_float() * 0.7, 0.05, 4000.0, {wave = NOISE, vol = 0.12, decay = 60.0})
	_put(Sfx.EXPLOSION, b)
	b = _buf(0.22)
	_tone(b, 0.0, 0.2, 1600.0, {f1 = 260.0, wave = SQUARE, vol = 0.18, duty = 0.3, decay = 10.0})
	_tone(b, 0.0, 0.08, 5000.0, {wave = NOISE, vol = 0.2, decay = 40.0, lowpass = 0.5})
	_put(Sfx.ENEMY_FIRE, b)
	b = _buf(0.95)
	for k in 6:
		_tone(b, k * 0.15, 0.15, 880.0 if k % 2 == 0 else 660.0, {vol = 0.18, duty = 0.5, lowpass = 0.6})
	_put(Sfx.ALARM, b)
	b = _buf(0.2)
	# A spent casing bouncing on the floor.
	for k in 3:
		_tone(b, k * 0.06 - k * k * 0.008, 0.05, 4200.0 - k * 300.0, {wave = SINE, vol = 0.14 / (k + 1), decay = 50.0})
	_put(Sfx.SHELL, b)

	# ---- Pinball.
	b = _buf(0.12)
	_tone(b, 0.0, 0.015, 5000.0, {wave = NOISE, vol = 0.35, decay = 120.0})
	_tone(b, 0.0, 0.1, 140.0, {f1 = 60.0, wave = SINE, vol = 0.7, decay = 30.0})
	_tone(b, 0.0, 0.05, 420.0, {f1 = 220.0, wave = SQUARE, vol = 0.12, duty = 0.3, decay = 40.0})
	_put(Sfx.FLIPPER, b)
	b = _buf(0.3)
	_tone(b, 0.0, 0.02, 7000.0, {wave = NOISE, vol = 0.3, decay = 100.0})
	_tone(b, 0.0, 0.25, 1100.0, {f1 = 700.0, wave = SINE, vol = 0.35, decay = 16.0})
	_tone(b, 0.0, 0.25, 2200.0, {wave = TRIANGLE, vol = 0.15, decay = 14.0})
	_tone(b, 0.0, 0.12, 180.0, {f1 = 90.0, wave = SINE, vol = 0.5, decay = 25.0})
	_put(Sfx.BUMPER, b)
	b = _buf(0.14)
	_tone(b, 0.0, 0.05, 6000.0, {wave = NOISE, vol = 0.4, decay = 70.0})
	_tone(b, 0.0, 0.12, 620.0, {f1 = 260.0, wave = SQUARE, vol = 0.15, duty = 0.25, decay = 22.0})
	_put(Sfx.SLINGSHOT, b)
	b = _buf(0.45)
	_tone(b, 0.0, 0.4, 90.0, {f1 = 240.0, wave = SAW, vol = 0.2, vib_hz = 22.0, vib_depth = 0.08, decay = 5.0, lowpass = 0.4})
	_tone(b, 0.02, 0.3, 800.0, {f1 = 4000.0, wave = NOISE, vol = 0.2, attack = 0.03, decay = 8.0, lowpass = 0.3})
	_put(Sfx.PLUNGER, b)
	b = _buf(0.9)
	_tone(b, 0.0, 0.3, 392.0, {f1 = 370.0, wave = TRIANGLE, vol = 0.25})
	_tone(b, 0.3, 0.55, 294.0, {f1 = 110.0, wave = TRIANGLE, vol = 0.28, decay = 3.0})
	_tone(b, 0.0, 0.8, 300.0, {f1 = 80.0, wave = NOISE, vol = 0.15, lowpass = 0.08, decay = 3.0})
	_put(Sfx.DRAIN, b)
	b = _buf(0.45)
	# Ticks slowing down as the spinner winds out (Kotlin accumulates these in 32-bit floats).
	var at := 0.0
	var gap := _to_f32(0.022)
	while at < _to_f32(0.4):
		_tone(b, at, 0.015, 2300.0, {wave = SQUARE, vol = 0.15, duty = 0.2, decay = 150.0})
		at = _to_f32(at + gap)
		gap = _to_f32(gap * _to_f32(1.18))
	_put(Sfx.SPINNER, b)
	b = _buf(0.8)
	for k in 4:
		_tone(b, k * 0.2, 0.14, 110.0, {wave = SAW, vol = 0.2, lowpass = 0.35, vib_hz = 30.0, vib_depth = 0.04})
	_put(Sfx.TILT, b)

	# ---- Fishing.
	b = _buf(0.5)
	_tone(b, 0.0, 0.45, 700.0, {f1 = 5200.0, wave = NOISE, vol = 0.3, attack = 0.06, decay = 5.0, lowpass = 0.25})
	_tone(b, 0.02, 0.4, 1800.0, {f1 = 2600.0, wave = SQUARE, vol = 0.04, duty = 0.1, vib_hz = 60.0, vib_depth = 0.3, decay = 4.0})
	_put(Sfx.CAST, b)
	b = _buf(0.6)
	_tone(b, 0.0, 0.55, 3200.0, {f1 = 500.0, wave = NOISE, vol = 0.45, decay = 6.0, lowpass = 0.3})
	_tone(b, 0.0, 0.12, 260.0, {f1 = 90.0, wave = SINE, vol = 0.4, decay = 22.0})
	var splash := KRandom.new(5)
	for k in 5:
		var st := 0.08 + splash.next_float() * 0.3
		var a := splash.range_f(900.0, 1600.0)
		var z := splash.range_f(1800.0, 2600.0)
		_tone(b, st, 0.05, a, {f1 = z, wave = SINE, vol = 0.1, decay = 40.0})
	_put(Sfx.SPLASH, b)
	b = _buf(0.2)
	# Ratchet clicks, short enough to repeat while the crank turns.
	for k in 8:
		_tone(b, k * 0.025, 0.012, 1500.0, {wave = SQUARE, vol = 0.12, duty = 0.2, decay = 200.0})
		_tone(b, k * 0.025, 0.008, 5000.0, {wave = NOISE, vol = 0.08, decay = 250.0})
	_put(Sfx.REEL, b)
	b = _buf(0.28)
	_tone(b, 0.0, 0.1, 280.0, {f1 = 720.0, wave = SINE, vol = 0.4, decay = 14.0})
	_tone(b, 0.13, 0.12, 320.0, {f1 = 900.0, wave = SINE, vol = 0.45, decay = 12.0})
	_put(Sfx.BITE, b)
	b = _buf(0.5)
	_tone(b, 0.0, 0.02, 9000.0, {wave = NOISE, vol = 0.45, decay = 150.0})
	_tone(b, 0.0, 0.45, 950.0, {f1 = 180.0, wave = SAW, vol = 0.18, decay = 7.0, vib_hz = 24.0, vib_depth = 0.06, lowpass = 0.5})
	_put(Sfx.LINE_SNAP, b)
	b = _buf(0.8)
	var catch_notes := [660.0, 880.0, 1100.0, 1320.0, 1760.0]
	for i in catch_notes.size():
		_tone(b, i * 0.07, 0.25, catch_notes[i], {wave = TRIANGLE, vol = 0.22, decay = 9.0})
	_tone(b, 0.0, 0.3, 2400.0, {f1 = 700.0, wave = NOISE, vol = 0.15, decay = 9.0, lowpass = 0.3})
	_put(Sfx.CATCH, b)

	# ---- Racer.
	b = _buf(0.24)
	# Steady (no decay) so back-to-back plays blur into one engine note.
	_tone(b, 0.0, 0.24, 82.0, {wave = SAW, vol = 0.22, attack = 0.01, vib_hz = 32.0, vib_depth = 0.06, lowpass = 0.25})
	_tone(b, 0.0, 0.24, 41.0, {wave = SQUARE, vol = 0.12, attack = 0.01, duty = 0.35, lowpass = 0.2})
	_tone(b, 0.0, 0.24, 1200.0, {wave = NOISE, vol = 0.05, attack = 0.01, lowpass = 0.1})
	_put(Sfx.ENGINE, b)
	b = _buf(0.6)
	_tone(b, 0.0, 0.55, 2200.0, {f1 = 1500.0, wave = NOISE, vol = 0.35, attack = 0.03, decay = 3.0, lowpass = 0.55, vib_hz = 45.0, vib_depth = 0.2})
	_tone(b, 0.0, 0.5, 900.0, {f1 = 760.0, wave = SQUARE, vol = 0.05, duty = 0.1, decay = 3.0, vib_hz = 45.0, vib_depth = 0.05})
	_put(Sfx.SKID, b)
	b = _buf(0.7)
	_tone(b, 0.0, 0.65, 140.0, {f1 = 620.0, wave = SAW, vol = 0.2, attack = 0.05, decay = 2.5, lowpass = 0.35})
	_tone(b, 0.0, 0.6, 500.0, {f1 = 5000.0, wave = NOISE, vol = 0.25, attack = 0.1, decay = 3.0, lowpass = 0.3})
	_put(Sfx.BOOST, b)
	b = _buf(1.0)
	_tone(b, 0.0, 0.95, 4500.0, {f1 = 300.0, wave = NOISE, vol = 0.65, decay = 4.0, lowpass = 0.35})
	_tone(b, 0.0, 0.5, 95.0, {f1 = 30.0, wave = SINE, vol = 0.9, decay = 6.0})
	_tone(b, 0.03, 0.6, 1320.0, {wave = SINE, vol = 0.14, decay = 9.0})
	_tone(b, 0.05, 0.6, 1930.0, {wave = SINE, vol = 0.1, decay = 11.0})
	_put(Sfx.CRASH, b)
	b = _buf(0.5)
	_tone(b, 0.0, 0.12, 988.0, {wave = TRIANGLE, vol = 0.3, decay = 8.0})
	_tone(b, 0.12, 0.35, 1319.0, {wave = TRIANGLE, vol = 0.3, decay = 6.0})
	_put(Sfx.LAP, b)
	b = _buf(1.4)
	var finish := [523.0, 659.0, 784.0, 1047.0, 784.0, 1047.0]
	var finish_times := [0.0, 0.1, 0.2, 0.3, 0.5, 0.6]
	for i in finish.size():
		var last := i == finish.size() - 1
		_tone(b, finish_times[i], 0.7 if last else 0.1, finish[i], {vol = 0.2, duty = 0.25, decay = 3.0 if last else 0.0, vib_hz = 7.0 if last else 0.0, vib_depth = 0.012})
	_tone(b, 0.0, 1.3, 131.0, {f1 = 196.0, wave = TRIANGLE, vol = 0.3, decay = 1.5})
	_put(Sfx.FINISH, b)
	b = _buf(0.5)
	_tone(b, 0.0, 0.45, 350.0, {vol = 0.18, duty = 0.45, lowpass = 0.35})
	_tone(b, 0.0, 0.45, 440.0, {vol = 0.14, duty = 0.45, lowpass = 0.35})
	_put(Sfx.HORN, b)

	# ---- The café: the espresso machine's steam wand, a long soft hiss that swells and fades.
	b = _buf(2.4)
	_tone(b, 0.0, 2.3, 9000.0, {f1 = 5000.0, wave = NOISE, vol = 0.34, attack = 0.4, decay = 1.3, lowpass = 0.8})
	_tone(b, 0.0, 2.3, 5200.0, {f1 = 3200.0, wave = NOISE, vol = 0.2, attack = 0.55, decay = 1.0, lowpass = 0.45})
	_put(Sfx.STEAM, b)


# ------------------------------------------------------------------ streams and the disk cache

## [param s] as a mono 16-bit [AudioStreamWAV] at this bank's rate (converted by Godot's WAV loader).
func stream_of(s: PackedFloat32Array) -> AudioStreamWAV:
	return SfxBank.wav_stream(s, sample_rate, 1)


## Float samples (interleaved when [param channels] is 2) as a 16-bit [AudioStreamWAV]: the samples
## are wrapped in a 32-bit float WAV file and handed to Godot's loader, which does the conversion.
static func wav_stream(s: PackedFloat32Array, rate: int, channels: int, loop_end: int = -1) -> AudioStreamWAV:
	var data := s.to_byte_array()
	var header := PackedByteArray()
	header.resize(44)
	header.encode_u32(0, 0x46464952) # "RIFF"
	header.encode_u32(4, 36 + data.size())
	header.encode_u32(8, 0x45564157) # "WAVE"
	header.encode_u32(12, 0x20746d66) # "fmt "
	header.encode_u32(16, 16)
	header.encode_u16(20, 3) # IEEE float
	header.encode_u16(22, channels)
	header.encode_u32(24, rate)
	header.encode_u32(28, rate * channels * 4)
	header.encode_u16(32, channels * 4)
	header.encode_u16(34, 32)
	header.encode_u32(36, 0x61746164) # "data"
	header.encode_u32(40, data.size())
	var options := {"compress/mode": 0, "force/8_bit": false, "force/mono": false, "force/max_rate": false, "edit/trim": false, "edit/normalize": false}
	if loop_end > 0:
		options["edit/loop_mode"] = 2 # forward
		options["edit/loop_begin"] = 0
		options["edit/loop_end"] = loop_end
	var wav := AudioStreamWAV.load_from_buffer(header + data, options)
	return wav
