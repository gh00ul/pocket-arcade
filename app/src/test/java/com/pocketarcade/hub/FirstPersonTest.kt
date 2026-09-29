package com.pocketarcade.hub

import com.pocketarcade.engine.FIXED_DT
import com.pocketarcade.engine.r3d.Camera3D
import com.pocketarcade.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * First person in the hall: the eye and its look limits, head-bob, walking relative to the
 * view with the usual collision, and the split-screen controls (left walks, right looks).
 */
class FirstPersonTest {
    private companion object {
        const val W = 1080f
        const val H = 2400f
        const val DENSITY = 2.75f
        const val DEG = PI.toFloat() / 180f
    }

    private fun world(): HubWorld {
        val w = HubWorld(GameRegistry.createAll(), null)
        w.setViewport(W, H)
        w.density = DENSITY
        return w
    }

    private fun HubWorld.run(seconds: Float) {
        repeat((seconds / FIXED_DT).roundToInt()) { update(FIXED_DT) }
    }

    // ------------------------------------------------------------------ camera

    @Test
    fun theEyeSitsAtKidHeightOverThePlayerLookingWhereTheViewPoints() {
        val w = world()
        w.setFirstPerson(true, animate = false)
        w.camera.setLook(PI.toFloat() / 2f, -10f * DEG)
        w.run(0.1f)
        val cam = Camera3D()
        w.camera.apply(cam, W.toInt(), H.toInt())
        // Over the player, pulled back a little behind the feet (facing +x here).
        val back = w.camera.eyeBack
        assertTrue(back > 0f && back <= HubCamera.EYE_BACK)
        assertEquals(w.player.x - back, cam.ex, 1e-3f)
        assertEquals(HubCamera.EYE_HEIGHT, cam.ey, 1e-3f)
        assertEquals(w.player.y, cam.ez, 1e-3f)
        // Yaw π/2 faces +x; pitched 10° down.
        assertEquals(cos(10f * DEG), cam.fx, 1e-3f)
        assertEquals(-sin(10f * DEG), cam.fy, 1e-3f)
        assertEquals(0f, cam.fz, 1e-3f)
        assertEquals(HubCamera.FP_NEAR, cam.near, 0f)
        // A kid's eye: above the kids' heads (≈ 44), below the marquees.
        assertTrue(HubCamera.EYE_HEIGHT in 46f..62f)
    }

    @Test
    fun pitchIsClampedAndYawWrapsFreely() {
        val c = HubCamera()
        c.setLook(0f, 0f)
        c.look(0f, 2f)
        assertEquals(HubCamera.PITCH_LIMIT_DEG * DEG, c.pitch, 1e-5f)
        c.look(0f, -5f)
        assertEquals(-HubCamera.PITCH_LIMIT_DEG * DEG, c.pitch, 1e-5f)
        repeat(9) { c.look(1f, 0f) }
        assertTrue(c.yaw > -PI && c.yaw <= PI)
        assertEquals(HubCamera.wrap(9f), c.yaw, 1e-4f)
    }

    @Test
    fun theFieldOfViewStaysSensibleOnAnyScreen() {
        assertEquals(HubCamera.FP_FOV_DEG, HubCamera.fpFovY(16f / 9f) / DEG, 0.01f)
        assertEquals(HubCamera.FP_FOV_DEG, HubCamera.fpFovY(1f) / DEG, 0.01f)
        // A 20:9 portrait phone: wider vertically, capped, and still ~48° across.
        val tall = HubCamera.fpFovY(1080f / 2400f)
        assertTrue(tall / DEG in 80f..HubCamera.FP_MAX_FOV_DEG + 0.01f)
        val across = 2f * kotlin.math.atan(kotlin.math.tan(tall / 2f) * (1080f / 2400f)) / DEG
        assertTrue(across > 45f)
    }

    @Test
    fun headBobOnlyWhileWalkingAndExactlyZeroAtRest() {
        val w = world()
        w.setFirstPerson(true, animate = false)
        assertEquals(HubCamera.EYE_HEIGHT, w.camera.eyeY, 0f)
        // Walk forward and watch the eye move.
        w.pointerDown(1, 200f, 1800f)
        w.pointerMove(1, 200f, 1800f - w.joystick.radius)
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        repeat(120) {
            w.update(FIXED_DT)
            minY = minOf(minY, w.camera.eyeY)
            maxY = maxOf(maxY, w.camera.eyeY)
        }
        assertTrue("the head bobs while walking", maxY - minY > 0.5f)
        assertTrue("but only a little", maxY - minY < 3f)
        w.pointerUp(1, 200f, 1800f - w.joystick.radius)
        w.run(1f)
        assertEquals(0f, w.camera.bobWeight, 0f)
        assertEquals(HubCamera.EYE_HEIGHT, w.camera.eyeY, 0f)
        // Facing −z: the eye is straight behind the feet, no sway left.
        assertEquals(w.player.x, w.camera.eyeX, 1e-3f)
        assertEquals(w.player.y + w.camera.eyeBack, w.camera.eyeZ, 1e-3f)
    }

    @Test
    fun theEyeNeverEndsUpInsideAWallBehindYou() {
        val w = world()
        w.setFirstPerson(true, animate = false)
        // Stand against the west wall, facing east: there's no room behind for the eye.
        val x = HubLayout.WALL + Collision.FEET_HALF_W + 1f
        // Somewhere along it with open floor to the east, so turning round gives the eye room.
        val z = (150 until 1000 step 5).map { it.toFloat() }.first { z ->
            (0..40 step 2).all { d -> (-4..10 step 2).all { dz -> !Collision.blocked(w.map.solids, x + d, z + dz) } }
        }
        w.player.x = x
        w.player.y = z
        w.camera.setLook(PI.toFloat() / 2f, 0f)
        w.update(FIXED_DT)
        assertTrue("eye not pulled in at ($x, $z): ${w.camera.eyeBack}", w.camera.eyeBack < HubCamera.EYE_BACK)
        val ex = w.camera.eyeX
        val ez = w.camera.eyeZ
        assertFalse("eye inside a solid at ($ex, $ez)", w.map.solids.any { ex > it.left && ex < it.right && ez > it.top && ez < it.bottom })
        // Turn round to face the wall: the room behind comes back and the eye eases out again.
        w.camera.setLook(-PI.toFloat() / 2f, 0f)
        w.run(1f)
        assertTrue("eye never eased out at ($x, $z): ${w.camera.eyeBack}", w.camera.eyeBack > 0f)
    }

    @Test
    fun switchingEasesBetweenTheTwoPosesInHalfASecond() {
        val w = world()
        val cam = Camera3D()
        w.camera.apply(cam, W.toInt(), H.toInt())
        val overheadY = cam.ey
        assertTrue(overheadY > 200f)
        w.setFirstPerson(true, animate = true)
        w.run(0.25f)
        val mid = w.camera.fpAmount
        assertTrue(mid > 0.2f && mid < 0.8f)
        w.camera.apply(cam, W.toInt(), H.toInt())
        assertTrue(cam.ey < overheadY && cam.ey > HubCamera.EYE_HEIGHT)
        w.run(0.3f)
        assertEquals(1f, w.camera.fpAmount, 0f)
        w.camera.apply(cam, W.toInt(), H.toInt())
        assertEquals(HubCamera.EYE_HEIGHT, cam.ey, 1e-3f)
        // Entering first person looks the way the kid was facing.
        assertEquals(HubCamera.wrap(w.player.yaw), w.camera.yaw, 1e-4f)
        w.setFirstPerson(false, animate = true)
        w.run(0.6f)
        w.camera.apply(cam, W.toInt(), H.toInt())
        assertEquals(overheadY, cam.ey, 1f)
        assertEquals(HubCamera.OVERHEAD_NEAR, cam.near, 0f)
    }

    // ------------------------------------------------------------------ walking

    @Test
    fun theStickWalksRelativeToTheView() {
        val out = FloatArray(2)
        // Facing the back wall (−z): up walks to −z, right strafes to +x.
        HubCamera.moveRelative(0f, -1f, PI.toFloat(), out)
        assertEquals(0f, out[0], 1e-5f); assertEquals(-1f, out[1], 1e-5f)
        HubCamera.moveRelative(1f, 0f, PI.toFloat(), out)
        assertEquals(1f, out[0], 1e-5f); assertEquals(0f, out[1], 1e-5f)
        // Facing +x: up walks to +x, right strafes toward the entrance (+z), down backs off.
        HubCamera.moveRelative(0f, -1f, PI.toFloat() / 2f, out)
        assertEquals(1f, out[0], 1e-5f); assertEquals(0f, out[1], 1e-5f)
        HubCamera.moveRelative(1f, 0f, PI.toFloat() / 2f, out)
        assertEquals(0f, out[0], 1e-5f); assertEquals(1f, out[1], 1e-5f)
        HubCamera.moveRelative(0f, 1f, PI.toFloat() / 2f, out)
        assertEquals(-1f, out[0], 1e-5f)
        // Diagonals keep the stick's length (same speed as overhead).
        HubCamera.moveRelative(0.6f, -0.8f, 1.234f, out)
        assertEquals(1f, kotlin.math.hypot(out[0], out[1]), 1e-5f)
    }

    @Test
    fun walkingInFirstPersonGoesWhereYouLookAtTheUsualSpeed() {
        val w = world()
        w.setFirstPerson(true, animate = false)
        val yaw = 200f * DEG
        w.camera.setLook(yaw, 0f)
        val x0 = w.player.x
        val z0 = w.player.y
        w.pointerDown(1, 200f, 1800f)
        w.pointerMove(1, 200f, 1800f - w.joystick.radius * 2f)
        w.run(0.25f)
        val dx = w.player.x - x0
        val dz = w.player.y - z0
        assertEquals("heading", yaw, atan2(dx, dz) + 2f * PI.toFloat(), 0.02f)
        assertEquals("speed", Player.SPEED * 0.25f, kotlin.math.hypot(dx, dz), 1.5f)
        assertEquals(yaw, HubCamera.wrap(w.player.yaw) + 2f * PI.toFloat(), 1e-4f)
    }

    @Test
    fun walkingIntoAWallSlidesAlongIt() {
        // A wall along x = 100; walk diagonally into it (forward-left while facing −z).
        val wall = listOf(Box(80f, 0f, 100f, 400f))
        val p = Player()
        p.x = 110f
        p.y = 300f
        val out = FloatArray(2)
        HubCamera.moveRelative(-0.7f, -0.7f, PI.toFloat(), out)
        repeat(240) { p.update(FIXED_DT, out[0], out[1], wall, faceYaw = PI.toFloat()) }
        assertTrue("stopped at the wall", p.x >= 100f + Collision.FEET_HALF_W - 0.01f)
        assertTrue("slid along it", p.y < 300f - 60f)
        assertFalse(Collision.blocked(wall, p.x, p.y))
        assertEquals(PI.toFloat(), p.yaw, 0f)
    }

    // ------------------------------------------------------------------ controls

    @Test
    fun leftHalfWalksAndRightHalfLooksWithBothFingersAtOnce() {
        val w = world()
        w.setFirstPerson(true, animate = false)
        w.camera.setLook(PI.toFloat(), 0f)
        w.pointerDown(1, 200f, 1800f)
        w.pointerDown(2, 800f, 1200f)
        assertTrue(w.joystick.active)
        assertEquals(1L, w.joystick.pointerId)
        assertEquals(2L, w.lookPointer)
        // Both move in the same frame.
        w.pointerMove(1, 200f, 1800f - w.joystick.radius)
        w.pointerMove(2, 800f + 200f, 1200f)
        assertTrue(w.joystick.outY < -0.9f)
        // 200 px right at 2.75 px/dp and 0.3°/dp: a turn to the right (yaw goes down).
        val expected = 200f / DENSITY * HubWorld.LOOK_DEG_PER_DP * DEG
        assertEquals(PI.toFloat() - expected, w.camera.yaw + if (w.camera.yaw < 0f) 2f * PI.toFloat() else 0f, 1e-4f)
        // Dragging up looks up.
        w.pointerMove(2, 1000f, 1100f)
        assertTrue(w.camera.pitch > 0f)
        // Lifting the look finger leaves the stick alone, and vice versa.
        w.pointerUp(2, 1000f, 1100f)
        assertEquals(-1L, w.lookPointer)
        assertTrue(w.joystick.active)
        w.pointerUp(1, 200f, 1800f)
        assertFalse(w.joystick.active)
    }

    @Test
    fun aTapOnTheLookSideDoesNotTurnTheView() {
        val w = world()
        w.setFirstPerson(true, animate = false)
        w.camera.setLook(1f, 0.1f)
        val slop = HubWorld.TAP_SLOP_DP * DENSITY
        w.pointerDown(3, 900f, 1000f)
        w.pointerMove(3, 900f + slop * 0.6f, 1000f - slop * 0.5f)
        assertFalse(w.lookDragging)
        assertNull(w.pointerUp(3, 900f + slop * 0.6f, 1000f - slop * 0.5f))
        assertEquals(1f, w.camera.yaw, 0f)
        assertEquals(0.1f, w.camera.pitch, 0f)
        assertFalse(w.joystick.active)
        // Past the slop it turns, from where the finger first landed.
        w.pointerDown(4, 900f, 1000f)
        w.pointerMove(4, 900f - slop * 2f, 1000f)
        assertTrue(w.lookDragging)
        assertEquals(1f + slop * 2f / DENSITY * HubWorld.LOOK_DEG_PER_DP * DEG, w.camera.yaw, 1e-4f)
    }

    @Test
    fun overheadTheWholeScreenIsTheStick() {
        val w = world()
        w.pointerDown(1, 900f, 1500f)
        assertTrue(w.joystick.active)
        assertEquals(-1L, w.lookPointer)
    }

    @Test
    fun cancellingLetsGoOfEveryFinger() {
        val w = world()
        w.setFirstPerson(true, animate = false)
        w.pointerDown(1, 200f, 1800f)
        w.pointerDown(2, 800f, 1200f)
        w.pointerMove(2, 900f, 1200f)
        w.cancelInput()
        assertFalse(w.joystick.active)
        assertEquals(-1L, w.lookPointer)
        val yaw = w.camera.yaw
        // The same fingers keep moving: nothing happens until they land again.
        w.pointerMove(2, 1000f, 1200f)
        w.pointerMove(1, 200f, 1600f)
        assertEquals(yaw, w.camera.yaw, 0f)
        assertEquals(0f, w.joystick.outY, 0f)
        // Switching views mid-gesture lets go too.
        w.pointerDown(5, 800f, 1200f)
        w.setFirstPerson(false, animate = true)
        assertEquals(-1L, w.lookPointer)
    }

    @Test
    fun thePromptStillTakesTheTapInFirstPersonAndTheViewFacesTheMachineAfterwards() {
        val w = world()
        w.setFirstPerson(true, animate = false)
        val spot = w.map.spots.first { it.type == SpotType.MACHINE }
        w.player.x = spot.area.centerX
        w.player.y = spot.area.centerY
        w.update(FIXED_DT)
        assertSame(spot, w.activeSpot)
        // The renderer placed the bubble on the right half of the screen.
        w.bubbleLeft = 600f; w.bubbleTop = 500f; w.bubbleRight = 1000f; w.bubbleBottom = 800f
        w.pointerDown(7, 800f, 650f)
        assertEquals(-1L, w.lookPointer)
        assertSame(spot, w.pointerUp(7, 800f, 650f))
        // Look away, dive in, and come back out facing the machine.
        w.camera.setLook(0f, 0.3f)
        w.setDive(spot, 1f)
        val toMachine = atan2(spot.focusX - w.player.x, spot.focusZ - w.player.y)
        assertEquals(HubCamera.wrap(toMachine), w.camera.yaw, 1e-4f)
        assertTrue(w.camera.pitch <= 0f && w.camera.pitch >= -25f * DEG - 1e-4f)
        w.setDive(spot, 0.5f)
        w.setDive(null, 0f)
        assertEquals(0f, w.camera.dive, 0f)
        // Every counter and kiosk has a spot to stand at.
        assertNotNull(w.map.spots.firstOrNull { it.type == SpotType.PRIZES })
        assertNotNull(w.map.spots.firstOrNull { it.type == SpotType.TOKENS })
        assertTrue(abs(w.camera.eyeY - HubCamera.EYE_HEIGHT) < 1e-3f)
    }
}
