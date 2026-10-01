class_name TouchInput
extends RefCounted
## The touch stream a game reads, every sample in order with its own time (build-13's game canvas
## handed the game each batched MotionEvent sample, oldest first, so a flick's speed is measured
## on all of them). On Android the plugin records the raw stream, history included; elsewhere
## Godot's screen touch and drag events stand in, timed as they are handled.
##
## A sample is [type (TouchType), pointer id, x, y (window pixels), time (ms on Godot's clock)].

## Feed Godot's own touch events through [method handle_event] (no plugin).
var _from_events := true
var _queue: Array = []
var _capturing := false


func _init() -> void:
	_from_events = not AndroidBridge.available()


## Starts or stops listening (the host turns it on while a round is playing).
func set_capturing(on: bool) -> void:
	if on == _capturing:
		return
	_capturing = on
	_queue.clear()
	if not _from_events:
		AndroidBridge.touch_capture(on)
		if not on:
			AndroidBridge.touch_take()


func capturing() -> bool:
	return _capturing


## While on, the first finger's stream is delivered as it arrives rather than once a frame.
func set_unbuffered(on: bool) -> void:
	if not _from_events:
		AndroidBridge.touch_unbuffered(on)


## Takes a Godot input event (when the stream comes from Godot's events); true if it was a touch.
func handle_event(event: InputEvent) -> bool:
	if not _from_events or not _capturing:
		return false
	var now := Time.get_ticks_msec()
	if event is InputEventScreenTouch:
		var t := event as InputEventScreenTouch
		var kind := TouchType.DOWN if t.pressed else TouchType.UP
		_queue.append([kind, t.index, t.position.x, t.position.y, now])
		return true
	if event is InputEventScreenDrag:
		var d := event as InputEventScreenDrag
		_queue.append([TouchType.MOVE, d.index, d.position.x, d.position.y, now])
		return true
	return false


## Every sample since the last call, oldest first.
func poll() -> Array:
	if _from_events:
		var out := _queue
		_queue = []
		return out
	return decode(AndroidBridge.touch_take(), Time.get_ticks_msec())


## The plugin's record ([param raw]: its clock at the call, then the samples) as samples on Godot's
## clock, [param now_ms] being Godot's clock at the call.
static func decode(raw: PackedInt32Array, now_ms: int) -> Array:
	var out: Array = []
	if raw.is_empty():
		return out
	var offset := now_ms - raw[0]
	var i := 1
	while i + AndroidBridge.TOUCH_STRIDE <= raw.size():
		var action := raw[i]
		var kind := TouchType.MOVE
		if action == AndroidBridge.TOUCH_DOWN:
			kind = TouchType.DOWN
		elif action == AndroidBridge.TOUCH_UP or action == AndroidBridge.TOUCH_CANCEL:
			kind = TouchType.UP
		out.append([kind, raw[i + 1], raw[i + 2] / AndroidBridge.TOUCH_FIXED, raw[i + 3] / AndroidBridge.TOUCH_FIXED, raw[i + 4] + offset])
		i += AndroidBridge.TOUCH_STRIDE
	return out


## build-13's thumb zones (engine/Touch.kt thumbZoneGestureExclusion): no Back swipes from the
## bottom of either side of [param area] (window pixels), [param zone_height] tall and
## [param edge_width] wide, in dp scaled by [param density]. As rectangles for
## [method AndroidBridge.set_gesture_exclusion].
static func thumb_zones(area: Rect2, density: float, zone_height_dp: float = 200.0, edge_width_dp: float = 80.0) -> PackedInt32Array:
	var w := area.size.x
	var h := area.size.y
	var zone := minf(zone_height_dp * density, h)
	var edge := minf(edge_width_dp * density, w / 2.0)
	var x0 := area.position.x
	var y1 := area.position.y + h
	return PackedInt32Array([
		roundi(x0), roundi(y1 - zone), roundi(x0 + edge), roundi(y1),
		roundi(x0 + w - edge), roundi(y1 - zone), roundi(x0 + w), roundi(y1),
	])
