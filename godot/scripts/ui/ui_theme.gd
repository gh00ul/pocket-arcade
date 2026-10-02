class_name UiTheme
extends RefCounted
## ui/UiTheme.kt: the glow drawing every screen shares, and the panels' surface texture. The colour,
## spacing, radius, edge, type and glow tokens are [UiColors], [UiSpace], [UiRadius], [UiEdge],
## [UiText] and [UiGlow] (files of their own, so every package reaches them by Kotlin's names).
##
## Everything is in dp (the window is laid out in dp, see [Display]); the few numbers build-13 gave
## in pixels are converted with [method px].

## A glow is this many stroked rings, brightest at the surface and fading quadratically outward.
const GLOW_RINGS := 5


## One physical pixel in dp (build-13's pixel constants, such as a glow ring's extra 0.75 px).
static func px() -> float:
	return 1.0 / maxf(Display.density, 0.01)


## [param color] with its alpha replaced (Compose's `copy(alpha = a)`).
static func with_alpha(color: Color, a: float) -> Color:
	return Color(color.r, color.g, color.b, a)


## Paints a soft glow of [param color] round the rounded rectangle at [param top_left] of
## [param rect_size]: [constant GLOW_RINGS] strokes stepping out to [param reach], faint at the far
## end. It is cheap (five strokes, no blur) and stays outside the shape, so translucent glass above
## it isn't tinted. [param strength] is the alpha at the edge.
static func glow_round_rect(ds: DrawScope, color: Color, top_left: Vector2, rect_size: Vector2, corner: float, reach: float, strength: float) -> void:
	if strength <= 0.003 or reach <= 0.5 * px():
		return
	var step := reach / GLOW_RINGS
	var width := step + 0.75 * px()
	for i in GLOW_RINGS:
		var t := i / float(GLOW_RINGS)
		var grow := step * (i + 0.5)
		ds.draw_round_rect(with_alpha(color, strength * (1.0 - t) * (1.0 - t)), top_left - Vector2(grow, grow),
			rect_size + Vector2(grow * 2.0, grow * 2.0), corner + grow, 1.0, width)


## The same glow round a circle of [param radius] at [param center].
static func glow_circle(ds: DrawScope, color: Color, center: Vector2, radius: float, reach: float, strength: float) -> void:
	if strength <= 0.003 or reach <= 0.5 * px():
		return
	var step := reach / GLOW_RINGS
	var width := step + 0.75 * px()
	for i in GLOW_RINGS:
		var t := i / float(GLOW_RINGS)
		ds.draw_circle(with_alpha(color, strength * (1.0 - t) * (1.0 - t)), radius + step * (i + 0.5), center, 1.0, width)


## The surface texture: faint scanlines and film-grain specks in one small tile, painted the first
## time a panel needs it and then only tiled (one texel per screen pixel, as build-13's bitmap
## shader drew it). Laid over a panel's gradient it keeps big dark areas from looking like flat
## plastic. The specks come from build-13's own seeded sequence ([KRandom]).
class UiTexture:
	extends RefCounted
	## The tile's side in pixels, its scanline period and the grain's seed.
	const TILE := 64
	const SCAN_PERIOD := 4
	const SEED := 0x5EED
	## How strongly a panel shows the texture.
	const PANEL_ALPHA := 0.9

	static var _scan: ImageTexture = null

	## The tile's pixels as build-13 set them (ARGB).
	static func pixels() -> PackedInt64Array:
		var px := PackedInt64Array()
		px.resize(TILE * TILE)
		var rnd := KRandom.new(SEED)
		for y in TILE:
			if y % SCAN_PERIOD == 0:
				for x in TILE:
					px[y * TILE + x] = 0x0EFFFFFF
		# Light and dark specks, unevenly strong (the index is drawn before the strength).
		for i in 110:
			var at := rnd.next_int_until(TILE * TILE)
			px[at] = ((6 + rnd.next_int_until(18)) << 24) | 0xFFFFFF
		for i in 70:
			var at := rnd.next_int_until(TILE * TILE)
			px[at] = (16 + rnd.next_int_until(30)) << 24
		return px

	## The tile as a texture (draw it tiled, nearest-filtered, one texel per pixel).
	static func scan() -> ImageTexture:
		if _scan == null:
			var px := pixels()
			var img := Image.create_empty(TILE, TILE, false, Image.FORMAT_RGBA8)
			for i in px.size():
				var v := px[i]
				img.set_pixel(i % TILE, i / TILE, Color8((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF, (v >> 24) & 0xFF))
			_scan = ImageTexture.create_from_image(img)
		return _scan
