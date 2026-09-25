package com.pocketarcade.games

import com.pocketarcade.engine.AudioSynth
import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.Haptics
import com.pocketarcade.engine.TouchType
import com.pocketarcade.games.claw.ClawMachineGame
import com.pocketarcade.games.coinpusher.CoinPusherGame
import com.pocketarcade.games.hoops.HoopsGame
import com.pocketarcade.games.hoops.HoopsTuning
import com.pocketarcade.games.skeeball.SkeeBallGame
import com.pocketarcade.games.whackamole.WhackAMoleGame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Plays every machine headlessly with simple bots of different skill and checks that rounds
 * finish, pay out a sensible number of tickets, and reward skill. Run with
 * `./gradlew testDebugUnitTest` and read the printed table to retune payouts.
 */
class GameSimulationTest {
    private val collected = ArrayList<String>()
    private val fx = GameFx(AudioSynth(), Haptics(null)) { collected += it }

    private class Stats(val name: String) {
        val scores = ArrayList<Int>()
        val tickets = ArrayList<Int>()
        val avgScore get() = scores.average()
        val avgTickets get() = tickets.average()
        override fun toString() = "%-22s score %7.1f  tickets %6.1f  (min %d, max %d)".format(
            name, avgScore, avgTickets, tickets.minOrNull() ?: 0, tickets.maxOrNull() ?: 0,
        )
    }

    /** Runs one round; [bot] is called every step with (simulated seconds, simulated millis). */
    private var roundSeed = 1L

    private fun play(game: MiniGame, stats: Stats, bot: (Float, Long) -> Unit) {
        (game as? BaseMiniGame)?.seed = roundSeed++
        game.start(fx)
        var timeLeft = game.roundSeconds
        var t = 0f
        var steps = 0
        val maxSteps = ((game.roundSeconds + 20f) / FIXED_DT).toInt()
        while (!game.finished && steps < maxSteps) {
            bot(t, (t * 1000f).toLong())
            timeLeft = (timeLeft - FIXED_DT).coerceAtLeast(0f)
            game.update(FIXED_DT, timeLeft)
            t += FIXED_DT
            steps++
        }
        assertTrue("${game.title} never finished", game.finished)
        stats.scores += game.score
        stats.tickets += game.ticketsFor(game.score) + game.bonusTickets
    }

    /** A straight-line flick ending at release, sampled like a real finger. */
    private fun flick(game: MiniGame, id: Long, x0: Float, y0: Float, vx: Float, vy: Float, ms: Long) {
        game.onTouch(TouchType.DOWN, id, x0, y0, ms)
        for (k in 1..6) {
            val dt = k * 0.012f
            game.onTouch(TouchType.MOVE, id, x0 + vx * dt, y0 + vy * dt, ms + (dt * 1000).toLong())
        }
        game.onTouch(TouchType.UP, id, x0 + vx * 0.072f, y0 + vy * 0.072f, ms + 72)
    }

    private fun gaussian(rng: Random): Float {
        var u = 0f
        repeat(6) { u += rng.nextFloat() }
        return (u - 3f) / 0.707f
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
                        game.onTouch(TouchType.DOWN, id, x, y, ms)
                        game.onTouch(TouchType.UP, id++, x, y, ms + 40)
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
                    game.onTouch(TouchType.DOWN, id, x, 200f, ms)
                    game.onTouch(TouchType.UP, id++, x, 200f, ms + 30)
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

    @Test
    fun everyMachinePaysOutAndRewardsSkill() {
        val rounds = 12
        val results = listOf(
            claw(rounds, aimError = 4f, patience = 3f, seed = 1, watchClaw = true) to claw(rounds, aimError = 18f, patience = 0.2f, seed = 2, watchClaw = false),
            skee(rounds, speedNoise = 0.04f, angleNoiseDeg = 2f, seed = 3) to skee(rounds, speedNoise = 0.15f, angleNoiseDeg = 6f, seed = 4),
            whack(rounds, reaction = 0.30f, missChance = 0.05f, bombMistake = 0.05f, seed = 5) to whack(rounds, reaction = 0.55f, missChance = 0.2f, bombMistake = 0.3f, seed = 6),
            pusher(rounds, interval = 0.5f, seed = 7) to pusher(rounds, interval = 1.8f, seed = 8),
            hoops(rounds, speedNoise = 0.04f, lateralNoise = 40f, seed = 9) to hoops(rounds, speedNoise = 0.14f, lateralNoise = 150f, seed = 10),
        )
        println("---- Pocket Arcade payout simulation ($rounds rounds each) ----")
        for ((good, casual) in results) {
            println(good)
            println(casual)
        }
        for ((good, casual) in results) {
            assertTrue("$good pays too little", good.avgTickets >= 8.0)
            assertTrue("$good pays too much", good.avgTickets <= 70.0)
            assertTrue("$casual pays nothing", casual.avgTickets >= 2.0)
            assertTrue("skill should pay: $good vs $casual", good.avgScore >= casual.avgScore)
        }
        println("plush won by claw bots: ${collected.size}")
    }

    @Test
    fun pusherNeverScoresBeforeTheFirstCoin() {
        val game = CoinPusherGame()
        game.seed = 42L
        game.start(fx)
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
        game.start(fx)
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
