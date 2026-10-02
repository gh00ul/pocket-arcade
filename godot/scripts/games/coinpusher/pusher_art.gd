class_name PusherArt
extends RefCounted
## games/coinpusher/PusherArt.kt: painted art for the 3D coin pusher. Every texture is made on
## first use and shared (Kotlin's `by lazy`).

static var _coin: PaTexture = null
static var _coin_edge: PaTexture = null
static var _big_coin: PaTexture = null
static var _gem: PaTexture = null
static var _tickets: PaTexture = null
static var _star: PaTexture = null
static var _shelf_front: PaTexture = null
static var _shelf_top: PaTexture = null
static var _back_wall: PaTexture = null
static var _cabinet: PaTexture = null
static var _gold: PaTexture = null
static var _tray: PaTexture = null
static var _lip_face: PaTexture = null
static var _dark: PaTexture = null
static var _glass: PaTexture = null


## A coin face seen from above: milled rim, face, an emboss and a glint.
static func _coin_face(n: int, face: int, rim: int, mark: bool) -> PaTexture:
	return TexPaint.paint_texture(n, n, 8, func(tp: TexPaint) -> void:
		tp.clear(0)
		var m := n / 2.0
		tp.circle(m, m, m - 0.3, rim)
		for k in 40:
			var a := k * (2.0 * PI / 40.0)
			tp.line(m + cos(a) * (m - 1.6), m + sin(a) * (m - 1.6), m + cos(a) * (m - 0.4), m + sin(a) * (m - 0.4), 0.25, Pal.shade(rim, 0.75))
		var p := PaPaint.new(0xFFFFFFFF)
		p.shader = PaBrush.radial([Pal.mix(face, Pal.WHITE, 0.35), Pal.shade(face, 0.85)], Vector2(m - m * 0.3, m - m * 0.35), m * 1.3)
		tp.c_draw_circle(m, m, m - 2.0, p)
		tp.ring(m, m, m - 3.6, 0.6, Pal.shade(face, 0.8))
		if mark:
			tp.star(m + 0.3, m + 0.4, m * 0.42, Pal.shade(face, 0.72))
			tp.star(m, m, m * 0.42, Pal.mix(face, Pal.WHITE, 0.2))
		else:
			tp.oval(m + 0.3, m + 0.4, m * 0.25, m * 0.3, Pal.shade(face, 0.75))
			tp.oval(m, m, m * 0.25, m * 0.3, Pal.mix(face, Pal.WHITE, 0.15))
		tp.oval(m - m * 0.45, m - m * 0.45, m * 0.18, m * 0.08, Pal.with_alpha(Pal.WHITE, 0.8)))


static func coin() -> PaTexture:
	if _coin == null:
		_coin = _coin_face(24, Pal.GOLD, Pal.ORANGE, false)
	return _coin


static func coin_edge() -> PaTexture:
	if _coin_edge == null:
		_coin_edge = TexPaint.paint_texture(24, 24, 4, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.circle(12.0, 12.0, 11.6, Pal.shade(Pal.ORANGE, 0.6)))
	return _coin_edge


static func big_coin() -> PaTexture:
	if _big_coin == null:
		_big_coin = _coin_face(32, Pal.YELLOW, Pal.ORANGE, true)
	return _big_coin


## A cut gem: facets in two blues with a sparkle.
static func gem() -> PaTexture:
	if _gem == null:
		_gem = TexPaint.paint_texture(12, 12, 12, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.polygon(PackedFloat32Array([2.0, 4.0, 6.0, 11.5, 10.0, 4.0]), Pal.SKY)
			tp.polygon(PackedFloat32Array([2.0, 4.0, 6.0, 11.5, 6.0, 4.0]), Pal.shade(Pal.SKY, 0.8))
			tp.polygon(PackedFloat32Array([3.5, 1.0, 8.5, 1.0, 10.0, 4.0, 2.0, 4.0]), Pal.CYAN)
			tp.polygon(PackedFloat32Array([5.0, 1.0, 7.0, 1.0, 7.5, 4.0, 4.5, 4.0]), Pal.mix(Pal.CYAN, Pal.WHITE, 0.5))
			tp.line(2.0, 4.0, 10.0, 4.0, 0.25, Pal.WHITE)
			tp.star(4.0, 2.4, 1.3, Pal.WHITE, 0.3))
	return _gem


static func tickets() -> PaTexture:
	if _tickets == null:
		_tickets = TexPaint.paint_texture(20, 14, 8, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.round(0.0, 3.0, 20.0, 11.0, 1.0, Pal.ORANGE)
			tp.round(0.0, 0.0, 20.0, 11.0, 1.0, Pal.GOLD)
			tp.stroke_round(0.4, 0.4, 19.2, 10.2, 1.0, 0.6, Pal.ORANGE)
			for x in range(4, 20, 5):
				tp.line(float(x), 1.5, float(x), 9.5, 0.3, Pal.shade(Pal.GOLD, 0.75))
			tp.star(2.3, 5.5, 1.4, Pal.DARKRED))
	return _tickets


static func star() -> PaTexture:
	if _star == null:
		_star = TexPaint.paint_texture(14, 14, 10, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.glow(0.8, Pal.with_alpha(Pal.ORANGE, 0.9), func(g: TexPaint) -> void:
				g.star(7.0, 7.4, 6.4, Pal.WHITE))
			tp.star(7.0, 7.4, 6.2, Pal.YELLOW)
			tp.star(6.6, 6.8, 3.0, Pal.mix(Pal.YELLOW, Pal.WHITE, 0.6)))
	return _star


## The deck: dark blue with lane stripes and arrows towards the lip.
static func deck(w: int, h: int) -> PaTexture:
	return TexPaint.paint_texture(w, h, 3, func(tp: TexPaint) -> void:
		tp.vgrad(0.0, 0.0, float(w), float(h), [Pal.shade(Pal.NAVY, 0.8), Pal.NAVY])
		for y in range(0, h, 40):
			tp.rect(0.0, float(y), float(w), 1.5, Pal.INDIGO)
		for x in range(0, w, 50):
			tp.rect(float(x), 0.0, 0.8, float(h), Pal.shade(Pal.INDIGO, 0.8))
		for i in 60:
			tp.circle(MathUtil.hash01(i, 31) * w, MathUtil.hash01(i, 32) * h, 0.5, Pal.shade(Pal.SKY, 0.5))
		# A warm glow along each side wall, and a cool wash down the middle so coins pop against the deck.
		tp.hgrad(0.0, 0.0, w * 0.06, float(h), [Pal.with_alpha(Pal.GOLD, 0.22), Pal.with_alpha(Pal.GOLD, 0.0)])
		tp.hgrad(w * 0.94, 0.0, w * 0.06, float(h), [Pal.with_alpha(Pal.GOLD, 0.0), Pal.with_alpha(Pal.GOLD, 0.22)])
		tp.hgrad(w * 0.3, 0.0, w * 0.4, float(h), [Pal.with_alpha(Pal.SKY, 0.0), Pal.with_alpha(Pal.SKY, 0.07), Pal.with_alpha(Pal.SKY, 0.0)])
		for k in 3:
			var cy := h - 26.0 - k * 10.0
			for i in 6:
				var cx := 25.0 + i * 50.0
				var c := Pal.with_alpha(Pal.shade(Pal.GOLD, 0.6), 0.85)
				tp.line(cx - 6.0, cy - 6.0, cx, cy, 1.2, c)
				tp.line(cx, cy, cx + 6.0, cy - 6.0, 1.2, c))


## Pusher shelf front: hazard stripes.
static func shelf_front() -> PaTexture:
	if _shelf_front == null:
		_shelf_front = TexPaint.paint_texture(120, 14, 4, func(tp: TexPaint) -> void:
			tp.fill(Pal.BLACK)
			var x := -14.0
			while x < 120.0:
				tp.polygon(PackedFloat32Array([x, 14.0, x + 7.0, 14.0, x + 21.0, 0.0, x + 14.0, 0.0]), Pal.YELLOW)
				x += 14.0
			tp.vgrad(0.0, 0.0, 120.0, 2.0, [Pal.LIGHTGRAY, Pal.GRAY]))
	return _shelf_front


## The pusher shelf's top: brushed steel, darker at the back and catching a highlight along the front.
static func shelf_top() -> PaTexture:
	if _shelf_top == null:
		_shelf_top = TexPaint.paint_texture(60, 40, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 60.0, 40.0, [Pal.shade(Pal.DARKGRAY, 0.9), Pal.shade(Pal.GRAY, 0.85)])
			for y in range(0, 40, 3):
				tp.rect(0.0, float(y), 60.0, 0.5, Pal.with_alpha(Pal.shade(Pal.GRAY, 0.6), 0.7))
			for y in range(1, 40, 5):
				tp.rect(0.0, float(y), 60.0, 0.35, Pal.with_alpha(Pal.LIGHTGRAY, 0.25))
			tp.vgrad(0.0, 33.0, 60.0, 7.0, [Pal.with_alpha(Pal.LIGHTGRAY, 0.0), Pal.with_alpha(Pal.LIGHTGRAY, 0.55)])
			tp.hgrad(0.0, 0.0, 6.0, 40.0, [0x66000000, 0])
			tp.hgrad(54.0, 0.0, 6.0, 40.0, [0, 0x66000000]))
	return _shelf_top


## Back wall of the cabinet, with room for the coin counter.
static func back_wall() -> PaTexture:
	if _back_wall == null:
		_back_wall = TexPaint.paint_texture(180, 130, 3, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 180.0, 130.0, [Pal.shade(Pal.ORANGE, 0.8), Pal.shade(Pal.DARKRED, 0.8)])
			for i in 9:
				var x := 10.0 + i * 20.0
				tp.hgrad(x, 0.0, 8.0, 130.0, [Pal.shade(Pal.ORANGE, 0.62), Pal.shade(Pal.ORANGE, 0.75), Pal.shade(Pal.ORANGE, 0.62)])
			tp.vgrad(0.0, 120.0, 180.0, 10.0, [Pal.shade(Pal.DARKRED, 0.6), Pal.shade(Pal.DARKRED, 0.4)])
			# Occlusion: shadow settles at the foot of the wall and in its corners.
			tp.vgrad(0.0, 84.0, 180.0, 46.0, [0, 0x88000000])
			tp.hgrad(0.0, 0.0, 26.0, 130.0, [0x77000000, 0])
			tp.hgrad(154.0, 0.0, 26.0, 130.0, [0, 0x77000000])
			tp.rect(0.0, 0.0, 180.0, 4.0, Pal.GOLD)
			tp.glow(2.0, Pal.with_alpha(Pal.YELLOW, 0.6), func(g: TexPaint) -> void:
				g.label("COIN PUSHER", 90.0, 8.0, 13.0, Pal.WHITE))
			tp.label("COIN PUSHER", 90.0, 8.0, 13.0, Pal.YELLOW, true, false, Pal.DARKRED))
	return _back_wall


static func cabinet() -> PaTexture:
	if _cabinet == null:
		_cabinet = TexPaint.paint_texture(32, 32, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 32.0, 32.0, [Pal.ORANGE, Pal.shade(Pal.ORANGE, 0.6)])
			tp.vgrad(0.0, 0.0, 32.0, 3.0, [Pal.GOLD, Pal.with_alpha(Pal.GOLD, 0.4)]))
	return _cabinet


static func gold() -> PaTexture:
	if _gold == null:
		_gold = TexKit.solid(8, 8, Pal.GOLD)
	return _gold


## The win tray: plum velvet with gold stripes.
static func tray() -> PaTexture:
	if _tray == null:
		_tray = TexPaint.paint_texture(64, 32, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 64.0, 32.0, [Pal.PLUM, Pal.shade(Pal.PLUM, 0.7)])
			for x in range(0, 64, 8):
				tp.rect(float(x), 0.0, 0.6, 32.0, Pal.shade(Pal.GOLD, 0.5))
			tp.grain(0.05, 3))
	return _tray


## The face below the lip that coins tumble past.
static func lip_face() -> PaTexture:
	if _lip_face == null:
		_lip_face = TexPaint.paint_texture(64, 16, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 64.0, 16.0, [Pal.shade(Pal.ORANGE, 0.55), Pal.shade(Pal.DARKRED, 0.5)])
			for x in range(0, 64, 16):
				tp.round(x + 0.0, 3.0, 8.0, 2.0, 1.0, Pal.YELLOW))
	return _lip_face


static func dark() -> PaTexture:
	if _dark == null:
		_dark = TexKit.solid(8, 8, Pal.shade(Pal.NIGHT, 0.7))
	return _dark


static func glass() -> PaTexture:
	if _glass == null:
		_glass = TexPaint.paint_texture(32, 16, 4, func(tp: TexPaint) -> void:
			tp.fill(Pal.with_alpha(Pal.SKY, 0.16))
			tp.polygon(PackedFloat32Array([3.0, 2.0, 12.0, 2.0, 7.0, 9.0, 3.0, 9.0]), Pal.with_alpha(Pal.WHITE, 0.3))
			tp.rect(0.0, 0.0, 32.0, 0.6, Pal.with_alpha(Pal.WHITE, 0.6)))
	return _glass


## Small LED panel (Kotlin `PusherArt.Panel`; Godot has a Panel class) repainted with the coins left / won counters.
class PaPanel:
	extends RefCounted
	var _cols: int
	var _rows: int
	var _painter: TexPaint = null
	var tex: PaTexture
	var _last := ""

	func _init(cols: int, rows: int) -> void:
		_cols = cols
		_rows = rows
		tex = PaTexture.new(cols, rows, null, 4)

	func paint(text: String, color: int) -> void:
		if text == _last:
			return
		_last = text
		if _painter == null:
			_painter = TexPaint.new(_cols * 4, _rows * 4)
			_painter.use_units(4.0)
		var tp := _painter
		# (Godot's painter records its drawing: start the recording over instead of painting on top.)
		tp.clear(0)
		tp.fill(0xFF06040A)
		for y in range(0, _rows, 2):
			tp.rect(0.0, float(y), float(_cols), 0.6, Pal.with_alpha(Pal.NIGHT, 0.7))
		tp.stroke_round(0.5, 0.5, _cols - 1.0, _rows - 1.0, 2.0, 1.0, Pal.shade(Pal.GOLD, 0.6))
		var cap := 11.0
		var cols := _cols
		var rows := _rows
		tp.glow(1.5, Pal.with_alpha(color, 0.8), func(g: TexPaint) -> void:
			g.label(text, cols / 2.0, (rows - cap) / 2.0, cap, Pal.WHITE))
		tp.label(text, cols / 2.0, (rows - cap) / 2.0, cap, Pal.mix(color, Pal.WHITE, 0.2))
		tp.update(tex)
