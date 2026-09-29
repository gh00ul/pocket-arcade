package com.pocketarcade.hub

import com.pocketarcade.engine.hash01
import com.pocketarcade.engine.r3d.BoxFaces
import com.pocketarcade.engine.r3d.Fonts
import com.pocketarcade.engine.r3d.Model
import com.pocketarcade.engine.r3d.ModelBuilder
import com.pocketarcade.engine.r3d.TexPaint
import com.pocketarcade.engine.r3d.Texture
import com.pocketarcade.engine.r3d.paintTexture
import com.pocketarcade.engine.r3d.Xform
import kotlin.math.PI

/** Materials and small parts shared by every cabinet of a kind (nets, lanes, balls, claws...). */
object MachineKit {
    /** Diamond mesh netting (alpha-blended). */
    val net: Texture by lazy {
        val tp = TexPaint(128, 128)
        tp.clear(0)
        for (k in -8..16) {
            tp.line(k * 16f, 0f, k * 16f + 128f, 128f, 1.6f, alpha(-1, 0.55f), round = false)
            tp.line(k * 16f, 128f, k * 16f + 128f, 0f, 1.6f, alpha(-1, 0.55f), round = false)
        }
        tp.toTexture().also { it.repeat = true; tp.recycle() }
    }

    val velvet: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.vgrad(0f, 0f, 64f, 64f, 0xFF5A1640.toInt(), 0xFF2E0A22.toInt())
        tp.grain(0.12f, 17)
        tp.toTexture().also { tp.recycle() }
    }

    val laneWood: Texture by lazy { HallArt.wood(0xFFD69A5A.toInt(), 5) }

    /**
     * Skee-ball target (painted at 3x): concentric scoring rings, each a dished, bevelled band in
     * its own colour with its value in big outlined numerals, a black 100 pocket in the middle
     * and two rimmed bonus holes in the top corners.
     */
    val skeeRings: Texture by lazy {
        paintTexture(192, 192, 3) {
            val w = 192f
            val h = 192f
            vgrad(0f, 0f, w, h, 0xFF22306E.toInt(), 0xFF101838.toInt())
            val cx = w / 2f
            val cy = h * 0.56f
            val colors = intArrayOf(0xFF4DA6FF.toInt(), 0xFF8A4FFF.toInt(), 0xFFFF3FA4.toInt(), 0xFFFF9A3C.toInt(), 0xFFFFE14D.toInt(), 0xFFFF4D4D.toInt())
            val radii = floatArrayOf(88f, 72f, 57f, 42f, 27f, 12f)
            val points = intArrayOf(10, 20, 30, 40, 50, 100)
            for (i in radii.indices) {
                val r = radii[i]
                // The dish: a dark rim, the band's colour, a lighter crown and a shadow line under the next ring in.
                circle(cx, cy + 1.5f, r, alpha(0xFF000000.toInt(), 0.5f))
                circle(cx, cy, r, dim(colors[i], 0.5f))
                circle(cx, cy + 2f, r - 3f, dim(colors[i], 0.34f))
                radial(cx, cy - r * 0.3f, r * 0.95f, alpha(lift(colors[i], 0.4f), 0.35f), 0)
                ring(cx, cy, r - 1.1f, 2.2f, colors[i])
                ring(cx, cy, r - 2.6f, 0.7f, alpha(-1, 0.35f))
            }
            circle(cx, cy, 9f, 0xFF050308.toInt())
            ring(cx, cy, 9.5f, 1.8f, 0xFFC4C8D4.toInt())
            for (i in 0 until radii.size - 1) {
                val r = (radii[i] + radii[i + 1]) / 2f
                val x = cx + (if (i % 2 == 0) r else -r)
                outlinedText(points[i].toString(), x, cy + 5.5f, 14f, -1, 0xFF0A1030.toInt(), 3f, Fonts.display)
            }
            // The 200 bonus pockets in the top corners, rimmed in chrome.
            for (sx in floatArrayOf(20f, w - 20f)) {
                circle(sx, 22f, 11f, 0xFFC4C8D4.toInt())
                circle(sx, 22f, 9f, 0xFFFFC83D.toInt())
                circle(sx, 22f, 6.5f, 0xFF050308.toInt())
            }
            strokeRound(2f, 2f, w - 4f, h - 4f, 6f, 3f, 0xFFC4C8D4.toInt())
            grain(0.03f, 5)
        }
    }

    val court: Texture by lazy {
        val tp = TexPaint(128, 256)
        for (x in 0 until 128 step 16) {
            val c = if ((x / 16) % 2 == 0) 0xFFD8A266.toInt() else 0xFFCC955A.toInt()
            tp.rect(x.toFloat(), 0f, 16f, 256f, c)
            tp.rect(x.toFloat(), 0f, 1f, 256f, 0xFF9A6A3A.toInt())
        }
        tp.rect(40f, 0f, 48f, 90f, alpha(0xFFFF3B30.toInt(), 0.45f))
        tp.strokeRound(40f, -10f, 48f, 100f, 2f, 3f, -1)
        tp.ring(64f, 90f, 24f, 3f, -1)
        tp.grain(0.05f, 3)
        tp.toTexture().also { tp.recycle() }
    }

    val hoopBoard: Texture by lazy {
        val tp = TexPaint(192, 128)
        tp.vgrad(0f, 0f, 192f, 128f, 0xFFF4F8FF.toInt(), 0xFFC8D6EA.toInt())
        tp.strokeRound(4f, 4f, 184f, 120f, 6f, 6f, 0xFFE8323C.toInt())
        tp.strokeRound(66f, 56f, 60f, 50f, 2f, 5f, 0xFFE8323C.toInt())
        tp.toTexture().also { tp.recycle() }
    }

    /**
     * The air-hockey surface (painted at 3x): pale ice-blue with a grid of air holes, the red
     * centre line and circle, blue goal arcs and creases, and dark goal slots at both ends.
     */
    val hockeySurface: Texture by lazy {
        paintTexture(128, 256, 3) {
            val w = 128f
            val h = 256f
            vgrad(0f, 0f, w, h, 0xFFF2F8FF.toInt(), 0xFFD8E8FA.toInt())
            for (y in 6 until 256 step 10) for (x in 6 until 128 step 10) circle(x.toFloat(), y.toFloat(), 1.1f, 0xFF9DB4D0.toInt())
            rect(0f, h / 2f - 2f, w, 4f, 0xFFE8323C.toInt())
            ring(w / 2f, h / 2f, 22f, 3f, 0xFFE8323C.toInt())
            circle(w / 2f, h / 2f, 3f, 0xFFE8323C.toInt())
            ring(w / 2f, 0f, 30f, 3f, 0xFF2F5BE0.toInt())
            ring(w / 2f, h, 30f, 3f, 0xFF2F5BE0.toInt())
            rect(0f, h * 0.25f, w, 3f, 0xFF2F5BE0.toInt())
            rect(0f, h * 0.75f, w, 3f, 0xFF2F5BE0.toInt())
            round(w / 2f - 22f, -2f, 44f, 7f, 3f, 0xFF101018.toInt())
            round(w / 2f - 22f, h - 5f, 44f, 7f, 3f, 0xFF101018.toInt())
            grain(0.02f, 9)
        }
    }

    /** Hole positions for the whack-a-mole table top (fractions of width and depth). */
    val whackHoles = arrayOf(0.2f to 0.62f, 0.35f to 0.38f, 0.5f to 0.66f, 0.65f to 0.38f, 0.8f to 0.62f)

    /** The whack-a-mole table top for a body colour, painted once per colour at 2x (all copies of a machine share it). */
    fun whackTop(color: Int): Texture = whackTops.getOrPut(color) {
        paintTexture(256, 208, 2) {
            val w = 256f
            val h = 208f
            vgrad(0f, 0f, w, h, lift(color, 0.12f), dim(color, 0.66f))
            radial(w / 2f, h * 0.4f, w * 0.6f, alpha(lift(color, 0.5f), 0.25f), 0)
            for ((fx, fz) in whackHoles) {
                val x = fx * w
                val y = fz * h
                // Each hole: a rubber collar, a dark well and a shadow at the back of it.
                circle(x, y + 1.5f, 32f, alpha(0xFF000000.toInt(), 0.35f))
                circle(x, y, 30f, 0xFF7A4A22.toInt())
                circle(x, y, 27f, 0xFF3A2414.toInt())
                radial(x, y + 3f, 25f, 0xFF050303.toInt(), 0xFF2A1A0E.toInt())
            }
            rect(0f, 0f, w, 8f, 0xFF8B5A2B.toInt())
            rect(0f, 8f, w, 1.5f, alpha(-1, 0.2f))
            grain(0.05f, 21)
        }
    }
    private val whackTops = HashMap<Int, Texture>()

    /**
     * The prize door on a claw machine's front: a smoked flap hinged along its top edge over a dark
     * chute, with "PRIZE" and a down arrow printed on it in warm yellow.
     */
    val prizeChute: Texture by lazy {
        paintTexture(48, 52, CabinetPaint.PLATE_SCALE) {
            round(0f, 0f, 48f, 52f, 4f, 0xFF08070C.toInt())
            roundGrad(3f, 3f, 42f, 46f, 3f, 0xFF3A3648.toInt(), 0xFF14121C.toInt())
            rect(3f, 9f, 42f, 1.4f, 0xFF8A8E9E.toInt())
            for (x in floatArrayOf(6f, 24f, 42f)) circle(x, 6f, 1.6f, 0xFF8A8E9E.toInt())
            text("PRIZE", 24f, 26f, 10f, 0xFFFFD84D.toInt(), Fonts.condensed)
            polygon(floatArrayOf(17f, 32f, 31f, 32f, 24f, 41f), alpha(0xFFFFD84D.toInt(), 0.9f))
            vgrad(3f, 10f, 42f, 14f, alpha(-1, 0.16f), 0)
        }
    }

    val deck: Texture by lazy {
        val tp = TexPaint(128, 128)
        tp.vgrad(0f, 0f, 128f, 128f, 0xFF1E2A6C.toInt(), 0xFF101640.toInt())
        for (y in 0 until 128 step 16) tp.rect(0f, y.toFloat(), 128f, 1.5f, 0xFF2E3C8E.toInt())
        tp.toTexture().also { tp.recycle() }
    }

    val gold: Texture by lazy { HallArt.paint(0xFFFFC83D.toInt(), 0.35f, 0.7f) }
    val coinFace: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.circle(32f, 32f, 32f, 0xFFE0A020.toInt())
        tp.circle(32f, 32f, 26f, 0xFFFFD04A.toInt())
        tp.ring(32f, 32f, 20f, 2f, 0xFFE0A020.toInt())
        tp.text("★", 32f, 42f, 26f, 0xFFE0A020.toInt(), Fonts.heavy)
        tp.toTexture().also { tp.recycle() }
    }

    val seatLeather: Texture by lazy {
        val tp = TexPaint(64, 64)
        tp.vgrad(0f, 0f, 64f, 64f, 0xFF2C2A32.toInt(), 0xFF141218.toInt())
        for (x in 8 until 64 step 16) tp.rect(x.toFloat(), 0f, 1f, 64f, 0xFF3C3A44.toInt())
        tp.grain(0.06f, 5)
        tp.toTexture().also { tp.recycle() }
    }

    val rubber: Texture by lazy { HallArt.paint(0xFF1A1A20.toInt(), 0.08f, 0.8f) }

    val moleFur: Texture by lazy { HallArt.paint(0xFF8B5A2B.toInt(), 0.15f, 0.8f) }

    /** Mole head: fur with eyes, a pink nose and buck teeth at the front. */
    val moleFace: Texture by lazy {
        val tp = TexPaint(128, 64)
        tp.vgrad(0f, 0f, 128f, 64f, 0xFF9C6A38.toInt(), 0xFF6E4420.toInt())
        val cx = 32f
        tp.oval(cx, 40f, 16f, 11f, 0xFFD9A066.toInt())
        for (s in intArrayOf(-1, 1)) {
            tp.oval(cx + s * 9f, 26f, 3.2f, 4.2f, 0xFF120C08.toInt())
            tp.circle(cx + s * 9f - 1f, 24.5f, 1.2f, -1)
        }
        tp.oval(cx, 36f, 5f, 3.5f, 0xFFFF6FA0.toInt())
        tp.rect(cx - 3f, 43f, 2.6f, 4f, -1)
        tp.rect(cx + 0.4f, 43f, 2.6f, 4f, -1)
        tp.toTexture().also { tp.recycle() }
    }

    /**
     * The back of a cabinet, as a kid walking behind a bank sees it: a screwed-on service panel
     * with louvred vents, a fan grille, the mains inlet, a serial plate and the usual stickers.
     * [tall] is for backs taller than they are wide.
     */
    fun rearPanel(tall: Boolean): Texture = if (tall) rearTall else rearWide
    private val rearTall: Texture by lazy { paintRear(128, 256) }
    private val rearWide: Texture by lazy { paintRear(256, 160) }

    private fun paintRear(w: Int, h: Int): Texture {
        val tp = TexPaint(w, h)
        val fw = w.toFloat()
        val fh = h.toFloat()
        tp.vgrad(0f, 0f, fw, fh, 0xFF2A2830.toInt(), 0xFF17161C.toInt())
        tp.grain(0.05f, w + h)
        // The removable service panel, its screws and a keyed lock.
        val m = fw * 0.08f
        tp.round(m, m, fw - 2f * m, fh - 2f * m, 4f, 0xFF211F27.toInt())
        tp.strokeRound(m, m, fw - 2f * m, fh - 2f * m, 4f, 1.5f, 0xFF3C3A46.toInt())
        for (sx in floatArrayOf(m + 5f, fw - m - 5f)) for (sy in floatArrayOf(m + 5f, fh - m - 5f)) {
            tp.circle(sx, sy, 2.4f, 0xFF8A8C98.toInt())
            tp.rect(sx - 1.6f, sy - 0.4f, 3.2f, 0.8f, 0xFF3A3A44.toInt())
        }
        // Louvred vents across the top.
        val vx = m + 12f
        val vw = fw - 2f * vx
        var vy = m + 12f
        for (k in 0 until 7) {
            tp.round(vx, vy, vw, 3.4f, 1.7f, 0xFF08070B.toInt())
            tp.rect(vx + 1f, vy + 3.4f, vw - 2f, 1f, 0xFF3A3842.toInt())
            vy += 7f
        }
        // Fan grille, and the mains inlet with its switch.
        val fanR = minOf(fw, fh) * 0.16f
        val fanX = if (w > h) fw * 0.72f else fw * 0.5f
        val fanY = if (w > h) fh * 0.62f else fh * 0.5f
        tp.circle(fanX, fanY, fanR + 2f, 0xFF3C3A46.toInt())
        tp.circle(fanX, fanY, fanR, 0xFF09080C.toInt())
        for (k in 1..4) tp.ring(fanX, fanY, fanR * k / 4.5f, 1f, 0xFF55535F.toInt())
        tp.line(fanX - fanR, fanY, fanX + fanR, fanY, 1f, 0xFF55535F.toInt())
        tp.line(fanX, fanY - fanR, fanX, fanY + fanR, 1f, 0xFF55535F.toInt())
        val ix = if (w > h) fw * 0.2f else fw * 0.3f
        val iy = fh - m - 34f
        tp.round(ix, iy, 26f, 18f, 2f, 0xFF0C0B10.toInt())
        tp.rect(ix + 5f, iy + 5f, 16f, 8f, 0xFF2A2830.toInt())
        tp.round(ix + 30f, iy + 2f, 10f, 14f, 2f, 0xFFB0213A.toInt())
        // A riveted serial plate.
        val px = if (w > h) fw * 0.14f else fw * 0.22f
        val py = if (w > h) fh * 0.5f else fh * 0.7f
        val pw = if (w > h) fw * 0.3f else fw * 0.56f
        tp.round(px, py, pw, 18f, 2f, 0xFFB8BCC8.toInt())
        tp.rect(px + 4f, py + 5f, pw * 0.6f, 2f, 0xFF4A4C58.toInt())
        tp.rect(px + 4f, py + 10f, pw * 0.8f, 2f, 0xFF4A4C58.toInt())
        tp.text("SN 04-7731", px + pw / 2f, py + 17f, 5f, 0xFF2A2C36.toInt(), Fonts.heavy)
        // Stickers: a yellow warning triangle and a white service label.
        val wx = if (w > h) fw * 0.52f else fw * 0.3f
        val wy = if (w > h) fh * 0.5f else fh * 0.34f
        tp.polygon(floatArrayOf(wx, wy + 22f, wx + 13f, wy, wx + 26f, wy + 22f), 0xFFFFD23A.toInt())
        tp.polygon(floatArrayOf(wx + 4f, wy + 20f, wx + 13f, wy + 5f, wx + 22f, wy + 20f), 0xFF16141C.toInt())
        tp.polygon(floatArrayOf(wx + 6.5f, wy + 18.5f, wx + 13f, wy + 8f, wx + 19.5f, wy + 18.5f), 0xFFFFD23A.toInt())
        tp.rect(wx + 12f, wy + 10.5f, 2f, 5f, 0xFF16141C.toInt())
        tp.rect(wx + 12f, wy + 16.5f, 2f, 1.6f, 0xFF16141C.toInt())
        val lx = if (w > h) fw * 0.52f else fw * 0.56f
        val ly = if (w > h) fh * 0.72f else fh * 0.36f
        tp.round(lx, ly, 34f, 22f, 2f, 0xFFF2F0EA.toInt())
        tp.rect(lx, ly, 34f, 6f, 0xFFE8323C.toInt())
        for (k in 0 until 3) tp.rect(lx + 3f, ly + 9f + k * 4f, 28f - k * 6f, 1.6f, 0xFF6A6870.toInt())
        return tp.toTexture().also { tp.recycle() }
    }

    // ------------------------------------------------------------------ small parts

    private fun colored(color: Int) = HallArt.paint(color, 0.25f, 0.75f).full

    /** A glossy ball of the given colour, radius 1 (scale with an [Xform]). */
    fun ball(color: Int): Model = balls.getOrPut(color) {
        ModelBuilder().sphere(0f, 0f, 0f, 1f, colored(color), slices = 12, stacks = 8, gloss = 0.8f).build()
    }
    private val balls = HashMap<Int, Model>()

    val basketball: Model by lazy {
        val tp = TexPaint(128, 64)
        tp.fill(0xFFE8782A.toInt())
        tp.grain(0.08f, 3)
        for (x in intArrayOf(0, 32, 64, 96)) tp.rect(x.toFloat(), 0f, 2.5f, 64f, 0xFF3A1A08.toInt())
        tp.rect(0f, 31f, 128f, 2.5f, 0xFF3A1A08.toInt())
        val tex = tp.toTexture().also { tp.recycle() }
        ModelBuilder().sphere(0f, 0f, 0f, 1f, tex.full, slices = 12, stacks = 8, gloss = 0.3f).build()
    }

    val coin: Model by lazy {
        ModelBuilder().cylinder(0f, 0f, -0.35f, 0.35f, 2.2f, 12, gold.full, top = coinFace.full, gloss = 0.9f).build()
    }

    val mole: Model by lazy {
        val b = ModelBuilder()
        b.cylinder(0f, 0f, -9f, 0f, 2.6f, 12, moleFur.full, gloss = 0.1f)
        b.sphere(0f, 0f, 0f, 2.9f, moleFace.full, slices = 16, stacks = 10, sy = 1.05f, gloss = 0.1f)
        b.build()
    }

    val mallet: Model by lazy {
        val b = ModelBuilder()
        b.capsule(0f, 0f, 0f, 0f, 0f, 9f, 0.5f, HallArt.wood().full)
        val head = ModelBuilder().cylinder(0f, 0f, -3f, 3f, 1.8f, 12, colored(0xFFE8323C.toInt()), top = colored(0xFFFFE0A0.toInt()), bottom = colored(0xFFFFE0A0.toInt()), gloss = 0.4f).build()
        b.add(head, Xform().set(0f, 0f, 9.5f, roll = PI.toFloat() / 2f))
        b.build()
    }

    /** Claw: hub, cable stub and three hooked prongs, hanging from y = 0 down to about -9. */
    val claw: Model by lazy {
        val b = ModelBuilder()
        val metal = HallArt.chrome.full
        b.cylinder(0f, 0f, -3f, 0f, 1.8f, 12, metal, top = metal, bottom = metal, gloss = 0.9f)
        for (k in 0 until 3) {
            val a = PI.toFloat() / 2f + k * 2f * PI.toFloat() / 3f
            val c = kotlin.math.cos(a)
            val s = kotlin.math.sin(a)
            b.capsule(c * 1.4f, -2.5f, s * 1.4f, c * 3.6f, -7f, s * 3.6f, 0.35f, metal, slices = 6, gloss = 0.9f)
            b.capsule(c * 3.6f, -7f, s * 3.6f, c * 2.4f, -9f, s * 2.4f, 0.35f, metal, slices = 6, gloss = 0.9f)
        }
        b.build()
    }

    val puck: Model by lazy {
        ModelBuilder().cylinder(0f, 0f, 0f, 0.9f, 2.2f, 14, colored(0xFFE8323C.toInt()), top = colored(0xFFFF5A5A.toInt()), gloss = 0.6f).build()
    }

    fun hockeyMallet(color: Int): Model = mallets.getOrPut(color) {
        val c = colored(color)
        ModelBuilder()
            .cylinder(0f, 0f, 0f, 1.6f, 3.2f, 16, c, top = c, gloss = 0.6f)
            .cylinder(0f, 0f, 1.6f, 4.6f, 1.2f, 10, c, top = c, gloss = 0.6f)
            .build()
    }
    private val mallets = HashMap<Int, Model>()

    // ------------------------------------------------------------------ control hardware

    /**
     * Where the printed control panel ([CabinetPaint.panel]) puts its joystick well and button
     * rings, as fractions of the panel's width, so the hardware sits exactly on its printing.
     */
    const val PANEL_STICK_U = 0.26f
    const val PANEL_BUTTON_U = 0.56f
    const val PANEL_BUTTON_STEP_U = 0.12f
    const val PANEL_BUTTON_ROW_SHIFT_U = 0.03f
    /** The printed rows of buttons, as fractions of the panel's depth from its back. */
    const val PANEL_ROW_V = 0.33f
    const val PANEL_ROW2_V = 0.625f

    /** How brightly a lit button cap's core glows (kept low: caps are saturated but often light). */
    const val CAP_GLOW = 0.7f
    /** Pale colours bloom sooner, so a glow multiplier is cut by up to this much at pure white. */
    const val PALE_GLOW_CUT = 0.3f

    /**
     * The emissive to give a surface painted [color] that should read as lit at strength [base]:
     * light colours (white trim, yellow, cyan) already sit close to the bloom threshold, so they
     * get less than dark saturated ones and the highlight's extra glow has headroom.
     */
    fun glowFor(color: Int, base: Float): Float {
        val lum = ((color shr 16 and 255) * 0.3f + (color shr 8 and 255) * 0.59f + (color and 255) * 0.11f) / 255f
        val pale = ((lum - 0.5f) / 0.5f).coerceIn(0f, 1f)
        return base * (1f - PALE_GLOW_CUT * pale)
    }

    /**
     * Arcade push button of cap [radius], its bottom at y = 0: a chrome-topped collar, a dark gap,
     * a shaded plastic dome and a lit core on the dome's crown (the same paint, whose lightest part
     * is the crown), in [color]. Eight-sided and smooth shaded, about 60 polygons.
     */
    fun button(color: Int, radius: Float): Model = buttons.getOrPut(color * 31 + java.lang.Float.floatToIntBits(radius)) {
        val cap = colored(color)
        val chrome = HallArt.chrome.full
        val black = HallArt.solid(0xFF08070C.toInt()).full
        val r = radius
        ModelBuilder()
            .cylinder(0f, 0f, 0f, r * 0.4f, r * 1.28f, 8, HallArt.darkMetal.full, top = chrome, gloss = 0.85f)
            .cylinder(0f, 0f, r * 0.4f, r * 0.5f, r * 1.08f, 8, black, top = black)
            .sphere(0f, r * 0.5f, 0f, r, cap, slices = 8, stacks = 3, sy = 0.55f, gloss = 0.85f, yFrom = 0f)
            .sphere(0f, r * 0.5f, 0f, r * 1.01f, cap, slices = 8, stacks = 2, sy = 0.55f, gloss = 0.6f, emissive = CAP_GLOW, yFrom = 0.55f)
            .build()
    }
    private val buttons = HashMap<Int, Model>()

    /** Ball-top joystick with a ball of [color], its plate at y = 0, about 100 polygons. */
    fun joystick(color: Int): Model = sticks.getOrPut(color) {
        val plate = HallArt.darkMetal.full
        ModelBuilder()
            .box(-2.3f, 0f, -2.3f, 2.3f, 0.35f, 2.3f, BoxFaces.all(plate, 0.5f))
            .lathe(0f, 0f, 0f, floatArrayOf(1.8f, 0.35f, 1.4f, 0.9f, 0.75f, 1.9f, 0.5f, 2.3f), 8, rubber.full, gloss = 0.25f)
            .cylinder(0f, 0f, 2.3f, 5.2f, 0.32f, 6, HallArt.chrome.full, gloss = 0.9f)
            .sphere(0f, 6.2f, 0f, 1.4f, colored(color), slices = 10, stacks = 6, gloss = 0.9f)
            .build()
    }
    private val sticks = HashMap<Int, Model>()

    /** The classic red-ball joystick. */
    val joystick: Model get() = joystick(0xFFE8323C.toInt())

    /** A trackball: a plate with a ring collar and a lustrous ball in it, its plate at y = 0. */
    val trackball: Model by lazy {
        val ballTex = colored(0xFF2A4AD0.toInt())
        ModelBuilder()
            .cylinder(0f, 0f, 0f, 0.5f, 3.0f, 10, HallArt.darkMetal.full, top = HallArt.chrome.full, gloss = 0.85f)
            .sphere(0f, 0.9f, 0f, 2.3f, ballTex, slices = 10, stacks = 6, gloss = 0.95f, yFrom = -0.3f)
            .build()
    }

    // ------------------------------------------------------------------ plates and decals

    /** Left slot centre, spacing and vertical extent of the lit coin windows, as fractions of the door. */
    const val SLOT_X0 = 36f / 128f
    const val SLOT_DX = 56f / 128f
    const val SLOT_Y0 = 21f / 160f
    const val SLOT_Y1 = 63f / 160f
    const val SLOT_HALF_W = 15f / 128f

    /**
     * The steel coin door (128 x 160 texels, painted at 4x): two coin mechs with chrome slot
     * plates, a return flap under each, a key lock and screws. The slots' lit windows are
     * [coinSlotLit], laid over the plates at the fractions above.
     */
    val coinDoor: Texture by lazy {
        paintTexture(128, 160, CabinetPaint.PLATE_SCALE) {
            val w = 128f
            val h = 160f
            round(0f, 0f, w, h, 8f, 0xFF30323C.toInt())
            roundGrad(3f, 3f, w - 6f, h - 6f, 6f, 0xFFCDD1DC.toInt(), 0xFF7A7E8E.toInt())
            // Brushed grain, and a darker recess round each mech.
            for (y in 6 until 154 step 3) rect(6f, y.toFloat(), w - 12f, 1f, alpha(-1, 0.07f))
            for (k in 0 until 2) {
                val x = SLOT_X0 * w + k * SLOT_DX * w
                round(x - 22f, 12f, 44f, 118f, 6f, alpha(0xFF000000.toInt(), 0.28f))
                // The slot plate: a chrome surround round the lit window.
                roundGrad(x - 19f, 15f, 38f, 54f, 5f, 0xFFF2F4FA.toInt(), 0xFF8A8E9E.toInt())
                round(x - 15f, 21f, 30f, 42f, 3f, 0xFF20090B.toInt())
                text("25", x, 79f, 10f, 0xFF22242C.toInt(), Fonts.condensed)
                // The return flap and its slot.
                round(x - 10f, 87f, 20f, 26f, 4f, 0xFF3A3C46.toInt())
                round(x - 8f, 89f, 16f, 22f, 3f, 0xFF16171D.toInt())
                round(x - 5f, 92f, 10f, 3f, 1.5f, 0xFF050508.toInt())
                circle(x, 122f, 4.6f, 0xFF7A1010.toInt())
                circle(x - 0.8f, 121.2f, 3.4f, 0xFFD02828.toInt())
            }
            // Keyed lock between the mechs, near the bottom.
            circle(w / 2f, 128f, 7f, 0xFF3A3C46.toInt())
            circle(w / 2f, 128f, 5.4f, 0xFFA8ACBA.toInt())
            round(w / 2f - 1.2f, 124f, 2.4f, 8f, 1.2f, 0xFF20222A.toInt())
            text("TOKENS ONLY", w / 2f, 150f, 8f, 0xFF2A2C34.toInt(), Fonts.condensed)
            for (sx in floatArrayOf(9f, w - 9f)) for (sy in floatArrayOf(9f, h - 9f)) with(CabinetPaint) { screw(sx, sy, 2.6f) }
        }
    }

    /** A coin slot's lit window: red glow round a black slit and a coin outline. Emissive quad, black stays black. */
    val coinSlotLit: Texture by lazy {
        paintTexture(30, 42, CabinetPaint.PLATE_SCALE) {
            vgrad(0f, 0f, 30f, 42f, 0xFFFF5040.toInt(), 0xFFB01818.toInt())
            radial(15f, 20f, 20f, alpha(0xFFFFB0A0.toInt(), 0.7f), 0)
            ring(15f, 12f, 6f, 1.3f, alpha(0xFF400808.toInt(), 0.8f))
            round(13.2f, 17f, 3.6f, 20f, 1.8f, 0xFF120303.toInt())
            vgrad(0f, 0f, 30f, 14f, alpha(-1, 0.25f), 0)
        }
    }

    /** A long speaker panel (painted at 4x): a dark plate of rows of slots between chrome screws, for backboxes and fascias. */
    val speakerStrip: Texture by lazy {
        paintTexture(128, 16, CabinetPaint.PLATE_SCALE) {
            roundGrad(0f, 0f, 128f, 16f, 3f, 0xFF2C2A34.toInt(), 0xFF121016.toInt())
            for (row in 0 until 3) for (k in 0 until 26) round(9f + k * 4.4f, 3f + row * 4f, 3.2f, 1.8f, 0.9f, 0xFF050408.toInt())
            strokeRound(0.8f, 0.8f, 126.4f, 14.4f, 3f, 0.9f, 0xFF6E7282.toInt())
            for (sx in floatArrayOf(4.5f, 123.5f)) with(CabinetPaint) { screw(sx, 8f, 1.5f) }
        }
    }

    /** A ticket dispenser's front plate: dark housing, a lit slot, "TICKETS" above it and chevrons pointing down. */
    val ticketPlate: Texture by lazy {
        paintTexture(64, 32, CabinetPaint.PLATE_SCALE) {
            roundGrad(0f, 0f, 64f, 32f, 4f, 0xFF3A3C48.toInt(), 0xFF1A1B22.toInt())
            strokeRound(1f, 1f, 62f, 30f, 3.5f, 1.2f, 0xFF8A8E9E.toInt())
            text("TICKETS", 32f, 10f, 7.5f, 0xFFFFD84D.toInt(), Fonts.condensed)
            round(9f, 14f, 46f, 7.5f, 3.5f, 0xFF050508.toInt())
            round(10.5f, 15.5f, 43f, 2.5f, 1.2f, 0xFFFF5A3C.toInt())
            for (k in 0 until 3) polygon(floatArrayOf(22f + k * 9f, 25f, 26f + k * 9f, 25f, 24f + k * 9f, 28.4f), alpha(0xFFFFD84D.toInt(), 0.8f))
            for (sx in floatArrayOf(4f, 60f)) with(CabinetPaint) {
                screw(sx, 4f, 1.4f)
                screw(sx, 28f, 1.4f)
            }
        }
    }

    /** A ticket sticking out of a dispenser: cream paper, red end stripes, a perforation and tiny print. */
    val ticketPaper: Texture by lazy {
        paintTexture(16, 32, CabinetPaint.PLATE_SCALE) {
            fill(0xFFF0E6C8.toInt())
            rect(0f, 0f, 16f, 3f, 0xFFD8323C.toInt())
            rect(0f, 29f, 16f, 3f, 0xFFD8323C.toInt())
            for (k in 0 until 7) circle(2f + k * 2f, 23f, 0.5f, 0xFFB8AC90.toInt())
            text("ADMIT", 8f, 12f, 3.6f, 0xFF8A2A30.toInt(), Fonts.condensed)
            text("ONE", 8f, 17f, 3.6f, 0xFF8A2A30.toInt(), Fonts.condensed)
            hgrad(0f, 0f, 3f, 32f, alpha(0xFF000000.toInt(), 0.12f), 0)
        }
    }

    /** Random prize-wall and claw-pile arrangement helper. */
    fun jitter(i: Int, salt: Int, range: Float) = (hash01(i, salt) - 0.5f) * 2f * range
}
