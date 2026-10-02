extends PaTest
## hub/HubHapticsTest.kt: the hall's haptics: a soft tick when a play prompt appears, and one bump
## (not a buzz) when the player walks into a wall or a kid, overhead and in first person.

const W := 1080.0
const H := 2400.0
const DENSITY := 2.75
const DEG := PI / 180.0
const DT := GameLoop.FIXED_DT


## Counts what the hall asks for instead of vibrating.
class Counting:
	extends Haptics
	var softs := 0
	var bumps := 0

	func soft() -> void:
		softs += 1

	func bump() -> void:
		bumps += 1


func after_each() -> void:
	FigureAnim.reduce_motion = false


func _world(haptics: Haptics, first_person: bool, kids: bool = false) -> HubWorld:
	var w := HubWorld.new(HallGames.games(), null, haptics)
	w.density = DENSITY
	w.set_viewport(W, H)
	w.set_first_person(first_person, false)
	if not kids:
		w.npcs.clear()
	return w


func _run(w: HubWorld, seconds: float) -> void:
	for i in MathUtil.round_to_int(seconds / DT):
		w.update(DT)


func _at_the_door(w: HubWorld) -> void:
	w.player.place(w.map.spawn_x, w.map.spawn_y)
	w.camera.set_look(PI, HubCamera.REST_PITCH_DEG * DEG)
	w.update(DT)


## Pushes the stick straight up the screen (forward, and up the main aisle overhead) at nearly full tilt.
func _push_up(w: HubWorld) -> void:
	w.pointer_down(1, 200.0, 1800.0)
	w.pointer_move(1, 200.0, 1800.0 - w.joystick.radius * Joystick.FULL_AT)


## Walks on until the player has been stopped for a while (or [param limit] seconds pass); returns the seconds walked.
func _walk_until_stopped(w: HubWorld, limit: float) -> float:
	var t := 0.0
	var still := 0.0
	while t < limit and still < 0.5:
		w.update(DT)
		t += DT
		still = still + DT if w.player.speed_frac < 0.05 and t > 0.5 else 0.0
	return t


func test_walking_head_on_into_the_end_of_the_aisle_bumps_once_in_first_person_and_overhead() -> void:
	for first_person: bool in [true, false]:
		var h := Counting.new()
		var w := _world(h, first_person)
		_at_the_door(w)
		h.bumps = 0
		_push_up(w)
		var walked := _walk_until_stopped(w, 40.0)
		assert_true(walked < 40.0, "never came to a stop (first person %s, %f s)" % [first_person, walked])
		assert_eq(1, h.bumps, "one bump on the stop (first person %s)" % first_person)
		# Leaning on the wall is not a stream of bumps.
		_run(w, 2.0)
		assert_eq(1, h.bumps, "still one bump after leaning on it (first person %s)" % first_person)


func test_letting_go_of_the_stick_is_not_a_bump() -> void:
	for first_person: bool in [true, false]:
		var h := Counting.new()
		var w := _world(h, first_person)
		_at_the_door(w)
		_push_up(w)
		_run(w, 0.6)
		assert_true(w.player.speed_frac > 0.5)
		w.pointer_up(1, 0.0, 0.0)
		_run(w, 0.5)
		assert_eq(0, h.bumps, "stopping by choice (first person %s)" % first_person)


func test_turning_in_the_aisle_and_walking_on_is_not_a_bump() -> void:
	var h := Counting.new()
	var w := _world(h, true)
	_at_the_door(w)
	_push_up(w)
	_run(w, 1.0)
	# Swing the stick right round to walk back the way we came: braking is gradual, not a wall.
	w.pointer_move(1, 200.0, 1800.0 + w.joystick.radius * Joystick.FULL_AT)
	_run(w, 1.0)
	assert_eq(0, h.bumps)


func test_walking_into_a_kid_bumps_once_and_a_kid_bumping_you_does_not() -> void:
	# A kid dead ahead of a walking player.
	var h := Counting.new()
	var w := _world(h, true, true)
	var kid := w.npcs[0]
	w.npcs.assign([kid])
	_at_the_door(w)
	kid.x = w.player.x
	kid.y = w.player.y - 40.0
	h.bumps = 0
	_push_up(w)
	var closest := INF
	for i in int(2.5 / DT):
		w.update(DT)
		closest = minf(closest, sqrt((kid.x - w.player.x) * (kid.x - w.player.x) + (kid.y - w.player.y) * (kid.y - w.player.y)))
	assert_true(closest < PaBody.RADIUS + HubWorld.KID_RADIUS, "never reached the kid (%f)" % closest)
	assert_true(h.bumps >= 1, "no bump for walking into a kid")
	assert_true(h.bumps <= 3, "a bump per meeting, not per step (%d)" % h.bumps)

	# A kid on top of a player who is standing still: nothing to feel.
	var g := Counting.new()
	var v := _world(g, true, true)
	v.npcs.assign([v.npcs[0]])
	_at_the_door(v)
	v.npcs[0].x = v.player.x + 4.0
	v.npcs[0].y = v.player.y + 4.0
	_run(v, 0.3)
	assert_eq(0, g.bumps)


func test_a_play_prompt_appearing_gives_one_soft_tick() -> void:
	for first_person: bool in [true, false]:
		var h := Counting.new()
		var w := _world(h, first_person)
		_at_the_door(w)
		assert_eq(0, h.softs, "nothing at the door")
		var spot: Spot = null
		for s: Spot in w.map.spots:
			if s.type == SpotType.MACHINE:
				spot = s
				break
		w.player.place(spot.area.center_x, spot.area.center_y)
		_run(w, 0.5)
		assert_eq(1, h.softs, "the prompt appeared (first person %s)" % first_person)
		# Staying put in it is not another tick.
		_run(w, 0.5)
		assert_eq(1, h.softs)
