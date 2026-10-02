extends PaTest
## hub/CameraEntranceTest.kt: the hall camera's arrival from the title: pulled back at first,
## settling to exactly its resting pose.


## The camera's eye, focal length and forward direction for a 1080 × 2340 screen.
func _pose(cam: HubCamera) -> PaCamera3D:
	var c := PaCamera3D.new()
	cam.apply(c, 1080, 2340)
	return c


func _fresh(first_person: bool) -> HubCamera:
	var c := HubCamera.new()
	c.snap_to(304.0, 1045.0)
	c.set_first_person(first_person, false)
	return c


func _dist(a: PaCamera3D, b: PaCamera3D) -> float:
	var dx := a.ex - b.ex
	var dy := a.ey - b.ey
	var dz := a.ez - b.ez
	return sqrt(dx * dx + dy * dy + dz * dz)


func test_no_entrance_changes_nothing() -> void:
	for fp: bool in [false, true]:
		var cam := _fresh(fp)
		var rest := _pose(cam)
		cam.entrance = 1.0
		cam.entrance = 0.0
		var again := _pose(cam)
		assert_eq(rest.ex, again.ex)
		assert_eq(rest.ey, again.ey)
		assert_eq(rest.ez, again.ez)
		assert_eq(rest.focal, again.focal)


func test_the_hall_starts_pulled_back_along_its_line_of_sight_and_wider() -> void:
	for fp: bool in [false, true]:
		var cam := _fresh(fp)
		var rest := _pose(cam)
		cam.entrance = 1.0
		var start := _pose(cam)
		# Further back, not sideways: it still looks the same way.
		assert_near(rest.fx, start.fx, 0.05 if fp else 0.01)
		assert_near(rest.fz, start.fz, 0.05 if fp else 0.01)
		assert_true(_dist(rest, start) > (10.0 if fp else 40.0), "moved back %f (first person %s)" % [_dist(rest, start), fp])
		# A little wider: a shorter focal length for the same screen.
		assert_true(start.focal < rest.focal)


func test_it_settles_smoothly_without_ever_crossing_its_resting_pose() -> void:
	for fp: bool in [false, true]:
		var cam := _fresh(fp)
		var rest := _pose(cam)
		var last := INF
		for i in range(100, -1, -1):
			cam.entrance = i / 100.0
			var d := _dist(rest, _pose(cam))
			assert_true(d <= last + 1e-3, "distance %f after %f at %f" % [d, last, i / 100.0])
			last = d
		assert_near(0.0, last, 1e-3)


func test_the_first_person_eye_stays_inside_the_doors() -> void:
	# Spawn is 35 inside the front wall; pulled back at the start of the entrance the eye must still
	# be inside the hall, not out on the pavement.
	var cam := _fresh(true)
	cam.set_look(PI, HubCamera.REST_PITCH_DEG * PI / 180.0)
	cam.entrance = 1.0
	var start := _pose(cam)
	assert_true(start.ez < HubLayout.FRONT_WALL, "eye z %f" % start.ez)
