package com.pocketarcade.hub

import androidx.compose.ui.graphics.drawscope.DrawScope
import com.pocketarcade.data.DecorStyle
import com.pocketarcade.engine.Painter
import com.pocketarcade.engine.TouchType
import com.pocketarcade.games.CabinetLook
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GameFx
import com.pocketarcade.games.GameRegistry
import com.pocketarcade.games.MiniGame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.hypot

/**
 * Checks the real hall floor plan, built from every registered machine, with and without every
 * decoration bought: nothing overlaps, every aisle is at least as wide as the narrowest one in
 * the first floor plan, and the kids' walk grid reaches every prompt, counter and hangout.
 */
class HubMapTest {
    private companion object {
        /**
         * The narrowest aisle between fixtures in the first floor plan: the diagonal squeeze
         * between a foyer pillar and a kiddie ride (13 across, 17 along, 21.4 corner to corner).
         */
        const val MIN_WALKWAY = 21f
        /**
         * Gaps up to this are closed, not aisles: cabinets side by side in a bank, a machine
         * backed up to a wall. Nobody walks through them (a kid needs 12 of clearance).
         */
        const val CLOSED_GAP = 12f
    }

    private val games: List<MiniGame> = GameRegistry.createAll()
    private val maps: List<Pair<String, HubMap>> = listOf(
        "no decor" to HubLayout.build(games, emptySet()),
        "all decor" to HubLayout.build(games, DecorStyle.entries.toSet()),
    )

    private fun isWall(b: Box) = b.left <= 0f || b.top <= 0f || b.right >= HubLayout.WIDTH || b.bottom >= HubLayout.DEPTH

    private fun Box.str() = "(${left}, ${top})-(${right}, ${bottom})"

    private fun overlaps(a: Box, b: Box) = a.intersects(b.left, b.top, b.right, b.bottom)

    /** Clear distance between two boxes: straight across when they face each other, else corner to corner. */
    private fun gap(a: Box, b: Box): Float {
        val dx = maxOf(b.left - a.right, a.left - b.right, 0f)
        val dz = maxOf(b.top - a.bottom, a.top - b.bottom, 0f)
        return if (dx > 0f && dz > 0f) hypot(dx, dz) else maxOf(dx, dz)
    }

    /** The empty rectangle between two separated boxes. */
    private fun between(a: Box, b: Box): Box {
        val sepX = a.right <= b.left || b.right <= a.left
        val sepZ = a.bottom <= b.top || b.bottom <= a.top
        val x0 = if (sepX) minOf(a.right, b.right) else maxOf(a.left, b.left)
        val x1 = if (sepX) maxOf(a.left, b.left) else minOf(a.right, b.right)
        val z0 = if (sepZ) minOf(a.bottom, b.bottom) else maxOf(a.top, b.top)
        val z1 = if (sepZ) maxOf(a.top, b.top) else minOf(a.bottom, b.bottom)
        return Box(minOf(x0, x1), minOf(z0, z1), maxOf(x0, x1), maxOf(z0, z1))
    }

    /** Breadth-first flood of the kids' walk grid from the spawn tile. */
    private fun reachable(map: HubMap): BooleanArray {
        val seen = BooleanArray(map.cols * map.rows)
        val sx = (map.spawnX / HubLayout.TILE).toInt()
        val sy = ((map.spawnY - 4f) / HubLayout.TILE).toInt()
        assertTrue("the spawn tile isn't walkable", map.tileWalkable(sx, sy))
        val queue = IntArray(seen.size)
        var head = 0
        var tail = 0
        queue[tail++] = sy * map.cols + sx
        seen[sy * map.cols + sx] = true
        while (head < tail) {
            val cur = queue[head++]
            val cx = cur % map.cols
            val cy = cur / map.cols
            for (d in 0 until 4) {
                val nx = cx + if (d == 0) 1 else if (d == 1) -1 else 0
                val ny = cy + if (d == 2) 1 else if (d == 3) -1 else 0
                if (!map.tileWalkable(nx, ny)) continue
                val ni = ny * map.cols + nx
                if (seen[ni]) continue
                seen[ni] = true
                queue[tail++] = ni
            }
        }
        return seen
    }

    @Test
    fun nothingOverlaps() {
        for ((name, map) in maps) {
            val s = map.solids
            for (i in s.indices) for (j in i + 1 until s.size) {
                if (isWall(s[i]) && isWall(s[j])) continue
                assertFalse("$name: ${s[i].str()} overlaps ${s[j].str()}", overlaps(s[i], s[j]))
            }
        }
    }

    @Test
    fun everythingStaysInsideTheWalls() {
        for ((name, map) in maps) {
            for (p in map.props) {
                if (p.kind == PropKind.DOORS) continue
                assertTrue(
                    "$name: ${p.kind} at (${p.x0}, ${p.z0})-(${p.x1}, ${p.z1}) pokes through a wall",
                    p.x0 >= HubLayout.WALL && p.x1 <= HubLayout.WIDTH - HubLayout.WALL &&
                        p.z0 >= HubLayout.BACK_WALL && p.z1 <= HubLayout.FRONT_WALL,
                )
            }
        }
    }

    @Test
    fun aislesAreAtLeastAsWideAsBefore() {
        for ((name, map) in maps) {
            val s = map.solids
            for (i in s.indices) for (j in i + 1 until s.size) {
                val a = s[i]
                val b = s[j]
                if (isWall(a) && isWall(b)) continue
                val g = gap(a, b)
                if (g <= CLOSED_GAP || g >= MIN_WALKWAY) continue
                val r = between(a, b)
                val blocked = s.indices.any { k -> k != i && k != j && overlaps(s[k], r) }
                if (!blocked) fail("$name: a ${"%.1f".format(g)}-wide squeeze between ${a.str()} and ${b.str()} (aisles need $MIN_WALKWAY)")
            }
        }
    }

    @Test
    fun theSpawnPointIsClear() {
        for ((name, map) in maps) {
            assertFalse("$name: spawn is inside something", Collision.blocked(map.solids, map.spawnX, map.spawnY))
        }
    }

    @Test
    fun everyPromptIsReachableFromTheDoor() {
        for ((name, map) in maps) {
            val seen = reachable(map)
            for (spot in map.spots) {
                var ok = false
                for (ty in 0 until map.rows) for (tx in 0 until map.cols) {
                    if (!seen[ty * map.cols + tx]) continue
                    val cx = tx * HubLayout.TILE + HubLayout.TILE / 2f
                    val cy = ty * HubLayout.TILE + HubLayout.TILE / 2f
                    if (spot.area.contains(cx, cy)) ok = true
                }
                val what = if (spot.type == SpotType.MACHINE) games[spot.machine].id else spot.type.name
                assertTrue("$name: can't walk to the $what spot at ${spot.area.str()}", ok)
            }
        }
    }

    @Test
    fun everyHangoutIsReachable() {
        for ((name, map) in maps) {
            val seen = reachable(map)
            for (h in map.hangouts) {
                val tx = h.tileX
                val ty = h.tileY
                assertTrue("$name: kids can't reach the hangout at (${h.x}, ${h.z})", map.tileWalkable(tx, ty) && seen[ty * map.cols + tx])
                val far = hypot(tx * HubLayout.TILE + HubLayout.TILE / 2f - h.x, ty * HubLayout.TILE + HubLayout.TILE / 2f - h.z)
                assertTrue("$name: the hangout at (${h.x}, ${h.z}) is ${far} from its tile", far < 2f * HubLayout.TILE)
            }
        }
    }

    @Test
    fun everyMachineHasACabinetAndAPrompt() {
        for ((name, map) in maps) {
            for (i in games.indices) {
                assertTrue("$name: ${games[i].id} has no cabinet", map.props.any { it.kind == PropKind.MACHINE && it.machine == i })
                assertTrue("$name: ${games[i].id} has no prompt", map.spots.any { it.type == SpotType.MACHINE && it.machine == i })
            }
        }
    }

    @Test
    fun everyCabinetFitsItsBank() {
        for ((name, map) in maps) {
            for (i in games.indices) {
                val g = games[i]
                val slot = map.machineSlots[i]
                val d = g.cabinet
                if (d != null) {
                    assertTrue(
                        "$name: ${g.id}'s design ${d.width} x ${d.depth} x ${d.height} doesn't fit its bank's ${slot.maxW} x ${slot.maxD} x ${slot.maxH}",
                        d.width <= slot.maxW && d.depth <= slot.maxD && d.height <= slot.maxH,
                    )
                }
                for (p in map.props) {
                    if (p.kind != PropKind.MACHINE || p.machine != i) continue
                    assertTrue("$name: a ${g.id} cabinet sticks out of its bank", p.x0 >= slot.x0 && p.x1 <= slot.x1 && p.z0 >= slot.back && p.z1 <= slot.back + slot.maxD)
                }
            }
        }
    }

    /** A do-nothing machine for probing the floor plan's limits. */
    private class Probe(shape: CabinetShape, private val design: CabinetDesign? = null) : MiniGame {
        override val id = "probe"
        override val title = "PROBE"
        override val marquee = "PROBE"
        override val instructions = emptyList<String>()
        override val look = CabinetLook(0, 0, 0, shape)
        override val cabinet: CabinetDesign? get() = design
        override val roundSeconds = 1f
        override fun drawAttract(p: Painter, w: Int, h: Int, time: Float) {}
        override fun start(fx: GameFx) {}
        override fun update(dt: Float, timeLeft: Float) {}
        override fun draw(scope: DrawScope) {}
        override fun onTouch(type: TouchType, id: Long, x: Float, y: Float, timeMs: Long) {}
        override fun cancelInput() {}
        override val score = 0
        override val finished = true
        override fun ticketsFor(score: Int) = 0
        override val bonusTickets = 0
    }

    private fun buildFails(games: List<MiniGame>): Boolean = try {
        HubLayout.build(games, emptySet())
        false
    } catch (_: IllegalStateException) {
        true
    }

    @Test
    fun machinesTakeSpareBanksThenFailLoudly() {
        val spares = HubLayout.slots.count { it.shape == null }
        assertTrue("keep at least two spare banks for new machines", spares >= 2)
        // Machines without a bank of their own take the spares, one each...
        assertFalse(buildFails(games + List(spares) { Probe(CabinetShape.UPRIGHT) }))
        // ...and one more has nowhere to go: the build fails rather than overlap anything.
        assertTrue(buildFails(games + List(spares + 1) { Probe(CabinetShape.UPRIGHT) }))
    }

    @Test
    fun aCabinetTooBigForItsBankFailsTheBuild() {
        val huge = object : CabinetDesign {
            override val width = 500f
            override val depth = 40f
            override val height = 60f
            override val focusHeight = 40f
            override val focusSetBack = 6f
            override val screenUnits: Pair<Int, Int>? = null
            override fun build(c: CabinetBuild) {}
        }
        assertTrue(buildFails(listOf(Probe(CabinetShape.UPRIGHT, huge))))
    }

    @Test
    fun slotsAreKeptClear() {
        val slots = HubLayout.slots
        for (i in slots.indices) for (j in i + 1 until slots.size) {
            assertFalse("slots $i and $j overlap", overlaps(slots[i].area, slots[j].area))
        }
        // Nothing but a slot's own cabinets may stand in the floor it reserves.
        for ((name, map) in maps) {
            for (slot in slots) {
                for (p in map.props) {
                    val foot = p.foot ?: continue
                    if (p.kind == PropKind.MACHINE && slot.area.contains(p.centerX, p.centerZ)) continue
                    assertFalse("$name: ${p.kind} at ${foot.str()} stands in a bank's floor ${slot.area.str()}", overlaps(foot, slot.area))
                }
            }
        }
    }
}
