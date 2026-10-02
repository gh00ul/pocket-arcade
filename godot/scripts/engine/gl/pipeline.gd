class_name Pipeline
extends RefCounted
## engine/gl/HdrPipeline.kt Pipeline: which picture the renderer draws with.

## RGBA8 scene target with a per-fragment tone map: the picture before HDR existed. (In Godot the
## LDR scene target is RGB10_A2, Godot's format for an opaque SubViewport.)
const LDR := 0
## RGBA16F scene target, no multisampling.
const HDR := 1
## RGBA16F multisampled scene target resolved into an RGBA16F texture.
const HDR_MSAA := 2

const LABELS := ["LDR", "HDR16F", "HDR16F+MSAA"]


static func label(p: int) -> String:
	return LABELS[clampi(p, 0, 2)]


static func is_hdr(p: int) -> bool:
	return p != LDR
