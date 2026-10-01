class_name Flash
extends RefCounted
## engine/Juice.kt Flash: a value that jumps to 1 and decays to 0; white flashes and hit highlights.

var decay_per_sec: float
var value := 0.0


func _init(p_decay_per_sec: float = 4.0) -> void:
	decay_per_sec = p_decay_per_sec


func trigger(amount: float = 1.0) -> void:
	value = maxf(value, amount)


func reset() -> void:
	value = 0.0


func update(dt: float) -> void:
	value = maxf(value - decay_per_sec * dt, 0.0)
