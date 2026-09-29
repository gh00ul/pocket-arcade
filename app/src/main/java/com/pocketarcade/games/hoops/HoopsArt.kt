package com.pocketarcade.games.hoops

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Fonts
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.Region
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture
import kotlin.math.PI
import kotlin.math.sin

/**
 * Painted art and the ball for the 3D basketball alley. Everything is built lazily on first use
 * (the headless tests never draw), and the palette is dark on purpose: the pale surfaces the
 * bloom would turn to white are kept for the things that should glow.
 */
internal object HoopsArt {
    // The wood is a mid brown, so the warm lamp above the court still leaves it under the
    // bloom threshold (maple at full brightness burns white).
    private const val WOOD_DARK = 0xFF5C3A1E.toInt()
    private const val WOOD_LIGHT = 0xFF80552C.toInt()
    /** Court paint: cream lines a little under white and a deep crimson key. */
    private const val LINE = 0xFFDCCFAE.toInt()
    private const val KEY_PAINT = 0xFFA82634.toInt()
    /** Transparent white: soft gradients fade to this so their middles don't turn grey. */
    private const val CLEAR_WHITE = 0x00FFFFFF

    /** The crowd wall: 208 units span the 520 cm of wall (2.5 cm a unit), top to bottom. */
    private const val CROWD_W = 208
    private const val CROWD_H = 208

    /** Size of the ad-board strip in texture units (200 by 17 for a 400 by 34 cm board). */
    private const val AD_W = 200
    private const val AD_H = 17

    private fun TexPaint.arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float, width: Float, color: Int) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color
        canvas.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), start, sweep, false, paint)
    }

    /**
     * The alley floor: polished planks with the painted key, free-throw circle and three-point
     * arc under the hoop (the top of the texture is the far end), and a mid-court roundel.
     */
    val floor: Texture by lazy {
        paintTexture(100, 190, 4) {
            for (x in 0 until 100 step 5) {
                val tone = 0.86f + hash01(x, 51) * 0.28f
                val col = Pal.shade(Pal.mix(WOOD_DARK, WOOD_LIGHT, hash01(x, 54)), tone)
                hgrad(x.toFloat(), 0f, 5f, 190f, Pal.shade(col, 0.92f), col, Pal.shade(col, 0.92f))
                rect(x.toFloat(), 0f, 0.35f, 190f, Pal.shade(col, 0.5f))
                var y = hash01(x, 52) * 40f
                while (y < 190f) {
                    rect(x.toFloat(), y, 5f, 0.35f, Pal.shade(col, 0.55f))
                    y += 45f + hash01(x + y.toInt(), 53) * 30f
                }
                for (k in 0 until 3) {
                    val gx = x + 0.8f + hash01(x, 60 + k) * 3.4f
                    line(gx, 0f, gx + hash01(x, 64 + k) * 0.6f - 0.3f, 190f, 0.25f, Pal.withAlpha(Pal.shade(col, 0.6f), 0.35f))
                }
            }
            // Painted key (towards the hoop), free-throw circle, three-point arc and roundel.
            rect(36f, 0f, 28f, 68f, Pal.withAlpha(KEY_PAINT, 0.6f))
            rect(35.3f, 0f, 1.4f, 69f, LINE)
            rect(63.3f, 0f, 1.4f, 69f, LINE)
            rect(35.3f, 67.6f, 29.4f, 1.4f, LINE)
            arc(50f, 68f, 14f, 180f, 180f, 1.4f, LINE)
            for (k in 0 until 6) arc(50f, 68f, 14f, k * 30f + 4f, 16f, 1.4f, LINE)
            arc(50f, 30f, 44f, 0f, 180f, 1.4f, LINE)
            rect(5.3f, 0f, 1.4f, 30f, LINE)
            rect(93.3f, 0f, 1.4f, 30f, LINE)
            ring(50f, 112f, 24f, 1.6f, Pal.withAlpha(LINE, 0.7f))
            star(50f, 112.5f, 13f, Pal.withAlpha(KEY_PAINT, 0.75f))
            // Contact shadow along the far wall and the sidelines.
            vgrad(0f, 0f, 100f, 24f, 0xB0060310.toInt(), 0)
            hgrad(0f, 0f, 9f, 190f, 0x90060310.toInt(), 0)
            hgrad(91f, 0f, 9f, 190f, 0, 0x90060310.toInt())
        }
    }

    /** Dark carpet beyond the cage. */
    val apron: Texture by lazy {
        paintTexture(16, 16, 4) {
            fill(0xFF130D26.toInt())
            for (i in 0 until 34) circle(hash01(i, 81) * 16f, hash01(i, 82) * 16f, 0.3f, 0xFF20173A.toInt())
        }
    }

    /**
     * The wall behind the hoop, painted as an arena: seven tiers of crowd silhouettes that grow
     * smaller and darker towards the roof, rim-lit heads, raised arms and the odd phone light,
     * under a haze of arena light. Dark on purpose: the hoop is the brightest thing in the room.
     */
    val crowd: Texture by lazy {
        paintTexture(CROWD_W, CROWD_H, 3) {
            val w = CROWD_W.toFloat()
            val h = CROWD_H.toFloat()
            vgrad(0f, 0f, w, h, 0xFF040209.toInt(), 0xFF0B0619.toInt(), 0xFF170D30.toInt(), 0xFF1D1139.toInt())
            radial(w / 2f, h * 0.36f, w * 0.36f, Pal.withAlpha(Pal.INDIGO, 0.3f), 0)
            val phones = intArrayOf(0xFFDDEEFF.toInt(), 0xFFFFE9A8.toInt(), 0xFFFFB8E0.toInt())
            val rows = 7
            // Far rows first, so the near ones overlap them.
            for (k in rows - 1 downTo 0) {
                val depth = k / (rows - 1f)
                val baseV = 192f - k * 13.5f
                val r = 1.95f - depth * 0.75f
                val step = r * 3.5f
                val body = Pal.mix(0xFF2A1A4E.toInt(), 0xFF0C0718.toInt(), depth)
                val skin = Pal.mix(0xFF3A2762.toInt(), 0xFF120B24.toInt(), depth)
                val rimLit = Pal.mix(0xFF60419E.toInt(), 0xFF2A1B52.toInt(), depth)
                var i = 0
                var cx = hash01(k, 71) * step
                while (cx < w + step) {
                    val n = hash01(i, k, 72)
                    val cy = baseV - r * 3f + (n - 0.5f) * r * 0.8f
                    round(cx - r * 1.35f, cy + r * 1.1f, r * 2.7f, r * 6f, r * 0.9f, body)
                    circle(cx, cy, r, rimLit)
                    circle(cx + r * 0.12f, cy + r * 0.16f, r * 0.9f, skin)
                    if (n > 0.93f) line(cx + r, cy + r * 1.4f, cx + r * 1.7f + n, cy - r * 2.6f, r * 0.45f, body)
                    if (hash01(i, k, 73) > 0.9f) circle(cx + r * 1.15f, cy - r * 0.4f, r * 0.26f, phones[(i + k) % 3])
                    cx += step * (0.9f + hash01(i, k, 74) * 0.25f)
                    i++
                }
            }
            rect(0f, 199f, w, 9f, 0xFF0A0614.toInt())
        }
    }

    /** The neon HOOP SHOT sign: a crisp tube outline and lettering, and a separate blurred halo, both white to be tinted. */
    val logoCore: Texture by lazy {
        paintTexture(100, 24, 6) {
            clear(0)
            strokeRound(1.6f, 1.6f, 96.8f, 20.8f, 5f, 0.9f, Pal.WHITE)
            text("HOOP SHOT", 50f, 16.6f, 12.4f, Pal.WHITE, spacing = 0.05f)
        }
    }
    val logoHalo: Texture by lazy {
        paintTexture(100, 24, 6) {
            clear(0)
            glow(2.6f, Pal.withAlpha(Pal.WHITE, 0.95f)) {
                strokeRound(1.6f, 1.6f, 96.8f, 20.8f, 5f, 2.2f, -1)
                text("HOOP SHOT", 50f, 16.6f, 12.4f, -1, spacing = 0.05f)
            }
        }
    }

    /** LED ad boards along the foot of the wall: four lit cells. */
    val adBoard: Texture by lazy {
        paintTexture(AD_W, AD_H, 5) {
            fill(0xFF07050D.toInt())
            val cells = arrayOf("HOOP SHOT", "SWISH +3", "x5 ON FIRE", "ARCADE")
            val colors = intArrayOf(Pal.ORANGE, Pal.CYAN, Pal.YELLOW, Pal.PINK)
            for (i in 0 until 4) {
                val x = i * 50f
                strokeRound(x + 2f, 1.6f, 46f, 13.8f, 2.5f, 0.7f, Pal.shade(colors[i], 0.55f))
                glow(1.2f, Pal.withAlpha(colors[i], 0.8f)) { text(cells[i], x + 25f, 11.2f, 7.4f, -1, Fonts.heavy) }
                text(cells[i], x + 25f, 11.2f, 7.4f, Pal.mix(colors[i], Pal.WHITE, 0.35f), Fonts.heavy)
            }
        }
    }

    /** Gunmetal for posts, the backboard frame and brackets, with a thin highlight down one side. */
    val steel: Texture by lazy {
        paintTexture(8, 16, 4) {
            vgrad(0f, 0f, 8f, 16f, 0xFF565C74.toInt(), 0xFF262A3C.toInt())
            rect(1.4f, 0f, 1.2f, 16f, Pal.withAlpha(Pal.WHITE, 0.22f))
        }
    }

    /** Padding round the foot of the stanchion: dark red with an orange piping. */
    val pad: Texture by lazy {
        paintTexture(16, 32, 4) {
            vgrad(0f, 0f, 16f, 32f, Pal.shade(Pal.RED, 0.62f), Pal.shade(Pal.DARKRED, 0.5f))
            rect(0f, 0f, 16f, 1.6f, Pal.shade(Pal.ORANGE, 0.8f))
            for (y in 8 until 32 step 8) rect(0f, y.toFloat(), 16f, 0.5f, Pal.withAlpha(Pal.BLACK, 0.4f))
        }
    }

    /** The backboard: midnight glass with an LED matrix, dim markings (they light up through [boardMarks]) and a faint sheen. */
    val boardFace: Texture by lazy {
        paintTexture(120, 85, 4) {
            // Blue kept low (the lamp and spot add to it) so the glass stays under the bloom threshold.
            vgrad(0f, 0f, 120f, 85f, 0xFF162456.toInt(), 0xFF0A102C.toInt())
            for (x in 0 until 120 step 3) rect(x.toFloat(), 0f, 0.25f, 85f, Pal.withAlpha(Pal.WHITE, 0.035f))
            for (y in 2 until 85 step 4) for (x in 2 until 120 step 4) circle(x.toFloat(), y.toFloat(), 0.42f, Pal.withAlpha(Pal.SKY, 0.16f))
            val dim = Pal.shade(Pal.RED, 0.5f)
            strokeRound(3f, 3f, 114f, 79f, 3f, 1.6f, dim)
            strokeRound(42f, 44f, 36f, 26f, 1f, 1.8f, dim)
            polygon(floatArrayOf(6f, 6f, 30f, 6f, 10f, 40f, 6f, 40f), 0x14FFFFFF)
            polygon(floatArrayOf(36f, 6f, 46f, 6f, 26f, 40f, 16f, 40f), 0x0EFFFFFF)
        }
    }

    /** The same markings as [boardFace] in white with a halo, drawn additively in the game's colour. */
    val boardMarks: Texture by lazy {
        paintTexture(120, 85, 4) {
            clear(0)
            glow(2.2f, Pal.withAlpha(Pal.WHITE, 0.85f)) {
                strokeRound(3f, 3f, 114f, 79f, 3f, 1.6f, -1)
                strokeRound(42f, 44f, 36f, 26f, 1f, 1.8f, -1)
            }
            strokeRound(3f, 3f, 114f, 79f, 3f, 1.2f, Pal.WHITE)
            strokeRound(42f, 44f, 36f, 26f, 1f, 1.4f, Pal.WHITE)
        }
    }

    /** A soft slanted band that sweeps across the glass now and then. */
    val glint: Texture by lazy {
        paintTexture(32, 8, 4) { hgrad(0f, 0f, 32f, 8f, CLEAR_WHITE, Pal.withAlpha(Pal.WHITE, 0.55f), CLEAR_WHITE) }
    }

    /** The rim: enamelled orange, brighter on top. */
    val rim: Texture by lazy {
        paintTexture(32, 8, 4) { vgrad(0f, 0f, 32f, 8f, Pal.mix(Pal.ORANGE, Pal.YELLOW, 0.3f), Pal.ORANGE, Pal.shade(Pal.RED, 0.75f)) }
    }

    /** Diamond mesh for the cage nets: tiles seamlessly (a wrapping region), alpha-blended. */
    val cageNet: Texture by lazy {
        paintTexture(16, 16, 8) {
            clear(0)
            val c = Pal.withAlpha(0xFF9CB4E8.toInt(), 0.55f)
            line(-2f, -2f, 18f, 18f, 0.8f, c, round = false)
            line(-2f, 18f, 18f, -2f, 0.8f, c, round = false)
        }
    }
    val cageNetRegion: Region by lazy { cageNet.region(wrap = true) }

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

    /** A four-point glint with a hot core, for swishes and camera flashes. */
    val flare: Texture by lazy {
        paintTexture(32, 32, 4) {
            clear(0)
            radial(16f, 16f, 14f, Pal.withAlpha(Pal.WHITE, 0.8f), 0)
            oval(16f, 16f, 15.5f, 0.8f, Pal.withAlpha(Pal.WHITE, 0.9f))
            oval(16f, 16f, 0.8f, 15.5f, Pal.withAlpha(Pal.WHITE, 0.9f))
            circle(16f, 16f, 2.2f, Pal.WHITE)
        }
    }

    /** A beam of light: brightest at its start (the top of the texture), a soft bell across, fading to nothing. */
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

    /**
     * A small LED readout for the back wall: a caption over big digits, repainted only when the
     * value changes. Its texture is opaque, so it draws as a solid, glowing face.
     */
    class Readout(private val caption: String, private val accent: Int) {
        private val painter by lazy { TexPaint(W * SCALE, H * SCALE).also { it.useUnits(SCALE.toFloat()) } }
        val tex: Texture by lazy { Texture(W, H, IntArray(W * SCALE * H * SCALE), SCALE) }
        private var lastKey = Int.MIN_VALUE

        fun stale(key: Int) = key != lastKey

        /** Paints [text] in [color] under the caption; [key] identifies what was painted. */
        fun paint(key: Int, text: String, color: Int) {
            lastKey = key
            with(painter) {
                vgrad(0f, 0f, W.toFloat(), H.toFloat(), 0xFF130D26.toInt(), 0xFF06040C.toInt())
                for (y in 0 until H step 2) rect(0f, y.toFloat(), W.toFloat(), 0.5f, Pal.withAlpha(Pal.NIGHT, 0.5f))
                strokeRound(0.6f, 0.6f, W - 1.2f, H - 1.2f, 3f, 1.2f, Pal.shade(accent, 0.7f))
                label(caption, W / 2f, 3.2f, 5f, Pal.LAVENDER, tiny = true)
                glow(1.4f, Pal.withAlpha(color, 0.8f)) { label(text, W / 2f, 11.5f, 16f, -1) }
                label(text, W / 2f, 11.5f, 16f, color)
                update(tex)
            }
        }

        companion object {
            const val W = 64
            const val H = 34
            private const val SCALE = 4
        }
    }

    /** Builds the basketball: pebbled orange leather with black seams. */
    fun ball(radius: Float): Model {
        val tex = paintTexture(128, 64, 4) {
            vgrad(0f, 0f, 128f, 64f, Pal.mix(Pal.ORANGE, Pal.YELLOW, 0.12f), Pal.shade(Pal.ORANGE, 0.78f))
            for (i in 0 until 1200) {
                val dark = hash01(i, 63) > 0.4f
                circle(
                    hash01(i, 61) * 128f, hash01(i, 62) * 64f, 0.35f,
                    Pal.withAlpha(if (dark) Pal.shade(Pal.ORANGE, 0.62f) else Pal.mix(Pal.ORANGE, Pal.YELLOW, 0.5f), 0.55f),
                )
            }
            val seam = 0xFF1E0E06.toInt()
            rect(0f, 31.2f, 128f, 1.6f, seam)
            rect(0f, 0f, 1.6f, 64f, seam)
            rect(63.2f, 0f, 1.6f, 64f, seam)
            // The two curved seams, bowing round the ball.
            for (centre in floatArrayOf(32f, 96f)) {
                var prevX = 0f
                var prevY = 0f
                for (k in 0..32) {
                    val v = k / 32f
                    val x = centre + (if (centre < 64f) 1f else -1f) * 14f * sin(v * PI.toFloat())
                    val y = v * 64f
                    if (k > 0) line(prevX, prevY, x, y, 1.6f, seam)
                    prevX = x
                    prevY = y
                }
            }
        }
        return ModelBuilder().sphere(0f, 0f, 0f, radius, tex.full, slices = 22, stacks = 14, gloss = 0.4f).build()
    }
}
