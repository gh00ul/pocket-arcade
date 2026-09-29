package com.pocketarcade.games.stacker

import com.pocketarcade.engine.r3d.Stage3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The stacker scene's visual state is plain arithmetic: it must run headlessly and never need art. */
class StackerSceneTest {
    @Test
    fun effectsRunWithoutAnyArt() {
        val scene = StackerScene()
        scene.perfect(0f, 200f, 0f, 100f, 100f, 11, 4)
        scene.land(0f, 218f, 0f, 90f, 90f)
        scene.cut(40f, 218f, 0f, 12)
        scene.miss(0f, 218f, 0f)
        scene.milestone(198f)
        repeat(600) { scene.step(1f / 120f, combo = 3) }
        scene.reset()
    }

    @Test
    fun heightMarkersSitLeftOfTheTowerOnScreen() {
        // The camera as StackerGame.aim places it at the start, and a marker's world spot.
        val stage = Stage3D(360, 640)
        stage.look(330f, 330f, 420f, 0f, -20f, 0f, fovDeg = 44f)
        val out = FloatArray(3)
        assertTrue(stage.toField(StackerLook.MARKER_X, 198f, StackerLook.MARKER_Z, out))
        // The tower fills roughly x 120..240; the marker's bar and number live clear of it, on the field.
        assertTrue("marker at ${out[0]}", out[0] in 40f..100f)
    }

    @Test
    fun slabBodiesStayUnderTheBloomThreshold() {
        for (level in 0 until 300) {
            val c = StackerArt.bodyColor(level)
            val max = maxOf(c shr 16 and 255, c shr 8 and 255, c and 255)
            // Body paint is at most half brightness, so the lamps can lift it without turning it white.
            assertTrue("level $level body $max", max <= 130)
            assertEquals(0xFF, c ushr 24)
        }
    }

    @Test
    fun hueWalksTheWheelOneStepPerLevel() {
        assertEquals(StackerArt.HUE_START, StackerArt.hueOf(0), 1e-3f)
        assertEquals((StackerArt.HUE_START + StackerArt.HUE_STEP) % 360f, StackerArt.hueOf(1), 1e-3f)
    }
}
