package com.pocketarcade.engine.r3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Renderer3DTest {
    private fun solid(argb: Int) = Texture(4, 4, IntArray(16) { argb })

    private fun renderer(): Renderer3D {
        val r = Renderer3D(64, 64)
        r.camera.lookAt(0f, 0f, 0f, 0f, 0f, -1f, (Math.PI / 2).toFloat(), 64, 64)
        r.lighting.ambR = 1f; r.lighting.ambG = 1f; r.lighting.ambB = 1f
        r.clear(0xFF000000.toInt())
        return r
    }

    private fun wall(r: Renderer3D, z: Float, size: Float, tex: Texture) {
        r.quad(
            -size, size, z, size, size, z, size, -size, z, -size, -size, z,
            tex.full, 0f, 0f, 1f,
        )
    }

    @Test
    fun drawsAFacingQuadAndLeavesTheRestClear() {
        val r = renderer()
        wall(r, -10f, 2f, solid(0xFFFF0000.toInt()))
        assertEquals(0xFFFF0000.toInt(), r.color[32 * 64 + 32])
        assertEquals(0xFF000000.toInt(), r.color[1 * 64 + 1])
    }

    @Test
    fun nearerSurfaceWinsWhateverTheDrawOrder() {
        val r = renderer()
        wall(r, -10f, 3f, solid(0xFF00FF00.toInt()))
        wall(r, -20f, 6f, solid(0xFF0000FF.toInt()))
        assertEquals(0xFF00FF00.toInt(), r.color[32 * 64 + 32])
    }

    @Test
    fun culledWhenFacingAway() {
        val r = renderer()
        r.quad(-2f, 2f, -10f, 2f, 2f, -10f, 2f, -2f, -10f, -2f, -2f, -10f, solid(-1).full, 0f, 0f, -1f)
        assertEquals(0xFF000000.toInt(), r.color[32 * 64 + 32])
    }

    @Test
    fun clipsGeometryThatCrossesTheNearPlane() {
        val r = Renderer3D(64, 64)
        r.camera.lookAt(0f, 10f, 0f, 0f, 0f, -30f, 1.2f, 64, 64)
        r.lighting.ambR = 1f; r.lighting.ambG = 1f; r.lighting.ambB = 1f
        r.clear(0xFF000000.toInt())
        // A floor running from well behind the camera to far in front.
        r.quad(-50f, 0f, -200f, 50f, 0f, -200f, 50f, 0f, 50f, -50f, 0f, 50f, solid(0xFFFFFFFF.toInt()).full, 0f, 1f, 0f)
        assertEquals(0xFFFFFFFF.toInt(), r.color[63 * 64 + 32])
    }

    @Test
    fun projectsAndUnprojectsConsistently() {
        val cam = Camera3D()
        cam.lookAt(0f, 300f, 300f, 0f, 0f, 0f, 0.7f, 360, 800)
        val p = FloatArray(3)
        assertTrue(cam.project(40f, 0f, -25f, p))
        val hit = FloatArray(2)
        assertTrue(cam.rayToPlaneY(p[0], p[1], 0f, hit))
        assertEquals(40f, hit[0], 0.05f)
        assertEquals(-25f, hit[1], 0.05f)
    }

    @Test
    fun fillRateBenchmark() {
        val r = Renderer3D(360, 800)
        r.camera.lookAt(0f, 0f, 0f, 0f, 0f, -1f, 1.0f, 360, 800)
        r.lighting.ambR = 0.8f
        val tex = Texture(32, 32, IntArray(32 * 32) { if (it % 3 == 0) 0xFF884422.toInt() else 0xFF2244AA.toInt() })
        val frames = 30
        val start = System.nanoTime()
        repeat(frames) {
            r.clear(0xFF101010.toInt())
            for (k in 0 until 3) wall(r, -2f - k, 1.2f + k, tex)
        }
        val ms = (System.nanoTime() - start) / 1e6 / frames
        println("fill: %.2f ms per frame for ~3 full-screen layers at 360x800".format(ms))
        assertTrue(ms < 200)
    }
}
