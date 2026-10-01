class_name Haptics
extends RefCounted
## engine/Haptics.kt: short vibration patterns for hits, bonks and wins, rate-limited so bursts
## don't smear together. Every decision is build-13's (which primitive or fallback, its strength,
## the gaps); a [Device] (the Android plugin's VibrationEffect calls) makes the phone buzz. Without
## one (desktop, tests) every call is a no-op.
##
## On phones with a haptic engine (API 30+ supporting the primitives) the patterns are composed of
## primitives (click, tick, thud, low tick...); everything else falls back to one-shots and
## waveforms. The plugin plays every effect with the touch usage from API 33, as build-13 did.

## VibrationEffect.Composition.PRIMITIVE_* ids.
const CLICK := 1
const THUD := 2
const QUICK_RISE := 4
const TICK := 7
const LOW_TICK := 8

## Below this strength the vibrator stays off.
const MIN_STRENGTH := 0.02
## Shortest gaps between the effects that are only background texture.
const SOFT_GAP_MS := 45
const RUMBLE_GAP_MS := 110
const BUMP_GAP_MS := 90

const WIN_WAVE_MS := [0, 35, 50, 35, 50, 90]
const WIN_WAVE_AMP := [0, 180, 0, 220, 0, 255]
const WIN_PRIMS := [CLICK, CLICK, THUD]
const WIN_SCALES := [0.7, 0.85, 1.0]
const WIN_DELAYS := [0, 70, 70]

const JACKPOT_WAVE_MS := [0, 40, 40, 40, 40, 40, 40, 140]
const JACKPOT_WAVE_AMP := [0, 160, 0, 200, 0, 230, 0, 255]
const JACKPOT_PRIMS := [TICK, CLICK, CLICK, CLICK, QUICK_RISE, THUD]
const JACKPOT_SCALES := [0.6, 0.7, 0.8, 0.9, 1.0, 1.0]
const JACKPOT_DELAYS := [0, 45, 45, 45, 60, 30]


## What the phone's vibrator can do, and the calls that drive it (see [AndroidBridge]).
class Device:
	extends RefCounted
	## The vibrator can vary its amplitude.
	var amplitude_control := false
	## Click, tick and quick rise compose (API 30+).
	var primitives := false
	## The thud primitive (API 31); a click stands in without it.
	var thud := false
	## The low tick primitive (API 31).
	var low_tick := false

	## A buzz of [param ms] at [param amplitude] (1..255, or -1 for the vibrator's default).
	func one_shot(_ms: int, _amplitude: int) -> void:
		pass

	## A waveform: [param timings] alternate off/on; [param amplitudes] empty means on/off only.
	func waveform(_timings: PackedInt32Array, _amplitudes: PackedInt32Array) -> void:
		pass

	## Primitives at scales (0..1) with delays (ms) before each.
	func composition(_prims: PackedInt32Array, _scales: PackedFloat32Array, _delays: PackedInt32Array) -> void:
		pass


## A base amplitude (1..255) at [param strength] (0..1).
static func scale_amplitude(base: int, strength: float) -> int:
	return clampi(MathUtil.round_to_int(base * strength), 1, 255)


## A base duration at [param strength], for a vibrator that can't vary its amplitude: a weaker
## setting means a shorter buzz (never below 4 ms).
static func scale_duration(ms: int, strength: float) -> int:
	return maxi(int(ms * (0.4 + 0.6 * strength)), 4)


## Amplitude (1..255) of a rumble at [param level] (0..1): always a low buzz, never a knock.
static func rumble_amplitude(level: float) -> int:
	return MathUtil.round_to_int(20.0 + 70.0 * clampf(level, 0.0, 1.0))


## Master switch: false silences every call.
var enabled := true

## How strong the vibration is, 0..1, scaling every amplitude and primitive; near zero it is off.
var strength := 1.0:
	set(value):
		strength = 1.0 if is_nan(value) else clampf(value, 0.0, 1.0)

## Milliseconds now (the engine's monotonic clock; tests replace it).
var clock: Callable = Time.get_ticks_msec

var _device: Device
var _last_at := -1000000
var _last_ambient_at := -1000000
var _last_bump_at := -1000000
## Until when a multi-beat pattern is still playing (background texture waits for it).
var _busy_until := 0


func _init(device: Device = null) -> void:
	_device = device


func device() -> Device:
	return _device


## The primitive to play for [param id]: a thud where the phone has one, a click otherwise.
func _playable(id: int) -> int:
	return CLICK if id == THUD and not _device.thud else id


## A light tick: a tap on something, a step in a count.
func tick() -> void:
	_single(35, TICK, 0.6, 10, 70)


## A firm knock: a hit or a landed catch.
func hit() -> void:
	_single(25, CLICK, 0.7, 22, 170)


## The heaviest single thud: a slam.
func heavy() -> void:
	_single(25, THUD, 1.0, 55, 255)


## A very light tick, for texture rather than events: a prompt appearing, a reel click.
func soft() -> void:
	if _device != null and _device.low_tick:
		_single(SOFT_GAP_MS, LOW_TICK, 0.5, 8, 40, true)
	else:
		_single(SOFT_GAP_MS, TICK, 0.3, 8, 40, true)


## A short dull thud, for walking into something or a flipper slamming up; rate-limited.
func bump() -> void:
	_single(BUMP_GAP_MS, THUD, 0.55, 30, 150, false, true)


## A short, low buzz for an engine or a rumble strip, [param level] 0..1. Rate-limited, never cuts
## off a hit or a win, and skipped by a vibrator that can't vary its amplitude.
func rumble(level: float) -> void:
	if not (level >= 0.05) or _device == null or not _device.amplitude_control:
		return
	if not _ready(RUMBLE_GAP_MS, true):
		return
	_one_shot(28, scale_amplitude(rumble_amplitude(level), strength))


func win() -> void:
	_sequence(WIN_WAVE_MS, WIN_WAVE_AMP, WIN_PRIMS, WIN_SCALES, WIN_DELAYS, 280)


func jackpot() -> void:
	_sequence(JACKPOT_WAVE_MS, JACKPOT_WAVE_AMP, JACKPOT_PRIMS, JACKPOT_SCALES, JACKPOT_DELAYS, 480)


## Whether an effect may start now: enabled, not silenced by [member strength], and no sooner than
## [param min_gap_ms] after the last. Background texture ([param ambient]) also waits for a pattern
## still playing and doesn't count against the events that follow it. A bump ([param own_gap]) is
## the player's own doing: only another bump is too soon for it, though it holds off a tick after it.
func _ready(min_gap_ms: int, ambient: bool = false, hold_ms: int = 0, own_gap: bool = false) -> bool:
	if not enabled or strength < MIN_STRENGTH or _device == null:
		return false
	var now: int = clock.call()
	if own_gap:
		if now - _last_bump_at < min_gap_ms:
			return false
		_last_bump_at = now
		_last_at = now
		return true
	if now - _last_at < min_gap_ms:
		return false
	if ambient:
		if now - _last_ambient_at < min_gap_ms or now < _busy_until:
			return false
		_last_ambient_at = now
	else:
		_last_at = now
		_busy_until = now + hold_ms
	return true


## One beat: a primitive where the phone has them, else a one-shot of [param ms] at [param amp].
func _single(min_gap_ms: int, primitive: int, scale: float, ms: int, amp: int, ambient: bool = false, own_gap: bool = false) -> void:
	if not _ready(min_gap_ms, ambient, 0, own_gap):
		return
	var composes := _device.low_tick if primitive == LOW_TICK else _device.primitives
	if composes:
		_device.composition(PackedInt32Array([_playable(primitive)]), PackedFloat32Array([clampf(scale * strength, 0.0, 1.0)]), PackedInt32Array([0]))
	else:
		_one_shot(ms, scale_amplitude(amp, strength))


## A pattern of several beats: primitives with delays between, else the waveform.
func _sequence(wave_ms: Array, wave_amp: Array, prims: Array, scales: Array, delays: Array, hold_ms: int) -> void:
	if not _ready(0, false, hold_ms):
		return
	if _device.primitives:
		var p := PackedInt32Array()
		var s := PackedFloat32Array()
		for i in prims.size():
			p.append(_playable(prims[i]))
			s.append(clampf(scales[i] * strength, 0.0, 1.0))
		_device.composition(p, s, PackedInt32Array(delays))
	elif _device.amplitude_control:
		var amps := PackedInt32Array()
		for a: int in wave_amp:
			amps.append(0 if a == 0 else scale_amplitude(a, strength))
		_device.waveform(PackedInt32Array(wave_ms), amps)
	else:
		_device.waveform(PackedInt32Array(wave_ms), PackedInt32Array())


func _one_shot(ms: int, amplitude: int) -> void:
	if _device.amplitude_control:
		_device.one_shot(ms, amplitude)
	else:
		_device.one_shot(scale_duration(ms, strength), -1)
