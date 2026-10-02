class_name DriftButton
extends RefCounted
## games/racer/RacerGame.kt DriftButton: the on-screen DRIFT button, in field units: low on the
## right, where the right thumb rests, and clear of the speed and drift gauges under it. Hold it to
## drift; the touch has a little more reach than the drawn disc.

const X := 302.0
const Y := 528.0
## Radius of the drawn disc.
const R := 34.0
## Radius of the touch area.
const HIT_R := 46.0


## Whether a touch at field position ([param x], [param y]) presses the button.
static func hit(x: float, y: float) -> bool:
	var dx := x - X
	var dy := y - Y
	return dx * dx + dy * dy <= HIT_R * HIT_R
