class_name CabinetBuild
extends CabinetBox
## hub/CabinetDesign.kt CabinetBuild: what a CabinetDesign builds one copy with: the model builder
## [member b], the copy's box (this is a [CabinetBox]) and the shared cabinet pieces every built-in
## cabinet uses (side panels with T-molding, a lit marquee with chase bulbs, LED score display,
## glass, chrome posts, lights, a live screen).
##
## Kotlin's named-argument defaults that depend on the box (`depthFront = z1`, `z = z0`,
## `color = art.glow`, `region = art.darkPaint.full`) are sentinels here: NAN for a coordinate,
## [constant ART_GLOW] for the colour, null for the region.

## How far T-moulding stands proud of the face it edges, and how far it overlaps a panel on each side.
const T_MOLD_OUT := 0.35
const T_MOLD_SIDE := 0.15
## The marquee sign's own light (just under the bloom threshold on its brightest paint).
const MARQUEE_GLOW := 1.2
## The topper on top of the marquee, seen from above.
const TOPPER_GLOW := 0.9
## The lit line along the marquee's top cap and down its ends.
const MARQUEE_TRIM_GLOW := 0.8
## Strip lights under a marquee, along a bezel, in a glass case.
const LED_GLOW := 1.2
## The neon line hugging a screen.
const BEZEL_GLOW := 1.0
## Width of that line.
const BEZEL_LINE := 0.35
## The coin slots' lit windows.
const SLOT_GLOW := 1.5
## Emissive of the lit T-moulding along a cabinet's edges (before MachineKit.glow_for tones it down
## for pale trim).
const TMOLD_GLOW := 0.6
## [method neon_strip]'s default colour: the art's glow colour.
const ART_GLOW := -2

## The builder the copy's model goes into.
var b: ModelBuilder
var _unit: MachineUnit
## Polygon index ranges [start, end) of [member b] that belong to this copy alone (seeded prize piles,
## coins): the hall shares the rest of a bank's model between its copies (see MachineUnit).
var own_ranges: Array[Vector2i] = []
var _own_start := -1


func _init(p_b: ModelBuilder, p_unit: MachineUnit, p_x0: float, p_x1: float, p_z0: float, p_z1: float, p_h: float,
		p_variant: int, p_seed: int, p_art: MachineArt) -> void:
	super(p_x0, p_x1, p_z0, p_z1, p_h, p_variant, p_seed, p_art)
	b = p_b
	_unit = p_unit


## Godot-only: what is added to [member b] between this and [method end_own] varies from copy to
## copy (a seeded prize pile), so it is kept out of the model the copies of a bank share.
func begin_own() -> void:
	_own_start = b.polys.size()


func end_own() -> void:
	if _own_start >= 0 and b.polys.size() > _own_start:
		own_ranges.append(Vector2i(_own_start, b.polys.size()))
	_own_start = -1


## Adds a coloured point light ([param color] is ARGB). Keep to 2 or 3 per cabinet.
func light(x: float, y: float, z: float, color: int, radius: float, intensity: float) -> void:
	_unit.lights.append(PointLight.new(x, y, z, ((color >> 16) & 255) / 255.0, ((color >> 8) & 255) / 255.0, (color & 255) / 255.0, radius, intensity))


## A row of [param count] chase bulbs from [param xa] to [param xb] at height [param y], depth [param z].
func bulb_row(xa: float, xb: float, y: float, z: float, count: int) -> void:
	for k in count:
		_unit.add_bulb(xa + (xb - xa) * (k + 0.5) / count, y, z)


## Side panels with printed art and lit T-moulding down their front edges: the moulding is a raised
## strip in the trim colour, a little wider than the panel and standing [constant T_MOLD_OUT] proud of
## its front, the way the real plastic edging wraps a panel's edge. [param depth_front] NAN: z1.
func side_panels(top: float, depth_front: float = NAN, thick: float = 1.8) -> void:
	if is_nan(depth_front):
		depth_front = z1
	var side := art.side_art().full()
	var inner := art.body_paint().full()
	var dark := art.dark_paint().full()
	CabinetForm.beveled_box(b, x0, 0.0, z0, x0 + thick, top, depth_front, BoxFaces.new(dark, side, inner, dark, dark, 0.0, 0.0, 0.0, 0.35))
	CabinetForm.beveled_box(b, x1 - thick, 0.0, z0, x1, top, depth_front, BoxFaces.new(dark, inner, side, dark, dark, 0.0, 0.0, 0.0, 0.35))
	t_moulding(x0 - T_MOLD_SIDE, x0 + thick + T_MOLD_SIDE, 0.0, top, depth_front)
	t_moulding(x1 - thick - T_MOLD_SIDE, x1 + T_MOLD_SIDE, 0.0, top, depth_front)


## A vertical strip of lit T-moulding from [param xa] to [param xb] and [param ya] to [param yb], its
## back on the face at depth [param z] and standing [constant T_MOLD_OUT] proud. Glossy, in the trim
## colour.
func t_moulding(xa: float, xb: float, ya: float, yb: float, z: float) -> void:
	var t := art.trim_tex().full()
	var e := MachineKit.glow_for(art.trim, TMOLD_GLOW)
	b.box(xa, ya, z - 0.01, xb, yb, z + T_MOLD_OUT, BoxFaces.new(t, t, t, t, null, e, e, 0.0, 0.5))


## The lit marquee sign: its face leans back a little so it catches the eye from the hall's high
## camera while still reading square-on from a kid's eye height in front of it, a backlit topper with
## the game's emblem and short name covers its top (the part of a cabinet the hall view sees most
## of). Round the face runs a frame: a dark cap along the top with a lit trim line and a row of chase
## bulbs, a dark rail along the bottom with an LED strip under it washing the screen below, and lit
## edging down both ends.
func marquee_box(xa: float, xb: float, y0: float, y1: float, za: float, zb: float) -> void:
	var dark := art.dark_paint().full()
	var metal := HallArt.dark_metal().full()
	var hgt := y1 - y0
	# A gentle lean: enough to catch the hall camera, still square-on to a kid in front of it.
	var lean := minf(hgt * 0.22, (zb - za) * 0.45)
	var zt := zb - lean
	var length := sqrt(hgt * hgt + lean * lean)
	b.quad(xa, y1, zt, xb, y1, zt, xb, y0, zb, xa, y0, zb, art.marquee().full(), 0.0, lean / length, hgt / length,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, MARQUEE_GLOW)
	b.quad(xa, y1, za, xb, y1, za, xb, y1, zt, xa, y1, zt, art.topper().full(), 0.0, 1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, TOPPER_GLOW, true, -1, 0.5)
	b.quad(xa, y1, za, xa, y1, zt, xa, y0, zb, xa, y0, za, dark, -1.0, 0.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.35)
	b.quad(xb, y1, zt, xb, y1, za, xb, y0, za, xb, y0, zb, dark, 1.0, 0.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.35)
	b.quad(xb, y1, za, xa, y1, za, xa, y0, za, xb, y0, za, dark, 0.0, 0.0, -1.0)
	b.quad(xa, y0, zb, xb, y0, zb, xb, y0, za, xa, y0, za, dark, 0.0, -1.0, 0.0)
	# The frame: a dark cap over the top edge of the face with a lit trim line on its front...
	var trim := art.trim_tex().full()
	var e := MachineKit.glow_for(art.trim, MARQUEE_TRIM_GLOW)
	b.box(xa - 0.4, y1 - 0.5, zt - 0.9, xb + 0.4, y1 + 0.5, zt + 0.6, BoxFaces.new(metal, metal, metal, metal, null, 0.0, 0.0, 0.0, 0.7))
	b.quad(xa - 0.3, y1 + 0.32, zt + 0.62, xb + 0.3, y1 + 0.32, zt + 0.62, xb + 0.3, y1 - 0.18, zt + 0.62, xa - 0.3, y1 - 0.18, zt + 0.62, trim, 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, e)
	# ...a rail along the bottom edge...
	b.box(xa - 0.3, y0 - 0.3, zb - 0.5, xb + 0.3, y0 + 0.7, zb + 0.5, BoxFaces.new(metal, metal, metal, metal, null, 0.0, 0.0, 0.0, 0.7))
	# ...and lit edging down both ends of the face.
	b.quad(xa - 0.26, y1, zt - 0.4, xa - 0.26, y1, zt + 0.2, xa - 0.26, y0, zb + 0.2, xa - 0.26, y0, zb - 0.4, trim, -1.0, 0.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, e)
	b.quad(xb + 0.26, y1, zt + 0.2, xb + 0.26, y1, zt - 0.4, xb + 0.26, y0, zb - 0.4, xb + 0.26, y0, zb + 0.2, trim, 1.0, 0.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, e)
	# A strip light on the underside, at the front, throwing the sign's colour down the screen.
	led_strip_down(xa + 0.8, xb - 0.8, y0 - 0.02, zb - 2.2, zb - 0.9)
	bulb_row(xa + 1.0, xb - 1.0, y1 + 1.0, zt - 0.1, int((xb - xa) / 3.2))


## A strip of light facing down at height [param y] between depths [param za] and [param zb], in the
## glow colour, from [param xa] to [param xb].
func led_strip_down(xa: float, xb: float, y: float, za: float, zb: float, emissive: float = LED_GLOW) -> void:
	b.quad(xa, y, zb, xb, y, zb, xb, y, za, xa, y, za, art.glow_tex().full(), 0.0, -1.0, 0.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, MachineKit.glow_for(art.glow, emissive))


## A thin vertical or horizontal strip of light on a face at depth [param z] facing +z, in the glow
## colour.
func led_strip(xa: float, ya: float, xb: float, yb: float, z: float, emissive: float = LED_GLOW) -> void:
	b.quad(xa, yb, z, xb, yb, z, xb, ya, z, xa, ya, z, art.glow_tex().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, MachineKit.glow_for(art.glow, emissive))


## A screen's bezel: a raised black frame [param frame] wide round the screen rectangle on the plane
## at depth [param z], standing [param depth] proud at its outer edge and sloping down to the glass,
## so it catches the light, and a thin lit line hugging the screen's edge in the glow colour.
func screen_bezel(xa: float, xb: float, ya: float, yb: float, z: float, frame: float = 1.3, depth: float = 0.7) -> void:
	var m := art.black().full()
	var zo := z + depth
	var length := sqrt(depth * depth + frame * frame)
	var nz := frame / length
	var nd := depth / length
	var g := 0.6
	# The four slopes, from the outer edge (standing proud) to the inner edge (at the glass).
	b.quad(xa - frame, yb + frame, zo, xb + frame, yb + frame, zo, xb, yb, z, xa, yb, z, m, 0.0, -nd, nz, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, g)
	b.quad(xa, ya, z, xb, ya, z, xb + frame, ya - frame, zo, xa - frame, ya - frame, zo, m, 0.0, nd, nz, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, g)
	b.quad(xa - frame, yb + frame, zo, xa, yb, z, xa, ya, z, xa - frame, ya - frame, zo, m, nd, 0.0, nz, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, g)
	b.quad(xb, yb, z, xb + frame, yb + frame, zo, xb + frame, ya - frame, zo, xb, ya, z, m, -nd, 0.0, nz, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, g)
	# The outer wall, so the frame reads as a solid from the side.
	var xo0 := xa - frame
	var xo1 := xb + frame
	var yo0 := ya - frame
	var yo1 := yb + frame
	b.quad(xo0, yo1, z, xo1, yo1, z, xo1, yo1, zo, xo0, yo1, zo, m, 0.0, 1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, g)
	b.quad(xo0, yo0, zo, xo1, yo0, zo, xo1, yo0, z, xo0, yo0, z, m, 0.0, -1.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, g)
	b.quad(xo0, yo1, zo, xo0, yo1, z, xo0, yo0, z, xo0, yo0, zo, m, -1.0, 0.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, g)
	b.quad(xo1, yo1, z, xo1, yo1, zo, xo1, yo0, zo, xo1, yo0, z, m, 1.0, 0.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, g)
	# The lit line just inside the frame.
	var w := BEZEL_LINE
	var zl := z + 0.04
	led_strip(xa, yb - w, xb, yb, zl, BEZEL_GLOW)
	led_strip(xa, ya, xb, ya + w, zl, BEZEL_GLOW)
	led_strip(xa, ya + w, xa + w, yb - w, zl, BEZEL_GLOW)
	led_strip(xb - w, ya + w, xb, yb - w, zl, BEZEL_GLOW)


## A pane of glass over a screen: one clear, glossy, alpha-blended quad on the plane at depth
## [param z] that picks up the room's reflections and the glass texture's faint streaks. It shares
## its material with [method glass_box], so it costs no extra draw call on a cabinet that has both.
func screen_glass(xa: float, xb: float, ya: float, yb: float, z: float) -> void:
	b.quad(xa, yb, z, xb, yb, z, xb, ya, z, xa, ya, z, HallArt.glass().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.ALPHA, 0.0, false, -1, 1.0)


## A ticket dispenser [param w] wide, its bottom at [param y0], its front flush with the face at depth
## [param z] (it sits in a housing behind that face): a dark plate with a lit slot and a ticket
## sticking out of it and curling down.
func ticket_dispenser(x: float, y0: float, z: float, w: float = 7.0) -> void:
	var hh := w * 0.5
	var dark := art.dark_paint().full()
	b.box(x - w / 2.0, y0, z - 0.9, x + w / 2.0, y0 + hh, z, BoxFaces.new(MachineKit.ticket_plate().full(), dark, dark, dark, null, 0.25, 0.0, 0.0, 0.4))
	# The slot is at 14..21.5 of the plate's 32 texels; the paper leaves it at the middle.
	var sy := y0 + hh * (1.0 - 17.5 / 32.0)
	var pw := w * 0.17
	b.quad(x - pw, sy + 0.2, z + 0.02, x + pw, sy + 0.2, z + 0.02, x + pw, sy - w * 0.42, z + 1.4, x - pw, sy - w * 0.42, z + 1.4,
		MachineKit.ticket_paper().full(), 0.0, 0.36, 0.93, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, false)


## A coin door [param w] wide, its bottom at [param y0], on a face at depth [param z] facing +z: a
## steel door with a chrome frame and two coin mechs whose slots glow red, the way real ones are lit,
## and (with [param cup]) a coin return cup under it.
func coin_door(x: float, y0: float, z: float, w: float = 8.0, cup: bool = true) -> void:
	var hgt := w * 1.25
	var chrome := HallArt.chrome().full()
	var metal := HallArt.dark_metal().full()
	b.box(x - w / 2.0 - 0.4, y0 - 0.4, z, x + w / 2.0 + 0.4, y0 + hgt + 0.4, z + 0.3, BoxFaces.new(chrome, chrome, chrome, chrome, null, 0.0, 0.0, 0.0, 0.9))
	b.quad(x - w / 2.0, y0 + hgt, z + 0.32, x + w / 2.0, y0 + hgt, z + 0.32, x + w / 2.0, y0, z + 0.32, x - w / 2.0, y0, z + 0.32, art.coin_door().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.7)
	# The slots' lit windows, laid over the painted door's slot plates.
	var slot := MachineKit.coin_slot_lit().full()
	var top := y0 + hgt * (1.0 - MachineKit.SLOT_Y0)
	var bot := y0 + hgt * (1.0 - MachineKit.SLOT_Y1)
	var hw := w * MachineKit.SLOT_HALF_W
	for k in 2:
		var sx := x - w / 2.0 + w * (MachineKit.SLOT_X0 + k * MachineKit.SLOT_DX)
		b.quad(sx - hw, top, z + 0.36, sx + hw, top, z + 0.36, sx + hw, bot, z + 0.36, sx - hw, bot, z + 0.36, slot, 0.0, 0.0, 1.0,
			0.0, 0.0, NAN, NAN, Blend.OPAQUE, SLOT_GLOW)
	# The coin return cup under the door: a dark tray with a chrome lip.
	if cup:
		b.box(x - w * 0.3, y0 - 1.8, z, x + w * 0.3, y0 - 0.4, z + 0.8, BoxFaces.new(metal, metal, metal, chrome, null, 0.0, 0.0, 0.0, 0.6))


## A thin lit strip (T-molding, a neon edge) from ([param xa], [param ya]) to ([param xb],
## [param yb]) on a face at depth [param z]; [param color] [constant ART_GLOW] is the art's glow.
func neon_strip(xa: float, ya: float, xb: float, yb: float, z: float, color: int = ART_GLOW, emissive: float = 1.5) -> void:
	var c := art.glow if color == ART_GLOW else color
	b.quad(xa, yb, z, xb, yb, z, xb, ya, z, xa, ya, z, HallArt.solid(c).full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, emissive)


## The red LED score display, facing +z at depth [param z].
func display(xa: float, xb: float, ya: float, yb: float, z: float) -> void:
	b.quad(xa, yb, z, xb, yb, z, xb, ya, z, xa, ya, z, art.display().full(), 0.0, 0.0, 1.0,
		0.0, 0.0, NAN, NAN, Blend.OPAQUE, 1.2)


## The cabinet's back, for anyone walking behind the bank: a service panel ([param xa]..[param xb],
## [param y0]..[param y1]) laid over the back face at depth [param z] (NAN: z0), facing -z, with
## vents, a fan, stickers and a serial plate, and the mains cable dropping from its inlet to the floor.
func rear_panel(xa: float, xb: float, y0: float, y1: float, z: float = NAN) -> void:
	if is_nan(z):
		z = z0
	var w := xb - xa
	var hgt := y1 - y0
	var tall := hgt > w * 1.3
	var tex := MachineKit.rear_panel(tall).full()
	var zf := z - 0.06
	# Seen from behind, +x runs right to left: the texture's left edge goes at xb.
	b.quad(xb, y1, zf, xa, y1, zf, xa, y0, zf, xb, y0, zf, tex, 0.0, 0.0, -1.0, 0.0, 0.0, NAN, NAN, Blend.OPAQUE, 0.0, true, -1, 0.25)
	# The mains inlet sits near the panel's bottom (see MachineKit.paint_rear); its cable drops to the
	# floor and trails off a little way behind, towards the wall socket.
	var px := xb - w * (0.4 if tall else 0.25)
	var py := y1 - hgt * (0.86 if tall else 0.72)
	# In the cabinet's own dark paint, which it already draws with: no extra draw call.
	var cable := art.dark_paint().full()
	b.capsule(px, py, zf - 0.4, px - 0.6, py * 0.4, zf - 2.2, 0.45, cable, 5, -1, 0.3)
	b.capsule(px - 0.6, py * 0.4, zf - 2.2, px - 1.4, 0.45, zf - 2.8, 0.45, cable, 5, -1, 0.3)
	b.capsule(px - 1.4, 0.45, zf - 2.8, px - 3.0, 0.45, zf - 6.0, 0.45, cable, 5, -1, 0.3)


## A face at height [param y] looking down, closing the underside of a part hung above eye level
## ([param region] null: the art's dark paint).
func underside(xa: float, xb: float, za: float, zb: float, y: float, region: Region = null) -> void:
	var r := region if region != null else art.dark_paint().full()
	b.quad(xa, y, zb, xb, y, zb, xb, y, za, xa, y, za, r, 0.0, -1.0, 0.0)


## Glass front and sides (and back, if [param back]) of a case.
func glass_box(xa: float, ya: float, za: float, xb: float, yb: float, zb: float, back: bool = false) -> void:
	var g := HallArt.glass().full()
	b.quad(xa, yb, zb, xb, yb, zb, xb, ya, zb, xa, ya, zb, g, 0.0, 0.0, 1.0, 0.0, 0.0, NAN, NAN, Blend.ALPHA, 0.0, false, -1, 1.0)
	b.quad(xa, yb, za, xa, yb, zb, xa, ya, zb, xa, ya, za, g, -1.0, 0.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.ALPHA, 0.0, false, -1, 1.0)
	b.quad(xb, yb, zb, xb, yb, za, xb, ya, za, xb, ya, zb, g, 1.0, 0.0, 0.0, 0.0, 0.0, NAN, NAN, Blend.ALPHA, 0.0, false, -1, 1.0)
	if back:
		b.quad(xb, yb, za, xa, yb, za, xa, ya, za, xb, ya, za, g, 0.0, 0.0, -1.0, 0.0, 0.0, NAN, NAN, Blend.ALPHA, 0.0, false, -1, 1.0)


## Four chrome corner posts.
func posts(xa: float, xb: float, za: float, zb: float, ya: float, yb: float, t: float = 1.4) -> void:
	var m := HallArt.chrome().full()
	var f := BoxFaces.new(m, m, m, m, m, 0.0, 0.0, 0.0, 0.9)
	b.box(xa, ya, za, xa + t, yb, za + t, f)
	b.box(xb - t, ya, za, xb, yb, za + t, f)
	b.box(xa, ya, zb - t, xa + t, yb, zb, f)
	b.box(xb - t, ya, zb - t, xb, yb, zb, f)


## Makes this copy's live attract screen and registers it so the hall repaints it while the cabinet
## is in view. Map `live_screen().texture.full()` onto a quad (emissive about 1.1).
func live_screen() -> LiveScreen:
	var s := LiveScreen.new(art, seed_value)
	_unit.attach_screen(s)
	return s
