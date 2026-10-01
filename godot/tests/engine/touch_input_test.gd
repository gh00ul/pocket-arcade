extends PaTest
## Godot: the touch stream (the plugin's raw samples, or Godot's events) and build-13's thumb zones
## (engine/Touch.kt thumbZoneGestureExclusion).


func test_the_plugins_samples_decode_in_order_on_godots_clock() -> void:
	# The plugin's clock was 500 at the call, Godot's 10500: a sample at 480 happened at 10480.
	var raw := PackedInt32Array([500,
		AndroidBridge.TOUCH_DOWN, 0, 160, 320, 470,
		AndroidBridge.TOUCH_MOVE, 0, 168, 300, 475,
		AndroidBridge.TOUCH_MOVE, 1, 800, 16, 478,
		AndroidBridge.TOUCH_UP, 0, 170, 296, 480,
		AndroidBridge.TOUCH_CANCEL, 1, 800, 16, 490])
	var out := TouchInput.decode(raw, 10500)
	assert_eq(5, out.size())
	assert_eq([TouchType.DOWN, 0, 10.0, 20.0, 10470], out[0])
	assert_eq([TouchType.MOVE, 0, 10.5, 18.75, 10475], out[1])
	assert_eq([TouchType.MOVE, 1, 50.0, 1.0, 10478], out[2])
	assert_eq([TouchType.UP, 0, 10.625, 18.5, 10480], out[3])
	# A cancelled pointer ends like a lift.
	assert_eq(TouchType.UP, out[4][0])
	assert_eq([], TouchInput.decode(PackedInt32Array(), 0))


func test_godot_events_become_samples_while_capturing() -> void:
	var t := TouchInput.new()
	var down := InputEventScreenTouch.new()
	down.index = 2
	down.position = Vector2(40, 50)
	down.pressed = true
	assert_false(t.handle_event(down), "not capturing yet")
	t.set_capturing(true)
	assert_true(t.handle_event(down))
	var drag := InputEventScreenDrag.new()
	drag.index = 2
	drag.position = Vector2(44, 58)
	assert_true(t.handle_event(drag))
	var up := InputEventScreenTouch.new()
	up.index = 2
	up.position = Vector2(44, 58)
	up.pressed = false
	assert_true(t.handle_event(up))
	assert_false(t.handle_event(InputEventKey.new()))
	var got := t.poll()
	assert_eq(3, got.size())
	assert_eq(TouchType.DOWN, got[0][0])
	assert_eq(2, got[0][1])
	assert_eq(TouchType.MOVE, got[1][0])
	assert_eq(44.0, got[1][2])
	assert_eq(TouchType.UP, got[2][0])
	assert_eq([], t.poll())


func test_thumb_zones_are_build_13s_strips() -> void:
	# A 1080 x 2400 canvas at 2.75 px/dp: 200 dp tall (550 px), 80 dp wide (220 px), each bottom corner.
	var r := TouchInput.thumb_zones(Rect2(0, 0, 1080, 2400), 2.75)
	assert_eq(PackedInt32Array([0, 1850, 220, 2400, 860, 1850, 1080, 2400]), r)
	# A short, narrow area: the zone is no taller than the area and no wider than half of it.
	var s := TouchInput.thumb_zones(Rect2(10, 20, 300, 400), 3.0)
	assert_eq(PackedInt32Array([10, 20, 160, 420, 160, 20, 310, 420]), s)
