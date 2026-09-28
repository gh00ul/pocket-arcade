package com.pocketarcade.games.fishing

import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.TouchType
import com.pocketarcade.games.Stats
import com.pocketarcade.games.assertPayoutBands
import com.pocketarcade.games.playRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * A finger drawing circles on the reel through the real touch path: it keeps its own angle
 * round the reel's centre and sends a MOVE about every 16 ms, like a phone's touch sampling.
 */
internal class ReelFinger(private val game: FishingGame, private val radius: Float = 46f) {
    var id = -1L
        private set
    private var angle = 0f
    private var lastSent = 0L

    val down: Boolean get() = id >= 0

    fun press(newId: Long, ms: Long) {
        id = newId
        angle = 0f
        lastSent = ms
        game.onTouch(TouchType.DOWN, id, x(), y(), ms)
    }

    /** Turns the finger at [omega] radians per second (positive is clockwise, reeling in) for one step. */
    fun turn(omega: Float, ms: Long) {
        if (id < 0) return
        angle += omega * FIXED_DT
        if (ms - lastSent >= 16L) {
            lastSent = ms
            game.onTouch(TouchType.MOVE, id, x(), y(), ms)
        }
    }

    fun lift(ms: Long) {
        if (id < 0) return
        game.onTouch(TouchType.UP, id, x(), y(), ms)
        id = -1L
    }

    /** The host dropped every pointer (a pause): this finger is simply gone. */
    fun lost() {
        id = -1L
    }

    private fun x() = FishingGame.REEL_CX + cos(angle) * radius
    private fun y() = FishingGame.REEL_CY + sin(angle) * radius
}

/** Plays Gone Fishing headlessly through the touch path with bots of different skill. */
class FishingSimulationTest {
    private var roundSeed = 1L

    /**
     * A good angler: casts just short of the most valuable fish it can see (clear of the old
     * boot), keeps a finger on the reel, strikes quickly, and winds hard while the fish rests but
     * almost stops when it thrashes and runs, watching the tension gauge.
     */
    private fun goodAngler(rounds: Int, seed: Int): Stats {
        val stats = Stats("fishing good")
        val rng = Random(seed)
        val game = FishingGame()
        var landed = 0
        var snaps = 0
        var thrown = 0
        var missed = 0
        repeat(rounds) {
            var nextId = 1L
            val reel = ReelFinger(game)
            var castId = -1L
            var wantPower = 0f
            var biteSeen = -1f
            var waitStart = 0f
            var omega = 0f
            var decideAt = 0f
            val aim = FloatArray(2)
            playRound(game, roundSeed++, stats) { t, ms ->
                when (game.botPhase) {
                    CastPhase.IDLE -> {
                        if (castId < 0 && t > 0.2f) {
                            val target = bestFish(game)
                            if (target >= 0) {
                                // Just short of the fish, so the splash doesn't scare it off.
                                val fx = game.botFishX(target)
                                val fz = game.botFishZ(target)
                                val d = hypot(fx - FishingGame.DOCK_X, fz - FishingGame.DOCK_Z)
                                val k = ((d - 30f) / d).coerceAtLeast(0f)
                                game.botAimFor(FishingGame.DOCK_X + (fx - FishingGame.DOCK_X) * k, FishingGame.DOCK_Z + (fz - FishingGame.DOCK_Z) * k, aim)
                                wantPower = aim[1]
                                castId = nextId++
                                game.onTouch(TouchType.DOWN, castId, aim[0].coerceIn(4f, 356f), 300f, ms)
                            }
                        }
                        reel.turn(0f, ms)
                    }
                    CastPhase.CHARGE -> {
                        if (castId >= 0 && kotlin.math.abs(game.botPower - wantPower) < 0.025f) {
                            game.onTouch(TouchType.UP, castId, aim[0].coerceIn(4f, 356f), 300f, ms)
                            castId = -1L
                            waitStart = t
                            biteSeen = -1f
                        }
                    }
                    CastPhase.FLIGHT, CastPhase.WAIT -> {
                        if (!reel.down) reel.press(nextId++, ms)
                        if (game.botBiting) {
                            if (biteSeen < 0f) biteSeen = t
                            reel.turn(if (t - biteSeen > 0.2f) 10f else 0f, ms)
                        } else {
                            biteSeen = -1f
                            // Nothing interested for a long while: wind in and try elsewhere.
                            reel.turn(if (game.botSuitor < 0 && t - waitStart > 7f) 14f else 0f, ms)
                        }
                    }
                    CastPhase.FIGHT -> {
                        if (t >= decideAt) {
                            decideAt = t + 0.08f + rng.nextFloat() * 0.04f
                            val tension = game.botTension
                            omega = when {
                                game.botFishSpecies(game.botHooked) == 4 -> 8f
                                tension > 0.9f -> 0f
                                game.botPull != Pull.REST -> 1.5f
                                tension > 0.75f -> 5f
                                tension < 0.3f -> 12f
                                else -> 10f
                            }
                        }
                        reel.turn(omega, ms)
                    }
                    CastPhase.LANDING, CastPhase.RECOVER -> reel.turn(0f, ms)
                }
            }
            landed += game.botLanded
            snaps += game.botSnaps
            thrown += game.botThrown
            missed += game.botMissed
            assertEquals("failsafes tripped", 0, game.botFailsafeTrips)
        }
        println("good angler: landed $landed, snapped $snaps, thrown $thrown, missed $missed over $rounds rounds")
        return stats
    }

    /** The fish (not the boot) worth the most, discounted by distance, clear of the boot. */
    private fun bestFish(game: FishingGame): Int {
        var best = -1
        var bestScore = 0f
        val boot = FishingGame.BOOT
        for (i in 0 until FishingTuning.FISH_SLOTS) {
            if (game.botFishMode(i) != FishMode.SWIM) continue
            val x = game.botFishX(i)
            val z = game.botFishZ(i)
            if (hypot(x - game.botFishX(boot), z - game.botFishZ(boot)) < 75f) continue
            val d = hypot(x - FishingGame.DOCK_X, z - FishingGame.DOCK_Z)
            val s = game.botPoints(game.botFishSpecies(i), game.botFishWeight(i)) / (1f + d / 250f)
            if (s > bestScore) {
                bestScore = s
                best = i
            }
        }
        return best
    }

    /**
     * A casual player: casts anywhere at whatever power, gets a finger to the reel late,
     * strikes late, fidgets with the reel while waiting and winds at one speed, only now and
     * then noticing the gauge.
     */
    private fun casualAngler(rounds: Int, seed: Int): Stats {
        val stats = Stats("fishing casual")
        val rng = Random(seed)
        val game = FishingGame()
        var landed = 0
        var snaps = 0
        var thrown = 0
        var missed = 0
        var early = 0
        var casts = 0
        repeat(rounds) {
            var nextId = 1L
            val reel = ReelFinger(game)
            var castId = -1L
            var castX = 180f
            var castAt = 0f
            var holdFor = 0f
            var idleSince = -1f
            var reelAt = 0f
            var reactIn = 0f
            var biteSeen = -1f
            var fidgetAt = 0f
            var waitStart = 0f
            var omega = 8f
            var lastPhase = CastPhase.IDLE
            var checkAt = 0f
            playRound(game, roundSeed++, stats) { t, ms ->
                val phase = game.botPhase
                if (phase == CastPhase.FIGHT && lastPhase != CastPhase.FIGHT) omega = 3f + rng.nextFloat() * 13f
                lastPhase = phase
                when (phase) {
                    CastPhase.IDLE -> {
                        if (idleSince < 0f) idleSince = t + 0.3f + rng.nextFloat() * 0.9f
                        if (castId < 0 && t >= idleSince) {
                            castId = nextId++
                            castX = 30f + rng.nextFloat() * 300f
                            castAt = t
                            holdFor = 0.25f + rng.nextFloat() * 1.35f
                            game.onTouch(TouchType.DOWN, castId, castX, 250f + rng.nextFloat() * 170f, ms)
                        }
                        reel.turn(0f, ms)
                    }
                    CastPhase.CHARGE -> {
                        if (castId >= 0 && t - castAt >= holdFor) {
                            game.onTouch(TouchType.UP, castId, castX, 300f, ms)
                            castId = -1L
                            idleSince = -1f
                            reelAt = t + 0.3f + rng.nextFloat() * 1.2f
                            fidgetAt = if (rng.nextFloat() < 0.3f) t + 1f + rng.nextFloat() * 3f else -1f
                            waitStart = t
                            biteSeen = -1f
                        }
                    }
                    CastPhase.FLIGHT, CastPhase.WAIT -> {
                        idleSince = -1f
                        if (!reel.down && t >= reelAt) reel.press(nextId++, ms)
                        if (game.botBiting) {
                            if (biteSeen < 0f) {
                                biteSeen = t
                                reactIn = 0.35f + rng.nextFloat() * 0.95f
                            }
                            reel.turn(if (t - biteSeen > reactIn) omega.coerceAtLeast(6f) else 0f, ms)
                        } else {
                            biteSeen = -1f
                            val fidget = fidgetAt > 0f && t in fidgetAt..fidgetAt + 0.3f
                            val bored = t - waitStart > 9f
                            reel.turn(if (fidget) 4f else if (bored) 10f else 0f, ms)
                        }
                    }
                    CastPhase.FIGHT -> {
                        if (t >= checkAt) {
                            checkAt = t + 1f
                            if (game.botTension > 0.95f) omega *= 0.6f
                            if (game.botTension < 0.25f) omega *= 1.4f
                            omega = omega.coerceIn(2f, 18f)
                        }
                        reel.turn(omega, ms)
                    }
                    CastPhase.LANDING, CastPhase.RECOVER -> {
                        idleSince = -1f
                        reel.turn(0f, ms)
                    }
                }
            }
            landed += game.botLanded
            snaps += game.botSnaps
            thrown += game.botThrown
            missed += game.botMissed
            early += game.botTooSoon
            casts += game.botCasts
        }
        println("casual angler: $casts casts, landed $landed, snapped $snaps, thrown $thrown, missed $missed, too soon $early over $rounds rounds")
        return stats
    }

    @Test
    fun fishingPaysOutAndRewardsSkill() {
        val rounds = 12
        val good = goodAngler(rounds, seed = 31)
        val casual = casualAngler(rounds, seed = 32)
        println(good)
        println(casual)
        assertPayoutBands(good, casual)
        assertTrue("a good angler lands fish every round: ${good.scores}", good.scores.all { it > 0 })
    }
}
