class_name WhackArt
extends RefCounted
## games/whackamole/WhackArt.kt: painted art for the 3D whack-a-mole table. Every texture is made on
## first use and shared (Kotlin's `by lazy`).

static var _front: PaTexture = null
static var _backboard: PaTexture = null
static var _cloud: PaTexture = null
static var _wood: PaTexture = null
static var _well: PaTexture = null
static var _well_bottom: PaTexture = null
static var _rim: PaTexture = null
static var _mallet_head: PaTexture = null
static var _mallet_cap: PaTexture = null
static var _handle: PaTexture = null
static var _star: PaTexture = null
## The score display panel and its painter (repainted whenever its message changes).
static var _panel_paint: TexPaint = null
static var _panel_tex: PaTexture = null


## Wood with a few long grain streaks across a [param w] × [param h] area.
static func _wood_grain(tp: TexPaint, x: float, y: float, w: float, h: float, base: int, seed_v: int) -> void:
	tp.vgrad(x, y, w, h, [base, Pal.shade(base, 0.8)])
	var lines := maxi(int(w * h / 90.0), 3)
	for i in lines:
		var gx := x + MathUtil.hash01(i, seed_v) * w
		var gy := y + MathUtil.hash01(i, seed_v + 1) * h
		var length := 6.0 + MathUtil.hash01(i, seed_v + 2) * 18.0
		tp.line(gx, gy, minf(gx + length, x + w), gy + 0.3, 0.45, Pal.with_alpha(Pal.shade(base, 0.6), 0.5))


## The playfield: mown grass with flowers inside a wooden border, earthy rings round the holes and
## the holes themselves cut out (transparent) so moles can rise through them. [param holes] holds
## each hole's centre as [x, y] in texels.
static func table(w: int, h: int, holes: Array, hole_r: float) -> PaTexture:
	return TexPaint.paint_texture(w, h, 2, func(tp: TexPaint) -> void:
		# Deeper than the paint looks under the lights: the grass sits well under the bloom.
		tp.vgrad(0.0, 0.0, float(w), float(h), [Pal.shade(Pal.GREEN, 0.72), Pal.shade(Pal.GREEN, 0.56)])
		# Mown stripes (dark and faintly bright in turn), blade tufts and little flowers.
		for y in range(0, h, 40):
			tp.rect(0.0, float(y), float(w), 20.0, Pal.with_alpha(Pal.shade(Pal.GREEN, 0.5), 0.4))
			tp.rect(0.0, y + 20.0, float(w), 20.0, Pal.with_alpha(Pal.LIME, 0.05))
		for i in 900:
			var x := MathUtil.hash01(i, 11) * w
			var y := MathUtil.hash01(i, 12) * h
			var col := Pal.LIME if i % 3 == 0 else Pal.DARKGREEN
			var lean := (MathUtil.hash01(i, 15) - 0.5) * 2.0
			tp.line(x, y, x + lean, y - 2.6, 0.6, Pal.with_alpha(Pal.shade(col, 0.9), 0.8))
		for i in 30:
			var x := MathUtil.hash01(i, 13) * w
			var y := MathUtil.hash01(i, 14) * h
			var col := Pal.YELLOW
			if i % 3 == 1:
				col = Pal.WHITE
			elif i % 3 == 2:
				col = Pal.HOTPINK
			for k in 5:
				var a := k * 1.2566
				tp.circle(x + cos(a) * 1.3, y + sin(a) * 1.3, 1.0, col)
			tp.circle(x, y, 0.8, Pal.ORANGE)
		# Contact shadow round each hole, and trampled earth right at its rim.
		for hc: Array in holes:
			tp.radial(hc[0], hc[1] + 3.0, hole_r + 30.0, Pal.with_alpha(Pal.BLACK, 0.6), 0)
			tp.circle(hc[0], hc[1] + 3.0, hole_r + 11.0, Pal.shade(Pal.BROWN, 0.62))
			tp.ring(hc[0], hc[1] + 3.0, hole_r + 10.0, 2.0, Pal.shade(Pal.BROWN, 0.45))
		# The far corners and the edges of the playfield fall into shadow.
		tp.vgrad(0.0, 0.0, float(w), h * 0.16, [0x66000000, 0])
		tp.hgrad(0.0, 0.0, w * 0.06, float(h), [0x55000000, 0])
		tp.hgrad(w * 0.94, 0.0, w * 0.06, float(h), [0, 0x55000000])
		# Wooden border.
		var b := 18.0
		_wood_grain(tp, 0.0, 0.0, float(w), b, Pal.WOOD, 21)
		_wood_grain(tp, 0.0, h - b, float(w), b, Pal.WOOD, 22)
		_wood_grain(tp, 0.0, 0.0, b, float(h), Pal.WOOD, 23)
		_wood_grain(tp, w - b, 0.0, b, float(h), Pal.WOOD, 24)
		tp.stroke_round(b - 1.0, b - 1.0, w - 2 * b + 2.0, h - 2 * b + 2.0, 3.0, 2.0, Pal.shade(Pal.WOOD, 0.55))
		tp.stroke_round(0.5, 0.5, w - 1.0, h - 1.0, 2.0, 1.0, Pal.shade(Pal.WOOD, 0.5))
		for hc: Array in holes:
			tp.punch(hc[0], hc[1], hole_r))


## Front of the cabinet under the table.
static func front() -> PaTexture:
	if _front == null:
		_front = TexPaint.paint_texture(210, 100, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 210.0, 100.0, [Pal.shade(Pal.GREEN, 0.7), Pal.shade(Pal.DARKGREEN, 0.6)])
			for x in range(0, 210, 30):
				tp.round_grad(x + 2.0, 14.0, 22.0, 78.0, 4.0, Pal.shade(Pal.DARKGREEN, 0.85), Pal.shade(Pal.DARKGREEN, 0.6))
			_wood_grain(tp, 0.0, 0.0, 210.0, 8.0, Pal.WOOD, 31)
			tp.rect(0.0, 8.0, 210.0, 1.5, Pal.shade(Pal.WOOD, 0.45))
			tp.rect(0.0, 94.0, 210.0, 6.0, Pal.shade(Pal.BROWN, 0.55)))
	return _front


## A rolling hill across the backboard (WhackArt.kt's local `hill`).
static func _hill(tp: TexPaint, base: float, amp1: float, f1: float, amp2: float, f2: float, phase: float, color: int) -> void:
	var pts := PackedFloat32Array([0.0, 140.0])
	var x := 0.0
	while x <= 210.0:
		pts.append(x)
		pts.append(base + sin(x / f1 + phase) * amp1 + sin(x / f2) * amp2)
		x += 3.0
	pts.append(210.0)
	pts.append(140.0)
	tp.polygon(pts, color)


## Backboard: wooden frame, a painted meadow under a sunny sky, and the title plaque.
static func backboard() -> PaTexture:
	if _backboard == null:
		_backboard = TexPaint.paint_texture(210, 140, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 210.0, 140.0, [Pal.mix(Pal.SKY, Pal.WHITE, 0.15), Pal.shade(Pal.SKY, 0.72)])
			tp.radial(178.0, 30.0, 30.0, Pal.with_alpha(Pal.YELLOW, 0.55), 0)
			tp.circle(178.0, 30.0, 11.0, Pal.YELLOW)
			tp.circle(178.0, 30.0, 8.5, Pal.CREAM)
			# (The clouds drift across live: see cloud.)
			# Rolling hills.
			_hill(tp, 96.0, 6.0, 17.0, 2.0, 7.0, 0.0, Pal.shade(Pal.GREEN, 0.85))
			_hill(tp, 112.0, 5.0, 11.0, 1.0, 5.0, 2.0, Pal.DARKGREEN)
			# Title plaque.
			var text := "WHACK-A-MOLE"
			tp.round_grad(24.0, 42.0, 162.0, 26.0, 5.0, Pal.shade(Pal.BROWN, 0.85), Pal.shade(Pal.BROWN, 0.6))
			tp.stroke_round(24.0, 42.0, 162.0, 26.0, 5.0, 1.6, Pal.GOLD)
			tp.label(text, 105.0, 48.0, 14.0, Pal.YELLOW, true, false, Pal.BLACK)
			# Frame.
			_wood_grain(tp, 0.0, 0.0, 210.0, 6.0, Pal.WOOD, 41)
			_wood_grain(tp, 0.0, 0.0, 6.0, 140.0, Pal.WOOD, 42)
			_wood_grain(tp, 204.0, 0.0, 6.0, 140.0, Pal.WOOD, 43)
			tp.stroke_round(6.0, 6.0, 198.0, 140.0, 1.0, 1.0, Pal.shade(Pal.WOOD, 0.55))
			# Occlusion under the frame and towards the corners.
			tp.vgrad(6.0, 6.0, 198.0, 14.0, [0x55000000, 0])
			tp.hgrad(6.0, 6.0, 16.0, 134.0, [0x44000000, 0])
			tp.hgrad(188.0, 6.0, 16.0, 134.0, [0, 0x44000000]))
	return _backboard


## A soft white cloud with a faintly shaded underside, drifted across the backboard by the game.
static func cloud() -> PaTexture:
	if _cloud == null:
		_cloud = TexPaint.paint_texture(32, 12, 8, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.oval(16.0, 8.2, 14.0, 3.2, Pal.with_alpha(Pal.shade(Pal.SKY, 0.85), 0.55))
			tp.oval(16.0, 7.4, 13.5, 3.6, Pal.WHITE)
			tp.circle(10.0, 5.6, 4.0, Pal.WHITE)
			tp.circle(17.0, 4.2, 4.8, Pal.WHITE)
			tp.circle(22.5, 5.8, 3.6, Pal.WHITE)
			tp.oval(17.0, 3.4, 3.0, 1.4, Pal.with_alpha(Pal.CREAM, 0.7)))
	return _cloud


static func wood() -> PaTexture:
	if _wood == null:
		_wood = TexPaint.paint_texture(32, 16, 4, func(tp: TexPaint) -> void:
			_wood_grain(tp, 0.0, 0.0, 32.0, 16.0, Pal.WOOD, 5))
	return _wood


## Dark soil inside the holes, darker towards the bottom.
static func well() -> PaTexture:
	if _well == null:
		_well = TexPaint.paint_texture(16, 32, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 16.0, 32.0, [Pal.shade(Pal.DARKBROWN, 0.55), Pal.BLACK, Pal.BLACK])
			for i in 14:
				tp.circle(MathUtil.hash01(i, 7) * 16.0, MathUtil.hash01(i, 8) * 20.0, 0.5, Pal.shade(Pal.BROWN, 0.55)))
	return _well


static func well_bottom() -> PaTexture:
	if _well_bottom == null:
		_well_bottom = TexKit.solid(8, 8, Pal.BLACK)
	return _well_bottom


## Rubber hole rim: a light top edge fading down.
static func rim() -> PaTexture:
	if _rim == null:
		_rim = TexPaint.paint_texture(32, 8, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 32.0, 8.0, [Pal.shade(Pal.DARKGREEN, 1.05), Pal.shade(Pal.DARKGREEN, 0.5)])
			tp.vgrad(0.0, 0.0, 32.0, 2.5, [Pal.with_alpha(Pal.LIME, 0.75), Pal.with_alpha(Pal.LIME, 0.0)]))
	return _rim


## The score display panel's texture (128 × 24 texels at 4× detail), repainted by [method paint_panel].
static func panel_tex() -> PaTexture:
	if _panel_tex == null:
		_panel_tex = PaTexture.new(128, 24, null, 4)
	return _panel_tex


static func paint_panel(text: String, color: int) -> void:
	if _panel_paint == null:
		_panel_paint = TexPaint.new(128 * 4, 24 * 4)
		_panel_paint.use_units(4.0)
	var tp := _panel_paint
	# (Godot's painter records its drawing: start the recording over instead of painting on top.)
	tp.clear(0)
	tp.fill(0xFF06040A)
	for y in range(0, 24, 2):
		tp.rect(0.0, float(y), 128.0, 0.6, Pal.with_alpha(Pal.NIGHT, 0.6))
	tp.stroke_round(0.5, 0.5, 127.0, 23.0, 2.0, 1.0, Pal.shade(Pal.GRAY, 0.6))
	tp.glow(2.0, Pal.with_alpha(color, 0.8), func(g: TexPaint) -> void:
		g.label(text, 64.0, 6.0, 12.0, Pal.WHITE))
	tp.label(text, 64.0, 6.0, 12.0, Pal.mix(color, Pal.WHITE, 0.25))
	tp.update(panel_tex())


static func mallet_head() -> PaTexture:
	if _mallet_head == null:
		_mallet_head = TexPaint.paint_texture(32, 16, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 32.0, 16.0, [Pal.RED, Pal.DARKRED])
			tp.rect(0.0, 0.0, 4.0, 16.0, Pal.CREAM)
			tp.rect(28.0, 0.0, 4.0, 16.0, Pal.CREAM)
			tp.vgrad(0.0, 2.5, 32.0, 3.0, [Pal.mix(Pal.RED, Pal.WHITE, 0.45), Pal.with_alpha(Pal.RED, 0.0)]))
	return _mallet_head


static func mallet_cap() -> PaTexture:
	if _mallet_cap == null:
		_mallet_cap = TexPaint.paint_texture(16, 16, 4, func(tp: TexPaint) -> void:
			tp.fill(Pal.CREAM)
			tp.ring(8.0, 8.0, 6.5, 1.2, Pal.shade(Pal.CREAM, 0.7)))
	return _mallet_cap


static func handle() -> PaTexture:
	if _handle == null:
		_handle = TexPaint.paint_texture(8, 32, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 8.0, 32.0, [Pal.TAN, Pal.shade(Pal.TAN, 0.75)])
			tp.rect(0.0, 0.0, 8.0, 6.0, Pal.shade(Pal.DARKRED, 0.8))
			for y in range(1, 6, 2):
				tp.rect(0.0, float(y), 8.0, 0.5, Pal.shade(Pal.DARKRED, 0.6)))
	return _handle


## The dizzy stars that circle a bonked mole.
static func star() -> PaTexture:
	if _star == null:
		_star = TexPaint.paint_texture(9, 9, 12, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.glow(0.8, Pal.with_alpha(Pal.ORANGE, 0.9), func(g: TexPaint) -> void:
				g.star(4.5, 4.7, 4.2, Pal.WHITE))
			tp.star(4.5, 4.7, 4.0, Pal.YELLOW)
			tp.star(4.2, 4.3, 2.0, Pal.mix(Pal.YELLOW, Pal.WHITE, 0.6)))
	return _star
