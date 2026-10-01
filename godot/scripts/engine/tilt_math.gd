class_name TiltMath
extends RefCounted
## engine/TiltSteer.kt TiltMath: the sums behind tilt steering, apart from the sensor.

## Lean this far off the neutral (degrees) before anything happens: a hand is never still.
const DEAD_DEG := 2.5
## Lean this far off the neutral (degrees) for full lock.
const FULL_DEG := 20.0


## How far the device leans to the right, in radians: the angle of "up" out of the device's own
## front-back plane, given as ([param ux], [param uy], [param uz]) in device coordinates (x right,
## y up the screen, z out of it). Right edge down is positive; any length of vector works.
static func lean(ux: float, uy: float, uz: float) -> float:
	return atan2(-ux, sqrt(uy * uy + uz * uz))


## Steering from -1 (full left) to 1 (full right) for a phone leaning [param p_lean] radians when
## level was [param neutral]: nothing inside the dead zone, full at FULL_DEG, finer near the middle.
static func steer(p_lean: float, neutral: float, dead_deg: float = DEAD_DEG, full_deg: float = FULL_DEG) -> float:
	var off := (p_lean - neutral) * (180.0 / PI)
	var a := absf(off)
	if not (a > dead_deg):
		return 0.0
	var t := minf((a - dead_deg) / (full_deg - dead_deg), 1.0)
	return signf(off) * t * (0.35 + 0.65 * t)
