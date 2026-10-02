class_name StackerLook
extends RefCounted
## games/stacker/StackerScene.kt StackerLook: every number that shapes how the stacker's scene
## looks, in one place so it can be tuned blind.

# ---- Lights.
const AMB_R := 0.38
const AMB_G := 0.38
const AMB_B := 0.5
const DIR_R := 0.6
const DIR_G := 0.58
const DIR_B := 0.56
## The top glow light: the top slab's own colour, lighting the courses just below it.
const TOP_RADIUS := 420.0
const TOP_INTENSITY := 0.85
const TOP_FLASH := 1.4
const RIM_INTENSITY := 0.55
const KEY_INTENSITY := 0.45

# ---- Slab geometry and material.
## The chamfer round a slab's top edge, which the neon outline runs along.
const BEVEL := 1.5
const TOP_GLOSS := 0.6
const SIDE_GLOSS := 0.35
## Emissive strength of a slab's outline at full glow, and the least an old slab keeps.
const EDGE_EMISSIVE := 1.0
const EDGE_MIN := 0.15
## How many courses below the top the outline takes to fade to its minimum.
const GLOW_FADE_LEVELS := 9.0

# ---- The world around the tower.
const ROOF_Y := -60.0
const ROOF_HALF := 380.0
const CITY_Y := -1100.0
const CITY_HALF := 3500.0
const CITY_EMISSIVE := 0.9
const CLOUD_Y_NEAR := -380.0
const CLOUD_Y_FAR := -760.0
const CLOUD_ALPHA := 0.5
const COLUMN_ALPHA := 0.08
const MOTES := 16
## Height markers stand behind the tower's left, at this place (see the projection test).
const MARKER_X := -225.0
const MARKER_Z := -120.0
const MARKER_RANGE := 520.0

## How much of each flash and pulse survives the reduce-motion setting.
const CALM_K := 0.4
