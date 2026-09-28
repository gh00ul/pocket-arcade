package com.pocketarcade.games.racer

import com.pocketarcade.games.Stats
import com.pocketarcade.games.assertPayoutBands
import com.pocketarcade.games.gaussian
import com.pocketarcade.games.playRound
import org.junit.Test
import kotlin.random.Random

/** Plays Turbo Racer headlessly with bots of different skill and checks its payout bands. */
class RacerSimulationTest {
    /** Seeds successive rounds so every run replays the same games. */
    private var roundSeed = 1L

    /** Picks the clearest lane every [lag] seconds and steers for it with [noise] error. */
    private fun racer(rounds: Int, lag: Float, noise: Float, seed: Int, chaseTokens: Boolean): Stats {
        val stats = Stats("racer lag ${(lag * 1000).toInt()}ms")
        val rng = Random(seed)
        val game = RacerGame()
        repeat(rounds) {
            var next = 0f
            var target = 0f
            playRound(game, roundSeed++, stats) { t, _ ->
                if (t >= next) {
                    next = t + lag
                    val clear = game.botLaneClearance()
                    val current = ((game.botPX / 100f) + 1f).toInt().coerceIn(0, 2)
                    var best = current
                    if (clear[current] < 700f) {
                        for (i in 0..2) if (clear[i] > clear[best]) best = i
                    }
                    val token = if (chaseTokens) game.botTokenLane(900f) else -1
                    if (token >= 0 && clear[token] > 500f) best = token
                    target = (best - 1) * 100f + gaussian(rng) * noise
                }
                game.botSteer(target)
            }
        }
        return stats
    }

    @Test
    fun racerPaysOutAndRewardsSkill() {
        val rounds = 12
        val good = racer(rounds, lag = 0.15f, noise = 8f, seed = 15, chaseTokens = true)
        val casual = racer(rounds, lag = 0.9f, noise = 45f, seed = 16, chaseTokens = false)
        println(good)
        println(casual)
        assertPayoutBands(good, casual)
    }
}
