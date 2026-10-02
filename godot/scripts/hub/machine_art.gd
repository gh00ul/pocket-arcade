class_name MachineArt
extends RefCounted
## hub/MachineArt.kt: the printed and lit artwork for one game's cabinets: side art, marquee, kick
## panel, control panel and an LED score display, all in the game's colours. Kotlin's lazy texture
## properties are methods here (`art.side_art().full()`), painted on first call; [member body],
## [member trim] and [member glow] are the CabinetLook's colours.
##
## The file's other pieces: [LiveScreen] (its own file: cabinet designs make them), the overlays
## Kotlin's ScreenGlass and DotMatrix painted (here [method screen_glass] and [method dot_matrix],
## computed once per size as straight-alpha images) and the TexPaint extension `emblem`
## ([method emblem], the painter first).

var game: MiniGame
var body: int
var trim: int
var glow: int
var _shape: int

var _body_paint: PaTexture = null
var _dark_paint: PaTexture = null
var _black: PaTexture = null
var _trim_tex: PaTexture = null
var _glow_tex: PaTexture = null
var _side_art: PaTexture = null
var _side_art_square: PaTexture = null
var _side_art_long: PaTexture = null
var _marquee: PaTexture = null
var _topper: PaTexture = null
var _kick: PaTexture = null
var _panel: PaTexture = null
var _panel_big: PaTexture = null
var _bezel: PaTexture = null

var _display_text := ""
var _display_best := -1
var _display_paint: TexPaint = null
var _display: PaTexture = null

static var _glass_overlays := {}
static var _dot_masks := {}


func _init(p_game: MiniGame) -> void:
	game = p_game
	body = game.look.body
	trim = game.look.trim
	glow = game.look.glow
	_shape = game.look.shape


func body_paint() -> PaTexture:
	if _body_paint == null:
		_body_paint = HallArt.paint(HallArt.dim(body, 0.85))
	return _body_paint


func dark_paint() -> PaTexture:
	if _dark_paint == null:
		_dark_paint = HallArt.paint(HallArt.dim(body, 0.3), 0.05, 0.7)
	return _dark_paint


func black() -> PaTexture:
	if _black == null:
		_black = HallArt.paint(0xFF15131C, 0.06, 0.8)
	return _black


func trim_tex() -> PaTexture:
	if _trim_tex == null:
		_trim_tex = HallArt.solid(trim)
	return _trim_tex


func glow_tex() -> PaTexture:
	if _glow_tex == null:
		_glow_tex = HallArt.solid(glow)
	return _glow_tex


## Side panel art for a tall face (upright sides, backboards): speed stripes, a ringed emblem badge
## over the title, a pinstripe frame with rivets, grime and scuffs at the foot. See
## CabinetPaint.side_art; [method side_art_square] and [method side_art_long] are the same print laid
## out for squarish skirts and long rails, so nothing is squashed onto a face of the wrong shape.
func side_art() -> PaTexture:
	if _side_art == null:
		_side_art = CabinetPaint.side_art(CabinetPaint.SIDE_TALL_W, CabinetPaint.SIDE_TALL_H, body, trim, glow, _shape, game.title)
	return _side_art


func side_art_square() -> PaTexture:
	if _side_art_square == null:
		_side_art_square = CabinetPaint.side_art(CabinetPaint.SIDE_SQUARE, CabinetPaint.SIDE_SQUARE, body, trim, glow, _shape, game.title)
	return _side_art_square


func side_art_long() -> PaTexture:
	if _side_art_long == null:
		_side_art_long = CabinetPaint.side_art(CabinetPaint.SIDE_LONG_W, CabinetPaint.SIDE_LONG_H, body, trim, glow, _shape, game.title)
	return _side_art_long


## The lit sign on top of the cabinet: backlit acrylic with the emblem, title, neon keyline and
## chrome frame.
func marquee() -> PaTexture:
	if _marquee == null:
		_marquee = CabinetPaint.marquee(body, trim, glow, _shape, game.title)
	return _marquee


## The top of the marquee, which is what the hall camera sees most of.
func topper() -> PaTexture:
	if _topper == null:
		_topper = CabinetPaint.topper(body, trim, glow, _shape, game.marquee)
	return _topper


## Kick panel: recessed speaker grille, chevron stripe, printed emblems, toe strip and scuffs.
func kick() -> PaTexture:
	if _kick == null:
		_kick = CabinetPaint.kick(body, trim, glow, _shape)
	return _kick


## The face of the coin door, the same steel for every machine (its slots light up separately).
func coin_door() -> PaTexture:
	return MachineKit.coin_door()


func _how_to() -> String:
	return game.instructions[0] if game.instructions.size() > 0 else game.title


## The control panel's printing: a joystick well, button rings, a stripe and the how-to line.
func panel() -> PaTexture:
	if _panel == null:
		_panel = CabinetPaint.panel(body, trim, glow, _how_to())
	return _panel


## The tower's panel, with one big button ring between two small ones.
func panel_big() -> PaTexture:
	if _panel_big == null:
		_panel_big = CabinetPaint.panel_big(body, trim, glow, _how_to())
	return _panel_big


## Screen bezel: glossy black with a printed neon frame, corner flashes and the game's name.
func bezel() -> PaTexture:
	if _bezel == null:
		_bezel = CabinetPaint.bezel(trim, glow, game.marquee)
	return _bezel


# ------------------------------------------------------------------ live displays

## A red LED score display showing the machine's best score and a "PLAY" call (256 × 64; its
## picture arrives with the first [method update_display]).
func display() -> PaTexture:
	if _display == null:
		_display = PaTexture.new(256, 64)
	return _display


func update_display(best: int, t: float) -> void:
	# Called every frame for every copy on screen: only paint when the text changes.
	var hi := fmod(t, 6.0) < 3.0
	if hi and best == _display_best and _display_text.begins_with("HI"):
		return
	if not hi and _display_text == "PLAY!":
		return
	var text := "HI %d" % best if hi else "PLAY!"
	_display_text = text
	_display_best = best
	if _display_paint == null:
		_display_paint = TexPaint.new(256, 64)
	var tp := _display_paint
	tp.clear(0)
	tp.fill(0xFF0A0404)
	tp.glow_text(text, 128.0, 46.0, 42.0, 0xFFFF5A3C, 0xFFFF2010, 6.0, Fonts.condensed(), 0.12)
	# The LED matrix: dark gaps between the dots, one draw of a pre-painted grid.
	tp.image(dot_matrix(tp.w, tp.h), 0.0, 0.0)
	tp.update(display())


## The text the display shows now ("HI n" or "PLAY!"), for tests.
func display_text() -> String:
	return _display_text


## Screen size in painter units: the game's own cabinet design's, else by shape (portrait for the
## tower). Null (and a pushed error, Kotlin's `error`) for a design without a live screen.
func screen_units() -> Variant:
	var design := game.cabinet() as CabinetDesign
	if design != null:
		if design.screen_units == null:
			push_error("%s's cabinet has no live screen" % game.id)
		return design.screen_units
	if game.look.shape == MiniGame.CabinetShape.TOWER:
		return Vector2i(16, 24)
	return Vector2i(24, 18)


# ------------------------------------------------------------------ shared overlays

## hub/MachineArt.kt ScreenGlass: the CRT scanlines and the soft sheen of the glass over a live
## screen, made once for each screen size and shared by every screen of that size, and laid over the
## game's picture in a single draw. Every third pixel row gets a dark line (0x33000000), a pale
## radial sheen near the top left (0x22FFFFFF), the corners fall away (0x5C000000 at the far
## corners) and the top catches the room (0x1CFFFFFF fading over the top 14%). Kotlin painted it on
## a Canvas; here the same layers are composited in straight alpha (Android interpolates gradient
## colours unpremultiplied, as here).
static func screen_glass(w: int, h: int) -> Texture2D:
	var key := Vector2i(w, h)
	var tex: Texture2D = _glass_overlays.get(key)
	if tex != null:
		return tex
	var data := PackedByteArray()
	data.resize(w * h * 4)
	var sheen_x := w * 0.3
	var sheen_y := h * 0.2
	var sheen_r := w * 0.5
	var vig_r := maxf(w, h) * 0.78
	var top_h := h * 0.14
	for y in h:
		for x in w:
			var px := x + 0.5
			var py := y + 0.5
			var r := 0.0
			var g := 0.0
			var b := 0.0
			var a := 0.0
			# Scanline.
			if y % 3 == 0:
				var la := 0x33 / 255.0
				a = la
			# Sheen: white to transparent black from the centre out.
			var ds := Vector2(px - sheen_x, py - sheen_y).length()
			if ds < sheen_r:
				var t := ds / sheen_r
				var sa := (0x22 / 255.0) * (1.0 - t)
				var sc := 1.0 - t
				var na := sa + a * (1.0 - sa)
				if na > 0.0:
					r = (sc * sa + r * a * (1.0 - sa)) / na
					g = (sc * sa + g * a * (1.0 - sa)) / na
					b = (sc * sa + b * a * (1.0 - sa)) / na
				a = na
			# Vignette: transparent to black from the middle out.
			var dv := Vector2(px - w / 2.0, py - h / 2.0).length()
			if dv < vig_r:
				var t := dv / vig_r
				var va := (0x5C / 255.0) * t
				var na := va + a * (1.0 - va)
				if na > 0.0:
					r = (r * a * (1.0 - va)) / na
					g = (g * a * (1.0 - va)) / na
					b = (b * a * (1.0 - va)) / na
				a = na
			# Top light: white to transparent black down the top band.
			if py < top_h:
				var t := py / top_h
				var ta := (0x1C / 255.0) * (1.0 - t)
				var tc := 1.0 - t
				var na := ta + a * (1.0 - ta)
				if na > 0.0:
					r = (tc * ta + r * a * (1.0 - ta)) / na
					g = (tc * ta + g * a * (1.0 - ta)) / na
					b = (tc * ta + b * a * (1.0 - ta)) / na
				a = na
			var o := (y * w + x) * 4
			data[o] = clampi(int(r * 255.0 + 0.5), 0, 255)
			data[o + 1] = clampi(int(g * 255.0 + 0.5), 0, 255)
			data[o + 2] = clampi(int(b * 255.0 + 0.5), 0, 255)
			data[o + 3] = clampi(int(a * 255.0 + 0.5), 0, 255)
	tex = ImageTexture.create_from_image(Image.create_from_data(w, h, false, Image.FORMAT_RGBA8, data))
	_glass_overlays[key] = tex
	return tex


## hub/MachineArt.kt DotMatrix: the dark gaps that make a flat LED display read as a matrix of dots
## (a 0x88000000 line every 4 pixels both ways), made once per size.
static func dot_matrix(w: int, h: int) -> Texture2D:
	var key := Vector2i(w, h)
	var tex: Texture2D = _dot_masks.get(key)
	if tex != null:
		return tex
	var data := PackedByteArray()
	data.resize(w * h * 4)
	var one := 0x88 / 255.0
	var both := one + one * (1.0 - one)
	for y in h:
		for x in w:
			var on_x := x % 4 == 0
			var on_y := y % 4 == 0
			var a := 0.0
			if on_x and on_y:
				a = both
			elif on_x or on_y:
				a = one
			data[(y * w + x) * 4 + 3] = clampi(int(a * 255.0 + 0.5), 0, 255)
	tex = ImageTexture.create_from_image(Image.create_from_data(w, h, false, Image.FORMAT_RGBA8, data))
	_dot_masks[key] = tex
	return tex


# ------------------------------------------------------------------ emblems

## Paints a machine's emblem in a ringed badge of radius [param r] at ([param cx], [param cy]): a
## claw over a plush, a mole in its hole, skee rings and a ball, a basketball under a rim, a coin
## stack, a mallet and puck, a block tower, a chequered flag, a crosshair, a pinball and flipper, a
## fish. Used on marquees, side art, toppers and kick panels. [param shape] is a
## MiniGame.CabinetShape.
static func emblem(tp: TexPaint, shape: int, cx: float, cy: float, r: float, body: int, trim: int, glow: int) -> void:
	var S := MiniGame.CabinetShape
	var ink := 0xFF120E18
	var chrome := 0xFFDCE0EA
	tp.circle(cx, cy, r, HallArt.dim(body, 0.32))
	tp.radial(cx, cy - r * 0.2, r, HallArt.alpha(HallArt.lift(glow, 0.4), 0.55), 0)
	tp.ring(cx, cy, r * 0.94, r * 0.1, trim)
	tp.ring(cx, cy, r * 0.8, r * 0.03, HallArt.alpha(HallArt.lift(glow, 0.5), 0.8))
	var s := r * 0.8
	if shape == S.CLAW or shape == S.WIDE:
		tp.ball(cx + s * 0.1, cy + s * 0.55, s * 0.3, 0xFFFF6FB0)
		tp.circle(cx + s * 0.02, cy + s * 0.48, s * 0.05, ink)
		tp.circle(cx + s * 0.2, cy + s * 0.48, s * 0.05, ink)
		tp.line(cx, cy - s, cx, cy - s * 0.2, s * 0.08, chrome)
		tp.circle(cx, cy - s * 0.2, s * 0.17, chrome)
		for k in range(-1, 2):
			var ex := cx + k * s * 0.42
			tp.line(cx, cy - s * 0.15, ex, cy + s * 0.2, s * 0.08, chrome)
			tp.line(ex, cy + s * 0.2, cx + k * s * 0.3, cy + s * 0.4, s * 0.08, chrome)
	elif shape == S.WHACK:
		tp.oval(cx, cy + s * 0.5, s * 0.72, s * 0.2, ink)
		tp.oval(cx, cy + s * 0.05, s * 0.4, s * 0.48, 0xFF9C6A38)
		tp.oval(cx, cy + s * 0.18, s * 0.24, s * 0.2, 0xFFD9A066)
		tp.circle(cx - s * 0.14, cy - s * 0.1, s * 0.07, ink)
		tp.circle(cx + s * 0.14, cy - s * 0.1, s * 0.07, ink)
		tp.oval(cx, cy + s * 0.08, s * 0.09, s * 0.06, 0xFFFF6FA0)
		tp.oval(cx, cy + s * 0.56, s * 0.72, s * 0.14, HallArt.dim(body, 0.32))
		tp.line(cx + s * 0.25, cy - s * 0.25, cx + s * 0.8, cy - s * 0.75, s * 0.1, 0xFFC9884A)
		tp.round(cx + s * 0.02, cy - s * 0.98, s * 0.5, s * 0.36, s * 0.08, 0xFFE8323C)
	elif shape == S.SKEEBALL or shape == S.LANE:
		var colors := [0xFF4DA6FF, 0xFFFF3FA4, 0xFFFFE14D]
		for k in 3:
			tp.circle(cx, cy - s * 0.1, s * (0.7 - k * 0.22), HallArt.dim(colors[k], 0.55))
			tp.ring(cx, cy - s * 0.1, s * (0.7 - k * 0.22), s * 0.07, colors[k])
		tp.circle(cx, cy - s * 0.1, s * 0.1, ink)
		tp.ball(cx + s * 0.5, cy + s * 0.5, s * 0.24, 0xFFB0213A)
	elif shape == S.HOOPS:
		tp.round(cx - s * 0.62, cy - s * 0.98, s * 1.24, s * 0.62, s * 0.08, 0xFFF4F8FF)
		tp.stroke_round(cx - s * 0.24, cy - s * 0.78, s * 0.48, s * 0.36, s * 0.04, s * 0.06, 0xFFE8323C)
		tp.ball(cx, cy + s * 0.28, s * 0.5, 0xFFE8782A, 0.35)
		tp.line(cx - s * 0.5, cy + s * 0.28, cx + s * 0.5, cy + s * 0.28, s * 0.05, 0xFF3A1A08)
		tp.line(cx, cy - s * 0.22, cx, cy + s * 0.78, s * 0.05, 0xFF3A1A08)
		tp.line(cx - s * 0.45, cy - s * 0.42, cx + s * 0.45, cy - s * 0.42, s * 0.1, 0xFFFF7A1A)
	elif shape == S.PUSHER:
		for k in 4:
			var y := cy + s * 0.5 - k * s * 0.2
			tp.oval(cx - s * 0.2, y + s * 0.05, s * 0.46, s * 0.16, 0xFFB07818)
			tp.oval(cx - s * 0.2, y, s * 0.46, s * 0.16, 0xFFFFD04A)
		tp.circle(cx + s * 0.42, cy + s * 0.2, s * 0.3, 0xFFE0A020)
		tp.circle(cx + s * 0.42, cy + s * 0.2, s * 0.24, 0xFFFFD04A)
		tp.star(cx + s * 0.42, cy + s * 0.2, s * 0.16, 0xFFE0A020)
	elif shape == S.AIR_HOCKEY or shape == S.TABLE:
		for k in 3:
			tp.line(cx + s * 0.05, cy + s * (0.1 + k * 0.14), cx + s * 0.5, cy + s * (0.1 + k * 0.14), s * 0.05, HallArt.alpha(0xFFFFFFFF, 0.6))
		tp.oval(cx + s * 0.45, cy + s * 0.32, s * 0.26, s * 0.12, ink)
		tp.oval(cx + s * 0.45, cy + s * 0.28, s * 0.26, s * 0.12, 0xFF2A2A34)
		tp.circle(cx - s * 0.25, cy, s * 0.46, 0xFFB01A24)
		tp.circle(cx - s * 0.25, cy - s * 0.05, s * 0.44, 0xFFE8323C)
		tp.circle(cx - s * 0.25, cy - s * 0.1, s * 0.2, 0xFFFF7A7A)
	elif shape == S.TOWER or shape == S.UPRIGHT:
		var colors := [glow, trim, 0xFF39E6F2, 0xFFFF4FA8, 0xFFFFD84D]
		for k in 5:
			var bw := s * (1.1 - k * 0.18)
			var y := cy + s * 0.62 - k * s * 0.3
			tp.round(cx - bw / 2.0 + (k % 2) * s * 0.06, y - s * 0.26, bw, s * 0.26, s * 0.04, colors[k])
			tp.rect(cx - bw / 2.0 + (k % 2) * s * 0.06, y - s * 0.26, bw, s * 0.05, HallArt.alpha(0xFFFFFFFF, 0.4))
	elif shape == S.RACER:
		tp.line(cx - s * 0.62, cy - s * 0.62, cx - s * 0.62, cy + s * 0.85, s * 0.08, chrome)
		var n := 5
		var q := s * 1.2 / n
		for i in n:
			for j in 4:
				var wave := sin(i * 1.1) * s * 0.08
				tp.rect(cx - s * 0.58 + i * q, cy - s * 0.62 + j * q + wave, q + 0.5, q + 0.5, 0xFFFFFFFF if (i + j) % 2 == 0 else ink)
	elif shape == S.GUN:
		tp.ring(cx, cy, s * 0.6, s * 0.09, 0xFFFF3B30)
		tp.ring(cx, cy, s * 0.3, s * 0.05, 0xFFFF3B30)
		for k in 4:
			var dx := (1.0 if k == 0 else -1.0) if k < 2 else 0.0
			var dy := (1.0 if k == 2 else -1.0) if k >= 2 else 0.0
			tp.line(cx + dx * s * 0.4, cy + dy * s * 0.4, cx + dx * s * 0.9, cy + dy * s * 0.9, s * 0.09, 0xFFFFFFFF)
		tp.circle(cx, cy, s * 0.08, 0xFFFF3B30)
	elif shape == S.PINBALL:
		tp.star(cx - s * 0.3, cy - s * 0.45, s * 0.3, 0xFFFFE14D)
		tp.ball(cx + s * 0.2, cy - s * 0.15, s * 0.32, 0xFFD8DCE8, 0.6)
		tp.line(cx - s * 0.62, cy + s * 0.35, cx + s * 0.18, cy + s * 0.62, s * 0.26, 0xFFE8323C)
		tp.line(cx - s * 0.6, cy + s * 0.33, cx + s * 0.16, cy + s * 0.58, s * 0.17, 0xFFFFFFFF)
		tp.circle(cx - s * 0.6, cy + s * 0.33, s * 0.06, 0xFFE8323C)
	elif shape == S.FISHING:
		for k in 2:
			tp.line(cx - s * 0.8, cy + s * (0.62 + k * 0.18), cx + s * 0.8, cy + s * (0.62 + k * 0.18), s * 0.06, HallArt.alpha(0xFF7AD8FF, 0.8))
		tp.polygon(PackedFloat32Array([cx + s * 0.35, cy, cx + s * 0.82, cy - s * 0.32, cx + s * 0.82, cy + s * 0.32]), 0xFFFF8A3D)
		tp.oval(cx - s * 0.08, cy, s * 0.52, s * 0.3, 0xFFFF9A3C)
		tp.oval(cx - s * 0.08, cy + s * 0.08, s * 0.42, s * 0.16, 0xFFFFD08A)
		tp.circle(cx - s * 0.34, cy - s * 0.06, s * 0.08, 0xFFFFFFFF)
		tp.circle(cx - s * 0.36, cy - s * 0.06, s * 0.045, ink)
		tp.circle(cx - s * 0.3, cy - s * 0.62, s * 0.08, HallArt.alpha(0xFFFFFFFF, 0.7))
		tp.circle(cx - s * 0.12, cy - s * 0.78, s * 0.05, HallArt.alpha(0xFFFFFFFF, 0.7))
