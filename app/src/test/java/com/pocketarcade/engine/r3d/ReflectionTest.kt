package com.pocketarcade.engine.r3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** Pure-JVM checks for the reflection maths, the built-in room and the floor-mirror bookkeeping. */
class ReflectionTest {
    private fun lum(c: FloatArray) = 0.3f * c[0] + 0.5f * c[1] + 0.2f * c[2]

    @Test
    fun reflectMirrorsAboutTheNormalAndKeepsLength() {
        val out = FloatArray(3)
        // Looking down at 45° onto the floor bounces up at 45°, still going forward.
        val k = sqrt(0.5f)
        EnvMap.reflect(0f, -k, -k, 0f, 1f, 0f, out)
        assertEquals(0f, out[0], 1e-6f)
        assertEquals(k, out[1], 1e-6f)
        assertEquals(-k, out[2], 1e-6f)
        // Any direction: same length, and incident + reflected is parallel to the normal.
        val n = floatArrayOf(0.48f, 0.6f, 0.64f)
        EnvMap.reflect(0.3f, -0.9f, 0.1f, n[0], n[1], n[2], out)
        val lin = sqrt(0.3f * 0.3f + 0.81f + 0.01f)
        val lout = sqrt(out[0] * out[0] + out[1] * out[1] + out[2] * out[2])
        assertEquals(lin, lout, 1e-5f)
        val sx = 0.3f - out[0]
        val sy = -0.9f - out[1]
        val sz = 0.1f - out[2]
        // (i - r) is a multiple of n: its cross product with n vanishes.
        assertEquals(0f, sy * n[2] - sz * n[1], 1e-5f)
        assertEquals(0f, sz * n[0] - sx * n[2], 1e-5f)
        assertEquals(0f, sx * n[1] - sy * n[0], 1e-5f)
    }

    @Test
    fun cubeFaceLookupInvertsFaceDirections() {
        val d = FloatArray(3)
        val st = FloatArray(2)
        for (face in 0 until 6) {
            for (s in floatArrayOf(-0.9f, -0.3f, 0f, 0.45f, 0.95f)) for (t in floatArrayOf(-0.8f, 0.1f, 0.7f)) {
                EnvMap.faceDir(face, s, t, d)
                // Any positive scale of the direction hits the same texel.
                val f = EnvMap.dirToFace(d[0] * 3f, d[1] * 3f, d[2] * 3f, st)
                assertEquals("face", face, f)
                assertEquals("s on face $face", s, st[0], 1e-5f)
                assertEquals("t on face $face", t, st[1], 1e-5f)
            }
        }
        // The axes land in the middle of their own faces.
        assertEquals(2, EnvMap.dirToFace(0f, 1f, 0f, st))
        assertEquals(5, EnvMap.dirToFace(0f, 0f, -1f, st))
        assertEquals(0f, st[0], 1e-6f)
    }

    @Test
    fun theRoomIsDarkOverheadWithBrightWindowsTowardTheEntrance() {
        val c = FloatArray(3)
        // Between the ceiling panels, straight up, it is nearly black.
        EnvMap.radiance(0.35f, 1f, 0.9f, c)
        val ceiling = lum(c)
        assertTrue("ceiling $ceiling", ceiling < 0.1f)
        // The entrance (+z, just above the horizon) is the brightest thing around.
        EnvMap.radiance(0.1f, 0.2f, 1f, c)
        val window = lum(c)
        assertTrue("window $window", window > 0.8f)
        // Toward the back wall at the same height it's much darker.
        EnvMap.radiance(0f, 0.2f, -1f, c)
        assertTrue(lum(c) < window * 0.5f)
        // Everything stays within what the 8-bit faces can hold, and never goes negative.
        val d = FloatArray(3)
        for (face in 0 until 6) for (i in 0 until 16) for (j in 0 until 16) {
            EnvMap.faceDir(face, i / 7.5f - 1f, j / 7.5f - 1f, d)
            EnvMap.radiance(d[0], d[1], d[2], c)
            for (k in 0 until 3) assertTrue(c[k] >= 0f && c[k] <= EnvMap.RANGE)
        }
    }

    @Test
    fun encodedRadianceRoundTripsWithFinePrecisionInTheDarks() {
        for (v in floatArrayOf(0f, 0.01f, 0.05f, 0.3f, 1f, 2.5f, 4f)) {
            val px = EnvMap.encode(v, v, v)
            assertEquals(255, px ushr 24)
            val back = EnvMap.decode(px and 255)
            // Square-root storage: the step near black is far below 1/255 of the range.
            val tol = if (v < 0.1f) 0.004f else v * 0.03f
            assertEquals("value $v", v, back, tol)
        }
        val face = EnvMap.buildFace(2, 8)
        assertEquals(64, face.size)
        assertTrue(face.all { it ushr 24 == 255 })
    }

    @Test
    fun cubeFacesMeetWithoutSeams() {
        // Texels either side of the +Y / +Z edge look along nearly the same direction, so the
        // painted room must agree there (no discontinuity in the reflections).
        val n = 32
        val top = EnvMap.buildFace(2, n)
        val front = EnvMap.buildFace(4, n)
        var worst = 0
        for (i in 0 until n) {
            // +Y's last row (t → +1, toward +z) meets +Z's first row (t → -1, looking up).
            val a = top[(n - 1) * n + i]
            val b = front[i]
            for (sh in intArrayOf(0, 8, 16)) worst = maxOf(worst, abs((a shr sh and 255) - (b shr sh and 255)))
        }
        assertTrue("seam difference $worst", worst < 40)
    }

    private fun solid(argb: Int) = Texture(4, 4, IntArray(16) { argb })

    private fun renderer(): Renderer3D {
        val r = Renderer3D(64, 64)
        r.startFrame()
        r.camera.lookAt(0f, 0f, 0f, 0f, 0f, -1f, (Math.PI / 2).toFloat(), 64, 64)
        return r
    }

    @Test
    fun glowingDrawsAreFlaggedForTheFloorMirror() {
        val r = renderer()
        val lit = solid(-1)
        val neon = solid(0xFFFF00FF.toInt())
        r.quad(-2f, 2f, -10f, 2f, 2f, -10f, 2f, -2f, -10f, -2f, -2f, -10f, lit.full, 0f, 0f, 1f)
        r.quad(-2f, 2f, -12f, 2f, 2f, -12f, 2f, -2f, -12f, -2f, -2f, -12f, neon.full, 0f, 0f, 1f, emissive = 1.5f)
        r.quad(-2f, 2f, -14f, 2f, 2f, -14f, 2f, -2f, -14f, -2f, -2f, -14f, lit.full, 0f, 0f, 1f, blend = Blend.ADD)
        r.quad(-2f, 2f, -15f, 2f, 2f, -15f, 2f, -2f, -15f, -2f, -2f, -15f, neon.full, 0f, 0f, 1f, blend = Blend.ADD, emissive = 1f)
        val glowModel = ModelBuilder().quad(-1f, 1f, -8f, 1f, 1f, -8f, 1f, -1f, -8f, -1f, -1f, -8f, neon.full, 0f, 0f, 1f, emissive = 2f).build()
        val plainModel = ModelBuilder().quad(-1f, 1f, -8f, 1f, 1f, -8f, 1f, -1f, -8f, -1f, -1f, -8f, lit.full, 0f, 0f, 1f).build()
        glowModel.draw(r)
        plainModel.draw(r)
        glowModel.draw(r, emissiveBoost = 0f) // switched off: nothing to mirror
        r.floorReflect = 1.2f
        val p = r.finishFrame(0, 0, 64, 64)
        // Opaque: lit batch, neon batch, then the three model instances; then the two ADD batches
        // (different textures, so not merged).
        assertEquals(7, p.drawCount)
        val glow = BooleanArray(p.drawCount) { p.drawGlow[it] }
        assertEquals(listOf(false, true, true, false, false, false, true), glow.toList())
        assertEquals(3, p.glowDraws)
        assertEquals(1.2f, p.floorReflect, 0f)
        assertEquals(0f, p.floorReflectMatte, 0f)
        assertEquals(Look.ENV_REFLECT, p.envReflect, 0f)
    }

    @Test
    fun reflectionSettingsResetWithEachPass() {
        val r = renderer()
        r.floorReflect = 0.8f
        r.floorReflectMatte = 0.1f
        r.envReflect = 0.5f
        val p = r.finishFrame(0, 0, 64, 64)
        assertEquals(0.1f, p.floorReflectMatte, 0f)
        assertEquals(0.5f, p.envReflect, 0f)
        p.recycle()
        val fresh = Renderer3D(8, 8)
        fresh.startFrame()
        val q = fresh.finishFrame(0, 0, 8, 8)
        assertEquals(0f, q.floorReflect, 0f)
        assertEquals(0, q.glowDraws)
        assertFalse(Look.ENV_REFLECT == 0f)
    }
}
