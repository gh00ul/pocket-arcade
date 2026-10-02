extends PaTest
## engine/gl/ScalePacerTest.kt (16 tests). Times are microseconds (build-13: nanoseconds).

const MS := 1000


## Feeds [param n] frames [param frame_ms] apart starting after [param t0]; returns the time of the last one.
func _run(p: ScalePacer, t0: int, n: int, frame_ms: float, gpu_ms: float = -1.0) -> int:
	var t := t0
	for i in n:
		t += int(frame_ms * MS)
		p.on_frame(t, gpu_ms)
	return t


func test_a_slow_startup_burst_is_ignored() -> void:
	var p := ScalePacer.new()
	p.reset(0)
	# 1.9 s of 40 ms frames while shaders compile and textures upload.
	_run(p, 0, 47, 40.0)
	assert_near(0.8, p.scale, 1e-6)


func test_sustained_slow_frames_step_down_to_the_floor() -> void:
	var p := ScalePacer.new()
	p.reset(0)
	var t := _run(p, 0, 130, 16.6)  # past the warm-up
	t = _run(p, t, 30, 30.0)
	assert_near(0.8, p.scale, 1e-6)
	t = _run(p, t, 1, 30.0)
	assert_near(0.7, p.scale, 1e-6)
	_run(p, t, 400, 40.0)
	assert_near(0.5, p.scale, 1e-6)


func test_hitches_and_pauses_do_not_block_recovery() -> void:
	var p := ScalePacer.new(0.5, 0.8, 1.0, 0.5)
	p.reset(0)
	var t := _run(p, 0, 130, 16.6)
	# Fast frames with a stray 30 ms hitch every 40 frames and a 300 ms pause: still climbs,
	# a step per 120-frame window (about 2 s), to the 0.8 ceiling.
	for k in 12:
		t = _run(p, t, 39, 16.6)
		t = _run(p, t, 1, 30.0)
		if k == 5:
			t = _run(p, t, 1, 300.0)
	assert_near(0.8, p.scale, 1e-6)
	# Without a GPU timer it never goes past the ceiling.
	_run(p, t, 1000, 16.6)
	assert_near(0.8, p.scale, 1e-6)


func test_a_known_light_gpu_load_raises_quickly_and_above_the_ceiling() -> void:
	var p := ScalePacer.new(0.5, 0.8, 1.0, 0.5)
	p.reset(0)
	var t := _run(p, 0, 121, 16.6, 3.0)
	# Quarter windows once the GPU time says there is plenty of room.
	t = _run(p, t, 30 * 3, 16.6, 3.0)
	assert_near(0.8, p.scale, 1e-4)
	# 3 ms at 0.8 predicts under 5 ms at 1.0: allowed above the usual ceiling.
	_run(p, t, 300, 16.6, 3.0)
	assert_near(1.0, p.scale, 1e-4)


func test_a_heavy_gpu_load_stays_at_the_ceiling() -> void:
	var p := ScalePacer.new()
	p.reset(0)
	# 9 ms at 0.8 would be ~14 ms at 0.9: over the two-thirds budget.
	_run(p, 0, 2000, 16.6, 9.0)
	assert_near(0.8, p.scale, 1e-4)
	assert_near(14.06, p.predict(9.0, 0.8, 1.0), 0.01)


func test_bouncing_off_the_same_scale_backs_off() -> void:
	var p := ScalePacer.new()
	p.reset(0)
	var t := _run(p, 0, 130, 16.6)
	t = _run(p, t, 32, 30.0)
	assert_near(0.7, p.scale, 1e-6)
	# Trying 0.8 again now takes two windows, not one.
	t = _run(p, t, 125, 16.6)
	assert_near(0.7, p.scale, 1e-6)
	t = _run(p, t, 125, 16.6)
	assert_near(0.8, p.scale, 1e-6)
	# Too slow again: the next retry takes four windows.
	t = _run(p, t, 32, 30.0)
	assert_near(0.7, p.scale, 1e-6)
	t = _run(p, t, 360, 16.6)
	assert_near(0.7, p.scale, 1e-6)
	_run(p, t, 130, 16.6)
	assert_near(0.8, p.scale, 1e-6)


func test_a_new_screen_starts_over_at_the_default() -> void:
	var p := ScalePacer.new()
	p.reset(0)
	var t := _run(p, 0, 130, 16.6)
	t = _run(p, t, 200, 40.0)
	assert_near(0.5, p.scale, 1e-6)
	p.reset(t)
	assert_near(0.8, p.scale, 1e-6)
	# A surface change keeps the scale but still skips the warm-up.
	p.reset(t, true)
	_run(p, t, 40, 40.0)
	assert_near(0.8, p.scale, 1e-6)


# ------------------------------------------------------------------ the quality ladder

func _ladder_pacer(start_rung: int = 0, top: int = 0, bottom: int = ScalePacer.INT_MAX, start: float = 0.8) -> ScalePacer:
	return ScalePacer.new(0.5, 0.8, 1.0, start, 2000000, 120, 30, GfxQuality.LADDER, start_rung, top, bottom)


## Runs the pacer past its warm-up with fast frames; returns the time.
func _warm_up(p: ScalePacer) -> int:
	p.reset(0)
	return _run(p, 0, 130, 16.6)


func test_still_slow_at_the_floor_steps_down_a_rung_at_a_time() -> void:
	var p := _ladder_pacer()
	var t := _warm_up(p)
	# 31 slow frames per step: three scale steps to the 0.5 floor first, resolution before effects.
	t = _run(p, t, 31 * 3, 40.0)
	assert_near(0.5, p.scale, 1e-6)
	assert_eq(0, p.rung)
	t = _run(p, t, 31, 40.0)
	assert_eq(1, p.rung)
	t = _run(p, t, 31, 40.0)
	assert_eq(2, p.rung)
	# Rung 3 has a lower floor: the scale gives way once more before rung 4 comes.
	t = _run(p, t, 31, 40.0)
	assert_eq(3, p.rung)
	assert_near(0.5, p.scale, 1e-6)
	t = _run(p, t, 31, 40.0)
	assert_near(0.45, p.scale, 1e-6)
	assert_eq(3, p.rung)
	t = _run(p, t, 31, 40.0)
	assert_eq(4, p.rung)
	t = _run(p, t, 31, 40.0)
	assert_near(0.4, p.scale, 1e-6)
	# The bottom of the ladder is the end of the road.
	_run(p, t, 2000, 40.0)
	assert_eq(4, p.rung)
	assert_near(0.4, p.scale, 1e-6)


func test_a_tier_can_forbid_giving_up_effects() -> void:
	var p := _ladder_pacer(0, 0, 0)
	var t := _warm_up(p)
	_run(p, t, 3000, 40.0)
	assert_eq(0, p.rung)
	assert_near(0.5, p.scale, 1e-6)


func test_a_long_smooth_stretch_at_the_ceiling_climbs_one_rung_up() -> void:
	var p := _ladder_pacer(3, 0, ScalePacer.INT_MAX, 0.5)
	var t := _warm_up(p)
	# Two windows lift the scale from 0.5 to the rung's 0.7 ceiling, then three more windows
	# (360 smooth frames) at the ceiling earn a rung.
	t = _run(p, t, 560, 16.6)
	assert_near(0.7, p.scale, 1e-6)
	assert_eq(3, p.rung)
	t = _run(p, t, 60, 16.6)
	assert_eq(2, p.rung)
	# And it does not run on: the next rung has to be earned the same way.
	t = _run(p, t, 200, 16.6)
	assert_eq(2, p.rung)
	_run(p, t, 1000, 16.6)
	assert_eq(0, p.rung)
	assert_near(0.8, p.scale, 1e-6)


func test_a_tier_caps_how_high_it_climbs() -> void:
	var p := _ladder_pacer(3, 2)
	var t := _warm_up(p)
	_run(p, t, 5000, 16.6)
	assert_eq(2, p.rung)


func test_a_rung_that_was_just_too_slow_takes_twice_as_long_to_return_to() -> void:
	var p := _ladder_pacer(2, 0, ScalePacer.INT_MAX, 0.5)
	var t := _warm_up(p)
	# Slow at rung 2's floor: down to rung 3.
	t = _run(p, t, 31, 40.0)
	assert_eq(3, p.rung)
	# Two windows to the 0.7 ceiling, then 360 frames would normally do; now it takes 720.
	t = _run(p, t, 120 * 2 + 120 * 5, 16.6)
	assert_eq(3, p.rung)
	_run(p, t, 130, 16.6)
	assert_eq(2, p.rung)


func test_a_known_heavy_gpu_load_holds_the_rung_down() -> void:
	var p := _ladder_pacer(3)
	var t := _warm_up(p)
	# Frames make 60 fps but the GPU is busy for 12 ms of each: no headroom for more effects.
	_run(p, t, 5000, 16.6, 12.0)
	assert_eq(3, p.rung)


func test_a_thirty_fps_cap_doubles_the_frame_time_limits() -> void:
	var slow := ScalePacer.new()
	slow.reset(0)
	var t := _run(slow, 0, 130, 16.6)
	# At the 60 fps limits, 33 ms frames are slow.
	_run(slow, t, 40, 33.0)
	assert_near(0.7, slow.scale, 1e-6)

	var capped := ScalePacer.new()
	capped.set_target_fps(30)
	capped.reset(0)
	t = _run(capped, 0, 65, 33.0)
	t = _run(capped, t, 400, 33.0)
	assert_near(0.8, capped.scale, 1e-6)
	# 60 ms frames are slow even against 30 fps.
	_run(capped, t, 40, 60.0)
	assert_near(0.7, capped.scale, 1e-6)


func test_a_thirty_fps_cap_counts_thirty_fps_frames_as_fast_for_climbing() -> void:
	var p := ScalePacer.new(0.5, 0.8, 1.0, 0.5)
	p.set_target_fps(30)
	p.reset(0)
	# 33 ms frames: fast at a 30 fps target, so the scale climbs to the ceiling.
	var t := _run(p, 0, 65, 33.0)
	_run(p, t, 800, 33.0)
	assert_near(0.8, p.scale, 1e-6)


func test_changing_the_tier_moves_the_rung_into_range_without_touching_the_scale_unduly() -> void:
	var p := _ladder_pacer()
	_warm_up(p)
	assert_near(0.8, p.scale, 1e-6)
	p.set_limits(2, 4)
	assert_eq(2, p.rung)
	# Rung 2's ceiling is 0.7.
	assert_near(0.7, p.scale, 1e-6)
	p.set_limits(0, 0)
	assert_eq(0, p.rung)
	p.jump_to(3)
	assert_eq(0, p.rung)
	p.set_limits(0, 4)
	p.jump_to(3)
	assert_eq(3, p.rung)
