extends PaTest
## games/stacker/StackerSceneTest.kt: the stacker scene's visual state is plain arithmetic: it must
## run headlessly and never need art. (The last test is Godot-only.)


func test_effects_run_without_any_art() -> void:
	var jobs := PaintPump.pending()
	var scene := StackerScene.new()
	scene.perfect(0.0, 200.0, 0.0, 100.0, 100.0, 11, 4)
	scene.land(0.0, 218.0, 0.0, 90.0, 90.0)
	scene.cut(40.0, 218.0, 0.0, 12)
	scene.miss(0.0, 218.0, 0.0)
	scene.milestone(198.0)
	for i in 600:
		scene.step(1.0 / 120.0, 3)
	scene.reset()
	# Godot: nothing was painted on the way.
	assert_eq(jobs, PaintPump.pending(), "no texture painted")


func test_height_markers_sit_left_of_the_tower_on_screen() -> void:
	# The camera as StackerGame._aim places it at the start, and a marker's world spot.
	var stage := Stage3D.new(360, 640)
	stage.look(330.0, 330.0, 420.0, 0.0, -20.0, 0.0, 44.0)
	var out: Variant = stage.to_field(StackerLook.MARKER_X, 198.0, StackerLook.MARKER_Z)
	assert_not_null(out)
	# The tower fills roughly x 120..240; the marker's bar and number live clear of it, on the field.
	var x: float = (out as Vector3).x
	assert_true(x >= 40.0 and x <= 100.0, "marker at %s" % x)


func test_slab_bodies_stay_under_the_bloom_threshold() -> void:
	for level in 300:
		var c := StackerArt.body_color(level)
		var mx := maxi((c >> 16) & 255, maxi((c >> 8) & 255, c & 255))
		# Body paint is at most half brightness, so the lamps can lift it without turning it white.
		assert_true(mx <= 130, "level %d body %d" % [level, mx])
		assert_eq(0xFF, (c >> 24) & 0xFF)


func test_hue_walks_the_wheel_one_step_per_level() -> void:
	assert_near(StackerArt.HUE_START, StackerArt.hue_of(0), 1e-3)
	assert_near(fmod(StackerArt.HUE_START + StackerArt.HUE_STEP, 360.0), StackerArt.hue_of(1), 1e-3)


## Godot-only: the slab colours come out of build-13's Float arithmetic bit for bit (body / glow of
## levels 0..29, printed by build-13).
func test_slab_colours_match_build_13() -> void:
	var expected := [
		[0xFF3B197F, 0xFFA172FF], [0xFF51197F, 0xFFBF72FF], [0xFF67197F, 0xFFDE72FF], [0xFF7D197F, 0xFFFC72FF],
		[0xFF7F196B, 0xFFFF72E2], [0xFF7F1955, 0xFFFF72C4], [0xFF7F193E, 0xFFFF72A6], [0xFF7F1928, 0xFFFF7287],
		[0xFF7F2019, 0xFFFF7C72], [0xFF7F3619, 0xFFFF9A72], [0xFF7F4C19, 0xFFFFB872], [0xFF7F6219, 0xFFFFD772],
		[0xFF7F7819, 0xFFFFF572], [0xFF707F19, 0xFFE9FF72], [0xFF5A7F19, 0xFFCBFF72], [0xFF437F19, 0xFFADFF72],
		[0xFF2D7F19, 0xFF8EFF72], [0xFF197F1B, 0xFF72FF75], [0xFF197F31, 0xFF72FF93], [0xFF197F47, 0xFF72FFB1],
		[0xFF197F5D, 0xFF72FFD0], [0xFF197F73, 0xFF72FFEE], [0xFF19757F, 0xFF72F0FF], [0xFF195F7F, 0xFF72D2FF],
		[0xFF19497F, 0xFF72B4FF], [0xFF19327F, 0xFF7295FF], [0xFF191C7F, 0xFF7277FF], [0xFF2C197F, 0xFF8C72FF],
		[0xFF42197F, 0xFFAA72FF], [0xFF58197F, 0xFFC972FF],
	]
	for level in expected.size():
		assert_eq(expected[level][0], StackerArt.body_color(level), "body %d" % level)
		assert_eq(expected[level][1], StackerArt.glow_color(level), "glow %d" % level)
