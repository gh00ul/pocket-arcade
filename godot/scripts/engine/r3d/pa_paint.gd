class_name PaPaint
extends RefCounted
## android.graphics.Paint as the art code uses it with TexPaint's canvas: colour, fill or stroke,
## a gradient shader, a blur mask, a drop shadow, text settings and the two transfer modes the art
## uses (CLEAR punches a hole; DST_IN keeps only what is under the shape).

enum Style { FILL, STROKE, FILL_AND_STROKE }
enum Align { LEFT, CENTER, RIGHT }
enum Xfer { NONE, CLEAR, DST_IN }

## ARGB.
var color := 0xFF000000
var style: int = Style.FILL
var stroke_width := 0.0
var round_cap := false
var round_join := false
## A gradient (Paint.shader = LinearGradient/RadialGradient), in drawing coordinates; null for none.
var shader: PaBrush = null
## BlurMaskFilter(radius, NORMAL): 0 for none.
var blur_radius := 0.0
## setShadowLayer(radius, dx, dy, colour); radius 0 for none.
var shadow_radius := 0.0
var shadow_dx := 0.0
var shadow_dy := 0.0
var shadow_color := 0
var typeface: Font = null
var text_size := 12.0
var text_align: int = Align.LEFT
## Letter spacing in em.
var letter_spacing := 0.0
var xfer: int = Xfer.NONE


func _init(p_color: int = 0xFF000000) -> void:
	color = p_color


## Paint.reset() with anti-aliasing on.
func reset() -> PaPaint:
	color = 0xFF000000
	style = Style.FILL
	stroke_width = 0.0
	round_cap = false
	round_join = false
	shader = null
	blur_radius = 0.0
	shadow_radius = 0.0
	typeface = null
	text_size = 12.0
	text_align = Align.LEFT
	letter_spacing = 0.0
	xfer = Xfer.NONE
	return self


## Paint.alpha (0..255) on the current colour.
func set_alpha(a: int) -> void:
	color = (clampi(a, 0, 255) << 24) | (color & 0xFFFFFF)


func copy() -> PaPaint:
	var p := PaPaint.new(color)
	p.style = style
	p.stroke_width = stroke_width
	p.round_cap = round_cap
	p.round_join = round_join
	p.shader = shader
	p.blur_radius = blur_radius
	p.shadow_radius = shadow_radius
	p.shadow_dx = shadow_dx
	p.shadow_dy = shadow_dy
	p.shadow_color = shadow_color
	p.typeface = typeface
	p.text_size = text_size
	p.text_align = text_align
	p.letter_spacing = letter_spacing
	p.xfer = xfer
	return p
