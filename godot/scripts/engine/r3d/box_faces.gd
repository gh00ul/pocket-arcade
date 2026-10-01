class_name BoxFaces
extends RefCounted
## engine/r3d/Model.kt BoxFaces: which faces of a box are generated (null faces are not; the
## bottom never is), their glow, wrapping texel density and gloss.

var front: Region = null
var left: Region = null
var right: Region = null
var top: Region = null
var back: Region = null
var front_emissive := 0.0
var top_emissive := 0.0
## Texels per world unit for wrapping regions (their UVs are scaled by face size).
var texels_per_unit := 0.0
var gloss := 0.0


func _init(p_front: Region = null, p_left: Region = null, p_right: Region = null, p_top: Region = null, p_back: Region = null,
		p_front_emissive: float = 0.0, p_top_emissive: float = 0.0, p_texels_per_unit: float = 0.0, p_gloss: float = 0.0) -> void:
	front = p_front
	left = p_left
	right = p_right
	top = p_top
	back = p_back
	front_emissive = p_front_emissive
	top_emissive = p_top_emissive
	texels_per_unit = p_texels_per_unit
	gloss = p_gloss


## The same region on every visible face.
static func all(r: Region, p_gloss: float = 0.0) -> BoxFaces:
	return BoxFaces.new(r, r, r, r, r, 0.0, 0.0, 0.0, p_gloss)
