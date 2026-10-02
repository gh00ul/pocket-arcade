extends PaTest
## games/hoops/HoopsSceneTest.kt: the hoops scene's visual state is plain arithmetic: it must run
## headlessly and never need art.


func test_effects_and_trails_run_without_any_art() -> void:
	var jobs := PaintPump.pending()
	var scene := HoopsScene.new()
	scene.made(0.3, true, 5)
	scene.rim_hit(12.0, 230.0, -260.0)
	scene.board_hit(0.3, 14.0, 250.0)
	scene.floor_hit(0.0, -100.0, 1.0)
	for i in 6:
		scene.trail(i, 0.0, 120.0, -80.0)
	for i in 1200:
		scene.step(1.0 / 120.0)
	scene.reset()
	for i in 10:
		scene.step(1.0 / 120.0)
	# Godot: nothing was painted on the way.
	assert_eq(jobs, PaintPump.pending(), "no texture painted")


func test_board_stands_between_the_rim_and_the_wall() -> void:
	# The rim hangs clear of the glass, and the glass clear of the wall, so the mount and the rail fit.
	assert_true(HoopsGeo.BOARD_Z > HoopsGeo.HOOP_Z + HoopsGeo.RIM_R)
	assert_true(HoopsGeo.BACK_Z - HoopsGeo.BOARD_Z > 0.25)
	assert_true(HoopsGeo.WALL_Z == -HoopsGeo.BACK_Z * HoopsGeo.S)


func test_the_wall_displays_sit_above_the_backboard() -> void:
	var board_top := HoopsGeo.BOARD_TOP * HoopsGeo.S + HoopsLook.FRAME_W
	assert_true(HoopsLook.SIGN_Y - HoopsLook.SIGN_HH > board_top + 20.0)
	# The readouts share the sign's height and stay on the wall.
	assert_true(HoopsLook.PANEL_X + HoopsLook.PANEL_HW < HoopsLook.WALL_HALF_W)
	assert_true(HoopsLook.PANEL_X - HoopsLook.PANEL_HW > HoopsLook.SIGN_HW)
