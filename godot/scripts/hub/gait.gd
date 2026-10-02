class_name Gait
extends RefCounted
## hub/Gait.kt: the maths of walking and running: how far a stride swings, how far the cycle
## advances per unit of ground covered (so a planted foot stays put instead of skating), and where
## the feet are. Pure functions and constants; [FigureAnim] does the stateful part.
##
## Conventions: [member FigureAnim.phase] is the stride cycle in radians, and a foot lands (both
## legs at their furthest reach, the body at its lowest) at every multiple of π; the legs pass
## under the body at π/2 + kπ. A leg's pitch is negative when the foot is forward.

## Hip to sole (figure units): what the body drops by as a straight leg leans out from vertical.
const LEG_LEN := 14.0

## The kids' fastest walk (world units a second); above it the stride opens out into a run.
const WALK_TOP := 46.0

## Speeds (world units a second) where a run starts to take over from a walk and where it is complete.
const RUN_FROM := 54.0
const RUN_FULL := 78.0

## Leg swing amplitude (radians each way): a shuffle, a full walking stride and a run.
const AMP_MIN := 0.24
const AMP_WALK := 0.60
const AMP_RUN := 0.76

## How square the leg's back-and-forth is: 0 is a sine, 1 a triangle wave. A triangle moves the
## planted foot back at a constant speed (so it doesn't slide); a touch less keeps the ends soft.
const SHAPE_K := 0.92

## How high a swinging foot clears the ground (figure units) in a full walking stride and in a run.
const LIFT_WALK := 1.1
const LIFT_RUN := 2.0

## How much of the body's geometric dip (straight legs leaning out lower the hips) is kept in a
## walk, and how much less of it in a run (where the feet leave the ground at the ends of the
## stride). All of it would make a compass walker; none leaves the feet floating in a walk.
const CONTACT := 0.75
const CONTACT_RUN := 0.5

## Extra rise at the top of a run stride (the flight), figure units.
const RUN_HOP := 1.6

static var _asin_k := asin(SHAPE_K)


## 0 walking .. 1 running, for a ground speed of [param speed].
static func run_blend(speed: float) -> float:
	return AnimMath.smooth((speed - RUN_FROM) / (RUN_FULL - RUN_FROM))


## Leg swing amplitude (radians) at [param speed]: grows through the walk, then on into the run.
static func amplitude(speed: float) -> float:
	var walk := AMP_MIN + (AMP_WALK - AMP_MIN) * AnimMath.smooth(speed / WALK_TOP)
	var r := run_blend(speed)
	return walk + (AMP_RUN - walk) * r


## How high a swinging foot clears the ground at swing amplitude [param amp] and run blend
## [param run]: a shuffle barely lifts it.
static func lift(amp: float, run: float) -> float:
	var walk := LIFT_WALK * (0.4 + 0.6 * clampf((amp - AMP_MIN) / (AMP_WALK - AMP_MIN), 0.0, 1.0))
	return walk + (LIFT_RUN - walk) * run


## Ground covered by one step (heel strike to the other heel strike) at swing amplitude [param amp].
static func stride(amp: float) -> float:
	return 2.0 * LEG_LEN * sin(amp)


## How far the cycle advances (radians) for [param dist] of ground at swing amplitude [param amp]: π a stride.
static func phase_for(dist: float, amp: float) -> float:
	return dist * PI / stride(amp)


## The leg swing's shape: −1..1 for [param c], the sine of the leg's swing angle (which is cos of
## the cycle phase). Nearly linear through the middle, rounding off at the ends.
static func shape(c: float) -> float:
	return asin(clampf(SHAPE_K * c, -SHAPE_K, SHAPE_K)) / _asin_k


## The cycle phase nearest [param phase] at which the legs stand under the body (π/2 + kπ).
static func rest_phase(phase: float) -> float:
	# Java's Math.round: half up.
	return MathUtil.round_to_int((phase - PI / 2.0) / PI) * PI + PI / 2.0


## Leg [param side]'s (−1 left, +1 right) pitch at cycle [param phase] and swing amplitude [param amp].
static func leg_pitch(side: float, phase: float, amp: float) -> float:
	return side * amp * shape(cos(phase))


## How high leg [param side]'s foot is lifted at [param phase]: a half sine through its swing, on
## the ground while it pushes back.
static func leg_lift(side: float, phase: float, p_lift: float) -> float:
	return p_lift * maxf(0.0, side * sin(phase))


## Height of a foot above where a standing one would be: the lean of a straight leg plus its [param p_lift].
static func foot_height(pitch: float, p_lift: float) -> float:
	return LEG_LEN * (1.0 - cos(pitch)) + p_lift


## How far forward of the hip a foot is at [param pitch].
static func foot_forward(pitch: float) -> float:
	return -LEG_LEN * sin(pitch)
