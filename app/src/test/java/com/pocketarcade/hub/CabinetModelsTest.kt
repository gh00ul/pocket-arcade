package com.pocketarcade.hub

import com.pocketarcade.data.DecorStyle
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.Poly
import com.pocketarcade.games.CabinetShape
import com.pocketarcade.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * The hall's cabinet models, built for real on the JVM (the painted textures are pixel-less
 * stand-ins, see `android/graphics/Bitmap.java` in the test sources): every game's own cabinet
 * and every generic shape. Nothing here can say a cabinet is pretty, but it keeps the things a
 * detailed model can silently get wrong in check: parts poking out of the footprint, garbage
 * normals or coordinates, a runaway polygon count (every cabinet in view is drawn each frame), a
 * runaway number of textures (each one is a draw call) and glow bright enough to bloom white.
 */
class CabinetModelsTest {
    private val games = GameRegistry.createAll()
    private val map = HubLayout.build(games, DecorStyle.entries.toSet())

    /** One built cabinet plus what it was built in. */
    private class Built(val name: String, val prop: Prop, val unit: MachineUnit)

    private fun machineProp(shape: CabinetShape, machine: Int): Prop {
        val (w, d, h) = HubLayout.cabinetSize(shape)
        return Prop(PropKind.MACHINE, 100f, 100f, 100f + w, 100f + d, h, machine = machine, shape = shape)
    }

    /** Each game's first copy on the floor, then the generic shapes no shipped game uses, on the claw game's look. */
    private val built: List<Built> by lazy {
        val out = ArrayList<Built>()
        for ((i, g) in games.withIndex()) {
            val p = map.props.first { it.kind == PropKind.MACHINE && it.machine == i }
            out += Built(g.id, p, MachineUnit(p, g, MachineArt(g)))
        }
        val clawGame = games.first { it.id == "claw" }
        for (shape in listOf(CabinetShape.UPRIGHT, CabinetShape.WIDE, CabinetShape.LANE, CabinetShape.TABLE)) {
            val p = machineProp(shape, games.indexOf(clawGame))
            out += Built("generic-${shape.name.lowercase()}", p, MachineUnit(p, clawGame, MachineArt(clawGame)))
        }
        out
    }

    private fun Model.textures() = polys.map { it.region.tex }.toSet().size

    @Test
    fun printBudgets() {
        // Read with the test report; the asserts below are the actual limits.
        for (b in built) {
            val m = b.unit.model
            val opaque = m.polys.count { it.blend == Blend.OPAQUE }
            val alphaN = m.polys.count { it.blend == Blend.ALPHA }
            val addN = m.polys.count { it.blend == Blend.ADD }
            println(
                "CABINET %-16s polys %4d (opaque %4d alpha %3d add %3d) textures %2d lights %d  x %.1f..%.1f (box %.1f..%.1f)  z %.1f..%.1f (box %.1f..%.1f)  y 0..%.1f (h %.1f)".format(
                    b.name, m.polys.size, opaque, alphaN, addN, m.textures(), b.unit.lights.size,
                    m.minX, m.maxX, b.prop.x0, b.prop.x1, m.minZ, m.maxZ, b.prop.z0, b.prop.z1, m.maxY, b.prop.height,
                ),
            )
        }
    }

    @Test
    fun everyModelHasSaneGeometry() {
        for (b in built) {
            val m = b.unit.model
            assertTrue("${b.name} is empty", m.polys.isNotEmpty())
            for (p in m.polys) {
                assertNotNull(p.region)
                assertTrue("${b.name}: a polygon with ${p.n} vertices", p.n in 3..8)
                for (i in 0 until p.n) {
                    val f = floatArrayOf(p.xs[i], p.ys[i], p.zs[i], p.us[i], p.vs[i])
                    for (v in f) assertTrue("${b.name}: non-finite vertex value", v.isFinite())
                }
                val len = sqrt(p.nx * p.nx + p.ny * p.ny + p.nz * p.nz)
                // The shader normalises, and a few hand-written quads lean a normal without renormalising it.
                // Lathed shapes (spheres, tori, capsules) average their edge normals, so their face normal is shorter.
                if (p.vnx == null) assertEquals("${b.name}: a normal that isn't near unit length", 1f, len, 0.08f)
                else assertTrue("${b.name}: a smooth polygon's normal is only $len long", len > 0.5f)
                assertTrue("${b.name}: gloss ${p.gloss}", p.gloss in 0f..1f)
                assertTrue("${b.name}: emissive ${p.emissive}", p.emissive in 0f..MAX_EMISSIVE)
            }
        }
    }

    @Test
    fun cabinetsStayInsideTheirFootprintPlusTheKnownOverhang() {
        for (b in built) {
            val o = OVERHANG_OF[b.name] ?: OVERHANG
            val p = b.prop
            // Solid and glass parts only: an additive glow decal (the racer's floor pan light) spills further by design.
            val parts = b.unit.model.polys.filter { it.blend != Blend.ADD }
            fun lo(f: (Poly, Int) -> Float) = parts.minOf { q -> (0 until q.n).minOf { f(q, it) } }
            fun hi(f: (Poly, Int) -> Float) = parts.maxOf { q -> (0 until q.n).maxOf { f(q, it) } }
            val minX = lo { q, i -> q.xs[i] }
            val maxX = hi { q, i -> q.xs[i] }
            val minZ = lo { q, i -> q.zs[i] }
            val maxZ = hi { q, i -> q.zs[i] }
            val minY = lo { q, i -> q.ys[i] }
            val maxY = hi { q, i -> q.ys[i] }
            assertTrue("${b.name} sticks out left: $minX vs ${p.x0}", minX >= p.x0 - o.side - 1e-3f)
            assertTrue("${b.name} sticks out right: $maxX vs ${p.x1}", maxX <= p.x1 + o.side + 1e-3f)
            assertTrue("${b.name} sticks out at the back: $minZ vs ${p.z0}", minZ >= p.z0 - o.back - 1e-3f)
            assertTrue("${b.name} sticks out at the front: $maxZ vs ${p.z1}", maxZ <= p.z1 + o.front + 1e-3f)
            assertTrue("${b.name} is too tall: $maxY vs ${p.height}", maxY <= p.height + o.top + 1e-3f)
            assertTrue("${b.name} goes below the floor: $minY", minY >= -1e-3f)
        }
    }

    @Test
    fun polygonAndTextureBudgetsHold() {
        for (b in built) {
            val m = b.unit.model
            assertTrue("${b.name} has ${m.polys.size} polygons (budget $POLY_BUDGET)", m.polys.size <= POLY_BUDGET)
            assertTrue("${b.name} uses ${m.textures()} textures (budget $TEXTURE_BUDGET)", m.textures() <= TEXTURE_BUDGET)
        }
    }

    @Test
    fun everyCabinetHasItsFloorPoolAndAFewLightsOfItsOwn() {
        for (b in built) {
            // The hall has a light budget: the floor pool every cabinet throws plus one or two of its own.
            assertTrue("${b.name} has ${b.unit.lights.size} lights", b.unit.lights.size in 2..4)
        }
    }

    @Test
    fun paleColoursGlowLessThanSaturatedOnesAndDarkOnesAreLeftAlone() {
        val white = MachineKit.glowFor(0xFFFFFFFF.toInt(), 1f)
        val yellow = MachineKit.glowFor(0xFFFFE14D.toInt(), 1f)
        val red = MachineKit.glowFor(0xFFB0213A.toInt(), 1f)
        assertTrue("white $white, yellow $yellow", white < yellow)
        assertTrue("yellow $yellow, red $red", yellow <= red)
        assertEquals(1f, red, 1e-6f)
        assertEquals(1f - MachineKit.PALE_GLOW_CUT, white, 1e-6f)
        assertEquals(0f, MachineKit.glowFor(-1, 0f), 0f)
    }

    @Test
    fun controlPanelHardwareFitsOnItsPrintedPanel() {
        // An upright's panel is 20.4 wide; the joystick's plate is 4.6 across, a button's collar 2.3.
        val w = 20.4f
        val stickLeft = MachineKit.PANEL_STICK_U * w - 2.3f
        val stickRight = MachineKit.PANEL_STICK_U * w + 2.3f
        val firstButtonLeft = MachineKit.PANEL_BUTTON_U * w - 1.15f
        val lastButtonRight = (MachineKit.PANEL_BUTTON_U + 2 * MachineKit.PANEL_BUTTON_STEP_U + MachineKit.PANEL_BUTTON_ROW_SHIFT_U) * w + 1.15f
        assertTrue("the joystick runs off the panel's left", stickLeft > 0f)
        assertTrue("the joystick overlaps the first button", stickRight < firstButtonLeft)
        assertTrue("the buttons run off the panel's right", lastButtonRight < w)
        assertTrue("neighbouring buttons overlap", MachineKit.PANEL_BUTTON_STEP_U * w > 2.3f)
        // The two rows sit 8 deep panel units apart at most; their collars must clear each other.
        assertTrue("the rows overlap", (MachineKit.PANEL_ROW2_V - MachineKit.PANEL_ROW_V) * 8f > 2.3f)
    }

    private class Overhang(val side: Float, val back: Float, val front: Float, val top: Float)

    private fun overhang(name: String): Overhang = OVERHANG
    private companion object {
        /** Loudest emissive multiplier any cabinet polygon may have; the highlight adds a fifth on top. */
        const val MAX_EMISSIVE = 1.6f
        const val POLY_BUDGET = 5600
        const val TEXTURE_BUDGET = 48

        /**
         * How far a cabinet's parts may reach past its footprint: marquees, rails and the air-hockey
         * scoreboard past the sides, the mains cable trailing off behind, a coin door, a lip or a leaning seat at the
         * front. Details go inside these margins; the footprint, the collision boxes and the bank
         * cells are unchanged.
         */
        val OVERHANG = Overhang(side = 3.1f, back = 6.6f, front = 2.0f, top = 0.7f)

        /** The pinball table's plunger pokes out of its front. */
        val OVERHANG_OF = mapOf("pinball" to Overhang(side = 3.1f, back = 6.6f, front = 4.6f, top = 0.7f))
    }
}
