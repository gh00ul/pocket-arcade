class_name RacerScene
extends RefCounted
## games/racer/RacerScene.kt: cars and sky for the racer: car models in two levels of detail (a lit
## body with a cabin, wing, tail-light bar and wheels; and a plain one for cars far down the road),
## the starry synthwave sky with its searchlights, and the flames and streaks that go with speed.
## Nothing here reads the simulation, and nothing builds a texture until it is drawn.
##
## Draw calls: each car is one Model, drawn as one MultiMesh per (texture, blend, culling) part, so
## a car costs its parts (7 near, 5 far) whatever its placement; the sky is a handful of batches.

const HALF_W := 22.0
const TAIL := 0xFFFF2A2A

var _xf := Xform.new()
## For placing parts while a model is built, so building one mid-frame never disturbs _xf.
var _build_xf := Xform.new()
var _hi_models: Array = []
var _lo_models: Array = []
var _paints: Array = []


## A car's paint, painted once and shared by its detailed and plain models.
class CarPaint:
	extends RefCounted
	var side: Region
	var top: Region
	var rear: Region

	func _init(p_side: Region, p_top: Region, p_rear: Region) -> void:
		side = p_side
		top = p_top
		rear = p_rear


func _init() -> void:
	_hi_models.resize(RacerTuning.CARS)
	_lo_models.resize(RacerTuning.CARS)
	_paints.resize(RacerTuning.CARS)


static func _calm_k() -> float:
	return RacerLook.CALM_K if ScreenShake.intensity <= 0.0 else 1.0


# ---------------------------------------------------------------- cars

func _paint(i: int, color: int) -> CarPaint:
	var p: CarPaint = _paints[i]
	if p == null:
		p = CarPaint.new(RacerArt.car_side(color).full(), RacerArt.car_top(color).full(), RacerArt.car_rear(color).full())
		_paints[i] = p
	return p


## The car of [param color] for grid slot [param i], detailed or plain.
func _model(i: int, color: int, hi: bool) -> Model:
	var cache := _hi_models if hi else _lo_models
	var m: Model = cache[i]
	if m == null:
		m = _build_car(_paint(i, color)) if hi else _build_far_car(_paint(i, color))
		cache[i] = m
	return m


## Whether the detailed and plain models of slot [param i] have been built (tests).
func built(i: int) -> bool:
	return _hi_models[i] != null or _lo_models[i] != null


func _build_car(paint: CarPaint) -> Model:
	var side := paint.side
	var top := paint.top
	var rear := paint.rear
	var glass := RacerArt.glass().full()
	var white := TexKit.white().full()
	var g := RacerLook.BODY_GLOSS
	var b := ModelBuilder.new()
	# The tub, chamfered so its edges catch the light; the trunk deck on it; the cabin with its
	# sloping rear glass and sides.
	_beveled_box(b, -HALF_W, 4.0, -40.0, HALF_W, 15.0, 40.0, BoxFaces.new(rear, side, side, top, null, 0.0, 0.0, 0.0, g), 1.6)
	_beveled_box(b, -19.0, 15.0, 16.0, 19.0, 18.0, 39.0, BoxFaces.new(side, side, side, top, null, 0.0, 0.0, 0.0, g), 0.8)
	var gg := RacerLook.GLASS_GLOSS
	b.quad(-11.0, 27.0, 6.0, 11.0, 27.0, 6.0, 14.0, 15.0, 12.0, -14.0, 15.0, 12.0, glass, 0.0, 0.447, 0.894,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, gg)
	b.quad(-11.0, 27.0, -8.0, 11.0, 27.0, -8.0, 11.0, 27.0, 6.0, -11.0, 27.0, 6.0, top, 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, g)
	b.poly(PackedFloat32Array([-11.0, -11.0, -14.0, -14.0]), PackedFloat32Array([27.0, 27.0, 15.0, 15.0]), PackedFloat32Array([-8.0, 6.0, 12.0, -12.0]),
		PackedFloat32Array([0.0, 16.0, 16.0, 0.0]), PackedFloat32Array([0.0, 0.0, 8.0, 8.0]), glass, -0.968, 0.242, 0.0,
		Blend.OPAQUE, 0.0, true, -1, gg)
	b.poly(PackedFloat32Array([11.0, 11.0, 14.0, 14.0]), PackedFloat32Array([27.0, 27.0, 15.0, 15.0]), PackedFloat32Array([6.0, -8.0, -12.0, 12.0]),
		PackedFloat32Array([0.0, 16.0, 16.0, 0.0]), PackedFloat32Array([0.0, 0.0, 8.0, 8.0]), glass, 0.968, 0.242, 0.0,
		Blend.OPAQUE, 0.0, true, -1, gg)
	# Rear wing on two pillars, with end plates.
	_beveled_box(b, -24.0, 24.0, 31.0, 24.0, 26.2, 41.0, BoxFaces.new(side, side, side, top, null, 0.0, 0.0, 0.0, g), 0.5)
	b.box(-9.0, 15.0, 33.0, -6.0, 24.0, 37.0, BoxFaces.new(side, side, side))
	b.box(6.0, 15.0, 33.0, 9.0, 24.0, 37.0, BoxFaces.new(side, side, side))
	b.box(-24.6, 22.0, 31.0, -24.0, 28.0, 41.0, BoxFaces.new(side, side, side, side))
	b.box(24.0, 22.0, 31.0, 24.6, 28.0, 41.0, BoxFaces.new(side, side, side, side))
	# Tail lights: a bar across the tail and brighter lenses at each end.
	var z := 40.2
	b.quad(-19.0, 12.4, z, 19.0, 12.4, z, 19.0, 10.2, z, -19.0, 10.2, z, white, 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, RacerLook.TAIL_EMISSIVE, true, TAIL)
	b.quad(-19.0, 13.2, z + 0.05, -11.0, 13.2, z + 0.05, -11.0, 9.6, z + 0.05, -19.0, 9.6, z + 0.05, white, 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, RacerLook.TAIL_EMISSIVE * 1.4, true, 0xFFFF6A5A)
	b.quad(11.0, 13.2, z + 0.05, 19.0, 13.2, z + 0.05, 19.0, 9.6, z + 0.05, 11.0, 9.6, z + 0.05, white, 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, RacerLook.TAIL_EMISSIVE * 1.4, true, 0xFFFF6A5A)
	# Wheels.
	var wheel := _wheel_model()
	for sx in 2:
		for sz in 2:
			var x := -HALF_W - 2.0 + 4.5 if sx == 0 else HALF_W + 2.0 - 4.5
			var zc := -26.0 if sz == 0 else 26.0
			b.add_xf(wheel, _build_xf.set_xf(x, 8.0, zc, 0.0, 0.0, PI / 2.0))
	return b.build()


## HALL-PENDING: build-13 chamfers these three boxes with hub/CabinetForm.kt's beveledBox
## (floorAo 0, the given bevel), which the hall agent is porting. Until it is merged they are square
## boxes with the same faces and textures (so the same draw calls); see docs/parity/notes/games-c.md.
static func _beveled_box(b: ModelBuilder, x0: float, y0: float, z0: float, x1: float, y1: float, z1: float, f: BoxFaces, _bevel: float) -> void:
	b.box(x0, y0, z0, x1, y1, z1, f)


## One wheel, eight-sided, turned to lie with its axle along y (the caller rolls it into place): a
## tyre with a rim face on each side. The faces are two-sided, since a wheel is seen from whichever
## side the camera is on.
func _wheel_model() -> Model:
	var rim := RacerArt.wheel().full()
	var b := ModelBuilder.new().cylinder(0.0, 0.0, -4.5, 4.5, 8.0, 8, RacerArt.tyre().full())
	var n := 8
	for k in 2:
		var y := 4.5 if k == 0 else -4.5
		var xs := PackedFloat32Array()
		var ys := PackedFloat32Array()
		var zs := PackedFloat32Array()
		var us := PackedFloat32Array()
		var vs := PackedFloat32Array()
		xs.resize(n)
		ys.resize(n)
		zs.resize(n)
		us.resize(n)
		vs.resize(n)
		ys.fill(y)
		for i in n:
			var a := i * (2.0 * PI / n)
			xs[i] = cos(a) * 8.0
			zs[i] = sin(a) * 8.0
			us[i] = 8.0 + cos(a) * 8.0
			vs[i] = 8.0 + sin(a) * 8.0
		b.poly(xs, ys, zs, us, vs, rim, 0.0, 1.0 if k == 0 else -1.0, 0.0, Blend.OPAQUE, 0.0, false)
	return b.build()


## The cheap car for the distance: a body, a cabin, a wing and a tail-light bar.
func _build_far_car(paint: CarPaint) -> Model:
	var side := paint.side
	var top := paint.top
	var rear := paint.rear
	var glass := RacerArt.glass().full()
	var b := ModelBuilder.new()
	b.box(-HALF_W, 4.0, -40.0, HALF_W, 17.0, 40.0, BoxFaces.new(rear, side, side, top))
	b.box(-14.0, 17.0, -10.0, 14.0, 27.0, 12.0, BoxFaces.new(glass, glass, glass, top))
	b.box(-24.0, 24.0, 31.0, 24.0, 26.0, 41.0, BoxFaces.new(side, side, side, top))
	b.quad(-19.0, 12.4, 40.2, 19.0, 12.4, 40.2, 19.0, 10.2, 40.2, -19.0, 10.2, 40.2, TexKit.white().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, RacerLook.TAIL_EMISSIVE, true, TAIL)
	return b.build()


## Draws car [param i] (paint [param color]) at ([param x], [param y], [param z]) turned by
## [param yaw] and leaning by [param roll], pitched to the road's [param grade] (height gained per
## unit of distance) with a contact shadow and a glow of its colour on the road under it, both laid
## along the slope.
func draw_car(r: Renderer3D, i: int, color: int, hi: bool, x: float, y: float, z: float, yaw: float, roll: float, grade: float) -> void:
	_decal(r, TexKit.shadow().full(), Blend.ALPHA, 0.0, RacerLook.SHADOW_ALPHA, -1, x, y + 0.35, z, 58.0, 100.0, grade)
	_decal(r, TexKit.glow().full(), Blend.ADD, 1.0, RacerLook.UNDERGLOW_ALPHA, color, x, y + 0.6, z, 84.0, 138.0, grade)
	_xf.set_xf(x, y, z, yaw, grade, roll)
	_model(i, color, hi).draw(r, -1, 1.0, _xf)


## A [param w] × [param length] patch of road centred on ([param x], [param y], [param z]) that
## rises by [param grade] per unit towards the horizon.
static func _decal(r: Renderer3D, region: Region, blend: int, emissive: float, alpha: float, tint: int,
		x: float, y: float, z: float, w: float, length: float, grade: float) -> void:
	var hw := w / 2.0
	var hl := length / 2.0
	var y_far := y + grade * hl
	var y_near := y - grade * hl
	r.quad(x - hw, y_far, z - hl, x + hw, y_far, z - hl, x + hw, y_near, z + hl, x - hw, y_near, z + hl,
		region, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, blend, emissive, alpha, false, tint)


# ---------------------------------------------------------------- flames and speed

## The player's exhaust: two plumes of stacked glows that lengthen and flicker with
## [param boost_k] (0 to 1, ramping in and out), cyan for a turbo and gold for a super turbo, and a
## dimmer pair of cool flickers at full speed. [param x], [param y] are the car's; the tail is at z = 41.
func draw_flames(r: Renderer3D, x: float, y: float, boost_k: float, super_k: float, speed_k: float, t: float) -> void:
	var glow := TexKit.glow().full()
	if boost_k > 0.01:
		var hot := Pal.mix_argb(Pal.CYAN, Pal.ORANGE, super_k)
		var core := Pal.mix_argb(Pal.WHITE, Pal.YELLOW, super_k)
		var flick := 0.85 + 0.15 * sin(t * 70.0)
		for side in 2:
			var sx := x + (-9.0 if side == 0 else 9.0)
			for k in 4:
				var zz := 42.0 + k * 9.0 * boost_k
				var size := (18.0 - k * 2.5) * (0.6 + 0.4 * boost_k) * flick
				r.sprite(sx, y + 8.0, zz, size * 1.3, size, glow, 0.0, Blend.ADD, 1.2, boost_k * (0.8 - k * 0.17), 1.0, core if k < 2 else hot)
		r.sprite(x, y + 9.0, 62.0, 46.0 * boost_k + 12.0, 30.0 * boost_k + 8.0, glow, 0.0, Blend.ADD, 1.1, 0.45 * boost_k * flick, 1.0, hot)
	elif speed_k > 0.8:
		var a := (0.35 + 0.25 * sin(t * 50.0)) * clampf((speed_k - 0.8) / 0.2, 0.0, 1.0)
		r.sprite(x, y + 8.0, 46.0, 30.0, 18.0, glow, 0.0, Blend.ADD, 1.0, a, 1.0, Pal.CYAN)


## Streaks of light rushing past at speed and in a turbo: thin beams, toward the camera, beside and
## above the road, placed by hash so nothing is stored. [param speed_k] is speed as a share of top
## speed, [param boost_k] the turbo ramp; [param x], [param y] are the player's, [param t] the clock.
func draw_speed_lines(r: Renderer3D, x: float, y: float, speed_k: float, boost_k: float, t: float) -> void:
	var k := clampf((speed_k - RacerLook.SPEED_LINE_FROM) / (1.0 - RacerLook.SPEED_LINE_FROM), 0.0, 1.0) + boost_k * 0.6
	if k < 0.02:
		return
	var white := TexKit.white().full()
	var glow := TexKit.glow().full()
	var alpha := RacerLook.SPEED_LINE_ALPHA * minf(k, 1.4) * _calm_k()
	var tint := Pal.mix_argb(0xFFCFE8FF, Pal.CYAN, boost_k)
	var span := 900.0
	for i in RacerLook.SPEED_LINES:
		var side := 1.0 if MathUtil.hash01(i, 61) > 0.5 else -1.0
		var lx := x + side * (70.0 + MathUtil.hash01(i, 62) * 260.0)
		var ly := y + 6.0 + MathUtil.hash01(i, 63) * 120.0
		var z_pos := 300.0 - fmod(MathUtil.hash01(i, 64) * span + t * (2200.0 + 1600.0 * boost_k), span)
		var length := 40.0 + 130.0 * k
		# Streaks fade in and out along their run so they never pop.
		var life := clampf((300.0 - z_pos) / span, 0.0, 1.0)
		var fade := sin(life * PI)
		r.beam(lx, ly, z_pos, lx, ly, z_pos + length, 2.2, white, Blend.ADD, 1.0, alpha * fade, tint)
		if k > 0.6 and i % 4 == 0:
			r.sprite(lx, ly, z_pos, 10.0, 10.0, glow, 0.0, Blend.ADD, 1.0, alpha * fade * 0.6, 1.0, tint)


# ---------------------------------------------------------------- sky

## The sky behind the road: a horizon glow, twinkling stars, the striped sun with its halo, slow
## searchlights sweeping from the city, and two skylines and a mountain range that drift against
## the bend ahead at different rates ([param shift] is the bend's sideways drift).
func draw_sky(r: Renderer3D, shift: float, t: float) -> void:
	var far := -3200.0
	var glow := TexKit.glow().full()
	var calm := ScreenShake.intensity <= 0.0
	# Horizon glow, under everything else.
	r.quad(shift - 3400.0, 360.0, far - 20.0, shift + 3400.0, 360.0, far - 20.0, shift + 3400.0, -40.0, far - 20.0, shift - 3400.0, -40.0, far - 20.0,
		RacerArt.horizon().full(), 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, RacerLook.HORIZON_ALPHA, true, Pal.PINK)
	# Stars in the upper sky.
	var dot := TexKit.dot().full()
	for i in RacerLook.STARS:
		var x := shift * 0.2 + (MathUtil.hash01(i, 11) - 0.5) * 5200.0
		var y := 420.0 + MathUtil.hash01(i, 12) * 1300.0
		var tw := 0.75 if calm else 0.55 + 0.45 * sin(t * (1.2 + MathUtil.hash01(i, 13) * 1.6) + i * 3.1)
		var s := 9.0 + MathUtil.hash01(i, 14) * 12.0
		r.sprite(x, y, far - 40.0, s, s, dot, 0.0, Blend.ADD, 1.0, RacerLook.STAR_ALPHA * tw, 1.0, Pal.HOTPINK if i % 5 == 0 else 0xFFDDE8FF)
	# The sun, its halo, and a warm bloom round it.
	r.sprite(shift, 260.0, far, 1100.0, 1100.0, glow, 0.0, Blend.ADD, 1.0, 0.28, 1.0, Pal.ORANGE)
	r.sprite(shift, 260.0, far, 900.0, 900.0, glow, 0.0, Blend.ADD, 1.0, 0.55, 1.0, Pal.PINK)
	r.sprite(shift, 250.0, far + 10.0, 520.0, 520.0, RacerArt.sun().full(), 0.0, Blend.OPAQUE, 1.1)
	# Searchlights from the city, sweeping slowly.
	var shaft := TexKit.glow().full()
	for i in 3:
		var sway := 0.0 if calm else sin(t * 0.35 + i * 2.1) * 260.0
		var bx := shift * 1.5 + (i - 1) * 1300.0
		r.beam(bx, 0.0, far + 100.0, bx + sway, 1500.0, far + 100.0, 180.0, shaft, Blend.ADD, 1.0, RacerLook.SEARCHLIGHT_ALPHA, Pal.CYAN if i == 1 else Pal.HOTPINK)
	# Mountains, and the skylines in front of them, far to near.
	var m := RacerArt.mountains().full()
	var mx := shift * 1.3
	r.quad(mx - 3200.0, 330.0, far + 60.0, mx + 3200.0, 330.0, far + 60.0, mx + 3200.0, -40.0, far + 60.0, mx - 3200.0, -40.0, far + 60.0,
		m, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.9)
	var sf := RacerArt.skyline_far().full()
	var fx := shift * 1.45
	r.quad(fx - 2600.0, 170.0, far + 90.0, fx + 2600.0, 170.0, far + 90.0, fx + 2600.0, -40.0, far + 90.0, fx - 2600.0, -40.0, far + 90.0,
		sf, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.7)
	var s := RacerArt.skyline().full()
	var nx := shift * 1.6
	r.quad(nx - 2200.0, 190.0, far + 120.0, nx + 2200.0, 190.0, far + 120.0, nx + 2200.0, -40.0, far + 120.0, nx - 2200.0, -40.0, far + 120.0,
		s, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.8)
