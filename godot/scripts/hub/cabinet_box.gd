class_name CabinetBox
extends RefCounted
## hub/CabinetDesign.kt CabinetBox: one copy of a cabinet on the floor: the box it stands in
## ([member x0]..[member x1] across, [member z0] back to [member z1] front, [member h] tall), which
## copy of the bank it is ([member variant]), a seed to vary copies by ([member seed_value]; Kotlin's
## `seed`, a name GDScript keeps for its own function) and the game's printed artwork
## ([member art]). Also handed to CabinetDesign.animate.

var x0: float
var x1: float
var z0: float
var z1: float
var h: float
var variant: int
var seed_value: int
var art: MachineArt

var cx: float:
	get:
		return (x0 + x1) / 2.0
var cz: float:
	get:
		return (z0 + z1) / 2.0

## Scratch placements for drawing or adding models; reuse them rather than allocating.
var xf := Xform.new()
var xf2 := Xform.new()


func _init(p_x0: float, p_x1: float, p_z0: float, p_z1: float, p_h: float, p_variant: int, p_seed: int, p_art: MachineArt) -> void:
	x0 = p_x0
	x1 = p_x1
	z0 = p_z0
	z1 = p_z1
	h = p_h
	variant = p_variant
	seed_value = p_seed
	art = p_art


## This copy's attract clock, offset so copies side by side don't move in step.
func phase(t: float) -> float:
	return t + seed_value * 0.37
