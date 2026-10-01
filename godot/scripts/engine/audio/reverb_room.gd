class_name ReverbRoom
extends RefCounted
## engine/audio/Reverb.kt Room: a room for the sound effects to sit in, and how Godot's own reverb
## is set to sound like it.
##
## build-13 ran its own Freeverb-style reverb per block. Godot's AudioEffectReverb is the same
## design (8 damped combs at Freeverb's tunings into 4 all-passes per ear, the right ear's delays a
## little longer), so each room is mapped onto its parameters from the 4.6.2 source
## (servers/audio/effects/reverb_filter.cpp):
##   comb feedback  = 0.7 + 0.28 * room_size (the same for every comb)
##   damping filter = keeps exp(-2 pi (damping / 2 + 0.5)^2 * 10 kHz / rate) of its last output
##   output         = tail * wet * 0.6 + dry
## build-13 sized every comb's feedback from the room's rt60 and scaled its input so a white-noise
## send came back at the room's wet level; the mapping below matches the mean comb's decay, the
## damping filter's coefficient and that return level. Early reflections have no Godot equivalent
## and are not reproduced (see docs/PORT_PARITY.md).

enum { HALL, GAME, TITLE, MUSIC }

const COUNT := 4
const NAMES := ["HALL", "GAME", "TITLE", "MUSIC"]

## Seconds for the tail to fall by 60 dB.
const RT60 := [2.3, 0.5, 3.2, 2.0]
## 0 for a bright tail, 1 for a dark one.
const DAMPING := [0.45, 0.55, 0.35, 0.5]
## How much of the tail is returned to the mix (1 = as loud as the signal that fed it).
const WET := [0.9, 0.25, 1.2, 0.8]
## How much of the first few reflections to add (not reproduced in Godot).
const EARLY := [0.5, 0.8, 0.3, 0.25]

## Time constant (seconds) of a room change: 95% of the way there in three times this.
const MORPH_TIME := 0.35
## The tail starts this long after the sound (a pure delay before the reverb).
const PRE_DELAY_MS := 16.0
## The tail is high-passed here so low sounds don't turn it to mud.
const HIGH_PASS_HZ := 140.0

## build-13's damping filter coefficient: from bright (0.05) to dark (0.55).
const DAMP_MIN := 0.05
const DAMP_RANGE := 0.5

## Godot's comb tunings (seconds), and their mean.
const GODOT_COMBS := [0.025306122448979593, 0.026938775510204082, 0.028956916099773241, 0.03074829931972789,
	0.032244897959183672, 0.03380952380952381, 0.035306122448979592, 0.036666666666666667]
const GODOT_COMB_MEAN := 0.03124681122448979
const GODOT_ROOM_OFFSET := 0.7
const GODOT_ROOM_SCALE := 0.28
const GODOT_WET_SCALE := 0.6
## Godot's high-pass parameter is a fraction of 6 kHz.
const GODOT_HPF_SCALE := 6000.0


## The comb feedback that makes Godot's mean comb fall 60 dB in [param rt60] seconds.
static func feedback_for(rt60: float) -> float:
	return pow(10.0, -3.0 * GODOT_COMB_MEAN / maxf(rt60, 0.01))


## AudioEffectReverb.room_size for a tail of [param rt60] seconds (clamped to Godot's range: the
## shortest tail it can make is about 0.6 s).
static func room_size_for(rt60: float) -> float:
	return clampf((feedback_for(rt60) - GODOT_ROOM_OFFSET) / GODOT_ROOM_SCALE, 0.0, 1.0)


## The comb feedback Godot will actually use for [param rt60].
static func godot_feedback(rt60: float) -> float:
	return GODOT_ROOM_OFFSET + GODOT_ROOM_SCALE * room_size_for(rt60)


## AudioEffectReverb.damping matching build-13's damping filter at [param rate] Hz.
static func damping_param(damping: float, rate: float = 48000.0) -> float:
	var keep := DAMP_MIN + DAMP_RANGE * damping
	var aux := -log(keep) * rate / (TAU * 10000.0)
	return clampf((sqrt(maxf(aux, 0.0)) - 0.5) * 2.0, 0.0, 1.0)


## AudioEffectReverb.wet giving the room's return level: build-13 normalised its combs' input so a
## noise send came back at about [param wet]; Godot's eight combs return sqrt(8 / (1 - g^2)) of it
## before its 0.6 output scale.
static func wet_param(wet: float, rt60: float) -> float:
	var g := godot_feedback(rt60)
	return wet / (GODOT_WET_SCALE * sqrt(8.0 / (1.0 - g * g)))


## AudioEffectReverb.hipass for build-13's 140 Hz high-pass.
static func hipass_param() -> float:
	return HIGH_PASS_HZ / GODOT_HPF_SCALE
