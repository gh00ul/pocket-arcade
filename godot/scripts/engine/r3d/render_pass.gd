class_name RenderPass
extends RefCounted
## engine/r3d/RenderPass.kt: one frame of 3D drawing for one area of the screen, recorded by
## [Renderer3D] and handed to [Gfx], which turns it into the slot's Godot scene the same frame.
## Containers are reused between frames.

## Floats per model instance in a run (MultiMesh buffer order): a 3×4 placement, the instance
## colour (tint rgb, 1) and the custom data (tint rgb, emissive boost). Instance colours are always
## on: Godot's Compatibility renderer multiplies vertex colours by garbage when a MultiMesh has none.
const INSTANCE_STRIDE := 20
const MAX_LIGHTS := 64
## Light indices stored per light-grid cell.
const CELL_LIGHTS := 8
## The near clipping distance every pass used before it became configurable.
const DEFAULT_NEAR := 8.0
## The grid texture is this size at most (two texels per cell, so 64 × 64 cells).
const GRID_MAX_W := 64
const GRID_MAX_H := 64


## Immediate polygons sharing one texture and blend, as Godot mesh arrays.
class Batch:
	extends RefCounted
	var tex: PaTexture
	var blend: int
	var glow := false
	var pos := PackedVector3Array()
	var nrm := PackedVector3Array()
	var uv := PackedVector2Array()
	var col := PackedColorArray()
	var cus := PackedFloat32Array()

	func reset(p_tex: PaTexture, p_blend: int) -> void:
		tex = p_tex
		blend = p_blend
		glow = false
		pos.clear()
		nrm.clear()
		uv.clear()
		col.clear()
		cus.clear()

	func vertex_count() -> int:
		return pos.size()


## Placements of one model for one blend layer (Blend.OPAQUE runs hold every opaque instance).
class ModelRun:
	extends RefCounted
	var model: Model
	var blend: int
	var glow := false
	var data := PackedFloat32Array()
	var count := 0

	func reset(p_model: Model, p_blend: int) -> void:
		model = p_model
		blend = p_blend
		glow = false
		data.clear()
		count = 0


## A frame the frame-rate cap skipped: empty, dropped by Gfx.submit (the last picture stays).
var skipped := false

# Where on the window to draw (logical units, top-left origin) and an optional clip rectangle.
var vx := 0.0
var vy := 0.0
var vw := 1.0
var vh := 1.0
var clip := false
var cx0 := 0.0
var cy0 := 0.0
var cx1 := 0.0
var cy1 := 0.0

## The camera, as recorded (a copy).
var cam := PaCamera3D.new()
var near := DEFAULT_NEAR

# Background.
var clear_color := 0
## Per band: [top ARGB, bottom ARGB, y0, y1] with y as fractions of the image height.
var gradients: Array = []

# Lighting and fog.
var ambient := Vector3.ZERO
var dir_dir := Vector3(0, 1, 0)
var dir_col := Vector3.ZERO
var light_count := 0
## Per light: x, y, z, radius, r, g, b, intensity.
var lights := PackedFloat32Array()
var fog_near := 1e8
var fog_far := 2e8
var fog_floor := 0.0
var exposure := 1.0
var bloom := 0.8
var bloom_threshold := Look.BLOOM_THRESHOLD
var bloom_threshold_hdr := Look.BLOOM_THRESHOLD_HDR
var bloom_radius := Look.BLOOM_RADIUS
var grade := Look.GRADE
var sharpen := Look.SHARPEN
var vignette := Look.VIGNETTE
var rim := Look.RIM
var floor_glow := 0.0
var env_reflect := Look.ENV_REFLECT
var floor_reflect := 0.0
var floor_reflect_matte := 0.0
var floor_mirror := false

# Light grid over the XZ plane: two RGBA texels per cell holding up to 8 light indices + 1.
var grid_w := 0
var grid_h := 0
var grid_x0 := 0.0
var grid_z0 := 0.0
var grid_inv_cell := 0.0
var grid := PackedByteArray()

# Geometry.
var opaque_batches: Array = []
var opaque_runs: Array = []
## See-through batches and model runs, in the order they were recorded.
var ordered: Array = []
## How many draws hold glowing geometry (floor reflections only bother when some do).
var glow_draws := 0
var polys_drawn := 0
var models_culled := 0

## The particle pool offered to this picture (drawn inside it so sparks glow in the bloom), the
## field it is measured in, and the shake offset (field units) to draw it at.
var particles: Particles = null
var particle_field := Vector2.ZERO
var particle_offset := Vector2.ZERO

## Microseconds spent recording (startFrame → finishFrame), for frame stats.
var record_usec := 0


func _init() -> void:
	lights.resize(MAX_LIGHTS * 8)
	grid.resize(GRID_MAX_W * 2 * GRID_MAX_H * 4)


func reset() -> void:
	skipped = false
	clip = false
	near = DEFAULT_NEAR
	clear_color = 0
	gradients.clear()
	light_count = 0
	fog_near = 1e8
	fog_far = 2e8
	fog_floor = 0.0
	exposure = 1.0
	bloom = 0.8
	bloom_threshold = Look.BLOOM_THRESHOLD
	bloom_threshold_hdr = Look.BLOOM_THRESHOLD_HDR
	bloom_radius = Look.BLOOM_RADIUS
	grade = Look.GRADE
	sharpen = Look.SHARPEN
	vignette = Look.VIGNETTE
	rim = Look.RIM
	floor_glow = 0.0
	env_reflect = Look.ENV_REFLECT
	floor_reflect = 0.0
	floor_reflect_matte = 0.0
	floor_mirror = false
	grid_w = 0
	grid_h = 0
	opaque_batches.clear()
	opaque_runs.clear()
	ordered.clear()
	glow_draws = 0
	polys_drawn = 0
	models_culled = 0
	particles = null
	record_usec = 0
