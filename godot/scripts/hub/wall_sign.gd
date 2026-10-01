class_name WallSign
extends RefCounted
## hub/HubMap.kt WallSign: a neon zone sign on a side wall ([member right] or left): its words and
## colour, and the stretch of wall it spans (z0..z1 along the wall, y0..y1 up it). Each one
## straddles its bank's front and the cross aisle in front of it, so it reads from the hall camera
## over the bank and, at eye level, from the main aisle down the cross aisle. A sign with a
## [member shape] (a MiniGame.CabinetShape; -1 for none) is only up while a machine of that shape
## is on the floor.

var text: String
var color: int
var right: bool
var z0: float
var z1: float
var y0: float
var y1: float
## The neon texture's width in pixels and its lettering size.
var tex_w: int
var size: float
var shape: int


func _init(p_text: String, p_color: int, p_right: bool, p_z0: float, p_z1: float, p_y0: float = 100.0,
		p_y1: float = 124.0, p_tex_w: int = 640, p_size: float = 100.0, p_shape: int = -1) -> void:
	text = p_text
	color = p_color
	right = p_right
	z0 = p_z0
	z1 = p_z1
	y0 = p_y0
	y1 = p_y1
	tex_w = p_tex_w
	size = p_size
	shape = p_shape
