package com.pocketarcade.games.fishing

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.hub.CabinetBox
import com.pocketarcade.hub.CabinetBuild
import com.pocketarcade.hub.CabinetDesign
import com.pocketarcade.hub.HallArt
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gone Fishing's hall cabinet: a big round tub of water with fish circling under the surface,
 * a ring of chase bulbs round its padded rim, two rods on chrome posts at the front with their
 * floats bobbing on the water, and a lit sign on a post at the back with the attract screen.
 */
object FishingCabinet : CabinetDesign {
    override val width = 62f
    override val depth = 62f
    override val height = 62f
    override val focusHeight = 44f
    override val focusSetBack = depth - 10f
    override val screenUnits = 24 to 18

    private const val TUB_R = 26f
    private const val RIM_Y = 20f
    private const val WATER_Y = 17f
    private const val FLOOR_Y = 7f
    /** The tub's centre sits this far in front of the cabinet's back. */
    private const val TUB_BACK = 33f
    /** The rods' posts, either side of the front, as an angle off straight ahead. */
    private const val ROD_SPREAD = 0.7f
    private const val POST_H = 27f
    private const val FISH_IN_TUB = 4

    override fun build(c: CabinetBuild) {
        val b = c.b
        val art = c.art
        val cx = c.cx
        val tz = c.z0 + TUB_BACK
        val dark = art.darkPaint.full
        // The tub: a plinth, the printed wall, a tiled inside, pebbles, water and a padded rim.
        b.cylinder(cx, tz, 0f, 2.5f, TUB_R + 1.4f, 32, dark, gloss = 0.2f)
        b.cylinder(cx, tz, 2.5f, RIM_Y, TUB_R, 32, art.sideArt.full, gloss = 0.35f)
        b.cylinder(cx, tz, FLOOR_Y, RIM_Y, TUB_R - 1.6f, 32, FishingArt.tubTiles, inward = true)
        b.disc(cx, tz, FLOOR_Y, TUB_R - 1.6f, 32, FishingArt.tubFloor)
        b.torus(cx, RIM_Y, tz, TUB_R - 0.8f, 1.6f, art.trimTex.full, segments = 32, sides = 6, gloss = 0.5f)
        b.disc(cx, tz, WATER_Y, TUB_R - 1.6f, 32, FishingArt.tubWater, blend = Blend.ALPHA, gloss = 1f)
        // Lily pads floating on top.
        b.disc(cx - 13f, tz - 9f, WATER_Y + 0.15f, 4f, 10, FishingArt.lilyPad)
        b.disc(cx + 15f, tz - 4f, WATER_Y + 0.15f, 3.2f, 10, FishingArt.lilyPad)
        // Chase bulbs round the front of the rim.
        val bulbs = 14
        for (k in 0 until bulbs) {
            val a = PI.toFloat() * (0.08f + 0.84f * k / (bulbs - 1))
            val x = cx + cos(a) * (TUB_R + 0.6f)
            val z = tz + sin(a) * (TUB_R + 0.6f)
            c.bulbRow(x, x, RIM_Y + 1.7f, z, 1)
        }
        // Two rods on chrome posts at the front, leaning out over the water.
        for (side in 0..1) {
            val a = PI.toFloat() / 2f + if (side == 0) -ROD_SPREAD else ROD_SPREAD
            val px = cx + cos(a) * (TUB_R + 3f)
            val pz = tz + sin(a) * (TUB_R + 3f)
            b.cylinder(px, pz, 0f, POST_H, 0.9f, 8, HallArt.chrome.full, top = HallArt.chrome.full, gloss = 0.9f)
            b.torus(px, POST_H - 1f, pz, 1.4f, 0.4f, HallArt.darkMetal.full, segments = 10, sides = 4)
            val tipX = cx + cos(a) * 12f
            val tipZ = tz + sin(a) * 12f
            val rodTex = FishingArt.rod
            b.capsule(px, POST_H - 2f, pz, tipX, POST_H + 10f, tipZ, 0.45f, rodTex)
            b.capsule(px, POST_H - 4f, pz, px + (tipX - px) * 0.08f, POST_H - 1f, pz + (tipZ - pz) * 0.08f, 0.8f, FishingArt.cork)
            // The reel, and the line down to the water.
            b.cylinder(px + (tipX - px) * 0.14f, pz + (tipZ - pz) * 0.14f, POST_H - 1.6f, POST_H + 0.2f, 1.3f, 10, HallArt.darkMetal.full, top = HallArt.chrome.full)
            b.capsule(tipX, POST_H + 10f, tipZ, tipX, WATER_Y + 1.5f, tipZ, 0.1f, TexKit.white.full)
        }
        // The sign post at the back: live screen in a bezel, the lit marquee above.
        val postZ = c.z0 + 3f
        b.cylinder(cx, postZ, 0f, 52f, 1.5f, 10, HallArt.chrome.full, gloss = 0.9f)
        b.box(cx - 13f, 32f, postZ - 1.5f, cx + 13f, 51f, postZ + 2.5f, BoxFaces(front = art.bezel.full, left = dark, right = dark, top = dark, back = dark, gloss = 0.5f))
        val live = c.liveScreen()
        b.quad(cx - 10f, 49f, postZ + 2.6f, cx + 10f, 49f, postZ + 2.6f, cx + 10f, 34f, postZ + 2.6f, cx - 10f, 34f, postZ + 2.6f, live.texture.full, 0f, 0f, 1f, emissive = 1.1f)
        c.marqueeBox(cx - 18f, cx + 18f, 51f, 60f, postZ - 2f, postZ + 3f)
        // Light: the water glows blue, the sign throws a warm light over the tub.
        c.light(cx, RIM_Y + 12f, tz, 0xFF4DD8FF.toInt(), 56f, 1f)
        c.light(cx, 50f, postZ + 14f, 0xFFFFD27A.toInt(), 46f, 0.8f)
    }

    /** Fish circle the tub under the water; the floats bob at the ends of the rods' lines. */
    override fun animate(r: Renderer3D, c: CabinetBox, t: Float) {
        val phase = c.phase(t)
        val cx = c.cx
        val tz = c.z0 + TUB_BACK
        for (k in 0 until FISH_IN_TUB) {
            val dir = if (k % 2 == 0) 1f else -1f
            val a = phase * (0.45f + k * 0.12f) * dir + k * 1.7f
            val rad = 9f + k * 3.6f
            val x = cx + cos(a) * rad
            val z = tz + sin(a) * rad
            // Heading along the circle: the tangent's direction.
            val yaw = kotlin.math.atan2(-sin(a) * dir, cos(a) * dir) + sin(phase * 7f + k) * 0.15f
            FishingArt.fish(k).draw(r, Blend.OPAQUE, xf = c.xf.set(x, WATER_Y - 3f - k * 0.6f, z, yaw = yaw, scale = 0.3f))
        }
        for (side in 0..1) {
            val a = PI.toFloat() / 2f + if (side == 0) -ROD_SPREAD else ROD_SPREAD
            val bx = cx + cos(a) * 12f
            val bz = tz + sin(a) * 12f
            val cycle = (phase * 0.7f + side * 1.9f) % 5f
            val dip = if (cycle > 4.4f) 1.2f else 0f
            val y = WATER_Y + 0.3f + sin(phase * 3f + side) * 0.25f - dip
            FishingArt.bobber.draw(r, Blend.OPAQUE, xf = c.xf2.set(bx, y, bz, scale = 0.32f))
        }
    }
}
