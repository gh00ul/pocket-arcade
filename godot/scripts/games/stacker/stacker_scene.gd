class_name StackerScene
extends RefCounted
## games/stacker/StackerScene.kt: the stacker as a 3D scene: a rooftop above a night city with
## cloud decks between, a tower of dark glass slabs with neon edges that fade from the top down,
## and every effect that answers the game (perfect drops, cuts, landings, misses, height
## milestones). It keeps all the visual state, so the simulation never reads it and rendering
## never touches the game's random generator.
##
## No texture is built until something is drawn, so headless tests can step a game freely.
## (StackerLook, declared in the same Kotlin file, is stacker_look.gd.)

const BURSTS := 12
const K_RING := 0
const K_FLARE := 1
const K_BEAM := 2
const S := 0.70710678


class Burst:
	extends RefCounted
	var kind := 0
	var x := 0.0
	var y := 0.0
	var z := 0.0
	var age := 1.0
	var life := 1.0
	var s0 := 0.0
	var s1 := 0.0
	var alpha := 0.0
	var tint := -1


# ---------------------------------------------------------------- visual state

var _bursts: Array[Burst] = []
var _burst_next := 0
var _miss_pulse := 0.0
var _combo_glow := 0.0


func _init() -> void:
	for i in BURSTS:
		_bursts.append(Burst.new())


func reset() -> void:
	for b in _bursts:
		b.age = b.life
	_miss_pulse = 0.0
	_combo_glow = 0.0


func _calm_k() -> float:
	return StackerLook.CALM_K if ScreenShake.intensity <= 0.0 else 1.0


func step(dt: float, combo: int) -> void:
	for b in _bursts:
		if b.age < b.life:
			b.age += dt
	_miss_pulse = maxf(_miss_pulse - dt * 2.0, 0.0)
	# The column of light behind the tower swells with the combo and settles smoothly.
	var target := minf(combo / 6.0, 1.0)
	_combo_glow += (target - _combo_glow) * (1.0 - exp(-3.0 * dt))


func _burst(kind: int, x: float, y: float, z: float, s0: float, s1: float, life: float, alpha: float, tint: int) -> void:
	var b := _bursts[_burst_next]
	_burst_next = (_burst_next + 1) % BURSTS
	b.kind = kind
	b.x = x
	b.y = y
	b.z = z
	b.s0 = s0
	b.s1 = s1
	b.life = life
	b.age = 0.0
	b.alpha = alpha * _calm_k()
	b.tint = tint


# ---------------------------------------------------------------- events

## A perfect drop: a shockwave round the slab's top, a glint and a column of light rising from it.
func perfect(x: float, y: float, z: float, w: float, d: float, level: int, combo: int) -> void:
	var glow := StackerArt.glow_color(level)
	var size := maxf(w, d)
	_burst(K_RING, x, y + 0.6, z, size * 1.1, size * (2.2 + combo * 0.12), 0.6, 0.85, glow)
	_burst(K_FLARE, x, y + 6.0, z, size * 0.3, size * (0.9 + combo * 0.05), 0.4, 1.0, Pal.mix(glow, Pal.WHITE, 0.5))
	_burst(K_BEAM, x, y, z, 44.0, 12.0, 0.7, 0.55, glow)


## A landing that was not perfect: a faint ring on the slab's top.
func land(x: float, y: float, z: float, w: float, d: float) -> void:
	var size := maxf(w, d)
	_burst(K_RING, x, y + 0.6, z, size * 1.05, size * 1.5, 0.35, 0.3, 0xFFDDE4FF)


## A slab was trimmed: a flare where it broke off.
func cut(x: float, y: float, z: float, level: int) -> void:
	_burst(K_FLARE, x, y + 6.0, z, 14.0, 60.0, 0.3, 0.9, StackerArt.glow_color(level))


## A slab was missed altogether.
func miss(x: float, y: float, z: float) -> void:
	_miss_pulse = _calm_k()
	_burst(K_RING, x, y, z, 90.0, 520.0, 0.7, 0.6, 0xFFFF4D4D)


## The tower passed a height worth a banner: a wide ring at [param y].
func milestone(y: float) -> void:
	_burst(K_RING, 0.0, y + 1.0, 0.0, 260.0, 1000.0, 1.1, 0.6, Pal.CYAN)


# ---------------------------------------------------------------- lights

var _key := PointLight.new(200.0, 0.0, 260.0, 1.0, 0.95, 0.9, 900.0, StackerLook.KEY_INTENSITY)
var _rim := PointLight.new(-380.0, 0.0, -260.0, 0.3, 0.7, 1.0, 900.0, StackerLook.RIM_INTENSITY)
var _top := PointLight.new(0.0, 0.0, 140.0, 1.0, 1.0, 1.0, StackerLook.TOP_RADIUS, StackerLook.TOP_INTENSITY)
var _roof_light := PointLight.new(0.0, 40.0, 0.0, 1.0, 0.3, 0.65, 520.0, 0.0)


## Sets the lighting for a tower whose top slab (level [param top_level]) is at height
## [param top_y], the camera at [param cam_y].
func light(r: Renderer3D, cam_y: float, top_y: float, top_level: int, flash: float, climb: float) -> void:
	var l := r.lighting
	l.amb_r = StackerLook.AMB_R
	l.amb_g = StackerLook.AMB_G
	l.amb_b = StackerLook.AMB_B
	l.set_direction(0.55, 1.0, 0.35)
	l.dir_r = StackerLook.DIR_R
	l.dir_g = StackerLook.DIR_G
	l.dir_b = StackerLook.DIR_B
	l.points.clear()
	_key.y = cam_y + 200.0
	l.points.append(_key)
	_rim.y = cam_y + 120.0
	l.points.append(_rim)
	var c := StackerArt.glow_color(top_level)
	_top.x = 0.0
	_top.y = top_y + 50.0
	_top.r = ((c >> 16) & 255) / 255.0
	_top.g = ((c >> 8) & 255) / 255.0
	_top.b = (c & 255) / 255.0
	# A miss washes the light red; a perfect drop lifts it.
	_top.intensity = StackerLook.TOP_INTENSITY + flash * StackerLook.TOP_FLASH * _calm_k()
	if _miss_pulse > 0.0:
		_top.r = 1.0
		_top.g = 0.25
		_top.b = 0.2
		_top.intensity += _miss_pulse * 1.2
	l.points.append(_top)
	# Near the start the rooftop is lit from below by its pad rings.
	_roof_light.intensity = 0.6 * (1.0 - climb)
	if _roof_light.intensity > 0.02:
		l.points.append(_roof_light)
	r.vignette = 0.3


# ---------------------------------------------------------------- models

var _roof_model: Model = null




func _build_roof() -> Model:
	var b := ModelBuilder.new()
	var h := StackerLook.ROOF_HALF
	var y := StackerLook.ROOF_Y
	b.quad(-h, y, -h, h, y, -h, h, y, h, -h, y, h, StackerArt.roof().full(), 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.4)
	# A low parapet round the edge, and a lit lip along it.
	var steel := StackerArt.steel().full()
	var f := BoxFaces.new(steel, steel, steel, steel, null, 0.0, 0.0, 0.0, 0.4)
	var t := 26.0
	var up := y + 28.0
	CabinetForm.beveled_box(b, -h - t, y, -h - t, h + t, up, -h, f, -1, 3.0, 0.0)
	CabinetForm.beveled_box(b, -h - t, y, h, h + t, up, h + t, f, -1, 3.0, 0.0)
	CabinetForm.beveled_box(b, -h - t, y, -h, -h, up, h, f, -1, 3.0, 0.0)
	CabinetForm.beveled_box(b, h, y, -h, h + t, up, h, f, -1, 3.0, 0.0)
	return b.build()


var _xf := Xform.new()


# ---------------------------------------------------------------- drawing

## The world around the tower: the rooftop and its parapet, the city far below, cloud decks, motes
## of light, height markers and a column of light behind the tower, brighter with a combo.
## [param climb] runs 0 to 1 as the tower rises. Draws first.
func draw_world(r: Renderer3D, cam_y: float, t: float, climb: float, top_y: float, top_level: int) -> void:
	if _roof_model == null:
		_roof_model = _build_roof()
	var ch := StackerLook.CITY_HALF
	var cy := StackerLook.CITY_Y
	var city_tint := Pal.mix_argb(0xFFFFFFFF, 0xFF505070, climb * 0.8)
	r.quad(-ch, cy, -ch, ch, cy, -ch, ch, cy, ch, -ch, cy, ch, StackerArt.city().full(), 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, StackerLook.CITY_EMISSIVE, 1.0, true, city_tint)
	_roof_model.draw(r)
	var glow := TexKit.glow().full()
	var color := StackerArt.glow_color(top_level)
	# Cloud decks between the roof and the city, drifting.
	var cloud := StackerArt.cloud().full()
	r.flat(sin(t * 0.05) * 120.0, -60.0, StackerLook.CLOUD_Y_NEAR, 2800.0, 2800.0, cloud, 0.0, Blend.ALPHA, 0.75, StackerLook.CLOUD_ALPHA)
	r.flat(sin(t * 0.04 + 2.0) * 160.0, 200.0, StackerLook.CLOUD_Y_FAR, 3600.0, 3600.0, cloud, 0.0, Blend.ALPHA, 0.6, StackerLook.CLOUD_ALPHA * 0.9)
	# Light pooling on the rooftop under the tower, in the top slab's colour, fading as we climb.
	var roof_a := 0.3 * (1.0 - climb)
	if roof_a > 0.01:
		r.flat(0.0, 0.0, StackerLook.ROOF_Y + 0.8, 520.0, 520.0, glow, 0.0, Blend.ADD, 1.0, roof_a, color)
	# A column of light behind the tower, swelling with the combo. (Build-13 fetched the shaft
	# texture here too, though the column is drawn with the glow; fetching it paints it early.)
	StackerArt.shaft()
	var col_a := StackerLook.COLUMN_ALPHA + _combo_glow * 0.1
	r.beam(0.0, StackerLook.ROOF_Y, -30.0, 0.0, top_y + 260.0, -30.0, 240.0, glow, Blend.ADD, 1.0, col_a, color)
	# Height markers: a bar and its number, every ten courses, to the tower's left.
	for n in range(1, 11):
		var y := (10 * n + 1) * 18.0
		if absf(y - cam_y) > StackerLook.MARKER_RANGE:
			continue
		_draw_marker(r, n, y, t)
	# Motes of light drifting up round the tower.
	var dot := TexKit.dot().full()
	for i in StackerLook.MOTES:
		var a := MathUtil.hash01(i, 51) * 6.2832
		var rad := 90.0 + MathUtil.hash01(i, 52) * 200.0
		var x := cos(a + t * 0.05) * rad
		var z := sin(a + t * 0.05) * rad - 40.0
		var span := 700.0
		var y := cam_y - 300.0 + fmod(MathUtil.hash01(i, 53) * span + t * (10.0 + MathUtil.hash01(i, 54) * 14.0), span)
		r.sprite(x, y, z, 3.2, 3.2, dot, 0.0, Blend.ADD, 1.0, 0.4 * (0.6 + 0.4 * sin(t * 1.6 + i * 2.3)), 1.0, color)


func _draw_marker(r: Renderer3D, n: int, y: float, t: float) -> void:
	var x := StackerLook.MARKER_X
	var z := StackerLook.MARKER_Z
	var white := TexKit.white().full()
	var pulse := 0.85 + 0.15 * sin(t * 2.5 + n)
	r.quad(x - 45.0, y + 0.9, z, x + 45.0, y + 0.9, z, x + 45.0, y - 0.9, z, x - 45.0, y - 0.9, z, white, 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, 0.6 * pulse, true, Pal.CYAN)
	r.sprite(x - 20.0, y + 12.0, z, 45.0, 20.0, StackerArt.marker(n), 0.0, Blend.ALPHA, 1.1, 0.95 * pulse)


## A slab [param w] × [param h] × [param d] standing on [param y], centred on ([param x], [param z])
## and optionally turned by [param spin] about the axis it was sliding along (tumbling pieces).
## Dark glass with a chamfered top edge whose neon [param glow_k] (1 at full) tints the edge;
## [param flash_k] lights the top white.
func slab(r: Renderer3D, x: float, y: float, h: float, z: float, w: float, d: float, level: int, glow_k: float, spin: float, axis_x: bool, flash_k: float) -> void:
	if axis_x:
		_xf.set_xf(x, y + h / 2.0, z, 0.0, 0.0, -spin)
	else:
		_xf.set_xf(x, y + h / 2.0, z, 0.0, spin)
	var hw := w / 2.0
	var hh := h / 2.0
	var hd := d / 2.0
	var b := minf(StackerLook.BEVEL, minf(hw, minf(hh, hd)) * 0.6)
	var body := StackerArt.body_color(level)
	var glow := StackerArt.glow_color(level)
	var edge := maxf(glow_k, StackerLook.EDGE_MIN) * StackerLook.EDGE_EMISSIVE
	var flat := TexKit.white().full()
	var side := StackerArt.slab_side().full()
	var top_tint: int
	if flash_k > 0.02:
		top_tint = Pal.mix_argb(Pal.mix_argb(body, Pal.WHITE, 0.12), 0xFFCFF8FF, flash_k)
	else:
		top_tint = Pal.mix_argb(body, Pal.WHITE, 0.12)
	var top_em := flash_k * 0.85 if flash_k > 0.02 else 0.0
	# Top face, inset by the chamfer.
	_face(r, flat, top_tint, 0.0, 1.0, 0.0, top_em, StackerLook.TOP_GLOSS, 0.0, 0.0, 0.0, 0.0,
		-hw + b, hh, -hd + b, hw - b, hh, -hd + b, hw - b, hh, hd - b, -hw + b, hh, hd - b)
	# The four chamfers, glowing: the slab's neon outline.
	_face(r, flat, glow, 0.0, S, S, edge, 0.0, 0.0, 0.0, 0.0, 0.0,
		-hw + b, hh, hd - b, hw - b, hh, hd - b, hw, hh - b, hd, -hw, hh - b, hd)
	_face(r, flat, glow, S, S, 0.0, edge, 0.0, 0.0, 0.0, 0.0, 0.0,
		hw - b, hh, hd - b, hw - b, hh, -hd + b, hw, hh - b, -hd, hw, hh - b, hd)
	_face(r, flat, glow, -S, S, 0.0, edge, 0.0, 0.0, 0.0, 0.0, 0.0,
		-hw + b, hh, -hd + b, -hw + b, hh, hd - b, -hw, hh - b, hd, -hw, hh - b, -hd)
	_face(r, flat, glow, 0.0, S, -S, edge, 0.0, 0.0, 0.0, 0.0, 0.0,
		hw - b, hh, -hd + b, -hw + b, hh, -hd + b, -hw, hh - b, -hd, hw, hh - b, -hd)
	# The four sides, shading down to the foot (the texture's own gradient).
	var tw := float(side.w)
	var th := float(side.h)
	_face(r, side, body, 0.0, 0.0, 1.0, 0.0, StackerLook.SIDE_GLOSS, tw, th, 0.0, 0.0,
		-hw, hh - b, hd, hw, hh - b, hd, hw, -hh, hd, -hw, -hh, hd)
	_face(r, side, body, 1.0, 0.0, 0.0, 0.0, StackerLook.SIDE_GLOSS, tw, th, 0.0, 0.0,
		hw, hh - b, hd, hw, hh - b, -hd, hw, -hh, -hd, hw, -hh, hd)
	_face(r, side, body, -1.0, 0.0, 0.0, 0.0, StackerLook.SIDE_GLOSS, tw, th, 0.0, 0.0,
		-hw, hh - b, -hd, -hw, hh - b, hd, -hw, -hh, hd, -hw, -hh, -hd)
	_face(r, side, body, 0.0, 0.0, -1.0, 0.0, StackerLook.SIDE_GLOSS, tw, th, 0.0, 0.0,
		hw, hh - b, -hd, -hw, hh - b, -hd, -hw, -hh, -hd, hw, -hh, -hd)


## One quad of a slab in the slab's own frame, placed by _xf; [param tw]/[param th] are the
## texture's extent (0 for a flat colour).
func _face(r: Renderer3D, region: Region, tint: int, nx: float, ny: float, nz: float, emissive: float, gloss: float,
		tw: float, th: float, u0: float, v0: float,
		ax: float, ay: float, az: float, bx: float, by: float, bz: float,
		cx: float, cy: float, cz: float, dx: float, dy: float, dz: float) -> void:
	var xf := _xf
	var uw := tw if tw > 0.0 else 4.0
	var vh := th if th > 0.0 else 4.0
	r.begin(region, Blend.OPAQUE, emissive, 1.0, 1.0, true, gloss)
	r.normal(xf.dir_x(nx, ny, nz), xf.dir_y(nx, ny, nz), xf.dir_z(nx, ny, nz))
	r.tint(tint)
	r.vertex(xf.x(ax, ay, az), xf.y(ax, ay, az), xf.z(ax, ay, az), u0, v0)
	r.vertex(xf.x(bx, by, bz), xf.y(bx, by, bz), xf.z(bx, by, bz), uw, v0)
	r.vertex(xf.x(cx, cy, cz), xf.y(cx, cy, cz), xf.z(cx, cy, cz), uw, vh)
	r.vertex(xf.x(dx, dy, dz), xf.y(dx, dy, dz), xf.z(dx, dy, dz), u0, vh)
	r.end()


## Shockwaves, glints and rising light from perfect drops, cuts and misses, on top of the tower.
func draw_effects(r: Renderer3D) -> void:
	var ring := StackerArt.ring().full()
	var flare := StackerArt.flare().full()
	var shaft := StackerArt.shaft().full()
	for b in _bursts:
		if b.age >= b.life:
			continue
		var p := b.age / b.life
		var e := 1.0 - (1.0 - p) * (1.0 - p)
		var size := lerpf(b.s0, b.s1, e)
		var a := b.alpha * (1.0 - p) * (1.0 - p * 0.3)
		if b.kind == K_RING:
			r.flat(b.x, b.z, b.y, size, size, ring, 0.0, Blend.ADD, 1.0, a, b.tint)
		elif b.kind == K_FLARE:
			r.sprite(b.x, b.y, b.z, size, size, flare, 0.0, Blend.ADD, 1.0, a, 1.0, b.tint)
		else:
			# The beam rises from the slab and thins as it goes.
			r.beam(b.x, b.y, b.z, b.x, b.y + 60.0 + 260.0 * e, b.z, size, shaft, Blend.ADD, 1.0, a, b.tint)
