class_name TicketIcon
extends UiView
## UiIcons.kt TicketIcon: the prize ticket, [member icon_size] dp tall (1.3 times as wide), on a
## soft orange glow.

var icon_size := 24.0


## TicketIcon(size, modifier).
static func make(p_size: float) -> TicketIcon:
	var t := TicketIcon.new()
	t.icon_size = p_size
	t.width_dp = p_size * 1.3
	t.height_dp = p_size
	return t


func _paint(ds: DrawScope) -> void:
	var w := size.x * 0.95
	var c := size / 2.0
	UiTheme.glow_circle(ds, UiColors.ticket, c, w * 0.36, w * 0.26, 0.22)
	UiIcons.draw_ticket(ds, c, w)
