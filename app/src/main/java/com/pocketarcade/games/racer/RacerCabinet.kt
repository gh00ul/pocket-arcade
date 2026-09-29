package com.pocketarcade.games.racer

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.hub.CabinetBuild
import com.pocketarcade.hub.CabinetDesign
import com.pocketarcade.hub.HallArt
import com.pocketarcade.hub.MachineKit

/**
 * Turbo Racer's hall cabinet: a sit-down racing pod. A tall monitor housing with a chequered
 * sun visor over a wide screen, a dash with lit speedo and rev dials, a steering wheel, gear
 * lever and pedals, and a bucket seat on a floor pan that glows underneath.
 */
object RacerCabinet : CabinetDesign {
    override val width = 34f
    override val depth = 56f
    override val height = 70f
    override val focusHeight = 44f
    override val focusSetBack = depth - 12f
    override val screenUnits = 26 to 18

    private val wheel: Model by lazy {
        ModelBuilder()
            .torus(0f, 0f, 0f, 4.8f, 0.8f, MachineKit.rubber.full, segments = 18, sides = 6, gloss = 0.3f)
            .capsule(0f, 0f, 0f, 0f, 0f, -3f, 0.5f, HallArt.darkMetal.full)
            .capsule(-4.4f, 0f, 0f, 4.4f, 0f, 0f, 0.35f, HallArt.chrome.full, gloss = 0.8f)
            .build()
    }

    private val seat: BoxFaces by lazy {
        val leather = MachineKit.seatLeather.full
        BoxFaces(front = leather, left = leather, right = leather, top = leather, back = leather, gloss = 0.35f)
    }
    /**
     * A high-backed racing bucket seat, one per pod number: leather in front, and on its back a
     * lit numbered plate under a light bar, tall enough to show over the machines in front of
     * the racers from the hall camera.
     */
    private fun seatBack(n: Int, glow: Int, trim: Int): Model = seatBacks.getOrPut(n * 31 + glow) {
        val leather = MachineKit.seatLeather.full
        val bar = HallArt.solid(trim).full
        val lamp = HallArt.solid(glow).full
        ModelBuilder()
            .box(-8f, 0f, -1.8f, 8f, 32f, 1.8f, BoxFaces(back = leather, left = leather, right = leather, top = leather, front = RacerArt.seatPlate(n).full, frontEmissive = 1.05f, gloss = 0.35f))
            // Side wings round the shoulders.
            .box(-9.5f, 12f, -3.5f, -8f, 30f, 1.8f, BoxFaces(front = leather, left = leather, right = leather, top = bar, back = leather, topEmissive = 1f, gloss = 0.35f))
            .box(8f, 12f, -3.5f, 9.5f, 30f, 1.8f, BoxFaces(front = leather, left = leather, right = leather, top = bar, back = leather, topEmissive = 1f, gloss = 0.35f))
            // The light bar across the top.
            .box(-9.5f, 32f, -2.2f, 9.5f, 34.2f, 2.2f, BoxFaces(front = lamp, top = lamp, left = lamp, right = lamp, back = lamp, frontEmissive = 1.8f, topEmissive = 1.6f))
            .build()
    }
    private val seatBacks = HashMap<Int, Model>()

    override fun build(c: CabinetBuild) {
        val b = c.b
        val art = c.art
        val x0 = c.x0
        val x1 = c.x1
        val z0 = c.z0
        val z1 = c.z1
        val h = c.h
        val cx = c.cx
        val cabZ = z0 + 26f
        val st = 1.8f
        val ix0 = x0 + st
        val ix1 = x1 - st
        val dark = art.darkPaint.full
        val side = art.sideArt.full
        val inner = art.bodyPaint.full
        val trim = art.trimTex.full
        val metal = HallArt.darkMetal.full
        val glow = art.glowTex.full

        // Monitor housing: side panels with the printed art, lower body, bezel and the screen.
        b.box(x0, 0f, z0, x0 + st, h, cabZ + 8f, BoxFaces(left = side, right = inner, top = dark, back = dark, front = trim, frontEmissive = 0.9f, gloss = 0.35f))
        b.box(x1 - st, 0f, z0, x1, h, cabZ + 8f, BoxFaces(right = side, left = inner, top = dark, back = dark, front = trim, frontEmissive = 0.9f, gloss = 0.35f))
        b.box(ix0, 0f, z0, ix1, 26f, cabZ, BoxFaces(front = art.kick.full, top = dark, back = dark))
        b.box(ix0, 26f, z0, ix1, h - 10f, cabZ - 2f, BoxFaces(front = art.bezel.full, top = dark, back = dark, gloss = 0.6f))
        val live = c.liveScreen()
        b.quad(ix0 + 1.2f, 50f, cabZ - 1.85f, ix1 - 1.2f, 50f, cabZ - 1.85f, ix1 - 1.2f, 30f, cabZ - 1.85f, ix0 + 1.2f, 30f, cabZ - 1.85f, live.texture.full, 0f, 0f, 1f, emissive = 1.15f)
        // Sun visor over the screen, chequered along its edge with a neon strip under it.
        b.box(ix0, 53f, cabZ - 2f, ix1, 55.5f, cabZ + 6f, BoxFaces(front = RacerArt.checker.full, top = dark, left = dark, right = dark, frontEmissive = 0.5f))
        b.quad(ix0, 53f, cabZ + 6.05f, ix1, 53f, cabZ + 6.05f, ix1, 52.3f, cabZ + 6.05f, ix0, 52.3f, cabZ + 6.05f, glow, 0f, 0f, 1f, emissive = 1.4f)
        c.underside(ix0, ix1, cabZ - 2f, cabZ + 6f, 53f)
        c.marqueeBox(ix0, ix1, h - 10f, h, z0, cabZ + 2f)
        c.rearPanel(ix0, ix1, 1f, h - 10f)

        // Dash with the lit dials, steering wheel, gear lever and pedals.
        b.box(ix0, 17f, cabZ, ix1, 27f, cabZ + 7f, BoxFaces(front = RacerArt.dash.full, top = metal, frontEmissive = 0.8f, gloss = 0.5f))
        b.add(wheel, c.xf.set(cx, 30f, cabZ + 8f, pitch = -1.05f))
        b.box(ix1 - 6f, 0f, cabZ + 12f, ix1 - 1f, 13f, cabZ + 19f, BoxFaces(front = dark, left = inner, right = inner, top = metal))
        b.capsule(ix1 - 3.5f, 13f, cabZ + 15.5f, ix1 - 3.5f, 19f, cabZ + 14.5f, 0.45f, HallArt.chrome.full, gloss = 0.8f)
        b.sphere(ix1 - 3.5f, 19.6f, cabZ + 14.4f, 1.3f, art.glowTex.full, slices = 10, stacks = 6, gloss = 0.6f)
        b.box(cx - 5f, 1.5f, cabZ + 3f, cx - 2f, 3f, cabZ + 7f, BoxFaces(top = metal, front = metal))
        b.box(cx + 2f, 1.5f, cabZ + 3f, cx + 5f, 3f, cabZ + 7f, BoxFaces(top = metal, front = metal))

        // Floor pan joining the pod together, glowing underneath.
        b.box(x0 + 1f, 0.2f, cabZ, x1 - 1f, 1.5f, z1, BoxFaces(front = trim, left = trim, right = trim, top = MachineKit.rubber.full, frontEmissive = 0.9f))
        b.quad(x0, 0.1f, cabZ - 2f, x1, 0.1f, cabZ - 2f, x1, 0.1f, z1 + 2f, x0, 0.1f, z1 + 2f, TexKit.glow.full, 0f, 1f, 0f, blend = Blend.ADD, emissive = 1f, tint = art.glow)

        // Bucket seat with side bolsters, facing the screen.
        b.box(cx - 9f, 1.5f, z1 - 19f, cx + 9f, 10f, z1 - 3f, BoxFaces(front = art.kick.full, left = side, right = side, top = dark, back = dark))
        c.coinDoor(cx, 2.4f, z1 - 3f, 5.6f)
        b.box(cx - 7f, 10f, z1 - 18f, cx + 7f, 13f, z1 - 4f, seat)
        b.box(cx - 9f, 10f, z1 - 18f, cx - 7f, 16f, z1 - 4f, BoxFaces(front = inner, left = inner, right = inner, top = trim, back = inner, gloss = 0.4f))
        b.box(cx + 7f, 10f, z1 - 18f, cx + 9f, 16f, z1 - 4f, BoxFaces(front = inner, left = inner, right = inner, top = trim, back = inner, gloss = 0.4f))
        b.add(seatBack(c.variant + 1, art.glow, art.trim), c.xf.set(cx, 11f, z1 - 4.5f, pitch = 0.12f))

        c.light(cx, 38f, cabZ + 9f, art.glow, 52f, 1f)
        c.light(cx, 3f, cabZ + 16f, Pal.CYAN, 34f, 0.7f)
    }
}
