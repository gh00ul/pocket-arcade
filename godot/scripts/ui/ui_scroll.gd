class_name UiScroll
extends UiView
## Compose's Modifier.verticalScroll (and the scrolling of LazyVerticalGrid): one child laid out at
## its full height and moved up by [member scroll], clipped to this box. A finger that moves past
## the touch slop drags it (cancelling the press of whatever button it started on, as a scrollable
## takes the pointer from a clickable in Compose), and letting go flings it with Android's spline
## deceleration (Compose's splineBasedDecay: the platform's fling curve, friction 0.015).

## How far the content is scrolled, in dp (0 at the top).
var scroll := 0.0
var _max_scroll := 0.0
var _tracking := false
var _dragging := false
var _start := Vector2.ZERO
var _last := Vector2.ZERO
var _flick := FlickTracker.new()
## A fling in progress: its start position, direction and Android fling geometry (px, ms).
var _fling := false
var _fling_from := 0.0
var _fling_sign := 1.0
var _fling_dist_px := 0.0
var _fling_ms := 0.0
var _fling_t := 0.0

## Android's fling physics (FlingCalculator, AndroidFlingSpline).
const INFLEXION := 0.35
const START_TENSION := 0.5
const END_TENSION := 1.0
const GRAVITY_EARTH := 9.80665
const INCHES_PER_METER := 39.37
const SCROLL_FRICTION := 0.015
const SPLINE_SAMPLES := 100

static var _spline_positions := PackedFloat32Array()


func _init() -> void:
	super()
	clip_contents = true
	mouse_filter = Control.MOUSE_FILTER_PASS


func _child() -> UiView:
	var kids := ui_children()
	return kids[0] if not kids.is_empty() else null


func _measure_content(max_w: float, max_h: float, _fixed_w: bool, _fixed_h: bool) -> Vector2:
	var c := _child()
	if c == null:
		return Vector2.ZERO
	var s := c.ui_measure(max_w, INF)
	return Vector2(s.x, minf(s.y, max_h))


func _layout_content(rect: Rect2) -> void:
	var c := _child()
	if c == null:
		return
	var s := c.ui_measure(rect.size.x, INF)
	_max_scroll = maxf(s.y - rect.size.y, 0.0)
	scroll = clampf(scroll, 0.0, _max_scroll)
	c.ui_place(Rect2(rect.position.x, rect.position.y - px_round(scroll), s.x, s.y))


## The furthest it can scroll.
func max_scroll() -> float:
	return _max_scroll


## Scrolls to [param y] (clamped to the content).
func scroll_to(y: float) -> void:
	var v := clampf(y, 0.0, _max_scroll)
	if v == scroll:
		return
	scroll = v
	var c := _child()
	if c != null:
		var pl := px_round(padding.x)
		var pt := px_round(padding.y)
		c.ui_place(Rect2(pl, pt - px_round(scroll), c.size.x + px_round(c.margin.x) + px_round(c.margin.z),
			c.size.y + px_round(c.margin.y) + px_round(c.margin.w)))


func _gui_input(event: InputEvent) -> void:
	var mb := event as InputEventMouseButton
	if mb != null and mb.button_index == MOUSE_BUTTON_LEFT:
		if mb.pressed:
			_tracking = true
			_dragging = false
			_fling = false
			_start = mb.position
			_last = mb.position
			_flick.reset(mb.position.x, mb.position.y, Time.get_ticks_msec())
		elif _tracking:
			_tracking = false
			if _dragging:
				_dragging = false
				_flick.add(mb.position.x, mb.position.y, Time.get_ticks_msec())
				fling(-_flick.velocity().y)
		return
	var mm := event as InputEventMouseMotion
	if mm == null or not _tracking:
		return
	if not _dragging and absf(mm.position.y - _start.y) > TOUCH_SLOP:
		_dragging = true
		var pv := UiView.pressed_view
		if pv != null and is_instance_valid(pv) and is_ancestor_of(pv):
			pv.cancel_press()
		_last = mm.position
	if _dragging:
		scroll_to(scroll - (mm.position.y - _last.y))
		_last = mm.position
		_flick.add(mm.position.x, mm.position.y, Time.get_ticks_msec())
		accept_event()


## Flings with [param velocity] dp per second (positive scrolls further down the content).
func fling(velocity: float) -> void:
	var d := maxf(Display.density, 0.01)
	var v_px := absf(velocity) * d
	if v_px < 1.0 or _max_scroll <= 0.0:
		return
	var coeff := GRAVITY_EARTH * INCHES_PER_METER * d * 160.0 * 0.84
	var l := log(INFLEXION * v_px / (SCROLL_FRICTION * coeff))
	var rate := log(0.78) / log(0.9)
	_fling_ms = 1000.0 * exp(l / (rate - 1.0))
	_fling_dist_px = SCROLL_FRICTION * coeff * exp(rate / (rate - 1.0) * l)
	_fling_sign = signf(velocity)
	_fling_from = scroll
	_fling_t = 0.0
	_fling = _fling_ms > 0.0
	wake()


func _animate(dt: float) -> bool:
	if not _fling:
		return false
	_fling_t += dt * 1000.0
	var f := minf(_fling_t / _fling_ms, 1.0)
	var y := _fling_from + _fling_sign * _fling_dist_px / maxf(Display.density, 0.01) * spline_position(f)
	scroll_to(y)
	if f >= 1.0 or scroll <= 0.0 or scroll >= _max_scroll:
		_fling = false
	return _fling


## AndroidFlingSpline.flingPosition(time).distanceCoefficient: how far along its distance a fling
## is at [param t] (0..1) of its duration.
static func spline_position(t: float) -> float:
	if _spline_positions.is_empty():
		_spline_positions = _compute_spline()
	var index := int(SPLINE_SAMPLES * t)
	if index >= SPLINE_SAMPLES:
		return 1.0
	var t_inf := float(index) / SPLINE_SAMPLES
	var t_sup := float(index + 1) / SPLINE_SAMPLES
	var d_inf := _spline_positions[index]
	var d_sup := _spline_positions[index + 1]
	var v := (d_sup - d_inf) / (t_sup - t_inf)
	return d_inf + (t - t_inf) * v


static func _compute_spline() -> PackedFloat32Array:
	var out := PackedFloat32Array()
	out.resize(SPLINE_SAMPLES + 1)
	var p1 := START_TENSION * INFLEXION
	var p2 := 1.0 - END_TENSION * (1.0 - INFLEXION)
	var x_min := 0.0
	for i in SPLINE_SAMPLES:
		var alpha := float(i) / SPLINE_SAMPLES
		var x_max := 1.0
		var x := 0.0
		var coef := 0.0
		while true:
			x = x_min + (x_max - x_min) / 2.0
			coef = 3.0 * x * (1.0 - x)
			var tx := coef * ((1.0 - x) * p1 + x * p2) + x * x * x
			if absf(tx - alpha) < 1e-5:
				break
			if tx > alpha:
				x_max = x
			else:
				x_min = x
		out[i] = coef * ((1.0 - x) * START_TENSION + x) + x * x * x
	out[SPLINE_SAMPLES] = 1.0
	return out
