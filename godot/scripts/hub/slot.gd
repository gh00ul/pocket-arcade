class_name Slot
extends RefCounted
## hub/HubMap.kt Slot: a pre-sized stretch of floor for one machine's bank: room for
## [member count] cabinets of at most [member max_w] wide, [member max_d] deep and [member max_h]
## tall, [member gap] apart, from [member x0] with their backs at [member back]. That floor and the
## play spots in front of it are kept clear of everything else, so any cabinet that fits can't
## overlap anything. The cabinets stand [member gap] apart whatever their size, packed against the
## [member anchor] side (-1 the left end, 1 the right end, 0 centred), so a slot by a wall keeps its
## cabinets against the wall. A slot with a [member shape] (a MiniGame.CabinetShape) is that
## shape's bank; a spare slot ([member shape] = [constant SPARE], Kotlin's null) takes the first
## machine that has no bank of its own.

const SPARE := -1

var shape: int
var count: int
var x0: float
var back: float
var gap: float
var max_w: float
var max_d: float
var max_h: float
var anchor: int

var x1: float:
	get:
		return x0 + count * max_w + (count - 1) * gap

## The floor the slot reserves: every cabinet at its largest plus the play spots in front.
var area: Box:
	get:
		return Box.new(x0, back, x1, back + max_d + HubLayout.PROMPT_DEPTH)


func _init(p_shape: int, p_count: int, p_x0: float, p_back: float, p_gap: float, p_max_w: float, p_max_d: float,
		p_max_h: float, p_anchor: int = 0) -> void:
	shape = p_shape
	count = p_count
	x0 = p_x0
	back = p_back
	gap = p_gap
	max_w = p_max_w
	max_d = p_max_d
	max_h = p_max_h
	anchor = p_anchor


## Left edge of cabinet [param k] when each is [param w] wide.
func cabinet_x(k: int, w: float) -> float:
	var span := count * w + (count - 1) * gap
	var start: float
	if anchor < 0:
		start = x0
	elif anchor > 0:
		start = x1 - span
	else:
		start = (x0 + x1 - span) / 2.0
	return start + k * (w + gap)
