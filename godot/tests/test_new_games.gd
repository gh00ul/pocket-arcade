extends SceneTree

var failures: int = 0

func _initialize() -> void:
	call_deferred("run_tests")

func check(condition: bool, message: String) -> void:
	if not condition:
		failures += 1
		push_error(message)

func make_game(path: String) -> Node:
	var script: Script = load(path)
	var game: Node = script.new()
	root.add_child(game)
	game.set_physics_process(false)
	game.running = true
	game.rng.seed = 12345
	return game

func tap_at(game: Node, point: Vector3) -> void:
	var normalized: Vector2 = game.camera.unproject_position(point) / root.get_visible_rect().size
	game.pointer("down", normalized, 1)

func run_tests() -> void:
	root.get_node("SaveStore").data.muted = true
	var shooter: Node = make_game("res://games/shooter.gd")
	shooter._spawn_target(true)
	var gold: Dictionary = shooter.targets[0]
	gold.node.position.x = 0
	tap_at(shooter, gold.node.global_position)
	check(shooter.score == 300 and shooter.ammo == 5 and shooter.hits == 1, "Shooter gold target and ammo")
	shooter.pointer("down", Vector2(0.5, 0.95), 1)
	check(shooter.reload_left > 0, "Shooter reload input")
	shooter.step(0.8)
	check(shooter.ammo == 6, "Shooter reload completion")
	shooter._hurt()
	shooter._hurt()
	shooter._hurt()
	check(shooter.down_left > 0 and shooter.hearts == 0, "Shooter knockdown")
	shooter.step(1.7)
	check(shooter.hearts == 3, "Shooter recovery")
	shooter._build_boss()
	shooter.boss_clock = 3.0
	shooter.boss_hp = 6
	shooter.boss_cores[0].visible = true
	tap_at(shooter, shooter.boss_cores[0].global_position)
	check(shooter.cleared and shooter.finished and shooter.tickets_for_score() >= 7, "Shooter boss clear and bonus")
	var final_score: int = shooter.score
	shooter.finish()
	check(shooter.score == final_score, "Shooter tally exactly once")
	shooter.queue_free()
	await process_frame
	var pinball: Node = make_game("res://games/pinball.gd")
	pinball.pointer("down", Vector2(0.86, 0.65), 4)
	pinball.pointer("move", Vector2(0.86, 0.81), 4)
	pinball.pointer("up", Vector2(0.86, 0.81), 4)
	check(not pinball.balls[0].lane and pinball.balls[0].vel.y < -1500, "Pinball plunger launch")
	var ball: Dictionary = pinball.balls[0]
	ball.pos = Vector2(286, 572)
	ball.vel = Vector2(0, 200)
	pinball._physics_ball(ball, 0.016)
	check(ball.lane and ball.vel == Vector2.ZERO, "Pinball weak launch returns to plunger")
	pinball._launch(ball, 0.9)
	ball.pos = Vector2(100, 127)
	ball.vel = Vector2(0, 400)
	pinball._physics_ball(ball, 0.002)
	check(pinball.score >= 10 and ball.vel.y < 0, "Pinball bumper collision and score")
	for i: int in 3:
		ball.pos = Vector2(248, 248 + i * 30)
		ball.vel = Vector2(500, 0)
		pinball._physics_ball(ball, 0.002)
	check(pinball.multiplier == 2 and pinball.bank_reset > 0, "Pinball completed target bank")
	for i: int in 3:
		ball.pos = Vector2(96 + i * 40, 63)
		ball.vel = Vector2(0, 100)
		pinball._physics_ball(ball, 0.02)
	check(pinball.multiball, "Pinball three lane multiball")
	await process_frame
	check(pinball.balls.size() == 2, "Pinball multiball serves second ball")
	for i: int in 3600:
		pinball.step(1.0 / 60)
		if pinball.finished:
			break
	check(pinball.score > 0, "Pinball full simulation earns score")
	pinball.queue_free()
	await process_frame
	var fishing: Node = make_game("res://games/fishing.gd")
	fishing.pointer("down", Vector2(0.4, 0.4), 8)
	fishing.step(0.35)
	check(fishing.phase == "charge" and absf(fishing.power - 0.5) < 0.01, "Fishing charge meter")
	fishing.pointer("up", Vector2(0.4, 0.4), 8)
	check(fishing.phase == "flight", "Fishing cast release")
	fishing.step(1.0)
	check(fishing.phase == "wait", "Fishing lure landing")
	fishing.suitor = 0
	fishing.bite_left = 1.0
	fishing.pending_turn = 0.7
	fishing.step(0.016)
	check(fishing.phase == "fight" and fishing.hooked == 0, "Fishing strike hook")
	fishing.line_distance = 51
	fishing.pending_turn = 1.0
	fishing.crank_rate = 8.0
	fishing.crank_sample_time = fishing.elapsed
	fishing.step(0.016)
	check(fishing.phase == "landing", "Fishing reel reaches landing")
	var expected: int = maxi(1, int(round(fishing.fishes[0].weight * fishing.VALUE[fishing.fishes[0].species])))
	fishing.step(0.91)
	check(fishing.score == expected and fishing.phase == "idle", "Fishing species weight scoring")
	fishing.fishes[1].species = 2
	fishing.fishes[1].weight = 8.0
	fishing._hook(1)
	fishing.pull_state = "run"
	fishing.pull_left = 2.0
	for i: int in 90:
		fishing.crank_rate = 60.0
		fishing.crank_sample_time = fishing.elapsed
		fishing.step(1.0 / 60.0)
		if fishing.phase != "fight":
			break
	check(fishing.phase == "recover", "Fishing excessive tension snaps line")
	fishing.queue_free()
	await process_frame
	print("New-game checks: ", "PASS" if failures == 0 else str(failures) + " failures")
	quit(failures)
