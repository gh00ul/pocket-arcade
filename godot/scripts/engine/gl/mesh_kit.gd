class_name MeshKit
extends RefCounted
## Shared bits of the Godot side of the renderer.

## Surface flag: CUSTOM0 holds 4 floats per vertex (emissive, depth bias, gloss, fog).
const CUSTOM0_FLOAT := Mesh.ARRAY_CUSTOM_RGBA_FLOAT << Mesh.ARRAY_FORMAT_CUSTOM0_SHIFT

static var _black: ImageTexture = null


## A 1 × 1 black texture (stand-in for a reflection source that isn't there).
static func black() -> Texture2D:
	if _black == null:
		var img := Image.create_empty(1, 1, false, Image.FORMAT_RGBA8)
		img.fill(Color(0, 0, 0, 1))
		_black = ImageTexture.create_from_image(img)
	return _black


## Mesh arrays for one batch of immediate polygons.
static func batch_arrays(b: RenderPass.Batch) -> Array:
	var arrays := []
	arrays.resize(Mesh.ARRAY_MAX)
	arrays[Mesh.ARRAY_VERTEX] = b.pos
	arrays[Mesh.ARRAY_NORMAL] = b.nrm
	arrays[Mesh.ARRAY_TEX_UV] = b.uv
	arrays[Mesh.ARRAY_COLOR] = b.col
	arrays[Mesh.ARRAY_CUSTOM0] = b.cus
	return arrays
