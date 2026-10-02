class_name HoopsTuning
extends RefCounted
## games/hoops/HoopsGame.kt HoopsTuning: difficulty and payout knobs for basketball hoops.

const ROUND_SECONDS := 44.0
## Launch angle above horizontal, degrees.
const LAUNCH_ANGLE_DEG := 68.0
## Upward flick speed (field units/s) that produces the perfect-power shot.
const FLICK_IDEAL := 1700.0
## Fractional power change per field unit/s away from FLICK_IDEAL. Lower is more forgiving.
const POWER_SENSITIVITY := 0.0001
const MIN_FLICK := 380.0
## Sideways flick speed → sideways ball speed (m/s per field unit/s).
const LATERAL_SCALE := 0.0012
## 0..1 share of the sideways aim the machine corrects for you (aimed at the hoop's current spot).
const AIM_ASSIST := 0.6
const RELOAD_SECONDS := 0.3
const MOVE_AMPLITUDE_M := 0.45
const MOVE_PERIOD := 3.2
const BASKET_POINTS := 10
const SWISH_BONUS := 3
const MAX_MULTIPLIER := 5
const POINTS_PER_TICKET := 20
const BASE_TICKETS := 2
