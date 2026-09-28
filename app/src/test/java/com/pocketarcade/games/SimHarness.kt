package com.pocketarcade.games

import com.pocketarcade.engine.AudioSynth
import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.Haptics
import com.pocketarcade.engine.TouchType
import org.junit.Assert.assertTrue
import kotlin.random.Random

/*
 * Shared plumbing for the headless game tests: game services, a round runner that plays a round
 * exactly the way the host (ui/GameHostScreen.kt) does, touch helpers and the payout bands every
 * machine must hit. Per-game tests in the game sub-packages use it too.
 */

/** Collectibles (claw plushies) won by bots, in the order they were won. */
internal val simCollected = ArrayList<String>()

/** Game services for headless rounds: silent audio, no vibrator. */
internal val simFx = GameFx(AudioSynth(), Haptics(null)) { simCollected += it }

/** Scores and tickets of a batch of rounds played by one bot. */
internal class Stats(val name: String) {
    val scores = ArrayList<Int>()
    val tickets = ArrayList<Int>()
    val avgScore get() = scores.average()
    val avgTickets get() = tickets.average()
    override fun toString() = "%-22s score %7.1f  tickets %6.1f  (min %d, max %d)".format(
        name, avgScore, avgTickets, tickets.minOrNull() ?: 0, tickets.maxOrNull() ?: 0,
    )
}

/** Seconds of ENDING the host plays after a round finishes, before it reads the score. */
internal const val ENDING_SECONDS = 1.2f

/**
 * Drives one round of [game] step by step the way the host does: [play] is the PLAYING phase
 * (the clock runs down, touches reach the game), [pause] is what happens on Back, the close
 * button or the app going to the background, and [end] is the ENDING tail.
 */
internal class RoundDriver(val game: MiniGame, seed: Long) {
    /** Simulated seconds of PLAYING so far. */
    var t = 0f
        private set
    var timeLeft = game.roundSeconds
        private set
    var steps = 0
        private set
    val ms: Long get() = (t * 1000f).toLong()

    init {
        (game as? BaseMiniGame)?.seed = seed
        game.start(simFx)
    }

    /**
     * Steps PLAYING for up to [seconds], stopping early once the game reports finished (which
     * also cancels input, as the host does when it switches to ENDING). [bot] runs before every
     * step with (simulated seconds, simulated millis). Returns whether the round finished.
     */
    fun play(seconds: Float, bot: ((Float, Long) -> Unit)? = null): Boolean {
        val until = steps + (seconds / FIXED_DT).toInt()
        while (!game.finished && steps < until) {
            bot?.invoke(t, ms)
            timeLeft = (timeLeft - FIXED_DT).coerceAtLeast(0f)
            game.update(FIXED_DT, timeLeft)
            t += FIXED_DT
            steps++
        }
        if (game.finished) game.cancelInput()
        return game.finished
    }

    /**
     * The host pauses mid-round: it stops forwarding touches and tells the game. The clock
     * doesn't move and nothing steps while paused, so a finger lifted meanwhile is simply lost.
     */
    fun pause() = game.cancelInput()

    /** The host's ENDING tail: [ENDING_SECONDS] of `update(dt, 0f)` whatever the game is doing. */
    fun end() {
        var phaseT = 0f
        while (true) {
            phaseT += FIXED_DT
            game.update(FIXED_DT, 0f)
            if (phaseT > ENDING_SECONDS) break
        }
    }
}

/**
 * Plays one whole round of [game] like the host: PLAYING until the game finishes (it must, within
 * 20 s of the clock running out), then the ENDING tail, then reads score and tickets (the host
 * reads them after ENDING) into [stats]. [bot] is called every PLAYING step with (simulated
 * seconds, simulated millis). Returns the driver so callers can inspect the finished round.
 */
internal fun playRound(game: MiniGame, seed: Long, stats: Stats? = null, bot: (Float, Long) -> Unit): RoundDriver {
    val d = RoundDriver(game, seed)
    d.play(game.roundSeconds + 20f, bot)
    assertTrue("${game.title} never finished", game.finished)
    d.end()
    stats?.scores?.add(game.score)
    stats?.tickets?.add(game.ticketsFor(game.score) + game.bonusTickets)
    return d
}

/** A straight-line flick ending at release, sampled like a real finger. */
internal fun flick(game: MiniGame, id: Long, x0: Float, y0: Float, vx: Float, vy: Float, ms: Long) {
    game.onTouch(TouchType.DOWN, id, x0, y0, ms)
    for (k in 1..6) {
        val dt = k * 0.012f
        game.onTouch(TouchType.MOVE, id, x0 + vx * dt, y0 + vy * dt, ms + (dt * 1000).toLong())
    }
    game.onTouch(TouchType.UP, id, x0 + vx * 0.072f, y0 + vy * 0.072f, ms + 72)
}

/**
 * A flick with no MOVE samples (DOWN, then UP 72 ms later), so the release velocity is exactly
 * ([vx], [vy]) and the thing being flicked stays where the finger landed.
 */
internal fun flickNoMove(game: MiniGame, id: Long, x0: Float, y0: Float, vx: Float, vy: Float, ms: Long) {
    game.onTouch(TouchType.DOWN, id, x0, y0, ms)
    game.onTouch(TouchType.UP, id, x0 + vx * 0.072f, y0 + vy * 0.072f, ms + 72)
}

/** A tap: DOWN, then UP at the same spot [holdMs] later. */
internal fun tap(game: MiniGame, id: Long, x: Float, y: Float, ms: Long, holdMs: Long = 40L) {
    game.onTouch(TouchType.DOWN, id, x, y, ms)
    game.onTouch(TouchType.UP, id, x, y, ms + holdMs)
}

/** Roughly normal noise with unit spread (sum of six uniforms). */
internal fun gaussian(rng: Random): Float {
    var u = 0f
    repeat(6) { u += rng.nextFloat() }
    return (u - 3f) / 0.707f
}

/**
 * The payout bands every machine must hit: a good player averages 8..70 tickets a round, a
 * casual one still gets at least 2, and skill never scores less than carelessness.
 */
internal fun assertPayoutBands(good: Stats, casual: Stats) {
    assertTrue("$good pays too little", good.avgTickets >= 8.0)
    assertTrue("$good pays too much", good.avgTickets <= 70.0)
    assertTrue("$casual pays nothing", casual.avgTickets >= 2.0)
    assertTrue("skill should pay: $good vs $casual", good.avgScore >= casual.avgScore)
}
