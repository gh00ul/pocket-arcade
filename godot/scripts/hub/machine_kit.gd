class_name MachineKit
extends RefCounted
## hub/MachineKit.kt: materials and small parts shared by every cabinet of a kind (nets, lanes,
## balls, claws...). Kotlin's `by lazy` values are functions here that build on first call and hand
## back the same texture or model after (allocation-free, so draw code may ask every frame).

# ------------------------------------------------------------------ control hardware

## Where the printed control panel (CabinetPaint.panel) puts its joystick well and button rings, as
## fractions of the panel's width, so the hardware sits exactly on its printing.
const PANEL_STICK_U := 0.26
const PANEL_BUTTON_U := 0.56
const PANEL_BUTTON_STEP_U := 0.12
const PANEL_BUTTON_ROW_SHIFT_U := 0.03
## The printed rows of buttons, as fractions of the panel's depth from its back.
const PANEL_ROW_V := 0.33
const PANEL_ROW2_V := 0.625

## How brightly a lit button cap's core glows (kept low: caps are saturated but often light).
const CAP_GLOW := 0.7
## Pale colours bloom sooner, so a glow multiplier is cut by up to this much at pure white.
const PALE_GLOW_CUT := 0.3

## Left slot centre, spacing and vertical extent of the lit coin windows, as fractions of the door.
const SLOT_X0 := 36.0 / 128.0
const SLOT_DX := 56.0 / 128.0
const SLOT_Y0 := 21.0 / 160.0
const SLOT_Y1 := 63.0 / 160.0
const SLOT_HALF_W := 15.0 / 128.0

## Hole positions for the whack-a-mole table top (fractions of width and depth): [fx, fz] pairs.
const WHACK_HOLES := [[0.2, 0.62], [0.35, 0.38], [0.5, 0.66], [0.65, 0.38], [0.8, 0.62]]

static var _net: PaTexture = null
static var _velvet: PaTexture = null
static var _lane_wood: PaTexture = null
static var _skee_rings: PaTexture = null
static var _court: PaTexture = null
static var _hoop_board: PaTexture = null
static var _hockey_surface: PaTexture = null
static var _whack_tops := {}
static var _prize_chute: PaTexture = null
static var _deck: PaTexture = null
static var _gold: PaTexture = null
static var _coin_face: PaTexture = null
static var _seat_leather: PaTexture = null
static var _rubber: PaTexture = null
static var _mole_fur: PaTexture = null
static var _mole_face: PaTexture = null
static var _rear_tall: PaTexture = null
static var _rear_wide: PaTexture = null
static var _coin_door: PaTexture = null
static var _coin_slot_lit: PaTexture = null
static var _speaker_strip: PaTexture = null
static var _ticket_plate: PaTexture = null
static var _ticket_paper: PaTexture = null

static var _balls := {}
static var _basketball: Model = null
static var _coin: Model = null
static var _mole: Model = null
static var _mallet: Model = null
static var _claw: Model = null
static var _puck: Model = null
static var _mallets := {}
static var _buttons := {}
static var _sticks := {}
static var _trackball: Model = null


static func _done(tp: TexPaint, repeat: bool = false) -> PaTexture:
	var t := tp.to_texture()
	if repeat:
		t.repeat = true
	tp.recycle()
	return t


## Diamond mesh netting (alpha-blended).
static func net() -> PaTexture:
	if _net == null:
		var tp := TexPaint.new(128, 128)
		tp.clear(0)
		for k in range(-8, 17):
			tp.line(k * 16.0, 0.0, k * 16.0 + 128.0, 128.0, 1.6, HallArt.alpha(0xFFFFFFFF, 0.55), false)
			tp.line(k * 16.0, 128.0, k * 16.0 + 128.0, 0.0, 1.6, HallArt.alpha(0xFFFFFFFF, 0.55), false)
		_net = _done(tp, true)
	return _net


static func velvet() -> PaTexture:
	if _velvet == null:
		var tp := TexPaint.new(64, 64)
		tp.vgrad(0.0, 0.0, 64.0, 64.0, [0xFF5A1640, 0xFF2E0A22])
		tp.grain(0.12, 17)
		_velvet = _done(tp)
	return _velvet


static func lane_wood() -> PaTexture:
	if _lane_wood == null:
		_lane_wood = HallArt.wood(0xFFD69A5A, 5)
	return _lane_wood


## Skee-ball target (painted at 3x): concentric scoring rings, each a dished, bevelled band in its
## own colour with its value in big outlined numerals, a black 100 pocket in the middle and two
## rimmed bonus holes in the top corners.
static func skee_rings() -> PaTexture:
	if _skee_rings == null:
		_skee_rings = TexPaint.paint_texture(192, 192, 3, func(tp: TexPaint) -> void:
			var w := 192.0
			var h := 192.0
			tp.vgrad(0.0, 0.0, w, h, [0xFF22306E, 0xFF101838])
			var cx := w / 2.0
			var cy := h * 0.56
			var colors := [0xFF4DA6FF, 0xFF8A4FFF, 0xFFFF3FA4, 0xFFFF9A3C, 0xFFFFE14D, 0xFFFF4D4D]
			var radii := [88.0, 72.0, 57.0, 42.0, 27.0, 12.0]
			var points := [10, 20, 30, 40, 50, 100]
			for i in radii.size():
				var r: float = radii[i]
				var col: int = colors[i]
				# The dish: a dark rim, the band's colour, a lighter crown and a shadow line under the next ring in.
				tp.circle(cx, cy + 1.5, r, HallArt.alpha(0xFF000000, 0.5))
				tp.circle(cx, cy, r, HallArt.dim(col, 0.5))
				tp.circle(cx, cy + 2.0, r - 3.0, HallArt.dim(col, 0.34))
				tp.radial(cx, cy - r * 0.3, r * 0.95, HallArt.alpha(HallArt.lift(col, 0.4), 0.35), 0)
				tp.ring(cx, cy, r - 1.1, 2.2, col)
				tp.ring(cx, cy, r - 2.6, 0.7, HallArt.alpha(0xFFFFFFFF, 0.35))
			tp.circle(cx, cy, 9.0, 0xFF050308)
			tp.ring(cx, cy, 9.5, 1.8, 0xFFC4C8D4)
			for i in radii.size() - 1:
				var r: float = (float(radii[i]) + float(radii[i + 1])) / 2.0
				var x := cx + (r if i % 2 == 0 else -r)
				tp.outlined_text(str(points[i]), x, cy + 5.5, 14.0, 0xFFFFFFFF, 0xFF0A1030, 3.0, Fonts.display())
			# The 200 bonus pockets in the top corners, rimmed in chrome.
			for sx: float in [20.0, w - 20.0]:
				tp.circle(sx, 22.0, 11.0, 0xFFC4C8D4)
				tp.circle(sx, 22.0, 9.0, 0xFFFFC83D)
				tp.circle(sx, 22.0, 6.5, 0xFF050308)
			tp.stroke_round(2.0, 2.0, w - 4.0, h - 4.0, 6.0, 3.0, 0xFFC4C8D4)
			tp.grain(0.03, 5))
	return _skee_rings


static func court() -> PaTexture:
	if _court == null:
		var tp := TexPaint.new(128, 256)
		for x in range(0, 128, 16):
			var c := 0xFFD8A266 if (x / 16) % 2 == 0 else 0xFFCC955A
			tp.rect(float(x), 0.0, 16.0, 256.0, c)
			tp.rect(float(x), 0.0, 1.0, 256.0, 0xFF9A6A3A)
		tp.rect(40.0, 0.0, 48.0, 90.0, HallArt.alpha(0xFFFF3B30, 0.45))
		tp.stroke_round(40.0, -10.0, 48.0, 100.0, 2.0, 3.0, 0xFFFFFFFF)
		tp.ring(64.0, 90.0, 24.0, 3.0, 0xFFFFFFFF)
		tp.grain(0.05, 3)
		_court = _done(tp)
	return _court


static func hoop_board() -> PaTexture:
	if _hoop_board == null:
		var tp := TexPaint.new(192, 128)
		tp.vgrad(0.0, 0.0, 192.0, 128.0, [0xFFF4F8FF, 0xFFC8D6EA])
		tp.stroke_round(4.0, 4.0, 184.0, 120.0, 6.0, 6.0, 0xFFE8323C)
		tp.stroke_round(66.0, 56.0, 60.0, 50.0, 2.0, 5.0, 0xFFE8323C)
		_hoop_board = _done(tp)
	return _hoop_board


## The air-hockey surface (painted at 3x): pale ice-blue with a grid of air holes, the red centre
## line and circle, blue goal arcs and creases, and dark goal slots at both ends.
static func hockey_surface() -> PaTexture:
	if _hockey_surface == null:
		_hockey_surface = TexPaint.paint_texture(128, 256, 3, func(tp: TexPaint) -> void:
			var w := 128.0
			var h := 256.0
			tp.vgrad(0.0, 0.0, w, h, [0xFFF2F8FF, 0xFFD8E8FA])
			for y in range(6, 256, 10):
				for x in range(6, 128, 10):
					tp.circle(float(x), float(y), 1.1, 0xFF9DB4D0)
			tp.rect(0.0, h / 2.0 - 2.0, w, 4.0, 0xFFE8323C)
			tp.ring(w / 2.0, h / 2.0, 22.0, 3.0, 0xFFE8323C)
			tp.circle(w / 2.0, h / 2.0, 3.0, 0xFFE8323C)
			tp.ring(w / 2.0, 0.0, 30.0, 3.0, 0xFF2F5BE0)
			tp.ring(w / 2.0, h, 30.0, 3.0, 0xFF2F5BE0)
			tp.rect(0.0, h * 0.25, w, 3.0, 0xFF2F5BE0)
			tp.rect(0.0, h * 0.75, w, 3.0, 0xFF2F5BE0)
			tp.round(w / 2.0 - 22.0, -2.0, 44.0, 7.0, 3.0, 0xFF101018)
			tp.round(w / 2.0 - 22.0, h - 5.0, 44.0, 7.0, 3.0, 0xFF101018)
			tp.grain(0.02, 9))
	return _hockey_surface


## The whack-a-mole table top for a body colour, painted once per colour at 2x (all copies of a
## machine share it).
static func whack_top(color: int) -> PaTexture:
	var c := color & 0xFFFFFFFF
	var t: PaTexture = _whack_tops.get(c)
	if t == null:
		t = TexPaint.paint_texture(256, 208, 2, func(tp: TexPaint) -> void:
			var w := 256.0
			var h := 208.0
			tp.vgrad(0.0, 0.0, w, h, [HallArt.lift(c, 0.12), HallArt.dim(c, 0.66)])
			tp.radial(w / 2.0, h * 0.4, w * 0.6, HallArt.alpha(HallArt.lift(c, 0.5), 0.25), 0)
			for hole: Array in WHACK_HOLES:
				var x: float = hole[0] * w
				var y: float = hole[1] * h
				# Each hole: a rubber collar, a dark well and a shadow at the back of it.
				tp.circle(x, y + 1.5, 32.0, HallArt.alpha(0xFF000000, 0.35))
				tp.circle(x, y, 30.0, 0xFF7A4A22)
				tp.circle(x, y, 27.0, 0xFF3A2414)
				tp.radial(x, y + 3.0, 25.0, 0xFF050303, 0xFF2A1A0E)
			tp.rect(0.0, 0.0, w, 8.0, 0xFF8B5A2B)
			tp.rect(0.0, 8.0, w, 1.5, HallArt.alpha(0xFFFFFFFF, 0.2))
			tp.grain(0.05, 21))
		_whack_tops[c] = t
	return t


## The prize door on a claw machine's front: a smoked flap hinged along its top edge over a dark
## chute, with "PRIZE" and a down arrow printed on it in warm yellow.
static func prize_chute() -> PaTexture:
	if _prize_chute == null:
		_prize_chute = TexPaint.paint_texture(48, 52, CabinetPaint.PLATE_SCALE, func(tp: TexPaint) -> void:
			tp.round(0.0, 0.0, 48.0, 52.0, 4.0, 0xFF08070C)
			tp.round_grad(3.0, 3.0, 42.0, 46.0, 3.0, 0xFF3A3648, 0xFF14121C)
			tp.rect(3.0, 9.0, 42.0, 1.4, 0xFF8A8E9E)
			for x: float in [6.0, 24.0, 42.0]:
				tp.circle(x, 6.0, 1.6, 0xFF8A8E9E)
			tp.text("PRIZE", 24.0, 26.0, 10.0, 0xFFFFD84D, Fonts.condensed())
			tp.polygon(PackedFloat32Array([17.0, 32.0, 31.0, 32.0, 24.0, 41.0]), HallArt.alpha(0xFFFFD84D, 0.9))
			tp.vgrad(3.0, 10.0, 42.0, 14.0, [HallArt.alpha(0xFFFFFFFF, 0.16), 0]))
	return _prize_chute


static func deck() -> PaTexture:
	if _deck == null:
		var tp := TexPaint.new(128, 128)
		tp.vgrad(0.0, 0.0, 128.0, 128.0, [0xFF1E2A6C, 0xFF101640])
		for y in range(0, 128, 16):
			tp.rect(0.0, float(y), 128.0, 1.5, 0xFF2E3C8E)
		_deck = _done(tp)
	return _deck


static func gold() -> PaTexture:
	if _gold == null:
		_gold = HallArt.paint(0xFFFFC83D, 0.35, 0.7)
	return _gold


static func coin_face() -> PaTexture:
	if _coin_face == null:
		var tp := TexPaint.new(64, 64)
		tp.circle(32.0, 32.0, 32.0, 0xFFE0A020)
		tp.circle(32.0, 32.0, 26.0, 0xFFFFD04A)
		tp.ring(32.0, 32.0, 20.0, 2.0, 0xFFE0A020)
		tp.text("★", 32.0, 42.0, 26.0, 0xFFE0A020, Fonts.heavy())
		_coin_face = _done(tp)
	return _coin_face


static func seat_leather() -> PaTexture:
	if _seat_leather == null:
		var tp := TexPaint.new(64, 64)
		tp.vgrad(0.0, 0.0, 64.0, 64.0, [0xFF2C2A32, 0xFF141218])
		for x in range(8, 64, 16):
			tp.rect(float(x), 0.0, 1.0, 64.0, 0xFF3C3A44)
		tp.grain(0.06, 5)
		_seat_leather = _done(tp)
	return _seat_leather


static func rubber() -> PaTexture:
	if _rubber == null:
		_rubber = HallArt.paint(0xFF1A1A20, 0.08, 0.8)
	return _rubber


static func mole_fur() -> PaTexture:
	if _mole_fur == null:
		_mole_fur = HallArt.paint(0xFF8B5A2B, 0.15, 0.8)
	return _mole_fur


## Mole head: fur with eyes, a pink nose and buck teeth at the front.
static func mole_face() -> PaTexture:
	if _mole_face == null:
		var tp := TexPaint.new(128, 64)
		tp.vgrad(0.0, 0.0, 128.0, 64.0, [0xFF9C6A38, 0xFF6E4420])
		var cx := 32.0
		tp.oval(cx, 40.0, 16.0, 11.0, 0xFFD9A066)
		for s: int in [-1, 1]:
			tp.oval(cx + s * 9.0, 26.0, 3.2, 4.2, 0xFF120C08)
			tp.circle(cx + s * 9.0 - 1.0, 24.5, 1.2, 0xFFFFFFFF)
		tp.oval(cx, 36.0, 5.0, 3.5, 0xFFFF6FA0)
		tp.rect(cx - 3.0, 43.0, 2.6, 4.0, 0xFFFFFFFF)
		tp.rect(cx + 0.4, 43.0, 2.6, 4.0, 0xFFFFFFFF)
		_mole_face = _done(tp)
	return _mole_face


## The back of a cabinet, as a kid walking behind a bank sees it: a screwed-on service panel with
## louvred vents, a fan grille, the mains inlet, a serial plate and the usual stickers. [param tall]
## is for backs taller than they are wide.
static func rear_panel(tall: bool) -> PaTexture:
	if tall:
		if _rear_tall == null:
			_rear_tall = _paint_rear(128, 256)
		return _rear_tall
	if _rear_wide == null:
		_rear_wide = _paint_rear(256, 160)
	return _rear_wide


static func _paint_rear(w: int, h: int) -> PaTexture:
	var tp := TexPaint.new(w, h)
	var fw := float(w)
	var fh := float(h)
	tp.vgrad(0.0, 0.0, fw, fh, [0xFF2A2830, 0xFF17161C])
	tp.grain(0.05, w + h)
	# The removable service panel, its screws and a keyed lock.
	var m := fw * 0.08
	tp.round(m, m, fw - 2.0 * m, fh - 2.0 * m, 4.0, 0xFF211F27)
	tp.stroke_round(m, m, fw - 2.0 * m, fh - 2.0 * m, 4.0, 1.5, 0xFF3C3A46)
	for sx: float in [m + 5.0, fw - m - 5.0]:
		for sy: float in [m + 5.0, fh - m - 5.0]:
			tp.circle(sx, sy, 2.4, 0xFF8A8C98)
			tp.rect(sx - 1.6, sy - 0.4, 3.2, 0.8, 0xFF3A3A44)
	# Louvred vents across the top.
	var vx := m + 12.0
	var vw := fw - 2.0 * vx
	var vy := m + 12.0
	for k in 7:
		tp.round(vx, vy, vw, 3.4, 1.7, 0xFF08070B)
		tp.rect(vx + 1.0, vy + 3.4, vw - 2.0, 1.0, 0xFF3A3842)
		vy += 7.0
	# Fan grille, and the mains inlet with its switch.
	var fan_r := minf(fw, fh) * 0.16
	var fan_x := fw * 0.72 if w > h else fw * 0.5
	var fan_y := fh * 0.62 if w > h else fh * 0.5
	tp.circle(fan_x, fan_y, fan_r + 2.0, 0xFF3C3A46)
	tp.circle(fan_x, fan_y, fan_r, 0xFF09080C)
	for k in range(1, 5):
		tp.ring(fan_x, fan_y, fan_r * k / 4.5, 1.0, 0xFF55535F)
	tp.line(fan_x - fan_r, fan_y, fan_x + fan_r, fan_y, 1.0, 0xFF55535F)
	tp.line(fan_x, fan_y - fan_r, fan_x, fan_y + fan_r, 1.0, 0xFF55535F)
	var ix := fw * 0.2 if w > h else fw * 0.3
	var iy := fh - m - 34.0
	tp.round(ix, iy, 26.0, 18.0, 2.0, 0xFF0C0B10)
	tp.rect(ix + 5.0, iy + 5.0, 16.0, 8.0, 0xFF2A2830)
	tp.round(ix + 30.0, iy + 2.0, 10.0, 14.0, 2.0, 0xFFB0213A)
	# A riveted serial plate.
	var px := fw * 0.14 if w > h else fw * 0.22
	var py := fh * 0.5 if w > h else fh * 0.7
	var pw := fw * 0.3 if w > h else fw * 0.56
	tp.round(px, py, pw, 18.0, 2.0, 0xFFB8BCC8)
	tp.rect(px + 4.0, py + 5.0, pw * 0.6, 2.0, 0xFF4A4C58)
	tp.rect(px + 4.0, py + 10.0, pw * 0.8, 2.0, 0xFF4A4C58)
	tp.text("SN 04-7731", px + pw / 2.0, py + 17.0, 5.0, 0xFF2A2C36, Fonts.heavy())
	# Stickers: a yellow warning triangle and a white service label.
	var wx := fw * 0.52 if w > h else fw * 0.3
	var wy := fh * 0.5 if w > h else fh * 0.34
	tp.polygon(PackedFloat32Array([wx, wy + 22.0, wx + 13.0, wy, wx + 26.0, wy + 22.0]), 0xFFFFD23A)
	tp.polygon(PackedFloat32Array([wx + 4.0, wy + 20.0, wx + 13.0, wy + 5.0, wx + 22.0, wy + 20.0]), 0xFF16141C)
	tp.polygon(PackedFloat32Array([wx + 6.5, wy + 18.5, wx + 13.0, wy + 8.0, wx + 19.5, wy + 18.5]), 0xFFFFD23A)
	tp.rect(wx + 12.0, wy + 10.5, 2.0, 5.0, 0xFF16141C)
	tp.rect(wx + 12.0, wy + 16.5, 2.0, 1.6, 0xFF16141C)
	var lx := fw * 0.52 if w > h else fw * 0.56
	var ly := fh * 0.72 if w > h else fh * 0.36
	tp.round(lx, ly, 34.0, 22.0, 2.0, 0xFFF2F0EA)
	tp.rect(lx, ly, 34.0, 6.0, 0xFFE8323C)
	for k in 3:
		tp.rect(lx + 3.0, ly + 9.0 + k * 4.0, 28.0 - k * 6.0, 1.6, 0xFF6A6870)
	return _done(tp)


# ------------------------------------------------------------------ small parts

static func _colored(color: int) -> Region:
	return HallArt.paint(color, 0.25, 0.75).full()


## A glossy ball of the given colour, radius 1 (scale with an [Xform]).
static func ball(color: int) -> Model:
	var c := color & 0xFFFFFFFF
	var m: Model = _balls.get(c)
	if m == null:
		m = ModelBuilder.new().sphere(0.0, 0.0, 0.0, 1.0, _colored(c), 12, 8, 1.0, -1, 0.8).build()
		_balls[c] = m
	return m


static func basketball() -> Model:
	if _basketball == null:
		var tp := TexPaint.new(128, 64)
		tp.fill(0xFFE8782A)
		tp.grain(0.08, 3)
		for x: int in [0, 32, 64, 96]:
			tp.rect(float(x), 0.0, 2.5, 64.0, 0xFF3A1A08)
		tp.rect(0.0, 31.0, 128.0, 2.5, 0xFF3A1A08)
		var tex := _done(tp)
		_basketball = ModelBuilder.new().sphere(0.0, 0.0, 0.0, 1.0, tex.full(), 12, 8, 1.0, -1, 0.3).build()
	return _basketball


static func coin() -> Model:
	if _coin == null:
		_coin = ModelBuilder.new().cylinder(0.0, 0.0, -0.35, 0.35, 2.2, 12, gold().full(), coin_face().full(), 0.0, -1, null, false, NAN, true, 0.9).build()
	return _coin


static func mole() -> Model:
	if _mole == null:
		var b := ModelBuilder.new()
		b.cylinder(0.0, 0.0, -9.0, 0.0, 2.6, 12, mole_fur().full(), null, 0.0, -1, null, false, NAN, true, 0.1)
		b.sphere(0.0, 0.0, 0.0, 2.9, mole_face().full(), 16, 10, 1.05, -1, 0.1)
		_mole = b.build()
	return _mole


static func mallet() -> Model:
	if _mallet == null:
		var b := ModelBuilder.new()
		b.capsule(0.0, 0.0, 0.0, 0.0, 0.0, 9.0, 0.5, HallArt.wood().full())
		var cap := _colored(0xFFFFE0A0)
		var head := ModelBuilder.new().cylinder(0.0, 0.0, -3.0, 3.0, 1.8, 12, _colored(0xFFE8323C), cap, 0.0, -1, cap, false, NAN, true, 0.4).build()
		b.add_xf(head, Xform.new().set_xf(0.0, 0.0, 9.5, 0.0, 0.0, PI / 2.0))
		_mallet = b.build()
	return _mallet


## Claw: hub, cable stub and three hooked prongs, hanging from y = 0 down to about -9.
static func claw() -> Model:
	if _claw == null:
		var b := ModelBuilder.new()
		var metal := HallArt.chrome().full()
		b.cylinder(0.0, 0.0, -3.0, 0.0, 1.8, 12, metal, metal, 0.0, -1, metal, false, NAN, true, 0.9)
		for k in 3:
			var a := PI / 2.0 + k * 2.0 * PI / 3.0
			var c := cos(a)
			var s := sin(a)
			b.capsule(c * 1.4, -2.5, s * 1.4, c * 3.6, -7.0, s * 3.6, 0.35, metal, 6, -1, 0.9)
			b.capsule(c * 3.6, -7.0, s * 3.6, c * 2.4, -9.0, s * 2.4, 0.35, metal, 6, -1, 0.9)
		_claw = b.build()
	return _claw


static func puck() -> Model:
	if _puck == null:
		_puck = ModelBuilder.new().cylinder(0.0, 0.0, 0.0, 0.9, 2.2, 14, _colored(0xFFE8323C), _colored(0xFFFF5A5A), 0.0, -1, null, false, NAN, true, 0.6).build()
	return _puck


static func hockey_mallet(color: int) -> Model:
	var key := color & 0xFFFFFFFF
	var m: Model = _mallets.get(key)
	if m == null:
		var c := _colored(color)
		m = ModelBuilder.new() \
			.cylinder(0.0, 0.0, 0.0, 1.6, 3.2, 16, c, c, 0.0, -1, null, false, NAN, true, 0.6) \
			.cylinder(0.0, 0.0, 1.6, 4.6, 1.2, 10, c, c, 0.0, -1, null, false, NAN, true, 0.6) \
			.build()
		_mallets[key] = m
	return m


## The emissive to give a surface painted [param color] that should read as lit at strength
## [param base]: light colours (white trim, yellow, cyan) already sit close to the bloom threshold,
## so they get less than dark saturated ones and the highlight's extra glow has headroom.
static func glow_for(color: int, base: float) -> float:
	var lum := (((color >> 16) & 255) * 0.3 + ((color >> 8) & 255) * 0.59 + (color & 255) * 0.11) / 255.0
	var pale := clampf((lum - 0.5) / 0.5, 0.0, 1.0)
	return base * (1.0 - PALE_GLOW_CUT * pale)


## Arcade push button of cap [param radius], its bottom at y = 0: a chrome-topped collar, a dark gap,
## a shaded plastic dome and a lit core on the dome's crown (the same paint, whose lightest part is
## the crown), in [param color]. Eight-sided and smooth shaded, about 60 polygons.
static func button(color: int, radius: float) -> Model:
	var key := Vector2(color & 0xFFFFFFFF, radius)
	var m: Model = _buttons.get(key)
	if m == null:
		var cap := _colored(color)
		var chrome := HallArt.chrome().full()
		var black := HallArt.solid(0xFF08070C).full()
		var r := radius
		m = ModelBuilder.new() \
			.cylinder(0.0, 0.0, 0.0, r * 0.4, r * 1.28, 8, HallArt.dark_metal().full(), chrome, 0.0, -1, null, false, NAN, true, 0.85) \
			.cylinder(0.0, 0.0, r * 0.4, r * 0.5, r * 1.08, 8, black, black) \
			.sphere(0.0, r * 0.5, 0.0, r, cap, 8, 3, 0.55, -1, 0.85, 0.0, 0.0) \
			.sphere(0.0, r * 0.5, 0.0, r * 1.01, cap, 8, 2, 0.55, -1, 0.6, CAP_GLOW, 0.55) \
			.build()
		_buttons[key] = m
	return m


## Ball-top joystick with a ball of [param color] (the classic red one by default: Kotlin's
## `joystick` property), its plate at y = 0, about 100 polygons.
static func joystick(color: int = 0xFFE8323C) -> Model:
	var key := color & 0xFFFFFFFF
	var m: Model = _sticks.get(key)
	if m == null:
		var plate := HallArt.dark_metal().full()
		m = ModelBuilder.new() \
			.box(-2.3, 0.0, -2.3, 2.3, 0.35, 2.3, BoxFaces.all(plate, 0.5)) \
			.lathe(0.0, 0.0, 0.0, PackedFloat32Array([1.8, 0.35, 1.4, 0.9, 0.75, 1.9, 0.5, 2.3]), 8, rubber().full(), -1, 0.25) \
			.cylinder(0.0, 0.0, 2.3, 5.2, 0.32, 6, HallArt.chrome().full(), null, 0.0, -1, null, false, NAN, true, 0.9) \
			.sphere(0.0, 6.2, 0.0, 1.4, _colored(color), 10, 6, 1.0, -1, 0.9) \
			.build()
		_sticks[key] = m
	return m


## A trackball: a plate with a ring collar and a lustrous ball in it, its plate at y = 0.
static func trackball() -> Model:
	if _trackball == null:
		var ball_tex := _colored(0xFF2A4AD0)
		_trackball = ModelBuilder.new() \
			.cylinder(0.0, 0.0, 0.0, 0.5, 3.0, 10, HallArt.dark_metal().full(), HallArt.chrome().full(), 0.0, -1, null, false, NAN, true, 0.85) \
			.sphere(0.0, 0.9, 0.0, 2.3, ball_tex, 10, 6, 1.0, -1, 0.95, 0.0, -0.3) \
			.build()
	return _trackball


# ------------------------------------------------------------------ plates and decals

## The steel coin door (128 x 160 texels, painted at 4x): two coin mechs with chrome slot plates, a
## return flap under each, a key lock and screws. The slots' lit windows are [method coin_slot_lit],
## laid over the plates at the SLOT_ fractions.
static func coin_door() -> PaTexture:
	if _coin_door == null:
		_coin_door = TexPaint.paint_texture(128, 160, CabinetPaint.PLATE_SCALE, func(tp: TexPaint) -> void:
			var w := 128.0
			var h := 160.0
			tp.round(0.0, 0.0, w, h, 8.0, 0xFF30323C)
			tp.round_grad(3.0, 3.0, w - 6.0, h - 6.0, 6.0, 0xFFCDD1DC, 0xFF7A7E8E)
			# Brushed grain, and a darker recess round each mech.
			for y in range(6, 154, 3):
				tp.rect(6.0, float(y), w - 12.0, 1.0, HallArt.alpha(0xFFFFFFFF, 0.07))
			for k in 2:
				var x := SLOT_X0 * w + k * SLOT_DX * w
				tp.round(x - 22.0, 12.0, 44.0, 118.0, 6.0, HallArt.alpha(0xFF000000, 0.28))
				# The slot plate: a chrome surround round the lit window.
				tp.round_grad(x - 19.0, 15.0, 38.0, 54.0, 5.0, 0xFFF2F4FA, 0xFF8A8E9E)
				tp.round(x - 15.0, 21.0, 30.0, 42.0, 3.0, 0xFF20090B)
				tp.text("25", x, 79.0, 10.0, 0xFF22242C, Fonts.condensed())
				# The return flap and its slot.
				tp.round(x - 10.0, 87.0, 20.0, 26.0, 4.0, 0xFF3A3C46)
				tp.round(x - 8.0, 89.0, 16.0, 22.0, 3.0, 0xFF16171D)
				tp.round(x - 5.0, 92.0, 10.0, 3.0, 1.5, 0xFF050508)
				tp.circle(x, 122.0, 4.6, 0xFF7A1010)
				tp.circle(x - 0.8, 121.2, 3.4, 0xFFD02828)
			# Keyed lock between the mechs, near the bottom.
			tp.circle(w / 2.0, 128.0, 7.0, 0xFF3A3C46)
			tp.circle(w / 2.0, 128.0, 5.4, 0xFFA8ACBA)
			tp.round(w / 2.0 - 1.2, 124.0, 2.4, 8.0, 1.2, 0xFF20222A)
			tp.text("TOKENS ONLY", w / 2.0, 150.0, 8.0, 0xFF2A2C34, Fonts.condensed())
			for sx: float in [9.0, w - 9.0]:
				for sy: float in [9.0, h - 9.0]:
					CabinetPaint.screw(tp, sx, sy, 2.6))
	return _coin_door


## A coin slot's lit window: red glow round a black slit and a coin outline. Emissive quad, black
## stays black.
static func coin_slot_lit() -> PaTexture:
	if _coin_slot_lit == null:
		_coin_slot_lit = TexPaint.paint_texture(30, 42, CabinetPaint.PLATE_SCALE, func(tp: TexPaint) -> void:
			tp.vgrad(0.0, 0.0, 30.0, 42.0, [0xFFFF5040, 0xFFB01818])
			tp.radial(15.0, 20.0, 20.0, HallArt.alpha(0xFFFFB0A0, 0.7), 0)
			tp.ring(15.0, 12.0, 6.0, 1.3, HallArt.alpha(0xFF400808, 0.8))
			tp.round(13.2, 17.0, 3.6, 20.0, 1.8, 0xFF120303)
			tp.vgrad(0.0, 0.0, 30.0, 14.0, [HallArt.alpha(0xFFFFFFFF, 0.25), 0]))
	return _coin_slot_lit


## A long speaker panel (painted at 4x): a dark plate of rows of slots between chrome screws, for
## backboxes and fascias.
static func speaker_strip() -> PaTexture:
	if _speaker_strip == null:
		_speaker_strip = TexPaint.paint_texture(128, 16, CabinetPaint.PLATE_SCALE, func(tp: TexPaint) -> void:
			tp.round_grad(0.0, 0.0, 128.0, 16.0, 3.0, 0xFF2C2A34, 0xFF121016)
			for row in 3:
				for k in 26:
					tp.round(9.0 + k * 4.4, 3.0 + row * 4.0, 3.2, 1.8, 0.9, 0xFF050408)
			tp.stroke_round(0.8, 0.8, 126.4, 14.4, 3.0, 0.9, 0xFF6E7282)
			for sx: float in [4.5, 123.5]:
				CabinetPaint.screw(tp, sx, 8.0, 1.5))
	return _speaker_strip


## A ticket dispenser's front plate: dark housing, a lit slot, "TICKETS" above it and chevrons
## pointing down.
static func ticket_plate() -> PaTexture:
	if _ticket_plate == null:
		_ticket_plate = TexPaint.paint_texture(64, 32, CabinetPaint.PLATE_SCALE, func(tp: TexPaint) -> void:
			tp.round_grad(0.0, 0.0, 64.0, 32.0, 4.0, 0xFF3A3C48, 0xFF1A1B22)
			tp.stroke_round(1.0, 1.0, 62.0, 30.0, 3.5, 1.2, 0xFF8A8E9E)
			tp.text("TICKETS", 32.0, 10.0, 7.5, 0xFFFFD84D, Fonts.condensed())
			tp.round(9.0, 14.0, 46.0, 7.5, 3.5, 0xFF050508)
			tp.round(10.5, 15.5, 43.0, 2.5, 1.2, 0xFFFF5A3C)
			for k in 3:
				tp.polygon(PackedFloat32Array([22.0 + k * 9.0, 25.0, 26.0 + k * 9.0, 25.0, 24.0 + k * 9.0, 28.4]), HallArt.alpha(0xFFFFD84D, 0.8))
			for sx: float in [4.0, 60.0]:
				CabinetPaint.screw(tp, sx, 4.0, 1.4)
				CabinetPaint.screw(tp, sx, 28.0, 1.4))
	return _ticket_plate


## A ticket sticking out of a dispenser: cream paper, red end stripes, a perforation and tiny print.
static func ticket_paper() -> PaTexture:
	if _ticket_paper == null:
		_ticket_paper = TexPaint.paint_texture(16, 32, CabinetPaint.PLATE_SCALE, func(tp: TexPaint) -> void:
			tp.fill(0xFFF0E6C8)
			tp.rect(0.0, 0.0, 16.0, 3.0, 0xFFD8323C)
			tp.rect(0.0, 29.0, 16.0, 3.0, 0xFFD8323C)
			for k in 7:
				tp.circle(2.0 + k * 2.0, 23.0, 0.5, 0xFFB8AC90)
			tp.text("ADMIT", 8.0, 12.0, 3.6, 0xFF8A2A30, Fonts.condensed())
			tp.text("ONE", 8.0, 17.0, 3.6, 0xFF8A2A30, Fonts.condensed())
			tp.hgrad(0.0, 0.0, 3.0, 32.0, [HallArt.alpha(0xFF000000, 0.12), 0]))
	return _ticket_paper


## Random prize-wall and claw-pile arrangement helper.
static func jitter(i: int, salt: int, span: float) -> float:
	return (MathUtil.hash01(i, salt) - 0.5) * 2.0 * span
