class_name CabinetPaint
extends RefCounted
## hub/CabinetPaint.kt: the printed artwork of a cabinet: side art, marquee, topper, kick plate,
## control panel and bezel, painted at a finer resolution than the texel size the models map them in
## (see [method TexPaint.paint_texture]) so decals stay crisp when a kid stands a step away from the
## glass. Everything is painted once per game (the shared hardware once for all) from the game's
## CabinetLook colours.
##
## Painting needs the 2D renderer (headless runs record it but get no pixels); the numbers that
## decide how it looks are the named constants below. Kotlin's TexPaint extensions `screw` and
## `wear` are static functions taking the painter first.

## Painting scale (pixels per texel) for the big printed panels: side art, marquee, kick.
const PANEL_SCALE := 2
## Painting scale for small, detailed hardware plates (coin door, ticket plate, control panel).
const PLATE_SCALE := 4

## Side art sizes in texels: the tall panel, a squarish one for skirts and a long strip for rails.
const SIDE_TALL_W := 192
const SIDE_TALL_H := 384
const SIDE_SQUARE := 192
const SIDE_LONG_W := 384
const SIDE_LONG_H := 96

## Marquee and topper sizes in texels.
const MARQUEE_W := 384
const MARQUEE_H := 120
const TOPPER_W := 192
const TOPPER_H := 144

## How much the paint darkens towards the foot of a printed panel (floor grime), 0..1.
const GRIME := 0.34
## How many light scuffs are scratched into the lowest part of a panel.
const SCUFFS := 16

const CHROME := 0xFFC4C8D4
const CHROME_DARK := 0xFF6E7282
const INK := 0xFF120E18


## The biggest text size (<= [param max_size]) at which every one of [param lines] fits in
## [param width].
static func fit_size(tp: TexPaint, lines: Array, width: float, max_size: float) -> float:
	var size := max_size
	for s: String in lines:
		var wd := tp.text_width(s, max_size, Fonts.display(), 0.02)
		if wd > width:
			size = minf(size, max_size * width / wd)
	return size


## A slotted chrome screw head.
static func screw(tp: TexPaint, x: float, y: float, r: float, angle: float = 0.6) -> void:
	tp.circle(x, y + r * 0.25, r * 1.05, HallArt.alpha(INK, 0.5))
	tp.circle(x, y, r, CHROME_DARK)
	tp.circle(x - r * 0.12, y - r * 0.12, r * 0.82, CHROME)
	tp.line(x - cos(angle) * r * 0.7, y - sin(angle) * r * 0.7, x + cos(angle) * r * 0.7, y + sin(angle) * r * 0.7, r * 0.28, 0xFF3A3C48, false)


## Light scratches and scuffs over the foot of a panel, so it looks handled, not printed yesterday.
static func wear(tp: TexPaint, w: float, h: float, seed_v: int) -> void:
	tp.vgrad(0.0, h * 0.68, w, h * 0.32, [0, HallArt.alpha(0xFF000000, GRIME)])
	for i in SCUFFS:
		var x := MathUtil.hash01(i, seed_v) * w
		var y := h * (0.72 + MathUtil.hash01(i, seed_v + 1) * 0.26)
		var length := 3.0 + MathUtil.hash01(i, seed_v + 2) * 9.0
		var a := (MathUtil.hash01(i, seed_v + 3) - 0.5) * 1.2
		tp.line(x, y, x + cos(a) * length, y + sin(a) * length * 0.4, 0.5 + MathUtil.hash01(i, seed_v + 4), HallArt.alpha(0xFFFFFFFF, 0.10 + MathUtil.hash01(i, seed_v + 5) * 0.08))


# ------------------------------------------------------------------ side art

## The printed side of a cabinet. [param w] × [param h] picks the layout: a tall panel (uprights,
## backboards) stacks the badge over the title, a squarish one (skirts) centres the badge, a long
## strip (rails) puts the badge at one end with the title beside it. Bold speed stripes with drop
## shadows and a highlight line sweep across, a pinstripe frame with rivets runs round the edge and
## the foot is grimy and scuffed. [param shape] is a MiniGame.CabinetShape.
static func side_art(w: int, h: int, body: int, trim: int, glow: int, shape: int, title: String) -> PaTexture:
	return TexPaint.paint_texture(w, h, PANEL_SCALE, func(tp: TexPaint) -> void:
		var fw := float(w)
		var fh := float(h)
		var long := w > h * 2
		var tall := h > w * 1.4
		tp.vgrad(0.0, 0.0, fw, fh, [HallArt.lift(body, 0.22), body, HallArt.dim(body, 0.48)])
		# Halftone dots fading out of the top corner.
		var dots := 6 if long else 10
		for y in dots:
			for x in dots + 2:
				var d := (x + y) / (dots * 1.6)
				if d > 1.0:
					continue
				var step := minf(fw, fh) / 12.0
				tp.circle(fw - step * 0.6 - x * step + (y % 2) * step * 0.5, step * 0.7 + y * step, step * 0.4 * (1.0 - d), HallArt.alpha(HallArt.lift(glow, 0.5), 0.32))
		# Speed stripes sweeping across the panel, each with a shadow edge and a bright top line.
		var rise := fh * 0.9 if long else fw * 0.95
		var bands := 3 if long else 4
		for k in bands:
			var y := fh * (0.95 - k * 0.3) if long else fh * (0.92 - k * 0.115)
			var c: int
			if k % 3 == 0:
				c = glow
			elif k % 3 == 1:
				c = trim
			else:
				c = HallArt.lift(body, 0.45)
			var t := (9.0 if long else 13.0) - k * 1.2
			var dx := fw
			tp.polygon(PackedFloat32Array([0.0, y + 4.0, dx, y - rise + 4.0, dx, y - rise + t + 4.0, 0.0, y + t + 4.0]), HallArt.alpha(0xFF000000, 0.35))
			tp.polygon(PackedFloat32Array([0.0, y, dx, y - rise, dx, y - rise + t, 0.0, y + t]), c)
			tp.polygon(PackedFloat32Array([0.0, y, dx, y - rise, dx, y - rise + 1.4, 0.0, y + 1.4]), HallArt.alpha(0xFFFFFFFF, 0.5))
			tp.polygon(PackedFloat32Array([0.0, y + t - 1.2, dx, y - rise + t - 1.2, dx, y - rise + t, 0.0, y + t]), HallArt.alpha(0xFF000000, 0.25))
		# The badge: a burst behind the emblem, on a dark ground.
		var badge_r := fh * 0.42 if long else fw * 0.34
		var bx := fh * 0.62 if long else fw * 0.5
		var by: float
		if long:
			by = fh * 0.5
		elif tall:
			by = fh * 0.3
		else:
			by = fh * 0.4
		for k in 18:
			var a := k * PI / 9.0
			var rr := badge_r * 2.3
			tp.polygon(PackedFloat32Array([bx, by, bx + cos(a) * rr, by + sin(a) * rr, bx + cos(a + 0.17) * rr, by + sin(a + 0.17) * rr]), HallArt.alpha(HallArt.lift(glow, 0.3), 0.2))
		tp.circle(bx, by + badge_r * 0.08, badge_r * 1.08, HallArt.alpha(0xFF000000, 0.4))
		MachineArt.emblem(tp, shape, bx, by, badge_r, body, trim, glow)
		# The title: stacked one word a line on a tall panel, in one line beside the badge on a strip.
		var words := title.split(" ")
		if long:
			var size := fit_size(tp, [title], fw - bx - badge_r - 36.0, fh * 0.42)
			var tx := bx + badge_r + 14.0 + (fw - bx - badge_r - 26.0) / 2.0
			tp.outlined_text(title, tx, fh * 0.5 + size * 0.34, size, HallArt.lift(glow, 0.65), HallArt.dim(body, 0.18), size * 0.16, Fonts.display())
		else:
			var size := fit_size(tp, Array(words), fw * 0.86, fw * 0.3)
			for i in words.size():
				var y := fh - fh * 0.075 - (words.size() - 1 - i) * size * 0.95
				tp.outlined_text(words[i], fw / 2.0, y, size, HallArt.lift(glow, 0.65), HallArt.dim(body, 0.18), size * 0.16, Fonts.display())
		# A pinstripe frame with rivets in its corners.
		var m := minf(fw, fh) * 0.045 + 3.0
		tp.stroke_round(m, m, fw - 2.0 * m, fh - 2.0 * m, 6.0, 1.4, HallArt.alpha(trim, 0.85))
		tp.stroke_round(m + 3.0, m + 3.0, fw - 2.0 * m - 6.0, fh - 2.0 * m - 6.0, 4.0, 0.6, HallArt.alpha(HallArt.lift(glow, 0.5), 0.7))
		for sx: float in [m, fw - m]:
			for sy: float in [m, fh - m]:
				screw(tp, sx, sy, 2.0)
		wear(tp, fw, fh, title.length() * 7 + w)
		# T-moulding's catch-light down the front edge, its shadow down the back.
		tp.hgrad(0.0, 0.0, 7.0, fh, [HallArt.alpha(0xFFFFFFFF, 0.32), 0])
		tp.hgrad(fw - 7.0, 0.0, 7.0, fh, [0, HallArt.alpha(0xFF000000, 0.32)])
		tp.grain(0.03, 13))


# ------------------------------------------------------------------ marquee and topper

## The lit sign on top of the cabinet, painted as backlit acrylic: a hot glow behind the lettering
## that fades into the corners, a sunburst, the emblem at both ends, the title in glowing outlined
## letters, a neon keyline and a chrome frame with screws.
static func marquee(body: int, trim: int, glow: int, shape: int, title: String) -> PaTexture:
	return TexPaint.paint_texture(MARQUEE_W, MARQUEE_H, PANEL_SCALE, func(tp: TexPaint) -> void:
		var w := float(MARQUEE_W)
		var h := float(MARQUEE_H)
		tp.vgrad(0.0, 0.0, w, h, [HallArt.dim(body, 0.32), HallArt.lift(body, 0.1), HallArt.dim(body, 0.42)])
		for k in 32:
			var a := k * PI / 16.0
			var rr := 330.0
			tp.polygon(PackedFloat32Array([w / 2.0, h / 2.0, w / 2.0 + cos(a) * rr, h / 2.0 + sin(a) * rr, w / 2.0 + cos(a + 0.1) * rr, h / 2.0 + sin(a + 0.1) * rr]), HallArt.alpha(HallArt.lift(glow, 0.4), 0.12))
		tp.radial(w / 2.0, h / 2.0, w * 0.42, HallArt.alpha(HallArt.lift(glow, 0.55), 0.6), 0)
		# Emblems at both ends, each on its own soft halo.
		for ex: float in [52.0, w - 52.0]:
			tp.radial(ex, h / 2.0, 50.0, HallArt.alpha(HallArt.lift(glow, 0.5), 0.4), 0)
			MachineArt.emblem(tp, shape, ex, h / 2.0, 37.0, body, trim, glow)
		# The title: one line, or two for a two-word name, as big as fits between the emblems.
		var words := title.split(" ")
		var lines: Array = Array(words) if words.size() == 2 else [title]
		var size := fit_size(tp, lines, 206.0, 50.0 if lines.size() == 2 else 68.0)
		for i in lines.size():
			var line: String = lines[i]
			var y := h / 2.0 + size * 0.36 + (i - (lines.size() - 1) / 2.0) * size * 0.92
			tp.glow_text(line, w / 2.0, y, size, HallArt.lift(glow, 0.8), glow, 9.0, Fonts.display(), 0.02)
			tp.outlined_text(line, w / 2.0, y + size * 0.05, size, HallArt.alpha(0xFF000000, 0.45), HallArt.dim(body, 0.1), size * 0.16, Fonts.display(), 0.02)
			tp.outlined_text(line, w / 2.0, y, size, HallArt.lift(glow, 0.86), HallArt.dim(body, 0.15), size * 0.12, Fonts.display(), 0.02)
			tp.text(line, w / 2.0, y, size, HallArt.alpha(0xFFFFFFFF, 0.5), Fonts.display(), TexPaint.ALIGN_CENTER, 0.02)
		# Acrylic: a plastic sheen across the top half and fine diagonal glints.
		tp.vgrad(0.0, 0.0, w, h * 0.48, [HallArt.alpha(0xFFFFFFFF, 0.18), HallArt.alpha(0xFFFFFFFF, 0.02)])
		for k in 3:
			var x := 70.0 + k * 120.0
			tp.polygon(PackedFloat32Array([x, 0.0, x + 9.0, 0.0, x - 24.0, h, x - 33.0, h]), HallArt.alpha(0xFFFFFFFF, 0.045))
		# Neon keyline, then a frame: dark reveal, chrome bevel, corner screws.
		tp.glow(4.0, HallArt.alpha(glow, 0.9), func(g: TexPaint) -> void: g.stroke_round(10.0, 10.0, w - 20.0, h - 20.0, 12.0, 3.0, 0xFFFFFFFF))
		tp.stroke_round(10.0, 10.0, w - 20.0, h - 20.0, 12.0, 2.2, HallArt.lift(glow, 0.7))
		tp.stroke_round(2.2, 2.2, w - 4.4, h - 4.4, 9.0, 4.6, CHROME)
		tp.stroke_round(4.4, 4.4, w - 8.8, h - 8.8, 8.0, 1.2, CHROME_DARK)
		tp.rect(0.0, 0.0, w, 2.2, 0xFFF4F6FF)
		tp.rect(0.0, h - 2.2, w, 2.2, 0xFF2A2C34)
		for sx: float in [7.0, w - 7.0]:
			for sy: float in [7.0, h - 7.0]:
				screw(tp, sx, sy, 2.4)
		tp.grain(0.02, 21))


## The top of the marquee, which is what the hall camera sees most of: a backlit topper with a
## pinstripe border, the emblem and the machine's short name.
static func topper(body: int, trim: int, glow: int, shape: int, name: String) -> PaTexture:
	return TexPaint.paint_texture(TOPPER_W, TOPPER_H, PANEL_SCALE, func(tp: TexPaint) -> void:
		var w := float(TOPPER_W)
		var h := float(TOPPER_H)
		tp.vgrad(0.0, 0.0, w, h, [HallArt.dim(body, 0.5), HallArt.dim(body, 0.8)])
		tp.radial(w / 2.0, h / 2.0, w * 0.55, HallArt.alpha(HallArt.lift(glow, 0.3), 0.45), 0)
		for k in 7:
			tp.rect(0.0, 15.0 + k * 18.0, w, 1.5, HallArt.alpha(HallArt.dim(body, 0.3), 0.5))
		tp.round(6.0, 6.0, w - 12.0, h - 12.0, 10.0, HallArt.alpha(0xFF000000, 0.18))
		tp.stroke_round(6.0, 6.0, w - 12.0, h - 12.0, 10.0, 3.6, trim)
		tp.stroke_round(12.5, 12.5, w - 25.0, h - 25.0, 6.0, 1.4, HallArt.alpha(glow, 0.8))
		MachineArt.emblem(tp, shape, 46.0, h / 2.0, 30.0, body, trim, glow)
		var size := fit_size(tp, [name], 106.0, 48.0)
		tp.outlined_text(name, 122.0, h / 2.0 + size * 0.36, size, HallArt.lift(glow, 0.75), HallArt.dim(body, 0.15), size * 0.14, Fonts.display())
		tp.vgrad(0.0, 0.0, w, h * 0.4, [HallArt.alpha(0xFFFFFFFF, 0.14), 0])
		tp.grain(0.02, 23))


# ------------------------------------------------------------------ kick plate

## The kick plate under the coin door: a recessed speaker grille, a racing stripe with chevrons,
## printed emblems either side of where the coin door fits, a rubber toe strip and plenty of scuffs.
static func kick(body: int, trim: int, glow: int, shape: int) -> PaTexture:
	return TexPaint.paint_texture(160, 160, PANEL_SCALE, func(tp: TexPaint) -> void:
		var w := 160.0
		var h := 160.0
		tp.vgrad(0.0, 0.0, w, h, [HallArt.dim(body, 0.6), HallArt.dim(body, 0.26)])
		# Speaker grille: a recessed slot of holes in a chrome ring.
		tp.round(23.0, 9.0, 114.0, 30.0, 7.0, HallArt.alpha(0xFF000000, 0.5))
		tp.round(25.0, 11.0, 110.0, 26.0, 6.0, 0xFF16141C)
		for x in 17:
			for y in 3:
				tp.circle(31.0 + x * 6.4, 17.5 + y * 6.2, 1.45, 0xFF302D3C)
		tp.stroke_round(25.0, 11.0, 110.0, 26.0, 6.0, 1.3, 0xFF7C8090)
		# Racing stripe with chevrons.
		tp.rect(0.0, 52.0, w, 16.0, trim)
		tp.rect(0.0, 52.0, w, 1.8, HallArt.alpha(0xFFFFFFFF, 0.42))
		tp.rect(0.0, 73.0, w, 3.6, glow)
		for k in 8:
			var x := 5.0 + k * 20.0
			tp.polygon(PackedFloat32Array([x, 66.0, x + 7.5, 54.0, x + 15.0, 66.0, x + 11.0, 66.0, x + 7.5, 60.0, x + 4.0, 66.0]), HallArt.dim(body, 0.35))
		MachineArt.emblem(tp, shape, 25.0, 122.0, 17.0, body, trim, glow)
		MachineArt.emblem(tp, shape, w - 25.0, 122.0, 17.0, body, trim, glow)
		tp.text("1 TOKEN PER PLAY", 80.0, 148.0, 8.0, HallArt.alpha(0xFFFFFFFF, 0.7), Fonts.condensed())
		wear(tp, w, h, 41)
		# A rubber toe strip along the floor and screws at the corners.
		tp.rect(0.0, h - 5.0, w, 5.0, 0xFF0E0D12)
		tp.rect(0.0, h - 5.0, w, 0.8, HallArt.alpha(0xFFFFFFFF, 0.14))
		for sx: float in [6.0, w - 6.0]:
			for sy: float in [6.0, h - 12.0]:
				screw(tp, sx, sy, 1.7)
		tp.grain(0.03, 11))


# ------------------------------------------------------------------ control panels

## The control panel's printing for an upright: a joystick well, six button rings in two rows, a
## diagonal stripe of the game's colours, player labels and the how-to line. Button and joystick
## positions match MachineKit.PANEL_STICK_U and MachineKit.PANEL_BUTTON_U.
static func panel(body: int, trim: int, glow: int, how_to: String) -> PaTexture:
	return TexPaint.paint_texture(256, 128, PANEL_SCALE, func(tp: TexPaint) -> void:
		var w := 256.0
		var h := 128.0
		tp.vgrad(0.0, 0.0, w, h, [0xFF2C2A36, 0xFF100E14])
		tp.polygon(PackedFloat32Array([0.0, 76.0, w, 26.0, w, 46.0, 0.0, 96.0]), HallArt.alpha(body, 0.55))
		tp.polygon(PackedFloat32Array([0.0, 96.0, w, 46.0, w, 52.0, 0.0, 102.0]), HallArt.alpha(glow, 0.8))
		tp.rect(0.0, 0.0, w, 6.0, trim)
		tp.rect(0.0, 6.0, w, 1.5, HallArt.alpha(0xFFFFFFFF, 0.3))
		# The joystick's well: a shaded dish with an engraved ring.
		var sx := MachineKit.PANEL_STICK_U * w
		tp.circle(sx, 58.0, 24.0, HallArt.alpha(0xFF000000, 0.45))
		tp.radial(sx, 56.0, 22.0, 0xFF050408, 0xFF22202A)
		tp.ring(sx, 58.0, 24.0, 2.2, HallArt.alpha(trim, 0.85))
		# Six button rings, each ringed in its own colour over a dark well.
		var ring_colors := [glow, trim, HallArt.lift(glow, 0.4)]
		for row in 2:
			for k in 3:
				var bx := (MachineKit.PANEL_BUTTON_U + k * MachineKit.PANEL_BUTTON_STEP_U + row * MachineKit.PANEL_BUTTON_ROW_SHIFT_U) * w
				var by := 42.0 if row == 0 else 80.0
				tp.circle(bx, by + 1.5, 13.0, HallArt.alpha(0xFF000000, 0.4))
				tp.circle(bx, by, 12.0, 0xFF0A090E)
				tp.ring(bx, by, 12.5, 2.4, HallArt.alpha(ring_colors[k], 0.9))
		tp.text("1P", 22.0, 26.0, 12.0, HallArt.alpha(trim, 0.9), Fonts.condensed())
		tp.text("START", w - 30.0, 26.0, 10.0, HallArt.alpha(0xFFFFFFFF, 0.75), Fonts.condensed())
		tp.round(24.0, 106.0, w - 48.0, 15.0, 5.0, HallArt.alpha(0xFF000000, 0.5))
		tp.text(how_to, w / 2.0, 117.5, 11.0, HallArt.alpha(0xFFFFFFFF, 0.85), Fonts.condensed())
		tp.vgrad(0.0, 8.0, w, 34.0, [HallArt.alpha(0xFFFFFFFF, 0.08), 0])
		for sx2: float in [7.0, w - 7.0]:
			for sy: float in [14.0, h - 7.0]:
				screw(tp, sx2, sy, 2.0)
		tp.grain(0.03, 29))


## The tower's panel: one big ringed button in the middle between two small ones.
static func panel_big(body: int, trim: int, glow: int, how_to: String) -> PaTexture:
	return TexPaint.paint_texture(256, 128, PANEL_SCALE, func(tp: TexPaint) -> void:
		var w := 256.0
		var h := 128.0
		tp.vgrad(0.0, 0.0, w, h, [0xFF2C2A36, 0xFF100E14])
		tp.polygon(PackedFloat32Array([0.0, 84.0, w, 34.0, w, 52.0, 0.0, 102.0]), HallArt.alpha(body, 0.55))
		tp.polygon(PackedFloat32Array([0.0, 102.0, w, 52.0, w, 58.0, 0.0, 108.0]), HallArt.alpha(glow, 0.8))
		tp.rect(0.0, 0.0, w, 6.0, trim)
		tp.rect(0.0, 6.0, w, 1.5, HallArt.alpha(0xFFFFFFFF, 0.3))
		tp.circle(w / 2.0, 60.0, 34.0, HallArt.alpha(0xFF000000, 0.4))
		tp.circle(w / 2.0, 58.0, 31.0, 0xFF0A090E)
		tp.ring(w / 2.0, 58.0, 32.0, 3.0, HallArt.alpha(glow, 0.9))
		for s: int in [-1, 1]:
			tp.circle(w / 2.0 + s * 78.0, 62.0, 12.0, 0xFF0A090E)
			tp.ring(w / 2.0 + s * 78.0, 62.0, 12.5, 2.2, HallArt.alpha(trim, 0.9))
		tp.text("DROP", w / 2.0, 24.0, 12.0, HallArt.alpha(0xFFFFFFFF, 0.75), Fonts.condensed())
		tp.round(24.0, 106.0, w - 48.0, 15.0, 5.0, HallArt.alpha(0xFF000000, 0.5))
		tp.text(how_to, w / 2.0, 117.5, 11.0, HallArt.alpha(0xFFFFFFFF, 0.85), Fonts.condensed())
		tp.vgrad(0.0, 8.0, w, 34.0, [HallArt.alpha(0xFFFFFFFF, 0.08), 0])
		for sx2: float in [7.0, w - 7.0]:
			for sy: float in [14.0, h - 7.0]:
				screw(tp, sx2, sy, 2.0)
		tp.grain(0.03, 31))


# ------------------------------------------------------------------ screen bezel

## Screen surround: glossy black with subtle corner flashes and the game's name, "INSERT COIN" above.
static func bezel(trim: int, glow: int, name: String) -> PaTexture:
	return TexPaint.paint_texture(128, 128, 3, func(tp: TexPaint) -> void:
		tp.vgrad(0.0, 0.0, 128.0, 128.0, [0xFF1C1A24, 0xFF08070C])
		tp.glow(2.5, HallArt.alpha(glow, 0.7), func(g: TexPaint) -> void: g.stroke_round(5.0, 5.0, 118.0, 118.0, 9.0, 2.0, 0xFFFFFFFF))
		tp.stroke_round(5.0, 5.0, 118.0, 118.0, 9.0, 1.5, HallArt.lift(glow, 0.5))
		for corner: Vector2 in [Vector2(0, 0), Vector2(128, 0), Vector2(0, 128), Vector2(128, 128)]:
			var x := corner.x
			var y := corner.y
			var sx := 1.0 if x == 0.0 else -1.0
			var sy := 1.0 if y == 0.0 else -1.0
			tp.polygon(PackedFloat32Array([x, y, x + sx * 22.0, y, x, y + sy * 22.0]), trim)
			tp.polygon(PackedFloat32Array([x + sx * 25.0, y, x + sx * 29.0, y, x, y + sy * 29.0, x, y + sy * 25.0]), HallArt.alpha(glow, 0.8))
		tp.text(name, 64.0, 122.0, 8.0, HallArt.alpha(trim, 0.9), Fonts.condensed())
		tp.text("INSERT COIN", 64.0, 11.0, 5.5, HallArt.alpha(0xFFFFFFFF, 0.6), Fonts.condensed())
		tp.vgrad(0.0, 0.0, 128.0, 45.0, [HallArt.alpha(0xFFFFFFFF, 0.08), 0]))
