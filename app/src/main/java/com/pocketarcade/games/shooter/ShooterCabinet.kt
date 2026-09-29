package com.pocketarcade.games.shooter

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Renderer3D
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture
import com.pocketarcade.hub.CabinetBox
import com.pocketarcade.hub.CabinetBuild
import com.pocketarcade.hub.CabinetDesign
import com.pocketarcade.hub.HallArt
import com.pocketarcade.hub.beveledBox
import kotlin.math.sin

/**
 * The shootout's hall cabinet: a hooded upright with a big screen set back in the shade of the
 * hood, a lit marquee with chase bulbs, speaker grilles either side of the screen, and a gun deck
 * in front with two light-guns (blue 1P, red 2P) resting in holsters, their coiled cables running
 * back into the deck. Coin door on the kick panel and a foot pedal on the floor.
 */
object ShooterCabinet : CabinetDesign {
    override val width = 44f
    override val depth = 40f
    override val height = 85f
    override val focusHeight = 53f
    override val focusSetBack = 18f
    override val screenUnits = 24 to 18

    /** Depth of the screen face behind the back of the cabinet. */
    private const val SCREEN_Z = 20f
    private const val DECK_Y = 34f

    /** Painted speaker grille: a dark disc of holes in a chrome ring. */
    private val grille: Texture by lazy {
        paintTexture(32, 32, 4) {
            clear(0xFF121018.toInt())
            circle(16f, 16f, 15f, 0xFF8A8C98.toInt())
            circle(16f, 16f, 13.5f, 0xFF0C0A10.toInt())
            for (y in 0 until 9) for (x in 0 until 9) {
                val px = 5f + x * 2.75f
                val py = 5f + y * 2.75f
                val dx = px - 16f
                val dy = py - 16f
                if (dx * dx + dy * dy < 120f) circle(px, py, 0.8f, 0xFF3A3844.toInt())
            }
            circle(16f, 16f, 3f, 0xFF22202A.toInt())
        }
    }

    /** The hood's inner lining: dark with a warning-stripe lip along its front edge. */
    private val hoodLip: Texture by lazy {
        paintTexture(64, 8, 4) {
            fill(Pal.BLACK)
            for (k in 0 until 10) {
                val x = k * 8f - 4f
                polygon(floatArrayOf(x, 8f, x + 4f, 8f, x + 8f, 0f, x + 4f, 0f), Pal.YELLOW)
            }
        }
    }

    /** A light-gun lying in its holster, barrel towards the screen (-z), grip hanging off the deck. */
    private fun gun(color: Int): Model {
        val body = ShooterArt.paint(color)
        val metal = HallArt.darkMetal.full
        val tip = ShooterArt.paint(Pal.ORANGE)
        val b = ModelBuilder()
        b.box(-1.1f, 0f, -8.5f, 1.1f, 2.2f, 1.5f, BoxFaces.all(body, 0.6f))
        b.box(-0.8f, 2.2f, -7.5f, 0.8f, 2.6f, 0.5f, BoxFaces.all(metal, 0.6f))
        b.box(-1.15f, 0.2f, -9.2f, 1.15f, 2f, -8.5f, BoxFaces.all(tip))
        // Grip, raked back, and the trigger guard.
        b.box(-0.9f, -4.5f, 0f, 0.9f, 0f, 2.4f, BoxFaces.all(body, 0.3f))
        b.box(-0.25f, -2f, -2.8f, 0.25f, 0f, -2.3f, BoxFaces.all(metal))
        b.box(-0.25f, -2.2f, -2.8f, 0.25f, -1.8f, 0f, BoxFaces.all(metal))
        return b.build()
    }

    private val blueGun: Model by lazy { gun(0xFF2F5BE0.toInt()) }
    private val redGun: Model by lazy { gun(0xFFE8323C.toInt()) }

    /** A holster cradle: a cup the barrel rests in, open at the top. */
    private val holster: Model by lazy {
        val m = ShooterArt.paint(0xFF1A1820.toInt())
        ModelBuilder()
            .box(-2f, 0f, -10f, 2f, 1f, -1f, BoxFaces.all(m, 0.3f))
            .box(-2f, 1f, -10f, -1.4f, 3f, -1f, BoxFaces.all(m, 0.3f))
            .box(1.4f, 1f, -10f, 2f, 3f, -1f, BoxFaces.all(m, 0.3f))
            .box(-2f, 1f, -10f, 2f, 3f, -9.4f, BoxFaces.all(m, 0.3f))
            .build()
    }

    /** A coiled cable from a gun's grip back to the deck, sagging over the edge. */
    private fun cable(b: ModelBuilder, x: Float, y: Float, z: Float, backZ: Float, black: Region) {
        var px = x
        var py = y
        var pz = z
        val n = 9
        for (k in 1..n) {
            val t = k / n.toFloat()
            val nx = x + sin(t * 9.4f) * 0.9f
            val ny = y - 4.5f * sin(t * 3.14f) - t * 1.5f
            val nz = z + (backZ - z) * t
            b.capsule(px, py, pz, nx, ny, nz, 0.45f, black, slices = 5)
            px = nx; py = ny; pz = nz
        }
    }

    override fun build(c: CabinetBuild) {
        val b = c.b
        val art = c.art
        val x0 = c.x0
        val x1 = c.x1
        val z0 = c.z0
        val z1 = c.z1
        val h = c.h
        val cx = c.cx
        val dark = art.darkPaint.full
        val side = art.sideArt.full
        val inner = art.bodyPaint.full
        val trim = art.trimTex.full
        val screenZ = z0 + SCREEN_Z
        val hoodZ = z0 + 31f
        val st = 2f

        // Side panels run the full depth up to the deck, and up past the screen into the hood.
        b.beveledBox(x0, 0f, z0, x0 + st, h - 11f, hoodZ, BoxFaces(left = side, right = inner, top = dark, back = dark, front = trim, frontEmissive = 0.9f, gloss = 0.35f))
        b.beveledBox(x1 - st, 0f, z0, x1, h - 11f, hoodZ, BoxFaces(right = side, left = inner, top = dark, back = dark, front = trim, frontEmissive = 0.9f, gloss = 0.35f))
        b.box(x0, 0f, hoodZ, x0 + st, DECK_Y + 2f, z1 - 3f, BoxFaces(left = side, right = inner, top = dark, front = trim, frontEmissive = 0.9f))
        b.box(x1 - st, 0f, hoodZ, x1, DECK_Y + 2f, z1 - 3f, BoxFaces(right = side, left = inner, top = dark, front = trim, frontEmissive = 0.9f))
        // Body behind the screen, the bezel around it and the screen itself.
        val ix0 = x0 + st
        val ix1 = x1 - st
        b.box(ix0, DECK_Y, z0, ix1, h - 11f, screenZ, BoxFaces(front = art.bezel.full, top = dark, back = dark, gloss = 0.6f))
        // The back is closed all the way down (the deck's body is open behind otherwise).
        c.rearPanel(ix0, ix1, 1f, h - 11f)
        val sy0 = DECK_Y + 7f
        val sy1 = sy0 + 26f
        val live = c.liveScreen()
        b.quad(cx - 16f, sy1, screenZ + 0.15f, cx + 16f, sy1, screenZ + 0.15f, cx + 16f, sy0, screenZ + 0.15f, cx - 16f, sy0, screenZ + 0.15f, live.texture.full, 0f, 0f, 1f, emissive = 1.15f)
        // Speaker grilles either side of the screen, low on the bezel.
        val g = grille.full
        for (s in intArrayOf(-1, 1)) {
            val gx = cx + s * 18.2f
            b.quad(gx - 1.6f, sy0 + 3.2f, screenZ + 0.2f, gx + 1.6f, sy0 + 3.2f, screenZ + 0.2f, gx + 1.6f, sy0, screenZ + 0.2f, gx - 1.6f, sy0, screenZ + 0.2f, g, 0f, 0f, 1f)
            b.quad(gx - 1.6f, sy1, screenZ + 0.2f, gx + 1.6f, sy1, screenZ + 0.2f, gx + 1.6f, sy1 - 3.2f, screenZ + 0.2f, gx - 1.6f, sy1 - 3.2f, screenZ + 0.2f, g, 0f, 0f, 1f)
        }
        // The hood: a canopy over the screen with a striped lip, and the marquee on top of it.
        b.box(ix0, h - 14f, screenZ, ix1, h - 11f, hoodZ, BoxFaces(front = hoodLip.full, top = dark, frontEmissive = 0.3f))
        b.quad(ix0, h - 14f, hoodZ, ix1, h - 14f, hoodZ, ix1, h - 14f, screenZ, ix0, h - 14f, screenZ, dark, 0f, -1f, 0f)
        c.marqueeBox(x0, x1, h - 11f, h - 1f, z0 + 12f, hoodZ + 1f)
        // A strip light under the hood washing the screen.
        b.quad(ix0 + 1f, h - 14.1f, hoodZ - 1f, ix1 - 1f, h - 14.1f, hoodZ - 1f, ix1 - 1f, h - 14.1f, hoodZ - 3f, ix0 + 1f, h - 14.1f, hoodZ - 3f, TexKit.white.full, 0f, -1f, 0f, emissive = 1.6f, tint = art.glow)

        // The gun deck: kick panel with the coin door, a sloped control top, the instruction strip.
        b.beveledBox(ix0, 0f, screenZ, ix1, DECK_Y - 4f, z1 - 4f, BoxFaces(front = art.kick.full, top = dark, gloss = 0.3f), bevel = 0f)
        c.coinDoor(cx, 8f, z1 - 4f, 8f)
        val deckFront = z1 - 3f
        b.quad(ix0, DECK_Y + 2f, screenZ, ix1, DECK_Y + 2f, screenZ, ix1, DECK_Y - 1f, deckFront, ix0, DECK_Y - 1f, deckFront, HallArt.darkMetal.full, 0f, 0.97f, 0.24f, gloss = 0.5f)
        b.box(ix0, DECK_Y - 4f, deckFront - 1f, ix1, DECK_Y - 1f, deckFront, BoxFaces(front = art.panel.full, top = trim, frontEmissive = 0.7f))
        b.box(x0, DECK_Y - 5f, deckFront, x1, DECK_Y - 4f, deckFront + 0.6f, BoxFaces(front = trim, top = trim, frontEmissive = 1f))
        // Two guns in their holsters, cables coiling back into the deck.
        val black = HallArt.solid(0xFF101014.toInt()).full
        val slopeY = { z: Float -> DECK_Y + 2f - 3f * (z - screenZ) / (deckFront - screenZ) }
        for (s in intArrayOf(-1, 1)) {
            val gx = cx + s * 10f
            val gz = deckFront - 1f
            val gy = slopeY(gz - 5f) + 0.3f
            b.add(holster, c.xf.set(gx, gy, gz, pitch = 0.2f))
            b.add(if (s < 0) blueGun else redGun, c.xf.set(gx, gy + 1.1f, gz - 0.4f, pitch = 0.2f))
            cable(b, gx, gy - 3.4f, gz + 1.8f, screenZ + 4f, black)
            b.quad(gx - 2.2f, gy + 0.2f, gz + 0.6f, gx + 2.2f, gy + 0.2f, gz + 0.6f, gx + 2.2f, gy - 1.2f, gz + 0.6f, gx - 2.2f, gy - 1.2f, gz + 0.6f, TexKit.white.full, 0f, 0f, 1f, emissive = 1.3f, tint = if (s < 0) Pal.SKY else Pal.RED)
        }
        // Foot pedal.
        b.box(cx - 4f, 0f, z1 - 4f, cx + 4f, 1.2f, z1, BoxFaces(front = HallArt.darkMetal.full, top = HallArt.darkMetal.full, left = dark, right = dark))
        b.quad(cx - 3.4f, 2.4f, z1 - 3.8f, cx + 3.4f, 2.4f, z1 - 3.8f, cx + 3.4f, 1.2f, z1 - 0.4f, cx - 3.4f, 1.2f, z1 - 0.4f, ShooterArt.paint(0xFF2A2830.toInt()), 0f, 0.94f, 0.34f, gloss = 0.4f)

        // Lights: the marquee's glow, and the screen washing the players.
        c.light(cx, h - 4f, hoodZ + 6f, art.glow, 50f, 0.85f)
        c.light(cx, sy0 + 12f, hoodZ + 10f, 0xFF7AB8FF.toInt(), 40f, 0.55f)
    }

    /** The guns' muzzle LEDs take turns blinking in attract mode ("player 1... player 2..."). */
    override fun animate(r: Renderer3D, c: CabinetBox, t: Float) {
        val p = c.phase(t)
        val on = ((p * 2f).toInt() and 1) == 0
        val deckFront = c.z1 - 3f
        val y = DECK_Y + 2f - 3f * (deckFront - 6f - (c.z0 + SCREEN_Z)) / (deckFront - (c.z0 + SCREEN_Z)) + 2.6f
        for (k in 0..1) {
            val gx = c.cx + (k * 20f - 10f)
            val lit = if (k == 0) on else !on
            r.sprite(gx, y, deckFront - 11.2f, 1.1f, 1.1f, TexKit.dot.full, emissive = if (lit) 2.2f else 0.3f, tint = if (k == 0) Pal.SKY else Pal.RED)
        }
    }
}
