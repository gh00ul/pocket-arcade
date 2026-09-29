package com.pocketarcade.games.pinball

import android.graphics.Paint
import android.graphics.RectF
import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.Blend
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import com.pocketarcade.games.pinball.PinballTable as T

/**
 * Painted textures and models for the pinball table and its hall cabinet. Everything is built
 * lazily on first draw (never from unit tests). World units are table units: x across, z down
 * the table, y up from the playfield.
 */
internal object PinballArt {
    /** Texels per table unit on the playfield. */
    private const val TPU = 1.28f
    /** The backbox's front face, behind the top of the table. */
    const val BACKBOX_Z = -18f
    const val BACKBOX_TOP = 214f

    /** The backglass: a 308 x 196 window in the backbox front, one texel per world unit. */
    const val GLASS_X0 = -4f
    const val GLASS_X1 = 304f
    const val GLASS_Y0 = 8f
    const val GLASS_Y1 = 204f

    /**
     * The dot-matrix display's window in the backglass (world units). It keeps the display's 4:1
     * shape, so the dots come out square.
     */
    const val DMD_X0 = 34f
    const val DMD_X1 = 266f
    const val DMD_BOTTOM = 26f
    const val DMD_TOP = DMD_BOTTOM + (DMD_X1 - DMD_X0) * DotMatrix.ROWS / DotMatrix.COLS

    private const val RAIL_H = 22f
    private const val GUIDE_H = 10f
    private const val RUBBER_H = 9f

    /** Half-width of the steel cap along the top of each wall, per style (world units). */
    private const val RAIL_CAP = 1.7f
    private const val GUIDE_CAP = 1.0f
    private const val RUBBER_CAP = 0.9f

    /** Width of the floor light strip along the outer rails, and how far in from the wall it sits. */
    private const val STRIP_W = 7f
    private const val STRIP_IN = 4.5f

    private fun TexPaint.stars(w: Float, h: Float, n: Int, seed: Int, color: Int) {
        for (i in 0 until n) {
            val x = hash01(i, seed) * w
            val y = hash01(i, seed + 1) * h
            val s = 0.5f + hash01(i, seed + 2) * 1.6f
            if (i % 5 == 0) star(x, y, s * 2.2f, color) else circle(x, y, s * 0.5f, Pal.withAlpha(color, 0.5f + hash01(i, seed + 3) * 0.5f))
        }
    }

    /** An arrow insert pointing along [angle] (radians, y down), painted unlit. */
    private fun TexPaint.arrow(cx: Float, cy: Float, size: Float, angle: Float, color: Int) {
        val c = cos(angle)
        val s = sin(angle)
        fun px(u: Float, v: Float) = cx + c * u - s * v
        fun py(u: Float, v: Float) = cy + s * u + c * v
        val pts = floatArrayOf(
            px(size, 0f), py(size, 0f),
            px(-size * 0.4f, size * 0.7f), py(-size * 0.4f, size * 0.7f),
            px(-size * 0.1f, 0f), py(-size * 0.1f, 0f),
            px(-size * 0.4f, -size * 0.7f), py(-size * 0.4f, -size * 0.7f),
        )
        // A dark keyline under the insert so it reads against the busy sunburst.
        val edge = FloatArray(pts.size)
        for (i in pts.indices step 2) {
            edge[i] = cx + (pts[i] - cx) * 1.22f
            edge[i + 1] = cy + (pts[i + 1] - cy) * 1.22f
        }
        polygon(edge, 0x66000000)
        polygon(pts, Pal.shade(color, 0.35f))
        val inner = FloatArray(pts.size)
        for (i in pts.indices step 2) {
            inner[i] = cx + (pts[i] - cx) * 0.7f
            inner[i + 1] = cy + (pts[i + 1] - cy) * 0.7f
        }
        polygon(inner, Pal.shade(color, 0.55f))
    }

    /** A round insert with a ring, unlit. */
    private fun TexPaint.insert(cx: Float, cy: Float, r: Float, color: Int) {
        circle(cx, cy, r + 2.6f, 0x55000000)
        circle(cx, cy, r + 1.5f, Pal.shade(color, 0.2f))
        radial(cx, cy, r, Pal.shade(color, 0.6f), Pal.shade(color, 0.3f))
        ring(cx, cy, r, 1f, Pal.withAlpha(Pal.WHITE, 0.35f))
    }

    /** A stroked arc of an ellipse, for planet rings; angles in degrees, clockwise from +x. */
    private fun TexPaint.arc(cx: Float, cy: Float, rx: Float, ry: Float, from: Float, sweep: Float, width: Float, color: Int) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color
        canvas.drawArc(RectF(cx - rx, cy - ry, cx + rx, cy + ry), from, sweep, false, paint)
    }

    /** A soft shadow along a wall: three strokes of falling strength stand in for a blur. */
    private fun TexPaint.wallShadow(x0: Float, y0: Float, x1: Float, y1: Float, k: Float, reach: Float) {
        line(x0 * k, y0 * k, x1 * k, y1 * k, reach * k, 0x16000000)
        line(x0 * k, y0 * k, x1 * k, y1 * k, reach * 0.62f * k, 0x22000000)
        line(x0 * k, y0 * k, x1 * k, y1 * k, reach * 0.3f * k, 0x38000000)
    }

    /** The playfield: a neon starfield with its inserts, lanes and markings. */
    val playfield: Texture by lazy {
        val k = TPU
        val w = 384f
        val h = 820f
        paintTexture(384, 820, 2) {
            vgrad(0f, 0f, w, h, 0xFF0A0B30.toInt(), 0xFF1B1150.toInt(), 0xFF2A0E47.toInt(), 0xFF140828.toInt())
            // A sunburst behind the pop bumpers.
            val sx = T.CX * k
            val sy = 175f * k
            for (i in 0 until 24) {
                val a0 = i * 2f * PI.toFloat() / 24f
                val a1 = a0 + PI.toFloat() / 24f
                polygon(
                    floatArrayOf(sx, sy, sx + cos(a0) * 420f, sy + sin(a0) * 420f, sx + cos(a1) * 420f, sy + sin(a1) * 420f),
                    if (i % 2 == 0) 0x1CFF3FA4 else 0x143DF5FF,
                )
            }
            radial(sx, sy, 150f, 0x55FF77C8, 0)
            stars(w, h, 170, 5, Pal.LAVENDER)
            // A comet crossing the lower table, behind the name: a tapering tail and a bright head.
            for (i in 0 until 6) {
                val t0 = i / 6f
                line(
                    (T.CX + 88f - t0 * 118f) * k, (388f + t0 * 26f) * k, (T.CX + 88f - (t0 + 0.17f) * 118f) * k, (388f + (t0 + 0.17f) * 26f) * k,
                    (5.5f - i * 0.8f) * k, Pal.withAlpha(Pal.CYAN, 0.13f - i * 0.018f),
                )
            }
            radial((T.CX + 88f) * k, 388f * k, 9f * k, 0x88FFFFFF.toInt(), 0)
            // Lower-table swoosh and the machine's name.
            radial(T.CX * k, 440f * k, 120f, 0x40FF3FA4, 0)
            glow(4f, Pal.withAlpha(Pal.HOTPINK, 0.9f)) { label("STAR", T.CX * k, 418f * k, 20f, -1) }
            label("STAR", T.CX * k, 418f * k, 20f, Pal.CREAM)
            glow(4f, Pal.withAlpha(Pal.CYAN, 0.9f)) { label("FLIPPER", T.CX * k, 446f * k, 16f, -1) }
            label("FLIPPER", T.CX * k, 446f * k, 16f, Pal.CREAM)
            // The left orbit channel and the shooter lane.
            vgrad(0f, T.ORBIT_Y0 * k, T.ORBIT_X * k, (T.ORBIT_Y1 - T.ORBIT_Y0) * k, 0xFF101238.toInt(), 0xFF1A0E3C.toInt())
            for (i in 0 until 6) arrow(T.ORBIT_X * k / 2f, (300f - i * 30f) * k, 7f, -PI.toFloat() / 2f, Pal.CYAN)
            vgrad(T.PLAY_W * k, 0f, (T.W - T.PLAY_W) * k, h, 0xFF15122A.toInt(), 0xFF0C0A1A.toInt())
            for (i in 0 until 5) arrow(T.LANE_X * k, (520f - i * 60f) * k, 7f, -PI.toFloat() / 2f, Pal.YELLOW)
            // Contact shadows where the walls, guides and posts meet the playfield: ambient
            // occlusion painted into the floor, so every rail and pin sits in the table.
            for (i in 0 until T.segCount) {
                when (T.style[i]) {
                    T.STYLE_RAIL -> wallShadow(T.ax[i], T.ay[i], T.bx[i], T.by[i], k, 15f)
                    T.STYLE_GUIDE -> wallShadow(T.ax[i], T.ay[i], T.bx[i], T.by[i], k, 9f)
                    else -> wallShadow(T.ax[i], T.ay[i], T.bx[i], T.by[i], k, 6f)
                }
            }
            for (i in 0 until T.postCount) {
                circle(T.px[i] * k, T.py[i] * k, (T.pr[i] + 4.5f) * k, 0x1E000000)
                circle(T.px[i] * k, T.py[i] * k, (T.pr[i] + 2.5f) * k, 0x30000000)
            }
            // Top lanes: arrows below each rollover, lane names above.
            for (i in 0 until 3) {
                arrow(T.ROLLOVER_X[i] * k, (T.ROLLOVER_Y + 34f) * k, 11f, PI.toFloat() / 2f, Pal.SKY)
                rect((T.ROLLOVER_X[i] - 5f) * k, (T.ROLLOVER_Y - 1f) * k, 10f * k, 2f * k, Pal.LIGHTGRAY)
            }
            label("S", T.ROLLOVER_X[0] * k, 20f * k, 9f, Pal.SKY)
            label("T", T.ROLLOVER_X[1] * k, 20f * k, 9f, Pal.SKY)
            label("R", T.ROLLOVER_X[2] * k, 20f * k, 9f, Pal.SKY)
            // Bumper footprints.
            for (i in 0 until 3) {
                circle(T.BUMPER_X[i] * k, T.BUMPER_Y[i] * k, (T.BUMPER_R + 8f) * k, 0x30000000)
                circle(T.BUMPER_X[i] * k, T.BUMPER_Y[i] * k, (T.BUMPER_R + 5f) * k, 0x66000000)
                ring(T.BUMPER_X[i] * k, T.BUMPER_Y[i] * k, (T.BUMPER_R + 3f) * k, 1.5f, Pal.withAlpha(Pal.YELLOW, 0.6f))
            }
            // Multiplier row, the orbit/jackpot arrow, drop target lamps and shoot-again.
            val mult = arrayOf("2X", "3X", "4X", "5X")
            for (i in 0 until 4) {
                val x = (T.CX - 54f + i * 36f) * k
                insert(x, 372f * k, 11f, Pal.GOLD)
                label(mult[i], x, 372f * k - 5f, 8f, Pal.CREAM, shadow = Pal.BLACK)
            }
            label("MULTIPLIER", T.CX * k, 390f * k, 7f, Pal.GOLD)
            arrow(22f * k, 350f * k, 16f, -PI.toFloat() / 2f - 0.35f, Pal.GOLD)
            label("JACKPOT", 26f * k, 372f * k, 5.5f, Pal.GOLD)
            for (i in 0 until 3) insert((T.DROP_X - 22f) * k, (T.DROP_Y0[i] + T.DROP_LEN / 2f) * k, 6f, Pal.ORANGE)
            insert(T.CX * k, 568f * k, 12f, Pal.RED)
            label("SHOOT", T.CX * k, 560f * k, 5.5f, Pal.CREAM, shadow = Pal.BLACK)
            label("AGAIN", T.CX * k, 568f * k, 5.5f, Pal.CREAM, shadow = Pal.BLACK)
            // Inlane and outlane arrows.
            for (side in 0..1) {
                val ox = if (side == 0) 11f else T.PLAY_W - 11f
                val ix = if (side == 0) 36f else T.PLAY_W - 36f
                arrow(ox * k, 430f * k, 7f, PI.toFloat() / 2f, Pal.RED)
                arrow(ix * k, 440f * k, 7f, PI.toFloat() / 2f, Pal.LIME)
            }
            // Flipper zone shading, and a soft pool of shadow under where the flippers work.
            vgrad(0f, 480f * k, T.PLAY_W * k, 110f * k, 0, 0x66000000)
            grain(0.04f, 17)
        }
    }

    /**
     * How much of the apron, from its front edge, carries the printed art: the player's view
     * ends about 50 units down it, so the cards and lettering all sit inside this strip.
     */
    const val APRON_PRINT = 40f

    /** The apron over the drain: instruction cards either side of the lit lettering. */
    val apron: Texture by lazy {
        paintTexture(272, APRON_PRINT.toInt(), 2) {
            val h = APRON_PRINT
            vgrad(0f, 0f, 272f, h, 0xFF2B1A5C.toInt(), 0xFF140C30.toInt())
            rect(0f, 0f, 272f, 2f, Pal.CYAN)
            roundGrad(10f, 7f, 80f, 27f, 4f, Pal.CREAM, Pal.TAN)
            label("3 BALLS", 50f, 11f, 6f, Pal.DARKBROWN)
            label("LANES=MULTI", 50f, 22f, 5f, Pal.DARKBROWN)
            roundGrad(182f, 7f, 80f, 27f, 4f, Pal.CREAM, Pal.TAN)
            label("BANK=MULT", 222f, 11f, 5f, Pal.DARKBROWN)
            label("ORBIT=JACKPOT", 222f, 22f, 4.5f, Pal.DARKBROWN)
            glow(3f, Pal.withAlpha(Pal.HOTPINK, 0.8f)) { label("FLIP", 136f, 9f, 14f, -1) }
            label("FLIP", 136f, 9f, 14f, Pal.CREAM)
            rect(96f, 29f, 80f, 1.5f, Pal.withAlpha(Pal.CYAN, 0.7f))
        }
    }

    /** The rest of the apron, down to the front of the cabinet: plain moulded plastic. */
    private val apronBack: Texture by lazy {
        paintTexture(32, 32, 2) { vgrad(0f, 0f, 32f, 32f, 0xFF140C30.toInt(), 0xFF0C0820.toInt()) }
    }

    /**
     * Backbox front: the backglass art above the display. It is 308 x 196 texels for the
     * [GLASS_X0]..[GLASS_X1] by [GLASS_Y0]..[GLASS_Y1] window, so a texel is a world unit: a
     * space horizon with a ringed planet and a moon, a neon city skyline, the extruded title,
     * and the display's bezel with lamp columns either side. Kept dark and saturated behind the
     * lettering, so the glass glows in colour rather than blowing out to white.
     */
    val backglass: Texture by lazy {
        val w = 308f
        val h = 196f
        paintTexture(308, 196, 2) {
            vgrad(0f, 0f, w, h, 0xFF0A0520.toInt(), 0xFF1E0E48.toInt(), 0xFF4C1668.toInt(), 0xFF2A0C46.toInt(), 0xFF0E0620.toInt())
            radial(w / 2f, 100f, 170f, 0x40FF3FA4, 0)
            stars(w, 96f, 55, 9, Pal.CREAM)
            // The moon, low and left, with a few craters.
            circle(52f, 46f, 12f, 0xFFB9A8E8.toInt())
            radial(48f, 42f, 12f, 0x77FFFFFF, 0)
            circle(56f, 50f, 3f, 0x33403060)
            circle(46f, 51f, 2f, 0x33403060)
            // A ringed planet: back half of the ring, the ball, then the front half.
            canvas.save()
            canvas.rotate(-14f, 246f, 58f)
            arc(246f, 58f, 56f, 12f, 180f, 180f, 3.4f, 0x77B896FF)
            canvas.restore()
            ball(246f, 58f, 30f, 0xFF5A32C2.toInt(), 0.35f)
            radial(238f, 48f, 24f, 0x30FFB0FF, 0)
            canvas.save()
            canvas.rotate(-14f, 246f, 58f)
            arc(246f, 58f, 56f, 12f, 0f, 180f, 4.2f, 0xCCD8B8FF.toInt())
            arc(246f, 58f, 47f, 9f, 0f, 180f, 1.6f, 0x88FFA0E0.toInt())
            canvas.restore()
            // A neon city skyline along the horizon, with lit windows and a glowing kerb.
            for (i in 0 until 31) {
                val bx = i * 10f
                val bh = 5f + hash01(i, 3) * 12f
                rect(bx, 108f - bh, 9f, bh, 0xFF0A0418.toInt())
                var wy = 108f - bh + 2f
                while (wy < 106f) {
                    if (hash01(i, wy.toInt() + 40) > 0.5f) {
                        rect(bx + 1.6f, wy, 1.4f, 1.4f, if (hash01(i, wy.toInt()) > 0.5f) 0xFFFFC85A.toInt() else 0xFF5AE8FF.toInt())
                    }
                    if (hash01(i + 90, wy.toInt() + 40) > 0.55f) {
                        rect(bx + 5.4f, wy, 1.4f, 1.4f, 0xFFFF7ACC.toInt())
                    }
                    wy += 3.6f
                }
            }
            vgrad(0f, 96f, w, 12f, 0, 0x66FF3FA4)
            rect(0f, 107.5f, w, 2f, Pal.withAlpha(Pal.HOTPINK, 0.9f))
            // The title, extruded: dark copies stepped down and right, then the face on top.
            for (d in 4 downTo 1) {
                label("STAR", 154f + d * 0.8f, 4f + d * 1.1f, 40f, Pal.shade(Pal.PINK, 0.32f + 0.05f * (4 - d)))
                label("FLIPPER", 154f + d * 0.6f, 50f + d * 0.9f, 28f, Pal.shade(Pal.CYAN, 0.26f + 0.05f * (4 - d)))
            }
            glow(7f, Pal.withAlpha(Pal.HOTPINK, 0.85f)) { label("STAR", 154f, 4f, 40f, -1) }
            label("STAR", 154f, 4f, 40f, Pal.YELLOW, shadow = Pal.BLACK)
            glow(6f, Pal.withAlpha(Pal.CYAN, 0.85f)) { label("FLIPPER", 154f, 50f, 28f, -1) }
            label("FLIPPER", 154f, 50f, 28f, Pal.CREAM, shadow = Pal.BLACK)
            label("SPACE PINBALL", 154f, 84f, 5.5f, Pal.LAVENDER, tiny = true)
            // The display's bezel: a chamfered dark frame, a bright top edge, four screws.
            roundGrad(30f, 112f, 248f, 74f, 7f, 0xFF44444F.toInt(), 0xFF0C0C12.toInt())
            round(33f, 115f, 242f, 68f, 5f, 0xFF06060A.toInt())
            rect(38f, 120f, 232f, 58f, 0xFF120600.toInt())
            line(36f, 114.4f, 272f, 114.4f, 1f, 0x66FFFFFF)
            for (sx in floatArrayOf(36f, 272f)) for (sy in floatArrayOf(118f, 180f)) {
                circle(sx, sy, 2.3f, 0xFF8E8EA6.toInt())
                line(sx - 1.4f, sy - 0.6f, sx + 1.4f, sy + 0.6f, 0.6f, 0xFF30303C.toInt())
            }
            // Lamp columns either side of the display.
            for (i in 0 until 5) {
                val cy = 122f + i * 14f
                circle(15f, cy, 3.6f, Pal.shade(if (i % 2 == 0) Pal.PINK else Pal.CYAN, 0.55f))
                circle(293f, cy, 3.6f, Pal.shade(if (i % 2 == 0) Pal.CYAN else Pal.PINK, 0.55f))
                ring(15f, cy, 3.6f, 0.8f, 0x66FFFFFF)
                ring(293f, cy, 3.6f, 0.8f, 0x66FFFFFF)
            }
        }
    }

    val rail: Texture by lazy {
        paintTexture(64, 22) {
            vgrad(0f, 0f, 64f, 22f, 0xFF6B4FB0.toInt(), 0xFF2B1A5C.toInt(), 0xFF1E1042.toInt())
            rect(0f, 0f, 64f, 2f, Pal.LAVENDER)
            rect(0f, 20f, 64f, 2f, Pal.shade(Pal.NIGHT, 0.8f))
        }
    }

    val metal: Texture by lazy {
        paintTexture(32, 10) {
            vgrad(0f, 0f, 32f, 10f, 0xFFDCDCEE.toInt(), 0xFF9A9AB8.toInt(), 0xFF5A5A70.toInt())
        }
    }

    /** Brushed steel for rail caps and frames: a touch darker than the ball's chrome so it never blows out. */
    private val steel: Texture by lazy {
        paintTexture(32, 16) {
            vgrad(0f, 0f, 32f, 16f, 0xFFCFD2E6.toInt(), 0xFF7C809C.toInt(), 0xFFA6AAC6.toInt(), 0xFF3E4058.toInt())
            rect(0f, 5f, 32f, 1f, 0x55FFFFFF)
        }
    }

    val rubber: Texture by lazy {
        paintTexture(16, 10) {
            vgrad(0f, 0f, 16f, 10f, 0xFFF0F0F8.toInt(), 0xFFD0D0DE.toInt(), 0xFF9494A8.toInt())
        }
    }

    private val postTop: Texture by lazy {
        paintTexture(16, 16) {
            fill(Pal.RED)
            radial(6f, 6f, 8f, 0xFFFF9090.toInt(), Pal.RED)
        }
    }

    private val slingPlastic: Texture by lazy {
        paintTexture(32, 32) {
            fill(0xFF1E7A4E.toInt())
            radial(16f, 16f, 18f, Pal.LIME, 0xFF1E7A4E.toInt())
            star(16f, 16f, 7f, Pal.withAlpha(Pal.WHITE, 0.7f))
        }
    }

    private val darkMetal: Texture by lazy {
        paintTexture(16, 16) { vgrad(0f, 0f, 16f, 16f, 0xFF3A3A48.toInt(), 0xFF16161E.toInt()) }
    }

    /**
     * The cabinet's side art, seen inside the table: a deep violet that darkens towards the
     * floor, with a pink and a cyan racing stripe running the table's length (the face is
     * stretched along it, so the stripes are horizontal lines here).
     */
    private val cabinetSide: Texture by lazy {
        paintTexture(64, 64) {
            vgrad(0f, 0f, 64f, 64f, 0xFF5A34A0.toInt(), 0xFF3A2072.toInt(), 0xFF1A0E3A.toInt())
            rect(0f, 22f, 64f, 2.2f, Pal.shade(Pal.PINK, 0.75f))
            rect(0f, 27f, 64f, 1.2f, Pal.shade(Pal.CYAN, 0.7f))
            stars(64f, 20f, 8, 3, Pal.LAVENDER)
        }
    }

    val spinnerPlate: Texture by lazy {
        paintTexture(40, 16) {
            vgrad(0f, 0f, 40f, 16f, 0xFFF4F4FF.toInt(), 0xFFA8A8C0.toInt())
            star(20f, 8f, 6f, Pal.RED)
        }
    }

    /** Faint diagonal reflections on the glass, for additive blending (black is invisible). */
    val glassStreaks: Texture by lazy {
        paintTexture(64, 128) {
            fill(0xFF000000.toInt())
            line(-10f, 90f, 60f, 10f, 10f, 0xFF4A4A60.toInt())
            line(10f, 120f, 74f, 40f, 4f, 0xFF30303E.toInt())
            line(-4f, 40f, 30f, 0f, 3f, 0xFF262632.toInt())
        }
    }

    private val chrome: Texture by lazy {
        paintTexture(64, 32) {
            // A reflection of the room: bright ceiling, the dark cabinet, a lit floor band.
            vgrad(0f, 0f, 64f, 32f, 0xFFFFFFFF.toInt(), 0xFFB8C0E0.toInt(), 0xFF40405A.toInt(), 0xFF8A7AB0.toInt(), 0xFF2A2440.toInt())
            rect(0f, 14f, 64f, 1f, 0xFFFFE0F0.toInt())
        }
    }

    /** The flipper's top: a lavender-white bat (kept under the bloom threshold) with a pale stripe and a star at the pivot end. */
    private val flipperBody: Texture by lazy {
        paintTexture(32, 16) {
            vgrad(0f, 0f, 32f, 16f, 0xFFE6E2F8.toInt(), 0xFFB8B0DC.toInt(), 0xFF8E86BC.toInt())
            rect(0f, 7f, 32f, 2f, 0x55FFFFFF)
            star(6f, 8f, 3.4f, Pal.withAlpha(Pal.PINK, 0.85f))
        }
    }

    private val flipperRubber: Texture by lazy {
        paintTexture(16, 10) {
            vgrad(0f, 0f, 16f, 10f, 0xFFD02C48.toInt(), 0xFF8E1830.toInt())
            rect(0f, 3f, 16f, 2.5f, 0xFFFF5A70.toInt())
        }
    }

    private val bumperSkirt: Texture by lazy {
        paintTexture(32, 8) {
            vgrad(0f, 0f, 32f, 8f, 0xFFF0F0FA.toInt(), 0xFF9C9CB8.toInt())
            for (i in 0 until 8) rect(i * 4f, 0f, 1f, 8f, 0x33000000)
        }
    }

    private val bumperTop: Texture by lazy {
        paintTexture(32, 32) {
            fill(0xFFE0E0EC.toInt())
            radial(16f, 16f, 16f, 0xFFF8F8FF.toInt(), 0xFFA0A0B8.toInt())
            ring(16f, 16f, 12.5f, 1.2f, 0x66000000)
            star(16f, 16f, 9f, 0xFF707080.toInt())
        }
    }

    private val targetFace: Texture by lazy {
        paintTexture(16, 16) {
            fill(0xFFF0F0F0.toInt())
            rect(0f, 0f, 16f, 2f, 0xFFB0B0B0.toInt())
            circle(8f, 9f, 4f, 0xFF404040.toInt())
            star(8f, 9f, 3f, Pal.WHITE)
        }
    }

    // ---------------------------------------------------------------- light textures (no bitmaps)

    /** A soft ring, for shock waves round a bumper: white, brightest at 72% of the radius. */
    val ringGlow: Texture by lazy {
        val n = 64
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f - n / 2f) / (n / 2f)
            val dy = (y + 0.5f - n / 2f) / (n / 2f)
            val d = sqrt(dx * dx + dy * dy)
            val e = (d - 0.72f) / 0.13f
            val a = (exp(-e * e) * 255f).toInt().coerceIn(0, 255)
            px[y * n + x] = (a shl 24) or 0xFFFFFF
        }
        Texture(n, n, px)
    }

    /** A strip of light: bright along its middle, fading to nothing at both long edges (v across). */
    val stripGlow: Texture by lazy {
        val w = 4
        val h = 16
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val t = 1f - kotlin.math.abs((y + 0.5f) / h * 2f - 1f)
            val a = (t * t * 255f).toInt().coerceIn(0, 255)
            px[y * w + x] = (a shl 24) or 0xFFFFFF
        }
        Texture(w, h, px)
    }

    /** A burst of rays fading with distance, for the backglass: 14 wedges, brightest near the centre. */
    val rays: Texture by lazy {
        val n = 128
        val px = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f - n / 2f) / (n / 2f)
            val dy = (y + 0.5f - n / 2f) / (n / 2f)
            val d = sqrt(dx * dx + dy * dy)
            val fade = (1f - d).coerceIn(0f, 1f)
            val a = atan2(dy, dx)
            val wedge = (sin(a * 14f) * 1.6f).coerceIn(0f, 1f)
            val v = (fade * fade * wedge * 255f).toInt().coerceIn(0, 255)
            px[y * n + x] = (v shl 24) or 0xFFFFFF
        }
        Texture(n, n, px)
    }

    /** A soft band of light for the shimmer that crosses the backglass: bright in the middle, fading sideways. */
    val shimmer: Texture by lazy {
        val w = 32
        val h = 4
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val t = 1f - kotlin.math.abs((x + 0.5f) / w * 2f - 1f)
            val a = (t * t * t * 255f).toInt().coerceIn(0, 255)
            px[y * w + x] = (a shl 24) or 0xFFFFFF
        }
        Texture(w, h, px)
    }

    // ---------------------------------------------------------------- models

    /** A wall standing on segment i: a double-sided band of its style's height and look. */
    private fun ModelBuilder.wall(x0: Float, z0: Float, x1: Float, z1: Float, h: Float, tex: Texture) {
        val dx = x1 - x0
        val dz = z1 - z0
        val l = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-4f)
        quad(x0, h, z0, x1, h, z1, x1, 0f, z1, x0, 0f, z0, tex.full, -dz / l, 0f, dx / l, cull = false, gloss = 0.3f)
    }

    /** A steel cap along the top of a wall: a narrow glossy strip that catches the light and gives the wall a thickness. */
    private fun ModelBuilder.cap(x0: Float, z0: Float, x1: Float, z1: Float, h: Float, half: Float) {
        val dx = x1 - x0
        val dz = z1 - z0
        val l = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-4f)
        val nx = -dz / l * half
        val nz = dx / l * half
        quad(
            x0 - nx, h, z0 - nz, x1 - nx, h, z1 - nz, x1 + nx, h, z1 + nz, x0 + nx, h, z0 + nz,
            steel.full, 0f, 1f, 0f, cull = false, gloss = 0.85f,
        )
    }

    /** Everything on the table that never moves: playfield, rails, posts, slings, apron, backbox. */
    val table: Model by lazy {
        val b = ModelBuilder()
        b.quad(0f, 0f, 0f, T.W, 0f, 0f, T.W, 0f, 640f, 0f, 0f, 640f, playfield.full, 0f, 1f, 0f)
        for (i in 0 until T.segCount) {
            val (h, tex) = when (T.style[i]) {
                T.STYLE_RAIL -> RAIL_H to rail
                T.STYLE_RUBBER -> RUBBER_H to rubber
                else -> GUIDE_H to metal
            }
            b.wall(T.ax[i], T.ay[i], T.bx[i], T.by[i], h, tex)
            val half = when (T.style[i]) {
                T.STYLE_RAIL -> RAIL_CAP
                T.STYLE_RUBBER -> RUBBER_CAP
                else -> GUIDE_CAP
            }
            // The cabinet's own side walls are capped by the chrome side rails below.
            if (T.style[i] != T.STYLE_RUBBER) b.cap(T.ax[i], T.ay[i], T.bx[i], T.by[i], h, half)
        }
        for (i in 0 until T.postCount) {
            b.cylinder(T.px[i], T.py[i], 0f, 10f, T.pr[i], 10, rubber.full, postTop.full, gloss = 0.4f)
        }
        // Slingshot plastics over the rubbers.
        for (side in 0..1) {
            fun sx(x: Float) = if (side == 0) x else T.PLAY_W - x
            b.poly(
                floatArrayOf(sx(T.SLING_AX), sx(T.SLING_CX), sx(T.SLING_BX)),
                floatArrayOf(11f, 11f, 11f),
                floatArrayOf(T.SLING_AY, T.SLING_CY, T.SLING_BY),
                floatArrayOf(16f, 32f, 0f), floatArrayOf(0f, 32f, 32f),
                slingPlastic.full, 0f, 1f, 0f, emissive = 0.55f, cull = false, gloss = 0.6f,
            )
        }
        // Side cabinet walls, each with a steel side rail along its inner top edge.
        val side = BoxFaces(top = cabinetSide.full, left = cabinetSide.full, right = cabinetSide.full, front = cabinetSide.full, gloss = 0.3f)
        val steelAll = BoxFaces(front = steel.full, left = steel.full, right = steel.full, top = steel.full, back = steel.full, gloss = 0.85f)
        b.box(-26f, 0f, BACKBOX_Z, 0f, 26f, 700f, side)
        b.box(T.W, 0f, BACKBOX_Z, T.W + 26f, 26f, 700f, side)
        b.box(-4.5f, 26f, BACKBOX_Z, 1.5f, 28.4f, 700f, steelAll)
        b.box(T.W - 1.5f, 26f, BACKBOX_Z, T.W + 4.5f, 28.4f, 700f, steelAll)
        b.box(0f, 0f, T.APRON_Y, T.PLAY_W, 8f, T.APRON_Y + APRON_PRINT, BoxFaces(top = apron.full, gloss = 0.4f))
        b.box(0f, 0f, T.APRON_Y + APRON_PRINT, T.PLAY_W, 8f, 700f, BoxFaces(top = apronBack.full, front = darkMetal.full, gloss = 0.4f))
        b.box(T.PLAY_W, 0f, T.LANE_REST_Y + 30f, T.W, 6f, 700f, BoxFaces(top = darkMetal.full, gloss = 0.6f))
        // Backbox, standing behind the top arch: a dark body with a closed front, a steel top
        // lip, and a steel frame round the backglass window.
        val back = BoxFaces(front = darkMetal.full, top = darkMetal.full, left = cabinetSide.full, right = cabinetSide.full, gloss = 0.4f)
        b.box(-26f, 0f, BACKBOX_Z - 40f, T.W + 26f, BACKBOX_TOP, BACKBOX_Z, back)
        b.box(-28f, BACKBOX_TOP, BACKBOX_Z - 41f, T.W + 28f, BACKBOX_TOP + 3f, BACKBOX_Z + 1.6f, steelAll)
        val fz = BACKBOX_Z + 1.4f
        b.box(GLASS_X0 - 12f, GLASS_Y0 - 6f, BACKBOX_Z, GLASS_X0, GLASS_Y1 + 8f, fz, steelAll)
        b.box(GLASS_X1, GLASS_Y0 - 6f, BACKBOX_Z, GLASS_X1 + 12f, GLASS_Y1 + 8f, fz, steelAll)
        b.box(GLASS_X0 - 12f, GLASS_Y1, BACKBOX_Z, GLASS_X1 + 12f, GLASS_Y1 + 8f, fz, steelAll)
        b.box(GLASS_X0 - 12f, GLASS_Y0 - 6f, BACKBOX_Z, GLASS_X1 + 12f, GLASS_Y0, fz, steelAll)
        b.quad(
            GLASS_X0, GLASS_Y1, BACKBOX_Z + 0.2f, GLASS_X1, GLASS_Y1, BACKBOX_Z + 0.2f,
            GLASS_X1, GLASS_Y0, BACKBOX_Z + 0.2f, GLASS_X0, GLASS_Y0, BACKBOX_Z + 0.2f,
            backglass.full, 0f, 0f, 1f, emissive = 0.85f,
        )
        b.build()
    }

    /**
     * Strips of light laid on the floor just inside the outer rails and up the inside of the side
     * walls: pink at the top of the table running to cyan at the bottom. Additive and glowing,
     * drawn with an animated brightness (see the game's render), so the table's edge breathes.
     */
    val railGlow: Model by lazy {
        val b = ModelBuilder()
        val strip = stripGlow.full
        // Floor strips along every outer rail segment, offset towards the middle of the table.
        for (i in 0 until T.segCount) {
            if (T.style[i] != T.STYLE_RAIL) continue
            val x0 = T.ax[i]
            val z0 = T.ay[i]
            val x1 = T.bx[i]
            val z1 = T.by[i]
            val dx = x1 - x0
            val dz = z1 - z0
            val l = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-4f)
            var nx = -dz / l
            var nz = dx / l
            // Point the offset at the table's middle.
            if ((T.CX + 14f - (x0 + x1) / 2f) * nx + (330f - (z0 + z1) / 2f) * nz < 0f) {
                nx = -nx; nz = -nz
            }
            val hw = STRIP_W / 2f
            val cx0 = x0 + nx * STRIP_IN
            val cz0 = z0 + nz * STRIP_IN
            val cx1 = x1 + nx * STRIP_IN
            val cz1 = z1 + nz * STRIP_IN
            val t = ((z0 + z1) / 2f / 640f).coerceIn(0f, 1f)
            b.quad(
                cx0 - nx * hw, 0.5f, cz0 - nz * hw, cx1 - nx * hw, 0.5f, cz1 - nz * hw,
                cx1 + nx * hw, 0.5f, cz1 + nz * hw, cx0 + nx * hw, 0.5f, cz0 + nz * hw,
                strip, 0f, 1f, 0f, blend = Blend.ADD, emissive = 1f, cull = false,
                tint = Pal.mix(Pal.PINK, Pal.CYAN, t),
            )
        }
        // Two racing stripes up the inside of each side wall (matching the painted side art).
        for (s in 0..1) {
            val x = if (s == 0) 0.25f else T.W - 0.25f
            val nxs = if (s == 0) 1f else -1f
            val chunks = 6
            for (c in 0 until chunks) {
                val za = 70f + (590f) * c / chunks
                val zb = 70f + (590f) * (c + 1) / chunks
                val col = Pal.mix(Pal.PINK, Pal.CYAN, c / (chunks - 1f))
                b.quad(
                    x, 13.2f, za, x, 13.2f, zb, x, 9.8f, zb, x, 9.8f, za,
                    strip, nxs, 0f, 0f, blend = Blend.ADD, emissive = 1f, cull = false, tint = col,
                )
            }
        }
        b.build()
    }

    val bumperBase: Model by lazy {
        ModelBuilder()
            .cylinder(0f, 0f, 0f, 2f, T.BUMPER_R + 3f, 16, darkMetal.full, darkMetal.full, gloss = 0.6f)
            .cylinder(0f, 0f, 2f, 9f, T.BUMPER_R, 16, bumperSkirt.full, gloss = 0.4f)
            .cylinder(0f, 0f, 9f, 12f, T.BUMPER_R - 4f, 12, darkMetal.full, gloss = 0.5f)
            .build()
    }

    /** The lit cap: tinted per bumper and brightened with the draw's emissive boost. */
    val bumperCap: Model by lazy {
        ModelBuilder()
            .cylinder(0f, 0f, 12f, 17f, T.BUMPER_R - 1f, 16, bumperTop.full, bumperTop.full, emissive = 0.6f, topRadius = T.BUMPER_R - 3f)
            .build()
    }

    val dropTarget: Model by lazy {
        val f = targetFace.full
        ModelBuilder().box(-2f, 0f, -T.DROP_LEN / 2f, 2f, 14f, T.DROP_LEN / 2f, BoxFaces(left = f, right = f, front = f, top = f, back = f, gloss = 0.5f)).build()
    }

    /** A flipper lying along +x from its pivot: a bat with a red rubber round it, and a steel bolt at the pivot. */
    val flipper: Model by lazy {
        val b = ModelBuilder()
        val r0 = T.FLIP_R0
        val r1 = T.FLIP_R1
        val l = T.FLIP_LEN
        val body = flipperBody.full
        val band = flipperRubber.full
        b.quad(0f, 9f, -r0, l, 9f, -r1, l, 9f, r1, 0f, 9f, r0, body, 0f, 1f, 0f, cull = false, gloss = 0.5f)
        b.quad(0f, 9f, -r0, l, 9f, -r1, l, 0f, -r1, 0f, 0f, -r0, band, 0f, 0f, -1f, cull = false, gloss = 0.3f)
        b.quad(0f, 9f, r0, l, 9f, r1, l, 0f, r1, 0f, 0f, r0, band, 0f, 0f, 1f, cull = false, gloss = 0.3f)
        b.cylinder(0f, 0f, 0f, 9f, r0, 14, band, body, gloss = 0.3f)
        b.cylinder(l, 0f, 0f, 9f, r1, 10, band, body, gloss = 0.3f)
        b.cylinder(0f, 0f, 9f, 10.6f, 3.4f, 10, steel.full, steel.full, gloss = 0.9f)
        b.build()
    }

    /** The plunger: a chrome rod running towards the player with a red knob. */
    val plunger: Model by lazy {
        ModelBuilder()
            .capsule(0f, 0f, 0f, 0f, 0f, 70f, 2.4f, metal.full, gloss = 0.9f)
            .cylinder(0f, 2f, -3f, 3f, 5f, 10, darkMetal.full, darkMetal.full)
            .build()
    }

    val ball: Model by lazy {
        ModelBuilder().sphere(0f, 0f, 0f, T.BALL_R, chrome.full, slices = 18, stacks = 12, gloss = 1f).build()
    }

    // ---------------------------------------------------------------- hall cabinet

    /** The playfield seen through the cabinet's glass: a small, simplified painting of the table. */
    val cabinetPlayfield: Texture by lazy {
        paintTexture(60, 128, 3) {
            vgrad(0f, 0f, 60f, 128f, 0xFF0A0B30.toInt(), 0xFF2A0E47.toInt(), 0xFF140828.toInt())
            stars(60f, 128f, 40, 2, Pal.LAVENDER)
            val kx = 60f / T.W
            val ky = 128f / 660f
            for (i in 0 until 3) {
                circle(T.BUMPER_X[i] * kx, T.BUMPER_Y[i] * ky, T.BUMPER_R * kx * 1.2f, if (i == 1) Pal.CYAN else Pal.HOTPINK)
                circle(T.BUMPER_X[i] * kx, T.BUMPER_Y[i] * ky, T.BUMPER_R * kx * 0.6f, Pal.WHITE)
            }
            for (i in 0 until T.segCount) {
                val c = when (T.style[i]) {
                    T.STYLE_RAIL -> Pal.LAVENDER
                    T.STYLE_RUBBER -> Pal.WHITE
                    else -> Pal.LIGHTGRAY
                }
                line(T.ax[i] * kx, T.ay[i] * ky, T.bx[i] * kx, T.by[i] * ky, 0.8f, c)
            }
            for (i in 0 until 4) circle((T.CX - 54f + i * 36f) * kx, 372f * ky, 2f, Pal.GOLD)
            for (s in 0..1) {
                val x = T.flipX(s) * kx
                val a = T.restAngle(s)
                line(x, T.FLIP_Y * ky, x + cos(a) * T.FLIP_LEN * kx, (T.FLIP_Y + sin(a) * T.FLIP_LEN) * ky, 2f, Pal.WHITE)
            }
            label("FLIP", 30f, 84f, 6f, Pal.HOTPINK)
            rect(0f, T.APRON_Y * ky, T.PLAY_W * kx, 128f - T.APRON_Y * ky, 0xFF2B1A5C.toInt())
        }
    }

    /** The attract-mode ball rolling round the cabinet's playfield. */
    val cabinetBall: Model by lazy {
        ModelBuilder().sphere(0f, 0f, 0f, 0.9f, chrome.full, slices = 10, stacks = 6, gloss = 1f).build()
    }
}
