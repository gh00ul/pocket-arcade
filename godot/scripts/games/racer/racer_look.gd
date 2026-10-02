class_name RacerLook
extends RefCounted
## games/racer/RacerScene.kt RacerLook: every number that shapes how the racer's scene looks, in one
## place so it can be tuned blind.

# ---- Cars.
const BODY_GLOSS := 0.6
const GLASS_GLOSS := 0.8
## Tail-light strength (emissive; above 1 so the bloom lifts it).
const TAIL_EMISSIVE := 1.3
const UNDERGLOW_ALPHA := 0.32
const SHADOW_ALPHA := 0.5
## Rivals farther ahead than this are drawn as the cheap model.
const LOD_DISTANCE := 900.0

# ---- Sky.
const STARS := 30
const STAR_ALPHA := 0.7
const HORIZON_ALPHA := 0.5
const SEARCHLIGHT_ALPHA := 0.09

# ---- Road.
const ROAD_GLOSS := 0.4
const EDGE_LINE_EMISSIVE := 1.2
## Segments ahead that still get lane dashes and edge lines (further out they'd only shimmer).
const DETAIL_SEGMENTS := 40

# ---- Effects.
const SPEED_LINES := 12
## Speed (share of top speed) above which speed lines appear, and the alpha of one at full speed.
const SPEED_LINE_FROM := 0.6
const SPEED_LINE_ALPHA := 0.28
const SMOKE_ALPHA := 0.4
const SKID_ALPHA := 0.5
## How much of each flash and pulse survives the reduce-motion setting.
const CALM_K := 0.4
