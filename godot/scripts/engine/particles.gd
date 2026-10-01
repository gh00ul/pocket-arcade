class_name Particles
extends RefCounted
## engine/Particles.kt: an allocation-free particle pool stored as parallel arrays. Particles are
## square "pixels", confetti strips that flip as they fall, or sparkles that twinkle. A game with
## a 3D stage has them drawn inside its picture (so bright ones glow in the bloom, see
## [method record_into]); otherwise they are painted in 2D by [method draw].

const SQUARE := 0
const CONFETTI := 1
const SPARKLE := 2

## Half the width of the soft glow under a glowing particle, as a multiple of its size.
const GLOW_SIZE := 1.3
## How strongly each shape's glow shows (a share of its alpha, weighted by how bright it is).
const GLOW_SQUARE := 0.30
const GLOW_SPARKLE := 0.50
const GLOW_CONFETTI := 0.0
## Glows fainter than this are not worth a quad.
const GLOW_MIN := 0.01

const CONFETTI_COLORS := [Pal.PINK, Pal.YELLOW, Pal.CYAN, Pal.LIME, Pal.ORANGE, Pal.PURPLE, Pal.WHITE]

var capacity: int
var x := PackedFloat32Array()
var y := PackedFloat32Array()
var vx := PackedFloat32Array()
var vy := PackedFloat32Array()
var life := PackedFloat32Array()
var max_life := PackedFloat32Array()
var size := PackedFloat32Array()
var gravity := PackedFloat32Array()
var drag := PackedFloat32Array()
var phase := PackedFloat32Array()
var color := PackedInt64Array()
var shape := PackedInt32Array()
var count := 0

var _rng := KRandom.new(1234)


func _init(p_capacity: int = 600) -> void:
	capacity = p_capacity
	# (Packed arrays placed in an Array are copies: each is resized by name.)
	x.resize(capacity)
	y.resize(capacity)
	vx.resize(capacity)
	vy.resize(capacity)
	life.resize(capacity)
	max_life.resize(capacity)
	size.resize(capacity)
	gravity.resize(capacity)
	drag.resize(capacity)
	phase.resize(capacity)
	color.resize(capacity)
	shape.resize(capacity)


func clear() -> void:
	count = 0


func spawn(px: float, py: float, pvx: float, pvy: float, lifetime: float, sz: float, argb: int,
		grav: float = 0.0, drag_per_sec: float = 0.0, kind: int = SQUARE) -> void:
	var i: int
	if count < capacity:
		i = count
		count += 1
	else:
		i = _rng.next_int_until(capacity)
	x[i] = px
	y[i] = py
	vx[i] = pvx
	vy[i] = pvy
	life[i] = lifetime
	max_life[i] = lifetime
	size[i] = sz
	color[i] = argb & 0xFFFFFFFF
	gravity[i] = grav
	drag[i] = drag_per_sec
	shape[i] = kind
	phase[i] = _rng.next_float() * TAU


## Radial burst of [param n] particles in colours picked from [param colors] (ARGB ints).
func burst(px: float, py: float, n: int, speed_min: float, speed_max: float, colors: Array,
		lifetime: float = 0.6, sz: float = 3.0, grav: float = 0.0, drag_per_sec: float = 2.0, kind: int = SQUARE,
		angle_from: float = 0.0, angle_to: float = TAU) -> void:
	for k in n:
		var a := _rng.range_f(angle_from, angle_to)
		var sp := _rng.range_f(speed_min, speed_max)
		spawn(px, py, cos(a) * sp, sin(a) * sp,
			lifetime * _rng.range_f(0.6, 1.2), sz * _rng.range_f(0.7, 1.3),
			int(colors[_rng.next_int_until(colors.size())]), grav, drag_per_sec, kind)


## Confetti shower falling from the top of an area [param width] wide.
func confetti(left: float, top: float, width: float, n: int, sz: float = 4.0) -> void:
	for k in n:
		spawn(left + _rng.next_float() * width, top - _rng.next_float() * 60.0,
			_rng.range_f(-60.0, 60.0), _rng.range_f(40.0, 180.0),
			_rng.range_f(1.6, 3.2), sz * _rng.range_f(0.8, 1.4),
			int(CONFETTI_COLORS[_rng.next_int_until(CONFETTI_COLORS.size())]), 220.0, 1.4, CONFETTI)


func update(dt: float) -> void:
	var i := 0
	while i < count:
		life[i] -= dt
		if life[i] <= 0.0:
			var last := count - 1
			if i != last:
				x[i] = x[last]
				y[i] = y[last]
				vx[i] = vx[last]
				vy[i] = vy[last]
				life[i] = life[last]
				max_life[i] = max_life[last]
				size[i] = size[last]
				gravity[i] = gravity[last]
				drag[i] = drag[last]
				phase[i] = phase[last]
				color[i] = color[last]
				shape[i] = shape[last]
			count -= 1
			continue
		var k := 1.0 - minf(drag[i] * dt, 1.0)
		vx[i] *= k
		vy[i] = vy[i] * k + gravity[i] * dt
		x[i] += vx[i] * dt
		y[i] += vy[i] * dt
		phase[i] += dt * 9.0
		i += 1


## A particle's opacity over its life: full, then fading out over the last 30%.
static func fade(t: float) -> float:
	return t / 0.3 if t < 0.3 else 1.0


## The soft glows under bright squares and sparkles (Kotlin's recordGl halo quads), drawn by an
## additive layer of the 3D picture that maps field units to its own space; the particles
## themselves follow on a layer above ([method draw_shapes]). [param glow_tex] is a soft round
## white sprite.
func draw_glows(ci: CanvasItem, glow_tex: Texture2D) -> void:
	for i in count:
		var gain := GLOW_SQUARE
		if shape[i] == SPARKLE:
			gain = GLOW_SPARKLE
		elif shape[i] == CONFETTI:
			gain = GLOW_CONFETTI
		if gain <= 0.0:
			continue
		var t := life[i] / max_life[i]
		var argb := color[i]
		var r := ((argb >> 16) & 255) / 255.0
		var g := ((argb >> 8) & 255) / 255.0
		var b := (argb & 255) / 255.0
		# A dark colour has nothing to glow with.
		var lum := maxf(r, maxf(g, b))
		var a := fade(t) * ((argb >> 24) & 255) / 255.0 * gain * lum * lum
		if a < GLOW_MIN:
			continue
		var s := size[i] * (0.5 + 0.5 * absf(sin(phase[i]))) if shape[i] == SPARKLE else size[i] * (0.4 + 0.6 * t)
		var half := s * GLOW_SIZE
		ci.draw_texture_rect(glow_tex, Rect2(x[i] - half, y[i] - half, half * 2.0, half * 2.0), false, Color(r, g, b, a))


## The particles in field units (the 3D picture's particle layer).
func draw_shapes(ci: CanvasItem) -> void:
	_draw_shapes(ci, 1.0, 0.0, 0.0)


## Draws with world→screen transform: screen = origin + world × scale (2D, no 3D stage).
func draw(ds: DrawScope, origin_x: float = 0.0, origin_y: float = 0.0, scale: float = 1.0) -> void:
	ds._apply()
	ds.ci.draw_set_transform_matrix(ds.transform() * Transform2D(0.0, Vector2(scale, scale), 0.0, Vector2(origin_x, origin_y)))
	_draw_shapes(ds.ci, 1.0, 0.0, 0.0)
	ds.ci.draw_set_transform_matrix(ds.transform())


func _draw_shapes(ci: CanvasItem, scale: float, ox: float, oy: float) -> void:
	for i in count:
		var t := life[i] / max_life[i]
		var alpha := fade(t)
		var argb := color[i]
		var c := Color8((argb >> 16) & 255, (argb >> 8) & 255, argb & 255, (argb >> 24) & 255)
		c.a *= alpha
		var sx := ox + x[i] * scale
		var sy := oy + y[i] * scale
		match shape[i]:
			CONFETTI:
				var w := size[i] * scale * (0.25 + 0.75 * absf(sin(phase[i])))
				var h := size[i] * scale * 0.6
				ci.draw_rect(Rect2(sx - w / 2.0, sy - h / 2.0, w, h), c)
			SPARKLE:
				var s := size[i] * scale * (0.5 + 0.5 * absf(sin(phase[i])))
				var thin := maxf(s * 0.34, 1.0)
				ci.draw_rect(Rect2(sx - s, sy - thin / 2.0, s * 2.0, thin), c)
				ci.draw_rect(Rect2(sx - thin / 2.0, sy - s, thin, s * 2.0), c)
			_:
				var s := size[i] * scale * (0.4 + 0.6 * t)
				ci.draw_rect(Rect2(sx - s / 2.0, sy - s / 2.0, s, s), c)
