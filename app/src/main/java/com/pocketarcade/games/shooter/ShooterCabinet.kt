package com.pocketarcade.games.shooter

import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.hub.CabinetBuild
import com.pocketarcade.hub.CabinetDesign
import com.pocketarcade.hub.HallArt

/**
 * Placeholder light-gun cabinet: a tall screen box with a lit marquee, and a control shelf in
 * front with two pistols in holsters.
 */
object ShooterCabinet : CabinetDesign {
    override val width = 44f
    override val depth = 38f
    override val height = 82f
    override val focusHeight = 54f
    override val focusSetBack = 10f
    override val screenUnits = 24 to 18

    /** A pistol lying barrel-back (towards -z), grip down. */
    private fun gun(color: Int): Model {
        val body = HallArt.paint(color, 0.3f, 0.8f).full
        val metal = HallArt.darkMetal.full
        return ModelBuilder()
            .box(-1.2f, 0f, -7f, 1.2f, 2.4f, 1f, BoxFaces(front = body, left = body, right = body, top = body, back = metal, gloss = 0.5f))
            .box(-1f, -3.5f, -0.5f, 1f, 0f, 1.8f, BoxFaces(front = metal, left = metal, right = metal, back = metal))
            .build()
    }

    private val blueGun: Model by lazy { gun(0xFF2F5BE0.toInt()) }
    private val redGun: Model by lazy { gun(0xFFE8323C.toInt()) }

    override fun build(c: CabinetBuild) {
        val b = c.b
        val art = c.art
        val dark = art.darkPaint.full
        val screenZ = c.z0 + 26f
        val shelfY = 34f
        // Screen box, its lit marquee and the live screen.
        b.box(c.x0, 0f, c.z0, c.x1, c.h - 14f, screenZ, BoxFaces(front = art.bezel.full, left = art.sideArt.full, right = art.sideArt.full, top = dark, back = dark, gloss = 0.5f))
        val live = c.liveScreen()
        b.quad(c.x0 + 3f, c.h - 18f, screenZ + 0.15f, c.x1 - 3f, c.h - 18f, screenZ + 0.15f, c.x1 - 3f, shelfY + 8f, screenZ + 0.15f, c.x0 + 3f, shelfY + 8f, screenZ + 0.15f, live.texture.full, 0f, 0f, 1f, emissive = 1.15f)
        c.marqueeBox(c.x0, c.x1, c.h - 14f, c.h, c.z0, screenZ + 2f)
        // Kick panel and the control shelf with the two guns.
        b.box(c.x0 + 2f, 0f, screenZ, c.x1 - 2f, shelfY, c.z1 - 4f, BoxFaces(front = art.kick.full, left = art.sideArt.full, right = art.sideArt.full, gloss = 0.3f))
        b.box(c.x0, shelfY, screenZ, c.x1, shelfY + 3f, c.z1, BoxFaces(front = art.trimTex.full, top = HallArt.darkMetal.full, left = dark, right = dark, frontEmissive = 0.9f, gloss = 0.5f))
        b.add(blueGun, c.xf.set(c.cx - 10f, shelfY + 3f, c.z1 - 4f))
        b.add(redGun, c.xf.set(c.cx + 10f, shelfY + 3f, c.z1 - 4f))
        c.light(c.cx, c.h - 6f, c.z0 + 36f, art.glow, 55f, 0.9f)
    }
}
