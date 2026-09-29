package com.pocketarcade.games.shooter

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.games.shooter.ShooterArt.coverBox
import com.pocketarcade.games.shooter.ShooterArt.panel
import com.pocketarcade.games.shooter.ShooterWorld.COVER
import com.pocketarcade.games.shooter.ShooterWorld.FACADE_Z
import com.pocketarcade.games.shooter.ShooterWorld.ROOM_Z
import com.pocketarcade.games.shooter.ShooterWorld.WINDOW_HALF
import com.pocketarcade.games.shooter.ShooterWorld.WINDOW_X
import com.pocketarcade.games.shooter.ShooterWorld.WINDOW_Y0
import com.pocketarcade.games.shooter.ShooterWorld.WINDOW_Y1
import com.pocketarcade.hub.HallArt

/**
 * The shootout's static set, built once as one model: the night sky and skyline, the bank with
 * its upstairs windows and neon sign, a street lamp, and the cover the figures hide behind
 * (crates, a road barrier, oil drums, a parked car and the sandbag wall in front of the player).
 * The cover's front faces match [ShooterWorld.COVER], which the hit tests use.
 */
internal object ShooterScene {
    val model: Model by lazy { build() }

    /** Front face [i] of [ShooterWorld.COVER]: x0, y0, x1, y1, z. */
    private fun c(i: Int, k: Int) = COVER[i * 5 + k]

    private fun build(): Model {
        val b = ModelBuilder()
        val stone = ShooterArt.stone.region(wrap = true)
        val dark = ShooterArt.paint(0xFF2A2230.toInt())

        // Sky and skyline far behind, and the street.
        b.panel(-220f, -40f, 580f, 720f, -420f, ShooterArt.backdrop, emissive = 0.55f)
        val road = ShooterArt.asphalt.region(wrap = true)
        b.quad(-400f, 0f, -420f, 760f, 0f, -420f, 760f, 0f, 700f, -400f, 0f, 700f, road, 0f, 1f, 0f, u1 = 1160f / 80f * 64f, v1 = 1120f / 80f * 64f)
        // Kerb and pavement along the bank.
        b.box(-400f, 0f, FACADE_Z, 760f, 3f, -40f, BoxFaces(top = stone, front = HallArt.darkMetal.full, texelsPerUnit = 0.6f))
        b.box(-400f, 0f, -44f, 760f, 3.4f, -40f, BoxFaces(front = ShooterArt.paint(Pal.YELLOW), top = ShooterArt.paint(Pal.YELLOW)))

        // The bank: ground floor with its shopfront, piers between the windows and the top band.
        b.panel(c(0, 0), c(0, 1), c(0, 2), c(0, 3), FACADE_Z, ShooterArt.bankFront)
        b.box(c(0, 0), c(0, 1), FACADE_Z - 12f, c(0, 2), c(0, 3), FACADE_Z - 0.2f, BoxFaces(top = stone, texelsPerUnit = 0.6f))
        for (i in 1..5) {
            b.box(c(i, 0), c(i, 1), FACADE_Z - 12f, c(i, 2), c(i, 3), FACADE_Z, BoxFaces(front = stone, left = stone, right = stone, texelsPerUnit = 0.6f))
        }
        // Cornice and parapet.
        b.box(-130f, 376f, FACADE_Z - 12f, 490f, 388f, FACADE_Z + 8f, BoxFaces(front = ShooterArt.paint(0xFFB8A48E.toInt()), top = dark))
        b.box(-130f, 264f, FACADE_Z, 490f, 272f, FACADE_Z + 4f, BoxFaces(front = ShooterArt.paint(0xFFA8947E.toInt()), top = dark, left = dark, right = dark))
        // The neon BANK sign on the top band.
        b.panel(120f, 290f, 240f, 350f, FACADE_Z + 1.5f, ShooterArt.neonSign, emissive = 1.3f)

        // The rooms behind the upstairs windows: back wall, side walls, ceiling, and a sill.
        val wall = ShooterArt.paint(0xFF3A2838.toInt())
        for (k in 0 until 3) {
            val x0 = WINDOW_X[k] - WINDOW_HALF
            val x1 = WINDOW_X[k] + WINDOW_HALF
            b.panel(x0, WINDOW_Y0, x1, WINDOW_Y1, ROOM_Z, ShooterArt.room, emissive = 0.35f)
            b.quad(x0, WINDOW_Y1, ROOM_Z, x0, WINDOW_Y1, FACADE_Z, x0, WINDOW_Y0 - 30f, FACADE_Z, x0, WINDOW_Y0 - 30f, ROOM_Z, wall, 1f, 0f, 0f)
            b.quad(x1, WINDOW_Y1, FACADE_Z, x1, WINDOW_Y1, ROOM_Z, x1, WINDOW_Y0 - 30f, ROOM_Z, x1, WINDOW_Y0 - 30f, FACADE_Z, wall, -1f, 0f, 0f)
            b.quad(x0, WINDOW_Y1, FACADE_Z, x1, WINDOW_Y1, FACADE_Z, x1, WINDOW_Y1, ROOM_Z, x0, WINDOW_Y1, ROOM_Z, wall, 0f, -1f, 0f)
            b.box(x0 - 4f, WINDOW_Y0 - 4f, FACADE_Z, x1 + 4f, WINDOW_Y0 + 2f, FACADE_Z + 7f, BoxFaces(front = stone, top = stone, left = stone, right = stone, texelsPerUnit = 0.6f))
            // Striped awning over each window.
            b.quad(x0 - 6f, WINDOW_Y1 + 8f, FACADE_Z, x1 + 6f, WINDOW_Y1 + 8f, FACADE_Z, x1 + 6f, WINDOW_Y1 - 8f, FACADE_Z + 22f, x0 - 6f, WINDOW_Y1 - 8f, FACADE_Z + 22f, ShooterArt.paint(if (k == 1) Pal.DARKRED else 0xFF1E6A5A.toInt()), 0f, 0.8f, 0.6f, cull = false)
        }

        // A street lamp on the left.
        val iron = HallArt.darkMetal.full
        b.cylinder(-8f, -52f, 0f, 250f, 3.5f, 10, iron, gloss = 0.5f)
        b.cylinder(-8f, -52f, 0f, 14f, 7f, 10, iron, top = iron)
        b.box(-10f, 246f, -54f, 40f, 250f, -50f, BoxFaces.all(iron))
        b.box(26f, 236f, -60f, 46f, 246f, -44f, BoxFaces(front = iron, left = iron, right = iron, top = iron))
        b.quad(27f, 236f, -45f, 45f, 236f, -45f, 45f, 236f, -59f, 27f, 236f, -59f, TexKit.white.full, 0f, -1f, 0f, emissive = 2f, tint = 0xFFFFE0A0.toInt())

        // Far row: two crate stacks with a road barrier between them.
        val wood = ShooterArt.paint(0xFF8A5A30.toInt())
        for (i in intArrayOf(9, 11)) {
            b.coverBox(c(i, 0), c(i, 1), c(i, 4) - 30f, c(i, 2), c(i, 3), c(i, 4), ShooterArt.crate.full, wood)
        }
        val concrete = ShooterArt.paint(0xFF9A96A0.toInt())
        b.coverBox(c(10, 0), c(10, 1), c(10, 4) - 16f, c(10, 2), c(10, 3), c(10, 4), ShooterArt.barrier.full, concrete)

        // Mid row: three oil drums, and a parked car side-on.
        for (k in 0 until 3) {
            val x = c(12, 0) + 14f + k * 28f
            b.cylinder(x, c(12, 4) - 14f, 0f, c(12, 3), 14f, 14, ShooterArt.drum.full, top = ShooterArt.paint(0xFF6A1418.toInt()), gloss = 0.4f)
        }
        val carPaint = ShooterArt.paint(Pal.BLUE)
        b.box(c(13, 0) + 2f, 4f, 200f, c(13, 2) - 2f, 34f, c(13, 4) - 2f, BoxFaces(top = carPaint, left = carPaint, right = carPaint, gloss = 0.6f))
        b.box(c(14, 0) + 2f, 34f, 206f, c(14, 2) - 2f, 56f, c(14, 4) - 8f, BoxFaces(top = carPaint, left = HallArt.glass.full, right = HallArt.glass.full, gloss = 0.6f))
        b.quad(c(13, 0), 62f, c(13, 4), c(13, 2), 62f, c(13, 4), c(13, 2), 0f, c(13, 4), c(13, 0), 0f, c(13, 4), ShooterArt.carSide.full, 0f, 0f, 1f, gloss = 0.5f)

        // The sandbag wall the player shoots over.
        val bags = ShooterArt.sandbags
        val z1 = c(15, 4)
        b.box(c(15, 0), 0f, z1 - 26f, c(15, 2), c(15, 3), z1, BoxFaces(front = bags.region(wrap = true), top = ShooterArt.paint(0xFFA08A60.toInt()), texelsPerUnit = 1f))
        return b.build()
    }
}
