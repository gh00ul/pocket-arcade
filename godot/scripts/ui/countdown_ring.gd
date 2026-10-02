class_name CountdownRing
extends UiView
## UiParts.kt CountdownRing: a ring that fills clockwise from the top as its fraction (0..1)
## grows, its head glowing, with content (a number, an icon) in the middle. Its size is the ring's
## diameter in dp; add the content as a child (it is centred).

var color := Color.WHITE
var thickness := 7.0
var fraction := 0.0
var _shown := UiAnim.Animatable.new(0.0)


## CountdownRing(fraction, color, size, modifier, thickness, content).
static func make(p_fraction: float, p_color: Color, p_size: float, p_thickness: float = 7.0) -> CountdownRing:
	var r := CountdownRing.new()
	r.color = p_color
	r.thickness = p_thickness
	r.width_dp = p_size
	r.height_dp = p_size
	r.content_align = Vector2(0.0, 0.0)
	r.fraction = clampf(p_fraction, 0.0, 1.0)
	r._shown.snap_to(r.fraction)
	return r


func set_fraction(f: float) -> void:
	var target := clampf(f, 0.0, 1.0)
	if target == fraction:
		return
	fraction = target
	UiParts.animate_progress(_shown, target)
	wake()
	queue_redraw()


func shown() -> float:
	return _shown.value


func _animate(dt: float) -> bool:
	if _shown.is_running():
		_shown.step(dt)
		queue_redraw()
		return true
	return false


func _paint(ds: DrawScope) -> void:
	var t := thickness
	var d := minf(size.x, size.y) - t
	var tl := Vector2((size.x - d) / 2.0, (size.y - d) / 2.0)
	var c := size / 2.0
	ds.draw_circle(UiColors.well, d / 2.0 + t / 2.0, c)
	ds.draw_arc(UiColors.well_edge, 0.0, 360.0, false, tl, Vector2(d, d), 1.0, t)
	var shown := _shown.value
	if shown <= 0.003:
		return
	# The gradient runs round from the arc's start (turned up to 12 o'clock), dim to bright at its head.
	var dim := Widgets.shade(color, 0.7)
	var bright := Widgets.lift(color, 0.22)
	var head_at := maxf(shown, 0.02)
	var color_at := func(angle_deg: float) -> Color:
		var u := fposmod(angle_deg + 90.0, 360.0) / 360.0
		if u >= head_at:
			return bright
		return dim.lerp(bright, u / head_at)
	# The round cap behind the start reaches back past 12 o'clock, where the sweep is already bright.
	var start := c + Vector2(0.0, -d / 2.0)
	ds._apply()
	ds.ci.draw_circle(start, t * 0.5, bright, true, -1.0, true)
	UiDraw.sweep_arc(ds, tl, Vector2(d, d), -90.0, 360.0 * shown, t, color_at, false)
	var a := deg_to_rad(-90.0 + 360.0 * shown)
	var head := Vector2(c.x + cos(a) * d / 2.0, c.y + sin(a) * d / 2.0)
	ds._apply()
	ds.ci.draw_circle(head, t * 0.5, bright, true, -1.0, true)
	# The head of the arc catches the light.
	UiTheme.glow_circle(ds, color, head, t * 0.5, t * 1.2, 0.4)
	ds.draw_circle(Color(1, 1, 1, 0.85), t * 0.22, head)
