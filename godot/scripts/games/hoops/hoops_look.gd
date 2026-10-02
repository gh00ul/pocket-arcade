class_name HoopsLook
extends RefCounted
## games/hoops/HoopsScene.kt HoopsLook: every number that shapes how the hoops scene looks, in one
## place so it can be tuned blind.

# ---- Lights. The ambient is dim and cool so the pools of light carry the mood.
const AMB_R := 0.36
const AMB_G := 0.34
const AMB_B := 0.48
const DIR_R := 0.26
const DIR_G := 0.25
const DIR_B := 0.30
## The warm lamp over the court, and the cool spot that picks out the backboard and the wall round it.
const GYM_Y := 330.0
const GYM_Z := -170.0
const GYM_RADIUS := 760.0
const GYM_INTENSITY := 1.3
const SPOT_Y := 310.0
const SPOT_Z := -215.0
const SPOT_RADIUS := 430.0
const SPOT_INTENSITY := 0.85
## A soft light in front of the shooter, so the ball reads first.
const READY_Y := 190.0
const READY_Z := 40.0
const READY_RADIUS := 320.0
const READY_INTENSITY := 0.7
## The light at the rim: a glow at rest, swelling with makes and rim hits.
const ACCENT_RADIUS := 260.0
const ACCENT_REST := 0.22
const ACCENT_MAKE := 1.5
const ACCENT_RIM := 0.7

# ---- Post effects the scene asks the renderer for.
const VIGNETTE := 0.3
const BLOOM := 0.85
## Streaks of the neon and the hoop in the glossy floor (0 off).
const FLOOR_REFLECT := 0.45
const FLOOR_GLOSS := 0.55
const BOARD_GLOSS := 0.7
const FRAME_GLOSS := 0.55

# ---- Emissive strengths (1 = the colour as painted, which the bloom then softens).
const AD_EMISSIVE := 0.8
const PANEL_EMISSIVE := 1.0
const LED_BASE := 0.75
const LED_PULSE := 0.25
const LED_FLASH := 1.1

# ---- Additive glows (alpha of each).
const SHAFT_ALPHA := 0.09
const SHAFT_HYPE := 0.10
const SIGN_HALO_ALPHA := 0.9
const POOL_ALPHA := 0.1
const POOL_FLASH := 0.22
const BOARD_GLOW_ALPHA := 0.55
const TRAIL_ALPHA := 0.45

## How much of each flash and pulse survives the reduce-motion setting.
const CALM_K := 0.4

# ---- Where things hang on the wall (centimetres).
const WALL_HALF_W := 260.0
const WALL_TOP := 520.0
const SIGN_Z := -316.7
const SIGN_Y := 372.0
const SIGN_HW := 98.0
const SIGN_HH := 23.5
const PANEL_X := 138.0
const PANEL_HW := 32.0
const PANEL_HH := 17.0
const PANEL_Z := -316.6
const AD_Z := -318.5
const AD_HW := 200.0
const AD_H := 34.0
const LAMP_X := 150.0
const LAMP_Y := 405.0
const LAMP_Z := -316.0
const RAIL_Y := 256.0
const RAIL_Z := -306.0
const FRAME_W := 4.0

# ---- The net.
const NET_STRANDS := 10
const NET_RINGS := 3
const NET_DROP := 36.0
## Fraction of the rim's radius the net narrows to at its foot.
const NET_FOOT := 0.58
## Angular twist of a strand from top to foot, so the two families of strands weave diamonds.
const NET_TWIST := 0.94
## How far a ball passing through pushes the net out, and how tall that bulge is (cm).
const NET_BULGE := 9.0
const NET_BULGE_H := 11.0
