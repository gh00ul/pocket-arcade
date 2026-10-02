class_name CurrencyRow
extends UiRow
## Widgets.kt CurrencyRow: token and ticket counters shown side by side, a hairline between them.
## The numbers roll like an odometer whenever they change ([RollingNumber]), calling
## [member on_tick] as they pass values, and the token count breathes while it is at
## [constant Widgets.LOW_TOKENS] or under and turns red at zero. [member token_icon] and
## [member ticket_icon] are the icons, for a screen that needs to know where they are (a coin
## flying into a counter).
##
## `CurrencyRow.make(tokens, tickets, unit, on_tick)`; update it with [method set_counts].

var tokens := 0
var tickets := 0
var token_icon: TokenIcon
var ticket_icon: TicketIcon
var token_number: RollingNumber
var ticket_number: RollingNumber
var _token_group: UiRow
var _pulse_time := 0.0


## CurrencyRow(tokens, tickets, modifier, unit, onTick).
static func make(p_tokens: int, p_tickets: int, unit: float = 3.0, on_tick: Callable = Callable()) -> CurrencyRow:
	var r := CurrencyRow.new()
	r._build(p_tokens, p_tickets, unit, on_tick)
	return r


func _build(p_tokens: int, p_tickets: int, unit: float, on_tick: Callable) -> void:
	v_align = 0.0
	tokens = p_tokens
	tickets = p_tickets
	_token_group = UiRow.new()
	_token_group.v_align = 0.0
	add_child(_token_group)
	token_icon = TokenIcon.make(unit * 9.0, true)
	_token_group.add_child(token_icon)
	_token_group.add_child(UiSpacer.w(6.0))
	token_number = RollingNumber.make(p_tokens, _token_color(p_tokens), unit, on_tick)
	_token_group.add_child(token_number)
	add_child(UiSpacer.w(UiSpace.md))
	var rule := UiView.new()
	rule.width_dp = UiEdge.hair
	rule.height_dp = unit * 6.0
	rule.bg = func(ds: DrawScope, area: Vector2) -> void: ds.draw_rect(UiColors.glass_edge, Vector2.ZERO, area)
	add_child(rule)
	add_child(UiSpacer.w(UiSpace.md))
	ticket_icon = TicketIcon.make(unit * 8.0)
	add_child(ticket_icon)
	add_child(UiSpacer.w(6.0))
	ticket_number = RollingNumber.make(p_tickets, UiColors.ticket, unit, on_tick)
	add_child(ticket_number)
	wake()


static func _token_color(n: int) -> Color:
	return UiColors.bad if n == 0 else UiColors.token


## Shows new totals (the numbers roll to them).
func set_counts(p_tokens: int, p_tickets: int) -> void:
	tokens = p_tokens
	tickets = p_tickets
	token_number.color = _token_color(p_tokens)
	token_number.set_value(p_tokens)
	ticket_number.set_value(p_tickets)
	wake()


## The breathing of the low-token pulse right now (0 when not low or not moving).
func pulse_value() -> float:
	if tokens > Widgets.LOW_TOKENS or not UiMotion.enabled:
		return 0.0
	return Widgets.low_pulse(_pulse_time)


func _wants_process() -> bool:
	return tokens <= Widgets.LOW_TOKENS


func _animate(dt: float) -> bool:
	var low := tokens <= Widgets.LOW_TOKENS
	if low:
		_pulse_time += dt
	else:
		_pulse_time = 0.0
	var s := 1.0 + 0.1 * pulse_value()
	_token_group.set_layer(1.0, Vector2(s, s), Vector2.ZERO)
	return low
