extends PaTest
## engine/gl/HdrPlanTest.kt: 9 of its 11 tests. The last two (aFailureBlamedOnHdrRestartsWithout-
## UsingUpASlot, aFatalFailureIsNeverBlamedAway) test RestartPolicy, which is dropped with
## GlThread: Godot owns the GL context and its loss (see docs/parity/notes/look.md).

const FLOAT := "GL_OES_texture_float GL_EXT_color_buffer_float GL_EXT_texture_filter_anisotropic"
const HALF := "GL_EXT_color_buffer_half_float GL_OES_texture_half_float"


func before_each() -> void:
	HdrGuard.reset()


func after_each() -> void:
	HdrGuard.reset()


func _caps(version: String = "OpenGL ES 3.1 V@1", ext: String = "", max_samples: int = 4) -> GlCaps:
	return GlCaps.new(version, ext, max_samples)


func _choose(c: GlCaps, tier: int = GfxQuality.Tier.AUTO, rung_hdr: bool = true, samples: int = 4,
		blocked: bool = false, msaa_blocked: bool = false) -> int:
	return HdrPlan.choose(c, tier, rung_hdr, samples, blocked, msaa_blocked)


# ------------------------------------------------------------------ capabilities

func test_float_support_is_read_from_the_extension_list() -> void:
	assert_true(_caps("OpenGL ES 3.1 V@1", FLOAT).color_buffer_float)
	assert_true(_caps("OpenGL ES 3.1 V@1", FLOAT).float_msaa())
	assert_false(_caps().float_targets())
	# Whole-token matching: a longer name that merely starts the same is not the extension.
	assert_false(_caps("OpenGL ES 3.1 V@1", "GL_EXT_color_buffer_float_fake").color_buffer_float)
	var half := _caps("OpenGL ES 3.1 V@1", HALF)
	assert_true(half.color_buffer_half_float)
	assert_true(half.float_targets())
	# Half float renders single-sampled only.
	assert_false(half.float_msaa())


func test_open_gl_es32_folds_float_targets_into_the_core() -> void:
	assert_true(_caps("OpenGL ES 3.2 V@ 502.0").color_buffer_float)
	assert_true(_caps("OpenGL ES 3.2 Mesa 24.0").float_msaa())
	assert_true(_caps("OpenGL ES 4.0").color_buffer_float)
	assert_false(_caps("OpenGL ES 3.1 V@ 502.0").color_buffer_float)
	assert_false(_caps("OpenGL ES 3.0 V@ 502.0").color_buffer_float)
	assert_false(_caps("something unexpected").color_buffer_float)


func test_float_multisampling_needs_more_than_one_sample() -> void:
	assert_false(_caps("OpenGL ES 3.1 V@1", FLOAT, 0).float_msaa())
	assert_false(_caps("OpenGL ES 3.1 V@1", FLOAT, 1).float_msaa())
	assert_true(_caps("OpenGL ES 3.1 V@1", FLOAT, 2).float_msaa())


# ------------------------------------------------------------------ the plan

func test_a_device_that_passes_everything_gets_hdr_with_multisampling() -> void:
	assert_eq(Pipeline.HDR_MSAA, _choose(_caps("OpenGL ES 3.1 V@1", FLOAT)))
	assert_eq(Pipeline.HDR_MSAA, _choose(_caps("OpenGL ES 3.1 V@1", FLOAT), GfxQuality.Tier.QUALITY))
	assert_eq(Pipeline.HDR_MSAA, _choose(_caps("OpenGL ES 3.1 V@1", FLOAT), GfxQuality.Tier.AUTO, true, 2))


func test_battery_and_the_lower_rungs_keep_the_ldr_picture() -> void:
	assert_eq(Pipeline.LDR, _choose(_caps("OpenGL ES 3.1 V@1", FLOAT), GfxQuality.Tier.BATTERY))
	assert_eq(Pipeline.LDR, _choose(_caps("OpenGL ES 3.1 V@1", FLOAT), GfxQuality.Tier.AUTO, false))
	assert_eq(Pipeline.LDR, _choose(_caps("OpenGL ES 3.1 V@1", FLOAT), GfxQuality.Tier.BATTERY, false))


func test_a_device_without_float_targets_keeps_the_ldr_picture() -> void:
	assert_eq(Pipeline.LDR, _choose(_caps()))
	assert_eq(Pipeline.LDR, _choose(null))
	assert_eq(Pipeline.LDR, _choose(_caps("OpenGL ES 3.1 V@1", "GL_OES_texture_float"), GfxQuality.Tier.AUTO, true, 0))


func test_where_multisampling_is_wanted_hdr_needs_float_multisampling() -> void:
	# Half float alone can't multisample: better the anti-aliased LDR picture than jagged HDR.
	assert_eq(Pipeline.LDR, _choose(_caps("OpenGL ES 3.1 V@1", HALF), GfxQuality.Tier.AUTO, true, 4))
	# With no multisampling wanted, single-sampled half float will do.
	assert_eq(Pipeline.HDR, _choose(_caps("OpenGL ES 3.1 V@1", HALF), GfxQuality.Tier.AUTO, true, 0))
	assert_eq(Pipeline.HDR, _choose(_caps("OpenGL ES 3.1 V@1", FLOAT), GfxQuality.Tier.AUTO, true, 0))
	# A float multisampled framebuffer that failed to build rules HDR out where multisampling is wanted.
	assert_eq(Pipeline.LDR, _choose(_caps("OpenGL ES 3.1 V@1", FLOAT), GfxQuality.Tier.AUTO, true, 4, false, true))
	assert_eq(Pipeline.HDR, _choose(_caps("OpenGL ES 3.1 V@1", FLOAT), GfxQuality.Tier.AUTO, true, 0, false, true))


func test_a_blocked_pipeline_stays_blocked() -> void:
	assert_eq(Pipeline.LDR, _choose(_caps("OpenGL ES 3.1 V@1", FLOAT), GfxQuality.Tier.AUTO, true, 4, true))
	assert_eq(Pipeline.LDR, _choose(_caps("OpenGL ES 3.1 V@1", FLOAT), GfxQuality.Tier.QUALITY, true, 0, true))


func test_the_guard_remembers_why_it_blocked_and_only_resets_on_request() -> void:
	assert_false(HdrGuard.blocked)
	HdrGuard.block_msaa("float msaa")
	assert_true(HdrGuard.msaa_blocked)
	assert_false(HdrGuard.blocked)
	assert_eq("float msaa", HdrGuard.reason)
	HdrGuard.block("shader")
	HdrGuard.block("later")
	assert_true(HdrGuard.blocked)
	# The reason that ended it is the one kept for the log.
	assert_eq("shader", HdrGuard.reason)
	HdrGuard.reset()
	assert_false(HdrGuard.blocked)
	assert_false(HdrGuard.msaa_blocked)
	assert_eq("", HdrGuard.reason)
