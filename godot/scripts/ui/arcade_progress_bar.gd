class_name ArcadeProgressBar
extends UiView
## UiParts.kt ArcadeProgressBar: a sunken track and a glossy fill in [member color] that eases to its
## fraction (0..1) and glows at its leading edge. Its description is what a screen reader says with
## the value. Fills the width, its height in dp.

var color := Color.WHITE
var fraction := 0.0
var _shown := UiAnim.Animatable.new(0.0)


## ArcadeProgressBar(fraction, color, modifier, height, description).
static func make(p_fraction: float, p_color: Color, p_height: float = 10.0, description: String = "Progress") -> ArcadeProgressBar:
	var b := ArcadeProgressBar.new()
	b.color = p_color
	b.fill_width = true
	b.height_dp = p_height
	b.access_role = DisplayServer.ROLE_PROGRESS_INDICATOR
	b.accessibility_name = description
	b.fraction = clampf(p_fraction, 0.0, 1.0)
	b._shown.snap_to(b.fraction)
	b.access_progress = b.fraction
	return b


func set_fraction(f: float) -> void:
	var target := clampf(f, 0.0, 1.0)
	if target == fraction:
		return
	fraction = target
	access_progress = target
	queue_accessibility_update()
	UiParts.animate_progress(_shown, target)
	wake()
	queue_redraw()


## What the bar shows right now.
func shown() -> float:
	return _shown.value


func _animate(dt: float) -> bool:
	if _shown.is_running():
		_shown.step(dt)
		queue_redraw()
		return true
	return false


func _paint(ds: DrawScope) -> void:
	var r := size.y / 2.0
	ds.draw_round_rect(UiColors.well, Vector2.ZERO, size, r)
	ds.draw_round_rect(UiColors.well_edge, Vector2.ZERO, size, r, 1.0, 1.0)
	var fill := size.x * _shown.value
	if fill > UiTheme.px():
		var w := maxf(fill, size.y)
		UiTheme.glow_round_rect(ds, color, Vector2.ZERO, Vector2(w, size.y), r, 5.0, 0.22)
		ds.draw_round_rect(PaBrush.vertical([Widgets.lift(color, 0.35), color, Widgets.shade(color, 0.7)], 0.0, size.y), Vector2.ZERO, Vector2(w, size.y), r)
		ds.draw_round_rect(PaBrush.vertical([Color(1, 1, 1, 0.4), Color(0, 0, 0, 0)], 0.0, size.y * 0.55),
			Vector2(r * 0.4, size.y * 0.12), Vector2(maxf(w - r * 0.8, 0.0), size.y * 0.42), r * 0.5)
