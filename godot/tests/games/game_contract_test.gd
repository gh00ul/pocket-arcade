extends PaTest
## Godot: the game contract itself. GameFx keeps the strongest request and clears on flush (only the
## playing phase may slow a game), BaseMiniGame runs the clock, ends once settled, never lets the
## score go negative and seeds rounds reproducibly; the registry and the physics work headless.


class Dummy:
	extends BaseMiniGame
	var settled := true
	var steps := 0
	var cancels := 0

	func _init() -> void:
		id = "dummy"
		title = "DUMMY"
		marquee = "DUMMY"
		round_seconds = 2.0
		look = MiniGame.CabinetLook.new(Pal.PURPLE, Pal.YELLOW, Pal.PINK, MiniGame.CabinetShape.UPRIGHT)

	func step(_dt: float) -> void:
		steps += 1

	func is_settled() -> bool:
		return settled

	func cancel_input() -> void:
		cancels += 1

	func tickets_for(s: int) -> int:
		return s / 10


func test_game_fx_keeps_the_strongest_request_and_clears_on_flush() -> void:
	var fx := GameFx.new()
	fx.hit_stop(0.04)
	fx.hit_stop(0.09)
	fx.hit_stop(0.05)
	fx.slow_mo(0.5, 0.3)
	fx.slow_mo(0.35, 0.2)
	fx.punch(0.2)
	fx.punch(0.7)
	var time := TimeScale.new(func() -> float: return 1.0)
	assert_near(0.7, fx.flush(time), 1e-9)
	assert_true(time.frozen(), "the freeze was handed over")
	# Cleared: a second flush hands over nothing.
	assert_eq(0.0, fx.flush(time))
	# Outside the playing phase the requests are dropped, but the punch still comes back.
	fx.hit_stop(0.1)
	fx.punch(0.3)
	assert_near(0.3, fx.flush(null), 1e-9)
	# A collectible goes to the callback.
	var got: Array = []
	var fx2 := GameFx.new(null, null, func(item: String) -> void: got.append(item))
	fx2.collect("plush_cat")
	assert_eq(["plush_cat"], got)


func test_the_round_clock_and_finishing() -> void:
	var g := Dummy.new()
	g.fixed_seed = 42
	g.start(SimHarness.fx())
	assert_eq(2.0, g.time_left)
	assert_false(g.finished())
	var d := SimHarness.RoundDriver.new(g, 42)
	assert_false(d.play(1.0))
	assert_true(d.play(5.0), "finished once the clock ran out")
	assert_true(g.time_up)
	assert_true(g.steps >= 240 and g.steps <= 241, "2 s at 120 Hz (rounding may take one more step): %d" % g.steps)
	assert_eq(1, g.cancels, "input is cancelled when the round ends")
	# A game still moving doesn't finish.
	var h := Dummy.new()
	h.settled = false
	var d2 := SimHarness.RoundDriver.new(h, 1)
	assert_false(d2.play(3.0))
	h.settled = true
	assert_true(h.finished())


func test_score_never_goes_negative_and_popups_label_it() -> void:
	var g := Dummy.new()
	g.start(SimHarness.fx())
	g.add_score(30, 10.0, 10.0, Pal.WHITE)
	g.add_score(-50, 10.0, 10.0, Pal.RED)
	assert_eq(0, g.score)
	g.add_score(5, 0.0, 0.0, Pal.WHITE, "BONUS")
	assert_eq(5, g.score)
	var labels: Array = []
	for item: Array in g.popups.active():
		labels.append(item[0])
	assert_true(labels.has("+30") and labels.has("-50") and labels.has("BONUS"), str(labels))


func test_a_seeded_round_draws_the_same_numbers() -> void:
	var a := Dummy.new()
	a.fixed_seed = 7
	a.start(SimHarness.fx())
	var b := Dummy.new()
	b.fixed_seed = 7
	b.start(SimHarness.fx())
	for i in 5:
		assert_eq(a.rng.next_int(), b.rng.next_int())


func test_the_registry_lists_build_13s_machines_in_order() -> void:
	assert_eq(PackedStringArray(["claw", "whack", "skeeball", "hoops", "pusher", "airhockey", "racer", "stacker", "shooter", "pinball", "fishing"]), GameRegistry.ids())
	# Whatever is ported so far loads, with the id it is registered under.
	for g in GameRegistry.create_all():
		assert_true(GameRegistry.ids().has(g.id), g.id)
	assert_null(GameRegistry.create("nonsense"))


func test_circle_world_settles_a_pile_and_keeps_it_out_of_the_walls() -> void:
	var w := CircleWorld.new(0.0, 400.0)
	w.segments.append(CircleWorld.Segment.new(0.0, 100.0, 100.0, 100.0))
	w.segments.append(CircleWorld.Segment.new(0.0, 0.0, 0.0, 100.0))
	w.segments.append(CircleWorld.Segment.new(100.0, 0.0, 100.0, 100.0))
	var r := KRandom.new(3)
	for i in 12:
		var b := CircleWorld.Body.new(10.0 + r.next_float() * 80.0, r.next_float() * 50.0, 6.0)
		w.bodies.append(b)
	for s in 600:
		w.step(1.0 / 120.0)
	for b in w.bodies:
		assert_true(b.y <= 100.0 - b.r + 0.5, "below the floor: %f" % b.y)
		assert_true(b.x >= b.r - 0.5 and b.x <= 100.0 - b.r + 0.5, "through a wall: %f" % b.x)
		assert_true(absf(b.vy) < 20.0, "still bouncing: %f" % b.vy)
	# No two bodies overlap by much once settled.
	for i in w.bodies.size():
		for j in range(i + 1, w.bodies.size()):
			var a := w.bodies[i]
			var b := w.bodies[j]
			assert_true(Vector2(a.x - b.x, a.y - b.y).length() > a.r + b.r - 1.5)
