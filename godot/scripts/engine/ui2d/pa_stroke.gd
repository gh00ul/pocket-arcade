class_name PaStroke
extends RefCounted
## Compose's Stroke(width, cap) for the DrawScope shim.

var width: float
var round_cap: bool


func _init(p_width: float, p_round_cap: bool = false) -> void:
	width = p_width
	round_cap = p_round_cap
