class_name MachineUnit
extends RefCounted
## hub/Machines.kt MachineUnit: one cabinet on the arcade floor: its model, the parts that move in
## attract mode (a claw patrolling the prizes, moles popping, a puck gliding...), the chase lights on
## its marquee and the coloured light it throws on the carpet.
##
## Godot: the copies of a bank build the same model in a different place, so the hall shares it
## ([method share_bank]): every copy draws one model, placed by [member place] (a MultiMesh of its
## copies on the GPU, one draw per texture for the whole bank), and [member own] holds what is
## this copy's alone (its live screen, a seeded prize pile). A unit built on its own, as the tests and
## the showroom build them, keeps its whole model in [member model], exactly as build-13 built it.

const SH := MiniGame.CabinetShape

## The live screens' glow: just above 1 so the picture reads as lit but stays under the bloom threshold.
const SCREEN_GLOW := 1.15
## Radius of the cap of a control-panel button on an upright.
const BUTTON_R := 0.9
## Depth of a claw machine's control deck, in front of its glass.
const CLAW_DECK_D := 7.2
## Plush prizes in a claw machine's pile (each is a few hundred polygons).
const CLAW_PRIZES := 8

var prop: Prop
var game: MiniGame
var art: MachineArt
## The cabinet's model (all of it; or, once shared, the bank's common part, placed by [member place]).
var model: Model
## This copy's own part when its bank shares a model (world coordinates), else null.
var own: Model = null
## Where the shared [member model] is drawn for this copy (null: where it was built).
var place: Xform = null
var lights: Array[PointLight] = []
var screen: LiveScreen = null
## How lit-up the "you're standing here" highlight is, 0..1 (Highlight eases and shapes it).
var highlight := 0.0
## The emissive multiplier for this frame's draws: 1 unless highlighted.
var _boost := 1.0

var _x0: float
var _x1: float
var _z0: float
var _z1: float
var _h: float
var _cx: float
var _seed: int
## Marquee chase lights: x, y, z per bulb.
var _bulbs := PackedFloat32Array()
var _xf := Xform.new()
var _xf2 := Xform.new()
## The game's own cabinet, if it has one; otherwise the built-in one for its shape.
var _design: CabinetDesign
var _box: CabinetBuild

static var _skee_ball: Model = null
static var _blue_mallet: Model = null
static var _red_mallet: Model = null


func _init(p_prop: Prop, p_game: MiniGame, p_art: MachineArt) -> void:
	prop = p_prop
	game = p_game
	art = p_art
	_x0 = prop.x0
	_x1 = prop.x1
	_z0 = prop.z0
	_z1 = prop.z1
	_h = prop.height
	_cx = (_x0 + _x1) / 2.0
	_seed = prop.variant * 7 + prop.machine * 13
	_design = game.cabinet() as CabinetDesign
	var b := ModelBuilder.new()
	var c := CabinetBuild.new(b, self, _x0, _x1, _z0, _z1, _h, prop.variant, _seed, art)
	_box = c
	if _design != null:
		_design.build(c)
	else:
		var s := prop.shape
		if s == SH.UPRIGHT:
			_upright(c, false)
		elif s == SH.TOWER:
			_upright(c, true)
		elif s == SH.CLAW:
			_claw(c, true)
		elif s == SH.WIDE:
			_claw(c, false)
		elif s == SH.WHACK:
			_whack(c)
		elif s == SH.SKEEBALL:
			_lane(c, true)
		elif s == SH.LANE:
			_lane(c, false)
		elif s == SH.HOOPS:
			_hoops(c)
		elif s == SH.PUSHER:
			_pusher(c)
		elif s == SH.AIR_HOCKEY:
			_table(c, true)
		elif s == SH.TABLE:
			_table(c, false)
		elif s == SH.RACER or s == SH.GUN:
			# Machines that bring their own design; a stand-in if one ever doesn't.
			_upright(c, false)
		else:
			# PINBALL, FISHING
			_table(c, false)
	model = b.build()
	# Every machine glows onto the carpet in front of it.
	c.light(_cx, 20.0, _z1 + 10.0, art.glow, 70.0, 0.85)


func add_bulb(x: float, y: float, z: float) -> void:
	_bulbs.append(x)
	_bulbs.append(y)
	_bulbs.append(z)


func attach_screen(s: LiveScreen) -> void:
	screen = s


# ------------------------------------------------------------------ cabinets

## A classic upright: a kick plate with the coin door and a ticket dispenser, a sloped control deck
## with a joystick and lit buttons on its printing, a screen under glass in a raised bezel, and a
## framed, lit marquee. The tower is taller, with one big button.
func _upright(c: CabinetBuild, tall: bool) -> void:
	var b := c.b
	var st := 1.8
	var ix0 := _x0 + st
	var ix1 := _x1 - st
	var pw := ix1 - ix0
	var panel_y := 28.0 if tall else 26.0
	var cp_back := _z1 - 9.0
	var zf := _z1 - 1.0
	var screen_top := _h - (14.0 if tall else 11.0)
	c.side_panels(_h)
	var dark := art.dark_paint().full()
	var metal := HallArt.dark_metal().full()
	CabinetForm.beveled_box(b, ix0, 0.0, _z0, ix1, panel_y, zf, BoxFaces.new(art.kick().full(), null, null, dark, dark), -1, 0.0)
	b.quad(ix0, panel_y + 4.0, cp_back, ix1, panel_y + 4.0, cp_back, ix1, panel_y, zf, ix0, panel_y, zf,
		(art.panel_big() if tall else art.panel()).full(), 0.0, 0.91, 0.41, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.55)
	# A raised lip along the front of the deck, chrome on top.
	b.box(ix0, panel_y - 0.7, zf - 0.6, ix1, panel_y + 0.5, zf + 0.7, BoxFaces.new(metal, metal, metal, HallArt.chrome().full(), null, 0.0, 0.0, 0.0, 0.8))
	# The screen: housing, a bezel round it, the live picture, and a pane of glass over it.
	b.box(ix0, panel_y + 4.0, _z0, ix1, screen_top, cp_back, BoxFaces.new(art.bezel().full(), null, null, dark, dark, 0.0, 0.0, 0.0, 0.6))
	var live := c.live_screen()
	var sxa := ix0 + 1.6
	var sxb := ix1 - 1.6
	var sya := panel_y + 6.5
	var syb := screen_top - 1.6
	var sz := cp_back + 0.15
	b.quad(sxa, syb, sz, sxb, syb, sz, sxb, sya, sz, sxa, sya, sz, live.texture.full(), 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, SCREEN_GLOW)
	c.screen_bezel(sxa, sxb, sya, syb, sz)
	c.screen_glass(sxa, sxb, sya, syb, sz + 0.09)
	c.marquee_box(ix0, ix1, screen_top, _h - 1.0, _z0, cp_back + 3.0)
	var te := MachineKit.glow_for(art.trim, 0.7)
	b.box(_x0, _h - 1.0, _z0, _x1, _h, cp_back + 4.0, BoxFaces.new(art.trim_tex().full(), dark, dark, art.topper().full(), dark, te, CabinetBuild.TOPPER_GLOW, 0.0, 0.5))
	c.rear_panel(ix0, ix1, 1.0, screen_top)
	# The kick plate's hardware: the coin door, and the ticket dispenser under it.
	c.coin_door(_cx, 8.0, zf, 8.0)
	c.ticket_dispenser(_cx, 2.4, zf + 0.5, 7.0)
	# Controls sit on the sloping deck, on the rings and well printed there.
	var tilt := atan2(4.0, zf - cp_back)
	if tall:
		var bz := cp_back + 0.45 * (zf - cp_back)
		b.add_xf(MachineKit.button(art.glow, 2.3), _xf.set_xf(_cx, panel_y + 4.0 * (zf - bz) / (zf - cp_back), bz, 0.0, tilt))
		var sz2 := cp_back + 0.48 * (zf - cp_back)
		for s: int in [-1, 1]:
			b.add_xf(MachineKit.button(art.trim, 0.8), _xf.set_xf(_cx + s * 0.305 * pw, panel_y + 4.0 * (zf - sz2) / (zf - cp_back), sz2, 0.0, tilt))
	else:
		var jz := cp_back + 0.45 * (zf - cp_back)
		b.add_xf(MachineKit.joystick(art.glow), _xf.set_xf(ix0 + MachineKit.PANEL_STICK_U * pw, panel_y + 4.0 * (zf - jz) / (zf - cp_back), jz, 0.0, tilt))
		var ring_colors := [art.glow, art.trim, HallArt.lift(art.glow, 0.4)]
		for row in 2:
			for k in 3:
				var bx := ix0 + (MachineKit.PANEL_BUTTON_U + k * MachineKit.PANEL_BUTTON_STEP_U + row * MachineKit.PANEL_BUTTON_ROW_SHIFT_U) * pw
				var v := MachineKit.PANEL_ROW_V if row == 0 else MachineKit.PANEL_ROW2_V
				var bz := cp_back + v * (zf - cp_back)
				b.add_xf(MachineKit.button(ring_colors[k], BUTTON_R), _xf.set_xf(bx, panel_y + 4.0 * (zf - bz) / (zf - cp_back), bz, 0.0, tilt))
	c.light(_cx, panel_y + 16.0, _z1 + 4.0, art.glow, 50.0, 0.7)


## T-moulding down the two front corners of a body box ([param xa]..[param xb] wide, [param top]
## high, its front face at depth [param z]).
func _front_corners(c: CabinetBuild, xa: float, xb: float, top: float, z: float, thick: float = 1.6) -> void:
	c.t_moulding(xa - CabinetBuild.T_MOLD_SIDE, xa + thick, 0.0, top, z)
	c.t_moulding(xb - thick, xb + CabinetBuild.T_MOLD_SIDE, 0.0, top, z)


## A glass merchandiser: prize pile (or a screen) under a gantry and claw with LED strips, and a
## control deck sloping up in front of the glass with a joystick and a lit drop button. The base has
## the coin door, a ticket dispenser and a prize door with a smoked flap; the marquee on top is framed
## and lit.
func _claw(c: CabinetBuild, with_prizes: bool) -> void:
	var b := c.b
	var x0 := _x0
	var x1 := _x1
	var z0 := _z0
	var z1 := _z1
	var base_top := 28.0
	var glass_top := _h - 8.0
	var glass_front := z1 - CLAW_DECK_D
	var side := art.side_art_square().full()
	var dark := art.dark_paint().full()
	var metal := HallArt.dark_metal().full()
	var chrome := HallArt.chrome().full()
	var trim := art.trim_tex().full()
	CabinetForm.beveled_box(b, x0, 0.0, z0, x1, base_top, z1, BoxFaces.new(art.kick().full(), side, side, dark, dark, 0.0, 0.0, 0.0, 0.3))
	_front_corners(c, x0, x1, base_top, z1)
	var band_e := MachineKit.glow_for(art.trim, 0.7)
	b.box(x0 - 0.3, base_top - 1.0, z0 - 0.3, x1 + 0.3, base_top, z1 + 0.3, BoxFaces.new(trim, trim, trim, null, null, band_e))
	c.posts(x0, x1, z0, glass_front, base_top, glass_top)
	# A solid back behind the prizes, so the case isn't see-through from behind the bank.
	c.rear_panel(x0, x1, 1.0, glass_top)
	# The front of the base: a prize door on the left, the coin door, and a ticket dispenser.
	var fx := x0 + 7.5
	b.box(fx - 5.4, 3.4, z1, fx + 5.4, 13.4, z1 + 0.3, BoxFaces.new(chrome, chrome, chrome, chrome, null, 0.0, 0.0, 0.0, 0.9))
	b.quad(fx - 4.8, 12.8, z1 + 0.32, fx + 4.8, 12.8, z1 + 0.32, fx + 4.8, 4.0, z1 + 0.32, fx - 4.8, 4.0, z1 + 0.32, MachineKit.prize_chute().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.5)
	c.coin_door(_cx + 2.5, 7.0, z1, 8.0)
	c.ticket_dispenser(x1 - 5.2, 5.0, z1 + 0.5, 5.0)
	# The control deck: a wedge sloping up to the glass, a raised lip along its front.
	var dy0 := base_top + 0.5
	var dy1 := base_top + 4.5
	var d_len := sqrt(CLAW_DECK_D * CLAW_DECK_D + 16.0)
	b.quad(x0 + 1.0, dy1, glass_front, x1 - 1.0, dy1, glass_front, x1 - 1.0, dy0, z1, x0 + 1.0, dy0, z1, metal, 0.0, CLAW_DECK_D / d_len, 4.0 / d_len,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.5)
	var uv := PackedFloat32Array([0.0, 0.0, 0.0])
	b.poly(PackedFloat32Array([x0 + 1.0, x0 + 1.0, x0 + 1.0]), PackedFloat32Array([base_top, dy1, dy0]), PackedFloat32Array([glass_front, glass_front, z1]), uv, uv, dark, -1.0, 0.0, 0.0)
	b.poly(PackedFloat32Array([x1 - 1.0, x1 - 1.0, x1 - 1.0]), PackedFloat32Array([dy0, dy1, base_top]), PackedFloat32Array([z1, glass_front, glass_front]), uv, uv, dark, 1.0, 0.0, 0.0)
	b.box(x0 + 1.0, base_top, z1 - 0.7, x1 - 1.0, dy0 + 0.3, z1, BoxFaces.new(metal, null, null, chrome, null, 0.0, 0.0, 0.0, 0.8))
	var tilt := atan2(4.0, CLAW_DECK_D)
	b.add_xf(MachineKit.joystick(art.trim), _xf.set_xf(_cx - 4.5, dy0 + 4.0 * 3.8 / CLAW_DECK_D, z1 - 3.8, 0.0, tilt))
	b.add_xf(MachineKit.button(art.glow, 2.1), _xf.set_xf(_cx + 5.0, dy0 + 4.0 * 3.6 / CLAW_DECK_D, z1 - 3.6, 0.0, tilt))
	# Inside: velvet floor and a printed back wall, lit by strips down the back corners and along the roof.
	b.quad(x0 + 1.0, base_top + 0.3, z0 + 1.0, x1 - 1.0, base_top + 0.3, z0 + 1.0, x1 - 1.0, base_top + 0.3, glass_front - 1.0, x0 + 1.0, base_top + 0.3, glass_front - 1.0,
		MachineKit.velvet().full(), 0.0, 1.0, 0.0)
	b.quad(x0 + 1.0, glass_top, z0 + 1.2, x1 - 1.0, glass_top, z0 + 1.2, x1 - 1.0, base_top, z0 + 1.2, x0 + 1.0, base_top, z0 + 1.2, art.side_art_square().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.55)
	c.led_strip(x0 + 1.4, base_top, x0 + 2.2, glass_top, z0 + 1.3)
	c.led_strip(x1 - 2.2, base_top, x1 - 1.4, glass_top, z0 + 1.3)
	c.led_strip_down(x0 + 1.4, x1 - 1.4, glass_top - 0.05, glass_front - 2.4, glass_front - 1.2)
	if with_prizes:
		var plushies := Catalog.plushies()
		var zc := (z0 + glass_front) / 2.0
		var zr := (glass_front - z0) / 2.0 - 5.5
		# The seeded pile differs from copy to copy: it is each copy's own.
		c.begin_own()
		for k in CLAW_PRIZES:
			var p: Catalog.Plush = plushies[(_seed + k * 3) % plushies.size()]
			var px := _cx + MachineKit.jitter(_seed + k, 1, (x1 - x0) / 2.0 - 5.0)
			var pz := zc + MachineKit.jitter(_seed + k, 2, zr)
			var py := base_top + 0.3 + (k / 4) * 2.2
			var m := HallPlush.model(p)
			if m != null:
				b.add_xf(m, _xf.set_xf(px, py, pz, MachineKit.jitter(_seed + k, 3, 0.9), 0.0, MachineKit.jitter(_seed + k, 4, 0.2), 0.42))
		c.end_own()
		# Prize chute in the front-left corner: a chrome-rimmed hole in the floor.
		b.box(x0 + 1.4, base_top, glass_front - 9.0, x0 + 9.0, base_top + 0.8, glass_front - 1.6,
			BoxFaces.new(chrome, chrome, chrome, HallArt.solid(0xFF08060A).full(), chrome, 0.0, 0.0, 0.0, 0.8))
	else:
		var live := c.live_screen()
		var sxa := x0 + 3.0
		var sxb := x1 - 3.0
		var sya := base_top + 6.0
		var syb := glass_top - 3.0
		var sz := z0 + 1.6
		b.quad(sxa, syb, sz, sxb, syb, sz, sxb, sya, sz, sxa, sya, sz, live.texture.full(), 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, SCREEN_GLOW)
		c.screen_bezel(sxa, sxb, sya, syb, sz, 1.0, 0.5)
	# Gantry rails: a frame of chrome under the roof.
	var rail := BoxFaces.new(chrome, chrome, chrome, chrome, chrome, 0.0, 0.0, 0.0, 0.9)
	b.box(x0 + 1.0, glass_top - 2.5, z0 + 5.0, x1 - 1.0, glass_top - 1.5, z0 + 6.0, rail)
	b.box(x0 + 1.0, glass_top - 2.5, glass_front - 6.0, x1 - 1.0, glass_top - 1.5, glass_front - 5.0, rail)
	b.box(x0 + 1.2, glass_top - 2.5, z0 + 5.0, x0 + 2.4, glass_top - 1.5, glass_front - 5.0, rail)
	b.box(x1 - 2.4, glass_top - 2.5, z0 + 5.0, x1 - 1.2, glass_top - 1.5, glass_front - 5.0, rail)
	c.glass_box(x0 + 0.4, base_top, z0 + 0.4, x1 - 0.4, glass_top, glass_front - 0.4)
	c.marquee_box(x0, x1, glass_top, _h, z0, z1)
	c.light(_cx, glass_top - 6.0, (z0 + glass_front) / 2.0, HallArt.lift(art.glow, 0.3), 44.0, 1.1)


## Whack-a-mole: a padded table with five lit holes, a painted backboard with a framed score display
## and a lit marquee, the coin door and a ticket dispenser on the front, two mallets resting on the
## corners.
func _whack(c: CabinetBuild) -> void:
	var b := c.b
	var x0 := _x0
	var x1 := _x1
	var z0 := _z0
	var z1 := _z1
	var table_y := 26.0
	var board_z := z0 + 4.0
	var side := art.side_art_square().full()
	var dark := art.dark_paint().full()
	var pad := art.trim_tex().full()
	CabinetForm.beveled_box(b, x0, 0.0, board_z, x1, table_y, z1, BoxFaces.new(art.kick().full(), side, side, null, dark, 0.0, 0.0, 0.0, 0.3))
	_front_corners(c, x0, x1, table_y, z1)
	b.quad(x0, table_y, board_z, x1, table_y, board_z, x1, table_y, z1, x0, table_y, z1, MachineKit.whack_top(art.body).full(), 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.35)
	c.coin_door(_cx - 5.0, 6.0, z1, 8.0)
	c.ticket_dispenser(_cx + 6.5, 7.0, z1 + 0.5, 6.5)
	# Each hole has a lit ring round its collar.
	var w := x1 - x0
	var d := z1 - board_z
	var ring_e := MachineKit.glow_for(art.glow, 0.9)
	for hole: Array in MachineKit.WHACK_HOLES:
		b.annulus(x0 + float(hole[0]) * w, board_z + float(hole[1]) * d, table_y + 0.06, 3.55, 4.2, 12, art.glow_tex().full(), -1, 0.0, ring_e)
	# Padded rim round three sides of the table, with a strip of light along its front.
	var pad_faces := BoxFaces.new(pad, pad, pad, pad, null, 0.0, 0.0, 0.0, 0.4)
	CabinetForm.beveled_box(b, x0 - 0.5, table_y, z1 - 1.5, x1 + 0.5, table_y + 1.2, z1 + 0.5, pad_faces, -1, 0.45, 0.0)
	CabinetForm.beveled_box(b, x0 - 0.5, table_y, board_z, x0 + 1.0, table_y + 1.2, z1 - 1.4, pad_faces, -1, 0.45, 0.0)
	CabinetForm.beveled_box(b, x1 - 1.0, table_y, board_z, x1 + 0.5, table_y + 1.2, z1 - 1.4, pad_faces, -1, 0.45, 0.0)
	c.led_strip(x0 + 1.0, table_y + 0.2, x1 - 1.0, table_y + 0.9, z1 + 0.52)
	# Backboard with the marquee on top and the score display in a bezel.
	CabinetForm.beveled_box(b, x0, 0.0, z0, x1, _h - 10.0, board_z, BoxFaces.new(art.side_art().full(), dark, dark, dark, dark))
	c.t_moulding(x0 - CabinetBuild.T_MOLD_SIDE, x0 + 1.6, table_y, _h - 10.0, board_z)
	c.t_moulding(x1 - 1.6, x1 + CabinetBuild.T_MOLD_SIDE, table_y, _h - 10.0, board_z)
	c.rear_panel(x0, x1, 1.0, _h - 10.0)
	c.display(_cx - 9.0, _cx + 9.0, table_y + 16.0, table_y + 21.0, board_z + 0.1)
	c.screen_bezel(_cx - 9.0, _cx + 9.0, table_y + 16.0, table_y + 21.0, board_z + 0.1, 0.9, 0.5)
	c.marquee_box(x0 - 1.0, x1 + 1.0, _h - 10.0, _h, z0, board_z + 1.0)
	# Mallets resting on the front corners.
	b.add_xf(MachineKit.mallet(), _xf.set_xf(x0 + 5.0, table_y + 2.0, z1 - 4.0, 2.4))
	b.add_xf(MachineKit.mallet(), _xf.set_xf(x1 - 5.0, table_y + 2.0, z1 - 4.0, -2.4))
	c.light(_cx, table_y + 20.0, (board_z + z1) / 2.0, HallArt.lift(art.glow, 0.3), 50.0, 0.8)


## Skee-ball alley (or a generic lane with a screen at the end): rails with printed sides and chrome
## caps, a rising wood lane with gutters, a ball tray, the tilted target board (or a screen), a lit
## score display over the coin door, netting and a framed marquee.
func _lane(c: CabinetBuild, skee: bool) -> void:
	var b := c.b
	var x0 := _x0
	var x1 := _x1
	var z0 := _z0
	var z1 := _z1
	var side := art.side_art_long().full()
	var inner := art.body_paint().full()
	var dark := art.dark_paint().full()
	var metal := HallArt.dark_metal().full()
	var chrome := HallArt.chrome().full()
	var black := HallArt.solid(0xFF14121A).full()
	var rail_h := 22.0
	var board_z := z0 + 8.0
	# The rails: printed side, chrome cap, lit T-moulding at the front.
	for s: int in [-1, 1]:
		var xa := x0 if s < 0 else x1 - 2.0
		var faces := BoxFaces.new(dark, side, inner, dark, null, 0.0, 0.0, 0.0, 0.4) if s < 0 else BoxFaces.new(dark, inner, side, dark, null, 0.0, 0.0, 0.0, 0.4)
		CabinetForm.beveled_box(b, xa, 0.0, board_z, xa + 2.0, rail_h, z1, faces)
		b.box(xa - 0.15, rail_h, board_z, xa + 2.15, rail_h + 0.6, z1, BoxFaces.new(chrome, chrome, chrome, chrome, null, 0.0, 0.0, 0.0, 0.9))
		c.t_moulding(xa - 0.15, xa + 2.15, 0.0, rail_h, z1)
	# Front console with the coin door, a score display, a ticket dispenser and the ball tray.
	CabinetForm.beveled_box(b, x0 + 2.0, 0.0, z1 - 10.0, x1 - 2.0, 18.0, z1, BoxFaces.new(art.kick().full(), null, null, black, null, 0.0, 0.0, 0.0, 0.3))
	c.coin_door(_cx - 4.0, 3.0, z1, 7.0)
	c.ticket_dispenser(_cx + 6.0, 4.0, z1 + 0.5, 6.5)
	c.display(_cx - 6.0, _cx + 6.0, 13.4, 16.8, z1 + 0.05)
	c.screen_bezel(_cx - 6.0, _cx + 6.0, 13.4, 16.8, z1 + 0.05, 0.6, 0.4)
	var tray := BoxFaces.new(chrome, chrome, chrome, chrome, null, 0.0, 0.0, 0.0, 0.85)
	b.box(x0 + 3.0, 18.0, z1 - 1.5, x1 - 3.0, 19.4, z1 - 0.8, tray)
	b.box(x0 + 3.0, 18.0, z1 - 8.0, x0 + 3.8, 19.4, z1 - 1.5, tray)
	b.box(x1 - 3.8, 18.0, z1 - 8.0, x1 - 3.0, 19.4, z1 - 1.5, tray)
	# The lane rises towards the jump; a gutter with a chrome divider runs down each side.
	var lane_front := z1 - 10.0
	var lane_back := board_z + 30.0
	var wood := MachineKit.lane_wood().region(0, 0, -1, -1, true)
	b.quad(x0 + 2.0, 26.0, lane_back, x1 - 2.0, 26.0, lane_back, x1 - 2.0, 18.0, lane_front, x0 + 2.0, 18.0, lane_front, wood, 0.0, 0.99, 0.12,
		0.0, 0.0, 128.0, 512.0, Blend.OPAQUE, 0.0, true, -1, 0.5)
	var gw := 2.2
	var y_back := 18.0 + 8.0 * (lane_front - lane_back) / (lane_front - lane_back) + 0.08
	var y_front := 18.0 + 0.08
	for s: int in [-1, 1]:
		var xa := x0 + 2.0 if s < 0 else x1 - 2.0 - gw
		b.quad(xa, y_back, lane_back, xa + gw, y_back, lane_back, xa + gw, y_front, lane_front, xa, y_front, lane_front, metal, 0.0, 0.99, 0.12,
			0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.6)
		var xl := xa + gw if s < 0 else xa - 0.4
		b.quad(xl, y_back + 0.03, lane_back, xl + 0.4, y_back + 0.03, lane_back, xl + 0.4, y_front + 0.03, lane_front, xl, y_front + 0.03, lane_front, chrome, 0.0, 0.99, 0.12,
			0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.9)
	# The jump hump.
	b.quad(x0 + 2.0, 30.0, lane_back - 4.0, x1 - 2.0, 30.0, lane_back - 4.0, x1 - 2.0, 26.0, lane_back, x0 + 2.0, 26.0, lane_back, wood, 0.0, 0.7, 0.7,
		0.0, 0.0, 128.0, 40.0, Blend.OPAQUE, 0.0, true, -1, 0.5)
	if skee:
		# Tilted target board with the scoring rings.
		b.quad(x0 + 2.0, 50.0, board_z, x1 - 2.0, 50.0, board_z, x1 - 2.0, 27.0, lane_back - 6.0, x0 + 2.0, 27.0, lane_back - 6.0, MachineKit.skee_rings().full(), 0.0, 0.66, 0.75,
			0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.4)
	else:
		var live := c.live_screen()
		b.quad(x0 + 3.0, 50.0, board_z + 0.2, x1 - 3.0, 50.0, board_z + 0.2, x1 - 3.0, 30.0, board_z + 0.2, x0 + 3.0, 30.0, board_z + 0.2, live.texture.full(), 0.0, 0.0, 1.0,
			0.0, 0.0, NAN, NAN, Blend.OPAQUE, SCREEN_GLOW)
		c.screen_bezel(x0 + 3.0, x1 - 3.0, 30.0, 50.0, board_z + 0.2, 1.0, 0.5)
		b.quad(x0 + 2.0, 28.0, board_z, x1 - 2.0, 28.0, board_z, x1 - 2.0, 28.0, lane_back - 4.0, x0 + 2.0, 28.0, lane_back - 4.0, dark, 0.0, 1.0, 0.0)
	# Backboard and marquee.
	CabinetForm.beveled_box(b, x0, 0.0, z0, x1, _h - 12.0, board_z, BoxFaces.new(dark, dark, dark, dark, dark))
	c.rear_panel(x0, x1, 1.0, _h - 12.0)
	c.marquee_box(x0 - 1.0, x1 + 1.0, _h - 12.0, _h, z0, board_z + 1.0)
	# Netting over the target end.
	var net := MachineKit.net().region(0, 0, -1, -1, true)
	var net_top := _h - 14.0
	var net_end := board_z + 44.0
	b.quad(x0, net_top, board_z, x1, net_top, board_z, x1, net_top, net_end, x0, net_top, net_end, net, 0.0, -1.0, 0.0,
		0.0, 0.0, 64.0, 160.0, Blend.ALPHA, 0.0, false)
	b.quad(x0 + 0.3, net_top, board_z, x0 + 0.3, net_top, net_end, x0 + 0.3, rail_h, net_end, x0 + 0.3, rail_h, board_z, net, 1.0, 0.0, 0.0,
		0.0, 0.0, 160.0, 90.0, Blend.ALPHA, 0.0, false)
	b.quad(x1 - 0.3, net_top, net_end, x1 - 0.3, net_top, board_z, x1 - 0.3, rail_h, board_z, x1 - 0.3, rail_h, net_end, net, -1.0, 0.0, 0.0,
		0.0, 0.0, 160.0, 90.0, Blend.ALPHA, 0.0, false)
	# Balls waiting in the tray.
	for k in 3:
		b.add_xf(MachineKit.ball(0xFFB0213A), _xf.set_xf(_cx - 5.0 + k * 5.0, 20.0, z1 - 5.0, 0.0, 0.0, 0.0, 2.1))
	c.light(_cx, 44.0, board_z + 20.0, 0xFFFFE8C8, 60.0, 0.9)


## Basketball alley: printed side walls with chrome caps, a court ramp, a net cage with a chrome
## frame, and at the end a backboard with an LED border, a rim and net, and a framed score display;
## the coin door, a credits display and a ticket dispenser on the console.
func _hoops(c: CabinetBuild) -> void:
	var b := c.b
	var x0 := _x0
	var x1 := _x1
	var z0 := _z0
	var z1 := _z1
	var h := _h
	var cx := _cx
	var side := art.side_art_long().full()
	var inner := art.body_paint().full()
	var dark := art.dark_paint().full()
	var chrome := HallArt.chrome().full()
	var black := HallArt.solid(0xFF14121A).full()
	var wall_h := 30.0
	for s: int in [-1, 1]:
		var xa := x0 if s < 0 else x1 - 2.0
		var faces := BoxFaces.new(dark, side, inner, dark, null, 0.0, 0.0, 0.0, 0.4) if s < 0 else BoxFaces.new(dark, inner, side, dark, null, 0.0, 0.0, 0.0, 0.4)
		CabinetForm.beveled_box(b, xa, 0.0, z0 + 4.0, xa + 2.0, wall_h, z1, faces)
		b.box(xa - 0.15, wall_h, z0 + 4.0, xa + 2.15, wall_h + 0.6, z1, BoxFaces.new(chrome, chrome, chrome, chrome, null, 0.0, 0.0, 0.0, 0.9))
		c.t_moulding(xa - 0.15, xa + 2.15, 0.0, wall_h, z1)
	CabinetForm.beveled_box(b, x0 + 2.0, 0.0, z1 - 12.0, x1 - 2.0, 20.0, z1, BoxFaces.new(art.kick().full(), null, null, black, null, 0.0, 0.0, 0.0, 0.3))
	c.coin_door(cx - 4.0, 4.0, z1, 7.0)
	c.ticket_dispenser(cx + 6.0, 5.0, z1 + 0.5, 6.5)
	c.display(cx - 6.0, cx + 6.0, 14.8, 18.2, z1 + 0.05)
	c.screen_bezel(cx - 6.0, cx + 6.0, 14.8, 18.2, z1 + 0.05, 0.6, 0.4)
	# A chrome-lipped rack for the balls on the console.
	var tray := BoxFaces.new(chrome, chrome, chrome, chrome, null, 0.0, 0.0, 0.0, 0.85)
	b.box(x0 + 3.0, 20.0, z1 - 1.5, x1 - 3.0, 21.4, z1 - 0.8, tray)
	b.box(x0 + 3.0, 20.0, z1 - 10.0, x0 + 3.8, 21.4, z1 - 1.5, tray)
	b.box(x1 - 3.8, 20.0, z1 - 10.0, x1 - 3.0, 21.4, z1 - 1.5, tray)
	b.quad(x0 + 2.0, 28.0, z0 + 4.0, x1 - 2.0, 28.0, z0 + 4.0, x1 - 2.0, 20.0, z1 - 12.0, x0 + 2.0, 20.0, z1 - 12.0, MachineKit.court().full(), 0.0, 0.99, 0.1,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.45)
	# Backboard frame, board, an LED border round it, rim and net.
	CabinetForm.beveled_box(b, x0, 0.0, z0, x1, h - 10.0, z0 + 4.0, BoxFaces.new(dark, dark, dark, dark, dark))
	c.rear_panel(x0, x1, 1.0, h - 10.0)
	b.quad(cx - 13.0, h - 13.0, z0 + 4.2, cx + 13.0, h - 13.0, z0 + 4.2, cx + 13.0, h - 31.0, z0 + 4.2, cx - 13.0, h - 31.0, z0 + 4.2, MachineKit.hoop_board().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.4, true, -1, 0.9)
	var lz := z0 + 4.25
	c.led_strip(cx - 13.7, h - 13.0, cx + 13.7, h - 12.3, lz)
	c.led_strip(cx - 13.7, h - 31.7, cx + 13.7, h - 31.0, lz)
	c.led_strip(cx - 13.7, h - 31.0, cx - 13.0, h - 13.0, lz)
	c.led_strip(cx + 13.0, h - 31.0, cx + 13.7, h - 13.0, lz)
	var rim_y := h - 29.0
	var rim_z := z0 + 10.0
	var orange := HallArt.paint(0xFFFF7A1A, 0.3, 0.8).full()
	b.torus(cx, rim_y, rim_z, 4.6, 0.45, orange, 16, 6, -1, 0.7)
	b.box(cx - 0.6, rim_y - 0.5, z0 + 4.0, cx + 0.6, rim_y + 0.3, rim_z - 4.4, BoxFaces.new(orange, orange, orange, orange))
	var net_region := MachineKit.net().region(0, 0, -1, -1, true)
	b.cylinder(cx, rim_z, rim_y - 7.0, rim_y, 3.0, 12, net_region, null, 0.0, -1, null, false, 4.5)
	b.cylinder(cx, rim_z, rim_y - 7.0, rim_y, 3.0, 12, net_region, null, 0.0, -1, null, true, 4.5)
	c.display(cx - 8.0, cx + 8.0, h - 38.0, h - 33.0, z0 + 4.1)
	c.screen_bezel(cx - 8.0, cx + 8.0, h - 38.0, h - 33.0, z0 + 4.1, 0.8, 0.45)
	c.marquee_box(x0 - 1.0, x1 + 1.0, h - 10.0, h, z0, z0 + 6.0)
	# Net cage over the alley, on a chrome frame.
	var net := MachineKit.net().region(0, 0, -1, -1, true)
	var cage_end := z1 - 14.0
	var top := h - 12.0
	b.quad(x0, top, z0 + 4.0, x1, top, z0 + 4.0, x1, top, cage_end, x0, top, cage_end, net, 0.0, -1.0, 0.0,
		0.0, 0.0, 80.0, 160.0, Blend.ALPHA, 0.0, false)
	b.quad(x0 + 0.3, top, z0 + 4.0, x0 + 0.3, top, cage_end, x0 + 0.3, wall_h, cage_end, x0 + 0.3, wall_h, z0 + 4.0, net, 1.0, 0.0, 0.0,
		0.0, 0.0, 160.0, 90.0, Blend.ALPHA, 0.0, false)
	b.quad(x1 - 0.3, top, cage_end, x1 - 0.3, top, z0 + 4.0, x1 - 0.3, wall_h, z0 + 4.0, x1 - 0.3, wall_h, cage_end, net, -1.0, 0.0, 0.0,
		0.0, 0.0, 160.0, 90.0, Blend.ALPHA, 0.0, false)
	var frame := BoxFaces.new(chrome, chrome, chrome, chrome, chrome, 0.0, 0.0, 0.0, 0.9)
	b.box(x0, top - 0.5, z0 + 4.0, x0 + 1.0, top + 0.5, cage_end, frame)
	b.box(x1 - 1.0, top - 0.5, z0 + 4.0, x1, top + 0.5, cage_end, frame)
	b.box(x0, top - 0.5, cage_end - 1.0, x1, top + 0.5, cage_end, frame)
	b.box(x0, wall_h, cage_end - 1.0, x0 + 1.0, top, cage_end, frame)
	b.box(x1 - 1.0, wall_h, cage_end - 1.0, x1, top, cage_end, frame)
	for k in 3:
		b.add_xf(MachineKit.basketball(), _xf.set_xf(cx - 7.0 + k * 7.0, 23.2, z1 - 6.0, k * 1.3, 0.0, 0.0, 3.2))
	c.light(cx, h - 16.0, z0 + 20.0, 0xFFFFE8C8, 70.0, 0.9)


## Coin pusher: a glass case over a coin-covered deck with side walls lit along their tops and a
## sliding shelf, LED strips in the roof and back corners, and a base with the coin door, a framed
## credits display and a ticket dispenser.
func _pusher(c: CabinetBuild) -> void:
	var b := c.b
	var x0 := _x0
	var x1 := _x1
	var z0 := _z0
	var z1 := _z1
	var base_top := 30.0
	var glass_top := _h - 8.0
	var side := art.side_art_square().full()
	var dark := art.dark_paint().full()
	var trim := art.trim_tex().full()
	CabinetForm.beveled_box(b, x0, 0.0, z0, x1, base_top, z1, BoxFaces.new(art.kick().full(), side, side, dark, dark, 0.0, 0.0, 0.0, 0.3))
	_front_corners(c, x0, x1, base_top, z1)
	c.posts(x0, x1, z0, z1, base_top, glass_top)
	c.rear_panel(x0, x1, 1.0, glass_top)
	c.coin_door(_cx - 6.0, 8.0, z1, 9.0)
	c.ticket_dispenser(_cx + 8.5, 8.5, z1 + 0.5, 7.0)
	c.display(_cx - 9.0, _cx + 9.0, 23.0, 27.5, z1 + 0.05)
	c.screen_bezel(_cx - 9.0, _cx + 9.0, 23.0, 27.5, z1 + 0.05, 0.8, 0.45)
	b.quad(x0 + 1.0, glass_top, z0 + 1.2, x1 - 1.0, glass_top, z0 + 1.2, x1 - 1.0, base_top, z0 + 1.2, x0 + 1.0, base_top, z0 + 1.2, art.side_art_square().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.5)
	c.led_strip(x0 + 1.4, base_top, x0 + 2.2, glass_top, z0 + 1.3)
	c.led_strip(x1 - 2.2, base_top, x1 - 1.4, glass_top, z0 + 1.3)
	c.led_strip_down(x0 + 1.4, x1 - 1.4, glass_top - 0.05, z1 - 2.4, z1 - 1.2)
	var deck_y := base_top + 3.0
	b.box(x0 + 1.0, base_top, z0 + 1.0, x1 - 1.0, deck_y, z1 - 1.0, BoxFaces.new(MachineKit.gold().full(), null, null, MachineKit.deck().full(), null, 0.0, 0.0, 0.0, 0.6))
	# Side walls round the playfield, their tops lit.
	var wall_e := MachineKit.glow_for(art.trim, 0.8)
	var wall := BoxFaces.new(dark, dark, dark, trim, null, 0.0, wall_e, 0.0, 0.5)
	b.box(x0 + 1.0, deck_y, z0 + 1.0, x0 + 2.2, deck_y + 4.6, z1 - 1.0, wall)
	b.box(x1 - 2.2, deck_y, z0 + 1.0, x1 - 1.0, deck_y + 4.6, z1 - 1.0, wall)
	# The seeded coins differ from copy to copy: they are each copy's own.
	c.begin_own()
	for k in 26:
		var px := _cx + MachineKit.jitter(_seed + k, 5, (x1 - x0) / 2.0 - 4.0)
		var pz := z0 + 12.0 + MathUtil.hash01(_seed + k, 6) * (z1 - z0 - 16.0)
		var layer := 1 if k % 4 == 0 else 0
		b.add_xf(MachineKit.coin(), _xf.set_xf(px, deck_y + 0.4 + layer * 0.8, pz, MathUtil.hash01(k, 7) * 6.0))
	c.end_own()
	c.glass_box(x0 + 0.4, base_top, z0 + 0.4, x1 - 0.4, glass_top, z1 - 0.4)
	c.marquee_box(x0, x1, glass_top, _h, z0, z1)
	c.light(_cx, glass_top - 5.0, (z0 + z1) / 2.0, 0xFFFFD27A, 44.0, 1.1)


## Air hockey (or a generic table with a screen): a table on legs with levelling feet, a glowing
## air-hole surface, padded rails lit along their inner edge with glowing goal mouths, the coin door
## and a ticket dispenser on the front, and an overhead scoreboard on a post.
func _table(c: CabinetBuild, hockey: bool) -> void:
	var b := c.b
	var x0 := _x0
	var x1 := _x1
	var z0 := _z0
	var z1 := _z1
	var h := _h
	var cx := _cx
	var top := 22.0
	var side := art.side_art_long().full()
	var dark := art.dark_paint().full()
	var metal := HallArt.dark_metal().full()
	var leg := BoxFaces.new(metal, metal, metal, null, metal)
	for lp: Vector2 in [Vector2(x0 + 1.0, z0 + 7.0), Vector2(x1 - 3.0, z0 + 7.0), Vector2(x0 + 1.0, z1 - 3.0), Vector2(x1 - 3.0, z1 - 3.0)]:
		var lx := lp.x
		var lz := lp.y
		b.box(lx, 0.5, lz, lx + 2.0, 9.0, lz + 2.0, leg)
		b.box(lx - 0.3, 0.0, lz - 0.3, lx + 2.3, 0.5, lz + 2.3, BoxFaces.all(dark))
	CabinetForm.beveled_box(b, x0 + 0.5, 9.0, z0 + 6.0, x1 - 0.5, top, z1, BoxFaces.new(art.kick().full(), side, side, null, dark, 0.0, 0.0, 0.0, 0.3))
	c.t_moulding(x0 + 0.35, x0 + 2.1, 9.0, top, z1)
	c.t_moulding(x1 - 2.1, x1 - 0.35, 9.0, top, z1)
	c.rear_panel(x0 + 0.5, x1 - 0.5, 9.5, top, z0 + 6.0)
	c.coin_door(cx - 6.0, 10.5, z1, 7.5)
	c.ticket_dispenser(cx + 8.0, 11.0, z1 + 0.5, 6.0)
	var surface := MachineKit.hockey_surface().full() if hockey else dark
	b.quad(x0 + 2.0, top + 0.3, z0 + 7.5, x1 - 2.0, top + 0.3, z0 + 7.5, x1 - 2.0, top + 0.3, z1 - 1.5, x0 + 2.0, top + 0.3, z1 - 1.5, surface, 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.55, true, -1, 0.8)
	# Padded rails, their tops shaded from the trim colour.
	var rail := HallArt.paint(art.trim, 0.0, 0.55).full()
	var rf := BoxFaces.new(rail, rail, rail, rail, rail, 0.0, 0.0, 0.0, 0.6)
	b.box(x0, top, z0 + 6.0, x0 + 2.0, top + 3.0, z1, rf)
	b.box(x1 - 2.0, top, z0 + 6.0, x1, top + 3.0, z1, rf)
	b.box(x0, top, z1 - 1.5, cx - 6.0, top + 3.0, z1, rf)
	b.box(cx + 6.0, top, z1 - 1.5, x1, top + 3.0, z1, rf)
	b.box(x0, top, z0 + 6.0, cx - 6.0, top + 3.0, z0 + 7.5, rf)
	b.box(cx + 6.0, top, z0 + 6.0, x1, top + 3.0, z0 + 7.5, rf)
	# The rails' inner faces carry a strip of light (the table's rim light) and the goal mouths glow.
	var glow_tex := art.glow_tex().full()
	var rim_e := MachineKit.glow_for(art.glow, 1.1)
	var ry0 := top + 0.9
	var ry1 := top + 1.7
	b.quad(x0 + 2.02, ry1, z0 + 7.5, x0 + 2.02, ry1, z1 - 1.5, x0 + 2.02, ry0, z1 - 1.5, x0 + 2.02, ry0, z0 + 7.5, glow_tex, 1.0, 0.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, rim_e)
	b.quad(x1 - 2.02, ry1, z1 - 1.5, x1 - 2.02, ry1, z0 + 7.5, x1 - 2.02, ry0, z0 + 7.5, x1 - 2.02, ry0, z1 - 1.5, glow_tex, -1.0, 0.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, rim_e)
	for span: Vector2 in [Vector2(x0 + 2.0, cx - 6.0), Vector2(cx + 6.0, x1 - 2.0)]:
		var xa := span.x
		var xb := span.y
		b.quad(xa, ry1, z0 + 7.52, xb, ry1, z0 + 7.52, xb, ry0, z0 + 7.52, xa, ry0, z0 + 7.52, glow_tex, 0.0, 0.0, 1.0,
			0.0, 0.0, NAN, NAN, Blend.OPAQUE, rim_e)
		b.quad(xb, ry1, z1 - 1.52, xa, ry1, z1 - 1.52, xa, ry0, z1 - 1.52, xb, ry0, z1 - 1.52, glow_tex, 0.0, 0.0, -1.0,
			0.0, 0.0, NAN, NAN, Blend.OPAQUE, rim_e)
	if hockey:
		var red_goal := HallArt.solid(0xFFE8323C).full()
		var blue_goal := HallArt.solid(0xFF2F5BE0).full()
		b.quad(cx - 6.0, top + 2.6, z0 + 7.52, cx + 6.0, top + 2.6, z0 + 7.52, cx + 6.0, top + 0.4, z0 + 7.52, cx - 6.0, top + 0.4, z0 + 7.52, red_goal, 0.0, 0.0, 1.0,
			0.0, 0.0, NAN, NAN, Blend.OPAQUE, 1.0)
		b.quad(cx + 6.0, top + 2.6, z1 - 1.52, cx - 6.0, top + 2.6, z1 - 1.52, cx - 6.0, top + 0.4, z1 - 1.52, cx + 6.0, top + 0.4, z1 - 1.52, blue_goal, 0.0, 0.0, -1.0,
			0.0, 0.0, NAN, NAN, Blend.OPAQUE, 1.0)
	# Overhead scoreboard on a post, framed in dark metal.
	b.box(cx - 1.5, top, z0 + 1.0, cx + 1.5, h - 12.0, z0 + 4.0, leg)
	var frame := BoxFaces.new(metal, metal, metal, metal, metal, 0.0, 0.0, 0.0, 0.7)
	if hockey:
		b.box(x0 - 3.0, h - 14.0, z0, x1 + 3.0, h, z0 + 4.0, BoxFaces.new(art.marquee().full(), dark, dark, dark, dark, CabinetBuild.MARQUEE_GLOW))
		b.box(x0 - 3.0, h - 14.6, z0, x1 + 3.0, h - 13.8, z0 + 4.2, frame)
		b.box(x0 - 3.0, h - 0.1, z0, x1 + 3.0, h + 0.5, z0 + 4.2, frame)
		c.underside(x0 - 3.0, x1 + 3.0, z0, z0 + 4.0, h - 14.0)
		c.display(cx - 8.0, cx + 8.0, h - 20.0, h - 15.0, z0 + 4.2)
		c.screen_bezel(cx - 8.0, cx + 8.0, h - 20.0, h - 15.0, z0 + 4.2, 0.8, 0.45)
	else:
		var live := c.live_screen()
		b.box(x0 - 3.0, h - 22.0, z0, x1 + 3.0, h, z0 + 4.0, BoxFaces.new(art.bezel().full(), dark, dark, dark, dark))
		b.box(x0 - 3.0, h - 22.6, z0, x1 + 3.0, h - 21.8, z0 + 4.2, frame)
		b.box(x0 - 3.0, h - 0.1, z0, x1 + 3.0, h + 0.5, z0 + 4.2, frame)
		c.underside(x0 - 3.0, x1 + 3.0, z0, z0 + 4.0, h - 22.0)
		b.quad(x0 - 1.0, h - 2.0, z0 + 4.15, x1 + 1.0, h - 2.0, z0 + 4.15, x1 + 1.0, h - 20.0, z0 + 4.15, x0 - 1.0, h - 20.0, z0 + 4.15, live.texture.full(), 0.0, 0.0, 1.0,
			0.0, 0.0, NAN, NAN, Blend.OPAQUE, SCREEN_GLOW)
		c.screen_bezel(x0 - 1.0, x1 + 1.0, h - 20.0, h - 2.0, z0 + 4.15, 1.0, 0.5)
	c.bulb_row(x0 - 2.0, x1 + 2.0, h + 0.6, z0 + 3.4, 10)
	c.light(cx, top + 26.0, (z0 + z1) / 2.0, HallArt.lift(art.glow, 0.4), 60.0, 0.9)


# ------------------------------------------------------------------ per frame

## Repaints the live screen and display (only while the machine is on screen).
func refresh(best: int, t: float) -> void:
	if screen != null:
		screen.paint(best, t)
	art.update_display(best, t)


## Fades the highlight in while [param active] is this cabinet's own play spot and out otherwise,
## and works out this frame's emissive boost. Cheap enough to run for every cabinet, on screen or
## not, so one that was lit as the kid walked away fades out rather than freezing.
func step_highlight(active: Spot, dt: float, t: float) -> void:
	highlight = Highlight.step(highlight, Highlight.is_spot_of(active, prop), dt)
	_boost = Highlight.boost(highlight, t)


## This frame's emissive multiplier (tests).
func boost() -> float:
	return _boost


## Draws the solid parts and whatever moves in attract mode.
func draw_opaque(r: Renderer3D, t: float) -> void:
	r.draw_model(model, Blend.OPAQUE, _boost, place, -1)
	if own != null and own.has_opaque:
		r.draw_model(own, Blend.OPAQUE, _boost, null, -1)
	if _design != null:
		_design.animate(r, _box, t)
		return
	var phase := t + _seed * 0.37
	var s := prop.shape
	if s == SH.CLAW or s == SH.WIDE:
		# The claw patrols over the prizes, dips now and then.
		var glass_top := _h - 8.0
		var glass_front := _z1 - CLAW_DECK_D
		var sweep := sin(phase * 0.55)
		var px := _cx + sweep * ((_x1 - _x0) / 2.0 - 6.0)
		var pz := (_z0 + glass_front) / 2.0 + sin(phase * 0.31) * ((glass_front - _z0) / 2.0 - 7.0)
		var dip := clampf((sin(phase * 0.23) - 0.75) * 4.0, 0.0, 1.0) * 12.0
		var cy := glass_top - 6.0 - dip
		r.beam(px, glass_top - 2.0, pz, px, cy, pz, 0.35, HallArt.chrome().full())
		r.draw_model(MachineKit.claw(), Blend.OPAQUE, 1.0, _xf2.set_xf(px, cy, pz), -1)
		r.quad(px - 2.0, glass_top - 1.2, pz - 3.0, px + 2.0, glass_top - 1.2, pz - 3.0, px + 2.0, glass_top - 1.2, pz + 3.0, px - 2.0, glass_top - 1.2, pz + 3.0,
			HallArt.dark_metal().full(), 0.0, -1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, 1.0, false)
	elif s == SH.WHACK:
		var table_y := 26.0
		var w := _x1 - _x0
		var d := _z1 - (_z0 + 4.0)
		var holes := MachineKit.WHACK_HOLES
		for k in holes.size():
			var cycle := fmod(phase * 0.9 + k * 1.37, 4.0)
			var rise := sin(cycle * PI) if cycle < 1.0 else 0.0
			if rise <= 0.02:
				continue
			var hole: Array = holes[k]
			var mx := _x0 + float(hole[0]) * w
			var mz := _z0 + 4.0 + float(hole[1]) * d
			r.draw_model(MachineKit.mole(), Blend.OPAQUE, 1.0, _xf.set_xf(mx, table_y - 3.0 + rise * 6.5, mz, 0.0), -1)
	elif s == SH.PUSHER:
		var deck_y := 33.0
		var push := (sin(phase * 1.4) * 0.5 + 0.5) * 5.0
		var shelf := HallArt.brushed_metal().full()
		r.quad(_x0 + 1.5, deck_y + 5.0, _z0 + 1.5, _x1 - 1.5, deck_y + 5.0, _z0 + 1.5, _x1 - 1.5, deck_y + 5.0, _z0 + 7.0 + push, _x0 + 1.5, deck_y + 5.0, _z0 + 7.0 + push,
			shelf, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, 1.0, true, -1, 1.0, 0.8)
		r.quad(_x0 + 1.5, deck_y + 5.0, _z0 + 7.0 + push, _x1 - 1.5, deck_y + 5.0, _z0 + 7.0 + push, _x1 - 1.5, deck_y, _z0 + 7.0 + push, _x0 + 1.5, deck_y, _z0 + 7.0 + push,
			art.trim_tex().full(), 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.7)
	elif s == SH.AIR_HOCKEY:
		var top := 22.3
		var w := (_x1 - _x0) / 2.0 - 5.0
		var d := (_z1 - _z0 - 8.0) / 2.0 - 5.0
		var mz := (_z0 + 7.5 + _z1 - 1.5) / 2.0
		var px := _cx + _tri(phase * 0.45) * w
		var pz := mz + _tri(phase * 0.33 + 0.3) * d
		r.draw_model(MachineKit.puck(), Blend.OPAQUE, 1.0, _xf.set_xf(px, top, pz), -1)
		if _blue_mallet == null:
			_blue_mallet = MachineKit.hockey_mallet(0xFF2F5BE0)
			_red_mallet = MachineKit.hockey_mallet(0xFFE8323C)
		r.draw_model(_blue_mallet, Blend.OPAQUE, 1.0, _xf.set_xf(_cx + (px - _cx) * 0.6, top, _z1 - 7.0), -1)
		r.draw_model(_red_mallet, Blend.OPAQUE, 1.0, _xf.set_xf(_cx + (px - _cx) * 0.7, top, _z0 + 13.0), -1)
	elif s == SH.SKEEBALL:
		# Now and then a ball rolls up the lane and jumps into the rings.
		var cycle := fmod(phase * 0.5, 3.0)
		if cycle < 1.3:
			var k := cycle / 1.3
			var lane_front := _z1 - 10.0
			var board_z := _z0 + 8.0
			var zz := lane_front - (lane_front - board_z - 14.0) * k
			var yy := 19.5 + 8.0 * k + (sin((k - 0.75) / 0.25 * PI) * 5.0 if k > 0.75 else 0.0)
			if _skee_ball == null:
				_skee_ball = MachineKit.ball(0xFFB0213A)
			r.draw_model(_skee_ball, Blend.OPAQUE, 1.0, _xf.set_xf(_cx + sin(phase * 3.0) * 2.0, yy + 2.0, zz, 0.0, -k * 20.0, 0.0, 2.1), -1)
	elif s == SH.HOOPS:
		var cycle := fmod(phase * 0.45, 3.0)
		if cycle < 1.2:
			var k := cycle / 1.2
			var rim_y := _h - 29.0
			var zz := (_z1 - 8.0) + ((_z0 + 10.0) - (_z1 - 8.0)) * k
			var yy := 24.0 + (rim_y + 12.0 - 24.0) * (4.0 * k * (1.0 - k)) + (rim_y - 24.0) * k * k
			r.draw_model(MachineKit.basketball(), Blend.OPAQUE, 1.0, _xf.set_xf(_cx, yy, zz, 0.0, k * 9.0, 0.0, 3.2), -1)


## Glass and netting, drawn after every solid thing, then the additive glows (halos, light pools
## under the cabinet, neon edges) over them.
func draw_transparent(r: Renderer3D) -> void:
	if model.has_alpha:
		r.draw_model(model, Blend.ALPHA, _boost, place, -1)
	if own != null and own.has_alpha:
		r.draw_model(own, Blend.ALPHA, _boost, null, -1)
	if model.has_add:
		r.draw_model(model, Blend.ADD, _boost, place, -1)
	if own != null and own.has_add:
		r.draw_model(own, Blend.ADD, _boost, null, -1)


## Chasing marquee bulbs, with a soft halo each; the highlight brightens the idle bulbs and the
## flashing halos.
func draw_bulbs(r: Renderer3D, t: float, bulb: Region, halo: Region) -> void:
	var n := _bulbs.size() / 3
	if n == 0:
		return
	var chase := int((t + _seed * 0.19) * 9.0)
	var hl := Highlight.ease(highlight)
	var idle := 0.7 + Highlight.BULB_BOOST * hl
	var halo_alpha := 0.55 + Highlight.BULB_HALO * hl
	var off := HallArt.dim(art.trim, 0.45)
	for i in n:
		var x := _bulbs[i * 3]
		var y := _bulbs[i * 3 + 1]
		var z := _bulbs[i * 3 + 2]
		var on := (chase + i) % 3 == 0
		r.sprite(x, y, z, 1.4, 1.4, bulb, 0.0, Blend.OPAQUE, 1.8 if on else idle, 1.0, 1.0, 0xFFFFF4C0 if on else off)
		if on:
			r.sprite(x, y, z + 0.3, 6.0, 6.0, halo, 0.0, Blend.ADD, 1.0, halo_alpha, 1.0, 0xFFFFE08A)


## Bulb positions (x, y, z per bulb), for tests.
func bulbs() -> PackedFloat32Array:
	return _bulbs


static func _tri(x: float) -> float:
	var f := x - floorf(x)
	return absf(f * 4.0 - 2.0) - 1.0


# ------------------------------------------------------------------ sharing (Godot)

## The whole cabinet as one model in world coordinates, as build-13 built it (the shared part placed
## where this copy stands, plus its own part). For tests and tools; the hall draws the parts.
func full_model() -> Model:
	if place == null and own == null:
		return model
	var b := ModelBuilder.new()
	if place != null:
		b.add_xf(model, place)
	else:
		b.add(model)
	if own != null:
		b.add(own)
	return b.build()


## Godot-only: lets the copies of one game's bank share one model. Every copy builds the same cabinet
## in its own place apart from what is its own: its live screen, a seeded prize pile or coin spread
## (bracketed with CabinetBuild.begin_own/end_own) and any texture not every copy uses (a numbered
## seat). The rest is compared copy by copy, moved by the difference in where they stand; a copy that
## matches the first draws the first's model placed where it stands, so the GPU draws the bank's
## common parts as one instanced batch per texture. A copy that doesn't match keeps its whole model.
## Returns how many copies now share.
static func share_bank(units: Array[MachineUnit]) -> int:
	if units.size() < 2:
		return 0
	var parts: Array = []
	var common := {}
	for i in units.size():
		var u := units[i]
		var own_mask := u._own_mask()
		var texes := {}
		for k in u.model.polys.size():
			if own_mask[k] == 0:
				texes[u.model.polys[k].region.tex.id] = true
		if i == 0:
			common = texes
		else:
			for id: int in common.keys():
				if not texes.has(id):
					common.erase(id)
		parts.append(own_mask)
	var splits: Array = []
	for i in units.size():
		var u := units[i]
		var own_mask: PackedByteArray = parts[i]
		var shared: Array[Poly] = []
		var mine: Array[Poly] = []
		for k in u.model.polys.size():
			var p := u.model.polys[k]
			if own_mask[k] == 0 and common.has(p.region.tex.id):
				shared.append(p)
			else:
				mine.append(p)
		splits.append([shared, mine])
	var ref := units[0]
	var ref_shared: Array[Poly] = splits[0][0]
	var shared_model := Model.new(ref_shared)
	var sharing := 0
	for i in range(1, units.size()):
		var u := units[i]
		var dx := u.prop.x0 - ref.prop.x0
		var dz := u.prop.z0 - ref.prop.z0
		var candidate: Array[Poly] = splits[i][0]
		if not _same_polys(ref_shared, candidate, dx, dz):
			continue
		var mine: Array[Poly] = splits[i][1]
		u.model = shared_model
		u.place = Xform.new().set_xf(dx, 0.0, dz)
		u.own = Model.new(mine) if not mine.is_empty() else null
		sharing += 1
	if sharing > 0:
		var ref_mine: Array[Poly] = splits[0][1]
		ref.model = shared_model
		ref.place = null
		ref.own = Model.new(ref_mine) if not ref_mine.is_empty() else null
		sharing += 1
	return sharing


## Which of this unit's polygons are its own (1): its screen's, and the ranges its build marked.
func _own_mask() -> PackedByteArray:
	var polys := model.polys
	var mask := PackedByteArray()
	mask.resize(polys.size())
	var screen_tex: PaTexture = screen.texture if screen != null else null
	for k in polys.size():
		if screen_tex != null and polys[k].region.tex == screen_tex:
			mask[k] = 1
	for r: Vector2i in _box.own_ranges:
		for k in range(r.x, mini(r.y, polys.size())):
			mask[k] = 1
	return mask


static func _same_polys(a: Array[Poly], b: Array[Poly], dx: float, dz: float) -> bool:
	if a.size() != b.size():
		return false
	for i in a.size():
		if not _same_poly(a[i], b[i], dx, dz):
			return false
	return true


static func _same_poly(a: Poly, b: Poly, dx: float, dz: float) -> bool:
	if a.n != b.n or a.blend != b.blend or a.cull != b.cull or (a.tint & 0xFFFFFFFF) != (b.tint & 0xFFFFFFFF):
		return false
	var ra := a.region
	var rb := b.region
	if ra != rb and (ra.tex != rb.tex or ra.x != rb.x or ra.y != rb.y or ra.w != rb.w or ra.h != rb.h or ra.wrap != rb.wrap):
		return false
	if absf(a.emissive - b.emissive) > 1e-5 or absf(a.gloss - b.gloss) > 1e-5:
		return false
	if absf(a.nx - b.nx) > 1e-4 or absf(a.ny - b.ny) > 1e-4 or absf(a.nz - b.nz) > 1e-4:
		return false
	const E := 2e-3
	for i in a.n:
		if absf(a.xs[i] + dx - b.xs[i]) > E or absf(a.ys[i] - b.ys[i]) > E or absf(a.zs[i] + dz - b.zs[i]) > E:
			return false
		if absf(a.us[i] - b.us[i]) > E or absf(a.vs[i] - b.vs[i]) > E:
			return false
	if not _close(a.vnx, b.vnx) or not _close(a.vny, b.vny) or not _close(a.vnz, b.vnz) or not _close(a.shade, b.shade):
		return false
	return true


static func _close(a: PackedFloat32Array, b: PackedFloat32Array) -> bool:
	if a.size() != b.size():
		return false
	for i in a.size():
		if absf(a[i] - b[i]) > 1e-4:
			return false
	return true
