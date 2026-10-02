class_name StackerTuning
extends RefCounted
## games/stacker/StackerGame.kt StackerTuning: difficulty and payout knobs for the stacker.

const ROUND_SECONDS := 60.0
const LIVES := 3
const BASE_SIZE := 120.0
const SLAB_HEIGHT := 18.0
## How far either side of the tower a slab slides before turning back.
const SWING := 170.0
const SPEED_START := 150.0
const SPEED_PER_LEVEL := 7.0
const SPEED_MAX := 430.0
## A drop within this distance of the slab below counts as perfect and loses nothing.
const PERFECT_TOLERANCE := 5.0
## After this many perfects in a row, each further perfect grows the slab back a little.
const GROW_AFTER := 3
const GROW_AMOUNT := 8.0
const LEVEL_POINTS := 10
const PERFECT_POINTS := 10
## Extra points per perfect in a row, counted up to COMBO_CAP.
const COMBO_POINTS := 5
const COMBO_CAP := 4
const POINTS_PER_TICKET := 20
const BASE_TICKETS := 1
