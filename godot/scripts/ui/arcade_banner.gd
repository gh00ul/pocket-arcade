class_name ArcadeBanner
extends UiView
## Widgets.kt ArcadeBanner: the short notice at the top of the screen ("DAILY BONUS", "COMING
## SOON"); an empty text hides it. It drops in with a little spring, and fades away rather than
## vanishing, keeping the words it last showed while it goes. A new text while it is up gives it a
## small pop. Reduce motion cuts straight between shown and hidden.
##
## Place it as the app does (top centre, inside the safe area, 132 dp down, 12 dp from the sides)
## and call [method set_text].

const BACKDROP := Color(0x12 / 255.0, 0x0C / 255.0, 0x22 / 255.0, 0xE6 / 255.0)

## The text it is asked to show ("" for none: Kotlin's null).
var text := ""
var _last := ""
var _shown := UiAnim.Animatable.new(0.0)
var _box: GlassBox
var _label: ArcadeText


func _init() -> void:
	super()
	_box = GlassBox.make(Pal.c(Pal.YELLOW))
	_box.claims_entrance = false
	_box.bg = func(ds: DrawScope, area: Vector2) -> void: ds.draw_round_rect(BACKDROP, Vector2.ZERO, area, UiRadius.box)
	add_child(_box)
	_label = ArcadeText.styled("", UiText.HEADING, Pal.c(Pal.YELLOW), true)
	_box.add_child(_label)
	visible = false


## Shows [param p_text], or hides the banner when it is empty.
func set_text(p_text: String) -> void:
	if p_text == text:
		return
	text = p_text
	if not text.is_empty():
		_last = text
		_label.text = text
	if not UiMotion.enabled:
		_shown.snap_to(1.0 if not text.is_empty() else 0.0)
	elif not text.is_empty():
		# Dropping in from above, or a quick re-pop if it was already up with other words.
		if _shown.value > 0.5:
			_shown.snap_to(0.82)
		_shown.animate_spring(1.0, 0.55, UiAnim.STIFFNESS_MEDIUM_LOW)
	else:
		_shown.animate_tween(0.0, 240.0, UiAnim.Easing.FAST_OUT_SLOW_IN)
	_apply()
	wake()


## How far in it is (0 gone, 1 settled).
func shown() -> float:
	return _shown.value


func _animate(dt: float) -> bool:
	var running := _shown.step(dt)
	_apply()
	return running


func _apply() -> void:
	var p := _shown.value
	visible = not text.is_empty() or p > 0.002
	var sc := 0.94 + 0.06 * p
	set_layer(clampf(p, 0.0, 1.0), Vector2(sc, sc), Vector2(0.0, -(1.0 - p) * 36.0))
