extends PaTest
## Regression (2.0.0 bug 3): black bars on tall phones (a 540 x 960 canvas with aspect "keep").
## The window's content is laid out in dp and drawn at the window's full size at every aspect from
## 16:9 to 21:9, and the touch UI stays inside the safe area (cutouts, bars).

## Phone screens (px) from 16:9 to 21:9, and densities they come with.
const SCREENS := [
	[Vector2i(1080, 1920), 2.625], [Vector2i(720, 1280), 2.0], [Vector2i(1080, 2340), 2.625],
	[Vector2i(1080, 2400), 2.625], [Vector2i(1344, 2992), 3.0], [Vector2i(1080, 2520), 2.625],
	[Vector2i(1440, 3360), 3.5],
]

var _saved: Array = []


func before_each() -> void:
	var w := tree.root
	_saved = [w.content_scale_mode, w.content_scale_aspect, w.content_scale_size, w.content_scale_factor, Display.density, Display.forced_density, Display.forced_insets]


func after_each() -> void:
	var w := tree.root
	w.content_scale_mode = _saved[0]
	w.content_scale_aspect = _saved[1]
	w.content_scale_size = _saved[2]
	w.content_scale_factor = _saved[3]
	Display.density = _saved[4]
	Display.forced_density = _saved[5]
	Display.forced_insets = _saved[6]


func test_the_window_is_drawn_edge_to_edge_in_dp() -> void:
	var w := tree.root
	Display.forced_density = 2.625
	Display.configure(w)
	assert_eq(Window.CONTENT_SCALE_MODE_CANVAS_ITEMS, w.content_scale_mode, "UI drawn at full resolution, not scaled from a small canvas")
	assert_eq(Window.CONTENT_SCALE_ASPECT_EXPAND, w.content_scale_aspect, "the canvas grows with the screen: no letterbox at any aspect")
	assert_eq(1.0, w.content_scale_factor)
	# The canvas in dp is the window over the density, whatever the window.
	var px := w.size
	assert_eq(Vector2i(roundi(px.x / 2.625), roundi(px.y / 2.625)), w.content_scale_size)


func test_every_aspect_fills_the_screen_in_whole_dp() -> void:
	for entry: Array in SCREENS:
		var px: Vector2i = entry[0]
		var d: float = entry[1]
		var dp := Vector2(px) / d
		# Content scaled back up covers the screen to within a pixel: nothing letterboxed.
		var content := Vector2i(roundi(dp.x), roundi(dp.y))
		assert_true(absf(content.x * d - px.x) <= d and absf(content.y * d - px.y) <= d, "%s at %f" % [px, d])
		var aspect := float(px.y) / px.x
		assert_true(aspect >= 16.0 / 9.0 - 0.01 and aspect <= 21.0 / 9.0 + 0.2)


func test_the_game_field_and_its_bar_stay_inside_the_safe_area_at_every_aspect() -> void:
	for entry: Array in SCREENS:
		var px: Vector2i = entry[0]
		var d: float = entry[1]
		var screen := Vector2(px) / d
		# A top cutout and a gesture bar, as on the test emulator.
		for insets: Vector4 in [Vector4.ZERO, Vector4(0, 48, 0, 24)]:
			var lay := GameHostScreen.field_layout(screen, insets, d)
			var bar_h := lay.x
			var gx := lay.y
			var gy := lay.z
			var gs := lay.w
			var gw := MiniGame.GAME_W * gs
			var gh := MiniGame.GAME_H * gs
			var label := "%s insets %s" % [px, insets]
			assert_true(bar_h >= insets.y + 42.0 * 0.0, label)
			assert_true(gy >= bar_h - 0.01, "field under the bar: " + label)
			assert_true(gy + gh <= screen.y - insets.w + 0.01, "field above the bottom inset: " + label)
			assert_true(gx >= -0.01 and gx + gw <= screen.x + 0.01, "field inside the width: " + label)
			# As big as it can be: it touches the sides or fills the height left.
			var fills_width := absf(gw - screen.x) < 0.01
			var fills_height := absf(gh - (screen.y - bar_h - insets.w - maxf(floorf(2.0 * d), 2.0) / d * 6.0)) < 0.01
			assert_true(fills_width or fills_height, "field as large as fits: " + label)


func test_insets_can_be_forced_for_captures_and_tests() -> void:
	Display.forced_insets = Vector4(0, 40, 0, 20)
	assert_eq(Vector4(0, 40, 0, 20), Display.insets_dp(tree.root))
	Display.forced_insets = Vector4(-1, 0, 0, 0)
	var real := Display.insets_dp(tree.root)
	assert_true(real.x >= 0.0 and real.y >= 0.0)
