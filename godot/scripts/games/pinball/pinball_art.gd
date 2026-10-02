class_name PinballArt
extends RefCounted
## games/pinball/PinballArt.kt: painted textures and models for the pinball table and its hall
## cabinet. Everything is built lazily on first use (Kotlin's `by lazy`; here a function per
## texture or model). World units are table units: x across, z down the table, y up from the
## playfield.

const T := preload("res://scripts/games/pinball/pinball_table.gd")

## Texels per table unit on the playfield.
const TPU := 1.28
## The backbox's front face, behind the top of the table.
const BACKBOX_Z := -18.0
const BACKBOX_TOP := 214.0

## The backglass: a 308 x 196 window in the backbox front, one texel per world unit.
const GLASS_X0 := -4.0
const GLASS_X1 := 304.0
const GLASS_Y0 := 8.0
const GLASS_Y1 := 204.0

## The dot-matrix display's window in the backglass (world units). It keeps the display's 4:1 shape,
## so the dots come out square.
const DMD_X0 := 34.0
const DMD_X1 := 266.0
const DMD_BOTTOM := 26.0
const DMD_TOP := DMD_BOTTOM + (DMD_X1 - DMD_X0) * PinballDisplay.DotMatrix.ROWS / PinballDisplay.DotMatrix.COLS

const RAIL_H := 22.0
const GUIDE_H := 10.0
const RUBBER_H := 9.0

## Half-width of the steel cap along the top of each wall, per style (world units).
const RAIL_CAP := 1.7
const GUIDE_CAP := 1.0
const RUBBER_CAP := 0.9

## Width of the floor light strip along the outer rails, and how far in from the wall it sits.
const STRIP_W := 7.0
const STRIP_IN := 4.5

## How much of the apron, from its front edge, carries the printed art: the player's view ends
## about 50 units down it, so the cards and lettering all sit inside this strip.
const APRON_PRINT := 40.0

static var _playfield: PaTexture = null
static var _apron: PaTexture = null
static var _apron_back: PaTexture = null
static var _backglass: PaTexture = null
static var _rail: PaTexture = null
static var _metal: PaTexture = null
static var _steel: PaTexture = null
static var _rubber: PaTexture = null
static var _post_top: PaTexture = null
static var _sling_plastic: PaTexture = null
static var _dark_metal: PaTexture = null
static var _cabinet_side: PaTexture = null
static var _spinner_plate: PaTexture = null
static var _glass_streaks: PaTexture = null
static var _chrome: PaTexture = null
static var _flipper_body: PaTexture = null
static var _flipper_rubber: PaTexture = null
static var _bumper_skirt: PaTexture = null
static var _bumper_top: PaTexture = null
static var _target_face: PaTexture = null
static var _ring_glow: PaTexture = null
static var _strip_glow: PaTexture = null
static var _rays: PaTexture = null
static var _shimmer: PaTexture = null
static var _cabinet_playfield: PaTexture = null

static var _table: Model = null
static var _rail_glow: Model = null
static var _bumper_base: Model = null
static var _bumper_cap: Model = null
static var _drop_target: Model = null
static var _flipper: Model = null
static var _plunger: Model = null
static var _ball: Model = null
static var _cabinet_ball: Model = null


# ---------------------------------------------------------------- painting helpers (Kotlin's TexPaint extensions)

static func _stars(tp: TexPaint, w: float, h: float, n: int, seed_value: int, color: int) -> void:
	for i in n:
		var x := MathUtil.hash01(i, seed_value) * w
		var y := MathUtil.hash01(i, seed_value + 1) * h
		var s := 0.5 + MathUtil.hash01(i, seed_value + 2) * 1.6
		if i % 5 == 0:
			tp.star(x, y, s * 2.2, color)
		else:
			tp.circle(x, y, s * 0.5, Pal.with_alpha(color, 0.5 + MathUtil.hash01(i, seed_value + 3) * 0.5))


## An arrow insert pointing along [param angle] (radians, y down), painted unlit.
static func _arrow(tp: TexPaint, cx: float, cy: float, size: float, angle: float, color: int) -> void:
	var c := cos(angle)
	var s := sin(angle)
	var us := [size, -size * 0.4, -size * 0.1, -size * 0.4]
	var vs := [0.0, size * 0.7, 0.0, -size * 0.7]
	var pts := PackedFloat32Array()
	for i in 4:
		var u: float = us[i]
		var v: float = vs[i]
		pts.append(cx + c * u - s * v)
		pts.append(cy + s * u + c * v)
	# A dark keyline under the insert so it reads against the busy sunburst.
	var edge := PackedFloat32Array()
	edge.resize(pts.size())
	var i := 0
	while i < pts.size():
		edge[i] = cx + (pts[i] - cx) * 1.22
		edge[i + 1] = cy + (pts[i + 1] - cy) * 1.22
		i += 2
	tp.polygon(edge, 0x66000000)
	tp.polygon(pts, Pal.shade(color, 0.35))
	var inner := PackedFloat32Array()
	inner.resize(pts.size())
	i = 0
	while i < pts.size():
		inner[i] = cx + (pts[i] - cx) * 0.7
		inner[i + 1] = cy + (pts[i + 1] - cy) * 0.7
		i += 2
	tp.polygon(inner, Pal.shade(color, 0.55))


## A round insert with a ring, unlit.
static func _insert(tp: TexPaint, cx: float, cy: float, r: float, color: int) -> void:
	tp.circle(cx, cy, r + 2.6, 0x55000000)
	tp.circle(cx, cy, r + 1.5, Pal.shade(color, 0.2))
	tp.radial(cx, cy, r, Pal.shade(color, 0.6), Pal.shade(color, 0.3))
	tp.ring(cx, cy, r, 1.0, Pal.with_alpha(Pal.WHITE, 0.35))


## A stroked arc of an ellipse, for planet rings; angles in degrees, clockwise from +x.
static func _arc(tp: TexPaint, cx: float, cy: float, rx: float, ry: float, from: float, sweep: float, width: float, color: int) -> void:
	var p := tp.paint.reset()
	p.style = PaPaint.Style.STROKE
	p.stroke_width = width
	p.color = color
	tp.c_draw_arc(cx - rx, cy - ry, cx + rx, cy + ry, from, sweep, false, p)


## A soft shadow along a wall: three strokes of falling strength stand in for a blur.
static func _wall_shadow(tp: TexPaint, x0: float, y0: float, x1: float, y1: float, k: float, reach: float) -> void:
	tp.line(x0 * k, y0 * k, x1 * k, y1 * k, reach * k, 0x16000000)
	tp.line(x0 * k, y0 * k, x1 * k, y1 * k, reach * 0.62 * k, 0x22000000)
	tp.line(x0 * k, y0 * k, x1 * k, y1 * k, reach * 0.3 * k, 0x38000000)


# ---------------------------------------------------------------- painted textures

## The playfield: a neon starfield with its inserts, lanes and markings.
static func playfield() -> PaTexture:
	if _playfield == null:
		_playfield = TexPaint.paint_texture(384, 820, 2, _paint_playfield)
	return _playfield


static func _paint_playfield(tp: TexPaint) -> void:
	var k := TPU
	var w := 384.0
	var h := 820.0
	tp.vgrad(0.0, 0.0, w, h, [0xFF0A0B30, 0xFF1B1150, 0xFF2A0E47, 0xFF140828])
	# A sunburst behind the pop bumpers.
	var sx := T.CX * k
	var sy := 175.0 * k
	for i in 24:
		var a0 := i * 2.0 * PI / 24.0
		var a1 := a0 + PI / 24.0
		tp.polygon(PackedFloat32Array([sx, sy, sx + cos(a0) * 420.0, sy + sin(a0) * 420.0, sx + cos(a1) * 420.0, sy + sin(a1) * 420.0]),
			0x1CFF3FA4 if i % 2 == 0 else 0x143DF5FF)
	tp.radial(sx, sy, 150.0, 0x55FF77C8, 0)
	_stars(tp, w, h, 170, 5, Pal.LAVENDER)
	# A comet crossing the lower table, behind the name: a tapering tail and a bright head.
	for i in 6:
		var t0 := i / 6.0
		tp.line(
			(T.CX + 88.0 - t0 * 118.0) * k, (388.0 + t0 * 26.0) * k, (T.CX + 88.0 - (t0 + 0.17) * 118.0) * k, (388.0 + (t0 + 0.17) * 26.0) * k,
			(5.5 - i * 0.8) * k, Pal.with_alpha(Pal.CYAN, 0.13 - i * 0.018))
	tp.radial((T.CX + 88.0) * k, 388.0 * k, 9.0 * k, 0x88FFFFFF, 0)
	# Lower-table swoosh and the machine's name.
	tp.radial(T.CX * k, 440.0 * k, 120.0, 0x40FF3FA4, 0)
	tp.glow(4.0, Pal.with_alpha(Pal.HOTPINK, 0.9), func(g: TexPaint) -> void: g.label("STAR", T.CX * TPU, 418.0 * TPU, 20.0, -1))
	tp.label("STAR", T.CX * k, 418.0 * k, 20.0, Pal.CREAM)
	tp.glow(4.0, Pal.with_alpha(Pal.CYAN, 0.9), func(g: TexPaint) -> void: g.label("FLIPPER", T.CX * TPU, 446.0 * TPU, 16.0, -1))
	tp.label("FLIPPER", T.CX * k, 446.0 * k, 16.0, Pal.CREAM)
	# The left orbit channel and the shooter lane.
	tp.vgrad(0.0, T.ORBIT_Y0 * k, T.ORBIT_X * k, (T.ORBIT_Y1 - T.ORBIT_Y0) * k, [0xFF101238, 0xFF1A0E3C])
	for i in 6:
		_arrow(tp, T.ORBIT_X * k / 2.0, (300.0 - i * 30.0) * k, 7.0, -PI / 2.0, Pal.CYAN)
	tp.vgrad(T.PLAY_W * k, 0.0, (T.W - T.PLAY_W) * k, h, [0xFF15122A, 0xFF0C0A1A])
	for i in 5:
		_arrow(tp, T.LANE_X * k, (520.0 - i * 60.0) * k, 7.0, -PI / 2.0, Pal.YELLOW)
	# Contact shadows where the walls, guides and posts meet the playfield: ambient occlusion
	# painted into the floor, so every rail and pin sits in the table.
	for i in T.seg_count:
		var st := T.style[i]
		var reach := 15.0 if st == T.STYLE_RAIL else (9.0 if st == T.STYLE_GUIDE else 6.0)
		_wall_shadow(tp, T.ax[i], T.ay[i], T.bx[i], T.by[i], k, reach)
	for i in T.post_count:
		tp.circle(T.px[i] * k, T.py[i] * k, (T.pr[i] + 4.5) * k, 0x1E000000)
		tp.circle(T.px[i] * k, T.py[i] * k, (T.pr[i] + 2.5) * k, 0x30000000)
	# Top lanes: arrows below each rollover, lane names above.
	for i in 3:
		_arrow(tp, T.ROLLOVER_X[i] * k, (T.ROLLOVER_Y + 34.0) * k, 11.0, PI / 2.0, Pal.SKY)
		tp.rect((T.ROLLOVER_X[i] - 5.0) * k, (T.ROLLOVER_Y - 1.0) * k, 10.0 * k, 2.0 * k, Pal.LIGHTGRAY)
	tp.label("S", T.ROLLOVER_X[0] * k, 20.0 * k, 9.0, Pal.SKY)
	tp.label("T", T.ROLLOVER_X[1] * k, 20.0 * k, 9.0, Pal.SKY)
	tp.label("R", T.ROLLOVER_X[2] * k, 20.0 * k, 9.0, Pal.SKY)
	# Bumper footprints.
	for i in 3:
		tp.circle(T.BUMPER_X[i] * k, T.BUMPER_Y[i] * k, (T.BUMPER_R + 8.0) * k, 0x30000000)
		tp.circle(T.BUMPER_X[i] * k, T.BUMPER_Y[i] * k, (T.BUMPER_R + 5.0) * k, 0x66000000)
		tp.ring(T.BUMPER_X[i] * k, T.BUMPER_Y[i] * k, (T.BUMPER_R + 3.0) * k, 1.5, Pal.with_alpha(Pal.YELLOW, 0.6))
	# Multiplier row, the orbit/jackpot arrow, drop target lamps and shoot-again.
	var mult := ["2X", "3X", "4X", "5X"]
	for i in 4:
		var x := (T.CX - 54.0 + i * 36.0) * k
		_insert(tp, x, 372.0 * k, 11.0, Pal.GOLD)
		tp.label(mult[i], x, 372.0 * k - 5.0, 8.0, Pal.CREAM, true, false, Pal.BLACK)
	tp.label("MULTIPLIER", T.CX * k, 390.0 * k, 7.0, Pal.GOLD)
	_arrow(tp, 22.0 * k, 350.0 * k, 16.0, -PI / 2.0 - 0.35, Pal.GOLD)
	tp.label("JACKPOT", 26.0 * k, 372.0 * k, 5.5, Pal.GOLD)
	for i in 3:
		_insert(tp, (T.DROP_X - 22.0) * k, (T.DROP_Y0[i] + T.DROP_LEN / 2.0) * k, 6.0, Pal.ORANGE)
	_insert(tp, T.CX * k, 568.0 * k, 12.0, Pal.RED)
	tp.label("SHOOT", T.CX * k, 560.0 * k, 5.5, Pal.CREAM, true, false, Pal.BLACK)
	tp.label("AGAIN", T.CX * k, 568.0 * k, 5.5, Pal.CREAM, true, false, Pal.BLACK)
	# Inlane and outlane arrows.
	for side in 2:
		var ox := 11.0 if side == 0 else T.PLAY_W - 11.0
		var ix := 36.0 if side == 0 else T.PLAY_W - 36.0
		_arrow(tp, ox * k, 430.0 * k, 7.0, PI / 2.0, Pal.RED)
		_arrow(tp, ix * k, 440.0 * k, 7.0, PI / 2.0, Pal.LIME)
	# Flipper zone shading, and a soft pool of shadow under where the flippers work.
	tp.vgrad(0.0, 480.0 * k, T.PLAY_W * k, 110.0 * k, [0, 0x66000000])
	tp.grain(0.04, 17)


## The apron over the drain: instruction cards either side of the lit lettering.
static func apron() -> PaTexture:
	if _apron == null:
		_apron = TexPaint.paint_texture(272, int(APRON_PRINT), 2, func(tp: TexPaint) -> void:
			var h := APRON_PRINT
			tp.vgrad(0.0, 0.0, 272.0, h, [0xFF2B1A5C, 0xFF140C30])
			tp.rect(0.0, 0.0, 272.0, 2.0, Pal.CYAN)
			tp.round_grad(10.0, 7.0, 80.0, 27.0, 4.0, Pal.CREAM, Pal.TAN)
			tp.label("3 BALLS", 50.0, 11.0, 6.0, Pal.DARKBROWN)
			tp.label("LANES=MULTI", 50.0, 22.0, 5.0, Pal.DARKBROWN)
			tp.round_grad(182.0, 7.0, 80.0, 27.0, 4.0, Pal.CREAM, Pal.TAN)
			tp.label("BANK=MULT", 222.0, 11.0, 5.0, Pal.DARKBROWN)
			tp.label("ORBIT=JACKPOT", 222.0, 22.0, 4.5, Pal.DARKBROWN)
			tp.glow(3.0, Pal.with_alpha(Pal.HOTPINK, 0.8), func(g: TexPaint) -> void: g.label("FLIP", 136.0, 9.0, 14.0, -1))
			tp.label("FLIP", 136.0, 9.0, 14.0, Pal.CREAM)
			tp.rect(96.0, 29.0, 80.0, 1.5, Pal.with_alpha(Pal.CYAN, 0.7)))
	return _apron


## The rest of the apron, down to the front of the cabinet: plain moulded plastic.
static func _apron_back_tex() -> PaTexture:
	if _apron_back == null:
		_apron_back = TexPaint.paint_texture(32, 32, 2, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 32.0, 32.0, [0xFF140C30, 0xFF0C0820]))
	return _apron_back


## Backbox front: the backglass art above the display. It is 308 x 196 texels for the GLASS_X0..X1
## by GLASS_Y0..Y1 window, so a texel is a world unit: a space horizon with a ringed planet and a
## moon, a neon city skyline, the extruded title, and the display's bezel with lamp columns either
## side. Kept dark and saturated behind the lettering, so the glass glows in colour rather than
## blowing out to white.
static func backglass() -> PaTexture:
	if _backglass == null:
		_backglass = TexPaint.paint_texture(308, 196, 2, _paint_backglass)
	return _backglass


static func _paint_backglass(tp: TexPaint) -> void:
	var w := 308.0
	var h := 196.0
	tp.vgrad(0.0, 0.0, w, h, [0xFF0A0520, 0xFF1E0E48, 0xFF4C1668, 0xFF2A0C46, 0xFF0E0620])
	tp.radial(w / 2.0, 100.0, 170.0, 0x40FF3FA4, 0)
	_stars(tp, w, 96.0, 55, 9, Pal.CREAM)
	# The moon, low and left, with a few craters.
	tp.circle(52.0, 46.0, 12.0, 0xFFB9A8E8)
	tp.radial(48.0, 42.0, 12.0, 0x77FFFFFF, 0)
	tp.circle(56.0, 50.0, 3.0, 0x33403060)
	tp.circle(46.0, 51.0, 2.0, 0x33403060)
	# A ringed planet: back half of the ring, the ball, then the front half.
	tp.save()
	tp.rotate(-14.0, 246.0, 58.0)
	_arc(tp, 246.0, 58.0, 56.0, 12.0, 180.0, 180.0, 3.4, 0x77B896FF)
	tp.restore()
	tp.ball(246.0, 58.0, 30.0, 0xFF5A32C2, 0.35)
	tp.radial(238.0, 48.0, 24.0, 0x30FFB0FF, 0)
	tp.save()
	tp.rotate(-14.0, 246.0, 58.0)
	_arc(tp, 246.0, 58.0, 56.0, 12.0, 0.0, 180.0, 4.2, 0xCCD8B8FF)
	_arc(tp, 246.0, 58.0, 47.0, 9.0, 0.0, 180.0, 1.6, 0x88FFA0E0)
	tp.restore()
	# A neon city skyline along the horizon, with lit windows and a glowing kerb.
	for i in 31:
		var bx := i * 10.0
		var bh := 5.0 + MathUtil.hash01(i, 3) * 12.0
		tp.rect(bx, 108.0 - bh, 9.0, bh, 0xFF0A0418)
		var wy := 108.0 - bh + 2.0
		while wy < 106.0:
			if MathUtil.hash01(i, int(wy) + 40) > 0.5:
				tp.rect(bx + 1.6, wy, 1.4, 1.4, 0xFFFFC85A if MathUtil.hash01(i, int(wy)) > 0.5 else 0xFF5AE8FF)
			if MathUtil.hash01(i + 90, int(wy) + 40) > 0.55:
				tp.rect(bx + 5.4, wy, 1.4, 1.4, 0xFFFF7ACC)
			wy += 3.6
	tp.vgrad(0.0, 96.0, w, 12.0, [0, 0x66FF3FA4])
	tp.rect(0.0, 107.5, w, 2.0, Pal.with_alpha(Pal.HOTPINK, 0.9))
	# The title, extruded: dark copies stepped down and right, then the face on top.
	var d := 4
	while d >= 1:
		tp.label("STAR", 154.0 + d * 0.8, 4.0 + d * 1.1, 40.0, Pal.shade(Pal.PINK, 0.32 + 0.05 * (4 - d)))
		tp.label("FLIPPER", 154.0 + d * 0.6, 50.0 + d * 0.9, 28.0, Pal.shade(Pal.CYAN, 0.26 + 0.05 * (4 - d)))
		d -= 1
	tp.glow(7.0, Pal.with_alpha(Pal.HOTPINK, 0.85), func(g: TexPaint) -> void: g.label("STAR", 154.0, 4.0, 40.0, -1))
	tp.label("STAR", 154.0, 4.0, 40.0, Pal.YELLOW, true, false, Pal.BLACK)
	tp.glow(6.0, Pal.with_alpha(Pal.CYAN, 0.85), func(g: TexPaint) -> void: g.label("FLIPPER", 154.0, 50.0, 28.0, -1))
	tp.label("FLIPPER", 154.0, 50.0, 28.0, Pal.CREAM, true, false, Pal.BLACK)
	tp.label("SPACE PINBALL", 154.0, 84.0, 5.5, Pal.LAVENDER, true, true)
	# The display's bezel: a chamfered dark frame, a bright top edge, four screws.
	tp.round_grad(30.0, 112.0, 248.0, 74.0, 7.0, 0xFF44444F, 0xFF0C0C12)
	tp.round(33.0, 115.0, 242.0, 68.0, 5.0, 0xFF06060A)
	tp.rect(38.0, 120.0, 232.0, 58.0, 0xFF120600)
	tp.line(36.0, 114.4, 272.0, 114.4, 1.0, 0x66FFFFFF)
	for sxv: float in [36.0, 272.0]:
		for syv: float in [118.0, 180.0]:
			tp.circle(sxv, syv, 2.3, 0xFF8E8EA6)
			tp.line(sxv - 1.4, syv - 0.6, sxv + 1.4, syv + 0.6, 0.6, 0xFF30303C)
	# Lamp columns either side of the display.
	for i in 5:
		var cy := 122.0 + i * 14.0
		tp.circle(15.0, cy, 3.6, Pal.shade(Pal.PINK if i % 2 == 0 else Pal.CYAN, 0.55))
		tp.circle(293.0, cy, 3.6, Pal.shade(Pal.CYAN if i % 2 == 0 else Pal.PINK, 0.55))
		tp.ring(15.0, cy, 3.6, 0.8, 0x66FFFFFF)
		tp.ring(293.0, cy, 3.6, 0.8, 0x66FFFFFF)


static func rail() -> PaTexture:
	if _rail == null:
		_rail = TexPaint.paint_texture(64, 22, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 64.0, 22.0, [0xFF6B4FB0, 0xFF2B1A5C, 0xFF1E1042])
			tp.rect(0.0, 0.0, 64.0, 2.0, Pal.LAVENDER)
			tp.rect(0.0, 20.0, 64.0, 2.0, Pal.shade(Pal.NIGHT, 0.8)))
	return _rail


static func metal() -> PaTexture:
	if _metal == null:
		_metal = TexPaint.paint_texture(32, 10, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 32.0, 10.0, [0xFFDCDCEE, 0xFF9A9AB8, 0xFF5A5A70]))
	return _metal


## Brushed steel for rail caps and frames: a touch darker than the ball's chrome so it never blows out.
static func _steel_tex() -> PaTexture:
	if _steel == null:
		_steel = TexPaint.paint_texture(32, 16, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 32.0, 16.0, [0xFFCFD2E6, 0xFF7C809C, 0xFFA6AAC6, 0xFF3E4058])
			tp.rect(0.0, 5.0, 32.0, 1.0, 0x55FFFFFF))
	return _steel


static func rubber() -> PaTexture:
	if _rubber == null:
		_rubber = TexPaint.paint_texture(16, 10, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 16.0, 10.0, [0xFFF0F0F8, 0xFFD0D0DE, 0xFF9494A8]))
	return _rubber


static func _post_top_tex() -> PaTexture:
	if _post_top == null:
		_post_top = TexPaint.paint_texture(16, 16, 4, func(tp: TexPaint) -> void:
			tp.fill(Pal.RED)
			tp.radial(6.0, 6.0, 8.0, 0xFFFF9090, Pal.RED))
	return _post_top


static func _sling_plastic_tex() -> PaTexture:
	if _sling_plastic == null:
		_sling_plastic = TexPaint.paint_texture(32, 32, 4, func(tp: TexPaint) -> void:
			tp.fill(0xFF1E7A4E)
			tp.radial(16.0, 16.0, 18.0, Pal.LIME, 0xFF1E7A4E)
			tp.star(16.0, 16.0, 7.0, Pal.with_alpha(Pal.WHITE, 0.7)))
	return _sling_plastic


static func _dark_metal_tex() -> PaTexture:
	if _dark_metal == null:
		_dark_metal = TexPaint.paint_texture(16, 16, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 16.0, 16.0, [0xFF3A3A48, 0xFF16161E]))
	return _dark_metal


## The cabinet's side art, seen inside the table: a deep violet that darkens towards the floor, with
## a pink and a cyan racing stripe running the table's length (the face is stretched along it, so the
## stripes are horizontal lines here).
static func _cabinet_side_tex() -> PaTexture:
	if _cabinet_side == null:
		_cabinet_side = TexPaint.paint_texture(64, 64, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 64.0, 64.0, [0xFF5A34A0, 0xFF3A2072, 0xFF1A0E3A])
			tp.rect(0.0, 22.0, 64.0, 2.2, Pal.shade(Pal.PINK, 0.75))
			tp.rect(0.0, 27.0, 64.0, 1.2, Pal.shade(Pal.CYAN, 0.7))
			PinballArt._stars(tp, 64.0, 20.0, 8, 3, Pal.LAVENDER))
	return _cabinet_side


static func spinner_plate() -> PaTexture:
	if _spinner_plate == null:
		_spinner_plate = TexPaint.paint_texture(40, 16, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 40.0, 16.0, [0xFFF4F4FF, 0xFFA8A8C0])
			tp.star(20.0, 8.0, 6.0, Pal.RED))
	return _spinner_plate


## Faint diagonal reflections on the glass, for additive blending (black is invisible).
static func glass_streaks() -> PaTexture:
	if _glass_streaks == null:
		_glass_streaks = TexPaint.paint_texture(64, 128, 4, func(tp: TexPaint) -> void:
			tp.fill(0xFF000000)
			tp.line(-10.0, 90.0, 60.0, 10.0, 10.0, 0xFF4A4A60)
			tp.line(10.0, 120.0, 74.0, 40.0, 4.0, 0xFF30303E)
			tp.line(-4.0, 40.0, 30.0, 0.0, 3.0, 0xFF262632))
	return _glass_streaks


static func _chrome_tex() -> PaTexture:
	if _chrome == null:
		_chrome = TexPaint.paint_texture(64, 32, 4, func(tp: TexPaint) -> void:
			# A reflection of the room: bright ceiling, the dark cabinet, a lit floor band.
			tp.vgrad(0.0, 0.0, 64.0, 32.0, [0xFFFFFFFF, 0xFFB8C0E0, 0xFF40405A, 0xFF8A7AB0, 0xFF2A2440])
			tp.rect(0.0, 14.0, 64.0, 1.0, 0xFFFFE0F0))
	return _chrome


## The flipper's top: a lavender-white bat (kept under the bloom threshold) with a pale stripe and a
## star at the pivot end.
static func _flipper_body_tex() -> PaTexture:
	if _flipper_body == null:
		_flipper_body = TexPaint.paint_texture(32, 16, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 32.0, 16.0, [0xFFE6E2F8, 0xFFB8B0DC, 0xFF8E86BC])
			tp.rect(0.0, 7.0, 32.0, 2.0, 0x55FFFFFF)
			tp.star(6.0, 8.0, 3.4, Pal.with_alpha(Pal.PINK, 0.85)))
	return _flipper_body


static func _flipper_rubber_tex() -> PaTexture:
	if _flipper_rubber == null:
		_flipper_rubber = TexPaint.paint_texture(16, 10, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 16.0, 10.0, [0xFFD02C48, 0xFF8E1830])
			tp.rect(0.0, 3.0, 16.0, 2.5, 0xFFFF5A70))
	return _flipper_rubber


static func _bumper_skirt_tex() -> PaTexture:
	if _bumper_skirt == null:
		_bumper_skirt = TexPaint.paint_texture(32, 8, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 32.0, 8.0, [0xFFF0F0FA, 0xFF9C9CB8])
			for i in 8:
				tp.rect(i * 4.0, 0.0, 1.0, 8.0, 0x33000000))
	return _bumper_skirt


static func _bumper_top_tex() -> PaTexture:
	if _bumper_top == null:
		_bumper_top = TexPaint.paint_texture(32, 32, 4, func(tp: TexPaint) -> void:
			tp.fill(0xFFE0E0EC)
			tp.radial(16.0, 16.0, 16.0, 0xFFF8F8FF, 0xFFA0A0B8)
			tp.ring(16.0, 16.0, 12.5, 1.2, 0x66000000)
			tp.star(16.0, 16.0, 9.0, 0xFF707080))
	return _bumper_top


static func _target_face_tex() -> PaTexture:
	if _target_face == null:
		_target_face = TexPaint.paint_texture(16, 16, 4, func(tp: TexPaint) -> void:
			tp.fill(0xFFF0F0F0)
			tp.rect(0.0, 0.0, 16.0, 2.0, 0xFFB0B0B0)
			tp.circle(8.0, 9.0, 4.0, 0xFF404040)
			tp.star(8.0, 9.0, 3.0, Pal.WHITE))
	return _target_face


# ---------------------------------------------------------------- light textures (no bitmaps)

## A soft ring, for shock waves round a bumper: white, brightest at 72% of the radius.
static func ring_glow() -> PaTexture:
	if _ring_glow == null:
		var n := 64
		var px := PackedInt32Array()
		px.resize(n * n)
		for y in n:
			for x in n:
				var dx := (x + 0.5 - n / 2.0) / (n / 2.0)
				var dy := (y + 0.5 - n / 2.0) / (n / 2.0)
				var d := sqrt(dx * dx + dy * dy)
				var e := (d - 0.72) / 0.13
				var a := clampi(int(exp(-e * e) * 255.0), 0, 255)
				px[y * n + x] = (a << 24) | 0xFFFFFF
		_ring_glow = PaTexture.from_argb(n, n, px)
	return _ring_glow


## A strip of light: bright along its middle, fading to nothing at both long edges (v across).
static func strip_glow() -> PaTexture:
	if _strip_glow == null:
		var w := 4
		var h := 16
		var px := PackedInt32Array()
		px.resize(w * h)
		for y in h:
			for x in w:
				var t := 1.0 - absf((y + 0.5) / h * 2.0 - 1.0)
				var a := clampi(int(t * t * 255.0), 0, 255)
				px[y * w + x] = (a << 24) | 0xFFFFFF
		_strip_glow = PaTexture.from_argb(w, h, px)
	return _strip_glow


## A burst of rays fading with distance, for the backglass: 14 wedges, brightest near the centre.
static func rays() -> PaTexture:
	if _rays == null:
		var n := 128
		var px := PackedInt32Array()
		px.resize(n * n)
		for y in n:
			for x in n:
				var dx := (x + 0.5 - n / 2.0) / (n / 2.0)
				var dy := (y + 0.5 - n / 2.0) / (n / 2.0)
				var d := sqrt(dx * dx + dy * dy)
				var fade := clampf(1.0 - d, 0.0, 1.0)
				var a := atan2(dy, dx)
				var wedge := clampf(sin(a * 14.0) * 1.6, 0.0, 1.0)
				var v := clampi(int(fade * fade * wedge * 255.0), 0, 255)
				px[y * n + x] = (v << 24) | 0xFFFFFF
		_rays = PaTexture.from_argb(n, n, px)
	return _rays


## A soft band of light for the shimmer that crosses the backglass: bright in the middle, fading sideways.
static func shimmer() -> PaTexture:
	if _shimmer == null:
		var w := 32
		var h := 4
		var px := PackedInt32Array()
		px.resize(w * h)
		for y in h:
			for x in w:
				var t := 1.0 - absf((x + 0.5) / w * 2.0 - 1.0)
				var a := clampi(int(t * t * t * 255.0), 0, 255)
				px[y * w + x] = (a << 24) | 0xFFFFFF
		_shimmer = PaTexture.from_argb(w, h, px)
	return _shimmer


# ---------------------------------------------------------------- models

## A wall standing on a segment: a double-sided band of its style's height and look.
static func _wall(b: ModelBuilder, x0: float, z0: float, x1: float, z1: float, h: float, tex: PaTexture) -> void:
	var dx := x1 - x0
	var dz := z1 - z0
	var l := maxf(sqrt(dx * dx + dz * dz), 1e-4)
	b.quad(x0, h, z0, x1, h, z1, x1, 0.0, z1, x0, 0.0, z0, tex.full(), -dz / l, 0.0, dx / l, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, false, -1, 0.3)


## A steel cap along the top of a wall: a narrow glossy strip that catches the light and gives the
## wall a thickness.
static func _cap(b: ModelBuilder, x0: float, z0: float, x1: float, z1: float, h: float, half: float) -> void:
	var dx := x1 - x0
	var dz := z1 - z0
	var l := maxf(sqrt(dx * dx + dz * dz), 1e-4)
	var nx := -dz / l * half
	var nz := dx / l * half
	b.quad(
		x0 - nx, h, z0 - nz, x1 - nx, h, z1 - nz, x1 + nx, h, z1 + nz, x0 + nx, h, z0 + nz,
		_steel_tex().full(), 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, false, -1, 0.85)


## Everything on the table that never moves: playfield, rails, posts, slings, apron, backbox.
static func table() -> Model:
	if _table != null:
		return _table
	var b := ModelBuilder.new()
	b.quad(0.0, 0.0, 0.0, T.W, 0.0, 0.0, T.W, 0.0, 640.0, 0.0, 0.0, 640.0, playfield().full(), 0.0, 1.0, 0.0)
	for i in T.seg_count:
		var st := T.style[i]
		var h := GUIDE_H
		var tex := metal()
		var half := GUIDE_CAP
		if st == T.STYLE_RAIL:
			h = RAIL_H
			tex = rail()
			half = RAIL_CAP
		elif st == T.STYLE_RUBBER:
			h = RUBBER_H
			tex = rubber()
			half = RUBBER_CAP
		_wall(b, T.ax[i], T.ay[i], T.bx[i], T.by[i], h, tex)
		# The cabinet's own side walls are capped by the chrome side rails below.
		if st != T.STYLE_RUBBER:
			_cap(b, T.ax[i], T.ay[i], T.bx[i], T.by[i], h, half)
	for i in T.post_count:
		b.cylinder(T.px[i], T.py[i], 0.0, 10.0, T.pr[i], 10, rubber().full(), _post_top_tex().full(), 0.0, -1, null, false, NAN, true, 0.4)
	# Slingshot plastics over the rubbers.
	for side in 2:
		var ax := T.SLING_AX if side == 0 else T.PLAY_W - T.SLING_AX
		var cx := T.SLING_CX if side == 0 else T.PLAY_W - T.SLING_CX
		var bx := T.SLING_BX if side == 0 else T.PLAY_W - T.SLING_BX
		b.poly(
			PackedFloat32Array([ax, cx, bx]),
			PackedFloat32Array([11.0, 11.0, 11.0]),
			PackedFloat32Array([T.SLING_AY, T.SLING_CY, T.SLING_BY]),
			PackedFloat32Array([16.0, 32.0, 0.0]), PackedFloat32Array([0.0, 32.0, 32.0]),
			_sling_plastic_tex().full(), 0.0, 1.0, 0.0, Blend.OPAQUE, 0.55, false, -1, 0.6)
	# Side cabinet walls, each with a steel side rail along its inner top edge.
	var cs := _cabinet_side_tex().full()
	var stl := _steel_tex().full()
	var dm := _dark_metal_tex().full()
	var side_faces := BoxFaces.new(cs, cs, cs, cs, null, 0.0, 0.0, 0.0, 0.3)
	var steel_all := BoxFaces.new(stl, stl, stl, stl, stl, 0.0, 0.0, 0.0, 0.85)
	b.box(-26.0, 0.0, BACKBOX_Z, 0.0, 26.0, 700.0, side_faces)
	b.box(T.W, 0.0, BACKBOX_Z, T.W + 26.0, 26.0, 700.0, side_faces)
	b.box(-4.5, 26.0, BACKBOX_Z, 1.5, 28.4, 700.0, steel_all)
	b.box(T.W - 1.5, 26.0, BACKBOX_Z, T.W + 4.5, 28.4, 700.0, steel_all)
	b.box(0.0, 0.0, T.APRON_Y, T.PLAY_W, 8.0, T.APRON_Y + APRON_PRINT, BoxFaces.new(null, null, null, apron().full(), null, 0.0, 0.0, 0.0, 0.4))
	b.box(0.0, 0.0, T.APRON_Y + APRON_PRINT, T.PLAY_W, 8.0, 700.0, BoxFaces.new(dm, null, null, _apron_back_tex().full(), null, 0.0, 0.0, 0.0, 0.4))
	b.box(T.PLAY_W, 0.0, T.LANE_REST_Y + 30.0, T.W, 6.0, 700.0, BoxFaces.new(null, null, null, dm, null, 0.0, 0.0, 0.0, 0.6))
	# Backbox, standing behind the top arch: a dark body with a closed front, a steel top lip, and a
	# steel frame round the backglass window.
	var back := BoxFaces.new(dm, cs, cs, dm, null, 0.0, 0.0, 0.0, 0.4)
	b.box(-26.0, 0.0, BACKBOX_Z - 40.0, T.W + 26.0, BACKBOX_TOP, BACKBOX_Z, back)
	b.box(-28.0, BACKBOX_TOP, BACKBOX_Z - 41.0, T.W + 28.0, BACKBOX_TOP + 3.0, BACKBOX_Z + 1.6, steel_all)
	var fz := BACKBOX_Z + 1.4
	b.box(GLASS_X0 - 12.0, GLASS_Y0 - 6.0, BACKBOX_Z, GLASS_X0, GLASS_Y1 + 8.0, fz, steel_all)
	b.box(GLASS_X1, GLASS_Y0 - 6.0, BACKBOX_Z, GLASS_X1 + 12.0, GLASS_Y1 + 8.0, fz, steel_all)
	b.box(GLASS_X0 - 12.0, GLASS_Y1, BACKBOX_Z, GLASS_X1 + 12.0, GLASS_Y1 + 8.0, fz, steel_all)
	b.box(GLASS_X0 - 12.0, GLASS_Y0 - 6.0, BACKBOX_Z, GLASS_X1 + 12.0, GLASS_Y0, fz, steel_all)
	b.quad(
		GLASS_X0, GLASS_Y1, BACKBOX_Z + 0.2, GLASS_X1, GLASS_Y1, BACKBOX_Z + 0.2,
		GLASS_X1, GLASS_Y0, BACKBOX_Z + 0.2, GLASS_X0, GLASS_Y0, BACKBOX_Z + 0.2,
		backglass().full(), 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.85)
	_table = b.build()
	return _table


## Strips of light laid on the floor just inside the outer rails and up the inside of the side walls:
## pink at the top of the table running to cyan at the bottom. Additive and glowing, drawn with an
## animated brightness (see the game's render), so the table's edge breathes.
static func rail_glow() -> Model:
	if _rail_glow != null:
		return _rail_glow
	var b := ModelBuilder.new()
	var strip := strip_glow().full()
	# Floor strips along every outer rail segment, offset towards the middle of the table.
	for i in T.seg_count:
		if T.style[i] != T.STYLE_RAIL:
			continue
		var x0 := T.ax[i]
		var z0 := T.ay[i]
		var x1 := T.bx[i]
		var z1 := T.by[i]
		var dx := x1 - x0
		var dz := z1 - z0
		var l := maxf(sqrt(dx * dx + dz * dz), 1e-4)
		var nx := -dz / l
		var nz := dx / l
		# Point the offset at the table's middle.
		if (T.CX + 14.0 - (x0 + x1) / 2.0) * nx + (330.0 - (z0 + z1) / 2.0) * nz < 0.0:
			nx = -nx
			nz = -nz
		var hw := STRIP_W / 2.0
		var cx0 := x0 + nx * STRIP_IN
		var cz0 := z0 + nz * STRIP_IN
		var cx1 := x1 + nx * STRIP_IN
		var cz1 := z1 + nz * STRIP_IN
		var t := clampf((z0 + z1) / 2.0 / 640.0, 0.0, 1.0)
		b.quad(
			cx0 - nx * hw, 0.5, cz0 - nz * hw, cx1 - nx * hw, 0.5, cz1 - nz * hw,
			cx1 + nx * hw, 0.5, cz1 + nz * hw, cx0 + nx * hw, 0.5, cz0 + nz * hw,
			strip, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, false, Pal.mix(Pal.PINK, Pal.CYAN, t))
	# Two racing stripes up the inside of each side wall (matching the painted side art).
	for s in 2:
		var x := 0.25 if s == 0 else T.W - 0.25
		var nxs := 1.0 if s == 0 else -1.0
		var chunks := 6
		for c in chunks:
			var za := 70.0 + 590.0 * c / chunks
			var zb := 70.0 + 590.0 * (c + 1) / chunks
			var col := Pal.mix(Pal.PINK, Pal.CYAN, c / (chunks - 1.0))
			b.quad(
				x, 13.2, za, x, 13.2, zb, x, 9.8, zb, x, 9.8, za,
				strip, nxs, 0.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, false, col)
	_rail_glow = b.build()
	return _rail_glow


static func bumper_base() -> Model:
	if _bumper_base == null:
		var dm := _dark_metal_tex().full()
		_bumper_base = ModelBuilder.new() \
			.cylinder(0.0, 0.0, 0.0, 2.0, T.BUMPER_R + 3.0, 16, dm, dm, 0.0, -1, null, false, NAN, true, 0.6) \
			.cylinder(0.0, 0.0, 2.0, 9.0, T.BUMPER_R, 16, _bumper_skirt_tex().full(), null, 0.0, -1, null, false, NAN, true, 0.4) \
			.cylinder(0.0, 0.0, 9.0, 12.0, T.BUMPER_R - 4.0, 12, dm, null, 0.0, -1, null, false, NAN, true, 0.5) \
			.build()
	return _bumper_base


## The lit cap: tinted per bumper and brightened with the draw's emissive boost.
static func bumper_cap() -> Model:
	if _bumper_cap == null:
		var top := _bumper_top_tex().full()
		_bumper_cap = ModelBuilder.new() \
			.cylinder(0.0, 0.0, 12.0, 17.0, T.BUMPER_R - 1.0, 16, top, top, 0.6, -1, null, false, T.BUMPER_R - 3.0) \
			.build()
	return _bumper_cap


static func drop_target() -> Model:
	if _drop_target == null:
		var f := _target_face_tex().full()
		_drop_target = ModelBuilder.new().box(-2.0, 0.0, -T.DROP_LEN / 2.0, 2.0, 14.0, T.DROP_LEN / 2.0, BoxFaces.new(f, f, f, f, f, 0.0, 0.0, 0.0, 0.5)).build()
	return _drop_target


## A flipper lying along +x from its pivot: a bat with a red rubber round it, and a steel bolt at the pivot.
static func flipper() -> Model:
	if _flipper != null:
		return _flipper
	var b := ModelBuilder.new()
	var r0 := T.FLIP_R0
	var r1 := T.FLIP_R1
	var l := T.FLIP_LEN
	var body := _flipper_body_tex().full()
	var band := _flipper_rubber_tex().full()
	b.quad(0.0, 9.0, -r0, l, 9.0, -r1, l, 9.0, r1, 0.0, 9.0, r0, body, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, false, -1, 0.5)
	b.quad(0.0, 9.0, -r0, l, 9.0, -r1, l, 0.0, -r1, 0.0, 0.0, -r0, band, 0.0, 0.0, -1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, false, -1, 0.3)
	b.quad(0.0, 9.0, r0, l, 9.0, r1, l, 0.0, r1, 0.0, 0.0, r0, band, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, false, -1, 0.3)
	b.cylinder(0.0, 0.0, 0.0, 9.0, r0, 14, band, body, 0.0, -1, null, false, NAN, true, 0.3)
	b.cylinder(l, 0.0, 0.0, 9.0, r1, 10, band, body, 0.0, -1, null, false, NAN, true, 0.3)
	var stl := _steel_tex().full()
	b.cylinder(0.0, 0.0, 9.0, 10.6, 3.4, 10, stl, stl, 0.0, -1, null, false, NAN, true, 0.9)
	_flipper = b.build()
	return _flipper


## The plunger: a chrome rod running towards the player with a red knob.
static func plunger() -> Model:
	if _plunger == null:
		var dm := _dark_metal_tex().full()
		_plunger = ModelBuilder.new() \
			.capsule(0.0, 0.0, 0.0, 0.0, 0.0, 70.0, 2.4, metal().full(), 10, -1, 0.9) \
			.cylinder(0.0, 2.0, -3.0, 3.0, 5.0, 10, dm, dm) \
			.build()
	return _plunger


static func ball() -> Model:
	if _ball == null:
		_ball = ModelBuilder.new().sphere(0.0, 0.0, 0.0, T.BALL_R, _chrome_tex().full(), 18, 12, 1.0, -1, 1.0).build()
	return _ball


# ---------------------------------------------------------------- hall cabinet

## The playfield seen through the cabinet's glass: a small, simplified painting of the table.
static func cabinet_playfield() -> PaTexture:
	if _cabinet_playfield == null:
		_cabinet_playfield = TexPaint.paint_texture(60, 128, 3, _paint_cabinet_playfield)
	return _cabinet_playfield


static func _paint_cabinet_playfield(tp: TexPaint) -> void:
	tp.vgrad(0.0, 0.0, 60.0, 128.0, [0xFF0A0B30, 0xFF2A0E47, 0xFF140828])
	_stars(tp, 60.0, 128.0, 40, 2, Pal.LAVENDER)
	var kx := 60.0 / T.W
	var ky := 128.0 / 660.0
	for i in 3:
		tp.circle(T.BUMPER_X[i] * kx, T.BUMPER_Y[i] * ky, T.BUMPER_R * kx * 1.2, Pal.CYAN if i == 1 else Pal.HOTPINK)
		tp.circle(T.BUMPER_X[i] * kx, T.BUMPER_Y[i] * ky, T.BUMPER_R * kx * 0.6, Pal.WHITE)
	for i in T.seg_count:
		var st := T.style[i]
		var c := Pal.LAVENDER if st == T.STYLE_RAIL else (Pal.WHITE if st == T.STYLE_RUBBER else Pal.LIGHTGRAY)
		tp.line(T.ax[i] * kx, T.ay[i] * ky, T.bx[i] * kx, T.by[i] * ky, 0.8, c)
	for i in 4:
		tp.circle((T.CX - 54.0 + i * 36.0) * kx, 372.0 * ky, 2.0, Pal.GOLD)
	for s in 2:
		var x := T.flip_x(s) * kx
		var a := T.rest_angle(s)
		tp.line(x, T.FLIP_Y * ky, x + cos(a) * T.FLIP_LEN * kx, (T.FLIP_Y + sin(a) * T.FLIP_LEN) * ky, 2.0, Pal.WHITE)
	tp.label("FLIP", 30.0, 84.0, 6.0, Pal.HOTPINK)
	tp.rect(0.0, T.APRON_Y * ky, T.PLAY_W * kx, 128.0 - T.APRON_Y * ky, 0xFF2B1A5C)


## The attract-mode ball rolling round the cabinet's playfield.
static func cabinet_ball() -> Model:
	if _cabinet_ball == null:
		_cabinet_ball = ModelBuilder.new().sphere(0.0, 0.0, 0.0, 0.9, _chrome_tex().full(), 10, 6, 1.0, -1, 1.0).build()
	return _cabinet_ball
