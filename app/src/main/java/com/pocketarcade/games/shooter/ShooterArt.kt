package com.pocketarcade.games.shooter

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Fonts
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.TexKit
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture
import com.pocketarcade.hub.HallArt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Painted textures and models for the light-gun shootout: a bank front on a night street with
 * crates, barrels, a parked car and a sandbag wall, the robo-bandits, the civilians, drones and
 * the mothership boss. Everything is built lazily from render paths only (android.graphics).
 */
internal object ShooterArt {
    // ------------------------------------------------------------------ materials

    /** Strength of the lamp's light shaft at its brightest (0..1 alpha). */
    private const val CONE_ALPHA = 0.30f

    private val paints = HashMap<Int, Region>()

    /** A painted/plastic surface in [color], cached. */
    fun paint(color: Int): Region = paints.getOrPut(color) { HallArt.paint(color, 0.18f, 0.72f).full }

    private val white: Region get() = TexKit.white.full

    /** Night asphalt with grit, cracks and faded lane paint (tiles). */
    val asphalt: Texture by lazy {
        paintTexture(64, 64, 4) {
            fill(0xFF26242E.toInt())
            for (i in 0 until 500) {
                val x = hash01(i, 41) * 64f
                val y = hash01(i, 42) * 64f
                val c = if (i % 3 == 0) 0xFF3A3844.toInt() else 0xFF1C1A22.toInt()
                circle(x, y, 0.25f + hash01(i, 43) * 0.45f, c)
            }
            for (k in 0 until 3) {
                val x = hash01(k, 44) * 64f
                line(x, 0f, x + 6f, 20f, 0.25f, 0x55101014)
                line(x + 6f, 20f, x + 2f, 34f, 0.25f, 0x55101014)
            }
        }.also { it.repeat = true }
    }

    /** Sandstone blocks with mortar lines (tiles). */
    val stone: Texture by lazy {
        paintTexture(64, 64, 4) {
            fill(0xFF8C7866.toInt())
            for (row in 0 until 4) {
                val y = row * 16f
                val off = if (row % 2 == 0) 0f else 16f
                for (k in -1 until 3) {
                    val x = off + k * 32f
                    val tone = 0.85f + hash01(row * 7 + k, 3) * 0.25f
                    rect(x + 0.6f, y + 0.6f, 30.8f, 14.8f, Pal.shade(0xFF9A8672.toInt(), tone))
                }
                rect(0f, y, 64f, 0.6f, 0xFF4E4238.toInt())
            }
            grain(0.06f, 9)
        }.also { it.repeat = true }
    }

    /** Dark brick for the neighbouring buildings (tiles). */
    val brick: Texture by lazy {
        paintTexture(64, 64, 4) {
            fill(0xFF2A1A1E.toInt())
            for (row in 0 until 8) {
                val y = row * 8f
                val off = if (row % 2 == 0) 0f else 8f
                for (k in -1 until 5) {
                    val tone = 0.75f + hash01(row * 11 + k, 5) * 0.4f
                    rect(off + k * 16f + 0.5f, y + 0.5f, 15f, 7f, Pal.shade(0xFF6A3A34.toInt(), tone))
                }
            }
            grain(0.05f, 4)
        }.also { it.repeat = true }
    }

    /** The bank's ground floor: columns, a revolving door, dark display windows and a clock. */
    val bankFront: Texture by lazy {
        paintTexture(260, 96, 4) {
            vgrad(0f, 0f, 260f, 96f, 0xFF8E7A68.toInt(), 0xFF6E5E50.toInt())
            for (row in 0 until 12) rect(0f, row * 8f, 260f, 0.4f, 0x33000000)
            // Display windows either side of the entrance, warmly lit.
            for (k in 0 until 4) {
                val x = 22f + k * 44f + (if (k >= 2) 40f else 0f)
                if (k >= 2 && x > 230f) continue
                round(x, 26f, 30f, 42f, 2f, 0xFF2A2024.toInt())
                vgrad(x + 2f, 28f, 26f, 38f, 0xFFFFC77A.toInt(), 0xFF8A5A30.toInt())
                rect(x + 14f, 28f, 1.2f, 38f, 0xFF3A2A24.toInt())
                rect(x + 2f, 45f, 26f, 1.2f, 0xFF3A2A24.toInt())
            }
            // Columns.
            for (k in 0 until 6) {
                val x = 8f + k * 49f
                hgrad(x, 10f, 8f, 86f, 0xFFB8A48E.toInt(), 0xFFD8C6AE.toInt(), 0xFF8C7A68.toInt())
                rect(x - 1.5f, 8f, 11f, 4f, 0xFFC8B49C.toInt())
                rect(x - 1.5f, 90f, 11f, 6f, 0xFF7A6A5A.toInt())
            }
            // Entrance: steps and a dark revolving door.
            round(106f, 30f, 48f, 66f, 3f, 0xFF1E1618.toInt())
            vgrad(110f, 34f, 40f, 62f, 0xFF4A3A40.toInt(), 0xFF171216.toInt())
            rect(129.4f, 34f, 1.2f, 62f, 0xFFC8A860.toInt())
            rect(110f, 60f, 40f, 1f, 0xFFC8A860.toInt())
            text("BANK", 130f, 24f, 11f, 0xFFE8D8B8.toInt(), Fonts.display)
            // A clock over the door.
            circle(130f, 10f, 6f, 0xFFE8D8B8.toInt())
            circle(130f, 10f, 5f, 0xFFFFF4D6.toInt())
            line(130f, 10f, 130f, 6.5f, 0.6f, 0xFF201818.toInt())
            line(130f, 10f, 132.6f, 11f, 0.6f, 0xFF201818.toInt())
            rect(0f, 0f, 260f, 3f, 0xFF5E4E42.toInt())
            grain(0.04f, 21)
        }
    }

    /** The dim room behind an upstairs window: wallpaper, a desk lamp and curtains. */
    val room: Texture by lazy {
        paintTexture(40, 40, 6) {
            vgrad(0f, 0f, 40f, 40f, 0xFF3C2A3A.toInt(), 0xFF1A1220.toInt())
            for (x in 0 until 40 step 5) rect(x.toFloat(), 0f, 1.2f, 40f, 0x22FFD8A0)
            radial(28f, 22f, 16f, 0x66FFC070, 0)
            rect(26f, 20f, 4f, 3f, 0xFFFFD890.toInt())
            vgrad(0f, 0f, 7f, 40f, 0xFF8A1E3A.toInt(), 0xFF4A0E1E.toInt())
            vgrad(33f, 0f, 7f, 40f, 0xFF8A1E3A.toInt(), 0xFF4A0E1E.toInt())
        }
    }

    /** The neon "BANK" sign, lit pink on a dark board. */
    val neonSign: Texture by lazy {
        paintTexture(140, 60, 4) {
            round(0f, 0f, 140f, 60f, 6f, 0xFF140C1A.toInt())
            strokeRound(3f, 3f, 134f, 54f, 5f, 2f, 0xFF3DF5FF.toInt())
            glowText("BANK", 70f, 46f, 40f, 0xFFFFE0F0.toInt(), 0xFFFF3FA4.toInt(), 5f, Fonts.display, 0.08f)
        }
    }

    /** A wooden crate stack face: two crates with cross braces and stencils. */
    val crate: Texture by lazy {
        paintTexture(80, 62, 4) {
            fill(0xFF6A4424.toInt())
            for (k in 0 until 2) {
                val y = k * 31f
                vgrad(1f, y + 1f, 78f, 29f, 0xFFB47A44.toInt(), 0xFF8A5A30.toInt())
                for (p in 0 until 4) rect(1f, y + 1f + p * 7.3f, 78f, 0.5f, 0x44000000)
                line(4f, y + 4f, 76f, y + 27f, 3f, 0xFF7A4E28.toInt(), round = false)
                strokeRound(2f, y + 2f, 76f, 27f, 1f, 2.5f, 0xFF5A3A1E.toInt())
                text(if (k == 0) "FRAGILE" else "\$\$\$", 40f, y + 20f, 8f, 0x88201008.toInt(), Fonts.condensed)
            }
            grain(0.06f, 31)
        }
    }

    /** A concrete road barrier with hazard stripes. */
    val barrier: Texture by lazy {
        paintTexture(80, 58, 4) {
            vgrad(0f, 0f, 80f, 58f, 0xFFB4B0B8.toInt(), 0xFF7A7680.toInt())
            for (k in 0 until 10) {
                val x = k * 10f - 6f
                polygon(floatArrayOf(x, 16f, x + 5f, 16f, x + 13f, 30f, x + 8f, 30f), Pal.YELLOW)
            }
            rect(0f, 14f, 80f, 2f, 0xFF3A3840.toInt())
            rect(0f, 30f, 80f, 2f, 0xFF3A3840.toInt())
            grain(0.08f, 7)
        }
    }

    /** An oil drum's side, red with ribs and a hazard label. */
    val drum: Texture by lazy {
        paintTexture(64, 58, 4) {
            hgrad(0f, 0f, 64f, 58f, 0xFF7A1A1E.toInt(), 0xFFC83A34.toInt(), 0xFF6A1418.toInt())
            for (y in floatArrayOf(4f, 20f, 38f, 54f)) rect(0f, y, 64f, 1.5f, 0xFF3A0A0C.toInt())
            round(22f, 24f, 20f, 12f, 2f, Pal.YELLOW)
            text("OIL", 32f, 33.5f, 9f, 0xFF201008.toInt(), Fonts.display)
        }
    }

    /** A parked car's side: two-tone paint, dark windows, chrome trim and wheels. */
    val carSide: Texture by lazy {
        paintTexture(140, 62, 4) {
            clear(0)
            // Cabin with windows.
            polygon(floatArrayOf(34f, 28f, 46f, 4f, 104f, 4f, 118f, 28f), 0xFF1A3A6C.toInt())
            polygon(floatArrayOf(40f, 26f, 49f, 8f, 72f, 8f, 72f, 26f), 0xFF22303E.toInt())
            polygon(floatArrayOf(76f, 26f, 76f, 8f, 101f, 8f, 111f, 26f), 0xFF22303E.toInt())
            line(52f, 10f, 60f, 22f, 1.5f, 0x44FFFFFF)
            // Body.
            round(0f, 26f, 140f, 26f, 6f, 0xFF2F5BE0.toInt())
            vgrad(0f, 26f, 140f, 8f, 0x55FFFFFF, 0)
            rect(0f, 40f, 140f, 1.5f, 0xFFD8DCE8.toInt())
            rect(72f, 28f, 0.8f, 22f, 0xFF1A2A6C.toInt())
            round(130f, 30f, 8f, 5f, 2f, 0xFFFFE8A0.toInt())
            round(2f, 30f, 6f, 5f, 2f, 0xFFFF4D4D.toInt())
            // Wheel arches and wheels.
            for (x in floatArrayOf(30f, 110f)) {
                circle(x, 50f, 13f, 0xFF101018.toInt())
                circle(x, 50f, 11f, 0xFF1C1C24.toInt())
                circle(x, 50f, 6f, 0xFFB8BCC8.toInt())
                circle(x, 50f, 2f, 0xFF5A5E6A.toInt())
            }
        }
    }

    /** Rows of sandbags (tiles across). */
    val sandbags: Texture by lazy {
        paintTexture(64, 46, 4) {
            fill(0xFF3A3224.toInt())
            for (row in 0 until 4) {
                val y = row * 11.5f
                val off = if (row % 2 == 0) 0f else 16f
                for (k in -1 until 3) {
                    val x = off + k * 32f
                    val tone = 0.85f + hash01(row * 5 + k, 13) * 0.25f
                    round(x + 0.8f, y + 0.6f, 30.4f, 10.6f, 5f, Pal.shade(0xFFB09A6E.toInt(), tone))
                    vgrad(x + 2f, y + 1f, 28f, 3f, 0x33FFFFFF, 0)
                    rect(x + 15.5f, y + 2f, 0.6f, 8f, 0x33000000)
                }
            }
            grain(0.07f, 17)
        }.also { it.repeat = true }
    }

    /** The far backdrop: night sky, stars, a moon and a city skyline with lit windows. */
    val backdrop: Texture by lazy {
        paintTexture(220, 215, 4) {
            vgrad(0f, 0f, 220f, 215f, 0xFF05040E.toInt(), 0xFF10102A.toInt(), 0xFF2A1848.toInt(), 0xFF4A2458.toInt())
            for (i in 0 until 140) {
                val x = hash01(i, 71) * 220f
                val y = hash01(i, 72) * 150f
                circle(x, y, 0.15f + hash01(i, 73) * 0.3f, Pal.withAlpha(Pal.WHITE, 0.4f + hash01(i, 74) * 0.6f))
            }
            radial(168f, 36f, 22f, 0x44C3A6FF, 0)
            circle(168f, 36f, 9f, 0xFFFFF4D6.toInt())
            circle(171f, 34f, 8f, 0xFFE8DCC0.toInt())
            circle(165f, 39f, 1.6f, 0xFFD8CCB0.toInt())
            // Skyline silhouettes.
            var x = 0f
            var k = 0
            while (x < 220f) {
                val bw = 10f + hash01(k, 75) * 16f
                val bh = 26f + hash01(k, 76) * 48f
                val top = 215f - bh
                rect(x, top, bw, bh, 0xFF120E1E.toInt())
                if (hash01(k, 77) > 0.6f) rect(x + bw / 2f - 0.4f, top - 6f, 0.8f, 6f, 0xFF120E1E.toInt())
                for (wy in 0 until (bh / 4f).toInt()) for (wx in 0 until (bw / 3.5f).toInt()) {
                    if (hash01(k * 131 + wx, wy + 78) > 0.72f) {
                        rect(x + 1.5f + wx * 3.5f, top + 2f + wy * 4f, 1.4f, 1.8f, if (hash01(wx, k + wy) > 0.5f) 0xFFFFD890.toInt() else 0xFF9AD8FF.toInt())
                    }
                }
                x += bw + 0.5f
                k++
            }
        }
    }

    /**
     * A second, nearer skyline of dark towers between the far backdrop and the bank: they show
     * above the bank's roof line and give the sky depth. Alpha cut-outs; lit windows are sparse.
     */
    val skylineNear: Texture by lazy {
        paintTexture(240, 30, 4) {
            clear(0)
            var x = 0f
            var k = 0
            while (x < 240f) {
                val bw = 12f + hash01(k, 91) * 20f
                val bh = 8f + hash01(k, 92) * 20f
                val top = 30f - bh
                rect(x, top, bw, bh + 1f, 0xFF0C0916.toInt())
                // A lit edge on the side towards the moon, a rooftop unit, and a few windows.
                rect(x + bw - 0.7f, top, 0.7f, bh + 1f, 0x55503A78)
                if (hash01(k, 93) > 0.55f) rect(x + bw * 0.3f, top - 2.4f, bw * 0.3f, 2.4f, 0xFF0C0916.toInt())
                for (wy in 0 until (bh / 3.4f).toInt()) for (wx in 0 until (bw / 3.4f).toInt()) {
                    if (hash01(k * 57 + wx, wy + 94) > 0.82f) {
                        rect(x + 1.4f + wx * 3.4f, top + 1.6f + wy * 3.4f, 1.3f, 1.6f, if (hash01(wx, k + wy) > 0.5f) 0xFFFFC880.toInt() else 0xFF80C8FF.toInt())
                    }
                }
                x += bw + 0.4f
                k++
            }
        }
    }

    /**
     * The shaft of light under the street lamp: warm and brightest at the lamp, fading down and
     * out to the sides (alpha only, laid additively; its strength is baked in, so it stays subtle).
     */
    val lampCone: Texture by lazy {
        val w = 16
        val h = 32
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val v = (y + 0.5f) / h
            val u = abs((x + 0.5f) / w * 2f - 1f)
            val across = (1f - u * u).coerceIn(0f, 1f)
            val a = (1f - v) * (1f - v) * across * across * CONE_ALPHA
            px[y * w + x] = ((a * 255f).toInt().coerceIn(0, 255) shl 24) or 0xFFFFFF
        }
        Texture(w, h, px)
    }

    /**
     * Drifting ground mist: thicker towards the bottom of the quad it covers, with soft
     * uneven clumps that tile sideways (draw with a wrapping region and scroll its u).
     */
    val mist: Texture by lazy {
        val w = 128
        val h = 32
        val px = IntArray(w * h)
        val tau = (2.0 * PI).toFloat()
        for (y in 0 until h) for (x in 0 until w) {
            val u = x / w.toFloat()
            val v = (y + 0.5f) / h
            // Whole numbers of waves across the width, so the left and right edges meet.
            val n = 0.55f + 0.20f * sin(tau * 2f * u + 1.3f) + 0.15f * sin(tau * 5f * u + 0.4f) + 0.10f * sin(tau * 9f * u + 2.2f)
            // Densest just above the ground, thinning at its very foot (no hard seam where it meets it).
            val foot = ((1f - v) / 0.14f).coerceIn(0f, 1f)
            val rise = (v * v * (0.25f + 0.75f * foot)).coerceIn(0f, 1f)
            val a = (n * rise * 255f).toInt().coerceIn(0, 255)
            px[y * w + x] = (a shl 24) or 0xFFFFFF
        }
        Texture(w, h, px).also { it.repeat = true }
    }

    /** Wisps of cloud for the night sky: they tile sideways and thin out top and bottom. */
    val clouds: Texture by lazy {
        val w = 128
        val h = 32
        val px = IntArray(w * h)
        val tau = (2.0 * PI).toFloat()
        for (y in 0 until h) for (x in 0 until w) {
            val u = x / w.toFloat()
            val v = (y + 0.5f) / h
            val n = 0.5f + 0.28f * sin(tau * 3f * u + 0.8f + v * 2.4f) + 0.2f * sin(tau * 7f * u + 2.1f - v * 3.1f) + 0.12f * sin(tau * 12f * u + 4.2f)
            val band = sin(v * PI.toFloat()).coerceIn(0f, 1f)
            val a = (((n - 0.42f) * 2.2f).coerceIn(0f, 1f) * band * band * 255f).toInt().coerceIn(0, 255)
            px[y * w + x] = (a shl 24) or 0xFFFFFF
        }
        Texture(w, h, px).also { it.repeat = true }
    }

    /** A dark gradient, black at the top fading to nothing (alpha; ambient darkening under a ledge). */
    val shadeDown: Texture by lazy {
        val h = 16
        val px = IntArray(4 * h)
        for (y in 0 until h) for (x in 0 until 4) {
            val v = (y + 0.5f) / h
            val a = ((1f - v) * (1f - v) * 0.55f * 255f).toInt().coerceIn(0, 255)
            px[y * 4 + x] = a shl 24
        }
        Texture(4, h, px)
    }

    /** A soft-edged ring, for shock waves (white, alpha; brightest at 70% of the radius). */
    val shock: Texture by lazy {
        val n = 64
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f - n / 2f) / (n / 2f)
            val dy = (y + 0.5f - n / 2f) / (n / 2f)
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            val e = (d - 0.7f) / 0.14f
            val a = (kotlin.math.exp(-e * e) * 255f).toInt().coerceIn(0, 255)
            px[y * n + x] = (a shl 24) or 0xFFFFFF
        }
        Texture(n, n, px)
    }

    /** A brass shell casing lying along z, 2.5 world units long with its rounded ends. */
    val shell: Model by lazy {
        val brass = paint(0xFFC99A3A.toInt())
        ModelBuilder()
            .capsule(0f, 0f, -0.5f, 0f, 0f, 0.5f, 0.75f, brass, slices = 6, gloss = 0.8f)
            .build()
    }

    // ------------------------------------------------------------------ effects

    /** A bullet hole: a dark pit with a cracked, lighter rim (alpha). */
    val hole: Texture by lazy {
        paintTexture(16, 16, 4) {
            clear(0)
            radial(8f, 8f, 8f, 0x66000000, 0)
            for (k in 0 until 6) {
                val a = k * 1.047f + hash01(k, 3) * 0.6f
                line(8f, 8f, 8f + cos(a) * 6.5f, 8f + sin(a) * 6.5f, 0.45f, 0xAA201A18.toInt())
            }
            circle(8f, 8f, 3f, 0xFF3A3432.toInt())
            circle(8f, 8f, 2.1f, 0xFF050406.toInt())
        }
    }

    /** A thin white ring (lock-on warnings and civilian halos). */
    val ring: Texture by lazy {
        paintTexture(32, 32, 4) {
            clear(0)
            ring(16f, 16f, 13.5f, 2.2f, Pal.WHITE)
            for (k in 0 until 4) {
                val a = k * 1.5708f
                line(16f + cos(a) * 10f, 16f + sin(a) * 10f, 16f + cos(a) * 15.5f, 16f + sin(a) * 15.5f, 1.6f, Pal.WHITE)
            }
        }
    }

    /** A "!" warning badge. */
    val warn: Texture by lazy {
        paintTexture(16, 16, 6) {
            clear(0)
            polygon(floatArrayOf(8f, 0.5f, 15.5f, 14.5f, 0.5f, 14.5f), Pal.RED)
            polygon(floatArrayOf(8f, 3f, 13.2f, 13f, 2.8f, 13f), Pal.YELLOW)
            round(7.1f, 5.5f, 1.8f, 4.6f, 0.8f, Pal.BLACK)
            circle(8f, 11.5f, 0.9f, Pal.BLACK)
        }
    }

    /** A four-point muzzle-flash star (additive). */
    val flash: Texture by lazy {
        paintTexture(32, 32, 4) {
            clear(0)
            radial(16f, 16f, 12f, 0xFFFFFFFF.toInt(), 0)
            star(16f, 16f, 15f, 0xFFFFF4C0.toInt(), 0.18f)
            circle(16f, 16f, 4f, Pal.WHITE)
        }
    }

    // ------------------------------------------------------------------ figures

    /** Height of a standing figure from its feet to the top of its hat. */
    const val FIGURE_H = 104f

    /**
     * A robo-bandit, feet on y = 0, facing +z: dark suit, chrome head with a glowing red visor,
     * a black hat and a pistol held out towards the player.
     */
    val goon: Model by lazy {
        val b = ModelBuilder()
        val suit = paint(0xFF7A1C2A.toInt())
        val pants = paint(0xFF221C2A.toInt())
        val metal = HallArt.chrome.full
        val dark = HallArt.darkMetal.full
        val hat = paint(0xFF1A1418.toInt())
        b.box(-11f, 0f, -6f, -2f, 42f, 6f, BoxFaces.all(pants))
        b.box(2f, 0f, -6f, 11f, 42f, 6f, BoxFaces.all(pants))
        b.box(-17f, 42f, -9f, 17f, 78f, 9f, BoxFaces.all(suit, 0.2f))
        b.box(-17.2f, 42f, -9.2f, 17.2f, 46f, 9.2f, BoxFaces.all(dark, 0.4f))
        // Shirt, tie and bandolier.
        b.box(-5f, 58f, 9f, 5f, 78f, 9.6f, BoxFaces(front = paint(0xFFE8E0D8.toInt())))
        b.box(-1.6f, 56f, 9.6f, 1.6f, 76f, 10.2f, BoxFaces(front = paint(Pal.DARKRED)))
        b.capsule(-15f, 76f, 9.5f, 13f, 48f, 9.5f, 1.8f, paint(0xFF8A6A30.toInt()), slices = 6)
        // Arms: the right one holds the gun out, the left hangs.
        b.capsule(15f, 74f, 0f, 14f, 64f, 20f, 4.5f, suit, slices = 8)
        b.capsule(-16f, 74f, 0f, -20f, 50f, 4f, 4.5f, suit, slices = 8)
        b.sphere(14f, 63f, 21f, 4f, metal, slices = 8, stacks = 6, gloss = 0.8f)
        b.box(11.5f, 62f, 18f, 16.5f, 68f, 36f, BoxFaces.all(dark, 0.6f))
        b.box(12f, 55f, 19f, 16f, 63f, 24f, BoxFaces.all(dark, 0.6f))
        // Head: chrome dome, glowing visor, antenna.
        b.cylinder(0f, 0f, 78f, 84f, 5f, 8, dark)
        b.sphere(0f, 90f, 0f, 11.5f, metal, slices = 14, stacks = 10, gloss = 0.9f)
        b.box(-9.5f, 87f, 7f, 9.5f, 93f, 11.6f, BoxFaces(front = white, top = white, left = white, right = white, frontEmissive = 1.6f, topEmissive = 1.6f), tint = Pal.RED)
        // Hat.
        b.cylinder(0f, 0f, 96f, 97.5f, 18f, 16, hat, top = hat, bottom = hat)
        b.cylinder(0f, 0f, 97.5f, FIGURE_H, 10f, 12, hat, top = hat, topRadius = 8.5f)
        b.cylinder(0f, 0f, 97.5f, 99.5f, 10.2f, 12, paint(Pal.DARKRED))
        b.build()
    }

    /**
     * A civilian with their hands up, feet on y = 0, facing +z: bright shirt, jeans, a scared
     * face. They must not be shot.
     */
    val civilian: Model by lazy {
        val b = ModelBuilder()
        val shirt = paint(0xFF3DC8FF.toInt())
        val jeans = paint(0xFF2F4A90.toInt())
        val skin = paint(Pal.SKIN_LIGHT)
        val hair = paint(0xFFE8B040.toInt())
        val black = HallArt.solid(0xFF140E10.toInt()).full
        b.box(-11f, 0f, -6f, -2f, 42f, 6f, BoxFaces.all(jeans))
        b.box(2f, 0f, -6f, 11f, 42f, 6f, BoxFaces.all(jeans))
        b.box(-16f, 42f, -8f, 16f, 76f, 8f, BoxFaces.all(shirt, 0.15f))
        b.box(-6f, 64f, 8f, 6f, 76f, 8.6f, BoxFaces(front = paint(Pal.WHITE)))
        // Hands up.
        for (s in intArrayOf(-1, 1)) {
            b.capsule(s * 15f, 72f, 0f, s * 22f, 90f, 2f, 4.2f, shirt, slices = 8)
            b.capsule(s * 22f, 90f, 2f, s * 22f, 104f, 4f, 3.6f, skin, slices = 8)
            b.sphere(s * 22f, 106f, 4f, 4.6f, skin, slices = 10, stacks = 6)
        }
        b.cylinder(0f, 0f, 76f, 82f, 4.5f, 8, skin)
        b.sphere(0f, 90f, 0f, 11f, skin, slices = 14, stacks = 10)
        b.sphere(0f, 93f, -1.5f, 11.6f, hair, slices = 14, stacks = 8, yFrom = 0.1f)
        // Wide eyes and an "O" of a mouth.
        for (s in intArrayOf(-1, 1)) {
            b.sphere(s * 4f, 92f, 10f, 2.4f, paint(Pal.WHITE), slices = 8, stacks = 6)
            b.sphere(s * 4f, 92f, 12f, 1.1f, black, slices = 6, stacks = 4)
        }
        b.torus(0f, 85.5f, 10.4f, 2f, 0.8f, black, segments = 10, sides = 4)
        b.build()
    }

    /** Radius of a drone's saucer. */
    const val DRONE_R = 22f

    private fun saucer(hull: Region, dome: Int, lights: Int, scale: Float): Model {
        val b = ModelBuilder()
        val r = DRONE_R * scale
        val profile = floatArrayOf(
            0f, -7f * scale,
            r * 0.55f, -6.5f * scale,
            r, -1f * scale,
            r * 0.98f, 1.5f * scale,
            r * 0.6f, 5f * scale,
            0f, 6f * scale,
        )
        b.lathe(0f, 0f, 0f, profile, 18, hull, gloss = 0.8f)
        b.sphere(0f, 5f * scale, 0f, 8.5f * scale, paint(dome), slices = 14, stacks = 8, yFrom = 0f, emissive = 0.6f, gloss = 1f)
        for (k in 0 until 8) {
            val a = k * 0.785f
            b.sphere(cos(a) * r * 0.92f, 0f, sin(a) * r * 0.92f, 2.2f * scale, white, slices = 6, stacks = 4, tint = lights, emissive = 1.6f)
        }
        return b.build()
    }

    /** An enemy drone: a small chrome saucer with a teal dome and red running lights. */
    val drone: Model by lazy { saucer(HallArt.chrome.full, 0xFF1FA89A.toInt(), Pal.RED, 1f) }

    /** The bonus drone: gold, with a white dome and gold lights. */
    val goldDrone: Model by lazy { saucer(paint(Pal.GOLD), Pal.WHITE, Pal.YELLOW, 0.8f) }

    // ------------------------------------------------------------------ the boss

    /** The mothership's hull: a wide armoured saucer with a bridge on top, centred on its origin. */
    val bossHull: Model by lazy {
        val b = ModelBuilder()
        val armour = paint(0xFF4A4C62.toInt())
        val trim = paint(0xFF8A1C2A.toInt())
        val r = ShooterWorld.BOSS_RADIUS
        val profile = floatArrayOf(
            0f, -30f,
            r * 0.5f, -28f,
            r * 0.9f, -12f,
            r, -2f,
            r * 0.96f, 6f,
            r * 0.7f, 18f,
            r * 0.35f, 26f,
            0f, 28f,
        )
        b.lathe(0f, 0f, 0f, profile, 28, armour, gloss = 0.6f)
        b.torus(0f, -2f, 0f, r * 0.99f, 3f, trim, segments = 28, sides = 6, gloss = 0.5f)
        b.sphere(0f, 28f, 0f, 26f, paint(0xFF1FA89A.toInt()), slices = 16, stacks = 8, yFrom = 0f, emissive = 0.5f, gloss = 1f)
        for (k in 0 until 16) {
            val a = k * 0.3927f
            b.sphere(cos(a) * r * 0.99f, -2f, sin(a) * r * 0.99f, 3f, white, slices = 6, stacks = 4, tint = Pal.ORANGE, emissive = 1.5f)
        }
        // Pod housings either side and the cannon mount under the front.
        for (s in intArrayOf(-1, 1)) {
            b.sphere(s * ShooterWorld.BOSS_POD_X, ShooterWorld.BOSS_POD_Y, 30f, 24f, armour, slices = 14, stacks = 10, gloss = 0.6f)
            b.capsule(s * ShooterWorld.BOSS_POD_X, ShooterWorld.BOSS_POD_Y - 20f, 30f, s * ShooterWorld.BOSS_POD_X, ShooterWorld.BOSS_POD_Y - 34f, 30f, 5f, HallArt.darkMetal.full)
        }
        b.cylinder(0f, ShooterWorld.BOSS_EYE_Z - 18f, ShooterWorld.BOSS_EYE_Y - 12f, -8f, 20f, 14, HallArt.darkMetal.full, top = HallArt.darkMetal.full)
        b.build()
    }

    /** A weak point's glowing core (tinted and lit per frame). */
    val core: Model by lazy {
        ModelBuilder().sphere(0f, 0f, 0f, ShooterWorld.BOSS_CORE_R, white, slices = 14, stacks = 10, emissive = 1.4f, gloss = 1f).build()
    }

    /** The armour shutter that covers a closed core. */
    val shutter: Model by lazy {
        val m = HallArt.darkMetal.full
        ModelBuilder().sphere(0f, 0f, 0f, ShooterWorld.BOSS_CORE_R + 1.5f, m, slices = 14, stacks = 8, yFrom = -0.2f, gloss = 0.7f)
            .sphere(0f, 0f, 0f, ShooterWorld.BOSS_CORE_R + 1f, m, slices = 14, stacks = 8, yTo = -0.1f, gloss = 0.7f)
            .build()
    }

    // ------------------------------------------------------------------ the player's gun

    /** A chunky toy light-gun, grip at the origin, barrel pointing along -z. */
    val pistol: Model by lazy {
        val b = ModelBuilder()
        val body = paint(0xFF2A2A34.toInt())
        val accent = paint(Pal.ORANGE)
        val metal = HallArt.darkMetal.full
        b.box(-2.2f, 2f, -18f, 2.2f, 6.5f, 3f, BoxFaces.all(body, 0.5f))
        b.box(-1.6f, 6.5f, -16f, 1.6f, 7.2f, 1f, BoxFaces.all(metal, 0.6f))
        b.box(-2.3f, 2.6f, -20f, 2.3f, 6f, -18f, BoxFaces.all(accent, 0.4f))
        b.box(-1.9f, -9f, -2f, 1.9f, 2.2f, 3.5f, BoxFaces.all(body, 0.3f))
        b.box(-2f, -9.5f, -2.5f, 2f, -8.5f, 4f, BoxFaces.all(accent))
        b.box(-0.5f, -2.5f, -7f, 0.5f, 2f, -6f, BoxFaces.all(metal))
        b.box(-0.5f, -3f, -7f, 0.5f, -2f, -2f, BoxFaces.all(metal))
        b.box(-2.4f, 3.5f, -12f, 2.4f, 5f, -4f, BoxFaces(left = accent, right = accent))
        b.build()
    }

    /** Forces every model (and its textures) to be built up front. */
    fun warm(): Array<Model> = arrayOf(goon, civilian, drone, goldDrone, bossHull, core, shutter, pistol, shell)

    // ------------------------------------------------------------------ helpers for the scene

    /** A textured box whose front uses [front] and the rest [side]. */
    fun ModelBuilder.coverBox(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, front: Region, side: Region, top: Region = side) {
        box(x0, y0, z0, x1, y1, z1, BoxFaces(front = front, left = side, right = side, top = top))
    }

    /** A flat quad facing +z at depth [z], mapping the whole of [t] (optionally tiled every [tile] units). */
    fun ModelBuilder.panel(x0: Float, y0: Float, x1: Float, y1: Float, z: Float, t: Texture, tile: Float = 0f, emissive: Float = 0f, blend: Blend = Blend.OPAQUE) {
        val u1 = if (tile > 0f) (x1 - x0) / tile * t.width else t.width.toFloat()
        val v1 = if (tile > 0f) (y1 - y0) / tile * t.height else t.height.toFloat()
        val reg = if (tile > 0f) t.region(wrap = true) else t.full
        quad(x0, y1, z, x1, y1, z, x1, y0, z, x0, y0, z, reg, 0f, 0f, 1f, u1 = u1, v1 = v1, emissive = emissive, blend = blend)
    }
}
