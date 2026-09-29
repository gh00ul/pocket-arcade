package com.pocketarcade.engine.r3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * The view-volume culling the hall uses for first person (eye level, looking along the floor)
 * as well as overhead: near things at the screen edges stay in, things behind the eye or past
 * the draw distance drop out, and the floor footprint covers what's in view.
 */
class FrustumTest {
    private companion object {
        const val W = 1080
        const val H = 2400
        const val EYE_X = 300f
        const val EYE_Y = 54f
        const val EYE_Z = 600f
        const val FAR = 900f
        val FOV = Math.toRadians(90.0).toFloat()
    }

    /** An eye-level camera at (300, 54, 600) looking north (−z), pitched by [pitchDeg]. */
    private fun eyeLevel(pitchDeg: Float = 0f, yawDeg: Float = 180f): Pair<Camera3D, Frustum> {
        val cam = Camera3D()
        val p = Math.toRadians(pitchDeg.toDouble()).toFloat()
        val y = Math.toRadians(yawDeg.toDouble()).toFloat()
        cam.near = 1.5f
        cam.lookAt(EYE_X, EYE_Y, EYE_Z, EYE_X + sin(y) * cos(p) * 100f, EYE_Y + sin(p) * 100f, EYE_Z + cos(y) * cos(p) * 100f, FOV, W, H)
        val f = Frustum()
        f.set(cam, FAR)
        return cam to f
    }

    /** The world point at view position (vx, vy, vz). */
    private fun world(cam: Camera3D, vx: Float, vy: Float, vz: Float) = floatArrayOf(
        cam.ex + cam.rx * vx + cam.ux * vy + cam.fx * vz,
        cam.ey + cam.ry * vx + cam.uy * vy + cam.fy * vz,
        cam.ez + cam.rz * vx + cam.uz * vy + cam.fz * vz,
    )

    private fun Frustum.pointBox(p: FloatArray, half: Float) =
        boxVisible(p[0] - half, p[1] - half, p[2] - half, p[0] + half, p[1] + half, p[2] + half)

    @Test
    fun nearThingsRightAtTheScreenEdgesAreKept() {
        val (cam, f) = eyeLevel()
        val out = FloatArray(3)
        // Just inside each edge, only 6 units in front of the eye: a cabinet you stand beside.
        val depth = 6f
        val halfW = cam.cx / cam.focal * depth
        val halfH = cam.cy / cam.focal * depth
        for (p in listOf(world(cam, -halfW * 0.97f, 0f, depth), world(cam, halfW * 0.97f, 0f, depth), world(cam, 0f, halfH * 0.97f, depth), world(cam, 0f, -halfH * 0.97f, depth))) {
            assertTrue(cam.project(p[0], p[1], p[2], out))
            assertTrue("projects on screen", out[0] in 0f..W.toFloat() && out[1] in 0f..H.toFloat())
            assertTrue("a point box at the edge is kept", f.pointBox(p, 0.1f))
            assertTrue("its light reaches the view", f.sphereVisible(p[0], p[1], p[2], 0.1f))
        }
        // A box whose middle is just off the left edge but which pokes into view is kept too.
        val off = world(cam, -halfW - 1f, 0f, depth)
        assertTrue(f.boxVisible(off[0] - 2f, off[1] - 2f, off[2] - 2f, off[0] + 2f, off[1] + 2f, off[2] + 2f))
    }

    @Test
    fun thingsBehindBesideOrBeyondTheDrawDistanceAreDropped() {
        val (_, f) = eyeLevel()
        // Behind the eye (the eye looks toward −z).
        assertFalse(f.boxVisible(290f, 0f, 610f, 310f, 170f, 640f))
        // Far off to the side, level with the eye: outside the ~48° wide view.
        assertFalse(f.boxVisible(0f, 0f, 560f, 20f, 170f, 580f))
        // Past the draw distance.
        assertFalse(f.boxVisible(290f, 0f, EYE_Z - FAR - 60f, 310f, 170f, EYE_Z - FAR - 20f))
        assertFalse(f.sphereVisible(EYE_X, 40f, EYE_Z - FAR - 50f, 20f))
        // Straddling the draw distance, or straight ahead: kept.
        assertTrue(f.boxVisible(290f, 0f, EYE_Z - FAR - 10f, 310f, 170f, EYE_Z - FAR + 10f))
        assertTrue(f.boxVisible(290f, 0f, 400f, 310f, 170f, 420f))
        // A big light behind the eye still lights the floor in front.
        assertTrue(f.sphereVisible(EYE_X, 150f, EYE_Z + 40f, 175f))
        assertFalse(f.sphereVisible(EYE_X, 150f, EYE_Z + 200f, 60f))
    }

    @Test
    fun theFloorFootprintOfAHorizonViewRunsFromTheEyeToTheDrawDistance() {
        val (cam, f) = eyeLevel()
        val rect = FloatArray(4)
        assertTrue(f.footprint(0f, 170f, rect))
        // Starts at the eye (nothing behind it) and reaches the draw distance ahead.
        assertEquals(EYE_Z, rect[3], 0.5f)
        assertEquals(EYE_Z - FAR, rect[2], 0.5f)
        assertTrue(rect[0] < EYE_X && rect[1] > EYE_X)
        // Every floor point the screen's corners and edges see lies inside it.
        val hit = FloatArray(2)
        for (sx in listOf(0f, W / 2f, W.toFloat())) for (sy in listOf(H * 0.6f, H * 0.8f, H.toFloat())) {
            if (!cam.rayToPlaneY(sx, sy, 0f, hit)) continue
            if (cam.viewZ(hit[0], 0f, hit[1]) > FAR) continue
            assertTrue("floor hit (${hit[0]}, ${hit[1]}) inside", hit[0] >= rect[0] - 0.5f && hit[0] <= rect[1] + 0.5f && hit[1] >= rect[2] - 0.5f && hit[1] <= rect[3] + 0.5f)
        }
    }

    @Test
    fun lookingUpOrAwayStillGivesASaneFootprint() {
        // Up at the rig: the floor near you is below the view, but tall things ahead are kept.
        val (_, up) = eyeLevel(pitchDeg = 40f)
        val rect = FloatArray(4)
        assertTrue(up.footprint(0f, 290f, rect))
        assertTrue(rect[2] < rect[3] && rect[0] < rect[1])
        assertFalse("a low box at your feet", up.boxVisible(295f, 0f, 560f, 305f, 1f, 570f))
        assertTrue("a cabinet top ahead", up.boxVisible(290f, 0f, 520f, 310f, 170f, 540f))
        // Toward the entrance and the street (+z).
        val (_, south) = eyeLevel(yawDeg = 0f)
        assertTrue(south.footprint(0f, 170f, rect))
        assertEquals(EYE_Z, rect[2], 0.5f)
        assertEquals(EYE_Z + FAR, rect[3], 0.5f)
        // Looking straight down from overhead: the footprint is the patch under the camera.
        val cam = Camera3D()
        cam.lookAt(300f, 640f, 1000f, 300f, 10f, 560f, Math.toRadians(56.0).toFloat(), W, H)
        val over = Frustum()
        over.set(cam, 3000f)
        assertTrue(over.footprint(0f, 170f, rect))
        val hit = FloatArray(2)
        for (sx in listOf(0f, W.toFloat())) for (sy in listOf(0f, H.toFloat())) {
            assertTrue(cam.rayToPlaneY(sx, sy, 0f, hit))
            assertTrue(hit[0] >= rect[0] - 0.5f && hit[0] <= rect[1] + 0.5f && hit[1] >= rect[2] - 0.5f && hit[1] <= rect[3] + 0.5f)
        }
    }

    @Test
    fun modelInstancesAreCulledByViewAndDrawDistance() {
        val tex = Texture(4, 4, IntArray(16) { -1 })
        val cube = ModelBuilder().box(-5f, 0f, -5f, 5f, 10f, 5f, BoxFaces.all(tex.full)).build()
        val r = Renderer3D(W, H)
        r.startFrame()
        r.camera.near = 1.5f
        r.camera.lookAt(EYE_X, EYE_Y, EYE_Z, EYE_X, EYE_Y, EYE_Z - 100f, FOV, W, H)
        r.cullModels = true
        r.drawDistance = FAR
        val xf = Xform()
        cube.draw(r, xf = xf.set(EYE_X, 0f, EYE_Z - 100f)) // ahead
        cube.draw(r, xf = xf.set(EYE_X, 0f, EYE_Z + 60f)) // behind
        cube.draw(r, xf = xf.set(EYE_X, 0f, EYE_Z - FAR - 40f)) // beyond the draw distance
        cube.draw(r, xf = xf.set(EYE_X - 400f, 40f, EYE_Z - 60f)) // off to the side
        cube.draw(r, xf = xf.set(EYE_X, 0f, EYE_Z - FAR - 40f, scale = 20f)) // huge, so it reaches back in
        assertEquals(3, r.modelsCulled)
        val p = r.finishFrame(0, 0, W, H)
        assertEquals(2, p.instanceCount)
        // The pass carries the camera's near plane to the GPU.
        assertEquals(1.5f, p.near, 0f)
    }

    @Test
    fun passesKeepTheOldNearPlaneByDefault() {
        val r = Renderer3D(64, 64)
        r.startFrame()
        r.camera.lookAt(0f, 0f, 0f, 0f, 0f, -1f, FOV, 64, 64)
        val p = r.finishFrame(0, 0, 64, 64)
        assertEquals(RenderPass.DEFAULT_NEAR, p.near, 0f)
        assertEquals(8f, RenderPass.DEFAULT_NEAR, 0f)
    }
}
