class_name HdrGuard
extends RefCounted
## engine/gl/HdrPipeline.kt HdrGuard: the record of what went wrong with the HDR picture, kept for
## the whole run. The renderer turns the picture off at the first sign of trouble and draws the
## LDR one, which is unchanged and has always worked. In Godot the trouble it can see is a float
## render target that does not hold light above 1 ([method Gfx.probe_float_targets]); shader
## compiles are Godot's own and raise no error the game can catch.

## HDR is off for the rest of the run.
static var blocked := false
## Multisampled float targets failed: HDR is only possible without multisampling.
static var msaa_blocked := false
## Why, for the log.
static var reason := ""


static func block(why: String) -> void:
	if not blocked:
		reason = why
	blocked = true


static func block_msaa(why: String) -> void:
	if not msaa_blocked and not blocked:
		reason = why
	msaa_blocked = true


## Clears everything (tests).
static func reset() -> void:
	blocked = false
	msaa_blocked = false
	reason = ""
