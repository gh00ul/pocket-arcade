class_name F32RoundDriver
extends RefCounted
## Godot-only test helper (games-b): drives a round the way build-13's SimHarness RoundDriver did,
## its clock included. Kotlin kept the round's time in Floats (`t += 1f/120f`, `timeLeft -=`,
## `(t * 1000f).toLong()` for the touch time), so a bot that acts "every second" fires on the same
## steps only with the same rounding. With it, seeded rounds can be compared with numbers build-13
## printed (the parity tests of hoops, air hockey and the stacker); the games themselves still run
## in 64-bit floats.

static var _buf := PackedFloat32Array([0.0])

var game: MiniGame
## Simulated seconds of PLAYING so far (a 32-bit float value).
var t := 0.0
var time_left: float
var steps := 0
## Kotlin's FIXED_DT, 1f / 120f.
var dt := f32(1.0 / 120.0)


## [param x] rounded to a 32-bit float.
static func f32(x: float) -> float:
	_buf[0] = x
	return _buf[0]


## build-13's `gaussian(rng)` in Float arithmetic: the sum of six uniforms, centred and scaled.
static func gaussian(rng: KRandom) -> float:
	var u := 0.0
	for i in 6:
		u = f32(u + rng.next_float())
	return f32(f32(u - 3.0) / f32(0.707))


func _init(p_game: MiniGame, seed_value: int) -> void:
	game = p_game
	if game is BaseMiniGame:
		(game as BaseMiniGame).fixed_seed = seed_value
	game.start(SimHarness.fx())
	time_left = game.round_seconds


## Kotlin's `ms`: `(t * 1000f).toLong()`.
func ms() -> int:
	return int(f32(t * 1000.0))


## Steps PLAYING for up to [param seconds] like build-13's RoundDriver.play; [param bot] runs
## before every step with (seconds, millis). Returns whether the round finished.
func play(seconds: float, bot: Callable = Callable()) -> bool:
	return play_steps(int(f32(f32(seconds) / dt)), bot)


## The same for up to [param n] steps.
func play_steps(n: int, bot: Callable = Callable()) -> bool:
	var until := steps + n
	while not game.finished() and steps < until:
		if bot.is_valid():
			bot.call(t, ms())
		time_left = maxf(f32(time_left - dt), 0.0)
		game.update(dt, time_left)
		t = f32(t + dt)
		steps += 1
	if game.finished():
		game.cancel_input()
	return game.finished()
