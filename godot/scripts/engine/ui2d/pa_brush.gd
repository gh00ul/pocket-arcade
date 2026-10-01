class_name PaBrush
extends RefCounted
## Compose's Brush for the DrawScope shim: linear (vertical, horizontal, any direction), radial and
## sweep gradients through colour stops. Painted through a cached 256-texel gradient texture whose
## UVs are computed per vertex, so any number of stops renders exactly.

enum Kind { LINEAR, RADIAL, SWEEP }

var kind: int = Kind.LINEAR
var colors: PackedColorArray
## Stop positions 0..1 (empty: evenly spaced).
var stops: PackedFloat32Array
## LINEAR: start and end points; RADIAL/SWEEP: centre (start) and radius (radius).
var start := Vector2.ZERO
var end_point := Vector2.ZERO
var radius := 0.0
## Vertical / horizontal gradients with no bounds given span the shape being drawn.
var auto_axis := -1

static var _cache := {}
static var _cache_order: Array = []
const CACHE_MAX := 384


## Brush.verticalGradient(colors, startY, endY): [param start_y]/[param end_y] NAN = the shape's bounds.
static func vertical(p_colors: Array, start_y: float = NAN, end_y: float = NAN, p_stops: Array = []) -> PaBrush:
	var b := PaBrush.new()
	b.kind = Kind.LINEAR
	b.colors = _colors(p_colors)
	b.stops = PackedFloat32Array(p_stops)
	if is_nan(start_y) or is_nan(end_y):
		b.auto_axis = 1
	else:
		b.start = Vector2(0, start_y)
		b.end_point = Vector2(0, end_y)
	return b


static func horizontal(p_colors: Array, start_x: float = NAN, end_x: float = NAN, p_stops: Array = []) -> PaBrush:
	var b := PaBrush.new()
	b.kind = Kind.LINEAR
	b.colors = _colors(p_colors)
	b.stops = PackedFloat32Array(p_stops)
	if is_nan(start_x) or is_nan(end_x):
		b.auto_axis = 0
	else:
		b.start = Vector2(start_x, 0)
		b.end_point = Vector2(end_x, 0)
	return b


static func linear(p_colors: Array, p_start: Vector2, p_end: Vector2, p_stops: Array = []) -> PaBrush:
	var b := PaBrush.new()
	b.kind = Kind.LINEAR
	b.colors = _colors(p_colors)
	b.stops = PackedFloat32Array(p_stops)
	b.start = p_start
	b.end_point = p_end
	return b


static func radial(p_colors: Array, center: Vector2, p_radius: float, p_stops: Array = []) -> PaBrush:
	var b := PaBrush.new()
	b.kind = Kind.RADIAL
	b.colors = _colors(p_colors)
	b.stops = PackedFloat32Array(p_stops)
	b.start = center
	b.radius = maxf(p_radius, 0.001)
	return b


static func sweep(p_colors: Array, center: Vector2, p_stops: Array = []) -> PaBrush:
	var b := PaBrush.new()
	b.kind = Kind.SWEEP
	b.colors = _colors(p_colors)
	b.stops = PackedFloat32Array(p_stops)
	b.start = center
	return b


## Colours given as Godot Colors or ARGB ints.
static func _colors(list: Array) -> PackedColorArray:
	var out := PackedColorArray()
	for c in list:
		if c is Color:
			out.append(c)
		else:
			out.append(Pal.c(int(c)))
	return out


## The gradient as a 256 × 1 texture (cached by its stops).
func texture() -> Texture2D:
	var key := str(colors) + "|" + str(stops)
	var t: Texture2D = _cache.get(key)
	if t != null:
		return t
	var g := Gradient.new()
	var n := colors.size()
	var offsets := PackedFloat32Array()
	for i in n:
		offsets.append(stops[i] if i < stops.size() else (float(i) / maxf(n - 1, 1)))
	if n == 1:
		g.offsets = PackedFloat32Array([0.0, 1.0])
		g.colors = PackedColorArray([colors[0], colors[0]])
	else:
		g.offsets = offsets
		g.colors = colors
	var gt := GradientTexture1D.new()
	gt.gradient = g
	gt.width = 256
	_cache[key] = gt
	_cache_order.append(key)
	if _cache_order.size() > CACHE_MAX:
		_cache.erase(_cache_order.pop_front())
	return gt


## The gradient coordinate (0..1 along the texture) at [param p], for a shape spanning [param bounds].
func coord(p: Vector2, bounds: Rect2) -> float:
	match kind:
		Kind.RADIAL:
			return clampf(p.distance_to(start) / radius, 0.0, 1.0)
		Kind.SWEEP:
			var a := atan2(p.y - start.y, p.x - start.x)
			if a < 0.0:
				a += TAU
			return a / TAU
	var s := start
	var e := end_point
	if auto_axis == 1:
		s = Vector2(0, bounds.position.y)
		e = Vector2(0, bounds.end.y)
	elif auto_axis == 0:
		s = Vector2(bounds.position.x, 0)
		e = Vector2(bounds.end.x, 0)
	var d := e - s
	var len2 := d.length_squared()
	if len2 < 1e-9:
		return 0.0
	return clampf((p - s).dot(d) / len2, 0.0, 1.0)
