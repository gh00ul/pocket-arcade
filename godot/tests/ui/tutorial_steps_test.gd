extends PaTest
## ui/TutorialStepsTest.kt: the first-run tutorial's step machine, driven with plain snapshots of
## the hall.
##
## The two arrow tests built build-13's hall map (HubLayout.build); until the hall's port lands they
## run on a stand-in floor with the same kinds of spots (machines in banks, the prize counter at
## the back, the spawn by the doors) and check the same thing: the nearest machine, then nothing
## once standing at one, and the prize counter only on the last step.

const DT := GameLoop.FIXED_DT

var input: Tutorial.TutorialInput


func before_each() -> void:
	input = Tutorial.TutorialInput.new()


func _tick(t: Tutorial, seconds: float) -> void:
	var s := 0.0
	while s < seconds:
		t.update(DT, input)
		s += DT


## Walks [param dist] units along x in unit steps of one update each.
func _walk(t: Tutorial, dist: float, step: float = 1.0) -> void:
	var moved := 0.0
	while moved < dist:
		input.x += step
		moved += step
		t.update(DT, input)


func _finish_check(t: Tutorial) -> void:
	_tick(t, Tutorial.CHECK_SECONDS + 0.1)


# ---------------------------------------------------------------- policy

func test_only_a_player_who_has_neither_seen_it_nor_played_gets_it_by_themselves() -> void:
	assert_true(Tutorial.TutorialPolicy.should_auto_start(false, 0))
	assert_false(Tutorial.TutorialPolicy.should_auto_start(true, 0))
	assert_false(Tutorial.TutorialPolicy.should_auto_start(false, 12))
	assert_false(Tutorial.TutorialPolicy.should_auto_start(true, 12))


func test_the_saved_flag_is_the_save_formats_unlock_id() -> void:
	# Never change it: it is what existing installs have saved.
	assert_eq("tutorial_done", Tutorial.TUTORIAL_DONE)


# ---------------------------------------------------------------- walking

func test_it_starts_on_the_walk_with_nothing_done() -> void:
	var t := Tutorial.new()
	assert_eq(Tutorial.TutorialStep.WALK, t.step)
	assert_eq(Tutorial.Phase.ACTIVE, t.phase)
	assert_eq(0, t.steps_done)
	assert_eq(4, t.step_count)
	assert_false(t.finished)


func test_standing_still_does_nothing_however_long() -> void:
	var t := Tutorial.new()
	_tick(t, 60.0)
	assert_eq(Tutorial.TutorialStep.WALK, t.step)
	assert_eq(Tutorial.Phase.ACTIVE, t.phase)


func test_walking_far_enough_finishes_the_walk_then_shows_a_tick_then_moves_on() -> void:
	var t := Tutorial.new()
	_walk(t, Tutorial.WALK_DISTANCE - 5.0)
	assert_eq(Tutorial.Phase.ACTIVE, t.phase)
	_walk(t, 10.0)
	assert_eq(Tutorial.Phase.CHECK, t.phase)
	assert_eq(Tutorial.TutorialStep.WALK, t.step)
	assert_eq(1, t.steps_done)
	# The tick stays a moment, then the look begins.
	_tick(t, Tutorial.CHECK_SECONDS - 0.2)
	assert_eq(Tutorial.Phase.CHECK, t.phase)
	_tick(t, 0.4)
	assert_eq(Tutorial.TutorialStep.LOOK, t.step)
	assert_eq(Tutorial.Phase.ACTIVE, t.phase)


func test_a_jump_is_not_walking() -> void:
	var t := Tutorial.new()
	t.update(DT, input)
	# A decoration lands on the player and nudges them 60 across: not a walk.
	input.x += 60.0
	t.update(DT, input)
	assert_eq(0.0, t.walked)
	_walk(t, 30.0, 0.5)
	assert_near(30.0, t.walked, 0.5)


func test_nothing_counts_while_a_panel_has_the_screen() -> void:
	var t := Tutorial.new()
	input.blocked = true
	_walk(t, 200.0)
	assert_eq(0.0, t.walked)
	assert_eq(Tutorial.TutorialStep.WALK, t.step)


# ---------------------------------------------------------------- looking

func _to_look(t: Tutorial) -> void:
	_walk(t, Tutorial.WALK_DISTANCE + 1.0)
	_finish_check(t)
	assert_eq(Tutorial.TutorialStep.LOOK, t.step)


func test_only_a_finger_turning_the_first_person_view_counts_as_looking() -> void:
	var t := Tutorial.new()
	_to_look(t)
	# Overhead: turning does nothing.
	input.yaw = 0.0
	for i in 200:
		input.yaw += 0.02
		t.update(DT, input)
	assert_eq(0.0, t.looked)
	# First person but the view turning by itself (facing a machine): still nothing.
	input.first_person = true
	input.look_dragging = false
	for i in 200:
		input.yaw += 0.02
		t.update(DT, input)
	assert_eq(0.0, t.looked)
	# A finger dragging it does, either way round.
	input.look_dragging = true
	for i in 20:
		input.yaw -= 0.02
		t.update(DT, input)
	assert_near(0.4, t.looked, 0.02)
	assert_eq(Tutorial.TutorialStep.LOOK, t.step)
	for i in 12:
		input.yaw += 0.02
		t.update(DT, input)
	assert_eq(Tutorial.Phase.CHECK, t.phase)


func test_turning_past_the_back_of_the_compass_counts_the_short_way() -> void:
	var t := Tutorial.new()
	_to_look(t)
	input.yaw = PI - 0.1
	t.update(DT, input)
	input.first_person = true
	input.look_dragging = true
	input.yaw = -PI + 0.1
	t.update(DT, input)
	assert_near(0.2, t.looked, 0.01)


func test_looking_around_early_counts_when_the_look_step_arrives() -> void:
	var t := Tutorial.new()
	input.first_person = true
	input.look_dragging = true
	for i in 60:
		input.yaw += 0.02
		t.update(DT, input)
	# Still on the walk (nothing else done), but the look is already in the bank.
	assert_eq(Tutorial.TutorialStep.WALK, t.step)
	_walk(t, Tutorial.WALK_DISTANCE + 1.0)
	_finish_check(t)
	assert_eq(Tutorial.TutorialStep.LOOK, t.step)
	_tick(t, 0.1)
	assert_eq(Tutorial.Phase.CHECK, t.phase)


# ---------------------------------------------------------------- playing and prizes

func test_starting_a_round_settles_everything_before_it() -> void:
	var t := Tutorial.new()
	t.on_played()
	_tick(t, 0.05)
	assert_eq(Tutorial.TutorialStep.PLAY, t.step)
	assert_eq(Tutorial.Phase.CHECK, t.phase)
	_finish_check(t)
	assert_eq(Tutorial.TutorialStep.PRIZES, t.step)
	assert_eq(Tutorial.Phase.ACTIVE, t.phase)


func test_opening_the_prize_counter_finishes_the_lesson_wherever_you_were() -> void:
	var t := Tutorial.new()
	t.on_prizes_opened()
	_tick(t, 0.05)
	assert_eq(Tutorial.TutorialStep.PRIZES, t.step)
	assert_eq(Tutorial.Phase.CHECK, t.phase)
	_finish_check(t)
	assert_eq(Tutorial.Phase.OUTRO, t.phase)


func test_a_full_run_ends_with_a_thank_you_then_is_over_and_not_skipped() -> void:
	var t := Tutorial.new()
	_walk(t, Tutorial.WALK_DISTANCE + 1.0)
	_finish_check(t)
	input.first_person = true
	input.look_dragging = true
	for i in 40:
		input.yaw += 0.02
		t.update(DT, input)
	_finish_check(t)
	assert_eq(Tutorial.TutorialStep.PLAY, t.step)
	t.on_played()
	_finish_check(t)
	assert_eq(Tutorial.TutorialStep.PRIZES, t.step)
	t.on_prizes_opened()
	_tick(t, 0.05)
	assert_eq(Tutorial.Phase.CHECK, t.phase)
	_finish_check(t)
	assert_eq(Tutorial.Phase.OUTRO, t.phase)
	assert_eq("YOU'RE ALL SET!", t.text(input).title)
	_tick(t, Tutorial.OUTRO_SECONDS + 0.1)
	assert_true(t.finished)
	assert_false(t.skipped)


func test_a_round_started_while_a_panel_was_open_is_not_lost() -> void:
	var t := Tutorial.new()
	input.blocked = true
	t.on_played()
	_tick(t, 1.0)
	assert_eq(Tutorial.Phase.ACTIVE, t.phase)
	input.blocked = false
	_tick(t, 0.05)
	assert_eq(Tutorial.Phase.CHECK, t.phase)


# ---------------------------------------------------------------- skipping

func test_skipping_a_step_moves_to_the_next_and_skipping_the_last_ends_it() -> void:
	var t := Tutorial.new()
	t.skip_step()
	assert_eq(Tutorial.TutorialStep.LOOK, t.step)
	assert_eq(Tutorial.Phase.ACTIVE, t.phase)
	t.skip_step()
	t.skip_step()
	assert_eq(Tutorial.TutorialStep.PRIZES, t.step)
	assert_false(t.finished)
	t.skip_step()
	assert_true(t.finished)
	assert_true(t.skipped)


func test_skipping_everything_ends_it_at_once_from_anywhere() -> void:
	var t := Tutorial.new()
	_walk(t, Tutorial.WALK_DISTANCE + 1.0)
	assert_eq(Tutorial.Phase.CHECK, t.phase)
	t.skip_all()
	assert_true(t.finished)
	assert_true(t.skipped)
	# And it stays over.
	t.on_played()
	_tick(t, 5.0)
	assert_true(t.finished)


func test_a_step_cannot_be_skipped_while_its_tick_is_showing() -> void:
	var t := Tutorial.new()
	_walk(t, Tutorial.WALK_DISTANCE + 1.0)
	t.skip_step()
	assert_eq(Tutorial.TutorialStep.WALK, t.step)
	assert_eq(Tutorial.Phase.CHECK, t.phase)


# ---------------------------------------------------------------- words

func test_the_walk_lesson_fits_the_view_and_the_hand() -> void:
	var t := Tutorial.new()
	assert_true(t.text(input).body.contains("DRAG ANYWHERE"))
	input.first_person = true
	assert_true(t.text(input).body.contains("LEFT OF THE SCREEN"))
	input.left_handed = true
	assert_true(t.text(input).body.contains("RIGHT OF THE SCREEN"))


func test_the_look_lesson_points_at_the_eye_button_then_at_the_other_thumb() -> void:
	var t := Tutorial.new()
	t.skip_step()
	assert_true(t.text(input).body.contains("EYE BUTTON"))
	input.first_person = true
	assert_true(t.text(input).body.contains("RIGHT OF THE SCREEN"))
	input.left_handed = true
	assert_true(t.text(input).body.contains("LEFT OF THE SCREEN"))


func test_the_play_and_prize_lessons_change_once_you_are_standing_at_the_thing() -> void:
	var t := Tutorial.new()
	t.skip_step()
	t.skip_step()
	assert_eq(Tutorial.TutorialStep.PLAY, t.step)
	assert_true(t.text(input).body.contains("WALK UP"))
	input.at_machine = true
	assert_true(t.text(input).body.contains("PLAY BUBBLE"))
	t.skip_step()
	assert_true(t.text(input).body.contains("BACK OF THE HALL"))
	input.at_prizes = true
	assert_true(t.text(input).body.contains("SHOP"))


func test_a_finished_step_says_nice_with_a_tick() -> void:
	var t := Tutorial.new()
	_walk(t, Tutorial.WALK_DISTANCE + 1.0)
	var text := t.text(input)
	assert_eq("NICE!", text.title)
	assert_true(text.check)


# ---------------------------------------------------------------- the arrow

## A hall spot as the tutorial reads it (hub/HubMap.kt Spot and Box).
class FakeBox:
	var left: float
	var top: float
	var right: float
	var bottom: float

	func _init(l: float, t: float, r: float, b: float) -> void:
		left = l
		top = t
		right = r
		bottom = b


class FakeSpot:
	var type: int
	var area: FakeBox

	func _init(p_type: int, p_area: FakeBox) -> void:
		type = p_type
		area = p_area


## The stand-in floor: the spawn by the doors (build-13's 304, FRONT_WALL - 35), the prize
## counter's spot at the back (build-13's box), and machine spots in banks down both sides.
const SPAWN_X := 304.0
const SPAWN_Y := 1045.0
const SPOT_TOKENS := 1


func _spots() -> Array:
	var s: Array = []
	s.append(FakeSpot.new(Tutorial.SPOT_PRIZES, FakeBox.new(262.0, 94.0, 346.0, 124.0)))
	s.append(FakeSpot.new(SPOT_TOKENS, FakeBox.new(40.0, 900.0, 90.0, 930.0)))
	for i in 6:
		s.append(FakeSpot.new(Tutorial.SPOT_MACHINE, FakeBox.new(60.0, 200.0 + i * 130.0, 110.0, 226.0 + i * 130.0)))
		s.append(FakeSpot.new(Tutorial.SPOT_MACHINE, FakeBox.new(498.0, 230.0 + i * 130.0, 548.0, 256.0 + i * 130.0)))
	return s


func test_the_arrow_points_at_the_nearest_machine_until_you_stand_at_one() -> void:
	var spots := _spots()
	var t := Tutorial.new()
	t.skip_step()
	t.skip_step()
	input.x = SPAWN_X
	input.y = SPAWN_Y
	var target: Variant = t.guide_target(spots, input)
	assert_not_null(target)
	assert_eq(Tutorial.SPOT_MACHINE, (target as FakeSpot).type)
	var best: FakeSpot = null
	var best_d := INF
	for sp: FakeSpot in spots:
		if sp.type != Tutorial.SPOT_MACHINE:
			continue
		var cx := (sp.area.left + sp.area.right) / 2.0
		var cy := (sp.area.top + sp.area.bottom) / 2.0
		var d := (cx - input.x) * (cx - input.x) + (cy - input.y) * (cy - input.y)
		if d < best_d:
			best_d = d
			best = sp
	assert_true(target == best)
	input.at_machine = true
	assert_null(t.guide_target(spots, input))


func test_the_arrow_points_at_the_prize_counter_on_the_last_step_only() -> void:
	var spots := _spots()
	var t := Tutorial.new()
	assert_null(t.guide_target(spots, input))
	t.skip_step()
	assert_null(t.guide_target(spots, input))
	t.skip_step()
	t.skip_step()
	assert_eq(Tutorial.TutorialStep.PRIZES, t.step)
	var target: Variant = t.guide_target(spots, input)
	assert_eq(Tutorial.SPOT_PRIZES, (target as FakeSpot).type)
	input.at_prizes = true
	assert_null(t.guide_target(spots, input))
	input.at_prizes = false
	t.on_prizes_opened()
	_tick(t, 0.05)
	assert_null(t.guide_target(spots, input), "no arrow while the tick shows")


# ---------------------------------------------------------------- where the arrow is drawn

var out := PackedFloat32Array([0.0, 0.0, 0.0])


func _place(vx: float, vy: float, vz: float) -> bool:
	return Tutorial.GuideMarker.place(vx, vy, vz, 1000.0, 540.0, 1200.0, 8.0, 1080.0, 2400.0, 60.0, 300.0, 2000.0, out)


func test_a_point_in_view_gets_the_marker_on_it_pointing_down() -> void:
	assert_true(_place(50.0, 20.0, 400.0))
	assert_near(540.0 + 50.0 / 400.0 * 1000.0, out[0], 0.01)
	assert_near(1200.0 - 20.0 / 400.0 * 1000.0, out[1], 0.01)
	assert_near(PI / 2.0, out[2], 1e-5)


func test_a_point_off_to_the_right_gets_an_arrow_on_the_right_edge_pointing_right() -> void:
	assert_false(_place(2000.0, 0.0, 400.0))
	assert_near(1080.0 - 60.0, out[0], 0.01)
	assert_near(0.0, out[2], 0.05)
	assert_true(out[1] >= 300.0 and out[1] <= 2000.0)


func test_a_point_far_above_gets_an_arrow_along_the_top_pointing_up() -> void:
	assert_false(_place(0.0, 5000.0, 400.0))
	assert_near(300.0, out[1], 0.01)
	assert_near(-PI / 2.0, out[2], 0.05)


func test_a_point_behind_you_gets_an_arrow_along_the_bottom_on_its_side() -> void:
	assert_false(_place(-80.0, 0.0, -300.0))
	assert_near(2000.0, out[1], 0.01)
	assert_true(out[0] < 540.0, "on the left %s" % out[0])
	assert_false(_place(80.0, 0.0, -300.0))
	assert_true(out[0] > 540.0, "on the right %s" % out[0])
	# Dead behind: the middle of the bottom edge.
	assert_false(_place(0.0, 0.0, -300.0))
	assert_near(540.0, out[0], 0.5)
	assert_near(2000.0, out[1], 0.01)


func test_the_marker_never_leaves_the_screen_wherever_the_point_is() -> void:
	var vx := -3000.0
	while vx <= 3000.0:
		var vy := -3000.0
		while vy <= 3000.0:
			for vz in [-500.0, 3.0, 100.0, 900.0]:
				_place(vx, vy, vz)
				assert_true(out[0] >= 59.9 and out[0] <= 1020.1, "x %s for (%s, %s, %s)" % [out[0], vx, vy, vz])
				assert_true(out[1] >= 299.9 and out[1] <= 2000.1, "y %s for (%s, %s, %s)" % [out[1], vx, vy, vz])
			vy += 500.0
		vx += 500.0
