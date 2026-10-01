class_name GodotReverbSim
extends RefCounted
## Godot 4.6.2's reverb (servers/audio/effects/reverb_filter.cpp, Reverb::process), one channel,
## run offline so the tests can check the room mappings against what Godot will actually do.

const ALLPASS_TUNINGS := [0.0051020408163265302, 0.007732426303854875, 0.01, 0.012607709750566893]
const ALLPASS_FEEDBACK := 0.7
## The right channel's extra comb and all-pass length (AudioEffectReverbInstance).
const RIGHT_SPREAD := 0.000521

var _combs: Array[PackedFloat32Array] = []
var _comb_pos := PackedInt32Array()
var _comb_damp_h := PackedFloat32Array()
var _aps: Array[PackedFloat32Array] = []
var _ap_pos := PackedInt32Array()
var _feedback := 0.0
var _damp := 0.0
var _wet := 0.0
var _hp_a1 := 0.0
var _hp_a2 := 0.0
var _hp_b1 := 0.0
var _hp_on := false
var _h1 := 0.0
var _h2 := 0.0


## A reverb at [param rate] Hz set as AudioEffectReverb's room_size, damping, wet and hipass would
## set it (dry 0, spread 1, no pre-delay feedback); [param right] gives the right channel's lengths.
func _init(rate: int, room_size: float, damping: float, wet: float, hipass: float, right: bool = false) -> void:
	var extra := roundi(RIGHT_SPREAD * rate) if right else 0
	for t: float in ReverbRoom.GODOT_COMBS:
		var b := PackedFloat32Array()
		b.resize(roundi(t * rate) + extra)
		_combs.append(b)
	_comb_pos.resize(_combs.size())
	_comb_damp_h.resize(_combs.size())
	for t: float in ALLPASS_TUNINGS:
		var b := PackedFloat32Array()
		b.resize(roundi(t * rate) + extra)
		_aps.append(b)
	_ap_pos.resize(_aps.size())
	_feedback = clampf(0.7 + room_size * 0.28, 0.7, 0.98)
	var aux := damping / 2.0 + 0.5
	aux *= aux
	_damp = exp(-TAU * aux * 10000.0 / rate)
	_wet = wet
	if hipass > 0.0:
		var hpaux := exp(-TAU * hipass * 6000.0 / rate)
		_hp_a1 = (1.0 + hpaux) / 2.0
		_hp_a2 = -(1.0 + hpaux) / 2.0
		_hp_b1 = hpaux
		_hp_on = true


## Runs [param src] through the reverb and returns the wet output.
func process(src: PackedFloat32Array) -> PackedFloat32Array:
	var n := src.size()
	var input := src.duplicate()
	if _hp_on:
		for i in n:
			var x := input[i]
			var y := x * _hp_a1 + _h1 * _hp_a2 + _h2 * _hp_b1
			input[i] = y
			_h2 = y
			_h1 = x
	var dst := PackedFloat32Array()
	dst.resize(n)
	var keep := 1.0 - _damp
	for c in _combs.size():
		var buf := _combs[c]
		var size := buf.size()
		var pos := _comb_pos[c]
		var h := _comb_damp_h[c]
		for j in n:
			if pos >= size:
				pos = 0
			var out := buf[pos] * _feedback
			if absf(out) < 1.17549435e-38:
				out = 0.0
			out = out * keep + h * _damp
			h = out
			buf[pos] = input[j] + out
			dst[j] += out
			pos += 1
		_combs[c] = buf
		_comb_pos[c] = pos
		_comb_damp_h[c] = h
	for a in _aps.size():
		var buf := _aps[a]
		var size := buf.size()
		var pos := _ap_pos[a]
		for j in n:
			if pos >= size:
				pos = 0
			var aux := buf[pos]
			var w := ALLPASS_FEEDBACK * aux + dst[j]
			if absf(w) < 1.17549435e-38:
				w = 0.0
			buf[pos] = w
			dst[j] = aux - ALLPASS_FEEDBACK * w
			pos += 1
		_aps[a] = buf
		_ap_pos[a] = pos
	for i in n:
		dst[i] = dst[i] * _wet * ReverbRoom.GODOT_WET_SCALE
	return dst


## A reverb set up for build-13's [param room] the way the mixer sets AudioEffectReverb.
static func for_room(room: int, rate: int, right: bool = false) -> GodotReverbSim:
	var rt: float = ReverbRoom.RT60[room]
	return GodotReverbSim.new(rate, ReverbRoom.room_size_for(rt), ReverbRoom.damping_param(ReverbRoom.DAMPING[room], rate),
		ReverbRoom.wet_param(ReverbRoom.WET[room], rt), ReverbRoom.hipass_param(), right)
