class_name StackerArt
extends RefCounted
## games/stacker/StackerArt.kt: painted art for the stacker's world. The tower is being built on a
## rooftop high above a night city, so the picture is a dark floor of lights far below with soft
## cloud decks between, and the tower itself the only bright, saturated thing. Everything is built
## lazily on first use.

## Transparent white: soft gradients fade to this so their middles don't turn grey.
const CLEAR_WHITE := 0x00FFFFFF
const CITY_DARK := 0xFF05040E

## Slab hues: a step round the colour wheel a level, starting at violet.
const HUE_START := 260.0
const HUE_STEP := 13.0

static var _body_table := PackedInt64Array()
static var _glow_table := PackedInt64Array()
static var _slab_side: PaTexture = null
static var _city: PaTexture = null
static var _cloud: PaTexture = null
static var _roof: PaTexture = null
static var _steel: PaTexture = null
static var _ring: PaTexture = null
static var _flare: PaTexture = null
static var _shaft: PaTexture = null
static var _markers: PaTexture = null
static var _marker_regions: Array[Region] = []
static var _f32_buf := PackedFloat32Array([0.0])


## [param x] rounded to a 32-bit float: build-13 did this arithmetic in Kotlin Floats, and where a
## result is truncated to an integer (a colour channel, a count) the port must round alike.
static func _f32(x: float) -> float:
	_f32_buf[0] = x
	return _f32_buf[0]


## Hue (degrees) of a level's slab.
static func hue_of(level: int) -> float:
	return fmod(_f32(level * HUE_STEP) + HUE_START, 360.0)


static func _tables() -> void:
	if _body_table.size() == 256:
		return
	_body_table.resize(256)
	_glow_table.resize(256)
	for i in 256:
		_body_table[i] = hsv(hue_of(i), 0.8, 0.5)
		_glow_table[i] = hsv(hue_of(i), 0.55, 1.0)


## A slab's body colour: a dark, saturated glass, so only its edges and top catch the light and glow.
static func body_color(level: int) -> int:
	_tables()
	return _body_table[level & 255]


## The neon of a slab's edges: the same hue, light and vivid.
static func glow_color(level: int) -> int:
	_tables()
	return _glow_table[level & 255]


## ARGB from hue in degrees, saturation and value in 0..1 (in 32-bit float arithmetic, as build-13).
static func hsv(h: float, s: float, v: float) -> int:
	var hf := _f32(h)
	var sf := _f32(s)
	var vf := _f32(v)
	var c := _f32(vf * sf)
	var hp := _f32(fmod(hf, 360.0) / 60.0)
	var x := _f32(c * _f32(1.0 - absf(_f32(fmod(hp, 2.0) - 1.0))))
	var r1 := 0.0
	var g1 := 0.0
	var b1 := 0.0
	match int(hp):
		0:
			r1 = c
			g1 = x
		1:
			r1 = x
			g1 = c
		2:
			g1 = c
			b1 = x
		3:
			g1 = x
			b1 = c
		4:
			r1 = x
			b1 = c
		_:
			r1 = c
			b1 = x
	var m := _f32(vf - c)
	var r := clampi(int(_f32(_f32(r1 + m) * 255.0)), 0, 255)
	var g := clampi(int(_f32(_f32(g1 + m) * 255.0)), 0, 255)
	var b := clampi(int(_f32(_f32(b1 + m) * 255.0)), 0, 255)
	return (0xFF << 24) | (r << 16) | (g << 8) | b


## A slab's side: light at the top, shading darker towards the foot, with a groove where it meets
## the slab below.
static func slab_side() -> PaTexture:
	if _slab_side == null:
		_slab_side = TexPaint.paint_texture(8, 16, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 8.0, 16.0, [Pal.WHITE, 0xFFB4B4B4])
			tp.rect(0.0, 13.8, 8.0, 1.2, Pal.with_alpha(Pal.BLACK, 0.3)))
	return _slab_side


## The city far below, seen from above: streets, blocks and thousands of lit windows and cars,
## brighter downtown, with a river and two highways, fading to the dark at the edges.
static func city() -> PaTexture:
	if _city == null:
		_city = TexPaint.paint_texture(256, 256, 4, func(tp: TexPaint) -> void: _paint_city(tp))
	return _city


static func _paint_city(tp: TexPaint) -> void:
	tp.fill(CITY_DARK)
	# Blocks of buildings: faint lit rooftops.
	for by in 16:
		for bx in 16:
			var n := MathUtil.hash01(bx, by, 11)
			var x := bx * 16.0
			var y := by * 16.0
			tp.rect(x + 2.0, y + 2.0, 12.0, 12.0, Pal.with_alpha(0xFF241C44 if n > 0.5 else 0xFF181233, 0.9))
			# Windows and cars (their number in Float arithmetic, as build-13 counted them).
			var nf := _f32(n)
			var dens := _f32(_f32(0.35) + _f32(_f32(0.65) * _f32(1.0 - _f32(_f32(absf(bx - 7.5) + absf(by - 7.5)) / 15.0))))
			var count := int(_f32(3.0 + _f32(_f32(nf * 14.0) * dens)))
			for k in count:
				var c := MathUtil.hash01(bx * 31 + k, by * 17 + k, 12)
				var color: int
				if c < 0.5:
					color = 0xFFFFC060
				elif c < 0.75:
					color = 0xFFDDEEFF
				elif c < 0.9:
					color = Pal.CYAN
				else:
					color = Pal.HOTPINK
				tp.circle(x + 2.0 + MathUtil.hash01(k, bx + by * 16, 13) * 12.0, y + 2.0 + MathUtil.hash01(k, by + bx * 16, 14) * 12.0, 0.5,
					Pal.with_alpha(color, 0.5 + 0.5 * MathUtil.hash01(k, bx, by)))
	# Streets.
	for i in 17:
		var p := i * 16.0
		for k in 16:
			if MathUtil.hash01(i, k, 15) > 0.3:
				tp.rect(p - 0.5, k * 16.0, 1.0, 16.0, Pal.with_alpha(0xFFFFC060, 0.32))
			if MathUtil.hash01(k, i, 16) > 0.3:
				tp.rect(k * 16.0, p - 0.5, 16.0, 1.0, Pal.with_alpha(0xFFFFC060, 0.32))
	# A river winding through, and two bright highways.
	var prev_x := 0.0
	var prev_y := 120.0
	for k in range(1, 41):
		var x := k * 6.4
		var y := 120.0 + sin(x / 38.0) * 26.0 + sin(x / 15.0) * 6.0
		tp.line(prev_x, prev_y, x, y, 9.0, 0xFF0A1636)
		tp.line(prev_x, prev_y, x, y, 1.4, Pal.with_alpha(Pal.SKY, 0.3))
		prev_x = x
		prev_y = y
	tp.line(0.0, 40.0, 256.0, 210.0, 2.4, Pal.with_alpha(0xFFFF9A3C, 0.7))
	tp.line(30.0, 256.0, 220.0, 0.0, 2.0, Pal.with_alpha(Pal.CYAN, 0.55))
	# Fade into the dark at the edges of the map.
	tp.radial(128.0, 128.0, 128.0, 0x0005040E, CITY_DARK)


## Soft cloud banks: overlapping lavender and rose puffs (alpha-blended, self-lit).
static func cloud() -> PaTexture:
	if _cloud == null:
		_cloud = TexPaint.paint_texture(128, 128, 3, func(tp: TexPaint) -> void:
			tp.clear(0)
			for i in 46:
				var x := 14.0 + MathUtil.hash01(i, 31) * 100.0
				var y := 14.0 + MathUtil.hash01(i, 32) * 100.0
				var r := 9.0 + MathUtil.hash01(i, 33) * 22.0
				var c := 0xFFC58AE0 if MathUtil.hash01(i, 34) > 0.6 else 0xFF8A78D8
				tp.radial(x, y, r, Pal.with_alpha(c, 0.3), Pal.with_alpha(c, 0.0))
			# Fade to nothing at the edges so the bank has no rim.
			var p := tp.paint.reset()
			p.xfer = PaPaint.Xfer.DST_IN
			p.shader = PaBrush.radial([Pal.WHITE, Pal.WHITE, CLEAR_WHITE], Vector2(64.0, 64.0), 64.0, [0.0, 0.55, 1.0])
			tp.c_draw_rect(0.0, 0.0, 128.0, 128.0, p)
			p.shader = null
			p.xfer = PaPaint.Xfer.NONE)
	return _cloud


## The rooftop the tower stands on: dark panels with vents, and neon rings round the pad (a cyan
## ring and a pink one, thin so only they glow).
static func roof() -> PaTexture:
	if _roof == null:
		_roof = TexPaint.paint_texture(64, 64, 8, func(tp: TexPaint) -> void: _paint_roof(tp))
	return _roof


static func _paint_roof(tp: TexPaint) -> void:
	tp.fill(0xFF120E22)
	for i in 8:
		tp.rect(i * 8.0, 0.0, 0.3, 64.0, 0xFF0A0716)
		tp.rect(0.0, i * 8.0, 64.0, 0.3, 0xFF0A0716)
	for i in 60:
		tp.circle(MathUtil.hash01(i, 41) * 64.0, MathUtil.hash01(i, 42) * 64.0, 0.25, 0xFF1C1634)
	tp.radial(32.0, 32.0, 26.0, Pal.with_alpha(Pal.VIOLET, 0.28), 0x00000000)
	tp.ring(32.0, 32.0, 12.5, 0.5, Pal.with_alpha(Pal.CYAN, 0.9))
	tp.ring(32.0, 32.0, 19.0, 0.35, Pal.with_alpha(Pal.HOTPINK, 0.7))
	tp.ring(32.0, 32.0, 25.5, 0.3, Pal.with_alpha(Pal.CYAN, 0.45))
	# Hazard chevrons at the four corners.
	for k in 4:
		var ang := k * 1.5708 + 0.7854
		for s in 3:
			var d := 30.0 + s * 1.6
			tp.circle(32.0 + cos(ang) * d * 1.05, 32.0 + sin(ang) * d * 1.05, 0.55, Pal.with_alpha(Pal.YELLOW, 0.7))
	tp.hgrad(0.0, 0.0, 8.0, 64.0, [0x90000000, 0])
	tp.hgrad(56.0, 0.0, 8.0, 64.0, [0, 0x90000000])
	tp.vgrad(0.0, 0.0, 64.0, 8.0, [0x90000000, 0])
	tp.vgrad(0.0, 56.0, 64.0, 8.0, [0, 0x90000000])


static func steel() -> PaTexture:
	if _steel == null:
		_steel = TexPaint.paint_texture(8, 16, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 8.0, 16.0, [0xFF4C526C, 0xFF1C2034])
			tp.rect(1.4, 0.0, 1.2, 16.0, Pal.with_alpha(Pal.WHITE, 0.2)))
	return _steel


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


## A four-point glint with a hot core.
static func flare() -> PaTexture:
	if _flare == null:
		_flare = TexPaint.paint_texture(32, 32, 4, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.radial(16.0, 16.0, 14.0, Pal.with_alpha(Pal.WHITE, 0.8), CLEAR_WHITE)
			tp.oval(16.0, 16.0, 15.5, 0.8, Pal.with_alpha(Pal.WHITE, 0.9))
			tp.oval(16.0, 16.0, 0.8, 15.5, Pal.with_alpha(Pal.WHITE, 0.9))
			tp.circle(16.0, 16.0, 2.2, Pal.WHITE))
	return _flare


## A column of light: a soft bell across, fading to nothing towards the end of the beam.
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


## Height markers "10", "20" ... "100" in the game's pixel type, one 36 × 16 cell each.
static func _markers_tex() -> PaTexture:
	if _markers == null:
		_markers = TexPaint.paint_texture(360, 16, 4, func(tp: TexPaint) -> void:
			tp.clear(0)
			for i in 10:
				var text := str((i + 1) * 10)
				var cx := i * 36.0 + 18.0
				tp.glow(1.2, Pal.with_alpha(Pal.CYAN, 0.8), func(g: TexPaint) -> void: g.label(text, cx, 3.0, 10.0, Pal.WHITE))
				tp.label(text, cx, 3.0, 10.0, Pal.mix(Pal.CYAN, Pal.WHITE, 0.6)))
	return _markers


## The marker for height [param tens] × 10 (1..10); higher ones reuse the last.
static func marker(tens: int) -> Region:
	if _marker_regions.is_empty():
		var t := _markers_tex()
		for i in 10:
			_marker_regions.append(t.region(i * 36, 0, 36, 16))
	return _marker_regions[clampi(tens - 1, 0, 9)]
