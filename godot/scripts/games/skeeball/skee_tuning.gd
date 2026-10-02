class_name SkeeTuning
extends RefCounted
## games/skeeball/SkeeBallGame.kt SkeeTuning: difficulty and payout knobs for skee-ball.

const ROUND_SECONDS := 40.0
## Flick speed (field units/s) → ball speed.
const FLICK_TO_SPEED := 0.33
const MIN_SPEED := 120.0
const MAX_SPEED := 760.0
## Upward flick speed needed to count as a roll.
const MIN_FLICK := 320.0
## Largest roll angle away from straight up, in degrees.
const MAX_ANGLE_DEG := 32.0
const ROLL_FRICTION := 60.0
## Vertical launch speed off the ramp as a fraction of planar speed.
const LAUNCH_RATIO := 0.5
const FLIGHT_GRAVITY := 1000.0
const RELOAD_SECONDS := 0.35
## Failsafe: a ball still in play this long after its roll is sent to the gutter. The slowest real
## roll (stopping just short of the ramp, then rolling all the way back) is over in about 5.5 s, so
## this only catches a ball the physics can't finish.
const BALL_TIMEOUT := 8.0

const RING_POINTS := [100, 50, 40, 30, 20, 10]
## Ring outer x-radii matching RING_POINTS; rings are ellipses squashed to 78% height.
const RING_RADII := [13.0, 30.0, 48.0, 68.0, 90.0, 118.0]
const BONUS_POINTS := 200

const POINTS_PER_TICKET := 50
const BASE_TICKETS := 1
