class_name HockeyLook
extends RefCounted
## games/airhockey/HockeyScene.kt HockeyLook: every number that shapes how the air hockey scene
## looks, in one place.

# ---- Lights: a dim cool ambient, a lamp over the table, a cyan and a pink light at the two ends,
# a little red-orange light that travels with the puck, and a flash for goals.
const AMB_R := 0.3
const AMB_G := 0.32
const AMB_B := 0.46
const DIR_R := 0.22
const DIR_G := 0.23
const DIR_B := 0.3
const LAMP_Y := 380.0
const LAMP_RADIUS := 700.0
const LAMP_INTENSITY := 0.95
const END_Y := 90.0
const END_OFFSET := 60.0
const END_RADIUS := 420.0
const END_INTENSITY := 0.8
const PUCK_LIGHT_Y := 26.0
const PUCK_LIGHT_RADIUS := 190.0
const PUCK_LIGHT_LIVE := 0.8
const PUCK_LIGHT_SERVE := 0.3
const GOAL_LIGHT_RADIUS := 380.0
const GOAL_LIGHT_PEAK := 2.4

# ---- Materials and post effects.
const SURFACE_GLOSS := 0.55
const RAIL_GLOSS := 0.6
const MALLET_GLOSS := 0.75
const FLOOR_GLOSS := 0.45
const VIGNETTE := 0.28
const BLOOM := 0.9

# ---- Emissive strengths and additive glows.
const WALL_EMISSIVE := 0.9
const LED_LEVEL := 1.15
const UNDERGLOW_ALPHA := 0.22
const NEON_GLOW_ALPHA := 0.32
const TRAIL_ALPHA := 0.55

## How much of each flash and pulse survives the reduce-motion setting.
const CALM_K := 0.4

# ---- The wall behind the far end and the scoreboard in front of it (scene units).
const WALL_Z := -300.0
const WALL_HALF_W := 580.0
const WALL_TOP := 420.0
const BOARD_HW := 94.0
const BOARD_Y0 := 156.0
const BOARD_Y1 := 214.0
const FLOOR_HALF_X := 700.0
