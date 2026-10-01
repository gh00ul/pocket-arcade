class_name FlickTracker
extends RefCounted
## engine/Touch.kt FlickTracker: tracks recent pointer samples and reports the release velocity of
## a flick, measured over the last [member window_ms] so a slow wind-up followed by a fast snap
## reads as a fast flick. The host forwards every touch sample with its own time (on Android the
## batched historical samples too), so the ring holds twice what a window needs at 240 Hz.

var window_ms: int
var _capacity: int
var _times := PackedInt64Array()
var _xs := PackedFloat32Array()
var _ys := PackedFloat32Array()
var _count := 0
var _head := 0
var start_x := 0.0
var start_y := 0.0
var start_time := 0


func _init(p_window_ms: int = 90) -> void:
	window_ms = p_window_ms
	_capacity = maxi(24, int(window_ms * 2 * 240 / 1000) + 2)
	_times.resize(_capacity)
	_xs.resize(_capacity)
	_ys.resize(_capacity)


func capacity() -> int:
	return _capacity


func reset(x: float, y: float, time_ms: int) -> void:
	_count = 0
	_head = 0
	start_x = x
	start_y = y
	start_time = time_ms
	add(x, y, time_ms)


func add(x: float, y: float, time_ms: int) -> void:
	_times[_head] = time_ms
	_xs[_head] = x
	_ys[_head] = y
	_head = (_head + 1) % _capacity
	if _count < _capacity:
		_count += 1


func _index(back: int) -> int:
	return ((_head - 1 - back) % _capacity + _capacity) % _capacity


func last_x() -> float:
	return start_x if _count == 0 else _xs[_index(0)]


func last_y() -> float:
	return start_y if _count == 0 else _ys[_index(0)]


## Velocity in units per second (Kotlin wrote it into an out Vec2).
func velocity() -> Vector2:
	if _count < 2:
		return Vector2.ZERO
	var newest := _index(0)
	var oldest := newest
	for i in range(1, _count):
		var idx := _index(i)
		if _times[newest] - _times[idx] > window_ms:
			break
		oldest = idx
	if oldest == newest:
		oldest = _index(1)
	var dt := maxi(_times[newest] - _times[oldest], 8) / 1000.0
	return Vector2((_xs[newest] - _xs[oldest]) / dt, (_ys[newest] - _ys[oldest]) / dt)
