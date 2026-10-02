class_name GlCaps
extends RefCounted
## engine/gl/HdrPipeline.kt GlCaps: what the GL driver offers that the HDR picture cares about, read
## from its version and extension strings. Pure (no GL calls), so [HdrPlan]'s decision is tested.
##
## In Godot the version string is RenderingServer.get_video_adapter_api_version() and the
## extension list is not exposed; [method Gfx.caps] builds one from what the device is known to do
## (desktop GL always renders to float targets; on GLES a one-frame probe checks, see
## [method Gfx.probe_float_targets]).

var max_samples: int
## OpenGL ES 3.2 folds EXT_color_buffer_float into the core.
var es32 := false
## Half and full float colour buffers, including multisampled ones.
var color_buffer_float := false
## RGBA16F colour buffers (single-sampled only, as the extension promises).
var color_buffer_half_float := false

static var _es_version: RegEx = null


func _init(version: String, extensions: String, p_max_samples: int) -> void:
	max_samples = p_max_samples
	var tokens := {}
	for t in extensions.split(" ", false):
		tokens[t] = true
	if _es_version == null:
		_es_version = RegEx.create_from_string("OpenGL ES (\\d+)\\.(\\d+)")
	var m := _es_version.search(version)
	if m != null:
		var major := m.get_string(1).to_int()
		var minor := m.get_string(2).to_int()
		es32 = major > 3 or (major == 3 and minor >= 2)
	color_buffer_float = es32 or tokens.has("GL_EXT_color_buffer_float")
	color_buffer_half_float = tokens.has("GL_EXT_color_buffer_half_float")


## Whether an RGBA16F target can be rendered to at all.
func float_targets() -> bool:
	return color_buffer_float or color_buffer_half_float


## Whether a multisampled RGBA16F renderbuffer may be offered.
func float_msaa() -> bool:
	return color_buffer_float and max_samples > 1
