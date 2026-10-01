class_name DrawScope
extends RefCounted
## Compose's DrawScope over a Godot CanvasItem, for ported `render(scope)` / `drawAttract` code.
## Use it inside the item's `_draw()`: `var ds := DrawScope.new(self, size)`.
##
## Mapping (positional, Compose order where it is unambiguous; docs/PORTING.md lists them):
##  drawRect(color|brush, topLeft, size, alpha, style)        → draw_rect(paint, top_left, size, alpha, style)
##  drawCircle(color|brush, radius, center, alpha, style)     → draw_circle(paint, radius, center, alpha, style)
##  drawRoundRect(…, cornerRadius, …)                         → draw_round_rect(paint, top_left, size, corner, alpha, style)
##  drawLine(color, start, end, strokeWidth, cap, …, alpha)   → draw_line(paint, start, end, width, round_cap, alpha)
##  drawPath(path, color|brush, alpha, style)                 → draw_path(path, paint, alpha, style)
##  drawArc(color, start°, sweep°, useCenter, topLeft, size…) → draw_arc(paint, start_deg, sweep_deg, use_center, top_left, size, alpha, style)
##  drawOval(color, topLeft, size, alpha, style)              → draw_oval(paint, top_left, size, alpha, style)
##  translate/scale/rotate/withTransform/inset/clipRect { }  → push() … transforms … pop()
## `style`: null (fill), a float stroke width, or a [PaStroke]. `paint`: a Color, an ARGB int or a [PaBrush].

var ci: CanvasItem
## The size of the area being drawn (Compose's `size`), shrunk by insets.
var size: Vector2
var _xf := Transform2D.IDENTITY
var _applied := Transform2D.IDENTITY
var _stack: Array = []
var _clip := Rect2()
var _has_clip := false
## Circle segments per full turn (scaled down for small circles).
const MAX_SEGMENTS := 48


func _init(item: CanvasItem, area: Vector2) -> void:
	ci = item
	size = area
	ci.draw_set_transform_matrix(Transform2D.IDENTITY)


func center() -> Vector2:
	return size / 2.0


## The current transform (field units → the item's coordinates).
func transform() -> Transform2D:
	return _xf


# ---------------------------------------------------------------- transforms

func push() -> void:
	_stack.append([_xf, size, _clip, _has_clip])


func pop() -> void:
	if _stack.is_empty():
		return
	var s: Array = _stack.pop_back()
	_xf = s[0]
	size = s[1]
	_clip = s[2]
	_has_clip = s[3]


func translate(dx: float, dy: float) -> void:
	_xf = _xf * Transform2D(0.0, Vector2(dx, dy))


## Compose `scale(sx, sy, pivot)` (pivot defaults to the centre of the area).
func scale_by(sx: float, sy: float = NAN, pivot: Vector2 = Vector2(NAN, NAN)) -> void:
	if is_nan(sy):
		sy = sx
	if is_nan(pivot.x):
		pivot = center()
	_xf = _xf * Transform2D(0.0, pivot) * Transform2D(0.0, Vector2(sx, sy), 0.0, Vector2.ZERO) * Transform2D(0.0, -pivot)


## Compose `rotate(degrees, pivot)` (pivot defaults to the centre of the area).
func rotate_deg(degrees: float, pivot: Vector2 = Vector2(NAN, NAN)) -> void:
	if is_nan(pivot.x):
		pivot = center()
	_xf = _xf * Transform2D(0.0, pivot) * Transform2D(deg_to_rad(degrees), Vector2.ZERO) * Transform2D(0.0, -pivot)


## Compose `inset(left, top, right, bottom)`: moves the origin and shrinks [member size].
func inset(left: float, top: float = NAN, right: float = NAN, bottom: float = NAN) -> void:
	if is_nan(top):
		top = left
	if is_nan(right):
		right = left
	if is_nan(bottom):
		bottom = top
	translate(left, top)
	size = Vector2(size.x - left - right, size.y - top - bottom)


## Compose `clipRect(left, top, right, bottom)`: rectangles are clipped to it; other shapes are not
## (Godot draws have no per-call scissor: whole areas clip with a clipping Control instead).
func clip_rect(left: float, top: float, right: float, bottom: float) -> void:
	var r := _xf * Rect2(left, top, right - left, bottom - top)
	_clip = r if not _has_clip else _clip.intersection(r)
	_has_clip = true


func _apply() -> void:
	if _xf != _applied:
		ci.draw_set_transform_matrix(_xf)
		_applied = _xf


# ---------------------------------------------------------------- helpers

static func _color(paint: Variant, alpha: float) -> Color:
	var c: Color
	if paint is Color:
		c = paint
	else:
		c = Pal.c(int(paint))
	c.a *= alpha
	return c


static func _stroke_width(style: Variant) -> float:
	if style == null:
		return 0.0
	if style is PaStroke:
		return (style as PaStroke).width
	return float(style)


static func _round_cap(style: Variant) -> bool:
	return style is PaStroke and (style as PaStroke).round_cap


func _segments(r: float) -> int:
	var px := r * _xf.get_scale().x
	return clampi(int(px * 0.9) + 8, 10, MAX_SEGMENTS)


## Fills [param pts] with a colour or a brush.
func _fill(pts: PackedVector2Array, paint: Variant, alpha: float) -> void:
	if pts.size() < 3:
		return
	_apply()
	if paint is PaBrush:
		var b: PaBrush = paint
		var bounds := Rect2(pts[0], Vector2.ZERO)
		for p in pts:
			bounds = bounds.expand(p)
		var uvs := PackedVector2Array()
		uvs.resize(pts.size())
		for i in pts.size():
			uvs[i] = b.uv(pts[i], bounds)
		ci.draw_polygon(pts, PackedColorArray([Color(1, 1, 1, alpha)]), uvs, b.texture())
	else:
		ci.draw_colored_polygon(pts, _color(paint, alpha))


func _outline(pts: PackedVector2Array, paint: Variant, alpha: float, width: float, closed: bool = true) -> void:
	if pts.size() < 2:
		return
	_apply()
	var line := pts
	if closed:
		line = pts.duplicate()
		line.append(pts[0])
	var c := _color(paint, alpha) if not (paint is PaBrush) else _brush_mid(paint, alpha)
	ci.draw_polyline(line, c, width, true)


static func _brush_mid(b: PaBrush, alpha: float) -> Color:
	var c := b.colors[b.colors.size() / 2]
	c.a *= alpha
	return c


func _ellipse_points(c: Vector2, rx: float, ry: float) -> PackedVector2Array:
	var n := _segments(maxf(rx, ry))
	var pts := PackedVector2Array()
	pts.resize(n)
	for i in n:
		var a := TAU * i / n
		pts[i] = c + Vector2(cos(a) * rx, sin(a) * ry)
	return pts


# ---------------------------------------------------------------- drawing

func draw_rect(paint: Variant, top_left: Vector2 = Vector2.ZERO, rect_size: Vector2 = Vector2(NAN, NAN), alpha: float = 1.0, style: Variant = null) -> void:
	if is_nan(rect_size.x):
		rect_size = size - top_left
	var w := _stroke_width(style)
	if w > 0.0:
		# Compose strokes centre on the edge.
		var pts := PackedVector2Array([top_left, top_left + Vector2(rect_size.x, 0), top_left + rect_size, top_left + Vector2(0, rect_size.y)])
		_outline(pts, paint, alpha, w)
		return
	if paint is PaBrush:
		_fill(PackedVector2Array([top_left, top_left + Vector2(rect_size.x, 0), top_left + rect_size, top_left + Vector2(0, rect_size.y)]), paint, alpha)
		return
	var r := Rect2(top_left, rect_size)
	if _has_clip:
		var world := _xf * r
		var cut := world.intersection(_clip)
		if cut.size.x <= 0.0 or cut.size.y <= 0.0:
			return
		r = _xf.affine_inverse() * cut
	_apply()
	ci.draw_rect(r, _color(paint, alpha), true)


func draw_circle(paint: Variant, radius: float = NAN, c: Vector2 = Vector2(NAN, NAN), alpha: float = 1.0, style: Variant = null) -> void:
	if is_nan(radius):
		radius = minf(size.x, size.y) / 2.0
	if is_nan(c.x):
		c = center()
	if radius <= 0.0:
		return
	var w := _stroke_width(style)
	if w > 0.0:
		_outline(_ellipse_points(c, radius, radius), paint, alpha, w)
		return
	if paint is PaBrush:
		_fill(_ellipse_points(c, radius, radius), paint, alpha)
		return
	_apply()
	ci.draw_circle(c, radius, _color(paint, alpha), true, -1.0, true)


func draw_oval(paint: Variant, top_left: Vector2, oval_size: Vector2, alpha: float = 1.0, style: Variant = null) -> void:
	var pts := _ellipse_points(top_left + oval_size / 2.0, oval_size.x / 2.0, oval_size.y / 2.0)
	var w := _stroke_width(style)
	if w > 0.0:
		_outline(pts, paint, alpha, w)
	else:
		_fill(pts, paint, alpha)


## [param corner]: a radius (float) or a Vector2 (x, y radii).
func draw_round_rect(paint: Variant, top_left: Vector2, rect_size: Vector2, corner: Variant = 0.0, alpha: float = 1.0, style: Variant = null) -> void:
	var rx: float
	var ry: float
	if corner is Vector2:
		rx = (corner as Vector2).x
		ry = (corner as Vector2).y
	else:
		rx = float(corner)
		ry = rx
	var steps := clampi(int(maxf(rx, ry) * _xf.get_scale().x * 0.25) + 3, 3, 10)
	var pts := PaPath.round_rect_points(top_left.x, top_left.y, top_left.x + rect_size.x, top_left.y + rect_size.y, rx, ry, steps)
	var w := _stroke_width(style)
	if w > 0.0:
		_outline(pts, paint, alpha, w)
	else:
		_fill(pts, paint, alpha)


func draw_line(paint: Variant, start: Vector2, end_point: Vector2, width: float = 1.0, round_cap: bool = false, alpha: float = 1.0) -> void:
	_apply()
	var c := _color(paint, alpha) if not (paint is PaBrush) else _brush_mid(paint, alpha)
	ci.draw_line(start, end_point, c, width, true)
	if round_cap and width > 1.0:
		ci.draw_circle(start, width / 2.0, c, true, -1.0, true)
		ci.draw_circle(end_point, width / 2.0, c, true, -1.0, true)


func draw_path(path: PaPath, paint: Variant, alpha: float = 1.0, style: Variant = null) -> void:
	var parts: Array = path.all_subpaths()
	var subs: Array = parts[0]
	var closed: Array = parts[1]
	var w := _stroke_width(style)
	for i in subs.size():
		var pts: PackedVector2Array = subs[i]
		if w > 0.0:
			_outline(pts, paint, alpha, w, closed[i])
			if _round_cap(style) and not closed[i]:
				var c := _color(paint, alpha) if not (paint is PaBrush) else _brush_mid(paint, alpha)
				ci.draw_circle(pts[0], w / 2.0, c, true, -1.0, true)
				ci.draw_circle(pts[pts.size() - 1], w / 2.0, c, true, -1.0, true)
		elif pts.size() >= 3:
			_fill(pts, paint, alpha)


func draw_arc(paint: Variant, start_deg: float, sweep_deg: float, use_center: bool, top_left: Vector2, arc_size: Vector2, alpha: float = 1.0, style: Variant = null) -> void:
	var c := top_left + arc_size / 2.0
	var rx := arc_size.x / 2.0
	var ry := arc_size.y / 2.0
	var n := maxi(2, int(_segments(maxf(rx, ry)) * absf(sweep_deg) / 360.0) + 1)
	var pts := PackedVector2Array()
	if use_center:
		pts.append(c)
	for i in n + 1:
		var a := deg_to_rad(start_deg + sweep_deg * i / n)
		pts.append(c + Vector2(cos(a) * rx, sin(a) * ry))
	var w := _stroke_width(style)
	if w > 0.0:
		_outline(pts, paint, alpha, w, use_center)
		if _round_cap(style) and not use_center:
			var col := _color(paint, alpha) if not (paint is PaBrush) else _brush_mid(paint, alpha)
			ci.draw_circle(pts[0], w / 2.0, col, true, -1.0, true)
			ci.draw_circle(pts[pts.size() - 1], w / 2.0, col, true, -1.0, true)
	else:
		if not use_center:
			pts.append(c)
		_fill(pts, paint, alpha)


func draw_image(tex: Texture2D, top_left: Vector2, image_size: Vector2, alpha: float = 1.0) -> void:
	if tex == null:
		return
	_apply()
	ci.draw_texture_rect(tex, Rect2(top_left, image_size), false, Color(1, 1, 1, alpha))


## A polygon from flat coordinates (x0, y0, x1, y1, ...), for ported Path code built point by point.
func draw_polygon(points: PackedVector2Array, paint: Variant, alpha: float = 1.0) -> void:
	_fill(points, paint, alpha)
