class_name SimHarness
extends RefCounted
## games/SimHarness.kt: shared plumbing for the headless game tests: game services, a round runner
## that plays a round exactly the way the host does, touch helpers and the payout bands every
## machine must hit. Per-game tests use it through these static functions.

## Seconds of ENDING the host plays after a round finishes, before it reads the score.
const ENDING_SECONDS := 1.2

## Collectibles (claw plushies) won by bots, in the order they were won.
static var collected: PackedStringArray = PackedStringArray()


## Game services for headless rounds: silent sound, no vibrator.
static func fx() -> GameFx:
	return GameFx.new(SilentAudio.new(), Haptics.new(null), func(item_id: String) -> void: collected.append(item_id))


## Takes every play and play_at and does nothing (tests may read what was asked for).
class SilentAudio:
	extends RefCounted
	var played: Array = []

	func play(sfx: int, volume: float = 1.0, pitch: float = 1.0) -> void:
		played.append([sfx, volume, pitch])

	func play_at(sfx: int, x: float, z: float, volume: float = 1.0, pitch: float = 1.0) -> void:
		played.append([sfx, volume, pitch, x, z])


## Scores and tickets of a batch of rounds played by one bot.
class Stats:
	extends RefCounted
	var name: String
	var scores: Array[int] = []
	var tickets: Array[int] = []

	func _init(p_name: String) -> void:
		name = p_name

	func avg_score() -> float:
		return SimHarness.average(scores)

	func avg_tickets() -> float:
		return SimHarness.average(tickets)

	func _to_string() -> String:
		var lo: int = tickets.min() if not tickets.is_empty() else 0
		var hi: int = tickets.max() if not tickets.is_empty() else 0
		return "%-22s score %7.1f  tickets %6.1f  (min %d, max %d)" % [name, avg_score(), avg_tickets(), lo, hi]


static func average(values: Array[int]) -> float:
	if values.is_empty():
		return 0.0
	var s := 0.0
	for v in values:
		s += v
	return s / values.size()


## Drives one round of a game step by step the way the host does: [method play] is the PLAYING
## phase, [method pause] what happens on Back or the app going to the background, [method end] the
## ENDING tail.
class RoundDriver:
	extends RefCounted
	var game: MiniGame
	## Simulated seconds of PLAYING so far.
	var t := 0.0
	var time_left: float
	var steps := 0

	func _init(p_game: MiniGame, seed_value: int) -> void:
		game = p_game
		time_left = game.round_seconds
		if game is BaseMiniGame:
			(game as BaseMiniGame).fixed_seed = seed_value
		game.start(SimHarness.fx())

	## Simulated milliseconds.
	func ms() -> int:
		return int(t * 1000.0)

	## Steps PLAYING for up to [param seconds], stopping early once the game reports finished (which
	## also cancels input, as the host does when it switches to ENDING). [param bot] runs before every
	## step with (simulated seconds, simulated millis). Returns whether the round finished.
	func play(seconds: float, bot: Callable = Callable()) -> bool:
		var until := steps + int(seconds / GameLoop.FIXED_DT)
		while not game.finished() and steps < until:
			if bot.is_valid():
				bot.call(t, ms())
			time_left = maxf(time_left - GameLoop.FIXED_DT, 0.0)
			game.update(GameLoop.FIXED_DT, time_left)
			t += GameLoop.FIXED_DT
			steps += 1
		if game.finished():
			game.cancel_input()
		return game.finished()

	## The host pauses mid-round: it stops forwarding touches and tells the game.
	func pause() -> void:
		game.cancel_input()

	## The host's ENDING tail: ENDING_SECONDS of update(dt, 0) whatever the game is doing.
	func end() -> void:
		var phase_t := 0.0
		while true:
			phase_t += GameLoop.FIXED_DT
			game.update(GameLoop.FIXED_DT, 0.0)
			if phase_t > SimHarness.ENDING_SECONDS:
				break


## Plays one whole round like the host: PLAYING until the game finishes (it must, within 20 s of
## the clock running out), then ENDING, then reads score and tickets into [param stats]. Returns
## the driver, or null (after failing [param test]) if the game never finished.
static func play_round(test: PaTest, game: MiniGame, seed_value: int, stats: Stats, bot: Callable) -> RoundDriver:
	var d := RoundDriver.new(game, seed_value)
	d.play(game.round_seconds + 20.0, bot)
	test.assert_true(game.finished(), "%s never finished" % game.title)
	d.end()
	if stats != null:
		stats.scores.append(game.score)
		stats.tickets.append(game.tickets_for(game.score) + game.bonus_tickets)
	return d


## A straight-line flick ending at release, sampled like a real finger.
static func flick(game: MiniGame, id: int, x0: float, y0: float, vx: float, vy: float, ms: int) -> void:
	game.on_touch(TouchType.DOWN, id, x0, y0, ms)
	for k in range(1, 7):
		var dt := k * 0.012
		game.on_touch(TouchType.MOVE, id, x0 + vx * dt, y0 + vy * dt, ms + int(dt * 1000.0))
	game.on_touch(TouchType.UP, id, x0 + vx * 0.072, y0 + vy * 0.072, ms + 72)


## A flick with no MOVE samples (DOWN, then UP 72 ms later), so the release velocity is exactly
## ([param vx], [param vy]) and the thing being flicked stays where the finger landed.
static func flick_no_move(game: MiniGame, id: int, x0: float, y0: float, vx: float, vy: float, ms: int) -> void:
	game.on_touch(TouchType.DOWN, id, x0, y0, ms)
	game.on_touch(TouchType.UP, id, x0 + vx * 0.072, y0 + vy * 0.072, ms + 72)


## A tap: DOWN, then UP at the same spot [param hold_ms] later.
static func tap(game: MiniGame, id: int, x: float, y: float, ms: int, hold_ms: int = 40) -> void:
	game.on_touch(TouchType.DOWN, id, x, y, ms)
	game.on_touch(TouchType.UP, id, x, y, ms + hold_ms)


## Roughly normal noise with unit spread (sum of six uniforms).
static func gaussian(rng: KRandom) -> float:
	var u := 0.0
	for i in 6:
		u += rng.next_float()
	return (u - 3.0) / 0.707


## The payout bands every machine must hit: a good player averages 8..70 tickets a round, a casual
## one still gets at least 2, and skill never scores less than carelessness.
static func assert_payout_bands(test: PaTest, good: Stats, casual: Stats) -> void:
	test.assert_true(good.avg_tickets() >= 8.0, "%s pays too little" % good)
	test.assert_true(good.avg_tickets() <= 70.0, "%s pays too much" % good)
	test.assert_true(casual.avg_tickets() >= 2.0, "%s pays nothing" % casual)
	test.assert_true(good.avg_score() >= casual.avg_score(), "skill should pay: %s vs %s" % [good, casual])
