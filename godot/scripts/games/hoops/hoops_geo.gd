class_name HoopsGeo
extends RefCounted
## games/hoops/HoopsScene.kt HoopsGeo: measurements the simulation and the 3D scene share. The
## simulation works in metres, the scene in centimetres with z towards the viewer ([constant S]
## converts, and the scene's z is minus the simulation's). The camera in [HoopsGame] matches the
## simulation's own projection exactly.

const G := 9.8
const CAM_Y := 1.7
const CAM_Z := -1.2
const F := 420.0
const CX := 180.0
const HORIZON := 258.0
const BALL_R := 0.12
const RIM_R := 0.23
const RIM_Y := 2.3
const HOOP_Z := 2.6
const BOARD_Z := 2.87
const BOARD_HALF_W := 0.6
const BOARD_BOTTOM := 2.15
const BOARD_TOP := 3.0
const START_Y := 0.7
const START_Z := 0.2
const CAGE_HALF_W := 1.0
const BACK_Z := 3.2

## World units (centimetres) per simulation metre.
const S := 100.0

## The wall behind the hoop, in scene units.
const WALL_Z := -BACK_Z * S
