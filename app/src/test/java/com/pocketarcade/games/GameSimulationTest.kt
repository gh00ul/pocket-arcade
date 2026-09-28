package com.pocketarcade.games

import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.TouchType
import com.pocketarcade.games.airhockey.AirHockeyGame
import com.pocketarcade.games.claw.ClawMachineGame
import com.pocketarcade.games.coinpusher.CoinPusherGame
import com.pocketarcade.games.hoops.HoopsGame
import com.pocketarcade.games.hoops.HoopsTuning
import com.pocketarcade.games.skeeball.SkeeBallGame
import com.pocketarcade.games.stacker.StackerGame
import com.pocketarcade.games.whackamole.WhackAMoleGame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Plays every machine headlessly with simple bots of different skill and checks that rounds
 * finish, pay out a sensible number of tickets, and reward skill. Run with
 * `./gradlew testDebugUnitTest` and read the printed table to retune payouts.
 */
class GameSimulationTest {
    /** Seeds successive rounds so every run replays the same games. */
    private var roundSeed = 1L

    private fun play(game: MiniGame, stats: Stats, bot: (Float, Long) -> Unit) {
        playRound(game, roundSeed++, stats, bot)
    }

    private fun skee(rounds: Int, speedNoise: Float, angleNoiseDeg: Float, seed: Int): Stats {
        val stats = Stats("skee noise ${(speedNoise * 100).toInt()}%")
        val rng = Random(seed)
        val game = SkeeBallGame()
        repeat(rounds) {
            var next = 0.5f
            var id = 1L
            play(game, stats) { t, ms ->
                if (t >= next) {
                    next = t + 1.1f
                    val speed = 1340f * (1f + gaussian(rng) * speedNoise)
                    val ang = gaussian(rng) * angleNoiseDeg * (Math.PI.toFloat() / 180f)
                    flick(game, id++, 180f, 600f, kotlin.math.sin(ang) * speed, -kotlin.math.cos(ang) * speed, ms)
                }
            }
        }
        return stats
    }

    private fun hoops(rounds: Int, speedNoise: Float, lateralNoise: Float, seed: Int): Stats {
        val stats = Stats("hoops noise ${(speedNoise * 100).toInt()}%")
        val rng = Random(seed)
        val game = HoopsGame()
        repeat(rounds) {
            var next = 0.5f
            var id = 1L
            play(game, stats) { t, ms ->
                if (t >= next) {
                    next = t + 1.0f
                    val up = HoopsTuning.FLICK_IDEAL * (1f + gaussian(rng) * speedNoise)
                    flick(game, id++, 180f, 560f, gaussian(rng) * lateralNoise, -up, ms)
                }
            }
        }
        return stats
    }

    private fun whack(rounds: Int, reaction: Float, missChance: Float, bombMistake: Float, seed: Int): Stats {
        val stats = Stats("whack react ${(reaction * 1000).toInt()}ms")
        val rng = Random(seed)
        val game = WhackAMoleGame()
        repeat(rounds) {
            val seenAt = FloatArray(9) { -1f }
            var id = 1L
            play(game, stats) { t, ms ->
                for (i in 0 until 9) {
                    val v = game.botView(i)
                    if (v == 0) {
                        seenAt[i] = -1f
                        continue
                    }
                    if (seenAt[i] < 0f) seenAt[i] = t + reaction * (0.7f + rng.nextFloat() * 0.6f)
                    if (t >= seenAt[i] && seenAt[i] > 0f) {
                        seenAt[i] = 1e9f
                        if (v == 3 && rng.nextFloat() > bombMistake) continue
                        val miss = rng.nextFloat() < missChance
                        val x = game.holeX(i) + if (miss) 70f else 0f
                        val y = game.holeY(i) - 40f
                        tap(game, id++, x, y, ms)
                    }
                }
            }
        }
        return stats
    }

    private fun pusher(rounds: Int, interval: Float, seed: Int): Stats {
        val stats = Stats("pusher every ${(interval * 1000).toInt()}ms")
        val rng = Random(seed)
        val game = CoinPusherGame()
        repeat(rounds) {
            var next = 0.3f
            var id = 1L
            play(game, stats) { t, ms ->
                if (t >= next) {
                    next = t + interval
                    val x = 60f + rng.nextFloat() * 240f
                    tap(game, id++, x, 200f, ms, holdMs = 30L)
                }
            }
        }
        return stats
    }

    private fun claw(rounds: Int, aimError: Float, patience: Float, seed: Int, watchClaw: Boolean): Stats {
        val stats = Stats("claw aim ±${aimError.toInt()}")
        val rng = Random(seed)
        val game = ClawMachineGame()
        val totals = IntArray(4)
        repeat(rounds) {
            var target = Float.NaN
            var holding = 0
            var settleT = 0f
            val buttons = game.botButtons()
            play(game, stats) { t, ms ->
                if (!game.botReady) {
                    target = Float.NaN
                    if (holding != 0) {
                        game.onTouch(TouchType.UP, 1L, 0f, 0f, ms)
                        holding = 0
                    }
                    return@play
                }
                if (target.isNaN()) {
                    // Aim for one of the most exposed prizes.
                    val tops = game.botPrizes().sortedBy { it.second }.take(3)
                    if (tops.isEmpty()) return@play
                    target = tops[rng.nextInt(tops.size)].first + gaussian(rng) * aimError
                    settleT = 0f
                }
                val dx = target - game.botTrolleyX
                val want = when {
                    dx > 3f -> 1
                    dx < -3f -> -1
                    else -> 0
                }
                if (want != holding) {
                    if (holding != 0) game.onTouch(TouchType.UP, 1L, 0f, 0f, ms)
                    if (want != 0) {
                        val bx = if (want < 0) buttons[0] else buttons[2]
                        val by = if (want < 0) buttons[1] else buttons[3]
                        game.onTouch(TouchType.DOWN, 1L, bx, by, ms)
                    }
                    holding = want
                }
                if (want == 0) {
                    settleT += FIXED_DT
                    // A careful player waits for the swing to die down; a hasty one drops at once.
                    val calm = if (watchClaw) game.botSwing < 1.5f else true
                    if (settleT > patience || (calm && settleT > 0.1f)) {
                        game.onTouch(TouchType.DOWN, 2L, buttons[4], buttons[5], ms)
                        game.onTouch(TouchType.UP, 2L, buttons[4], buttons[5], ms + 40)
                        target = Float.NaN
                    }
                }
            }
            for (k in 0..3) totals[k] += game.botCounters[k]
        }
        println("${stats.name}: grabs ${totals[0]}, clean holds ${totals[1]}, slips ${totals[2]}, prizes ${totals[3]}")
        return stats
    }

    /**
     * Guards the goal, and when the puck comes into our half strikes through it towards the far
     * goal. [lag] is how often the bot re-reads the table; [noise] is aiming error in world units.
     */
    private fun hockey(rounds: Int, lag: Float, noise: Float, seed: Int): Stats {
        val stats = Stats("hockey lag ${(lag * 1000).toInt()}ms")
        val rng = Random(seed)
        val game = AirHockeyGame()
        repeat(rounds) {
            var next = 0f
            var down = false
            var tx = 180f
            var ty = 540f
            play(game, stats) { t, ms ->
                if (t >= next) {
                    next = t + lag
                    val px = game.botPuckX
                    val py = game.botPuckY
                    if (py > 340f) {
                        // Aim for the side of the goal the CPU isn't covering.
                        val aimX = if (game.botCpuX > 180f) 140f else 220f
                        val gx = aimX - px
                        val gy = 60f - py
                        val gl = hypot(gx, gy).coerceAtLeast(1f)
                        val behind = if (game.botMalletY > py + 8f) -8f else 40f
                        tx = px - gx / gl * behind + gaussian(rng) * noise
                        ty = py - gy / gl * behind + gaussian(rng) * noise
                    } else {
                        tx = 180f + (px - 180f) * 0.5f
                        ty = 550f
                    }
                }
                val (sx, sy) = game.botScreen(tx, ty)
                if (!down) {
                    game.onTouch(TouchType.DOWN, 1L, sx, sy, ms)
                    down = true
                } else {
                    game.onTouch(TouchType.MOVE, 1L, sx, sy, ms)
                }
            }
        }
        return stats
    }

    /** Taps when the sliding slab passes a spot [sigma] away from perfect, on average. */
    private fun stacker(rounds: Int, sigma: Float, seed: Int): Stats {
        val stats = Stats("stacker sigma ${sigma.toInt()}")
        val rng = Random(seed)
        val game = StackerGame()
        repeat(rounds) {
            var height = -1
            var aim = 0f
            var last = Float.NaN
            var id = 1L
            play(game, stats) { _, ms ->
                if (!game.botMoving) {
                    last = Float.NaN
                    return@play
                }
                if (game.botHeight != height) {
                    height = game.botHeight
                    aim = gaussian(rng) * sigma
                    last = Float.NaN
                }
                val d = game.botDelta() - aim
                if (!last.isNaN() && (d > 0f) != (last > 0f)) {
                    tap(game, id++, 180f, 300f, ms, holdMs = 30L)
                    last = Float.NaN
                    height = -2
                } else {
                    last = d
                }
            }
        }
        return stats
    }

    @Test
    fun everyMachinePaysOutAndRewardsSkill() {
        val rounds = 12
        val plushBefore = simCollected.size
        val results = listOf(
            claw(rounds, aimError = 4f, patience = 3f, seed = 1, watchClaw = true) to claw(rounds, aimError = 18f, patience = 0.2f, seed = 2, watchClaw = false),
            skee(rounds, speedNoise = 0.04f, angleNoiseDeg = 2f, seed = 3) to skee(rounds, speedNoise = 0.15f, angleNoiseDeg = 6f, seed = 4),
            whack(rounds, reaction = 0.30f, missChance = 0.05f, bombMistake = 0.05f, seed = 5) to whack(rounds, reaction = 0.55f, missChance = 0.2f, bombMistake = 0.3f, seed = 6),
            pusher(rounds, interval = 0.5f, seed = 7) to pusher(rounds, interval = 1.8f, seed = 8),
            hoops(rounds, speedNoise = 0.04f, lateralNoise = 40f, seed = 9) to hoops(rounds, speedNoise = 0.14f, lateralNoise = 150f, seed = 10),
            hockey(rounds, lag = 0.05f, noise = 3f, seed = 11) to hockey(rounds, lag = 0.3f, noise = 18f, seed = 12),
            stacker(rounds, sigma = 8f, seed = 13) to stacker(rounds, sigma = 22f, seed = 14),
        )
        println("---- Pocket Arcade payout simulation ($rounds rounds each) ----")
        for ((good, casual) in results) {
            println(good)
            println(casual)
        }
        for ((good, casual) in results) assertPayoutBands(good, casual)
        println("plush won by claw bots: ${simCollected.size - plushBefore}")
    }

    @Test
    fun pusherNeverScoresBeforeTheFirstCoin() {
        val game = CoinPusherGame()
        game.seed = 42L
        game.start(simFx)
        var timeLeft = game.roundSeconds
        repeat((5f / FIXED_DT).toInt()) {
            timeLeft -= FIXED_DT
            game.update(FIXED_DT, timeLeft)
        }
        assertTrue("deck spilled on its own: ${game.score}", game.score == 0)
    }

    @Test
    fun whackBombCostsPoints() {
        val game = WhackAMoleGame()
        game.seed = 7L
        game.start(simFx)
        var timeLeft = game.roundSeconds
        var sawBomb = false
        var steps = 0
        while (!sawBomb && steps < 40_000) {
            timeLeft = (timeLeft - FIXED_DT).coerceAtLeast(0.01f)
            game.update(FIXED_DT, timeLeft)
            steps++
            for (i in 0 until 9) if (game.botView(i) == 3) {
                val before = game.score
                game.onTouch(TouchType.DOWN, 1L, game.holeX(i), game.holeY(i) - 40f, steps.toLong())
                assertTrue(game.score <= before)
                sawBomb = true
                break
            }
        }
        assertTrue("no bomb appeared", sawBomb)
    }

    @Test
    fun flickHelperProducesTheRequestedVelocity() {
        val tracker = com.pocketarcade.engine.FlickTracker()
        tracker.reset(0f, 0f, 0L)
        for (k in 1..6) tracker.add(0f, -1200f * k * 0.012f, (k * 12).toLong())
        val v = com.pocketarcade.engine.Vec2()
        tracker.velocity(v)
        assertTrue("vy=${v.y}", abs(v.y + 1200f) < 60f)
    }
}
