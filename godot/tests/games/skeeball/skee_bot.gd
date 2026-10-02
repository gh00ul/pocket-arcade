class_name SkeeBot
extends RefCounted
## games/GameSimulationTest.kt `skee(...)`: the skee-ball bot. Every 1.1 s it flicks the ball
## straight up from the rest spot at about 1340 units/s, with gaussian noise on the speed (a
## fraction) and on the angle (degrees). Shared by the per-machine test and the cross-game
## simulation test.


## Plays [param rounds] seeded rounds and returns their scores and tickets. [param round_seeds] is
## a one-element array holding the next round seed (Kotlin's `roundSeed++`); it is advanced.
static func play(test: PaTest, rounds: int, speed_noise: float, angle_noise_deg: float, seed_v: int, round_seeds: Array) -> SimHarness.Stats:
	var stats := SimHarness.Stats.new("skee noise %d%%" % int(speed_noise * 100.0))
	var rng := KRandom.new(seed_v)
	var game := SkeeBallGame.new()
	for r in rounds:
		# next flick time, next pointer id
		var state := [0.5, 1]
		var round_seed: int = round_seeds[0]
		round_seeds[0] = round_seed + 1
		SimHarness.play_round(test, game, round_seed, stats, func(t: float, ms: int) -> void:
			if t >= state[0]:
				state[0] = t + 1.1
				var speed := 1340.0 * (1.0 + SimHarness.gaussian(rng) * speed_noise)
				var ang := SimHarness.gaussian(rng) * angle_noise_deg * (PI / 180.0)
				var pointer: int = state[1]
				state[1] = pointer + 1
				SimHarness.flick(game, pointer, 180.0, 600.0, sin(ang) * speed, -cos(ang) * speed, ms))
	return stats
