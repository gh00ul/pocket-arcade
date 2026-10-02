class_name FigureShadow
extends RefCounted
## hub/FigureShadow.kt: a kid's shadow on the floor: a soft contact blob under the feet, and the
## figure's silhouette flattened along the light, away from the lamps above it. The flattening is
## worked out for the two parts that make a kid's outline (legs and torso as one stripe, the head as
## a disc), each a soft ellipse, so the shadow is drawn once with no overlapping meshes to darken
## twice, and costs three quads a figure instead of another copy of the figure.
##
## Which way it leans is a blend of the lights that reach the spot, weighted by how strongly each
## lights it (the same falloff the shader uses), with a faint fixed key light as the tiebreak. So a
## kid under a downlight stands over their own shadow, and one between two lamps gets a short shadow
## that swings smoothly as they walk, with no jump when the nearest lamp changes. The region is the
## soft round shadow texture. Allocation-free: this runs for every kid in view every frame.

## Lamps lower than this (the floor glow of each cabinet, the café till strip) light a kid from the
## side rather than from above, and cast no floor shadow.
const KEY_MIN_Y := 100.0
## The height on a kid the lean is measured at: about their middle.
const REF_HEIGHT := 22.0
## How much the fixed key light counts against the lamps (a downlight 45 units off weighs about this much).
const KEY_WEIGHT := 0.15
## The longest shadow, per unit of height: a kid's head lands at most this fraction of their height from their feet.
const MAX_LEAN := 0.7
## Below this lean the shadow is too short to tell from the contact blob, so only the blob is drawn.
const MIN_LEAN := 0.06

## Contact blob: size (the figure's scale multiplies it) and how dark.
const CONTACT_W := 20.0
const CONTACT_D := 14.0
const CONTACT_ALPHA := 0.5
## Legs and torso: the height they reach, their width and how dark the stripe is.
const BODY_TOP := 27.0
const BODY_WIDTH := 13.0
const BODY_ALPHA := 0.5
## The head: how high its centre is, how big and how dark.
const HEAD_Y := 35.5
const HEAD_SIZE := 15.0
const HEAD_ALPHA := 0.4
## How far from a figure's feet any part of its shadow can reach, for culling.
const REACH := MAX_LEAN * HEAD_Y + HEAD_SIZE
## Heights above the floor: the cast shadow just under the contact blob, both over the floor decals.
const Y_CAST := 0.19
const Y_CONTACT := 0.2

var _region: Region
var _lean := PackedFloat64Array([0.0, 0.0])


func _init(region: Region) -> void:
	_region = region


## Draws the shadow of a figure of size [param scale] standing at ([param x], [param z]), lit by
## what [param r]'s lighting holds this frame.
func draw(r: Renderer3D, x: float, z: float, scale: float = 1.0) -> void:
	cast(r.lighting, x, z, _lean)
	var lx := _lean[0]
	var lz := _lean[1]
	var lean_len := sqrt(lx * lx + lz * lz)
	if lean_len >= MIN_LEAN:
		# The long axis of a flat quad is its local z, which the angle turns to point along the lean.
		var angle := atan2(-lx, lz)
		var body_mid := BODY_TOP / 2.0 * scale
		r.flat(x + lx * body_mid, z + lz * body_mid, Y_CAST, BODY_WIDTH * scale, (BODY_TOP * lean_len + BODY_WIDTH) * scale,
			_region, angle, Blend.ALPHA, 0.0, BODY_ALPHA)
		var head_at := HEAD_Y * scale
		r.flat(x + lx * head_at, z + lz * head_at, Y_CAST, HEAD_SIZE * scale, HEAD_SIZE * scale, _region, angle, Blend.ALPHA, 0.0, HEAD_ALPHA)
	r.flat(x, z + 1.0, Y_CONTACT, CONTACT_W * scale, CONTACT_D * scale, _region, 0.0, Blend.ALPHA, 0.0, CONTACT_ALPHA)


## Where the shadow of something standing at ([param x], [param z]) falls, as how far it lands
## from the feet across the floor per unit of height (x in `out[0]`, z in `out[1]`): away from the
## lamps above, blended by how much each lights the spot, and away from the hall's key light where
## none reach. Never longer than [constant MAX_LEAN].
func cast(l: Lighting, x: float, z: float, out: PackedFloat64Array) -> void:
	# The key light: Lighting.dir_x and friends point towards it, so the shadow runs the other way.
	var up := maxf(l.dir_y, 0.2)
	var sx := -l.dir_x / up * KEY_WEIGHT
	var sz := -l.dir_z / up * KEY_WEIGHT
	var total := KEY_WEIGHT
	for p: PointLight in l.points:
		if p.y < KEY_MIN_Y:
			continue
		var dx := x - p.x
		var dz := z - p.z
		var d2 := dx * dx + dz * dz
		if d2 >= p.radius * p.radius:
			continue
		var fall := 1.0 - sqrt(d2) / p.radius
		var w := fall * fall * p.intensity
		# A point REF_HEIGHT up is lit from a lamp p.y high and dx across: it lands dx * REF / (p.y - REF) away.
		var k := w / (p.y - REF_HEIGHT)
		sx += dx * k
		sz += dz * k
		total += w
	var ox := sx / total
	var oz := sz / total
	var lean_len := sqrt(ox * ox + oz * oz)
	if lean_len > MAX_LEAN:
		ox *= MAX_LEAN / lean_len
		oz *= MAX_LEAN / lean_len
	out[0] = ox
	out[1] = oz
