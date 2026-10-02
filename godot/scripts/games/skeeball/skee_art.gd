class_name SkeeArt
extends RefCounted
## games/skeeball/SkeeArt.kt: painted textures and the ball for the 3D skee-ball alley. Every
## texture is made on first use and shared (Kotlin's `by lazy`).

## Texels per world unit across the target board.
const BOARD_TPU := 1.5

static var _chevron: PaTexture = null
static var _ramp: PaTexture = null
static var _side_panel: PaTexture = null
static var _rail_side: PaTexture = null
static var _rail_top: PaTexture = null
static var _ring_wall: PaTexture = null
static var _marquee: PaTexture = null
static var _pit: PaTexture = null
static var _board_edge: PaTexture = null


## Wooden planks running along a [param w] × [param h] area, with seams, grain and the odd knot.
static func _planks(tp: TexPaint, w: float, h: float, plank_w: float, base: int, seed_v: int) -> void:
	var n := int((w + plank_w - 1.0) / plank_w)
	for i in n:
		var tone := 0.86 + MathUtil.hash01(i, seed_v) * 0.22
		var col := Pal.mix(base, Pal.CREAM, (tone - 1.0) * 0.8) if tone > 1.0 else Pal.shade(base, tone)
		var x0 := i * plank_w
		tp.hgrad(x0, 0.0, plank_w, h, [Pal.shade(col, 0.95), col, Pal.shade(col, 0.93)])
		# Grain: long faint streaks.
		for k in int(h / 5.0):
			var gx := x0 + 1.0 + MathUtil.hash01(i * 97 + k, seed_v + 1) * (plank_w - 2.0)
			var gy := MathUtil.hash01(i * 97 + k, seed_v + 2) * h
			var length := 8.0 + MathUtil.hash01(k, i + seed_v) * 26.0
			tp.line(gx, gy, gx + (MathUtil.hash01(k, i) - 0.5) * 0.6, gy + length, 0.35, Pal.with_alpha(Pal.shade(col, 0.82), 0.6))
		if MathUtil.hash01(i, seed_v + 7) > 0.5:
			var ky := MathUtil.hash01(i, seed_v + 8) * (h - 8.0) + 3.0
			tp.oval(x0 + plank_w / 2.0, ky, 1.7, 2.6, Pal.shade(col, 0.72))
			tp.oval(x0 + plank_w / 2.0, ky, 0.8, 1.3, Pal.shade(col, 0.6))
		tp.rect(x0, 0.0, 0.6, h, Pal.shade(base, 0.55))
		tp.rect(x0 + 0.6, 0.0, 0.6, h, Pal.with_alpha(Pal.WHITE, 0.12))
		# Butt joints at staggered lengths.
		var y := MathUtil.hash01(i, seed_v + 3) * 120.0
		while y < h:
			tp.rect(x0, y, plank_w, 0.6, Pal.shade(base, 0.6))
			y += 140.0 + MathUtil.hash01(i + int(y), seed_v + 4) * 80.0


## The lane from the ramp (top of the texture) to the player's end.
static func lane(width_units: int, length_units: int, tpu: float) -> PaTexture:
	var w := int(width_units * tpu)
	var h := int(length_units * tpu)
	return TexPaint.paint_texture(w, h, 2, func(tp: TexPaint) -> void:
		_planks(tp, float(w), float(h), 18.0, Pal.shade(Pal.WOOD, 0.92), 3)
		# Varnish: a faint sheen down the middle, darker edges.
		tp.hgrad(0.0, 0.0, w * 0.18, float(h), [0x55000000, 0])
		tp.hgrad(w * 0.82, 0.0, w * 0.18, float(h), [0, 0x55000000])
		tp.hgrad(w * 0.3, 0.0, w * 0.4, float(h), [0, 0x0EFFFFFF, 0])
		# Depth: the far end of the lane, by the ramp, sits in shadow and the player's end catches the light.
		tp.vgrad(0.0, 0.0, float(w), h * 0.45, [0x88000000, 0])
		# Foul line near the player's end.
		var fy := h - 60.0 * tpu
		tp.rect(0.0, fy, float(w), 3.0, Pal.CREAM)
		tp.rect(0.0, fy + 3.0, float(w), 1.0, Pal.shade(Pal.WOOD, 0.55)))


## A chevron pointing up the lane, for the pulsing guide lights.
static func chevron() -> PaTexture:
	if _chevron == null:
		_chevron = TexPaint.paint_texture(24, 16, 6, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.polygon(PackedFloat32Array([12.0, 1.0, 23.0, 12.0, 19.0, 15.0, 12.0, 8.0, 5.0, 15.0, 1.0, 12.0]), Pal.WHITE))
	return _chevron


## Lighter, steeper planks for the jump ramp.
static func ramp() -> PaTexture:
	if _ramp == null:
		_ramp = TexPaint.paint_texture(120, 24, 4, func(tp: TexPaint) -> void:
			_planks(tp, 120.0, 24.0, 9.0, Pal.TAN, 11)
			tp.vgrad(0.0, 0.0, 120.0, 2.5, [Pal.CREAM, Pal.with_alpha(Pal.CREAM, 0.3)]))
	return _ramp


## The inclined target board: concentric scoring rings, their values, the centre cup and the 200
## bonus hole. Laid out so the game's squashed ring ellipses become true circles once the board is
## tilted back.
static func board(left: float, top: float, right: float, bottom: float, cx: float, cy: float, squash: float,
		radii: Array, points: Array, colors: Array, bonus_x: float, bonus_y: float, bonus_r: float) -> PaTexture:
	var tu := BOARD_TPU
	var tv := BOARD_TPU / squash
	var w := int((right - left) * tu)
	var h := int((bottom - top) * tv)
	return TexPaint.paint_texture(w, h, 3, func(tp: TexPaint) -> void:
		tp.vgrad(0.0, 0.0, float(w), float(h), [Pal.INDIGO, Pal.NAVY])
		for x in range(0, w, 24):
			tp.rect(float(x), 0.0, 0.8, float(h), Pal.with_alpha(Pal.shade(Pal.NAVY, 0.6), 0.7))
		for y in range(0, h, 24):
			tp.rect(0.0, float(y), float(w), 0.8, Pal.with_alpha(Pal.shade(Pal.NAVY, 0.6), 0.7))
		var ux := (cx - left) * tu
		var vy := (cy - top) * tv
		# Soft light behind the rings, and shadow towards the board's top and sides (occlusion).
		tp.radial(ux, vy, w * 0.55, Pal.with_alpha(Pal.SKY, 0.16), 0)
		tp.vgrad(0.0, 0.0, float(w), h * 0.3, [0x77000000, 0])
		tp.hgrad(0.0, 0.0, w * 0.12, float(h), [0x66000000, 0])
		tp.hgrad(w * 0.88, 0.0, w * 0.12, float(h), [0, 0x66000000])
		var i := radii.size() - 1
		while i >= 0:
			var r: float = float(radii[i]) * tu
			var col: int = colors[i]
			tp.circle(ux, vy, r, Pal.shade(col, 0.32))
			# Recessed cup: shadow along the top inside edge, a lit lip along the bottom.
			tp.radial(ux, vy + 3.0, r - 3.0, Pal.shade(col, 0.55), Pal.shade(col, 0.38))
			tp.ring(ux, vy, r - 1.5, 3.0, col)
			tp.ring(ux, vy + 1.0, r - 3.5, 1.0, Pal.mix(col, Pal.WHITE, 0.45))
			i -= 1
		tp.radial(ux, vy, 9.0 * tu, Pal.BLACK, Pal.shade(Pal.NIGHT, 0.6))
		tp.ring(ux, vy, 9.0 * tu, 1.5, Pal.shade(Pal.RED, 0.6))
		for k in range(1, radii.size()):
			var side := 1.0 if k % 2 == 1 else -1.0
			var lx := ux + side * (float(radii[k - 1]) + float(radii[k])) / 2.0 * tu
			tp.label(str(points[k]), lx, vy - 6.0, 11.0, Pal.WHITE, true, false, Pal.BLACK)
		tp.label(str(points[0]), ux, vy - float(radii[0]) * tu - 20.0, 13.0, Pal.YELLOW, true, false, Pal.BLACK)
		# Bonus hole.
		var bx := (bonus_x - left) * tu
		var by := (bonus_y - top) * tv
		tp.radial(bx, by, (bonus_r + 5.0) * tu, Pal.mix(Pal.GOLD, Pal.WHITE, 0.3), Pal.GOLD)
		tp.ring(bx, by, (bonus_r + 5.0) * tu, 1.5, Pal.ORANGE)
		tp.circle(bx, by, bonus_r * tu, Pal.BLACK)
		tp.label("200", bx, by + (bonus_r + 7.0) * tu, 11.0, Pal.GOLD, true, false, Pal.BLACK))


## Inner face of a cabinet side: blue panel, stars, and a dark socket strip for the bulbs.
static func side_panel() -> PaTexture:
	if _side_panel == null:
		_side_panel = TexPaint.paint_texture(160, 140, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 160.0, 140.0, [Pal.shade(Pal.BLUE, 0.75), Pal.shade(Pal.NAVY, 0.7)])
			for i in 40:
				var x := MathUtil.hash01(i, 21) * 160.0
				var y := MathUtil.hash01(i, 22) * 140.0
				if i % 3 == 0:
					tp.star(x, y, 2.2, Pal.shade(Pal.SKY, 0.8))
				else:
					tp.circle(x, y, 0.5, Pal.SKY)
			for x in range(0, 160, 20):
				tp.rect(float(x), 0.0, 1.5, 140.0, Pal.shade(Pal.NAVY, 0.55))
			tp.rect(0.0, 0.0, 160.0, 6.0, Pal.YELLOW)
			tp.rect(0.0, 6.0, 160.0, 2.0, Pal.ORANGE))
	return _side_panel


## Lane rails: blue cabinet wood with a light cap.
static func rail_side() -> PaTexture:
	if _rail_side == null:
		_rail_side = TexPaint.paint_texture(64, 16, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 64.0, 16.0, [Pal.BLUE, Pal.shade(Pal.BLUE, 0.55)])
			tp.vgrad(0.0, 0.0, 64.0, 2.5, [Pal.SKY, Pal.with_alpha(Pal.SKY, 0.2)]))
	return _rail_side


## Along the top of each rail, the yellow edge on the lane side.
static func rail_top() -> PaTexture:
	if _rail_top == null:
		_rail_top = TexPaint.paint_texture(40, 4, 4, func(tp: TexPaint) -> void:
			tp.fill(Pal.shade(Pal.BLUE, 0.8))
			tp.rect(30.0, 0.0, 10.0, 4.0, Pal.YELLOW)
			tp.rect(29.0, 0.0, 1.0, 4.0, Pal.ORANGE))
	return _rail_top


## The raised ring walls: white, shading darker towards their base.
static func ring_wall() -> PaTexture:
	if _ring_wall == null:
		_ring_wall = TexPaint.paint_texture(4, 8, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 4.0, 8.0, [0xFFE2E2EA, 0xFF6A6A80]))
	return _ring_wall


## Marquee above the target.
static func marquee() -> PaTexture:
	if _marquee == null:
		_marquee = TexPaint.paint_texture(320, 90, 3, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 320.0, 90.0, [Pal.DARKRED, Pal.shade(Pal.PLUM, 0.8)])
			tp.rect(0.0, 0.0, 320.0, 4.0, Pal.GOLD)
			tp.rect(0.0, 86.0, 320.0, 4.0, Pal.GOLD)
			var text := "SKEE-BALL"
			for o in [3, 2, 1]:
				tp.label(text, 160.0 + o * 0.8, 24.0 + o * 1.0, 42.0, Pal.shade(Pal.DARKRED, 0.4))
			tp.glow(3.0, Pal.with_alpha(Pal.ORANGE, 0.8), func(g: TexPaint) -> void:
				g.label(text, 160.0, 24.0, 42.0, Pal.WHITE))
			tp.label(text, 160.0, 24.0, 42.0, Pal.YELLOW)
			for i in 18:
				tp.circle(9.0 + i * 17.8, 10.0, 3.2, Pal.shade(Pal.BROWN, 0.5))
				tp.circle(9.0 + i * 17.8, 80.0, 3.2, Pal.shade(Pal.BROWN, 0.5)))
	return _marquee


static func pit() -> PaTexture:
	if _pit == null:
		_pit = TexKit.solid(8, 8, Pal.shade(Pal.NIGHT, 0.6))
	return _pit


static func board_edge() -> PaTexture:
	if _board_edge == null:
		_board_edge = TexKit.solid(8, 8, Pal.shade(Pal.NAVY, 0.5))
	return _board_edge


## The ball: glossy red with a brighter band round its middle, edged in cream pinstripes.
static func ball(radius: float) -> Model:
	var tex := TexPaint.paint_texture(64, 32, 4, func(tp: TexPaint) -> void:
		tp.vgrad(0.0, 0.0, 64.0, 32.0, [Pal.mix(Pal.RED, Pal.DARKRED, 0.3), Pal.shade(Pal.DARKRED, 0.9)])
		tp.rect(0.0, 12.0, 64.0, 8.0, Pal.RED)
		tp.rect(0.0, 11.0, 64.0, 1.2, Pal.shade(Pal.CREAM, 0.9))
		tp.rect(0.0, 19.8, 64.0, 1.2, Pal.shade(Pal.CREAM, 0.9))
		tp.rect(0.0, 12.2, 64.0, 0.6, Pal.mix(Pal.RED, Pal.WHITE, 0.4)))
	return ModelBuilder.new().sphere(0.0, 0.0, 0.0, radius, tex.full(), 20, 14, 1.0, -1, 0.9).build()
