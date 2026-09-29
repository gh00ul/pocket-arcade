package com.pocketarcade.hub

import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.PointLight
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.games.MiniGame
import kotlin.math.sqrt

/**
 * A machine's own hall cabinet, supplied through [MiniGame.cabinet]: how big it is, where the
 * camera dives to, its model, lights and live screen, and any parts that move in attract mode.
 * Games without one get the built-in cabinet for their shape. A design lives in its game's
 * package with the rest of the game's art.
 *
 * Designs must be headless-safe: the sizes below are plain numbers (the floor plan and its unit
 * tests read them on the JVM), and every texture or model is built lazily, from [build] or
 * [animate] only.
 *
 * Coordinates are hall units: x across the hall, y up, z from the back wall towards the
 * entrance. The cabinet faces +z; the player stands in front of [CabinetBox.z1].
 */
interface CabinetDesign {
    /** Footprint across the hall. It must fit the width of its bank's cells (see [HubLayout.slots]). */
    val width: Float
    /** Footprint front to back; must fit the cells' depth. */
    val depth: Float
    /** Height of the tallest part; must fit the cells' height. */
    val height: Float

    /** Height the camera dives to when the machine is entered (usually the screen's middle). */
    val focusHeight: Float
    /** How far behind the cabinet's front the dive ends. */
    val focusSetBack: Float

    /**
     * Size of the attract screen in painter units (what [MiniGame.drawAttract] gets as w × h), or
     * null if the cabinet has no live screen. [CabinetBuild.liveScreen] makes one this size.
     */
    val screenUnits: Pair<Int, Int>?

    /**
     * Builds one copy of the cabinet into [c]: model parts through [CabinetBuild.b] and the
     * helpers, lights with [CabinetBuild.light] (2 or 3 per cabinet: the hall has a light
     * budget) and the live screen with [CabinetBuild.liveScreen]. Called once per copy.
     */
    fun build(c: CabinetBuild)

    /**
     * Draws the parts that move in attract mode (a patrolling claw, a sweeping shelf) every
     * frame the cabinet is on screen, at hall time [t]. Draw opaque parts only; must not allocate.
     */
    fun animate(r: Renderer3D, c: CabinetBox, t: Float) {}
}

/**
 * One copy of a cabinet on the floor: the box it stands in ([x0]..[x1] across, [z0] back to
 * [z1] front, [h] tall), which copy of the bank it is ([variant]), a [seed] to vary copies by,
 * and the game's printed artwork ([art]). Also handed to [CabinetDesign.animate].
 */
open class CabinetBox internal constructor(
    val x0: Float,
    val x1: Float,
    val z0: Float,
    val z1: Float,
    val h: Float,
    val variant: Int,
    val seed: Int,
    val art: MachineArt,
) {
    val cx: Float get() = (x0 + x1) / 2f
    val cz: Float get() = (z0 + z1) / 2f

    /** Scratch placements for drawing or adding models; reuse them rather than allocating. */
    val xf = Xform()
    val xf2 = Xform()

    /** This copy's attract clock, offset so copies side by side don't move in step. */
    fun phase(t: Float): Float = t + seed * 0.37f
}

/**
 * What a [CabinetDesign] builds one copy with: the model builder [b], the copy's [CabinetBox]
 * and the shared cabinet pieces every built-in cabinet uses (side panels with T-molding, a lit
 * marquee with chase bulbs, LED score display, glass, chrome posts, lights, a live screen).
 */
class CabinetBuild internal constructor(
    val b: ModelBuilder,
    private val unit: MachineUnit,
    x0: Float, x1: Float, z0: Float, z1: Float, h: Float, variant: Int, seed: Int, art: MachineArt,
) : CabinetBox(x0, x1, z0, z1, h, variant, seed, art) {

    /** How lit each part of the shared cabinet furniture is; the numbers only ever multiply a colour that is already painted. */
    companion object {
        /** How far T-moulding stands proud of the face it edges, and how far it overlaps a panel on each side. */
        const val T_MOLD_OUT = 0.35f
        const val T_MOLD_SIDE = 0.15f
        /** The marquee sign's own light (just under the bloom threshold on its brightest paint). */
        const val MARQUEE_GLOW = 1.2f
        /** The topper on top of the marquee, seen from above. */
        const val TOPPER_GLOW = 0.9f
        /** The lit line along the marquee's top cap and down its ends. */
        const val MARQUEE_TRIM_GLOW = 0.8f
        /** Strip lights under a marquee, along a bezel, in a glass case. */
        const val LED_GLOW = 1.2f
        /** The neon line hugging a screen. */
        const val BEZEL_GLOW = 1.0f
        /** Width of that line. */
        const val BEZEL_LINE = 0.35f
        /** The coin slots' lit windows. */
        const val SLOT_GLOW = 1.5f
    }

    /** Adds a coloured point light ([color] is ARGB). Keep to 2 or 3 per cabinet. */
    fun light(x: Float, y: Float, z: Float, color: Int, radius: Float, intensity: Float) {
        unit.lights += PointLight(x, y, z, (color shr 16 and 255) / 255f, (color shr 8 and 255) / 255f, (color and 255) / 255f, radius, intensity)
    }

    /** A row of [count] chase bulbs from [xa] to [xb] at height [y], depth [z]. */
    fun bulbRow(xa: Float, xb: Float, y: Float, z: Float, count: Int) {
        for (k in 0 until count) unit.addBulb(xa + (xb - xa) * (k + 0.5f) / count, y, z)
    }

    /** Emissive of the lit T-moulding along a cabinet's edges (before [MachineKit.glowFor] tones it down for pale trim). */
    private val tmoldGlow = 0.6f

    /**
     * Side panels with printed art and lit T-moulding down their front edges: the moulding is a
     * raised strip in the trim colour, a little wider than the panel and standing [T_MOLD_OUT]
     * proud of its front, the way the real plastic edging wraps a panel's edge.
     */
    fun sidePanels(top: Float, depthFront: Float = z1, thick: Float = 1.8f) {
        val side = art.sideArt.full
        val inner = art.bodyPaint.full
        val dark = art.darkPaint.full
        b.beveledBox(x0, 0f, z0, x0 + thick, top, depthFront, BoxFaces(left = side, right = inner, top = dark, back = dark, front = dark, gloss = 0.35f))
        b.beveledBox(x1 - thick, 0f, z0, x1, top, depthFront, BoxFaces(right = side, left = inner, top = dark, back = dark, front = dark, gloss = 0.35f))
        tMoulding(x0 - T_MOLD_SIDE, x0 + thick + T_MOLD_SIDE, 0f, top, depthFront)
        tMoulding(x1 - thick - T_MOLD_SIDE, x1 + T_MOLD_SIDE, 0f, top, depthFront)
    }

    /**
     * A vertical strip of lit T-moulding from [xa] to [xb] and [ya] to [yb], its back on the face
     * at depth [z] and standing [T_MOLD_OUT] proud. Glossy, in the trim colour.
     */
    fun tMoulding(xa: Float, xb: Float, ya: Float, yb: Float, z: Float) {
        val t = art.trimTex.full
        val e = MachineKit.glowFor(art.trim, tmoldGlow)
        b.box(xa, ya, z - 0.01f, xb, yb, z + T_MOLD_OUT, BoxFaces(front = t, left = t, right = t, top = t, frontEmissive = e, topEmissive = e, gloss = 0.5f))
    }

    /**
     * The lit marquee sign: its face leans back a little so it catches the eye from the hall's
     * high camera while still reading square-on from a kid's eye height in front of it, a backlit
     * topper with the game's emblem and short name covers its top (the part of a cabinet the hall
     * view sees most of). Round the face runs a frame: a dark cap along the top with a lit trim
     * line and a row of chase bulbs, a dark rail along the bottom with an LED strip under it
     * washing the screen below, and lit edging down both ends.
     */
    fun marqueeBox(xa: Float, xb: Float, y0: Float, y1: Float, za: Float, zb: Float) {
        val dark = art.darkPaint.full
        val metal = HallArt.darkMetal.full
        val hgt = y1 - y0
        // A gentle lean: enough to catch the hall camera, still square-on to a kid in front of it.
        val lean = minOf(hgt * 0.22f, (zb - za) * 0.45f)
        val zt = zb - lean
        val len = sqrt(hgt * hgt + lean * lean)
        b.quad(xa, y1, zt, xb, y1, zt, xb, y0, zb, xa, y0, zb, art.marquee.full, 0f, lean / len, hgt / len, emissive = MARQUEE_GLOW)
        b.quad(xa, y1, za, xb, y1, za, xb, y1, zt, xa, y1, zt, art.topper.full, 0f, 1f, 0f, emissive = TOPPER_GLOW, gloss = 0.5f)
        b.quad(xa, y1, za, xa, y1, zt, xa, y0, zb, xa, y0, za, dark, -1f, 0f, 0f, gloss = 0.35f)
        b.quad(xb, y1, zt, xb, y1, za, xb, y0, za, xb, y0, zb, dark, 1f, 0f, 0f, gloss = 0.35f)
        b.quad(xb, y1, za, xa, y1, za, xa, y0, za, xb, y0, za, dark, 0f, 0f, -1f)
        b.quad(xa, y0, zb, xb, y0, zb, xb, y0, za, xa, y0, za, dark, 0f, -1f, 0f)
        // The frame: a dark cap over the top edge of the face with a lit trim line on its front...
        val trim = art.trimTex.full
        val e = MachineKit.glowFor(art.trim, MARQUEE_TRIM_GLOW)
        b.box(xa - 0.4f, y1 - 0.5f, zt - 0.9f, xb + 0.4f, y1 + 0.5f, zt + 0.6f, BoxFaces(front = metal, top = metal, left = metal, right = metal, gloss = 0.7f))
        b.quad(xa - 0.3f, y1 + 0.32f, zt + 0.62f, xb + 0.3f, y1 + 0.32f, zt + 0.62f, xb + 0.3f, y1 - 0.18f, zt + 0.62f, xa - 0.3f, y1 - 0.18f, zt + 0.62f, trim, 0f, 0f, 1f, emissive = e)
        // ...a rail along the bottom edge...
        b.box(xa - 0.3f, y0 - 0.3f, zb - 0.5f, xb + 0.3f, y0 + 0.7f, zb + 0.5f, BoxFaces(front = metal, top = metal, left = metal, right = metal, gloss = 0.7f))
        // ...and lit edging down both ends of the face.
        b.quad(xa - 0.26f, y1, zt - 0.4f, xa - 0.26f, y1, zt + 0.2f, xa - 0.26f, y0, zb + 0.2f, xa - 0.26f, y0, zb - 0.4f, trim, -1f, 0f, 0f, emissive = e)
        b.quad(xb + 0.26f, y1, zt + 0.2f, xb + 0.26f, y1, zt - 0.4f, xb + 0.26f, y0, zb - 0.4f, xb + 0.26f, y0, zb + 0.2f, trim, 1f, 0f, 0f, emissive = e)
        // A strip light on the underside, at the front, throwing the sign's colour down the screen.
        ledStripDown(xa + 0.8f, xb - 0.8f, y0 - 0.02f, zb - 2.2f, zb - 0.9f)
        bulbRow(xa + 1f, xb - 1f, y1 + 1.0f, zt - 0.1f, ((xb - xa) / 3.2f).toInt())
    }

    /** A strip of light facing down at height [y] between depths [za] and [zb], in the glow colour, from [xa] to [xb]. */
    fun ledStripDown(xa: Float, xb: Float, y: Float, za: Float, zb: Float, emissive: Float = LED_GLOW) {
        b.quad(xa, y, zb, xb, y, zb, xb, y, za, xa, y, za, art.glowTex.full, 0f, -1f, 0f, emissive = MachineKit.glowFor(art.glow, emissive))
    }

    /** A thin vertical or horizontal strip of light on a face at depth [z] facing +z, in the glow colour. */
    fun ledStrip(xa: Float, ya: Float, xb: Float, yb: Float, z: Float, emissive: Float = LED_GLOW) {
        b.quad(xa, yb, z, xb, yb, z, xb, ya, z, xa, ya, z, art.glowTex.full, 0f, 0f, 1f, emissive = MachineKit.glowFor(art.glow, emissive))
    }

    /**
     * A screen's bezel: a raised black frame [frame] wide round the screen rectangle on the plane
     * at depth [z], standing [depth] proud at its outer edge and sloping down to the glass, so it
     * catches the light, and a thin lit line hugging the screen's edge in the glow colour.
     */
    fun screenBezel(xa: Float, xb: Float, ya: Float, yb: Float, z: Float, frame: Float = 1.3f, depth: Float = 0.7f) {
        val m = art.black.full
        val zo = z + depth
        val len = sqrt(depth * depth + frame * frame)
        val nz = frame / len
        val nd = depth / len
        val g = 0.6f
        // The four slopes, from the outer edge (standing proud) to the inner edge (at the glass).
        b.quad(xa - frame, yb + frame, zo, xb + frame, yb + frame, zo, xb, yb, z, xa, yb, z, m, 0f, -nd, nz, gloss = g)
        b.quad(xa, ya, z, xb, ya, z, xb + frame, ya - frame, zo, xa - frame, ya - frame, zo, m, 0f, nd, nz, gloss = g)
        b.quad(xa - frame, yb + frame, zo, xa, yb, z, xa, ya, z, xa - frame, ya - frame, zo, m, nd, 0f, nz, gloss = g)
        b.quad(xb, yb, z, xb + frame, yb + frame, zo, xb + frame, ya - frame, zo, xb, ya, z, m, -nd, 0f, nz, gloss = g)
        // The outer wall, so the frame reads as a solid from the side.
        val xo0 = xa - frame
        val xo1 = xb + frame
        val yo0 = ya - frame
        val yo1 = yb + frame
        b.quad(xo0, yo1, z, xo1, yo1, z, xo1, yo1, zo, xo0, yo1, zo, m, 0f, 1f, 0f, gloss = g)
        b.quad(xo0, yo0, zo, xo1, yo0, zo, xo1, yo0, z, xo0, yo0, z, m, 0f, -1f, 0f, gloss = g)
        b.quad(xo0, yo1, zo, xo0, yo1, z, xo0, yo0, z, xo0, yo0, zo, m, -1f, 0f, 0f, gloss = g)
        b.quad(xo1, yo1, z, xo1, yo1, zo, xo1, yo0, zo, xo1, yo0, z, m, 1f, 0f, 0f, gloss = g)
        // The lit line just inside the frame.
        val w = BEZEL_LINE
        val zl = z + 0.04f
        ledStrip(xa, yb - w, xb, yb, zl, BEZEL_GLOW)
        ledStrip(xa, ya, xb, ya + w, zl, BEZEL_GLOW)
        ledStrip(xa, ya + w, xa + w, yb - w, zl, BEZEL_GLOW)
        ledStrip(xb - w, ya + w, xb, yb - w, zl, BEZEL_GLOW)
    }

    /**
     * A pane of glass over a screen: one clear, glossy, alpha-blended quad on the plane at depth
     * [z] that picks up the room's reflections and the glass texture's faint streaks. It shares
     * its material with [glassBox], so it costs no extra draw call on a cabinet that has both.
     */
    fun screenGlass(xa: Float, xb: Float, ya: Float, yb: Float, z: Float) {
        b.quad(xa, yb, z, xb, yb, z, xb, ya, z, xa, ya, z, HallArt.glass.full, 0f, 0f, 1f, blend = Blend.ALPHA, cull = false, gloss = 1f)
    }

    /**
     * A ticket dispenser [w] wide, its bottom at [y0], its front flush with the face at depth [z]
     * (it sits in a housing behind that face): a dark plate with a lit slot and a ticket sticking
     * out of it and curling down.
     */
    fun ticketDispenser(x: Float, y0: Float, z: Float, w: Float = 7f) {
        val h = w * 0.5f
        val dark = art.darkPaint.full
        b.box(x - w / 2f, y0, z - 0.9f, x + w / 2f, y0 + h, z, BoxFaces(front = MachineKit.ticketPlate.full, left = dark, right = dark, top = dark, frontEmissive = 0.25f, gloss = 0.4f))
        // The slot is at 14..21.5 of the plate's 32 texels; the paper leaves it at the middle.
        val sy = y0 + h * (1f - 17.5f / 32f)
        val pw = w * 0.17f
        b.quad(x - pw, sy + 0.2f, z + 0.02f, x + pw, sy + 0.2f, z + 0.02f, x + pw, sy - w * 0.42f, z + 1.6f, x - pw, sy - w * 0.42f, z + 1.6f, MachineKit.ticketPaper.full, 0f, 0.36f, 0.93f, cull = false)
    }

    /**
     * A coin door [w] wide, its bottom at [y0], on a face at depth [z] facing +z: a steel door
     * with a chrome frame and two coin mechs whose slots glow red, the way real ones are lit, and a
     * coin return cup under it.
     */
    fun coinDoor(x: Float, y0: Float, z: Float, w: Float = 8f) {
        val hgt = w * 1.25f
        val chrome = HallArt.chrome.full
        val metal = HallArt.darkMetal.full
        b.box(x - w / 2f - 0.4f, y0 - 0.4f, z, x + w / 2f + 0.4f, y0 + hgt + 0.4f, z + 0.3f, BoxFaces(front = chrome, top = chrome, left = chrome, right = chrome, gloss = 0.9f))
        b.quad(x - w / 2f, y0 + hgt, z + 0.32f, x + w / 2f, y0 + hgt, z + 0.32f, x + w / 2f, y0, z + 0.32f, x - w / 2f, y0, z + 0.32f, art.coinDoor.full, 0f, 0f, 1f, gloss = 0.7f)
        // The slots' lit windows, laid over the painted door's slot plates.
        val slot = MachineKit.coinSlotLit.full
        val top = y0 + hgt * (1f - MachineKit.SLOT_Y0)
        val bot = y0 + hgt * (1f - MachineKit.SLOT_Y1)
        val hw = w * MachineKit.SLOT_HALF_W
        for (k in 0 until 2) {
            val sx = x - w / 2f + w * (MachineKit.SLOT_X0 + k * MachineKit.SLOT_DX)
            b.quad(sx - hw, top, z + 0.36f, sx + hw, top, z + 0.36f, sx + hw, bot, z + 0.36f, sx - hw, bot, z + 0.36f, slot, 0f, 0f, 1f, emissive = SLOT_GLOW)
        }
        // The coin return cup under the door: a dark tray with a chrome lip.
        b.box(x - w * 0.3f, y0 - 1.8f, z, x + w * 0.3f, y0 - 0.4f, z + 0.8f, BoxFaces(front = metal, top = chrome, left = metal, right = metal, gloss = 0.6f))
    }

    /** A thin lit strip (T-molding, a neon edge) from ([xa], [ya]) to ([xb], [yb]) on a face at depth [z]. */
    fun neonStrip(xa: Float, ya: Float, xb: Float, yb: Float, z: Float, color: Int = art.glow, emissive: Float = 1.5f) {
        b.quad(xa, yb, z, xb, yb, z, xb, ya, z, xa, ya, z, HallArt.solid(color).full, 0f, 0f, 1f, emissive = emissive)
    }

    /** The red LED score display, facing +z at depth [z]. */
    fun display(xa: Float, xb: Float, ya: Float, yb: Float, z: Float) {
        b.quad(xa, yb, z, xb, yb, z, xb, ya, z, xa, ya, z, art.display.full, 0f, 0f, 1f, emissive = 1.2f)
    }

    /**
     * The cabinet's back, for anyone walking behind the bank: a service panel ([xa]..[xb],
     * [y0]..[y1]) laid over the back face at depth [z], facing -z, with vents, a fan, stickers
     * and a serial plate, and the mains cable dropping from its inlet to the floor.
     */
    fun rearPanel(xa: Float, xb: Float, y0: Float, y1: Float, z: Float = z0) {
        val w = xb - xa
        val hgt = y1 - y0
        val tall = hgt > w * 1.3f
        val tex = MachineKit.rearPanel(tall).full
        val zf = z - 0.06f
        // Seen from behind, +x runs right to left: the texture's left edge goes at xb.
        b.quad(xb, y1, zf, xa, y1, zf, xa, y0, zf, xb, y0, zf, tex, 0f, 0f, -1f, gloss = 0.25f)
        // The mains inlet sits near the panel's bottom (see MachineKit.paintRear); its cable drops
        // to the floor and trails off a little way behind, towards the wall socket.
        val px = xb - w * (if (tall) 0.4f else 0.25f)
        val py = y1 - hgt * (if (tall) 0.86f else 0.72f)
        // In the cabinet's own dark paint, which it already draws with: no extra draw call.
        val cable = art.darkPaint.full
        b.capsule(px, py, zf - 0.4f, px - 0.6f, py * 0.4f, zf - 2.2f, 0.45f, cable, slices = 5, gloss = 0.3f)
        b.capsule(px - 0.6f, py * 0.4f, zf - 2.2f, px - 1.4f, 0.45f, zf - 2.8f, 0.45f, cable, slices = 5, gloss = 0.3f)
        b.capsule(px - 1.4f, 0.45f, zf - 2.8f, px - 3f, 0.45f, zf - 6f, 0.45f, cable, slices = 5, gloss = 0.3f)
    }

    /** A face at height [y] looking down, closing the underside of a part hung above eye level. */
    fun underside(xa: Float, xb: Float, za: Float, zb: Float, y: Float, region: Region = art.darkPaint.full) {
        b.quad(xa, y, zb, xb, y, zb, xb, y, za, xa, y, za, region, 0f, -1f, 0f)
    }

    /** Glass front and sides (and back, if [back]) of a case. */
    fun glassBox(xa: Float, ya: Float, za: Float, xb: Float, yb: Float, zb: Float, back: Boolean = false) {
        val g = HallArt.glass.full
        b.quad(xa, yb, zb, xb, yb, zb, xb, ya, zb, xa, ya, zb, g, 0f, 0f, 1f, blend = Blend.ALPHA, cull = false, gloss = 1f)
        b.quad(xa, yb, za, xa, yb, zb, xa, ya, zb, xa, ya, za, g, -1f, 0f, 0f, blend = Blend.ALPHA, cull = false, gloss = 1f)
        b.quad(xb, yb, zb, xb, yb, za, xb, ya, za, xb, ya, zb, g, 1f, 0f, 0f, blend = Blend.ALPHA, cull = false, gloss = 1f)
        if (back) b.quad(xb, yb, za, xa, yb, za, xa, ya, za, xb, ya, za, g, 0f, 0f, -1f, blend = Blend.ALPHA, cull = false, gloss = 1f)
    }

    /** Four chrome corner posts. */
    fun posts(xa: Float, xb: Float, za: Float, zb: Float, ya: Float, yb: Float, t: Float = 1.4f) {
        val m = HallArt.chrome.full
        val f = BoxFaces(front = m, back = m, left = m, right = m, top = m, gloss = 0.9f)
        b.box(xa, ya, za, xa + t, yb, za + t, f)
        b.box(xb - t, ya, za, xb, yb, za + t, f)
        b.box(xa, ya, zb - t, xa + t, yb, zb, f)
        b.box(xb - t, ya, zb - t, xb, yb, zb, f)
    }

    /**
     * Makes this copy's live attract screen and registers it so the hall repaints it while the
     * cabinet is in view. Map `liveScreen().texture.full` onto a quad (emissive ~1.1).
     */
    fun liveScreen(): LiveScreen = LiveScreen(art, seed).also { unit.attachScreen(it) }
}
