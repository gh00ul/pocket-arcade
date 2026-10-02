class_name ArcadeButton
extends UiView
## Widgets.kt ArcadeButton: a glossy arcade push-button: a candy-coloured cap on a darker skirt,
## lit by a soft glow of its own colour. It sinks, shrinks a touch and brightens under your finger,
## then springs back up past rest and settles. Disabled, it is a dull slate cap that doesn't glow.
## Its label shrinks to fit if the button is narrower than the words. A screen reader hears the
## words once, as a button.
##
## `ArcadeButton.make(text, on_click, color, text_color, enabled, unit, tiny)`; size it like any
## view (`.with_width(60)`, `.fill_max_width()`).

## The skirt below the cap, in dp.
const LIP := 5.0

var text := "":
	set(v):
		text = v
		if _label != null:
			_label.text = v
		accessibility_name = Widgets.spoken_text(v)
var color := Pal.c(Pal.PINK):
	set(v):
		color = v
		queue_redraw()
var text_color := Color.WHITE:
	set(v):
		text_color = v
		_style_label()

var _label: ArcadeText
var _label_box: UiView


func _init() -> void:
	super()
	clickable = true
	press_shrink = 1.0
	content_align = Vector2(0.0, 0.0)
	_label_box = UiView.new()
	_label_box.padding = Vector4(16.0, 12.0, 16.0, 12.0)
	_label_box.access_clear = true
	add_child(_label_box)
	_label = ArcadeText.new()
	_label.fit = true
	_label.centered = true
	_label_box.add_child(_label)
	_style_label()


## ArcadeButton(text, onClick, modifier, color, textColor, enabled, unit, tiny).
static func make(p_text: String, p_on_click: Callable, p_color: Color = Pal.c(Pal.PINK), p_text_color: Color = Color.WHITE,
		p_enabled: bool = true, p_unit: float = 3.0, p_tiny: bool = false) -> ArcadeButton:
	var b := ArcadeButton.new()
	b.on_click = p_on_click
	b.color = p_color
	b.text_color = p_text_color
	b.enabled = p_enabled
	b.set_unit(p_unit, p_tiny)
	b.text = p_text
	return b


func set_unit(u: float, p_tiny: bool = false) -> void:
	_label.unit = u
	_label.tiny = p_tiny
	_label.tracking = 0.3 if p_tiny else 0.0


func label() -> ArcadeText:
	return _label


func _style_label() -> void:
	if _label == null:
		return
	_label.color = text_color if enabled else UiColors.text_off
	_label.shadow = enabled


func _on_enabled_changed() -> void:
	_style_label()


func _on_press_changed() -> void:
	super()
	_ride_label()


func _sort() -> void:
	super()
	_ride_label()


## The label rides the cap: up at rest, down when pressed.
func _ride_label() -> void:
	_label_box.set_layer(1.0, Vector2.ONE, Vector2(0.0, (-0.5 + 0.8 * press_value()) * LIP))


func _paint(ds: DrawScope) -> void:
	Widgets.button_cap(ds, size, color, enabled, LIP, press_value(), false)
