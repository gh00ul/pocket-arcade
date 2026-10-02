class_name TextKit
extends RefCounted
## Android Canvas.drawText for TexPaint: a string at a fractional pixel size with letter spacing
## in em, aligned left, centre or right on [param pos] (baseline), filled or stroked. Glyphs are
## laid out one by one (exact fractional spacing; no pair kerning).

const REF_SIZE := 64
static var _adv := {}


static func _advance(font: Font, ch: String, size: float) -> float:
	var key := str(font.get_instance_id()) + ch
	var a: Variant = _adv.get(key)
	if a == null:
		a = font.get_char_size(ch.unicode_at(0), REF_SIZE).x
		_adv[key] = a
	return float(a) * size / REF_SIZE


static func width(s: String, font: Font, size: float, spacing_em: float) -> float:
	var w := 0.0
	var sp := spacing_em * size
	for i in s.length():
		w += _advance(font, s[i], size) + sp
	return w


## Draws [param s] on [param ci] whose current transform is [param xf].
static func draw(ci: CanvasItem, xf: Transform2D, s: String, pos: Vector2, p: PaPaint) -> void:
	if s.is_empty():
		return
	if TextLog.lines != null:
		TextLog.note(s)
	var font: Font = p.typeface if p.typeface != null else Fonts.display()
	var size := p.text_size
	var w := width(s, font, size, p.letter_spacing)
	var x := pos.x
	if p.text_align == PaPaint.Align.CENTER:
		x -= w / 2.0
	elif p.text_align == PaPaint.Align.RIGHT:
		x -= w
	var fsize := maxi(1, ceili(size))
	var k := size / fsize
	var col := Pal.c(p.color)
	var stroke := p.style == PaPaint.Style.STROKE
	var outline := maxi(1, roundi(p.stroke_width / k))
	var sp := p.letter_spacing * size
	for i in s.length():
		var ch := s[i]
		ci.draw_set_transform_matrix(xf * Transform2D(0.0, Vector2(k, k), 0.0, Vector2(x, pos.y)))
		if stroke:
			font.draw_char_outline(ci.get_canvas_item(), Vector2.ZERO, ch.unicode_at(0), fsize, outline, col)
		else:
			font.draw_char(ci.get_canvas_item(), Vector2.ZERO, ch.unicode_at(0), fsize, col)
		x += _advance(font, ch, size) + sp
	ci.draw_set_transform_matrix(xf)
