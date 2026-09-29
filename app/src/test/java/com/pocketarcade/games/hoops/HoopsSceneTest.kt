package com.pocketarcade.games.hoops

import org.junit.Assert.assertTrue
import org.junit.Test

/** The hoops scene's visual state is plain arithmetic: it must run headlessly and never need art. */
class HoopsSceneTest {
    @Test
    fun effectsAndTrailsRunWithoutAnyArt() {
        val scene = HoopsScene()
        scene.made(0.3f, swish = true, streak = 5)
        scene.rimHit(12f, 230f, -260f)
        scene.boardHit(0.3f, 14f, 250f)
        scene.floorHit(0f, -100f, 1f)
        for (i in 0 until 6) scene.trail(i, 0f, 120f, -80f)
        repeat(1200) { scene.step(1f / 120f) }
        scene.reset()
        repeat(10) { scene.step(1f / 120f) }
    }

    @Test
    fun boardStandsBetweenTheRimAndTheWall() {
        // The rim hangs clear of the glass, and the glass clear of the wall, so the mount and the rail fit.
        assertTrue(HoopsGeo.BOARD_Z > HoopsGeo.HOOP_Z + HoopsGeo.RIM_R)
        assertTrue(HoopsGeo.BACK_Z - HoopsGeo.BOARD_Z > 0.25f)
        assertTrue(HoopsGeo.WALL_Z == -HoopsGeo.BACK_Z * HoopsGeo.S)
    }

    @Test
    fun theWallDisplaysSitAboveTheBackboard() {
        val boardTop = HoopsGeo.BOARD_TOP * HoopsGeo.S + HoopsLook.FRAME_W
        assertTrue(HoopsLook.SIGN_Y - HoopsLook.SIGN_HH > boardTop + 20f)
        // The readouts share the sign's height and stay on the wall.
        assertTrue(HoopsLook.PANEL_X + HoopsLook.PANEL_HW < HoopsLook.WALL_HALF_W)
        assertTrue(HoopsLook.PANEL_X - HoopsLook.PANEL_HW > HoopsLook.SIGN_HW)
    }
}
