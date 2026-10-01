class_name SimClock
extends RefCounted
## engine/GameLoop.kt SimClock: turns the host's real fixed steps into game steps under a time
## scale. The game always steps by exactly FIXED_DT; slow motion means fewer steps, a freeze none.

var _acc := 0.0


## Feed the scaled time of each real step (at most FIXED_DT); returns whether the game steps.
func advance(scaled_dt: float) -> bool:
	_acc += scaled_dt
	if _acc < GameLoop.FIXED_DT - 1e-6:
		return false
	_acc = maxf(_acc - GameLoop.FIXED_DT, 0.0)
	return true


func reset() -> void:
	_acc = 0.0
