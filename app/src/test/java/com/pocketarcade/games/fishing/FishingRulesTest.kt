package com.pocketarcade.games.fishing

import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.TouchType
import com.pocketarcade.games.RoundDriver
import com.pocketarcade.games.playRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** The rules of Gone Fishing: the crank, the strike, the line's tension, scoring and the round's end. */
class FishingRulesTest {
    // ---------------------------------------------------------------- the crank

    /** Feeds [n] samples [step] radians apart round (100, 100) at radius [r], 16 ms apart. */
    private fun circle(c: Crank, id: Long, from: Float, step: Float, n: Int, ms0: Long, r: Float = 50f): Long {
        var a = from
        var ms = ms0
        repeat(n) {
            a += step
            ms += 16L
            c.move(id, 100f + cos(a) * r, 100f + sin(a) * r, ms)
        }
        return ms
    }

    @Test
    fun clockwiseCrankingIsPositiveAndCounterClockwiseNegative() {
        val c = Crank(100f, 100f, 16f)
        assertTrue(c.grab(1L, 150f, 100f, 0L))
        // 0.16 rad every 16 ms is 10 rad/s, clockwise on screen (y down); it crosses ±π on the way.
        var ms = circle(c, 1L, 0f, 0.16f, 30, 0L)
        assertEquals(10f, c.rate, 0.2f)
        assertEquals(4.8f, c.take(), 1e-3f)
        assertEquals("taken turning is gone", 0f, c.take(), 0f)
        ms = circle(c, 1L, 4.8f, -0.16f, 30, ms)
        assertEquals(-10f, c.rate, 0.2f)
        assertEquals(-4.8f, c.take(), 1e-3f)
        // Half the speed reads as half the rate.
        circle(c, 1L, 0f, 0.08f, 30, ms)
        assertEquals(5f, c.rate, 0.2f)
    }

    @Test
    fun crankIgnoresTheHubAndJitter() {
        val c = Crank(100f, 100f, 16f)
        c.grab(1L, 150f, 100f, 0L)
        // A sample right on the hub, then one a quarter turn round: no jump is counted.
        c.move(1L, 101f, 101f, 16L)
        c.move(1L, 100f, 150f, 32L)
        assertEquals(0f, c.take(), 1e-4f)
        // Only real movement after it counts.
        c.move(1L, 100f + cos(1.7708f) * 50f, 100f + sin(1.7708f) * 50f, 48L)
        assertEquals(0.2f, c.take(), 1e-3f)
        // A finger trembling on the spot goes nowhere.
        var ms = 64L
        for (k in 0 until 40) {
            val a = 1.7708f + if (k % 2 == 0) 0.01f else -0.01f
            c.move(1L, 100f + cos(a) * 50f, 100f + sin(a) * 50f, ms)
            ms += 16L
        }
        assertEquals(0f, c.take(), 0.011f)
        assertTrue("jitter barely spins the reel: ${c.rate}", abs(c.rate) < 1f)
    }

    @Test
    fun crankFollowsOnlyItsOwnPointer() {
        val c = Crank(100f, 100f, 16f)
        c.grab(1L, 150f, 100f, 0L)
        assertFalse("a second finger can't take the held crank", c.grab(2L, 100f, 150f, 0L))
        circle(c, 2L, 0f, 0.2f, 10, 0L)
        assertEquals("another finger's samples turn nothing", 0f, c.take(), 0f)
        c.release(2L)
        assertTrue("releasing another finger lets nothing go", c.held)
        c.release(1L)
        assertFalse(c.held)
        circle(c, 1L, 0f, 0.2f, 10, 200L)
        assertEquals("a lifted finger's samples turn nothing", 0f, c.take(), 0f)
        assertTrue(c.grab(2L, 150f, 100f, 400L))
        circle(c, 2L, 0f, 0.2f, 10, 400L)
        assertEquals(2f, c.take(), 1e-3f)
        // Stop moving: the reel spins down.
        repeat(60) { c.age(FIXED_DT) }
        assertEquals(0f, c.rate, 0.05f)
    }

    // ---------------------------------------------------------------- helpers

    /** A round with fish [slot] placed as given and already hooked. */
    private fun hooked(seed: Long, species: Int, weight: Float, x: Float, z: Float, slot: Int = 0): Pair<FishingGame, RoundDriver> {
        val game = FishingGame()
        val d = RoundDriver(game, seed)
        d.play(0.2f)
        game.botPlaceFish(slot, species, weight, x, z)
        game.botHookNow(slot)
        assertEquals(CastPhase.FIGHT, game.botPhase)
        return game to d
    }

    /** Winds like a careful angler: hard while the fish rests, gently while it thrashes and runs. */
    private fun careful(game: FishingGame): Float = when {
        game.botTension > 0.9f -> 0f
        game.botPull != Pull.REST -> 1.5f
        game.botTension > 0.75f -> 5f
        else -> 10f
    }

    /** Holds a cast on the pond aimed at world ([x], [z]) and lets go at the right power. */
    private fun castAt(game: FishingGame, d: RoundDriver, id: Long, x: Float, z: Float) {
        val aim = FloatArray(2)
        game.botAimFor(x, z, aim)
        game.onTouch(TouchType.DOWN, id, aim[0], 300f, d.ms)
        var guard = 0
        while (abs(game.botPower - aim[1]) >= 0.02f && guard++ < 600) d.play(FIXED_DT * 1.5f)
        game.onTouch(TouchType.UP, id, aim[0], 300f, d.ms)
        assertEquals(CastPhase.FLIGHT, game.botPhase)
    }

    private fun playUntil(d: RoundDriver, seconds: Float, done: () -> Boolean, bot: ((Float, Long) -> Unit)? = null): Boolean {
        val steps = (seconds / FIXED_DT).toInt()
        repeat(steps) {
            if (done()) return true
            d.play(FIXED_DT * 1.5f, bot)
        }
        return done()
    }

    // ---------------------------------------------------------------- the strike

    @Test
    fun aMissedStrikeLosesTheFish() {
        val game = FishingGame()
        val d = RoundDriver(game, 14L)
        d.play(0.2f)
        game.botPlaceFish(0, 1, 2f, 180f, 280f)
        castAt(game, d, 1L, 180f, 310f)
        assertTrue("something should bite", playUntil(d, 30f, { game.botBiting }))
        val biter = game.botSuitor
        // Never touch the reel: the window runs out.
        d.play(FishingTuning.STRIKE_WINDOW + 0.05f)
        assertEquals(1, game.botMissed)
        assertFalse(game.botBiting)
        assertEquals(-1, game.botHooked)
        assertEquals("the lure stays in the water", CastPhase.WAIT, game.botPhase)
        assertEquals("the fish swims off", FishMode.SWIM, game.botFishMode(biter))
        assertEquals(0, game.score)
    }

    @Test
    fun crankingInTheWindowSetsTheHook() {
        val game = FishingGame()
        val d = RoundDriver(game, 15L)
        d.play(0.2f)
        game.botPlaceFish(0, 0, 0.8f, 180f, 280f)
        castAt(game, d, 1L, 180f, 310f)
        val reel = ReelFinger(game)
        reel.press(2L, d.ms)
        assertTrue(playUntil(d, 30f, { game.botBiting }) { _, ms -> reel.turn(0f, ms) })
        val biter = game.botSuitor
        playUntil(d, 0.5f, { game.botPhase == CastPhase.FIGHT }) { _, ms -> reel.turn(10f, ms) }
        assertEquals(CastPhase.FIGHT, game.botPhase)
        assertEquals(biter, game.botHooked)
        assertEquals(FishMode.HOOKED, game.botFishMode(biter))
    }

    // ---------------------------------------------------------------- the line

    @Test
    fun tooMuchTensionSnapsTheLine() {
        val (game, d) = hooked(11L, 1, 2.5f, 180f, 250f)
        val reel = ReelFinger(game)
        reel.press(5L, d.ms)
        var peak = 0f
        d.play(6f) { _, ms ->
            reel.turn(30f, ms)
            peak = maxOf(peak, game.botTension)
        }
        assertEquals(1, game.botSnaps)
        assertTrue("the tension went over the limit: $peak", peak > FishingTuning.SNAP_TENSION)
        assertEquals(0, game.botLanded)
        assertEquals(0, game.score)
        assertEquals(-1, game.botHooked)
    }

    @Test
    fun aSlackLineThrowsTheHook() {
        // Winding backwards gives line: slack at once.
        val (game, d) = hooked(12L, 1, 2.5f, 180f, 300f)
        val reel = ReelFinger(game)
        reel.press(5L, d.ms)
        d.play(2.5f) { _, ms -> reel.turn(-10f, ms) }
        assertEquals(1, game.botThrown)
        assertEquals(0, game.botSnaps)
        assertEquals(0, game.score)
        // Never winding at all loses it too, a little later.
        val (idle, d2) = hooked(13L, 0, 0.8f, 180f, 300f)
        assertTrue(playUntil(d2, 20f, { idle.botThrown == 1 }))
        assertEquals(0, idle.score)
    }

    @Test
    fun landingScoresWeightTimesSpeciesValue() {
        val (game, d) = hooked(16L, 1, 2.5f, 180f, 420f)
        val reel = ReelFinger(game)
        reel.press(5L, d.ms)
        assertTrue(playUntil(d, 20f, { game.botLanded == 1 && game.botPhase == CastPhase.IDLE }) { _, ms -> reel.turn(careful(game), ms) })
        assertEquals(30, game.botPoints(1, 2.5f))
        assertEquals(30, game.score)
        assertEquals(30, game.botLastCatch)
        assertEquals(0, game.botSnaps + game.botThrown)
    }

    @Test
    fun bigFishTakeLonger() {
        fun secondsToLand(species: Int, weight: Float, seed: Long): Float {
            val (game, d) = hooked(seed, species, weight, 180f, 250f)
            val reel = ReelFinger(game)
            reel.press(5L, d.ms)
            val start = d.t
            assertTrue(playUntil(d, 40f, { game.botLanded == 1 }) { _, ms -> reel.turn(careful(game), ms) })
            assertEquals("${FishingTuning.NAMES[species]} got away", 0, game.botSnaps + game.botThrown)
            return d.t - start
        }
        val perch = secondsToLand(0, 0.6f, 17L)
        val catfish = secondsToLand(2, 7f, 17L)
        assertTrue("catfish $catfish s vs perch $perch s", catfish > perch * 1.3f)
    }

    // ---------------------------------------------------------------- the round's end

    @Test
    fun noScoringAfterTimeUp() {
        val game = FishingGame()
        val d = RoundDriver(game, 18L)
        d.play(game.roundSeconds - 1f)
        game.botPlaceFish(0, 2, 6f, 180f, 120f)
        game.botHookNow(0)
        val reel = ReelFinger(game)
        reel.press(5L, d.ms)
        assertTrue(d.play(25f) { _, ms -> reel.turn(careful(game), ms) })
        assertEquals("the fish still fighting got away", 0, game.botLanded)
        assertEquals(0, game.score)
        assertEquals(CastPhase.IDLE, game.botPhase)
        // Nothing new is cast once time's up.
        game.onTouch(TouchType.DOWN, 9L, 180f, 300f, d.ms)
        d.end()
        game.onTouch(TouchType.UP, 9L, 180f, 300f, d.ms)
        assertEquals(0, game.botCasts)
        assertEquals(0, game.score)
    }

    @Test
    fun aFishBeingLiftedOutAtTimeUpStillScores() {
        val game = FishingGame()
        val d = RoundDriver(game, 19L)
        d.play(game.roundSeconds - 0.3f)
        game.botPlaceFish(0, 0, 0.6f, 180f, FishingGame.DOCK_Z - FishingTuning.LAND_DIST - 3f)
        game.botHookNow(0)
        val reel = ReelFinger(game)
        reel.press(5L, d.ms)
        playUntil(d, 0.25f, { game.botPhase == CastPhase.LANDING }) { _, ms -> reel.turn(8f, ms) }
        assertEquals("landing before the buzzer", CastPhase.LANDING, game.botPhase)
        assertTrue(d.play(5f))
        d.end()
        assertEquals(1, game.botLanded)
        assertEquals(game.botPoints(0, 0.6f), game.score)
    }

    // ---------------------------------------------------------------- input

    @Test
    fun cancelInputDropsTheCastBeingWoundUp() {
        val game = FishingGame()
        val d = RoundDriver(game, 20L)
        d.play(0.3f)
        game.onTouch(TouchType.DOWN, 1L, 120f, 300f, d.ms)
        d.play(0.4f)
        assertEquals(CastPhase.CHARGE, game.botPhase)
        assertTrue(game.botPower > 0f)
        d.pause()
        assertEquals(CastPhase.IDLE, game.botPhase)
        d.play(0.3f)
        // The lost finger's late events cast nothing.
        game.onTouch(TouchType.MOVE, 1L, 200f, 300f, d.ms)
        game.onTouch(TouchType.UP, 1L, 200f, 300f, d.ms)
        assertEquals(0, game.botCasts)
        assertEquals(CastPhase.IDLE, game.botPhase)
        // A new finger casts.
        game.onTouch(TouchType.DOWN, 2L, 180f, 300f, d.ms)
        d.play(0.5f)
        game.onTouch(TouchType.UP, 2L, 180f, 300f, d.ms)
        assertEquals(1, game.botCasts)
        assertEquals(CastPhase.FLIGHT, game.botPhase)
    }

    @Test
    fun cancelInputLetsGoOfTheReel() {
        // The old boot: no runs, so winding always brings it closer.
        val boot = FishingGame.BOOT
        val (game, d) = hooked(21L, 4, 1f, 180f, 200f, slot = boot)
        val reel = ReelFinger(game)
        reel.press(1L, d.ms)
        d.play(0.5f) { _, ms -> reel.turn(8f, ms) }
        assertTrue(game.botCrankRate > 5f)
        d.pause()
        reel.lost()
        assertFalse(game.botCrankHeld)
        assertEquals(0f, game.botCrankRate, 0f)
        val before = game.botLineDist
        for (k in 1..10) {
            val a = k * 0.3f
            game.onTouch(TouchType.MOVE, 1L, FishingGame.REEL_CX + cos(a) * 46f, FishingGame.REEL_CY + sin(a) * 46f, d.ms + k * 16L)
        }
        d.play(0.2f)
        assertEquals("the lost finger winds nothing", 0f, game.botCrankRate, 0f)
        assertTrue(game.botLineDist >= before - 0.01f)
        assertEquals(CastPhase.FIGHT, game.botPhase)
        val again = ReelFinger(game)
        again.press(2L, d.ms)
        d.play(0.4f) { _, ms -> again.turn(8f, ms) }
        assertTrue("a new finger winds the fish in", game.botLineDist < before - 10f)
    }

    @Test
    fun castAndReelFingersAreTrackedByTheirIds() {
        val game = FishingGame()
        val d = RoundDriver(game, 22L)
        d.play(0.3f)
        // One finger holds a cast out to the left, another circles the reel.
        game.onTouch(TouchType.DOWN, 1L, 60f, 300f, d.ms)
        val reel = ReelFinger(game)
        reel.press(2L, d.ms)
        d.play(0.3f) { _, ms -> reel.turn(10f, ms) }
        assertTrue(game.botCrankHeld)
        assertEquals(CastPhase.CHARGE, game.botPhase)
        // A stranger's events change nothing.
        game.onTouch(TouchType.MOVE, 7L, 300f, 300f, d.ms)
        game.onTouch(TouchType.UP, 7L, 300f, 300f, d.ms)
        assertEquals(CastPhase.CHARGE, game.botPhase)
        assertTrue(game.botCrankHeld)
        game.onTouch(TouchType.UP, 1L, 60f, 300f, d.ms)
        assertEquals(1, game.botCasts)
        assertTrue("the reel finger is still on", game.botCrankHeld)
        d.play(1.5f) { _, ms -> reel.turn(0f, ms) }
        assertTrue("the cast went left: ${game.botLureX}", game.botLureX < FishingGame.DOCK_X - 20f)
    }

    // ---------------------------------------------------------------- robustness

    /** Random play: taps, holds, casts, circles of any speed either way, pauses. */
    private fun fuzzRound(game: FishingGame, seed: Long, botSeed: Int, check: Boolean): Int {
        val rng = Random(botSeed)
        val reel = ReelFinger(game)
        var next = 0f
        var castId = -1L
        var nextId = 10L
        var omega = 0f
        val slots = game.botSlots
        playRound(game, seed) { t, ms ->
            if (t >= next) {
                next = t + 0.2f + rng.nextFloat() * 0.8f
                when (rng.nextInt(6)) {
                    0 -> if (castId < 0) {
                        castId = nextId++
                        game.onTouch(TouchType.DOWN, castId, rng.nextFloat() * 360f, rng.nextFloat() * 460f, ms)
                    }
                    1 -> if (castId >= 0) {
                        game.onTouch(TouchType.UP, castId, rng.nextFloat() * 360f, 300f, ms)
                        castId = -1L
                    }
                    2 -> if (!reel.down) reel.press(nextId++, ms) else reel.lift(ms)
                    3 -> omega = (rng.nextFloat() - 0.3f) * 30f
                    4 -> if (rng.nextFloat() < 0.15f) {
                        game.cancelInput()
                        reel.lost()
                        castId = -1L
                    }
                    else -> game.onTouch(TouchType.MOVE, 999L, rng.nextFloat() * 360f, rng.nextFloat() * 640f, ms)
                }
            }
            reel.turn(omega, ms)
            if (check) {
                assertEquals("the fish pool never grows", slots, game.botSlots)
                assertTrue(game.botTension.isFinite() && game.botLineDist.isFinite() && game.botPower.isFinite())
                assertTrue(game.botLureX.isFinite() && game.botLureZ.isFinite())
                var busy = 0
                for (i in 0 until slots) {
                    assertTrue("fish $i went NaN", game.botFishX(i).isFinite() && game.botFishY(i).isFinite() && game.botFishZ(i).isFinite())
                    val m = game.botFishMode(i)
                    if (m == FishMode.HOOKED || m == FishMode.LANDED) busy++
                }
                assertTrue("one fish on the line at most", busy <= 1)
            }
        }
        return game.score
    }

    @Test
    fun randomPlayAlwaysFinishesWithoutNaN() {
        val game = FishingGame()
        for (k in 0 until 8) fuzzRound(game, 100L + k, k, check = true)
        assertEquals(FishingTuning.FISH_SLOTS + 1, game.botSlots)
    }

    @Test
    fun resetLeavesNothingFromTheLastRound() {
        val game = FishingGame()
        var d = RoundDriver(game, 23L)
        d.play(0.2f)
        game.botPlaceFish(0, 1, 2f, 180f, 300f)
        game.botHookNow(0)
        val reel = ReelFinger(game)
        reel.press(1L, d.ms)
        d.play(0.6f) { _, ms -> reel.turn(8f, ms) }
        game.onTouch(TouchType.DOWN, 2L, 100f, 300f, d.ms)
        // A new round starts on the same machine mid-fight.
        d = RoundDriver(game, 24L)
        assertEquals(CastPhase.IDLE, game.botPhase)
        assertEquals(-1, game.botHooked)
        assertEquals(-1, game.botSuitor)
        assertEquals(0f, game.botTension, 0f)
        assertEquals(0f, game.botLineDist, 0f)
        assertFalse(game.botCrankHeld)
        assertEquals(0f, game.botCrankRate, 0f)
        assertEquals(0, game.score)
        assertEquals(0, game.botCasts + game.botLanded + game.botSnaps + game.botThrown + game.botMissed + game.botTooSoon)
        for (i in 0 until game.botSlots) assertEquals(FishMode.SWIM, game.botFishMode(i))
        // The old fingers are forgotten.
        game.onTouch(TouchType.MOVE, 1L, FishingGame.REEL_CX, FishingGame.REEL_CY + 46f, d.ms)
        game.onTouch(TouchType.UP, 2L, 100f, 300f, d.ms)
        d.play(0.2f)
        assertEquals(0f, game.botCrankRate, 0f)
        assertEquals(0, game.botCasts)
        // And a round replays exactly whether or not the machine played before.
        val fresh = fuzzRound(FishingGame(), 77L, 5, check = false)
        val reused = FishingGame()
        fuzzRound(reused, 78L, 6, check = false)
        assertEquals(fresh, fuzzRound(reused, 77L, 5, check = false))
    }
}
