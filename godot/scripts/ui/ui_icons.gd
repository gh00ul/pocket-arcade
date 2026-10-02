class_name UiIcons
extends RefCounted
## ui/UiIcons.kt: the menus' vector icons ([UiIcon]), the twinkle, and the currency art (the gold
## token and the prize ticket). [TokenIcon] and [TicketIcon] are the composables. Also the map and
## gear glyphs ([method draw_map_icon] from MapScreen.kt, [method draw_gear_icon] from
## SettingsScreen.kt), kept here so the icons don't depend on the screens.
##
## Sizes are in the DrawScope's units (dp on screen); build-13's few pixel limits (a coin too small
## for milling, a twinkle too small to draw) are measured in real pixels through the scope's
## transform and [member Display.density].

## How big the token's glint is when it isn't twinkling (0..1 of its full size).
const TOKEN_GLINT_REST := 0.5
## How long one twinkle cycle of a token icon takes, and how much of it is the twinkle itself.
const TWINKLE_MILLIS := 3600
const TWINKLE_SHARE := 0.16

const _DETAIL := Color(0x1B / 255.0, 0x10 / 255.0, 0x30 / 255.0, 1.0)


## How many screen pixels one unit of [param ds] is.
static func px_per_unit(ds: DrawScope) -> float:
	return absf(ds.transform().get_scale().x) * Display.density


## The ink for a glyph's small details (a keyhole, a star stamped on a cup): dark, and as faint as
## its glyph is.
static func detail_of(glyph: Color) -> Color:
	return Color(_DETAIL.r, _DETAIL.g, _DETAIL.b, 0.32 * glyph.a)


## A five-point star of outer radius [param r] as points, point up, its inner points at 45% of it.
static func star_points(c: Vector2, r: float) -> PackedVector2Array:
	var pts := PackedVector2Array()
	pts.resize(10)
	for k in 10:
		var a := -PI / 2.0 + k * PI / 5.0
		var rr := r if k % 2 == 0 else r * 0.45
		pts[k] = Vector2(c.x + cos(a) * rr, c.y + sin(a) * rr)
	return pts


static func _star_path(c: Vector2, r: float) -> PaPath:
	var p := PaPath.new()
	var pts := star_points(c, r)
	p.move_to(pts[0].x, pts[0].y)
	for i in range(1, 10):
		p.line_to(pts[i].x, pts[i].y)
	p.close()
	return p


## A four-point twinkle of reach [param r] round [param c]: the concave star that says "shiny".
## Used for glints on tokens, tickets and new things.
static func draw_sparkle(ds: DrawScope, c: Vector2, r: float, color: Color) -> void:
	if r * px_per_unit(ds) <= 0.3:
		return
	# How close the flanks pinch toward the middle: small is spiky, large is fat.
	var k := 0.16
	var p := PaPath.new()
	p.move_to(c.x, c.y - r)
	p.quadratic_to(c.x + r * k, c.y - r * k, c.x + r, c.y)
	p.quadratic_to(c.x + r * k, c.y + r * k, c.x, c.y + r)
	p.quadratic_to(c.x - r * k, c.y + r * k, c.x - r, c.y)
	p.quadratic_to(c.x - r * k, c.y - r * k, c.x, c.y - r)
	p.close()
	ds.draw_path(p, color)


## Simple glyphs for round buttons, fitting a box [param s] across centred on [param c].
static func draw_ui_icon(ds: DrawScope, icon: int, c: Vector2, s: float, color: Color) -> void:
	match icon:
		UiIcon.CLOSE:
			var d := s * 0.3
			ds.draw_line(color, c + Vector2(-d, -d), c + Vector2(d, d), s * 0.15, true)
			ds.draw_line(color, c + Vector2(d, -d), c + Vector2(-d, d), s * 0.15, true)
		UiIcon.PAUSE:
			var bw := s * 0.16
			ds.draw_round_rect(color, c + Vector2(-s * 0.26, -s * 0.32), Vector2(bw, s * 0.64), bw / 3.0)
			ds.draw_round_rect(color, c + Vector2(s * 0.1, -s * 0.32), Vector2(bw, s * 0.64), bw / 3.0)
		UiIcon.EYE:
			# An almond outline with a round iris and a glint.
			var w := s * 0.46
			var h := s * 0.26
			var eye := PaPath.new()
			eye.move_to(c.x - w, c.y)
			eye.quadratic_to(c.x, c.y - h * 2.0, c.x + w, c.y)
			eye.quadratic_to(c.x, c.y + h * 2.0, c.x - w, c.y)
			eye.close()
			ds.draw_path(eye, color, 1.0, s * 0.09)
			ds.draw_circle(color, s * 0.17, c)
			ds.draw_circle(detail_of(color), s * 0.07, c + Vector2(s * 0.06, -s * 0.06))
		UiIcon.CAMERA:
			# A camera body with its viewfinder bump and a solid lens.
			var bw := s * 0.84
			var bh := s * 0.56
			var top := c.y - bh / 2.0 + s * 0.06
			ds.draw_round_rect(color, Vector2(c.x - s * 0.2, top - s * 0.14), Vector2(s * 0.4, s * 0.16), s * 0.05)
			ds.draw_round_rect(color, Vector2(c.x - bw / 2.0, top), Vector2(bw, bh), s * 0.12, 1.0, s * 0.09)
			ds.draw_circle(color, s * 0.15, Vector2(c.x, top + bh / 2.0))
			ds.draw_circle(detail_of(color), s * 0.06, Vector2(c.x - s * 0.03, top + bh / 2.0 - s * 0.03))
			ds.draw_circle(color, s * 0.045, Vector2(c.x + bw * 0.32, top + s * 0.12))
		UiIcon.MAP:
			draw_map_icon(ds, c, s, color)
		UiIcon.GEAR:
			draw_gear_icon(ds, c, s, color)
		UiIcon.TROPHY:
			var cup := PaPath.new()
			cup.move_to(c.x - s * 0.3, c.y - s * 0.38)
			cup.line_to(c.x + s * 0.3, c.y - s * 0.38)
			cup.cubic_to(c.x + s * 0.3, c.y + s * 0.02, c.x + s * 0.18, c.y + s * 0.12, c.x, c.y + s * 0.14)
			cup.cubic_to(c.x - s * 0.18, c.y + s * 0.12, c.x - s * 0.3, c.y + s * 0.02, c.x - s * 0.3, c.y - s * 0.38)
			cup.close()
			ds.draw_path(cup, color)
			var handle := PaStroke.new(s * 0.08, true)
			ds.draw_arc(color, 90.0, 180.0, false, Vector2(c.x - s * 0.46, c.y - s * 0.32), Vector2(s * 0.3, s * 0.3), 1.0, handle)
			ds.draw_arc(color, -90.0, 180.0, false, Vector2(c.x + s * 0.16, c.y - s * 0.32), Vector2(s * 0.3, s * 0.3), 1.0, handle)
			ds.draw_rect(color, Vector2(c.x - s * 0.06, c.y + s * 0.12), Vector2(s * 0.12, s * 0.16))
			ds.draw_round_rect(color, Vector2(c.x - s * 0.24, c.y + s * 0.27), Vector2(s * 0.48, s * 0.12), s * 0.04)
			# A star stamped on the cup.
			ds.draw_polygon(star_points(c + Vector2(0.0, -s * 0.14), s * 0.14), detail_of(color))
		UiIcon.SOUND, UiIcon.MUTED:
			ds.draw_polygon(PackedVector2Array([
				Vector2(c.x - s * 0.42, c.y - s * 0.14), Vector2(c.x - s * 0.24, c.y - s * 0.14), Vector2(c.x - s * 0.02, c.y - s * 0.36),
				Vector2(c.x - s * 0.02, c.y + s * 0.36), Vector2(c.x - s * 0.24, c.y + s * 0.14), Vector2(c.x - s * 0.42, c.y + s * 0.14)]), color)
			if icon == UiIcon.SOUND:
				for k in range(1, 3):
					var rr := s * (0.14 + k * 0.13)
					ds.draw_arc(color, -45.0, 90.0, false, Vector2(c.x + s * 0.02 - rr, c.y - rr), Vector2(rr * 2.0, rr * 2.0), 1.0, PaStroke.new(s * 0.09, true))
			else:
				var x0 := c.x + s * 0.14
				var d := s * 0.14
				ds.draw_line(color, Vector2(x0, c.y - d), Vector2(x0 + d * 2.0, c.y + d), s * 0.1, true)
				ds.draw_line(color, Vector2(x0 + d * 2.0, c.y - d), Vector2(x0, c.y + d), s * 0.1, true)
		UiIcon.LOCK:
			var w := s * 0.56
			var body_top := c.y - s * 0.04
			# The shackle: a half ring over two short legs, then the body over their feet.
			ds.draw_arc(color, 180.0, 180.0, false, Vector2(c.x - w * 0.32, c.y - s * 0.4), Vector2(w * 0.64, s * 0.62), 1.0, PaStroke.new(s * 0.1, true))
			ds.draw_line(color, Vector2(c.x - w * 0.32, c.y - s * 0.09), Vector2(c.x - w * 0.32, body_top + s * 0.04), s * 0.1)
			ds.draw_line(color, Vector2(c.x + w * 0.32, c.y - s * 0.09), Vector2(c.x + w * 0.32, body_top + s * 0.04), s * 0.1)
			ds.draw_round_rect(color, Vector2(c.x - w / 2.0, body_top), Vector2(w, s * 0.44), s * 0.08)
			var ink := detail_of(color)
			ds.draw_circle(ink, s * 0.07, Vector2(c.x, body_top + s * 0.17))
			ds.draw_line(ink, Vector2(c.x, body_top + s * 0.17), Vector2(c.x, body_top + s * 0.32), s * 0.06, true)
		UiIcon.CHECK:
			UiDraw.stroke_round(ds, PackedVector2Array([Vector2(c.x - s * 0.3, c.y + s * 0.02), Vector2(c.x - s * 0.08, c.y + s * 0.25),
				Vector2(c.x + s * 0.32, c.y - s * 0.24)]), color, s * 0.17, false, true)
		UiIcon.STAR:
			var star := star_points(c + Vector2(0.0, s * 0.03), s * 0.44)
			ds.draw_polygon(star, color)
			# Soften the points.
			UiDraw.stroke_round(ds, star, color, s * 0.07, true)
		UiIcon.SPARKLE:
			draw_sparkle(ds, c + Vector2(-s * 0.06, s * 0.05), s * 0.42, color)
			draw_sparkle(ds, c + Vector2(s * 0.3, -s * 0.3), s * 0.17, color)
		UiIcon.PLAY:
			var tri := PackedVector2Array([Vector2(c.x - s * 0.22, c.y - s * 0.32), Vector2(c.x + s * 0.34, c.y), Vector2(c.x - s * 0.22, c.y + s * 0.32)])
			ds.draw_polygon(tri, color)
			UiDraw.stroke_round(ds, tri, color, s * 0.08, true)


## MapScreen.kt drawMapIcon: a folded paper map with a place marked on it, [param s] across,
## centred on [param c].
static func draw_map_icon(ds: DrawScope, c: Vector2, s: float, color: Color) -> void:
	var l := c.x - s * 0.42
	var r := c.x + s * 0.42
	var t := c.y - s * 0.34
	var b := c.y + s * 0.34
	var a := c.x - s * 0.14
	var m := c.x + s * 0.14
	var dip := s * 0.07
	var paper := PackedVector2Array([Vector2(l, t + dip), Vector2(a, t), Vector2(m, t + dip), Vector2(r, t),
		Vector2(r, b - dip), Vector2(m, b), Vector2(a, b - dip), Vector2(l, b)])
	ds._apply()
	var closed := paper.duplicate()
	closed.append(paper[0])
	ds.ci.draw_polyline(closed, color, s * 0.09, true)
	ds.draw_line(color, Vector2(a, t), Vector2(a, b - dip), s * 0.07)
	ds.draw_line(color, Vector2(m, t + dip), Vector2(m, b), s * 0.07)
	ds.draw_circle(color, s * 0.07, Vector2(c.x + s * 0.28, c.y - s * 0.06))
	ds.draw_circle(color, s * 0.07, Vector2(c.x - s * 0.28, c.y + s * 0.08))


## SettingsScreen.kt drawGearIcon: a cog, a ring with eight teeth, [param s] across, centred on
## [param c].
static func draw_gear_icon(ds: DrawScope, c: Vector2, s: float, color: Color) -> void:
	ds.draw_circle(color, s * 0.2, c, 1.0, s * 0.13)
	for k in 8:
		var a := k * PI / 4.0
		var dx := cos(a)
		var dy := sin(a)
		ds.draw_line(color, c + Vector2(dx * s * 0.27, dy * s * 0.27), c + Vector2(dx * s * 0.41, dy * s * 0.41), s * 0.13)


# ---------------------------------------------------------------- currency

## The glint scale (between [constant TOKEN_GLINT_REST] and 1) at [param phase] (0..1) of a twinkle
## cycle: rest for most of it, then a quick swell and fade. Pure so it can be tested.
static func twinkle(phase: float) -> float:
	var p := clampf(phase, 0.0, 1.0)
	if p >= TWINKLE_SHARE:
		return TOKEN_GLINT_REST
	var spike := sin(p / TWINKLE_SHARE * PI)
	return TOKEN_GLINT_REST + (1.0 - TOKEN_GLINT_REST) * spike


const _EDGE := Color(0x6E / 255.0, 0x40 / 255.0, 0x06 / 255.0, 1.0)
const _RIM: Array[Color] = [Color(1.0, 0xF3 / 255.0, 0xB8 / 255.0, 1.0), Color(0xE9 / 255.0, 0xA8 / 255.0, 0x21 / 255.0, 1.0), Color(0x8A / 255.0, 0x50 / 255.0, 0x08 / 255.0, 1.0)]
const _FACE: Array[Color] = [Color(1.0, 0xE8 / 255.0, 0x9A / 255.0, 1.0), Color(0xF5 / 255.0, 0xB8 / 255.0, 0x2E / 255.0, 1.0), Color(0xC7 / 255.0, 0x7A / 255.0, 0x12 / 255.0, 1.0)]
const _TICK_LIT := Color(1.0, 0xF0 / 255.0, 0xB0 / 255.0, 1.0)
const _TICK_DARK := Color(0x5A / 255.0, 0x32 / 255.0, 0x04 / 255.0, 1.0)
const _GROOVE := Color(0x8E / 255.0, 0x52 / 255.0, 0x0A / 255.0, 1.0)
const _STAR_SHADOW := Color(0x8A / 255.0, 0x4E / 255.0, 0x08 / 255.0, 0.8)
const _STAR: Array[Color] = [Color(0xD9 / 255.0, 0x8A / 255.0, 0x16 / 255.0, 1.0), Color(0xB3 / 255.0, 0x6A / 255.0, 0x0C / 255.0, 1.0)]


## A gold arcade token, [param r] in radius round [param c]: a milled rim, a bevelled face with a
## raised star, a crescent of gloss and a four-point glint that [param glint] (0..1) scales.
static func draw_token(ds: DrawScope, c: Vector2, r: float, glint: float = TOKEN_GLINT_REST) -> void:
	if r <= 0.0:
		return
	var ppu := px_per_unit(ds)
	var one_px := 1.0 / maxf(ppu, 1e-4)
	# The coin's edge, seen below the face.
	ds.draw_circle(_EDGE, r, c + Vector2(0.0, r * 0.1))
	# The rim: a lit-from-above gradient.
	ds.draw_circle(PaBrush.linear(_RIM, c - Vector2(r, r), c + Vector2(r, r)), r, c)
	# Milling: short ticks round the rim (skipped when the coin is too small for them to read).
	if r * ppu >= 9.0:
		var ticks := 26
		var inner := r * 0.86
		var outer := r * 0.97
		var w := maxf(r * 0.055, one_px)
		for k in ticks:
			var a := k * 2.0 * PI / ticks
			var ca := cos(a)
			var sa := sin(a)
			# Ticks on the lit side catch light; the far side sinks into shadow.
			var lit := (-ca - sa) * 0.5
			var tone := UiTheme.with_alpha(_TICK_LIT, 0.5 * lit) if lit > 0.0 else UiTheme.with_alpha(_TICK_DARK, 0.45 * -lit)
			ds.draw_line(tone, c + Vector2(ca * inner, sa * inner), c + Vector2(ca * outer, sa * outer), w)
	var face := r * 0.8
	ds.draw_circle(PaBrush.radial(_FACE, c - Vector2(face * 0.35, face * 0.4), face * 1.7), face, c)
	# A bevelled groove: shadow along the lower right, light along the upper left.
	var ring := face * 0.84
	var groove := maxf(r * 0.085, one_px)
	ds.draw_arc(_GROOVE, 20.0, 160.0, false, c - Vector2(ring, ring), Vector2(ring * 2.0, ring * 2.0), 1.0, groove)
	ds.draw_arc(UiTheme.with_alpha(_TICK_LIT, 0.85), 200.0, 160.0, false, c - Vector2(ring, ring), Vector2(ring * 2.0, ring * 2.0), 1.0, groove)
	# The raised star, with its own little shadow and a lit top edge.
	var star := star_points(c + Vector2(0.0, r * 0.03), r * 0.48)
	var shadow := star.duplicate()
	for i in shadow.size():
		shadow[i] += Vector2(r * 0.03, r * 0.05)
	ds.draw_polygon(shadow, _STAR_SHADOW)
	ds.draw_polygon(star, PaBrush.vertical(_STAR, c.y - r * 0.45, c.y + r * 0.45))
	ds.push()
	ds.rotate_deg(-35.0, c)
	ds.draw_oval(Color(1, 1, 1, 0.42), c + Vector2(-r * 0.55, -r * 0.84), Vector2(r * 0.7, r * 0.24))
	ds.pop()
	if glint > 0.02:
		draw_sparkle(ds, c + Vector2(r * 0.56, -r * 0.6), r * 0.44 * glint, Color(1, 1, 1, 0.92))


const _TICKET_SHADOW := Color(0x7A / 255.0, 0x2A / 255.0, 0x06 / 255.0, 1.0)
const _TICKET_BODY: Array[Color] = [Color(1.0, 0xC0 / 255.0, 0x70 / 255.0, 1.0), Color(0xF0 / 255.0, 0x7A / 255.0, 0x1A / 255.0, 1.0), Color(0xD9 / 255.0, 0x60 / 255.0, 0x0C / 255.0, 1.0)]
const _TICKET_HOLE := Color(0x8A / 255.0, 0x34 / 255.0, 0x06 / 255.0, 0.55)
const _TICKET_PRINT := Color(1.0, 0xE0 / 255.0, 0xA8 / 255.0, 1.0)
const _TICKET_STAR: Array[Color] = [Color(0xD0 / 255.0, 0x3A / 255.0, 0x0C / 255.0, 1.0), Color(0x9A / 255.0, 0x26 / 255.0, 0x06 / 255.0, 1.0)]
const _SHEEN: Array[Color] = [Color(1, 1, 1, 0.34), Color(1, 1, 1, 0.0)]


## The ticket's outline (a rounded body with a half-round notch cut into each end), [param w] wide
## centred on [param c].
static func ticket_shape(c: Vector2, w: float) -> PackedVector2Array:
	var h := w * 0.58
	var tl := c - Vector2(w / 2.0, h / 2.0)
	var notch := h * 0.18
	var shape := PaPath.round_rect_points(tl.x, tl.y, tl.x + w, tl.y + h, h * 0.12, h * 0.12, 4)
	for cx: float in [tl.x, tl.x + w]:
		var cut := PackedVector2Array()
		for i in 20:
			var a := TAU * i / 20.0
			cut.append(Vector2(cx + cos(a) * notch, c.y + sin(a) * notch))
		var left := Geometry2D.clip_polygons(shape, cut)
		if not left.is_empty():
			shape = left[0]
	return shape


## A prize ticket [param w] wide centred on [param c]: notched ends, a perforated stub, a printed
## border, a gradient star and a band of sheen.
static func draw_ticket(ds: DrawScope, c: Vector2, w: float) -> void:
	if w <= 0.0:
		return
	var h := w * 0.58
	var tl := c - Vector2(w / 2.0, h / 2.0)
	var ticket := ticket_shape(c, w)
	ds.push()
	ds.rotate_deg(-8.0, c)
	ds.draw_polygon(ticket, _TICKET_SHADOW, 0.6)
	ds.translate(0.0, -h * 0.07)
	ds.draw_polygon(ticket, PaBrush.vertical(_TICKET_BODY, tl.y, tl.y + h))
	# The stub's perforation: a column of pin-holes a quarter of the way in.
	var perf_x := tl.x + w * 0.24
	var holes := 5
	for k in holes:
		ds.draw_circle(_TICKET_HOLE, h * 0.035, Vector2(perf_x, tl.y + h * (0.16 + 0.68 * k / (holes - 1))))
	ds.draw_round_rect(_TICKET_PRINT, tl + Vector2(w * 0.32, h * 0.2), Vector2(w * 0.52, h * 0.6), h * 0.08, 1.0, h * 0.06)
	var star_c := Vector2(tl.x + w * 0.58, c.y)
	ds.draw_polygon(star_points(star_c, h * 0.21), PaBrush.vertical(_TICKET_STAR, c.y - h * 0.2, c.y + h * 0.2))
	# A band of sheen across the upper half.
	ds.draw_round_rect(PaBrush.vertical(_SHEEN, tl.y, tl.y + h * 0.5), tl + Vector2(w * 0.03, h * 0.04), Vector2(w * 0.94, h * 0.42), h * 0.1)
	ds.pop()
