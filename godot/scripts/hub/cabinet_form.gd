class_name CabinetForm
extends RefCounted
## hub/CabinetForm.kt: how cabinet boxes are shaped and shaded. Every strength is a constant here so
## the look can be tuned in one place: the chamfers are subtle (they only have to catch the light
## along an edge), and the occlusion only ever darkens paint that is already unlit, so nothing new
## can glow.
##
## Kotlin's extension `ModelBuilder.beveledBox(...)` is the static [method beveled_box] here
## (GDScript has no extension functions): `CabinetForm.beveled_box(b, x0, y0, z0, x1, y1, z1, faces)`
## adds to builder `b` and returns it.

## Chamfer on a cabinet box's vertical and top edges, in hall units.
const BEVEL := 0.8
## A chamfer never takes more than this fraction of a box's thinnest side (thin panels stay panels).
const BEVEL_MAX_FRACTION := 0.3
## Boxes whose chamfer would come out smaller than this are left square.
const MIN_BEVEL := 0.1
## Gloss the chamfer strips get at least, so an edge catches a highlight even on matte paint.
const BEVEL_GLOSS := 0.3
## How much the paint darkens right at the floor (0 none, 1 black).
const FLOOR_AO := 0.4
## How far up a box the floor darkening reaches before it has faded out, in hall units.
const FLOOR_AO_HEIGHT := 7.0

const _K := 0.70710678
const _C := 0.57735027


## An axis-aligned box like [method ModelBuilder.box], with its vertical and top edges chamfered and
## its paint darkened towards the floor, added to [param b] (which is returned).
##
## - Chamfers. An edge is cut at 45° by [param bevel] (at most a third of the box's thinnest side; 0
##   for square edges, with just the occlusion) wherever both faces that meet there exist in
##   [param f]; a face that isn't there (a box tucked between two side panels) keeps a square edge on
##   that side. Each cut is a narrow strip, and a small triangle closes each top corner where three
##   cuts meet, so the surface stays closed. Faces keep their texture registered to the whole box, so
##   painted art doesn't shift.
## - Occlusion. With [param floor_ao] above zero the four sides (and the vertical cuts) fade from
##   `1 - floor_ao` at the bottom to plain paint [param ao_height] up, through a brightness stored per
##   vertex (Poly.shade). By default (NAN) that happens for boxes that stand on the floor
##   (y0 <= 1). Glowing faces are never darkened.
##
## A box with all five faces goes from 5 polygons to 17 (4 vertical cuts, 4 top cuts, 4 corners),
## and to 25 with occlusion (each side and vertical cut splits in two). The box's bounds don't
## change, so culling is unaffected.
static func beveled_box(b: ModelBuilder, x0: float, y0: float, z0: float, x1: float, y1: float, z1: float, f: BoxFaces,
		tint: int = -1, bevel: float = BEVEL, floor_ao: float = NAN, ao_height: float = FLOOR_AO_HEIGHT) -> ModelBuilder:
	if is_nan(floor_ao):
		floor_ao = FLOOR_AO if y0 <= 1.0 else 0.0
	var w := x1 - x0
	var d := z1 - z0
	var h := y1 - y0
	var limited := minf(bevel, BEVEL_MAX_FRACTION * minf(w, minf(d, h)))
	# A cut too small to see means square edges (which may still have occlusion at the foot).
	var bv := limited if limited >= MIN_BEVEL else 0.0
	var ao := clampf(floor_ao, 0.0, 0.9)
	if bv == 0.0 and (ao == 0.0 or h < ao_height):
		return b.box(x0, y0, z0, x1, y1, z1, f, tint)
	var job := _Job.new(b, x0, y0, z0, x1, y1, z1, f, tint, bv, ao, ao_height)
	job.run()
	return b


## One beveled box being built (Kotlin's local functions and the values they close over).
class _Job:
	var b: ModelBuilder
	var x0: float
	var y0: float
	var z0: float
	var x1: float
	var y1: float
	var z1: float
	var f: BoxFaces
	var tint: int
	var bv: float
	var ao: float
	var w: float
	var d: float
	var h: float
	var tpu: float
	var ya: float
	var strip_gloss: float

	func _init(p_b: ModelBuilder, p_x0: float, p_y0: float, p_z0: float, p_x1: float, p_y1: float, p_z1: float, p_f: BoxFaces,
			p_tint: int, p_bv: float, p_ao: float, ao_height: float) -> void:
		b = p_b
		x0 = p_x0
		y0 = p_y0
		z0 = p_z0
		x1 = p_x1
		y1 = p_y1
		z1 = p_z1
		f = p_f
		tint = p_tint
		bv = p_bv
		ao = p_ao
		w = x1 - x0
		d = z1 - z0
		h = y1 - y0
		tpu = f.texels_per_unit
		# Up to here the sides darken towards the floor; never more than most of the way up a short box.
		ya = y0 + minf(ao_height, 0.6 * (y1 - bv - y0))
		strip_gloss = maxf(f.gloss, CabinetForm.BEVEL_GLOSS)

	func cut(on: bool) -> float:
		return bv if on else 0.0

	## Texture size a face maps across: whole region, or texels per unit for wrapping regions.
	func uw(r: Region, face_w: float) -> float:
		return face_w * tpu if r.wrap and tpu > 0.0 else float(r.w)

	func vh(r: Region, face_h: float) -> float:
		return face_h * tpu if r.wrap and tpu > 0.0 else float(r.h)

	func v(v_size: float, y: float) -> float:
		return v_size * (y1 - y) / h

	## A vertical quad from (ax, az) to (bx, bz) between y_lo and y_hi. Its texture columns run
	## u0..u1 and its rows follow the box's height (v_size rows over h). Split at ya when the bottom
	## is to darken.
	func wall(ax: float, az: float, bx: float, bz: float, y_lo: float, y_hi: float, r: Region, u0: float, u1: float, v_size: float,
			nx: float, nz: float, emissive: float, gloss: float) -> void:
		if y_hi - y_lo < 1e-4:
			return
		if ao > 0.0 and emissive <= 0.0 and y_lo < ya - 1e-4:
			var y_mid := minf(ya, y_hi)
			b.quad(ax, y_mid, az, bx, y_mid, bz, bx, y_lo, bz, ax, y_lo, az, r, nx, 0.0, nz,
				u0, v(v_size, y_mid), u1, v(v_size, y_lo), Blend.OPAQUE, emissive, true, tint, gloss,
				PackedFloat32Array([1.0, 1.0, 1.0 - ao, 1.0 - ao]))
			if y_hi > y_mid + 1e-4:
				b.quad(ax, y_hi, az, bx, y_hi, bz, bx, y_mid, bz, ax, y_mid, az, r, nx, 0.0, nz,
					u0, v(v_size, y_hi), u1, v(v_size, y_mid), Blend.OPAQUE, emissive, true, tint, gloss)
		else:
			b.quad(ax, y_hi, az, bx, y_hi, bz, bx, y_lo, bz, ax, y_lo, az, r, nx, 0.0, nz,
				u0, v(v_size, y_hi), u1, v(v_size, y_lo), Blend.OPAQUE, emissive, true, tint, gloss)

	## A triangle closing a top corner where two top cuts and a vertical cut meet.
	func corner(on: bool, r: Region, sx: float, sz: float, cx: float, cz: float, emissive: float) -> void:
		if not on or bv <= 0.0 or r == null:
			return
		# The three corners: on the top face, on the side facing x, on the side facing z.
		var half := PackedFloat32Array([0.5, 0.5, 0.5])
		b.poly(PackedFloat32Array([cx - sx * bv, cx, cx - sx * bv]), PackedFloat32Array([y1, y1 - bv, y1 - bv]),
			PackedFloat32Array([cz - sz * bv, cz - sz * bv, cz]), half, half, r, sx * CabinetForm._C, CabinetForm._C, sz * CabinetForm._C,
			Blend.OPAQUE, emissive, true, tint, strip_gloss)

	func run() -> void:
		var front := f.front
		var back := f.back
		var left := f.left
		var right := f.right
		var top := f.top
		var fl := front != null and left != null
		var fr := front != null and right != null
		var bl := back != null and left != null
		var br := back != null and right != null
		var tf := top != null and front != null
		var tb := top != null and back != null
		var tl := top != null and left != null
		var tr := top != null and right != null
		var cuts := bv > 0.0
		var k := CabinetForm._K
		var fe := f.front_emissive
		var te := f.top_emissive
		var side_top := y1 - cut(top != null)

		# The four sides, each inset by whatever chamfers meet it and topped by its top chamfer.
		if front != null:
			var xa := x0 + cut(fl)
			var xb := x1 - cut(fr)
			var ru := uw(front, w)
			wall(xa, z1, xb, z1, y0, y1 - cut(tf), front, ru * (xa - x0) / w, ru * (xb - x0) / w, vh(front, h), 0.0, 1.0, fe, f.gloss)
		if back != null:
			var xa := x1 - cut(br)
			var xb := x0 + cut(bl)
			var ru := uw(back, w)
			wall(xa, z0, xb, z0, y0, y1 - cut(tb), back, ru * (x1 - xa) / w, ru * (x1 - xb) / w, vh(back, h), 0.0, -1.0, 0.0, f.gloss)
		if left != null:
			var za := z0 + cut(bl)
			var zb := z1 - cut(fl)
			var ru := uw(left, d)
			wall(x0, za, x0, zb, y0, y1 - cut(tl), left, ru * (za - z0) / d, ru * (zb - z0) / d, vh(left, h), -1.0, 0.0, 0.0, f.gloss)
		if right != null:
			var za := z1 - cut(fr)
			var zb := z0 + cut(br)
			var ru := uw(right, d)
			wall(x1, za, x1, zb, y0, y1 - cut(tr), right, ru * (z1 - za) / d, ru * (z1 - zb) / d, vh(right, h), 1.0, 0.0, 0.0, f.gloss)
		if top != null:
			var xa := x0 + cut(tl)
			var xb := x1 - cut(tr)
			var za := z0 + cut(tb)
			var zb := z1 - cut(tf)
			var ru := uw(top, w)
			var rv := vh(top, d)
			b.quad(xa, y1, za, xb, y1, za, xb, y1, zb, xa, y1, zb, top, 0.0, 1.0, 0.0,
				ru * (xa - x0) / w, rv * (za - z0) / d, ru * (xb - x0) / w, rv * (zb - z0) / d,
				Blend.OPAQUE, te, true, tint, f.gloss)

		# Vertical cuts down each corner where two sides meet: sample the side's own edge column. The
		# front ones glow as much as the front does, so a lit T-molding just rounds off, not dims.
		if fl and cuts:
			var r: Region = left if left != null else front
			var u := uw(r, d) - 0.5 if r == left else 0.5
			wall(x0, z1 - bv, x0 + bv, z1, y0, side_top, r, u, u, vh(r, h), -k, k, fe, strip_gloss)
		if fr and cuts:
			var r: Region = right if right != null else front
			var u := 0.5 if r == right else uw(r, w) - 0.5
			wall(x1 - bv, z1, x1, z1 - bv, y0, side_top, r, u, u, vh(r, h), k, k, fe, strip_gloss)
		if bl and cuts:
			var r: Region = left if left != null else back
			var u := 0.5 if r == left else uw(r, w) - 0.5
			wall(x0 + bv, z0, x0, z0 + bv, y0, side_top, r, u, u, vh(r, h), -k, -k, 0.0, strip_gloss)
		if br and cuts:
			var r: Region = right if right != null else back
			var u := uw(r, d) - 0.5 if r == right else 0.5
			wall(x1, z0 + bv, x1 - bv, z0, y0, side_top, r, u, u, vh(r, h), k, -k, 0.0, strip_gloss)

		# Chamfers along the top edges: sample the side's top row, its columns following the side's.
		if tf and cuts:
			var xa := x0 + cut(fl)
			var xb := x1 - cut(fr)
			var ru := uw(front, w)
			b.quad(xa, y1, z1 - bv, xb, y1, z1 - bv, xb, y1 - bv, z1, xa, y1 - bv, z1, front, 0.0, k, k,
				ru * (xa - x0) / w, 0.5, ru * (xb - x0) / w, 0.5, Blend.OPAQUE, (fe + te) * 0.5, true, tint, strip_gloss)
		if tb and cuts:
			var xa := x1 - cut(br)
			var xb := x0 + cut(bl)
			var ru := uw(back, w)
			b.quad(xa, y1, z0 + bv, xb, y1, z0 + bv, xb, y1 - bv, z0, xa, y1 - bv, z0, back, 0.0, k, -k,
				ru * (x1 - xa) / w, 0.5, ru * (x1 - xb) / w, 0.5, Blend.OPAQUE, te * 0.5, true, tint, strip_gloss)
		if tl and cuts:
			var za := z0 + cut(bl)
			var zb := z1 - cut(fl)
			var ru := uw(left, d)
			b.quad(x0 + bv, y1, za, x0 + bv, y1, zb, x0, y1 - bv, zb, x0, y1 - bv, za, left, -k, k, 0.0,
				ru * (za - z0) / d, 0.5, ru * (zb - z0) / d, 0.5, Blend.OPAQUE, te * 0.5, true, tint, strip_gloss)
		if tr and cuts:
			var za := z1 - cut(fr)
			var zb := z0 + cut(br)
			var ru := uw(right, d)
			b.quad(x1 - bv, y1, za, x1 - bv, y1, zb, x1, y1 - bv, zb, x1, y1 - bv, za, right, k, k, 0.0,
				ru * (z1 - za) / d, 0.5, ru * (z1 - zb) / d, 0.5, Blend.OPAQUE, te * 0.5, true, tint, strip_gloss)

		# A triangle closes each top corner where two top cuts and a vertical cut meet.
		var ce := (fe + te) * 0.3
		corner(fl and tf and tl, top, -1.0, 1.0, x0, z1, ce)
		corner(fr and tf and tr, top, 1.0, 1.0, x1, z1, ce)
		corner(bl and tb and tl, top, -1.0, -1.0, x0, z0, ce)
		corner(br and tb and tr, top, 1.0, -1.0, x1, z0, ce)
