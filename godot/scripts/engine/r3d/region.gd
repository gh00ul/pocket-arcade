class_name Region
extends RefCounted
## engine/r3d/Texture.kt Region: a rectangle of a [PaTexture]. Texture coordinates handed to the
## renderer are texel offsets inside the region; with [member wrap] they repeat (whole texture only).

var tex: PaTexture
var x: int
var y: int
var w: int
var h: int
var wrap: bool


func _init(p_tex: PaTexture, p_x: int, p_y: int, p_w: int, p_h: int, p_wrap: bool = false) -> void:
	tex = p_tex
	x = p_x
	y = p_y
	w = p_w
	h = p_h
	wrap = p_wrap
