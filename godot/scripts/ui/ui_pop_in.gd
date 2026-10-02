class_name UiPopIn
extends RefCounted
## Widgets.kt Modifier.popIn: a view fades up from 70% scale with a springy overshoot (spring 0.5,
## stiffness 300) after a delay, again whenever its key changes. Reduce motion shows it at once.

var view: UiView
var key: Variant = null
var progress := UiAnim.Animatable.new(1.0)
var _delay := 0.0


func _init(p_view: UiView) -> void:
	view = p_view


func restart(delay_ms: float, p_key: Variant) -> void:
	key = p_key
	if UiMotion.enabled:
		progress.snap_to(0.0)
		_delay = maxf(delay_ms, 0.0) / 1000.0
		if _delay <= 0.0:
			progress.animate_spring(1.0, 0.5, 300.0)
	else:
		_delay = 0.0
		progress.snap_to(1.0)
	_apply()
	if is_instance_valid(view):
		view.wake()


func step(dt: float) -> bool:
	if _delay > 0.0:
		_delay -= dt
		if _delay <= 0.0:
			progress.animate_spring(1.0, 0.5, 300.0)
			progress.step(-_delay)
		_apply()
		return true
	var running := progress.step(dt)
	_apply()
	return running


func _apply() -> void:
	if not is_instance_valid(view):
		return
	var v := progress.value
	var alpha := clampf(v * 1.8, 0.0, 1.0)
	if UiMotion.enabled:
		var s := 0.7 + 0.3 * v
		view.set_layer(alpha, Vector2(s, s), Vector2(0.0, (1.0 - minf(v, 1.0)) * 18.0))
	else:
		view.set_layer(alpha, Vector2.ONE, Vector2.ZERO)
