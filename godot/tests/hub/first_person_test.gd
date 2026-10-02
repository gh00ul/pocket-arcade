extends PaTest
## hub/FirstPersonTest.kt: first person in the hall: the eye and its look limits, head-bob, walking
## relative to the view with the usual collision, and the split-screen controls (left walks, right
## looks).

const W := 1080.0
const H := 2400.0
const DENSITY := 2.75
const DEG := PI / 180.0
const DT := GameLoop.FIXED_DT


func after_each() -> void:
	FigureAnim.reduce_motion = false


func _world() -> HubWorld:
	var w := HubWorld.new(HallGames.games(), null)
	w.set_viewport(W, H)
	w.density = DENSITY
	return w


func _run(w: HubWorld, seconds: float) -> void:
	for i in MathUtil.round_to_int(seconds / DT):
		w.update(DT)


# ------------------------------------------------------------------ camera

func test_the_eye_sits_at_kid_height_over_the_player_looking_where_the_view_points() -> void:
	var w := _world()
	w.set_first_person(true, false)
	w.camera.set_look(PI / 2.0, -10.0 * DEG)
	_run(w, 0.1)
	var cam := PaCamera3D.new()
	w.camera.apply(cam, int(W), int(H))
	# Over the player, at the back of the head (facing +x here).
	var back := w.camera.eye_back
	assert_true(back >= 0.0 and back <= 3.0)
	assert_near(w.player.x - back, cam.ex, 1e-3)
	assert_near(HubCamera.EYE_HEIGHT, cam.ey, 1e-3)
	assert_near(w.player.y, cam.ez, 1e-3)
	# Yaw π/2 faces +x; pitched 10° down.
	assert_near(cos(10.0 * DEG), cam.fx, 1e-3)
	assert_near(-sin(10.0 * DEG), cam.fy, 1e-3)
	assert_near(0.0, cam.fz, 1e-3)
	assert_eq(HubCamera.FP_NEAR, cam.near)
	# A kid's eye: above the kids' heads (≈ 44), below the marquees.
	assert_true(HubCamera.EYE_HEIGHT >= 46.0 and HubCamera.EYE_HEIGHT <= 62.0)


func test_pitch_is_clamped_and_yaw_wraps_freely() -> void:
	var c := HubCamera.new()
	c.set_look(0.0, 0.0)
	c.look(0.0, 2.0)
	assert_near(HubCamera.PITCH_LIMIT_DEG * DEG, c.pitch, 1e-5)
	c.look(0.0, -5.0)
	assert_near(-HubCamera.PITCH_LIMIT_DEG * DEG, c.pitch, 1e-5)
	for i in 9:
		c.look(1.0, 0.0)
	assert_true(c.yaw > -PI and c.yaw <= PI)
	assert_near(HubCamera.wrap(9.0), c.yaw, 1e-4)


func test_the_field_of_view_stays_sensible_on_any_screen() -> void:
	assert_near(HubCamera.FP_FOV_DEG, HubCamera.fp_fov_y(16.0 / 9.0) / DEG, 0.01)
	assert_near(HubCamera.FP_FOV_DEG, HubCamera.fp_fov_y(1.0) / DEG, 0.01)
	# A 20:9 portrait phone: wider vertically, capped, and still ~48° across.
	var tall := HubCamera.fp_fov_y(1080.0 / 2400.0)
	assert_true(tall / DEG >= 80.0 and tall / DEG <= HubCamera.FP_MAX_FOV_DEG + 0.01)
	var across := 2.0 * atan(tan(tall / 2.0) * (1080.0 / 2400.0)) / DEG
	assert_true(across > 45.0)


func test_head_bob_only_while_walking_and_exactly_zero_at_rest() -> void:
	var w := _world()
	w.set_first_person(true, false)
	assert_eq(HubCamera.EYE_HEIGHT, w.camera.eye_y)
	# Walk forward and watch the eye move.
	w.pointer_down(1, 200.0, 1800.0)
	w.pointer_move(1, 200.0, 1800.0 - w.joystick.radius)
	var min_y := INF
	var max_y := -INF
	for i in 120:
		w.update(DT)
		min_y = minf(min_y, w.camera.eye_y)
		max_y = maxf(max_y, w.camera.eye_y)
	assert_true(max_y - min_y > 0.5, "the head bobs while walking")
	assert_true(max_y - min_y < 3.0, "but only a little")
	w.pointer_up(1, 200.0, 1800.0 - w.joystick.radius)
	_run(w, 1.0)
	assert_eq(0.0, w.camera.bob_weight)
	assert_eq(HubCamera.EYE_HEIGHT, w.camera.eye_y)
	# Facing −z: the eye is straight behind the feet, no sway left.
	assert_near(w.player.x, w.camera.eye_x, 1e-3)
	assert_near(w.player.y + w.camera.eye_back, w.camera.eye_z, 1e-3)


func test_the_eye_never_ends_up_inside_a_wall_behind_you() -> void:
	var w := _world()
	w.set_first_person(true, false)
	# Stand with the feet right against the west wall, facing east.
	var x := HubLayout.WALL + Collision.FEET_HALF_W + 1.0
	# Somewhere along it with open floor to the east.
	var z := -1.0
	for zi in range(150, 1000, 5):
		var ok := true
		for d in range(0, 41, 2):
			for dz in range(-12, 13, 2):
				if Collision.blocked(w.map.solids, x + d, zi + dz):
					ok = false
					break
			if not ok:
				break
		if ok:
			z = float(zi)
			break
	assert_true(z > 0.0, "no open floor along the west wall")
	w.player.x = x
	w.player.y = z
	w.camera.set_look(PI / 2.0, 0.0)
	w.update(DT)
	# First person's body keeps its clearance: it eases off the wall, without it counting as a walk.
	assert_true(w.player.x >= HubLayout.WALL + PaBody.RADIUS - 0.01, "body still against the wall at %f" % w.player.x)
	assert_false(w.player.moving)
	for yaw_deg in range(0, 360, 15):
		w.camera.set_look(yaw_deg * DEG, 0.0)
		w.update(DT)
		var ex := w.camera.eye_x
		var ez := w.camera.eye_z
		assert_true(PaBody.clear(w.map.solids, ex, ez, PaBody.RADIUS - HubCamera.EYE_BACK - 0.5), "eye too near a solid at (%f, %f)" % [ex, ez])


func test_switching_eases_between_the_two_poses_in_half_a_second() -> void:
	var w := _world()
	var cam := PaCamera3D.new()
	w.camera.apply(cam, int(W), int(H))
	var overhead_y := cam.ey
	assert_true(overhead_y > 200.0)
	w.set_first_person(true, true)
	_run(w, 0.25)
	var mid := w.camera.fp_amount
	assert_true(mid > 0.2 and mid < 0.8)
	w.camera.apply(cam, int(W), int(H))
	assert_true(cam.ey < overhead_y and cam.ey > HubCamera.EYE_HEIGHT)
	_run(w, 0.3)
	assert_eq(1.0, w.camera.fp_amount)
	w.camera.apply(cam, int(W), int(H))
	assert_near(HubCamera.EYE_HEIGHT, cam.ey, 1e-3)
	# Entering first person looks the way the kid was facing.
	assert_near(HubCamera.wrap(w.player.yaw), w.camera.yaw, 1e-4)
	w.set_first_person(false, true)
	_run(w, 0.6)
	w.camera.apply(cam, int(W), int(H))
	assert_near(overhead_y, cam.ey, 1.0)
	assert_eq(HubCamera.OVERHEAD_NEAR, cam.near)


# ------------------------------------------------------------------ walking

func test_the_stick_walks_relative_to_the_view() -> void:
	var out := PackedFloat32Array([0.0, 0.0])
	# Facing the back wall (−z): up walks to −z, right strafes to +x.
	HubCamera.move_relative(0.0, -1.0, PI, out)
	assert_near(0.0, out[0], 1e-5)
	assert_near(-1.0, out[1], 1e-5)
	HubCamera.move_relative(1.0, 0.0, PI, out)
	assert_near(1.0, out[0], 1e-5)
	assert_near(0.0, out[1], 1e-5)
	# Facing +x: up walks to +x, right strafes toward the entrance (+z), down backs off.
	HubCamera.move_relative(0.0, -1.0, PI / 2.0, out)
	assert_near(1.0, out[0], 1e-5)
	assert_near(0.0, out[1], 1e-5)
	HubCamera.move_relative(1.0, 0.0, PI / 2.0, out)
	assert_near(0.0, out[0], 1e-5)
	assert_near(1.0, out[1], 1e-5)
	HubCamera.move_relative(0.0, 1.0, PI / 2.0, out)
	assert_near(-1.0, out[0], 1e-5)
	# Diagonals keep the stick's length (same speed as overhead).
	HubCamera.move_relative(0.6, -0.8, 1.234, out)
	assert_near(1.0, sqrt(out[0] * out[0] + out[1] * out[1]), 1e-5)


func test_walking_in_first_person_goes_where_you_look_at_the_usual_speed() -> void:
	var w := _world()
	w.set_first_person(true, false)
	var yaw := 200.0 * DEG
	w.camera.set_look(yaw, 0.0)
	# Up to walking pace (full walking speed, short of the run at the rim)...
	w.pointer_down(1, 200.0, 1800.0)
	w.pointer_move(1, 200.0, 1800.0 - w.joystick.radius * Joystick.FULL_AT)
	_run(w, 0.3)
	# ...then a quarter of a second at it.
	var x0 := w.player.x
	var z0 := w.player.y
	_run(w, 0.25)
	var dx := w.player.x - x0
	var dz := w.player.y - z0
	assert_near(yaw, atan2(dx, dz) + TAU, 0.02, "heading")
	assert_near(Player.SPEED * 0.25, sqrt(dx * dx + dz * dz), 0.5, "speed")
	assert_near(yaw, HubCamera.wrap(w.player.yaw) + TAU, 1e-4)


func test_walking_into_a_wall_slides_along_it() -> void:
	# A wall along x = 100; walk diagonally into it (forward-left while facing −z).
	var wall: Array[Box] = [Box.new(80.0, 0.0, 100.0, 400.0)]
	var p := Player.new()
	p.x = 110.0
	p.y = 300.0
	var out := PackedFloat32Array([0.0, 0.0])
	HubCamera.move_relative(-0.7, -0.7, PI, out)
	for i in 240:
		p.update(DT, out[0], out[1], wall, PI)
	assert_true(p.x >= 100.0 + Collision.FEET_HALF_W - 0.01, "stopped at the wall")
	assert_true(p.y < 300.0 - 60.0, "slid along it")
	assert_false(Collision.blocked(wall, p.x, p.y))
	assert_eq(PI, p.yaw)


# ------------------------------------------------------------------ controls

func test_left_half_walks_and_right_half_looks_with_both_fingers_at_once() -> void:
	var w := _world()
	w.set_first_person(true, false)
	w.camera.set_look(PI, 0.0)
	w.pointer_down(1, 200.0, 1800.0)
	w.pointer_down(2, 800.0, 1200.0)
	assert_true(w.joystick.active)
	assert_eq(1, w.joystick.pointer_id)
	assert_eq(2, w.look_pointer)
	# Both move in the same frame.
	w.pointer_move(1, 200.0, 1800.0 - w.joystick.radius)
	w.pointer_move(2, 800.0 + 200.0, 1200.0)
	assert_true(w.joystick.out_y < -0.9)
	# The drag is smoothed in over a few hundredths of a second, all of it.
	_run(w, 0.1)
	# 200 px right at 2.75 px/dp and 0.3°/dp: a turn to the right (yaw goes down).
	var expected := 200.0 / DENSITY * HubWorld.LOOK_DEG_PER_DP * DEG
	assert_near(PI - expected, w.camera.yaw + (TAU if w.camera.yaw < 0.0 else 0.0), 1e-4)
	# Dragging up looks up.
	w.pointer_move(2, 1000.0, 1100.0)
	_run(w, 0.05)
	assert_true(w.camera.pitch > 0.0)
	# Lifting the look finger leaves the stick alone, and vice versa.
	w.pointer_up(2, 1000.0, 1100.0)
	assert_eq(-1, w.look_pointer)
	assert_true(w.joystick.active)
	w.pointer_up(1, 200.0, 1800.0)
	assert_false(w.joystick.active)


func test_a_tap_on_the_look_side_does_not_turn_the_view() -> void:
	var w := _world()
	w.set_first_person(true, false)
	w.camera.set_look(1.0, 0.1)
	var slop := HubWorld.TAP_SLOP_DP * DENSITY
	w.pointer_down(3, 900.0, 1000.0)
	w.pointer_move(3, 900.0 + slop * 0.6, 1000.0 - slop * 0.5)
	assert_false(w.look_dragging)
	assert_null(w.pointer_up(3, 900.0 + slop * 0.6, 1000.0 - slop * 0.5))
	assert_eq(1.0, w.camera.yaw)
	assert_eq(0.1, w.camera.pitch)
	assert_false(w.joystick.active)
	# Past the slop it turns, from where the finger first landed.
	w.pointer_down(4, 900.0, 1000.0)
	w.pointer_move(4, 900.0 - slop * 2.0, 1000.0)
	assert_true(w.look_dragging)
	_run(w, 0.1)
	assert_near(1.0 + slop * 2.0 / DENSITY * HubWorld.LOOK_DEG_PER_DP * DEG, w.camera.yaw, 1e-4)


func test_overhead_the_whole_screen_is_the_stick() -> void:
	var w := _world()
	w.pointer_down(1, 900.0, 1500.0)
	assert_true(w.joystick.active)
	assert_eq(-1, w.look_pointer)


func test_cancelling_lets_go_of_every_finger() -> void:
	var w := _world()
	w.set_first_person(true, false)
	w.pointer_down(1, 200.0, 1800.0)
	w.pointer_down(2, 800.0, 1200.0)
	w.pointer_move(2, 900.0, 1200.0)
	w.cancel_input()
	assert_false(w.joystick.active)
	assert_eq(-1, w.look_pointer)
	var yaw := w.camera.yaw
	# The same fingers keep moving: nothing happens until they land again.
	w.pointer_move(2, 1000.0, 1200.0)
	w.pointer_move(1, 200.0, 1600.0)
	assert_eq(yaw, w.camera.yaw)
	assert_eq(0.0, w.joystick.out_y)
	# Switching views mid-gesture lets go too.
	w.pointer_down(5, 800.0, 1200.0)
	w.set_first_person(false, true)
	assert_eq(-1, w.look_pointer)


func test_the_prompt_still_takes_the_tap_in_first_person_and_the_view_faces_the_machine_afterwards() -> void:
	var w := _world()
	w.set_first_person(true, false)
	var spot: Spot = null
	for s: Spot in w.map.spots:
		if s.type == SpotType.MACHINE:
			spot = s
			break
	w.player.x = spot.area.center_x
	w.player.y = spot.area.center_y
	w.update(DT)
	assert_true(spot == w.active_spot)
	# The renderer placed the bubble on the right half of the screen.
	w.bubble_left = 600.0
	w.bubble_top = 500.0
	w.bubble_right = 1000.0
	w.bubble_bottom = 800.0
	w.pointer_down(7, 800.0, 650.0)
	assert_eq(-1, w.look_pointer)
	assert_true(spot == w.pointer_up(7, 800.0, 650.0))
	# Look away, dive in, and come back out facing the machine.
	w.camera.set_look(0.0, 0.3)
	w.set_dive(spot, 1.0)
	var to_machine := atan2(spot.focus_x - w.player.x, spot.focus_z - w.player.y)
	assert_near(HubCamera.wrap(to_machine), w.camera.yaw, 1e-4)
	assert_true(w.camera.pitch <= 0.0 and w.camera.pitch >= -25.0 * DEG - 1e-4)
	w.set_dive(spot, 0.5)
	w.set_dive(null, 0.0)
	assert_eq(0.0, w.camera.dive)
	# Every counter and kiosk has a spot to stand at.
	var prizes := false
	var tokens := false
	for s: Spot in w.map.spots:
		prizes = prizes or s.type == SpotType.PRIZES
		tokens = tokens or s.type == SpotType.TOKENS
	assert_true(prizes)
	assert_true(tokens)
	assert_true(absf(w.camera.eye_y - HubCamera.EYE_HEIGHT) < 1e-3)
