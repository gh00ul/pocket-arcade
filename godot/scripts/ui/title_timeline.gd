class_name TitleTimeline
extends RefCounted
## ui/TitleTimeline.kt: the title screen's clock: when each part of the sign lights, how the tap
## prompt pulses, and how everything leaves when the player taps. Pure functions of time, so they
## can be tested without a screen; `calm` (the reduce-motion setting) swaps every flicker, sweep and
## bob for a slow fade. The file's other objects are [TitleTimeline.DustField] (the showroom's dust)
## and [TitleTimeline.TitleCopy] (the welcome line); its `smoothstep` is [method smoothstep].

## Seconds after the title appears that POCKET, then ARCADE, begin to light.
const POCKET_START := 0.5
const ARCADE_START := 1.35

## Each letter lights this much after the one before it, and takes [constant IGNITE_SECONDS] to settle.
const LETTER_STAGGER := 0.09
const IGNITE_SECONDS := 0.75

## How bright an unlit neon tube looks: a faint ghost of the letter, never nothing.
const UNLIT := 0.10

## How many times a second an igniting tube can change its mind.
const FLICKER_RATE := 22.0

## Under reduce motion the sign fades in together over this long.
const CALM_FADE_SECONDS := 1.2

## Seconds after the title appears before the tagline starts to fade in, and how long it takes.
const TAGLINE_START := ARCADE_START + 1.0
const TAGLINE_FADE := 0.9

## The glow behind the sign breathes: its strength multiplier over time (a gentle 1.0 down to about 0.88).
const BREATH_PERIOD := 4.6
const BREATH_DEPTH := 0.12

## A light sweep crosses the sign for [constant SWEEP_SECONDS] every [constant SWEEP_PERIOD] seconds,
## from [constant SWEEP_START].
const SWEEP_START := 3.6
const SWEEP_PERIOD := 6.5
const SWEEP_SECONDS := 1.15

## Once in a while one tube of the settled sign buzzes: every [constant BUZZ_PERIOD] seconds, for
## [constant BUZZ_SECONDS].
const BUZZ_PERIOD := 9.0
const BUZZ_SECONDS := 0.14

## TAP TO START waits for the sign, fades in over [constant PROMPT_FADE], then pulses.
const PROMPT_START := 2.8
const PROMPT_FADE := 0.8
const PULSE_HZ := 0.75


## 0 below [param a], 1 above [param b], and an S-curve between (ui/TitleTimeline.kt `smoothstep`).
static func smoothstep(a: float, b: float, x: float) -> float:
	var t := clampf((x - a) / (b - a), 0.0, 1.0)
	return t * t * (3.0 - 2.0 * t)


## How lit letter [param index] of a word that starts at [param word_start] is at time [param t]: a
## ghost, then a few stuttering flashes as the tube catches, then steady at 1 for good. Calm is a
## smooth fade.
static func letter_on(t: float, word_start: float, index: int, calm: bool) -> float:
	if calm:
		return UNLIT + (1.0 - UNLIT) * smoothstep(0.0, CALM_FADE_SECONDS, t - word_start)
	var local := t - word_start - index * LETTER_STAGGER
	if local <= 0.0:
		return UNLIT
	# A microsecond of slack: build-13 did this sum in 32-bit floats, where the settled moment
	# (start + stagger + ignite) lands on IGNITE_SECONDS; in 64-bit it can land a hair under.
	if local >= IGNITE_SECONDS - 1e-6:
		return 1.0
	var p := local / IGNITE_SECONDS
	var ramp := UNLIT + (1.0 - UNLIT) * p * p
	# A hash of which flicker slot we are in decides on or off; the stutter dies away as p -> 1.
	var slot := floori(local * FLICKER_RATE)
	var flash := 1.0 if MathUtil.hash01(index, slot, 7) > 0.42 else 0.18
	var settle := p * p * p
	return ramp * (flash + (1.0 - flash) * settle)


static func tagline_alpha(t: float) -> float:
	return smoothstep(TAGLINE_START, TAGLINE_START + TAGLINE_FADE, t)


static func breath(t: float, calm: bool) -> float:
	var depth := BREATH_DEPTH * 0.5 if calm else BREATH_DEPTH
	return 1.0 - depth * (0.5 + 0.5 * sin(TAU * t / BREATH_PERIOD))


## Where the sweep's centre is across the sign (0 = its left edge, 1 = its right), or -1 when there is none.
static func sweep(t: float, calm: bool) -> float:
	if calm or t < SWEEP_START:
		return -1.0
	var local := fmod(t - SWEEP_START, SWEEP_PERIOD)
	if local > SWEEP_SECONDS:
		return -1.0
	return smoothstep(0.0, 1.0, local / SWEEP_SECONDS)


## The multiplier (1 normally, a dip while buzzing) for letter [param index] of a [param count]-letter word.
static func buzz(t: float, index: int, count: int, word: int, calm: bool) -> float:
	if calm or t < SWEEP_START or count <= 0:
		return 1.0
	var cycle := floori((t + word * 3.1) / BUZZ_PERIOD)
	var local := (t + word * 3.1) - cycle * BUZZ_PERIOD
	if local > BUZZ_SECONDS:
		return 1.0
	var victim := clampi(int(MathUtil.hash01(cycle, word, 11) * count), 0, count - 1)
	return 0.55 if index == victim else 1.0


## How opaque the prompt is at [param t].
static func prompt_alpha(t: float, calm: bool) -> float:
	var fade := smoothstep(PROMPT_START, PROMPT_START + PROMPT_FADE, t)
	if fade <= 0.0:
		return 0.0
	var pulse := 0.5 + 0.5 * sin(TAU * t * (PULSE_HZ * 0.6 if calm else PULSE_HZ))
	var depth := 0.25 if calm else 0.42
	return fade * (1.0 - depth + depth * pulse)


## How much bigger than normal the prompt is at [param t] (1 when calm): it swells with each pulse.
static func prompt_scale(t: float, calm: bool) -> float:
	if calm or t < PROMPT_START:
		return 1.0
	return 1.0 + 0.04 * (0.5 + 0.5 * sin(TAU * t * PULSE_HZ))


# ---------------------------------------------------------------- leaving

## The sign's opacity as the exit progresses (0..1): gone by 60%.
static func logo_exit_alpha(exit_p: float) -> float:
	return 1.0 - smoothstep(0.05, 0.6, exit_p)


## How far the sign has risen by the exit's progress, as a share of the screen height.
static func logo_exit_lift(exit_p: float) -> float:
	return -0.05 * smoothstep(0.0, 1.0, exit_p)


## How much the sign has grown by the exit's progress.
static func logo_exit_scale(exit_p: float) -> float:
	return 1.0 + 0.10 * smoothstep(0.0, 1.0, exit_p)


## The small print (prompt, save line, version) is gone by a third of the way.
static func ui_exit_alpha(exit_p: float) -> float:
	return 1.0 - smoothstep(0.0, 0.32, exit_p)


## ui/TitleTimeline.kt DustField: the dust motes hanging in the showroom's air. Each has a fixed
## depth, so the nearer ones drift across the screen further as the camera moves (parallax) and
## rise a little faster; on the way out they stream outward past the lens. All positions are shares
## of the screen (0..1) computed from the mote's index alone: nothing is stored, nothing allocated.
class DustField:
	const COUNT := 46

	## How many screen widths a near mote slides per world unit the camera moves along the row.
	const PARALLAX := 0.0011

	## Screen-height shares per second a near mote rises.
	const RISE := 0.030

	## Depth 0.3 (far) … 1 (near).
	static func depth(i: int) -> float:
		return 0.3 + 0.7 * MathUtil.hash01(i, 1, 21)

	static func _wrap01(v: float) -> float:
		return v - floorf(v)

	## Horizontal position (0..1) at time [param t] for a camera eye at world x [param cam_x]; a calm
	## field never moves.
	static func x(i: int, t: float, cam_x: float, calm: bool) -> float:
		var d := depth(i)
		if calm:
			return MathUtil.hash01(i, 2, 21)
		var sway := sin(t * 0.31 + i) * 0.012 * d
		return _wrap01(MathUtil.hash01(i, 2, 21) + sway - cam_x * PARALLAX * d)

	## Vertical position (0..1): rising slowly, wrapping from the top back to the bottom.
	static func y(i: int, t: float, calm: bool) -> float:
		var d := depth(i)
		if calm:
			return MathUtil.hash01(i, 3, 21)
		return _wrap01(MathUtil.hash01(i, 3, 21) - t * RISE * d)

	## Peak opacity: brighter the nearer, and a slow twinkle; a calm field is a little fainter and still.
	static func alpha(i: int, t: float, calm: bool) -> float:
		var d := depth(i)
		var base := 0.10 + 0.26 * d
		if calm:
			return base * 0.6
		return base * (0.65 + 0.35 * sin(t * (0.6 + MathUtil.hash01(i, 4, 21)) + i * 1.7))

	## Radius in units of the screen width: nearer motes are bigger.
	static func radius(i: int) -> float:
		return (0.0009 + 0.0026 * depth(i)) * (0.7 + 0.6 * MathUtil.hash01(i, 5, 21))

	## Pushes a position outward from the screen centre as the exit progresses, nearer motes faster,
	## so leaving the title feels like flying through the dust. [param v] is a 0..1 position,
	## [param exit_p] 0..1.
	static func stream(v: float, i: int, exit_p: float) -> float:
		var e := clampf(exit_p, 0.0, 1.0)
		return 0.5 + (v - 0.5) * (1.0 + 2.6 * e * e * depth(i))


## ui/TitleTimeline.kt TitleCopy: the small welcome line under the title's save summary: a word
## about where the player left off.
class TitleCopy:
	## A short line for [param save] (empty until it has loaded): a greeting for a brand-new player,
	## otherwise a welcome back with the most useful thing to say, in order: tickets that can buy
	## something, how many plushies are found, or just the greeting.
	static func welcome(save: SaveState) -> String:
		if not save.loaded:
			return ""
		if save.total_plays == 0 and save.tickets == 0:
			return "WELCOME, PLAYER ONE!"
		var can_buy := false
		for item in Catalog.all():
			if item.price >= 1 and item.price <= save.tickets and not save.owns(item.id):
				can_buy = true
				break
		if can_buy:
			return "WELCOME BACK!  %d TICKETS TO SPEND" % save.tickets
		var found := 0
		for p in Catalog.plushies():
			if int(save.collection.get(p.id, 0)) > 0:
				found += 1
		var total := Catalog.plushies().size()
		if found >= total:
			return "WELCOME BACK!  EVERY PLUSH FOUND"
		if found > 0:
			return "WELCOME BACK!  %d OF %d PLUSHIES FOUND" % [found, total]
		return "WELCOME BACK!"
