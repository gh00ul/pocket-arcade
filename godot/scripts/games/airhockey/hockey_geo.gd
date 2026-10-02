class_name HockeyGeo
extends RefCounted
## games/airhockey/HockeyScene.kt HockeyGeo: the rink's measurements, in world units (x across, y
## up, z from the CPU's end to the player's; the simulation's y is the world's z). Shared by the
## simulation and the 3D scene.

const RL := 40.0
const RR := 320.0
const RT := 60.0
const RB := 600.0
const CX := 180.0
const CY := (RT + RB) / 2.0
const GOAL_HALF := 62.0
const PUCK_R := 13.0
const MALLET_R := 22.0
const RAIL_H := 12.0
## Radius of the table's rounded corners, which steer pucks back into play.
const CORNER_R := 46.0
## Height of the mallet's grip plane, where touches land.
const MALLET_H := 8.0
## Height of the table's top above the arena floor, and so the floor's height.
const TABLE_H := 160.0
const FLOOR_Y := -TABLE_H
