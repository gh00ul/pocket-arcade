class_name SceneFx
extends RefCounted
## games/scenea/SceneFx.kt: small lighting-and-glow toolkit shared by the claw, skee-ball,
## whack-a-mole and coin-pusher scenes: soft rings, light shafts and flares built from a handful of
## pure-math textures and thin helpers that draw them additively. Everything here is presentation:
## nothing reads or writes a game's simulation or its rng.

## A thin bright band at RING_RADIUS of the sprite's half-width, inside a faint halo.
const RING_RADIUS := 0.8
const RING_WIDTH := 0.07
const RING_HALO := 0.16

static var _ring: PaTexture = null
static var _shaft: PaTexture = null
static var _flare: PaTexture = null
static var _streak: PaTexture = null


## Pixel alpha (0..255) for a soft-edged ring; exposed so tests can check the profile.
static func ring_alpha(d: float) -> int:
	var z := (d - RING_RADIUS) / RING_WIDTH
	var band := exp(-z * z)
	var h := clampf(1.0 - d, 0.0, 1.0)
	var halo := h * h * RING_HALO
	# Fade to nothing over the last 7% so the quad's edge never shows.
	var edge := 1.0 - clampf((d - 0.93) / 0.07, 0.0, 1.0)
	return int(clampf((band + halo) * edge, 0.0, 1.0) * 255.0)


## A soft ring for shockwaves and pulses (white; tint it when drawing).
static func ring() -> PaTexture:
	if _ring == null:
		var n := 64
		var px := PackedInt32Array()
		px.resize(n * n)
		for y in n:
			for x in n:
				var dx := (x + 0.5 - n / 2.0) / (n / 2.0)
				var dy := (y + 0.5 - n / 2.0) / (n / 2.0)
				px[y * n + x] = (ring_alpha(sqrt(dx * dx + dy * dy)) << 24) | 0xFFFFFF
		_ring = PaTexture.from_argb(n, n, px)
	return _ring


## A light shaft: soft on both long edges, brightest at the top (row 0), fading to nothing at the bottom.
static func shaft() -> PaTexture:
	if _shaft == null:
		var w := 16
		var h := 64
		var px := PackedInt32Array()
		px.resize(w * h)
		for y in h:
			for x in w:
				var u := (x + 0.5) / w * 2.0 - 1.0
				var e := clampf(1.0 - absf(u), 0.0, 1.0)
				var edge := e * e * (3.0 - 2.0 * e)
				var a := 1.0 - (y + 0.5) / h
				var along := a * a
				px[y * w + x] = (int(edge * along * 255.0) << 24) | 0xFFFFFF
		_shaft = PaTexture.from_argb(w, h, px)
	return _shaft


## A four-point flare: a bright core with a long thin cross, for jackpots and glints.
static func flare_tex() -> PaTexture:
	if _flare == null:
		var n := 64
		var px := PackedInt32Array()
		px.resize(n * n)
		for y in n:
			for x in n:
				var dx := absf((x + 0.5 - n / 2.0) / (n / 2.0))
				var dy := absf((y + 0.5 - n / 2.0) / (n / 2.0))
				var arms := exp(-dx * 9.0) * exp(-dy * 42.0) + exp(-dy * 9.0) * exp(-dx * 42.0)
				var core := exp(-(dx * dx + dy * dy) * 40.0)
				var edge := 1.0 - clampf((sqrt(dx * dx + dy * dy) - 0.85) / 0.15, 0.0, 1.0)
				px[y * n + x] = (int(clampf(arms * 0.75 + core, 0.0, 1.0) * edge * 255.0) << 24) | 0xFFFFFF
		_flare = PaTexture.from_argb(n, n, px)
	return _flare


## A soft horizontal band, faded on all four sides: sheens that sweep across signs and glass.
static func streak() -> PaTexture:
	if _streak == null:
		var w := 64
		var h := 16
		var px := PackedInt32Array()
		px.resize(w * h)
		for y in h:
			for x in w:
				var u := (x + 0.5) / w * 2.0 - 1.0
				var v := (y + 0.5) / h * 2.0 - 1.0
				var a := clampf(1.0 - absf(u), 0.0, 1.0) * clampf(1.0 - absf(v), 0.0, 1.0)
				px[y * w + x] = (int(a * a * 255.0) << 24) | 0xFFFFFF
		_streak = PaTexture.from_argb(w, h, px)
	return _streak


## A vertical gradient of [param top] to [param bottom] (opaque ARGB), for backdrops and panels.
static func gradient(w: int, h: int, top: int, bottom: int) -> PaTexture:
	var px := PackedInt32Array()
	px.resize(w * h)
	for y in h:
		var t := y / float(maxi(h - 1, 1))
		var c := Pal.mix(top, bottom, t)
		for x in w:
			px[y * w + x] = c
	return PaTexture.from_argb(w, h, px)


# ------------------------------------------------------------------ drawing helpers

## A soft additive light pool lying flat at height [param y].
static func pool(r: Renderer3D, x: float, y: float, z: float, w: float, d: float, color: int, alpha: float) -> void:
	if alpha <= 0.004:
		return
	r.flat(x, z, y, w, d, TexKit.glow().full(), 0.0, Blend.ADD, 1.0, alpha, color)


## A soft additive glow facing the camera.
static func glow(r: Renderer3D, x: float, y: float, z: float, size: float, color: int, alpha: float) -> void:
	if alpha <= 0.004:
		return
	r.sprite(x, y, z, size, size, TexKit.glow().full(), 0.0, Blend.ADD, 1.0, alpha, 1.0, color)


## An expanding ring lying flat at height [param y] (impacts on a table, board or floor).
static func shockwave(r: Renderer3D, x: float, y: float, z: float, diameter: float, color: int, alpha: float) -> void:
	if alpha <= 0.004:
		return
	r.flat(x, z, y, diameter, diameter, ring().full(), 0.0, Blend.ADD, 1.0, alpha, color)


## An expanding ring facing the camera (bursts in the air).
static func burst_ring(r: Renderer3D, x: float, y: float, z: float, diameter: float, color: int, alpha: float) -> void:
	if alpha <= 0.004:
		return
	r.sprite(x, y, z, diameter, diameter, ring().full(), 0.0, Blend.ADD, 1.0, alpha, 1.0, color)


## A four-point flare facing the camera, turned by [param roll].
static func flare(r: Renderer3D, x: float, y: float, z: float, size: float, color: int, alpha: float, roll: float = 0.0) -> void:
	if alpha <= 0.004:
		return
	r.sprite(x, y, z, size, size, flare_tex().full(), roll, Blend.ADD, 1.0, alpha, 1.0, color)


## A light shaft [param width] wide from a lamp at the first point, thinning out towards the second.
static func shaft_beam(r: Renderer3D, x0: float, y0: float, z0: float, x1: float, y1: float, z1: float,
		width: float, color: int, alpha: float) -> void:
	if alpha <= 0.004:
		return
	r.beam(x0, y0, z0, x1, y1, z1, width, shaft().full(), Blend.ADD, 1.0, alpha, color)


## Scales a colour's brightness by [param k] (for fading an additive sprite through its tint).
static func dim(argb: int, k: float) -> int:
	return Pal.shade(argb, clampf(k, 0.0, 1.0))
