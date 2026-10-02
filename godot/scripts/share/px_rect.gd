class_name PxRect
extends RefCounted
## share/PhotoStrip.kt PxRect: a rectangle in a strip's pixels.

var left: int
var top: int
var right: int
var bottom: int

var width: int:
	get:
		return right - left

var height: int:
	get:
		return bottom - top


func _init(p_left: int, p_top: int, p_right: int, p_bottom: int) -> void:
	left = p_left
	top = p_top
	right = p_right
	bottom = p_bottom


func _to_string() -> String:
	return "(%d, %d)-(%d, %d)" % [left, top, right, bottom]
