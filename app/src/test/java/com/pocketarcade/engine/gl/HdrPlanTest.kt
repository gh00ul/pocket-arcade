package com.pocketarcade.engine.gl

import com.pocketarcade.engine.gl.GfxQuality.Tier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Choosing the picture from what the driver offers, and the fallbacks that keep a failing pipeline from showing. */
class HdrPlanTest {
    @Before
    @After
    fun clean() = HdrGuard.reset()

    private fun caps(version: String = "OpenGL ES 3.1 V@1", ext: String = "", maxSamples: Int = 4) =
        GlCaps(version, ext, maxSamples)

    private val FLOAT = "GL_OES_texture_float GL_EXT_color_buffer_float GL_EXT_texture_filter_anisotropic"
    private val HALF = "GL_EXT_color_buffer_half_float GL_OES_texture_half_float"

    private fun choose(
        c: GlCaps?,
        tier: Tier = Tier.AUTO,
        rungHdr: Boolean = true,
        samples: Int = 4,
        blocked: Boolean = false,
        msaaBlocked: Boolean = false,
    ) = HdrPlan.choose(c, tier, rungHdr, samples, blocked, msaaBlocked)

    // ------------------------------------------------------------------ capabilities

    @Test
    fun floatSupportIsReadFromTheExtensionList() {
        assertTrue(caps(ext = FLOAT).colorBufferFloat)
        assertTrue(caps(ext = FLOAT).floatMsaa)
        assertFalse(caps().floatTargets)
        // Whole-token matching: a longer name that merely starts the same is not the extension.
        assertFalse(caps(ext = "GL_EXT_color_buffer_float_fake").colorBufferFloat)
        val half = caps(ext = HALF)
        assertTrue(half.colorBufferHalfFloat)
        assertTrue(half.floatTargets)
        // Half float renders single-sampled only.
        assertFalse(half.floatMsaa)
    }

    @Test
    fun openGlEs32FoldsFloatTargetsIntoTheCore() {
        assertTrue(caps(version = "OpenGL ES 3.2 V@ 502.0").colorBufferFloat)
        assertTrue(caps(version = "OpenGL ES 3.2 Mesa 24.0").floatMsaa)
        assertTrue(caps(version = "OpenGL ES 4.0").colorBufferFloat)
        assertFalse(caps(version = "OpenGL ES 3.1 V@ 502.0").colorBufferFloat)
        assertFalse(caps(version = "OpenGL ES 3.0 V@ 502.0").colorBufferFloat)
        assertFalse(caps(version = "something unexpected").colorBufferFloat)
    }

    @Test
    fun floatMultisamplingNeedsMoreThanOneSample() {
        assertFalse(caps(ext = FLOAT, maxSamples = 0).floatMsaa)
        assertFalse(caps(ext = FLOAT, maxSamples = 1).floatMsaa)
        assertTrue(caps(ext = FLOAT, maxSamples = 2).floatMsaa)
    }

    // ------------------------------------------------------------------ the plan

    @Test
    fun aDeviceThatPassesEverythingGetsHdrWithMultisampling() {
        assertEquals(Pipeline.HDR_MSAA, choose(caps(ext = FLOAT)))
        assertEquals(Pipeline.HDR_MSAA, choose(caps(ext = FLOAT), Tier.QUALITY))
        assertEquals(Pipeline.HDR_MSAA, choose(caps(ext = FLOAT), Tier.AUTO, samples = 2))
    }

    @Test
    fun batteryAndTheLowerRungsKeepTheLdrPicture() {
        assertEquals(Pipeline.LDR, choose(caps(ext = FLOAT), Tier.BATTERY))
        assertEquals(Pipeline.LDR, choose(caps(ext = FLOAT), rungHdr = false))
        assertEquals(Pipeline.LDR, choose(caps(ext = FLOAT), Tier.BATTERY, rungHdr = false))
    }

    @Test
    fun aDeviceWithoutFloatTargetsKeepsTheLdrPicture() {
        assertEquals(Pipeline.LDR, choose(caps()))
        assertEquals(Pipeline.LDR, choose(null))
        assertEquals(Pipeline.LDR, choose(caps(ext = "GL_OES_texture_float"), samples = 0))
    }

    @Test
    fun whereMultisamplingIsWantedHdrNeedsFloatMultisampling() {
        // Half float alone can't multisample: better the anti-aliased LDR picture than jagged HDR.
        assertEquals(Pipeline.LDR, choose(caps(ext = HALF), samples = 4))
        // With no multisampling wanted, single-sampled half float will do.
        assertEquals(Pipeline.HDR, choose(caps(ext = HALF), samples = 0))
        assertEquals(Pipeline.HDR, choose(caps(ext = FLOAT), samples = 0))
        // A float multisampled framebuffer that failed to build rules HDR out where multisampling is wanted.
        assertEquals(Pipeline.LDR, choose(caps(ext = FLOAT), msaaBlocked = true))
        assertEquals(Pipeline.HDR, choose(caps(ext = FLOAT), samples = 0, msaaBlocked = true))
    }

    @Test
    fun aBlockedPipelineStaysBlocked() {
        assertEquals(Pipeline.LDR, choose(caps(ext = FLOAT), blocked = true))
        assertEquals(Pipeline.LDR, choose(caps(ext = FLOAT), Tier.QUALITY, samples = 0, blocked = true))
    }

    @Test
    fun theGuardRemembersWhyItBlockedAndOnlyResetsOnRequest() {
        assertFalse(HdrGuard.blocked)
        HdrGuard.blockMsaa("float msaa")
        assertTrue(HdrGuard.msaaBlocked)
        assertFalse(HdrGuard.blocked)
        assertEquals("float msaa", HdrGuard.reason)
        HdrGuard.block("shader")
        HdrGuard.block("later")
        assertTrue(HdrGuard.blocked)
        // The reason that ended it is the one kept for the log.
        assertEquals("shader", HdrGuard.reason)
        HdrGuard.reset()
        assertFalse(HdrGuard.blocked)
        assertFalse(HdrGuard.msaaBlocked)
        assertEquals("", HdrGuard.reason)
    }

    // ------------------------------------------------------------------ the restart policy's part

    @Test
    fun aFailureBlamedOnHdrRestartsWithoutUsingUpASlot() {
        val p = RestartPolicy()
        // Any number of free restarts changes nothing about the budget of ordinary ones.
        assertTrue(p.shouldRestart(0, blamed = true))
        assertTrue(p.shouldRestart(1, blamed = true))
        assertTrue(p.shouldRestart(1_000))
        assertTrue(p.shouldRestart(2_000))
        assertTrue(p.shouldRestart(3_000))
        assertFalse(p.shouldRestart(4_000))
        assertTrue(p.gaveUp)
    }

    @Test
    fun aFatalFailureIsNeverBlamedAway() {
        val p = RestartPolicy()
        assertFalse(p.shouldRestart(0, fatal = true, blamed = true))
        assertTrue(p.gaveUp)
        assertFalse(p.shouldRestart(10, blamed = true))
    }
}
