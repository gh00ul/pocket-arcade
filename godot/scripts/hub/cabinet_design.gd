class_name CabinetDesign
extends RefCounted
## hub/CabinetDesign.kt CabinetDesign: a machine's own hall cabinet, supplied through
## [method MiniGame.cabinet]: how big it is, where the camera dives to, its model, lights and live
## screen, and any parts that move in attract mode. Games without one get the built-in cabinet for
## their shape. A design lives in its game's package with the rest of the game's art: extend this
## class, set the sizes in `_init` and override [method build] (and [method animate] if parts move).
##
## Designs must be headless-safe: the sizes are plain numbers (the floor plan and its tests read
## them), and every texture or model is built lazily, from [method build] or [method animate] only.
##
## Coordinates are hall units: x across the hall, y up, z from the back wall towards the entrance.
## The cabinet faces +z; the player stands in front of the box's z1.

## Footprint across the hall. It must fit the width of its bank's cells (see [member HubLayout.slots]).
var width := 0.0
## Footprint front to back; must fit the cells' depth.
var depth := 0.0
## Height of the tallest part; must fit the cells' height.
var height := 0.0
## Height the camera dives to when the machine is entered (usually the screen's middle).
var focus_height := 0.0
## How far behind the cabinet's front the dive ends.
var focus_set_back := 0.0
## Size of the attract screen in painter units (what [method MiniGame.draw_attract] gets as w × h),
## a [Vector2i], or null if the cabinet has no live screen. The build's live_screen() makes one
## this size.
var screen_units: Variant = null


## Builds one copy of the cabinet into [param c] (a CabinetBuild): model parts through its builder
## `b` and the helpers, lights with `light` (2 or 3 per cabinet: the hall has a light budget) and
## the live screen with `live_screen`. Called once per copy.
func build(_c: Object) -> void:
	pass


## Draws the parts that move in attract mode (a patrolling claw, a sweeping shelf) every frame the
## cabinet is on screen, at hall time [param t]; [param c] is the copy's CabinetBox. Draw opaque
## parts only; must not allocate.
func animate(_r: Renderer3D, _c: Object, _t: float) -> void:
	pass
