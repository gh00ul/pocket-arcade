class_name FishingArt
extends RefCounted
## games/fishing/FishingArt.kt: painted textures and models for the fishing pond and its tub cabinet.
## Everything is built lazily on first use (Kotlin's `by lazy`; here a function per texture or
## model), from render or cabinet-build paths, so headless tests never paint anything.

const K := preload("res://scripts/games/fishing/fishing_tuning.gd")

## Fish skins: back colour, belly colour and markings per species (see FishingTuning.NAMES).
const BACKS: Array[int] = [0xFF6E8A2A, 0xFF3E6A38, 0xFF4E4A44, 0xFFE0A020]
const BELLIES: Array[int] = [0xFFF2D68A, 0xFFD8E0B0, 0xFFB8B0A0, 0xFFFFE890]
const FINS: Array[int] = [0xFFE07A2A, 0xFF5A7A48, 0xFF5A524A, 0xFFFF9A20]

static var _water: Region = null
static var _caustics: Region = null
static var _bed: Region = null
static var _lily_pad: Region = null
static var _flower: Region = null
static var _ripple: Region = null
static var _target: Region = null
static var _tree: Region = null
static var _reeds: Region = null
static var _treeline: Region = null
static var _fog: Region = null
static var _shaft: Region = null
static var _haze: Region = null
static var _planks: Region = null
static var _rod: Region = null
static var _cork: Region = null
static var _tub_tiles: Region = null
static var _tub_floor: Region = null
static var _tub_water: Region = null
static var _canopy: Region = null
static var _fish_models: Array = [null, null, null, null]
static var _boot: Model = null
static var _bobber: Model = null


## Deep pond water with lighter wave streaks; tiles in both directions.
static func water() -> Region:
	if _water == null:
		_water = TexPaint.paint_texture(128, 128, 2, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 128.0, 128.0, [0xFF1C6E7A, 0xFF155E6E, 0xFF1C6E7A])
			for k in 26:
				var x := MathUtil.hash01(k, 41) * 128.0
				var y := MathUtil.hash01(k, 42) * 128.0
				var len := 10.0 + MathUtil.hash01(k, 43) * 22.0
				var a := Pal.with_alpha(0xFF8FE3E0, 0.16 + MathUtil.hash01(k, 44) * 0.18)
				for o in range(-1, 2):
					tp.line(x - len / 2.0 + o * 128.0, y, x + len / 2.0 + o * 128.0, y + (MathUtil.hash01(k, 45) - 0.5) * 3.0, 1.1, a)
					tp.line(x - len / 2.0, y + o * 128.0, x + len / 2.0, y + o * 128.0 + (MathUtil.hash01(k, 45) - 0.5) * 3.0, 1.1, a)
		).region(0, 0, -1, -1, true)
	return _water


## Bright caustic web on black, added over the water and scrolled.
static func caustics() -> Region:
	if _caustics == null:
		_caustics = TexPaint.paint_texture(128, 128, 2, func(tp: TexPaint) -> void:
			tp.clear(0xFF000000)
			for k in 40:
				var cx := MathUtil.hash01(k, 51) * 128.0
				var cy := MathUtil.hash01(k, 52) * 128.0
				var r := 6.0 + MathUtil.hash01(k, 53) * 12.0
				for ox in range(-1, 2):
					for oy in range(-1, 2):
						tp.ring(cx + ox * 128.0, cy + oy * 128.0, r, 0.9 + MathUtil.hash01(k, 54) * 0.8, Pal.with_alpha(Pal.WHITE, 0.35 + MathUtil.hash01(k, 55) * 0.4))
		).region(0, 0, -1, -1, true)
	return _caustics


## Muddy pond bed with weed and pebbles.
static func bed() -> Region:
	if _bed == null:
		_bed = TexPaint.paint_texture(128, 128, 2, func(tp: TexPaint) -> void:
			tp.fill(0xFF2E4A36)
			for k in 90:
				var x := MathUtil.hash01(k, 61) * 128.0
				var y := MathUtil.hash01(k, 62) * 128.0
				var c := 0xFF5E6B48 if k % 3 == 0 else (0xFF22382A if k % 3 == 1 else 0xFF7A7458)
				tp.oval(x, y, 1.5 + MathUtil.hash01(k, 63) * 3.0, 1.0 + MathUtil.hash01(k, 64) * 2.0, c)
			tp.grain(0.08, 6)
		).region(0, 0, -1, -1, true)
	return _bed


## Grass all round the pond with the pond itself cut out: an ellipse touching the edges of the pond's
## rectangle, which the game maps over the pond inside a bigger square
## ([param out_x0]..[param out_x1] × [param out_z0]..[param out_z1]).
static func bank(out_x0: float, out_z0: float, out_x1: float, out_z1: float, cx: float, cz: float, rx: float, rz: float) -> PaTexture:
	var n := 256
	var sx := n / (out_x1 - out_x0)
	var sz := n / (out_z1 - out_z0)
	var ux := (cx - out_x0) * sx
	var vz := (cz - out_z0) * sz
	return TexPaint.paint_texture(n, n, 2, func(tp: TexPaint) -> void:
		var nf := float(n)
		tp.vgrad(0.0, 0.0, nf, nf, [0xFF3E7A34, 0xFF4E9440])
		for k in 700:
			var x := MathUtil.hash01(k, 71) * nf
			var y := MathUtil.hash01(k, 72) * nf
			var c := 0xFF6DB84E if k % 4 == 0 else (0xFF2F5E28 if k % 4 == 1 else 0xFF4A8C3C)
			tp.line(x, y, x + (MathUtil.hash01(k, 73) - 0.5) * 2.0, y - 2.5, 0.6, c)
		# A muddy, reedy rim, then the water hole.
		tp.oval(ux, vz, rx * sx + 6.0, rz * sz + 6.0, 0xFF5A4A30)
		tp.oval(ux, vz, rx * sx + 2.5, rz * sz + 2.5, 0xFF3F3524)
		_punch_oval(tp, ux, vz, rx * sx, rz * sz)
		# Stones along the rim.
		for k in 40:
			var a := MathUtil.hash01(k, 74) * 6.283
			var px := ux + cos(a) * (rx * sx + 4.0)
			var py := vz + sin(a) * (rz * sz + 4.0)
			tp.oval(px, py, 1.5 + MathUtil.hash01(k, 75) * 1.5, 1.0 + MathUtil.hash01(k, 76), 0xFF8C8878))


## Android's CLEAR transfer mode over a filled oval: a transparent hole (CLEAR ignores the colour).
static func _punch_oval(tp: TexPaint, cx: float, cy: float, rx: float, ry: float) -> void:
	var p := PaPaint.new(0)
	p.xfer = PaPaint.Xfer.CLEAR
	tp.c_draw_oval(cx - rx, cy - ry, cx + rx, cy + ry, p)


## A lily pad seen from above. build-13 cut its notch with the canvas paint still set up by the vein
## lines (stroke, 0.6 wide), so the CLEAR path only strokes the notch's outline: a thin cut, kept.
static func lily_pad() -> Region:
	if _lily_pad == null:
		_lily_pad = TexPaint.paint_texture(48, 48, 3, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.circle(24.0, 24.0, 22.0, 0xFF2F7A32)
			tp.radial(20.0, 20.0, 20.0, 0xFF5FB04A, 0xFF2F7A32)
			for k in 9:
				var a := k * 0.7 + 0.4
				tp.line(24.0, 24.0, 24.0 + cos(a) * 20.0, 24.0 + sin(a) * 20.0, 0.6, 0xFF7ACB5A)
			var p := PaPaint.new(0)
			p.style = PaPaint.Style.STROKE
			p.stroke_width = 0.6
			p.round_cap = true
			p.xfer = PaPaint.Xfer.CLEAR
			var path := PaPath.new()
			path.move_to(24.0, 24.0)
			path.line_to(48.0, 16.0)
			path.line_to(48.0, 32.0)
			path.close()
			tp.c_draw_path(path, p)
		).full()
	return _lily_pad


## A pink water-lily flower, a small sprite.
static func flower() -> Region:
	if _flower == null:
		_flower = TexPaint.paint_texture(32, 32, 3, func(tp: TexPaint) -> void:
			tp.clear(0)
			for k in 8:
				var a := k * PI / 4.0
				tp.oval(16.0 + cos(a) * 7.0, 16.0 + sin(a) * 7.0, 5.5, 5.5, 0xFFFF8FC8 if k % 2 == 0 else 0xFFFFB8DC)
			tp.circle(16.0, 16.0, 5.0, 0xFFFFE14D)
		).full()
	return _flower


## Expanding ring for ripples (added).
static func ripple() -> Region:
	if _ripple == null:
		_ripple = TexPaint.paint_texture(64, 64, 2, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.ring(32.0, 32.0, 28.0, 3.0, Pal.with_alpha(Pal.WHITE, 0.9))
			tp.ring(32.0, 32.0, 24.0, 1.5, Pal.with_alpha(Pal.WHITE, 0.35))
		).full()
	return _ripple


## Target ring shown on the water while charging a cast.
static func target() -> Region:
	if _target == null:
		_target = TexPaint.paint_texture(64, 64, 2, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.ring(32.0, 32.0, 27.0, 4.0, Pal.WHITE)
			tp.ring(32.0, 32.0, 13.0, 3.0, Pal.WHITE)
			tp.circle(32.0, 32.0, 3.0, Pal.WHITE)
		).full()
	return _target


## A leafy tree for the far bank (billboard).
static func tree() -> Region:
	if _tree == null:
		_tree = TexPaint.paint_texture(96, 128, 2, func(tp: TexPaint) -> void:
			tp.clear(0)
			tp.rect(44.0, 70.0, 8.0, 58.0, 0xFF4A3220)
			tp.rect(46.0, 70.0, 2.0, 58.0, 0xFF6A4A30)
			var greens := [0xFF1F5A2A, 0xFF2E7A36, 0xFF3F9444, 0xFF5AAE50]
			for layer in 4:
				for k in 9:
					var x := 48.0 + (MathUtil.hash01(k, 81 + layer) - 0.5) * (60.0 - layer * 8.0)
					var y := 58.0 + (MathUtil.hash01(k, 91 + layer) - 0.5) * (72.0 - layer * 10.0) - layer * 4.0
					tp.circle(x, y, 17.0 - layer * 2.5, greens[layer])
		).full()
	return _tree


## Cattails and reeds along the bank (billboard).
static func reeds() -> Region:
	if _reeds == null:
		_reeds = TexPaint.paint_texture(64, 64, 3, func(tp: TexPaint) -> void:
			tp.clear(0)
			for k in 14:
				var x := 4.0 + MathUtil.hash01(k, 101) * 56.0
				var top := 8.0 + MathUtil.hash01(k, 102) * 28.0
				var lean := (MathUtil.hash01(k, 103) - 0.5) * 10.0
				tp.line(x, 64.0, x + lean, top, 1.3, 0xFF4E8A3A if k % 2 == 0 else 0xFF6FA848)
				if k % 3 == 0:
					tp.oval(x + lean * 0.9, top + 6.0, 1.8, 5.0, 0xFF6A3E1E)
		).full()
	return _reeds


## The distant tree line and hills behind the pond.
static func treeline() -> Region:
	if _treeline == null:
		_treeline = TexPaint.paint_texture(256, 64, 2, func(tp: TexPaint) -> void:
			tp.clear(0)
			for k in 3:
				var base := 0xFF2A5E48
				var c := Pal.mix(base, 0xFF8FC0C8, 0.45 - k * 0.18)
				for i in 40:
					var x := i * 7.0 + MathUtil.hash01(i, 111 + k) * 6.0
					var r := 7.0 + MathUtil.hash01(i, 121 + k) * 9.0
					tp.circle(x, 30.0 + k * 8.0 - MathUtil.hash01(i, 131 + k) * 10.0, r, c)
				tp.rect(0.0, 34.0 + k * 8.0, 256.0, 30.0, c)
		).full()
	return _treeline


# ------------------------------------------------------------------ light and air (no bitmaps)

## A drifting bank of mist: it tiles sideways (whole numbers of waves across the width) and fades to
## nothing along the top and bottom edges, so a quad of it has no visible border. Alpha only; tint it
## when drawing.
static func fog() -> Region:
	if _fog == null:
		var w := 128
		var h := 32
		var px := PackedInt32Array()
		px.resize(w * h)
		var tau := 2.0 * PI
		for y in h:
			for x in w:
				var u := x / float(w)
				var v := (y + 0.5) / h
				var n := 0.5 + 0.22 * sin(tau * 2.0 * u + 0.7 + v * 1.9) + 0.16 * sin(tau * 5.0 * u + 2.4 - v * 2.6) + 0.1 * sin(tau * 11.0 * u + 4.1)
				var edge := clampf(sin(v * PI), 0.0, 1.0)
				var a := clampf((n - 0.28) * 1.6, 0.0, 1.0) * edge * edge
				px[y * w + x] = (clampi(int(a * 255.0), 0, 255) << 24) | 0xFFFFFF
		var t := PaTexture.from_argb(w, h, px)
		t.repeat = true
		_fog = t.region(0, 0, -1, -1, true)
	return _fog


## A beam of low sun: soft at both sides, brightest near its top (the sun's end, v = 0) and fading
## away down its length. Alpha only, for additive drawing.
static func shaft() -> Region:
	if _shaft == null:
		var w := 16
		var h := 32
		var px := PackedInt32Array()
		px.resize(w * h)
		for y in h:
			for x in w:
				var v := (y + 0.5) / h
				var across := clampf(1.0 - absf((x + 0.5) / w * 2.0 - 1.0), 0.0, 1.0)
				var a := across * across * (1.0 - v) * (1.0 - v * 0.4)
				px[y * w + x] = (clampi(int(a * 255.0), 0, 255) << 24) | 0xFFFFFF
		_shaft = PaTexture.from_argb(w, h, px).full()
	return _shaft


## A band of warm horizon haze: brightest a little below its top, fading out both ways (alpha).
static func haze() -> Region:
	if _haze == null:
		var h := 24
		var px := PackedInt32Array()
		px.resize(4 * h)
		for y in h:
			for x in 4:
				var v := (y + 0.5) / h
				var a := clampf(sin(v * PI), 0.0, 1.0)
				px[y * 4 + x] = (clampi(int((a * a) * 255.0), 0, 255) << 24) | 0xFFFFFF
		_haze = PaTexture.from_argb(4, h, px).full()
	return _haze


## Weathered dock planks, running away from the viewer.
static func planks() -> Region:
	if _planks == null:
		_planks = TexPaint.paint_texture(128, 128, 2, func(tp: TexPaint) -> void:
			var base := 0xFF9A6A3E
			for i in 8:
				var tone := 0.82 + MathUtil.hash01(i, 141) * 0.25
				var c := Pal.shade(base, minf(tone, 1.0))
				tp.rect(i * 16.0, 0.0, 16.0, 128.0, c)
				for k in 8:
					var gx := i * 16.0 + 2.0 + MathUtil.hash01(i * 13 + k, 142) * 12.0
					tp.line(gx, MathUtil.hash01(k, i + 143) * 128.0, gx + 0.4, MathUtil.hash01(k, i + 144) * 128.0, 0.4, Pal.with_alpha(Pal.shade(c, 0.7), 0.6))
				tp.rect(i * 16.0, 0.0, 1.2, 128.0, 0xFF3A2614)
				tp.circle(i * 16.0 + 4.0, 10.0, 0.9, 0xFF2A1A10)
				tp.circle(i * 16.0 + 12.0, 10.0, 0.9, 0xFF2A1A10)
			tp.grain(0.07, 9)
		).region(0, 0, -1, -1, true)
	return _planks


## The rod blank: dark glossy blue, lit down its middle (beams map u across the rod).
static func rod() -> Region:
	if _rod == null:
		_rod = TexPaint.paint_texture(8, 4, 4, func(tp: TexPaint) -> void:
			tp.hgrad(0.0, 0.0, 8.0, 4.0, [0xFF0E1838, 0xFF4A6AC8, 0xFF101C40])
		).full()
	return _rod


static func cork() -> Region:
	if _cork == null:
		_cork = TexPaint.paint_texture(16, 16, 2, func(tp: TexPaint) -> void:
			tp.hgrad(0.0, 0.0, 16.0, 16.0, [0xFF8A6238, 0xFFD8A870, 0xFF7A5430])
			for k in 30:
				tp.circle(MathUtil.hash01(k, 151) * 16.0, MathUtil.hash01(k, 152) * 16.0, 0.5, 0xFF6A4A28)
		).full()
	return _cork


static func line() -> Region:
	return TexKit.white().full()


# ------------------------------------------------------------------ models

## The skin of [param species]: u runs round the body (one flank at 0 and 32, the belly at 16, the
## back at 48); v runs from the nose (0) to the tail (32).
static func _skin(species: int) -> PaTexture:
	return TexPaint.paint_texture(64, 32, 2, func(tp: TexPaint) -> void:
		var back := BACKS[species]
		var belly := BELLIES[species]
		var side := Pal.mix(back, belly, 0.5)
		tp.hgrad(0.0, 0.0, 64.0, 32.0, [side, belly, side, back, side])
		if species == 0:
			for k in 5:
				tp.rect(34.0, 6.0 + k * 5.0, 28.0, 2.2, 0xFF2E3A18)
		elif species == 1:
			tp.rect(30.0, 4.0, 4.0, 28.0, 0xFF1E3A1A)
			tp.rect(0.0, 4.0, 2.0, 28.0, 0xFF1E3A1A)
			tp.rect(62.0, 4.0, 2.0, 28.0, 0xFF1E3A1A)
		elif species == 2:
			for k in 30:
				tp.circle(MathUtil.hash01(k, 161) * 64.0, MathUtil.hash01(k, 162) * 32.0, 0.8, 0xFF2A2420)
		elif species == 3:
			for k in 24:
				tp.circle(MathUtil.hash01(k, 163) * 64.0, MathUtil.hash01(k, 164) * 32.0, 1.2, 0xFFFFF4B0)
		# Eyes just behind the nose, one on each flank.
		tp.circle(36.0, 3.5, 1.7, Pal.WHITE)
		tp.circle(60.0, 3.5, 1.7, Pal.WHITE)
		tp.circle(36.0, 3.5, 1.0, Pal.BLACK)
		tp.circle(60.0, 3.5, 1.0, Pal.BLACK))


## A fish [param species], nose along +z, centred on the origin: a lathed body flattened side to
## side, a forked tail and a dorsal fin. One model per species; sizes vary by scale.
static func fish(species: int) -> Model:
	var cached: Variant = _fish_models[species]
	if cached != null:
		return cached
	var len: float = K.LENGTH[species]
	var tex := _skin(species)
	# Body along +y (tail at 0, nose at len), then laid along +z.
	var prof := PackedFloat32Array([0.0, 0.0, 0.06, 0.05, 0.12, 0.2, 0.18, 0.42, 0.19, 0.6, 0.16, 0.78, 0.1, 0.92, 0.0, 1.0])
	for i in prof.size():
		prof[i] *= len
	var body := ModelBuilder.new().lathe(0.0, 0.0, 0.0, prof, 10, tex.full(), -1, 0.6).build()
	var fin_tex := TexKit.solid(4, 4, FINS[species]).full()
	var b := ModelBuilder.new()
	var laid := ModelBuilder.new().add_xf(body, Xform.new().set_xf(0.0, 0.0, -len / 2.0, 0.0, PI / 2.0)).build()
	b.add_scaled(laid, 0.55, 0.85, 1.0)
	# Tail fin: a forked vertical fan behind the body.
	var tz := -len / 2.0
	b.quad(0.0, len * 0.26, tz - len * 0.24, 0.0, 0.0, tz + len * 0.04, 0.0, 0.0, tz + len * 0.04, 0.0, -len * 0.26, tz - len * 0.24,
		fin_tex, 1.0, 0.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, false)
	b.quad(0.0, len * 0.26, tz - len * 0.24, 0.0, 0.02, tz - len * 0.1, 0.0, -0.02, tz - len * 0.1, 0.0, -len * 0.26, tz - len * 0.24,
		fin_tex, 1.0, 0.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, false)
	# Dorsal fin.
	b.quad(0.0, len * 0.26, -len * 0.05, 0.0, len * 0.14, len * 0.16, 0.0, len * 0.1, len * 0.16, 0.0, len * 0.12, -len * 0.12,
		fin_tex, 1.0, 0.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, false)
	var model := b.build()
	_fish_models[species] = model
	return model


## The old boot: a scuffed leather boot lying on its side, toe along +z.
static func boot() -> Model:
	if _boot == null:
		var leather := TexPaint.paint_texture(32, 32, 2, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 32.0, 32.0, [0xFF6A4424, 0xFF3A2414])
			tp.grain(0.12, 3)).full()
		var sole := TexKit.solid(4, 4, 0xFF1E1812).full()
		var f := BoxFaces.new(leather, leather, leather, leather, leather)
		_boot = ModelBuilder.new() \
			.box(-3.5, 0.0, -7.0, 3.5, 13.0, -1.0, f) \
			.box(-3.5, 0.0, -1.0, 3.5, 5.5, 7.0, f) \
			.box(-3.7, -0.8, -7.2, 3.7, 0.0, 7.2, BoxFaces.new(sole, sole, sole, sole, sole)) \
			.torus(0.0, 13.0, -4.0, 3.3, 0.6, sole, 10, 4) \
			.build()
	return _boot


## The float: red cap over a white belly with an antenna on top.
static func bobber() -> Model:
	if _bobber == null:
		var tex := TexPaint.paint_texture(16, 16, 2, func(tp: TexPaint) -> void:
			tp.rect(0.0, 0.0, 16.0, 8.0, 0xFFFF3B30)
			tp.rect(0.0, 8.0, 16.0, 8.0, Pal.WHITE)
			tp.rect(0.0, 7.5, 16.0, 1.0, 0xFF20202A)).full()
		_bobber = ModelBuilder.new() \
			.sphere(0.0, 0.0, 0.0, 4.0, tex, 12, 8, 1.0, -1, 0.8) \
			.cylinder(0.0, 0.0, 3.5, 9.0, 0.5, 6, TexKit.solid(4, 4, 0xFFFF3B30).full()) \
			.build()
	return _bobber


# ------------------------------------------------------------------ cabinet art

## The tub's inside: blue tiles.
static func tub_tiles() -> Region:
	if _tub_tiles == null:
		_tub_tiles = TexPaint.paint_texture(64, 32, 2, func(tp: TexPaint) -> void:
			tp.fill(0xFF2A6AB8)
			for x in range(0, 64, 8):
				tp.rect(float(x), 0.0, 0.8, 32.0, 0xFF1A4A88)
			for y in range(0, 32, 8):
				tp.rect(0.0, float(y), 64.0, 0.8, 0xFF1A4A88)
		).full()
	return _tub_tiles


## Pebbles on the tub floor.
static func tub_floor() -> Region:
	if _tub_floor == null:
		_tub_floor = TexPaint.paint_texture(64, 64, 2, func(tp: TexPaint) -> void:
			tp.fill(0xFF1A3A5A)
			var cols := [0xFF3A6A8A, 0xFF2A4A6A, 0xFFC8B878, 0xFF5A8AAA]
			for k in 120:
				tp.circle(MathUtil.hash01(k, 171) * 64.0, MathUtil.hash01(k, 172) * 64.0, 1.0 + MathUtil.hash01(k, 173) * 1.8, cols[k % cols.size()])
		).full()
	return _tub_floor


## The tub's water, translucent so the fish show through.
static func tub_water() -> Region:
	if _tub_water == null:
		_tub_water = TexPaint.paint_texture(64, 64, 2, func(tp: TexPaint) -> void:
			tp.fill(Pal.with_alpha(0xFF2AB8D8, 0.55))
			for k in 7:
				tp.ring(32.0, 32.0, 4.0 + k * 4.2, 0.8, Pal.with_alpha(Pal.WHITE, 0.18))
		).full()
	return _tub_water


## Red-and-white striped canopy.
static func canopy() -> Region:
	if _canopy == null:
		_canopy = TexPaint.paint_texture(64, 16, 2, func(tp: TexPaint) -> void:
			for k in 8:
				tp.rect(k * 8.0, 0.0, 8.0, 16.0, 0xFFE8323C if k % 2 == 0 else 0xFFFFF4E0)
			tp.rect(0.0, 13.0, 64.0, 3.0, 0xFFFFC83D)
		).full()
	return _canopy
