package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.r3d.Camera3D
import com.pocketarcade.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * The interactive-prop seam: every prop that does something (the photo booth, kiddie rides,
 * vending machines, the café counter and the bought trophy case, fish tank and jukebox) gets a
 * spot to stand at, generated the same way for each, without disturbing the hand-placed spots of
 * the machines, the token kiosk and the prize counter.
 */
class InteractivePropsTest {
    private companion object {
        const val W = 1080f
        const val H = 2400f
        const val DEG = PI.toFloat() / 180f
    }

    private val games = GameRegistry.createAll()
    private val everyDecor = DecorStyle.entries.toSet()
    private val maps = listOf(
        "no decor" to HubLayout.build(games, emptySet()),
        "all decor" to HubLayout.build(games, everyDecor),
    )

    private fun Box.str() = "(${left}, ${top})-(${right}, ${bottom})"

    private fun overlaps(a: Box, b: Box) = a.intersects(b.left, b.top, b.right, b.bottom)

    private fun interactive(map: HubMap) = map.props.filter { HubLayout.spotTypeOf(it) != null }

    /** Breadth-first flood of the kids' walk grid from the spawn tile (as in HubMapTest). */
    private fun reachable(map: HubMap): BooleanArray {
        val seen = BooleanArray(map.cols * map.rows)
        val sx = (map.spawnX / HubLayout.TILE).toInt()
        val sy = ((map.spawnY - 4f) / HubLayout.TILE).toInt()
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
    fun theRightPropsAreInteractive() {
        for ((name, map) in maps) {
            fun count(type: SpotType) = interactive(map).count { HubLayout.spotTypeOf(it) == type }
            assertEquals("$name: photo booths", 1, count(SpotType.PHOTO))
            assertEquals("$name: kiddie rides", 2, count(SpotType.RIDE))
            assertEquals("$name: vending machines", 2, count(SpotType.VENDING))
            assertEquals("$name: café counters", 1, count(SpotType.CAFE))
            // The decorations only exist once bought.
            val bought = if (name == "all decor") 1 else 0
            assertEquals("$name: trophy cases", bought, count(SpotType.TROPHY))
            assertEquals("$name: fish tanks", bought, count(SpotType.TANK))
            assertEquals("$name: jukeboxes", bought, count(SpotType.JUKEBOX))
        }
        // Everything else only decorates.
        for (kind in PropKind.entries) {
            if (kind in setOf(PropKind.PHOTO_BOOTH, PropKind.KIDDIE_RIDE, PropKind.VENDING, PropKind.CAFE_COUNTER, PropKind.DECOR)) continue
            assertNull("$kind", HubLayout.spotTypeOf(Prop(kind, 0f, 0f, 10f, 10f, 10f)))
        }
        for (style in DecorStyle.entries) {
            val expected = when (style) {
                DecorStyle.TROPHY_CASE -> SpotType.TROPHY
                DecorStyle.FISH_TANK -> SpotType.TANK
                DecorStyle.JUKEBOX -> SpotType.JUKEBOX
                else -> null
            }
            assertEquals("$style", expected, HubLayout.spotTypeOf(Prop(PropKind.DECOR, 0f, 0f, 10f, 10f, 10f, decor = style)))
        }
    }

    @Test
    fun everyInteractivePropHasExactlyOneSpotInFrontOfIt() {
        for ((name, map) in maps) {
            for (p in interactive(map)) {
                val mine = map.spots.filter { it.prop === p }
                assertEquals("$name: ${p.kind} at (${p.x0}, ${p.z0}) spots", 1, mine.size)
                val s = mine.single()
                assertEquals("$name: ${p.kind} spot type", HubLayout.spotTypeOf(p), s.type)
                // Starts right at the front (so the first-person body keeps its gap off it)...
                assertEquals("$name: ${s.type} spot doesn't start at the prop's front", p.frontZ, s.area.top, 0f)
                // ...between the minimum depth and the usual one, inside the prop's width or the till's.
                val depth = s.area.height
                assertTrue("$name: ${s.type} spot is $depth deep", depth in HubLayout.MIN_STAND_DEPTH..HubLayout.PROMPT_DEPTH)
                assertTrue("$name: ${s.type} spot is ${s.area.width} wide", s.area.width in 2f * HubLayout.MIN_STAND_HALF..2f * HubLayout.MAX_STAND_HALF)
                // The prompt floats over the prop's top and the view turns to the prop.
                assertEquals(p.height + 6f, s.anchorHeight, 0f)
                assertTrue("$name: ${s.type} prompt isn't over the prop", s.anchorX in p.x0..p.x1)
                assertTrue("$name: ${s.type} focus is off the prop", s.focusX in p.x0..p.x1 && s.focusZ <= p.frontZ)
            }
        }
    }

    @Test
    fun everySpotTypeIsUsedOnTheFullFloor() {
        val used = maps.last().second.spots.map { it.type }.toSet()
        for (t in SpotType.entries) assertTrue("no $t spot on the full floor", t in used)
    }

    @Test
    fun spotsNeverOverlapSolidsOrEachOther() {
        for ((name, map) in maps) {
            for (s in map.spots) {
                if (s.type == SpotType.MACHINE) continue
                for (b in map.solids) {
                    assertFalse("$name: the ${s.type} spot ${s.area.str()} overlaps the solid ${b.str()}", overlaps(s.area, b))
                }
            }
            for (i in map.spots.indices) for (j in i + 1 until map.spots.size) {
                val a = map.spots[i]
                val b = map.spots[j]
                assertFalse("$name: the ${a.type} spot ${a.area.str()} overlaps the ${b.type} spot ${b.area.str()}", overlaps(a.area, b.area))
            }
        }
    }

    @Test
    fun spotsKeepInsideTheHallAndOutOfTheMainAisle() {
        for ((name, map) in maps) {
            val counter = map.props.single { it.kind == PropKind.COUNTER }
            val aisle = Box(HubLayout.AISLE_X0, counter.frontZ, HubLayout.AISLE_X1, HubLayout.FRONT_WALL)
            for (s in map.spots) {
                if (s.type == SpotType.MACHINE || s.type == SpotType.PRIZES || s.type == SpotType.TOKENS) continue
                assertTrue(
                    "$name: the ${s.type} spot ${s.area.str()} is outside the walls",
                    s.area.left >= HubLayout.WALL && s.area.right <= HubLayout.WIDTH - HubLayout.WALL &&
                        s.area.top >= HubLayout.BACK_WALL && s.area.bottom <= HubLayout.FRONT_WALL,
                )
                assertFalse("$name: the ${s.type} spot ${s.area.str()} is in the main aisle", overlaps(s.area, aisle))
            }
        }
    }

    @Test
    fun everySpotIsReachableFromTheDoorAndTheBodyFitsWhereItStands() {
        for ((name, map) in maps) {
            val seen = reachable(map)
            val body = Body.solidsFor(map)
            for (s in interactive(map).map { p -> map.spots.single { it.prop === p } }) {
                var ok = false
                for (ty in 0 until map.rows) for (tx in 0 until map.cols) {
                    if (seen[ty * map.cols + tx] && s.area.contains(tx * HubLayout.TILE + HubLayout.TILE / 2f, ty * HubLayout.TILE + HubLayout.TILE / 2f)) ok = true
                }
                assertTrue("$name: can't walk to the ${s.type} spot ${s.area.str()}", ok)
                // Where tap-to-walk stops: inside the spot, and the first-person body fits there.
                val x = s.area.centerX
                val z = (s.area.top + HubWorld.STAND_DEPTH).coerceAtMost(s.area.bottom - 2f)
                assertTrue("$name: the ${s.type} stand point ($x, $z) isn't in its spot", s.area.contains(x, z))
                assertTrue("$name: the body doesn't fit at the ${s.type} stand point ($x, $z)", Body.clear(body, x, z))
            }
        }
    }

    @Test
    fun theHandPlacedSpotsAreUnchanged() {
        for ((name, map) in maps) {
            val tokens = map.spots.single { it.type == SpotType.TOKENS }
            assertEquals("$name: token kiosk", Box(366f, 1014f, 412f, 1042f).str(), tokens.area.str())
            assertEquals(389f, tokens.anchorX, 0f)
            val prizes = map.spots.single { it.type == SpotType.PRIZES }
            assertEquals("$name: prize counter", Box(262f, 94f, 346f, 124f).str(), prizes.area.str())
            assertEquals(304f, prizes.anchorX, 0f)
            // One spot per cabinet, in front of it: the cabinet's width less a hand each side, or 24.
            for (p in map.props) {
                if (p.kind != PropKind.MACHINE) continue
                val s = map.spots.single { it.type == SpotType.MACHINE && it.machine == p.machine && it.area.top == p.z1 && it.area.centerX == p.centerX }
                val half = maxOf((p.x1 - p.x0) / 2f - 1f, 12f)
                assertEquals("$name: machine ${p.machine} spot", Box(p.centerX - half, p.z1, p.centerX + half, p.z1 + HubLayout.PROMPT_DEPTH).str(), s.area.str())
                assertNull(s.prop)
            }
            assertEquals("$name: spots", map.machineSlots.sumOf { it.count } + 2 + interactive(map).size, map.spots.size)
        }
    }

    @Test
    fun aLongCounterIsServedAtItsTillAndSomethingInFrontCutsTheSpotShort() {
        val till = HubLayout.standArea(Prop(PropKind.CAFE_COUNTER, 20f, 830f, 152f, 868f, 28f), emptyList())
        assertEquals(CafeLayout.TILL_X, till.centerX, 0f)
        assertEquals(2f * HubLayout.MAX_STAND_HALF, till.width, 0f)
        // A narrow prop gets the least width; a wide one the most.
        assertEquals(2f * HubLayout.MIN_STAND_HALF, HubLayout.standArea(Prop(PropKind.DECOR, 100f, 100f, 110f, 110f, 30f, decor = DecorStyle.JUKEBOX), emptyList()).width, 0f)
        val prop = Prop(PropKind.PHOTO_BOOTH, 100f, 100f, 150f, 150f, 78f)
        assertEquals(HubLayout.PROMPT_DEPTH, HubLayout.standArea(prop, emptyList()).height, 0f)
        // A solid standing in the strip cuts it short where it starts...
        val cut = HubLayout.standArea(prop, listOf(Box(110f, 175f, 130f, 190f), Box(0f, 300f, 400f, 310f)))
        assertEquals(150f, cut.top, 0f)
        assertEquals(175f, cut.bottom, 0f)
        // ...one to the side of it doesn't...
        assertEquals(HubLayout.PROMPT_DEPTH, HubLayout.standArea(prop, listOf(Box(0f, 160f, 90f, 200f), Box(160f, 160f, 300f, 200f))).height, 0f)
        // ...and if that leaves too little to stand in, the floor plan fails loudly.
        try {
            HubLayout.standArea(prop, listOf(Box(110f, 160f, 130f, 190f)))
            fail("a spot with no room to stand should fail the build")
        } catch (_: IllegalStateException) {
        }
    }

    // ------------------------------------------------------------------ first person

    private fun world(): HubWorld {
        val w = HubWorld(games, null)
        w.density = 2.75f
        w.setViewport(W, H)
        w.setDecor(everyDecor)
        w.setFirstPerson(true, animate = false)
        w.npcs.clear()
        return w
    }

    @Test
    fun spotOfFindsEveryInteractivePropsSpot() {
        val w = world()
        for (p in interactive(w.map)) {
            val s = w.spotOf(p)
            assertNotNull("no spot for ${p.kind}", s)
            assertSame(p, s!!.prop)
        }
        // Props that only decorate have none.
        assertNull(w.spotOf(w.map.props.first { it.kind == PropKind.PILLAR }))
        assertNull(w.spotOf(w.map.props.first { it.kind == PropKind.BENCH }))
    }

    @Test
    fun walkingToAPropsSpotArrivesAndShowsItsPrompt() {
        val w = world()
        for (p in interactive(w.map)) {
            val spot = w.spotOf(p)!!
            w.player.place(w.map.spawnX, w.map.spawnY)
            w.camera.setLook(PI.toFloat(), HubCamera.REST_PITCH_DEG * DEG)
            w.update(FIXED_DT)
            assertTrue("no route to the ${spot.type}", w.walkTo(spot))
            var t = 0f
            while (w.route.active && t < 40f) {
                w.update(FIXED_DT)
                t += FIXED_DT
            }
            repeat((1f / FIXED_DT).roundToInt()) { w.update(FIXED_DT) }
            assertSame("didn't get to the ${spot.type} (at ${w.player.x}, ${w.player.y} after ${t}s)", spot, w.activeSpot)
        }
    }

    @Test
    fun tappingAPropOnScreenWalksToItsSpot() {
        val w = world()
        val cam = Camera3D()
        val out = FloatArray(3)
        for (p in interactive(w.map)) {
            val spot = w.spotOf(p)!!
            // Well back from the front of the prop, looking straight at it.
            val fromX = spot.area.centerX
            val fromZ = spot.area.bottom + 34f
            w.player.place(fromX, fromZ)
            w.camera.setLook(atan2(p.centerX - fromX, p.centerZ - fromZ), 0f)
            w.update(FIXED_DT)
            w.camera.apply(cam, W.toInt(), H.toInt())
            assertTrue("the ${spot.type} isn't on screen", cam.project(p.centerX, p.height * 0.4f, p.frontZ, out))
            w.route.clear()
            assertTrue("tapping the ${spot.type} set no route", w.tapToWalk(out[0], out[1]))
            assertSame("tapping the ${spot.type} walks elsewhere", spot, w.route.spot)
        }
    }
}
