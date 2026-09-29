package com.pocketarcade.games.pinball

import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.hub.CabinetBox
import com.pocketarcade.hub.CabinetBuild
import com.pocketarcade.hub.CabinetDesign
import com.pocketarcade.hub.HallArt
import com.pocketarcade.hub.MachineKit
import com.pocketarcade.hub.beveledBox
import kotlin.math.PI
import kotlin.math.sin

/**
 * Star Flipper's hall cabinet: a pinball table on four chrome legs, its glass-topped playfield
 * sloping up to a tall backbox with the lit backglass (the live dot-matrix display, in a bezel
 * under glass), a speaker panel, a marquee with chase bulbs, a lockdown bar, sculpted flipper
 * buttons on the sides, the plunger, a coin door and a ticket dispenser. Lit inserts glow on the
 * playfield under the glass. In attract mode a ball wanders round the playfield.
 */
object PinballCabinet : CabinetDesign {
    override val width = 28f
    override val depth = 58f
    override val height = 76f
    override val focusHeight = 54f
    override val focusSetBack = depth - 14f
    override val screenUnits = 40 to 30

    /** Underside of the body, where the legs end. */
    private const val BODY_BOTTOM = 21f
    private const val BODY_TOP = 30f
    /** Top of the side walls at the front and at the backbox (the table slopes up to the back). */
    private const val FRONT_TOP = 31.5f
    private const val BACK_TOP = 37.5f
    /** Playfield below the side walls' top; the glass just under it. */
    private const val FIELD_DROP = 2.5f
    private const val GLASS_DROP = 0.2f
    /** Depth of the backbox from the cabinet's back. */
    private const val BACKBOX_D = 11f
    /** How brightly the lit inserts on the playfield glow. */
    private const val INSERT_GLOW = 1.0f
    /** The lit inserts: where on the playfield (across, down from the backbox, as fractions), how big, which colour. */
    private val INSERTS = arrayOf(
        floatArrayOf(0.30f, 0.06f, 0.9f, 0f), floatArrayOf(0.50f, 0.06f, 0.9f, 0f), floatArrayOf(0.70f, 0.06f, 0.9f, 0f),
        floatArrayOf(0.50f, 0.25f, 1.0f, 1f), floatArrayOf(0.32f, 0.31f, 0.9f, 1f), floatArrayOf(0.68f, 0.31f, 0.9f, 1f),
        floatArrayOf(0.16f, 0.72f, 0.8f, 2f), floatArrayOf(0.84f, 0.72f, 0.8f, 2f),
        floatArrayOf(0.20f, 0.80f, 0.8f, 2f), floatArrayOf(0.80f, 0.80f, 0.8f, 2f),
        floatArrayOf(0.40f, 0.90f, 0.8f, 0f), floatArrayOf(0.60f, 0.90f, 0.8f, 0f),
    )
    private val INSERT_COLORS = intArrayOf(0xFFFFD84D.toInt(), 0xFFFF4FA8.toInt(), 0xFF39E6F2.toInt())

    private fun backZ(c: CabinetBox) = c.z0 + BACKBOX_D

    /** Top of the side walls at depth [z]. */
    private fun wallTop(c: CabinetBox, z: Float): Float {
        val bz = backZ(c)
        return BACK_TOP + (FRONT_TOP - BACK_TOP) * ((z - bz) / (c.z1 - bz)).coerceIn(0f, 1f)
    }

    override fun build(c: CabinetBuild) {
        val b = c.b
        val art = c.art
        val x0 = c.x0
        val x1 = c.x1
        val z0 = c.z0 + 2f
        val z1 = c.z1
        val cx = c.cx
        val bz = backZ(c)
        val dark = art.darkPaint.full
        val side = art.sideArt.full
        val sideLong = art.sideArtLong.full
        val metal = HallArt.chrome.full
        val chrome = BoxFaces(front = metal, left = metal, right = metal, back = metal, top = metal, gloss = 0.9f)
        // Legs with levelling feet.
        for (lx in floatArrayOf(x0 + 0.4f, x1 - 2.4f)) {
            for (lz in floatArrayOf(z0 + 1f, z1 - 3f)) {
                b.box(lx, 0.6f, lz, lx + 2f, BODY_BOTTOM, lz + 2f, chrome)
                b.box(lx - 0.3f, 0f, lz - 0.3f, lx + 2.3f, 0.6f, lz + 2.3f, BoxFaces(front = dark, left = dark, right = dark, back = dark, top = dark))
            }
        }
        // The body, then the side walls rising towards the backbox.
        b.beveledBox(x0, BODY_BOTTOM, z0, x1, BODY_TOP, z1, BoxFaces(front = art.kick.full, left = sideLong, right = sideLong, back = dark, top = dark, gloss = 0.3f))
        c.tMoulding(x0 - 0.15f, x0 + 1.6f, BODY_BOTTOM, FRONT_TOP, z1)
        c.tMoulding(x1 - 1.6f, x1 + 0.15f, BODY_BOTTOM, FRONT_TOP, z1)
        b.quad(x0, BACK_TOP, bz, x0, FRONT_TOP, z1, x0, BODY_TOP, z1, x0, BODY_TOP, bz, sideLong, -1f, 0f, 0f, cull = false, gloss = 0.3f)
        b.quad(x1, FRONT_TOP, z1, x1, BACK_TOP, bz, x1, BODY_TOP, bz, x1, BODY_TOP, z1, sideLong, 1f, 0f, 0f, cull = false, gloss = 0.3f)
        // Chrome side rails along the walls' sloping tops.
        b.quad(x0 - 0.3f, BACK_TOP + 0.3f, bz, x0 + 1f, BACK_TOP + 0.3f, bz, x0 + 1f, FRONT_TOP + 0.3f, z1, x0 - 0.3f, FRONT_TOP + 0.3f, z1, metal, 0f, 1f, 0.1f, cull = false, gloss = 0.9f)
        b.quad(x1 - 1f, BACK_TOP + 0.3f, bz, x1 + 0.3f, BACK_TOP + 0.3f, bz, x1 + 0.3f, FRONT_TOP + 0.3f, z1, x1 - 1f, FRONT_TOP + 0.3f, z1, metal, 0f, 1f, 0.1f, cull = false, gloss = 0.9f)
        // Front strip under the lockdown bar, and the bar itself.
        b.quad(x0, FRONT_TOP, z1, x1, FRONT_TOP, z1, x1, BODY_TOP, z1, x0, BODY_TOP, z1, art.trimTex.full, 0f, 0f, 1f, emissive = MachineKit.glowFor(art.trim, 0.8f))
        b.box(x0 - 0.2f, FRONT_TOP, z1 - 2.2f, x1 + 0.2f, FRONT_TOP + 1.2f, z1 + 0.3f, chrome)
        // The playfield under the glass.
        val fb = BACK_TOP - FIELD_DROP
        val ff = FRONT_TOP - FIELD_DROP
        b.quad(x0 + 1f, fb, bz, x1 - 1f, fb, bz, x1 - 1f, ff, z1 - 2.2f, x0 + 1f, ff, z1 - 2.2f, PinballArt.cabinetPlayfield.full, 0f, 0.99f, 0.12f, emissive = 0.55f)
        // Lit inserts glowing on the playfield, following its slope.
        val fLen = z1 - 2.2f - bz
        for (ins in INSERTS) {
            val iz = bz + ins[1] * fLen
            val ix = x0 + 1f + ins[0] * (x1 - x0 - 2f)
            val iy = fb + (ff - fb) * ins[1] + 0.07f
            b.disc(ix, iz, iy, ins[2], 6, HallArt.solid(INSERT_COLORS[ins[3].toInt()]).full, emissive = INSERT_GLOW)
        }
        val gb = BACK_TOP - GLASS_DROP
        val gf = FRONT_TOP - GLASS_DROP
        b.quad(x0 + 1f, gb, bz, x1 - 1f, gb, bz, x1 - 1f, gf, z1 - 2.2f, x0 + 1f, gf, z1 - 2.2f, HallArt.glass.full, 0f, 0.99f, 0.12f, blend = Blend.ALPHA, cull = false, gloss = 1f)
        // Coin door with its two lit slots and a ticket dispenser on the front of the body.
        c.coinDoor(cx - 5f, 21.6f, z1, 6f, cup = false)
        c.ticketDispenser(cx + 6.5f, 23f, z1 + 0.5f, 5.5f)
        // Sculpted flipper buttons on the sides, and the plunger at the front right.
        b.add(MachineKit.button(art.glow, 1.3f), c.xf.set(x0 - 0.3f, 27.8f, z1 - 6f, roll = PI.toFloat() / 2f))
        b.add(MachineKit.button(art.glow, 1.3f), c.xf.set(x1 + 0.3f, 27.8f, z1 - 6f, roll = -PI.toFloat() / 2f))
        b.capsule(x1 - 4f, 28f, z1, x1 - 4f, 28f, z1 + 3.2f, 0.45f, metal, slices = 6)
        b.cylinder(x1 - 4f, z1 + 3.4f, 27.1f, 28.9f, 0.9f, 8, HallArt.solid(0xFFFF4D4D.toInt()).full, HallArt.solid(0xFFFF4D4D.toInt()).full)
        // The backbox: a speaker panel, then the backglass (the live display) and its bulbs.
        val top = c.h - 10f
        b.box(x0, BODY_TOP, z0, x1, top, bz, BoxFaces(front = art.bezel.full, left = side, right = side, top = dark, back = dark, gloss = 0.5f))
        b.quad(x0 + 1.2f, 43f, bz + 0.12f, x1 - 1.2f, 43f, bz + 0.12f, x1 - 1.2f, 39f, bz + 0.12f, x0 + 1.2f, 39f, bz + 0.12f, MachineKit.speakerStrip.full, 0f, 0f, 1f, gloss = 0.4f)
        val live = c.liveScreen()
        b.quad(x0 + 1.5f, top - 1.5f, bz + 0.15f, x1 - 1.5f, top - 1.5f, bz + 0.15f, x1 - 1.5f, 45.5f, bz + 0.15f, x0 + 1.5f, 45.5f, bz + 0.15f, live.texture.full, 0f, 0f, 1f, emissive = 1.15f)
        c.screenBezel(x0 + 1.5f, x1 - 1.5f, 45.5f, top - 1.5f, bz + 0.15f, frame = 0.7f, depth = 0.45f)
        c.screenGlass(x0 + 1.5f, x1 - 1.5f, 45.5f, top - 1.5f, bz + 0.22f)
        c.bulbRow(x0 + 1.5f, x1 - 1.5f, 44.4f, bz + 0.6f, 7)
        c.marqueeBox(x0, x1, top, c.h - 2f, z0, bz + 1f)
        c.rearPanel(x0 + 0.3f, x1 - 0.3f, BODY_TOP + 0.5f, top, z0)
        c.light(cx, top - 4f, bz + 7f, art.glow, 48f, 0.9f)
        c.light(cx, 46f, c.cz + 8f, 0xFFB8C8FF.toInt(), 40f, 0.55f)
    }

    /** A ball wandering round the playfield under the glass; drawn every frame, allocation-free. */
    override fun animate(r: Renderer3D, c: CabinetBox, t: Float) {
        val p = c.phase(t)
        val bz = backZ(c)
        val u = 0.5f + 0.4f * sin(p * 1.9f)
        val v = 0.5f + 0.42f * sin(p * 1.13f + 0.7f)
        val x = c.x0 + 2.5f + u * (c.x1 - c.x0 - 5f)
        val z = bz + 1.5f + v * (c.z1 - bz - 5f)
        val y = wallTop(c, z) - FIELD_DROP + 0.95f
        PinballArt.cabinetBall.draw(r, xf = c.xf.set(x, y, z))
    }
}
