package com.pocketarcade.games.pinball

import com.pocketarcade.engine.TouchType
import com.pocketarcade.games.Stats
import com.pocketarcade.games.assertPayoutBands
import com.pocketarcade.games.playRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Plays Star Flipper headlessly through the real touch path: a player who flips as the ball
 * comes onto a flipper and plunges hard, and a casual one who flips late, misses some balls
 * and flips at random. Checks the payout bands and that every round finishes.
 */
class PinballSimulationTest {
    /** Seeds successive rounds so every run replays the same games. */
    private var roundSeed = 1L

    /**
     * A thumb on each flipper and one on the plunger. [reaction] is the delay between the ball
     * coming into a flipper's reach and the press, [missChance] the chance of not flipping at
     * all, [lead] how far ahead (seconds) it reads the ball, [randomFlips] panic flips per second.
     */
    internal class FlipperBot(
        private val game: PinballGame,
        private val rng: Random,
        private val reaction: Float,
        private val missChance: Float,
        private val lead: Float,
        private val randomFlips: Float,
        private val pullMin: Float,
        private val holdSeconds: Float = 0.2f,
    ) {
        private var nextId = 10L
        private val held = longArrayOf(-1L, -1L)
        private val releaseAt = FloatArray(2)
        private val pressAt = floatArrayOf(-1f, -1f)
        private val readyAt = FloatArray(2)
        private var plungeId = -1L
        private var plungeUpAt = 0f
        private var plungePull = 1f
        private var plungeWaitUntil = -1f
        private var lastT = 0f

        fun tick(t: Float, ms: Long) {
            val dt = t - lastT
            lastT = t
            // The plunger: pull down, hold a moment, let go.
            if (plungeId < 0L && game.botLaneReady) {
                if (plungeWaitUntil < 0f) plungeWaitUntil = t + 0.25f + rng.nextFloat() * 0.3f
                if (t >= plungeWaitUntil) {
                    plungeId = nextId++
                    plungePull = pullMin + (1f - pullMin) * rng.nextFloat()
                    plungeUpAt = t + 0.3f
                    game.onTouch(TouchType.DOWN, plungeId, PLUNGE_X, PLUNGE_Y, ms)
                    game.onTouch(TouchType.MOVE, plungeId, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE * plungePull * 0.5f, ms + 60)
                    game.onTouch(TouchType.MOVE, plungeId, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE * plungePull, ms + 120)
                }
            }
            if (plungeId >= 0L && t >= plungeUpAt) {
                game.onTouch(TouchType.UP, plungeId, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE * plungePull, ms)
                plungeId = -1L
                plungeWaitUntil = -1f
            }
            for (side in 0..1) {
                if (held[side] >= 0L && t >= releaseAt[side]) {
                    game.onTouch(TouchType.UP, held[side], sideX(side), FLIP_Y, ms)
                    held[side] = -1L
                    readyAt[side] = t + 0.08f
                }
                if (held[side] < 0L && pressAt[side] < 0f && t >= readyAt[side]) {
                    if (ballComing(side)) {
                        if (rng.nextFloat() < missChance) {
                            readyAt[side] = t + 0.5f
                        } else {
                            pressAt[side] = t + reaction * (0.7f + 0.6f * rng.nextFloat())
                        }
                    } else if (randomFlips > 0f && rng.nextFloat() < randomFlips * dt) {
                        pressAt[side] = t
                    }
                }
                if (pressAt[side] >= 0f && t >= pressAt[side] && held[side] < 0L) {
                    pressAt[side] = -1f
                    held[side] = nextId++
                    releaseAt[side] = t + holdSeconds
                    game.onTouch(TouchType.DOWN, held[side], sideX(side), FLIP_Y, ms)
                }
            }
        }

        /** A ball (read [lead] seconds ahead) is over the flipper's outer part, not rising fast. */
        private fun ballComing(side: Int): Boolean {
            val px = PinballTable.flipX(side)
            val a = PinballTable.restAngle(side)
            val ex = cos(a) * PinballTable.FLIP_LEN
            val ey = sin(a) * PinballTable.FLIP_LEN
            for (i in 0 until game.botMaxBalls) {
                if (!game.botBallInPlay(i)) continue
                val vy = game.botBallVY(i)
                if (vy < -150f) continue
                val bx = game.botBallX(i) + game.botBallVX(i) * lead
                val by = game.botBallY(i) + vy * lead
                val along = ((bx - px) * ex + (by - PinballTable.FLIP_Y) * ey) / (PinballTable.FLIP_LEN * PinballTable.FLIP_LEN)
                if (along < 0.2f || along > 1.05f) continue
                val cx = px + ex * along
                val cy = PinballTable.FLIP_Y + ey * along
                val dx = bx - cx
                val dy = by - cy
                // Above the bat (smaller y) and close to it.
                if (dy < 6f && sqrt(dx * dx + dy * dy) < PinballTable.FLIP_R0 + PinballTable.BALL_R + 14f) return true
            }
            return false
        }

        private fun sideX(side: Int) = if (side == 0) 70f else 230f
    }

    private companion object {
        const val PLUNGE_X = 320f
        const val PLUNGE_Y = 520f
        const val FLIP_Y = 560f
    }

    private fun pinball(rounds: Int, seed: Int, name: String, make: (PinballGame, Random) -> FlipperBot): Stats {
        val stats = Stats(name)
        val rng = Random(seed)
        val game = PinballGame()
        var drains = 0
        var bumpers = 0
        var multiballs = 0
        var jackpots = 0
        var failsafe = 0
        var early = 0
        var playTime = 0f
        repeat(rounds) {
            val bot = make(game, rng)
            val d = playRound(game, roundSeed++, stats) { t, ms -> bot.tick(t, ms) }
            drains += game.botDrains
            bumpers += game.botBumperHits
            multiballs += game.botMultiballs
            jackpots += game.botJackpots
            failsafe += game.botFailsafeTrips
            if (game.botEndedEarly) early++
            playTime += d.t
        }
        println("$stats  drains %.1f  bumpers %.1f  multiballs %.2f  jackpots %.2f  failsafe %d  ended early %d/%d  avg %.1fs".format(
            drains / rounds.toFloat(), bumpers / rounds.toFloat(), multiballs / rounds.toFloat(), jackpots / rounds.toFloat(),
            failsafe, early, rounds, playTime / rounds,
        ))
        return stats
    }

    @Test
    fun pinballPaysOutAndRewardsSkill() {
        val rounds = 14
        val good = pinball(rounds, 31, "pinball good") { g, r -> FlipperBot(g, r, reaction = 0.0f, missChance = 0.02f, lead = 0.035f, randomFlips = 0f, pullMin = 0.95f) }
        val casual = pinball(rounds, 32, "pinball casual") { g, r -> FlipperBot(g, r, reaction = 0.16f, missChance = 0.3f, lead = 0f, randomFlips = 0.4f, pullMin = 0.3f) }
        assertPayoutBands(good, casual)
    }

    @Test
    fun everyRoundFinishesEvenWithNobodyPlaying() {
        val game = PinballGame()
        val stats = Stats("idle")
        repeat(3) { playRound(game, roundSeed++, stats) { _, _ -> } }
        assertEquals(3, stats.scores.size)
    }

    @Test
    fun aRoundWithOnlyPlungesEndsEarlyWhenTheBallsRunOut() {
        val game = PinballGame()
        val rng = Random(5)
        val stats = Stats("plunge only")
        val d = playRound(game, 77L, stats) { t, ms ->
            if (game.botLaneReady && !game.botPlungerHeld) {
                game.onTouch(TouchType.DOWN, (t * 1000).toLong() + 1L, PLUNGE_X, PLUNGE_Y, ms)
                game.onTouch(TouchType.MOVE, (t * 1000).toLong() + 1L, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE * (0.6f + 0.4f * rng.nextFloat()), ms + 50)
                game.onTouch(TouchType.UP, (t * 1000).toLong() + 1L, PLUNGE_X, PLUNGE_Y + PinballTuning.PULL_RANGE, ms + 100)
            }
        }
        println("plunge-only round: ${d.t}s, drains ${game.botDrains}, score ${game.score}")
        assertTrue(game.botEndedEarly || d.t >= game.roundSeconds)
    }
}
