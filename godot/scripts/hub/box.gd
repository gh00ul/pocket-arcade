class_name Box
extends RefCounted
## hub/Collision.kt Box: an axis-aligned box in hall art pixels (x across the hall, y the hall's z
## from the back wall). [member top] < [member bottom] and [member left] < [member right].

var left: float
var top: float
var right: float
var bottom: float

var width: float:
	get:
		return right - left
var height: float:
	get:
		return bottom - top
var center_x: float:
	get:
		return (left + right) / 2.0
var center_y: float:
	get:
		return (top + bottom) / 2.0


func _init(p_left: float, p_top: float, p_right: float, p_bottom: float) -> void:
	left = p_left
	top = p_top
	right = p_right
	bottom = p_bottom


func contains(x: float, y: float) -> bool:
	return x >= left and x < right and y >= top and y < bottom


func intersects(l: float, t: float, r: float, b: float) -> bool:
	return l < right and r > left and t < bottom and b > top


static func of_size(x: float, y: float, w: float, h: float) -> Box:
	return Box.new(x, y, x + w, y + h)


func _to_string() -> String:
	return "Box(%s, %s, %s, %s)" % [left, top, right, bottom]
