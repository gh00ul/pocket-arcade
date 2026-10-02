class_name PanelEntrance
extends RefCounted
## Widgets.kt PanelEntrance: the entrance of the panel a piece sits in: its [member progress] and a
## counter that hands the pieces inside their place in the queue ([method claim]), so they arrive
## one after another.

var progress: UiEntrance
var _next := 0


func _init(p_progress: UiEntrance = null) -> void:
	progress = p_progress


## The next free place in the queue (0 for the first piece).
func claim() -> int:
	_next += 1
	return _next - 1


## When (0..1 of the entrance) the piece at [param index] starts arriving: a step behind the one
## before, never later than 0.7.
func slot_start(index: int) -> float:
	return minf(0.2 + 0.06 * index, 0.7)
