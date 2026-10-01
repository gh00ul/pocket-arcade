class_name GameLoop
extends RefCounted
## engine/GameLoop.kt: the fixed simulation step and the frame accumulator that turns display
## frames into whole steps (snapping vsync jitter, capping catch-up).

## Simulation step. 120 Hz divides evenly into 60, 90 and 120 Hz displays and keeps physics stable.
const FIXED_DT := 1.0 / 120.0
const MAX_FRAME_SECONDS := 0.1
const MAX_STEPS_PER_FRAME := 12
const VSYNC_SNAP_SECONDS := 0.0006

var _accumulator := 0.0
## Frames seen (Compose's frame counter).
var frame := 0


## How many fixed steps this frame's [param elapsed] seconds give; run the step that many times.
func steps_for(elapsed: float) -> int:
	elapsed = clampf(elapsed, 0.0, MAX_FRAME_SECONDS)
	var whole := MathUtil.round_to_int(elapsed / FIXED_DT)
	if whole > 0 and absf(elapsed - whole * FIXED_DT) < VSYNC_SNAP_SECONDS:
		elapsed = whole * FIXED_DT
	_accumulator += elapsed
	var steps := 0
	while _accumulator >= FIXED_DT - 1e-6 and steps < MAX_STEPS_PER_FRAME:
		_accumulator -= FIXED_DT
		steps += 1
	if steps == MAX_STEPS_PER_FRAME:
		_accumulator = 0.0
	if _accumulator < 0.0:
		_accumulator = 0.0
	frame += 1
	return steps


func reset() -> void:
	_accumulator = 0.0
