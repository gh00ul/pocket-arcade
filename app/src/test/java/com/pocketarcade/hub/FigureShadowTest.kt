package com.pocketarcade.hub

import com.pocketarcade.engine.r3d.Lighting
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Texture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/** Where kids' shadows fall: away from the lamps above, smoothly, and never longer than the cap. */
class FigureShadowTest {
    private val shadow = FigureShadow(Texture(4, 4, IntArray(16) { 0x80000000.toInt() }).full)
    private val out = FloatArray(2)

    /** The hall's real key light and no lamps. */
    private fun hall(vararg lamps: PointLight): Lighting {
        val l = Lighting()
        l.setDirection(0.1f, 1f, 0.35f)
        for (p in lamps) l.points += p
        return l
    }

    private fun downlight(x: Float, z: Float) = PointLight(x, 150f, z, 1f, 0.9f, 0.78f, 175f, 0.55f)

    private fun lean(l: Lighting, x: Float, z: Float): Pair<Float, Float> {
        shadow.cast(l, x, z, out)
        return out[0] to out[1]
    }

    @Test
    fun withNoLampsTheShadowRunsAwayFromTheKeyLight() {
        val (lx, lz) = lean(hall(), 300f, 300f)
        // The key light stands over +x and +z of the hall, so shadows run towards -x and -z.
        assertTrue("lean x $lx", lx < 0f)
        assertTrue("lean z $lz", lz < 0f)
        assertTrue(hypot(lx, lz) > FigureShadow.MIN_LEAN)
    }

    @Test
    fun aKidRightUnderADownlightHasNoShadowToSpeakOf() {
        val under = lean(hall(downlight(300f, 300f)), 300f, 300f)
        val away = lean(hall(downlight(300f, 300f)), 300f + 60f, 300f)
        assertTrue("under the lamp: $under", hypot(under.first, under.second) < hypot(away.first, away.second))
        assertTrue("under the lamp: $under", hypot(under.first, under.second) < FigureShadow.MIN_LEAN * 2f)
    }

    @Test
    fun theShadowLeansAwayFromALampOffToOneSide() {
        // The lamp is to the kid's left (smaller x): the shadow falls to the right (larger x).
        val (lx, _) = lean(hall(downlight(240f, 300f)), 300f, 300f)
        assertTrue("lean x $lx", lx > 0.1f)
        // The lamp is behind the kid (smaller z): the shadow falls towards the entrance (larger z).
        val (_, lz) = lean(hall(downlight(300f, 240f)), 300f, 300f)
        assertTrue("lean z $lz", lz > 0.1f)
    }

    @Test
    fun lampsOnEitherSideCancelAndLowLampsAreIgnored() {
        val pair = hall(downlight(240f, 300f), downlight(360f, 300f))
        val (lx, _) = lean(pair, 300f, 300f)
        val single = lean(hall(downlight(240f, 300f)), 300f, 300f).first
        assertTrue("two lamps either side: $lx vs one: $single", kotlin.math.abs(lx) < single / 3f)
        // The floor glow of a cabinet is only 20 up: no floor shadow from it.
        val glow = PointLight(240f, 20f, 300f, 1f, 0.2f, 0.9f, 70f, 1f)
        assertEquals(lean(hall(), 300f, 300f), lean(hall(glow), 300f, 300f))
    }

    @Test
    fun lampsOutOfReachDoNotCount() {
        val far = hall(downlight(0f, 0f))
        assertEquals(lean(hall(), 500f, 500f), lean(far, 500f, 500f))
    }

    @Test
    fun theShadowSwingsSmoothlyAsAKidWalksBetweenLamps() {
        val l = hall(downlight(76f, 90f), downlight(196f, 90f), downlight(76f, 210f), downlight(196f, 210f))
        var prev = lean(l, 60f, 60f)
        var x = 60f
        while (x < 220f) {
            x += 1f
            val now = lean(l, x, 60f + (x - 60f) * 0.9f)
            assertTrue("jump of ${hypot(now.first - prev.first, now.second - prev.second)} at x=$x", hypot(now.first - prev.first, now.second - prev.second) < 0.05f)
            prev = now
        }
    }

    @Test
    fun theShadowIsNeverLongerThanTheCap() {
        // A very strong lamp just past the edge of a kid.
        val l = hall(PointLight(300f, 110f, 300f, 1f, 1f, 1f, 400f, 50f))
        for (dx in -170..170 step 17) for (dz in -170..170 step 17) {
            val (lx, lz) = lean(l, 300f + dx, 300f + dz)
            assertTrue("lean ${hypot(lx, lz)} at $dx,$dz", hypot(lx, lz) <= FigureShadow.MAX_LEAN + 1e-4f)
        }
    }

    @Test
    fun drawsTheBlobAlwaysAndTheCastPiecesWhenTheShadowIsLongEnough() {
        fun quadsFor(l: Lighting, x: Float, z: Float): Int {
            val r = Renderer3D(64, 64)
            r.startFrame()
            r.camera.lookAt(x, 300f, z + 40f, x, 0f, z, (Math.PI / 3).toFloat(), 64, 64)
            r.clear(0xFF000000.toInt())
            l.points.forEach { r.lighting.points += it }
            r.lighting.setDirection(l.dirX, l.dirY, l.dirZ)
            shadow.draw(r, x, z, 1f)
            return r.polysDrawn
        }
        // Off to one side of a lamp: contact blob, body stripe and head.
        assertEquals(3, quadsFor(hall(downlight(240f, 300f)), 300f, 300f))
        // Right under it, with no key light leaning it: just the blob.
        val overhead = hall(downlight(300f, 300f)).also { it.setDirection(0f, 1f, 0f) }
        assertEquals(1, quadsFor(overhead, 300f, 300f))
    }

    @Test
    fun theReachCoversTheWholeShadow() {
        // Head at full lean, plus half the head's disc, must sit inside the culling reach.
        assertTrue(FigureShadow.REACH >= FigureShadow.MAX_LEAN * FigureShadow.HEAD_Y + FigureShadow.HEAD_SIZE / 2f)
    }

    @Test
    fun theShadowIsSoftEnoughNotToDoubleUpInADarkBlot() {
        // Each piece has the shadow texture's centre alpha times its own; overlapping pieces
        // (feet under the body stripe) must stay a light shadow, not black.
        val worst = 1f - (1f - FigureShadow.CONTACT_ALPHA) * (1f - FigureShadow.BODY_ALPHA) * (1f - FigureShadow.HEAD_ALPHA)
        assertTrue("combined alpha $worst", worst < 0.9f)
    }
}
