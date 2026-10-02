extends SceneTree
## Times the CircleWorld hot loops of the claw machine and the coin pusher: milliseconds per
## simulation step with a full pile / deck, on this machine. Not a test. Run headless:
##   godot --headless --path godot -s res://tools/timing/sim_step_timing.gd [-- --game=pusher|claw] [--steps=1200]
## Prints, per game: the bodies in the world, ms per world.step (the physics alone) and ms per
## game.update (the whole fixed step: physics, rules, juice).

var _steps := 1200


func _initialize() -> void:
	var games: Array[String] = ["pusher", "claw"]
	for a in OS.get_cmdline_user_args():
		if a.begins_with("--game="):
			games = [a.substr(7)]
		elif a.begins_with("--steps="):
			_steps = a.substr(8).to_int()
	for g in games:
		_time(g)
	quit()


func _time(game_id: String) -> void:
	var game := GameRegistry.create(game_id) as BaseMiniGame
	if game == null:
		print("%s: not ported yet" % game_id)
		return
	game.fixed_seed = 1
	var t0 := Time.get_ticks_usec()
	game.start(GameFx.new())
	var start_ms := (Time.get_ticks_usec() - t0) / 1000.0
	var world: CircleWorld = game.get("world")
	var dt := GameLoop.FIXED_DT
	# Whole fixed steps, the way the host runs them (no input: the pile just settles / gets pushed).
	var time_left := game.round_seconds
	t0 = Time.get_ticks_usec()
	for i in _steps:
		time_left = maxf(time_left - dt, 0.01)
		game.update(dt, time_left)
	var update_ms := (Time.get_ticks_usec() - t0) / 1000.0 / _steps
	# The physics alone on the same world.
	t0 = Time.get_ticks_usec()
	for i in _steps:
		world.step(dt)
	var step_ms := (Time.get_ticks_usec() - t0) / 1000.0 / _steps
	print("%-7s bodies %3d  start() %7.1f ms  world.step %.3f ms  game.update %.3f ms  (%d steps)" % [game_id, world.bodies.size(), start_ms, step_ms, update_ms, _steps])
