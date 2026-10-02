class_name Hangout
extends RefCounted
## hub/HubMap.kt Hangout: where a wandering kid can stop: position, which way they face (radians)
## and whether it's a machine. Kids path to the walk-grid tile ([member tile_x], [member tile_y])
## nearest the spot, then step onto it. A [member cafe] seat is where kids sit with what they
## bought; the tile is the one nearest the approach point ([member approach_x],
## [member approach_z]), so kids get into a booth from the aisle end.
##
## Kotlin's defaults that depend on other arguments: a tile left at [constant AUTO_TILE] is the one
## under (x, z), an approach left NAN is (x, z).

const AUTO_TILE := -2147483648

var x: float
var z: float
var yaw: float
var playing: bool
var tile_x: int
var tile_y: int
var cafe: bool
var approach_x: float
var approach_z: float


func _init(p_x: float, p_z: float, p_yaw: float, p_playing: bool, p_tile_x: int = AUTO_TILE, p_tile_y: int = AUTO_TILE,
		p_cafe: bool = false, p_approach_x: float = NAN, p_approach_z: float = NAN) -> void:
	x = p_x
	z = p_z
	yaw = p_yaw
	playing = p_playing
	tile_x = int(x / HubLayout.TILE) if p_tile_x == AUTO_TILE else p_tile_x
	tile_y = int(z / HubLayout.TILE) if p_tile_y == AUTO_TILE else p_tile_y
	cafe = p_cafe
	approach_x = x if is_nan(p_approach_x) else p_approach_x
	approach_z = z if is_nan(p_approach_z) else p_approach_z
