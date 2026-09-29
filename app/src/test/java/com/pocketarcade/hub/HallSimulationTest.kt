package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Long seeded runs of the hall's crowd, with decorations bought, checking what must always
 * hold: nobody is NaN or inside a cabinet, every kid keeps moving through the states (nobody
 * waits forever), the café's queue and hangouts are never double-booked, the first-person
 * player is never wedged, tap-to-walk reaches every play spot, and a decoration landing on the
 * player moves them clear.
 */
class HallSimulationTest {
    private val games = GameRegistry.createAll()
    private val br = System.lineSeparator()

    /** No decor, all of it, and a few mixes. */
    private fun decorSets(mixes: Int): List<Set<DecorStyle>> {
        val all = DecorStyle.entries
        val sets = ArrayList<Set<DecorStyle>>()
        sets.add(emptySet())
        sets.add(all.toSet())
        val rng = Random(9)
        repeat(mixes) { sets.add(all.filter { rng.nextBoolean() }.toSet()) }
        return sets
    }

    /** A hall with [extra] more kids than usual, so the seats and the queue are fought over. */
    private fun crowdedWorld(decor: Set<DecorStyle>, extra: Int): HubWorld {
        val w = HubWorld(games, null)
        w.setDecor(decor)
        val rng = Random(5)
        repeat(extra) { i ->
            var tx: Int
            var ty: Int
            do {
                tx = rng.nextInt(1, w.map.cols - 1)
                ty = rng.nextInt(8, w.map.rows - 4)
            } while (!w.map.tileWalkable(tx, ty))
            w.npcs += Npc(Looks.randomKid(100 + i), tx * HubLayout.TILE + 8f, ty * HubLayout.TILE + 12f, Random(500 + i), i * 0.9f)
        }
        return w
    }

    private class Watch(val npc: Npc) {
        var state = npc.state
        var since = 0f
        var lastBusy = 0f
    }

    @Test
    fun crowdInvariantsHoldForEveryDecorSet() {
        val dt = 1f / 30f
        for ((n, decor) in decorSets(mixes = 3).withIndex()) {
            val w = crowdedWorld(decor, 30)
            val watches = w.npcs.map { Watch(it) }
            var time = 0f
            repeat((10 * 60 / dt).toInt()) {
                w.update(dt)
                time += dt
                val tag = "decor set #$n $decor at ${"%.1f".format(time)}s"
                for ((i, k) in watches.withIndex()) {
                    val npc = k.npc
                    assertTrue("$tag: kid $i is NaN (${npc.x}, ${npc.y})", npc.x.isFinite() && npc.y.isFinite() && npc.yaw.isFinite() && npc.phase.isFinite())
                    if (npc.state != k.state) {
                        k.state = npc.state
                        k.since = 0f
                    } else {
                        k.since += dt
                    }
                    if (npc.state != Npc.State.IDLE) k.lastBusy = 0f else k.lastBusy += dt
                    assertTrue("$tag: kid $i idle for ${k.lastBusy}s", k.lastBusy < 60f)
                    val cap = when (npc.state) {
                        Npc.State.IDLE -> 60f
                        Npc.State.WALK -> 150f
                        Npc.State.PLAY, Npc.State.SIT -> 20f
                        Npc.State.QUEUE -> 50f
                    }
                    assertTrue("$tag: kid $i has been ${npc.state} for ${k.since}s", k.since < cap)
                    if (npc.state == Npc.State.PLAY || npc.state == Npc.State.QUEUE) {
                        assertFalse("$tag: kid $i is ${npc.state} inside a solid at (${npc.x}, ${npc.y})", Collision.blocked(w.map.solids, npc.x, npc.y))
                    }
                }
                // Hangouts are used by one kid at a time.
                val users = HashMap<Int, Int>()
                for ((i, k) in watches.withIndex()) {
                    val npc = k.npc
                    if (npc.hangout < 0) continue
                    val other = users.put(npc.hangout, i)
                    assertTrue("$tag: kids $other and $i both hold hangout ${npc.hangout}", other == null)
                }
                // The queue.
                val q = w.cafe.queue
                for (k in q.indices) {
                    val kid = q[k] ?: continue
                    assertEquals("$tag: kid in queue spot $k thinks they're in ${kid.queueSpot}", k, kid.queueSpot)
                }
                for ((i, k) in watches.withIndex()) {
                    val s = k.npc.queueSpot
                    if (s >= 0) assertTrue("$tag: kid $i holds queue spot $s but the queue says otherwise", q[s] === k.npc)
                    if (k.npc.state == Npc.State.QUEUE) assertTrue("$tag: kid $i is queueing with no spot", s >= 0)
                }
                val c = w.cafe.barista.customer
                if (c != null) assertEquals("$tag: the barista is serving someone who isn't at the till", 0, c.queueSpot)
            }
            assertTrue("decor set #$n: nobody was served in 10 minutes", w.cafe.served > 0)
        }
    }

    /**
     * A player wandering first person through the crowd (the stick in random directions, now and
     * then a tap-to-walk to a machine or the counter): kids make way, and nobody ends up double-booked,
     * inside a cabinet or stuck, and the player is never wedged into anything.
     */
    @Test
    fun crowdAndPlayerInvariantsHoldInFirstPerson() {
        val dt = 1f / 120f
        val sets = decorSets(mixes = 2)
        for ((n, decor) in sets.withIndex()) {
            val w = crowdedWorld(decor, 30)
            w.density = 2.75f
            w.setViewport(1080f, 2400f)
            w.setFirstPerson(true, animate = false)
            val watches = w.npcs.map { Watch(it) }
            val rng = Random(70 + n)
            var time = 0f
            var holdFor = 0f
            var travelled = 0f
            var lx = w.player.x
            var ly = w.player.y
            repeat((4 * 60 / dt).toInt()) {
                if (holdFor <= 0f) {
                    holdFor = rng.nextFloat() * 3f + 0.5f
                    w.pointerUp(1, 0f, 0f)
                    when (rng.nextInt(4)) {
                        0 -> w.walkTo(w.map.spots[rng.nextInt(w.map.spots.size)])
                        else -> {
                            w.pointerDown(1, 200f, 1800f)
                            val a = rng.nextFloat() * 6.283f
                            w.pointerMove(1, 200f + kotlin.math.cos(a) * 150f, 1800f + kotlin.math.sin(a) * 150f)
                        }
                    }
                }
                holdFor -= dt
                w.update(dt)
                time += dt
                val tag = "FP decor set #$n $decor at ${"%.1f".format(time)}s"
                val p = w.player
                assertTrue("$tag: player is NaN", p.x.isFinite() && p.y.isFinite() && p.vx.isFinite() && p.vy.isFinite())
                assertTrue("$tag: player is inside a solid at (${p.x}, ${p.y}): ${Body.penetration(w.bodySolids, p.x, p.y)}", Body.clear(w.bodySolids, p.x, p.y, Body.RADIUS - 0.6f))
                travelled += kotlin.math.hypot(p.x - lx, p.y - ly)
                lx = p.x
                ly = p.y
                for ((i, k) in watches.withIndex()) {
                    val npc = k.npc
                    assertTrue("$tag: kid $i is NaN (${npc.x}, ${npc.y})", npc.x.isFinite() && npc.y.isFinite())
                    if (npc.state != k.state) {
                        k.state = npc.state
                        k.since = 0f
                    } else {
                        k.since += dt
                    }
                    if (npc.state != Npc.State.IDLE) k.lastBusy = 0f else k.lastBusy += dt
                    assertTrue("$tag: kid $i idle for ${k.lastBusy}s", k.lastBusy < 60f)
                }
                val q = w.cafe.queue
                for (k in q.indices) {
                    val kid = q[k] ?: continue
                    assertEquals("$tag: kid in queue spot $k thinks they're in ${kid.queueSpot}", k, kid.queueSpot)
                }
                for ((i, k) in watches.withIndex()) {
                    val s = k.npc.queueSpot
                    if (s >= 0) assertTrue("$tag: kid $i holds queue spot $s but the queue says otherwise", q[s] === k.npc)
                }
            }
            assertTrue("decor set #$n: the player barely moved ($travelled)", travelled > 300f)
        }
    }

    /**
     * Tap-to-walk to every play spot, from many places, with and without decorations: the route
     * is planned, the body follows it without ever touching a solid, and it ends inside the spot.
     */
    @Test
    fun tapToWalkReachesEverySpotFromAnywhere() {
        val dt = 1f / 120f
        for ((n, decor) in listOf(emptySet<DecorStyle>(), DecorStyle.entries.toSet()).withIndex()) {
            val w = HubWorld(games, null)
            w.setDecor(decor)
            w.npcs.clear()
            w.density = 2.75f
            w.setViewport(1080f, 2400f)
            w.setFirstPerson(true, animate = false)
            val rng = Random(31 + n)
            var planned = 0
            val failed = ArrayList<String>()
            for ((si, spot) in w.map.spots.withIndex()) {
                repeat(12) { attempt ->
                    // A start somewhere in the hall the body fits and can walk from the doors to.
                    var sx: Float
                    var sy: Float
                    var tries = 0
                    do {
                        sx = rng.nextFloat() * (HubLayout.WIDTH - 60f) + 30f
                        sy = rng.nextFloat() * (HubLayout.FRONT_WALL - 100f) + 60f
                        tries++
                    } while (!Body.clear(w.bodySolids, sx, sy, Body.RADIUS + 1f) && tries < 200)
                    w.player.place(sx, sy)
                    w.player.halt()
                    w.update(dt)
                    val ok = w.walkTo(spot)
                    if (!ok) {
                        failed.add("decor $n: no route from ($sx, $sy) to spot $si (${spot.type})")
                        return@repeat
                    }
                    planned++
                    var t = 0f
                    while (w.route.active && t < 90f) {
                        w.update(dt)
                        t += dt
                        assertTrue("decor $n: the walk to spot $si went into a solid at (${w.player.x}, ${w.player.y})", Body.clear(w.bodySolids, w.player.x, w.player.y, Body.RADIUS - 0.6f))
                    }
                    if (w.route.active) failed.add("decor $n: still walking to spot $si after 90 s from ($sx, $sy)")
                    else if (!spot.area.contains(w.player.x, w.player.y)) failed.add("decor $n: the walk from ($sx, $sy) to spot $si ended at (${w.player.x}, ${w.player.y}), outside ${spot.area.left}..${spot.area.right} x ${spot.area.top}..${spot.area.bottom}")
                }
            }
            assertTrue("routes planned: $planned; problems:" + failed.take(15).joinToString(separator = br, prefix = br) + br + "(${failed.size} in all)", failed.isEmpty())
        }
    }

    /** Buying a decoration while standing right where it lands: the overhead player is moved clear of it. */
    @Test
    fun aDecorationLandingOnThePlayerMovesThemClear() {
        for (style in DecorStyle.entries) {
            val w = HubWorld(games, null)
            val landing = HubLayout.build(games, setOf(style)).props.first { it.decor == style }
            w.player.place(landing.centerX, landing.centerZ)
            w.setDecor(setOf(style))
            repeat(240) { w.update(1f / 120f) }
            assertFalse("$style landed on the player, who is still in a solid at (${w.player.x}, ${w.player.y})", Collision.blocked(w.map.solids, w.player.x, w.player.y))
        }
    }
}
