class_name FloatingTexts
extends RefCounted
## engine/Juice.kt FloatingTexts: short-lived popup texts ("+50", "SWISH!") that pop in, float
## upward and fade.


class Item:
	extends RefCounted
	var text := ""
	var x := 0.0
	var y := 0.0
	var vy := 0.0
	var life := 0.0
	var max_life := 1.0
	var color := Color.WHITE
	var size := 2.0
	var active := false


var _items: Array[Item] = []
var _next := 0


func _init(capacity: int = 24) -> void:
	for i in capacity:
		_items.append(Item.new())


## [param color]: a Color or an ARGB int.
func add(text: String, x: float, y: float, color: Variant, size: float = 2.0, life: float = 0.9, rise: float = 60.0) -> void:
	var it := _items[_next]
	_next = (_next + 1) % _items.size()
	it.text = text
	it.x = x
	it.y = y
	it.vy = -rise
	it.life = life
	it.max_life = life
	it.color = color if color is Color else Pal.c(int(color))
	it.size = size
	it.active = true


func clear() -> void:
	for it in _items:
		it.active = false


func update(dt: float) -> void:
	for it in _items:
		if not it.active:
			continue
		it.life -= dt
		if it.life <= 0.0:
			it.active = false
			continue
		it.y += it.vy * dt
		it.vy *= maxf(1.0 - 2.5 * dt, 0.0)


## Draws in a space where one unit is [param unit] drawing units, offset by the origin.
func draw(ds: DrawScope, origin_x: float, origin_y: float, unit: float) -> void:
	for it in _items:
		if not it.active:
			continue
		var age := 1.0 - it.life / it.max_life
		var pop := MathUtil.ease_out_back(age / 0.15) if age < 0.15 else 1.0
		var alpha := it.life / 0.3 if it.life < 0.3 else 1.0
		var scale := it.size * unit * (0.6 + 0.4 * pop)
		ArcadeFont.draw_centered(ds, it.text, origin_x + it.x * unit, origin_y + it.y * unit, scale, it.color, alpha)


## Active popups, for tests: [text, x, y] each.
func active() -> Array:
	var out: Array = []
	for it in _items:
		if it.active:
			out.append([it.text, it.x, it.y])
	return out
