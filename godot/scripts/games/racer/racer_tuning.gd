class_name RacerTuning
extends RefCounted
## games/racer/RacerGame.kt RacerTuning: difficulty and payout knobs for the racer.

const ROUND_SECONDS := 72.0
const LAPS := 3
## Cars on the grid, the player's included.
const CARS := 8
## The player's grid slot, 0 being pole position.
const PLAYER_SLOT := 6
const MAX_SPEED := 1100.0
const ACCEL := 520.0
const OFFROAD_SPEED := 450.0
## How hard curves push the car towards the outside (world units/s at top speed per unit of curve).
const CENTRIFUGAL := 110.0
const STEER_SPEED := 620.0
## Road units the steering target moves per field unit of finger travel.
const STEER_GAIN := 1.6
## Tilt steering: road units per second the target moves at full lean (about a fast thumb).
const TILT_RATE := 330.0
## Speed the player's car coasts at once past the flag.
const COAST_SPEED := 700.0

## Share of top speed a bend of 1 scrubs off.
const CORNER_SCRUB := 0.08

## Share of the curve's push still felt while drifting.
const DRIFT_GRIP := 0.35
## Share of the corner scrub still felt while drifting.
const DRIFT_SCRUB := 0.25
## Below this bend a drift has nothing to slide on and drags instead: holding one all race costs.
const DRIFT_MIN_BEND := 0.3
## Share of top speed a drift gives up where the road runs (nearly) straight.
const DRIFT_DRAG := 0.15
## Boost charge per second of drifting through a curve of bend 1 at top speed.
const DRIFT_CHARGE := 1.0
const BOOST_CHARGE_1 := 1.0
const BOOST_CHARGE_2 := 2.2
const BOOST_TIME_1 := 0.8
const BOOST_TIME_2 := 1.5
const BOOST_SPEED := 1450.0
const BOOST_ACCEL := 1400.0

## Rivals' top speed as a share of the player's, from pole down to the back of the grid.
const AI_SKILL_MAX := 1.0
const AI_SKILL_MIN := 0.9
const AI_ACCEL := 480.0
## Share of top speed rivals give up per unit of bend.
const AI_CORNER_SLOW := 0.045
const AI_STEER := 240.0
## Racing line: how far towards the inside of the coming bend cars aim, per unit of bend.
const LINE_GAIN := 45.0
const LINE_MAX := 85.0
## Rubber band: rivals this far ahead of (behind) the player drive up to RUBBER slower (faster).
const RUBBER := 0.05
const RUBBER_RANGE := 1500.0
## Sideways speed at which touching cars push apart.
const BUMP_SHOVE := 320.0
## A pass is clean if the player hasn't touched another car for this long.
const CLEAN_SECONDS := 1.2

## One point per this many world units driven.
const UNITS_PER_POINT := 350.0
const OVERTAKE_POINTS := 25
## Points for finishing 1st, 2nd, ...
const PLACE_POINTS := [500, 380, 290, 220, 160, 110, 70, 40]
## Share of the place points paid when the clock runs out before the flag.
const DNF_SHARE := 0.4
const TIME_BONUS_PER_SECOND := 12
const POINTS_PER_TICKET := 40
const BASE_TICKETS := 1
