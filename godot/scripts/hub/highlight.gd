class_name Highlight
extends RefCounted
## hub/Highlight.kt: the glow that marks the thing whose ▶ PLAY prompt is up: a machine, the token
## kiosk or the prize counter. Its level eases 0 → 1 while it is the world's active spot and back
## down when the kid walks off, and the emissive parts of its model (marquee, screen, T-molding,
## coin slots) breathe a little brighter meanwhile.
##
## Everything tweakable is a named constant. The boost only ever multiplies what already glows, and
## the peak is kept small so a marquee that sits just under the bloom threshold doesn't flare white;
## raise [constant EMISSIVE_BOOST] in small steps and look at the pale marquees.

## Seconds to fade in, or out again.
const FADE_SECONDS := 0.25
## Extra emissive (on top of 1) at the top of the pulse.
const EMISSIVE_BOOST := 0.2
## How much of [constant EMISSIVE_BOOST] the pulse gives back at its low point (0 = steady glow).
const PULSE_DEPTH := 0.4
## Pulse speed, radians per second (about one breath a second).
const PULSE_RATE := 6.0
## Extra alpha on the additive light pool on the floor in front of a highlighted cabinet (its
## everyday alpha is about 0.3; the pool sits close to the bloom threshold on pale glows).
const POOL_ALPHA := 0.08
## Extra emissive on the marquee bulbs that are between flashes (0.7 normally). A white-trimmed
## cabinet's idle bulbs reach the bloom threshold at about 1.0, so stay a little under that.
const BULB_BOOST := 0.25
## Extra alpha on the halo of a bulb that is flashing.
const BULB_HALO := 0.2


## Smoothstep: starts and ends gently, so the fade doesn't jump.
static func ease(level: float) -> float:
	var x := clampf(level, 0.0, 1.0)
	return x * x * (3.0 - 2.0 * x)


## Moves [param level] a step of [param dt] seconds towards fully on or fully off.
static func step(level: float, on: bool, dt: float) -> float:
	var d := dt / FADE_SECONDS
	return minf(1.0, level + d) if on else maxf(0.0, level - d)


## The emissive multiplier for a model at fade [param level] and hall time [param t]: exactly 1
## when off.
static func boost(level: float, t: float) -> float:
	var e := Highlight.ease(level)
	if e <= 0.0:
		return 1.0
	var swing := 0.5 + 0.5 * sin(t * PULSE_RATE)
	return 1.0 + EMISSIVE_BOOST * e * (1.0 - PULSE_DEPTH * (1.0 - swing))


## Whether [param spot] is the one that [param p] is for: a machine's own copy in its bank, the kiosk
## (its token and ticket machines), the prize counter's desk. Null (nobody is at anything) is no.
static func is_spot_of(spot: Spot, p: Prop) -> bool:
	if spot == null:
		return false
	if p.kind == PropKind.MACHINE:
		return spot.type == SpotType.MACHINE and spot.machine == p.machine \
			and absf(spot.area.center_x - p.center_x) < 1.0 and absf(spot.area.top - p.z1) < 1.0
	if p.kind == PropKind.TOKENS or p.kind == PropKind.CHANGE:
		return spot.type == SpotType.TOKENS
	if p.kind == PropKind.COUNTER:
		return spot.type == SpotType.PRIZES
	return false
