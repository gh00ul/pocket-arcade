package com.pocketarcade.engine.gl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The HDR maths pinned: the tone map and its inverse, the scene encoding, the bloom threshold with
 * its knee, its caps, and the energy-conserving combine. The GLSL twins are built from the same
 * [HdrLook] constants; the last tests check the shaders were assembled as intended.
 */
class HdrMathTest {
    private fun near(expected: Float, actual: Float, eps: Float = 1e-4f, msg: String = "") =
        assertEquals(msg, expected, actual, eps)

    // ------------------------------------------------------------------ tone map

    @Test
    fun theToneMapFitsWhatTheLdrPipelineAlwaysUsed() {
        near(0f, HdrMath.aces(0f), 1e-3f)
        // The published anchors of the Narkowicz fit.
        near(0.80f, HdrMath.aces(1f), 5e-3f)
        near(0.886f, HdrMath.aces(1.6f), 5e-3f)
        assertEquals(1f, HdrMath.aces(50f), 0f)
        var prev = 0f
        for (i in 0..400) {
            val y = HdrMath.aces(i / 20f)
            assertTrue("monotonic at ${i / 20f}", y >= prev)
            prev = y
        }
    }

    @Test
    fun theInverseToneMapReturnsWhatTheToneMapMade() {
        for (i in 0..60) {
            val x = i / 12f // 0..5 of linear light
            val y = HdrMath.aces(x)
            if (y >= HdrLook.ACES_INV_MAX) continue
            near(x, HdrMath.inverseAces(y), 2e-3f * (1f + x * x), "x = $x")
        }
    }

    @Test
    fun theInverseIsClampedWhereTheCurveGoesFlat() {
        val top = HdrMath.inverseAces(1f)
        assertTrue(top.isFinite())
        assertEquals(top, HdrMath.inverseAces(0.999f), 0f)
        near(0f, HdrMath.inverseAces(0f), 1e-6f)
        near(0f, HdrMath.inverseAces(-3f), 1e-6f)
        // The ceiling stays a modest number of stops above white, so a display-white gradient or particle can't blow the bloom up.
        assertTrue("top $top", top < 8f)
    }

    // ------------------------------------------------------------------ scene encoding

    @Test
    fun theSceneEncodingIsBoundedAndReversible() {
        for (m in listOf(0f, 0.01f, 0.5f, 1f, 4f, 10f, 20f)) {
            val x = HdrMath.encode(m)
            assertTrue("encoded $m = $x", x in 0f..1f)
            near(m, HdrMath.decode(x), 1e-3f * (1f + m * m), "m = $m")
        }
        near(0.5f, HdrMath.encode(1f))
    }

    @Test
    fun overlappingGlowsCannotDecodeToMoreLightThanTheCap() {
        val cap = HdrLook.ENC_MAX / (1f - HdrLook.ENC_MAX)
        assertEquals(cap, HdrMath.decode(0.99f), 1e-3f)
        assertEquals(cap, HdrMath.decode(5f), 1e-3f)
        assertTrue(cap in 20f..30f)
    }

    // ------------------------------------------------------------------ soft cap

    @Test
    fun aSoftCapIsTheIdentityThenNeverExceedsItsCap() {
        for (x in listOf(0f, 0.3f, 1.2f)) near(x, HdrMath.softCap(x, 2.5f, 1.2f), 0f)
        var prev = -1f
        for (i in 0..2000) {
            val y = HdrMath.softCap(i / 100f, 2.5f, 1.2f)
            assertTrue("never above the cap at ${i / 100f}: $y", y <= 2.5f)
            assertTrue("monotonic at ${i / 100f}", y >= prev)
            prev = y
        }
        assertTrue(HdrMath.softCap(1000f, 2.5f, 1.2f) > 2.49f)
    }

    @Test
    fun aSoftCapHasNoKinkAtItsStart() {
        // Slope 1 on both sides of `start`: the ease begins gently instead of clipping.
        val h = 1e-3f
        val below = (HdrMath.softCap(1.2f, 2.5f, 1.2f) - HdrMath.softCap(1.2f - h, 2.5f, 1.2f)) / h
        val above = (HdrMath.softCap(1.2f + h, 2.5f, 1.2f) - HdrMath.softCap(1.2f, 2.5f, 1.2f)) / h
        near(1f, below, 1e-2f)
        near(1f, above, 2e-2f)
    }

    // ------------------------------------------------------------------ bloom threshold, knee, cap

    private val threshold = 1.0f

    @Test
    fun nothingGlowsWellBelowTheThreshold() {
        val start = threshold - HdrLook.BLOOM_KNEE
        for (br in listOf(0f, 0.1f, 0.3f, start - 0.001f)) near(0f, HdrMath.bloomEnergy(br, threshold), 0f, "br = $br")
    }

    @Test
    fun theKneeFadesGlowInInsteadOfPoppingItOn() {
        val k = HdrLook.BLOOM_KNEE
        var prev = 0f
        var step = 0f
        var br = threshold - k
        while (br <= threshold + k) {
            val e = HdrMath.bloomEnergy(br, threshold)
            assertTrue("monotonic at $br", e >= prev)
            // No jump between neighbouring brightnesses: the ramp is smooth.
            step = maxOf(step, e - prev)
            prev = e
            br += 0.01f
        }
        assertTrue("largest 0.01 step $step", step < 0.02f)
        // At the threshold itself a quarter of the knee passes (the quadratic's value there).
        near(k / 4f, HdrMath.bloomEnergy(threshold, threshold), 1e-4f)
        // Past the knee it is exactly the excess over the threshold.
        near(k + 0.7f, HdrMath.bloomEnergy(threshold + k + 0.7f, threshold), 1e-4f)
        near(2f, HdrMath.bloomEnergy(threshold + 2f, threshold), 1e-4f)
    }

    @Test
    fun aVeryBrightTexelFeedsTheChainNoMoreThanTheCap() {
        for (br in listOf(5f, 10f, 24f, 1000f)) {
            val e = HdrMath.bloomEnergy(br, threshold)
            assertTrue("energy at $br: $e", e <= HdrLook.BLOOM_INPUT_CAP)
        }
        assertTrue(HdrMath.bloomEnergy(24f, threshold) > HdrMath.bloomEnergy(6f, threshold))
    }

    @Test
    fun paleSurfacesUnderTheLitCeilingBarelyGlow() {
        // The trap this design exists for: a lit pale tile at the very top of the paint's ceiling.
        val ceiling = HdrLook.LIT_CEILING
        val tile = HdrMath.bloomEnergy(ceiling, threshold)
        assertTrue("a ceiling-lit tile feeds $tile", tile < 0.15f)
        // Well-lit paint under the ceiling feeds next to nothing.
        assertTrue(HdrMath.bloomEnergy(0.8f, threshold) < 0.03f)
        near(0f, HdrMath.bloomEnergy(0.55f, threshold), 0f)
        // A glowing sign three times that bright feeds the chain many times more.
        assertTrue(HdrMath.bloomEnergy(2.5f, threshold) > 8f * tile)
    }

    @Test
    fun litPaintNeverClimbsPastItsCeiling() {
        for (m in listOf(0f, 0.4f, HdrLook.LIT_START)) near(m, HdrMath.litLimit(m), 0f)
        for (i in 0..1000) assertTrue(HdrMath.litLimit(i / 20f) <= HdrLook.LIT_CEILING)
        // Its ceiling lies under the point where the bloom starts to take real energy.
        assertTrue(HdrLook.LIT_CEILING < threshold + HdrLook.BLOOM_KNEE / 2f)
    }

    // ------------------------------------------------------------------ Karis average, energy conservation

    @Test
    fun aHotTexelCannotDominateItsBlock() {
        val plain = (100f + 0.05f * 3f) / 4f
        val w = HdrMath.karisWeight(100f)
        val d = HdrMath.karisWeight(0.05f)
        val karis = (100f * w + 0.05f * d * 3f) / (w + 3f * d)
        assertTrue("plain $plain, karis $karis", karis < plain / 8f)
        // Thin neon is dimmed, but mildly: a 3.0 tube in a block of dark keeps most of its plain average.
        val wt = HdrMath.karisWeight(3f)
        val tube = (3f * wt + 0.05f * d * 3f) / (wt + 3f * d)
        val plainTube = (3f + 0.15f) / 4f
        assertTrue("tube $tube vs plain $plainTube", tube > plainTube * 0.55f)
    }

    @Test
    fun bloomAddedIsCappedAndHeldBackOnBrightPixels() {
        // In the dark surroundings it lands in full (below the cap's start)...
        near(0.8f, HdrMath.bloomAdded(0.8f, 0.02f), 0.01f)
        // ...but on a pixel that is already bright it is reduced, so cores and pale surfaces don't wash out.
        assertTrue(HdrMath.bloomAdded(0.8f, 1.5f) < 0.6f * HdrMath.bloomAdded(0.8f, 0f))
        // Nothing, however hot, adds more than the cap.
        for (b in listOf(3f, 10f, 200f)) assertTrue(HdrMath.bloomAdded(b, 0f) <= HdrLook.BLOOM_ADD_CAP)
        // And the more scene light there is, the less it adds.
        var prev = Float.MAX_VALUE
        for (s in listOf(0f, 0.25f, 0.5f, 1f, 2f, 4f)) {
            val a = HdrMath.bloomAdded(1f, s)
            assertTrue(a <= prev)
            prev = a
        }
    }

    // ------------------------------------------------------------------ the shaders are built from these numbers

    @Test
    fun theHdrShadersAreAssembledFromTheSharedSources() {
        val hdrSources = mapOf(
            "scene" to GlShaders.SCENE_FS_HDR,
            "background" to GlShaders.BG_HDR_FS,
            "particles" to GlShaders.PARTICLE_HDR_FS,
            "bright" to GlShaders.BRIGHT_HDR_FS,
            "glare" to GlShaders.GLARE_FS,
            "composite" to GlShaders.COMPOSITE_HDR_FS,
        )
        for ((name, src) in hdrSources) {
            assertTrue("$name starts with the version line", src.startsWith("#version 300 es"))
            assertFalse("$name has an unresolved template", src.contains("\${") || src.contains("\$HDR"))
        }
        // The scene shader is the LDR source with the HDR paths switched on, and nothing else.
        assertTrue(GlShaders.SCENE_FS_HDR.startsWith("#version 300 es\n#define HDR_OUT\n"))
        assertFalse(GlShaders.SCENE_FS.contains("#define HDR_OUT"))
        assertEquals(GlShaders.SCENE_FS.length + "#define HDR_OUT\n".length, GlShaders.SCENE_FS_HDR.length)
    }

    @Test
    fun theShadersCarryTheSameNumbersAsTheKotlinMirrors() {
        assertTrue(GlShaders.HDR_GLSL.contains("const float ENC_MAX = ${HdrLook.ENC_MAX};"))
        assertTrue(GlShaders.BRIGHT_HDR_FS.contains("max3(c) * ${HdrLook.KARIS_STRENGTH}"))
        assertTrue(GlShaders.BRIGHT_HDR_FS.contains("float knee = ${HdrLook.BLOOM_KNEE};"))
        assertTrue(GlShaders.BRIGHT_HDR_FS.contains("${HdrLook.BLOOM_INPUT_CAP}, ${HdrLook.BLOOM_INPUT_START}"))
        assertTrue(GlShaders.COMPOSITE_HDR_FS.contains("${HdrLook.BLOOM_ADD_CAP}, ${HdrLook.BLOOM_ADD_START}"))
        assertTrue(GlShaders.COMPOSITE_HDR_FS.contains("max3(c) * ${HdrLook.BLOOM_SELF_SHADOW}"))
        assertTrue(GlShaders.SCENE_FS_HDR.contains("const float EMISSIVE_GAIN = ${HdrLook.EMISSIVE_GAIN};"))
        assertTrue(GlShaders.SCENE_FS_HDR.contains("${HdrLook.LIT_CEILING}, ${HdrLook.LIT_START}"))
        assertTrue(GlShaders.GLARE_FS.contains("i <= ${HdrLook.GLARE_TAPS}"))
    }

    @Test
    fun theLdrShadersAreTheOnesThatAlwaysShipped() {
        // The HDR work touched none of the LDR-only sources.
        assertTrue(GlShaders.COMPOSITE_FS.contains("smoothstep(0.35, 0.85, length(d * vec2(1.0, 0.8)))"))
        assertTrue(GlShaders.BRIGHT_FS.contains("const float knee = 0.1;"))
        assertFalse(GlShaders.COMPOSITE_FS.contains("hdrDecode"))
        assertTrue(abs(HdrLook.ENC_MAX - 0.96f) < 1e-6f)
    }
}
