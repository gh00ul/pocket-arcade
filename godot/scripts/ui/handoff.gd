class_name TitleHandoff
extends RefCounted
## ui/Handoff.kt: the title-to-hall handoff. This class is Kotlin's TitleHandoff: it drives the
## handoff, [member exit] carrying the title away, [member entrance] easing the hall in, and
## [member hud] bringing the interface up afterwards. [TitleHandoff.HandoffPlan] is its timing and
## look as pure functions of the two progress values, and [TitleHandoff.HandoffWash] draws the dip.
##
## build-13 ran the sequence in one coroutine; here [method run] starts it and [method frame] is
## called once a frame with the frame's time (nanoseconds). The animations are [FrameAnim]s, which
## step exactly as Compose's did, so the handoff takes the same frames.

## 0 = the title at rest, 1 = pushed all the way in.
var exit := FrameAnim.new(0.0)
## 1 = the hall has just appeared, 0 = settled.
var entrance := FrameAnim.new(0.0)
## The interface's opacity: held at 0 through the handoff, then eased up by [method fade_in_hud].
var hud := FrameAnim.new(1.0)

enum _Phase { IDLE, EXIT, GATE, SETTLE, ENTRANCE, DONE }

var _phase := _Phase.IDLE
var _calm := false
var _gate: Callable
var _enter_hall: Callable
var _on_camera: Callable
var _on_done: Callable
var _settle_left := 0


## Android's animator duration scale for this handoff's animations (negative: the system's; 0:
## animations off, every animation lands at once).
func set_duration_scale(s: float) -> void:
	exit.duration_scale = s
	entrance.duration_scale = s
	hud.duration_scale = s


## Whether [method run]'s sequence is under way (not idle, not finished).
func running() -> bool:
	return _phase != _Phase.IDLE and _phase != _Phase.DONE


## Plays the whole handoff. The title exits ([member exit] 0 to 1), then [param gate] is asked each
## frame while the screen is fully dark (a place for a loading step: it returns true to go on), then
## [param enter_hall] switches to the hall, which eases in ([member entrance] 1 to 0, and
## [param on_camera] gets the same value each frame so the hall camera can settle with it). With
## [param calm] both ends are short crossfades and the camera is never asked to move.
## [param on_done] is called when the hall has settled.
##
## The steps run one after another: nothing races, so animations switched off still end with the
## hall showing.
func run(calm: bool, gate: Callable, enter_hall: Callable, on_camera: Callable = Callable(), on_done: Callable = Callable()) -> void:
	_calm = calm
	_gate = gate
	_enter_hall = enter_hall
	_on_camera = on_camera
	_on_done = on_done
	exit.animate_to(1.0, HandoffPlan.CALM_EXIT_MS if calm else HandoffPlan.EXIT_MS, FrameAnim.Easing.LINEAR)
	_phase = _Phase.EXIT


## Fades the interface in over [constant HandoffPlan.HUD_MS].
func fade_in_hud() -> void:
	hud.animate_to(1.0, HandoffPlan.HUD_MS)


## One frame at [param now_ns].
func frame(now_ns: int) -> void:
	# The interface's fade runs on its own (it starts after the handoff, on the next frame).
	hud.frame(now_ns)
	match _phase:
		_Phase.EXIT:
			if exit.frame(now_ns):
				_phase = _Phase.GATE
				_try_gate()
		_Phase.GATE:
			_try_gate()
		_Phase.SETTLE:
			# Frames waited after the hall first appears (its first frame builds its models and can
			# take long): the entrance clock starts after them, so the hitch can't eat the animation.
			_settle_left -= 1
			if _settle_left <= 0:
				# The cover is the same picture either side of this: dark with the doorway's warm core.
				entrance.snap_to(1.0)
				exit.snap_to(0.0)
				var calm := _calm
				var cam := _on_camera
				entrance.animate_to(0.0, HandoffPlan.CALM_ENTRANCE_MS if calm else HandoffPlan.ENTRANCE_MS, FrameAnim.Easing.FAST_OUT_SLOW_IN,
					func(v: float) -> void:
						if not calm and cam.is_valid():
							cam.call(v))
				_phase = _Phase.ENTRANCE
		_Phase.ENTRANCE:
			if entrance.frame(now_ns):
				if not _calm and _on_camera.is_valid():
					_on_camera.call(0.0)
				_phase = _Phase.DONE
				var done := _on_done
				_gate = Callable()
				_enter_hall = Callable()
				_on_camera = Callable()
				_on_done = Callable()
				if done.is_valid():
					done.call()


func _try_gate() -> void:
	if _gate.is_valid() and not bool(_gate.call()):
		return
	hud.snap_to(0.0)
	if not _calm and _on_camera.is_valid():
		_on_camera.call(1.0)
	if _enter_hall.is_valid():
		_enter_hall.call()
	_settle_left = HandoffPlan.SETTLE_FRAMES
	_phase = _Phase.SETTLE


## ui/Handoff.kt HandoffPlan: the timing and look of the title-to-hall handoff, as pure functions of
## two progress values: `exit` (0 = the title at rest, 1 = pushed all the way in) and `entrance`
## (1 = the hall has just appeared, 0 = settled). The screen dips into a warm doorway of light, then
## to dark, and the hall comes up out of the dark as that light drains away; both ends of the dip
## are the same picture (fully dark, a warm core), so a loading step can sit between them without a
## seam.
class HandoffPlan:
	## Milliseconds for the title's exit and the hall's entrance; the calm versions are quick crossfades.
	const EXIT_MS := 820
	const ENTRANCE_MS := 1150
	const CALM_EXIT_MS := 320
	const CALM_ENTRANCE_MS := 450

	## How long the interface takes to fade in once the hall has settled.
	const HUD_MS := 500

	## Frames to wait after the hall first appears before its entrance starts. Its first frame builds
	## the hall's models and can take a long time; the entrance clock starts after that, so the hitch
	## can't eat the animation and make the hall pop.
	const SETTLE_FRAMES := 2

	## The doorway light's strongest, as an opacity of its warm core.
	const GLOW_PEAK := 0.55

	## The exit's light is at full by this much of the way; the darkness closes in from [constant DARK_FROM].
	const GLOW_RISE_END := 0.85
	const DARK_FROM := 0.55

	## The entrance's darkness has lifted by this much of the way, and its light has drained by
	## [constant ENTRANCE_GLOW_FADE].
	const ENTRANCE_DARK_FADE := 0.62
	const ENTRANCE_GLOW_FADE := 0.45

	## How opaque the dark cover is. Whichever of the two is running counts: the entrance once it has begun.
	static func dark(exit_p: float, entrance_p: float, calm: bool) -> float:
		if entrance_p > 0.0:
			return 1.0 - TitleTimeline.smoothstep(0.0, ENTRANCE_DARK_FADE, 1.0 - entrance_p)
		return TitleTimeline.smoothstep(0.0, 1.0, exit_p) if calm else TitleTimeline.smoothstep(DARK_FROM, 1.0, exit_p)

	## How strong the warm doorway light at the middle of the screen is (0 to [constant GLOW_PEAK]); none when calm.
	static func glow(exit_p: float, entrance_p: float, calm: bool) -> float:
		if calm:
			return 0.0
		if entrance_p > 0.0:
			return GLOW_PEAK * (1.0 - TitleTimeline.smoothstep(0.0, ENTRANCE_GLOW_FADE, 1.0 - entrance_p))
		return GLOW_PEAK * TitleTimeline.smoothstep(0.0, GLOW_RISE_END, exit_p)


## ui/Handoff.kt HandoffWash: the dip in the handoff: a dark cover, and over it the warm light of a
## doorway at the middle of the picture (where the title camera's push is heading: the showcase's
## lens centre, hub/TitleShowcase.kt CENTER_Y). Touches pass through.
class HandoffWash:
	extends Control

	const DARK_COVER := 0xFF0B0616
	const DOORWAY_LIGHT := 0xFFFFDDB0
	## hub/TitleShowcase.kt CENTER_Y: where the title camera's lens centre sits down the picture.
	const SHOWCASE_CENTER_Y := 0.585

	var handoff: TitleHandoff
	var calm := false
	var _brush: PaBrush

	func _init(p_handoff: TitleHandoff = null) -> void:
		handoff = p_handoff
		mouse_filter = Control.MOUSE_FILTER_IGNORE
		var light := Pal.c(DOORWAY_LIGHT)
		var mid := light
		light.a = 0.9
		mid.a = 0.35
		# The glow's strength is applied as the draw's alpha; the stops keep Kotlin's 0.9 : 0.35 : 0.
		_brush = PaBrush.radial([light, mid, Color(0, 0, 0, 0)], Vector2.ZERO, 1.0)

	func _ready() -> void:
		set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)

	func _process(_delta: float) -> void:
		queue_redraw()

	func _draw() -> void:
		if handoff == null:
			return
		var e := handoff.exit.value
		var en := handoff.entrance.value
		var dark := HandoffPlan.dark(e, en, calm)
		var glow := HandoffPlan.glow(e, en, calm)
		if dark > 0.002:
			var c := Pal.c(DARK_COVER)
			c.a = clampf(dark, 0.0, 1.0)
			draw_rect(Rect2(Vector2.ZERO, size), c)
		if glow > 0.002:
			var ds := DrawScope.new(self, size)
			_brush.start = Vector2(size.x / 2.0, size.y * SHOWCASE_CENTER_Y)
			_brush.radius = maxf(size.x, size.y) * (0.25 + 0.75 * glow / HandoffPlan.GLOW_PEAK)
			ds.draw_rect(_brush, Vector2.ZERO, size, glow)
