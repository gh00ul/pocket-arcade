class_name HubMap
extends RefCounted
## hub/HubMap.kt HubMap: the built hall floor plan ([method HubLayout.build]): the props, the
## solids walking collides with, the spots, the kids' walk grid and hangouts, the spawn and clerk
## points, the disco ball's place, the bank each machine was given and the café queue.
##
## [member walkable] is Kotlin's BooleanArray as bytes (1 = walkable), row-major: index
## `ty * cols + tx`; [method tile_walkable] is the bounds-checked read.

var width_px: int
var height_px: int
var cols: int
var rows: int
var props: Array[Prop]
var solids: Array[Box]
var spots: Array[Spot]
var walkable: PackedByteArray
var spawn_x: float
var spawn_y: float
var clerk_x: float
var clerk_y: float
var hangouts: Array[Hangout]
var disco_x: float
var disco_y: float
## The bank each machine was given, by game index.
var machine_slots: Array[Slot]
## The café queue, from the till backwards.
var cafe_queue: Array[Hangout]


func _init(p_width_px: int, p_height_px: int, p_cols: int, p_rows: int, p_props: Array[Prop], p_solids: Array[Box],
		p_spots: Array[Spot], p_walkable: PackedByteArray, p_spawn_x: float, p_spawn_y: float, p_clerk_x: float,
		p_clerk_y: float, p_hangouts: Array[Hangout], p_disco_x: float, p_disco_y: float, p_machine_slots: Array[Slot],
		p_cafe_queue: Array[Hangout] = []) -> void:
	width_px = p_width_px
	height_px = p_height_px
	cols = p_cols
	rows = p_rows
	props = p_props
	solids = p_solids
	spots = p_spots
	walkable = p_walkable
	spawn_x = p_spawn_x
	spawn_y = p_spawn_y
	clerk_x = p_clerk_x
	clerk_y = p_clerk_y
	hangouts = p_hangouts
	disco_x = p_disco_x
	disco_y = p_disco_y
	machine_slots = p_machine_slots
	cafe_queue = p_cafe_queue


func tile_walkable(tx: int, ty: int) -> bool:
	return tx >= 0 and tx < cols and ty >= 0 and ty < rows and walkable[ty * cols + tx] != 0
