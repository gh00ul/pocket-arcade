class_name TexKit
extends RefCounted
## engine/r3d/TexKit.kt: shared procedural textures: glows, shadows, bulbs and flat colours.

static var _glow: PaTexture = null
static var _shadow: PaTexture = null
static var _dot: PaTexture = null
static var _white: PaTexture = null


## Soft white radial glow; tinted when drawn additively.
static func glow() -> PaTexture:
	if _glow == null:
		_glow = radial(32, 1.0, 255)
	return _glow


## Soft dark ellipse for contact shadows.
static func shadow() -> PaTexture:
	if _shadow == null:
		_shadow = radial(32, 1.0, 170, Pal.BLACK)
	return _shadow


## A white disc with a soft edge: light bulbs and round sparks.
static func dot() -> PaTexture:
	if _dot == null:
		_dot = radial(32, -0.85, 255)
	return _dot


## Plain white, for tinted solid-colour polygons.
static func white() -> PaTexture:
	if _white == null:
		_white = solid(4, 4, Pal.WHITE)
	return _white


static func solid(w: int, h: int, color: int) -> PaTexture:
	var px := PackedInt32Array()
	px.resize(w * h)
	px.fill(MathUtil.i32(color))
	return PaTexture.from_argb(w, h, px)


static func radial(n: int, power: float, max_alpha: int, color: int = Pal.WHITE) -> PaTexture:
	var px := PackedInt32Array()
	px.resize(n * n)
	for y in n:
		for x in n:
			var dx := (x + 0.5 - n / 2.0) / (n / 2.0)
			var dy := (y + 0.5 - n / 2.0) / (n / 2.0)
			var d := sqrt(dx * dx + dy * dy)
			var v := clampf(1.0 - d, 0.0, 1.0)
			var a := int(pow(v, power + 1.0) * max_alpha)
			px[y * n + x] = MathUtil.i32((a << 24) | (color & 0xFFFFFF))
	return PaTexture.from_argb(n, n, px)
