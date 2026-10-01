class_name Spatial
extends RefCounted
## engine/audio/Spatial.kt: the maths of positional sound, as pure functions.
##
## World coordinates are the hall's: x runs across the hall, z from the back wall towards the
## entrance. A listener's yaw is the hall camera's: 0 faces +z, forward is (sin yaw, cos yaw), so the
## right-hand side is (-cos yaw, sin yaw). Overhead the camera looks at the back wall (yaw pi).

## Inside this distance a source is at full level (about a cabinet's width).
const REF_DISTANCE := 40.0
## Past this distance a source is silent (the hall is 608 x 1100).
const MAX_DISTANCE := 420.0
## Sources closer than this pan less and less towards the side.
const NEAR_FIELD := 30.0
## A source dead behind the listener is this much quieter than one dead ahead.
const REAR_SHADOW := 0.3
## [method place] scales the equal-power gains by this (sqrt 2), so a close sound dead ahead plays
## exactly as loud in each channel as an unpanned one.
const CENTRE_MAKEUP := 1.4142135


## Level at [param distance]: 1 out to REF_DISTANCE, then a quadratic ease to exactly 0 at MAX_DISTANCE.
static func attenuation(distance: float) -> float:
	var t := fade(distance)
	return (1.0 - t) * (1.0 - t)


## How far through the audible range [param distance] is: 0 at REF_DISTANCE or closer, 1 at MAX_DISTANCE.
static func fade(distance: float) -> float:
	return clampf((distance - REF_DISTANCE) / (MAX_DISTANCE - REF_DISTANCE), 0.0, 1.0)


## The equal-power pan law into [param out] (no CENTRE_MAKEUP, no distance).
static func pan_gains(pan: float, out: Placement) -> void:
	var p := clampf(pan, -1.0, 1.0)
	var a := (p + 1.0) * (PI / 4.0)
	out.left = cos(a)
	out.right = sin(a)
	out.pan = p


## Places a source at ([param x], [param z]) for a listener at ([param listener_x], [param listener_z])
## looking along [param yaw]: pan from its bearing, level from its distance and how far behind it is,
## and the reverb share.
static func place(listener_x: float, listener_z: float, yaw: float, x: float, z: float, out: Placement) -> void:
	var dx := x - listener_x
	var dz := z - listener_z
	var dist := sqrt(dx * dx + dz * dz)
	out.distance = dist
	var att := attenuation(dist)
	var t := fade(dist)
	out.send = 1.0 - t
	var k := att * CENTRE_MAKEUP
	if dist < 1e-3:
		pan_gains(0.0, out)
		out.left *= k
		out.right *= k
		return
	var sy := sin(yaw)
	var cy := cos(yaw)
	var ahead := (dx * sy + dz * cy) / dist
	var side := (dx * -cy + dz * sy) / dist
	# Easing the pan in over the near field: smoothstep of distance.
	var n := clampf(dist / NEAR_FIELD, 0.0, 1.0)
	var pan := side * (n * n * (3.0 - 2.0 * n))
	pan_gains(pan, out)
	var rear := -ahead if ahead < 0.0 else 0.0
	var g := k * (1.0 - REAR_SHADOW * rear)
	out.left *= g
	out.right *= g
