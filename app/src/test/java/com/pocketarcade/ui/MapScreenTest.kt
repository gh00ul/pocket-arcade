package com.pocketarcade.ui

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.Pal
import com.pocketarcade.games.GameRegistry
import com.pocketarcade.hub.Collision
import com.pocketarcade.hub.HubLayout
import com.pocketarcade.hub.HubWorld
import com.pocketarcade.hub.PropKind
import com.pocketarcade.hub.SpotType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The quick-travel map: it has a marker for everything worth going to, tapping one gets you
 * there from the doors in either camera, and its labels stay readable (clear of each other and
 * on the map) at any size.
 */
class MapScreenTest {
    private val games = GameRegistry.createAll()

    private fun world(firstPerson: Boolean, decor: Set<DecorStyle> = emptySet()): HubWorld {
        val w = HubWorld(games, null)
        w.density = 2.75f
        w.setViewport(1080f, 2400f)
        w.npcs.clear()
        w.setDecor(decor)
        w.setFirstPerson(firstPerson, animate = false)
        return w
    }

    private fun HubWorld.atTheDoor() {
        player.place(map.spawnX, map.spawnY)
        update(FIXED_DT)
    }

    @Test
    fun everyMachineHasOneLabelInItsGlowAndMarqueeAndThereAreTheOtherPlaces() {
        val map = HubLayout.build(games, emptySet())
        val pins = HallMap.pins(map, games)
        for ((i, g) in games.withIndex()) {
            val mine = pins.filter { it.kind == PinKind.MACHINE && it.spot?.machine == i }
            assertEquals("${g.id}: a pin for every cabinet", map.spots.count { it.type == SpotType.MACHINE && it.machine == i }, mine.size)
            val labelled = mine.filter { it.labelled }
            assertEquals("${g.id}: exactly one label", 1, labelled.size)
            assertEquals(g.marquee, labelled[0].text)
            assertEquals(g.look.glow, labelled[0].color)
            for (p in mine) assertEquals(g.look.glow, p.color)
        }
        for (kind in listOf(PinKind.TOKENS, PinKind.PRIZES, PinKind.CAFE, PinKind.DOORS)) {
            val of = pins.filter { it.kind == kind }
            assertEquals("$kind", 1, of.size)
            assertTrue(of[0].labelled)
        }
        assertSame(map.spots.first { it.type == SpotType.TOKENS }, pins.first { it.kind == PinKind.TOKENS }.spot)
        assertSame(map.spots.first { it.type == SpotType.PRIZES }, pins.first { it.kind == PinKind.PRIZES }.spot)
        assertEquals(null, pins.first { it.kind == PinKind.CAFE }.spot)
        // Everything sits on the floor plan.
        for (p in pins) {
            assertTrue("${p.text} at (${p.x}, ${p.z})", p.x in 0f..HallMap.WIDTH && p.z in 0f..HallMap.DEPTH)
        }
        // A label sits on the cabinet nearest the middle of its bank.
        val mids = HashMap<Int, Float>()
        for ((i, _) in games.withIndex()) {
            val xs = map.spots.filter { it.type == SpotType.MACHINE && it.machine == i }.map { it.area.centerX }
            mids[i] = xs.average().toFloat()
            val lead = pins.first { it.kind == PinKind.MACHINE && it.spot?.machine == i && it.labelled }
            for (x in xs) assertTrue(abs(lead.x - mids[i]!!) <= abs(x - mids[i]!!) + 1e-3f)
        }
    }

    @Test
    fun everyPinIsReachableFromTheDoorInBothCameras() {
        for (fp in listOf(false, true)) {
            for (decor in listOf(emptySet(), DecorStyle.entries.toSet())) {
                val w = world(fp, decor)
                val pins = HallMap.pins(w.map, games)
                for (pin in pins) {
                    val what = "${pin.kind} ${pin.text} (${if (fp) "first person" else "overhead"}, $decor)"
                    w.atTheDoor()
                    assertTrue("no route to $what", pin.go(w))
                    var t = 0f
                    while (w.route.active && t < 45f) {
                        w.update(FIXED_DT)
                        t += FIXED_DT
                        assertFalse("walked into something on the way to $what", Collision.blocked(w.map.solids, w.player.x, w.player.y))
                    }
                    w.run(1f)
                    val spot = pin.spot
                    if (spot != null) {
                        assertSame("didn't get to $what (at ${w.player.x}, ${w.player.y} after ${t}s)", spot, w.activeSpot)
                    } else {
                        // A floor point: close to it (it is nudged to where the body fits).
                        val d = hypot(w.player.x - pin.goalX, w.player.y - pin.goalZ)
                        assertTrue("stopped $d from the goal of $what", d < 12f)
                    }
                    assertFalse(w.player.moving)
                }
            }
        }
    }

    @Test
    fun travellingFromAnywhereOnTheFloorWorksToo() {
        // From the far corner of the hall, the café and the prize counter are a long way round.
        val w = world(firstPerson = false)
        val pins = HallMap.pins(w.map, games)
        for (start in listOf(60f to 100f, 560f to 300f, 300f to 620f, 80f to 900f)) {
            assertFalse("start $start is inside something", Collision.blocked(w.map.solids, start.first, start.second))
            for (pin in pins.filter { it.kind != PinKind.MACHINE }) {
                w.player.place(start.first, start.second)
                w.run(0.1f)
                assertTrue("no route from $start to ${pin.text}", pin.go(w))
                w.finishWalk()
                val spot = pin.spot
                if (spot != null) assertSame("$start -> ${pin.text}", spot, w.activeSpot)
            }
        }
    }

    private fun HubWorld.run(seconds: Float) {
        repeat((seconds / FIXED_DT).roundToInt()) { update(FIXED_DT) }
    }

    private fun HubWorld.finishWalk() {
        var t = 0f
        while (route.active && t < 60f) {
            update(FIXED_DT)
            t += FIXED_DT
        }
        run(1f)
    }

    @Test
    fun theDoorsPinWalksYouBackToWhereYouStart() {
        val w = world(firstPerson = false)
        val doors = HallMap.pins(w.map, games).first { it.kind == PinKind.DOORS }
        w.player.place(300f, 600f)
        w.run(0.1f)
        assertTrue(doors.go(w))
        w.finishWalk()
        assertEquals(0f, hypot(w.player.x - w.map.spawnX, w.player.y - w.map.spawnY), 3f)
    }

    @Test
    fun theTintsAreForSolidPropsAndTheLeftOutOnesAreNot() {
        val map = HubLayout.build(games, DecorStyle.entries.toSet())
        for (p in map.props) {
            if (p.solid && p.kind != PropKind.MACHINE) {
                // A solid thing you can bump into is on the plan, apart from the plain furniture.
                if (p.kind !in setOf(PropKind.STOOL, PropKind.CHAIR, PropKind.CAFE_FLOOR, PropKind.DOORS)) {
                    assertTrue("${p.kind} isn't tinted", HallMap.propColor(p.kind) != 0)
                }
            }
        }
        assertEquals(Pal.PINK, HallMap.propColor(PropKind.COUNTER))
        assertEquals(0, HallMap.propColor(PropKind.DOORS))
    }

    // ------------------------------------------------------------------ geometry, labels, taps

    @Test
    fun theFloorPlanFitsWholeAndCentred() {
        for ((w, h) in listOf(926f to 1780f, 600f to 1000f, 1200f to 700f, 300f to 300f)) {
            val g = HallMap.Geometry(w, h)
            assertTrue(g.sx(0f) >= -0.01f && g.sx(HallMap.WIDTH) <= w + 0.01f)
            assertTrue(g.sy(0f) >= -0.01f && g.sy(HallMap.DEPTH) <= h + 0.01f)
            // Filled one way, centred the other.
            val slackX = w - HallMap.WIDTH * g.scale
            val slackY = h - HallMap.DEPTH * g.scale
            assertTrue(slackX < 0.01f || slackY < 0.01f)
            assertEquals(slackX / 2f, g.left, 0.01f)
            assertEquals(slackY / 2f, g.top, 0.01f)
        }
        // An unmeasured area doesn't crash or scale to nothing.
        assertEquals(1f, HallMap.Geometry(0f, 0f).scale, 0f)
    }

    /** Label boxes for the real map at a screen [w] × [h] with text [unit] px per grid unit. */
    private fun labelBoxes(w: Float, h: Float, density: Float): Triple<List<MapPin>, FloatArray, FloatArray> {
        val map = HubLayout.build(games, emptySet())
        val labelled = HallMap.pins(map, games).filter { it.labelled }
        val g = HallMap.Geometry(w, h)
        val lu = 1.7f * density
        val n = labelled.size
        // About what the real font measures: 4.4 grid units a character, condensed capitals.
        val lh = 5f * lu + 8f * density
        val lw = FloatArray(n) { labelled[it].text.length * 4.4f * lu + HallMap.labelPad(lh, density) }
        val ax = FloatArray(n) { g.sx(labelled[it].x) }
        val ay = FloatArray(n) { g.sy(labelled[it].z) }
        val ox = FloatArray(n)
        val oy = FloatArray(n)
        HallMap.placeLabels(n, ax, ay, lw, lh, 3f * density, w, h, ox, oy)
        // Checked here, where the sizes are known.
        for (i in 0 until n) {
            assertTrue("${labelled[i].text} runs off the left", ox[i] - lw[i] / 2f >= -0.01f)
            assertTrue("${labelled[i].text} runs off the right", ox[i] + lw[i] / 2f <= w + 0.01f)
            assertTrue("${labelled[i].text} runs off the top", oy[i] - lh / 2f >= -0.01f)
            assertTrue("${labelled[i].text} runs off the bottom", oy[i] + lh / 2f <= h + 0.01f)
            for (j in 0 until i) {
                val apartX = abs(ox[i] - ox[j]) - (lw[i] + lw[j]) / 2f
                val apartY = abs(oy[i] - oy[j]) - lh
                assertTrue("${labelled[i].text} and ${labelled[j].text} overlap", apartX >= 0f || apartY >= 0f)
            }
            // Never far from what it labels.
            assertTrue("${labelled[i].text} moved ${hypot(ox[i] - ax[i], oy[i] - ay[i])} px", hypot(ox[i] - ax[i], oy[i] - ay[i]) <= 4f * lh)
        }
        return Triple(labelled, ox, oy)
    }

    @Test
    fun labelsDoNotOverlapAtAnyPhoneSize() {
        for ((w, h, d) in listOf(
            Triple(926f, 1780f, 2.75f), Triple(1080f, 1800f, 2.75f), Triple(700f, 1250f, 2f),
            Triple(560f, 900f, 1.5f), Triple(1400f, 1000f, 2.5f), Triple(900f, 1500f, 3f),
        )) {
            labelBoxes(w, h, d)
        }
    }

    @Test
    fun placingLabelsSlidesACollidingOneAndKeepsTheOthersPut() {
        val ax = floatArrayOf(100f, 110f, 400f)
        val ay = floatArrayOf(200f, 205f, 200f)
        val w = floatArrayOf(80f, 80f, 80f)
        val ox = FloatArray(3)
        val oy = FloatArray(3)
        HallMap.placeLabels(3, ax, ay, w, 20f, 3f, 500f, 400f, ox, oy)
        assertEquals(100f, ox[0], 0f)
        assertEquals(200f, oy[0], 0f)
        assertTrue("the second slid clear of the first", abs(oy[1] - oy[0]) >= 20f + 3f - 1e-3f)
        assertEquals(400f, ox[2], 0f)
        assertEquals(200f, oy[2], 0f)
        // One that would hang off the edge is pulled back on.
        HallMap.placeLabels(1, floatArrayOf(-50f), floatArrayOf(500f), floatArrayOf(80f), 20f, 3f, 500f, 400f, ox, oy)
        assertEquals(40f, ox[0], 0f)
        assertEquals(390f, oy[0], 0f)
    }

    @Test
    fun aTapPicksTheNearestPinAndIgnoresEmptyFloor() {
        val (labelled, ox, oy) = labelBoxes(926f, 1780f, 2.75f)
        val n = labelled.size
        val hw = FloatArray(n) { 60f }
        val hh = FloatArray(n) { 30f }
        for (i in 0 until n) {
            assertEquals(i, HallMap.hit(n, ox, oy, hw, hh, ox[i], oy[i], 40f))
            // A little off the box still counts.
            assertEquals(i, HallMap.hit(n, ox, oy, hw, hh, ox[i] + hw[i] / 2f + 20f, oy[i], 40f))
        }
        // Far from every pin: nothing.
        assertEquals(-1, HallMap.hit(n, ox, oy, hw, hh, -500f, -500f, 40f))
        assertEquals(-1, HallMap.hit(0, ox, oy, hw, hh, 10f, 10f, 40f))
        // Between two, the nearer wins.
        val fx = floatArrayOf(100f, 300f)
        val fy = floatArrayOf(100f, 100f)
        val fw = floatArrayOf(20f, 20f)
        assertEquals(0, HallMap.hit(2, fx, fy, fw, fw, 190f, 100f, 100f))
        assertEquals(1, HallMap.hit(2, fx, fy, fw, fw, 210f, 100f, 100f))
    }

    @Test
    fun walkingAcrossTheHallUsesTheSameRouteFollowingEitherCamera() {
        // Sanity for the map's promise: the route it starts is followed the same in both views.
        for (fp in listOf(false, true)) {
            val w = world(fp)
            w.atTheDoor()
            val prizes = HallMap.pins(w.map, games).first { it.kind == PinKind.PRIZES }
            assertTrue(prizes.go(w))
            assertNotNull(w.route.spot)
            assertTrue(w.route.active)
            w.run(0.5f)
            assertTrue("walking north (${if (fp) "first person" else "overhead"})", w.player.y < w.map.spawnY - 20f)
        }
    }
}
