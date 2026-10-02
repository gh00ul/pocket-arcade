class_name UiColumn
extends UiView
## Compose's Column: children top to bottom. Children without a weight are measured first (each
## with the height left), then the weighted ones share what remains in proportion; each is placed
## across by [member h_align] (horizontalAlignment) and down by [member arrange]
## (verticalArrangement), with [member spacing] dp between them (Arrangement.spacedBy).

enum Arrange { TOP, CENTER, BOTTOM, SPACE_BETWEEN }

## horizontalAlignment: -1 start, 0 centre, 1 end.
var h_align := -1.0
var arrange := Arrange.TOP
var spacing := 0.0


## Column(horizontalAlignment = CenterHorizontally).
func centered() -> UiColumn:
	h_align = 0.0
	ui_invalidate()
	return self


func spaced(dp: float) -> UiColumn:
	spacing = dp
	ui_invalidate()
	return self


func _measure_content(max_w: float, max_h: float, _fixed_w: bool, _fixed_h: bool) -> Vector2:
	var sizes := _measure_children(max_w, max_h)
	var used := 0.0
	var wide := 0.0
	for s in sizes:
		used += s.y
		wide = maxf(wide, s.x)
	used += px_round(spacing) * maxi(sizes.size() - 1, 0)
	return Vector2(wide, used)


## Every laid-out child's size under these maxima, in child order.
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
		var s := c.ui_measure(max_w, maxf(max_h - used, 0.0))
		sizes[i] = s
		used += s.y
	if total_weight > 0.0:
		var bounded := not is_inf(max_h)
		var remaining := maxf(max_h - used, 0.0)
		for i in n:
			var c := kids[i]
			if c.weight <= 0.0:
				continue
			var share := px_round(remaining * c.weight / total_weight) if bounded else INF
			sizes[i] = c.ui_measure(max_w, share, false, c.weight_fill and bounded)
	return sizes


func _layout_content(rect: Rect2) -> void:
	var kids := ui_children()
	var sizes := _measure_children(rect.size.x, rect.size.y)
	var n := kids.size()
	var total := 0.0
	for s in sizes:
		total += s.y
	var gap := px_round(spacing)
	var y := rect.position.y
	match arrange:
		Arrange.CENTER:
			y += (rect.size.y - total - gap * maxi(n - 1, 0)) * 0.5
		Arrange.BOTTOM:
			y += rect.size.y - total - gap * maxi(n - 1, 0)
		Arrange.SPACE_BETWEEN:
			if n > 1:
				gap = maxf((rect.size.y - total) / (n - 1), 0.0)
	for i in n:
		var s := sizes[i]
		var x := rect.position.x + (rect.size.x - s.x) * (1.0 + h_align) * 0.5
		kids[i].ui_place(Rect2(x, y, s.x, s.y))
		y += s.y + gap
