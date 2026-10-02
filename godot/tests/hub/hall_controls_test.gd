extends PaTest
## hub/HallControlsTest.kt: the hall's controls beyond first person's basics: the run latch,
## tap-to-walk from the overhead camera, and how the player's settings (look speed and direction,
## handedness, field of view, reduced motion) reach the hall.

const W := 1080.0
const H := 2400.0
const DENSITY := 2.75
const DEG := PI / 180.0
const DT := GameLoop.FIXED_DT


func after_each() -> void:
	FigureAnim.reduce_motion = false
	ScreenShake.intensity = 1.0


## GameSettings with the given fields changed (Kotlin's named arguments).
static func _settings(fields: Dictionary = {}) -> GameSettings:
	var s := GameSettings.new()
	for k: String in fields:
		s.set(k, fields[k])
	return s


## A world with nobody else in it, overhead unless [param first_person].
func _world(first_person: bool = false, settings: GameSettings = null) -> HubWorld:
	var w := HubWorld.new(HallGames.games(), null)
	w.density = DENSITY
	w.set_viewport(W, H)
	w.npcs.clear()
	w.apply_settings(settings if settings != null else GameSettings.new())
	w.set_first_person(first_person, false)
	return w


func _run(w: HubWorld, seconds: float) -> void:
	for i in MathUtil.round_to_int(seconds / DT):
		w.update(DT)


## Runs until the route has ended (or [param limit] seconds), then a moment more.
func _finish_walk(w: HubWorld, limit: float = 40.0) -> void:
	var t := 0.0
	while w.route.active and t < limit:
		w.update(DT)
		t += DT
	_run(w, 1.0)


func _to_focus(w: HubWorld, s: Spot) -> float:
	return atan2(s.focus_x - w.player.x, s.focus_z - w.player.y)


static func _what(s: Spot) -> String:
	return HallGames.games()[s.machine].id if s.type == SpotType.MACHINE else SpotType.name_of(s.type)


# ------------------------------------------------------------------ the run latch

func _stick(latch: bool) -> Joystick:
	var j := Joystick.new()
	j.radius = 100.0
	j.run_latch = latch
	j.down(1, 500.0, 500.0)
	return j


func test_a_latched_run_keeps_going_when_the_thumb_eases_off_the_rim() -> void:
	var j := _stick(true)
	j.move(1, 500.0, 500.0 - 85.0)
	assert_false(j.latched, "walking pace isn't the rim")
	assert_eq(0.0, j.run)
	# Past the rim: the run locks, at full speed (the base trails the thumb).
	j.move(1, 500.0, 500.0 - 130.0)
	assert_true(j.latched)
	assert_eq(1.0, j.run)
	# The thumb relaxes to 60% of the way: still running, still at full speed, same heading.
	j.move(1, 500.0, 500.0 - 130.0 + 100.0 * 0.4)
	assert_near(0.6, sqrt((j.knob_x - j.base_x) * (j.knob_x - j.base_x) + (j.knob_y - j.base_y) * (j.knob_y - j.base_y)) / j.radius, 1e-3)
	assert_true(j.latched)
	assert_eq(1.0, j.run)
	assert_near(0.0, j.out_x, 1e-4)
	assert_near(-1.0, j.out_y, 1e-4)
	# Turning about with the thumb still well out keeps the run and the new heading.
	j.move(1, j.base_x + 60.0, j.base_y)
	assert_true(j.latched)
	assert_near(1.0, j.out_x, 1e-4)
	assert_near(0.0, j.out_y, 1e-4)
	# Down to under half: a real ease-off, the run lets go and it's the ordinary curve again.
	j.move(1, j.base_x + 40.0, j.base_y)
	assert_false(j.latched)
	assert_eq(0.0, j.run)
	assert_near(Joystick.curve(0.4), j.out_x, 1e-4)
	# Easing off doesn't re-latch by itself: it takes the rim again.
	j.move(1, j.base_x + 90.0, j.base_y)
	assert_false(j.latched)
	j.move(1, j.base_x + 120.0, j.base_y)
	assert_true(j.latched)


func test_lifting_the_thumb_ends_a_latched_run_and_the_next_touch_starts_fresh() -> void:
	var j := _stick(true)
	j.move(1, 500.0, 500.0 - 130.0)
	assert_true(j.latched)
	j.up(1)
	assert_false(j.latched)
	assert_eq(0.0, j.run)
	assert_eq(0.0, j.out_x)
	j.down(2, 300.0, 300.0)
	assert_false(j.latched)
	j.move(2, 300.0, 300.0 - 60.0)
	assert_false(j.latched)
	# Letting go without a lift (a dialog, a pause) does it too.
	j.move(2, 300.0, 300.0 - 130.0)
	assert_true(j.latched)
	j.release()
	assert_false(j.latched)


func test_the_rim_mode_still_runs_only_while_the_thumb_is_at_the_rim() -> void:
	var j := _stick(false)
	j.move(1, 500.0, 500.0 - 130.0)
	assert_false(j.latched)
	assert_eq(1.0, j.run)
	j.move(1, 500.0, 500.0 - 130.0 + 100.0 * 0.4)
	assert_eq(0.0, j.run)
	assert_near(Joystick.curve(0.6), -j.out_y, 1e-4)


func test_the_world_latches_only_in_first_person_and_only_if_the_setting_is_on() -> void:
	# First person: the left thumb has the stick.
	var fp := _world(true)
	fp.pointer_down(1, 200.0, 2000.0)
	assert_true(fp.joystick.run_latch)
	fp.cancel_input()
	var over := _world(false)
	over.pointer_down(1, 800.0, 2000.0)
	assert_false(over.joystick.run_latch, "overhead has no run to lock")
	var rim := _world(true, _settings({"run_latch": false}))
	rim.pointer_down(1, 200.0, 2000.0)
	assert_false(rim.joystick.run_latch)


func _latched_speed(latch: bool) -> float:
	var w := _world(true, _settings({"run_latch": latch}))
	w.player.place(w.map.spawn_x, w.map.spawn_y)
	w.camera.set_look(PI, HubCamera.REST_PITCH_DEG * DEG)
	w.update(DT)
	w.pointer_down(1, 200.0, 1800.0)
	var r := w.joystick.radius
	# Out past the rim, then back to 60% of the way.
	w.pointer_move(1, 200.0, 1800.0 - r * 1.3)
	_run(w, 0.3)
	w.pointer_move(1, 200.0, 1800.0 - r * 1.3 + r * 0.4)
	_run(w, 0.4)
	var x0 := w.player.x
	var z0 := w.player.y
	_run(w, 0.2)
	return sqrt((w.player.x - x0) * (w.player.x - x0) + (w.player.y - z0) * (w.player.y - z0)) / 0.2


func test_a_latched_run_holds_run_speed_with_a_relaxed_thumb() -> void:
	assert_near(Player.SPEED * Player.RUN_SCALE, _latched_speed(true), 1.5)
	# The rim mode slows to a walk-and-a-bit at 60%.
	assert_true(_latched_speed(false) < Player.SPEED * 0.6)


# ------------------------------------------------------------------ tap to walk, overhead

## Overhead, the camera settled behind a player standing at ([param x], [param z]).
func _overhead_at(w: HubWorld, x: float, z: float) -> void:
	w.player.place(x, z)
	_run(w, 3.0)


func _tap(w: HubWorld, id: int, sx: float, sy: float) -> Spot:
	w.pointer_down(id, sx, sy)
	return w.pointer_up(id, sx, sy)


func test_tapping_the_floor_overhead_walks_there_and_stops() -> void:
	var w := _world()
	_overhead_at(w, 304.0, 800.0)
	var cam := PaCamera3D.new()
	w.camera.apply(cam, int(W), int(H))
	var hit: Variant = cam.project(340.0, 0.0, 700.0)
	assert_not_null(hit)
	var p: Vector3 = hit
	assert_null(_tap(w, 3, p.x, p.y))
	assert_true(w.route.active, "no route to a tapped floor point")
	assert_near(340.0, w.route.goal_x, 1.0)
	assert_near(700.0, w.route.goal_y, 1.0)
	assert_true(w.has_walked)
	_finish_walk(w)
	assert_false(w.route.active)
	assert_near(0.0, sqrt((w.player.x - 340.0) * (w.player.x - 340.0) + (w.player.y - 700.0) * (w.player.y - 700.0)), 2.0)
	assert_false(w.player.moving)


func test_tapping_a_machine_overhead_walks_to_its_spot_and_faces_it() -> void:
	var w := _world()
	var spot: Spot = null
	for s: Spot in w.map.spots:
		if s.type == SpotType.MACHINE and s.area.center_x > 300.0:
			spot = s
			break
	_overhead_at(w, spot.area.center_x + 30.0, spot.area.bottom + 90.0)
	var prop: Prop = null
	for pr: Prop in w.map.props:
		if pr.kind == PropKind.MACHINE and absf(pr.center_x - spot.area.center_x) < 1.0 and absf(pr.z1 - spot.area.top) < 1.0:
			prop = pr
			break
	var cam := PaCamera3D.new()
	w.camera.apply(cam, int(W), int(H))
	# The top of the cabinet, as seen from above.
	var hit: Variant = cam.project(prop.center_x, prop.height, prop.center_z)
	assert_not_null(hit)
	var p: Vector3 = hit
	assert_null(_tap(w, 3, p.x, p.y))
	assert_true(w.route.active)
	assert_true(spot == w.route.spot)
	_finish_walk(w)
	assert_true(spot == w.active_spot, "didn't stop at the machine's spot")
	assert_false(w.player.moving)
	assert_near(0.0, HubCamera.wrap(w.player.yaw - _to_focus(w, spot)), 0.06, "not facing the machine")


func test_the_stick_overrides_an_overhead_walk_at_once() -> void:
	var w := _world()
	_overhead_at(w, 304.0, 800.0)
	assert_true(w.walk_to_point(304.0, 500.0))
	_run(w, 0.3)
	assert_true(w.route.active)
	var y0 := w.player.y
	assert_true(y0 < 800.0, "walking north")
	# A thumb pushing the other way (east): the route is dropped and the stick has the kid.
	w.pointer_down(1, 200.0, 1800.0)
	w.pointer_move(1, 200.0 + w.joystick.radius, 1800.0)
	w.update(DT)
	assert_false(w.route.active)
	var x0 := w.player.x
	_run(w, 0.4)
	assert_true(w.player.x > x0 + 15.0, "the stick walked the kid east")
	assert_near(y0, w.player.y, 8.0)
	w.pointer_up(1, 0.0, 0.0)
	_run(w, 0.2)
	assert_false(w.player.moving)


func test_a_drag_overhead_is_still_just_walking_and_not_a_tap() -> void:
	var w := _world()
	_overhead_at(w, 304.0, 800.0)
	w.pointer_down(1, 500.0, 1500.0)
	w.pointer_move(1, 500.0, 1500.0 - 200.0)
	_run(w, 0.5)
	assert_null(w.pointer_up(1, 500.0, 1300.0))
	assert_false(w.route.active, "a long drag isn't a tap")
	# Nor is a press that's held.
	w.pointer_down(2, 500.0, 1500.0)
	_run(w, 0.6)
	w.pointer_up(2, 500.0, 1500.0)
	assert_false(w.route.active)


func test_a_tap_mid_transition_between_the_views_is_ignored() -> void:
	var w := _world()
	_overhead_at(w, 304.0, 800.0)
	w.set_first_person(true, true)
	_run(w, 0.2)
	assert_true(w.camera.fp_amount >= 0.01 and w.camera.fp_amount <= 0.99)
	assert_false(w.tap_to_walk(540.0, 1500.0))
	assert_false(w.route.active)


func test_every_spot_is_reachable_overhead_from_the_door_and_faced() -> void:
	var none: Array[int] = []
	for decor: Array[int] in [none, HallGames.every_decor()]:
		var w := _world()
		w.set_decor(decor)
		for spot: Spot in w.map.spots:
			var what := _what(spot)
			w.player.place(w.map.spawn_x, w.map.spawn_y)
			_run(w, 0.1)
			assert_true(w.walk_to(spot), "%s: no route to %s" % [str(decor), what])
			var t := 0.0
			var clean := true
			while w.route.active and t < 40.0:
				w.update(DT)
				t += DT
				if clean and Collision.blocked(w.map.solids, w.player.x, w.player.y):
					fail("%s: walked into something on the way to %s" % [str(decor), what])
					clean = false
			_run(w, 1.0)
			assert_true(spot == w.active_spot, "%s: didn't get to %s (at %f, %f after %fs)" % [str(decor), what, w.player.x, w.player.y, t])
			assert_false(w.player.moving)
			assert_near(0.0, HubCamera.wrap(w.player.yaw - _to_focus(w, spot)), 0.06, "%s: not facing %s" % [str(decor), what])


func test_walking_to_the_spot_you_are_already_at_just_turns_you_to_face_it() -> void:
	var w := _world()
	var spot: Spot = null
	for s: Spot in w.map.spots:
		if s.type == SpotType.MACHINE:
			spot = s
			break
	w.player.place(spot.area.center_x, minf(spot.area.top + HubWorld.STAND_DEPTH, spot.area.bottom - 2.0))
	w.player.yaw = 0.0
	_run(w, 0.1)
	assert_true(w.walk_to(spot))
	assert_false(w.route.active)
	_run(w, 1.0)
	assert_near(0.0, HubCamera.wrap(w.player.yaw - _to_focus(w, spot)), 0.06)


# ------------------------------------------------------------------ settings

func test_left_handed_swaps_the_walk_and_look_halves() -> void:
	assert_true(HubWorld.is_look_side(800.0, W, false))
	assert_false(HubWorld.is_look_side(200.0, W, false))
	assert_false(HubWorld.is_look_side(800.0, W, true))
	assert_true(HubWorld.is_look_side(200.0, W, true))
	for x: float in [0.0, 1.0, 539.0, 540.0, 541.0, 1079.0]:
		assert_true(HubWorld.is_look_side(x, W, false) != HubWorld.is_look_side(x, W, true), str(x))

	var right := _world(true)
	right.pointer_down(1, 800.0, 1800.0)
	assert_eq(1, right.look_pointer)
	assert_false(right.joystick.active)
	right.pointer_down(2, 200.0, 1800.0)
	assert_true(right.joystick.active)

	var left := _world(true, _settings({"left_handed": true}))
	left.pointer_down(1, 800.0, 1800.0)
	assert_true(left.joystick.active, "the right thumb walks")
	assert_eq(-1, left.look_pointer)
	left.pointer_down(2, 200.0, 1800.0)
	assert_eq(2, left.look_pointer, "the left thumb looks")
	# Overhead, handedness doesn't matter: anywhere starts the stick.
	var over := _world(false, _settings({"left_handed": true}))
	over.pointer_down(1, 200.0, 1800.0)
	assert_true(over.joystick.active)


func _turned(s: GameSettings) -> Vector2:
	var w := _world(true, s)
	w.player.place(w.map.spawn_x, w.map.spawn_y)
	w.camera.set_look(0.0, 0.0)
	w.update(DT)
	w.pointer_down(2, 800.0, 1200.0)
	w.pointer_move(2, 900.0, 1300.0)
	_run(w, 0.3)
	return Vector2(w.camera.yaw, w.camera.pitch)


func test_look_speed_and_invert_scale_and_flip_the_drag() -> void:
	var k := HubWorld.LOOK_DEG_PER_DP * DEG / DENSITY
	var plain := _turned(GameSettings.new())
	assert_near(-100.0 * k, plain.x, 1e-4)
	assert_near(-100.0 * k * HubWorld.LOOK_PITCH_SCALE, plain.y, 1e-4)
	var fast := _turned(_settings({"look_percent": 200}))
	assert_near(2.0 * plain.x, fast.x, 1e-4)
	assert_near(2.0 * plain.y, fast.y, 1e-4)
	var slow := _turned(_settings({"look_percent": 50}))
	assert_near(0.5 * plain.x, slow.x, 1e-4)
	# Inverted: the same drag tilts the other way, and turns the same.
	var inv := _turned(_settings({"invert_y": true}))
	assert_near(plain.x, inv.x, 1e-4)
	assert_near(-plain.y, inv.y, 1e-4)


func _focal(s: GameSettings) -> float:
	var w := _world(true, s)
	w.update(DT)
	var cam := PaCamera3D.new()
	w.camera.apply(cam, int(W), int(H))
	return cam.focal


func test_the_field_of_view_setting_scales_first_person_and_the_default_changes_nothing() -> void:
	var plain := HubWorld.new(HallGames.games(), null)
	plain.set_viewport(W, H)
	plain.set_first_person(true, false)
	var ref := PaCamera3D.new()
	plain.camera.apply(ref, int(W), int(H))
	assert_near(ref.focal, _focal(GameSettings.new()), 1e-3, "the default FOV must be the designed view")
	assert_true(_focal(_settings({"fov_deg": 90})) < ref.focal, "wider view, shorter focal length")
	assert_true(_focal(_settings({"fov_deg": 60})) > ref.focal, "narrower view, longer focal length")
	# It only reaches first person: overhead is untouched.
	var over := _world(false, _settings({"fov_deg": 90}))
	var over_ref := PaCamera3D.new()
	HubWorld.new(HallGames.games(), null).camera.apply(over_ref, int(W), int(H))
	var over_cam := PaCamera3D.new()
	over.camera.apply(over_cam, int(W), int(H))
	assert_near(over_ref.focal, over_cam.focal, 1e-3)


func _bob_and_kick(s: GameSettings) -> Vector2:
	var w := _world(true, s)
	w.player.place(w.map.spawn_x, w.map.spawn_y)
	w.camera.set_look(PI, HubCamera.REST_PITCH_DEG * DEG)
	w.update(DT)
	w.pointer_down(1, 200.0, 1800.0)
	w.pointer_move(1, 200.0, 1800.0 - w.joystick.radius * 1.3)
	_run(w, 0.5)
	var lo := INF
	var hi := -INF
	for i in 60:
		w.update(DT)
		lo = minf(lo, w.camera.eye_y)
		hi = maxf(hi, w.camera.eye_y)
	return Vector2(hi - lo, w.camera.fov_kick)


func _shake() -> float:
	var s := ScreenShake.new()
	s.add(1.0)
	var peak := 0.0
	for i in 20:
		s.update(0.02)
		peak = maxf(peak, absf(s.offset_x) + absf(s.offset_y))
	return peak


func test_reduced_motion_stops_the_bob_the_run_kick_and_the_shake() -> void:
	var lively := _bob_and_kick(GameSettings.new())
	assert_true(lively.x > 0.6, "running bobs: %f" % lively.x)
	assert_near(HubCamera.RUN_FOV_KICK_DEG, lively.y, 0.5)
	var calm := _bob_and_kick(_settings({"reduce_motion": true}))
	assert_near(0.0, calm.x, 1e-4)
	assert_near(0.0, calm.y, 1e-4)
	assert_true(_shake() > 1.0)
	ScreenShake.intensity = 0.0
	assert_eq(0.0, _shake())
	ScreenShake.intensity = 1.0


func test_settings_reach_the_hall_and_the_defaults_leave_it_as_it_was() -> void:
	var w := HubWorld.new(HallGames.games(), null)
	assert_true(GameSettings.new().equals(w.settings))
	assert_eq(1.0, w.camera.fov_scale)
	assert_eq(1.0, w.camera.bob_scale)
	assert_eq(1.0, w.camera.kick_scale)
	w.apply_settings(_settings({"fov_deg": 87, "reduce_motion": true, "look_percent": 7777}))
	# 87 snaps to the nearest step, 85.
	assert_near(85.0 / HubCamera.FP_FOV_DEG, w.camera.fov_scale, 1e-5)
	assert_eq(0.0, w.camera.bob_scale)
	assert_eq(0.0, w.camera.kick_scale)
	assert_eq(200, w.settings.look_percent, "out-of-range values are clamped on the way in")
	assert_not_null(w.settings)
	w.apply_settings(GameSettings.new())
	assert_eq(1.0, w.camera.bob_scale)


func test_the_hud_extra_row_pushes_the_bottom_of_the_hud_down() -> void:
	var w := HubWorld.new(HallGames.games(), null)
	w.hud_bottom = 250.0
	assert_eq(250.0, w.hud_bottom)
	w.hud_extra = 130.0
	assert_eq(380.0, w.hud_bottom)
	# The first row's height is set each layout, without forgetting the extra.
	w.hud_bottom = 260.0
	assert_eq(390.0, w.hud_bottom)
