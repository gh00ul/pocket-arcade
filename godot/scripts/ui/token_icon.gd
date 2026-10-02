class_name TokenIcon
extends UiView
## UiIcons.kt TokenIcon: the token, [member icon_size] dp across, on a soft gold glow. With
## [member twinkle] a glint sweeps its face every few seconds (never with reduce motion).

var icon_size := 24.0
var twinkle := false:
	set(v):
		twinkle = v
		if v:
			wake()
var _time := 0.0
var _glint := UiIcons.TOKEN_GLINT_REST


## TokenIcon(size, modifier, twinkle).
static func make(p_size: float, p_twinkle: bool = false) -> TokenIcon:
	var t := TokenIcon.new()
	t.icon_size = p_size
	t.width_dp = p_size
	t.height_dp = p_size
	t.twinkle = p_twinkle
	return t


func _wants_process() -> bool:
	return twinkle


func _animate(dt: float) -> bool:
	if not twinkle:
		return false
	var g := UiIcons.TOKEN_GLINT_REST
	if UiMotion.enabled:
		_time += dt
		g = UiIcons.twinkle(fmod(_time, UiIcons.TWINKLE_MILLIS / 1000.0) / (UiIcons.TWINKLE_MILLIS / 1000.0))
	if g != _glint:
		_glint = g
		queue_redraw()
	return true


func _paint(ds: DrawScope) -> void:
	var r := minf(size.x, size.y) / 2.0 * 0.92
	var c := size / 2.0
	UiTheme.glow_circle(ds, UiColors.token, c, r, r * 0.7, 0.28)
	UiIcons.draw_token(ds, c, r, _glint if twinkle else UiIcons.TOKEN_GLINT_REST)
