class_name HoopsScene
extends RefCounted
## games/hoops/HoopsScene.kt: the hoops alley as a 3D scene: an arena wall with a painted crowd,
## LED ad boards, a neon sign and wall readouts, a glowing backboard on a rail, the rim and net,
## and every effect that answers the game (makes, rim and board hits, streaks). It keeps all the
## visual state (glows that decay, trails, shockwaves) so the game's simulation never reads it and
## rendering never touches the game's random generator: visual randomness is hash-based.
##
## Nothing here builds a texture until it is drawn, so headless tests can step a game freely.
## (HoopsGeo and HoopsLook, declared in the same Kotlin file, are hoops_geo.gd and hoops_look.gd.)

const S := HoopsGeo.S
const BALLS := 6
const TRAIL_N := 7
## Seconds between trail samples, and how long a sample lives.
const TRAIL_DT := 0.014
const TRAIL_LIFE := 0.13
const BURSTS := 10
const K_RING_FLAT := 0
const K_RING_UP := 1
const K_FLARE := 2
const K_GLOW := 3
const FLASHES := 12
const MOTES := 12


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
var _flash := 0.0
var _rim_flash := 0.0
var _board_flash := 0.0
var _board_hit_x := 0.0
var _board_hit_y := 0.0
var _hype := 0.0
var _trail_pos := PackedFloat32Array()
var _trail_age := PackedFloat32Array()
var _trail_head := PackedInt32Array()
var _trail_gap := PackedFloat32Array()


func _init() -> void:
	for i in BURSTS:
		_bursts.append(Burst.new())
	_trail_pos.resize(BALLS * TRAIL_N * 3)
	_trail_age.resize(BALLS * TRAIL_N)
	_trail_age.fill(99.0)
	_trail_head.resize(BALLS)
	_trail_gap.resize(BALLS)


## Clears every glow and trail, for a fresh round.
func reset() -> void:
	for b in _bursts:
		b.age = b.life
	_flash = 0.0
	_rim_flash = 0.0
	_board_flash = 0.0
	_hype = 0.0
	_trail_age.fill(99.0)
	_trail_head.fill(0)
	_trail_gap.fill(0.0)


func _calm_k() -> float:
	return HoopsLook.CALM_K if ScreenShake.intensity <= 0.0 else 1.0


## Decays the glows and ages the trails (called from the game's fixed step).
func step(dt: float) -> void:
	_flash = maxf(_flash - dt * 2.4, 0.0)
	_rim_flash = maxf(_rim_flash - dt * 6.0, 0.0)
	_board_flash = maxf(_board_flash - dt * 3.5, 0.0)
	_hype = maxf(_hype - dt * 0.12, 0.0)
	for b in _bursts:
		if b.age < b.life:
			b.age += dt
	var ages := _trail_age
	for i in ages.size():
		ages[i] += dt
	for i in BALLS:
		_trail_gap[i] -= dt


## Records where ball [param i] is (scene units) so a glowing trail can follow it.
func trail(i: int, x: float, y: float, z: float) -> void:
	if _trail_gap[i] > 0.0:
		return
	_trail_gap[i] = TRAIL_DT
	var slot := (_trail_head[i] + 1) % TRAIL_N
	_trail_head[i] = slot
	var at := i * TRAIL_N + slot
	_trail_pos[at * 3] = x
	_trail_pos[at * 3 + 1] = y
	_trail_pos[at * 3 + 2] = z
	_trail_age[at] = 0.0


func _burst(kind: int, x: float, y: float, z: float, s0: float, s1: float, life: float, alpha: float, tint: int, delay: float = 0.0) -> void:
	var b := _bursts[_burst_next]
	_burst_next = (_burst_next + 1) % BURSTS
	b.kind = kind
	b.x = x
	b.y = y
	b.z = z
	b.s0 = s0
	b.s1 = s1
	b.life = life
	b.age = -delay
	b.alpha = alpha * _calm_k()
	b.tint = tint


## Colour of the game's state: orange at rest, hot pink on a combo, gold on fire.
func _state_color(streak: int) -> int:
	if streak >= HoopsTuning.MAX_MULTIPLIER:
		return Pal.GOLD
	if streak >= 2:
		return Pal.PINK
	return Pal.ORANGE


## _state_color, flickering between orange and yellow while the hoop is on fire.
func _accent(streak: int, t: float) -> int:
	if streak >= HoopsTuning.MAX_MULTIPLIER:
		return Pal.mix_argb(Pal.ORANGE, Pal.YELLOW, 0.5 + 0.5 * sin(t * 13.0))
	return _state_color(streak)


# ---------------------------------------------------------------- events

## A ball dropped through the hoop: a shockwave round the rim, a glint and a pulse through every light.
func made(hoop_x: float, swish: bool, streak: int) -> void:
	var cx := hoop_x * S
	var ry := HoopsGeo.RIM_Y * S
	var cz := -HoopsGeo.HOOP_Z * S
	var color := _state_color(streak)
	_flash = _calm_k()
	_hype = MathUtil.clamp01(_hype + 0.25 + 0.1 * streak)
	var rim_r := HoopsGeo.RIM_R * S
	_burst(K_RING_FLAT, cx, ry - 3.0, cz, rim_r * 2.0, rim_r * 8.0, 0.6, 0.85, color)
	if streak >= 2:
		_burst(K_RING_FLAT, cx, ry - 3.0, cz, rim_r * 1.6, rim_r * 6.0, 0.5, 0.6, Pal.WHITE, 0.08)
	_burst(K_FLARE, cx, ry - 10.0, cz + 8.0, 30.0, 190.0 if swish else 130.0, 0.4, 1.0, Pal.mix(color, Pal.WHITE, 0.5))
	_burst(K_GLOW, cx, ry - 8.0, cz, 100.0, 240.0, 0.5, 0.6, color)


## A ball clipped the rim at scene position ([param x], [param y], [param z]).
func rim_hit(x: float, y: float, z: float) -> void:
	_rim_flash = _calm_k()
	_burst(K_FLARE, x, y, z + 6.0, 14.0, 60.0, 0.22, 0.9, Pal.mix(Pal.ORANGE, Pal.WHITE, 0.5))


## A ball struck the backboard at [param x] (scene units from the board's centre) and height [param y].
func board_hit(hoop_x: float, x: float, y: float) -> void:
	_board_flash = _calm_k()
	_board_hit_x = x
	_board_hit_y = y
	_burst(K_RING_UP, hoop_x * S + x, y, -HoopsGeo.BOARD_Z * S + 2.0, 10.0, 78.0, 0.32, 0.8, Pal.mix(Pal.SKY, Pal.WHITE, 0.4))


## A ball landed on the court at ([param x], [param z]) with [param strength] 0..1.
func floor_hit(x: float, z: float, strength: float) -> void:
	_burst(K_RING_FLAT, x, 0.9, z, 10.0, 14.0 + strength * 48.0, 0.3, 0.3 * strength + 0.05, 0xFFFFD6A0)


# ---------------------------------------------------------------- lights

var _gym := PointLight.new(0.0, HoopsLook.GYM_Y, HoopsLook.GYM_Z, 1.0, 0.9, 0.75, HoopsLook.GYM_RADIUS, HoopsLook.GYM_INTENSITY)
var _spot := PointLight.new(0.0, HoopsLook.SPOT_Y, HoopsLook.SPOT_Z, 0.65, 0.78, 1.0, HoopsLook.SPOT_RADIUS, HoopsLook.SPOT_INTENSITY)
var _ready_light := PointLight.new(0.0, HoopsLook.READY_Y, HoopsLook.READY_Z, 1.0, 0.85, 0.65, HoopsLook.READY_RADIUS, HoopsLook.READY_INTENSITY)
var _accent_light := PointLight.new(0.0, HoopsGeo.RIM_Y * S + 5.0, -HoopsGeo.HOOP_Z * S + 10.0, 1.0, 0.55, 0.2, HoopsLook.ACCENT_RADIUS, HoopsLook.ACCENT_REST)


## Sets the room's lighting and the renderer's look for this frame.
func light(r: Renderer3D, hoop_x: float, streak: int, t: float) -> void:
	var l: Lighting = r.lighting
	l.amb_r = HoopsLook.AMB_R
	l.amb_g = HoopsLook.AMB_G
	l.amb_b = HoopsLook.AMB_B
	l.set_direction(0.15, 1.0, 0.55)
	l.dir_r = HoopsLook.DIR_R
	l.dir_g = HoopsLook.DIR_G
	l.dir_b = HoopsLook.DIR_B
	var fire := streak >= HoopsTuning.MAX_MULTIPLIER
	_accent_light.x = hoop_x * S
	var k := HoopsLook.ACCENT_REST + _flash * HoopsLook.ACCENT_MAKE + _rim_flash * HoopsLook.ACCENT_RIM
	if fire:
		k += 0.9 + 0.35 * sin(t * 14.0)
	_accent_light.intensity = k
	if fire:
		_accent_light.r = 1.0
		_accent_light.g = 0.5
		_accent_light.b = 0.12
	elif streak >= 2:
		_accent_light.r = 1.0
		_accent_light.g = 0.3
		_accent_light.b = 0.7
	else:
		_accent_light.r = 1.0
		_accent_light.g = 0.55
		_accent_light.b = 0.2
	_spot.x = hoop_x * S * 0.5
	l.points.clear()
	l.points.append(_gym)
	l.points.append(_spot)
	l.points.append(_accent_light)
	l.points.append(_ready_light)
	r.vignette = HoopsLook.VIGNETTE
	r.bloom = HoopsLook.BLOOM
	r.floor_reflect = HoopsLook.FLOOR_REFLECT


# ---------------------------------------------------------------- models

var _room: Model = null
var _board_model: Model = null
var _led_model: Model = null
var _rim_model: Model = null


func _room_model() -> Model:
	if _room == null:
		_room = _build_room()
	return _room


## The rim with its mounting plate, centred on the hoop (place it each frame).
func rim_model() -> Model:
	if _rim_model == null:
		_rim_model = _build_rim()
	return _rim_model


func _steel_faces(gloss: float = HoopsLook.FRAME_GLOSS) -> BoxFaces:
	var s := HoopsArt.steel().full()
	return BoxFaces.new(s, s, s, s, null, 0.0, 0.0, 0.0, gloss)


## hub/CabinetForm.kt ModelBuilder.beveledBox (chamfered edges, floor occlusion). [param bevel] and
## [param floor_ao] NAN mean the hall's defaults. Until the hall's port is merged this builds the
## plain box (see docs/parity/notes/games-b.md).
static func _beveled_box(b: ModelBuilder, x0: float, y0: float, z0: float, x1: float, y1: float, z1: float,
		f: BoxFaces, tint: int = -1, _bevel: float = NAN, _floor_ao: float = NAN) -> void:
	b.box(x0, y0, z0, x1, y1, z1, f, tint)


func _build_room() -> Model:
	var b := ModelBuilder.new()
	var w := HoopsGeo.CAGE_HALF_W * S
	var back := HoopsGeo.WALL_Z
	var hw := HoopsLook.WALL_HALF_W
	var steel := HoopsArt.steel().full()
	# The arena wall, rising into the dark.
	b.quad(-hw, HoopsLook.WALL_TOP, back, hw, HoopsLook.WALL_TOP, back, hw, 0.0, back, -hw, 0.0, back, HoopsArt.crowd().full(), 0.0, 0.0, 1.0)
	# Court and the carpet beyond the cage (darker towards the outside).
	b.quad(-w, 0.0, back, w, 0.0, back, w, 0.0, 60.0, -w, 0.0, 60.0, HoopsArt.floor_tex().full(), 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, HoopsLook.FLOOR_GLOSS)
	var apron := HoopsArt.apron().full()
	b.quad(-hw, 0.0, back, -w, 0.0, back, -w, 0.0, 60.0, -hw, 0.0, 60.0, apron, 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.0, PackedFloat32Array([0.45, 1.0, 1.0, 0.45]))
	b.quad(w, 0.0, back, hw, 0.0, back, hw, 0.0, 60.0, w, 0.0, 60.0, apron, 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.0, PackedFloat32Array([1.0, 0.45, 0.45, 1.0]))
	# LED ad boards along the foot of the wall, under a capping rail.
	var ad := HoopsLook.AD_HW
	b.quad(-ad, HoopsLook.AD_H, HoopsLook.AD_Z, ad, HoopsLook.AD_H, HoopsLook.AD_Z, ad, 0.0, HoopsLook.AD_Z, -ad, 0.0, HoopsLook.AD_Z,
		HoopsArt.ad_board().full(), 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, HoopsLook.AD_EMISSIVE)
	_beveled_box(b, -ad - 1.0, HoopsLook.AD_H, HoopsLook.AD_Z - 1.5, ad + 1.0, HoopsLook.AD_H + 3.5, HoopsLook.AD_Z + 2.5, _steel_faces(), -1, 0.6, 0.0)
	# The cage's back posts and top rail.
	for sx: float in [-w, w]:
		_beveled_box(b, sx - 4.0, 0.0, back + 3.0, sx + 4.0, 424.0, back + 11.0, _steel_faces(), -1, NAN, 0.4)
	_beveled_box(b, -w, 414.0, back + 3.0, w, 424.0, back + 11.0, _steel_faces(), -1, NAN, 0.0)
	# Floodlight housings in the top corners.
	for sx: float in [-HoopsLook.LAMP_X, HoopsLook.LAMP_X]:
		_beveled_box(b, sx - 22.0, HoopsLook.LAMP_Y - 9.0, HoopsLook.LAMP_Z - 6.0, sx + 22.0, HoopsLook.LAMP_Y + 7.0, HoopsLook.LAMP_Z + 4.0, _steel_faces(0.4), -1, 0.7, 0.0)
	# The backboard's rail, high on the wall, and its brackets.
	_beveled_box(b, -HoopsLook.WALL_HALF_W * 0.6, HoopsLook.RAIL_Y - 6.0, HoopsLook.RAIL_Z - 3.0, HoopsLook.WALL_HALF_W * 0.6, HoopsLook.RAIL_Y + 6.0, HoopsLook.RAIL_Z + 3.0, _steel_faces(), -1, 0.7, 0.0)
	for bx: float in [-140.0, -70.0, 70.0, 140.0]:
		b.box(bx - 3.0, HoopsLook.RAIL_Y - 5.0, back + 1.0, bx + 3.0, HoopsLook.RAIL_Y + 5.0, HoopsLook.RAIL_Z - 3.0, BoxFaces.new(steel, steel, steel, steel))
	# Dark plates behind the sign and the two readouts.
	_beveled_box(b, -HoopsLook.SIGN_HW - 6.0, HoopsLook.SIGN_Y - HoopsLook.SIGN_HH - 5.0, back + 0.5, HoopsLook.SIGN_HW + 6.0, HoopsLook.SIGN_Y + HoopsLook.SIGN_HH + 5.0, HoopsLook.SIGN_Z - 1.5, _steel_faces(0.3), 0xFF444458, 0.8, 0.0)
	for sx: float in [-HoopsLook.PANEL_X, HoopsLook.PANEL_X]:
		_beveled_box(b, sx - HoopsLook.PANEL_HW - 3.0, HoopsLook.SIGN_Y - HoopsLook.PANEL_HH - 3.0, back + 0.5, sx + HoopsLook.PANEL_HW + 3.0, HoopsLook.SIGN_Y + HoopsLook.PANEL_HH + 3.0, HoopsLook.PANEL_Z - 0.4, _steel_faces(0.4), -1, 0.8, 0.0)
	return b.build()


## The backboard: face, frame and the carriage that hangs it from the rail (moves with the hoop).
func _build_board() -> Model:
	var b := ModelBuilder.new()
	var hw := HoopsGeo.BOARD_HALF_W * S
	var bz := -HoopsGeo.BOARD_Z * S
	var top := HoopsGeo.BOARD_TOP * S
	var bottom := HoopsGeo.BOARD_BOTTOM * S
	var t := HoopsLook.FRAME_W
	var steel := HoopsArt.steel().full()
	b.quad(-hw, top, bz, hw, top, bz, hw, bottom, bz, -hw, bottom, bz, HoopsArt.board_face().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, HoopsLook.BOARD_GLOSS)
	var f := _steel_faces()
	_beveled_box(b, -hw - t, top, bz - 5.0, hw + t, top + t, bz + 3.0, f, -1, 0.7, 0.0)
	_beveled_box(b, -hw - t, bottom - t, bz - 5.0, hw + t, bottom, bz + 3.0, f, -1, 0.7, 0.0)
	_beveled_box(b, -hw - t, bottom, bz - 5.0, -hw, top, bz + 3.0, f, -1, 0.7, 0.0)
	_beveled_box(b, hw, bottom, bz - 5.0, hw + t, top, bz + 3.0, f, -1, 0.7, 0.0)
	b.box(-hw, bottom, bz - 5.0, hw, top, bz - 0.5, BoxFaces.new(null, steel, steel, steel))
	# The carriage: from the back of the board to the rail.
	_beveled_box(b, -22.0, HoopsLook.RAIL_Y - 12.0, HoopsLook.RAIL_Z + 3.0, 22.0, HoopsLook.RAIL_Y + 12.0, bz - 5.0, f, -1, 0.7, 0.0)
	return b.build()


## Emissive strips on the face of the backboard's frame, tinted and pulsed by the game.
func _build_led() -> Model:
	var b := ModelBuilder.new()
	var hw := HoopsGeo.BOARD_HALF_W * S
	var bz := -HoopsGeo.BOARD_Z * S + 3.25
	var top := HoopsGeo.BOARD_TOP * S
	var bottom := HoopsGeo.BOARD_BOTTOM * S
	var t := HoopsLook.FRAME_W
	var w := TexKit.white().full()
	var o := t / 2.0 - 0.7
	var ext := hw + t - 0.5
	var strip := func(x0: float, y0: float, x1: float, y1: float) -> void:
		b.quad(x0, y1, bz, x1, y1, bz, x1, y0, bz, x0, y0, bz, w, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 1.0)
	strip.call(-ext, top + o, ext, top + o + 1.4)
	strip.call(-ext, bottom - o - 1.4, ext, bottom - o)
	strip.call(-hw - o - 1.4, bottom, -hw - o, top)
	strip.call(hw + o, bottom, hw + o + 1.4, top)
	return b.build()


func _build_rim() -> Model:
	var b := ModelBuilder.new()
	var r := HoopsGeo.RIM_R * S
	var steel := HoopsArt.steel().full()
	b.torus(0.0, 0.0, 0.0, r, 2.0, HoopsArt.rim().full(), 20, 6, -1, 0.8)
	# Mounting plate on the glass and the arm out to the rim.
	var gap := (HoopsGeo.BOARD_Z - HoopsGeo.HOOP_Z) * S
	var faces := BoxFaces.new(steel, steel, steel, steel, null, 0.0, 0.0, 0.0, 0.6)
	_beveled_box(b, -11.0, -9.0, -gap - 0.5, 11.0, 7.0, -gap + 2.5, faces, -1, 0.6, 0.0)
	_beveled_box(b, -3.0, -2.5, -gap + 2.0, 3.0, 1.5, -r + 2.0, faces, -1, 0.5, 0.0)
	return b.build()


# ---------------------------------------------------------------- drawing

var _board_xf := Xform.new()

## Scratch placement for the game to position [method rim_model] with each frame.
var rim_xf := Xform.new()

var _makes_panel := HoopsArt.Readout.new("MAKES", Pal.ORANGE)
var _mult_panel := HoopsArt.Readout.new("MULT", Pal.PINK)


## Repaints the wall readouts when what they show has changed.
func update_readouts(makes: int, shots: int, streak: int) -> void:
	var mk := makes * 1000 + shots
	if _makes_panel.stale(mk):
		_makes_panel.paint(mk, "%d/%d" % [makes, shots], Pal.YELLOW)
	var mult := clampi(streak, 1, HoopsTuning.MAX_MULTIPLIER)
	var tier := 0
	if streak >= HoopsTuning.MAX_MULTIPLIER:
		tier = 2
	elif streak >= 2:
		tier = 1
	var key := mult * 10 + tier
	if _mult_panel.stale(key):
		var color := Pal.ORANGE if tier == 2 else (Pal.PINK if tier == 1 else Pal.GRAY)
		_mult_panel.paint(key, "x%d" % mult, color)


## The arena: wall, ad boards, posts, sign, readouts, floodlights with their shafts of light, dust in
## the beams and camera flashes in the crowd. Draws first: everything else is in front.
func draw_backdrop(r: Renderer3D, t: float, streak: int, mult_pop: float) -> void:
	_room_model().draw(r)
	var accent := _accent(streak, t)
	var fire := streak >= HoopsTuning.MAX_MULTIPLIER
	var calm := ScreenShake.intensity <= 0.0
	var glow := TexKit.glow().full()
	var white := TexKit.white().full()
	var led_level := HoopsLook.LED_BASE + HoopsLook.LED_PULSE * (0.5 + 0.5 * sin(t * 4.0)) + _flash * HoopsLook.LED_FLASH

	# Light strips: the ad boards' cap and the cage's back posts.
	var ad := HoopsLook.AD_HW
	var cap_z := HoopsLook.AD_Z + 2.6
	r.quad(-ad, HoopsLook.AD_H + 2.6, cap_z, ad, HoopsLook.AD_H + 2.6, cap_z, ad, HoopsLook.AD_H + 1.2, cap_z, -ad, HoopsLook.AD_H + 1.2, cap_z,
		white, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, led_level, 1.0, true, accent)
	var w := HoopsGeo.CAGE_HALF_W * S
	var post_z := HoopsGeo.WALL_Z + 11.2
	for sx: float in [-w, w]:
		r.quad(sx - 0.8, 408.0, post_z, sx + 0.8, 408.0, post_z, sx + 0.8, 24.0, post_z, sx - 0.8, 24.0, post_z,
			white, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, led_level, 1.0, true, accent)

	# The wall readouts: solid glowing faces, the multiplier popping with the game's spring.
	_draw_panel(r, _makes_panel.tex().full(), -HoopsLook.PANEL_X, 1.0)
	_draw_panel(r, _mult_panel.tex().full(), HoopsLook.PANEL_X, clampf(mult_pop, 0.7, 1.5))

	# Neon sign: halo added on top of the wall, crisp tube over it.
	var flick := 0.8 + 0.2 * sin(t * 21.0) * sin(t * 5.3) if fire else 1.0
	var k := (0.85 + 0.15 * sin(t * 2.4)) * flick * (1.0 + _flash * 0.35)
	var sz := HoopsLook.SIGN_Z
	var sy := HoopsLook.SIGN_Y
	var shw := HoopsLook.SIGN_HW
	var shh := HoopsLook.SIGN_HH
	r.quad(-shw, sy + shh, sz, shw, sy + shh, sz, shw, sy - shh, sz, -shw, sy - shh, sz, HoopsArt.logo_halo().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, HoopsLook.SIGN_HALO_ALPHA * k, true, accent)
	r.quad(-shw, sy + shh, sz, shw, sy + shh, sz, shw, sy - shh, sz, -shw, sy - shh, sz, HoopsArt.logo_core().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.ALPHA, 1.15 * k, 1.0, true, Pal.mix_argb(accent, Pal.WHITE, 0.6))

	# Floodlights and their shafts.
	var shaft := HoopsArt.shaft().full()
	var dot := TexKit.dot().full()
	var shaft_a := HoopsLook.SHAFT_ALPHA + _hype * HoopsLook.SHAFT_HYPE
	for side in 2:
		var sx := -HoopsLook.LAMP_X if side == 0 else HoopsLook.LAMP_X
		for n in range(-1, 2):
			r.sprite(sx + n * 13.0, HoopsLook.LAMP_Y - 1.0, HoopsLook.LAMP_Z + 4.5, 9.0, 9.0, dot, 0.0, Blend.OPAQUE, 1.5, 1.0, 1.0, 0xFFFFF0D8)
		var breathe := 0.9 + 0.1 * sin(t * 0.7 + side * 2.0)
		r.sprite(sx, HoopsLook.LAMP_Y, HoopsLook.LAMP_Z + 6.0, 80.0, 80.0, glow, 0.0, Blend.ADD, 1.0, 0.3 * breathe, 1.0, 0xFFFFE0B0)
		var tx := sx * 0.4
		r.beam(sx, HoopsLook.LAMP_Y - 4.0, HoopsLook.LAMP_Z + 5.0, tx, 0.0, -196.0, 64.0, shaft, Blend.ADD, 1.0, shaft_a * breathe, 0xFFFFE0B8)
	# Light spilling down the back posts.
	for sx: float in [-w, w]:
		r.beam(sx, 24.0, post_z + 1.0, sx, 408.0, post_z + 1.0, 22.0, glow, Blend.ADD, 1.0, 0.14 * (0.85 + 0.3 * _flash), accent)
	# A sweep along the ad boards, brighter on every make.
	var sweep := -ad - 60.0 + fmod(t * 70.0, ad * 2.0 + 120.0)
	r.quad(sweep, HoopsLook.AD_H, HoopsLook.AD_Z + 0.4, sweep + 60.0, HoopsLook.AD_H, HoopsLook.AD_Z + 0.4, sweep + 40.0, 0.0, HoopsLook.AD_Z + 0.4, sweep - 20.0, 0.0, HoopsLook.AD_Z + 0.4,
		HoopsArt.glint().full(), 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, 0.35, true, 0xFFB8C8FF)
	if _flash > 0.02:
		r.quad(-ad, HoopsLook.AD_H, HoopsLook.AD_Z + 0.5, ad, HoopsLook.AD_H, HoopsLook.AD_Z + 0.5, ad, 0.0, HoopsLook.AD_Z + 0.5, -ad, 0.0, HoopsLook.AD_Z + 0.5,
			white, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, _flash * 0.3, true, accent)

	# Dust in the beams: slow drifting motes.
	for i in MOTES:
		var x := (MathUtil.hash01(i, 91) - 0.5) * 300.0 + sin(t * 0.3 + i) * 8.0
		var y := 30.0 + fmod(MathUtil.hash01(i, 92) * 330.0 + t * (4.0 + MathUtil.hash01(i, 93) * 5.0), 330.0)
		var z := -300.0 + MathUtil.hash01(i, 94) * 150.0
		r.sprite(x, y, z, 2.6, 2.6, dot, 0.0, Blend.ADD, 1.0, 0.3 * (0.6 + 0.4 * sin(t * 1.3 + i * 2.1)), 1.0, 0xFFFFE6C0)

	# Camera flashes in the crowd (none with reduce motion: they are flashing lights).
	if not calm:
		var flare := HoopsArt.flare().full()
		var active := 5 + int(_hype * 7.0)
		for i in mini(active, FLASHES):
			var period := 1.7 + MathUtil.hash01(i, 93) * 2.6
			var u := t + MathUtil.hash01(i, 94) * period
			var cycle := int(u / period)
			var phase := u - cycle * period
			if phase > 0.1:
				continue
			var x := -180.0 + MathUtil.hash01(i, cycle, 95) * 360.0
			var y := 60.0 + MathUtil.hash01(i, cycle, 96) * 190.0
			var tint := 0xFFDDEEFF
			var pick := (i + cycle) % 3
			if pick == 1:
				tint = 0xFFFFE9A8
			elif pick == 2:
				tint = 0xFFFFB8E0
			r.sprite(x, y, HoopsGeo.WALL_Z + 2.0, 16.0, 16.0, flare, 0.0, Blend.ADD, 1.0, 1.0 - phase / 0.1, 1.0, tint)


func _draw_panel(r: Renderer3D, region: Region, cx: float, pop: float) -> void:
	var hw := HoopsLook.PANEL_HW * pop
	var hh := HoopsLook.PANEL_HH * pop
	var y := HoopsLook.SIGN_Y
	var z := HoopsLook.PANEL_Z
	r.quad(cx - hw, y + hh, z, cx + hw, y + hh, z, cx + hw, y - hh, z, cx - hw, y - hh, z, region, 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, HoopsLook.PANEL_EMISSIVE)


## Pools of lamp light on the court: under the hoop and where the shooter stands.
func draw_pools(r: Renderer3D, hoop_x: float, streak: int) -> void:
	var glow := TexKit.glow().full()
	var a := HoopsLook.POOL_ALPHA + _flash * HoopsLook.POOL_FLASH
	r.flat(hoop_x * S, -190.0, 0.7, 250.0, 330.0, glow, 0.0, Blend.ADD, 1.0, a, Pal.mix_argb(0xFFFFD9A0, _state_color(streak), 0.35))
	r.flat(0.0, -25.0, 0.7, 190.0, 150.0, glow, 0.0, Blend.ADD, 1.0, HoopsLook.POOL_ALPHA * 0.8, 0xFFFFD9A0)


## The backboard on its rail, with its LED frame, lit markings, a slow glint across the glass and
## the flash where a ball struck it. [param hoop_x] is the board's offset in metres.
func draw_board(r: Renderer3D, hoop_x: float, t: float, streak: int) -> void:
	if _board_model == null:
		_board_model = _build_board()
	if _led_model == null:
		_led_model = _build_led()
	var hx := hoop_x * S
	_board_xf.set_xf(hx, 0.0, 0.0)
	_board_model.draw(r, -1, 1.0, _board_xf)
	var accent := _accent(streak, t)
	var level := HoopsLook.LED_BASE + HoopsLook.LED_PULSE * (0.5 + 0.5 * sin(t * 4.0)) + (_flash + _board_flash * 0.6) * HoopsLook.LED_FLASH
	_led_model.draw(r, -1, level, _board_xf, accent)

	var hw := HoopsGeo.BOARD_HALF_W * S
	var top := HoopsGeo.BOARD_TOP * S
	var bottom := HoopsGeo.BOARD_BOTTOM * S
	var bz := -HoopsGeo.BOARD_Z * S
	var fire := streak >= HoopsTuning.MAX_MULTIPLIER
	var flick := 0.85 + 0.15 * sin(t * 19.0) if fire else 1.0
	var marks := (HoopsLook.BOARD_GLOW_ALPHA + 0.2 * sin(t * 3.0) + _flash * 0.4 + _board_flash * 0.3) * flick
	var glow := TexKit.glow().full()
	# The lit markings on the glass, and a wash of the state colour behind the board.
	r.quad(hx - hw, top, bz + 0.6, hx + hw, top, bz + 0.6, hx + hw, bottom, bz + 0.6, hx - hw, bottom, bz + 0.6, HoopsArt.board_marks().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, clampf(marks, 0.0, 1.0), true, accent)
	r.sprite(hx, (top + bottom) / 2.0, bz - 4.0, 240.0, 190.0, glow, 0.0, Blend.ADD, 1.0, 0.12 + _flash * 0.16 + (0.08 if fire else 0.0), 1.0, accent)
	# A slow glint across the glass every few seconds.
	var gp := fmod(t, 9.0) / 3.2
	if gp < 1.0:
		var gx := hx + (gp * 2.0 - 1.0) * (hw + 10.0)
		var fade := sin(gp * PI)
		r.quad(gx - 12.0, top, bz + 0.9, gx + 12.0, top, bz + 0.9, gx - 6.0, bottom, bz + 0.9, gx - 30.0, bottom, bz + 0.9, HoopsArt.glint().full(), 0.0, 0.0, 1.0,
			0.0, 0.0, NAN, NAN, Blend.ADD, 1.0, 0.3 * fade, true, 0xFFC0D0FF)
	# A light chasing round the frame.
	var half := hw + HoopsLook.FRAME_W / 2.0
	var top_y := top + HoopsLook.FRAME_W / 2.0
	var bot_y := bottom - HoopsLook.FRAME_W / 2.0
	var per := 4.0 * half + 2.0 * (top_y - bot_y)
	for i in 3:
		var u := fmod(fmod(t * 0.32 - i * 0.02, 1.0) + 1.0, 1.0)
		_chase(r, hx, u * per, half, top_y, bot_y, bz + 3.6, 22.0 - i * 6.0, 0.55 - i * 0.15)
	# The flash where a ball hit the glass.
	if _board_flash > 0.02:
		r.sprite(hx + _board_hit_x, _board_hit_y, bz + 2.0, 60.0, 60.0, glow, 0.0, Blend.ADD, 1.0, _board_flash * 0.8, 1.0, 0xFFB8D8FF)


## A bead of light [param d] centimetres round the frame's perimeter (clockwise from the top left).
func _chase(r: Renderer3D, hx: float, d: float, half: float, top: float, bottom: float, z: float, size: float, alpha: float) -> void:
	var w_top := 2.0 * half
	var hgt := top - bottom
	var p := d
	var x: float
	var y: float
	if p < w_top:
		x = -half + p
		y = top
	elif p < w_top + hgt:
		p -= w_top
		x = half
		y = top - p
	elif p < 2.0 * w_top + hgt:
		p -= w_top + hgt
		x = half - p
		y = bottom
	else:
		p -= 2.0 * w_top + hgt
		x = -half
		y = bottom + p
	r.sprite(hx + x, y, z, size, size, TexKit.glow().full(), 0.0, Blend.ADD, 1.0, alpha, 1.0, 0xFFFFE8C8)


# ---------------------------------------------------------------- net

var _ring_y := PackedFloat32Array([0.0, 0.0, 0.0, 0.0])
var _ring_r := PackedFloat32Array([0.0, 0.0, 0.0, 0.0])
var _ring_x := PackedFloat32Array([0.0, 0.0, 0.0, 0.0])


## The net: two families of strands weaving diamonds down a tapering cone. It hangs from the rim
## at [param rim_y] (scene units, with the rim's wobble), stretches and kicks after a make
## ([param swish] 1 to 0) and bulges round a ball passing through at height [param ball_y]
## (negative for none).
func draw_net(r: Renderer3D, hoop_x: float, rim_y: float, t: float, swish: float, ball_y: float) -> void:
	var tex := TexKit.white().full()
	var rings := HoopsLook.NET_RINGS
	var rim_r := HoopsGeo.RIM_R * S
	var drop := HoopsLook.NET_DROP + swish * 8.0
	var sway := sin(t * 20.0) * swish * 3.0
	var ring_y := _ring_y
	var ring_r := _ring_r
	var ring_x := _ring_x
	for j in rings + 1:
		var f := j / float(rings)
		ring_y[j] = rim_y - 1.0 - drop * f
		var rad := rim_r * lerpf(1.0, HoopsLook.NET_FOOT, f)
		if ball_y >= 0.0:
			var d := (ring_y[j] - ball_y) / HoopsLook.NET_BULGE_H
			rad += HoopsLook.NET_BULGE * exp(-d * d)
		ring_r[j] = rad * (1.0 + swish * 0.05 * sin(t * 26.0 + f * 3.0))
		ring_x[j] = sway * f
	var cx := hoop_x * S
	var cz := -HoopsGeo.HOOP_Z * S
	var n := HoopsLook.NET_STRANDS
	var net_tint := 0xFFE4E0F0
	for i in n:
		var a := i / float(n) * TAU
		for family in 2:
			var dir := -1.0 if family == 0 else 1.0
			for j in rings:
				var f0 := j / float(rings)
				var f1 := (j + 1) / float(rings)
				var a0 := a + dir * HoopsLook.NET_TWIST * f0
				var a1 := a + dir * HoopsLook.NET_TWIST * f1
				r.beam(cx + cos(a0) * ring_r[j] + ring_x[j], ring_y[j], cz + sin(a0) * ring_r[j],
					cx + cos(a1) * ring_r[j + 1] + ring_x[j + 1], ring_y[j + 1], cz + sin(a1) * ring_r[j + 1],
					1.5, tex, Blend.ALPHA, 0.0, 0.85, net_tint)


## The two side nets of the cage and its roof, a fine cool mesh.
func draw_cage(r: Renderer3D) -> void:
	var w := HoopsGeo.CAGE_HALF_W * S
	var back := HoopsGeo.WALL_Z
	var net := HoopsArt.cage_net_region()
	var cell := 8.0
	var tw := float(net.w)
	var len_u := (-100.0 - back) / cell * tw
	var h_u := 420.0 / cell * tw
	for x: float in [-w, w]:
		r.quad(x, 420.0, back, x, 420.0, -100.0, x, 0.0, -100.0, x, 0.0, back, net, 1.0 if x < 0.0 else -1.0, 0.0, 0.0,
			0.0, 0.0, len_u, h_u, Blend.ALPHA, 0.0, 0.7, false)
	r.quad(-w, 420.0, -100.0, w, 420.0, -100.0, w, 420.0, back, -w, 420.0, back, net, 0.0, -1.0, 0.0,
		0.0, 0.0, w * 2.0 / cell * tw, len_u, Blend.ALPHA, 0.0, 0.7, false)


# ---------------------------------------------------------------- effects

## Glowing trails behind balls in flight, the shockwaves and glints of makes and hits, and (on
## fire) a halo round the ball and the hoop. Draws last among the additive layers.
func draw_effects(r: Renderer3D, hoop_x: float, rim_y: float, t: float, streak: int, ball_d: float) -> void:
	var glow := TexKit.glow().full()
	var fire := streak >= HoopsTuning.MAX_MULTIPLIER
	var color := 0xFFFFA030 if fire else 0xFFFF8A38
	var ring := HoopsArt.ring().full()
	var flare := HoopsArt.flare().full()
	var ages := _trail_age
	var pos := _trail_pos
	for i in BALLS:
		for s in TRAIL_N:
			var at := i * TRAIL_N + s
			var age := ages[at]
			if age >= TRAIL_LIFE:
				continue
			var u := age / TRAIL_LIFE
			var size := ball_d * (1.0 - 0.55 * u)
			var a := HoopsLook.TRAIL_ALPHA * (1.0 - u) * (1.35 if fire else 1.0)
			r.sprite(pos[at * 3], pos[at * 3 + 1], pos[at * 3 + 2], size, size, glow, 0.0, Blend.ADD, 1.0, a, 1.0, color)
			if fire and age < TRAIL_DT * 1.5:
				r.sprite(pos[at * 3], pos[at * 3 + 1], pos[at * 3 + 2], ball_d * 2.4, ball_d * 2.4, glow, 0.0, Blend.ADD, 1.0, 0.3, 1.0, Pal.ORANGE)
	if fire:
		# The hoop is on fire: a flickering heat glow.
		var flick := 0.75 + 0.25 * sin(t * 17.0) * sin(t * 6.1)
		r.sprite(hoop_x * S, rim_y + 4.0, -HoopsGeo.HOOP_Z * S + 6.0, 120.0, 100.0, glow, 0.0, Blend.ADD, 1.0, 0.32 * flick, 1.0, Pal.ORANGE)
	for b in _bursts:
		if b.age < 0.0 or b.age >= b.life:
			continue
		var p := b.age / b.life
		var e := 1.0 - (1.0 - p) * (1.0 - p)
		var size := lerpf(b.s0, b.s1, e)
		var a := b.alpha * (1.0 - p) * (1.0 - p * 0.3)
		if b.kind == K_RING_FLAT:
			r.flat(b.x, b.z, b.y, size, size, ring, 0.0, Blend.ADD, 1.0, a, b.tint)
		elif b.kind == K_RING_UP:
			r.sprite(b.x, b.y, b.z, size, size, ring, 0.0, Blend.ADD, 1.0, a, 1.0, b.tint)
		elif b.kind == K_FLARE:
			r.sprite(b.x, b.y, b.z, size, size, flare, 0.0, Blend.ADD, 1.0, a, 1.0, b.tint)
		else:
			r.sprite(b.x, b.y, b.z, size, size, glow, 0.0, Blend.ADD, 1.0, a * 0.7, 1.0, b.tint)
