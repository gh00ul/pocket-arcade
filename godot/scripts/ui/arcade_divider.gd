class_name ArcadeDivider
extends UiView
## UiParts.kt ArcadeDivider: a hairline rule that starts at [member color] on the left and fades away
## to the right (fills the width, [constant UiEdge.line] tall).

var color := UiColors.glass_edge:
	set(v):
		color = v
		queue_redraw()


## ArcadeDivider(color, modifier).
static func make(p_color: Color = UiColors.glass_edge) -> ArcadeDivider:
	var d := ArcadeDivider.new()
	d.color = p_color
	return d


func _init() -> void:
	super()
	fill_width = true
	height_dp = UiEdge.line


func _paint(ds: DrawScope) -> void:
	ds.draw_rect(PaBrush.horizontal([Color(color, 0.85), Color(color, 0.3), Color(0, 0, 0, 0)], 0.0, size.x), Vector2.ZERO, size)
