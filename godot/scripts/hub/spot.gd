class_name Spot
extends RefCounted
## hub/HubMap.kt Spot: a place the player can stand to use something. [member area] is where they
## must be; the prompt floats at the anchor, and the enter transition flies the camera to the
## focus point. A spot generated for an interactive prop carries that [member prop], so a handler
## can tell which of several (vending machine, ride) was tapped. [member type] is a [SpotType].

var type: int
var machine: int
var area: Box
var anchor_x: float
var anchor_height: float
var anchor_z: float
var focus_x: float
var focus_y: float
var focus_z: float
var prop: Prop


func _init(p_type: int, p_machine: int, p_area: Box, p_anchor_x: float, p_anchor_height: float, p_anchor_z: float,
		p_focus_x: float, p_focus_y: float, p_focus_z: float, p_prop: Prop = null) -> void:
	type = p_type
	machine = p_machine
	area = p_area
	anchor_x = p_anchor_x
	anchor_height = p_anchor_height
	anchor_z = p_anchor_z
	focus_x = p_focus_x
	focus_y = p_focus_y
	focus_z = p_focus_z
	prop = p_prop
