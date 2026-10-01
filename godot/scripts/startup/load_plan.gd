class_name LoadPlan
extends RefCounted
## startup/LoadPlan.kt LoadPlan: an ordered list of [LoadStep]s, what a loading screen is waiting
## for. Pure data. The rest of LoadPlan.kt is [LoadStep], [LoadListener] and [LoadDriver].

static var EMPTY: LoadPlan = LoadPlan.new([])

var steps: Array[LoadStep] = []
var total_weight: float = 0.0


func _init(p_steps: Array = []) -> void:
	steps.assign(p_steps)
	# Kotlin sums in doubles and keeps a float.
	var sum := 0.0
	for s in steps:
		sum += s.weight
	total_weight = sum


## Kotlin's `plan + other`: this plan's steps, then [param other]'s.
func plus(other: LoadPlan) -> LoadPlan:
	return LoadPlan.new(steps + other.steps)


## False when a step couldn't be made (a bad weight): Kotlin would have thrown building the plan.
func is_valid() -> bool:
	for s in steps:
		if not s.valid:
			return false
	return true
