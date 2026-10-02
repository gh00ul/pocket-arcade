class_name FishingTuning
extends RefCounted
## games/fishing/FishingGame.kt FishingTuning: difficulty and payout knobs for fishing. (A Kotlin
## `object` beside FishingGame; a file of its own here so tests and tools read the knobs by
## build-13's names.)

const ROUND_SECONDS := 55.0

# Casting.
## Seconds for the power meter to fill and empty again while the cast is held.
const CHARGE_PERIOD := 1.4
## Cast distance (world units from the dock) at no power and at full power.
const MIN_CAST := 90.0
const MAX_CAST := 470.0
## Widest aim either side of straight out, in degrees (finger at the field's edge).
const MAX_AIM_DEG := 38.0
## Seconds the lure flies: a base plus a little per unit of distance.
const FLIGHT_BASE := 0.35
const FLIGHT_PER_UNIT := 0.0011
## Fish this close to the splash dart away for a moment.
const SPLASH_SPOOK_RADIUS := 18.0

# Bites.
## How far a fish notices a lure from.
const NOTICE_RADIUS := 120.0
## Nibbles before the bite, and the gaps between them.
const NIBBLES_MIN := 1
const NIBBLES_MAX := 3
const NIBBLE_GAP_MIN := 0.45
const NIBBLE_GAP_MAX := 0.85
## Seconds the fish holds the lure under: crank in that time or it lets go.
const STRIKE_WINDOW := 1.0
## Radians of reel-in that set the hook (also what counts as striking too soon).
const STRIKE_ANGLE := 0.6
## Seconds a spooked fish stays away from lures.
const SPOOK_SECONDS := 5.0
## A lure landing this near the old boot snags it, after BOOT_SNAG_DELAY.
const BOOT_SNAG_RADIUS := 40.0
const BOOT_SNAG_DELAY := 1.0
const BOOT_WINDOW := 1.4
## A suitor that hasn't reached the lure after this long loses interest.
const APPROACH_TIMEOUT := 6.0

# Reel and line.
## Crank speed (radians per second) of a brisk, steady wind: 1.0 on the tension scale.
const CRANK_REF := 10.0
## Line wound in per radian of crank, and how much a heavy fish slows that per pound.
const REEL_PER_RAD := 12.0
const REEL_WEIGHT_K := 0.08
## Lure pulled in per radian while no fish is on.
const RETRIEVE_PER_RAD := 9.0
## Line given back per radian of cranking backwards.
const LET_OUT_PER_RAD := 8.0
## A fish this near the dock is landed.
const LAND_DIST := 50.0

# Tension: 0 is a slack line, 1 snaps it.
const REST_TENSION := 0.14
const RUN_TENSION := 0.25
const RUN_PULL_TENSION := 0.8
const BOOT_TENSION := 0.3
## Tension added per unit of crank speed (see CRANK_REF).
const CRANK_TENSION := 0.45
const LET_OUT_RELIEF := 0.5
## How fast the tension follows its target (per second).
const TENSION_RATE := 6.0
## Jolt at the start of every run, times the fish's pull.
const YANK := 0.25
const SNAP_TENSION := 1.0
## Seconds over SNAP_TENSION before the line goes.
const SNAP_GRACE := 0.35
const SLACK_TENSION := 0.2
## Seconds of slack before the fish throws the hook.
const SLACK_GRACE := 1.2
## Top of the green band on the gauge.
const SAFE_HIGH := 0.85

# The fight's rhythm: rest, a warning thrash, then a run.
const FIRST_REST := 0.9
const REST_MIN := 0.9
const REST_MAX := 1.8
const WARN_SECONDS := 0.5
const RUN_MIN := 0.55
const RUN_MAX := 1.1
## Line a resting fish drifts out per second, times its pull.
const REST_DRIFT := 8.0
## Runs sway the fish sideways this fast (radians per second).
const RUN_SWAY := 0.35
const MAX_SWAY := 0.75
## Each run leaves the fish this much of its strength.
const STAMINA_DECAY := 0.82
## Failsafe: a fight still going this long ends with the fish getting away.
const FIGHT_TIMEOUT := 40.0
const LAND_SECONDS := 0.9
const RECOVER_SECONDS := 0.5
const RESPAWN_SECONDS := 1.5
## Failsafe: anything still busy this long after time-up is packed away.
const SETTLE_FAILSAFE := 4.0

# Species: perch, bass, catfish, golden carp, and junk (the old boot).
const NAMES: Array[String] = ["PERCH", "BASS", "CATFISH", "GOLDEN CARP", "OLD BOOT"]
const LENGTH: Array[float] = [16.0, 24.0, 34.0, 20.0, 14.0]
const SPEED: Array[float] = [36.0, 30.0, 20.0, 50.0, 0.0]
const WEIGHT_MIN: Array[float] = [0.4, 1.5, 4.0, 1.0, 1.0]
const WEIGHT_MAX: Array[float] = [1.0, 3.5, 8.0, 2.0, 1.0]
## Points per pound. A catch scores weight × value.
const VALUE: Array[float] = [18.0, 12.0, 9.0, 60.0, 3.0]
## How hard each species pulls on a run (0..1) and how fast it takes line.
const PULL: Array[float] = [0.25, 0.4, 0.55, 0.5, 0.0]
const RUN_SPEED: Array[float] = [40.0, 60.0, 75.0, 90.0, 0.0]
## Chance per second a fish near the lure goes for it.
const APPETITE: Array[float] = [1.0, 0.9, 0.8, 0.7, 0.0]
## Relative odds of each species when a fish swims into the pond.
const SPAWN_ODDS: Array[float] = [45.0, 33.0, 16.0, 6.0, 0.0]
## Fish swimming in the pond at once (the boot comes on top).
const FISH_SLOTS := 10

const POINTS_PER_TICKET := 10
const BASE_TICKETS := 2
## A catch worth this much gets the big celebration.
const BIG_CATCH := 50
