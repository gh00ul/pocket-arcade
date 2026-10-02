class_name CanvasPainter
extends Painter
## engine/r3d/TexPaint.kt CanvasPainter (a new file in the hall's folder: the hall's live screens
## are its only user): a [Painter] over a [TexPaint], so games' attract-mode drawings (in coarse
## "art pixel" units) come out as clean, anti-aliased shapes and real text at [member scale] texels
## per unit. Colours are ARGB ints or Godot Colors, as Painter takes them.

var tp: TexPaint
var scale: float


func _init(p_tp: TexPaint, p_scale: float) -> void:
	tp = p_tp
	scale = p_scale


func fill(x: float, y: float, w: float, h: float, color: Variant, alpha: float = 1.0) -> void:
	tp.rect(x * scale, y * scale, w * scale, h * scale, _with_alpha(color, alpha))


func disc(cx: float, cy: float, r: float, color: Variant, alpha: float = 1.0) -> void:
	tp.circle(cx * scale, cy * scale, r * scale, _with_alpha(color, alpha))


func frame(x: float, y: float, w: float, h: float, color: Variant, alpha: float = 1.0) -> void:
	var s := scale * 0.6
	tp.paint.reset()
	tp.paint.style = PaPaint.Style.STROKE
	tp.paint.stroke_width = s
	tp.paint.color = _with_alpha(color, alpha)
	tp.c_draw_rect(x * scale + s / 2.0, y * scale + s / 2.0, (x + w) * scale - s / 2.0, (y + h) * scale - s / 2.0, tp.paint)


func text(s: String, x: float, y: float, color: Variant, tiny: bool = false, alpha: float = 1.0, size: float = 1.0) -> void:
	var size_px := (6.2 if tiny else 8.5) * size * scale
	tp.text(s, x * scale, y * scale + size_px * 0.78, size_px, _with_alpha(color, alpha), Fonts.heavy(), TexPaint.ALIGN_LEFT)


func text_centered(s: String, cx: float, y: float, color: Variant, tiny: bool = false, alpha: float = 1.0, size: float = 1.0) -> void:
	var size_px := (6.2 if tiny else 8.5) * size * scale
	tp.text(s, cx * scale, y * scale + size_px * 0.78, size_px, _with_alpha(color, alpha), Fonts.heavy(), TexPaint.ALIGN_CENTER)


static func _with_alpha(c: Variant, alpha: float) -> int:
	var argb: int = Pal.to_argb(c) if c is Color else (int(c) & 0xFFFFFFFF)
	var a := clampi(int(((argb >> 24) & 0xFF) * alpha), 0, 255)
	return (a << 24) | (argb & 0xFFFFFF)
