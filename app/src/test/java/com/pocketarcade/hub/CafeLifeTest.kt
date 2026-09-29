package com.pocketarcade.hub

import com.pocketarcade.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the hall's crowd for a while and checks the café works: kids queue in order, the barista
 * serves them, and they take their treats to café seats.
 */
class CafeLifeTest {
    @Test
    fun kidsQueueGetServedAndSitWithTheirTreats() {
        val world = HubWorld(GameRegistry.createAll(), null)
        var sippers = 0
        var maxQueue = 0
        val dt = 1f / 30f
        repeat((12 * 60 / dt).toInt()) {
            world.update(dt)
            val q = world.cafe.queue
            var inQueue = 0
            for (k in q.indices) {
                val n = q[k] ?: continue
                inQueue++
                assertEquals("a kid in queue spot $k thinks they're in spot ${n.queueSpot}", k, n.queueSpot)
                for (j in k + 1 until q.size) assertTrue("a kid holds two queue spots", q[j] !== n)
            }
            maxQueue = maxOf(maxQueue, inQueue)
            for (n in world.npcs) {
                if (n.state == Npc.State.SIT && n.holding) {
                    assertTrue("a kid sat down with a treat away from the café", world.map.hangouts[n.hangout].cafe)
                    sippers++
                }
            }
            val b = world.cafe.barista
            assertTrue("the barista left the lane", b.x in CafeLayout.LANE_X0..CafeLayout.LANE_X1)
        }
        assertTrue("nobody was served in 12 minutes (${world.cafe.served})", world.cafe.served >= 5)
        assertTrue("nobody ever queued behind anyone", maxQueue >= 2)
        assertTrue("nobody sat down with a treat", sippers > 0)
    }
}
