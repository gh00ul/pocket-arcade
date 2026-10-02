extends RefCounted
## The player bots of games/fishing/FishingSimulationTest.kt: the reel finger (ReelFinger), the good
## angler and the casual angler (their per-round state as objects; Kotlin kept it in locals captured
## by the round's lambda), and bestFish. Loaded with preload (no global class name).

const K := preload("res://scripts/games/fishing/fishing_tuning.gd")
const CastPhase := FishingGame.CastPhase
const Pull := FishingGame.Pull
const FishMode := FishingGame.FishMode


## A finger drawing circles on the reel through the real touch path: it keeps its own angle round the
## reel's centre and sends a MOVE about every 16 ms, like a phone's touch sampling.
class ReelFinger:
	extends RefCounted
	var game: FishingGame
	var radius: float
	var id := -1
	var _angle := 0.0
	var _last_sent := 0

	func _init(p_game: FishingGame, p_radius: float = 46.0) -> void:
		game = p_game
		radius = p_radius

	func down() -> bool:
		return id >= 0

	func press(new_id: int, ms: int) -> void:
		id = new_id
		_angle = 0.0
		_last_sent = ms
		game.on_touch(TouchType.DOWN, id, _x(), _y(), ms)

	## Turns the finger at [param omega] radians per second (positive is clockwise, reeling in) for one step.
	func turn(omega: float, ms: int) -> void:
		if id < 0:
			return
		_angle += omega * GameLoop.FIXED_DT
		if ms - _last_sent >= 16:
			_last_sent = ms
			game.on_touch(TouchType.MOVE, id, _x(), _y(), ms)

	func lift(ms: int) -> void:
		if id < 0:
			return
		game.on_touch(TouchType.UP, id, _x(), _y(), ms)
		id = -1

	## The host dropped every pointer (a pause): this finger is simply gone.
	func lost() -> void:
		id = -1

	func _x() -> float:
		return FishingGame.REEL_CX + cos(_angle) * radius

	func _y() -> float:
		return FishingGame.REEL_CY + sin(_angle) * radius


## Shared helpers (inner classes can't call the outer script's functions).
class Util:
	static func hypot(x: float, y: float) -> float:
		return sqrt(x * x + y * y)

	## The fish (not the boot) worth the most, discounted by distance, clear of the boot.
	static func best_fish(game: FishingGame) -> int:
		var best := -1
		var best_score := 0.0
		var boot := FishingGame.BOOT
		for i in K.FISH_SLOTS:
			if game.bot_fish_mode(i) != FishMode.SWIM:
				continue
			var x := game.bot_fish_x(i)
			var z := game.bot_fish_z(i)
			if hypot(x - game.bot_fish_x(boot), z - game.bot_fish_z(boot)) < 75.0:
				continue
			var d := hypot(x - FishingGame.DOCK_X, z - FishingGame.DOCK_Z)
			var s := game.bot_points(game.bot_fish_species(i), game.bot_fish_weight(i)) / (1.0 + d / 250.0)
			if s > best_score:
				best_score = s
				best = i
		return best


## A good angler: casts just short of the most valuable fish it can see (clear of the old boot), keeps
## a finger on the reel, strikes quickly, and winds hard while the fish rests but almost stops when
## it thrashes and runs, watching the tension gauge. One per round; the rng runs on across rounds.
class GoodAngler:
	extends RefCounted
	var game: FishingGame
	var rng: KRandom
	var reel: ReelFinger
	var next_id := 1
	var cast_id := -1
	var want_power := 0.0
	var bite_seen := -1.0
	var wait_start := 0.0
	var omega := 0.0
	var decide_at := 0.0
	var aim := Vector2.ZERO

	func _init(p_game: FishingGame, p_rng: KRandom) -> void:
		game = p_game
		rng = p_rng
		reel = ReelFinger.new(game)

	func tick(t: float, ms: int) -> void:
		var phase := game.bot_phase()
		if phase == CastPhase.IDLE:
			if cast_id < 0 and t > 0.2:
				var target := Util.best_fish(game)
				if target >= 0:
					# Just short of the fish, so the splash doesn't scare it off.
					var fx := game.bot_fish_x(target)
					var fz := game.bot_fish_z(target)
					var d := Util.hypot(fx - FishingGame.DOCK_X, fz - FishingGame.DOCK_Z)
					var k := maxf((d - 30.0) / d, 0.0)
					aim = game.bot_aim_for(FishingGame.DOCK_X + (fx - FishingGame.DOCK_X) * k, FishingGame.DOCK_Z + (fz - FishingGame.DOCK_Z) * k)
					want_power = aim.y
					cast_id = next_id
					next_id += 1
					game.on_touch(TouchType.DOWN, cast_id, clampf(aim.x, 4.0, 356.0), 300.0, ms)
			reel.turn(0.0, ms)
		elif phase == CastPhase.CHARGE:
			if cast_id >= 0 and absf(game.bot_power() - want_power) < 0.025:
				game.on_touch(TouchType.UP, cast_id, clampf(aim.x, 4.0, 356.0), 300.0, ms)
				cast_id = -1
				wait_start = t
				bite_seen = -1.0
		elif phase == CastPhase.FLIGHT or phase == CastPhase.WAIT:
			if not reel.down():
				reel.press(next_id, ms)
				next_id += 1
			if game.bot_biting():
				if bite_seen < 0.0:
					bite_seen = t
				reel.turn(10.0 if t - bite_seen > 0.2 else 0.0, ms)
			else:
				bite_seen = -1.0
				# Nothing interested for a long while: wind in and try elsewhere.
				reel.turn(14.0 if game.bot_suitor() < 0 and t - wait_start > 7.0 else 0.0, ms)
		elif phase == CastPhase.FIGHT:
			if t >= decide_at:
				decide_at = t + 0.08 + rng.next_float() * 0.04
				var tension := game.bot_tension()
				if game.bot_fish_species(game.bot_hooked()) == 4:
					omega = 8.0
				elif tension > 0.9:
					omega = 0.0
				elif game.bot_pull() != Pull.REST:
					omega = 1.5
				elif tension > 0.75:
					omega = 5.0
				elif tension < 0.3:
					omega = 12.0
				else:
					omega = 10.0
			reel.turn(omega, ms)
		else:
			reel.turn(0.0, ms)


## A casual player: casts anywhere at whatever power, gets a finger to the reel late, strikes late,
## fidgets with the reel while waiting and winds at one speed, only now and then noticing the gauge.
class CasualAngler:
	extends RefCounted
	var game: FishingGame
	var rng: KRandom
	var reel: ReelFinger
	var next_id := 1
	var cast_id := -1
	var cast_x := 180.0
	var cast_at := 0.0
	var hold_for := 0.0
	var idle_since := -1.0
	var reel_at := 0.0
	var react_in := 0.0
	var bite_seen := -1.0
	var fidget_at := 0.0
	var wait_start := 0.0
	var omega := 8.0
	var last_phase := 0
	var check_at := 0.0

	func _init(p_game: FishingGame, p_rng: KRandom) -> void:
		game = p_game
		rng = p_rng
		reel = ReelFinger.new(game)

	func tick(t: float, ms: int) -> void:
		var phase := game.bot_phase()
		if phase == CastPhase.FIGHT and last_phase != CastPhase.FIGHT:
			omega = 3.0 + rng.next_float() * 13.0
		last_phase = phase
		if phase == CastPhase.IDLE:
			if idle_since < 0.0:
				idle_since = t + 0.3 + rng.next_float() * 0.9
			if cast_id < 0 and t >= idle_since:
				cast_id = next_id
				next_id += 1
				cast_x = 30.0 + rng.next_float() * 300.0
				cast_at = t
				hold_for = 0.25 + rng.next_float() * 1.35
				game.on_touch(TouchType.DOWN, cast_id, cast_x, 250.0 + rng.next_float() * 170.0, ms)
			reel.turn(0.0, ms)
		elif phase == CastPhase.CHARGE:
			if cast_id >= 0 and t - cast_at >= hold_for:
				game.on_touch(TouchType.UP, cast_id, cast_x, 300.0, ms)
				cast_id = -1
				idle_since = -1.0
				reel_at = t + 0.3 + rng.next_float() * 1.2
				fidget_at = t + 1.0 + rng.next_float() * 3.0 if rng.next_float() < 0.3 else -1.0
				wait_start = t
				bite_seen = -1.0
		elif phase == CastPhase.FLIGHT or phase == CastPhase.WAIT:
			idle_since = -1.0
			if not reel.down() and t >= reel_at:
				reel.press(next_id, ms)
				next_id += 1
			if game.bot_biting():
				if bite_seen < 0.0:
					bite_seen = t
					react_in = 0.35 + rng.next_float() * 0.95
				reel.turn(maxf(omega, 6.0) if t - bite_seen > react_in else 0.0, ms)
			else:
				bite_seen = -1.0
				var fidget := fidget_at > 0.0 and t >= fidget_at and t <= fidget_at + 0.3
				var bored := t - wait_start > 9.0
				reel.turn(4.0 if fidget else (10.0 if bored else 0.0), ms)
		elif phase == CastPhase.FIGHT:
			if t >= check_at:
				check_at = t + 1.0
				if game.bot_tension() > 0.95:
					omega *= 0.6
				if game.bot_tension() < 0.25:
					omega *= 1.4
				omega = clampf(omega, 2.0, 18.0)
			reel.turn(omega, ms)
		else:
			idle_since = -1.0
			reel.turn(0.0, ms)
