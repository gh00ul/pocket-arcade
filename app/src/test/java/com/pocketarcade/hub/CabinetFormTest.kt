package com.pocketarcade.hub

import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Poly
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Texture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Chamfered, floor-shaded cabinet boxes: closed, outward-facing, inside their box and only ever darker. */
class CabinetFormTest {
    private val tex = Texture(4, 4, IntArray(16) { -1 })
    private val r: Region = tex.full
    private val all = BoxFaces(front = r, left = r, right = r, top = r, back = r, gloss = 0.3f)

    private fun build(
        f: BoxFaces = all, x0: Float = 10f, y0: Float = 0f, z0: Float = 20f, x1: Float = 50f, y1: Float = 80f, z1: Float = 50f,
        bevel: Float = CabinetForm.BEVEL, floorAo: Float = CabinetForm.FLOOR_AO,
    ): Model = Model(ModelBuilder().beveledBox(x0, y0, z0, x1, y1, z1, f, bevel = bevel, floorAo = floorAo).build().polys)

    private fun key(p: Poly, i: Int) = "${(p.xs[i] * 1000f).roundToInt()},${(p.ys[i] * 1000f).roundToInt()},${(p.zs[i] * 1000f).roundToInt()}"

    @Test
    fun aFullBoxBecomesSeventeenPolygonsOrTwentyFiveWithOcclusion() {
        assertEquals(17, build(floorAo = 0f).polys.size)
        assertEquals(25, build().polys.size)
        assertEquals(5, ModelBuilder().box(0f, 0f, 0f, 1f, 1f, 1f, all).build().polys.size)
    }

    @Test
    fun theBoxKeepsExactlyItsBounds() {
        val m = build()
        assertEquals(10f, m.minX, 1e-4f)
        assertEquals(50f, m.maxX, 1e-4f)
        assertEquals(0f, m.minY, 1e-4f)
        assertEquals(80f, m.maxY, 1e-4f)
        assertEquals(20f, m.minZ, 1e-4f)
        assertEquals(50f, m.maxZ, 1e-4f)
    }

    @Test
    fun theSurfaceIsClosedAboveTheFloor() {
        for (ao in floatArrayOf(0f, CabinetForm.FLOOR_AO)) {
            val edges = HashMap<String, Int>()
            val onFloor = HashSet<String>()
            for (p in build(floorAo = ao).polys) for (i in 0 until p.n) {
                val j = (i + 1) % p.n
                val a = key(p, i)
                val b = key(p, j)
                if (a == b) continue
                val e = if (a < b) "$a|$b" else "$b|$a"
                edges[e] = (edges[e] ?: 0) + 1
                if (p.ys[i] == 0f && p.ys[j] == 0f) onFloor += e
            }
            for ((e, n) in edges) {
                if (e in onFloor) assertEquals("floor edge $e (ao $ao)", 1, n)
                else assertEquals("edge $e (ao $ao) should join exactly two polygons", 2, n)
            }
        }
    }

    @Test
    fun everyNormalIsUnitLengthAndPointsOutOfTheBox() {
        val cx = 30f
        val cy = 40f
        val cz = 35f
        for (p in build().polys) {
            val len = sqrt(p.nx * p.nx + p.ny * p.ny + p.nz * p.nz)
            assertEquals(1f, len, 1e-3f)
            var mx = 0f
            var my = 0f
            var mz = 0f
            for (i in 0 until p.n) { mx += p.xs[i]; my += p.ys[i]; mz += p.zs[i] }
            mx /= p.n; my /= p.n; mz /= p.n
            assertTrue("normal (${p.nx}, ${p.ny}, ${p.nz}) at ($mx, $my, $mz)", p.nx * (mx - cx) + p.ny * (my - cy) + p.nz * (mz - cz) > 0f)
            // And it is the polygon's own plane (the mesh builder winds triangles from it).
            var gx = 0f
            var gy = 0f
            var gz = 0f
            for (a in 0 until p.n) {
                val b = (a + 1) % p.n
                gx += (p.ys[a] - p.ys[b]) * (p.zs[a] + p.zs[b])
                gy += (p.zs[a] - p.zs[b]) * (p.xs[a] + p.xs[b])
                gz += (p.xs[a] - p.xs[b]) * (p.ys[a] + p.ys[b])
            }
            val gl = sqrt(gx * gx + gy * gy + gz * gz)
            assertTrue("degenerate polygon", gl > 1e-4f)
            assertEquals(1f, abs(gx * p.nx + gy * p.ny + gz * p.nz) / gl, 1e-3f)
        }
    }

    @Test
    fun chamfersCatchTheLightWithAtLeastTheBevelGloss() {
        val flat = BoxFaces(front = r, left = r, right = r, top = r, back = r, gloss = 0f)
        // The cuts are the polygons that face neither straight along an axis: 4 down the corners, 4 along the top, 4 corners.
        val cuts = build(flat, floorAo = 0f).polys.filter { p -> maxOf(abs(p.nx), maxOf(abs(p.ny), abs(p.nz))) < 0.99f }
        assertEquals(12, cuts.size)
        for (p in cuts) assertTrue(p.gloss >= CabinetForm.BEVEL_GLOSS - 1e-6f)
    }

    @Test
    fun occlusionOnlyDarkensTheFootOfUnlitSides() {
        val m = build()
        var darkest = 1f
        for (p in m.polys) {
            val s = p.shade
            if (s == null) {
                // Anything without shading is a top face, a cut on top, or the plain upper part of a side.
                continue
            }
            assertEquals(p.n, s.size)
            for (i in 0 until p.n) {
                assertTrue("shade ${s[i]}", s[i] in (1f - CabinetForm.FLOOR_AO - 1e-4f)..1f)
                darkest = minOf(darkest, s[i])
                // Darkest right at the floor, plain from the top of the occlusion band up.
                if (p.ys[i] >= CabinetForm.FLOOR_AO_HEIGHT - 1e-3f) assertEquals(1f, s[i], 1e-5f)
                if (p.ys[i] <= 1e-4f) assertEquals(1f - CabinetForm.FLOOR_AO, s[i], 1e-5f)
            }
        }
        assertEquals(1f - CabinetForm.FLOOR_AO, darkest, 1e-5f)
        // Everything at the floor is shaded.
        for (p in m.polys) for (i in 0 until p.n) if (p.ys[i] <= 1e-4f) assertNotNull(p.shade)
    }

    @Test
    fun glowingFacesAreNeverDarkened() {
        val lit = BoxFaces(front = r, left = r, right = r, top = r, back = r, frontEmissive = 0.9f, gloss = 0.3f)
        for (p in build(lit).polys) {
            if (p.emissive > 0f) assertNull("a glowing polygon must keep its full brightness", p.shade)
        }
        assertTrue(build(lit).polys.any { it.emissive > 0f })
    }

    @Test
    fun boxesNotOnTheFloorGetNoOcclusionByDefault() {
        val m = Model(ModelBuilder().beveledBox(10f, 30f, 20f, 50f, 60f, 50f, all).build().polys)
        assertTrue(m.polys.none { it.shade != null })
        assertEquals(17, m.polys.size)
    }

    @Test
    fun aMissingFaceKeepsItsNeighboursSquareOnThatSide() {
        // A kick panel between two side panels: front, top and back only, no sides.
        val m = build(BoxFaces(front = r, top = r, back = r), floorAo = 0f)
        assertEquals(10f, m.minX, 1e-4f)
        assertEquals(50f, m.maxX, 1e-4f)
        // The front runs the full width (nothing cut at its ends); there is one cut along the top
        // front, one along the top back, and no vertical cuts or corners.
        val front = m.polys.filter { it.nz == 1f && it.ny == 0f }
        assertEquals(1, front.size)
        assertEquals(10f, front[0].xs.min(), 1e-4f)
        assertEquals(50f, front[0].xs.max(), 1e-4f)
        assertEquals(3 + 2, m.polys.size)
    }

    @Test
    fun frontFaceKeepsItsTextureRegisteredToTheWholeBox() {
        val m = build(floorAo = 0f)
        val front = m.polys.single { it.nz == 1f && it.ny == 0f }
        val b = CabinetForm.BEVEL
        // The face is inset by a bevel on each side; its u runs the same fraction of the texture.
        assertEquals(r.w * b / 40f, front.us.min(), 1e-3f)
        assertEquals(r.w * (40f - b) / 40f, front.us.max(), 1e-3f)
        // Rows too: inset by the top cut, all the way down to the floor.
        assertEquals(r.h * b / 80f, front.vs.min(), 1e-3f)
        assertEquals(r.h.toFloat(), front.vs.max(), 1e-3f)
    }

    @Test
    fun thinPanelsKeepMostOfTheirWidth() {
        // The side panels are only 1.8 thick: the chamfer must not eat the panel.
        val m = build(x0 = 10f, x1 = 11.8f, floorAo = 0f)
        val front = m.polys.single { it.nz == 1f && it.ny == 0f }
        assertTrue("front width ${front.xs.max() - front.xs.min()}", front.xs.max() - front.xs.min() > 1.8f * 0.35f)
    }

    @Test
    fun aBevelOfZeroKeepsSquareEdgesButStillShadesTheFoot() {
        // A kick panel tucked between side panels: nothing to cut, but it still sits in the floor.
        val kick = BoxFaces(front = r, top = r, back = r)
        val m = build(kick, bevel = 0f)
        assertEquals(2 + 2 + 1, m.polys.size)
        assertEquals(10f, m.minX, 1e-4f)
        assertEquals(50f, m.maxX, 1e-4f)
        assertTrue(m.polys.count { it.shade != null } == 2)
        for (p in m.polys) assertTrue("only axis-facing polygons", maxOf(abs(p.nx), maxOf(abs(p.ny), abs(p.nz))) > 0.99f)
        // And with no occlusion either it is exactly a plain box.
        assertEquals(3, build(kick, bevel = 0f, floorAo = 0f).polys.size)
    }

    @Test
    fun tinyBoxesStayPlain() {
        val m = Model(ModelBuilder().beveledBox(0f, 0f, 0f, 0.2f, 0.2f, 0.2f, all).build().polys)
        assertEquals(5, m.polys.size)
    }

    @Test
    fun theOcclusionBandIsClampedOnShortBoxes() {
        // A box lower than the band: the darkening stops short of the top cut instead of overshooting it.
        val m = build(y1 = 6f)
        for (p in m.polys) for (i in 0 until p.n) assertTrue(p.ys[i] in 0f..6f)
        assertTrue(m.polys.any { it.shade != null })
    }
}
