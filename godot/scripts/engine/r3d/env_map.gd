class_name EnvMap
extends RefCounted
## engine/r3d/EnvMap.kt: the room glossy things reflect, a small cube map painted once in code: a
## dark arcade with warm ceiling panels, neon strips, light boxes, cabinet screens, bright windows
## toward the entrance (+z) and a carpet dotted with coloured pools. Radiance is stored square-root
## encoded over RANGE so darks keep their precision in 8 bits. Built once (2×2 supersampled), then
## kept in user:// so later launches load it instead of computing 6 × 64² samples again.

## Texels along a face edge (mipmapped down to 1 for rough reflections).
const SIZE := 64
## Brightest radiance the 8-bit faces can hold.
const RANGE := 4.0
const CACHE_PATH := "user://cache/env_map_v1.res"

const POOL_R := [1.0, 0.2, 1.0, 0.6, 0.3]
const POOL_G := [0.3, 0.85, 0.75, 0.4, 1.0]
const POOL_B := [0.7, 1.0, 0.25, 1.0, 0.55]

static var _cubemap: Cubemap = null


## The direction through texel coordinates (s, t) in -1..1 of cube [param face] (GL's layout).
static func face_dir(face: int, s: float, t: float) -> Vector3:
	match face:
		0:
			return Vector3(1.0, -t, -s)
		1:
			return Vector3(-1.0, -t, s)
		2:
			return Vector3(s, 1.0, t)
		3:
			return Vector3(s, -1.0, -t)
		4:
			return Vector3(s, -t, 1.0)
	return Vector3(-s, -t, -1.0)


## The inverse of [method face_dir], as the GPU does it: [face, s, t].
static func dir_to_face(x: float, y: float, z: float) -> Array:
	var ax := absf(x)
	var ay := absf(y)
	var az := absf(z)
	if ax >= ay and ax >= az:
		if x > 0.0:
			return [0, -z / ax, -y / ax]
		return [1, z / ax, -y / ax]
	elif ay >= az:
		if y > 0.0:
			return [2, x / ay, z / ay]
		return [3, x / ay, -z / ay]
	if z > 0.0:
		return [4, x / az, -y / az]
	return [5, -x / az, -y / az]


## GLSL's reflect(): the incident direction mirrored about unit normal n.
static func reflect(i: Vector3, n: Vector3) -> Vector3:
	return i - 2.0 * i.dot(n) * n


static func _smooth(e0: float, e1: float, x: float) -> float:
	var t := clampf((x - e0) / (e1 - e0), 0.0, 1.0)
	return t * t * (3.0 - 2.0 * t)


static func _box(x: float, y: float, hx: float, hy: float, f: float) -> float:
	return (1.0 - _smooth(hx - f, hx + f, absf(x))) * (1.0 - _smooth(hy - f, hy + f, absf(y)))


static func _fract(v: float) -> float:
	return v - floorf(v)


## Radiance seen along direction (x, y, z) (any length), as r, g, b.
static func radiance(x: float, y: float, z: float) -> Vector3:
	var l := maxf(sqrt(x * x + y * y + z * z), 1e-6)
	var dx := x / l
	var dy := y / l
	var dz := z / l
	# Base: a purple-black room, darkest overhead.
	var up := _smooth(0.05, 0.55, dy)
	var down := _smooth(-0.02, -0.45, dy)
	var r := 0.085 * (1.0 - up) + 0.02 * up
	var g := 0.06 * (1.0 - up) + 0.017 * up
	var b := 0.14 * (1.0 - up) + 0.04 * up
	r = r * (1.0 - down) + 0.045 * down
	g = g * (1.0 - down) + 0.03 * down
	b = b * (1.0 - down) + 0.075 * down
	var az := atan2(dx, dz)
	if dy > 0.12:
		# The ceiling plane at height 1: warm light panels in a grid and two neon strips.
		var px := dx / dy
		var pz := dz / dy
		var fade := _smooth(0.12, 0.35, dy)
		var cx := _fract(px * 0.55 + 0.5) - 0.5
		var cz := _fract(pz * 0.55 + 0.25) - 0.5
		var panel := _box(cx, cz, 0.16, 0.08, 0.02) * fade
		r += 1.9 * panel
		g += 1.6 * panel
		b += 1.15 * panel
		var sx := _fract(px * 0.3) - 0.5
		var strip := (1.0 - _smooth(0.012, 0.03, absf(sx))) * fade
		var pink := 1.0 if _fract(px * 0.15) < 0.5 else 0.0
		r += strip * (1.2 + 1.3 * pink)
		g += strip * (1.9 - 1.4 * pink)
		b += strip * 2.2
	if dy > -0.5 and dy < 0.5:
		# Walls: a neon band high up all the way round, pink at the back, cyan at the sides.
		var band := 1.0 - _smooth(0.012, 0.03, absf(dy - 0.3))
		var back := _smooth(1.6, 2.6, absf(az))
		r += band * (0.5 + 1.9 * back)
		g += band * (1.9 - 1.5 * back)
		b += band * (2.1 - 0.4 * back)
		if absf(az) > 0.8:
			var sector := _fract(az * 1.2) - 0.5
			var cool := _box(sector, dy - 0.12, 0.18, 0.045, 0.02)
			var warm := _box(_fract(az * 1.2 + 0.5) - 0.5, dy - 0.05, 0.12, 0.03, 0.015)
			r += cool * 0.8 + warm * 2.0
			g += cool * 1.05 + warm * 1.2
			b += cool * 1.5 + warm * 0.55
			var cab := _fract(az * 2.6)
			var scr := _box(cab - 0.5, dy + 0.16, 0.3, 0.13, 0.04)
			var c := (MathUtil.i32(floori(az * 2.6)) & 0x7fffffff) % POOL_R.size()
			var k := scr * 1.1 * _smooth(0.8, 1.1, absf(az))
			r += k * POOL_R[c]
			g += k * POOL_G[c]
			b += k * POOL_B[c]
		# The entrance: tall bright windows with dark mullions.
		var win := _box(az, dy + 0.075, 0.72, 0.42, 0.06)
		if win > 0.0:
			var mullion := 1.0 - (1.0 - _smooth(0.02, 0.045, absf(_fract(az * 2.4) - 0.5))) * 0.85
			var sill := 1.0 - (1.0 - _smooth(0.008, 0.02, absf(dy - 0.12))) * 0.8
			var k := win * mullion * sill
			var sky := _smooth(-0.3, 0.3, dy)
			r += k * (0.35 + 0.6 * sky)
			g += k * (0.4 + 0.65 * sky)
			b += k * (0.5 + 0.8 * sky)
	if dy < -0.3:
		# The carpet: dense faint specks of blacklight colour that blur into a tint.
		var px := dx / -dy
		var pz := dz / -dy
		var fade := _smooth(-0.3, -0.5, dy)
		var gx := px * 4.0
		var gz := pz * 4.0
		var ix := floorf(gx)
		var iz := floorf(gz)
		var fx := gx - ix - 0.5
		var fz := gz - iz - 0.5
		var speck := maxf(0.0, 1.0 - (fx * fx + fz * fz) * 10.0) * fade
		var c := absi(MathUtil.i32(int(ix * 7.0 + iz * 3.0))) % POOL_R.size()
		r += speck * POOL_R[c] * 0.35
		g += speck * POOL_G[c] * 0.35
		b += speck * POOL_B[c] * 0.35
	return Vector3(minf(r, RANGE), minf(g, RANGE), minf(b, RANGE))


## One channel as the stored byte.
static func encode_channel(v: float) -> int:
	return int(sqrt(minf(maxf(v / RANGE, 0.0), 1.0)) * 255.0 + 0.5)


## A stored byte back to radiance (what the shader does).
static func decode(byte: int) -> float:
	var s := byte / 255.0
	return s * s * RANGE


## The [param size]² texels of cube [param face], 2×2 supersampled, as RGBA8 bytes.
static func build_face(face: int, size: int) -> PackedByteArray:
	var out := PackedByteArray()
	out.resize(size * size * 4)
	var o := 0
	for j in size:
		for i in size:
			var acc := Vector3.ZERO
			for k in 4:
				var s := 2.0 * (i + 0.25 + 0.5 * (k & 1)) / size - 1.0
				var t := 2.0 * (j + 0.25 + 0.5 * (k >> 1)) / size - 1.0
				var d := face_dir(face, s, t)
				acc += radiance(d.x, d.y, d.z)
			acc *= 0.25
			out[o] = encode_channel(acc.x)
			out[o + 1] = encode_channel(acc.y)
			out[o + 2] = encode_channel(acc.z)
			out[o + 3] = 255
			o += 4
	return out


## The cube map (built or loaded once).
static func cubemap() -> Cubemap:
	if _cubemap != null:
		return _cubemap
	var images: Array[Image] = []
	if FileAccess.file_exists(CACHE_PATH):
		var cached: Variant = ResourceLoader.load(CACHE_PATH)
		if cached is Cubemap:
			_cubemap = cached
			return _cubemap
	for f in 6:
		var img := Image.create_from_data(SIZE, SIZE, false, Image.FORMAT_RGBA8, build_face(f, SIZE))
		img.generate_mipmaps()
		images.append(img)
	_cubemap = Cubemap.new()
	_cubemap.create_from_images(images)
	DirAccess.make_dir_recursive_absolute(CACHE_PATH.get_base_dir())
	ResourceSaver.save(_cubemap, CACHE_PATH)
	return _cubemap
