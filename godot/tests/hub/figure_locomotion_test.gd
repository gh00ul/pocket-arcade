extends PaTest
## hub/FigureLocomotionTest.kt: walking and running: the stride follows the ground covered so feet
## stay planted, it opens out from a walk into a run, stopping plants the feet together, and the
## body leans and turns smoothly.

const DT := RigChecks.DT


func after_each() -> void:
	FigureAnim.reduce_motion = false


## Walks [param a] along +z at [param speed] for [param secs] from [param z0], calling [param each]
## (with z) after every step; returns the final z.
func _walk(a: FigureAnim, speed: float, secs: float, z0: float = 0.0, pose: int = Pose.WALK, each: Callable = Callable()) -> float:
	var z := z0
	for i in int(secs / DT):
		z += speed * DT
		a.update(DT, 0.0, z, 0.0, pose)
		if each.is_valid():
			each.call(z)
	return z


## The planted foot's speed over the ground, as a fraction of [param speed]: (mean, mean of the
## magnitude) over [param secs] of walking.
func _slide(speed: float, secs: float = 6.0) -> Vector2:
	var a := FigureAnim.new()
	var z := _walk(a, speed, 1.5)
	# A plain Array: the lambda below writes into it (a captured packed array would be a copy).
	var prev_forward := [Gait.foot_forward(a.leg_pitch[0]), Gait.foot_forward(a.leg_pitch[1])]
	var acc := [0.0, 0.0, 0]
	_walk(a, speed, secs, z, Pose.WALK, func(_z: float) -> void:
		for s in 2:
			var fwd := Gait.foot_forward(a.leg_pitch[s])
			# The planted foot: on the ground and moving back against the body.
			var planted: bool = a.leg_lift[s] < 0.02 and fwd - prev_forward[s] < 0.0
			if planted:
				var slip: float = (speed * DT + fwd - prev_forward[s]) / DT / speed
				acc[0] += slip
				acc[1] += absf(slip)
				acc[2] += 1
			prev_forward[s] = fwd)
	assert_true(acc[2] > 100, "no planted foot found at %f" % speed)
	return Vector2(acc[0] / acc[2], acc[1] / acc[2])


func test_feet_stay_planted_instead_of_sliding() -> void:
	for speed: float in [22.0, 34.0, 46.0, 62.0, 78.0]:
		var r := _slide(speed)
		# On average the planted foot goes back exactly as fast as the body goes forward...
		assert_true(absf(r.x) < 0.12, "at %f the planted foot slips %f%% on average" % [speed, r.x * 100.0])
		# ...and never skates far from that (a sine swing, or the old time-driven walk, slid ~30%).
		assert_true(r.y < 0.3, "at %f the planted foot skates %f%% of the body's speed" % [speed, r.y * 100.0])


func _peak(speed: float) -> PackedFloat64Array:
	var a := FigureAnim.new()
	_walk(a, speed, 2.0)
	var acc := [0.0, 0.0, 0.0, 0.0, 0]
	_walk(a, speed, 2.0, 300.0, Pose.WALK, func(_z: float) -> void:
		acc[0] = maxf(acc[0], absf(a.leg_pitch[0]))
		acc[1] = maxf(acc[1], absf(a.arm_pitch[1] - a.arm_pitch[0]))
		acc[2] = maxf(acc[2], a.leg_lift[0])
		acc[3] += a.lean
		acc[4] += 1)
	return PackedFloat64Array([acc[0], acc[1], acc[2], acc[3] / acc[4]])


func test_the_stride_opens_out_with_speed_and_running_is_its_own_gait() -> void:
	var shuffle := _peak(12.0)
	var slow := _peak(28.0)
	var walk := _peak(46.0)
	var run := _peak(80.0)
	for i in 4:
		assert_true(shuffle[i] < slow[i] and slow[i] < walk[i] and walk[i] < run[i],
			"joint %d doesn't grow with speed: %f %f %f %f" % [i, shuffle[i], slow[i], walk[i], run[i]])
	# A run leans well forward and lifts its feet higher than a walk.
	assert_true(run[3] > 0.12, "a run's lean %f" % run[3])
	assert_true(walk[3] < 0.08, "a walk's lean %f" % walk[3])
	assert_true(run[2] > Gait.LIFT_WALK * 1.4, "a run's foot lift %f" % run[2])


func test_steps_land_once_per_stride_of_ground_covered() -> void:
	for speed: float in [30.0, 46.0, 78.0]:
		var a := FigureAnim.new()
		_walk(a, speed, 1.0)
		var steps := [0]
		var z0 := _walk(a, speed, 0.5, 100.0)
		var z1 := _walk(a, speed, 4.0, z0, Pose.WALK, func(_z: float) -> void:
			if a.stepped:
				steps[0] += 1)
		var expected := (z1 - z0) / Gait.stride(Gait.amplitude(speed))
		assert_true(absf(steps[0] - expected) <= 1.5, "at %f: %d steps in %f units, expected about %f" % [speed, steps[0], z1 - z0, expected])


func test_stopping_brings_the_feet_together_and_levels_the_body() -> void:
	for speed: float in [30.0, 46.0, 80.0]:
		for stop_at in 8:
			var a := FigureAnim.new()
			var z := _walk(a, speed, 2.0 + stop_at * 0.13)
			for i in 60:
				a.update(DT, 0.0, z, 0.0, Pose.STAND)
			for s in 2:
				assert_near(0.0, a.leg_pitch[s], 0.03, "leg %d still swinging %f/%d" % [s, speed, stop_at])
				assert_near(0.0, a.leg_lift[s], 0.05, "leg %d still lifted" % s)
			assert_near(0.0, a.root_y, 0.1, "body not level")


func test_the_body_leans_into_a_start_and_rocks_back_on_a_stop() -> void:
	var a := FigureAnim.new()
	for i in 60:
		a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
	var z := 0.0
	var early := 0.0
	for i in 48:
		z += 60.0 * DT
		a.update(DT, 0.0, z, 0.0, Pose.WALK)
		early = maxf(early, a.lean)
	assert_true(early > 0.15, "leaned only %f on setting off" % early)
	var back := 0.0
	for i in 60:
		a.update(DT, 0.0, z, 0.0, Pose.STAND)
		back = minf(back, a.lean)
	assert_true(back < -0.03, "didn't rock back on stopping (%f)" % back)
	for i in 240:
		a.update(DT, 0.0, z, 0.0, Pose.STAND)
	assert_near(0.0, a.lean, 0.005, "didn't settle upright")


func test_turning_is_smoothed_and_never_faster_than_a_body_can_spin() -> void:
	var a := FigureAnim.new()
	a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
	var prev := a.yaw
	var biggest := 0.0
	var target := 0.0
	for n in 120 * 6:
		if n % 120 == 0:
			target = 3.0 if target == 0.0 else 0.0
		a.update(DT, 0.0, 0.0, target, Pose.STAND)
		biggest = maxf(biggest, absf(a.yaw - prev))
		prev = a.yaw
	assert_true(biggest <= 14.0 * DT + 1e-4, "turned %f in a step" % biggest)
	assert_near(target, a.yaw, 0.02)
	# It takes the short way round: from just under π to just over -π is a hair, not a spin.
	var b := FigureAnim.new()
	b.update(DT, 0.0, 0.0, 3.1, Pose.STAND)
	for i in 12:
		b.update(DT, 0.0, 0.0, -3.1, Pose.STAND)
	assert_true(absf(b.yaw) > 2.9, "went the long way round (%f)" % b.yaw)


func test_a_shove_or_a_teleport_is_not_walking() -> void:
	var a := FigureAnim.new()
	a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
	for i in 30:
		a.update(DT, 0.0, 0.0, 0.0, Pose.STAND)
	var phase := a.phase
	for i in 60:
		a.update(DT, 200.0, 300.0, 0.0, Pose.STAND)
	assert_eq(phase, a.phase, "a teleport advanced the stride")
	assert_true(a.speed < 3.0, "a teleport looked like a sprint (%f)" % a.speed)
	RigChecks.assert_sane(self, a, "after a teleport")


func _bounce() -> Vector2:
	var a := FigureAnim.new()
	_walk(a, 80.0, 2.0)
	var acc := [99.0, -99.0, 0.0]
	_walk(a, 80.0, 2.0, 500.0, Pose.WALK, func(_z: float) -> void:
		acc[0] = minf(acc[0], a.root_y)
		acc[1] = maxf(acc[1], a.root_y)
		acc[2] = maxf(acc[2], absf(a.leg_pitch[0])))
	return Vector2(acc[1] - acc[0], acc[2])


func test_reduce_motion_drops_the_bounce_but_keeps_the_stride() -> void:
	var full := _bounce()
	FigureAnim.reduce_motion = true
	var calm := _bounce()
	FigureAnim.reduce_motion = false
	assert_true(calm.x < full.x * 0.5, "reduce motion still bounces %f vs %f" % [calm.x, full.x])
	assert_near(full.y, calm.y, 0.02, "the stride must stay readable")


func test_running_about_never_bends_a_limb_impossibly() -> void:
	var rng := KRandom.new(9)
	var a := FigureAnim.new(2)
	var z := 0.0
	var x := 0.0
	var yaw := 0.0
	var speed := 0.0
	var speeds: Array[float] = [0.0, 10.0, 25.0, 46.0, 80.0, 100.0, 140.0]
	for n in 120 * 90:
		if n % 100 == 0:
			speed = speeds[rng.next_int_until(speeds.size())]
		if n % 170 == 0:
			yaw = rng.next_float() * 6.28 - 3.14
		z += speed * DT * cos(yaw)
		x += speed * DT * sin(yaw)
		a.update(DT, x, z, yaw, Pose.WALK if speed > 0.0 else Pose.STAND)
		if not RigChecks.assert_sane(self, a, "fuzz %d at %f" % [n, speed]):
			return
