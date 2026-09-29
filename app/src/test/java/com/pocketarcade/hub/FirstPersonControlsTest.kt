package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.r3d.Camera3D
import com.pocketarcade.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * How first person feels: getting up to speed and stopping, slower backpedal and strafe and a
 * run at the rim, the eye at the head, the body's clearance (and that it still fits every aisle),
 * the stick's size and curve, the look drag's smoothing, levelling and turn to face a machine,
 * tap-to-walk, sliding round corners, kids making way, and footsteps in step with the bob.
 */
class FirstPersonControlsTest {
    private companion object {
        const val W = 1080f
        const val H = 2400f
        const val DENSITY = 2.75f
        const val DEG = PI.toFloat() / 180f
    }

    private val games = GameRegistry.createAll()

    /** A first-person world with nobody else in it (kids get their own tests). */
    private fun world(kids: Boolean = false): HubWorld {
        val w = HubWorld(games, null)
        w.density = DENSITY
        w.setViewport(W, H)
        w.setFirstPerson(true, animate = false)
        if (!kids) w.npcs.clear()
        return w
    }

    private fun HubWorld.run(seconds: Float) {
        repeat((seconds / FIXED_DT).roundToInt()) { update(FIXED_DT) }
    }

    /** Holds the stick at [m] of its radius in direction ([dx], [dy]) on screen. */
    private fun HubWorld.stick(m: Float, dx: Float, dy: Float) {
        if (!joystick.active) pointerDown(1, 200f, 1800f)
        val l = hypot(dx, dy)
        pointerMove(1, 200f + dx / l * joystick.radius * m, 1800f + dy / l * joystick.radius * m)
    }

    private fun HubWorld.letGo() {
        pointerUp(1, 0f, 0f)
    }

    /** Standing at the door facing up the main aisle (plenty of open floor ahead). */
    private fun HubWorld.atTheDoor() {
        player.place(map.spawnX, map.spawnY)
        camera.setLook(PI.toFloat(), HubCamera.REST_PITCH_DEG * DEG)
        update(FIXED_DT)
    }

    private fun steadySpeed(m: Float, dx: Float, dy: Float): Float {
        val w = world()
        w.atTheDoor()
        w.stick(m, dx, dy)
        w.run(0.4f)
        val x0 = w.player.x
        val z0 = w.player.y
        w.run(0.2f)
        return hypot(w.player.x - x0, w.player.y - z0) / 0.2f
    }

    // ------------------------------------------------------------------ walking feel

    @Test
    fun gettingUpToSpeedAndStoppingTakeAFractionOfASecond() {
        val w = world()
        w.atTheDoor()
        w.stick(Joystick.FULL_AT, 0f, -1f)
        var t = 0f
        var early = -1f
        while (w.player.speedFrac < 0.99f && t < 1f) {
            w.update(FIXED_DT)
            t += FIXED_DT
            if (early < 0f && t >= 0.05f) early = w.player.speedFrac
        }
        assertTrue("full speed took ${t}s", t in 0.14f..0.24f)
        assertTrue("no jump to full speed: ${early} after 0.05 s", early in 0.15f..0.45f)
        w.run(0.2f)
        w.letGo()
        t = 0f
        while (w.player.moving && t < 1f) {
            w.update(FIXED_DT)
            t += FIXED_DT
        }
        assertTrue("stopping took ${t}s", t in 0.06f..0.14f)
        assertEquals(0f, w.player.speedFrac, 0.05f)
    }

    @Test
    fun backpedalAndStrafeAreSlowerAndTheRimRuns() {
        val fwd = steadySpeed(Joystick.FULL_AT, 0f, -1f)
        val back = steadySpeed(Joystick.FULL_AT, 0f, 1f)
        val side = steadySpeed(Joystick.FULL_AT, 1f, 0f)
        val run = steadySpeed(1.2f, 0f, -1f)
        assertEquals(Player.SPEED, fwd, 1f)
        assertEquals(Player.SPEED * Player.BACK_SCALE, back, 1f)
        assertEquals(Player.SPEED * Player.STRAFE_SCALE, side, 1f)
        assertEquals(Player.SPEED * Player.RUN_SCALE, run, 1.5f)
        assertTrue(Player.RUN_SCALE in 1.3f..1.4f)
    }

    @Test
    fun runningBobsHarderAndWidensTheViewALittle() {
        fun bobAndKick(m: Float): Pair<Float, Float> {
            val w = world()
            w.atTheDoor()
            w.stick(m, 0f, -1f)
            w.run(0.5f)
            var lo = Float.MAX_VALUE
            var hi = -Float.MAX_VALUE
            repeat(60) {
                w.update(FIXED_DT)
                lo = minOf(lo, w.camera.eyeY)
                hi = maxOf(hi, w.camera.eyeY)
            }
            return (hi - lo) to w.camera.fovKick
        }
        val (walkBob, walkKick) = bobAndKick(Joystick.FULL_AT)
        val (runBob, runKick) = bobAndKick(1.2f)
        assertTrue("walking bob $walkBob", walkBob in 0.6f..1.5f)
        assertTrue("running bobs harder: $runBob vs $walkBob", runBob > walkBob * 1.2f && runBob < 2.5f)
        assertEquals(0f, walkKick, 1e-3f)
        assertEquals(HubCamera.RUN_FOV_KICK_DEG, runKick, 0.5f)
        assertTrue(HubCamera.RUN_FOV_KICK_DEG in 2f..6f)
    }

    @Test
    fun turningOnTheSpotTurnsTheViewInPlace() {
        val w = world()
        w.atTheDoor()
        w.player.place(304f, 800f)
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minZ = Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        for (deg in 0 until 360 step 10) {
            w.camera.setLook(deg * DEG, 0f)
            w.update(FIXED_DT)
            val ex = w.camera.eyeX
            val ez = w.camera.eyeZ
            assertTrue(hypot(ex - w.player.x, ez - w.player.y) <= HubCamera.EYE_BACK + 1e-3f)
            minX = minOf(minX, ex); maxX = maxOf(maxX, ex)
            minZ = minOf(minZ, ez); maxZ = maxOf(maxZ, ez)
        }
        // A full turn moves the eye no more than a head's width: it rotates, it doesn't orbit.
        assertTrue("the eye swung ${maxX - minX} x ${maxZ - minZ}", maxX - minX <= 2f * HubCamera.EYE_BACK + 0.01f && maxZ - minZ <= 2f * HubCamera.EYE_BACK + 0.01f)
        assertTrue(HubCamera.EYE_BACK <= 3f)
    }

    // ------------------------------------------------------------------ clearance

    /** Every point on a 2-unit grid the body can stand on and walk to from the door. */
    private class Reach(val map: HubMap, val solids: List<Box>) {
        val step = 2f
        val cols = (map.widthPx / step).toInt()
        val rows = (map.heightPx / step).toInt()
        val seen = BooleanArray(cols * rows)

        init {
            val clear = BooleanArray(cols * rows) { i -> Body.clear(solids, (i % cols) * step, (i / cols) * step) }
            val start = ((map.spawnY / step).roundToInt()) * cols + (map.spawnX / step).roundToInt()
            assertTrue("the body doesn't fit at the door", clear[start])
            val queue = IntArray(cols * rows)
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            while (head < tail) {
                val cur = queue[head++]
                val cx = cur % cols
                val cy = cur / cols
                for (d in 0 until 4) {
                    val nx = cx + if (d == 0) 1 else if (d == 1) -1 else 0
                    val ny = cy + if (d == 2) 1 else if (d == 3) -1 else 0
                    if (nx !in 0 until cols || ny !in 0 until rows) continue
                    val ni = ny * cols + nx
                    if (seen[ni] || !clear[ni]) continue
                    seen[ni] = true
                    queue[tail++] = ni
                }
            }
        }

        /** How close the body can get to ([x], [z]). */
        fun nearest(x: Float, z: Float): Float {
            var best = Float.MAX_VALUE
            for (i in seen.indices) if (seen[i]) best = minOf(best, hypot((i % cols) * step - x, (i / cols) * step - z))
            return best
        }
    }

    @Test
    fun theBodyFitsEveryAisleAndReachesEverySpotQueueAndHangout() {
        // Under half the narrowest aisle the floor plan allows (HubMapTest.MIN_WALKWAY = 21).
        assertTrue(Body.RADIUS * 2f < 21f)
        for (decor in listOf(emptySet(), DecorStyle.entries.toSet())) {
            val map = HubLayout.build(games, decor)
            val solids = Body.solidsFor(map)
            val reach = Reach(map, solids)
            for (s in map.spots) {
                val what = if (s.type == SpotType.MACHINE) games[s.machine].id else s.type.name
                val sx = s.area.centerX
                val sz = (s.area.top + HubWorld.STAND_DEPTH).coerceAtMost(s.area.bottom - 2f)
                assertTrue("$decor: the $what stand point isn't in its spot", s.area.contains(sx, sz))
                assertTrue("$decor: can't reach the $what spot (${reach.nearest(sx, sz)} off)", reach.nearest(sx, sz) <= 1.5f)
                // Standing there, the eye is well back from the cabinet: a machine to look at,
                // not a control panel under your nose.
                assertTrue(sz - HubCamera.EYE_BACK * 0f - s.area.top >= Body.RADIUS + Body.FRONT_GAP - 0.01f)
            }
            for (q in map.cafeQueue) {
                assertTrue("$decor: can't reach the queue spot at (${q.x}, ${q.z})", reach.nearest(q.x, q.z) <= 12f)
            }
            for (h in map.hangouts) {
                // Play spots and seats are for kids standing right at a cabinet or sitting at a
                // table; the body only has to get to their doorstep.
                val limit = if (h.cafe) 24f else if (h.playing) 12f else 2f
                assertTrue("$decor: can't reach the hangout at (${h.x}, ${h.z}) (${reach.nearest(h.x, h.z)})", reach.nearest(h.x, h.z) <= limit)
            }
        }
    }

    // ------------------------------------------------------------------ joystick

    @Test
    fun theStickIsThumbSizedWithAFineCentre() {
        assertEquals(Joystick.RADIUS_DP * DENSITY, Joystick.radiusFor(DENSITY, W, H), 1e-3f)
        assertEquals(56f, Joystick.radiusFor(1f, 480f, 800f), 1e-3f)
        // Capped on a small screen at a high density.
        assertEquals(Joystick.MAX_SCREEN_FRAC * 600f, Joystick.radiusFor(4f, 600f, 1000f), 1e-3f)
        val w = world()
        w.pointerDown(1, 200f, 1800f)
        assertEquals(Joystick.radiusFor(DENSITY, W, H), w.joystick.radius, 1e-3f)
        assertTrue("bigger than the old 11% of the width", w.joystick.radius > W * 0.11f)

        assertEquals(0f, Joystick.curve(Joystick.DEAD_ZONE), 0f)
        assertTrue(Joystick.curve(Joystick.DEAD_ZONE + 0.02f) > 0f)
        var last = 0f
        for (i in 0..100) {
            val v = Joystick.curve(i / 100f)
            assertTrue(v >= last && v <= 1f)
            last = v
        }
        // Fine near the centre: half a push is well under half speed (a straight line gives 0.53).
        assertTrue(Joystick.curve(0.5f) < 0.42f)
        assertEquals(1f, Joystick.curve(Joystick.FULL_AT), 1e-5f)
        assertEquals(1f, Joystick.curve(1f), 0f)
        assertEquals(0f, Joystick.runFor(Joystick.FULL_AT), 0f)
        assertEquals(1f, Joystick.runFor(1f), 0f)
        w.pointerMove(1, 200f, 1800f - w.joystick.radius * 0.5f)
        assertEquals(-Joystick.curve(0.5f), w.joystick.outY, 1e-4f)
        assertEquals(0f, w.joystick.run, 0f)
        w.pointerMove(1, 200f, 1800f - w.joystick.radius * 3f)
        assertEquals(1f, w.joystick.run, 0f)
    }

    // ------------------------------------------------------------------ look

    @Test
    fun theLookDragIsSmoothedButNeverLostOrLate() {
        val w = world()
        w.atTheDoor()
        w.camera.setLook(0f, 0f)
        w.pointerDown(2, 800f, 1200f)
        w.pointerMove(2, 900f, 1200f)
        val k = HubWorld.LOOK_DEG_PER_DP * DEG / DENSITY
        val want = -100f * k
        w.update(FIXED_DT)
        w.update(FIXED_DT)
        assertTrue("under 17 ms, most of the turn is there: ${w.camera.yaw / want}", w.camera.yaw / want > 0.7f)
        repeat(3) { w.update(FIXED_DT) }
        assertTrue("under 42 ms, nearly all of it: ${w.camera.yaw / want}", w.camera.yaw / want > 0.95f)
        w.run(0.1f)
        assertEquals("every pixel counts", want, w.camera.yaw, 1e-4f)
        // Up and down turns less per dp than side to side.
        w.pointerMove(2, 900f, 1300f)
        w.run(0.1f)
        assertEquals(-100f * k * HubWorld.LOOK_PITCH_SCALE, w.camera.pitch, 1e-4f)
        assertTrue(HubWorld.LOOK_PITCH_SCALE in 0.5f..0.9f)
    }

    @Test
    fun aJitteryFingerDoesNotShakeTheView() {
        val w = world()
        w.atTheDoor()
        w.camera.setLook(0f, 0f)
        w.pointerDown(2, 800f, 1200f)
        w.pointerMove(2, 900f, 1200f)
        w.run(0.2f)
        val k = HubWorld.LOOK_DEG_PER_DP * DEG / DENSITY
        // A finger held still, its reported position flickering 3 px either way every frame.
        var worst = 0f
        var prev = w.camera.yaw
        repeat(60) { i ->
            w.pointerMove(2, 900f + if (i % 2 == 0) 3f else -3f, 1200f)
            w.update(FIXED_DT)
            worst = maxOf(worst, abs(w.camera.yaw - prev))
            prev = w.camera.yaw
        }
        val raw = 6f * k
        assertTrue("the view shook ${worst / raw} of the raw flicker", worst < 0.6f * raw)
    }

    @Test
    fun walkingLevelsTheViewOnlyWhenYouLetGoOfIt() {
        val w = world()
        w.atTheDoor()
        w.camera.setLook(PI.toFloat(), 30f * DEG)
        w.stick(Joystick.FULL_AT, 0f, -1f)
        w.run(2.5f)
        assertEquals(HubCamera.REST_PITCH_DEG, w.camera.pitch / DEG, 3f)
        // Standing still, the view stays where you put it.
        w.letGo()
        w.run(0.5f)
        w.camera.setLook(PI.toFloat(), 30f * DEG)
        w.run(2f)
        assertEquals(30f, w.camera.pitch / DEG, 1e-3f)
        // And with a finger on the view, walking leaves it alone too.
        val v = world()
        v.atTheDoor()
        v.camera.setLook(PI.toFloat(), 30f * DEG)
        v.pointerDown(2, 800f, 1200f)
        v.stick(Joystick.FULL_AT, 0f, -1f)
        v.run(2f)
        assertEquals(30f, v.camera.pitch / DEG, 1e-3f)
    }

    private fun toFocus(w: HubWorld, s: Spot) = atan2(s.focusX - w.player.x, s.focusZ - w.player.y)

    @Test
    fun steppingIntoAPlaySpotTurnsTheViewToItsMachine() {
        val w = world()
        val spot = w.map.spots.first { it.type == SpotType.MACHINE }
        w.player.place(spot.area.centerX, spot.area.top + HubWorld.STAND_DEPTH)
        w.camera.setLook(toFocus(w, spot) + 50f * DEG, 20f * DEG)
        w.run(0.8f)
        assertSame(spot, w.activeSpot)
        assertEquals(0f, HubCamera.wrap(w.camera.yaw - toFocus(w, spot)) / DEG, 1f)
        assertTrue(w.camera.pitch / DEG in -20.01f..10.01f)
        // Only once a visit: look away and it stays looking away.
        w.camera.setLook(toFocus(w, spot) + 60f * DEG, 0f)
        w.run(0.8f)
        assertEquals(60f, HubCamera.wrap(w.camera.yaw - toFocus(w, spot)) / DEG, 0.5f)

        // Steering the view yourself: no assist.
        val v = world()
        v.atTheDoor()
        v.pointerDown(2, 800f, 1200f)
        v.pointerMove(2, 900f, 1200f)
        v.run(0.2f)
        v.player.place(spot.area.centerX, spot.area.top + HubWorld.STAND_DEPTH)
        v.camera.setLook(toFocus(v, spot) + 50f * DEG, 0f)
        v.run(0.8f)
        assertEquals(50f, HubCamera.wrap(v.camera.yaw - toFocus(v, spot)) / DEG, 0.5f)
    }

    // ------------------------------------------------------------------ tap to walk

    @Test
    fun tapToWalkReachesEverySpotFromTheDoorAndFacesIt() {
        val w = world()
        for (spot in w.map.spots) {
            val what = if (spot.type == SpotType.MACHINE) games[spot.machine].id else spot.type.name
            w.atTheDoor()
            assertTrue("no route to $what", w.walkTo(spot))
            var t = 0f
            while (w.route.active && t < 30f) {
                w.update(FIXED_DT)
                t += FIXED_DT
                assertTrue("walked into something on the way to $what", Body.clear(w.bodySolids, w.player.x, w.player.y))
            }
            w.run(1f)
            assertSame("didn't get to $what (at ${w.player.x}, ${w.player.y} after ${t}s)", spot, w.activeSpot)
            assertFalse(w.player.moving)
            assertEquals("not facing $what", 0f, HubCamera.wrap(w.camera.yaw - toFocus(w, spot)) / DEG, 2f)
        }
    }

    @Test
    fun tappingAMachineOnScreenWalksToItAndAnyInputCancels() {
        val w = world()
        // In the cross aisle in front of a bank, looking at it.
        val spot = w.map.spots.first { it.type == SpotType.MACHINE && it.area.centerX > 300f }
        w.player.place(spot.area.centerX + 20f, spot.area.bottom + 60f)
        w.camera.setLook(toFocus(w, spot), 0f)
        w.update(FIXED_DT)
        val cam = Camera3D()
        w.camera.apply(cam, W.toInt(), H.toInt())
        val p = FloatArray(3)
        val prop = w.map.props.first { it.kind == PropKind.MACHINE && abs(it.centerX - spot.area.centerX) < 1f && abs(it.z1 - spot.area.top) < 1f }
        // Anywhere on the cabinet: here, low on its front.
        assertTrue(cam.project(prop.centerX, prop.height * 0.3f, prop.z1, p))
        w.pointerDown(3, p[0], p[1])
        assertEquals(null, w.pointerUp(3, p[0], p[1]))
        assertTrue(w.route.active)
        assertSame(spot, w.route.spot)
        // The stick takes over at once.
        w.stick(0.5f, 1f, 0f)
        w.update(FIXED_DT)
        assertFalse(w.route.active)
        w.letGo()
        // So does dragging the view.
        assertTrue(w.walkTo(spot))
        w.pointerDown(2, 800f, 1200f)
        w.pointerMove(2, 900f, 1200f)
        assertFalse(w.route.active)
        w.pointerUp(2, 900f, 1200f)
        // A tap on the floor walks to that spot of floor.
        w.camera.setLook(PI.toFloat(), -20f * DEG)
        w.player.place(304f, 800f)
        w.update(FIXED_DT)
        w.camera.apply(cam, W.toInt(), H.toInt())
        assertTrue(cam.project(310f, 0f, 740f, p))
        w.pointerDown(4, p[0], p[1])
        w.pointerUp(4, p[0], p[1])
        assertTrue(w.route.active)
        assertEquals(310f, w.route.goalX, 1f)
        assertEquals(740f, w.route.goalY, 1f)
        w.run(3f)
        assertFalse(w.route.active)
        assertEquals(0f, hypot(w.player.x - 310f, w.player.y - 740f), 2f)
    }

    @Test
    fun thePromptStillTakesTheTapAtTheEndOfAWalk() {
        val w = world()
        val spot = w.map.spots.first { it.type == SpotType.MACHINE }
        w.atTheDoor()
        assertTrue(w.walkTo(spot))
        w.run(20f)
        assertSame(spot, w.activeSpot)
        w.bubbleLeft = 600f; w.bubbleTop = 500f; w.bubbleRight = 1000f; w.bubbleBottom = 800f
        w.pointerDown(7, 800f, 650f)
        assertSame(spot, w.pointerUp(7, 800f, 650f))
        assertFalse("tapping PLAY doesn't start a walk", w.route.active)
    }

    // ------------------------------------------------------------------ collision feel

    @Test
    fun aCabinetCornerDoesNotSnag() {
        val box = listOf(Box(100f, 100f, 140f, 140f))
        val p = Player()
        // Heading straight up (−z) with the body's centre 3 units inside the box's left edge.
        p.place(103f, 170f)
        var t = 0f
        while (p.y > 90f && t < 2f) {
            p.walkFirstPerson(FIXED_DT, 0f, -1f, box, PI.toFloat())
            t += FIXED_DT
            assertTrue(Body.clear(box, p.x, p.y))
        }
        assertTrue("snagged on the corner at (${p.x}, ${p.y})", p.y <= 90f)
        // Straight into the middle of the face, though, it just stops.
        val q = Player()
        q.place(120f, 170f)
        repeat(240) { q.walkFirstPerson(FIXED_DT, 0f, -1f, box, PI.toFloat()) }
        assertEquals(140f + Body.RADIUS, q.y, 0.05f)
        assertEquals(120f, q.x, 1e-3f)
    }

    @Test
    fun slidingAlongAWallDoesNotJitter() {
        val wall = listOf(Box(80f, 0f, 100f, 400f))
        // Into the wall at 45°, 70° and 85° off its line (nearly head-on).
        for (deg in listOf(45f, 70f, 85f)) {
            val p = Player()
            p.place(115f, 300f)
            val wx = -kotlin.math.sin(deg * DEG)
            val wy = -kotlin.math.cos(deg * DEG)
            var touching = 0
            var prevX = p.x
            val y0 = p.y
            repeat(240) {
                p.walkFirstPerson(FIXED_DT, wx, wy, wall, PI.toFloat())
                if (p.x <= 100f + Body.RADIUS + 0.01f) {
                    if (touching > 0) assertEquals("jitter against the wall at $deg°", prevX, p.x, 1e-3f)
                    touching++
                }
                prevX = p.x
                assertTrue(Body.clear(wall, p.x, p.y))
            }
            assertTrue(touching > 100)
            // Slides along at the speed the stick asks for along the wall.
            val expect = Player.SPEED * -wy * 2f
            assertTrue("slid ${y0 - p.y} along it at $deg° (expected about $expect)", y0 - p.y > expect * 0.8f)
        }
    }

    // ------------------------------------------------------------------ kids

    @Test
    fun kidsMakeWayAndNobodyIsPushedIntoASolid() {
        val w = world(kids = true)
        w.atTheDoor()
        // Walk laps of the hall's middle, turning now and then, through the crowd.
        w.stick(Joystick.FULL_AT, 0f, -1f)
        var closest = Float.MAX_VALUE
        repeat((40f / FIXED_DT).toInt()) { i ->
            if (i % 180 == 0) w.camera.setLook(w.camera.yaw + 1.1f, w.camera.pitch)
            w.update(FIXED_DT)
            assertTrue("the player ended up in a solid at (${w.player.x}, ${w.player.y})", Body.penetration(w.bodySolids, w.player.x, w.player.y) < 0.05f)
            for (n in w.npcs) {
                val d = hypot(n.x - w.player.x, n.y - w.player.y)
                if (d > 30f) continue
                if (n.state == Npc.State.WALK || n.state == Npc.State.IDLE) {
                    assertFalse("a kid near the player is inside something at (${n.x}, ${n.y})", Collision.blocked(w.map.solids, n.x, n.y))
                }
                if (n.state != Npc.State.SIT) closest = minOf(closest, d)
            }
        }
        // Bumps are soft, but nobody walks through anybody.
        assertTrue("walked through a kid ($closest)", closest > Body.RADIUS + HubWorld.KID_RADIUS - 4f)

        // Head-on: a kid right in the player's way steps aside and the player gets past.
        val v = world(kids = true)
        v.npcs.retainAll(listOf(v.npcs[0]))
        val kid = v.npcs[0]
        v.atTheDoor()
        kid.x = v.player.x
        kid.y = v.player.y - 60f
        v.stick(Joystick.FULL_AT, 0f, -1f)
        var minD = Float.MAX_VALUE
        repeat((2.5f / FIXED_DT).toInt()) {
            v.update(FIXED_DT)
            minD = minOf(minD, hypot(kid.x - v.player.x, kid.y - v.player.y))
            assertFalse(Collision.blocked(v.map.solids, kid.x, kid.y))
        }
        assertTrue("the camera ended up inside the kid ($minD)", minD > Body.RADIUS + HubWorld.KID_RADIUS - 4f)
        assertTrue("never got past the kid", v.player.y < kid.y)
    }

    // ------------------------------------------------------------------ footsteps

    @Test
    fun footstepsLandAtTheBottomOfTheBobAndQuickenWithThePace() {
        fun walk(m: Float): Triple<Int, Float, Boolean> {
            val w = world()
            w.atTheDoor()
            w.stick(m, 0f, -1f)
            w.run(0.4f)
            val before = w.steps
            var inStep = true
            var pitch = 0f
            repeat((2f / FIXED_DT).toInt()) {
                val n = w.steps
                w.update(FIXED_DT)
                if (w.steps != n) {
                    pitch += w.lastStepPitch
                    // A foot lands as the head is at its lowest.
                    val low = HubCamera.EYE_HEIGHT - 0.2f * HubCamera.BOB_HEIGHT * w.camera.bobWeight
                    if (w.camera.eyeY > low) inStep = false
                }
            }
            val count = w.steps - before
            return Triple(count, pitch / count.coerceAtLeast(1), inStep)
        }
        val (walkSteps, walkPitch, walkInStep) = walk(Joystick.FULL_AT)
        val (runSteps, runPitch, runInStep) = walk(1.2f)
        assertTrue("$walkSteps steps in 2 s of walking", walkSteps in 6..10)
        assertTrue("running takes quicker steps: $runSteps vs $walkSteps", runSteps > walkSteps)
        assertTrue("brisker steps sound higher: $runPitch vs $walkPitch", runPitch > walkPitch)
        assertTrue(walkInStep && runInStep)
    }
}
