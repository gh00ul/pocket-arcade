extends PaTest
## engine/FlickTrackerTest.kt: the host forwards every batched touch sample, so the tracker sees
## anything from one sample per frame to a fast digitiser's 240 Hz. The same gesture must read the
## same velocity either way.


## Feeds a vertical gesture whose height is y(t) at time t (seconds), sampled at [param hz] to [param end].
func sample(hz: int, end: float, y: Callable) -> FlickTracker:
	var tracker := FlickTracker.new()
	tracker.reset(0.0, y.call(0.0), 0)
	var n := MathUtil.round_to_int(end * hz)
	for k in range(1, n + 1):
		var t := k / float(hz)
		tracker.add(0.0, y.call(t), MathUtil.round_to_int(t * 1000.0))
	return tracker


func vy(tracker: FlickTracker) -> float:
	return tracker.velocity().y


func test_the_same_flick_reads_the_same_at_60_and_240_hz() -> void:
	var flick := func(t: float) -> float: return -1400.0 * t
	var slow := vy(sample(60, 0.3, flick))
	var dense := vy(sample(240, 0.3, flick))
	assert_near(-1400.0, slow, 100.0, "60 Hz")
	assert_near(-1400.0, dense, 30.0, "240 Hz")
	assert_true(absf(slow - dense) < 0.08 * absf(dense), "60 Hz %f vs 240 Hz %f" % [slow, dense])


func test_a_snap_after_a_slow_wind_up_reads_fast_at_every_rate() -> void:
	# 300 ms creeping at 40 units/s, then 60 ms snapping at 1500 units/s.
	var wind := 0.3
	var snap := 0.06
	var flick := func(t: float) -> float:
		return -40.0 * t if t <= wind else -40.0 * wind - 1500.0 * (minf(t, wind + snap) - wind)
	for hz: int in [60, 120, 240, 480]:
		var v := vy(sample(hz, wind + snap, flick))
		assert_true(v < -850.0 and v > -1500.0, "%d Hz read %f" % [hz, v])


func test_a_finger_that_stopped_before_letting_go_reads_slow() -> void:
	var flick := func(t: float) -> float: return -1500.0 * t if t <= 0.1 else -150.0
	for hz: int in [60, 240]:
		var v := vy(sample(hz, 0.25, flick))
		assert_true(absf(v) < 60.0, "%d Hz read %f" % [hz, v])


func test_the_window_survives_a_dense_burst() -> void:
	var v := vy(sample(480, 0.25, func(t: float) -> float: return -900.0 * t))
	assert_near(-900.0, v, 40.0)


func test_low_rate_input_keeps_its_old_reading() -> void:
	var tracker := FlickTracker.new()
	tracker.reset(0.0, 0.0, 0)
	for k in range(1, 7):
		tracker.add(0.0, -1200.0 * k * 0.012, k * 12)
	assert_near(-1200.0, vy(tracker), 60.0)
