package com.pocketarcade.engine.r3d

import com.pocketarcade.engine.gl.Gfx
import com.pocketarcade.engine.gl.GfxQuality
import com.pocketarcade.engine.gl.NanoClock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The quality levers that live on the recording side: the light budget and the frame cap. */
class QualityLeversTest {
    @After
    fun restore() {
        GfxQuality.lightBudget = RenderPass.MAX_LIGHTS
        GfxQuality.displayHz = 60f
        GfxQuality.frameCap = 0
        Gfx.remove(GameViewport.SLOT)
    }

    private fun renderer(): Renderer3D {
        val r = Renderer3D(64, 64)
        r.startFrame()
        r.camera.lookAt(0f, 0f, 0f, 0f, 0f, -1f, (Math.PI / 2).toFloat(), 64, 64)
        r.clear(0xFF000000.toInt())
        return r
    }

    /** [n] lights along x; light i has radius 10 + i and intensity 1, so a higher index is stronger. */
    private fun addLights(r: Renderer3D, n: Int) {
        for (i in 0 until n) r.lighting.points += PointLight(i * 5f, 0f, -20f, 1f, 1f, 1f, 10f + i)
    }

    @Test
    fun aFullBudgetPacksTheFirstLightsAsItAlwaysDid() {
        val r = renderer()
        addLights(r, 70)
        val p = r.finishFrame(0, 0, 64, 64)
        assertEquals(RenderPass.MAX_LIGHTS, p.lightCount)
        assertEquals(0f, p.lights[0], 0f)
        assertEquals(63 * 5f, p.lights[63 * 8], 0f)
    }

    @Test
    fun aTighterBudgetKeepsTheStrongestLightsInTheirOrder() {
        GfxQuality.lightBudget = 16
        val r = renderer()
        addLights(r, 40)
        val p = r.finishFrame(0, 0, 64, 64)
        assertEquals(16, p.lightCount)
        // The 16 strongest are lights 24..39, packed in the rig's order.
        for (j in 0 until 16) {
            assertEquals("light $j", (24 + j) * 5f, p.lights[j * 8], 0f)
            assertEquals("radius $j", 10f + 24 + j, p.lights[j * 8 + 3], 0f)
        }
    }

    @Test
    fun aBudgetBiggerThanTheRigChangesNothing() {
        GfxQuality.lightBudget = 32
        val r = renderer()
        addLights(r, 20)
        val p = r.finishFrame(0, 0, 64, 64)
        assertEquals(20, p.lightCount)
        for (j in 0 until 20) assertEquals(j * 5f, p.lights[j * 8], 0f)
    }

    @Test
    fun theLightGridOnlyPointsAtPackedLights() {
        GfxQuality.lightBudget = 8
        val r = renderer()
        addLights(r, 30)
        val p = r.finishFrame(0, 0, 64, 64)
        assertEquals(8, p.lightCount)
        var seen = 0
        for (i in 0 until p.gridW * 2 * p.gridH * 4) {
            val id = p.grid[i].toInt() and 255
            if (id > 0) seen++
            assertTrue("grid entry $id past the ${p.lightCount} packed lights", id <= p.lightCount)
        }
        assertTrue("some lights reach the grid", seen > 0)
    }

    @Test
    fun aCappedRendererSkipsTheFramesBetween() {
        GfxQuality.displayHz = 120f
        GfxQuality.frameCap = 60
        var now = 1_000_000_000L
        val r = Renderer3D(64, 64)
        r.frameCapped = true
        r.clock = NanoClock { now }
        val period = 8_333_333L
        val kept = ArrayList<Boolean>()
        repeat(6) {
            r.startFrame()
            r.camera.lookAt(0f, 0f, 0f, 0f, 0f, -1f, (Math.PI / 2).toFloat(), 64, 64)
            r.clear(0xFF000000.toInt())
            val tex = Texture(4, 4, IntArray(16) { -1 })
            r.quad(-2f, 2f, -10f, 2f, 2f, -10f, 2f, -2f, -10f, -2f, -2f, -10f, tex.full, 0f, 0f, 1f)
            val p = r.finishFrame(0, 0, 64, 64)
            kept += !p.skipped
            // What recording produced: everything for a kept frame, nothing for a skipped one.
            assertEquals(if (p.skipped) 0 else 6, p.vertCount)
            p.recycle()
            now += period
        }
        assertEquals(listOf(true, false, true, false, true, false), kept)
    }

    @Test
    fun anUncappedRendererRecordsEveryFrame() {
        GfxQuality.displayHz = 120f
        GfxQuality.frameCap = 60
        var now = 1_000_000_000L
        val r = Renderer3D(64, 64)
        r.clock = NanoClock { now }
        repeat(6) {
            r.startFrame()
            r.camera.lookAt(0f, 0f, 0f, 0f, 0f, -1f, (Math.PI / 2).toFloat(), 64, 64)
            val p = r.finishFrame(0, 0, 64, 64)
            assertFalse(p.skipped)
            p.recycle()
            now += 8_333_333L
        }
    }

    @Test
    fun aDisplayAtTheCapIsNeverSkipped() {
        GfxQuality.displayHz = 60f
        GfxQuality.frameCap = 60
        var now = 1_000_000_000L
        val r = Renderer3D(64, 64)
        r.frameCapped = true
        r.clock = NanoClock { now }
        repeat(6) {
            r.startFrame()
            val p = r.finishFrame(0, 0, 64, 64)
            assertFalse(p.skipped)
            p.recycle()
            now += 16_666_666L
        }
    }

    @Test
    fun submittingASkippedPassDoesNotReplaceThePictureOnScreen() {
        val shown = Renderer3D(64, 64)
        shown.startFrame()
        val real = shown.finishFrame(0, 0, 64, 64)
        Gfx.submit(GameViewport.SLOT, real)
        GfxQuality.displayHz = 120f
        GfxQuality.frameCap = 60
        var now = 1_000_000_000L
        val capped = Renderer3D(64, 64)
        capped.frameCapped = true
        capped.clock = NanoClock { now }
        capped.startFrame() // taken
        capped.finishFrame(0, 0, 64, 64).recycle()
        now += 1_000_000L
        capped.startFrame() // skipped
        val skipped = capped.finishFrame(0, 0, 64, 64)
        assertTrue(skipped.skipped)
        Gfx.submit(GameViewport.SLOT, skipped)
        // The real pass is still the one waiting for the GL thread.
        val current = LinkedHashMap<String, RenderPass>()
        assertTrue(Gfx.take(current, 1))
        assertTrue(current[GameViewport.SLOT] === real)
        assertFalse(real.skipped)
    }
}
