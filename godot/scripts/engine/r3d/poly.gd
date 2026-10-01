class_name Poly
extends RefCounted
## engine/r3d/Model.kt Poly: one pre-built polygon of a static model. [member nx] [member ny]
## [member nz] is the face normal; smooth surfaces also carry per-vertex normals. [member gloss]
## (0..1) adds specular highlights. [member shade] is an optional brightness per vertex (baked
## ambient occlusion: it darkens the paint before it is lit).

var region: Region
var n: int
var xs: PackedFloat32Array
var ys: PackedFloat32Array
var zs: PackedFloat32Array
var us: PackedFloat32Array
var vs: PackedFloat32Array
var nx: float
var ny: float
var nz: float
var blend: int
var emissive: float
var cull: bool
## ARGB tint, -1 (or 0xFFFFFFFF) for none.
var tint: int
var gloss: float
## Per-vertex normals, or empty for flat shading.
var vnx: PackedFloat32Array
var vny: PackedFloat32Array
var vnz: PackedFloat32Array
## Per-vertex brightness, or empty.
var shade: PackedFloat32Array


func _init(p_region: Region, p_n: int, p_xs: PackedFloat32Array, p_ys: PackedFloat32Array, p_zs: PackedFloat32Array,
		p_us: PackedFloat32Array, p_vs: PackedFloat32Array, p_nx: float, p_ny: float, p_nz: float,
		p_blend: int, p_emissive: float, p_cull: bool, p_tint: int = -1, p_gloss: float = 0.0,
		p_vnx: PackedFloat32Array = PackedFloat32Array(), p_vny: PackedFloat32Array = PackedFloat32Array(),
		p_vnz: PackedFloat32Array = PackedFloat32Array(), p_shade: PackedFloat32Array = PackedFloat32Array()) -> void:
	region = p_region
	n = p_n
	xs = p_xs
	ys = p_ys
	zs = p_zs
	us = p_us
	vs = p_vs
	nx = p_nx
	ny = p_ny
	nz = p_nz
	blend = p_blend
	emissive = p_emissive
	cull = p_cull
	tint = p_tint
	gloss = p_gloss
	vnx = p_vnx
	vny = p_vny
	vnz = p_vnz
	shade = p_shade


func has_vertex_normals() -> bool:
	return not vnx.is_empty() and not vny.is_empty() and not vnz.is_empty()
