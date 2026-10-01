extends Node

var errors: int = 0
var root: Window

func _ready() -> void:
	root = get_tree().root
	_run.call_deferred()

func check(condition: bool, message: String) -> void:
	if not condition:
		push_error(message)
		errors += 1

func game(id: String) -> ArcadeGame:
	var script = load("res://games/%s.gd" % id)
	var instance: ArcadeGame = script.new()
	root.add_child(instance)
	instance.set_physics_process(false)
	instance.running = true
	instance.rng.seed = 408
	return instance

func _run() -> void:
	var skee = game("skeeball")
	for speed in [360.0, 460.0, 620.0]:
		skee._launch(180.0, speed, 0.0)
		for i in range(1000):
			skee.step(1.0 / 120.0)
	check(skee.score > 0, "Skee-ball rolls must reach and score on target board")
	check(skee.balls.is_empty(), "Skee-ball must resolve every ball within timeout")
	check(skee.tickets_for_score() == 1 + int(skee.score / 50), "Skee-ball payout")
	skee.pointer("down", Vector2(0.5, 0.75), 7)
	skee.cancel_input()
	check(skee.dragging == -1, "Skee-ball cancel input clears drag")
	print("PASS skee physics; score=", skee.score)
	skee.free()
	var whack = game("whack")
	var mole: Dictionary = whack.moles[4]
	var hit: Vector2 = whack.camera.unproject_position(mole.pos + Vector3(0, 0.4, 0)) / root.get_visible_rect().size
	for i in range(5):
		mole.kind = 0
		mole.phase = 2
		mole.rise = 1.0
		whack.pointer("down", hit, 1)
	check(whack.score == 52, "Whack five-hit combo scores 10+10+10+10+12")
	whack.pointer("down", hit, 1)
	check(whack.score == 52, "Whack cannot score the same mole twice")
	mole.kind = 2
	mole.phase = 2
	whack.pointer("down", hit, 1)
	check(whack.score == 22 and whack.stun > 0 and whack.combo == 0, "Bomb applies penalty, stun, combo reset")
	print("PASS whack hit, combo, bomb, duplicate-hit rules")
	whack.free()
	var claw = game("claw")
	var target = claw.pile[0]
	for p in claw.pile:
		if p.y < target.y:
			target = p
	claw.trolley_x = target.x
	claw.lucky = true
	claw._enter(1)
	for i in range(1800):
		claw.step(1.0 / 120.0)
		if claw.prizes.size() > 0:
			break
	check(claw.prizes.size() > 0 and claw.score > 0, "A centered lucky claw must deliver and collect a plush")
	check(claw.tickets_for_score() == 2 + int(claw.score / 10), "Claw payout")
	claw.pointer("down", Vector2(0.2, 0.8), 1)
	claw.cancel_input()
	check(claw.move_pointers.is_empty(), "Claw pause clears held direction")
	print("PASS claw centered lucky grab; score=", claw.score, " prizes=", claw.prizes)
	claw.free()
	var pusher = game("pusher")
	pusher._collect(2)
	check(pusher.bonus_tickets == 10 and pusher.score == 0, "Ticket bundle preserves separate bonus tickets")
	pusher._collect(1)
	check(pusher.score == 50, "Gem scores 50")
	pusher._collect(3)
	check(pusher.drops.size() == 6, "Star launches six shower coins")
	var initial: int = pusher.score
	for i in range(25):
		pusher.pointer("down", Vector2(0.25 + float(i % 5) * 0.125, 0.5), 0)
		for frame in range(100):
			pusher.step(1.0 / 120)
	for i in range(500):
		pusher.step(1.0 / 120)
	check(pusher.coins_left == 0, "Pusher allows exactly 25 paid drops")
	check(pusher.score > initial, "Pusher simulation transfers shelf movement through pile to front-edge wins")
	check(pusher.finished, "Pusher finishes after last coin settles")
	check(pusher.tickets_for_score() == 1 + int(pusher.score / 15) + pusher.bonus_tickets, "Pusher bonus payout")
	print("PASS pusher simulation; score=", pusher.score, " won=", pusher.won)
	pusher.free()
	print("Skill game tests complete: ", errors, " failures")
	get_tree().quit(1 if errors > 0 else 0)
