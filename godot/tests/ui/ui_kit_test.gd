extends PaTest
## Godot-only: the ui kit's layout, presses, semantics and animations behave as build-13's
## Compose did (sizes, weights, shrink-to-fit, the press spring, reduce motion, the rolling
## counter, coin flights, the panel's entrance). Driven with fixed steps, never the wall clock.

const DT := 1.0 / 60.0

var _density := 1.0
var _intensity := 1.0


func before_each() -> void:
	_density = Display.density
	_intensity = ScreenShake.intensity
	Display.density = 1.0
	ScreenShake.intensity = 1.0


func after_each() -> void:
	Display.density = _density
	ScreenShake.intensity = _intensity


## Lays [param root] out at [param area] now, top-down, as the frame's sort would.
static func layout_now(root: UiView, area: Vector2) -> void:
	root.size = area
	_sort_tree(root)


static func _sort_tree(v: UiView) -> void:
	v._sort()
	for c in v.get_children():
		if c is UiView:
			_sort_tree(c)


static func steps(v: UiView, seconds: float) -> void:
	var n := int(round(seconds / DT))
	for i in n:
		v.ui_step(DT)


static func press_event(at: Vector2, down: bool) -> InputEventMouseButton:
	var e := InputEventMouseButton.new()
	e.button_index = MOUSE_BUTTON_LEFT
	e.pressed = down
	e.position = at
	return e


func test_a_column_stacks_its_children_and_a_row_shares_what_is_left_by_weight() -> void:
	var col := UiColumn.new()
	col.h_align = 0.0
	host.add_child(col)
	var a := col.add(UiView.new().with_size(100.0, 40.0)) as UiView
	col.add(UiSpacer.h(10.0))
	var row := col.add(UiRow.new().fill_max_width()) as UiRow
	var left := row.add(UiView.new().with_size(60.0, 20.0)) as UiView
	var mid := row.add(UiView.new().with_height(30.0).with_weight(1.0)) as UiView
	var right := row.add(UiView.new().with_size(60.0, 20.0)) as UiView
	layout_now(col, Vector2(300.0, 500.0))
	assert_eq(Vector2(100.0, 40.0), a.size)
	assert_eq(Vector2(100.0, 0.0), a.position, "centred across the column")
	assert_eq(50.0, row.position.y, "below the spacer")
	assert_eq(Vector2(300.0, 30.0), row.size, "as tall as its tallest child")
	assert_eq(Vector2(180.0, 30.0), mid.size, "the weight takes what the fixed children leave")
	assert_eq(Vector2(60.0, 0.0), mid.position)
	assert_eq(Vector2(240.0, 0.0), right.position)
	assert_eq(Vector2(0.0, 0.0), left.position)
	# The column wraps its content: 40 + 10 + 30.
	assert_eq(Vector2(300.0, 80.0), col.ui_measure(300.0, INF))


func test_padding_and_margin_and_a_minimum_height_follow_compose() -> void:
	var box := UiView.new()
	box.padding = Vector4(16.0, 12.0, 16.0, 12.0)
	box.margin = Vector4(0.0, 8.0, 0.0, 12.0)
	box.min_height_dp = 64.0
	var inner := UiView.new().with_size(50.0, 20.0) as UiView
	box.add_child(inner)
	host.add_child(box)
	# 50 + 32 wide; max(20 + 24, 64) tall, plus the margins outside.
	assert_eq(Vector2(82.0, 84.0), box.ui_measure(INF, INF))
	var col := UiColumn.new()
	host.add_child(col)
	host.remove_child(box)
	col.add_child(box)
	layout_now(col, Vector2(200.0, 200.0))
	assert_eq(Vector2(0.0, 8.0), box.position, "the margin sits outside what is drawn")
	assert_eq(Vector2(82.0, 64.0), box.size)
	assert_eq(Vector2(16.0, 12.0), inner.position, "the padding insets the content")


func test_a_label_that_fits_keeps_its_size_and_one_that_does_not_shrinks() -> void:
	var t := ArcadeText.styled("A FAIRLY LONG LABEL", UiText.HEADING)
	host.add_child(t)
	var natural := t.text_size()
	assert_gt(natural.x, 0.0)
	assert_eq(natural, t.ui_measure(1000.0, 1000.0), "room to spare: its natural size")
	var narrow := t.ui_measure(natural.x / 2.0, 1000.0)
	assert_near(natural.x / 2.0, narrow.x, 1.0, "squeezed to half: half as wide")
	assert_near(natural.y / 2.0, narrow.y, 1.0, "and half as tall (it scales, it doesn't wrap)")
	var plain := ArcadeText.plain("A FAIRLY LONG LABEL", Color.WHITE, 2.6)
	host.add_child(plain)
	# Without fit the box is cut to the constraint but the text keeps its size.
	assert_eq(natural.x / 2.0, plain.ui_measure(natural.x / 2.0, 1000.0).x)
	layout_now(t, Vector2(narrow.x, narrow.y))
	assert_near(0.5, t.fit_scale(), 0.02)


func test_text_height_is_the_capitals_plus_the_shadow_pad() -> void:
	var t := ArcadeText.plain("HI", Color.WHITE, 3.0)
	host.add_child(t)
	var s := t.text_size()
	assert_eq(UiView.px_round(ArcadeFont.height(3.0) + 3.0 * 1.2), s.y)
	var two := ArcadeText.plain("HI\nTHERE", Color.WHITE, 3.0)
	host.add_child(two)
	# Two lines: a cap, the 3.2-unit gap, a cap, the pad.
	assert_eq(UiView.px_round(2.0 * 21.0 + 9.6 + 3.6), two.text_size().y)
	var bare := ArcadeText.plain("HI", Color.WHITE, 3.0, false)
	host.add_child(bare)
	assert_eq(UiView.px_round(21.0), bare.text_size().y, "no shadow, no pad")


func test_a_button_springs_down_clicks_on_release_and_overshoots_back_up() -> void:
	var clicks := [0]
	var b := ArcadeButton.make("PLAY", func() -> void: clicks[0] += 1)
	host.add_child(b)
	layout_now(b, b.ui_measure(INF, INF))
	# 16 + label + 16 wide; the label's cap and pad plus 12 + 12 tall (Compose's numbers).
	assert_eq(UiView.px_round(ArcadeFont.height(3.0) + 3.6) + 24.0, b.size.y)
	b._gui_input(press_event(b.size / 2.0, true))
	steps(b, 0.15)
	# spring(1, 1600): x = 1 - (1 + 40t)e^(-40t), 0.983 at 0.15 s.
	assert_near(0.983, b.press_value(), 0.01, "pressed down")
	assert_near(1.0 - Widgets.PRESS_SHRINK * b.press_value(), b.scale.x, 1e-4, "shrinks as it sinks")
	assert_eq(0, clicks[0], "no click until the finger lifts")
	b._gui_input(press_event(b.size / 2.0, false))
	assert_eq(1, clicks[0])
	var lowest := 1.0
	for i in 60:
		b.ui_step(DT)
		lowest = minf(lowest, b.press_value())
	assert_lt(lowest, -0.05, "the release pops the cap up past rest")
	assert_near(0.0, b.press_value(), 0.0, "and it settles")
	assert_false(b.is_processing(), "and stops drawing frames")


func test_a_press_that_slides_off_the_button_does_not_click() -> void:
	var clicks := [0]
	var b := ArcadeButton.make("PLAY", func() -> void: clicks[0] += 1)
	host.add_child(b)
	layout_now(b, b.ui_measure(INF, INF))
	b._gui_input(press_event(b.size / 2.0, true))
	var mm := InputEventMouseMotion.new()
	mm.position = b.size + Vector2(40.0, 40.0)
	b._gui_input(mm)
	b._gui_input(press_event(b.size + Vector2(40.0, 40.0), false))
	assert_eq(0, clicks[0])
	var off := ArcadeButton.make("NO", func() -> void: clicks[0] += 1, Pal.c(Pal.PINK), Color.WHITE, false)
	host.add_child(off)
	layout_now(off, off.ui_measure(INF, INF))
	off._gui_input(press_event(off.size / 2.0, true))
	off._gui_input(press_event(off.size / 2.0, false))
	assert_eq(0, clicks[0], "a disabled button doesn't click")
	assert_eq(UiColors.text_off, off.label().color, "and its words are dimmed")


func test_reduce_motion_snaps_the_press_and_the_entrances() -> void:
	ScreenShake.intensity = 0.0
	assert_false(UiMotion.enabled)
	var b := RoundButton.make(UiIcon.CLOSE, Callable(), Pal.c(Pal.RED))
	host.add_child(b)
	layout_now(b, b.ui_measure(INF, INF))
	b._gui_input(press_event(b.size / 2.0, true))
	assert_eq(1.0, b.press_value(), "down at once")
	b._gui_input(press_event(b.size / 2.0, false))
	assert_eq(0.0, b.press_value(), "and up with no overshoot")
	var e := UiEntrance.new(700.0)
	e.start()
	assert_eq(1.0, e.value(), "an entrance is finished before it starts")
	var v := UiView.new()
	host.add_child(v)
	e.stage(v, 0.2, 0.5, 16.0, true)
	assert_eq(Vector2.ZERO, v.layer_offset, "nothing rises")
	assert_eq(Vector2.ONE, v.layer_scale, "nothing pops")
	var n := RollingNumber.make(5, Color.WHITE, 3.0)
	host.add_child(n)
	n.set_value(40)
	assert_eq(40.0, n.shown_value(), "a counter just shows its number")


func test_round_buttons_and_texts_tell_a_screen_reader_what_they_are() -> void:
	assert_eq("Close", RoundButton.icon_label(UiIcon.CLOSE))
	assert_eq("Profile", RoundButton.icon_label(UiIcon.TROPHY))
	assert_eq("Sound on", RoundButton.icon_label(UiIcon.SOUND))
	assert_eq("Sound off", RoundButton.icon_label(UiIcon.MUTED))
	assert_eq("Camera view, first person", RoundButton.icon_label(UiIcon.EYE))
	assert_eq("Camera view, overhead", RoundButton.icon_label(UiIcon.CAMERA))
	assert_eq("Map", RoundButton.icon_label(UiIcon.MAP))
	assert_eq("Gear", RoundButton.icon_label(UiIcon.GEAR))
	var b := RoundButton.make(UiIcon.TROPHY, Callable(), Pal.c(Pal.PURPLE))
	host.add_child(b)
	assert_eq("Profile", b.accessibility_name)
	assert_eq(DisplayServer.ROLE_BUTTON, b.effective_role())
	var a := ArcadeButton.make("PLAY AGAIN\n1 TOKEN", Callable())
	host.add_child(a)
	assert_eq("play again 1 token", a.accessibility_name)
	assert_true(a.get_child(0).access_clear, "its painted label stays out of the way")
	var t := ArcadeText.styled("%s 5" % ArcadeFont.TOKEN, UiText.BODY)
	host.add_child(t)
	assert_eq("token 5", t.accessibility_name)
	var bar := ArcadeProgressBar.make(0.25, Color.WHITE, 10.0, "Tickets toward the next token")
	host.add_child(bar)
	assert_eq(DisplayServer.ROLE_PROGRESS_INDICATOR, bar.effective_role())
	assert_eq(0.25, bar.access_progress)
	var sw := ArcadeToggle.make(true, Callable(), "Haptics")
	host.add_child(sw)
	assert_eq(DisplayServer.ROLE_CHECK_BUTTON, sw.effective_role())
	assert_eq(1, sw.access_checked)


func test_a_rolling_number_rolls_through_the_values_ticks_and_grows_its_box() -> void:
	var ticks := [0]
	var n := RollingNumber.make(8, Color.WHITE, 3.0, func() -> void: ticks[0] += 1)
	host.add_child(n)
	var one_digit := n.ui_measure(INF, INF).x
	n.set_value(12)
	var seen_between := false
	for i in 120:
		n.ui_step(DT)
		var v := n.shown_value()
		if v > 8.5 and v < 11.5:
			seen_between = true
	assert_true(seen_between, "it counts through the values in between")
	assert_eq(12.0, n.shown_value())
	assert_gt(ticks[0], 0, "it ticks as it passes whole values")
	assert_le(ticks[0], 4)
	assert_gt(n.ui_measure(INF, INF).x, one_digit, "a second digit gets its own cell")
	# Going back down: the box keeps both cells while it rolls, then gives one back.
	n.set_value(3)
	n.ui_step(DT)
	assert_gt(n.ui_measure(INF, INF).x, one_digit)
	for i in 180:
		n.ui_step(DT)
	assert_eq(3.0, n.shown_value())
	assert_near(one_digit, n.ui_measure(INF, INF).x, 0.01)
	assert_eq("3", n.accessibility_name)


func test_coins_fly_and_land_and_reduce_motion_lands_them_at_once() -> void:
	var fx := CurrencyFx.new()
	fx.view_w = 1080.0
	fx.view_h = 2400.0
	var landed := [0]
	fx.launch(CurrencyFx.Kind.TOKEN, Vector2(100.0, 100.0), CurrencyFx.UNSPECIFIED, 0.0, 0.6, func() -> void: landed[0] += 1)
	fx.launch(CurrencyFx.Kind.TICKET, Vector2(500.0, 1800.0), Vector2(100.0, 100.0), 0.2, 0.5, func() -> void: landed[0] += 1)
	assert_true(fx.any())
	for i in 30:
		fx.update(DT)
	assert_eq(0, landed[0], "half a second in, nothing has landed")
	for i in 12:
		fx.update(DT)
	assert_eq(1, landed[0], "the token lands after 0.6 s")
	for i in 20:
		fx.update(DT)
	assert_eq(2, landed[0], "the ticket after its delay and flight")
	assert_false(fx.any())
	# The pool is fixed: past 32 in the air, the rest land at once.
	for i in 40:
		fx.launch(CurrencyFx.Kind.TOKEN, Vector2(1.0, 1.0), CurrencyFx.UNSPECIFIED, 0.0, 0.6, func() -> void: landed[0] += 1)
	assert_eq(2 + 8, landed[0])
	fx.clear()
	assert_false(fx.any())
	ScreenShake.intensity = 0.0
	fx.launch(CurrencyFx.Kind.TOKEN, Vector2(1.0, 1.0), CurrencyFx.UNSPECIFIED, 0.0, 0.6, func() -> void: landed[0] += 1)
	assert_eq(11, landed[0], "no motion: the counter updates at once")
	assert_false(fx.any())


func test_a_panel_fades_its_scrim_in_and_its_glass_boxes_arrive_one_after_another() -> void:
	var closed := [false]
	var p := ArcadePanel.make("PROFILE", Pal.c(Pal.PURPLE), func() -> void: closed[0] = true)
	var first := p.add_content(GlassBox.make()) as GlassBox
	var second := p.add_content(GlassBox.make(UiColors.good)) as GlassBox
	host.add_child(p)
	layout_now(p, Vector2(411.0, 914.0))
	assert_eq(0.0, p.entrance.value())
	assert_eq(0.0, first.layer_alpha, "the boxes wait for their turn")
	steps(p, 0.62 * 0.3)
	assert_gt(first.layer_alpha, second.layer_alpha, "the first box is ahead of the second")
	steps(p, 0.62)
	assert_eq(1.0, p.entrance.value())
	assert_eq(1.0, first.layer_alpha)
	assert_eq(1.0, second.layer_alpha)
	assert_eq(Vector2.ZERO, second.layer_offset, "everything has landed")
	assert_eq(1.0, p.body.layer_alpha)
	p.close_button.perform_click()
	assert_true(closed[0], "the close button closes")
	assert_eq("Close", p.close_button.accessibility_name)
	assert_true(p.blocks_input, "it blocks touches to the hall behind it")


func test_the_scan_tile_is_build_13s() -> void:
	var px := UiTheme.UiTexture.pixels()
	assert_eq(64 * 64, px.size())
	# Scanlines every fourth row, faint white, where no speck landed.
	var line_pixels := 0
	for x in 64:
		if px[4 * 64 + x] == 0x0EFFFFFF:
			line_pixels += 1
	assert_gt(line_pixels, 56)
	var light := 0
	var dark := 0
	for v in px:
		var a := (v >> 24) & 0xFF
		if (v & 0xFFFFFF) == 0xFFFFFF and a >= 6 and a < 24:
			light += 1
		elif (v & 0xFFFFFF) == 0 and a >= 16 and a < 46:
			dark += 1
	assert_gt(light, 100, "110 light specks, a few landing on each other")
	assert_gt(dark, 60, "70 dark specks")
	# The same seed makes the same tile every time.
	assert_eq(px, UiTheme.UiTexture.pixels())


func test_compose_easing_and_springs_land_where_compose_does() -> void:
	# FastOutSlowIn is CubicBezier(0.4, 0, 0.2, 1): ends pinned, the middle well past linear.
	assert_eq(0.0, UiAnim.curve(UiAnim.Easing.FAST_OUT_SLOW_IN, 0.0))
	assert_eq(1.0, UiAnim.curve(UiAnim.Easing.FAST_OUT_SLOW_IN, 1.0))
	assert_near(0.7746, UiAnim.curve(UiAnim.Easing.FAST_OUT_SLOW_IN, 0.5), 0.002)
	# A critically damped spring never passes its target; a bouncy one does.
	var a := UiAnim.Animatable.new(0.0)
	a.animate_spring(1.0, 1.0, 1500.0)
	var peak := 0.0
	while a.step(DT):
		peak = maxf(peak, a.value)
	assert_le(peak, 1.0001)
	assert_eq(1.0, a.value)
	var b := UiAnim.Animatable.new(0.0)
	b.animate_spring(1.0, 0.42, 700.0)
	peak = 0.0
	while b.step(DT):
		peak = maxf(peak, b.value)
	assert_gt(peak, 1.1, "the release spring overshoots")
	assert_eq(1.0, b.value)
