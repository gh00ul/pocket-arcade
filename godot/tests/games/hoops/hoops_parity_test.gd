extends PaTest
## Godot-only: seeded hoops rounds against numbers build-13 printed (a scratch run of its
## GameSimulationTest bot on round seed 97, the first hoops round of build-13's simulation). Played
## with build-13's clock (F32RoundDriver) every shot lands the same way for the first 34 seconds:
## flick, launch, rim and board bounces and scoring all agree. (Later, once the hoop moves, the
## 64-bit round clock and ball maths can turn a rim-out into a make: the simulation statistics test
## covers the rest.)

## build-13: score/shots at the start of each second of round seed 97, seconds 0..33.
const BUILD13_TRACE := [
	"0/0", "0/1", "10/2", "36/3", "75/4", "127/5", "177/6", "242/7", "307/8", "307/9", "357/10",
	"367/11", "387/12", "417/13", "457/14", "507/15", "572/16", "637/17", "702/18", "702/19", "752/20",
	"762/21", "782/22", "782/23", "782/24", "782/25", "782/26", "782/27", "792/28", "792/29", "802/30",
	"802/31", "802/32", "802/33",
]


## games/GameSimulationTest.kt `hoops` with its Float arithmetic (the shot clock and the noise).
static func f32_bot(game: HoopsGame, rng: KRandom, speed_noise: float, lateral_noise: float) -> Callable:
	var next := [F32RoundDriver.f32(0.5)]
	var id := [1]
	return func(t: float, ms: int) -> void:
		if t >= next[0]:
			next[0] = F32RoundDriver.f32(t + 1.0)
			var noise := F32RoundDriver.f32(F32RoundDriver.gaussian(rng) * F32RoundDriver.f32(speed_noise))
			var up := F32RoundDriver.f32(HoopsTuning.FLICK_IDEAL * F32RoundDriver.f32(1.0 + noise))
			var lateral := F32RoundDriver.f32(F32RoundDriver.gaussian(rng) * F32RoundDriver.f32(lateral_noise))
			SimHarness.flick(game, id[0], 180.0, 560.0, lateral, -up, ms)
			id[0] += 1


func test_seeded_round_follows_build_13() -> void:
	var game := HoopsGame.new()
	var d := F32RoundDriver.new(game, 97)
	var bot := f32_bot(game, KRandom.new(9), 0.04, 40.0)
	var trace: Array[String] = []
	var last := [-1]
	d.play(34.0, func(t: float, ms: int) -> void:
		bot.call(t, ms)
		var s := int(t)
		if s != last[0]:
			last[0] = s
			trace.append("%d/%d" % [game.score, game.bot_shots()]))
	assert_eq(BUILD13_TRACE.size(), trace.size())
	for i in mini(trace.size(), BUILD13_TRACE.size()):
		assert_eq(BUILD13_TRACE[i], trace[i], "second %d" % i)
