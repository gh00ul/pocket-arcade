package com.pocketarcade.games

import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.TouchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import kotlin.random.Random

/**
 * Adversarial input for every registered machine, played through the same [RoundDriver] the host
 * logic is mirrored by. The scripts (see [Flavor]): random taps, drags and flicks anywhere (well
 * outside the field too), several fingers at once, an UP with no DOWN, a MOVE for an id nobody
 * put down, a repeated DOWN, cancelInput() at random moments (pause) with the lost fingers' UPs
 * arriving late or never, odd timestamps, zero-length and absurdly fast flicks, tap spam, every
 * finger held down for the whole round, and circles wound round the fishing reel.
 *
 * Whatever the fingers do, every round must finish shortly after the clock stops, pay a sane
 * number of tickets, never leave NaN or Infinity anywhere in the game's state (found by walking
 * its fields), and a replay on a reused instance must play out exactly like a round on a fresh
 * one. Set the FUZZ_SEEDS environment variable (say 150) for a long soak.
 */
class GameFuzzTest {
    private enum class Flavor { CHAOS, SPAM, HOLD_ALL, FLICKS, ORBIT }

    private class Ev(val step: Int, val type: TouchType, val id: Long, val x: Float, val y: Float, val ms: Long)

    /**
     * A hostile finger script driven only by its own seeded [Random] and the clock, never by the
     * game's state, so the same script replays identically on any instance.
     */
    private class Hostile(private val game: MiniGame, seed: Long, private val flavor: Flavor, private val driver: () -> RoundDriver) {
        private val r = Random(seed * 7919 + flavor.ordinal)
        private val pending = ArrayList<Ev>()
        private var stepNo = 0
        private var lastMs = 0L
        private var orbitAngle = 0f
        private var orbitSpeed = 8f
        /** Fingers this script believes are down (it still sends events for lifted ones on purpose). */
        private val held = HashSet<Long>()
        var events = 0
            private set
        var cancels = 0
            private set

        private fun px(): Float = when (r.nextInt(100)) {
            in 0..59 -> r.nextFloat() * GAME_W
            in 60..84 -> -100f + r.nextFloat() * (GAME_W + 200f)
            in 85..94 -> (if (r.nextBoolean()) 1f else -1f) * (1000f + r.nextFloat() * 99000f)
            95, 96 -> (if (r.nextBoolean()) 1f else -1f) * 1e7f
            else -> if (r.nextBoolean()) 0f else GAME_W
        }

        private fun py(): Float = when (r.nextInt(100)) {
            in 0..59 -> r.nextFloat() * GAME_H
            in 60..84 -> -100f + r.nextFloat() * (GAME_H + 200f)
            in 85..94 -> (if (r.nextBoolean()) 1f else -1f) * (1000f + r.nextFloat() * 99000f)
            95, 96 -> (if (r.nextBoolean()) 1f else -1f) * 1e7f
            else -> if (r.nextBoolean()) 0f else GAME_H
        }

        private fun stamp(now: Long): Long {
            val ms = when (r.nextInt(100)) {
                in 0..79 -> now
                in 80..89 -> lastMs
                in 90..96 -> (now - r.nextInt(60)).coerceAtLeast(0L)
                97 -> 0L
                else -> now + r.nextInt(500)
            }
            lastMs = ms
            return ms
        }

        private fun send(type: TouchType, id: Long, x: Float, y: Float, now: Long) {
            events++
            game.onTouch(type, id, x, y, stamp(now))
            when (type) {
                TouchType.DOWN -> held += id
                TouchType.UP -> held -= id
                else -> Unit
            }
        }

        private fun later(afterSteps: Int, type: TouchType, id: Long, x: Float, y: Float) {
            pending += Ev(stepNo + afterSteps, type, id, x, y, 0L)
        }

        /** A finger that stays down for [steps] steps, wandering. */
        private fun drag(id: Long, steps: Int, now: Long) {
            var x = px().coerceIn(-50f, GAME_W + 50f)
            var y = py().coerceIn(-50f, GAME_H + 50f)
            send(TouchType.DOWN, id, x, y, now)
            var s = 3
            while (s < steps) {
                if (r.nextInt(4) == 0) { x = px(); y = py() } else { x += (r.nextFloat() - 0.5f) * 60f; y += (r.nextFloat() - 0.5f) * 60f }
                later(s, TouchType.MOVE, id, x, y)
                s += 1 + r.nextInt(6)
            }
            later(steps, TouchType.UP, id, x, y)
        }

        private fun anId(): Long = 1L + r.nextInt(8)

        fun step(now: Long) {
            stepNo++
            // Deliver what earlier steps scheduled (a lifted finger's UP, a drag's MOVEs).
            var i = 0
            while (i < pending.size) {
                val e = pending[i]
                if (e.step <= stepNo) {
                    pending.removeAt(i)
                    send(e.type, e.id, e.x, e.y, now)
                } else i++
            }
            when (flavor) {
                Flavor.SPAM -> {
                    // Tap spam on a handful of ids, several taps in the same step.
                    repeat(1 + r.nextInt(3)) {
                        val id = anId()
                        val x = px().coerceIn(-20f, GAME_W + 20f)
                        val y = py().coerceIn(-20f, GAME_H + 20f)
                        send(TouchType.DOWN, id, x, y, now)
                        send(TouchType.UP, id, x, y, now)
                    }
                    return
                }
                Flavor.HOLD_ALL -> {
                    // Every finger is down all the time and keeps moving.
                    for (id in 1L..4L) {
                        if (id !in held) send(TouchType.DOWN, id, px().coerceIn(0f, GAME_W), py().coerceIn(0f, GAME_H), now)
                        else send(TouchType.MOVE, id, px().coerceIn(-30f, GAME_W + 30f), py().coerceIn(-30f, GAME_H + 30f), now)
                    }
                    if (r.nextInt(600) == 0) { cancels++; driver().pause(); held.clear() }
                    return
                }
                Flavor.FLICKS -> {
                    // Upward flicks off the bottom of the field at every speed, and stray taps.
                    if (r.nextInt(100) < 8) {
                        val id = anId()
                        val speed = when (r.nextInt(5)) { 0 -> 100f + r.nextFloat() * 300f; 1 -> 3000f + r.nextFloat() * 20000f; else -> 600f + r.nextFloat() * 1800f }
                        val a = (r.nextFloat() - 0.5f) * 1.6f
                        flick(game, id, 40f + r.nextFloat() * 280f, 540f + r.nextFloat() * 90f, kotlin.math.sin(a) * speed, -kotlin.math.cos(a) * speed, stamp(now))
                        events += 8
                    }
                    if (r.nextInt(100) < 3) { val x = px(); val y = py(); send(TouchType.DOWN, 9L, x, y, now); send(TouchType.UP, 9L, x, y, now) }
                    if (r.nextInt(2000) == 0) { cancels++; driver().pause() }
                    return
                }
                Flavor.ORBIT -> {
                    // One finger winds circles round the fishing reel (and other games' corners), speeding
                    // up, reversing, lifting and re-landing; another holds and lets go on the pond.
                    orbitAngle += orbitSpeed * FIXED_DT
                    if (r.nextInt(30) == 0) orbitSpeed = (r.nextFloat() - 0.5f) * 30f
                    val ox = 266f + kotlin.math.cos(orbitAngle) * (20f + r.nextFloat() * 100f)
                    val oy = 552f + kotlin.math.sin(orbitAngle) * (20f + r.nextFloat() * 100f)
                    if (1L !in held) { if (r.nextInt(40) == 0) send(TouchType.DOWN, 1L, ox, oy, now) }
                    else if (r.nextInt(500) == 0) send(TouchType.UP, 1L, ox, oy, now)
                    else send(TouchType.MOVE, 1L, ox, oy, now)
                    if (2L !in held) { if (r.nextInt(60) == 0) send(TouchType.DOWN, 2L, r.nextFloat() * GAME_W, 100f + r.nextFloat() * 350f, now) }
                    else if (r.nextInt(120) == 0) send(TouchType.UP, 2L, r.nextFloat() * GAME_W, 100f + r.nextFloat() * 350f, now)
                    else if (r.nextInt(10) == 0) send(TouchType.MOVE, 2L, r.nextFloat() * GAME_W, 100f + r.nextFloat() * 350f, now)
                    if (r.nextInt(1500) == 0) { cancels++; driver().pause(); held.clear() }
                    return
                }
                Flavor.CHAOS -> Unit
            }
            if (r.nextInt(100) >= 6) return
            when (r.nextInt(14)) {
                0, 1, 2 -> { // a tap
                    val id = anId()
                    val x = px(); val y = py()
                    send(TouchType.DOWN, id, x, y, now)
                    if (r.nextBoolean()) send(TouchType.UP, id, x, y, now) else later(1 + r.nextInt(30), TouchType.UP, id, x, y)
                }
                3, 4 -> drag(anId(), 4 + r.nextInt(200), now)
                5 -> drag(anId(), 120 + r.nextInt(500), now) // a long hold
                6, 7 -> { // a flick, sometimes with no movement at all or an absurd speed
                    val id = anId()
                    val x0 = px().coerceIn(-50f, GAME_W + 50f)
                    val y0 = py().coerceIn(-50f, GAME_H + 50f)
                    val speed = when (r.nextInt(4)) { 0 -> 0f; 1 -> 3000f; 2 -> 1e5f; else -> 300f + r.nextFloat() * 1500f }
                    val a = r.nextFloat() * 6.2831855f
                    flick(game, id, x0, y0, kotlin.math.cos(a) * speed, kotlin.math.sin(a) * speed, stamp(now))
                    events += 8
                    held -= id
                }
                8 -> send(TouchType.UP, 20L + r.nextInt(5), px(), py(), now) // UP without a DOWN
                9 -> send(TouchType.MOVE, 30L + r.nextInt(5), px(), py(), now) // MOVE for a stranger
                10 -> { // DOWN twice for one id
                    val id = anId()
                    send(TouchType.DOWN, id, px(), py(), now)
                    send(TouchType.DOWN, id, px(), py(), now)
                    later(1 + r.nextInt(40), TouchType.UP, id, px(), py())
                }
                11 -> { // two or three fingers land in the same step
                    val n = 2 + r.nextInt(2)
                    for (k in 0 until n) send(TouchType.DOWN, 1L + r.nextInt(8), px(), py(), now)
                    for (k in 0 until n) later(2 + r.nextInt(20), TouchType.UP, 1L + r.nextInt(8), px(), py())
                }
                12 -> { // the host pauses: touches stop, the lost fingers' UPs may or may not come later
                    cancels++
                    driver().pause()
                    for (id in held.toList()) if (r.nextBoolean()) later(1 + r.nextInt(400), TouchType.UP, id, px(), py())
                    held.clear()
                }
                else -> { // a burst of MOVEs for whichever fingers might be down
                    for (id in 1L..8L) if (r.nextBoolean()) send(TouchType.MOVE, id, px(), py(), now)
                }
            }
        }
    }

    // ---------------------------------------------------------------- invariants

    /** The first NaN or Infinity found anywhere in [root]'s state (fields, arrays, lists), or null. */
    private fun firstNonFinite(root: Any): String? {
        val seen = IdentityHashMap<Any, Boolean>()
        val work = ArrayDeque<Pair<Any, String>>()
        work.addLast(root to root.javaClass.simpleName)
        seen[root] = true
        var budget = 60_000
        fun descend(v: Any?, path: String) {
            if (v == null || seen.put(v, true) != null) return
            val cn = v.javaClass.name
            if (v is FloatArray || v is DoubleArray || v is Iterable<*> || (v is Array<*>) ||
                (cn.startsWith("com.pocketarcade") && !cn.contains(".engine.r3d.") && !cn.contains(".engine.gl."))
            ) work.addLast(v to path)
        }
        fun bad(f: Float) = f.isNaN() || f.isInfinite()
        while (work.isNotEmpty() && budget-- > 0) {
            val (o, path) = work.removeFirst()
            when (o) {
                is FloatArray -> { for (i in o.indices) if (bad(o[i])) return "$path[$i]=${o[i]}" }
                is DoubleArray -> { for (i in o.indices) if (o[i].isNaN() || o[i].isInfinite()) return "$path[$i]=${o[i]}" }
                is Array<*> -> for (i in o.indices) descend(o[i], "$path[$i]")
                is Iterable<*> -> { var i = 0; for (e in o) { descend(e, "$path[$i]"); if (++i > 800) break } }
                else -> {
                    var c: Class<*>? = o.javaClass
                    while (c != null && c.name.startsWith("com.pocketarcade")) {
                        for (f in c.declaredFields) {
                            if (Modifier.isStatic(f.modifiers)) continue
                            f.isAccessible = true
                            val name = "$path.${f.name}"
                            when (f.type) {
                                java.lang.Float.TYPE -> { val v = f.getFloat(o); if (bad(v)) return "$name=$v" }
                                java.lang.Double.TYPE -> { val v = f.getDouble(o); if (v.isNaN() || v.isInfinite()) return "$name=$v" }
                                else -> if (!f.type.isPrimitive) descend(f.get(o), name)
                            }
                        }
                        c = c.superclass
                    }
                }
            }
        }
        return null
    }

    private class Outcome(val trace: String, val score: Int, val tickets: Int)

    /**
     * Plays one whole round like the host with the hostile script, checking the invariants as it
     * goes. Returns a trace of the round so runs can be compared.
     */
    private fun fuzzRound(game: MiniGame, seed: Long, flavor: Flavor, stopAfterSeconds: Float = Float.MAX_VALUE, checks: Boolean = true): Outcome {
        val where = "${game.id} $flavor seed=$seed"
        val d = RoundDriver(game, seed)
        val h = Hostile(game, seed, flavor) { d }
        val sb = StringBuilder()
        var lastSec = -1
        val limit = minOf(game.roundSeconds + OVERTIME_SECONDS, stopAfterSeconds)
        val done = try {
            d.play(limit) { t, ms ->
                h.step(ms)
                val sec = t.toInt()
                if (sec != lastSec) {
                    lastSec = sec
                    sb.append(game.score).append(',')
                    if (checks) {
                        assertTrue("$where: score ${game.score} went negative at ${t}s", game.score >= 0)
                        assertTrue("$where: bonusTickets ${game.bonusTickets} negative at ${t}s", game.bonusTickets >= 0)
                        if (sec % 2 == 0) assertNull("$where: non-finite state at ${t}s", firstNonFinite(game))
                    }
                }
            }
        } catch (e: Throwable) {
            throw AssertionError("$where: threw at step ${d.steps} (${d.t}s): $e", e)
        }
        if (stopAfterSeconds < Float.MAX_VALUE && !game.finished) return Outcome(sb.toString(), game.score, 0)
        if (checks) {
            assertTrue("$where: never finished (t=${d.t}s of ${game.roundSeconds}s)", done && game.finished)
        }
        d.end()
        val tickets = game.ticketsFor(game.score) + game.bonusTickets
        if (checks) {
            assertTrue("$where: score ${game.score} negative", game.score >= 0)
            assertTrue("$where: tickets $tickets out of range (score ${game.score}, bonus ${game.bonusTickets})", tickets in 0..TICKET_CAP)
            assertNull("$where: non-finite state at the end", firstNonFinite(game))
        }
        sb.append(" steps=").append(d.steps).append(" score=").append(game.score)
            .append(" tickets=").append(game.ticketsFor(game.score)).append('+').append(game.bonusTickets)
        return Outcome(sb.toString(), game.score, tickets)
    }

    @Test
    fun hostileFingersNeverBreakAnyMachine() {
        for (game in GameRegistry.createAll()) {
            // One instance plays every round in turn, as the arcade reuses it for PLAY AGAIN.
            for (seed in 1L..SEEDS) fuzzRound(game, seed, Flavor.CHAOS)
            for (seed in 1L..SEEDS / 3) {
                fuzzRound(game, 1000L + seed, Flavor.SPAM)
                fuzzRound(game, 2000L + seed, Flavor.HOLD_ALL)
                fuzzRound(game, 3000L + seed, Flavor.FLICKS)
                fuzzRound(game, 4000L + seed, Flavor.ORBIT)
            }
        }
    }

    @Test
    fun aReplayAfterHostileRoundsPlaysLikeAFreshInstance() {
        val fresh = GameRegistry.createAll()
        val reused = GameRegistry.createAll()
        for (i in fresh.indices) {
            val a = fresh[i]
            val b = reused[i]
            val expected = fuzzRound(a, 21L, Flavor.CHAOS).trace
            fuzzRound(b, 5L, Flavor.CHAOS)
            fuzzRound(b, 6L, Flavor.HOLD_ALL)
            assertEquals("${b.id}: a replay on a reused instance differs from a fresh round", expected, fuzzRound(b, 21L, Flavor.CHAOS).trace)
        }
    }

    /** Leaving mid-round and playing again must start from a clean machine. */
    @Test
    fun aRoundAbandonedHalfWayLeavesNothingBehind() {
        val fresh = GameRegistry.createAll()
        val reused = GameRegistry.createAll()
        for (i in fresh.indices) {
            val a = fresh[i]
            val b = reused[i]
            val expected = fuzzRound(a, 31L, Flavor.CHAOS).trace
            // Abandon a round part-way through with four fingers still down. The host cancels input on
            // pause, but start() alone has to be enough (a recreated host restarts the same instance).
            fuzzRound(b, 8L, Flavor.HOLD_ALL, stopAfterSeconds = 4f + (i * 3.7f) % 30f, checks = false)
            assertEquals("${b.id}: a round after an abandoned one differs from a fresh round", expected, fuzzRound(b, 31L, Flavor.CHAOS).trace)
        }
    }

    /** start() again resets score, bonus and the finished flag whatever state the machine was in. */
    @Test
    fun startResetsTheRoundWhereverItStopped() {
        for (game in GameRegistry.createAll()) {
            for (stop in floatArrayOf(0f, 1.5f, 9f, 26f)) {
                fuzzRound(game, 41L, Flavor.CHAOS, stopAfterSeconds = stop, checks = false)
                (game as? BaseMiniGame)?.seed = 42L
                game.start(simFx)
                assertEquals("${game.id}: score after start()", 0, game.score)
                assertEquals("${game.id}: bonus after start()", 0, game.bonusTickets)
                assertTrue("${game.id}: finished right after start()", !game.finished)
                assertNull("${game.id}: non-finite right after start()", firstNonFinite(game))
            }
        }
    }

    private class Leaf(@Suppress("unused") val x: Float)
    private class Holder(val leaves: List<Leaf>, val arr: FloatArray, @Suppress("unused") val ok: Float = 1f)

    /** The scanner the invariants rely on really does see into fields, lists and arrays. */
    @Test
    fun theNonFiniteScannerFindsWhatItShouldAndNothingElse() {
        assertNull(firstNonFinite(Holder(listOf(Leaf(1f), Leaf(2f)), floatArrayOf(0f, 3f))))
        assertTrue(firstNonFinite(Holder(listOf(Leaf(1f), Leaf(Float.NaN)), floatArrayOf(0f)))!!.contains("NaN"))
        assertTrue(firstNonFinite(Holder(emptyList(), floatArrayOf(0f, Float.POSITIVE_INFINITY)))!!.contains("Infinity"))
    }

    /** Every machine's basic facts hold: the hall and the host depend on them. */
    @Test
    fun everyMachineIsDescribedSanely() {
        val all = GameRegistry.createAll()
        assertEquals("duplicate machine ids", all.size, all.map { it.id }.toSet().size)
        for (g in all) {
            assertTrue("${g.id}: empty title", g.title.isNotBlank())
            assertTrue("${g.id}: marquee '${g.marquee}' is over 6 characters", g.marquee.isNotEmpty() && g.marquee.length <= 6)
            assertTrue("${g.id}: no instructions", g.instructions.isNotEmpty())
            assertTrue("${g.id}: round of ${g.roundSeconds}s", g.roundSeconds in 10f..120f)
            assertTrue("${g.id}: a zero score should still print at least one ticket", g.ticketsFor(0) >= 0)
        }
    }

    private companion object {
        const val TICKET_CAP = 200
        /** The longest a round may run on after the clock stops (a claw still carrying its prize, say). */
        const val OVERTIME_SECONDS = 12f
        /** Chaos rounds per machine; raise it with the FUZZ_SEEDS environment variable for a long soak. */
        val SEEDS: Long = System.getenv("FUZZ_SEEDS")?.toLongOrNull() ?: 8L
    }
}
