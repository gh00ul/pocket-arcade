class_name ArcadeToggle
extends UiView
## UiParts.kt ArcadeToggle: a switch: a track that fills with [member accent] and a glossy knob that
## springs across. The whole 64 × 48 dp box is the touch target, and a screen reader hears its
## label and its state (a switch). [member on_change] gets the new state; show it with
## [method set_checked].

var checked := false
var accent := Pal.c(Pal.GREEN)
var on_change := Callable()
var _t := UiAnim.Animatable.new(0.0)

const KNOB_TOP := Color(1.0, 1.0, 1.0, 1.0)
const KNOB_BOTTOM := Color(0xCF / 255.0, 0xC7 / 255.0, 0xE6 / 255.0, 1.0)


## ArcadeToggle(checked, onChange, label, modifier, accent).
static func make(p_checked: bool, p_on_change: Callable, label: String, p_accent: Color = Pal.c(Pal.GREEN)) -> ArcadeToggle:
	var t := ArcadeToggle.new()
	t.checked = p_checked
	t.on_change = p_on_change
	t.accent = p_accent
	t.accessibility_name = label
	t._t.snap_to(1.0 if p_checked else 0.0)
	t.access_checked = 1 if p_checked else 0
	return t


func _init() -> void:
	super()
	width_dp = 64.0
	height_dp = 48.0
	clickable = true
	access_role = DisplayServer.ROLE_CHECK_BUTTON
	on_click = _toggle


func _toggle() -> void:
	if on_change.is_valid():
		on_change.call(not checked)


## Shows [param on] (the knob springs across: spring 0.6, medium stiffness).
func set_checked(on: bool) -> void:
	if on == checked:
		return
	checked = on
	access_checked = 1 if on else 0
	queue_accessibility_update()
	if UiMotion.enabled:
		_t.animate_spring(1.0 if on else 0.0, 0.6, UiAnim.STIFFNESS_MEDIUM)
		wake()
	else:
		_t.snap_to(1.0 if on else 0.0)
	queue_redraw()


## Where the knob is (0 off, 1 on; past them in the spring's overshoot).
func knob() -> float:
	return _t.value


func _animate(dt: float) -> bool:
	if _t.is_running():
		_t.step(dt)
		queue_redraw()
		return true
	return false


func _paint(ds: DrawScope) -> void:
	var sz := Vector2(52.0, 30.0)
	ds.push()
	ds.translate((size.x - sz.x) / 2.0, (size.y - sz.y) / 2.0)
	var r := sz.y / 2.0
	var t := _t.value
	var on := clampf(t, 0.0, 1.0)
	ds.draw_round_rect(UiColors.well, Vector2.ZERO, sz, r)
	ds.draw_round_rect(Color(accent, 0.85 * on), Vector2.ZERO, sz, r)
	UiTheme.glow_round_rect(ds, accent, Vector2.ZERO, sz, r, 6.0, 0.3 * on)
	ds.draw_round_rect(Color(1, 1, 1, 0.22), Vector2.ZERO, sz, r, 1.0, 1.0)
	var pad := 3.0
	var kr := r - pad
	var cx := pad + kr + (sz.x - pad * 2.0 - kr * 2.0) * t
	var c := Vector2(cx, r)
	ds.draw_circle(Color(0, 0, 0, 0.35), kr, c + Vector2(0.0, 1.5))
	ds.draw_circle(PaBrush.vertical([KNOB_TOP, KNOB_BOTTOM], c.y - kr, c.y + kr), kr, c)
	ds.draw_circle(Color(1, 1, 1, 0.7), kr, c, 1.0, 1.0)
	ds.pop()
