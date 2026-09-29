package com.pocketarcade.games.fishing

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.Xform
import com.pocketarcade.engine.r3d.paintTexture
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Painted textures and models for the fishing pond and its tub cabinet. Everything is built
 * lazily from render or cabinet-build paths, so headless tests never touch android.graphics.
 */
internal object FishingArt {
    /** Deep pond water with lighter wave streaks; tiles in both directions. */
    val water: Region by lazy {
        paintTexture(128, 128, 2) {
            vgrad(0f, 0f, 128f, 128f, 0xFF1C6E7A.toInt(), 0xFF155E6E.toInt(), 0xFF1C6E7A.toInt())
            for (k in 0 until 26) {
                val x = hash01(k, 41) * 128f
                val y = hash01(k, 42) * 128f
                val len = 10f + hash01(k, 43) * 22f
                val a = Pal.withAlpha(0xFF8FE3E0.toInt(), 0.16f + hash01(k, 44) * 0.18f)
                for (o in -1..1) {
                    line(x - len / 2f + o * 128f, y, x + len / 2f + o * 128f, y + (hash01(k, 45) - 0.5f) * 3f, 1.1f, a)
                    line(x - len / 2f, y + o * 128f, x + len / 2f, y + o * 128f + (hash01(k, 45) - 0.5f) * 3f, 1.1f, a)
                }
            }
        }.region(wrap = true)
    }

    /** Bright caustic web on black, added over the water and scrolled. */
    val caustics: Region by lazy {
        paintTexture(128, 128, 2) {
            clear(0xFF000000.toInt())
            for (k in 0 until 40) {
                val cx = hash01(k, 51) * 128f
                val cy = hash01(k, 52) * 128f
                val r = 6f + hash01(k, 53) * 12f
                for (ox in -1..1) for (oy in -1..1) {
                    ring(cx + ox * 128f, cy + oy * 128f, r, 0.9f + hash01(k, 54) * 0.8f, Pal.withAlpha(Pal.WHITE, 0.35f + hash01(k, 55) * 0.4f))
                }
            }
        }.region(wrap = true)
    }

    /** Muddy pond bed with weed and pebbles. */
    val bed: Region by lazy {
        paintTexture(128, 128, 2) {
            fill(0xFF2E4A36.toInt())
            for (k in 0 until 90) {
                val x = hash01(k, 61) * 128f
                val y = hash01(k, 62) * 128f
                val c = if (k % 3 == 0) 0xFF5E6B48.toInt() else if (k % 3 == 1) 0xFF22382A.toInt() else 0xFF7A7458.toInt()
                oval(x, y, 1.5f + hash01(k, 63) * 3f, 1f + hash01(k, 64) * 2f, c)
            }
            grain(0.08f, 6)
        }.region(wrap = true)
    }

    /**
     * Grass all round the pond with the pond itself cut out: an ellipse touching the edges of
     * the pond's rectangle, which the game maps over [pondX0]..[pondX1] × [pondZ0]..[pondZ1]
     * inside a bigger square.
     */
    fun bank(outX0: Float, outZ0: Float, outX1: Float, outZ1: Float, cx: Float, cz: Float, rx: Float, rz: Float): Texture {
        val n = 256
        val sx = n / (outX1 - outX0)
        val sz = n / (outZ1 - outZ0)
        val ux = (cx - outX0) * sx
        val vz = (cz - outZ0) * sz
        return paintTexture(n, n, 2) {
            vgrad(0f, 0f, n.toFloat(), n.toFloat(), 0xFF3E7A34.toInt(), 0xFF4E9440.toInt())
            for (k in 0 until 700) {
                val x = hash01(k, 71) * n
                val y = hash01(k, 72) * n
                val c = if (k % 4 == 0) 0xFF6DB84E.toInt() else if (k % 4 == 1) 0xFF2F5E28.toInt() else 0xFF4A8C3C.toInt()
                line(x, y, x + (hash01(k, 73) - 0.5f) * 2f, y - 2.5f, 0.6f, c)
            }
            // A muddy, reedy rim, then the water hole.
            oval(ux, vz, rx * sx + 6f, rz * sz + 6f, 0xFF5A4A30.toInt())
            oval(ux, vz, rx * sx + 2.5f, rz * sz + 2.5f, 0xFF3F3524.toInt())
            punchOval(ux, vz, rx * sx, rz * sz)
            // Stones along the rim.
            for (k in 0 until 40) {
                val a = hash01(k, 74) * 6.283f
                val px = ux + cos(a) * (rx * sx + 4f)
                val py = vz + sin(a) * (rz * sz + 4f)
                oval(px, py, 1.5f + hash01(k, 75) * 1.5f, 1f + hash01(k, 76), 0xFF8C8878.toInt())
            }
        }
    }

    private fun TexPaint.punchOval(cx: Float, cy: Float, rx: Float, ry: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
        canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint)
        paint.xfermode = null
    }

    /** A lily pad (with its notch cut out) seen from above. */
    val lilyPad: Region by lazy {
        paintTexture(48, 48, 3) {
            clear(0)
            circle(24f, 24f, 22f, 0xFF2F7A32.toInt())
            radial(20f, 20f, 20f, 0xFF5FB04A.toInt(), 0xFF2F7A32.toInt())
            for (k in 0 until 9) {
                val a = k * 0.7f + 0.4f
                line(24f, 24f, 24f + cos(a) * 20f, 24f + sin(a) * 20f, 0.6f, 0xFF7ACB5A.toInt())
            }
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            canvas.drawPath(android.graphics.Path().apply { moveTo(24f, 24f); lineTo(48f, 16f); lineTo(48f, 32f); close() }, paint)
            paint.xfermode = null
        }.full
    }

    /** A pink water-lily flower, a small sprite. */
    val flower: Region by lazy {
        paintTexture(32, 32, 3) {
            clear(0)
            for (k in 0 until 8) {
                val a = k * PI.toFloat() / 4f
                oval(16f + cos(a) * 7f, 16f + sin(a) * 7f, 5.5f, 5.5f, if (k % 2 == 0) 0xFFFF8FC8.toInt() else 0xFFFFB8DC.toInt())
            }
            circle(16f, 16f, 5f, 0xFFFFE14D.toInt())
        }.full
    }

    /** Expanding ring for ripples (added). */
    val ripple: Region by lazy {
        paintTexture(64, 64, 2) {
            clear(0)
            ring(32f, 32f, 28f, 3f, Pal.withAlpha(Pal.WHITE, 0.9f))
            ring(32f, 32f, 24f, 1.5f, Pal.withAlpha(Pal.WHITE, 0.35f))
        }.full
    }

    /** Target ring shown on the water while charging a cast. */
    val target: Region by lazy {
        paintTexture(64, 64, 2) {
            clear(0)
            ring(32f, 32f, 27f, 4f, Pal.WHITE)
            ring(32f, 32f, 13f, 3f, Pal.WHITE)
            circle(32f, 32f, 3f, Pal.WHITE)
        }.full
    }

    /** A leafy tree for the far bank (billboard). */
    val tree: Region by lazy {
        paintTexture(96, 128, 2) {
            clear(0)
            rect(44f, 70f, 8f, 58f, 0xFF4A3220.toInt())
            rect(46f, 70f, 2f, 58f, 0xFF6A4A30.toInt())
            val greens = intArrayOf(0xFF1F5A2A.toInt(), 0xFF2E7A36.toInt(), 0xFF3F9444.toInt(), 0xFF5AAE50.toInt())
            for (layer in 0 until 4) {
                for (k in 0 until 9) {
                    val x = 48f + (hash01(k, 81 + layer) - 0.5f) * (60f - layer * 8f)
                    val y = 58f + (hash01(k, 91 + layer) - 0.5f) * (72f - layer * 10f) - layer * 4f
                    circle(x, y, 17f - layer * 2.5f, greens[layer])
                }
            }
        }.full
    }

    /** Cattails and reeds along the bank (billboard). */
    val reeds: Region by lazy {
        paintTexture(64, 64, 3) {
            clear(0)
            for (k in 0 until 14) {
                val x = 4f + hash01(k, 101) * 56f
                val top = 8f + hash01(k, 102) * 28f
                val lean = (hash01(k, 103) - 0.5f) * 10f
                line(x, 64f, x + lean, top, 1.3f, if (k % 2 == 0) 0xFF4E8A3A.toInt() else 0xFF6FA848.toInt())
                if (k % 3 == 0) {
                    oval(x + lean * 0.9f, top + 6f, 1.8f, 5f, 0xFF6A3E1E.toInt())
                }
            }
        }.full
    }

    /** The distant tree line and hills behind the pond. */
    val treeline: Region by lazy {
        paintTexture(256, 64, 2) {
            clear(0)
            for (k in 0 until 3) {
                val base = 0xFF2A5E48.toInt()
                val c = Pal.mix(base, 0xFF8FC0C8.toInt(), 0.45f - k * 0.18f)
                for (i in 0 until 40) {
                    val x = i * 7f + hash01(i, 111 + k) * 6f
                    val r = 7f + hash01(i, 121 + k) * 9f
                    circle(x, 30f + k * 8f - hash01(i, 131 + k) * 10f, r, c)
                }
                rect(0f, 34f + k * 8f, 256f, 30f, c)
            }
        }.full
    }

    /** Weathered dock planks, running away from the viewer. */
    val planks: Region by lazy {
        paintTexture(128, 128, 2) {
            val base = 0xFF9A6A3E.toInt()
            for (i in 0 until 8) {
                val tone = 0.82f + hash01(i, 141) * 0.25f
                val c = Pal.shade(base, tone.coerceAtMost(1f))
                rect(i * 16f, 0f, 16f, 128f, c)
                for (k in 0 until 8) {
                    val gx = i * 16f + 2f + hash01(i * 13 + k, 142) * 12f
                    line(gx, hash01(k, i + 143) * 128f, gx + 0.4f, hash01(k, i + 144) * 128f, 0.4f, Pal.withAlpha(Pal.shade(c, 0.7f), 0.6f))
                }
                rect(i * 16f, 0f, 1.2f, 128f, 0xFF3A2614.toInt())
                circle(i * 16f + 4f, 10f, 0.9f, 0xFF2A1A10.toInt())
                circle(i * 16f + 12f, 10f, 0.9f, 0xFF2A1A10.toInt())
            }
            grain(0.07f, 9)
        }.region(wrap = true)
    }

    /** The rod blank: dark glossy blue, lit down its middle (beams map u across the rod). */
    val rod: Region by lazy {
        paintTexture(8, 4, 4) {
            hgrad(0f, 0f, 8f, 4f, 0xFF0E1838.toInt(), 0xFF4A6AC8.toInt(), 0xFF101C40.toInt())
        }.full
    }

    val cork: Region by lazy {
        paintTexture(16, 16, 2) {
            hgrad(0f, 0f, 16f, 16f, 0xFF8A6238.toInt(), 0xFFD8A870.toInt(), 0xFF7A5430.toInt())
            for (k in 0 until 30) circle(hash01(k, 151) * 16f, hash01(k, 152) * 16f, 0.5f, 0xFF6A4A28.toInt())
        }.full
    }

    val line: Region by lazy { TexKit.white.full }

    // ------------------------------------------------------------------ models

    /** Fish skins: back colour, belly colour and markings per species (see [FishingTuning.NAMES]). */
    private fun skin(species: Int): Texture = paintTexture(64, 32, 2) {
        val back = BACKS[species]
        val belly = BELLIES[species]
        val side = Pal.mix(back, belly, 0.5f)
        // u runs round the body: one flank at 0 and 32, the belly at 16, the back at 48; v runs
        // from the nose (0) to the tail (32).
        hgrad(0f, 0f, 64f, 32f, side, belly, side, back, side)
        when (species) {
            0 -> for (k in 0 until 5) rect(34f, 6f + k * 5f, 28f, 2.2f, 0xFF2E3A18.toInt())
            1 -> {
                rect(30f, 4f, 4f, 28f, 0xFF1E3A1A.toInt())
                rect(0f, 4f, 2f, 28f, 0xFF1E3A1A.toInt())
                rect(62f, 4f, 2f, 28f, 0xFF1E3A1A.toInt())
            }
            2 -> for (k in 0 until 30) circle(hash01(k, 161) * 64f, hash01(k, 162) * 32f, 0.8f, 0xFF2A2420.toInt())
            3 -> for (k in 0 until 24) circle(hash01(k, 163) * 64f, hash01(k, 164) * 32f, 1.2f, 0xFFFFF4B0.toInt())
            else -> Unit
        }
        // Eyes just behind the nose, one on each flank.
        circle(36f, 3.5f, 1.7f, Pal.WHITE)
        circle(60f, 3.5f, 1.7f, Pal.WHITE)
        circle(36f, 3.5f, 1f, Pal.BLACK)
        circle(60f, 3.5f, 1f, Pal.BLACK)
    }

    private val BACKS = intArrayOf(0xFF6E8A2A.toInt(), 0xFF3E6A38.toInt(), 0xFF4E4A44.toInt(), 0xFFE0A020.toInt())
    private val BELLIES = intArrayOf(0xFFF2D68A.toInt(), 0xFFD8E0B0.toInt(), 0xFFB8B0A0.toInt(), 0xFFFFE890.toInt())
    private val FINS = intArrayOf(0xFFE07A2A.toInt(), 0xFF5A7A48.toInt(), 0xFF5A524A.toInt(), 0xFFFF9A20.toInt())

    private val fishModels = arrayOfNulls<Model>(4)

    /**
     * A fish [length] long, nose along +z, centred on the origin: a lathed body flattened side
     * to side, a forked tail and a dorsal fin. One model per species; sizes vary by scale.
     */
    fun fish(species: Int): Model {
        fishModels[species]?.let { return it }
        val len = FishingTuning.LENGTH[species]
        val tex = skin(species)
        // Body along +y (tail at 0, nose at len), then laid along +z.
        val prof = floatArrayOf(
            0f, 0f, 0.06f, 0.05f, 0.12f, 0.2f, 0.18f, 0.42f, 0.19f, 0.6f, 0.16f, 0.78f, 0.1f, 0.92f, 0f, 1f,
        )
        for (i in prof.indices) prof[i] *= len
        val body = ModelBuilder().lathe(0f, 0f, 0f, prof, 10, tex.full, gloss = 0.6f).build()
        val finTex = TexKit.solid(4, 4, FINS[species]).full
        val b = ModelBuilder()
        val laid = ModelBuilder().add(body, Xform().set(0f, 0f, -len / 2f, pitch = PI.toFloat() / 2f)).build()
        b.addScaled(laid, 0.55f, 0.85f, 1f)
        // Tail fin: a forked vertical fan behind the body.
        val tz = -len / 2f
        b.quad(0f, len * 0.26f, tz - len * 0.24f, 0f, 0f, tz + len * 0.04f, 0f, 0f, tz + len * 0.04f, 0f, -len * 0.26f, tz - len * 0.24f, finTex, 1f, 0f, 0f, cull = false)
        b.quad(0f, len * 0.26f, tz - len * 0.24f, 0f, 0.02f, tz - len * 0.1f, 0f, -0.02f, tz - len * 0.1f, 0f, -len * 0.26f, tz - len * 0.24f, finTex, 1f, 0f, 0f, cull = false)
        // Dorsal fin.
        b.quad(0f, len * 0.26f, -len * 0.05f, 0f, len * 0.14f, len * 0.16f, 0f, len * 0.1f, len * 0.16f, 0f, len * 0.12f, -len * 0.12f, finTex, 1f, 0f, 0f, cull = false)
        return b.build().also { fishModels[species] = it }
    }

    /** The old boot: a scuffed leather boot lying on its side, toe along +z. */
    val boot: Model by lazy {
        val leather = paintTexture(32, 32, 2) {
            vgrad(0f, 0f, 32f, 32f, 0xFF6A4424.toInt(), 0xFF3A2414.toInt())
            grain(0.12f, 3)
        }.full
        val sole = TexKit.solid(4, 4, 0xFF1E1812.toInt()).full
        val f = BoxFaces(front = leather, left = leather, right = leather, top = leather, back = leather)
        ModelBuilder()
            .box(-3.5f, 0f, -7f, 3.5f, 13f, -1f, f)
            .box(-3.5f, 0f, -1f, 3.5f, 5.5f, 7f, f)
            .box(-3.7f, -0.8f, -7.2f, 3.7f, 0f, 7.2f, BoxFaces(front = sole, left = sole, right = sole, back = sole, top = sole))
            .torus(0f, 13f, -4f, 3.3f, 0.6f, sole, segments = 10, sides = 4)
            .build()
    }

    /** The float: red cap over a white belly with an antenna on top. */
    val bobber: Model by lazy {
        val tex = paintTexture(16, 16, 2) {
            rect(0f, 0f, 16f, 8f, 0xFFFF3B30.toInt())
            rect(0f, 8f, 16f, 8f, Pal.WHITE)
            rect(0f, 7.5f, 16f, 1f, 0xFF20202A.toInt())
        }.full
        ModelBuilder()
            .sphere(0f, 0f, 0f, 4f, tex, slices = 12, stacks = 8, gloss = 0.8f)
            .cylinder(0f, 0f, 3.5f, 9f, 0.5f, 6, TexKit.solid(4, 4, 0xFFFF3B30.toInt()).full)
            .build()
    }

    // ------------------------------------------------------------------ cabinet art

    /** The tub's inside: blue tiles. */
    val tubTiles: Region by lazy {
        paintTexture(64, 32, 2) {
            fill(0xFF2A6AB8.toInt())
            for (x in 0 until 64 step 8) rect(x.toFloat(), 0f, 0.8f, 32f, 0xFF1A4A88.toInt())
            for (y in 0 until 32 step 8) rect(0f, y.toFloat(), 64f, 0.8f, 0xFF1A4A88.toInt())
        }.full
    }

    /** Pebbles on the tub floor. */
    val tubFloor: Region by lazy {
        paintTexture(64, 64, 2) {
            fill(0xFF1A3A5A.toInt())
            val cols = intArrayOf(0xFF3A6A8A.toInt(), 0xFF2A4A6A.toInt(), 0xFFC8B878.toInt(), 0xFF5A8AAA.toInt())
            for (k in 0 until 120) circle(hash01(k, 171) * 64f, hash01(k, 172) * 64f, 1f + hash01(k, 173) * 1.8f, cols[k % cols.size])
        }.full
    }

    /** The tub's water, translucent so the fish show through. */
    val tubWater: Region by lazy {
        paintTexture(64, 64, 2) {
            fill(Pal.withAlpha(0xFF2AB8D8.toInt(), 0.55f))
            for (k in 0 until 7) ring(32f, 32f, 4f + k * 4.2f, 0.8f, Pal.withAlpha(Pal.WHITE, 0.18f))
        }.full
    }

    /** Red-and-white striped canopy. */
    val canopy: Region by lazy {
        paintTexture(64, 16, 2) {
            for (k in 0 until 8) rect(k * 8f, 0f, 8f, 16f, if (k % 2 == 0) 0xFFE8323C.toInt() else 0xFFFFF4E0.toInt())
            rect(0f, 13f, 64f, 3f, 0xFFFFC83D.toInt())
        }.full
    }
}
