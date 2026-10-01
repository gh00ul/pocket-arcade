class_name Placement
extends RefCounted
## engine/audio/Spatial.kt Placement: where a sound lands in the stereo field (left and right
## gains, reverb share). One instance is reused per caller.

const SILENT := 0.0005

## Gain into the left and right channels (equal-power pan, distance and rear shadow folded in).
var left := 1.0
var right := 1.0
## How much of the source's reverb send survives the distance, 0..1 (falls off more slowly than the
## dry gain, so far sounds arrive wetter).
var send := 1.0
## Straight-line distance to the listener, world units.
var distance := 0.0
## Pan, -1 (hard left) to +1 (hard right).
var pan := 0.0


## True when nothing of it would be heard, so the caller can skip the voice altogether.
func is_silent() -> bool:
	return left <= SILENT and right <= SILENT
