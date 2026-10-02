extends PaTest
## hub/FigureShadowTest.kt: where kids' shadows fall: away from the lamps above, smoothly, and never
## longer than the cap.

var _shadow: FigureShadow
var _out := PackedFloat64Array([0.0, 0.0])


func before_each() -> void:
	var px := PackedInt32Array()
	px.resize(16)
	px.fill(0x80000000)
	_shadow = FigureShadow.new(PaTexture.from_argb(4, 4, px).full())


## The hall's real key light and [param lamps].
func _hall(lamps: Array = []) -> Lighting:
	var l := Lighting.new()
	l.set_direction(0.1, 1.0, 0.35)
	for p: PointLight in lamps:
		l.points.append(p)
	return l


func _downlight(x: float, z: float) -> PointLight:
	return PointLight.new(x, 150.0, z, 1.0, 0.9, 0.78, 175.0, 0.55)


## The lean at ([param x], [param z]) as [x, z].
func _lean(l: Lighting, x: float, z: float) -> Array[float]:
	_shadow.cast(l, x, z, _out)
	return [_out[0], _out[1]]


static func _len(v: Array[float]) -> float:
	return sqrt(v[0] * v[0] + v[1] * v[1])


func test_with_no_lamps_the_shadow_runs_away_from_the_key_light() -> void:
	var v := _lean(_hall(), 300.0, 300.0)
	# The key light stands over +x and +z of the hall, so shadows run towards -x and -z.
	assert_true(v[0] < 0.0, "lean x %f" % v[0])
	assert_true(v[1] < 0.0, "lean z %f" % v[1])
	assert_true(_len(v) > FigureShadow.MIN_LEAN)


func test_a_kid_right_under_a_downlight_has_no_shadow_to_speak_of() -> void:
	var under := _lean(_hall([_downlight(300.0, 300.0)]), 300.0, 300.0)
	var away := _lean(_hall([_downlight(300.0, 300.0)]), 300.0 + 60.0, 300.0)
	assert_true(_len(under) < _len(away), "under the lamp: %s" % str(under))
	assert_true(_len(under) < FigureShadow.MIN_LEAN * 2.0, "under the lamp: %s" % str(under))


func test_the_shadow_leans_away_from_a_lamp_off_to_one_side() -> void:
	# The lamp is to the kid's left (smaller x): the shadow falls to the right (larger x).
	var lx := _lean(_hall([_downlight(240.0, 300.0)]), 300.0, 300.0)[0]
	assert_true(lx > 0.1, "lean x %f" % lx)
	# The lamp is behind the kid (smaller z): the shadow falls towards the entrance (larger z).
	var lz := _lean(_hall([_downlight(300.0, 240.0)]), 300.0, 300.0)[1]
	assert_true(lz > 0.1, "lean z %f" % lz)


func test_lamps_on_either_side_cancel_and_low_lamps_are_ignored() -> void:
	var pair := _hall([_downlight(240.0, 300.0), _downlight(360.0, 300.0)])
	var lx := _lean(pair, 300.0, 300.0)[0]
	var single := _lean(_hall([_downlight(240.0, 300.0)]), 300.0, 300.0)[0]
	assert_true(absf(lx) < single / 3.0, "two lamps either side: %f vs one: %f" % [lx, single])
	# The floor glow of a cabinet is only 20 up: no floor shadow from it.
	var glow := PointLight.new(240.0, 20.0, 300.0, 1.0, 0.2, 0.9, 70.0, 1.0)
	assert_eq(_lean(_hall(), 300.0, 300.0), _lean(_hall([glow]), 300.0, 300.0))


func test_lamps_out_of_reach_do_not_count() -> void:
	var far := _hall([_downlight(0.0, 0.0)])
	assert_eq(_lean(_hall(), 500.0, 500.0), _lean(far, 500.0, 500.0))


func test_the_shadow_swings_smoothly_as_a_kid_walks_between_lamps() -> void:
	var l := _hall([_downlight(76.0, 90.0), _downlight(196.0, 90.0), _downlight(76.0, 210.0), _downlight(196.0, 210.0)])
	var prev := _lean(l, 60.0, 60.0)
	var x := 60.0
	while x < 220.0:
		x += 1.0
		var now := _lean(l, x, 60.0 + (x - 60.0) * 0.9)
		var jump := sqrt((now[0] - prev[0]) * (now[0] - prev[0]) + (now[1] - prev[1]) * (now[1] - prev[1]))
		if not (jump < 0.05):
			fail("jump of %f at x=%f" % [jump, x])
			return
		prev = now


func test_the_shadow_is_never_longer_than_the_cap() -> void:
	# A very strong lamp just past the edge of a kid.
	var l := _hall([PointLight.new(300.0, 110.0, 300.0, 1.0, 1.0, 1.0, 400.0, 50.0)])
	for dx in range(-170, 171, 17):
		for dz in range(-170, 171, 17):
			var v := _lean(l, 300.0 + dx, 300.0 + dz)
			assert_true(_len(v) <= FigureShadow.MAX_LEAN + 1e-4, "lean %f at %d,%d" % [_len(v), dx, dz])


func _quads_for(l: Lighting, x: float, z: float) -> int:
	var r := Renderer3D.new(64, 64)
	r.start_frame()
	r.camera.look_at(x, 300.0, z + 40.0, x, 0.0, z, PI / 3.0, 64, 64)
	r.clear(0xFF000000)
	for p in l.points:
		r.lighting.points.append(p)
	r.lighting.set_direction(l.dir_x, l.dir_y, l.dir_z)
	_shadow.draw(r, x, z, 1.0)
	return r.polys_drawn


func test_draws_the_blob_always_and_the_cast_pieces_when_the_shadow_is_long_enough() -> void:
	# Off to one side of a lamp: contact blob, body stripe and head.
	assert_eq(3, _quads_for(_hall([_downlight(240.0, 300.0)]), 300.0, 300.0))
	# Right under it, with no key light leaning it: just the blob.
	var overhead := _hall([_downlight(300.0, 300.0)])
	overhead.set_direction(0.0, 1.0, 0.0)
	assert_eq(1, _quads_for(overhead, 300.0, 300.0))


func test_the_reach_covers_the_whole_shadow() -> void:
	# Head at full lean, plus half the head's disc, must sit inside the culling reach.
	assert_true(FigureShadow.REACH >= FigureShadow.MAX_LEAN * FigureShadow.HEAD_Y + FigureShadow.HEAD_SIZE / 2.0)


func test_the_shadow_is_soft_enough_not_to_double_up_in_a_dark_blot() -> void:
	# Each piece has the shadow texture's centre alpha times its own; overlapping pieces (feet under
	# the body stripe) must stay a light shadow, not black.
	var worst := 1.0 - (1.0 - FigureShadow.CONTACT_ALPHA) * (1.0 - FigureShadow.BODY_ALPHA) * (1.0 - FigureShadow.HEAD_ALPHA)
	assert_true(worst < 0.9, "combined alpha %f" % worst)
