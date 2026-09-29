package com.pocketarcade.hub

import com.pocketarcade.data.Catalog
import com.pocketarcade.engine.hash01
import com.pocketarcade.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prize wall shows what you've won: the shelves' pattern is the same as it was when the
 * plushies were baked into the wall, and which of them are in colour follows the collection.
 */
class PrizeWallTest {
    private val wall: Prop = HubLayout.build(GameRegistry.createAll(), emptySet()).props.single { it.kind == PropKind.PRIZE_WALL }

    /** The layout as the first version wrote it, in the one loop that built the whole wall. */
    private fun firstVersion(p: Prop): Pair<List<List<Float>>, List<List<Float>>> {
        val boxes = ArrayList<List<Float>>()
        val plushes = ArrayList<List<Float>>()
        val shelves = 5
        for (k in 0 until shelves) {
            val y = 14f + k * 17f
            var x = p.x0 + 4f
            var i = 0
            while (x < p.x1 - 6f) {
                if ((k + i) % 3 == 0) {
                    val w = 9f + hash01(i, k) * 5f
                    val hgt = 8f + hash01(i, k + 9) * 6f
                    boxes += listOf(x, y, w, hgt, (i + k * 3).toFloat())
                    x += w + 2f
                } else {
                    val index = (i * 7 + k * 3) % Catalog.plushies.size
                    val s = if (k == shelves - 1) 0.75f else 0.55f
                    plushes += listOf(index.toFloat(), x + 5f, y, (p.z0 + p.z1) / 2f + 1f, (hash01(i, k + 3) - 0.5f) * 0.6f, s)
                    x += 12f * s + 4f
                }
                i++
            }
        }
        return boxes to plushes
    }

    @Test
    fun theShelvesHoldTheSameToysInTheSamePlacesAsBefore() {
        val (boxes, plushes) = firstVersion(wall)
        val layout = PrizeWall.layout(wall)
        assertEquals(boxes.size, layout.boxes.size)
        assertEquals(plushes.size, layout.plushes.size)
        for ((i, t) in layout.boxes.withIndex()) {
            assertEquals(boxes[i], listOf(t.x, t.y, t.w, t.h, t.art.toFloat()))
        }
        for ((i, s) in layout.plushes.withIndex()) {
            assertEquals(plushes[i], listOf(Catalog.plushies.indexOf(s.plush).toFloat(), s.x, s.y, s.z, s.yaw, s.scale))
        }
    }

    @Test
    fun theWallIsFullOfPlushiesOfMostKinds() {
        val layout = PrizeWall.layout(wall)
        assertTrue("only ${layout.plushes.size} plushies on the wall", layout.plushes.size >= 30)
        assertTrue("only ${layout.boxes.size} boxed toys on the wall", layout.boxes.size >= 10)
        for (s in layout.plushes) {
            assertTrue("a plush at x ${s.x} is off the shelf", s.x > wall.x0 && s.x < wall.x1)
            assertTrue("a plush at height ${s.y} isn't on a shelf", (0 until PrizeWall.SHELVES).any { PrizeWall.shelfY(it) == s.y })
        }
        val kinds = layout.plushes.map { it.plush.id }.toSet()
        assertTrue("the wall shows only ${kinds.size} of ${Catalog.plushies.size} plushies", kinds.size >= Catalog.plushies.size - 2)
    }

    @Test
    fun aPlushIsOwnedOnceItsInTheCollection() {
        val bear = Catalog.plushies.first { it.id == "plush_bear" }
        val cat = Catalog.plushies.first { it.id == "plush_cat" }
        assertFalse(PrizeWall.isOwned(bear, emptyMap()))
        assertTrue(PrizeWall.isOwned(bear, mapOf("plush_bear" to 1)))
        assertTrue(PrizeWall.isOwned(bear, mapOf("plush_bear" to 7, "plush_cat" to 0)))
        // A zero count (or a stray key) doesn't count.
        assertFalse(PrizeWall.isOwned(cat, mapOf("plush_bear" to 7, "plush_cat" to 0)))
        assertFalse(PrizeWall.isOwned(cat, mapOf("cat" to 3)))
    }

    @Test
    fun whichSlotsAreInColourFollowsTheCollection() {
        val slots = PrizeWall.layout(wall).plushes
        val won = OwnedSlots(slots)
        assertTrue(won.update(emptyMap()))
        assertTrue("nothing is won yet", won.owned.none { it })

        val some = mapOf("plush_bear" to 2, "plush_golden" to 1, "plush_frog" to 0)
        assertTrue(won.update(some))
        for ((i, s) in slots.withIndex()) {
            val expected = s.plush.id == "plush_bear" || s.plush.id == "plush_golden"
            assertEquals("${s.plush.id} at slot $i", expected, won.owned[i])
        }
        assertTrue("some, but not all, are won", won.owned.any { it } && won.owned.any { !it })

        val all = Catalog.plushies.associate { it.id to 1 }
        assertTrue(won.update(all))
        assertTrue("everything is won", won.owned.all { it })
    }

    @Test
    fun theOwnedListIsOnlyReadAgainForANewCollection() {
        val won = OwnedSlots(PrizeWall.layout(wall).plushes)
        val collection = mapOf("plush_bear" to 1)
        assertTrue(won.update(collection))
        // The same map, frame after frame: nothing to do (and the answer array is the same one).
        val answer = won.owned
        repeat(100) { assertFalse(won.update(collection)) }
        assertSame(answer, won.owned)
        // A save hands over a new map each time it changes.
        assertTrue(won.update(mapOf("plush_bear" to 1, "plush_cat" to 1)))
    }
}
