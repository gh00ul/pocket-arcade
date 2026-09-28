package com.pocketarcade.games.fishing

import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.hub.CabinetBox
import com.pocketarcade.hub.CabinetBuild
import com.pocketarcade.hub.CabinetDesign
import com.pocketarcade.hub.HallArt
import kotlin.math.cos
import kotlin.math.sin

/**
 * Placeholder fishing cabinet: a big round tub of glowing water with rods along the front rim,
 * a sign on a post at the back, and fish circling under the surface in attract mode.
 */
object FishingCabinet : CabinetDesign {
    override val width = 62f
    override val depth = 62f
    override val height = 60f
    override val focusHeight = 46f
    override val focusSetBack = depth - 10f
    override val screenUnits = 24 to 18

    private const val WATER_Y = 15f

    private val fish: Model by lazy {
        ModelBuilder().capsule(-2.5f, 0f, 0f, 2.5f, 0f, 0f, 1.2f, HallArt.paint(0xFFFF9A3C.toInt(), 0.3f, 0.7f).full).build()
    }

    override fun build(c: CabinetBuild) {
        val b = c.b
        val art = c.art
        val r = minOf(c.x1 - c.x0, c.z1 - c.z0) / 2f - 1f
        val cx = c.cx
        val cz = c.cz
        // The tub, its water and a padded rim.
        b.cylinder(cx, cz, 0f, WATER_Y + 3f, r, 28, art.sideArt.full, gloss = 0.3f)
        b.disc(cx, cz, WATER_Y, r - 1f, 28, HallArt.solid(0xFF1A5AA0.toInt()).full, emissive = 0.8f)
        b.torus(cx, WATER_Y + 3f, cz, r, 1.2f, art.trimTex.full, segments = 28, sides = 6, gloss = 0.5f)
        // Rods along the front half of the rim, leaning out over the water.
        val rod = HallArt.darkMetal.full
        for (k in 0 until 4) {
            val a = 0.5f + k * 0.7f
            val px = cx + cos(a) * r
            val pz = cz + sin(a) * r
            b.capsule(px, WATER_Y + 3f, pz, px + (cx - px) * 0.4f, WATER_Y + 22f, pz + (cz - pz) * 0.4f, 0.4f, rod)
        }
        // Sign on a post at the back: live screen under the marquee.
        val postZ = c.z0 + 6f
        b.cylinder(cx, postZ, WATER_Y, c.h - 26f, 1.5f, 10, HallArt.chrome.full, gloss = 0.9f)
        val dark = art.darkPaint.full
        b.box(cx - 14f, c.h - 30f, postZ - 3f, cx + 14f, c.h - 12f, postZ + 2f, BoxFaces(front = art.bezel.full, left = dark, right = dark, top = dark, back = dark))
        val live = c.liveScreen()
        b.quad(cx - 12.5f, c.h - 13.5f, postZ + 2.15f, cx + 12.5f, c.h - 13.5f, postZ + 2.15f, cx + 12.5f, c.h - 28.5f, postZ + 2.15f, cx - 12.5f, c.h - 28.5f, postZ + 2.15f, live.texture.full, 0f, 0f, 1f, emissive = 1.1f)
        c.marqueeBox(cx - 16f, cx + 16f, c.h - 12f, c.h, postZ - 3f, postZ + 3f)
        b.quad(cx - r, WATER_Y + 0.3f, cz - r, cx + r, WATER_Y + 0.3f, cz - r, cx + r, WATER_Y + 0.3f, cz + r, cx - r, WATER_Y + 0.3f, cz + r, HallArt.glow.full, 0f, 1f, 0f, blend = Blend.ADD, emissive = 1f, tint = 0xFF39E6F2.toInt())
        c.light(cx, WATER_Y + 10f, cz, 0xFF4DB8FF.toInt(), 60f, 1f)
    }

    override fun animate(r: Renderer3D, c: CabinetBox, t: Float) {
        val phase = c.phase(t)
        val rad = (c.x1 - c.x0) / 2f - 8f
        for (k in 0 until 3) {
            val a = phase * (0.5f + k * 0.15f) + k * 2.1f
            fish.draw(r, Blend.OPAQUE, xf = c.xf.set(c.cx + cos(a) * rad * (0.4f + k * 0.2f), WATER_Y + 1f, c.cz + sin(a) * rad * (0.4f + k * 0.2f), yaw = -a))
        }
    }
}
