class_name ArcadeChip
extends UiView
## UiParts.kt ArcadeChip: a small pill label: tinted glass with a coloured edge, or with
## [member filled] a solid gradient with dark type (for the one tag that must be seen, like a
## price or NEW). Its text shrinks to fit.

var color := Color.WHITE
var filled := false
var label: ArcadeText

const INK := Color(0x1B / 255.0, 0x10 / 255.0, 0x30 / 255.0, 1.0)


## ArcadeChip(text, color, modifier, filled, style).
static func make(text: String, p_color: Color, p_filled: bool = false, style: UiText = UiText.CAPTION) -> ArcadeChip:
	var c := ArcadeChip.new()
	c.color = p_color
	c.filled = p_filled
	c.padding = Vector4(7.0, 3.0, 7.0, 3.0)
	c.content_align = Vector2(0.0, 0.0)
	c.label = ArcadeText.styled(text, style, INK if p_filled else Widgets.lift(p_color, 0.3), true, 1.0, not p_filled)
	c.add_child(c.label)
	return c


func set_text(text: String) -> void:
	label.text = text


func _paint(ds: DrawScope) -> void:
	var r := UiRadius.chip
	if filled:
		ds.draw_round_rect(PaBrush.vertical([Widgets.lift(color, 0.28), Widgets.shade(color, 0.82)], 0.0, size.y), Vector2.ZERO, size, r)
	else:
		ds.draw_round_rect(PaBrush.vertical([Color(color, 0.26), Color(color, 0.1)], 0.0, size.y), Vector2.ZERO, size, r)
	Widgets.border(ds, size, UiEdge.hair, Widgets.lift(color, 0.45) if filled else Color(color, 0.7), r)
