package com.pocketarcade.games.whackamole

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.paintTexture
import com.pocketarcade.hub.HallArt

/**
 * The critters in the holes, modelled standing on y = 0 and facing +z: a furry mole (brown,
 * or gold for the bonus one) and the bomb on its spring, each with a bonked version.
 */
internal object Moles {
    /** Total height, from the bottom (below the rim) to the top of the head: the game's MOLE_H + MOLE_BELOW. */
    const val HEIGHT = 142f
    const val RADIUS = 30f

    private val paints = HashMap<Int, Region>()
    private fun paint(color: Int, gloss: Boolean = false): Region =
        paints.getOrPut(color * 2 + if (gloss) 1 else 0) { if (gloss) HallArt.paint(color, 0.35f, 0.6f).full else fur(color) }

    /** Soft fur: the colour with a fine, fibrous grain. */
    private fun fur(color: Int): Region = paintTexture(32, 32, 4) {
        vgrad(0f, 0f, 32f, 32f, Pal.mix(color, Pal.WHITE, 0.1f), Pal.shade(color, 0.85f))
        for (i in 0 until 260) {
            val x = hash01(i, color) * 32f
            val y = hash01(i, color + 1) * 32f
            line(x, y, x + 0.4f, y + 1.6f, 0.35f, Pal.withAlpha(if (i % 2 == 0) Pal.shade(color, 0.7f) else Pal.mix(color, Pal.WHITE, 0.25f), 0.5f))
        }
    }.full

    private val black: Region by lazy { HallArt.solid(0xFF140E10.toInt()).full }
    private val white: Region by lazy { TexKit.white.full }

    val normal: Model by lazy { mole(Pal.BROWN, Pal.TAN, bonked = false, gold = false) }
    val normalBonked: Model by lazy { mole(Pal.BROWN, Pal.TAN, bonked = true, gold = false) }
    val gold: Model by lazy { mole(Pal.GOLD, Pal.YELLOW, bonked = false, gold = true) }
    val goldBonked: Model by lazy { mole(Pal.GOLD, Pal.YELLOW, bonked = true, gold = true) }
    val bomb: Model by lazy { bomb(false) }
    val bombHit: Model by lazy { bomb(true) }

    private fun mole(body: Int, face: Int, bonked: Boolean, gold: Boolean): Model {
        val b = ModelBuilder()
        val fur = paint(body, gold)
        val muzzle = paint(face, gold)
        val g = if (gold) 0.9f else 0.05f
        val r = RADIUS
        val headY = HEIGHT - r
        b.capsule(0f, r, 0f, 0f, headY, 0f, r, fur, slices = 20, gloss = g)
        // Belly and paws.
        b.sphere(0f, 62f, r * 0.5f, r * 0.62f, muzzle, slices = 16, stacks = 10, sy = 1.3f, gloss = g)
        b.sphere(-r * 0.72f, 80f, r * 0.62f, 8f, muzzle, slices = 12, stacks = 8, gloss = g)
        b.sphere(r * 0.72f, 80f, r * 0.62f, 8f, muzzle, slices = 12, stacks = 8, gloss = g)
        for (s in intArrayOf(-1, 1)) for (k in -1..1) {
            b.capsule(s * r * 0.72f + k * 3f, 80f, r * 0.62f + 7f, s * r * 0.72f + k * 3.4f, 78f, r * 0.62f + 9f, 1.1f, white, slices = 6)
        }
        // Ears.
        b.sphere(-r * 0.78f, headY + 16f, -2f, 7f, fur, slices = 12, stacks = 8, sy = 0.8f, gloss = g)
        b.sphere(r * 0.78f, headY + 16f, -2f, 7f, fur, slices = 12, stacks = 8, sy = 0.8f, gloss = g)
        // Muzzle, nose and teeth.
        b.sphere(0f, headY - 6f, r * 0.62f, r * 0.55f, muzzle, slices = 16, stacks = 10, sy = 0.78f, gloss = g)
        b.sphere(0f, headY + 1f, r * 0.62f + r * 0.5f, 5.5f, HallArt.paint(Pal.HOTPINK, 0.4f, 0.7f).full, slices = 12, stacks = 8, gloss = 0.6f)
        if (bonked) {
            b.sphere(0f, headY - 13f, r * 0.62f + r * 0.38f, 6.5f, paint(Pal.DARKRED, true), slices = 12, stacks = 8, sy = 0.8f)
        }
        b.box(-3.6f, headY - 16f, r * 0.62f + r * 0.4f, -0.4f, headY - 9f, r * 0.62f + r * 0.47f, BoxFaces.all(white, 0.5f))
        b.box(0.4f, headY - 16f, r * 0.62f + r * 0.4f, 3.6f, headY - 9f, r * 0.62f + r * 0.47f, BoxFaces.all(white, 0.5f))
        // Eyes: shiny beads, or dizzy crosses once bonked.
        val eyeY = headY + 13f
        val eyeZ = r * 0.86f
        for (s in intArrayOf(-1, 1)) {
            val ex = s * 11f
            if (bonked) {
                b.capsule(ex - 4f, eyeY + 4f, eyeZ + 1f, ex + 4f, eyeY - 4f, eyeZ + 1f, 1.4f, black, slices = 6)
                b.capsule(ex - 4f, eyeY - 4f, eyeZ + 1f, ex + 4f, eyeY + 4f, eyeZ + 1f, 1.4f, black, slices = 6)
            } else {
                b.sphere(ex, eyeY, eyeZ, 4.6f, black, slices = 12, stacks = 8, gloss = 1f)
                b.sphere(ex - 1.4f, eyeY + 1.8f, eyeZ + 3.8f, 1.3f, white, slices = 6, stacks = 4, emissive = 0.8f)
            }
        }
        return b.build()
    }

    private fun bomb(hit: Boolean): Model {
        val b = ModelBuilder()
        val metal = HallArt.chrome.full
        // The spring, a stack of coils.
        var y = 6f
        while (y < 84f) {
            b.torus(0f, y, 0f, 11f, 2.2f, metal, segments = 16, sides = 6, gloss = 0.9f)
            y += 7f
        }
        val cy = 106f
        val shell = if (hit) paint(Pal.ORANGE, true) else paint(0xFF26222E.toInt(), true)
        b.sphere(0f, cy, 0f, 30f, shell, slices = 22, stacks = 14, gloss = 0.9f, emissive = if (hit) 1.2f else 0f)
        if (hit) b.sphere(0f, cy, 6f, 26f, paint(Pal.YELLOW, true), slices = 18, stacks = 12, emissive = 1.6f)
        // Cap and fuse.
        b.cylinder(0f, 0f, cy + 26f, cy + 34f, 8f, 12, HallArt.darkMetal.full, top = HallArt.darkMetal.full, gloss = 0.6f)
        b.capsule(0f, cy + 34f, 0f, 5f, cy + 44f, 2f, 1.6f, paint(Pal.TAN), slices = 6)
        if (!hit) {
            // Angry red eyes under slanted brows, and a grimace.
            val red = HallArt.solid(Pal.RED).full
            for (s in intArrayOf(-1, 1)) {
                b.sphere(s * 10f, cy + 4f, 26.5f, 4f, red, slices = 10, stacks = 6, emissive = 1.2f)
                b.capsule(s * 16f, cy + 13f, 25f, s * 4f, cy + 9f, 28f, 1.8f, red, slices = 6)
            }
            b.capsule(-8f, cy - 10f, 28f, 8f, cy - 10f, 28f, 1.6f, black, slices = 6)
        }
        return b.build()
    }

    /** Forces the models (and their textures) to be built up front. */
    fun warm(): Array<Model> = arrayOf(normal, normalBonked, gold, goldBonked, bomb, bombHit)
}
