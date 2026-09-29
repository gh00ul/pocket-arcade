package com.pocketarcade.games.pinball

import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.hub.CabinetBox
import com.pocketarcade.hub.CabinetBuild
import com.pocketarcade.hub.CabinetDesign
import com.pocketarcade.hub.HallArt
import com.pocketarcade.hub.beveledBox
import kotlin.math.sin

/**
 * Star Flipper's hall cabinet: a pinball table on four chrome legs, its glass-topped playfield
 * sloping up to a tall backbox with the lit backglass (the live dot-matrix display), a marquee
 * with chase bulbs, a lockdown bar, flipper buttons on the sides, the plunger and a coin door.
 * In attract mode a ball wanders round the playfield under the glass.
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
        b.beveledBox(x0, BODY_BOTTOM, z0, x1, BODY_TOP, z1, BoxFaces(front = art.kick.full, left = side, right = side, back = dark, top = dark, gloss = 0.3f))
        b.quad(x0, BACK_TOP, bz, x0, FRONT_TOP, z1, x0, BODY_TOP, z1, x0, BODY_TOP, bz, side, -1f, 0f, 0f, cull = false, gloss = 0.3f)
        b.quad(x1, FRONT_TOP, z1, x1, BACK_TOP, bz, x1, BODY_TOP, bz, x1, BODY_TOP, z1, side, 1f, 0f, 0f, cull = false, gloss = 0.3f)
        // Chrome side rails along the walls' sloping tops.
        b.quad(x0 - 0.3f, BACK_TOP + 0.3f, bz, x0 + 1f, BACK_TOP + 0.3f, bz, x0 + 1f, FRONT_TOP + 0.3f, z1, x0 - 0.3f, FRONT_TOP + 0.3f, z1, metal, 0f, 1f, 0.1f, cull = false, gloss = 0.9f)
        b.quad(x1 - 1f, BACK_TOP + 0.3f, bz, x1 + 0.3f, BACK_TOP + 0.3f, bz, x1 + 0.3f, FRONT_TOP + 0.3f, z1, x1 - 1f, FRONT_TOP + 0.3f, z1, metal, 0f, 1f, 0.1f, cull = false, gloss = 0.9f)
        // Front strip under the lockdown bar, and the bar itself.
        b.quad(x0, FRONT_TOP, z1, x1, FRONT_TOP, z1, x1, BODY_TOP, z1, x0, BODY_TOP, z1, art.trimTex.full, 0f, 0f, 1f, emissive = 0.8f)
        b.box(x0 - 0.2f, FRONT_TOP, z1 - 2.2f, x1 + 0.2f, FRONT_TOP + 1.2f, z1 + 0.3f, chrome)
        // The playfield under the glass.
        val fb = BACK_TOP - FIELD_DROP
        val ff = FRONT_TOP - FIELD_DROP
        b.quad(x0 + 1f, fb, bz, x1 - 1f, fb, bz, x1 - 1f, ff, z1 - 2.2f, x0 + 1f, ff, z1 - 2.2f, PinballArt.cabinetPlayfield.full, 0f, 0.99f, 0.12f, emissive = 0.55f)
        val gb = BACK_TOP - GLASS_DROP
        val gf = FRONT_TOP - GLASS_DROP
        b.quad(x0 + 1f, gb, bz, x1 - 1f, gb, bz, x1 - 1f, gf, z1 - 2.2f, x0 + 1f, gf, z1 - 2.2f, HallArt.glass.full, 0f, 0.99f, 0.12f, blend = Blend.ALPHA, cull = false, gloss = 1f)
        // Coin door with two lit coin slots.
        b.box(cx - 5f, 22.5f, z1, cx + 5f, 28.5f, z1 + 0.4f, BoxFaces(front = HallArt.darkMetal.full, top = metal, left = metal, right = metal, gloss = 0.6f))
        val slot = HallArt.solid(0xFFFF4D4D.toInt()).full
        b.quad(cx - 3.2f, 27.5f, z1 + 0.45f, cx - 1.2f, 27.5f, z1 + 0.45f, cx - 1.2f, 25.8f, z1 + 0.45f, cx - 3.2f, 25.8f, z1 + 0.45f, slot, 0f, 0f, 1f, emissive = 1.2f)
        b.quad(cx + 1.2f, 27.5f, z1 + 0.45f, cx + 3.2f, 27.5f, z1 + 0.45f, cx + 3.2f, 25.8f, z1 + 0.45f, cx + 1.2f, 25.8f, z1 + 0.45f, slot, 0f, 0f, 1f, emissive = 1.2f)
        // Flipper buttons on the sides, and the plunger at the front right.
        val button = HallArt.solid(art.glow).full
        b.box(x0 - 0.8f, 27f, z1 - 7f, x0, 28.6f, z1 - 5f, BoxFaces(left = button, front = button, top = button, frontEmissive = 0.8f))
        b.box(x1, 27f, z1 - 7f, x1 + 0.8f, 28.6f, z1 - 5f, BoxFaces(right = button, front = button, top = button, frontEmissive = 0.8f))
        b.capsule(x1 - 4f, 28f, z1, x1 - 4f, 28f, z1 + 3.2f, 0.45f, metal)
        b.cylinder(x1 - 4f, z1 + 3.4f, 27.1f, 28.9f, 0.9f, 8, HallArt.solid(0xFFFF4D4D.toInt()).full, HallArt.solid(0xFFFF4D4D.toInt()).full)
        // The backbox: a speaker panel, then the backglass (the live display) and its bulbs.
        val top = c.h - 10f
        b.box(x0, BODY_TOP, z0, x1, top, bz, BoxFaces(front = art.bezel.full, left = side, right = side, top = dark, back = dark, gloss = 0.5f))
        b.quad(x0 + 1.2f, 43f, bz + 0.12f, x1 - 1.2f, 43f, bz + 0.12f, x1 - 1.2f, 39f, bz + 0.12f, x0 + 1.2f, 39f, bz + 0.12f, art.panel.full, 0f, 0f, 1f, emissive = 0.6f)
        val live = c.liveScreen()
        b.quad(x0 + 1.5f, top - 1.5f, bz + 0.15f, x1 - 1.5f, top - 1.5f, bz + 0.15f, x1 - 1.5f, 45.5f, bz + 0.15f, x0 + 1.5f, 45.5f, bz + 0.15f, live.texture.full, 0f, 0f, 1f, emissive = 1.15f)
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
