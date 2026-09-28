package com.pocketarcade.games.racer

import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.hub.CabinetBuild
import com.pocketarcade.hub.CabinetDesign
import com.pocketarcade.hub.HallArt
import com.pocketarcade.hub.MachineKit

/** Turbo Racer's hall cabinet: a sit-down racer with a big screen, a dash with a steering wheel and a bucket seat. */
object RacerCabinet : CabinetDesign {
    override val width = 32f
    override val depth = 50f
    override val height = 62f
    override val focusHeight = 42f
    override val focusSetBack = depth - 10f
    override val screenUnits = 26 to 18

    private val wheel: Model by lazy {
        ModelBuilder()
            .torus(0f, 0f, 0f, 4.8f, 0.8f, MachineKit.rubber.full, segments = 18, sides = 6, gloss = 0.3f)
            .capsule(0f, 0f, 0f, 0f, 0f, -3f, 0.5f, HallArt.darkMetal.full)
            .build()
    }

    private val seat: BoxFaces by lazy {
        val leather = MachineKit.seatLeather.full
        BoxFaces(front = leather, left = leather, right = leather, top = leather, back = leather, gloss = 0.35f)
    }
    private val seatBack: Model by lazy { ModelBuilder().box(-7f, 0f, -1.8f, 7f, 20f, 1.8f, seat).build() }
    private val headrest: Model by lazy { ModelBuilder().box(-4f, 0f, -1.5f, 4f, 6f, 1.5f, seat).build() }

    override fun build(c: CabinetBuild) {
        val b = c.b
        val art = c.art
        val x0 = c.x0
        val x1 = c.x1
        val z0 = c.z0
        val z1 = c.z1
        val h = c.h
        val cx = c.cx
        val cabZ = z0 + 24f
        val st = 1.8f
        val ix0 = x0 + st
        val ix1 = x1 - st
        val dark = art.darkPaint.full
        val side = art.sideArt.full
        val inner = art.bodyPaint.full
        val trim = art.trimTex.full
        b.box(x0, 0f, z0, x0 + st, h, cabZ + 6f, BoxFaces(left = side, right = inner, top = dark, back = dark, front = trim, frontEmissive = 0.9f, gloss = 0.35f))
        b.box(x1 - st, 0f, z0, x1, h, cabZ + 6f, BoxFaces(right = side, left = inner, top = dark, back = dark, front = trim, frontEmissive = 0.9f, gloss = 0.35f))
        b.box(ix0, 0f, z0, ix1, 22f, cabZ, BoxFaces(front = dark, top = dark, back = dark))
        b.box(ix0, 22f, z0, ix1, h - 10f, cabZ - 2f, BoxFaces(front = art.bezel.full, top = dark, back = dark, gloss = 0.6f))
        val live = c.liveScreen()
        b.quad(ix0 + 1.4f, h - 12f, cabZ - 1.85f, ix1 - 1.4f, h - 12f, cabZ - 1.85f, ix1 - 1.4f, 26f, cabZ - 1.85f, ix0 + 1.4f, 26f, cabZ - 1.85f, live.texture.full, 0f, 0f, 1f, emissive = 1.15f)
        c.marqueeBox(ix0, ix1, h - 10f, h, z0, cabZ)
        // Dash, steering wheel and pedals.
        b.box(ix0, 16f, cabZ, ix1, 26f, cabZ + 6f, BoxFaces(front = dark, top = HallArt.darkMetal.full, gloss = 0.5f))
        b.add(wheel, c.xf.set(cx, 29f, cabZ + 7f, pitch = -1.05f))
        b.box(cx - 5f, 0.5f, cabZ + 3f, cx - 2f, 2f, cabZ + 7f, BoxFaces(top = HallArt.darkMetal.full, front = HallArt.darkMetal.full))
        b.box(cx + 2f, 0.5f, cabZ + 3f, cx + 5f, 2f, cabZ + 7f, BoxFaces(top = HallArt.darkMetal.full, front = HallArt.darkMetal.full))
        // Bucket seat facing the screen.
        b.box(cx - 8f, 0f, z1 - 18f, cx + 8f, 9f, z1 - 3f, BoxFaces(front = art.kick.full, left = side, right = side, top = dark, back = dark))
        b.box(cx - 7f, 9f, z1 - 17f, cx + 7f, 12f, z1 - 4f, seat)
        b.add(seatBack, c.xf.set(cx, 11f, z1 - 3.5f, pitch = 0.2f))
        b.add(headrest, c.xf.set(cx, 31f, z1 + 0.5f, pitch = 0.2f))
        c.light(cx, 34f, cabZ + 8f, art.glow, 50f, 1f)
    }
}
