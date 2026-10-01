class_name TexPaint
extends RefCounted
## engine/r3d/TexPaint.kt: paints smooth, anti-aliased textures: gradients, rounded shapes, real
## fonts, soft glows and grain. Build-13 painted with Android's Canvas; here the same calls are
## recorded and drawn by Godot's 2D renderer into an offscreen viewport (4× MSAA), which is read
## back once into a mipmapped texture ([method to_texture]), or kept and repainted in place for a
## live texture ([method update]: cabinet screens, LED displays).
##
## Android Canvas calls the art makes directly (`tp.canvas.drawArc(..., tp.paint)`) map to the
## `c_*` methods with a [PaPaint]; save/restore/rotate/translate/scale/clip_rect act on the
## recorded transform as Canvas did. Grain, blur masks, glows and the DST_IN mask are composited
## in extra viewports, in the order they were called.

const ALIGN_LEFT := PaPaint.Align.LEFT
const ALIGN_CENTER := PaPaint.Align.CENTER
const ALIGN_RIGHT := PaPaint.Align.RIGHT

# Op kinds.
const K_POLY := 0
const K_CIRCLE := 1
const K_LINE := 2
const K_TEXT := 3
const K_IMAGE := 4
const K_POLYLINE := 5
const K_LABEL := 6
# Layer markers.
const L_GRAIN := 100
const L_GLOW := 101
const L_MASK := 102
const L_CLIP := 103
const L_UNCLIP := 104

var w: int
var h: int
## Pixels per drawing unit: above 1 when painting in texel units at a finer resolution.
var unit := 1.0
## The recorded drawing: [kind, args..., paint, transform] entries and layer markers.
var ops: Array = []
var _xf := Transform2D.IDENTITY
var _base := Transform2D.IDENTITY
var _stack: Array = []
## Kotlin's tp.paint, for code that sets it up and draws on the canvas.
var paint := PaPaint.new()
var _job: PaintJob = null


func _init(p_w: int, p_h: int) -> void:
	w = maxi(1, p_w)
	h = maxi(1, p_h)


## From now on draw in units of [param scale] pixels (see [method paint_texture]).
func use_units(scale: float) -> void:
	unit = scale
	_base = Transform2D(0.0, Vector2(scale, scale), 0.0, Vector2.ZERO)
	_xf = _base


## Erases everything to [param color] (Bitmap.eraseColor): the recording starts over.
func clear(color: int = 0) -> void:
	ops.clear()
	_stack.clear()
	_xf = _base
	if (color >> 24) & 0xFF != 0:
		var p := PaPaint.new(color)
		_push_poly(_rect_pts(0, 0, w / unit, h / unit), p)


# ---------------------------------------------------------------- Canvas: transform and clip

func save() -> void:
	_stack.append(_xf)


func restore() -> void:
	if _stack.is_empty():
		return
	var top: Variant = _stack.pop_back()
	if top is Array:
		# A clip ends here.
		ops.append([L_UNCLIP])
		_xf = top[0]
	else:
		_xf = top


func translate(dx: float, dy: float) -> void:
	_xf = _xf * Transform2D(0.0, Vector2(dx, dy))


func rotate(degrees: float, px: float = 0.0, py: float = 0.0) -> void:
	_xf = _xf * Transform2D(0.0, Vector2(px, py)) * Transform2D(deg_to_rad(degrees), Vector2.ZERO) * Transform2D(0.0, Vector2(-px, -py))


func scale_by(sx: float, sy: float, px: float = 0.0, py: float = 0.0) -> void:
	_xf = _xf * Transform2D(0.0, Vector2(px, py)) * Transform2D(0.0, Vector2(sx, sy), 0.0, Vector2.ZERO) * Transform2D(0.0, Vector2(-px, -py))


## Canvas.setMatrix(null) puts the plain pixel transform back.
func set_matrix(m: Variant = null) -> void:
	_xf = Transform2D.IDENTITY if m == null else m


## Canvas.clipRect until the matching restore().
func clip_rect(left: float, top: float, right: float, bottom: float) -> void:
	_stack.append([_xf])
	ops.append([L_CLIP, _xf * PackedVector2Array(_rect_pts(left, top, right - left, bottom - top))])


# ---------------------------------------------------------------- recording helpers

static func _rect_pts(x: float, y: float, rw: float, rh: float) -> PackedVector2Array:
	return PackedVector2Array([Vector2(x, y), Vector2(x + rw, y), Vector2(x + rw, y + rh), Vector2(x, y + rh)])


static func _ellipse_pts(cx: float, cy: float, rx: float, ry: float, n: int = 48) -> PackedVector2Array:
	var pts := PackedVector2Array()
	pts.resize(n)
	for i in n:
		var a := TAU * i / n
		pts[i] = Vector2(cx + cos(a) * rx, cy + sin(a) * ry)
	return pts


func _push(entry: Array, p: PaPaint) -> void:
	var pc := p.copy()
	if pc.xfer == PaPaint.Xfer.DST_IN:
		ops.append([L_MASK, entry, pc, _xf])
		return
	if pc.blur_radius > 0.0:
		var inner := pc.copy()
		inner.blur_radius = 0.0
		ops.append([L_GLOW, pc.blur_radius * unit, pc.color, [[entry[0]] + entry.slice(1) + [inner, _xf]], Vector2.ZERO])
		return
	if pc.shadow_radius > 0.0 and pc.shadow_color != 0:
		var sh := pc.copy()
		sh.shadow_radius = 0.0
		ops.append([L_GLOW, pc.shadow_radius, pc.shadow_color, [[entry[0]] + entry.slice(1) + [sh, _xf]], Vector2(pc.shadow_dx, pc.shadow_dy)])
		pc.shadow_radius = 0.0
	ops.append([entry[0]] + entry.slice(1) + [pc, _xf])


func _push_poly(pts: PackedVector2Array, p: PaPaint) -> void:
	_push([K_POLY, pts], p)


func _fill_paint(color: int) -> PaPaint:
	var p := PaPaint.new(color)
	return p


# ---------------------------------------------------------------- TexPaint's own drawing

func fill(color: int) -> void:
	rect(0.0, 0.0, w / unit, h / unit, color)


func rect(x: float, y: float, rw: float, rh: float, color: int) -> void:
	_push_poly(_rect_pts(x, y, rw, rh), _fill_paint(color))


func round(x: float, y: float, rw: float, rh: float, r: float, color: int) -> void:
	_push_poly(PaPath.round_rect_points(x, y, x + rw, y + rh, r, r, 8), _fill_paint(color))


func circle(cx: float, cy: float, r: float, color: int) -> void:
	_push([K_CIRCLE, Vector2(cx, cy), r], _fill_paint(color))


func oval(cx: float, cy: float, rx: float, ry: float, color: int) -> void:
	_push_poly(_ellipse_pts(cx, cy, rx, ry), _fill_paint(color))


func line(x0: float, y0: float, x1: float, y1: float, width: float, color: int, round_cap: bool = true) -> void:
	var p := PaPaint.new(color)
	p.style = PaPaint.Style.STROKE
	p.stroke_width = width
	p.round_cap = round_cap
	_push([K_LINE, Vector2(x0, y0), Vector2(x1, y1)], p)


func stroke_round(x: float, y: float, rw: float, rh: float, r: float, width: float, color: int) -> void:
	var p := PaPaint.new(color)
	p.style = PaPaint.Style.STROKE
	p.stroke_width = width
	_push([K_POLYLINE, PaPath.round_rect_points(x, y, x + rw, y + rh, r, r, 8), true], p)


## Cuts a fully transparent round hole.
func punch(cx: float, cy: float, r: float) -> void:
	var p := PaPaint.new(0)
	p.xfer = PaPaint.Xfer.CLEAR
	_push([K_CIRCLE, Vector2(cx, cy), r], p)


## A five-pointed star of outer radius [param r].
func star(cx: float, cy: float, r: float, color: int, inner: float = 0.45) -> void:
	var pts := PackedVector2Array()
	for k in 10:
		var a := -PI / 2.0 + k * PI / 5.0
		var rr := r if k % 2 == 0 else r * inner
		pts.append(Vector2(cx + cos(a) * rr, cy + sin(a) * rr))
	_push_poly(pts, _fill_paint(color))


func ring(cx: float, cy: float, r: float, width: float, color: int) -> void:
	var p := PaPaint.new(color)
	p.style = PaPaint.Style.STROKE
	p.stroke_width = width
	_push([K_POLYLINE, _ellipse_pts(cx, cy, r, r, 64), true], p)


## A polygon from flat (x, y) pairs.
func polygon(points: PackedFloat32Array, color: int) -> void:
	var pts := PackedVector2Array()
	var i := 0
	while i + 1 < points.size():
		pts.append(Vector2(points[i], points[i + 1]))
		i += 2
	_push_poly(pts, _fill_paint(color))


## A vertical gradient over a rectangle through the given colours (evenly spaced).
func vgrad(x: float, y: float, rw: float, rh: float, colors: Array) -> void:
	var p := PaPaint.new(0xFFFFFFFF)
	p.shader = PaBrush.linear(colors, Vector2(0, y), Vector2(0, y + rh))
	_push_poly(_rect_pts(x, y, rw, rh), p)


func hgrad(x: float, y: float, rw: float, rh: float, colors: Array) -> void:
	var p := PaPaint.new(0xFFFFFFFF)
	p.shader = PaBrush.linear(colors, Vector2(x, 0), Vector2(x + rw, 0))
	_push_poly(_rect_pts(x, y, rw, rh), p)


## A rounded rectangle filled with a vertical gradient.
func round_grad(x: float, y: float, rw: float, rh: float, r: float, top: int, bottom: int) -> void:
	var p := PaPaint.new(0xFFFFFFFF)
	p.shader = PaBrush.linear([top, bottom], Vector2(0, y), Vector2(0, y + rh))
	_push_poly(PaPath.round_rect_points(x, y, x + rw, y + rh, r, r, 8), p)


## A soft radial blob fading from [param inner] at the centre to [param outer] at radius [param r].
func radial(cx: float, cy: float, r: float, inner: int, outer: int) -> void:
	var p := PaPaint.new(0xFFFFFFFF)
	p.shader = PaBrush.radial([inner, outer], Vector2(cx, cy), maxf(r, 0.5))
	_push_poly(_ellipse_pts(cx, cy, r, r), p)


## A shaded sphere-like disc: lit from the top left with a soft rim.
func ball(cx: float, cy: float, r: float, color: int, light: float = 0.45) -> void:
	var hi := Pal.mix_argb(color, 0xFFFFFFFF, light)
	var lo := Pal.mix_argb(color, 0xFF000000, 0.45)
	var p := PaPaint.new(0xFFFFFFFF)
	p.shader = PaBrush.radial([hi, color, lo], Vector2(cx - r * 0.35, cy - r * 0.4), r * 1.4, [0.0, 0.45, 1.0])
	_push_poly(_ellipse_pts(cx, cy, r, r), p)


## Text with its left, centre or right edge at [param x] and its baseline at [param y].
func text(s: String, x: float, y: float, size: float, color: int, face: Font = null, align: int = ALIGN_CENTER, spacing: float = 0.0) -> void:
	var p := PaPaint.new(color)
	p.typeface = face if face != null else Fonts.display()
	p.text_size = size
	p.text_align = align
	p.letter_spacing = spacing
	_push([K_TEXT, s, Vector2(x, y)], p)


## Text with a blurred halo behind it, for neon and lit signs.
func glow_text(s: String, x: float, y: float, size: float, color: int, glow_color: int, radius: float, face: Font = null, spacing: float = 0.0) -> void:
	var f := face if face != null else Fonts.display()
	var halo := PaPaint.new(glow_color)
	halo.typeface = f
	halo.text_size = size
	halo.text_align = ALIGN_CENTER
	halo.letter_spacing = spacing
	halo.blur_radius = maxf(radius, 0.5)
	# Drawn twice through the blur in Kotlin, for a stronger halo.
	_push([K_TEXT, s, Vector2(x, y)], halo)
	_push([K_TEXT, s, Vector2(x, y)], halo)
	text(s, x, y, size, color, f, ALIGN_CENTER, spacing)


## Outlined text (a stroke under the fill), for marquees and labels that must read anywhere.
func outlined_text(s: String, x: float, y: float, size: float, color: int, outline: int, stroke: float, face: Font = null, spacing: float = 0.0) -> void:
	var f := face if face != null else Fonts.display()
	var o := PaPaint.new(outline)
	o.typeface = f
	o.text_size = size
	o.text_align = ALIGN_CENTER
	o.letter_spacing = spacing
	o.style = PaPaint.Style.STROKE
	o.stroke_width = stroke
	_push([K_TEXT, s, Vector2(x, y)], o)
	text(s, x, y, size, color, f, ALIGN_CENTER, spacing)


func text_width(s: String, size: float, face: Font = null, spacing: float = 0.0) -> float:
	return TextKit.width(s, face if face != null else Fonts.display(), size, spacing)


## Draws a soft, blurred [param color] halo in the shape of whatever [param block] paints (it is
## called with this painter; Kotlin's `glow(radius, color) { ... }`).
func glow(radius: float, color: int, block: Callable) -> void:
	var outer := ops
	ops = []
	block.call(self)
	var inner := ops
	ops = outer
	ops.append([L_GLOW, radius * unit, color, inner, Vector2.ZERO])


## Game-style text (ArcadeFont) with capitals [param cap] units tall and their top at [param y].
func label(s: String, x: float, y: float, cap: float, color: int, centered: bool = true, tiny: bool = false, shadow: int = 0) -> void:
	var u := cap / (ArcadeFont.TINY_CAP if tiny else ArcadeFont.CAP)
	var left := x - ArcadeFont.width(s, u, tiny) / 2.0 if centered else x
	# ArcadeFont draws its own soft shadow: the paint only carries the shadow colour.
	var p := PaPaint.new(color)
	ops.append([K_LABEL, s, Vector2(left, y), u, tiny, shadow, p, _xf])


## Sprinkles random brightness variation over the painted pixels (fabric, paint, wood grain).
func grain(amount: float, seed: int = 1) -> void:
	ops.append([L_GRAIN, amount, seed])


## Draws [param tex] (Canvas.drawBitmap) at (x, y), [param size] defaulting to its own.
func image(tex: Texture2D, x: float, y: float, size: Vector2 = Vector2(NAN, NAN)) -> void:
	if tex == null:
		return
	var s := Vector2(tex.get_width(), tex.get_height()) if is_nan(size.x) else size
	_push([K_IMAGE, tex, Rect2(Vector2(x, y), s)], PaPaint.new(0xFFFFFFFF))


# ---------------------------------------------------------------- Canvas draws with a Paint

func c_draw_rect(l: float, t: float, r: float, b: float, p: PaPaint) -> void:
	_shape(_rect_pts(l, t, r - l, b - t), p)


func c_draw_round_rect(l: float, t: float, r: float, b: float, rx: float, ry: float, p: PaPaint) -> void:
	_shape(PaPath.round_rect_points(l, t, r, b, rx, ry, 8), p)


func c_draw_circle(cx: float, cy: float, r: float, p: PaPaint) -> void:
	if p.style == PaPaint.Style.FILL and p.shader == null:
		_push([K_CIRCLE, Vector2(cx, cy), r], p)
	else:
		_shape(_ellipse_pts(cx, cy, r, r), p)


func c_draw_oval(l: float, t: float, r: float, b: float, p: PaPaint) -> void:
	_shape(_ellipse_pts((l + r) / 2.0, (t + b) / 2.0, (r - l) / 2.0, (b - t) / 2.0), p)


func c_draw_arc(l: float, t: float, r: float, b: float, start_deg: float, sweep_deg: float, use_center: bool, p: PaPaint) -> void:
	var c := Vector2((l + r) / 2.0, (t + b) / 2.0)
	var rx := (r - l) / 2.0
	var ry := (b - t) / 2.0
	var n := maxi(4, int(48 * absf(sweep_deg) / 360.0) + 2)
	var pts := PackedVector2Array()
	if use_center:
		pts.append(c)
	for i in n + 1:
		var a := deg_to_rad(start_deg + sweep_deg * i / n)
		pts.append(c + Vector2(cos(a) * rx, sin(a) * ry))
	if p.style == PaPaint.Style.STROKE and not use_center:
		_push([K_POLYLINE, pts, false], p)
	else:
		_shape(pts, p)


func c_draw_line(x0: float, y0: float, x1: float, y1: float, p: PaPaint) -> void:
	_push([K_LINE, Vector2(x0, y0), Vector2(x1, y1)], p)


func c_draw_path(path: PaPath, p: PaPaint) -> void:
	var parts: Array = path.all_subpaths()
	for i in (parts[0] as Array).size():
		var pts: PackedVector2Array = parts[0][i]
		if p.style == PaPaint.Style.STROKE:
			_push([K_POLYLINE, pts, parts[1][i]], p)
		else:
			_shape(pts, p)


func c_draw_text(s: String, x: float, y: float, p: PaPaint) -> void:
	_push([K_TEXT, s, Vector2(x, y)], p)


func _shape(pts: PackedVector2Array, p: PaPaint) -> void:
	if p.style == PaPaint.Style.FILL or p.style == PaPaint.Style.FILL_AND_STROKE:
		_push_poly(pts, p)
	if p.style == PaPaint.Style.STROKE or p.style == PaPaint.Style.FILL_AND_STROKE:
		_push([K_POLYLINE, pts, true], p)


# ---------------------------------------------------------------- results

## A new texture with the painted pixels (the painter can keep drawing afterwards). Painted in
## units, it measures w / unit texels across. Its pixels arrive after the next rendered frame
## (until then the texture shows the painter's viewport directly).
func to_texture(smooth: bool = true) -> PaTexture:
	var s := maxi(1, int(unit))
	var t := PaTexture.new(w / s, h / s, null, s)
	t.smooth = smooth
	t.premultiplied = true
	var job := PaintJob.create(self, false)
	if job != null:
		t.set_gpu(job.texture())
		PaintPump.queue(job, t)
	return t


## Repaints [param t] (same size) with what is recorded now, in place: the live texture shows the
## painter's viewport (no read-back).
func update(t: PaTexture) -> void:
	if _job == null or not is_instance_valid(_job):
		_job = PaintJob.create(self, true)
		if _job == null:
			return
		t.set_gpu(_job.texture())
	else:
		_job.rebuild(self)
	t.touch_live()


## Frees the painter's viewport (a live painter that is no longer needed).
func recycle() -> void:
	if _job != null and is_instance_valid(_job):
		_job.queue_free()
	_job = null


## Paints a texture that code maps as [param tw] × [param th] texels at [param scale] times that
## resolution: [param draw] (called with the painter) works in texel units.
static func paint_texture(tw: int, th: int, scale: int, draw: Callable) -> PaTexture:
	var tp := TexPaint.new(tw * scale, th * scale)
	tp.use_units(scale)
	draw.call(tp)
	return tp.to_texture()
