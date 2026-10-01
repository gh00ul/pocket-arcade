class_name StepRange
extends RefCounted
## data/SettingsStore.kt StepRange: a stepper's range, [min]..[max] in whole [step]s.

var min_v: int
var max_v: int
var step: int


func _init(p_min: int, p_max: int, p_step: int) -> void:
	min_v = p_min
	max_v = p_max
	step = p_step


## [param v] limited to the range and snapped to the nearest step.
func clamp_to(v: int) -> int:
	return mini(((clampi(v, min_v, max_v) - min_v + step / 2) / step) * step + min_v, max_v)


## One step up ([param dir] > 0) or down from [param v], stopping at the ends.
func nudge(v: int, dir: int) -> int:
	return clamp_to(clamp_to(v) + (step if dir > 0 else -step))
