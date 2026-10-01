class_name PointLight
extends RefCounted
## engine/r3d/Lighting.kt PointLight: a coloured light with a smooth quadratic falloff to zero at
## [member radius].

var x: float
var y: float
var z: float
var r: float
var g: float
var b: float
var radius: float
var intensity: float


func _init(p_x: float = 0.0, p_y: float = 0.0, p_z: float = 0.0, p_r: float = 1.0, p_g: float = 1.0, p_b: float = 1.0, p_radius: float = 100.0, p_intensity: float = 1.0) -> void:
	x = p_x
	y = p_y
	z = p_z
	r = p_r
	g = p_g
	b = p_b
	radius = p_radius
	intensity = p_intensity
