class_name WhackTuning
extends RefCounted
## games/whackamole/WhackAMoleGame.kt WhackTuning: difficulty and payout knobs for whack-a-mole.

const ROUND_SECONDS := 45.0
## Seconds between pop-ups at the start and at the end of the round (linear ramp).
const SPAWN_INTERVAL_START := 0.95
const SPAWN_INTERVAL_END := 0.38
## How long a mole stays up at the start and at the end of the round.
const UP_TIME_START := 1.15
const UP_TIME_END := 0.55
const GOLD_CHANCE := 0.08
const BOMB_CHANCE_START := 0.10
const BOMB_CHANCE_END := 0.22
const POINTS_NORMAL := 10
const POINTS_GOLD := 20
const BOMB_PENALTY := 30
## Every this many hits in a row adds COMBO_BONUS points per hit.
const COMBO_STEP := 5
const COMBO_BONUS := 2
const BOMB_STUN_SECONDS := 0.5
const POINTS_PER_TICKET := 30
const BASE_TICKETS := 1
