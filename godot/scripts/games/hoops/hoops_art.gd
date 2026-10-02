class_name HoopsArt
extends RefCounted
## games/hoops/HoopsArt.kt: painted art and the ball for the 3D basketball alley. Everything is
## built lazily on first use (the headless tests never draw), and the palette is dark on purpose:
## the pale surfaces the bloom would turn to white are kept for the things that should glow.

# The wood is a mid brown, so the warm lamp above the court still leaves it under the bloom
# threshold (maple at full brightness burns white).
const WOOD_DARK := 0xFF5C3A1E
const WOOD_LIGHT := 0xFF80552C
## Court paint: cream lines a little under white and a deep crimson key.
const LINE := 0xFFDCCFAE
const KEY_PAINT := 0xFFA82634
## Transparent white: soft gradients fade to this so their middles don't turn grey.
const CLEAR_WHITE := 0x00FFFFFF

## The crowd wall: 208 units span the 520 cm of wall (2.5 cm a unit), top to bottom.
const CROWD_W := 208
const CROWD_H := 208

## Size of the ad-board strip in texture units (200 by 17 for a 400 by 34 cm board).
const AD_W := 200
const AD_H := 17

static var _floor: PaTexture = null
static var _apron: PaTexture = null
static var _crowd: PaTexture = null
static var _logo_core: PaTexture = null
static var _logo_halo: PaTexture = null
static var _ad_board: PaTexture = null
static var _steel: PaTexture = null
static var _pad: PaTexture = null
static var _board_face: PaTexture = null
static var _board_marks: PaTexture = null
static var _glint: PaTexture = null
static var _rim: PaTexture = null
static var _cage_net: PaTexture = null
static var _cage_net_region: Region = null
static var _ring: PaTexture = null
static var _flare: PaTexture = null
static var _shaft: PaTexture = null


## Kotlin's TexPaint.arc extension: a stroked arc of a circle (degrees, clockwise from 3 o'clock).
static func _arc(tp: TexPaint, cx: float, cy: float, r: float, start: float, sweep: float, width: float, color: int) -> void:
	var p := tp.paint.reset()
	p.style = PaPaint.Style.STROKE
	p.stroke_width = width
	p.color = color
	tp.c_draw_arc(cx - r, cy - r, cx + r, cy + r, start, sweep, false, p)


## The alley floor: polished planks with the painted key, free-throw circle and three-point arc
## under the hoop (the top of the texture is the far end), and a mid-court roundel.
static func floor_tex() -> PaTexture:
	if _floor == null:
		_floor = TexPaint.paint_texture(100, 190, 4, func(tp: TexPaint) -> void: _paint_floor(tp))
	return _floor


static var _f32_buf := PackedFloat32Array([0.0])


## [param x] rounded to a 32-bit float: build-13 did this arithmetic in Kotlin Floats, and where a
## loop's count or an integer cut depends on the sums, the port must round alike.
static func _f32(x: float) -> float:
	_f32_buf[0] = x
	return _f32_buf[0]


static func _paint_floor(tp: TexPaint) -> void:
	for x in range(0, 100, 5):
		var tone := 0.86 + MathUtil.hash01(x, 51) * 0.28
		var col := Pal.shade(Pal.mix(WOOD_DARK, WOOD_LIGHT, MathUtil.hash01(x, 54)), tone)
		tp.hgrad(x, 0.0, 5.0, 190.0, [Pal.shade(col, 0.92), col, Pal.shade(col, 0.92)])
		tp.rect(x, 0.0, 0.35, 190.0, Pal.shade(col, 0.5))
		# The plank joints step along in Float sums (their count and hash keys depend on them).
		var y := _f32(_f32(MathUtil.hash01(x, 52)) * 40.0)
		while y < 190.0:
			tp.rect(x, y, 5.0, 0.35, Pal.shade(col, 0.55))
			y = _f32(y + _f32(45.0 + _f32(_f32(MathUtil.hash01(x + int(y), 53)) * 30.0)))
		for k in 3:
			var gx := x + 0.8 + MathUtil.hash01(x, 60 + k) * 3.4
			tp.line(gx, 0.0, gx + MathUtil.hash01(x, 64 + k) * 0.6 - 0.3, 190.0, 0.25, Pal.with_alpha(Pal.shade(col, 0.6), 0.35))
	# Painted key (towards the hoop), free-throw circle, three-point arc and roundel.
	tp.rect(36.0, 0.0, 28.0, 68.0, Pal.with_alpha(KEY_PAINT, 0.6))
	tp.rect(35.3, 0.0, 1.4, 69.0, LINE)
	tp.rect(63.3, 0.0, 1.4, 69.0, LINE)
	tp.rect(35.3, 67.6, 29.4, 1.4, LINE)
	_arc(tp, 50.0, 68.0, 14.0, 180.0, 180.0, 1.4, LINE)
	for k in 6:
		_arc(tp, 50.0, 68.0, 14.0, k * 30.0 + 4.0, 16.0, 1.4, LINE)
	_arc(tp, 50.0, 30.0, 44.0, 0.0, 180.0, 1.4, LINE)
	tp.rect(5.3, 0.0, 1.4, 30.0, LINE)
	tp.rect(93.3, 0.0, 1.4, 30.0, LINE)
	tp.ring(50.0, 112.0, 24.0, 1.6, Pal.with_alpha(LINE, 0.7))
	tp.star(50.0, 112.5, 13.0, Pal.with_alpha(KEY_PAINT, 0.75))
	# Contact shadow along the far wall and the sidelines.
	tp.vgrad(0.0, 0.0, 100.0, 24.0, [0xB0060310, 0])
	tp.hgrad(0.0, 0.0, 9.0, 190.0, [0x90060310, 0])
	tp.hgrad(91.0, 0.0, 9.0, 190.0, [0, 0x90060310])


## Dark carpet beyond the cage.
static func apron() -> PaTexture:
	if _apron == null:
		_apron = TexPaint.paint_texture(16, 16, 4, func(tp: TexPaint) -> void:
			tp.fill(0xFF130D26)
			for i in 34:
				tp.circle(MathUtil.hash01(i, 81) * 16.0, MathUtil.hash01(i, 82) * 16.0, 0.3, 0xFF20173A))
	return _apron


## The wall behind the hoop, painted as an arena: seven tiers of crowd silhouettes that grow
## smaller and darker towards the roof, rim-lit heads, raised arms and the odd phone light, under a
## haze of arena light. Dark on purpose: the hoop is the brightest thing in the room.
static func crowd() -> PaTexture:
	if _crowd == null:
		_crowd = TexPaint.paint_texture(CROWD_W, CROWD_H, 3, func(tp: TexPaint) -> void: _paint_crowd(tp))
	return _crowd


static func _paint_crowd(tp: TexPaint) -> void:
	var w := float(CROWD_W)
	var h := float(CROWD_H)
	tp.vgrad(0.0, 0.0, w, h, [0xFF040209, 0xFF0B0619, 0xFF170D30, 0xFF1D1139])
	tp.radial(w / 2.0, h * 0.36, w * 0.36, Pal.with_alpha(Pal.INDIGO, 0.3), 0)
	var phones := [0xFFDDEEFF, 0xFFFFE9A8, 0xFFFFB8E0]
	var rows := 7
	# Far rows first, so the near ones overlap them.
	for k in range(rows - 1, -1, -1):
		# Head size and spacing in Float arithmetic: how many heads fit a row depends on the sums.
		var depth := _f32(k / (rows - 1.0))
		var base_v := 192.0 - k * 13.5
		var r := _f32(_f32(1.95) - _f32(depth * _f32(0.75)))
		var step := _f32(r * 3.5)
		var body := Pal.mix(0xFF2A1A4E, 0xFF0C0718, depth)
		var skin := Pal.mix(0xFF3A2762, 0xFF120B24, depth)
		var rim_lit := Pal.mix(0xFF60419E, 0xFF2A1B52, depth)
		var i := 0
		var cx := _f32(_f32(MathUtil.hash01(k, 71)) * step)
		var end_x := _f32(w + step)
		while cx < end_x:
			var n := MathUtil.hash01(i, k, 72)
			var cy := base_v - r * 3.0 + (n - 0.5) * r * 0.8
			tp.round(cx - r * 1.35, cy + r * 1.1, r * 2.7, r * 6.0, r * 0.9, body)
			tp.circle(cx, cy, r, rim_lit)
			tp.circle(cx + r * 0.12, cy + r * 0.16, r * 0.9, skin)
			if n > 0.93:
				tp.line(cx + r, cy + r * 1.4, cx + r * 1.7 + n, cy - r * 2.6, r * 0.45, body)
			if MathUtil.hash01(i, k, 73) > 0.9:
				tp.circle(cx + r * 1.15, cy - r * 0.4, r * 0.26, phones[(i + k) % 3])
			cx = _f32(cx + _f32(step * _f32(_f32(0.9) + _f32(_f32(MathUtil.hash01(i, k, 74)) * 0.25))))
			i += 1
	tp.rect(0.0, 199.0, w, 9.0, 0xFF0A0614)


## The neon HOOP SHOT sign: a crisp tube outline and lettering, and a separate blurred halo, both
## white to be tinted.
static func logo_core() -> PaTexture:
	if _logo_core == null:
		_logo_core = TexPaint.paint_texture(100, 24, 6, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.stroke_round(1.6, 1.6, 96.8, 20.8, 5.0, 0.9, Pal.WHITE)
			tp.text("HOOP SHOT", 50.0, 16.6, 12.4, Pal.WHITE, null, TexPaint.ALIGN_CENTER, 0.05))
	return _logo_core


static func logo_halo() -> PaTexture:
	if _logo_halo == null:
		_logo_halo = TexPaint.paint_texture(100, 24, 6, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.glow(2.6, Pal.with_alpha(Pal.WHITE, 0.95), func(g: TexPaint) -> void:
				g.stroke_round(1.6, 1.6, 96.8, 20.8, 5.0, 2.2, Pal.WHITE)
				g.text("HOOP SHOT", 50.0, 16.6, 12.4, Pal.WHITE, null, TexPaint.ALIGN_CENTER, 0.05)))
	return _logo_halo


## LED ad boards along the foot of the wall: four lit cells.
static func ad_board() -> PaTexture:
	if _ad_board == null:
		_ad_board = TexPaint.paint_texture(AD_W, AD_H, 5, func(tp: TexPaint) -> void: _paint_ad_board(tp))
	return _ad_board


static func _paint_ad_board(tp: TexPaint) -> void:
	tp.fill(0xFF07050D)
	var cells := ["HOOP SHOT", "SWISH +3", "x5 ON FIRE", "ARCADE"]
	var colors := [Pal.ORANGE, Pal.CYAN, Pal.YELLOW, Pal.PINK]
	for i in 4:
		var x := i * 50.0
		var cell: String = cells[i]
		var color: int = colors[i]
		tp.stroke_round(x + 2.0, 1.6, 46.0, 13.8, 2.5, 0.7, Pal.shade(color, 0.55))
		tp.glow(1.2, Pal.with_alpha(color, 0.8), func(g: TexPaint) -> void:
			g.text(cell, x + 25.0, 11.2, 7.4, Pal.WHITE, Fonts.heavy()))
		tp.text(cell, x + 25.0, 11.2, 7.4, Pal.mix(color, Pal.WHITE, 0.35), Fonts.heavy())


## Gunmetal for posts, the backboard frame and brackets, with a thin highlight down one side.
static func steel() -> PaTexture:
	if _steel == null:
		_steel = TexPaint.paint_texture(8, 16, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 8.0, 16.0, [0xFF565C74, 0xFF262A3C])
			tp.rect(1.4, 0.0, 1.2, 16.0, Pal.with_alpha(Pal.WHITE, 0.22)))
	return _steel


## Padding round the foot of the stanchion: dark red with an orange piping (painted by build-13
## too, though no part of the scene uses it).
static func pad() -> PaTexture:
	if _pad == null:
		_pad = TexPaint.paint_texture(16, 32, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 16.0, 32.0, [Pal.shade(Pal.RED, 0.62), Pal.shade(Pal.DARKRED, 0.5)])
			tp.rect(0.0, 0.0, 16.0, 1.6, Pal.shade(Pal.ORANGE, 0.8))
			for y in range(8, 32, 8):
				tp.rect(0.0, y, 16.0, 0.5, Pal.with_alpha(Pal.BLACK, 0.4)))
	return _pad


## The backboard: midnight glass with an LED matrix, dim markings (they light up through
## [method board_marks]) and a faint sheen.
static func board_face() -> PaTexture:
	if _board_face == null:
		_board_face = TexPaint.paint_texture(120, 85, 4, func(tp: TexPaint) -> void: _paint_board_face(tp))
	return _board_face


static func _paint_board_face(tp: TexPaint) -> void:
	# Blue kept low (the lamp and spot add to it) so the glass stays under the bloom threshold.
	tp.vgrad(0.0, 0.0, 120.0, 85.0, [0xFF162456, 0xFF0A102C])
	for x in range(0, 120, 3):
		tp.rect(x, 0.0, 0.25, 85.0, Pal.with_alpha(Pal.WHITE, 0.035))
	for y in range(2, 85, 4):
		for x in range(2, 120, 4):
			tp.circle(x, y, 0.42, Pal.with_alpha(Pal.SKY, 0.16))
	var dim := Pal.shade(Pal.RED, 0.5)
	tp.stroke_round(3.0, 3.0, 114.0, 79.0, 3.0, 1.6, dim)
	tp.stroke_round(42.0, 44.0, 36.0, 26.0, 1.0, 1.8, dim)
	tp.polygon(PackedFloat32Array([6.0, 6.0, 30.0, 6.0, 10.0, 40.0, 6.0, 40.0]), 0x14FFFFFF)
	tp.polygon(PackedFloat32Array([36.0, 6.0, 46.0, 6.0, 26.0, 40.0, 16.0, 40.0]), 0x0EFFFFFF)


## The same markings as [method board_face] in white with a halo, drawn additively in the game's colour.
static func board_marks() -> PaTexture:
	if _board_marks == null:
		_board_marks = TexPaint.paint_texture(120, 85, 4, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.glow(2.2, Pal.with_alpha(Pal.WHITE, 0.85), func(g: TexPaint) -> void:
				g.stroke_round(3.0, 3.0, 114.0, 79.0, 3.0, 1.6, Pal.WHITE)
				g.stroke_round(42.0, 44.0, 36.0, 26.0, 1.0, 1.8, Pal.WHITE))
			tp.stroke_round(3.0, 3.0, 114.0, 79.0, 3.0, 1.2, Pal.WHITE)
			tp.stroke_round(42.0, 44.0, 36.0, 26.0, 1.0, 1.4, Pal.WHITE))
	return _board_marks


## A soft slanted band that sweeps across the glass now and then.
static func glint() -> PaTexture:
	if _glint == null:
		_glint = TexPaint.paint_texture(32, 8, 4, func(tp: TexPaint) -> void:
			tp.hgrad(0.0, 0.0, 32.0, 8.0, [CLEAR_WHITE, Pal.with_alpha(Pal.WHITE, 0.55), CLEAR_WHITE]))
	return _glint


## The rim: enamelled orange, brighter on top.
static func rim() -> PaTexture:
	if _rim == null:
		_rim = TexPaint.paint_texture(32, 8, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 32.0, 8.0, [Pal.mix(Pal.ORANGE, Pal.YELLOW, 0.3), Pal.ORANGE, Pal.shade(Pal.RED, 0.75)]))
	return _rim


## Diamond mesh for the cage nets: tiles seamlessly (a wrapping region), alpha-blended.
static func cage_net() -> PaTexture:
	if _cage_net == null:
		_cage_net = TexPaint.paint_texture(16, 16, 8, func(tp: TexPaint) -> void:
			tp.clear(0)
			var c := Pal.with_alpha(0xFF9CB4E8, 0.55)
			tp.line(-2.0, -2.0, 18.0, 18.0, 0.8, c, false)
			tp.line(-2.0, 18.0, 18.0, -2.0, 0.8, c, false))
	return _cage_net


static func cage_net_region() -> Region:
	if _cage_net_region == null:
		_cage_net_region = cage_net().region(0, 0, -1, -1, true)
	return _cage_net_region


## A thin ring, soft either side, for shockwaves.
static func ring() -> PaTexture:
	if _ring == null:
		_ring = TexPaint.paint_texture(32, 32, 4, func(tp: TexPaint) -> void:
			tp.clear(0)
			var p := tp.paint.reset()
			# Android ignores a shaded paint's RGB (only its alpha, 255 after reset, counts); the
			# port's painter modulates the gradient by the whole colour, so it is white here.
			p.color = Pal.WHITE
			p.shader = PaBrush.radial([CLEAR_WHITE, CLEAR_WHITE, Pal.WHITE, CLEAR_WHITE], Vector2(16.0, 16.0), 15.5, [0.0, 0.72, 0.88, 1.0])
			tp.c_draw_circle(16.0, 16.0, 15.5, p)
			p.shader = null)
	return _ring


## A four-point glint with a hot core, for swishes and camera flashes.
static func flare() -> PaTexture:
	if _flare == null:
		_flare = TexPaint.paint_texture(32, 32, 4, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.radial(16.0, 16.0, 14.0, Pal.with_alpha(Pal.WHITE, 0.8), 0)
			tp.oval(16.0, 16.0, 15.5, 0.8, Pal.with_alpha(Pal.WHITE, 0.9))
			tp.oval(16.0, 16.0, 0.8, 15.5, Pal.with_alpha(Pal.WHITE, 0.9))
			tp.circle(16.0, 16.0, 2.2, Pal.WHITE))
	return _flare


## A beam of light: brightest at its start (the top of the texture), a soft bell across, fading to nothing.
static func shaft() -> PaTexture:
	if _shaft == null:
		_shaft = TexPaint.paint_texture(16, 64, 2, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.hgrad(0.0, 0.0, 16.0, 64.0, [CLEAR_WHITE, Pal.WHITE, CLEAR_WHITE])
			var p := tp.paint.reset()
			p.xfer = PaPaint.Xfer.DST_IN
			p.shader = PaBrush.linear([Pal.WHITE, CLEAR_WHITE], Vector2(0.0, 0.0), Vector2(0.0, 64.0))
			tp.c_draw_rect(0.0, 0.0, 16.0, 64.0, p)
			p.shader = null
			p.xfer = PaPaint.Xfer.NONE)
	return _shaft


## A small LED readout for the back wall: a caption over big digits, repainted only when the value
## changes. Its texture is opaque, so it draws as a solid, glowing face.
class Readout:
	extends RefCounted
	const W := 64
	const H := 34
	const SCALE := 4

	var _caption: String
	var _accent: int
	var _painter: TexPaint = null
	var _tex: PaTexture = null
	var _last_key := -0x80000000

	func _init(caption: String, accent: int) -> void:
		_caption = caption
		_accent = accent

	func tex() -> PaTexture:
		if _tex == null:
			_tex = PaTexture.new(W, H, null, SCALE)
		return _tex

	func stale(key: int) -> bool:
		return key != _last_key

	## Paints [param text] in [param color] under the caption; [param key] identifies what was painted.
	func paint(key: int, text: String, color: int) -> void:
		_last_key = key
		if _painter == null:
			_painter = TexPaint.new(W * SCALE, H * SCALE)
			_painter.use_units(SCALE)
		var tp := _painter
		# Each repaint starts a fresh recording (build-13 painted over the old picture: its first
		# gradient is opaque and covers everything, so the result is the same).
		tp.clear(0)
		tp.vgrad(0.0, 0.0, W, H, [0xFF130D26, 0xFF06040C])
		for y in range(0, H, 2):
			tp.rect(0.0, y, W, 0.5, Pal.with_alpha(Pal.NIGHT, 0.5))
		tp.stroke_round(0.6, 0.6, W - 1.2, H - 1.2, 3.0, 1.2, Pal.shade(_accent, 0.7))
		tp.label(_caption, W / 2.0, 3.2, 5.0, Pal.LAVENDER, true, true)
		tp.glow(1.4, Pal.with_alpha(color, 0.8), func(g: TexPaint) -> void: g.label(text, W / 2.0, 11.5, 16.0, Pal.WHITE))
		tp.label(text, W / 2.0, 11.5, 16.0, color)
		tp.update(tex())


## Builds the basketball: pebbled orange leather with black seams.
static func ball(radius: float) -> Model:
	var tex := TexPaint.paint_texture(128, 64, 4, func(tp: TexPaint) -> void: _paint_ball(tp))
	return ModelBuilder.new().sphere(0.0, 0.0, 0.0, radius, tex.full(), 22, 14, 1.0, -1, 0.4).build()


static func _paint_ball(tp: TexPaint) -> void:
	tp.vgrad(0.0, 0.0, 128.0, 64.0, [Pal.mix(Pal.ORANGE, Pal.YELLOW, 0.12), Pal.shade(Pal.ORANGE, 0.78)])
	var dark_c := Pal.with_alpha(Pal.shade(Pal.ORANGE, 0.62), 0.55)
	var light_c := Pal.with_alpha(Pal.mix(Pal.ORANGE, Pal.YELLOW, 0.5), 0.55)
	for i in 1200:
		var dark := MathUtil.hash01(i, 63) > 0.4
		tp.circle(MathUtil.hash01(i, 61) * 128.0, MathUtil.hash01(i, 62) * 64.0, 0.35, dark_c if dark else light_c)
	var seam := 0xFF1E0E06
	tp.rect(0.0, 31.2, 128.0, 1.6, seam)
	tp.rect(0.0, 0.0, 1.6, 64.0, seam)
	tp.rect(63.2, 0.0, 1.6, 64.0, seam)
	# The two curved seams, bowing round the ball.
	for centre: float in [32.0, 96.0]:
		var prev_x := 0.0
		var prev_y := 0.0
		for k in 33:
			var v := k / 32.0
			var x := centre + (1.0 if centre < 64.0 else -1.0) * 14.0 * sin(v * PI)
			var y := v * 64.0
			if k > 0:
				tp.line(prev_x, prev_y, x, y, 1.6, seam)
			prev_x = x
			prev_y = y
