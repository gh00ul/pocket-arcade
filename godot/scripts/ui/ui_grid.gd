class_name UiGrid
extends UiView
## The layout of Compose's LazyVerticalGrid(GridCells.Fixed(n)): items fill [member columns]
## equal cells a line at a time (the cells split the width in whole pixels as Compose does: the
## leftover pixels go one each to the first cells), [member h_spacing] / [member v_spacing] dp
## apart; an item with [member UiView.grid_span] takes a line of its own; a line is as tall as its
## tallest item, items sit at its top. It does not scroll by itself: put it in a [UiScroll], as the
## lazy grid scrolls. Every item is laid out (the menus' grids hold a few dozen at most).

var columns := 3
var h_spacing := 0.0
var v_spacing := 0.0


static func fixed(count: int, spacing: float) -> UiGrid:
	var g := UiGrid.new()
	g.columns = count
	g.h_spacing = spacing
	g.v_spacing = spacing
	return g


## The cells' widths (dp) across [param width]: Compose's calculateCellsCrossAxisSize in pixels.
func cell_widths(width: float) -> PackedFloat32Array:
	var d := maxf(Display.density, 0.01)
	var n := maxi(columns, 1)
	var grid_px := maxi(roundi(width * d) - roundi(h_spacing * d) * (n - 1), 0)
	var slot := grid_px / n
	var rest := grid_px % n
	var out := PackedFloat32Array()
	out.resize(n)
	for i in n:
		out[i] = float(slot + (1 if i < rest else 0)) / d
	return out


## Lines of item indices (an index list per line).
func _lines() -> Array[PackedInt32Array]:
	var kids := ui_children()
	var lines: Array[PackedInt32Array] = []
	var cur := PackedInt32Array()
	for i in kids.size():
		if kids[i].grid_span:
			if not cur.is_empty():
				lines.append(cur)
				cur = PackedInt32Array()
			lines.append(PackedInt32Array([i]))
			continue
		cur.append(i)
		if cur.size() >= columns:
			lines.append(cur)
			cur = PackedInt32Array()
	if not cur.is_empty():
		lines.append(cur)
	return lines


func _measure_content(max_w: float, _max_h: float, _fixed_w: bool, _fixed_h: bool) -> Vector2:
	var w := max_w if not is_inf(max_w) else 0.0
	var kids := ui_children()
	var cells := cell_widths(w)
	var total := 0.0
	var lines := _lines()
	for li in lines.size():
		var line := lines[li]
		var tall := 0.0
		for k in line.size():
			var c := kids[line[k]]
			var cw := w if c.grid_span else cells[k]
			tall = maxf(tall, c.ui_measure(cw, INF, true, false).y)
		total += tall
	total += px_round(v_spacing) * maxi(lines.size() - 1, 0)
	return Vector2(w, total)


func _layout_content(rect: Rect2) -> void:
	var kids := ui_children()
	var cells := cell_widths(rect.size.x)
	var gap := px_round(h_spacing)
	var y := rect.position.y
	for line in _lines():
		var tall := 0.0
		var x := rect.position.x
		for k in line.size():
			var c := kids[line[k]]
			var cw := rect.size.x if c.grid_span else cells[k]
			var s := c.ui_measure(cw, INF, true, false)
			c.ui_place(Rect2(x, y, s.x, s.y))
			tall = maxf(tall, s.y)
			x += cw + gap
		y += tall + px_round(v_spacing)
