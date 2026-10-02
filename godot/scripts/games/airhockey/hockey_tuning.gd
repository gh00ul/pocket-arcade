class_name HockeyTuning
extends RefCounted
## games/airhockey/AirHockeyGame.kt HockeyTuning: difficulty and payout knobs for air hockey.

const ROUND_SECONDS := 60.0
## First to this many goals ends the match early.
const GOALS_TO_WIN := 7
const GOAL_POINTS := 100
## Extra points per goal for each goal in a row.
const STREAK_BONUS := 25
const WIN_BONUS := 300
const PUCK_MAX_SPEED := 1300.0
## Fraction of puck speed kept per second (the air cushion is nearly frictionless).
const PUCK_DRAG := 0.45
const WALL_BOUNCE := 0.9
const MALLET_BOUNCE := 0.85
const PLAYER_MALLET_SPEED := 2200.0
## CPU mallet top speed at the start and once the player pulls ahead.
const CPU_SPEED_START := 360.0
const CPU_SPEED_MAX := 680.0
## Seconds the CPU takes to react to where the puck is.
const CPU_REACTION := 0.16
const POINTS_PER_TICKET := 50
const BASE_TICKETS := 2
