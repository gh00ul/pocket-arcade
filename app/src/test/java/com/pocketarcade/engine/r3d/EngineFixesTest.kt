package com.pocketarcade.engine.r3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** Pure-JVM checks for the renderer's math and texture hand-off. */
class EngineFixesTest {
    private fun normalized(x: Float, y: Float, z: Float): FloatArray {
        val l = sqrt(x * x + y * y + z * z)
        return floatArrayOf(x / l, y / l, z / l)
    }

    private fun assertVec(expected: FloatArray, actual: FloatArray, eps: Float = 1e-4f) {
        for (i in 0 until 3) assertEquals("component $i", expected[i], actual[i], eps)
    }

    @Test
    fun stretchedNormalsStayPerpendicularToTheSurface() {
        // The plane x + y = 1 has normal (1, 1, 0). Stretched 2× along x it becomes
        // x/2 + y = 1, whose normal is (1, 2, 0) — not the naively stretched (2, 1, 0).
        val xf = Xform().set().stretch(2f, 1f, 1f)
        val n = normalized(xf.normalX(1f, 1f, 0f), xf.normalY(1f, 1f, 0f), xf.normalZ(1f, 1f, 0f))
        assertVec(normalized(1f, 2f, 0f), n)
        // The transformed normal is perpendicular to a transformed in-plane direction (1, -1, 0).
        val tx = xf.dirX(1f, -1f, 0f)
        val ty = xf.dirY(1f, -1f, 0f)
        val tz = xf.dirZ(1f, -1f, 0f)
        assertEquals(0f, n[0] * tx + n[1] * ty + n[2] * tz, 1e-4f)
    }

    @Test
    fun rotatedAndMirroredNormalsKeepFacingOutwards() {
        // A quarter turn about y carries +z to +x.
        val turn = Xform().set(yaw = (Math.PI / 2).toFloat(), scale = 3f)
        assertVec(floatArrayOf(1f, 0f, 0f), normalized(turn.normalX(0f, 0f, 1f), turn.normalY(0f, 0f, 1f), turn.normalZ(0f, 0f, 1f)))
        // Mirroring x: a face pointing +x must now point -x (outwards), not back inwards.
        val mirror = Xform().set().stretch(-1f, 1f, 1f)
        assertVec(floatArrayOf(-1f, 0f, 0f), normalized(mirror.normalX(1f, 0f, 0f), mirror.normalY(1f, 0f, 0f), mirror.normalZ(1f, 0f, 0f)))
    }

    @Test
    fun addingAStretchedModelCorrectsItsNormals() {
        val tex = Texture(4, 4)
        val sphere = ModelBuilder().sphere(0f, 0f, 0f, 1f, tex.full, slices = 8, stacks = 6).build()
        val xf = Xform().set(5f, 0f, 0f).stretch(1f, 3f, 1f)
        val out = ModelBuilder().add(sphere, xf).build()
        // Every vertex normal of the ellipsoid x² + (y/3)² + z² = 1 is ∝ (x, y/9, z) locally.
        for (p in out.polys) {
            val vnx = p.vnx!!
            val vny = p.vny!!
            val vnz = p.vnz!!
            for (i in 0 until p.n) {
                val lx = p.xs[i] - 5f
                val ly = p.ys[i]
                val lz = p.zs[i]
                if (abs(lx) + abs(lz) < 1e-3f) continue // poles: the lathe's normal is exact there anyway
                val expect = normalized(lx, ly / 9f, lz)
                assertVec(expect, floatArrayOf(vnx[i], vny[i], vnz[i]), 0.02f)
            }
        }
    }

    @Test
    fun premultipliedUploadKeepsOpaqueTexelsAndBlackensOnlyByAlpha() {
        val src = intArrayOf(0xFF102030.toInt(), 0x00FFFFFF, 0x80FF8000.toInt())
        val dst = IntArray(3)
        Texture.premultiplyToRgba(src, dst, 3)
        // Opaque: plain ARGB → RGBA swizzle (bytes R, G, B, A little-endian).
        assertEquals(0xFF302010.toInt(), dst[0])
        // Fully transparent texels carry no colour at all.
        assertEquals(0, dst[1])
        // Half alpha: colour scaled by 128/255.
        val a = 0x80
        val r = (255 * a + 127) / 255
        val g = (0x80 * a + 127) / 255
        assertEquals((a shl 24) or (0 shl 16) or (g shl 8) or r, dst[2])
    }

    @Test
    fun changingTexturesReuseTheirSnapshotBuffers() {
        val t = Texture(8, 8)
        t.pixels.fill(0xFF112233.toInt())
        t.touch()
        t.prepare()
        val first = t.takeSnapshot()!!
        assertEquals(1, first.version)
        assertEquals(0xFF112233.toInt(), first.pixels[5])
        assertNull("taken once", t.takeSnapshot())
        // The GL thread hands the buffer back after the next upload; the next change reuses it.
        t.recycle(first)
        t.pixels.fill(0xFF445566.toInt())
        t.touch()
        t.prepare()
        val second = t.takeSnapshot()!!
        assertSame(first, second)
        assertEquals(2, second.version)
        assertEquals(0xFF445566.toInt(), second.pixels[63])
        // An unchanged texture takes no new snapshot.
        t.prepare()
        assertNull(t.takeSnapshot())
    }

    @Test
    fun aSnapshotTheGlThreadSkippedIsRecycledNotLeaked() {
        val t = Texture(4, 4)
        t.touch(); t.prepare()
        t.touch(); t.prepare() // replaces the untaken first snapshot, which becomes a spare
        val newest = t.takeSnapshot()!!
        assertEquals(2, newest.version)
        t.touch(); t.prepare()
        val third = t.takeSnapshot()!!
        assertNotSame(newest, third)
        assertEquals(3, third.version)
    }

    @Test
    fun framesOfOnlyModelsStillGetFog() {
        val r = Renderer3D(64, 64)
        r.startFrame()
        r.camera.lookAt(0f, 0f, 0f, 0f, 0f, -1f, (Math.PI / 2).toFloat(), 64, 64)
        r.fogNear = 100f
        r.fogFar = 400f
        r.fogFloor = 0.3f
        val tex = Texture(4, 4)
        val box = ModelBuilder().box(-1f, -1f, -1f, 1f, 1f, 1f, BoxFaces.all(tex.full)).build()
        box.draw(r, xf = Xform().set(0f, 0f, -20f))
        val p = r.finishFrame(0, 0, 64, 64)
        assertEquals(100f, p.fogNear, 0f)
        assertEquals(400f, p.fogFar, 0f)
        assertEquals(0.3f, p.fogFloor, 0f)
        assertTrue(p.drawCount > 0)
    }

    @Test
    fun recordingTimeIsStampedOnThePass() {
        val r = Renderer3D(16, 16)
        r.startFrame()
        val p = r.finishFrame(0, 0, 16, 16)
        assertTrue(p.recordNs >= 0L)
    }
}
