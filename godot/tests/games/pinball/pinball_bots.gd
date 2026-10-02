extends RefCounted
## The player bots of games/pinball/PinballSimulationTest.kt (FlipperBot), shared by the pinball
## tests. Loaded with preload (no global class name).

const T := preload("res://scripts/games/pinball/pinball_table.gd")
const K := preload("res://scripts/games/pinball/pinball_tuning.gd")

const PLUNGE_X := 320.0
const PLUNGE_Y := 520.0
const FLIP_Y := 560.0


## A thumb on each flipper and one on the plunger. [member reaction] is the delay between the ball
## coming into a flipper's reach and the press, [member miss_chance] the chance of not flipping at
## all, [member lead] how far ahead (seconds) it reads the ball, [member random_flips] panic flips
## per second.
class FlipperBot:
	extends RefCounted
	var game: PinballGame
	var rng: KRandom
	var reaction: float
	var miss_chance: float
	var lead: float
	var random_flips: float
	var pull_min: float
	var hold_seconds: float
	var _next_id := 10
	var _held := PackedInt64Array([-1, -1])
	var _release_at := PackedFloat64Array([0.0, 0.0])
	var _press_at := PackedFloat64Array([-1.0, -1.0])
	var _ready_at := PackedFloat64Array([0.0, 0.0])
	var _plunge_id := -1
	var _plunge_up_at := 0.0
	var _plunge_pull := 1.0
	var _plunge_wait_until := -1.0
	var _last_t := 0.0

	func _init(p_game: PinballGame, p_rng: KRandom, p_reaction: float, p_miss_chance: float, p_lead: float,
			p_random_flips: float, p_pull_min: float, p_hold_seconds: float = 0.2) -> void:
		game = p_game
		rng = p_rng
		reaction = p_reaction
		miss_chance = p_miss_chance
		lead = p_lead
		random_flips = p_random_flips
		pull_min = p_pull_min
		hold_seconds = p_hold_seconds

	func tick(t: float, ms: int) -> void:
		var dt := t - _last_t
		_last_t = t
		# The plunger: pull down, hold a moment, let go.
		if _plunge_id < 0 and game.bot_lane_ready():
			if _plunge_wait_until < 0.0:
				_plunge_wait_until = t + 0.25 + rng.next_float() * 0.3
			if t >= _plunge_wait_until:
				_plunge_id = _next_id
				_next_id += 1
				_plunge_pull = pull_min + (1.0 - pull_min) * rng.next_float()
				_plunge_up_at = t + 0.3
				game.on_touch(TouchType.DOWN, _plunge_id, PLUNGE_X, PLUNGE_Y, ms)
				game.on_touch(TouchType.MOVE, _plunge_id, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE * _plunge_pull * 0.5, ms + 60)
				game.on_touch(TouchType.MOVE, _plunge_id, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE * _plunge_pull, ms + 120)
		if _plunge_id >= 0 and t >= _plunge_up_at:
			game.on_touch(TouchType.UP, _plunge_id, PLUNGE_X, PLUNGE_Y + K.PULL_RANGE * _plunge_pull, ms)
			_plunge_id = -1
			_plunge_wait_until = -1.0
		for side in 2:
			if _held[side] >= 0 and t >= _release_at[side]:
				game.on_touch(TouchType.UP, _held[side], _side_x(side), FLIP_Y, ms)
				_held[side] = -1
				_ready_at[side] = t + 0.08
			if _held[side] < 0 and _press_at[side] < 0.0 and t >= _ready_at[side]:
				if _ball_coming(side):
					if rng.next_float() < miss_chance:
						_ready_at[side] = t + 0.5
					else:
						_press_at[side] = t + reaction * (0.7 + 0.6 * rng.next_float())
				elif random_flips > 0.0 and rng.next_float() < random_flips * dt:
					_press_at[side] = t
			if _press_at[side] >= 0.0 and t >= _press_at[side] and _held[side] < 0:
				_press_at[side] = -1.0
				_held[side] = _next_id
				_next_id += 1
				_release_at[side] = t + hold_seconds
				game.on_touch(TouchType.DOWN, _held[side], _side_x(side), FLIP_Y, ms)

	## A ball (read [member lead] seconds ahead) is over the flipper's outer part, not rising fast.
	func _ball_coming(side: int) -> bool:
		var px := T.flip_x(side)
		var a := T.rest_angle(side)
		var ex := cos(a) * T.FLIP_LEN
		var ey := sin(a) * T.FLIP_LEN
		for i in game.bot_max_balls():
			if not game.bot_ball_in_play(i):
				continue
			var vy := game.bot_ball_vy(i)
			if vy < -150.0:
				continue
			var bx := game.bot_ball_x(i) + game.bot_ball_vx(i) * lead
			var by := game.bot_ball_y(i) + vy * lead
			var along := ((bx - px) * ex + (by - T.FLIP_Y) * ey) / (T.FLIP_LEN * T.FLIP_LEN)
			if along < 0.2 or along > 1.05:
				continue
			var cx := px + ex * along
			var cy := T.FLIP_Y + ey * along
			var dx := bx - cx
			var dy := by - cy
			# Above the bat (smaller y) and close to it.
			if dy < 6.0 and sqrt(dx * dx + dy * dy) < T.FLIP_R0 + T.BALL_R + 14.0:
				return true
		return false

	func _side_x(side: int) -> float:
		return 70.0 if side == 0 else 230.0
