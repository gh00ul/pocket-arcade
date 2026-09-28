package com.pocketarcade.games.racer

import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.TouchType
import com.pocketarcade.games.RoundDriver
import com.pocketarcade.games.Stats
import com.pocketarcade.games.assertPayoutBands
import com.pocketarcade.games.gaussian
import com.pocketarcade.games.playRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Plays Turbo Racer headlessly with bots of different skill and checks its rules and payout bands. */
class RacerSimulationTest {
    /** Seeds successive rounds so every run replays the same games. */
    private var roundSeed = 1L

    /**
     * A driver playing through the touch path like a person: one steering thumb and, if
     * [drifts], a second finger held down through corners. Every [lag] seconds it decides where
     * on the road it wants to be (with [noise] error: the racing line or the middle, dodging
     * the car ahead if [avoids]) and slides its thumb over to steer there. Between decisions the
     * thumb stays put, so the curve's push carries the car outwards.
     */
    internal class Driver(
        val game: RacerGame,
        val rng: Random,
        val lag: Float,
        val noise: Float,
        val racingLine: Boolean,
        val avoids: Boolean,
        val drifts: Boolean,
    ) {
        private var next = 0f
        private var fingerX = MID
        private var fingerGoal = MID
        private var steerId = -1L
        private var driftId = -1L
        private var ids = 0L

        fun drive(t: Float, ms: Long) {
            if (steerId < 0L) {
                steerId = ++ids
                fingerX = MID
                fingerGoal = MID
                game.onTouch(TouchType.DOWN, steerId, fingerX, 450f, ms)
            }
            if (t >= next) {
                next = t + lag
                var want = if (racingLine) game.botRacingLine else 0f
                if (avoids) {
                    val bx = game.botBlockerX(320f)
                    if (!bx.isNaN()) {
                        // Pass on the side we're already on, unless that's off the road.
                        want = if (game.botPX < bx) bx - 66f else bx + 66f
                        if (want < -135f) want = bx + 66f
                        if (want > 135f) want = bx - 66f
                    }
                }
                want = (want + gaussian(rng) * noise).coerceIn(-135f, 135f)
                fingerGoal = fingerX + (want - game.botSteerTarget) / RacerTuning.STEER_GAIN
            }
            val move = (fingerGoal - fingerX).coerceIn(-MAX_MOVE, MAX_MOVE)
            if (abs(move) > 0.01f) {
                fingerX += move
                game.onTouch(TouchType.MOVE, steerId, fingerX, 450f, ms)
            }
            // Thumb at the edge of the screen: lift it and put it back down in the middle.
            if (fingerX < 15f || fingerX > 345f) {
                game.onTouch(TouchType.UP, steerId, fingerX, 450f, ms)
                fingerGoal += MID - fingerX
                fingerX = MID
                steerId = ++ids
                game.onTouch(TouchType.DOWN, steerId, fingerX, 450f, ms)
            }
            if (drifts) {
                if (driftId < 0L && !game.botBoosting && abs(game.botBendAhead(4)) > 0.9f && game.botSpeed > 500f) {
                    driftId = ++ids
                    game.onTouch(TouchType.DOWN, driftId, 300f, 540f, ms)
                } else if (driftId >= 0L && (abs(game.botBend) < 0.35f || game.botCharge >= RacerTuning.BOOST_CHARGE_2)) {
                    game.onTouch(TouchType.UP, driftId, 300f, 540f, ms)
                    driftId = -1L
                }
            }
        }

        companion object {
            const val MID = 180f
            /** How far a thumb slides in one 1/120 s step. */
            const val MAX_MOVE = 10f
        }
    }

    private class RaceStats(name: String) {
        val stats = Stats(name)
        val places = ArrayList<Int>()
        var finishes = 0
        val passes = ArrayList<Int>()
        val contacts = ArrayList<Int>()
        override fun toString() = "$stats  place %.2f  finished %d/%d  passes %.1f  bumps %.1f".format(
            places.average(), finishes, places.size, passes.average(), contacts.average(),
        )
    }

    private fun race(rounds: Int, name: String, seed: Int, make: (RacerGame, Random) -> Driver): RaceStats {
        val out = RaceStats(name)
        val rng = Random(seed)
        val game = RacerGame()
        repeat(rounds) {
            val driver = make(game, rng)
            playRound(game, roundSeed++, out.stats) { t, ms -> driver.drive(t, ms) }
            out.places += game.botPlace
            if (game.botRaceDone) out.finishes++
            out.passes += game.botPasses
            out.contacts += game.botContacts
        }
        return out
    }

    private fun goodDriver(game: RacerGame, rng: Random) =
        Driver(game, rng, lag = 0.1f, noise = 6f, racingLine = true, avoids = true, drifts = true)

    private fun casualDriver(game: RacerGame, rng: Random) =
        Driver(game, rng, lag = 0.6f, noise = 30f, racingLine = false, avoids = false, drifts = false)

    @Test
    fun racerPaysOutAndRewardsSkill() {
        val rounds = 12
        val good = race(rounds, "racer good", 15, ::goodDriver)
        val casual = race(rounds, "racer casual", 16, ::casualDriver)
        println(good)
        println(casual)
        assertPayoutBands(good.stats, casual.stats)
        assertTrue("skill should place better: $good vs $casual", good.places.average() < casual.places.average())
        assertTrue("a good driver should usually finish: $good", good.finishes * 4 >= rounds * 3)
        assertTrue("a casual driver should usually run out of time: $casual", casual.finishes * 2 <= rounds)
    }

    /** Plays [rounds] rounds with a fresh [bot] each, from fixed seeds. */
    private fun playRounds(rounds: Int, name: String, seed: Long, bot: (RacerGame) -> ((Float, Long) -> Unit)): Stats {
        val out = Stats(name)
        val game = RacerGame()
        repeat(rounds) { r ->
            val b = bot(game)
            playRound(game, seed + r, out) { t, ms -> b(t, ms) }
        }
        println(out)
        return out
    }

    /** A good driver's steering thumb with a second finger put down at the start and never lifted. */
    private fun steerAndHoldDrift(game: RacerGame, drives: Boolean): (Float, Long) -> Unit {
        val driver = Driver(game, Random(3), 0.1f, 6f, racingLine = true, avoids = true, drifts = false)
        var started = false
        return { t, ms ->
            if (!started) {
                started = true
                if (!drives) game.onTouch(TouchType.DOWN, 1L, 180f, 400f, ms)
                game.onTouch(TouchType.DOWN, 999L, 300f, 500f, ms)
            }
            if (drives) driver.drive(t, ms)
        }
    }

    @Test
    fun zeroEffortAndEndlessDriftPayLessThanDriving() {
        val rounds = 8
        val idle = playRounds(rounds, "racer never touch", 100L) { { _, _ -> } }
        val resting = playRounds(rounds, "racer rest + hold drift", 100L) { steerAndHoldDrift(it, drives = false) }
        val endless = playRounds(rounds, "racer steer + hold drift", 100L) { steerAndHoldDrift(it, drives = true) }
        val noDrift = playRounds(rounds, "racer steer no drift", 100L) {
            val d = Driver(it, Random(3), 0.1f, 6f, racingLine = true, avoids = true, drifts = false)
            d::drive
        }
        val full = playRounds(rounds, "racer full kit", 100L) {
            val d = Driver(it, Random(3), 0.1f, 6f, racingLine = true, avoids = true, drifts = true)
            d::drive
        }
        val casualRng = Random(16)
        val casual = playRounds(rounds, "racer casual", 100L) { casualDriver(it, casualRng)::drive }
        assertTrue("never touching the screen pays too much: $idle", idle.avgTickets <= 3.0)
        assertTrue("resting two fingers should pay less than casual driving: $resting vs $casual", resting.avgTickets < casual.avgTickets)
        assertTrue("holding the drift all race should pay less than the full kit: $endless vs $full", endless.avgTickets < full.avgTickets)
        assertTrue("holding the drift all race should pay no more than not drifting: $endless vs $noDrift", endless.avgTickets <= noDrift.avgTickets + 1.0)
    }

    @Test
    fun theGridStartsWithoutContact() {
        for (seed in 1L..8L) {
            val game = RacerGame()
            RoundDriver(game, seed).play(0.5f)
            assertEquals("contacts in the first 0.5 s with no input (seed $seed)", 0, game.botContacts)
        }
    }

    @Test
    fun crossingTheLineEndsTheRoundEarlyAndRanksByFinishOrder() {
        val game = RacerGame()
        val d = RoundDriver(game, 3L)
        d.play(0.5f)
        val line = game.botRaceLength
        // Two rivals just short of the flag, the player a little further back.
        game.botPlaceCar(1, line - 150f, -60f, 1000f)
        game.botPlaceCar(2, line - 100f, 60f, 1000f)
        game.botPlaceCar(0, line - 400f, 0f, 1000f)
        assertEquals(3, game.botPlace)
        assertTrue(d.play(3f))
        assertTrue("ended before the clock", d.timeLeft > 0f)
        assertTrue(game.botRaceDone)
        assertEquals(1, game.botFinishOrder(2))
        assertEquals(2, game.botFinishOrder(1))
        assertEquals(3, game.botFinishOrder(0))
        assertEquals(3, game.botPlace)
        // Nothing scores after the flag, through the host's ending either.
        val score = game.score
        assertTrue(score >= RacerTuning.PLACE_POINTS[2])
        d.end()
        assertEquals(score, game.score)
        assertEquals(3, game.botPlace)
    }

    @Test
    fun timeUpRanksByProgressAndStopsScoring() {
        val game = RacerGame()
        RoundDriver(game, 9L)
        // No input at all: the curves push the car off the road and it never makes the flag.
        var left = game.roundSeconds
        while (left - FIXED_DT > 1e-4f) {
            left -= FIXED_DT
            game.update(FIXED_DT, left)
        }
        assertFalse(game.botRaceDone)
        var expected = 1
        for (i in 1 until RacerTuning.CARS) {
            if (game.botFinishOrder(i) > 0 || game.botCarD(i) > game.botProgress) expected++
        }
        val before = game.score
        game.update(FIXED_DT, 0f)
        assertEquals(expected, game.botPlace)
        assertEquals((RacerTuning.PLACE_POINTS[expected - 1] * RacerTuning.DNF_SHARE).toInt(), game.score - before)
        // The car rolls to a stop, rivals keep going, but the score and the place are final.
        val final = game.score
        repeat((5f / FIXED_DT).toInt()) { game.update(FIXED_DT, 0f) }
        assertTrue(game.finished)
        assertEquals(final, game.score)
        assertEquals(expected, game.botPlace)
    }

    @Test
    fun positionsUpdateWhenOvertaking() {
        val game = RacerGame()
        val driver = goodDriver(game, Random(4))
        val start = RoundDriver(game, 44L)
        assertEquals(RacerTuning.PLAYER_SLOT + 1, game.botPlace)
        var best = game.botPlace
        var changes = 0
        var last = game.botPlace
        start.play(game.roundSeconds) { t, ms ->
            driver.drive(t, ms)
            if (!game.botRaceDone) {
                var ahead = 0
                for (i in 1 until RacerTuning.CARS) if (game.botFinishOrder(i) > 0 || game.botCarD(i) > game.botProgress) ahead++
                assertEquals("place at %.2f s".format(t), ahead + 1, game.botPlace)
                if (game.botPlace != last) changes++
                last = game.botPlace
                best = minOf(best, game.botPlace)
            }
        }
        assertTrue("the good driver should move up the field (best $best)", best <= 3)
        assertTrue(changes >= 3)
        assertTrue("clean passes should score", game.botPasses > 0)
    }

    @Test
    fun cancelInputReleasesThePointerAndANewPointerSteers() {
        val game = RacerGame()
        game.soloForTests = true
        val d = RoundDriver(game, 21L)
        d.play(0.5f)
        val base = game.botSteerTarget
        game.onTouch(TouchType.DOWN, 1L, 180f, 400f, d.ms)
        game.onTouch(TouchType.MOVE, 1L, 150f, 400f, d.ms + 16)
        assertEquals(base - 48f, game.botSteerTarget, 0.01f)
        game.onTouch(TouchType.DOWN, 2L, 300f, 500f, d.ms + 20)
        assertTrue("a second finger drifts", game.botDrifting)
        d.pause()
        assertFalse("pausing lets go of the drift", game.botDrifting)
        d.play(0.3f)
        // The lost fingers' late events change nothing (and the drift gives no boost).
        game.onTouch(TouchType.MOVE, 1L, 60f, 400f, d.ms)
        game.onTouch(TouchType.UP, 1L, 60f, 400f, d.ms)
        game.onTouch(TouchType.UP, 2L, 300f, 500f, d.ms)
        assertEquals(base - 48f, game.botSteerTarget, 0.01f)
        assertFalse(game.botBoosting)
        // A new finger steers on from where the car was heading.
        game.onTouch(TouchType.DOWN, 3L, 200f, 400f, d.ms)
        game.onTouch(TouchType.MOVE, 3L, 250f, 400f, d.ms + 16)
        assertEquals(base + 32f, game.botSteerTarget, 0.01f)
        assertFalse(game.botDrifting)
    }

    /** Car offsets through the first bend with one finger held down, still or trembling. */
    private fun throughTheFirstBend(jitter: Boolean): FloatArray {
        val game = RacerGame()
        game.soloForTests = true
        val d = RoundDriver(game, 5L)
        val noise = Random(8)
        val out = FloatArray((6f / FIXED_DT).toInt())
        game.onTouch(TouchType.DOWN, 1L, 180f, 400f, 0L)
        var nextMove = 0.008f
        var k = 0
        d.play(6f) { t, ms ->
            // A finger that's "still" on glass still reports MOVEs every 8 ms, a pixel either way.
            while (jitter && nextMove <= t) {
                game.onTouch(TouchType.MOVE, 1L, 180f + (noise.nextFloat() * 2f - 1f), 400f, ms)
                nextMove += 0.008f
            }
            if (k < out.size) out[k++] = game.botPX
        }
        return out
    }

    @Test
    fun curvePushSurvivesAJitteringFinger() {
        val still = throughTheFirstBend(jitter = false)
        val shaky = throughTheFirstBend(jitter = true)
        val start = still[0]
        var pushed = 0f
        var worst = 0f
        for (k in still.indices) {
            pushed = maxOf(pushed, abs(still[k] - start))
            worst = maxOf(worst, abs(still[k] - shaky[k]))
        }
        assertTrue("the bend should push the car wide (moved $pushed)", pushed > 60f)
        assertTrue("finger jitter changed the car's line by $worst", worst < 4f)
    }

    @Test
    fun noNaNOverALongRun() {
        val game = RacerGame()
        val rng = Random(77)
        val d = RoundDriver(game, 77L)
        val down = LongArray(3) { -1L }
        val xs = FloatArray(3)
        var ids = 0L
        fun check() {
            for (i in 0 until RacerTuning.CARS) {
                assertTrue(game.botCarD(i).isFinite() && game.botCarX(i).isFinite() && game.botCarV(i).isFinite())
            }
            assertTrue(game.botPlace in 1..RacerTuning.CARS)
        }
        d.play(game.roundSeconds + 20f) { _, ms ->
            // Up to three fingers landing, sliding wildly and lifting at random.
            val f = rng.nextInt(3)
            when {
                down[f] < 0L && rng.nextFloat() < 0.05f -> {
                    down[f] = ++ids
                    xs[f] = rng.nextFloat() * 360f
                    game.onTouch(TouchType.DOWN, down[f], xs[f], 400f, ms)
                }
                down[f] >= 0L && rng.nextFloat() < 0.03f -> {
                    game.onTouch(TouchType.UP, down[f], xs[f], 400f, ms)
                    down[f] = -1L
                }
                down[f] >= 0L -> {
                    xs[f] += (rng.nextFloat() - 0.5f) * 60f
                    game.onTouch(TouchType.MOVE, down[f], xs[f], 400f, ms)
                }
            }
            if (rng.nextFloat() < 0.002f) {
                // The host pausing: every finger is forgotten.
                game.cancelInput()
                down.fill(-1L)
            }
            check()
        }
        assertTrue(game.finished)
        repeat((10f / FIXED_DT).toInt()) {
            game.update(FIXED_DT, 0f)
            check()
        }
    }
}
