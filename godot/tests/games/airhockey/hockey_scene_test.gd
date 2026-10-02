extends PaTest
## games/airhockey/HockeySceneTest.kt: the air hockey scene's visual state is plain arithmetic: it
## must run headlessly and never need art.


func test_effects_and_trail_run_without_any_art() -> void:
	var jobs := PaintPump.pending()
	var scene := HockeyScene.new()
	scene.mallet_hit(180.0, 500.0, 0.9, true)
	scene.mallet_hit(150.0, 120.0, 0.4, false)
	scene.wall_hit(60.0, 300.0, 0.7)
	scene.goal(true)
	scene.goal(false)
	for i in 600:
		scene.trail(100.0 + i * 0.1, 300.0, 1.0 / 120.0)
		scene.step(1.0 / 120.0)
	scene.clear_trail()
	scene.reset()
	# Godot: nothing was painted on the way.
	assert_eq(jobs, PaintPump.pending(), "no texture painted")


func test_the_table_sits_on_the_floor_under_the_camera() -> void:
	assert_near(-HockeyGeo.TABLE_H, HockeyGeo.FLOOR_Y, 0.0)
	# The goal slot fits between the corner arcs, and the mallet's grip plane is above the surface.
	assert_true(HockeyGeo.GOAL_HALF < (HockeyGeo.RR - HockeyGeo.RL) / 2.0 - HockeyGeo.CORNER_R)
	assert_true(HockeyGeo.MALLET_H > 0.0)
	assert_near((HockeyGeo.RT + HockeyGeo.RB) / 2.0, HockeyGeo.CY, 0.0)


func test_scoreboard_face_fits_its_housing() -> void:
	# The face is 188 wide and 58 tall inside a 208 by 74 housing (see HockeyScene._build_table).
	assert_true(HockeyLook.BOARD_HW * 2.0 < 208.0)
	assert_true(HockeyLook.BOARD_Y1 - HockeyLook.BOARD_Y0 < 74.0)
	assert_true(HockeyLook.BOARD_Y0 > 148.0 and HockeyLook.BOARD_Y1 < 222.0)
