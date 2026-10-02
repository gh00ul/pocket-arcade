class_name RacerArt
extends RefCounted
## games/racer/RacerArt.kt: painted art for the synthwave racer. Every texture is painted on first
## use (Kotlin's `by lazy`) and kept; the car paints are painted per call and cached by RacerScene.
## The hall's racer cabinet (RacerCabinet) uses [method seat_plate], [method dash] and
## [method checker].

## Transparent white: soft gradients fade to this so their middles don't turn grey.
const CLEAR_WHITE := 0x00FFFFFF
## Car paint is a touch under full brightness so pale colours (white, yellow) stay under the bloom.
const PAINT := 0.86

static var _sun: PaTexture = null
static var _skyline: PaTexture = null
static var _skyline_far: PaTexture = null
static var _mountains: PaTexture = null
static var _ground: PaTexture = null
static var _asphalt: PaTexture = null
static var _asphalt_dark: PaTexture = null
static var _palm: PaTexture = null
static var _signs: Array = []
static var _post: PaTexture = null
static var _lamp_red: PaTexture = null
static var _lamp_green: PaTexture = null
static var _lamp_off: PaTexture = null
static var _lamp_box: PaTexture = null
static var _checker: PaTexture = null
static var _seat_plates := {}
static var _dash: PaTexture = null
static var _banner: PaTexture = null
static var _token: PaTexture = null
static var _glass: PaTexture = null
static var _tyre: PaTexture = null
static var _wheel: PaTexture = null
static var _plume: PaTexture = null
static var _smoke: PaTexture = null
static var _horizon: PaTexture = null
static var _skid: PaTexture = null


## Setting sun, sliced by bands that widen towards the horizon.
static func sun() -> PaTexture:
	if _sun == null:
		var n := 64
		_sun = TexPaint.paint_texture(n, n, 8, func(tp: TexPaint) -> void:
			tp.clear(0)
			var m := n / 2.0
			_vgrad_circle(tp, m, m, m - 1.0)
			var band := m + 2.0
			var gap := 1.0
			while band < n:
				var p := tp.paint.reset()
				p.xfer = PaPaint.Xfer.CLEAR
				tp.c_draw_rect(0.0, band, float(n), band + gap, p)
				p.xfer = PaPaint.Xfer.NONE
				band += gap + 6.0
				gap += 1.0
		)
	return _sun


static func _vgrad_circle(tp: TexPaint, cx: float, cy: float, r: float) -> void:
	var p := tp.paint.reset()
	p.shader = PaBrush.linear([Pal.YELLOW, Pal.PINK], Vector2(0.0, cy - r), Vector2(0.0, cy + r))
	tp.c_draw_circle(cx, cy, r, p)
	p.shader = null


## City skyline silhouette for the horizon: plum towers rim-lit in magenta on their right edge,
## stepped tops, antennas with red beacons, and windows lit in a few colours.
static func skyline() -> PaTexture:
	if _skyline == null:
		_skyline = _skyline_texture(0.0, 0)
	return _skyline


## A second, hazier skyline behind the first, so the city has depth as it slides past.
static func skyline_far() -> PaTexture:
	if _skyline_far == null:
		_skyline_far = _skyline_texture(0.55, 7)
	return _skyline_far


static func _skyline_texture(haze: float, seed_k: int) -> PaTexture:
	return TexPaint.paint_texture(256, 48, 4, func(tp: TexPaint) -> void:
		tp.clear(0)
		var windows := [Pal.YELLOW, Pal.CYAN, Pal.HOTPINK]
		var x := 0.0
		var k := seed_k
		while x < 256.0:
			var w := 8.0 + (k * 37 % 14)
			var h := (12.0 + (k * 53 % 30)) * (0.8 if haze > 0.0 else 1.0)
			var body := Pal.mix(Pal.shade(Pal.PLUM, 0.7), Pal.mix(Pal.PLUM, Pal.PINK, 0.4), haze)
			tp.vgrad(x, 48.0 - h, w, h, [Pal.shade(body, 1.05), Pal.shade(body, 0.7)])
			tp.rect(x + w - 0.6, 48.0 - h, 0.6, h, Pal.with_alpha(Pal.HOTPINK, 0.55 - haze * 0.25))
			if k % 3 == 0:
				tp.rect(x + w * 0.25, 48.0 - h - 3.0, w * 0.5, 3.0, Pal.shade(body, 0.85))
			if k % 4 == 1:
				tp.rect(x + w / 2.0 - 0.3, 48.0 - h - 7.0, 0.6, 7.0, Pal.shade(body, 0.8))
				tp.circle(x + w / 2.0, 48.0 - h - 7.0, 0.7, Pal.with_alpha(Pal.RED, 0.9 - haze * 0.5))
			var wy := 48.0 - h + 3.0
			while wy < 46.0:
				var wx := x + 2.0
				while wx < x + w - 2.0:
					var n := int(wx * 7) + int(wy * 13) + k
					if n % 5 == 0:
						tp.rect(wx, wy, 1.2, 1.6, Pal.with_alpha(windows[(n / 5) % 3], 0.85 - haze * 0.6))
					wx += 3.0
				wy += 4.0
			x += w + (k % 3)
			k += 1
	)


static func mountains() -> PaTexture:
	if _mountains == null:
		_mountains = TexPaint.paint_texture(256, 40, 4, func(tp: TexPaint) -> void:
			tp.clear(0)
			var pts := PackedFloat32Array([0.0, 40.0])
			var x := 0.0
			while x <= 256.0:
				pts.append(x)
				pts.append(40.0 - (18.0 + sin(x / 19.0) * 9.0 + sin(x / 7.0 + 1.0) * 4.0 + sin(x / 43.0) * 6.0))
				x += 1.0
			pts.append(256.0)
			pts.append(40.0)
			var p := tp.paint.reset()
			p.shader = PaBrush.linear([Pal.mix(Pal.INDIGO, Pal.VIOLET, 0.4), Pal.INDIGO], Vector2(0.0, 5.0), Vector2(0.0, 40.0))
			var path := PaPath.new()
			path.move_to(pts[0], pts[1])
			var i := 2
			while i < pts.size():
				path.line_to(pts[i], pts[i + 1])
				i += 2
			path.close()
			tp.c_draw_path(path, p)
			p.shader = null
			# A neon ridge line.
			p.style = PaPaint.Style.STROKE
			p.stroke_width = 0.7
			p.color = Pal.HOTPINK
			var ridge := PaPath.new()
			ridge.move_to(pts[2], pts[3])
			i = 4
			while i < pts.size() - 2:
				ridge.line_to(pts[i], pts[i + 1])
				i += 2
			tp.c_draw_path(ridge, p)
			p.style = PaPaint.Style.FILL
		)
	return _mountains


## Ground with a neon grid; each segment shows one cross line along its start.
static func ground() -> PaTexture:
	if _ground == null:
		_ground = TexPaint.paint_texture(128, 8, 4, func(tp: TexPaint) -> void:
			tp.fill(0xFF14082A)
			for x in range(0, 128, 8):
				tp.rect(float(x), 0.0, 0.7, 8.0, Pal.shade(Pal.PINK, 0.8))
			tp.rect(0.0, 0.0, 128.0, 0.8, Pal.PINK)
		)
	return _ground


## Road surface, one tile across the road's width: speckled asphalt with two darker wheel tracks
## (where the lanes' cars run) and a faintly polished middle.
static func _asphalt_tex(base: int, speck: int, seed_k: int) -> PaTexture:
	return TexPaint.paint_texture(16, 8, 8, func(tp: TexPaint) -> void:
		tp.fill(base)
		for i in 40:
			tp.circle(MathUtil.hash01(i, seed_k) * 16.0, MathUtil.hash01(i, seed_k + 1) * 8.0, 0.18, speck)
		tp.rect(3.2, 0.0, 2.6, 8.0, Pal.with_alpha(Pal.BLACK, 0.2))
		tp.rect(10.2, 0.0, 2.6, 8.0, Pal.with_alpha(Pal.BLACK, 0.2))
		tp.rect(6.6, 0.0, 2.8, 8.0, Pal.with_alpha(Pal.WHITE, 0.03))
	)


static func asphalt() -> PaTexture:
	if _asphalt == null:
		_asphalt = _asphalt_tex(0xFF24203A, 0xFF34304E, 3)
	return _asphalt


static func asphalt_dark() -> PaTexture:
	if _asphalt_dark == null:
		_asphalt_dark = _asphalt_tex(0xFF1C1830, 0xFF2A2644, 5)
	return _asphalt_dark


static func white() -> PaTexture:
	return TexKit.white()


## A palm tree silhouette with a neon rim.
static func palm() -> PaTexture:
	if _palm == null:
		_palm = TexPaint.paint_texture(28, 44, 8, func(tp: TexPaint) -> void:
			tp.clear(0)
			var trunk := Pal.shade(Pal.BROWN, 0.5)
			_palm_tree(tp, Pal.PINK, 0.9)
			_palm_tree(tp, 0xFF1A0A24, 0.0)
			for k in 7:
				tp.line(13.6, 40.0 - k * 4.0, 15.4, 39.4 - k * 4.0, 0.25, trunk)
		)
	return _palm


const _FRONDS := [[-1.0, -0.35], [1.0, -0.35], [-1.0, 0.25], [1.0, 0.25], [-0.4, -0.9], [0.4, -0.9]]


static func _palm_tree(tp: TexPaint, color: int, grow: float) -> void:
	var prev_x := 14.5
	var prev_y := 44.0
	for k in range(1, 16):
		var y := 44.0 - k * 2.0
		var x := 14.5 + sin(y / 9.0) * 2.0
		tp.line(prev_x, prev_y, x, y, 2.4 + grow - k * 0.06, color)
		prev_x = x
		prev_y = y
	for frond: Array in _FRONDS:
		var dx: float = frond[0]
		var dy: float = frond[1]
		var px := 14.0
		var py := 14.0
		for t in range(1, 13):
			var nx := 14.0 + dx * t
			var ny := 14.0 + dy * t + t * t * 0.05
			tp.line(px, py, nx, ny, (2.2 - t * 0.12) + grow, color)
			px = nx
			py = ny
	tp.circle(14.0, 14.0, 2.4 + grow, color)


## A roadside billboard.
static func _sign(text: String, color: int) -> PaTexture:
	return TexPaint.paint_texture(60, 22, 6, func(tp: TexPaint) -> void:
		tp.fill(0xFF08060C)
		tp.stroke_round(0.6, 0.6, 58.8, 20.8, 1.5, 1.2, color)
		tp.stroke_round(2.5, 2.5, 55.0, 17.0, 1.0, 0.5, Pal.shade(color, 0.5))
		tp.glow(1.2, Pal.with_alpha(color, 0.8), func(g: TexPaint) -> void: g.label(text, 30.0, 7.0, 8.0, Pal.WHITE))
		tp.label(text, 30.0, 7.0, 8.0, Pal.mix(color, Pal.WHITE, 0.3))
	)


## The roadside billboards, in the order the track cycles through them.
static func signs() -> Array:
	if _signs.is_empty():
		_signs = [
			_sign("ARCADE", Pal.CYAN),
			_sign("TURBO!", Pal.PINK),
			_sign("DRIFT!", Pal.YELLOW),
			_sign("HI-SCORE", Pal.LIME),
		]
	return _signs


static func post() -> PaTexture:
	if _post == null:
		_post = TexKit.solid(4, 4, Pal.DARKGRAY)
	return _post


## Start light lenses, lit red and green and dark, and the black box they sit in.
static func _lamp(color: int, lit: bool) -> PaTexture:
	return TexPaint.paint_texture(16, 16, 8, func(tp: TexPaint) -> void:
		tp.clear(0)
		tp.circle(8.0, 8.0, 7.2, Pal.shade(color, 0.7 if lit else 0.25))
		tp.circle(8.0, 8.0, 5.6, Pal.shade(color, 1.0 if lit else 0.35))
		if lit:
			tp.circle(6.5, 6.5, 2.0, Pal.mix(color, Pal.WHITE, 0.6))
	)


static func lamp_red() -> PaTexture:
	if _lamp_red == null:
		_lamp_red = _lamp(0xFFFF2020, true)
	return _lamp_red


static func lamp_green() -> PaTexture:
	if _lamp_green == null:
		_lamp_green = _lamp(0xFF20FF50, true)
	return _lamp_green


static func lamp_off() -> PaTexture:
	if _lamp_off == null:
		_lamp_off = _lamp(Pal.GRAY, false)
	return _lamp_off


static func lamp_box() -> PaTexture:
	if _lamp_box == null:
		_lamp_box = TexKit.solid(4, 4, 0xFF06040A)
	return _lamp_box


## Black and white chequers painted across the road at the start/finish line.
static func checker() -> PaTexture:
	if _checker == null:
		_checker = TexPaint.paint_texture(32, 4, 4, func(tp: TexPaint) -> void:
			tp.fill(Pal.WHITE)
			for x in 16:
				for y in 2:
					if (x + y) % 2 == 0:
						tp.rect(x * 2.0, y * 2.0, 2.0, 2.0, Pal.BLACK)
		)
	return _checker


## The lit plate on the back of a hall cabinet's racing seat, the pod's number [param n] in a
## roundel under a chequered strip, so the pods read from across the hall.
static func seat_plate(n: int) -> PaTexture:
	var t: PaTexture = _seat_plates.get(n)
	if t == null:
		t = TexPaint.paint_texture(32, 48, 6, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 32.0, 48.0, [0xFF3A0A12, 0xFF140408])
			for x in 8:
				for y in 2:
					tp.rect(x * 4.0, 2.0 + y * 4.0, 4.0, 4.0, Pal.WHITE if (x + y) % 2 == 0 else Pal.BLACK)
			tp.glow(2.5, Pal.with_alpha(Pal.RED, 0.9), func(g: TexPaint) -> void: g.stroke_round(2.0, 12.0, 28.0, 34.0, 4.0, 1.4, Pal.WHITE))
			tp.stroke_round(2.0, 12.0, 28.0, 34.0, 4.0, 1.0, 0xFFFF8A80)
			tp.circle(16.0, 26.0, 9.0, Pal.WHITE)
			tp.ring(16.0, 26.0, 9.0, 1.2, Pal.RED)
			tp.label(str(n), 16.0, 21.5, 10.0, 0xFF140408)
			tp.label("TURBO", 16.0, 38.5, 4.2, Pal.WHITE)
		)
		_seat_plates[n] = t
	return t


## The hall cabinet's dash: a lit speedo and rev counter either side of a digital readout.
static func dash() -> PaTexture:
	if _dash == null:
		_dash = TexPaint.paint_texture(64, 20, 6, func(tp: TexPaint) -> void:
			tp.fill(0xFF100C18)
			tp.rect(0.0, 0.0, 64.0, 0.8, Pal.shade(Pal.PINK, 0.7))
			for g in 2:
				var cx := 12.0 if g == 0 else 52.0
				var color := Pal.CYAN if g == 0 else Pal.PINK
				tp.circle(cx, 11.0, 8.0, 0xFF1C1828)
				tp.ring(cx, 11.0, 7.4, 0.8, color)
				for k in 9:
					var a := (0.75 + k * 0.1875) * PI
					tp.line(cx + cos(a) * 5.6, 11.0 + sin(a) * 5.6, cx + cos(a) * 6.8, 11.0 + sin(a) * 6.8, 0.5, Pal.RED if k >= 7 else Pal.WHITE)
				var needle := (1.95 if g == 0 else 2.1) * PI
				tp.line(cx, 11.0, cx + cos(needle) * 6.0, 11.0 + sin(needle) * 6.0, 0.8, Pal.ORANGE)
				tp.circle(cx, 11.0, 1.2, Pal.GRAY)
			tp.round(23.0, 6.0, 18.0, 9.0, 1.0, 0xFF06040A)
			tp.label("288", 32.0, 7.5, 6.0, Pal.CYAN)
		)
	return _dash


## The neon FINISH banner on the start/finish gantry, chequered at both ends.
static func banner() -> PaTexture:
	if _banner == null:
		_banner = TexPaint.paint_texture(120, 20, 6, func(tp: TexPaint) -> void:
			tp.fill(0xFF08060C)
			for x in 4:
				for y in 4:
					var c := Pal.WHITE if (x + y) % 2 == 0 else Pal.BLACK
					tp.rect(x * 4.0, y * 5.0, 4.0, 5.0, c)
					tp.rect(104.0 + x * 4.0, y * 5.0, 4.0, 5.0, c)
			tp.stroke_round(17.0, 1.0, 86.0, 18.0, 2.0, 1.0, Pal.CYAN)
			tp.glow(1.4, Pal.with_alpha(Pal.PINK, 0.8), func(g: TexPaint) -> void: g.label("FINISH", 60.0, 5.0, 10.0, Pal.WHITE))
			tp.label("FINISH", 60.0, 5.0, 10.0, Pal.mix(Pal.PINK, Pal.WHITE, 0.3))
		)
	return _banner


## A spinning token pickup (drawn squashed to fake the spin).
static func token() -> PaTexture:
	if _token == null:
		_token = TexPaint.paint_texture(14, 14, 10, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.circle(7.0, 7.0, 6.6, Pal.ORANGE)
			var p := tp.paint.reset()
			p.shader = PaBrush.radial([Pal.mix(Pal.GOLD, Pal.WHITE, 0.35), Pal.GOLD], Vector2(5.5, 5.0), 9.0)
			tp.c_draw_circle(7.0, 7.0, 5.2, p)
			p.shader = null
			tp.star(7.0, 7.2, 3.0, Pal.ORANGE)
		)
	return _token


# ------------------------------------------------------------------ cars

## A car's flank: clearcoat light at the shoulder, a white racing stripe, a dark sill.
static func car_side(color: int) -> PaTexture:
	return TexPaint.paint_texture(48, 12, 4, func(tp: TexPaint) -> void:
		var c := Pal.shade(color, PAINT)
		tp.vgrad(0.0, 0.0, 48.0, 12.0, [Pal.mix(c, Pal.WHITE, 0.22), c, Pal.shade(c, 0.55)])
		tp.rect(0.0, 2.2, 48.0, 0.6, Pal.with_alpha(Pal.WHITE, 0.45))
		tp.rect(0.0, 6.0, 48.0, 1.4, Pal.with_alpha(Pal.WHITE, 0.55))
		tp.rect(0.0, 10.2, 48.0, 1.8, 0xFF0A0810)
	)


## Roof, hood and deck: a centre stripe bordered in black, with a little metallic flake.
static func car_top(color: int) -> PaTexture:
	return TexPaint.paint_texture(24, 48, 4, func(tp: TexPaint) -> void:
		var c := Pal.shade(color, PAINT)
		tp.vgrad(0.0, 0.0, 24.0, 48.0, [Pal.mix(c, Pal.WHITE, 0.16), c, Pal.shade(c, 0.85)])
		tp.rect(9.6, 0.0, 4.8, 48.0, Pal.with_alpha(Pal.WHITE, 0.6))
		tp.rect(9.0, 0.0, 0.6, 48.0, Pal.with_alpha(Pal.BLACK, 0.3))
		tp.rect(14.4, 0.0, 0.6, 48.0, Pal.with_alpha(Pal.BLACK, 0.3))
		for i in 40:
			tp.circle(MathUtil.hash01(i, 71) * 24.0, MathUtil.hash01(i, 72) * 48.0, 0.25, Pal.with_alpha(Pal.WHITE, 0.25))
	)


## The tail: body colour above, a dark finned diffuser below, tail-light housings and a plate.
static func car_rear(color: int) -> PaTexture:
	return TexPaint.paint_texture(44, 12, 6, func(tp: TexPaint) -> void:
		var c := Pal.shade(color, PAINT)
		tp.vgrad(0.0, 0.0, 44.0, 6.4, [Pal.mix(c, Pal.WHITE, 0.18), c])
		tp.rect(0.0, 6.4, 44.0, 5.6, 0xFF0A0810)
		var fx := 4.0
		while fx < 40.0:
			tp.rect(fx, 7.2, 0.7, 4.2, 0xFF1C1A28)
			fx += 4.5
		tp.round(2.5, 2.2, 14.0, 3.4, 1.2, 0xFF3A0810)
		tp.round(27.5, 2.2, 14.0, 3.4, 1.2, 0xFF3A0810)
		tp.round(17.0, 7.4, 10.0, 3.2, 0.6, Pal.WHITE)
		tp.label("TURBO", 22.0, 7.9, 2.4, 0xFF140408, true, true)
	)


## Tinted glass with a sky reflection streak.
static func glass() -> PaTexture:
	if _glass == null:
		_glass = TexPaint.paint_texture(16, 8, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 16.0, 8.0, [Pal.shade(Pal.SKY, 0.55), 0xFF0C1030])
			tp.polygon(PackedFloat32Array([2.0, 0.5, 8.0, 0.5, 5.0, 3.5, 1.5, 3.5]), Pal.with_alpha(Pal.mix(Pal.SKY, Pal.WHITE, 0.4), 0.7))
			tp.polygon(PackedFloat32Array([10.0, 0.5, 12.0, 0.5, 10.0, 3.5, 8.5, 3.5]), Pal.with_alpha(Pal.WHITE, 0.3))
		)
	return _glass


static func tyre() -> PaTexture:
	if _tyre == null:
		_tyre = TexKit.solid(4, 4, 0xFF101018)
	return _tyre


## A wheel's face: black tyre round a silver rim with five spokes.
static func wheel() -> PaTexture:
	if _wheel == null:
		_wheel = TexPaint.paint_texture(16, 16, 6, func(tp: TexPaint) -> void:
			tp.fill(0xFF101018)
			tp.circle(8.0, 8.0, 7.4, 0xFF16161E)
			tp.circle(8.0, 8.0, 5.4, 0xFF9098B0)
			tp.circle(8.0, 8.0, 4.4, 0xFF262838)
			for k in 5:
				var a := k * 1.2566
				tp.line(8.0, 8.0, 8.0 + cos(a) * 4.6, 8.0 + sin(a) * 4.6, 1.1, 0xFFB4BCD0)
			tp.circle(8.0, 8.0, 1.3, 0xFFD0D6E6)
		)
	return _wheel


## A flame or exhaust plume: a hot bell across, fading to nothing along its length (the top is the car's end).
static func plume() -> PaTexture:
	if _plume == null:
		_plume = TexPaint.paint_texture(16, 32, 3, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.hgrad(0.0, 0.0, 16.0, 32.0, [CLEAR_WHITE, Pal.WHITE, CLEAR_WHITE])
			var p := tp.paint.reset()
			p.xfer = PaPaint.Xfer.DST_IN
			p.shader = PaBrush.linear([Pal.WHITE, CLEAR_WHITE], Vector2(0.0, 0.0), Vector2(0.0, 32.0))
			tp.c_draw_rect(0.0, 0.0, 16.0, 32.0, p)
			p.shader = null
			p.xfer = PaPaint.Xfer.NONE
		)
	return _plume


## A puff of tyre smoke: a soft, slightly lumpy grey disc.
static func smoke() -> PaTexture:
	if _smoke == null:
		_smoke = TexPaint.paint_texture(32, 32, 3, func(tp: TexPaint) -> void:
			tp.clear(0)
			for i in 7:
				var x := 16.0 + (MathUtil.hash01(i, 81) - 0.5) * 10.0
				var y := 16.0 + (MathUtil.hash01(i, 82) - 0.5) * 10.0
				tp.radial(x, y, 9.0 + MathUtil.hash01(i, 83) * 4.0, Pal.with_alpha(0xFFB8B4D0, 0.32), 0x00B8B4D0)
		)
	return _smoke


## A glow that fades from a colour at the horizon to nothing towards the sky.
static func horizon() -> PaTexture:
	if _horizon == null:
		_horizon = TexPaint.paint_texture(4, 32, 4, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 4.0, 32.0, [CLEAR_WHITE, Pal.with_alpha(Pal.WHITE, 0.55), Pal.with_alpha(Pal.WHITE, 0.95)])
		)
	return _horizon


## A soft dark streak for tyre marks: dense in the middle, feathered at the sides.
static func skid() -> PaTexture:
	if _skid == null:
		_skid = TexPaint.paint_texture(8, 8, 4, func(tp: TexPaint) -> void:
			tp.hgrad(0.0, 0.0, 8.0, 8.0, [0x00000000, 0xFF000000, 0x00000000])
		)
	return _skid


static func tail_glow() -> PaTexture:
	return TexKit.glow()
