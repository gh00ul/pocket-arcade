package com.pocketarcade.hub

import com.pocketarcade.engine.r3d.Camera3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/** The hall camera's arrival from the title: pulled back at first, settling to exactly its resting pose. */
class CameraEntranceTest {
    private class Pose(val ex: Float, val ey: Float, val ez: Float, val focal: Float, val fx: Float, val fy: Float, val fz: Float)

    private fun pose(cam: HubCamera): Pose {
        val c = Camera3D()
        cam.apply(c, 1080, 2340)
        return Pose(c.ex, c.ey, c.ez, c.focal, c.fx, c.fy, c.fz)
    }

    private fun fresh(firstPerson: Boolean): HubCamera = HubCamera().also {
        it.snapTo(304f, 1045f)
        it.setFirstPerson(firstPerson, animate = false)
    }

    private fun dist(a: Pose, b: Pose): Float {
        val dx = a.ex - b.ex
        val dy = a.ey - b.ey
        val dz = a.ez - b.ez
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    @Test
    fun noEntranceChangesNothing() {
        for (fp in listOf(false, true)) {
            val cam = fresh(fp)
            val rest = pose(cam)
            cam.entrance = 1f
            cam.entrance = 0f
            val again = pose(cam)
            assertEquals(rest.ex, again.ex, 0f)
            assertEquals(rest.ey, again.ey, 0f)
            assertEquals(rest.ez, again.ez, 0f)
            assertEquals(rest.focal, again.focal, 0f)
        }
    }

    @Test
    fun theHallStartsPulledBackAlongItsLineOfSightAndWider() {
        for (fp in listOf(false, true)) {
            val cam = fresh(fp)
            val rest = pose(cam)
            cam.entrance = 1f
            val start = pose(cam)
            // Further back, not sideways: it still looks the same way.
            assertEquals(rest.fx, start.fx, if (fp) 0.05f else 0.01f)
            assertEquals(rest.fz, start.fz, if (fp) 0.05f else 0.01f)
            assertTrue("moved back ${dist(rest, start)} (first person $fp)", dist(rest, start) > if (fp) 10f else 40f)
            // A little wider: a shorter focal length for the same screen.
            assertTrue(start.focal < rest.focal)
        }
    }

    @Test
    fun itSettlesSmoothlyWithoutEverCrossingItsRestingPose() {
        for (fp in listOf(false, true)) {
            val cam = fresh(fp)
            val rest = pose(cam)
            var last = Float.MAX_VALUE
            for (i in 100 downTo 0) {
                cam.entrance = i / 100f
                val d = dist(rest, pose(cam))
                assertTrue("distance $d after $last at ${i / 100f}", d <= last + 1e-3f)
                last = d
            }
            assertEquals(0f, last, 1e-3f)
        }
    }

    @Test
    fun theFirstPersonEyeStaysInsideTheDoors() {
        // Spawn is 35 inside the front wall; pulled back at the start of the entrance the eye
        // must still be inside the hall, not out on the pavement.
        val cam = fresh(true)
        cam.setLook(Math.PI.toFloat(), HubCamera.REST_PITCH_DEG * Math.PI.toFloat() / 180f)
        cam.entrance = 1f
        val start = pose(cam)
        assertTrue("eye z ${start.ez}", start.ez < HubLayout.FRONT_WALL)
    }
}
