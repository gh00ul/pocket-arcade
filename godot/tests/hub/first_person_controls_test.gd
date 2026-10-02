extends PaTest
## hub/FirstPersonControlsTest.kt: how first person feels: getting up to speed and stopping, slower
## backpedal and strafe and a run at the rim, the eye at the head, the body's clearance (and that it
## still fits every aisle), the stick's size and curve, the look drag's smoothing, levelling and turn
## to face a machine, tap-to-walk, sliding round corners, kids making way, and footsteps in step with
## the bob.

const W := 1080.0
const H := 2400.0
const DENSITY := 2.75
const DEG := PI / 180.0
const DT := GameLoop.FIXED_DT


func after_each() -> void:
	FigureAnim.reduce_motion = false


## A first-person world with nobody else in it (kids get their own tests).
func _world(kids: bool = false) -> HubWorld:
	var w := HubWorld.new(HallGames.games(), null)
	w.density = DENSITY
	w.set_viewport(W, H)
	w.set_first_person(true, false)
	if not kids:
		w.npcs.clear()
	return w


func _run(w: HubWorld, seconds: float) -> void:
	for i in MathUtil.round_to_int(seconds / DT):
		w.update(DT)


## Holds the stick at [param m] of its radius in direction ([param dx], [param dy]) on screen.
func _stick(w: HubWorld, m: float, dx: float, dy: float) -> void:
	if not w.joystick.active:
		w.pointer_down(1, 200.0, 1800.0)
	var l := sqrt(dx * dx + dy * dy)
	w.pointer_move(1, 200.0 + dx / l * w.joystick.radius * m, 1800.0 + dy / l * w.joystick.radius * m)


func _let_go(w: HubWorld) -> void:
	w.pointer_up(1, 0.0, 0.0)


## Standing at the door facing up the main aisle (plenty of open floor ahead).
func _at_the_door(w: HubWorld) -> void:
	w.player.place(w.map.spawn_x, w.map.spawn_y)
	w.camera.set_look(PI, HubCamera.REST_PITCH_DEG * DEG)
	w.update(DT)


func _steady_speed(m: float, dx: float, dy: float) -> float:
	var w := _world()
	_at_the_door(w)
	_stick(w, m, dx, dy)
	_run(w, 0.4)
	var x0 := w.player.x
	var z0 := w.player.y
	_run(w, 0.2)
	return sqrt((w.player.x - x0) * (w.player.x - x0) + (w.player.y - z0) * (w.player.y - z0)) / 0.2


static func _first_machine_spot(w: HubWorld) -> Spot:
	for s: Spot in w.map.spots:
		if s.type == SpotType.MACHINE:
			return s
	return null


static func _what(s: Spot) -> String:
	return HallGames.games()[s.machine].id if s.type == SpotType.MACHINE else SpotType.name_of(s.type)


# ------------------------------------------------------------------ walking feel

func test_getting_up_to_speed_and_stopping_take_a_fraction_of_a_second() -> void:
	var w := _world()
	_at_the_door(w)
	_stick(w, Joystick.FULL_AT, 0.0, -1.0)
	var t := 0.0
	var early := -1.0
	while w.player.speed_frac < 0.99 and t < 1.0:
		w.update(DT)
		t += DT
		if early < 0.0 and t >= 0.05:
			early = w.player.speed_frac
	assert_true(t >= 0.14 and t <= 0.24, "full speed took %fs" % t)
	assert_true(early >= 0.15 and early <= 0.45, "no jump to full speed: %f after 0.05 s" % early)
	_run(w, 0.2)
	_let_go(w)
	t = 0.0
	while w.player.moving and t < 1.0:
		w.update(DT)
		t += DT
	assert_true(t >= 0.06 and t <= 0.14, "stopping took %fs" % t)
	assert_near(0.0, w.player.speed_frac, 0.05)


func test_backpedal_and_strafe_are_slower_and_the_rim_runs() -> void:
	var fwd := _steady_speed(Joystick.FULL_AT, 0.0, -1.0)
	var back := _steady_speed(Joystick.FULL_AT, 0.0, 1.0)
	var side := _steady_speed(Joystick.FULL_AT, 1.0, 0.0)
	var run := _steady_speed(1.2, 0.0, -1.0)
	assert_near(Player.SPEED, fwd, 1.0)
	assert_near(Player.SPEED * Player.BACK_SCALE, back, 1.0)
	assert_near(Player.SPEED * Player.STRAFE_SCALE, side, 1.0)
	assert_near(Player.SPEED * Player.RUN_SCALE, run, 1.5)
	assert_true(Player.RUN_SCALE >= 1.3 and Player.RUN_SCALE <= 1.4)


func _bob_and_kick(m: float) -> Vector2:
	var w := _world()
	_at_the_door(w)
	_stick(w, m, 0.0, -1.0)
	_run(w, 0.5)
	var lo := INF
	var hi := -INF
	for i in 60:
		w.update(DT)
		lo = minf(lo, w.camera.eye_y)
		hi = maxf(hi, w.camera.eye_y)
	return Vector2(hi - lo, w.camera.fov_kick)


func test_running_bobs_harder_and_widens_the_view_a_little() -> void:
	var walk := _bob_and_kick(Joystick.FULL_AT)
	var run := _bob_and_kick(1.2)
	assert_true(walk.x >= 0.6 and walk.x <= 1.5, "walking bob %f" % walk.x)
	assert_true(run.x > walk.x * 1.2 and run.x < 2.5, "running bobs harder: %f vs %f" % [run.x, walk.x])
	assert_near(0.0, walk.y, 1e-3)
	assert_near(HubCamera.RUN_FOV_KICK_DEG, run.y, 0.5)
	assert_true(HubCamera.RUN_FOV_KICK_DEG >= 2.0 and HubCamera.RUN_FOV_KICK_DEG <= 6.0)


func test_turning_on_the_spot_turns_the_view_in_place() -> void:
	var w := _world()
	_at_the_door(w)
	w.player.place(304.0, 800.0)
	var min_x := INF
	var max_x := -INF
	var min_z := INF
	var max_z := -INF
	for deg in range(0, 360, 10):
		w.camera.set_look(deg * DEG, 0.0)
		w.update(DT)
		var ex := w.camera.eye_x
		var ez := w.camera.eye_z
		assert_true(sqrt((ex - w.player.x) * (ex - w.player.x) + (ez - w.player.y) * (ez - w.player.y)) <= HubCamera.EYE_BACK + 1e-3)
		min_x = minf(min_x, ex)
		max_x = maxf(max_x, ex)
		min_z = minf(min_z, ez)
		max_z = maxf(max_z, ez)
	# A full turn moves the eye no more than a head's width: it rotates, it doesn't orbit.
	assert_true(max_x - min_x <= 2.0 * HubCamera.EYE_BACK + 0.01 and max_z - min_z <= 2.0 * HubCamera.EYE_BACK + 0.01,
		"the eye swung %f x %f" % [max_x - min_x, max_z - min_z])
	assert_true(HubCamera.EYE_BACK <= 3.0)


# ------------------------------------------------------------------ clearance

## Every point on a 2-unit grid the body can stand on and walk to from the door. (Godot: the grid's
## "the body fits here" is rasterised box by box with PaBody.penetration's own test, and nearest()
## looks within 32 units, which covers every limit the test checks; Kotlin scanned it all.)
class Reach:
	extends RefCounted
	const STEP := 2.0
	const LOOK := 32.0
	var cols: int
	var rows: int
	var seen := PackedByteArray()
	var start_clear := false

	func _init(map: HubMap, solids: Array[Box]) -> void:
		cols = int(map.width_px / STEP)
		rows = int(map.height_px / STEP)
		var blocked := PackedByteArray()
		blocked.resize(cols * rows)
		var r := PaBody.RADIUS
		for b: Box in solids:
			# PaBody.penetration(solids, x, y) > EPS for this box, over the cells it can reach.
			var i0 := maxi(0, int(floorf((b.left - r) / STEP)))
			var i1 := mini(cols - 1, int(ceilf((b.right + r) / STEP)))
			var j0 := maxi(0, int(floorf((b.top - r) / STEP)))
			var j1 := mini(rows - 1, int(ceilf((b.bottom + r) / STEP)))
			for j in range(j0, j1 + 1):
				var y := j * STEP
				if y <= b.top - r or y >= b.bottom + r:
					continue
				for i in range(i0, i1 + 1):
					var x := i * STEP
					if x <= b.left - r or x >= b.right + r:
						continue
					var cx := clampf(x, b.left, b.right)
					var cy := clampf(y, b.top, b.bottom)
					var d2 := (x - cx) * (x - cx) + (y - cy) * (y - cy)
					var p: float
					if d2 <= 1e-12:
						p = r + minf(minf(x - b.left, b.right - x), minf(y - b.top, b.bottom - y))
					else:
						p = r - sqrt(d2)
					if p > PaBody.EPS:
						blocked[j * cols + i] = 1
		seen.resize(cols * rows)
		var start := MathUtil.round_to_int(map.spawn_y / STEP) * cols + MathUtil.round_to_int(map.spawn_x / STEP)
		start_clear = blocked[start] == 0
		if not start_clear:
			return
		var queue := PackedInt32Array()
		queue.resize(cols * rows)
		var head := 0
		var tail := 0
		queue[tail] = start
		tail += 1
		seen[start] = 1
		while head < tail:
			var cur := queue[head]
			head += 1
			var cx := cur % cols
			var cy := cur / cols
			for d in 4:
				var nx := cx + (1 if d == 0 else (-1 if d == 1 else 0))
				var ny := cy + (1 if d == 2 else (-1 if d == 3 else 0))
				if nx < 0 or nx >= cols or ny < 0 or ny >= rows:
					continue
				var ni := ny * cols + nx
				if seen[ni] != 0 or blocked[ni] != 0:
					continue
				seen[ni] = 1
				queue[tail] = ni
				tail += 1

	## How close the body can get to ([param x], [param z]) (INF if not within LOOK).
	func nearest(x: float, z: float) -> float:
		var best := INF
		var i0 := maxi(0, int((x - LOOK) / STEP))
		var i1 := mini(cols - 1, int((x + LOOK) / STEP) + 1)
		var j0 := maxi(0, int((z - LOOK) / STEP))
		var j1 := mini(rows - 1, int((z + LOOK) / STEP) + 1)
		for j in range(j0, j1 + 1):
			for i in range(i0, i1 + 1):
				if seen[j * cols + i] != 0:
					var dx := i * STEP - x
					var dz := j * STEP - z
					best = minf(best, sqrt(dx * dx + dz * dz))
		return best


func test_the_body_fits_every_aisle_and_reaches_every_spot_queue_and_hangout() -> void:
	# Under half the narrowest aisle the floor plan allows (HubMapTest.MIN_WALKWAY = 21).
	assert_true(PaBody.RADIUS * 2.0 < 21.0)
	var none: Array[int] = []
	for decor: Array[int] in [none, HallGames.every_decor()]:
		var map := HubLayout.build(HallGames.games(), decor)
		var solids := PaBody.solids_for(map)
		var reach := Reach.new(map, solids)
		assert_true(reach.start_clear, "the body doesn't fit at the door")
		for s: Spot in map.spots:
			var what := _what(s)
			var sx := s.area.center_x
			var sz := minf(s.area.top + HubWorld.STAND_DEPTH, s.area.bottom - 2.0)
			assert_true(s.area.contains(sx, sz), "%s: the %s stand point isn't in its spot" % [str(decor), what])
			assert_true(reach.nearest(sx, sz) <= 1.5, "%s: can't reach the %s spot (%f off)" % [str(decor), what, reach.nearest(sx, sz)])
			# Standing there, the eye is well back from the cabinet: a machine to look at, not a
			# control panel under your nose.
			assert_true(sz - HubCamera.EYE_BACK * 0.0 - s.area.top >= PaBody.RADIUS + PaBody.FRONT_GAP - 0.01)
		for q: Hangout in map.cafe_queue:
			assert_true(reach.nearest(q.x, q.z) <= 12.0, "%s: can't reach the queue spot at (%f, %f)" % [str(decor), q.x, q.z])
		for h: Hangout in map.hangouts:
			# Play spots and seats are for kids standing right at a cabinet or sitting at a table; the
			# body only has to get to their doorstep.
			var limit := 24.0 if h.cafe else (12.0 if h.playing else 2.0)
			assert_true(reach.nearest(h.x, h.z) <= limit, "%s: can't reach the hangout at (%f, %f) (%f)" % [str(decor), h.x, h.z, reach.nearest(h.x, h.z)])


# ------------------------------------------------------------------ joystick

func test_the_stick_is_thumb_sized_with_a_fine_centre() -> void:
	assert_near(Joystick.RADIUS_DP * DENSITY, Joystick.radius_for(DENSITY, W, H), 1e-3)
	assert_near(56.0, Joystick.radius_for(1.0, 480.0, 800.0), 1e-3)
	# Capped on a small screen at a high density.
	assert_near(Joystick.MAX_SCREEN_FRAC * 600.0, Joystick.radius_for(4.0, 600.0, 1000.0), 1e-3)
	var w := _world()
	w.pointer_down(1, 200.0, 1800.0)
	assert_near(Joystick.radius_for(DENSITY, W, H), w.joystick.radius, 1e-3)
	assert_true(w.joystick.radius > W * 0.11, "bigger than the old 11% of the width")

	assert_eq(0.0, Joystick.curve(Joystick.DEAD_ZONE))
	assert_true(Joystick.curve(Joystick.DEAD_ZONE + 0.02) > 0.0)
	var last := 0.0
	for i in 101:
		var v := Joystick.curve(i / 100.0)
		assert_true(v >= last and v <= 1.0)
		last = v
	# Fine near the centre: half a push is well under half speed (a straight line gives 0.53).
	assert_true(Joystick.curve(0.5) < 0.42)
	assert_near(1.0, Joystick.curve(Joystick.FULL_AT), 1e-5)
	assert_eq(1.0, Joystick.curve(1.0))
	assert_eq(0.0, Joystick.run_for(Joystick.FULL_AT))
	assert_eq(1.0, Joystick.run_for(1.0))
	w.pointer_move(1, 200.0, 1800.0 - w.joystick.radius * 0.5)
	assert_near(-Joystick.curve(0.5), w.joystick.out_y, 1e-4)
	assert_eq(0.0, w.joystick.run)
	w.pointer_move(1, 200.0, 1800.0 - w.joystick.radius * 3.0)
	assert_eq(1.0, w.joystick.run)


# ------------------------------------------------------------------ look

func test_the_look_drag_is_smoothed_but_never_lost_or_late() -> void:
	var w := _world()
	_at_the_door(w)
	w.camera.set_look(0.0, 0.0)
	w.pointer_down(2, 800.0, 1200.0)
	w.pointer_move(2, 900.0, 1200.0)
	var k := HubWorld.LOOK_DEG_PER_DP * DEG / DENSITY
	var want := -100.0 * k
	w.update(DT)
	w.update(DT)
	assert_true(w.camera.yaw / want > 0.7, "under 17 ms, most of the turn is there: %f" % (w.camera.yaw / want))
	for i in 3:
		w.update(DT)
	assert_true(w.camera.yaw / want > 0.95, "under 42 ms, nearly all of it: %f" % (w.camera.yaw / want))
	_run(w, 0.1)
	assert_near(want, w.camera.yaw, 1e-4, "every pixel counts")
	# Up and down turns less per dp than side to side.
	w.pointer_move(2, 900.0, 1300.0)
	_run(w, 0.1)
	assert_near(-100.0 * k * HubWorld.LOOK_PITCH_SCALE, w.camera.pitch, 1e-4)
	assert_true(HubWorld.LOOK_PITCH_SCALE >= 0.5 and HubWorld.LOOK_PITCH_SCALE <= 0.9)


func test_a_jittery_finger_does_not_shake_the_view() -> void:
	var w := _world()
	_at_the_door(w)
	w.camera.set_look(0.0, 0.0)
	w.pointer_down(2, 800.0, 1200.0)
	w.pointer_move(2, 900.0, 1200.0)
	_run(w, 0.2)
	var k := HubWorld.LOOK_DEG_PER_DP * DEG / DENSITY
	# A finger held still, its reported position flickering 3 px either way every frame.
	var worst := 0.0
	var prev := w.camera.yaw
	for i in 60:
		w.pointer_move(2, 900.0 + (3.0 if i % 2 == 0 else -3.0), 1200.0)
		w.update(DT)
		worst = maxf(worst, absf(w.camera.yaw - prev))
		prev = w.camera.yaw
	var raw := 6.0 * k
	assert_true(worst < 0.6 * raw, "the view shook %f of the raw flicker" % (worst / raw))


func test_walking_levels_the_view_only_when_you_let_go_of_it() -> void:
	var w := _world()
	_at_the_door(w)
	w.camera.set_look(PI, 30.0 * DEG)
	_stick(w, Joystick.FULL_AT, 0.0, -1.0)
	_run(w, 2.5)
	assert_near(HubCamera.REST_PITCH_DEG, w.camera.pitch / DEG, 3.0)
	# Standing still, the view stays where you put it.
	_let_go(w)
	_run(w, 0.5)
	w.camera.set_look(PI, 30.0 * DEG)
	_run(w, 2.0)
	assert_near(30.0, w.camera.pitch / DEG, 1e-3)
	# And with a finger on the view, walking leaves it alone too.
	var v := _world()
	_at_the_door(v)
	v.camera.set_look(PI, 30.0 * DEG)
	v.pointer_down(2, 800.0, 1200.0)
	_stick(v, Joystick.FULL_AT, 0.0, -1.0)
	_run(v, 2.0)
	assert_near(30.0, v.camera.pitch / DEG, 1e-3)


func _to_focus(w: HubWorld, s: Spot) -> float:
	return atan2(s.focus_x - w.player.x, s.focus_z - w.player.y)


func test_stepping_into_a_play_spot_turns_the_view_to_its_machine() -> void:
	var w := _world()
	var spot := _first_machine_spot(w)
	w.player.place(spot.area.center_x, spot.area.top + HubWorld.STAND_DEPTH)
	w.camera.set_look(_to_focus(w, spot) + 50.0 * DEG, 20.0 * DEG)
	_run(w, 0.8)
	assert_true(spot == w.active_spot)
	assert_near(0.0, HubCamera.wrap(w.camera.yaw - _to_focus(w, spot)) / DEG, 1.0)
	assert_true(w.camera.pitch / DEG >= -20.01 and w.camera.pitch / DEG <= 10.01)
	# Only once a visit: look away and it stays looking away.
	w.camera.set_look(_to_focus(w, spot) + 60.0 * DEG, 0.0)
	_run(w, 0.8)
	assert_near(60.0, HubCamera.wrap(w.camera.yaw - _to_focus(w, spot)) / DEG, 0.5)

	# Steering the view yourself: no assist.
	var v := _world()
	_at_the_door(v)
	v.pointer_down(2, 800.0, 1200.0)
	v.pointer_move(2, 900.0, 1200.0)
	_run(v, 0.2)
	v.player.place(spot.area.center_x, spot.area.top + HubWorld.STAND_DEPTH)
	v.camera.set_look(_to_focus(v, spot) + 50.0 * DEG, 0.0)
	_run(v, 0.8)
	assert_near(50.0, HubCamera.wrap(v.camera.yaw - _to_focus(v, spot)) / DEG, 0.5)


# ------------------------------------------------------------------ tap to walk

func test_tap_to_walk_reaches_every_spot_from_the_door_and_faces_it() -> void:
	var w := _world()
	for spot: Spot in w.map.spots:
		var what := _what(spot)
		_at_the_door(w)
		assert_true(w.walk_to(spot), "no route to %s" % what)
		var t := 0.0
		var clean := true
		while w.route.active and t < 30.0:
			w.update(DT)
			t += DT
			if clean and not PaBody.clear(w.body_solids, w.player.x, w.player.y):
				fail("walked into something on the way to %s" % what)
				clean = false
		_run(w, 1.0)
		assert_true(spot == w.active_spot, "didn't get to %s (at %f, %f after %fs)" % [what, w.player.x, w.player.y, t])
		assert_false(w.player.moving)
		assert_near(0.0, HubCamera.wrap(w.camera.yaw - _to_focus(w, spot)) / DEG, 2.0, "not facing %s" % what)


func test_tapping_a_machine_on_screen_walks_to_it_and_any_input_cancels() -> void:
	var w := _world()
	# In the cross aisle in front of a bank, looking at it.
	var spot: Spot = null
	for s: Spot in w.map.spots:
		if s.type == SpotType.MACHINE and s.area.center_x > 300.0:
			spot = s
			break
	w.player.place(spot.area.center_x + 20.0, spot.area.bottom + 60.0)
	w.camera.set_look(_to_focus(w, spot), 0.0)
	w.update(DT)
	var cam := PaCamera3D.new()
	w.camera.apply(cam, int(W), int(H))
	var prop: Prop = null
	for p: Prop in w.map.props:
		if p.kind == PropKind.MACHINE and absf(p.center_x - spot.area.center_x) < 1.0 and absf(p.z1 - spot.area.top) < 1.0:
			prop = p
			break
	# Anywhere on the cabinet: here, low on its front.
	var hit: Variant = cam.project(prop.center_x, prop.height * 0.3, prop.z1)
	assert_not_null(hit)
	var p3: Vector3 = hit
	w.pointer_down(3, p3.x, p3.y)
	assert_null(w.pointer_up(3, p3.x, p3.y))
	assert_true(w.route.active)
	assert_true(spot == w.route.spot)
	# The stick takes over at once.
	_stick(w, 0.5, 1.0, 0.0)
	w.update(DT)
	assert_false(w.route.active)
	_let_go(w)
	# So does dragging the view.
	assert_true(w.walk_to(spot))
	w.pointer_down(2, 800.0, 1200.0)
	w.pointer_move(2, 900.0, 1200.0)
	assert_false(w.route.active)
	w.pointer_up(2, 900.0, 1200.0)
	# A tap on the floor walks to that spot of floor.
	w.camera.set_look(PI, -20.0 * DEG)
	w.player.place(304.0, 800.0)
	w.update(DT)
	w.camera.apply(cam, int(W), int(H))
	var floor_hit: Variant = cam.project(310.0, 0.0, 740.0)
	assert_not_null(floor_hit)
	var f3: Vector3 = floor_hit
	w.pointer_down(4, f3.x, f3.y)
	w.pointer_up(4, f3.x, f3.y)
	assert_true(w.route.active)
	assert_near(310.0, w.route.goal_x, 1.0)
	assert_near(740.0, w.route.goal_y, 1.0)
	_run(w, 3.0)
	assert_false(w.route.active)
	assert_near(0.0, sqrt((w.player.x - 310.0) * (w.player.x - 310.0) + (w.player.y - 740.0) * (w.player.y - 740.0)), 2.0)


func test_the_prompt_still_takes_the_tap_at_the_end_of_a_walk() -> void:
	var w := _world()
	var spot := _first_machine_spot(w)
	_at_the_door(w)
	assert_true(w.walk_to(spot))
	_run(w, 20.0)
	assert_true(spot == w.active_spot)
	w.bubble_left = 600.0
	w.bubble_top = 500.0
	w.bubble_right = 1000.0
	w.bubble_bottom = 800.0
	w.pointer_down(7, 800.0, 650.0)
	assert_true(spot == w.pointer_up(7, 800.0, 650.0))
	assert_false(w.route.active, "tapping PLAY doesn't start a walk")


# ------------------------------------------------------------------ collision feel

func test_a_cabinet_corner_does_not_snag() -> void:
	var box: Array[Box] = [Box.new(100.0, 100.0, 140.0, 140.0)]
	var p := Player.new()
	# Heading straight up (−z) with the body's centre 3 units inside the box's left edge.
	p.place(103.0, 170.0)
	var t := 0.0
	while p.y > 90.0 and t < 2.0:
		p.walk_first_person(DT, 0.0, -1.0, box, PI)
		t += DT
		if not PaBody.clear(box, p.x, p.y):
			fail("inside the box at (%f, %f)" % [p.x, p.y])
			return
	assert_true(p.y <= 90.0, "snagged on the corner at (%f, %f)" % [p.x, p.y])
	# Straight into the middle of the face, though, it just stops.
	var q := Player.new()
	q.place(120.0, 170.0)
	for i in 240:
		q.walk_first_person(DT, 0.0, -1.0, box, PI)
	assert_near(140.0 + PaBody.RADIUS, q.y, 0.05)
	assert_near(120.0, q.x, 1e-3)


func test_sliding_along_a_wall_does_not_jitter() -> void:
	var wall: Array[Box] = [Box.new(80.0, 0.0, 100.0, 400.0)]
	# Into the wall at 45°, 70° and 85° off its line (nearly head-on).
	for deg: float in [45.0, 70.0, 85.0]:
		var p := Player.new()
		p.place(115.0, 300.0)
		var wx := -sin(deg * DEG)
		var wy := -cos(deg * DEG)
		var touching := 0
		var prev_x := p.x
		var y0 := p.y
		for i in 240:
			p.walk_first_person(DT, wx, wy, wall, PI)
			if p.x <= 100.0 + PaBody.RADIUS + 0.01:
				if touching > 0:
					assert_near(prev_x, p.x, 1e-3, "jitter against the wall at %f°" % deg)
				touching += 1
			prev_x = p.x
			assert_true(PaBody.clear(wall, p.x, p.y))
		assert_true(touching > 100)
		# Slides along at the speed the stick asks for along the wall.
		var expect := Player.SPEED * -wy * 2.0
		assert_true(y0 - p.y > expect * 0.8, "slid %f along it at %f° (expected about %f)" % [y0 - p.y, deg, expect])


# ------------------------------------------------------------------ kids

func test_kids_make_way_and_nobody_is_pushed_into_a_solid() -> void:
	var w := _world(true)
	_at_the_door(w)
	# Walk laps of the hall's middle, turning now and then, through the crowd.
	_stick(w, Joystick.FULL_AT, 0.0, -1.0)
	var closest := INF
	for i in int(40.0 / DT):
		if i % 180 == 0:
			w.camera.set_look(w.camera.yaw + 1.1, w.camera.pitch)
		w.update(DT)
		if PaBody.penetration(w.body_solids, w.player.x, w.player.y) >= 0.05:
			fail("the player ended up in a solid at (%f, %f)" % [w.player.x, w.player.y])
			return
		for n: Npc in w.npcs:
			var d := sqrt((n.x - w.player.x) * (n.x - w.player.x) + (n.y - w.player.y) * (n.y - w.player.y))
			if d > 30.0:
				continue
			if n.state == Npc.State.WALK or n.state == Npc.State.IDLE:
				if Collision.blocked(w.map.solids, n.x, n.y):
					fail("a kid near the player is inside something at (%f, %f)" % [n.x, n.y])
					return
			if n.state != Npc.State.SIT:
				closest = minf(closest, d)
	# Bumps are soft, but nobody walks through anybody.
	assert_true(closest > PaBody.RADIUS + HubWorld.KID_RADIUS - 4.0, "walked through a kid (%f)" % closest)

	# Head-on: a kid right in the player's way steps aside and the player gets past.
	var v := _world(true)
	var kid := v.npcs[0]
	v.npcs.assign([kid])
	_at_the_door(v)
	kid.x = v.player.x
	kid.y = v.player.y - 60.0
	_stick(v, Joystick.FULL_AT, 0.0, -1.0)
	var min_d := INF
	for i in int(2.5 / DT):
		v.update(DT)
		min_d = minf(min_d, sqrt((kid.x - v.player.x) * (kid.x - v.player.x) + (kid.y - v.player.y) * (kid.y - v.player.y)))
		assert_false(Collision.blocked(v.map.solids, kid.x, kid.y))
	assert_true(min_d > PaBody.RADIUS + HubWorld.KID_RADIUS - 4.0, "the camera ended up inside the kid (%f)" % min_d)
	assert_true(v.player.y < kid.y, "never got past the kid")


# ------------------------------------------------------------------ footsteps

## Steps, their average pitch and whether every one landed at the bottom of the bob.
func _footsteps(m: float) -> Array:
	var w := _world()
	_at_the_door(w)
	_stick(w, m, 0.0, -1.0)
	_run(w, 0.4)
	var before := w.steps
	var in_step := true
	var pitch := 0.0
	for i in int(2.0 / DT):
		var n := w.steps
		w.update(DT)
		if w.steps != n:
			pitch += w.last_step_pitch
			# A foot lands as the head is at its lowest.
			var low := HubCamera.EYE_HEIGHT - 0.2 * HubCamera.BOB_HEIGHT * w.camera.bob_weight
			if w.camera.eye_y > low:
				in_step = false
	var count := w.steps - before
	return [count, pitch / maxi(count, 1), in_step]


func test_footsteps_land_at_the_bottom_of_the_bob_and_quicken_with_the_pace() -> void:
	var walk := _footsteps(Joystick.FULL_AT)
	var run := _footsteps(1.2)
	assert_true(walk[0] >= 6 and walk[0] <= 10, "%d steps in 2 s of walking" % walk[0])
	assert_true(run[0] > walk[0], "running takes quicker steps: %d vs %d" % [run[0], walk[0]])
	assert_true(run[1] > walk[1], "brisker steps sound higher: %f vs %f" % [run[1], walk[1]])
	assert_true(walk[2] and run[2])
