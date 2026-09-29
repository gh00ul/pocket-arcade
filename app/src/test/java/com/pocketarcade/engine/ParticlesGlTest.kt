package com.pocketarcade.engine

import com.pocketarcade.engine.gl.Gfx
import com.pocketarcade.engine.gl.GfxQuality
import com.pocketarcade.engine.gl.NanoClock
import com.pocketarcade.engine.r3d.GameViewport
import com.pocketarcade.engine.r3d.RenderPass
import com.pocketarcade.engine.r3d.Stage3D
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

/** Particles recorded into the GPU pass so they glow in the bloom (the 2D path is the fallback). */
class ParticlesGlTest {
    @After
    fun restore() {
        GameViewport.particles = null
        GameViewport.particlesInGl = false
        GfxQuality.displayHz = 60f
        GfxQuality.frameCap = 0
        GfxQuality.glParticles = true
        Gfx.remove(GameViewport.SLOT)
    }

    private val stride = RenderPass.PARTICLE_STRIDE

    private fun pass() = RenderPass(ConcurrentLinkedQueue())

    /** Min and max of vertex component [comp] (0 = x, 1 = y) over vertices [from] until [to]. */
    private fun extent(p: RenderPass, from: Int, to: Int, comp: Int): FloatArray {
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (v in from until to) {
            val f = p.particleVerts[v * stride + comp]
            lo = minOf(lo, f)
            hi = maxOf(hi, f)
        }
        return floatArrayOf(lo, hi)
    }

    @Test
    fun aSquareIsAGlowUnderACoreCentredOnItsFieldPosition() {
        val ps = Particles(8)
        ps.spawn(180f, 320f, 0f, 0f, 1f, 4f, Pal.PINK)
        val p = pass()
        ps.recordGl(p, 360f, 640f)
        // One quad for the glow, one for the particle, six vertices each.
        assertEquals(12, p.particleVertCount)
        assertEquals(6, p.particleHaloCount)
        // Field (180, 320) is the middle of the 360 x 640 field: clip space (0, 0).
        // A full-life square is 4 units wide: half of it is 2 units = 2 * 2/360 in clip space.
        val coreX = extent(p, 6, 12, 0)
        val coreY = extent(p, 6, 12, 1)
        assertEquals(-2f * 2f / 360f, coreX[0], 1e-5f)
        assertEquals(2f * 2f / 360f, coreX[1], 1e-5f)
        assertEquals(-2f * 2f / 640f, coreY[0], 1e-5f)
        assertEquals(2f * 2f / 640f, coreY[1], 1e-5f)
        // The glow reaches 1.3 sizes out on each side.
        val glowX = extent(p, 0, 6, 0)
        assertEquals(4f * Particles.GLOW_SIZE * 2f / 360f, glowX[1], 1e-5f)
        // The particle keeps its colour and full opacity; the glow is a fraction of it.
        assertEquals(1f, p.particleVerts[6 * stride + 4], 1e-6f)
        assertEquals(0x3F / 255f, p.particleVerts[6 * stride + 5], 1e-6f)
        assertEquals(0xA4 / 255f, p.particleVerts[6 * stride + 6], 1e-6f)
        assertEquals(1f, p.particleVerts[6 * stride + 7], 1e-6f)
        assertEquals(Particles.GLOW_SQUARE, p.particleVerts[7], 1e-6f)
    }

    @Test
    fun fieldCornersMapToClipSpaceCornersWithYPointingUp() {
        val ps = Particles(8)
        ps.spawn(0f, 0f, 0f, 0f, 1f, 2f, Pal.WHITE)
        ps.spawn(360f, 640f, 0f, 0f, 1f, 2f, Pal.WHITE)
        val p = pass()
        ps.recordGl(p, 360f, 640f)
        // Cores come after the two glows: (top-left) then (bottom-right), 6 vertices each.
        val first = 12
        val cx0 = (extent(p, first, first + 6, 0).let { it[0] + it[1] }) / 2f
        val cy0 = (extent(p, first, first + 6, 1).let { it[0] + it[1] }) / 2f
        val cx1 = (extent(p, first + 6, first + 12, 0).let { it[0] + it[1] }) / 2f
        val cy1 = (extent(p, first + 6, first + 12, 1).let { it[0] + it[1] }) / 2f
        assertEquals(-1f, cx0, 1e-5f)
        assertEquals(1f, cy0, 1e-5f)
        assertEquals(1f, cx1, 1e-5f)
        assertEquals(-1f, cy1, 1e-5f)
    }

    @Test
    fun aSparkleIsTwoCrossingBarsWithAGlow() {
        val ps = Particles(8)
        ps.spawn(180f, 320f, 0f, 0f, 1f, 6f, Pal.GOLD, kind = Particles.SPARKLE)
        val p = pass()
        ps.recordGl(p, 360f, 640f)
        assertEquals(6 + 12, p.particleVertCount)
        assertEquals(6, p.particleHaloCount)
        val barAx = extent(p, 6, 12, 0)
        val barAy = extent(p, 6, 12, 1)
        val barBx = extent(p, 12, 18, 0)
        val barBy = extent(p, 12, 18, 1)
        // Clip units back to field units: 360 x 640 units span 2 clip units each way.
        val aw = (barAx[1] - barAx[0]) * 360f / 2f
        val ah = (barAy[1] - barAy[0]) * 640f / 2f
        val bw = (barBx[1] - barBx[0]) * 360f / 2f
        val bh = (barBy[1] - barBy[0]) * 640f / 2f
        // One bar is long and thin one way, the other the other way; both are centred on the spot.
        assertTrue("bar A ${aw}x$ah", aw > ah * 2f)
        assertTrue("bar B ${bw}x$bh", bh > bw * 2f)
        assertEquals(aw, bh, 1e-3f)
        assertEquals(ah, bw, 1e-3f)
        assertEquals(0f, barAx[0] + barAx[1], 1e-5f)
        assertEquals(0f, barAy[0] + barAy[1], 1e-5f)
        assertEquals(0f, barBx[0] + barBx[1], 1e-5f)
        assertEquals(0f, barBy[0] + barBy[1], 1e-5f)
    }

    @Test
    fun confettiHasNoGlowOfItsOwn() {
        val ps = Particles(8)
        ps.spawn(100f, 100f, 0f, 0f, 1f, 5f, Pal.CYAN, kind = Particles.CONFETTI)
        val p = pass()
        ps.recordGl(p, 360f, 640f)
        assertEquals(6, p.particleVertCount)
        assertEquals(0, p.particleHaloCount)
    }

    @Test
    fun aDarkParticleGetsNoGlow() {
        val ps = Particles(8)
        ps.spawn(100f, 100f, 0f, 0f, 1f, 5f, 0xFF101010.toInt())
        val p = pass()
        ps.recordGl(p, 360f, 640f)
        assertEquals(6, p.particleVertCount)
        assertEquals(0, p.particleHaloCount)
    }

    @Test
    fun particlesFadeOutOverTheirLastThirtyPercent() {
        val ps = Particles(8)
        ps.spawn(100f, 100f, 0f, 0f, 1f, 4f, Pal.WHITE)
        ps.update(0.85f) // 15% of its life left: half opaque
        val p = pass()
        ps.recordGl(p, 360f, 640f)
        assertEquals(0.5f, p.particleVerts[p.particleHaloCount * stride + 7], 1e-4f)
        // Its glow fades with it.
        assertEquals(0.5f * Particles.GLOW_SQUARE, p.particleVerts[7], 1e-4f)
    }

    @Test
    fun aFullPoolFitsAndAResetPassStartsEmpty() {
        val ps = Particles(700)
        repeat(700) { ps.spawn(it * 0.5f, it * 0.9f, 0f, 0f, 1f, 3f, Pal.YELLOW) }
        val p = pass()
        ps.recordGl(p, 360f, 640f)
        assertEquals(700 * 12, p.particleVertCount)
        assertEquals(700 * 6, p.particleHaloCount)
        p.reset()
        assertEquals(0, p.particleVertCount)
        assertEquals(0, p.particleHaloCount)
        // Everything in the buffer is finite.
        ps.recordGl(p, 360f, 640f)
        for (i in 0 until p.particleVertCount * stride) assertTrue(p.particleVerts[i].isFinite())
    }

    @Test
    fun anEmptyPoolRecordsNothing() {
        val p = pass()
        Particles().recordGl(p, 360f, 640f)
        assertEquals(0, p.particleVertCount)
    }

    // ------------------------------------------------------------------ the 3D stage takes them

    private fun stage() = Stage3D(360, 640).apply { look(180f, 200f, 700f, 180f, 0f, 100f, fovDeg = 45f) }

    private fun offer(ps: Particles) {
        GameViewport.particles = ps
        GameViewport.particlesInGl = false
    }

    @Test
    fun presentRecordsTheOfferedParticlesIntoTheGpuPicture() {
        val ps = Particles(8)
        ps.spawn(180f, 320f, 0f, 0f, 1f, 4f, Pal.PINK)
        val stage = stage()
        offer(ps)
        stage.begin()
        stage.present()
        assertTrue(GameViewport.particlesInGl)
        val current = LinkedHashMap<String, RenderPass>()
        assertTrue(Gfx.take(current, 1))
        assertEquals(12, current[GameViewport.SLOT]!!.particleVertCount)
    }

    @Test
    fun withoutOfferedParticlesTheFallbackStaysInCharge() {
        val stage = stage()
        GameViewport.particles = null
        GameViewport.particlesInGl = false
        stage.begin()
        stage.present()
        assertFalse(GameViewport.particlesInGl)
        val current = LinkedHashMap<String, RenderPass>()
        assertTrue(Gfx.take(current, 1))
        assertEquals(0, current[GameViewport.SLOT]!!.particleVertCount)
    }

    @Test
    fun ifTheGpuCannotDrawParticlesTheyStayIn2D() {
        GfxQuality.glParticles = false // the GL thread's particle shader did not build
        val ps = Particles(8)
        ps.spawn(180f, 320f, 0f, 0f, 1f, 4f, Pal.PINK)
        val stage = stage()
        offer(ps)
        stage.begin()
        stage.present()
        assertFalse(GameViewport.particlesInGl)
        val current = LinkedHashMap<String, RenderPass>()
        assertTrue(Gfx.take(current, 1))
        assertEquals(0, current[GameViewport.SLOT]!!.particleVertCount)
    }

    @Test
    fun aFrameTheCapSkippedStillHasTheParticlesInTheLastPicture() {
        GfxQuality.displayHz = 120f
        GfxQuality.frameCap = 60
        var now = 1_000_000_000L
        val stage = stage()
        stage.r.clock = NanoClock { now }
        val ps = Particles(8)
        ps.spawn(180f, 320f, 0f, 0f, 1f, 4f, Pal.PINK)

        offer(ps)
        stage.begin()
        stage.present() // taken
        val current = LinkedHashMap<String, RenderPass>()
        assertTrue(Gfx.take(current, 1))
        val shown = current[GameViewport.SLOT]!!
        assertEquals(12, shown.particleVertCount)

        now += 8_333_333L
        offer(ps)
        stage.begin()
        stage.present() // skipped: the picture on screen is still the first one
        // Not painted a second time in 2D over the picture that already has them.
        assertTrue(GameViewport.particlesInGl)
        assertFalse(Gfx.take(current, 1))
        assertTrue(current[GameViewport.SLOT] === shown)
    }
}
