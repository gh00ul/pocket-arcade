extends PaTest
## engine/gl/GfxQualityTest.kt (15 tests). FrameGate times are microseconds (build-13: nanoseconds).


func after_each() -> void:
	GfxQuality.frame_cap = 0
	GfxQuality.tier = GfxQuality.Tier.AUTO


func test_rung_zero_is_how_the_renderer_looked_before_tiers_existed() -> void:
	var r: GfxQuality.Rung = GfxQuality.LADDER[0]
	assert_near(0.5, r.scale_floor, 0.0)
	assert_near(0.8, r.scale_ceiling, 0.0)
	assert_near(1.0, r.scale_boost, 0.0)
	assert_eq(4, r.msaa)
	assert_eq(4, r.bloom_octaves)
	assert_eq(GfxQuality.Reflections.MIRROR, r.reflections)
	assert_eq(RenderPass.MAX_LIGHTS, r.lights)
	# And it is where the HDR picture and its whole cinematic finish live.
	assert_true(r.hdr)
	assert_true(r.glare)
	assert_true(r.film)


func test_the_finish_goes_first_then_hdr_then_multisampling() -> void:
	var l := GfxQuality.LADDER
	# Rung 1 keeps HDR and full multisampling but drops the glare and the film finish.
	assert_true(l[1].hdr)
	assert_false(l[1].glare)
	assert_false(l[1].film)
	assert_eq(4, l[1].msaa)
	# Rung 2 is the LDR picture; multisampling falls with it, never before it.
	assert_false(l[2].hdr)
	assert_true(l[2].msaa < l[1].msaa)
	for i in l.size():
		if l[i].msaa < 4:
			assert_false(l[i].hdr, "HDR with reduced msaa at %d" % i)


func test_battery_never_starts_on_an_hdr_rung() -> void:
	for i in range(GfxQuality.BATTERY_TOP, GfxQuality.LADDER.size()):
		assert_false(GfxQuality.LADDER[i].hdr, "rung %d" % i)
	assert_false(GfxQuality.LADDER[GfxQuality.start_rung(GfxQuality.Tier.BATTERY, 0)].hdr)


func test_the_finish_levers_need_hdr() -> void:
	for r: GfxQuality.Rung in GfxQuality.LADDER:
		if not r.hdr:
			assert_false(r.glare)
			assert_false(r.film)


func test_every_rung_is_no_better_than_the_one_above() -> void:
	for i in range(1, GfxQuality.LADDER.size()):
		var up: GfxQuality.Rung = GfxQuality.LADDER[i - 1]
		var r: GfxQuality.Rung = GfxQuality.LADDER[i]
		assert_true(r.scale_floor <= up.scale_floor, "floor at %d" % i)
		assert_true(r.scale_ceiling <= up.scale_ceiling, "ceiling at %d" % i)
		assert_true(r.scale_boost <= up.scale_boost, "boost at %d" % i)
		assert_true(r.scale_boost >= r.scale_ceiling, "boost above ceiling at %d" % i)
		assert_true(r.msaa <= up.msaa, "msaa at %d" % i)
		assert_true(r.bloom_octaves <= up.bloom_octaves, "bloom at %d" % i)
		assert_true(r.reflections >= up.reflections, "reflections at %d" % i)
		assert_true(r.lights <= up.lights, "lights at %d" % i)
		assert_true(not r.hdr or up.hdr, "hdr at %d" % i)
		assert_true(not r.glare or up.glare, "glare at %d" % i)
		assert_true(not r.film or up.film, "film at %d" % i)
		assert_true(r.scale_floor < r.scale_ceiling, "floor under ceiling at %d" % i)


func test_levers_stay_within_what_the_renderer_can_do() -> void:
	for r: GfxQuality.Rung in GfxQuality.LADDER:
		assert_true(r.msaa == 0 or r.msaa == 2 or r.msaa == 4)
		assert_true(r.bloom_octaves >= 2 and r.bloom_octaves <= 4)
		assert_true(r.lights >= 1 and r.lights <= RenderPass.MAX_LIGHTS)


func test_tiers_set_the_range_of_the_ladder() -> void:
	var last := GfxQuality.LADDER.size() - 1
	assert_eq(0, GfxQuality.top_rung(GfxQuality.Tier.AUTO))
	assert_eq(last, GfxQuality.bottom_rung(GfxQuality.Tier.AUTO))
	assert_eq(GfxQuality.BATTERY_TOP, GfxQuality.top_rung(GfxQuality.Tier.BATTERY))
	assert_eq(last, GfxQuality.bottom_rung(GfxQuality.Tier.BATTERY))
	assert_eq(0, GfxQuality.top_rung(GfxQuality.Tier.QUALITY))
	assert_eq(0, GfxQuality.bottom_rung(GfxQuality.Tier.QUALITY))


func test_starting_rung_follows_the_tier_and_the_device() -> void:
	# AUTO trusts the device; BATTERY never starts above its top; QUALITY always starts at the best.
	assert_eq(0, GfxQuality.start_rung(GfxQuality.Tier.AUTO, 0))
	assert_eq(3, GfxQuality.start_rung(GfxQuality.Tier.AUTO, 3))
	assert_eq(GfxQuality.BATTERY_TOP, GfxQuality.start_rung(GfxQuality.Tier.BATTERY, 0))
	assert_eq(4, GfxQuality.start_rung(GfxQuality.Tier.BATTERY, 4))
	assert_eq(0, GfxQuality.start_rung(GfxQuality.Tier.QUALITY, 4))


func test_the_device_suggestion_is_cautious_about_old_and_software_renderers() -> void:
	var last := GfxQuality.LADDER.size() - 1
	assert_eq(0, GfxQuality.device_rung("Adreno (TM) 740", false))
	assert_eq(0, GfxQuality.device_rung("Adreno (TM) 618", false))
	assert_eq(0, GfxQuality.device_rung("Mali-G78", false))
	assert_eq(0, GfxQuality.device_rung("Android Emulator OpenGL ES Translator (Apple M2 Pro)", false))
	assert_eq(0, GfxQuality.device_rung("", false))
	assert_eq(2, GfxQuality.device_rung("Adreno (TM) 306", false))
	assert_eq(2, GfxQuality.device_rung("Adreno (TM) 420", false))
	assert_eq(2, GfxQuality.device_rung("Mali-T760", false))
	assert_eq(2, GfxQuality.device_rung("Mali-400 MP", false))
	assert_eq(2, GfxQuality.device_rung("PowerVR SGX 544MP", false))
	assert_eq(2, GfxQuality.device_rung("Adreno (TM) 740", true))
	assert_eq(last, GfxQuality.device_rung("Google SwiftShader", false))
	assert_eq(last, GfxQuality.device_rung("llvmpipe (LLVM 15.0.7, 256 bits)", true))
	assert_eq(last, GfxQuality.device_rung("ANGLE (Google, Vulkan 1.3.0 (SwiftShader Device (Subzero)))", false))


func test_the_cap_is_thirty_or_sixty_and_auto_means_sixty() -> void:
	GfxQuality.frame_cap = 0
	assert_eq(60, GfxQuality.effective_cap())
	GfxQuality.frame_cap = 30
	assert_eq(30, GfxQuality.effective_cap())
	GfxQuality.frame_cap = 60
	assert_eq(60, GfxQuality.effective_cap())
	GfxQuality.frame_cap = 45  # not an offered value
	assert_eq(60, GfxQuality.effective_cap())


func test_a_cap_only_bites_on_a_display_fast_enough_for_it() -> void:
	assert_true(GfxQuality.cap_applies(60, 120.0))
	assert_true(GfxQuality.cap_applies(60, 144.0))
	assert_true(GfxQuality.cap_applies(30, 60.0))
	assert_true(GfxQuality.cap_applies(30, 90.0))
	assert_false(GfxQuality.cap_applies(60, 60.0))
	# 90 Hz would be pushed down to 45 by the vsync grid: better left alone.
	assert_false(GfxQuality.cap_applies(60, 90.0))


# ------------------------------------------------------------------ FrameGate

## How many of the frames a [param hz] display offers for [param seconds] the gate lets through.
func _accepted(cap: int, hz: float, seconds: int = 4, jitter_usec: int = 0) -> int:
	var gate := FrameGate.new()
	var period := int(1e6 / hz)
	var frames := int(hz * seconds)
	var taken := 0
	for k in range(1, frames + 1):
		# A small deterministic wobble, as a real choreographer has.
		var wobble := 0 if jitter_usec == 0 else ((k * 7919) % 5 - 2) * jitter_usec / 2
		if gate.due(k * period + wobble, cap, hz):
			taken += 1
	return taken


func test_a_sixty_fps_cap_on_a120_hz_display_draws_every_second_frame() -> void:
	assert_eq(240, _accepted(60, 120.0, 4))
	assert_eq(240, _accepted(60, 120.0, 4, 1000))


func test_a_thirty_fps_cap_draws_every_fourth_frame_on120_hz_and_every_second_on60() -> void:
	assert_eq(120, _accepted(30, 120.0, 4))
	assert_eq(120, _accepted(30, 60.0, 4))
	assert_eq(120, _accepted(30, 120.0, 4, 1000))
	assert_eq(120, _accepted(30, 90.0, 4))


func test_no_cap_where_the_display_is_not_fast_enough() -> void:
	assert_eq(240, _accepted(60, 60.0, 4))
	assert_eq(360, _accepted(60, 90.0, 4))


func test_the_gate_says_how_long_to_wait() -> void:
	var gate := FrameGate.new()
	var ms := 1000
	# Nothing taken yet: due at once.
	assert_eq(0, gate.wait_usec(5 * ms, 60, 120.0))
	gate.take(100 * ms)
	# 60 fps is 16.67 ms; less the 3 ms slack, 13.67 ms must pass (13 666 µs; build-13: 13 666 666 ns).
	assert_eq(13666, gate.wait_usec(100 * ms, 60, 120.0))
	assert_eq(3666, gate.wait_usec(110 * ms, 60, 120.0))
	assert_eq(0, gate.wait_usec(114 * ms, 60, 120.0))
	# A clock that stepped back never asks for more than one interval.
	assert_eq(13666, gate.wait_usec(50 * ms, 60, 120.0))
	# Uncapped displays never wait.
	assert_eq(0, gate.wait_usec(100 * ms, 60, 60.0))
