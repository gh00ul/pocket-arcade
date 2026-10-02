class_name GlassBox
extends UiColumn
## Widgets.kt GlassBox: a frosted inset box inside a panel: glass lit from the top with a hairline
## bevel. Given a [member highlight] it takes that colour (tinted glass, a solid edge and a soft
## outer glow), for the one box on a screen that matters most. Inside an [ArcadePanel] it arrives
## with it: each box that joins while the panel is still coming in slides up a beat after the one
## before it. Children are centred across (Column(horizontalAlignment = CenterHorizontally)).
##
## `GlassBox.make()` or `GlassBox.make(UiColors.good)`.

## The highlight colour; alpha 0 for none.
var highlight := Color(0, 0, 0, 0):
	set(v):
		highlight = v
		_style()


func _init() -> void:
	super()
	h_align = 0.0
	padding = Vector4(UiSpace.md, UiSpace.md, UiSpace.md, UiSpace.md)
	claims_entrance = true
	_style()


## GlassBox(modifier, highlight); [param p_highlight] with alpha 0 means none.
static func make(p_highlight: Color = Color(0, 0, 0, 0)) -> GlassBox:
	var g := GlassBox.new()
	g.highlight = p_highlight
	return g


func has_highlight() -> bool:
	return highlight.a > 0.0


func _style() -> void:
	glow_color = highlight
	glow_corner = UiRadius.box
	glow_reach = 8.0
	glow_strength = 0.26 if has_highlight() else 0.0
	queue_redraw()


func _paint(ds: DrawScope) -> void:
	var r := UiRadius.box
	var hl := has_highlight()
	var top := Color(highlight, 0.22) if hl else UiColors.glass_hi
	var bottom := Color(highlight, 0.08) if hl else UiColors.glass_lo
	ds.draw_round_rect(PaBrush.vertical([top, bottom], 0.0, size.y), Vector2.ZERO, size, r)
	Widgets.paint_glass_bevel(ds, size, r)
	Widgets.border(ds, size, UiEdge.strong if hl else UiEdge.hair, highlight if hl else UiColors.glass_edge, r)
