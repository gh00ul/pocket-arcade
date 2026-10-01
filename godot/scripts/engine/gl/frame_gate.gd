class_name FrameGate
extends RefCounted
## engine/gl/GfxQuality.kt FrameGate: lets frames through at the GfxQuality cap. Pure logic, fed
## the time (microseconds here, nanoseconds in Kotlin): a frame is due once an interval (less the
## slack) has passed since the last one taken.

var _last_usec := 0


## Microseconds until a frame is due (0 if it is due now). Takes nothing.
func wait_usec(now_usec: int, cap: int, display_hz: float) -> int:
	if _last_usec == 0 or not GfxQuality.cap_applies(cap, display_hz):
		return 0
	var gap: int = 1000000 / cap - GfxQuality.CAP_SLACK_USEC
	var left := gap - (now_usec - _last_usec)
	# A clock that stepped back must not hold a frame for longer than one interval.
	return clampi(left, 0, gap)


func take(now_usec: int) -> void:
	_last_usec = now_usec


## Whether a frame is due at [param now_usec]; if so it is taken.
func due(now_usec: int, cap: int, display_hz: float) -> bool:
	if wait_usec(now_usec, cap, display_hz) > 0:
		return false
	take(now_usec)
	return true
