package com.pocketarcade.games.scenea

import com.pocketarcade.games.claw.PlushShade
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.TexKit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The glow textures are pure maths and the plush shading only ever darkens: both checkable headlessly. */
class SceneFxTest {
    private fun alphaAt(t: com.pocketarcade.engine.r3d.Texture, x: Int, y: Int) = t.pixels[y * t.width + x] ushr 24

    @Test
    fun theRingIsBrightestOnItsBandAndGoneAtTheEdge() {
        val band = SceneFx.ringAlpha(SceneFx.RING_RADIUS)
        assertTrue("band $band", band >= 250)
        assertTrue("the centre is only a faint halo", SceneFx.ringAlpha(0f) < band / 3)
        assertEquals("nothing left at the quad's edge", 0, SceneFx.ringAlpha(1f))
        val t = SceneFx.ring
        assertEquals(64, t.width)
        assertEquals("corners are empty", 0, alphaAt(t, 0, 0))
    }

    @Test
    fun theShaftFadesDownAndSoftensAtItsEdges() {
        val t = SceneFx.shaft
        val mid = t.width / 2
        val top = alphaAt(t, mid, 0)
        val bottom = alphaAt(t, mid, t.height - 1)
        assertTrue("top $top", top > 200)
        assertTrue("bottom $bottom", bottom < 8)
        assertTrue("the edges are softer than the middle", alphaAt(t, 0, 0) < top / 2)
    }

    @Test
    fun theFlareHasABrightCoreAndEmptyCorners() {
        val t = SceneFx.flare
        assertTrue(alphaAt(t, t.width / 2, t.height / 2) > 220)
        assertEquals(0, alphaAt(t, 0, 0))
        assertEquals(0, alphaAt(t, t.width - 1, t.height - 1))
    }

    @Test
    fun aGradientRunsFromTopToBottomColour() {
        val g = SceneFx.gradient(2, 8, 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        assertEquals(0xFF000000.toInt(), g.pixels[0])
        assertEquals(0xFFFFFFFF.toInt(), g.pixels[g.pixels.size - 1])
    }

    // ---------------------------------------------------------------- plush shading

    @Test
    fun paleFabricIsCappedAndDarkFabricIsUntouched() {
        val white = PlushShade.soften(0xFFFFFFFF.toInt())
        val top = maxOf(white shr 16 and 255, maxOf(white shr 8 and 255, white and 255))
        assertTrue("white capped to ${PlushShade.PALE_CAP}: $top", top <= (PlushShade.PALE_CAP * 255f).toInt())
        assertEquals("alpha is kept", 0xFF, white ushr 24)
        val brown = 0xFF8B5A2B.toInt()
        assertEquals(brown, PlushShade.soften(brown))
    }

    @Test
    fun occlusionOnlyDarkensAndTheFootIsDarkest() {
        for (h in 0..10) for (ny in -10..10) {
            val s = PlushShade.shadeAt(h / 10f, ny / 10f)
            assertTrue("shade $s", s in PlushShade.AO_MIN..1f)
        }
        assertTrue(PlushShade.shadeAt(0f, -1f) < PlushShade.shadeAt(1f, 1f))
        assertEquals(PlushShade.AO_MIN, PlushShade.shadeAt(0f, -1f), 1e-5f)
        assertEquals(1f, PlushShade.shadeAt(1f, 1f), 1e-5f)
    }

    @Test
    fun bakingKeepsGeometryAndShadesEveryMatteVertex() {
        val region = TexKit.white.full
        val src = ModelBuilder()
            .sphere(0f, 6f, 0f, 6f, region, slices = 8, stacks = 6)
            .sphere(0f, 14f, 0f, 2f, region, slices = 6, stacks = 4, emissive = 1f)
            .build()
        val baked = PlushShade.bake(src)
        assertEquals(src.polys.size, baked.polys.size)
        for ((a, b) in src.polys.zip(baked.polys)) {
            assertEquals(a.n, b.n)
            for (i in 0 until a.n) {
                assertEquals(a.xs[i], b.xs[i], 0f)
                assertEquals(a.ys[i], b.ys[i], 0f)
            }
            if (a.emissive > 0f) {
                assertNull("a glowing polygon keeps full brightness", b.shade)
            } else {
                val sh = b.shade
                assertNotNull(sh)
                for (v in sh!!) assertTrue("shade $v", v in PlushShade.AO_MIN..1f)
            }
        }
    }
}
