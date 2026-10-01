extends PaTest
## engine/TiltMathTest.kt: the lean of a phone from its gravity reading, and how far it must lean
## to steer. Plus (Godot) TiltSteer's handling of the plugin's readings.

const DEG := PI / 180.0


func test_a_flat_phone_leans_nowhere_and_right_edge_down_leans_right() -> void:
	assert_near(0.0, TiltMath.lean(0.0, 0.0, 9.8), 1e-6)
	# The right edge down 20 degrees: "up" tips towards -x in the phone's own axes.
	assert_near(20.0, TiltMath.lean(-sin(20.0 * DEG) * 9.8, 0.0, cos(20.0 * DEG) * 9.8) / DEG, 0.01)
	# The left edge down 15 degrees leans left.
	assert_near(-15.0, TiltMath.lean(sin(15.0 * DEG), 0.0, cos(15.0 * DEG)) / DEG, 0.01)


func test_an_upright_phone_turned_like_a_wheel_leans_the_same_angle() -> void:
	assert_near(30.0, TiltMath.lean(-sin(30.0 * DEG), cos(30.0 * DEG), 0.0) / DEG, 0.01)
	assert_near(-30.0, TiltMath.lean(sin(30.0 * DEG), cos(30.0 * DEG), 0.0) / DEG, 0.01)
	# Length is no matter: an accelerometer's 9.8 or a rotation matrix's 1.
	assert_near(TiltMath.lean(-0.5, 0.8, 0.1), TiltMath.lean(-4.9, 7.84, 0.98), 1e-5)


func test_steering_has_a_dead_zone_and_full_lock_and_is_symmetric() -> void:
	var n := 0.1
	assert_eq(0.0, TiltMath.steer(n, n))
	assert_eq(0.0, TiltMath.steer(n + 2.0 * DEG, n))
	assert_eq(0.0, TiltMath.steer(n - 2.0 * DEG, n))
	var small := TiltMath.steer(n + 5.0 * DEG, n)
	var mid := TiltMath.steer(n + 12.0 * DEG, n)
	assert_true(small > 0.0 and small < mid and mid < 1.0, "%f %f" % [small, mid])
	assert_near(1.0, TiltMath.steer(n + TiltMath.FULL_DEG * DEG, n), 1e-4)
	assert_eq(1.0, TiltMath.steer(n + 70.0 * DEG, n))
	for a: float in [4.0, 9.0, 15.0, 40.0]:
		assert_near(-TiltMath.steer(n + a * DEG, n), TiltMath.steer(n - a * DEG, n), 1e-5)


func test_steering_is_measured_from_the_level_the_player_held_not_from_flat() -> void:
	var held := 12.0 * DEG
	assert_eq(0.0, TiltMath.steer(held, held))
	assert_true(TiltMath.steer(held + 10.0 * DEG, held) > 0.2)
	assert_true(TiltMath.steer(held - 10.0 * DEG, held) < -0.2)
	var last := -1.0
	var a := 0.0
	while a <= 40.0:
		var s := TiltMath.steer(held + a * DEG, held)
		assert_true(s >= last, "steering fell at %f: %f after %f" % [a, s, last])
		last = s
		a += 0.5


func test_nonsense_readings_steer_nothing() -> void:
	assert_eq(0.0, TiltMath.steer(NAN, 0.0))
	assert_eq(0.0, TiltMath.steer(0.0, NAN))


class Leaner:
	var leans: Array = []

	func on_tilt(lean: float) -> void:
		leans.append(lean)


func test_tilt_steer_passes_rotation_readings_and_smooths_the_accelerometer() -> void:
	var fake := FakePlugin.new()
	AndroidBridge.fake = fake
	var g := Leaner.new()
	var t := TiltSteer.new()
	fake.kind = TiltSteer.KIND_ACCEL
	assert_true(t.start(g))
	# The first accelerometer reading is taken whole, later ones blended 15%.
	t.reading(TiltSteer.KIND_ACCEL, 0.0, 0.0, 9.8)
	t.reading(TiltSteer.KIND_ACCEL, -9.8, 0.0, 0.0)
	assert_near(0.0, g.leans[0], 1e-6)
	var x := -0.15
	var z := 0.85
	assert_near(TiltMath.lean(x, 0.0, z), g.leans[1], 1e-6)
	# Free fall (no gravity) is no reading.
	t.reading(TiltSteer.KIND_ACCEL, 0.1, 0.0, 0.1)
	assert_eq(2, g.leans.size())
	# Rotation vector readings go straight through.
	t.stop()
	fake.kind = TiltSteer.KIND_ROTATION
	assert_true(t.start(g))
	fake.readings = PackedFloat32Array([1.0, -0.5, 0.0, 0.866])
	t.poll()
	assert_near(TiltMath.lean(-0.5, 0.0, 0.866), g.leans[2], 1e-6)
	t.stop()
	assert_eq(2, fake.stops, "each stop of a listening reader stops the sensor")
	# Without a sensor the game keeps its touch steering.
	fake.kind = 0
	assert_false(t.start(g))
	AndroidBridge.fake = null


class FakePlugin:
	var kind := 0
	var readings := PackedFloat32Array()
	var stops := 0

	func tiltStart() -> int:
		return kind

	func tiltStop() -> void:
		stops += 1

	func tiltTake() -> PackedFloat32Array:
		var r := readings
		readings = PackedFloat32Array()
		return r
