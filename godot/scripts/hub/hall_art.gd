class_name HallArt
extends RefCounted
## hub/HallArt.kt: smooth procedural materials for the arcade hall: the blacklight carpet, walls,
## tiles, wood, metal, glass, signage and prize packaging. Everything is painted once (Kotlin's
## `by lazy`: each texture here is a function that paints it on first call and hands back the same
## [PaTexture] after, allocation-free, so draw code may ask every frame), at a resolution meant for
## a full-HD phone screen. Identical requests share one texture, so models built from them group
## into fewer draws (and are painted and uploaded once, not once per scene or per prop).
##
## Kotlin's file-level colour helpers `dim`, `lift` and `alpha` are [method dim], [method lift] and
## [method alpha] here.

## How many stages the carpet is painted in.
const CARPET_STAGES := 3

static var _keyed := {}
static var _solids := {}
static var _paints := {}

static var _tiles: PaTexture = null
static var _concrete: PaTexture = null
static var _asphalt: PaTexture = null
static var _floor_logo: PaTexture = null
static var _mat: PaTexture = null
static var _wall: PaTexture = null
static var _upper_wall: PaTexture = null
static var _mural: PaTexture = null
static var _mural_city: PaTexture = null
static var _mural_space: PaTexture = null
static var _race_sign: PaTexture = null
static var _ceiling_tiles: PaTexture = null
static var _troffer: PaTexture = null
static var _duct: PaTexture = null
static var _street_backdrop: PaTexture = null
static var _washer: PaTexture = null
static var _brushed_metal: PaTexture = null
static var _dark_metal: PaTexture = null
static var _chrome: PaTexture = null
static var _glass: PaTexture = null
static var _shadow: PaTexture = null
static var _glow: PaTexture = null
static var _beam: PaTexture = null
static var _shaft: PaTexture = null
static var _ticket_stack: PaTexture = null


# ------------------------------------------------------------------ colour helpers

## [param c] darkened to [param f] of its brightness (mixed toward black by 1 - f; alpha kept).
static func dim(c: int, f: float) -> int:
	return Pal.mix_argb(c, 0xFF000000, 1.0 - f)


## [param c] mixed toward white by [param f] (alpha kept).
static func lift(c: int, f: float) -> int:
	return Pal.mix_argb(c, 0xFFFFFFFF, f)


## [param c] with alpha [param a] (0..1).
static func alpha(c: int, a: float) -> int:
	return (clampi(int(a * 255), 0, 255) << 24) | (c & 0xFFFFFF)


static func _done(tp: TexPaint, repeat: bool = false) -> PaTexture:
	var t := tp.to_texture()
	if repeat:
		t.repeat = true
	tp.recycle()
	return t


# ------------------------------------------------------------------ floors

## The classic arcade carpet: black with neon planets, stars, zigzags and squiggles that glow under
## the blacklights. Tiles seamlessly. It is the biggest texture in the hall, so a loading plan paints
## it a third at a time ([method paint_carpet_stage]); asking for it early paints whatever is left.
static func carpet() -> PaTexture:
	return CarpetPainter.finish()


## Paints the next of the carpet's [constant CARPET_STAGES] stages (a loading step); harmless once
## it is done.
static func paint_carpet_stage() -> void:
	CarpetPainter.next()


## Polished dark floor tiles for the entrance and the prize counter.
static func tiles() -> PaTexture:
	if _tiles == null:
		var n := 256
		var tp := TexPaint.new(n, n)
		var cells := 4
		var s := n / float(cells)
		for y in cells:
			for x in cells:
				var base := 0xFF1C1826 if (x + y) % 2 == 0 else 0xFF2A2538
				tp.vgrad(x * s, y * s, s, s, [lift(base, 0.05), base])
		for k in cells + 1:
			tp.rect(k * s - 1.0, 0.0, 2.0, float(n), 0xFF0A0810)
			tp.rect(0.0, k * s - 1.0, float(n), 2.0, 0xFF0A0810)
		tp.grain(0.06, 5)
		_tiles = _done(tp, true)
	return _tiles


## Pavement slabs for the sidewalk out front.
static func concrete() -> PaTexture:
	if _concrete == null:
		var n := 256
		var tp := TexPaint.new(n, n)
		tp.vgrad(0.0, 0.0, float(n), float(n), [0xFF4C4A50, 0xFF424047])
		tp.grain(0.22, 21)
		for k in 7:
			var x := MathUtil.hash01(k, 31) * n
			var y := MathUtil.hash01(k, 32) * n
			tp.radial(x, y, 14.0 + MathUtil.hash01(k, 33) * 26.0, 0x22000000, 0)
		tp.rect(0.0, 0.0, float(n), 3.0, 0xFF2C2B30)
		tp.rect(0.0, 0.0, 3.0, float(n), 0xFF2C2B30)
		_concrete = _done(tp, true)
	return _concrete


## Parking-lot blacktop.
static func asphalt() -> PaTexture:
	if _asphalt == null:
		var n := 256
		var tp := TexPaint.new(n, n)
		tp.fill(0xFF1D1D22)
		tp.grain(0.5, 23)
		_asphalt = _done(tp, true)
	return _asphalt


## Terrazzo medallion set into the floor inside the entrance.
static func floor_logo() -> PaTexture:
	if _floor_logo == null:
		var n := 512
		var c := n / 2.0
		var tp := TexPaint.new(n, n)
		tp.clear(0)
		tp.radial(c, c, c, 0xFF2A1F44, 0xFF1A1330)
		tp.ring(c, c, c - 8.0, 12.0, 0xFFB8B4C8)
		tp.ring(c, c, c - 30.0, 4.0, 0xFF39E6F2)
		# A compass of coloured terrazzo points.
		var colors := [0xFFFF4FA8, 0xFFFFD84D, 0xFF39E6F2, 0xFF9B6BFF]
		for k in 8:
			var a := k * 0.7854
			var r0 := c - 40.0 if k % 2 == 0 else c - 90.0
			var pts := PackedFloat32Array([
				c + cos(a) * r0, c + sin(a) * r0,
				c + cos(a + 0.18) * 90.0, c + sin(a + 0.18) * 90.0,
				c + cos(a - 0.18) * 90.0, c + sin(a - 0.18) * 90.0,
			])
			tp.polygon(pts, dim(colors[k % colors.size()], 0.8))
		tp.circle(c, c, 110.0, 0xFF120C22)
		tp.ring(c, c, 110.0, 5.0, 0xFFFFD84D)
		tp.outlined_text("POCKET", c, c - 8.0, 52.0, 0xFF39E6F2, 0xFF0A0614, 6.0, Fonts.display())
		tp.outlined_text("ARCADE", c, c + 50.0, 52.0, 0xFFFFD84D, 0xFF0A0614, 6.0, Fonts.display())
		tp.grain(0.12, 41)
		_floor_logo = _done(tp)
	return _floor_logo


## Coir entrance mat.
static func mat() -> PaTexture:
	if _mat == null:
		var tp := TexPaint.new(256, 128)
		tp.fill(0xFF2B1F1A)
		tp.grain(0.35, 9)
		tp.stroke_round(6.0, 6.0, 244.0, 116.0, 10.0, 5.0, 0xFFB08040)
		tp.text("WELCOME", 128.0, 82.0, 44.0, 0xFFD9A066, Fonts.display())
		_mat = _done(tp)
	return _mat


# ------------------------------------------------------------------ walls

## Dark wall panels with a rail and baseboard; tiles horizontally.
static func wall() -> PaTexture:
	if _wall == null:
		var tp := TexPaint.new(256, 256)
		tp.vgrad(0.0, 0.0, 256.0, 256.0, [0xFF1A1330, 0xFF120D22])
		for x in range(0, 256, 64):
			tp.rect(float(x), 0.0, 2.0, 200.0, 0xFF0C0818)
			tp.rect(x + 2.0, 0.0, 1.0, 200.0, 0xFF2A2046)
		tp.rect(0.0, 196.0, 256.0, 10.0, 0xFF3A2E5E)
		tp.rect(0.0, 196.0, 256.0, 2.0, 0xFF5A4A8E)
		tp.vgrad(0.0, 226.0, 256.0, 30.0, [0xFF201A30, 0xFF0A0810])
		tp.grain(0.05, 7)
		_wall = _done(tp, true)
	return _wall


## Acoustic panels on the upper walls, fading into the dark towards the ceiling.
static func upper_wall() -> PaTexture:
	if _upper_wall == null:
		var n := 256
		var tp := TexPaint.new(n, n)
		tp.vgrad(0.0, 0.0, float(n), float(n), [0xFF06040C, 0xFF171029])
		for y in 2:
			for x in 2:
				var px := x * 128.0
				var py := y * 128.0
				tp.rect(px + 3.0, py + 3.0, 122.0, 122.0, 0x10FFFFFF)
				tp.rect(px + 3.0, py + 3.0, 122.0, 2.0, 0x14FFFFFF)
				tp.rect(px + 3.0, py + 123.0, 122.0, 2.0, 0x30000000)
		tp.grain(0.04, 17)
		_upper_wall = _done(tp, true)
	return _upper_wall


## A backlit synthwave sunset for the wall above the prize counter.
static func mural() -> PaTexture:
	if _mural == null:
		var w := 512
		var h := 240
		var tp := TexPaint.new(w, h)
		var horizon := h * 0.62
		tp.vgrad(0.0, 0.0, float(w), horizon, [0xFF1A0B3A, 0xFFFF4F8A])
		for i in 40:
			tp.circle(MathUtil.hash01(i, 51) * w, MathUtil.hash01(i, 52) * horizon * 0.6, 1.0 + MathUtil.hash01(i, 53) * 1.2, 0xCCFFFFFF)
		# The sun, sliced by the horizon bands.
		var sun_r := h * 0.3
		tp.save()
		tp.clip_rect(0.0, 0.0, float(w), horizon)
		tp.radial(w / 2.0, horizon - sun_r * 0.35, sun_r * 1.7, 0x66FFB040, 0)
		tp.paint.reset()
		tp.paint.shader = PaBrush.linear([0xFFFFE45A, 0xFFFF3F8E], Vector2(0.0, horizon - sun_r * 1.35), Vector2(0.0, horizon))
		tp.c_draw_circle(w / 2.0, horizon - sun_r * 0.35, sun_r, tp.paint)
		tp.paint.shader = null
		var band := horizon - sun_r * 0.55
		var gap := 3.0
		while band < horizon:
			tp.rect(0.0, band, float(w), gap, 0xFF6A1A6A)
			band += gap + 9.0
			gap += 1.5
		tp.restore()
		# Mountains.
		tp.polygon(PackedFloat32Array([0.0, horizon, 70.0, horizon - 48.0, 130.0, horizon - 18.0, 190.0, horizon - 60.0, 250.0, horizon]), 0xFF2A0F4A)
		tp.polygon(PackedFloat32Array([300.0, horizon, 360.0, horizon - 40.0, 420.0, horizon - 70.0, 470.0, horizon - 30.0, float(w), horizon - 44.0, float(w), horizon]), 0xFF2A0F4A)
		# The grid floor.
		tp.vgrad(0.0, horizon, float(w), h - horizon, [0xFF12062A, 0xFF05020E])
		for k in range(-12, 13):
			tp.line(w / 2.0 + k * 12.0, horizon, w / 2.0 + k * 70.0, float(h), 1.6, 0xFF39E6F2)
		var gy := horizon + 3.0
		var step := 4.0
		while gy < h:
			tp.line(0.0, gy, float(w), gy, 1.6, 0xFFFF4FA8)
			gy += step
			step *= 1.45
		tp.rect(0.0, horizon - 1.0, float(w), 2.0, 0xFFFFB0E0)
		tp.stroke_round(3.0, 3.0, w - 6.0, h - 6.0, 8.0, 6.0, 0xFF2A2440)
		_mural = _done(tp)
	return _mural


## A backlit neon skyline for the upper back wall: towers with lit windows under a big moon.
static func mural_city() -> PaTexture:
	if _mural_city == null:
		var w := 384
		var h := 240
		var tp := TexPaint.new(w, h)
		tp.vgrad(0.0, 0.0, float(w), float(h), [0xFF0A0624, 0xFF2A0E4A, 0xFF5A1A5E])
		for i in 50:
			tp.circle(MathUtil.hash01(i, 71) * w, MathUtil.hash01(i, 72) * h * 0.5, 0.8 + MathUtil.hash01(i, 73), 0xAAFFFFFF)
		tp.radial(w * 0.72, h * 0.3, 70.0, 0x55FFD0F0, 0)
		tp.circle(w * 0.72, h * 0.3, 34.0, 0xFFFFE8F4)
		tp.circle(w * 0.72 - 10.0, h * 0.3 - 6.0, 8.0, 0x22000000)
		var colors := [0xFF39E6F2, 0xFFFF4FA8, 0xFFFFD84D, 0xFF9B6BFF]
		var x := 0.0
		var k := 0
		while x < w:
			var bw := 26.0 + MathUtil.hash01(k, 74) * 34.0
			var bh := 60.0 + MathUtil.hash01(k, 75) * 110.0
			var top := h - bh
			tp.rect(x, top, bw - 3.0, bh, 0xFF100822)
			var c: int = colors[k % colors.size()]
			tp.rect(x, top, bw - 3.0, 2.0, c)
			var wy := top + 8.0
			while wy < h - 14.0:
				var wx := x + 4.0
				while wx < x + bw - 8.0:
					if MathUtil.hash01(int(wx * 7 + wy), k) > 0.45:
						tp.rect(wx, wy, 3.0, 4.0, alpha(lift(c, 0.4), 0.85))
					wx += 7.0
				wy += 9.0
			x += bw
			k += 1
		# Light trails along the freeway at the foot.
		tp.rect(0.0, h - 16.0, float(w), 16.0, 0xFF08040E)
		tp.line(0.0, h - 11.0, float(w), h - 11.0, 2.0, 0xFFFF3B30)
		tp.line(0.0, h - 6.0, float(w), h - 6.0, 2.0, 0xFFFFF4C0)
		tp.stroke_round(3.0, 3.0, w - 6.0, h - 6.0, 8.0, 6.0, 0xFF2A2440)
		_mural_city = _done(tp)
	return _mural_city


## A backlit space scene for the upper back wall: a ringed planet, moons and a rocket.
static func mural_space() -> PaTexture:
	if _mural_space == null:
		var w := 384
		var h := 240
		var tp := TexPaint.new(w, h)
		tp.vgrad(0.0, 0.0, float(w), float(h), [0xFF050418, 0xFF0E1440, 0xFF1A0A3A])
		for i in 90:
			tp.circle(MathUtil.hash01(i, 81) * w, MathUtil.hash01(i, 82) * h, 0.6 + MathUtil.hash01(i, 83) * 1.2, 0xBBFFFFFF)
		tp.radial(w * 0.24, h * 0.44, 110.0, 0x3339E6F2, 0)
		tp.ball(w * 0.24, h * 0.44, 58.0, 0xFF4DA6FF, 0.3)
		tp.save()
		tp.rotate(-18.0, w * 0.24, h * 0.44)
		tp.paint.reset()
		tp.paint.style = PaPaint.Style.STROKE
		tp.paint.stroke_width = 7.0
		tp.paint.color = 0xFFFFD84D
		tp.c_draw_oval(w * 0.24 - 100.0, h * 0.44 - 20.0, w * 0.24 + 100.0, h * 0.44 + 20.0, tp.paint)
		tp.restore()
		tp.ball(w * 0.62, h * 0.22, 16.0, 0xFFFF8A3D)
		tp.ball(w * 0.86, h * 0.7, 26.0, 0xFFFF4FA8)
		# A rocket climbing away with its exhaust.
		var rx := w * 0.66
		var ry := h * 0.62
		tp.line(rx - 30.0, ry + 50.0, rx, ry + 8.0, 10.0, 0x66FF9A3C)
		tp.line(rx - 22.0, ry + 38.0, rx, ry + 8.0, 5.0, 0xCCFFE14D)
		tp.save()
		tp.rotate(35.0, rx, ry)
		tp.oval(rx, ry - 10.0, 8.0, 22.0, 0xFFE8ECF6)
		tp.circle(rx, ry - 14.0, 3.5, 0xFF39E6F2)
		tp.polygon(PackedFloat32Array([rx - 8.0, ry + 2.0, rx - 15.0, ry + 14.0, rx - 6.0, ry + 10.0]), 0xFFE8323C)
		tp.polygon(PackedFloat32Array([rx + 8.0, ry + 2.0, rx + 15.0, ry + 14.0, rx + 6.0, ry + 10.0]), 0xFFE8323C)
		tp.restore()
		tp.stroke_round(3.0, 3.0, w - 6.0, h - 6.0, 8.0, 6.0, 0xFF2A2440)
		_mural_space = _done(tp)
	return _mural_space


## The racers' hung bank sign: a lit box with chequered ends round the neon lettering.
static func race_sign() -> PaTexture:
	if _race_sign == null:
		var w := 640
		var h := 96
		var tp := TexPaint.new(w, h)
		tp.vgrad(0.0, 0.0, float(w), float(h), [0xFF1A0610, 0xFF08020A])
		for end in 2:
			var x0 := 0.0 if end == 0 else w - 96.0
			for x in 6:
				for y in 6:
					if (x + y) % 2 == 0:
						tp.rect(x0 + x * 16.0, y * 16.0, 16.0, 16.0, 0xFFF4F4F4)
		tp.glow_text("TURBO RACEWAY", w / 2.0, h / 2.0 + 20.0, 54.0, 0xFFFFF0F0, 0xFFFF2A3C, 12.0, Fonts.display(), 0.04)
		tp.stroke_round(3.0, 3.0, w - 6.0, h - 6.0, 6.0, 5.0, 0xFFFF3B30)
		_race_sign = _done(tp)
	return _race_sign


# ------------------------------------------------------------------ overhead, seen from the floor

## Dark acoustic ceiling tiles in a grid of thin metal runners; tiles both ways.
static func ceiling_tiles() -> PaTexture:
	if _ceiling_tiles == null:
		var n := 128
		var tp := TexPaint.new(n, n)
		tp.fill(0xFF0B0913)
		for y in 2:
			for x in 2:
				var px := x * 64.0
				var py := y * 64.0
				tp.vgrad(px + 2.0, py + 2.0, 60.0, 60.0, [0xFF15121F, 0xFF100D18])
				# The fissured acoustic surface.
				for k in 26:
					var fx := px + 4.0 + MathUtil.hash01(k + x * 31, y + 5) * 56.0
					var fy := py + 4.0 + MathUtil.hash01(k + y * 17, x + 9) * 56.0
					tp.rect(fx, fy, 2.0 + MathUtil.hash01(k, 3) * 3.0, 1.0, 0xFF0A0810)
		for k in 3:
			tp.rect(k * 64.0 - 1.0, 0.0, 2.0, float(n), 0xFF2A2634)
			tp.rect(0.0, k * 64.0 - 1.0, float(n), 2.0, 0xFF2A2634)
		tp.grain(0.04, 23)
		_ceiling_tiles = _done(tp, true)
	return _ceiling_tiles


## A recessed light panel: a warm diffuser in a slim frame, with a louvred grid.
static func troffer() -> PaTexture:
	if _troffer == null:
		var tp := TexPaint.new(64, 64)
		tp.fill(0xFF3A3642)
		tp.radial(32.0, 32.0, 34.0, 0xFFFFF4DC, 0xFFE8D2A8)
		tp.rect(0.0, 0.0, 64.0, 4.0, 0xFF3A3642)
		tp.rect(0.0, 60.0, 64.0, 4.0, 0xFF3A3642)
		tp.rect(0.0, 0.0, 4.0, 64.0, 0xFF3A3642)
		tp.rect(60.0, 0.0, 4.0, 64.0, 0xFF3A3642)
		for k in range(1, 4):
			tp.rect(4.0 + k * 14.0 - 1.0, 4.0, 2.0, 56.0, 0x55A08A6A)
			tp.rect(4.0, 4.0 + k * 14.0 - 1.0, 56.0, 2.0, 0x55A08A6A)
		_troffer = _done(tp)
	return _troffer


## Galvanised duct: dull metal with seams every so often; wraps round and along.
static func duct() -> PaTexture:
	if _duct == null:
		var tp := TexPaint.new(64, 128)
		tp.vgrad(0.0, 0.0, 64.0, 128.0, [0xFF3A3A44, 0xFF24242C])
		for y in range(0, 128, 32):
			tp.rect(0.0, float(y), 64.0, 3.0, 0xFF52525E)
			tp.rect(0.0, y + 3.0, 64.0, 1.0, 0xFF18181E)
		tp.grain(0.05, 29)
		_duct = _done(tp, true)
	return _duct


## The street across from the entrance at night, seen through the shopfront glass: a dusky sky, a
## row of shops and flats with lit windows, a diner's neon and the glow of the street lamps. Painted
## wide so it fills the glass from anywhere inside.
static func street_backdrop() -> PaTexture:
	if _street_backdrop == null:
		var w := 1024
		var h := 320
		var tp := TexPaint.new(w, h)
		var fw := float(w)
		var fh := float(h)
		tp.vgrad(0.0, 0.0, fw, fh, [0xFF05040E, 0xFF140C2E, 0xFF3A1A4A])
		for i in 90:
			tp.circle(MathUtil.hash01(i, 91) * fw, MathUtil.hash01(i, 92) * fh * 0.4, 0.6 + MathUtil.hash01(i, 93) * 0.8, 0x99FFFFFF)
		tp.radial(fw * 0.83, fh * 0.16, 40.0, 0x44FFF0D0, 0)
		tp.circle(fw * 0.83, fh * 0.16, 14.0, 0xFFFFF4DC)
		# Far skyline.
		var x := 0.0
		var k := 0
		while x < fw:
			var bw := 30.0 + MathUtil.hash01(k, 94) * 50.0
			var bh := 90.0 + MathUtil.hash01(k, 95) * 120.0
			tp.rect(x, fh - bh, bw, bh, 0xFF0E0A1C)
			var wy := fh - bh + 8.0
			while wy < fh - 30.0:
				var wx := x + 5.0
				while wx < x + bw - 6.0:
					if MathUtil.hash01(int(wx * 3 + wy), k + 7) > 0.72:
						tp.rect(wx, wy, 3.0, 4.0, 0xAAFFD890)
					wx += 8.0
				wy += 11.0
			x += bw + 2.0
			k += 1
		# The near row of shops across the street: flat fronts, awnings, lit windows.
		var shop_colors := [0xFF2A1E3A, 0xFF1E2A3A, 0xFF3A2424, 0xFF24322A]
		var awnings := [0xFFE8323C, 0xFF39A0E6, 0xFFFFC83D, 0xFF5CC08A]
		var names := ["DINER", "COMICS", "PIZZA", "TOYS", "BOWL", "DONUTS"]
		x = -20.0
		k = 0
		while x < fw:
			var bw := 120.0 + MathUtil.hash01(k, 96) * 60.0
			var bh := 110.0 + MathUtil.hash01(k, 97) * 40.0
			var top := fh - bh
			var shop: int = shop_colors[k % shop_colors.size()]
			var awning: int = awnings[k % awnings.size()]
			tp.rect(x, top, bw - 4.0, bh, shop)
			tp.rect(x, top, bw - 4.0, 4.0, dim(shop, 0.6))
			# Upstairs windows.
			for c in 3:
				var lit := MathUtil.hash01(k * 3 + c, 98) > 0.35
				tp.rect(x + 12.0 + c * (bw - 28.0) / 3.0, top + 14.0, (bw - 40.0) / 3.0, 22.0, 0xFFFFD890 if lit else 0xFF120E1C)
			# Shop window and awning, the name in neon.
			var sy := fh - 56.0
			tp.rect(x + 8.0, sy, bw - 20.0, 44.0, alpha(lift(awning, 0.55), 0.85))
			tp.rect(x + 8.0, sy - 12.0, bw - 20.0, 12.0, awning)
			for st in range(0, int((bw - 20.0) / 12.0), 2):
				tp.rect(x + 8.0 + st * 12.0, sy - 12.0, 12.0, 12.0, alpha(0xFFFFFFFF, 0.25))
			tp.glow_text(names[k % names.size()], x + bw / 2.0 - 6.0, top + 62.0, 24.0, 0xFFFFFFFF, awnings[(k + 1) % awnings.size()], 7.0, Fonts.display())
			x += bw
			k += 1
		# The far kerb and a line of light where the street lamps pool on it.
		tp.rect(0.0, fh - 10.0, fw, 10.0, 0xFF3A3642)
		for i in 6:
			tp.radial(fw * (0.08 + i * 0.17), fh - 8.0, 60.0, 0x55FFD8A0, 0)
		_street_backdrop = _done(tp)
	return _street_backdrop


## A fan of coloured light thrown up a wall by a floor-level fixture (additive, white).
static func washer() -> PaTexture:
	if _washer == null:
		var tp := TexPaint.new(64, 256)
		for y in 256:
			var t := 1.0 - y / 255.0
			var half := 5.0 + t * 27.0
			var a := (1.0 - t) * (1.0 - t) * 0.5 + 0.02
			tp.hgrad(32.0 - half, float(y), half, 1.0, [0x00FFFFFF, alpha(0xFFFFFFFF, a)])
			tp.hgrad(32.0, float(y), half, 1.0, [alpha(0xFFFFFFFF, a), 0x00FFFFFF])
		_washer = _done(tp)
	return _washer


## A framed poster for the walls, one of four designs. Painted once per design and shared: the nine
## wall spots used to paint nine textures for four pictures.
static func poster(kind: int) -> PaTexture:
	var key := "poster|%d" % (kind & 3)
	var t: PaTexture = _keyed.get(key)
	if t == null:
		t = _paint_poster(kind)
		_keyed[key] = t
	return t


static func _paint_poster(kind: int) -> PaTexture:
	var tp := TexPaint.new(160, 240)
	var palettes := [
		[0xFF2A0F5C, 0xFFFF4FA8, 0xFFFFD84D],
		[0xFF06243A, 0xFF39E6F2, 0xFFFF8A3D],
		[0xFF1C3A12, 0xFF5CF08A, 0xFFFFFFFF],
		[0xFF3A0A14, 0xFFFF5A5A, 0xFFFFE14D],
	]
	var pal: Array = palettes[kind % palettes.size()]
	var bg: int = pal[0]
	var a: int = pal[1]
	var b: int = pal[2]
	tp.fill(0xFF0A0A0A)
	tp.vgrad(8.0, 8.0, 144.0, 224.0, [lift(bg, 0.15), bg])
	var design := kind % 4
	if design == 0:
		# Sunset over a grid.
		tp.circle(80.0, 100.0, 46.0, b)
		for k in 6:
			tp.rect(30.0, 104.0 + k * 9.0, 100.0, 3.0 + k * 0.6, bg)
		for k in 8:
			tp.line(8.0 + k * 21.0, 150.0, 80.0 + (k - 3.5) * 60.0, 232.0, 2.0, a)
		for k in 5:
			tp.line(8.0, 160.0 + k * k * 3.2, 152.0, 160.0 + k * k * 3.2, 2.0, a)
	elif design == 1:
		# Rocket.
		tp.oval(80.0, 110.0, 22.0, 52.0, lift(a, 0.4))
		tp.circle(80.0, 96.0, 10.0, bg)
		tp.polygon(PackedFloat32Array([58.0, 150.0, 80.0, 130.0, 102.0, 150.0, 102.0, 166.0, 58.0, 166.0]), b)
		for k in 30:
			tp.circle(MathUtil.hash01(k, 3) * 160.0, MathUtil.hash01(k, 4) * 230.0, 1.5, 0xFFFFFFFF)
	elif design == 2:
		# Joystick.
		tp.round(40.0, 140.0, 80.0, 44.0, 10.0, dim(a, 0.7))
		tp.line(80.0, 150.0, 80.0, 90.0, 8.0, 0xFF333333)
		tp.ball(80.0, 82.0, 18.0, 0xFFE83A3A)
		tp.ball(104.0, 160.0, 7.0, b)
	else:
		# Lightning.
		tp.polygon(PackedFloat32Array([90.0, 50.0, 50.0, 130.0, 78.0, 130.0, 62.0, 196.0, 112.0, 104.0, 84.0, 104.0, 104.0, 50.0]), b)
	var titles := ["NEON NIGHTS", "STAR BLASTER", "HIGH SCORE", "POWER UP!"]
	tp.glow_text(titles[kind % titles.size()], 80.0, 36.0, 20.0, 0xFFFFFFFF, a, 6.0, Fonts.display())
	tp.text("NOW PLAYING", 80.0, 222.0, 12.0, alpha(0xFFFFFFFF, 0.7), Fonts.condensed())
	return _done(tp)


# ------------------------------------------------------------------ materials

## A flat colour (4 × 4 texels), one shared texture per colour.
static func solid(color: int) -> PaTexture:
	var c := color & 0xFFFFFFFF
	var t: PaTexture = _solids.get(c)
	if t == null:
		t = TexKit.solid(4, 4, c)
		_solids[c] = t
	return t


## A plastic/paint surface: soft vertical gradient with fine grain. One texture per colour and
## shading, shared by whoever asks.
static func paint(color: int, top: float = 0.12, bottom: float = 0.8) -> PaTexture:
	var c := color & 0xFFFFFFFF
	# Kotlin keyed by the colour and the shading in thousandths.
	var key := Vector3i(c, int(top * 1000.0), int(bottom * 1000.0))
	var t: PaTexture = _paints.get(key)
	if t == null:
		var tp := TexPaint.new(64, 64)
		tp.vgrad(0.0, 0.0, 64.0, 64.0, [lift(c, top), dim(c, bottom)])
		tp.grain(0.03, MathUtil.i32(c))
		t = _done(tp)
		_paints[key] = t
	return t


static func brushed_metal() -> PaTexture:
	if _brushed_metal == null:
		var tp := TexPaint.new(128, 128)
		tp.vgrad(0.0, 0.0, 128.0, 128.0, [0xFFC8CAD6, 0xFF8A8C98])
		for y in range(0, 128, 2):
			tp.rect(0.0, float(y), 128.0, 1.0, alpha(0xFFFFFFFF, 0.06 + MathUtil.hash01(y, 3) * 0.08))
		_brushed_metal = _done(tp)
	return _brushed_metal


static func dark_metal() -> PaTexture:
	if _dark_metal == null:
		var tp := TexPaint.new(64, 64)
		tp.vgrad(0.0, 0.0, 64.0, 64.0, [0xFF4A4C58, 0xFF22232A])
		tp.grain(0.05, 2)
		_dark_metal = _done(tp)
	return _dark_metal


static func chrome() -> PaTexture:
	if _chrome == null:
		var tp := TexPaint.new(64, 64)
		tp.vgrad(0.0, 0.0, 64.0, 64.0, [0xFFFFFFFF, 0xFF9AA0B4, 0xFFE8ECF6, 0xFF6A6E7E])
		_chrome = _done(tp)
	return _chrome


## Light wood (counters, skee-ball lanes, benches). One texture per colour and plank count, shared
## by whoever asks.
static func wood(base: int = 0xFFC9884A, planks: int = 6) -> PaTexture:
	var key := "wood|%d|%d" % [base & 0xFFFFFFFF, planks]
	var t: PaTexture = _keyed.get(key)
	if t == null:
		t = _paint_wood(base, planks)
		_keyed[key] = t
	return t


static func _paint_wood(base: int, planks: int) -> PaTexture:
	var tp := TexPaint.new(256, 256)
	var pw := 256.0 / planks
	for i in planks:
		var tone := 0.9 + MathUtil.hash01(i, 5) * 0.2
		var c := lift(base, tone - 1.0) if tone > 1.0 else dim(base, tone)
		tp.rect(i * pw, 0.0, pw, 256.0, c)
		for k in 18:
			var x := i * pw + MathUtil.hash01(i * 31 + k, 6) * pw
			tp.line(x, MathUtil.hash01(k, i + 7) * 256.0, x + (MathUtil.hash01(k, i) - 0.5) * 4.0, MathUtil.hash01(k, i + 9) * 256.0, 1.2, alpha(dim(c, 0.7), 0.35))
		tp.rect(i * pw, 0.0, 1.5, 256.0, dim(base, 0.55))
	tp.grain(0.05, 4)
	return _done(tp, true)


## Clear glass with a faint tint and diagonal reflections (alpha-blended).
static func glass() -> PaTexture:
	if _glass == null:
		var tp := TexPaint.new(128, 128)
		tp.fill(alpha(0xFFB8D8FF, 0.10))
		tp.paint.reset()
		for k in 3:
			var x := 20.0 + k * 40.0
			tp.polygon(PackedFloat32Array([x, 0.0, x + 14.0 - k * 4.0, 0.0, x - 50.0 + 14.0 - k * 4.0, 128.0, x - 50.0, 128.0]), alpha(0xFFFFFFFF, 0.10 - k * 0.02))
		_glass = _done(tp)
	return _glass


static func shadow() -> PaTexture:
	if _shadow == null:
		var tp := TexPaint.new(64, 64)
		tp.radial(32.0, 32.0, 32.0, alpha(0xFF000000, 0.75), 0)
		_shadow = _done(tp)
	return _shadow


static func glow() -> PaTexture:
	if _glow == null:
		var tp := TexPaint.new(64, 64)
		tp.radial(32.0, 32.0, 32.0, 0xFFFFFFFF, 0x00FFFFFF)
		_glow = _done(tp)
	return _glow


## A cone of light seen from the side, for beams under spotlights (additive).
static func beam() -> PaTexture:
	if _beam == null:
		var tp := TexPaint.new(64, 128)
		for y in 128:
			var t := y / 127.0
			var half := 6.0 + t * 26.0
			tp.hgrad(32.0 - half, float(y), half, 1.0, [0x00FFFFFF, alpha(0xFFFFFFFF, 0.22 * (1.0 - t))])
			tp.hgrad(32.0, float(y), half, 1.0, [alpha(0xFFFFFFFF, 0.22 * (1.0 - t)), 0x00FFFFFF])
		_beam = _done(tp)
	return _beam


## A spotlight's shaft through the haze (additive): narrow and brightest at the lens, spreading and
## thinning towards the floor, with soft edges.
static func shaft() -> PaTexture:
	if _shaft == null:
		var tp := TexPaint.new(64, 256)
		for y in 256:
			var t := y / 255.0
			var half := 3.0 + t * 29.0
			var a := 0.42 * (1.0 - t * 0.7) * (0.35 + 0.65 * minf(1.0, y / 12.0))
			tp.hgrad(32.0 - half, float(y), half, 1.0, [0x00FFFFFF, alpha(0xFFFFFFFF, a)])
			tp.hgrad(32.0, float(y), half, 1.0, [alpha(0xFFFFFFFF, a), 0x00FFFFFF])
		_shaft = _done(tp)
	return _shaft


# ------------------------------------------------------------------ signs

## A neon sign: glowing tube lettering on a transparent background (drawn additively, so the bloom
## makes it bleed light like the real thing).
static func neon(text: String, color: int, w: int = 512, h: int = 128, size: float = 86.0) -> PaTexture:
	var key := "neon|%s|%d|%d|%d|%s" % [text, color & 0xFFFFFFFF, w, h, size]
	var t: PaTexture = _keyed.get(key)
	if t == null:
		t = _paint_neon(text, color, w, h, size)
		_keyed[key] = t
	return t


static func _paint_neon(text: String, color: int, w: int, h: int, size: float) -> PaTexture:
	var tp := TexPaint.new(w, h)
	tp.clear(0)
	tp.glow(18.0, alpha(color, 0.55), func(g: TexPaint) -> void:
		g.text(text, w / 2.0, h / 2.0 + size * 0.36, size, 0xFFFFFFFF, Fonts.display(), TexPaint.ALIGN_CENTER, 0.06))
	tp.paint.reset()
	tp.paint.typeface = Fonts.display()
	tp.paint.text_size = size
	tp.paint.text_align = PaPaint.Align.CENTER
	tp.paint.letter_spacing = 0.06
	tp.paint.style = PaPaint.Style.STROKE
	tp.paint.stroke_width = size * 0.08
	tp.paint.color = lift(color, 0.35)
	tp.c_draw_text(text, w / 2.0, h / 2.0 + size * 0.36, tp.paint)
	tp.paint.stroke_width = size * 0.03
	tp.paint.color = lift(color, 0.85)
	tp.c_draw_text(text, w / 2.0, h / 2.0 + size * 0.36, tp.paint)
	return _done(tp)


## A lit box sign: coloured panel with bold lettering.
static func lightbox(text: String, bg: int, fg: int, w: int = 512, h: int = 128, size: float = 70.0) -> PaTexture:
	var key := "box|%s|%d|%d|%d|%d|%s" % [text, bg & 0xFFFFFFFF, fg & 0xFFFFFFFF, w, h, size]
	var t: PaTexture = _keyed.get(key)
	if t == null:
		t = _paint_lightbox(text, bg, fg, w, h, size)
		_keyed[key] = t
	return t


static func _paint_lightbox(text: String, bg: int, fg: int, w: int, h: int, size: float) -> PaTexture:
	var tp := TexPaint.new(w, h)
	tp.vgrad(0.0, 0.0, float(w), float(h), [lift(bg, 0.25), dim(bg, 0.75)])
	tp.stroke_round(4.0, 4.0, w - 8.0, h - 8.0, 10.0, 5.0, alpha(0xFFFFFFFF, 0.5))
	tp.outlined_text(text, w / 2.0, h / 2.0 + size * 0.36, size, fg, dim(bg, 0.35), size * 0.12, Fonts.display())
	return _done(tp)


# ------------------------------------------------------------------ prizes

## Printed packaging for the boxed prizes on the prize wall.
static func prize_box(seed_v: int) -> PaTexture:
	var key := "prize|%d" % seed_v
	var t: PaTexture = _keyed.get(key)
	if t == null:
		t = _paint_prize_box(seed_v)
		_keyed[key] = t
	return t


static func _paint_prize_box(seed_v: int) -> PaTexture:
	var tp := TexPaint.new(128, 128)
	var colors := [0xFFE8394A, 0xFF2F6BFF, 0xFF22C06A, 0xFFFFB020, 0xFF9B4DFF, 0xFF00C2D8]
	var c: int = colors[seed_v % colors.size()]
	tp.vgrad(0.0, 0.0, 128.0, 128.0, [lift(c, 0.2), dim(c, 0.7)])
	tp.round(12.0, 20.0, 104.0, 70.0, 10.0, alpha(0xFFFFFFFF, 0.85))
	var names := ["ROBOT", "RC CAR", "BLASTER", "DRONE", "YO-YO", "SLIME", "LASER", "PUZZLE"]
	tp.text(names[seed_v % names.size()], 64.0, 66.0, 22.0, dim(c, 0.5), Fonts.display())
	tp.ball(96.0, 104.0, 12.0, colors[(seed_v + 2) % colors.size()])
	tp.text("TOYS", 36.0, 116.0, 16.0, 0xFFFFFFFF, Fonts.condensed())
	return _done(tp)


static func ticket_stack() -> PaTexture:
	if _ticket_stack == null:
		var tp := TexPaint.new(64, 64)
		tp.fill(0xFFFFA23C)
		for y in range(0, 64, 6):
			tp.rect(0.0, float(y), 64.0, 1.5, 0xFFE07A20)
		_ticket_stack = _done(tp)
	return _ticket_stack


## hub/HallArt.kt CarpetPainter: paints [method HallArt.carpet] in stages so a loading screen can
## keep moving between them.
class CarpetPainter:
	const N := 512
	const COLORS := [0xFF39E6F2, 0xFFFF4FA8, 0xFFFFD84D, 0xFF9B6BFF, 0xFF5CF08A, 0xFFFF8A3D]
	static var _painter: TexPaint = null
	static var _stage := 0
	static var _texture: PaTexture = null

	## Calls [param draw] with (cx, cy) for each copy of a motif of radius [param r] at (x, y),
	## wrapped round the edges so the pattern tiles.
	static func _wrapped(x: float, y: float, r: float, draw: Callable) -> void:
		for ox in range(-1, 2):
			for oy in range(-1, 2):
				var cx := x + ox * N
				var cy := y + oy * N
				if cx + r < 0 or cx - r > N or cy + r < 0 or cy - r > N:
					continue
				draw.call(cx, cy)

	static func next() -> void:
		if _texture != null:
			return
		if _painter == null:
			_painter = TexPaint.new(N, N)
		var tp := _painter
		if _stage == 0:
			tp.fill(0xFF0B0816)
			tp.grain(0.35, 3)
			# Worn, mottled pile: broad soft patches a shade lighter or darker.
			for i in 18:
				var r := 50.0 + MathUtil.hash01(i, 61) * 70.0
				var c := 0x0E6A4AB0 if i % 2 == 0 else 0x16000000
				_wrapped(MathUtil.hash01(i, 62) * N, MathUtil.hash01(i, 63) * N, r, func(cx: float, cy: float) -> void: tp.radial(cx, cy, r, c, 0))
		elif _stage == 1:
			# Blacklight bleed: each motif's ink glows faintly into the fibres around it.
			for i in 46:
				var size := 10.0 + MathUtil.hash01(i, 13) * 18.0
				var halo := HallArt.alpha(COLORS[i % COLORS.size()], 0.16)
				_wrapped(MathUtil.hash01(i, 11) * N, MathUtil.hash01(i, 12) * N, size * 1.7, func(cx: float, cy: float) -> void: tp.radial(cx, cy, size * 1.7, halo, 0))
			for i in 46:
				var x := MathUtil.hash01(i, 11) * N
				var y := MathUtil.hash01(i, 12) * N
				var c := HallArt.dim(COLORS[i % COLORS.size()], 0.72)
				var kind := i % 7
				var size := 10.0 + MathUtil.hash01(i, 13) * 18.0
				var rot := MathUtil.hash01(i, 14) * 6.28
				var ring_c := HallArt.dim(COLORS[(i + 2) % COLORS.size()], 0.72)
				_wrapped(x, y, size * 2.0, func(cx: float, cy: float) -> void: _motif(tp, kind, cx, cy, size, rot, c, ring_c))
		else:
			# Tiny stars everywhere, a few with a glint of their own.
			for i in 400:
				var x := MathUtil.hash01(i, 21) * N
				var y := MathUtil.hash01(i, 22) * N
				if i % 9 == 0:
					tp.radial(x, y, 6.0, HallArt.alpha(COLORS[i % COLORS.size()], 0.22), 0)
				tp.circle(x, y, 0.8 + MathUtil.hash01(i, 23) * 1.2, HallArt.alpha(COLORS[i % COLORS.size()], 0.7))
			# Fine fibre speckle over everything.
			tp.grain(0.12, 29)
			_texture = tp.to_texture()
			_texture.repeat = true
			tp.recycle()
			_painter = null
		_stage += 1

	static func _motif(tp: TexPaint, kind: int, cx: float, cy: float, size: float, rot: float, c: int, ring_c: int) -> void:
		if kind == 0:
			# Ringed planet.
			tp.circle(cx, cy, size * 0.6, c)
			tp.circle(cx - size * 0.15, cy - size * 0.15, size * 0.22, HallArt.lift(c, 0.4))
			tp.save()
			tp.rotate(rot * 57.3, cx, cy)
			tp.oval(cx, cy, size * 1.25, size * 0.32, 0)
			tp.paint.reset()
			tp.paint.style = PaPaint.Style.STROKE
			tp.paint.stroke_width = 3.0
			tp.paint.color = ring_c
			tp.c_draw_oval(cx - size * 1.25, cy - size * 0.32, cx + size * 1.25, cy + size * 0.32, tp.paint)
			tp.restore()
		elif kind == 1:
			# Star.
			var pts := PackedFloat32Array()
			pts.resize(20)
			for k in 10:
				var a := rot + k * PI / 5.0
				var rr := size * 0.8 if k % 2 == 0 else size * 0.35
				pts[k * 2] = cx + cos(a) * rr
				pts[k * 2 + 1] = cy + sin(a) * rr
			tp.polygon(pts, c)
		elif kind == 2:
			# Zigzag.
			var px := cx - size
			var py := cy
			for k in 5:
				var nx := px + size * 0.45
				var ny := cy + (-size * 0.35 if k % 2 == 0 else size * 0.35)
				tp.line(px, py, nx, ny, 3.5, c)
				px = nx
				py = ny
		elif kind == 3:
			tp.ring(cx, cy, size * 0.55, 3.5, c)
		elif kind == 4:
			# Triangle outline.
			var pts := PackedFloat32Array()
			pts.resize(6)
			for k in 3:
				var a := rot + k * 2.094
				pts[k * 2] = cx + cos(a) * size * 0.7
				pts[k * 2 + 1] = cy + sin(a) * size * 0.7
			for k in 3:
				var k2 := (k + 1) % 3
				tp.line(pts[k * 2], pts[k * 2 + 1], pts[k2 * 2], pts[k2 * 2 + 1], 3.0, c)
		elif kind == 5:
			# Squiggle.
			var px := cx - size
			var py := cy
			for k in range(1, 13):
				var nx := cx - size + k * size / 6.0
				var ny := cy + sin(k * 1.1 + rot) * size * 0.3
				tp.line(px, py, nx, ny, 3.0, c)
				px = nx
				py = ny
		else:
			# Sparkle dots.
			for k in 5:
				var a := rot + k * 1.256
				tp.circle(cx + cos(a) * size * 0.6, cy + sin(a) * size * 0.6, 2.2, c)

	## The finished carpet, painting whatever stages are left first.
	static func finish() -> PaTexture:
		while _texture == null:
			next()
		return _texture
