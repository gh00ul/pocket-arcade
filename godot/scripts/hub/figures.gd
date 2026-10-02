class_name Figure
extends RefCounted
## hub/Figures.kt Figure: a 3D kid: sculpted body, painted face, hair and hat, with limbs that swing
## as they walk. Stands on its origin facing +z at yaw 0; about 44 units tall. (CharacterLook,
## Looks and Pose, the file's other classes, are character_look.gd, looks.gd and pose.gd.)
##
## The body is built in pieces that move against each other: the torso (with neck and apron), the
## head (face and hair; the hat rides on it), a ponytail that swings on its own, the eyelids for
## blinking, and the limbs. The torso is modelled about the hips and the head about the neck, so
## leaning and turning pivot where a body does.
##
## Godot: every piece is a model keyed by the textures it uses (a shirt colour, a skin colour, a
## face), shared by every figure that wears the same thing, so the renderer draws all the kids'
## legs of one colour, say, as one instanced batch. The pieces, their geometry and their textures
## are build-13's; only the grouping into models differs (a Kotlin leg is one model of pants and
## shoe, here a pants model and a shoe model drawn at the same placement).

const HIP_Y := 14.0
const SHOULDER_Y := 25.2
const SHOULDER_X := 7.3
const HEAD_Y := 35.5
const HEAD_R := 8.0
## The neck joint the head turns about (root coordinates).
const NECK_Y := 30.0

# Where the painted eyes sit on the head sphere, for the eyelids that blink over them: the top of
# each eye at EYE_LAT radians of latitude and ±EYE_LON of longitude from straight ahead.
const EYE_LAT := 0.129
const EYE_LON := 0.32
const LID_HALF_W := 1.25
const LID_HALF_H := 1.45
const LID_DEPTH := 0.5
## Below this closure a lid isn't drawn at all.
const BLINK_VISIBLE := 0.04

## Café treats a figure can hold: none, a slushie cup or a soft-serve cone.
const ITEM_NONE := 0
const ITEM_CUP := 1
const ITEM_CONE := 2

## A slushie cup's flavours.
const SLUSH_COLORS: Array[int] = [0xFF2FB8FF, 0xFFFF3D6E, 0xFF7CF25A, 0xFFB070FF]

const _EYE_R := HEAD_R + 0.05

static var _paints := {}
static var _solids := {}
static var _hat_models := {}
static var _propeller: Model = null
static var _cups: Array[Model] = []
static var _cone: Model = null
## Shared face and hair textures (kids with the same colouring reuse them).
static var _faces := {}
static var _hairs := {}
## Body pieces shared by every figure wearing the same colours (see the class notes).
static var _pieces := {}

var look: CharacterLook

var _torso: Model
var _neck: Model
var _apron: Model = null
var _head: Model
var _tail: Model = null
var _lid: Model
var _pants: Model
var _shoe: Model
var _upper_arm: Model
var _lower_arm: Model
var _hat_model: Model = null
var _propeller_hat := false
var _eye_x := 0.0
var _eye_top := 0.0
var _eye_z := 0.0

var _root := Xform.new()
var _spine := Xform.new()
var _neck_xf := Xform.new()
var _hat_xf := Xform.new()
var _part := Xform.new()
var _local := Xform.new()
var _held := Xform.new()
var _own_item: int
var _item_seed: int

## Animation state for [method draw_still], which poses a figure from scratch each call (portraits).
var _still := FigureAnim.new()


func _init(p_look: CharacterLook) -> void:
	look = p_look
	_eye_x = _EYE_R * cos(EYE_LAT) * sin(EYE_LON)
	_eye_top = HEAD_Y - NECK_Y + _EYE_R * sin(EYE_LAT)
	_eye_z = _EYE_R * cos(EYE_LAT) * cos(EYE_LON)
	if look.hat != Catalog.NONE:
		_hat_model = hat(look.hat)
		_propeller_hat = look.hat == Catalog.HatStyle.PROPELLER
	_own_item = held_item(look)
	_item_seed = (look.shirt ^ look.hair) & 0xFFFFFFFF
	_torso = _piece("torso|%x" % look.shirt, _build_torso.bind(look.shirt))
	_neck = _piece("neck|%x" % look.skin, _build_neck.bind(look.skin))
	if look.apron != 0:
		_apron = _piece("apron|%x" % look.apron, _build_apron.bind(look.apron))
	_head = _piece("head|%x|%x|%d" % [look.skin, look.hair, look.hair_style], _build_head.bind(look.skin, look.hair, look.hair_style))
	if look.hair_style == 2:
		_tail = _piece("tail|%x|%d" % [look.hair, look.hair_style], _build_tail.bind(look.hair, look.hair_style))
	_lid = _piece("lid|%x|%x" % [look.skin, look.hair], _build_lid.bind(look.skin, look.hair))
	_pants = _piece("pants|%x" % look.pants, _build_pants.bind(look.pants))
	_shoe = _piece("shoe|%x" % look.shoes, _build_shoe.bind(look.shoes))
	_upper_arm = _piece("uarm|%x" % look.shirt, _build_upper_arm.bind(look.shirt))
	_lower_arm = _piece("larm|%x" % look.skin, _build_lower_arm.bind(look.skin))


# ------------------------------------------------------------------ shared art

## A plastic/paint surface in [param color] (HallArt.paint(color, 0.2, 0.72)), as a region.
static func paint(color: int) -> Region:
	var c := color & 0xFFFFFFFF
	var t: PaTexture = _paints.get(c)
	if t == null:
		t = hall_paint(c, 0.2, 0.72)
		_paints[c] = t
	return t.full()


## HallArt.paint's recipe: a soft vertical gradient with fine grain (64 × 64), cached by colour and
## shading. The hall's HallArt is the hall's own file; this is the same texture for the figures'
## colours (see the porting notes).
static func hall_paint(color: int, top: float = 0.12, bottom: float = 0.8) -> PaTexture:
	var key := "%x|%d|%d" % [color & 0xFFFFFFFF, int(top * 1000.0), int(bottom * 1000.0)]
	var t: PaTexture = _paints.get(key)
	if t != null:
		return t
	var tp := TexPaint.new(64, 64)
	tp.vgrad(0.0, 0.0, 64.0, 64.0, [lift(color, top), dim(color, bottom)])
	tp.grain(0.03, MathUtil.i32(color))
	t = tp.to_texture()
	_paints[key] = t
	return t


## HallArt.solid: a flat 4 × 4 texture of [param color].
static func hall_solid(color: int) -> PaTexture:
	var c := color & 0xFFFFFFFF
	var t: PaTexture = _solids.get(c)
	if t == null:
		t = PaTexture.solid(4, 4, c)
		_solids[c] = t
	return t


## HallArt's colour helpers: [param c] darkened to [param f] of itself, lifted toward white by
## [param f], and given alpha [param a].
static func dim(c: int, f: float) -> int:
	return Pal.mix_argb(c, 0xFF000000, 1.0 - f)


static func lift(c: int, f: float) -> int:
	return Pal.mix_argb(c, 0xFFFFFFFF, f)


static func alpha(c: int, a: float) -> int:
	return (clampi(int(a * 255.0), 0, 255) << 24) | (c & 0xFFFFFF)


## What [param p_look] always buys at the café: most kids a slushie, some a cone.
static func held_item(p_look: CharacterLook) -> int:
	return ITEM_CONE if (p_look.hash_code() & 0x7FFFFFFF) % 3 == 0 else ITEM_CUP


## The model for café treat [param item] (a cup's colour picked by [param seed_value]), or null for none.
static func item_model(item: int, seed_value: int) -> Model:
	if item == ITEM_CUP:
		if _cups.is_empty():
			for c in SLUSH_COLORS:
				_cups.append(_cup(c))
		return _cups[(seed_value & 0x7FFFFFFF) % _cups.size()]
	if item == ITEM_CONE:
		if _cone == null:
			_cone = _build_cone()
		return _cone
	return null


## A slushie cup with a domed lid and a straw, gripped at its middle.
static func _cup(color: int) -> Model:
	var b := ModelBuilder.new()
	b.lathe(0.0, -3.2, 0.0, PackedFloat32Array([1.35, 0.0, 1.6, 2.4, 1.9, 6.4]), 12, paint(color), -1, 0.5)
	b.cylinder(0.0, 0.0, -1.1, 1.3, 1.72, 12, hall_solid(0xFFFFFFFF).full(), null, 0.0, -1, null, false, NAN, true, 0.3)
	b.sphere(0.0, 3.2, 0.0, 1.95, hall_solid(0xFFE8F4FF).full(), 12, 4, 0.45, -1, 0.9, 0.0, 0.0, 1.0)
	b.capsule(0.3, 3.6, 0.0, 0.9, 7.2, -0.3, 0.3, paint(0xFFFF4FA8), 5)
	return b.build()


static func _build_cone() -> Model:
	var b := ModelBuilder.new()
	var tp := TexPaint.new(32, 32)
	tp.fill(0xFFD89A4A)
	var k := -32
	while k < 64:
		tp.line(float(k), 0.0, k + 32.0, 32.0, 1.4, 0xFFA8692A)
		tp.line(k + 32.0, 0.0, float(k), 32.0, 1.4, 0xFFA8692A)
		k += 8
	var waffle := tp.to_texture()
	b.lathe(0.0, -4.5, 0.0, PackedFloat32Array([0.1, 0.0, 1.3, 3.5, 2.1, 6.0]), 12, waffle.full(), -1, 0.1)
	var cream := hall_paint(0xFFFFF4E4, 0.3, 0.85).full()
	b.sphere(0.0, 2.1, 0.0, 2.2, cream, 12, 6, 0.7, -1, 0.5)
	b.sphere(0.0, 3.6, 0.0, 1.6, hall_paint(0xFFFF9EC8, 0.3, 0.85).full(), 10, 5, 0.8, -1, 0.5)
	b.cylinder(0.0, 0.0, 4.6, 6.0, 0.9, 8, cream, null, 0.0, -1, null, false, 0.05)
	return b.build()


static func _propeller_model() -> Model:
	if _propeller == null:
		var b := ModelBuilder.new()
		var c := paint(0xFFFF4D4D)
		b.box(-6.0, 0.0, -0.8, 6.0, 0.5, 0.8, BoxFaces.all(c, 0.5))
		b.box(-0.8, 0.0, -6.0, 0.8, 0.5, 6.0, BoxFaces.all(paint(0xFF4DA6FF), 0.5))
		_propeller = b.build()
	return _propeller


## A hat model in head coordinates (head centre at the origin, radius [constant HEAD_R]).
static func hat(style: int) -> Model:
	var m: Model = _hat_models.get(style)
	if m == null:
		m = _build_hat(style)
		_hat_models[style] = m
	return m


## The spinning propeller on top of the PROPELLER hat (for warm-up lists).
static func propeller() -> Model:
	return _propeller_model()


static func _build_hat(style: int) -> Model:
	var b := ModelBuilder.new()
	var r := HEAD_R
	if style == Catalog.HatStyle.CAP:
		var c := paint(0xFFE8323C)
		b.sphere(0.0, 0.0, 0.0, r + 0.6, c, 18, 8, 1.0, -1, 0.2, 0.0, 0.28, 1.0)
		b.box(-5.5, 2.2, 4.0, 5.5, 2.8, 12.5, BoxFaces.all(c, 0.2))
		b.sphere(0.0, r + 0.4, 0.0, 1.0, c)
	elif style == Catalog.HatStyle.BEANIE:
		var tp := TexPaint.new(64, 64)
		tp.fill(0xFF3DDC84)
		var y := 0
		while y < 64:
			tp.rect(0.0, float(y), 64.0, 5.0, 0xFFFFE14D)
			y += 12
		tp.grain(0.12, 5)
		var stripes := tp.to_texture()
		b.sphere(0.0, 0.5, 0.0, r + 0.8, stripes.full(), 18, 8, 1.08, -1, 0.0, 0.0, 0.18, 1.0)
		b.cylinder(0.0, 0.0, 1.0, 3.6, r + 0.9, 18, paint(0xFF3DDC84))
		b.sphere(0.0, r + 2.6, 0.0, 2.4, paint(0xFFFFE14D))
	elif style == Catalog.HatStyle.PARTY:
		var tp := TexPaint.new(128, 64)
		tp.fill(0xFF39E6F2)
		var x := 0
		while x < 128:
			tp.rect(float(x), 0.0, 8.0, 64.0, 0xFFFF4FA8)
			x += 16
		for k in 20:
			tp.circle(float(k * 37 % 128), float(k * 23 % 64), 2.0, 0xFFFFE14D)
		var tex := tp.to_texture()
		b.cylinder(0.0, 0.0, 5.5, 17.0, 4.8, 14, tex.full(), null, 0.0, -1, null, false, 0.2, true, 0.3)
		b.sphere(0.0, 17.2, 0.0, 1.8, paint(0xFFFFE14D))
	elif style == Catalog.HatStyle.HEADPHONES:
		var band := paint(0xFF22222A)
		var band3 := ModelBuilder.new().torus(0.0, 0.0, 0.0, r + 1.1, 0.8, band, 20, 6, -1, 0.5).build()
		b.add_xf(band3, Xform.new().set_xf(0.0, 0.5, 0.0, 0.0, 0.0, PI / 2.0))
		var dark := paint(0xFF22222A)
		var cup := ModelBuilder.new().cylinder(0.0, 0.0, -1.4, 1.4, 3.3, 14, paint(0xFFFF4FA8), dark, 0.0, -1, dark, false, NAN, true, 0.6).build()
		b.add_xf(cup, Xform.new().set_xf(-r - 0.6, -0.5, 0.0, 0.0, 0.0, PI / 2.0))
		b.add_xf(cup, Xform.new().set_xf(r + 0.6, -0.5, 0.0, 0.0, 0.0, PI / 2.0))
	elif style == Catalog.HatStyle.COWBOY:
		var c := paint(0xFFB5763C)
		b.lathe(0.0, 2.4, 0.0, PackedFloat32Array([r + 6.5, 1.8, r + 5.0, 0.4, r - 0.5, 0.0, r - 1.5, 0.2]), 20, c, -1, 0.2)
		b.lathe(0.0, 2.4, 0.0, PackedFloat32Array([r - 1.2, 0.0, r - 1.2, 1.0, r - 1.6, 6.0, r - 2.4, 8.5, 0.1, 9.0]), 18, c, -1, 0.2)
		b.cylinder(0.0, 0.0, 2.6, 4.0, r - 1.1, 18, paint(0xFF3A2414))
	elif style == Catalog.HatStyle.PROPELLER:
		var tp := TexPaint.new(128, 32)
		var cs: Array[int] = [0xFFFF4D4D, 0xFFFFE14D, 0xFF3DDC84, 0xFF4DA6FF]
		for k in 8:
			tp.rect(k * 16.0, 0.0, 16.0, 32.0, cs[k % 4])
		var tex := tp.to_texture()
		b.sphere(0.0, 0.0, 0.0, r + 0.6, tex.full(), 16, 8, 1.0, -1, 0.3, 0.0, 0.3, 1.0)
		b.capsule(0.0, r + 0.3, 0.0, 0.0, r + 3.0, 0.0, 0.4, paint(0xFF888888))
	elif style == Catalog.HatStyle.WIZARD:
		var tp := TexPaint.new(128, 128)
		tp.vgrad(0.0, 0.0, 128.0, 128.0, [0xFF4A2A9A, 0xFF2A1060])
		for k in 18:
			var x := float(k * 53 % 128)
			var y := float(k * 29 % 128)
			tp.text("★", x, y, 16.0, 0xFFFFE14D, Fonts.heavy())
		var tex := tp.to_texture()
		b.cylinder(0.0, 0.0, 3.5, 22.0, r - 0.5, 16, tex.full(), null, 0.0, -1, null, false, 0.3, true, 0.2)
		b.lathe(0.0, 3.4, 0.0, PackedFloat32Array([r + 5.0, 0.3, r + 4.5, 0.0, r - 1.0, 0.2]), 18, tex.full())
	elif style == Catalog.HatStyle.TOPHAT:
		var c := paint(0xFF1A1820)
		b.cylinder(0.0, 0.0, 3.5, 15.0, r - 1.5, 18, c, c, 0.0, -1, null, false, NAN, true, 0.5)
		b.cylinder(0.0, 0.0, 3.5, 5.0, r - 1.4, 18, paint(0xFFE8323C))
		b.lathe(0.0, 3.3, 0.0, PackedFloat32Array([r + 3.0, 0.5, r + 3.0, 0.0, r - 1.5, 0.1]), 18, c, -1, 0.5)
	elif style == Catalog.HatStyle.CROWN:
		var gold := hall_paint(0xFFFFC83D, 0.35, 0.7).full()
		b.cylinder(0.0, 0.0, 4.0, 7.5, r - 1.0, 18, gold, null, 0.0, -1, null, false, r - 0.6, true, 0.9)
		for k in 6:
			var a := k * PI / 3.0
			var x := cos(a) * (r - 0.8)
			var z := sin(a) * (r - 0.8)
			b.cylinder(x, z, 7.4, 11.0, 1.3, 6, gold, null, 0.0, -1, null, false, 0.1, true, 0.9)
			b.sphere(x, 11.3, z, 0.6, gold, 6, 4, 1.0, -1, 0.9)
		b.sphere(0.0, 5.8, r - 0.2, 1.2, paint(0xFFE8323C), 16, 10, 1.0, -1, 1.0)
	elif style == Catalog.HatStyle.HALO:
		var halo_gold := hall_solid(0xFFFFE99A).full()
		b.torus(0.0, 13.5, 0.0, 6.0, 0.7, halo_gold, 22, 6, -1, 0.0, 1.6)
	return b.build()


# ------------------------------------------------------------------ the body's pieces

static func _piece(key: String, build: Callable) -> Model:
	var m: Model = _pieces.get(key)
	if m == null:
		m = build.call()
		_pieces[key] = m
	return m


## Torso: a rounded, slightly flattened shape from the hips to the shoulders (about the hips).
static func _build_torso(shirt: int) -> Model:
	var shape := ModelBuilder.new().lathe(0.0, 0.0, 0.0,
		PackedFloat32Array([0.0, 12.0, 5.4, 12.3, 6.3, 14.0, 6.4, 18.0, 6.5, 22.0, 6.1, 24.6, 4.8, 26.4, 2.4, 27.4]),
		16, paint(shirt), -1, 0.05).build()
	return ModelBuilder.new().add_scaled(shape, 1.0, 1.0, 0.76, 0.0, -HIP_Y, 0.0).build()


## The neck, part of the torso in build-13 (drawn at the torso's placement).
static func _build_neck(skin: int) -> Model:
	return ModelBuilder.new().cylinder(0.0, 0.0, 26.5 - HIP_Y, 29.5 - HIP_Y, 2.1, 10, paint(skin)).build()


## Staff wear an apron with a bib and a pocket over the shirt (part of the torso in build-13).
static func _build_apron(apron_color: int) -> Model:
	var b := ModelBuilder.new()
	var apron := paint(apron_color)
	var trim := paint(lift(apron_color, 0.45))
	b.box(-5.2, 7.5 - HIP_Y, 4.1, 5.2, 20.5 - HIP_Y, 5.5, BoxFaces.new(apron, apron, apron, null, null, 0.0, 0.0, 0.0, 0.1))
	b.box(-3.4, 20.5 - HIP_Y, 3.9, 3.4, 25.2 - HIP_Y, 5.2, BoxFaces.new(apron, apron, apron, apron, null, 0.0, 0.0, 0.0, 0.1))
	b.box(-2.6, 12.5 - HIP_Y, 5.5, 2.6, 15.8 - HIP_Y, 5.8, BoxFaces.new(trim, trim, trim, trim))
	b.box(-6.5, 18.6 - HIP_Y, -5.0, 6.5, 19.8 - HIP_Y, 4.2, BoxFaces.new(null, apron, apron, apron, apron))
	return b.build()


## Head with the painted face, and hair over it (about the neck).
static func _build_head(skin: int, hair_color: int, style: int) -> Model:
	var face_region := face(skin, hair_color)
	var hair := hair_texture(hair_color, style)
	var hy := HEAD_Y - NECK_Y
	var hb := ModelBuilder.new()
	hb.sphere(0.0, hy, 0.0, HEAD_R, face_region, 20, 14, 1.0, -1, 0.08)
	hb.sphere(0.0, hy + 0.4, -0.2, HEAD_R + 0.55, hair, 20, 12, 1.0, -1, 0.25, 0.0, -0.35, 1.0)
	if style == 1:
		for k in 5:
			var a := -0.9 + k * 0.45
			hb.cylinder(sin(a) * 4.0, -1.0 - cos(a) * 2.0, hy + 6.5, hy + 11.0, 2.0, 6, hair, null, 0.0, -1, null, false, 0.2)
	return hb.build()


## A ponytail, hanging from a joint of its own.
static func _build_tail(hair_color: int, style: int) -> Model:
	return ModelBuilder.new().capsule(0.0, 0.0, 0.0, 0.0, -11.0, 0.5, 3.0, hair_texture(hair_color, style)).build()


## An eyelid: half an ellipsoid of forehead skin hanging from the top of the eye, which is drawn
## squashed down over the painted eye to blink.
static func _build_lid(skin: int, hair_color: int) -> Model:
	var face_region := face(skin, hair_color)
	var patch := Region.new(face_region.tex, 54, 22, 20, 12)
	var ball := ModelBuilder.new().sphere(0.0, 0.0, 0.0, 1.0, patch, 8, 6).build()
	return ModelBuilder.new().add_scaled(ball, LID_HALF_W, LID_HALF_H, LID_DEPTH, 0.0, -LID_HALF_H, 0.0).build()


## A leg's trouser (build-13's leg is this and [method _build_shoe] in one model).
static func _build_pants(pants: int) -> Model:
	return ModelBuilder.new().capsule(0.0, 0.0, 0.0, 0.0, -11.2, 0.0, 2.7, paint(pants)).build()


static func _build_shoe(shoes: int) -> Model:
	return ModelBuilder.new().capsule(0.0, -12.2, -0.8, 0.0, -12.2, 3.2, 2.1, paint(shoes)).build()


## An arm's sleeve (build-13's arm is this and [method _build_lower_arm] in one model).
static func _build_upper_arm(shirt: int) -> Model:
	return ModelBuilder.new().capsule(0.0, 0.0, 0.0, 0.0, -5.5, 0.0, 2.2, paint(shirt)).build()


## The forearm and hand.
static func _build_lower_arm(skin: int) -> Model:
	var s := paint(skin)
	return ModelBuilder.new().capsule(0.0, -5.5, 0.0, 0.0, -10.5, 0.0, 1.7, s).sphere(0.0, -11.8, 0.0, 2.0, s).build()


# ------------------------------------------------------------------ drawing

## Every model this figure draws (warm-ups).
func models() -> Array[Model]:
	var out: Array[Model] = [_torso, _neck, _head, _lid, _pants, _shoe, _upper_arm, _lower_arm]
	if _apron != null:
		out.append(_apron)
	if _tail != null:
		out.append(_tail)
	if _hat_model != null:
		out.append(_hat_model)
	if _propeller_hat:
		out.append(_propeller_model())
	return out


## Draws the figure standing at ([param x], [param y], [param z]) facing [param yaw], in
## [param pose] at moment [param time] of its idle motion and [param phase] of its stride. With no
## history it can't blend, so this is for portraits and pictures; a figure that moves keeps a
## [FigureAnim] and uses [method draw]. (Kotlin's first `draw` overload.)
func draw_still(r: Renderer3D, x: float, y: float, z: float, yaw: float, pose: int, phase: float, time: float, p_scale: float = 1.0, item: int = -1) -> void:
	_still.set_static(pose, time, phase, yaw)
	draw(r, x, y, z, _still, p_scale, item)


## Draws the figure standing at ([param x], [param y], [param z]) as [param a] has it: its facing,
## pose, gait, gaze and follow-through. In the holding poses the hand carries café treat
## [param item] (by default the one this look always buys).
func draw(r: Renderer3D, x: float, y: float, z: float, a: FigureAnim, p_scale: float = 1.0, item: int = -1) -> void:
	var yaw := a.yaw
	var cy := cos(yaw)
	var sy := sin(yaw)
	var local := _local
	var part := _part
	var spine := _spine
	var neck := _neck_xf
	# Weight shifts sway the whole body sideways, along its own x axis.
	_root.set_xf(x + cy * a.sway * p_scale, y + a.root_y * p_scale, z - sy * a.sway * p_scale, yaw, 0.0, 0.0, p_scale)
	# The spine turns and leans about the hips; breathing swells the chest a touch.
	local.set_xf(0.0, HIP_Y, 0.0, a.twist, a.lean, a.lean_roll)
	spine.set_product(_root, local)
	var br := a.breath
	part.copy_from(spine).stretch(1.0 + br * 0.5, 1.0 + br, 1.0 + br * 0.5)
	_torso.draw(r, Blend.OPAQUE, 1.0, part)
	_neck.draw(r, Blend.OPAQUE, 1.0, part)
	if _apron != null:
		_apron.draw(r, Blend.OPAQUE, 1.0, part)
	# The head turns about the neck; the hat and ponytail follow it, each with a little lag.
	local.set_xf(0.0, NECK_Y - HIP_Y + a.breath_lift, 0.0, a.head_yaw, a.head_pitch, a.head_roll)
	neck.set_product(spine, local)
	_head.draw(r, Blend.OPAQUE, 1.0, neck)
	if a.blink > BLINK_VISIBLE:
		for s in 2:
			var side := -1.0 if s == 0 else 1.0
			local.set_xf(side * _eye_x, _eye_top, _eye_z, side * EYE_LON, -EYE_LAT).stretch(1.0, a.blink, 1.0)
			part.set_product(neck, local)
			_lid.draw(r, Blend.OPAQUE, 1.0, part)
	if _hat_model != null:
		local.set_xf(0.0, HEAD_Y - NECK_Y + a.hat_lift, 0.0, 0.0, a.hat_pitch, a.hat_roll)
		_hat_xf.set_product(neck, local)
		_hat_model.draw(r, -1, 1.0, _hat_xf)
		if _propeller_hat:
			local.set_xf(0.0, HEAD_R + 3.0, 0.0, a.clock * 14.0)
			part.set_product(_hat_xf, local)
			_propeller_model().draw(r, Blend.OPAQUE, 1.0, part)
	if _tail != null:
		local.set_xf(0.0, HEAD_Y - NECK_Y + 2.0, -7.5, 0.0, a.tail_pitch, a.tail_roll)
		part.set_product(neck, local)
		_tail.draw(r, Blend.OPAQUE, 1.0, part)
	# Legs swing from the hips (or stick out forwards when sitting).
	for s in 2:
		local.set_xf((-3.1 if s == 0 else 3.1), HIP_Y + a.leg_lift[s], 0.0, 0.0, a.leg_pitch[s])
		part.set_product(_root, local)
		_pants.draw(r, Blend.OPAQUE, 1.0, part)
		_shoe.draw(r, Blend.OPAQUE, 1.0, part)
	# Arms hang from the shoulders, so they lean and twist with the spine.
	for s in 2:
		local.set_xf((-SHOULDER_X if s == 0 else SHOULDER_X), SHOULDER_Y - HIP_Y + a.breath_lift, 0.0, 0.0, a.arm_pitch[s], a.arm_roll[s])
		part.set_product(spine, local)
		_upper_arm.draw(r, Blend.OPAQUE, 1.0, part)
		_lower_arm.draw(r, Blend.OPAQUE, 1.0, part)
		# The treat stays upright in the right hand.
		if s == 1 and a.item_amount > 0.02:
			var m := item_model(item if item >= 0 else _own_item, _item_seed)
			if m != null:
				_held.set_xf(part.x(0.0, -12.6, 1.2), part.y(0.0, -12.6, 1.2), part.z(0.0, -12.6, 1.2), yaw, 0.0, 0.0, p_scale * a.item_amount)
				m.draw(r, Blend.OPAQUE, 1.0, _held)


# ------------------------------------------------------------------ textures

## Skin with eyes, brows, a smile and rosy cheeks, centred where the sphere faces +z.
static func face(skin: int, hair_color: int) -> Region:
	var key := "%x|%x" % [skin & 0xFFFFFFFF, hair_color & 0xFFFFFFFF]
	var t: PaTexture = _faces.get(key)
	if t == null:
		t = _paint_face(skin, hair_color)
		_faces[key] = t
	return t.full()


static func _paint_face(skin: int, hair_color: int) -> PaTexture:
	var w := 256
	var h := 128
	var tp := TexPaint.new(w, h)
	tp.vgrad(0.0, 0.0, float(w), float(h), [lift(skin, 0.08), dim(skin, 0.88)])
	var cx := w * 0.25
	var cy := h * 0.5
	var ink := 0xFF1C140E
	for s: int in [-1, 1]:
		var ex := cx + s * 13.0
		tp.oval(ex, cy + 2.0, 5.2, 7.0, 0xFFFFFFFF)
		tp.oval(ex, cy + 3.0, 4.2, 5.6, ink)
		tp.circle(ex - 1.5, cy + 0.5, 1.6, 0xFFFFFFFF)
		tp.line(ex - 5.0, cy - 8.0, ex + 5.0, cy - 9.5 - s * 0.5, 2.2, dim(hair_color, 0.8))
		tp.oval(cx + s * 21.0, cy + 14.0, 5.0, 3.0, 0x55FF5A7A)
	var p := tp.paint
	p.reset()
	p.style = PaPaint.Style.STROKE
	p.stroke_width = 2.4
	p.round_cap = true
	p.color = 0xFF7A2A20
	tp.c_draw_arc(cx - 7.0, cy + 10.0, cx + 7.0, cy + 21.0, 20.0, 140.0, false, p)
	tp.oval(cx, cy + 9.0, 2.0, 1.4, dim(skin, 0.8))
	return tp.to_texture()


## Hair colour with soft strands; the face area is left clear (cut out).
static func hair_texture(hair_color: int, style: int) -> Region:
	var key := "%x|%d" % [hair_color & 0xFFFFFFFF, style]
	var t: PaTexture = _hairs.get(key)
	if t == null:
		t = _paint_hair(hair_color, style)
		_hairs[key] = t
	return t.full()


static func _paint_hair(hair_color: int, style: int) -> PaTexture:
	var w := 256
	var h := 128
	var tp := TexPaint.new(w, h)
	tp.vgrad(0.0, 0.0, float(w), float(h), [lift(hair_color, 0.12), dim(hair_color, 0.75)])
	var strand := alpha(lift(hair_color, 0.25), 0.35)
	for k in 90:
		var x := float(k * 53 % w)
		tp.line(x, 0.0, x + 6.0, h * 0.9, 1.2, strand)
	# Cut out the face: fringe line across the forehead, open down to the chin.
	var cx := w * 0.25
	var half_w := 32.0 if style == 2 else 42.0
	var fringe := 44.0 if style == 1 else 50.0
	var p := tp.paint
	p.reset()
	p.xfer = PaPaint.Xfer.CLEAR
	tp.c_draw_round_rect(cx - half_w, fringe, cx + half_w, h + 20.0, 16.0, 16.0, p)
	p.xfer = PaPaint.Xfer.NONE
	return tp.to_texture()
