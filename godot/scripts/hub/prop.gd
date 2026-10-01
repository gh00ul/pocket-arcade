class_name Prop
extends RefCounted
## hub/HubMap.kt Prop: an object on the hall floor. x runs across the hall, z from the back wall (0)
## towards the entrance. [member height] is its top; machines face +z (towards the entrance and the
## camera). [member kind] is a [PropKind]; [member decor] a Catalog.DecorStyle or
## [constant Catalog.NONE] (Kotlin's null); [member shape] a MiniGame.CabinetShape.

var kind: int
var x0: float
var z0: float
var x1: float
var z1: float
var height: float
var machine: int
var decor: int
var solid: bool
var shape: int
var variant: int
## The footprint collision uses, or null for a prop nothing bumps into.
var foot: Box

var center_x: float:
	get:
		return (x0 + x1) / 2.0
var center_z: float:
	get:
		return (z0 + z1) / 2.0
var front_z: float:
	get:
		return z1


func _init(p_kind: int, p_x0: float, p_z0: float, p_x1: float, p_z1: float, p_height: float,
		p_machine: int = -1, p_decor: int = Catalog.NONE, p_solid: bool = true,
		p_shape: int = MiniGame.CabinetShape.UPRIGHT, p_variant: int = 0) -> void:
	kind = p_kind
	x0 = p_x0
	z0 = p_z0
	x1 = p_x1
	z1 = p_z1
	height = p_height
	machine = p_machine
	decor = p_decor
	solid = p_solid
	shape = p_shape
	variant = p_variant
	foot = Box.new(x0, z0, x1, z1) if solid else null
