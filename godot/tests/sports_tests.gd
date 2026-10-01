extends Node

var failures: int = 0

func _ready() -> void:
	for argument in OS.get_cmdline_user_args():
		if argument.begins_with("--preview="):
			call_deferred("preview", argument.trim_prefix("--preview="))
			return
	call_deferred("run_tests")

func preview(game_name: String) -> void:
	var game = new_game(game_name)
	if game_name == "stacker":
		for i in range(9):
			game.slide = 0.0
			game.drop_slab()
		game.update_camera(1.0)
	if game_name == "racer":
		for i in range(2 * 60):
			game.steer_target = 0.0
			game._physics_process(1.0 / 60.0)
	for frame in range(5):
		await get_tree().process_frame
	await RenderingServer.frame_post_draw
	var path: String = OS.get_environment("TEMP") + "/pocket_" + game_name + ".png"
	get_viewport().get_texture().get_image().save_png(path)
	print(path)
	get_tree().quit()

func check(condition: bool, description: String) -> void:
	if condition:
		print("PASS: ", description)
	else:
		push_error("FAIL: " + description)
		failures += 1

func new_game(name: String) -> ArcadeGame:
	var script = load("res://games/" + name + ".gd")
	var game: ArcadeGame = script.new()
	get_tree().root.add_child(game)
	game.set_physics_process(false)
	game.running = true
	game.rng.seed = 42
	return game

func run_tests() -> void:
	var was_muted: bool = SaveStore.data.muted
	SaveStore.data.muted = true
	var hoops = new_game("hoops")
	hoops.shoot(Vector2(0, -1700.0 / 640.0))
	for frame in range(150):
		hoops._physics_process(1.0 / 120.0)
	check(hoops.score == 13, "Hoops ideal flick makes a swish worth 13 points")
	check(hoops.makes == 1, "Hoops crossing the rim scores exactly once")
	hoops.time_left = 0.01
	for frame in range(20):
		hoops._physics_process(1.0 / 120.0)
	check(hoops.finished, "Hoops buzzer completes after shots settle")
	hoops.free()
	hoops = new_game("hoops")
	hoops.pointer("down",Vector2(0.5,0.8),7)
	var release_time: int = Time.get_ticks_msec()
	hoops.flick_samples[0]["time"] = release_time-1090
	hoops.record_flick(Vector2(0.5,0.8),release_time-90)
	hoops.record_flick(Vector2(0.5,0.68),release_time-45)
	hoops.pointer("up",Vector2(0.5,0.56),7)
	check(hoops.shots == 1,"Hoops one-second aim hold followed by a sharp flick launches")
	for frame in range(150):
		hoops._physics_process(1.0/120.0)
	check(hoops.score == 13,"Hoops held-then-flicked shot keeps recent flick power and swishes")
	hoops.pointer("down",Vector2(0.5,0.8),8)
	hoops.cancel_input()
	check(hoops.flick_samples.is_empty() and hoops.drag_id == -1,"Hoops cancellation clears old flick samples")
	hoops.free()

	var hockey = new_game("airhockey")
	hockey.player = Vector2(0, 1.2)
	hockey.target = Vector2(0, 0.3)
	hockey.puck = Vector2(0, 0.7)
	hockey.serve_timer = 0.0
	for frame in range(10):
		hockey._physics_process(1.0 / 120.0)
	check(hockey.velocity.y < 0.0, "Hockey mallet imparts velocity toward far goal")
	hockey.cancel_input()
	check(hockey.target == hockey.player, "Hockey cancel stops mallet chasing a lost touch")
	hockey.score = 0
	hockey.goals = 0
	hockey.cpu_goals = 0
	hockey.streak = 0
	for goal in range(7):
		hockey.goal(true)
	check(hockey.finished and hockey.goals == 7, "Hockey ends on seventh goal")
	check(hockey.score == 1525, "Hockey streak and win bonuses preserve original payout")
	hockey.free()
	hockey = new_game("airhockey")
	hockey.time_left = 0.0
	hockey.goal(true)
	check(hockey.score == 0 and hockey.goals == 0, "Hockey cannot award a goal after the buzzer")
	hockey.free()

	var stacker = new_game("stacker")
	stacker.slide = 0.0
	stacker.drop_slab()
	check(stacker.height == 1 and stacker.score == 20, "Stacker perfect drop preserves width and pays bonus")
	stacker.slide = 0.3
	stacker.drop_slab()
	var top: Dictionary = stacker.tower.back()
	var size: Vector2 = top["size"]
	check(absf(size.y - 0.9) < 0.001, "Stacker removes actual overhang from alternating axis")
	check(stacker.chunks.size() == 1, "Stacker cut piece tumbles away")
	for miss in range(3):
		stacker.slide = 1.7
		stacker.drop_slab()
	check(stacker.finished and stacker.lives == 0, "Stacker ends after three misses")
	stacker.free()

	var racer = new_game("racer")
	check(racer.cars.size() == 7, "Racer has seven independent rivals")
	check(racer.course[0].distance_to(racer.course[-1]) < 0.001, "Racer course is a closed 3D loop")
	racer.drifting = true
	racer.charge = 2.3
	racer.release_drift()
	check(racer.boost == 1.5, "Racer full drift releases a super turbo")
	for frame in range(72 * 60):
		if racer.finished:
			break
		# A competent driver maintains the inside line and releases charged drifts.
		var bend: float = racer.sample_course(racer.player_distance)["bend"]
		racer.steer_target = clampf(-bend * 0.55, -1.1, 1.1)
		if racer.charge >= 2.2:
			racer.release_drift()
		elif racer.boost <= 0.0:
			racer.drifting = true
		racer._physics_process(1.0 / 60.0)
	check(racer.finished, "Racer completes its timed round")
	check(racer.player_distance >= racer.lap_length * 3, "Racer skilled driving finishes all three laps")
	check(racer.final_place >= 1 and racer.final_place <= 8, "Racer records a valid finishing rank")
	check(racer.score > 200, "Racer awards distance and race rewards")
	print("Racer result: place=", racer.final_place, " score=", racer.score, " remaining=", racer.time_left)
	racer.free()
	SaveStore.data.muted = was_muted
	print("SPORTS TESTS: ", "PASS" if failures == 0 else str(failures) + " FAILURES")
	await get_tree().process_frame
	get_tree().quit(0 if failures == 0 else 1)
