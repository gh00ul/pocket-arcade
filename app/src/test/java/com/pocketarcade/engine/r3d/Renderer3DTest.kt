package com.pocketarcade.engine.r3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Renderer3DTest {
    private fun solid(argb: Int) = Texture(4, 4, IntArray(16) { argb })

    private fun renderer(): Renderer3D {
        val r = Renderer3D(64, 64)
        r.startFrame()
        r.camera.lookAt(0f, 0f, 0f, 0f, 0f, -1f, (Math.PI / 2).toFloat(), 64, 64)
        r.clear(0xFF000000.toInt())
        return r
    }

    private fun wall(r: Renderer3D, z: Float, size: Float, tex: Texture, blend: Blend = Blend.OPAQUE, nz: Float = 1f) {
        r.quad(
            -size, size, z, size, size, z, size, -size, z, -size, -size, z,
            tex.full, 0f, 0f, nz, blend = blend,
        )
    }

    @Test
    fun recordsAFacingQuadAsTwoTriangles() {
        val r = renderer()
        wall(r, -10f, 2f, solid(0xFFFF0000.toInt()))
        val p = r.finishFrame(0, 0, 64, 64)
        assertEquals(6, p.vertCount)
        assertEquals(1, p.drawCount)
        assertEquals(RenderPass.KIND_BATCH, p.draws[0])
        assertEquals(Blend.OPAQUE.ordinal, p.draws[1])
    }

    @Test
    fun culledAndBehindTheEyePolygonsAreDropped() {
        val r = renderer()
        wall(r, -10f, 2f, solid(-1), nz = -1f)
        wall(r, 10f, 2f, solid(-1))
        val p = r.finishFrame(0, 0, 64, 64)
        assertEquals(0, p.vertCount)
    }

    @Test
    fun opaqueGeometryIsGroupedByTextureAndDrawnBeforeSeeThrough() {
        val r = renderer()
        val a = solid(0xFFFF0000.toInt())
        val b = solid(0xFF00FF00.toInt())
        wall(r, -30f, 2f, a, Blend.ALPHA)
        wall(r, -10f, 2f, a)
        wall(r, -12f, 2f, b)
        wall(r, -14f, 2f, a)
        val p = r.finishFrame(0, 0, 64, 64)
        // Two opaque batches (a, b) then the alpha batch, whatever order they were recorded in.
        assertEquals(3, p.drawCount)
        assertEquals(Blend.OPAQUE.ordinal, p.draws[1])
        assertEquals(12, p.draws[4])
        assertEquals(Blend.OPAQUE.ordinal, p.draws[5 + 1])
        assertEquals(Blend.ALPHA.ordinal, p.draws[10 + 1])
        assertEquals(24, p.vertCount)
    }

    @Test
    fun consecutiveSeeThroughPolygonsWithTheSameTextureMerge() {
        val r = renderer()
        val glow = solid(0x80FFFFFF.toInt())
        wall(r, -10f, 2f, glow, Blend.ADD)
        wall(r, -11f, 2f, glow, Blend.ADD)
        val p = r.finishFrame(0, 0, 64, 64)
        assertEquals(1, p.drawCount)
        assertEquals(12, p.draws[4])
    }

    @Test
    fun lightGridListsLightsOnlyWhereTheyReach() {
        val r = renderer()
        r.lighting.points += PointLight(0f, 10f, 0f, 1f, 1f, 1f, 50f)
        r.lighting.points += PointLight(500f, 10f, 0f, 1f, 1f, 1f, 50f)
        val p = r.finishFrame(0, 0, 64, 64)
        assertEquals(2, p.lightCount)
        assertTrue(p.gridW > 1)
        fun lightsAt(x: Float, z: Float): Set<Int> {
            val gx = ((x - p.gridX0) * p.gridInvCell).toInt()
            val gz = ((z - p.gridZ0) * p.gridInvCell).toInt()
            val base = (gz * p.gridW * 2 + gx * 2) * 4
            return (0 until 8).map { (p.grid[base + it].toInt() and 255) - 1 }.filter { it >= 0 }.toSet()
        }
        assertEquals(setOf(0), lightsAt(0f, 0f))
        assertEquals(setOf(1), lightsAt(500f, 0f))
        assertEquals(emptySet<Int>(), lightsAt(250f, 0f))
    }

    @Test
    fun projectsAndUnprojectsThroughTheCamera() {
        val cam = Camera3D()
        cam.lookAt(0f, 100f, 100f, 0f, 0f, 0f, (Math.PI / 3).toFloat(), 200, 200)
        val out = FloatArray(3)
        assertTrue(cam.project(10f, 0f, 5f, out))
        val hit = FloatArray(2)
        assertTrue(cam.rayToPlaneY(out[0], out[1], 0f, hit))
        assertEquals(10f, hit[0], 0.01f)
        assertEquals(5f, hit[1], 0.01f)
    }
}
