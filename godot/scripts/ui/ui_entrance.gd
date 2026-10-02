class_name UiEntrance
extends RefCounted
## Widgets.kt rememberEntrance + Modifier.enterStage: a 0..1 progress that runs once, linearly over
## [member millis], when started (at once with reduce motion), and the pieces staged on it: each
## fades in and rises into place (or pops up from 70% with an overshoot) during its own slot of
## the progress. The view it was added to ([method UiView.add_animator]) steps it every frame.

var millis := 700.0
var progress: UiAnim.Animatable
## [view, from, to, rise, pop] per staged piece.
var _stages: Array = []


func _init(p_millis: float = 700.0) -> void:
	millis = p_millis
	progress = UiAnim.Animatable.new(0.0 if UiMotion.enabled else 1.0)


## Runs the progress from 0 (or shows it finished at once with reduce motion).
func start() -> void:
	if UiMotion.enabled:
		progress.snap_to(0.0)
		progress.animate_tween(1.0, millis, UiAnim.Easing.LINEAR)
	else:
		progress.snap_to(1.0)
	apply()


func value() -> float:
	return progress.value


func is_running() -> bool:
	return progress.is_running()


## Stages [param view] on this entrance during [param from]..[param to].
func stage(view: UiView, from: float, to: float, rise: float = 16.0, pop: bool = false) -> void:
	for s in _stages:
		if s[0] == view:
			s[1] = from
			s[2] = to
			s[3] = rise
			s[4] = pop
			_apply_one(s)
			return
	var entry := [view, from, to, rise, pop]
	_stages.append(entry)
	_apply_one(entry)


func step(dt: float) -> bool:
	var running := progress.step(dt)
	apply()
	return running


## Puts every staged piece where the progress says.
func apply() -> void:
	for s in _stages:
		_apply_one(s)


func _apply_one(s: Array) -> void:
	var view: UiView = s[0]
	if not is_instance_valid(view):
		return
	var e := Widgets.stage_of(progress.value, s[1], s[2])
	var alpha := clampf(e * 2.2, 0.0, 1.0)
	var k := 1.0
	var sc := 1.0
	var ty := 0.0
	if UiMotion.enabled:
		k = MathUtil.ease_out_back(e) if s[4] else MathUtil.ease_out_cubic(e)
		ty = (1.0 - k) * float(s[3])
		if s[4]:
			sc = 0.7 + 0.3 * k
	view.set_layer(alpha, Vector2(sc, sc), Vector2(0.0, ty))
