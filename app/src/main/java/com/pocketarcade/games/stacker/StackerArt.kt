package com.pocketarcade.games.stacker

import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.LinearGradient
import android.graphics.Shader
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Painted art for the stacker's world. The tower is being built on a rooftop high above a night
 * city, so the picture is a dark floor of lights far below with soft cloud decks between, and the
 * tower itself the only bright, saturated thing. Everything is built lazily on first use.
 */
internal object StackerArt {
    /** Transparent white: soft gradients fade to this so their middles don't turn grey. */
    private const val CLEAR_WHITE = 0x00FFFFFF
    private const val CITY_DARK = 0xFF05040E.toInt()

    /** Slab hues: a step round the colour wheel a level, starting at violet. */
    const val HUE_START = 260f
    const val HUE_STEP = 13f

    /** Hue (degrees) of a level's slab. */
    fun hueOf(level: Int): Float = (level * HUE_STEP + HUE_START) % 360f

    private val bodyTable: IntArray by lazy { IntArray(256) { hsv(hueOf(it), 0.8f, 0.5f) } }
    private val glowTable: IntArray by lazy { IntArray(256) { hsv(hueOf(it), 0.55f, 1f) } }

    /** A slab's body colour: a dark, saturated glass, so only its edges and top catch the light and glow. */
    fun bodyColor(level: Int): Int = bodyTable[level and 255]

    /** The neon of a slab's edges: the same hue, light and vivid. */
    fun glowColor(level: Int): Int = glowTable[level and 255]

    /** ARGB from hue in degrees, saturation and value in 0..1. */
    fun hsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val hp = (h % 360f) / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = v - c
        return (0xFF shl 24) or (((r1 + m) * 255).toInt().coerceIn(0, 255) shl 16) or (((g1 + m) * 255).toInt().coerceIn(0, 255) shl 8) or ((b1 + m) * 255).toInt().coerceIn(0, 255)
    }

    /** A slab's side: light at the top, shading darker towards the foot, with a groove where it meets the slab below. */
    val slabSide: Texture by lazy {
        paintTexture(8, 16, 4) {
            vgrad(0f, 0f, 8f, 16f, Pal.WHITE, 0xFFB4B4B4.toInt())
            rect(0f, 13.8f, 8f, 1.2f, Pal.withAlpha(Pal.BLACK, 0.3f))
        }
    }

    /**
     * The city far below, seen from above: streets, blocks and thousands of lit windows and
     * cars, brighter downtown, with a river and two highways, fading to the dark at the edges.
     */
    val city: Texture by lazy {
        paintTexture(256, 256, 4) {
            fill(CITY_DARK)
            // Blocks of buildings: faint lit rooftops.
            for (by in 0 until 16) for (bx in 0 until 16) {
                val n = hash01(bx, by, 11)
                val x = bx * 16f
                val y = by * 16f
                rect(x + 2f, y + 2f, 12f, 12f, Pal.withAlpha(if (n > 0.5f) 0xFF241C44.toInt() else 0xFF181233.toInt(), 0.9f))
                // Windows and cars.
                val dens = 0.35f + 0.65f * (1f - (abs(bx - 7.5f) + abs(by - 7.5f)) / 15f)
                val count = (3 + n * 14f * dens).toInt()
                for (k in 0 until count) {
                    val c = hash01(bx * 31 + k, by * 17 + k, 12)
                    val color = when {
                        c < 0.5f -> 0xFFFFC060.toInt()
                        c < 0.75f -> 0xFFDDEEFF.toInt()
                        c < 0.9f -> Pal.CYAN
                        else -> Pal.HOTPINK
                    }
                    circle(x + 2f + hash01(k, bx + by * 16, 13) * 12f, y + 2f + hash01(k, by + bx * 16, 14) * 12f, 0.5f, Pal.withAlpha(color, 0.5f + 0.5f * hash01(k, bx, by)))
                }
            }
            // Streets.
            for (i in 0..16) {
                val p = i * 16f
                for (k in 0 until 16) {
                    if (hash01(i, k, 15) > 0.3f) rect(p - 0.5f, k * 16f, 1f, 16f, Pal.withAlpha(0xFFFFC060.toInt(), 0.32f))
                    if (hash01(k, i, 16) > 0.3f) rect(k * 16f, p - 0.5f, 16f, 1f, Pal.withAlpha(0xFFFFC060.toInt(), 0.32f))
                }
            }
            // A river winding through, and two bright highways.
            var prevX = 0f
            var prevY = 120f
            for (k in 1..40) {
                val x = k * 6.4f
                val y = 120f + sin(x / 38f) * 26f + sin(x / 15f) * 6f
                line(prevX, prevY, x, y, 9f, 0xFF0A1636.toInt())
                line(prevX, prevY, x, y, 1.4f, Pal.withAlpha(Pal.SKY, 0.3f))
                prevX = x; prevY = y
            }
            line(0f, 40f, 256f, 210f, 2.4f, Pal.withAlpha(0xFFFF9A3C.toInt(), 0.7f))
            line(30f, 256f, 220f, 0f, 2f, Pal.withAlpha(Pal.CYAN, 0.55f))
            // Fade into the dark at the edges of the map.
            radial(128f, 128f, 128f, 0x0005040E, CITY_DARK)
        }
    }

    /** Soft cloud banks: overlapping lavender and rose puffs (alpha-blended, self-lit). */
    val cloud: Texture by lazy {
        paintTexture(128, 128, 3) {
            clear(0)
            for (i in 0 until 46) {
                val x = 14f + hash01(i, 31) * 100f
                val y = 14f + hash01(i, 32) * 100f
                val r = 9f + hash01(i, 33) * 22f
                val c = if (hash01(i, 34) > 0.6f) 0xFFC58AE0.toInt() else 0xFF8A78D8.toInt()
                radial(x, y, r, Pal.withAlpha(c, 0.3f), Pal.withAlpha(c, 0f))
            }
            // Fade to nothing at the edges so the bank has no rim.
            paint.reset()
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            paint.shader = RadialGradient(64f, 64f, 64f, intArrayOf(Pal.WHITE, Pal.WHITE, CLEAR_WHITE), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 128f, 128f, paint)
            paint.shader = null
            paint.xfermode = null
        }
    }

    /**
     * The rooftop the tower stands on: dark panels with vents, and neon rings round the pad
     * (a cyan ring and a pink one, thin so only they glow).
     */
    val roof: Texture by lazy {
        paintTexture(64, 64, 8) {
            fill(0xFF120E22.toInt())
            for (i in 0 until 8) {
                rect(i * 8f, 0f, 0.3f, 64f, 0xFF0A0716.toInt())
                rect(0f, i * 8f, 64f, 0.3f, 0xFF0A0716.toInt())
            }
            for (i in 0 until 60) circle(hash01(i, 41) * 64f, hash01(i, 42) * 64f, 0.25f, 0xFF1C1634.toInt())
            radial(32f, 32f, 26f, Pal.withAlpha(Pal.VIOLET, 0.28f), 0x00000000)
            ring(32f, 32f, 12.5f, 0.5f, Pal.withAlpha(Pal.CYAN, 0.9f))
            ring(32f, 32f, 19f, 0.35f, Pal.withAlpha(Pal.HOTPINK, 0.7f))
            ring(32f, 32f, 25.5f, 0.3f, Pal.withAlpha(Pal.CYAN, 0.45f))
            // Hazard chevrons at the four corners.
            for (k in 0 until 4) {
                val ang = k * 1.5708f + 0.7854f
                for (s in 0 until 3) {
                    val d = 30f + s * 1.6f
                    circle(32f + cos(ang) * d * 1.05f, 32f + sin(ang) * d * 1.05f, 0.55f, Pal.withAlpha(Pal.YELLOW, 0.7f))
                }
            }
            hgrad(0f, 0f, 8f, 64f, 0x90000000.toInt(), 0)
            hgrad(56f, 0f, 8f, 64f, 0, 0x90000000.toInt())
            vgrad(0f, 0f, 64f, 8f, 0x90000000.toInt(), 0)
            vgrad(0f, 56f, 64f, 8f, 0, 0x90000000.toInt())
        }
    }

    val steel: Texture by lazy {
        paintTexture(8, 16, 4) {
            vgrad(0f, 0f, 8f, 16f, 0xFF4C526C.toInt(), 0xFF1C2034.toInt())
            rect(1.4f, 0f, 1.2f, 16f, Pal.withAlpha(Pal.WHITE, 0.2f))
        }
    }

    /** A thin ring, soft either side, for shockwaves. */
    val ring: Texture by lazy {
        paintTexture(32, 32, 4) {
            clear(0)
            paint.reset()
            paint.isAntiAlias = true
            paint.shader = RadialGradient(
                16f, 16f, 15.5f,
                intArrayOf(CLEAR_WHITE, CLEAR_WHITE, Pal.WHITE, CLEAR_WHITE), floatArrayOf(0f, 0.72f, 0.88f, 1f), Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(16f, 16f, 15.5f, paint)
            paint.shader = null
        }
    }

    /** A four-point glint with a hot core. */
    val flare: Texture by lazy {
        paintTexture(32, 32, 4) {
            clear(0)
            radial(16f, 16f, 14f, Pal.withAlpha(Pal.WHITE, 0.8f), CLEAR_WHITE)
            oval(16f, 16f, 15.5f, 0.8f, Pal.withAlpha(Pal.WHITE, 0.9f))
            oval(16f, 16f, 0.8f, 15.5f, Pal.withAlpha(Pal.WHITE, 0.9f))
            circle(16f, 16f, 2.2f, Pal.WHITE)
        }
    }

    /** A column of light: a soft bell across, fading to nothing towards the end of the beam. */
    val shaft: Texture by lazy {
        paintTexture(16, 64, 2) {
            clear(0)
            hgrad(0f, 0f, 16f, 64f, CLEAR_WHITE, Pal.WHITE, CLEAR_WHITE)
            paint.reset()
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            paint.shader = LinearGradient(0f, 0f, 0f, 64f, Pal.WHITE, CLEAR_WHITE, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 16f, 64f, paint)
            paint.shader = null
            paint.xfermode = null
        }
    }

    /** Height markers "10", "20" ... "100" in the game's pixel type, one 36 × 16 cell each. */
    private val markers: Texture by lazy {
        paintTexture(360, 16, 4) {
            clear(0)
            for (i in 0 until 10) {
                val text = ((i + 1) * 10).toString()
                glow(1.2f, Pal.withAlpha(Pal.CYAN, 0.8f)) { label(text, i * 36f + 18f, 3f, 10f, -1) }
                label(text, i * 36f + 18f, 3f, 10f, Pal.mix(Pal.CYAN, Pal.WHITE, 0.6f))
            }
        }
    }

    /** The marker for height [tens] × 10 (1..10); higher ones reuse the last. */
    fun marker(tens: Int): Region = markerRegions[(tens - 1).coerceIn(0, 9)]
    private val markerRegions: Array<Region> by lazy { Array(10) { markers.region(it * 36, 0, 36, 16) } }
}
