class_name PoseBlender
extends RefCounted
## hub/PoseBlender.kt: cross-fades between [Pose]s so a figure never snaps from one to the next.
## It keeps a weight for every pose (they always sum to 1); asking for a new pose eases the new one
## in and every other out over [method blend_time], starting from wherever the weights are right
## now, so a pose changed again half way through blends on smoothly from the middle of the last one.
##
## Pure and allocation-free after construction: one blender per figure, updated every step.

## Shortest and longest blends (seconds): quick enough to feel responsive, slow enough not to pop.
const BLEND_MIN := 0.12
const BLEND_MAX := 0.22

## Each pose's current weight, indexed by pose. Non-negative, summing to 1.
var weights := PackedFloat64Array()
## The pose being blended to (or held, once [member settled]). Read only.
var target: int = Pose.STAND

## True once the target has fully taken over (its weight is 1).
var settled: bool:
	get:
		return _progress >= 1.0

## The weights when the current blend began (a snapshot, so an interruption starts clean).
var _from := PackedFloat64Array()
var _progress := 1.0
var _duration := 0.2


func _init(initial: int = Pose.STAND) -> void:
	weights.resize(Pose.COUNT)
	_from.resize(Pose.COUNT)
	target = initial
	_duration = blend_time(initial)
	weights[initial] = 1.0
	_from[initial] = 1.0


## [param pose]'s weight now (0..1).
func weight(pose: int) -> float:
	return weights[pose]


## Blends to [param pose]; asking for the pose already being blended to changes nothing.
## (Kotlin's `set`.)
func set_pose(pose: int) -> void:
	if pose == target:
		return
	# Start from what is on screen now (renormalised, so rounding can never build up).
	var sum := 0.0
	for i in Pose.COUNT:
		sum += weights[i]
	var inv := 1.0 / sum if sum > 1e-6 else 1.0
	for i in Pose.COUNT:
		_from[i] = weights[i] * inv
	target = pose
	_progress = 0.0
	_duration = blend_time(pose)


## Jumps straight to [param pose] with no blend (a photo, a respawn).
func snap(pose: int) -> void:
	target = pose
	_progress = 1.0
	_duration = blend_time(pose)
	for i in Pose.COUNT:
		weights[i] = 1.0 if i == pose else 0.0
		_from[i] = weights[i]


## Advances the blend by [param dt] seconds.
func update(dt: float) -> void:
	if _progress >= 1.0 or not (dt > 0.0):
		return
	_progress = minf(1.0, _progress + dt / _duration)
	var s := AnimMath.smooth(_progress)
	var t := target
	for i in Pose.COUNT:
		weights[i] = _from[i] * (1.0 - s) + (s if i == t else 0.0)


## How long blending *into* [param pose] takes. Sharp poses (a cheer, a clap) come in fast,
## settling ones (sitting down, standing easy) take a little longer.
static func blend_time(pose: int) -> float:
	if pose == Pose.CLAP:
		return 0.12
	if pose == Pose.CHEER or pose == Pose.WAVE:
		return 0.14
	if pose == Pose.WALK or pose == Pose.CARRY:
		return 0.16
	if pose == Pose.PLAY or pose == Pose.WIPE or pose == Pose.HOLD:
		return 0.18
	if pose == Pose.STAND or pose == Pose.SIP:
		return 0.20
	# Pose.SIT.
	return 0.22
