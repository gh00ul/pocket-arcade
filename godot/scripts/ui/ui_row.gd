class_name UiRow
extends UiView
## Compose's Row: children left to right. Children without a weight are measured first, each with
## the width still left (so a label that shrinks to fit takes only what is left), then the
## weighted ones share the rest in proportion; each is placed down by [member v_align]
## (verticalAlignment) and across by [member arrange] (horizontalArrangement), with
## [member spacing] dp between them (Arrangement.spacedBy).

enum Arrange { START, CENTER, END, SPACE_BETWEEN }

## verticalAlignment: -1 top, 0 centre, 1 bottom.
var v_align := -1.0
var arrange := Arrange.START
var spacing := 0.0


## Row(verticalAlignment = CenterVertically).
func centered() -> UiRow:
	v_align = 0.0
	ui_invalidate()
	return self


func spaced(dp: float) -> UiRow:
	spacing = dp
	ui_invalidate()
	return self


func _measure_content(max_w: float, max_h: float, _fixed_w: bool, _fixed_h: bool) -> Vector2:
	var sizes := _measure_children(max_w, max_h)
	var used := 0.0
	var tall := 0.0
	for s in sizes:
		used += s.x
		tall = maxf(tall, s.y)
	used += px_round(spacing) * maxi(sizes.size() - 1, 0)
	return Vector2(used, tall)


func _measure_children(max_w: float, max_h: float) -> PackedVector2Array:
	var kids := ui_children()
	var n := kids.size()
	var sizes := PackedVector2Array()
	sizes.resize(n)
	var gap := px_round(spacing)
	var used := gap * maxi(n - 1, 0)
	var total_weight := 0.0
	for i in n:
		var c := kids[i]
		if c.weight > 0.0:
			total_weight += c.weight
			continue
		var s := c.ui_measure(maxf(max_w - used, 0.0), max_h)
		sizes[i] = s
		used += s.x
	if total_weight > 0.0:
		var bounded := not is_inf(max_w)
		var remaining := maxf(max_w - used, 0.0)
		for i in n:
			var c := kids[i]
			if c.weight <= 0.0:
				continue
			var share := px_round(remaining * c.weight / total_weight) if bounded else INF
			sizes[i] = c.ui_measure(share, max_h, c.weight_fill and bounded, false)
	return sizes


func _layout_content(rect: Rect2) -> void:
	var kids := ui_children()
	var sizes := _measure_children(rect.size.x, rect.size.y)
	var n := kids.size()
	var total := 0.0
	for s in sizes:
		total += s.x
	var gap := px_round(spacing)
	var x := rect.position.x
	match arrange:
		Arrange.CENTER:
			x += (rect.size.x - total - gap * maxi(n - 1, 0)) * 0.5
		Arrange.END:
			x += rect.size.x - total - gap * maxi(n - 1, 0)
		Arrange.SPACE_BETWEEN:
			if n > 1:
				gap = maxf((rect.size.x - total) / (n - 1), 0.0)
	for i in n:
		var s := sizes[i]
		var y := rect.position.y + (rect.size.y - s.y) * (1.0 + v_align) * 0.5
		kids[i].ui_place(Rect2(x, y, s.x, s.y))
		x += s.x + gap
