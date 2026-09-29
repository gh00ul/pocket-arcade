package com.pocketarcade.games

import com.pocketarcade.games.airhockey.AirHockeyGame
import com.pocketarcade.engine.TouchType
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * PLAY AGAIN (ui/GameHostScreen.kt) reuses the machine's instance and just calls start() again,
 * so every machine's reset() must clear all of its round state: a replay with the same seed and
 * the same inputs has to play out exactly like a round on a fresh instance.
 */
class ReplayResetTest {
    /** A deterministic bot that isn't tuned to any machine: seeded taps and upward flicks. */
    private fun bot(game: MiniGame): (Float, Long) -> Unit {
        val r = Random(99)
        var next = 0.2f
        var n = 0L
        return { t, ms ->
            if (t >= next) {
                next += 0.37f
                n += 10
                if (r.nextBoolean()) {
                    tap(game, n, r.nextFloat() * GAME_W, r.nextFloat() * GAME_H, ms)
                } else {
                    flick(game, n + 1, 120f + r.nextFloat() * 120f, 590f, (r.nextFloat() - 0.5f) * 600f, -1200f - r.nextFloat() * 900f, ms)
                }
            }
        }
    }

    /** Plays a whole round like the host and records the score every second, then the payout. */
    private fun trace(game: MiniGame, seed: Long, bot: (Float, Long) -> Unit): String {
        val sb = StringBuilder()
        var lastSec = -1
        val d = RoundDriver(game, seed)
        d.play(game.roundSeconds + 20f) { t, ms ->
            bot(t, ms)
            val s = t.toInt()
            if (s != lastSec) {
                lastSec = s
                sb.append(game.score).append(',')
            }
        }
        d.end()
        return sb.append(" steps=").append(d.steps).append(" score=").append(game.score)
            .append(" tickets=").append(game.ticketsFor(game.score)).append('+').append(game.bonusTickets).toString()
    }

    @Test
    fun everyMachineReplaysLikeAFreshOne() {
        val fresh = GameRegistry.createAll()
        val reused = GameRegistry.createAll()
        for (i in fresh.indices) {
            val a = fresh[i]
            val b = reused[i]
            val expected = trace(a, 42L, bot(a))
            trace(b, 7L, bot(b))
            assertEquals("${b.title}: a replay on a reused instance differs from a fresh round", expected, trace(b, 42L, bot(b)))
        }
    }

    /** Air hockey with a bot that chases the puck, so the CPU mallet is kept busy defending. */
    @Test
    fun airHockeyReplaysLikeAFreshOneAgainstAChaser() {
        fun run(g: AirHockeyGame, seed: Long): String {
            val sb = StringBuilder()
            var down = false
            var k = 0
            val d = RoundDriver(g, seed)
            d.play(g.roundSeconds + 20f) { _, ms ->
                val (fx, fy) = g.botScreen(g.botPuckX, g.botPuckY + 10f)
                g.onTouch(if (down) TouchType.MOVE else TouchType.DOWN, 1L, fx, fy, ms)
                down = true
                if (k++ % 30 == 0) sb.append("%.1f,%.1f;".format(g.botCpuX, g.botCpuY))
            }
            d.end()
            return sb.append(" score=").append(g.score).append(" goals=").append(g.botGoals).toString()
        }
        val expected = run(AirHockeyGame(), 42L)
        val reused = AirHockeyGame()
        run(reused, 7L)
        assertEquals(expected, run(reused, 42L))
    }
}
