extends PaTest
## games/scenea/SceneFxTest.kt (its SceneFx half; the plush-shading tests are with the claw in
## tests/games/claw): the glow textures are pure maths, checkable headlessly.


func alpha_at(t: PaTexture, x: int, y: int) -> int:
	return t.argb_at(x, y) >> 24


func test_the_ring_is_brightest_on_its_band_and_gone_at_the_edge() -> void:
	var band := SceneFx.ring_alpha(SceneFx.RING_RADIUS)
	assert_true(band >= 250, "band %d" % band)
	assert_true(SceneFx.ring_alpha(0.0) < band / 3, "the centre is only a faint halo")
	assert_eq(0, SceneFx.ring_alpha(1.0), "nothing left at the quad's edge")
	var t := SceneFx.ring()
	assert_eq(64, t.width)
	assert_eq(0, alpha_at(t, 0, 0), "corners are empty")


func test_the_shaft_fades_down_and_softens_at_its_edges() -> void:
	var t := SceneFx.shaft()
	var mid := t.width / 2
	var top := alpha_at(t, mid, 0)
	var bottom := alpha_at(t, mid, t.height - 1)
	assert_true(top > 200, "top %d" % top)
	assert_true(bottom < 8, "bottom %d" % bottom)
	assert_true(alpha_at(t, 0, 0) < top / 2, "the edges are softer than the middle")


func test_the_flare_has_a_bright_core_and_empty_corners() -> void:
	var t := SceneFx.flare_tex()
	assert_true(alpha_at(t, t.width / 2, t.height / 2) > 220)
	assert_eq(0, alpha_at(t, 0, 0))
	assert_eq(0, alpha_at(t, t.width - 1, t.height - 1))


func test_a_gradient_runs_from_top_to_bottom_colour() -> void:
	var g := SceneFx.gradient(2, 8, 0xFF000000, 0xFFFFFFFF)
	assert_eq(0xFF000000, g.argb_at(0, 0))
	assert_eq(0xFFFFFFFF, g.argb_at(1, 7))
