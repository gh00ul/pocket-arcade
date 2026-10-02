class_name UiShrink
extends UiView
## Widgets.kt Modifier.shrinkToFit(): lets its one child be as wide as it likes, then scales it
## down (never up) to fit the width it was offered. A label that would run past its box shrinks
## instead: nothing in the menus may overflow on a narrow phone. It reports the scaled size, so
## neighbours pack against what is seen.

var _scale := 1.0


static func of(child: UiView) -> UiShrink:
	var s := UiShrink.new()
	s.add_child(child)
	return s


func _child() -> UiView:
	var kids := ui_children()
	return kids[0] if not kids.is_empty() else null


func _measure_content(max_w: float, max_h: float, _fixed_w: bool, _fixed_h: bool) -> Vector2:
	var c := _child()
	if c == null:
		return Vector2.ZERO
	var nat := c.ui_measure(INF, INF)
	var d := maxf(Display.density, 0.01)
	var s := Widgets.shrink_scale(roundi(nat.x * d), Widgets.CONSTRAINTS_INFINITY if is_inf(max_w) else roundi(max_w * d))
	return Vector2(minf(px_round(nat.x * s), max_w), minf(px_round(nat.y * s), max_h))


func _layout_content(rect: Rect2) -> void:
	var c := _child()
	if c == null:
		return
	var nat := c.ui_measure(INF, INF)
	var d := maxf(Display.density, 0.01)
	_scale = Widgets.shrink_scale(roundi(nat.x * d), roundi(rect.size.x * d))
	c.layer_origin = Vector2.ZERO
	c.ui_place(Rect2(rect.position, nat))
	c.set_layer(c.layer_alpha, Vector2(_scale, _scale), c.layer_offset)


## The scale the child is shown at (1 when it fits).
func fit_scale() -> float:
	return _scale
