class_name HallAmbience
extends RefCounted
## engine/audio/HallAmbience.kt: the arcade's background sound, in stereo: a mains hum, a crowd
## murmur that swells with how busy the hall is, machine bleeps that come from the cabinets
## themselves and the cafe's steam wand and clinking cups.
##
## build-13 synthesized the hum and murmur sample by sample on its mixer thread. Here they are
## rendered once into seamless loops (the same oscillators and noise filters; see
## [method render_hum_loop] and [method render_babble_loop]) that the mixer plays at
## [member hum_gain] and [member murmur_gain]; [method tick] does everything else build-13 did
## once a block: the level and crowd easing, and the bleeps and cafe sounds, started through a
## voice sink (the mixer) with the same random sequence.

## How fast the overall loudness eases to its target, per 10 ms block (about half a second).
const LEVEL_EASE := 0.02
## ...and the crowd's busyness (a slower drift, so a passing kid doesn't pump it).
const CROWD_EASE := 0.03
## build-13's mixer block: 480 frames at 48 kHz.
const BLOCK_SECONDS := 0.01

## Mains hum level: three partials of 55 Hz, low under everything.
const HUM_LEVEL := 0.05
## Crowd murmur level at the quietest and busiest end of the hall's range.
const MURMUR_QUIET := 0.4
const MURMUR_BUSY := 0.9
const MURMUR_LEVEL := 0.45

## Machine bleeps come this often (seconds); only nearby cabinets are audible, so it is often.
const BLEEP_MIN := 0.25
const BLEEP_MAX := 0.9
## Bleep loudness at a source close by, before the ambience volume.
const BLEEP_LEVEL_MIN := 0.05
const BLEEP_LEVEL_MAX := 0.11
## No more than this many ambience voices at once, so a busy hall stays a bed and not a din.
const MAX_AMBIENT_VOICES := 5
## How many times a bleep tries for a cabinet within earshot before giving up this round.
const PICK_TRIES := 4
## Reverb send of a bleep, as a share of its own level: machines are a room away.
const BLEEP_SEND := 0.5
## Without positions (the title, before the hall reports in) a bleep comes from a random spot.
const FALLBACK_PAN := 0.8
const FALLBACK_LEVEL_MIN := 0.35
const FALLBACK_LEVEL_MAX := 0.8

## The cafe: a steam wand hiss every few seconds and a cup clink now and then.
const STEAM_MIN := 8.0
const STEAM_MAX := 17.0
const CLINK_MIN := 2.4
const CLINK_MAX := 6.5
const STEAM_LEVEL := 0.16
const CLINK_LEVEL := 0.1

## The hum loop's length: a whole number of cycles of 55, 110 and 165.3 Hz.
const HUM_LOOP_SECONDS := 20.0
## The murmur's slow swell, and how many of its cycles the murmur loop holds.
const SWELL_HZ := 0.11
const BABBLE_LOOP_CYCLES := 2
## The murmur loop's end is crossfaded into its start over this long, so it never clicks.
const LOOP_CROSSFADE := 0.25

## Loudness target (0 = silent); the hall sets it per screen.
var target := 0.0
## How busy the hall is around the listener, 0..1: more crowd murmur.
var crowd := 0.5

## Where the levels have eased to.
var ambient := 0.0
var crowd_now := 0.5
## The gains the mixer plays the hum and murmur loops at (0 when silent).
var hum_gain := 0.0
var murmur_gain := 0.0

var _xs := PackedFloat32Array()
var _zs := PackedFloat32Array()
var _kinds := PackedInt32Array()
var _count := 0
var _cafe_at := false
var _cafe_x := 0.0
var _cafe_z := 0.0

var _rng := KRandom.new(99)
var _placement := Placement.new()
var _bleep_timer := 1.0
var _steam_timer := 4.0
var _clink_timer := 2.0


## Publishes the machines' positions: [param count] cabinets at ([param xs], [param zs]) with
## palette [param kinds] (see [Attract]). Copies the arrays.
func set_sources(xs: PackedFloat32Array, zs: PackedFloat32Array, kinds: PackedInt32Array, count: int) -> void:
	var n := maxi(0, mini(count, mini(xs.size(), mini(zs.size(), kinds.size()))))
	_xs = xs.slice(0, n)
	_zs = zs.slice(0, n)
	_kinds = kinds.slice(0, n)
	_count = n


## Publishes where the cafe's counter is.
func set_cafe(x: float, z: float) -> void:
	_cafe_x = x
	_cafe_z = z
	_cafe_at = true


## Advances [param seconds]: eases the levels, sets [member hum_gain] and [member murmur_gain], and
## starts any bleeps that fall due through [param sink] (an object with build-13's VoiceSink methods
## start_voice and ambient_voices). [param volume] is the player's ambience setting, ([param lx],
## [param lz], [param yaw]) the listener.
func tick(seconds: float, sink: Object, volume: float, lx: float, lz: float, yaw: float) -> void:
	var blocks := seconds / BLOCK_SECONDS
	ambient += (target - ambient) * (1.0 - pow(1.0 - LEVEL_EASE, blocks))
	crowd_now += (crowd - crowd_now) * (1.0 - pow(1.0 - CROWD_EASE, blocks))
	if volume <= 0.0 or ambient < 0.001:
		hum_gain = 0.0
		murmur_gain = 0.0
		return
	_bleep_timer -= seconds
	if _bleep_timer <= 0.0:
		_bleep_timer = _rng.range_f(BLEEP_MIN, BLEEP_MAX)
		if ambient > 0.05:
			_bleep(sink, volume, lx, lz, yaw)
	_steam_timer -= seconds
	if _steam_timer <= 0.0:
		_steam_timer = _rng.range_f(STEAM_MIN, STEAM_MAX)
		_cafe_sound(sink, Sfx.STEAM, STEAM_LEVEL, volume, lx, lz, yaw, _rng.range_f(0.9, 1.1))
	_clink_timer -= seconds
	if _clink_timer <= 0.0:
		_clink_timer = _rng.range_f(CLINK_MIN, CLINK_MAX)
		_cafe_sound(sink, Sfx.CLINK, CLINK_LEVEL, volume, lx, lz, yaw, _rng.range_f(0.8, 1.25))
	hum_gain = ambient * volume * HUM_LEVEL
	murmur_gain = ambient * volume * MURMUR_LEVEL * (MURMUR_QUIET + (MURMUR_BUSY - MURMUR_QUIET) * crowd_now)


## One machine bleep, from a cabinet within earshot (or from nowhere in particular before the hall reports).
func _bleep(sink: Object, volume: float, lx: float, lz: float, yaw: float) -> void:
	if sink.ambient_voices() >= MAX_AMBIENT_VOICES:
		return
	var level := _rng.range_f(BLEEP_LEVEL_MIN, BLEEP_LEVEL_MAX) * ambient * volume
	var p := _placement
	var kind: int
	if _count == 0:
		Spatial.pan_gains(_rng.range_f(-FALLBACK_PAN, FALLBACK_PAN), p)
		var k := _rng.range_f(FALLBACK_LEVEL_MIN, FALLBACK_LEVEL_MAX) * Spatial.CENTRE_MAKEUP
		p.left *= k
		p.right *= k
		p.send = 0.6
		kind = Attract.GENERIC
	else:
		var picked := -1
		for t in PICK_TRIES:
			var i := _rng.next_int_until(_count)
			Spatial.place(lx, lz, yaw, _xs[i], _zs[i], p)
			if not p.is_silent():
				picked = i
				break
		if picked < 0:
			return
		kind = _kinds[picked]
	var pal := Attract.palette(kind)
	var sfx := pal.sfx[_rng.next_int_until(pal.sfx.size())]
	var v := level * pal.level
	sink.start_voice(sfx, p.left * v, p.right * v, BLEEP_SEND * p.send * v, _rng.range_f(pal.pitch_lo, pal.pitch_hi), AudioPriority.AMBIENT)


## A sound from the cafe counter, if the cafe is known and the listener is within earshot of it.
func _cafe_sound(sink: Object, sfx: int, level: float, volume: float, lx: float, lz: float, yaw: float, pitch: float) -> void:
	if not _cafe_at or sink.ambient_voices() >= MAX_AMBIENT_VOICES:
		return
	var p := _placement
	Spatial.place(lx, lz, yaw, _cafe_x, _cafe_z, p)
	if p.is_silent():
		return
	var v := level * ambient * volume
	sink.start_voice(sfx, p.left * v, p.right * v, BLEEP_SEND * p.send * v, pitch, AudioPriority.AMBIENT)


# ------------------------------------------------------------------ the loops

## The mains hum, mono, [constant HUM_LOOP_SECONDS] long: 55 Hz with its second and third partials
## (the third a little sharp, as in build-13), unscaled (the mixer applies [member hum_gain]).
static func render_hum_loop(rate: int) -> PackedFloat32Array:
	var n := int(HUM_LOOP_SECONDS * rate)
	var out := PackedFloat32Array()
	out.resize(n)
	var p1 := 0.0
	var p2 := 0.0
	var p3 := 0.0
	var i1 := 55.0 / rate
	var i2 := 110.0 / rate
	var i3 := 165.3 / rate
	for i in n:
		p1 += i1
		if p1 >= 1.0:
			p1 -= 1.0
		p2 += i2
		if p2 >= 1.0:
			p2 -= 1.0
		p3 += i3
		if p3 >= 1.0:
			p3 -= 1.0
		out[i] = sin(TAU * p1) * 0.7 + sin(TAU * p2) * 0.4 + sin(TAU * p3) * 0.15
	return out


## Frames in the murmur loop.
static func babble_loop_frames(rate: int) -> int:
	return roundi(BABBLE_LOOP_CYCLES * rate / SWELL_HZ)


## The crowd murmur, interleaved stereo (one babble per ear, seeded as build-13's), with the slow
## swell applied, as a seamless loop of [method babble_loop_frames] frames.
static func render_babble_loop(rate: int) -> PackedFloat32Array:
	var n := babble_loop_frames(rate)
	var x := int(LOOP_CROSSFADE * rate)
	var total := n + x
	var other := Thread.new()
	other.start(_babble.bind(5678, rate, total))
	var left := _babble(1234, rate, total)
	var right: PackedFloat32Array = other.wait_to_finish()
	var lfo := 0.0
	var lfo_inc := SWELL_HZ / rate
	for i in total:
		lfo += lfo_inc
		if lfo >= 1.0:
			lfo -= 1.0
		var swell := 0.65 + 0.35 * sin(TAU * lfo)
		left[i] *= swell
		right[i] *= swell
	var out := PackedFloat32Array()
	out.resize(n * 2)
	for i in n:
		var l := left[i]
		var r := right[i]
		if i < x:
			# The loop's start fades in over what follows its end (equal power: the two are uncorrelated).
			var t := float(i) / x
			var a := sin(t * PI / 2.0)
			var b := cos(t * PI / 2.0)
			l = l * a + left[n + i] * b
			r = r * a + right[n + i] * b
		out[i * 2] = l
		out[i * 2 + 1] = r
	return out


## One ear's crowd murmur: a low rumble plus a voice-band babble that rises and falls like speech.
## build-13 drew its noise from kotlin.random.Random(seed); this draws the same uniform noise from
## Godot's generator with the same seed, a few times faster than [KRandom] in GDScript (the murmur
## is noise either way, and a loop is an excerpt of it).
static func _babble(seed: int, rate: int, n: int) -> PackedFloat32Array:
	var rng := RandomNumberGenerator.new()
	rng.seed = seed
	var out := PackedFloat32Array()
	out.resize(n)
	var sr := float(rate)
	var brown := 0.0
	var rumble := 0.0
	var hi := 0.0
	var lo := 0.0
	var mod := 0.6
	var mod_target := 0.6
	var mod_left := 0
	for i in n:
		var w := rng.randf() - 0.5
		brown = (brown + w * 0.05) * 0.996
		rumble += (brown - rumble) * 0.08
		hi += (w - hi) * 0.16
		lo += (w - lo) * 0.035
		mod_left -= 1
		if mod_left <= 0:
			# A new "syllable" every 90 to 250 ms.
			mod_left = int(sr * rng.randf_range(0.09, 0.25))
			mod_target = rng.randf_range(0.3, 1.0)
		mod += (mod_target - mod) * 0.0006
		out[i] = rumble * 0.55 + (hi - lo) * 1.5 * mod
	return out
