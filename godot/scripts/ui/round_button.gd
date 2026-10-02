class_name RoundButton
extends UiView
## Widgets.kt RoundButton: a round glossy button carrying a vector [member icon], ringed by a soft
## glow of its [member color]. Screen readers say its label (by default what the icon means:
## "Close", "Profile", "Sound on"...) and call it a button.
##
## `RoundButton.make(icon, on_click, color, size, label)`.

## The skirt below the cap, in dp.
const LIP := 4.0

var icon := UiIcon.CLOSE:
	set(v):
		icon = v
		queue_redraw()
var color := Color.WHITE:
	set(v):
		color = v
		queue_redraw()


func _init() -> void:
	super()
	clickable = true
	press_shrink = 1.0


## RoundButton(icon, onClick, color, modifier, size, label); an empty [param label] means the icon's.
static func make(p_icon: int, p_on_click: Callable, p_color: Color, p_size: float = 50.0, p_label: String = "") -> RoundButton:
	var b := RoundButton.new()
	b.icon = p_icon
	b.on_click = p_on_click
	b.color = p_color
	b.width_dp = p_size
	b.height_dp = p_size + LIP
	b.accessibility_name = p_label if not p_label.is_empty() else icon_label(p_icon)
	return b


## What a screen reader calls a round button, by its icon (icons not listed here go by their name).
static func icon_label(p_icon: int) -> String:
	match p_icon:
		UiIcon.CLOSE:
			return "Close"
		UiIcon.TROPHY:
			return "Profile"
		UiIcon.SOUND:
			return "Sound on"
		UiIcon.MUTED:
			return "Sound off"
		UiIcon.EYE:
			return "Camera view, first person"
		UiIcon.CAMERA:
			return "Camera view, overhead"
	var n: String = UiIcon.NAMES[p_icon].to_lower().replace("_", " ")
	return n.substr(0, 1).to_upper() + n.substr(1)


## Changes the icon and, unless a label was given, what a screen reader says for it.
func set_icon(p_icon: int, p_label: String = "") -> void:
	icon = p_icon
	accessibility_name = p_label if not p_label.is_empty() else icon_label(p_icon)


func _paint(ds: DrawScope) -> void:
	var l := LIP
	var p := press_value()
	Widgets.button_cap(ds, size, color, true, l, p, true)
	var face_y := Widgets.press_offset(l, p)
	var c := Vector2(size.x / 2.0, face_y + (size.y - l) / 2.0)
	UiIcons.draw_ui_icon(ds, icon, c + Vector2(0.0, size.x * 0.03), size.x * 0.5, Color(0, 0, 0, 0.3))
	UiIcons.draw_ui_icon(ds, icon, c, size.x * 0.5, Color.WHITE)
