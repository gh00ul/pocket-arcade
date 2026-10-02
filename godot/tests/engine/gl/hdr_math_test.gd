extends PaTest
## engine/gl/HdrMathTest.kt (17 tests). The HDR maths pinned: the tone map and its inverse, the scene
## encoding, the bloom threshold with its knee, its caps, and the energy-conserving combine. The
## shader twins are assembled from the same [HdrLook] constants; the last three tests check the
## Godot shader sources (PostShaders, SceneShader) were assembled as intended.

const THRESHOLD := 1.0


func _near(expected: float, actual: float, eps: float = 1e-4, msg: String = "") -> void:
	assert_near(expected, actual, eps, msg)


# ------------------------------------------------------------------ tone map

func test_the_tone_map_fits_what_the_ldr_pipeline_always_used() -> void:
	_near(0.0, HdrMath.aces(0.0), 1e-3)
	# The published anchors of the Narkowicz fit.
	_near(0.80, HdrMath.aces(1.0), 5e-3)
	_near(0.886, HdrMath.aces(1.6), 5e-3)
	assert_near(1.0, HdrMath.aces(50.0), 0.0)
	var prev := 0.0
	for i in 401:
		var y := HdrMath.aces(i / 20.0)
		assert_true(y >= prev, "monotonic at %s" % (i / 20.0))
		prev = y


func test_the_inverse_tone_map_returns_what_the_tone_map_made() -> void:
	for i in 61:
		var x := i / 12.0  # 0..5 of linear light
		var y := HdrMath.aces(x)
		if y >= HdrLook.ACES_INV_MAX:
			continue
		_near(x, HdrMath.inverse_aces(y), 2e-3 * (1.0 + x * x), "x = %s" % x)


func test_the_inverse_is_clamped_where_the_curve_goes_flat() -> void:
	var top := HdrMath.inverse_aces(1.0)
	assert_true(is_finite(top))
	assert_near(top, HdrMath.inverse_aces(0.999), 0.0)
	_near(0.0, HdrMath.inverse_aces(0.0), 1e-6)
	_near(0.0, HdrMath.inverse_aces(-3.0), 1e-6)
	# The ceiling stays a modest number of stops above white, so a display-white gradient or particle can't blow the bloom up.
	assert_true(top < 8.0, "top %s" % top)


# ------------------------------------------------------------------ scene encoding

func test_the_scene_encoding_is_bounded_and_reversible() -> void:
	for m: float in [0.0, 0.01, 0.5, 1.0, 4.0, 10.0, 20.0]:
		var x := HdrMath.encode(m)
		assert_true(x >= 0.0 and x <= 1.0, "encoded %s = %s" % [m, x])
		_near(m, HdrMath.decode(x), 1e-3 * (1.0 + m * m), "m = %s" % m)
	_near(0.5, HdrMath.encode(1.0))


func test_overlapping_glows_cannot_decode_to_more_light_than_the_cap() -> void:
	var cap := HdrLook.ENC_MAX / (1.0 - HdrLook.ENC_MAX)
	assert_near(cap, HdrMath.decode(0.99), 1e-3)
	assert_near(cap, HdrMath.decode(5.0), 1e-3)
	assert_true(cap >= 20.0 and cap <= 30.0)


# ------------------------------------------------------------------ soft cap

func test_a_soft_cap_is_the_identity_then_never_exceeds_its_cap() -> void:
	for x: float in [0.0, 0.3, 1.2]:
		_near(x, HdrMath.soft_cap(x, 2.5, 1.2), 0.0)
	var prev := -1.0
	for i in 2001:
		var y := HdrMath.soft_cap(i / 100.0, 2.5, 1.2)
		assert_true(y <= 2.5, "never above the cap at %s: %s" % [i / 100.0, y])
		assert_true(y >= prev, "monotonic at %s" % (i / 100.0))
		prev = y
	assert_true(HdrMath.soft_cap(1000.0, 2.5, 1.2) > 2.49)


func test_a_soft_cap_has_no_kink_at_its_start() -> void:
	# Slope 1 on both sides of `start`: the ease begins gently instead of clipping.
	var h := 1e-3
	var below := (HdrMath.soft_cap(1.2, 2.5, 1.2) - HdrMath.soft_cap(1.2 - h, 2.5, 1.2)) / h
	var above := (HdrMath.soft_cap(1.2 + h, 2.5, 1.2) - HdrMath.soft_cap(1.2, 2.5, 1.2)) / h
	_near(1.0, below, 1e-2)
	_near(1.0, above, 2e-2)


# ------------------------------------------------------------------ bloom threshold, knee, cap

func test_nothing_glows_well_below_the_threshold() -> void:
	var start := THRESHOLD - HdrLook.BLOOM_KNEE
	for br: float in [0.0, 0.1, 0.3, start - 0.001]:
		_near(0.0, HdrMath.bloom_energy(br, THRESHOLD), 0.0, "br = %s" % br)


func test_the_knee_fades_glow_in_instead_of_popping_it_on() -> void:
	var k := HdrLook.BLOOM_KNEE
	var prev := 0.0
	var step := 0.0
	var br := THRESHOLD - k
	while br <= THRESHOLD + k:
		var e := HdrMath.bloom_energy(br, THRESHOLD)
		assert_true(e >= prev, "monotonic at %s" % br)
		# No jump between neighbouring brightnesses: the ramp is smooth.
		step = maxf(step, e - prev)
		prev = e
		br += 0.01
	assert_true(step < 0.02, "largest 0.01 step %s" % step)
	# At the threshold itself a quarter of the knee passes (the quadratic's value there).
	_near(k / 4.0, HdrMath.bloom_energy(THRESHOLD, THRESHOLD), 1e-4)
	# Past the knee it is exactly the excess over the threshold.
	_near(k + 0.7, HdrMath.bloom_energy(THRESHOLD + k + 0.7, THRESHOLD), 1e-4)
	_near(2.0, HdrMath.bloom_energy(THRESHOLD + 2.0, THRESHOLD), 1e-4)


func test_a_very_bright_texel_feeds_the_chain_no_more_than_the_cap() -> void:
	for br: float in [5.0, 10.0, 24.0, 1000.0]:
		var e := HdrMath.bloom_energy(br, THRESHOLD)
		assert_true(e <= HdrLook.BLOOM_INPUT_CAP, "energy at %s: %s" % [br, e])
	assert_true(HdrMath.bloom_energy(24.0, THRESHOLD) > HdrMath.bloom_energy(6.0, THRESHOLD))


func test_pale_surfaces_under_the_lit_ceiling_barely_glow() -> void:
	# The trap this design exists for: a lit pale tile at the very top of the paint's ceiling.
	var ceiling := HdrLook.LIT_CEILING
	var tile := HdrMath.bloom_energy(ceiling, THRESHOLD)
	assert_true(tile < 0.15, "a ceiling-lit tile feeds %s" % tile)
	# Well-lit paint under the ceiling feeds next to nothing.
	assert_true(HdrMath.bloom_energy(0.8, THRESHOLD) < 0.03)
	_near(0.0, HdrMath.bloom_energy(0.55, THRESHOLD), 0.0)
	# A glowing sign three times that bright feeds the chain many times more.
	assert_true(HdrMath.bloom_energy(2.5, THRESHOLD) > 8.0 * tile)


func test_lit_paint_never_climbs_past_its_ceiling() -> void:
	for m: float in [0.0, 0.4, HdrLook.LIT_START]:
		_near(m, HdrMath.lit_limit(m), 0.0)
	for i in 1001:
		assert_true(HdrMath.lit_limit(i / 20.0) <= HdrLook.LIT_CEILING)
	# Its ceiling lies under the point where the bloom starts to take real energy.
	assert_true(HdrLook.LIT_CEILING < THRESHOLD + HdrLook.BLOOM_KNEE / 2.0)


# ------------------------------------------------------------------ Karis average, energy conservation

func test_a_hot_texel_cannot_dominate_its_block() -> void:
	var plain := (100.0 + 0.05 * 3.0) / 4.0
	var w := HdrMath.karis_weight(100.0)
	var d := HdrMath.karis_weight(0.05)
	var karis := (100.0 * w + 0.05 * d * 3.0) / (w + 3.0 * d)
	assert_true(karis < plain / 8.0, "plain %s, karis %s" % [plain, karis])
	# Thin neon is dimmed, but mildly: a 3.0 tube in a block of dark keeps most of its plain average.
	var wt := HdrMath.karis_weight(3.0)
	var tube := (3.0 * wt + 0.05 * d * 3.0) / (wt + 3.0 * d)
	var plain_tube := (3.0 + 0.15) / 4.0
	assert_true(tube > plain_tube * 0.55, "tube %s vs plain %s" % [tube, plain_tube])


func test_bloom_added_is_capped_and_held_back_on_bright_pixels() -> void:
	# In the dark surroundings it lands in full (below the cap's start)...
	_near(0.8, HdrMath.bloom_added(0.8, 0.02), 0.01)
	# ...but on a pixel that is already bright it is reduced, so cores and pale surfaces don't wash out.
	assert_true(HdrMath.bloom_added(0.8, 1.5) < 0.6 * HdrMath.bloom_added(0.8, 0.0))
	# Nothing, however hot, adds more than the cap.
	for b: float in [3.0, 10.0, 200.0]:
		assert_true(HdrMath.bloom_added(b, 0.0) <= HdrLook.BLOOM_ADD_CAP)
	# And the more scene light there is, the less it adds.
	var prev := INF
	for s: float in [0.0, 0.25, 0.5, 1.0, 2.0, 4.0]:
		var a := HdrMath.bloom_added(1.0, s)
		assert_true(a <= prev)
		prev = a


# ------------------------------------------------------------------ the shaders are built from these numbers

func test_the_hdr_shaders_are_assembled_from_the_shared_sources() -> void:
	var scene := SceneShader.code(Blend.OPAQUE, true, true, false, false)
	var hdr_sources := {
		"scene": scene,
		"background": PostShaders.code("background"),
		"particles": PostShaders.code("particle"),
		"bright": PostShaders.code("bright_hdr"),
		"glare": PostShaders.code("glare"),
		"composite": PostShaders.code("composite_hdr"),
	}
	for shader_name: String in hdr_sources:
		var src: String = hdr_sources[shader_name]
		assert_true(src.begins_with("shader_type "), "%s starts with its shader type" % shader_name)
		assert_false(src.contains("$") or src.contains("%s") or src.contains("%d"), "%s has an unresolved template" % shader_name)
	# Godot: one scene source for both pictures (build-13 compiled it twice, with HDR_OUT defined or
	# not); the HDR paths are behind the data texture's flag, and the LDR tone map is still there.
	assert_true(scene.contains("bool hdr = g_fog.w > 0.5;"))
	assert_true(scene.contains("hdr_encode(col + refl) : tonemap(col + refl, exposure)"))
	assert_false(scene.contains("HDR_OUT"))


func test_the_shaders_carry_the_same_numbers_as_the_kotlin_mirrors() -> void:
	var f := PostShaders.f
	assert_true(PostShaders.hdr_glsl().contains("const float ENC_MAX = %s;" % f.call(HdrLook.ENC_MAX)))
	var bright := PostShaders.code("bright_hdr")
	assert_true(bright.contains("max3(c) * %s" % f.call(HdrLook.KARIS_STRENGTH)))
	assert_true(bright.contains("float knee = %s;" % f.call(HdrLook.BLOOM_KNEE)))
	assert_true(bright.contains("%s, %s" % [f.call(HdrLook.BLOOM_INPUT_CAP), f.call(HdrLook.BLOOM_INPUT_START)]))
	var comp := PostShaders.code("composite_hdr")
	assert_true(comp.contains("%s, %s" % [f.call(HdrLook.BLOOM_ADD_CAP), f.call(HdrLook.BLOOM_ADD_START)]))
	assert_true(comp.contains("max3(c) * %s" % f.call(HdrLook.BLOOM_SELF_SHADOW)))
	var scene := SceneShader.code(Blend.OPAQUE, true, true, false, false)
	assert_true(scene.contains("const float EMISSIVE_GAIN = %s;" % f.call(HdrLook.EMISSIVE_GAIN)))
	assert_true(scene.contains("%s, %s" % [f.call(HdrLook.LIT_CEILING), f.call(HdrLook.LIT_START)]))
	assert_true(PostShaders.code("glare").contains("i <= %d" % HdrLook.GLARE_TAPS))
	# The literals are the values themselves (6.0, 0.0022...), not rounded.
	assert_eq("6.0", f.call(6.0))
	assert_eq("0.0022", f.call(0.0022))


func test_the_ldr_shaders_are_the_ones_that_always_shipped() -> void:
	# The HDR work touched none of the LDR-only sources.
	assert_true(PostShaders.code("composite").contains("smoothstep(0.35, 0.85, length(d * vec2(1.0, 0.8)))"))
	assert_true(PostShaders.code("bright").contains("const float knee = 0.1;"))
	assert_false(PostShaders.code("composite").contains("hdr_decode"))
	assert_true(absf(HdrLook.ENC_MAX - 0.96) < 1e-6)
