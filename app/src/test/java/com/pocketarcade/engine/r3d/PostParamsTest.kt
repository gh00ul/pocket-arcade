package com.pocketarcade.engine.r3d

import org.junit.Assert.assertEquals
import org.junit.Test

/** The post-processing parameters a scene sets reach the pass the GL thread draws. */
class PostParamsTest {
    private fun frame(configure: Renderer3D.() -> Unit = {}): RenderPass {
        val r = Renderer3D(64, 64)
        r.startFrame()
        r.camera.lookAt(0f, 0f, 0f, 0f, 0f, -1f, (Math.PI / 2).toFloat(), 64, 64)
        r.clear(0xFF000000.toInt())
        r.configure()
        return r.finishFrame(0, 0, 64, 64)
    }

    @Test
    fun theHdrBloomThresholdDefaultsAndIsCopiedToThePass() {
        assertEquals(Look.BLOOM_THRESHOLD_HDR, frame().bloomThresholdHdr, 0f)
        assertEquals(1.4f, frame { bloomThresholdHdr = 1.4f }.bloomThresholdHdr, 0f)
    }

    @Test
    fun theLdrThresholdIsUntouchedByTheHdrOne() {
        val p = frame { bloomThresholdHdr = 1.4f }
        assertEquals(Look.BLOOM_THRESHOLD, p.bloomThreshold, 0f)
    }

    @Test
    fun aRecycledPassGoesBackToTheDefaults() {
        val r = Renderer3D(64, 64)
        r.startFrame()
        r.clear(0xFF000000.toInt())
        r.bloomThresholdHdr = 1.4f
        val first = r.finishFrame(0, 0, 64, 64)
        assertEquals(1.4f, first.bloomThresholdHdr, 0f)
        first.recycle()
        // The renderer's next frame reuses that very pass, and its own setting (the default again) is what lands in it.
        r.bloomThresholdHdr = Look.BLOOM_THRESHOLD_HDR
        r.startFrame()
        r.clear(0xFF000000.toInt())
        val second = r.finishFrame(0, 0, 64, 64)
        assertEquals(Look.BLOOM_THRESHOLD_HDR, second.bloomThresholdHdr, 0f)
    }
}
