class_name Dsp
extends RefCounted
## engine/audio/Dsp.kt: small signal-processing helpers shared by the audio code.

const SINE_SIZE := 2048
const SINE_MASK := SINE_SIZE - 1

## A tiny constant added inside feedback loops so a decaying tail never reaches the denormal range.
const ANTI_DENORMAL := 1e-20

## One cycle of a sine plus a guard point, so an interpolated read at the very end is safe.
static var _sine := PackedFloat32Array()
## MIDI note number to Hz: 69 is A440.
static var _note_hz := PackedFloat32Array()


static func _tables() -> void:
	if not _sine.is_empty():
		return
	_sine.resize(SINE_SIZE + 1)
	for i in SINE_SIZE + 1:
		_sine[i] = sin(i * 2.0 * PI / SINE_SIZE)
	_note_hz.resize(128)
	for i in 128:
		_note_hz[i] = 440.0 * pow(2.0, (i - 69) / 12.0)


static func note_hz(midi: int) -> float:
	_tables()
	return _note_hz[clampi(midi, 0, 127)]


## A table-driven sine of [param phase], a position in one cycle in 0..1 (wrapped if it strays).
static func sine(phase: float) -> float:
	_tables()
	var x := phase * SINE_SIZE
	var i := int(x)
	var f := x - i
	var k := i & SINE_MASK
	return _sine[k] + (_sine[k + 1] - _sine[k]) * f


## The sine table itself, for hot loops that index it directly.
static func sine_table() -> PackedFloat32Array:
	_tables()
	return _sine


## A smooth limiter: a Padé approximation of tanh, transparent for small signals, never leaving -1..1.
static func soft_limit(x: float) -> float:
	if x >= 3.0:
		return 1.0
	if x <= -3.0:
		return -1.0
	var x2 := x * x
	return x * (27.0 + x2) / (27.0 + 9.0 * x2)


## Cubic smoothstep of [param x] in 0..1.
static func smooth(x: float) -> float:
	var t := clampf(x, 0.0, 1.0)
	return t * t * (3.0 - 2.0 * t)
