package com.pocketarcade.games.airhockey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The air hockey scene's visual state is plain arithmetic: it must run headlessly and never need art. */
class HockeySceneTest {
    @Test
    fun effectsAndTrailRunWithoutAnyArt() {
        val scene = HockeyScene()
        scene.malletHit(180f, 500f, 0.9f, byPlayer = true)
        scene.malletHit(150f, 120f, 0.4f, byPlayer = false)
        scene.wallHit(60f, 300f, 0.7f)
        scene.goal(byPlayer = true)
        scene.goal(byPlayer = false)
        repeat(600) {
            scene.trail(100f + it * 0.1f, 300f, 1f / 120f)
            scene.step(1f / 120f)
        }
        scene.clearTrail()
        scene.reset()
    }

    @Test
    fun theTableSitsOnTheFloorUnderTheCamera() {
        assertEquals(-HockeyGeo.TABLE_H, HockeyGeo.FLOOR_Y, 0f)
        // The goal slot fits between the corner arcs, and the mallet's grip plane is above the surface.
        assertTrue(HockeyGeo.GOAL_HALF < (HockeyGeo.RR - HockeyGeo.RL) / 2f - HockeyGeo.CORNER_R)
        assertTrue(HockeyGeo.MALLET_H > 0f)
        assertEquals((HockeyGeo.RT + HockeyGeo.RB) / 2f, HockeyGeo.CY, 0f)
    }

    @Test
    fun scoreboardFaceFitsItsHousing() {
        // The face is 188 wide and 58 tall inside a 208 by 74 housing (see HockeyScene.buildTable).
        assertTrue(HockeyLook.BOARD_HW * 2f < 208f)
        assertTrue(HockeyLook.BOARD_Y1 - HockeyLook.BOARD_Y0 < 74f)
        assertTrue(HockeyLook.BOARD_Y0 > 148f && HockeyLook.BOARD_Y1 < 222f)
    }
}
