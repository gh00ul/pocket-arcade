class_name AnimSpring
extends RefCounted
## hub/AnimMath.kt Spring (renamed: the engine's Juice [Spring] already has the global name).
## A damped spring following a target: [member x] is the value and [member v] its speed. Integrated
## with small semi-implicit Euler steps, so it stays stable at any frame time and a long hitch just
## runs a few more sub-steps. Under-damped springs overshoot and settle, which is what gives hats,
## hair and swinging arms their follow-through.

## The longest stretch integrated in one call (a stalled frame is not worth simulating in full).
const MAX_STEP := 0.1
## Sub-step: 1/90 s keeps ω·h under 0.35 for the stiffest spring used here.
const SUB := 1.0 / 90.0

var x := 0.0
var v := 0.0


func _init(p_x: float = 0.0) -> void:
	x = p_x


## Runs the spring for [param dt] toward [param target] with natural frequency [param omega]
## (rad/s) and damping ratio [param zeta].
func step(target: float, dt: float, omega: float, zeta: float) -> void:
	drive(target, 0.0, dt, omega, zeta)


## As [method step], with an extra acceleration [param push] on the spring (a body accelerating
## under a hanging hat pushes it the other way): the spring pulls back to [param target] while it
## is pushed.
func drive(target: float, push: float, dt: float, omega: float, zeta: float) -> void:
	var left := clampf(dt, 0.0, MAX_STEP)
	var o2 := omega * omega
	var c := 2.0 * zeta * omega
	while left > 0.0:
		var h := SUB if left > SUB else left
		v += (o2 * (target - x) - c * v + push) * h
		x += v * h
		left -= h
	# A spring that has come to rest stays put (and never carries a denormal around).
	if x > -1e-5 and x < 1e-5 and v > -1e-4 and v < 1e-4 and target == 0.0 and push == 0.0:
		x = 0.0
		v = 0.0


func reset(value: float = 0.0) -> void:
	x = value
	v = 0.0
