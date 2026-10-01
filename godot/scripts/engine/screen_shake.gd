class_name ScreenShake
extends RefCounted
## engine/Juice.kt ScreenShake: trauma-based screen shake: add trauma (0..1), offsets grow with
## trauma² and decay smoothly. Every shake in the game is scaled by [member intensity].

## How much of every shake shows: 1 as designed, 0 with the reduce-motion setting.
static var intensity := 1.0

var max_offset: float
var decay_per_sec: float
var trauma := 0.0
var _time := 0.0
var offset_x := 0.0
var offset_y := 0.0


func _init(p_max_offset: float = 14.0, p_decay_per_sec: float = 1.8) -> void:
	max_offset = p_max_offset
	decay_per_sec = p_decay_per_sec


func add(amount: float) -> void:
	trauma = minf(trauma + amount, 1.0)


func reset() -> void:
	trauma = 0.0
	offset_x = 0.0
	offset_y = 0.0


func update(dt: float) -> void:
	_time += dt
	trauma = maxf(trauma - decay_per_sec * dt, 0.0)
	var k := trauma * trauma * max_offset * intensity
	offset_x = k * (sin(_time * 71.0) * 0.6 + sin(_time * 113.0 + 1.3) * 0.4)
	offset_y = k * (sin(_time * 83.0 + 2.1) * 0.6 + sin(_time * 127.0 + 0.4) * 0.4)
