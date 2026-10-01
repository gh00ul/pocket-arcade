class_name PaPath
extends RefCounted
## Compose/Android Path for the DrawScope shim: subpaths of points (curves flattened as they are
## added), filled (each closed subpath, concave allowed) or stroked.

## Each subpath: PackedVector2Array; [member closed] says which are closed.
var subpaths: Array = []
var closed: Array[bool] = []
var _cur := PackedVector2Array()
var _pen := Vector2.ZERO

## Segments per curve when flattening.
const CURVE_STEPS := 12


func reset() -> PaPath:
	subpaths.clear()
	closed.clear()
	_cur = PackedVector2Array()
	_pen = Vector2.ZERO
	return self


func _flush(close: bool) -> void:
	if _cur.size() >= 2:
		subpaths.append(_cur)
		closed.append(close)
	_cur = PackedVector2Array()


func move_to(x: float, y: float) -> PaPath:
	_flush(false)
	_pen = Vector2(x, y)
	_cur.append(_pen)
	return self


func line_to(x: float, y: float) -> PaPath:
	if _cur.is_empty():
		_cur.append(_pen)
	_pen = Vector2(x, y)
	_cur.append(_pen)
	return self


func relative_line_to(dx: float, dy: float) -> PaPath:
	return line_to(_pen.x + dx, _pen.y + dy)


func quadratic_to(x1: float, y1: float, x2: float, y2: float) -> PaPath:
	if _cur.is_empty():
		_cur.append(_pen)
	var p0 := _pen
	var c := Vector2(x1, y1)
	var p2 := Vector2(x2, y2)
	for i in range(1, CURVE_STEPS + 1):
		var t := float(i) / CURVE_STEPS
		var a := p0.lerp(c, t)
		var b := c.lerp(p2, t)
		_cur.append(a.lerp(b, t))
	_pen = p2
	return self


func cubic_to(x1: float, y1: float, x2: float, y2: float, x3: float, y3: float) -> PaPath:
	if _cur.is_empty():
		_cur.append(_pen)
	var p0 := _pen
	var c1 := Vector2(x1, y1)
	var c2 := Vector2(x2, y2)
	var p3 := Vector2(x3, y3)
	for i in range(1, CURVE_STEPS + 1):
		var t := float(i) / CURVE_STEPS
		_cur.append(p0.bezier_interpolate(c1, c2, p3, t))
	_pen = p3
	return self


func close() -> PaPath:
	_flush(true)
	return self


## An ellipse inscribed in the rect (Path.addOval).
func add_oval(left: float, top: float, right: float, bottom: float, segments: int = 32) -> PaPath:
	_flush(false)
	var c := Vector2((left + right) / 2.0, (top + bottom) / 2.0)
	var r := Vector2((right - left) / 2.0, (bottom - top) / 2.0)
	var pts := PackedVector2Array()
	for i in segments:
		var a := TAU * i / segments
		pts.append(c + Vector2(cos(a) * r.x, sin(a) * r.y))
	subpaths.append(pts)
	closed.append(true)
	return self


func add_circle(cx: float, cy: float, r: float) -> PaPath:
	return add_oval(cx - r, cy - r, cx + r, cy + r)


func add_rect(left: float, top: float, right: float, bottom: float) -> PaPath:
	_flush(false)
	subpaths.append(PackedVector2Array([Vector2(left, top), Vector2(right, top), Vector2(right, bottom), Vector2(left, bottom)]))
	closed.append(true)
	return self


func add_round_rect(left: float, top: float, right: float, bottom: float, rx: float, ry: float = NAN) -> PaPath:
	_flush(false)
	subpaths.append(round_rect_points(left, top, right, bottom, rx, rx if is_nan(ry) else ry))
	closed.append(true)
	return self


## The outline of a rounded rectangle as points (shared with DrawScope).
static func round_rect_points(left: float, top: float, right: float, bottom: float, rx: float, ry: float, steps: int = 8) -> PackedVector2Array:
	var w := right - left
	var h := bottom - top
	rx = clampf(rx, 0.0, w / 2.0)
	ry = clampf(ry, 0.0, h / 2.0)
	var pts := PackedVector2Array()
	if rx <= 0.01 or ry <= 0.01:
		pts.append(Vector2(left, top))
		pts.append(Vector2(right, top))
		pts.append(Vector2(right, bottom))
		pts.append(Vector2(left, bottom))
		return pts
	var centers := [Vector2(right - rx, top + ry), Vector2(right - rx, bottom - ry), Vector2(left + rx, bottom - ry), Vector2(left + rx, top + ry)]
	var starts := [-PI / 2.0, 0.0, PI / 2.0, PI]
	for k in 4:
		var c: Vector2 = centers[k]
		for i in steps + 1:
			var a: float = starts[k] + (PI / 2.0) * i / steps
			pts.append(c + Vector2(cos(a) * rx, sin(a) * ry))
	return pts


## Every point, for bounds.
func bounds() -> Rect2:
	var r := Rect2()
	var first := true
	for sp in subpaths + ([_cur] if _cur.size() > 1 else []):
		for p in sp:
			if first:
				r = Rect2(p, Vector2.ZERO)
				first = false
			else:
				r = r.expand(p)
	return r


## The subpaths including the one still open.
func all_subpaths() -> Array:
	var out := subpaths.duplicate()
	var cl := closed.duplicate()
	if _cur.size() >= 2:
		out.append(_cur)
		cl.append(false)
	return [out, cl]
