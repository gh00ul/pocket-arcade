extends PaTest
## ui/HandoffTest.kt: the title-to-hall handoff: its look as a function of progress, and its
## sequencing. Frames come as fast as the handoff asks for them, 16.7 ms of animation time apiece;
## "remove animations" is a duration scale of 0.

const FRAME_NS := 16_666_667

const Plan := TitleHandoff.HandoffPlan


class Frames:
	var now := 0
	var count := 0

	func tick() -> int:
		now += 16_666_667
		count += 1
		return now


# ---------------------------------------------------------------- the look

func test_the_title_dips_to_dark_and_the_hall_comes_up_out_of_it() -> void:
	# At rest nothing covers the title; pushed all the way in, everything is dark.
	assert_eq(0.0, Plan.dark(0.0, 0.0, false))
	assert_eq(0.0, Plan.glow(0.0, 0.0, false))
	assert_eq(1.0, Plan.dark(1.0, 0.0, false))
	# The hall has just appeared: still dark, and once settled: clear.
	assert_eq(1.0, Plan.dark(0.0, 1.0, false))
	assert_eq(0.0, Plan.dark(0.0, 0.0, false))


func test_both_sides_of_the_handoff_are_the_same_picture_so_a_loading_step_can_sit_between() -> void:
	# End of the exit (exit 1, entrance 0) and start of the entrance (entrance 1; exit is reset to 0).
	assert_eq(Plan.dark(1.0, 0.0, false), Plan.dark(0.0, 1.0, false))
	assert_near(Plan.glow(1.0, 0.0, false), Plan.glow(0.0, 1.0, false), 1e-6)
	assert_near(Plan.GLOW_PEAK, Plan.glow(1.0, 0.0, false), 1e-6)
	# The calm crossfade too.
	assert_eq(Plan.dark(1.0, 0.0, true), Plan.dark(0.0, 1.0, true))


func test_the_exit_only_darkens_and_the_entrance_only_lightens() -> void:
	var last_dark := 0.0
	var last_glow := 0.0
	for i in 201:
		var e := i / 200.0
		var d := Plan.dark(e, 0.0, false)
		var g := Plan.glow(e, 0.0, false)
		assert_true(d >= last_dark - 1e-6 and d >= 0.0 and d <= 1.0)
		assert_true(g >= last_glow - 1e-6 and g >= 0.0 and g <= Plan.GLOW_PEAK + 1e-6)
		last_dark = d
		last_glow = g
	last_dark = 1.0
	last_glow = Plan.GLOW_PEAK
	for i in range(200, 0, -1):
		var en := i / 200.0
		var d := Plan.dark(0.0, en, false)
		var g := Plan.glow(0.0, en, false)
		assert_true(d <= last_dark + 1e-6, "dark %s after %s" % [d, last_dark])
		assert_true(g <= last_glow + 1e-6, "glow %s after %s" % [g, last_glow])
		last_dark = d
		last_glow = g


func test_the_light_is_up_before_the_darkness_closes() -> void:
	# Half way through the exit the doorway is glowing but the picture isn't yet covered.
	assert_true(Plan.glow(0.5, 0.0, false) > 0.2)
	assert_eq(0.0, Plan.dark(Plan.DARK_FROM, 0.0, false))
	# And coming in, the dark lifts before the light has finished draining.
	var p := Plan.ENTRANCE_GLOW_FADE
	assert_true(Plan.dark(0.0, 1.0 - p, false) < Plan.dark(0.0, 1.0, false))


func test_a_calm_handoff_is_a_plain_crossfade_through_dark() -> void:
	for i in 21:
		assert_eq(0.0, Plan.glow(i / 20.0, 0.0, true))
		assert_eq(0.0, Plan.glow(0.0, i / 20.0, true))
	# It darkens from the first moment (no light first) and is quicker than the full version.
	assert_true(Plan.dark(0.3, 0.0, true) > 0.0)
	assert_true(Plan.CALM_EXIT_MS < Plan.EXIT_MS)
	assert_true(Plan.CALM_ENTRANCE_MS < Plan.ENTRANCE_MS)


# ---------------------------------------------------------------- the sequence

class Rec:
	var events: Array = []
	var camera: Array = []


## Plays a handoff to its end; returns [handoff, rec, frames].
func _play(calm: bool, animations_off: bool, frames: Frames = null) -> Array:
	var f := frames if frames != null else Frames.new()
	var handoff := TitleHandoff.new()
	if animations_off:
		handoff.set_duration_scale(0.0)
	var rec := Rec.new()
	var done := [false]
	handoff.run(calm,
		func() -> bool:
			rec.events.append(["gate", handoff.exit.value])
			return true,
		func() -> void: rec.events.append(["enter", handoff.hud.value, handoff.exit.value]),
		func(v: float) -> void: rec.camera.append(v),
		func() -> void:
			rec.events.append(["done"])
			done[0] = true)
	var guard := 0
	while not done[0] and guard < 10000:
		handoff.frame(f.tick())
		guard += 1
	return [handoff, rec, f]


func test_the_title_exits_fully_then_the_gate_runs_then_the_hall_is_entered() -> void:
	var r := _play(false, false)
	var handoff: TitleHandoff = r[0]
	var rec: Rec = r[1]
	# The gate sees the title pushed all the way in; the hall is entered with the HUD hidden.
	assert_eq([["gate", 1.0], ["enter", 0.0, 1.0], ["done"]], rec.events)
	assert_eq(0.0, handoff.entrance.value)
	assert_eq(0.0, handoff.exit.value)


func test_the_hall_camera_starts_pulled_back_and_settles_to_exactly_rest() -> void:
	var rec: Rec = _play(false, false)[1]
	assert_eq(1.0, rec.camera.front())
	assert_eq(0.0, rec.camera.back())
	assert_true(rec.camera.size() > 20, "many frames of easing")
	# Never goes backwards once it starts settling.
	var settling := rec.camera.slice(1)
	for i in range(1, settling.size()):
		assert_true(settling[i] <= settling[i - 1] + 1e-6)
	for v: float in rec.camera:
		assert_true(v >= 0.0 and v <= 1.0)


func test_the_whole_handoff_takes_about_two_seconds() -> void:
	var frames: Frames = _play(false, false)[2]
	var seconds := frames.now / 1e9
	var expected := (Plan.EXIT_MS + Plan.ENTRANCE_MS) / 1000.0
	assert_true(seconds >= expected and seconds <= expected + 0.4, "%s s vs %s s" % [seconds, expected])


func test_the_entrance_waits_for_the_halls_first_frames_before_its_clock_starts() -> void:
	var frames := Frames.new()
	var handoff := TitleHandoff.new()
	var frames_at_enter := [-1]
	var entrance_when_entered := [-1.0]
	var done := [false]
	handoff.run(false, Callable(), func() -> void:
		frames_at_enter[0] = frames.count
		entrance_when_entered[0] = handoff.entrance.value, Callable(), func() -> void: done[0] = true)
	var guard := 0
	while not done[0] and guard < 10000:
		handoff.frame(frames.tick())
		guard += 1
	# Entering the hall doesn't start the entrance: it is still 0 (cover held by the exit), and
	# frames keep coming (the settle wait) before it begins.
	assert_eq(0.0, entrance_when_entered[0])
	assert_true(frames.count >= frames_at_enter[0] + Plan.SETTLE_FRAMES)


func test_with_animations_switched_off_the_hall_still_ends_up_showing() -> void:
	var r := _play(false, true)
	var handoff: TitleHandoff = r[0]
	var rec: Rec = r[1]
	assert_eq(["done"], rec.events.back())
	assert_eq(0.0, handoff.entrance.value)
	assert_eq(0.0, handoff.exit.value)
	assert_eq(0.0, rec.camera.back())


func test_a_calm_handoff_never_moves_the_camera() -> void:
	var r := _play(true, false)
	var handoff: TitleHandoff = r[0]
	var rec: Rec = r[1]
	var frames: Frames = r[2]
	assert_true(rec.camera.is_empty(), "no camera pushes: %s" % str(rec.camera))
	assert_eq(0.0, handoff.entrance.value)
	var seconds := frames.now / 1e9
	assert_true(seconds < (Plan.CALM_EXIT_MS + Plan.CALM_ENTRANCE_MS) / 1000.0 + 0.3, "%s s" % seconds)


func test_the_interface_fades_in_afterwards() -> void:
	var frames := Frames.new()
	var handoff := TitleHandoff.new()
	var done := [false]
	handoff.run(false, Callable(), func() -> void: pass, Callable(), func() -> void: done[0] = true)
	var guard := 0
	while not done[0] and guard < 10000:
		handoff.frame(frames.tick())
		guard += 1
	assert_eq(0.0, handoff.hud.value)
	handoff.fade_in_hud()
	guard = 0
	while handoff.hud.running and guard < 10000:
		handoff.frame(frames.tick())
		guard += 1
	assert_eq(1.0, handoff.hud.value)
