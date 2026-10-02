class_name PusherBot
extends RefCounted
## games/GameSimulationTest.kt `pusher(...)`: the coin-pusher bot taps a random spot across the
## deck (x 60..300, y 200) every [param interval] seconds. Shared by the per-machine test and the
## cross-game simulation test.


## Plays [param rounds] seeded rounds and returns their scores and tickets. [param round_seeds] is
## a one-element array holding the next round seed (Kotlin's `roundSeed++`); it is advanced.
static func play(test: PaTest, rounds: int, interval: float, seed_v: int, round_seeds: Array) -> SimHarness.Stats:
	var stats := SimHarness.Stats.new("pusher every %dms" % int(interval * 1000.0))
	var rng := KRandom.new(seed_v)
	var game := CoinPusherGame.new()
	for r in rounds:
		# next tap time, next pointer id
		var state := [0.3, 1]
		var round_seed: int = round_seeds[0]
		round_seeds[0] = round_seed + 1
		SimHarness.play_round(test, game, round_seed, stats, func(t: float, ms: int) -> void:
			if t >= state[0]:
				state[0] = t + interval
				var x := 60.0 + rng.next_float() * 240.0
				var pointer: int = state[1]
				state[1] = pointer + 1
				SimHarness.tap(game, pointer, x, 200.0, ms, 30))
	return stats
