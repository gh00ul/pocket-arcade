class_name SectionHeader
extends UiRow
## UiParts.kt SectionHeader: a small heading over a section: an accent tick, the text and a rule
## running on after it, and an optional [param trailing] note at the end.


## SectionHeader(text, color, modifier, trailing).
static func make(text: String, color: Color, trailing: String = "") -> SectionHeader:
	var h := SectionHeader.new()
	h.fill_width = true
	h.v_align = 0.0
	var tick := UiView.new()
	tick.width_dp = 4.0
	tick.height_dp = 14.0
	tick.bg = func(ds: DrawScope, area: Vector2) -> void: ds.draw_round_rect(color, Vector2.ZERO, area, minf(area.x, area.y) / 2.0)
	h.add_child(tick)
	h.add_child(UiSpacer.w(UiSpace.sm))
	h.add_child(ArcadeText.styled(text, UiText.HEADING, Widgets.lift(color, 0.15)))
	h.add_child(UiSpacer.w(UiSpace.sm))
	var rule := UiView.new()
	rule.weight = 1.0
	rule.height_dp = UiEdge.hair
	rule.bg = func(ds: DrawScope, area: Vector2) -> void:
		ds.draw_rect(PaBrush.horizontal([Color(color, 0.5), Color(0, 0, 0, 0)], 0.0, area.x), Vector2.ZERO, area)
	h.add_child(rule)
	if not trailing.is_empty():
		h.add_child(UiSpacer.w(UiSpace.sm))
		h.add_child(ArcadeText.styled(trailing, UiText.LABEL, UiColors.text_mid))
	return h
