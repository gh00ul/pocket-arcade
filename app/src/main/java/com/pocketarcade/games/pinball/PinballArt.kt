package com.pocketarcade.games.pinball

import com.pocketarcade.engine.Pal
import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture
import kotlin.math.PI
import kotlin.math.cos
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
    /** Height range of the dot-matrix display on the backbox (drawn as an overlay in play). */
    const val DMD_TOP = 86f
    const val DMD_BOTTOM = 16f
    const val BACKBOX_TOP = 214f

    private const val RAIL_H = 22f
    private const val GUIDE_H = 10f
    private const val RUBBER_H = 9f

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
        circle(cx, cy, r + 1.5f, Pal.shade(color, 0.2f))
        radial(cx, cy, r, Pal.shade(color, 0.6f), Pal.shade(color, 0.3f))
        ring(cx, cy, r, 1f, Pal.withAlpha(Pal.WHITE, 0.35f))
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
            // Flipper zone shading.
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

    /** Backbox front: the backglass art above the display. */
    val backglass: Texture by lazy {
        paintTexture(320, 220, 2) {
            vgrad(0f, 0f, 320f, 220f, 0xFF1A0A40.toInt(), 0xFF3B1470.toInt(), 0xFF0E0624.toInt())
            stars(320f, 130f, 60, 9, Pal.CREAM)
            for (i in 0 until 12) {
                val a = PI.toFloat() + i * PI.toFloat() / 11f
                line(160f, 110f, 160f + cos(a) * 200f, 110f + sin(a) * 200f, 5f, if (i % 2 == 0) 0x33FF3FA4 else 0x333DF5FF)
            }
            glow(6f, Pal.withAlpha(Pal.HOTPINK, 0.9f)) { label("STAR", 160f, 16f, 44f, -1) }
            label("STAR", 160f, 16f, 44f, Pal.YELLOW, shadow = Pal.BLACK)
            glow(6f, Pal.withAlpha(Pal.CYAN, 0.9f)) { label("FLIPPER", 160f, 70f, 34f, -1) }
            label("FLIPPER", 160f, 70f, 34f, Pal.CREAM, shadow = Pal.BLACK)
            // The display's bezel (the dots themselves are drawn live).
            roundGrad(24f, 124f, 272f, 88f, 6f, 0xFF2A2A34.toInt(), 0xFF101016.toInt())
            rect(32f, 130f, 256f, 76f, 0xFF120600.toInt())
        }
    }

    val rail: Texture by lazy {
        paintTexture(64, 22) {
            vgrad(0f, 0f, 64f, 22f, 0xFF6B4FB0.toInt(), 0xFF2B1A5C.toInt())
            rect(0f, 0f, 64f, 2f, Pal.LAVENDER)
            rect(0f, 20f, 64f, 2f, Pal.shade(Pal.NIGHT, 0.8f))
        }
    }

    val metal: Texture by lazy {
        paintTexture(32, 10) {
            vgrad(0f, 0f, 32f, 10f, 0xFFF4F4FF.toInt(), 0xFF9A9AB8.toInt(), 0xFF5A5A70.toInt())
        }
    }

    val rubber: Texture by lazy {
        paintTexture(16, 10) {
            vgrad(0f, 0f, 16f, 10f, Pal.WHITE, 0xFFD8D8E4.toInt(), 0xFFA0A0B0.toInt())
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

    private val cabinetSide: Texture by lazy {
        paintTexture(64, 64) {
            vgrad(0f, 0f, 64f, 64f, 0xFF4A2A8A.toInt(), 0xFF1E1040.toInt())
            stars(64f, 64f, 16, 3, Pal.LAVENDER)
        }
    }

    val spinnerPlate: Texture by lazy {
        paintTexture(40, 16) {
            vgrad(0f, 0f, 40f, 16f, 0xFFFFFFFF.toInt(), 0xFFA8A8C0.toInt())
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

    private val flipperBody: Texture by lazy {
        paintTexture(16, 16) { vgrad(0f, 0f, 16f, 16f, Pal.WHITE, 0xFFD0D0E0.toInt()) }
    }

    private val flipperRubber: Texture by lazy {
        paintTexture(16, 10) {
            fill(0xFFB0213A.toInt())
            rect(0f, 3f, 16f, 4f, Pal.RED)
        }
    }

    private val bumperSkirt: Texture by lazy {
        paintTexture(32, 8) {
            vgrad(0f, 0f, 32f, 8f, Pal.WHITE, 0xFFB0B0C8.toInt())
            for (i in 0 until 8) rect(i * 4f, 0f, 1f, 8f, 0x33000000)
        }
    }

    private val bumperTop: Texture by lazy {
        paintTexture(32, 32) {
            fill(0xFFE8E8F0.toInt())
            radial(16f, 16f, 16f, Pal.WHITE, 0xFFB0B0C0.toInt())
            star(16f, 16f, 9f, 0xFF707080.toInt())
        }
    }

    private val targetFace: Texture by lazy {
        paintTexture(16, 16) {
            fill(Pal.WHITE)
            rect(0f, 0f, 16f, 2f, 0xFFB0B0B0.toInt())
            circle(8f, 9f, 4f, 0xFF404040.toInt())
            star(8f, 9f, 3f, Pal.WHITE)
        }
    }

    // ---------------------------------------------------------------- models

    /** A wall standing on segment i: a double-sided band of its style's height and look. */
    private fun ModelBuilder.wall(x0: Float, z0: Float, x1: Float, z1: Float, h: Float, tex: Texture) {
        val dx = x1 - x0
        val dz = z1 - z0
        val l = sqrt(dx * dx + dz * dz).coerceAtLeast(1e-4f)
        quad(x0, h, z0, x1, h, z1, x1, 0f, z1, x0, 0f, z0, tex.full, -dz / l, 0f, dx / l, cull = false, gloss = 0.3f)
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
        // Side cabinet walls, the apron and the plunger housing.
        val side = BoxFaces(top = cabinetSide.full, left = cabinetSide.full, right = cabinetSide.full, front = cabinetSide.full, gloss = 0.3f)
        b.box(-26f, 0f, BACKBOX_Z, 0f, 26f, 700f, side)
        b.box(T.W, 0f, BACKBOX_Z, T.W + 26f, 26f, 700f, side)
        b.box(0f, 0f, T.APRON_Y, T.PLAY_W, 8f, T.APRON_Y + APRON_PRINT, BoxFaces(top = apron.full, gloss = 0.4f))
        b.box(0f, 0f, T.APRON_Y + APRON_PRINT, T.PLAY_W, 8f, 700f, BoxFaces(top = apronBack.full, front = darkMetal.full, gloss = 0.4f))
        b.box(T.PLAY_W, 0f, T.LANE_REST_Y + 30f, T.W, 6f, 700f, BoxFaces(top = darkMetal.full, gloss = 0.6f))
        // Backbox, standing behind the top arch.
        val back = BoxFaces(top = darkMetal.full, left = cabinetSide.full, right = cabinetSide.full, gloss = 0.4f)
        b.box(-26f, 0f, BACKBOX_Z - 40f, T.W + 26f, BACKBOX_TOP, BACKBOX_Z, back)
        b.quad(-8f, BACKBOX_TOP - 4f, BACKBOX_Z + 0.2f, T.W + 8f, BACKBOX_TOP - 4f, BACKBOX_Z + 0.2f, T.W + 8f, 4f, BACKBOX_Z + 0.2f, -8f, 4f, BACKBOX_Z + 0.2f, backglass.full, 0f, 0f, 1f, emissive = 0.95f)
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

    /** A flipper lying along +x from its pivot: white bat with a red rubber round it. */
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
        b.cylinder(0f, 0f, 9f, 10.5f, 3f, 8, metal.full, metal.full, gloss = 0.9f)
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
