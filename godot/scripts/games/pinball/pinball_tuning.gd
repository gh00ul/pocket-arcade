class_name PinballTuning
extends RefCounted
## games/pinball/PinballGame.kt PinballTuning: difficulty and payout knobs for pinball. Speeds are
## table units per second. (A Kotlin `object` beside PinballGame; a file of its own here so tests
## and tools read the knobs by build-13's names.)

const ROUND_SECONDS := 60.0
## Balls per round; losing the last one ends the round early.
const BALLS := 3

# Physics.
const GRAVITY := 1000.0
const MAX_SPEED := 2000.0
## Fraction of speed lost per second rolling across the playfield.
const DRAG := 0.04
## Normal speeds below this don't bounce (a ball resting on a flipper or a guide stays put).
const REST_SPEED := 45.0
const FLIP_UP_SPEED := 24.0
const FLIP_DOWN_SPEED := 14.0
const FLIP_BOUNCE := 0.3
const POST_BOUNCE := 0.55
const BUMPER_BOUNCE := 0.5
## Speed a pop bumper throws the ball away with.
const BUMPER_KICK := 560.0
const SLING_KICK := 470.0
## Approach speed a slingshot or drop target needs to fire.
const SLING_MIN := 70.0
const DROP_MIN := 60.0
## Largest movement per physics sub-step, as a fraction of the ball radius (never tunnel).
const SUBSTEP_FRACTION := 0.45

# Plunger.
const LAUNCH_MIN := 500.0
const LAUNCH_MAX := 1750.0
## Pull (0..1) below which letting go doesn't fire the ball.
const MIN_PULL := 0.08
## Field units of drag for a full pull.
const PULL_RANGE := 110.0
const AUTO_LAUNCH_SPEED := 1650.0
## The plunger answers touches below and right of this corner of the field (when a ball waits).
const PLUNGER_ZONE_X := 250.0
const PLUNGER_ZONE_Y := 380.0

# Help and failsafes.
const BALL_SAVE_SECONDS := 6.0
const MULTIBALL_SAVE_SECONDS := 8.0
const SERVE_DELAY := 1.1
## A ball slower than this for STUCK_SECONDS (and not cradled on a flipper) gets kicked free.
const STUCK_SPEED := 30.0
const STUCK_SECONDS := 3.0
const STUCK_KICK := 480.0
## After this many kicks in a row the ball is taken off and served again (no ball lost).
const STUCK_KICKS := 3
## A stall within this distance of the last kick counts as the same stuck spot.
const STUCK_RADIUS := 40.0
## Seconds of free play after a kick that clear the kick count.
const STUCK_FREE_SECONDS := 8.0

# Nudge and tilt: a quick upward swipe of a flipper thumb.
const NUDGE_DIST := 45.0
const NUDGE_MS := 170
const NUDGE_KICK := 170.0
const TILT_WARN := 1.6
const TILT_AT := 2.6
## Tilt meter drained per second.
const TILT_DECAY := 0.45

# Scoring (every award is multiplied by the playfield multiplier).
const BUMPER_POINTS := 10
const SLING_POINTS := 5
const DROP_POINTS := 25
const BANK_POINTS := 100
const LANE_POINTS := 25
const LANE_REPEAT_POINTS := 5
const MULTIBALL_POINTS := 150
const SPINNER_POINTS := 3
const ORBIT_POINTS := 40
const JACKPOT_POINTS := 300
const MAX_MULT := 5

const POINTS_PER_TICKET := 60
const BASE_TICKETS := 1
