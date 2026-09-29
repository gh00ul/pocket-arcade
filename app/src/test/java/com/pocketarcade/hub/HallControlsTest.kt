package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.data.GameSettings
import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.ScreenShake
import com.pocketarcade.engine.r3d.Camera3D
import com.pocketarcade.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The hall's controls beyond first person's basics: the run latch, tap-to-walk from the overhead
 * camera, and how the player's settings (look speed and direction, handedness, field of view,
 * reduced motion) reach the hall.
 */
class HallControlsTest {
    private companion object {
        const val W = 1080f
        const val H = 2400f
        const val DENSITY = 2.75f
        const val DEG = PI.toFloat() / 180f
    }

    private val games = GameRegistry.createAll()

    /** A world with nobody else in it, overhead unless [firstPerson]. */
    private fun world(firstPerson: Boolean = false, settings: GameSettings = GameSettings()): HubWorld {
        val w = HubWorld(games, null)
        w.density = DENSITY
        w.setViewport(W, H)
        w.npcs.clear()
        w.applySettings(settings)
        w.setFirstPerson(firstPerson, animate = false)
        return w
    }

    private fun HubWorld.run(seconds: Float) {
        repeat((seconds / FIXED_DT).roundToInt()) { update(FIXED_DT) }
    }

    /** Runs until the route has ended (or [limit] seconds), then a moment more. */
    private fun HubWorld.finishWalk(limit: Float = 40f) {
        var t = 0f
        while (route.active && t < limit) {
            update(FIXED_DT)
            t += FIXED_DT
        }
        run(1f)
    }

    private fun toFocus(w: HubWorld, s: Spot) = atan2(s.focusX - w.player.x, s.focusZ - w.player.y)

    // ------------------------------------------------------------------ the run latch

    private fun stick(latch: Boolean): Joystick = Joystick().also {
        it.radius = 100f
        it.runLatch = latch
        it.down(1, 500f, 500f)
    }

    @Test
    fun aLatchedRunKeepsGoingWhenTheThumbEasesOffTheRim() {
        val j = stick(latch = true)
        j.move(1, 500f, 500f - 85f)
        assertFalse("walking pace isn't the rim", j.latched)
        assertEquals(0f, j.run, 0f)
        // Past the rim: the run locks, at full speed (the base trails the thumb).
        j.move(1, 500f, 500f - 130f)
        assertTrue(j.latched)
        assertEquals(1f, j.run, 0f)
        // The thumb relaxes to 60% of the way: still running, still at full speed, same heading.
        j.move(1, 500f, 500f - 130f + 100f * 0.4f)
        assertEquals(0.6f, hypot(j.knobX - j.baseX, j.knobY - j.baseY) / j.radius, 1e-3f)
        assertTrue(j.latched)
        assertEquals(1f, j.run, 0f)
        assertEquals(0f, j.outX, 1e-4f)
        assertEquals(-1f, j.outY, 1e-4f)
        // Turning about with the thumb still well out keeps the run and the new heading.
        j.move(1, j.baseX + 60f, j.baseY)
        assertTrue(j.latched)
        assertEquals(1f, j.outX, 1e-4f)
        assertEquals(0f, j.outY, 1e-4f)
        // Down to under half: a real ease-off, the run lets go and it's the ordinary curve again.
        j.move(1, j.baseX + 40f, j.baseY)
        assertFalse(j.latched)
        assertEquals(0f, j.run, 0f)
        assertEquals(Joystick.curve(0.4f), j.outX, 1e-4f)
        // Easing off doesn't re-latch by itself: it takes the rim again.
        j.move(1, j.baseX + 90f, j.baseY)
        assertFalse(j.latched)
        j.move(1, j.baseX + 120f, j.baseY)
        assertTrue(j.latched)
    }

    @Test
    fun liftingTheThumbEndsALatchedRunAndTheNextTouchStartsFresh() {
        val j = stick(latch = true)
        j.move(1, 500f, 500f - 130f)
        assertTrue(j.latched)
        j.up(1)
        assertFalse(j.latched)
        assertEquals(0f, j.run, 0f)
        assertEquals(0f, j.outX, 0f)
        j.down(2, 300f, 300f)
        assertFalse(j.latched)
        j.move(2, 300f, 300f - 60f)
        assertFalse(j.latched)
        // Letting go without a lift (a dialog, a pause) does it too.
        j.move(2, 300f, 300f - 130f)
        assertTrue(j.latched)
        j.release()
        assertFalse(j.latched)
    }

    @Test
    fun theRimModeStillRunsOnlyWhileTheThumbIsAtTheRim() {
        val j = stick(latch = false)
        j.move(1, 500f, 500f - 130f)
        assertFalse(j.latched)
        assertEquals(1f, j.run, 0f)
        j.move(1, 500f, 500f - 130f + 100f * 0.4f)
        assertEquals(0f, j.run, 0f)
        assertEquals(Joystick.curve(0.6f), -j.outY, 1e-4f)
    }

    @Test
    fun theWorldLatchesOnlyInFirstPersonAndOnlyIfTheSettingIsOn() {
        // First person: the left thumb has the stick.
        val fp = world(firstPerson = true)
        fp.pointerDown(1, 200f, 2000f)
        assertTrue(fp.joystick.runLatch)
        fp.cancelInput()
        val over = world(firstPerson = false)
        over.pointerDown(1, 800f, 2000f)
        assertFalse("overhead has no run to lock", over.joystick.runLatch)
        val rim = world(firstPerson = true, settings = GameSettings(runLatch = false))
        rim.pointerDown(1, 200f, 2000f)
        assertFalse(rim.joystick.runLatch)
    }

    @Test
    fun aLatchedRunHoldsRunSpeedWithARelaxedThumb() {
        fun speed(latch: Boolean): Float {
            val w = world(firstPerson = true, settings = GameSettings(runLatch = latch))
            w.player.place(w.map.spawnX, w.map.spawnY)
            w.camera.setLook(PI.toFloat(), HubCamera.REST_PITCH_DEG * DEG)
            w.update(FIXED_DT)
            w.pointerDown(1, 200f, 1800f)
            val r = w.joystick.radius
            // Out past the rim, then back to 60% of the way.
            w.pointerMove(1, 200f, 1800f - r * 1.3f)
            w.run(0.3f)
            w.pointerMove(1, 200f, 1800f - r * 1.3f + r * 0.4f)
            w.run(0.4f)
            val x0 = w.player.x
            val z0 = w.player.y
            w.run(0.2f)
            return hypot(w.player.x - x0, w.player.y - z0) / 0.2f
        }
        assertEquals(Player.SPEED * Player.RUN_SCALE, speed(latch = true), 1.5f)
        // The rim mode slows to a walk-and-a-bit at 60%.
        assertTrue(speed(latch = false) < Player.SPEED * 0.6f)
    }

    // ------------------------------------------------------------------ tap to walk, overhead

    /** Overhead, the camera settled behind a player standing at ([x], [z]). */
    private fun HubWorld.overheadAt(x: Float, z: Float) {
        player.place(x, z)
        run(3f)
    }

    private fun HubWorld.tap(id: Long, sx: Float, sy: Float): Spot? {
        pointerDown(id, sx, sy)
        return pointerUp(id, sx, sy)
    }

    @Test
    fun tappingTheFloorOverheadWalksThereAndStops() {
        val w = world()
        w.overheadAt(304f, 800f)
        val cam = Camera3D()
        w.camera.apply(cam, W.toInt(), H.toInt())
        val p = FloatArray(3)
        assertTrue(cam.project(340f, 0f, 700f, p))
        assertEquals(null, w.tap(3, p[0], p[1]))
        assertTrue("no route to a tapped floor point", w.route.active)
        assertEquals(340f, w.route.goalX, 1f)
        assertEquals(700f, w.route.goalY, 1f)
        assertTrue(w.hasWalked)
        w.finishWalk()
        assertFalse(w.route.active)
        assertEquals(0f, hypot(w.player.x - 340f, w.player.y - 700f), 2f)
        assertFalse(w.player.moving)
    }

    @Test
    fun tappingAMachineOverheadWalksToItsSpotAndFacesIt() {
        val w = world()
        val spot = w.map.spots.first { it.type == SpotType.MACHINE && it.area.centerX > 300f }
        w.overheadAt(spot.area.centerX + 30f, spot.area.bottom + 90f)
        val prop = w.map.props.first { it.kind == PropKind.MACHINE && abs(it.centerX - spot.area.centerX) < 1f && abs(it.z1 - spot.area.top) < 1f }
        val cam = Camera3D()
        w.camera.apply(cam, W.toInt(), H.toInt())
        val p = FloatArray(3)
        // The top of the cabinet, as seen from above.
        assertTrue(cam.project(prop.centerX, prop.height, prop.centerZ, p))
        assertEquals(null, w.tap(3, p[0], p[1]))
        assertTrue(w.route.active)
        assertSame(spot, w.route.spot)
        w.finishWalk()
        assertSame("didn't stop at the machine's spot", spot, w.activeSpot)
        assertFalse(w.player.moving)
        assertEquals("not facing the machine", 0f, HubCamera.wrap(w.player.yaw - toFocus(w, spot)), 0.06f)
    }

    @Test
    fun theStickOverridesAnOverheadWalkAtOnce() {
        val w = world()
        w.overheadAt(304f, 800f)
        assertTrue(w.walkToPoint(304f, 500f))
        w.run(0.3f)
        assertTrue(w.route.active)
        val y0 = w.player.y
        assertTrue("walking north", y0 < 800f)
        // A thumb pushing the other way (east): the route is dropped and the stick has the kid.
        w.pointerDown(1, 200f, 1800f)
        w.pointerMove(1, 200f + w.joystick.radius, 1800f)
        w.update(FIXED_DT)
        assertFalse(w.route.active)
        val x0 = w.player.x
        w.run(0.4f)
        assertTrue("the stick walked the kid east", w.player.x > x0 + 15f)
        assertEquals(y0, w.player.y, 8f)
        w.pointerUp(1, 0f, 0f)
        w.run(0.2f)
        assertFalse(w.player.moving)
    }

    @Test
    fun aDragOverheadIsStillJustWalkingAndNotATap() {
        val w = world()
        w.overheadAt(304f, 800f)
        w.pointerDown(1, 500f, 1500f)
        w.pointerMove(1, 500f, 1500f - 200f)
        w.run(0.5f)
        assertEquals(null, w.pointerUp(1, 500f, 1300f))
        assertFalse("a long drag isn't a tap", w.route.active)
        // Nor is a press that's held.
        w.pointerDown(2, 500f, 1500f)
        w.run(0.6f)
        w.pointerUp(2, 500f, 1500f)
        assertFalse(w.route.active)
    }

    @Test
    fun aTapMidTransitionBetweenTheViewsIsIgnored() {
        val w = world()
        w.overheadAt(304f, 800f)
        w.setFirstPerson(true, animate = true)
        w.run(0.2f)
        assertTrue(w.camera.fpAmount in 0.01f..0.99f)
        assertFalse(w.tapToWalk(540f, 1500f))
        assertFalse(w.route.active)
    }

    @Test
    fun everySpotIsReachableOverheadFromTheDoorAndFaced() {
        for (decor in listOf(emptySet(), DecorStyle.entries.toSet())) {
            val w = world()
            w.setDecor(decor)
            for (spot in w.map.spots) {
                val what = if (spot.type == SpotType.MACHINE) games[spot.machine].id else spot.type.name
                w.player.place(w.map.spawnX, w.map.spawnY)
                w.run(0.1f)
                assertTrue("$decor: no route to $what", w.walkTo(spot))
                var t = 0f
                while (w.route.active && t < 40f) {
                    w.update(FIXED_DT)
                    t += FIXED_DT
                    assertFalse("$decor: walked into something on the way to $what", Collision.blocked(w.map.solids, w.player.x, w.player.y))
                }
                w.run(1f)
                assertSame("$decor: didn't get to $what (at ${w.player.x}, ${w.player.y} after ${t}s)", spot, w.activeSpot)
                assertFalse(w.player.moving)
                assertEquals("$decor: not facing $what", 0f, HubCamera.wrap(w.player.yaw - toFocus(w, spot)), 0.06f)
            }
        }
    }

    @Test
    fun walkingToTheSpotYouAreAlreadyAtJustTurnsYouToFaceIt() {
        val w = world()
        val spot = w.map.spots.first { it.type == SpotType.MACHINE }
        w.player.place(spot.area.centerX, (spot.area.top + HubWorld.STAND_DEPTH).coerceAtMost(spot.area.bottom - 2f))
        w.player.yaw = 0f
        w.run(0.1f)
        assertTrue(w.walkTo(spot))
        assertFalse(w.route.active)
        w.run(1f)
        assertEquals(0f, HubCamera.wrap(w.player.yaw - toFocus(w, spot)), 0.06f)
    }

    // ------------------------------------------------------------------ settings

    @Test
    fun leftHandedSwapsTheWalkAndLookHalves() {
        assertTrue(HubWorld.isLookSide(800f, W, leftHanded = false))
        assertFalse(HubWorld.isLookSide(200f, W, leftHanded = false))
        assertFalse(HubWorld.isLookSide(800f, W, leftHanded = true))
        assertTrue(HubWorld.isLookSide(200f, W, leftHanded = true))
        for (x in listOf(0f, 1f, 539f, 540f, 541f, 1079f)) {
            assertTrue("$x", HubWorld.isLookSide(x, W, false) != HubWorld.isLookSide(x, W, true))
        }

        val right = world(firstPerson = true)
        right.pointerDown(1, 800f, 1800f)
        assertEquals(1L, right.lookPointer)
        assertFalse(right.joystick.active)
        right.pointerDown(2, 200f, 1800f)
        assertTrue(right.joystick.active)

        val left = world(firstPerson = true, settings = GameSettings(leftHanded = true))
        left.pointerDown(1, 800f, 1800f)
        assertTrue("the right thumb walks", left.joystick.active)
        assertEquals(-1L, left.lookPointer)
        left.pointerDown(2, 200f, 1800f)
        assertEquals("the left thumb looks", 2L, left.lookPointer)
        // Overhead, handedness doesn't matter: anywhere starts the stick.
        val over = world(firstPerson = false, settings = GameSettings(leftHanded = true))
        over.pointerDown(1, 200f, 1800f)
        assertTrue(over.joystick.active)
    }

    @Test
    fun lookSpeedAndInvertScaleAndFlipTheDrag() {
        fun turned(s: GameSettings): Pair<Float, Float> {
            val w = world(firstPerson = true, settings = s)
            w.player.place(w.map.spawnX, w.map.spawnY)
            w.camera.setLook(0f, 0f)
            w.update(FIXED_DT)
            w.pointerDown(2, 800f, 1200f)
            w.pointerMove(2, 900f, 1300f)
            w.run(0.3f)
            return w.camera.yaw to w.camera.pitch
        }
        val k = HubWorld.LOOK_DEG_PER_DP * DEG / DENSITY
        val (yaw, pitch) = turned(GameSettings())
        assertEquals(-100f * k, yaw, 1e-4f)
        assertEquals(-100f * k * HubWorld.LOOK_PITCH_SCALE, pitch, 1e-4f)
        val (fastYaw, fastPitch) = turned(GameSettings(lookPercent = 200))
        assertEquals(2f * yaw, fastYaw, 1e-4f)
        assertEquals(2f * pitch, fastPitch, 1e-4f)
        val (slowYaw, _) = turned(GameSettings(lookPercent = 50))
        assertEquals(0.5f * yaw, slowYaw, 1e-4f)
        // Inverted: the same drag tilts the other way, and turns the same.
        val (invYaw, invPitch) = turned(GameSettings(invertY = true))
        assertEquals(yaw, invYaw, 1e-4f)
        assertEquals(-pitch, invPitch, 1e-4f)
    }

    @Test
    fun theFieldOfViewSettingScalesFirstPersonAndTheDefaultChangesNothing() {
        fun focal(s: GameSettings): Float {
            val w = world(firstPerson = true, settings = s)
            w.update(FIXED_DT)
            val cam = Camera3D()
            w.camera.apply(cam, W.toInt(), H.toInt())
            return cam.focal
        }
        val plain = HubWorld(games, null)
        plain.setViewport(W, H)
        plain.setFirstPerson(true, animate = false)
        val ref = Camera3D()
        plain.camera.apply(ref, W.toInt(), H.toInt())
        assertEquals("the default FOV must be the designed view", ref.focal, focal(GameSettings()), 1e-3f)
        assertTrue("wider view, shorter focal length", focal(GameSettings(fovDeg = 90)) < ref.focal)
        assertTrue("narrower view, longer focal length", focal(GameSettings(fovDeg = 60)) > ref.focal)
        // It only reaches first person: overhead is untouched.
        val over = world(firstPerson = false, settings = GameSettings(fovDeg = 90))
        val overRef = Camera3D()
        HubWorld(games, null).camera.apply(overRef, W.toInt(), H.toInt())
        val overCam = Camera3D()
        over.camera.apply(overCam, W.toInt(), H.toInt())
        assertEquals(overRef.focal, overCam.focal, 1e-3f)
    }

    @Test
    fun reducedMotionStopsTheBobTheRunKickAndTheShake() {
        fun bobAndKick(s: GameSettings): Pair<Float, Float> {
            val w = world(firstPerson = true, settings = s)
            w.player.place(w.map.spawnX, w.map.spawnY)
            w.camera.setLook(PI.toFloat(), HubCamera.REST_PITCH_DEG * DEG)
            w.update(FIXED_DT)
            w.pointerDown(1, 200f, 1800f)
            w.pointerMove(1, 200f, 1800f - w.joystick.radius * 1.3f)
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
        val (bob, kick) = bobAndKick(GameSettings())
        assertTrue("running bobs: $bob", bob > 0.6f)
        assertEquals(HubCamera.RUN_FOV_KICK_DEG, kick, 0.5f)
        val (calmBob, calmKick) = bobAndKick(GameSettings(reduceMotion = true))
        assertEquals(0f, calmBob, 1e-4f)
        assertEquals(0f, calmKick, 1e-4f)

        fun shake(): Float {
            val s = ScreenShake()
            s.add(1f)
            var peak = 0f
            repeat(20) {
                s.update(0.02f)
                peak = maxOf(peak, abs(s.offsetX) + abs(s.offsetY))
            }
            return peak
        }
        try {
            assertTrue(shake() > 1f)
            ScreenShake.intensity = 0f
            assertEquals(0f, shake(), 0f)
        } finally {
            ScreenShake.intensity = 1f
        }
    }

    @Test
    fun settingsReachTheHallAndTheDefaultsLeaveItAsItWas() {
        val w = HubWorld(games, null)
        assertEquals(GameSettings(), w.settings)
        assertEquals(1f, w.camera.fovScale, 0f)
        assertEquals(1f, w.camera.bobScale, 0f)
        assertEquals(1f, w.camera.kickScale, 0f)
        w.applySettings(GameSettings(fovDeg = 87, reduceMotion = true, lookPercent = 7777))
        // 87 snaps to the nearest step, 85.
        assertEquals(85f / HubCamera.FP_FOV_DEG, w.camera.fovScale, 1e-5f)
        assertEquals(0f, w.camera.bobScale, 0f)
        assertEquals(0f, w.camera.kickScale, 0f)
        assertEquals("out-of-range values are clamped on the way in", 200, w.settings.lookPercent)
        assertNotNull(w.settings)
        w.applySettings(GameSettings())
        assertEquals(1f, w.camera.bobScale, 0f)
    }

    @Test
    fun theHudExtraRowPushesTheBottomOfTheHudDown() {
        val w = HubWorld(games, null)
        w.hudBottom = 250f
        assertEquals(250f, w.hudBottom, 0f)
        w.hudExtra = 130f
        assertEquals(380f, w.hudBottom, 0f)
        // The first row's height is set each layout, without forgetting the extra.
        w.hudBottom = 260f
        assertEquals(390f, w.hudBottom, 0f)
    }
}
