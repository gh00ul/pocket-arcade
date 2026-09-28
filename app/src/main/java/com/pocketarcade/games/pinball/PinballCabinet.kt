package com.pocketarcade.games.pinball

import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.hub.CabinetBuild
import com.pocketarcade.hub.CabinetDesign
import com.pocketarcade.hub.HallArt

/**
 * Placeholder pinball cabinet: a table on chrome legs with a glass-topped playfield sloping up
 * to a lit backbox, flipper buttons on the sides and a plunger at the front right.
 */
object PinballCabinet : CabinetDesign {
    override val width = 28f
    override val depth = 58f
    override val height = 74f
    override val focusHeight = 54f
    override val focusSetBack = depth - 12f
    override val screenUnits = 24 to 18

    override fun build(c: CabinetBuild) {
        val b = c.b
        val art = c.art
        val dark = art.darkPaint.full
        val side = art.sideArt.full
        val metal = HallArt.chrome.full
        val legs = BoxFaces(front = metal, left = metal, right = metal, back = metal, gloss = 0.9f)
        val backZ = c.z0 + 10f
        // Legs, then the body: its top slopes from 30 at the front to 36 at the backbox.
        for (lx in floatArrayOf(c.x0, c.x1 - 2f)) {
            b.box(lx, 0f, backZ + 2f, lx + 2f, 22f, backZ + 4f, legs)
            b.box(lx, 0f, c.z1 - 4f, lx + 2f, 20f, c.z1 - 2f, legs)
        }
        b.box(c.x0, 20f, backZ, c.x1, 30f, c.z1, BoxFaces(front = art.kick.full, left = side, right = side, back = dark, gloss = 0.3f))
        b.quad(c.x0, 36f, backZ, c.x0, 30f, c.z1, c.x0, 30f, backZ, c.x0, 30f, backZ, side, -1f, 0f, 0f, cull = false)
        b.quad(c.x1, 30f, backZ, c.x1, 30f, c.z1, c.x1, 36f, backZ, c.x1, 36f, backZ, side, 1f, 0f, 0f, cull = false)
        b.quad(c.x0 + 1f, 35.5f, backZ, c.x1 - 1f, 35.5f, backZ, c.x1 - 1f, 30.5f, c.z1 - 1f, c.x0 + 1f, 30.5f, c.z1 - 1f, art.sideArt.full, 0f, 0.99f, 0.12f, emissive = 0.5f)
        b.quad(c.x0, 36.2f, backZ, c.x1, 36.2f, backZ, c.x1, 30.7f, c.z1, c.x0, 30.7f, c.z1, HallArt.glass.full, 0f, 0.99f, 0.12f, blend = Blend.ALPHA, cull = false, gloss = 1f)
        // Backbox with the backglass (live screen) and the marquee on top.
        b.box(c.x0, 30f, c.z0, c.x1, c.h - 12f, backZ, BoxFaces(front = art.bezel.full, left = side, right = side, top = dark, back = dark, gloss = 0.5f))
        val live = c.liveScreen()
        b.quad(c.x0 + 2f, c.h - 14f, backZ + 0.15f, c.x1 - 2f, c.h - 14f, backZ + 0.15f, c.x1 - 2f, 40f, backZ + 0.15f, c.x0 + 2f, 40f, backZ + 0.15f, live.texture.full, 0f, 0f, 1f, emissive = 1.15f)
        c.marqueeBox(c.x0, c.x1, c.h - 12f, c.h, c.z0, backZ + 1f)
        // Flipper buttons and the plunger.
        val button = HallArt.solid(art.glow).full
        b.box(c.x0 - 0.8f, 25f, c.z1 - 8f, c.x0, 27f, c.z1 - 6f, BoxFaces(left = button, front = button, top = button, frontEmissive = 0.8f))
        b.box(c.x1, 25f, c.z1 - 8f, c.x1 + 0.8f, 27f, c.z1 - 6f, BoxFaces(right = button, front = button, top = button, frontEmissive = 0.8f))
        b.capsule(c.x1 - 4f, 26f, c.z1, c.x1 - 4f, 26f, c.z1 + 3f, 0.6f, metal)
        c.light(c.cx, 44f, c.cz, art.glow, 50f, 0.9f)
    }
}
