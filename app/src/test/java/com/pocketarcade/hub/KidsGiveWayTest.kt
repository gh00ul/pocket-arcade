package com.pocketarcade.hub

import com.pocketarcade.games.GameRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * First person, the player walks up to somewhere and stops (to shop, to read a prompt) among the
 * crowd: the kids near them make way, and none of them is left standing frozen beside the player
 * for as long as the player stays.
 */
class KidsGiveWayTest {
    private val games = GameRegistry.createAll()

    /** The hall with 46 kids and the player, in first person, standing still at [spotIndex]'s stand point. */
    private fun parkedAt(spotIndex: Int): HubWorld {
        val w = HubWorld(games, null)
        val rng = Random(5)
        repeat(30) { i ->
            var tx: Int
            var ty: Int
            do {
                tx = rng.nextInt(1, w.map.cols - 1)
                ty = rng.nextInt(8, w.map.rows - 4)
            } while (!w.map.tileWalkable(tx, ty))
            w.npcs += Npc(Looks.randomKid(100 + i), tx * HubLayout.TILE + 8f, ty * HubLayout.TILE + 12f, Random(500 + i), i * 0.9f)
        }
        w.density = 2.75f
        w.setViewport(1080f, 2400f)
        w.setFirstPerson(true, animate = false)
        w.player.place(w.map.spawnX, w.map.spawnY)
        w.walkTo(w.map.spots[spotIndex])
        return w
    }

    /** The longest any kid stayed frozen beside the player (seconds), in the 5 minutes after they arrived. */
    private fun longestFreeze(w: HubWorld): Float {
        val dt = 1f / 30f
        val near = FloatArray(w.npcs.size)
        var worst = 0f
        var t = 0f
        var arrived = false
        while (t < 360f) {
            w.update(dt)
            t += dt
            if (!arrived) {
                arrived = !w.route.active && t > 1f
                continue
            }
            for ((i, n) in w.npcs.withIndex()) {
                val d = hypot(n.x - w.player.x, n.y - w.player.y)
                // Kids sitting or queueing are meant to stay put; the rest should be going somewhere.
                val frozen = d < 23f && n.state != Npc.State.SIT && n.state != Npc.State.QUEUE
                near[i] = if (frozen) near[i] + dt else 0f
                worst = maxOf(worst, near[i])
            }
        }
        return worst
    }

    @Test
    fun kidsDoNotFreezeAroundAPlayerAtThePrizeCounter() {
        val spot = 0
        val worst = longestFreeze(parkedAt(spot))
        assertTrue("a kid stood frozen next to the player at the prize counter for ${worst}s", worst < 30f)
    }

    @Test
    fun kidsPinnedAgainstACabinetDoNotFreezeBesideThePlayer() {
        val w0 = HubWorld(games, null)
        // The claw machines' spots: the bank's strip in front of them is narrow, so a kid pushed
        // against a cabinet has no room to step aside.
        for (spot in w0.map.spots.indices.filter { w0.map.spots[it].type == SpotType.MACHINE && w0.map.spots[it].machine == 0 }) {
            val worst = longestFreeze(parkedAt(spot))
            assertTrue("a kid stood frozen next to the player at spot $spot for ${worst}s", worst < 30f)
        }
    }
}
