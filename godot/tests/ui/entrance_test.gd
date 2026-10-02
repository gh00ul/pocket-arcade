extends PaTest
## ui/EntranceTest.kt: the pure parts of the UI's entrances and button presses.


func test_a_slot_of_an_entrance_clamps_before_and_after_itself() -> void:
	assert_near(0.0, Widgets.stage_of(0.0, 0.2, 0.6), 0.0)
	assert_near(0.0, Widgets.stage_of(0.2, 0.2, 0.6), 0.0)
	assert_near(0.5, Widgets.stage_of(0.4, 0.2, 0.6), 1e-6)
	assert_near(1.0, Widgets.stage_of(0.6, 0.2, 0.6), 0.0)
	assert_near(1.0, Widgets.stage_of(5.0, 0.2, 0.6), 0.0)
	assert_near(0.0, Widgets.stage_of(-1.0, 0.2, 0.6), 0.0)


func test_an_empty_slot_does_not_divide_by_zero() -> void:
	var v := Widgets.stage_of(0.5, 0.5, 0.5)
	assert_true(v == 0.0 or v == 1.0)
	assert_true(is_finite(Widgets.stage_of(0.9, 0.5, 0.5)))


func test_panel_pieces_queue_up_in_order_and_all_finish_inside() -> void:
	var e := PanelEntrance.new(UiEntrance.new())
	assert_eq(0, e.claim())
	assert_eq(1, e.claim())
	assert_eq(2, e.claim())
	var prev := -1.0
	for i in range(0, 41):
		var start := e.slot_start(i)
		assert_true(start >= prev and start <= 0.7, "slot %d" % i)
		# Each piece takes 0.3 of the entrance, so even the last is done by the end.
		assert_true(start + 0.3 <= 1.0001)
		prev = start
	# Early pieces are distinct steps apart, so the stagger is visible.
	assert_true(e.slot_start(1) - e.slot_start(0) > 0.03)


func test_a_button_sinks_when_pressed_and_pops_up_past_rest_on_release() -> void:
	assert_near(0.0, Widgets.press_offset(10.0, 0.0), 0.0)
	assert_near(8.0, Widgets.press_offset(10.0, 1.0), 1e-5)
	# The release overshoot (press below 0) lifts the cap above its rest, a little.
	var lifted := Widgets.press_offset(10.0, -0.25)
	assert_true(lifted < 0.0 and lifted > -4.0)
	# Monotonic: deeper press, deeper cap.
	assert_true(Widgets.press_offset(10.0, 0.6) > Widgets.press_offset(10.0, 0.3))
