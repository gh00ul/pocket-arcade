class_name UiDraw
extends RefCounted
## Drawing the menus need that [DrawScope] doesn't do on its own: strokes painted with a gradient
## (Compose strokes a shape with a Brush; DrawScope gives a stroked brush one colour), round joins
## and caps on stroked paths, a picture clipped to a rounded rectangle, a sweep-gradient arc, and a
## tiled texture clipped to a shape. A port helper of the ui kit (no Kotlin file of its own).


## The colour of a gradient through [param colors] (evenly spaced, or at [param stops]) at
## [param t] (clamped to 0..1, as Compose's TileMode.Clamp).
static func gradient_at(colors: PackedColorArray, t: float, stops: PackedFloat32Array = PackedFloat32Array()) -> Color:
	var n := colors.size()
	if n == 0:
		return Color(0, 0, 0, 0)
	if n == 1:
		return colors[0]
	var x := clampf(t, 0.0, 1.0)
	var prev_pos := stops[0] if stops.size() > 0 else 0.0
	if x <= prev_pos:
		return colors[0]
	for i in range(1, n):
		var pos := stops[i] if i < stops.size() else float(i) / (n - 1)
		if x <= pos:
			var span := pos - prev_pos
			var k := 0.0 if span <= 1e-6 else (x - prev_pos) / span
			return colors[i - 1].lerp(colors[i], k)
		prev_pos = pos
	return colors[n - 1]


## Strokes the closed or open polyline [param pts] with one colour per point.
static func stroke_colors(ds: DrawScope, pts: PackedVector2Array, cols: PackedColorArray, width: float, closed: bool) -> void:
	if pts.size() < 2:
		return
	ds._apply()
	if closed:
		var line := pts.duplicate()
		line.append(pts[0])
		var c2 := cols.duplicate()
		c2.append(cols[0])
		ds.ci.draw_polyline_colors(line, c2, width, true)
	else:
		ds.ci.draw_polyline_colors(pts, cols, width, true)


## Strokes the outline of a rounded rectangle with a vertical gradient through [param colors]
## running from [param start_y] to [param end_y] (Brush.verticalGradient with startY/endY).
static func stroke_round_rect_vgradient(ds: DrawScope, top_left: Vector2, rect_size: Vector2, corner: float, width: float,
		colors: PackedColorArray, start_y: float, end_y: float) -> void:
	var steps := clampi(int(corner * ds.transform().get_scale().x * Display.density * 0.15) + 3, 3, 12)
	var pts := PaPath.round_rect_points(top_left.x, top_left.y, top_left.x + rect_size.x, top_left.y + rect_size.y, corner, corner, steps)
	var cols := PackedColorArray()
	cols.resize(pts.size())
	var span := end_y - start_y
	for i in pts.size():
		var t := 0.0 if absf(span) < 1e-6 else (pts[i].y - start_y) / span
		cols[i] = gradient_at(colors, t)
	stroke_colors(ds, pts, cols, width, true)


## Strokes [param pts] in one [param color] with round joins (and round caps on an open line): the
## polyline plus a dot of the stroke's width at every corner.
static func stroke_round(ds: DrawScope, pts: PackedVector2Array, color: Color, width: float, closed: bool, round_caps: bool = true) -> void:
	if pts.size() < 2:
		return
	ds._apply()
	var line := pts
	if closed:
		line = pts.duplicate()
		line.append(pts[0])
	ds.ci.draw_polyline(line, color, width, true)
	var r := width * 0.5
	if r <= 0.0:
		return
	var n := pts.size()
	for i in n:
		var corner := closed or (i > 0 and i < n - 1)
		if corner or round_caps:
			ds.ci.draw_circle(pts[i], r, color, true, -1.0, true)


## A texture drawn into [param rect] with its corners rounded by [param corner] (an image clipped by
## RoundedCornerShape), at [param alpha].
static func rounded_image(ds: DrawScope, tex: Texture2D, rect: Rect2, corner: float, alpha: float = 1.0) -> void:
	if tex == null:
		return
	var pts := PaPath.round_rect_points(rect.position.x, rect.position.y, rect.end.x, rect.end.y, corner, corner, 6)
	var uvs := PackedVector2Array()
	uvs.resize(pts.size())
	for i in pts.size():
		uvs[i] = (pts[i] - rect.position) / rect.size
	ds._apply()
	ds.ci.draw_polygon(pts, PackedColorArray([Color(1, 1, 1, alpha)]), uvs, tex)


## An arc of the circle inscribed in ([param top_left], [param arc_size]) from [param start_deg]
## through [param sweep_deg], stroked [param width] wide, coloured along its length by
## [param color_at] (a Callable(angle_deg) -> Color: a sweep gradient), with round caps if asked.
static func sweep_arc(ds: DrawScope, top_left: Vector2, arc_size: Vector2, start_deg: float, sweep_deg: float, width: float,
		color_at: Callable, round_caps: bool) -> void:
	var c := top_left + arc_size / 2.0
	var rx := arc_size.x / 2.0
	var ry := arc_size.y / 2.0
	var n := maxi(2, int(48.0 * absf(sweep_deg) / 360.0) + 2)
	var pts := PackedVector2Array()
	var cols := PackedColorArray()
	pts.resize(n + 1)
	cols.resize(n + 1)
	for i in n + 1:
		var a := start_deg + sweep_deg * i / n
		var r := deg_to_rad(a)
		pts[i] = c + Vector2(cos(r) * rx, sin(r) * ry)
		cols[i] = color_at.call(a)
	ds._apply()
	ds.ci.draw_polyline_colors(pts, cols, width, true)
	if round_caps:
		ds.ci.draw_circle(pts[0], width * 0.5, cols[0], true, -1.0, true)
		ds.ci.draw_circle(pts[n], width * 0.5, cols[n], true, -1.0, true)
