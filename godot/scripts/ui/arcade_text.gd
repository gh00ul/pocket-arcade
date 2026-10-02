class_name ArcadeText
extends UiView
## Widgets.kt ArcadeText: text in the game's type, sized in grid units (capitals are 7 units tall,
## tiny ones 5), with a soft shadow. Multi-line text splits on '\n'. [member tracking] adds that
## many units of space between letters (small capitals look better spaced). [member fit] shrinks
## the text to the width it is offered instead of letting it run past its box; [member max_width]
## does the same to an explicit width. A screen reader is given the words ([method Widgets.spoken_text]).
##
## Make one with [method plain] (Kotlin's first overload) or [method styled] (the [UiText] one,
## which picks size, face and tracking together and fits by default).

var text := "":
	set(v):
		if text == v:
			return
		text = v
		_relayout_text()
var color := Color.WHITE:
	set(v):
		if color == v:
			return
		color = v
		queue_redraw()
## Grid unit in dp.
var unit := 3.0:
	set(v):
		if unit == v:
			return
		unit = v
		_relayout_text()
var shadow := true:
	set(v):
		if shadow == v:
			return
		shadow = v
		_relayout_text()
var tiny := false:
	set(v):
		if tiny == v:
			return
		tiny = v
		_relayout_text()
var centered := false:
	set(v):
		centered = v
		queue_redraw()
## The text's own alpha (Kotlin's `alpha` parameter).
var alpha := 1.0:
	set(v):
		if alpha == v:
			return
		alpha = v
		queue_redraw()
## If set (>= 0), the text shrinks (never grows) to fit this width in dp.
var max_width := -1.0:
	set(v):
		if max_width == v:
			return
		max_width = v
		_relayout_text()
var tracking := 0.0:
	set(v):
		if tracking == v:
			return
		tracking = v
		_relayout_text()
## Shrinks to the width it is offered (Modifier.shrinkToFit).
var fit := false:
	set(v):
		if fit == v:
			return
		fit = v
		ui_invalidate()

var _lines := PackedStringArray()
## Each tracked line's glyphs (empty when not tracked).
var _glyphs: Array[PackedStringArray] = []
var _widths := PackedFloat32Array()
## The unit after [member max_width] and the natural size (dp, unrounded).
var _u := 3.0
var _w := 0.0
var _h := 0.0
var _line_h := 0.0
var _pad := 0.0


func _init() -> void:
	super()
	access_role = DisplayServer.ROLE_STATIC_TEXT


## ArcadeText(text, modifier, color, unit, shadow, tiny, centered, alpha, maxWidth, tracking, fit).
static func plain(p_text: String, p_color: Color = Color.WHITE, p_unit: float = 3.0, p_shadow: bool = true, p_tiny: bool = false,
		p_centered: bool = false, p_alpha: float = 1.0, p_max_width: float = -1.0, p_tracking: float = 0.0, p_fit: bool = false) -> ArcadeText:
	var t := ArcadeText.new()
	t.color = p_color
	t.unit = p_unit
	t.shadow = p_shadow
	t.tiny = p_tiny
	t.centered = p_centered
	t.alpha = p_alpha
	t.max_width = p_max_width
	t.tracking = p_tracking
	t.fit = p_fit
	t.text = p_text
	return t


## ArcadeText(text, style, modifier, color, centered, alpha, shadow, fit): text in one of the
## menus' [param style]s, shrinking to fit the width it is offered unless [param p_fit] is off.
static func styled(p_text: String, style: UiText, p_color: Color = UiColors.text_hi, p_centered: bool = false, p_alpha: float = 1.0,
		p_shadow: bool = true, p_fit: bool = true) -> ArcadeText:
	return plain(p_text, p_color, style.unit, p_shadow, style.tiny, p_centered, p_alpha, -1.0, style.tracking, p_fit)


## Sets the style's size, face and tracking on this text.
func set_style(style: UiText) -> void:
	unit = style.unit
	tiny = style.tiny
	tracking = style.tracking


## The width of [param glyphs] set one by one with [param p_tracking] units between them, at unit [param u].
static func tracked_width(glyphs: PackedStringArray, u: float, p_tiny: bool, p_tracking: float) -> float:
	var w := 0.0
	for g in glyphs:
		w += ArcadeFont.width(g, u, p_tiny)
	return w + p_tracking * u * maxi(glyphs.size() - 1, 0)


func _line_width(i: int, u: float) -> float:
	if tracking > 0.0:
		return tracked_width(_glyphs[i], u, tiny, tracking)
	return ArcadeFont.width(_lines[i], u, tiny)


func _relayout_text() -> void:
	_lines = text.split("\n")
	_glyphs.clear()
	if tracking > 0.0:
		for l in _lines:
			var g := PackedStringArray()
			for ch in l:
				g.append(ch)
			_glyphs.append(g)
	var u := unit
	if max_width >= 0.0:
		# Width is linear in the unit, shadow pad included, so one scale fits it exactly.
		var widest := 0.0
		for i in _lines.size():
			widest = maxf(widest, _line_width(i, u))
		var natural := widest + (u * 1.2 if shadow else 0.0)
		if natural > max_width and natural > 0.0:
			u *= max_width / natural
	_u = u
	var gap := u * 3.2
	_line_h = ArcadeFont.height(u, tiny) + gap
	_pad = u * 1.2 if shadow else 0.0
	_widths.resize(_lines.size())
	var wmax := 0.0
	for i in _lines.size():
		_widths[i] = _line_width(i, u)
		wmax = maxf(wmax, _widths[i])
	_w = wmax + _pad
	_h = _lines.size() * _line_h - gap + _pad
	accessibility_name = Widgets.spoken_text(text)
	ui_invalidate()
	queue_redraw()


## The natural size in dp (Compose's Modifier.size(wDp, hDp), snapped to pixels).
func text_size() -> Vector2:
	return Vector2(px_round(_w), px_round(_h))


func _measure_content(max_w: float, max_h: float, _fixed_w: bool, _fixed_h: bool) -> Vector2:
	var nat := text_size()
	if fit:
		var d := maxf(Display.density, 0.01)
		var s := Widgets.shrink_scale(roundi(nat.x * d), Widgets.CONSTRAINTS_INFINITY if is_inf(max_w) else roundi(max_w * d))
		return Vector2(minf(px_round(nat.x * s), max_w), minf(px_round(nat.y * s), max_h))
	return Vector2(minf(nat.x, max_w), minf(nat.y, max_h))


## The scale the text is drawn at (below 1 when it was shrunk to fit).
func fit_scale() -> float:
	if not fit:
		return 1.0
	var nat := text_size()
	if nat.x <= 0.0:
		return 1.0
	var d := maxf(Display.density, 0.01)
	return Widgets.shrink_scale(roundi(nat.x * d), roundi(size.x * d))


func _paint(ds: DrawScope) -> void:
	if text.is_empty():
		return
	var s := fit_scale()
	if s < 1.0:
		ds.push()
		ds.scale_by(s, s, Vector2.ZERO)
	var u := _u
	for i in _lines.size():
		var x0 := (_w - _pad - _widths[i]) / 2.0 if centered else 0.0
		var y := i * _line_h
		if tracking <= 0.0:
			if shadow:
				ArcadeFont.draw_shadowed(ds, _lines[i], x0, y, u, color, ArcadeFont.SHADOW, alpha, tiny)
			else:
				ArcadeFont.draw(ds, _lines[i], x0, y, u, color, alpha, tiny)
		else:
			var x := x0
			for g in _glyphs[i]:
				if shadow:
					ArcadeFont.draw_shadowed(ds, g, x, y, u, color, ArcadeFont.SHADOW, alpha, tiny)
				else:
					ArcadeFont.draw(ds, g, x, y, u, color, alpha, tiny)
				x += ArcadeFont.width(g, u, tiny) + tracking * u
	if s < 1.0:
		ds.pop()
