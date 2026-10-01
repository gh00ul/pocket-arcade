class_name Spring
extends RefCounted
## engine/Juice.kt Spring: a damped spring around 1.0 used for squash and stretch: kick it and
## read [member value] as a scale.

var stiffness: float
var damping: float
var target: float
var value: float
var _velocity := 0.0


func _init(p_stiffness: float = 380.0, p_damping: float = 16.0, p_target: float = 1.0) -> void:
	stiffness = p_stiffness
	damping = p_damping
	target = p_target
	value = p_target


func kick(impulse: float) -> void:
	_velocity += impulse


func snap(v: float) -> void:
	value = v
	_velocity = 0.0


func update(dt: float) -> void:
	var force := (target - value) * stiffness - _velocity * damping
	_velocity += force * dt
	value += _velocity * dt
