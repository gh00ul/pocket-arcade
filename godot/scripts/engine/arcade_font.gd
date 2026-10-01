class_name ArcadeFont
extends RefCounted
## engine/ArcadeFont.kt: the game's type: a black sans for headings and numbers and a condensed
## bold for small labels, anti-aliased at any size, with a few symbols (play, star, heart, token,
## ticket, note, arrows) drawn inline as vector icons.
##
## Sizes are given as a *unit*: capitals stand [constant CAP] units tall ([constant TINY_CAP] for
## tiny text). Text is always set in capitals. Letter spacing is kept fractional (glyph by glyph);
## pair kerning is not applied (recorded in docs/PORT_PARITY.md).

const CAP := 7.0
const TINY_CAP := 5.0

const PLAY := "▶"
const STAR := "★"
const HEART := "♥"
const TOKEN := "¤"
const TICKET := "¢"
const NOTE := "♪"
const LEFT := "←"
const RIGHT := "→"
const UP := "↑"
const DOWN := "↓"
const ICONS := "▶★♥¤¢♪←→↑↓"

## The shadow every shadowed text gets by default.
const SHADOW := 0xFF05030A

static var _upper := {}


static func is_icon(ch: String) -> bool:
	return ICONS.contains(ch)


static func _caps(text: String) -> String:
	var s: Variant = _upper.get(text)
	if s == null:
		if _upper.size() > 512:
			_upper.clear()
		s = text.to_upper()
		_upper[text] = s
	return s


static func _font(tiny: bool) -> Font:
	return Fonts.condensed() if tiny else Fonts.display()


## The font size (in units of the drawing space) whose capitals are [param cap] tall.
static func _text_size(cap: float) -> float:
	return cap / Fonts.CAP_RATIO


static func _spacing(tiny: bool) -> float:
	return 0.07 if tiny else 0.035


static func icon_width(ch: String, cap: float) -> float:
	match ch:
		TICKET:
			return cap * 1.5
		LEFT, RIGHT:
			return cap * 1.2
		UP, DOWN, NOTE, PLAY:
			return cap * 0.9
	return cap * 1.15


## Glyph advances are measured at a fixed reference size and scaled (sizes are fractional here).
const REF_SIZE := 64
static var _adv_cache := {}


static func _advance(font: Font, ch: String, size: float) -> float:
	var key := str(font.get_instance_id()) + ch
	var a: Variant = _adv_cache.get(key)
	if a == null:
		a = font.get_char_size(ch.unicode_at(0), REF_SIZE).x
		_adv_cache[key] = a
	return float(a) * size / REF_SIZE


static func width(text: String, unit: float, tiny: bool = false) -> float:
	if text.is_empty():
		return 0.0
	var s := _caps(text)
	var cap := (TINY_CAP if tiny else CAP) * unit
	var size := _text_size(cap)
	var font := _font(tiny)
	var sp := _spacing(tiny) * size
	var w := 0.0
	for i in s.length():
		var ch := s[i]
		if is_icon(ch):
			w += icon_width(ch, cap) + cap * 0.18
		else:
			w += _advance(font, ch, size) + sp
	return w


static func height(unit: float, tiny: bool = false) -> float:
	return (TINY_CAP if tiny else CAP) * unit


## Draws [param text] with its top-left corner at (x, y) (the top of the capitals).
static func draw(ds: DrawScope, text: String, x: float, y: float, unit: float, color: Variant, alpha: float = 1.0, tiny: bool = false) -> void:
	ds._apply()
	draw_to(ds.ci, ds.transform(), text, x, y, unit, _argb(color), alpha, tiny, 0)


## Draws text with a soft drop shadow for legibility over busy pictures.
static func draw_shadowed(ds: DrawScope, text: String, x: float, y: float, unit: float, color: Variant, shadow: Variant = SHADOW, alpha: float = 1.0, tiny: bool = false) -> void:
	ds._apply()
	draw_to(ds.ci, ds.transform(), text, x, y, unit, _argb(color), alpha, tiny, _argb(shadow))


static func draw_centered(ds: DrawScope, text: String, cx: float, y: float, unit: float, color: Variant, alpha: float = 1.0, tiny: bool = false, shadow: bool = true) -> void:
	var x := cx - width(text, unit, tiny) / 2.0
	ds._apply()
	draw_to(ds.ci, ds.transform(), text, x, y, unit, _argb(color), alpha, tiny, SHADOW if shadow else 0)


static func _argb(c: Variant) -> int:
	if c is Color:
		return Pal.to_argb(c)
	return int(c)


## Draws onto any canvas item whose current transform is [param xf] (the screen, or a texture
## being painted). [param color] and [param shadow] are ARGB; shadow 0 for none.
static func draw_to(ci: CanvasItem, xf: Transform2D, text: String, x: float, y: float, unit: float, color: int, alpha: float, tiny: bool, shadow: int) -> void:
	if text.is_empty() or alpha <= 0.004:
		return
	var s := _caps(text)
	var cap := (TINY_CAP if tiny else CAP) * unit
	var size := _text_size(cap)
	var font := _font(tiny)
	var sp := _spacing(tiny) * size
	var col := Pal.c(color)
	col.a *= alpha
	if shadow != 0:
		var sc := Pal.c(shadow)
		sc.a *= alpha * 0.85
		var blur := maxf(unit * 0.9, 1.0)
		var dx := unit * 0.3
		var dy := unit * 0.55
		# A blurred shadow, approximated with a soft cluster of offset copies.
		var taps := [Vector2(0, 0), Vector2(-0.5, 0), Vector2(0.5, 0), Vector2(0, -0.5), Vector2(0, 0.5)]
		var tap_c := sc
		tap_c.a = sc.a * 0.3
		for t: Vector2 in taps:
			_run(ci, xf, s, x + dx + t.x * blur, y + dy + t.y * blur, cap, size, font, sp, tap_c, true)
	_run(ci, xf, s, x, y, cap, size, font, sp, col, false)


## One pass of glyphs and icons.
static func _run(ci: CanvasItem, xf: Transform2D, s: String, x: float, y: float, cap: float, size: float, font: Font, sp: float, col: Color, is_shadow: bool) -> void:
	var fsize := maxi(1, ceili(size))
	var k := size / fsize
	var baseline := y + cap
	var pen := x
	for i in s.length():
		var ch := s[i]
		if is_icon(ch):
			ci.draw_set_transform_matrix(xf)
			_icon(ci, ch, pen + cap * 0.09, y, cap, col, is_shadow)
			pen += icon_width(ch, cap) + cap * 0.18
			continue
		# Glyphs are drawn at an integer size scaled to the exact fractional one.
		ci.draw_set_transform_matrix(xf * Transform2D(0.0, Vector2(k, k), 0.0, Vector2(pen, baseline)))
		font.draw_char(ci, Vector2.ZERO, ch.unicode_at(0), fsize, col)
		pen += _advance(font, ch, size) + sp
	ci.draw_set_transform_matrix(xf)


static func _darker(c: Color) -> Color:
	return Color(c.r * 0.45, c.g * 0.45, c.b * 0.45, c.a)


static func _star_points(cx: float, cy: float, r: float, inner: float = 0.45) -> PackedVector2Array:
	var pts := PackedVector2Array()
	for k in 10:
		var a := -PI / 2.0 + k * PI / 5.0
		var rr := r if k % 2 == 0 else r * inner
		pts.append(Vector2(cx + cos(a) * rr, cy + sin(a) * rr))
	return pts


static func _circle_points(cx: float, cy: float, r: float, n: int = 24) -> PackedVector2Array:
	var pts := PackedVector2Array()
	for i in n:
		var a := TAU * i / n
		pts.append(Vector2(cx + cos(a) * r, cy + sin(a) * r))
	return pts


## One vector symbol in a box [param cap] tall starting at (x, y).
static func _icon(ci: CanvasItem, ch: String, x: float, y: float, cap: float, col: Color, is_shadow: bool) -> void:
	match ch:
		PLAY:
			ci.draw_colored_polygon(PackedVector2Array([Vector2(x, y), Vector2(x + cap * 0.9, y + cap / 2.0), Vector2(x, y + cap)]), col)
		STAR:
			ci.draw_colored_polygon(_star_points(x + cap * 0.575, y + cap * 0.55, cap * 0.64), col)
		HEART:
			var w := cap * 1.15
			var cx := x + w / 2.0
			var path := PaPath.new()
			path.move_to(cx, y + cap)
			path.cubic_to(x - w * 0.05, y + cap * 0.58, x, y - cap * 0.02, cx, y + cap * 0.24)
			path.cubic_to(x + w, y - cap * 0.02, x + w * 1.05, y + cap * 0.58, cx, y + cap)
			path.close()
			ci.draw_colored_polygon(path.subpaths[0], col)
		TOKEN:
			var r := cap * 0.575
			var cx := x + r
			var cy := y + cap / 2.0
			ci.draw_circle(Vector2(cx, cy), r, col, true, -1.0, true)
			if not is_shadow:
				var d := _darker(col)
				ci.draw_arc(Vector2(cx, cy), r * 0.72, 0.0, TAU, 32, d, cap * 0.09, true)
				ci.draw_colored_polygon(_star_points(cx, cy + r * 0.04, r * 0.5), d)
		TICKET:
			var w := cap * 1.5
			var top := y + cap * 0.1
			var bottom := y + cap * 0.95
			var mid := (top + bottom) / 2.0
			var nr := cap * 0.16
			# The body with a half-round notch cut in each end.
			var pts := PaPath.round_rect_points(x, top, x + w, bottom, cap * 0.12, cap * 0.12, 3)
			var body := Geometry2D.clip_polygons(pts, _circle_points(x, mid, nr, 16))
			var shape: PackedVector2Array = body[0] if not body.is_empty() else pts
			var body2 := Geometry2D.clip_polygons(shape, _circle_points(x + w, mid, nr, 16))
			if not body2.is_empty():
				shape = body2[0]
			ci.draw_colored_polygon(shape, col)
			if not is_shadow:
				var inner := PaPath.round_rect_points(x + cap * 0.26, top + cap * 0.14, x + w - cap * 0.26, bottom - cap * 0.14, cap * 0.06, cap * 0.06, 2)
				inner.append(inner[0])
				ci.draw_polyline(inner, _darker(col), cap * 0.07, true)
		NOTE:
			var stem_x := x + cap * 0.52
			var head := PackedVector2Array()
			var hc := Vector2(x + cap * 0.3, y + cap * 0.83)
			var rot := deg_to_rad(-22.0)
			for i in 20:
				var a := TAU * i / 20.0
				var p := Vector2(cos(a) * cap * 0.28, sin(a) * cap * 0.17).rotated(rot)
				head.append(hc + p)
			ci.draw_colored_polygon(head, col)
			ci.draw_rect(Rect2(stem_x - cap * 0.08, y, cap * 0.12, cap * 0.82), col)
			var flag := PaPath.new()
			flag.move_to(stem_x - cap * 0.06, y)
			flag.cubic_to(stem_x + cap * 0.1, y + cap * 0.2, stem_x + cap * 0.5, y + cap * 0.22, stem_x + cap * 0.36, y + cap * 0.62)
			flag.cubic_to(stem_x + cap * 0.36, y + cap * 0.38, stem_x + cap * 0.14, y + cap * 0.34, stem_x - cap * 0.06, y + cap * 0.3)
			flag.close()
			ci.draw_colored_polygon(flag.subpaths[0], col)
		LEFT, RIGHT, UP, DOWN:
			var w := icon_width(ch, cap)
			var cx := x + w / 2.0
			var cy := y + cap / 2.0
			var length := w if (ch == LEFT or ch == RIGHT) else cap
			var half := length / 2.0
			var arrow := PackedVector2Array([
				Vector2(-half, -cap * 0.11), Vector2(half - cap * 0.42, -cap * 0.11), Vector2(half - cap * 0.42, -cap * 0.4),
				Vector2(half, 0), Vector2(half - cap * 0.42, cap * 0.4), Vector2(half - cap * 0.42, cap * 0.11), Vector2(-half, cap * 0.11)])
			var angle := 0.0
			if ch == LEFT:
				angle = PI
			elif ch == UP:
				angle = -PI / 2.0
			elif ch == DOWN:
				angle = PI / 2.0
			for i in arrow.size():
				arrow[i] = Vector2(cx, cy) + arrow[i].rotated(angle)
			ci.draw_colored_polygon(arrow, col)
