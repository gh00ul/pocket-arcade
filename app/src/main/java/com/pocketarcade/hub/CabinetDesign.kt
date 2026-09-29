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

    /** Adds a coloured point light ([color] is ARGB). Keep to 2 or 3 per cabinet. */
    fun light(x: Float, y: Float, z: Float, color: Int, radius: Float, intensity: Float) {
        unit.lights += PointLight(x, y, z, (color shr 16 and 255) / 255f, (color shr 8 and 255) / 255f, (color and 255) / 255f, radius, intensity)
    }

    /** A row of [count] chase bulbs from [xa] to [xb] at height [y], depth [z]. */
    fun bulbRow(xa: Float, xb: Float, y: Float, z: Float, count: Int) {
        for (k in 0 until count) unit.addBulb(xa + (xb - xa) * (k + 0.5f) / count, y, z)
    }

    /** Side panels with printed art and lit T-molding along their front edges. */
    fun sidePanels(top: Float, depthFront: Float = z1, thick: Float = 1.8f) {
        val side = art.sideArt.full
        val inner = art.bodyPaint.full
        val dark = art.darkPaint.full
        val trim = art.trimTex.full
        b.box(x0, 0f, z0, x0 + thick, top, depthFront, BoxFaces(left = side, right = inner, top = dark, back = dark, front = trim, frontEmissive = 0.9f, gloss = 0.35f))
        b.box(x1 - thick, 0f, z0, x1, top, depthFront, BoxFaces(right = side, left = inner, top = dark, back = dark, front = trim, frontEmissive = 0.9f, gloss = 0.35f))
    }

    /**
     * The lit marquee sign: its face leans back a little so it catches the eye from the hall's
     * high camera while still reading square-on from a kid's eye height in front of it, a backlit
     * topper with the game's emblem and short name covers its top (the part of a cabinet the hall
     * view sees most of), a lit trim edge runs along the top of the face and a row of chase bulbs
     * sits on it.
     */
    fun marqueeBox(xa: Float, xb: Float, y0: Float, y1: Float, za: Float, zb: Float) {
        val dark = art.darkPaint.full
        val hgt = y1 - y0
        // A gentle lean: enough to catch the hall camera, still square-on to a kid in front of it.
        val lean = minOf(hgt * 0.22f, (zb - za) * 0.45f)
        val zt = zb - lean
        val len = sqrt(hgt * hgt + lean * lean)
        b.quad(xa, y1, zt, xb, y1, zt, xb, y0, zb, xa, y0, zb, art.marquee.full, 0f, lean / len, hgt / len, emissive = 1.25f)
        b.quad(xa, y1, za, xb, y1, za, xb, y1, zt, xa, y1, zt, art.topper.full, 0f, 1f, 0f, emissive = 0.9f, gloss = 0.5f)
        b.quad(xa, y1, za, xa, y1, zt, xa, y0, zb, xa, y0, za, dark, -1f, 0f, 0f, gloss = 0.35f)
        b.quad(xb, y1, zt, xb, y1, za, xb, y0, za, xb, y0, zb, dark, 1f, 0f, 0f, gloss = 0.35f)
        b.quad(xb, y1, za, xa, y1, za, xa, y0, za, xb, y0, za, dark, 0f, 0f, -1f)
        b.quad(xa, y0, zb, xb, y0, zb, xb, y0, za, xa, y0, za, dark, 0f, -1f, 0f)
        // A lit trim edge along the top of the face and down its sides.
        val trim = art.trimTex.full
        b.box(xa - 0.25f, y1 - 0.3f, zt - 0.5f, xb + 0.25f, y1 + 0.35f, zt + 0.35f, BoxFaces(front = trim, top = trim, left = trim, right = trim, frontEmissive = 1.1f, topEmissive = 1.1f))
        b.quad(xa - 0.26f, y1, zt - 0.4f, xa - 0.26f, y1, zt + 0.2f, xa - 0.26f, y0, zb + 0.2f, xa - 0.26f, y0, zb - 0.4f, trim, -1f, 0f, 0f, emissive = 1f)
        b.quad(xb + 0.26f, y1, zt + 0.2f, xb + 0.26f, y1, zt - 0.4f, xb + 0.26f, y0, zb - 0.4f, xb + 0.26f, y0, zb + 0.2f, trim, 1f, 0f, 0f, emissive = 1f)
        bulbRow(xa + 1f, xb - 1f, y1 + 0.9f, zt - 0.1f, ((xb - xa) / 3.2f).toInt())
    }

    /**
     * A coin door [w] wide, its bottom at [y0], on a face at depth [z] facing +z: a steel door
     * with a chrome frame and two coin slots that glow red, the way real ones are lit.
     */
    fun coinDoor(x: Float, y0: Float, z: Float, w: Float = 8f) {
        val hgt = w * 1.25f
        val chrome = HallArt.chrome.full
        b.box(x - w / 2f - 0.4f, y0 - 0.4f, z, x + w / 2f + 0.4f, y0 + hgt + 0.4f, z + 0.3f, BoxFaces(front = chrome, top = chrome, left = chrome, right = chrome, gloss = 0.9f))
        b.quad(x - w / 2f, y0 + hgt, z + 0.32f, x + w / 2f, y0 + hgt, z + 0.32f, x + w / 2f, y0, z + 0.32f, x - w / 2f, y0, z + 0.32f, art.coinDoor.full, 0f, 0f, 1f, gloss = 0.7f)
        // The slots' lit inserts, where the painted door has its dark plates.
        val slot = HallArt.solid(0xFFFF3B30.toInt()).full
        for (k in 0 until 2) {
            val sx = x - w / 2f + w * (36f + k * 56f) / 128f
            val sw = w * 12f / 128f
            val top = y0 + hgt * (1f - 26f / 160f)
            val bot = y0 + hgt * (1f - 64f / 160f)
            b.quad(sx - sw, top, z + 0.36f, sx + sw, top, z + 0.36f, sx + sw, bot, z + 0.36f, sx - sw, bot, z + 0.36f, slot, 0f, 0f, 1f, emissive = 1.7f)
        }
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
